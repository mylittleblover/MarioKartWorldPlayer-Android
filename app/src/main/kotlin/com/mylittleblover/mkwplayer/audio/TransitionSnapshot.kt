package com.mylittleblover.mkwplayer.audio

/**
 * Reads the transition fields out of the engine snapshot packet.
 *
 * `EngineSnapshot` covers the transport; a transition has its own 14-word block starting at
 * index 24, written by the bridge from `EngineSnapshot::transition`, which `EngineRuntime`
 * fills in `snapshot()`:
 *
 * ```
 * result.transition = { run.active, run.rule->index, run.sourceSegment,
 *                       run.segment, run.rule->bridge, run.requested, run.sourceSync,
 *                       run.destinationEntry, run.preStart, run.candidates,
 *                       run.specificity, run.priority, queuedTransition_,
 *                       !run.active ? 3 : run.sourceLeft ? 2
 *                                    : run.destinationStarted || run.bridgeStarted ? 1 : 0 };
 * ```
 *
 * That order is copied from the source rather than guessed. An earlier version of this file
 * assumed `{index, source, destination, bridge, elapsed, phase, result}` and would have
 * reported a transition's request frame as its result code -- the same class of shifted read
 * that every other snapshot reader in this project refuses to do.
 *
 * The last word is a phase *code*, not an enum: 0 pending, 1 entered, 2 source left,
 * 3 inactive.
 */
internal object TransitionSnapshot {

    /** Wire version the bridge writes at index 0. */
    const val WIRE_VERSION = 8L

    private const val FIRST = 24
    private const val LAST = 37
    private const val MIN_LENGTH = LAST + 1

    /** The request was accepted but the source has not departed yet. */
    const val PHASE_PENDING = 0L

    /** The destination (or its bridge) has started sounding. */
    const val PHASE_ENTERED = 1L

    /** The source has left; only the destination remains. */
    const val PHASE_SOURCE_LEFT = 2L

    /** No transition is running. */
    const val PHASE_INACTIVE = 3L

    data class Fields(
        /** True while a transition is executing. */
        val active: Boolean,
        /** Transition node being executed, or -1. */
        val transition: Long,
        /** Segment being left, or -1. */
        val source: Long,
        /** Segment being entered, or -1. */
        val destination: Long,
        /** Bridge segment in use, or -1. */
        val bridge: Long,
        /** Frame at which the transition was requested. */
        val requestedAt: Long,
        /** Sync point the source will leave on, or -1. */
        val sourceSync: Long,
        /** Entry frame in the destination, or -1. */
        val destinationEntry: Long,
        /** Frame at which pre-entry started, or -1. */
        val preStart: Long,
        /** Number of transition rules that matched. */
        val candidates: Long,
        val specificity: Long,
        val priority: Long,
        /** A target queued behind the running transition, or -1. */
        val queued: Long,
        /** Phase code; see the PHASE_* values. */
        val phase: Long,
    )

    fun read(snapshot: LongArray?): Fields? {
        if (snapshot == null || snapshot.size < MIN_LENGTH) return null
        if (snapshot[0] != WIRE_VERSION) return null

        return Fields(
            active = snapshot[24] != 0L,
            transition = snapshot[25],
            source = snapshot[26],
            destination = snapshot[27],
            bridge = snapshot[28],
            requestedAt = snapshot[29],
            sourceSync = snapshot[30],
            destinationEntry = snapshot[31],
            preStart = snapshot[32],
            candidates = snapshot[33],
            specificity = snapshot[34],
            priority = snapshot[35],
            queued = snapshot[36],
            phase = snapshot[37],
        )
    }

    /** Human-readable phase. Unknown values are reported, never coerced. */
    fun phaseName(phase: Long): String = when (phase) {
        PHASE_PENDING -> "pending"
        PHASE_ENTERED -> "entered"
        PHASE_SOURCE_LEFT -> "source left"
        PHASE_INACTIVE -> "inactive"
        else -> "unknown phase $phase"
    }

    /** First and last snapshot index this reader owns, exposed for tests. */
    fun range(): IntRange = FIRST..LAST
}
