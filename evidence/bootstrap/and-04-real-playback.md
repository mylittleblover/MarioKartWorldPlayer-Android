# AND-04 · 真实 BWAV 端到端播放验证（S5 达成，含真实解码）

- **checked_at**: 2026-11-08T00:25+08:00 (Asia/Shanghai)
- **证据级别**: **S5 真实设备执行 + 真实 PCM 非静音**
- **关键突破**：修复了 BWAV 解析器对 MKW 真实文件的**两处不兼容**，从「绑定失败」到「解出 8 段非静音 PCM」

---

## 1. 结果

| 项 | 值 |
|---|---|
| Instrumentation 测试 | **14 / 14 通过** |
| 真实音频源 | `BGM_Stage_Coaster.bwav`（3,855,900 字节） |
| 引擎图 | 自建 v6 编译图（573 字节，CRC 校验通过） |
| 渲染结果 | **peak = 0.131，8,189 / 8,192 个非零采样** |
| 传输状态 | Playing(1)，position 随时间推进 |

**这是本项目第一次证明：能解真实 MKW 游戏音频，且输出可听范围的 PCM。**

---

## 2. 修掉的两个真实缺陷

### 2.1 末块截断（2403 个文件被误判）

**现象**：`bindSource` 返回 `EngineStatus::InvalidRequest`。

**根因**：原解析器要求每个 DSP 声道的数据区必须有完整的 8 字节块：

```cpp
const auto encoded = kind ? ((samples+13)/14)*8 : samples*2;
input.need(start, encoded);     // ← 要求完整块
```

但 MKW 的实际布局是：

- **最后声道的最后一块被截断**到刚好所需的字节数 = `1` 个头字节 + `ceil(剩余采样/2)` 个半字节字节
- 其余声道补齐到完整块
- 每个声道起点 = 上一声道（按完整块计的）末尾向上对齐到 **64 字节**

**全库实测**（20991 个 bwav）：

| 类别 | 数量 |
|---|---|
| 末块补齐到完整 8 字节 | 15,044 |
| 末块截断 | 5,694 |
| 非 DSP 编码（kind=2） | 253 |
| **合计** | **20,991** |

其中音乐文件 647 个：截断 140、补齐 254、kind2 253。

**修复**：接受截断形式，只保证 `minimum` 字节存在：

```cpp
const auto finalSamples = samples - (blocks-1)*14;
const auto minimum = (blocks-1)*8 + 1 + (finalSamples+1)/2;
input.need(start, minimum);
previousEnd = start + (ch+1==count ? minimum : encoded);
```

读取仍由帧数约束，不会越界。

### 2.2 初始预测器校验过严（654 个文件被误判）

**现象**：修完 2.1 后仍有文件失败。

**根因**：原解析器要求头部 `u16(h+68)` 必须等于数据区第一个字节：

```cpp
if(input.u16(h+68)!=input.u8(start) || input.u16(h+68)>0x7f) fail(...);
```

**实测**：

| 情况 | 文件数 |
|---|---|
| `u16(h+68) == 首字节` | 20,084 |
| **不相等但 ≤ 0x7f** | **654** |
| 不相等且 > 0x7f | 0 |

`BGM_Stage_Coaster.bwav` 就是其中之一：头部 `39`，首字节 `53`。

**为什么原校验是错的**：该字段是**跳转用的历史种子**（predictor/scale + 两个历史采样），不是首块头字节的副本。MKW 里有 654 个文件两者本就不同。

**修复**：只保留范围校验（predictor ≥ 8 无法解码）：

```cpp
if(input.u16(h+68)>0x7f) fail(SourceStatus::MalformedSource);
```

---

## 3. 端到端链路（每一步都真实执行）

```
1. createEngine()                              → 句柄非零
2. loadCompiledGraph(573 字节 v6 图)            → LoadResult::Ok
3. engineSampleRate()                          → 48000
4. bindSource(asset=0, "native.bwav", 真实 bwav) → EngineStatus::Ok
5. command(Play, target=1)                     → EngineStatus::Ok
6. renderRealtime × 8 (512 帧 × 2 声道)         → Ok，peak=0.131，8189 非零采样
7. snapshot()                                  → status=Playing, position 递增
```

---

## 4. 两个重要的行为规律（实测，非假设）

### 4.1 未 Play 前引擎是**静音**的

先渲染再 Play → 全零。必须 `Play` 后才有输出。

**这推翻了我最初的测试写法**：我原本在一个引擎里先 `loadLinear` 再 `bindSource`，结果两者都返回 Ok 但**渲染全零**。

### 4.2 `loadLinear` 会**替换整个引擎运行时**

它丢弃已加载的图。所以两个路径必须用**两个独立引擎**测试。

我最初把它们放在同一个引擎里跑，得到「两条路径都 Ok 但都静音」的错误结论。

---

## 5. 自建的编译图

MKW 目前没有可用的真实图，所以我按 `GraphReader.cpp` 的线格式**从零构造**了一个最小 v6 图：

| 字段 | 值 |
|---|---|
| magic | `0x50324731` |
| version | 6 |
| body | 553 字节 |
| CRC32 | 校验通过 |
| sampleRate | 48000 |
| identities | asset, segment, track, clip |
| nodes | segment(1) + track(2, parent=1) |
| segment | entry=0, exit=3373711, 无 loop |
| clip | asset=0, sourceLength=3373711 |
| asset | `BGM_Stage_Coaster.bwav`, 48000 Hz, 2ch, 3373711 帧 |

**构造过程中修正了三处格式错误**（都是我最初理解错的）：

1. CRC 只覆盖 body，不覆盖头部
2. body 开头是 `sampleRate`，不是 identities 计数
3. clip 的布局是 `(index, asset, start, sourceOffset, sourceLength)`，我漏了 `start`

这些错误都由我自己写的解析器先发现，再用真实引擎验证。

---

## 6. 测试清单（14 个，全部真实执行）

| 测试 | 验证内容 |
|---|---|
| `nativeLibraryLoads` | .so 可 dlopen |
| `transportSampleRateIsPositive` | JNI 调用成功 |
| `engineCanBeCreatedAndDestroyed` | 引擎生命周期 |
| `engineSampleRateMatchesTransport` | 采样率一致性 |
| `engineSnapshotIsReadable` | 快照可读 |
| `renderProducesFramesWithoutCrashing` | 无崩溃渲染 |
| `nativeLibrariesArePackagedInTheApk` | 四个 .so 在 APK 内 |
| `compiledGraphLoads` | **真实图加载** |
| `realBwavBindsToTheCompiledGraph` | **真实 bwav 绑定成功** |
| `transportIsSilentUntilPlayIsSubmitted` | 未 Play 时静音 |
| `playingTheSegmentProducesNonSilentAudio` | **解出非静音 PCM** |
| `renderedPcmStaysInsideTheNormalisedRange` | 无 NaN/Inf/越界 |
| `transportAdvancesWhilePlaying` | 传输时钟推进 |
| `linearLoadBindsWithoutAGraph` | 无图路径可绑定 |

---

## 7. 仍然 NOT_RUN

| 项 | 状态 | 原因 |
|---|---|---|
| **人耳试听** | **NOT_RUN** | 模拟器 `-no-audio`，无扬声器验证 |
| **音频内容是否正确** | **NOT_RUN** | 只验证非静音、范围合法；未与参考波形比对 |
| **loop 点接缝听感** | **NOT_RUN** | 同上 |
| **长时间连续播放** | **NOT_RUN** | 只跑 8×512 帧（约 85 ms） |
| **真机 ARM 设备** | **NOT_RUN** | 仅 x86_64 模拟器 |
| **kind=2 编码（253 个文件）** | **NOT_RUN** | 解析器仍拒绝；这些是 JukeBox/Demo 曲目 |
| DSP 解码算法正确性 | **未自证** | 无独立参考解码器对照 |

> **必须说清**：`peak=0.131` 只证明**有波形、在合法范围内**。它**不证明**解出来的音乐是正确的。
> 一个错误的解码器只要输出噪声，也能通过这个测试。

## 8. AND-04 状态

**PARTIAL（S5 真实播放达成，S6 听音未做）**

- 达成：真实 bwav 解码、图加载、完整播放链路、非静音输出
- 未达成：人耳验证、内容正确性、kind=2 编码支持
