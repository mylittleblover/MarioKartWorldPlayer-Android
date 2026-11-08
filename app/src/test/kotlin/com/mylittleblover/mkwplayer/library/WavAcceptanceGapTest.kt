package com.mylittleblover.mkwplayer.library

import com.mylittleblover.mkwplayer.audio.BwavHeader
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The picker advertises `.wav` and the decoder only accepts BWAV.
 *
 * Those two facts sit in different files and nothing connected them: the picker accepts a `.wav`,
 * the pipeline hands the bytes to [BwavHeader], which rejects anything that is not a BWAV
 * container. So a user who picks a `.wav` -- a file the button explicitly invites them to choose --
 * reaches "not a BWAV container".
 *
 * This test pins the mismatch rather than fixing it, because fixing it is a decision, not a bug
 * fix: either the decoder learns RIFF/WAVE PCM, or the picker stops offering `.wav`. Both are
 * product choices.
 */
class WavAcceptanceGapTest {

    /** Minimal RIFF/WAVE header, which is what a `.wav` actually starts with. */
    private fun riffWav(): ByteArray {
        val bytes = ByteArray(64)
        bytes[0] = 'R'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[8] = 'W'.code.toByte()
        bytes[9] = 'A'.code.toByte()
        bytes[10] = 'V'.code.toByte()
        bytes[11] = 'E'.code.toByte()
        return bytes
    }

    @Test
    fun thePickerWouldAcceptADotWavName() {
        // Mirrors AudioPicker's extension check, which is what the UI promises.
        val accepted = listOf("song.wav", "song.WAV").all {
            it.endsWith(".wav", ignoreCase = true) || it.endsWith(".bwav", ignoreCase = true)
        }
        assertTrue(accepted, "the picker advertises .wav")
    }

    @Test
    fun theDecoderRejectsRiffContainers() {
        // The other half of the mismatch: real `.wav` bytes do not reach the decoder.
        val result = BwavHeader.read(riffWav())
        assertTrue(
            result is BwavHeader.Result.Rejected,
            "a RIFF file must be rejected by the BWAV reader, got $result",
        )
    }

    @Test
    fun theRejectionReasonNamesTheContainer() {
        // A user who picks a .wav should be told what went wrong, not handed a status code.
        val result = BwavHeader.read(riffWav())
        val reason = (result as BwavHeader.Result.Rejected).reason
        assertTrue(reason.isNotBlank())
        assertTrue(reason.contains("BWAV"), "the reason should name the container: $reason")
    }

    @Test
    fun aNonWavFileIsAlsoRejectedWithoutCrashing() {
        // The reader must not depend on the extension having been checked first.
        assertNull(
            (BwavHeader.read(ByteArray(0)) as? BwavHeader.Result.Ok)?.info,
            "empty input cannot be a header",
        )
    }
}
