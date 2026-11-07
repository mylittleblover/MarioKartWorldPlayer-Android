# AND-20 · 曲库持久化

- **checked_at**: 2026-11-08T15:20+08:00 (Asia/Shanghai)
- **证据级别**: **JVM 14 测试 + 设备 6 测试**

---

## 1. 问题

之前每次启动曲库都是空的：用户重复选同一批文件，应用每次都重新测量一遍。

**这不是某个界面的 bug，是缺了一层。**

---

## 2. 做了什么

`LibraryStore` 把曲库写成 `filesDir/library.json`：

```json
{
  "schemaVersion": 1,
  "rows": [
    {"key":"<sha256>","name":"BGM_Stage_Coaster.bwav",
     "frames":3373711,"sampleRate":48000,"playable":true}
  ]
}
```

| 层 | 内容 |
|---|---|
| `LibraryStore` | 读写 JSON，原子替换 |
| `PlayerViewModel` | `init` 恢复、`add`/`remove` 后写回 |
| 界面 | 每行加 `Remove` 按钮 |

---

## 3. 三个刻意的健壮性设计

### 3.1 原子写入

用 `Files.move(REPLACE_EXISTING)` 而不是 `File.renameTo`。

**这是修掉的一个真 bug**：`renameTo` 在 **Windows 上不会覆盖已存在的目标文件**，所以第二次保存会静默失败 —— 而 Android（Linux）上却正常。

我的 JVM 测试跑在 Windows 上，直接暴露了它：

```
savingReplacesRatherThanAppends FAILED
expected:<1> but was:<2>
emptyListIsWritable FAILED
"library could not be replaced"
```

**如果只用设备测试，这个平台差异会被完全掩盖。**

### 3.2 损坏文件隔离而非删除

解析失败时把文件改名为 `library.json.corrupt`，**保留原文件**并报告。

直接删掉会永久丢失用户的曲库，而且没有任何痕迹。

### 3.3 版本不符同样隔离

`schemaVersion` 不匹配时走同样的隔离路径，而不是尝试猜测旧格式。

---

## 4. 关键测试

| 测试 | 断言 |
|---|---|
| `corruptFileIsReportedAndSetAside` | 损坏文件 → 0 行 + 有提示 + `.corrupt` 存在 |
| `unknownSchemaVersionIsSetAsideRatherThanMisread` | 版本不符 → 隔离，不误读 |
| `aRowWithoutAKeyIsSkippedAndCounted` | 缺 key 的行跳过并**计数** |
| `missingOptionalFieldsDoNotBreakTheRow` | 缺字段不崩，且**默认不可播**（保守） |
| `noTemporaryFileIsLeftBehindAfterASave` | 保存后无 `.tmp` 残留 |
| `theFileLivesInAppStorage` | 文件在 `filesDir` 内，未越界 |

**`missingOptionalFieldsDoNotBreakTheRow` 的默认值很重要**：字段缺失时默认 `playable = false`，宁可不让播，也不假装能播。

---

## 5. 移除曲目时的联动

`remove(key)` 会：

1. 若删的是正在播的曲目 → 先 `releaseTransport()`
2. 忘掉它的 prepared 缓存
3. `AudioPicker.prune` 清掉无引用的缓存文件
4. 写回库

**不做第 1 步会导致**：传输继续跑在一个用户看不见也选不中的文件上。

---

## 6. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **217** |
| Android Instrumentation | **52** |
| **合计** | **412** |

---

## 7. 仍未做

| 项 | 状态 |
|---|---|
| 后台播放 / 通知栏控制 | **下一步** |
| 内置 650 条索引 | 未做 |
| 真机 arm64 | NOT_RUN |
| 人耳听音 | NOT_RUN |
| codec 2 解码 | UNRESOLVED |
