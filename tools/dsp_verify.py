"""Decode MKW BWAV (DSP-ADPCM) in pure Python and compare with the reference WAV.

Purpose: prove the decoder is CORRECT, not merely producing non-silent output.
A broken decoder that emits noise passes a "peak > 0" check; only comparison
against an independent render catches that.

Layout facts used here were measured on the corpus:
  header    16 + channels*76
  channel   start = align64(previous channel's padded end)
  data      8-byte blocks, each: 1 header byte + 7 bytes of nibbles (14 samples)
  final     block of the FINAL channel is truncated to 1 + ceil(last/2) bytes
"""
import os
import struct
import sys
import wave

BW = r"G:\Mario Kart World\romfs\Sound\Resource\Stream"
WAV = r"G:\Mario Kart World\readable_assets\audio\romfs\Sound\Resource\Stream"

COEFS = [
    (0, 0), (2048, 0), (0, 2048), (1024, 1024),
    (4096, -2048), (3584, -1536), (3072, -1024), (4608, -2560),
]


def clamp16(v):
    return -32768 if v < -32768 else (32767 if v > 32767 else v)


def decode_channel(data, start, samples, coefficients, hist1, hist2):
    """Decode `samples` DSP-ADPCM frames starting at `start`."""
    out = []
    frames = 0
    pos = start
    while frames < samples:
        header = data[pos]
        predictor = header >> 4
        scale = header & 0x0F
        if predictor >= 8:
            raise ValueError(f"bad predictor {predictor}")
        c1, c2 = coefficients[predictor]
        for byte_index in range(7):
            packed = data[pos + 1 + byte_index]
            for nibble in (packed >> 4, packed & 0x0F):
                if frames >= samples:
                    break
                if nibble >= 8:
                    nibble -= 16
                sample = (nibble * (1 << scale) * 2048 + c1 * hist1 + c2 * hist2 + 1024) // 2048
                sample = clamp16(sample)
                hist2 = hist1
                hist1 = sample
                out.append(sample)
                frames += 1
            if frames >= samples:
                break
        pos += 8
        # The real final block may be truncated; stop once enough frames exist.
    return out, hist1, hist2


def parse(path):
    data = open(path, "rb").read()
    assert data[:4] == b"BWAV", data[:4]
    channels = struct.unpack_from("<H", data, 14)[0]
    header_end = 16 + channels * 76
    info = []
    previous_end = header_end
    for ch in range(channels):
        h = 16 + ch * 76
        kind = struct.unpack_from("<H", data, h)[0]
        rate = struct.unpack_from("<I", data, h + 4)[0]
        samples = struct.unpack_from("<I", data, h + 12)[0]
        start = struct.unpack_from("<I", data, h + 52)[0]
        coefficients = [
            struct.unpack_from("<h", data, h + 16 + n * 2)[0] for n in range(16)
        ]
        pairs = [(coefficients[i * 2], coefficients[i * 2 + 1]) for i in range(8)]
        lo = struct.unpack_from("<H", data, h + 60)[0]
        hi = struct.unpack_from("<I", data, h + 64)[0] if False else struct.unpack_from("<I", data, h + 60)[0]
        loop = None
        last = struct.unpack_from("<I", data, h + 60)[0]
        first = struct.unpack_from("<I", data, h + 64)[0]
        if last != 0xFFFFFFFF:
            loop = (first, last)
        init1 = struct.unpack_from("<h", data, h + 70)[0]
        init2 = struct.unpack_from("<h", data, h + 72)[0]
        info.append(
            dict(channel=ch, kind=kind, rate=rate, samples=samples, start=start,
                 pairs=pairs, loop=loop, init=(init1, init2))
        )
        blocks = (samples + 13) // 14
        previous_end = start + blocks * 8
    return data, channels, info


def decode_file(path, limit=None):
    data, channels, info = parse(path)
    result = []
    for ch in info:
        samples = ch["samples"] if limit is None else min(limit, ch["samples"])
        pcm, _, _ = decode_channel(
            data, ch["start"], samples, ch["pairs"], ch["init"][0], ch["init"][1]
        )
        result.append(pcm)
    return result, info


def read_wav_reference(path):
    with wave.open(path, "rb") as w:
        channels = w.getnchannels()
        frames = w.getnframes()
        raw = w.readframes(frames)
    values = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    per_channel = [list(values[i::channels]) for i in range(channels)]
    return per_channel, channels, frames


def compare(bwav_path, wav_path, limit=48000):
    ours, info = decode_file(bwav_path, limit)
    ref, ref_channels, ref_frames = read_wav_reference(wav_path)
    print(f"bwav channels={len(ours)} ref channels={ref_channels} ref frames={ref_frames}")
    if len(ours) != len(ref):
        print("  channel count mismatch")
        return None
    for ch in range(len(ours)):
        a = ours[ch]
        b = ref[ch][: len(a)]
        n = min(len(a), len(b))
        if n == 0:
            print(f"  ch{ch}: nothing to compare")
            continue
        a = a[:n]
        b = b[:n]
        exact = sum(1 for x, y in zip(a, b) if x == y)
        close = sum(1 for x, y in zip(a, b) if abs(x - y) <= 2)
        diff = [abs(x - y) for x, y in zip(a, b)]
        maxdiff = max(diff)
        meandiff = sum(diff) / n
        print(f"  ch{ch}: n={n} exact={exact} ({100*exact/n:.2f}%) "
              f"within2={close} ({100*close/n:.2f}%) maxdiff={maxdiff} meandiff={meandiff:.3f}")
        print(f"        ours[0:8]={a[:8]}")
        print(f"        ref [0:8]={b[:8]}")
    return ours, ref


if __name__ == "__main__":
    name = sys.argv[1] if len(sys.argv) > 1 else "BGM_Stage_Coaster.bwav"
    bwav = os.path.join(BW, name)
    wav = os.path.join(WAV, name.replace(".bwav", ".wav"))
    print(f"bwav={bwav}\n  exists={os.path.exists(bwav)}")
    print(f"wav ={wav}\n  exists={os.path.exists(wav)}")
    if os.path.exists(bwav) and os.path.exists(wav):
        compare(bwav, wav)
