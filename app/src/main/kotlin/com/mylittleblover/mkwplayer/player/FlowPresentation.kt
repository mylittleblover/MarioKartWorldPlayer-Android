package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow

/**
 * What a multi-part flow looks like to the screen.
 *
 * The flow itself is a compile-time structure; the screen needs a flat, presentable view of it:
 * which section is playing, what else can be requested, and whether the timings are real. Keeping
 * that mapping here means the UI never has to know about segment indices or provenance rules.
 */
internal object FlowPresentation {

    /** One requestable section, as a button would show it. */
    data class Section(
        /** Segment index the engine addresses. */
        val segment: Int,
        /** Short label, e.g. `Pre`, `Main`, `Outro 01`. */
        val label: String,
        /** Frames, for a duration readout. */
        val frames: Long,
        val sampleRate: Int,
    ) {
        val seconds: Int get() = if (sampleRate <= 0) 0 else (frames / sampleRate).toInt()
    }

    /**
     * Builds the section list for a compiled flow.
     *
     * The preludes are included even though only one is normally used, because this is a
     * development surface: being able to start either prelude is how the difference between them
     * gets checked. Outros are listed by number so a request can name one.
     */
    fun sections(flow: StageFlow, compiled: com.mylittleblover.mkwplayer.graph.StageFlowCompiler.Compiled): List<Section> {
        val structural = compiled.segmentByRole.map { (role, segment) ->
            val part = flow.part(role)!!
            Section(
                segment = segment,
                label = when (role) {
                    StageFlow.Role.PreShort -> "Pre (short)"
                    StageFlow.Role.Pre -> "Pre"
                    StageFlow.Role.Bridge -> "Bridge"
                    StageFlow.Role.Main -> "Main"
                    StageFlow.Role.Outro -> "Outro"
                    StageFlow.Role.Fast -> "Fast"
                },
                frames = part.frames,
                sampleRate = part.sampleRate,
            )
        }
        val outros = compiled.segmentByOutro.map { (number, segment) ->
            val part = flow.outro(number)!!
            Section(
                segment = segment,
                label = "Outro %02d".format(number),
                frames = part.frames,
                sampleRate = part.sampleRate,
            )
        }
        // Bridge before Main reads better, and outros last: that is the order they are heard in.
        val order = listOf("Pre (short)", "Pre", "Bridge", "Main")
        return structural.sortedBy { order.indexOf(it.label).let { p -> if (p < 0) order.size else p } } +
            outros.sortedBy { it.label }
    }

    /**
     * A one-line statement of what the timings are.
     *
     * The screen must show this. A flow assembled by this project is not a reproduction of the
     * game's behaviour, and a listener who is not told would reasonably assume it is.
     */
    fun provenanceLabel(flow: StageFlow): String = when (flow.provenance) {
        StageFlow.Provenance.PROVISIONAL ->
            "Testing configuration: section timings are this app's choice, not the game's."
        StageFlow.Provenance.VERIFIED ->
            "Timings verified against external evidence."
    }

    /** Compact summary for a header line, e.g. `5 parts, 2 outros`. */
    fun summary(flow: StageFlow): String {
        val parts = flow.parts.size
        val outros = flow.outros().size
        val partText = if (parts == 1) "1 part" else "$parts parts"
        return if (outros == 0) {
            partText
        } else {
            val outroText = if (outros == 1) "1 outro" else "$outros outros"
            "$partText, $outroText"
        }
    }

    /**
     * The section to start with.
     *
     * The full prelude when present, otherwise whichever prelude exists, otherwise the main
     * section. Never an outro: starting at an ending is not a listen, it is a fragment.
     */
    fun entrySection(compiled: com.mylittleblover.mkwplayer.graph.StageFlowCompiler.Compiled): Int =
        compiled.segmentByRole[StageFlow.Role.Pre]
            ?: compiled.segmentByRole[StageFlow.Role.PreShort]
            ?: compiled.segmentByRole[StageFlow.Role.Main]
            ?: compiled.segmentByRole.values.first()

    /** Total frames across the structural parts, for an approximate total duration. */
    fun totalFrames(flow: StageFlow): Long {
        val structural = flow.parts
            .filter { it.role != StageFlow.Role.Outro }
            .sumOf { it.frames }
        return if (structural > 0) structural else flow.parts.sumOf { it.frames }
    }
}
