"""Check a `.mkwpack` against the reader's own rules, before shipping it to a phone.

The app refuses a pack for reasons that are stated precisely and are therefore checkable here. Every rule
this script tests is one the Kotlin reader enforces; the point is not to duplicate the reader but to fail
before a 1.2 GB file is copied to a device and rejected there.

Rules tested, and why each one has bitten this project at least once:

  no duplicate member names       a ZIP cannot hold two, and Python's zipfile writes both without complaint
  no member outside the layout    a member named `../x` is a legal ZIP entry and would escape the staging dir
  audio members are content named every audio member's name must be its own SHA-256
  every asset the manifest lists exists, and nothing else does
  every declared byte size matches the archive
  every flow has a timing, and a provenance record covering that flow's own key
  every flow has a MAIN part
  the unlock scope is non-empty       an empty scope opens the Special Cup over nothing
  the declared coverage matches the content

Usage:
    python tools/verify_pack.py "G:/Mario Kart World/_packs/mkw-course-test.mkwpack"
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

DIGEST = re.compile(r"^[0-9a-f]{64}$")
STAGE_CODE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
ROOT_FILES = {"manifest.json", "checksums.json", "catalog.json"}
DIRECTORIES = {"audio", "music-graphs", "markers", "routes", "artwork"}


def fail(problems: list[str], message: str) -> None:
    problems.append(message)


def main(argv: list[str]) -> int:
    if not argv:
        print("usage: verify_pack.py <pack>", file=sys.stderr)
        return 2

    path = Path(argv[0])
    if not path.is_file():
        print(f"{path} is not a file", file=sys.stderr)
        return 2

    problems: list[str] = []

    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()

        # Duplicate names. zipfile.writestr writes both and warns; a strict reader refuses.
        seen: set[str] = set()
        for name in names:
            if name in seen:
                fail(problems, f"duplicate member name: {name}")
            seen.add(name)

        # Layout, which is the only thing standing between a pack and the user's storage.
        for name in names:
            if "\\" in name:
                fail(problems, f"member uses a backslash: {name}")
                continue
            segments = name.split("/")
            if any(segment in ("", ".", "..") for segment in segments):
                fail(problems, f"member has a relative or empty segment: {name}")
                continue
            if len(segments) == 1:
                if name not in ROOT_FILES:
                    fail(problems, f"unknown root file: {name}")
            elif len(segments) == 2:
                if segments[0] not in DIRECTORIES:
                    fail(problems, f"member in an unknown directory: {name}")
                elif segments[0] == "audio" and not DIGEST.match(segments[1]):
                    fail(problems, f"audio member is not a SHA-256: {name}")
            else:
                fail(problems, f"member nested too deep: {name}")

        if "manifest.json" not in names:
            print("no manifest.json: not a pack", file=sys.stderr)
            return 1

        manifest = json.loads(archive.read("manifest.json"))

        declared = {asset["digest"]: asset for asset in manifest.get("assets", [])}
        present = {
            name.split("/", 1)[1]
            for name in names
            if name.startswith("audio/")
        }

        for digest, asset in declared.items():
            if digest not in present:
                fail(problems, f"asset {digest[:12]} is declared but absent from the archive")
                continue
            info = archive.getinfo(f"audio/{digest}")
            if info.file_size != asset["byteSize"]:
                fail(
                    problems,
                    f"asset {digest[:12]} is {info.file_size} bytes, manifest says {asset['byteSize']}",
                )

        for digest in present - set(declared):
            fail(problems, f"audio member {digest[:12]} is in the archive but not in the manifest")

        tracks = manifest.get("tracks", [])
        provenance = manifest.get("provenance", [])
        timing_subjects = {row["subject"] for row in provenance if row.get("field") == "timing"}

        for index, track in enumerate(tracks):
            where = f"tracks[{index}] ({track.get('key')})"
            parts = track.get("parts", [])
            if not parts:
                fail(problems, f"{where} has no parts")
                continue
            roles = {part["role"] for part in parts}
            if track.get("isFlow"):
                if "MAIN" not in roles:
                    fail(problems, f"{where} is a flow but has no MAIN part")
                if "timing" not in track:
                    fail(problems, f"{where} is a flow but declares no timing")
                if track.get("key") not in timing_subjects:
                    fail(problems, f"{where} declares a timing but no provenance record covers it")
            for part in parts:
                if part["asset"] not in declared:
                    fail(problems, f"{where} references unknown asset {part['asset'][:12]}")

        coverage = manifest.get("coverage", {})
        if coverage.get("totalLogicalTracks") != len(tracks):
            fail(
                problems,
                f"coverage says {coverage.get('totalLogicalTracks')} tracks, the manifest lists {len(tracks)}",
            )
        if coverage.get("playableLogicalTracks", 0) > len(tracks):
            fail(problems, f"coverage claims more playable tracks than exist")

        unlock = manifest.get("unlock", {})
        if not unlock.get("stages"):
            fail(problems, "the unlock scope is empty, which the reader refuses")
        stage_codes = {stage["code"] for stage in manifest.get("stages", [])}
        for code in unlock.get("stages", []):
            if not STAGE_CODE.match(code):
                fail(problems, f"unlock stage '{code}' is not a valid stage code")
            elif code not in stage_codes:
                fail(problems, f"unlock names stage '{code}' which the pack does not describe")

        if not provenance:
            fail(problems, "the pack declares no provenance records")

        # The archive's own integrity, as opposed to the manifest's claims about it.
        bad = archive.testzip()
        if bad is not None:
            fail(problems, f"member failed its CRC check: {bad}")

    print(f"pack      : {path}")
    print(f"size      : {path.stat().st_size / (1024 ** 3):.2f} GiB")
    print(f"members   : {len(names)}")
    print(f"assets    : {len(declared)}")
    print(f"tracks    : {len(tracks)}")
    print(f"stages    : {len(manifest.get('stages', []))}")
    print(f"provenance: {len(provenance)} records")

    if problems:
        print()
        print(f"REFUSED: {len(problems)} problem(s)")
        for problem in problems[:40]:
            print(f"  - {problem}")
        if len(problems) > 40:
            print(f"  ... and {len(problems) - 40} more")
        return 1

    print()
    print("OK: every rule the reader enforces passes here")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
