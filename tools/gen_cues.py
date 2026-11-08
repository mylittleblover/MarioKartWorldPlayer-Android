"""Build the cue asset bundled into the APK from the 30-course research export.

The research export (`MKW_BGM_30赛道统一结构化数据_v2.json`) records, for each course, the
named `Full_ToOutroNN` markers read out of the AMTA marker tables. Those are L1 metadata:
they say a marker exists at a byte position in a source file. They do NOT say the game
schedules a transition there, which is L3 behaviour this project does not have.

So the emitted asset carries both facts separately:

  - `cues`        the marker positions, as measured (L1)
  - `provenance`  always "L1_MARKER_ONLY" for this source

The app must never present a cue as a game event. Writing the marker positions as if they
were verified exit points would be exactly the L1-as-L3 substitution the evidence rules
forbid.

What is deliberately NOT emitted:
  - any claim about which cue the game picks
  - outro durations (the export does not carry frame counts for assets)
  - a course list for courses the export could not map (Rainbow has stage_entries = 0)

Usage:
    python tools/gen_cues.py \
        --research "baseline/research/MKW_BGM_30赛道统一结构化数据_v2.json" \
        --out app/src/main/assets/cues.json
"""
from __future__ import annotations

import argparse
import json
import sys

# The only provenance this source can support. Anything else would be a fabrication.
PROVENANCE = "L1_MARKER_ONLY"

# Courses whose stage entries could not be mapped. Recorded as unmapped rather than skipped, so
# the app can say "known course, positions unknown" instead of silently omitting it.
EXPECTED_UNMAPPED = {"Rainbow"}


def build(research: dict) -> dict:
    """Reduces the research export to the cue facts the app can use."""
    courses = []
    total_cues = 0
    unmapped = []

    for course in research.get("stage_courses", []):
        code = course.get("stage_code") or ""
        if not code:
            continue

        cues = []
        for cue in course.get("exit_cues", []) or []:
            name = cue.get("name") or ""
            position = cue.get("position_samples")
            rate = cue.get("sample_rate_reported")
            # A cue without a position or a rate cannot be scheduled, so it is dropped rather
            # than defaulted to zero -- zero is a real position at the start of the track.
            if not name or position is None or not rate:
                continue
            cues.append(
                {
                    "name": name,
                    "markerId": int(cue.get("marker_id", -1)),
                    "frames": int(position),
                    "sampleRate": int(rate),
                }
            )

        if not cues:
            unmapped.append(code)
            continue

        cues.sort(key=lambda c: c["frames"])
        total_cues += len(cues)
        courses.append(
            {
                "code": code,
                "name": course.get("course_name") or code,
                "cues": cues,
                "outroCount": int(course.get("outro_count") or 0),
                # Whether the source archive carries a Bridge entry. This is an asset fact, not a
                # statement that the game routes through it.
                "hasBridge": bool(course.get("has_bridge")),
                "hasShortPre": bool(course.get("has_short_pre")),
                "hasFast": bool(course.get("has_fast")),
            }
        )

    return {
        "schemaVersion": 1,
        "provenance": PROVENANCE,
        "provenanceNote": (
            "Cue positions are AMTA marker records (L1 metadata). Which cue the game schedules, "
            "and when, is not established by this source."
        ),
        "source": research.get("source") or "",
        "sourceDate": research.get("date") or "",
        "courseCount": len(courses),
        "cueCount": total_cues,
        "courses": courses,
        "unmappedCourses": unmapped,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--research", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)

    with open(args.research, encoding="utf-8") as handle:
        research = json.load(handle)

    payload = build(research)

    if payload["cueCount"] == 0:
        # An empty asset would look like "this game has no cues" rather than "the build broke".
        print("no cues extracted; refusing to write an empty asset", file=sys.stderr)
        return 1

    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
        handle.write("\n")

    print(
        f"wrote {payload['courseCount']} courses, {payload['cueCount']} cues "
        f"({len(payload['unmappedCourses'])} unmapped) to {args.out}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
