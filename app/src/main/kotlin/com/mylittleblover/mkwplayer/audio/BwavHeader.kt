package com.mylittleblover.mkwplayer.audio

/**
 * Reads the metadata a compiled graph needs from a BWAV header.
 *
 * The graph builder needs the sample rate, channel count, frame count and loop
 * points. Those live in the container, which the native decoder also parses; this
 * reader exists so the app can build a graph without a JNI round trip first.
 *
 * It deliberately reads only the 76-byte per-channel headers and never touches
 * sample data, so it is cheap enough to run on any file before deciding whether to
 * decode it. Field offsets were measured on the corpus, not taken from a spec:
 * `evidence/bootstrap/and-04-real-playback.md` records the layout, including the
 * truncated final block that made a strict reader reject 5694 real files.
 */
object BwavHeader {

    /** Codec kind this build can decode: Nintendo DSP-ADPCM. */
    const val KIND_DSP_ADPCM = 1

    /** Loop end sentinel meaning "no loop". */
    private const val NO_LOOP = 0xFFFFFFFFL

    private const val FILE_HEADER_BYTES = 16
    private const val CHANNEL_HEADER_BYTES = 76

    data class Info(
        val sampleRate: Int,
        val channels: Int,
        val frames: Int,
        /** Codec kind of the first channel; 1 for DSP-ADPCM. */
        val codecKind: Int,
        val loopStart: Int?,
        val loopEnd: Int?,
    ) {
        val hasLoop: Boolean get() = loopStart != null && loopEnd != null
    }

    sealed interface Result {
        data class Ok(val info: Info) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Parses the header.
     *
     * Returns [Result.Rejected] rather than throwing: a file the app cannot read
     * is a normal condition, not an exceptional one, and the caller has to decide
     * what to show for it.
     */
    fun read(bytes: ByteArray): Result {
        if (bytes.size < FILE_HEADER_BYTES) {
            return Result.Rejected("file is ${bytes.size} bytes, header needs $FILE_HEADER_BYTES")
        }
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "BWAV") {
            return Result.Rejected("not a BWAV container")
        }

        val version = u16(bytes, 4)
        if (version != 0xFEFF.toInt()) {
            return Result.Rejected("unsupported BWAV version 0x%04x".format(version))
        }

        val channels = u16(bytes, 14)
        if (channels < 1) {
            return Result.Rejected("channel count is $channels")
        }

        val headerEnd = FILE_HEADER_BYTES + channels * CHANNEL_HEADER_BYTES
        if (bytes.size < headerEnd) {
            return Result.Rejected(
                "file is ${bytes.size} bytes, $channels channels need $headerEnd of header",
            )
        }

        val rate = u32(bytes, FILE_HEADER_BYTES + 4)
        if (rate < 8000 || rate > 192000) {
            return Result.Rejected("unsupported sample rate $rate")
        }

        val frames = u32(bytes, FILE_HEADER_BYTES + 12)
        if (frames <= 0) {
            return Result.Rejected("frame count is $frames")
        }

        val codecKind = u16(bytes, FILE_HEADER_BYTES)

        // Every channel must agree, or the container describes two different
        // streams and the graph cannot represent it.
        for (channel in 1 until channels) {
            val base = FILE_HEADER_BYTES + channel * CHANNEL_HEADER_BYTES
            val channelRate = u32(bytes, base + 4)
            val channelFrames = u32(bytes, base + 12)
            val channelKind = u16(bytes, base)
            if (channelRate != rate || channelFrames != frames || channelKind != codecKind) {
                return Result.Rejected(
                    "channel $channel disagrees with channel 0 " +
                        "(rate $channelRate/$rate, frames $channelFrames/$frames, kind $channelKind/$codecKind)",
                )
            }
        }

        val loopLast = u32(bytes, FILE_HEADER_BYTES + 60).toLong() and 0xFFFFFFFFL
        val loopFirst = u32(bytes, FILE_HEADER_BYTES + 64).toLong() and 0xFFFFFFFFL
        val loop = when {
            loopLast == NO_LOOP && loopFirst == 0L -> null
            loopLast == NO_LOOP -> return Result.Rejected("loop start set while loop end is absent")
            loopFirst >= loopLast -> return Result.Rejected(
                "loop start $loopFirst is not before loop end $loopLast",
            )
            loopLast > frames -> return Result.Rejected(
                "loop end $loopLast exceeds frame count $frames",
            )
            else -> loopFirst.toInt() to loopLast.toInt()
        }

        return Result.Ok(
            Info(
                sampleRate = rate,
                channels = channels,
                frames = frames,
                codecKind = codecKind,
                loopStart = loop?.first,
                loopEnd = loop?.second,
            ),
        )
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
}
