package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Header parsing rules, pinned against the layout measured on the real corpus.
 *
 * Every rejection here corresponds to a case the native decoder would also
 * refuse; accepting one would let the app build a graph that the engine then
 * rejects, with a far less specific message.
 */
class BwavHeaderTest {

    private fun builder(
        channels: Int = 2,
        rate: Int = 48000,
        frames: Int = 1000,
        kind: Int = 1,
        loopStart: Int? = 100,
        loopEnd: Int? = 900,
    ): ByteArray {
        val headerEnd = 16 + channels * 76
        val bytes = ByteArray(headerEnd)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        putU16(bytes, 4, 0xFEFF)
        putU16(bytes, 14, channels)
        for (channel in 0 until channels) {
            val base = 16 + channel * 76
            putU16(bytes, base, kind)
            putU32(bytes, base + 4, rate)
            putU32(bytes, base + 8, frames)
            putU32(bytes, base + 12, frames)
            if (loopEnd == null) {
                putU32(bytes, base + 60, 0xFFFFFFFF.toInt())
                // Honour loopStart so the malformed "start without end" case can
                // actually be constructed; a real file with a non-zero start and
                // the sentinel end is inconsistent and must be refused.
                putU32(bytes, base + 64, loopStart ?: 0)
            } else {
                putU32(bytes, base + 60, loopEnd)
                putU32(bytes, base + 64, loopStart ?: 0)
            }
        }
        return bytes
    }

    private fun putU16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun putU32(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[at + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[at + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun ok(bytes: ByteArray): BwavHeader.Info {
        val result = BwavHeader.read(bytes)
        assertTrue(result is BwavHeader.Result.Ok, "expected Ok, got $result")
        return (result as BwavHeader.Result.Ok).info
    }

    private fun rejected(bytes: ByteArray): String {
        val result = BwavHeader.read(bytes)
        assertTrue(result is BwavHeader.Result.Rejected, "expected Rejected, got $result")
        return (result as BwavHeader.Result.Rejected).reason
    }

    @Test
    fun readsTheFieldsAGraphNeeds() {
        val info = ok(builder())
        assertEquals(48000, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(1000, info.frames)
        assertEquals(BwavHeader.KIND_DSP_ADPCM, info.codecKind)
        assertEquals(100, info.loopStart)
        assertEquals(900, info.loopEnd)
        assertTrue(info.hasLoop)
    }

    @Test
    fun noLoopUsesTheSentinelAndReportsAbsent() {
        val info = ok(builder(loopStart = null, loopEnd = null))
        assertFalse(info.hasLoop)
        assertEquals(null, info.loopStart)
        assertEquals(null, info.loopEnd)
    }

    @Test
    fun monoIsAccepted() {
        assertEquals(1, ok(builder(channels = 1)).channels)
    }

    @Test
    fun codecKindIsReportedSoPlayabilityCanDecide() {
        // Kind 2 is what the 253 unsupported entries use; the header reader must
        // surface it rather than assume DSP.
        assertEquals(2, ok(builder(kind = 2)).codecKind)
    }

    @Test
    fun rejectsTooShortAFile() {
        assertTrue(rejected(ByteArray(8)).contains("header needs"))
    }

    @Test
    fun rejectsNonBwavMagic() {
        val bytes = builder()
        bytes[0] = 'X'.code.toByte()
        assertTrue(rejected(bytes).contains("not a BWAV"))
    }

    @Test
    fun rejectsWrongVersion() {
        val bytes = builder()
        putU16(bytes, 4, 0x0001)
        assertTrue(rejected(bytes).contains("version"))
    }

    @Test
    fun rejectsZeroChannels() {
        val bytes = builder()
        putU16(bytes, 14, 0)
        assertTrue(rejected(bytes).contains("channel count"))
    }

    @Test
    fun rejectsHeaderThatDoesNotFitTheFile() {
        val bytes = builder(channels = 2)
        val truncated = bytes.copyOf(16 + 76)   // room for one channel only
        assertTrue(rejected(truncated).contains("channels need"))
    }

    @Test
    fun rejectsUnsupportedSampleRate() {
        val bytes = builder()
        putU32(bytes, 16 + 4, 1000)
        assertTrue(rejected(bytes).contains("sample rate"))
    }

    @Test
    fun rejectsZeroFrames() {
        val bytes = builder()
        putU32(bytes, 16 + 12, 0)
        putU32(bytes, 16 + 8, 0)
        assertTrue(rejected(bytes).contains("frame count"))
    }

    @Test
    fun rejectsChannelsThatDisagree() {
        // A container describing two different streams cannot be a graph asset.
        val bytes = builder(channels = 2)
        putU32(bytes, 16 + 76 + 4, 44100)
        assertTrue(rejected(bytes).contains("disagrees"))
    }

    @Test
    fun rejectsLoopStartWithoutALoopEnd() {
        val bytes = builder(loopStart = 500, loopEnd = null)
        assertTrue(rejected(bytes).contains("loop start set"))
    }

    @Test
    fun rejectsInvertedLoop() {
        val bytes = builder(loopStart = 900, loopEnd = 100)
        assertTrue(rejected(bytes).contains("not before"))
    }

    @Test
    fun rejectsLoopPastTheEnd() {
        val bytes = builder(loopStart = 100, loopEnd = 5000)
        assertTrue(rejected(bytes).contains("exceeds frame count"))
    }

    @Test
    fun acceptsLoopEndingExactlyAtTheLastFrame() {
        // Every real MKW loop ends at the frame count, so refusing this would
        // reject the whole library.
        val info = ok(builder(loopStart = 100, loopEnd = 1000))
        assertEquals(1000, info.loopEnd)
    }

    @Test
    fun reasonIsNeverBlankOnRejection() {
        val cases = listOf(
            ByteArray(0),
            byteArrayOf('B'.code.toByte()),
            ByteArray(200),
        )
        for (case in cases) {
            assertTrue(rejected(case).isNotBlank())
        }
    }
}
