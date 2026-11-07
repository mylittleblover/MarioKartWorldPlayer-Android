"""VER-02 RED tests: BWAV loop points mapped to explicit source coordinates.

The mapping must be explicit and reversible. Frame indices are source-sample
coordinates, never seconds derived by assuming 48 kHz, and never Transport
frames.
"""

from __future__ import annotations

import struct
from pathlib import Path

import pytest

from mkw_tools.assets.sample_map import (
    SampleMapError,
    build_loop_record,
    frames_to_seconds,
    map_loop,
)


def _bwav(path: Path, *, rate: int, channels: int, frames: int,
          loop_start: int | None = None, loop_end: int | None = None) -> Path:
    head = bytearray(160)
    head[0:4] = b"BWAV"
    struct.pack_into("<I", head, 4, 0x0001FEFF)
    struct.pack_into("<H", head, 14, channels)
    struct.pack_into("<I", head, 16, 0x00020001)
    struct.pack_into("<I", head, 20, rate)
    struct.pack_into("<I", head, 24, frames)
    struct.pack_into("<I", head, 28, frames)
    if loop_start is None or loop_end is None:
        struct.pack_into("<I", head, 76, 0xFFFFFFFF)
        struct.pack_into("<I", head, 80, 0)
    else:
        struct.pack_into("<I", head, 76, loop_end)
        struct.pack_into("<I", head, 80, loop_start)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(head) + b"\x00" * 64)
    return path


# ------------------------------------------------------------- conversions

def test_frames_to_seconds_uses_actual_rate(tmp_path: Path) -> None:
    assert frames_to_seconds(48000, 48000) == pytest.approx(1.0)
    assert frames_to_seconds(44100, 44100) == pytest.approx(1.0)


def test_frames_to_seconds_rejects_zero_rate(tmp_path: Path) -> None:
    with pytest.raises(SampleMapError):
        frames_to_seconds(100, 0)


def test_frames_to_seconds_rejects_negative_frames(tmp_path: Path) -> None:
    with pytest.raises(SampleMapError):
        frames_to_seconds(-1, 48000)


# ----------------------------------------------------------------- mapping

def test_loop_maps_to_source_coordinates(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "a.bwav", rate=48000, channels=2,
              frames=3_373_711, loop_start=493_714, loop_end=3_373_711)

    record = map_loop(str(p))

    assert record.has_loop is True
    assert record.loop_start_frame == 493_714
    assert record.loop_end_frame == 3_373_711
    assert record.loop_start_seconds == pytest.approx(493_714 / 48000)
    # The real Coaster loop is 2 879 997 frames = 59.9999375 s, not a round 60 s.
    assert record.loop_length_frames == 3_373_711 - 493_714
    assert record.loop_length_seconds == pytest.approx(2_879_997 / 48000)
    assert record.loop_length_seconds == pytest.approx(60.0, abs=1e-3)


def test_no_loop_maps_to_explicit_absence(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "n.bwav", rate=48000, channels=2, frames=226_286)

    record = map_loop(str(p))

    assert record.has_loop is False
    assert record.loop_start_frame is None
    assert record.loop_start_seconds is None


def test_loop_always_ends_at_file_end(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "e.bwav", rate=48000, channels=2,
              frames=1_000_000, loop_start=250_000, loop_end=1_000_000)

    record = map_loop(str(p))

    assert record.loop_end_frame == record.frame_count


def test_records_are_json_ready(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "j.bwav", rate=48000, channels=2,
              frames=1_000_000, loop_start=250_000, loop_end=1_000_000)

    payload = build_loop_record(str(p))

    assert payload["hasLoop"] is True
    assert payload["sampleRate"] == 48000
    assert payload["frameCount"] == 1_000_000
    assert payload["loopStartFrame"] == 250_000
    assert isinstance(payload["loopStartSeconds"], float)


def test_non_bwav_source_is_rejected(tmp_path: Path) -> None:
    p = tmp_path / "x.wav"
    p.write_bytes(b"RIFF\x00\x00\x00\x00WAVE" + b"\x00" * 40)

    with pytest.raises(SampleMapError):
        map_loop(str(p))


def test_missing_file_raises(tmp_path: Path) -> None:
    with pytest.raises(FileNotFoundError):
        map_loop(str(tmp_path / "nope.bwav"))


# ------------------------------------------------------- boundary behaviour

def test_zero_length_loop_is_reported_as_zero_not_rejected(tmp_path: Path) -> None:
    """A loop starting exactly at EOF yields zero length; that is data, not an error."""
    p = _bwav(tmp_path / "z.bwav", rate=48000, channels=2,
              frames=1000, loop_start=1000, loop_end=1000)

    record = map_loop(str(p))

    assert record.has_loop is True
    assert record.loop_length_seconds == 0.0


def test_loop_start_zero_is_full_file_loop(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "f.bwav", rate=48000, channels=2,
              frames=48000, loop_start=0, loop_end=48000)

    record = map_loop(str(p))

    assert record.loop_start_frame == 0
    assert record.loop_length_seconds == pytest.approx(1.0)


def test_non_48k_rate_converts_correctly(tmp_path: Path) -> None:
    """The mapping must use the file's real rate, not a hard-coded 48 kHz."""
    p = _bwav(tmp_path / "r.bwav", rate=32000, channels=1,
              frames=64000, loop_start=32000, loop_end=64000)

    record = map_loop(str(p))

    assert record.loop_start_seconds == pytest.approx(1.0)
    assert record.loop_length_seconds == pytest.approx(1.0)
