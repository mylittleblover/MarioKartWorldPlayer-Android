"""Pick the world-map basemap size and encode it as WebP.

The app draws one bitmap under every map pin, and that bitmap is the single largest thing in the APK.
This measures the trade rather than guessing it: each candidate is encoded at two qualities, and the
resulting sizes are printed so the choice is an argument about numbers instead of taste.

It also checks that the anchors in `map_icon_anchors.json` still line up after the resize, because a
basemap swapped for a differently-framed one would put every pin in the wrong place while looking
perfectly fine.
"""
from __future__ import annotations

import argparse
import json
import os

from PIL import Image


def encode(source: str, out_dir: str, quality: int) -> tuple[str, int, tuple[int, int]]:
    image = Image.open(source).convert("RGB")
    name = os.path.splitext(os.path.basename(source))[0]
    target = os.path.join(out_dir, f"{name}_q{quality}.webp")
    image.save(target, "WEBP", quality=quality, method=6)
    return target, os.path.getsize(target), image.size


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--anchors", required=True)
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)

    original = os.path.getsize(args.source)
    print(f"source: {args.source}")
    print(f"  {original / 1048576:.2f} MB")

    for quality in (82, 90):
        target, size, dimensions = encode(args.source, args.out_dir, quality)
        saved = 100.0 * (1 - size / original)
        print(
            f"  q{quality}: {size / 1048576:.2f} MB  ({saved:.0f} % smaller)  "
            f"{dimensions[0]}x{dimensions[1]}  {os.path.basename(target)}"
        )

    with open(args.anchors, encoding="utf-8") as handle:
        anchors = json.load(handle)

    width, height = anchors["canvas"]
    problems = []
    for group in ("stages", "cups", "sv"):
        for entry in anchors[group]:
            # u/v is resolution independent, so a resize cannot break it. px/py is tied to the canvas,
            # and a mismatch means the anchor file and the chosen image disagree about which image
            # they describe.
            if abs(entry["u"] - entry["px"] / width) > 0.002:
                problems.append((group, entry["name"], "u"))
            if abs(entry["v"] - entry["py"] / height) > 0.002:
                problems.append((group, entry["name"], "v"))

    if problems:
        print(f"anchors inconsistent with canvas {width}x{height}: {problems[:5]}")
        return 1

    print(
        f"anchors: {len(anchors['stages'])} stages, {len(anchors['cups'])} cups, "
        f"{len(anchors['sv'])} survival routes, all consistent with {width}x{height}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
