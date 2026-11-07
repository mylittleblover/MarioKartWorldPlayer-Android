package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.AudioOutput
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
    ) {
        val active: Boolean get() = playing || paused
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

        return publish(active.poll(), title, frames, current.sampleRate)
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
        return publishState(active.stop())
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
        )
        return _state.value
    }

    private fun fail(reason: String): State {
        _state.value = _state.value.copy(playing = false, paused = false, detail = reason)
        return _state.value
    }
}
