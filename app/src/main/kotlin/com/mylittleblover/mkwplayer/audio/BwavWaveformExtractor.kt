package com.mylittleblover.mkwplayer.audio

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs

/**
 * Lightweight streaming BWAV amplitude peak extractor for the Flow Editor's zoomable waveform.
 *
 * Reads only channel 0 payload sequentially in fixed 8-byte DSP-ADPCM blocks (codec kind 1) or
 * planar PCM16 (codec kind 0), accumulating min/max normalized buckets without allocating full PCM
 * buffers. Returns null gracefully for codec kind 2 (Opus) or missing files.
 */
internal object BwavWaveformExtractor {

    data class WaveformPeaks(
        val digest: String,
        val frames: Long,
        val sampleRate: Int,
        val minPeaks: FloatArray,
        val maxPeaks: FloatArray,
    ) {
        val bucketCount: Int get() = minPeaks.size
    }

    const val DEFAULT_BUCKETS = 1200

    private const val MAX_CACHE_ENTRIES = 48
    private val cache = object : LinkedHashMap<String, WaveformPeaks>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WaveformPeaks>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private fun cacheKey(digest: String, bucketCount: Int): String = "$digest:$bucketCount"

    fun getCached(digest: String, bucketCount: Int = DEFAULT_BUCKETS): WaveformPeaks? =
        synchronized(cache) { cache[cacheKey(digest, bucketCount)] }

    fun extract(
        file: File,
        digest: String,
        bucketCount: Int = DEFAULT_BUCKETS,
    ): WaveformPeaks? {
        if (bucketCount <= 0) return null
        val key = cacheKey(digest, bucketCount)
        synchronized(cache) { cache[key] }?.let { return it }
        if (!file.isFile) return null
        val extracted = runCatching { decodeFromFile(file, digest, bucketCount) }.getOrNull() ?: return null
        synchronized(cache) { cache[key] = extracted }
        return extracted
    }

    private fun decodeFromFile(
        file: File,
        digest: String,
        bucketCount: Int,
    ): WaveformPeaks? {
        BufferedInputStream(FileInputStream(file), 32 * 1024).use { input ->
            val fileHeader = readExact(input, 16) ?: return null
            if (fileHeader[0] != 'B'.code.toByte() ||
                fileHeader[1] != 'W'.code.toByte() ||
                fileHeader[2] != 'A'.code.toByte() ||
                fileHeader[3] != 'V'.code.toByte()
            ) return null
            val version = u16(fileHeader, 4)
            if (version != 0xFEFF) return null
            val channels = u16(fileHeader, 14)
            if (channels < 1 || channels > 64) return null

            val ch0 = readExact(input, 76) ?: return null
            val kind = u16(ch0, 0)
            val rate = u32(ch0, 4).toInt()
            val frames = u32(ch0, 12)
            if (rate <= 0 || frames <= 0L || kind > 1) return null

            val startOffset = u32(ch0, 52)
            val headerBytesConsumed = 16L + 76L
            if (startOffset < headerBytesConsumed) return null

            val skipBytes = startOffset - headerBytesConsumed
            skipFully(input, skipBytes)

            val minPeaks = FloatArray(bucketCount) { 0f }
            val maxPeaks = FloatArray(bucketCount) { 0f }

            if (kind == 0) {
                val pcmBytes = ByteArray(4096)
                var frameIndex = 0L
                while (frameIndex < frames) {
                    val remainingFrames = (frames - frameIndex).coerceAtMost((pcmBytes.size / 2).toLong()).toInt()
                    val needed = remainingFrames * 2
                    var read = 0
                    while (read < needed) {
                        val n = input.read(pcmBytes, read, needed - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read < 2) break
                    val sampleCount = read / 2
                    for (i in 0 until sampleCount) {
                        val s = s16(pcmBytes, i * 2) / 32768f
                        val bucket = ((frameIndex * bucketCount) / frames).toInt().coerceIn(0, bucketCount - 1)
                        if (s < minPeaks[bucket]) minPeaks[bucket] = s
                        if (s > maxPeaks[bucket]) maxPeaks[bucket] = s
                        frameIndex++
                    }
                }
            } else {
                // Nintendo DSP-ADPCM (kind == 1)
                val coefs = IntArray(16) { n -> s16(ch0, 16 + n * 2) }
                var previous = s16(ch0, 70)
                var older = s16(ch0, 72)
                val block = ByteArray(8)
                var frameIndex = 0L
                while (frameIndex < frames) {
                    val headerByte = input.read()
                    if (headerByte < 0) break
                    block[0] = headerByte.toByte()
                    val samplesInBlock = (frames - frameIndex).coerceAtMost(14L).toInt()
                    val dataBytesNeeded = (samplesInBlock + 1) / 2
                    var got = 0
                    while (got < dataBytesNeeded) {
                        val n = input.read(block, 1 + got, dataBytesNeeded - got)
                        if (n <= 0) break
                        got += n
                    }
                    // Consume padding bytes of full 8-byte block if present
                    val trailingPad = 7 - dataBytesNeeded
                    if (trailingPad > 0) {
                        skipFully(input, trailingPad.toLong())
                    }
                    if (got < dataBytesNeeded) break

                    val predictor = (headerByte ushr 4) and 0x0F
                    if (predictor >= 8) break
                    val scale = 1 shl (headerByte and 0x0F)
                    val c0 = coefs[predictor * 2]
                    val c1 = coefs[predictor * 2 + 1]

                    for (within in 0 until samplesInBlock) {
                        val packed = block[1 + (within / 2)].toInt() and 0xFF
                        var nibble = if (within % 2 != 0) (packed and 0x0F) else (packed ushr 4)
                        if (nibble >= 8) nibble -= 16
                        val sum = nibble.toLong() * scale * 2048L +
                            c0.toLong() * previous +
                            c1.toLong() * older +
                            1024L
                        val rounded = if (sum >= 0L) sum / 2048L else -((-sum + 2047L) / 2048L)
                        val sample = rounded.coerceIn(-32768L, 32767L).toInt()
                        older = previous
                        previous = sample
                        val normalized = sample / 32768f
                        val bucket = ((frameIndex * bucketCount) / frames).toInt().coerceIn(0, bucketCount - 1)
                        if (normalized < minPeaks[bucket]) minPeaks[bucket] = normalized
                        if (normalized > maxPeaks[bucket]) maxPeaks[bucket] = normalized
                        frameIndex++
                    }
                }
            }

            // Soft auto-normalize only if peak is extremely quiet, otherwise keep faithful dynamic headroom.
            var peak = 0f
            for (i in 0 until bucketCount) {
                val p = maxOf(abs(minPeaks[i]), abs(maxPeaks[i]))
                if (p > peak) peak = p
            }
            if (peak > 0.001f && peak < 0.7f) {
                val boost = (0.9f / peak).coerceAtMost(2.5f)
                for (i in 0 until bucketCount) {
                    minPeaks[i] = (minPeaks[i] * boost).coerceIn(-1f, 1f)
                    maxPeaks[i] = (maxPeaks[i] * boost).coerceIn(-1f, 1f)
                }
            }

            return WaveformPeaks(
                digest = digest,
                frames = frames,
                sampleRate = rate,
                minPeaks = minPeaks,
                maxPeaks = maxPeaks,
            )
        }
    }

    private fun readExact(input: BufferedInputStream, length: Int): ByteArray? {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n <= 0) return null
            read += n
        }
        return out
    }

    private fun skipFully(input: BufferedInputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                if (input.read() < 0) break
                remaining--
            }
        }
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Long =
        ((bytes[at].toInt() and 0xFF).toLong()) or
            ((bytes[at + 1].toInt() and 0xFF).toLong() shl 8) or
            ((bytes[at + 2].toInt() and 0xFF).toLong() shl 16) or
            ((bytes[at + 3].toInt() and 0xFF).toLong() shl 24)

    private fun s16(bytes: ByteArray, at: Int): Int {
        val v = u16(bytes, at)
        return if (v < 32768) v else v - 65536
    }
}
