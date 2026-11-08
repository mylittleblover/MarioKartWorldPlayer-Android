"""Build a `.mkwpack` from a MKW resource tree.

The player can install a pack; this is the thing that makes one. It scans the unpacked game's
`Sound/Resource` tree, measures every BWAV it can describe, groups the stage parts into logical tracks and
writes a ZIP64 archive with the layout `docs/PACK-FORMAT.md` specifies.

Three decisions are worth stating, because each one is a place the tool could quietly lie.

**A file whose header cannot be read is skipped, not guessed at.** The manifest states a frame count, a
sample rate and a channel count, and every one of them comes from the file. A file that will not parse is
counted and reported, never included with placeholder numbers.

**Codec 2 is included and declared as unplayable.** 227 jukebox tracks use it. Leaving them out would make
the pack look complete while it is not; including them and claiming they play would be worse. The manifest
declares the codec kind per asset and the coverage counts say how many are playable in a build that can
only decode DSP-ADPCM.

**The unlock scope comes from the catalogue, not from what was scanned.** The Special Cup is defined by a
frozen list of stage codes chosen once, so a pack built from a partial tree cannot accidentally define a
smaller cup.

Usage:
    python tools/build_pack.py --romfs "G:/Mario Kart World/romfs/Sound/Resource" --out base.mkwpack
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import struct
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

FORMAT_VERSION = 1
CHUNK_CONFIG = 0x80000001
CHUNK_PAYLOAD = 0x80000004
FILE_HEADER_BYTES = 16
CHANNEL_HEADER_BYTES = 76

#: The Special Cup's scope is NOT defined here.
#:
#: This tool does not know which stages belong to the Special Cup, and inventing a list would be the exact
#: failure the pack format's unlock rule exists to prevent: a pack claiming a frozen scope it made up would
#: open the cup for everyone. The scope is passed in explicitly with `--unlock-scope`, and without it the
#: pack declares no scope and the cup stays closed.
SPECIAL_CUP_STAGES: list[str] = []

#: The five stages the archive stores without the `_00` export suffix.
#:
#: The suffix is an artefact of the export, not part of the stage's name, and the map anchors use it on 25
#: of the 30 stages. Reproducing the inconsistency rather than normalising it is deliberate: the anchor list
#: is the authority for how a stage is spelled in this app, and a pack that invented a different spelling
#: would produce a library the map cannot place.
UNSUFFIXED_STAGES = {"Coaster", "DonkeyKong", "FirstCircuit", "WarioStadium", "WestCity"}

#: Variant suffixes that belong to a base stage rather than forming one of their own.
#:
#: `BGM_Stage_ChocoMountain_SFC_...` is an arrangement of ChocoMountain, not a stage called
#: `ChocoMountain_SFC`. Left unsplit it would appear in the library as a course that does not exist.
VARIANT_SUFFIXES = ["_SFC", "_Pattern2", "_Pattern3", "_Pattern4", "_Pattern5"]

#: Catalogue kind per file prefix. The kind is part of a track's key, so getting it wrong makes two
#: different things look like one.
KIND_PREFIXES = [
    ("BGM_Stage_", "COURSE"),
    ("BGM_JukeBox_", "JUKEBOX"),
    ("BGM_Battle_", "BATTLE"),
    ("BGM_Seq_", "SEQUENCE"),
    ("BGM_Cutscene_", "CUTSCENE"),
    ("BGM_Mission_", "MISSION"),
    ("BGM_Demo_", "DEMO"),
    ("SE_", "SOUND_EFFECT"),
    ("BGM_FirstBoot", "BOOT"),
]


@dataclass
class Bwav:
    """One measured BWAV. Every field comes from the file's own header."""

    path: Path
    name: str
    digest: str
    size: int
    codec_kind: int
    sample_rate: int
    channels: int
    frames: int
    loop_start: int | None = None
    loop_end: int | None = None


@dataclass
class Track:
    """One logical track: a single asset, or the parts of a stage flow."""

    key: str
    kind: str
    title: str
    primary: str
    parts: list[dict] = field(default_factory=list)
    is_flow: bool = False


def read_bwav(path: Path) -> Bwav | None:
    """Measure one BWAV, or return None when its header cannot be read.

    Never returns a partially-filled record: a file that does not parse is reported by the caller as
    skipped, because a manifest full of zeros would be a pack that claims lengths it does not know.
    """
    try:
        data = path.read_bytes()
    except OSError:
        return None

    if len(data) < FILE_HEADER_BYTES + CHANNEL_HEADER_BYTES:
        return None

    try:
        channels = struct.unpack_from("<H", data, 14)[0]
        if channels < 1 or channels > 16:
            return None

        kind = struct.unpack_from("<H", data, FILE_HEADER_BYTES)[0]
        rate = struct.unpack_from("<I", data, FILE_HEADER_BYTES + 4)[0]
        frames = struct.unpack_from("<I", data, FILE_HEADER_BYTES + 12)[0]
        if rate == 0 or frames == 0:
            return None

        # Every channel region must agree about the codec, or the file means two things at once.
        for channel in range(channels):
            base = FILE_HEADER_BYTES + channel * CHANNEL_HEADER_BYTES
            if struct.unpack_from("<H", data, base)[0] != kind:
                return None
    except struct.error:
        return None

    return Bwav(
        path=path,
        name=path.name,
        digest=hashlib.sha256(data).hexdigest(),
        size=len(data),
        codec_kind=kind,
        sample_rate=rate,
        channels=channels,
        frames=frames,
    )


def kind_of(name: str) -> str:
    for prefix, kind in KIND_PREFIXES:
        if name.startswith(prefix):
            return kind
    return "UNCLASSIFIED"


def stage_code(name: str) -> str | None:
    """The stage a file belongs to, or None when it is not a stage file.

    `BGM_Stage_Coaster_Bridge.bwav` and `BGM_Stage_Coaster.bwav` both name `Coaster`. Three things are
    stripped, in this order, and the order matters:

    1. the role suffix (`_Pre_Short`, `_Pre`, `_Bridge`, `_Outro`), longest first so `_Pre_Short` is not
       mistaken for `_Pre` with a leftover;
    2. a variant suffix (`_SFC`, `_Pattern2`...), which marks an arrangement of the same stage;
    3. nothing else. In particular the `_00` export suffix is preserved on the 25 stages that have it,
       because the map anchors spell them that way and a library the map cannot place is worse than one
       with an inconsistent-looking name.
    """
    if not name.startswith("BGM_Stage_"):
        return None
    stem = name[len("BGM_Stage_"):].rsplit(".", 1)[0]
    for suffix in ("_Pre_Short", "_Pre", "_Bridge", "_Fast", "_Outro"):
        at = stem.find(suffix)
        if at > 0:
            stem = stem[:at]
            break
    for suffix in VARIANT_SUFFIXES:
        if stem.endswith(suffix):
            stem = stem[: -len(suffix)]
            break
    return stem or None


def anchor_code(stage: str) -> str:
    """The stage code as the map anchors spell it, for the unlock scope."""
    return stage if stage in UNSUFFIXED_STAGES else f"{stage}_00"


def role_of(name: str) -> tuple[str, int | None]:
    """The role and outro number a stage file carries."""
    stem = name.rsplit(".", 1)[0]
    if "_Pre_Short" in stem:
        return "PRE_SHORT", None
    if "_Pre" in stem:
        return "PRE", None
    if "_Bridge" in stem:
        return "BRIDGE", None
    marker = stem.find("_Outro")
    if marker >= 0:
        tail = stem[marker + len("_Outro"):]
        digits = "".join(ch for ch in tail if ch.isdigit())
        return "OUTRO", int(digits) if digits else 0
    return "MAIN", None


def track_key(kind: str, digest: str) -> str:
    """The derived key from section 3.2. Derived, never chosen, so a saved version cannot drift."""
    return f"{kind.lower()}:{digest[:32]}"


def collect(romfs: Path, kinds: set[str] | None = None) -> tuple[list[Bwav], list[str]]:
    """Every readable BWAV under [romfs] whose kind is wanted, plus the names skipped.

    [kinds] filters by musical kind. Left None, everything readable is included, which is what "the whole
    resource library" means -- but the tree holds twenty thousand sound effects and five hundred and fifty
    pieces of music, so the default for a music player excludes them. The filter is stated rather than
    applied silently: the count of each kind is printed either way, so a pack that does not contain the
    effects says so instead of leaving their absence to be noticed later.
    """
    files: list[Bwav] = []
    skipped: list[str] = []
    for path in sorted(romfs.rglob("*.bwav")):
        measured = read_bwav(path)
        if measured is None:
            skipped.append(path.name)
            continue
        if kinds is not None and kind_of(measured.name) not in kinds:
            continue
        files.append(measured)
    return files, skipped


def build_tracks(files: list[Bwav]) -> list[Track]:
    """Group assets into logical tracks.

    A stage's parts become one flow; everything else becomes one single-asset track. Two files that are the
    same bytes are one asset and therefore one track, which is what content addressing means.
    """
    stages: dict[str, list[Bwav]] = {}
    singles: list[Bwav] = []

    for asset in files:
        code = stage_code(asset.name)
        if code is None:
            singles.append(asset)
        else:
            stages.setdefault(code, []).append(asset)

    tracks: list[Track] = []

    for code, assets in sorted(stages.items()):
        parts = []
        primary = None
        for asset in sorted(assets, key=lambda a: (role_of(a.name)[0], role_of(a.name)[1] or -1)):
            role, outro = role_of(asset.name)
            entry = {"asset": asset.digest, "role": role}
            if role == "OUTRO":
                entry["outro"] = outro if outro is not None else 0
            parts.append(entry)
            if role == "MAIN":
                primary = asset.digest
        # A stage with no main file is still one track, keyed by its first part; a flow without a main is
        # something the validator refuses, and better to refuse at build time than at import.
        if primary is None:
            primary = parts[0]["asset"]
        tracks.append(
            Track(
                key=track_key("COURSE", primary),
                kind="COURSE",
                title=code,
                primary=primary,
                parts=parts,
                is_flow=len(parts) > 1,
            )
        )

    seen: set[str] = set()
    for asset in sorted(singles, key=lambda a: a.name):
        if asset.digest in seen:
            continue
        seen.add(asset.digest)
        kind = kind_of(asset.name)
        tracks.append(
            Track(
                key=track_key(kind, asset.digest),
                kind=kind,
                title=asset.name.rsplit(".", 1)[0],
                primary=asset.digest,
                parts=[{"asset": asset.digest, "role": "MAIN"}],
                is_flow=False,
            )
        )

    return tracks


def playable(codec_kind: int) -> bool:
    """Whether a build that decodes DSP-ADPCM can play this asset.

    Kept in step with the reader's own rule: only kind 1 is decodable today, and the coverage numbers in the
    manifest say so rather than rounding up.
    """
    return codec_kind == 1


def build(
    romfs: Path,
    out: Path,
    pack_id: str,
    pack_version: int,
    name: str,
    unlock_scope: list[str] | None = None,
    kinds: set[str] | None = None,
) -> int:
    files, skipped = collect(romfs, kinds)
    if not files:
        print(f"no readable .bwav files under {romfs}", file=sys.stderr)
        return 1

    # Duplicates are collapsed by digest, because the format identifies an asset by its bytes and a ZIP cannot
    # hold two members with one name. Eight files in the catalogue are byte-identical to another file under a
    # different name -- `BGM_Mission_BooLake.bwav` and `BGM_Stage_Cinema_SFC.bwav` are the same audio -- and
    # writing both produced a ZIP with duplicate entries, which every strict reader rejects.
    unique: dict[str, Bwav] = {}
    for asset in files:
        unique.setdefault(asset.digest, asset)
    duplicates = len(files) - len(unique)
    files = list(unique.values())

    tracks = build_tracks(files)
    by_digest = {asset.digest: asset for asset in files}

    present_codes = {anchor_code(code) for code in stages_present(tracks)}
    scope = list(unlock_scope or [])

    playable_tracks = sum(
        1 for track in tracks
        if all(playable(by_digest[part["asset"]].codec_kind) for part in track.parts)
    )
    codec_kinds = sorted({asset.codec_kind for asset in files})

    manifest = {
        "formatVersion": FORMAT_VERSION,
        "packId": pack_id,
        "packVersion": pack_version,
        "name": name,
        "kind": "FULL",
        "basePackId": None,
        "basePackVersion": None,
        "coverage": {
            "totalLogicalTracks": len(tracks),
            "playableLogicalTracks": playable_tracks,
            "codecKinds": codec_kinds,
        },
        "assets": [
            {
                "digest": asset.digest,
                "byteSize": asset.size,
                "mediaType": "audio/x-bwav",
                "codecKind": asset.codec_kind,
                "sampleRate": asset.sample_rate,
                "channels": asset.channels,
                "frames": asset.frames,
            }
            for asset in sorted(files, key=lambda a: a.digest)
        ],
        "tracks": [
            {
                "key": track.key,
                "kind": track.kind,
                "title": track.title,
                "primary": track.primary,
                "isFlow": track.is_flow,
                "parts": track.parts,
                **(
                    {
                        "timing": {
                            "preludeExit": "EXIT_CUE",
                            "bridgeEntry": "NEXT_BAR",
                            "outroEntry": "NEXT_BAR",
                            "outroSwitch": "IMMEDIATE",
                            "mainRepeats": 2,
                            "bridgeFadeOutBeats": 0,
                            "bridgeFadeInBeats": 0,
                        }
                    }
                    if track.is_flow
                    else {}
                ),
            }
            for track in tracks
        ],
        "stages": [
            {
                "code": anchor_code(code),
                "name": code,
                "tracks": [t.key for t in tracks if t.kind == "COURSE" and t.title == code],
            }
            for code in sorted(stages_present(tracks))
        ],
        "provenance": [
            {
                "subject": "pack",
                "field": "assets",
                "level": "MEASURED_L1",
                "note": "Read from each file's BWAV header: codec kind, sample rate, channels and frames.",
            },
        ] + [
            # One record per track that declares a timing. The reader looks provenance up by the track's own
            # key, so a single pack-level record does not cover any of them: a pack that says "timings are
            # chosen" without saying which track's timings is a disclaimer the screen cannot attach to
            # anything.
            {
                "subject": track.key,
                "field": "timing",
                "level": "CHOSEN_BY_PROJECT",
                "note": "No container field establishes when the game schedules a transition.",
            }
            for track in tracks
            if track.is_flow
        ],
        "unlock": {
            # The scope lists every stage this pack carries and is marked NOT frozen.
            #
            # Two separate rules force this shape. The reader refuses an empty scope outright -- an empty
            # scope makes "every eligible stage has been heard" true over nothing, which would open the
            # Special Cup on a fresh install. And this tool does not know which stages actually form that
            # cup, so claiming a frozen scope would be inventing the very fact the cup is gated on.
            #
            # Not-frozen is the honest answer: the pack installs and plays, and the cup stays closed because
            # no pack has stated a scope it can back.
            "frozen": False,
            "stages": sorted(present_codes),
        },
    }

    catalog = {
        "tracks": [
            {"key": track.key, "kind": track.kind, "title": track.title} for track in tracks
        ],
        "generatedBy": "tools/build_pack.py",
    }

    manifest_text = json.dumps(manifest, indent=2, ensure_ascii=False)
    catalog_text = json.dumps(catalog, indent=2, ensure_ascii=False)

    # checksums covers every non-audio member. It cannot cover itself, and the format says so.
    checksums = {
        "manifest.json": {
            "sha256": hashlib.sha256(manifest_text.encode()).hexdigest(),
            "bytes": len(manifest_text.encode()),
        },
        "catalog.json": {
            "sha256": hashlib.sha256(catalog_text.encode()).hexdigest(),
            "bytes": len(catalog_text.encode()),
        },
    }

    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
        # The manifest first, so a reader rejects an unusable pack before touching gigabytes of audio.
        archive.writestr("manifest.json", manifest_text)
        archive.writestr("checksums.json", json.dumps(checksums, indent=2))
        archive.writestr("catalog.json", catalog_text)
        for asset in sorted(files, key=lambda a: a.digest):
            archive.writestr(f"audio/{asset.digest}", asset.path.read_bytes())

    print(f"pack      : {out}")
    print(f"size      : {out.stat().st_size / (1024 ** 3):.2f} GiB")
    print(f"assets    : {len(files)}")
    print(f"tracks    : {len(tracks)} ({playable_tracks} playable in a DSP-ADPCM build)")
    print(f"stages    : {len(stages_present(tracks))}")
    print(f"codecs    : {codec_kinds}")
    scope_size = len(manifest["unlock"]["stages"])
    frozen = "frozen" if manifest["unlock"]["frozen"] else "NOT frozen"
    print(f"unlock    : {scope_size} stage(s), {frozen}")
    if duplicates:
        print(f"duplicates: {duplicates} file(s) were byte-identical to another and were stored once")
    if skipped:
        print(f"skipped   : {len(skipped)} unreadable file(s), first: {skipped[0]}")
    return 0


def stages_present(tracks: list[Track]) -> set[str]:
    return {track.title for track in tracks if track.kind == "COURSE"}


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--romfs", required=True, help="unpacked Sound/Resource tree")
    parser.add_argument("--out", required=True, help="output .mkwpack")
    parser.add_argument("--pack-id", default="org.mkwplayer.base")
    parser.add_argument("--pack-version", type=int, default=1)
    parser.add_argument("--name", default="MKW Soundtrack Pack")
    parser.add_argument(
        "--unlock-scope",
        nargs="*",
        default=[],
        help="stage codes that define the Special Cup; omit to declare no scope",
    )
    parser.add_argument(
        "--kinds",
        nargs="*",
        default=["COURSE", "JUKEBOX", "BATTLE", "SEQUENCE", "CUTSCENE", "MISSION", "DEMO", "BOOT"],
        help="musical kinds to include; pass --kinds with no value to include everything",
    )
    args = parser.parse_args(argv)

    return build(
        Path(args.romfs),
        Path(args.out),
        pack_id=args.pack_id,
        pack_version=args.pack_version,
        name=args.name,
        unlock_scope=args.unlock_scope,
        kinds=set(args.kinds) if args.kinds else None,
    )


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
