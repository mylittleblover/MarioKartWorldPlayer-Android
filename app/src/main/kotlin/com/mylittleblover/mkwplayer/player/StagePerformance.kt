package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow

/**
 * Drives one stage from first prelude to final outro.
 *
 * The engine can play any section on request, but "play this stage" is a sequence of decisions over time,
 * and putting those decisions in the UI would mean every screen re-implements them differently. This is
 * the one place that knows what a full stage performance is:
 *
 * ```
 * Pre (or Pre_Short) -> Main, looping N times -> user asks for an outro -> that outro -> end
 * ```
 *
 * Two rules are worth stating because getting either wrong produces a plausible but wrong performance.
 *
 * The **prelude choice** is the caller's, not a fallback: the game has both a normal and a short prelude
 * and uses them in different circumstances, and picking one silently would make the short prelude
 * unreachable. [Prelude.NORMAL] is the default only because it is the longer listen.
 *
 * The **outro is not automatic**. A stage performance ends when the listener says so; the driver reports
 * that an outro is available and waits. Auto-selecting one would make every rehearsal the same length.
 *
 * Pure Kotlin, no Android types: the decisions are testable without a device, which matters because the
 * interesting failures here are ordering and availability rather than audio.
 */
internal class StagePerformance(
    private val flow: StageFlow,
    /** Segment index per role, as the compiler produced them. */
    private val segmentByRole: Map<StageFlow.Role, Int>,
    /** Segment index per outro number. */
    private val segmentByOutro: Map<Int, Int>,
    private val prelude: Prelude = Prelude.NORMAL,
    /** How many main-loop passes before an outro is offered. */
    private val mainRepeats: Int = flow.timing.mainRepeats,
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
    enum class Stage {
        /** Nothing started. */
        NOT_STARTED,

        /** In a prelude. */
        PRELUDE,

        /** In the main loop. */
        MAIN,

        /** In an outro, which is the end of the performance. */
        OUTRO,

        /** Finished, because the outro reached its end. */
        ENDED,

        /** Stopped before reaching an outro. */
        ABANDONED,
    }

    /** What the driver wants the caller to do next. */
    sealed interface Step {
        /** Request this segment. */
        data class Play(val segment: Int, val reason: String) : Step

        /** Do nothing; the current segment is still running. */
        data class Wait(val reason: String) : Step

        /** The performance is over. */
        data class Done(val finished: Boolean, val reason: String) : Step

        /** The performance cannot continue, and why. */
        data class Blocked(val reason: String) : Step
    }

    private var stage: Stage = Stage.NOT_STARTED
    private var mainPasses = 0
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
     * section whose audio was never compiled. The planner had the identical bug in the opposite direction,
     * wiring a bridge that did not exist, which is why this check is here rather than at the call site.
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
     * and starting the prelude alone would end in silence with no stated reason.
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
        mainPasses = 0
        currentSegment = entry
        return Step.Play(entry, "opening the performance")
    }

    /**
     * Reports that the current segment reached its end.
     *
     * The caller reports this from the transport, not from a timer: a countdown that reaches zero while
     * the audio is still buffering would advance the performance past a section nobody heard.
     */
    fun segmentEnded(): Step {
        val main = segmentFor(StageFlow.Role.Main)
            ?: return Step.Blocked("this flow has no main section")

        return when (stage) {
            Stage.NOT_STARTED -> Step.Blocked("the performance has not started")
            Stage.ENDED -> Step.Done(finished = true, reason = "the outro already finished")
            Stage.ABANDONED -> Step.Done(finished = false, reason = "the performance was stopped")

            Stage.PRELUDE -> {
                stage = Stage.MAIN
                mainPasses = 0
                currentSegment = main
                Step.Play(main, "prelude finished, entering the main loop")
            }

            Stage.MAIN -> {
                mainPasses += 1
                if (mainRepeats in 1..mainPasses) {
                    // The main loop has run its passes. The performance does not choose an outro: it stops
                    // repeating and waits, because which outro to take is the listener's decision.
                    Step.Wait("main loop complete; an outro is available when requested")
                } else {
                    Step.Wait("main loop pass $mainPasses of $mainRepeats")
                }
            }

            Stage.OUTRO -> {
                stage = Stage.ENDED
                Step.Done(finished = true, reason = "the outro reached its end")
            }
        }
    }

    /** True once the main loop has run the passes the flow asks for. */
    fun outroAvailable(): Boolean = stage == Stage.MAIN && mainPasses >= mainRepeats

    /**
     * Takes an outro.
     *
     * Refuses before the main loop has run its passes and when the flow does not carry that outro. Both
     * refusals are stated rather than silently substituted, because an outro is the end of the
     * performance and picking a different one changes what the listener hears last.
     */
    fun requestOutro(number: Int): Step {
        if (stage == Stage.ENDED) return Step.Done(finished = true, reason = "already finished")
        if (stage != Stage.MAIN) return Step.Blocked("an outro can only be taken from the main loop")

        val segment = segmentByOutro[number]
            ?: return Step.Blocked(
                "this flow has no outro $number; it has ${segmentByOutro.keys.sorted()}",
            )

        stage = Stage.OUTRO
        currentSegment = segment
        return Step.Play(segment, "outro $number requested")
    }

    /** Every outro number this flow can end with. */
    fun availableOutros(): List<Int> = segmentByOutro.keys.sorted()

    /** Stops the performance before it ends. */
    fun abandon(): Step {
        if (stage == Stage.ENDED) return Step.Done(finished = true, reason = "already finished")
        stage = Stage.ABANDONED
        return Step.Done(finished = false, reason = "stopped before an outro")
    }

    /** How many main-loop passes have completed, for a progress line. */
    fun mainPassesCompleted(): Int = mainPasses

    /**
     * One line describing where the performance is.
     *
     * Says which prelude was chosen, because a listener hearing the short prelude should be able to tell
     * that it was the short one rather than a truncated normal one.
     */
    fun describe(): String = when (stage) {
        Stage.NOT_STARTED -> "not started"
        Stage.PRELUDE -> if (prelude == Prelude.SHORT) "short prelude" else "prelude"
        Stage.MAIN -> "main loop, pass ${mainPasses + 1} of $mainRepeats"
        Stage.OUTRO -> "outro"
        Stage.ENDED -> "finished"
        Stage.ABANDONED -> "stopped"
    }
}
