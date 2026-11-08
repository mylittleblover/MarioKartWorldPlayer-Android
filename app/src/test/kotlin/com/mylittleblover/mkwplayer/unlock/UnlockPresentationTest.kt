package com.mylittleblover.mkwplayer.unlock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wording is the only place two uncertainties can be told to the user, so it is tested like logic.
 *
 * If the provisional rule notice or the ceremony caveat is ever dropped, the app presents a guess as
 * the game's behaviour, and nothing else in the codebase would catch it.
 */
class UnlockPresentationTest {

    private val rate = 48_000
    private val required = SpecialCupUnlock.requiredFrames(rate)

    private fun earned(vararg stages: String): SpecialCupUnlock {
        var u = SpecialCupUnlock(stages.toSet())
        for (stage in stages) {
            u = u.withState(u.beginStage(stage, 10_000_000, rate))
            var position = 0L
            while (position <= required) {
                u = u.withState(u.report(position).state)
                position += rate
            }
        }
        return u
    }

    @Test
    fun theProgressLabelCountsHeardStages() {
        val u = earned("A", "B", "C").let { SpecialCupUnlock(setOf("A", "B", "C", "D", "E")).withState(it.snapshot) }
        assertEquals("3 of 5 stages heard", UnlockPresentation.progressLabel(u))
    }

    @Test
    fun aSingleStageIsSingular() {
        val u = SpecialCupUnlock(setOf("A"))
        assertEquals("0 of 1 stage heard", UnlockPresentation.progressLabel(u))
    }

    @Test
    fun theRemainingListNamesWhatIsLeft() {
        val u = earned("A").let { SpecialCupUnlock(setOf("A", "B", "C")).withState(it.snapshot) }
        val label = UnlockPresentation.remainingLabel(u)
        assertTrue(label != null && label.contains("B") && label.contains("C"), label ?: "null")
    }

    @Test
    fun nothingRemainingHasNoLabel() {
        assertNull(UnlockPresentation.remainingLabel(earned("A")))
    }

    @Test
    fun theOfferSaysTheCupWasHidden() {
        // A user never told it was hidden experiences it as an ordinary cup, which loses the feature.
        assertTrue(UnlockPresentation.offerLabel().contains("hidden", ignoreCase = true))
    }

    @Test
    fun theOfferSaysTheCupStaysOpenAfterLeaving() {
        assertTrue(UnlockPresentation.offerDetail().contains("stays open"), UnlockPresentation.offerDetail())
    }

    @Test
    fun theRuleNoticeCarriesTheProvisionalLabel() {
        val notice = UnlockPresentation.ruleNotice(SpecialCupUnlock(setOf("A", "B")))
        assertTrue(notice.contains("Provisional"), notice)
        assertTrue(notice.contains("not established"), notice)
    }

    @Test
    fun theCeremonyCaveatNamesWhatIsUnknown() {
        // The cue is this app's confirmed file; the game's own choice is unverified.
        val caveat = UnlockPresentation.CEREMONY_AUDIO_CAVEAT
        assertTrue(caveat.contains("not established"), caveat)
        assertTrue(caveat.contains("cue"), caveat)
    }

    @Test
    fun anEmptyLibrarySaysWhatToDoRatherThanShowingZero() {
        val label = UnlockPresentation.emptyLabel()
        assertTrue(label.contains("Add"), label)
    }
}
