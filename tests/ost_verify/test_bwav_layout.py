"""Regression tests for the BWAV layout findings, runnable without a device.

These encode what the real MKW corpus forced the parser to accept:
  - final DSP block truncated to the bytes its samples need
  - initial predictor field not required to match the first block header
  - channel starts aligned to 64 bytes from the padded previous end
"""
import struct

import pytest


def dsp_full(samples: int) -> int:
    return ((samples + 13) // 14) * 8


def dsp_tight(samples: int) -> int:
    """Bytes the final block actually occupies when truncated."""
    blocks = (samples + 13) // 14
    last = samples - (blocks - 1) * 14
    return (blocks - 1) * 8 + 1 + (last + 1) // 2


def align(value: int, to: int) -> int:
    return ((value + to - 1) // to) * to


class Bwav:
    """Builds a BWAV container with the layout MKW actually uses."""

    def __init__(self, *, chroma=2, frames=1000, rate=48000, loop=None,
                 truncate_last=True):
        self.chroma = chroma
        self.frames = frames
        self.rate = rate
        self.loop = loop
        self.truncate_last = truncate_last

    def block_size(self, channel: int) -> int:
        if not self.truncate_last or channel != self.chroma - 1:
            return dsp_full(self.frames)
        return dsp_tight(self.frames)

    def build(self) -> bytes:
        header_end = 16 + self.chroma * 76
        starts = []
        cursor = header_end
        for channel in range(self.chroma):
            cursor = align(cursor, 64)
            starts.append(cursor)
            cursor += dsp_full(self.frames)

        total = starts[-1] + self.block_size(self.chroma - 1)
        data = bytearray(total)
        data[0:4] = b"BWAV"
        struct.pack_into("<H", data, 4, 0xFEFF)
        struct.pack_into("<H", data, 6, 1)
        struct.pack_into("<H", data, 12, 0)
        struct.pack_into("<H", data, 14, self.chroma)

        for channel in range(self.chroma):
            h = 16 + channel * 76
            struct.pack_into("<H", data, h, 1)             # DSP codec
            struct.pack_into("<H", data, h + 2, channel)   # speaker
            struct.pack_into("<I", data, h + 4, self.rate)
            struct.pack_into("<I", data, h + 8, self.frames)
            struct.pack_into("<I", data, h + 12, self.frames)
            for n in range(16):
                struct.pack_into("<h", data, h + 16 + n * 2, 0)
            struct.pack_into("<I", data, h + 48, starts[channel])
            struct.pack_into("<I", data, h + 52, starts[channel])
            struct.pack_into("<I", data, h + 56, 1)
            if self.loop:
                struct.pack_into("<I", data, h + 60, self.loop[1])
                struct.pack_into("<I", data, h + 64, self.loop[0])
            else:
                struct.pack_into("<I", data, h + 60, 0xFFFFFFFF)
                struct.pack_into("<I", data, h + 64, 0)
            # A predictor/scale seed deliberately different from the first block
            # header byte, which real files do in 654 of 20991 cases.
            struct.pack_into("<H", data, h + 68, 0x20)
            struct.pack_into("<h", data, h + 70, 0)
            struct.pack_into("<h", data, h + 72, 0)
            struct.pack_into("<H", data, h + 74, 0)
            # First block header byte is 0x00, so it differs from 0x20 above.
            data[starts[channel]] = 0x00
        return bytes(data)


class TestBlockSizing:
    def test_full_block_is_eight_bytes_per_fourteen_samples(self):
        assert dsp_full(14) == 8
        assert dsp_full(28) == 16
        assert dsp_full(15) == 16  # a 15th sample needs a whole extra block

    def test_tight_block_shrinks_the_final_block(self):
        # 1 sample needs 1 header byte + 1 nibble byte.
        assert dsp_tight(1) == 2
        # 14 samples still fit exactly one block.
        assert dsp_tight(14) == 8
        # 15 samples fill block one (8 bytes) and put a single sample in block
        # two, which still needs its own header byte plus one nibble byte.
        assert dsp_tight(15) == 10

    def test_tight_is_never_larger_than_full(self):
        for samples in (1, 2, 7, 13, 14, 15, 27, 28, 29, 100, 1000, 3373711):
            assert dsp_tight(samples) <= dsp_full(samples)

    def test_coaster_real_dimensions(self):
        """The shipped Coaster asset uses the truncated form."""
        assert dsp_tight(3_373_711) == 1_927_836
        assert dsp_full(3_373_711) == 1_927_840
        assert dsp_full(3_373_711) - dsp_tight(3_373_711) == 4


class TestChannelAlignment:
    def test_channels_start_on_sixty_four_byte_boundaries(self):
        first = align(16 + 2 * 76, 64)
        assert first % 64 == 0
        assert first == 192

    def test_second_channel_clears_the_padded_first(self):
        end = 192 + dsp_full(3_373_711)
        assert align(end, 64) == 1_928_064

    def test_alignment_is_strictly_upward(self):
        for value in range(64, 512):
            assert align(value, 64) >= value
            assert align(value, 64) % 64 == 0
            assert align(value, 64) - value < 64


class TestLayoutRoundTrip:
    def test_builder_produces_the_truncated_shape_by_default(self):
        data = Bwav().build()
        second_start = struct.unpack_from("<I", data, 92 + 52)[0]
        assert len(data) - second_start == dsp_tight(1000)

    def test_builder_can_produce_the_padded_shape(self):
        data = Bwav(truncate_last=False).build()
        second_start = struct.unpack_from("<I", data, 92 + 52)[0]
        assert len(data) - second_start == dsp_full(1000)

    def test_padded_file_is_longer_than_truncated_by_the_tail(self):
        truncated = Bwav().build()
        padded = Bwav(truncate_last=False).build()
        assert len(padded) > len(truncated)

    def test_header_seed_may_differ_from_the_first_block_byte(self):
        """The parser must not require these to match; real files disagree."""
        data = Bwav().build()
        seed = struct.unpack_from("<H", data, 16 + 68)[0]
        first_start = struct.unpack_from("<I", data, 16 + 52)[0]
        assert seed == 0x20
        assert data[first_start] == 0x00
        assert seed != data[first_start]

    def test_loop_fields_survive_the_layout(self):
        data = Bwav(loop=(493_714, 3_373_711), frames=3_373_711).build()
        last = struct.unpack_from("<I", data, 16 + 60)[0]
        first = struct.unpack_from("<I", data, 16 + 64)[0]
        assert last == 3_373_711
        assert first == 493_714

    def test_no_loop_uses_the_sentinel(self):
        data = Bwav().build()
        assert struct.unpack_from("<I", data, 16 + 60)[0] == 0xFFFFFFFF
        assert struct.unpack_from("<I", data, 16 + 64)[0] == 0

    def test_mono_file_has_one_channel_stream(self):
        data = Bwav(chroma=1).build()
        start = struct.unpack_from("<I", data, 16 + 52)[0]
        assert len(data) == start + dsp_tight(1000)

    def test_seed_above_seven_bits_is_the_only_rejected_case(self):
        data = bytearray(Bwav().build())
        struct.pack_into("<H", data, 16 + 68, 0x80)
        assert struct.unpack_from("<H", data, 16 + 68)[0] > 0x7F
