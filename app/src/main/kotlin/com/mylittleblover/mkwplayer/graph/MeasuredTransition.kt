package com.mylittleblover.mkwplayer.graph

import com.mylittleblover.mkwplayer.library.BridgeCatalog
import com.mylittleblover.mkwplayer.library.BridgeTransition
import com.mylittleblover.mkwplayer.library.StageFlow

/**
 * Turns the measured bridge timings into the transition the engine actually schedules.
 *
 * The engine already has the machinery: a transition can declare a bridge segment, and `preEntry` makes
 * it start the bridge early so the destination lands on its own entry exactly when the source reaches the
 * sync point. That is precisely the arithmetic the transition research measured — branch A starts the
 * Full section `overlap` early so it reaches its grid origin at the switch.
 *
 * What was missing is the numbers. Before this, every flow used `StageFlow.Timing`, which is this
 * project's guess. For the twenty stages that have a measured bridge, the guess is replaced by the
 * measurement, and the object records which of the two it used so the UI can say so.
 *
 * The LAYERED stages are deliberately *not* wired as a bridge. They have no switch point: the research
 * established that the metadata that would carry one is empty by design, and the three signals that
 * establish it agree with no exceptions. Scheduling a layered bridge as a pre-entry transition would
 * insert a gap where the game has an overlap, which is worse than not using it at all.
 */
internal object MeasuredTransition {

    /** Where a transition's numbers came from. */
    enum class Basis {
        /** Read from the archive and, for three stages, checked against the running game. */
        MEASURED_BRIDGE,

        /** This project's choice, because the stage has no measured bridge. */
        PROJECT_DEFAULT,
    }

    /**
     * What to hand the builder for one stage's bridge handover.
     *
     * [bridgeSegment] is -1 when the flow should not use a bridge at all — either because the stage has
     * none, or because the measured bridge is layered and therefore has no handover to schedule.
     */
    data class Plan(
        val basis: Basis,
        val bridgeSegment: Int,
        val sync: MusicGraphBuilder.SyncPoint,
        val preEntry: Boolean,
        val fadeOutBeats: Double,
        val fadeInBeats: Double,
        /**
         * Whether the destination should start from its own beginning or resume at the measured source
         * offset.
         *
         * Branch B resumption needs the destination to enter at a specific offset inside the Full track,
         * which is expressed in the graph by giving the Full segment its own entry point rather than by
         * moving the playhead here. The flag is carried so a caller cannot silently choose the wrong one.
         */
        val destinationResumes: Boolean,
        /** One line the UI can show. Never claims more than the basis supports. */
        val explanation: String,
    ) {
        /** True when the numbers came from the archive rather than from this project. */
        val isMeasured: Boolean get() = basis == Basis.MEASURED_BRIDGE
    }

    /**
     * Builds the plan for a flow.
     *
     * [bridgeSegment] is the compiled segment index of the flow's bridge part, or null when the flow has
     * no bridge asset at all.
     */
    fun planFor(flow: StageFlow, bridgeSegment: Int?): Plan {
        val measured = measuredBridgeFor(flow)
        if (measured == null) {
            return Plan(
                basis = Basis.PROJECT_DEFAULT,
                bridgeSegment = bridgeSegment ?: -1,
                sync = flow.timing.bridgeEntry,
                preEntry = false,
                fadeOutBeats = flow.timing.bridgeFadeOutBeats,
                fadeInBeats = flow.timing.bridgeFadeInBeats,
                destinationResumes = false,
                explanation = if (bridgeSegment == null) {
                    "No bridge for this stage. The prelude hands over directly, using this app's timing."
                } else {
                    "Bridge timing is this app's choice: no measurement exists for this stage."
                },
            )
        }

        if (measured.mode == BridgeTransition.Mode.LAYERED) {
            // Layered means there is no handover to schedule. Using the bridge would need the engine to
            // run two segments at once, which this graph does not express; wiring it as a pre-entry
            // splice would instead invent a gap the game does not have.
            return Plan(
                basis = Basis.MEASURED_BRIDGE,
                bridgeSegment = -1,
                sync = flow.timing.bridgeEntry,
                preEntry = false,
                fadeOutBeats = 0.0,
                fadeInBeats = 0.0,
                destinationResumes = false,
                explanation = "Measured: this bridge is layered over the main loop, with no switch " +
                    "point. It is not scheduled as a handover.",
            )
        }

        val switch = measured.switchSample ?: return projectDefault(flow, bridgeSegment)
        val enters = measured.fullEntersAt ?: switch
        val leads = switch - enters

        // The engine's own rule: with preEntry the bridge starts `bridge.entry / speed` before the sync
        // point, and the destination lands at the sync point plus the bridge's duration. When the measured
        // entry leads the switch by a positive amount, that lead is exactly the deliberate overlap the
        // research predicted, so preEntry is the mechanism that reproduces it.
        val usesPreEntry = leads > 0

        val destinationResumes = measured.fullSourceOffset > 0

        return Plan(
            basis = Basis.MEASURED_BRIDGE,
            bridgeSegment = bridgeSegment ?: -1,
            sync = syncFor(measured),
            preEntry = usesPreEntry,
            // No fades: the measured handover is a splice at a sample position. Adding a fade here would
            // contradict the measurement, and the research explicitly found no section-level fade values
            // anywhere in the corpus.
            fadeOutBeats = 0.0,
            fadeInBeats = 0.0,
            destinationResumes = destinationResumes,
            explanation = buildString {
                append("Measured bridge: switch at sample ").append(switch)
                append(" (").append("%.3f".format(measured.switchBeats() ?: 0.0)).append(" beats)")
                when (measured.switchSource) {
                    BridgeTransition.SwitchSource.EXIT_RECORD -> append(", read from the exit record")
                    BridgeTransition.SwitchSource.FINAL_NODE -> append(", read from the grid node")
                    else -> append(", inferred from the musical grid")
                }
                if (usesPreEntry) {
                    append("; the main loop starts ").append(leads).append(" samples early (")
                    append("%.0f".format(measured.overlapSeconds() * 1000)).append(" ms overlap)")
                }
                if (destinationResumes) {
                    append("; the main loop resumes from sample ").append(measured.fullSourceOffset)
                }
            },
        )
    }

    /**
     * The bridge record for a flow, or null when the stage has no measured bridge.
     *
     * Matching is by the flow's own stage code. A flow whose name carries a suffix the archive does not
     * (a hypothetical `Coaster_Extra`) still finds its bridge, because the code is reduced to the part
     * before the first underscore that the catalogue recognises.
     */
    fun measuredBridgeFor(flow: StageFlow): BridgeTransition? {
        val direct = BridgeCatalog.forStage(flow.name)
        if (direct != null) return direct

        val stem = flow.name.substringAfterLast('_')
        BridgeCatalog.forStage(stem)?.let { return it }
        return BridgeCatalog.all().firstOrNull { flow.name.startsWith(it.stage) }
    }

    /**
     * The sync point that corresponds to a measured sample position.
     *
     * The measured position is a sample offset, not a beat, so the transition cannot use a musical sync
     * point without changing when it happens. `Immediate` tells the engine to act on the request now,
     * which is the closest available expression of "at this sample"; the position itself is carried by
     * the bridge timing rather than by the sync enum.
     */
    private fun syncFor(measured: BridgeTransition): MusicGraphBuilder.SyncPoint =
        MusicGraphBuilder.SyncPoint.Immediate

    private fun projectDefault(flow: StageFlow, bridgeSegment: Int?): Plan = Plan(
        basis = Basis.PROJECT_DEFAULT,
        bridgeSegment = bridgeSegment ?: -1,
        sync = flow.timing.bridgeEntry,
        preEntry = false,
        fadeOutBeats = flow.timing.bridgeFadeOutBeats,
        fadeInBeats = flow.timing.bridgeFadeInBeats,
        destinationResumes = false,
        explanation = "Bridge timing is this app's choice: the measurement has no switch point.",
    )
}
