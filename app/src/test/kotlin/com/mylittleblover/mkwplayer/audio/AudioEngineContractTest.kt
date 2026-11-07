package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the numeric constants that cross the JNI boundary.
 *
 * These values are not arbitrary; each one is read from the native side and a
 * wrong number is rejected at runtime with a generic status, far from the edit
 * that broke it. Pinning them here turns that into a build failure.
 *
 * Sources, so the numbers are checkable rather than trusted:
 *  - command kinds: engine/native/api/EngineTypes.hpp
 *  - status ordinals: same enum
 *  - provider id:  engine/native/api/AudioSourceApi.cpp probeAudioSource
 */
class AudioEngineContractTest {

    @Test
    fun playTargetForLinearIsMinusOne() {
        // startSegment: `if(target!=-1 || !linear_) return InvalidRequest`.
        assertEquals(-1, AudioEngine.PLAY_LINEAR)
    }

    @Test
    fun commandKindsMatchTheNativeEnum() {
        assertEquals(0, AudioEngine.COMMAND_PLAY)
        assertEquals(1, AudioEngine.COMMAND_STOP)
        assertEquals(2, AudioEngine.COMMAND_PAUSE)
        assertEquals(3, AudioEngine.COMMAND_RESUME)
    }

    @Test
    fun okStatusIsZero() {
        assertEquals(0, AudioEngine.STATUS_OK)
    }

    @Test
    fun preparedIsZeroSoStreamedIsOne() {
        assertEquals(0, AudioEngine.PREPARATION_PREPARED)
    }

    @Test
    fun providerIdIsTheOneTheProbeReturns() {
        assertEquals("native.bwav", AudioEngine.PROVIDER_BWAV)
    }

    @Test
    fun closedSentinelCannotCollideWithARealStatus() {
        // EngineStatus values run 0..11; -1 is outside that range, so a caller
        // that ignores the closed check still cannot read it as success.
        assertEquals(-1, AudioEngine.CLOSED)
        assertTrue(AudioEngine.CLOSED < 0)
    }

    @Test
    fun commandKindsAreDistinct() {
        val kinds = listOf(
            AudioEngine.COMMAND_PLAY,
            AudioEngine.COMMAND_STOP,
            AudioEngine.COMMAND_PAUSE,
            AudioEngine.COMMAND_RESUME,
        )
        assertEquals(kinds.size, kinds.toSet().size)
    }
}
