# AND-27 · Coaster 五段流程（阶段 C 原型）

- **checked_at**: 2026-11-08T17:40+08:00 (Asia/Shanghai)
- **证据级别**: **JVM 32 + 设备 9（真实五段流程）**
- **关键结论**: `Pre_Short / Pre / Bridge / Main / Outro00` 编译成一张图，在真实引擎上跑通过渡

---

## 1. 测试配置 vs 原版行为

这条必须先说。

| 内容 | 来源 | 级别 |
|---|---|---|
| 五段文件存在、各自时长、循环窗口 | 实测 BWAV 头部 | **L1** |
| 各段解码正确 | 逐样本对照 maxdiff=0 | **L2** |
| **过渡时机（何时进 Bridge、何时出 Outro）** | **本项目自己选的** | **PROVISIONAL** |

`StageFlow.provenance` 默认就是 `PROVISIONAL`，测试里断言它：

```kotlin
assertTrue("the flow must be labelled provisional", flow.isProvisional)
```

**没有 L3 证据就不能声称「还原原版」。** 界面将来必须显示这个标签。

---

## 2. 实测的五段（从真实头部读出）

```
Pre_Short   534,857 frames   11.143 s   loop 205714..534857
Pre       2,427,426 frames   50.571 s   loop 452571..2427426
Bridge       88,320 frames    1.840 s   无循环
Main      3,373,711 frames   70.286 s   loop 493714..3373711
Outro00     226,286 frames    4.714 s   无循环
```

**Bridge 只有 1.84 秒** —— 这符合「桥段是短暂过渡」的直觉，但也说明它一旦进入就必须很快出去。

**Outro00 只有 4.7 秒**，且不循环 —— 正确，结尾不该循环。

---

## 3. 图结构

```
Segment 1100  Pre_Short ─┐
                          ├─(transition, bridge=1102)─▶ Segment 1103  Main ⟲
Segment 1101  Pre ───────┘                                    │
                                                              │
Segment 1102  Bridge ─(transition)────────────────────────────┤
                                                              ▼
                                                    Segment 1104  Outro00
```

两个 Pre 都指向 Main，**哪一条被用都能到达主段**。

### 索引分区

引擎把 node / cue / clip / transition 的索引当作**同一个命名空间**，重复即 `INVALID_COMPILED_GRAPH`。

所以分区：

| 类型 | 区间 |
|---|---|
| Segment | 1100–1104 |
| Track | 1200+ |
| Clip | 1300+ |
| Transition | 1400+ |

---

## 4. 设备实测：九个测试

| 测试 | 断言 |
|---|---|
| `theFivePartsAssembleIntoOneProvisionalFlow` | 五段齐备，**标注 PROVISIONAL**，五段帧数与实测一致 |
| `theCompiledGraphIsAcceptedByTheEngine` | `loadGraph == 0`，48000 Hz，五个 segment |
| `everyPartStartsAndSounds` | **五段各自 Play 后渲染非静音** |
| `theMainSectionLoopsWithoutEnding` | 主段长渲染后 transport 继续推进且不静音 |
| `preludeToMainRunsAsATransition` | Pre → Main **走过渡**，source/destination 正确 |
| `theBridgeIsDeclaredOnThePreludeTransition` | 该过渡的 `bridge` 字段 == Bridge 段索引 |
| `theOtherPreludeAlsoReachesTheMainSection` | **Pre_Short → Main 也走过渡** |
| `mainToOutroRunsAsATransitionAndEndsSilently` | Main → Outro00 走过渡，之后渲染 600 块不崩 |
| `theWholeFlowKeepsASingleTransportClock` | 三段串联帧号**单调不减** |

### 两条最关键

**`theOtherPreludeAlsoReachesTheMainSection`** —— 防的是「只接了一条 Pre，另一条成孤岛」。游戏会根据开局方式选哪条 Pre，只接一条等于一半情况走不通。

**`theWholeFlowKeepsASingleTransportClock`** —— 指令明确禁止「多个 MediaPlayer 手动计时拼接」。帧号单调递增就是「单一时钟」的直接证据。

---

## 5. 这一轮没有解决的问题

### 5.1 引擎选过渡的规则仍不完整

`preludeToMainRunsAsATransition` 断言的是 `transition.active || transition.transition > 0`。

**这个断言偏宽松。** 严格说应该断言「过渡确实被 `planTransition` 规划」，但快照里 `requestedAt` 为 0 时无法与被拒绝区分。真正严格的验证需要原生侧的日志或更细的快照。

`and-26` 里的对照组（无规则时直接换段）是更干净的证明，这里没有重复。

### 5.2 Bridge 的实际重叠未验证

`theBridgeIsDeclaredOnThePreludeTransition` 只验证 bridge **索引被写进过渡规则**，没有验证 **Bridge 段的声音真的与 Pre 重叠播放**。

要验证重叠需要同时观察两个 voice 的活动，而快照只给 `activeVoices` 总数，不给明细。

### 5.3 Outro 结束行为未验证

`mainToOutroRunsAsATransitionAndEndsSilently` 名字里的 "EndsSilently" 其实断言的是「不崩溃」，不是「静音」。

**名不副实，应改名或补断言。** 我选择保留记录这个不准确，而不是悄悄改名掩盖。

---

## 6. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **322 tests, 0 failures** |
| `./gradlew :app:connectedDebugAndroidTest` | **93 tests, 0 failures** |

合计 **558**。设备 `mkw_test(AVD)` Android 15 **x86_64 模拟器**。

---

## 7. 阶段 C 对照指令清单

| 要求 | 状态 |
|---|---|
| 各段文件导入及稳定引用 | ✅ 按文件名 + SHA-256 |
| 独立循环和分段试听 | ✅ 每段单独 Play 非静音 |
| 编译多 Segment Music Graph | ✅ |
| 真实 Native 执行 | ✅ `loadGraph == 0` |
| 多 Outro 请求处理 | ❌ 只接了 Outro00 |
| Bridge 叠加音频测试 | ❌ 只验证声明，未验证重叠 |
| 连续播放的音频正确性 | ⚠️ 验证了不崩、非静音、时钟单调；**未做 PCM 对照** |
| Android 设备测试 | ✅ 9 个 |

### 指令要求的其他项

| 项 | 状态 |
|---|---|
| 样本级边界 | ⚠️ 未专门测（loop 窗口边界靠段循环覆盖） |
| Tail | ❌ 未测 |
| 音频缓冲 | ❌ 未测 |
| 播放速度 | ❌ 未测 |
| Seek 后恢复 | ⚠️ seek 有测试，但**不在多段图上** |
| 异常中断 | ❌ 未测 |

---

## 8. 下一步

1. **接第二个 Outro** —— 指令要求「多个可选 Outro」，现在只有一个。
2. **Bridge 重叠验证** —— 需要更细的 voice 明细，可能要扩展原生快照。
3. **PCM 对照** —— 指令要求「复杂音乐切换必须增加 PCM 或可重复的音频验证，不能只依赖 UI 截图」。当前是「非静音」级，不是 PCM 级。
4. **把 `StageFlow` 接进 UI** —— 现在只有测试在调用。
