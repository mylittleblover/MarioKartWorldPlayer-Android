# 架构

这份文档解释**为什么这样分层**，以及每一层各自拒绝了什么。

代码能看出「是什么」，看不出「为什么不是另一种」。而这个项目里大多数决定，都是在排除了一条
看起来更自然、但会导致静默错误的路之后才落下来的 —— 那些排除的理由必须写下来，否则下一个人
会「顺手改回去」。

---

## 1. 分层

```
┌──────────────────────────────────────────────────────────────┐
│ MainActivity.kt (311)            Compose 界面                 │
│   只读一个 StateFlow、发四个命令，不含任何业务规则              │
└───────────────────────────┬──────────────────────────────────┘
                            │ Ui / pause() resume() seekTo() stop()
┌───────────────────────────▼──────────────────────────────────┐
│ player/PlayerViewModel.kt (418)   选文件、准备、轮询、持久化    │
│ player/PlaybackSession.kt (198)   传输状态机（进程级）          │
│ player/PlaybackHolder.kt  (25)    进程级单例持有者              │
│ player/PlaybackService.kt (225)   前台服务 + MediaSession      │
└───────────────────────────┬──────────────────────────────────┘
                            │ PlaybackEngine 接口
┌───────────────────────────▼──────────────────────────────────┐
│ audio/  策略与所有权                                          │
│   TrackOpener (121)        读头 → 查编码 → 造图 → 探针 → 引擎   │
│   PlaybackController (148) 传输状态机（可测）                  │
│   AudioEngine (102)        持有原生句柄                       │
│   AudioOutput (78)         持有 Oboe 流                       │
│   PlaybackEngine (40)      引擎接口（便于测试替身）             │
│   BwavHeader (119)         BWAV 头部解析                      │
│   Kind2Chunks (117)        codec 2 容器解析（不解码）           │
│   SeekTarget (56)          比例 ⇄ 帧号                        │
│   EngineSnapshot (67)      65 长快照包解析                    │
│   TimingDiagnosis (111)    回调时序判定                       │
│   AudioStatusText (77)     状态码 → 可读文本                   │
├──────────────────────────────────────────────────────────────┤
│ graph/  GraphBuilder (233) · GraphProbe (81)                  │
│ library/ AudioPicker · LibraryStore · CatalogLoader ·          │
│          TrackEntry · OstIndexLoader · StableIds              │
└───────────────────────────┬──────────────────────────────────┘
                            │ JNI
┌───────────────────────────▼──────────────────────────────────┐
│ player2/platform/audio/NativeAudioLibrary.kt (92)  41 个 external fun │
└───────────────────────────┬──────────────────────────────────┘
                            │ JNI 符号
┌───────────────────────────▼──────────────────────────────────┐
│ app/src/main/cpp/platform/audio/native/  (13 文件)            │
│   jni/NativeAudioBridge.cpp (265)   引擎与命令                 │
│   jni/AudioSourceBridge.cpp (203)   源绑定与探测                │
│   jni/AudioOutputBridge.cpp (37)    Oboe 输出                  │
│   oboe/NativeOutput.cpp (134)       音频回调 + 时序            │
│   oboe/CallbackTiming.hpp (68)      无锁回调记录                │
│   oboe/AudioCallbackLifetime.hpp    回调生命周期守卫            │
└───────────────────────────┬──────────────────────────────────┘
                            │ C++ 调用
┌───────────────────────────▼──────────────────────────────────┐
│ engine/native/  (213 文件，PLAYER² 引擎)                       │
│   api · audio · clock · compiled · decoder · dsp ·            │
│   emulation · mixer · music · rtpc · runtime · scheduler ·     │
│   voice                                                        │
└──────────────────────────────────────────────────────────────┘
```

`engine/native/` 是上游 PLAYER² 的引擎，本项目只修了 BWAV 解码器的两处（见 §4）。
`app/src/main/cpp/` 是本项目自己的桥接层。

---

## 2. 引擎所有权为什么在进程级

**失败的样子**：把 `AudioEngine` 放在 `PlayerViewModel` 里（最自然的写法），退出界面时
`onCleared` 触发，引擎销毁，音乐停止。作为音乐播放器这是不可接受的。

**约束**：前台服务不能拥有 lifecycle 作用域对象。服务可能被系统重建，Activity 可以反复销毁重建。

**结论**：引擎归 `PlaybackHolder`（进程级单例），服务与 UI 都从它读同一个 `PlaybackSession`。

```
PlaybackHolder.get()  ──> PlaybackSession（唯一）
                              ├── PlaybackService  提供前台/通知/MediaSession
                              └── PlayerViewModel  只读状态、发命令
```

**服务不持有任何音频状态**。在服务里再实现一份传输，是播放器出现「两个时钟、通知与听感不一致」
的标准原因。

`PlayerViewModel.onCleared()` 现在**故意不停止播放** —— 只取消轮询。这是唯一一处
「生命周期结束但不清理」的地方，注释写明了原因。

---

## 3. 为什么 `AudioOutput` 不能伪造

`AudioOutput` 有私有构造且包着原生句柄，测试里**无法**做替身。

这不是疏忽，是承认：

| 测试层 | 能覆盖 | 覆盖不了 |
|---|---|---|
| JVM | 会话自身的记账、所有权、失败清除 | 真实开流 |
| 设备 | 真实 Oboe 流、回调次数、时序包 | 人耳听感 |

所以 `PlaybackSessionTest` 用 `openOutput = { _, _ -> null }`（即「无音频设备」状态）来测会话逻辑，
真实开流交给 `AudioOutputDeviceTest`。

**我没有为了凑测试覆盖率给 `AudioOutput` 加一个能注入的假接口** —— 那样测的是假接口，不是真实路径。

---

## 4. 解码器只改了两处，但都是硬伤

`engine/native/decoder/BwavFormat.hpp` 被改过两处，对象是 MKW 的真实文件，不是文档：

### 4.1 末块截断

MKW 会把**最后一个声道的最后一块**截断到刚好够用：

```
minimum = (blocks - 1) * 8 + 1 + ceil(lastSamples / 2)
```

原解析器要求完整 8 字节块 → **5,694 个文件被拒**。

### 4.2 预测器种子

原解析器要求 `u16(header+68) == u8(firstByte)`。实测 **654 个文件**不满足
（`BGM_Stage_Coaster.bwav`：头写 39，首字节 53）。

改为只要求 `<= 0x7f`。

**这一处没有被任何参考解码器验证过**，理由只有「不这样改就播不了」。风险最高，所以：

- 自动化：`dsp_verify.py`（独立第二实现）逐样本对照，5 首曲目 52,000 采样 maxdiff = 0
- 人工：真机听音确认**音高正确**（种子错会导致 DSP-ADPCM 预测失谐，人耳可辨）

两路独立证据同时成立，才敢说这个修复对。

---

## 5. 状态码必须按名字显示，不得强转

`AudioStatusText` 把 `EngineStatus` 0..11 和 `SourceStatus` 0..14 全部命名，`UNKNOWN(n)` **永不**
被强转成 `Ok`。

**为什么**：状态码在数值上高度重合（0 = Ok、1 = InvalidRequest、7 = OutputActive…），
一个错误的强转会把手里的失败显示成成功。这个项目已经因为「只看状态码不看音频」误判过两次。

---

## 6. 图信封在调用原生之前就要查

`GraphProbe` 在 `AudioEngine.loadGraph` 之前校验 magic、版本、body 长度、CRC32。

**为什么**：原生侧拿到畸形包是未定义行为，不是「返回错误码」。把校验放在 Kotlin 侧意味着
坏包根本进不了 JNI。

`GraphBuilder` 与 `GraphProbe` 都拒绝非法路径、越界、非法采样率。

---

## 7. Seek 在两层的行为**故意不同**

| 层 | 越界处理 | 理由 |
|---|---|---|
| `SeekTarget`（UI 侧） | **钳位** | 拖动手势会给出 NaN、±∞、负数；钳位让进度条手感正常 |
| `PlaybackController` | **拒绝并报错** | 到这里还越界说明上游有 bug，必须暴露 |

测试断言越界 seek 时 `engine.seekCalls == 0` —— **越界请求根本不进原生层**。

**如果两层都钳位，上游的错误会被永久掩盖。**

---

## 8. `playing` 与 `loaded` 是两个概念

引擎的 `Play` 走**命令队列**，同一瞬间取快照可能仍是 `Stopped`。

- `playing`：引擎快照说在播（用于「能不能暂停」）
- `loaded`：`Play` 已被接受（用于「要不要保活前台服务」）
- `active = playing || paused || loaded`

**混用会出错**：第一版用 `playing` 做服务守卫，结果是加载成功后服务立刻拒绝启动
（`PlaybackService.start()` 返回 false）。

---

## 9. 快照版本不符时必须返回 null

`EngineSnapshot` 与 `TimingDiagnosis` 都检查 wire version（快照 8、时序 1），不符返回 `null`。

**为什么**：偏移读错不会崩，只会让「没在播」显示成「在播」、「不健康」显示成「健康」。
这是本项目最贵的错误类型 —— 它静默。

---

## 10. 时序数据只用于诊断，永不驱动调度

`CallbackTiming` 记录每次回调的 CPU / wall 时间（2048 槽无锁环形缓冲），
`TimingDiagnosis` 把它判成 `HEALTHY` / `DEGRADED` / `FAILING` / `NO_DATA`。

**它不参与任何播放决策。** 头文件注释写明「Never drives Transport or musical scheduling」。

**为什么**：一旦时序能影响调度，测量就变成了干扰源，而且会让「卡顿」和「音乐逻辑错误」互相掩盖。

判定阈值（1% / 5% 丢帧，70% / 90% 预算）是**策略**，不是测量值，写成了常量并有测试钉住。

---

## 11. codec 2 不猜

253 个文件（227 JukeBox + 22 Cutscene）用 codec 2。

**已经确定的**（253/253 文件、594 声道、零例外）：

```
u32 tag 0x80000001, size 24   ← 六个 u32 配置，全库相同：256 48000 32 0 0 120
u32 tag 0x80000004, size N    ← 编码音频
```

**没有确定的**：包边界。`tools/k2_framing.py` 穷举了 size 前缀 1/2/3/4 字节 × 大端/小端 ×
对齐 1/16/64 × 起始偏移 0..4095，**没有一种能恰好消费完 payload**。

平均码率恒定在 0.2177 字节/采样（83.6 kbps），跨 250 万到 1550 万采样差异 < 0.08% ——
按目标码率编码的特征（如 Opus），但没有包边界就没有东西可以交给解码器。

**为什么不猜**：即便写出一个能出声的实现，也没有参考能证明它是对的。交接包规则写明
`never guessing`。**「看起来能播」和「确实正确」是两件事，而后者无法验证时，前者没有价值。**

界面显示 `BWAV codec 2 (chunked, 83.6 kbps at 48000 Hz)` 而不是笼统的 unsupported ——
知道「是什么」和知道「怎么解」是两个不同的成就。

---

## 12. 目录带着自己的免责声明

`app/src/main/assets/catalog.json` 由 `tools/gen_catalog.py` 生成，覆盖声明**原样保留**：

```json
"freezeStatus": "PROVISIONAL_INCOMPLETE",
"coverage": "INCOMPLETE",
"coverageReasons": ["only 4/499 archives read", "verification not performed: ..."]
```

界面把这几行**显示出来**。

**为什么**：如果把 650 条说成完整原声带，那是一个**无法撤回的假陈述** —— 用户不会去数，
但少掉的 495 个归档里的曲目永远不会出现在列表里，而用户会以为「游戏里就这些」。

`Catalog.isPartial` 由 `freezeStatus != "FROZEN" || coverage != "COMPLETE"` 判定，有测试钉住。

---

## 13. 持久化：损坏的文件隔离，不删除

`LibraryStore` 写 `filesDir/library.json`：

- 原子替换用 `Files.move(REPLACE_EXISTING)`，**不是** `File.renameTo`
  （`renameTo` 在 Windows 上不覆盖已存在目标，第二次保存会静默失败）
- 解析失败或版本不符 → 改名 `.corrupt` **并保留**，返回可显示的原因
- 缺字段的行默认 `playable = false`（信息缺失时宁可不让播）

**为什么不删**：删掉会永久丢失用户的曲库，且不留痕迹。

---

## 14. 测试分层的理由

| 层 | 数量 | 为什么在这一层 |
|---|---|---|
| Python | 143 | 全库统计（20,991 个 bwav）、逐样本对照、容器穷举 |
| Kotlin JVM | 242 | 纯逻辑：状态机、算术、解析、持久化。**跑得快且能跑在 Windows 上**，因此抓到了 `renameTo` 的平台差异 |
| Android Instrumentation | 69 | 需要真实 Context / 真实原生库 / 真实 Oboe / 真实资产 |
| 人工（S6） | 1 | 听感。**不可能自动化** |

**听音证据与自动化证据分开记账**，因为前者不可重放、依赖报告者判断。混在一起会让以后的人
分不清哪些结论来自机器、哪些来自人。

---

## 15. 一处已知的架构代价

`PlayerViewModel.prepared` 是按 digest 存在**内存里**的（`mutableMapOf<String, Prepared>`）。
进程被系统杀死后，持久化只恢复名称与元数据，**音频字节需要重新选文件**。

`select()` 遇到这种行会显示 `Not prepared / Pick this file again to reopen it`。

**这是有意的取舍**：把 3 MB × N 的音频字节持久化会显著膨胀存储，而重新选文件是可接受的成本。
如果以后要改，应当改在 `AudioPicker` 的缓存策略上，而不是把字节塞进 `library.json`。
