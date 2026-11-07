package com.mylittleblover.mkwplayer.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.AudioOutput
import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.PlaybackController
import com.mylittleblover.mkwplayer.audio.PlaybackEngine
import com.mylittleblover.mkwplayer.audio.TimingDiagnosis
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.library.AudioPicker
import com.mylittleblover.mkwplayer.library.Playability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Holds the picked tracks and drives the one open transport.
 *
 * Responsibilities stay narrow: accept a picked file, prepare it once, remember what
 * was learned about it, and drive the transport through [PlaybackController]. All
 * audio semantics stay in the engine; all transport rules stay in the controller.
 *
 * Preparation is cached per digest because it costs a hash and a graph build over
 * the whole file, and re-selecting a track should not repeat that.
 */
internal class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    /** One row in the library. */
    data class Track(
        val key: String,
        val name: String,
        val frames: Long,
        val sampleRate: Int,
        val playable: Boolean,
        val reason: String?,
    )

    /** Everything the screen needs, as one value. */
    data class Ui(
        val tracks: List<Track> = emptyList(),
        val selectedKey: String? = null,
        val title: String = "",
        val status: String = "No track loaded",
        val position: String = "--:--",
        val total: String = "--:--",
        val progress: Float = 0f,
        val canPause: Boolean = false,
        val canResume: Boolean = false,
        val canStop: Boolean = false,
        val timing: String = "Timing unavailable",
        val timingVerdict: TimingDiagnosis.Verdict = TimingDiagnosis.Verdict.NO_DATA,
        val outputActive: Boolean = false,
        val detail: String? = null,
        val busy: Boolean = false,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private data class Prepared(
        val name: String,
        val bytes: ByteArray,
        val packet: ByteArray,
        val frames: Long,
        val sampleRate: Int,
    )

    private val prepared = mutableMapOf<String, Prepared>()
    private var controller: PlaybackController? = null
    private var engine: AudioEngine? = null
    private var output: AudioOutput? = null
    private var pollJob: Job? = null
    private var totalFrames: Long = 0
    private var rate: Int = 0

    /**
     * Adds a picked file to the library. Copies, inspects and prepares it off the
     * main thread, then selects it.
     */
    fun add(uri: Uri) {
        _ui.value = _ui.value.copy(busy = true, status = "Reading file...", detail = null)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { inspect(uri) }
            _ui.value = _ui.value.copy(busy = false)

            when (outcome) {
                is Outcome.Refused -> {
                    _ui.value = _ui.value.copy(status = "Cannot play this file", detail = outcome.reason)
                }
                is Outcome.Added -> {
                    _ui.value = _ui.value.copy(
                        tracks = _ui.value.tracks + outcome.track,
                        status = if (outcome.track.playable) "Ready to play" else "Unsupported codec",
                        detail = outcome.track.reason,
                    )
                    if (outcome.track.playable) select(outcome.track.key)
                }
            }
        }
    }

    private sealed interface Outcome {
        data class Added(val track: Track) : Outcome
        data class Refused(val reason: String) : Outcome
    }

    private fun inspect(uri: Uri): Outcome {
        val picked = when (val copied = AudioPicker.copy(getApplication(), uri)) {
            is AudioPicker.Result.Rejected -> return Outcome.Refused(copied.reason)
            is AudioPicker.Result.Ok -> copied.picked
        }

        val bytes = try {
            picked.readBytes()
        } catch (e: Exception) {
            return Outcome.Refused("The file could not be opened: ${e.message}")
        }

        val header = when (val read = BwavHeader.read(bytes)) {
            is BwavHeader.Result.Rejected -> return Outcome.Refused(read.reason)
            is BwavHeader.Result.Ok -> read.info
        }

        val playability = Playability.evaluate(header.codecKind)
        val track = Track(
            key = picked.key,
            name = picked.displayName,
            frames = header.frames.toLong(),
            sampleRate = header.sampleRate,
            playable = playability.playable,
            reason = playability.reason,
        )

        if (!playability.playable) {
            // Kept in the library so the limitation is visible instead of the file
            // silently vanishing after the picker closes.
            return Outcome.Added(track)
        }

        val packet = try {
            GraphBuilder.build(
                GraphBuilder.Spec(
                    asset = GraphBuilder.Asset(
                        path = picked.displayName,
                        sampleRate = header.sampleRate,
                        channels = header.channels,
                        frames = header.frames.toLong(),
                        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes),
                        loop = if (header.hasLoop) {
                            GraphBuilder.Loop(header.loopStart!!.toLong(), header.loopEnd!!.toLong())
                        } else {
                            null
                        },
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            return Outcome.Refused("This file cannot be prepared: ${e.message}")
        }

        val probed = GraphProbe.inspect(packet)
        if (probed is GraphProbe.Result.Rejected) {
            return Outcome.Refused("Internal check failed: ${probed.reason}")
        }

        prepared[picked.key] = Prepared(
            name = picked.displayName,
            bytes = bytes,
            packet = packet,
            frames = header.frames.toLong(),
            sampleRate = header.sampleRate,
        )
        return Outcome.Added(track)
    }

    /** Starts the track with the given key, or reports why it cannot start. */
    fun select(key: String) {
        val track = _ui.value.tracks.firstOrNull { it.key == key } ?: return
        if (!track.playable) {
            _ui.value = _ui.value.copy(
                selectedKey = key,
                title = track.name,
                status = "Unsupported codec",
                detail = track.reason,
                canPause = false,
                canResume = false,
                canStop = false,
            )
            return
        }

        val ready = prepared[key]
        if (ready == null) {
            _ui.value = _ui.value.copy(
                selectedKey = key,
                status = "Not prepared",
                detail = "Pick this file again to reopen it.",
            )
            return
        }

        start(track, ready)
    }

    private fun start(track: Track, ready: Prepared) {
        releaseTransport()

        val created = AudioEngine.create()
        if (created == null) {
            _ui.value = _ui.value.copy(status = "Cannot play this file", detail = "Out of memory.")
            return
        }

        val controller = PlaybackController(created as PlaybackEngine)
        val state = controller.load(
            title = ready.name,
            packet = ready.packet,
            audio = ready.bytes,
            assetId = ready.name,
        )
        if (state is PlaybackController.State.Failed) {
            created.close()
            _ui.value = _ui.value.copy(status = "Cannot play this file", detail = state.reason)
            return
        }

        controller.setDuration(ready.frames)
        this.controller = controller
        this.engine = created
        totalFrames = ready.frames
        rate = ready.sampleRate

        // Device output is best-effort. An emulator started with -no-audio has no
        // stream, and that must not be reported as a playback failure.
        output = AudioOutput.open(created.nativeHandle(), ready.sampleRate)

        _ui.value = _ui.value.copy(
            selectedKey = track.key,
            title = ready.name,
            status = "Playing",
            total = EngineSnapshot.formatPosition(totalFrames, rate),
            canPause = true,
            canStop = true,
            outputActive = output != null,
            detail = if (output == null) {
                "No device output on this system; the engine is running without a speaker."
            } else {
                null
            },
        )
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val active = controller ?: break
                publish(active.poll())
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun publish(state: PlaybackController.State) {
        val positionFrames = when (state) {
            is PlaybackController.State.Playing -> state.positionFrames
            is PlaybackController.State.Paused -> state.positionFrames
            else -> 0L
        }
        val progress = if (totalFrames > 0) {
            (positionFrames.toDouble() / totalFrames).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

        val timing = TimingDiagnosis.read(output?.timingSnapshot())

        _ui.value = _ui.value.copy(
            status = when (state) {
                is PlaybackController.State.Idle -> "No track loaded"
                is PlaybackController.State.Ready -> "Ready"
                is PlaybackController.State.Playing -> "Playing"
                is PlaybackController.State.Paused -> "Paused"
                is PlaybackController.State.Stopped -> "Stopped"
                is PlaybackController.State.Failed -> "Failed"
            },
            position = EngineSnapshot.formatPosition(positionFrames, rate),
            total = EngineSnapshot.formatPosition(totalFrames, rate),
            progress = progress,
            canPause = state is PlaybackController.State.Playing,
            canResume = state is PlaybackController.State.Paused,
            canStop = state is PlaybackController.State.Playing ||
                state is PlaybackController.State.Paused,
            timing = timing?.explanation ?: "Timing unavailable",
            timingVerdict = timing?.verdict ?: TimingDiagnosis.Verdict.NO_DATA,
            detail = if (state is PlaybackController.State.Failed) {
                state.reason
            } else {
                _ui.value.detail
            },
        )
    }

    fun pause() {
        controller?.pause()?.let { publish(it) }
    }

    fun resume() {
        controller?.resume()?.let { publish(it) }
    }

    fun stop() {
        controller?.stop()?.let { publish(it) }
        _ui.value = _ui.value.copy(progress = 0f, position = EngineSnapshot.formatPosition(0, rate))
    }

    private fun releaseTransport() {
        pollJob?.cancel()
        pollJob = null
        output?.close()
        output = null
        controller?.release()
        controller = null
        engine = null
    }

    override fun onCleared() {
        releaseTransport()
        super.onCleared()
    }

    companion object {
        /** 200 ms keeps the progress bar smooth without touching the audio thread. */
        const val POLL_INTERVAL_MS = 200L
    }
}
