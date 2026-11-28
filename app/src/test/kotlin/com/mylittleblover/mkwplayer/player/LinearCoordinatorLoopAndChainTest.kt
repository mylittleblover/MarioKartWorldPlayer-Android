package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig
import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinearCoordinatorLoopAndChainTest {

    private fun writeSyntheticLoopingBwav(
        dir: File,
        sha256: String,
        frames: Int,
        loopStart: Int,
        loopEnd: Int,
        sampleRate: Int = 48_000,
    ): File {
        val audioDir = File(dir, "audio").apply { mkdirs() }
        val file = File(audioDir, sha256)
        val channels = 2
        val headerSize = 16 + channels * 76
        val dataStart = (headerSize + 63) and 63.inv()
        val bytes = ByteArray(dataStart + channels * 64)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        buf.putShort(4, 0xFEFF.toShort())
        buf.putShort(6, 1)
        buf.putShort(14, channels.toShort())
        for (ch in 0 until channels) {
            val h = 16 + ch * 76
            buf.putShort(h + 0, 1) // DSP-ADPCM (decodable)
            buf.putShort(h + 2, ch.toShort())
            buf.putInt(h + 4, sampleRate)
            buf.putInt(h + 8, frames)
            buf.putInt(h + 12, frames)
            buf.putInt(h + 48, dataStart + ch * 64)
            buf.putInt(h + 52, dataStart + ch * 64)
            buf.putInt(h + 56, 1)
            buf.putInt(h + 60, loopEnd)
            buf.putInt(h + 64, loopStart)
        }
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun loopingTrackStartsWithOneLoopPlusTenSecFadeAndUpdatesLiveAcrossConfigAndRepeatToggle() {
        val tempDir = createTempDir("mkw-loop-test")
        try {
            val sr = 48_000
            val baseFrames = 60 * sr       // 60s
            val loopStart = 5 * sr         // 5s
            val loopEnd = 60 * sr          // 55s loop body
            val sha = "a".repeat(64)
            writeSyntheticLoopingBwav(tempDir, sha, baseFrames, loopStart, loopEnd, sr)

            val fadeWindows = mutableListOf<Pair<Long, Long>>()
            val seekCalls = mutableListOf<Long>()
            var engineCreations = 0
            var fakeTransportFrame = 20L * sr
            var fakeSourceFrame = 20L * sr

            val session = PlaybackSession(
                createEngine = {
                    engineCreations++
                    object : SessionAudioEngine {
                        override val sampleRate: Int = sr
                        override fun nativeHandle(): Long = engineCreations.toLong()
                        override fun cancelPendingTransition(): Int = 0
                        override fun loadGraph(packet: ByteArray): Int = 0
                        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = 0
                        override fun loadLinearFile(file: File, assetId: String, loop: Boolean, streamed: Boolean): Int =
                            AudioEngine.STATUS_OK
                        override fun setLinearRepeat(enabled: Boolean, begin: Long, end: Long): Int =
                            AudioEngine.STATUS_OK
                        override fun setLinearFadeWindow(endFrame: Long, fadeOutFrames: Long): Int {
                            fadeWindows += endFrame to fadeOutFrames
                            return AudioEngine.STATUS_OK
                        }
                        override fun play(target: Int): Int = AudioEngine.STATUS_OK
                        override fun stop(): Int = AudioEngine.STATUS_OK
                        override fun pause(): Int = AudioEngine.STATUS_OK
                        override fun resume(): Int = AudioEngine.STATUS_OK
                        override fun seek(frame: Long): Int {
                            seekCalls += frame
                            fakeTransportFrame = frame
                            return AudioEngine.STATUS_OK
                        }
                        override fun sourceSnapshot(slot: Int): LongArray = LongArray(11).also {
                            it[1] = 1L
                            it[10] = fakeSourceFrame
                        }
                        override fun snapshot(): LongArray = LongArray(65).also {
                            it[0] = EngineSnapshot.WIRE_VERSION
                            it[1] = fakeTransportFrame
                            it[2] = EngineSnapshot.PLAYING
                            it[3] = 1L
                        }
                        override fun close() {}
                    }
                },
                openOutput = { _, _ -> null },
            )

            val coordinator = LinearPlaybackCoordinator(
                sessionProvider = { session },
                mainDispatcher = Dispatchers.Unconfined,
                ioDispatcher = Dispatchers.Unconfined,
            )

            val entry = LinearQueue.Entry(
                key = "extra:course_select",
                title = "Course Select",
                assetSha256 = sha,
                loop = true,
                loopStart = loopStart.toLong(),
                loopEnd = loopEnd.toLong(),
            )
            coordinator.startExtraQueue(
                entries = listOf(entry),
                startIndex = 0,
                packDir = tempDir,
                repeatMode = LinearQueue.RepeatMode.LIST,
            )

            // Default: 60s + 1 * 55s + 10s = 125s
            val expectedDefaultTotal = 125L * sr
            assertEquals(1, engineCreations)
            assertTrue(coordinator.state.value.isLoopingTrack)
            assertEquals(expectedDefaultTotal, coordinator.state.value.totalFrames)
            assertEquals(expectedDefaultTotal, session.state.value.totalFrames)
            assertEquals(expectedDefaultTotal to 10L * sr, fadeWindows.last())

            // Live update to 3 loops + 10s = 60 + 3*55 + 10 = 235s (NO engine recreation, NO seek!)
            coordinator.setNonInteractiveLoopConfig(NonInteractiveLoopConfig.byLoopCount(3))
            val expectedThreeLoops = 235L * sr
            assertEquals(1, engineCreations, "live config update must not recreate engine")
            assertEquals(expectedThreeLoops, coordinator.state.value.totalFrames)
            assertEquals(expectedThreeLoops, session.state.value.totalFrames)
            assertEquals(expectedThreeLoops to 10L * sr, fadeWindows.last())
            assertTrue(seekCalls.isEmpty())

            // Live update to target duration 5:00 (300s) with 10s fadeout:
            coordinator.setNonInteractiveLoopConfig(NonInteractiveLoopConfig.byTargetDurationMs(300_000L))
            assertEquals(300L * sr, coordinator.state.value.totalFrames)
            assertEquals(300L * sr to 10L * sr, fadeWindows.last())

            // Toggle RepeatMode.LIST -> SINGLE (infinite loop at loop points, display base 60s):
            coordinator.cycleRepeatMode()
            assertEquals(LinearQueue.RepeatMode.SINGLE, coordinator.queue.repeatMode)
            assertEquals(baseFrames.toLong(), coordinator.state.value.totalFrames)
            assertEquals(-1L to 0L, fadeWindows.last())

            // Simulate looping in Repeat-One for 10 minutes (transport = 600s > 300s target), source at 25s:
            fakeTransportFrame = 600L * sr
            fakeSourceFrame = 25L * sr

            // Toggle RepeatMode.SINGLE -> OFF: must re-enable 300s+10s fade AND rebase transport to sourcePos (25s)!
            coordinator.cycleRepeatMode()
            assertEquals(LinearQueue.RepeatMode.OFF, coordinator.queue.repeatMode)
            assertEquals(300L * sr, coordinator.state.value.totalFrames)
            assertEquals(300L * sr to 10L * sr, fadeWindows.last())
            assertEquals(listOf(25L * sr), seekCalls)
            assertEquals(1, engineCreations)

            coordinator.clearSession()
            session.release()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun survivalRallySeekSucceedsAfterNaturalAdvanceAndManualSkipNext() {
        val tempDir = createTempDir("mkw-rally-seek-test")
        try {
            val sr = 48_000
            val stopFrames = 150 * sr // 2m30s per stop -> 6 stops = 15m (43.2M frames > 10m!)
            val handoverFrames = 145 * sr
            val entries = (0 until 6).map { idx ->
                val stem = "BGM_Stage_TestStop${idx}_Pre"
                val sha = "%064x".format(idx + 1)
                writeSyntheticLoopingBwav(tempDir, sha, stopFrames, 0, -1, sr)
                val medley = com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog.Medley(
                    id = "rally_stop_$idx",
                    index = idx,
                    courseCode = "TestStop$idx",
                    variant = "a",
                    totalFrames = stopFrames.toLong(),
                    durationMs = stopFrames.toLong() * 1000L / sr,
                    clips = listOf(
                        com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog.MedleyClip(
                            stem = stem,
                            start = 0L,
                            sourceOffset = 0L,
                            sourceLength = stopFrames.toLong(),
                        ),
                    ),
                    uniqueStems = listOf(stem),
                )
                val resolved = com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.ResolvedMedley(
                    medley = medley,
                    sampleRate = sr,
                    stemDigests = mapOf(stem to sha),
                    outroHandoverFrame = if (idx < 5) handoverFrames.toLong() else null,
                )
                LinearQueue.Entry(
                    key = "rally:test:$idx",
                    title = "Rally Stop ${idx + 1}",
                    courseCode = "TestStop$idx",
                    nativeTransitionEnabled = true,
                    courseMedley = resolved,
                )
            }

            var nextHandle = 1L
            var activeSegment = 100L
            var transportFrame = 0L
            val seekCalls = mutableListOf<Long>()
            val rebindFlushes = mutableListOf<Boolean>()
            var outputSuspended = false

            val output = object : com.mylittleblover.mkwplayer.audio.SessionAudioOutput {
                override fun timingSnapshot(): LongArray? = null
                override fun suspendStream(): Boolean {
                    outputSuspended = true
                    return true
                }
                override fun resumeStream(sampleRate: Int): Boolean {
                    outputSuspended = false
                    return true
                }
                override fun rebindEngine(engineHandle: Long, sampleRate: Int, flush: Boolean): Boolean {
                    rebindFlushes += flush
                    outputSuspended = false
                    return true
                }
                override fun close() {
                    outputSuspended = true
                }
            }

            val session = PlaybackSession(
                createEngine = {
                    val h = nextHandle++
                    object : SessionAudioEngine {
                        override val sampleRate: Int = sr
                        override fun nativeHandle(): Long = h
                        override fun cancelPendingTransition(): Int = AudioEngine.STATUS_OK
                        override fun loadGraph(packet: ByteArray): Int = AudioEngine.STATUS_OK
                        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int = AudioEngine.STATUS_OK
                        override fun bindBwavFile(
                            assetIndex: Int,
                            file: File,
                            assetId: String,
                            streamed: Boolean,
                            expectedSha256: ByteArray?,
                        ): Int = AudioEngine.STATUS_OK
                        override fun play(target: Int): Int = AudioEngine.STATUS_OK
                        override fun stop(): Int = AudioEngine.STATUS_OK
                        override fun pause(): Int = AudioEngine.STATUS_OK
                        override fun resume(): Int = AudioEngine.STATUS_OK
                        override fun seek(frame: Long): Int {
                            seekCalls += frame
                            transportFrame = frame
                            return AudioEngine.STATUS_OK
                        }
                        override fun sourceSnapshot(slot: Int): LongArray? = null
                        override fun snapshot(): LongArray = LongArray(65).also {
                            it[0] = EngineSnapshot.WIRE_VERSION
                            it[1] = transportFrame
                            it[2] = EngineSnapshot.PLAYING
                            it[3] = 1L
                            it[11] = activeSegment
                        }
                        override fun close() {}
                    }
                },
                openOutput = { _, _ -> output },
            )

            val coordinator = LinearPlaybackCoordinator(
                sessionProvider = { session },
                mainDispatcher = Dispatchers.Unconfined,
                ioDispatcher = Dispatchers.Unconfined,
            )
            coordinator.startExtraQueue(
                entries = entries,
                startIndex = 0,
                packDir = tempDir,
                repeatMode = LinearQueue.RepeatMode.OFF,
            )
            assertEquals("rally:test:0", coordinator.state.value.current?.key)

            // 1. Natural advance Stop 1 -> Stop 2 (seg 101) -> Stop 5 (seg 104, origin = 4 * 145s = 580s = 27.84M)
            activeSegment = 101L
            transportFrame = handoverFrames.toLong()
            session.poll()
            assertEquals("rally:test:1", coordinator.state.value.current?.key)

            // Seek to 60% of Stop 2 (local = 90s -> effective = 145s + 90s = 235s > Stop 1's 150s!)
            coordinator.seekToFrame(90L * sr)
            assertEquals(listOf((handoverFrames + 90 * sr).toLong()), seekCalls)
            assertTrue(session.state.value.playing, "Stop 2 seek failed: ${session.state.value.detail}")
            assertEquals(null, session.state.value.detail)
            assertEquals(90L * sr, session.state.value.positionFrames)
            assertEquals(stopFrames.toLong(), session.state.value.totalFrames)
            assertTrue(!outputSuspended)

            // Natural advance Stop 2 -> Stop 3 -> Stop 4 -> Stop 5 (origin = 4 * 145s = 580s) and seek past 10m (30.72M frames!)
            for (step in 2..4) {
                activeSegment = 100L + step
                transportFrame = step.toLong() * handoverFrames
                session.poll()
                assertEquals("rally:test:$step", coordinator.state.value.current?.key)
            }

            coordinator.seekToFrame(60L * sr)
            assertEquals((4L * handoverFrames + 60L * sr), seekCalls.last())
            assertTrue(session.state.value.playing, "Stop 5 (>10m) seek failed: ${session.state.value.detail}")
            assertEquals(null, session.state.value.detail)
            assertEquals(60L * sr, session.state.value.positionFrames)
            assertTrue(!outputSuspended)

            // 2. Manual skipNext() from Stop 5 -> Stop 6 (opens Stop 6 fresh with flush=true), then seek forward & backward!
            activeSegment = 100L
            transportFrame = 0L
            assertTrue(coordinator.skipNext())
            assertEquals("rally:test:5", coordinator.state.value.current?.key)
            assertEquals(true, rebindFlushes.last())

            coordinator.seekToFrame(100L * sr)
            assertEquals(100L * sr, seekCalls.last())
            assertTrue(session.state.value.playing, "Post-skipNext forward seek failed: ${session.state.value.detail}")
            assertEquals(100L * sr, session.state.value.positionFrames)

            coordinator.seekToFrame(20L * sr)
            assertEquals(20L * sr, seekCalls.last())
            assertTrue(session.state.value.playing, "Post-skipNext backward seek failed: ${session.state.value.detail}")
            assertEquals(20L * sr, session.state.value.positionFrames)
            assertTrue(!outputSuspended)

            coordinator.clearSession()
            session.release()
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
