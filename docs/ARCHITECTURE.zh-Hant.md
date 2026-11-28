# 架構

[简体中文](ARCHITECTURE.md) | **繁體中文** | [English](ARCHITECTURE.en.md) | [日本語](ARCHITECTURE.ja.md)

這份文件解釋**為什麼這樣分層**，以及每一層各自拒絕了什麼。

程式碼看得出「是什麼」，看不出「為什麼不是另一種」。而這個專案裡大多數決定，都是在排除了一條看起來更自然、但會導致靜默錯誤的路之後才拍板定案的 —— 那些排除的理由必須寫下來，否則下一個人會「順手改回去」。

> **`Beta 0.1.6` · MKW v1.8.0 演進補充說明（2026-11-28）**：本文第 1～15 節保留早期 Alpha 階段確立的架構鐵律；自該基線以後，三處關鍵事實已透過 MKW v1.8.0 遊戲內實機逆向提取與原生實作完成補齊：
> 1. **Codec 2（NintendoOpus，原 §11）**：已完成原生 `MultiOpusSource` 解碼器，227 首點唱機與 22 條 6 聲道 5.1 過場（24/24 條 6ch 串流 LFE 皆為靜音，下混響度對齊 `beta01`）全部可播。
> 2. **音訊輸出（原 §1/§3）**：`AudioOutput` 預設採用 Android 標準深度緩衝立體聲 `AudioTrack`（`16,384` 影格 ≈ 341 ms，Oboe 保留為回退），並配合 `NativeOutput::rebind()` 熱切換與 512 頁 `SourcePageCache` 保障熄螢幕與藍牙 TWS 零卡頓。
> 3. **完整 MKW v1.8.0 曲庫與音樂圖資料（原 §12）**：已全量對齊 `BgmAll.Product.180.bars.zs` 650 條串流、344 條 `MINF v1.1` 樂理網格、202 條有向賽道連接、13 張 `SoundJukeBoxPlaylist`（1,102 槽位 / 227 曲）與 121 首賽道組曲；詳見[音樂銜接機制詳解](MUSIC-TRANSITIONS.md)。

---

## 1. 分層

```
┌──────────────────────────────────────────────────────────────┐
│ MainActivity.kt (311)            Compose 介面                │
│   只讀一個 StateFlow、發四個命令，不含任何業務規則              │
└───────────────────────────┬──────────────────────────────────┘
                            │ Ui / pause() resume() seekTo() stop()
┌───────────────────────────▼──────────────────────────────────┐
│ player/PlayerViewModel.kt (418)   選檔案、準備、輪詢、持久化    │
│ player/PlaybackSession.kt (198)   傳輸狀態機（程序層級）        │
│ player/PlaybackHolder.kt  (25)    程序層級單例持有者            │
│ player/PlaybackService.kt (225)   前景服務 + MediaSession      │
└───────────────────────────┬──────────────────────────────────┘
                            │ PlaybackEngine 介面
┌───────────────────────────▼──────────────────────────────────┐
│ audio/  策略與所有權                                          │
│   TrackOpener (121)        讀標頭 → 查編碼 → 建圖 → 探針 → 引擎 │
│   PlaybackController (148) 傳輸狀態機（可測）                  │
│   AudioEngine (102)        持有原生控制代碼                    │
│   AudioOutput (78)         持有 Oboe 串流                      │
│   PlaybackEngine (40)      引擎介面（便於測試替身）             │
│   BwavHeader (119)         BWAV 標頭解析                      │
│   Kind2Chunks (117)        codec 2 容器解析（不解碼）          │
│   SeekTarget (56)          比例 ⇄ 幀號                        │
│   EngineSnapshot (67)      65 長度快照封包解析                 │
│   TimingDiagnosis (111)    回呼時序判定                       │
│   AudioStatusText (77)     狀態碼 → 可讀文字                   │
├──────────────────────────────────────────────────────────────┤
│ graph/  GraphBuilder (233) · GraphProbe (81)                  │
│ library/ AudioPicker · LibraryStore · CatalogLoader ·          │
│          TrackEntry · OstIndexLoader · StableIds              │
└───────────────────────────┬──────────────────────────────────┘
                            │ JNI
┌───────────────────────────▼──────────────────────────────────┐
│ player2/platform/audio/NativeAudioLibrary.kt (92)  41 個 external fun │
└───────────────────────────┬──────────────────────────────────┘
                            │ JNI 符號
┌───────────────────────────▼──────────────────────────────────┐
│ app/src/main/cpp/platform/audio/native/  (13 檔案)            │
│   jni/NativeAudioBridge.cpp (265)   引擎與命令                 │
│   jni/AudioSourceBridge.cpp (203)   音訊源綁定與探測            │
│   jni/AudioOutputBridge.cpp (37)    Oboe 輸出                  │
│   oboe/NativeOutput.cpp (134)       音訊回呼 + 時序            │
│   oboe/CallbackTiming.hpp (68)      無鎖回呼記錄                │
│   oboe/AudioCallbackLifetime.hpp    回呼生命週期守衛            │
└───────────────────────────┬──────────────────────────────────┘
                            │ C++ 呼叫
┌───────────────────────────▼──────────────────────────────────┐
│ engine/native/  (213 檔案，PLAYER² 引擎)                       │
│   api · audio · clock · compiled · decoder · dsp ·            │
│   emulation · mixer · music · rtpc · runtime · scheduler ·     │
│   voice                                                        │
└──────────────────────────────────────────────────────────────┘
```

`engine/native/` 是上游 PLAYER² 的引擎，本專案只修了 BWAV 解碼器的兩處（見 §4）。
`app/src/main/cpp/` 是本專案自己的橋接層。

---

## 2. 引擎所有權為什麼在程序層級

**失敗的樣子**：把 `AudioEngine` 放在 `PlayerViewModel` 裡（最自然的寫法），離開畫面時
`onCleared` 觸發，引擎銷毀，音樂停止。作為音樂播放器這是不可接受的。

**約束**：前景服務不能擁有 lifecycle 作用域物件。服務可能被系統重建，Activity 可以反覆銷毀重建。

**結論**：引擎歸 `PlaybackHolder`（程序層級單例），服務與 UI 都從它讀取同一個 `PlaybackSession`。

```
PlaybackHolder.get()  ──> PlaybackSession（唯一）
                              ├── PlaybackService  提供前景／通知／MediaSession
                              └── PlayerViewModel  只讀狀態、發命令
```

**服務不持有任何音訊狀態**。在服務裡再實作一份傳輸狀態，是播放器出現「兩個時鐘、通知與聽感不一致」
的標準原因。

`PlayerViewModel.onCleared()` 現在**故意不停止播放** —— 只取消輪詢。這是唯一一處
「生命週期結束但不清理」的地方，註解已寫明原因。

---

## 3. 為什麼 `AudioOutput` 不能偽造

`AudioOutput` 具有私有建構子且封裝著原生控制代碼，測試裡**無法**做替身。

這不是疏忽，而是承認：

| 測試層 | 能覆蓋 | 覆蓋不了 |
|---|---|---|
| JVM | 工作階段自身的記帳、所有權、失敗清除 | 真實開流 |
| 裝置 | 真實 Oboe 串流、回呼次數、時序封包 | 人耳聽感 |

所以 `PlaybackSessionTest` 用 `openOutput = { _, _ -> null }`（即「無音訊裝置」狀態）來測工作階段邏輯，
真實開流交給 `AudioOutputDeviceTest`。

**我沒有為了湊測試覆蓋率而替 `AudioOutput` 加上一個可注入的假介面** —— 那樣測的是假介面，不是真實路徑。

---

## 4. 解碼器只改了兩處，但都是硬傷

`engine/native/decoder/BwavFormat.hpp` 被改過兩處，依據的是 MKW 的真實檔案，而不是文件：

### 4.1 末區塊截斷

MKW 會把**最後一個聲道的最後一塊**截斷到剛好夠用：

```
minimum = (blocks - 1) * 8 + 1 + ceil(lastSamples / 2)
```

原解析器要求完整 8 位元組區塊 → **5,694 個檔案被拒**。

### 4.2 預測器種子

原解析器要求 `u16(header+68) == u8(firstByte)`。實測 **654 個檔案**不滿足
（`BGM_Stage_Coaster.bwav`：標頭寫 39，首位元組 53）。

改為只要求 `<= 0x7f`。

**這一處沒有被任何參考解碼器驗證過**，理由只有「不這樣改就播不了」。風險最高，所以：

- 自動化：`dsp_verify.py`（獨立第二實作）逐樣本對照，5 首曲目 52,000 採樣 maxdiff = 0
- 人工：實機聽音確認**音高正確**（種子錯會導致 DSP-ADPCM 預測失調，人耳可辨）

兩路獨立證據同時成立，才敢說這個修復正確。

---

## 5. 狀態碼必須按名稱顯示，不得強轉

`AudioStatusText` 把 `EngineStatus` 0..11 和 `SourceStatus` 0..14 全部命名，`UNKNOWN(n)` **永不**
被強轉成 `Ok`。

**為什麼**：狀態碼在數值上高度重疊（0 = Ok、1 = InvalidRequest、7 = OutputActive…），
一個錯誤的強轉會把手裡的失敗顯示成成功。這個專案已經因為「只看狀態碼不看音訊」誤判過兩次。

---

## 6. 圖封套在呼叫原生層之前就要檢查

`GraphProbe` 在 `AudioEngine.loadGraph` 之前校驗 magic、版本、body 長度、CRC32。

**為什麼**：原生側拿到畸形封包是未定義行為，而不是「回傳錯誤碼」。把校驗放在 Kotlin 側意味著
壞封包根本進不了 JNI。

`GraphBuilder` 與 `GraphProbe` 都拒絕非法路徑、越界、非法取樣率。

---

## 7. Seek 在兩層的行為**故意不同**

| 層 | 越界處理 | 理由 |
|---|---|---|
| `SeekTarget`（UI 側） | **鉗位** | 拖曳手勢會給出 NaN、±∞、負數；鉗位讓進度條手感正常 |
| `PlaybackController` | **拒絕並報錯** | 到這裡還越界說明上游有 bug，必須暴露 |

測試斷言越界 seek 時 `engine.seekCalls == 0` —— **越界請求根本不進原生層**。

**如果兩層都鉗位，上游的錯誤會被永久掩蓋。**

---

## 8. `playing` 與 `loaded` 是兩個概念

引擎的 `Play` 走**命令佇列**，同一瞬間取快照可能仍是 `Stopped`。

- `playing`：引擎快照說在播（用於「能不能暫停」）
- `loaded`：`Play` 已被接受（用於「要不要保活前景服務」）
- `active = playing || paused || loaded`

**混用會出錯**：第一版用 `playing` 做服務守衛，結果是載入成功後服務立刻拒絕啟動
（`PlaybackService.start()` 回傳 false）。

---

## 9. 快照版本不符時必須回傳 null

`EngineSnapshot` 與 `TimingDiagnosis` 都檢查 wire version（快照 8、時序 1），不符回傳 `null`。

**為什麼**：偏移讀錯不會當機，只會讓「沒在播」顯示成「在播」、「不健康」顯示成「健康」。
這是本專案最昂貴的錯誤類型 —— 它靜默發生。

---

## 10. 時序資料只用於診斷，永不驅動排程

`CallbackTiming` 記錄每次回呼的 CPU / wall 時間（2048 槽無鎖環形緩衝），
`TimingDiagnosis` 把它判成 `HEALTHY` / `DEGRADED` / `FAILING` / `NO_DATA`。

**它不參與任何播放決策。** 標頭檔註解寫明「Never drives Transport or musical scheduling」。

**為什麼**：一旦時序能影響排程，量測就變成了干擾源，而且會讓「卡頓」和「音樂邏輯錯誤」互相掩蓋。

判定閾值（1% / 5% 掉幀，70% / 90% 預算）是**策略**，不是量測值，寫成了常數並有測試釘住。

---

## 11. codec 2 不猜

253 個檔案（227 JukeBox + 22 Cutscene）使用 codec 2。

**已經確定的**（253/253 檔案、594 聲道、零例外）：

```
u32 tag 0x80000001, size 24   ← 六個 u32 設定，全庫相同：256 48000 32 0 0 120
u32 tag 0x80000004, size N    ← 編碼音訊
```

**沒有確定的**：封包邊界。`tools/k2_framing.py` 窮舉了 size 前綴 1/2/3/4 位元組 × 大端/小端 ×
對齊 1/16/64 × 起始偏移 0..4095，**沒有一種能恰好消費完 payload**。

平均碼率恆定在 0.2177 位元組/採樣（83.6 kbps），跨 250 萬到 1550 萬採樣差異 < 0.08% ——
按目標碼率編碼的特徵（如 Opus），但沒有封包邊界就沒有東西可以交給解碼器。

**為什麼不猜**：即便寫出一個能出聲的實作，也沒有參考能證明它是對的。交接包規則寫明
`never guessing`。**「看起來能播」和「確實正確」是兩件事，而後者無法驗證時，前者沒有價值。**

介面顯示 `BWAV codec 2 (chunked, 83.6 kbps at 48000 Hz)` 而不是籠統的 unsupported ——
知道「是什麼」和知道「怎麼解」是兩個不同的成就。

---

## 12. 目錄帶著自己的免責聲明

`app/src/main/assets/catalog.json` 由 `tools/gen_catalog.py` 產生，覆蓋聲明**原樣保留**：

```json
"freezeStatus": "PROVISIONAL_INCOMPLETE",
"coverage": "INCOMPLETE",
"coverageReasons": ["only 4/499 archives read", "verification not performed: ..."]
```

介面把這幾行**顯示出來**。

**為什麼**：如果把 650 筆說成完整原聲帶，那是一個**無法撤回的假陳述** —— 使用者不會去數，
但少掉的 495 個歸檔裡的曲目永遠不會出現在列表裡，而使用者會以為「遊戲裡就這些」。

`Catalog.isPartial` 由 `freezeStatus != "FROZEN" || coverage != "COMPLETE"` 判定，有測試釘住。

---

## 13. 持久化：損壞的檔案隔離，不刪除

`LibraryStore` 寫 `filesDir/library.json`：

- 原子替換用 `Files.move(REPLACE_EXISTING)`，**不是** `File.renameTo`
  （`renameTo` 在 Windows 上不覆蓋已存在目標，第二次儲存會靜默失敗）
- 解析失敗或版本不符 → 改名 `.corrupt` **並保留**，回傳可顯示的原因
- 缺欄位的列預設 `playable = false`（資訊缺失時寧可不讓播）

**為什麼不刪**：刪掉會永久遺失使用者的曲庫，且不留痕跡。

---

## 14. 測試分層的理由

| 層 | 數量 | 為什麼在這一層 |
|---|---|---|
| Python | 143 | 全庫統計（20,991 個 bwav）、逐樣本對照、容器窮舉 |
| Kotlin JVM | 242 | 純邏輯：狀態機、算術、解析、持久化。**跑得快且能跑在 Windows 上**，因此抓到了 `renameTo` 的平台差異 |
| Android Instrumentation | 69 | 需要真實 Context / 真實原生函式庫 / 真實 Oboe / 真實資產 |
| 人工（S6） | 1 | 聽感。**不可能自動化** |

**聽音證據與自動化證據分開記帳**，因為前者不可重播、依賴報告者判斷。混在一起會讓以後的人
分不清哪些結論來自機器、哪些來自人。

---

## 15. 一處已知的架構代價

`PlayerViewModel.prepared` 是按 digest 存在**記憶體裡**的（`mutableMapOf<String, Prepared>`）。
程序被系統殺死後，持久化只恢復名稱與中繼資料，**音訊位元組需要重新選檔案**。

`select()` 遇到這種列會顯示 `Not prepared / Pick this file again to reopen it`。

**這是有意的取捨**：把 3 MB × N 的音訊位元組持久化會顯著膨脹儲存空間，而重新選檔案是可接受的成本。
如果以後要改，應當改在 `AudioPicker` 的快取策略上，而不是把位元組塞進 `library.json`。
