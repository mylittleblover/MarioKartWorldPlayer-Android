# Pinned ordinary-media decoder

dr_flac 0.13.4 is pinned at dr_libs commit
`dfe8377631000664666519fdb83da193fd8037f4`; original header SHA-256 is
`111144e778f55738db6851cb226015c419e00d04b916a09506d4856d9cff945c`.
CMake verifies the unmodified source digest before compiling. Selected license is
the upstream **MIT-0** alternative; its text remains in the original header and
is packaged as an APK asset. No license/legal choice is deferred to application
users. This dependency only decodes FLAC and owns no PLAYER² playback semantics.

The pure decoder builds for host and Android through the unique Engine target.
File/stdout/platform entry points and Ogg-FLAC are disabled; native FLAC I/O and
allocation use Engine callbacks. CRC validation stays enabled. Third-party
headers are SYSTEM includes, while the wrapper and Engine retain
`-Wall -Wextra -Wpedantic -Werror`. Engine must additionally bound metadata,
duration, source reads, heap budget and malformed-input admission; upstream
tolerant decoding alone is insufficient.

This dependency is not format acceptance. Android ABI compilation, binary size,
decoder allocation behavior, deterministic rendering, seek/malformed source,
original sample fixtures and real-source/listening evidence are tracked by the
Provider implementation and consolidated acceptance batch.

## MPEG Layer III

dr_mp3 0.7.4 uses the same pinned dr_libs commit; original header SHA-256 is
`997b7ee18de6e6b81e2a83f1ea9fc62aef25c62b28d48db95635f49e65de0a2f`.
The selected MIT-0 text and upstream minimp3 CC0 dedication are preserved in the
source tree and APK license asset. Engine uses only its low-level frame decoder
with float output. There is no codec-owned Transport, Mixer or seek table. Engine
owns exact frame admission, encoded reads, gapless source coordinates, bounded
history checkpoints and one reusable frame scratch buffer. The SDK's tolerant
pull parser is deliberately not the admission boundary. File/stdio APIs are
disabled; strict flags apply to the wrapper and Engine. MP3 format acceptance is
PARTIAL and regression execution remains deferred to the consolidated batch.

## Ogg Vorbis

stb_vorbis 1.22 is pinned at stb commit
`2c980bb59875b0d32144a71867fbdebb2f77cd20`, unmodified source SHA-256
`4c7cb2ff1f7011e9d67950446b7eb9ca044f2e464d76bfbb0b84dd2e23e65636`.
The selected MIT notice is preserved and packaged in APK assets. Pure push APIs
use an Engine-owned arena; stdio, integer conversion and pull playback are disabled.
The derived build source contains one explicitly checked local patch: upstream
mutable global CRC initialization is replaced by the identical constexpr table.
This avoids data races when independent Providers open/decode on different workers.
The pinned original is kept intact; patch-context drift fails CMake configuration.
No callback lock or second Runtime is introduced. Floor 0, chained/multiplexed
streams and complete format acceptance remain outside the current subset.
