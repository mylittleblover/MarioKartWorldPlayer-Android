"""VER-02 RED tests: BWAV loop points read from the original container.

The loop fields are real bytes in the file, not a guess: every one of the
20991 local files either satisfies `0 <= start <= end == frameCount` or uses
the (0xFFFFFFFF, 0) no-loop sentinel.
"""

from __future__ import annotations

import struct
from pathlib import Path

import pytest

from mkw_tools.assets.audio_probe import AudioFormat, ProbeError, probe_audio


def _bwav(
    path: Path,
    *,
    rate: int = 48000,
    channels: int = 2,
    frames: int = 1_000_000,
    loop_start: int | None = None,
    loop_end: int | None = None,
) -> Path:
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


# ------------------------------------------------------------- loop reading

def test_loop_points_are_read(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "a.bwav", frames=3_373_711, loop_start=493_714, loop_end=3_373_711)

    probe = probe_audio(str(p))

    assert probe.format is AudioFormat.BWAV
    assert probe.loop_start_frame == 493_714
    assert probe.loop_end_frame == 3_373_711


def test_no_loop_sentinel_yields_none(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "n.bwav", frames=226_286)

    probe = probe_audio(str(p))

    assert probe.loop_start_frame is None
    assert probe.loop_end_frame is None


def test_loop_end_always_equals_frame_count(tmp_path: Path) -> None:
    """In all 174 real loop-bearing music files, loopEnd == frameCount."""
    p = _bwav(tmp_path / "b.bwav", frames=2_427_426, loop_start=452_571, loop_end=2_427_426)

    probe = probe_audio(str(p))

    assert probe.loop_end_frame == probe.frame_count


def test_zero_length_loop_is_accepted_as_instant(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "z.bwav", frames=1000, loop_start=1000, loop_end=1000)

    probe = probe_audio(str(p))

    assert probe.loop_start_frame == 1000
    assert probe.loop_end_frame == 1000


def test_loop_start_zero_is_valid(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "s.bwav", frames=1000, loop_start=0, loop_end=1000)

    probe = probe_audio(str(p))

    assert probe.loop_start_frame == 0


# ------------------------------------------------------- consistency checks

def test_loop_end_not_matching_frames_is_rejected(tmp_path: Path) -> None:
    """A partial loop would contradict every observed file; treat as corrupt."""
    p = _bwav(tmp_path / "bad.bwav", frames=1000, loop_start=100, loop_end=900)

    with pytest.raises(ProbeError):
        probe_audio(str(p))


def test_loop_start_after_end_is_rejected(tmp_path: Path) -> None:
    head = bytearray(160)
    head[0:4] = b"BWAV"
    struct.pack_into("<I", head, 4, 0x0001FEFF)
    struct.pack_into("<H", head, 14, 2)
    struct.pack_into("<I", head, 20, 48000)
    struct.pack_into("<I", head, 24, 1000)
    struct.pack_into("<I", head, 28, 1000)
    struct.pack_into("<I", head, 76, 500)   # loopEnd
    struct.pack_into("<I", head, 80, 900)   # loopStart > end
    p = tmp_path / "rev.bwav"
    p.write_bytes(bytes(head) + b"\x00" * 64)

    with pytest.raises(ProbeError):
        probe_audio(str(p))


def test_no_loop_sentinel_with_nonzero_start_is_rejected(tmp_path: Path) -> None:
    head = bytearray(160)
    head[0:4] = b"BWAV"
    struct.pack_into("<I", head, 4, 0x0001FEFF)
    struct.pack_into("<H", head, 14, 2)
    struct.pack_into("<I", head, 20, 48000)
    struct.pack_into("<I", head, 24, 1000)
    struct.pack_into("<I", head, 28, 1000)
    struct.pack_into("<I", head, 76, 0xFFFFFFFF)
    struct.pack_into("<I", head, 80, 4096)  # contradictory
    p = tmp_path / "contra.bwav"
    p.write_bytes(bytes(head) + b"\x00" * 64)

    with pytest.raises(ProbeError):
        probe_audio(str(p))


def test_short_bwav_is_rejected(tmp_path: Path) -> None:
    p = tmp_path / "short.bwav"
    p.write_bytes(b"BWAV" + b"\x00" * 40)

    with pytest.raises(ProbeError):
        probe_audio(str(p))
