"""Build an honest coverage report over classified OST items.

The report exists to say NO clearly. Completeness requires more than reading
files: it needs the full archive set, plus audio and behaviour verification,
neither of which this stage performs.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from enum import Enum
from typing import Sequence

from mkw_tools.inventory.classifier import AssetClass, InventoryItem

__all__ = ["Coverage", "InventoryReport", "build_report"]


class Coverage(str, Enum):
    COMPLETE = "COMPLETE"
    INCOMPLETE = "INCOMPLETE"


@dataclass
class InventoryReport:
    counts: Counter
    unclassified: int
    unverified: int
    coverage: Coverage
    reasons: list[str] = field(default_factory=list)
    missing_sources: list[dict] = field(default_factory=list)


def build_report(
    items: Sequence[InventoryItem],
    archives_expected: int,
    archives_read: int,
) -> InventoryReport:
    """Summarise coverage.

    Deliberately conservative: an empty inventory, unread archives, or any
    unverified item all force INCOMPLETE. There is no path to COMPLETE here --
    that requires audio and behaviour verification, which this stage does not
    perform.
    """
    counts: Counter = Counter({cls: 0 for cls in AssetClass})
    for item in items:
        counts[item.asset_class] += 1

    unclassified = counts[AssetClass.UNCLASSIFIED]
    unverified = sum(1 for item in items if not item.verified)

    reasons: list[str] = []

    if not items:
        reasons.append("inventory empty: no items classified")
    if archives_read < archives_expected:
        reasons.append(
            f"only {archives_read}/{archives_expected} archives read"
        )
    if unverified:
        reasons.append(f"{unverified} items have no probed audio source")
    if unclassified:
        reasons.append(f"{unclassified} items unclassified")

    reasons.append(
        "verification not performed: no PCM decode, no loop check, "
        "no behaviour evidence (L2/L3 absent)"
    )

    missing = [
        {
            "entry_key": item.entry_key,
            "internal_name": item.internal_name,
            "asset_class": item.asset_class.value,
            "reason": "no probed audio file resolved",
        }
        for item in items
        if item.asset_path is None
    ]

    return InventoryReport(
        counts=counts,
        unclassified=unclassified,
        unverified=unverified,
        coverage=Coverage.INCOMPLETE,
        reasons=reasons,
        missing_sources=missing,
    )
