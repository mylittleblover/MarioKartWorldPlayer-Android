package com.mylittleblover.mkwplayer.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BwavWaveformExtractorTest {

    private fun buildPcm16Bwav(
        samples: ShortArray,
        sampleRate: Int = 48_000,
        channels: Int = 1,
        dataOffset: Int = 128,
    ): ByteArray {
        val frames = samples.size
        val bytes = ByteArray(dataOffset + frames * 2 * channels)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        buf.putShort(4, 0xFEFF.toShort())
        buf.putShort(6, 1.toShort())
        buf.putShort(14, channels.toShort())

        // Channel 0 header (76 bytes starting at offset 16)
        buf.putShort(16 + 0, 0.toShort()) // codec kind 0 = PCM16
        buf.putInt(16 + 4, sampleRate)
        buf.putInt(16 + 8, frames)
        buf.putInt(16 + 12, frames)
        buf.putInt(16 + 52, dataOffset)

        for (i in 0 until frames) {
            buf.putShort(dataOffset + i * 2, samples[i])
        }
        return bytes
    }

    private fun buildDspAdpcmBwav(
        frames: Int = 32,
        sampleRate: Int = 48_000,
        dataOffset: Int = 128,
    ): ByteArray {
        val blocks = (frames + 13) / 14
        val bytes = ByteArray(dataOffset + blocks * 8)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        buf.putShort(4, 0xFEFF.toShort())
        buf.putShort(6, 1.toShort())
        buf.putShort(14, 1.toShort())

        buf.putShort(16 + 0, 1.toShort()) // codec kind 1 = DSP-ADPCM
        buf.putInt(16 + 4, sampleRate)
        buf.putInt(16 + 8, frames)
        buf.putInt(16 + 12, frames)
        // Predictor 0 coefficients: c0 = 2048 (1.0), c1 = 0
        buf.putShort(16 + 16, 2048.toShort())
        buf.putShort(16 + 18, 0.toShort())
        buf.putInt(16 + 52, dataOffset)

        for (blk in 0 until blocks) {
            val base = dataOffset + blk * 8
            bytes[base] = 0x05 // pred = 0, scale = 1 << 5 = 32
            for (b in 1..7) {
                // Alternating positive (+5) and negative (-6 -> 0xA) nibbles
                bytes[base + b] = 0x5A.toByte()
            }
        }
        return bytes
    }

    @Test
    fun getCachedMatchesDefaultBucketExtractKeyAndServesCachedInstance() {
        val dir = createTempDirectory("bwav_waveform_cache_default").toFile()
        try {
            val digest = "test-cache-default-digest-01"
            assertNull(BwavWaveformExtractor.getCached(digest))

            val samples = ShortArray(2400) { i ->
                when {
                    i < 1200 -> if (i % 2 == 0) 24_576 else -24_576
                    else -> if (i % 2 == 0) 8_192 else -8_192
                }
            }
            val file = File(dir, "default.bwav").apply { writeBytes(buildPcm16Bwav(samples)) }

            val extracted = BwavWaveformExtractor.extract(file, digest)
            assertNotNull(extracted)
            assertEquals(BwavWaveformExtractor.DEFAULT_BUCKETS, extracted.bucketCount)
            assertEquals(2400L, extracted.frames)
            assertEquals(48_000, extracted.sampleRate)

            // Default-arg getCached(digest) must hit the exact same cached entry.
            val cachedDefault = BwavWaveformExtractor.getCached(digest)
            assertNotNull(cachedDefault)
            assertSame(extracted, cachedDefault)

            // Explicit DEFAULT_BUCKETS matches too; a different bucket count misses.
            assertSame(extracted, BwavWaveformExtractor.getCached(digest, BwavWaveformExtractor.DEFAULT_BUCKETS))
            assertNull(BwavWaveformExtractor.getCached(digest, 64))

            // Even if the backing file is deleted, extract() and getCached() serve from cache.
            assertTrue(file.delete())
            assertSame(extracted, BwavWaveformExtractor.extract(file, digest))
            assertSame(extracted, BwavWaveformExtractor.getCached(digest))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun getCachedWithCustomBucketCountIsolatesCacheEntries() {
        val dir = createTempDirectory("bwav_waveform_cache_custom").toFile()
        try {
            val digest = "test-cache-custom-digest-02"
            val samples = ShortArray(128) { i -> if (i % 2 == 0) 28_000 else -28_000 }
            val file = File(dir, "custom.bwav").apply { writeBytes(buildPcm16Bwav(samples)) }

            assertNull(BwavWaveformExtractor.getCached(digest, 16))
            assertNull(BwavWaveformExtractor.getCached(digest, 32))

            val peaks16 = BwavWaveformExtractor.extract(file, digest, bucketCount = 16)
            assertNotNull(peaks16)
            assertEquals(16, peaks16.bucketCount)
            assertSame(peaks16, BwavWaveformExtractor.getCached(digest, 16))
            assertNull(BwavWaveformExtractor.getCached(digest, 32))
            assertNull(BwavWaveformExtractor.getCached(digest))

            val peaks32 = BwavWaveformExtractor.extract(file, digest, bucketCount = 32)
            assertNotNull(peaks32)
            assertEquals(32, peaks32.bucketCount)
            assertSame(peaks32, BwavWaveformExtractor.getCached(digest, 32))
            assertSame(peaks16, BwavWaveformExtractor.getCached(digest, 16))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun extractsAccuratePcm16AndDspAdpcmPeaks() {
        val dir = createTempDirectory("bwav_waveform_decode").toFile()
        try {
            // PCM16: first half loud (~0.75), second half quieter (~0.25) -> peak >= 0.7 so no auto-boost.
            val pcmSamples = ShortArray(256) { i ->
                if (i < 128) {
                    if (i % 2 == 0) 24_576 else -24_576 // +/- 0.75
                } else {
                    if (i % 2 == 0) 8_192 else -8_192 // +/- 0.25
                }
            }
            val pcmFile = File(dir, "pcm16.bwav").apply { writeBytes(buildPcm16Bwav(pcmSamples, sampleRate = 44_100)) }
            val pcmPeaks = BwavWaveformExtractor.extract(pcmFile, "test-pcm16-peaks-03", bucketCount = 8)
            assertNotNull(pcmPeaks)
            assertEquals(256L, pcmPeaks.frames)
            assertEquals(44_100, pcmPeaks.sampleRate)
            assertEquals(8, pcmPeaks.bucketCount)
            for (b in 0 until 4) {
                assertEquals(0.75f, pcmPeaks.maxPeaks[b], 0.005f)
                assertEquals(-0.75f, pcmPeaks.minPeaks[b], 0.005f)
            }
            for (b in 4 until 8) {
                assertEquals(0.25f, pcmPeaks.maxPeaks[b], 0.005f)
                assertEquals(-0.25f, pcmPeaks.minPeaks[b], 0.005f)
            }

            // DSP-ADPCM with non-multiple-of-14 frame count (32 frames = 2 full blocks + 4 samples).
            val adpcmFile = File(dir, "adpcm.bwav").apply { writeBytes(buildDspAdpcmBwav(frames = 32)) }
            val adpcmPeaks = BwavWaveformExtractor.extract(adpcmFile, "test-adpcm-peaks-04", bucketCount = 8)
            assertNotNull(adpcmPeaks)
            assertEquals(32L, adpcmPeaks.frames)
            assertEquals(48_000, adpcmPeaks.sampleRate)
            assertEquals(8, adpcmPeaks.bucketCount)
            assertTrue(adpcmPeaks.maxPeaks.maxOf { abs(it) } > 0f || adpcmPeaks.minPeaks.maxOf { abs(it) } > 0f)
            assertSame(adpcmPeaks, BwavWaveformExtractor.getCached("test-adpcm-peaks-04", 8))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidInputsWithoutCaching() {
        val dir = createTempDirectory("bwav_waveform_invalid").toFile()
        try {
            val missing = File(dir, "does_not_exist.bwav")
            assertNull(BwavWaveformExtractor.extract(missing, "test-invalid-missing-05"))
            assertNull(BwavWaveformExtractor.getCached("test-invalid-missing-05"))

            val opusStub = buildPcm16Bwav(ShortArray(16))
            ByteBuffer.wrap(opusStub).order(ByteOrder.LITTLE_ENDIAN).putShort(16 + 0, 2.toShort()) // kind=2 Opus
            val opusFile = File(dir, "opus.bwav").apply { writeBytes(opusStub) }
            assertNull(BwavWaveformExtractor.extract(opusFile, "test-invalid-opus-06"))
            assertNull(BwavWaveformExtractor.getCached("test-invalid-opus-06"))

            val validFile = File(dir, "valid.bwav").apply { writeBytes(buildPcm16Bwav(ShortArray(32) { 10_000 })) }
            assertNull(BwavWaveformExtractor.extract(validFile, "test-invalid-buckets-07", bucketCount = 0))
            assertNull(BwavWaveformExtractor.extract(validFile, "test-invalid-buckets-07", bucketCount = -4))
            assertNull(BwavWaveformExtractor.getCached("test-invalid-buckets-07", 0))
        } finally {
            dir.deleteRecursively()
        }
    }
}
