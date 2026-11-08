package com.mylittleblover.mkwplayer.player

/**
 * What to do when the system audio focus changes.
 *
 * Android's focus rules are a system requirement, not a product choice, so the decision is
 * separated from the `AudioManager` callbacks that deliver it. That keeps the policy
 * testable on the JVM: every branch below has a wrong answer, and the wrong answer is
 * audible as two sources playing at once.
 *
 * The manager dispatches, this decides.
 */
internal object FocusPolicy {

    /** The focus event the platform reported, reduced to what the policy needs. */
    enum class Event {
        /** Focus granted, including a return from a transient loss. */
        GAIN,

        /** Focus lost for a while: a call, a navigation prompt. Resume after. */
        LOSS_TRANSIENT,

        /** Focus lost; another app owns audio for the foreseeable future. */
        LOSS,

        /** Another source wants to be heard. Ducking is permitted, not required. */
        LOSS_TRANSIENT_CAN_DUCK,

        /** The output route went away: headphones unplugged, Bluetooth dropped. */
        BECOMING_NOISY,
    }

    /** What the service should do. */
    enum class Action {
        /** Nothing to change. */
        NONE,

        /** Pause; resume automatically when focus returns. */
        PAUSE_RESUMABLE,

        /** Pause; stay paused until the user asks again. */
        PAUSE_FINAL,

        /** Resume, because focus returned to a session focus itself paused. */
        RESUME,
    }

    /**
     * Decides an action.
     *
     * @param event what the platform reported
     * @param playing whether the transport is currently playing
     * @param pausedByFocus whether this app was paused by an earlier focus loss. This is the
     *   only condition that justifies resuming without the user asking: resuming after a
     *   user-initiated pause would start audio nobody asked for.
     */
    fun decide(
        event: Event,
        playing: Boolean,
        pausedByFocus: Boolean = false,
    ): Action = when (event) {
        Event.GAIN -> if (pausedByFocus) Action.RESUME else Action.NONE

        Event.LOSS_TRANSIENT -> if (playing) Action.PAUSE_RESUMABLE else Action.NONE

        Event.LOSS -> if (playing) Action.PAUSE_FINAL else Action.NONE

        // Ducking is not implemented: the engine has no master-gain command, and the
        // property/RTPC route needs a graph that declares the parameter. Continuing at full
        // volume is explicitly permitted for this event, so that is what happens rather
        // than pretending to duck something. Recorded as a gap in and-24.
        Event.LOSS_TRANSIENT_CAN_DUCK -> Action.NONE

        // Unplugging headphones must stop audible output: continuing through the
        // loudspeaker is the most embarrassing audio bug a player can ship.
        Event.BECOMING_NOISY -> if (playing) Action.PAUSE_FINAL else Action.NONE
    }

    /** True when [action] should mark the session as paused by focus. */
    fun marksPausedByFocus(action: Action): Boolean = action == Action.PAUSE_RESUMABLE
}
