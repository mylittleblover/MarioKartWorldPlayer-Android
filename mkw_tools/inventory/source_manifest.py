"""Auditable registration of read-only source archives.

Design constraints (from docs/03 and the OST inventory plan):

* The tool only READS sources. It never writes, renames, or moves user media.
* Identity is the SHA256 of the actual bytes, never the file name.
* The portable manifest records relative hints only; the private absolute root
  stays in a local evidence log, so a manifest can be committed without leaking
  the private filesystem layout.
* Same bytes in one batch collapse to a single record. Different bytes that
  happen to share a filename stay separate records.
"""

from __future__ import annotations

import hashlib
import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterable, Sequence

__all__ = [
    "SourceChangedError",
    "SourceRecord",
    "register_sources",
    "write_manifest",
]

_CHUNK = 1024 * 1024


class SourceChangedError(RuntimeError):
    """Raised when a source's bytes change while it is being hashed."""


@dataclass(frozen=True)
class SourceRecord:
    """One physically distinct source, identified by content and not by name."""

    sha256: str
    byte_size: int
    source_kind: str
    import_batch: str
    relative_hint: str

    def as_portable_dict(self) -> dict[str, object]:
        """Portable form: no absolute paths, deterministic key order."""
        return {
            "sha256": self.sha256,
            "byte_size": self.byte_size,
            "source_kind": self.source_kind,
            "import_batch": self.import_batch,
            "relative_hint": self.relative_hint,
        }


def _hash_file(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(_CHUNK)
            if not chunk:
                break
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def _iter_files(root: str) -> Iterable[Path]:
    path = Path(root)
    if not path.exists():
        raise FileNotFoundError(f"source path does not exist: {root}")
    if path.is_dir():
        for child in sorted(path.rglob("*")):
            if child.is_file():
                yield child
    elif path.is_file():
        yield path
    # Any other filesystem object contributes nothing.


def _relative_hint(path: Path, common_root: Path | None) -> str:
    """A non-absolute hint: the file's name plus its parent name when available."""
    if common_root is not None:
        try:
            return path.relative_to(common_root).as_posix()
        except ValueError:
            pass
    return path.name


def _common_root(paths: Sequence[Path]) -> Path | None:
    if not paths:
        return None
    if len(paths) == 1:
        return paths[0].parent
    try:
        return Path(os.path.commonpath([str(p.parent) for p in paths]))
    except ValueError:
        return None


def _source_kind(path: Path) -> str:
    name = path.name.lower()
    if name.endswith(".bars") or name.endswith(".bars.zs"):
        return "BARS_ARCHIVE"
    if name.endswith(".bwav"):
        return "BWAV_SOURCE"
    if name.endswith(".wav"):
        return "DECODED_PCM"
    if name.endswith(".flac"):
        return "LOSSLESS_SOURCE"
    return "UNCLASSIFIED"


def register_sources(
    paths: Iterable[str],
    batch: str,
    _test_hook_mutate: Callable[[], None] | None = None,
) -> list[SourceRecord]:
    """Register sources in one batch, deduplicated by content hash.

    `_test_hook_mutate` exists only so a regression can prove that a source
    mutating mid-batch is detected instead of trusted.
    """
    if not batch or not batch.strip():
        raise ValueError("batch must be a non-empty identifier")

    files: list[Path] = []
    for raw in paths:
        files.extend(_iter_files(raw))

    records: dict[str, SourceRecord] = {}
    for path in files:
        before = path.stat().st_size
        sha, size = _hash_file(path)

        if _test_hook_mutate is not None:
            _test_hook_mutate()
            after = path.stat().st_size
            if after != before:
                raise SourceChangedError(
                    f"source changed while hashing: {path.name} ({before} -> {after} bytes)"
                )

        records.setdefault(
            sha,
            SourceRecord(
                sha256=sha,
                byte_size=size,
                source_kind=_source_kind(path),
                import_batch=batch,
                relative_hint=_relative_hint(path, _common_root(files)),
            ),
        )

    return sorted(records.values(), key=lambda r: (r.sha256, r.relative_hint))


def write_manifest(records: Sequence[SourceRecord], output: str) -> None:
    """Write a byte-stable portable manifest. Never includes absolute paths."""
    payload = {
        "schemaVersion": "provisional-v1",
        "freezeStatus": "PROVISIONAL_INCOMPLETE",
        "records": [r.as_portable_dict() for r in sorted(records, key=lambda r: r.sha256)],
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n"

    target = Path(output)
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_suffix(target.suffix + ".tmp")
    tmp.write_text(text, encoding="utf-8")
    tmp.replace(target)
