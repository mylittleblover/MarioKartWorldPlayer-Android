package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Always-run unit tests pinning every verified Mario Kart World v1.8.0 music transition rule
 * without requiring external ROMFS audio files.
 */
class VerifiedV180TransitionsTest {

    private fun minf(): MinfCatalog =
        MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())

    private fun medleys(): CourseMedleyCatalog.Catalog =
        CourseMedleyCatalog.parse(File("src/main/assets/course_medley_catalog.json").readText())

    private fun bwavHeader(
        frames: Long,
        sampleRate: Int = 48_000,
        channels: Int = 2,
        loopStart: Long? = null,
        loopEnd: Long? = null,
        codecKind: Int = 1,
    ): ByteArray {
        val bytes = ByteArray(16 + channels * 76)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'W'.code.toByte()
        bytes[2] = 'A'.code.toByte()
        bytes[3] = 'V'.code.toByte()
        buf.putShort(4, 0xFEFF.toShort())
        buf.putShort(6, 1)
        buf.putShort(14, channels.toShort())
        for (ch in 0 until channels) {
            val base = 16 + ch * 76
            buf.putShort(base + 0, codecKind.toShort())
            buf.putShort(base + 2, ch.toShort())
            buf.putInt(base + 4, sampleRate)
            buf.putInt(base + 8, frames.toInt())
            buf.putInt(base + 12, frames.toInt())
            buf.putInt(base + 56, 1)
            if (loopStart != null && loopEnd != null) {
                buf.putInt(base + 60, loopEnd.toInt())
                buf.putInt(base + 64, loopStart.toInt())
            } else {
                buf.putInt(base + 60, -1)
                buf.putInt(base + 64, 0)
            }
        }
        return bytes
    }

    // ---------------------------------------------------------------- Pre_TransitionGrid (3/4/8/16)

    @Test
    fun preTransitionGridLookupAndCandidatesMatchVerifiedV180Data() {
        val catalog = minf()

        assertEquals(8, MinfCatalog.preTransitionGridBeats("Cinema", "Pre"))
        assertEquals(8, MinfCatalog.preTransitionGridBeats("Cinema", "Pre_Short"))
        assertEquals(8, MinfCatalog.preTransitionGridBeats("Lava", "Pre"))
        assertEquals(8, MinfCatalog.preTransitionGridBeats("Lava", "Pre_Short"))
        assertEquals(3, MinfCatalog.preTransitionGridBeats("BigTree", "Pre"))
        assertEquals(3, MinfCatalog.preTransitionGridBeats("DinoBones", "Pre_Short"))
        assertEquals(16, MinfCatalog.preTransitionGridBeats("Rainbow", "Area1_Pre"))
        assertEquals(4, MinfCatalog.preTransitionGridBeats("Rainbow", "Area3_Pre"))
        assertEquals(4, MinfCatalog.preTransitionGridBeats("WestCity", "Pre"))

        // Cinema_Pre (8-beat): 16 candidates, includes beat 133 (offset 128 = 1815426)
        val cinemaPre = catalog.part("Cinema", "Pre")!!.gridLoopCandidates(8)
        assertEquals(16, cinemaPre.size)
        assertTrue(1815426L in cinemaPre)

        // Cinema_Pre_Short (8-beat): 4 candidates, includes beat 37 (offset 32 = 498856)
        val cinemaShort = catalog.part("Cinema", "Pre_Short")!!.gridLoopCandidates(8)
        assertEquals(4, cinemaShort.size)
        assertTrue(498856L in cinemaShort)

        // Lava_Pre (8-beat): 10 candidates, includes beat 83 (offset 80 = 1319998)
        val lavaPre = catalog.part("Lava", "Pre")!!.gridLoopCandidates(8)
        assertEquals(10, lavaPre.size)
        assertTrue(1319998L in lavaPre)

        // Lava_Pre_Short (8-beat): 5 candidates, ±1 boundary beat at loopStart-1 clamped to loopStart (168000)
        val lavaShortRec = catalog.part("Lava", "Pre_Short")!!
        val lavaShort = lavaShortRec.gridLoopCandidates(8)
        assertEquals(5, lavaShort.size)
        assertEquals(lavaShortRec.loopStart, lavaShort.first())
        assertTrue(551999L in lavaShort)

        // BigTree_Pre (3-beat): 33 candidates, ±1 boundary beat at loopStart-1 clamped to loopStart
        val bigTreePreRec = catalog.part("BigTree", "Pre")!!
        val bigTreePre = bigTreePreRec.gridLoopCandidates(3)
        assertEquals(33, bigTreePre.size)
        assertEquals(bigTreePreRec.loopStart, bigTreePre.first())

        // DinoBones_Pre (3-beat): 33 candidates
        assertEquals(33, catalog.part("DinoBones", "Pre")!!.gridLoopCandidates(3).size)

        // Rainbow Area1_Pre (16-beat): 6 candidates including beat 240 (4374673)
        val rainbowPre = catalog.part("Rainbow", "Area1_Pre")!!.gridLoopCandidates(16)
        assertEquals(6, rainbowPre.size)
        assertTrue(4374673L in rainbowPre)
    }

    @Test
    fun minfCompilerEmitsGridPreExitCuesWhileKeepingBeat1ExitCues() {
        val catalog = minf()
        val pre = catalog.part("Cinema", "Pre")!!
        val full = catalog.part("Cinema", "Full")!!
        val confirmed = ConfirmedOutroCatalog.forStage("Cinema")!!
        val parts = listOf(
            StageFlow.Part(StageFlow.Role.Pre, "BGM_Stage_Cinema_Pre.bwav", pre.loopEnd, 48_000, 2, pre.loopStart, pre.loopEnd),
            StageFlow.Part(StageFlow.Role.Main, "BGM_Stage_Cinema.bwav", maxOf(full.loopEnd, confirmed.values.flatten().max() + 1), 48_000, 2, full.loopStart, full.loopEnd),
        ) + confirmed.keys.map { n -> StageFlow.Part(StageFlow.Role.Outro, "BGM_Stage_Cinema_Outro%02d.bwav".format(n), 100_000, 48_000, 2, null, null, n) }
        val flow = StageFlow("BGM_Stage_Cinema", parts, StageFlow.DEFAULT_TIMING)
        val prepared = MinfInteractiveCompiler.prepare(
            flow = flow,
            digests = parts.associate { it.fileName to ByteArray(32) },
            catalog = catalog,
            includeBeat1Cues = true,
        )
        val preSeg = prepared.spec.segments.single { it.index == prepared.compiled.segmentByRole.getValue(StageFlow.Role.Pre) }
        assertEquals(16, preSeg.cues.count { it.name == "pre.exit" })
        assertEquals(pre.beats.count { it.label == 1 && it.frame in 0L..pre.loopEnd }, preSeg.cues.count { it.name == "beat1.exit" })
    }

    // ---------------------------------------------------------------- FullVersionCompiler (0.5s vs 2.0s)

    @Test
    fun cinemaUses500msBothWaysAndSnowMountainUses500msEnter2000msExit() {
        val catalog = minf()
        for (stage in listOf("Cinema", "SnowMountain")) {
            val pre = catalog.part(stage, "Pre")!!
            val full = catalog.part(stage, "Full")!!
            val fast = catalog.part(stage, "Fast")!!
            val confirmed = ConfirmedOutroCatalog.forStage(stage)!!
            val fullFrames = maxOf(full.loopEnd, confirmed.values.flatten().max() + 1)

            val parts = listOf(
                StageFlow.Part(StageFlow.Role.Pre, "BGM_Stage_${stage}_Pre.bwav", pre.loopEnd, 48_000, 2, pre.loopStart, pre.loopEnd),
                StageFlow.Part(StageFlow.Role.Main, "BGM_Stage_$stage.bwav", fullFrames, 48_000, 2, full.loopStart, full.loopEnd),
                StageFlow.Part(StageFlow.Role.Fast, "BGM_Stage_${stage}_Fast.bwav", fast.loopEnd, 48_000, 2, fast.loopStart, fast.loopEnd),
            ) + confirmed.keys.map { n ->
                StageFlow.Part(StageFlow.Role.Outro, "BGM_Stage_${stage}_Outro%02d.bwav".format(n), 200_000, 48_000, 2, null, null, n)
            }
            val flow = StageFlow("BGM_Stage_$stage", parts, StageFlow.DEFAULT_TIMING)
            val bytes = buildMap {
                for (p in parts) {
                    put(p.fileName, bwavHeader(p.frames, 48_000, 2, p.loopStart, p.loopEnd))
                }
                put("BGM_Stage_${stage}_Pattern2.bwav", bwavHeader(fullFrames, 48_000, 2, full.loopStart, full.loopEnd))
                put("BGM_Stage_${stage}_Fast_Pattern2.bwav", bwavHeader(fast.loopEnd, 48_000, 2, fast.loopStart, fast.loopEnd))
            }
            val plan = InteractivePlaybackPlan.normal(flow, bytes, catalog)
            val main = plan.labels.entries.single { it.value == "Main" }.key
            val altMain = plan.labels.entries.single { it.value == "Main Pattern2" }.key
            val fastId = plan.labels.entries.single { it.value == "Fast" }.key
            val altFast = plan.labels.entries.single { it.value == "Fast Pattern2" }.key

            val enterMain = plan.spec.transitions.single { it.sources == listOf(main) && it.destinations == listOf(altMain) }
            val exitMain = plan.spec.transitions.single { it.sources == listOf(altMain) && it.destinations == listOf(main) }
            val enterFast = plan.spec.transitions.single { it.sources == listOf(fastId) && it.destinations == listOf(altFast) }
            val exitFast = plan.spec.transitions.single { it.sources == listOf(altFast) && it.destinations == listOf(fastId) }

            fun framesOf(fade: G.Fade): Long = (fade.beats * 60.0 * 48_000.0 / plan.spec.bpm).roundToLong()

            assertEquals(24_000L, framesOf(enterMain.fadeIn), "$stage Main->Pattern2 fadeIn must be 0.5s")
            assertEquals(24_000L, framesOf(enterMain.fadeOut), "$stage Main->Pattern2 fadeOut must be 0.5s")
            assertEquals(24_000L, framesOf(enterFast.fadeIn), "$stage Fast->FastPattern2 fadeIn must be 0.5s")
            assertEquals(24_000L, framesOf(enterFast.fadeOut), "$stage Fast->FastPattern2 fadeOut must be 0.5s")

            val expectedExitFrames = if (stage == "SnowMountain") 96_000L else 24_000L
            assertEquals(expectedExitFrames, framesOf(exitMain.fadeIn), "$stage Pattern2->Main fadeIn")
            assertEquals(expectedExitFrames, framesOf(exitMain.fadeOut), "$stage Pattern2->Main fadeOut")
            assertEquals(expectedExitFrames, framesOf(exitFast.fadeIn), "$stage FastPattern2->Fast fadeIn")
            assertEquals(expectedExitFrames, framesOf(exitFast.fadeOut), "$stage FastPattern2->Fast fadeOut")
            assertTrue(GraphProbe.inspect(plan.packet) is GraphProbe.Result.Ok)
        }
    }

    // ---------------------------------------------------------------- DonkeyRegionCompiler (4-beat & 8-beat grids)

    @Test
    fun donkeyKongUses4BeatGridOnRegion1AndLoopAnchored8BeatGridOnRegions2To5WithLookAhead() {
        val catalog = minf()
        val r1 = catalog.part("DonkeyKong", "Full")!!
        val r2 = catalog.part("DonkeyKong_Pattern2", "Full")!!
        val r3 = catalog.part("DonkeyKong_Pattern3", "Full")!!
        val r4 = catalog.part("DonkeyKong_Pattern4", "Full")!!
        val r5 = catalog.part("DonkeyKong_Pattern5", "Full")!!

        // Region 1: every label == 1 bar (4 beats)
        assertEquals(r1.beats.filter { it.label == 1 }.map { it.frame }, DonkeyRegionCompiler.regionExitFrames(r1, 1))

        // Pattern2: 7-bar intro + 8-bar loop -> loopStart at beat 28 -> mod 8 hits beats [4, 12, 20, 28, 36, 44, 52, 60]
        val r2ExpectedIdxs = listOf(4, 12, 20, 28, 36, 44, 52, 60)
        assertEquals(r2ExpectedIdxs.map { r2.beats[it].frame }, DonkeyRegionCompiler.regionExitFrames(r2, 2))

        // Pattern3/4/5: loopStart at beat 8 -> mod 8 hits beats [0, 8, 16, 24, 32, 40, 48, 56, 64, 72]
        val r345ExpectedIdxs = listOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 72)
        for ((idx, rec) in listOf(3 to r3, 4 to r4, 5 to r5)) {
            assertEquals(r345ExpectedIdxs.map { rec.beats[it].frame }, DonkeyRegionCompiler.regionExitFrames(rec, idx))
        }

        // End-to-end synthetic compilation verifies region.exit cues and 2s lookAheadFrames on Pattern2..5
        val pre = catalog.part("DonkeyKong", "Pre")!!
        val fast = catalog.part("DonkeyKong", "Fast")!!
        val confirmed = ConfirmedOutroCatalog.forStage("DonkeyKong")!!
        val fullFrames = maxOf(r1.loopEnd, confirmed.values.flatten().max() + 1)
        val parts = listOf(
            StageFlow.Part(StageFlow.Role.Pre, "BGM_Stage_DonkeyKong_Pre.bwav", pre.loopEnd, 48_000, 2, pre.loopStart, pre.loopEnd),
            StageFlow.Part(StageFlow.Role.Main, "BGM_Stage_DonkeyKong.bwav", fullFrames, 48_000, 2, r1.loopStart, r1.loopEnd),
            StageFlow.Part(StageFlow.Role.Fast, "BGM_Stage_DonkeyKong_Fast.bwav", fast.loopEnd, 48_000, 2, fast.loopStart, fast.loopEnd),
        ) + confirmed.keys.map { n ->
            StageFlow.Part(StageFlow.Role.Outro, "BGM_Stage_DonkeyKong_Outro%02d.bwav".format(n), 200_000, 48_000, 2, null, null, n)
        }
        val flow = StageFlow("BGM_Stage_DonkeyKong", parts, StageFlow.DEFAULT_TIMING)
        val bytes = buildMap {
            for (p in parts) put(p.fileName, bwavHeader(p.frames, 48_000, 2, p.loopStart, p.loopEnd))
            for ((n, rec) in listOf(2 to r2, 3 to r3, 4 to r4, 5 to r5)) {
                put("BGM_Stage_DonkeyKong_Pattern$n.bwav", bwavHeader(rec.loopEnd, 48_000, 2, rec.loopStart, rec.loopEnd))
            }
        }
        val plan = InteractivePlaybackPlan.normal(flow, bytes, catalog)
        assertEquals(5, plan.regions.size)
        val p2Seg = plan.spec.segments.single { it.index == plan.regions.getValue(2) }
        assertEquals(r2ExpectedIdxs.map { r2.beats[it].frame }, p2Seg.cues.filter { it.name == "region.exit" }.map { it.frame })
        for (n in 2..5) {
            val asset = plan.spec.assets.single { it.path == "BGM_Stage_DonkeyKong_Pattern$n.bwav" }
            assertEquals(96_000L, asset.lookAheadFrames)
        }
        assertTrue(GraphProbe.inspect(plan.packet) is GraphProbe.Result.Ok)
    }

    // ---------------------------------------------------------------- InteractivePlaybackPlan guards & Rainbow splitFade

    @Test
    fun applySegmentSwitchFadeOutPreservesAuthoredTransitionsAndRainbowExposesSplitFade() {
        val catalog = minf()
        val rainbowBytes = RainbowInteractiveCompiler.roles.associate { role ->
            val r = catalog.part("Rainbow", role)!!
            val frames = r.loopEnd + 10_000L
            "BGM_Rainbow_$role.bwav" to bwavHeader(
                frames = frames,
                sampleRate = 48_000,
                channels = 2,
                loopStart = if (r.loopStart < r.loopEnd) r.loopStart else null,
                loopEnd = if (r.loopStart < r.loopEnd) r.loopEnd else null,
            )
        }
        val plan = InteractivePlaybackPlan.rainbow(
            bytes = rainbowBytes,
            catalog = catalog,
            options = InteractivePlaybackPlan.Options(segmentSwitchFadeOutMs = 1500),
        )
        val area1Id = plan.labels.entries.single { it.value == "Area1" }.key
        val area2Id = plan.labels.entries.single { it.value == "Area2" }.key
        val area1PreId = plan.labels.entries.single { it.value == "Area1_Pre" }.key
        val area3PreId = plan.labels.entries.single { it.value == "Area3_Pre" }.key
        val area3Id = plan.labels.entries.single { it.value == "Area3" }.key
        val area2Outro0Id = plan.labels.entries.single { it.value == "Area2_Outro00" }.key

        assertEquals(mapOf(area1Id to area2Id), plan.splitFadeOnAdvance)
        assertEquals(300, plan.splitFadeOverlayMs)

        // Authored transitions must NOT be overwritten by segmentSwitchFadeOutMs = 1500ms:
        val pre1ToArea1 = plan.spec.transitions.single { it.sources == listOf(area1PreId) && it.destinations == listOf(area1Id) }
        assertEquals(0.0, pre1ToArea1.fadeOut.beats, "Bridge transition must keep 0 fadeOut")
        val pre3ToArea3 = plan.spec.transitions.single { it.sources == listOf(area3PreId) && it.destinations == listOf(area3Id) }
        assertEquals(0.0, pre3ToArea3.fadeOut.beats, "pre.exit transition must keep 0 fadeOut")
        val area2ToOutro = plan.spec.transitions.single { it.sources == listOf(area2Id) && it.destinations == listOf(area2Outro0Id) }
        assertEquals(0.0, area2ToOutro.fadeOut.beats, "Full_ToOutro transition must keep 0 fadeOut")
        val outroToPre3 = plan.spec.transitions.single { it.sources == listOf(area2Outro0Id) && it.destinations == listOf(area3PreId) }
        assertEquals(0.0, outroToPre3.fadeOut.beats, "ExitCue transition must keep 0 fadeOut")

        // Non-authored zero-fade transition (e.g. a custom switch) still receives user fadeOut:
        val customSpec = G.Spec(
            assets = emptyList(),
            segments = listOf(G.Segment(100, exit = 48_000), G.Segment(101, exit = 48_000)),
            tracks = emptyList(),
            transitions = listOf(
                G.Transition(200, listOf(100), listOf(101), sync = G.SyncPoint.NextCustomCue, sourceCue = "region.exit"),
            ),
            bpm = 120.0,
        )
        val faded = InteractivePlaybackPlan.applySegmentSwitchFadeOut(customSpec, 1000)
        assertEquals(2.0, faded.transitions.single().fadeOut.beats, 1e-6)
        assertTrue(faded.transitions.single().postExit)
    }

    // ---------------------------------------------------------------- NativeInteractiveDriver lookahead & hooks

    @Test
    fun nativeInteractiveDriverEnforces12000SampleMinOutroLookaheadAndFiresHooks() {
        val spec = G.Spec(
            assets = emptyList(),
            segments = listOf(
                G.Segment(
                    index = 101,
                    exit = 200_000L,
                    loop = G.Loop(20_000L, 200_000L),
                    cues = listOf(
                        G.Cue(10, "outro.0", 55_000L), // only 5,000 frames ahead of pos 50,000 (< 12,000!)
                        G.Cue(11, "outro.1", 80_000L), // 30,000 frames ahead (>= 12,000!)
                    ),
                ),
                G.Segment(102, exit = 48_000L),
                G.Segment(103, exit = 48_000L),
            ),
            tracks = emptyList(),
            transitions = listOf(
                G.Transition(201, listOf(101), listOf(102), sourceCue = "outro.0"),
                G.Transition(202, listOf(101), listOf(103), sourceCue = "outro.1"),
            ),
        )
        val requestedEvents = mutableListOf<Pair<Int, Int>>()
        val cancelledEvents = mutableListOf<Pair<Int, Int>>()
        val driver = NativeInteractiveDriver(
            spec = spec,
            entry = 101,
            next = emptyMap(),
            outros = mapOf(0 to 102, 1 to 103),
            fast = null,
            request = { null },
            cancelRequest = { null },
            onTransitionRequested = { from, to -> requestedEvents += from to to },
            onTransitionCancelled = { from, cancelled -> cancelledEvents += from to cancelled },
        )
        driver.observe(NativeInteractiveDriver.Observation(101, 50_000L))
        assertNull(driver.takeOutro())
        // Must skip outro.0 (delay 5,000 < 12,000) and select outro.1 (delay 30,000 >= 12,000)
        assertEquals(103, driver.pending)
        assertEquals(listOf(101 to 103), requestedEvents)

        driver.observe(NativeInteractiveDriver.Observation(101, 52_000L, transitioning = true, destination = 103, canCancel = true))
        assertNull(driver.cancelPending())
        assertEquals(listOf(101 to 103), cancelledEvents)
    }

    // ---------------------------------------------------------------- ExtraInteractiveCompilers & SpeakerOverlay lookAhead

    @Test
    fun extraAndSpeakerCompilersSetTwoSecondLookAheadFrames() {
        val opRef = ExtraMusicCatalog.AssetRef("Dm_Op", "a".repeat(64), 96_000L, 48_000, 6, false, null, null)
        val telopRef = ExtraMusicCatalog.AssetRef("BGM_Telop", "b".repeat(64), 48_000L, 48_000, 2, false, null, null)
        val opening = ExtraMusicCatalog.OtherMusicItem.OverlaidOpening(
            id = "op_test", ostIndex = null, artwork = "", names = mapOf("en" to "Op"),
            searchAliases = emptyList(), durationMs = 2000L, op = opRef, telop = telopRef, deltaFrames = 12_000L, totalFrames = 96_000L,
        )
        val compiledOpening = ExtraInteractiveCompilers.compileOverlaidOpeningGraph(
            opening, bwavHeader(96_000L, 48_000, 6), bwavHeader(48_000L, 48_000, 2),
        )
        assertTrue(GraphProbe.inspect(compiledOpening.packet) is GraphProbe.Result.Ok)

        val battle = ExtraMusicCatalog.TwoStateTrack(
            id = "battle_la", ostIndex = null, artwork = "", names = mapOf("en" to "Battle"),
            mainStem = "BGM_Battle_00", fastStem = "BGM_Battle_00_FL", searchAliases = emptyList(),
            main = ExtraMusicCatalog.AssetRef("BGM_Battle_00", "c".repeat(64), 96_000L, 48_000, 2, true, 0L, 96_000L),
            fast = ExtraMusicCatalog.AssetRef("BGM_Battle_00_FL", "d".repeat(64), 96_000L, 48_000, 2, true, 0L, 96_000L),
            durationMs = 2000L,
        )
        val battlePlan = ExtraInteractiveCompilers.compileTwoStateLoopPlan(
            battle, isBattle = true,
            bytesByStem = mapOf("BGM_Battle_00" to bwavHeader(96_000L), "BGM_Battle_00_FL" to bwavHeader(96_000L)),
        )
        assertTrue(battlePlan.spec.assets.all { it.lookAheadFrames == 96_000L })

        val catalog = minf()
        val kPre = catalog.part("KoopaTroopaBeach", "Pre")!!
        val kMain = catalog.part("KoopaTroopaBeach", "Full")!!
        val kFast = catalog.part("KoopaTroopaBeach", "Fast")!!
        val kBridge = catalog.part("KoopaTroopaBeach", "Bridge")!!
        val kConfirmed = ConfirmedOutroCatalog.forStage("KoopaTroopaBeach")!!
        val kMainFrames = maxOf(kMain.loopEnd, kConfirmed.values.flatten().max() + 1)
        val kParts = listOf(
            StageFlow.Part(StageFlow.Role.Pre, "BGM_Stage_KoopaTroopaBeach_Pre.bwav", kPre.loopEnd, 48_000, 2, kPre.loopStart, kPre.loopEnd),
            StageFlow.Part(StageFlow.Role.Bridge, "BGM_Stage_KoopaTroopaBeach_Bridge.bwav", 196_600L, 48_000, 2, null, null),
            StageFlow.Part(StageFlow.Role.Main, "BGM_Stage_KoopaTroopaBeach.bwav", kMainFrames, 48_000, 2, kMain.loopStart, kMain.loopEnd),
            StageFlow.Part(StageFlow.Role.Fast, "BGM_Stage_KoopaTroopaBeach_Fast.bwav", kFast.loopEnd, 48_000, 2, kFast.loopStart, kFast.loopEnd),
        ) + kConfirmed.keys.map { n ->
            StageFlow.Part(StageFlow.Role.Outro, "BGM_Stage_KoopaTroopaBeach_Outro%02d.bwav".format(n), 200_000L, 48_000, 2, null, null, n)
        }
        val kFlow = StageFlow("BGM_Stage_KoopaTroopaBeach", kParts, StageFlow.DEFAULT_TIMING)
        val kBytes = kParts.associate { it.fileName to bwavHeader(it.frames, 48_000, 2, it.loopStart, it.loopEnd) }
        val baseKoopa = InteractivePlaybackPlan.normal(kFlow, kBytes, catalog)
        val speakerCfg = ExtraMusicCatalog.KoopaSpeakerConfig(
            stageCode = "KoopaTroopaBeach",
            normalAsset = ExtraMusicCatalog.AssetRef("SE_Stage_KoopaTroopaBeach_Speaker_Normal", "e".repeat(64), kMainFrames, 48_000, 2, true, kMain.loopStart, kMain.loopEnd),
            fastAsset = ExtraMusicCatalog.AssetRef("SE_Stage_KoopaTroopaBeach_Speaker_Fast", "f".repeat(64), kFast.loopEnd, 48_000, 2, true, kFast.loopStart, kFast.loopEnd),
        )
        val speakerPlan = SpeakerOverlayCompiler.extend(
            baseKoopa, speakerCfg,
            bwavHeader(kMainFrames, 48_000, 2, kMain.loopStart, kMain.loopEnd),
            bwavHeader(kFast.loopEnd, 48_000, 2, kFast.loopStart, kFast.loopEnd),
        )
        assertTrue(speakerPlan.spec.assets.filter { it.path.contains("Speaker") }.all { it.lookAheadFrames == 96_000L })
    }

    // ---------------------------------------------------------------- CourseFlowRecipeBuilder strict v1.8.0 checks

    @Test
    fun flowRecipeBuilderEnforcesRainbowV180TimingsAndGridConstrainedSnapping() {
        val catalog = minf()
        val medleyCatalog = medleys()
        val sr = 48_000
        val rainbowResolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                if (!stem.startsWith("BGM_Rainbow_")) return null
                val role = stem.removePrefix("BGM_Rainbow_")
                val rec = catalog.part("Rainbow", role)
                val frames = when (role) {
                    "FinalLapFanfare" -> 338_824L
                    else -> maxOf(rec?.loopEnd ?: 1_000_000L, 1_000_000L)
                }
                val hasLoop = rec != null && rec.loopStart < rec.loopEnd
                return CourseFlowRecipeBuilder.AssetHeader(
                    stem = stem,
                    digest = "sha-$stem",
                    frames = frames,
                    sampleRate = sr,
                    loopStart = if (hasLoop) rec!!.loopStart else null,
                    loopEnd = if (hasLoop) rec!!.loopEnd else null,
                )
            }
        }
        val inv = CourseFlowRecipeBuilder.inspectCourse("Rainbow", catalog, medleyCatalog, rainbowResolver)
        val area1Pre = inv.part("Area1_Pre")!!
        assertTrue(area1Pre.isGridConstrained)
        assertEquals(6, area1Pre.exitCandidates.size, "Rainbow Area1_Pre must strictly constrain to 6 16-beat exits")
        assertEquals(4374673L, area1Pre.defaultExitFrame)

        // Area1 -> Area2 split fade (0x0243d500): Melody fades out 0.30s starting one bar (~72,911 f)
        // before bar.exit so pure Inst finishes the bar before fading 0.30s post-exit into Area2.
        val a1Exit = inv.part("Area1")!!.beats.first { it.label == 1 && it.frame > 200_000L }.frame
        val a1Bpm = catalog.part("Rainbow", "Area1_Inst")!!.bpm
        val expectedBarFrames = kotlin.math.round(4.0 * 60.0 / a1Bpm * sr.toDouble()).toLong()
        val splitPreset = CoursePreset(
            id = "user:rb-split",
            courseCode = "Rainbow",
            name = "RB Split",
            steps = listOf(FlowStep("Area1", exitFrame = a1Exit), FlowStep("Area2")),
            outroNumber = null,
        )
        val splitCompiled = CourseFlowRecipeBuilder.compileUserPreset(splitPreset, inv, catalog, rainbowResolver)!!
        val instClip = splitCompiled.medley.clips.single { it.stem == "BGM_Rainbow_Area1_Inst" }
        val melClip = splitCompiled.medley.clips.single { it.stem == "BGM_Rainbow_Area1_Melody" }
        assertEquals(a1Exit + 14_400L, instClip.sourceLength, "Area1_Inst extends 0.30s post-exit")
        assertEquals(14_400L, instClip.fadeOut)
        assertEquals(
            a1Exit - expectedBarFrames + 14_400L,
            melClip.sourceLength,
            "Area1_Melody fades out 0.30s starting 1 bar (~$expectedBarFrames f) before bar.exit",
        )
        assertEquals(14_400L, melClip.fadeOut, "Area1_Melody fades out over 0.30s")
        assertEquals(a1Exit - expectedBarFrames, melClip.sourceLength - melClip.fadeOut)

        // Area3 -> Final_Pre -> Final: 1.2s hold + 3.0s fadeOut on Area3, 2.5s fadeIn on Final_Pre, 0.10s fadeOut + 0.20s fanfare delay
        val a3Exit = inv.part("Area3")!!.beats.first { it.label == 1 && it.frame > 500_000L }.frame
        val fpExit = inv.part("Final_Pre")!!.defaultExitFrame
        val finalPreset = CoursePreset(
            id = "user:rb-final",
            courseCode = "Rainbow",
            name = "RB Final",
            steps = listOf(
                FlowStep("Area3", exitFrame = a3Exit),
                FlowStep("Final_Pre", exitFrame = fpExit),
                FlowStep("Final"),
            ),
            outroNumber = null,
        )
        val estimated = CourseFlowRecipeBuilder.estimateTimelineFrames(finalPreset.steps, null, 0, inv)
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(finalPreset, inv, catalog, rainbowResolver)!!
        assertEquals(estimated, compiled.medley.totalFrames, "estimateTimelineFrames must match compiled totalFrames")

        val a3Clip = compiled.medley.clips.single { it.stem == "BGM_Rainbow_Area3" }
        val fpClips = compiled.medley.clips.filter { it.stem == "BGM_Rainbow_Final_Pre" }
        val fpHead = fpClips.first()
        val fpTail = fpClips.last()
        val ffClip = compiled.medley.clips.single { it.stem == "BGM_Rainbow_FinalLapFanfare" }
        val finalClip = compiled.medley.clips.single { it.stem == "BGM_Rainbow_Final" }

        // Area3 extends 1.20s (57,600) + 3.00s (144,000) = 201,600 frames with fadeOut = 144,000
        assertEquals(a3Exit + 201_600L, a3Clip.sourceLength)
        assertEquals(144_000L, a3Clip.fadeOut)
        // Final_Pre starts at a3Exit with 2.50s (120,000) fadeIn, and fades out 0.10s (4,800) into Fanfare (loop-wrapping if exit == loopEnd)
        assertEquals(a3Exit, fpHead.start)
        assertEquals(120_000L, fpHead.fadeIn)
        assertEquals(4_800L, fpTail.fadeOut)
        assertEquals(a3Exit + fpExit + 4_800L, fpTail.start + fpTail.sourceLength)
        // FinalLapFanfare starts at cursor + 9,600 (0.20s), Final starts at cursor + 9,600 + 218,734
        val fpCursor = a3Exit + fpExit
        assertEquals(fpCursor + 9_600L, ffClip.start)
        assertEquals(fpCursor + 9_600L + 218_734L, finalClip.start)
    }

    @Test
    fun flowRecipeBuilderEnforcesAsymmetricSnowMountainCinemaFadesAndDonkeyKong8BeatGrid() {
        val catalog = minf()
        val medleyCatalog = medleys()
        val sr = 48_000

        fun resolverFor(course: String) = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                if (!stem.contains(course)) return null
                val rec = catalog.records.firstOrNull { it.logicalName == stem }
                val frames = maxOf(rec?.loopEnd ?: 2_600_000L, 2_600_000L)
                val hasLoop = rec != null && rec.loopStart < rec.loopEnd
                return CourseFlowRecipeBuilder.AssetHeader(
                    stem = stem,
                    digest = "sha-$stem",
                    frames = frames,
                    sampleRate = sr,
                    loopStart = if (hasLoop) rec!!.loopStart else null,
                    loopEnd = if (hasLoop) rec!!.loopEnd else null,
                )
            }
        }

        // SnowMountain: Main -> Pattern2 = 0.5s (24,000), Pattern2 -> Main = 2.0s (96,000)
        val snowRes = resolverFor("SnowMountain")
        val snowInv = CourseFlowRecipeBuilder.inspectCourse("SnowMountain", catalog, medleyCatalog, snowRes)
        val snowPreset = CoursePreset(
            id = "user:snow-asym",
            courseCode = "SnowMountain",
            name = "Snow Asym",
            steps = listOf(
                FlowStep("Main", exitFrame = 500_000L),
                FlowStep("Pattern2", exitFrame = 1_000_000L),
                FlowStep("Main", exitFrame = 1_500_000L),
            ),
            outroNumber = null,
        )
        val snowCompiled = CourseFlowRecipeBuilder.compileUserPreset(snowPreset, snowInv, catalog, snowRes)!!
        val snowClips = snowCompiled.medley.clips
        assertEquals(24_000L, snowClips[0].fadeOut, "SnowMountain Main->Pattern2 fadeOut = 0.5s")
        assertEquals(24_000L, snowClips[1].fadeIn, "SnowMountain Main->Pattern2 fadeIn = 0.5s")
        assertEquals(96_000L, snowClips[1].fadeOut, "SnowMountain Pattern2->Main fadeOut = 2.0s")
        assertEquals(96_000L, snowClips[2].fadeIn, "SnowMountain Pattern2->Main fadeIn = 2.0s")

        // Cinema: Main -> Pattern2 = 0.5s, Pattern2 -> Main = 0.5s; Pre is grid-constrained to 16 8-beat exits
        val cinRes = resolverFor("Cinema")
        val cinInv = CourseFlowRecipeBuilder.inspectCourse("Cinema", catalog, medleyCatalog, cinRes)
        assertTrue(cinInv.part("Pre")!!.isGridConstrained)
        assertEquals(16, cinInv.part("Pre")!!.exitCandidates.size)
        val cinPreset = CoursePreset(
            id = "user:cin-sym",
            courseCode = "Cinema",
            name = "Cinema Sym",
            steps = listOf(
                FlowStep("Main", exitFrame = 500_000L),
                FlowStep("Pattern2", exitFrame = 1_000_000L),
                FlowStep("Main", exitFrame = 1_500_000L),
            ),
            outroNumber = null,
        )
        val cinCompiled = CourseFlowRecipeBuilder.compileUserPreset(cinPreset, cinInv, catalog, cinRes)!!
        val cinClips = cinCompiled.medley.clips
        assertEquals(24_000L, cinClips[0].fadeOut)
        assertEquals(24_000L, cinClips[1].fadeIn)
        assertEquals(24_000L, cinClips[1].fadeOut)
        assertEquals(24_000L, cinClips[2].fadeIn)

        // DonkeyKong: Region2 strictly constrained to loopStart-anchored 8-beat exits
        val dkRes = resolverFor("DonkeyKong")
        val dkInv = CourseFlowRecipeBuilder.inspectCourse("DonkeyKong", catalog, medleyCatalog, dkRes)
        assertTrue(dkInv.part("Main")!!.isGridConstrained)
        val dkReg2 = dkInv.part("Region2")!!
        assertTrue(dkReg2.isGridConstrained)
        val dkR2Minf = catalog.part("DonkeyKong_Pattern2", "Full")!!
        assertEquals(
            DonkeyRegionCompiler.regionExitFrames(dkR2Minf, 2).filter { it > 0L },
            dkReg2.exitCandidates.map { it.frame },
        )
    }

    @Test
    fun inspectExtraDisablesFinalLapFanfareForBattleTracksEvenWhenAssetPresent() {
        val packDir = java.nio.file.Files.createTempDirectory("mkw-battle-noff").toFile()
        val audioDir = File(packDir, "audio").apply { mkdirs() }
        File(audioDir, "sha-bmain").writeBytes(bwavHeader(240_000L))
        File(audioDir, "sha-bfast").writeBytes(bwavHeader(192_000L))
        val battleTrack = ExtraMusicCatalog.TwoStateTrack(
            id = "battle_noff",
            ostIndex = null, artwork = "", names = mapOf("en" to "Battle"),
            mainStem = "BGM_Battle_00", fastStem = "BGM_Battle_00_FL", searchAliases = emptyList(),
            main = ExtraMusicCatalog.AssetRef("BGM_Battle_00", "sha-bmain", 240_000L, 48_000, 2, true, 0L, 240_000L),
            fast = ExtraMusicCatalog.AssetRef("BGM_Battle_00_FL", "sha-bfast", 192_000L, 48_000, 2, true, 0L, 192_000L),
            durationMs = 5000L,
        )
        val catalog = ExtraMusicCatalog.Catalog(battleTracks = listOf(battleTrack))
        val inv = CourseFlowRecipeBuilder.inspectExtra(
            extraId = "extra:battle_noff",
            extraCatalog = catalog,
            extraMedleyCatalog = ExtraMedleyCatalog.Catalog(),
            packDir = packDir,
            hasFanfareAsset = true,
        )!!
        assertFalse(inv.hasFanfareAsset, "Battle tracks must never enable FinalLapFF even if fanfare asset exists")
    }

    @Test
    fun rainbowHelpersAndEntryExclusionMatchV180Spec() {
        val catalog = minf()
        val rainbowBytes = RainbowInteractiveCompiler.roles.associate { role ->
            val r = catalog.part("Rainbow", role)!!
            val frames = r.loopEnd + 10_000L
            "BGM_Rainbow_$role.bwav" to bwavHeader(
                frames = frames,
                sampleRate = 48_000,
                channels = 2,
                loopStart = if (r.loopStart < r.loopEnd) r.loopStart else null,
                loopEnd = if (r.loopStart < r.loopEnd) r.loopEnd else null,
            )
        }
        val plan = InteractivePlaybackPlan.rainbow(rainbowBytes, catalog)
        val area1Id = plan.labels.entries.single { it.value == "Area1" }.key
        val area2Id = plan.labels.entries.single { it.value == "Area2" }.key
        val area3Id = plan.labels.entries.single { it.value == "Area3" }.key
        val finalPreId = plan.labels.entries.single { it.value == "Final_Pre" }.key
        val finalId = plan.labels.entries.single { it.value == "Final" }.key

        assertEquals(300, RainbowInteractiveCompiler.AREA1_MELODY_FADE_MS)
        val melodyTrackId = RainbowInteractiveCompiler.area1MelodyTrackId(plan.spec)
        assertNotNull(melodyTrackId)
        assertEquals(listOf(melodyTrackId), plan.overlayTrackIndices)
        assertTrue(RainbowInteractiveCompiler.isArea1ToArea2(plan, area1Id, area2Id))
        assertFalse(RainbowInteractiveCompiler.isArea1ToArea2(plan, area2Id, area3Id))
        assertFalse(RainbowInteractiveCompiler.isArea1ToArea2(plan, area3Id, finalPreId))

        // Rainbow Area 1 melody toggle visibility helper
        assertTrue(RainbowInteractiveCompiler.isRainbowArea1Stage("Area1"))
        assertTrue(com.mylittleblover.mkwplayer.ui.interactive.isRainbowArea1Stage("Area1"))
        for (other in listOf(null, "Area1_Pre", "Area1_Bridge", "Area2", "Area3_Pre", "Area3", "Final_Pre", "Final")) {
            assertFalse(RainbowInteractiveCompiler.isRainbowArea1Stage(other), "Melody toggle must hide on $other")
            assertFalse(com.mylittleblover.mkwplayer.ui.interactive.isRainbowArea1Stage(other))
        }

        // Direct entry into Final or Final_Pre must be disallowed for Rainbow Road (#20)
        assertFalse(finalPreId in plan.initialSegments, "Rainbow must not offer Final_Pre on entry")
        assertFalse(finalId in plan.initialSegments, "Rainbow must not offer Final on entry")
        for (allowed in listOf("Area1_Pre", "Area1_Bridge", "Area1", "Area2", "Area3_Pre", "Area3")) {
            val id = plan.initialSegments.single { plan.labels[it] == allowed }
            assertEquals(id, plan.startingAt(allowed).entry)
        }
        assertTrue(runCatching { plan.startingAt("Final") }.isFailure)
        assertTrue(runCatching { plan.startingAt("Final_Pre") }.isFailure)
    }

    @Test
    fun bigTreeOutro03InteractiveReachabilityRejectsPassedPreLoopCue() {
        val catalog = minf()
        val bigTreeFull = catalog.part("BigTree", "Full")!!
        val confirmed = ConfirmedOutroCatalog.forStage("BigTree")!!
        val outro3Frame = confirmed.getValue(3).single()
        assertTrue(outro3Frame < bigTreeFull.loopStart, "BigTree Outro03 ($outro3Frame) is pre-loop (< ${bigTreeFull.loopStart})")

        val fullFrames = maxOf(bigTreeFull.loopEnd, confirmed.values.flatten().max() + 1)
        val cues = buildList {
            var id = 1000
            for ((num, frames) in confirmed) {
                for (f in frames) add(G.Cue(id++, "outro.$num", f))
            }
        }.sortedBy { it.frame }
        val outroSegs = confirmed.keys.sorted().associateWith { 200 + it }
        val spec = G.Spec(
            assets = emptyList(),
            segments = listOf(
                G.Segment(100, exit = fullFrames, loop = G.Loop(bigTreeFull.loopStart, bigTreeFull.loopEnd), cues = cues),
            ) + outroSegs.values.map { G.Segment(it, exit = 200_000L) },
            tracks = emptyList(),
            transitions = outroSegs.entries.mapIndexed { idx, (num, segId) ->
                G.Transition(300 + idx, listOf(100), listOf(segId), sync = G.SyncPoint.NextCustomCue, sourceCue = "outro.$num")
            },
            bpm = bigTreeFull.bpm,
        )
        val submitted = mutableListOf<Int>()
        val driver = NativeInteractiveDriver(
            spec = spec,
            entry = 100,
            next = emptyMap(),
            outros = outroSegs,
            fast = null,
            request = { submitted += it; null },
        )

        // Before 417,857: Outro03 is reachable and selectable.
        driver.observe(NativeInteractiveDriver.Observation(100, 100_000L))
        assertTrue(3 in driver.reachableOutros)
        assertEquals(confirmed.keys, driver.reachableOutros)
        assertNull(driver.takeOutro(3))
        assertEquals(listOf(outroSegs.getValue(3)), submitted)

        // Reset driver and test after passing 417,857 (inside loop at 600,000): Outro03 is unreachable and rejected cleanly.
        val submittedAfter = mutableListOf<Int>()
        val driverAfter = NativeInteractiveDriver(
            spec = spec,
            entry = 100,
            next = emptyMap(),
            outros = outroSegs,
            fast = null,
            request = { submittedAfter += it; null },
        )
        driverAfter.observe(NativeInteractiveDriver.Observation(100, 600_000L))
        assertFalse(3 in driverAfter.reachableOutros, "Passed pre-loop BigTree Outro03 must be excluded from reachableOutros")
        assertTrue(1 in driverAfter.reachableOutros, "In-loop BigTree Outro01 must remain reachable")
        assertNotNull(driverAfter.takeOutro(3), "takeOutro(3) must reject passed pre-loop Outro03")
        assertTrue(submittedAfter.isEmpty(), "Rejected unreachable Outro03 must never submit to Native")
        assertNull(driverAfter.pending)
    }

    @Test
    fun estimatedFramesUntilPendingSwitchAndUiFormattingHelpersAreExact() {
        val spec = G.Spec(
            assets = emptyList(),
            segments = listOf(
                G.Segment(
                    index = 10,
                    exit = 400_000L,
                    loop = G.Loop(100_000L, 400_000L),
                    cues = listOf(
                        G.Cue(1, "bar.exit", 200_000L),
                        G.Cue(2, "outro.0", 120_000L),
                        G.Cue(3, "outro.0", 260_000L),
                    ),
                ),
                G.Segment(11, exit = 48_000L),
                G.Segment(12, exit = 48_000L),
                G.Segment(13, exit = 48_000L),
                G.Segment(14, exit = 48_000L),
                G.Segment(15, exit = 48_000L),
                G.Segment(16, exit = 48_000L),
                G.Segment(17, exit = 48_000L),
            ),
            tracks = emptyList(),
            transitions = listOf(
                G.Transition(100, listOf(10), listOf(11), sync = G.SyncPoint.Immediate),
                G.Transition(101, listOf(10), listOf(12), sync = G.SyncPoint.ExitCue),
                G.Transition(
                    102, listOf(10), listOf(13),
                    sync = G.SyncPoint.NextCustomCue, sourceCue = "bar.exit",
                    timeline = G.TransitionTimeline(0L, -11_392L, 0L, 0L),
                ),
                G.Transition(103, listOf(10), listOf(14), sync = G.SyncPoint.NextCustomCue, sourceCue = "outro.0"),
                G.Transition(104, listOf(10), listOf(15), sync = G.SyncPoint.NextBeat),
                G.Transition(105, listOf(10), listOf(16), sync = G.SyncPoint.NextBar),
                G.Transition(106, listOf(10), listOf(17), sync = G.SyncPoint.NextGrid),
            ),
            transportSampleRate = 48_000,
            bpm = 120.0,
            numerator = 4,
            denominator = 4,
            grid = 2.0,
        )
        fun driverFor(target: Int, pos: Long): NativeInteractiveDriver {
            val d = NativeInteractiveDriver(
                spec = spec,
                entry = 10,
                next = mapOf(10 to target),
                outros = mapOf(0 to 14),
                fast = null,
                request = { null },
            )
            d.observe(NativeInteractiveDriver.Observation(10, pos))
            assertNull(d.advance())
            return d
        }

        // Immediate -> 0
        assertEquals(0L, driverFor(11, 150_000L).estimatedFramesUntilPendingSwitch())
        // ExitCue -> exit - pos = 400,000 - 150,000 = 250,000
        assertEquals(250_000L, driverFor(12, 150_000L).estimatedFramesUntilPendingSwitch())
        // NextCustomCue forward: 200,000 - 150,000 = 50,000 (>= 11,392 lead)
        assertEquals(50_000L, driverFor(13, 150_000L).estimatedFramesUntilPendingSwitch())
        // NextCustomCue with insufficient negative destinationStart lead (5,000 < 11,392) wraps +300,000 -> 305,000
        assertEquals(305_000L, driverFor(13, 195_000L).estimatedFramesUntilPendingSwitch())
        // NextCustomCue past cue wraps loop: (400,000 - 250,000) + (200,000 - 100,000) = 250,000
        assertEquals(250_000L, driverFor(13, 250_000L).estimatedFramesUntilPendingSwitch())
        // Outro NextCustomCue skips cue within MIN_OUTRO_LOOKAHEAD_FRAMES (120,000 - 115,000 = 5,000 < 12,000) -> picks 260,000 (145,000)
        assertEquals(145_000L, driverFor(14, 115_000L).estimatedFramesUntilPendingSwitch())
        // Tempo math @ 120 BPM, 48 kHz: beat = 24,000, bar = 96,000, grid(2.0) = 12,000
        assertEquals(14_000L, driverFor(15, 10_000L).estimatedFramesUntilPendingSwitch())
        assertEquals(86_000L, driverFor(16, 10_000L).estimatedFramesUntilPendingSwitch())
        assertEquals(2_000L, driverFor(17, 10_000L).estimatedFramesUntilPendingSwitch())

        // Integer countdown formatting (always integer >= 1, rounded up)
        assertEquals(1, com.mylittleblover.mkwplayer.ui.interactive.formatCountdownIntegerSeconds(0L, 48_000))
        assertEquals(1, com.mylittleblover.mkwplayer.ui.interactive.formatCountdownIntegerSeconds(12_000L, 48_000))
        assertEquals(2, com.mylittleblover.mkwplayer.ui.interactive.formatCountdownIntegerSeconds(48_001L, 48_000))
        assertEquals(5, com.mylittleblover.mkwplayer.ui.interactive.formatCountdownIntegerSeconds(240_000L, 48_000))

        // Artwork bucket & ratio stabilization helpers
        assertEquals(256, com.mylittleblover.mkwplayer.ui.art.normalizeArtworkBucket(132))
        assertEquals(512, com.mylittleblover.mkwplayer.ui.art.normalizeArtworkBucket(0))
        assertEquals(512, com.mylittleblover.mkwplayer.ui.art.normalizeArtworkBucket(480))
        assertEquals(1024, com.mylittleblover.mkwplayer.ui.art.normalizeArtworkBucket(1080))
        assertEquals(16f / 9f, com.mylittleblover.mkwplayer.ui.art.stabilizeArtworkRatio(1280f / 718f), 1e-4f)
        assertEquals(1f, com.mylittleblover.mkwplayer.ui.art.stabilizeArtworkRatio(500f / 492f), 1e-4f)
    }

    @Test
    fun threeFourCoursesEmitNumeratorThreeAndCuesJsonMatchesMinfEventsWithoutZeroPlaceholders() {
        val catalog = minf()
        for ((stage, expectedNumerator) in listOf("BigTree" to 3, "DinoBones" to 3, "FirstCircuit" to 4, "Cinema" to 4)) {
            val pre = catalog.part(stage, "Pre")!!
            val full = catalog.part(stage, "Full")!!
            val confirmed = ConfirmedOutroCatalog.forStage(stage)!!
            val fullFrames = maxOf(full.loopEnd, confirmed.values.flatten().max() + 1)
            val bridge = catalog.part(stage, "Bridge")
            val parts = buildList {
                add(StageFlow.Part(StageFlow.Role.Pre, "BGM_Stage_${stage}_Pre.bwav", pre.loopEnd, 48_000, 2, pre.loopStart, pre.loopEnd))
                if (bridge != null) {
                    add(StageFlow.Part(StageFlow.Role.Bridge, "BGM_Stage_${stage}_Bridge.bwav", bridge.loopEnd, 48_000, 2, null, null))
                }
                add(StageFlow.Part(StageFlow.Role.Main, "BGM_Stage_$stage.bwav", fullFrames, 48_000, 2, full.loopStart, full.loopEnd))
                confirmed.keys.forEach { n ->
                    add(StageFlow.Part(StageFlow.Role.Outro, "BGM_Stage_${stage}_Outro%02d.bwav".format(n), 200_000L, 48_000, 2, null, null, n))
                }
            }
            val flow = StageFlow("BGM_Stage_$stage", parts, StageFlow.DEFAULT_TIMING)
            val prepared = MinfInteractiveCompiler.prepare(
                flow = flow,
                digests = parts.associate { it.fileName to ByteArray(32) },
                catalog = catalog,
            )
            assertEquals(expectedNumerator, prepared.spec.numerator, "$stage must emit bar numerator $expectedNumerator")
        }

        val cuesCatalog = com.mylittleblover.mkwplayer.library.CueCatalog.parse(
            File("src/main/assets/cues.json").readText(),
        )
        assertNotNull(cuesCatalog, "Bundled cues.json must parse cleanly")
        assertEquals(30, cuesCatalog.courses.size)
        for (course in cuesCatalog.courses) {
            assertTrue(course.cues.isNotEmpty(), "${course.code} must have cues")
            assertTrue(course.cues.all { it.frames > 0L }, "${course.code} must not contain 0-frame placeholder cues")
            val rec = catalog.records.single {
                it.logicalName == if (course.code == "Rainbow") "BGM_Rainbow_Area2" else "BGM_Stage_${course.code}"
            }
            val expectedPairs = rec.events
                .filter { it.name.startsWith("Full_ToOutro") }
                .flatMap { ev -> ev.frames.map { ev.name to it } }
                .sortedWith(compareBy({ it.second }, { it.first }))
            val actualPairs = course.cues.map { it.name to it.frames }
            assertEquals(expectedPairs, actualPairs, "cues.json mismatch for ${course.code}")
        }
    }

    @Test
    fun flowEditorEnforcesVerifiedOutroHostsForDonkeyKongRegionsSnowMountainPattern2AndRainbowDefaults() {
        val catalog = minf()
        val medleyCatalog = medleys()
        val sr = 48_000

        fun resolverFor(course: String) = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                val matchesCourse = if (course == "Rainbow") stem.startsWith("BGM_Rainbow_") else stem.contains(course)
                if (!matchesCourse) return null
                val rec = catalog.records.firstOrNull { it.logicalName == stem }
                val frames = when {
                    stem == "BGM_Rainbow_FinalLapFanfare" -> 338_824L
                    else -> maxOf(rec?.loopEnd ?: 2_600_000L, 2_600_000L)
                }
                val hasLoop = rec != null && rec.loopStart < rec.loopEnd
                return CourseFlowRecipeBuilder.AssetHeader(
                    stem = stem,
                    digest = "sha-$stem",
                    frames = frames,
                    sampleRate = sr,
                    loopStart = if (hasLoop) rec!!.loopStart else null,
                    loopEnd = if (hasLoop) rec!!.loopEnd else null,
                )
            }
        }

        // DonkeyKong: Region 1 (Main) hosts Outros; Region2..5 have zero Full_ToOutro events and must NOT host Outros.
        val dkInv = CourseFlowRecipeBuilder.inspectCourse("DonkeyKong", catalog, medleyCatalog, resolverFor("DonkeyKong"))
        assertTrue(dkInv.supportsOutroEnding(listOf(FlowStep("Pre"), FlowStep("Main"))))
        for (r in 2..5) {
            val steps = listOf(FlowStep("Pre"), FlowStep("Main"), FlowStep("Region$r"))
            assertFalse(dkInv.supportsOutroEnding(steps), "DonkeyKong Region$r must not host Outros")
            assertTrue(dkInv.availableOutrosForTail(steps).isEmpty())
        }

        // SnowMountain: Main allows Outro00..07; Pattern2 only allows verified Outro00..05 (no Outro06/Outro07).
        val snowInv = CourseFlowRecipeBuilder.inspectCourse("SnowMountain", catalog, medleyCatalog, resolverFor("SnowMountain"))
        assertEquals((0..7).toList(), snowInv.availableOutrosForTail(listOf(FlowStep("Main"))).map { it.number })
        assertEquals((0..5).toList(), snowInv.availableOutrosForTail(listOf(FlowStep("Main"), FlowStep("Pattern2"))).map { it.number })

        // Rainbow: defaultEditorSteps includes Area2_Outro04 between Area2 and Area3_Pre, and Area2 -> Final uses Fanfare.
        val rbRes = resolverFor("Rainbow")
        val rbInv = CourseFlowRecipeBuilder.inspectCourse("Rainbow", catalog, medleyCatalog, rbRes)
        assertEquals(
            listOf("Area1_Pre", "Area1_Bridge", "Area1", "Area2", "Area2_Outro04", "Area3_Pre", "Area3", "Final_Pre", "Final"),
            rbInv.defaultEditorSteps().map { it.roleKey },
        )
        assertTrue(rbInv.describeTransitionEvent("Area2", "Final").contains("FinalLapFanfare"))
        val area2ToFinal = CourseFlowRecipeBuilder.compileUserPreset(
            preset = CoursePreset(
                id = "user:rb-a2-final",
                courseCode = "Rainbow",
                name = "RB Area2->Final",
                steps = listOf(FlowStep("Area2", exitFrame = 1_000_000L), FlowStep("Final")),
                outroNumber = null,
            ),
            inventory = rbInv,
            minf = catalog,
            resolver = rbRes,
        )
        assertNotNull(area2ToFinal)
        assertTrue(area2ToFinal.medley.clips.any { it.stem == "BGM_Rainbow_FinalLapFanfare" })
    }
}
