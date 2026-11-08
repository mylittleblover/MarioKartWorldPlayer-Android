"""Characterise the BWAV codec-kind-2 payload and its packet framing.

What this measures
------------------
For every `kind == 2` channel of every `.bwav` under the corpus root it reports:

  * the six-word configuration block, and whether it is constant corpus-wide;
  * whether the audio chunk parses as a chain of

        u32 big-endian packet_size, 4 opaque bytes, packet[packet_size]

    and whether that chain consumes the payload **exactly** (remainder 0);
  * the packet-size distribution, the total packet count, and the mean bytes per
    packet;
  * the first byte of every packet (the Opus TOC byte) and the configuration it
    decodes to under `config(5) s(1) c(2)`;
  * `960 * packets - declared_samples`, i.e. whether the packet count can
    account for the declared sample count at 960 samples (20 ms) per packet;
  * the byte rate per channel and the implied bitrate;
  * statistics of the four opaque bytes that follow each size field, and a
    bounded test of six checksum candidates against them.

What this does NOT do
---------------------
It does **not** decode audio, write audio, or verify correctness. Decoding needs
libopus and, more importantly, a reference to compare against; that comparison
was done out-of-band against vgmstream and is recorded in
`evidence/bootstrap/codec2-investigation.md`. This script only establishes the
container framing, which is a prerequisite for any decoder and is checkable
without one.

It also does not explain the four opaque bytes. Six checksum candidates are
tested and none match; the script re-runs that test so the negative result stays
reproducible. The field is not needed to decode.

Usage
-----
    python tools/codec2_report.py [--root PATH] [--hash-sample N]

`--root` defaults to the corpus path recorded in this repository. No file is
written; all output goes to stdout.
"""
import argparse
import collections
import os
import struct
import sys
import zlib

DEFAULT_ROOT = r"G:\Mario Kart World\romfs\Sound\Resource\Stream"
CONFIG_TAG = 0x80000001
AUDIO_TAG = 0x80000004
FRAME_SAMPLES = 960          # 20 ms at 48 kHz: what TOC 0xF8 selects
PRE_SKIP = 0                 # the container carries no pre-skip; 120 samples are lost instead
PREFIX = 8                   # u32 size + 4 opaque bytes
MAX_CHANNELS = 32
M32 = 0xFFFFFFFF


def read_bwav(path):
    """Return a list of kind-2 channel records, or [] when the file is not kind 2."""
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 16 or data[:4] != b"BWAV":
        return []
    channels = struct.unpack_from("<H", data, 14)[0]
    if not (1 <= channels <= MAX_CHANNELS) or len(data) < 16 + channels * 76:
        return []
    if struct.unpack_from("<H", data, 16)[0] != 2:
        return []
    out = []
    for ch in range(channels):
        h = 16 + ch * 76
        rate = struct.unpack_from("<I", data, h + 4)[0]
        samples = struct.unpack_from("<I", data, h + 12)[0]
        start = struct.unpack_from("<I", data, h + 52)[0]
        if start + 8 > len(data):
            raise SystemExit("%s ch%d: region start past EOF" % (path, ch))
        tag, size = struct.unpack_from("<II", data, start)
        if tag != CONFIG_TAG:
            raise SystemExit("%s ch%d: config tag 0x%08x" % (path, ch, tag))
        if start + 8 + size + 8 > len(data):
            raise SystemExit("%s ch%d: config block past EOF" % (path, ch))
        config = struct.unpack_from("<6I", data, start + 8)
        at = start + 8 + size
        atag, asize = struct.unpack_from("<II", data, at)
        if atag != AUDIO_TAG:
            raise SystemExit("%s ch%d: audio tag 0x%08x" % (path, ch, atag))
        body = data[at + 8:at + 8 + asize]
        if len(body) != asize:
            raise SystemExit("%s ch%d: audio chunk truncated" % (path, ch))
        out.append(dict(ch=ch, rate=rate, samples=samples, config=config, payload=body))
    return out


def split_packets(payload):
    """Walk the [u32 BE size][4 opaque][packet] chain.

    Returns (packets, opaque, remainder). `remainder` is the number of payload
    bytes the chain could not consume; 0 means the framing is exact. A malformed
    or zero-length size stops the walk and shows up as a non-zero remainder.
    """
    packets, opaque = [], []
    at, end = 0, len(payload)
    while at + PREFIX <= end:
        size = struct.unpack_from(">I", payload, at)[0]
        if size <= 0 or at + PREFIX + size > end:
            break
        opaque.append(payload[at + 4:at + 8])
        packets.append(payload[at + PREFIX:at + PREFIX + size])
        at += PREFIX + size
    return packets, opaque, end - at


def describe_config(config):
    """Name the Opus configuration field (RFC 6716 section 3.1)."""
    if config < 12:
        return "SILK %s" % ("NB" if config < 4 else "MB" if config < 8 else "WB")
    if config < 16:
        return "hybrid %s" % ("SWB" if config == 12 else "FB")
    if config < 20:
        return "CELT NB"
    if config < 24:
        return "CELT WB"
    if config < 28:
        return "CELT SWB"
    return "CELT FB"


def crc_normal(data, poly, init=M32, xorout=M32):
    crc = init
    for b in data:
        crc ^= b << 24
        for _ in range(8):
            crc = ((crc << 1) ^ poly) & M32 if crc & 0x80000000 else (crc << 1) & M32
    return crc ^ xorout


def crc_reflected(data, poly):
    crc = M32
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ poly if crc & 1 else crc >> 1
    return crc ^ M32


HASHES = [
    ("crc32-ieee", lambda b: zlib.crc32(b) & M32),
    ("crc32-bzip2", lambda b: crc_normal(b, 0x04C11DB7, M32, M32)),
    ("crc32-mpeg2", lambda b: crc_normal(b, 0x04C11DB7, M32, 0)),
    ("crc32-posix", lambda b: crc_normal(b, 0x04C11DB7, 0, M32)),
    ("crc32c", lambda b: crc_reflected(b, 0x82F63B78)),
    ("adler32", lambda b: zlib.adler32(b) & M32),
]


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--root", default=DEFAULT_ROOT)
    ap.add_argument("--hash-sample", type=int, default=4000,
                    help="packets per checksum candidate (0 disables)")
    args = ap.parse_args(argv)

    if not os.path.isdir(args.root):
        raise SystemExit("corpus root not found: %s" % args.root)

    names = sorted(n for n in os.listdir(args.root) if n.lower().endswith(".bwav"))
    files = channels = 0
    bad_chain = []
    configs = collections.Counter()
    toc_hist = collections.Counter()
    sizes = []
    opaque = []
    hash_pairs = []
    total_packets = total_bytes = total_samples = 0
    overhang = []
    short = []

    for name in names:
        recs = read_bwav(os.path.join(args.root, name))
        if not recs:
            continue
        files += 1
        for rec in recs:
            channels += 1
            configs[rec["config"]] += 1
            packets, opaque_words, remainder = split_packets(rec["payload"])
            if remainder != 0 or not packets:
                bad_chain.append((name, rec["ch"], remainder))
                continue
            for pkt in packets:
                sizes.append(len(pkt))
                toc_hist[pkt[0]] += 1
            opaque.extend(opaque_words)
            if args.hash_sample and len(hash_pairs) < args.hash_sample:
                hash_pairs.extend(zip(packets, opaque_words))
            total_packets += len(packets)
            total_bytes += len(rec["payload"])
            total_samples += rec["samples"]
            over = FRAME_SAMPLES * len(packets) - rec["samples"]
            overhang.append(over)
            if FRAME_SAMPLES * len(packets) - 120 < rec["samples"]:
                short.append((name, rec["ch"], rec["samples"],
                              FRAME_SAMPLES * len(packets) - PRE_SKIP))
    if not channels:
        raise SystemExit("no BWAV codec-kind-2 files found under %s" % args.root)

    print("corpus root                  %s" % args.root)
    print("bwav files                   %d" % len(names))
    print("kind-2 files                 %d" % files)
    print("kind-2 channels              %d" % channels)
    print()
    print("configuration block (six u32)")
    for cfg, count in configs.items():
        print("   %-34s x%d" % (str(cfg), count))
    print()
    print("packet framing               [u32 BE size][4 opaque][packet]")
    print("channels with exact framing  %d/%d" % (channels - len(bad_chain), channels))
    for b in bad_chain[:10]:
        print("   FAILED %s ch%d remainder %d" % b)
    print("packets                      %d" % total_packets)
    print("payload bytes                %d" % total_bytes)
    print("declared samples             %d" % total_samples)
    print("bytes / packet               %.3f" % (total_bytes / total_packets))
    print("bytes / sample               %.6f" % (total_bytes / total_samples))
    print("implied bitrate              %.2f kbps per channel at 48 kHz"
          % (total_bytes / total_samples * 48000 * 8 / 1000))
    print()
    sizes_sorted = sorted(sizes)
    hist = collections.Counter(sizes)
    print("packet size                  min %d  max %d  mean %.3f  median %d"
          % (sizes_sorted[0], sizes_sorted[-1], sum(sizes) / len(sizes),
             sizes_sorted[len(sizes_sorted) // 2]))
    print("   most common               %s" % hist.most_common(5))
    print("   fraction == 201 bytes     %.4f" % (hist.get(201, 0) / len(sizes)))
    print()
    print("first packet byte (Opus TOC: config 5 bits, s 1 bit, c 2 bits)")
    for toc, count in toc_hist.most_common(8):
        print("   0x%02x x%-10d config %2d  %-9s s=%d c=%d"
              % (toc, count, toc >> 3, describe_config(toc >> 3), (toc >> 2) & 1, toc & 3))
    print("   distinct TOC values       %d" % len(toc_hist))
    print()
    print("sample accounting at %d samples/packet" % FRAME_SAMPLES)
    print("   960*packets - declared samples   min %d  max %d  mean %.2f"
          % (min(overhang), max(overhang), sum(overhang) / len(overhang)))
    print("   channels whose packets cannot reach the declared length: %d" % len(short))
    for s in short[:8]:
        print("      %-44s ch%d declared %d available %d" % (s[0][:44], s[1], s[2], s[3]))
    print()
    print("opaque 4 bytes after the size field")
    print("   distinct values           %d of %d" % (len(set(opaque)), len(opaque)))
    print("   first byte nonzero        %d" % sum(1 for w in opaque if w[0]))
    if args.hash_sample:
        hits = collections.Counter()
        for pkt, word in hash_pairs:
            v_be = int.from_bytes(word, "big")
            v_le = int.from_bytes(word, "little")
            for label, fn in HASHES:
                h = fn(pkt)
                if h == v_be:
                    hits[label + " BE"] += 1
                if h == v_le:
                    hits[label + " LE"] += 1
        print("   checksum test over %d packets:" % len(hash_pairs))
        for label, _ in HASHES:
            print("      %-12s BE %d  LE %d" % (label, hits[label + " BE"], hits[label + " LE"]))
        print("   all-zero hits mean the field is none of these. It is not")
        print("   required to decode and its meaning remains unknown.")


if __name__ == "__main__":
    main(sys.argv[1:])
