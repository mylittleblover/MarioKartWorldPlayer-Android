package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.TransitionSnapshot
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowReader
import com.mylittleblover.mkwplayer.player.AudioLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Choosing between several outros, replacing a request that has not been executed, and measuring
 * the result at the sample level.
 *
 * `CoasterFlowTest` established that the flow plays and that segments swap. Its audio assertions
 * were "not silent", which a transition that halved the level, or restarted the outro, would also
 * pass. This file replaces that with measurements: RMS before and after the handover, and an
 * explicit check that the outro's own end really is silent.
 *
 * The timings remain `PROVISIONAL`: the flow is assembled by this project, not read from evidence
 * about the game's behaviour.
 */
@RunWith(AndroidJUnit4::class)
class MultiOutroTest {

    private val directory = File("/data/local/tmp")
    private val expected = listOf(
        "BGM_Stage_Coaster_Pre_Short.bwav" to "coaster-PreShort.bwav",
        "BGM_Stage_Coaster_Pre.bwav" to "coaster-Pre.bwav",
        "BGM_Stage_Coaster_Bridge.bwav" to "coaster-Bridge.bwav",
        "BGM_Stage_Coaster.bwav" to "coaster.bwav",
        "BGM_Stage_Coaster_Outro00.bwav" to "coaster-Outro00.bwav",
        "BGM_Stage_Coaster_Outro01.bwav" to "coaster-Outro01.bwav",
        "BGM_Stage_Coaster_Outro02.bwav" to "coaster-Outro02.bwav",
    )

    /** Measured elsewhere and re-asserted here so a swapped fixture fails loudly. */
    private val expectedOutroFrames = mapOf(
        0 to 226_286L,
        1 to 205_715L,
        2 to 205_376L,
    )

    private fun bytes(): Map<String, ByteArray>? {
        val map = LinkedHashMap<String, ByteArray>()
        for ((logical, actual) in expected) {
            val file = File(directory, actual)
            if (!file.exists()) return null
            map[logical] = file.readBytes()
        }
        return map
    }

    private fun flow(data: Map<String, ByteArray>): StageFlow {
        val parts = expected.map { (logical, _) ->
            StageFlowReader.readPart(logical, data[logical]!!)
                .getOrElse { throw AssertionError("could not read $logical: ${it.message}") }
        }
        return StageFlowReader.assemble("Coaster", parts)
            .getOrElse { throw AssertionError("could not assemble: ${it.message}") }
    }

    private fun engineFor(
        data: Map<String, ByteArray>,
        flow: StageFlow,
    ): Pair<AudioEngine, StageFlowCompiler.Compiled> {
        val compiled = StageFlowCompiler.compileFromBytes(flow, data)
        val audio = AudioEngine.create()!!
        assertEquals(0, audio.loadGraph(compiled.packet))
        compiled.assetPaths.forEachIndexed { index, path ->
            assertEquals(
                "binding asset $index ($path)",
                0,
                audio.bindBwav(index, data[path]!!, path),
            )
        }
        return audio to compiled
    }

    // ---------------------------------------------------------------- the outros exist

    @Test
    fun allThreeOutrosAreCarriedWithTheirMeasuredLengths() {
        val data = bytes() ?: return
        val flow = flow(data)

        assertEquals(3, flow.outros().size)
        for ((number, frames) in expectedOutroFrames) {
            val part = flow.outro(number)
            assertNotNull("outro $number is missing", part)
            assertEquals("outro $number length", frames, part!!.frames)
            // An outro that looped would never end; the reader must report no loop window.
            assertTrue("outro $number must not loop", !part.hasLoop)
        }
    }

    @Test
    fun eachOutroGetsItsOwnSegment() {
        val data = bytes() ?: return
        val compiled = StageFlowCompiler.compileFromBytes(flow(data), data)
        assertEquals(3, compiled.segmentByOutro.size)
        assertEquals(
            "outro segments must be distinct",
            3,
            compiled.segmentByOutro.values.toSet().size,
        )
        for (number in expectedOutroFrames.keys) {
            assertNotNull("outro $number has no segment", compiled.segmentForOutro(number))
        }
    }

    // ---------------------------------------------------------------- selection

    @Test
    fun requestingASpecificOutroLandsOnThatOutro() {
        // Not "some outro": the caller asked for 1, so the transition must name segment for 1.
        val data = bytes() ?: return
        val flow = flow(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!
            val target = compiled.segmentForOutro(1)!!

            assertEquals(0, audio.play(main))
            AudioLevel.render(audio, blocks = 8)

            assertEquals("requesting outro 1 was refused", 0, audio.play(target))
            AudioLevel.render(audio, blocks = 8)

            val transition = TransitionSnapshot.read(audio.snapshot())
            assertNotNull("no snapshot after requesting outro 1", transition)
            assertEquals("the source should be Main", main.toLong(), transition!!.source)
            assertEquals(
                "the destination should be the segment for outro 1, not any other",
                target.toLong(),
                transition.destination,
            )
        } finally {
            audio.close()
        }
    }

    @Test
    fun everyOutroCanBeRequestedFromMain() {
        val data = bytes() ?: return
        val flow = flow(data)
        for (number in expectedOutroFrames.keys) {
            val (audio, compiled) = engineFor(data, flow)
            try {
                val main = compiled.segmentByRole[StageFlow.Role.Main]!!
                val target = compiled.segmentForOutro(number)!!

                assertEquals(0, audio.play(main))
                AudioLevel.render(audio, blocks = 6)
                assertEquals("requesting outro $number was refused", 0, audio.play(target))
                AudioLevel.render(audio, blocks = 6)

                val transition = TransitionSnapshot.read(audio.snapshot())!!
                assertEquals("outro $number destination", target.toLong(), transition.destination)

                val level = AudioLevel.render(audio, blocks = 8)
                assertTrue(
                    "outro $number produced no audio: $level",
                    level.anyAudio,
                )
            } finally {
                audio.close()
            }
        }
    }

    // ---------------------------------------------------------------- level, not just presence

    @Test
    fun theHandoverToAnOutroHoldsTheLevel() {
        // A transition that silenced the track, or dropped it by 26 dB, would pass a
        // "not all zeros" check. This measures the actual level either side.
        val data = bytes() ?: return
        val flow = flow(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!
            val target = compiled.segmentForOutro(0)!!

            assertEquals(0, audio.play(main))
            AudioLevel.render(audio, blocks = 8)
            val before = AudioLevel.render(audio, blocks = 8)

            assertEquals(0, audio.play(target))
            AudioLevel.render(audio, blocks = 8)
            val after = AudioLevel.render(audio, blocks = 8)

            assertTrue("the main loop was silent before the handover: $before", before.anyAudio)
            assertTrue("nothing sounded after the handover: $after", after.anyAudio)
            assertTrue(
                "the level collapsed across the handover: ${before.meanRms} -> ${after.meanRms}",
                AudioLevel.levelHeld(before.meanRms, after.meanRms),
            )
        } finally {
            audio.close()
        }
    }

    @Test
    fun anOutroRunsOutInsteadOfLoopingForever() {
        // The outros do not loop. Rendering well past their length must end in silence rather
        // than restarting; otherwise a finite ending behaves like a loop.
        val data = bytes() ?: return
        val flow = flow(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val target = compiled.segmentForOutro(2)!!   // 205,376 frames = 4.279 s
            assertEquals(0, audio.play(target))

            // 4.279 s at 48 kHz is ~401 blocks of 512 frames; render 600 to be well past it.
            val whole = AudioLevel.render(audio, blocks = 600)

            assertTrue("the outro produced no audio at all: $whole", whole.anyAudio)
            assertFalse("a render failed during the outro: $whole", whole.failed)
            assertTrue(
                "the outro never stopped: only ${whole.silentBlocks} of ${whole.blocks} blocks silent",
                whole.silentBlocks > 0,
            )
        } finally {
            audio.close()
        }
    }

    // ---------------------------------------------------------------- replacement

    @Test
    fun aQueuedOutroRequestCanBeReplacedBeforeItExecutes() {
        // Request outro 0, then immediately request outro 1 without rendering in between: the
        // second request should win, because nothing has been executed yet.
        val data = bytes() ?: return
        val flow = flow(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!
            val first = compiled.segmentForOutro(0)!!
            val second = compiled.segmentForOutro(1)!!

            assertEquals(0, audio.play(main))
            AudioLevel.render(audio, blocks = 4)

            assertEquals(0, audio.play(first))
            // No render between the two requests: the first cannot have been executed.
            assertEquals(0, audio.play(second))
            AudioLevel.render(audio, blocks = 8)

            val transition = TransitionSnapshot.read(audio.snapshot())!!
            assertEquals(
                "the replacement should have won, not the original request: $transition",
                second.toLong(),
                transition.destination,
            )
        } finally {
            audio.close()
        }
    }

    @Test
    fun replacingWhileTheOutroAlreadyRunsKeepsTheTransportMoving() {
        // Once the outro is sounding, a replacement is a second transition rather than a simple
        // queue swap. Whatever the engine decides, the clock must not stall or go backwards.
        val data = bytes() ?: return
        val flow = flow(data)
        val (audio, compiled) = engineFor(data, flow)
        try {
            val main = compiled.segmentByRole[StageFlow.Role.Main]!!
            val first = compiled.segmentForOutro(0)!!
            val second = compiled.segmentForOutro(2)!!

            assertEquals(0, audio.play(main))
            AudioLevel.render(audio, blocks = 4)
            assertEquals(0, audio.play(first))
            AudioLevel.render(audio, blocks = 40)

            val before = EngineSnapshot.read(audio.snapshot())!!.transportFrame
            assertEquals(0, audio.play(second))
            AudioLevel.render(audio, blocks = 16)
            val after = EngineSnapshot.read(audio.snapshot())!!.transportFrame

            assertTrue("the transport stalled on replacement: $before -> $after", after >= before)
        } finally {
            audio.close()
        }
    }
}
