package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The UI must never turn a failure into something that looks like success.
 *
 * These tests exist because the engine has two ways to look fine while doing
 * nothing: Play with the wrong target returns Ok, and an unknown status code
 * formats as a number nobody reads.
 */
class AudioStatusTextTest {

    @Test
    fun okIsNamedAndHasNoHint() {
        assertEquals("Ok", AudioStatusText.engine(0))
        assertNull(AudioStatusText.engineHint(0))
    }

    @Test
    fun everyEngineCodeInRangeIsNamed() {
        // EngineStatus is a contiguous 0..11 enum; a gap would mean a code the UI
        // would print as UNKNOWN.
        for (code in 0..11) {
            val name = AudioStatusText.engine(code)
            assertFalse(name.startsWith("UNKNOWN"), "engine code $code is unnamed")
        }
    }

    @Test
    fun unknownEngineCodeKeepsItsNumber() {
        val text = AudioStatusText.engine(99)
        assertEquals("UNKNOWN(99)", text)
        assertTrue("99" in text)
    }

    @Test
    fun unknownEngineCodeIsNotTreatedAsOk() {
        assertFalse(AudioStatusText.engineIsOk(99))
    }

    @Test
    fun okIsTheOnlySuccess() {
        for (code in 1..11) {
            assertFalse(AudioStatusText.engineIsOk(code), "code $code must not read as success")
        }
    }

    @Test
    fun invalidRequestExplainsItself() {
        // This is what a kind=2 BWAV produces once the codec check fires.
        val hint = AudioStatusText.engineHint(1)
        assertNotNull(hint)
        assertTrue(hint.isNotBlank())
    }

    @Test
    fun everySourceCodeInRangeIsNamed() {
        for (code in 0..14) {
            val name = AudioStatusText.source(code)
            assertFalse(name.startsWith("UNKNOWN"), "source code $code is unnamed")
        }
    }

    @Test
    fun unsupportedCodecSaysSoInsteadOfLookingCorrupt() {
        // kind=2 BWAV returns UnsupportedCodec(4). Telling the user the file is
        // corrupt would be wrong; it is a known, documented limitation.
        assertEquals("UnsupportedCodec", AudioStatusText.source(4))
        val hint = AudioStatusText.sourceHint(4)
        assertNotNull(hint)
        assertFalse(
            hint.contains("malformed", ignoreCase = true),
            "an unsupported codec must not be described as a malformed file: $hint",
        )
    }

    @Test
    fun malformedIsDistinguishedFromUnsupported() {
        val malformed = AudioStatusText.sourceHint(2)
        val unsupported = AudioStatusText.sourceHint(4)
        assertNotNull(malformed)
        assertNotNull(unsupported)
        assertTrue(malformed != unsupported)
    }

    @Test
    fun unknownSourceCodeKeepsItsNumber() {
        assertEquals("UNKNOWN(42)", AudioStatusText.source(42))
    }
}
