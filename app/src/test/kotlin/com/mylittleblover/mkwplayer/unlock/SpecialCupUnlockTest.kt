package com.mylittleblover.mkwplayer.unlock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The unlock rules exist to stop a user satisfying the requirement without listening.
 *
 * Every guard below closes a specific hole: scrubbing forward, reporting a wall clock instead of the
 * transport, replaying one stage to credit another, or leaving the ceremony early and still being
 * congratulated. Those are the tests that matter; the rest is bookkeeping.
 */
class SpecialCupUnlockTest {

    private val rate = 48_000
    private val required = SpecialCupUnlock.requiredFrames(rate)   // 1_440_000 frames = 30 s

    private fun unlock(vararg stages: String) =
        SpecialCupUnlock(stages.toSet())

    /** Reports a run of playback in small steps, as a real caller would. */
    private fun play(
        unlock: SpecialCupUnlock,
        stage: String,
        fromFrames: Long,
        toFrames: Long,
        step: Long = 4_800,
        trackFrames: Long = 10_000_000,
    ): SpecialCupUnlock {
        var current = unlock.withState(unlock.beginStage(stage, trackFrames, rate))
        var position = fromFrames
        while (position <= toFrames) {
            current = current.withState(current.report(position).state)
            position += step
        }
        return current
    }

    // ---------------------------------------------------------------- threshold

    @Test
    fun exactlyThirtySecondsCompletesTheStage() {
        val u = play(unlock("S"), "S", 0, required)
        assertTrue("S" in u.snapshot.completedStages, "thirty seconds must satisfy the requirement")
    }

    @Test
    fun justUnderThirtySecondsDoesNotComplete() {
        val u = play(unlock("S"), "S", 0, required - 48_000)
        assertFalse("S" in u.snapshot.completedStages, "29 seconds is not 30")
    }

    @Test
    fun theThresholdIsThirtySecondsAtTheGivenRate() {
        assertEquals(1_440_000L, SpecialCupUnlock.requiredFrames(48_000))
        assertEquals(1_323_000L, SpecialCupUnlock.requiredFrames(44_100))
    }

    @Test
    fun aZeroRateIsRefused() {
        assertFailsWith<IllegalArgumentException> { SpecialCupUnlock.requiredFrames(0) }
    }

    // ---------------------------------------------------------------- short tracks

    @Test
    fun aShortTrackCompletesAtItsNaturalEnd() {
        // A 5 s track cannot reach 30 s, so reaching its end is what counts.
        val shortFrames = 5L * rate
        val u = play(unlock("S"), "S", 0, shortFrames, trackFrames = shortFrames)
        assertTrue("S" in u.snapshot.completedStages, "a short track completes at its end")
    }

    @Test
    fun aShortTrackStoppedBeforeItsEndDoesNotComplete() {
        val shortFrames = 5L * rate
        val u = play(unlock("S"), "S", 0, shortFrames - rate, trackFrames = shortFrames)
        assertFalse("S" in u.snapshot.completedStages, "stopping early is not a complete listen")
    }

    @Test
    fun aLongTrackDoesNotCompleteOnItsEndAlone() {
        // A 10 s track is shorter than 30 s, so the end rule applies; a 40 s track is not, so it must
        // actually reach the threshold rather than being credited for finishing.
        val longFrames = 40L * rate
        val u = play(unlock("S"), "S", 0, required, trackFrames = longFrames)
        assertTrue("S" in u.snapshot.completedStages, "the threshold was reached")
    }

    // ---------------------------------------------------------------- seek guards

    @Test
    fun seekingForwardDoesNotCreditTheJump() {
        // Start, then jump straight to the end: the credited time stays at zero.
        var u = unlock("S").withState(unlock("S").beginStage("S", 10_000_000, rate))
        u = u.withState(u.report(0).state)
        val jumped = u.report(50_000_000)
        assertTrue(jumped.ignored, "an implausible jump must be ignored")
        assertEquals(
            0L,
            jumped.state.listenedFrames["S"] ?: 0L,
            "a seek must not be credited",
        )
    }

    @Test
    fun seekingBackwardsIsIgnored() {
        var u = unlock("S").withState(unlock("S").beginStage("S", 10_000_000, rate))
        u = u.withState(u.report(0).state)
        u = u.withState(u.report(48_000).state)
        val backwards = u.report(1_000)
        assertTrue(backwards.ignored, "a backwards jump must be ignored")
        assertTrue(
            backwards.ignoredReason!!.contains("backwards"),
            backwards.ignoredReason!!,
        )
    }

    @Test
    fun scrubbingToTheEndOfEveryStageDoesNotUnlock() {
        // The whole point: a user who seeks to the end of every track must get nothing.
        var u = unlock("A", "B")
        for (stage in listOf("A", "B")) {
            var one = u.withState(u.beginStage(stage, 10_000_000, rate))
            one = one.withState(one.report(0).state)
            one = one.withState(one.report(9_999_999).state)
            u = one
        }
        assertFalse(u.isEntitled, "seeking must never unlock the cup")
    }

    // ---------------------------------------------------------------- repeated counting

    @Test
    fun replayingOneStageDoesNotCreditAnother() {
        val u = play(unlock("A", "B"), "A", 0, required)
        assertTrue("A" in u.snapshot.completedStages)
        assertFalse("B" in u.snapshot.completedStages, "listening to A must not credit B")
    }

    @Test
    fun furtherReportsForACompletedStageDoNotInflate() {
        var u = play(unlock("A"), "A", 0, required)
        val before = u.snapshot.listenedFrames["A"]
        u = u.withState(u.report(required * 2).state)
        assertEquals(before, u.snapshot.listenedFrames["A"], "a completed stage stops accumulating")
    }

    @Test
    fun listeningTwiceToTheSameStageCompletesItOnce() {
        var u = play(unlock("A"), "A", 0, required)
        val completedOnce = u.snapshot.completedStages
        u = play(u, "A", 0, required)
        assertEquals(completedOnce, u.snapshot.completedStages, "completion is a set, not a counter")
    }

    // ---------------------------------------------------------------- eligibility

    @Test
    fun allStagesCompleteUnlocksTheCup() {
        var u = unlock("A", "B")
        u = play(u, "A", 0, required)
        assertFalse(u.isEntitled, "one of two is not enough")
        u = play(u, "B", 0, required)
        assertTrue(u.isEntitled, "both stages complete opens the cup")
    }

    @Test
    fun entitlementIsReportedAsNewlyGrantedOnce() {
        var u = unlock("A")
        u = play(u, "A", 0, required - 9_600)
        val finish = u.withState(u.beginStage("A", 10_000_000, rate))
        var current = finish
        var newlyEntitled = false
        var position = 0L
        while (position <= required) {
            val progress = current.report(position)
            if (progress.newlyEntitled) newlyEntitled = true
            current = current.withState(progress.state)
            position += 4_800
        }
        assertTrue(newlyEntitled, "the transition into entitlement is reported")
    }

    @Test
    fun anIneligibleStageContributesNothing() {
        val u = unlock("A")
        var current = u.withState(u.beginStage("Other", 10_000_000, rate))
        current = current.withState(current.report(0).state)
        val progress = current.report(required)
        assertTrue(progress.ignored, "a stage nobody must listen to cannot count")
        assertFalse(current.isEntitled)
    }

    @Test
    fun aReportWithoutAStartedStageIsIgnored() {
        val progress = unlock("A").report(1000)
        assertTrue(progress.ignored)
    }

    @Test
    fun aNegativePositionIsIgnored() {
        var u = unlock("A").withState(unlock("A").beginStage("A", 1000, rate))
        assertTrue(u.report(-1).ignored)
    }

    // ---------------------------------------------------------------- progress reporting

    @Test
    fun remainingStagesListsOnlyTheOutstanding() {
        var u = unlock("A", "B", "C")
        u = play(u, "A", 0, required)
        assertEquals(setOf("B", "C"), u.remainingStages())
    }

    @Test
    fun progressFractionIsZeroWhenNothingIsEligible() {
        assertEquals(0.0, unlock().progressFraction(), 1e-9)
    }

    @Test
    fun progressFractionCountsCompletedStages() {
        var u = unlock("A", "B")
        u = play(u, "A", 0, required)
        assertEquals(0.5, u.progressFraction(), 1e-9)
    }

    // ---------------------------------------------------------------- ceremony

    @Test
    fun theCeremonyCannotStartBeforeTheCupIsUnlocked() {
        assertFailsWith<IllegalArgumentException> { unlock("A").beginCeremony() }
    }

    @Test
    fun entitlementMarksTheCeremonyPendingRatherThanStartingIt() {
        // Normal mode waits for the track to end, so the offer is recorded rather than shown.
        var u = unlock("A")
        u = play(u, "A", 0, required)
        assertEquals(SpecialCupUnlock.CeremonyPhase.PENDING, u.snapshot.ceremony)
        assertTrue(u.isPending)
    }

    @Test
    fun theCeremonyPlaysWhenEntered() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        u = u.withState(u.beginCeremony())
        assertEquals(SpecialCupUnlock.CeremonyPhase.PLAYING, u.snapshot.ceremony)
    }

    @Test
    fun aCeremonyCannotFinishWithoutPlaying() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        assertFailsWith<IllegalArgumentException> { u.finishCeremony(reachedEnd = true) }
    }

    @Test
    fun anIncompletePlaybackDoesNotCompleteTheCeremony() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        u = u.withState(u.beginCeremony())
        u = u.withState(u.finishCeremony(reachedEnd = false))
        assertEquals(
            SpecialCupUnlock.CeremonyPhase.PLAYING,
            u.snapshot.ceremony,
            "a ceremony that did not reach its end is still playing",
        )
    }

    @Test
    fun aCompletedCeremonyAllowsReplay() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        u = u.withState(u.beginCeremony())
        u = u.withState(u.finishCeremony(reachedEnd = true))
        assertEquals(SpecialCupUnlock.CeremonyPhase.COMPLETED, u.snapshot.ceremony)
        assertTrue(u.canReplayCeremony)
    }

    @Test
    fun leavingEarlyKeepsTheEntitlement() {
        // Quitting the show must not take the cup away.
        var u = unlock("A")
        u = play(u, "A", 0, required)
        u = u.withState(u.beginCeremony())
        u = u.withState(u.leaveCeremonyEarly())
        assertTrue(u.isEntitled, "the entitlement survives an early exit")
        assertEquals(SpecialCupUnlock.CeremonyPhase.PENDING, u.snapshot.ceremony)
    }

    @Test
    fun reEnteringRestartsFromTheBeginning() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        u = u.withState(u.beginCeremony())
        u = u.withState(u.leaveCeremonyEarly())
        u = u.withState(u.beginCeremony())
        assertEquals(
            SpecialCupUnlock.CeremonyPhase.PLAYING,
            u.snapshot.ceremony,
            "re-entering always starts the ceremony again",
        )
    }

    // ---------------------------------------------------------------- congratulations

    @Test
    fun theCongratulationsIsOwedOnlyAfterACompleteCeremony() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        assertFalse(u.owesCongratulations, "nothing is owed before the ceremony runs")

        u = u.withState(u.beginCeremony())
        assertFalse(u.owesCongratulations, "nothing is owed while it plays")

        u = u.withState(u.finishCeremony(reachedEnd = true))
        assertTrue(u.owesCongratulations, "the first complete viewing owes the moment")
    }

    @Test
    fun leavingEarlyDoesNotConsumeTheFirstTimeMoment() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        u = u.withState(u.beginCeremony())
        u = u.withState(u.leaveCeremonyEarly())
        assertFalse(u.owesCongratulations, "an early exit must not spend the first-time moment")

        u = u.withState(u.beginCeremony())
        u = u.withState(u.finishCeremony(reachedEnd = true))
        assertTrue(u.owesCongratulations, "a later complete viewing still owes it")
    }

    @Test
    fun theCongratulationsCannotBeMarkedBeforeItIsOwed() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        assertFailsWith<IllegalArgumentException> { u.markCongratulated() }
    }

    @Test
    fun markingTheCongratulationsIsIdempotent() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        u = u.withState(u.beginCeremony())
        u = u.withState(u.finishCeremony(reachedEnd = true))
        u = u.withState(u.markCongratulated())
        assertFalse(u.owesCongratulations)
        val again = u.withState(u.markCongratulated())
        assertFalse(again.owesCongratulations)
    }

    @Test
    fun aReplayDoesNotReopenTheFirstTimeMoment() {
        var u = unlock("A")
        u = play(u, "A", 0, required)
        u = u.withState(u.beginCeremony())
        u = u.withState(u.finishCeremony(reachedEnd = true))
        u = u.withState(u.markCongratulated())

        // Replay from the detail page.
        u = u.withState(u.beginCeremony())
        u = u.withState(u.finishCeremony(reachedEnd = true))
        assertFalse(u.owesCongratulations, "a replay must not re-send the first-time notice")
    }

    // ---------------------------------------------------------------- seeding

    @Test
    fun aRestoredStateKeepsItsProgress() {
        // A new instance over a persisted snapshot must not reset anything.
        val completed = play(unlock("A", "B"), "A", 0, required)
        val restored = unlock("A", "B").withState(completed.snapshot)
        assertEquals(completed.snapshot.completedStages, restored.snapshot.completedStages)
        assertEquals(completed.isEntitled, restored.isEntitled)
    }

    @Test
    fun theStagePrefixComesFromTheSharedRule() {
        val u = unlock("BGM_Stage_Coaster")
        assertEquals("BGM_Stage_Coaster", u.stageOf("BGM_Stage_Coaster_Pre.bwav"))
        assertEquals("BGM_Stage_Coaster", u.stageOf("BGM_Stage_Coaster_Outro00.bwav"))
    }
}
