package com.mylittleblover.mkwplayer.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Asks the system for audio focus and translates its answers into [FocusPolicy] actions.
 *
 * This class is the Android shell; every decision is in [FocusPolicy] so the rules can be
 * tested without a device. What lives here is only the part that cannot be: constructing
 * the request, registering the listener, and reporting the route change.
 *
 * Focus is requested when playback starts and abandoned when it stops. Holding it while
 * paused would keep ducking every other app for no reason.
 */
internal class AudioFocusManager(
    context: Context,
    private val onEvent: (FocusPolicy.Event) -> Unit,
) {
    private val manager = context.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val event = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusPolicy.Event.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> FocusPolicy.Event.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusPolicy.Event.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                FocusPolicy.Event.LOSS_TRANSIENT_CAN_DUCK
            else -> null
        }
        event?.let(onEvent)
    }

    /**
     * The focus request.
     *
     * Built unconditionally because `minSdk` is 26 and `AudioFocusRequest` arrived in 26:
     * the deprecated string-based overload is unreachable on any device this app installs
     * on, so there is no second path to keep correct.
     */
    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            // Ducking is not implemented, so do not ask the platform to prefer it.
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener)
            .build()

    private var held = false

    /** True when this app currently owns focus. */
    val hasFocus: Boolean get() = held

    /**
     * Requests focus.
     *
     * Returns false when the system refuses, which means another app owns audio and this
     * one must not start. Callers must treat false as "do not play", not as a warning.
     */
    fun request(): Boolean {
        val result = manager.requestAudioFocus(request)
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    /** Releases focus. Safe to call when focus was never held. */
    fun abandon() {
        if (!held) return
        held = false
        manager.abandonAudioFocusRequest(request)
    }

    /**
     * Reports that the output route went away.
     *
     * Called by whoever observes the broadcast; kept here so the service does not need to
     * know how the decision is made.
     */
    fun notifyRouteLost() {
        onEvent(FocusPolicy.Event.BECOMING_NOISY)
    }

    companion object {
        /** The broadcast action for a headphone or Bluetooth disconnect. */
        const val ACTION_AUDIO_BECOMING_NOISY = AudioManager.ACTION_AUDIO_BECOMING_NOISY
    }
}
