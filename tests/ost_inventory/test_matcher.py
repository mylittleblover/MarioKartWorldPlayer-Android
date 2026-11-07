"""OST-03 RED tests: propose BARS-entry <-> audio-asset matches without guessing."""

from __future__ import annotations

from pathlib import Path

import pytest

from mkw_tools.assets.audio_probe import AudioFormat, AudioProbe
from mkw_tools.assets.matcher import (
    Confidence,
    MatchCandidate,
    normalise_asset_name,
    propose_matches,
)


def _probe(path: str, sha: str = "0" * 64) -> AudioProbe:
    return AudioProbe(
        sha256=sha,
        byte_size=1000,
        format=AudioFormat.PCM_WAV,
        sample_rate=48000,
        channels=2,
        bits_per_sample=16,
        frame_count=48000,
        duration_seconds=1.0,
        data_offset=44,
        data_size=96000,
        path=path,
    )


# ------------------------------------------------------- normalisation rules

def test_strips_rate_suffix(tmp_path: Path) -> None:
    assert normalise_asset_name("BGM_Stage_PeachBeach_Outro.yu.48") == "BGM_Stage_PeachBeach_Outro"


def test_strips_tk_rate_suffix(tmp_path: Path) -> None:
    assert normalise_asset_name("BGM_Demo_ED_Result.tk.48") == "BGM_Demo_ED_Result"


def test_strips_version_suffix(tmp_path: Path) -> None:
    assert normalise_asset_name("BGM_Stage_StormClouds_Main.v02") == "BGM_Stage_StormClouds_Main"


def test_leaves_plain_names_untouched(tmp_path: Path) -> None:
    assert normalise_asset_name("BGM_Stage_Coaster_Bridge") == "BGM_Stage_Coaster_Bridge"


def test_does_not_strip_underscore_digit_suffix(tmp_path: Path) -> None:
    """`_2` is part of a real asset name, not a version suffix."""
    assert normalise_asset_name("BGM_Stage_BigTree_Outro00_2") == "BGM_Stage_BigTree_Outro00_2"


# ------------------------------------------------------------- matching

def test_exact_unique_match_is_verifiable(tmp_path: Path) -> None:
    assets = [_probe("/music/BGM_Stage_Coaster.wav", "a" * 64)]

    got = propose_matches([("m-bgm:464", "BGM_Stage_Coaster")], assets)

    assert len(got) == 1
    assert got[0].confidence is Confidence.EXACT_VERIFIABLE
    assert got[0].asset_sha256 == "a" * 64
    assert "exact" in got[0].match_reason


def test_normalised_match_is_verifiable_but_reason_recorded(tmp_path: Path) -> None:
    assets = [_probe("/music/BGM_Stage_PeachBeach_Outro.wav")]

    got = propose_matches([("k", "BGM_Stage_PeachBeach_Outro.yu.48")], assets)

    assert got[0].confidence is Confidence.EXACT_VERIFIABLE
    assert "normalisation" in got[0].match_reason


def test_no_candidate_is_unmatched(tmp_path: Path) -> None:
    assets = [_probe("/music/BGM_Stage_Other.wav")]

    got = propose_matches([("k", "BGM_Stage_Coaster")], assets)

    assert got[0].confidence is Confidence.UNMATCHED
    assert got[0].asset_path is None
    assert got[0].asset_sha256 is None


def test_duplicate_candidates_are_ambiguous(tmp_path: Path) -> None:
    """Two distinct files normalising to the same name must not be auto-picked."""
    assets = [
        _probe("/a/BGM_Stage_Coaster.wav", "a" * 64),
        _probe("/b/BGM_Stage_Coaster.wav", "b" * 64),
    ]

    got = propose_matches([("k", "BGM_Stage_Coaster")], assets)

    assert got[0].confidence is Confidence.AMBIGUOUS
    assert got[0].asset_path is None


def test_same_content_two_paths_is_still_ambiguous_at_name_level(tmp_path: Path) -> None:
    """Name ambiguity is about names; a shared SHA does not resolve which one."""
    assets = [
        _probe("/a/BGM_X.wav", "c" * 64),
        _probe("/b/BGM_X.wav", "c" * 64),
    ]

    got = propose_matches([("k", "BGM_X")], assets)

    assert got[0].confidence is Confidence.AMBIGUOUS


def test_multiple_entries_map_to_their_own_targets(tmp_path: Path) -> None:
    assets = [
        _probe("/m/BGM_Stage_PeachBeach_Outro00.wav", "0" * 64),
        _probe("/m/BGM_Stage_PeachBeach_Outro01.wav", "1" * 64),
    ]
    entries = [("e0", "BGM_Stage_PeachBeach_Outro00"), ("e1", "BGM_Stage_PeachBeach_Outro01")]

    got = propose_matches(entries, assets)

    assert [c.asset_sha256 for c in got] == ["0" * 64, "1" * 64]


def test_shared_source_entries_still_resolve_to_distinct_targets(tmp_path: Path) -> None:
    """Eight PeachBeach entries share a source but each targets its own wav."""
    assets = [_probe(f"/m/BGM_Stage_PeachBeach_Outro0{i}.wav", f"{i}" * 64) for i in range(3)]
    entries = [("s", f"BGM_Stage_PeachBeach_Outro0{i}") for i in range(3)]

    got = propose_matches(entries, assets)

    assert all(c.confidence is Confidence.EXACT_VERIFIABLE for c in got)
    assert len({c.asset_sha256 for c in got}) == 3


def test_empty_inputs_yield_no_candidates(tmp_path: Path) -> None:
    assert propose_matches([], []) == []


def test_matcher_never_reads_the_filesystem(tmp_path: Path) -> None:
    """Paths that do not exist must still match: facts come from AudioProbe."""
    assets = [_probe("/nonexistent/definitely/not/here/BGM_Ghost.wav", "d" * 64)]

    got = propose_matches([("k", "BGM_Ghost")], assets)

    assert got[0].confidence is Confidence.EXACT_VERIFIABLE
    assert not Path("/nonexistent/definitely/not/here/BGM_Ghost.wav").exists()
