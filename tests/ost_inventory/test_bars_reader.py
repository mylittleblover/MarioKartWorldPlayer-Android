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


def test_envelope_follows_header_length_64(tmp_path: Path) -> None:
    """24 real BgmAll entries use headerLength=64; the envelope shifts with it."""
    p = tmp_path / "hdr64.bars"
    p.write_bytes(
        build_simple(["BGM_Seq_Alpha"], fade=(0.15, 0.04, -23.5, -27.0), header_length=64)
    )

    entry = read_bars(str(p))[0]

    assert entry.header_length == 64
    assert entry.envelope is not None
    assert entry.envelope.fade_in_seconds == pytest.approx(0.15, abs=1e-4)
    assert entry.envelope.fade_out_seconds == pytest.approx(0.04, abs=1e-4)


def test_header_length_recorded_per_entry(tmp_path: Path) -> None:
    p = tmp_path / "hdr.bars"
    p.write_bytes(
        build(
            Archive(
                entries=[
                    Entry(amta=make_amta(name="BGM_A_B", fade=(0.15, 0.04, -23.5, -27.0))),
                    Entry(
                        amta=make_amta(
                            name="BGM_C_D",
                            fade=(0.15, 0.04, -23.5, -27.0),
                            header_length=64,
                        )
                    ),
                ]
            )
        )
    )

    entries = read_bars(str(p))

    assert [e.header_length for e in entries] == [56, 64]
    assert all(e.envelope is not None for e in entries)


def test_same_name_on_two_entries_stay_distinct(tmp_path: Path) -> None:
    """Real archives repeat one asset name across distinct entries."""
    p = tmp_path / "dup.bars"
    p.write_bytes(
        build(
            Archive(
                entries=[
                    Entry(amta=make_amta(name="BGM_Stage_Same", fade=(0.15, 0.04, -20.0, -27.0))),
                    Entry(amta=make_amta(name="BGM_Stage_Same", fade=(0.20, 0.05, -30.0, -33.0))),
                ]
            )
        )
    )

    entries = read_bars(str(p))

    assert len(entries) == 2
    assert entries[0].internal_name == entries[1].internal_name
    assert entries[0].ordinal != entries[1].ordinal
    assert entries[0].amta_offset != entries[1].amta_offset
    assert entries[0].envelope.fader_db != entries[1].envelope.fader_db


def test_dm_and_se_name_families_are_recognised(tmp_path: Path) -> None:
    """BgmAll carries Dm_ (cutscene) and SE_ (sound effect) assets, not just BGM_."""
    p = tmp_path / "fam.bars"
    p.write_bytes(build_simple(["Dm_SV01_OP", "SE_UI_SpecialCupOpen", "BGM_Seq_Title"]))

    names = [e.internal_name for e in read_bars(str(p))]

    assert names == ["Dm_SV01_OP", "SE_UI_SpecialCupOpen", "BGM_Seq_Title"]


def test_prefixed_name_wins_over_earlier_noise(tmp_path: Path) -> None:
    """A BGM_/Dm_/SE_ name is preferred even if binary noise precedes it."""
    p = tmp_path / "noise.bars"
    payload = bytearray(build_simple(["__placeholder__"]))
    count = struct.unpack_from("<I", payload, 12)[0]
    table = 16 + 4 * count
    amta_off = struct.unpack_from("<I", payload, table)[0]
    # Overwrite the fixture name with noise + a prefixed name.
    name_start = amta_off + 80
    noise = b"zz9_qq\x00BGM_Stage_Real\x00"
    payload[name_start:name_start + len(noise)] = noise
    p.write_bytes(bytes(payload))

    assert read_bars(str(p))[0].internal_name == "BGM_Stage_Real"


def test_fallback_name_used_when_no_prefix_present(tmp_path: Path) -> None:
    """15 real JukeBox covers expose only e.g. `sIceWorld_OutroFull`."""
    p = tmp_path / "fallback.bars"
    p.write_bytes(build_simple(["sIceWorld_OutroFull"]))

    assert read_bars(str(p))[0].internal_name == "sIceWorld_OutroFull"


def test_transition_labels_are_not_mistaken_for_assets(tmp_path: Path) -> None:
    """`Bridge_ToFull` / `Outro_ToNextPre` are transition labels, not assets."""
    p = tmp_path / "trans.bars"
    p.write_bytes(
        build(
            Archive(
                entries=[
                    Entry(amta=make_amta(
                        name="Bridge_ToFull",
                        fade=(0.15, 0.04, -23.5, -27.0),
                    ))
                ]
            )
        )
    )

    assert read_bars(str(p))[0].internal_name == ""


def test_apostrophe_in_asset_name_is_preserved(tmp_path: Path) -> None:
    """Nine real JukeBox covers contain apostrophes, e.g. Rosalina'sIceWorld."""
    name = "BGM_JukeBox_M488_MK7_Rosalina'sIceWorld_OutroFull"
    p = tmp_path / "apos.bars"
    p.write_bytes(build_simple([name], fade=(0.15, 0.04, -23.5, -27.0)))

    assert read_bars(str(p))[0].internal_name == name


def test_transition_entry_exposes_source_and_target(tmp_path: Path) -> None:
    """An entry is a transition: source asset, label, target asset, label."""
    p = tmp_path / "pair.bars"
    block = make_amta(name="BGM_Stage_PeachBeach_Outro.yu.48", fade=(0.15, 0.04, -23.5, -27.0))
    # Append the target asset + its label, mirroring the real string table.
    block = block[: len(block)] + b"Outro_ToNextPre\x00BGM_Stage_PeachBeach_Outro06\x00Outro_ToNextPre\x00"
    # Patch the declared size so the reader accepts the longer block.
    size = len(block)
    block = block[:8] + struct.pack("<I", size) + block[12:]
    p.write_bytes(build(Archive(entries=[Entry(amta=block)])))

    entry = read_bars(str(p))[0]

    assert entry.source_asset == "BGM_Stage_PeachBeach_Outro.yu.48"
    assert entry.internal_name == "BGM_Stage_PeachBeach_Outro06"
    assert entry.assets == (
        "BGM_Stage_PeachBeach_Outro.yu.48",
        "BGM_Stage_PeachBeach_Outro06",
    )


def test_shared_source_across_entries_is_normal(tmp_path: Path) -> None:
    """Eight real entries share one source and each target a different outro."""
    outs = ["00", "01", "02"]
    entries = []
    for o in outs:
        block = make_amta(name="BGM_Stage_PeachBeach_Outro.yu.48", fade=(0.15, 0.04, -23.5, -27.0))
        block = block + b"Outro_ToNextPre\x00" + f"BGM_Stage_PeachBeach_Outro{o}\x00Outro_ToNextPre\x00".encode()
        size = len(block)
        block = block[:8] + struct.pack("<I", size) + block[12:]
        entries.append(Entry(amta=block))

    p = tmp_path / "shared.bars"
    p.write_bytes(build(Archive(entries=entries)))

    got = read_bars(str(p))

    assert {e.source_asset for e in got} == {"BGM_Stage_PeachBeach_Outro.yu.48"}
    assert [e.internal_name for e in got] == [
        f"BGM_Stage_PeachBeach_Outro{o}" for o in outs
    ]
    assert len({e.internal_name for e in got}) == 3


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
