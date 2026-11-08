package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow

/**
 * Drives one stage the way the user described it, and the way the engine's semantics require.
 *
 * ```
 * prelude   loops, forever, until told to advance
 *    |  advance()                       <- the race-start event
 *    v
 * main      looping; entered through the transition rule, which plays the Bridge when there is one
 *    |  takeOutro(position)             <- the lap-ending event
 *    v
 * outro     the one nearest to how far into the main loop playback has got; it does not loop
 *    |
 *    v
 * complete
 * ```
 *
 * **Nothing here runs on a clock.** The engine ends a segment by going inactive; it does not follow a
 * transition by itself. And a position compared against the whole flow's length never reaches that length,
 * because each section is shorter than the sum. So the only things that advance a performance are commands:
 * [advance] for the prelude and [takeOutro] for the ending. That is also what the game does -- a race start
 * and a lap ending are events, not timers.
 *
 * The prelude looping forever is what makes the earlier version look broken: it played the prelude, the
 * prelude ended, and nothing told it what to do next. Looping the prelude means it is still playing when the
 * command arrives, which is the only condition under which a transition can be scheduled at all.
 *
 * The bridge is not this object's business. The transition rule from prelude to main names the bridge
 * segment, so requesting main is what makes the engine insert it; the timing of that insertion is the
 * measured handover, not a decision made here.
 */
internal class StagePerformance(
    private val flow: StageFlow,
    /** Segment index per role, as the compiler produced them. */
    private val segmentByRole: Map<StageFlow.Role, Int>,
    /** Segment index per outro number. */
    private val segmentByOutro: Map<Int, Int>,
    private val prelude: Prelude = Prelude.NORMAL,
) {

    /** Which prelude the performance opens with. */
    enum class Prelude {
        /** The long prelude. */
        NORMAL,

        /**
         * The short prelude.
         *
         * A distinct choice rather than a fallback for a missing normal prelude: the game uses it in
         * circumstances this project has not established, so silently preferring one would be a guess
         * dressed as a default.
         */
        SHORT,
    }

    /** Where the performance currently is. */
    enum class Stage { NOT_STARTED, PRELUDE, MAIN, OUTRO, ENDED, ABANDONED }

    /** What the driver wants the caller to do next. */
    sealed interface Step {
        /** Request this segment. The engine turns it into the matching transition. */
        data class Play(val segment: Int, val reason: String) : Step

        /** Nothing to do; the current segment is looping. */
        data class Hold(val reason: String) : Step

        /** The performance is over. */
        data class Done(val finished: Boolean, val reason: String) : Step

        /** The performance cannot continue, and why. */
        data class Blocked(val reason: String) : Step
    }

    private var stage: Stage = Stage.NOT_STARTED
    private var currentSegment: Int? = null

    /** Current stage of the performance. */
    val current: Stage get() = stage

    /** The segment requested last, or null before anything started. */
    val playing: Int? get() = currentSegment

    /** True once an outro has finished, so the caller can say the performance completed. */
    val isComplete: Boolean get() = stage == Stage.ENDED

    /**
     * Segment index for a role, but only when the flow actually carries that part.
     *
     * The segment map is what the compiler produced; the flow's parts are what exist. A caller can pass a
     * complete map for a flow that has no prelude, and trusting the map would start a performance in a
     * section whose audio was never compiled.
     */
    private fun segmentFor(role: StageFlow.Role): Int? =
        if (flow.parts.any { it.role == role }) segmentByRole[role] else null

    /** The opening segment, or null when the flow cannot start. */
    fun entrySegment(): Int? = when (prelude) {
        Prelude.SHORT -> segmentFor(StageFlow.Role.PreShort) ?: segmentFor(StageFlow.Role.Pre)
        Prelude.NORMAL -> segmentFor(StageFlow.Role.Pre) ?: segmentFor(StageFlow.Role.PreShort)
    } ?: segmentFor(StageFlow.Role.Main)

    /**
     * Starts the performance.
     *
     * Refuses rather than guessing when there is no main section: a flow without one cannot be performed,
     * and starting a looping prelude alone would never produce a second section and would never say why.
     */
    fun start(): Step {
        if (segmentFor(StageFlow.Role.Main) == null) {
            stage = Stage.ABANDONED
            return Step.Blocked("this flow has no main section, so it cannot be performed")
        }
        val entry = entrySegment()
            ?: run {
                stage = Stage.ABANDONED
                return Step.Blocked("this flow has no section to start from")
            }
        stage = if (entry == segmentFor(StageFlow.Role.Main)) Stage.MAIN else Stage.PRELUDE
        currentSegment = entry
        return Step.Play(entry, "opening the performance")
    }

    /**
     * The caller has announced that the next section should begin.
     *
     * This is the race-start event. In [Stage.NOT_STARTED] it starts the performance rather than refusing,
     * because "begin" is a sensible first command.
     */
    fun advance(): Step {
        val main = segmentFor(StageFlow.Role.Main)
            ?: return Step.Blocked("this flow has no main section")

        return when (stage) {
            Stage.NOT_STARTED -> start()
            Stage.PRELUDE -> {
                stage = Stage.MAIN
                currentSegment = main
                // The transition rule between prelude and main names the bridge, so the engine inserts it.
                // Nothing on this side has to know about the bridge, which is why there is no branch here.
                Step.Play(main, "advancing to the main loop")
            }
            Stage.MAIN -> Step.Hold("already in the main loop")
            Stage.OUTRO -> Step.Hold("already taking the ending")
            Stage.ENDED -> Step.Done(finished = true, reason = "the performance already finished")
            Stage.ABANDONED -> Step.Done(finished = false, reason = "the performance was stopped")
        }
    }

    /**
     * Chooses the ending nearest to the position the listener has reached.
     *
     * The game picks an outro by how much of the stage has been played, so each ending has a natural place to
     * start from. [positionFrames] is the transport's own position within the main loop; the ending whose
     * length is closest to it wins, and ties go to the lowest number so the result is deterministic.
     *
     * Refuses outside the main loop rather than substituting: an ending is the last thing heard, and choosing
     * a different one changes what the listener remembers.
     */
    fun takeOutro(positionFrames: Long): Step {
        if (stage == Stage.ENDED) return Step.Done(finished = true, reason = "already finished")
        if (stage != Stage.MAIN) return Step.Blocked("an ending can only be taken from the main loop")
        if (segmentByOutro.isEmpty()) {
            return Step.Blocked("this flow has no endings, so the main loop is the whole performance")
        }

        val chosen = nearestOutro(positionFrames)
        stage = Stage.OUTRO
        currentSegment = segmentByOutro.getValue(chosen)
        return Step.Play(
            segmentByOutro.getValue(chosen),
            "ending $chosen is the nearest to $positionFrames frames into the loop",
        )
    }

    /**
     * The outro number whose own length is closest to [positionFrames].
     *
     * Ties go to the lowest number, so the choice does not depend on map iteration order.
     */
    fun nearestOutro(positionFrames: Long): Int {
        val position = positionFrames.coerceAtLeast(0)
        return segmentByOutro.keys.sorted().minByOrNull { number ->
            val frames = flow.outro(number)?.frames ?: Long.MAX_VALUE
            kotlin.math.abs(frames - position)
        } ?: segmentByOutro.keys.min()
    }

    /** Reports that the ending reached its own end. */
    fun finalEnded(): Step = when (stage) {
        Stage.OUTRO -> {
            stage = Stage.ENDED
            Step.Done(finished = true, reason = "the ending reached its end")
        }
        Stage.ENDED -> Step.Done(finished = true, reason = "already finished")
        Stage.ABANDONED -> Step.Done(finished = false, reason = "the performance was stopped")
        Stage.NOT_STARTED -> Step.Blocked("the performance has not started")
        Stage.PRELUDE, Stage.MAIN -> Step.Blocked("this section loops, so it has no natural end")
    }

    /** Stops the performance before it ends. */
    fun abandon(): Step {
        if (stage == Stage.ENDED) return Step.Done(finished = true, reason = "already finished")
        stage = Stage.ABANDONED
        return Step.Done(finished = false, reason = "stopped before an ending")
    }

    /** Every outro number this flow can end with. */
    fun availableOutros(): List<Int> = segmentByOutro.keys.sorted()

    /** True when the flow carries a fast arrangement to switch to. */
    val hasFastArrangement: Boolean get() = segmentFor(StageFlow.Role.Fast) != null

    /** The fast arrangement's segment, or null when the flow has none. */
    fun fastSegment(): Int? = segmentFor(StageFlow.Role.Fast)

    /** The main loop's segment, or null when the flow has none. */
    fun mainSegment(): Int? = segmentFor(StageFlow.Role.Main)

    /**
     * Switches to the fast arrangement.
     *
     * Only from the main loop: the two are the same music, so switching from a prelude or an ending would be
     * a different piece of music arriving rather than the same one changing tempo. The compiled rule carries
     * the crossfade, so nothing here has to know about it.
     */
    fun enterFast(): Step {
        if (stage != Stage.MAIN) return Step.Blocked("the fast arrangement can only be entered from the main loop")
        val fastSegment = segmentFor(StageFlow.Role.Fast)
            ?: return Step.Blocked("this flow has no fast arrangement")
        return Step.Play(fastSegment, "switching to the fast arrangement")
    }

    /** Switches back to the main loop. */
    fun leaveFast(): Step {
        val main = segmentFor(StageFlow.Role.Main)
            ?: return Step.Blocked("this flow has no main section")
        return Step.Play(main, "switching back to the main loop")
    }

    /** One line describing where the performance is. */
    fun describe(): String = when (stage) {
        Stage.NOT_STARTED -> "not started"
        Stage.PRELUDE -> if (prelude == Prelude.SHORT) "short prelude, looping" else "prelude, looping"
        Stage.MAIN -> "main loop"
        Stage.OUTRO -> "ending"
        Stage.ENDED -> "finished"
        Stage.ABANDONED -> "stopped"
    }
}
