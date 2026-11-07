package com.mylittleblover.mkwplayer.audio

import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.mkwplayer.graph.GraphProbe

/**
 * Turns a file's bytes into a running engine, or explains why it cannot.
 *
 * This is the only place the three pieces are joined: [BwavHeader] reads the
 * container, [GraphBuilder] emits a graph, [AudioEngine] loads and plays it.
 * Keeping the join in one function means the UI never assembles audio state
 * itself, and every failure has a single shape.
 *
 * Order matters and is deliberate:
 *  1. read the header -- cheap, and refuses formats before any allocation
 *  2. check the codec -- refuses kind 2 with a specific reason before building
 *  3. build the graph -- pure computation, no native call
 *  4. probe the graph -- catches envelope mistakes before JNI
 *  5. create the engine and load -- the first native step
 *
 * Steps 1-4 need no engine, so a bad file never allocates one.
 */
internal object TrackOpener {

    /** Play target for a graph holding one segment, which the builder always emits. */
    const val SINGLE_SEGMENT = 1

    sealed interface Result {
        /** The engine is running. The caller owns [engine] and must close it. */
        data class Playing(
            val engine: AudioEngine,
            val sampleRate: Int,
            val frames: Int,
            val loopStart: Int?,
            val loopEnd: Int?,
        ) : Result

        /** Nothing was allocated; the reason is suitable for display. */
        data class Refused(val reason: String) : Result
    }

    /**
     * Prepares and starts playback of one BWAV.
     *
     * @param assetId the name the graph records for this asset; must match what
     *   the caller passes to the binding step, or the engine reports a mismatch
     * @param transportSampleRate the graph's transport rate
     */
    fun open(
        assetId: String,
        bytes: ByteArray,
        transportSampleRate: Int = 48000,
    ): Result {
        val header = when (val read = BwavHeader.read(bytes)) {
            is BwavHeader.Result.Rejected -> return Result.Refused(read.reason)
            is BwavHeader.Result.Ok -> read.info
        }

        val playability = com.mylittleblover.mkwplayer.library.Playability.evaluate(header.codecKind)
        if (!playability.playable) {
            return Result.Refused(playability.reason ?: "This entry cannot be played.")
        }

        val packet = try {
            GraphBuilder.build(
                GraphBuilder.Spec(
                    asset = GraphBuilder.Asset(
                        path = assetId,
                        sampleRate = header.sampleRate,
                        channels = header.channels,
                        frames = header.frames.toLong(),
                        sha256 = sha256(bytes),
                        loop = if (header.hasLoop) {
                            GraphBuilder.Loop(
                                startFrame = header.loopStart!!.toLong(),
                                endFrame = header.loopEnd!!.toLong(),
                            )
                        } else {
                            null
                        },
                    ),
                    transportSampleRate = transportSampleRate,
                ),
            )
        } catch (e: IllegalArgumentException) {
            return Result.Refused("Cannot build a graph for this file: ${e.message}")
        }

        val probed = GraphProbe.inspect(packet)
        if (probed is GraphProbe.Result.Rejected) {
            return Result.Refused("Internal graph check failed: ${probed.reason}")
        }

        val engine = AudioEngine.create() ?: return Result.Refused("Out of memory creating the engine.")
        try {
            val loaded = engine.loadGraph(packet)
            if (loaded != AudioEngine.STATUS_OK) {
                engine.close()
                return Result.Refused(
                    AudioStatusText.engineHint(loaded) ?: "The engine rejected the graph (${AudioStatusText.engine(loaded)}).",
                )
            }

            val bound = engine.bindBwav(assetIndex = 0, bytes = bytes, assetId = assetId)
            if (bound != AudioEngine.STATUS_OK) {
                engine.close()
                return Result.Refused(
                    AudioStatusText.engineHint(bound) ?: "The engine could not bind the file (${AudioStatusText.engine(bound)}).",
                )
            }

            val played = engine.play(SINGLE_SEGMENT)
            if (played != AudioEngine.STATUS_OK) {
                engine.close()
                return Result.Refused(
                    AudioStatusText.engineHint(played) ?: "The engine could not start (${AudioStatusText.engine(played)}).",
                )
            }

            return Result.Playing(
                engine = engine,
                sampleRate = header.sampleRate,
                frames = header.frames,
                loopStart = header.loopStart,
                loopEnd = header.loopEnd,
            )
        } catch (e: Throwable) {
            engine.close()
            throw e
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
}
