package com.mylittleblover.mkwplayer.audio

import com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowAudioRendererTest {

    private class FakeRenderEngine(
        override val sampleRate: Int = 48_000,
        private val generator: (frameIndex: Long, outPair: FloatArray) -> Unit,
        private val segmentAtFrame: (Long) -> Int = { 100 },
    ) : SessionAudioEngine {
        var closed = false
        var outputGainCalls = mutableListOf<Pair<Double, Double>>()
        var playCalls = mutableListOf<Int>()
        var renderedTotal = 0L

        override fun nativeHandle(): Long = 99L
        override fun cancelPendingTransition(): Int = 0
        override fun loadGraph(packet: ByteArray): Int = AudioEngine.STATUS_OK
        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = AudioEngine.STATUS_OK
        override fun bindStreamedBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = AudioEngine.STATUS_OK
        override fun setOutputGain(gainDb: Double, ceilingDb: Double): Int {
            outputGainCalls += gainDb to ceilingDb
            return AudioEngine.STATUS_OK
        }
        override fun play(target: Int): Int {
            playCalls += target
            return AudioEngine.STATUS_OK
        }
        override fun stop(): Int = AudioEngine.STATUS_OK
        override fun pause(): Int = AudioEngine.STATUS_OK
        override fun resume(): Int = AudioEngine.STATUS_OK
        override fun seek(frame: Long): Int = AudioEngine.STATUS_OK
        override fun sourceSnapshot(slot: Int): LongArray? = null

        override fun renderInto(output: FloatArray, frames: Int): Int {
            val pair = FloatArray(2)
            for (i in 0 until frames) {
                generator(renderedTotal + i, pair)
                output[i * 2] = pair[0]
                output[i * 2 + 1] = pair[1]
            }
            renderedTotal += frames
            return AudioEngine.STATUS_OK
        }

        override fun snapshot(): LongArray = LongArray(65).also { arr ->
            arr[0] = EngineSnapshot.WIRE_VERSION
            arr[1] = renderedTotal
            arr[2] = EngineSnapshot.PLAYING
            arr[3] = 1L
            arr[11] = segmentAtFrame(renderedTotal).toLong()
        }

        override fun close() {
            closed = true
        }
    }

    private fun readPcm16Samples(wav: ByteArray): ShortArray {
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("fmt ", String(wav, 12, 4))
        assertEquals(16, buf.getInt(16))
        assertEquals(1.toShort(), buf.getShort(20)) // PCM
        assertEquals(2.toShort(), buf.getShort(22)) // stereo
        assertEquals(16.toShort(), buf.getShort(34)) // 16-bit
        assertEquals("data", String(wav, 36, 4))
        val dataBytes = buf.getInt(40)
        assertEquals(wav.size - 44, dataBytes)
        assertEquals(wav.size - 8, buf.getInt(4))
        val out = ShortArray(dataBytes / 2)
        for (i in out.indices) {
            out[i] = buf.getShort(44 + i * 2)
        }
        return out
    }

    @Test
    fun flatTrimProducesValidWavWithBitAccurateAmplitude() = runBlocking {
        val totalFrames = 12_000L
        val amp = 0.25f
        val engine = FakeRenderEngine(
            generator = { idx, out ->
                val s = (sin(2.0 * PI * 440.0 * idx / 48_000.0) * amp).toFloat()
                out[0] = s
                out[1] = -s
            },
        )
        val renderer = FlowAudioRenderer(createEngine = { engine }, ioDispatcher = Dispatchers.Unconfined)
        val spec = FlowAudioRenderer.CompiledFlowSpec(
            packet = byteArrayOf(1, 2, 3),
            assets = listOf(CourseMedleyCompiler.AssetSource.Bytes("a.bwav", ByteArray(32), byteArrayOf(9))),
            totalFrames = totalFrames,
            sampleRate = 48_000,
        )
        val progressSteps = mutableListOf<Float>()
        val baos = ByteArrayOutputStream()
        val result = renderer.renderFlowToWav(spec, baos, OutputTrim.Flat) { progressSteps += it }

        assertTrue(result is FlowAudioRenderer.RenderResult.Success)
        assertTrue(engine.closed)
        assertEquals(listOf(0.0 to 0.0), engine.outputGainCalls)
        assertEquals(totalFrames, result.framesRendered)
        assertEquals(44L + totalFrames * 4L, result.bytesWritten)
        assertEquals(1f, progressSteps.last())

        val samples = readPcm16Samples(baos.toByteArray())
        assertEquals(totalFrames.toInt() * 2, samples.size)
        var maxSample = 0
        for (s in samples) maxSample = maxOf(maxSample, abs(s.toInt()))
        val measuredAmp = maxSample / 32767.0
        assertEquals(amp.toDouble(), measuredAmp, 0.002)
    }

    @Test
    fun boostedTrimAppliesTenDbGainAndHoldsHotPeaksStrictlyUnderMinusOneDbfs() = runBlocking {
        // First half: quiet -24 dBFS sine -> boosted by +10 dB to ~-14 dBFS.
        // Second half: hot 0.60 / 0.30 asymmetric stereo -> +10 dB would hit 1.90 / 0.95, must limit strictly <= -1 dBFS (~0.8913) preserving L/R ratio!
        val totalFrames = 16_384L
        val quietAmp = 10.0.pow(-24.0 / 20.0).toFloat()
        val engine = FakeRenderEngine(
            generator = { idx, out ->
                if (idx < 8_192L) {
                    val s = (sin(2.0 * PI * 440.0 * idx / 48_000.0) * quietAmp).toFloat()
                    out[0] = s
                    out[1] = s
                } else {
                    out[0] = 0.60f
                    out[1] = 0.30f
                }
            },
        )
        val renderer = FlowAudioRenderer(createEngine = { engine }, ioDispatcher = Dispatchers.Unconfined)
        val spec = FlowAudioRenderer.CompiledFlowSpec(
            packet = byteArrayOf(1),
            assets = emptyList(),
            totalFrames = totalFrames,
            sampleRate = 48_000,
        )
        val baos = ByteArrayOutputStream()
        val result = renderer.renderFlowToWav(spec, baos, OutputTrim.Boosted)

        assertTrue(result is FlowAudioRenderer.RenderResult.Success)
        val ceilingLinear = 10.0.pow(OutputTrim.Boosted.ceilingDb / 20.0)
        assertTrue(result.peakLinear <= ceilingLinear.toFloat() + 1e-4f, "peak ${result.peakLinear} exceeded ceiling $ceilingLinear")
        assertTrue(result.limitedSamples > 0L)

        val samples = readPcm16Samples(baos.toByteArray())
        // Check quiet half RMS boosted by +10 dB (~3.162x).
        var sumSq = 0.0
        for (f in 0 until 8_192) {
            val v = samples[f * 2] / 32767.0
            sumSq += v * v
        }
        val measuredRms = sqrt(sumSq / 8_192.0)
        val expectedRms = quietAmp * 10.0.pow(10.0 / 20.0) / sqrt(2.0)
        assertEquals(expectedRms, measuredRms, 0.01)

        // Check hot half held under -1 dBFS ceiling AND preserves exact 2:1 stereo ratio!
        val hotL = samples[12_000 * 2] / 32767.0
        val hotR = samples[12_000 * 2 + 1] / 32767.0
        assertTrue(hotL <= ceilingLinear + 0.001, "hotL=$hotL exceeded ceiling $ceilingLinear")
        assertTrue(hotL > 0.75, "hotL=$hotL unexpectedly attenuated")
        assertEquals(0.5, hotR / hotL, 0.01)
    }

    @Test
    fun multiSegmentAutoAdvanceQueuesNextSegmentDuringOfflineRender() = runBlocking {
        val engine = FakeRenderEngine(
            generator = { _, out -> out[0] = 0.1f; out[1] = 0.1f },
            segmentAtFrame = { frame -> if (frame < 10_000L) 100 else 101 },
        )
        val renderer = FlowAudioRenderer(createEngine = { engine }, ioDispatcher = Dispatchers.Unconfined)
        val spec = FlowAudioRenderer.CompiledFlowSpec(
            packet = byteArrayOf(1),
            assets = emptyList(),
            totalFrames = 24_000L,
            sampleRate = 48_000,
            startSegment = 100,
            autoAdvance = mapOf(100 to 101, 101 to 102),
            prequeueInitial = true,
        )
        val result = renderer.renderFlowToWav(spec, ByteArrayOutputStream(), OutputTrim.Flat)
        assertTrue(result is FlowAudioRenderer.RenderResult.Success)
        assertEquals(listOf(100, 101, 102), engine.playCalls)
    }

    @Test
    fun cancellationStopsRenderAndClosesEngineCleanly() = runBlocking {
        val engine = FakeRenderEngine(generator = { _, out -> out[0] = 0.1f; out[1] = 0.1f })
        val renderer = FlowAudioRenderer(createEngine = { engine }, ioDispatcher = Dispatchers.Unconfined)
        val spec = FlowAudioRenderer.CompiledFlowSpec(
            packet = byteArrayOf(1),
            assets = emptyList(),
            totalFrames = 1_000_000L,
            sampleRate = 48_000,
        )
        assertFailsWith<CancellationException> {
            val job = launch {
                renderer.renderFlowToWav(spec, ByteArrayOutputStream(), OutputTrim.Flat) { progress ->
                    if (progress > 0f) cancel("user cancelled export")
                }
            }
            job.join()
            if (job.isCancelled) throw CancellationException("cancelled")
        }
        assertTrue(engine.closed, "native engine must always be closed on cancellation")
    }
}
