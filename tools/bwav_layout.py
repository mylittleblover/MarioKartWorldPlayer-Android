"""Determine the exact BWAV data layout rule on the real MKW corpus.

Hypothesis to test:
  - each channel's payload is a sequence of 8-byte DSP blocks
  - the FINAL block of the FINAL channel is truncated to the bytes its remaining
    samples actually need: 1 header byte + ceil(last/2) nibble bytes
  - each channel's start is the previous channel's end aligned up to 64 bytes
"""
import json
import os
import struct

BW = r"G:\Mario Kart World\romfs\Sound\Resource\Stream"


def blocks_for(samples):
    return (samples + 13) // 14


def full_bytes(samples):
    return blocks_for(samples) * 8


def tight_bytes(samples):
    if samples == 0:
        return 0
    n = blocks_for(samples)
    last = samples - (n - 1) * 14
    return (n - 1) * 8 + 1 + (last + 1) // 2


def align(value, to):
    return ((value + to - 1) // to) * to


def load_head(path, count):
    need = 16 + count * 76
    with open(path, "rb") as f:
        head = f.read(max(need, 16))
    if len(head) < need:
        with open(path, "rb") as f:
            head = f.read(need)
    return head


def analyse(path):
    size = os.path.getsize(path)
    with open(path, "rb") as f:
        head = f.read(16)
    if head[:4] != b"BWAV":
        return None
    count = struct.unpack_from("<H", head, 14)[0]
    if count < 1 or count > 32:
        return {"error": "bad channel count", "count": count}
    head = load_head(path, count)
    chans = []
    for ch in range(count):
        h = 16 + ch * 76
        chans.append(
            {
                "ch": ch,
                "kind": struct.unpack_from("<H", head, h)[0],
                "speaker": struct.unpack_from("<H", head, h + 2)[0],
                "rate": struct.unpack_from("<I", head, h + 4)[0],
                "samples": struct.unpack_from("<I", head, h + 12)[0],
                "start": struct.unpack_from("<I", head, h + 52)[0],
            }
        )
    return {"size": size, "count": count, "chans": chans}


def main():
    names = sorted(n for n in os.listdir(BW) if n.endswith(".bwav"))
    report = {
        "align64_truncated_last": 0,
        "align64_padded_last": 0,
        "other": 0,
        "kind2": 0,
        "multi_channel": 0,
        "mono": 0,
        "stereo": 0,
        "failures": [],
    }

    for name in names:
        path = os.path.join(BW, name)
        info = analyse(path)
        if info is None:
            continue
        if "error" in info:
            report["failures"].append((name, info["error"]))
            continue
        chans = info["chans"]
        if any(c["kind"] > 1 for c in chans):
            report["kind2"] += 1
            continue
        if info["count"] == 1:
            report["mono"] += 1
        elif info["count"] == 2:
            report["stereo"] += 1
        else:
            report["multi_channel"] += 1

        # Predict each channel's start from 64-byte alignment of the tight end.
        ok = True
        for i, c in enumerate(chans):
            samples = c["samples"]
            if i == 0:
                expected = align(16 + info["count"] * 76, 64)
                # first channel may also be lightly padded, tolerate up to 63
                if not (expected <= c["start"] < expected + 64):
                    ok = False
            else:
                prev = chans[i - 1]
                end = prev["start"] + tight_bytes(prev["samples"])
                expected = align(end, 64)
                if c["start"] != expected:
                    ok = False
        last = chans[-1]
        last_tight = last["start"] + tight_bytes(last["samples"])
        last_full = last["start"] + full_bytes(last["samples"])
        if not ok:
            report["other"] += 1
            if len(report["failures"]) < 10:
                report["failures"].append((name, "start prediction failed"))
            continue
        if info["size"] == last_tight:
            report["align64_truncated_last"] += 1
        elif info["size"] == last_full:
            report["align64_padded_last"] += 1
        else:
            report["other"] += 1
            if len(report["failures"]) < 10:
                report["failures"].append(
                    (name, info["size"], last_tight, last_full)
                )

    for k in ("align64_truncated_last", "align64_padded_last", "kind2",
              "mono", "stereo", "multi_channel", "other"):
        print(f"{k:26s} {report[k]}")
    print(f"total analysed             {len(names)}")
    if report["failures"]:
        print("\nfirst failures:")
        for f in report["failures"]:
            print("  ", f)

    out = r"G:\Mario Kart World\MKW-Player\reports\generated\bwav-layout.json"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    print("\nwritten:", out)


if __name__ == "__main__":
    main()
