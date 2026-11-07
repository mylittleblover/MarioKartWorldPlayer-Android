# Pinned Module renderer dependency

Upstream: https://lib.openmpt.org/files/libopenmpt/src/libopenmpt-0.8.9+release.autotools.tar.gz
Version: 0.8.9, SHA-256 `d7ce84fd05d686c4bcf66af40eae857afa371442db60eeda3f874bd6cf6fc318`.
The original source archive and license are preserved. CMake verifies its digest,
extracts locally without network and derives 149 translation units from its own
Makefile.am. Host and Android use the identical renderer target.

The library's BSD-3-Clause license and mpt BSD/Boost notices are packaged as APK
assets and remain present in the source archive. Optional system mpg123,
Vorbis/OGG/zlib, external plugin hosts and platform output APIs are not enabled.
MO3 sample decoding now enables pinned minimp3 (CC0/public domain, revision
ea99364f61c14656440e8d77e9c233ccf3124633) and the already reviewed stb_vorbis
(selected MIT license). Sources are digest-checked, static/PIC and built by the
same target on host and all Android ABIs; notices are packaged in APK assets.
MO3 Vorbis uses separate prefixed symbols and the same constexpr CRC-table fix,
avoiding global initialization races and collisions with standalone Vorbis.
Their allocations run only during worker-side Module loading/rendering; neither
decoder is called from the PLAYER² audio callback. No proprietary UNMO3/BASS
binary or encoder is linked or redistributed.
No external library Transport, Game Sync or PLAYER² Mixer/Transition is adopted.
Its internal instrument mixing belongs only to source rendering.

One explicit local constructor hook initializes the per-source PRNG from the
`player2.seed` initial ctl before load. It does not reseed global random state.
The archive remains unchanged; the hook is visible in CMake and applied only to
the build directory. A renderer must recreate this state for exact replay seek;
Native float rendering avoids integer dither. Version/seed/render settings must
participate in derived-cache identity. Cross-ABI bit equality is not assumed.

No realtime allocation promise is made for the library: direct calls belong to
control/decoder workers. Module rendering must reach the same PLAYER² Runtime
through bounded source preparation/streaming, never in an Oboe callback.
Upstream tolerant recovery is insufficient for admission: PLAYER² must perform
format-specific bounded validation and refuse truncated/missing samples.

The visible prepare_mo3.cmake patch rejects truncated sample bitstreams, missing
sample codecs and incompletely decoded MPEG/Vorbis samples. Upstream's tolerant
silent recovery is insufficient for PLAYER² admission. The original archive is
unchanged and the modified revision remains marked 25652M. New wrapper admission
also bounds the MO3 structure, patterns, decoded sample heap and encoded chunks.
Provider acceptance is separate from this dependency checkpoint. MOD/XM are
PARTIAL; IT/S3M remain PLANNED-P1 as standalone providers. MO3 provider work and
its format-specific acceptance are recorded in IMPLEMENTATION_STATUS.md.
