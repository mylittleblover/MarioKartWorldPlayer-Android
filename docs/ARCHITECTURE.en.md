# Architecture

[简体中文](ARCHITECTURE.md) | [繁體中文](ARCHITECTURE.zh-Hant.md) | **English** | [日本語](ARCHITECTURE.ja.md)

This document explains **why the layers are split this way**, and what each layer explicitly refuses to do.

Code shows "what it is", not "why it isn't the other way". Most decisions in this project were settled only after ruling out a path that looked more natural but would cause silent failures — those reasons for ruling them out must be written down, or the next person will "cleanly change it back".

> **`Beta 0.1.6` · MKW v1.8.0 Evolution Note (2026-11-28)**: Sections 1–15 preserve the original architectural invariants established during early Alpha. Three key subsystems have since been completed and aligned directly with MKW v1.8.0 in-game extracted reports:
> 1. **Codec 2 (NintendoOpus, §11)**: Implemented natively via `MultiOpusSource`; all 227 Jukebox tracks and 22 six-channel 5.1 Opus cutscenes (all 24 six-channel streams verified silent-LFE, downmixed at `beta01` loudness parity) are fully playable.
> 2. **Audio Output (§1/§3)**: `AudioOutput` now defaults to Android deep-buffer stereo `AudioTrack` (`16,384` frames ≈ 341 ms, Oboe kept as fallback), paired with lock-free `NativeOutput::rebind()` hot-switching and a 512-page `SourcePageCache` for glitch-free background/TWS playback.
> 3. **Complete MKW v1.8.0 Music Data (§12)**: Fully audited against `BgmAll.Product.180.bars.zs` (650 streams), 344 `MINF v1.1` beat grids, 202 directed connections, 13 `SoundJukeBoxPlaylist` tables (1,102 slots / 227 tracks), and 121 course medleys; see [MUSIC-TRANSITIONS.md](MUSIC-TRANSITIONS.md).

---

## 1. Layering

```
┌─────────────────────────────────────────────────────────────────────────┐
│ MainActivity.kt (311)            Compose UI                             │
│   Reads one StateFlow, sends four commands, zero business rules         │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ Ui / pause() resume() seekTo() stop()
┌────────────────────────────────────▼────────────────────────────────────┐
│ player/PlayerViewModel.kt (418)   Pick file, prepare, poll, persist     │
│ player/PlaybackSession.kt (198)   Transport state machine (process)     │
│ player/PlaybackHolder.kt  (25)    Process-level singleton holder        │
│ player/PlaybackService.kt (225)   Foreground service + MediaSession     │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ PlaybackEngine interface
┌────────────────────────────────────▼────────────────────────────────────┐
│ audio/  Policy and ownership                                            │
│   TrackOpener (121)        Read header → codec → graph → probe → engine │
│   PlaybackController (148) Transport state machine (testable)           │
│   AudioEngine (102)        Owns native handle                           │
│   AudioOutput (78)         Owns Oboe stream                             │
│   PlaybackEngine (40)      Engine interface (for test doubles)          │
│   BwavHeader (119)         BWAV header parser                           │
│   Kind2Chunks (117)        codec 2 container parser (no decoding)       │
│   SeekTarget (56)          Ratio ⇄ frame index                          │
│   EngineSnapshot (67)      65-long snapshot packet parser               │
│   TimingDiagnosis (111)    Callback timing verdict                      │
│   AudioStatusText (77)     Status code → human-readable text            │
├─────────────────────────────────────────────────────────────────────────┤
│ graph/  GraphBuilder (233) · GraphProbe (81)                            │
│ library/ AudioPicker · LibraryStore · CatalogLoader ·                   │
│          TrackEntry · OstIndexLoader · StableIds                        │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ JNI
┌────────────────────────────────────▼────────────────────────────────────┐
│ player2/platform/audio/NativeAudioLibrary.kt (92)   41 external funs    │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ JNI symbols
┌────────────────────────────────────▼────────────────────────────────────┐
│ app/src/main/cpp/platform/audio/native/  (13 files)                     │
│   jni/NativeAudioBridge.cpp (265)   Engine & commands                   │
│   jni/AudioSourceBridge.cpp (203)   Source binding & probing            │
│   jni/AudioOutputBridge.cpp (37)    Oboe output                         │
│   oboe/NativeOutput.cpp (134)       Audio callback + timing             │
│   oboe/CallbackTiming.hpp (68)      Lock-free callback recording        │
│   oboe/AudioCallbackLifetime.hpp    Callback lifetime guard             │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ C++ calls
┌────────────────────────────────────▼────────────────────────────────────┐
│ engine/native/  (213 files, PLAYER² engine)                             │
│   api · audio · clock · compiled · decoder · dsp ·                      │
│   emulation · mixer · music · rtpc · runtime · scheduler ·              │
│   voice                                                                 │
└─────────────────────────────────────────────────────────────────────────┘
```

`engine/native/` is the upstream PLAYER² engine; this project patched only two places in the BWAV decoder (see §4).
`app/src/main/cpp/` is this project's own bridge layer.

---

## 2. Why Engine Ownership Lives at Process Level

**Failure mode**: Putting `AudioEngine` inside `PlayerViewModel` (the most natural design) causes `onCleared` to fire when leaving the UI, destroying the engine and stopping the music. That is unacceptable for a music player.

**Constraint**: A foreground service cannot own lifecycle-scoped objects. The service may be recreated by the OS, and the Activity can be destroyed and recreated repeatedly.

**Conclusion**: The engine belongs to `PlaybackHolder` (a process-level singleton); both the service and the UI read the same `PlaybackSession` from it.

```
PlaybackHolder.get()  ──> PlaybackSession (unique)
                              ├── PlaybackService  Provides foreground / notification / MediaSession
                              └── PlayerViewModel  Reads state, sends commands only
```

**The service holds no audio state whatsoever.** Re-implementing a second copy of transport state in the service is the textbook reason players end up with "two clocks, where the notification and what you hear disagree".

`PlayerViewModel.onCleared()` now **intentionally does not stop playback** — it only cancels polling. This is the single place where "lifecycle ends without cleanup", and the comment states why.

---

## 3. Why `AudioOutput` Cannot Be Faked

`AudioOutput` has a private constructor and wraps a native handle; it **cannot** be substituted with a test double.

This is not an oversight — it is an acknowledgement:

| Test layer | Can cover | Cannot cover |
|---|---|---|
| JVM | Session accounting, ownership, failure cleanup | Opening a real stream |
| Device | Real Oboe stream, callback counts, timing packet | Human hearing |

So `PlaybackSessionTest` tests session logic with `openOutput = { _, _ -> null }` (the "no audio device" state), while opening a real stream is handled by `AudioOutputDeviceTest`.

**I did not add an injectable fake interface to `AudioOutput` just to pad test coverage** — that would test the fake interface, not the real path.

---

## 4. Only Two Decoder Changes, Both Hard Defects

`engine/native/decoder/BwavFormat.hpp` was modified in two places, targeting real MKW files rather than documentation:

### 4.1 Truncated Final Block

MKW truncates the **last block of the last channel** to just enough bytes:

```
minimum = (blocks - 1) * 8 + 1 + ceil(lastSamples / 2)
```

The original parser required a full 8-byte block → **5,694 files rejected**.

### 4.2 Predictor Seed

The original parser required `u16(header+68) == u8(firstByte)`. Measurements showed **654 files** violate this (`BGM_Stage_Coaster.bwav`: header says 39, first byte is 53).

Relaxed to require only `<= 0x7f`.

**This change has not been verified against any reference decoder**; the only reason is "it won't play without it". It carries the highest risk, so:

- Automated: `dsp_verify.py` (an independent second implementation) verified sample-by-sample across 5 tracks, 52,000 samples, maxdiff = 0
- Manual: Real-device listening confirmed **correct pitch** (a wrong seed causes DSP-ADPCM predictor detuning, which is audible)

Only with both independent lines of evidence holding together can this fix be considered right.

---

## 5. Status Codes Must Be Shown by Name, Never Coerced

`AudioStatusText` names every `EngineStatus` 0..11 and `SourceStatus` 0..14; `UNKNOWN(n)` is **never** coerced to `Ok`.

**Why**: Status codes overlap heavily in numeric value (0 = Ok, 1 = InvalidRequest, 7 = OutputActive…); a wrong coercion turns an actual failure into a reported success. This project already misdiagnosed twice because people "looked only at status codes and not the audio".

---

## 6. Graph Envelopes Must Be Checked Before Calling Native Code

`GraphProbe` validates magic, version, body length, and CRC32 before `AudioEngine.loadGraph`.

**Why**: Passing a malformed packet to native code is undefined behaviour, not "returning an error code". Putting validation on the Kotlin side means bad packets never cross JNI.

Both `GraphBuilder` and `GraphProbe` reject illegal paths, out-of-range values, and invalid sample rates.

---

## 7. Seek Behaviour Is **Intentionally Different** Across Two Layers

| Layer | Out-of-bounds handling | Rationale |
|---|---|---|
| `SeekTarget` (UI side) | **Clamp** | Drag gestures produce NaN, ±∞, and negative values; clamping keeps the scrubber responsive |
| `PlaybackController` | **Reject with error** | Reaching this layer out of bounds means an upstream bug that must be exposed |

Tests assert `engine.seekCalls == 0` on an out-of-bounds seek — **out-of-bounds requests never enter the native layer**.

**If both layers clamped, upstream bugs would be hidden forever.**

---

## 8. `playing` and `loaded` Are Two Distinct Concepts

The engine's `Play` goes through a **command queue**, so taking a snapshot at the same instant may still report `Stopped`.

- `playing`: Engine snapshot says it is playing (used for "can we pause?")
- `loaded`: `Play` has been accepted (used for "should the foreground service stay alive?")
- `active = playing || paused || loaded`

**Conflating them breaks**: v1 used `playing` as the service guard, which caused the service to immediately refuse to start right after a successful load (`PlaybackService.start()` returned false).

---

## 9. Snapshot Version Mismatches Must Return null

Both `EngineSnapshot` and `TimingDiagnosis` check the wire version (8 for snapshots, 1 for timing) and return `null` on mismatch.

**Why**: Reading at the wrong offset does not crash — it silently displays "not playing" as "playing", and "unhealthy" as "healthy". This is the most expensive class of bug in this project: it is silent.

---

## 10. Timing Data Is for Diagnosis Only — Never Drives Scheduling

`CallbackTiming` records CPU / wall time for each callback (a 2048-slot lock-free ring buffer), and `TimingDiagnosis` classifies it as `HEALTHY` / `DEGRADED` / `FAILING` / `NO_DATA`.

**It takes no part in any playback decision.** The header comment explicitly states: "Never drives Transport or musical scheduling".

**Why**: Once timing can influence scheduling, measurement becomes a source of interference, and "stuttering" and "music logic bugs" mask each other.

The classification thresholds (1% / 5% dropped frames, 70% / 90% budget) are **policy**, not measurements, written as constants and pinned by tests.

---

## 11. Do Not Guess codec 2

253 files (227 JukeBox + 22 Cutscene) use codec 2.

**What is established** (253/253 files, 594 channels, zero exceptions):

```
u32 tag 0x80000001, size 24   ← Six u32 config values, identical across corpus: 256 48000 32 0 0 120
u32 tag 0x80000004, size N    ← Encoded audio
```

**What is not established**: Packet boundaries. `tools/k2_framing.py` exhaustively tested size prefixes of 1/2/3/4 bytes × big/little endian × alignment 1/16/64 × start offset 0..4095; **none consumed the payload cleanly**.

Average bitrate stays constant at 0.2177 bytes/sample (83.6 kbps), varying < 0.08% across 2.5M to 15.5M samples — characteristic of target-bitrate coding (such as Opus), but without packet boundaries there is nothing to hand to a decoder.

**Why not guess**: Even if an implementation produced sound, there is no reference to prove it right. The handoff rules explicitly specify `never guessing`. **"Sounds like it plays" and "actually correct" are two different things, and when the latter cannot be verified, the former has no value.**

The UI shows `BWAV codec 2 (chunked, 83.6 kbps at 48000 Hz)` rather than a generic unsupported message — knowing *what* it is and knowing *how* to decode it are two distinct achievements.

---

## 12. The Catalogue Carries Its Own Disclaimer

`app/src/main/assets/catalog.json` is generated by `tools/gen_catalog.py`, with its coverage declaration **preserved verbatim**:

```json
"freezeStatus": "PROVISIONAL_INCOMPLETE",
"coverage": "INCOMPLETE",
"coverageReasons": ["only 4/499 archives read", "verification not performed: ..."]
```

The UI **displays these lines**.

**Why**: Calling 650 entries the complete soundtrack would be an **irrevocable false claim** — users won't count them, the tracks from the missing 495 archives will simply never appear in the list, and users will assume "that's everything in the game".

`Catalog.isPartial` is determined by `freezeStatus != "FROZEN" || coverage != "COMPLETE"` and is pinned by tests.

---

## 13. Persistence: Quarantine Corrupt Files, Never Delete

`LibraryStore` writes `filesDir/library.json`:

- Atomic replacement uses `Files.move(REPLACE_EXISTING)`, **not** `File.renameTo` (`renameTo` does not overwrite an existing target on Windows, so the second save silently fails)
- Parse failure or version mismatch → rename to `.corrupt` **and keep it**, returning a displayable reason
- Rows missing fields default to `playable = false` (when information is missing, refuse to play rather than guess)

**Why not delete**: Deleting permanently destroys the user's library without leaving a trace.

---

## 14. Why Tests Are Layered This Way

| Layer | Count | Why at this layer |
|---|---|---|
| Python | 143 | Corpus-wide statistics (20,991 bwav files), sample-by-sample checks, container exhaustive search |
| Kotlin JVM | 242 | Pure logic: state machines, arithmetic, parsing, persistence. **Fast and runs on Windows**, which is how the `renameTo` platform difference was caught |
| Android Instrumentation | 69 | Needs real Context / real native library / real Oboe / real assets |
| Manual (S6) | 1 | Listening quality. **Cannot be automated** |

**Listening evidence and automated evidence are tracked separately**, because the former is non-replayable and depends on the reporter's judgement. Mixing them would make it impossible for future maintainers to tell which conclusions came from machines and which from people.

---

## 15. One Known Architectural Trade-off

`PlayerViewModel.prepared` is kept **in memory** keyed by digest (`mutableMapOf<String, Prepared>`).
After the process is killed by the OS, persistence restores only names and metadata; **audio bytes require picking the file again**.

When `select()` encounters such a row, it displays `Not prepared / Pick this file again to reopen it`.

**This is an intentional trade-off**: persisting 3 MB × N of audio bytes would bloat storage significantly, whereas re-picking the file is an acceptable cost. If this is ever changed later, it should be changed in `AudioPicker`'s caching strategy rather than stuffing raw bytes into `library.json`.
