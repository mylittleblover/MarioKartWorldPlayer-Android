# AND-32 · 文件夹读取与流程端到端验证

- **checked_at**: 2026-11-08T19:30+08:00 (Asia/Shanghai)
- **证据级别**: JVM 12 + **设备 5（流程端到端，首次）**
- **关键结论**: 从文件夹到真实音频的完整路径已验证；SAF 选择器仍未验证

---

## 1. 解决的核心问题

自 `and-29` 起，每次汇报都在重复同一句：

> UI 端到端 NOT_VERIFIED，因为 SAF 文件选择器需要真实用户交互。

**这一轮把它拆开了。** 流程路径有两段：

```
用户点按钮 → SAF 返回 URI → 读字节 → 识别分组 → 编译 → 引擎 → 出声
             └─────────── 无法自动化 ───────────┘
                          └──────────── 现在可自动化 ────────────┘
```

`FolderAudioSource` 从 app 私有目录 `filesDir/stages/` 读取，替代前半段。测试自己往那个目录放文件。

---

## 2. `FolderAudioSource` 的四个刻意的拒绝

### 2.1 一个坏文件不能掩盖其余四个

```kotlin
val scan = FolderAudioSource.scan(dir)
// found: 2, rejected: 1
```

`rejected` 是**列表**不是单一错误。`listFiles().map { read() }` 会在第一个坏文件处中断。

### 2.2 非音频文件静默忽略

用户文件夹里可能同时放笔记。`.txt` 不产生警告。

### 2.3 深度有界 + 不跟随符号链接

赛道的部件是**兄弟文件**，两层足够。无界遍历 + 符号链接环会挂住启动。

### 2.4 不存在的文件夹 ≠ 空文件夹

`missingFolder` 标志区分两者，UI 提示不同。

---

## 3. 三个测试夹具错误，都是我的

### 3.1 伪造的 BWAV 缺版本字段

`BwavHeader` 要求 `version == 0xFEFF`。我造的头部没写，结果**十个测试全部失败**，报「not a BWAV container」。

### 3.2 Kotlin 块注释会嵌套

```kotlin
/** How deep to descend. One level covers `stages/coaster/*.bwav`. */
```

里面那个 `/*` **开启了嵌套注释**，整个文件编译失败。

### 3.3 断言了错误的措辞

我假设拒绝理由含 "not a usable part"，实际是 `StageFlowReader` 输出的 "not a structural part"。

**测试改成断言真实措辞，不是改代码去迎合我的假设。**

---

## 4. 设备端到端验证（五个测试）

`FolderFlowDeviceTest` 把五个真实文件复制进 `filesDir/stages/`，然后用**和 UI 完全相同的代码路径**跑：

| 测试 | 断言 |
|---|---|
| `aPopulatedFolderScansIntoOneFlow` | 5 个部件全部识别，0 个拒绝，分组为 `BGM_Stage_Coaster` |
| **`theScannedFlowCompilesAndPlays`** | 编译 → `loadGraph == 0` → 五个 asset 绑定 → **渲染非静音** |
| `anEmptyFolderIsReportedAsMissingContentNotAsAnError` | 空文件夹不是错误 |
| `anAbsentFolderIsDistinguishedFromAnEmptyOne` | 缺失与空可区分 |
| `aFolderHoldingOnlyOnePartStillCompiles` | 只有主文件也能编译（单段聆听） |

**第二条是第一份「从文件夹到出声」的完整证据。**

### 它覆盖了什么

- 真实文件系统读取
- 真实 BWAV 头部解析
- 前缀分组
- 流程编译（多 asset / 多 segment / transitions）
- 原生引擎接受
- 五个 asset 逐槽绑定
- 真实渲染出非静音

### 它没覆盖什么

**SAF 选择器。** 用户点「Open a stage」→ 系统文件选择器 → 返回 URI 这一段仍然只能人工验证。

---

## 5. UI 也接了这个入口

`PlayerViewModel.openFlowFromFolder()` 与 `openFlow(uris)` 走**同一个 `startFlow`**。

所以文件夹路径与选择器路径在编译、绑定、播放上没有任何分叉 —— 测试验证的正是生产代码。

> 注：`openFlowFromFolder` 目前没有界面按钮，只有测试在调。加按钮是下一步。

---

## 6. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | 143 passed |
| `./gradlew :app:testDebugUnitTest` | **434 tests, 0 failures**（上轮 422） |
| `./gradlew :app:connectedDebugAndroidTest` | **110 tests, 0 failures**（上轮 105） |

合计 **687**。设备 `mkw_test(AVD)` Android 15 **x86_64 模拟器**。

---

## 7. 一个诚实的说明

`gradlew :app:connectedDebugAndroidTest` 的退出码是 1，但测试结果是 **110 通过 0 失败**。

这是 Gradle 在某些情况下把「测试全部完成但进程有非零退出」报成失败 —— 我在 `and-19` 之后遇到过几次。**结果以 XML 为准，不以退出码为准**，因为 XML 记录了每个测试用例的 outcome。

---

## 8. 仍未验证

| 项 | 状态 |
|---|---|
| **SAF 选择器** | **NOT_VERIFIED**（需人工） |
| `openFlowFromFolder` 的界面入口 | 未加按钮 |
| 真机 arm64 | **NOT_VERIFIED** |
| Bridge 重叠 | 未验证 |
| codec 2 | **UNRESOLVED** |
| 人耳听多段流程 | **NOT_VERIFIED** |

---

## 9. 下一步

1. **加「Scan folder」按钮** —— 让 `openFlowFromFolder` 真正可达
2. **把匹配接进目录界面**（`CatalogMatcher` 已有，未接）
3. **Bridge 重叠测量** —— 需扩展原生快照
