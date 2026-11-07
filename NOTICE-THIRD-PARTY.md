# Third-Party Notices

This project redistributes the following third-party components, which keep
their own licenses. The full texts are shipped in `app/src/main/assets/licenses/`
and are therefore present inside the built APK.

| Component | Version / pin | License | Text file |
|---|---|---|---|
| clHCA (vgmstream) | pinned, `third_party/clhca` | ISC | `vgmstream-clhca.txt` |
| libopenmpt | 0.8.9 | BSD-3-Clause | `libopenmpt.txt`, `libopenmpt-mpt-bsd.txt`, `libopenmpt-mpt-boost.txt` |
| minimp3 | pinned, `third_party/libopenmpt/mo3-codecs/minimp3` | CC0 1.0 | `minimp3.txt` |
| dr_flac | pinned commit `dfe8377` | MIT-0 (selected) | `dr-flac.txt` |
| dr_mp3 | pinned commit `dfe8377` | MIT-0 (selected) | `dr-mp3.txt` |
| stb_vorbis | pinned commit `2c980bb` | MIT | `stb-vorbis.txt` |
| Opus | pinned, `third_party/opus/upstream` | BSD-3-Clause | `opus.txt` |
| Game_Music_Emu | 0.6.5 (archive SHA256 pinned) | LGPL-2.1-or-later | `game-music-emu-lgpl-2.1.txt` |
| emu2413 (OPN2 kernel) | via Game_Music_Emu | MIT | `emu2413-mit.txt` |
| Nuked OPN2 | via Game_Music_Emu | notice | `nuked-opn2-notice.txt` |
| Oboe | 1.10.0 (Maven) | Apache-2.0 | see Maven artifact |
| AndroidX / Jetpack Compose | BOM 2025.04.01 | Apache-2.0 | see Maven artifacts |
| Kotlin standard library | 2.1.20 | Apache-2.0 | see Maven artifact |

## Vendored source integrity

Vendored trees under `third_party/` are pinned by SHA256 and are verified at
CMake configure time. `.gitattributes` marks them `-text` so no checkout can
silently rewrite line endings and break a pinned digest.

Opus additionally ships `third_party/opus/SHA256SUMS.json` (345 entries), all of
which are hashed on every configure.

## LGPL compliance for Game_Music_Emu

Game_Music_Emu is built as a **shared** library (`gme_shared` → `libgme.so`) and
is not statically linked into the application's own code. This keeps it
replaceable, as LGPL-2.1 section 6 requires. No LGPL source has been modified
beyond what its own build files select.

## No Nintendo assets

This project ships no game audio, no `.bars`, `.bwav`, `.bfstm`, or `.bfstp`
files, and no extracted game data. `.gitignore` excludes all such extensions.
The application reads files the user supplies at runtime; it contains no key,
no decryption routine, and nothing that bypasses a technical protection measure.
