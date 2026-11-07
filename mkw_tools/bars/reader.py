"""Bounded reader for Nintendo BARS sound archives.

Verified container layout (measured against real MKW archives; see
evidence/bootstrap/bars-format.md):

    +0   "BARS"
    +4   u32 total decompressed size
    +8   u32 version tag (0x0102FEFF)
    +12  u32 entry count
    +16  u32[count]                per-entry content hash
    +16+4*count  (u32 amta, u32 data)[count]   interleaved offset pairs
    ...  AMTA blocks

Every AMTA block starts with "AMTA" + 0x0500FEFF, carries its declared size at
+8 and a fixed 0x38 byte header, then:

    +56  u32   leading tag (observed 0x51..0x6F, entry-dependent)
    +60  f32   fade-in  seconds   (range 0.082..0.270 across 540 entries)
    +64  f32   fade-out seconds   (range 0.013..0.065)
    +68  f32   fader gain dB      (range -38.3..-20.8)
    +72  f32   main gain dB       (range -40.4..-22.9)

The four-float envelope at +60..+72 is structurally stable across every entry
sampled, so it is exposed as **L1 metadata inference**. It is NOT verified
playback behaviour and must not be presented as one.

What this module deliberately does NOT do
-----------------------------------------
The interior record layout binding marker NAMES to sample positions is not yet
proven. Earlier research produced per-marker numbers, but the 12-byte stride
found in the Coaster block does not reproduce those marker IDs, so those values
are treated as UNVERIFIED here. We expose marker NAMES with an explicit
MarkerStatus.UNRESOLVED rather than emitting positions we cannot defend.
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

AMTA_FIXED_HEADER = 0x38
ENVELOPE_OFFSET = 56
ENVELOPE_MIN_SIZE = 80

# Plausibility windows derived from every entry of every real archive.
FADE_IN_RANGE = (0.01, 5.0)
FADE_OUT_RANGE = (0.001, 5.0)
GAIN_RANGE = (-96.0, 12.0)

MAX_ENTRIES = 1 << 20
MAX_DECOMPRESSED = 512 * 1024 * 1024

# Asset names are always BGM_-prefixed and NUL-terminated in the string table.
_NAME_RE = re.compile(rb"(BGM_[A-Za-z0-9_.]+)\x00")
# Marker names are NUL-terminated but not BGM_-prefixed.
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

    These are per-entry fade and gain parameters. Their exact runtime semantics
    (which Bus/Effect they apply to, whether fader_db is a mix-bus trim) is
    UNVERIFIED and must be confirmed against behaviour evidence before use.
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


def _scan_names(block: bytes) -> tuple[str, ...]:
    """Recover NUL-terminated asset names from the AMTA string table.

    Only `BGM_`-prefixed, NUL-terminated strings are accepted. A blind
    printable-run scan over-approximates badly (386/540 garbage names on real
    archives), while this anchored form resolves 610/650 entries exactly.
    """
    hits = _NAME_RE.findall(block)
    names: list[str] = []
    for raw in hits:
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        if text not in names:
            names.append(text)
    return tuple(names)


def _scan_markers(block: bytes) -> tuple[str, ...]:
    """Marker names are discoverable; their sample positions are not proven."""
    names: list[str] = []
    for raw in _MARKER_RE.findall(block):
        text = raw.decode("ascii")
        if text not in names:
            names.append(text)
    return tuple(names)


def _read_envelope(block: bytes) -> Envelope | None:
    """Read the +60..+72 float block, or return None when implausible."""
    if len(block) < ENVELOPE_OFFSET + 20:
        return None
    fi = _f32(block, 60)
    fo = _f32(block, 64)
    fader = _f32(block, 68)
    main = _f32(block, 72)

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
        if declared < AMTA_FIXED_HEADER or off + declared > len(buf):
            raise BarsFormatError(
                f"entry {i} declared size {declared} does not fit at offset {off}"
            )

        block = buf[off:off + declared]
        names = _scan_names(block)
        marker_names = _scan_markers(block)
        internal = names[0] if names else ""

        entries.append(
            BarsEntry(
                ordinal=i,
                internal_name=internal,
                amta_offset=off,
                declared_size=declared,
                data_offset=data_off[i],
                marker_names=marker_names,
                markers=(),
                marker_status=MarkerStatus.UNRESOLVED if marker_names else MarkerStatus.NONE,
                envelope=_read_envelope(block),
                names=names,
            )
        )

    return entries
