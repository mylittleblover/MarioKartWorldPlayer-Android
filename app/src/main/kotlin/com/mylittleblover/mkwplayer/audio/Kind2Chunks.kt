package com.mylittleblover.mkwplayer.audio

/**
 * Reader for the chunk stream that BWAV codec kind 2 stores per channel.
 *
 * Measured on all 253 kind-2 files in the corpus (594 channels, zero exceptions):
 * every channel region is exactly two chunks laid out contiguously as
 * `u32 tag, u32 size, payload[size]`, with the region padded to 64 bytes at the end.
 *
 *   tag 0x80000001, size 24   six u32 words, identical in every file:
 *                             256, 48000, 32, 0, 0, 120
 *   tag 0x80000004, size N    the encoded audio
 *
 * What this does NOT do is decode. The audio payload has no size-prefixed packet
 * chain at any width, endianness, alignment or start offset, and its average size is
 * constant to within 0.08% across files spanning 2.5M to 15.5M samples -- an implied
 * ~83.6 kbps at 48 kHz. That is consistent with a bitrate-targeted codec such as
 * Opus, but identifying it by resemblance would be guessing, so the bytes are read
 * and reported, never decoded.
 *
 * See `evidence/bootstrap/and-17-jukebox-container.md` for the measurements.
 */
internal object Kind2Chunks {

    /** Chunk tag carrying the six-word configuration block. */
    const val TAG_CONFIG = 0x80000001L

    /** Chunk tag carrying the encoded audio. */
    const val TAG_AUDIO = 0x80000004L

    /** Every observed config chunk is exactly this many bytes. */
    const val CONFIG_BYTES = 24

    /** Region padding, in bytes, allowed after the last chunk. */
    const val MAX_REGION_PADDING = 63

    private const val HEADER_BYTES = 8

    data class Audio(
        val offset: Int,
        val size: Int,
        val impliedBitrate: Int,
    )

    data class Layout(
        /** The six config words, in file order. */
        val config: List<Long>,
        /** Sample rate reported inside the config block. */
        val configRate: Int,
        /** The audio chunk, or null when the stream is malformed. */
        val audio: Audio?,
    )

    sealed interface Result {
        data class Ok(val layout: Layout) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Parses one channel region.
     *
     * @param regionStart first byte of the region
     * @param regionEnd first byte after the region, typically the next channel's
     *   start or the end of the file
     * @param samples the frame count declared by the channel header, used only to
     *   report an implied bitrate
     */
    fun read(bytes: ByteArray, regionStart: Int, regionEnd: Int, samples: Int): Result {
        if (regionStart < 0 || regionEnd > bytes.size || regionEnd - regionStart < HEADER_BYTES) {
            return Result.Rejected("channel region $regionStart..$regionEnd is out of bounds")
        }

        val config = ArrayList<Long>(6)
        var cursor = regionStart
        var audio: Audio? = null
        var configRate = 0

        while (cursor + HEADER_BYTES <= regionEnd) {
            val tag = u32(bytes, cursor)
            val size = u32(bytes, cursor + 4).toInt()

            if (size == 0) break   // trailing zero padding, not a chunk
            if (cursor + HEADER_BYTES + size > regionEnd) {
                return Result.Rejected("chunk at $cursor declares $size bytes past the region end")
            }

            val payloadStart = cursor + HEADER_BYTES
            when (tag) {
                TAG_CONFIG -> {
                    if (size != CONFIG_BYTES) {
                        return Result.Rejected("config chunk is $size bytes, expected $CONFIG_BYTES")
                    }
                    config.clear()
                    for (index in 0 until 6) {
                        config += u32(bytes, payloadStart + index * 4)
                    }
                    configRate = config[1].toInt()
                }

                TAG_AUDIO -> {
                    val bitrate = if (samples > 0 && configRate > 0) {
                        (size.toLong() * 8L * configRate / samples).toInt()
                    } else {
                        0
                    }
                    audio = Audio(offset = payloadStart, size = size, impliedBitrate = bitrate)
                }

                else -> return Result.Rejected("unexpected chunk tag 0x%08x".format(tag))
            }

            cursor = payloadStart + size
        }

        val padding = regionEnd - cursor
        if (padding > MAX_REGION_PADDING) {
            return Result.Rejected("$padding bytes of padding after the last chunk")
        }
        if (config.size != 6) {
            return Result.Rejected("no config chunk found")
        }

        return Result.Ok(Layout(config = config, configRate = configRate, audio = audio))
    }

    private fun u32(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24)

    /** Human-readable summary for the "unsupported" message in the UI. */
    fun describe(layout: Layout): String {
        val bitrate = layout.audio?.impliedBitrate ?: 0
        val kbps = if (bitrate > 0) "%.1f kbps".format(bitrate / 1000.0) else "unknown bitrate"
        return "BWAV codec 2 (chunked, $kbps at ${layout.configRate} Hz)"
    }
}
