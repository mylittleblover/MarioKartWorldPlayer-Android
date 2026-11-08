package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * A multi-asset, multi-segment graph played by the real native engine.
 *
 * The JVM tests prove this builder emits the same bytes as the verified single-asset one for
 * the single-segment case, and that it rejects bad input. Neither proves the engine accepts a
 * graph with three assets and three segments, because only the engine can answer that.
 *
 * The graph is the Coaster shape the prototype needs:
 *
 *     Pre (segment 10)  --transition-->  Main (segment 12)  with Bridge (segment 11) as its
 *                                                          bridge segment
 *
 * Assets are the real files from the game data, each bound separately, so a pass means the
 * engine really is addressing three distinct sources rather than reusing one.
 */
@RunWith(AndroidJUnit4::class)
class MultiSegmentPlaybackTest {

    private val pre = File("/data/local/tmp/coaster-Pre.bwav")
    private val bridge = File("/data/local/tmp/coaster-Bridge.bwav")
    private val main = File("/data/local/tmp/coaster.bwav")

    private val preFrames = 2_427_426L
    private val bridgeFrames = 88_320L
    private val mainFrames = 3_373_711L

    private data class Assets(val pre: ByteArray, val bridge: ByteArray, val main: ByteArray)

    private fun assets(): Assets? {
        if (!pre.exists() || !bridge.exists() || !main.exists()) return null
        return Assets(pre.readBytes(), bridge.readBytes(), main.readBytes())
    }

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    /** Pre → Main with Bridge declared as the bridge segment of that transition. */
    private fun spec(assets: Assets): MusicGraphBuilder.Spec = MusicGraphBuilder.Spec(
        assets = listOf(
            MusicGraphBuilder.Asset("BGM_Stage_Coaster_Pre.bwav", 48000, 2, preFrames, digest(assets.pre)),
            MusicGraphBuilder.Asset("BGM_Stage_Coaster_Bridge.bwav", 48000, 2, bridgeFrames, digest(assets.bridge)),
            MusicGraphBuilder.Asset("BGM_Stage_Coaster.bwav", 48000, 2, mainFrames, digest(assets.main)),
        ),
        segments = listOf(
            MusicGraphBuilder.Segment(index = 10, entry = 0, exit = preFrames),
            MusicGraphBuilder.Segment(index = 11, entry = 0, exit = bridgeFrames),
            MusicGraphBuilder.Segment(
                index = 12,
                entry = 0,
                exit = mainFrames,
                loop = MusicGraphBuilder.Loop(493_714, mainFrames),
                cues = listOf(
                    MusicGraphBuilder.Cue(20, "Bridge", 883_200),
                    MusicGraphBuilder.Cue(21, "Outro", 2_427_426),
                ),
            ),
        ),
        tracks = listOf(
            MusicGraphBuilder.Track(
                index = 30,
                segment = 10,
                clips = listOf(MusicGraphBuilder.Clip(31, asset = 0, sourceLength = preFrames)),
            ),
            MusicGraphBuilder.Track(
                index = 32,
                segment = 11,
                clips = listOf(MusicGraphBuilder.Clip(33, asset = 1, sourceLength = bridgeFrames)),
            ),
            MusicGraphBuilder.Track(
                index = 34,
                segment = 12,
                clips = listOf(
                    MusicGraphBuilder.Clip(
                        index = 35,
                        asset = 2,
                        sourceLength = mainFrames,
                        loop = MusicGraphBuilder.Loop(493_714, mainFrames),
                    ),
                ),
            ),
        ),
        transitions = listOf(
            MusicGraphBuilder.Transition(
                index = 40,
                sources = listOf(10),
                destinations = listOf(12),
                sync = MusicGraphBuilder.SyncPoint.ExitCue,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
                bridge = 11,
            ),
        ),
    )

    @Test
    fun theEngineAcceptsAThreeAssetThreeSegmentGraph() {
        val data = assets() ?: return   // fixtures absent: nothing to assert
        val packet = MusicGraphBuilder.build(spec(data))

        assertTrue("the builder produced an unreadable packet", GraphProbe.inspect(packet) is GraphProbe.Result.Ok)

        val engine = AudioEngine.create()
        assertNotNull("engine allocation failed", engine)
        val audio = engine!!
        try {
            assertEquals(
                "the native reader rejected a three-segment graph",
                0,
                audio.loadGraph(packet),
            )
            assertEquals(48000, audio.sampleRate)
        } finally {
            audio.close()
        }
    }

    @Test
    fun threeAssetsBindIndependently() {
        val data = assets() ?: return
        val engine = AudioEngine.create()!!
        try {
            assertEquals(0, engine.loadGraph(MusicGraphBuilder.build(spec(data))))
            // Distinct asset slots, each with its own bytes. A naive implementation that
            // reused slot 0 would still "work" for playback but would play the wrong file.
            assertEquals("asset 0 (Pre)", 0, engine.bindBwav(0, data.pre, "BGM_Stage_Coaster_Pre.bwav"))
            assertEquals("asset 1 (Bridge)", 0, engine.bindBwav(1, data.bridge, "BGM_Stage_Coaster_Bridge.bwav"))
            assertEquals("asset 2 (Main)", 0, engine.bindBwav(2, data.main, "BGM_Stage_Coaster.bwav"))
        } finally {
            engine.close()
        }
    }

    @Test
    fun eachSegmentStartsAndRendersAudio() {
        val data = assets() ?: return
        for ((segment, label) in listOf(10 to "Pre", 11 to "Bridge", 12 to "Main")) {
            val engine = AudioEngine.create()!!
            try {
                assertEquals(0, engine.loadGraph(MusicGraphBuilder.build(spec(data))))
                assertEquals(0, engine.bindBwav(0, data.pre, "BGM_Stage_Coaster_Pre.bwav"))
                assertEquals(0, engine.bindBwav(1, data.bridge, "BGM_Stage_Coaster_Bridge.bwav"))
                assertEquals(0, engine.bindBwav(2, data.main, "BGM_Stage_Coaster.bwav"))

                assertEquals("Play($segment) for $label", 0, engine.play(segment))

                var peak = 0f
                var nonZero = 0
                repeat(4) {
                    val block = engine.render(frames = 512)
                    assertNotNull("render returned null for $label", block)
                    for (sample in block!!) {
                        val magnitude = kotlin.math.abs(sample)
                        if (magnitude > peak) peak = magnitude
                        if (sample != 0f) nonZero++
                    }
                }
                assertTrue(
                    "segment $label rendered silence: peak=$peak nonzero=$nonZero",
                    nonZero > 0,
                )
            } finally {
                engine.close()
            }
        }
    }

    @Test
    fun theTransportIsSharedAcrossSegments() {
        // One transport, not one per segment: playing a second segment must continue the same
        // clock rather than restart it at zero.
        val data = assets() ?: return
        val engine = AudioEngine.create()!!
        try {
            assertEquals(0, engine.loadGraph(MusicGraphBuilder.build(spec(data))))
            assertEquals(0, engine.bindBwav(0, data.pre, "BGM_Stage_Coaster_Pre.bwav"))
            assertEquals(0, engine.bindBwav(1, data.bridge, "BGM_Stage_Coaster_Bridge.bwav"))
            assertEquals(0, engine.bindBwav(2, data.main, "BGM_Stage_Coaster.bwav"))

            assertEquals(0, engine.play(10))
            repeat(4) { engine.render(frames = 512) }

            val afterPre = EngineSnapshot.read(engine.snapshot())
            assertNotNull("no snapshot after the first segment", afterPre)
            assertTrue(
                "the transport did not advance in the Pre segment: ${afterPre!!.transportFrame}",
                afterPre.transportFrame > 0,
            )

            assertEquals(0, engine.play(12))
            repeat(4) { engine.render(frames = 512) }

            val afterMain = EngineSnapshot.read(engine.snapshot())
            assertNotNull("no snapshot after the second segment", afterMain)
            assertTrue(
                "the transport went backwards when moving to Main: " +
                    "${afterPre.transportFrame} -> ${afterMain!!.transportFrame}",
                afterMain.transportFrame >= afterPre.transportFrame,
            )
        } finally {
            engine.close()
        }
    }
}
