# AND-12 · OST 索引加载器

- **checked_at**: 2026-11-08T03:30+08:00 (Asia/Shanghai)
- **证据级别**: **15 个 JVM 测试**（含拒绝路径）；**未接 UI**

---

## 1. 加载器的立场

索引是**临时的**（`freezeStatus = PROVISIONAL_INCOMPLETE`，`coverage = INCOMPLETE`，只读了 **4/499** 个档案）。

加载器**不粉饰**这个状态：

```kotlin
val complete: Boolean
    get() = coverage == "COMPLETE"
        && freezeStatus == "FROZEN"
        && archivesRead == archivesExpected
```

**只要索引自认不完整，`complete` 就是 false。** UI 必须据此显示"曲库不完整"，而不是假装齐全。

## 2. 关键设计决策

| 决策 | 原因 |
|---|---|
| **不信任类别判断可播性** | SEQUENCE/DEMO 是混合的，实测已证明 |
| 用 `assetSha256` 定位真实文件 | 索引不记录 codec kind，必须查真实字节 |
| 摘要无法解析 → **标为不可播** | 不能默认"能播" |
| 无 `entryKey` → **跳过但计数** | 无法持久化选择，但**不能静默丢弃** |
| `entryCount` 不符 → **抛异常** | 文件被截断，必须报错 |

## 3. 一个测试暴露的设计缺陷

第一版 `Loaded` 只有 `entries`，把无 key 的行**静默跳过**。测试失败：

```
index declares 1 entries but carries 0
```

**这暴露了两个问题**：

1. `entryCount` 比较用了"成功加载数"，而不是"实际条目数" —— **逻辑错误**
2. 跳过的行**不可见** —— **可观测性缺陷**

**修正**：
- 比较改为 `items.length()`（数组真实长度）
- `Loaded` 增加 `skippedCount`，跳过的行必须**被报告**

新增测试 `skippedCountIsZeroWhenEverythingLoads` 与 `entryWithoutAStableKeyIsSkipped` 同时钉住这两点。

## 4. 15 个测试

| 测试 | 保护 |
|---|---|
| `loadsAPlayableEntry` | 正常路径 |
| `kindTwoEntryLoadsAsBlockedRatherThanDropped` | **置灰但仍在列表** |
| `entryWithNoResolvableDigestIsBlockedNotAssumedPlayable` | 缺摘要不默认可播 |
| `entryWithEmptyDigestIsBlocked` | 空摘要同样 |
| `entryWithoutAStableKeyIsSkipped` | 跳过并**计数** |
| `skippedCountIsZeroWhenEverythingLoads` | 正常时计数为 0 |
| `nameFallsBackToTheKeyWhenInternalNameIsEmpty` | 名字缺省用 key |
| `entryCountMismatchIsRejected` | 声明数不符则报错 |
| `zeroEntryCountIsNotTreatedAsAMismatch` | 索引可省略计数 |
| `invalidJsonIsRejected` | 非法 JSON |
| `missingItemsArrayIsRejected` | 缺 items |
| `incompleteIndexReportsItselfAsIncomplete` | 4/499 → false |
| `fullyCoveredIndexReportsComplete` | 全覆 → true |
| `unknownClassBecomesOtherInsteadOfFailing` | 新类别不崩 |
| `everyEntryKeepsItsOwnPlayability` | **同类别内逐条判断** |

## 5. 仍缺的依赖

`org.json` 是 Android 框架类，JVM 单测里不存在。已在 `build.gradle.kts` 加：

```kotlin
testImplementation("org.json:json:20250107")
```

**仅测试用**，不进入 APK。

## 6. 仍未做的

| 项 | 状态 |
|---|---|
| 把索引 JSON 打进 APK 或运行时生成 | **未做** |
| UI 列表接线 | **未做** |
| SHA256 → codec kind 的真实查询 | **未做**（加载器接受回调，调用方负责） |
| 文件选择器 | **未做** |

**加载器是纯函数，没有被任何 UI 调用。**

## 7. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **80** |
| Android Instrumentation | 17 |
| **合计** | **240** |
