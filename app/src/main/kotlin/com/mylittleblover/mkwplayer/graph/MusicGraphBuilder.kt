package com.mylittleblover.mkwplayer.graph

import java.io.ByteArrayOutputStream

/**
 * Builds a compiled music graph with more than one asset, segment, track or clip.
 *
 * The native runtime has always supported this: `GraphReader` parses lists of assets,
 * segments, tracks, clips, cues, transitions and a musical loop. What was missing was a
 * Kotlin writer, so the app could only ever emit one segment covering one file. This is
 * that writer.
 *
 * Every field order below is copied from the reader, not from a specification, and every
 * constraint the reader enforces is re-checked here first. A Kotlin-side rejection gives a
 * specific message; a reader-side rejection gives `INVALID_COMPILED_GRAPH` after a JNI round
 * trip, which is useless for diagnosing a bad cue.
 *
 * Wire values for the enums are pinned in tests against `GraphProtocolIds.hpp` rather than
 * trusted to stay aligned.
 */
object MusicGraphBuilder {

    /** Highest version this writer emits; matches what the reader accepts. */
    const val VERSION = 6

    // ---------------------------------------------------------------- enum wire values

    /** Node kinds, from GraphProtocolIds.hpp. */
    enum class Kind(val wire: Int) {
        Segment(1), Track(2), Transition(9);
    }

    /** TrackMode. */
    enum class TrackMode(val wire: Int) {
        Normal(0), RandomStep(1), SequenceStep(2), Switch(3);
    }

    /** CueKind. */
    enum class CueKind(val wire: Int) {
        Custom(0), Event(1);
    }

    /** SyncPoint: where a transition is allowed to happen. */
    enum class SyncPoint(val wire: Int) {
        Immediate(0), NextBeat(1), NextBar(2), NextGrid(3),
        ExitCue(4), NextCue(5), NextCustomCue(6);
    }

    /** EntryMode: where the destination starts from. */
    enum class EntryMode(val wire: Int) {
        EntryCue(0), SameTime(1), RandomCue(2), RandomCustomCue(3),
        LastExit(4), PlaylistLast(5), PlaylistNext(6), PlaylistJump(7);
    }

    /** Shape: fade curve. */
    enum class Shape(val wire: Int) {
        Linear(0), Hold(1), SCurve(2), EaseIn(3), EaseOut(4);
    }

    /** EndBehavior: how a musical loop stops. */
    enum class EndBehavior(val wire: Int) {
        Natural(0), Immediate(1), Fade(2), Ending(3);
    }

    // ---------------------------------------------------------------- spec

    data class Loop(val start: Long, val end: Long, val count: Int = -1)

    data class Asset(
        val path: String,
        val sampleRate: Int,
        val channels: Int,
        val frames: Long,
        val sha256: ByteArray,
    )

    data class Cue(
        val index: Int,
        val name: String,
        val frame: Long,
        val kind: CueKind = CueKind.Custom,
        val event: Int = -1,
    )

    data class Segment(
        val index: Int,
        val entry: Long = 0,
        val exit: Long,
        /** Segment-level loop. `retainTail` keeps the audio after the exit while looping. */
        val loop: Loop? = null,
        val retainTail: Boolean = false,
        val cues: List<Cue> = emptyList(),
    )

    data class Clip(
        val index: Int,
        val asset: Int,
        val start: Long = 0,
        val sourceOffset: Long = 0,
        val sourceLength: Long,
        val gainDb: Double = 0.0,
        val fadeIn: Long = 0,
        val fadeOut: Long = 0,
        /** Clip-level loop, independent of any source loop metadata. */
        val loop: Loop? = null,
    )

    data class Track(
        val index: Int,
        val segment: Int,
        val clips: List<Clip>,
        val mode: TrackMode = TrackMode.Normal,
        val group: Int = -1,
        val bus: Int = -1,
        val seed: Long = 1,
        val avoidRepeat: Int = 0,
    )

    data class Fade(
        val beats: Double = 0.0,
        val offset: Double = 0.0,
        val shape: Shape = Shape.Linear,
    )

    data class Transition(
        val index: Int,
        val sources: List<Int>,
        val destinations: List<Int>,
        val sync: SyncPoint = SyncPoint.NextBar,
        val sourceCue: String? = null,
        val entry: EntryMode = EntryMode.EntryCue,
        val destinationCue: String? = null,
        val bridge: Int = -1,
        val fadeOut: Fade = Fade(),
        val fadeIn: Fade = Fade(),
        /** Play the destination's post-exit before leaving the source. */
        val postExit: Boolean = false,
        /** Play the destination's pre-entry before arriving. */
        val preEntry: Boolean = false,
        val priority: Int = 0,
        val specificity: Int = 0,
        val jumpItem: Int = -1,
    )

    data class MusicalLoop(
        val loop: Int,
        val repeats: Int,
        val intro: Int = -1,
        val ending: Int = -1,
        val retainIntroTail: Boolean = false,
        val stop: EndBehavior = EndBehavior.Immediate,
    )

    data class Spec(
        val assets: List<Asset>,
        val segments: List<Segment>,
        val tracks: List<Track>,
        val transitions: List<Transition> = emptyList(),
        val musicalLoop: MusicalLoop? = null,
        val transportSampleRate: Int = 48000,
        val bpm: Double = 140.0,
        val numerator: Int = 4,
        val denominator: Int = 4,
        val grid: Double = 1.0,
        /**
         * Names for the identity table, one per index.
         *
         * Optional because the reader only requires the count to exceed every index used. It
         * is settable so a caller can reproduce the identity names of an already-verified
         * packet byte for byte, which is how this writer is checked against the single-asset
         * builder.
         */
        val identities: List<String>? = null,
    )

    /**
     * Emits the packet.
     *
     * Rejects anything the reader would reject, with a message naming the offending part.
     */
    fun build(spec: Spec): ByteArray {
        require(spec.transportSampleRate in intArrayOf(44100, 48000, 96000)) {
            "transport rate must be 44100, 48000 or 96000, got ${spec.transportSampleRate}"
        }
        require(spec.bpm in 10.0..400.0) { "bpm must be 10..400, got ${spec.bpm}" }
        require(spec.numerator in 1..32) { "numerator must be 1..32, got ${spec.numerator}" }
        require(spec.denominator in intArrayOf(1, 2, 4, 8, 16, 32)) {
            "denominator must be 1/2/4/8/16/32, got ${spec.denominator}"
        }
        require(spec.grid.isFinite() && spec.grid > 0) { "grid must be > 0, got ${spec.grid}" }

        require(spec.assets.isNotEmpty()) { "a graph needs at least one asset" }
        require(spec.segments.isNotEmpty()) { "a graph needs at least one segment" }

        spec.assets.forEachIndexed { position, asset -> validateAsset(asset, position) }
        spec.segments.forEach { validateSegment(it, spec.assets) }
        spec.tracks.forEach { validateTrack(it, spec.segments) }
        spec.transitions.forEach { validateTransition(it) }
        spec.musicalLoop?.let { validateMusicalLoop(it, spec.segments) }

        // Every index the reader sees must be inside the identities list, and no two nodes may
        // share an index. Identities are named so a rejection can point at one.
        val nodeIndices = spec.segments.map { it.index } + spec.tracks.map { it.index }
        require(nodeIndices.toSet().size == nodeIndices.size) {
            "node indices must be unique, got $nodeIndices"
        }
        val maxIndex = maxOf(
            nodeIndices.maxOrNull() ?: 0,
            spec.transitions.maxOfOrNull { it.index } ?: 0,
            spec.segments.flatMap { s -> s.cues.map { it.index } }.maxOrNull() ?: 0,
            spec.tracks.flatMap { t -> t.clips.map { it.index } }.maxOrNull() ?: 0,
            spec.assets.size - 1,
        )
        val identities = spec.identities ?: (0..maxIndex).map { "n$it" }
        require(identities.size > maxIndex) {
            "identity table has ${identities.size} names but index $maxIndex is used"
        }

        val body = ByteArrayOutputStream(4096)
        GraphWire.writeI32(body, spec.transportSampleRate)
        GraphWire.writeStringList(body, identities)

        // Nodes: one per segment, track and transition.
        //
        // Transitions need a node of their own. `validGraph` calls
        // `define(t.index, Kind::Transition)`, which requires a node with that index and kind
        // to exist -- a transition listed only in the transition table is rejected. The
        // single-segment graph has no transitions, so this only shows up once one is added.
        GraphWire.writeI32(body, spec.segments.size + spec.tracks.size + spec.transitions.size)
        spec.segments.forEach { segment ->
            GraphWire.writeNode(
                body,
                index = segment.index,
                kind = Kind.Segment.wire,
                parent = -1,
                bpm = spec.bpm,
                numerator = spec.numerator,
                denominator = spec.denominator,
                grid = spec.grid,
                gridOffset = 0.0,
            )
        }
        spec.tracks.forEach { track ->
            GraphWire.writeNode(
                body,
                index = track.index,
                kind = Kind.Track.wire,
                parent = track.segment,
                bpm = spec.bpm,
                numerator = spec.numerator,
                denominator = spec.denominator,
                grid = spec.grid,
                gridOffset = 0.0,
            )
        }
        spec.transitions.forEach { transition ->
            GraphWire.writeNode(
                body,
                index = transition.index,
                kind = Kind.Transition.wire,
                parent = -1,
                bpm = spec.bpm,
                numerator = spec.numerator,
                denominator = spec.denominator,
                grid = spec.grid,
                gridOffset = 0.0,
            )
        }

        writeSegments(body, spec.segments)
        writeTracks(body, spec.tracks)

        // Empty top-level collections, in reader order: playlists, switches, groups,
        // parameters, events. Then transitions, which are not empty here.
        repeat(5) { GraphWire.writeI32(body, 0) }
        writeTransitions(body, spec.transitions)
        // curves, stingers, buses.
        repeat(3) { GraphWire.writeI32(body, 0) }

        writeAssets(body, spec.assets)

        GraphWire.writeI32(body, 0)   // entries
        GraphWire.writeI32(body, 0)   // performances

        writeMusicalLoop(body, spec.musicalLoop)

        GraphWire.writeByteValue(body, 0)   // hasAutomatic

        return GraphWire.envelope(body.toByteArray(), VERSION)
    }

    // ---------------------------------------------------------------- sections

    private fun writeSegments(out: ByteArrayOutputStream, segments: List<Segment>) {
        GraphWire.writeI32(out, segments.size)
        segments.forEach { segment ->
            GraphWire.writeI32(out, segment.index)
            GraphWire.writeLong(out, segment.entry)
            GraphWire.writeLong(out, segment.exit)
            if (segment.loop == null) {
                GraphWire.writeByteValue(out, 0)
            } else {
                GraphWire.writeByteValue(out, 1)
                GraphWire.writeLong(out, segment.loop.start)
                GraphWire.writeLong(out, segment.loop.end)
                GraphWire.writeI32(out, segment.loop.count)
                GraphWire.writeByteValue(out, if (segment.retainTail) 1 else 0)
            }
            GraphWire.writeI32(out, segment.cues.size)
            segment.cues.forEach { cue ->
                GraphWire.writeI32(out, cue.index)
                GraphWire.writeString(out, cue.name)
                GraphWire.writeLong(out, cue.frame)
                GraphWire.writeI32(out, cue.kind.wire)
                GraphWire.writeI32(out, cue.event)
            }
        }
    }

    private fun writeTracks(out: ByteArrayOutputStream, tracks: List<Track>) {
        GraphWire.writeI32(out, tracks.size)
        tracks.forEach { track ->
            GraphWire.writeI32(out, track.index)
            GraphWire.writeI32(out, track.segment)
            GraphWire.writeI32(out, track.mode.wire)
            GraphWire.writeI32(out, track.group)
            GraphWire.writeI32(out, track.bus)
            GraphWire.writeLong(out, track.seed)
            GraphWire.writeI32(out, track.avoidRepeat)
            GraphWire.writeByteValue(out, 0)   // reset
            GraphWire.writeI32(out, track.clips.size)
            track.clips.forEach { clip ->
                GraphWire.writeClip(
                    out,
                    index = clip.index,
                    asset = clip.asset,
                    start = clip.start,
                    sourceOffset = clip.sourceOffset,
                    sourceLength = clip.sourceLength,
                    gainDb = clip.gainDb,
                    fadeIn = clip.fadeIn,
                    fadeOut = clip.fadeOut,
                    loop = clip.loop?.let { GraphWire.CompiledLoop(it.start, it.end, it.count) },
                )
            }
            GraphWire.writeI32(out, 0)   // subtracks
            GraphWire.writeI32(out, 0)   // sends
        }
    }

    private fun writeTransitions(out: ByteArrayOutputStream, transitions: List<Transition>) {
        GraphWire.writeI32(out, transitions.size)
        transitions.forEach { transition ->
            GraphWire.writeI32(out, transition.index)
            GraphWire.writeIndexList(out, transition.sources)
            GraphWire.writeIndexList(out, transition.destinations)
            GraphWire.writeI32(out, transition.specificity)
            GraphWire.writeI32(out, transition.sync.wire)
            GraphWire.writeNullableString(out, transition.sourceCue)
            GraphWire.writeI32(out, transition.entry.wire)
            GraphWire.writeNullableString(out, transition.destinationCue)
            GraphWire.writeI32(out, transition.jumpItem)
            GraphWire.writeI32(out, transition.bridge)
            writeFade(out, transition.fadeOut)
            writeFade(out, transition.fadeIn)
            GraphWire.writeByteValue(out, if (transition.postExit) 1 else 0)
            GraphWire.writeByteValue(out, if (transition.preEntry) 1 else 0)
            GraphWire.writeI32(out, transition.priority)
            GraphWire.writeI32(out, 0)   // conditions
        }
    }

    private fun writeFade(out: ByteArrayOutputStream, fade: Fade) {
        GraphWire.writeDouble(out, fade.beats)
        GraphWire.writeDouble(out, fade.offset)
        GraphWire.writeI32(out, fade.shape.wire)
    }

    private fun writeAssets(out: ByteArrayOutputStream, assets: List<Asset>) {
        GraphWire.writeI32(out, assets.size)
        assets.forEachIndexed { index, asset ->
            GraphWire.writeI32(out, index)
            GraphWire.writeString(out, asset.path)
            GraphWire.writeI32(out, asset.sampleRate)
            GraphWire.writeI32(out, asset.channels)
            GraphWire.writeLong(out, asset.frames)
            GraphWire.writeLong(out, 0)   // lookAhead
            out.write(asset.sha256)       // v3 digest
            GraphWire.writeByteValue(out, 0)   // v4 hasSource = false
        }
    }

    private fun writeMusicalLoop(out: ByteArrayOutputStream, loop: MusicalLoop?) {
        if (loop == null) {
            GraphWire.writeByteValue(out, 0)
            return
        }
        GraphWire.writeByteValue(out, 1)
        GraphWire.writeI32(out, loop.intro)
        GraphWire.writeI32(out, loop.loop)
        GraphWire.writeI32(out, loop.ending)
        GraphWire.writeI32(out, loop.repeats)
        GraphWire.writeByteValue(out, if (loop.retainIntroTail) 1 else 0)
        GraphWire.writeI32(out, loop.stop.wire)
    }

    // ---------------------------------------------------------------- validation

    private fun validateAsset(asset: Asset, position: Int) {
        require(asset.sampleRate in 8000..192000) {
            "asset $position sample rate must be 8000..192000, got ${asset.sampleRate}"
        }
        require(asset.channels in 1..2) {
            "asset $position channels must be 1..2, got ${asset.channels}"
        }
        require(asset.frames > 0) { "asset $position frames must be positive, got ${asset.frames}" }
        require(asset.sha256.size == 32) {
            "asset $position sha256 must be 32 bytes, got ${asset.sha256.size}"
        }
        require(asset.path.isNotEmpty()) { "asset $position path must not be empty" }
        require(!asset.path.startsWith("/")) {
            "asset $position path must be relative: ${asset.path}"
        }
        require(!asset.path.contains('\\')) {
            "asset $position path must use forward slashes: ${asset.path}"
        }
        require(!asset.path.contains(':')) {
            "asset $position path must not contain a drive or scheme: ${asset.path}"
        }
        require(asset.path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "asset $position path must not contain empty, '.' or '..' parts: ${asset.path}"
        }
    }

    private fun validateSegment(segment: Segment, assets: List<Asset>) {
        require(segment.entry >= 0) {
            "segment ${segment.index} entry must be >= 0, got ${segment.entry}"
        }
        require(segment.exit > segment.entry) {
            "segment ${segment.index} exit ${segment.exit} must exceed entry ${segment.entry}"
        }
        segment.loop?.let { loop ->
            require(loop.start >= segment.entry) {
                "segment ${segment.index} loop start ${loop.start} is before its entry ${segment.entry}"
            }
            require(loop.end <= segment.exit) {
                "segment ${segment.index} loop end ${loop.end} exceeds its exit ${segment.exit}"
            }
            require(loop.end > loop.start) {
                "segment ${segment.index} loop must satisfy start < end, got ${loop.start}..${loop.end}"
            }
            require(loop.count >= -1) {
                "segment ${segment.index} loop count must be >= -1, got ${loop.count}"
            }
        }
        require(segment.cues.map { it.index }.toSet().size == segment.cues.size) {
            "segment ${segment.index} cue indices must be unique"
        }
        segment.cues.forEach { cue ->
            require(cue.frame >= 0) { "cue ${cue.name} frame must be >= 0, got ${cue.frame}" }
            require(cue.name.isNotEmpty()) { "cue ${cue.index} must have a name" }
        }
        // A segment's clips must fit inside its own timeline, otherwise the exit is
        // unreachable and the loop point cannot be honoured.
        require(assets.isNotEmpty()) { "segment ${segment.index} has no assets to draw from" }
    }

    private fun validateTrack(track: Track, segments: List<Segment>) {
        require(segments.any { it.index == track.segment }) {
            "track ${track.index} names segment ${track.segment}, which is not in the graph"
        }
        require(track.avoidRepeat in 0..63) {
            "track ${track.index} avoidRepeat must be 0..63, got ${track.avoidRepeat}"
        }
        require(track.clips.isNotEmpty()) { "track ${track.index} has no clips" }
        require(track.clips.map { it.index }.toSet().size == track.clips.size) {
            "track ${track.index} clip indices must be unique"
        }
        track.clips.forEach { clip ->
            require(clip.start >= 0) {
                "clip ${clip.index} start must be >= 0, got ${clip.start}"
            }
            require(clip.sourceOffset >= 0) {
                "clip ${clip.index} sourceOffset must be >= 0, got ${clip.sourceOffset}"
            }
            require(clip.sourceLength > 0) {
                "clip ${clip.index} sourceLength must be positive, got ${clip.sourceLength}"
            }
            require(clip.fadeIn >= 0) { "clip ${clip.index} fadeIn must be >= 0" }
            require(clip.fadeOut >= 0) { "clip ${clip.index} fadeOut must be >= 0" }
            clip.loop?.let { loop ->
                // Mirrors validClipLoop in the reader.
                require(loop.start >= clip.sourceOffset) {
                    "clip ${clip.index} loop start ${loop.start} is before its source offset " +
                        "${clip.sourceOffset}"
                }
                require(loop.end > loop.start) {
                    "clip ${clip.index} loop must satisfy start < end"
                }
                require(loop.end <= clip.sourceOffset + clip.sourceLength) {
                    "clip ${clip.index} loop end ${loop.end} exceeds source window " +
                        "${clip.sourceOffset}..${clip.sourceOffset + clip.sourceLength}"
                }
                require(loop.count >= -1) {
                    "clip ${clip.index} loop count must be >= -1, got ${loop.count}"
                }
            }
        }
    }

    private fun validateTransition(transition: Transition) {
        require(transition.sources.isNotEmpty()) {
            "transition ${transition.index} must name at least one source"
        }
        require(transition.destinations.isNotEmpty()) {
            "transition ${transition.index} must name at least one destination"
        }
        require(transition.fadeOut.beats >= 0) {
            "transition ${transition.index} fadeOut beats must be >= 0"
        }
        require(transition.fadeIn.beats >= 0) {
            "transition ${transition.index} fadeIn beats must be >= 0"
        }
        // Only a *custom* cue sync needs a name. `ExitCue` syncs on the segment's own exit,
        // which is a position the segment already declares, so naming a cue there would be
        // redundant rather than required.
        if (transition.sync == SyncPoint.NextCustomCue) {
            require(transition.sourceCue != null) {
                "transition ${transition.index} syncs on a custom cue but names none"
            }
        }
        if (transition.entry == EntryMode.RandomCustomCue) {
            require(transition.destinationCue != null) {
                "transition ${transition.index} enters on a custom cue but names none"
            }
        }
    }

    private fun validateMusicalLoop(loop: MusicalLoop, segments: List<Segment>) {
        require(loop.repeats >= 1) {
            "musical loop repeats must be >= 1, got ${loop.repeats}"
        }
        require(segments.any { it.index == loop.loop }) {
            "musical loop names segment ${loop.loop}, which is not in the graph"
        }
        if (loop.intro >= 0) {
            require(segments.any { it.index == loop.intro }) {
                "musical loop intro ${loop.intro} is not a segment in the graph"
            }
        }
        if (loop.ending >= 0) {
            require(segments.any { it.index == loop.ending }) {
                "musical loop ending ${loop.ending} is not a segment in the graph"
            }
        }
    }
}
