package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.unlock.ListeningProgress
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cup must be invisible until it is earned.
 *
 * That is the feature. An entry that appears locked, or a counter that reads zero before anything has
 * been heard, tells the user there is something to unlock and removes the surprise the whole stage
 * exists to deliver.
 */
class UnlockUiTest {

    private val rate = 48_000
    private val required = SpecialCupUnlock.requiredFrames(rate)

    private fun earned(vararg stages: String): ListeningProgress {
        val bridge = ListeningProgress(SpecialCupUnlock(stages.toSet()))
        for (stage in stages) {
            bridge.begin(stage, 10_000_000, rate)
            var position = 0L
            while (position <= required) {
                bridge.sample(position, playing = true)
                position += rate
            }
        }
        return bridge
    }

    private fun fresh(vararg stages: String) = ListeningProgress(SpecialCupUnlock(stages.toSet()))

    // ---------------------------------------------------------------- hidden until earned

    @Test
    fun theCupIsInvisibleBeforeItIsEarned() {
        val ui = UnlockUi.from(fresh("A", "B"), offered = false)
        assertFalse(ui.visible, "an unearned cup must not appear in the list")
        assertFalse(ui.canEnter)
    }

    @Test
    fun anUnearnedCupDoesNotAnnounceThatSomethingIsLocked() {
        // 'Locked' would tell the user a hidden thing exists, which defeats hiding it.
        val ui = UnlockUi.from(fresh("A"), offered = false)
        val text = listOfNotNull(ui.progress, ui.remaining, ui.hiddenReason).joinToString(" ")
        assertFalse(text.contains("Locked", ignoreCase = true), text)
        assertFalse(text.contains("locked", ignoreCase = true), text)
    }

    @Test
    fun theCupBecomesVisibleWhenEarned() {
        val ui = UnlockUi.from(earned("A"), offered = true)
        assertTrue(ui.visible, "the earned cup appears")
        assertNull(ui.hiddenReason)
    }

    @Test
    fun theCupIsNotEnterableWhilePlaying() {
        // Entering mid-ceremony would restart it, so the button must be unavailable.
        val bridge = earned("A")
        val playing = bridge.snapshot.copy(ceremony = SpecialCupUnlock.CeremonyPhase.PLAYING)
        val ui = UnlockUi.from(
            ListeningProgress(SpecialCupUnlock(setOf("A"))).also { it.seed(playing) },
            offered = true,
        )
        assertTrue(ui.ceremonyPlaying)
        assertFalse(ui.canEnter)
    }

    // ---------------------------------------------------------------- progress wording

    @Test
    fun theProgressCountsHeardStages() {
        val ui = UnlockUi.from(fresh("A", "B", "C"), offered = false)
        assertEquals("0 of 3 stages heard", ui.progress)
    }

    @Test
    fun aSingleStageIsSingular() {
        assertEquals("0 of 1 stage heard", UnlockUi.from(fresh("A"), offered = false).progress)
    }

    @Test
    fun theRemainingCountDisappearsWhenEverythingIsHeard() {
        assertNull(UnlockUi.from(earned("A"), offered = true).remaining)
    }

    @Test
    fun theRemainingCountIsShownWhileWorkIsLeft() {
        val bridge = earned("A")
        val withTwo = ListeningProgress(
            SpecialCupUnlock(setOf("A", "B", "C")),
        ).also { it.seed(bridge.snapshot) }
        assertTrue(UnlockUi.from(withTwo, offered = false).remaining!!.startsWith("2"))
    }

    // ---------------------------------------------------------------- the two caveats

    @Test
    fun theRuleNoticeIsAlwaysCarried() {
        // Not only when earned: the requirement is a choice from the first launch.
        val ui = UnlockUi.from(fresh("A"), offered = false)
        assertTrue(ui.ruleNotice.contains("Provisional"), ui.ruleNotice)
    }

    @Test
    fun theCeremonyCompletedStateIsDistinctFromPlaying() {
        val completed = fresh("A").snapshot.copy(
            ceremony = SpecialCupUnlock.CeremonyPhase.COMPLETED,
            congratulated = false,
        )
        val ui = UnlockUi.from(
            ListeningProgress(SpecialCupUnlock(setOf("A"))).also { it.seed(completed) },
            offered = true,
        )
        assertTrue(ui.ceremonyCompleted)
        assertFalse(ui.ceremonyPlaying)
        assertTrue(ui.congratulationOwed)
    }

    @Test
    fun aConsumedCongratulationsIsNotOwedAgain() {
        val completed = fresh("A").snapshot.copy(
            ceremony = SpecialCupUnlock.CeremonyPhase.COMPLETED,
            congratulated = true,
        )
        val ui = UnlockUi.from(
            ListeningProgress(SpecialCupUnlock(setOf("A"))).also { it.seed(completed) },
            offered = true,
        )
        assertFalse(ui.congratulationOwed)
    }
}
