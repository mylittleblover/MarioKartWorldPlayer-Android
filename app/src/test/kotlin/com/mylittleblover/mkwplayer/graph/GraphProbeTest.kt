package com.mylittleblover.mkwplayer.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The graph envelope is checked before any JNI call, so a wrong CRC or a wrong
 * length is reported precisely instead of as a generic InvalidGraph from native.
 */
class GraphProbeTest {

    private fun fixture(): ByteArray {
        val text = javaClass.classLoader!!
            .getResourceAsStream("mkw-coaster-graph-v6.hex")
            ?.bufferedReader()?.readText()
            ?: error("mkw-coaster-graph-v6.hex missing from test resources")
        return text.trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    @Test
    fun acceptsTheCommittedFixture() {
        val result = GraphProbe.inspect(fixture())
        assertTrue(result is GraphProbe.Result.Ok, "fixture rejected: $result")
        result as GraphProbe.Result.Ok
        assertEquals(6, result.version)
        assertEquals(557, result.bodyLength)
    }

    @Test
    fun rejectsShortPacket() {
        val result = GraphProbe.inspect(ByteArray(8))
        assertTrue(result is GraphProbe.Result.Rejected)
    }

    @Test
    fun rejectsWrongMagic() {
        val packet = fixture()
        packet[0] = 0
        val result = GraphProbe.inspect(packet)
        assertTrue(result is GraphProbe.Result.Rejected)
        assertTrue((result as GraphProbe.Result.Rejected).reason.contains("magic"))
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val packet = fixture()
        packet[7] = 99          // version is the last byte of the big-endian i32
        val result = GraphProbe.inspect(packet)
        assertTrue(result is GraphProbe.Result.Rejected)
        assertTrue((result as GraphProbe.Result.Rejected).reason.contains("version"))
    }

    @Test
    fun rejectsBodyLengthThatDoesNotMatchThePacket() {
        val packet = fixture()
        packet[11] = (packet[11] + 1).toByte()
        val result = GraphProbe.inspect(packet)
        assertTrue(result is GraphProbe.Result.Rejected)
        assertTrue((result as GraphProbe.Result.Rejected).reason.contains("declared body"))
    }

    @Test
    fun rejectsCorruptedBody() {
        val packet = fixture()
        val at = packet.size - 1
        packet[at] = (packet[at].toInt() xor 0x01).toByte()
        val result = GraphProbe.inspect(packet)
        assertTrue(result is GraphProbe.Result.Rejected)
        assertTrue((result as GraphProbe.Result.Rejected).reason.contains("crc"))
    }

    @Test
    fun rejectsOversizedPacket() {
        val result = GraphProbe.inspect(ByteArray(GraphProbe.MAX_PACKET_BYTES + 1))
        assertTrue(result is GraphProbe.Result.Rejected)
        assertTrue((result as GraphProbe.Result.Rejected).reason.contains("limit"))
    }

    @Test
    fun versionRangeMatchesTheNativeReader() {
        // GraphReader accepts 1..13 today; widening this without widening the
        // native reader would let a packet through the probe and fail in native.
        assertEquals(1, GraphProbe.SUPPORTED_VERSIONS.first)
        assertEquals(13, GraphProbe.SUPPORTED_VERSIONS.last)
    }

    @Test
    fun magicIsTheOneTheReaderExpects() {
        assertEquals(0x50324731L, GraphProbe.MAGIC)
    }

    @Test
    fun rejectionReasonIsNeverEmpty() {
        val cases = listOf(
            ByteArray(0),
            ByteArray(4),
            ByteArray(64),
        )
        for (packet in cases) {
            val result = GraphProbe.inspect(packet)
            assertTrue(result is GraphProbe.Result.Rejected)
            assertFalse((result as GraphProbe.Result.Rejected).reason.isBlank())
        }
    }
}
