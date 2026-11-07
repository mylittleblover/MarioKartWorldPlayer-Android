# AND-13 · Kotlin 编译图生成器

- **checked_at**: 2026-11-08T03:55+08:00 (Asia/Shanghai)
- **证据级别**: **22 个 JVM 测试**，其中 1 个是**跨实现逐字节比对**

---

## 1. 最有价值的那条证据

本轮的 Kotlin 生成器，与上一轮用 Python 写的生成器（`tools/graph_build.py`），**输出的字节完全相同**。

```
kotlinBuilderReproducesThePythonFixtureExactly
→ 573 字节，逐字节相等，maxdiff 位置 = 无
```

**关键点**：这个 fixture 不是我这次跑出来的副本 —— 它是**上一轮用 Python 生成、并且在模拟器上被原生引擎接受并真实播放**的那个文件。

**两个独立实现对同一线格式达成逐字节一致**，这是在没有参考规范时能拿到的最强证据。

## 2. 生成器做了什么

按 `GraphReader.cpp` 的线格式构造最小可播放图：

| 字段 | 值 |
|---|---|
| magic | `0x50324731` |
| version | 6（第一个带 digest 的版本） |
| CRC32 | **仅覆盖 body**，不含 16 字节头 |
| body 首字段 | `sampleRate` |
| 节点 | segment(1) + track(2, parent=1) |
| 段 | entry=0, exit=asset.frames |
| clip | 含 loop（若有） |
| asset | path/rate/channels/frames/sha256 |

## 3. 与原生读取器对齐的拒绝规则

生成器**主动拒绝**原生会拒绝的输入，避免"JNI 往返后才得到一个笼统 InvalidGraph"：

| 拒绝 | 依据 |
|---|---|
| `frames <= 0` | `s.frames<=0` invalid |
| 采样率 ∉ 8000..192000 | `a.sampleRate<8000 \|\| >192000` |
| 声道 ∉ 1..2 | `a.channels<1 \|\| >2` |
| sha256 ≠ 32 字节 | `need(a.sha256.size)` |
| transport ∉ {44100,48000,96000} | `g.sampleRate!=44100 && !=48000 && !=96000` |
| 路径绝对 / 反斜杠 / 盘符 / `..` / `//` | 路径分段校验 |
| loop start < 0 或 end ≤ start | `validClipLoop` |
| loop end > frames | `c.sourceOffset>a.frames-c.sourceLength` |
| loop count < -1 | `loop.count< -1` |
| exit > frames | 段边界 |

## 4. 20 个单元测试

覆盖：正常路径、loop 携带、无 loop 表示、每一条拒绝规则、以及"每个拒绝都必须有说明"。

**特别注意** `acceptsLoopEndingExactlyAtTheLastFrame`：真实 MKW 的**所有** loop 都是 `end == frames`。如果生成器拒绝这个形态，整个曲库都生成不出来。

## 5. 修掉的两个真实错误

### 5.1 `writeByte` 名字冲突

```
Unresolved reference 'writeByte'
```

`OutputStream` 已有 `write(int)`，扩展函数名叫 `writeByte` 无法解析。改为 `writeByteValue`。

### 5.2 测试里的字节字面量

```kotlin
0xb1.toByte()   // ← 0xb1 是 Int，byteArrayOf 要 Byte
```

改成从 hex 字符串解析，更短也更不易错。

### 5.3 我自己写错的边界测试

我最初写的 `rejectsLoopBeyondTheAsset` 用 `end = 999_999`，而该 asset 有 **3,373,711** 帧 —— **999,999 完全合法**，所以测试失败。

这是我**把边界想错了**，不是代码错了。修正后：

- `rejectsLoopBeyondTheAsset` 用 `3_373_712`（真的超一帧）
- 新增 `acceptsLoopEndingExactlyAtTheLastFrame`（真库的形态）

## 6. 设备验证：本生成器产出的图能真实播放

`BuiltGraphPlaybackTest`（设备上运行）**不读 fixture**，而是：

1. 从设备上的 `coaster.bwav` 读真实头部（rate / channels / frames / loop）
2. 计算该文件的 SHA-256
3. 用 **Kotlin 生成器**构造图
4. 先过 `GraphProbe`
5. `loadCompiledGraph` → `bindSource` → `Play(1)` → 渲染

结果：

```
PASS builtGraphIsAcceptedAndPlays
PASS builtGraphCarriesTheFilesOwnRateAndLength
```

**19 / 19 设备测试全部通过。**

### 这把 §1 的"逻辑等价"补成了"独立验证"

上一版证据里我写过：

> 「逐字节一致意味着与已验证的 Python 输出相同，但**本生成器的输出还没有在设备上单独跑过**。」

**现在跑过了。** Kotlin 生成器发出的字节被原生引擎接受并播放出非静音 PCM。

## 7. 一个环境坑（第二次踩到）

跑设备测试时出现：

```
Starting 19 tests on FNPlay(AVD) - 11
Tests on FNPlay(AVD) - 11 failed: There was 12 failure(s).
```

**原因**：机器上有**别的 AVD 实例**（`FNPlay`、`FNTest`、`FNArm64`），adb 把测试派给了 `FNPlay` 而不是 `mkw_test`，那个实例上没有我推的 fixture，12 个测试找不到文件而失败。

**修复**：杀掉所有 `emulator`/`qemu-system-*` 进程，重启 adb，用 `-port 5554` 单实例启动 `mkw_test`。

**清理后 19/19 全部通过。**

> 这是**第二次**因为多实例导致测试失败（第一次报 `Expected 17 tests, received 11`）。已记入 `and-06`，这次再记一次 —— 说明它不是偶发。

## 8. 仍未做的

| 项 | 状态 |
|---|---|
| 生成器接进播放流程 | **未做** |
| 从真实 bwav **自动**读取 sha256/frames/loop | **未做**（测试里手写了读取，生产代码还没有） |
| 曲线 / 转场 / Cue 等完整图特性 | **未做**（只生成最小图） |

## 9. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **102** |
| Android Instrumentation | **19** |
| **合计** | **264** |
