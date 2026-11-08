# AND-28 · 多 Outro 选择、替换与 PCM 级验证（阶段 C 续）

- **checked_at**: 2026-11-08T17:55+08:00 (Asia/Shanghai)
- **证据级别**: **JVM 17 + 设备 8（含电平测量与替换）**
- **关键结论**: 可以**指定**某条 Outro、可以**替换**未执行的请求，且切换前后的电平是**测出来的**

---

## 1. 补上的三处空白

| 之前 | 现在 |
|---|---|
| 只接 `Outro00` | **三条 Outro（00/01/02）各自成段** |
| 只验证「非静音」 | **RMS 电平测量** + 明确的「不塌陷」判据 |
| `EndsSilently` 名不副实 | **改名** + 另设真实验证「Outro 会结束」 |

---

## 2. 三条 Outro 的实测长度

```
Outro00   226,286 frames   4.714 s   无循环
Outro01   205,715 frames   4.286 s   无循环
Outro02   205,376 frames   4.279 s   无循环
```

**都不循环** —— 结尾不该循环。测试断言 `!hasLoop`。

---

## 3. 结构变化

`Compiled` 现在有两个映射：

```kotlin
val segmentByRole: Map<StageFlow.Role, Int>   // 4 个结构段
val segmentByOutro: Map<Int, Int>             // 每条 Outro 一段，按编号索引
fun segmentForOutro(number: Int): Int?
```

**为什么分开**：`Role.Outro` 只有一个值，无法区分三条 Outro。用编号索引才能「请求第 1 条」。

段索引 `1110 + numero`，与结构段（1100–1103）不重叠。

### 过渡规则

| 规则 | source → destination |
|---|---|
| Prelude 交接 | Pre_Short / Pre → Main（含 bridge） |
| Bridge 出口 | Bridge → Main |
| Outro 请求 | Main → **每条** Outro（各一条规则） |
| Outro 替换 | 每对有序 Outro（A → B，A ≠ B） |

**Outro 各设一条规则而不是「Main → 多条」**：后者把选择权交给引擎，而调用方要求的是「第 2 条」，必须精确。

---

## 4. PCM 级验证：`AudioLevel`

新增一个可测的测量工具，把「非全零」升级成可复现的断言：

| 度量 | 用途 |
|---|---|
| `rms(block)` | 块的能量 |
| `peak(block)` | 峰值 |
| `isSilent(block)` | **全部**为零才算静音 |
| `levelHeld(before, after, floor=0.05)` | 切换前后电平是否保持在合理范围 |
| `render(engine, blocks)` | 批量渲染，返回静止块数、峰值、平均 RMS、失败块索引 |

### 为什么需要 `levelHeld`

一条过渡如果**把音轨静音**、**压低 26 dB**、或**从头重启**，都能通过「非全零」检查。

`levelHeld` 三件事都能区分：

- `after == 0` → 失败
- `after / before < 0.05`（约 −26 dB）→ 失败
- `after / before > 20` → 失败（爆炸性增长）
- 正常淡入淡出（如 −12 dB）→ 通过

阈值是**声明出来的策略**，写在代码里并有测试钉住。

---

## 5. 设备实测：八个测试

| 测试 | 断言 |
|---|---|
| `allThreeOutrosAreCarriedWithTheirMeasuredLengths` | 三条都在，帧数与实测一致，**都不循环** |
| `eachOutroGetsItsOwnSegment` | 三个段索引互不相同 |
| `requestingASpecificOutroLandsOnThatOutro` | 请求 1 → destination **就是 1 的段** |
| `everyOutroCanBeRequestedFromMain` | 三条**逐一**从 Main 请求成功且都出声 |
| `theHandoverToAnOutroHoldsTheLevel` | **RMS 前后不塌陷** |
| `anOutroRunsOutInsteadOfLoopingForever` | 渲染 600 块后**出现静音块** |
| `aQueuedOutroRequestCanBeReplacedBeforeItExecutes` | 两次请求间不渲染 → **第二次胜出** |
| `replacingWhileTheOutroAlreadyRunsKeepsTheTransportMoving` | 已在播时替换 → 时钟不回退 |

### 两条最关键

**`aQueuedOutroRequestCanBeReplacedBeforeItExecutes`** —— 这是指令要求的「取消或替换尚未执行的 Outro」。关键在**两次 `play()` 之间不渲染**，确保第一次请求还没被执行。

**`theHandoverToAnOutroHoldsTheLevel`** —— 这是指令要求的「复杂音乐切换必须增加 PCM 验证」。

---

## 6. 修掉的问题

### 6.1 测试自身的错误：Outro 缺编号

`StageFlowReader.assemble` 现在要求每条 Outro 都有编号（从文件名解析）。测试里手工构造的 `Part` 没给编号 → 全部失败。

**这不是代码错，是测试夹具没跟上新约束。** 补上 `outroNumber` 即可。

### 6.2 `everyRoleGetsItsOwnSegment` 期望 5

Outro 移入 `segmentByOutro` 后，`segmentByRole` 只有 4 条。改成断言两个映射合计 5 段且互不相同。

### 6.3 名不副实的测试，改名而不是掩盖

原名：`mainToOutroRunsAsATransitionAndEndsSilently`
实际断言：渲染 600 块不崩溃

**改名为 `mainToOutroRunsAsATransitionWithoutCrashing`**，并在注释里指向真正的静音验证（`MultiOutroTest.anOutroRunsOutInsteadOfLoopingForever`）。

`and-27` 里我记下了这个不准确，这一轮把它解决了。

---

## 7. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **339 tests, 0 failures** |
| `./gradlew :app:connectedDebugAndroidTest` | **101 tests, 0 failures** |

合计 **583**。设备 `mkw_test(AVD)` Android 15 **x86_64 模拟器**。

---

## 8. 仍未验证

| 项 | 状态 |
|---|---|
| Bridge 与主体的**实际重叠** | 未验证（快照只给 voice 总数，无 per-voice 明细） |
| 过渡**失败**时原音乐不中断 | 未验证 |
| Cue 相对同步（`NextCustomCue` / `ExitCue`） | 未验证 |
| preEntry / postExit 实际效果 | 未验证 |
| Fade 曲线实际听感 | 未验证 |
| 原版切换时机 | **BLOCKED**（缺 L3） |
| 真机 arm64 | 未验证 |

---

## 9. 对照指令「阶段 C 完成标准」

| 要求 | 状态 |
|---|---|
| 各段文件导入及稳定引用 | ✅ |
| 独立循环和分段试听 | ✅ |
| 编译多 Segment Music Graph | ✅ |
| 真实 Native 执行 | ✅ |
| **多 Outro 请求处理** | ✅ 三条可逐一请求 |
| **替换未执行的请求** | ✅ |
| Bridge 叠加音频测试 | ❌ 只验证声明 |
| **连续播放的音频正确性（PCM 级）** | ✅ 电平测量 |
| Android 设备测试 | ✅ |

### 仍缺

| 项 | 状态 |
|---|---|
| 样本级边界 | ⚠️ 部分（loop 窗口靠段循环） |
| Tail | ❌ |
| 音频缓冲 | ❌ |
| 播放速度 | ❌ |
| 多段图上的 Seek | ❌ |
| 异常中断 | ❌ |
