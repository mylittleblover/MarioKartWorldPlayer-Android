package com.mylittleblover.mkwplayer.unlock

/**
 * The conditions the Special Cup's eligibility gate requires, kept together because they are the
 * place a "cup appears for no reason" bug lives.
 *
 * The specification names the rule exactly:
 *
 * ```
 * eligible = isManifestFrozen && set.isNotEmpty() && allVerified && allCompleted
 * ```
 *
 * Every clause closes a specific way the cup could open wrongly:
 *
 * - `isManifestFrozen`: while the index is still being built, the set of tracks is incomplete. A user
 *   who has heard every track *so far* has not heard every track.
 * - `set.isNotEmpty()`: `all(emptyList())` is `true` in Kotlin. Without this clause an empty library
 *   would satisfy "everything has been heard" and the cup would open on first launch -- the exact
 *   `all(emptyList())` trap the specification warns about.
 * - `allVerified`: a track whose audio has never been proved playable cannot be listened to, so
 *   requiring it would make the cup unearnable, and not requiring it would let a broken library open
 *   the cup.
 * - `allCompleted`: the ordinary requirement.
 */
internal object CupEligibility {

    /** Why the gate is shut, in a form the screen can show without re-deriving anything. */
    enum class Blocker {
        /** The manifest is not frozen, so the track set is still incomplete. */
        MANIFEST_NOT_FROZEN,

        /** Nothing is eligible. Never treated as "everything is done". */
        NO_ELIGIBLE_TRACKS,

        /** Some required tracks are not verified playable, so they cannot be heard. */
        UNVERIFIED_TRACKS,

        /** Stages are still outstanding. */
        INCOMPLETE,
    }

    /** Everything the gate needs. */
    data class Conditions(
        val isManifestFrozen: Boolean,
        val eligibleStages: Set<String>,
        /** Stages whose audio has been proved playable. */
        val verifiedStages: Set<String>,
        val completedStages: Set<String>,
    )

    /**
     * The gate.
     *
     * Returns null when the cup may open, or the first blocker in the order above. The order matters
     * for the message the user sees: "the index is not finished" is a different statement from "you
     * have not listened enough", and showing the wrong one would make the requirement look arbitrary.
     */
    fun blocker(conditions: Conditions): Blocker? = when {
        !conditions.isManifestFrozen -> Blocker.MANIFEST_NOT_FROZEN
        conditions.eligibleStages.isEmpty() -> Blocker.NO_ELIGIBLE_TRACKS
        // Checked before INCOMPLETE on purpose. A required track that cannot be played can never be
        // completed, so telling the user to keep listening would be advice they cannot follow; the
        // unplayable fact is the one that explains why the cup will not open.
        conditions.eligibleStages.any { it !in conditions.verifiedStages } -> Blocker.UNVERIFIED_TRACKS
        !conditions.completedStages.containsAll(conditions.eligibleStages) -> Blocker.INCOMPLETE
        else -> null
    }

    /** True only when the gate is fully open. The single predicate callers should use. */
    fun isEligible(conditions: Conditions): Boolean = blocker(conditions) == null

    /** A line for the screen, so a closed gate is explained rather than merely unresponsive. */
    fun explain(blocker: Blocker): String = when (blocker) {
        Blocker.MANIFEST_NOT_FROZEN ->
            "The music index is not finished yet, so the track list is incomplete."
        Blocker.NO_ELIGIBLE_TRACKS ->
            "No tracks to listen to yet. Add some music first."
        Blocker.UNVERIFIED_TRACKS ->
            "Some tracks cannot be played in this build, so they cannot count."
        Blocker.INCOMPLETE -> "Keep listening."
    }
}
