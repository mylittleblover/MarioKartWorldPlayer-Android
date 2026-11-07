package com.mylittleblover.mkwplayer.audio

import com.mylittleblover.player2.platform.audio.NativeAudioLibrary

/**
 * Owns one engine handle for the lifetime of a playback session.
 *
 * The engine's contract is unusually strict and worth restating where callers
 * will see it:
 *  - every control call belongs to the thread that created the handle
 *  - `destroyEngine` must run exactly once
 *  - a source can only be bound while no output consumer is leased
 *
 * Nothing here adds scheduling. It only makes misuse hard: the handle cannot
 * escape as a raw Long, and [close] is idempotent because a destroyed native
 * handle is a dangling pointer, not an error code.
 */
internal class AudioEngine private constructor(private val handle: Long) : PlaybackEngine {

    private var closed = false

    /** Exposed only for constructing the output, which must reference the engine. */
    internal fun nativeHandle(): Long = if (closed) 0 else handle

    override val sampleRate: Int
        get() = if (closed) 0 else NativeAudioLibrary.engineSampleRate(handle)

    /** Transport rate is fixed at 48000 until a graph declares otherwise. */
    val transportRate: Int
        get() = NativeAudioLibrary.transportSampleRate()

    override fun loadGraph(packet: ByteArray): Int =
        if (closed) CLOSED else NativeAudioLibrary.loadCompiledGraph(handle, packet)

    override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int =
        if (closed) {
            CLOSED
        } else {
            NativeAudioLibrary.bindSource(
                handle, assetIndex, PROVIDER_BWAV, bytes, PREPARATION_PREPARED,
                assetId, null, null, null,
            )
        }

    fun loadLinearBwav(bytes: ByteArray, assetId: String, loop: Boolean): Int =
        if (closed) {
            CLOSED
        } else {
            NativeAudioLibrary.loadLinear(
                handle, PROVIDER_BWAV, bytes, loop, PREPARATION_PREPARED,
                assetId, null, null, null,
            )
        }

    /**
     * Submits Play.
     *
     * The target is not a detail: with a graph loaded it must name a segment,
     * while a linear runtime only accepts [PLAY_LINEAR]. Passing the wrong one
     * returns Ok on the graph path with no segment started, so a caller that
     * only checks the status will see success and hear silence.
     */
    override fun play(target: Int): Int =
        if (closed) CLOSED else NativeAudioLibrary.command(handle, COMMAND_PLAY, target, -1, 0.0, -1L)

    override fun stop(): Int =
        if (closed) CLOSED else NativeAudioLibrary.command(handle, COMMAND_STOP, -1, -1, 0.0, -1L)

    override fun pause(): Int =
        if (closed) CLOSED else NativeAudioLibrary.command(handle, COMMAND_PAUSE, -1, -1, 0.0, -1L)

    override fun resume(): Int =
        if (closed) CLOSED else NativeAudioLibrary.command(handle, COMMAND_RESUME, -1, -1, 0.0, -1L)

    /** Transport seek in source frames. Only valid while a source is bound. */
    override fun seek(frame: Long): Int =
        if (closed) CLOSED else NativeAudioLibrary.seek(handle, frame)

    fun render(frames: Int): FloatArray? {
        if (closed) return null
        val buffer = FloatArray(frames * 2)
        val status = NativeAudioLibrary.render(handle, buffer, frames)
        return if (status == STATUS_OK) buffer else null
    }

    override fun snapshot(): LongArray? = if (closed) null else NativeAudioLibrary.snapshot(handle)

    /** Releases the engine. Safe to call repeatedly; the native delete runs once. */
    override fun close() {
        if (closed) return
        closed = true
        NativeAudioLibrary.destroyEngine(handle)
    }

    companion object {
        /** `native.bwav` is the provider id the engine's probe reports for BWAV. */
        const val PROVIDER_BWAV = "native.bwav"

        /** SourcePreparation.Prepared: decode up front instead of streaming. */
        const val PREPARATION_PREPARED = 0

        /** EngineCommandKind.Play */
        const val COMMAND_PLAY = 0

        /** EngineCommandKind.Stop */
        const val COMMAND_STOP = 1

        /** EngineCommandKind.Pause */
        const val COMMAND_PAUSE = 2

        /** EngineCommandKind.Resume */
        const val COMMAND_RESUME = 3

        /** The only Play target a linear runtime accepts. */
        const val PLAY_LINEAR = -1

        /** EngineStatus.Ok */
        const val STATUS_OK = 0

        /** Returned by this wrapper once the handle is gone; never a native code. */
        const val CLOSED = -1

        /** Returns null when the native allocator refuses, which means OOM. */
        fun create(): AudioEngine? {
            val handle = NativeAudioLibrary.createEngine()
            return if (handle == 0L) null else AudioEngine(handle)
        }
    }
}
