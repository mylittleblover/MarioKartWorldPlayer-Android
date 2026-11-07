# AND-06 · 两条播放路径的语义（实测）

- **checked_at**: 2026-11-08T01:05+08:00 (Asia/Shanghai)
- **证据级别**: **S5 实测** —— 17 个 instrumentation 测试全部通过

---

## 1. 为什么单独记这条

`loadLinear` 和 `loadCompiledGraph` + `bindSource` 看起来都能"加载音频"，但**它们的 Play 语义完全不同**。

我在这上面连续犯了两次错，都得到了「全部返回 Ok 但渲染全零」的假象。记下来避免再犯。

---

## 2. 两条路径的 Play 目标

### 有图（`loadCompiledGraph`）

```cpp
// runtime/EngineRuntime.cpp:74
EngineStatus EngineRuntime::startSegment(int target) {
    if(!graph_) { ... }          // 无图走下面
    const auto* object=graph_->node(target);
    if(!object || (object->kind!=Segment && ...)) return InvalidRequest;
```

**必须给真实节点索引**。本测试的图里 segment 索引是 `1`，所以 `Play(1)` 出声。

### 无图（`loadLinear`）

```cpp
if(!graph_) {
    if(target!=-1 || !linear_) return EngineStatus::InvalidRequest;
    ...
    voice.begin(linear_.get(),rate_); ...; clock_.play(); return EngineStatus::Ok;
```

**必须给 `-1`**。`Play(1)` 在 linear 模式下**返回 InvalidRequest**，什么都不会发生。

---

## 3. 两条实测结论

| 路径 | `Play(-1)` | `Play(1)` |
|---|---|---|
| 有图 | 返回 **Ok**，但**不启动任何 segment**，渲染**静音** | **出声** |
| 无图（linear） | **出声** | 返回 **InvalidRequest** |

**注意第一行的陷阱**：有图时 `Play(-1)` 返回 **Ok**，但什么都没启动。**只看返回码会误判为成功。**

这就是为什么测试必须断言**音频**，不能只断言状态码。

---

## 4. 另一个陷阱：`loadLinear` 会替换整个运行时

```cpp
engine->runtime.swap(next); engine->graphSchemaDirty=true; ...
```

`loadLinear` 构造**新的 EngineRuntime**，把已加载的图**丢掉**。

我最初在一个引擎里先 `loadLinear` 再 `bindSource`，结果两次都返回 Ok，然后渲染全零 —— 因为第二次 `bindSource` 面对的是一个**没有图的运行时**，`find(graph->assets, 0)` 找不到资产。

**正确做法**：两条路径用**两个独立引擎**。

---

## 5. 未 Play 前引擎是静音的

实测：`bindSource` 成功后直接渲染 → 全零。必须 `Play` 才出声。

这条不写进测试就会被误读成「解码器坏了」。

---

## 6. 测试覆盖（17 个，全部真实执行）

新增三条把上述语义钉死：

| 测试 | 断言 |
|---|---|
| `transportIsSilentUntilPlayIsSubmitted` | Play 前必须全零 |
| `playingTheSegmentProducesNonSilentAudio` | 有图 + Play(1) 必须出声 |
| `graphPlayWithNoTargetLeavesTheTransportSilent` | 有图 + Play(-1) 必须**静音**（状态码是 Ok，但没启动） |
| `linearPlaybackStartsWithTargetMinusOne` | 无图 + Play(-1) 必须出声 |
| `linearLoadBindsWithoutAGraph` | linear 不需要图 |

---

## 7. 环境问题（记录以备复现）

跑测试时出现过一次 `Test run failed to complete. Expected 17 tests, received 11`，且伴随 `emulator-5556 offline`。

**根因**：之前多次启动模拟器留下了**重复实例**（`emulator-5554` 正常 + `emulator-5556` 掉线），adb 在两者间分配测试导致中断。

**修复**：`Stop-Process` 清掉所有 `emulator`/`qemu-system-*`，重启 adb server，用 `-port 5554` 固定端口单实例启动。

**清理后 17/17 全部通过。**

---

## 8. 状态

| 项 | 状态 |
|---|---|
| 两条播放路径 | ✅ **实测可用** |
| 语义差异 | ✅ **已记录并写成测试** |
| 人耳听音 | **NOT_RUN** |
| kind=2（253 文件） | **仍不支持** |
