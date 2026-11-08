package com.mylittleblover.mkwplayer.graph

import com.mylittleblover.mkwplayer.library.BridgeCatalog
import com.mylittleblover.mkwplayer.library.BridgeTransition
import com.mylittleblover.mkwplayer.library.StageFlow

/**
 * Builds the whole transition set for a stage flow, using measured bridge timings where they exist.
 *
 * [StageFlowCompiler] already knew how to wire a flow; what it did not know was that for twenty stages the
 * handover position is a measured number rather than a musical guess. This assembles the complete rule set
 * — prelude exits, the bridge handover, one rule per outro, and the outro-to-outro replacements — so the
 * measured numbers reach the engine through a single place that can be tested without building a packet.
 *
 * The rule that matters most: a **layered** bridge is not wired at all. It has no switch point, so
 * scheduling it as a pre-entry transition would insert a gap where the game has an overlap. Four stages
 * are layered, and the transition research established that by three independent signals agreeing with no
 * exceptions.
 */
internal object StageTransitionPlanner {

    /** One stage's complete transition plan, ready to be turned into graph rules. */
    data class Plan(
        val transitions: List<MusicGraphBuilder.Transition>,
        /**
         * True when at least one transition uses measured numbers.
         *
         * The UI shows this: a flow whose handover is measured and a flow whose handover is this app's
         * musical guess must not read the same.
         */
        val hasMeasuredBridge: Boolean,
        /** The bridge plan, for its explanation. Null when the flow has no bridge at all. */
        val bridge: MeasuredTransition.Plan?,
    )

    /** Segment indices the caller has already compiled. */
    data class Segments(
        val main: Int,
        val bridge: Int? = null,
        val prelude: Int? = null,
        val preludeShort: Int? = null,
        /** Outro number to segment index. */
        val outros: Map<Int, Int> = emptyMap(),
    )

    /**
     * Assembles every transition a flow needs.
     *
     * [firstIndex] is the first transition index to use; the caller owns the numbering so a plan can be
     * spliced into a larger graph without collisions.
     */
    fun plan(
        flow: StageFlow,
        segments: Segments,
        firstIndex: Int,
    ): Plan {
        val transitions = mutableListOf<MusicGraphBuilder.Transition>()
        var next = firstIndex

        // A caller can pass a bridge segment for a flow that has no bridge part; that is a contradiction,
        // and honouring the segment would wire a bridge into a flow whose Bridge audio does not exist.
        // The flow's own parts decide, because they are what was actually compiled.
        val flowHasBridge = flow.parts.any { it.role == StageFlow.Role.Bridge }
        val declaredBridge = if (flowHasBridge) segments.bridge else null

        val bridgePlan = MeasuredTransition.planFor(flow, declaredBridge)
        val bridgeSegment = bridgePlan.bridgeSegment

        // Preludes hand over to the main loop. When a bridge is scheduled they declare it here, which is
        // what makes the engine run the bridge rather than a direct cut.
        for (from in listOfNotNull(segments.preludeShort, segments.prelude)) {
            transitions += MusicGraphBuilder.Transition(
                index = next++,
                sources = listOf(from),
                destinations = listOf(segments.main),
                sync = flow.timing.preludeExit,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
                bridge = bridgeSegment,
                fadeOut = MusicGraphBuilder.Fade(bridgePlan.fadeOutBeats),
                fadeIn = MusicGraphBuilder.Fade(bridgePlan.fadeInBeats),
                // The measured lead is expressed through preEntry: the bridge starts early so the
                // destination lands on its own entry exactly when the source reaches the switch. That is
                // the arithmetic the research measured for branch A.
                preEntry = bridgePlan.preEntry,
            )
        }

        // The bridge needs its own way out, or a flow that enters it never reaches the main loop. Only a
        // scheduled bridge gets this rule; a layered one never enters.
        if (declaredBridge != null && bridgeSegment >= 0) {
            transitions += MusicGraphBuilder.Transition(
                index = next++,
                sources = listOf(declaredBridge),
                destinations = listOf(segments.main),
                sync = flow.timing.bridgeEntry,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
            )
        }

        // One rule per outro. Listing them as destinations on a single rule would leave the engine to
        // choose; a caller asking for outro 2 must get outro 2.
        for ((_, segment) in segments.outros.toSortedMap()) {
            transitions += MusicGraphBuilder.Transition(
                index = next++,
                sources = listOf(segments.main),
                destinations = listOf(segment),
                sync = flow.timing.outroEntry,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
            )
        }

        // Outro-to-outro replacement, for a request that has not been executed yet. Each ordered pair is
        // its own rule so a replacement is addressable directly.
        val numbers = segments.outros.keys.sorted()
        for (from in numbers) {
            for (to in numbers) {
                if (from == to) continue
                transitions += MusicGraphBuilder.Transition(
                    index = next++,
                    sources = listOf(segments.outros.getValue(from)),
                    destinations = listOf(segments.outros.getValue(to)),
                    sync = flow.timing.outroSwitch,
                    entry = MusicGraphBuilder.EntryMode.EntryCue,
                )
            }
        }

        return Plan(
            transitions = transitions,
            hasMeasuredBridge = bridgePlan.isMeasured && bridgeSegment >= 0,
            // Null when the flow has no bridge part, even if the caller passed a segment index: the plan
            // describes what was compiled, not what was offered.
            bridge = if (!flowHasBridge) null else bridgePlan,
        )
    }

    /**
     * What the flow's handover should be called in the UI.
     *
     * Never says "the game's handover" for a project-chosen timing, and never claims a measured bridge for
     * a stage that has none. Both would be the same error in different directions.
     */
    fun provenanceLine(flow: StageFlow, plan: Plan): String {
        val bridge = plan.bridge ?: return "No bridge for this stage; sections are joined directly."
        return bridge.explanation
    }

    /** Stages whose bridge is layered, for a screen that wants to explain why nothing is scheduled. */
    fun layeredStages(): Set<String> =
        BridgeCatalog.layered().map(BridgeTransition::stage).toSet()
}
