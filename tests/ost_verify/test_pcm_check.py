"""VER-01 RED tests: verify decoded PCM integrity without trusting headers.

The point is to catch lying containers: a header claiming N frames while the
payload holds fewer, silent padding passed off as audio, NaN/inf in float
streams, and channel layouts that do not match the declared channel count.
"""

from __future__ import annotations

import struct
from pathlib import Path

import pytest

from mkw_tools.assets.pcm_check import (
    PcmIntegrity,
    PcmStatus,
    check_pcm,
)


def _wave(path: Path, *, rate: int = 48000, channels: int = 2, bits: int = 16,
          samples: list[list[int]] | None = None, declare_frames: int | None = None) -> Path:
    """Write a WAVE file. `samples[frame][channel]` are integers."""
    samples = samples or [[1] * channels for _ in range(100)]
    width = bits // 8
    payload = bytearray()
    for frame in samples:
        for value in frame:
            if bits == 16:
                payload += struct.pack("<h", value)
            elif bits == 8:
                payload += struct.pack("<B", value)
            elif bits == 32:
                payload += struct.pack("<i", value)

    declared = len(samples) if declare_frames is None else declare_frames
    data_size = declared * channels * width

    fmt = struct.pack("<HHIIHH", 1, channels, rate, rate * channels * width,
                      channels * width, bits)
    body = (
        b"WAVE"
        + b"fmt " + struct.pack("<I", len(fmt)) + fmt
        + b"data" + struct.pack("<I", data_size) + bytes(payload)
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)
    return path


# ------------------------------------------------------------------ healthy

def test_healthy_pcm_reports_ok(tmp_path: Path) -> None:
    p = _wave(tmp_path / "ok.wav", channels=2, samples=[[100, -100]] * 240)

    result = check_pcm(str(p))

    assert result.status is PcmStatus.OK
    assert result.frames_decoded == 240
    assert result.channels == 2
    assert 0.0 < result.peak < 1.0


def test_peak_reflects_actual_samples(tmp_path: Path) -> None:
    p = _wave(tmp_path / "loud.wav", channels=1, samples=[[32767], [-32768]] * 10)

    result = check_pcm(str(p))

    assert result.peak > 0.99


def test_silence_is_detected_not_hidden(tmp_path: Path) -> None:
    """All-zero audio is a real diagnosis, not a pass."""
    p = _wave(tmp_path / "silent.wav", channels=1, samples=[[0]] * 100)

    result = check_pcm(str(p))

    assert result.status is PcmStatus.SILENT
    assert result.peak == 0.0


def test_silence_is_not_reported_as_failure(tmp_path: Path) -> None:
    p = _wave(tmp_path / "silent2.wav", channels=1, samples=[[0]] * 50)

    assert check_pcm(str(p)).status is not PcmStatus.TRUNCATED


# ---------------------------------------------------------------- truncated

def test_declared_more_frames_than_payload_is_truncated(tmp_path: Path) -> None:
    """Header claims 1000 frames but only 100 are present."""
    p = _wave(tmp_path / "trunc.wav", channels=1, samples=[[5]] * 100, declare_frames=1000)

    result = check_pcm(str(p))

    assert result.status is PcmStatus.TRUNCATED
    assert result.frames_decoded < 1000


def test_data_chunk_larger_than_file_is_truncated(tmp_path: Path) -> None:
    p = _wave(tmp_path / "over.wav", channels=1, samples=[[5]] * 10, declare_frames=999_999)

    result = check_pcm(str(p))

    assert result.status is PcmStatus.TRUNCATED


# -------------------------------------------------------------- bad layout

def test_partial_frame_at_end_is_truncated(tmp_path: Path) -> None:
    """A trailing byte-count that is not a whole number of frames."""
    p = _wave(tmp_path / "partial.wav", channels=2, samples=[[1, 2]] * 10)
    raw = bytearray(p.read_bytes())
    # Declare a data size that is not divisible by block align.
    idx = raw.find(b"data")
    struct.pack_into("<I", raw, idx + 4, 41)
    p.write_bytes(bytes(raw))

    result = check_pcm(str(p))

    assert result.status is PcmStatus.TRUNCATED


# -------------------------------------------------------------------- float

def test_float_wave_nan_is_rejected(tmp_path: Path) -> None:
    channels = 1
    samples = struct.pack("<f", float("nan")) + struct.pack("<f", 0.5) * 7
    fmt = struct.pack("<HHIIHH", 3, channels, 48000, 48000 * 4, 4, 32)
    body = (
        b"WAVE"
        + b"fmt " + struct.pack("<I", len(fmt)) + fmt
        + b"data" + struct.pack("<I", len(samples)) + samples
    )
    p = tmp_path / "nan.wav"
    p.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)

    result = check_pcm(str(p))

    assert result.status is PcmStatus.INVALID_FLOAT


def test_float_wave_in_range_is_ok(tmp_path: Path) -> None:
    samples = b"".join(struct.pack("<f", v) for v in [0.1, -0.5, 0.9, -0.9] * 25)
    fmt = struct.pack("<HHIIHH", 3, 1, 48000, 48000 * 4, 4, 32)
    body = (
        b"WAVE"
        + b"fmt " + struct.pack("<I", len(fmt)) + fmt
        + b"data" + struct.pack("<I", len(samples)) + samples
    )
    p = tmp_path / "okf.wav"
    p.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)

    result = check_pcm(str(p))

    assert result.status is PcmStatus.OK
    assert 0.85 < result.peak <= 1.0


def test_float_out_of_range_is_flagged(tmp_path: Path) -> None:
    samples = b"".join(struct.pack("<f", v) for v in [5.0, -5.0, 0.0, 0.0])
    fmt = struct.pack("<HHIIHH", 3, 1, 48000, 48000 * 4, 4, 32)
    body = (
        b"WAVE"
        + b"fmt " + struct.pack("<I", len(fmt)) + fmt
        + b"data" + struct.pack("<I", len(samples)) + samples
    )
    p = tmp_path / "oor.wav"
    p.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)

    result = check_pcm(str(p))

    assert result.peak > 1.0
    assert result.status is PcmStatus.INVALID_FLOAT


# -------------------------------------------------------------------- misc

def test_missing_file_raises(tmp_path: Path) -> None:
    with pytest.raises(FileNotFoundError):
        check_pcm(str(tmp_path / "nope.wav"))


def test_empty_data_chunk_is_reported(tmp_path: Path) -> None:
    """A zero-frame file is a real state, not a crash."""
    fmt = struct.pack("<HHIIHH", 1, 2, 48000, 48000 * 4, 4, 16)
    body = (
        b"WAVE"
        + b"fmt " + struct.pack("<I", len(fmt)) + fmt
        + b"data" + struct.pack("<I", 0) + b""
    )
    p = tmp_path / "empty.wav"
    p.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)

    result = check_pcm(str(p))

    assert result.status is PcmStatus.EMPTY
    assert result.frames_decoded == 0
