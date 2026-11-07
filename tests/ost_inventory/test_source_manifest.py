"""OST-01 RED tests: auditable source registration and dedup.

These tests are written BEFORE the implementation exists. They must fail with
ImportError / AttributeError on first run -- that is the expected RED state.
No source media is created here; only original synthetic bytes.
"""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

import pytest

# The module under test does not exist yet: importing it is the real RED signal.
from mkw_tools.inventory.source_manifest import (
    SourceChangedError,
    SourceRecord,
    register_sources,
    write_manifest,
)


def _write(path: Path, payload: bytes) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)
    return path


def test_same_sha_in_same_batch_registered_once(tmp_path: Path) -> None:
    """Identical bytes registered twice in one batch must collapse to one record."""
    payload = b"MKWTEST-synthetic-source-A" * 8
    a = _write(tmp_path / "a" / "M_Bgm.bars.zs", payload)
    b = _write(tmp_path / "b" / "copy_of_same.bars.zs", payload)

    records = register_sources([str(a), str(b)], batch="batch-001")

    assert len(records) == 1
    assert records[0].sha256 == hashlib.sha256(payload).hexdigest()


def test_same_name_different_sha_stay_distinct(tmp_path: Path) -> None:
    """Two different payloads sharing a filename must never be merged."""
    first = _write(tmp_path / "x" / "M_Bgm.bars.zs", b"payload-one" * 16)
    second = _write(tmp_path / "y" / "M_Bgm.bars.zs", b"payload-two" * 16)

    records = register_sources([str(first), str(second)], batch="batch-002")

    assert len(records) == 2
    hashes = {r.sha256 for r in records}
    assert len(hashes) == 2


def test_record_carries_size_and_batch(tmp_path: Path) -> None:
    """Each record must expose byte size and the import batch it came from."""
    payload = b"0123456789"
    p = _write(tmp_path / "one.bin", payload)

    records = register_sources([str(p)], batch="batch-003")

    assert len(records) == 1
    rec = records[0]
    assert isinstance(rec, SourceRecord)
    assert rec.byte_size == len(payload)
    assert rec.import_batch == "batch-003"
    assert rec.sha256 == hashlib.sha256(payload).hexdigest()


def test_private_absolute_path_not_written_to_manifest(tmp_path: Path) -> None:
    """The portable manifest must not leak the private absolute filesystem root."""
    private_root = tmp_path / "PRIVATE-DO-NOT-LEAK"
    p = _write(private_root / "deep" / "M_Bgm.bars.zs", b"secret-location" * 4)
    out = tmp_path / "manifest.json"

    records = register_sources([str(p)], batch="batch-004")
    write_manifest(records, str(out))

    text = out.read_text(encoding="utf-8")
    assert "PRIVATE-DO-NOT-LEAK" not in text
    assert str(tmp_path) not in text

    data = json.loads(text)
    assert data["records"], "manifest must contain the registered record"


def test_manifest_is_byte_stable_across_runs(tmp_path: Path) -> None:
    """Re-running the same batch must produce byte-identical manifest output."""
    p1 = _write(tmp_path / "s1" / "one.bin", b"alpha" * 32)
    p2 = _write(tmp_path / "s2" / "two.bin", b"beta" * 32)

    out1 = tmp_path / "m1.json"
    out2 = tmp_path / "m2.json"

    write_manifest(register_sources([str(p1), str(p2)], batch="batch-005"), str(out1))
    write_manifest(register_sources([str(p2), str(p1)], batch="batch-005"), str(out2))

    assert out1.read_bytes() == out2.read_bytes()


def test_source_file_mutated_during_batch_is_rejected(tmp_path: Path) -> None:
    """A file whose bytes change between stat and hash must fail loudly, not be trusted."""
    p = tmp_path / "flapping.bin"
    p.write_bytes(b"initial" * 8)

    # Mutation uses a different length so detection never depends on mtime resolution.
    with pytest.raises(SourceChangedError):
        register_sources(
            [str(p)],
            batch="batch-006",
            _test_hook_mutate=lambda: p.write_bytes(b"changed-and-longer" * 8),
        )


def test_missing_path_raises(tmp_path: Path) -> None:
    """A path that does not exist must raise, never silently produce an empty record."""
    with pytest.raises(FileNotFoundError):
        register_sources([str(tmp_path / "nope.bin")], batch="batch-007")


def test_empty_directory_input_yields_no_records(tmp_path: Path) -> None:
    """An empty directory contributes nothing, and that is reported honestly."""
    empty = tmp_path / "empty"
    empty.mkdir()

    records = register_sources([str(empty)], batch="batch-008")

    assert records == []
