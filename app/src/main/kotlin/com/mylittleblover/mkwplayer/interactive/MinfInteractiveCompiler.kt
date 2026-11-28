package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.library.StageFlow

/** Compiles original sample nodes. Unsupported/ambiguous research never falls back to guessed timing. */
internal object MinfInteractiveCompiler {
    data class Prepared(val spec: G.Spec, val compiled: StageFlowCompiler.Compiled, val entryBasis: String = "SOURCE_MINF_PHASE_CANDIDATE")

    fun prepare(
        flow: StageFlow,
        digests: Map<String, ByteArray>,
        catalog: MinfCatalog,
        allowPreludeToFast: Boolean = false,
        includeBeat1Cues: Boolean = false,
    ): Prepared {
        val stage=flow.name.removePrefix("BGM_Stage_")
        require(stage!="Rainbow") { "Rainbow requires its Area/Final graph" }
        val full=catalog.part(stage,"Full") ?: error("No unambiguous Full MINF for $stage")
        val fromBridge=full.event("Full_FromBridge")?.singleOrNull()
        val skip=full.event("Full_SkipForBridge")?.singleOrNull()
        require(fromBridge==null || skip==null || fromBridge==skip) { "Conflicting Full entry markers for $stage" }
        // Zero is an explicit source entry too. It must not fall through to phase pre-entry.
        val fullEntry=fromBridge ?: skip
        val ordered=flow.parts.filter { it.role!=StageFlow.Role.Outro }.sortedBy { it.role.ordinal } + flow.outros().sortedBy { it.outroNumber }
        val indices=ordered.mapIndexed { i,p -> p.fileName to (100+i) }.toMap()
        val byRole=ordered.filter { it.role!=StageFlow.Role.Outro }.associate { it.role to indices.getValue(it.fileName) }
        val byOutro=flow.outros().associate { it.outroNumber!! to indices.getValue(it.fileName) }
        val main=byRole.getValue(StageFlow.Role.Main)
        val fast=catalog.part(stage,"Fast")
        val hasEntry=flow.parts.any { it.role==StageFlow.Role.Pre || it.role==StageFlow.Role.PreShort }
        val bridge=if(hasEntry) catalog.part(stage,"Bridge") else null
        val bridgePart=flow.part(StageFlow.Role.Bridge)
        require((bridge==null)==(bridgePart==null)) { "Bridge audio/MINF mismatch for $stage" }
        val sourceBridgeExit=bridge?.event("Bridge_ToFull")
        // User proposal received in this session: Choco's missing marker is its natural source end.
        // Keep the empty raw event unchanged and expose this different evidence basis to callers.
        val userNaturalEnd=stage=="ChocoMountain" && sourceBridgeExit?.isEmpty()==true && bridgePart!=null
        val bridgeExit=sourceBridgeExit?.singleOrNull() ?: if(userNaturalEnd) bridgePart!!.frames else null
        if(bridge!=null) require(bridgeExit!=null && bridgeExit>=0 && bridgeExit<=bridgePart!!.frames) {
            "$stage requires a separately verified layered/missing-marker bridge plan"
        }
        val confirmed=ConfirmedOutroCatalog.forStage(stage)
            ?: if(flow.outros().isEmpty()) emptyMap() else error("No confirmed Outro mapping for $stage")
        var cueIndex=1000
        val segments=ordered.map { part ->
            val id=indices.getValue(part.fileName)
            when(part.role) {
                StageFlow.Role.Pre,StageFlow.Role.PreShort -> {
                    val role=if(part.role==StageFlow.Role.Pre) "Pre" else "Pre_Short"
                    val r=catalog.part(stage,role) ?: error("No $role MINF for $stage")
                    require(part.loopStart==r.loopStart && part.loopEnd==r.loopEnd) { "$stage $role source loop differs from MINF" }
                    val preCandidates = r.gridLoopCandidates(MinfCatalog.preTransitionGridBeats(stage, role))
                    require(preCandidates.isNotEmpty())
                    val extraBeat1 = if (includeBeat1Cues) {
                        r.beats.filter { it.label == 1 && it.frame in 0L..part.frames }
                            .map { G.Cue(cueIndex++, "beat1.exit", it.frame) }
                    } else emptyList()
                    G.Segment(id,exit=part.frames,loop=G.Loop(r.loopStart,r.loopEnd),
                        cues=(preCandidates.map { G.Cue(cueIndex++,"pre.exit",it) } + extraBeat1).sortedBy { it.frame })
                }
                StageFlow.Role.Bridge -> G.Segment(id,exit=part.frames)
                StageFlow.Role.Main -> {
                    val extraBeat1 = if (includeBeat1Cues) {
                        full.beats.filter { it.label == 1 && it.frame in 0L..part.frames }
                            .map { G.Cue(cueIndex++, "beat1.exit", it.frame) }
                    } else emptyList()
                    G.Segment(id,exit=part.frames,
                        loop=part.loopStart?.let { G.Loop(it,part.loopEnd!!) },
                        cues=(confirmed.flatMap { (n,points) -> points.map { point ->
                            require(point<part.frames) { "$stage Outro$n trigger outside Full source" }
                            G.Cue(cueIndex++,"outro.$n",point)
                        } } + (if(fast!=null && byRole[StageFlow.Role.Fast]!=null) full.loopCandidates.map { G.Cue(cueIndex++,"fast.exit",it) } else emptyList()) + extraBeat1).sortedBy { it.frame })
                }
                StageFlow.Role.Outro -> G.Segment(id,exit=part.frames)
                StageFlow.Role.Fast -> G.Segment(id,exit=part.frames,loop=part.loopStart?.let { G.Loop(it,part.loopEnd!!) })
            }
        }
        var rule=2000
        val transitions=buildList {
            for(role in listOf(StageFlow.Role.Pre,StageFlow.Role.PreShort)) byRole[role]?.let {
                add(G.Transition(rule++,listOf(it),listOf(main),sync=G.SyncPoint.NextCustomCue,
                    sourceCue="pre.exit",bridge=byRole[StageFlow.Role.Bridge] ?: -1,postExit=true,
                    timeline=if(bridge==null) G.TransitionTimeline(0,-full.phase,0,0) else {
                        val handover=bridgeExit!!-bridge.phase
                        G.TransitionTimeline(-bridge.phase,
                            if(fullEntry==null) handover-full.phase else handover,
                            maxOf(0,handover),fullEntry ?: 0)
                    }))
            }
            byRole[StageFlow.Role.Fast]?.let { target ->
                require(fast!=null && full.loopCandidates.isNotEmpty()) { "$stage has no source grid/Fast phase plan" }
                // Research phase-alignment preview. No rate change; Fast is its own real asset.
                add(G.Transition(rule++,listOf(main),listOf(target),sync=G.SyncPoint.NextCustomCue,
                    sourceCue="fast.exit",timeline=G.TransitionTimeline(0,-fast.phase,0,0)))
                if (allowPreludeToFast) {
                    for (preRole in listOf(StageFlow.Role.Pre, StageFlow.Role.PreShort)) {
                        byRole[preRole]?.let { preId ->
                            add(G.Transition(rule++, listOf(preId), listOf(target), sync = G.SyncPoint.NextCustomCue,
                                sourceCue = if (includeBeat1Cues) "beat1.exit" else "pre.exit",
                                timeline = G.TransitionTimeline(0, -fast.phase, 0, 0)))
                        }
                    }
                }
            }
            for((n,to) in byOutro) {
                require(n in confirmed) { "$stage Outro$n has no confirmed Full exit" }
                add(G.Transition(rule++,listOf(main),listOf(to),sync=G.SyncPoint.NextCustomCue,sourceCue="outro.$n"))
            }
        }
        val numerator = full.beats.maxOfOrNull { it.label }?.takeIf { it in 2..12 } ?: 4
        val spec=G.Spec(
            assets=ordered.map { p -> G.Asset(p.fileName,p.sampleRate,p.channels,p.frames,digests.getValue(p.fileName),lookAheadFrames=p.sampleRate*2L) },
            segments=segments,
            tracks=ordered.mapIndexed { i,p -> G.Track(300+i,indices.getValue(p.fileName),listOf(G.Clip(500+i,i,sourceLength=p.frames))) },
            transitions=transitions,bpm=full.bpm,numerator=numerator,
        )
        return Prepared(spec,StageFlowCompiler.Compiled(G.build(spec),flow,byRole,byOutro,ordered.map { it.fileName }),
            if(userNaturalEnd) "USER_PROPOSED_BRIDGE_END" else if(fullEntry!=null) "SOURCE_MINF_EXPLICIT_FULL_ENTRY" else "SOURCE_MINF_PHASE_CANDIDATE")
    }
}
