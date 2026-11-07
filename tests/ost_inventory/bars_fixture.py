"""Writes a minimal, well-formed BARS archive for regression tests.

This builder is deliberately independent of the reader under test: it encodes
the container layout from first principles so a reader bug cannot make the
fixture agree with itself.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass, field

BARS_MAGIC = b"BARS"
AMTA_MAGIC = b"AMTA"
BARS_VERSION_TAG = 0x0102FEFF
AMTA_VERSION_TAG = 0x0500FEFF


@dataclass
class Entry:
    amta: bytes
    data: bytes = b""
    digest: int = 0


@dataclass
class Archive:
    entries: list[Entry] = field(default_factory=list)


def make_amta(
    *,
    size_override: int | None = None,
    name: str = "BGM_Test_Entry",
    marker_names: tuple[str, ...] = (),
    marker_positions: tuple[int, ...] = (),
    pad: int = 16,
    fade: tuple[float, float, float, float] | None = None,
) -> bytes:
    """Build an AMTA block: header + envelope + opaque body + string table.

    `fade` = (fadeInSec, fadeOutSec, faderDb, mainDb). When omitted the envelope
    area is zeroed, which the reader must treat as "implausible / absent".
    """
    strings = [name, *marker_names]

    header_len = 56
    fade_len = 24
    body = b"\x00" * pad
    table = b"".join(s.encode("utf-8") + b"\x00" for s in strings)

    total = header_len + fade_len + len(body) + len(table)
    real = total if size_override is None else size_override

    # Magic is raw bytes; the remaining 52 bytes are thirteen little-endian u32.
    header = AMTA_MAGIC + struct.pack(
        "<IIIIIIIIIIIII",
        AMTA_VERSION_TAG,   # +4
        real,               # +8  declared block size
        0,                  # +12
        header_len,         # +16 fixed header length (56)
        0,                  # +20
        0,                  # +24
        0,                  # +28
        0,                  # +32
        0,                  # +36
        0,                  # +40 content hash
        7,                  # +44
        0x0201,             # +48
        0x0101,             # +52
    )
    assert len(header) == header_len, len(header)

    if fade is None:
        envelope = b"\x00" * fade_len
    else:
        fi, fo, fader_db, main_db = fade
        envelope = struct.pack("<ffff", fi, fo, fader_db, main_db) + b"\x00" * (fade_len - 16)
        # +56 is a leading tag; keep it as in real archives.
        envelope = struct.pack("<I", 111) + struct.pack("<ffff", fi, fo, fader_db, main_db) + b"\x00" * 4

    return header + envelope + body + table


def build(archive: Archive) -> bytes:
    count = len(archive.entries)

    # Real layout (verified against MKW archives):
    #   +0  magic, +4 total size, +8 version tag, +12 entry count
    #   +16 u32[count] content hashes
    #   +16+4*count  interleaved (u32 amtaOffset, u32 dataOffset)[count]
    header_len = 16 + 4 * count
    table_len = 8 * count

    # Two-pass: sizes are known, offsets computed before encoding.
    body_len = sum(len(e.amta) + len(e.data) for e in archive.entries)
    total = header_len + table_len + body_len

    cursor = header_len + table_len
    placements = []
    for e in archive.entries:
        amta_off = cursor
        cursor += len(e.amta)
        data_off = cursor if e.data else 0
        cursor += len(e.data)
        placements.append((amta_off, data_off, e))

    out = bytearray()
    out += BARS_MAGIC
    out += struct.pack("<I", total)
    out += struct.pack("<I", BARS_VERSION_TAG)
    out += struct.pack("<I", count)
    for _, _, e in placements:
        out += struct.pack("<I", e.digest)
    for amta_off, data_off, _ in placements:
        out += struct.pack("<II", amta_off, data_off)
    for _, _, e in placements:
        out += e.amta
        out += e.data

    assert len(out) == total, (len(out), total)
    return bytes(out)


def build_simple(
    names: list[str],
    *,
    markers_per_entry: int = 0,
    fade: tuple[float, float, float, float] | None = None,
) -> bytes:
    entries = []
    for name in names:
        mk = tuple(f"Full_ToOutro{j:02d}" for j in range(markers_per_entry))
        pos = tuple(1000 * (j + 1) for j in range(markers_per_entry))
        entries.append(
            Entry(
                amta=make_amta(
                    name=name,
                    marker_names=mk,
                    marker_positions=pos,
                    fade=fade,
                )
            )
        )
    return build(Archive(entries=entries))
