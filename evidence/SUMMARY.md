# MKW Player · 阶段总结

- **生成时间**: 2026-11-08T17:20+08:00 (Asia/Shanghai)
- **仓库**: `mylittleblover/MarioKartWorldPlayer-Android`
- **最新提交**: `c0c9c43`
- **APK**: `MKW-Player-0.1.0-alpha01.apk`（39.04 MB，四 ABI，测试签名）

---

## 1. 当前能力（可验证）

| 能力 | 证据 | 级别 |
|---|---|---|
| 构建四 ABI APK | 39.04 MB，20 个 `.so`，`assets/catalog.json` 已打包 | **S4** |
| 真机播放真实游戏音乐 | iQOO Z9 Turbo (arm64-v8a)，人耳确认 | **S6** |
| 解码结果逐样本正确 | 52,000 采样 maxdiff = 0 | **L2** |
| Oboe 真实开流 | 48000 Hz，54 次回调/500ms，CPU p99 占预算 19% | **S5** |
| 后台播放 | 按 Home 后继续，通知栏可控 | **S6** |
| 曲库持久化 | 重启后保留，损坏文件隔离不删除 | **S5** |
| 内置 650 条目录 | 397 可播，覆盖声明原样保留 | **S5** |
| 音频焦点 | 5 种事件策略全 JVM 覆盖 + 设备实测请求/释放 | **S5** |
| 多 Asset / 多 Segment 音乐图 | 三 Asset 三 Segment 在设备上出声，共享单一时钟 | **S5** |
| BWAV 全库结构解析 | 20,991 个文件全部映射 | **L1** |
| JukeBox codec 2 容器 | 253/253 文件、594 声道零例外 | **L1** |

---

## 2. 明确的限制（必须说清）

| 限制 | 影响 | 状态 |
|---|---|---|
| **codec 2 无法解码** | 253 个文件，含 **227 首 JukeBox** + 22 首 Cutscene | **UNRESOLVED** |
| **只听过一首** | 仅 `BGM_Stage_Coaster` 有人耳确认 | 其余未测 |
| **目录不完整** | 源数据只覆盖 499 个归档中的 **4 个** | **PROVISIONAL_INCOMPLETE** |
| **过渡执行未验证** | Transition / Cue / Bridge 重叠 / MusicalLoop 运行时未实测 | **NOT_VERIFIED** |
| **ducking 未实现** | 通知音时继续全音量（平台允许） | 有意的取舍 |
| **标记名绑定未确认** | 两份分析产物矛盾 127/165 | `UNCONFIRMED` |

---

## 3. 未做/未测清单

| 项 | 状态 |
|---|---|
| Transition 实际触发验证 | 未验证 |
| Cue 触发与 Bridge 重叠 | 未验证 |
| MusicalLoop 运行时行为 | 未验证 |
| 耳机 / 蓝牙路由切换 | 未测 |
| 长时间连续播放稳定性 | 未测 |
| COURSE 以外类别的人耳验证 | 未测 |
| 真实曲库目录播放（需用户给文件夹） | 未实现 |
| 通知栏封面图 / 进度条 | 未实现 |

---

## 4. 测试

| 套件 | 数量 | 状态 |
|---|---|---|
| Python (`tests/`) | 143 | ✅ |
| Kotlin JVM | 279 | ✅ |
| Android Instrumentation | 78 | ✅ |
| **合计** | **500** | **0 失败** |

**另有 1 项人工验证**（S6 听音），不计入自动化测试数。

---

## 5. 关键缺陷（已修）

| 缺陷 | 影响面 | 发现方式 |
|---|---|---|
| BWAV 末块截断未处理 | **5,694** 个文件被拒 | 全库实测 |
| 预测器种子校验过严 | **654** 个文件被拒 | 全库实测 |
| `gen_catalog.py` 循环点偏移错 | 650 条目录**全部丢失循环点** | 设备断言 |
| 前台服务对空闲会话启动 | **进程崩溃**（`ForegroundServiceDidNotStartInTime`） | 设备测试 |
| `stopForeground(DETACH)` | 停止后通知栏残留控件 | 设备测试 |
| `File.renameTo` 不覆盖（Windows） | 第二次保存静默失败 | JVM 测试 |
| 引擎快照与命令队列竞态 | 服务拒绝启动 | 设备测试 |

**前两条最有价值** —— 它们让「播放真实游戏音频」从不可能变成可能。

---

## 6. 证据文档

34 份，全部在 `evidence/bootstrap/`：

| 前缀 | 内容 |
|---|---|
| `baseline` / `toolchain-build` | 起点与工具链 |
| `bars-format` / `name-overlap` / `suffix-semantics` | BARS 与命名研究 |
| `ost-03` / `ost-04` | 资产匹配与审计 |
| `bwav-loop` / `ver-01` / `ver-02` | 完整性与循环坐标 |
| `ver-03-markers` | 标记解析（含更正声明） |
| `and-01` .. `and-22` | Android 各阶段 |
| **`and-23-real-device-listening`** | **S6 听音验证（首次）** |
| `and-24-audio-focus` | 音频焦点与生命周期收口（阶段 A） |
| `and-25-multi-segment-graph` | 多 Asset / 多 Segment 音乐图（阶段 B） |

---

## 7. 下一步（按价值排序）

1. **阶段 C：Coaster 五段流程** —— `Pre_Short → Pre → Bridge → Main → Outro`。写入层已就绪（`and-25`），缺的是 transition 的**实际触发**验证：现在 `Play(segment)` 能起段，但还没有走 transition 换段。
2. **过渡请求状态监控** —— 指令要求「预加载、流式素材和截止时间错误处理」，这部分完全没做。
3. **codec 2 攻克** —— 需要外部线索（文档或已知正确的解码器）。容器结构已解，包边界穷举 48 种 framing 组合无结果。
4. **多曲目听音覆盖** —— 至少覆盖 COURSE / BATTLE / SEQUENCE 各一首，以及无循环点的曲目。
5. **标记名绑定确认** —— 需要 L3 证据（游戏内实测）。

### 阶段 C 的前置阻塞

`Transition` 的 `sync` / `sourceCue` / `entry` 语义决定了「什么时候切」，而这些属于 **L3 游戏行为事实**。目前只有 L1（资源结构）和 L2（声音）证据，**没有 L3**。

因此阶段 C 只能用显式标注的 `PROVISIONAL` 测试配置（例如「按固定秒数切 Bridge」），并在界面与文档中注明**这不是原版行为还原**。

---

## 8. 交接说明

- `handoff/status.json` 记录所有门禁与任务状态
- 所有 `NOT_RUN` 项均在证据文档中显式登记，无一项被隐藏
- 未提交任何游戏素材（`.bwav`/`.bars` 等已在 `.gitignore`）
- 听音证据与自动化证据**分开记账**，便于区分来源
