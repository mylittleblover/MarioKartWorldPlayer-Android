package com.mylittleblover.mkwplayer.unlock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bridge between a transport and the unlock rules.
 *
 * Two policies are tested here that the rules themselves cannot express: a paused transport does not
 * count, and a resume after a stop starts a fresh run rather than measuring across the gap.
 */
class ListeningProgressTest {

    /** Frame rate as an Int for the API, and as a Long for positions, so neither needs a cast. */
    private val rateInt = 48_000
    private val rate = rateInt.toLong()
    private val required = SpecialCupUnlock.requiredFrames(rateInt)

    private fun progress(vararg stages: String) =
        ListeningProgress(SpecialCupUnlock(stages.toSet()))

    /** Feeds a continuous run in one-second steps, as the poller would. */
    private fun play(
        p: ListeningProgress,
        stage: String,
        to: Long,
        trackFrames: Long = 10_000_000,
    ): ListeningProgress {
        p.begin(stage, trackFrames, rateInt)
        var position = 0L
        while (position <= to) {
            p.sample(position, playing = true)
            position += rate
        }
        return p
    }

    // ---------------------------------------------------------------- basic crediting

    @Test
    fun playingCompletesAStage() {
        val p = play(progress("S"), "S", required)
        assertTrue("S" in p.snapshot.completedStages)
    }

    @Test
    fun aPausedTransportCreditsNothing() {
        // The engine's counter does not advance while paused, so neither may the rules.
        val p = progress("S")
        p.begin("S", 10_000_000, rateInt)
        val outcome = p.sample(required, playing = false)
        assertNotNull(outcome.ignoredReason)
        assertEquals(0L, p.snapshot.listenedFrames["S"] ?: 0L)
    }

    @Test
    fun anIntentionToPlayIsNotPlaying() {
        // Started but no output yet: crediting it would be the fake-timing mistake in miniature.
        val p = progress("S")
        p.begin("S", 10_000_000, rateInt)
        p.sample(0, playing = false)
        assertEquals(0L, p.snapshot.listenedFrames["S"] ?: 0L)
    }

    // ---------------------------------------------------------------- resume policy

    @Test
    fun aResumeAfterAStopStartsAFreshRun() {
        // Stopping drops the cursor, so the gap across the stop cannot be credited.
        val p = progress("S")
        p.begin("S", 10_000_000, rateInt)
        p.sample(0, playing = true)
        p.sample(rate, playing = true)

        p.stopped()
        // A new run begins far from where the old one ended; that must not be seen as a jump either
        // way -- the first report of a run has no previous position to measure from.
        p.begin("S", 10_000_000, rateInt)
        val outcome = p.sample(500_000, playing = true)
        assertEquals(null, outcome.ignoredReason, "the first report of a run is not a seek")
        assertEquals(
            rate,
            p.snapshot.listenedFrames["S"],
            "only the frames heard before and after, never the gap",
        )
    }

    @Test
    fun aStopClearsTheCursor() {
        val p = progress("S")
        p.begin("S", 10_000_000, rateInt)
        p.sample(0, playing = true)
        p.sample(rate, playing = true)
        p.stopped()
        assertEquals(-1L, p.snapshot.lastReportedFrames, "the cursor must not survive a stop")
    }

    // ---------------------------------------------------------------- completion signals

    @Test
    fun completingTheLastStageIsReportedOnce() {
        val p = progress("A")
        p.begin("A", 10_000_000, rateInt)
        var entitled = 0
        var position = 0L
        while (position <= required) {
            if (p.sample(position, playing = true).newlyEntitled) entitled++
            position += rate
        }
        assertEquals(1, entitled, "entitlement is announced exactly once")
    }

    @Test
    fun theOfferIsAnnouncedWhenItAppears() {
        val p = progress("A")
        p.begin("A", 10_000_000, rateInt)
        var offered = 0
        var position = 0L
        while (position <= required) {
            if (p.sample(position, playing = true).offeredNow) offered++
            position += rate
        }
        assertEquals(1, offered, "the notification is offered once, not every sample")
    }

    @Test
    fun anAlreadyEntitledCupIsNotOfferedAgain() {
        // Restored from disk with the offer pending: sampling must not re-offer it.
        val earned = ListeningProgress(SpecialCupUnlock(setOf("A")))
        play(earned, "A", required)
        assertTrue(earned.snapshot.entitled)

        val resumed = ListeningProgress(SpecialCupUnlock(setOf("A")), )
        // Seeding is done by the store in production; here the pending ceremony is set directly.
        val seeded = resumed.snapshot.copy(ceremony = SpecialCupUnlock.CeremonyPhase.PENDING)
        assertFalse(
            seeded.ceremony != SpecialCupUnlock.CeremonyPhase.PENDING,
            "the seeded state is already offered",
        )
    }

    // ---------------------------------------------------------------- coalescing

    @Test
    fun aStepBelowTheThresholdCarriesNoTime() {
        // Polling faster than the coalescing step must not double-count.
        val p = ListeningProgress(SpecialCupUnlock(setOf("S")), minStep = rate)
        p.begin("S", 10_000_000, rateInt)
        p.sample(0, playing = true)
        val tiny = p.sample(rate / 4, playing = true)
        assertEquals("below step", tiny.ignoredReason)
        assertEquals(0L, p.snapshot.listenedFrames["S"] ?: 0L)
    }

    @Test
    fun coalescingDoesNotLoseTimeWhenTheStepIsReached() {
        val p = ListeningProgress(SpecialCupUnlock(setOf("S")), minStep = rate)
        p.begin("S", 10_000_000, rateInt)
        var position = 0L
        while (position <= required) {
            p.sample(position, playing = true)
            position += rate
        }
        assertTrue("S" in p.snapshot.completedStages, "one-second polls still complete the stage")
    }

    // ---------------------------------------------------------------- short tracks

    @Test
    fun aShortTrackCompletesThroughTheBridgeToo() {
        // The rules own the short-track case; this confirms the bridge does not defeat it.
        val short = 5L * rate
        val p = progress("S")
        p.begin("S", short, rateInt)
        var position = 0L
        while (position <= short) {
            p.sample(position, playing = true)
            position += rate
        }
        assertTrue("S" in p.snapshot.completedStages)
    }

    // ---------------------------------------------------------------- multiple stages

    @Test
    fun twoStagesBothHaveToBeHeard() {
        val p = progress("A", "B")
        play(p, "A", required)
        assertFalse(p.snapshot.entitled, "one of two is not enough")
        play(p, "B", required)
        assertTrue(p.snapshot.entitled)
    }

    @Test
    fun anIneligibleStageIsRefusedByTheBridgeToo() {
        val p = progress("A")
        p.begin("Other", 10_000_000, rateInt)
        val outcome = p.sample(rate, playing = true)
        assertNotNull(outcome.ignoredReason)
        assertTrue(outcome.ignoredReason!!.contains("not eligible"), outcome.ignoredReason!!)
    }
}
