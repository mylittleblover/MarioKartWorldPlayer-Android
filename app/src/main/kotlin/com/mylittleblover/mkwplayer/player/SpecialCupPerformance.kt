package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow

/**
 * The Special Cup performance, as the screen sees it.
 *
 * A stage performance already exists as a driver; what this adds is the part that belongs to the Special
 * Cup specifically: which prelude to open with, and what to say when the perfomance cannot start.
 *
 * Kept separate from [StagePerformance] because a normal stage and the Special Cup start differently and
 * for different reasons. Folding a cup-specific rule into the general driver is how a screen ends up
 * offering the cup when it should not.
 */
internal object SpecialCupPerformance {

    /**
     * The prelude the Special Cup opens with.
     *
     * The short prelude, because the cup is a ceremony rather than a course: its opening is the announcement,
     * and the long prelude belongs to a stage that a listener has chosen to hear in full. This is a
     * presentation choice and is labelled as one -- the game's own choice for this cup has not been
     * established, and the pack format refuses to call a schedule measured for exactly this reason.
     */
    val PRELUDE: StagePerformance.Prelude get() = StagePerformance.Prelude.SHORT

    /** Why the cup cannot be performed, or null when it can. */
    fun blocker(eligible: Boolean, hasFlow: Boolean): String? = when {
        !hasFlow -> "No Special Cup audio is loaded. Import a soundtrack pack to perform it."
        !eligible -> "The Special Cup is not unlocked yet."
        else -> null
    }

    /**
     * The line shown while the cup performs.
     *
     * Never claims the performance reproduces the game's ceremony. The ceremony's timing is this app's
     * arrangement from measured parts, which is what [StageFlow.Provenance.PROVISIONAL] means.
     */
    fun provenanceLine(flow: StageFlow): String = if (flow.isProvisional) {
        "Arranged by this app from measured parts. Not a reproduction of the game's ceremony."
    } else {
        "Timing measured from the game."
    }

    /**
     * Whether the cup's card may be shown at all.
     *
     * A pass-through, deliberately: the real gate is `CupVisibility.mayReveal`, which every projection uses,
     * and this object must not add a second rule. A second rule is how the library and the map end up
     * disagreeing about whether the cup exists.
     */
    fun mayShow(visible: Boolean): Boolean = visible
}
