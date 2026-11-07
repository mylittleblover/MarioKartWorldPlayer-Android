# `.NN` 名称后缀语义验证（OST-03 前置）

- **checked_at**: 2026-11-07T21:05+08:00 (Asia/Shanghai)
- **证据级别**: **L1（元数据）**
- **问题**: `BGM_Stage_PeachBeach_Outro.yu.48` 结尾的 `48` 是不是 48kHz 采样率标记？

---

## 结论：**数字部分是采样率 ÷ 1000，但它是「目标 wav 的采样率」，不是独立的音频变体**

### 1. 全库采样率实测

对 20,991 个 wav 逐个读 RIFF 头：

| 采样率 | 文件数 | 占比 |
|---|---|---|
| **48,000** | **20,975** | **99.92%** |
| 32,000 | 9 | 0.04% |
| 96,000 | 5 | 0.02% |
| 44,100 | 2 | 0.01% |

**音乐类（`BGM_`/`Dm_`）全部是 48kHz，无一例外。**

那 16 个非 48k 的全是音效/语音/环境：
```
32000 Hz  Env_Bird_Mozu_0/1, Env_Bird_Mushikui_1, SE_RC_GOAL,
          SE_StormCloudBonfire_Small, SE_UI_ItemAlarm, DummyBlank_loop
96000 Hz  SE_KartAction_PressOnWater02, Voice_Luigi_*
44100 Hz  UI_GameSelectDecide, UI_GameSelectSelect
```

### 2. 后缀 token 分布

650 条 BARS 条目的**资产名**里带 `.NN` 后缀的：

| 后缀 | 出现次数 |
|---|---|
| `.yu.48` | 56 |
| `.tk.48` | 2 |
| `.yu`（无数字） | 1 |

**没有一个字母代号同时搭配两种数字** —— 说明 `yu`/`tk` 与 `48` 是正交的两个维度。

### 3. 数字与实测采样率的对应

对全部 58 个带 `.NN` 后缀的源，取其**目标** wav 实测采样率：

```
rate distribution: {48000: 58}
numeric suffix == target rate / 1000 : 58 / 58  ✅
```

**100% 吻合**。`.48` = 48,000 Hz。

### 4. 但 `.yu` / `.tk` 是什么？—— 未能确定

`yu` / `tk` 不是采样率（两者都配 `.48`）。已排除的解释：

- ❌ **不是编解码器**（都是同一份 wav 解码结果）
- ❌ **不是独立音频变体**：`BGM_Stage_PeachBeach_Outro.yu.48` 剥掉后缀是 `BGM_Stage_PeachBeach_Outro`，但库里**根本没有** `BGM_Stage_PeachBeach_Outro.wav/.bwav`（只有 `Outro00`…`Outro07`）
- ❌ 同一条目里也没有第二个 `.tk`/`.nn`/`.mj` 版本可供对比（`bases carrying more than one letter code: {}`）

**目前只能标 `UNKNOWN`。** 一个合理的猜测（**未证实**）是它标记该资源所属的**音频容器组 / 变体命名空间**，因为 15 条"源名剥后缀 ≠ 目标名"的条目全部集中在 PeachBeach 和 RaftingRiver 的 Outro 上；但这只是相关性，没有证据。

按 `docs/00` 规则，**不猜**。

### 5. 那 15 条"源名剥后缀 ≠ 目标名"的条目

```
source  BGM_Stage_PeachBeach_Outro.yu.48
strip → BGM_Stage_PeachBeach_Outro        ← 库里不存在这个文件
target  BGM_Stage_PeachBeach_Outro06      ← 实际 wav
```

其余 43 条剥掉后缀恰好等于目标名。这说明**源名不能直接当 wav 文件名用**，必须走"目标名"。

---

## 对 OST-03 的设计影响

1. **`.NN` 后缀必须在归一化时剥掉**，否则 56 条匹配不上
2. **剥掉后的名字仍可能不是文件名**（15 条），所以**只能用 `internal_name`（目标）做锚点**
3. **采样率匹配不能靠名字里的 `.48` 推断**：应该实读 RIFF 头。虽然 58/58 证明了相关性，但其余 592 条名字里根本没写采样率
4. 音乐类 WAV 全为 48kHz → 若将来发现非 48k 的 `BGM_` 文件，那是**异常信号**，应报错而非静默接受

---

## 本轮未做 / NOT_RUN

- 未验证 `yu` / `tk` 的真实语义（**UNKNOWN**）
- 未解码 PCM、未比对帧数（VER-01 范畴）
- 未做 L2/L3
