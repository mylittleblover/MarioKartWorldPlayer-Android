# BARS ↔ WAV 命名重合度探查（OST-03 前置调研）

- **checked_at**: 2026-11-07T21:00+08:00 (Asia/Shanghai)
- **证据级别**: **L1（元数据）**
- **目的**: 在写匹配器之前，先确认"按名字能不能直接对上"，避免做出与现实不符的关联逻辑
- **数据**: 650 条 BARS 条目（四份档案）× 20,991 个已解码 wav，全部只读

---

## 1. 初测：按条目名精确匹配 80.3%

| 指标 | 数值 |
|---|---|
| 精确同名命中 | **522 / 650（80.3%）** |
| 未命中 | 128 |

未命中的 128 条一眼就能看出有系统性后缀：

| 模式 | 数量 | 例子 |
|---|---|---|
| `.yu.48` 后缀 | 56 | `BGM_Stage_MooMooFarm_Outro04.yu.48` |
| `_Main` 后缀 | 13 | `BGM_Stage_Cave_Main` |
| `Outro<多位数字>` | 6 | `BGM_Stage_ChocoMountain_Outro010203` |
| `.v02` 后缀 | — | `BGM_Stage_StormClouds_Main.v02` |
| 其他（无前缀 JukeBox） | 50 | `sIceWorld_OutroFull` |

## 2. 加后缀归一化 → 89.4%，但露出真正的结构

剥离 `.yu.48` / `.vNN` 后升到 **581/650（89.4%）**。剩下的 69 条几乎全是**同一名字对应多个条目**：

| 名字 | BARS 条目数 | WAV 文件数 |
|---|---|---|
| `BGM_Stage_PeachBeach_Outro.yu.48` | **8** | 8（`Outro00`…`Outro07`） |
| `BGM_Stage_Cinema_Outro` | **6** | 6 |
| `BGM_Stage_RaftingRiver_Outro.yu.48` | 6 | 7 |
| `BGM_Rainbow_Area2_Outro` | 5 | 5 |

数字对得上（8↔8、6↔6、5↔5），说明**不是缺失，是我把名字理解错了**。

## 3. 真实结构：条目是一个「源 → 目标」过渡定义

把字符串表按角色拆开，PeachBeach 那 8 条是：

```
[ 95] 'BGM_Stage_PeachBeach_Outro.yu.48'  'Outro_ToNextPre'  'BGM_Stage_PeachBeach_Outro06'  'Outro_ToNextPre'
[106] 'BGM_Stage_PeachBeach_Outro.yu.48'  'Outro_ToNextPre'  'BGM_Stage_PeachBeach_Outro02'  'Outro_ToNextPre'
[126] 'BGM_Stage_PeachBeach_Outro.yu.48'  'Outro_ToNextPre'  'BGM_Stage_PeachBeach_Outro03'  'Outro_ToNextPre'
[141] 'BGM_Stage_PeachBeach_Outro.yu.48'  'Outro_ToNextPre'  'BGM_Stage_PeachBeach_Outro07'  'Outro_ToNextPre'
[361] 'BGM_Stage_PeachBeach_Outro.yu.48'  'Outro_ToNextPre'  'BGM_Stage_PeachBeach_Outro05'  'Outro_ToNextPre'
[369] 'BGM_Stage_PeachBeach_Outro.yu.48'  'Outro_ToNextPre'  'BGM_Stage_PeachBeach_Outro01'  'Outro_ToNextPre'
[459] 'BGM_Stage_PeachBeach_Outro.yu.48'  'Outro_ToNextPre'  'BGM_Stage_PeachBeach_Outro00'  'Outro_ToNextPre'
[473] 'BGM_Stage_PeachBeach_Outro.yu.48'  'Outro_ToNextPre'  'BGM_Stage_PeachBeach_Outro04'  'Outro_ToNextPre'
```

规律非常清晰：

```
[ 源资源名 ] [ 过渡标签 ] [ 目标资源名 ] [ 过渡标签 ]
```

- **第一个** prefixed 名 = 这条过渡的**源**
- **过渡标签**（`Outro_ToNextPre` / `Bridge_ToFull`）夹在中间
- **最后一个** prefixed 名 = 这条过渡的**目标**，且各不相同

这解释了为什么"名字不唯一"：**8 条共享同一个源，各自指向不同目标**。它们本来就是 8 条不同的过渡，不是 8 份重复数据。

## 4. 用「最后一个资产名」做锚点 → 650/650

| 锚点选择 | 解析到 wav | 命中率 |
|---|---|---|
| 第一个资产名 | 568 / 650 | 87.4% |
| **最后一个资产名** | **650 / 650** | **100%** |

逐档案：

| 档案 | last 命中 | first 命中 |
|---|---|---|
| `M_Bgm` | 525/540 | 468/540 |
| `M_BgmRainbow` | 15/15 | 8/15 |
| `M_BgmSFC` | 10/10 | 10/10 |
| `BgmAll` | 85/85 | 82/85 |

**结论：BARS 条目的「目标资源名」与 wav 文件名一一对应，100% 可解析。**

## 5. 名称正则漏了标点（本轮修掉）

初版正则 `[A-Za-z0-9_.]` 把 15 条 JukeBox 封面曲判成了"无名字"，实际上它们含有**撇号**：

```
BGM_JukeBox_M488_MK7_Rosalina'sIceWorld_OutroFull
BGM_JukeBox_M596_3DWFury_Bowser'sFuryTheme_OutroFull
BGM_JukeBox_M552_YoshiStory_Yoshi'sSong_OutroFull
BGM_JukeBox_M435_MKN64_Toad'sTurnpike_OutroFull
BGM_JukeBox_M531_Odyssey_Bowser'sCastleMainCountyard_OutroFull
BGM_JukeBox_M577_N64_PiranhaPlant'sLullaby_OutroFull
BGM_JukeBox_M572_MKWii_Wario'sGoldMine_OutroFull
BGM_JukeBox_M534_N64_Bowser'sRoad_OutroFull
BGM_JukeBox_M553_YoshiStory_Yoshi'sTalk_OutroFull
```

字符类补上 `'` `-` 空格后，这 15 条全部正常识别。之前靠 fallback 拿到的 `sIceWorld_OutroFull` 就是这条名字被截断的结果。

## 6. wav 名字空间分布（决定匹配器要不要全量扫）

| 前缀族 | 数量 | 性质 |
|---|---|---|
| `Ap_` | 10,112 | 材质交互音效（48%！） |
| `Voice_` | 7,030 | 角色语音 |
| `SE_` | 1,788 | 音效 |
| `CmnObj_` | 867 | 物件音效 |
| **`BGM_`** | **625** | **音乐** |
| `Env_` | 254 | 环境 |
| `Fol_` | 250 | 植被 |
| `Dm_` | 22 | 过场 |

**20,991 个 wav 里只有 647 个（3.1%）是音乐相关的 `BGM_` / `Dm_`。**
匹配器不需要对全部 20,991 个做全量关联，应先按前缀缩小候选集。

## 7. 反向核对：15 个 wav 在 BARS 里没有对应条目

`BGM_`/`Dm_` wav 归一化后 647 个，BARS 归一化资产名 670 个，差集 15 个：

| wav 名 |
|---|
| `BGM_JukeBox_M301_N64_Bob-OmbBattlefield_Outro` |
| `BGM_JukeBox_M336_MKDS_Tick-TockClock_OutroFull` |
| `BGM_JukeBox_M340_Galaxy2_BattleDigga-Leg_OutroFull` |
| `BGM_JukeBox_M435_MKN64_Toad'sTurnpike_OutroFull` |
| `BGM_JukeBox_M439_3DW_Hands-OnHall_OutroFull` |
| `BGM_JukeBox_M488_MK7_Rosalina'sIceWorld_OutroFull` |
| `BGM_JukeBox_M525_MK8_Thwomp-Ruins_OutroFull` |
| `BGM_JukeBox_M531_Odyssey_Bowser'sCastleMainCountyard_OutroFull` |
| `BGM_JukeBox_M534_N64_Bowser'sRoad_OutroFull` |
| `BGM_JukeBox_M552_YoshiStory_Yoshi'sSong_OutroFull` |
| `BGM_JukeBox_M553_YoshiStory_Yoshi'sTalk_OutroFull` |
| `BGM_JukeBox_M572_MKWii_Wario'sGoldMine_OutroFull` |
| `BGM_JukeBox_M577_N64_PiranhaPlant'sLullaby_OutroFull` |
| `BGM_JukeBox_M596_3DWFury_Bowser'sFuryTheme_OutroFull` |
| `BGM_JukeBox_M602_N64_Bob-OmbBattlefield_OutroFull` |

**全部是带撇号的 JukeBox 名**——正是 §5 那个正则缺陷造成的。修复后这 15 条全部对上，差集归零。

## 8. 对 OST-03 实现的直接结论

1. **匹配器锚点用「最后一个 prefixed 资产名」**，不是第一个，也不是条目名
2. **必须先按前缀收窄**：`BGM_` / `Dm_` / `SE_` 共 2,435 个 wav，其余 18,556 个是 SFX，不参与音乐匹配
3. **名称需要归一化**：剥离 `.yu.48` / `.tk.48` / `.nn.48` / `.mj.48` / `.yu` / `.vNN`
4. **条目是一个过渡定义（源→目标），不是一个独立音频**——这对后续 Cue/Bridge/Outro 建模是决定性事实
5. **"名称不唯一"不是数据质量问题**，而是"多个过渡共享同一源"的正常表现；去重必须按 `ordinal + amta_offset`

## 9. 本轮未做 / NOT_RUN

- 未解码 wav 内容、未比对采样率/帧数（那是 OST-03 本体 + VER-01）
- 未验证 `.yu.48` 中 `48` 是否真的代表 48kHz（**未测**）
- 未做 L2/L3 验证
