# AND-24 · 音频焦点与生命周期收口（阶段 A）

- **checked_at**: 2026-11-08T16:50+08:00 (Asia/Shanghai)
- **基线 HEAD**: `6fb9dbe`（比指令中的 `5c42fcd` 多 2 个文档提交）
- **证据级别**: JVM 14 + 设备 5（全部执行）

---

## 1. 做了什么

| 层 | 内容 |
|---|---|
| `FocusPolicy` | 纯决策函数，5 个事件 × 3 个状态，全部 JVM 可测 |
| `AudioFocusManager` | `AudioFocusRequest` 封装 + 路由丢失入口 |
| `PlaybackService` | 请求/放弃焦点、路由广播接收器、**状态轮询** |
| 播放前守卫 | 焦点被拒 → 不播放 |

---

## 2. 焦点规则的每一条都有错误答案

| 事件 | 动作 | 错误答案的后果 |
|---|---|---|
| `LOSS_TRANSIENT`（来电） | 暂停，可恢复 | 不暂停 → 通话时两个声音 |
| `LOSS`（其他播放器接管） | 暂停，**不**自动恢复 | 自动恢复 → 用户不想要的声音突然响 |
| `GAIN` | 仅当**焦点自己暂停过**才恢复 | 无条件恢复 → 用户手动暂停的曲目自己开始播 |
| `LOSS_TRANSIENT_CAN_DUCK` | 继续播（见 §4） | 暂停 → 每条通知都打断音乐 |
| `BECOMING_NOISY`（拔耳机） | 暂停，不自动恢复 | 不暂停 → **外放突然响** |

**最后一条是最尴尬的音频 bug。**

测试 `noEventEverResumesWithoutAPriorFocusPause` 遍历全部 `Event × playing` 组合，断言只有带 `pausedByFocus` 时才可能 `RESUME`。

---

## 3. 修掉一个会让进程崩溃的真 bug

第一次设备测试报了 `ForegroundServiceDidNotStartInTimeException`，logcat 关键行：

```
Bringing down service while still waiting for start foreground
```

**成因**：`startForegroundService()` 向系统承诺随后调用 `startForeground()`。如果在握手完成前调用 `stopService()`，服务被拆掉，系统判定承诺未兑现并**杀进程**。

**这条从 UI 可达**：开始播放后立刻按「停止」。

**修法**：`PlaybackService.stop()` 不再立即 `stopService`，而是延迟 400ms（`STOP_GRACE_MS`），让握手完成。`start()` 会取消未触发的停止（用户恢复了）。

> 这解释了之前几轮反复出现的同一崩溃 —— 之前只在测试里见到，这次定位到生产路径。

---

## 4. 明确未实现：ducking

`LOSS_TRANSIENT_CAN_DUCK` 的动作是**继续以全音量播放**。

**理由**：引擎没有主音量命令（`LayerGain` 是图层级，不是总线级），而 ducking 正确的路径是图里声明一个 `Parameter` + `Rtpc` 曲线映射到 `VolumeDb`，再用 `SetParameter` 驱动 —— 那是阶段 B 的图构建工作。

平台**明确允许**在此事件继续全音量播放，所以这不是「假装 duck」，而是「选择不做并提供理由」。已登记为缺口。

---

## 5. 修掉第二个真 bug：路由广播收不到

接收器最初用 `Context.RECEIVER_NOT_EXPORTED`：

```kotlin
registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
```

`AUDIO_BECOMING_NOISY` 是**系统广播**，来自进程之外。`NOT_EXPORTED` 意味着只接收本应用发出的广播 —— **系统自己也送不进来**，拔耳机在生产环境同样不会暂停。

改为 `RECEIVER_EXPORTED`，并注明这是安全的：该 action 是 **protected broadcast**，只有系统能发，其他应用发不了。

---

## 6. 修掉第三个真问题：界面不在时状态不更新

`PlaybackSession.state` 只在有人调用 `poll()` 时更新。

`PlayerViewModel` 每 200ms 轮询 —— **但那只在界面活着时**。退出界面后：

- 通知栏位置永远停在退出那一刻
- `state.playing` 变成陈旧值
- 焦点决策读到的是过期状态

**这正是指令里「前台 Service 与 Native 引擎状态一致」那条。**

修法：`PlaybackService` 自己每 500ms `poll()` + `refresh()`，仅在 `state.active` 为真时运行。

**这是测试逼出来的**：`playing` 一直不为真的失败让我发现没人轮询。

---

## 7. 无法验证的部分（如实记录）

`AUDIO_BECOMING_NOISY` 的**平台投递**无法验证：

```
adb shell am broadcast -a android.media.AUDIO_BECOMING_NOISY
java.lang.SecurityException: Permission Denial: not allowed to send broadcast
  android.media.AUDIO_BECOMING_NOISY from pid=4949, uid=2000
```

- 应用自己发 → `SecurityException`
- `adb shell`（uid 2000）发 → 同样被拒
- 模拟器无法真的拔耳机

**结论：`NOT_VERIFIED`。** 拆成三段分别验证：

| 段 | 验证方式 |
|---|---|
| 策略决定暂停 | JVM，全分支 |
| 管理器转发事件 | 设备：`theNoisyBroadcastReachesTheManager` |
| 服务注册了该 action | 构造上；`EXPORTED` 的理由见 §5 |

**第一版测试试图直接 `sendBroadcast`，第二版用 `UiAutomation` 走 shell，两次都被平台拒绝。** 把这个过程写下来比删掉测试更有价值。

---

## 8. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | **143 passed** |
| `./gradlew :app:testDebugUnitTest` | **256 tests, 0 failures** |
| `./gradlew :app:connectedDebugAndroidTest` | **74 tests, 0 failures** |

设备：`mkw_test(AVD)` Android 15 x86_64，**不是真机**。

四条历史数字（143 / 242 / 69 / 454）已过时，本轮为 **143 + 256 + 74 = 473**。

---

## 9. 仍未验证

| 项 | 状态 |
|---|---|
| 真实来电打断 | `NOT_VERIFIED`（模拟器无法拨号） |
| 真实拔耳机 | `NOT_VERIFIED`（见 §7） |
| 蓝牙 / 有线切换 | `NOT_VERIFIED` |
| 真机 arm64 | 本轮只跑 x86_64 模拟器 |
| ducking | 未实现（见 §4） |

---

## 10. 原生测试

本轮**未运行** CTest —— 阶段 A 只改 Kotlin 与 Android 层，未触及 `engine/native/`。
阶段 B 会修改图构建路径，届时需要运行原生测试并记录选择器与结果。
