package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.TrackOpener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end through [TrackOpener], the single join of header reading, graph
 * building and playback.
 *
 * Everything the UI will eventually call is exercised here: one function takes a
 * file and either returns a running engine or a displayable reason.
 */
@RunWith(AndroidJUnit4::class)
class TrackOpenerDeviceTest {

    private val bwavFile = File("/data/local/tmp/coaster.bwav")
    private val assetId = "BGM_Stage_Coaster.bwav"

    @Test
    fun opensAndPlaysTheRealTrack() {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val bytes = bwavFile.readBytes()

        val result = TrackOpener.open(assetId, bytes)
        assertTrue("opener refused: $result", result is TrackOpener.Result.Playing)
        result as TrackOpener.Result.Playing

        // The header values the opener read must match the real file.
        assertEquals(48000, result.sampleRate)
        assertEquals(3_373_711, result.frames)
        assertEquals(493_714, result.loopStart)
        assertEquals(3_373_711, result.loopEnd)

        try {
            val block = result.engine.render(frames = 512)
            assertTrue("render returned null", block != null)
            var peak = 0f
            var nonZero = 0
            for (sample in block!!) {
                val magnitude = kotlin.math.abs(sample)
                if (magnitude > peak) peak = magnitude
                if (sample != 0f) nonZero++
            }
            assertTrue(
                "opened track rendered silence: peak=$peak nonzero=$nonZero",
                nonZero > 0,
            )
            assertTrue("implausible peak $peak", peak in 1e-4f..4f)
        } finally {
            result.engine.close()
        }
    }

    @Test
    fun refusesWithoutAllocatingAnEngine() {
        // A refusal must not leave an engine behind. Signalled by the type: there
        // is no engine field on Refused, so nothing can leak.
        val result = TrackOpener.open("not-a-bwav.wav", ByteArray(64))
        assertTrue(result is TrackOpener.Result.Refused)
        assertTrue((result as TrackOpener.Result.Refused).reason.isNotBlank())
    }

    @Test
    fun closingTwiceIsSafe() {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val result = TrackOpener.open(assetId, bwavFile.readBytes())
        assertTrue(result is TrackOpener.Result.Playing)
        val playing = result as TrackOpener.Result.Playing

        playing.engine.close()
        playing.engine.close()   // must not double-free the native handle

        // After close the wrapper refuses to call into native.
        assertEquals(AudioEngineClosed, playing.engine.sampleRate)
    }

    private companion object {
        /** AudioEngine reports rate 0 once closed; asserted via a local constant. */
        const val AudioEngineClosed = 0
    }
}
