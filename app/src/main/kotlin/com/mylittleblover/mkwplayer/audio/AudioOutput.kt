package com.mylittleblover.mkwplayer.audio

import com.mylittleblover.player2.platform.audio.NativeAudioLibrary

/**
 * Thin Kotlin owner for the native audio output.
 *
 * Ownership rules come straight from the engine's contract and are enforced
 * here rather than assumed by callers:
 *  - one engine has at most one output consumer at a time
 *  - mutations, binding and seek require the consumer to be stopped
 *  - each handle is destroyed exactly once
 *
 * This class does not decode, schedule or resample. The native Engine owns all
 * audio semantics; anything here that looked like policy would be a second
 * implementation of it.
 */
internal class AudioOutput private constructor(
    private val engine: Long,
    private val handle: Long,
    val sampleRate: Int,
) {
    private var closed = false

    companion object {
        /**
         * Creates and starts the device output for [engine].
         *
         * Returns null when the device cannot be opened, which is a normal
         * condition on emulators and on devices with no audio route. Callers must
         * treat null as "no device output" and fall back to control rendering,
         * not as a fatal error.
         */
        fun open(engine: Long, sampleRate: Int): AudioOutput? {
            if (engine == 0L) return null
            val handle = NativeAudioLibrary.createOutput(engine)
            if (handle == 0L) return null
            if (!NativeAudioLibrary.startOutput(handle, sampleRate)) {
                NativeAudioLibrary.destroyOutput(handle)
                return null
            }
            return AudioOutput(engine, handle, sampleRate)
        }
    }

    /** Bytes-per-frame the callback writes: stereo interleaved float. */
    val channelCount: Int = 2

    /**
     * True once the stream reports a non-zero device rate.
     *
     * `outputSnapshot` returns `[rate, lastError, rebuilds, callbacks]`; a zero
     * rate means the device never opened, not that audio is silent.
     */
    fun isRunning(): Boolean = !closed && (snapshotRate() ?: 0) > 0

    private fun snapshotRate(): Int? =
        NativeAudioLibrary.outputSnapshot(handle)?.takeIf { it.isNotEmpty() }?.get(0)?.toInt()

    /** Oboe-reported fields: rate, last error, rebuild count, callback count. */
    fun snapshot(): LongArray? = if (closed) null else NativeAudioLibrary.outputSnapshot(handle)

    /**
     * Callback timing packet: `[wire, capacity, captured, total, missed,
     * clockFailures, clipped, saturated, cpu p50/p95/p99/p999/max, wall p50/p95/
     * p99/p999/max, xruns, budgetNanos]`.
     *
     * Returns null once closed, or when the native side has no packet. Callers pass
     * this to [TimingDiagnosis.read], which refuses an unexpected wire version.
     */
    fun timingSnapshot(): LongArray? =
        if (closed) null else NativeAudioLibrary.outputTimingSnapshot(handle)

    /**
     * Rebuilds the stream after a device change. Returns false when the device
     * refused; the caller should then stop and reopen rather than render into a
     * dead stream.
     */
    fun rebuild(rate: Int): Boolean = !closed && NativeAudioLibrary.rebuildOutput(handle, rate)

    /** Stops and releases the consumer. Safe to call more than once. */
    fun close() {
        if (closed) return
        closed = true
        NativeAudioLibrary.stopOutput(handle)
        NativeAudioLibrary.destroyOutput(handle)
    }
}
