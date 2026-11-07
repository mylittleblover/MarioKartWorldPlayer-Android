package com.mylittleblover.mkwplayer.audio

/**
 * The engine operations a playback session needs, as an interface.
 *
 * [AudioEngine] implements this against the native handle. The interface exists so
 * the controller's state machine can be exercised on the JVM without a device: a
 * transport bug that only shows up when audio is running is expensive to find, and
 * most of the transitions have nothing to do with the native side.
 *
 * Kept deliberately narrow. Anything not used by a playback session should not be
 * here, or the fake would start modelling engine details that do not matter.
 */
internal interface PlaybackEngine {
    val sampleRate: Int

    fun loadGraph(packet: ByteArray): Int
    fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int
    fun play(target: Int): Int
    fun stop(): Int
    fun pause(): Int
    fun resume(): Int

    /** Seeks the transport to [frame] within the current source. */
    fun seek(frame: Long): Int

    /** Engine snapshot; null once closed. */
    fun snapshot(): LongArray?

    fun close()
}

/** Adapts the owning wrapper to the interface above. */
internal class NativePlaybackEngine(private val engine: AudioEngine) : PlaybackEngine {
    override val sampleRate: Int get() = engine.sampleRate
    override fun loadGraph(packet: ByteArray): Int = engine.loadGraph(packet)
    override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int =
        engine.bindBwav(assetIndex, bytes, assetId)
    override fun play(target: Int): Int = engine.play(target)
    override fun stop(): Int = engine.stop()
    override fun pause(): Int = engine.pause()
    override fun resume(): Int = engine.resume()
    override fun seek(frame: Long): Int = engine.seek(frame)
    override fun snapshot(): LongArray? = engine.snapshot()
    override fun close() = engine.close()
}
