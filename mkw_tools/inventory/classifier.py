"""Classify OST entries by their internal names.

Classification is purely name-based and stays at L0/L1. It answers "what kind
of asset is this?" -- never "is this audio correct?" or "does the game really
trigger it here?".
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum

__all__ = [
    "AssetClass",
    "InventoryItem",
    "classify_entry",
    "classify_name",
]


class AssetClass(str, Enum):
    COURSE = "COURSE"
    JUKEBOX = "JUKEBOX"
    BATTLE = "BATTLE"
    SEQUENCE = "SEQUENCE"
    MISSION = "MISSION"
    DEMO = "DEMO"
    CUTSCENE = "CUTSCENE"
    SOUND_EFFECT = "SOUND_EFFECT"
    BOOT = "BOOT"
    UNCLASSIFIED = "UNCLASSIFIED"


@dataclass(frozen=True)
class InventoryItem:
    entry_key: str
    internal_name: str
    asset_class: AssetClass
    asset_path: str | None
    asset_sha256: str | None
    verified: bool


# Ordered rules; first match wins. Order matters: SE_ must beat the generic
# "anything with _Stage_" case, and JukeBox must beat Stage.
_RULES: tuple[tuple[re.Pattern[str], AssetClass], ...] = (
    (re.compile(r"^SE_"), AssetClass.SOUND_EFFECT),
    (re.compile(r"^Dm_"), AssetClass.CUTSCENE),
    (re.compile(r"^BGM_JukeBox_"), AssetClass.JUKEBOX),
    (re.compile(r"^BGM_Battle_"), AssetClass.BATTLE),
    (re.compile(r"^BGM_Seq_"), AssetClass.SEQUENCE),
    (re.compile(r"^BGM_Mission_"), AssetClass.MISSION),
    (re.compile(r"^BGM_Demo_"), AssetClass.DEMO),
    (re.compile(r"^BGM_FirstBoot"), AssetClass.BOOT),
    (re.compile(r"^BGM_Stage_"), AssetClass.COURSE),
    (re.compile(r"^BGM_Rainbow_"), AssetClass.COURSE),
)

# Role suffixes that describe a variant of the same logical asset.
_VARIANT = re.compile(
    r"_(?:Pre_Short|Pre|Fast|Bridge|Main|Inst|Melody|Pattern\d*"
    r"|Outro\d*|FL|FinalLapFanfare|Final|Area\d)$"
)


def classify_name(name: str) -> AssetClass:
    """Classify by internal name. Never guesses from file extensions."""
    if not name:
        return AssetClass.UNCLASSIFIED
    # Extension-like tails are not part of the asset identity.
    probe = re.sub(r"\.[A-Za-z0-9]{1,5}$", "", name) if "_" not in name.split(".")[-1] else name
    for pattern, cls in _RULES:
        if pattern.match(probe):
            return cls
    return AssetClass.UNCLASSIFIED


def classify_entry(
    entry_key: str,
    internal_name: str,
    asset_path: str | None,
    asset_sha256: str | None,
    verified: bool,
) -> InventoryItem:
    return InventoryItem(
        entry_key=entry_key,
        internal_name=internal_name,
        asset_class=classify_name(internal_name),
        asset_path=asset_path,
        asset_sha256=asset_sha256,
        verified=verified,
    )
