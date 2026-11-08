# AND-37 · v3.0 资源包消化：174 个退出点 + 特别杯门槛

- **checked_at**: 2026-11-08T20:25+08:00 (Asia/Shanghai)
- **证据级别**: JVM 30（新增）+ Python 143
- **关键结论**: 174 个退出点已作为**标注为 marker 的**数据入包；特别杯门槛按 v3.0 的四个条件实现

---

## 1. 新资源包带来了什么

`MKW_DeepSeek_全量资源与开发交接_v3.0` 里有一份此前没有的数据：

| 文件 | 内容 |
|---|---|
| `MKW_BGM_30赛道统一结构化数据_v2.json` | 30 赛道、313 个 stage asset、**174 个退出点** |
| `MKW_BGM_174个退出点_v2.csv` | 同样 174 个退出点的表格形式 |
| `MKW_PLAYER2_UI_页面验收矩阵_v1.0.csv` | **21 页的验收矩阵** |
| `docs/06_特别杯完整约束.md` | 特别杯完整合同（U-01～U-12 回归清单） |

### 之前缺的是什么

我一直在用自己算的 Coaster 数据（`and-27`/`and-28`）。**30 条赛道的退出点位置从来没有过。**

174 个 `Full_ToOutroNN` marker，带 marker id、样本位置、采样率。

---

## 2. 最重要的决定：这批数据叫什么

```kotlin
enum class Provenance {
    L1_MARKER_ONLY,
}
```

**这些是 marker record：L1 元数据，说明「某个记录在这个位置命名了一个点」。**

它**不**说明游戏在这里调度过渡 —— 那是本项目从来没有过的 L3 行为证据。

### 后果是具体的

| 叫法 | 后果 |
|---|---|
| 当作「已验证退出点」 | 应用可以宣称**还原了游戏的退出行为** |
| 当作「marker 位置」 | 应用提供**用户在归档记录的位置选择的过渡** |

**只有第二种是真的。**

所以每门赛道带 `provenance: L1_MARKER_ONLY` 和一句 caveat，界面上必须显示。

解析器**拒绝任何不认识的 provenance**，不默认 —— 未来某份资源若声称 `L3_VERIFIED_EVENTS`，必须响亮失败而不是被当作 marker 读进来。

---

## 3. 数据完整性

`tools/gen_cues.py` 从研究导出生成 `app/src/main/assets/cues.json`：

| 项 | 值 |
|---|---|
| 赛道 | **30** |
| 退出点 | **174** |
| 未映射 | **0** |
| 文件大小 | 26.8 KB |

Rainbow 特殊：`stage_entries = 0`，但**仍有 5 个退出点**（它的文件叫 `M_BgmRainbow.bars.zs`，不遵循 `BGM_Stage_*` 命名）。已正确收录。

### 生成器拒绝写空文件

```
no cues extracted; refusing to write an empty asset
```

**空文件会被读成「这个游戏没有退出点」，而不是「导出格式变了」。**

---

## 4. 三个逐条的丢弃规则

| 情况 | 处理 | 理由 |
|---|---|---|
| cue 没有位置 | 丢弃 | **0 是轨道开头的一个真实位置**，不能当「未知」 |
| cue 没有采样率 | 丢弃 | 默认 48 kHz 是猜测 |
| 赛道一个 cue 都没有 | 整条丢弃 | 空的过渡菜单比没有菜单更糟 |

---

## 5. 顺序在构造时强制

```kotlin
require(cues.zipWithNext().all { (a, b) -> a.frames <= b.frames })
```

**理由**：无序列表会让「下一个 cue」跳过某个点。那个 bug 会表现得像调度错误，而不是数据错误。

---

## 6. 特别杯：v3.0 给出了确切规则

我此前实现的门槛**漏了一个致命条件**。v3.0 的 `docs/06` 写明：

```
eligible = isManifestFrozen && set.isNotEmpty() && allVerified && allCompleted
```

### `set.isNotEmpty()` 是防什么的

**Kotlin 里 `all(emptyList())` 是 `true`。**

一个朴素的「是不是都听过了」检查会**在全新安装、第一次启动、用户还没有任何音乐时，直接解锁特别杯**。

这不是隐蔽的 bug —— 这是那个「显然实现」的默认行为。

### 四个条件各防一件事

| 条件 | 防的是 |
|---|---|
| `isManifestFrozen` | 索引还在建，听到的「全部」不是全部 |
| `set.isNotEmpty()` | **空集陷阱** |
| `allVerified` | 一个播不了的曲目永远无法完成，会让人永远解不开 |
| `allCompleted` | 普通要求 |

---

## 7. 一个顺序问题，改了两轮

我最初把 `INCOMPLETE` 排在 `UNVERIFIED_TRACKS` 前面。

测试失败后我调换了顺序 —— **然后又写了一个断言旧顺序的测试**。

最终顺序：**`UNVERIFIED_TRACKS` 在前**。

**理由**：一个播不了的曲目**永远无法完成**。告诉用户「继续听」是**他照做也解决不了的建议**。

两次都是测试抓到的。

---

## 8. 测试结果（本轮实际执行）

| 命令 | 结果 |
|---|---|
| `python -m pytest tests -q` | **143 passed** |
| `./gradlew :app:testDebugUnitTest` | **634 tests, 0 failures**（上轮 615） |
| 设备 | 110 passed（上轮结果，未改设备代码） |

---

## 9. 游戏 PNG 素材

工作区内已解包的 PNG：

| 位置 | 数量 |
|---|---|
| `readable_assets/toolbox_samples/batch/Tex` | **80,162** |
| `readable_assets/toolbox_samples/batch/Layout` | **4,754** |
| `readable_assets/images` | 3（字体纹理） |

**但这些都是游戏纹理（Alb/Nrm/Mix/Msk 通道），不是 UI 素材**，例如：

```
CmnObj_CoasterMonument000_Monument_01_Alb.png
CmnObj_BigBridgeArchBS000_CmnTex_Stage_BigBridge_Tiling_GlassB_Merged_Nrm.png
```

**没有找到杯赛图标、赛道图标或菜单 UI 素材。**

### 为什么这一轮没有用上

指令要求「不以自己生成的素材冒充原版」，且 v3.0 明确「游戏图标不直接内置于公开 APK」。

**这些纹理是场景贴图，不是 UI 图标。** 把它们贴进界面既不是原版 UI，也不解决任何功能问题。

**诚实的做法是记录「找到了什么、没找到什么」，而不是把一张纪念碑贴图放上首页。**

---

## 10. 仍未完成

| 项 | 状态 |
|---|---|
| 21 页 UI | ❌ 只有第 1 页的雏形 |
| 世界地图 UI | ❌ 数据层有 |
| 路线 UI | ❌ 数据层有 |
| 版本 UI | ❌ 数据层有 |
| 特别杯 UI | ❌ 逻辑完整，无界面 |
| 「Special Cup Appears」音频 | ❌ 未定位 |
| 退出点 UI | ❌ 数据有，未接界面 |
| 真机 arm64 | **NOT_VERIFIED** |
| SAF 选择器 | **NOT_VERIFIED** |
