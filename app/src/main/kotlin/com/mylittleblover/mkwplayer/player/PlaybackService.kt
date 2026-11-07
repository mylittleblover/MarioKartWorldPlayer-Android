package com.mylittleblover.mkwplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.mylittleblover.mkwplayer.MainActivity
import com.mylittleblover.mkwplayer.R

/**
 * Keeps playback alive when the activity goes away, and publishes media controls.
 *
 * The service holds no audio state of its own: it reads [PlaybackSession] through the
 * holder and forwards commands back. That is deliberate -- duplicating the transport in
 * a service is how a player ends up with two clocks and a notification that disagrees
 * with what you hear.
 *
 * Foreground rules this follows:
 *  - `startForeground` runs immediately in `onStartCommand`, because a service declared
 *    as media playback must show its notification within a few seconds of starting
 *  - the notification is always built from the current session state, never cached
 *  - `stopSelf` is called when the session goes inactive, so the service does not linger
 *    as a permanently foreground process with nothing playing
 */
class PlaybackService : Service() {

    private lateinit var session: PlaybackSession
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var manager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        session = PlaybackHolder.get()
        manager = getSystemService(NotificationManager::class.java)
        createChannel()
        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    session.resume()
                    refresh()
                }

                override fun onPause() {
                    session.pause()
                    refresh()
                }

                override fun onStop() {
                    session.stop()
                    refresh()
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    session.seek(pos)
                    refresh()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground before anything else: the platform requires the notification
        // to exist promptly once a media service is started.
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_PAUSE -> session.pause()
            ACTION_RESUME -> session.resume()
            ACTION_STOP -> {
                session.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        refresh()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        // A destroyed service must not leave its media notification in the shade.
        manager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    /** Rebuilds the notification and the media session state from the session. */
    private fun refresh() {
        val state = session.state.value
        updatePlaybackState(state)
        manager.notify(NOTIFICATION_ID, buildNotification())

        if (!state.active) {
            // REMOVE, not DETACH: detaching leaves the notification behind after the
            // service dies, so a stopped player would keep a media control in the shade
            // with nothing behind it.
            stopForeground(STOP_FOREGROUND_REMOVE)
            manager.cancel(NOTIFICATION_ID)
            stopSelf()
        }
    }

    private fun updatePlaybackState(state: PlaybackSession.State) {
        val session = mediaSession ?: return
        val playbackState = when {
            state.playing -> PlaybackStateCompat.STATE_PLAYING
            state.paused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        val position = if (state.sampleRate > 0) {
            // MediaSession reports milliseconds; the engine only knows frames.
            state.positionFrames * 1000L / state.sampleRate
        } else {
            0L
        }
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMillis(state))
                .build(),
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO,
                )
                .setState(playbackState, position, if (state.playing) 1f else 0f)
                .build(),
        )
    }

    private fun durationMillis(state: PlaybackSession.State): Long =
        if (state.sampleRate > 0) state.totalFrames * 1000L / state.sampleRate else 0L

    private fun buildNotification(): Notification {
        val state = session.state.value
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.title.ifEmpty { "MKW Player" })
            .setContentText(
                when {
                    state.detail != null -> state.detail
                    state.paused -> "Paused"
                    state.playing -> "Playing"
                    else -> "Stopped"
                },
            )
            .setContentIntent(openApp)
            .setOngoing(state.playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state.paused || !state.playing) {
            builder.addAction(
                android.R.drawable.ic_media_play, "Resume",
                command(ACTION_RESUME),
            )
        }
        if (state.playing) {
            builder.addAction(
                android.R.drawable.ic_media_pause, "Pause",
                command(ACTION_PAUSE),
            )
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", command(ACTION_STOP))

        mediaSession?.let { builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(it.sessionToken)) }
        return builder.build()
    }

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows what is playing and offers transport controls."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "mkw.playback"
        const val NOTIFICATION_ID = 1
        const val MEDIA_SESSION_TAG = "mkwplayer"

        const val ACTION_PAUSE = "com.mylittleblover.mkwplayer.PAUSE"
        const val ACTION_RESUME = "com.mylittleblover.mkwplayer.RESUME"
        const val ACTION_STOP = "com.mylittleblover.mkwplayer.STOP"

        /**
         * Starts the foreground service, but only when something is actually playing.
         *
         * The guard is not cosmetic. `startForegroundService` promises the platform that
         * `startForeground` follows promptly; starting it for an idle session and then
         * stopping immediately races that handshake and the system kills the process with
         * `ForegroundServiceDidNotStartInTimeException`. Observed on the emulator: the
         * service was "brought down while still waiting for start foreground".
         *
         * Returns false when nothing was started, so callers can tell the difference.
         */
        fun start(context: Context): Boolean {
            if (!PlaybackHolder.get().state.value.active) return false
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
