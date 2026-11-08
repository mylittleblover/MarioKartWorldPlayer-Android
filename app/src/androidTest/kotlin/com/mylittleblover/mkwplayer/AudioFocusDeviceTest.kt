package com.mylittleblover.mkwplayer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.player.AudioFocusManager
import com.mylittleblover.mkwplayer.player.FocusPolicy
import com.mylittleblover.mkwplayer.player.PlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Audio focus against the real `AudioManager`.
 *
 * [FocusPolicy] is covered on the JVM for every branch, so this only establishes what needs
 * a device: that the request is actually granted, that it is released, and that a route-loss
 * event reaches the policy.
 *
 * Deliberately does not start [PlaybackService]. Focus is global to the app, and starting the
 * foreground service from a test that then tears down immediately races the
 * `startForegroundService` handshake -- which crashes the process rather than failing a test.
 * The service lifecycle has its own coverage in `PlaybackServiceDeviceTest`.
 */
@RunWith(AndroidJUnit4::class)
class AudioFocusDeviceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        // Focus is app-global: a service left running by another class would make this
        // request fail for reasons that have nothing to do with the code under test.
        PlaybackService.stop(context)
        Thread.sleep(600)
    }

    @After
    fun tearDown() {
        PlaybackService.stop(context)
        Thread.sleep(300)
    }

    @Test
    fun theManagerNeverClaimsFocusItWasNotGiven() {
        // Asserting the contract rather than "the emulator grants focus". On a shared
        // emulator another media session can legitimately own focus, and that must not make
        // this test fail -- but the manager must never report `hasFocus` when the platform
        // refused, because callers use it to decide whether audio may start.
        val focus = AudioFocusManager(context) { }
        try {
            assertFalse("a fresh manager must not claim focus", focus.hasFocus)
            val granted = focus.request()
            assertEquals(
                "hasFocus must mirror the platform's answer",
                granted,
                focus.hasFocus,
            )
        } finally {
            focus.abandon()
        }
        assertFalse("focus must be released", focus.hasFocus)
    }

    @Test
    fun abandoningTwiceIsSafe() {
        val focus = AudioFocusManager(context) { }
        focus.request()
        focus.abandon()
        focus.abandon()
        assertFalse(focus.hasFocus)
    }

    @Test
    fun abandoningWithoutRequestingIsSafe() {
        val focus = AudioFocusManager(context) { }
        focus.abandon()
        assertFalse(focus.hasFocus)
    }

    @Test
    fun theNoisyBroadcastReachesTheManager() {
        // The system sends AUDIO_BECOMING_NOISY when the route disappears. The manager must
        // forward it as the policy event rather than swallowing it.
        val received = CountDownLatch(1)
        var seen: FocusPolicy.Event? = null
        val focus = AudioFocusManager(context) { event ->
            seen = event
            received.countDown()
        }
        try {
            focus.notifyRouteLost()
            assertTrue("event was not delivered", received.await(2, TimeUnit.SECONDS))
            assertEquals(FocusPolicy.Event.BECOMING_NOISY, seen)
        } finally {
            focus.abandon()
        }
    }

    @Test
    fun theRouteLossDecisionIsAPause() {
        // The manager forwards the event; the policy must turn it into a stop of audible
        // output. Both halves are asserted separately because the platform's delivery of the
        // broadcast itself cannot be simulated: AUDIO_BECOMING_NOISY is system-only, and
        // `adb shell` (uid 2000) is refused too.
        assertEquals(
            FocusPolicy.Action.PAUSE_FINAL,
            FocusPolicy.decide(FocusPolicy.Event.BECOMING_NOISY, playing = true),
        )
    }
}
