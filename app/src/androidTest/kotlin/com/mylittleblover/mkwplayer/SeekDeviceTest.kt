package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SeekTarget
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Seek on the real engine, with the real track.
 *
 * The JVM tests prove the arithmetic and the state machine; this proves the native
 * call actually repositions the transport. Both matter: a correct fraction mapping
 * into a seek the engine ignores would look right in unit tests and do nothing on the
 * device.
 */
@RunWith(AndroidJUnit4::class)
class SeekDeviceTest {

    private val bwavFile = File("/data/local/tmp/coaster.bwav")
    private val assetId = "BGM_Stage_Coaster.bwav"

    private fun buildPacket(bytes: ByteArray, frames: Int): ByteArray =
        GraphBuilder.build(
            GraphBuilder.Spec(
                asset = GraphBuilder.Asset(
                    path = assetId,
                    sampleRate = 48000,
                    channels = 2,
                    frames = frames.toLong(),
                    sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes),
                    loop = GraphBuilder.Loop(493_714, 3_373_711),
                ),
            ),
        )

    @Test
    fun seekRepositionsTheTransport() {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val bytes = bwavFile.readBytes()
        val frames = 3_373_711

        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(0, NativeAudioLibrary.loadCompiledGraph(engine, buildPacket(bytes, frames)))
            assertEquals(
                0,
                NativeAudioLibrary.bindSource(engine, 0, "native.bwav", bytes, 0, assetId, null, null, null),
            )
            assertEquals(0, NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L))

            // Render a little so the transport is genuinely moving first.
            val before = FloatArray(512 * 2)
            assertEquals(0, NativeAudioLibrary.renderRealtime(engine, before, 512))

            val target = SeekTarget.fractionToFrame(0.5f, frames.toLong())
            assertEquals("seek must return Ok(0)", 0, NativeAudioLibrary.seek(engine, target))

            val fields = EngineSnapshot.read(NativeAudioLibrary.snapshot(engine))
            assertTrue("snapshot unreadable after seek", fields != null)
            assertEquals(
                "transport did not move to the seek target",
                target,
                fields!!.transportFrame,
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun audioContinuesAfterSeek() {
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
        val bytes = bwavFile.readBytes()
        val frames = 3_373_711

        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(0, NativeAudioLibrary.loadCompiledGraph(engine, buildPacket(bytes, frames)))
            assertEquals(
                0,
                NativeAudioLibrary.bindSource(engine, 0, "native.bwav", bytes, 0, assetId, null, null, null),
            )
            assertEquals(0, NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L))

            val target = SeekTarget.fractionToFrame(0.25f, frames.toLong())
            assertEquals(0, NativeAudioLibrary.seek(engine, target))

            var peak = 0f
            var nonZero = 0
            repeat(4) {
                val buffer = FloatArray(512 * 2)
                assertEquals(0, NativeAudioLibrary.renderRealtime(engine, buffer, 512))
                for (sample in buffer) {
                    val magnitude = kotlin.math.abs(sample)
                    if (magnitude > peak) peak = magnitude
                    if (sample != 0f) nonZero++
                }
            }
            assertTrue(
                "playback went silent after a seek: peak=$peak nonzero=$nonZero",
                nonZero > 0,
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun seekBeforeLoadIsRejected() {
        // With no runtime, the engine must refuse rather than move a nonexistent clock.
        val engine = NativeAudioLibrary.createEngine()
        try {
            assertTrue(
                "seek on a bare engine must not report success",
                NativeAudioLibrary.seek(engine, 1000) != 0,
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }
}
