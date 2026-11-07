package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.AudioOutput

/**
 * The process-wide audio session.
 *
 * A foreground service cannot own a lifecycle-scoped object, and playback has to outlive
 * the activity, so ownership sits here. The service and the UI both read the same
 * instance, which is why there is exactly one engine rather than one per screen.
 *
 * Kept as a holder rather than a singleton on [PlaybackSession] itself so tests can
 * substitute a session without touching global state at class-initialisation time.
 */
internal object PlaybackHolder {

    private var instance: PlaybackSession? = null

    fun get(): PlaybackSession = instance ?: PlaybackSession(
        createEngine = { AudioEngine.create() },
        openOutput = { handle, rate -> AudioOutput.open(handle, rate) },
    ).also { instance = it }

    /** Replaces the session. Test-only; production never calls this. */
    fun overrideForTest(session: PlaybackSession?) {
        instance?.release()
        instance = session
    }
}
