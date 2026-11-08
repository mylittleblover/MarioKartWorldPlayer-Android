"""Find every stage icon on the world map by differencing it against the clean map.

The decorated render and the clean render show the same world. Subtract one from the other and what
remains is the stage dioramas and the roads -- the icons, in one image. That is a far better starting
point than template matching, because the on-map dioramas are rotated, perspective-projected and drop a
shadow, none of which a flat 2-D template match handles. An earlier version of this file tried exactly
that and confidently placed seventeen icons in the wrong places.

The pipeline:

  1. **Align.** The two renders use different framing, so the clean map is warped onto the decorated
     one. The alignment comes from the land bounding boxes, found by classifying each image into land
     and water by hue.
  2. **Difference.** Aligned clean minus decorated, thresholded, with small specks removed.
  3. **Components.** Connected regions of the difference are candidate icons.
  4. **Identify.** Each region is compared against the 30 icon templates by colour histogram, and which
     icon sits where is decided by an optimal assignment over all regions at once rather than one at a
     time -- greedy matching lets one bad region steal another's template and the error cascades.

Each stage is checked against something known. The alignment reports its aspect spread and refuses
above 6 %. The difference reports how much of the image it changed and refuses if the renders are not
actually aligned, since a large change everywhere means the warp failed. The assignment reports its
distance, and anything above the threshold is left in `undetected` rather than guessed.

Usage:
    python tools/locate_stage_icons.py \
        --map-clean   readable_assets/images/WorldMap/WorldMap_full_2000.png \
        --map-decorated <decorated render> \
        --icons readable_assets/images/Layout \
        --out app/src/main/assets/stage_positions.json \
        --debug-dir reports/generated
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

DTYPE = np.float32

# The ocean on both renders is blue-to-cyan. Land is green, orange, white or black -- none of which
# share that hue. The range is narrower than it looks: the shallows around the coast are cyan, and a
# loose range would classify the whole coastline as land.
WATER_HUE_MIN = 0.47
WATER_HUE_MAX = 0.63
WATER_SATURATION_MIN = 0.22

# A difference this small is JPEG noise and lighting, not an icon.
DIFFERENCE_THRESHOLD = 0.16

# Icons occupy roughly 0.02 % to 2 % of the map's area. Outside that range a blob is a road, a shadow
# band, or several icons merged into one.
MIN_BLOB_FRACTION = 0.0002
MAX_BLOB_FRACTION = 0.02


def load_rgb(path: str) -> np.ndarray:
    with Image.open(path) as image:
        return np.asarray(image.convert("RGB"), dtype=DTYPE) / DTYPE(255.0)


def load_rgba(path: str) -> tuple[np.ndarray, np.ndarray]:
    with Image.open(path) as image:
        rgba = np.asarray(image.convert("RGBA"), dtype=DTYPE) / DTYPE(255.0)
    return rgba[:, :, :3], rgba[:, :, 3]


def resize_plane(plane: np.ndarray, width: int, height: int) -> np.ndarray:
    if plane.ndim == 2:
        image = Image.fromarray((np.clip(plane, 0, 1) * 255).astype(np.uint8), mode="L")
        return np.asarray(image.resize((width, height), Image.LANCZOS), dtype=DTYPE) / DTYPE(255.0)
    return np.stack(
        [
            np.asarray(
                Image.fromarray((np.clip(plane[:, :, c], 0, 1) * 255).astype(np.uint8), mode="L")
                .resize((width, height), Image.LANCZOS),
                dtype=DTYPE,
            )
            / DTYPE(255.0)
            for c in range(plane.shape[2])
        ],
        axis=2,
    )


def water_mask(rgb: np.ndarray) -> np.ndarray:
    """True where a pixel is ocean.

    Hue alone is not enough -- the shaded ocean at one edge is dark and the shallows are bright, but
    both are blue-cyan while nothing on land is. Saturation rejects the grey-white snow.
    """
    maximum = rgb.max(axis=2)
    minimum = rgb.min(axis=2)
    delta = maximum - minimum
    safe_delta = np.where(delta > 1e-6, delta, 1.0)
    saturation = np.where(maximum > 1e-6, delta / np.maximum(maximum, 1e-6), 0.0)

    red, green, blue = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    hue = np.where(
        maximum == red,
        ((green - blue) / safe_delta) % 6.0,
        np.where(maximum == green, (blue - red) / safe_delta + 2.0, (red - green) / safe_delta + 4.0),
    )
    hue = hue / 6.0
    return (hue > WATER_HUE_MIN) & (hue < WATER_HUE_MAX) & (saturation > WATER_SATURATION_MIN)


def land_box(rgb: np.ndarray) -> tuple[float, float, float, float]:
    """Bounding box of land, as (x0, y0, x1, y1).

    Row and column occupancy are thresholded first, so a few misclassified pixels in deep water cannot
    stretch the box to the image edge.
    """
    mask = ~water_mask(rgb)
    rows = mask.sum(axis=1)
    columns = mask.sum(axis=0)
    ys = np.nonzero(rows > max(3, int(rows.max() * 0.004)))[0]
    xs = np.nonzero(columns > max(3, int(columns.max() * 0.004)))[0]
    if len(ys) == 0 or len(xs) == 0:
        raise SystemExit("no land found; the water classifier is wrong for this image")
    return float(xs[0]), float(ys[0]), float(xs[-1]), float(ys[-1])


def warp_to(image: np.ndarray, source_box, target_box, width: int, height: int) -> np.ndarray:
    """Resamples `image` so its `source_box` lands on `target_box` on a canvas of the given size.

    Crop, scale, paste. An affine resample would be equivalent because the transform is axis-aligned,
    and this is easier to check by eye in the debug output.
    """
    sx0, sy0, sx1, sy1 = source_box
    tx0, ty0, tx1, ty1 = target_box
    crop = image[int(round(sy0)) : int(round(sy1)) + 1, int(round(sx0)) : int(round(sx1)) + 1]
    target_width = max(1, int(round(tx1 - tx0)) + 1)
    target_height = max(1, int(round(ty1 - ty0)) + 1)
    scaled = resize_plane(crop, target_width, target_height)

    canvas = np.zeros((height, width, 3), dtype=DTYPE)
    x0 = max(0, int(round(tx0)))
    y0 = max(0, int(round(ty0)))
    x1 = min(width, x0 + target_width)
    y1 = min(height, y0 + target_height)
    canvas[y0:y1, x0:x1] = scaled[: y1 - y0, : x1 - x0]
    return canvas


def label_regions(mask: np.ndarray) -> list[tuple[int, float, float, float, int, int, int, int]]:
    """Connected components by run-length union-find.

    Returns (label, area, cx, cy, x0, y0, x1, y1) per component. Hand-written because there is no scipy
    here; run-length labelling is the standard way to do this without visiting every pixel twice.
    """
    height, width = mask.shape
    parent: list[int] = []

    def find(a: int) -> int:
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a: int, b: int) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[max(ra, rb)] = min(ra, rb)

    runs_per_row: list[list[tuple[int, int, int]]] = []
    for y in range(height):
        row = mask[y]
        if not row.any():
            runs_per_row.append([])
            continue
        edges = np.diff(np.concatenate(([0], row.view(np.int8), [0])))
        starts = np.nonzero(edges == 1)[0]
        ends = np.nonzero(edges == -1)[0]
        runs: list[tuple[int, int, int]] = []
        for start, end in zip(starts, ends):
            index = len(parent)
            parent.append(index)
            runs.append((int(start), int(end), index))
        runs_per_row.append(runs)

        if y == 0:
            continue
        previous = runs_per_row[y - 1]
        i = j = 0
        while i < len(previous) and j < len(runs):
            p_start, p_end, p_index = previous[i]
            c_start, c_end, c_index = runs[j]
            if p_end <= c_start:
                i += 1
            elif c_end <= p_start:
                j += 1
            else:
                union(p_index, c_index)
                if p_end < c_end:
                    i += 1
                else:
                    j += 1

    groups: dict[int, list] = {}
    for y, runs in enumerate(runs_per_row):
        for start, end, index in runs:
            root = find(index)
            entry = groups.setdefault(root, [0, 0.0, 0.0, start, y, end - 1, y])
            entry[0] += end - start
            entry[1] += (start + end - 1) / 2.0 * (end - start)
            entry[2] += y * (end - start)
            entry[3] = min(entry[3], start)
            entry[4] = min(entry[4], y)
            entry[5] = max(entry[5], end - 1)
            entry[6] = max(entry[6], y)

    result = []
    for label, (area, sum_x, sum_y, x0, y0, x1, y1) in groups.items():
        if area <= 0:
            continue
        result.append((label, float(area), sum_x / area, sum_y / area, x0, y0, x1, y1))
    return result


def colour_signature(rgb: np.ndarray, bins: int = 4) -> np.ndarray:
    """Normalised 4x4x4 RGB histogram.

    Colour rather than shape, because the on-map dioramas are rotated and perspective-projected, so
    their silhouettes do not match the flat source icons while their palettes do. A Lava diorama is
    orange and black whether seen from above or from the side.
    """
    pixels = rgb.reshape(-1, 3)
    if len(pixels) == 0:
        return np.zeros(bins ** 3, dtype=DTYPE)

    indices = np.clip((pixels * bins).astype(np.int32), 0, bins - 1)
    flat = indices[:, 0] * bins * bins + indices[:, 1] * bins + indices[:, 2]
    histogram = np.bincount(flat, minlength=bins ** 3).astype(DTYPE)
    total = histogram.sum()
    return histogram / total if total > 0 else histogram


def histogram_distance(a: np.ndarray, b: np.ndarray) -> float:
    """L1 distance between two normalised histograms, 0..2."""
    return float(np.abs(a - b).sum())


def assign(regions: list[np.ndarray], templates: list[np.ndarray]) -> list[tuple[int, int, float]]:
    """Optimal one-to-one assignment by histogram distance.

    Greedy matching is not used: with one wrong region present, greedy lets it claim a template that
    another region matched better, and the error cascades. This is the Hungarian algorithm on a small
    rectangular cost matrix, which is cheap here and removes the cascade.
    """
    n, m = len(regions), len(templates)
    if n == 0 or m == 0:
        return []

    cost = np.zeros((n, m), dtype=np.float64)
    for i in range(n):
        for j in range(m):
            cost[i, j] = histogram_distance(regions[i], templates[j])

    size = max(n, m)
    big = np.full((size, size), 1e6, dtype=np.float64)
    big[:n, :m] = cost

    u = np.zeros(size + 1)
    v = np.zeros(size + 1)
    p = np.zeros(size + 1, dtype=np.int64)
    way = np.zeros(size + 1, dtype=np.int64)
    for i in range(1, size + 1):
        p[0] = i
        j0 = 0
        minv = np.full(size + 1, np.inf)
        used = np.zeros(size + 1, dtype=bool)
        while True:
            used[j0] = True
            i0 = p[j0]
            delta = np.inf
            j1 = 0
            for j in range(1, size + 1):
                if used[j]:
                    continue
                current = big[i0 - 1, j - 1] - u[i0] - v[j]
                if current < minv[j]:
                    minv[j] = current
                    way[j] = j0
                if minv[j] < delta:
                    delta = minv[j]
                    j1 = j
            for j in range(size + 1):
                if used[j]:
                    u[p[j]] += delta
                    v[j] -= delta
                else:
                    minv[j] -= delta
            j0 = j1
            if p[j0] == 0:
                break
        while True:
            j1 = way[j0]
            p[j0] = p[j1]
            j0 = j1
            if j0 == 0:
                break

    pairs = []
    for j in range(1, size + 1):
        i = p[j]
        if 1 <= i <= n and j <= m:
            pairs.append((i - 1, j - 1, float(cost[i - 1, j - 1])))
    return pairs


def stage_icons(icons_dir: str) -> dict[str, str]:
    """Maps a course code to its MapStage icon.

    Accepts `MapStage_<Code>` and `MapStage_<Code>_00`. Skips `MapStageSh_` (the small badge) and
    `MapStageSelect_` (the menu card): those are different pictures of the same stage, and their
    palettes would compete with the diorama's.
    """
    paths: dict[str, str] = {}
    pattern = re.compile(r"^MapStage_(?!Sh_|Select_)([A-Za-z0-9]+?)(?:_00)?\.Nin_Ounce_NVN2\.png$")
    for name in sorted(os.listdir(icons_dir)):
        match = pattern.match(name)
        if match:
            paths[match.group(1)] = os.path.join(icons_dir, name)
    return paths


def trimmed_colour(path: str) -> np.ndarray:
    """Icon colour cropped to its visible silhouette, so transparent padding cannot skew the palette."""
    rgb, alpha = load_rgba(path)
    ys, xs = np.nonzero(alpha > 0.5)
    if len(ys) == 0:
        return rgb
    return rgb[ys.min() : ys.max() + 1, xs.min() : xs.max() + 1]


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--map-clean", required=True)
    parser.add_argument("--map-decorated", required=True)
    parser.add_argument("--icons", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--debug-dir", default=None)
    parser.add_argument("--max-distance", type=float, default=0.55)
    args = parser.parse_args(argv)

    icons = stage_icons(args.icons)
    print(f"stage icons: {len(icons)}")
    if len(icons) < 25:
        print("too few stage icons", file=sys.stderr)
        return 1

    clean = load_rgb(args.map_clean)
    decorated = load_rgb(args.map_decorated)
    clean_height, clean_width = clean.shape[:2]
    dec_height, dec_width = decorated.shape[:2]
    print(f"clean {clean_width}x{clean_height}   decorated {dec_width}x{dec_height}")

    # ---- 1. align the clean map onto the decorated one
    clean_box = land_box(clean)
    dec_box = land_box(decorated)
    print(f"clean land box     {clean_box}")
    print(f"decorated land box {dec_box}")

    scale_x = (dec_box[2] - dec_box[0]) / max(clean_box[2] - clean_box[0], 1.0)
    scale_y = (dec_box[3] - dec_box[1]) / max(clean_box[3] - clean_box[1], 1.0)
    spread = abs(scale_x - scale_y) / max((scale_x + scale_y) / 2.0, 1e-6)
    print(f"scale x {scale_x:.4f}  y {scale_y:.4f}  spread {spread * 100:.2f} %")
    if spread > 0.06:
        print("the land boxes disagree on aspect by more than 6 %; the alignment is wrong", file=sys.stderr)
        return 1

    aligned = warp_to(clean, clean_box, dec_box, dec_width, dec_height)

    # ---- 2. difference
    difference = np.abs(aligned - decorated).mean(axis=2)
    changed = difference > DIFFERENCE_THRESHOLD
    coverage = float(changed.mean())
    print(f"difference covers {coverage * 100:.2f} % of the image")
    if coverage > 0.35:
        print("most of the image differs; the two renders are not aligned", file=sys.stderr)
        return 1

    # Close then open: closing joins an icon to its own shadow, opening removes the speckle that would
    # otherwise fragment it into a dozen tiny regions.
    mask = changed
    for _ in range(2):
        padded = np.pad(mask, 1, constant_values=False)
        mask = mask | padded[:-2, 1:-1] | padded[2:, 1:-1] | padded[1:-1, :-2] | padded[1:-1, 2:]
    for _ in range(2):
        padded = np.pad(mask, 1, constant_values=True)
        mask = mask & padded[:-2, 1:-1] & padded[2:, 1:-1] & padded[1:-1, :-2] & padded[1:-1, 2:]

    regions = label_regions(mask)
    print(f"regions: {len(regions)}")

    total_area = float(dec_width * dec_height)
    candidates = []
    for _, area, cx, cy, x0, y0, x1, y1 in regions:
        fraction = area / total_area
        if not (MIN_BLOB_FRACTION <= fraction <= MAX_BLOB_FRACTION):
            continue
        width = x1 - x0 + 1
        height = y1 - y0 + 1
        if width < 12 or height < 12:
            continue
        aspect = width / height
        # Square-ish only: a road segment has a long thin bounding box.
        if not (0.45 <= aspect <= 2.2):
            continue
        candidates.append((cx, cy, x0, y0, x1, y1))
    print(f"plausible icon blobs: {len(candidates)}")

    # ---- 3. signatures
    signatures = []
    boxes = []
    for cx, cy, x0, y0, x1, y1 in candidates:
        px0, py0 = max(0, x0 - 2), max(0, y0 - 2)
        px1, py1 = min(dec_width, x1 + 3), min(dec_height, y1 + 3)
        signatures.append(colour_signature(decorated[py0:py1, px0:px1]))
        boxes.append((cx, cy))

    template_codes = sorted(icons)
    template_signatures = [colour_signature(trimmed_colour(icons[code])) for code in template_codes]
    print(f"templates: {len(template_codes)}")

    # ---- 4. assignment
    pairs = assign(signatures, template_signatures)

    accepted: dict[str, dict] = {}
    for region_index, template_index, distance in pairs:
        if distance > args.max_distance:
            continue
        code = template_codes[template_index]
        cx, cy = boxes[region_index]
        # Decorated-space centroid, mapped back into the clean map's coordinates.
        x = clean_box[0] + (cx - dec_box[0]) / scale_x
        y = clean_box[1] + (cy - dec_box[1]) / scale_y
        if not (0.0 <= x <= clean_width and 0.0 <= y <= clean_height):
            continue
        accepted[code] = {
            "x": round(x / clean_width, 5),
            "y": round(y / clean_height, 5),
            "distance": round(distance, 4),
        }
        print(f"  {code:22s} distance {distance:.3f}  clean ({x:7.1f},{y:7.1f})")

    undetected = sorted(set(template_codes) - set(accepted))
    print(f"accepted {len(accepted)}/{len(template_codes)}   undetected {undetected}")

    payload = {
        "schemaVersion": 1,
        "provenance": "IMAGE_DIFFERENCE",
        "provenanceNote": (
            "Stage positions were found by subtracting the clean world map from a decorated render of "
            "the same map, then matching each difference region to a stage icon by colour. They are "
            "pixel measurements of that render, not the game's own map coordinates."
        ),
        "cleanMap": {"width": clean_width, "height": clean_height},
        "decoratedMap": {"width": dec_width, "height": dec_height},
        "alignment": {
            "scaleX": round(scale_x, 6),
            "scaleY": round(scale_y, 6),
            "cleanLandBox": [round(v, 1) for v in clean_box],
            "decoratedLandBox": [round(v, 1) for v in dec_box],
        },
        "maxDistance": args.max_distance,
        "stages": accepted,
        "undetected": undetected,
    }

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    print(f"wrote {args.out}")

    if args.debug_dir:
        os.makedirs(args.debug_dir, exist_ok=True)
        Image.fromarray((mask * 255).astype(np.uint8), mode="L").save(
            os.path.join(args.debug_dir, "difference-mask.png")
        )
        with Image.open(args.map_clean) as source:
            canvas = source.convert("RGB")
        draw = ImageDraw.Draw(canvas)
        for code, row in accepted.items():
            x = row["x"] * clean_width
            y = row["y"] * clean_height
            draw.line([(x - 22, y), (x + 22, y)], fill=(255, 0, 0), width=4)
            draw.line([(x, y - 22), (x, y + 22)], fill=(255, 0, 0), width=4)
            draw.text((x + 24, y - 7), code, fill=(255, 0, 0))
        canvas.save(os.path.join(args.debug_dir, "stage-pins-debug.png"))
        print(f"wrote debug images to {args.debug_dir}")

    return 0 if len(accepted) >= 26 else 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
