# Game_Music_Emu 0.6.5 — PLAYER² modified source dependency

Original source: https://github.com/libgme/game-music-emu/commit/9e23d10f9fd2a6a2f33b10912dd8dc7153258995

Original, unmodified archive SHA-256: `5e8eb240b03ee76b17aafb2ba52ce838399664fb65a6a7739335a2f581a84e0e`.
The archive includes upstream source and all original license/copyright files.
`prepare.cmake` applies visible local changes to extracted build sources only.
This is **0.6.5 with PLAYER² modifications**, not an unmodified upstream build.

The selected NSF/NSFe/SPC kernels are LGPL-2.1-or-later. Build only the shared
library and preserve its stable C interface. GPL-only YM2612/MAME, other format
players, zlib autoload, demos and device outputs are disabled. The LGPL text is
also packaged at `assets/licenses/game-music-emu-lgpl-2.1.txt`. Source archive,
local patches and reproducible build scripts are in this repository. Users may
modify/rebuild/replace `libgme.so` through normal APK extraction/repackaging and
signing; no library signature restriction or anti-modification mechanism is
introduced. Preserve notices and LGPL source/replacement rights in distribution.

Before registering a provider, Engine must bound and validate its own accepted
format subset; loading arbitrary files directly through libgme is not admission.
All emulation calls stay on workers/control threads. This dependency creates no
PLAYER² Transport, Mixer, output owner or persistent cache. No format is marked
supported merely because the library can identify it.

Local fixes found during actual ASan/UBSan evaluation:

- Size the NSF voice-type table for the complete configured voice set. Previously
  simultaneous expansion-chip flags indexed beyond the 16-entry table. Existing
  first 16 types remain unchanged; new slots use the library's neutral type.
- Initialize NSF header/region state before early NSFe load failures can reach
  tempo setup; initialize NSFe header/actual track count as well.
- Enforce NSFe INFO → DATA → empty NEND ordering with real errors rather than
  diagnostic-only checks. Reject unknown required chunks by their first byte.
  A mutated DATA tag could otherwise be skipped and an empty ROM accepted.

Evaluation before integration: host and NDK27 arm64 release shared builds pass;
original 2-song NSF/NSFe and looping-BRR SPC yield nonzero PCM, identical across
1/17/256-frame partitions and independent instances. NSF/NSFe songs differ.
After these fixes, the sanitizer driver passes 820 bounded truncation/mutation
cases (532 refusals, 288 accepted) plus valid PCM/partition checks. Initial
failures are retained in external logs. This is bounded dependency evaluation,
not a fuzz-security proof, registered Provider or device playback acceptance.

NSFe admission continuation: Engine bounds canonical pre-DATA BANK mappings and reviewed VRC6/VRC7/FDS/MMC5/Namco163/Sunsoft5B flags, including combinations. VRC7 uses the already pinned MIT emu2413 component; its original notice is packaged at `assets/licenses/emu2413-mit.txt`. No emulator algorithm/new library or output/Runtime is added. Unknown flags and unsupported timing/playlists remain refused; Source-level validation must pass before new subsets are claimed.

VGM FM extension (2026-10-16): VGM is now compiled only with the LGPL
`GME_YM2612_EMU=Nuked` backend, never MAME/GENS. `Ym2413_Emu.cpp` adapts the
already bundled MIT emu2413 YM2413 ROM0 (the upstream YM2413 wrapper was a disabled
stub). `prepare-fm.cmake` publishes shared OPLL lookup tables with C11 acquire /
release initialization across VRC7 and YM2413 constructors, and makes Nuked's
chip model initialized per-instance rather than process-global. Bare YM2612
clock declarations select YM2612 silicon; YM3438/dual variants are not admitted.
The original archive remains unchanged. The adapter, context-checked patches,
license notices and all build switches are distributed here; this continues to
be a modified, replaceable LGPL shared dependency. FM Source capability tests
and acceptance are recorded separately in the Core handoff; enabling the build
flag alone never admits another chip/command family.
