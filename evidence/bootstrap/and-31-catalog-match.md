# AND-31 · 目录与本地文件自动匹配（阶段 D）

- **checked_at**: 2026-11-08T19:05+08:00 (Asia/Shanghai)
- **证据级别**: JVM 18（全部执行）
- **关键结论**: 650 条目录条目可以逐个判定「已拥有 / 缺失 / 不符」

---

## 1. 解决的问题

之前目录和曲库是两套互不相干的数据：

| 数据 | 知道什么 | 不知道什么 |
|---|---|---|
| `catalog.json`（650 条） | 游戏里有什么 | 用户有什么 |
| 曲库（用户选的） | 用户有什么 | 它属于目录的哪一条 |

**两边都只有一半用处。** 接起来之后才有「你已拥有 397 条中的 12 条」这种信息。

---

## 2. 匹配规则：名称优先，测量确认

**只按名称匹配是不够的**：用户可以重命名文件，两个赛道也可能撞名。

所以名称命中后，还要比对**目录自己的测量值**：

| 字段 | 不符时 |
|---|---|
| 采样率 | `sample rate 44100 Hz, catalogue says 48000 Hz` |
| 声道数 | `1 channels, catalogue says 2` |
| 帧数 | `999 frames, catalogue says 3373711` |

### 三态而不是两态

```kotlin
enum class Status { OWNED, MISSING, MISMATCH }
```

**为什么需要 MISMATCH**：如果只有「有/没有」，一个名字对但长度不对的文件会被报成「已拥有」。

**「你有这首」是应用不该凭猜测做的断言。**

### 测量缺失不算不符

文件帧数为 0（尚未测量）时不判定不符：

> Absence of evidence is not contradiction.

否则一份还没测量过的列表会产生一整片假不符。

---

## 3. 一个文件只能满足一条目

同一个资产名**真的会出现在多个归档条目里**。

如果用「已消耗集合」按**值**判重，两个测量完全相同的文件会被折叠成一个 —— 导致**少报拥有量**。

所以按**索引**跟踪：

```kotlin
val byStem = files.withIndex().groupBy { it.value.stem }
val consumed = mutableSetOf<Int>()
```

**这是本轮唯一一个被测试抓到的代码 bug**：`twoFilesWithTheSameNameSatisfyTwoEntries` 期望 2 得到 1。

---

## 4. 逻辑行合并

一个赛道主题在目录里是**多条**（Main / Pre / Bridge / Outro）。

`logicalRows()` 按与 `StageFlowDiscovery` **完全相同的前缀规则**合并，所以「这里显示成一首」和「能打开的是一首」是同一件事。

```kotlin
data class LogicalRow(
    val prefix: String,
    val kind: ResourceKind,
    val entries: List<Match>,
) {
    val ownedCount: Int
    val total: Int
    val isComplete: Boolean
    val missingPlayable: Int   // 只数可播的缺失项
}
```

`missingPlayable` 不算不可播的 —— 提示用户去找一个解不了的文件没有意义。

---

## 5. 覆盖声明不能因为「都匹配上了」就消失

```kotlin
data class Report(
    val matches: List<Match>,
    val unmatched: List<PresentFile>,
    val catalogIsPartial: Boolean,   // 从 Catalog.isPartial 带过来
)
```

有测试专门断言：

```kotlin
fun thePartialCatalogueFlagSurvivesMatching() {
    val report = CatalogMatcher.match(catalog(entry("X"), partial = true), listOf(file("X")))
    assertTrue(report.catalogIsPartial)
}
```

**如果连接操作把 `isPartial` 丢了，界面就会把 650 条说成完整清单** —— 即使每一行都匹配上了。

---

## 6. 测试

### `CatalogMatcherTest`（18 条）

| 组 | 覆盖 |
|---|---|
| 拥有 | 名称+测量相符、完全缺失 |
| 不符 | 帧数不符、采样率不符、声道不符、**测量为 0 不算不符** |
| 重复名 | **一条目一文件**、两文件两目录条目 |
| 未匹配 | 无目录条目的文件单独列出、不计入拥有 |
| 摘要 | 三态计数 |
| 声明 | **部分目录标记带过来**、冻结目录不带 |
| 可播性 | 拥有 ≠ 可听，分开统计 |
| 逻辑行 | 四段合一、两赛道两行、**只数可播的缺失**、类别分类 |

---

## 7. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **422 tests, 0 failures**（上轮 404） |
| 设备 | 105 passed（**上轮结果，本轮未改设备代码**） |

---

## 8. 阶段 D 进度

| 要求 | 状态 |
|---|---|
| 物理音频文件与逻辑曲目的区分 | ✅ |
| **目录与本地音频文件自动匹配** | ✅ **本轮** |
| 资源 SHA-256 与缺失提示 | ✅ |
| 逻辑赛道分组 | ✅ |
| 作品类别分类 | ✅ |
| 索引版本迁移 | ✅ |
| 重复文件检测 | ⚠️ 按索引去重，**未向用户提示「这两个是同一文件」** |
| 资源搬迁后的重新绑定 | ⚠️ `missing()` 支持，**UI 未接** |
| 曲目封面 | ❌ |
| BARS 侧稳定 ID 接入 | ❌ |
| 索引冻结 | **BLOCKED**（4/499 归档） |

---

## 9. 未接进 UI

`CatalogMatcher` 目前只有测试在调。界面仍显示原始目录（650 条，不带拥有状态）。

**这是刻意顺序**：先让逻辑可测，再改界面。上一轮 `and-29` 的教训是界面接上后没有端到端验证，所以这一轮先把能力做扎实。

---

## 10. 仍未验证

| 项 | 状态 |
|---|---|
| UI 端到端 | **NOT_VERIFIED** |
| 真实 SAF 返回的文件名 | 未验证（测试用构造名） |
| 真机 arm64 | **NOT_VERIFIED** |
| codec 2 | **UNRESOLVED** |
