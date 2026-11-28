package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.AudioOutput
import com.mylittleblover.mkwplayer.audio.AudioStatusText
import com.mylittleblover.mkwplayer.audio.PlaybackController
import com.mylittleblover.mkwplayer.audio.PlaybackEngine
import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import com.mylittleblover.mkwplayer.audio.SessionAudioOutput
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler
import com.mylittleblover.mkwplayer.interactive.NativeInteractiveDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one audio session in the process.
 *
 * Before this, the engine lived inside the view model, so leaving the activity
 * destroyed playback. A foreground service cannot own a lifecycle-scoped object, so
 * ownership had to move here: the service provides the notification and the media
 * session, and both it and the UI read this one state.
 *
 * Deliberately a plain class with an injected engine factory rather than a singleton
 * object. The transport rules are the same whether the screen is visible or not, so
 * they should be testable without Android or a device.
 *
 * Threading: every method runs on the control thread (main), except background seek
 * repositioning which suspends the control stream while running native seek off Main
 * under [nativeLifecycleLock] synchronization.
 */
internal class PlaybackSession(
    private val createEngine: () -> SessionAudioEngine?,
    private val openOutput: (Long, Int) -> SessionAudioOutput?,
) {

    /** What the UI and the notification both need to render. */
    data class State(
        val title: String = "",
        val playing: Boolean = false,
        val paused: Boolean = false,
        val positionFrames: Long = 0,
        val totalFrames: Long = 0,
        val sampleRate: Int = 0,
        val detail: String? = null,
        /**
         * True once a track is loaded and Play was accepted.
         *
         * Separate from [playing] on purpose. The engine processes Play through a command
         * queue, so a snapshot taken in the same instant can still read Stopped. Gating the
         * foreground service on [playing] therefore made it refuse to start immediately
         * after a successful load. `loaded` says "the transport was commanded", which is
         * what the service needs; [playing] keeps meaning "the engine says it is playing".
         */
        val loaded: Boolean = false,
        val linear: Boolean = false,
        val naturalEnd: Boolean = false,
        val artworkName: String? = null,
        val courseCode: String? = null,
        val packDirPath: String? = null,
        val subtitle: String? = null,
    ) {
        /** Something is loaded, so the transport is worth keeping alive and showing. */
        val active: Boolean get() = playing || paused || loaded
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun updateMetadata(
        artworkName: String?,
        courseCode: String?,
        packDirPath: String?,
        subtitle: String? = null,
    ) {
        _state.value = _state.value.copy(
            artworkName = artworkName,
            courseCode = courseCode,
            packDirPath = packDirPath,
            subtitle = subtitle,
        )
    }

    private var engine: SessionAudioEngine? = null
    private var controller: PlaybackController? = null
    @Volatile
    private var output: SessionAudioOutput? = null
    private var automaticTransitions: Map<Int,Int> = emptyMap()
    private var automaticSource: Int? = null
    private var automaticPending: Triple<Int,Int,Long>? = null
    private var linearSession: Boolean = false
    private var linearActiveSegment: Int = -1
    private var linearDisplayOffsetFrames: Long = 0L
    private var linearDisplayTotalFrames: Long = 0L

    private val nativeLifecycleLock = Any()
    @Volatile
    var isSeeking: Boolean = false
        private set
    @Volatile
    var seekSequence: Long = 0L
        private set
    @Volatile
    private var engineEpoch: Long = 0L
    @Volatile
    private var seekRequestGeneration: Long = 0L
    @Volatile
    private var pendingSeekTarget: Long = 0L

    fun updatePendingSeek(frame: Long) {
        if (uninterruptible || controller == null) return
        pendingSeekTarget = frame
        seekRequestGeneration++
    }

    /** Optional coordinator hooks shared with background service / ViewModel. */
    var onLinearCompleted: (() -> Boolean)? = null
    var onLinearSegmentAdvanced: ((Int, Long?) -> Unit)? = null
    var onLinearSeek: ((Long) -> Unit)? = null
    var onSkipNext: (() -> Boolean)? = null

    /**
     * When true, transport interruption controls (pause, stop, seek, fadeStop) are ignored until
     * the active track reaches its natural end or [release] clears the session.
     */
    @Volatile
    var uninterruptible: Boolean = false

    fun updateLinearWindow(
        title: String,
        totalFrames: Long,
        transportOriginFrames: Long,
        artworkName: String? = _state.value.artworkName,
        courseCode: String? = _state.value.courseCode,
        subtitle: String? = _state.value.subtitle,
    ) {
        if (!linearSession) return
        val safeOrigin = transportOriginFrames.coerceAtLeast(0L)
        val safeTotal = totalFrames.coerceAtLeast(0L)
        linearDisplayOffsetFrames = safeOrigin
        linearDisplayTotalFrames = safeTotal
        controller?.setSeekLimit(safeOrigin + safeTotal)
        val rawPos = controller?.poll()?.let {
            when (it) {
                is PlaybackController.State.Playing -> it.positionFrames
                is PlaybackController.State.Paused -> it.positionFrames
                else -> _state.value.positionFrames + safeOrigin
            }
        } ?: _state.value.positionFrames
        _state.value = _state.value.copy(
            title = title,
            totalFrames = safeTotal,
            positionFrames = (rawPos - safeOrigin).coerceAtLeast(0L),
            artworkName = artworkName,
            courseCode = courseCode,
            subtitle = subtitle,
        )
    }
    var onSkipPrevious: (() -> Boolean)? = null
    var canSkipQueue: (() -> Boolean)? = null

    /** True while a device stream is open. */
    val hasOutput: Boolean get() = output != null

    /**
     * The master trim every engine created from here on gets.
     *
     * Held on the session rather than passed to each open call so a setting chosen while a track is playing
     * takes effect immediately and a track opened afterwards starts with it already applied.
     */
    private var outputTrim: com.mylittleblover.mkwplayer.audio.OutputTrim =
        com.mylittleblover.mkwplayer.audio.OutputTrim.default

    private var playbackRateConfig: com.mylittleblover.mkwplayer.audio.PlaybackRateConfig =
        com.mylittleblover.mkwplayer.audio.PlaybackRateConfig()

    val currentPlaybackSpeed: Float
        get() = playbackRateConfig.speed

    private var masterDspConfig: com.mylittleblover.mkwplayer.audio.MasterDspStateConfig =
        com.mylittleblover.mkwplayer.audio.MasterDspStateConfig()

    @Volatile
    private var liveVisualizerEnabled: Boolean = false

    private fun applySessionEffectsToEngine(target: SessionAudioEngine) {
        target.setOutputGain(outputTrim.gainDb, outputTrim.ceilingDb)
        target.setPlaybackRate(playbackRateConfig.speed.toDouble(), playbackRateConfig.pitchSemitones.toDouble())
        target.setMasterDsp(
            eqEnabled = masterDspConfig.eqEnabled,
            bands5 = masterDspConfig.bandArrayDoubles(),
            bassBoostDb = masterDspConfig.bassBoostDb,
            loudnessDb = masterDspConfig.loudnessDb.toDouble(),
        )
        target.setVisualizerEnabled(liveVisualizerEnabled)
    }

    /**
     * Applies [trim] to the current engine, and remembers it for engines created later.
     *
     * Returns the status the engine reported, or null when there is no engine yet -- in which case the value
     * is still stored and will be applied when one is created. That distinction matters: "no engine" is not a
     * failure, and reporting one would make the settings screen show an error for a perfectly normal state.
     */
    fun setOutputTrim(trim: com.mylittleblover.mkwplayer.audio.OutputTrim): Int? {
        outputTrim = trim
        val current = engine ?: return null
        return current.setOutputGain(trim.gainDb, trim.ceilingDb)
    }

    fun setPlaybackRate(config: com.mylittleblover.mkwplayer.audio.PlaybackRateConfig): Int? {
        val clean = config.normalized()
        playbackRateConfig = clean
        val current = engine ?: return null
        return current.setPlaybackRate(clean.speed.toDouble(), clean.pitchSemitones.toDouble())
    }

    fun setMasterDsp(config: com.mylittleblover.mkwplayer.audio.MasterDspStateConfig): Int? {
        masterDspConfig = config
        val current = engine ?: return null
        return current.setMasterDsp(
            eqEnabled = config.eqEnabled,
            bands5 = config.bandArrayDoubles(),
            bassBoostDb = config.bassBoostDb,
            loudnessDb = config.loudnessDb.toDouble(),
        )
    }

    fun setVisualizerEnabled(enabled: Boolean) {
        liveVisualizerEnabled = enabled
        engine?.setVisualizerEnabled(enabled)
    }

    fun visualizerSpectrum(outBands: FloatArray): Boolean {
        if (!liveVisualizerEnabled) return false
        return engine?.visualizerSpectrum(outBands) == true
    }

    /** Set dynamic gain (dB) on a track layer in the currently loaded flow engine, optionally fading over [fadeMs]. */
    fun setLayerGain(trackIndex: Int, gainDb: Double, fadeMs: Int = 0): Int? =
        engine?.setLayerGain(trackIndex, gainDb, fadeMs)

    /**
     * Callback timing packet from the open stream, or null when there is none.
     *
     * Exposed here so the screen can show real timing without owning the output. A null
     * is normal: the service may own the stream while the screen is away, and a system
     * with no audio device never opens one at all.
     */
    fun timingSnapshot(): LongArray? = output?.timingSnapshot()

    private var lastObservedSourceSlot: Int = -1

    fun interactiveObservation(): NativeInteractiveDriver.Observation? =
        interactiveObservation(includeSourceFrame = true)

    private fun interactiveObservation(includeSourceFrame: Boolean): NativeInteractiveDriver.Observation? {
        val current=engine ?: return null
        val raw=current.snapshot() ?: return null
        if(raw.size<65 || raw[0]!=8L) return null
        val segment=raw[11].toInt()
        val activeVoices=raw[3].toInt()
        var sourceFrame: Long?=null
        if(includeSourceFrame && segment>=0 && activeVoices>0) {
            val cachedSlot = lastObservedSourceSlot
            if (cachedSlot in 0 until 128) {
                val cached = current.sourceSnapshot(cachedSlot)
                if (cached != null && cached.size == 207 && cached[0] in 3L..11L && cached[1] == 1L && cached[4] == segment.toLong()) {
                    sourceFrame = cached[10]
                }
            }
            if (sourceFrame == null) {
                for(slot in 0 until 128) {
                    if (slot == cachedSlot) continue
                    val source=current.sourceSnapshot(slot) ?: continue
                    if(source.size==207 && source[0] in 3L..11L && source[1]==1L && source[4]==segment.toLong()) {
                        lastObservedSourceSlot = slot
                        sourceFrame=source[10]
                        break
                    }
                }
            }
        }
        val destination=if(raw[24]!=0L) raw[27].toInt() else automaticPending?.takeIf {
            it.first==segment && it.third==raw[6]
        }?.second
        return NativeInteractiveDriver.Observation(segment,sourceFrame,raw[24]!=0L,raw[2]==2L,activeVoices,
            if(raw[7]!=0L) "Native playback: ${AudioStatusText.engine(raw[7].toInt())}" else null,raw[6],destination,canCancel=raw[24]!=0L && raw[37]==0L,stopped=raw[2]==0L,transportFrame=raw[1],fadeEndFrame=raw.getOrNull(65)?.takeIf { it>=0 })
    }

    class Prepared internal constructor(
        internal val engine: SessionAudioEngine,
        internal val controller: PlaybackController,
        internal val started: PlaybackController.State,
        internal val title: String,
        internal val frames: Long,
        internal val linear: Boolean,
        internal val autoAdvance: Map<Int, Int> = emptyMap(),
        internal val initialAutoSource: Int? = null,
        internal val initialSeeked: Boolean = false,
        internal val startSegment: Int = -1,
    ) {
        fun discard() {
            controller.release()
        }
    }

    sealed interface PrepareResult {
        data class Ok(val prepared: Prepared) : PrepareResult
        data class Failed(val reason: String) : PrepareResult
    }

    /**
     * Builds and prepares a fresh native engine off the main thread for a multi-segment or linear graph.
     * Caller must either pass [Prepared] to [commitPrepared] on the main thread or call [Prepared.discard].
     */
    fun prepareFlow(
        title: String,
        packet: ByteArray,
        assets: List<Pair<String, ByteArray>>,
        frames: Long,
        startSegment: Int,
        streamed: Boolean = false,
        autoAdvance: Map<Int, Int> = emptyMap(),
        preparedAssets: Set<String> = emptySet(),
        prequeueInitial: Boolean = false,
        initialLayerGains: Map<Int, Double> = emptyMap(),
        linear: Boolean = false,
        initialSeekFrames: Long = 0L,
        seekLimitFrames: Long = 0L,
    ): PrepareResult = prepareFlowInternal(
        title = title,
        packet = packet,
        frames = frames,
        startSegment = startSegment,
        autoAdvance = autoAdvance,
        prequeueInitial = prequeueInitial,
        initialLayerGains = initialLayerGains,
        linear = linear,
        initialSeekFrames = initialSeekFrames,
        seekLimitFrames = seekLimitFrames,
    ) bindAssets@{ staged ->
        assets.forEachIndexed { index, (name, bytes) ->
            val status = if (streamed && name !in preparedAssets) {
                staged.bindStreamedBwav(index, bytes, name)
            } else {
                staged.bindBwav(index, bytes, name)
            }
            if (status != AudioEngine.STATUS_OK) {
                return@bindAssets "Could not bind '$name': ${AudioStatusText.engineHint(status) ?: AudioStatusText.engine(status)}"
            }
        }
        null
    }

    /**
     * Builds and prepares a fresh native engine off the main thread for a file-backed graph
     * without reading full BWAV payloads into Kotlin ByteArrays.
     */
    fun prepareFlowFromFiles(
        title: String,
        packet: ByteArray,
        assets: List<CourseMedleyCompiler.AssetSource>,
        frames: Long,
        startSegment: Int,
        streamed: Boolean = false,
        autoAdvance: Map<Int, Int> = emptyMap(),
        preparedAssets: Set<String> = emptySet(),
        prequeueInitial: Boolean = false,
        initialLayerGains: Map<Int, Double> = emptyMap(),
        linear: Boolean = false,
        initialSeekFrames: Long = 0L,
        seekLimitFrames: Long = 0L,
    ): PrepareResult = prepareFlowInternal(
        title = title,
        packet = packet,
        frames = frames,
        startSegment = startSegment,
        autoAdvance = autoAdvance,
        prequeueInitial = prequeueInitial,
        initialLayerGains = initialLayerGains,
        linear = linear,
        initialSeekFrames = initialSeekFrames,
        seekLimitFrames = seekLimitFrames,
    ) bindAssets@{ staged ->
        assets.forEachIndexed { index, asset ->
            val isStreamed = streamed && asset.path !in preparedAssets
            val status = when (asset) {
                is CourseMedleyCompiler.AssetSource.DiskFile ->
                    staged.bindBwavFile(
                        assetIndex = index,
                        file = asset.file,
                        assetId = asset.path,
                        streamed = isStreamed,
                        expectedSha256 = asset.sha256,
                    )
                is CourseMedleyCompiler.AssetSource.Bytes ->
                    if (isStreamed) {
                        staged.bindStreamedBwav(index, asset.bytes, asset.path)
                    } else {
                        staged.bindBwav(index, asset.bytes, asset.path)
                    }
            }
            if (status != AudioEngine.STATUS_OK) {
                return@bindAssets "Could not bind '${asset.path}': ${AudioStatusText.engineHint(status) ?: AudioStatusText.engine(status)}"
            }
        }
        null
    }

    private fun prepareFlowInternal(
        title: String,
        packet: ByteArray,
        frames: Long,
        startSegment: Int,
        autoAdvance: Map<Int, Int>,
        prequeueInitial: Boolean,
        initialLayerGains: Map<Int, Double>,
        linear: Boolean,
        initialSeekFrames: Long,
        seekLimitFrames: Long = 0L,
        bindAssets: (SessionAudioEngine) -> String?,
    ): PrepareResult {
        val staged = createEngine() ?: return PrepareResult.Failed("Out of memory creating the audio engine.")
        val stagedController = PlaybackController(staged as PlaybackEngine)
        fun abort(reason: String): PrepareResult {
            stagedController.release()
            return PrepareResult.Failed(reason)
        }

        val loaded = stagedController.loadGraphOnly(packet, title)
        if (loaded is PlaybackController.State.Failed) return abort(loaded.reason)

        bindAssets(staged)?.let { return abort(it) }

        val started = stagedController.playSegment(startSegment)
        if (started is PlaybackController.State.Failed) return abort(started.reason)

        var autoSource: Int? = null
        if ((!linear || autoAdvance.isNotEmpty()) && initialSeekFrames <= 0L) {
            (if (prequeueInitial) autoAdvance[startSegment] else null)?.let { target ->
                val queued = stagedController.playSegment(target)
                if (queued is PlaybackController.State.Failed) return abort(queued.reason)
                autoSource = startSegment
            }
        }

        initialLayerGains.forEach { (trackIndex, db) ->
            val status = staged.setLayerGain(trackIndex, db)
            if (status != AudioEngine.STATUS_OK) {
                return abort("Could not set layer gain for track $trackIndex: ${AudioStatusText.engine(status)}")
            }
        }

        applySessionEffectsToEngine(staged)
        stagedController.setDuration(frames)
        if (seekLimitFrames > frames) {
            stagedController.setSeekLimit(seekLimitFrames)
        }
        val initialState = if (initialSeekFrames > 0L) {
            val clamped = initialSeekFrames.coerceIn(0L, (frames - 1L).coerceAtLeast(0L))
            val sought = stagedController.seek(clamped)
            if (sought is PlaybackController.State.Failed) return abort(sought.reason)
            sought
        } else {
            started
        }
        return PrepareResult.Ok(
            Prepared(
                engine = staged,
                controller = stagedController,
                started = initialState,
                title = title,
                frames = frames,
                linear = linear,
                autoAdvance = autoAdvance.toMap(),
                initialAutoSource = autoSource,
                initialSeeked = initialSeekFrames > 0L,
                startSegment = startSegment,
            ),
        )
    }

    /**
     * Builds and prepares a fresh native engine off the main thread for a standalone linear track.
     */
    fun prepareLinear(
        title: String,
        audio: ByteArray,
        assetId: String,
        frames: Long,
        loop: Boolean = false,
        streamed: Boolean = true,
        loopStart: Long? = null,
        loopEnd: Long? = null,
    ): PrepareResult {
        val staged = createEngine() ?: return PrepareResult.Failed("Out of memory creating the audio engine.")
        val stagedController = PlaybackController(staged as PlaybackEngine)
        fun abort(reason: String): PrepareResult {
            stagedController.release()
            return PrepareResult.Failed(reason)
        }

        val result = stagedController.loadLinear(title, audio, assetId, loop = loop, streamed = streamed)
        if (result is PlaybackController.State.Failed) return abort(result.reason)

        applySessionEffectsToEngine(staged)
        if (loop && loopStart != null && loopEnd != null && loopEnd > loopStart) {
            val repeated = stagedController.setLinearRepeat(true, loopStart, loopEnd)
            if (repeated is PlaybackController.State.Failed) return abort(repeated.reason)
        }
        stagedController.setDuration(frames)
        return PrepareResult.Ok(
            Prepared(
                engine = staged,
                controller = stagedController,
                started = stagedController.poll(),
                title = title,
                frames = frames,
                linear = true,
            ),
        )
    }

    /**
     * Builds and prepares a fresh native engine off the main thread for a standalone linear file
     * via duplicated file descriptor without reading the entire BWAV into a Kotlin ByteArray.
     */
    fun prepareLinearFile(
        title: String,
        file: java.io.File,
        assetId: String,
        frames: Long,
        loop: Boolean = false,
        streamed: Boolean = true,
        loopStart: Long? = null,
        loopEnd: Long? = null,
        initialSeekFrames: Long = 0L,
        fadeEndFrames: Long = -1L,
        fadeOutFrames: Long = 0L,
        displayTotalFrames: Long = frames,
    ): PrepareResult {
        val staged = createEngine() ?: return PrepareResult.Failed("Out of memory creating the audio engine.")
        val stagedController = PlaybackController(staged as PlaybackEngine)
        fun abort(reason: String): PrepareResult {
            stagedController.release()
            return PrepareResult.Failed(reason)
        }

        val result = stagedController.loadLinearFromFile(title, file, assetId, loop = loop, streamed = streamed)
        if (result is PlaybackController.State.Failed) return abort(result.reason)

        applySessionEffectsToEngine(staged)
        if (loop && loopStart != null && loopEnd != null && loopEnd > loopStart) {
            val repeated = stagedController.setLinearRepeat(true, loopStart, loopEnd)
            if (repeated is PlaybackController.State.Failed) return abort(repeated.reason)
        }
        val effectiveTotal = if (displayTotalFrames > 0L) displayTotalFrames else frames
        stagedController.setDuration(effectiveTotal)
        if (fadeEndFrames > 0L) {
            val faded = stagedController.setLinearFadeWindow(fadeEndFrames, fadeOutFrames, effectiveTotal)
            if (faded is PlaybackController.State.Failed) return abort(faded.reason)
        }
        val initialState = if (initialSeekFrames > 0L) {
            val clamped = initialSeekFrames.coerceIn(0L, (effectiveTotal - 1L).coerceAtLeast(0L))
            val sought = stagedController.seek(clamped)
            if (sought is PlaybackController.State.Failed) return abort(sought.reason)
            sought
        } else {
            stagedController.poll()
        }
        return PrepareResult.Ok(
            Prepared(
                engine = staged,
                controller = stagedController,
                started = initialState,
                title = title,
                frames = effectiveTotal,
                linear = true,
                initialSeeked = initialSeekFrames > 0L,
            ),
        )
    }

    /** Swaps a background-prepared engine into the active session and rebinds or opens output on Main. */
    fun commitPrepared(prepared: Prepared, flushOutput: Boolean = false): State {
        applySessionEffectsToEngine(prepared.engine)
        var oldEngine: SessionAudioEngine? = null
        var oldController: PlaybackController? = null
        synchronized(nativeLifecycleLock) {
            val existingOut = output
            val rebound = existingOut != null &&
                existingOut.rebindEngine(prepared.engine.nativeHandle(), prepared.engine.sampleRate, flush = flushOutput)
            if (!rebound) {
                closeOutput()
            }
            engineEpoch++
            isSeeking = false
            if (prepared.initialSeeked) {
                seekSequence++
            }
            oldEngine = engine
            oldController = controller
            engine = prepared.engine
            controller = prepared.controller
            linearSession = prepared.linear
            automaticTransitions = prepared.autoAdvance
            automaticSource = prepared.initialAutoSource
            automaticPending = null
            linearDisplayOffsetFrames = 0L
            linearDisplayTotalFrames = 0L
            lastObservedSourceSlot = -1
            linearActiveSegment = if (prepared.linear && prepared.autoAdvance.isNotEmpty()) {
                interactiveObservation(includeSourceFrame = false)?.segment?.takeIf { it >= 0 } ?: prepared.startSegment
            } else {
                -1
            }
            if (!rebound) {
                output = openOutput(prepared.engine.nativeHandle(), prepared.engine.sampleRate)
            }
        }
        oldController?.let { staleController ->
            if (oldEngine is AudioEngine) {
                CoroutineScope(Dispatchers.IO).launch {
                    staleController.release()
                }
            } else {
                staleController.release()
            }
        }
        val published = publish(prepared.started, prepared.title, prepared.frames, prepared.engine.sampleRate)
        _state.value = published.copy(loaded = true, linear = prepared.linear, naturalEnd = false)
        return _state.value
    }

    /**
     * Loads a multi-segment flow and starts its first section.
     *
     * The packet already describes every section, so this differs from [load] mainly in
     * binding: a flow has one asset per part and each must reach its own slot. Binding by the
     * compiler's own asset order means the position cannot silently disagree with the graph.
     *
     * @param startSegment segment to play first; the flow's entry point, chosen by the caller
     */
    fun loadFlow(
        title: String,
        packet: ByteArray,
        assets: List<Pair<String, ByteArray>>,
        frames: Long,
        startSegment: Int,
        streamed: Boolean = false,
        autoAdvance: Map<Int,Int> = emptyMap(),
        preparedAssets: Set<String> = emptySet(),
        prequeueInitial: Boolean = false,
        initialLayerGains: Map<Int, Double> = emptyMap(),
    ): State {
        var current = engine
        if (current == null) {
            current = createEngine() ?: return fail("Out of memory creating the audio engine.")
            engine = current
            controller = PlaybackController(current as PlaybackEngine)
        }
        val active = controller ?: return fail("The audio engine is not initialised.")

        suspendOrCloseOutput()
        linearSession = false
        linearDisplayOffsetFrames = 0L
        linearDisplayTotalFrames = 0L
        val loaded = active.loadGraphOnly(packet, title)
        if (loaded is PlaybackController.State.Failed) return fail(loaded.reason)

        // One bind per part. A flow that bound only its first asset would play the prelude and
        // then fail silently at the first transition.
        assets.forEachIndexed { index, (name, bytes) ->
            val status = if(streamed && name !in preparedAssets) current.bindStreamedBwav(index,bytes,name) else current.bindBwav(index, bytes, name)
            if (status != AudioEngine.STATUS_OK) {
                return fail("Could not bind '$name': ${AudioStatusText.engineHint(status) ?: AudioStatusText.engine(status)}")
            }
        }

        val started = active.playSegment(startSegment)
        if (started is PlaybackController.State.Failed) return fail(started.reason)
        automaticTransitions=autoAdvance.toMap()
        automaticSource = null
        automaticPending = null
        // Queue an initial automatic edge before opening output. This lets even a zero-frame
        // Bridge exit begin its overlapping destination on the first rendered sample.
        (if (prequeueInitial) automaticTransitions[startSegment] else null)?.let { target ->
            val queued = active.playSegment(target)
            if (queued is PlaybackController.State.Failed) return fail(queued.reason)
            automaticSource = startSegment
        }

        initialLayerGains.forEach { (trackIndex, db) ->
            val status = current.setLayerGain(trackIndex, db)
            if (status != AudioEngine.STATUS_OK) {
                return fail("Could not set layer gain for track $trackIndex: ${AudioStatusText.engine(status)}")
            }
        }

        // Apply master trim after loading the new runtime (loadGraphOnly replaces the native runtime)
        // and before opening output so the first rendered sample already has the chosen gain.
        applySessionEffectsToEngine(current)
        active.setDuration(frames)
        resumeOrOpenOutput(current)

        val published = publish(started, title, frames, current.sampleRate)
        _state.value = published.copy(loaded = true)
        return _state.value
    }

    /**
     * Plays a different section of the currently loaded flow.
     *
     * Goes through the engine's own request path, so a segment that has a transition rule is
     * reached by that transition rather than by a cut. That is the whole point of a flow.
     */
    fun playSegment(segment: Int): State {
        val active = controller ?: return _state.value
        return publishState(active.playSegment(segment))
    }

    /**
     * Prepares and starts one track.
     *
     * Reuses an existing engine when possible: tearing down and recreating the native
     * runtime for every track would drop the output lease and cause an audible gap on
     * each selection.
     */
    fun load(title: String, packet: ByteArray, audio: ByteArray, assetId: String, frames: Long): State {
        var current = engine
        if (current == null) {
            current = createEngine()
            if (current == null) {
                return fail("Out of memory creating the audio engine.")
            }
            engine = current
            controller = PlaybackController(current as PlaybackEngine)
        }

        val active = controller ?: return fail("The audio engine is not initialised.")
        suspendOrCloseOutput()
        linearSession = false
        linearDisplayOffsetFrames = 0L
        linearDisplayTotalFrames = 0L
        val result = active.load(title, packet, audio, assetId)
        if (result is PlaybackController.State.Failed) {
            return fail(result.reason)
        }
        applySessionEffectsToEngine(current)
        active.setDuration(frames)

        // The output must be leased after the graph is loaded and the transport is
        // playing, because binding is refused while a consumer holds the engine.
        resumeOrOpenOutput(current)

        val published = publish(active.poll(), title, frames, current.sampleRate)
        // Play was accepted, so the transport counts as loaded even if the queued command
        // has not been processed by the engine yet.
        _state.value = published.copy(loaded = true)
        return _state.value
    }

    /**
     * Loads and starts a standalone linear track (e.g. a Jukebox recording) using native
     * streamed decoding instead of compiling a graph or decoding full PCM into memory.
     */
    fun loadLinear(
        title: String,
        audio: ByteArray,
        assetId: String,
        frames: Long,
        loop: Boolean = false,
        streamed: Boolean = true,
        loopStart: Long? = null,
        loopEnd: Long? = null,
    ): State {
        var current = engine
        if (current == null) {
            current = createEngine()
            if (current == null) {
                return fail("Out of memory creating the audio engine.")
            }
            engine = current
            controller = PlaybackController(current as PlaybackEngine)
        }

        val active = controller ?: return fail("The audio engine is not initialised.")
        suspendOrCloseOutput()
        linearSession = true
        linearDisplayOffsetFrames = 0L
        linearDisplayTotalFrames = 0L
        val result = active.loadLinear(title, audio, assetId, loop = loop, streamed = streamed)
        if (result is PlaybackController.State.Failed) {
            return fail(result.reason)
        }
        applySessionEffectsToEngine(current)
        if (loop && loopStart != null && loopEnd != null && loopEnd > loopStart) {
            val repeated = active.setLinearRepeat(true, loopStart, loopEnd)
            if (repeated is PlaybackController.State.Failed) {
                return fail(repeated.reason)
            }
        }
        active.setDuration(frames)

        resumeOrOpenOutput(current)

        val published = publish(active.poll(), title, frames, current.sampleRate)
        _state.value = published.copy(loaded = true, linear = true, naturalEnd = false)
        return _state.value
    }

    /**
     * Loads and starts a multi-track linear graph (e.g., overlaid Opening + Telop or chained tracks).
     */
    fun loadLinearGraph(
        title: String,
        packet: ByteArray,
        assets: List<Pair<String, ByteArray>>,
        frames: Long,
        streamed: Boolean = false,
    ): State {
        var current = engine
        if (current == null) {
            current = createEngine()
            if (current == null) {
                return fail("Out of memory creating the audio engine.")
            }
            engine = current
            controller = PlaybackController(current as PlaybackEngine)
        }

        val active = controller ?: return fail("The audio engine is not initialised.")
        suspendOrCloseOutput()
        linearSession = true
        linearDisplayOffsetFrames = 0L
        linearDisplayTotalFrames = 0L
        val loaded = active.loadGraphOnly(packet, title)
        if (loaded is PlaybackController.State.Failed) return fail(loaded.reason)

        assets.forEachIndexed { index, (name, bytes) ->
            val status = if (streamed) current.bindStreamedBwav(index, bytes, name) else current.bindBwav(index, bytes, name)
            if (status != AudioEngine.STATUS_OK) {
                return fail("Could not bind '$name': ${AudioStatusText.engineHint(status) ?: AudioStatusText.engine(status)}")
            }
        }

        val started = active.playSegment(100)
        if (started is PlaybackController.State.Failed) return fail(started.reason)
        applySessionEffectsToEngine(current)
        active.setDuration(frames)

        resumeOrOpenOutput(current)

        val published = publish(started, title, frames, current.sampleRate)
        _state.value = published.copy(loaded = true, linear = true, naturalEnd = false)
        return _state.value
    }

    /** Updates linear single-track loop state cleanly across the native output lease boundary. */
    fun setLinearRepeat(enabled: Boolean, begin: Long = -1L, end: Long = -1L): State {
        val active = controller ?: return _state.value
        if (!linearSession) return _state.value
        val hadOutput = output != null
        if (hadOutput) {
            suspendOrCloseOutput(preserveAutoTransitions = true)
        }
        val updated = active.setLinearRepeat(enabled, begin, end)
        if (hadOutput && updated !is PlaybackController.State.Failed) {
            engine?.let(::resumeOrOpenOutput)
        }
        return publishState(updated)
    }

    /**
     * Updates the finite playback end + fade-out window on a running linear looping track live
     * (via the lock-free native command queue, without interrupting audio output).
     */
    fun setLinearFadeWindow(endFrame: Long, fadeOutFrames: Long, displayTotalFrames: Long): State {
        val active = controller ?: return _state.value
        if (!linearSession) return _state.value
        val updated = active.setLinearFadeWindow(endFrame, fadeOutFrames, displayTotalFrames)
        val published = publishState(updated)
        if (displayTotalFrames > 0L && published.totalFrames != displayTotalFrames) {
            _state.value = published.copy(totalFrames = displayTotalFrames)
        }
        return _state.value
    }

    /** Returns the current native source frame for slot 0 on a linear runtime, if active. */
    fun currentLinearSourceFrame(): Long? {
        val current = engine ?: return null
        val source = current.sourceSnapshot(0) ?: return null
        return if (source.size >= 11 && source[1] == 1L) source[10] else null
    }

    /** Returns the raw monotonic transport frame reported by the engine. */
    fun currentTransportFrame(): Long {
        val raw = engine?.snapshot() ?: return _state.value.positionFrames
        return if (raw.size >= 2 && raw[0] == com.mylittleblover.mkwplayer.audio.EngineSnapshot.WIRE_VERSION) {
            raw[1]
        } else {
            _state.value.positionFrames
        }
    }

    /** Queue cancellation, preserving the current source and transport. Native owns the start boundary. */
    fun cancelPendingTransition(): String? {
        val current=engine ?: return "No audio session"
        val status=current.cancelPendingTransition()
        if(status!=AudioEngine.STATUS_OK) return "Could not cancel: ${AudioStatusText.engine(status)}"
        automaticPending=null
        return null
    }

    fun pause(): State {
        if (uninterruptible) return _state.value
        val active = controller ?: return _state.value
        return publishState(active.pause())
    }

    fun resume(): State {
        val active = controller ?: return _state.value
        return publishState(active.resume())
    }

    /**
     * Rebuilds the active output stream when an external headphone/Bluetooth output device connects
     * so MIUI/Android audio routing cleanly establishes a stereo stream even when AAudio omits
     * ErrorDisconnected.
     */
    fun onAudioRouteChanged(): Boolean {
        if (isSeeking) return false
        val st = _state.value
        if (!st.active || !st.playing) return false
        return synchronized(nativeLifecycleLock) {
            val currentOutput = output ?: return@synchronized false
            val currentEngine = engine ?: return@synchronized false
            val rate = currentEngine.sampleRate
            when (currentOutput.rebuildStream(rate)) {
                true -> true
                null -> false
                false -> {
                    closeOutput(preserveAutoTransitions = true)
                    output = openOutput(currentEngine.nativeHandle(), rate)
                    output != null
                }
            }
        }
    }

    fun fadeStop(beats: Double): String? {
        if (uninterruptible) return "Ceremony playback cannot be interrupted"
        val current = engine ?: return "No audio source is open"
        val status = current.fadeStop(beats)
        return if (status == 0) null else AudioStatusText.engine(status)
    }

    fun stop(): State {
        if (uninterruptible) return _state.value
        val active = controller ?: return _state.value
        val stopped = synchronized(nativeLifecycleLock) {
            engineEpoch++
            isSeeking = false
            closeOutput()
            val result = active.stop()
            // Output is closed, so drain one control frame on native AudioEngine to execute the
            // queued Stop command synchronously rather than leaving stale Playing snapshot state.
            if (engine is AudioEngine) {
                engine?.renderInto(FloatArray(2), 1)
            }
            result
        }
        val published = publishState(stopped)
        _state.value = published.copy(loaded = false, naturalEnd = false)
        return _state.value
    }

    fun seek(frame: Long): State {
        if (uninterruptible) return _state.value
        val active = controller ?: return _state.value
        if (automaticSource != null) {
            engine?.cancelPendingTransition()
            automaticSource = null
            automaticPending = null
        }
        // Native seekEngine refuses while an AudioOutput lease holds engine->outputActive; suspend
        // the output stream across the seek without dropping interactive transition rules.
        val sought = synchronized(nativeLifecycleLock) {
            val hadOutput = output != null
            if (hadOutput) {
                suspendOrCloseOutput(preserveAutoTransitions = true)
            }
            seekSequence++
            val result = active.seek(frame)
            if (hadOutput) {
                val current = engine
                val stillAudible = result !is PlaybackController.State.Failed ||
                    com.mylittleblover.mkwplayer.audio.EngineSnapshot.read(current?.snapshot())
                        ?.let { it.isPlaying || it.isPaused } == true
                if (current != null && stillAudible) {
                    resumeOrOpenOutput(current)
                }
            }
            result
        }
        return publishState(sought)
    }

    /**
     * Seeks without running native `seekEngine` on the main UI thread. Rapid sequential calls
     * coalesce onto the latest requested [frame] before reopening the output stream.
     */
    suspend fun seekAsync(
        frame: Long,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): State {
        if (uninterruptible) return _state.value
        val active = controller ?: return _state.value
        pendingSeekTarget = frame
        seekRequestGeneration++
        if (isSeeking) return _state.value

        isSeeking = true
        val capturedEpoch = engineEpoch
        var sought: PlaybackController.State? = null
        var hadOutput = false
        var servedGeneration = -1L
        try {
            while (servedGeneration != seekRequestGeneration &&
                capturedEpoch == engineEpoch &&
                controller === active &&
                !uninterruptible
            ) {
                if (automaticSource != null) {
                    engine?.cancelPendingTransition()
                    automaticSource = null
                    automaticPending = null
                }
                val (latestResult, served) = withContext(kotlinx.coroutines.NonCancellable + ioDispatcher) {
                    var latest: PlaybackController.State? = null
                    var localServed = servedGeneration
                    while (localServed != seekRequestGeneration &&
                        capturedEpoch == engineEpoch &&
                        controller === active &&
                        !uninterruptible
                    ) {
                        localServed = seekRequestGeneration
                        val target = pendingSeekTarget
                        latest = synchronized(nativeLifecycleLock) {
                            if (controller === active && engineEpoch == capturedEpoch) {
                                if (output != null) {
                                    hadOutput = true
                                    suspendOrCloseOutput(preserveAutoTransitions = true)
                                }
                                seekSequence++
                                active.seek(target)
                            } else {
                                null
                            }
                        }
                    }
                    latest to localServed
                }
                servedGeneration = served
                if (latestResult != null) {
                    sought = latestResult
                }
            }
            if (hadOutput &&
                sought != null &&
                capturedEpoch == engineEpoch &&
                controller === active
            ) {
                withContext(kotlinx.coroutines.NonCancellable + ioDispatcher) {
                    synchronized(nativeLifecycleLock) {
                        val current = engine
                        val stillAudible = sought !is PlaybackController.State.Failed ||
                            com.mylittleblover.mkwplayer.audio.EngineSnapshot.read(current?.snapshot())
                                ?.let { it.isPlaying || it.isPaused } == true
                        if (current != null &&
                            controller === active &&
                            engineEpoch == capturedEpoch &&
                            stillAudible
                        ) {
                            resumeOrOpenOutput(current)
                        }
                    }
                }
            }
        } finally {
            isSeeking = false
        }
        val result = sought
        if (capturedEpoch != engineEpoch || controller !== active || result == null) {
            return _state.value
        }
        return publishState(result)
    }

    /**
     * Polls the engine.
     *
     * Called by whoever is driving the UI clock and, when the screen is away, by the
     * notification refresher. Cheap enough to call often; it reads one coherent packet.
     */
    fun poll(): State {
        if (isSeeking) return _state.value
        if (!_state.value.active) return _state.value
        val active = controller ?: return _state.value
        val current=active.poll()
        if(_state.value.loaded && (!linearSession || automaticTransitions.isNotEmpty())) {
            val observation=interactiveObservation(includeSourceFrame = !linearSession)
            if (linearSession && observation != null && observation.segment >= 0) {
                if (linearActiveSegment >= 0 && observation.segment != linearActiveSegment) {
                    linearActiveSegment = observation.segment
                    onLinearSegmentAdvanced?.invoke(observation.segment, observation.transportFrame)
                } else if (linearActiveSegment < 0) {
                    linearActiveSegment = observation.segment
                }
            }
            if (observation == null || (observation.segment >= 0 && observation.segment != automaticSource)) {
                automaticSource = null
                automaticPending = null
            }
            if(observation!=null && !observation.paused && !observation.transitioning && (linearSession || observation.sourceFrame!=null)) {
                val target=automaticTransitions[observation.segment]
                if(target!=null && automaticSource!=observation.segment) {
                    if (linearSession) {
                        val queued = active.tryQueueSegment(target)
                        if (queued == null) {
                            // Seek landed past the pre-entry lead window; finish current segment and
                            // let onLinearCompleted open the next queue entry.
                            automaticSource = observation.segment
                            automaticPending = null
                            return publishState(current)
                        }
                        if (queued !is PlaybackController.State.Failed) {
                            automaticSource = observation.segment
                            automaticPending = Triple(observation.segment, target, observation.failureCount)
                        }
                        return publishState(queued)
                    } else {
                        val queued=active.playSegment(target)
                        if(queued !is PlaybackController.State.Failed) {
                            automaticSource=observation.segment
                            automaticPending=Triple(observation.segment,target,observation.failureCount)
                        }
                        return publishState(queued)
                    }
                }
            }
        }
        if (_state.value.loaded && linearSession &&
            current is PlaybackController.State.Stopped && current.naturalEnd) {
            uninterruptible = false
            val published = publishState(current)
            val handedOff = onLinearCompleted?.invoke() == true
            if (!handedOff && _state.value.naturalEnd) {
                _state.value = _state.value.copy(loaded = false)
            }
            return _state.value
        }
        return publishState(current)
    }

    /** Releases the output and the engine. Safe to call more than once. */
    fun release() {
        uninterruptible = false
        engineEpoch++
        isSeeking = false
        closeOutput()
        linearSession = false
        linearActiveSegment = -1
        linearDisplayOffsetFrames = 0L
        linearDisplayTotalFrames = 0L
        lastObservedSourceSlot = -1
        synchronized(nativeLifecycleLock) {
            controller?.release()
            controller = null
            engine = null
        }
        _state.value = State()
    }

    /** Native output destruction joins its callback before a graph or binding can change. */
    private fun closeOutput(preserveAutoTransitions: Boolean = false) {
        output?.close()
        output = null
        if (!preserveAutoTransitions) {
            automaticTransitions = emptyMap()
        }
        automaticSource = null
        automaticPending = null
    }

    /**
     * Suspends an existing output handle when supported so native Oboe worker threads survive
     * across same-engine mutations/seeks; falls back to full close for legacy/fake outputs.
     */
    private fun suspendOrCloseOutput(preserveAutoTransitions: Boolean = false) {
        val currentOutput = output
        if (currentOutput != null && currentOutput.suspendStream()) {
            if (!preserveAutoTransitions) {
                automaticTransitions = emptyMap()
            }
            automaticSource = null
            automaticPending = null
        } else {
            closeOutput(preserveAutoTransitions = preserveAutoTransitions)
        }
    }

    private fun resumeOrOpenOutput(target: SessionAudioEngine) {
        val currentOutput = output
        if (currentOutput != null) {
            if (currentOutput.resumeStream(target.sampleRate)) {
                return
            }
            currentOutput.close()
            output = null
        }
        output = openOutput(target.nativeHandle(), target.sampleRate)
    }

    private fun publishState(controllerState: PlaybackController.State): State {
        val previous = _state.value
        return publish(
            controllerState,
            previous.title,
            previous.totalFrames,
            previous.sampleRate,
        )
    }

    private fun publish(
        controllerState: PlaybackController.State,
        title: String,
        totalFrames: Long,
        sampleRate: Int,
    ): State {
        val (rawPosition, detail) = when (controllerState) {
            is PlaybackController.State.Playing -> controllerState.positionFrames to null
            is PlaybackController.State.Paused -> controllerState.positionFrames to null
            is PlaybackController.State.Ready -> 0L to null
            is PlaybackController.State.Stopped -> 0L to null
            is PlaybackController.State.Idle -> 0L to null
            is PlaybackController.State.Failed -> 0L to controllerState.reason
        }
        val position = if (linearSession && linearDisplayOffsetFrames > 0L) {
            (rawPosition - linearDisplayOffsetFrames).coerceAtLeast(0L)
        } else {
            rawPosition
        }
        val prevState = _state.value

        val resolvedTitle = when {
            linearSession && linearDisplayOffsetFrames > 0L && prevState.title.isNotBlank() -> prevState.title
            controllerState is PlaybackController.State.Playing -> controllerState.title
            controllerState is PlaybackController.State.Paused -> controllerState.title
            controllerState is PlaybackController.State.Ready -> controllerState.title
            controllerState is PlaybackController.State.Stopped -> controllerState.title
            else -> title
        }.ifBlank { title }

        val resolvedTotal = when {
            linearSession && linearDisplayTotalFrames > 0L -> linearDisplayTotalFrames
            linearSession && linearDisplayOffsetFrames > 0L && prevState.totalFrames > 0L -> prevState.totalFrames
            controllerState is PlaybackController.State.Playing -> controllerState.totalFrames
            controllerState is PlaybackController.State.Paused -> controllerState.totalFrames
            controllerState is PlaybackController.State.Stopped -> controllerState.totalFrames
            controllerState is PlaybackController.State.Ready -> controllerState.totalFrames
            else -> totalFrames
        }

        val resolvedRate = when (controllerState) {
            is PlaybackController.State.Playing -> controllerState.sampleRate
            is PlaybackController.State.Paused -> controllerState.sampleRate
            is PlaybackController.State.Ready -> controllerState.sampleRate
            is PlaybackController.State.Stopped -> controllerState.sampleRate
            else -> sampleRate
        }

        _state.value = State(
            title = resolvedTitle,
            playing = controllerState is PlaybackController.State.Playing,
            paused = controllerState is PlaybackController.State.Paused,
            positionFrames = position,
            totalFrames = resolvedTotal,
            sampleRate = resolvedRate,
            detail = detail,
            // Preserved across polls: only load, stop, release and failure change it.
            loaded = _state.value.loaded,
            linear = linearSession,
            naturalEnd = (controllerState as? PlaybackController.State.Stopped)?.naturalEnd == true,
            artworkName = prevState.artworkName,
            courseCode = prevState.courseCode,
            packDirPath = prevState.packDirPath,
            subtitle = prevState.subtitle,
        )
        return _state.value
    }

    private fun fail(reason: String): State {
        closeOutput()
        _state.value = _state.value.copy(
            playing = false,
            paused = false,
            loaded = false,
            naturalEnd = false,
            detail = reason,
        )
        return _state.value
    }
}
