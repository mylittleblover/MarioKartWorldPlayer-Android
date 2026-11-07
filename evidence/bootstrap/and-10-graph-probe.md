# AND-10 · 编译图预处理校验

- **checked_at**: 2026-11-08T02:40+08:00 (Asia/Shanghai)
- **证据级别**: **10 个 JVM 测试**（含对已提交 fixture 的真实校验）

---

## 1. 为什么需要这一层

原生 `GraphReader` 对任何问题都抛同一个 `INVALID_COMPILED_GRAPH`，最后统一变成 `LoadResult::InvalidGraph`。

**CRC 错、长度错、magic 错，对调用方完全一样。**

`GraphProbe` 在调 JNI **之前**先查三件事，报出具体原因：

| 检查 | 失败信息示例 |
|---|---|
| magic | `bad magic 0x0; expected 0x50324731` |
| version | `unsupported graph version 99` |
| body 长度 | `declared body 558 does not match 557 present` |
| CRC32 | `crc mismatch: stored 0x6c49205d, computed 0x1234567` |

## 2. 不重复实现解析

`GraphProbe` **只校验信封**（16 字节头 + CRC），**不解析图内容**。

**理由**：解析器写第二份必然与原生的漂移。凡 `GraphProbe` 放过的，引擎仍要再审一遍。这一层的价值是**错误信息**，不是**信任转移**。

## 3. 已验证的事实

对已提交的 `mkw-coaster-graph-v6.hex`（就是 AND-04 里真实播放用的那个图）：

```
version = 6
bodyLength = 557
CRC = 0x6c49205d   ✓ 匹配
```

**这个 fixture 在 JVM 测试里也能被校验通过**，不需要设备。

## 4. 10 个测试

| 测试 | 验证 |
|---|---|
| `acceptsTheCommittedFixture` | 真实 fixture 通过，version=6 body=557 |
| `rejectsShortPacket` | < 16 字节 |
| `rejectsWrongMagic` | magic 错，信息含 "magic" |
| `rejectsUnsupportedVersion` | version 99 |
| `rejectsBodyLengthThatDoesNotMatchThePacket` | 长度不符 |
| `rejectsCorruptedBody` | 翻转最后 1 位，CRC 失败 |
| `rejectsOversizedPacket` | > 4 MiB + 16 |
| `versionRangeMatchesTheNativeReader` | 1..13，与 `GraphProtocolIds.hpp` 一致 |
| `magicIsTheOneTheReaderExpects` | `0x50324731` |
| `rejectionReasonIsNeverEmpty` | 空包也必有具体原因 |

## 5. 关于 CRC 覆盖范围（一个已修正的理解错误）

我构造第一个图时以为 CRC 覆盖整个包，实际**只覆盖 body**（第 16 字节之后）。

`GraphReader.cpp`：

```cpp
const auto body=u32(),checksum=u32();
if(body!=size_-16 || crc(data_+16,body)!=checksum) invalid();
```

`crc(data_+16, body)` —— 从第 16 字节开始，长度 `body`。

这条已经写进 `GraphProbe` 的实现和注释。

## 6. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **45** |
| Android Instrumentation | 17 |
| **合计** | **205** |
