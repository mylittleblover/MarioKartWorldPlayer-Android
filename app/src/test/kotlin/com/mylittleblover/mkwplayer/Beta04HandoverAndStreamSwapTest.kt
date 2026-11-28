package com.mylittleblover.mkwplayer

import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SessionAudioEngine
import com.mylittleblover.mkwplayer.audio.SessionAudioOutput
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.CourseMedleyChainCompiler
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.library.StageConnections
import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.player.LinearHandoverPlanner
import com.mylittleblover.mkwplayer.player.LinearQueue
import com.mylittleblover.mkwplayer.player.PlaybackSession
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Beta04HandoverAndStreamSwapTest {

    private val rate = 48_000

    private fun syntheticBwav(frames: Int = 480_000, sampleRate: Int = 48_000, channels: Int = 2): ByteArray {
        val bytes = ByteArray(16 + channels * 76)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        buf.putShort(4, 0xFEFF.toShort())
        buf.putShort(14, channels.toShort())
        for (ch in 0 until channels) {
            val base = 16 + ch * 76
            buf.putShort(base, 1) // DSP-ADPCM
            buf.putInt(base + 4, sampleRate)
            buf.putInt(base + 12, frames)
            buf.putInt(base + 60, -1)
            buf.putInt(base + 64, 0)
        }
        return bytes
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun dummyPacket(): ByteArray = G.build(
        G.Spec(
            assets = listOf(G.Asset("a.bwav", rate, 2, 1_000L, ByteArray(32))),
            segments = listOf(G.Segment(100, exit = 1_000L)),
            tracks = listOf(G.Track(200, 100, listOf(G.Clip(300, 0, sourceLength = 1_000L)))),
        ),
    )

    private class SwapTrackingOutput(
        private var currentHandle: Long,
        private val supportsSuspend: Boolean = true,
        private val supportsRebind: Boolean = true,
        private val onLeasedChange: (Long, Boolean) -> Unit,
    ) : SessionAudioOutput {
        var suspends = 0
        var resumes = 0
        var rebinds = 0
        var closes = 0
        var active = true

        override fun timingSnapshot(): LongArray? = null

        override fun suspendStream(): Boolean {
            if (!supportsSuspend || !active) return false
            suspends++
            active = false
            onLeasedChange(currentHandle, false)
            return true
        }

        override fun resumeStream(sampleRate: Int): Boolean {
            if (!supportsSuspend || active) return false
            resumes++
            active = true
            onLeasedChange(currentHandle, true)
            return true
        }

        override fun rebindEngine(engineHandle: Long, sampleRate: Int): Boolean {
            if (!supportsRebind || engineHandle == 0L) return false
            rebinds++
            if (currentHandle != engineHandle) {
                onLeasedChange(currentHandle, false)
                currentHandle = engineHandle
            }
            active = true
            onLeasedChange(currentHandle, true)
            return true
        }

        override fun close() {
            closes++
            active = false
            onLeasedChange(currentHandle, false)
        }
    }

    private class ChainSessionEngine(val id: Long) : SessionAudioEngine {
        var outputLeased = false
        var transportStatus = EngineSnapshot.PLAYING
        var transportFrame = 0L
        var activeSegment = 100L
        var closed = false
        val playCalls = mutableListOf<Int>()
        val cancelCalls = mutableListOf<Unit>()
        val fileBinds = mutableListOf<Triple<Int, File, ByteArray?>>()
        val streamedByteBinds = mutableListOf<Triple<Int, String, ByteArray>>()
        val preparedByteBinds = mutableListOf<Triple<Int, String, ByteArray>>()

        override val sampleRate: Int = 48_000
        override fun nativeHandle(): Long = id
        override fun cancelPendingTransition(): Int {
            cancelCalls += Unit
            return AudioEngine.STATUS_OK
        }
        override fun loadGraph(packet: ByteArray): Int = if (outputLeased) 1 else AudioEngine.STATUS_OK
        override fun bindBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int {
            if (outputLeased) return 1
            preparedByteBinds += Triple(assetIndex, assetId, bytes)
            return AudioEngine.STATUS_OK
        }
        override fun bindStreamedBwav(assetIndex: Int, bytes: ByteArray, assetId: String): Int {
            if (outputLeased) return 1
            streamedByteBinds += Triple(assetIndex, assetId, bytes)
            return AudioEngine.STATUS_OK
        }
        override fun bindBwavFile(
            assetIndex: Int,
            file: File,
            assetId: String,
            streamed: Boolean,
            expectedSha256: ByteArray?,
        ): Int {
            if (outputLeased) return 1
            fileBinds += Triple(assetIndex, file, expectedSha256)
            return AudioEngine.STATUS_OK
        }
        override fun play(target: Int): Int {
            playCalls += target
            transportStatus = EngineSnapshot.PLAYING
            return AudioEngine.STATUS_OK
        }
        override fun stop(): Int {
            transportStatus = EngineSnapshot.STOPPED
            return AudioEngine.STATUS_OK
        }
        override fun pause(): Int {
            transportStatus = EngineSnapshot.PAUSED
            return AudioEngine.STATUS_OK
        }
        override fun resume(): Int {
            transportStatus = EngineSnapshot.PLAYING
            return AudioEngine.STATUS_OK
        }
        override fun seek(frame: Long): Int {
            if (outputLeased) return 1
            transportFrame = frame
            return AudioEngine.STATUS_OK
        }
        override fun snapshot(): LongArray = LongArray(65).apply {
            this[0] = EngineSnapshot.WIRE_VERSION
            this[1] = transportFrame
            this[2] = transportStatus
            this[3] = if (transportStatus == EngineSnapshot.PLAYING) 1L else 0L
            this[11] = activeSegment
        }
        override fun sourceSnapshot(slot: Int): LongArray? = null
        override fun close() {
            closed = true
            outputLeased = false
        }
    }

    @Test
    fun seekingInsideChainedSurvivalRallyPreservesAutomaticTransitionsForBothSyncAndAsyncSeek() = runBlocking {
        for (supportsSuspend in listOf(true, false)) {
            val engine = ChainSessionEngine(1L)
            val session = PlaybackSession(
                createEngine = { engine },
                openOutput = { handle, _ ->
                    engine.outputLeased = true
                    SwapTrackingOutput(
                        currentHandle = handle,
                        supportsSuspend = supportsSuspend,
                        supportsRebind = false,
                        onLeasedChange = { _, leased -> engine.outputLeased = leased },
                    )
                },
            )
            val prep = session.prepareFlow(
                title = "Stop 1",
                packet = dummyPacket(),
                assets = emptyList(),
                frames = 480_000L,
                startSegment = 100,
                autoAdvance = mapOf(100 to 101, 101 to 102),
                prequeueInitial = true,
                linear = true,
            )
            assertIs<PlaybackSession.PrepareResult.Ok>(prep)
            session.commitPrepared(prep.prepared)
            assertEquals(listOf(100, 101), engine.playCalls)

            // Sync seek cancels pending transition and clears automaticSource, but MUST preserve automaticTransitions.
            session.seek(120_000L)
            assertEquals(1, engine.cancelCalls.size)
            session.poll()
            assertEquals(
                listOf(100, 101, 101),
                engine.playCalls,
                "poll() after seek() must re-queue segment 101 (supportsSuspend=$supportsSuspend)",
            )

            // Async seek must also preserve automaticTransitions so poll() re-queues segment 101 again.
            session.seekAsync(240_000L, Dispatchers.IO)
            assertEquals(2, engine.cancelCalls.size)
            session.poll()
            assertEquals(
                listOf(100, 101, 101, 101),
                engine.playCalls,
                "poll() after seekAsync() must re-queue segment 101 (supportsSuspend=$supportsSuspend)",
            )
            session.release()
        }
    }

    @Test
    fun commitPreparedAndPollBeforeFirstOboeRenderPreservePrequeuedAutoAdvance() {
        val engine = ChainSessionEngine(1L).apply {
            // Before the first audio render drains Play(100), native snapshot reports playingSegment = -1.
            activeSegment = -1L
        }
        val session = PlaybackSession(
            createEngine = { engine },
            openOutput = { _, _ -> null },
        )
        val advancedSegments = mutableListOf<Int>()
        session.onLinearSegmentAdvanced = { seg, _ -> advancedSegments += seg }

        val prep = session.prepareFlow(
            title = "Stop 1",
            packet = dummyPacket(),
            assets = emptyList(),
            frames = 480_000L,
            startSegment = 100,
            autoAdvance = mapOf(100 to 101, 101 to 102),
            prequeueInitial = true,
            linear = true,
        )
        assertIs<PlaybackSession.PrepareResult.Ok>(prep)
        session.commitPrepared(prep.prepared)
        assertEquals(listOf(100, 101), engine.playCalls)

        // Poll while playingSegment == -1: must NOT clear automaticSource!
        session.poll()
        assertEquals(listOf(100, 101), engine.playCalls)

        // First render drains Play(100): segment becomes 100, and 101 must NOT be queued a second time.
        engine.activeSegment = 100L
        session.poll()
        assertEquals(listOf(100, 101), engine.playCalls)
        assertTrue(advancedSegments.isEmpty(), "Initial segment 100 should not trigger segment-advanced callback")

        // Transition completes into segment 101: advances window and queues segment 102.
        engine.activeSegment = 101L
        session.poll()
        assertEquals(listOf(101), advancedSegments)
        assertEquals(listOf(100, 101, 102), engine.playCalls)
    }

    @Test
    fun all12SurvivalRalliesChainAll6StopsWithValidHandoverFramesAndKnockoutDirect() {
        assertTrue(LinearHandoverPlanner.MAX_CHAIN_SEGMENTS >= 6, "MAX_CHAIN_SEGMENTS must cover 6-stop rallies")

        val medleyCatalog = CourseMedleyCatalog.parse(File("src/main/assets/course_medley_catalog.json").readText())
        val minf = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val knownStages = medleyCatalog.medleys.map { it.courseCode }.toSet()
        val routeCatalog = assertNotNull(
            OfficialRouteCatalog.parse(File("src/main/assets/official_routes.json").readText(), knownStages),
        )
        val connections = assertNotNull(
            StageConnections.parse(File("src/main/assets/stage_connections.json").readText()),
        )

        val rallies = routeCatalog.routes.filter { it.kind == OfficialRouteKind.RALLY }
        assertEquals(14, rallies.size)

        var totalLegBoundaries = 0
        for (rally in rallies) {
            assertEquals(6, rally.stops.size, "Rally ${rally.id} must have 6 stops")
            val entries = rally.stops.mapIndexed { idx, stop ->
                val stage = stop.stageCode.removeSuffix("_00")
                val targetVariant = RoadJukeboxCatalog.resolveSuiteVariantForStop(
                    officialKind = OfficialRouteKind.RALLY,
                    suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                    stopIndex = idx,
                    stopCount = rally.stops.size,
                    stageCode = stage,
                )
                val courseMedleys = medleyCatalog.forCourse(stage)
                val medley = courseMedleys.firstOrNull { it.normalizedSuiteVariant == targetVariant }
                    ?: courseMedleys.firstOrNull { !it.isKnockoutVariant }
                    ?: error("Missing rally medley (variant=$targetVariant) for $stage in ${rally.id}")
                val handoverFrame = CourseFlowRecipeBuilder.resolveMedleyHandoverFrame(medley, minf)
                if (idx < rally.stops.lastIndex) {
                    assertNotNull(
                        handoverFrame,
                        "Missing Outro_ToNextPre handover frame for rally=${rally.id} stop=$idx stage=$stage variant=$targetVariant stem=${medley.clips.lastOrNull()?.stem}",
                    )
                    assertTrue(
                        handoverFrame in 1L..medley.totalFrames,
                        "Out-of-range handover frame $handoverFrame for $stage",
                    )
                }
                val incomingPhase = CourseFlowRecipeBuilder.resolveIncomingPhase(medley, minf)
                val resolved = CourseMedleyCompiler.ResolvedMedley(
                    medley = medley,
                    sampleRate = medleyCatalog.sampleRate,
                    stemDigests = medley.uniqueStems.associateWith { "0".repeat(64) },
                    outroHandoverFrame = handoverFrame,
                    incomingPrePhase = incomingPhase,
                )
                LinearQueue.Entry(
                    key = "rally:${rally.id}:$idx:$stage",
                    title = stage,
                    courseCode = stage,
                    nativeTransitionEnabled = true,
                    courseMedley = resolved,
                )
            }

            val chained = LinearHandoverPlanner.collectChainEntries(entries.first(), entries.drop(1))
            assertEquals(
                6,
                chained.size,
                "Rally ${rally.id} must chain all 6 stops in one native graph, got ${chained.size}",
            )
            totalLegBoundaries += rally.stops.size - 1
        }
        assertEquals(70, totalLegBoundaries)

        // Verify DKPass_Outro06 (event-tagged) and Lava_Outro01 (Outro00 fallback) resolve even against minimal MINF records.
        val dkPassMedley = medleyCatalog.forCourse("DKPass").first { it.clips.last().stem == "BGM_Stage_DKPass_Outro06" }
        assertEquals(321951L, CourseFlowRecipeBuilder.resolveMedleyHandoverFrame(dkPassMedley, minf)?.let {
            it - dkPassMedley.clips.last().start + dkPassMedley.clips.last().sourceOffset
        })
        val lavaMedley = medleyCatalog.forCourse("Lava").first { it.clips.last().stem == "BGM_Stage_Lava_Outro01" }
        assertEquals(352000L, CourseFlowRecipeBuilder.resolveMedleyHandoverFrame(lavaMedley, minf)?.let {
            it - lavaMedley.clips.last().start + lavaMedley.clips.last().sourceOffset
        })

        // Verify KNOCKOUT_DIRECT takes precedence over any CandidatePool entries on every rally leg.
        val firstRally = rallies.first()
        val fakePoolEntries = firstRally.stops.zipWithNext().associate { (a, b) ->
            RoadJukeboxCatalog.EdgeKey(a.stageCode, b.stageCode) to listOf("missing_jukebox_ref")
        }
        val customPool = RoadJukeboxCatalog.CandidatePool(dataVersion = 1, entries = fakePoolEntries)
        val report = RoadJukeboxCatalog.inspectOfficialRoute(
            route = firstRally,
            connections = connections,
            pool = customPool,
            installedJukebox = listOf(
                JukeboxCatalog.TrackItem(
                    track = PackManifest.Track(
                        key = "t_001",
                        kind = ResourceKind.JUKEBOX,
                        title = "t_001",
                        primary = AudioAssetId("a".repeat(64)),
                        isFlow = false,
                        parts = listOf(PackManifest.Part(AudioAssetId("a".repeat(64)), PackManifest.Role.MAIN)),
                    ),
                    gameId = "mkw",
                    gameOrder = 1,
                    jukeboxOrder = 1,
                    ostIndex = 1,
                    title = "Track",
                    arrangement = null,
                    displayName = "Track",
                    jukeboxCode = "t_001",
                    frames = 48_000,
                    sampleRate = 48_000,
                    durationMs = 1_000L,
                ),
            ),
            isStagePlayable = { true },
            isStageHidden = { false },
        )
        assertTrue(report.canPlay, "Survival rally should ignore missing CandidatePool refs when KNOCKOUT_DIRECT is active")
        assertTrue(
            report.legs.all { it.resolution is RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect },
            "All Survival Rally legs must resolve to KnockoutTourDirect",
        )
    }

    @Test
    fun commitPreparedCallsRebindEngineOnActiveOutputWithoutClosingOrReopening() {
        val engine1 = ChainSessionEngine(1L)
        val engine2 = ChainSessionEngine(2L)
        val engines = ArrayDeque(listOf(engine1, engine2))
        var openCount = 0
        var trackedOutput: SwapTrackingOutput? = null

        val session = PlaybackSession(
            createEngine = { engines.removeFirst() },
            openOutput = { handle, _ ->
                openCount++
                val eng = if (handle == 1L) engine1 else engine2
                eng.outputLeased = true
                SwapTrackingOutput(
                    currentHandle = handle,
                    supportsSuspend = true,
                    supportsRebind = true,
                    onLeasedChange = { targetHandle, leased ->
                        when (targetHandle) {
                            1L -> engine1.outputLeased = leased
                            2L -> engine2.outputLeased = leased
                        }
                    },
                ).also { trackedOutput = it }
            },
        )

        val prep1 = session.prepareFlow("Track 1", dummyPacket(), emptyList(), 1_000L, 100, linear = true)
        assertIs<PlaybackSession.PrepareResult.Ok>(prep1)
        session.commitPrepared(prep1.prepared)
        val out = assertNotNull(trackedOutput)
        assertEquals(1, openCount)
        assertEquals(0, out.rebinds)
        assertTrue(engine1.outputLeased)

        val prep2 = session.prepareFlow("Track 2", dummyPacket(), emptyList(), 2_000L, 100, linear = true)
        assertIs<PlaybackSession.PrepareResult.Ok>(prep2)
        val state = session.commitPrepared(prep2.prepared)

        assertNull(state.detail)
        assertEquals("Track 2", state.title)
        assertEquals(1, out.rebinds, "Active SessionAudioOutput must hot-swap via rebindEngine")
        assertEquals(1, openCount, "openOutput must not be called again when rebindEngine succeeds")
        assertEquals(0, out.closes, "close() must not be called when rebindEngine succeeds")
        assertTrue(engine1.closed)
        assertFalse(engine1.outputLeased)
        assertTrue(engine2.outputLeased)

        session.release()
        assertEquals(1, out.closes)
        assertTrue(engine2.closed)
    }

    @Test
    fun compileGraphFromFilesAndCompileChainFromFilesProduceByteIdenticalPacketsToBytesPath() {
        val tmpDir = kotlin.io.path.createTempDirectory("beta04-file-graph-").toFile()
        try {
            val pre1 = syntheticBwav(frames = 200_000)
            val outro1 = syntheticBwav(frames = 250_000)
            val pre2 = syntheticBwav(frames = 220_000)
            val fanfareFallback = syntheticBwav(frames = 180_000)

            val filePre1 = File(tmpDir, sha256Hex(pre1)).apply { writeBytes(pre1) }
            val fileOutro1 = File(tmpDir, sha256Hex(outro1)).apply { writeBytes(outro1) }
            val filePre2 = File(tmpDir, sha256Hex(pre2)).apply { writeBytes(pre2) }

            val medley1 = CourseMedleyCatalog.Medley(
                id = "m1",
                index = 1,
                courseCode = "FirstCircuit",
                variant = "a",
                totalFrames = 300_000L,
                durationMs = 6_250L,
                clips = listOf(
                    CourseMedleyCatalog.MedleyClip("BGM_Stage_FirstCircuit_Pre", 0L, 0L, 100_000L),
                    CourseMedleyCatalog.MedleyClip("BGM_Stage_FirstCircuit_Outro00", 100_000L, 0L, 200_000L),
                ),
                uniqueStems = listOf("BGM_Stage_FirstCircuit_Pre", "BGM_Stage_FirstCircuit_Outro00"),
            )
            val medley2 = CourseMedleyCatalog.Medley(
                id = "m2",
                index = 2,
                courseCode = "WestCity",
                variant = "a",
                totalFrames = 260_000L,
                durationMs = 5_416L,
                clips = listOf(
                    CourseMedleyCatalog.MedleyClip("BGM_Stage_WestCity_Pre", 0L, 0L, 140_000L),
                    CourseMedleyCatalog.MedleyClip("BGM_Seq_FinalLapFF", 140_000L, 0L, 120_000L),
                ),
                uniqueStems = listOf("BGM_Stage_WestCity_Pre", "BGM_Seq_FinalLapFF"),
            )

            val digests1 = mapOf(
                "BGM_Stage_FirstCircuit_Pre" to sha256Hex(pre1),
                "BGM_Stage_FirstCircuit_Outro00" to sha256Hex(outro1),
            )
            val digests2 = mapOf(
                "BGM_Stage_WestCity_Pre" to sha256Hex(pre2),
                "BGM_Seq_FinalLapFF" to sha256Hex(fanfareFallback),
            )

            val singleFromBytes = CourseMedleyCompiler.compileGraph(
                medley = medley1,
                sampleRate = rate,
                bytesByStem = mapOf(
                    "BGM_Stage_FirstCircuit_Pre" to pre1,
                    "BGM_Stage_FirstCircuit_Outro00" to outro1,
                ),
                knownDigestsHex = digests1,
            )
            val singleFromFiles = CourseMedleyCompiler.compileGraphFromFiles(
                medley = medley1,
                sampleRate = rate,
                filesByStem = mapOf(
                    "BGM_Stage_FirstCircuit_Pre" to filePre1,
                    "BGM_Stage_FirstCircuit_Outro00" to fileOutro1,
                ),
                knownDigestsHex = digests1,
            )
            assertContentEquals(singleFromBytes.packet, singleFromFiles.packet)
            assertEquals(singleFromBytes.totalFrames, singleFromFiles.totalFrames)
            assertTrue(GraphProbe.inspect(singleFromFiles.packet) is GraphProbe.Result.Ok)

            val resolved1 = CourseMedleyCompiler.ResolvedMedley(
                medley = medley1,
                sampleRate = rate,
                stemDigests = digests1,
                outroHandoverFrame = 220_000L,
            )
            val resolved2 = CourseMedleyCompiler.ResolvedMedley(
                medley = medley2,
                sampleRate = rate,
                stemDigests = digests2,
                outroHandoverFrame = 240_000L,
            )

            val chainFromBytes = CourseMedleyChainCompiler.compileChain(
                entries = listOf("e1" to resolved1, "e2" to resolved2),
                bytesByStem = mapOf(
                    "BGM_Stage_FirstCircuit_Pre" to pre1,
                    "BGM_Stage_FirstCircuit_Outro00" to outro1,
                    "BGM_Stage_WestCity_Pre" to pre2,
                    "BGM_Seq_FinalLapFF" to fanfareFallback,
                ),
            )
            val chainFromFiles = CourseMedleyChainCompiler.compileChainFromFiles(
                entries = listOf("e1" to resolved1, "e2" to resolved2),
                filesByStem = mapOf(
                    "BGM_Stage_FirstCircuit_Pre" to filePre1,
                    "BGM_Stage_FirstCircuit_Outro00" to fileOutro1,
                    "BGM_Stage_WestCity_Pre" to filePre2,
                ),
                fallbackBytesByStem = mapOf("BGM_Seq_FinalLapFF" to fanfareFallback),
            )

            assertContentEquals(chainFromBytes.packet, chainFromFiles.packet)
            assertEquals(chainFromBytes.autoAdvance, chainFromFiles.autoAdvance)
            assertEquals(chainFromBytes.windows, chainFromFiles.windows)
            assertEquals(chainFromBytes.firstTotalFrames, chainFromFiles.firstTotalFrames)
            assertEquals(chainFromBytes.sampleRate, chainFromFiles.sampleRate)
            assertTrue(GraphProbe.inspect(chainFromFiles.packet) is GraphProbe.Result.Ok)

            val engine = ChainSessionEngine(10L)
            val session = PlaybackSession(createEngine = { engine }, openOutput = { _, _ -> null })
            val prep = session.prepareFlowFromFiles(
                title = "File Chain",
                packet = chainFromFiles.packet,
                assets = chainFromFiles.assets,
                frames = chainFromFiles.firstTotalFrames,
                startSegment = 100,
                streamed = true,
                autoAdvance = chainFromFiles.autoAdvance,
                prequeueInitial = true,
                linear = true,
            )
            assertIs<PlaybackSession.PrepareResult.Ok>(prep)
            assertEquals(3, engine.fileBinds.size, "Disk stems must bind via bindBwavFile")
            assertEquals(1, engine.streamedByteBinds.size, "Fallback stem must bind via streamed bytes")
            assertEquals("BGM_Seq_FinalLapFF.bwav", engine.streamedByteBinds[0].second)
            prep.prepared.discard()
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
