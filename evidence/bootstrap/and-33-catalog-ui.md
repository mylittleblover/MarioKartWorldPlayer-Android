# AND-33 · 目录拥有量接入界面

- **checked_at**: 2026-11-08T19:55+08:00 (Asia/Shanghai)
- **证据级别**: JVM 12 + 设备 110（全部执行）
- **关键结论**: 目录卡片现在显示「已拥有 X of Y」并保留部分覆盖提示

---

## 1. 做了什么

`CatalogMatcher`（and-31）产出 650 条的完整报告，`CatalogBrowser`（and-32）把它压缩成界面能用的行，现在**接到界面上了**：

```
Catalog
  12 of 650 present
  Partial catalogue, 4 of 499 archives read.
```

---

## 2. `CatalogBrowser` 的三个关键决定

### 2.1 覆盖提示必须穿过每一层

```kotlin
coverageNotice = if (catalog.isPartial) {
    "Partial catalogue, ${catalog.archivesRead} of ${catalog.archivesExpected} archives read."
} else null
```

有测试断言它同时含 `"Partial"` 和 `"4 of 499"`。

**如果我这一层丢了它，界面会把 650 条说成完整原声带** —— 即使每一行都匹配上了。

### 2.2 行的「完整」继承匹配器的规则

```kotlin
fun aWrongLengthFileDoesNotCountAsOwned() {
    browser.refresh(listOf(file("A", frames = 999)))
    assertEquals(0, ctx.rows.single().owned)   // 目录说 1000 帧
}
```

**不在这一层重新用名字判断拥有** —— 那会让「名字对但内容不对」重新变成「已拥有」。

### 2.3 「拥有」不等于「能听」

```kotlin
fun aPartRowIsPlayableOnlyWhenAPresentPartDecodes() {
    // 拥有 JukeBox 条目 ≠ 能播放它
    assertTrue(row.complete)   // 文件在
    assertTrue(!row.playable)  // 但解不了
}
```

---

## 3. 修掉一个 UI 测试的真实问题

加第二处覆盖提示后：

```
PlayerScreenTest > theCatalogStatesItsOwnLimitations FAILED
  Expected at most 1 node but found 2 nodes that satisfy
  (Text contains 'Partial catalog')
```

**修的是测试，不是界面。**

原来断言「恰好有一个节点含这句话」。现在有两处（目录摘要卡 + 拥有量卡），这不是 bug 而是合理重复。

改成「至少有一个」：

```kotlin
val notices = rule.onAllNodesWithText("Partial catalog", substring = true).fetchSemanticsNodes()
assertTrue(notices.isNotEmpty(), "the coverage notice must be visible somewhere")
```

**为什么改测试是对的**：断言的原意是「这句话要出现在界面上」，不是「只出现一次」。后者是我当初写得太具体。

---

## 4. 一处诚实的记录

`refreshCatalog()` 里声道数写死为 2：

```kotlin
channels = 2,   // store 没有记录声道数
```

**这是真实缺口**：`LibraryStore.Row` 不存声道数，所以单声道文件会与立体声目录条目比较并产生假不符。

我没有为此改 `LibraryStore`（那需要另一次 schema 升级），但在提交信息里写明了这个缺口。

---

## 5. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **446 tests, 0 failures** |
| `./gradlew :app:connectedDebugAndroidTest` | **110 tests, 0 failures** |

合计 **699**。设备 `mkw_test(AVD)` Android 15 **x86_64 模拟器**。

---

## 6. 并行推进

本轮同时启动了三个子代理处理互不依赖的模块：

| 模块 | 阶段 | 内容 |
|---|---|---|
| `unlock/SpecialCupUnlock` | G | 特别杯解锁、聆听计时、仪式状态机 |
| `routes/RouteModel` | F | 五层路线数据（地理/连接/候选/过渡/保存） |
| `versions/ListeningVersion` | E | 保存的播放版本、快照恢复、缺失资产报告 |

**三者都只允许创建新文件**，不得修改现有代码，所以不会互相冲突。

---

## 7. 仍未验证

| 项 | 状态 |
|---|---|
| SAF 选择器 | **NOT_VERIFIED** |
| 真机 arm64 | **NOT_VERIFIED** |
| 人耳听多段流程 | **NOT_VERIFIED** |
| 声道数缺口 | 已知，未修 |
| codec 2 | **UNRESOLVED** |
