"""OST-03: propose BARS-entry <-> audio-asset matches without guessing.

Confidence is coarse and evidence-based:

  EXACT_VERIFIABLE -- exactly one probed file normalises to the target name.
  AMBIGUOUS        -- more than one candidate, or the name needed normalisation
                      beyond the documented suffix rules.
  UNMATCHED        -- nothing resolves.

A name match is NOT audio verification. It says nothing about decoded PCM,
loop points, or whether the audio is the right recording.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum
from pathlib import PurePosixPath
from typing import Iterable, Sequence

from mkw_tools.assets.audio_probe import AudioProbe

__all__ = [
    "Confidence",
    "MatchCandidate",
    "normalise_asset_name",
    "propose_matches",
]


class Confidence(str, Enum):
    EXACT_VERIFIABLE = "EXACT_VERIFIABLE"
    AMBIGUOUS = "AMBIGUOUS"
    UNMATCHED = "UNMATCHED"


@dataclass(frozen=True)
class MatchCandidate:
    entry_key: str
    target_asset: str
    asset_path: str | None
    asset_sha256: str | None
    confidence: Confidence
    match_reason: str


# Measured suffixes only (see evidence/bootstrap/suffix-semantics.md).
# `.NN` = target sample rate / 1000. `yu` / `tk` semantics stay UNKNOWN, but
# they belong to source names, and stripping them is reversible and recorded.
_RATE_SUFFIX = re.compile(r"\.(?:yu|tk|nn|mj)\.(\d+)$")
_BARE_LETTER_SUFFIX = re.compile(r"\.(?:yu|tk|nn|mj)$")
_VERSION_SUFFIX = re.compile(r"\.v\d+$")


def normalise_asset_name(name: str) -> str:
    """Strip only the suffixes whose meaning has been measured."""
    out = _RATE_SUFFIX.sub("", name)
    out = _BARE_LETTER_SUFFIX.sub("", out)
    out = _VERSION_SUFFIX.sub("", out)
    return out


def _stem(path: str) -> str:
    return PurePosixPath(path.replace("\\", "/")).stem


def propose_matches(
    entries: Sequence[tuple[str, str]],
    assets: Iterable[AudioProbe],
) -> list[MatchCandidate]:
    """Match (entry_key, target_asset_name) pairs against probed assets.

    Never touches the filesystem; all facts come from the AudioProbe values.
    """
    by_stem: dict[str, list[AudioProbe]] = {}
    for probe in assets:
        stem = _stem(probe.path)
        if stem:
            by_stem.setdefault(stem, []).append(probe)

    results: list[MatchCandidate] = []
    for entry_key, target in entries:
        stem = normalise_asset_name(target)
        hits = by_stem.get(stem, [])

        if not hits:
            results.append(
                MatchCandidate(
                    entry_key=entry_key,
                    target_asset=target,
                    asset_path=None,
                    asset_sha256=None,
                    confidence=Confidence.UNMATCHED,
                    match_reason="no probed file normalises to this name",
                )
            )
            continue

        if len(hits) > 1:
            results.append(
                MatchCandidate(
                    entry_key=entry_key,
                    target_asset=target,
                    asset_path=None,
                    asset_sha256=None,
                    confidence=Confidence.AMBIGUOUS,
                    match_reason=f"{len(hits)} files share this normalised name",
                )
            )
            continue

        probe = hits[0]
        normalised = stem != target
        results.append(
            MatchCandidate(
                entry_key=entry_key,
                target_asset=target,
                asset_path=probe.path,
                asset_sha256=probe.sha256,
                confidence=Confidence.EXACT_VERIFIABLE,
                match_reason=(
                    "exact name match"
                    if not normalised
                    else "unique match after documented suffix normalisation"
                ),
            )
        )

    return results
