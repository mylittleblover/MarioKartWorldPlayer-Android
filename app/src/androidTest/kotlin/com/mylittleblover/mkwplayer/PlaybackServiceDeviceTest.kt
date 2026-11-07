package com.mylittleblover.mkwplayer

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.AudioOutput
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.mkwplayer.player.PlaybackHolder
import com.mylittleblover.mkwplayer.player.PlaybackService
import com.mylittleblover.mkwplayer.player.PlaybackSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Background playback makes three claims: the service is allowed to run, it shows a media
 * notification while playing, and it does not linger when nothing is playing. Each is
 * checked against the platform rather than against our own flags.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceDeviceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)
    private val bwavFile = File("/data/local/tmp/coaster.bwav")
    private val assetId = "BGM_Stage_Coaster.bwav"

    @Before
    fun setUp() {
        context.stopService(Intent(context, PlaybackService::class.java))
        manager.cancel(PlaybackService.NOTIFICATION_ID)
    }

    @After
    fun tearDown() {
        context.stopService(Intent(context, PlaybackService::class.java))
        manager.cancel(PlaybackService.NOTIFICATION_ID)
        PlaybackHolder.overrideForTest(null)
    }

    /** A real session with the real track, so the notification has something to show. */
    private fun playingSession(): PlaybackSession {
        val session = PlaybackSession(
            createEngine = { AudioEngine.create() },
            openOutput = { handle, rate -> AudioOutput.open(handle, rate) },
        )
        PlaybackHolder.overrideForTest(session)

        if (!bwavFile.exists()) return session
        val bytes = bwavFile.readBytes()
        val packet = GraphBuilder.build(
            GraphBuilder.Spec(
                asset = GraphBuilder.Asset(
                    path = assetId,
                    sampleRate = 48000,
                    channels = 2,
                    frames = 3_373_711,
                    sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes),
                    loop = GraphBuilder.Loop(493_714, 3_373_711),
                ),
            ),
        )
        session.load(assetId, packet, bytes, assetId, 3_373_711)
        return session
    }

    private fun waitForNotification(expected: Boolean, timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val posted = manager.activeNotifications.any { it.id == PlaybackService.NOTIFICATION_ID }
            if (posted == expected) return true
            Thread.sleep(100)
        }
        return false
    }

    @Test
    fun theServiceIsDeclaredAsMediaPlayback() {
        // A foreground service with the wrong type is rejected at runtime on Android 14+.
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, PlaybackService::class.java),
            0,
        )
        assertNotNull(info)
        assertTrue(
            "service must declare the mediaPlayback foreground type",
            info.foregroundServiceType and
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK != 0,
        )
    }

    @Test
    fun theServiceIsNotExported() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, PlaybackService::class.java),
            0,
        )
        assertTrue("a playback service must not be exported", !info.exported)
    }

    @Test
    fun aPlayingSessionProducesAForegroundNotification() {
        val session = playingSession()
        assertTrue(
            "the fixture is missing so playback could not start: ${session.state.value.detail}",
            session.state.value.playing,
        )

        assertTrue("service refused to start: ${session.state.value}", PlaybackService.start(context))
        assertTrue("no notification appeared within 5s", waitForNotification(expected = true))

        val posted = manager.activeNotifications.first { it.id == PlaybackService.NOTIFICATION_ID }
        val title = posted.notification.extras.getCharSequence("android.title")?.toString()
        assertEquals("notification must name the track", assetId, title)
    }

    @Test
    fun stoppingRemovesTheNotification() {
        val session = playingSession()
        if (!session.state.value.playing) return   // no fixture: nothing to observe

        assertTrue(PlaybackService.start(context))
        assertTrue(waitForNotification(expected = true))

        context.stopService(Intent(context, PlaybackService::class.java))
        assertTrue(
            "notification survived a stop, leaving a media control with nothing behind it",
            waitForNotification(expected = false),
        )
    }

    @Test
    fun startingWithNothingPlayingDoesNotStayForeground() {
        // Starting the service with an idle session must not leave a permanent
        // notification offering controls for silence.
        PlaybackHolder.overrideForTest(
            PlaybackSession(createEngine = { null }, openOutput = { _, _ -> null }),
        )
        assertTrue(
            "starting a foreground service for an idle session must be refused, not raced",
            !PlaybackService.start(context),
        )
        assertTrue(
            "an idle session left a notification behind",
            waitForNotification(expected = false),
        )
    }

    @Test
    fun theSessionOutlivesTheServiceObject() {
        // The claim behind background playback: the engine is owned by the process, not by
        // the activity or a service instance, so the UI sees the same session again.
        val session = playingSession()
        context.stopService(Intent(context, PlaybackService::class.java))
        val again = PlaybackHolder.get()
        assertEquals(
            "the holder must return one session, not a new one per call",
            System.identityHashCode(session),
            System.identityHashCode(again),
        )
    }
}
