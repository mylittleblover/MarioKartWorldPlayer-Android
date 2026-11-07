# AND-08 · 播放器外壳与引擎封装

- **checked_at**: 2026-11-08T01:50+08:00 (Asia/Shanghai)
- **证据级别**: **编译通过 + 25 个 JVM 测试**；设备行为由 AND-04/05/06 覆盖

---

## 1. 新增的两层封装

应用层之前只有裸的 JNI 对象，任何调用方都能把引擎句柄当 `Long` 传错。现在有了持有点：

### `AudioEngine`

封装 `NativeAudioLibrary` 的引擎侧调用（**不新增任何调度逻辑**）：

| 职责 | 做法 |
|---|---|
| 句柄不外泄 | `private val handle`，构造函数私有 |
| 生命周期 | `close()` 幂等；原生 `delete` 只执行一次 |
| 关闭后调用 | 统一返回 `CLOSED = -1`，**不与任何真实 `EngineStatus` 冲突** |
| 常量 | `PROVIDER_BWAV`、`COMMAND_*`、`PLAY_LINEAR` 等集中定义并注释来源 |

### `AudioOutput`

封装 Oboe 输出流：

- `open()` 在设备无法打开时返回 `null`（**正常情况**，模拟器/无音频路由设备都会发生），调用方应回退到控制渲染，而不是当致命错误
- `close()` 幂等
- `isRunning()` 用 `outputSnapshot[0] > 0`（设备采样率），而不是猜测

## 2. 为什么不在这一层加"播放逻辑"

交接包 `docs/01` 要求**单 Runtime / 单 Transport / 单 Output**。

这些封装**只做所有权与数值转译**，不加队列、不加时钟、不加重采样 —— 加任何一样都会变成"第二个实现"，破坏那条契约。

## 3. 常量契约测试（新增 7 个）

这些数字跨 JNI 边界，写错只会在运行时得到一个笼统的状态码。现在写错会**编译期失败**：

| 测试 | 锁定 |
|---|---|
| `playTargetForLinearIsMinusOne` | linear 模式只接受 `-1` |
| `commandKindsMatchTheNativeEnum` | Play=0 Stop=1 Pause=2 Resume=3 |
| `okStatusIsZero` | `EngineStatus::Ok = 0` |
| `preparedIsZeroSoStreamedIsOne` | `SourcePreparation::Prepared = 0` |
| `providerIdIsTheOneTheProbeReturns` | `native.bwav` |
| `closedSentinelCannotCollideWithARealStatus` | `-1` 不在 `EngineStatus` 值域（0..11） |
| `commandKindsAreDistinct` | 命令码不重复 |

## 4. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **25** |
| Android Instrumentation | 17 |
| **合计** | **185** |

## 5. 未验

| 项 | 状态 |
|---|---|
| `AudioOutput` 在真实设备上开流 | **NOT_RUN**（模拟器 `-no-audio`） |
| 设备热切换 / 采样率变更 | **NOT_RUN** |
| UI 层接线 | **未做** |
| kind=2 播放 | **UNRESOLVED** |

> **明确**：本轮新增的只有编译期与单元测试证据。设备上的播放证据仍来自 AND-04/05/06，没有因为新增封装而增加。
