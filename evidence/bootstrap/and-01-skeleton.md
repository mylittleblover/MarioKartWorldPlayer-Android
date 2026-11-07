# AND-01 · 独立 Android 工程与 Native 复用审查

- **checked_at**: 2026-11-07T22:35+08:00 (Asia/Shanghai)
- **证据级别**: **S4 Build** —— 真实构建通过。**S5/S6 设备与听音 NOT_RUN**

---

## 1. 工程已建立并真实构建通过

| 项 | 值 |
|---|---|
| 仓库 | `mylittleblover/MarioKartWorldPlayer-Android` |
| applicationId | `com.mylittleblover.mkwplayer` |
| namespace | `com.mylittleblover.mkwplayer` |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| AGP / Kotlin / Compose | 8.9.2 / 2.1.20 / BOM 2025.04.01 |
| Gradle | 8.11.1（wrapper，与已验证的上游一致） |

**命令**：`gradlew :app:assembleDebug --no-daemon`
**结果**：`BUILD SUCCESSFUL`

| 产物 | 值 |
|---|---|
| APK 大小 | 9,853.9 KB |
| SHA256 | `bd256a54e95fb646fc0c43457a94a55fc73340ac126b54f8141640c160d6c49c` |
| 签名证书 DN | `CN=MKW Player Debug, O=Shenggui Development, C=CN` |
| 证书 SHA-256 | `dbe94b6e38a6bcd76fc399dee55bb2865544f12981f00b7aa51c3895805ecc88` |

## 2. 固定签名

生成**独立于 PLAYER²** 的专用调试密钥库：

```
config/signing/mkwplayer-debug.p12
SHA256: 9137098329bfccce2d498029cdef3af19773dbda2ee937dd5a44b4b82210fce9
alias:  mkwplayerdebugkey
storeType: PKCS12
有效期: 10950 天
```

在 `app/build.gradle.kts` 中以 `fixedDebug` 签名配置绑定。

> **明确边界**：这是**测试身份**，绝不能用于正式发布。发布签名必须单独生成并离线保管。

## 3. JVM 单元测试：13 passed

`gradlew :app:testDebugUnitTest` → **13 tests, 0 failures, 0 errors, 0 skipped**

覆盖的是 ID 体系的**不变式**：

| 测试 | 保护什么 |
|---|---|
| `same name on different entries never collapses` | 同名不同条目**必须**是不同 ID（真实档案里 M_Bgm 有 24 组重名） |
| `marker key does not embed an unverified outro name` | ID **不得**依赖未确认的 Outro 名 |
| `entry keys differ by ordinal` | 序号参与身份 |
| `entry keys are stable across construction` | 同一坐标多次构造必须相等 |
| `distinct kinds never collide on one slug` | 不同类别同 slug 不碰撞 |
| 各 reject 用例 | 非法输入必须抛异常，不静默 |

## 4. Native 复用审查（只读）

上游 `player2-android-new` 的 Native 接口事实：

| 项 | 事实 |
|---|---|
| 引擎入口 | `engine/native/api/EngineApi.hpp`（71 行，C++ 命名空间 `player2::api`） |
| 生命周期 | `createEngine()` / `destroyEngine()` / `loadCompiledGraph()` / `claimEngineOutput()` |
| 命令模型 | `EngineCommandKind` 18 种，`EngineCommand` 带 `TransportFrame at`（`-1` = 下一帧） |
| 状态语义 | `EngineStatus` 11 种，含 `MissingCue / DeadlineMiss / SourceMismatch / QueueFull` |
| 单输出约束 | `claimEngineOutput` 注释明确「一次只能有一个 output consumer lease」 |
| 架构不变量 | 单 Runtime / 单 Transport / 单 Output，`renderEngineDevice` 不引入第二时钟 |
| JNI 面 | `NativeAudioBridge.cpp`(22KB) 等 7 个文件 |

**与交接包 `docs/01` 列的契约路径逐一核对：全部存在**（见 `baseline.md` 第 2 节）。

### ⚠️ 许可证状态：**未确认**

- 上游仓库**根目录没有 LICENSE 文件**
- 只有 `third_party/*` 各自的上游许可证（clHCA ISC、libopenmpt BSD、opus BSD、stb MIT 等）
- `docs/specs/02.md` 提到「PixelPlayer 源码所附许可为 Proprietary License」，说明团队对第三方许可有意识，但**本仓库自身许可未见声明**

**结论：`LICENSE_UNVERIFIED`。** 在明确许可之前，**不得复制上游源码到本仓库**。当前工程是**独立编写**的（只有 Gradle 配置与 Kotlin ID 类型），未包含任何上游代码。

## 5. 已建立的内容

```
app/src/main/kotlin/.../registry/StableIds.kt      ID 体系
app/src/test/kotlin/.../registry/StableIdsTest.kt  13 个不变式测试
app/src/main/kotlin/.../MainActivity.kt            最小 Compose 入口
gradle/wrapper/                                     与上游同版本 8.11.1
config/signing/mkwplayer-debug.p12                  固定测试签名
```

## 6. ID 体系的设计依据（来自本轮实测）

| 实测事实 | 对 ID 设计的约束 |
|---|---|
| M_Bgm 有 24 组重名条目，一个名字对应 6 个条目 | **不能用名字当主键** |
| `markerId` 只有 14/197 跨赛道重复，且映射矛盾 | **不能用 markerId 当名字** |
| 位置→Outro 名绑定两份分析材料冲突 127/165 | **ID 不得内置 Outro 名** |
| 每个条目有唯一的 `(ordinal, amtaOffset)` | **用结构坐标当身份** |

因此 ID 定义：

```
EntryId  = (archiveSha256, ordinal, amtaOffset)
MarkerId = (EntryId, index, markerId, sourceSamplePosition)
ResourceId = (kind, slug)   # 逻辑资源，与 EntryId 多对多
```

## 7. 明确未做 / NOT_RUN

| 项 | 状态 | 原因 |
|---|---|---|
| `connectedDebugAndroidTest` | **NOT_RUN** | 无真机/模拟器 |
| S5 Instrumentation | **NOT_RUN** | 同上 |
| S6 真实听音 | **NOT_RUN** | 同上 |
| Native 库接入 App | **未做** | 许可证未确认，且需先完成 Runtime Adapter 设计 |
| 任何播放功能 | **未做** | 本任务是工程骨架，不是播放器 |

## 8. 与 AND-01 任务卡的偏离

任务卡要求「只读 Native reuse/license/API 的当前 HEAD 审查与独立 App 最小 Port」。

- **做到了**：API 审查（逐路径核对）、license 审查（发现未声明）、独立 App 最小骨架、ID 合同
- **没做到**：Native Port。理由见 §4，**许可证未确认时不复制上游代码**。这符合 `docs/09`「不得提交未授权文件」的要求。

**AND-01 状态：PARTIAL** —— 工程与 ID 完成，Native 接入被许可证阻塞。
