# 互动音频接入与验收状态（2026-11-12）

> **历史快照说明（已于 `Beta 0.1.6` 全面对齐 MKW v1.8.0 游戏内提取报告）**：本文为 2026-11-12 `alpha06` 阶段验收记录。文中部分早期过渡假设——例如彩虹之路 `Area3 → Final_Pre` 1 秒交叉淡化（实机为 `-220 m` 触发、1.20 s 延迟后 3.00 s 淡出 / 2.50 s 淡入）及 `Area1 → Area2` 未分轨淡出（实机为 `Melody` 立即 0.30 s + `Inst` 小节末 0.30 s）——均已在 `Beta 0.1.6` 中严格按 v1.8.0 逆向参数更新并通过 `VerifiedV180TransitionsTest` 钉死，最新机制以 [MUSIC-TRANSITIONS.md](../MUSIC-TRANSITIONS.md) 为准。

代码对应 alpha06。本页把声源、样本调度、Android 运行与听感证据分开记录。

## 本轮实现

- 普通赛道使用原始 MINF 的候选同步点、目标提前量、明确 Full 进入位置，以及用户确认的全部 Outro 退出采样点。零位置保留为有效进入点；一个 Outro 的多个退出点分别验收。
- Rainbow 使用 Area1 的独立 Melody/Inst 双层、跨区首拍退出/目标相位提前启动、5 个 Outro→Area3_Pre，以及用户确认的 Final_Pre→FinalLapFanfare→Final。
- Rainbow Outro 的下一 Pre 在会话层自动请求；后台服务使用同一会话轮询。实际 ExitCue、源尾音和目标开始由 Native 调度。
- 应用直接复用预览编译器；状态跟随 Native 提交，不把排队请求显示为已进入目标。更换曲目先释放输出 lease；暂停保留排队；Stop/Start 从入口重新加载。
- 打包文件通过音频 SHA-256 还原研究身份，自定义显示标题独立保留。真实 Fast 使用独立声源，未添加无依据的反向切换。

## 已执行验证

- Native 预览：主衔接 334 项（29 条普通赛道、Rainbow、2 个 Pattern2 变体），跨赛道 Outro→Pre 174 项，共 508 项。检查同步采样点、实际 source origin、PCM、削波与流式解码状态。
- JVM：1152 项，0 失败，6 项按环境开关跳过。预览重录属于显式 opt-in 任务，不在普通测试中隐式执行。
- Android 15 x86_64 软件模拟器：30 组真实声源的图、绑定和 PCM 输出通过；应用排队/暂停恢复/Stop 重载通过。最终构建的自动 Outro→Pre AAudio marker=382784 已通过，最终应用控制复核通过（暂停源位置、Native Full 提交、Stop/Start 重新输出帧已验）。
- 独立 Luna 审查已修复选错赛道、自定义标题绕过 MINF、持久错误无法重试三个问题；自动衔接新增审查未发现阻塞问题。

## 每赛道预览数量

| 赛道 | 主预览 | Outro→下一 Pre | 听感记录 |
|---|---:|---:|---|
| FirstCircuit | 9 | 5 | 未逐项标记人工通过 |
| WestCity | 16 | 5 | 未逐项标记人工通过 |
| Coaster | 10 | 7 | 未逐项标记人工通过 |
| DonkeyKong | 10 | 7 | 未逐项标记人工通过 |
| DesertHills | 11 | 4 | 未逐项标记人工通过 |
| ShyGuyBazaar | 11 | 8 | 未逐项标记人工通过 |
| WarioStadium | 12 | 4 | 未逐项标记人工通过 |
| AirshipFortress | 9 | 6 | 未逐项标记人工通过 |
| DKPass | 10 | 7 | 未逐项标记人工通过 |
| SnowMountain | 11 | 8 | 未逐项标记人工通过 |
| IceBuildings | 9 | 6 | 未逐项标记人工通过 |
| WarioShipyard | 10 | 6 | 未逐项标记人工通过 |
| KoopaTroopaBeach | 9 | 5 | 用户已确认 Koopa 修正版正确 |
| DKJungleParkway | 11 | 8 | 未逐项标记人工通过 |
| Central | 11 | 8 | 未逐项标记人工通过 |
| PeachBeach | 11 | 8 | 未逐项标记人工通过 |
| LagoonCity | 11 | 8 | 未逐项标记人工通过 |
| DinoDinoJungle | 11 | 3 | 未逐项标记人工通过 |
| StormClouds | 10 | 7 | 未逐项标记人工通过 |
| RaftingRiver | 10 | 7 | 未逐项标记人工通过 |
| Cave | 11 | 5 | 未逐项标记人工通过 |
| Cinema | 9 | 6 | 未逐项标记人工通过 |
| DinoBones | 11 | 2 | 未逐项标记人工通过 |
| MooMooFarm | 10 | 7 | 未逐项标记人工通过 |
| ChocoMountain | 10 | 7 | 未逐项标记人工通过 |
| ToadFactory | 12 | 1 | 未逐项标记人工通过 |
| Lava | 9 | 3 | 未逐项标记人工通过 |
| BigTree | 12 | 7 | 未逐项标记人工通过 |
| SFCMarioCircuit | 8 | 4 | 未逐项标记人工通过 |
| Rainbow | 16 | 5 | 未逐项标记人工通过 |

Pattern2：Cinema、SnowMountain 各 7 项，包含真实 Full→Fast 及其 Outro 出口。

## 边界与复现

- 预览 WAV 来自同一 Native 引擎的真实渲染，录音可能只保留衔接前后的窗口；引擎仍从声源起点处理。它们不等同于设备扬声器录音。
- 跨赛道测试沿验收清单顺序选择下一条 Pre，Rainbow 最后回 FirstCircuit。这个顺序用于测试，不定义游戏路线。跨赛道应用路线播放不属于本轮接入；Rainbow 内部自动下一 Pre 已接入。
- MooMooFarm Outro06→ChocoMountain Pre 使用 Full cue=1501875 的上下文，Pre 提前于 Outro 33900 帧开始；没有截断 Pre 或改写 MINF 相位。21 个源 marker 前不足 3 秒的案例保留全部可用上下文，不填造音频。
- ChocoMountain 的 Bridge_ToFull 源事件仍为空；使用用户提出的实际 Bridge EOF，并保留独立证据标记。
- 游戏声源、BARS、WAV 和 APK 不提交 Git。生成器与显式预览任务位于 tools/interactive-preview 和 app/src/test/.../interactive。音频身份清单可由 tools/gen_interactive_audio_identities.py 从用户提供的 328 份 BWAV 重建。
- 当前地图截图封面仍为占位素材，正式风景图/其他音乐封面待用户提供。

详细 WAV、日志、SHA 与完整 manifest 位于本会话 /workspace/mkw-recordings、/workspace/mkw-preview-jobs；分赛道 ZIP 和预览索引位于 /workspace/mkw-delivery。

## Rainbow 正确参考与一秒交叉过渡（2026-11-12）

用户更正参考文件为 `RainbowRoad.flac`（Drive `1tiQumSMVC15TujrhDRvjRArCLe9LmPWJ`）。独立全局长窗与 48 kHz 精确互相关均确认 Fanfare 源 0 在参考帧 30695935、Full 源 0 在 30914669，间隔 **218734 帧 / 4.556958333 秒**；三个不重叠窗口相关度均约 0.997。Pre 在 Fanfare 起点结束，不沿用错误 Medley 推导的 Pre 淡出。Full 进入后保留 Fanfare 的真实有限尾音，独立 Fanfare→Full 使用同一交接点。此值来自正确参考，不伪称 MINF 命名事件。

Area3→Final_Pre 从原 label-1 首拍起进行 48000 帧线性交叉淡化。循环 EOF 触发曾截短源声，现为匹配整源 clip/原始循环/采样率的有限 departure fade 保留循环，fadeEnd 关闭 voice；取消会撤销本次拥有的 repeat，含 sourceLeft 前后。普通首拍、循环 EOF、执行前取消的 Native 回归通过，独立审查无剩余阻塞。

- Rainbow 16 项已用最终 Native `eff1154769081f53ab366a9ca27b16afd5e49acba45a4226d7b1a9739062603c` 重录，同步点、源起点、PCM 与长度均通过，零削波/欠载/解码失败。
- 最终 APK 在 Android 15 x86_64 上通过新增采样边界测试（127.399 秒，OK 1 test）：实际交叉重叠 48000 帧、Full 在 Fanfare 后 218734 帧进入、Fanfare 自然结束后仅剩 Full。
- 完整 JVM 1152 项，0 失败，6 个 opt-in 跳过；四 ABI APK 已生成。此前 30 组 Android 输出与会话控制验收仍按各自构建记录保留。
- 正确参考分析见 [数据与方法](2026-11-12-rainbow-correct-reference.md)。先前错误参考与 81620 候选只保留为撤回记录，不属于验收依据。与参考的波形对齐和 Android Native PCM 不等于人工听音或扬声器录音。
