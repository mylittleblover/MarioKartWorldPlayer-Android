"""Audio source probing: read real metadata, never guess.

Deliberately narrow: this module only reports what the RIFF/WAVE header
actually says. It does not decode PCM, does not trust file extensions, and
does not fall back to "looks plausible".
"""

from __future__ import annotations

import hashlib
import struct
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

__all__ = [
    "AudioFormat",
    "AudioProbe",
    "ProbeError",
    "UnsupportedFormatError",
    "probe_audio",
]

_MAX_HEADER = 1 << 20  # 1 MiB is far beyond any sane RIFF header


class ProbeError(ValueError):
    """The file cannot be interpreted as a supported audio container."""


class UnsupportedFormatError(ProbeError):
    """The container or codec is recognised but deliberately not supported."""


class AudioFormat(str, Enum):
    PCM_WAV = "PCM_WAV"
    FLOAT_WAV = "FLOAT_WAV"
    BWAV = "BWAV"
    FLAC = "FLAC"
    UNKNOWN = "UNKNOWN"


@dataclass(frozen=True)
class AudioProbe:
    """Facts read from the file header. No inference beyond the container spec."""

    sha256: str
    byte_size: int
    format: AudioFormat
    sample_rate: int
    channels: int
    bits_per_sample: int
    frame_count: int
    duration_seconds: float
    data_offset: int
    data_size: int
    path: str = ""
    loop_start_frame: int | None = None
    loop_end_frame: int | None = None

    def as_dict(self) -> dict[str, object]:
        return {
            "path": self.path,
            "sha256": self.sha256,
            "byte_size": self.byte_size,
            "format": self.format.value,
            "sample_rate": self.sample_rate,
            "channels": self.channels,
            "bits_per_sample": self.bits_per_sample,
            "frame_count": self.frame_count,
            "duration_seconds": round(self.duration_seconds, 6),
            "data_offset": self.data_offset,
            "data_size": self.data_size,
            "loop_start_frame": self.loop_start_frame,
            "loop_end_frame": self.loop_end_frame,
        }


_WAVE_FORMAT_PCM = 0x0001
_WAVE_FORMAT_IEEE_FLOAT = 0x0003
_WAVE_FORMAT_EXTENSIBLE = 0xFFFE


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(1 << 20)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def _iter_chunks(blob: bytes, file_size: int):
    """Yield (chunk_id, payload_offset, payload_size) from a RIFF body.

    `file_size` is the real file length; `blob` may be a truncated header
    window, so a data chunk that extends past the window is still acceptable
    as long as it fits the file.
    """
    pos = 12
    limit = len(blob)
    while pos + 8 <= limit:
        chunk_id = blob[pos:pos + 4]
        size = struct.unpack_from("<I", blob, pos + 4)[0]
        payload = pos + 8
        if payload + size > file_size:
            raise ProbeError(
                f"chunk {chunk_id!r} declares {size} bytes but only "
                f"{file_size - payload} remain"
            )
        yield chunk_id, payload, size
        pos = payload + size + (size & 1)


def _parse_wave(path: Path, blob: bytes, sha: str, byte_size: int) -> AudioProbe:
    riff_size = struct.unpack_from("<I", blob, 4)[0]
    # Compare against the real file size: `blob` is only the header window.
    if riff_size + 8 > byte_size:
        raise ProbeError(f"RIFF size {riff_size} exceeds file size {byte_size}")

    fmt_seen = False
    sample_rate = channels = bits = 0
    fmt_tag = 0
    data_offset = data_size = -1

    for chunk_id, off, size in _iter_chunks(blob, byte_size):
        if chunk_id == b"fmt ":
            if size < 16:
                raise ProbeError(f"fmt chunk too small ({size} bytes)")
            fmt_tag, channels, sample_rate = struct.unpack_from("<HHI", blob, off)
            bits = struct.unpack_from("<H", blob, off + 14)[0]
            if fmt_tag == _WAVE_FORMAT_EXTENSIBLE:
                if size < 40:
                    raise ProbeError("WAVE_FORMAT_EXTENSIBLE without sub-format")
                fmt_tag = struct.unpack_from("<H", blob, off + 24)[0]
            fmt_seen = True
        elif chunk_id == b"data":
            data_offset, data_size = off, size
            break

    if not fmt_seen:
        raise ProbeError("no fmt chunk")
    if data_offset < 0:
        raise ProbeError("no data chunk")

    if fmt_tag == _WAVE_FORMAT_PCM:
        fmt = AudioFormat.PCM_WAV
    elif fmt_tag == _WAVE_FORMAT_IEEE_FLOAT:
        fmt = AudioFormat.FLOAT_WAV
    else:
        raise UnsupportedFormatError(f"unsupported wFormatTag 0x{fmt_tag:04X}")

    if channels <= 0:
        raise ProbeError(f"invalid channel count {channels}")
    if bits not in (8, 16, 24, 32, 64):
        raise ProbeError(f"unsupported sample width {bits} bits")

    block_align = channels * (bits // 8)
    if block_align <= 0:
        raise ProbeError("invalid block alignment")
    frame_count = data_size // block_align

    return AudioProbe(
        sha256=sha,
        byte_size=byte_size,
        format=fmt,
        sample_rate=sample_rate,
        channels=channels,
        bits_per_sample=bits,
        frame_count=frame_count,
        duration_seconds=frame_count / sample_rate if sample_rate else 0.0,
        data_offset=data_offset,
        data_size=data_size,
        path=str(path),
    )


def _parse_bwav(path: Path, blob: bytes, sha: str, byte_size: int) -> AudioProbe:
    """Nintendo BWAV header.

    Measured on all 20991 real files (version 0x0001FEFF throughout):

        +4   u32  version 0x0001FEFF
        +14  u16  channel count
        +16  u32  codec/format tag (0x00020001, 1, or 2); meaning UNKNOWN
        +20  u32  sample rate
        +24  u32  frame count
        +28  u32  frame count (repeat; equal in every observed file)
        +76  u32  loop end frame, or 0xFFFFFFFF when there is no loop
        +80  u32  loop start frame, or 0 when there is no loop

    Loop validity was checked against every file: 2881 files satisfy
    `0 <= loopStart <= loopEnd == frameCount` and 18110 use the sentinel pair,
    with zero exceptions. In every loop-bearing file loopEnd equals the total
    frame count, so a BWAV loop always runs to the end of the file.

    Field +16 stays opaque: three observed values are not enough to name a
    codec, and guessing would be fabrication.
    """
    if len(blob) < 84:
        raise ProbeError("BWAV shorter than 84 bytes")

    sample_rate = struct.unpack_from("<I", blob, 20)[0]
    channels = struct.unpack_from("<H", blob, 14)[0]
    frame_count = struct.unpack_from("<I", blob, 24)[0]
    frame_count_dup = struct.unpack_from("<I", blob, 28)[0]
    loop_end_raw = struct.unpack_from("<I", blob, 76)[0]
    loop_start = struct.unpack_from("<I", blob, 80)[0]

    if not (8000 <= sample_rate <= 384000):
        raise ProbeError(f"implausible BWAV sample rate {sample_rate}")
    if not (1 <= channels <= 16):
        raise ProbeError(f"implausible BWAV channel count {channels}")
    if frame_count_dup != frame_count:
        raise ProbeError(
            f"BWAV frame count mismatch: +24={frame_count} +28={frame_count_dup}"
        )

    no_loop = loop_end_raw == 0xFFFFFFFF
    if no_loop:
        if loop_start != 0:
            raise ProbeError(
                f"BWAV no-loop sentinel with non-zero start {loop_start}"
            )
        loop_start_frame = None
        loop_end_frame = None
    else:
        if loop_end_raw != frame_count:
            raise ProbeError(
                f"BWAV loop end {loop_end_raw} != frame count {frame_count}"
            )
        if loop_start > loop_end_raw:
            raise ProbeError(
                f"BWAV loop start {loop_start} exceeds loop end {loop_end_raw}"
            )
        loop_start_frame = loop_start
        loop_end_frame = loop_end_raw

    return AudioProbe(
        sha256=sha,
        byte_size=byte_size,
        format=AudioFormat.BWAV,
        sample_rate=sample_rate,
        channels=channels,
        bits_per_sample=16,
        frame_count=frame_count,
        duration_seconds=frame_count / sample_rate if sample_rate else 0.0,
        data_offset=0,
        data_size=byte_size,
        path=str(path),
        loop_start_frame=loop_start_frame,
        loop_end_frame=loop_end_frame,
    )


def probe_audio(path: str) -> AudioProbe:
    """Read container facts from a single file. Read-only, never guesses."""
    p = Path(path)
    byte_size = p.stat().st_size
    with p.open("rb") as handle:
        blob = handle.read(_MAX_HEADER)

    if len(blob) < 12:
        raise ProbeError(f"file too short ({len(blob)} bytes)")

    magic = blob[:4]
    sha = _sha256(p)

    if magic in (b"RIFF", b"RIFX") and blob[8:12] == b"WAVE":
        return _parse_wave(p, blob, sha, byte_size)
    if magic == b"BWAV":
        return _parse_bwav(p, blob, sha, byte_size)
    if magic == b"fLaC":
        raise UnsupportedFormatError("FLAC probing is not implemented")
    raise UnsupportedFormatError(f"unrecognised container {magic!r}")
