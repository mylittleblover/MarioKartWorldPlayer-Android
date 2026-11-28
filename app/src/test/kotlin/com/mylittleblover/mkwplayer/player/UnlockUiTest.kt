package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.unlock.ListeningProgress
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cup must be invisible until it is earned and its unlock ceremony completes (#24).
 *
 * That is the feature. An entry that appears locked, or a counter that reads zero before anything has
 * been heard, tells the user there is something to unlock and removes the surprise the whole stage
 * exists to deliver. Once the requirement is satisfied (via listening or the quiz), the ceremony
 * becomes ready and the persistent banner appears, while Special Cup & Rainbow Road stay hidden until
 * the ceremony plays to completion.
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

    // ---------------------------------------------------------------- hidden until ceremony completes (#24)

    @Test
    fun theCupIsInvisibleBeforeItIsEarned() {
        val ui = UnlockUi.from(fresh("A", "B"), offered = false)
        assertFalse(ui.visible, "an unearned cup must not appear in the list")
        assertFalse(ui.canEnter)
        assertFalse(ui.ceremonyReady)
        assertFalse(ui.shouldShowPersistentBanner)
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
    fun earningAllStagesMakesCeremonyReadyButKeepsCupHiddenUntilCeremonyCompletes() {
        val bridge = earned("A")
        val pendingUi = UnlockUi.from(bridge, offered = true)
        assertFalse(pendingUi.visible, "Special Cup stays hidden until the ceremony completes")
        assertNotNull(pendingUi.hiddenReason)
        assertTrue(pendingUi.canEnter, "user can enter the ceremony once earned")
        assertTrue(pendingUi.ceremonyReady)
        assertTrue(pendingUi.shouldShowPersistentBanner)
        assertTrue(pendingUi.offered)
        assertFalse(pendingUi.ceremonyCompleted)

        // Completing the ceremony officially unlocks Special Cup & clears the persistent banner.
        val unlock = SpecialCupUnlock(setOf("A")).withState(bridge.snapshot)
        val playing = unlock.withState(unlock.beginCeremony())
        val finished = playing.withState(playing.finishCeremony(reachedEnd = true))
        val completedUi = UnlockUi.from(finished, offered = true)
        assertTrue(completedUi.visible, "Special Cup becomes visible once ceremony completes")
        assertNull(completedUi.hiddenReason)
        assertTrue(completedUi.ceremonyCompleted)
        assertTrue(completedUi.canEnter, "completed ceremony can still be replayed")
        assertTrue(completedUi.ceremonyReady)
        assertFalse(completedUi.shouldShowPersistentBanner)
        assertTrue(completedUi.congratulationOwed)
    }

    @Test
    fun passingTheQuizMakesCeremonyReadyWithoutUnlockingBeforeCeremonyFinishes() {
        val bridge = fresh("A", "B").also { it.passQuiz() }
        val ui = UnlockUi.from(bridge, offered = false)
        assertTrue(ui.quizPassed)
        assertNull(ui.remaining)
        assertFalse(ui.visible, "passing the quiz must not unlock Special Cup before ceremony completes")
        assertNotNull(ui.hiddenReason)
        assertTrue(ui.canEnter)
        assertTrue(ui.ceremonyReady)
        assertTrue(ui.shouldShowPersistentBanner)
        assertTrue(ui.offered)
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
        assertFalse(ui.visible, "cup remains hidden while first-time ceremony is still playing")
        assertFalse(ui.shouldShowPersistentBanner, "persistent banner hides while ceremony is playing")
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
            specialUnlocked = true,
        )
        val ui = UnlockUi.from(
            ListeningProgress(SpecialCupUnlock(setOf("A"))).also { it.seed(completed) },
            offered = true,
        )
        assertTrue(ui.ceremonyCompleted)
        assertFalse(ui.ceremonyPlaying)
        assertTrue(ui.congratulationOwed)
        assertTrue(ui.visible)
        assertNull(ui.hiddenReason)
    }

    @Test
    fun aConsumedCongratulationsIsNotOwedAgain() {
        val completed = fresh("A").snapshot.copy(
            ceremony = SpecialCupUnlock.CeremonyPhase.COMPLETED,
            congratulated = true,
            specialUnlocked = true,
        )
        val ui = UnlockUi.from(
            ListeningProgress(SpecialCupUnlock(setOf("A"))).also { it.seed(completed) },
            offered = true,
        )
        assertFalse(ui.congratulationOwed)
        assertTrue(ui.visible)
    }
}
