package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The chunk reader exists so a kind-2 file can be described precisely instead of
 * being called "unsupported" with no detail, and so the discovery is pinned against
 * regression rather than living in a notes file.
 *
 * The numbers below come from the corpus: every one of 253 files, 594 channels,
 * carries exactly `tag 0x80000001 size 24` followed by `tag 0x80000004 size N`.
 */
class Kind2ChunksTest {

    /** Builds a channel region in the measured shape. */
    private fun region(
        config: List<Long> = listOf(256, 48000, 32, 0, 0, 120),
        audioSize: Int = 209,
        padding: Int = 0,
        configTag: Long = Kind2Chunks.TAG_CONFIG,
        audioTag: Long = Kind2Chunks.TAG_AUDIO,
        declaredAudioSize: Int? = null,
    ): ByteArray {
        val total = 8 + 24 + 8 + audioSize + padding
        val bytes = ByteArray(total)
        var p = 0
        put32(bytes, p, configTag); put32(bytes, p + 4, 24L); p += 8
        config.forEach { put32(bytes, p, it); p += 4 }
        put32(bytes, p, audioTag); put32(bytes, p + 4, (declaredAudioSize ?: audioSize).toLong()); p += 8
        // Fill the audio payload with a byte pattern so an offset mistake is visible.
        for (i in 0 until audioSize) bytes[p + i] = (i % 251).toByte()
        return bytes
    }

    private fun put32(bytes: ByteArray, at: Int, value: Long) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[at + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[at + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun ok(bytes: ByteArray, samples: Int = 960): Kind2Chunks.Layout {
        val result = Kind2Chunks.read(bytes, 0, bytes.size, samples)
        assertTrue(result is Kind2Chunks.Result.Ok, "expected Ok, got $result")
        return (result as Kind2Chunks.Result.Ok).layout
    }

    private fun rejected(bytes: ByteArray, samples: Int = 960): String {
        val result = Kind2Chunks.read(bytes, 0, bytes.size, samples)
        assertTrue(result is Kind2Chunks.Result.Rejected, "expected Rejected, got $result")
        return (result as Kind2Chunks.Result.Rejected).reason
    }

    @Test
    fun readsTheMeasuredConfigWords() {
        val layout = ok(region())
        assertEquals(listOf(256L, 48000L, 32L, 0L, 0L, 120L), layout.config)
        assertEquals(48000, layout.configRate)
    }

    @Test
    fun locatesTheAudioPayload() {
        val layout = ok(region(audioSize = 209))
        val audio = assertNotNull(layout.audio)
        assertEquals(209, audio.size)
        assertEquals(8 + 24 + 8, audio.offset)
    }

    @Test
    fun reportsAnImpliedBitrateFromTheConfigRate() {
        // 209 bytes per 960 samples at 48 kHz is 83 600 bps, which matches the
        // corpus average within measurement noise.
        val audio = assertNotNull(ok(region(audioSize = 209), samples = 960).audio)
        assertEquals(83_600, audio.impliedBitrate)
    }

    @Test
    fun toleratesTheRegionPadding() {
        // The corpus allows up to 63 bytes of padding after the last chunk.
        val layout = ok(region(padding = 63))
        assertNotNull(layout.audio)
    }

    @Test
    fun rejectsPaddingPastTheMeasuredMaximum() {
        assertTrue(rejected(region(padding = 64)).contains("padding"))
    }

    @Test
    fun rejectsAConfigChunkOfTheWrongSize() {
        val bytes = region()
        put32(bytes, 4, 20L)   // config size no longer 24
        assertTrue(rejected(bytes).contains("config chunk"))
    }

    @Test
    fun rejectsAnUnexpectedTag() {
        val bytes = region(configTag = 0x80000002)
        assertTrue(rejected(bytes).contains("unexpected chunk tag"))
    }

    @Test
    fun rejectsASizeThatRunsPastTheRegion() {
        val bytes = region(declaredAudioSize = 100_000)
        assertTrue(rejected(bytes).contains("past the region end"))
    }

    @Test
    fun rejectsARegionWithNoConfigChunk() {
        val bytes = region(audioTag = Kind2Chunks.TAG_AUDIO, configTag = Kind2Chunks.TAG_AUDIO)
        // Two audio chunks: no config, so the reader must refuse.
        assertTrue(rejected(bytes).contains("config"))
    }

    @Test
    fun rejectsARegionThatIsTooShort() {
        assertTrue(rejected(ByteArray(4)).contains("out of bounds"))
    }

    @Test
    fun rejectsRegionBoundsOutsideTheBuffer() {
        val bytes = region()
        val result = Kind2Chunks.read(bytes, 0, bytes.size + 10, 960)
        assertTrue(result is Kind2Chunks.Result.Rejected)
    }

    @Test
    fun zeroSampleCountDoesNotDivideByZero() {
        val audio = assertNotNull(ok(region(), samples = 0).audio)
        assertEquals(0, audio.impliedBitrate)
    }

    @Test
    fun descriptionNamesTheCodecAndBitrate() {
        val text = Kind2Chunks.describe(ok(region(audioSize = 209)))
        assertTrue(text.contains("codec 2"), text)
        assertTrue(text.contains("83.6 kbps"), text)
        assertTrue(text.contains("48000 Hz"), text)
    }

    @Test
    fun tagConstantsMatchTheCorpus() {
        assertEquals(0x80000001L, Kind2Chunks.TAG_CONFIG)
        assertEquals(0x80000004L, Kind2Chunks.TAG_AUDIO)
        assertEquals(24, Kind2Chunks.CONFIG_BYTES)
        assertEquals(63, Kind2Chunks.MAX_REGION_PADDING)
    }

    @Test
    fun audioIsNullWhenTheChunkIsAbsent() {
        // A config-only region is structurally valid even though nothing can play.
        val bytes = ByteArray(8 + 24)
        put32(bytes, 0, Kind2Chunks.TAG_CONFIG)
        put32(bytes, 4, 24L)

        val layout = (Kind2Chunks.read(bytes, 0, bytes.size, 960) as Kind2Chunks.Result.Ok).layout
        assertNull(layout.audio)
    }
}
