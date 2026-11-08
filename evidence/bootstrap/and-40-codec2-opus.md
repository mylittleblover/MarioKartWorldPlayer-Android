# AND-40 · codec 2 是裸 Opus：切割规则的最后一块

- **checked_at**: 2026-11-08T17:20+08:00 (Asia/Shanghai)
- **证据级别**: 全库 253 个 kind-2 声道区域的统计测量
- **关键结论**: **格式已确认是 Opus，切割规则已测出，但「末包长度」这一条还没定**

---

## 1. vgmstream 给出答案

```
BGM_JukeBox_M001B_SMW_Ground_RnR_Outro.bwav
sample rate: 48000 Hz
channels: 2
stream total samples: 7401516 (2:34.198 seconds)
encoding: libopus Opus
layout: layered (2 layers)
metadata from: Nintendo BWAV header
bitrate: 167 kbps
```

**`and-17` 说「全库搜不到 Ogg/Opus magic」** —— 这句话是对的，但推论错了：

**没有 magic 是因为它是裸 Opus 包，没有 Ogg 容器。**

我验证了四类文件：

| 类型 | vgmstream 报告 |
|---|---|
| JukeBox | **libopus Opus** |
| Stage | Nintendo DSP 4-bit ADPCM |
| Demo | Nintendo DSP 4-bit ADPCM |
| Seq | Nintendo DSP 4-bit ADPCM |

---

## 2. 切割规则：209 字节，全库验证

### 决定性证据：**TOC 合法性 253/253**

每个候选包的第一个字节必须是合法 Opus TOC。

```
regions where every packet starts on a legal TOC: 253 / 253
```

**如果步长错了，落在任意字节上，不可能全部合法。**

### 结构

```
payload = [8 字节流头] + N × 209 字节包
```

那 8 字节是 `00 00 01 06 07 6a 93 00`，其后 `F8 7F 24 4A…`：
`0xF8` = `config=31, stereo=0, code=0` = **CELT fullband 20 ms 单帧**。

960 样本/帧 @ 48 kHz。

---

## 3. 但末包长度还没定

`payload % 209 == 0` 只对 **189/253** 成立。

其余 64 个有余数。我试了两个规则：

| 规则 | 符合 | 不符 |
|---|---|---|
| `n = ceil(frames/960)` | 166 尾部为 0 | 23 个尾部 = **209**（多一整包） |
| `n = round(payload/209)` | 218 满足帧数约束 | 35 |

**23 个文件出现 `tail == 209`**，也就是说 `ceil(frames/960)` 算出的包数**少了一个**。

这 23 个的共同点：`frames` 恰好整除 960（`frames - 960n == 0`）。**采样数正好落在帧边界上时，末包仍然存在。**

---

## 4. 这个结果意味着什么

**已确定的部分**：
- 格式是 Opus
- 帧长 960 样本（20 ms）
- 包步长 209 字节
- 253/253 区域的 TOC 全部合法

**未确定的部分**：
- 末包是否可能更短（64 个文件有余数）
- `frames` 与包数的精确对应关系

**这不是「猜不出来」，而是需要 vgmstream 的参照来定标。** 一旦用 vgmstream 解出某个文件的样本数，就能反推那 64 个文件的真实包数。

---

## 5. 我不打算做的事

**不实现「按 209 切、尾部丢弃」。**

理由：64 个文件的余数是 32–209 字节，直接丢会在末尾切掉最多 209 字节的音频。听不出来，但那是**用猜测掩盖未知**。

同样**不实现「按 209 切、末包变长补齐」** —— 也是猜。

---

## 6. libopus 已经在工程里

```cmake
engine/native/CMakeLists.txt:45  add_subdirectory(third_party/opus ...)
engine/native/CMakeLists.txt:47  target_link_libraries(player2_engine PRIVATE Opus::opus)
```

`OpusSource.cpp` 已经在用 `opus_decode_float`，还处理了 `preSkip` 和 `OPUS_SET_GAIN`。

**它现在只吃 Ogg 封装**（`probeOpus` 检查 `OggS` + `OpusHead`）。缺的只是裸包切割。

**所以接入成本很低：一个 `parseBareOpus()`。**

---

## 7. 为什么不打包 vgmstream

| | 裸 Opus + libopus | vgmstream |
|---|---|---|
| 新增依赖 | **无**（已链接） | 整套 + 可能 FFmpeg |
| 需要确认 | 末包规则 | — |
| 许可 | 已合规 | 需新增 |
| APK 体积 | 0 | 显著 |

**vgmstream 留作验证工具** —— 它是这个项目唯一能给出「正确答案」的参照。

---

## 8. 6 声道与 4 声道

你说得对，确实有：

| 声道 | 数量 |
|---|---|
| 1 | 20,146 |
| 2 | 808 |
| **4** | **8** |
| **6** | **29** |

29 个 6 声道中 **22 个是 kind-2**（`Dm_*` 系列，全是开场动画音乐），每个声道**独立一个 kind-2 区域**，所以 209 字节规则**逐声道适用**，不需要改。

`Dm_RainbowAppears.bwav` —— **特别杯演出的候选音频可能就在这里**，6 声道，3,483,690 帧（72.6 秒）。

---

## 9. 测试状态

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | 946 passed |

---

## 10. 下一步

1. 子代理用 vgmstream 作参照，反推那 64 个文件的真实包数
2. 确认末包规则后，实现 `parseBareOpus()`
3. **验证标准：与 vgmstream 逐样本对照**，不是「听起来对」
