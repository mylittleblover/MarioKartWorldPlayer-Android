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
                loop = if (part.role == StageFlow.Role.Main && part.hasLoop) {
                    MusicGraphBuilder.Loop(part.loopStart!!, part.loopEnd!!)
                } else {
                    null
                },
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

        val bridgeSegment = segmentByRole[StageFlow.Role.Bridge]
        val mainSegment = segmentByRole[StageFlow.Role.Main]!!

        val transitions = mutableListOf<MusicGraphBuilder.Transition>()
        var nextTransition = TRANSITION_BASE

        // Preludes hand over to the main section, declaring the bridge on that transition.
        for (role in listOf(StageFlow.Role.PreShort, StageFlow.Role.Pre)) {
            val from = segmentByRole[role] ?: continue
            transitions += MusicGraphBuilder.Transition(
                index = nextTransition++,
                sources = listOf(from),
                destinations = listOf(mainSegment),
                sync = flow.timing.preludeExit,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
                bridge = bridgeSegment ?: -1,
                fadeOut = MusicGraphBuilder.Fade(flow.timing.bridgeFadeOutBeats),
                fadeIn = MusicGraphBuilder.Fade(flow.timing.bridgeFadeInBeats),
            )
        }

        // The bridge needs its own way out, or a flow that enters it never reaches main.
        if (bridgeSegment != null) {
            transitions += MusicGraphBuilder.Transition(
                index = nextTransition++,
                sources = listOf(bridgeSegment),
                destinations = listOf(mainSegment),
                sync = flow.timing.bridgeEntry,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
            )
        }

        // One transition per outro. Listing them all as destinations on one rule would leave the
        // engine to choose; a caller asking for outro 2 must get outro 2.
        for (number in segmentByOutro.keys.sorted()) {
            transitions += MusicGraphBuilder.Transition(
                index = nextTransition++,
                sources = listOf(mainSegment),
                destinations = listOf(segmentByOutro.getValue(number)),
                sync = flow.timing.outroEntry,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
            )
        }

        // Switching from one outro to another, for a request that has not been executed yet.
        // Each ordered pair gets its own rule so a replacement is addressable directly.
        val outrosSorted = segmentByOutro.keys.sorted()
        for (from in outrosSorted) {
            for (to in outrosSorted) {
                if (from == to) continue
                transitions += MusicGraphBuilder.Transition(
                    index = nextTransition++,
                    sources = listOf(segmentByOutro.getValue(from)),
                    destinations = listOf(segmentByOutro.getValue(to)),
                    sync = flow.timing.outroSwitch,
                    entry = MusicGraphBuilder.EntryMode.EntryCue,
                )
            }
        }

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
