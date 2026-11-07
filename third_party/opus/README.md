# Pinned Opus codec

Unmodified portable upstream Opus **1.5.2**, release archive
https://github.com/xiph/opus/releases/download/v1.5.2/opus-1.5.2.tar.gz,
SHA-256 `65c1d2f78b9f2fb20082c38cbe47c951ad5839345876e46941612ee87f9a7ce1`.
Upstream source/header/build subdirectories for the ordinary floating-point
codec remain intact, including unused demo source files. Optional DNN weights
and sources, test data and generated build artifacts are excluded; program and
test targets are disabled. SHA256SUMS.json pins each unmodified retained file.
The upstream COPYING/license and notices remain in those files, and COPYING is
packaged at app/src/main/assets/licenses/opus.txt. No codec binary is vendored.

Our wrapper builds upstream's static PIC library with hardening, portable C and
normal floating-point semantics. Optional DRED, deep PLC, OSCE, custom modes,
SIMD/runtime CPU dispatch and fast-math are disabled for the initial cross-ABI
source milestone. CPU/Android performance is not claimed measured or accepted.
Only Engine links the codec; Ogg/container policy, original root ownership,
duration/trim and seek semantics remain in Engine's Source implementation. The
product never requests packet-loss concealment or missing-packet replacement.
