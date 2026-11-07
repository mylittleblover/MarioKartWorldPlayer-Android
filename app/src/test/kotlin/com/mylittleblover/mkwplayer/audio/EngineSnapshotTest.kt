package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Snapshot reading is where a wrong offset turns into a lying UI, so the offsets
 * and the refusals are both pinned.
 */
class EngineSnapshotTest {

    private fun packet(
        version: Long = EngineSnapshot.WIRE_VERSION,
        frame: Long = 0,
        status: Long = EngineSnapshot.STOPPED,
        voices: Long = 0,
        lastResult: Long = 0,
    ): LongArray {
        val values = LongArray(65)
        values[0] = version
        values[1] = frame
        values[2] = status
        values[3] = voices
        values[7] = lastResult
        return values
    }

    @Test
    fun readsTheTransportFields() {
        val fields = EngineSnapshot.read(packet(frame = 48000, status = EngineSnapshot.PLAYING, voices = 2))!!
        assertEquals(48000L, fields.transportFrame)
        assertTrue(fields.isPlaying)
        assertFalse(fields.isStopped)
        assertEquals(2L, fields.activeVoices)
    }

    @Test
    fun distinguishesStoppedAndPaused() {
        assertTrue(EngineSnapshot.read(packet(status = EngineSnapshot.STOPPED))!!.isStopped)
        assertTrue(EngineSnapshot.read(packet(status = EngineSnapshot.PAUSED))!!.isPaused)
    }

    @Test
    fun absentPacketIsNull() {
        assertNull(EngineSnapshot.read(null))
    }

    @Test
    fun shortPacketIsNull() {
        assertNull(EngineSnapshot.read(LongArray(4)))
    }

    @Test
    fun wrongWireVersionIsNull() {
        // A reordered native packet would otherwise be read at the wrong offsets and
        // report playback that is not happening.
        assertNull(EngineSnapshot.read(packet(version = 7)))
        assertNull(EngineSnapshot.read(packet(version = 9)))
    }

    @Test
    fun framesConvertWithTheGivenRate() {
        assertEquals(1000L, EngineSnapshot.framesToMillis(48000, 48000))
        assertEquals(500L, EngineSnapshot.framesToMillis(24000, 48000))
        assertEquals(1000L, EngineSnapshot.framesToMillis(44100, 44100))
    }

    @Test
    fun zeroRateDoesNotDivideByZero() {
        assertEquals(0L, EngineSnapshot.framesToMillis(48000, 0))
        assertEquals("--:--", EngineSnapshot.formatPosition(48000, 0))
    }

    @Test
    fun positionFormatsAsMinutesAndSeconds() {
        assertEquals("0:00", EngineSnapshot.formatPosition(0, 48000))
        assertEquals("0:01", EngineSnapshot.formatPosition(48000, 48000))
        assertEquals("1:00", EngineSnapshot.formatPosition(48000 * 60, 48000))
        assertEquals("1:10", EngineSnapshot.formatPosition(48000 * 70, 48000))
    }

    @Test
    fun negativeFramesFormatAsUnknown() {
        assertEquals("--:--", EngineSnapshot.formatPosition(-1, 48000))
    }

    @Test
    fun wireVersionMatchesTheBridge() {
        // NativeAudioBridge.cpp writes 8 as the first field; widening this without
        // the bridge would silently disable every snapshot read.
        assertEquals(8L, EngineSnapshot.WIRE_VERSION)
    }

    @Test
    fun statusConstantsMatchTheNativeEnum() {
        assertEquals(0L, EngineSnapshot.STOPPED)
        assertEquals(1L, EngineSnapshot.PLAYING)
        assertEquals(2L, EngineSnapshot.PAUSED)
    }
}
