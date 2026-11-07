package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TrackOpener] is the only place that joins header reading, graph building and
 * the engine, so its refusal behaviour matters as much as its success path.
 *
 * These tests run on the JVM without a device. Everything up to the first native
 * call is exercised here; the parts that need an engine are covered by the
 * on-device suite.
 */
class TrackOpenerTest {

    private fun bwav(
        channels: Int = 2,
        rate: Int = 48000,
        frames: Int = 1000,
        kind: Int = 1,
        loopStart: Int? = 100,
        loopEnd: Int? = 900,
    ): ByteArray {
        val headerEnd = 16 + channels * 76
        val bytes = ByteArray(headerEnd + 64)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        put16(bytes, 4, 0xFEFF)
        put16(bytes, 14, channels)
        for (channel in 0 until channels) {
            val base = 16 + channel * 76
            put16(bytes, base, kind)
            put32(bytes, base + 4, rate)
            put32(bytes, base + 8, frames)
            put32(bytes, base + 12, frames)
            if (loopEnd == null) {
                put32(bytes, base + 60, 0xFFFFFFFF.toInt())
                put32(bytes, base + 64, loopStart ?: 0)
            } else {
                put32(bytes, base + 60, loopEnd)
                put32(bytes, base + 64, loopStart ?: 0)
            }
        }
        return bytes
    }

    private fun put16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun put32(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[at + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[at + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun refusal(bytes: ByteArray, id: String = "a.bwav"): String {
        val result = TrackOpener.open(id, bytes)
        assertTrue(result is TrackOpener.Result.Refused, "expected refusal, got $result")
        return (result as TrackOpener.Result.Refused).reason
    }

    @Test
    fun refusesANonBwavFile() {
        assertTrue(refusal(ByteArray(128)).contains("BWAV"))
    }

    @Test
    fun refusesAnUnsupportedCodec() {
        // Kind 2 is the 253-entry case; it must be refused by name, not by a
        // generic decode failure later.
        val reason = refusal(bwav(kind = 2))
        assertTrue(reason.contains("codec", ignoreCase = true), "reason was: $reason")
    }

    @Test
    fun refusesRatherThanThrowingOnBadInput() {
        // A refusal is a value, not an exception: callers show it, they do not catch it.
        val result = TrackOpener.open("x.bwav", ByteArray(4))
        assertTrue(result is TrackOpener.Result.Refused)
    }

    @Test
    fun refusalMessagesAreNotEmpty() {
        val cases = listOf(
            ByteArray(0),
            ByteArray(20),
            bwav(rate = 1000),
            bwav(frames = 0),
            bwav(kind = 2),
            bwav(loopStart = 900, loopEnd = 100),
        )
        for (case in cases) {
            assertTrue(refusal(case).isNotBlank())
        }
    }

    @Test
    fun refusalForUnsupportedCodecDoesNotSayMalformed() {
        // Kind 2 is a known limitation of this build, not a damaged file.
        val reason = refusal(bwav(kind = 2))
        assertTrue(!reason.contains("malformed", ignoreCase = true), "reason was: $reason")
    }

    @Test
    fun unsupportedTransportRateIsRefusedBeforeAnyEngineIsCreated() {
        // Building with a transport rate the reader rejects must fail in the
        // builder, not leak an engine.
        val reason = refusal(bwav(), id = "/absolute/path.bwav")
        assertTrue(reason.contains("graph", ignoreCase = true), "reason was: $reason")
    }

    @Test
    fun singleSegmentTargetIsOne() {
        // The builder always emits exactly one segment at index 1; playing
        // anything else on the graph path returns Ok with no audio.
        assertEquals(1, TrackOpener.SINGLE_SEGMENT)
    }
}
