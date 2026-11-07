# 工具链验证证据（S4 Android Build）

- **checked_at**: 2026-11-07T20:20+08:00 (Asia/Shanghai)
- **执行者**: DeepSeek（DSH）
- **性质**: 真实 Gradle 构建。**不是设备验收，不含听音。**

---

## 1. 工具链组件（全部实测可用）

| 组件 | 版本 | 路径 |
|---|---|---|
| JDK（Gradle 启动 JVM） | Temurin 21.0.13+9 | `_toolchain\jdk-21.0.13+9` |
| JDK（项目 toolchain 要求 17） | Temurin 17.0.21+9 | `_toolchain\jdk-17.0.21+9` |
| Android SDK cmdline-tools | 19.0 | `_toolchain\android-sdk\cmdline-tools\latest` |
| platform-tools / adb | 37.0.1 | `_toolchain\android-sdk\platform-tools` |
| platforms | android-35 | — |
| build-tools | 35.0.0 | — |
| NDK | 27.0.12077973 | — |
| CMake | 3.22.1（含 ninja） | — |
| Gradle | 8.11.1（wrapper） | 上游仓库自带 |

环境变量已持久化到**用户级**：`JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`，PATH 已补五项。
（机器级写入被拒绝：`SetEnvironmentVariable(..., "Machine")` 抛 `SecurityException`，当前会话无管理员权限。）

---

## 2. 构建结果

**目标**：`mylittleblover/player2-android-new` @ `331513d9f340a822285d3758308845afc9c0250d`
（已知良好 checkpoint；选它是因为 HEAD `396ff6f1` 编译不过，见第 4 节）

**命令**：
```powershell
$env:JAVA_HOME="G:\Mario Kart World\_toolchain\jdk-21.0.13+9"
$env:ANDROID_HOME="G:\Mario Kart World\_toolchain\android-sdk"
gradlew.bat -p <worktree> :app:assembleDebug --no-daemon --console=plain
```

**结果**：`BUILD SUCCESSFUL in 7m 29s` — 48 actionable tasks

| 产物属性 | 值 |
|---|---|
| 路径 | `app\build\outputs\apk\debug\app-debug.apk` |
| 大小 | 41.76 MB |
| SHA256 | `9c0eb8c85ca757d6eca24fc293614a7e1486cb838b4127b000211ab1fa16cd18` |
| 签名校验 | `apksigner verify` → **Verifies**（v2 scheme） |
| 证书 DN | `CN=Android Debug, O=Shenggui Development, C=CN` |
| 证书 SHA-256 | `1d6bae459d23edb2485f506def5f3dcf9b789cabab34d1b2cdcc929a3eb44e49` |
| 密钥 | RSA 2048 |

**四 ABI 原生产物（APK 内实测）**：

| ABI | libgme.so | libplayer2_native.so | libc++_shared.so |
|---|---|---|---|
| arm64-v8a | 201.8 KB | 3226.8 KB | 1262.6 KB |
| armeabi-v7a | 147.9 KB | 2274.0 KB | 852.4 KB |
| x86 | 238.0 KB | 3781.4 KB | 1225.7 KB |
| x86_64 | 218.9 KB | 3535.5 KB | 1222.7 KB |

→ **S4（Android Build）PASS**。规划中的四 ABI 目标全部达成。

---

## 3. 为达成构建所做的三处本地补丁（均为环境缺陷，非产品缺陷）

补丁只存在于 `_toolchain\` 下的临时 worktree，**未提交、未推送、未改动远端任何分支**。

### 补丁 1 — `core.autocrlf` 破坏 pinned 依赖校验

`third_party/libopenmpt/mo3-codecs/CMakeLists.txt` 用 SHA256 锁定 `minimp3.h`。
Windows 默认 `core.autocrlf=true` 把 LF 转成 CRLF（1865 处），哈希对不上，CMake 直接 `FATAL_ERROR`。

实测：
- 工作区文件（CRLF）：`29735314aa1814ca4b089a3605202c1f57af67b3413603dfb246402eff413fd7`
- LF 归一化后：`57e437c5c1f0e8b243885d3929c8973b5e6c778451e0100ab4251d19915cb3ad` ✅ 与 pinned 值**精确一致**

**处理**：`git config --global core.autocrlf false`，重建 worktree。
**根治建议**：仓库应加 `.gitattributes` 显式 `* -text` 或对 third_party 二进制/锁定文件设 `binary`，否则任何 Windows 贡献者都会踩。

### 补丁 2 — `gme_shared` 缺 libc++

`third_party/game-music-emu/CMakeLists.txt` 里 `gme_shared` 是 C/C++ 混合目标（含 `ext/emu2413.c`）。
上游 gme 的 CMakeLists 写了 `set(CMAKE_CXX_FLAGS "... -fno-rtti -fno-exceptions")`，而在 NDK 下 CMake 把它选成了 C 链接语言，链接行只有 `-latomic -lm`，**完全没有 libc++**：

```
ld.lld: error: undefined symbol: __cxa_guard_acquire
ld.lld: error: undefined symbol: operator new(unsigned long)
ld.lld: error: undefined symbol: std::__ndk1::__libcpp_verbose_abort(char const*, ...)
ld.lld: error: undefined symbol: __cxa_pure_virtual
```

**处理**：
```cmake
set_target_properties(gme_shared PROPERTIES LINKER_LANGUAGE CXX)
target_link_libraries(gme_shared PRIVATE c++_shared)
```
（`-stdlib=libc++` 单独试过，**不够** — NDK 的 clang 驱动需要显式链接 `libc++_shared.so`。）

### 补丁 3 — `player2_native` 同样缺 libc++

修好 gme 后 `player2_native` 暴露同类问题：

```
ld.lld: error: undefined symbol: std::__ndk1::to_string(int)
  >>> referenced by StreamSourceBridge.cpp:27
```

**处理**（`app/src/main/cpp/CMakeLists.txt`）：
```cmake
if(ANDROID)
    target_link_libraries(player2_native PRIVATE c++_shared)
endif()
```

---

## 4. 上游 HEAD `396ff6f1` 编译失败（可复现）

**命令**：`gradlew.bat -p <feature-branch> :app:assembleDebug`
**结果**：`:core:compileKotlin FAILED`

```
e: MusicValidation.kt:26:17 Unresolved reference 'quantized'.
e: MusicValidation.kt:26:41 Cannot infer type for this parameter.
e: MusicValidation.kt:28:39 Unresolved reference 'quantized'.
e: MusicValidation.kt:28:66 Cannot infer type for this parameter.
```

**git 溯源结论**：

`331513d9` 中 `quantized` 是 `validateMusic` 内的局部函数（第 13 行）：

```kotlin
fun quantized(beats: Double, timingOwner: ObjectId = id): Long? {
    if (!frameFits(beats, timingOwner)) return null
    val timing = project.timingFor(timingOwner)
    return (beats * project.sampleRate * 60.0 / timing.bpm * 4.0 / timing.meter.denominator).roundToLong()
}
```

`396ff6f1` 的重构把 envelope 校验抽成 `EnvelopeValidation.kt::envelopePointsValid`，**删掉了这个局部函数和第 1 行的 `import kotlin.math.roundToLong`**，但**第 26、28 行两处 `quantized(...)` 调用没删**。

→ 这不是配置问题、不是环境问题，是**重构遗漏的编译错误**。与交接包 `docs/01` 判断的「WIP 未整体验证」完全吻合，且现在有了硬证据。

---

## 5. 明确未做 / NOT_RUN

| 项目 | 状态 | 原因 |
|---|---|---|
| `connectedDebugAndroidTest` | **NOT_RUN** | 无真机、无模拟器（`adb devices` 为空） |
| S5 Instrumentation | **NOT_RUN** | 同上 |
| S6 真实听音 | **NOT_RUN** | 同上，且无音频输出设备接入声明 |
| 上游 CTest / Host JNI 测试 | **NOT_RUN** | 未在 `331513d9` 上运行 native host 测试（本轮目标是验证 Android 构建链路） |
| 上游测试套件 JVM | **NOT_RUN** | 同上 |

> **S4 PASS 不等于产品可用。** APK 能构建、能签名，只证明构建链路通了。
> 单 Runtime/单 Transport 的正确性、Cue/Bridge/Outro 的实际音频行为，全部仍是 `NOT_VERIFIED`。
