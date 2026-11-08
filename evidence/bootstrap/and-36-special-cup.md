# AND-36 · 特别杯隐藏解锁（阶段 G）

- **checked_at**: 2026-11-08T20:10+08:00 (Asia/Shanghai)
- **证据级别**: JVM 36（全部执行）
- **关键结论**: 解锁条件只能用**真实播放的帧计数器**满足，播种、刷屏、重播都无法绕过

---

## 1. 「有效聆听」是这一阶段全部难度

指令原文：

> 曲目至少真实播放 30 秒。如果曲目长度不足 30 秒，则必须完整播放。
> Seek、静音计时、后台假计时或重复统计不得绕过要求。

**几种直观实现都会让用户不听就解锁。** 每一道闸门对应一个具体的漏洞。

---

## 2. 进度来自引擎帧计数器，不是墙钟

```kotlin
fun report(frames: Long): Progress
```

调用方报告**引擎自己的帧计数**。

**为什么这能挡住「后台假计时」**：暂停的传输不会推进帧计数器。墙钟会。

---

## 3. 一个真实漏洞，被测试抓到

### 我最初写的守卫

```kotlin
val plausible = delta <= state.currentTrackFrames   // 只拒绝超过整首的跳跃
```

### 它能通过的攻击

一首十分钟的曲子（28,800,000 帧），报告 `delta = 9,999,999` —— **小于整首长度，被全额计入**。

两三次这样的报告就能「听完」一首歌。

### 测试

```kotlin
fun scrubbingToTheEndOfEveryStageDoesNotUnlock() {
    // 每首歌：report(0) → report(9_999_999)
    assertFalse(u.isEntitled, "seeking must never unlock the cup")
}
```

**失败。**

### 修正

```kotlin
const val MAX_CREDIT_SECONDS = 1
val maxCredit = MAX_CREDIT_SECONDS * sampleRate
if (delta > maxCredit) → 视为 seek，忽略
```

**正常使用零成本**：调用方每秒报告多次，永远不会触碰上限。

---

## 4. 短曲目规则只在一处实现

```kotlin
val reachedEnd = currentTrackFrames > 0 &&
    frames >= currentTrackFrames &&
    currentTrackFrames < required
val completed = reachedEnd || credited >= required
```

**先比较曲目长度**，所以「不足 30 秒的曲子播完即完成」不需要调用方特判。

`aShortTrackStoppedBeforeItsEndDoesNotComplete` 与 `aLongTrackDoesNotCompleteOnItsEndAlone` 从两侧钉住。

---

## 5. 完成是集合，不是计数器

```kotlin
completedStages: Set<String>
```

| 测试 | 作用 |
|---|---|
| `replayingOneStageDoesNotCreditAnother` | 听 A 不能顶替 B |
| `furtherReportsForACompletedStageDoNotInflate` | 完成后停止累加 |
| `listeningTwiceToTheSameStageCompletesItOnce` | 重复听不重复计 |

---

## 6. 仪式是显式状态机

```kotlin
enum class CeremonyPhase { NONE, PENDING, PLAYING, COMPLETED }
```

| 规则 | 测试 |
|---|---|
| 未解锁不能开始仪式 | `theCeremonyCannotStartBeforeTheCupIsUnlocked` |
| 解锁只标记 PENDING（普通模式等曲目自然结束） | `entitlementMarksTheCeremonyPendingRatherThanStartingIt` |
| 没播完不能标记完成 | `aCeremonyCannotFinishWithoutPlaying` |
| 中途退出**保留资格** | `leavingEarlyKeepsTheEntitlement` |
| 再次进入**从头开始** | `reEnteringRestartsFromTheBeginning` |
| 祝贺只属于完整观看 | `theCongratulationsIsOwedOnlyAfterACompleteCeremony` |
| 中途退出**不消耗**首次时刻 | `leavingEarlyDoesNotConsumeTheFirstTimeMoment` |
| 重温**不重发**祝贺 | `aReplayDoesNotReopenTheFirstTimeMoment` |

**`leaveCeremonyEarly` 回到 PENDING 而不是 COMPLETED** —— 这样再次进入是「重新看」而不是「继续看」。

---

## 7. 完成播放由上播者报告，不由计时器推断

```kotlin
fun finishCeremony(reachedEnd: Boolean): UnlockState
```

**参数是布尔而不是时长**：必须由知道音频真的播完的那一方报告。

指令明确禁止「通过普通 UI 定时器冒充 BGM 完播事件」—— 这个签名让那件事做不到。

---

## 8. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **544 tests, 0 failures**（上轮 508） |
| 设备 | 110 passed（上轮结果，未改设备代码） |

合计 **797**。

---

## 9. 阶段 G 进度

| 要求 | 状态 |
|---|---|
| 首次使用不出现特别杯 | ✅ 由 `isEntitled` 控制 |
| 30 秒有效聆听 | ✅ |
| 短曲目完整播放 | ✅ |
| Seek / 静音 / 假计时 / 重复统计不可绕过 | ✅ |
| 按逻辑赛道记录 | ✅ 前缀聚合 |
| 全部完成立即持久化解锁资格 | ✅ 状态可序列化 |
| 不强制中断当前音乐 | ✅ 只标记 PENDING |
| 普通模式等自然结束 | ✅ |
| 顶部通知 + 前往解锁 | ❌ **UI 未做** |
| 演出区域 + 播放原版音频 | ❌ **UI 未做** |
| 演出时禁用暂停/快进/拖动 | ❌ **UI 未做** |
| 中途退出不丢资格 | ✅ |
| 再次进入从头播放 | ✅ |
| 完整播完首次祝贺 | ✅ 逻辑 |
| 重温不重复修改状态 | ✅ |
| 音频焦点丢失 / 进程死亡 / 文件缺失处理 | ❌ **未做** |
| 解锁状态持久化到磁盘 | ❌ **未做** |

**逻辑层完整，UI 与持久化未做。**

---

## 10. 仍未验证

| 项 | 状态 |
|---|---|
| 特别杯 UI | ❌ |
| 「Special Cup Appears」原版音频 | ❌ 未定位该素材 |
| 解锁状态持久化 | ❌ |
| SAF 选择器 | **NOT_VERIFIED** |
| 真机 arm64 | **NOT_VERIFIED** |
