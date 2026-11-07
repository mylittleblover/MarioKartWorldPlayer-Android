"""Bounded reader for Nintendo BARS sound archives.

Verified container layout (measured against four real MKW archives; see
evidence/bootstrap/bars-format.md):

    +0   "BARS"
    +4   u32 total decompressed size
    +8   u32 version tag (0x0102FEFF)
    +12  u32 entry count
    +16  u32[count]                per-entry content hash
    +16+4*count  (u32 amta, u32 data)[count]   interleaved offset pairs
    ...  AMTA blocks

Every AMTA block starts with "AMTA" + 0x0500FEFF and carries:

    +8   u32   declared block size
    +16  u32   header length (56 in 635/650 entries, 64 in the other 15)
    ...  remaining header fields (semantics partly UNKNOWN)

The fade/gain envelope sits at `headerLength + 4`, as four float32:

    +0  f32  fade-in  seconds   (0.059..0.270 across all 650 entries)
    +4  f32  fade-out seconds   (0.013..0.072)
    +8  f32  fader gain dB      (-38.3..-20.8)
    +12 f32  main gain dB       (-40.4..-22.9)

Using `headerLength + 4` instead of a hard-coded 60 is what makes the 24
BgmAll entries parse: they carry headerLength=64 and previously produced
implausible values. Both encodings are now covered by tests.

IDENTITY WARNING
----------------
An asset name is NOT a unique key. Real archives contain distinct entries
that share one name (e.g. `BGM_Stage_SnowMountain_Outro01` appears in 6
separate entries with different envelopes). Entries are therefore identified
by `ordinal` plus `amta_offset`; the name is a descriptor only.

What this module deliberately does NOT do
-----------------------------------------
The binding between marker names and sample positions is not proven. Twelve
byte records in the Coaster block do hold the correct positions, but in an
order that does not match the marker numbers, and the research marker IDs do
not appear anywhere in the block. Positions are therefore left empty with
`MarkerStatus.UNRESOLVED` rather than guessed.
"""

from __future__ import annotations

import math
import re
import struct
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

__all__ = [
    "BarsEntry",
    "BarsFormatError",
    "BarsLimitError",
    "Envelope",
    "Marker",
    "MarkerStatus",
    "read_bars",
]

BARS_MAGIC = b"BARS"
AMTA_MAGIC = b"AMTA"

AMTA_MIN_HEADER = 56
AMTA_MAX_HEADER = 4096
ENVELOPE_LEAD = 4

# Plausibility windows derived from every entry of every real archive.
FADE_IN_RANGE = (0.01, 5.0)
FADE_OUT_RANGE = (0.001, 5.0)
GAIN_RANGE = (-96.0, 12.0)

MAX_ENTRIES = 1 << 20
MAX_DECOMPRESSED = 512 * 1024 * 1024

# Asset names are NUL-terminated. Three prefix families appear in real
# archives: BGM_ (music), Dm_ (cutscene), SE_ (sound effect). Real names also
# contain apostrophes and hyphens, e.g. `BGM_JukeBox_M488_MK7_Rosalina'sIceWorld`.
_NAME_RE = re.compile(rb"([A-Za-z][A-Za-z0-9_'.\- ]{3,})\x00")
_PREFIXED = re.compile(r"^(?:BGM|Dm|SE)_")
# Transition labels that live in the same string table but are not assets.
_TRANSITION = re.compile(
    r"^(?:Bridge_To\w+|Outro_To\w+|Outro_ToNext\w*|Full_ToOutro\d+"
    r"|Pre_Short.*|Pre_Normal|Fast_Pattern\d*)$"
)
# Marker names are NUL-terminated and never prefixed.
_MARKER_RE = re.compile(rb"(Full_ToOutro[0-9]+)\x00")


class BarsFormatError(ValueError):
    """The bytes are not a well-formed BARS archive."""


class BarsLimitError(BarsFormatError):
    """A declared value exceeds a defensive bound."""


class MarkerStatus(str, Enum):
    RESOLVED = "RESOLVED"
    UNRESOLVED = "UNRESOLVED"
    NONE = "NONE"


@dataclass(frozen=True)
class Marker:
    name: str
    source_sample_position: int


@dataclass(frozen=True)
class Envelope:
    """L1 metadata inference read from the AMTA header.

    Four numbers per entry. Their runtime semantics -- which bus or object they
    apply to, whether an RTPC overrides them, whether the units are really
    seconds and dB -- is UNVERIFIED and must not be presented as original game
    behaviour until behaviour evidence (L3) exists.
    """

    fade_in_seconds: float
    fade_out_seconds: float
    fader_db: float
    main_db: float


@dataclass(frozen=True)
class BarsEntry:
    ordinal: int
    internal_name: str
    amta_offset: int
    declared_size: int
    data_offset: int
    header_length: int = AMTA_MIN_HEADER
    assets: tuple[str, ...] = ()
    source_asset: str = ""
    marker_names: tuple[str, ...] = ()
    markers: tuple[Marker, ...] = ()
    marker_status: MarkerStatus = MarkerStatus.NONE
    envelope: Envelope | None = None
    names: tuple[str, ...] = ()


def _u32(buf: bytes, off: int) -> int:
    return struct.unpack_from("<I", buf, off)[0]


def _f32(buf: bytes, off: int) -> float:
    return struct.unpack_from("<f", buf, off)[0]


def _decompress(raw: bytes) -> bytes:
    if not raw.startswith(b"\x28\xb5\x2f\xfd"):
        return raw
    try:
        import zstandard
    except ImportError as exc:  # pragma: no cover - environment guard
        raise BarsFormatError("zstandard is required to read .bars.zs") from exc
    try:
        return zstandard.ZstdDecompressor().decompress(
            raw, max_output_size=MAX_DECOMPRESSED
        )
    except Exception as exc:
        raise BarsFormatError(f"zstd decompression failed: {exc}") from exc


def _scan_assets(block: bytes) -> tuple[str, ...]:
    """All prefixed asset names in string-table order, deduplicated.

    Real entries describe a TRANSITION, not a single sound:

        [source asset] [transition label] [target asset] [transition label]

    e.g. one PeachBeach entry holds
        'BGM_Stage_PeachBeach_Outro.yu.48' 'Outro_ToNextPre'
        'BGM_Stage_PeachBeach_Outro06'     'Outro_ToNextPre'

    so eight entries share one source while each targets a different outro.
    """
    names: list[str] = []
    for raw in _NAME_RE.findall(block):
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        if _PREFIXED.match(text) and text not in names:
            names.append(text)
    return tuple(names)


def _scan_names(block: bytes) -> tuple[str, ...]:
    """Recover the entry's primary asset name.

    Two-tier rule, both tiers verified against real archives:
      1. The LAST `BGM_` / `Dm_` / `SE_` prefixed name resolves to a WAV file
         in 650/650 entries, so it is the canonical entry identity.
      2. When no prefixed name exists (15 JukeBox covers such as
         `sIceWorld_OutroFull`), take the first non-transition string with an
         underscore and at least two letters before it.

    Transition labels (`Bridge_ToFull`, `Outro_ToNextPre`, `Pre_Short`) and
    binary noise stubs (`v_89`) are excluded in tier 2.
    """
    assets = _scan_assets(block)
    if assets:
        return (assets[-1],)

    for raw in _NAME_RE.findall(block):
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        if _TRANSITION.match(text) or "_" not in text:
            continue
        head = text.split("_", 1)[0]
        # Binary noise in the same table produces stubs like `v_89`; a real
        # asset always has a multi-letter word before its first underscore.
        if len(head) < 2 or not head[0].isalpha():
            continue
        return (text,)

    return ()


def _scan_markers(block: bytes) -> tuple[str, ...]:
    """Marker names are discoverable; their sample positions are not proven."""
    names: list[str] = []
    for raw in _MARKER_RE.findall(block):
        text = raw.decode("ascii")
        if text not in names:
            names.append(text)
    return tuple(names)


def _read_envelope(block: bytes, header_length: int) -> Envelope | None:
    """Read the four-float envelope located just past the entry header."""
    base = header_length + ENVELOPE_LEAD
    if base + 16 > len(block):
        return None

    fi = _f32(block, base)
    fo = _f32(block, base + 4)
    fader = _f32(block, base + 8)
    main = _f32(block, base + 12)

    for v in (fi, fo, fader, main):
        if not math.isfinite(v):
            return None
    if not (FADE_IN_RANGE[0] <= fi <= FADE_IN_RANGE[1]):
        return None
    if not (FADE_OUT_RANGE[0] <= fo <= FADE_OUT_RANGE[1]):
        return None
    if not (GAIN_RANGE[0] <= fader <= GAIN_RANGE[1]):
        return None
    if not (GAIN_RANGE[0] <= main <= GAIN_RANGE[1]):
        return None

    return Envelope(
        fade_in_seconds=fi,
        fade_out_seconds=fo,
        fader_db=fader,
        main_db=main,
    )


def read_bars(path: str) -> list[BarsEntry]:
    """Read a BARS archive (optionally zstd-compressed). Read-only."""
    raw = Path(path).read_bytes()
    buf = _decompress(raw)

    if len(buf) < 16:
        raise BarsFormatError(f"file too short ({len(buf)} bytes)")
    if buf[:4] != BARS_MAGIC:
        raise BarsFormatError(f"bad magic {buf[:4]!r}, expected {BARS_MAGIC!r}")

    total = _u32(buf, 4)
    count = _u32(buf, 12)

    if total != len(buf):
        raise BarsFormatError(
            f"declared size {total} != actual {len(buf)} (dirty or truncated archive)"
        )
    if count > MAX_ENTRIES:
        raise BarsLimitError(f"entry count {count} exceeds limit {MAX_ENTRIES}")
    if 16 + 8 * count > len(buf):
        raise BarsFormatError("offset table runs past end of data")

    table = 16 + 4 * count
    amta_off = [_u32(buf, table + 8 * i) for i in range(count)]
    data_off = [_u32(buf, table + 8 * i + 4) for i in range(count)]

    for off in amta_off:
        if off + 4 > len(buf):
            raise BarsFormatError(f"AMTA offset {off} past end (size {len(buf)})")
        if buf[off:off + 4] != AMTA_MAGIC:
            raise BarsFormatError(f"no AMTA magic at offset {off}")

    if len(set(amta_off)) != len(amta_off):
        raise BarsFormatError("duplicate AMTA offsets: entries would overlap")

    entries: list[BarsEntry] = []
    for i in range(count):
        off = amta_off[i]
        declared = _u32(buf, off + 8)
        if declared < AMTA_MIN_HEADER or off + declared > len(buf):
            raise BarsFormatError(
                f"entry {i} declared size {declared} does not fit at offset {off}"
            )

        header_length = _u32(buf, off + 16)
        if not (AMTA_MIN_HEADER <= header_length <= AMTA_MAX_HEADER):
            raise BarsFormatError(
                f"entry {i} header length {header_length} out of bounds"
            )
        if header_length > declared:
            raise BarsFormatError(
                f"entry {i} header length {header_length} exceeds block size {declared}"
            )

        block = buf[off:off + declared]
        assets = _scan_assets(block)
        names = _scan_names(block)
        marker_names = _scan_markers(block)

        entries.append(
            BarsEntry(
                ordinal=i,
                internal_name=names[0] if names else "",
                amta_offset=off,
                declared_size=declared,
                data_offset=data_off[i],
                header_length=header_length,
                assets=assets,
                source_asset=assets[0] if assets else "",
                marker_names=marker_names,
                markers=(),
                marker_status=MarkerStatus.UNRESOLVED if marker_names else MarkerStatus.NONE,
                envelope=_read_envelope(block, header_length),
                names=names,
            )
        )

    return entries
