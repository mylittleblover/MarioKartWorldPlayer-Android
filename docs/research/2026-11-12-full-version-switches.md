# Cinema / SnowMountain 版本切换

> **MKW v1.8.0 游戏内提取报告对齐校正（2026-11-28，已实装于 `Beta 0.1.6`）**
>
> 经《马力欧卡丁车 世界》v1.8.0 实机逆向（`Default.game__SoundBgmSetting.bgyml`、`main.bin` `SoundBgmMgr::switchDynamicStagePattern` `0x0243b078` 与 `BgmAll.Product.180.bars.zs`）核实，本文早期 `alpha08` 阶段统一采用“1 秒（`48,000` 帧）交叉淡化”的设定已按零售版 `.bgyml` 生效值完全修正：
> - **底层 `.bgyml` 参数差异**：C++ `.rodata` 默认值 `Stage_ChangePatternFade = 1.0 s`，但零售版 `Default.game__SoundBgmSetting.bgyml` 将其覆写为 **`0.5 s`（`24,000` 帧）**；`Stage_ChangePatternFade_Snow` 保持 **`2.0 s`（`96,000` 帧）**。
> - **`Cinema`（幽灵影院，双向 `0.5 s`）**：
>   - `Main` / `Pattern2` 逐样本锁定 **`2,880,000` 帧（210 BPM）**，`Fast` / `Fast_Pattern2` 逐样本锁定 **`2,562,711` 帧（236 BPM）**。
>   - `Main ↔ Pattern2` 与 `Fast ↔ Fast_Pattern2` 双向均采用 **`0.5 s`（`24,000` 帧）**同相位交叉淡化；触发器为 `SystemObj_CinemaInsideArea_00`（配合 `Cinema_Hall`/`Cinema_InnerScreen` 混响覆盖）。
>   - `Cinema` 与 `Cinema_Pattern2` 均合法挂载 **`Outro00..05`（6 个尾奏）**；`Pre`/`Pre_Short` 采用 `Pre_TransitionGrid: 8`（8 拍网格），无 `_Bridge`。
> - **`SnowMountain`（罗莎塔天文台，非对称 `0.5 s` / `2.0 s`）**：
>   - `Main` / `Pattern2` 逐样本锁定 **`2,846,116` 帧（170 BPM）**，`Fast` / `Fast_Pattern2` 逐样本锁定 **`2,468,568` 帧（196 BPM）**。
>   - 切入 `Pattern2`（水上）/ `Fast_Pattern2` 使用 **`0.5 s`（`24,000` 帧）**，切回 `Main` / `Fast` 使用 **`2.0 s`（`96,000` 帧）**慢速交叉淡化；触发器为 `SystemObj_SnowMountainSoundArea_00`（雪山顶上 2 处，另在彩虹之路 `Rainbow_00_Fix` 复用 1 处）。
>   - `SnowMountain` 主曲拥有 `Outro00..07`（8 个尾奏），而 **`SnowMountain_Pattern2` 仅确认拥有 `Outro00..05`（6 个尾奏，无 `Outro06`/`Outro07`）**；`Pre`/`Pre_Short` 采用 4 拍网格，无 `_Bridge`。
> - **游戏实机相位预读**：`0x0243b078` 切版时采用 `+200 ms`（`+9,600` 样本）目标预读与 `0.02` 进度迟滞，末圈自动同步切至 `_Fast ↔ _Fast_Pattern2`。
> - **单元测试钉死**：见 `VerifiedV180TransitionsTest.cinemaUses500msBothWaysAndSnowMountainUses500msEnter2000msExit`。

## 历史记录（2026-11-12 `alpha08` 初版接入与混合预解码验收）

初版接入确认 Pattern2 是同 Full 的另一版本，早期曾采用统一 1 秒交叉淡化（`48,000` 帧，现已改为上述 `0.5 s` / `2.0 s`）。MINF 校验两对 Full 以及两对 Fast 的 rate、BPM、phase、loop、逐拍 grid 完全一致。`FullVersionCompiler` 在生产图中增加同速双向 Immediate/SameTime；Full→Fast 保留各版本自己的最终圈加速规则。

24 个实际 Native WAV 来自初版生产图（两赛道 × Full/Fast × 双向 × 曲中/loop前半秒/loop后首帧），全部零削波、欠载和解码失败。源入口从 voice at/source 还原的误差为 0；render 后 snapshot 游标相对 render 前公式差一帧。请求后 1/23999/47999/48000 帧为双 voice，48001 帧为单目标 voice。loop 前请求时旧声源真实跨界继续循环，未提前丢声。

多版本合并的全量解码会超过 Native prepared PCM 固定 128 MiB 预算；最初全部 streamed 的设备试验又证实 Immediate/SameTime 会因目标当前采样页尚未就绪而被 RuntimePreparation 返回 DeadlineMiss。最终采用混合准备：两版 Full 与两版 Fast 共 4 个长曲完整预解码（Cinema 83.05 MiB、SnowMountain 81.10 MiB，均低于128 MiB），其他 Pre/Outro 等继续 streamed。首载及重新加载都传递 preparedAssets；测试核对4个源和预算，并验证会话按名字选择绑定策略。音乐图、源位置和一秒包络没有更改。Native 立即转场在提交目标后会清除 transition active，但旧 voice 淡化仍在继续；控制器因此还检查实际 voice 数量，禁止淡化过程中发起第三路版本或 Fast。

Pack 只从已声明 stage group 中按已知 SHA256 加载对应版本，加载 bytes 再校验摘要；Pattern2 保留自己的 Fast 和已确认 Outro，不借用不适用的退出点。UI 显示 Native 实际提交的版本，四语资源已加入（en/zh-Hans/zh-Hant/ja）；并非宣称全部旧界面翻译完成。

完整渲染与快照证据在本会话 /workspace/mkw-planning/full-version-native-results.json、full-version-snapshots，实际 WAV 在 /workspace/mkw-preview-jobs/FullVersions。两赛道预览已分别上传并完整回下载校验。Android 控件验收另记最终结果，不以 host WAV 代替设备验证。

2026-11-13 实际 Android 混合准备版本已通过 FullVersionControlsDeviceTest：Cinema/SnowMountain 分别 Full2→Fast2→Fast1，实际输出活动且 Native 版本/阶段已提交，设备结果随最终交付保留。组合测试的 Donkey 部分另行验收，不把前一个 test 的通过描述为整套通过。
