package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Seeking is where integer arithmetic turns a small slip into an absurd playhead, so
 * every conversion is pinned. The UI only ever holds a 0..1 float, and a drag gesture
 * can emit anything including NaN, so clamping is behaviour rather than tolerance.
 */
class SeekTargetTest {

    private val total = 3_373_711L   // the Coaster track length

    @Test
    fun startAndEndMapToTheFirstAndLastFrame() {
        assertEquals(0L, SeekTarget.fractionToFrame(0f, total))
        assertEquals(total, SeekTarget.fractionToFrame(1f, total))
    }

    @Test
    fun halfwayMapsToTheMiddle() {
        assertEquals(total / 2, SeekTarget.fractionToFrame(0.5f, total))
    }

    @Test
    fun negativeFractionClampsToZero() {
        assertEquals(0L, SeekTarget.fractionToFrame(-5f, total))
    }

    @Test
    fun fractionAboveOneClampsToTheEnd() {
        assertEquals(total, SeekTarget.fractionToFrame(42f, total))
    }

    @Test
    fun nanClampsToZeroRatherThanProducingGarbage() {
        assertEquals(0L, SeekTarget.fractionToFrame(Float.NaN, total))
    }

    @Test
    fun infiniteFractionClampsToTheEnd() {
        assertEquals(total, SeekTarget.fractionToFrame(Float.POSITIVE_INFINITY, total))
        assertEquals(0L, SeekTarget.fractionToFrame(Float.NEGATIVE_INFINITY, total))
    }

    @Test
    fun unknownDurationYieldsZero() {
        assertEquals(0L, SeekTarget.fractionToFrame(0.5f, 0))
        assertEquals(0L, SeekTarget.fractionToFrame(0.5f, -1))
    }

    @Test
    fun frameToFractionRoundTrips() {
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val frame = SeekTarget.fractionToFrame(fraction, total)
            assertEquals(fraction, SeekTarget.frameToFraction(frame, total), 1e-6f)
        }
    }

    @Test
    fun frameToFractionIsBounded() {
        assertEquals(0f, SeekTarget.frameToFraction(0, total))
        assertEquals(1f, SeekTarget.frameToFraction(total, total))
        assertEquals(1f, SeekTarget.frameToFraction(total * 2, total))
        assertEquals(0f, SeekTarget.frameToFraction(-100, total))
    }

    @Test
    fun frameToFractionNeedsNoDuration() {
        assertEquals(0f, SeekTarget.frameToFraction(100, 0))
    }

    @Test
    fun addressableCoversTheWholeTrackInclusive() {
        assertTrue(SeekTarget.isAddressable(0, total))
        assertTrue(SeekTarget.isAddressable(total, total))
        assertTrue(SeekTarget.isAddressable(total / 2, total))
    }

    @Test
    fun addressableRejectsOutsideTheTrack() {
        assertFalse(SeekTarget.isAddressable(-1, total))
        assertFalse(SeekTarget.isAddressable(total + 1, total))
        assertFalse(SeekTarget.isAddressable(0, 0))
    }

    @Test
    fun secondsConvertWithTheGivenRate() {
        assertEquals(48_000L, SeekTarget.secondsToFrame(1.0, 48_000, total))
        assertEquals(96_000L, SeekTarget.secondsToFrame(2.0, 48_000, total))
    }

    @Test
    fun secondsClampToTheTrack() {
        assertEquals(total, SeekTarget.secondsToFrame(9999.0, 48_000, total))
        assertEquals(0L, SeekTarget.secondsToFrame(-10.0, 48_000, total))
    }

    @Test
    fun secondsWithBadRateOrDurationYieldZero() {
        assertEquals(0L, SeekTarget.secondsToFrame(5.0, 0, total))
        assertEquals(0L, SeekTarget.secondsToFrame(5.0, 48_000, 0))
        assertEquals(0L, SeekTarget.secondsToFrame(Double.NaN, 48_000, total))
    }

    @Test
    fun aRealisticDragLandsInsideTheTrack() {
        // Sweeping the bar must never produce a frame outside the track.
        var fraction = -0.5f
        while (fraction <= 1.5f) {
            val frame = SeekTarget.fractionToFrame(fraction, total)
            assertTrue(
                frame in 0..total,
                "fraction $fraction produced frame $frame",
            )
            assertTrue(SeekTarget.isAddressable(frame, total))
            fraction += 0.01f
        }
    }
}
