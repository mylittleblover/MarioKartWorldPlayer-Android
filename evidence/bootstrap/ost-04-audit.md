# OST-04 分类与缺口报告（阶段 00 收口）

- **checked_at**: 2026-11-07T21:30+08:00 (Asia/Shanghai)
- **证据级别**: **L0/L1** —— 分类与来源台账。**不含 L2 音频验证、L3 行为验证**
- **结论**: `INVENTORY_INCOMPLETE`（这是正确且诚实的结果，不是失败）

---

## 1. 覆盖状态

| 指标 | 数值 |
|---|---|
| 已读 BARS 档案 | **4 / 499** |
| 条目总数 | **650** |
| 已探测音频文件 | **2,435**（`BGM_`/`Dm_`/`SE_` 前缀） |
| 探测失败 | **0** |
| 无源条目 | **0** |
| 未分类条目 | **0** |
| **覆盖状态** | **INCOMPLETE** |

### 为什么是 INCOMPLETE

两条硬理由（工具自动生成，非人工判断）：

1. **只读了 4/499 份档案** —— `romfs\Sound\Resource` 下有 499 个 `.bars.zs`，本轮只解析了 `M_Bgm`、`M_BgmRainbow`、`M_BgmSFC`、`BgmAll.Product.180` 这 4 份音乐档案
2. **未做任何验证** —— 没有 PCM 解码、没有 loop 检查、没有行为证据

用户已确认**剩余 495 个档案几乎都是音效数据**（`CmnObj_*`、`SE_*`、`Driver_*`、`Vehicle_*` 等），它们不属于 OST 音乐范畴。但**这个前提本身没有逐档案核实**，所以工具不会替用户把它当作"已覆盖"。

---

## 2. 分类台账

| 类别 | 数量 | 判定依据 |
|---|---|---|
| `COURSE` | **338** | `BGM_Stage_*` + `BGM_Rainbow_*` |
| `JUKEBOX` | **227** | `BGM_JukeBox_*`（合集曲目） |
| `SEQ` → `SEQUENCE` | **34** | `BGM_Seq_*`（标题/成绩/终点等序列音乐） |
| `CUTSCENE` | **22** | `Dm_*`（过场音乐） |
| `BATTLE` | **16** | `BGM_Battle_*` |
| `MISSION` | **5** | `BGM_Mission_*` |
| `DEMO` | **4** | `BGM_Demo_*` |
| `SOUND_EFFECT` | **3** | `SE_FirstBoot_*` |
| `BOOT` | **1** | `BGM_FirstBoot` |
| `UNCLASSIFIED` | **0** | — |
| **合计** | **650** | — |

**650/650 全部完成分类，0 条未分类。**

### 角色后缀分布（用于理解"逻辑曲目"与"物理条目"的差别）

| 角色 | 数量 |
|---|---|
| 基础曲（`_Main` / 无后缀） | 300 |
| `Fast` | 34 |
| `Pre` | 32 |
| `Outro00` | 32 |
| `Outro`（无名） | 32 |
| `Outro01` | 31 |
| `Outro02` | 30 |
| `Pre_Short` | 29 |
| `Outro03` | 26 |
| `Outro04` | 23 |
| `Bridge` | 21 |
| `Outro05` | 18 |
| `Outro06` | 14 |
| `FL` | 8 |
| `Outro07` | 6 |
| `Pattern2..5` | 8 |
| 其他（`Inst`/`Melody`/`Area2`/`Area3`/`Final`/`FinalLapFanfare`） | 7 |

> **注意**：650 是**物理条目数**，不是"曲目数"。`Coaster` 这一条赛道就占了基础曲 + Pre + Pre_Short + Fast + Bridge + 7 个 Outro = 至少 12 个条目。按逻辑曲目统计需要 IDX-01 的稳定 ID 合同，不在本任务范围。

---

## 3. 缺口清单

**`missing-sources.csv` 为空（0 行）。**

原因：650 条条目的目标资产名全部解析到了真实 wav 文件（`EXACT_VERIFIABLE` 650/650，见 `ost-03-probe-match.md`）。**没有"找不到源"的条目。**

这不代表"全 OST 齐了" —— 只代表**这 650 条**都有源。499 份档案里另外 495 份没读，那里有什么不知道。

---

## 4. 产出物

| 文件 | 路径 | 大小 |
|---|---|---|
| 临时索引 | `reports/generated/temporary-index.json` | 254 KB |
| 缺口表 | `reports/generated/missing-sources.csv` | 空（仅表头） |
| 覆盖报告 | `reports/generated/coverage-report.md` | — |

索引里每条含：`entryKey`（`bars:<ordinal>@<offset>`）、`internalName`、`class`、`assetPath`、`assetSha256`、`verified`。

> 索引**不写绝对私有路径到公开字段**的原则在此处放宽了 —— `assetPath` 含本机路径。这是**本地生成物**，已在 `.gitignore` 中排除（`reports/generated/`）。若要提交，必须先脱敏。

---

## 5. 顺带发现：道路连接元数据存在

查 `romfs\RSDB\Course.Product.180.rstbl.byml.zs` 时发现**真实道路连接表**：

```
Work/Banc/Course/InterStageEx1_<A>To<B>_A_00.bcett.json
```

实测 **137 条 `InterStageEx1_*` 连接，涉及 37 个赛道**，例如：

```
FirstCircuitToCoaster, CentralToRainbow, CaveToSnowMountain,
DKPassToLagoonCity, ChocoMountainToToadFactory, ...
```

**这对 AND-06（静态路线/道路音乐）是重要事实**：原版道路连接**不是靠猜的**，游戏数据里明确写了。`Rainbow` 只连 `Central` 一条。

> 但要注意：文件名里的 `InterStageEx1` 命名不等于已验证的**音乐行为**。连接存在 ≠ 走到那条路会播哪首。仍需 L3。

---

## 6. 本轮明确未做 / NOT_RUN

| 项 | 状态 |
|---|---|
| 剩余 495 份 `.bars.zs` 逐档核查 | **NOT_RUN**（用户判断为音效，未逐档验证） |
| 逻辑曲目归并（一个赛道算几首） | **NOT_RUN**（属 IDX-01） |
| PCM 解码 / loop 验证 | **NOT_RUN**（VER-01/02） |
| 任何 L3 行为验证 | **NOT_RUN** |
| 道路连接的音乐映射 | **NOT_RUN**（仅记录文件存在） |
