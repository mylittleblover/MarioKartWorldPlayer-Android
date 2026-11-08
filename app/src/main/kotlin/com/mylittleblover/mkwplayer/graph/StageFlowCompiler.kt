package com.mylittleblover.mkwplayer.graph

import com.mylittleblover.mkwplayer.library.StageFlow
import java.security.MessageDigest

/**
 * Compiles a [StageFlow] into a multi-segment graph.
 *
 * The shape is the one the flow describes:
 *
 * ```
 * Pre_Short ─┐
 *            ├─▶ Main ⟲ ──(transition)──▶ Outro00
 * Pre ───────┘         └─(transition)──▶ Outro01 …
 *            (bridge declared on the prelude transition)
 * ```
 *
 * Both preludes name the same main section, so whichever the game would have used lands in the
 * same place. The main loop is a **segment** loop rather than a clip loop: the measured window
 * runs to the end of the file, and a segment loop keeps the exit reachable while a clip loop
 * would bound it by the source window.
 *
 * Several outros are allowed, which is why transcripts give each one its own segment and its own
 * transition. Their indices follow their outro number so a caller can ask for "outro 2" without
 * having to know file order.
 *
 * Every identifier comes from a reserved block because the engine treats node, cue, clip and
 * transition indices as one namespace: an overlap is rejected as `INVALID_COMPILED_GRAPH`.
 */
internal object StageFlowCompiler {

    /** Reserved index blocks, far enough apart that an overlap cannot happen by accident. */
    private const val SEGMENT_PRE_SHORT = 1100
    private const val SEGMENT_PRE = 1101
    private const val SEGMENT_BRIDGE = 1102
    private const val SEGMENT_MAIN = 1103

    /** The fast arrangement of main. A distinct segment, because it is a distinct asset. */
    private const val SEGMENT_FAST = 1104

    /** Outro segments start here; outro N gets `SEGMENT_OUTRO_BASE + N`. */
    private const val SEGMENT_OUTRO_BASE = 1110

    private const val TRACK_BASE = 1200
    private const val CLIP_BASE = 1300
    private const val TRANSITION_BASE = 1400

    data class Compiled(
        val packet: ByteArray,
        val flow: StageFlow,
        /** Segment index for each non-outro role, plus every outro number. */
        val segmentByRole: Map<StageFlow.Role, Int>,
        /** Segment index per outro number. */
        val segmentByOutro: Map<Int, Int>,
        /** Asset path per asset index, in the order the caller must bind them. */
        val assetPaths: List<String>,
    ) {
        /** Segment for a specific outro, or null when the flow does not carry it. */
        fun segmentForOutro(number: Int): Int? = segmentByOutro[number]
    }

    /** Role order for the structural parts; outros are appended after these. */
    private val STRUCTURAL_ROLES = listOf(
        StageFlow.Role.PreShort,
        StageFlow.Role.Pre,
        StageFlow.Role.Bridge,
        StageFlow.Role.Main,
        StageFlow.Role.Fast,
    )

    /**
     * Compiles a flow using [digests] for the asset SHA-256.
     *
     * A digest is required rather than optional: this wire version demands one per asset, and a
     * zeroed digest would let the engine bind a different file than the graph declares.
     */
    fun compile(
        flow: StageFlow,
        digests: Map<String, ByteArray>,
        transportSampleRate: Int = 48000,
    ): Compiled {
        flow.part(StageFlow.Role.Main)
            ?: throw IllegalArgumentException("flow '${flow.name}' has no Main part")

        // Asset order: structural parts first, then outros ordered by their number, so a caller
        // that binds by position gets a stable mapping.
        val structural = STRUCTURAL_ROLES.filter { flow.part(it) != null }
        val outros = flow.outros().sortedBy { it.outroNumber ?: Int.MAX_VALUE }
        val ordered = structural.map { flow.part(it)!! } + outros

        val missing = ordered.map { it.fileName }.filter { it !in digests }
        require(missing.isEmpty()) { "no digest supplied for: $missing" }

        val segmentByRole = structural.associateWith { role ->
            when (role) {
                StageFlow.Role.PreShort -> SEGMENT_PRE_SHORT
                StageFlow.Role.Pre -> SEGMENT_PRE
                StageFlow.Role.Bridge -> SEGMENT_BRIDGE
                StageFlow.Role.Main -> SEGMENT_MAIN
                StageFlow.Role.Fast -> SEGMENT_FAST
                StageFlow.Role.Outro -> error("outros are indexed by number, not by role")
            }
        }
        val segmentByOutro = outros.associate { part ->
            val number = part.outroNumber
                ?: throw IllegalArgumentException("outro '${part.fileName}' has no number")
            require(number in 0..99) { "outro number $number is out of range" }
            number to (SEGMENT_OUTRO_BASE + number)
        }

        val assets = ordered.map { part ->
            val digest = digests[part.fileName]!!
            require(digest.size == 32) {
                "digest for ${part.fileName} must be 32 bytes, got ${digest.size}"
            }
            MusicGraphBuilder.Asset(
                path = part.fileName,
                sampleRate = part.sampleRate,
                channels = part.channels,
                frames = part.frames,
                sha256 = digest,
            )
        }
        val assetIndex = ordered.withIndex().associate { (position, part) -> part.fileName to position }

        val segments = ordered.map { part ->
            MusicGraphBuilder.Segment(
                index = segmentIndexOf(part, segmentByRole, segmentByOutro),
                entry = 0,
                exit = part.frames,
                // Every section that is not an ending loops, because the engine ends a segment by going
                // inactive and never follows a transition by itself. A prelude that simply ended would stop
                // the performance dead, which is exactly what it did before this: the prelude played, the
                // prelude ended, and nothing was left playing to transition from.
                //
                // An ending does not loop: it is the last thing heard, and looping it would mean the
                // performance could never complete.
                loop = loopFor(part),
            )
        }

        val tracks = ordered.map { part ->
            val index = assetIndex[part.fileName]!!
            MusicGraphBuilder.Track(
                index = TRACK_BASE + index,
                segment = segmentIndexOf(part, segmentByRole, segmentByOutro),
                clips = listOf(
                    MusicGraphBuilder.Clip(
                        index = CLIP_BASE + index,
                        asset = index,
                        sourceLength = part.frames,
                    ),
                ),
            )
        }

        // The whole transition set comes from the planner, so the measured bridge numbers reach the
        // engine through one tested path rather than being rebuilt here. What this compiler still owns
        // is the numbering: the planner takes the first index and the caller keeps the table.
        val plan = StageTransitionPlanner.plan(
            flow = flow,
            segments = StageTransitionPlanner.Segments(
                main = segmentByRole[StageFlow.Role.Main]!!,
                bridge = segmentByRole[StageFlow.Role.Bridge],
                prelude = segmentByRole[StageFlow.Role.Pre],
                preludeShort = segmentByRole[StageFlow.Role.PreShort],
                fast = segmentByRole[StageFlow.Role.Fast],
                outros = segmentByOutro,
            ),
            firstIndex = TRANSITION_BASE,
        )
        val transitions = plan.transitions

        return Compiled(
            packet = MusicGraphBuilder.build(
                MusicGraphBuilder.Spec(
                    assets = assets,
                    segments = segments,
                    tracks = tracks,
                    transitions = transitions,
                    musicalLoop = null,
                    transportSampleRate = transportSampleRate,
                ),
            ),
            flow = flow,
            segmentByRole = segmentByRole,
            segmentByOutro = segmentByOutro,
            assetPaths = ordered.map { it.fileName },
        )
    }

    /** Compiles a flow from the bytes it will actually bind, deriving every digest. */
    fun compileFromBytes(
        flow: StageFlow,
        bytesByFileName: Map<String, ByteArray>,
        transportSampleRate: Int = 48000,
    ): Compiled {
        val digests = flow.parts.associate { part ->
            val bytes = bytesByFileName[part.fileName]
                ?: throw IllegalArgumentException("no bytes supplied for ${part.fileName}")
            part.fileName to MessageDigest.getInstance("SHA-256").digest(bytes)
        }
        return compile(flow, digests, transportSampleRate)
    }

    /**
     * The loop a compiled section carries, or null when it must end.
     *
     * Two rules, and the second is the one that makes a performance work at all:
     *
     * A section the flow measured a loop window for loops over that window. The window is measured from the
     * file's own header, so it is what the game itself loops over.
     *
     * A section with **no** measured window still loops over its whole length, unless it is an ending. A
     * prelude that simply ran to its end would leave the engine with nothing playing, and the engine does not
     * follow a transition on its own -- so the performance would stop after the prelude with no way forward.
     * Looping the whole part is also musically correct here: the prelude is the section the game repeats
     * until the race actually starts.
     */
    private fun loopFor(part: StageFlow.Part): MusicGraphBuilder.Loop? = when {
        part.role == StageFlow.Role.Outro -> null
        part.hasLoop -> MusicGraphBuilder.Loop(part.loopStart!!, part.loopEnd!!)
        else -> MusicGraphBuilder.Loop(0, part.frames)
    }

    private fun segmentIndexOf(
        part: StageFlow.Part,
        byRole: Map<StageFlow.Role, Int>,
        byOutro: Map<Int, Int>,
    ): Int = when (part.role) {
        StageFlow.Role.Outro -> byOutro[part.outroNumber]
            ?: throw IllegalArgumentException("outro '${part.fileName}' has no segment")
        else -> byRole[part.role]
            ?: throw IllegalArgumentException("role ${part.role} has no segment")
    }
}
