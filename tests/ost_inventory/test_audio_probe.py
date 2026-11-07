"""OST-03 RED tests: probe lossless sources without guessing.

Written before the module existed. Fixtures are synthesised here so a reader
bug cannot make the fixture agree with itself.
"""

from __future__ import annotations

import hashlib
import struct
from pathlib import Path

import pytest

from mkw_tools.assets.audio_probe import (
    AudioFormat,
    ProbeError,
    UnsupportedFormatError,
    probe_audio,
)


def _wave(path: Path, *, rate: int, channels: int, bits: int, frames: int,
          fmt_tag: int = 1) -> Path:
    block_align = channels * (bits // 8)
    data = b"\x00" * (block_align * frames)
    fmt = struct.pack("<HHIIHH", fmt_tag, channels, rate, rate * block_align,
                      block_align, bits)
    body = (
        b"WAVE"
        + b"fmt " + struct.pack("<I", len(fmt)) + fmt
        + b"data" + struct.pack("<I", len(data)) + data
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)
    return path


def _bwav(path: Path, *, rate: int, channels: int, frames: int,
          version: int = 0x0001FEFF, dup_frames: int | None = None) -> Path:
    head = bytearray(160)
    head[0:4] = b"BWAV"
    struct.pack_into("<I", head, 4, version)
    struct.pack_into("<H", head, 14, channels)
    struct.pack_into("<I", head, 16, 0x00020001)
    struct.pack_into("<I", head, 20, rate)
    struct.pack_into("<I", head, 24, frames)
    struct.pack_into("<I", head, 28, frames if dup_frames is None else dup_frames)
    # no-loop sentinel: +76 = 0xFFFFFFFF, +80 = 0 (see test_bwav_loops.py)
    struct.pack_into("<I", head, 76, 0xFFFFFFFF)
    struct.pack_into("<I", head, 80, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(head) + b"\x00" * 64)
    return path


# ------------------------------------------------------------ wave probing

def test_probes_pcm_wave_facts(tmp_path: Path) -> None:
    p = _wave(tmp_path / "a.wav", rate=48000, channels=2, bits=16, frames=48000)

    probe = probe_audio(str(p))

    assert probe.format is AudioFormat.PCM_WAV
    assert probe.sample_rate == 48000
    assert probe.channels == 2
    assert probe.bits_per_sample == 16
    assert probe.frame_count == 48000
    assert probe.duration_seconds == pytest.approx(1.0)


def test_probe_reports_content_hash_not_filename(tmp_path: Path) -> None:
    payload = _wave(tmp_path / "a.wav", rate=48000, channels=2, bits=16, frames=100)
    renamed = tmp_path / "completely_different_name.wav"
    renamed.write_bytes(payload.read_bytes())

    a = probe_audio(str(payload))
    b = probe_audio(str(renamed))

    assert a.sha256 == b.sha256
    assert a.sha256 == hashlib.sha256(payload.read_bytes()).hexdigest()


def test_different_content_same_name_differs_by_hash(tmp_path: Path) -> None:
    a = _wave(tmp_path / "x" / "same.wav", rate=48000, channels=2, bits=16, frames=10)
    b = _wave(tmp_path / "y" / "same.wav", rate=48000, channels=2, bits=16, frames=20)

    assert probe_audio(str(a)).sha256 != probe_audio(str(b)).sha256


def test_mono_and_stereo_reported_exactly(tmp_path: Path) -> None:
    mono = _wave(tmp_path / "m.wav", rate=48000, channels=1, bits=16, frames=64)
    stereo = _wave(tmp_path / "s.wav", rate=48000, channels=2, bits=16, frames=64)

    assert probe_audio(str(mono)).channels == 1
    assert probe_audio(str(stereo)).channels == 2


def test_float_wave_recognised_separately(tmp_path: Path) -> None:
    p = _wave(tmp_path / "f.wav", rate=48000, channels=2, bits=32, frames=32, fmt_tag=3)

    assert probe_audio(str(p)).format is AudioFormat.FLOAT_WAV


# ------------------------------------------------------- rejection cases

def test_non_audio_file_rejected(tmp_path: Path) -> None:
    p = tmp_path / "notes.txt"
    p.write_bytes(b"this is definitely not audio, not even close" * 4)

    with pytest.raises(UnsupportedFormatError):
        probe_audio(str(p))


def test_truncated_riff_rejected(tmp_path: Path) -> None:
    p = tmp_path / "trunc.wav"
    p.write_bytes(b"RIFF\x00\x00\x00\x00WAVE")

    with pytest.raises(ProbeError):
        probe_audio(str(p))


def test_riff_size_larger_than_file_rejected(tmp_path: Path) -> None:
    good = _wave(tmp_path / "g.wav", rate=48000, channels=2, bits=16, frames=10)
    payload = bytearray(good.read_bytes())
    struct.pack_into("<I", payload, 4, len(payload) + 100_000)
    p = tmp_path / "bad.wav"
    p.write_bytes(bytes(payload))

    with pytest.raises(ProbeError):
        probe_audio(str(p))


def test_missing_data_chunk_rejected(tmp_path: Path) -> None:
    fmt = struct.pack("<HHIIHH", 1, 2, 48000, 48000 * 4, 4, 16)
    body = b"WAVE" + b"fmt " + struct.pack("<I", len(fmt)) + fmt
    p = tmp_path / "nodata.wav"
    p.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)

    with pytest.raises(ProbeError):
        probe_audio(str(p))


def test_unsupported_codec_tag_rejected(tmp_path: Path) -> None:
    p = _wave(tmp_path / "adpcm.wav", rate=48000, channels=1, bits=16, frames=4, fmt_tag=2)

    with pytest.raises(UnsupportedFormatError):
        probe_audio(str(p))


def test_flac_reports_unsupported_not_guessed(tmp_path: Path) -> None:
    p = tmp_path / "x.flac"
    p.write_bytes(b"fLaC" + b"\x00" * 64)

    with pytest.raises(UnsupportedFormatError):
        probe_audio(str(p))


def test_missing_file_raises(tmp_path: Path) -> None:
    with pytest.raises(FileNotFoundError):
        probe_audio(str(tmp_path / "nope.wav"))


# ------------------------------------------------------------ bwav probing

def test_probes_bwav_header(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "a.bwav", rate=48000, channels=6, frames=3483690)

    probe = probe_audio(str(p))

    assert probe.format is AudioFormat.BWAV
    assert probe.sample_rate == 48000
    assert probe.channels == 6
    assert probe.frame_count == 3483690


def test_bwav_rejects_implausible_rate(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "bad.bwav", rate=999_999, channels=2, frames=10)

    with pytest.raises(ProbeError):
        probe_audio(str(p))


def test_bwav_rejects_zero_channels(tmp_path: Path) -> None:
    p = _bwav(tmp_path / "z.bwav", rate=48000, channels=0, frames=10)

    with pytest.raises(ProbeError):
        probe_audio(str(p))


def test_bwav_rejects_inconsistent_frame_count(tmp_path: Path) -> None:
    """Real files repeat the frame count at +24 and +28; disagreement is corruption."""
    p = _bwav(tmp_path / "f.bwav", rate=48000, channels=2, frames=100, dup_frames=999)

    with pytest.raises(ProbeError):
        probe_audio(str(p))
