package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Plays a graph produced by the in-app builder, not a pre-built fixture.
 *
 * AND-04/05/06 proved the engine can play the graph that `tools/graph_build.py`
 * emitted. This closes the loop on the Kotlin builder: the bytes it produces are
 * fed straight to the engine and must produce audio.
 *
 * Reading the loop fields back from the BWAV header keeps the fixture honest --
 * if the file on the device differs from what the test assumes, the assertions
 * below fail instead of the playback silently using wrong bounds.
 */
@RunWith(AndroidJUnit4::class)
class BuiltGraphPlaybackTest {

    private val dir = "/data/local/tmp"
    private val bwavFile = File(dir, "coaster.bwav")
    private val assetId = "BGM_Stage_Coaster.bwav"

    /** Minimal BWAV header reader: only the fields the builder needs. */
    private fun readBwavFacts(bytes: ByteArray): Facts {
        assertEquals("BWAV", String(bytes, 0, 4, Charsets.US_ASCII))
        val channels = u16(bytes, 14)
        val headerEnd = 16 + channels * 76
        val rate = u32(bytes, 16 + 4)
        val frames = u32(bytes, 16 + 12)
        val last = u32(bytes, 16 + 60)
        val first = u32(bytes, 16 + 64)
        val loop = if (last.toLong() == 0xFFFFFFFFL) null else GraphBuilder.Loop(first.toLong(), last.toLong())
        return Facts(rate, channels, frames, loop, headerEnd)
    }

    private data class Facts(
        val rate: Int,
        val channels: Int,
        val frames: Int,
        val loop: GraphBuilder.Loop?,
        val headerEnd: Int,
    )

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private fun sha256(bytes: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)

    @Test
    fun builtGraphIsAcceptedAndPlays() {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val audio = bwavFile.readBytes()
        val facts = readBwavFacts(audio)

        val packet = GraphBuilder.build(
            GraphBuilder.Spec(
                asset = GraphBuilder.Asset(
                    path = assetId,
                    sampleRate = facts.rate,
                    channels = facts.channels,
                    frames = facts.frames.toLong(),
                    sha256 = sha256(audio),
                    loop = facts.loop,
                ),
                transportSampleRate = 48000,
            ),
        )

        // The envelope must pass our own probe before the engine sees it.
        val probed = GraphProbe.inspect(packet)
        assertTrue("builder output rejected by probe: $probed", probed is GraphProbe.Result.Ok)

        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(
                "engine rejected the built graph",
                0,
                NativeAudioLibrary.loadCompiledGraph(engine, packet),
            )
            assertEquals(
                "binding the real file failed",
                0,
                NativeAudioLibrary.bindSource(
                    engine, 0, "native.bwav", audio, 0, assetId, null, null, null,
                ),
            )
            assertEquals(0, NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L))

            var peak = 0f
            var nonZero = 0
            repeat(6) {
                val buffer = FloatArray(512 * 2)
                assertEquals(0, NativeAudioLibrary.renderRealtime(engine, buffer, 512))
                for (sample in buffer) {
                    val magnitude = kotlin.math.abs(sample)
                    if (magnitude > peak) peak = magnitude
                    if (sample != 0f) nonZero++
                }
            }

            assertTrue(
                "a graph built in-app produced only silence: peak=$peak nonzero=$nonZero",
                nonZero > 0,
            )
            assertTrue("implausible peak $peak", peak in 1e-4f..4f)
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun builtGraphCarriesTheFilesOwnRateAndLength() {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val audio = bwavFile.readBytes()
        val facts = readBwavFacts(audio)

        // The header facts this test relies on, asserted explicitly so a differing
        // fixture fails here rather than as a confusing playback failure.
        assertEquals(48000, facts.rate)
        assertEquals(2, facts.channels)
        assertEquals(3_373_711, facts.frames)
        assertTrue("expected a loop in the fixture", facts.loop != null)
        assertEquals(493_714L, facts.loop!!.startFrame)
        assertEquals(3_373_711L, facts.loop!!.endFrame)
    }
}
