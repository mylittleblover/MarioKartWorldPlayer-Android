package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seek through the transport state machine.
 *
 * The controller refuses a target outside the track instead of clamping, because the
 * UI already clamps through [SeekTarget]: a frame still out of range here means
 * something upstream is wrong and should surface as a failure, not be corrected
 * silently.
 */
class PlaybackControllerSeekTest {

    private class FakeEngine : PlaybackEngine {
        override val sampleRate: Int = 48000
        var frame = 0L
        var transport = 1L
        var seekStatus = 0
        var seekCalls = 0
        var closed = false

        override fun loadGraph(packet: ByteArray): Int = 0
        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = 0
        override fun play(target: Int): Int { transport = 1; return 0 }
        override fun stop(): Int { transport = 0; frame = 0; return 0 }
        override fun pause(): Int { transport = 2; return 0 }
        override fun resume(): Int { transport = 1; return 0 }
        override fun seek(frame: Long): Int {
            seekCalls++
            if (seekStatus != 0) return seekStatus
            this.frame = frame
            return 0
        }

        override fun snapshot(): LongArray? {
            if (closed) return null
            val values = LongArray(65)
            values[0] = EngineSnapshot.WIRE_VERSION
            values[1] = frame
            values[2] = transport
            return values
        }

        override fun close() { closed = true }
    }

    private val packet = byteArrayOf(1)
    private val audio = byteArrayOf(2)

    private fun started(engine: FakeEngine): PlaybackController {
        val controller = PlaybackController(engine)
        controller.load("T", packet, audio, "a.bwav")
        controller.setDuration(1_000_000)
        return controller
    }

    @Test
    fun seekMovesTheReportedPosition() {
        val engine = FakeEngine()
        val controller = started(engine)

        val state = controller.seek(500_000)
        assertTrue(state is PlaybackController.State.Playing, "got $state")
        assertEquals(500_000L, (state as PlaybackController.State.Playing).positionFrames)
        assertEquals(1, engine.seekCalls)
    }

    @Test
    fun seekToZeroIsAllowed() {
        val engine = FakeEngine()
        val controller = started(engine)
        val state = controller.seek(0)
        assertTrue(state is PlaybackController.State.Playing)
        assertEquals(0L, (state as PlaybackController.State.Playing).positionFrames)
    }

    @Test
    fun seekToTheLastFrameIsAllowed() {
        val engine = FakeEngine()
        val controller = started(engine)
        val state = controller.seek(1_000_000)
        assertTrue(state is PlaybackController.State.Playing)
    }

    @Test
    fun seekPastTheEndFailsWithoutCallingTheEngine() {
        val engine = FakeEngine()
        val controller = started(engine)

        val state = controller.seek(1_000_001)
        assertTrue(state is PlaybackController.State.Failed, "got $state")
        assertEquals(0, engine.seekCalls, "an out-of-range seek must not reach native")
    }

    @Test
    fun seekBeforeZeroFailsWithoutCallingTheEngine() {
        val engine = FakeEngine()
        val controller = started(engine)

        val state = controller.seek(-1)
        assertTrue(state is PlaybackController.State.Failed, "got $state")
        assertEquals(0, engine.seekCalls)
    }

    @Test
    fun engineSeekFailureIsReportedWithAReason() {
        val engine = FakeEngine()
        engine.seekStatus = 8   // RuntimeActive
        val controller = started(engine)

        val state = controller.seek(1000)
        assertTrue(state is PlaybackController.State.Failed, "got $state")
        assertTrue((state as PlaybackController.State.Failed).reason.isNotBlank())
    }

    @Test
    fun seekBeforeAnyTrackIsLoadedIsRefused() {
        val engine = FakeEngine()
        val controller = PlaybackController(engine)

        // No duration recorded yet, so nothing is addressable.
        val state = controller.seek(1000)
        assertTrue(state is PlaybackController.State.Failed, "got $state")
    }

    @Test
    fun seekWhilePausedStaysPaused() {
        val engine = FakeEngine()
        val controller = started(engine)
        controller.pause()

        val state = controller.seek(250_000)
        assertTrue(state is PlaybackController.State.Paused, "got $state")
        assertEquals(250_000L, (state as PlaybackController.State.Paused).positionFrames)
    }
}
