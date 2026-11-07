"""Search for a packet framing inside a BWAV kind=2 audio payload.

The average bitrate is constant across all 253 files (0.21771..0.21788 bytes per
sample at 48 kHz), which is what a bitrate-targeted VBR encoder produces, not what a
fixed-size packet stream produces. So packet sizes vary and must be discoverable.

This tries every plausible framing instead of guessing one:
  - size prefix of 1, 2 or 3 bytes
  - little or big endian
  - with or without 0x10 alignment after each packet
  - starting at any offset in the first few KiB of the payload

A framing is accepted only when it consumes the payload exactly.
"""
import struct
import sys


def try_chain(data, start, end, width, big, align, max_packets=1_000_000):
    """Walk a size-prefixed chain. Returns (packets, bytes_consumed) or None."""
    p = start
    count = 0
    while p + width <= end:
        if width == 1:
            size = data[p]
        elif width == 2:
            size = struct.unpack_from(">H" if big else "<H", data, p)[0]
        elif width == 3:
            raw = data[p:p + 3]
            size = int.from_bytes(raw, "big" if big else "little")
        else:
            size = struct.unpack_from(">I" if big else "<I", data, p)[0]

        if size <= 0:
            return None
        payload = p + width
        if payload + size > end:
            return None
        p = payload + size
        if align > 1:
            p = (p + align - 1) // align * align
        count += 1
        if count > max_packets:
            return None
    return (count, p - start) if count else None


def main():
    bwav = sys.argv[1]
    with open(bwav, "rb") as f:
        data = f.read()

    channels = struct.unpack_from("<H", data, 14)[0]
    start = struct.unpack_from("<I", data, 16 + 52)[0]
    samples = struct.unpack_from("<I", data, 16 + 12)[0]

    tag, size = struct.unpack_from("<II", data, start)
    print(f"channel 0 chunk: tag=0x{tag:08x} size={size}")
    payload_start = start + 8 + size
    tag2, size2 = struct.unpack_from("<II", data, payload_start)
    print(f"audio chunk: tag=0x{tag2:08x} size={size2}")
    audio_start = payload_start + 8
    audio_end = audio_start + size2
    print(f"audio payload: {audio_start}..{audio_end} ({size2} bytes, {samples} samples)")
    print(f"first 32: {data[audio_start:audio_start+32].hex()}")

    found = []
    for width in (1, 2, 3, 4):
        for big in (False, True):
            for align in (1, 16, 64):
                for offset in range(0, min(4096, size2)):
                    result = try_chain(data, audio_start + offset, audio_end, width, big, align)
                    if result is None:
                        continue
                    packets, consumed = result
                    if consumed == size2 - offset:
                        found.append((width, big, align, offset, packets))
    if not found:
        print("\nno framing consumes the payload exactly")
        return
    print(f"\n{len(found)} exact framings:")
    for width, big, align, offset, packets in found[:20]:
        endian = "BE" if big else "LE"
        per_packet = size2 / packets
        print(
            f"  width={width} {endian} align={align} offset={offset} "
            f"packets={packets} avg={per_packet:.2f}"
        )


if __name__ == "__main__":
    main()
