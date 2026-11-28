package com.mylittleblover.mkwplayer.audio

import android.content.Context

internal data class PlaybackBehaviorConfig(
    val segmentSwitchFadeOutMs: Int = 0,
    val perfectBeatEnabled: Boolean = false,
    val liveVisualizerEnabled: Boolean = false,
    val nonInteractiveLoopConfig: NonInteractiveLoopConfig = NonInteractiveLoopConfig.Default,
)

/**
 * Device-wide interactive/flow playback preferences persisted outside tracks, packs, and routes.
 */
internal object PlaybackBehaviorSettings {
    private const val FILE = "mkw-playback-behavior"
    private const val KEY_SEGMENT_SWITCH_FADEOUT_MS = "segment-switch-fadeout-ms"
    private const val KEY_PERFECT_BEAT = "perfect-beat-enabled"
    private const val KEY_LIVE_VISUALIZER = "live-visualizer-enabled"
    private const val KEY_NON_INTERACTIVE_LOOP_MODE = "non-interactive-loop-mode"
    private const val KEY_NON_INTERACTIVE_LOOP_COUNT = "non-interactive-loop-count"
    private const val KEY_NON_INTERACTIVE_TARGET_DURATION_MS = "non-interactive-target-duration-ms"
    private const val KEY_NON_INTERACTIVE_FADEOUT_MS = "non-interactive-fadeout-ms"
    const val MAX_SEGMENT_SWITCH_FADEOUT_MS = 30_000

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun current(context: Context): PlaybackBehaviorConfig {
        val prefs = preferences(context)
        val fadeMs = prefs.getInt(KEY_SEGMENT_SWITCH_FADEOUT_MS, 0)
            .coerceIn(0, MAX_SEGMENT_SWITCH_FADEOUT_MS)
        val perfectBeat = prefs.getBoolean(KEY_PERFECT_BEAT, false)
        val liveVisualizer = prefs.getBoolean(KEY_LIVE_VISUALIZER, false)
        val modeName = prefs.getString(KEY_NON_INTERACTIVE_LOOP_MODE, null)
        val loopMode = NonInteractiveLoopMode.entries.firstOrNull { it.name == modeName }
            ?: NonInteractiveLoopMode.LOOP_COUNT
        val loopCount = prefs.getInt(KEY_NON_INTERACTIVE_LOOP_COUNT, NonInteractiveLoopConfig.DEFAULT_LOOP_COUNT)
        val targetDurationMs = prefs.getLong(
            KEY_NON_INTERACTIVE_TARGET_DURATION_MS,
            NonInteractiveLoopConfig.DEFAULT_TARGET_DURATION_MS,
        )
        val loopFadeMs = prefs.getLong(
            KEY_NON_INTERACTIVE_FADEOUT_MS,
            NonInteractiveLoopConfig.DEFAULT_FADEOUT_MS,
        )
        return PlaybackBehaviorConfig(
            segmentSwitchFadeOutMs = fadeMs,
            perfectBeatEnabled = perfectBeat,
            liveVisualizerEnabled = liveVisualizer,
            nonInteractiveLoopConfig = NonInteractiveLoopConfig(
                mode = loopMode,
                loopCount = loopCount,
                targetDurationMs = targetDurationMs,
                fadeOutMs = loopFadeMs,
            ).normalized(),
        )
    }

    fun setSegmentSwitchFadeOutMs(context: Context, ms: Int): PlaybackBehaviorConfig {
        val clamped = ms.coerceIn(0, MAX_SEGMENT_SWITCH_FADEOUT_MS)
        preferences(context).edit()
            .putInt(KEY_SEGMENT_SWITCH_FADEOUT_MS, clamped)
            .apply()
        return current(context)
    }

    fun setPerfectBeatEnabled(context: Context, enabled: Boolean): PlaybackBehaviorConfig {
        preferences(context).edit()
            .putBoolean(KEY_PERFECT_BEAT, enabled)
            .apply()
        return current(context)
    }

    fun setLiveVisualizerEnabled(context: Context, enabled: Boolean): PlaybackBehaviorConfig {
        preferences(context).edit()
            .putBoolean(KEY_LIVE_VISUALIZER, enabled)
            .apply()
        return current(context)
    }

    fun setNonInteractiveLoopConfig(
        context: Context,
        config: NonInteractiveLoopConfig,
    ): PlaybackBehaviorConfig {
        val clean = config.normalized()
        preferences(context).edit()
            .putString(KEY_NON_INTERACTIVE_LOOP_MODE, clean.mode.name)
            .putInt(KEY_NON_INTERACTIVE_LOOP_COUNT, clean.loopCount)
            .putLong(KEY_NON_INTERACTIVE_TARGET_DURATION_MS, clean.targetDurationMs)
            .putLong(KEY_NON_INTERACTIVE_FADEOUT_MS, clean.fadeOutMs)
            .apply()
        return current(context)
    }
}
