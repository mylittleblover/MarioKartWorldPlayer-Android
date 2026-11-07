"""Streaming PCM integrity check.

Reads the decode result and reports what is actually there -- never what the
container promises. Frame counts come from bytes actually present, not from a
header field, so a file that claims more than it holds is caught.

Outcomes are diagnoses, not a pass/fail salad:

  OK             decoded cleanly, audio present, values in range
  SILENT         decoded cleanly but every sample is zero
  TRUNCATED      declared frame count exceeds the bytes actually present
  INVALID_FLOAT  NaN, infinity, or a value outside [-1, 1] in a float stream
  EMPTY          the data chunk holds no whole frame
"""

from __future__ import annotations

import math
import struct
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

__all__ = ["PcmIntegrity", "PcmStatus", "check_pcm"]

_WAVE_FORMAT_PCM = 0x0001
_WAVE_FORMAT_IEEE_FLOAT = 0x0003
_WAVE_FORMAT_EXTENSIBLE = 0xFFFE

_CHUNK = 1 << 20


class PcmStatus(str, Enum):
    OK = "OK"
    SILENT = "SILENT"
    TRUNCATED = "TRUNCATED"
    INVALID_FLOAT = "INVALID_FLOAT"
    EMPTY = "EMPTY"


@dataclass(frozen=True)
class PcmIntegrity:
    status: PcmStatus
    frames_declared: int
    frames_decoded: int
    channels: int
    sample_rate: int
    bits_per_sample: int
    peak: float
    detail: str = ""


def _read_header(path: Path) -> tuple[int, int, int, int, int, int]:
    """Return (fmt_tag, channels, rate, bits, data_offset, data_declared)."""
    with path.open("rb") as handle:
        header = handle.read(1 << 16)

    if header[:4] not in (b"RIFF", b"RIFX") or header[8:12] != b"WAVE":
        raise ValueError("not a RIFF/WAVE file")

    fmt_tag = channels = rate = bits = 0
    data_offset = -1
    data_declared = 0
    pos = 12
    while pos + 8 <= len(header):
        chunk_id = header[pos:pos + 4]
        size = struct.unpack_from("<I", header, pos + 4)[0]
        payload = pos + 8
        if chunk_id == b"fmt ":
            fmt_tag, channels, rate = struct.unpack_from("<HHI", header, payload)
            bits = struct.unpack_from("<H", header, payload + 14)[0]
            if fmt_tag == _WAVE_FORMAT_EXTENSIBLE and size >= 40:
                fmt_tag = struct.unpack_from("<H", header, payload + 24)[0]
        elif chunk_id == b"data":
            data_offset = payload
            data_declared = size
            break
        pos = payload + size + (size & 1)

    if data_offset < 0:
        raise ValueError("no data chunk")
    return fmt_tag, channels, rate, bits, data_offset, data_declared


def check_pcm(path: str) -> PcmIntegrity:
    """Verify decoded PCM integrity by reading actual sample bytes."""
    p = Path(path)
    fmt_tag, channels, rate, bits, data_offset, data_declared = _read_header(p)
    file_size = p.stat().st_size

    if channels <= 0 or bits <= 0:
        raise ValueError(f"invalid layout: {channels} channels, {bits} bits")

    width = bits // 8
    block_align = channels * width
    if block_align <= 0:
        raise ValueError("invalid block alignment")

    # Bytes that truly exist, bounded by both the declared size and the file.
    available = max(0, min(data_declared, file_size - data_offset))
    frames_decoded = available // block_align
    frames_declared = data_declared // block_align if data_declared else 0

    # A declared payload that is not a whole number of frames cannot be
    # decoded fully; the trailing partial frame is missing data.
    trailing_bytes = data_declared % block_align
    short_payload = data_declared > available

    is_float = fmt_tag == _WAVE_FORMAT_IEEE_FLOAT
    if fmt_tag not in (_WAVE_FORMAT_PCM, _WAVE_FORMAT_IEEE_FLOAT):
        raise ValueError(f"unsupported format tag 0x{fmt_tag:04X}")

    peak = 0.0
    invalid_float = False
    all_zero = True
    seen = 0

    with p.open("rb") as handle:
        handle.seek(data_offset)
        remaining = frames_decoded * block_align
        while remaining > 0:
            chunk = handle.read(min(_CHUNK, remaining))
            if not chunk:
                break
            remaining -= len(chunk)
            whole = (len(chunk) // block_align) * block_align
            chunk = chunk[:whole]
            seen += whole

            if is_float:
                values = struct.unpack(f"<{len(chunk) // 4}f", chunk)
                for v in values:
                    if not math.isfinite(v):
                        invalid_float = True
                        continue
                    if v != 0.0:
                        all_zero = False
                    a = abs(v)
                    if a > 1.0:
                        invalid_float = True
                    if a > peak:
                        peak = a
            else:
                if width == 2:
                    values = struct.unpack(f"<{len(chunk) // 2}h", chunk)
                    scale = 32768.0
                elif width == 4:
                    values = struct.unpack(f"<{len(chunk) // 4}i", chunk)
                    scale = 2147483648.0
                elif width == 3:
                    values = []
                    for i in range(0, len(chunk), 3):
                        raw = chunk[i:i + 3]
                        value = int.from_bytes(raw + (b"\xff" if raw[2] & 0x80 else b"\x00"), "little", signed=True)
                        values.append(value)
                    scale = 8388608.0
                else:  # 8-bit unsigned
                    values = [b - 128 for b in chunk]
                    scale = 128.0
                for v in values:
                    if v != 0:
                        all_zero = False
                    a = abs(v) / scale
                    if a > peak:
                        peak = a

    if seen == 0 or frames_decoded == 0:
        status = PcmStatus.EMPTY
        detail = "data chunk holds no whole frame"
    elif short_payload or trailing_bytes:
        status = PcmStatus.TRUNCATED
        detail = (
            f"declared {data_declared} bytes ({frames_declared} frames), "
            f"available {available}, trailing partial {trailing_bytes} bytes"
        )
    elif frames_decoded < frames_declared:
        status = PcmStatus.TRUNCATED
        detail = (
            f"declared {frames_declared} frames, decoded {frames_decoded}"
        )
    elif invalid_float:
        status = PcmStatus.INVALID_FLOAT
        detail = "non-finite or out-of-range float sample"
    elif all_zero:
        status = PcmStatus.SILENT
        detail = "all samples are zero"
    else:
        status = PcmStatus.OK
        detail = ""

    return PcmIntegrity(
        status=status,
        frames_declared=frames_declared,
        frames_decoded=frames_decoded,
        channels=channels,
        sample_rate=rate,
        bits_per_sample=bits,
        peak=peak,
        detail=detail,
    )
