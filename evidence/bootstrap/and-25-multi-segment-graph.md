# AND-25 · 多 Asset / 多 Segment 音乐图（阶段 B）

- **checked_at**: 2026-11-08T17:10+08:00 (Asia/Shanghai)
- **证据级别**: **JVM 23 + 设备 4（真实原生播放）**
- **关键结论**: 原生引擎接受并播放了三 Asset / 三 Segment 的图

---

## 1. 先说一个重要发现

**Native Runtime 早就支持多 Asset / 多 Segment。**

`engine/native/compiled/Graph.hpp` 里：

```cpp
struct Graph {
    std::vector<Segment> segments;   std::vector<Track> tracks;
    std::vector<Playlist> playlists; std::vector<Switch> switches;
    std::vector<Transition> transitions; std::vector<Asset> assets;
    ...
    bool hasMusicalLoop{}; MusicalLoop musicalLoop;
    bool hasAutomatic{};   Automatic automatic;
};
```

`Segment` 带 `entry / exit / loop / cues`，`Transition` 带 `bridge / preEntry / postExit / fadeIn / fadeOut / sync / entry`。

**缺的只是 Kotlin 的写入器** —— 原来的 `GraphBuilder` 只发单段图，因为单文件播放只需要那些。

所以阶段 B 是**补齐写入器**，不是扩展引擎。这避免了「重新建立第二套互动音乐引擎」这条禁止项。

---

## 2. 新增 `MusicGraphBuilder`

支持：多 Asset、多 Segment（各自 entry/exit/loop/cues）、多 Track 多 Clip、Transition（含 bridge、pre/post、fade）、MusicalLoop（intro/loop/ending）。

### 字段顺序的来源

全部抄自 `engine/native/music/GraphReader.cpp`，**不是抄文档**。每一处写入对应读取器的一次调用。

### 校验在 Kotlin 侧先做

读取器拒绝时只给 `INVALID_COMPILED_GRAPH`，对定位问题毫无帮助。所以每个读取器约束在 Kotlin 侧重做一遍，报具体位置：

```
segment 10 loop start 2000 is before its entry 0
clip 35 loop end 5000 exceeds source window 0..1000
track 30 names segment 99, which is not in the graph
```

---

## 3. 用「逐字节等价」证明新路径不是第二种方言

最强的、不需要设备的检查：**让通用写入器构造那条已经被原生接受并播放过的 Coaster 单段图，逐字节比对**。

```kotlin
val expected = fixture()                       // tools/graph_build.py 产出，设备已验证
val actual   = MusicGraphBuilder.build(coasterSpec())
assertContentEquals(expected, actual)          // 通过
```

同时断言它与旧的 `GraphBuilder` 输出也一致。

**如果两条路径产出不同字节，就说明其中一条是错的。**

---

## 4. 抓到三个只有真实引擎才会暴露的问题

### 4.1 Transition 必须有 Node 实体

第一版 `loadGraph` 返回 `1`（`InvalidRequest`）。

原因在 `GraphValidation.hpp`：

```cpp
for(const auto& t:g.transitions) {
    if(!define(t.index,Kind::Transition) || ...) return false;
```

`define` 内部调用 `typed(id, kind)` → `g.node(id)`。**transition 只列在 transition 表里是不够的，它还需要一个 kind=9 的 Node。**

单段图没有 transition，所以这条直到加了 transition 才出现。

### 4.2 索引是**全局唯一**的

```cpp
// Stable identity indices have one owner across nodes and nested DTOs.
auto claim=[&](int id) { if(... || owned[id]) return false; ... };
```

节点、cue、clip、asset、transition 的索引**共享一个命名空间**，不能重复。所以我把 cue/clip/transition 索引分配在互不重叠的区间（20/30/40…）。

### 4.3 索引必须小于 identities 长度

`index()` 在读取时校验 `value >= identities_` → 拒绝。

所以 identities 表长度必须 **> 最大索引**，而不是「节点数」。

---

## 5. 设备实测：多段图真的出声

`MultiSegmentPlaybackTest`，4 个测试，全部通过：

| 测试 | 断言 |
|---|---|
| `theEngineAcceptsAThreeAssetThreeSegmentGraph` | `loadGraph == 0`，采样率 48000 |
| `threeAssetsBindIndependently` | asset 0/1/2 各自绑定成功 |
| `eachSegmentStartsAndRendersAudio` | **Pre(10) / Bridge(11) / Main(12) 各自 Play 后渲染非静音** |
| `theTransportIsSharedAcrossSegments` | 跨段后 transport 帧号**不回退** |

用的是真实游戏文件：

```
coaster-Pre.bwav      2,774,429 字节  2,427,426 帧
coaster-Bridge.bwav     101,160 字节     88,320 帧
coaster.bwav          3,855,900 字节  3,373,711 帧
```

图结构：

```
Segment 10  Pre    ──transition 40──▶  Segment 12  Main（loop 493714..3373711）
                                       bridge = Segment 11 Bridge
```

**`theTransportIsSharedAcrossSegments` 是其中最关键的**：它证明切换段落是同一个时钟继续走，而不是每段一个时钟。这正是指令禁止的「多个 MediaPlayer 手动计时拼接」会失败的地方。

---

## 6. 尚未接进 UI

`MusicGraphBuilder` 目前只有测试在调用。`TrackOpener` / `PlayerViewModel` 仍走单段 `GraphBuilder`。

**这是有意的顺序**：先证明引擎能播，再改 UI。否则界面会先于能力存在。

下一阶段（C）会把 Coaster 的五段流程接进去。

---

## 7. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **279 tests, 0 failures** |
| `./gradlew :app:connectedDebugAndroidTest` | **78 tests, 0 failures** |

设备：`mkw_test(AVD)` Android 15 x86_64。

合计 **143 + 279 + 78 = 500**。

---

## 8. 未验证

| 项 | 状态 |
|---|---|
| Transition 的实际触发（`Play` 换段 ≠ 走 transition） | 未验证 |
| MusicalLoop 的运行时行为 | 未验证（只验证了图能被接受） |
| Cue 触发 | 未验证 |
| Bridge 与主体重叠播放 | 未验证 |
| 真机 arm64 | 未验证（模拟器 x86_64） |
| 原生 CTest | 未运行（本轮未修改 `engine/native/`） |

**`eachSegmentStartsAndRendersAudio` 用的是 `Play(segment)` 直接起段，不是走 transition。** transition 的真正执行需要事件驱动，那是阶段 C 的工作。

---

## 9. 阶段 B 对照指令清单

| 要求 | 状态 |
|---|---|
| 多个 Audio Asset | ✅ 设备验证 |
| 多个 Music Segment | ✅ 设备验证 |
| 一个 Segment 中多 Track / Clip | ✅ 构建器支持，**未设备验证** |
| Segment / Clip 不同循环语义 | ✅ 构建器支持，clip 循环设备已验证 |
| Cue 与同步位置 | ✅ 写入支持，**未验证触发** |
| Music Transition | ✅ 写入支持，**未验证执行** |
| Bridge / Transition Segment | ✅ 写入支持，**未验证重叠** |
| Pre-entry / Post-exit / Fade | ✅ 写入支持，**未验证执行** |
| 多个可选 Outro | ✅ 写入支持，**未验证执行** |
| 过渡请求状态监控 | ❌ 未做 |
| 预加载 / 流式 / 截止时间错误 | ❌ 未做 |

**写入层完成，执行层大部分未验证。** 这是本轮的真实边界。
