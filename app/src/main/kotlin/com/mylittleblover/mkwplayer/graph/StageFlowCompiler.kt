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
 *            ├─▶ Pre ──(with bridge)──▶ Main ⟲ ──(outro)──▶ Outro
 * Pre ───────┘
 * ```
 *
 * Both preludes name the same next segment, so whichever the game would have used lands in the
 * same place. The main loop is a **segment** loop rather than a clip loop: the measured window
 * `493714..3373711` runs to the end of the file, and a segment loop keeps the exit reachable
 * while a clip loop would bound it by the source window.
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
    private const val SEGMENT_OUTRO = 1104

    private const val TRACK_BASE = 1200
    private const val CLIP_BASE = 1300
    private const val TRANSITION_BASE = 1400

    data class Compiled(
        val packet: ByteArray,
        val flow: StageFlow,
        /** Segment index for each role that made it into the graph. */
        val segmentByRole: Map<StageFlow.Role, Int>,
        /** Asset path per asset index, in the order the caller must bind them. */
        val assetPaths: List<String>,
    )

    /** Role order used for asset indices; stable so the caller can bind by position. */
    private val ROLE_ORDER = listOf(
        StageFlow.Role.PreShort,
        StageFlow.Role.Pre,
        StageFlow.Role.Bridge,
        StageFlow.Role.Main,
        StageFlow.Role.Outro,
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

        val roles = ROLE_ORDER.filter { flow.part(it) != null }
        val missing = roles.map { flow.part(it)!!.fileName }.filter { it !in digests }
        require(missing.isEmpty()) { "no digest supplied for: $missing" }

        val segmentByRole = roles.associateWith { role ->
            when (role) {
                StageFlow.Role.PreShort -> SEGMENT_PRE_SHORT
                StageFlow.Role.Pre -> SEGMENT_PRE
                StageFlow.Role.Bridge -> SEGMENT_BRIDGE
                StageFlow.Role.Main -> SEGMENT_MAIN
                StageFlow.Role.Outro -> SEGMENT_OUTRO
            }
        }
        val assetIndexByRole = roles.withIndex().associate { (position, role) -> role to position }

        val assets = roles.map { role ->
            val part = flow.part(role)!!
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

        val segments = roles.map { role ->
            val part = flow.part(role)!!
            MusicGraphBuilder.Segment(
                index = segmentByRole[role]!!,
                entry = 0,
                exit = part.frames,
                loop = if (role == StageFlow.Role.Main && part.hasLoop) {
                    MusicGraphBuilder.Loop(part.loopStart!!, part.loopEnd!!)
                } else {
                    null
                },
            )
        }

        val tracks = roles.map { role ->
            val part = flow.part(role)!!
            val assetIndex = assetIndexByRole[role]!!
            MusicGraphBuilder.Track(
                index = TRACK_BASE + assetIndex,
                segment = segmentByRole[role]!!,
                clips = listOf(
                    MusicGraphBuilder.Clip(
                        index = CLIP_BASE + assetIndex,
                        asset = assetIndex,
                        sourceLength = part.frames,
                    ),
                ),
            )
        }

        val bridgeIndex = segmentByRole[StageFlow.Role.Bridge]
        val mainIndex = segmentByRole[StageFlow.Role.Main]!!
        val outroIndex = segmentByRole[StageFlow.Role.Outro]

        val transitions = mutableListOf<MusicGraphBuilder.Transition>()
        var nextTransition = TRANSITION_BASE

        // Preludes hand over to the main section. The bridge is declared on this transition
        // because that is where the engine looks for it when planning the move.
        for (role in listOf(StageFlow.Role.PreShort, StageFlow.Role.Pre)) {
            val from = segmentByRole[role] ?: continue
            transitions += MusicGraphBuilder.Transition(
                index = nextTransition++,
                sources = listOf(from),
                destinations = listOf(mainIndex),
                sync = flow.timing.preludeExit,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
                bridge = bridgeIndex ?: -1,
                fadeOut = MusicGraphBuilder.Fade(flow.timing.bridgeFadeOutBeats),
                fadeIn = MusicGraphBuilder.Fade(flow.timing.bridgeFadeInBeats),
            )
        }

        // The bridge also needs its own way out, or a flow that enters it never reaches Main.
        if (bridgeIndex != null) {
            transitions += MusicGraphBuilder.Transition(
                index = nextTransition++,
                sources = listOf(bridgeIndex),
                destinations = listOf(mainIndex),
                sync = flow.timing.bridgeEntry,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
            )
        }

        if (outroIndex != null) {
            transitions += MusicGraphBuilder.Transition(
                index = nextTransition++,
                sources = listOf(mainIndex),
                destinations = listOf(outroIndex),
                sync = flow.timing.outroEntry,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
            )
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
            assetPaths = roles.map { flow.part(it)!!.fileName },
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
}
