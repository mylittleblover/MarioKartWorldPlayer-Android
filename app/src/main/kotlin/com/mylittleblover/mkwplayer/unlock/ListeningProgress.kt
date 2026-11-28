package com.mylittleblover.mkwplayer.unlock

/**
 * Connects a transport to the unlock rules.
 *
 * The rules live in [SpecialCupUnlock] and know nothing about playback; this is the one place that
 * decides *when* a report is trustworthy enough to count. Keeping it separate means the rules stay
 * testable without a transport, and the policy has one home instead of being spread through the view
 * model.
 *
 * Two policies are enforced here rather than in the rules, because they depend on facts the rules
 * cannot see:
 *
 * - Reports are only taken while the transport is actually playing. A paused transport reports the
 *   same position repeatedly, which is harmless, but a stopped-then-resumed one jumps, and the jump
 *   is not listening.
 * - Each report is capped so a long gap between polls cannot credit more than the wall time that
 *   passed. The rules already reject oversized jumps; this stops a legitimate but slow poll interval
 *   from being treated as one.
 */
internal class ListeningProgress(
    private val unlock: SpecialCupUnlock,
    /** Shortest gap between counts, in frames. Reports closer than this are coalesced. */
    private val minStep: Long = 0,
) {

    /** What the caller should do after feeding a transport sample. */
    data class Outcome(
        val state: SpecialCupUnlock.UnlockState,
        /** True when this sample completed the last outstanding stage. */
        val newlyEntitled: Boolean,
        /** True when the cup was offered for the first time by this sample. */
        val offeredNow: Boolean,
        /** True when the sample was discarded and why. */
        val ignoredReason: String? = null,
    )

    private var current: SpecialCupUnlock.UnlockState = unlock.snapshot

    /** Latest state, for display. */
    val snapshot: SpecialCupUnlock.UnlockState get() = current

    /** How many stages the cup requires, so callers do not have to re-derive it. */
    val eligibleCount: Int get() = unlock.eligibleCount

    /**
     * Starts crediting [stage].
     *
     * Called when a track is loaded or restarted. Without this the seek guard would compare the new
     * track's opening frames against the old track's last position and see a backwards jump.
     */
    fun begin(stage: String, trackFrames: Long, sampleRate: Int): SpecialCupUnlock.UnlockState {
        current = unlock.withState(current).beginStage(stage, trackFrames, sampleRate)
        return current
    }

    /**
     * Feeds one transport sample.
     *
     * [playing] is the transport's own state, not the app's intention: a track that has been started
     * but whose output has not begun is not playing, and crediting it would be the fake-timing
     * mistake in miniature.
     */
    fun sample(frames: Long, playing: Boolean): Outcome {
        if (!playing) {
            return Outcome(current, newlyEntitled = false, offeredNow = false, ignoredReason = "not playing")
        }

        val before = current

        // Coalescing has to happen BEFORE the rules see the sample. Letting the rules credit it and
        // then discarding the outcome would still move the minutes heard -- the time would be counted
        // and only the return value would say otherwise, which is the worst of both.
        if (minStep > 0 && before.lastReportedFrames >= 0) {
            val step = frames - before.lastReportedFrames
            if (step in 0 until minStep) {
                return Outcome(
                    current,
                    newlyEntitled = false,
                    offeredNow = false,
                    ignoredReason = "below step",
                )
            }
        }

        val progress = unlock.withState(before).report(frames)
        if (progress.ignored) {
            return Outcome(
                current,
                newlyEntitled = false,
                offeredNow = false,
                ignoredReason = progress.ignoredReason,
            )
        }

        val offeredNow = progress.state.ceremony == SpecialCupUnlock.CeremonyPhase.PENDING &&
            before.ceremony != SpecialCupUnlock.CeremonyPhase.PENDING
        current = progress.state
        return Outcome(current, progress.newlyEntitled, offeredNow)
    }

    /**
     * Feeds a continuous transport sample from live playback polling.
     *
     * Unlike raw [sample], this recovers safely from legitimate poll delays or fast playback rates
     * (e.g., 2.0x speed or background timer jitter exceeding 1s) by stepping within the wall-time
     * budget, and re-anchors the cursor if an unexpected discontinuity occurs so one slow poll
     * never permanently blocks progress for the remainder of the track.
     */
    fun sampleContinuous(
        frames: Long,
        playing: Boolean,
        wallDeltaMs: Long = 1_000L,
        maxPlaybackSpeed: Double = 2.0,
    ): Outcome {
        if (!playing) {
            return sample(frames, playing = false)
        }
        val before = current
        val rate = before.currentSampleRate
        if (before.currentStage == null || rate <= 0) {
            return sample(frames, playing = true)
        }
        val previous = before.lastReportedFrames
        if (previous < 0L) {
            return sample(frames, playing = true)
        }
        val delta = frames - previous
        if (delta < 0L) {
            // Looped or restarted from an earlier frame: start a fresh run from the new position.
            stopped()
            return sample(frames, playing = true)
        }
        val maxCredit = SpecialCupUnlock.MAX_CREDIT_SECONDS.toLong() * rate
        if (delta <= maxCredit) {
            return sample(frames, playing = true)
        }
        val safeSpeed = maxPlaybackSpeed.coerceIn(1.0, 2.5)
        val safeWallMs = wallDeltaMs.coerceIn(1L, 10_000L)
        val wallBudgetFrames = ((safeWallMs * rate * safeSpeed) / 1000.0).toLong() + rate / 2L
        if (delta <= wallBudgetFrames && delta <= maxCredit * 5L) {
            var cursor = previous
            var anyNewlyEntitled = false
            var anyOfferedNow = false
            while (cursor < frames) {
                val nextCursor = (cursor + maxCredit).coerceAtMost(frames)
                val stepOutcome = sample(nextCursor, playing = true)
                if (stepOutcome.newlyEntitled) anyNewlyEntitled = true
                if (stepOutcome.offeredNow) anyOfferedNow = true
                if (stepOutcome.ignoredReason != null && stepOutcome.ignoredReason != "below step") {
                    stopped()
                    return sample(frames, playing = true)
                }
                cursor = nextCursor
            }
            return Outcome(
                state = current,
                newlyEntitled = anyNewlyEntitled,
                offeredNow = anyOfferedNow,
            )
        }
        // Un-notified jump larger than wall-clock budget: credit nothing for the jump and re-anchor.
        stopped()
        return sample(frames, playing = true)
    }

    /**
     * Normalizes stored stage keys against the active eligible stages and grants retroactive
     * entitlement if all eligible stages are already satisfied.
     */
    fun reconcile(defaultSampleRate: Int = 48_000): Boolean {
        val next = unlock.withState(current).reconcile(defaultSampleRate)
        if (next == current) return false
        current = next
        return true
    }

    /**
     * Marks the 10-question Special Cup quiz as passed, making the unlock ceremony ready
     * (without prematurely setting `specialUnlocked`).
     */
    fun passQuiz(): SpecialCupUnlock.UnlockState {
        current = unlock.withState(current).passQuiz()
        return current
    }

    /** Remaining eligible stages that have not met the 30-second listening threshold yet. */
    fun remainingStages(): Set<String> = unlock.withState(current).remainingStages()

    /** Eligible stages that have already met the listening threshold. */
    fun completedStages(): Set<String> = current.completedStages

    /**
     * Adopts a restored state.
     *
     * Used when progress comes back from disk and there is no transport yet. Kept separate from
     * [begin] because a restored state has no cursor, and inventing one would let the first sample
     * measure a gap the user never heard.
     */
    fun seed(state: SpecialCupUnlock.UnlockState) {
        current = state.copy(lastReportedFrames = -1)
    }

    /** Note that the transport stopped. Any later resume is a new run, not a continuation. */
    fun stopped() {
        // Dropping the cursor is what makes a resume start clean: the rules cannot then measure a
        // gap across the stop.
        current = current.copy(lastReportedFrames = -1)
    }
}
