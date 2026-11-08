package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.AudioEngine

/**
 * Stream-level measurements that make an audio transition assertion repeatable.
 *
 * "The buffers were not all zeros" is a weak claim: a transition can silence a track, drop its
 * level by 20 dB, or restart it from the top and still pass. What distinguishes those from real
 * playback is measurable from the samples alone, so the measurements live here, in one tested
 * place, instead of being computed inline in device tests.
 *
 * Everything is plain arithmetic over `FloatArray` blocks, so it runs on the JVM as well.
 */
internal object AudioLevel {

    /** Root-mean-square of an interleaved block; 0 for an empty block. */
    fun rms(block: FloatArray): Double {
        if (block.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in block) sum += sample.toDouble() * sample
        return Math.sqrt(sum / block.size)
    }

    /** Peak absolute sample. */
    fun peak(block: FloatArray): Double = block.maxOfOrNull { kotlin.math.abs(it).toDouble() } ?: 0.0

    /** True when every sample is exactly zero. */
    fun isSilent(block: FloatArray): Boolean = block.all { it == 0f }

    /**
     * Ratio of two RMS levels as a linear gain; 1.0 means unchanged.
     *
     * Returns [Double.POSITIVE_INFINITY] when [after] is compared against silence, which the
     * callers treat as a failure rather than a very loud result.
     */
    fun gainRatio(before: Double, after: Double): Double =
        if (before <= 0.0) Double.POSITIVE_INFINITY else after / before

    /**
     * Compares a level before and after a transition.
     *
     * Returns true when the second is within [floor]..[ceiling] times the first. A transition
     * that legitimately fades may drop below one; what it must not do is vanish entirely, which
     * is what a failed handover looks like from the outside.
     */
    fun levelHeld(
        before: Double,
        after: Double,
        floor: Double = 0.05,
        ceiling: Double = 20.0,
    ): Boolean {
        if (before <= 0.0) return after > 0.0
        val ratio = after / before
        return ratio in floor..ceiling
    }

    /**
     * Renders [blocks] blocks of [frames] and reports the aggregate statistics.
     *
     * A null render is reported as [RenderResult.failedAt] rather than throwing, so a test can
     * say which block failed instead of only that something did.
     */
    data class RenderResult(
        val blocks: Int,
        val silentBlocks: Int,
        val peak: Double,
        val meanRms: Double,
        /** Index of the first block whose render returned null, or -1. */
        val failedAt: Int,
    ) {
        val allSilent: Boolean get() = blocks > 0 && silentBlocks == blocks
        val anyAudio: Boolean get() = peak > 0.0
        val failed: Boolean get() = failedAt >= 0
    }

    fun render(
        engine: AudioEngine,
        blocks: Int,
        frames: Int = 512,
    ): RenderResult {
        var silent = 0
        var peak = 0.0
        var rmsSum = 0.0
        var counted = 0
        var failedAt = -1

        for (index in 0 until blocks) {
            val block = engine.render(frames = frames)
            if (block == null) {
                failedAt = index
                break
            }
            if (isSilent(block)) silent++
            peak = maxOf(peak, peak(block))
            rmsSum += rms(block)
            counted++
        }

        return RenderResult(
            blocks = counted,
            silentBlocks = silent,
            peak = peak,
            meanRms = if (counted > 0) rmsSum / counted else 0.0,
            failedAt = failedAt,
        )
    }
}
