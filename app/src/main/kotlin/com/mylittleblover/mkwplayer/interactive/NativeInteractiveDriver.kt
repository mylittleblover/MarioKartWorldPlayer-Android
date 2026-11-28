package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G

/** UI requests targets; the Native engine owns sample timing and committed playback state. */
internal class NativeInteractiveDriver(
    private val spec: G.Spec,
    entry: Int,
    private val next: Map<Int,Int>,
    private val outros: Map<Int,Int>,
    private val fast: Int?,
    private val request: (Int)->String?,
    private val versions: List<InteractivePlaybackPlan.Version> = emptyList(),
    private val outroVariants: Map<Int,List<Int>> = emptyMap(),
    private val regions: Map<Int,Int> = emptyMap(),
    private val cancelRequest: ()->String? = { "Cancellation is unavailable" },
    private val mandatoryAutomaticSources: Set<Int> = emptySet(),
    private val advanceViaOutro: Set<Int> = emptySet(),
    private val onTransitionRequested: ((from: Int, to: Int) -> Unit)? = null,
    private val onTransitionCancelled: ((from: Int, cancelledTarget: Int) -> Unit)? = null,
) {
    companion object {
        /** Verified v1.8.0 minimum lookahead (0.25s @ 48 kHz, 0x0243c900) for Full_ToOutro0N cues. */
        const val MIN_OUTRO_LOOKAHEAD_FRAMES = 12_000L
    }
    data class Observation(val segment: Int,val sourceFrame: Long?,val transitioning: Boolean=false,
                           val paused: Boolean=false,val voices: Int=1,val error: String?=null,val failureCount: Long=0,
                           val destination: Int?=null,val canCancel: Boolean=false,val stopped: Boolean=false,
                           val transportFrame: Long?=null,val fadeEndFrame: Long?=null)
    var current: Int=entry; private set
    var pending: Int?=null; private set
    var diagnostic: String?=null; private set
    private var observation: Observation?=null
    val observationState: Observation? get()=observation
    var isFading: Boolean=false; private set
    fun markFadeRequested() { isFading=true;pending=null;cancellationRequested=false }
    private var cancellationRequested=false
    private var heardVoices=false
    private var previousError: String?=null
    private var previousFailures=0L
    private val segmentsById: Map<Int, G.Segment> = spec.segments.associateBy { it.index }
    private val transitionsByEdge: Map<Long, G.Transition?> = buildMap {
        for (t in spec.transitions) {
            for (src in t.sources) {
                for (dst in t.destinations) {
                    val key = (src.toLong() shl 32) or (dst.toLong() and 0xFFFFFFFFL)
                    put(key, if (containsKey(key)) null else t)
                }
            }
        }
    }
    private val outroTargetSet: Set<Int> = (outros.values + outroVariants.values.flatten()).toSet()
    private val outroRulesBySource: Map<Int, List<G.Transition>> = buildMap<Int, MutableList<G.Transition>> {
        for (t in spec.transitions) {
            val singleDest = t.destinations.singleOrNull()
            if (singleDest != null && singleDest in outroTargetSet) {
                for (src in t.sources) {
                    getOrPut(src) { ArrayList(2) }.add(t)
                }
            }
        }
    }
    private val fastVersionIds: Set<Int> = versions.mapNotNull { it.fast }.toSet()
    private val versionByAnyId: Map<Int, InteractivePlaybackPlan.Version?> = buildMap {
        for (v in versions) {
            v.main?.let { id -> put(id, if (containsKey(id)) null else v) }
            v.fast?.let { id -> put(id, if (containsKey(id)) null else v) }
        }
    }
    private val fastTargetByMainId: Map<Int, Int?> = buildMap {
        for (v in versions) {
            v.main?.let { id -> put(id, if (containsKey(id)) null else v.fast) }
        }
    }
    private val regionBySegment: Map<Int, Int?> = buildMap {
        for ((num, seg) in regions) {
            put(seg, if (containsKey(seg)) null else num)
        }
    }
    private val available: Boolean get()=!isFading && pending==null && observation?.transitioning==false && observation?.paused==false && !finished &&
        !(currentVersion!=null && (observation?.voices ?: 0)>1)
    val canCancelPending: Boolean get()=current !in mandatoryAutomaticSources && !isFading && pending!=null && observation?.canCancel==true && !cancellationRequested
    fun cancelPending(): String? {
        if(!canCancelPending) return "The next section has already started or is not cancellable"
        val cancelled = pending
        val error=cancelRequest()
        diagnostic=error
        if(error==null) {
            cancellationRequested=true
            if (cancelled != null) onTransitionCancelled?.invoke(current, cancelled)
        }
        return error
    }
    val canAdvance: Boolean get()=available && (next[current]!=null || current in advanceViaOutro && outroRules().isNotEmpty())
    val canEnd: Boolean get()=available && current !in advanceViaOutro && hasReachableOutro()
    /** Outro numbers whose authored cue is reachable from the current playback position given segment.loop. */
    val reachableOutros: Set<Int> get() {
        val segment = segmentsById[current] ?: return emptySet()
        val position = observation?.sourceFrame
        val allNumbers = outros.keys + outroVariants.keys
        return buildSet {
            for (number in allNumbers) {
                val targets = listOfNotNull(outros[number]) + outroVariants[number].orEmpty()
                val target = targets.singleOrNull { rule(it) != null } ?: continue
                val transition = rule(target) ?: continue
                if (isCueReachable(segment, transition, position)) add(number)
            }
        }
    }
    val currentRegion: Int? get()=regionBySegment[current]
    val availableRegions: Set<Int> get()=if(!available) emptySet() else regions.filter { (_,target) -> target!=current && rule(target)!=null }.keys
    fun selectRegion(number: Int): String? {
        val target=regions[number] ?: return "No such region"
        return submit(target)
    }
    val currentVersion: Int? get()=versionByAnyId[current]?.number
    val isFast: Boolean get()=current==fast || current in fastVersionIds
    private val fastTarget: Int? get()=fastTargetByMainId[current] ?: fast
    val availableVersions: Set<Int> get()=if(!available) emptySet() else buildSet {
        for (v in versions) {
            val target = if (isFast) v.fast else v.main
            if (target != null && target != current && rule(target) != null) add(v.number)
        }
    }
    val canFast: Boolean get()=available && fastTarget?.let { rule(it)!=null }==true
    val finished: Boolean get()=heardVoices && observation?.let { it.voices==0 && (it.stopped || isFading && it.fadeEndFrame!=null) && !it.paused && !it.transitioning && pending==null }==true

    fun observe(value: Observation) {
        observation=value
        if(value.voices>0) heardVoices=true
        if(value.segment in segmentsById) current=value.segment
        if(!isFading && value.destination!=null && value.destination!=current) pending=value.destination
        val newFailure=value.error!=null && (value.error!=previousError || value.failureCount!=previousFailures)
        if(newFailure) { diagnostic=value.error;cancellationRequested=false }
        if(cancellationRequested && !value.transitioning && value.destination==null) {
            pending=null;cancellationRequested=false
        }
        if((pending==current && !value.transitioning) || newFailure) pending=null
        previousError=value.error;previousFailures=value.failureCount
    }
    private fun edgeKey(source: Int, target: Int): Long = (source.toLong() shl 32) or (target.toLong() and 0xFFFFFFFFL)
    private fun rule(target: Int): G.Transition? = transitionsByEdge[edgeKey(current, target)]
    private fun outroRules(): List<G.Transition> = outroRulesBySource[current].orEmpty()
    private fun isCueReachable(segment: G.Segment, transition: G.Transition, position: Long?): Boolean {
        if (transition.sync != G.SyncPoint.NextCustomCue) return true
        val cueName = transition.sourceCue ?: return true
        val matching = segment.cues.filter { it.name == cueName }
        if (matching.isEmpty()) return false
        if (position == null) return true
        val loop = segment.loop
        return matching.any { cue ->
            cue.frame > position || (loop != null && cue.frame >= loop.start && cue.frame < loop.end)
        }
    }
    private fun hasReachableOutro(): Boolean {
        val segment = segmentsById[current] ?: return false
        val position = observation?.sourceFrame
        return outroRules().any { transition -> isCueReachable(segment, transition, position) }
    }
    /**
     * Estimates exact transport frames remaining from [sourceFrame] until the currently queued
     * [pending] transition fires on its authored sync boundary, or null when nothing is pending.
     */
    fun estimatedFramesUntilPendingSwitch(sourceFrame: Long? = observation?.sourceFrame): Long? {
        val target = pending ?: return null
        val transition = rule(target) ?: return null
        val segment = segmentsById[current] ?: return null
        if (transition.sync == G.SyncPoint.Immediate) return 0L
        val pos = sourceFrame ?: return null
        return when (transition.sync) {
            G.SyncPoint.Immediate -> 0L
            G.SyncPoint.ExitCue -> (segment.exit - pos).coerceAtLeast(0L)
            G.SyncPoint.NextCustomCue, G.SyncPoint.NextCue -> {
                val cueFrames = buildList {
                    if (transition.sync == G.SyncPoint.NextCue && transition.sourceCue == null) {
                        add(segment.exit)
                    }
                    for (cue in segment.cues) {
                        val kindOk = transition.sync != G.SyncPoint.NextCustomCue || cue.kind == G.CueKind.Custom
                        val nameOk = transition.sourceCue == null || cue.name == transition.sourceCue
                        if (kindOk && nameOk) add(cue.frame)
                    }
                }
                if (cueFrames.isEmpty()) return null
                var minDelay = 0L
                transition.timeline?.let { tl ->
                    if (-tl.destinationStart > minDelay) minDelay = -tl.destinationStart
                    if (transition.bridge >= 0 && -tl.bridgeStart > minDelay) minDelay = -tl.bridgeStart
                }
                val loop = segment.loop
                val loopSpan = if (loop != null && loop.end > loop.start) loop.end - loop.start else 0L
                val distances = cueFrames.mapNotNull { frame ->
                    var d = frame - pos
                    if (d <= 0L) {
                        if (loop == null || loopSpan <= 0L || frame < loop.start || frame > loop.end) return@mapNotNull null
                        d += loopSpan
                    }
                    if (d < minDelay) {
                        if (loop == null || loopSpan <= 0L || frame < loop.start || frame > loop.end) return@mapNotNull null
                        val extra = ((minDelay - d) + loopSpan - 1L) / loopSpan
                        d += extra * loopSpan
                    }
                    d.coerceAtLeast(0L)
                }
                if (distances.isEmpty()) return null
                val candidates = if (target in outroTargetSet) {
                    distances.filter { it >= MIN_OUTRO_LOOKAHEAD_FRAMES }.ifEmpty { distances }
                } else {
                    distances
                }
                candidates.minOrNull()
            }
            G.SyncPoint.NextBeat, G.SyncPoint.NextBar, G.SyncPoint.NextGrid -> {
                val bpm = spec.bpm
                val rate = spec.transportSampleRate
                val denominator = spec.denominator
                if (!bpm.isFinite() || bpm <= 0.0 || rate <= 0 || denominator <= 0) return null
                val framesPerBeat = rate.toDouble() * 60.0 / bpm * 4.0 / denominator.toDouble()
                val quantum = when (transition.sync) {
                    G.SyncPoint.NextBeat -> framesPerBeat
                    G.SyncPoint.NextBar -> framesPerBeat * spec.numerator.coerceAtLeast(1)
                    G.SyncPoint.NextGrid -> {
                        val grid = spec.grid
                        if (!grid.isFinite() || grid <= 0.0) return null
                        framesPerBeat / grid
                    }
                    else -> framesPerBeat
                }
                if (!quantum.isFinite() || quantum <= 0.0) return null
                var units = pos.toDouble() / quantum
                val nearest = kotlin.math.round(units)
                if (kotlin.math.abs(units - nearest) <= 0.5000001 / quantum) units = nearest
                val targetFrame = kotlin.math.round((kotlin.math.floor(units) + 1.0) * quantum).toLong()
                (targetFrame - pos).coerceAtLeast(0L)
            }
        }
    }
    private fun submit(target: Int): String? {
        if(!available) return "Waiting for the Native transition"
        if(rule(target)==null) return "No source transition for this section"
        val error=request(target)
        diagnostic=error
        if(error==null) {
            pending=target
            onTransitionRequested?.invoke(current, target)
        }
        return error
    }
    fun advance(): String? {
        if (current in advanceViaOutro) return takeOutro()
        val target=next[current] ?: return "No next section here"
        return submit(target)
    }
    fun takeOutro(): String? {
        if(!available) return "Waiting for the Native transition"
        val position=observation?.sourceFrame ?: return "Waiting for the source playback position"
        val segment=segmentsById.getValue(current)
        val options=outroRules().flatMap { transition ->
            segment.cues.filter { it.name==transition.sourceCue }.mapNotNull { cue ->
                val delay=if(cue.frame>position) cue.frame-position else segment.loop?.let { loop ->
                    if(cue.frame>=loop.start && cue.frame<loop.end) loop.end-position+cue.frame-loop.start else null
                }
                delay?.takeIf { it>0 }?.let { it to transition.destinations.single() }
            }
        }
        val candidates = options.filter { it.first >= MIN_OUTRO_LOOKAHEAD_FRAMES }.ifEmpty { options }
        val target=candidates.minWithOrNull(compareBy<Pair<Long,Int>>({it.first},{it.second}))?.second
            ?: return "No reachable Outro exit from this source position"
        return submit(target)
    }
    fun takeOutro(number: Int): String? {
        val targets=listOfNotNull(outros[number])+outroVariants[number].orEmpty()
        val target=targets.singleOrNull { rule(it)!=null } ?: return "No confirmed Outro from this section"
        val segment = segmentsById[current]
        val transition = rule(target)
        if (segment != null && transition != null && !isCueReachable(segment, transition, observation?.sourceFrame)) {
            return "This Outro exit is no longer reachable from the current position"
        }
        return submit(target)
    }
    fun selectVersion(number: Int): String? {
        val version=versions.singleOrNull { it.number==number } ?: return "No such recording version"
        val target=if(isFast) version.fast else version.main
        return if(target==null) "No matching version for this section" else submit(target)
    }
    fun enterFast(): String? {
        val target=fastTarget ?: return "No Fast arrangement"
        return submit(target)
    }
}
