package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Decoder-correctness proof: the engine's output on the device must match an
 * independent reference render sample for sample.
 *
 * A "peak > 0" check only proves the decoder emitted something. This compares
 * actual PCM against `ref-coaster.pcm`, produced from the reference WAV that
 * ships in readable_assets, so a decoder that emits plausible noise fails.
 *
 * Samples are compared at the head of the track (offset 0) because the engine
 * starts playback at frame 0 and the reference begins there too. Both are
 * 16-bit PCM normalised by 32768, so the comparison is exact after scaling.
 */
@RunWith(AndroidJUnit4::class)
class DecoderCorrectnessTest {

    private val dir = "/data/local/tmp"
    private val graphFile = File(dir, "coaster-graph.bin")
    private val bwavFile = File(dir, "coaster.bwav")
    private val referenceFile = File(dir, "ref-coaster.pcm")
    private val assetId = "BGM_Stage_Coaster.bwav"

    private fun requireFixtures() {
        assertTrue("missing ${graphFile.path}", graphFile.exists())
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        assertTrue("missing ${referenceFile.path}", referenceFile.exists())
    }

    private fun referenceSamples(): ShortArray {
        val bytes = referenceFile.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = ShortArray(bytes.size / 2)
        for (i in out.indices) out[i] = buffer.short
        return out
    }

    @Test
    fun decodedPcmMatchesTheReferenceRender() {
        requireFixtures()
        val reference = referenceSamples()
        assertTrue("reference too small", reference.size >= 1024)

        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(0, NativeAudioLibrary.loadCompiledGraph(engine, graphFile.readBytes()))
            assertEquals(
                0,
                NativeAudioLibrary.bindSource(
                    engine, 0, "native.bwav", bwavFile.readBytes(), 0,
                    assetId, null, null, null,
                ),
            )
            assertEquals(0, NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L))

            // Render enough frames to cover the reference, in chunks so the
            // engine's internal block size does not matter.
            val framesWanted = reference.size / 2
            val rendered = FloatArray(framesWanted * 2)
            var written = 0
            while (written < framesWanted) {
                val block = minOf(512, framesWanted - written)
                val buffer = FloatArray(block * 2)
                assertEquals(0, NativeAudioLibrary.renderRealtime(engine, buffer, block))
                System.arraycopy(buffer, 0, rendered, written * 2, block * 2)
                written += block
            }

            var compared = 0
            var exact = 0
            var within1 = 0
            var maxDiff = 0
            var firstMismatch = -1
            for (i in 0 until framesWanted * 2) {
                val expected = reference[i]
                // Reference is int16; the engine renders normalised float.
                val actual = Math.round(rendered[i] * 32768.0f).toInt()
                val diff = abs(actual - expected.toInt())
                compared++
                if (diff == 0) exact++
                if (diff <= 1) within1++
                if (diff > maxDiff) maxDiff = diff
                if (diff > 0 && firstMismatch < 0) firstMismatch = i
            }

            val exactPct = 100.0 * exact / compared
            assertTrue(
                "decoded PCM does not match the reference: exact=$exact/$compared " +
                    "(${"%.3f".format(exactPct)}%), within1=$within1, maxDiff=$maxDiff, " +
                    "firstMismatch=$firstMismatch",
                maxDiff == 0,
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }
}
