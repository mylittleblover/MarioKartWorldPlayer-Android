package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonInteractiveLoopPolicyTest {

    @Test
    fun defaultConfigIsOneLoopPlusTenSecondFadeOut() {
        val sr = 48_000
        val loopStart = 5L * sr     // 5s intro
        val loopEnd = 60L * sr      // 60s base (55s loop body)
        val timing = NonInteractiveLoopPolicy.computeEffectiveFrames(
            baseFrames = loopEnd,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = true,
            sampleRate = sr,
            config = NonInteractiveLoopConfig.Default,
        )
        assertTrue(timing.usesLoopFade)
        // 60s + 1 * 55s + 10s fade = 125s
        assertEquals(125L * sr, timing.totalFrames)
        assertEquals(10L * sr, timing.fadeOutFrames)
        assertEquals(115L * sr, timing.fadeStartFrame)
        assertEquals(125_000L, timing.durationMs(sr))
    }

    @Test
    fun shortLoopTrackLikeSuperStarHandlesFadeOutLongerThanLoopBody() {
        // super_star_bgm: frames = 320000 (~6.67s), loopStart = 35556 (~0.74s), loopEnd = 320000, loopLen = 284444 (~5.93s)
        val sr = 48_000
        val frames = 320_000L
        val loopStart = 35_556L
        val loopEnd = 320_000L
        val loopLen = loopEnd - loopStart
        val timing = NonInteractiveLoopPolicy.computeEffectiveFrames(
            baseFrames = frames,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = true,
            sampleRate = sr,
            config = NonInteractiveLoopConfig.byLoopCount(1),
        )
        assertTrue(timing.usesLoopFade)
        assertEquals(loopEnd + loopLen + 10L * sr, timing.totalFrames)
        assertEquals(10L * sr, timing.fadeOutFrames)
        assertEquals(loopEnd + loopLen, timing.fadeStartFrame)
    }

    @Test
    fun customLoopCountZeroAndThreeComputeExactFrames() {
        val sr = 48_000
        val loopStart = 4L * sr
        val loopEnd = 34L * sr // 30s loop body
        val zeroLoops = NonInteractiveLoopPolicy.computeEffectiveFrames(
            baseFrames = loopEnd,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = true,
            sampleRate = sr,
            config = NonInteractiveLoopConfig.byLoopCount(0),
        )
        assertEquals(44L * sr, zeroLoops.totalFrames) // 34s + 10s fade
        assertEquals(34L * sr, zeroLoops.fadeStartFrame)

        val threeLoops = NonInteractiveLoopPolicy.computeEffectiveFrames(
            baseFrames = loopEnd,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = true,
            sampleRate = sr,
            config = NonInteractiveLoopConfig.byLoopCount(3),
        )
        assertEquals((34L + 3L * 30L + 10L) * sr, threeLoops.totalFrames) // 134s
        assertEquals(124L * sr, threeLoops.fadeStartFrame)
    }

    @Test
    fun targetDurationModeAlwaysEndsWithTenSecondFadeOrFullTrackWhenShorter() {
        val sr = 48_000
        val loopStart = 2L * sr
        val loopEnd = 40L * sr

        val fiveMin = NonInteractiveLoopPolicy.computeEffectiveFrames(
            baseFrames = loopEnd,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = true,
            sampleRate = sr,
            config = NonInteractiveLoopConfig.byTargetDurationMs(300_000L),
        )
        assertTrue(fiveMin.usesLoopFade)
        assertEquals(300L * sr, fiveMin.totalFrames)
        assertEquals(10L * sr, fiveMin.fadeOutFrames)
        assertEquals(290L * sr, fiveMin.fadeStartFrame)

        // Shorter than 10s: fade clamps cleanly to full target duration.
        val sixSec = NonInteractiveLoopPolicy.computeEffectiveFrames(
            baseFrames = loopEnd,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = true,
            sampleRate = sr,
            config = NonInteractiveLoopConfig.byTargetDurationMs(6_000L),
        )
        assertTrue(sixSec.usesLoopFade)
        assertEquals(6L * sr, sixSec.totalFrames)
        assertEquals(6L * sr, sixSec.fadeOutFrames)
        assertEquals(0L, sixSec.fadeStartFrame)
    }

    @Test
    fun nonLoopingTracksRemainCompletelyUnchanged() {
        val sr = 48_000
        val base = 90L * sr
        val timing = NonInteractiveLoopPolicy.computeEffectiveFrames(
            baseFrames = base,
            loopStart = null,
            loopEnd = null,
            hasLoop = false,
            sampleRate = sr,
            config = NonInteractiveLoopConfig.byLoopCount(5),
        )
        assertFalse(timing.usesLoopFade)
        assertEquals(base, timing.totalFrames)
        assertEquals(0L, timing.fadeOutFrames)
        assertEquals(90_000L, NonInteractiveLoopPolicy.computeEffectiveDurationMs(90_000L, base, null, null, false, sr))
    }
}
