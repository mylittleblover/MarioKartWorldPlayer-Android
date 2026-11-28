package com.mylittleblover.mkwplayer.audio

import com.mylittleblover.mkwplayer.interactive.CourseMedleyChainCompiler
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler
import com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Offline, faster-than-realtime flow-to-WAV audio renderer.
 *
 * Uses a dedicated isolated native [SessionAudioEngine] (`renderEngineControl`) so offline export
 * never interferes with live playback, produces zero underruns (synchronous page decode), supports
 * coroutine cancellation and progress reporting, and streams standard 16-bit stereo PCM `.wav`
 * directly to any [OutputStream] (working seamlessly with both [File] and Android SAF URIs).
 *
 * Honors [OutputTrim]: when [OutputTrim.Boosted] (+10 dB / -1 dBFS ceiling) is enabled in settings,
 * lifts the rendered mix by +10 dB and applies a stereo-linked smooth soft-knee brickwall limiter
 * held strictly under the ceiling so exported WAV files never harshly clip.
 */
internal class FlowAudioRenderer(
    private val createEngine: () -> SessionAudioEngine? = { AudioEngine.create() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val renderMutex = Mutex()

    data class CompiledFlowSpec(
        val packet: ByteArray,
        val assets: List<CourseMedleyCompiler.AssetSource>,
        val totalFrames: Long,
        val sampleRate: Int = 48_000,
        val startSegment: Int = 100,
        val autoAdvance: Map<Int, Int> = emptyMap(),
        val prequeueInitial: Boolean = autoAdvance.isNotEmpty(),
        val initialLayerGains: Map<Int, Double> = emptyMap(),
    ) {
        companion object {
            fun from(compiled: CourseMedleyCompiler.CompiledFileLinearGraph, sampleRate: Int = 48_000): CompiledFlowSpec =
                CompiledFlowSpec(
                    packet = compiled.packet,
                    assets = compiled.assets,
                    totalFrames = compiled.totalFrames,
                    sampleRate = sampleRate,
                )

            fun from(compiled: ExtraInteractiveCompilers.CompiledLinearGraph, sampleRate: Int = 48_000): CompiledFlowSpec =
                CompiledFlowSpec(
                    packet = compiled.packet,
                    assets = compiled.assets.map { (path, bytes) ->
                        CourseMedleyCompiler.AssetSource.Bytes(path = path, sha256 = ByteArray(0), bytes = bytes)
                    },
                    totalFrames = compiled.totalFrames,
                    sampleRate = sampleRate,
                )

            fun from(chain: CourseMedleyChainCompiler.CompiledFileChain): CompiledFlowSpec {
                val total = chain.windows.lastOrNull()?.let { last ->
                    last.originTransportFrame + last.displayTotalFrames
                } ?: chain.firstTotalFrames
                return CompiledFlowSpec(
                    packet = chain.packet,
                    assets = chain.assets,
                    totalFrames = total,
                    sampleRate = chain.sampleRate,
                    startSegment = chain.windows.firstOrNull()?.segmentId ?: 100,
                    autoAdvance = chain.autoAdvance,
                    prequeueInitial = chain.autoAdvance.isNotEmpty(),
                )
            }

            fun from(chain: CourseMedleyChainCompiler.CompiledChain): CompiledFlowSpec {
                val total = chain.windows.lastOrNull()?.let { last ->
                    last.originTransportFrame + last.displayTotalFrames
                } ?: chain.firstTotalFrames
                return CompiledFlowSpec(
                    packet = chain.packet,
                    assets = chain.assets.map { (path, bytes) ->
                        CourseMedleyCompiler.AssetSource.Bytes(path = path, sha256 = ByteArray(0), bytes = bytes)
                    },
                    totalFrames = total,
                    sampleRate = chain.sampleRate,
                    startSegment = chain.windows.firstOrNull()?.segmentId ?: 100,
                    autoAdvance = chain.autoAdvance,
                    prequeueInitial = chain.autoAdvance.isNotEmpty(),
                )
            }
        }
    }

    data class LinearFileSpec(
        val file: File,
        val assetId: String,
        val frames: Long,
        val sampleRate: Int = 48_000,
        val loopStart: Long? = null,
        val loopEnd: Long? = null,
        val hasLoop: Boolean = false,
        val loopConfig: NonInteractiveLoopConfig = NonInteractiveLoopConfig.Default,
    )

    sealed interface RenderResult {
        data class Success(
            val framesRendered: Long,
            val sampleRate: Int,
            val bytesWritten: Long,
            val peakLinear: Float,
            val limitedSamples: Long,
        ) : RenderResult

        data object Cancelled : RenderResult

        data class Failed(val reason: String) : RenderResult
    }

    /**
     * Renders a compiled flow graph offline as a 16-bit stereo PCM `.wav` stream into [outputStream].
     * Does not close [outputStream]; the caller retains stream ownership.
     */
    suspend fun renderFlowToWav(
        spec: CompiledFlowSpec,
        outputStream: OutputStream,
        outputTrim: OutputTrim = OutputTrim.default,
        onProgress: (Float) -> Unit = {},
    ): RenderResult = withContext(ioDispatcher) {
        if (spec.totalFrames <= 0L) return@withContext RenderResult.Failed("Flow has zero duration.")
        if (spec.sampleRate !in 8_000..192_000) {
            return@withContext RenderResult.Failed("Unsupported sample rate: ${spec.sampleRate}")
        }
        renderMutex.withLock {
            val engine = createEngine()
                ?: return@withLock RenderResult.Failed("Out of memory creating offline audio engine.")
            try {
                currentCoroutineContext().ensureActive()
                val loaded = engine.loadGraph(spec.packet)
                if (loaded != AudioEngine.STATUS_OK) {
                    return@withLock RenderResult.Failed("Could not load flow graph: ${describeStatus(loaded)}")
                }
                for ((index, asset) in spec.assets.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val status = when (asset) {
                        is CourseMedleyCompiler.AssetSource.DiskFile ->
                            engine.bindBwavFile(
                                assetIndex = index,
                                file = asset.file,
                                assetId = asset.path,
                                streamed = true,
                                expectedSha256 = asset.sha256.takeIf { it.size == 32 },
                            )
                        is CourseMedleyCompiler.AssetSource.Bytes ->
                            engine.bindStreamedBwav(index, asset.bytes, asset.path)
                    }
                    if (status != AudioEngine.STATUS_OK) {
                        return@withLock RenderResult.Failed("Could not bind '${asset.path}': ${describeStatus(status)}")
                    }
                }

                // Request unity/flat gain from the native engine (overriding its -1 dB hard-clamp default)
                // so our stereo-linked smooth soft-knee brickwall limiter owns OutputTrim cleanly.
                engine.setOutputGain(0.0, 0.0)

                val started = engine.play(spec.startSegment)
                if (started != AudioEngine.STATUS_OK) {
                    return@withLock RenderResult.Failed("Could not start flow segment ${spec.startSegment}: ${describeStatus(started)}")
                }
                var queuedFromSegment: Int? = null
                if (spec.prequeueInitial) {
                    spec.autoAdvance[spec.startSegment]?.let { nextSeg ->
                        val q = engine.play(nextSeg)
                        if (q == AudioEngine.STATUS_OK) {
                            queuedFromSegment = spec.startSegment
                        }
                    }
                }
                for ((trackIndex, gainDb) in spec.initialLayerGains) {
                    val status = engine.setLayerGain(trackIndex, gainDb, fadeMs = 0)
                    if (status != AudioEngine.STATUS_OK) {
                        return@withLock RenderResult.Failed("Could not set layer gain for track $trackIndex: ${describeStatus(status)}")
                    }
                }

                renderEngineToWav(
                    engine = engine,
                    totalFrames = spec.totalFrames,
                    sampleRate = engine.sampleRate.takeIf { it in 8_000..192_000 } ?: spec.sampleRate,
                    outputStream = outputStream,
                    outputTrim = outputTrim,
                    autoAdvance = spec.autoAdvance,
                    initialQueuedSource = queuedFromSegment,
                    onProgress = onProgress,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Throwable) {
                RenderResult.Failed(e.message ?: "Offline render failed.")
            } finally {
                engine.close()
            }
        }
    }

    /**
     * Convenience helper that renders [spec] into [targetFile] atomically (writing to a sibling `.tmp`
     * file and renaming only on success, removing the temp file on failure or cancellation).
     */
    suspend fun renderFlowToFile(
        spec: CompiledFlowSpec,
        targetFile: File,
        outputTrim: OutputTrim = OutputTrim.default,
        onProgress: (Float) -> Unit = {},
    ): RenderResult = withContext(ioDispatcher) {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            val result = FileOutputStream(tempFile).use { fos ->
                renderFlowToWav(spec, fos, outputTrim, onProgress)
            }
            if (result is RenderResult.Success) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } else {
                tempFile.delete()
            }
            result
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            throw cancelled
        } catch (e: Throwable) {
            tempFile.delete()
            RenderResult.Failed(e.message ?: "Could not write WAV file.")
        }
    }

    /**
     * Renders a standalone linear BWAV file (with optional non-interactive loop + 10s fadeout policy)
     * offline as a 16-bit stereo PCM `.wav` into [outputStream].
     */
    suspend fun renderLinearFileToWav(
        spec: LinearFileSpec,
        outputStream: OutputStream,
        outputTrim: OutputTrim = OutputTrim.default,
        onProgress: (Float) -> Unit = {},
    ): RenderResult = withContext(ioDispatcher) {
        if (spec.frames <= 0L) return@withContext RenderResult.Failed("Track has zero duration.")
        val timing = NonInteractiveLoopPolicy.computeEffectiveFrames(
            baseFrames = spec.frames,
            loopStart = spec.loopStart,
            loopEnd = spec.loopEnd,
            hasLoop = spec.hasLoop,
            sampleRate = spec.sampleRate,
            config = spec.loopConfig,
        )
        renderMutex.withLock {
            val engine = createEngine()
                ?: return@withLock RenderResult.Failed("Out of memory creating offline audio engine.")
            try {
                currentCoroutineContext().ensureActive()
                val loaded = engine.loadLinearFile(
                    file = spec.file,
                    assetId = spec.assetId,
                    loop = timing.usesLoopFade,
                    streamed = true,
                )
                if (loaded != AudioEngine.STATUS_OK) {
                    return@withLock RenderResult.Failed("Could not open audio file: ${describeStatus(loaded)}")
                }
                if (timing.usesLoopFade && spec.loopStart != null && spec.loopEnd != null) {
                    val rep = engine.setLinearRepeat(true, spec.loopStart, spec.loopEnd)
                    if (rep != AudioEngine.STATUS_OK) {
                        return@withLock RenderResult.Failed("Could not configure loop points: ${describeStatus(rep)}")
                    }
                    val fade = engine.setLinearFadeWindow(timing.totalFrames, timing.fadeOutFrames)
                    if (fade != AudioEngine.STATUS_OK) {
                        return@withLock RenderResult.Failed("Could not configure fadeout window: ${describeStatus(fade)}")
                    }
                }
                engine.setOutputGain(0.0, 0.0)
                val started = engine.play(AudioEngine.PLAY_LINEAR)
                if (started != AudioEngine.STATUS_OK) {
                    return@withLock RenderResult.Failed("Could not start track render: ${describeStatus(started)}")
                }
                renderEngineToWav(
                    engine = engine,
                    totalFrames = timing.totalFrames,
                    sampleRate = engine.sampleRate.takeIf { it in 8_000..192_000 } ?: spec.sampleRate,
                    outputStream = outputStream,
                    outputTrim = outputTrim,
                    autoAdvance = emptyMap(),
                    initialQueuedSource = null,
                    onProgress = onProgress,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Throwable) {
                RenderResult.Failed(e.message ?: "Offline render failed.")
            } finally {
                engine.close()
            }
        }
    }

    private suspend fun renderEngineToWav(
        engine: SessionAudioEngine,
        totalFrames: Long,
        sampleRate: Int,
        outputStream: OutputStream,
        outputTrim: OutputTrim,
        autoAdvance: Map<Int, Int>,
        initialQueuedSource: Int?,
        onProgress: (Float) -> Unit,
    ): RenderResult {
        val maxWavFrames = (0xFFFF_FFFFL - WAV_HEADER_BYTES) / STEREO_PCM16_FRAME_BYTES
        if (totalFrames > maxWavFrames) {
            return RenderResult.Failed("Audio exceeds maximum 4 GiB RIFF/WAV limit.")
        }
        val buffered = BufferedOutputStream(outputStream, IO_BUFFER_BYTES)
        val header = buildWavHeader(totalFrames, sampleRate)
        buffered.write(header)

        val floatChunk = FloatArray(RENDER_CHUNK_FRAMES * 2)
        val byteChunk = ByteArray(RENDER_CHUNK_FRAMES * STEREO_PCM16_FRAME_BYTES)
        val limiter = SoftPeakLimiter(sampleRate = sampleRate, outputTrim = outputTrim)

        var renderedFrames = 0L
        var queuedSource = initialQueuedSource
        var lastReportedProgress = -1f
        onProgress(0f)

        while (renderedFrames < totalFrames) {
            currentCoroutineContext().ensureActive()
            val framesToRender = minOf(RENDER_CHUNK_FRAMES.toLong(), totalFrames - renderedFrames).toInt()
            val status = engine.renderInto(floatChunk, framesToRender)
            if (status != AudioEngine.STATUS_OK) {
                return RenderResult.Failed("Native offline render failed: ${describeStatus(status)}")
            }

            limiter.processToPcm16Le(floatChunk, byteChunk, framesToRender)
            buffered.write(byteChunk, 0, framesToRender * STEREO_PCM16_FRAME_BYTES)
            renderedFrames += framesToRender

            if (autoAdvance.isNotEmpty() && renderedFrames < totalFrames) {
                val snap = engine.snapshot()
                if (snap != null && snap.size >= 25 && snap[0] == EngineSnapshot.WIRE_VERSION) {
                    val seg = snap[11].toInt()
                    val transitioning = snap[24] != 0L
                    if (seg >= 0 && !transitioning && seg != queuedSource) {
                        autoAdvance[seg]?.let { target ->
                            val q = engine.play(target)
                            if (q == AudioEngine.STATUS_OK || q == AudioEngine.STATUS_DEADLINE_MISS) {
                                queuedSource = seg
                            }
                        }
                    }
                }
            }

            val progress = (renderedFrames.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)
            if (progress - lastReportedProgress >= 0.005f || renderedFrames == totalFrames) {
                lastReportedProgress = progress
                onProgress(progress)
            }
        }

        buffered.flush()
        val bytesWritten = WAV_HEADER_BYTES.toLong() + renderedFrames * STEREO_PCM16_FRAME_BYTES
        return RenderResult.Success(
            framesRendered = renderedFrames,
            sampleRate = sampleRate,
            bytesWritten = bytesWritten,
            peakLinear = limiter.maxOutputPeak,
            limitedSamples = limiter.limitedSamples,
        )
    }

    /**
     * Stereo-linked OutputTrim gain + smooth envelope & C² soft-knee brickwall peak limiter.
     *
     * Guarantees:
     * - Exact linear gain `10^(gainDb/20)` below the limiter knee.
     * - When [OutputTrim.limits] is true (e.g. +10 dB / -1 dBFS ceiling), stereo-linked attack/release
     *   compression + C² soft-knee saturation holds peaks strictly below `10^(ceilingDb/20)` without
     *   harsh hard-clipping artifacts or stereo image shift.
     * - When [OutputTrim.Flat] (`gainDb == 0, !limits`), signals <= 0.985 pass bit-accurately with
     *   soft brickwall protection strictly under 1.0 full scale.
     */
    internal class SoftPeakLimiter(
        sampleRate: Int,
        outputTrim: OutputTrim,
    ) {
        private val linearGain: Double = 10.0.pow(outputTrim.gainDb / 20.0)
        private val ceiling: Double = if (outputTrim.limits) {
            10.0.pow(outputTrim.ceilingDb / 20.0).coerceIn(0.05, 0.999)
        } else {
            0.999
        }
        private val knee: Double = if (outputTrim.limits) ceiling * 0.88 else 0.985
        private val headroom: Double = (ceiling - knee).coerceAtLeast(1e-4)
        private val releaseCoeff: Double = exp(-1.0 / (0.040 * sampleRate.coerceAtLeast(8_000)))
        private var envelopeGain: Double = 1.0

        var maxOutputPeak: Float = 0f
            private set
        var limitedSamples: Long = 0L
            private set

        fun processToPcm16Le(stereoFloat: FloatArray, outBytes: ByteArray, frames: Int) {
            var outOffset = 0
            for (i in 0 until frames) {
                val rawL = stereoFloat[i * 2]
                val rawR = stereoFloat[i * 2 + 1]
                var l = if (rawL.isFinite()) rawL.toDouble() * linearGain else 0.0
                var r = if (rawR.isFinite()) rawR.toDouble() * linearGain else 0.0

                val peak = max(abs(l), abs(r))
                val targetGain = if (peak > knee) {
                    val shaped = knee + headroom * tanh((peak - knee) / headroom)
                    shaped / peak
                } else {
                    1.0
                }
                envelopeGain = if (targetGain < envelopeGain) {
                    // Instant attack protects transients from overshooting the ceiling.
                    targetGain
                } else {
                    targetGain + (envelopeGain - targetGain) * releaseCoeff
                }
                if (envelopeGain < 0.999999) {
                    l *= envelopeGain
                    r *= envelopeGain
                    limitedSamples += 2L
                }
                // Final stereo-linked brickwall guarantee strictly <= ceiling.
                val postPeak = max(abs(l), abs(r))
                if (postPeak > ceiling) {
                    val clampRatio = ceiling / postPeak
                    l *= clampRatio
                    r *= clampRatio
                }

                val outPeak = max(abs(l), abs(r)).toFloat()
                if (outPeak > maxOutputPeak) maxOutputPeak = outPeak

                val sL = (l * 32767.0).roundToInt().coerceIn(-32768, 32767)
                val sR = (r * 32767.0).roundToInt().coerceIn(-32768, 32767)
                outBytes[outOffset++] = (sL and 0xFF).toByte()
                outBytes[outOffset++] = ((sL shr 8) and 0xFF).toByte()
                outBytes[outOffset++] = (sR and 0xFF).toByte()
                outBytes[outOffset++] = ((sR shr 8) and 0xFF).toByte()
            }
        }
    }

    companion object {
        const val RENDER_CHUNK_FRAMES = 8192
        const val WAV_HEADER_BYTES = 44
        const val STEREO_PCM16_FRAME_BYTES = 4
        private const val IO_BUFFER_BYTES = 64 * 1024

        fun buildWavHeader(totalFrames: Long, sampleRate: Int): ByteArray {
            val dataBytes = totalFrames * STEREO_PCM16_FRAME_BYTES
            val riffChunkSize = 36L + dataBytes
            val byteRate = sampleRate * STEREO_PCM16_FRAME_BYTES
            val buf = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            buf.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
            buf.putInt(riffChunkSize.toInt())
            buf.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())
            buf.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
            buf.putInt(16) // PCM fmt chunk size
            buf.putShort(1) // PCM format tag
            buf.putShort(2) // Stereo
            buf.putInt(sampleRate)
            buf.putInt(byteRate)
            buf.putShort(STEREO_PCM16_FRAME_BYTES.toShort())
            buf.putShort(16) // 16-bit PCM
            buf.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
            buf.putInt(dataBytes.toInt())
            return buf.array()
        }

        private fun describeStatus(status: Int): String =
            AudioStatusText.engineHint(status) ?: AudioStatusText.engine(status)
    }
}
