# Changelog

[简体中文](CHANGELOG.md) | [繁體中文](CHANGELOG.zh-Hant.md) | **English** | [日本語](CHANGELOG.ja.md)

The entries below summarize releases and development milestones verifiable from the Git history.

## Beta 0.1.6 · 2026-11-28

> Adopts the new `Beta X.Y.Z` versioning format and consolidates all updates since `v0.1.0-beta05`.

- **MKW v1.8.0 Audio Transitions & Medley Alignment**: Calibrated `Pre_TransitionGrid` (`3`/`4`/`8`/`16`-beat) exits across all courses, final-lap fanfare timing (`0.10 s` fadeout + `0.133 s` delay + `4.05 s` fast entry), Boo Cinema (`0.5 s` crossfade) and Starview Peak (`0.5 s` / `2.0 s` asymmetric crossfade, `Pattern2` restricted to `Outro00..05`), DK Spaceport 5-region beat grids, Koopa Troopa Beach Suite B (matches `A Alt` after `Pre_Short` without Speaker overlay), and Rainbow Road split-stem `Area1 -> Area2` `0.30 s` fadeout and `Area3 -> Final_Pre` timing (removed the Rainbow Road melody switch), with all 4-locale UI hints and built-in medleys aligned.
- **Native Audio & Seek Reliability Fixes**: Fixed silent playback after scrubbing long tracks (such as Staff Credits) or seeking after Survival Rally track switches (`RuntimeSeek`, `SourcePageCache`, `BwavSource`/`OpusSource`, `PlaybackController`, `AudioOutput` lifecycle recovery); restored 5.1 surround loudness to `beta01` unity parity without clipping.
- **Staff Credits Dynamic Progress Bar**: Implemented 18-segment course-themed progress bar styling using real in-game course icon colors with left-to-right slide-in wipes, two-segment split display for dual-course overlaps (Acorn Heights + Dandelion Depths, Whistlestop Summit + Faraway Oasis), continuously flowing Rainbow Road cover-art gradient, and automatic 10 ms RMS-envelope offset calibration for Nintendo Music Special (`nm_special_119`).
- **Free Roam Engine, In-Game Library Mode & World Map**: Fixed 202 directed edges and 10 one-way route badges; added the 13-playlist Free Roam Jukebox engine, Pure Free Roam routes, and a dedicated "In-Game Free Roam Library" playback option across both Free Roam routes and Radio.
- **Markers, Flow Editor, Quiz, Themes & UI Polish**: Added track markers and "Play from marker", Flow Editor presets and offline WAV/FLAC export, 7 character accent themes in a bottom-sheet drawer, Special Cup & Staff Credits unlock flows with a music recognition quiz, an in-app GitHub update checker supporting both `Beta X.Y.Z` and legacy tags, unified `AppMotion`/`MkwKit` polish, and complete 4-language localization.
- **Repository Reports & Documentation Sync**: Audited and synchronized `docs/MUSIC-TRANSITIONS.md`, `docs/research/*`, `docs/ARCHITECTURE*.md`, and `docs/USAGE*.md` strictly against the *Mario Kart World* v1.8.0 in-game extracted reports (650 `BgmAll` streams, 24 6ch silent-LFE audit, 344 MINF grids, `Default.game__SoundBgmSetting.bgyml` constants, 202 directed connections, and 13 official Jukebox playlists).

## 0.1.0-beta05 · 2026-11-23

- Fixed audible seams at loop wraps and track transitions during background and screen-off playback: doubled the streaming page cache to 512 pages with lock-free `futex` worker wakeup, added multi-page lookahead prefetching for loop starts and transition destinations, enabled 2-second `lookAheadFrames` across interactive/medley/route compilers, added single-repeat medley preloading, and made background fast-polling scale with playback speed and A–B loop endpoints.
- Fixed choppy playback and single-ear dropouts on Bluetooth TWS earbuds (notably on MIUI / Qualcomm devices): switched primary audio output to standard Android deep-buffer stereo `AudioTrack` (`PCM16`, `PERFORMANCE_MODE_POWER_SAVING`, `16384`-frame buffer, `2048`-frame chunks, keeping Oboe as fallback) with prefill-before-play, full partial-write handling, synchronized track recovery, and real `AudioTrack.getUnderrunCount()` reporting in Timing Diagnosis.
- Eliminated the 1-second Bluetooth AVRCP and Notification/Widget/Tile Binder refresh storm during steady playback (now event-driven with drift resync and accurate playback speed reporting), fixed `AudioFocusManager` state tracking and `PlaybackSession` native lifecycle synchronization, cached active source slots to avoid 128-slot JNI polling churn, tuned decode-worker priority/wake cadence, and added 1:1 sample-rate and `0 dB` fast paths.
- Added a detailed technical guide to *Mario Kart World*'s music transition and stitching architecture (`docs/MUSIC-TRANSITIONS.md`), covering BWAV loop measurements, MINF beat/phase alignment, `TransitionTimeline` scheduling, special course flows, and 6-stop Survival Rally chaining.

## 0.1.0-beta04 · 2026-11-23

- Redesigned the mobile portrait custom-route editor in World Map: introduced a compact bottom dock (`DraftRouteCompactDock`) paired with an expandable legs-and-rules sheet (`DraftRouteSettingsSheet`), maximizing map visibility and removing internal identifier labels.
- Improved 6-stop Survival Rally (Knockout Tour) and linear `Outro -> Next Pre` handover: raised `MAX_CHAIN_SEGMENTS` to 8, added numbered `Outro` event-marker and Lava `Outro01 -> Outro00` fallbacks, prioritized `KNOCKOUT_DIRECT` edge resolution, and preserved automatic segment chains across `seek` and repeat-mode changes.
- Improved background track-to-track transition smoothness: added `NativeOutput::rebind` (`rebindOutput`) hot-swap stream binding to avoid closing/reopening the Oboe stream, increased Oboe buffer capacity to `8192` frames (`max(burst * 8, 4096)`), offloaded old `AudioEngine` release to `Dispatchers.IO`, and added file-backed / `trustExpectedDigest` zero-rehash compilation.

## 0.1.0-beta03 · 2026-11-22

- Migrated the repository URL to `mylittleblover/JukeKart-Android` and updated `README.md`, `docs/USAGE.md`, and localized Settings feedback links accordingly.
- Standardized adaptive launcher icon layer directories: moved `ic_launcher_background.png` and `ic_launcher_foreground.png` from `mipmap-*` to `drawable-*`, updated `mipmap-anydpi-v26` and `raw/keep.xml` references, and added explicit `android:icon="@mipmap/ic_launcher"` on `PlaybackService`, `PackImportService`, and `PlayerWidgetProvider` to improve Xiaomi HyperOS / MIUI `CustomizeIcon` compatibility.

## 0.1.0-beta02 · 2026-11-22

- Completed Simplified and Traditional Chinese localization for 114 Free Roam / Jukebox tracks (aligned with Super Mario Wiki and catalog naming conventions).
- Home and cup/course/mode/media category icons now prefer runtime artwork inside imported `.mkwpack` archives, falling back to built-in vectors; the world map supports full-resolution base imagery (`inSampleSize = 1`) and route highlight overlays.
- Extended "Recently Played" to cover Jukebox (`jb:`), SFC (`sfc:`), Battle (`battle:`), and Extra (`extra:`) tracks, refreshing across background track transitions and foreground resume.
- Refined the World Map layout and controls: streamlined top and bottom floating bars to preserve map viewport space, and added a Grand Prix / Knockout Tour segment route picker plus a pinned-header stops and road music panel.
- Added a first-launch welcome onboarding flow (highlighting core features, notification permission, and `.mkwpack` pack import), automatically skipped for users who already have an imported library.
- Fixed Special Cup / Rainbow Road unlock detection after listening for 30 seconds on the 29 unlockable courses, including retroactive progress grants, background track-advance accumulation, and high-priority heads-up unlock notifications.
- Fixed background linear playback transition stutter: hold `PARTIAL_WAKE_LOCK` during playback, use adaptive near-transition polling, reuse the Oboe output stream, and add background single-track preloading with known SHA-256 skip-rehash optimization.
- Fixed the `Pre_Short` loop count in Faraway Oasis (`DKJungleParkway`) Suite B (completed to 2 loops to match the other course Suite B arrangements).
- Disabled resource path obfuscation in Release builds and preserved app/notification icon resources via `keep.xml`, adding adaptive `<monochrome>` layers and notification `setLargeIcon(...)` so OEM ROMs do not fall back to the default Android robot icon.

## 0.1.0-beta01 · 2026-11-22

- Renamed the app to JukeKart and updated UI strings, localizations, and version metadata.
- Expanded course medleys, added playback modes for SFC, Battle, Main Menu, and route medleys, and completed Koopa Troopa Beach Suite B.
- Added a full-screen landscape unlock ceremony for Special Cup; packs may supply video, with a built-in fallback animation when absent.
- Removed bundled game audio, video, and extracted game textures from the app distribution, adding original course badges, covers, and map artwork. Launcher and notification icons retain their existing designs.
- Updated Android OS version compatibility handling for notifications, Quick Settings tiles, and background services. Enabled R8, resource shrinking, separate release signing, and 16 KB ELF alignment for native libraries.
- Added third-party license notices and feedback links inside Settings.
- Adopted Apache-2.0 for original source code and built-in original vector artwork (third-party components retain their own licenses; see [NOTICE-THIRD-PARTY.md](NOTICE-THIRD-PARTY.md)).

## 0.1.0-alpha25 · 2026-11-21

- Added scheduled sleep/switch timer, equalizer and audio effects, speed and pitch controls, and A–B looping.
- Added data backup and restore, playlist export, sorting, and smart playlists.
- Added home-screen widget and Quick Settings tile.

## alpha19 → alpha25 Milestone · 2026-11-16 to 2026-11-21

- Added the course Flow Editor, native playlist handover, and linear playback resume.
- Added road-segment music playback along official and custom routes.
- Switched pack import to streaming via the system file picker, supporting multi-pack merging, updates, and invalidation handling.
- Improved fade-out stop, interactive controls, the Media tab, and Free Roam track ordering.

## 0.1.0-alpha16 to alpha19 · 2026-11-14 to 2026-11-15

- Added Recently Played, natural-end behavior, and alternate starting stages, refining Home discovery and interactive controls.
- Preserved scroll positions across screens and refined navigation and player control transitions.
- Expanded course medleys, extra tracks, and the Jukebox catalog; added on-demand cover loading and fixed native seeking.

## alpha09 → alpha16 Milestone · 2026-11-13 to 2026-11-14

- Fixed a crash caused by scrollable layouts on the Interactive screen; added an adaptive official route map, camera position memory, and stage previews.
- Added in-app language switching, multilingual search, favorites, daily picks, Home discovery, and persistent course playlists.
- Added stage name localizations and improved interactive playback and the map.

## 0.1.0-alpha06 to alpha08 · 2026-11-12

- Integrated MINF playback, automatic stage chaining, and source-sample-aligned transitions.
- Supported interactive packs covering 30 courses and added variant controls for Boo Cinema and Starview Peak.
- Verified region controls and sample-aligned variant switching on Android.

## 0.1.0-alpha02 to alpha05 · 2026-11-08

- Added full-course stage orchestration, stage switching, and the interactive console.
- Defined and implemented the `.mkwpack` archive protocol with checksum verification, safe import, and duplicate asset deduplication.
- Added Settings, Map, navigation, and the Special Cup unlock entry.

## 0.1.0-alpha01 · 2026-11-08

- First playable internal build supporting single-BWAV import, play, pause, stop, and seek.
- Added persistent library storage, background playback, and notification media controls.
- Added the initial track catalog and device audio callback diagnostics, verified with course listening tests on physical Android hardware.
