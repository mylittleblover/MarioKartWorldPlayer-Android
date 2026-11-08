package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow

/**
 * Connects a [StagePerformance] to a transport, and is the console operator's interface to it.
 *
 * [StagePerformance] decides what should play next; a transport can start a segment but knows nothing about
 * preludes or endings. This is the join, and it exists as its own object for one reason: every move must be
 * a command from the operator, never a clock.
 *
 * A timer would be the obvious shortcut and it is wrong twice over. A section that is still buffering has
 * not been heard, and a section that was paused has not been heard either, but a clock advances through
 * both. Worse, the engine ends a section by going inactive and never follows a transition by itself, so no
 * amount of watching the transport would ever move the performance on.
 *
 * So the three controls are: [advance] (the next-stage event), [toggleFast] (the arrangement switch) and
 * [takeOutro] (the ending). [tick] exists only to notice the ending's natural end and to report failures.
 */
internal class PerformanceDriver(
    private val performance: StagePerformance,
    /** Starts a segment. Returns true when the transport accepted it. */
    private val startSegment: (Int) -> Boolean,
    /** The transport's current state, as far as the driver needs it. */
    private val transport: () -> PlaybackSession.State,
) {

    /** What happened on the last call, so a caller can act on it. */
    sealed interface Event {
        /** A segment was started. */
        data class Advanced(val segment: Int, val reason: String) : Event

        /** Nothing to do yet. */
        data class Waiting(val reason: String) : Event

        /** The performance reached its end. */
        data class Finished(val reason: String) : Event

        /** The performance cannot continue. */
        data class Blocked(val reason: String) : Event
    }

    private var armed = false

    /**
     * The transport reading that prompted the last request, so a repeat of it is not mistaken for new news.
     *
     * Without this the driver advances twice: the state it reads immediately after a request is still the one
     * from before it, showing the previous segment at its end, and the next tick would move again.
     *
     * The guard clears when the reading *changes*, not when a fixed time passes. A timer here would be the
     * same clock the driver exists to avoid.
     */
    private var stalePosition: Long? = null
    private var staleTotal: Long = -1

    /** True while the fast arrangement is the one playing. */
    private var fast = false

    /** True once [start] has run and the driver is following the transport. */
    val isRunning: Boolean get() = armed

    /** True when the fast arrangement is playing. */
    val isFast: Boolean get() = fast

    /** True when the flow carries a fast arrangement at all, so the console can hide the switch. */
    val hasFastArrangement: Boolean get() = performance.hasFastArrangement

    /** Begins the performance and starts its first segment. */
    fun start(): Event {
        if (armed) return Event.Waiting("already running")
        val step = performance.start()
        armed = true
        return act(step)
    }

    /**
     * The next-stage event: leave the prelude for the main loop.
     *
     * This is what the console's first control triggers. It is deliberately not automatic: the prelude loops
     * until someone says the race has started.
     */
    fun advance(): Event = act(performance.advance())

    /**
     * Switches between the main loop and its fast arrangement.
     *
     * The two are the same music at different tempos, so the move is a crossfade at a bar line rather than a
     * cut -- the compiled rule says which and carries the fade. The driver only decides which direction and
     * remembers which way it went.
     */
    fun toggleFast(): Event {
        if (!performance.hasFastArrangement) {
            return Event.Blocked("this flow has no fast arrangement, so there is nothing to switch to")
        }
        if (performance.current != StagePerformance.Stage.MAIN) {
            return Event.Blocked("the fast arrangement can only be switched to from the main loop")
        }
        val target = performance.fastSegment()
            ?: return Event.Blocked("this flow's fast arrangement has no segment")
        val back = performance.mainSegment()
            ?: return Event.Blocked("this flow has no main section")

        val next = if (fast) back else target
        // A failed request must not flip the remembered direction, or the console would show the wrong state
        // and the next press would do nothing.
        val accepted = if (fast) performance.leaveFast() else performance.enterFast()
        val step = when (accepted) {
            is StagePerformance.Step.Play -> accepted
            else -> accepted
        }
        val event = act(step)
        if (event is Event.Advanced) fast = !fast
        return event
    }

    /** Takes the ending nearest to the current position. */
    fun takeOutro(): Event {
        val position = transport().positionFrames
        return act(performance.takeOutro(position))
    }

    /** Where the performance is, for the console's status line. */
    fun describe(): String = performance.describe()

    /** True while the prelude is looping, which is the one moment the next-stage event means anything. */
    val canAdvance: Boolean get() = performance.current == StagePerformance.Stage.PRELUDE

    /** True while the main loop is playing, which is when an ending can be chosen. */
    val canEnd: Boolean get() = performance.current == StagePerformance.Stage.MAIN

    /**
     * Follows the transport for the one thing that cannot be a command: the ending reaching its own end.
     */
    fun tick(): Event {
        if (!armed) return Event.Waiting("the performance has not started")

        val state = transport()
        if (state.detail != null) {
            armed = false
            return Event.Blocked(state.detail)
        }

        // The same reading that prompted the last request. The transport has not moved yet, so this says
        // nothing about the segment that was just started.
        val stale = stalePosition != null &&
            state.positionFrames == stalePosition &&
            state.totalFrames == staleTotal
        stalePosition = null
        if (stale) return Event.Waiting("waiting for the new segment to start")

        if (performance.current != StagePerformance.Stage.OUTRO) {
            // Every other section loops, so reaching its end means it wrapped rather than that it ended.
            return Event.Waiting("the current section is looping")
        }
        if (!reachedEnd(state)) return Event.Waiting("the ending is still playing")

        return act(performance.finalEnded())
    }

    /** Stops before the end. */
    fun stop(): Event {
        if (!armed) return Event.Finished("not running")
        armed = false
        return act(performance.abandon())
    }

    /**
     * True when the transport has played the ending to its end.
     *
     * Requires a known length: an unknown length cannot be finished, and treating "no length" as "ended"
     * would advance a performance whose audio never loaded.
     */
    private fun reachedEnd(state: PlaybackSession.State): Boolean {
        if (state.totalFrames <= 0) return false
        val atEnd = state.positionFrames >= state.totalFrames
        val stoppedAfterPlaying = !state.playing && !state.paused && state.loaded
        return atEnd || stoppedAfterPlaying
    }

    private fun act(step: StagePerformance.Step): Event = when (step) {
        is StagePerformance.Step.Play -> {
            if (!startSegment(step.segment)) {
                armed = false
                Event.Blocked("the transport refused segment ${step.segment}")
            } else {
                Event.Advanced(step.segment, step.reason)
            }
        }
        is StagePerformance.Step.Hold -> Event.Waiting(step.reason)
        is StagePerformance.Step.Done -> {
            armed = false
            Event.Finished(step.reason)
        }
        is StagePerformance.Step.Blocked -> {
            armed = false
            Event.Blocked(step.reason)
        }
    }
}

/**
 * The straight-line road between two map anchors.
 *
 * The game's map draws roads as lines joining stages, and a straight line is what a player sees at the zoom
 * levels this app uses. An earlier attempt extracted the roads from the map image by colour and produced 1021
 * polylines covered in coastline and foliage outlines -- worse than straight lines, because a false road
 * looks like a bug while a straight one reads as a diagram.
 */
internal object MapRoad {

    /** A road between two anchors, in normalised map fractions. */
    data class Segment(
        val fromCode: String,
        val toCode: String,
        val fromU: Float,
        val fromV: Float,
        val toU: Float,
        val toV: Float,
    ) {
        init {
            require(fromCode.isNotBlank() && toCode.isNotBlank()) { "a road needs two named ends" }
            require(fromCode != toCode) { "a road from a stage to itself is not a road" }
            require(fromU in 0f..1f && toU in 0f..1f) { "u must be within 0..1" }
            require(fromV in 0f..1f && toV in 0f..1f) { "v must be within 0..1" }
        }

        /** Straight-line length in normalised units, for choosing the shortest connection. */
        fun length(): Double {
            val du = (toU - fromU).toDouble()
            val dv = (toV - fromV).toDouble()
            return kotlin.math.sqrt(du * du + dv * dv)
        }
    }

    /**
     * Roads joining each anchor to its nearest neighbour.
     *
     * Nearest rather than all-pairs: all-pairs would draw a complete graph over the map, which reads as a
     * wiring diagram rather than a road network.
     */
    fun nearestNeighbourRoads(
        stages: List<com.mylittleblover.mkwplayer.library.MapAnchors.Anchor>,
    ): List<Segment> {
        if (stages.size < 2) return emptyList()

        val seen = mutableSetOf<Pair<String, String>>()
        val roads = mutableListOf<Segment>()

        for (anchor in stages) {
            val nearest = stages
                .asSequence()
                .filter { it.code != anchor.code }
                .minByOrNull { distanceSquared(anchor, it) }
                ?: continue

            // Normalised so A->B and B->A are the same road.
            val key = if (anchor.code < nearest.code) {
                anchor.code to nearest.code
            } else {
                nearest.code to anchor.code
            }
            if (!seen.add(key)) continue

            roads += Segment(
                fromCode = anchor.code,
                toCode = nearest.code,
                fromU = anchor.u,
                fromV = anchor.v,
                toU = nearest.u,
                toV = nearest.v,
            )
        }
        return roads
    }

    private fun distanceSquared(
        a: com.mylittleblover.mkwplayer.library.MapAnchors.Anchor,
        b: com.mylittleblover.mkwplayer.library.MapAnchors.Anchor,
    ): Double {
        val du = (a.u - b.u).toDouble()
        val dv = (a.v - b.v).toDouble()
        return du * du + dv * dv
    }
}
