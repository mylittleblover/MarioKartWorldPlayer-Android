# AND-14 · BWAV 头部读取器

- **checked_at**: 2026-11-08T04:20+08:00 (Asia/Shanghai)
- **证据级别**: **17 个 JVM 测试**

---

## 1. 补齐了生成器缺的那一半

AND-13 的生成器需要 `rate / channels / frames / loop` 四个参数，但**没人提供它们**。

`BwavHeader` 补上这一步：**从容器头部直接读出生成图所需的一切**，不经过 JNI、不解码采样。

| 字段 | 偏移 | 来源 |
|---|---|---|
| codec kind | `16+0` | 决定能否播放 |
| sampleRate | `16+4` | 图资产字段 |
| frames | `16+12` | 图资产字段 |
| loopEnd | `16+60` | clip loop |
| loopStart | `16+64` | clip loop |

（`16` 是文件头长度，每声道头 76 字节。）

## 2. 读头部不解码，是刻意的

只看 76 字节/声道的头部，**从不触碰采样数据**。

理由：判断"这个文件能不能播"不需要解码。如果每次都要完整解码才知道，曲库列表就得对 650 个文件逐个解码 —— 在手机上是几秒的卡顿。

## 3. 拒绝规则

| 情况 | 处理 |
|---|---|
| 文件 < 16 字节 | 拒绝 |
| magic ≠ `BWAV` | 拒绝 |
| version ≠ `0xFEFF` | 拒绝 |
| 声道数 = 0 | 拒绝 |
| 头部放不下 | 拒绝 |
| 采样率 ∉ 8000..192000 | 拒绝 |
| frames ≤ 0 | 拒绝 |
| **各声道 rate/frames/kind 不一致** | 拒绝 |
| **有 loop start 但 loop end 是哨兵** | 拒绝 |
| loop start ≥ loop end | 拒绝 |
| loop end > frames | 拒绝 |
| `loopEnd == frames` | **接受**（真实库的唯一形态） |

**全部返回 `Rejected(reason)`，不抛异常。** 读不了是常态，不是异常。

## 4. 一个测试暴露了我的构造函数错误

`rejectsLoopStartWithoutALoopEnd` 失败：

```
expected Rejected, got Ok(... loopStart=null, loopEnd=null)
```

**原因**：我的测试构造函数在 `loopEnd == null` 时把 `loopStart` 也强制写成 0 —— 于是根本构造不出"有 start 没 end"这个场景。

**被测代码是对的，测试夹具是错的。**

修正：哨兵分支也写入传入的 `loopStart`，这样才能真的构造出不一致状态。

> 这已经是本轮**第三次**出现「代码对、测试写错」（前两次是 loop 边界和 entryCount 比较）。记下来：**测试失败时先确认夹具，再改代码。**

## 5. 仍未做

| 项 | 状态 |
|---|---|
| 把 `BwavHeader` + `GraphBuilder` 串成一条播放流程 | **未做** |
| UI 调用它们 | **未做** |
| 文件选择器 | **未做** |

**现在三个零件都在（读头 / 建图 / 播放），但还没有串起来。**

## 6. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **119** |
| Android Instrumentation | 19 |
| **合计** | **281** |
