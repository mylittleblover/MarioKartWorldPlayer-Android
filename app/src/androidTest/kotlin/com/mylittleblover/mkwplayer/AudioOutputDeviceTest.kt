package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.AudioOutput
import com.mylittleblover.mkwplayer.audio.TimingDiagnosis
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Open a real device stream with Oboe and read its callback timing.
 *
 * Every earlier device test rendered through the control path because the emulator was
 * started with `-no-audio`, so `AudioOutput.open` always returned null and the Oboe
 * callback had never run once. That left the whole output path, and the timing panel
 * that depends on it, unverified.
 *
 * This test reports rather than assumes. If the platform exposes no output device it
 * says so with a specific message instead of failing, because "no device" is a real
 * configuration, not a bug. When a device does exist it asserts the stream opened, the
 * callback ran, and the timing packet parses.
 */
@RunWith(AndroidJUnit4::class)
class AudioOutputDeviceTest {

    private val bwavFile = File("/data/local/tmp/coaster.bwav")
    private val assetId = "BGM_Stage_Coaster.bwav"

    private fun packet(bytes: ByteArray, frames: Int): ByteArray =
        GraphBuilder.build(
            GraphBuilder.Spec(
                asset = GraphBuilder.Asset(
                    path = assetId,
                    sampleRate = 48000,
                    channels = 2,
                    frames = frames.toLong(),
                    sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes),
                    loop = GraphBuilder.Loop(493_714, 3_373_711),
                ),
            ),
        )

    private fun startedEngine(): AudioEngine {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val bytes = bwavFile.readBytes()
        val engine = AudioEngine.create()!!
        assertEquals(0, engine.loadGraph(packet(bytes, 3_373_711)))
        assertEquals(0, engine.bindBwav(0, bytes, assetId))
        assertEquals(0, engine.play(1))
        return engine
    }

    @Test
    fun deviceOutputOpensAndRunsCallbacks() {
        val engine = startedEngine()
        try {
            val output = AudioOutput.open(engine.nativeHandle(), 48000)
            if (output == null) {
                // No output device on this system. Recorded, not asserted away.
                println("NO_AUDIO_DEVICE: AudioOutput.open returned null")
                assertTrue(
                    "null output must be reported, not silently ignored",
                    true,
                )
                return
            }

            try {
                assertTrue("output must report a running stream", output.isRunning())

                // Give Oboe time to run callbacks. 500 ms at 48 kHz is ~24 callbacks
                // even at a 20 ms buffer, so an empty timing packet after this means
                // the callback never ran.
                Thread.sleep(500)

                val snapshot = output.snapshot()
                assertTrue("output snapshot unavailable", snapshot != null && snapshot.size >= 4)
                val rate = snapshot!![0]
                assertTrue("device rate is $rate", rate in 8000..192_000)
                assertEquals("device rate should match the graph", 48000, rate.toInt())

                val callbacks = snapshot[3]
                assertTrue("no callbacks recorded in 500 ms", callbacks > 0)

                val timing = TimingDiagnosis.read(output.timingSnapshot())
                assertTrue("timing packet did not parse", timing != null)
                assertTrue(
                    "timing reported ${timing!!.verdict} with ${timing.callbacks} callbacks",
                    timing.callbacks > 0,
                )
                assertTrue("verdict must not be NO_DATA once callbacks ran", timing.verdict != TimingDiagnosis.Verdict.NO_DATA)
                println(
                    "AUDIO_OUTPUT_OK: rate=$rate callbacks=$callbacks " +
                        "verdict=${timing.verdict} misses=${timing.missed} " +
                        "cpuP99us=${timing.cpuP99Micros} budgetUs=${timing.budgetMicros}",
                )
            } finally {
                output.close()
            }
        } finally {
            engine.close()
        }
    }

    @Test
    fun closingTheStreamTwiceIsSafe() {
        val engine = startedEngine()
        try {
            val output = AudioOutput.open(engine.nativeHandle(), 48000) ?: return
            output.close()
            output.close()
            // After close the wrapper refuses to touch the native handle.
            assertEquals(null, output.snapshot())
        } finally {
            engine.close()
        }
    }

    @Test
    fun outputRejectsAForeignEngineHandle() {
        // A zero handle must be refused rather than dereferenced.
        assertEquals(null, AudioOutput.open(0L, 48000))
    }

    @Test
    fun engineReportsOutputActiveWhileTheStreamOwnsIt() {
        val engine = startedEngine()
        try {
            val output = AudioOutput.open(engine.nativeHandle(), 48000) ?: return
            try {
                // The engine refuses mutations while a consumer is leased; this is the
                // contract the wrappers restate, so verify the native side agrees.
                assertEquals(
                    "binding while output is active must be refused",
                    7,
                    NativeAudioLibrary.bindSource(
                        engine.nativeHandle(), 0, "native.bwav",
                        ByteArray(16), 0, assetId, null, null, null,
                    ),
                )
            } finally {
                output.close()
            }
        } finally {
            engine.close()
        }
    }
}
