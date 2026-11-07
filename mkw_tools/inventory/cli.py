"""End-to-end OST audit over real local archives.

Read-only. Produces the provisional inventory, the class table and the gap
report. Never writes to the source tree.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path

from mkw_tools.assets.audio_probe import ProbeError, UnsupportedFormatError, probe_audio
from mkw_tools.assets.matcher import Confidence, propose_matches
from mkw_tools.bars.reader import read_bars
from mkw_tools.inventory.audit import build_report
from mkw_tools.inventory.classifier import AssetClass, classify_entry

MUSIC_PREFIXES = ("BGM_", "Dm_", "SE_")


def _music_wavs(stream_dir: Path) -> list[Path]:
    return sorted(
        p for p in stream_dir.iterdir()
        if p.suffix.lower() == ".wav" and p.name.startswith(MUSIC_PREFIXES)
    )


def run(bars_dir: Path, stream_dir: Path, archives_expected: int, out_dir: Path) -> int:
    archives = sorted(p for p in bars_dir.iterdir() if p.name.endswith(".bars.zs") and p.name.startswith("M_Bgm"))
    archives += sorted(p for p in bars_dir.iterdir() if p.name.startswith("BgmAll"))

    entries = []
    for archive in archives:
        entries.extend(read_bars(str(archive)))

    wavs = _music_wavs(stream_dir)
    probes = []
    probe_failures = []
    for wav in wavs:
        try:
            probes.append(probe_audio(str(wav)))
        except (ProbeError, UnsupportedFormatError) as exc:
            probe_failures.append({"path": str(wav), "error": str(exc)})

    pairs = [(f"bars:{e.ordinal}@{e.amta_offset}", e.internal_name) for e in entries]
    matches = propose_matches(pairs, probes)
    by_key = {m.entry_key: m for m in matches}

    items = []
    for entry, (key, _) in zip(entries, pairs):
        match = by_key[key]
        verified = match.confidence is Confidence.EXACT_VERIFIABLE and match.asset_sha256 is not None
        items.append(
            classify_entry(
                entry_key=key,
                internal_name=entry.internal_name,
                asset_path=match.asset_path,
                asset_sha256=match.asset_sha256,
                verified=verified,
            )
        )

    report = build_report(items, archives_expected=archives_expected, archives_read=len(archives))

    out_dir.mkdir(parents=True, exist_ok=True)

    inventory = {
        "schemaVersion": "provisional-v1",
        "freezeStatus": "PROVISIONAL_INCOMPLETE",
        "coverage": report.coverage.value,
        "archivesRead": [a.name for a in archives],
        "archivesExpected": archives_expected,
        "entryCount": len(entries),
        "probedAudioCount": len(probes),
        "probeFailures": probe_failures,
        "counts": {cls.value: report.counts[cls] for cls in AssetClass},
        "reasons": report.reasons,
        "items": [
            {
                "entryKey": i.entry_key,
                "internalName": i.internal_name,
                "class": i.asset_class.value,
                "assetPath": i.asset_path,
                "assetSha256": i.asset_sha256,
                "verified": i.verified,
            }
            for i in items
        ],
    }
    (out_dir / "temporary-index.json").write_text(
        json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    csv_lines = ["entry_key,internal_name,class,reason"]
    for row in report.missing_sources:
        csv_lines.append(
            f"{row['entry_key']},{row['internal_name']},{row['asset_class']},{row['reason']}"
        )
    (out_dir / "missing-sources.csv").write_text("\n".join(csv_lines) + "\n", encoding="utf-8")

    lines = [
        "# Coverage report",
        "",
        f"- archives read: {len(archives)} / {archives_expected}",
        f"- entries: {len(entries)}",
        f"- probed audio files: {len(probes)}",
        f"- coverage: **{report.coverage.value}**",
        "",
        "## Class counts",
        "",
    ]
    for cls in AssetClass:
        lines.append(f"- {cls.value}: {report.counts[cls]}")
    lines += ["", "## Reasons", ""]
    lines += [f"- {r}" for r in report.reasons]
    lines += ["", "## Probe failures", ""]
    lines += [f"- {f['path']}: {f['error']}" for f in probe_failures] or ["- none"]
    (out_dir / "coverage-report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"entries={len(entries)} probed={len(probes)} coverage={report.coverage.value}")
    print("counts:", {c.value: report.counts[c] for c in AssetClass})
    print("reasons:", report.reasons)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="mkw_tools.inventory.cli")
    sub = parser.add_subparsers(dest="command", required=True)

    inv = sub.add_parser("inventory", help="build provisional inventory and gap report")
    inv.add_argument("--bars-dir", required=True)
    inv.add_argument("--stream-dir", required=True)
    inv.add_argument("--archives-expected", type=int, required=True)
    inv.add_argument("--out", required=True)

    args = parser.parse_args(argv)
    if args.command == "inventory":
        return run(
            Path(args.bars_dir),
            Path(args.stream_dir),
            args.archives_expected,
            Path(args.out),
        )
    return 2


if __name__ == "__main__":
    sys.exit(main())
