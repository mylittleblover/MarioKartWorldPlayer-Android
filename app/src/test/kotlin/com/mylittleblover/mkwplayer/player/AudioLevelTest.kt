package com.mylittleblover.mkwplayer.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level measurements are what turn "the buffer was not all zeros" into a repeatable claim, so
 * the arithmetic is pinned rather than trusted, and the thresholds are stated.
 */
class AudioLevelTest {

    private fun block(vararg values: Float) = values

    @Test
    fun silenceIsDetected() {
        assertTrue(AudioLevel.isSilent(floatArrayOf(0f, 0f, 0f, 0f)))
    }

    @Test
    fun oneNonZeroSampleIsNotSilence() {
        // A single stray sample means audio is flowing; calling that silent would hide a
        // transition that half-worked.
        assertFalse(AudioLevel.isSilent(floatArrayOf(0f, 1e-9f, 0f, 0f)))
    }

    @Test
    fun rmsOfAConstantSignalIsTheConstant() {
        assertEquals(0.5, AudioLevel.rms(floatArrayOf(0.5f, -0.5f, 0.5f, -0.5f)), 1e-9)
    }

    @Test
    fun rmsOfSilenceIsZero() {
        assertEquals(0.0, AudioLevel.rms(floatArrayOf(0f, 0f)))
    }

    @Test
    fun rmsOfAnEmptyBlockIsZeroNotNaN() {
        assertEquals(0.0, AudioLevel.rms(floatArrayOf()))
    }

    @Test
    fun peakUsesMagnitude() {
        assertEquals(0.75, AudioLevel.peak(block(0.1f, -0.75f, 0.2f)), 1e-6)
    }

    @Test
    fun peakOfAnEmptyBlockIsZero() {
        assertEquals(0.0, AudioLevel.peak(floatArrayOf()))
    }

    @Test
    fun gainRatioIsOneForAnUnchangedLevel() {
        assertEquals(1.0, AudioLevel.gainRatio(0.2, 0.2), 1e-9)
    }

    @Test
    fun gainRatioOfSilenceBeforeIsInfinite() {
        assertEquals(Double.POSITIVE_INFINITY, AudioLevel.gainRatio(0.0, 0.5))
    }

    @Test
    fun droppingToSilenceIsNotHeld() {
        assertFalse(AudioLevel.levelHeld(before = 0.2, after = 0.0))
    }

    @Test
    fun aLevelDropWithinTheFloorIsHeld() {
        // A 12 dB fade is a legitimate transition; it must not be reported as a failure.
        assertTrue(AudioLevel.levelHeld(before = 0.2, after = 0.2 * 0.25))
    }

    @Test
    fun aFullCollapseBelowTheFloorIsNotHeld() {
        assertFalse(AudioLevel.levelHeld(before = 0.2, after = 0.2 * 0.01))
    }

    @Test
    fun anExplosiveIncreaseIsNotHeld() {
        assertFalse(AudioLevel.levelHeld(before = 0.001, after = 1.0))
    }

    @Test
    fun aRiseFromSilenceIsHeld() {
        // Silence before means the previous segment had nothing; audio afterwards is fine.
        assertTrue(AudioLevel.levelHeld(before = 0.0, after = 0.3))
    }

    @Test
    fun aFailedRenderIsReportedWithItsIndex() {
        val result = AudioLevel.RenderResult(
            blocks = 0,
            silentBlocks = 0,
            peak = 0.0,
            meanRms = 0.0,
            failedAt = 3,
        )
        assertTrue(result.failed)
        assertEquals(3, result.failedAt)
        assertFalse(result.anyAudio)
    }

    @Test
    fun anAllSilentRenderIsReported() {
        val result = AudioLevel.RenderResult(
            blocks = 8,
            silentBlocks = 8,
            peak = 0.0,
            meanRms = 0.0,
            failedAt = -1,
        )
        assertTrue(result.allSilent)
        assertFalse(result.anyAudio)
        assertFalse(result.failed)
    }

    @Test
    fun aPartiallySilentRenderStillCountsAsAudio() {
        val result = AudioLevel.RenderResult(
            blocks = 8,
            silentBlocks = 3,
            peak = 0.1,
            meanRms = 0.02,
            failedAt = -1,
        )
        assertFalse(result.allSilent)
        assertTrue(result.anyAudio)
    }
}
