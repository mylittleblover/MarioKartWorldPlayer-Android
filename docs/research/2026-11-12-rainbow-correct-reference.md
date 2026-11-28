# RainbowRoad 正确参考录音对齐

分析使用用户更正提供的 `RainbowRoad.flac`，不是先前发错的文件。FLAC SHA-256 为 `49b00843…7e11328`，大小 192,546,927 bytes；48 kHz 双声道，共 40,101,265 帧。Native 源音频分别解码，先对整段参考做 8 kHz 单声道全局搜索，再对全局候选做 48 kHz 双声道精对齐。所有秒数都由帧数除以 48,000 计算。

| 音源 | 源帧 0 对应的参考帧 | 时间 | 验证 |
|---|---:|---:|---|
| Final_Pre | 29,529,353 | 615.194854167 s | 多个不重叠窗口同一偏移；源 2–22 s 的窗口相关系数为 .9903–.9926 |
| FinalLapFanfare | 30,695,935 | 639.498645833 s | 全局 0–1、1–2、2–3、0–3 s 窗口收敛；48 kHz 精对齐相关系数 .9969–.9979 |
| Final | 30,914,669 | 644.055604167 s | 0–1、1–2、3–4 s 窗口同一偏移；相关系数 .9978–.9980 |

## Pre 到 Fanfare

Final_Pre 的源长和 MINF loopEnd 都是 1,166,580 帧。它的源帧 0 到 Fanfare 源帧 0 相隔 1,166,582 帧，只多 2 帧。因此参考中 Pre 完成一遍后几乎紧接着进入 Fanfare。

我也检查了 Pre 如果在 loopEnd 后回到 loopStart=291,645 的假设。Fanfare 开始后 0–4 秒窗口中，loop-phase Pre 与参考相关系数约为 −.014 到 .022，拟合增益约为 −.006 到 .010；Fanfare 源拟合增益约为 3.94–3.99，相关系数约为 .972–.985。参考与“Pre 已结束、Fanfare 正在播放”吻合，未见 Pre 循环继续叠在 Fanfare 下。

## Fanfare 到 Final

Final 源帧 0 比 Fanfare 源帧 0 晚 **218,734 帧（4.556958333 秒）**。Final_Pre 和 Final 的 MINF tempo 都是 158.00035095 BPM；12 拍（3 个 4/4 小节）对应 218,733.691 帧，实测间隔仅多约 **0.309 帧**。相同间隔约为 12.911 个 Fanfare 的 170 BPM 拍；Fanfare 的最近拍点是第 220,235 帧，Final 提前 1,501 帧进入。三个角色的 MINF events 都为空。

这说明这份参考混音中的时间关系与 158 BPM 下的 12 拍高度吻合，但单凭参考无法证明它是游戏内固定延迟或某个 MINF 事件定义。Final 进入时，Fanfare 已播放 4.556958 秒，距其源文件结束仍有 120,090 帧（2.501875 秒）；不过 Fanfare 已进入快速衰减尾部，源 RMS 从 4.25 秒的 458.61 降到 5 秒的 9.28。该时间重叠不代表两条强旋律持续叠放。

完整哈希、每个窗口的对齐分数、loop 相位检查和裁剪元数据见 [alignment.json](/workspace/mkw-planning/rainbow-correct-reference/alignment.json)。12 秒对照片段是 [Rainbow-correct-reference-fan-final-636-648s.wav](/workspace/mkw-delivery/Rainbow-correct-reference-fan-final-636-648s.wav)：Fanfare 源帧 0 在片段 3.498646 秒，Final 源帧 0 在 8.055604 秒。

## MKW v1.8.0 游戏内提取报告完整对齐（2026-11-28，已实装于 `Beta 0.1.6`）

经 `M_BgmRainbow.bars.zs`（全部 15 条 2 声道流）、`Default.game__SoundBgmSetting.bgyml` 与 `main.bin`（`0x024396c0` / `0x0243bff0` / `0x0243d060..0x0243d8e0`）全量逆向，彩虹之路全段衔接规则已全部确认：

- **进场与 `Area1_Pre`**：连接赛道 `Central → Rainbow` 跳过点唱机直接起播 `BGM_Rainbow_Area1_Pre`（94.18 s 循环，`Pre_TransitionGrid: 16`），经 `BGM_Rainbow_Area1_Bridge`（`Bridge_ToFull = 218,734` 帧 = `4.557 s`）进入 Area 1。
- **`Area1_Inst` + `Area1_Melody` 双轨锁相与 `Area1 → Area2` 分轨淡出（`0x0243d500`）**：`Area1_Inst` 与 `Area1_Melody` 逐样本锁定 **`7,528,085` 帧**（158 BPM，`loop = 1,986,831..7,528,085`）。越过第 2 段检查点时，**`Area1_Melody` 立即 `0.30 s` 淡出（delay = 0）**，**`Area1_Inst` 播完当前小节后再 `0.30 s`（`14,400` 帧）淡出**，同时 `Area2`（73.15 s）按 `-Area2.phase` 强拍切入。
- **`Area2 → Area3_Pre → Area3`**：`Area2` 经 5 条 `Full_ToOutro00..04` 进入 `Area2_Outro00..04`，再按各自 `Outro_ToNextPre` 无缝留尾接入 `Area3_Pre`（`Pre_TransitionGrid: 4`）；并在 `Rainbow_00_Fix`（`Y = 4,549 m`）复用 1 枚 `SystemObj_SnowMountainSoundArea_00` 触发 Area 3 声场切换。
- **`Area3 → Final_Pre`（`0x02439790` / `0x0243c1f4`）**：领先车手距终段检查点 **`-220.0 m`** 时触发——**`Area3` 保持原音量 `1.20 s`（`57,600` 帧）后执行 `3.00 s`（`144,000` 帧）淡出**，同时 **`Final_Pre`（24.30 s）立即执行 `2.50 s`（`120,000` 帧）淡入**（取代了早期 `alpha06` 的 1 秒交叉淡化假设）。
- **`Final_Pre → FinalLapFanfare → Final`（`0x0243d8e0`）**：`Final_Pre` 执行 **`0.10 s`（`4,800` 帧）**淡出；`BGM_Rainbow_FinalLapFanfare`（7.06 s）按 `Rainbow_FanfareDelay` = **`0.20 s`（`9,600` 帧）**延迟起播；再经上文实测一致的 **`218,734` 帧（`4.557 s`，158 BPM 下精确 12 拍）**于总偏移 `228,334` 帧切入 `BGM_Rainbow_Final`（86.70 s），同时号角余尾自然叠加衰减。
