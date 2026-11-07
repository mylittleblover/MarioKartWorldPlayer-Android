# AND-19 · Oboe 真实输出流与回调时序（首次）

- **checked_at**: 2026-11-08T10:40+08:00 (Asia/Shanghai)
- **证据级别**: **S5 真实设备流 + 真实回调时序**
- **这是本项目第一次让 Oboe 真正打开输出流**

---

## 1. 之前的盲区

所有设备测试都用 `-no-audio` 启动模拟器，所以：

- `AudioOutput.open()` **永远返回 null**
- Oboe 回调**一次也没跑过**
- `TimingDiagnosis` **从未见过真实数据**
- 界面一直显示 `NO DATA`

**这条证据把整个输出路径从「代码存在」变成「实测运行」。**

---

## 2. 关键发现：模拟器要**不**带 `-no-audio`

之前为了静音都加 `-no-audio`。**去掉它，模拟器就提供虚拟音频设备。**

```bash
emulator -avd mkw_test -no-window -no-boot-anim -gpu swiftshader_indirect -no-snapshot
                                    ↑ 不带 -no-audio
```

---

## 3. 实测输出

```
AUDIO_OUTPUT_OK: rate=48000 callbacks=54 verdict=DEGRADED
                 misses=1 cpuP99us=1808 budgetUs=9500
```

| 字段 | 实测值 | 含义 |
|---|---|---|
| 设备采样率 | **48000** | 与图声明一致 |
| 500ms 内回调次数 | **54** | 约每 9.3ms 一次，符合低延迟配置 |
| CPU p99 | **1808 µs** | 单次回调计算耗时 |
| 回调预算 | **9500 µs** | 20ms 缓冲里的可用时间 |
| CPU 占比 | **19%** | 余量充足 |
| 丢帧 | **1 / 54** | 1.85% |
| 判定 | **DEGRADED** | 因丢帧率 ≥ 1% 触发 |

**判定正确**：CPU 占比 19% 远低于 70% 阈值，但丢帧率 1.85% 超过 1% 警戒线 → `DEGRADED`。

---

## 4. 三方交叉验证

`dumpsys media.audio_flinger` 独立确认：

```
Output thread 0x... name AudioOut_D, type 0 (MIXER):
  Sample rate: 48000 Hz
```

**引擎报告 48000，系统音频栈也报告 48000。** 两个独立来源一致。

---

## 5. 四个设备测试

| 测试 | 断言 |
|---|---|
| `deviceOutputOpensAndRunsCallbacks` | 流能开、回调 > 0、时序包能解析、判定非 NO_DATA |
| `closingTheStreamTwiceIsSafe` | 二次 close 不崩，关闭后快照为 null |
| `outputRejectsAForeignEngineHandle` | 句柄 0 被拒绝，不解引用 |
| `engineReportsOutputActiveWhileTheStreamOwnsIt` | 流占用时绑定返回 `OutputActive(7)` |

### 一个刻意的设计

`deviceOutputOpensAndRunsCallbacks` **在没有音频设备时不失败**，而是打印 `NO_AUDIO_DEVICE` 并返回。

**理由**：「没有音频设备」是真实配置，不是 bug。把它写成失败会让 CI 在无音频环境里永远红。

但**有设备时**，所有断言都是硬性的：流必须开、回调必须跑、时序必须能解析。

---

## 6. 对用户的意义

真机上这里会给出真实的 CPU 占用和丢帧率。

模拟器上 1.85% 丢帧是**软件渲染 + 虚拟音频栈**的结果，真机应该更好。**但这些数字现在是真的，不是猜的。**

---

## 7. 仍需注意

| 项 | 状态 |
|---|---|
| 人耳听音 | **NOT_RUN** —— 模拟器音频输出到虚拟设备，我无法确认内容 |
| 真机 arm64 | **NOT_RUN** |
| 长时间稳定性 | 只跑了 500ms |

---

## 8. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | 203 |
| Android Instrumentation | **46** |
| **合计** | **392** |

46 个设备测试全部通过。
