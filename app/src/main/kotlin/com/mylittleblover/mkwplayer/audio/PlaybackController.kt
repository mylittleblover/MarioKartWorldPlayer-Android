package com.mylittleblover.mkwplayer.audio

/**
 * Playback state machine for one track.
 *
 * Every transition is explicit and returns a new [State], so the UI can render one
 * value and the JVM tests can walk the whole graph without a device. The engine is
 * an interface here for exactly that reason.
 *
 * The states encode two facts that have already caused wrong conclusions in this
 * project:
 *
 *  - `Playing` does not imply audible output until [position] advances. Play with a
 *    bad target returns Ok and starts nothing.
 *  - [State.Failed] carries a reason fit for display, never a bare status code.
 */
internal class PlaybackController(private val engine: PlaybackEngine) {

    sealed interface State {
        /** Nothing loaded. */
        data object Idle : State

        /** A track is loaded and ready, but the transport has not started. */
        data class Ready(val title: String, val totalFrames: Long, val sampleRate: Int) : State

        data class Playing(
            val title: String,
            val positionFrames: Long,
            val totalFrames: Long,
            val sampleRate: Int,
        ) : State

        data class Paused(
            val title: String,
            val positionFrames: Long,
            val totalFrames: Long,
            val sampleRate: Int,
        ) : State

        /** Stopped by the user or natural track end, position reset. */
        data class Stopped(
            val title: String,
            val totalFrames: Long,
            val sampleRate: Int,
            val naturalEnd: Boolean = false,
        ) : State

        data class Failed(val reason: String) : State
    }

    var state: State = State.Idle
        private set

    private var title: String = ""
    private var totalFrames: Long = 0
    private var seekLimitFrames: Long = 0
    private var sampleRate: Int = 0
    private var linearMode: Boolean = false
    private var linearRepeating: Boolean = false
    private var linearFiniteDuration: Boolean = false
    private var userStopped: Boolean = false
    private var sawActivePlayback: Boolean = false
    private var reportedNaturalEnd: Boolean = false

    /**
     * Loads a prepared track and starts it.
     *
     * Takes the packet and bytes rather than the [TrackOpener] result so the caller
     * decides when to build; this method only sequences the engine calls.
     */
    fun load(title: String, packet: ByteArray, audio: ByteArray, assetId: String): State {
        if (state is State.Playing || state is State.Paused) {
            engine.stop()
        }

        val loaded = engine.loadGraph(packet)
        if (loaded != AudioEngine.STATUS_OK) {
            return fail("Could not load the track: ${describe(loaded)}")
        }

        val bound = engine.bindBwav(assetIndex = 0, bytes = audio, assetId = assetId)
        if (bound != AudioEngine.STATUS_OK) {
            return fail("Could not read the audio: ${describe(bound)}")
        }

        linearMode = false
        linearRepeating = false
        linearFiniteDuration = false
        seekLimitFrames = 0
        userStopped = false
        sawActivePlayback = false
        reportedNaturalEnd = false
        this.title = title
        this.sampleRate = engine.sampleRate

        val started = engine.play(TrackOpener.SINGLE_SEGMENT)
        if (started != AudioEngine.STATUS_OK) {
            return fail("Could not start playback: ${describe(started)}")
        }

        state = State.Ready(title, totalFrames = 0, sampleRate = this.sampleRate)
        return state
    }

    /** Loads and starts a single non-graph linear source (streamed by default for Jukebox/OST tracks). */
    fun loadLinear(
        title: String,
        audio: ByteArray,
        assetId: String,
        loop: Boolean = false,
        streamed: Boolean = true,
    ): State {
        if (state is State.Playing || state is State.Paused) {
            engine.stop()
        }
        val loaded = engine.loadLinearBwav(audio, assetId, loop = loop, streamed = streamed)
        if (loaded != AudioEngine.STATUS_OK) {
            return fail("Could not read the audio: ${describe(loaded)}")
        }
        linearMode = true
        linearRepeating = loop
        linearFiniteDuration = false
        seekLimitFrames = 0
        userStopped = false
        sawActivePlayback = false
        reportedNaturalEnd = false
        this.title = title
        this.sampleRate = engine.sampleRate

        val started = engine.play(AudioEngine.PLAY_LINEAR)
        if (started != AudioEngine.STATUS_OK) {
            return fail("Could not start playback: ${describe(started)}")
        }
        state = State.Ready(title, totalFrames = 0, sampleRate = this.sampleRate)
        return state
    }

    /** Loads and starts a single non-graph linear source directly from a File without full-file ByteArray copy. */
    fun loadLinearFromFile(
        title: String,
        file: java.io.File,
        assetId: String,
        loop: Boolean = false,
        streamed: Boolean = true,
    ): State {
        if (state is State.Playing || state is State.Paused) {
            engine.stop()
        }
        val loaded = engine.loadLinearFile(file, assetId, loop = loop, streamed = streamed)
        if (loaded != AudioEngine.STATUS_OK) {
            return fail("Could not read the audio: ${describe(loaded)}")
        }
        linearMode = true
        linearRepeating = loop
        linearFiniteDuration = false
        seekLimitFrames = 0
        userStopped = false
        sawActivePlayback = false
        reportedNaturalEnd = false
        this.title = title
        this.sampleRate = engine.sampleRate

        val started = engine.play(AudioEngine.PLAY_LINEAR)
        if (started != AudioEngine.STATUS_OK) {
            return fail("Could not start playback: ${describe(started)}")
        }
        state = State.Ready(title, totalFrames = 0, sampleRate = this.sampleRate)
        return state
    }

    /** Updates linear single-track repeat state while the output consumer is released. */
    fun setLinearRepeat(enabled: Boolean, begin: Long = 0L, end: Long = totalFrames): State {
        if (!linearMode) return state
        val status = if (enabled && begin >= 0L && end > begin) {
            engine.setLinearRepeat(true, begin, end)
        } else {
            engine.setLinearRepeat(enabled, -1L, -1L)
        }
        if (status != AudioEngine.STATUS_OK) {
            return fail("Could not update repeat: ${describe(status)}")
        }
        linearRepeating = enabled
        return poll()
    }

    /** Configures live finite playback end + fade-out window on a linear looping track. */
    fun setLinearFadeWindow(endFrame: Long, fadeOutFrames: Long, displayTotalFrames: Long): State {
        if (!linearMode) return state
        val status = engine.setLinearFadeWindow(endFrame, fadeOutFrames)
        if (status != AudioEngine.STATUS_OK) {
            return fail("Could not update loop duration: ${describe(status)}")
        }
        linearFiniteDuration = endFrame > 0L
        if (displayTotalFrames > 0L) {
            setDuration(displayTotalFrames)
        }
        return state
    }

    /** Records the track length once the caller knows it from the header. */
    fun setDuration(frames: Long): State {
        totalFrames = frames
        state = when (val current = state) {
            is State.Ready -> State.Ready(title, frames, sampleRate)
            is State.Playing -> State.Playing(title, current.positionFrames, frames, sampleRate)
            is State.Paused -> State.Paused(title, current.positionFrames, frames, sampleRate)
            is State.Stopped -> State.Stopped(title, frames, sampleRate)
            else -> current
        }
        return state
    }

    /**
     * Expands the addressable native transport range for multi-segment chains (e.g. Survival Rally
     * Stops 2..6 where native transport frames include prior stops' origins) without altering the
     * active stop's displayed [totalFrames].
     */
    fun setSeekLimit(maxFrames: Long) {
        seekLimitFrames = maxFrames.coerceAtLeast(0L)
    }

    /**
     * Loads a graph without starting anything.
     *
     * A multi-segment flow binds several assets before playing, and binding is refused while the
     * transport is running. So the flow path loads the packet, binds, then plays: separate steps
     * rather than the single bundled sequence [load] performs.
     */
    fun loadGraphOnly(packet: ByteArray, title: String = ""): State {
        val status = engine.loadGraph(packet)
        if (status != AudioEngine.STATUS_OK) {
            return fail("Could not load the flow: ${describe(status)}")
        }
        this.title = title
        linearMode = false
        linearRepeating = false
        linearFiniteDuration = false
        seekLimitFrames = 0
        userStopped = false
        sawActivePlayback = false
        reportedNaturalEnd = false
        state = State.Ready(this.title, totalFrames = 0, sampleRate = engine.sampleRate)
        sampleRate = engine.sampleRate
        return state
    }

    /**
     * Requests a segment by index.
     *
     * The index is a graph node, not a position in a list: for a flow it names one section, and
     * the engine decides whether the move is a cut or a transition based on the rules in the
     * graph.
     */
    fun playSegment(segment: Int): State {
        userStopped = false
        reportedNaturalEnd = false
        val status = engine.play(segment)
        if (status != AudioEngine.STATUS_OK) {
            return fail("Could not play section $segment: ${describe(status)}")
        }
        return poll()
    }

    /**
     * Queues a transition target segment, returning null instead of failing if the engine reports
     * [AudioEngine.STATUS_DEADLINE_MISS] (e.g. a seek landed past the pre-entry overlap lead).
     */
    fun tryQueueSegment(segment: Int): State? {
        userStopped = false
        reportedNaturalEnd = false
        val status = engine.play(segment)
        if (status == AudioEngine.STATUS_DEADLINE_MISS) {
            return null
        }
        if (status != AudioEngine.STATUS_OK) {
            return fail("Could not play section $segment: ${describe(status)}")
        }
        return poll()
    }

    /**
     * Polls the engine and advances the visible state.
     *
     * Returns the current state. Call on the control thread while audio runs; the
     * snapshot is coherent by itself, so no locking is needed here.
     */
    fun poll(): State {
        val fields = EngineSnapshot.read(engine.snapshot())
            ?: return if (state is State.Idle) state else fail("The engine stopped reporting state.")

        if (userStopped) {
            state = State.Stopped(title, totalFrames, sampleRate, naturalEnd = false)
            return state
        }

        val rawPosition = fields.transportFrame
        val position = if (linearMode && totalFrames > 0L) {
            if (linearFiniteDuration) {
                rawPosition.coerceIn(0L, totalFrames)
            } else {
                val source = engine.sourceSnapshot(0)
                if (source != null && source.size >= 11 && source[1] == 1L) {
                    source[10].coerceIn(0L, totalFrames)
                } else if (linearRepeating) {
                    rawPosition % totalFrames
                } else {
                    rawPosition.coerceIn(0L, totalFrames)
                }
            }
        } else {
            rawPosition
        }
        val previous = state
        if (fields.isPlaying || fields.isPaused) {
            sawActivePlayback = true
            reportedNaturalEnd = false
        }
        val reachedNaturalEnd = !reportedNaturalEnd && fields.isStopped && !userStopped &&
            (sawActivePlayback || totalFrames in 1..rawPosition)
        if (reachedNaturalEnd) {
            sawActivePlayback = false
            reportedNaturalEnd = true
        }
        state = when {
            fields.isPlaying -> State.Playing(title, position, totalFrames, sampleRate)
            fields.isPaused -> State.Paused(title, position, totalFrames, sampleRate)
            reachedNaturalEnd -> State.Stopped(title, totalFrames, sampleRate, naturalEnd = true)
            fields.isStopped && previous is State.Stopped && previous.naturalEnd ->
                State.Stopped(title, totalFrames, sampleRate, naturalEnd = false)
            fields.isStopped && totalFrames in 1..rawPosition -> State.Stopped(title, totalFrames, sampleRate)
            else -> previous
        }
        return state
    }

    fun pause(): State {
        val result = engine.pause()
        if (result != AudioEngine.STATUS_OK) return fail("Could not pause: ${describe(result)}")
        return poll()
    }

    fun resume(): State {
        userStopped = false
        reportedNaturalEnd = false
        val result = engine.resume()
        if (result != AudioEngine.STATUS_OK) return fail("Could not resume: ${describe(result)}")
        return poll()
    }

    fun stop(): State {
        userStopped = true
        sawActivePlayback = false
        reportedNaturalEnd = false
        linearRepeating = false
        linearFiniteDuration = false
        val result = engine.stop()
        if (result != AudioEngine.STATUS_OK) return fail("Could not stop: ${describe(result)}")
        state = State.Stopped(title, totalFrames, sampleRate, naturalEnd = false)
        return state
    }

    /**
     * Seeks to [frame].
     *
     * Refuses a target outside the track rather than clamping silently: the UI already
     * clamps through [SeekTarget], so a frame still out of range here means something
     * upstream is wrong and should be reported, not quietly corrected.
     */
    fun seek(frame: Long): State {
        val addressableFrames = maxOf(totalFrames, seekLimitFrames)
        if (!SeekTarget.isAddressable(frame, addressableFrames)) {
            return fail("Cannot seek to frame $frame of $addressableFrames.")
        }
        userStopped = false
        reportedNaturalEnd = false
        val result = engine.seek(frame)
        if (result != AudioEngine.STATUS_OK) {
            return fail("Could not seek: ${describe(result)}")
        }
        return poll()
    }

    /** Closes the engine and returns to Idle. Safe after any state, including Failed. */
    fun release(): State {
        engine.close()
        state = State.Idle
        title = ""
        totalFrames = 0
        seekLimitFrames = 0
        sampleRate = 0
        linearMode = false
        linearRepeating = false
        linearFiniteDuration = false
        userStopped = false
        sawActivePlayback = false
        reportedNaturalEnd = false
        return state
    }

    private fun fail(reason: String): State {
        state = State.Failed(reason)
        return state
    }

    private fun describe(status: Int): String =
        AudioStatusText.engineHint(status) ?: AudioStatusText.engine(status)
}
