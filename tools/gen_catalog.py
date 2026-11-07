"""Build the catalog asset bundled into the APK.

The index in reports/generated is a *handoff artifact*: its paths point at this
machine and it records no codec information. What the app needs is different -- a name,
a category, and whether this build can actually decode the entry.

So this joins the index against the real BWAV files and emits only measured facts:

  - codecKind, sample rate, channels, frames, loop points  (read from the header)
  - playable                                              (derived from codecKind)

Everything the catalog cannot establish is omitted rather than guessed. The index's own
coverage disclaimer travels with the asset, so the app shows the same caveat the
evidence documents use.

Usage:
    python tools/gen_catalog.py --romfs "<romfs>/Sound/Resource/Stream" \
        --index reports/generated/temporary-index.json \
        --out app/src/main/assets/catalog.json
"""
from __future__ import annotations

import argparse
import json
import os
import struct
import sys

# Playability is decided by one rule and one rule only, matching Playability.kt.
PLAYABLE_KINDS = {1}


def read_bwav_header(path: str):
    """Read the fields the app can act on. Returns None when the file is not a BWAV."""
    with open(path, "rb") as handle:
        head = handle.read(16)
        if len(head) < 16 or head[:4] != b"BWAV":
            return None
        channels = struct.unpack_from("<H", head, 14)[0]
        if channels <= 0:
            return None
        rest = handle.read(channels * 76)

    def u16(offset: int) -> int:
        return struct.unpack_from("<H", rest, offset)[0]

    def u32(offset: int) -> int:
        return struct.unpack_from("<I", rest, offset)[0]

    codec = u16(0)
    sample_rate = u32(4)
    frames = u32(12)
    # Offsets 60 and 64, matching BwavHeader.kt: loopLast then loopFirst. Reading 48 and 8
    # here silently produced a catalog with no loop points at all.
    loop_last = u32(60)
    loop_first = u32(64)

    # A loop is only meaningful when both ends are set, ordered, and inside the track.
    # 0xFFFFFFFF is the "no loop" sentinel.
    has_loop = (
        loop_last != 0xFFFFFFFF
        and 0 < loop_first < loop_last <= frames
    )

    return {
        "codecKind": codec,
        "sampleRate": sample_rate,
        "channels": channels,
        "frames": frames,
        "loopStart": loop_first if has_loop else None,
        "loopEnd": loop_last if has_loop else None,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--romfs", required=True, help="directory holding the .bwav files")
    parser.add_argument("--index", required=True, help="reports/generated/temporary-index.json")
    parser.add_argument("--out", required=True, help="asset path to write")
    args = parser.parse_args()

    with open(args.index, "r", encoding="utf-8") as handle:
        index = json.load(handle)

    entries = []
    missing = []
    unreadable = []

    for item in index["items"]:
        name = item["internalName"]
        path = os.path.join(args.romfs, name + ".bwav")
        if not os.path.exists(path):
            missing.append(name)
            continue
        header = read_bwav_header(path)
        if header is None:
            unreadable.append(name)
            continue

        playable = header["codecKind"] in PLAYABLE_KINDS
        entries.append(
            {
                "name": name,
                "category": item["class"],
                "codecKind": header["codecKind"],
                "sampleRate": header["sampleRate"],
                "channels": header["channels"],
                "frames": header["frames"],
                "loopStart": header["loopStart"],
                "loopEnd": header["loopEnd"],
                "playable": playable,
                "reason": None
                if playable
                else f"codec kind {header['codecKind']} is not decodable in this build",
            }
        )

    entries.sort(key=lambda e: (e["category"], e["name"]))

    counts: dict[str, int] = {}
    playable_by_category: dict[str, int] = {}
    for entry in entries:
        counts[entry["category"]] = counts.get(entry["category"], 0) + 1
        if entry["playable"]:
            playable_by_category[entry["category"]] = (
                playable_by_category.get(entry["category"], 0) + 1
            )

    catalog = {
        "schemaVersion": 1,
        "source": "reports/generated/temporary-index.json joined against measured BWAV headers",
        # Carried through verbatim: the app must not present this as a complete catalog.
        "freezeStatus": index.get("freezeStatus"),
        "coverage": index.get("coverage"),
        "coverageReasons": index.get("reasons", []),
        "archivesRead": index.get("archivesRead"),
        "archivesExpected": index.get("archivesExpected"),
        "entryCount": len(entries),
        "playableCount": sum(1 for e in entries if e["playable"]),
        "categoryCounts": counts,
        "playableByCategory": playable_by_category,
        "missingSources": len(missing),
        "unreadableSources": len(unreadable),
        "entries": entries,
    }

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(catalog, handle, indent=1, sort_keys=False)
        handle.write("\n")

    print(f"entries: {len(entries)}  playable: {catalog['playableCount']}")
    print(f"missing sources: {len(missing)}  unreadable: {len(unreadable)}")
    print("by category:", json.dumps(counts, sort_keys=True))
    print("playable by category:", json.dumps(playable_by_category, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
