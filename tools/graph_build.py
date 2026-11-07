"""Build a minimal compiled graph v6 that plays one BWAV asset.

The layout mirrors engine/native/music/GraphReader.cpp exactly: big-endian
fields, magic 0x50324731, body length and CRC32 over everything after the
16-byte header.
"""
import argparse
import json
import os
import struct
import zlib

MAGIC = 0x50324731
VERSION = 6
SR = 48000


def node(index, kind, parent, bpm, props, configured=0):
    b = struct.pack(">iii", index, kind, parent)
    b += struct.pack(">d", bpm)
    b += struct.pack(">ii", 4, 4)          # numerator, denominator
    b += struct.pack(">dd", 1.0, 0.0)      # grid, gridOffset
    b += struct.pack(">i", 7)              # property count sentinel
    for v in props:
        b += struct.pack(">d", v)
    b += struct.pack(">I", configured)
    return b


def clip(index, asset, length, gain=0.0, loop=None):
    b = struct.pack(">ii", index, asset)              # index, asset
    b += struct.pack(">qqq", 0, 0, length)            # start, sourceOffset, sourceLength
    b += struct.pack(">d", gain)
    b += struct.pack(">qq", 0, 0)                     # fadeIn, fadeOut
    if loop is None:
        b += struct.pack(">B", 0)
    else:
        start, end, count = loop
        b += struct.pack(">B", 1)
        b += struct.pack(">qqi", start, end, count)
    return b


def build(asset_path, asset_frames, asset_sha256, asset_rate=SR, asset_channels=2,
          loop=None, bpm=140.0, exit_frame=None):
    props = [0.0, 1.0, 20000.0, 20.0, 0.0, 0.0, 0.0]   # descriptor defaults
    exit_frame = asset_frames if exit_frame is None else exit_frame

    body = b""

    # The body opens with the transport sample rate, then the identity table.
    body += struct.pack(">i", SR)

    # identities
    identities = ["asset", "segment", "track", "clip"]
    body += struct.pack(">i", len(identities))
    for name in identities:
        raw = name.encode("utf-8")
        body += struct.pack(">i", len(raw)) + raw

    # nodes: segment(1), track(2, parent 1)
    body += struct.pack(">i", 2)
    body += node(1, 1, -1, bpm, props)
    body += node(2, 2, 1, bpm, props)

    # segments
    body += struct.pack(">i", 1)
    body += struct.pack(">i", 1)              # index
    body += struct.pack(">qq", 0, exit_frame)  # entry, exit
    body += struct.pack(">B", 0)              # hasLoop
    body += struct.pack(">i", 0)              # cues

    # tracks
    body += struct.pack(">i", 1)
    body += struct.pack(">i", 2)              # index
    body += struct.pack(">i", 1)              # segment
    body += struct.pack(">i", 0)              # mode Normal
    body += struct.pack(">i", -1)             # group
    body += struct.pack(">i", -1)             # bus
    body += struct.pack(">q", 1)              # seed
    body += struct.pack(">i", 0)              # avoidRepeat
    body += struct.pack(">B", 0)              # reset
    body += struct.pack(">i", 1)              # clips
    body += clip(3, 0, asset_frames, loop=loop)
    body += struct.pack(">i", 0)              # subtracks
    body += struct.pack(">i", 0)              # sends

    body += struct.pack(">i", 0)              # playlists
    body += struct.pack(">i", 0)              # switches
    body += struct.pack(">i", 0)              # groups
    body += struct.pack(">i", 0)              # parameters
    body += struct.pack(">i", 0)              # events
    body += struct.pack(">i", 0)              # transitions
    body += struct.pack(">i", 0)              # curves
    body += struct.pack(">i", 0)              # stingers
    body += struct.pack(">i", 0)              # buses

    # assets
    body += struct.pack(">i", 1)
    body += struct.pack(">i", 0)              # index
    raw = asset_path.encode("utf-8")
    body += struct.pack(">i", len(raw)) + raw
    body += struct.pack(">ii", asset_rate, asset_channels)
    body += struct.pack(">qq", asset_frames, 0)
    body += bytes.fromhex(asset_sha256)       # v3 digest
    body += struct.pack(">B", 0)              # v4 hasSource

    body += struct.pack(">i", 0)              # entries
    body += struct.pack(">i", 0)              # performances
    body += struct.pack(">B", 0)              # hasMusicalLoop
    body += struct.pack(">B", 0)              # hasAutomatic

    # Header is magic, version, bodyLength, checksum, then the body. The checksum
    # covers only the body (everything after byte 16).
    crc = zlib.crc32(body) & 0xFFFFFFFF
    packet = struct.pack(">IiiI", MAGIC, VERSION, len(body), crc) + body
    return packet


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--asset-path", required=True)
    ap.add_argument("--frames", type=int, required=True)
    ap.add_argument("--sha256", required=True)
    ap.add_argument("--rate", type=int, default=SR)
    ap.add_argument("--channels", type=int, default=2)
    ap.add_argument("--loop-start", type=int)
    ap.add_argument("--loop-end", type=int)
    ap.add_argument("--loop-count", type=int, default=-1)
    ap.add_argument("--bpm", type=float, default=140.0)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    loop = None
    if args.loop_start is not None:
        loop = (args.loop_start, args.loop_end, args.loop_count)

    packet = build(args.asset_path, args.frames, args.sha256, args.rate,
                   args.channels, loop, args.bpm)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write(packet.hex())
        f.write("\n")
    print(f"wrote {args.out}: {len(packet)} bytes, version {VERSION}, "
          f"loop={loop}")


if __name__ == "__main__":
    main()
