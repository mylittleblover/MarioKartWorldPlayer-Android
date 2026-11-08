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

        /** Stopped by the user, position reset. */
        data class Stopped(val title: String, val totalFrames: Long, val sampleRate: Int) : State

        data class Failed(val reason: String) : State
    }

    var state: State = State.Idle
        private set

    private var title: String = ""
    private var totalFrames: Long = 0
    private var sampleRate: Int = 0

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

        this.title = title
        this.sampleRate = engine.sampleRate

        val started = engine.play(TrackOpener.SINGLE_SEGMENT)
        if (started != AudioEngine.STATUS_OK) {
            return fail("Could not start playback: ${describe(started)}")
        }

        state = State.Ready(title, totalFrames = 0, sampleRate = this.sampleRate)
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
     * Loads a graph without starting anything.
     *
     * A multi-segment flow binds several assets before playing, and binding is refused while the
     * transport is running. So the flow path loads the packet, binds, then plays: separate steps
     * rather than the single bundled sequence [load] performs.
     */
    fun loadGraphOnly(packet: ByteArray): State {
        val status = engine.loadGraph(packet)
        if (status != AudioEngine.STATUS_OK) {
            return fail("Could not load the flow: ${describe(status)}")
        }
        state = State.Ready(title, totalFrames = 0, sampleRate = engine.sampleRate)
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
        val status = engine.play(segment)
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

        val position = fields.transportFrame
        state = when {
            fields.isPlaying -> State.Playing(title, position, totalFrames, sampleRate)
            fields.isPaused -> State.Paused(title, position, totalFrames, sampleRate)
            fields.isStopped && totalFrames in 1..position -> State.Stopped(title, totalFrames, sampleRate)
            else -> state
        }
        return state
    }

    fun pause(): State {
        val result = engine.pause()
        if (result != AudioEngine.STATUS_OK) return fail("Could not pause: ${describe(result)}")
        return poll()
    }

    fun resume(): State {
        val result = engine.resume()
        if (result != AudioEngine.STATUS_OK) return fail("Could not resume: ${describe(result)}")
        return poll()
    }

    fun stop(): State {
        val result = engine.stop()
        if (result != AudioEngine.STATUS_OK) return fail("Could not stop: ${describe(result)}")
        state = State.Stopped(title, totalFrames, sampleRate)
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
        if (!SeekTarget.isAddressable(frame, totalFrames)) {
            return fail("Cannot seek to frame $frame of $totalFrames.")
        }
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
        sampleRate = 0
        return state
    }

    private fun fail(reason: String): State {
        state = State.Failed(reason)
        return state
    }

    private fun describe(status: Int): String =
        AudioStatusText.engineHint(status) ?: AudioStatusText.engine(status)
}
