package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.unlock.ListeningProgress
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock
import com.mylittleblover.mkwplayer.unlock.UnlockPresentation

/**
 * Special Cup state as the screen sees it.
 *
 * Every string here comes from `UnlockPresentation`, so the provisional rule notice and the ceremony
 * caveat cannot be lost by the UI rewriting the wording.
 *
 * `hidden` is what keeps the feature honest on the first launch: the cup must not appear in the normal
 * cup list before it is earned, and it must not appear to be missing either -- so the screen shows
 * nothing rather than an empty or locked entry.
 */
internal data class UnlockUi(
    val progress: String,
    val remaining: String?,
    val ruleNotice: String,
    val offered: Boolean,
    val ceremonyPlaying: Boolean,
    val ceremonyCompleted: Boolean,
    val congratulationOwed: Boolean,
    val canEnter: Boolean,
    /** True when the cup should appear in the normal list at all. */
    val visible: Boolean,
    /** Why the cup is not visible yet, for a detail view; null once it is. */
    val hiddenReason: String?,
) {
    companion object {
        /**
         * Builds the view state.
         *
         * [offered] is passed in rather than read from the unlock's phase, because the notification is
         * a separate decision: the rules say the cup is ready, the app decides whether the user has
         * been told. Merging them would make the notification fire again after a restart.
         */
        fun from(bridge: ListeningProgress, offered: Boolean): UnlockUi {
            return build(
                state = bridge.snapshot,
                eligibleCount = bridge.eligibleCount,
                offered = offered,
            )
        }

        /** Builds from a bare state, for a restored snapshot with no live transport. */
        fun from(unlock: SpecialCupUnlock, offered: Boolean): UnlockUi = build(
            state = unlock.snapshot,
            eligibleCount = unlock.eligibleCount,
            offered = offered,
        )

        private fun build(
            state: SpecialCupUnlock.UnlockState,
            eligibleCount: Int,
            offered: Boolean,
        ): UnlockUi {
            val completed = state.completedStages.size
            val remaining = (eligibleCount - completed).coerceAtLeast(0)
            val progress = "$completed of $eligibleCount stage${if (eligibleCount == 1) "" else "s"} heard"

            return UnlockUi(
                progress = progress,
                remaining = if (remaining == 0) null else "$remaining still to hear",
                ruleNotice = com.mylittleblover.mkwplayer.unlock.SpecialCupRules.describe(eligibleCount),
                offered = offered,
                ceremonyPlaying = state.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING,
                ceremonyCompleted = state.ceremony == SpecialCupUnlock.CeremonyPhase.COMPLETED,
                congratulationOwed = state.ceremony == SpecialCupUnlock.CeremonyPhase.COMPLETED &&
                    !state.congratulated,
                canEnter = state.entitled && state.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING,
                // Hidden until earned: appearing early would remove the surprise, and showing a locked
                // entry would announce that there is something to unlock.
                visible = state.entitled,
                hiddenReason = if (state.entitled) null else UnlockPresentation.emptyLabel(),
            )
        }
    }
}
