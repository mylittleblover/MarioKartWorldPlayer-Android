package com.mylittleblover.mkwplayer.graph

import java.io.ByteArrayOutputStream

/**
 * Builds a minimal compiled playback graph for a single audio asset.
 *
 * The wire format is the one the native reader actually parses
 * (engine/native/music/GraphReader.cpp), verified field by field against the
 * engine in the device tests: magic, version, body length, CRC32 over the body
 * only, then a big-endian body starting with the transport sample rate.
 *
 * This produces the smallest graph that can play one file. It is not an authoring
 * tool: there is one segment, one track, one clip and one asset, because that is
 * all a single-file player needs. Anything richer belongs in the authoring path,
 * which does not exist yet.
 */
object GraphBuilder {

    /** Format version to emit. v6 is the lowest version that carries a digest. */
    const val VERSION = 6

    /** Node kinds, from GraphProtocolIds.hpp. */
    private const val KIND_SEGMENT = 1
    private const val KIND_TRACK = 2

    /** TrackMode.Normal */
    private const val TRACK_MODE_NORMAL = 0

    /** Tempo descriptor count sentinel the reader expects after the tempo fields. */
    private const val TEMPO_FIELD_COUNT = 7

    /** Defaults from rtpc/PropertyRegistry.hpp, in property id order. */
    private val DEFAULT_PROPERTIES = doubleArrayOf(
        0.0,        // VolumeDb
        1.0,        // PlaybackSpeed
        20000.0,    // LowPassHz
        20.0,       // HighPassHz
        0.0,        // ReverbWet
        0.0,        // Pan
        0.0,        // Bypass
    )

    data class Asset(
        val path: String,
        val sampleRate: Int,
        val channels: Int,
        val frames: Long,
        /** 32 raw bytes of SHA-256, exactly as the graph carries it. */
        val sha256: ByteArray,
        val loop: Loop? = null,
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    data class Loop(val startFrame: Long, val endFrame: Long, val count: Int = -1)

    data class Spec(
        val asset: Asset,
        val transportSampleRate: Int = 48000,
        val bpm: Double = 140.0,
        /** Segment exit frame; defaults to the asset length. */
        val exitFrame: Long? = null,
    )

    /**
     * Emits the packet.
     *
     * Rejects inputs the native reader would reject, so a caller gets a specific
     * message instead of a generic InvalidGraph after a JNI round trip.
     */
    fun build(spec: Spec): ByteArray {
        val asset = spec.asset
        require(asset.frames > 0) { "asset frames must be positive, got ${asset.frames}" }
        require(asset.sampleRate in 8000..192000) { "unsupported sample rate ${asset.sampleRate}" }
        require(asset.channels in 1..2) { "unsupported channel count ${asset.channels}" }
        require(asset.sha256.size == 32) { "sha256 must be 32 bytes, got ${asset.sha256.size}" }
        require(spec.transportSampleRate in intArrayOf(44100, 48000, 96000)) {
            "transport rate must be 44100, 48000 or 96000, got ${spec.transportSampleRate}"
        }
        require(asset.path.isNotEmpty()) { "asset path must not be empty" }
        require(!asset.path.startsWith("/")) { "asset path must be relative: ${asset.path}" }
        require(!asset.path.contains('\\')) { "asset path must use forward slashes: ${asset.path}" }
        require(!asset.path.contains(':')) { "asset path must not contain a drive or scheme" }
        require(asset.path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "asset path must not contain empty, '.' or '..' parts: ${asset.path}"
        }

        val exit = spec.exitFrame ?: asset.frames
        require(exit > 0) { "segment exit must be positive, got $exit" }
        require(exit <= asset.frames) {
            "segment exit $exit exceeds asset length ${asset.frames}"
        }
        asset.loop?.let { loop ->
            require(loop.startFrame >= 0 && loop.endFrame > loop.startFrame) {
                "loop must satisfy 0 <= start < end, got ${loop.startFrame}..${loop.endFrame}"
            }
            require(loop.endFrame <= asset.frames) {
                "loop end ${loop.endFrame} exceeds asset length ${asset.frames}"
            }
            require(loop.count >= -1) { "loop count must be >= -1, got ${loop.count}" }
        }

        val body = ByteArrayOutputStream(512)
        body.writeI32(spec.transportSampleRate)

        // Identities: every index in the graph must name one of these.
        body.writeStringList(listOf("asset", "segment", "track", "clip"))

        // Nodes: the segment and its track, with the track parented to the segment.
        val props = DEFAULT_PROPERTIES
        body.writeI32(2)
        body.writeNode(index = 1, kind = KIND_SEGMENT, parent = -1, bpm = spec.bpm, properties = props)
        body.writeNode(index = 2, kind = KIND_TRACK, parent = 1, bpm = spec.bpm, properties = props)

        // One segment covering the whole asset.
        body.writeI32(1)
        body.writeI32(1)              // index
        body.writeLong(0)             // entry
        body.writeLong(exit)          // exit
        body.writeByteValue(0)             // hasLoop (segment-level loop unused here)
        body.writeI32(0)              // cue count

        // One track with one clip.
        body.writeI32(1)
        body.writeI32(2)              // index
        body.writeI32(1)              // segment
        body.writeI32(TRACK_MODE_NORMAL)
        body.writeI32(-1)             // group
        body.writeI32(-1)             // bus
        body.writeLong(1)             // seed
        body.writeI32(0)              // avoidRepeat
        body.writeByteValue(0)             // reset
        body.writeI32(1)              // clip count
        body.writeClip(index = 3, asset = 0, frames = asset.frames, loop = asset.loop)
        body.writeI32(0)              // subtrack count
        body.writeI32(0)              // send count

        // Empty top-level collections, in reader order.
        repeat(9) { body.writeI32(0) }   // playlists, switches, groups, parameters,
                                         // events, transitions, curves, stingers, buses

        // One asset.
        body.writeI32(1)
        body.writeI32(0)                 // index
        body.writeString(asset.path)
        body.writeI32(asset.sampleRate)
        body.writeI32(asset.channels)
        body.writeLong(asset.frames)
        body.writeLong(0)                // lookAhead
        body.write(asset.sha256)         // v3 digest
        body.writeByteValue(0)                // v4 hasSource = false

        body.writeI32(0)                 // entries
        body.writeI32(0)                 // performances
        body.writeByteValue(0)                // hasMusicalLoop
        body.writeByteValue(0)                // hasAutomatic

        val payload = body.toByteArray()
        val crc = crc32(payload)

        val packet = ByteArrayOutputStream(payload.size + 16)
        packet.writeU32(MAGIC)
        packet.writeI32(VERSION)
        packet.writeI32(payload.size)
        packet.writeU32(crc)
        packet.write(payload)
        return packet.toByteArray()
    }

    /** "P2G1" big-endian, the same constant GraphProbe checks. */
    const val MAGIC: Long = 0x50324731L

    // ---------------------------------------------------------------- writers

    private fun ByteArrayOutputStream.writeU32(value: Long) {
        write(((value ushr 24) and 0xFF).toInt())
        write(((value ushr 16) and 0xFF).toInt())
        write(((value ushr 8) and 0xFF).toInt())
        write((value and 0xFF).toInt())
    }

    private fun ByteArrayOutputStream.writeI32(value: Int) = writeU32(value.toLong() and 0xFFFFFFFFL)

    /**
     * Named to avoid colliding with `OutputStream.write(int)`, which already
     * writes one byte; an extension named `writeByte` is not resolvable there.
     */
    private fun ByteArrayOutputStream.writeByteValue(value: Int) = write(value and 0xFF)

    private fun ByteArrayOutputStream.writeLong(value: Long) {
        for (shift in 56 downTo 0 step 8) {
            write(((value ushr shift) and 0xFF).toInt())
        }
    }

    private fun ByteArrayOutputStream.writeDouble(value: Double) =
        writeLong(java.lang.Double.doubleToRawLongBits(value))

    private fun ByteArrayOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeI32(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeStringList(values: List<String>) {
        writeI32(values.size)
        values.forEach { writeString(it) }
    }

    private fun ByteArrayOutputStream.writeNode(
        index: Int,
        kind: Int,
        parent: Int,
        bpm: Double,
        properties: DoubleArray,
    ) {
        writeI32(index)
        writeI32(kind)
        writeI32(parent)
        writeDouble(bpm)
        writeI32(4)                  // numerator
        writeI32(4)                  // denominator
        writeDouble(1.0)             // grid
        writeDouble(0.0)             // gridOffset
        writeI32(TEMPO_FIELD_COUNT)
        properties.forEach { writeDouble(it) }
        writeU32(0)                  // configuredProperties: all defaults
    }

    private fun ByteArrayOutputStream.writeClip(
        index: Int,
        asset: Int,
        frames: Long,
        loop: Loop?,
    ) {
        writeI32(index)
        writeI32(asset)
        writeLong(0)                 // start
        writeLong(0)                 // sourceOffset
        writeLong(frames)            // sourceLength
        writeDouble(0.0)             // gainDb
        writeLong(0)                 // fadeIn
        writeLong(0)                 // fadeOut
        if (loop == null) {
            writeByteValue(0)
        } else {
            writeByteValue(1)
            writeLong(loop.startFrame)
            writeLong(loop.endFrame)
            writeI32(loop.count)
        }
    }

    /** CRC32 matching the native reader: standard polynomial, body bytes only. */
    private fun crc32(bytes: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (byte in bytes) {
            crc = crc xor (byte.toLong() and 0xFF)
            for (bit in 0 until 8) {
                crc = if (crc and 1L != 0L) (crc ushr 1) xor 0xEDB88320L else crc ushr 1
            }
        }
        return crc.inv() and 0xFFFFFFFFL
    }
}
