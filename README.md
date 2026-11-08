# MKW Player

独立的《马力欧卡丁车 世界》专用互动音乐播放器（Android / Kotlin / C++ / JNI / Oboe）。

## 当前状态

**0.1.0-alpha01** —— 可以在真机上播放游戏里的赛道音乐。

| 能力 | 证据级别 |
|---|---|
| 四 ABI APK 构建 | S4 |
| 真机播放 + 人耳确认 | **S6** |
| 解码逐样本正确（maxdiff = 0） | L2 |
| 后台播放 + 通知栏控制 | S6 |
| 曲库持久化 | S5 |
| 内置 650 条曲目目录 | S5 |

完整的门禁与任务状态见 `handoff/status.json`，一页总结见 `evidence/SUMMARY.md`。

| 文档 | 内容 |
|---|---|
| `docs/USAGE.md` | 怎么装、怎么加曲目、界面各项什么意思 |
| `docs/ARCHITECTURE.md` | 分层理由，以及每一层拒绝了什么 |
| `CHANGELOG.md` | 版本变更 |
| `evidence/SUMMARY.md` | 能力、限制、缺陷、测试数一页汇总 |

## 明确不支持

| 项 | 影响 |
|---|---|
| **BWAV codec 2** | 253 个文件（含 227 首 JukeBox、22 首 Cutscene）无法播放。容器结构已解，包边界未找到，**故意不猜** |
| 音频焦点 | 来电不会自动暂停 |
| 完整原声带目录 | 只覆盖 499 个归档中的 4 个 |
| 多曲目听音 | 仅 `BGM_Stage_Coaster` 有人耳确认 |

## 硬约束

- 本仓库**不包含**任何任天堂商业音频、原版 CG 或未授权图标。
  仓库只保存清单、哈希与映射；音频由用户通过 SAF 在运行时导入。
- 单一 Native Runtime / 单一 Transport / 单一 AudioOutput。
- 没有真实运行时证据的行为一律标 `UNVERIFIED`；未执行的测试写 `NOT_RUN`。
- 每个任务一个可回滚小提交。

## 目录

| 路径 | 用途 |
|---|---|
| `app/` | Android 应用（Kotlin + Compose + 原生解码器） |
| `app/src/main/cpp/` | C++ 运行时：解码器、引擎、Oboe 输出、JNI 桥 |
| `app/src/main/assets/catalog.json` | 650 条曲目目录（由 `tools/gen_catalog.py` 生成） |
| `third_party/` | 按 SHA256 固定的 vendored 依赖 |
| `mkw_tools/` | BARS / BWAV / 资产分析工具（Python） |
| `tools/` | 证据推导脚本，见 `tools/README.md` |
| `tests/` | Python 测试 |
| `evidence/` | 基线、各阶段证据、`SUMMARY.md` |
| `handoff/` | `status.json`、`decision-register.json` |
| `config/signing/` | 固定测试签名（**不可用于发布**） |

## 本地开发

```bash
# Python 测试
python -m pytest tests -q

# JVM 测试
./gradlew :app:testDebugUnitTest

# 设备测试（需要一台在线设备或模拟器）
./gradlew :app:connectedDebugAndroidTest
```

模拟器**不要**加 `-no-audio`，否则 `AudioOutput` 永远拿不到设备、时序面板恒为 `NO DATA`。

## 包名

`com.mylittleblover.mkwplayer`（用户授权本轮自行确定，见 `handoff/decision-register.json` D-004/D-006）。

## 签名

`config/signing/mkwplayer-debug.p12`（PKCS12，store/key 口令 `android`，
别名 `mkwplayerdebugkey`，证书 SHA-256 见 `config/signing/debug-cert.sha256`）。

**这是测试身份，仅用于本地与测试 APK，绝不用于发布。**
