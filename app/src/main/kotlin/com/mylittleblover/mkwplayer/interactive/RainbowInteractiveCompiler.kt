package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G

/** Rainbow's independent regions; all offsets are source sample coordinates. */
internal object RainbowInteractiveCompiler {
    // Corrected user reference: Fan source 0 at 30695935; Final source 0 at 30914669.
    // Multiple independent 48 kHz windows agree. This is not a named MINF event.
    const val FINAL_FROM_FANFARE_FRAMES = 218_734L
    // Verified v1.8.0 Rainbow transition timings (Default.game__SoundBgmSetting & 0x0243d500/0x0243d8e0):
    const val AREA1_TO_AREA2_FADE_SEC = 0.30
    const val AREA1_MELODY_FADE_MS = 300
    const val AREA3_FADEOUT_DELAY_SEC = 1.20
    const val AREA3_FADEOUT_SEC = 3.00
    const val FINAL_PRE_FADEIN_SEC = 2.50
    const val RAINBOW_FANFARE_DELAY_FRAMES = 9_600L // 0.20s @ 48 kHz
    const val RAINBOW_FINAL_PRE_FADEOUT_SEC = 0.10
    val roles = listOf("Area1_Pre","Area1_Bridge","Area1_Inst","Area1_Melody","Area2",
        "Area2_Outro00","Area2_Outro01","Area2_Outro02","Area2_Outro03","Area2_Outro04",
        "Area3_Pre","Area3","Final_Pre","Final","FinalLapFanfare")

    /** Locates the `BGM_Rainbow_Area1_Melody.bwav` track index inside a compiled Rainbow spec. */
    fun area1MelodyTrackId(spec: G.Spec): Int? {
        val melodyAssetIdx = spec.assets.indexOfFirst { it.path == "BGM_Rainbow_Area1_Melody.bwav" }
        if (melodyAssetIdx < 0) return null
        return spec.tracks.firstOrNull { track ->
            track.clips.any { clip -> clip.asset == melodyAssetIdx }
        }?.index
    }

    /** True when a queued interactive transition is Rainbow Road's Area 1 -> Area 2 split fadeout (0x0243d500). */
    fun isArea1ToArea2(plan: InteractivePlaybackPlan, fromSegment: Int, toSegment: Int): Boolean =
        plan.name.equals("Rainbow", ignoreCase = true) &&
            plan.labels[fromSegment] == "Area1" &&
            plan.labels[toSegment] == "Area2"

    /** True only while actively playing the Rainbow Road Area 1 body where Area1_Melody exists. */
    fun isRainbowArea1Stage(stageKey: String?): Boolean = stageKey == "Area1"

    data class Source(val fileName: String,val sampleRate: Int,val channels: Int,val frames: Long,
                      val loopStart: Long?,val loopEnd: Long?,val sha256: ByteArray)
    data class Prepared(val spec: G.Spec,val packet: ByteArray,val segments: Map<String,Int>,val assets: List<Source>)
    fun prepare(
        sources: List<Source>,
        catalog: MinfCatalog,
        allowPreludeToFast: Boolean = false,
        perfectBeatEnabled: Boolean = false,
    ): Prepared {
        require(sources.size==roles.size && sources.map { it.fileName }.distinct().size==roles.size)
        val assets=roles.map { role -> sources.single { it.fileName=="BGM_Rainbow_$role.bwav" } }
        val byRole=roles.zip(assets).toMap()
        fun meta(role: String)=catalog.part("Rainbow",role) ?: error("Missing Rainbow MINF: $role")
        assets.forEachIndexed { i,s ->
            val r=meta(roles[i])
            require(s.sampleRate==r.rate && s.channels in 1..2 && s.sha256.size==32 && s.frames>=r.loopEnd)
            if(r.loopStart<r.loopEnd) require(s.loopStart==r.loopStart && s.loopEnd==r.loopEnd)
        }
        val inst=byRole.getValue("Area1_Inst");val melody=byRole.getValue("Area1_Melody")
        require(inst.frames==melody.frames && inst.loopStart==melody.loopStart && inst.loopEnd==melody.loopEnd &&
            meta("Area1_Inst").phase==meta("Area1_Melody").phase)
        val names=listOf("Area1_Pre","Area1_Bridge","Area1","Area2","Area2_Outro00","Area2_Outro01",
            "Area2_Outro02","Area2_Outro03","Area2_Outro04","Area3_Pre","Area3","Final_Pre","Final","FinalLapFanfare")
        val ids=names.mapIndexed { i,n -> n to 1001+i }.toMap()
        fun source(name: String)=byRole.getValue(if(name=="Area1") "Area1_Inst" else name)
        fun record(name: String)=meta(if(name=="Area1") "Area1_Inst" else name)
        var cueId=1500
        val segments=names.map { name ->
            val s=source(name);val r=record(name)
            val cues=buildList {
                if(name.endsWith("_Pre")) {
                    val gridBeats = MinfCatalog.preTransitionGridBeats("Rainbow", name)
                    r.gridLoopCandidates(gridBeats).forEach { add(G.Cue(cueId++, "pre.exit", it)) }
                    if (allowPreludeToFast && perfectBeatEnabled) {
                        r.beats.filter { it.label == 1 && it.frame in 0L..s.frames }
                            .forEach { add(G.Cue(cueId++, "beat1.exit", it.frame)) }
                    }
                }
                if(name in listOf("Area1","Area3")) r.beats.filter { it.label==1 }.forEach { add(G.Cue(cueId++,"bar.exit",it.frame)) }
                if(name=="Area2") (0..4).forEach { n ->
                    val label="Full_ToOutro%02d".format(n)
                    add(G.Cue(cueId++,label,r.event(label)!!.single()))
                }
            }.sortedBy { it.frame }
            val exit=when {
                name.startsWith("Area2_Outro") -> r.event("Outro_ToNextPre")!!.single()
                name=="FinalLapFanfare" -> FINAL_FROM_FANFARE_FRAMES
                else -> s.frames
            }
            require(exit in 1..s.frames)
            G.Segment(ids.getValue(name),exit=exit,
                loop=s.loopStart?.let { G.Loop(it,s.loopEnd!!) },cues=cues)
        }
        var trackId=100;var clipId=200
        val tracks=names.flatMap { name ->
            (if(name=="Area1") listOf("Area1_Melody","Area1_Inst") else listOf(name)).map { role ->
                val asset=roles.indexOf(role)
                G.Track(trackId++,ids.getValue(name),listOf(G.Clip(clipId++,asset,sourceLength=assets[asset].frames)))
            }
        }
        var transitionId=300
        val transitions=mutableListOf<G.Transition>()
        val bpm = meta("Area1_Inst").bpm
        fun fade(seconds: Double, offsetSeconds: Double = 0.0) =
            G.Fade(beats = seconds * bpm / 60.0, offset = offsetSeconds * bpm / 60.0, shape = G.Shape.Linear)
        fun link(from: String,to: String,cue: String?="bar.exit",bridge: String?=null,
                 sync: G.SyncPoint=G.SyncPoint.NextCustomCue,tail: Boolean=false,
                 fadeOut: G.Fade=G.Fade(),fadeIn: G.Fade=G.Fade(),
                 timeline: G.TransitionTimeline=G.TransitionTimeline(0,-record(to).phase,0,0)) {
            transitions+=G.Transition(transitionId++,listOf(ids.getValue(from)),listOf(ids.getValue(to)),
                sync=sync,sourceCue=cue,bridge=bridge?.let { ids.getValue(it) } ?: -1,
                postExit=tail,fadeIn=fadeIn,fadeOut=fadeOut,timeline=timeline)
        }
        val bridgeExit=meta("Area1_Bridge").event("Bridge_ToFull")!!.single()
        val bridgePhase=meta("Area1_Bridge").phase
        link("Area1_Pre","Area1","pre.exit","Area1_Bridge",tail=true,
            timeline=G.TransitionTimeline(-bridgePhase,bridgeExit-bridgePhase-meta("Area1_Inst").phase,bridgeExit-bridgePhase,0))
        // Verified v1.8.0 (0x0243d500): Melody fades immediately (0.30s via LayerGain), while Inst
        // continues to bar.exit then fades out 0.30s (postExit=true) as Area2 enters on-beat.
        link("Area1","Area2",tail=true,fadeOut=fade(AREA1_TO_AREA2_FADE_SEC))
        link("Area3_Pre","Area3","pre.exit")
        // Verified v1.8.0: Area3 holds 1.20s then fades out 3.00s; Final_Pre immediately fades in 2.50s.
        link(
            from = "Area3",
            to = "Final_Pre",
            cue = if (perfectBeatEnabled) "bar.exit" else null,
            sync = if (perfectBeatEnabled) G.SyncPoint.NextCustomCue else G.SyncPoint.Immediate,
            tail = true,
            fadeOut = fade(AREA3_FADEOUT_SEC, offsetSeconds = AREA3_FADEOUT_DELAY_SEC),
            fadeIn = fade(FINAL_PRE_FADEIN_SEC),
        )
        // Verified v1.8.0 (Rainbow_FanfareDelay = 0.20s = 9,600 f): Final_Pre fades out 0.10s,
        // Fanfare enters at +9,600 f, and Final enters at +9,600 + 218,734 f while Fanfare tail decays.
        val rainbowFinalStart = RAINBOW_FANFARE_DELAY_FRAMES + FINAL_FROM_FANFARE_FRAMES
        val rainbowFanfareTimeline = G.TransitionTimeline(RAINBOW_FANFARE_DELAY_FRAMES, rainbowFinalStart, rainbowFinalStart, 0L)
        link("Final_Pre","Final","pre.exit","FinalLapFanfare",tail=true,
            fadeOut=fade(RAINBOW_FINAL_PRE_FADEOUT_SEC),
            timeline=rainbowFanfareTimeline)
        link("FinalLapFanfare","Final",null,sync=G.SyncPoint.ExitCue,tail=true)
        if (allowPreludeToFast) {
            for (preName in listOf("Area1_Pre", "Area3_Pre")) {
                link(
                    from = preName,
                    to = "Final",
                    cue = if (perfectBeatEnabled) "beat1.exit" else null,
                    bridge = "FinalLapFanfare",
                    sync = if (perfectBeatEnabled) G.SyncPoint.NextCustomCue else G.SyncPoint.Immediate,
                    tail = true,
                    fadeOut = fade(RAINBOW_FINAL_PRE_FADEOUT_SEC),
                    timeline = rainbowFanfareTimeline,
                )
            }
        }
        for(n in 0..4) {
            val outro="Area2_Outro%02d".format(n)
            link("Area2",outro,"Full_ToOutro%02d".format(n),timeline=G.TransitionTimeline(0,0,0,0))
            link(outro,"Area3_Pre",null,sync=G.SyncPoint.ExitCue,tail=true)
        }
        val spec=G.Spec(assets=assets.map { G.Asset(it.fileName,it.sampleRate,it.channels,it.frames,it.sha256,lookAheadFrames=it.sampleRate*2L) },
            segments=segments,tracks=tracks,transitions=transitions,bpm=meta("Area1_Inst").bpm)
        return Prepared(spec,G.build(spec),ids,assets)
    }
}
