# AND-02 · Native Audio Runtime 接入（真实构建通过）

- **checked_at**: 2026-11-07T23:10+08:00 (Asia/Shanghai)
- **证据级别**: **S4 Build** —— 四 ABI 真实构建通过，JNI 符号已核。
- **S5 / S6：NOT_RUN**（无真机、无听音）

---

## 1. 授权依据

用户于 2026-11-07 明确选择**方案 B**：「明确授权复制 PLAYER² 源码到新仓库」。

记录在仓库根 `LICENSE`，含：
- 授权事实与日期
- 版权归同一个人，无需第三方授权
- 本项目**尚未选择公开分发许可** → 默认 All Rights Reserved
- 第三方组件许可证清单

同时新增 `NOTICE-THIRD-PARTY.md`，逐项列明 13 个第三方组件及其许可证与文本文件。

## 2. 复制了什么

| 来源 | 目标 | 文件数 | 大小 |
|---|---|---|---|
| `engine/native/` | `engine/native/` | 214 | 0.9 MB |
| `third_party/` | `third_party/` | 382 | 6.5 MB |
| `app/src/main/cpp/` | `app/src/main/cpp/` | 14 | 0.1 MB |
| `app/src/main/assets/licenses/` | 同 | 12 | — |

**许可证文本 12 份已打进 APK**（实测确认在 `assets/licenses/` 下）。

## 3. 构建结果

**命令**：`gradlew :app:assembleDebug --no-daemon`
**结果**：`BUILD SUCCESSFUL in 11m 12s`

| 项 | 值 |
|---|---|
| APK | **28.73 MB** |
| SHA256 | `e25b64cd90139e7679866c88e73c52fba376e47a695420c7c6809ad8c3e7f331` |

### 四 ABI 全部产出真实 .so

| ABI | libmkw_native.so | libgme.so | libc++_shared.so | liboboe.so |
|---|---|---|---|---|
| arm64-v8a | 3256.1 KB | 201.3 KB | 1262.6 KB | 319.8 KB |
| armeabi-v7a | 2296.4 KB | 147.4 KB | 852.4 KB | 207.9 KB |
| x86 | 3818.6 KB | 237.5 KB | 1225.7 KB | 299.8 KB |
| x86_64 | 3568.5 KB | 218.4 KB | 1222.7 KB | 316.1 KB |

## 4. 构建过程中修复的四个真实问题

### 4.1 CRLF 破坏 pinned SHA256（两次）

Windows 的 `core.autocrlf=true` 把 vendored 源码转成 CRLF，导致：

- `minimp3.h`：期望 `57e437c5...`，实际 `29735314...`（1865 处 CRLF）
- Opus：345 个受哈希文件里 **26 个**不匹配

**修复**：
1. 按 manifest 逐文件做 CRLF→LF 归一（third_party 共 348 个文件）
2. 新增 `.gitattributes`，把 `third_party/**` 与 `app/src/main/cpp/**` 标为 `-text`，**从根源上禁止**再次发生

### 4.2 gme_shared 链接失败

Game_Music_Emu 混合 C（`emu2413.c`）与 C++ 源码，CMake 选了 C 驱动当链接语言，不链 libc++，导致 `__cxa_guard_acquire` / `operator new` / `__cxa_pure_virtual` 未定义。

**修复**：显式 `LINKER_LANGUAGE CXX` + `target_link_libraries(c++_shared)`。

### 4.3 mkw_native 链接失败

同类问题（`std::__ndk1::to_string` 未定义）。

**修复**：`target_link_libraries(mkw_native PRIVATE c++_shared)`。

### 4.4 JNI 符号绑定

原生库导出 **74 个** `Java_com_mylittleblover_player2_*` 符号。JNI 符号由**包名+类名**决定，因此 Kotlin 侧必须留在 `com.mylittleblover.player2.platform.audio.NativeAudioLibrary`。

**决定**：保留该包名，并在源码注释中写明原因。改名需要改 7 个 C++ 文件里的 74 个函数名，收益为零、且会造成首次调用时 `NoSuchMethodError`。

## 5. JNI 契约测试（新增）

用 `llvm-nm -D --defined-only` 从构建出的 `arm64-v8a/libmkw_native.so` 提取符号表，写入 `app/src/test/resources/jni-symbols.txt`（41 条 `NativeAudioLibrary` 符号）。

测试 `NativeAudioLibraryContractTest`（5 个）：

| 测试 | 保护什么 |
|---|---|
| `every declared function exists in the native library` | Kotlin 声明与 .so 符号**必须**一一对应 |
| `native library exports no fewer functions than declared` | 反射确实找到了 native 方法 |
| `symbol manifest is not empty` | 清单没被截断 |
| `jni symbol name keeps the player2 package` | 包名不能被改 |
| `object name keeps the native library class name` | 类名不能被改 |

> 用**反射**读 `declaredMethods` 中 `native` 标志的方法，而不是正则扫源码 —— 第一版正则方案因为资源未打包直接 NPE，已修正。

## 6. 测试总数

| 套件 | 数量 | 结果 |
|---|---|---|
| Python（`tests/`） | 128 | 全过 |
| Kotlin JVM（`StableIdsTest`） | 13 | 全过 |
| Kotlin JVM（`NativeAudioLibraryContractTest`） | 5 | 全过 |
| **合计** | **146** | **0 失败** |

## 7. 明确未验 / NOT_RUN

| 项 | 状态 | 原因 |
|---|---|---|
| 库能否在设备上**加载** | **NOT_RUN** | 无真机/模拟器，`System.loadLibrary` 从未执行 |
| JNI 调用能否**成功** | **NOT_RUN** | 符号存在 ≠ 运行时可调用 |
| 能否**出声** | **NOT_RUN** | 无设备 |
| `connectedDebugAndroidTest` | **NOT_RUN** | 同上 |
| 引擎图（`loadCompiledGraph`） | **NOT_RUN** | 需真实编译后的 graph 包 |
| 许可证是否最终正确 | **未审** | 我不是律师；`NOTICE-THIRD-PARTY.md` 是工程声明，不是法律意见 |

> **最重要的一条**：本证据只证明「**能编译、能打包、符号对得上**」。
> 它**不证明**「能运行」。Symbol 存在与运行时行为是两件事。

## 8. AND-02 状态

**PARTIAL（S4 达成）**

- 达成：源码接入、四 ABI 构建、许可证合规、JNI 契约守卫
- 未达成：任何运行时验证
