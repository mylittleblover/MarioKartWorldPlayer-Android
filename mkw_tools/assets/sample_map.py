"""Map BWAV loop points into explicit source coordinates.

Two coordinate systems are kept apart on purpose:

  SourceSampleFrame -- an index into the decoded source audio.
  TransportFrame    -- the runtime's global clock. Not produced here.

Seconds are derived from the file's OWN sample rate. Nothing assumes 48 kHz:
a 32 kHz file must convert differently, and the tests enforce that.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass
from pathlib import Path

__all__ = [
    "LoopRecord",
    "SampleMapError",
    "build_loop_record",
    "frames_to_seconds",
    "map_loop",
]

_BWAV_MAGIC = b"BWAV"
_NO_LOOP_SENTINEL = 0xFFFFFFFF


class SampleMapError(ValueError):
    """The source cannot be mapped into reliable coordinates."""


@dataclass(frozen=True)
class LoopRecord:
    path: str
    sample_rate: int
    channels: int
    frame_count: int
    duration_seconds: float
    has_loop: bool
    loop_start_frame: int | None
    loop_end_frame: int | None
    loop_start_seconds: float | None
    loop_end_seconds: float | None
    loop_length_frames: int | None
    loop_length_seconds: float | None


def frames_to_seconds(frames: int, sample_rate: int) -> float:
    """Convert source frames to seconds using an explicitly supplied rate."""
    if sample_rate <= 0:
        raise SampleMapError(f"sample rate must be positive, got {sample_rate}")
    if frames < 0:
        raise SampleMapError(f"frame index must not be negative, got {frames}")
    return frames / sample_rate


def map_loop(path: str) -> LoopRecord:
    """Read loop coordinates from a BWAV source. Read-only."""
    p = Path(path)
    blob = p.read_bytes()[:192]

    if len(blob) < 84:
        raise SampleMapError(f"BWAV shorter than 84 bytes ({len(blob)})")
    if blob[:4] != _BWAV_MAGIC:
        raise SampleMapError(f"not a BWAV container: {blob[:4]!r}")

    sample_rate = struct.unpack_from("<I", blob, 20)[0]
    channels = struct.unpack_from("<H", blob, 14)[0]
    frame_count = struct.unpack_from("<I", blob, 24)[0]
    frame_dup = struct.unpack_from("<I", blob, 28)[0]
    loop_end_raw = struct.unpack_from("<I", blob, 76)[0]
    loop_start_raw = struct.unpack_from("<I", blob, 80)[0]

    if sample_rate <= 0:
        raise SampleMapError(f"invalid sample rate {sample_rate}")
    if channels <= 0:
        raise SampleMapError(f"invalid channel count {channels}")
    if frame_dup != frame_count:
        raise SampleMapError(
            f"frame count disagree: +24={frame_count} +28={frame_dup}"
        )

    duration = frames_to_seconds(frame_count, sample_rate)

    if loop_end_raw == _NO_LOOP_SENTINEL:
        if loop_start_raw != 0:
            raise SampleMapError(
                f"no-loop sentinel paired with non-zero start {loop_start_raw}"
            )
        return LoopRecord(
            path=str(p),
            sample_rate=sample_rate,
            channels=channels,
            frame_count=frame_count,
            duration_seconds=duration,
            has_loop=False,
            loop_start_frame=None,
            loop_end_frame=None,
            loop_start_seconds=None,
            loop_end_seconds=None,
            loop_length_frames=None,
            loop_length_seconds=None,
        )

    if loop_end_raw != frame_count:
        raise SampleMapError(
            f"loop end {loop_end_raw} != frame count {frame_count}"
        )
    if loop_start_raw > loop_end_raw:
        raise SampleMapError(
            f"loop start {loop_start_raw} after end {loop_end_raw}"
        )

    length_frames = loop_end_raw - loop_start_raw
    return LoopRecord(
        path=str(p),
        sample_rate=sample_rate,
        channels=channels,
        frame_count=frame_count,
        duration_seconds=duration,
        has_loop=True,
        loop_start_frame=loop_start_raw,
        loop_end_frame=loop_end_raw,
        loop_start_seconds=frames_to_seconds(loop_start_raw, sample_rate),
        loop_end_seconds=frames_to_seconds(loop_end_raw, sample_rate),
        loop_length_frames=length_frames,
        loop_length_seconds=frames_to_seconds(length_frames, sample_rate),
    )


def build_loop_record(path: str) -> dict[str, object]:
    """JSON-ready projection of a loop record."""
    r = map_loop(path)
    return {
        "path": r.path,
        "sampleRate": r.sample_rate,
        "channels": r.channels,
        "frameCount": r.frame_count,
        "durationSeconds": round(r.duration_seconds, 6),
        "hasLoop": r.has_loop,
        "loopStartFrame": r.loop_start_frame,
        "loopEndFrame": r.loop_end_frame,
        "loopStartSeconds": (
            round(r.loop_start_seconds, 6) if r.loop_start_seconds is not None else None
        ),
        "loopEndSeconds": (
            round(r.loop_end_seconds, 6) if r.loop_end_seconds is not None else None
        ),
        "loopLengthFrames": r.loop_length_frames,
        "loopLengthSeconds": (
            round(r.loop_length_seconds, 6) if r.loop_length_seconds is not None else None
        ),
    }
