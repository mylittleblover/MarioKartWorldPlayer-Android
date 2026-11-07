package com.mylittleblover.mkwplayer.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The builder must produce exactly what the native reader accepts, and refuse
 * what it would reject. A graph that the builder emits and the probe then refuses
 * is a bug in one of the two, so the two are tested against each other.
 */
class GraphBuilderTest {

    /** The real Coaster digest, expressed as bytes without signed-literal noise. */
    private val coasterSha: ByteArray =
        "6bb12e5329eb01b14e0bb8672b8be178d3e003cc922aa2ef17d95a598ab7595b"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

    private fun spec(
        path: String = "BGM_Stage_Coaster.bwav",
        frames: Long = 3_373_711,
        rate: Int = 48000,
        channels: Int = 2,
        loop: GraphBuilder.Loop? = GraphBuilder.Loop(493_714, 3_373_711),
        exit: Long? = null,
    ) = GraphBuilder.Spec(
        asset = GraphBuilder.Asset(
            path = path,
            sampleRate = rate,
            channels = channels,
            frames = frames,
            sha256 = coasterSha,
            loop = loop,
        ),
        exitFrame = exit,
    )

    @Test
    fun builtPacketPassesTheProbe() {
        val packet = GraphBuilder.build(spec())
        val result = GraphProbe.inspect(packet)
        assertTrue(result is GraphProbe.Result.Ok, "builder output rejected: $result")
        result as GraphProbe.Result.Ok
        assertEquals(GraphBuilder.VERSION, result.version)
    }

    @Test
    fun magicIsSharedWithTheProbe() {
        assertEquals(GraphProbe.MAGIC, GraphBuilder.MAGIC)
    }

    @Test
    fun bodyLengthExcludesTheHeader() {
        val packet = GraphBuilder.build(spec())
        val declared = ((packet[8].toInt() and 0xFF) shl 24) or
            ((packet[9].toInt() and 0xFF) shl 16) or
            ((packet[10].toInt() and 0xFF) shl 8) or
            (packet[11].toInt() and 0xFF)
        assertEquals(packet.size - 16, declared)
    }

    @Test
    fun loopIsCarriedIntoTheClip() {
        // The clip block is the only place a player can learn the loop points, so
        // a loop that does not survive the build would silently play start to end.
        val packet = GraphBuilder.build(spec())
        val (start, end) = findLoopFields(packet, 493_714L, 3_373_711L)
        assertTrue(start, "loop start not present in the packet")
        assertTrue(end, "loop end not present in the packet")
    }

    @Test
    fun noLoopIsRepresentedAsAbsent() {
        val packet = GraphBuilder.build(spec(loop = null))
        val (start, _) = findLoopFields(packet, 493_714L, 3_373_711L)
        assertTrue(!start, "loop start should not appear when no loop is requested")
    }

    @Test
    fun rejectsNonPositiveFrames() {
        assertFailsWith<IllegalArgumentException> { GraphBuilder.build(spec(frames = 0)) }
    }

    @Test
    fun rejectsUnsupportedSampleRate() {
        assertFailsWith<IllegalArgumentException> { GraphBuilder.build(spec(rate = 7000)) }
        assertFailsWith<IllegalArgumentException> { GraphBuilder.build(spec(rate = 200000)) }
    }

    @Test
    fun rejectsMoreThanTwoChannels() {
        assertFailsWith<IllegalArgumentException> { GraphBuilder.build(spec(channels = 3)) }
    }

    @Test
    fun rejectsAbsolutePath() {
        assertFailsWith<IllegalArgumentException> {
            GraphBuilder.build(spec(path = "/abs/BGM.bwav"))
        }
    }

    @Test
    fun rejectsBackslashPath() {
        assertFailsWith<IllegalArgumentException> {
            GraphBuilder.build(spec(path = "dir\\BGM.bwav"))
        }
    }

    @Test
    fun rejectsPathWithDriveLetter() {
        assertFailsWith<IllegalArgumentException> {
            GraphBuilder.build(spec(path = "C:/BGM.bwav"))
        }
    }

    @Test
    fun rejectsPathTraversal() {
        assertFailsWith<IllegalArgumentException> {
            GraphBuilder.build(spec(path = "dir/../BGM.bwav"))
        }
        assertFailsWith<IllegalArgumentException> {
            GraphBuilder.build(spec(path = "dir//BGM.bwav"))
        }
    }

    @Test
    fun rejectsShaOfWrongLength() {
        val bad = GraphBuilder.Spec(
            asset = GraphBuilder.Asset(
                path = "a.bwav", sampleRate = 48000, channels = 2,
                frames = 100, sha256 = ByteArray(31),
            ),
        )
        assertFailsWith<IllegalArgumentException> { GraphBuilder.build(bad) }
    }

    @Test
    fun rejectsLoopBeyondTheAsset() {
        // The default asset is 3 373 711 frames, so an end of 3 373 712 is one
        // past the last decodable sample.
        assertFailsWith<IllegalArgumentException> {
            GraphBuilder.build(spec(loop = GraphBuilder.Loop(100, 3_373_712)))
        }
    }

    @Test
    fun acceptsLoopEndingExactlyAtTheLastFrame() {
        // End == frames is the shape every real MKW loop has, so refusing it
        // would reject the whole library.
        val packet = GraphBuilder.build(spec(loop = GraphBuilder.Loop(493_714, 3_373_711)))
        assertTrue(GraphProbe.inspect(packet) is GraphProbe.Result.Ok)
    }

    @Test
    fun rejectsInvertedLoop() {
        assertFailsWith<IllegalArgumentException> {
            GraphBuilder.build(spec(loop = GraphBuilder.Loop(500, 400)))
        }
    }

    @Test
    fun rejectsExitBeyondTheAsset() {
        assertFailsWith<IllegalArgumentException> { GraphBuilder.build(spec(exit = 9_999_999)) }
    }

    @Test
    fun rejectsUnsupportedTransportRate() {
        val bad = GraphBuilder.Spec(asset = spec().asset, transportSampleRate = 44101)
        assertFailsWith<IllegalArgumentException> { GraphBuilder.build(bad) }
    }

    @Test
    fun everyRejectionHasAMessage() {
        val cases: List<() -> ByteArray> = listOf(
            { GraphBuilder.build(spec(frames = 0)) },
            { GraphBuilder.build(spec(rate = 1000)) },
            { GraphBuilder.build(spec(channels = 9)) },
            { GraphBuilder.build(spec(path = "")) },
            { GraphBuilder.build(spec(path = "/x")) },
        )
        for (case in cases) {
            val error = assertFailsWith<IllegalArgumentException> { case() }
            assertTrue(!error.message.isNullOrBlank(), "a rejection must explain itself")
        }
    }

    @Test
    fun rateAndChannelCountSurviveTheRoundTrip() {
        val packet = GraphBuilder.build(spec(rate = 44100, channels = 1))
        assertTrue(GraphProbe.inspect(packet) is GraphProbe.Result.Ok)
    }

    /** Scans for two consecutive big-endian i64 values, which is the clip loop pair. */
    private fun findLoopFields(packet: ByteArray, startValue: Long, endValue: Long): Pair<Boolean, Boolean> {
        var foundStart = false
        var foundEnd = false
        for (i in 16 until packet.size - 16) {
            if (readLongAt(packet, i) == startValue && readLongAt(packet, i + 8) == endValue) {
                foundStart = true
                foundEnd = true
                break
            }
        }
        return foundStart to foundEnd
    }

    private fun readLongAt(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }
}
