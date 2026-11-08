# AND-26 · Transition 实际执行（阶段 C 前提）

- **checked_at**: 2026-11-08T17:25+08:00 (Asia/Shanghai)
- **证据级别**: **JVM 11 + 设备 6（真实过渡执行）**
- **关键结论**: Transition 在真实引擎上**确实会执行**，不是只被解析

---

## 1. 补上的空白

`and-25` 证明引擎**接受**含 transition 的图，但每个测试都调 `Play(segment)` 直接起段 ——
`transitionChoice` 找不到规则，走的是 `startSegment`。

**所以在此之前，没有任何一次 transition 真正跑过。**

---

## 2. 机制（读源码得到，非猜测）

`EngineRuntime::requestPlay`：

```cpp
if(!graph_ || !foreground_ || !foreground_->segment || !clock_.playing()) return startSegment(target);
Transition probe; TransitionRun peek; preview_->reset();
const auto ready=prepareDestination(*foreground_,target,probe,peek);
if(ready!=EngineStatus::Ok) return ready;
const auto choice=transitionChoice(*foreground_,target,peek);
if(!choice.rule) return startSegment(target);        // ← 无规则直接换段
return planTransition(*foreground_,target,choice,false);   // ← 有规则走过渡
```

**所以：当前段是 10、请求 12、存在 `sources=[10] destinations=[12]` 的规则时，`Play(12)` 走的是过渡，而不是立即切段。**

这条路径之前从未被触发，因为图里没有 transition。

---

## 3. 快照字段顺序是从源码抄的，不是猜的

`EngineRuntime.cpp::snapshot()`：

```cpp
result.transition = { run.active, run.rule->index, run.sourceSegment,
                      run.segment, run.rule->bridge, run.requested, run.sourceSync,
                      run.destinationEntry, run.preStart, run.candidates,
                      run.specificity, run.priority, queuedTransition_,
                      !run.active ? 3 : run.sourceLeft ? 2
                                   : run.destinationStarted || run.bridgeStarted ? 1 : 0 };
```

对应快照索引 24..37。

### 我第一版猜错了

最初我写的是 `{index, source, destination, bridge, elapsed, phase, result}`。

**按那个顺序读，`requestedAt` 会被当成 `phase`，`sourceSync` 会被当成 `result`。** 不崩溃，只是把请求帧号报成阶段码。

**这是本项目反复出现的同一类错误** —— 偏移读错，静默出错。写完对照源码才发现，然后加了测试钉住：

```kotlin
fun requestedAtIsNotAStatus() {
    val fields = TransitionSnapshot.read(packet(requestedAt = 123456))!!
    assertEquals(123456L, fields.requestedAt)
    assertTrue(fields.phase < 4, "phase must be one of the four codes")
}
```

阶段是**码值不是枚举**：0 pending / 1 entered / 2 source left / 3 inactive。

---

## 4. 设备实测：六个测试

图结构：

```
Segment 10  Pre  ──transition 40 (bridge=11, sync=Immediate)──▶  Segment 12  Main
                       └─ Segment 11 Bridge
```

| 测试 | 断言 |
|---|---|
| `playingTheDestinationWhileTheSourceRunsStartsATransition` | `Play(12)` 后 **transition == 40**，source == 10，destination == 12 |
| `theTransitionNamesItsBridgeSegment` | bridge == 11 |
| `theTransitionAdvancesThroughPhases` | 相位**离开 pending** |
| `theTransportKeepsAdvancingThroughATransition` | 过渡期间 transport 帧号**递增** |
| `audioStaysNonSilentAcrossTheTransition` | 过渡期间**不静音** |
| `aTargetWithoutAMatchingRuleStartsDirectly` | 无规则时**不走过渡**（对照组） |

### 为什么最后一个最重要

它排除了「`Play` 根本没生效所以看不到 transition」这种解释。

同一张图、同一个引擎：`Play(12)` 走过渡，`Play(11)` 直接换段。**区别只在规则是否存在。**

---

## 5. `sync = Immediate` 是刻意的

真实游戏里 Coaster 的 Bridge 何时进入属于 **L3 游戏行为事实** —— 本项目只有 L1（资源结构）与 L2（声音），**没有 L3**。

所以：

- 用 `Immediate` 让过渡在几次渲染内可观测
- **不断言「这就是原版时机」**
- 界面与文档都标注这是 **PROVISIONAL 测试配置**

---

## 6. 仍未验证

| 项 | 状态 |
|---|---|
| Cue 相对同步（`NextCustomCue` / `ExitCue`） | 未验证 |
| `preEntry` / `postExit` 实际效果 | 未验证 |
| Fade 曲线 | 未验证 |
| Bridge 与主体**重叠**播放 | 未验证（只验证了 bridge 段被声明） |
| 多个可选 Outro 的请求与切换 | 未验证 |
| MusicalLoop 运行时 | 未验证 |
| 过渡被**取消或替换** | 未验证 |
| 过渡失败时原音乐不中断 | 未验证 |
| 原版 Coaster 真实切换时机 | **BLOCKED**（缺 L3） |

**`aTargetWithoutAMatchingRuleStartsDirectly` 只证明了「无规则时直接切段」，不等于「过渡失败时原音乐不中断」。**

---

## 7. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **290 tests, 0 failures** |
| `./gradlew :app:connectedDebugAndroidTest` | **84 tests, 0 failures** |

合计 **517**。设备 `mkw_test(AVD)` Android 15 x86_64。

---

## 8. 本轮修掉的测试自身错误

| 问题 | 现象 |
|---|---|
| `assertTrue("msg", cond)` 参数顺序 | Kotlin 版签名是 `assertTrue(cond, msg)`，编译失败 |
| 用 `EngineSnapshot.read` 验证块不重叠 | 全零数组版本号不符 → 返回 null → 断言 `!= null` 失败 |

第二条是我的测试写错了，不是代码错了 —— **但结论一样：那条断言本身没有意义，改成直接比较索引区间。**
