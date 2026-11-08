package com.mylittleblover.mkwplayer.graph

/**
 * Compiled-graph wire primitives shared by the builders.
 *
 * The format is fixed by `engine/native/music/GraphReader.cpp`: big-endian integers,
 * CRC32 over the body only, and a body that starts with the transport sample rate. Every
 * writer here mirrors one reader call, in order.
 */
internal object GraphWire {

    /** "P2G1" big-endian. */
    const val MAGIC: Long = 0x50324731L

    /** Envelope header the agent reader checks: 7 property words. */
    const val TEMPO_FIELD_COUNT = 7

    /** Defaults from rtpc/PropertyRegistry.hpp, in property id order. */
    val DEFAULT_PROPERTIES = doubleArrayOf(
        0.0,        // VolumeDb
        1.0,        // PlaybackSpeed
        20000.0,    // LowPassHz
        20.0,       // HighPassHz
        0.0,        // ReverbWet
        0.0,        // Pan
        0.0,        // Bypass
    )

    fun writeU32(out: java.io.ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    fun writeI32(out: java.io.ByteArrayOutputStream, value: Int) =
        writeU32(out, value.toLong() and 0xFFFFFFFFL)

    /**
     * Writes one byte.
     *
     * Named `writeByteValue` because an extension named `writeByte` is not resolvable on
     * `OutputStream`: `write(int)` already exists and wins.
     */
    fun writeByteValue(out: java.io.ByteArrayOutputStream, value: Int) = out.write(value and 0xFF)

    fun writeLong(out: java.io.ByteArrayOutputStream, value: Long) {
        for (shift in 56 downTo 0 step 8) {
            out.write(((value ushr shift) and 0xFF).toInt())
        }
    }

    fun writeDouble(out: java.io.ByteArrayOutputStream, value: Double) =
        writeLong(out, java.lang.Double.doubleToRawLongBits(value))

    fun writeString(out: java.io.ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeI32(out, bytes.size)
        out.write(bytes)
    }

    fun writeStringList(out: java.io.ByteArrayOutputStream, values: List<String>) {
        writeI32(out, values.size)
        values.forEach { writeString(out, it) }
    }

    /** Nullable string: -1 length means absent. */
    fun writeNullableString(out: java.io.ByteArrayOutputStream, value: String?) {
        if (value == null) {
            writeI32(out, -1)
        } else {
            writeString(out, value)
        }
    }

    fun writeIndexList(out: java.io.ByteArrayOutputStream, values: List<Int>) {
        writeI32(out, values.size)
        values.forEach { writeI32(out, it) }
    }

    fun writeNode(
        out: java.io.ByteArrayOutputStream,
        index: Int,
        kind: Int,
        parent: Int,
        bpm: Double,
        numerator: Int,
        denominator: Int,
        grid: Double,
        gridOffset: Double,
        properties: DoubleArray = DEFAULT_PROPERTIES,
    ) {
        writeI32(out, index)
        writeI32(out, kind)
        writeI32(out, parent)
        writeDouble(out, bpm)
        writeI32(out, numerator)
        writeI32(out, denominator)
        writeDouble(out, grid)
        writeDouble(out, gridOffset)
        writeI32(out, TEMPO_FIELD_COUNT)
        properties.forEach { writeDouble(out, it) }
        writeU32(out, 0)   // configuredProperties: every property at its default
    }

    fun writeClip(
        out: java.io.ByteArrayOutputStream,
        index: Int,
        asset: Int,
        start: Long,
        sourceOffset: Long,
        sourceLength: Long,
        gainDb: Double,
        fadeIn: Long,
        fadeOut: Long,
        loop: CompiledLoop?,
    ) {
        writeI32(out, index)
        writeI32(out, asset)
        writeLong(out, start)
        writeLong(out, sourceOffset)
        writeLong(out, sourceLength)
        writeDouble(out, gainDb)
        writeLong(out, fadeIn)
        writeLong(out, fadeOut)
        if (loop == null) {
            writeByteValue(out, 0)
        } else {
            writeByteValue(out, 1)
            writeLong(out, loop.start)
            writeLong(out, loop.end)
            writeI32(out, loop.count)
        }
    }

    data class CompiledLoop(val start: Long, val end: Long, val count: Int)

    /** CRC32 matching the reader: standard polynomial, body bytes only. */
    fun crc32(bytes: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (byte in bytes) {
            crc = crc xor (byte.toLong() and 0xFF)
            for (bit in 0 until 8) {
                crc = if (crc and 1L != 0L) (crc ushr 1) xor 0xEDB88320L else crc ushr 1
            }
        }
        return crc.inv() and 0xFFFFFFFFL
    }

    /** Wraps a body in the magic/version/length/CRC envelope. */
    fun envelope(body: ByteArray, version: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(body.size + 16)
        writeU32(out, MAGIC)
        writeI32(out, version)
        writeI32(out, body.size)
        writeU32(out, crc32(body))
        out.write(body)
        return out.toByteArray()
    }
}
