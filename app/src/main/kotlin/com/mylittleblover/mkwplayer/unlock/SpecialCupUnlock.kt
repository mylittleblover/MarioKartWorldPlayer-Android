package com.mylittleblover.mkwplayer.unlock

import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery

/**
 * Tracks listening progress toward the hidden Special Cup, and owns the unlock ceremony's state.
 *
 * The cup is locked until every eligible stage has been listened to *effectively*. "Effectively" is
 * the whole difficulty: several obvious implementations would let a user satisfy the requirement
 * without hearing anything, so each guard below exists to close one of them.
 *
 * Pure Kotlin and no Android types, because the rules are the product and must be testable without a
 * device. The caller feeds playback progress; this decides whether it counts.
 */
internal class SpecialCupUnlock(
    /** Stage prefixes that must all be completed before the cup opens. */
    private val eligibleStages: Set<String>,
    private val state: UnlockState = UnlockState.initial(),
) {

    /** How much continuous listening counts as having heard a track. */
    companion object {
        /** The threshold from the approved specification. */
        const val REQUIRED_SECONDS = 30

        /**
         * Most frames a single report may credit.
         *
         * Without this, one report advancing by a large-but-plausible amount -- say 9,999,999 frames
         * of a ten-minute track -- would be credited in full, and a user could scrub to the end of
         * every stage and unlock the cup without hearing anything. The earlier length-based check
         * was not enough because it only rejected jumps larger than the whole track.
         *
         * Callers are expected to report at least once a second; anything faster is capped, so the
         * bound costs nothing in normal use and closes the scrub path.
         */
        const val MAX_CREDIT_SECONDS = 1

        /** Frames needed at [sampleRate] to satisfy the requirement. */
        fun requiredFrames(sampleRate: Int): Long {
            require(sampleRate > 0) { "sample rate must be positive, got $sampleRate" }
            return REQUIRED_SECONDS.toLong() * sampleRate
        }
    }

    /**
     * Persisted progress. Deliberately plain data with no behaviour so a later layer can serialise
     * it; everything that decides is on [SpecialCupUnlock].
     */
    data class UnlockState(
        /** Frames credited per stage prefix. */
        val listenedFrames: Map<String, Long>,
        /** Stages that have met the requirement, including short tracks played to the end. */
        val completedStages: Set<String>,
        /** True once the cup is permanently open. */
        val entitled: Boolean,
        /** Ceremony phase. */
        val ceremony: CeremonyPhase,
        /** True once the congratulations dialog has been shown. */
        val congratulated: Boolean,
        /** The stage currently being credited, so a seek can be detected against it. */
        val currentStage: String?,
        /** Frames reported on the previous call for [currentStage]. */
        val lastReportedFrames: Long,
        /** Total frames of the current track, or 0 when unknown. */
        val currentTrackFrames: Long,
        val currentSampleRate: Int,
    ) {
        companion object {
            fun initial(): UnlockState = UnlockState(
                listenedFrames = emptyMap(),
                completedStages = emptySet(),
                entitled = false,
                ceremony = CeremonyPhase.NONE,
                congratulated = false,
                currentStage = null,
                lastReportedFrames = -1,
                currentTrackFrames = 0,
                currentSampleRate = 0,
            )
        }
    }

    /**
     * The unlock ceremony's phases.
     *
     * Explicit rather than a set of booleans because the order matters: a ceremony cannot be marked
     * complete before it has played, and a replay must not reset the first-time flag.
     */
    enum class CeremonyPhase {
        /** Not running. */
        NONE,

        /** Offered to the user but not entered. The top notification is showing. */
        PENDING,

        /** Playing the "Special Cup Appears" audio. Pause and seek are disabled here. */
        PLAYING,

        /** Played to the end at least once. Replays are allowed from the detail page. */
        COMPLETED,
    }

    /** Result of reporting progress, so the caller knows whether anything changed. */
    data class Progress(
        val state: UnlockState,
        /** True when this call completed a stage it had not completed before. */
        val newlyCompleted: Boolean,
        /** True when this call opened the cup. */
        val newlyEntitled: Boolean,
        /** True when the report was rejected because it looked like a seek or was malformed. */
        val ignored: Boolean,
        /** Why a report was ignored; null when it counted. */
        val ignoredReason: String? = null,
    )

    /** Current progress, for display. */
    val snapshot: UnlockState get() = state

    /** True when the cup is open. */
    val isEntitled: Boolean get() = state.entitled

    /** True when every eligible stage is complete but the ceremony has not been entered. */
    val isPending: Boolean get() = state.ceremony == CeremonyPhase.PENDING

    /** Stage prefixes that are still outstanding. */
    fun remainingStages(): Set<String> = eligibleStages - state.completedStages

    /** Fraction of stages completed, 0..1. Returns 0 when no stage is eligible rather than NaN. */
    fun progressFraction(): Double =
        if (eligibleStages.isEmpty()) 0.0 else state.completedStages.size.toDouble() / eligibleStages.size

    /**
     * Begins crediting a track.
     *
     * Must be called when playback of a stage starts or restarts, because the seek guard compares
     * against the previous reported position and would otherwise see the new track's opening frames
     * as a backwards jump.
     */
    fun beginStage(stage: String, trackFrames: Long, sampleRate: Int): UnlockState {
        require(stage.isNotBlank()) { "stage must not be blank" }
        require(trackFrames >= 0) { "track frames must not be negative, got $trackFrames" }
        require(sampleRate > 0) { "sample rate must be positive, got $sampleRate" }
        return copyState(
            currentStage = stage,
            lastReportedFrames = -1,
            currentTrackFrames = trackFrames,
            currentSampleRate = sampleRate,
        )
    }

    /**
     * Reports the transport position within the current stage.
     *
     * The caller reports the engine's own frame counter, not a wall clock: that is what makes
     * background fake timing impossible, because a paused transport does not advance it. A report
     * that goes backwards (or jumps forward by an implausible amount) is treated as a seek and
     * ignored -- otherwise a user could scrub to the end of every track and unlock the cup without
     * hearing a note.
     */
    fun report(frames: Long): Progress {
        if (frames < 0) {
            return Progress(state, false, false, ignored = true, ignoredReason = "negative position")
        }
        val stage = state.currentStage
            ?: return Progress(state, false, false, ignored = true, ignoredReason = "no stage started")
        if (state.currentSampleRate <= 0) {
            return Progress(
                state, false, false,
                ignored = true, ignoredReason = "no sample rate",
            )
        }
        if (stage !in eligibleStages) {
            // A stage nobody has to listen to cannot contribute to eligibility.
            return Progress(state, false, false, ignored = true, ignoredReason = "stage not eligible")
        }
        if (stage in state.completedStages) {
            // Already counted; further reports must not inflate anything.
            return Progress(state, false, false, ignored = false)
        }

        val previous = state.lastReportedFrames
        val delta = if (previous < 0) 0L else frames - previous
        if (previous >= 0 && delta < 0) {
            return Progress(
                state, false, false,
                ignored = true, ignoredReason = "position went backwards, treated as a seek",
            )
        }

        // A jump larger than one second of audio is treated as a seek, not as listening. The engine's
        // frame counter advances with the audio, so a legitimate report can never move faster than
        // real time; anything that does came from scrubbing.
        val maxCredit = MAX_CREDIT_SECONDS.toLong() * state.currentSampleRate
        if (delta > maxCredit) {
            return Progress(
                state, false, false,
                ignored = true, ignoredReason = "jump of $delta frames exceeds one second, treated as a seek",
            )
        }

        val credited = (state.listenedFrames[stage] ?: 0L) + delta
        val required = requiredFrames(state.currentSampleRate)

        // A short track completes when it reaches its natural end, which is shorter than the
        // threshold. Comparing against the track length first is what makes that case work without
        // special-casing it at the call site.
        val reachedEnd = state.currentTrackFrames > 0 &&
            frames >= state.currentTrackFrames &&
            state.currentTrackFrames < required
        val reachedThreshold = credited >= required

        val completed = reachedEnd || reachedThreshold
        val completedStages = if (completed) state.completedStages + stage else state.completedStages
        val newlyCompleted = completed && stage !in state.completedStages

        val allDone = eligibleStages.isNotEmpty() && completedStages.containsAll(eligibleStages)
        val newlyEntitled = allDone && !state.entitled

        return Progress(
            state = copyState(
                listenedFrames = state.listenedFrames + (stage to credited.coerceAtMost(required)),
                completedStages = completedStages,
                entitled = state.entitled || allDone,
                // Entitlement alone does not start a ceremony: normal mode waits for the track to
                // end, so the offer is only recorded here.
                ceremony = if ((state.entitled || allDone) && state.ceremony == CeremonyPhase.NONE) {
                    CeremonyPhase.PENDING
                } else {
                    state.ceremony
                },
                lastReportedFrames = frames,
            ),
            newlyCompleted = newlyCompleted,
            newlyEntitled = newlyEntitled,
            ignored = false,
        )
    }

    /**
     * Enters the ceremony.
     *
     * Always starts from the beginning, including on a replay: the requirement says re-entering
     * restarts the ceremony, so there is no resume-from-where-you-left path.
     */
    fun beginCeremony(): UnlockState {
        require(state.entitled) { "the ceremony cannot start before the cup is unlocked" }
        return copyState(ceremony = CeremonyPhase.PLAYING)
    }

    /**
     * Reports that the ceremony audio played to its natural end.
     *
     * Takes a boolean rather than trusting a timer so the caller must be the one that knows the
     * audio finished; a UI timer calling this would be the fake-timing mistake the specification
     * forbids.
     */
    fun finishCeremony(reachedEnd: Boolean): UnlockState {
        require(state.ceremony == CeremonyPhase.PLAYING) {
            "the ceremony is not playing, so it cannot finish"
        }
        if (!reachedEnd) return state
        return copyState(ceremony = CeremonyPhase.COMPLETED)
    }

    /**
     * Leaves the ceremony early.
     *
     * The entitlement is deliberately preserved: quitting the show must not take the cup away. The
     * phase returns to [CeremonyPhase.PENDING] so the user can enter again from the start.
     */
    fun leaveCeremonyEarly(): UnlockState {
        require(state.ceremony == CeremonyPhase.PLAYING) {
            "only a playing ceremony can be left early"
        }
        return copyState(ceremony = CeremonyPhase.PENDING)
    }

    /**
     * Marks the congratulations dialog as shown, so it never appears twice.
     *
     * Only valid after a complete viewing; an early exit must not consume the first-time moment.
     */
    fun markCongratulated(): UnlockState {
        require(state.ceremony == CeremonyPhase.COMPLETED) {
            "the first-time congratulations belongs to a completed ceremony"
        }
        if (state.congratulated) return state
        return copyState(congratulated = true)
    }

    /** True when the first-time congratulations is still owed to the user. */
    val owesCongratulations: Boolean
        get() = state.ceremony == CeremonyPhase.COMPLETED && !state.congratulated

    /** True when the ceremony may be replayed from the detail page. */
    val canReplayCeremony: Boolean
        get() = state.entitled && state.ceremony != CeremonyPhase.PLAYING

    private fun copyState(
        listenedFrames: Map<String, Long> = state.listenedFrames,
        completedStages: Set<String> = state.completedStages,
        entitled: Boolean = state.entitled,
        ceremony: CeremonyPhase = state.ceremony,
        congratulated: Boolean = state.congratulated,
        currentStage: String? = state.currentStage,
        lastReportedFrames: Long = state.lastReportedFrames,
        currentTrackFrames: Long = state.currentTrackFrames,
        currentSampleRate: Int = state.currentSampleRate,
    ): UnlockState = UnlockState(
        listenedFrames = listenedFrames,
        completedStages = completedStages,
        entitled = entitled,
        ceremony = ceremony,
        congratulated = congratulated,
        currentStage = currentStage,
        lastReportedFrames = lastReportedFrames,
        currentTrackFrames = currentTrackFrames,
        currentSampleRate = currentSampleRate,
    )

    /**
     * Returns a new instance carrying [next], so callers can keep the class immutable.
     *
     * The eligible stage set is fixed at construction because it describes the content, not the
     * user's progress.
     */
    fun withState(next: UnlockState): SpecialCupUnlock =
        SpecialCupUnlock(eligibleStages, next)

    /** Stage prefix of a file name, using the same rule as flow discovery. */
    fun stageOf(fileName: String): String = StageFlowDiscovery.prefixOf(fileName)

    /** Convenience for callers that only have asset ids. */
    fun isEligibleStage(stage: String): Boolean = stage in eligibleStages

    /** Unused parameter guard: assets are accepted so callers do not need a separate mapping. */
    fun stageOfAsset(asset: AudioAssetId, name: String): String = stageOf(name)
}
