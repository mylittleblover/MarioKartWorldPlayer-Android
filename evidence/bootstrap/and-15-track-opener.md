# AND-15 · TrackOpener：三个零件的接线

- **checked_at**: 2026-11-08T04:45+08:00 (Asia/Shanghai)
- **证据级别**: **7 个 JVM + 3 个设备测试**（22 个设备测试全过）

---

## 1. 这个类解决的问题

之前有三个独立零件：

```
BwavHeader   →  读容器（rate/channels/frames/loop/kind）
GraphBuilder →  造图（573 字节 v6）
AudioEngine  →  加载 / 绑定 / 播放
```

**但没人把它们串起来。** `TrackOpener` 就是那条线：

```kotlin
TrackOpener.open(assetId, bytes) → Playing(engine, ...) | Refused(reason)
```

**一个函数，要么给你一个在跑的引擎，要么给你一句能显示给用户的原因。**

## 2. 顺序是刻意的

```
1. 读头部        ← 最便宜，先拒掉非 BWAV / 坏头
2. 查编码        ← kind=2 在这里被拒，带具体原因
3. 造图          ← 纯计算，不碰原生
4. 探针校验      ← 信封错在这里被拒
5. 建引擎 + 加载  ← 第一次原生调用
```

**前四步完全不需要引擎。** 一个坏文件**永远不会**分配引擎。

## 3. 失败路径不留垃圾

每一条失败分支都 `engine.close()` 后再返回。`Refused` 类型里**根本没有 engine 字段** —— 结构上就不可能泄漏。

```kotlin
data class Playing(val engine: AudioEngine, ...) : Result
data class Refused(val reason: String) : Result   // 无 engine
```

## 4. 设备验证（真实曲目）

```
TrackOpenerDeviceTest
  opensAndPlaysTheRealTrack      PASS
  refusesWithoutAllocatingAnEngine PASS
  closingTwiceIsSafe             PASS
```

`opensAndPlaysTheRealTrack` 断言：

| 断言 | 结果 |
|---|---|
| 结果类型是 `Playing` | ✅ |
| 读出的 sampleRate | 48000 ✅ |
| 读出的 frames | 3,373,711 ✅ |
| 读出的 loop | 493,714 .. 3,373,711 ✅ |
| 渲染 512 帧非静音 | ✅ |
| peak 在合理范围 | ✅ |

## 5. 修复的一个编译错误

```
'public' function exposes its 'internal' parameter type 'AudioEngine'
```

`AudioEngine` 是 `internal`（不该外泄），而 `TrackOpener` 默认 public，`Playing` 里暴露了它。

**修复**：`TrackOpener` 也标 `internal`。

**这不是权宜之计** —— 引擎本就该封装在应用内部，UI 层只应看到状态，不该拿到原生句柄。

## 6. 又遇到同一个环境问题（第三次）

```
Starting 22 tests on FNPlay(AVD) - 11
Tests on FNPlay(AVD) - 11 failed: There was 14 failure(s).
```

**原因同前两次**：机器上有别的 AVD（`FNPlay`）在跑，adb 把测试派过去了。

**这次的处理**：用 `-port 5556` 在**另一个端口**起 `mkw_test`（避免杀掉别人的实例），然后：

```
Starting 22 tests on mkw_test(AVD) - 15
Finished 22 tests
BUILD SUCCESSFUL
```

**22 / 22 全过。**

> 这个坑已经连续踩了三次。**根因是 adb 会往任意一个在线设备派测试**，而机器上不止一个 AVD。正确做法应是 `ANDROID_SERIAL=emulator-5556` 固定目标，我下次会直接用这个。

## 7. 现在的完整链路

```
用户选文件
   ↓
BwavHeader.read(bytes)          → 可播？什么参数？
   ↓
Playability.evaluate(kind)      → kind=2 直接拒
   ↓
GraphBuilder.build(spec)        → 573 字节图
   ↓
GraphProbe.inspect(packet)      → CRC/长度/magic
   ↓
AudioEngine.loadGraph / bind / play
   ↓
渲染 / 输出
```

**除了"用户选文件"那一步，其余全部有测试覆盖。**

## 8. 仍未做的

| 项 | 状态 |
|---|---|
| 文件选择器 | **未做** |
| 曲库列表 UI | **未做** |
| 播控（暂停/进度/下一首） | **未做** |
| `AudioOutput` 在设备上真实开流 | **NOT_RUN**（模拟器 `-no-audio`） |
| 人耳听音 | **NOT_RUN** |

## 9. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **126** |
| Android Instrumentation | **22** |
| **合计** | **291** |
