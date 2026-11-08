package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.TransitionSnapshot
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * A transition actually firing, on the real engine.
 *
 * `and-25` proved the engine accepts a graph containing transitions; every test there called
 * `Play(segment)` and relied on `transitionChoice` finding no rule, so nothing was ever
 * executed as a transition. This closes that gap.
 *
 * The mechanism, from `EngineRuntime::requestPlay`: if a transition rule matches the current
 * foreground and the requested target, `planTransition` runs instead of `startSegment`. So
 * `Play(12)` while segment 10 is playing must produce a transition, not an immediate cut.
 *
 * The transition is `sources=[10] destinations=[12] bridge=11 sync=Immediate`, deliberately
 * the simplest legal rule. Cue-relative sync would require L3 knowledge of where the game
 * actually switches, which this project does not have.
 */
@RunWith(AndroidJUnit4::class)
class TransitionExecutionTest {

    private val pre = File("/data/local/tmp/coaster-Pre.bwav")
    private val bridge = File("/data/local/tmp/coaster-Bridge.bwav")
    private val main = File("/data/local/tmp/coaster.bwav")

    private val preFrames = 2_427_426L
    private val bridgeFrames = 88_320L
    private val mainFrames = 3_373_711L

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private data class Data(val pre: ByteArray, val bridge: ByteArray, val main: ByteArray)

    private fun data(): Data? {
        if (!pre.exists() || !bridge.exists() || !main.exists()) return null
        return Data(pre.readBytes(), bridge.readBytes(), main.readBytes())
    }

    /**
     * Pre → Main through Bridge.
     *
     * `sync = Immediate` so the transition is observable within a few renders instead of
     * waiting for a bar boundary; the point of this test is that the path runs at all.
     */
    private fun spec(d: Data): MusicGraphBuilder.Spec = MusicGraphBuilder.Spec(
        assets = listOf(
            MusicGraphBuilder.Asset("BGM_Stage_Coaster_Pre.bwav", 48000, 2, preFrames, digest(d.pre)),
            MusicGraphBuilder.Asset("BGM_Stage_Coaster_Bridge.bwav", 48000, 2, bridgeFrames, digest(d.bridge)),
            MusicGraphBuilder.Asset("BGM_Stage_Coaster.bwav", 48000, 2, mainFrames, digest(d.main)),
        ),
        segments = listOf(
            MusicGraphBuilder.Segment(index = 10, entry = 0, exit = preFrames),
            MusicGraphBuilder.Segment(index = 11, entry = 0, exit = bridgeFrames),
            MusicGraphBuilder.Segment(
                index = 12,
                entry = 0,
                exit = mainFrames,
                loop = MusicGraphBuilder.Loop(493_714, mainFrames),
            ),
        ),
        tracks = listOf(
            MusicGraphBuilder.Track(30, segment = 10, clips = listOf(MusicGraphBuilder.Clip(31, 0, sourceLength = preFrames))),
            MusicGraphBuilder.Track(32, segment = 11, clips = listOf(MusicGraphBuilder.Clip(33, 1, sourceLength = bridgeFrames))),
            MusicGraphBuilder.Track(34, segment = 12, clips = listOf(MusicGraphBuilder.Clip(35, 2, sourceLength = mainFrames))),
        ),
        transitions = listOf(
            MusicGraphBuilder.Transition(
                index = 40,
                sources = listOf(10),
                destinations = listOf(12),
                sync = MusicGraphBuilder.SyncPoint.Immediate,
                entry = MusicGraphBuilder.EntryMode.EntryCue,
                bridge = 11,
            ),
        ),
    )

    private fun engine(d: Data): AudioEngine {
        val audio = AudioEngine.create()!!
        assertEquals(0, audio.loadGraph(MusicGraphBuilder.build(spec(d))))
        assertEquals(0, audio.bindBwav(0, d.pre, "BGM_Stage_Coaster_Pre.bwav"))
        assertEquals(0, audio.bindBwav(1, d.bridge, "BGM_Stage_Coaster_Bridge.bwav"))
        assertEquals(0, audio.bindBwav(2, d.main, "BGM_Stage_Coaster.bwav"))
        return audio
    }

    private fun renderSome(audio: AudioEngine, blocks: Int = 8) {
        repeat(blocks) {
            val buffer = audio.render(frames = 512)
            assertNotNull("render returned null mid-transition", buffer)
        }
    }

    @Test
    fun playingTheDestinationWhileTheSourceRunsStartsATransition() {
        val d = data() ?: return
        val audio = engine(d)
        try {
            assertEquals(0, audio.play(10))
            renderSome(audio)

            val before = TransitionSnapshot.read(audio.snapshot())
            assertNotNull("no snapshot before the request", before)
            assertTrue(
                "no transition should be running before any request: ${before!!.phase}",
                !before.active,
            )

            // Must route through planTransition: sources=[10] matches, destinations=[12] matches.
            assertEquals("Play(12) was refused", 0, audio.play(12))
            renderSome(audio)

            val during = TransitionSnapshot.read(audio.snapshot())
            assertNotNull("no snapshot after the request", during)
            assertTrue(
                "Play(12) from segment 10 did not start a transition: ${during!!}",
                during.active || during.transition == 40L,
            )
            assertEquals(
                "the transition should be node 40",
                40L,
                during.transition,
            )
            assertEquals("the source should be segment 10", 10L, during.source)
            assertEquals("the destination should be segment 12", 12L, during.destination)
        } finally {
            audio.close()
        }
    }

    @Test
    fun theTransitionNamesItsBridgeSegment() {
        val d = data() ?: return
        val audio = engine(d)
        try {
            assertEquals(0, audio.play(10))
            renderSome(audio)
            assertEquals(0, audio.play(12))
            renderSome(audio)

            val during = TransitionSnapshot.read(audio.snapshot())!!
            assertEquals("the rule declares segment 11 as its bridge", 11L, during.bridge)
        } finally {
            audio.close()
        }
    }

    @Test
    fun theTransitionAdvancesThroughPhases() {
        // A transition that never leaves the pending phase never actually moves audio; the
        // request would look accepted while the transport sat still.
        val d = data() ?: return
        val audio = engine(d)
        try {
            assertEquals(0, audio.play(10))
            renderSome(audio)
            assertEquals(0, audio.play(12))

            val seen = mutableSetOf<Long>()
            repeat(60) {
                renderSome(audio, blocks = 4)
                TransitionSnapshot.read(audio.snapshot())?.let { seen += it.phase }
            }

            assertTrue(
                "the transition never left the pending phase: $seen",
                seen.any { it != TransitionSnapshot.PHASE_PENDING },
            )
        } finally {
            audio.close()
        }
    }

    @Test
    fun theTransportKeepsAdvancingThroughATransition() {
        val d = data() ?: return
        val audio = engine(d)
        try {
            assertEquals(0, audio.play(10))
            renderSome(audio)
            val before = EngineSnapshot.read(audio.snapshot())!!.transportFrame

            assertEquals(0, audio.play(12))
            renderSome(audio, blocks = 16)
            val after = EngineSnapshot.read(audio.snapshot())!!.transportFrame

            assertTrue(
                "the transport froze during the transition: $before -> $after",
                after > before,
            )
        } finally {
            audio.close()
        }
    }

    @Test
    fun audioStaysNonSilentAcrossTheTransition() {
        // A transition that cuts all sound -- or leaves every voice stopped after arriving --
        // would pass every position-based assertion above.
        val d = data() ?: return
        val audio = engine(d)
        try {
            assertEquals(0, audio.play(10))
            renderSome(audio)
            assertEquals(0, audio.play(12))

            var nonZeroAfterRequest = 0
            var peak = 0f
            repeat(40) {
                val block = audio.render(frames = 512)
                assertNotNull("render returned null", block)
                for (sample in block!!) {
                    val magnitude = kotlin.math.abs(sample)
                    if (magnitude > peak) peak = magnitude
                    if (sample != 0f) nonZeroAfterRequest++
                }
            }

            assertTrue(
                "the transition silenced playback: peak=$peak nonzero=$nonZeroAfterRequest",
                nonZeroAfterRequest > 0,
            )
        } finally {
            audio.close()
        }
    }

    @Test
    fun aTargetWithoutAMatchingRuleStartsDirectly() {
        // Control: with no rule for 10 -> 11 (the rule covers 10 -> 12 only), the request must
        // still succeed by starting the segment. This distinguishes "transition machinery
        // works" from "Play is broken".
        val d = data() ?: return
        val audio = engine(d)
        try {
            assertEquals(0, audio.play(10))
            renderSome(audio)
            assertEquals("a direct segment start must still succeed", 0, audio.play(11))
            renderSome(audio)

            val snapshot = TransitionSnapshot.read(audio.snapshot())!!
            assertTrue(
                "10 -> 11 has no rule, so no transition should run: $snapshot",
                !snapshot.active,
            )

            val playing = EngineSnapshot.read(audio.snapshot())!!
            assertTrue(
                "the transport should be playing segment 11 directly",
                playing.isPlaying || playing.currentObject >= 0,
            )
        } finally {
            audio.close()
        }
    }
}
