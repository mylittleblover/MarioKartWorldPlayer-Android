package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow

/**
 * Connects a [StagePerformance] to a transport, without either knowing about the other.
 *
 * [StagePerformance] decides what should play next; a transport can start a segment but knows nothing
 * about preludes or outros. This is the join, and it exists as its own object for one reason: the moment a
 * segment has finished must be read from the transport, not guessed.
 *
 * A timer would be the obvious shortcut and it is wrong twice over. A section that is still buffering has
 * not been heard, and a section that was paused has not been heard either — but a clock advances through
 * both. The transport's own position reaching its own length is the only signal that says the audio
 * actually played, and this class refuses to advance on anything else.
 */
internal class PerformanceDriver(
    private val performance: StagePerformance,
    /** Starts a segment. Returns true when the transport accepted it. */
    private val startSegment: (Int) -> Boolean,
    /** The transport's current state, as far as the driver needs it. */
    private val transport: () -> PlaybackSession.State,
) {

    /**
     * What happened on the last [tick], so a caller can act on it.
     *
     * A sealed result rather than a boolean because "started the next section" and "the performance
     * finished" need different handling in the UI, and a caller that cannot tell them apart will do the
     * wrong one.
     */
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
     * Without this the driver advances twice: the state it reads immediately after a request is still the
     * one from before it, showing the previous segment at its end, and the next tick would move again --
     * skipping a section, between two similar musical sections, where it is hardest to hear.
     *
     * The guard clears when the reading *changes*, not when a fixed time passes. A timer here would be the
     * same clock the driver exists to avoid: it would expire while the engine is still loading the next
     * segment and advance on audio nobody has heard.
     */
    private var stalePosition: Long? = null
    private var staleTotal: Long = -1

    /** True once [start] has run and the driver is following the transport. */
    val isRunning: Boolean get() = armed

    /** Begins the performance and starts its first segment. */
    fun start(): Event {
        if (armed) return Event.Waiting("already running")
        val step = performance.start()
        armed = true
        return act(step)
    }

    /**
     * Follows the transport.
     *
     * Called from the poll that already exists. It only advances when the transport reports that the
     * current segment reached its own end, so calling it more often or less often changes nothing but the
     * timing of the advance, never whether it happens.
     */
    fun tick(): Event {
        if (!armed) return Event.Waiting("the performance has not started")

        val state = transport()
        if (state.detail != null) {
            armed = false
            return Event.Blocked(state.detail)
        }

        // Stopped between segments, which is the normal state right after a request lands. Nothing has
        // finished, so nothing advances.
        if (!state.active && !state.playing && !state.paused && !state.loaded) {
            return Event.Waiting("the transport is idle")
        }

        // The same reading that prompted the last request. The transport has not moved yet, so this says
        // nothing about the segment that was just started.
        val stale = stalePosition != null &&
            state.positionFrames == stalePosition &&
            state.totalFrames == staleTotal

        stalePosition = null
        if (stale) return Event.Waiting("waiting for the new segment to start")

        if (!reachedEnd(state)) return Event.Waiting("the current segment is still playing")

        // Record the reading *before* the request is issued. Reading it afterwards would capture the new
        // segment's own state, which is the opposite of stale, and the guard would silence a real end.
        val previousPosition = state.positionFrames
        val previousTotal = state.totalFrames
        val event = act(performance.segmentEnded())
        if (event is Event.Advanced) {
            stalePosition = previousPosition
            staleTotal = previousTotal
        }
        return event
    }

    /** Asks for an outro. Returns the event so the caller can report a refusal. */
    fun requestOutro(number: Int): Event = act(performance.requestOutro(number))

    /** True once the main loop has run its passes, so an outro may be taken. */
    fun outroAvailable(): Boolean = performance.outroAvailable()

    /** Where the performance is, for a status line. Never invents detail. */
    fun describe(): String = performance.describe()

    /** Stops before the end. */
    fun stop(): Event {
        if (!armed) return Event.Finished("not running")
        armed = false
        return act(performance.abandon())
    }

    /**
     * True when the transport has played the current segment to its end.
     *
     * Requires a known length: an unknown length cannot be finished, and treating "no length" as "ended"
     * would advance a performance whose audio never loaded.
     */
    private fun reachedEnd(state: PlaybackSession.State): Boolean {
        if (state.totalFrames <= 0) return false
        // The engine stops at the end of a segment; either signal is enough, but at least one must be
        // present, so a transport that is merely idle mid-segment does not count.
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
                // The reading that produced this request is recorded by the caller, which has it; act() has
                // only the decision.
                Event.Advanced(step.segment, step.reason)
            }
        }
        is StagePerformance.Step.Wait -> Event.Waiting(step.reason)
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
 * The game's map draws roads as lines joining stages, and a straight line is what a player sees at the
 * zoom levels this app uses. An earlier attempt extracted the roads from the map image by colour and
 * produced 1021 polylines covered in coastline and foliage outlines -- worse than straight lines, because
 * a false road looks like a bug while a straight one reads as a diagram.
 *
 * So: given two anchors, the road is the segment between them. No image analysis, no data file.
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
            // Fractions, because the map is drawn at whatever size the screen allows.
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
     * wiring diagram rather than a road network. Each stage gets one road, and duplicate pairs are collapsed
     * so an anchor that is its neighbour's nearest does not produce two overlapping lines.
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
