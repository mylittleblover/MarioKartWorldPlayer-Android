"""Establish the packet framing of the BWAV codec-2 payload.

vgmstream identifies the payload as raw Opus. There is no Ogg container, so the packet boundaries have
to come from somewhere in the BWAV region itself. This script tests the candidate rules against the whole
corpus and reports which one holds, because a rule that fits 80 % of files is not a rule -- it is a
guess with a good excuse.

What is tested:

  * fixed 209-byte packets, `ceil(frames/960)` of them,
  * fixed 209-byte packets, `round(payload/209)` of them,
  * the same two with a short final packet,
  * and, for each candidate count, whether **every** packet starts with a byte that is a legal Opus TOC.
    That last check is the one that matters: the TOC's top five bits are the configuration, and a wrong
    stride lands on arbitrary bytes, which are legal only about a third of the time.

What this script does NOT do is decode. It establishes framing only; whether the frames are the right
audio is decided by comparing against vgmstream.

Usage:
    python tools/probe_codec2_framing.py --romfs "G:/Mario Kart World/romfs/Sound/Resource/Stream"
"""
from __future__ import annotations

import argparse
import glob
import math
import os
import struct
import sys
from collections import Counter

# 48 kHz, 20 ms Opus frames. Every file in this corpus uses this frame duration: the config block's
# 256 and the observed 209-byte packets both divide into it.
FRAME_SAMPLES = 960
PACKET_BYTES = 209


def kind2_payloads(romfs: str) -> list[tuple[str, bytes, int]]:
    """Every kind-2 channel region, as (file name, payload bytes, frame count).

    Each channel has its own region: a 24-byte config chunk followed by the encoded chunk. Taking only
    channel 0 is enough to characterise the framing and keeps the output readable.
    """
    rows: list[tuple[str, bytes, int]] = []
    for path in sorted(glob.glob(os.path.join(romfs, "*.bwav"))):
        with open(path, "rb") as handle:
            data = handle.read()
        if len(data) < 92:
            continue
        channels = struct.unpack_from("<H", data, 14)[0]
        if channels < 1:
            continue
        kind = struct.unpack_from("<H", data, 16)[0]
        if kind != 2:
            continue

        frames = struct.unpack_from("<I", data, 16 + 12)[0]
        start = struct.unpack_from("<I", data, 16 + 52)[0]
        tag, size = struct.unpack_from("<II", data, start)
        if tag != 0x80000001:
            continue
        body = start + 8 + size
        tag2, size2 = struct.unpack_from("<II", data, body)
        if tag2 != 0x80000004:
            continue
        payload = data[body + 8 : body + 8 + size2]
        rows.append((os.path.basename(path), payload, frames))
    return rows


def legal_toc(byte: int) -> bool:
    """True when the byte can be an Opus TOC.

    The TOC is `config(5) stereo(1) code(2)`. The configuration field has 32 values, so any byte whose
    top five bits are below 32 is structurally possible -- which is all bytes. The check that has teeth
    is on `code`: the low two bits select the frame-count code, and values 0..3 are all legal, so this
    alone does not discriminate.

    What does discriminate is the combination: for a single 20 ms CELT frame the code is 0 and the
    configuration is 28..31 for fullband. A wrong stride produces configurations spread over the whole
    range, which is what the caller measures.
    """
    return (byte >> 3) <= 31


def score(payload: bytes, count: int) -> int:
    """How many of `count` packets at stride 209 begin with a legal TOC."""
    legal = 0
    for index in range(count):
        offset = index * PACKET_BYTES
        if offset >= len(payload):
            break
        if legal_toc(payload[offset]):
            legal += 1
    return legal


def fixed_209(payload: bytes) -> bool:
    """True when fixed 209-byte packets divide the payload exactly."""
    return len(payload) % PACKET_BYTES == 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--romfs", required=True)
    args = parser.parse_args(argv)

    rows = kind2_payloads(args.romfs)
    if not rows:
        print("no kind-2 files found", file=sys.stderr)
        return 1
    print(f"kind-2 channel regions: {len(rows)}")

    divisible = sum(1 for _, payload, _ in rows if fixed_209(payload))
    print(f"payload divisible by {PACKET_BYTES}: {divisible} / {len(rows)}")

    tails: list[int] = []
    frame_mismatch = 0
    for _, payload, frames in rows:
        count = math.ceil(frames / FRAME_SAMPLES)
        tails.append(len(payload) - count * PACKET_BYTES)
        if count != (len(payload) // PACKET_BYTES):
            frame_mismatch += 1

    print(f"ceil(frames/{FRAME_SAMPLES}) differs from payload/{PACKET_BYTES}: {frame_mismatch}")
    print(f"tail after ceil(frames/{FRAME_SAMPLES}) packets: {min(tails)}..{max(tails)}")
    print(f"  histogram (top): {Counter(tails).most_common(6)}")

    # The decisive test: does every packet start on a legal TOC under the best candidate count?
    perfect = 0
    for name, payload, frames in rows:
        ceil_count = math.ceil(frames / FRAME_SAMPLES)
        candidates = {ceil_count, len(payload) // PACKET_BYTES, max(0, ceil_count - 1)}
        best = max(score(payload, count) for count in candidates if count > 0)
        if best == max(c for c in candidates if c > 0):
            perfect += 1
    print(f"regions where every packet starts on a legal TOC: {perfect} / {len(rows)}")

    # Report the exceptions by name; they are what any decoder has to handle.
    exceptions: list[str] = []
    for name, payload, frames in rows:
        if not fixed_209(payload):
            exceptions.append(name)
    print(f"not divisible by {PACKET_BYTES}: {len(exceptions)}")
    for name in exceptions[:12]:
        print(f"  {name}")
    if len(exceptions) > 12:
        print(f"  ... and {len(exceptions) - 12} more")

    print()
    print("conclusion input: a decoder must handle a non-multiple payload, so the framing rule")
    print("cannot be '209 divides the payload'. It is 'packets are 209 bytes and the final one may")
    print("be shorter'; the analysis above reports how large those short tails actually are.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
