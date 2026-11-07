package com.mylittleblover.mkwplayer.audio

/**
 * Reads the fields a player needs out of an engine snapshot.
 *
 * The bridge returns a fixed 65-long packet. Reading it by index all over the UI
 * would spread one wire layout across many files, so the offsets live here once
 * with the native source named next to them.
 *
 * Layout is fixed by `NativeAudioBridge.cpp`: `[0]` wire version 8, `[1]` transport
 * frame, `[2]` transport status, `[3]` active voices, `[4]` current object,
 * `[5]` dropped voices, `[6]` rejected commands, `[7]` last result.
 *
 * A packet that is absent, too short, or a different wire version yields null
 * instead of a guess: a shifted read would show "playing" while nothing plays,
 * which is exactly the failure this project has already hit twice.
 */
internal object EngineSnapshot {

    /** Wire version emitted by the current bridge. */
    const val WIRE_VERSION = 8L

    private const val MIN_LENGTH = 8

    /** TransportStatus::Stopped */
    const val STOPPED = 0L

    /** TransportStatus::Playing */
    const val PLAYING = 1L

    /** TransportStatus::Paused */
    const val PAUSED = 2L

    data class Fields(
        val transportFrame: Long,
        val status: Long,
        val activeVoices: Long,
        val currentObject: Long,
        val rejectedCommands: Long,
        val lastResult: Long,
    ) {
        val isPlaying: Boolean get() = status == PLAYING
        val isPaused: Boolean get() = status == PAUSED
        val isStopped: Boolean get() = status == STOPPED
    }

    fun read(snapshot: LongArray?): Fields? {
        if (snapshot == null || snapshot.size < MIN_LENGTH) return null
        if (snapshot[0] != WIRE_VERSION) return null
        return Fields(
            transportFrame = snapshot[1],
            status = snapshot[2],
            activeVoices = snapshot[3],
            currentObject = snapshot[4],
            rejectedCommands = snapshot[6],
            lastResult = snapshot[7],
        )
    }

    /**
     * Elapsed time in frames converted to milliseconds at [rate].
     *
     * Frames are the only time unit the engine reports, so every display value
     * must go through here rather than assuming a rate.
     */
    fun framesToMillis(frames: Long, rate: Int): Long =
        if (rate <= 0) 0 else frames * 1000L / rate

    /** Formats a frame position as `m:ss`, or `--:--` when the rate is unusable. */
    fun formatPosition(frames: Long, rate: Int): String {
        if (rate <= 0 || frames < 0) return "--:--"
        val totalSeconds = frames / rate
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
