package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * S5 end-to-end: load a real compiled graph, bind a real BWAV, play it, and
 * prove the engine emitted non-silent sample data.
 *
 * This is the step a build cannot reach. Compiling proves the code exists; this
 * proves the runtime decodes the game's own BWAV container and produces audio.
 *
 * Fixtures are pushed to /data/local/tmp by the runner script:
 *   coaster-graph.bin   compiled graph v6 referencing BGM_Stage_Coaster.bwav
 *   coaster.bwav        the real asset (48 kHz stereo, 3 373 711 frames,
 *                       loop 493714 .. 3373711)
 *
 * Neither is committed: the .bwav is user-supplied game data and the repository
 * must not carry it. See .gitignore and NOTICE-THIRD-PARTY.md.
 *
 * Two behaviours are load-bearing here and were measured, not assumed:
 *   - rendering before `Play` yields silence; the transport must be started
 *   - `loadLinear` replaces the engine runtime, discarding any loaded graph, so
 *     the two paths need separate engines
 */
@RunWith(AndroidJUnit4::class)
class RealBwavPlaybackTest {

    private val dir = "/data/local/tmp"
    private val graphFile = File(dir, "coaster-graph.bin")
    private val bwavFile = File(dir, "coaster.bwav")
    private val assetId = "BGM_Stage_Coaster.bwav"

    private fun requireFixtures() {
        assertTrue("missing ${graphFile.path}", graphFile.exists())
        assertTrue("missing ${bwavFile.path}", bwavFile.exists())
    }

    /** Opens an engine with the fixture graph loaded and the BWAV bound. */
    private fun engineWithBoundSource(): Long {
        val engine = NativeAudioLibrary.createEngine()
        assertEquals(
            "loadCompiledGraph must return Ok(0)",
            0,
            NativeAudioLibrary.loadCompiledGraph(engine, graphFile.readBytes()),
        )
        assertEquals(
            "bindSource must return Ok(0)",
            0,
            NativeAudioLibrary.bindSource(
                engine, 0, "native.bwav", bwavFile.readBytes(), 0,
                assetId, null, null, null,
            ),
        )
        return engine
    }

    private fun renderAndMeasure(engine: Long, blocks: Int): Pair<Float, Int> {
        var peak = 0f
        var nonzero = 0
        repeat(blocks) {
            val buffer = FloatArray(512 * 2)
            assertEquals("render must return Ok(0)", 0, NativeAudioLibrary.renderRealtime(engine, buffer, 512))
            for (sample in buffer) {
                val magnitude = kotlin.math.abs(sample)
                if (magnitude > peak) peak = magnitude
                if (sample != 0f) nonzero++
            }
        }
        return peak to nonzero
    }

    @Test
    fun compiledGraphLoads() {
        requireFixtures()
        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(0, NativeAudioLibrary.loadCompiledGraph(engine, graphFile.readBytes()))
            assertEquals(48000, NativeAudioLibrary.engineSampleRate(engine))
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun realBwavBindsToTheCompiledGraph() {
        requireFixtures()
        val engine = engineWithBoundSource()
        try {
            // A successful bind means the digest matched the graph's asset entry,
            // so the parser accepted MKW's truncated final DSP block.
            val snapshot = NativeAudioLibrary.snapshot(engine)
            assertTrue("snapshot should have fields", snapshot.isNotEmpty())
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun transportIsSilentUntilPlayIsSubmitted() {
        requireFixtures()
        val engine = engineWithBoundSource()
        try {
            val (peakBefore, nonzeroBefore) = renderAndMeasure(engine, 2)
            assertEquals("engine must be silent before Play", 0f, peakBefore, 0f)
            assertEquals("engine must be silent before Play", 0, nonzeroBefore)
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun playingTheSegmentProducesNonSilentAudio() {
        requireFixtures()
        val engine = engineWithBoundSource()
        try {
            assertEquals(
                "Play must return Ok(0)",
                0,
                NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L),
            )

            val (peak, nonzero) = renderAndMeasure(engine, 8)

            assertTrue(
                "engine produced only silence: peak=$peak nonzero=$nonzero. " +
                    "Either the transport never started or the decoder emitted nothing.",
                nonzero > 0,
            )
            assertTrue("peak amplitude $peak is implausible for decoded music", peak in 1e-4f..4f)
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun renderedPcmStaysInsideTheNormalisedRange() {
        requireFixtures()
        val engine = engineWithBoundSource()
        try {
            assertEquals(0, NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L))

            val buffer = FloatArray(1024 * 2)
            assertEquals(0, NativeAudioLibrary.renderRealtime(engine, buffer, 1024))

            var nonFinite = 0
            var outOfRange = 0
            for (sample in buffer) {
                if (!sample.isFinite()) nonFinite++
                if (sample < -1.01f || sample > 1.01f) outOfRange++
            }
            assertEquals("rendered PCM contains NaN or infinity", 0, nonFinite)
            assertEquals("rendered PCM left the normalised range", 0, outOfRange)
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun transportAdvancesWhilePlaying() {
        requireFixtures()
        val engine = engineWithBoundSource()
        try {
            assertEquals(0, NativeAudioLibrary.command(engine, 0, 1, -1, 0.0, -1L))
            renderAndMeasure(engine, 1)
            val first = NativeAudioLibrary.snapshot(engine)

            renderAndMeasure(engine, 4)
            val later = NativeAudioLibrary.snapshot(engine)

            // Snapshot layout is fixed by the bridge: [0] wire version tag,
            // [1] transport position, [2] transport status.
            val positionIndex = 1
            val statusIndex = 2
            assertEquals("snapshot wire version", 8L, first[0])
            assertEquals("transport should be Playing(1)", 1L, later[statusIndex])
            assertTrue(
                "transport did not advance: ${first[positionIndex]} -> ${later[positionIndex]}",
                later[positionIndex] > first[positionIndex],
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun linearLoadBindsWithoutAGraph() {
        requireFixtures()
        // loadLinear replaces the runtime and needs no graph; it must still accept
        // the same file the graph path accepts.
        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(
                0,
                NativeAudioLibrary.loadLinear(
                    engine, "native.bwav", bwavFile.readBytes(), true, 0,
                    assetId, null, null, null,
                ),
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun linearPlaybackStartsWithTargetMinusOne() {
        requireFixtures()
        // startSegment requires target == -1 when the runtime is linear: there is
        // no segment to name. Play(1) is meaningless here and silently yields
        // silence, which is exactly the trap that made an earlier test look like
        // a decoder failure.
        val engine = NativeAudioLibrary.createEngine()
        try {
            assertEquals(
                0,
                NativeAudioLibrary.loadLinear(
                    engine, "native.bwav", bwavFile.readBytes(), true, 0,
                    assetId, null, null, null,
                ),
            )
            assertEquals(
                "Play(-1) is the linear form",
                0,
                NativeAudioLibrary.command(engine, 0, -1, -1, 0.0, -1L),
            )

            val (peak, nonzero) = renderAndMeasure(engine, 4)
            assertTrue(
                "linear playback produced silence: peak=$peak nonzero=$nonzero",
                nonzero > 0,
            )
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }

    @Test
    fun graphPlayWithNoTargetLeavesTheTransportSilent() {
        requireFixtures()
        // Measured, not assumed: on the graph path Play(-1) is accepted (status
        // Ok) but starts no segment, so rendering stays silent. The command
        // status alone would look like success, which is why this asserts on the
        // audio instead.
        val engine = engineWithBoundSource()
        try {
            NativeAudioLibrary.command(engine, 0, -1, -1, 0.0, -1L)
            val (peak, nonzero) = renderAndMeasure(engine, 2)
            assertEquals("Play(-1) must not start a segment", 0f, peak, 0f)
            assertEquals("Play(-1) must not start a segment", 0, nonzero)
        } finally {
            NativeAudioLibrary.destroyEngine(engine)
        }
    }
}
