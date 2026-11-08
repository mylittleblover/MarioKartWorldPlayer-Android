package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Special Cup is the one place in the app where a wrong start is more than a wrong sound: the cup's
 * whole point is that it is not available until it has been earned, and a screen that performs it anyway
 * undoes every gate underneath.
 */
class SpecialCupPerformanceTest {

    private fun flow(provisional: Boolean) = StageFlow(
        name = "SpecialCup",
        parts = listOf(
            StageFlow.Part(
                StageFlow.Role.Main, "Main.bwav", 1_000_000, 48_000, 2, null, null,
            ),
        ),
        timing = StageFlow.DEFAULT_TIMING,
        provenance = if (provisional) {
            StageFlow.Provenance.PROVISIONAL
        } else {
            StageFlow.Provenance.VERIFIED
        },
    )

    @Test
    fun theCupOpensWithItsShortPrelude() {
        assertEquals(StagePerformance.Prelude.SHORT, SpecialCupPerformance.PRELUDE)
    }

    @Test
    fun theCupCannotStartWithoutAudio() {
        val reason = SpecialCupPerformance.blocker(eligible = true, hasFlow = false)
        assertNotNull(reason)
        assertTrue(reason.contains("No Special Cup audio"), reason)
    }

    @Test
    fun theCupCannotStartWithoutEligibility() {
        // The gate that matters: audio present but not earned is still not performable.
        val reason = SpecialCupPerformance.blocker(eligible = false, hasFlow = true)
        assertNotNull(reason)
        assertTrue(reason.contains("not unlocked"), reason)
    }

    @Test
    fun missingAudioIsReportedBeforeEligibility() {
        // Both are wrong at once; naming the audio problem is more useful, because it is the one the user
        // can fix by importing a pack, and it does not reveal whether the cup is unlocked.
        val reason = SpecialCupPerformance.blocker(eligible = false, hasFlow = false)
        assertNotNull(reason)
        assertTrue(reason.contains("No Special Cup audio"), reason)
        assertTrue(!reason.contains("unlocked"), "the reason leaked the cup's state: $reason")
    }

    @Test
    fun anEligibleCupWithAudioHasNoBlocker() {
        assertNull(SpecialCupPerformance.blocker(eligible = true, hasFlow = true))
    }

    @Test
    fun aProvisionalPerformanceSaysItIsAnArrangement() {
        val line = SpecialCupPerformance.provenanceLine(flow(provisional = true))
        assertTrue(line.contains("Arranged"), line)
        assertTrue(line.contains("Not a reproduction"), line)
    }

    @Test
    fun aMeasuredPerformanceDoesNotClaimToBeAnArrangement() {
        val line = SpecialCupPerformance.provenanceLine(flow(provisional = false))
        assertTrue(!line.contains("Arranged"), line)
    }

    @Test
    fun theCupCardDoesNotAddASecondVisibilityRule() {
        // The gate is CupVisibility, which takes an UnlockUi it builds from progress. This object must not
        // introduce a parallel rule, so the check here is that it exposes none -- a second rule is how the
        // library and the map end up disagreeing about whether the cup exists.
        assertEquals(false, SpecialCupPerformance.mayShow(false))
        assertEquals(true, SpecialCupPerformance.mayShow(true))
    }
}
