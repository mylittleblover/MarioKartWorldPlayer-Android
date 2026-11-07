"""Prove the DSP-ADPCM decoder bit-exactly against the reference WAV renders.

Compares decoded PCM at several positions per file: the head, a window around
the loop start, and the tail. A systematic error shows up at any of them.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dsp_verify import decode_file, read_wav_reference

BW = r"G:\Mario Kart World\romfs\Sound\Resource\Stream"
WAV = r"G:\Mario Kart World\readable_assets\audio\romfs\Sound\Resource\Stream"


def window(ours, ref, start, count):
    a = ours[start:start + count]
    b = ref[start:start + count]
    n = min(len(a), len(b))
    if n == 0:
        return None
    a, b = a[:n], b[:n]
    exact = sum(1 for x, y in zip(a, b) if x == y)
    maxdiff = max(abs(x - y) for x, y in zip(a, b))
    return dict(n=n, exact=exact, maxdiff=maxdiff)


def check(name, spans=(0, 1_000_000, 3_300_000), count=2000):
    bwav = os.path.join(BW, name)
    wav = os.path.join(WAV, name.replace(".bwav", ".wav"))
    if not (os.path.exists(bwav) and os.path.exists(wav)):
        return None
    ours, info = decode_file(bwav)
    ref, channels, frames = read_wav_reference(wav)
    if len(ours) != len(ref):
        return {"error": "channel mismatch"}

    rows = []
    for start in spans:
        for ch in range(len(ours)):
            r = window(ours[ch], ref[ch], start, count)
            if r is None:
                continue
            rows.append(
                dict(channel=ch, start=start, **r,
                     exact_pct=100.0 * r["exact"] / r["n"])
            )
    total = sum(r["n"] for r in rows)
    exact = sum(r["exact"] for r in rows)
    return dict(
        file=name,
        channels=len(ours),
        frames=frames,
        loop=info[0]["loop"],
        checked=total,
        exact=exact,
        exact_pct=100.0 * exact / total if total else 0.0,
        maxdiff=max((r["maxdiff"] for r in rows), default=0),
        rows=rows,
    )


def main():
    names = sys.argv[1:] or [
        "BGM_Stage_Coaster.bwav",
        "BGM_Stage_AirshipFortress.bwav",
        "BGM_Stage_PeachBeach.bwav",
        "BGM_Stage_Central.bwav",
        "BGM_Stage_WestCity.bwav",
    ]
    results = []
    for name in names:
        r = check(name)
        if r is None:
            print(f"{name}: SKIP (reference or source missing)")
            continue
        if "error" in r:
            print(f"{name}: {r['error']}")
            continue
        results.append(r)
        print(
            f"{name}: channels={r['channels']} frames={r['frames']} "
            f"loop={r['loop']} checked={r['checked']} exact={r['exact']} "
            f"({r['exact_pct']:.3f}%) maxdiff={r['maxdiff']}"
        )
        for row in r["rows"]:
            if row["maxdiff"]:
                print(
                    f"    ch{row['channel']} @{row['start']}: "
                    f"exact={row['exact']}/{row['n']} maxdiff={row['maxdiff']}"
                )

    if results:
        total = sum(r["checked"] for r in results)
        exact = sum(r["exact"] for r in results)
        print(
            f"\nTOTAL: {len(results)} files, {total} samples compared, "
            f"{exact} exact ({100.0*exact/total:.4f}%), "
            f"maxdiff={max(r['maxdiff'] for r in results)}"
        )
        out = r"G:\Mario Kart World\MKW-Player\reports\generated\dsp-verify.json"
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8") as f:
            json.dump(results, f, indent=2)
        print("written:", out)


if __name__ == "__main__":
    main()
