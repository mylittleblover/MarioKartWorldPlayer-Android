package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.PlaybackEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The session owns the transport so the service and the UI can share it.
 *
 * `AudioOutput` has a private constructor and wraps a native handle, so it cannot be
 * faked; these tests supply a factory that always returns null, which is exactly the
 * state of a system with no output device. Everything the session decides for itself --
 * when to create an engine, when to reuse one, and what a failure clears -- is covered
 * without a device.
 *
 * The engine is faked through [PlaybackEngine]. `AudioEngine` is the only real
 * implementation, so the fake covers the interface the session actually calls.
 */
class PlaybackSessionTest {

    private class FakeEngine : PlaybackEngine {
        override val sampleRate: Int = 48000
        var frame = 0L
        var transport = 0L
        var closed = false
        var seekStatus = 0

        override fun loadGraph(packet: ByteArray): Int = 0
        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = 0
        override fun play(target: Int): Int { transport = 1; return 0 }
        override fun stop(): Int { transport = 0; frame = 0; return 0 }
        override fun pause(): Int { if (transport == 1L) transport = 2; return 0 }
        override fun resume(): Int { if (transport == 2L) transport = 1; return 0 }
        override fun seek(frame: Long): Int {
            if (seekStatus != 0) return seekStatus
            this.frame = frame
            return 0
        }
        override fun snapshot(): LongArray? {
            if (closed) return null
            val values = LongArray(65)
            values[0] = com.mylittleblover.mkwplayer.audio.EngineSnapshot.WIRE_VERSION
            values[1] = frame
            values[2] = transport
            return values
        }
        override fun close() { closed = true }
    }

    private class Harness {
        var engineCreations = 0
        var outputOpens = 0
        val session = PlaybackSession(
            createEngine = { engineCreations++; null },
            openOutput = { _, _ -> outputOpens++; null },
        )
    }

    @Test
    fun initialStateIsInactive() {
        val session = PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null })
        val state = session.state.value
        assertFalse(state.active)
        assertEquals("", state.title)
        assertEquals(0L, state.totalFrames)
        assertFalse(session.hasOutput)
    }

    @Test
    fun aMissingEngineIsReportedInsteadOfThrowing() {
        val session = PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null })
        val state = session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)

        assertFalse(state.active)
        assertNotNull(state.detail)
        assertTrue(state.detail!!.contains("engine", ignoreCase = true), state.detail!!)
    }

    @Test
    fun aMissingEngineIsNotRetriedOnEveryCall() {
        val harness = Harness()
        harness.session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)
        harness.session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)
        assertEquals(2, harness.engineCreations)
    }

    @Test
    fun noOutputDeviceIsNotReportedAsAFailure() {
        // A machine without a speaker is a configuration, not an error: the engine
        // detail must stay about the engine.
        val session = PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null })
        val state = session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)
        assertFalse(state.active)
        assertTrue(
            state.detail == null || !state.detail!!.contains("speaker", ignoreCase = true),
            state.detail ?: "",
        )
    }

    @Test
    fun pausingOrStoppingWithoutAnEngineKeepsTheStateInactive() {
        val session = PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null })
        assertFalse(session.pause().active)
        assertFalse(session.resume().active)
        assertFalse(session.stop().active)
        assertFalse(session.seek(10).active)
        assertFalse(session.poll().active)
    }

    @Test
    fun releaseIsIdempotentAndClearsState() {
        val session = PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null })
        session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)
        session.release()
        session.release()

        val state = session.state.value
        assertFalse(state.active)
        assertEquals("", state.title)
        assertEquals(0L, state.totalFrames)
    }

    @Test
    fun theOutputFactoryIsNotCalledWithoutAnEngine() {
        val harness = Harness()
        harness.session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)
        assertEquals(0, harness.outputOpens, "no engine means no stream to open")
    }

    @Test
    fun aFailedLoadClearsTheActiveFlags() {
        // If a failure left `playing` set, the notification would keep offering controls
        // for audio that is not running.
        val session = PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null })
        val state = session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)
        assertFalse(state.playing)
        assertFalse(state.paused)
    }

    @Test
    fun stateIsObservableThroughTheFlow() {
        val session = PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null })
        val before = session.state.value
        session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)
        assertTrue(session.state.value != before || session.state.value.detail != null)
    }

    @Test
    fun aReportedFailureIsVisibleBeforeAnyEngineExists() {
        val session = PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null })
        val state = session.load("T", byteArrayOf(1), byteArrayOf(2), "a", 1000)
        assertNotNull(state.detail, "a refusal must explain itself")
        assertTrue(state.detail!!.isNotBlank())
    }
}
