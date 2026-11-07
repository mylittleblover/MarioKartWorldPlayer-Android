# G-BOOT · 基线核查（只读）

- **checked_at**: 2026-11-07T19:5x+08:00 (Asia/Shanghai)
- **执行者**: DeepSeek（DSH）
- **本轮性质**: 只读核查 + 工具链探测 + 证据归档。**未创建产品代码、未提交任何 commit、未修改任何源素材。**

---

## 1. 工作区与 Git

| 项 | 值 |
|---|---|
| 工作目录 | `G:\Mario Kart World` |
| 根目录是否 git 仓库 | **否**（无 `.git`） |
| 已存在 git 仓库 | `MKW-PSD-Continue`、`MKW-Video-Studio`、`_export-opt-review-source` |
| `MKW-PSD-Continue` | branch `codex/deepseek/cb6915795d5148c89aa2a5f3eded9274` @ `adeb0ff66ca39cfaad72c5ee2f6f7dabf8bb5e60`，dirty=0，origin=本地路径 |
| `MKW-Video-Studio` | branch `master` @ `d35f3fe17fa0ff9f707a45ea83f47fc5bf45b4c8`，**dirty=96**，无 remote |
| `_export-opt-review-source` | branch `audit` @ `507fbc53968194cfd01b3e80c57ba45d5a67ff99`，dirty=0，无 remote |
| 新 App 工作区 | **未授权/未创建**（合法：本包要求用户先指定） |

**结论**：以上三个仓库均与 MKW 播放器无关（视频工程/PSD/导出审查），不得作为新 App 目标仓库。

## 2. GitHub 上游基线（本轮实际重抓，非沿用快照）

仓库 `mylittleblover/player2-android-new`，7 个分支实测 HEAD：

| 分支 | HEAD SHA | 与 2026-11-07 包内快照对比 |
|---|---|---|
| `codex/player2-feature-development-20261028` | `396ff6f137b00019384c46511c9136fce2c043c3` | **一致（未变）** |
| `main` | `55cee2407504e89aec8bfe0dfa29cd5613ffe065` | 一致 |
| `codex/player2-integration-candidate` | `71bfc7547f6dec49661f5f86998cb12c28e11517` | 一致 |
| `codex/player2-clean-rebuild` | `fa719d098501cb6a2e4b52efd50ab5fc5fcf99c4` | 包内未记录 |
| `codex/player2-core` | `55e571f4a1fe090549b8d490f27defcc63b54dab` | 包内未记录 |
| `codex/player2-docs-governance-20261025` | `a77d751e8de8b876e2247cc4d5b3dfcfb5abc370` | 包内未记录 |
| `codex/player2-ui` | `9c2c6d04f2e8b331a5ed5485178417dd5f86e759` | 包内未记录 |

特性分支 HEAD 提交时间 `2026-10-30T22:05:12Z`，提交信息 `chore(architecture): preserve inventory ordering for pending audio additions`。共 7437 个 tree entry（未截断）。

**接口契约路径实测存在（在 `396ff6f1` 上）**：

- `core/src/main/kotlin/com/mylittleblover/player2/engine/audio/runtime/PlaybackEngine.kt` ✅
- `app/src/main/java/com/mylittleblover/player2/platform/audio/NativePlaybackEngine.kt` ✅
- `app/src/main/java/com/mylittleblover/player2/platform/audio/device/AndroidPlaybackOutput.kt` ✅
- `core/.../engine/wwise/compiler/{RuntimeCompiler,GraphBinaryWriter,GraphProtocolCapabilities}.kt` ✅
- `engine/native/runtime/{RuntimeTransitions,RuntimeCueLookAhead,RuntimeStingers}.cpp` ✅
- `engine/native/voice/Voice.hpp`、`engine/native/music/TimelineEnvelope.hpp` ✅
- `core/.../feature/project/usecase/ProjectPreviewCoordinator.kt` ✅
- `docs/03-audio/format-support.md`、`docs/06-quality/audio-acceptance-procedure.md`、`docs/07-development/implementation-status.md`、`docs/integration/2026-10-29-clip-envelope-acceptance.md` ✅
- 构建系统：`gradlew`、`gradlew.bat`、`build.gradle.kts`、`settings.gradle.kts`、`gradle/wrapper/*`、`CMakeLists.txt` ✅

→ 本包 `docs/01` 列出的路径**全部有效，无影子接口风险**。

## 3. 目标 App 仓库状态（OPEN 项的关键事实）

`mylittleblover/MarioKartWorldPlayer-Android`

| 项 | 值 |
|---|---|
| 可见性 | private |
| 创建时间 | 2026-11-07T10:35:23Z |
| pushed_at | 2026-11-07T10:35:25Z（仅比创建晚 2 秒） |
| size | 0 KB |
| 分支 | **0 个** |
| `contents/` | 404（空仓库，无任何 commit） |
| permissions | push=true, admin=true |

→ **仓库已存在且完全为空，但仓库名与包名尚未经用户确认**（`docs/00` 明令不得猜包名）。视为候选，不视为授权。

## 4. 本机工具链

| 工具 | 状态 |
|---|---|
| Git | ✅ 2.56.0.windows.4（本轮刚装，`C:\Program Files\Git\cmd` 已在系统 PATH） |
| GitHub CLI | ✅ 2.103.3，已登录 `mylittleblover`（scopes: repo, workflow, gist；缺 `read:org`） |
| Python | ✅ 3.12.14（DSH 内置），pytest 9.2.0 ✅，numpy 2.3.5 ✅，zstandard 0.25.0 ✅（本轮安装） |
| Java | ✅ JDK 26.0.1（`C:\Program Files\Java\jdk-26.0.1`）；**`JAVA_HOME` 未设置；JDK 26 对 AGP 大概率过新** |
| Gradle | ❌ 无（仅上游仓库自带 wrapper，未在本机） |
| Android SDK | ❌ 无（`ANDROID_HOME`/`ANDROID_SDK_ROOT` 均未设置，`%LOCALAPPDATA%\Android` 不存在） |
| NDK / CMake | ❌ 无 |
| adb / 真机 | ❌ 无 |
| 7-Zip | ✅ `C:\Program Files\7-Zip\7z.exe` |
| 磁盘 | C: 剩 19.1 GB；G: 剩 48.8 GB |

→ **S4（APK 构建）与 S5/S6（设备/听音）当前不可能执行；AND-01 的 gradleb 部分必须标 BLOCKED。**

## 5. 游戏素材实测（本轮重要发现）

本包 `docs/03` 假设“用户只给了两份 BARS（540+15）”。**实测工作区远超此假设**：

### 5.1 BARS 档案（`romfs\Sound\Resource`，直读 zstd 解压验证）

| 文件 | 磁盘 | 解压后 | BARS 头 entryCount | 内部名族 |
|---|---|---|---|---|
| `M_Bgm.bars.zs` | 5,462,528 | 6,261,248 | **540** | Stage 387 / JukeBox 227 |
| `M_BgmRainbow.bars.zs` | 240,375 | 282,304 | **15** | Rainbow 17 |
| `M_BgmSFC.bars.zs` | 155,789 | 173,120 | **10** | Stage 10 |
| `BgmAll.Product.180.bars.zs` | 4,302,938 | 8,423,488 | **85** | Seq 37 / Demo 5 / Mission 4 / Battle 3 / FirstBoot 1 |

四者合计 **650 条 BARS 记录**。`M_Bgm`(540) 与 `M_BgmRainbow`(15) 精确对应研究基线 **555**，验证通过。

BARS 头结构实测：`"BARS"` + u32 totalSize + u32 count + ...（`M_Bgm` 头 64 字节 hex 已记录：`42 41 52 53 00 8a 5f 00 ff fe 02 01 1c 02 00 00 ...`）。
`Full_ToOutro` 标记：`M_Bgm` 362 次出现 / 8 种字符串；`M_BgmRainbow` 10 / 5 种；合计原始 384 次。**与“174 个具名 Marker”不一致，需 OST-02 按真实 AMTA 结构重新计数，不得沿用 174。**

### 5.2 已解码 PCM（`readable_assets\audio`）

| 项 | 值 |
|---|---|
| 转换范围 | 20,991 个 `.bwav` → PCM `.wav`，报告称缺失 0 |
| 实测 wav 数 | **20,991**（与源 bwav 数一致） |
| 格式实测 | 48 kHz / 2ch / 16-bit（`BGM_Rainbow_Area1_Bridge.wav`：291,646 frames = 6.08 s） |
| WAV chunk | 仅 `fmt `(16) + `data`，**无 `smpl`/`INFO`/loop chunk** |
| 原始 BWAV | `romfs\Sound\Resource\Stream\*.bwav` 20,991 个仍在，magic `BWAV` |
| BGM 流 | `BGM_*.wav` 共 **625** 个（Stage 323 / JukeBox 227 / Seq 34 / Battle 16 / Rainbow 15 / Mission 5 / Demo 4 / FirstBoot 1） |

→ **关键限制**：`.wav` 是纯解码产物，**已丢失 BWAV 原始 loop/采样坐标**。VER-02（Loop 与原始坐标验证）**必须读原始 `.bwav`**，不能拿 `.wav` 当证据，也不能假设源 loop 点。

### 5.3 特别杯解锁 BGM 候选（仅登记，未定论）

| 文件 | 48kHz | 时长 | 备注 |
|---|---|---|---|
| `Dm_RainbowAppears.wav` | 6ch | 72.58 s | **“Special Cup Appears” 最强候选** |
| `Dm_SpecialOp.wav` | 6ch | 27.95 s | 候选 |
| `BGM_Demo_ED_WinningRun_Special.wav` | 2ch | 77.52 s | 候选（结局向） |
| `SE_UI_SpecialCupOpen.wav` / `_Pre` / `_Dialog` | 2ch | 2.90 / 1.09 / 2.14 s | UI 音效，非演出 BGM |

→ 仍为 **UNVERIFIED**，需 L3 行为证据；不得凭文件名定身份。

### 5.4 素材规模

| 目录 | 文件数 | 大小 |
|---|---|---|
| `romfs` | 206,202 | 21.9 GB |
| `readable_assets` | 158,248 | 36.8 GB |
| `releases` | 12,696 | 4.8 GB |
| `MKW-Video-Studio` | 9,059 | 3.8 GB |
| 根目录 `*.7z` | — | 22.5 GB（单一文件） |

## 6. 交接包完整性

- 解压到 `G:\Mario Kart World\_MKW_v3_handoff\MKW_DeepSeek_开发增强交接_v3.0\`
- 46 个文件，与 `meta\file_manifest.csv` 条目数一致
- `docs/00–11`（12 份）✅、`prompts/00–06`（7 份）✅、`templates/`（5 份）✅、`baseline/`（含 authoritative + research）✅
- `templates/tasks.json`：**24 个任务**，全部 `NOT_STARTED`（UTF-8 读取正常；`Get-Content -Raw` 默认编码会乱码，需 `-Encoding UTF8`）
- `templates/STATUS.json`：`nextTaskId = OST-01`，BOOT=`NOT_RUN`
- `meta/QA_report.json`：`original_audio_in_document_zip=false` ✅（文档包不含商业音频）
- **`MKW_PLAYER2_全量资源与Codex交接包_v2.0_2026-10-29.zip` 不在本机**（只有 v3.0 文档包）；`baseline/authoritative/` 已内置其权威子集，但 v2.0 的 `01_交付说明/02_设计资料` 部分缺失。

## 7. 能力表（FEATURE / code path / source SHA / status）

| FEATURE | code path | source SHA | status | evidence | blockers |
|---|---|---|---|---|---|
| 单 Runtime / 单 Transport / 单 Output | `PlaybackEngine.kt`、`NativePlaybackEngine.kt`、`AndroidPlaybackOutput.kt` | `396ff6f1` | IMPLEMENTED（上游自述） | 无本机运行 | 本机无 Android SDK/设备 |
| Clip Envelope Graph13 | 上游 | `331513d9` | HOST_TESTED_LIMITED | 上游文档自述 336/157/295/31 PASS | 无设备听音；本机未复跑 |
| Deferred streamed Cue / Pre-entry | 上游 | `24a9c409` | HOST_TESTED_LIMITED | 上游文档自述 | 负偏移/动态 Switch/部分 MusicalLoop 不支持 |
| Forecast / Bank / 精确 Envelope | 上游 | `396ff6f1` | **WIP_NOT_VERIFIED** | 仅上游计划声称 | 不得当稳定能力 |
| 原始 BARS 解析 | **本包无实现** | — | NOT_STARTED | — | OST-02 待做 |
| BWAV loop / 原始坐标 | **本包无实现** | — | NOT_STARTED | 原始 `.bwav` 在位 | VER-02 待做 |
| 全 OST 索引 | — | — | NOT_STARTED | BARS 650 条已可读 | OST-01..04 |
| MKW 行为 Profile（L3） | — | — | MISSING_FULL_L3 | — | 需游戏运行时证据 |
| 新 App 工程 | — | — | 未授权 | 候选空仓库存在 | 需用户确认仓库/包名 |
| Android 真机验收 | — | — | NOT_RUN | — | 无设备 |

## 8. 阻塞项

1. **G-BOOT 未通过**（本文件即其产物，但 `worktree`/`target app workspace` 两栏仍为空，因为新工作区未授权）。
2. **新 App 仓库地址 + 正式包名未确认**：`MarioKartWorldPlayer-Android` 是强候选但未获用户确认；`docs/00` 明令不得猜包名。
3. **无 Android SDK / NDK / Gradle / adb / 真机** → S4/S5/S6 全部 `DEVICE_BLOCKED`。
4. **JDK 26 过新**，AGP 需要 JDK 17/21；`JAVA_HOME` 未设。
5. **全 OST 未验证**：虽有 650 条 BARS + 20,991 段 PCM，但菜单/剧情/短音乐/道路覆盖尚未审计 → `INVENTORY_INCOMPLETE` 仍为正确状态。
6. **特别杯解锁 BGM 身份未核实**（L3 缺失）。
7. **v2.0 全量资源包缺失**于本机（若需要其历史设计资料）。

## 9. 本轮未做（明确 NOT_RUN）

- 未运行任何 Android/Gradle 构建（无 SDK）
- 未运行任何上游 CTest/pytest（未 clone 上游仓库；clone 属大体积操作，需授权）
- 未创建 `mkw_tools/`、未写任何任务卡代码
- 未修改任何素材文件或 git 仓库
- 未提交 commit（`NOT_COMMITTED`）
