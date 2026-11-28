# Generic FinalLapFF → Fast

Applies to ordinary courses and v1.8.0 SFC retro courses. Rainbow Road keeps its separate `BGM_Rainbow_FinalLapFanfare` timing, and Battle mode omits `FinalLapFF` completely.

> **MKW v1.8.0 In-Game Extracted Report Alignment (2026-11-28 — Supersedes 2026-11-14 Recording Estimate; Shipped in `Beta 0.1.6`)**
>
> Reverse-engineering `Default.game__SoundBgmSetting.bgyml` and `main.bin` (`0x005016d4..0x005019f0`) in Mario Kart World v1.8.0 replaced the earlier 3.935 s / zero-fade screen-recording approximation with the exact game-authored constants:
> - **Outgoing `Full` fadeout (`stopAllBgm`)**: **`0.10 s` (`4,800` frames)** linear fadeout (`postExit = true`).
> - **`FinalLapFanfareDelay`**: **`0.133 s` (`6,384` frames)** from trigger (C++ default `0.0 s`, overridden by `Default.game__SoundBgmSetting.bgyml` to `0.133 s`).
> - **`FastDelay`**: **`4.05 s` (`194,400` frames)** from trigger (C++ default `2.9 s`, `.bgyml` effective `4.05 s`), placing `_Fast` frame `0` at **`188,016` frames (`3.917 s`)** after `BGM_Seq_FinalLapFF` frame `0`; the remaining **`1.682 s`** physical tail of `FinalLapFF` (`268,759` frames total) naturally decays over `_Fast`.
> - **Production `TransitionTimeline`**: `(bridgeStart = 6384, destinationStart = 194400, destinationCommit = 194400, destinationSource = 0)` with outgoing linear fadeout `0.10 s` (`postExit = true`), default `SyncPoint.Immediate` (or beat-snapped `beat1.exit` / `fast.exit` / `NextBar` when Perfect Beat Snapping is enabled).
> - **Scope across all game modes**:
>   - **29 main courses** (including `DonkeyKong` Section 5 → `Fast` and both `Cinema`/`SnowMountain` Pattern1 & Pattern2) + **10 v1.8.0 SFC retro courses** (`M_BgmSFC.bars.zs` 5 themes → `_SFC_Fast`): all play `BGM_Seq_FinalLapFF` + dedicated pre-rendered `_Fast` / `_SFC_Fast` from sample `0` (average speed-up `1.1803×`, SFC `1.30–1.56×`; `PeachBeach` and `DKPass` have shorter intros baked directly into their `_Fast` assets).
>   - **Battle Mode (`BGM_Battle_00..07` → `_FL`)**: verified in v1.8.0 to **NOT play `BGM_Seq_FinalLapFF`** — transitions directly with a **`0.10 s`** fadeout into `_FL`.
>   - **Sole real-time pitch exception**: `SE_Item_Star_BGM_Stereo_00` (`SuperStar` item BGM) applies a real-time **`1.30×`** rate/pitch multiplier (`0x3FA66666` at `0x00513310`) on Final Lap.

## Historical Evidence (2026-11-14 Pre-Extraction Recording Approximation)

User supplied three gameplay screen recordings and `BGM_Seq_FinalLapFF.bwav` on 2026-11-14. The recordings contain loud game sound effects. Bandpass-filtered normalized correlation against independent original source windows isolates the music without using the sound effects as timing markers.

| Course | FF source-zero in recording | Fast source-zero in recording | Difference |
| --- | ---: | ---: | ---: |
| DinoBones | 1.704125 s | 5.639250 s | 3.935125 s |
| WestCity | 2.791875 s | 6.727000 s | 3.935125 s |
| DinoDinoJungle | 3.267125 s | 7.197250 s | 3.930125 s |

FF matches its native playback speed in all three recordings. Independent FF windows agree on its start. Fast matches source zero, regardless of each course's BPM or MINF phase. The FF source has 268759 stereo frames at 48000 Hz (5.5991458 s); its physical tail continues over Fast.

*Historical note*: The early `alpha13` build approximated this interval as `188,880` frames (`3.935 s`) with zero outgoing fadeout `(0, 188880, 188880, 0)` prior to binary extraction; notice how close the `3.930–3.935 s` recording delta was to the true `.bgyml` delta (`4.05 - 0.133 = 3.917 s`, within ~13–18 ms of frame-tick latency). All current builds use the exact v1.8.0 `.bgyml` parameters documented above. Analysis scripts and measurements are retained locally in `/workspace/mkw-planning/final-lap-evidence/`.

## Implementation (Updated to MKW v1.8.0)

`FinalLapFanfareCompiler` extends the normal production graph after alternate-version and Donkey-region compilation (and is reused by `ExtraInteractiveCompilers.compileTwoStateLoopPlan` for SFC courses while bypassed for Battle mode). Existing Full→Fast edges become Immediate (or Perfect Beat Snapped) transitions with a shared `BGM_Seq_FinalLapFF` bridge, `0.10 s` (`4,800`-frame) linear outgoing fadeout (`postExit = true`), and exact timeline `(6384, 194400, 194400, 0)`. Fast starts from source frame zero. The bridge segment's logical exit is `188,016` frames (`4.05 - 0.133 = 3.917 s`) while its clip keeps all `268,759` physical source frames so Native `postExit` retains the full `1.682 s` bridge tail over Fast.

Cinema and SnowMountain retain their independent Full→Fast paths for each version; same-speed version crossfades (`0.5 s` bidirectional for Cinema, `0.5 s` enter / `2.0 s` exit for SnowMountain) are unchanged. Donkey retains region 1→2→3→4→5; only region 5→Fast receives this FF. Rainbow does not pass through this compiler.

Android supplies the bundled source when opening a normal flow and preserves it in `flowBytes` for stop/restart. The shared FF is prepared before playback, including in streamed flows. Existing mkwpacks do not need reimporting. Offline legacy research graphs without the shared asset retain their prior speed edges.

## Source packaging

APK asset: `app/src/main/assets/audio/BGM_Seq_FinalLapFF.bwav` (307376 bytes).

SHA-256: `60790cdf84a2e04c9fffca7ccee8bafa5be2bfb6ceb6dfd4f98ecfc8bdd8ce94`.

The supplied binary is excluded from Git, like other audio assets. To reproduce the APK in another checkout, copy the user's supplied BWAV to that asset path. Production compilation validates the source digest and format.

## Verification scope

A new real-source graph test first failed because FF was absent. The follow-up test run was stopped when the user requested immediate build delivery and their own testing. A read-only independent code review found no Critical/Important issues. No new PCM or device playback acceptance is claimed for alpha13; the user will perform practical validation.
