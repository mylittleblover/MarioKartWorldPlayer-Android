package com.mylittleblover.mkwplayer.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Focus policy is where "two things play at once" is either prevented or shipped.
 *
 * Each branch has a plausible wrong answer, so each is pinned:
 *
 *  - resuming after a user pause starts audio nobody asked for
 *  - not pausing on a route change blasts music through the loudspeaker
 *  - pausing permanently on a phone call leaves the player stuck
 */
class FocusPolicyTest {

    // ---------------------------------------------------------------- transient loss

    @Test
    fun aPhoneCallPausesAndIsResumable() {
        val action = FocusPolicy.decide(FocusPolicy.Event.LOSS_TRANSIENT, playing = true)
        assertEquals(FocusPolicy.Action.PAUSE_RESUMABLE, action)
        assertTrue(FocusPolicy.marksPausedByFocus(action))
    }

    @Test
    fun aPhoneCallWhileAlreadyStoppedChangesNothing() {
        assertEquals(
            FocusPolicy.Action.NONE,
            FocusPolicy.decide(FocusPolicy.Event.LOSS_TRANSIENT, playing = false),
        )
    }

    // ---------------------------------------------------------------- permanent loss

    @Test
    fun anotherPlayerTakingOverPausesPermanently() {
        val action = FocusPolicy.decide(FocusPolicy.Event.LOSS, playing = true)
        assertEquals(FocusPolicy.Action.PAUSE_FINAL, action)
        assertFalse(
            FocusPolicy.marksPausedByFocus(action),
            "a permanent loss must not auto-resume later",
        )
    }

    @Test
    fun aPermanentLossWhileStoppedChangesNothing() {
        assertEquals(
            FocusPolicy.Action.NONE,
            FocusPolicy.decide(FocusPolicy.Event.LOSS, playing = false),
        )
    }

    // ---------------------------------------------------------------- regression to gain

    @Test
    fun gainResumesOnlyWhatFocusItselfPaused() {
        assertEquals(
            FocusPolicy.Action.RESUME,
            FocusPolicy.decide(FocusPolicy.Event.GAIN, playing = false, pausedByFocus = true),
        )
    }

    @Test
    fun gainAfterAUserPauseDoesNotStartAudio() {
        assertEquals(
            FocusPolicy.Action.NONE,
            FocusPolicy.decide(FocusPolicy.Event.GAIN, playing = false, pausedByFocus = false),
        )
    }

    @Test
    fun gainWhileAlreadyPlayingDoesNothing() {
        assertEquals(
            FocusPolicy.Action.NONE,
            FocusPolicy.decide(FocusPolicy.Event.GAIN, playing = true, pausedByFocus = false),
        )
    }

    // ---------------------------------------------------------------- route change

    @Test
    fun unpluggingHeadphonesStopsTheLoudspeaker() {
        // The single most embarrassing bug available to an audio app.
        val action = FocusPolicy.decide(FocusPolicy.Event.BECOMING_NOISY, playing = true)
        assertEquals(FocusPolicy.Action.PAUSE_FINAL, action)
    }

    @Test
    fun unpluggingWhileStoppedChangesNothing() {
        assertEquals(
            FocusPolicy.Action.NONE,
            FocusPolicy.decide(FocusPolicy.Event.BECOMING_NOISY, playing = false),
        )
    }

    @Test
    fun aRouteChangeNeverAutoResumes() {
        // Putting the headphones back must not restart playback on its own.
        assertFalse(
            FocusPolicy.marksPausedByFocus(
                FocusPolicy.decide(FocusPolicy.Event.BECOMING_NOISY, playing = true),
            ),
        )
    }

    // ---------------------------------------------------------------- ducking

    @Test
    fun duckableLossDoesNotPause() {
        // Ducking is not implemented, and the platform permits continuing at full volume.
        // Pausing on every notification would be worse than not ducking.
        assertEquals(
            FocusPolicy.Action.NONE,
            FocusPolicy.decide(FocusPolicy.Event.LOSS_TRANSIENT_CAN_DUCK, playing = true),
        )
    }

    // ---------------------------------------------------------------- invariants

    @Test
    fun noEventEverResumesWithoutAPriorFocusPause() {
        for (event in FocusPolicy.Event.entries) {
            for (playing in listOf(true, false)) {
                val action = FocusPolicy.decide(event, playing, pausedByFocus = false)
                assertTrue(
                    action != FocusPolicy.Action.RESUME,
                    "$event with playing=$playing must not resume on its own",
                )
            }
        }
    }

    @Test
    fun onlyResumablePausesMarkTheSession() {
        for (action in FocusPolicy.Action.entries) {
            assertEquals(
                action == FocusPolicy.Action.PAUSE_RESUMABLE,
                FocusPolicy.marksPausedByFocus(action),
                "$action",
            )
        }
    }

    @Test
    fun everyEventIsDecided() {
        for (event in FocusPolicy.Event.entries) {
            for (playing in listOf(true, false)) {
                for (pausedByFocus in listOf(true, false)) {
                    // Throwing here would mean a focus event the service cannot handle.
                    FocusPolicy.decide(event, playing, pausedByFocus)
                }
            }
        }
    }
}
