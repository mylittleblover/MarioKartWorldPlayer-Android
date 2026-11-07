"""Run the PCM integrity check across every probed music WAV.

Reports facts, never a verdict. A truncated or silent file is a finding to be
recorded, not something to round up to OK.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import Counter
from pathlib import Path

from mkw_tools.assets.pcm_check import PcmStatus, check_pcm

MUSIC_PREFIXES = ("BGM_", "Dm_")


def run(stream_dir: Path, out_dir: Path, limit: int | None = None) -> int:
    wavs = sorted(
        p for p in stream_dir.iterdir()
        if p.suffix.lower() == ".wav" and p.name.startswith(MUSIC_PREFIXES)
    )
    if limit:
        wavs = wavs[:limit]

    rows: list[dict[str, object]] = []
    counts: Counter = Counter()
    for wav in wavs:
        try:
            result = check_pcm(str(wav))
        except Exception as exc:  # noqa: BLE001 - recorded, never swallowed
            counts["ERROR"] += 1
            rows.append({"file": wav.name, "status": "ERROR", "detail": str(exc)})
            continue
        counts[result.status.value] += 1
        rows.append(
            {
                "file": wav.name,
                "status": result.status.value,
                "frames_declared": result.frames_declared,
                "frames_decoded": result.frames_decoded,
                "channels": result.channels,
                "sample_rate": result.sample_rate,
                "bits": result.bits_per_sample,
                "peak": round(result.peak, 6),
                "detail": result.detail,
            }
        )

    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "pcm-integrity.json").write_text(
        json.dumps({"checked": len(rows), "counts": dict(counts), "rows": rows},
                   ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    with (out_dir / "pcm-integrity.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["file", "status", "frames_declared", "frames_decoded",
                        "channels", "sample_rate", "bits", "peak", "detail"],
        )
        writer.writeheader()
        writer.writerows(rows)

    print(f"checked {len(rows)} files")
    for status, count in counts.most_common():
        print(f"  {status:14s} {count}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="mkw_tools.assets.pcm_cli")
    sub = parser.add_subparsers(dest="command", required=True)
    cmd = sub.add_parser("verify", help="verify PCM integrity of music wavs")
    cmd.add_argument("--stream-dir", required=True)
    cmd.add_argument("--out", required=True)
    cmd.add_argument("--limit", type=int, default=None)
    args = parser.parse_args(argv)
    if args.command == "verify":
        return run(Path(args.stream_dir), Path(args.out), args.limit)
    return 2


if __name__ == "__main__":
    sys.exit(main())
