"""VER-03 RED tests: BARS marker records carry id + source sample position.

Records bind a globally unique marker id to an exact source sample position.
The position is trustworthy; the id is identity, not an Outro name.
"""

from __future__ import annotations

import struct
from pathlib import Path

import pytest

from mkw_tools.bars.markers import (
    MarkerTableError,
    read_marker_table,
)


def _block(*, section: int, count: int, records: list[tuple[int, int, int, int]],
           size: int | None = None) -> bytes:
    total = size if size is not None else 2048
    buf = bytearray(total)
    struct.pack_into("<I", buf, 0, 0x41544D41)
    struct.pack_into("<I", buf, 20, section)
    if section + 4 <= total:
        struct.pack_into("<I", buf, section, count)
        for k, (idx, mid, pos, trailing) in enumerate(records):
            off = section + 4 + 16 * k
            if off + 16 > total:
                break
            struct.pack_into("<IIII", buf, off, idx, mid, pos, trailing)
    return bytes(buf)


# ------------------------------------------------------------------- parsing

def test_reads_marker_records(tmp_path: Path) -> None:
    block = _block(
        section=100,
        count=3,
        records=[(0, 2005, 637714, 0), (1, 2004, 884571, 0), (2, 2003, 1460570, 0)],
    )

    table = read_marker_table(block)

    assert table is not None
    assert len(table) == 3
    assert table.records[0].marker_id == 2005
    assert table.records[0].source_sample_position == 637714
    assert [r.index for r in table.records] == [0, 1, 2]


def test_positions_are_frame_indices_not_seconds(tmp_path: Path) -> None:
    block = _block(section=64, count=1, records=[(0, 2005, 637714, 0)])

    record = read_marker_table(block).records[0]

    assert record.source_sample_position == 637714
    assert record.source_sample_position != 13


def test_no_marker_section_returns_none(tmp_path: Path) -> None:
    block = bytearray(512)
    struct.pack_into("<I", block, 0, 0x41544D41)
    struct.pack_into("<I", block, 20, 0)

    assert read_marker_table(bytes(block)) is None


def test_zero_count_returns_empty_table(tmp_path: Path) -> None:
    block = _block(section=64, count=0, records=[])

    table = read_marker_table(block)

    assert table is not None
    assert len(table) == 0


def test_section_offset_recorded(tmp_path: Path) -> None:
    block = _block(section=300, count=1, records=[(0, 9, 100, 0)])

    assert read_marker_table(block).section_offset == 300


# ------------------------------------------------------------- rejectations

def test_section_offset_past_block_rejected(tmp_path: Path) -> None:
    block = _block(section=9999, count=1, records=[(0, 1, 1, 0)], size=256)

    with pytest.raises(MarkerTableError):
        read_marker_table(block)


def test_count_beyond_limit_rejected(tmp_path: Path) -> None:
    block = _block(section=64, count=5000, records=[])

    with pytest.raises(MarkerTableError):
        read_marker_table(block)


def test_records_running_past_block_rejected(tmp_path: Path) -> None:
    buf = bytearray(120)
    struct.pack_into("<I", buf, 20, 64)
    struct.pack_into("<I", buf, 64, 20)  # claims 20 records in a 120-byte block

    with pytest.raises(MarkerTableError):
        read_marker_table(bytes(buf))


def test_non_positional_index_rejected(tmp_path: Path) -> None:
    """Every observed record has index == slot. Out-of-order means corruption."""
    block = _block(section=64, count=2, records=[(0, 1, 10, 0), (7, 2, 20, 0)])

    with pytest.raises(MarkerTableError):
        read_marker_table(block)


def test_nonzero_trailing_field_rejected(tmp_path: Path) -> None:
    """The fourth u32 is zero in every one of the 482 observed records."""
    block = _block(section=64, count=1, records=[(0, 1, 10, 12345)])

    with pytest.raises(MarkerTableError):
        read_marker_table(block)


def test_block_too_short_for_section_field(tmp_path: Path) -> None:
    assert read_marker_table(b"\x00" * 8) is None
