package com.mylittleblover.mkwplayer.pack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mylittleblover.mkwplayer.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground notification host for single-task `.mkwpack` streaming imports (#91-#94).
 *
 * Shows live verification/extraction progress and a pre-commit Cancel action, removes the Cancel
 * action once entering the atomic commit point (`canCancel == false`), and posts a concise
 * summary notification after commit (#92).
 */
class PackImportService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null
    private lateinit var manager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_IMPORT) {
            PackImportCoordinator.requestCancel()
        }

        val current = PackImportCoordinator.state.value
        val notification = buildProgressNotification(current)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, PROGRESS_NOTIFICATION_ID, notification, serviceType)

        if (collectorJob == null) {
            collectorJob = serviceScope.launch {
                PackImportCoordinator.state.collect { state ->
                    when (state.phase) {
                        PackImportPhase.PROBING,
                        PackImportPhase.IMPORTING,
                        PackImportPhase.COMMITTING -> {
                            manager.notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(state))
                        }
                        PackImportPhase.COMPLETED -> {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            manager.cancel(PROGRESS_NOTIFICATION_ID)
                            state.changelog?.let { log ->
                                manager.notify(RESULT_NOTIFICATION_ID, buildSummaryNotification(log))
                            }
                            stopSelf()
                        }
                        PackImportPhase.FAILED,
                        PackImportPhase.CANCELLED,
                        PackImportPhase.IDLE,
                        PackImportPhase.RESUMABLE,
                        PackImportPhase.PAUSED_NEEDS_REPICK -> {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            manager.cancel(PROGRESS_NOTIFICATION_ID)
                            if (state.phase == PackImportPhase.FAILED && !state.errorReason.isNullOrBlank()) {
                                manager.notify(RESULT_NOTIFICATION_ID, buildErrorNotification(state.errorReason))
                            }
                            stopSelf()
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collectorJob?.cancel()
        collectorJob = null
        serviceScope.cancel()
        manager.cancel(PROGRESS_NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun buildProgressNotification(state: PackImportUiState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val displayName = state.displayName.ifBlank { ".mkwpack" }
        val title = when (state.phase) {
            PackImportPhase.COMMITTING -> getString(com.mylittleblover.mkwplayer.R.string.import_notif_applying_fmt, displayName)
            PackImportPhase.PROBING -> getString(com.mylittleblover.mkwplayer.R.string.import_notif_reading_fmt, displayName)
            else -> getString(com.mylittleblover.mkwplayer.R.string.import_notif_importing_fmt, displayName)
        }

        val text = when {
            state.phase == PackImportPhase.COMMITTING -> getString(com.mylittleblover.mkwplayer.R.string.settings_import_committing)
            state.totalMembers > 0 -> "${state.processedMembers}/${state.totalMembers} · ${state.currentMember}"
            else -> getString(com.mylittleblover.mkwplayer.R.string.import_notif_verifying)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.mylittleblover.mkwplayer.R.drawable.ic_stat_jukekart)
            .setColor(0xFFC52D49.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(
                state.totalMembers.coerceAtLeast(1),
                state.processedMembers.coerceIn(0, state.totalMembers.coerceAtLeast(1)),
                state.phase == PackImportPhase.PROBING || state.phase == PackImportPhase.COMMITTING || state.totalMembers == 0,
            )

        if (state.canCancel) {
            val cancelIntent = PendingIntent.getService(
                this,
                11,
                Intent(this, PackImportService::class.java).setAction(ACTION_CANCEL_IMPORT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(
                com.mylittleblover.mkwplayer.R.drawable.ic_notif_stop,
                getString(com.mylittleblover.mkwplayer.R.string.discovery_cancel),
                cancelIntent,
            )
        }
        return builder.build()
    }

    private fun buildSummaryNotification(log: PackImportChangelog): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            12,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val details = buildString {
            append(
                getString(
                    com.mylittleblover.mkwplayer.R.string.import_notif_done_body,
                    log.packName,
                    log.packVersion,
                    log.playableLibraryTracks,
                    log.totalLibraryTracks,
                ),
            )
            if (log.poolStatus == PoolUpdateStatus.REPLACED && log.poolDataVersion != null) {
                append(getString(com.mylittleblover.mkwplayer.R.string.import_notif_done_roads_suffix, log.poolDataVersion))
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.mylittleblover.mkwplayer.R.drawable.ic_stat_jukekart)
            .setColor(0xFFC52D49.toInt())
            .setContentTitle(getString(com.mylittleblover.mkwplayer.R.string.import_notif_done_title))
            .setContentText(details)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildErrorNotification(reason: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            13,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.mylittleblover.mkwplayer.R.drawable.ic_stat_jukekart)
            .setColor(0xFFC52D49.toInt())
            .setContentTitle(getString(com.mylittleblover.mkwplayer.R.string.import_notif_stopped_title))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(com.mylittleblover.mkwplayer.R.string.notif_channel_import_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(com.mylittleblover.mkwplayer.R.string.notif_channel_import_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "mkw.import"
        private const val PROGRESS_NOTIFICATION_ID = 2
        private const val RESULT_NOTIFICATION_ID = 3
        private const val ACTION_CANCEL_IMPORT = "com.mylittleblover.mkwplayer.ACTION_CANCEL_IMPORT"

        fun start(context: Context) {
            val intent = Intent(context, PackImportService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
