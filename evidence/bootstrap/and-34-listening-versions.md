# AND-34 · 保存的播放版本（阶段 E 前半）

- **checked_at**: 2026-11-08T19:35+08:00 (Asia/Shanghai)
- **证据级别**: JVM 29（全部执行）
- **关键结论**: 一个曲目可保存多个具名播放版本，可编辑、复制、快照恢复、并报告缺失资产

---

## 1. 解决了什么问题

`StageFlow` 描述一个赛道**能怎么播**，但用户可能想用不同方式听同一首：

- 只听 Main，循环 3 次
- 完整走 Pre → Bridge → Main → Outro01
- 每个版本起个名字，随时切回来

**之前没有这一层。** 每次打开都只有默认流程。

---

## 2. 身份来自资产，不来自名字

```kotlin
renamingDoesNotChangeIdentity        // 改名不产生新版本
duplicateGetsANewIdentity            // 复制**故意**产生新版本
duplicateRefusesToReuseTheIdentity   // 且不能复用同一个 id
```

**两条并存是有意的** —— 改名和复制是相反语义，测试直接把它们摆在一起，防止以后被「统一处理」掉。

---

## 3. 线性版本是独立结构，不是「只有一段的结构化版本」

```kotlin
sealed interface Structure {
    // Linear（单文件）
    // Structured（多段，含顺序与重复次数）
}
```

**理由**：普通单文件曲目**没有段落可排序、没有 Outro 可选**。如果合成一个类型，UI 会拿到一堆对它有意义的控件，但点了什么都不会发生。

`aLinearVersionRefusesASecondSection` 钉住这一点。

---

## 4. 快照的四个行为

| 测试 | 行为 |
|---|---|
| `aSnapshotRoundTripsTheSectionOrderExactly` | 顺序**逐项**一致 |
| `aSnapshotRoundTripsTheRepeatCountsAndTiming` | 重复次数与过渡时机一致 |
| `aSnapshotIsNotChangedByLaterEdits` | 快照**不可变**，后续编辑不回溯污染 |
| `aRestoredVersionReportsThatItIsARestorePoint` | 恢复后**自报是恢复点**，不冒充原创 |

最后一条是诚实性要求：用户应当能分辨「我自己编的」和「我从旧版本恢复的」。

---

## 5. 缺失资产要报告，不能跳过

```kotlin
fun missing(present: Set<AudioAssetId>): List<AudioAssetId>
fun isPlayable(present: Set<AudioAssetId>): Boolean
```

`aMissingAssetIsReportedRatherThanSkipped` 与 `theLibraryReportsEveryVersionThatCannotBePlayed` 覆盖。

**资产用摘要引用，所以重命名文件不会被误判为缺失。**

---

## 6. provenance 穿过每一次编辑

```kotlin
provenanceSurvivesEveryEdit
```

**理由**：时机是**临时选择**（`PROVISIONAL`）。如果一个版本被编辑几次后悄悄变成「已验证」，那是把猜测洗成了事实。

---

## 7. 名字冲突只限同一曲目内

```kotlin
aNameCollisionWithinOneTrackIsRefused      // 同一曲目不能有两个「我的最爱」
theSameNameOnDifferentTracksIsAllowed      // 不同曲目可以同名
aNameCollisionIgnoresCaseAndSurroundingSpaces  // 「My Mix」与「my mix」算冲突
```

---

## 8. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **479 tests, 0 failures**（上轮 450） |
| 设备 | 110 passed（上轮结果，本轮未改设备代码） |

---

## 9. 一个构建环境问题

运行测试时遇到：

```
Failed to create MD5 hash for file '.../kotlin/class-fq-name-to-source.tab'
Failed to delete some children. ... a process has files open
```

**原因**：三个子代理与我同时在这个工程目录跑 Gradle，共享的 `app/build/` 被并发写入。

**处理**：删掉 `app/build/kotlin` 缓存后重跑成功。

**教训**：并行子代理不应共享同一个构建目录。以后要么串行跑，要么让子代理只写代码不跑构建。

---

## 10. 阶段 E 进度

| 要求 | 状态 |
|---|---|
| **创建多个可保存的播放版本** | ✅ |
| **重新编辑** | ✅ |
| **重命名** | ✅ |
| **复制** | ✅ |
| **恢复版本** | ✅ |
| **引用稳定的资源 ID** | ✅ 摘要 |
| **普通音乐可线性播放** | ✅ Linear 结构 |
| 完整聆听模式执行真实多段流程 | ⚠️ 引擎侧已验证（and-27/28），**UI 未接** |
| 互动播放模式 | ❌ 未开始 |

---

## 11. 仍未验证

| 项 | 状态 |
|---|---|
| 版本持久化（存进 LibraryStore） | ❌ 未做 |
| 版本 UI | ❌ 未做 |
| SAF 选择器 | **NOT_VERIFIED** |
| 真机 arm64 | **NOT_VERIFIED** |
