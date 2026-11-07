# VER-02 · BWAV Loop 与源坐标验证

- **checked_at**: 2026-11-07T22:05+08:00 (Asia/Shanghai)
- **证据级别**: **L2 的坐标维度** —— loop 坐标已解出并全库校验；**听音 NOT_RUN**
- **数据**: 20,991 个 `.bwav` 全部映射，0 错误

---

## 1. 映射结果

| 指标 | 数值 |
|---|---|
| 成功映射 | **20,991 / 20,991** |
| 映射错误 | **0** |
| 带 loop | **2,881** |
| 无 loop | **18,110** |
| 其中音乐（`BGM_`/`Dm_`） | 647 个，**174 个带 loop** |

## 2. 坐标换算规则

```
源采样帧 → 秒：  seconds = frames / sample_rate
```

**用文件自身的采样率，绝不假设 48kHz。** 已写成测试：32kHz 文件必须换算成不同秒数，否则测试失败。

明确区分两套坐标（对齐 `docs/02`）：

| 坐标 | 含义 | 本模块是否产出 |
|---|---|---|
| `SourceSampleFrame` | 源音频帧索引 | ✅ 产出 |
| `TransportFrame` | 运行时全局时钟 | ❌ **不产出**（属 Runtime） |

## 3. 全库校验结果

| 校验项 | 结果 |
|---|---|
| `loopEnd == frameCount` | 2,881 / 2,881 ✅ |
| `loopStart <= loopEnd` | 2,881 / 2,881 ✅ |
| 无 loop 哨兵 `(0xFFFFFFFF, 0)` | 18,110 / 18,110 ✅ |
| 指纹一致性（`+24 == +28`） | 20,991 / 20,991 ✅ |

**零违规。**

## 4. 34 个赛道主曲的 loop 长度

| 赛道 | 循环长度 | 帧数 |
|---|---|---|
| Central | 57.000 s | 2,736,000 |
| Coaster | 60.000 s | 2,879,997 |
| PeachBeach | 61.440 s | 2,949,120 |
| WestCity | 52.500 s | 2,520,000 |
| MooMooFarm | 58.125 s | 2,790,000 |
| WarioStadium | 72.000 s | 3,455,996 |
| ChocoMountain | 89.600 s | 4,300,800 |
| …其余 27 条 | 29.965 – 75.200 s | — |

**关键观察**：只有 **1/34** 是整秒，**4/34** 是 0.1 秒整数倍。

→ **loop 长度不是按秒设计的，是按音乐小节设计的。** 我尝试用「小节数 × 拍数 × 60/BPM」反推，174 个音乐 loop 里只有 27 个能匹配常见 BPM 网格 —— **不成立**，所以**没有引入任何 BPM 假设**。

这很重要：说明 loop 点是**制作端按实际音频对齐的**，不是可计算的。播放器必须**读**它，不能**推**它。

## 5. loop 形态：只有一种

```
[BGM 开头 ... 前奏] [loopStart ───────────→ frameCount]
                      ↑ 循环区间（永远到结尾）
```

全库 2,881 个循环**无一例外** `loopEnd == frameCount`。没有中段循环。

## 6. 边界情况

| 情况 | 行为 |
|---|---|
| `loopStart == frameCount`（零长度循环） | 记录为长度 0，**不报错**（数据合法） |
| `loopStart == 0`（整文件循环） | 合法，长度 = 全曲长 |
| `loopEnd != frameCount` | **抛 `SampleMapError`** |
| `loopStart > loopEnd` | **抛 `SampleMapError`** |
| 无 loop 哨兵配非零 start | **抛 `SampleMapError`** |
| 非 BWAV 容器 | **抛 `SampleMapError`** |
| 采样率 ≤ 0 | **抛 `SampleMapError`** |

## 7. 产出物

`reports/generated/loop-map.json` —— 20,991 条完整记录，每条含：
`path / sampleRate / channels / frameCount / durationSeconds / hasLoop / loopStartFrame / loopEndFrame / loopStartSeconds / loopEndSeconds / loopLengthFrames / loopLengthSeconds`

（含本机路径，已 gitignore）

## 8. 明确未验 / NOT_RUN

| 项 | 状态 | 说明 |
|---|---|---|
| **loop 接缝听感** | **NOT_RUN** | 帧号对 ≠ 听起来无缝。没人耳或波形零交叉验证 |
| **loop 点与原版游戏行为一致** | **NOT_RUN** | 需 L3 |
| **DSP 原始解码** | **UNRESOLVED** | 手写解码器未复现参考 PCM |
| **BARS `Full_ToOutroNN` 与 bwav loop 的关系** | **NOT_RUN** | marker 位置仍 UNRESOLVED |

> **特别提醒**：`loopStart` 是**源采样帧坐标**。它是否对应"音乐上的正确接缝"，需要听音或波形分析确认。本模块只保证**读取正确**，不保证**音乐正确**。

## 9. 对播放器实现的影响

1. loop 点**必须从源文件读**，不能按 BPM 或小节推算
2. 只需支持 `[loopStart, frameCount)` 一种形态
3. 无 loop 时用 `None` 表示，**不能填 0**（0 是合法的 loopStart）
4. 换算必须用文件自身采样率
5. loop 坐标是 SourceSampleFrame，**不是 TransportFrame**，进入 Runtime 时必须显式转换
