package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.audio.BwavHeader
import java.security.MessageDigest

/** One graph and binding order shared by the Android session and its Native preview. */
internal data class InteractivePlaybackPlan(
    val name: String,val spec: G.Spec,val packet: ByteArray,val assetPaths: List<String>,
    val entry: Int,val next: Map<Int,Int>,val outros: Map<Int,Int>,val fast: Int?,
    val labels: Map<Int,String>,val standard: StageFlowCompiler.Compiled?=null,val streamed: Boolean=false,
    val versions: List<Version> = emptyList(),
    val fastByMain: Map<Int,Int> = emptyMap(),
    val outroVariants: Map<Int,List<Int>> = emptyMap(),
    val regions: Map<Int,Int> = emptyMap(),
    val preparedAssets: Set<String> = emptySet(),
    val directBridge: Int? = null,
    val originalEntry: Int? = null,
    val entryPreparedAssets: Map<Int,Set<String>> = emptyMap(),
    val entryAutomaticNext: Map<Int,Int> = emptyMap(),
    val advanceViaOutro: Set<Int> = emptySet(),
    val customInitialSegments: List<Int> = emptyList(),
    val overlayTrackIndices: List<Int> = emptyList(),
    val overlayDefaultEnabled: Boolean = false,
    val splitFadeOnAdvance: Map<Int, Int> = emptyMap(),
    val splitFadeOverlayMs: Int = 300,
) {
    data class Version(val number: Int,val main: Int,val fast: Int?)

    /** Start choices are real independent sources; Bridge is an authored overlap, not an entry shortcut. */
    val initialSegments: List<Int> get() {
        if (customInitialSegments.isNotEmpty()) return customInitialSegments
        val roles=standard?.segmentByRole
        if (roles == null) return (listOf(originalEntry ?: entry) + listOfNotNull(directBridge) +
            listOf("Area1", "Area2", "Area3_Pre", "Area3").mapNotNull { label -> labels.entries.singleOrNull { it.value == label }?.key }).distinct()
        val allowed=listOf(StageFlow.Role.Pre,StageFlow.Role.PreShort)+
            listOfNotNull(directBridge?.let { StageFlow.Role.Bridge })+listOf(StageFlow.Role.Main)+
            listOf(StageFlow.Role.Fast)
        return (allowed.mapNotNull { if (it == StageFlow.Role.Bridge) directBridge else roles[it] } + regions.toSortedMap().values + versions.flatMap { listOfNotNull(it.main, it.fast) }).distinct()
    }


    fun startingAt(label: String?): InteractivePlaybackPlan {
        if (label == null) return this
        val match = initialSegments.singleOrNull { labels[it] == label }
            ?: error("The requested starting stage is not available: $label")
        return copy(entry = match)
    }

    data class Options(
        val segmentSwitchFadeOutMs: Int = 0,
        val perfectBeatEnabled: Boolean = false,
        val allowPreludeToFast: Boolean = false,
    )

    /** Authored Outro markers lead into the next looping prelude without another user event. */
    val automaticNext: Map<Int,Int> get()=outros.values.mapNotNull { from -> next[from]?.let { from to it } }.toMap()+entryAutomaticNext
    companion object {
        internal fun applySegmentSwitchFadeOut(spec: G.Spec, fadeOutMs: Int): G.Spec {
            if (fadeOutMs <= 0) return spec
            val fadeBeats = (fadeOutMs.coerceAtMost(30_000) / 1000.0) * spec.bpm / 60.0
            if (fadeBeats <= 0.0) return spec
            return spec.copy(
                transitions = spec.transitions.map { rule ->
                    val isAuthoredStructural =
                        rule.bridge >= 0 ||
                            rule.sync == G.SyncPoint.ExitCue ||
                            rule.sourceCue == "pre.exit" ||
                            rule.sourceCue?.startsWith("outro.") == true ||
                            rule.sourceCue?.startsWith("Full_ToOutro") == true
                    if (isAuthoredStructural || rule.fadeOut.beats > 0.0 || rule.fadeOut.offset != 0.0) {
                        rule
                    } else {
                        rule.copy(
                            postExit = true,
                            fadeOut = G.Fade(beats = fadeBeats, offset = 0.0, shape = G.Shape.Linear),
                        )
                    }
                },
            )
        }

        internal fun applySwitchFadeToPlan(plan: InteractivePlaybackPlan, fadeOutMs: Int): InteractivePlaybackPlan {
            if (fadeOutMs <= 0) return plan
            val updatedSpec = applySegmentSwitchFadeOut(plan.spec, fadeOutMs)
            val updatedPacket = G.build(updatedSpec)
            return plan.copy(
                spec = updatedSpec,
                packet = updatedPacket,
                standard = plan.standard?.copy(packet = updatedPacket),
            )
        }

        fun normal(
            flow: StageFlow,
            bytes: Map<String,ByteArray>,
            catalog: MinfCatalog,
            options: Options = Options(),
        ): InteractivePlaybackPlan {
            val digests = flow.parts.associate { part ->
                part.fileName to MessageDigest.getInstance("SHA-256").digest(bytes.getValue(part.fileName))
            }
            val prepared=MinfInteractiveCompiler.prepare(
                flow = flow,
                digests = digests,
                catalog = catalog,
                allowPreludeToFast = options.allowPreludeToFast,
                includeBeat1Cues = options.perfectBeatEnabled,
            )
            val compiled=prepared.compiled
            val roles=compiled.segmentByRole
            val main=roles.getValue(StageFlow.Role.Main)
            val entry=roles[StageFlow.Role.Pre] ?: roles[StageFlow.Role.PreShort] ?: main
            val next=listOf(StageFlow.Role.Pre,StageFlow.Role.PreShort).mapNotNull { roles[it]?.let { id -> id to main } }.toMap()
            val labels=roles.entries.associate { it.value to it.key.name }+
                compiled.segmentByOutro.entries.associate { it.value to "Outro%02d".format(it.key) }
            val preparedAssets = setOfNotNull(
                flow.part(StageFlow.Role.Bridge)?.fileName,
                flow.part(StageFlow.Role.Main)?.fileName,
            )
            val entryPreparedAssets = buildMap {
                roles[StageFlow.Role.Pre]?.let { id ->
                    flow.part(StageFlow.Role.Pre)?.fileName?.let { put(id, setOf(it)) }
                }
                roles[StageFlow.Role.PreShort]?.let { id ->
                    flow.part(StageFlow.Role.PreShort)?.fileName?.let { put(id, setOf(it)) }
                }
                roles[StageFlow.Role.Fast]?.let { id ->
                    flow.part(StageFlow.Role.Fast)?.fileName?.let { put(id, setOf(it)) }
                }
            }
            val base=InteractivePlaybackPlan(flow.name,prepared.spec,compiled.packet,compiled.assetPaths,entry,next,
                compiled.segmentByOutro,roles[StageFlow.Role.Fast],labels,compiled,
                streamed=true,preparedAssets=preparedAssets,entryPreparedAssets=entryPreparedAssets)
            val versionExtended = FullVersionCompiler.extend(
                base = base,
                flow = flow,
                bytes = bytes,
                catalog = catalog,
                includeBeat1Cues = options.perfectBeatEnabled,
            )
            val regionExtended = DonkeyRegionCompiler.extend(versionExtended, flow, bytes, catalog)
            val fanfared = FinalLapFanfareCompiler.extend(regionExtended, bytes, perfectBeatEnabled = options.perfectBeatEnabled)
            val bridged = DirectBridgeCompiler.normal(fanfared, flow, catalog)
            return applySwitchFadeToPlan(bridged, options.segmentSwitchFadeOutMs)
        }
        fun rainbow(
            bytes: Map<String,ByteArray>,
            catalog: MinfCatalog,
            options: Options = Options(),
        ): InteractivePlaybackPlan {
            val sources=RainbowInteractiveCompiler.roles.map { role ->
                val name="BGM_Rainbow_$role.bwav";val data=bytes[name] ?: error("Missing $name")
                val info=(BwavHeader.read(data) as? BwavHeader.Result.Ok)?.info ?: error("Unreadable $name")
                RainbowInteractiveCompiler.Source(name,info.sampleRate,info.channels,info.frames.toLong(),
                    info.loopStart?.toLong(),info.loopEnd?.toLong(),MessageDigest.getInstance("SHA-256").digest(data))
            }
            val p=RainbowInteractiveCompiler.prepare(
                sources = sources,
                catalog = catalog,
                allowPreludeToFast = options.allowPreludeToFast,
                perfectBeatEnabled = options.perfectBeatEnabled,
            )
            val pairs=listOf("Area1_Pre" to "Area1","Area1" to "Area2","Area3_Pre" to "Area3",
                "Area3" to "Final_Pre","Final_Pre" to "Final","FinalLapFanfare" to "Final")+
                (0..4).map { "Area2_Outro%02d".format(it) to "Area3_Pre" }
            val area1SegId = p.segments.getValue("Area1")
            val melodyTrackIds = listOfNotNull(RainbowInteractiveCompiler.area1MelodyTrackId(p.spec))
            val fastId = if (options.allowPreludeToFast) p.segments.getValue("Final") else null
            val base = InteractivePlaybackPlan("Rainbow",p.spec,p.packet,p.assets.map { it.fileName },p.segments.getValue("Area1_Pre"),
                pairs.associate { p.segments.getValue(it.first) to p.segments.getValue(it.second) },
                (0..4).associateWith { p.segments.getValue("Area2_Outro%02d".format(it)) },fastId,
                p.segments.entries.associate { it.value to it.key },streamed=true,
                // The waiting prelude repeats while the bridge and layered main arrange their next entry.
                // Keep this loop resident so the decode worker cannot interrupt it at a page or loop boundary.
                preparedAssets=setOf("BGM_Rainbow_FinalLapFanfare.bwav"),
                entryPreparedAssets=mapOf(
                    p.segments.getValue("Area1_Pre") to setOf("BGM_Rainbow_Area1_Pre.bwav","BGM_Rainbow_FinalLapFanfare.bwav"),
                    p.segments.getValue("Area3_Pre") to setOf("BGM_Rainbow_Area3_Pre.bwav","BGM_Rainbow_FinalLapFanfare.bwav"),
                ),
                advanceViaOutro=setOf(p.segments.getValue("Area2")),
                overlayTrackIndices=melodyTrackIds,
                overlayDefaultEnabled=true,
                splitFadeOnAdvance=mapOf(area1SegId to p.segments.getValue("Area2")),
                splitFadeOverlayMs=RainbowInteractiveCompiler.AREA1_MELODY_FADE_MS)
            return applySwitchFadeToPlan(DirectBridgeCompiler.rainbow(base, catalog), options.segmentSwitchFadeOutMs)
        }
    }
}
