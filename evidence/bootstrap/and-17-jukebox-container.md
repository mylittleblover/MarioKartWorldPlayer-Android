# AND-17 · JukeBox（BWAV codec 2）容器解析

- **checked_at**: 2026-11-08T10:40+08:00 (Asia/Shanghai)
- **结论**: **容器结构已解**，**编码仍未解**（`DECODE_UNRESOLVED`）
- **影响面**: 253 个文件（227 JukeBox + 22 Cutscene + 3 Demo + 1 Sequence）

---

## 1. 已确认的容器结构（253/253 文件，594 声道，零例外）

每个 kind=2 声道的区域是**两个连续 chunk**：

```
u32 tag, u32 size, payload[size]

  tag 0x80000001, size 24    ← 配置块，六个 u32
  tag 0x80000004, size N     ← 编码音频
```

**所有 253 个文件的配置块完全相同**：

```
256, 48000, 32, 0, 0, 120
```

区域末尾补齐到 64 字节（最多 63 字节 padding）。

### 验证方式

对全库 253 个 kind=2 文件逐声道解析：

```
kind=2 files: 253, channels: 594
tag histogram: 0x80000001 x594(ok) · 0x80000004 x594(ok) · 0x0 x299(区域末端 padding)
```

**每个声道的 chunk 序列都是恰好 (config, audio)，零例外。**

---

## 2. 编码未解的判据

### 2.1 平均码率恒定

| 指标 | 值 |
|---|---|
| 字节/采样 | 0.217708 – 0.217878 |
| 离散度 | **0.078%** |
| 折算码率（48kHz） | **83.600 – 83.665 kbps** |

跨 250 万到 1550 万采样的完全不同内容，码率差异不到 0.08%。

**这是「按目标码率编码」的特征**（如 Opus VBR），不是固定块格式。

### 2.2 已排除的假设

| 假设 | 结果 |
|---|---|
| 未压缩 PCM | ❌ 0.2177 字节/采样，不是 2/3/4 |
| Ogg / OpusHead / OpusTags | ❌ 全库搜不到任何 magic |
| zstd 压缩 | ❌ frame header 无效 |
| **变长包链（u8/u16/u24/u32 × 大小端 × 对齐 1/16/64 × 起始 0..4095）** | ❌ **全部 0 个精确匹配** |

最后一条是穷举：**48 种组合 × 每个偏移都试过**，没有一种能恰好消费完 payload。

### 2.3 为什么停在这里

编码可能是 Opus（83.6 kbps 很典型），但：

1. **没有包边界信息** → 无法切包
2. **没有参考解码器可对照** → 即使猜出一个能出声的实现，**也无法证明对**
3. 交接包规则明确：`Prefetch fragments and NXOpus require their own admission, never guessing`

**所以只解析容器，不解码音频。**

---

## 3. 落地到代码

`Kind2Chunks` 现在能：

- 定位配置块与音频块
- 读出 6 个配置字段
- 从 `size / samples * 8 * rate` 推算码率
- 生成人类可读描述

```kotlin
Kind2Chunks.describe(layout)
// → "BWAV codec 2 (chunked, 83.6 kbps at 48000 Hz)"
```

**UI 的"不支持"提示现在能带上具体信息**，而不是只说一句 unsupported。

### 15 个测试钉住

包括：配置块 6 字段、音频块偏移、码率计算、padding 上限（63 接受 / 64 拒绝）、错误 tag、越界 size、无配置块、零采样不除零。

---

## 4. 这对用户意味着什么

| 之前 | 现在 |
|---|---|
| JukeBox 显示 "UnsupportedCodec" | 显示 **"BWAV codec 2 (chunked, 83.6 kbps at 48000 Hz)"** |

**诚实但没有进展的感觉不同**：
- 之前是"不知道这是什么"
- 现在是"知道它是什么形态，只是还没有解码器"

---

## 5. 仍未做

| 项 | 状态 |
|---|---|
| codec 2 解码 | **UNRESOLVED** |
| 227 首 JukeBox 播放 | **不可用** |
| 22 首 Cutscene 播放 | **不可用** |

**继续攻 codec 2 需要的**：一份该格式的说明，或一个已知正确的解码器做对照。两者目前都没有。

---

## 6. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **179** |
| Android Instrumentation | 39 |
| **合计** | **361** |
