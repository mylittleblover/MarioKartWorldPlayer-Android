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
    /** True when the Special Cup and Rainbow Road are officially unlocked (after ceremony completion). */
    val visible: Boolean,
    /** Why the cup is not visible yet, for a detail view; null once it is. */
    val hiddenReason: String?,
    val completedCount: Int = 0,
    val eligibleCount: Int = 0,
    val ceremonyReady: Boolean = false,
    val shouldShowPersistentBanner: Boolean = false,
    val remainingStages: Set<String> = emptySet(),
    val stageSeconds: Map<String, Int> = emptyMap(),
    val quizPassed: Boolean = false,
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
                remainingStages = bridge.remainingStages(),
                offered = offered,
            )
        }

        /** Builds from a bare state, for a restored snapshot with no live transport. */
        fun from(unlock: SpecialCupUnlock, offered: Boolean): UnlockUi = build(
            state = unlock.snapshot,
            eligibleCount = unlock.eligibleCount,
            remainingStages = unlock.remainingStages(),
            offered = offered,
        )

        private fun build(
            state: SpecialCupUnlock.UnlockState,
            eligibleCount: Int,
            remainingStages: Set<String>,
            offered: Boolean,
        ): UnlockUi {
            val completed = state.completedStages.size
            val remaining = (eligibleCount - completed).coerceAtLeast(0)
            val progress = "$completed of $eligibleCount stage${if (eligibleCount == 1) "" else "s"} heard"
            val officiallyUnlocked = state.specialUnlocked
            val rate = state.currentSampleRate.takeIf { it > 0 } ?: 48_000
            val stageSeconds = state.listenedFrames.mapValues { (_, frames) ->
                (frames.coerceAtLeast(0L) / rate.toLong()).toInt()
                    .coerceIn(0, SpecialCupUnlock.REQUIRED_SECONDS)
            }

            return UnlockUi(
                progress = progress,
                remaining = if (remaining == 0 || state.quizPassed) null else "$remaining still to hear",
                ruleNotice = com.mylittleblover.mkwplayer.unlock.SpecialCupRules.describe(eligibleCount),
                offered = offered || state.ceremonyReady,
                ceremonyPlaying = state.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING,
                ceremonyCompleted = state.ceremony == SpecialCupUnlock.CeremonyPhase.COMPLETED,
                congratulationOwed = state.ceremony == SpecialCupUnlock.CeremonyPhase.COMPLETED &&
                    !state.congratulated,
                canEnter = state.ceremonyReady && state.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING,
                // Special Cup & Rainbow Road remain locked until the unlock ceremony completes (#24).
                visible = officiallyUnlocked,
                hiddenReason = if (officiallyUnlocked) null else UnlockPresentation.emptyLabel(),
                completedCount = completed,
                eligibleCount = eligibleCount,
                ceremonyReady = state.ceremonyReady,
                shouldShowPersistentBanner = state.shouldShowPersistentCeremonyBanner,
                remainingStages = remainingStages,
                stageSeconds = stageSeconds,
                quizPassed = state.quizPassed,
            )
        }
    }
}
