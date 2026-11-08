package com.mylittleblover.mkwplayer.graph

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The multi-segment builder.
 *
 * Two kinds of test here, and both matter:
 *
 *  - the enum wire values, pinned against `GraphProtocolIds.hpp`. If the engine renumbers an
 *    enum, a graph would decode into a different musical meaning rather than fail.
 *  - the single-segment equivalence. Building the already-verified Coaster graph through the
 *    general writer and comparing byte for byte proves the new path is not a second dialect.
 */
class MusicGraphBuilderTest {

    private fun sha(seed: Int): ByteArray = ByteArray(32) { (seed + it).toByte() }

    private fun fixture(): ByteArray {
        val text = javaClass.classLoader!!
            .getResourceAsStream("mkw-coaster-graph-v6.hex")
            ?.bufferedReader()?.readText()
            ?: error("mkw-coaster-graph-v6.hex missing from test resources")
        return text.trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /** The Coaster track exactly as the verified single-asset builder emits it. */
    private fun coasterSpec(): MusicGraphBuilder.Spec = MusicGraphBuilder.Spec(
        assets = listOf(
            MusicGraphBuilder.Asset(
                path = "BGM_Stage_Coaster.bwav",
                sampleRate = 48000,
                channels = 2,
                frames = 3_373_711,
                sha256 = "6bb12e5329eb01b14e0bb8672b8be178d3e003cc922aa2ef17d95a598ab7595b"
                    .chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            ),
        ),
        segments = listOf(
            MusicGraphBuilder.Segment(index = 1, entry = 0, exit = 3_373_711),
        ),
        tracks = listOf(
            MusicGraphBuilder.Track(
                index = 2,
                segment = 1,
                clips = listOf(
                    MusicGraphBuilder.Clip(
                        index = 3,
                        asset = 0,
                        sourceLength = 3_373_711,
                        loop = MusicGraphBuilder.Loop(493_714, 3_373_711),
                    ),
                ),
            ),
        ),
        // The verified packet names its identities after the parts, not after their indices.
        identities = listOf("asset", "segment", "track", "clip"),
    )

    // ---------------------------------------------------------------- equivalence

    @Test
    fun theSingleSegmentCaseReproducesTheVerifiedFixtureExactly() {
        // The strongest check available without a device: the general writer must produce the
        // same bytes as the builder whose output the native engine already accepted and
        // played. Anything else means one of the two is wrong.
        val expected = fixture()
        val actual = MusicGraphBuilder.build(coasterSpec())

        assertEquals(expected.size, actual.size, "packet length differs")
        assertContentEquals(expected, actual)
    }

    @Test
    fun theSingleSegmentCaseAlsoMatchesTheOlderBuilderDirectly() {
        val direct = GraphBuilder.build(
            GraphBuilder.Spec(
                asset = GraphBuilder.Asset(
                    path = "BGM_Stage_Coaster.bwav",
                    sampleRate = 48000,
                    channels = 2,
                    frames = 3_373_711,
                    sha256 = "6bb12e5329eb01b14e0bb8672b8be178d3e003cc922aa2ef17d95a598ab7595b"
                        .chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                    loop = GraphBuilder.Loop(493_714, 3_373_711),
                ),
            ),
        )
        assertContentEquals(direct, MusicGraphBuilder.build(coasterSpec()))
    }

    // ---------------------------------------------------------------- envelope

    @Test
    fun thePacketPassesTheEnvelopeProbe() {
        val packet = MusicGraphBuilder.build(coasterSpec())
        val result = GraphProbe.inspect(packet)
        assertTrue(result is GraphProbe.Result.Ok, "got $result")
        assertEquals(MusicGraphBuilder.VERSION, (result as GraphProbe.Result.Ok).version)
    }

    // ---------------------------------------------------------------- wire values

    @Test
    fun enumWireValuesMatchTheEngine() {
        // From GraphProtocolIds.hpp. A silent renumbering would change musical meaning.
        assertEquals(1, MusicGraphBuilder.Kind.Segment.wire)
        assertEquals(2, MusicGraphBuilder.Kind.Track.wire)
        assertEquals(9, MusicGraphBuilder.Kind.Transition.wire)

        assertEquals(0, MusicGraphBuilder.TrackMode.Normal.wire)
        assertEquals(1, MusicGraphBuilder.TrackMode.RandomStep.wire)
        assertEquals(2, MusicGraphBuilder.TrackMode.SequenceStep.wire)
        assertEquals(3, MusicGraphBuilder.TrackMode.Switch.wire)

        assertEquals(0, MusicGraphBuilder.CueKind.Custom.wire)
        assertEquals(1, MusicGraphBuilder.CueKind.Event.wire)

        assertEquals(0, MusicGraphBuilder.SyncPoint.Immediate.wire)
        assertEquals(1, MusicGraphBuilder.SyncPoint.NextBeat.wire)
        assertEquals(2, MusicGraphBuilder.SyncPoint.NextBar.wire)
        assertEquals(3, MusicGraphBuilder.SyncPoint.NextGrid.wire)
        assertEquals(4, MusicGraphBuilder.SyncPoint.ExitCue.wire)
        assertEquals(5, MusicGraphBuilder.SyncPoint.NextCue.wire)
        assertEquals(6, MusicGraphBuilder.SyncPoint.NextCustomCue.wire)

        assertEquals(0, MusicGraphBuilder.EntryMode.EntryCue.wire)
        assertEquals(1, MusicGraphBuilder.EntryMode.SameTime.wire)
        assertEquals(2, MusicGraphBuilder.EntryMode.RandomCue.wire)
        assertEquals(3, MusicGraphBuilder.EntryMode.RandomCustomCue.wire)
        assertEquals(4, MusicGraphBuilder.EntryMode.LastExit.wire)
        assertEquals(5, MusicGraphBuilder.EntryMode.PlaylistLast.wire)
        assertEquals(6, MusicGraphBuilder.EntryMode.PlaylistNext.wire)
        assertEquals(7, MusicGraphBuilder.EntryMode.PlaylistJump.wire)

        assertEquals(0, MusicGraphBuilder.Shape.Linear.wire)
        assertEquals(1, MusicGraphBuilder.Shape.Hold.wire)
        assertEquals(2, MusicGraphBuilder.Shape.SCurve.wire)
        assertEquals(3, MusicGraphBuilder.Shape.EaseIn.wire)
        assertEquals(4, MusicGraphBuilder.Shape.EaseOut.wire)

        assertEquals(0, MusicGraphBuilder.EndBehavior.Natural.wire)
        assertEquals(1, MusicGraphBuilder.EndBehavior.Immediate.wire)
        assertEquals(2, MusicGraphBuilder.EndBehavior.Fade.wire)
        assertEquals(3, MusicGraphBuilder.EndBehavior.Ending.wire)
    }

    // ---------------------------------------------------------------- multi segment

    /** Pre → Bridge → Main, the shape the Coaster prototype needs. */
    private fun threeSegmentSpec(
        transitions: List<MusicGraphBuilder.Transition> = emptyList(),
        musicalLoop: MusicGraphBuilder.MusicalLoop? = null,
    ): MusicGraphBuilder.Spec = MusicGraphBuilder.Spec(
        assets = listOf(
            MusicGraphBuilder.Asset("BGM_Stage_Coaster_Pre.bwav", 48000, 2, 2_427_426, sha(1)),
            MusicGraphBuilder.Asset("BGM_Stage_Coaster_Bridge.bwav", 48000, 2, 88_320, sha(2)),
            MusicGraphBuilder.Asset("BGM_Stage_Coaster.bwav", 48000, 2, 3_373_711, sha(3)),
        ),
        segments = listOf(
            MusicGraphBuilder.Segment(index = 10, entry = 0, exit = 2_427_426),
            MusicGraphBuilder.Segment(index = 11, entry = 0, exit = 88_320),
            MusicGraphBuilder.Segment(
                index = 12,
                entry = 0,
                exit = 3_373_711,
                loop = MusicGraphBuilder.Loop(493_714, 3_373_711),
                cues = listOf(
                    MusicGraphBuilder.Cue(20, "Bridge", 883_200, MusicGraphBuilder.CueKind.Custom),
                    MusicGraphBuilder.Cue(21, "Outro", 2_427_426),
                ),
            ),
        ),
        tracks = listOf(
            MusicGraphBuilder.Track(30, segment = 10, clips = listOf(MusicGraphBuilder.Clip(31, asset = 0, sourceLength = 2_427_426))),
            MusicGraphBuilder.Track(32, segment = 11, clips = listOf(MusicGraphBuilder.Clip(33, asset = 1, sourceLength = 88_320))),
            MusicGraphBuilder.Track(34, segment = 12, clips = listOf(MusicGraphBuilder.Clip(35, asset = 2, sourceLength = 3_373_711))),
        ),
        transitions = transitions,
        musicalLoop = musicalLoop,
    )

    @Test
    fun threeSegmentsProduceOneValidPacket() {
        val packet = MusicGraphBuilder.build(threeSegmentSpec())
        val result = GraphProbe.inspect(packet)
        assertTrue(result is GraphProbe.Result.Ok, "got $result")
    }

    @Test
    fun aLargerGraphIsLargerThanTheSingleSegmentOne() {
        val single = MusicGraphBuilder.build(coasterSpec())
        val multi = MusicGraphBuilder.build(threeSegmentSpec())
        assertTrue(
            multi.size > single.size,
            "three segments and three assets must produce a bigger packet: " +
                "${multi.size} vs ${single.size}",
        )
    }

    @Test
    fun transitionsAndMusicalLoopAreAccepted() {
        val spec = threeSegmentSpec(
            transitions = listOf(
                MusicGraphBuilder.Transition(
                    index = 40,
                    sources = listOf(10),
                    destinations = listOf(12),
                    sync = MusicGraphBuilder.SyncPoint.ExitCue,
                    entry = MusicGraphBuilder.EntryMode.EntryCue,
                    bridge = 11,
                    preEntry = true,
                    postExit = true,
                    fadeOut = MusicGraphBuilder.Fade(2.0, 0.0, MusicGraphBuilder.Shape.SCurve),
                    fadeIn = MusicGraphBuilder.Fade(2.0, 0.0, MusicGraphBuilder.Shape.SCurve),
                ),
            ),
            musicalLoop = MusicGraphBuilder.MusicalLoop(
                loop = 12,
                repeats = 2,
                intro = 10,
                ending = 11,
            ),
        )
        val result = GraphProbe.inspect(MusicGraphBuilder.build(spec))
        assertTrue(result is GraphProbe.Result.Ok, "got $result")
    }

    // ---------------------------------------------------------------- rejections

    @Test
    fun aSegmentExitThatDoesNotExceedItsEntryIsRejected() {
        val spec = threeSegmentSpec().copy(
            segments = listOf(MusicGraphBuilder.Segment(index = 10, entry = 100, exit = 100)),
            tracks = listOf(
                MusicGraphBuilder.Track(30, segment = 10, clips = listOf(MusicGraphBuilder.Clip(31, 0, sourceLength = 10))),
            ),
        )
        assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
    }

    @Test
    fun aSegmentLoopOutsideItsOwnBoundsIsRejected() {
        val spec = threeSegmentSpec().copy(
            segments = listOf(
                MusicGraphBuilder.Segment(
                    index = 10,
                    entry = 0,
                    exit = 1000,
                    loop = MusicGraphBuilder.Loop(2000, 3000),
                ),
            ),
            tracks = listOf(
                MusicGraphBuilder.Track(30, segment = 10, clips = listOf(MusicGraphBuilder.Clip(31, 0, sourceLength = 1000))),
            ),
        )
        val error = assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
        // Either bound can be the one that fails first; what matters is that it is named.
        assertTrue(error.message!!.contains("loop"), error.message!!)
    }

    @Test
    fun aClipLoopOutsideItsSourceWindowIsRejected() {
        val spec = threeSegmentSpec().copy(
            tracks = listOf(
                MusicGraphBuilder.Track(
                    30,
                    segment = 10,
                    clips = listOf(
                        MusicGraphBuilder.Clip(
                            index = 31,
                            asset = 0,
                            sourceLength = 1000,
                            loop = MusicGraphBuilder.Loop(0, 5000),
                        ),
                    ),
                ),
            ),
        )
        val error = assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
        assertTrue(error.message!!.contains("source window"), error.message!!)
    }

    @Test
    fun aTrackNamingAMissingSegmentIsRejected() {
        val spec = threeSegmentSpec().copy(
            tracks = listOf(
                MusicGraphBuilder.Track(30, segment = 99, clips = listOf(MusicGraphBuilder.Clip(31, 0, sourceLength = 10))),
            ),
        )
        val error = assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
        assertTrue(error.message!!.contains("not in the graph"), error.message!!)
    }

    @Test
    fun duplicateNodeIndicesAreRejected() {
        val spec = threeSegmentSpec().copy(
            tracks = listOf(
                MusicGraphBuilder.Track(10, segment = 10, clips = listOf(MusicGraphBuilder.Clip(31, 0, sourceLength = 10))),
            ),
        )
        val error = assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
        assertTrue(error.message!!.contains("unique"), error.message!!)
    }

    @Test
    fun aMusicalLoopNamingAMissingSegmentIsRejected() {
        val spec = threeSegmentSpec(
            musicalLoop = MusicGraphBuilder.MusicalLoop(loop = 99, repeats = 2),
        )
        assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
    }

    @Test
    fun aMusicalLoopWithZeroRepeatsIsRejected() {
        val spec = threeSegmentSpec(
            musicalLoop = MusicGraphBuilder.MusicalLoop(loop = 12, repeats = 0),
        )
        assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
    }

    @Test
    fun anAbsoluteAssetPathIsRejected() {
        val spec = threeSegmentSpec().copy(
            assets = listOf(MusicGraphBuilder.Asset("/abs.bwav", 48000, 2, 100, sha(1))),
        )
        assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
    }

    @Test
    fun aTraversalAssetPathIsRejected() {
        val spec = threeSegmentSpec().copy(
            assets = listOf(MusicGraphBuilder.Asset("a/../b.bwav", 48000, 2, 100, sha(1))),
        )
        assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
    }

    @Test
    fun aBadTransportRateIsRejected() {
        val spec = threeSegmentSpec().copy(transportSampleRate = 22050)
        assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
    }

    @Test
    fun aCueSyncWithoutACueNameIsRejected() {
        val spec = threeSegmentSpec(
            transitions = listOf(
                MusicGraphBuilder.Transition(
                    index = 40,
                    sources = listOf(10),
                    destinations = listOf(12),
                    sync = MusicGraphBuilder.SyncPoint.NextCustomCue,
                ),
            ),
        )
        val error = assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
        assertTrue(error.message!!.contains("custom cue"), error.message!!)
    }

    @Test
    fun anIdentityTableTooShortForItsIndicesIsRejected() {
        val spec = threeSegmentSpec().copy(identities = listOf("a", "b"))
        val error = assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
        assertTrue(error.message!!.contains("identity"), error.message!!)
    }

    @Test
    fun anEmptyGraphIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            MusicGraphBuilder.build(
                MusicGraphBuilder.Spec(assets = emptyList(), segments = emptyList(), tracks = emptyList()),
            )
        }
    }

    @Test
    fun aTrackWithoutClipsIsRejected() {
        val spec = threeSegmentSpec().copy(
            tracks = listOf(MusicGraphBuilder.Track(30, segment = 10, clips = emptyList())),
        )
        assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
    }

    @Test
    fun aDenominatorTheEngineDoesNotAcceptIsRejected() {
        val spec = threeSegmentSpec().copy(denominator = 3)
        assertFailsWith<IllegalArgumentException> { MusicGraphBuilder.build(spec) }
    }

    @Test
    fun aBpmOutsideTheEngineRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            MusicGraphBuilder.build(threeSegmentSpec().copy(bpm = 900.0))
        }
    }
}
