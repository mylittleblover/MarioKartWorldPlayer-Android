# AND-35 · 路线数据层（阶段 F 前半）

- **checked_at**: 2026-11-08T19:50+08:00 (Asia/Shanghai)
- **证据级别**: JVM 30（全部执行）
- **关键结论**: 五层路线数据独立建模，连接来源强制标注

---

## 1. 五个必须分开的层

指令明确要求区分，**类型系统是唯一能强制执行的地方**：

| 层 | 类型 |
|---|---|
| 地理位置 | `MapPosition` / `StageNode.position: MapPosition?` |
| 实际道路连接 | `RoadConnection`（私有构造 + `ConnectionProvenance`） |
| 道路音乐候选 | `RoadMusicCandidate` |
| 音乐切换配置 | `MusicTransition`（sealed） |
| 用户保存的路线 | `SavedRoute`（私有构造，只引用稳定 id） |

---

## 2. 来源标注是最重要的一条

已有研究在游戏资源里找到部分 `InterStageEx1_*` 道路关系。

**这些可以用，但从命名推断出的连接绝不能冒充已验证。**

```kotlin
internal data class RoadConnection private constructor(...)

internal enum class ConnectionProvenance {
    VERIFIED, INFERRED
}
```

**私有构造 + 必填 provenance** —— 类型上就不可能「忘记标注」。

---

## 3. 未知就保持未知

| 情况 | 处理 |
|---|---|
| 赛道地图位置未确认 | `position = null`，**不是 `0.0, 0.0`** |
| 道路没有确认的音乐候选 | 空列表 + 原因，**不是合理猜测** |

`0,0` 在地图上是一个真实坐标（左下角），用它表示「未知」是把未知洗成了事实。

---

## 4. 随机源可注入

```kotlin
RouteSelection(random: (Int) -> Int, ...)
```

**不用 `kotlin.random.Random` 直接调用** —— 那会让「避免连续重复」这条规则无法确定性测试。

### 一个刻意的回退

当候选集里**每一个**都等于上一首时，返回上一首是正确行为，而不是无限重试。

已作为注释写明这是**有意的回退**，不是遗漏。

---

## 5. 保存的路线只引用稳定 id

```kotlin
internal data class SavedRoute private constructor(...)  // 只含 StageId
```

**不含文件路径。** 用户移动文件不会让保存的路线失效。

---

## 6. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **508 tests, 0 failures**（上轮 479） |
| 设备 | 110 passed（上轮结果，未改设备代码） |

合计 **761**。

---

## 7. 并行子代理的失败与处理

三个子代理同时在这一个工程目录跑 Gradle，**共享 `app/build/`**，导致：

```
Failed to create MD5 hash for file '.../kotlin/class-fq-name-to-source.tab'
Failed to create MD5 hash ...
Failed to delete some children.
```

随后出现**一片虚假的编译错误**：

```
e: .../AudioEngineContractTest.kt:24:26 Unresolved reference 'AudioEngine'
```

**而 `AudioEngine.kt` 完好无损。**

### 处理

1. 中断两个仍在运行的子代理
2. 删除整个 `app/build`
3. 重跑

结果：**508 通过，0 失败**。

### 教训

**并行子代理不应共享同一个 Gradle 构建目录。** 三个模块本身没问题，坏的是并发构建。

以后的正确做法：子代理只写代码不跑构建，或者各给独立的工作副本。

---

## 8. 三个子代理的最终产出

| 模块 | 状态 | 测试 |
|---|---|---|
| `versions/ListeningVersion` | ✅ 已提交 | 28 |
| `routes/RouteModel` | ✅ 已提交 | 30 |
| `unlock/SpecialCupUnlock` | ❌ **未产出**（被中断时尚未写文件） | 0 |

**特别杯解锁（阶段 G）仍需重做。**

---

## 9. 阶段 E / F 进度

| 要求 | 状态 |
|---|---|
| E：保存版本、编辑、重命名、复制、恢复 | ✅ |
| E：版本持久化 | ❌ |
| E：互动播放模式 | ❌ |
| F：五层数据模型 | ✅ |
| F：世界地图浏览 UI | ❌ |
| F：赛道 A → 道路 → 赛道 B 连续播放 | ❌ |
| F：自定义路线保存/载入 | ⚠️ 类型有，持久化无 |

---

## 10. 仍未验证

| 项 | 状态 |
|---|---|
| SAF 选择器 | **NOT_VERIFIED** |
| 真机 arm64 | **NOT_VERIFIED** |
| codec 2 | **UNRESOLVED** |
| 特别杯 | ❌ 未实现 |
| 21 页 UI | ❌ 未开始 |
