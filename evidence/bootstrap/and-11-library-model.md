# AND-11 · 曲库数据模型

- **checked_at**: 2026-11-08T03:10+08:00 (Asia/Shanghai)
- **证据级别**: **12 个 JVM 测试**；UI 尚未接线

---

## 1. 做了什么

把 OST 索引里的 650 条记录变成 UI 能用的模型，并**把"能不能播"的判断集中到一处**。

`Playability` 依据的是**实测事实**，不是猜：

| 事实 | 来源 | 对模型的约束 |
|---|---|---|
| 253 个文件用 kind=2 编码 | 全库实测 | 这些**必须**标为不可播 |
| kind=2 里 227 个是 JukeBox | 同上 | JukeBox 分组下大部分会置灰 |
| 所有 `BGM_Stage_*` 是 kind=1 | 同上 | 赛道主题**绝不能**被置灰 |

## 2. 核心规则

```kotlin
const val SUPPORTED_BWAV_CODEC_KIND = 1   // DSP-ADPCM

fun evaluate(codecKind: Int?): Result =
    when (codecKind) {
        1    -> playable
        else -> not playable, reason = "此编码尚未支持"
    }
```

**唯一改动点**：将来 kind=2 做出来了，只改这一个常量，UI 自动跟着变。

## 3. 排序策略

```
可播放优先 → 按类别 → 按名字
```

**为什么不按类别排在最前**：JukeBox 有 227 条，如果排在赛道主题前面，用户第一眼看到的是一大片灰掉的条目。**先给能用的**。

## 4. 12 个测试

| 测试 | 保护 |
|---|---|
| `dspIsPlayable` | kind=1 可播 |
| `kindTwoIsNotPlayable` | kind=2 不可播 |
| `unreadableSourceIsNotPlayable` | 读不出也不可播 |
| `unsupportedReasonDoesNotBlameTheFile` | 提示**不含** "corrupt"/"malformed" |
| `everyIndexClassMapsToACategory` | 索引里所有类别都有归属 |
| `unknownIndexClassFallsBackInsteadOfThrowing` | 新增类别不崩 |
| `classMatchingIgnoresCase` | 大小写无关 |
| `playableEntriesSortBeforeBlockedOnes` | 可播在前 |
| `blockedEntriesStillGroupUnderTheirCategory` | 置灰项仍归入原分组 |
| `groupingLosesNoEntries` | 分组不丢数据 |
| `groupingIsStableForEqualNames` | 排序稳定 |
| `supportedCodecConstantIsOne` | 常量值 |

## 5. 与索引的对齐

索引给出的实际分布（`temporary-index.json`）：

```
COURSE 338 · JUKEBOX 227 · SEQUENCE 34 · CUTSCENE 22
BATTLE 16 · MISSION 5 · DEMO 4 · SOUND_EFFECT 3 · BOOT 1
```

`TrackCategory` 与之一一对应，`UNCLASSIFIED` 落到 `OTHER`。

## 6. 仍未做的

| 项 | 状态 |
|---|---|
| 索引加载（JSON → 模型） | **未做** |
| UI 列表接线 | **未做** |
| 文件选择器 | **未做** |
| 播放控制 | **未做** |

**本轮只有模型与规则，没有可交互的曲库。**

## 7. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **57** |
| Android Instrumentation | 17 |
| **合计** | **217** |
