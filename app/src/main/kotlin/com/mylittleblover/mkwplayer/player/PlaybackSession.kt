package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.AudioOutput
import com.mylittleblover.mkwplayer.audio.AudioStatusText
import com.mylittleblover.mkwplayer.audio.PlaybackController
import com.mylittleblover.mkwplayer.audio.PlaybackEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Threading: every method runs on the control thread (main). The engine's own contract
 * requires that, and nothing here starts a thread of its own.
 */
internal class PlaybackSession(
    private val createEngine: () -> AudioEngine?,
    private val openOutput: (Long, Int) -> AudioOutput?,
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
    ) {
        /** Something is loaded, so the transport is worth keeping alive and showing. */
        val active: Boolean get() = playing || paused || loaded
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var engine: AudioEngine? = null
    private var controller: PlaybackController? = null
    private var output: AudioOutput? = null

    /** True while a device stream is open. */
    val hasOutput: Boolean get() = output != null

    /**
     * Callback timing packet from the open stream, or null when there is none.
     *
     * Exposed here so the screen can show real timing without owning the output. A null
     * is normal: the service may own the stream while the screen is away, and a system
     * with no audio device never opens one at all.
     */
    fun timingSnapshot(): LongArray? = output?.timingSnapshot()

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
    ): State {
        var current = engine
        if (current == null) {
            current = createEngine() ?: return fail("Out of memory creating the audio engine.")
            engine = current
            controller = PlaybackController(current as PlaybackEngine)
        }
        val active = controller ?: return fail("The audio engine is not initialised.")

        val loaded = active.loadGraphOnly(packet)
        if (loaded is PlaybackController.State.Failed) return fail(loaded.reason)

        // One bind per part. A flow that bound only its first asset would play the prelude and
        // then fail silently at the first transition.
        assets.forEachIndexed { index, (name, bytes) ->
            val status = current.bindBwav(index, bytes, name)
            if (status != AudioEngine.STATUS_OK) {
                return fail("Could not bind '$name': ${AudioStatusText.engineHint(status) ?: AudioStatusText.engine(status)}")
            }
        }

        val started = active.playSegment(startSegment)
        if (started is PlaybackController.State.Failed) return fail(started.reason)

        active.setDuration(frames)
        if (output == null) {
            output = openOutput(current.nativeHandle(), current.sampleRate)
        }

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
        val result = active.load(title, packet, audio, assetId)
        if (result is PlaybackController.State.Failed) {
            return fail(result.reason)
        }
        active.setDuration(frames)

        // The output must be leased after the graph is loaded and the transport is
        // playing, because binding is refused while a consumer holds the engine.
        if (output == null) {
            output = openOutput(current.nativeHandle(), current.sampleRate)
        }

        val published = publish(active.poll(), title, frames, current.sampleRate)
        // Play was accepted, so the transport counts as loaded even if the queued command
        // has not been processed by the engine yet.
        _state.value = published.copy(loaded = true)
        return _state.value
    }

    fun pause(): State {
        val active = controller ?: return _state.value
        return publishState(active.pause())
    }

    fun resume(): State {
        val active = controller ?: return _state.value
        return publishState(active.resume())
    }

    fun stop(): State {
        val active = controller ?: return _state.value
        val published = publishState(active.stop())
        _state.value = published.copy(loaded = false)
        return _state.value
    }

    fun seek(frame: Long): State {
        val active = controller ?: return _state.value
        return publishState(active.seek(frame))
    }

    /**
     * Polls the engine.
     *
     * Called by whoever is driving the UI clock and, when the screen is away, by the
     * notification refresher. Cheap enough to call often; it reads one coherent packet.
     */
    fun poll(): State {
        val active = controller ?: return _state.value
        return publishState(active.poll())
    }

    /** Releases the output and the engine. Safe to call more than once. */
    fun release() {
        output?.close()
        output = null
        controller?.release()
        controller = null
        engine = null
        _state.value = State()
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
        val (position, detail) = when (controllerState) {
            is PlaybackController.State.Playing -> controllerState.positionFrames to null
            is PlaybackController.State.Paused -> controllerState.positionFrames to null
            is PlaybackController.State.Ready -> 0L to null
            is PlaybackController.State.Stopped -> 0L to null
            is PlaybackController.State.Idle -> 0L to null
            is PlaybackController.State.Failed -> 0L to controllerState.reason
        }

        val resolvedTitle = when (controllerState) {
            is PlaybackController.State.Playing -> controllerState.title
            is PlaybackController.State.Paused -> controllerState.title
            is PlaybackController.State.Ready -> controllerState.title
            is PlaybackController.State.Stopped -> controllerState.title
            else -> title
        }

        val resolvedTotal = when (controllerState) {
            is PlaybackController.State.Playing -> controllerState.totalFrames
            is PlaybackController.State.Paused -> controllerState.totalFrames
            is PlaybackController.State.Stopped -> controllerState.totalFrames
            is PlaybackController.State.Ready -> controllerState.totalFrames
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
        )
        return _state.value
    }

    private fun fail(reason: String): State {
        _state.value = _state.value.copy(playing = false, paused = false, loaded = false, detail = reason)
        return _state.value
    }
}
