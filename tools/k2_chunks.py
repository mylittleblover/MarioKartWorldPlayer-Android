"""Parse the BWAV kind=2 container as a chunk stream and report its shape.

Hypothesis under test: a kind=2 channel region is a sequence of
    u32 tag, u32 size, payload[size]
laid out contiguously, with the whole region padded to 64 bytes.
"""
import os
import struct
import sys
from collections import Counter

BW = r"G:\Mario Kart World\romfs\Sound\Resource\Stream"


def chunks(data, start, end):
    """Yield (offset, tag, size, payload_start, payload_end) until the region ends."""
    p = start
    out = []
    while p + 8 <= end:
        tag, size = struct.unpack_from("<II", data, p)
        payload_start = p + 8
        payload_end = payload_start + size
        out.append((p - start, tag, size, payload_start, payload_end))
        if size == 0 or payload_end > end:
            break
        p = payload_end
        if len(out) > 64:
            break
    return out


def align(value, to=64):
    return (value + to - 1) // to * to


def main():
    names = sorted(n for n in os.listdir(BW) if n.endswith(".bwav"))
    exact = 0
    padded = 0
    broken = 0
    examples = []
    tag_hist = Counter()

    for name in names:
        path = os.path.join(BW, name)
        with open(path, "rb") as f:
            head = f.read(16)
        if head[:4] != b"BWAV":
            continue
        count = struct.unpack_from("<H", head, 14)[0]
        with open(path, "rb") as f:
            head = f.read(16 + count * 76)
        kind = struct.unpack_from("<H", head, 16)[0]
        if kind != 2:
            continue

        with open(path, "rb") as f:
            data = f.read()

        starts = [struct.unpack_from("<I", head, 16 + ch * 76 + 52)[0] for ch in range(count)]
        end_of_region = struct.unpack_from("<I", head, 16 + (count - 1) * 76 + 52)[0]
        sizes = [struct.unpack_from("<I", data, 16 + 0)[0]]

        ok = True
        for index, start in enumerate(starts):
            region_end = starts[index + 1] if index + 1 < count else len(data)
            parsed = chunks(data, start, region_end)
            if not parsed:
                ok = False
                break
            for _, tag, _, _, _ in parsed:
                tag_hist[tag] += 1
            consumed = parsed[-1][4]
            tail = region_end - consumed
            if not (0 <= tail <= 63):
                ok = False
                if len(examples) < 6:
                    examples.append((name, index, region_end - start, consumed - start, tail))
                break
        if not ok:
            broken += 1
        else:
            exact += 1

    print(f"kind=2 files whose regions parse as chunk streams: {exact}")
    print(f"files that do not: {broken}")
    print(f"tags seen: {dict(tag_hist.most_common(8))}")
    for e in examples:
        print("  ", e)


if __name__ == "__main__":
    main()
