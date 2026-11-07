"""OST-02 RED tests: bounded, fail-closed BARS container parsing.

Written before the reader exists; the import below is the intended RED signal.
Every fixture is synthetic (see bars_fixture.py) -- no game media is used.

Honest scope of this task
-------------------------
The BARS *container* (header, hash table, offset table, AMTA block framing) is
fully understood and verified against real archives. The AMTA *interior*
(marker -> sample position record layout) is only partially reverse-engineered,
so this reader reports it as UNRESOLVED instead of inventing values.

The AMTA envelope block (+56..+72, four float32) IS structurally stable across
all 540 entries of M_Bgm, so those values are exposed -- but only as L1
metadata inference, never as verified playback behaviour.
"""

from __future__ import annotations

import struct
from pathlib import Path

import pytest

from mkw_tools.bars.reader import (
    BarsEntry,
    BarsFormatError,
    BarsLimitError,
    MarkerStatus,
    read_bars,
)
from tests.ost_inventory.bars_fixture import Archive, Entry, build, build_simple, make_amta


# ---------------------------------------------------------------- happy path

def test_reads_entry_count_and_names(tmp_path: Path) -> None:
    p = tmp_path / "ok.bars"
    p.write_bytes(build_simple(["BGM_Stage_Alpha", "BGM_Stage_Beta", "BGM_Rainbow_Area1"]))

    entries = read_bars(str(p))

    assert len(entries) == 3
    assert all(isinstance(e, BarsEntry) for e in entries)
    assert [e.internal_name for e in entries] == [
        "BGM_Stage_Alpha", "BGM_Stage_Beta", "BGM_Rainbow_Area1"
    ]


def test_entry_ordinals_are_contiguous(tmp_path: Path) -> None:
    p = tmp_path / "ord.bars"
    p.write_bytes(build_simple([f"BGM_Stage_N{i}" for i in range(5)]))

    assert [e.ordinal for e in read_bars(str(p))] == [0, 1, 2, 3, 4]


def test_offsets_are_recorded(tmp_path: Path) -> None:
    p = tmp_path / "off.bars"
    p.write_bytes(build_simple(["BGM_Stage_Alpha"]))

    e = read_bars(str(p))[0]
    assert e.amta_offset > 0
    assert e.declared_size > 0


def test_marker_names_reported_but_positions_unresolved(tmp_path: Path) -> None:
    """Marker NAMES are discoverable; their sample positions are NOT claimed."""
    p = tmp_path / "mk.bars"
    p.write_bytes(build_simple(["BGM_Stage_Alpha"], markers_per_entry=3))

    entry = read_bars(str(p))[0]

    assert entry.marker_names == ("Full_ToOutro00", "Full_ToOutro01", "Full_ToOutro02")
    assert entry.markers == ()
    assert entry.marker_status is MarkerStatus.UNRESOLVED


def test_bytes_are_not_modified(tmp_path: Path) -> None:
    p = tmp_path / "ro.bars"
    p.write_bytes(build_simple(["BGM_Stage_Alpha"]))
    before = p.read_bytes()

    read_bars(str(p))

    assert p.read_bytes() == before


# ------------------------------------------------------------------- fades

def test_fade_envelope_parsed_when_present(tmp_path: Path) -> None:
    """+60 fadeIn, +64 fadeOut, +68 fader dB, +72 main dB."""
    p = tmp_path / "env.bars"
    p.write_bytes(build_simple(["BGM_Stage_Alpha"], fade=(0.15, 0.04, -23.5, -27.0)))

    env = read_bars(str(p))[0].envelope

    assert env is not None
    assert env.fade_in_seconds == pytest.approx(0.15, abs=1e-4)
    assert env.fade_out_seconds == pytest.approx(0.04, abs=1e-4)
    assert env.fader_db == pytest.approx(-23.5, abs=1e-4)
    assert env.main_db == pytest.approx(-27.0, abs=1e-4)


def test_zero_fade_envelope_is_rejected_as_implausible(tmp_path: Path) -> None:
    """A zero/negative fade is structurally impossible in real archives."""
    p = tmp_path / "zero.bars"
    p.write_bytes(build_simple(["BGM_Stage_Alpha"], fade=(0.0, 0.0, 0.0, 0.0)))

    assert read_bars(str(p))[0].envelope is None


def test_nan_fade_envelope_is_rejected(tmp_path: Path) -> None:
    p = tmp_path / "nan.bars"
    p.write_bytes(build_simple(["BGM_Stage_Alpha"], fade=(float("nan"), 0.04, -23.5, -27.0)))

    assert read_bars(str(p))[0].envelope is None


# --------------------------------------------------------- rejection cases

def test_bad_magic_rejected(tmp_path: Path) -> None:
    p = tmp_path / "bad.bars"
    p.write_bytes(b"XXXX" + b"\x00" * 60)

    with pytest.raises(BarsFormatError):
        read_bars(str(p))


def test_truncated_file_rejected(tmp_path: Path) -> None:
    p = tmp_path / "trunc.bars"
    p.write_bytes(build_simple(["BGM_Stage_Alpha"])[:12])

    with pytest.raises(BarsFormatError):
        read_bars(str(p))


def test_declared_size_mismatch_rejected(tmp_path: Path) -> None:
    payload = bytearray(build_simple(["BGM_Stage_Alpha"]))
    struct.pack_into("<I", payload, 4, len(payload) + 4096)
    p = tmp_path / "badsize.bars"
    p.write_bytes(bytes(payload))

    with pytest.raises(BarsFormatError):
        read_bars(str(p))


def test_offset_past_end_rejected(tmp_path: Path) -> None:
    payload = bytearray(build_simple(["BGM_Stage_Alpha"]))
    count = struct.unpack_from("<I", payload, 12)[0]
    table = 16 + 4 * count
    struct.pack_into("<I", payload, table, len(payload) + 999999)
    p = tmp_path / "badoff.bars"
    p.write_bytes(bytes(payload))

    with pytest.raises(BarsFormatError):
        read_bars(str(p))


def test_overlapping_offsets_rejected(tmp_path: Path) -> None:
    payload = bytearray(build_simple(["BGM_Stage_Alpha", "BGM_Stage_Beta"]))
    count = struct.unpack_from("<I", payload, 12)[0]
    table = 16 + 4 * count
    first = struct.unpack_from("<I", payload, table)[0]
    struct.pack_into("<I", payload, table + 8, first)
    p = tmp_path / "overlap.bars"
    p.write_bytes(bytes(payload))

    with pytest.raises(BarsFormatError):
        read_bars(str(p))


def test_amta_magic_missing_rejected(tmp_path: Path) -> None:
    payload = bytearray(build_simple(["BGM_Stage_Alpha"]))
    count = struct.unpack_from("<I", payload, 12)[0]
    table = 16 + 4 * count
    amta_off = struct.unpack_from("<I", payload, table)[0]
    payload[amta_off:amta_off + 4] = b"ZZZZ"
    p = tmp_path / "noamta.bars"
    p.write_bytes(bytes(payload))

    with pytest.raises(BarsFormatError):
        read_bars(str(p))


def test_huge_entry_count_rejected(tmp_path: Path) -> None:
    payload = bytearray(build_simple(["BGM_Stage_Alpha"]))
    struct.pack_into("<I", payload, 12, 0xFFFFFF)
    p = tmp_path / "huge.bars"
    p.write_bytes(bytes(payload))

    with pytest.raises(BarsLimitError):
        read_bars(str(p))


def test_entry_declared_size_overflow_rejected(tmp_path: Path) -> None:
    bad = make_amta(size_override=10_000_000, name="BGM_Stage_Alpha")
    p = tmp_path / "overflow.bars"
    p.write_bytes(build(Archive(entries=[Entry(amta=bad)])))

    with pytest.raises(BarsFormatError):
        read_bars(str(p))


def test_empty_archive_yields_no_entries(tmp_path: Path) -> None:
    p = tmp_path / "empty.bars"
    p.write_bytes(build(Archive(entries=[])))

    assert read_bars(str(p)) == []


def test_missing_file_raises(tmp_path: Path) -> None:
    with pytest.raises(FileNotFoundError):
        read_bars(str(tmp_path / "nope.bars"))


def test_corrupt_zstd_rejected(tmp_path: Path) -> None:
    p = tmp_path / "corrupt.bars.zs"
    p.write_bytes(b"\x28\xb5\x2f\xfd" + b"\x00\x01\x02\x03" * 8)

    with pytest.raises(BarsFormatError):
        read_bars(str(p))
