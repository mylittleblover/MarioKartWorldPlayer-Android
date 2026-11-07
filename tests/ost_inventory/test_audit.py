"""OST-04 RED tests: classify every OST entry and report gaps honestly.

Written before the classifier exists. The gap report must be able to say
INVENTORY_INCOMPLETE, and it must never turn "not found" into "fine".
"""

from __future__ import annotations

from pathlib import Path

import pytest

from mkw_tools.inventory.classifier import (
    AssetClass,
    InventoryItem,
    classify_entry,
    classify_name,
)
from mkw_tools.inventory.audit import (
    Coverage,
    InventoryReport,
    build_report,
)


# ------------------------------------------------------------- classification

def test_course_music_is_classified(tmp_path: Path) -> None:
    assert classify_name("BGM_Stage_Coaster") is AssetClass.COURSE


def test_course_variants_share_the_class(tmp_path: Path) -> None:
    for name in (
        "BGM_Stage_Coaster_Pre",
        "BGM_Stage_Coaster_Pre_Short",
        "BGM_Stage_Coaster_Fast",
        "BGM_Stage_Coaster_Bridge",
        "BGM_Stage_Coaster_Outro00",
    ):
        assert classify_name(name) is AssetClass.COURSE, name


def test_jukebox_is_its_own_class(tmp_path: Path) -> None:
    assert classify_name("BGM_JukeBox_M404_LuigiM_Maintheme_OutroFull") is AssetClass.JUKEBOX


def test_battle_music_is_classified(tmp_path: Path) -> None:
    assert classify_name("BGM_Battle_00") is AssetClass.BATTLE


def test_sequence_music_is_classified(tmp_path: Path) -> None:
    assert classify_name("BGM_Seq_Title_Outro02") is AssetClass.SEQUENCE


def test_mission_music_is_classified(tmp_path: Path) -> None:
    assert classify_name("BGM_Mission_ChocoIsland") is AssetClass.MISSION


def test_demo_and_cutscene_are_classified(tmp_path: Path) -> None:
    assert classify_name("BGM_Demo_Staffroll") is AssetClass.DEMO
    assert classify_name("Dm_SpecialOp") is AssetClass.CUTSCENE


def test_rainbow_is_still_course_or_its_own(tmp_path: Path) -> None:
    assert classify_name("BGM_Rainbow_Area1") is AssetClass.COURSE


def test_sound_effect_is_not_called_music(tmp_path: Path) -> None:
    assert classify_name("SE_UI_SpecialCupOpen") is AssetClass.SOUND_EFFECT


def test_unknown_name_is_unclassified_not_guessed(tmp_path: Path) -> None:
    assert classify_name("CompletelyUnknownThing") is AssetClass.UNCLASSIFIED
    assert classify_name("") is AssetClass.UNCLASSIFIED


def test_classification_is_not_derived_from_extension(tmp_path: Path) -> None:
    """A '.wav' in the name must not change the class."""
    assert classify_name("BGM_Stage_Coaster.wav") is AssetClass.COURSE


# ---------------------------------------------------------------- auditing

def _item(name: str, ordinal: int = 0) -> InventoryItem:
    return classify_entry(
        entry_key=f"bars:{ordinal}",
        internal_name=name,
        asset_path=None,
        asset_sha256=None,
        verified=True,
    )


def test_report_counts_every_class(tmp_path: Path) -> None:
    items = [
        _item("BGM_Stage_Coaster"),
        _item("BGM_JukeBox_M001_Test_OutroFull", 1),
        _item("BGM_Battle_00", 2),
        _item("SE_UI_SpecialCupOpen", 3),
    ]

    report = build_report(items, archives_expected=4, archives_read=4)

    assert report.counts[AssetClass.COURSE] == 1
    assert report.counts[AssetClass.JUKEBOX] == 1
    assert report.counts[AssetClass.BATTLE] == 1
    assert report.counts[AssetClass.SOUND_EFFECT] == 1


def test_missing_archives_force_incomplete(tmp_path: Path) -> None:
    report = build_report([_item("BGM_Stage_Coaster")], archives_expected=499, archives_read=4)

    assert report.coverage is Coverage.INCOMPLETE
    assert any("archive" in r.lower() for r in report.reasons)


def test_all_archives_read_still_not_complete_without_verification(tmp_path: Path) -> None:
    """Reading every archive does not prove audio or behaviour."""
    report = build_report([_item("BGM_Stage_Coaster")], archives_expected=4, archives_read=4)

    assert report.coverage is Coverage.INCOMPLETE
    assert any("verif" in r.lower() or "unverified" in r.lower() for r in report.reasons)


def test_unverified_items_are_listed(tmp_path: Path) -> None:
    items = [classify_entry("k", "BGM_Stage_Coaster", None, None, verified=False)]

    report = build_report(items, archives_expected=4, archives_read=4)

    assert report.unverified == 1
    assert report.coverage is Coverage.INCOMPLETE


def test_report_never_claims_complete_with_empty_input(tmp_path: Path) -> None:
    report = build_report([], archives_expected=0, archives_read=0)

    assert report.coverage is Coverage.INCOMPLETE
    assert any("empty" in r.lower() for r in report.reasons)


def test_missing_sources_rows_are_produced(tmp_path: Path) -> None:
    items = [
        classify_entry("k1", "BGM_Stage_Coaster", "/m/BGM_Stage_Coaster.wav", "a" * 64, verified=True),
        classify_entry("k2", "BGM_Stage_Missing", None, None, verified=False),
    ]

    report = build_report(items, archives_expected=4, archives_read=4)

    assert len(report.missing_sources) == 1
    assert report.missing_sources[0]["entry_key"] == "k2"
