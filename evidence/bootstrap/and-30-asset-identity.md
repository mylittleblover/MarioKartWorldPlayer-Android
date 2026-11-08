# AND-30 · 资源身份与逻辑曲目（阶段 D 起步）

- **checked_at**: 2026-11-08T18:45+08:00 (Asia/Shanghai)
- **证据级别**: JVM 41（全部执行）
- **关键结论**: 用户文件用摘要作身份，逻辑曲目与物理文件分离；曲库格式升级到 v2 且 v1 不丢数据

---

## 1. 为什么不能用文件名做身份

已有的 `ResourceId` 标识**目录里的**资源，身份来自源归档中的位置。用户选的文件拿不到这个坐标 —— 应用看不到归档。

只能用**摘要**：

| 方案 | 问题 |
|---|---|
| 文件名 | 可重命名；两个赛道可能撞名 |
| 路径 | 文件可移动（SAF 每个会话给的 URI 都可能不同） |
| 摘要 | **重命名不变、移动不变、内容变则变** |

`AudioAssetId` 强制 64 位小写十六进制，**大小写混写一律拒绝** —— 静默规范化会产生同一个文件的两个 ID。

---

## 2. 物理文件 ≠ 逻辑曲目

一个赛道主题是**五个文件**。把它们当成五首歌会让曲库长五倍，并且把「桥段」标成「歌曲」。

`LogicalTrack` 是这一层：

```kotlin
data class LogicalTrack(
    val id: String,              // kind + primary 摘要前缀
    val title: String,
    val kind: ResourceKind,
    val primary: AudioAssetId,   // 打开时先播哪个
    val assets: List<AudioAssetId>,
    val isFlow: Boolean,
    val frames: Long,
    val sampleRate: Int,
)
```

### 两个刻意的不变式

| 不变式 | 理由 |
|---|---|
| `primary` 必须在 `assets` 里 | 否则「播这首」有歧义 |
| `assets` 不得重复 | 重复会让「缺哪个」算错 |

### 同一文件出现在两个类别

`id = kind:摘要` —— 同一个主文件既是 COURSE 又编进 JUKEBOX 时**是两个曲目**，不会静默合并。

---

## 3. 移动/重命名后仍可解析

`isResolvable(present)` 与 `missing(present)` 按摘要集合判断：

```kotlin
fun missing(present: Set<AudioAssetId>): List<AudioAssetId>
```

**曲目可以列出「缺哪几个文件」**，而不是整条失效。这是指令要求的「资源搬迁后的重新绑定」的基础。

---

## 4. 曲库格式 v2：升级而不是丢弃

`LibraryStore` 从 v1 升到 v2，新增 `tracks` 数组。

**升级路径是这一轮最需要小心的地方。** 两种「自然」实现都是错的：

| 错误做法 | 后果 |
|---|---|
| 拒绝 v1 文件 | 用户曲库全丢 |
| 把 v1 读成空 | 静默丢数据 |

正确做法：**v1 的每行变成一条单文件曲目**，下次保存写成新格式。

```kotlin
const val SCHEMA_VERSION = 2
const val LEGACY_SCHEMA_VERSION = 1
```

未知的未来版本（如 9）仍然**隔离**，不假装能读 —— 前置兼容不做承诺。

---

## 5. 测试

### `AudioAssetIdTest`（23 条）

| 组 | 覆盖 |
|---|---|
| 摘要身份 | 同字节同 ID、异字节异 ID、长度错拒、非十六进制拒、**大写拒** |
| 分类 | COURSE / JUKEBOX / SEQUENCE / DEMO、**未知归 UNCLASSIFIED 而非猜测**、大小写不敏感 |
| 流程部件 | `_pre` / `_pre_short` / `_bridge` / `_outro` 识别，**主文件不算部件** |
| 逻辑曲目 | 空资产拒、primary 不在 assets 拒、重复资产拒、**重命名不改 ID**、kind 参与身份 |

### `LibraryStoreTracksTest`（12 条）

| 组 | 覆盖 |
|---|---|
| 往返 | 曲目存读、v2 落盘、无曲目时写空数组 |
| **升级** | v1 可读、不隔离、行变单文件曲目、名称变标题 |
| 未来版本 | v9 仍隔离 |
| 畸形数据 | 无 assets 跳过、primary 不在 assets 跳过、无 id 跳过、标题回退到 id、isFlow 推断 |

---

## 6. 一个被自己抓到的错误

`aVersionOneFileStillLoadsAndBecomesSingleTracks` 首版写成：

```kotlin
assertNull("a v1 file must not be treated as corrupt", loaded.problem)
```

**JUnit 的 `assertNull(String, Object)` 是 (消息, 值)**，而 Kotlin 的 `assertNull(Object, String)` 是 (值, 消息)。JVM 版 `kotlin.test` 桥接到 JUnit，参数顺序是**值在前**。

结果消息被当成值断言 —— 消息字符串非 null，于是失败。

**这类错误我已经犯过第三次**（前面 `assertTrue` 也踩过）。教训是：**在 Kotlin 里写断言时不要依赖 JVM 重载推断，参数顺序必须是 (值, 消息)。**

---

## 7. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **404 tests, 0 failures** |
| `./gradlew :app:connectedDebugAndroidTest` | 105 passed（上一轮结果，本轮未改设备代码） |

JVM 合计 **404**。设备 105 是上一轮实际执行结果。

---

## 8. 对照指令「阶段 D」

| 要求 | 状态 |
|---|---|
| **物理音频文件与逻辑曲目的区分** | ✅ |
| **BARS Entry / BWAV Asset / Track / Version 的稳定 ID** | ⚠️ 用户文件用摘要；BARS 侧沿用已有 `StableIds`，**未接入** |
| 多文件批量导入 | ✅ `openFlow` 多选 |
| **目录与本地音频文件自动匹配** | ❌ 未做 |
| **重复文件检测** | ⚠️ 摘要天然去重，但**未显式提示** |
| **资源 SHA-256 与缺失提示** | ✅ `missing()` |
| **资源搬迁后的重新绑定** | ⚠️ 身份支持，**UI 未接** |
| 逻辑赛道分组 | ✅ `StageFlowDiscovery` |
| 作品类别分类 | ✅ `TrackClassifier` |
| 曲目封面与 UI 素材关联 | ❌ 未做 |
| 可播放能力检查 | ✅ 已有 `Playability` |
| 资源导入失败恢复 | ⚠️ 跳过并报告，不重试 |
| **索引版本迁移** | ✅ v1 → v2 |

**核心的两条（物理/逻辑分离、版本迁移）完成；自动匹配与封面未做。**

---

## 9. 仍未做

| 项 | 状态 |
|---|---|
| 目录与本地文件的自动匹配 | ❌ |
| 重复文件的显式提示 | ❌ |
| 封面素材 | ❌ |
| 索引冻结 | **BLOCKED**（4/499 归档） |
| codec 2 | **UNRESOLVED** |
| UI 端到端 | **NOT_VERIFIED** |
| 真机 arm64 | **NOT_VERIFIED** |
