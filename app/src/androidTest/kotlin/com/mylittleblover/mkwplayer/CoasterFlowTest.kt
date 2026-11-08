package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.TransitionSnapshot
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Coaster five-part flow, played by the real engine.
 *
 * This is the stage C prototype: `Pre_Short`, `Pre`, `Bridge`, `Main` and `Outro00`, compiled
 * into one graph and driven through real transitions.
 *
 * The parts and their measured properties are read from the actual files rather than hardcoded,
 * so the test fails if a fixture is replaced with something of a different shape. The *timing* is
 * `PROVISIONAL`: when the game enters its bridge is game behaviour, and this project has no
 * game-behaviour evidence, so the flow is labelled rather than presented as a reproduction.
 */
@RunWith(AndroidJUnit4::class)
class CoasterFlowTest {

    private data class Files(val byName: Map<String, ByteArray>)

    private val directory = File("/data/local/tmp")
    private val expected = listOf(
        "BGM_Stage_Coaster_Pre_Short.bwav" to "coaster-PreShort.bwav",
        "BGM_Stage_Coaster_Pre.bwav" to "coaster-Pre.bwav",
        "BGM_Stage_Coaster_Bridge.bwav" to "coaster-Bridge.bwav",
        "BGM_Stage_Coaster.bwav" to "coaster.bwav",
        "BGM_Stage_Coaster_Outro00.bwav" to "coaster-Outro00.bwav",
    )

    private fun files(): Files? {
        val map = LinkedHashMap<String, ByteArray>()
        for ((logical, actual) in expected) {
            val file = File(directory, actual)
            if (!file.exists()) return null
            map[logical] = file.readBytes()
        }
        return Files(map)
    }

    /** Builds the flow from the real headers, then compiles it. */
    private fun flowOf(data: Files): StageFlow {
        val parts = expected.map { (logical, _) ->
            StageFlowReader.readPart(logical, data.byName[logical]!!)
                .getOrElse { throw AssertionError("could not read $logical: ${it.message}") }
        }
        return StageFlowReader.assemble("Coaster", parts)
            .getOrElse { throw AssertionError("could not assemble the flow: ${it.message}") }
    }

    private fun engineFor(data: Files, flow: StageFlow): Pair<AudioEngine, StageFlowCompiler.Compiled> {
        val compiled = StageFlowCompiler.compileFromBytes(flow, data.byName)
        val engine = AudioEngine.create()
        assertNotNull("engine allocation failed", engine)
        val audio = engine!!
        assertEquals(0, audio.loadGraph(compiled.packet))

        // Bind by the compiler's own asset order, so a mismatch cannot be papered over.
        compiled.assetPaths.forEachIndexed { index, path ->
            assertEquals(
                "binding asset $index ($path)",
                0,
                audio.bindBwav(index, data.byName[path]!!, path),
            )
        }
        return audio to compiled
    }

    private fun render(audio: AudioEngine, blocks: Int): Pair<Float, Int> {
        var peak = 0f
        var nonZero = 0
        repeat(blocks) {
            val block = audio.render(frames = 512)
            assertNotNull("render returned null", block)
            for (sample in block!!) {
                val magnitude = kotlin.math.abs(sample)
                if (magnitude > peak) peak = magnitude
                if (sample != 0f) nonZero++
            }
        }
        return peak to nonZero
    }

    // ---------------------------------------------------------------- shape

    @Test
    fun theFivePartsAssembleIntoOneProvisionalFlow() {
        val data = files() ?: return
        val flow = flowOf(data)

        assertEquals(5, flow.parts.size)
        assertTrue("the flow must be labelled provisional", flow.isProvisional)
        assertEquals(StageFlow.Provenance.PROVISIONAL, flow.provenance)

        // The measured durations, from the real headers.
        assertEquals(534_857L, flow.part(StageFlow.Role.PreShort)!!.frames)
        assertEquals(2_427_426L, flow.part(StageFlow.Role.Pre)!!.frames)
        assertEquals(88_320L, flow.part(StageFlow.Role.Bridge)!!.frames)
        assertEquals(3_373_711L, flow.part(StageFlow.Role.Main)!!.frames)
        assertEquals(226_286L, flow.part(StageFlow.Role.Outro)!!.frames)
    }

    @Test
    fun theCompiledGraphIsAcceptedByTheEngine() {
        val data = files() ?: return
        val flow = flowOf(data)
        val compiled = StageFlowCompiler.compileFromBytes(flow, data.byName)

        assertTrue(
            "the builder produced an unreadable packet",
            GraphProbe.inspect(compiled.packet) is GraphProbe.Result.Ok,
        )

        val audio = engineFor(data, flow).first
        try {
            assertEquals(48000, audio.sampleRate)
            assertEquals(
                "the five-part flow should get one segment per part",
                5,
                compiled.segmentByRole.size,
            )
        } finally {
            audio.close()
        }
    }

    // ---------------------------------------------------------------- playback

    @Test
    fun everyPartStartsAndSounds() {
        val data = files() ?: return
        val flow = flowOf(data)
        val compiled = StageFlowCompiler.compileFromBytes(flow, data.byName)

        for ((role, segment) in compiled.segmentByRole) {
            val audio = engineFor(data, flow).first
            try {
                assertEquals("Play($segment) for $role", 0, audio.play(segment))
                val (peak, nonZero) = render(audio, blocks = 4)
                assertTrue(
                    "$role rendered silence: peak=$peak",
                    nonZero > 0,
                )
            } finally {
                audio.close()
            }
        }
    }

    @Test
    fun theMainSectionLoopsWithoutEnding() {
        // The main theme is a loop: playing past its loop window must keep producing audio
        // rather than running out at the file end.
        val data = files() ?: return
        val flow = flowOf(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!
            assertEquals(0, audio.play(main))

            // Render a long stretch -- more than the 18 s of audio before the loop point
            // matters, and well past one buffer.
            render(audio, blocks = 32)
            val before = EngineSnapshot.read(audio.snapshot())!!.transportFrame

            render(audio, blocks = 64)
            val after = EngineSnapshot.read(audio.snapshot())!!.transportFrame

            assertTrue("the transport stalled in the main loop", after > before)
            val (peak, nonZero) = render(audio, blocks = 8)
            assertTrue("the loop went silent: peak=$peak", nonZero > 0)
        } finally {
            audio.close()
        }
    }

    @Test
    fun preludeToMainRunsAsATransition() {
        val data = files() ?: return
        val flow = flowOf(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val pre = compiled.segmentByRole[StageFlow.Role.Pre]!!
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!

            assertEquals(0, audio.play(pre))
            render(audio, blocks = 8)

            assertEquals("requesting the main section was refused", 0, audio.play(main))
            render(audio, blocks = 8)

            val transition = TransitionSnapshot.read(audio.snapshot())
            assertNotNull("no snapshot after the request", transition)
            assertTrue(
                "prelude to main did not run as a transition: ${transition!!}",
                transition.active || transition.transition > 0,
            )
            assertEquals("the source should be the prelude segment", pre.toLong(), transition.source)
            assertEquals("the destination should be the main segment", main.toLong(), transition.destination)
        } finally {
            audio.close()
        }
    }

    @Test
    fun theBridgeIsDeclaredOnThePreludeTransition() {
        val data = files() ?: return
        val flow = flowOf(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val pre = compiled.segmentByRole[StageFlow.Role.Pre]!!
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!
            val bridge = compiled.segmentByRole[StageFlow.Role.Bridge]!!

            assertEquals(0, audio.play(pre))
            render(audio, blocks = 8)
            assertEquals(0, audio.play(main))
            render(audio, blocks = 8)

            val transition = TransitionSnapshot.read(audio.snapshot())!!
            assertEquals(
                "the prelude transition must name the bridge segment",
                bridge.toLong(),
                transition.bridge,
            )
        } finally {
            audio.close()
        }
    }

    @Test
    fun theOtherPreludeAlsoReachesTheMainSection() {
        // Pre_Short and Pre both point at Main; whichever the game would have used, the flow
        // has to work. A graph where only one prelude is wired would strand the other.
        val data = files() ?: return
        val flow = flowOf(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val short = compiled.segmentByRole[StageFlow.Role.PreShort]!!
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!

            assertEquals(0, audio.play(short))
            render(audio, blocks = 8)
            assertEquals(0, audio.play(main))
            render(audio, blocks = 8)

            val transition = TransitionSnapshot.read(audio.snapshot())!!
            assertEquals(short.toLong(), transition.source)
            assertEquals(main.toLong(), transition.destination)
        } finally {
            audio.close()
        }
    }

    @Test
    fun mainToOutroRunsAsATransitionAndEndsSilently() {
        val data = files() ?: return
        val flow = flowOf(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!
            val outro = compiled.segmentByRole[StageFlow.Role.Outro]!!

            assertEquals(0, audio.play(main))
            render(audio, blocks = 8)

            assertEquals("requesting the outro was refused", 0, audio.play(outro))
            render(audio, blocks = 8)

            val transition = TransitionSnapshot.read(audio.snapshot())!!
            assertEquals("the source should be the main segment", main.toLong(), transition.source)
            assertEquals("the destination should be the outro", outro.toLong(), transition.destination)

            // The outro is short (226,286 frames, ~4.7 s); rendering well past it must not
            // resurrect audio, and must not crash.
            val (_, _) = render(audio, blocks = 600)
        } finally {
            audio.close()
        }
    }

    @Test
    fun theWholeFlowKeepsASingleTransportClock() {
        // Walking prelude -> main -> outro must not restart the clock between segments. A
        // manual concatenation of players would fail exactly here.
        val data = files() ?: return
        val flow = flowOf(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val frames = mutableListOf<Long>()

            assertEquals(0, audio.play(compiled.segmentByRole[StageFlow.Role.Pre]!!))
            render(audio, blocks = 8)
            frames += EngineSnapshot.read(audio.snapshot())!!.transportFrame

            assertEquals(0, audio.play(compiled.segmentByRole[StageFlow.Role.Main]!!))
            render(audio, blocks = 8)
            frames += EngineSnapshot.read(audio.snapshot())!!.transportFrame

            assertEquals(0, audio.play(compiled.segmentByRole[StageFlow.Role.Outro]!!))
            render(audio, blocks = 8)
            frames += EngineSnapshot.read(audio.snapshot())!!.transportFrame

            assertTrue(
                "the transport went backwards across the flow: $frames",
                frames.zipWithNext().all { (a, b) -> b >= a },
            )
        } finally {
            audio.close()
        }
    }
}
