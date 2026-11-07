package com.mylittleblover.mkwplayer.audio

/**
 * Turns a progress-bar fraction into a transport frame, safely.
 *
 * Seeking is the one transport action where a small arithmetic slip lands the
 * playhead somewhere absurd: past the end, before the start, or on a frame the
 * decoder cannot start from. Putting the conversion in one tested place keeps that
 * away from the UI, which only knows a 0..1 float.
 *
 * Frames are the engine's only time unit, so nothing here assumes a sample rate
 * except [frameToFraction], which needs one to compare against duration and takes it
 * as an argument.
 */
internal object SeekTarget {

    /**
     * Converts a progress fraction to a frame, clamped into `0..totalFrames`.
     *
     * A non-finite or out-of-range fraction is clamped rather than rejected: a drag
     * gesture can emit either while the bar is being moved, and refusing would make
     * the bar feel broken.
     */
    fun fractionToFrame(fraction: Float, totalFrames: Long): Long {
        if (totalFrames <= 0) return 0
        if (fraction.isNaN()) return 0
        val clamped = fraction.coerceIn(0f, 1f)
        return (clamped.toDouble() * totalFrames).toLong().coerceIn(0, totalFrames)
    }

    /**
     * Back-converts a frame to a fraction, for drawing the bar.
     *
     * Returns 0 for an unknown duration instead of NaN, which would propagate into
     * the progress indicator and blank it.
     */
    fun frameToFraction(frame: Long, totalFrames: Long): Float {
        if (totalFrames <= 0) return 0f
        if (frame <= 0) return 0f
        if (frame >= totalFrames) return 1f
        return (frame.toDouble() / totalFrames).toDouble().toFloat()
    }

    /**
     * True when a seek to [frame] is worth submitting.
     *
     * Seeking backwards is always allowed. Seeking forward is allowed anywhere the
     * decoder has data, which for this build is the whole file, so the only refused
     * target is one outside the track.
     */
    fun isAddressable(frame: Long, totalFrames: Long): Boolean =
        totalFrames > 0 && frame in 0..totalFrames

    /** Frame at a given position in seconds, clamped, for keyboard/test stepping. */
    fun secondsToFrame(seconds: Double, rate: Int, totalFrames: Long): Long {
        if (rate <= 0 || totalFrames <= 0) return 0
        if (!seconds.isFinite()) return 0
        val frames = (seconds * rate).toLong()
        return frames.coerceIn(0, totalFrames)
    }
}
