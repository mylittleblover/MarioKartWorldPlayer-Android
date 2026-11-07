package com.mylittleblover.mkwplayer.graph

/**
 * Reads the header of a compiled playback graph without the native engine.
 *
 * Purpose: fail early and specifically. The native reader rejects a bad packet
 * with a single `InvalidGraph`, so a wrong CRC and a wrong field layout look
 * identical. Checking the three header facts a caller can get wrong -- magic,
 * body length, CRC32 -- turns that into a precise message before a JNI call.
 *
 * This validates the envelope only. It does not parse the graph, by design: a
 * second parser would drift from the native one. Whatever this accepts, the
 * engine still has to accept.
 */
object GraphProbe {

    const val MAGIC = 0x50324731L  // "P2G1" big-endian

    /** The graph reader caps a packet at 4 MiB plus its 16-byte header. */
    const val MAX_PACKET_BYTES = 4 * 1024 * 1024 + 16

    private const val HEADER_BYTES = 16

    sealed interface Result {
        data class Ok(val version: Int, val bodyLength: Int) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Checks magic, declared body length and CRC32.
     *
     * Field order in the wire format is magic, version, body length, checksum,
     * then the body, all big-endian. The CRC covers the body only.
     */
    fun inspect(packet: ByteArray): Result {
        if (packet.size < HEADER_BYTES) {
            return Result.Rejected("packet is ${packet.size} bytes, header needs $HEADER_BYTES")
        }
        if (packet.size > MAX_PACKET_BYTES) {
            return Result.Rejected("packet is ${packet.size} bytes, limit is $MAX_PACKET_BYTES")
        }

        val magic = readU32(packet, 0)
        if (magic != MAGIC) {
            return Result.Rejected(
                "bad magic 0x${magic.toString(16)}; expected 0x${MAGIC.toString(16)}",
            )
        }

        val version = readI32(packet, 4)
        if (version !in SUPPORTED_VERSIONS) {
            return Result.Rejected("unsupported graph version $version")
        }

        val bodyLength = readI32(packet, 8)
        if (bodyLength != packet.size - HEADER_BYTES) {
            return Result.Rejected(
                "declared body $bodyLength does not match ${packet.size - HEADER_BYTES} present",
            )
        }

        val expected = readU32(packet, 12)
        val actual = crc32(packet, HEADER_BYTES, bodyLength)
        if (actual != expected) {
            return Result.Rejected(
                "crc mismatch: stored 0x${expected.toString(16)}, computed 0x${actual.toString(16)}",
            )
        }

        return Result.Ok(version = version, bodyLength = bodyLength)
    }

    /** Versions the native reader accepts, from GraphProtocolIds.hpp. */
    val SUPPORTED_VERSIONS: IntRange = 1..13

    private fun readU32(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 4) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }

    private fun readI32(bytes: ByteArray, offset: Int): Int = readU32(bytes, offset).toInt()

    private fun crc32(bytes: ByteArray, offset: Int, length: Int): Long {
        var crc = 0xFFFFFFFFL
        for (i in offset until offset + length) {
            crc = crc xor (bytes[i].toLong() and 0xFF)
            for (bit in 0 until 8) {
                crc = if (crc and 1L != 0L) (crc ushr 1) xor 0xEDB88320L else crc ushr 1
            }
        }
        return crc.inv() and 0xFFFFFFFFL
    }
}
