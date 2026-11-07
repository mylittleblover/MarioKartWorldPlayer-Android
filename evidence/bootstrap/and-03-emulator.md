# AND-03 · 模拟器运行时验证（S5 达成）

- **checked_at**: 2026-11-07T23:40+08:00 (Asia/Shanghai)
- **证据级别**: **S5 Instrumentation —— 真实设备上执行通过**
- **S6 听音：NOT_RUN**（模拟器 `-no-audio`，且无物理扬声器验证）

---

## 1. 关键结论

**Native 库在 Android 运行时真的能加载、能调用、能创建引擎。**

这不是「编译通过」，是 **7 个 instrumentation 测试在真实 Android 15 运行时上执行并全部通过**。

## 2. 设备环境

| 项 | 值 |
|---|---|
| AVD | `mkw_test` |
| Android 版本 | **15**（API 35） |
| ABI | **x86_64** |
| 镜像 | `system-images;android-35;default;x86_64` |
| 加速 | **AEHD 2.2**（Android Emulator Hypervisor Driver） |
| 启动参数 | `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot` |

## 3. Instrumentation 测试结果：**7 / 7 通过**

| 测试 | 证明了什么 |
|---|---|
| `nativeLibraryLoads` | `libmkw_native.so` **成功 dlopen**，`System.loadLibrary("mkw_native")` 无异常 |
| `transportSampleRateIsPositive` | JNI **真实调用成功**并返回合理值 |
| `engineCanBeCreatedAndDestroyed` | `createEngine()` 返回非零句柄，`destroyEngine()` 正常 |
| `engineSampleRateMatchesTransport` | 引擎采样率与 transport 采样率**一致** |
| `engineSnapshotIsReadable` | `snapshot()` 返回非空字段数组 |
| `renderProducesFramesWithoutCrashing` | `renderRealtime()` 渲染 512 帧立体声**未崩溃**，返回非负状态 |
| `nativeLibrariesArePackagedInTheApk` | 四个 `.so` 确实在 APK 内 |

## 4. 启动截图证据

App 在模拟器上**成功启动并显示界面**：

- 标题 `MKW Player`
- 版本 `v0.1.0-alpha01`
- `mCurrentFocus=com.mylittleblover.mkwplayer/com.mylittleblover.mkwplayer.MainActivity`
- 进程存活（`pidof` 返回有效 PID）

截图：`evidence/bootstrap/and-03-launch.png`（1080×2340）

![模拟器启动截图](and-03-launch.png)

## 5. 过程中遇到的三个真实问题

### 5.1 x86_64 模拟器缺硬件加速

```
ERROR | x86_64 emulation currently requires hardware acceleration!
CPU acceleration status: Android Emulator hypervisor driver is not installed
```

**排查**：`HypervisorPresent=False`，但 `VirtualizationFirmwareEnabled=True`、SLAT 支持。

**走过弯路**：先尝试 arm64 镜像绕开加速要求，结果：

```
FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator
        on x86_64 host. System image must match the host architecture.
```

**正解**：安装 `extras;google;Android_Emulator_Hypervisor_Driver`（AEHD 2.2），安装后：

```
accel: 0
AEHD (version 2.2) is installed and usable.
```

> 注意：AEHD 的 `silent_install.bat` 会请求管理员权限。**本次是用户已授权环境下完成的**，不是绕过。

### 5.2 `extractNativeLibs=false` 导致路径误判

第一版测试检查 `applicationInfo.nativeLibraryDir` 下的 `.so`，**失败**：

```
java.lang.AssertionError: libmkw_native.so missing from
  /data/app/~~2QBKashhJTWPCWdSdMkyoQ==/.../lib/x86_64
```

**原因**：AGP 默认 `android:extractNativeLibs="false"`（`aapt2 dump xmltree` 已确认），`.so` 直接从 APK **映射加载**，**不解压到 `nativeLibraryDir`**。

> 这是一个很重要的陷阱：库**明明加载成功了**（其他 6 个测试都过了），但检查解压目录会得出「库不存在」的错误结论。

**修正**：改为检查 **APK 内的 `lib/` 条目**。修正后通过。

### 5.3 测试 APK 覆盖安装导致 Activity 暂时不存在

跑完 `connectedDebugAndroidTest` 后直接 `am start` 报：

```
Error type 3
Error: Activity class {...MainActivity} does not exist.
```

**原因**：instrumentation 测试安装的是 **androidTest APK**，主 APK 状态被重置。

**修正**：重新 `adb install -r` 主 APK 后再启动，成功。

## 6. 这一条证据推翻了什么

AND-02 的证据文档明确写过：

> 「库能否在设备上**加载** —— NOT_RUN」
> 「JNI 调用能否**成功** —— NOT_RUN」
> 「Symbol 存在与运行时行为是两件完全不同的事。」

**现在这两条已经被真实的设备执行取代**：加载成功、JNI 调用成功、引擎可创建、可渲染。

## 7. 仍然 NOT_RUN

| 项 | 状态 | 原因 |
|---|---|---|
| **实际听到声音** | **NOT_RUN** | 模拟器以 `-no-audio` 启动；即使开音频，宿主机扬声器也未经人耳确认 |
| 音质 / 音准 | **NOT_RUN** | 同上 |
| 真实曲目播放 | **NOT_RUN** | 引擎图（compiled graph）尚未构建，也未绑定任何音频源 |
| 长时间稳定性 | **NOT_RUN** | 只跑了单次 512 帧渲染 |
| 真机（ARM）验证 | **NOT_RUN** | 只在 x86_64 模拟器上跑过 |
| 性能 / CPU 占用 | **NOT_RUN** | — |

> **必须说清**：`renderRealtime` 返回非负状态只说明**没崩溃**，不说明**输出的 PCM 是正确的或可听的**。缓冲区内容没有被检查。

## 8. 对交接包状态的影响

| 门禁 | 之前 | 现在 |
|---|---|---|
| `ANDROID_DEVICE` | `DEVICE_BLOCKED` | **`S5_PASS_S6_NOT_RUN`** |
| `ANDROID_BUILD_S4` | PASS | PASS（不变） |

首次获得了**运行时**证据，不再是纯静态证据。
