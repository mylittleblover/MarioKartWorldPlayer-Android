package com.mylittleblover.mkwplayer.ui.unlock

import com.mylittleblover.mkwplayer.player.UnlockUi
import com.mylittleblover.mkwplayer.ui.art.CupArt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ceremony's rules, tested where they are decided rather than where they are drawn.
 *
 * Three of these would be invisible in a screenshot: a disabled pause button renders perfectly, a
 * zero-length ceremony draws a bar at 0 % either way, and the second congratulations dialog looks
 * exactly like the first. Only the state layer can be asked.
 */
class CeremonyStateTest {

    private fun ui(
        offered: Boolean = true,
        visible: Boolean = true,
        ceremonyPlaying: Boolean = false,
    ): UnlockUi = UnlockUi(
        progress = "1 of 1 stage heard",
        remaining = null,
        ruleNotice = "Provisional rule (every stage you have): 1 stage(s) to listen to.",
        offered = offered,
        ceremonyPlaying = ceremonyPlaying,
        ceremonyCompleted = false,
        congratulationOwed = false,
        canEnter = visible && !ceremonyPlaying,
        visible = visible,
        hiddenReason = if (visible) null else "No stages to hear yet. Add some music first.",
    )

    // ---------------------------------------------------------------- controls

    @Test
    fun everyControlIsDisabledDuringPlaybackExceptExit() {
        // The audio layer will not pause, seek, skip or change track during the ceremony. A screen
        // that offers any of them makes the user believe a control they do not have.
        val controls = controlsFor("PLAYING")
        assertFalse(controls.canPause, "pause must not exist while the ceremony plays")
        assertFalse(controls.canSeek, "seek must not exist while the ceremony plays")
        assertFalse(controls.canSkip, "skip must not exist while the ceremony plays")
        assertFalse(controls.canChangeTrack, "the track must not change while the ceremony plays")
        assertTrue(controls.canExit, "leaving is always allowed")
    }

    @Test
    fun exitIsAvailableInEveryPhase() {
        for (phase in listOf("NONE", "PENDING", "PLAYING", "COMPLETED")) {
            assertTrue(controlsFor(phase).canExit, "exit must be offered in $phase")
        }
    }

    @Test
    fun aStoppedCeremonyEnablesEveryControl() {
        assertEquals(
            CeremonyControls(
                canPause = true,
                canSeek = true,
                canSkip = true,
                canExit = true,
                canChangeTrack = true,
            ),
            controlsFor("NONE"),
            "nothing is playing, so nothing is withheld",
        )
    }

    @Test
    fun anUnknownPhaseIsTreatedAsNone() {
        // Defaulting to PLAYING would lock controls and warn about leaving a ceremony that is not
        // running; crashing would take the screen down over a string.
        assertEquals(controlsFor("NONE"), controlsFor("NOT_A_PHASE"), "an unknown name reads as NONE")
        assertEquals(
            ceremonyPhaseOf("NOT_A_PHASE"),
            ceremonyPhaseOf("NONE"),
            "an unknown name is NONE, not PLAYING",
        )
        assertEquals(controlsFor("NONE"), controlsFor("   "), "a blank name reads as NONE")
    }

    @Test
    fun phaseNamesIgnoreCaseAndSurroundingSpace() {
        // A case slip must not re-enable pause during real playback.
        assertFalse(controlsFor(" playing ").canPause, "a padded lower-case name is still PLAYING")
        assertTrue(controlsFor(" Completed ").canSeek)
    }

    @Test
    fun pendingAndCompletedEnableEveryControl() {
        for (phase in listOf("PENDING", "COMPLETED")) {
            assertEquals(controlsFor("NONE"), controlsFor(phase), "$phase has nothing playing")
        }
    }

    // ---------------------------------------------------------------- progress

    @Test
    fun progressLabelsForAMeasuredCeremony() {
        val progress = progressFor(
            positionFrames = 48_000L * 10,
            totalFrames = 48_000L * 20,
            sampleRate = 48_000,
        )
        assertEquals("0:10", progress.elapsed, "ten seconds in")
        assertEquals("0:20", progress.total, "twenty seconds long")
        assertEquals(0.5f, progress.fraction, "half way through")
    }

    @Test
    fun aZeroLengthCeremonyReadsAsUnknownAndNeverAsFinished() {
        // A zero-length ceremony reads as finished if zero is printed as a time.
        val progress = progressFor(positionFrames = 0, totalFrames = 0, sampleRate = 48_000)
        assertEquals("--:--", progress.elapsed, "an unmeasured length has no elapsed time")
        assertEquals("--:--", progress.total, "an unmeasured length has no total")
        assertEquals(0f, progress.fraction, "an unmeasured length has no progress")
        assertFalse(
            progress.elapsed.contains("00:00") || progress.total.contains("00:00"),
            "a zero-length ceremony must not read as 00:00",
        )
        assertFalse(
            progress.elapsed.contains("0:00") || progress.total.contains("0:00"),
            "a zero-length ceremony must not read as 0:00",
        )
    }

    @Test
    fun theFractionClampsAtTheEndRatherThanOverflowing() {
        val progress = progressFor(
            positionFrames = 48_000L * 60,
            totalFrames = 48_000L * 20,
            sampleRate = 48_000,
        )
        assertEquals(1f, progress.fraction, "a position past the end fills the bar, not more")
    }

    @Test
    fun theProgressBarIsAlwaysReadOnly() {
        // The flag exists so a future caller adding a seek callback has to change this file first.
        val progress = progressFor(1_000, 48_000, 48_000)
        assertTrue(progress.isReadOnly, "the ceremony's bar reports a position, it never accepts one")
    }

    @Test
    fun anUnknownSampleRateLeavesTheLengthUnusable() {
        // Frames without a rate cannot be printed as a time, and a total with an unknown elapsed
        // time would be a bar moving under labels that deny knowing where it is.
        val progress = progressFor(positionFrames = 1_000, totalFrames = 48_000, sampleRate = 0)
        assertEquals("--:--", progress.total, "no rate means no length")
        assertEquals("--:--", progress.elapsed, "no rate means no position")
        assertEquals(0f, progress.fraction, "no rate means no fraction")
    }

    // ---------------------------------------------------------------- congratulations

    @Test
    fun theCongratulationIsShownForAFreshCompletion() {
        assertTrue(
            shouldShowCongratulation("COMPLETED", congratulated = false),
            "a first-time completion is congratulated",
        )
    }

    @Test
    fun aConsumedCongratulationIsNotShownAgain() {
        assertFalse(
            shouldShowCongratulation("COMPLETED", congratulated = true),
            "the flag is persisted, so a restart must not re-send the dialog",
        )
    }

    @Test
    fun aReplayDoesNotResendTheFirstTimeDialog() {
        // A replay reaches COMPLETED again. Showing the dialog there would claim the cup is newly
        // unlocked, which is false the second time.
        assertFalse(
            shouldShowCongratulation("COMPLETED", congratulated = true),
            "a replay must not re-show the first-time dialog",
        )
    }

    @Test
    fun theCongratulationNeedsACompletedCeremony() {
        for (phase in listOf("NONE", "PENDING", "PLAYING")) {
            assertFalse(
                shouldShowCongratulation(phase, congratulated = false),
                "the first-time dialog belongs to a finished ceremony, not $phase",
            )
        }
    }

    @Test
    fun theCeremonyViewShowsTheCongratulationOnlyWhenOwed() {
        val owed = viewFor(
            phase = "COMPLETED",
            positionFrames = 48_000L * 20,
            totalFrames = 48_000L * 20,
            sampleRate = 48_000,
            congratulationOwed = true,
            cupName = "Special Cup",
        )
        assertTrue(owed.showCongratulation, "an owed congratulations is announced")

        val consumed = viewFor(
            phase = "COMPLETED",
            positionFrames = 48_000L * 20,
            totalFrames = 48_000L * 20,
            sampleRate = 48_000,
            congratulationOwed = false,
            cupName = "Special Cup",
        )
        assertFalse(consumed.showCongratulation, "a consumed congratulations is not announced again")
    }

    // ---------------------------------------------------------------- the notice

    @Test
    fun theNoticeIsNullBeforeTheCupIsEarned() {
        // The notification is the reveal. A cup that has not been earned has nothing to announce.
        assertNull(
            noticeFor(ui(offered = false, visible = false)),
            "nothing is announced before the cup is offered and visible",
        )
    }

    @Test
    fun theNoticeIsNullWithoutAnUnlock() {
        assertNull(noticeFor(null), "no cup state means no notification")
    }

    @Test
    fun theNoticeIsNullOnceTheOfferIsConsumed() {
        // `offered` is the caller's record of having told the user; a restart must not re-announce.
        assertNull(noticeFor(ui(offered = false)), "a consumed offer is not announced again")
    }

    @Test
    fun theNoticeActionLabelDoesNotRevealTheCup() {
        val notice = noticeFor(ui())
        assertNotNull(notice, "an offered, visible cup is announced")
        assertFalse(
            notice.actionLabel.contains("unlock", ignoreCase = true),
            "the button must not promise an unlock the user has not entered: ${notice.actionLabel}",
        )
        assertFalse(
            notice.actionLabel.contains("special", ignoreCase = true),
            "the button must not name the hidden cup: ${notice.actionLabel}",
        )
        assertFalse(
            notice.message.contains("Special Cup"),
            "the message must not name the hidden cup: ${notice.message}",
        )
    }

    @Test
    fun theNoticeIsDismissibleAndCarriesAnAction() {
        val notice = noticeFor(ui())
        assertNotNull(notice)
        assertTrue(notice.dismissible, "closing only hides the notification; the cup stays earned")
        assertTrue(notice.actionLabel.isNotBlank(), "the notification needs an action")
    }

    @Test
    fun theNoticeIsNotShownWhileTheCeremonyPlays() {
        // The user is already there; an "open" action would be a control that does nothing.
        assertNull(
            noticeFor(ui(ceremonyPlaying = true)),
            "the notification would be a no-op during the ceremony",
        )
    }

    // ---------------------------------------------------------------- the panel

    @Test
    fun theExitHintIsPresentDuringPlaybackOnly() {
        fun hint(phase: String): String? = viewFor(
            phase = phase,
            positionFrames = 0,
            totalFrames = 48_000L * 20,
            sampleRate = 48_000,
            congratulationOwed = false,
            cupName = "Special Cup",
        ).exitHint

        assertNotNull(hint("PLAYING"), "leaving mid-ceremony costs something, so it is stated")
        for (phase in listOf("NONE", "PENDING", "COMPLETED")) {
            assertNull(hint(phase), "leaving $phase costs nothing, so there is no warning")
        }
    }

    @Test
    fun theCeremonyCarriesTheHiddenCupsEmblem() {
        // The ceremony is the one place the hidden cup may be shown before it is entered: by then it
        // has already been earned, and the panel is only ever reached through the offer.
        val view = viewFor(
            phase = "PLAYING",
            positionFrames = 0,
            totalFrames = 0,
            sampleRate = 48_000,
            congratulationOwed = false,
            cupName = "Special Cup",
        )
        assertEquals(CupArt.special.drawable, view.emblem, "the panel shows the hidden cup's emblem")
        assertEquals(controlsFor("PLAYING"), view.controls, "the view carries the phase's controls")
        assertEquals("Special Cup", view.title, "the cup's name is the title")
    }

    @Test
    fun theCeremonyTitleFallsBackWhenTheCupNameIsBlank() {
        // An empty heading is indistinguishable from a rendering failure.
        val view = viewFor(
            phase = "NONE",
            positionFrames = 0,
            totalFrames = 0,
            sampleRate = 48_000,
            congratulationOwed = false,
            cupName = "   ",
        )
        assertEquals(CupArt.special.label, view.title, "a blank name falls back to the cup's label")
    }
}
