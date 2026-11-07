"""Read BARS marker records: (index, markerId, sourceSamplePosition).

Layout measured and validated on real archives (see
evidence/bootstrap/ver-03-markers.md):

    +20  u32  marker section offset
    at that offset:
         u32  marker count
         then `count` records of 16 bytes:
             +0   u32  index (0..count-1, in order)
             +4   u32  markerId   -- per-record identity, NOT an Outro name code
             +8   u32  sourceSamplePosition (frames, at the source rate)
             +12  u32  zero in every observed record

Validated across all four archives: 262 entries carry a table, 531 records
total, every record has index == k and a zero trailing field. No exceptions.

Two things this module deliberately does NOT claim:

1. `markerId` is not an Outro name. 197 distinct ids appear and only 14 repeat
   across stages; those repeats map to contradictory names. The id identifies
   a record, nothing more.

2. The mapping from a position to `OutroNN` is NOT confirmed. The two available
   analyses disagree on 127 of 165 comparable cues, and both are derived from
   the data rather than declared by the game. Positions are trustworthy;
   names are not.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass

__all__ = ["MarkerRecord", "MarkerTable", "read_marker_table", "MARKER_SECTION_FIELD"]

MARKER_SECTION_FIELD = 20
RECORD_SIZE = 16
MAX_MARKERS = 64


class MarkerTableError(ValueError):
    """The marker section is present but structurally invalid."""


@dataclass(frozen=True)
class MarkerRecord:
    index: int
    marker_id: int
    source_sample_position: int


@dataclass(frozen=True)
class MarkerTable:
    section_offset: int
    records: tuple[MarkerRecord, ...]

    def __len__(self) -> int:
        return len(self.records)


def read_marker_table(block: bytes) -> MarkerTable | None:
    """Parse the marker table of one AMTA block, or None when absent.

    Returns None for entries without a table (most non-music entries and
    entries with no exit cues). Raises when a table is present but corrupt.
    """
    if len(block) < MARKER_SECTION_FIELD + 4:
        return None

    section = struct.unpack_from("<I", block, MARKER_SECTION_FIELD)[0]
    if section == 0:
        return None
    if section + 4 > len(block):
        raise MarkerTableError(
            f"marker section offset {section} past block end {len(block)}"
        )

    count = struct.unpack_from("<I", block, section)[0]
    if count > MAX_MARKERS:
        raise MarkerTableError(f"marker count {count} exceeds limit {MAX_MARKERS}")

    start = section + 4
    end = start + RECORD_SIZE * count
    if end > len(block):
        raise MarkerTableError(
            f"{count} marker records need {end - start} bytes at {start}, "
            f"block is {len(block)}"
        )

    records: list[MarkerRecord] = []
    for k in range(count):
        idx, marker_id, position, trailing = struct.unpack_from(
            "<IIII", block, start + RECORD_SIZE * k
        )
        if idx != k:
            raise MarkerTableError(
                f"marker index {idx} at slot {k}: expected positional order"
            )
        if trailing != 0:
            raise MarkerTableError(
                f"marker {k} trailing field is {trailing}, expected 0"
            )
        records.append(
            MarkerRecord(index=idx, marker_id=marker_id, source_sample_position=position)
        )

    return MarkerTable(section_offset=section, records=tuple(records))
