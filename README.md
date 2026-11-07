# MKW Player

独立的《马力欧卡丁车 世界》专用互动音乐播放器（Android / Kotlin / C++ / JNI / Oboe）。

## 状态

阶段 00（OST 盘点工具）进行中。当前里程碑：`OST-01 来源清单与去重`。

## 交接材料

工程执行细则不在本仓库内定义，遵循用户交接的
`MKW_DeepSeek_开发增强交接_v3.0`（docs/00–11、prompts/00–06、templates/tasks.json）。

## 硬约束

- 本仓库**不包含**任何任天堂商业音频、原版 CG 或未授权图标。
  用户本地素材通过 SAF 导入；仓库只保存清单、哈希与映射。
- 单一 Native Runtime / 单一 Transport / 单一 AudioOutput。
- 没有真实运行时证据的行为一律标 `UNVERIFIED`；未执行的测试写 `NOT_RUN`。
- 每个任务一个可回滚小提交。

## 目录

| 路径 | 用途 |
|---|---|
| `mkw_tools/inventory/` | 阶段 00 只读盘点工具 |
| `tests/ost_inventory/` | 阶段 00 测试（含强制反例） |
| `evidence/` | 基线、测试证据与缺口报告 |
| `handoff/` | `status.json`、决策登记、任务看板 |

## 本地开发

```bash
# 使用任意 Python 3.12+；本机验证使用 DSH 内置解释器
python -m pytest tests/ost_inventory -q
```

## 包名

`com.mylittleblover.mkwplayer`（用户授权本轮自行确定）。

固定签名：沿用 PLAYER² 已验证的 `config/signing/player2-debug.p12`
（PKCS12，store/key 口令 `android`，别名 `androiddebugkey`），
**仅在本地与测试 APK 使用，不作为发布签名。**
