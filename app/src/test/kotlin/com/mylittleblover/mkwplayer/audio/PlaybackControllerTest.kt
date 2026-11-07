package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Walks the transport state machine on the JVM, with a fake engine.
 *
 * The point is the transitions, not the audio. Two of them are subtle and have
 * already produced wrong conclusions in this project: Play returning Ok while
 * nothing starts, and a status-only check reading that as success.
 */
class PlaybackControllerTest {

    private class FakeEngine(
        var loadStatus: Int = 0,
        var bindStatus: Int = 0,
        var playStatus: Int = 0,
        var pauseStatus: Int = 0,
        var resumeStatus: Int = 0,
        var stopStatus: Int = 0,
    ) : PlaybackEngine {
        override val sampleRate: Int = 48000
        var closed = false
        var frame = 0L
        var transport = 0L   // 0 stopped, 1 playing, 2 paused

        override fun loadGraph(packet: ByteArray): Int = loadStatus
        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = bindStatus
        override fun play(target: Int): Int {
            if (playStatus == 0) transport = 1
            return playStatus
        }
        override fun stop(): Int {
            if (stopStatus == 0) { transport = 0; frame = 0 }
            return stopStatus
        }
        override fun pause(): Int {
            if (pauseStatus == 0 && transport == 1L) transport = 2
            return pauseStatus
        }
        override fun resume(): Int {
            if (resumeStatus == 0 && transport == 2L) transport = 1
            return resumeStatus
        }
        override fun snapshot(): LongArray? {
            if (closed) return null
            val packet = LongArray(65)
            packet[0] = EngineSnapshot.WIRE_VERSION
            packet[1] = frame
            packet[2] = transport
            packet[3] = if (transport == 1L) 1 else 0
            return packet
        }
        override fun close() { closed = true }
    }

    private fun controller(engine: PlaybackEngine = FakeEngine()) = PlaybackController(engine)

    private val packet = byteArrayOf(1, 2, 3)
    private val audio = byteArrayOf(4, 5, 6)

    @Test
    fun startsIdle() {
        assertEquals(PlaybackController.State.Idle, controller().state)
    }

    @Test
    fun loadRequestsPlayAndBecomesReady() {
        val state = controller().load("Coaster", packet, audio, "a.bwav")
        assertTrue(state is PlaybackController.State.Ready, "got $state")
        assertEquals("Coaster", (state as PlaybackController.State.Ready).title)
    }

    @Test
    fun loadFailureCarriesAReadableReason() {
        val state = controller(FakeEngine(loadStatus = 1)).load("X", packet, audio, "a.bwav")
        assertTrue(state is PlaybackController.State.Failed, "got $state")
        val reason = (state as PlaybackController.State.Failed).reason
        assertTrue(reason.isNotBlank())
        assertTrue(!reason.contains("Failed(reason="))
    }

    @Test
    fun bindFailureIsReportedSeparatelyFromLoadFailure() {
        val loadFail = controller(FakeEngine(loadStatus = 1)).load("X", packet, audio, "a.bwav")
        val bindFail = controller(FakeEngine(bindStatus = 11)).load("X", packet, audio, "a.bwav")
        val loadReason = (loadFail as PlaybackController.State.Failed).reason
        val bindReason = (bindFail as PlaybackController.State.Failed).reason
        assertTrue(loadReason != bindReason, "load and bind failures must be distinguishable")
    }

    @Test
    fun playFailureIsReported() {
        val state = controller(FakeEngine(playStatus = 1)).load("X", packet, audio, "a.bwav")
        assertTrue(state is PlaybackController.State.Failed)
    }

    @Test
    fun pollReportsPlayingWhileTheTransportAdvances() {
        val engine = FakeEngine()
        val c = controller(engine)
        c.load("T", packet, audio, "a.bwav")
        c.setDuration(48000)

        engine.frame = 12000
        val state = c.poll()
        assertTrue(state is PlaybackController.State.Playing, "got $state")
        assertEquals(12000L, (state as PlaybackController.State.Playing).positionFrames)
    }

    @Test
    fun pauseMovesToPausedAndResumeReturnsToPlaying() {
        val engine = FakeEngine()
        val c = controller(engine)
        c.load("T", packet, audio, "a.bwav")
        engine.frame = 5000

        assertTrue(c.pause() is PlaybackController.State.Paused)
        assertTrue(c.resume() is PlaybackController.State.Playing)
    }

    @Test
    fun stopResetsToStopped() {
        val engine = FakeEngine()
        val c = controller(engine)
        c.load("T", packet, audio, "a.bwav")
        c.setDuration(48000)
        engine.frame = 9000

        val state = c.stop()
        assertTrue(state is PlaybackController.State.Stopped, "got $state")
        assertEquals(48000L, (state as PlaybackController.State.Stopped).totalFrames)
    }

    @Test
    fun releaseClosesTheEngineExactlyOnceAndReturnsToIdle() {
        val engine = FakeEngine()
        val c = controller(engine)
        c.load("T", packet, audio, "a.bwav")
        c.release()
        assertTrue(engine.closed)
        assertEquals(PlaybackController.State.Idle, c.state)
        c.release()   // must not throw on a second release
    }

    @Test
    fun pollAfterCloseFailsRatherThanClaimingPlayback() {
        val engine = FakeEngine()
        val c = controller(engine)
        c.load("T", packet, audio, "a.bwav")
        engine.closed = true

        val state = c.poll()
        assertTrue(state is PlaybackController.State.Failed, "got $state")
    }

    @Test
    fun loadingWhilePlayingStopsThePreviousTrackFirst() {
        val engine = FakeEngine()
        val c = controller(engine)
        c.load("First", packet, audio, "a.bwav")
        engine.frame = 100
        c.poll()

        c.load("Second", packet, audio, "b.bwav")
        assertEquals(0L, engine.frame, "starting a new track must not continue the old position")
    }

    @Test
    fun durationSurvivesEveryLiveState() {
        val engine = FakeEngine()
        val c = controller(engine)
        c.load("T", packet, audio, "a.bwav")
        c.setDuration(1000)

        assertEquals(1000L, (c.poll() as PlaybackController.State.Playing).totalFrames)
        assertEquals(1000L, (c.pause() as PlaybackController.State.Paused).totalFrames)
        assertEquals(1000L, (c.resume() as PlaybackController.State.Playing).totalFrames)
        assertEquals(1000L, (c.stop() as PlaybackController.State.Stopped).totalFrames)
    }

    @Test
    fun everyFailureReasonIsNonEmpty() {
        val failures = listOf(1, 2, 6, 11)
        for (status in failures) {
            val state = controller(FakeEngine(loadStatus = status)).load("X", packet, audio, "a")
            val reason = (state as PlaybackController.State.Failed).reason
            assertTrue(reason.isNotBlank(), "status $status produced a blank reason")
        }
    }
}
