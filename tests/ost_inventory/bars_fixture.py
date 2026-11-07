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
    header_length: int = 56,
) -> bytes:
    """Build an AMTA block: header + envelope + opaque body + string table.

    `fade` = (fadeInSec, fadeOutSec, faderDb, mainDb), placed at
    `header_length + 4`. Real archives use headerLength 56 or 64.
    """
    strings = [name, *marker_names]

    if header_length not in (56, 64):
        raise ValueError("fixture supports header_length 56 or 64")
    envelope_area = 4 + 16 + 4  # lead tag + 4 floats + trailing pad
    body = b"\x00" * pad
    table = b"".join(s.encode("utf-8") + b"\x00" for s in strings)

    total = header_length + envelope_area + len(body) + len(table)
    real = total if size_override is None else size_override

    # Magic is raw bytes; the remaining (header_length - 4) bytes are u32s.
    fields: list[int] = [
        AMTA_VERSION_TAG,   # +4
        real,               # +8  declared block size
        0,                  # +12
        header_length,      # +16 fixed header length
        0,                  # +20
        0,                  # +24
        0,                  # +28
        0,                  # +32
        0,                  # +36
        0,                  # +40 content hash
        7,                  # +44
        0x0201,             # +48
        0x0101,             # +52
    ]
    while len(fields) < (header_length - 4) // 4:
        fields.append(0)
    header = AMTA_MAGIC + struct.pack(f"<{len(fields)}I", *fields)
    assert len(header) == header_length, (len(header), header_length)

    if fade is None:
        envelope = b"\x00" * envelope_area
    else:
        fi, fo, fader_db, main_db = fade
        envelope = (
            struct.pack("<I", 111)
            + struct.pack("<ffff", fi, fo, fader_db, main_db)
            + b"\x00" * 4
        )
        assert len(envelope) == envelope_area

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
    header_length: int = 56,
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
                    header_length=header_length,
                )
            )
        )
    return build(Archive(entries=entries))
