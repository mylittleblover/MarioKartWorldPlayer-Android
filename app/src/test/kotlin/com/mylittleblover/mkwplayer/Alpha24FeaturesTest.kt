package com.mylittleblover.mkwplayer

import com.mylittleblover.mkwplayer.audio.PlaybackBehaviorConfig
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.interactive.ConfirmedOutroCatalog
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers
import com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler
import com.mylittleblover.mkwplayer.interactive.InteractivePlaybackPlan
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.interactive.MinfInteractiveCompiler
import com.mylittleblover.mkwplayer.interactive.RainbowInteractiveCompiler
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.player.PlaybackService
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Alpha24FeaturesTest {
    private fun minf(): MinfCatalog =
        MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())

    private fun medleyCatalog(): CourseMedleyCatalog.Catalog =
        CourseMedleyCatalog.parse(File("src/main/assets/course_medley_catalog.json").readText())

    private fun syntheticBwav(
        frames: Int = 480_000,
        sampleRate: Int = 48_000,
        channels: Int = 2,
        loopStart: Int? = 48_000,
        loopEnd: Int? = 432_000,
    ): ByteArray {
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
            buf.putShort(base, 1)
            buf.putInt(base + 4, sampleRate)
            buf.putInt(base + 12, frames)
            if (loopStart != null && loopEnd != null) {
                buf.putInt(base + 56, 1)
                buf.putInt(base + 60, loopEnd - 1)
                buf.putInt(base + 64, loopStart)
            } else {
                buf.putInt(base + 60, -1)
                buf.putInt(base + 64, 0)
            }
        }
        return bytes
    }

    private val identities by lazy {
        com.mylittleblover.mkwplayer.interactive.InteractiveAudioIdentities.parse(
            File("src/main/assets/interactive_audio_identities.json").readText(),
        )
    }

    private fun resolverWith(
        frames: Long = 4_800_000L,
        sampleRate: Int = 48_000,
    ): CourseFlowRecipeBuilder.SourceResolver = object : CourseFlowRecipeBuilder.SourceResolver {
        override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when {
            fileName == FinalLapFanfareCompiler.ASSET ->
                CourseFlowRecipeBuilder.AssetHeader(
                    stem = "BGM_Seq_FinalLapFF",
                    digest = FinalLapFanfareCompiler.SHA256,
                    frames = 268_759L,
                    sampleRate = sampleRate,
                    loopStart = null,
                    loopEnd = null,
                )
            fileName.endsWith("_SFC.bwav") ||
                fileName.endsWith("_SFC_Fast.bwav") ||
                fileName.startsWith("SE_Stage_KoopaTroopaBeach_Speaker_") ||
                identities.digestForFileName(fileName) != null ->
                CourseFlowRecipeBuilder.AssetHeader(
                    stem = fileName.removeSuffix(".bwav"),
                    digest = identities.digestForFileName(fileName) ?: "sha-${fileName.removeSuffix(".bwav")}",
                    frames = frames,
                    sampleRate = sampleRate,
                    loopStart = 48_000L,
                    loopEnd = frames - 48_000L,
                )
            else -> null
        }
    }

    @Test
    fun playbackBehaviorDefaultsToZeroFadeOutAndOffPerfectBeat() {
        val defaults = PlaybackBehaviorConfig()
        assertEquals(0, defaults.segmentSwitchFadeOutMs)
        assertFalse(defaults.perfectBeatEnabled)
    }

    @Test
    fun rainbowInventoryExposesArea2OutrosAndArea1InstWithoutAlteringCatalogContract() {
        assertNull(ConfirmedOutroCatalog.forStage("Rainbow"))

        val minfCatalog = minf()
        val resolver = resolverWith(frames = 5_000_000L)
        val inv = CourseFlowRecipeBuilder.inspectCourse("Rainbow", minfCatalog, medleyCatalog(), resolver)

        assertEquals(listOf(0, 1, 2, 3, 4), inv.outros.map { it.number })
        assertTrue(inv.outros.all { it.mainTriggerPoints.size == 1 && it.outroToNextPreFrame == 382_784L })
        assertNotNull(inv.part("Area1_Inst"))
        assertNull(inv.part("Area1_Inst")?.secondaryStem)
        assertEquals("BGM_Rainbow_Area1_Melody", inv.part("Area1")?.secondaryStem)

        // Area1_Pre cannot skip Area1_Bridge to Area1, but allows direct FinalLap (Final).
        assertEquals(listOf("Area1_Bridge", "Final"), inv.allowedNextRoles(listOf(FlowStep("Area1_Pre"))))
        assertEquals(listOf("Area3", "Final"), inv.allowedNextRoles(listOf(FlowStep("Area3_Pre"))))

        val area1PreToFinal = CoursePreset(
            id = "user:rb-fast",
            courseCode = "Rainbow",
            name = "RB Fast",
            steps = listOf(
                FlowStep("Area1_Pre", exitFrame = 192_000L),
                FlowStep("Final"),
            ),
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(area1PreToFinal, inv, minfCatalog, resolver)
        assertNotNull(compiled)
        assertTrue(compiled.medley.clips.any { it.stem == "BGM_Rainbow_FinalLapFanfare" })
    }

    @Test
    fun specialVariantsExposeVerifiedMetadataForKoopaCinemaSnowMountainAndDonkeyKong() {
        val minfCatalog = minf()
        val resolver = resolverWith(frames = 4_800_000L)
        val medleys = medleyCatalog()

        val koopa = CourseFlowRecipeBuilder.inspectCourse("KoopaTroopaBeach", minfCatalog, medleys, resolver)
        assertEquals("SE_Stage_KoopaTroopaBeach_Speaker_Normal", koopa.part("Main_Speaker")?.secondaryStem)
        assertEquals("SE_Stage_KoopaTroopaBeach_Speaker_Fast", koopa.part("Fast_Speaker")?.secondaryStem)
        assertNull(koopa.part("SFC_Main"))
        assertNull(koopa.part("SFC_Fast"))
        assertTrue(koopa.supportsOutroEnding(listOf(FlowStep("Pre"), FlowStep("Bridge"), FlowStep("Main_Speaker"))))

        val koopaStart = koopa.allowedNextRoles(emptyList())
        val koopaAfterPre = koopa.allowedNextRoles(listOf(FlowStep("Pre")))
        assertTrue("SFC_Main" !in koopaStart && "SFC_Fast" !in koopaStart)
        assertTrue("SFC_Main" !in koopaAfterPre && "SFC_Fast" !in koopaAfterPre)
        assertTrue("Fast_Speaker" in koopa.allowedNextRoles(listOf(FlowStep("Fast"))))
        assertEquals(listOf("Fast"), koopa.allowedNextRoles(listOf(FlowStep("Fast_Speaker"))))

        val cinema = CourseFlowRecipeBuilder.inspectCourse("Cinema", minfCatalog, medleys, resolver)
        assertEquals(27_428L, cinema.part("Pattern2")?.phase)
        assertTrue(cinema.part("Pattern2")?.beats.orEmpty().isNotEmpty())
        assertNull(cinema.part("SFC_Main"))
        assertNull(cinema.part("SFC_Fast"))
        val cinemaStart = cinema.allowedNextRoles(emptyList())
        val cinemaAfterPre = cinema.allowedNextRoles(listOf(FlowStep("Pre")))
        assertTrue("Main" in cinemaAfterPre && "Pattern2" !in cinemaAfterPre && "Fast" in cinemaAfterPre)
        assertTrue("SFC_Main" !in cinemaStart && "SFC_Fast" !in cinemaStart)
        assertTrue("SFC_Main" !in cinemaAfterPre && "SFC_Fast" !in cinemaAfterPre)
        assertEquals(listOf("Pattern2", "Fast", "Fast_Pattern2"), cinema.allowedNextRoles(listOf(FlowStep("Main"))))
        assertEquals(listOf("Main", "Fast_Pattern2", "Fast"), cinema.allowedNextRoles(listOf(FlowStep("Pattern2"))))
        assertTrue("Fast_Pattern2" in cinema.allowedNextRoles(listOf(FlowStep("Fast"))))
        assertEquals(listOf("Fast"), cinema.allowedNextRoles(listOf(FlowStep("Fast_Pattern2"))))

        for (code in listOf("ChocoMountain", "IceBuildings", "SFCMarioCircuit")) {
            val inv = CourseFlowRecipeBuilder.inspectCourse(code, minfCatalog, medleys, resolver)
            assertNull(inv.part("SFC_Main"), "$code must not expose SFC_Main")
            assertNull(inv.part("SFC_Fast"), "$code must not expose SFC_Fast")
        }

        val snow = CourseFlowRecipeBuilder.inspectCourse("SnowMountain", minfCatalog, medleys, resolver)
        assertTrue(snow.part("Pattern2")?.beats.orEmpty().isNotEmpty())
        assertTrue("Pattern2" !in snow.allowedNextRoles(listOf(FlowStep("Pre"))))
        assertEquals(listOf("Pattern2", "Fast", "Fast_Pattern2"), snow.allowedNextRoles(listOf(FlowStep("Main"))))
        assertEquals(listOf("Main", "Fast_Pattern2", "Fast"), snow.allowedNextRoles(listOf(FlowStep("Pattern2"))))
        assertTrue("Fast_Pattern2" in snow.allowedNextRoles(listOf(FlowStep("Fast"))))
        assertEquals(listOf("Fast"), snow.allowedNextRoles(listOf(FlowStep("Fast_Pattern2"))))

        val dk = CourseFlowRecipeBuilder.inspectCourse("DonkeyKong", minfCatalog, medleys, resolver)
        assertTrue((2..5).all { n -> dk.part("Region$n")?.beats.orEmpty().isNotEmpty() })
        assertEquals(listOf("Region2"), dk.allowedNextRoles(listOf(FlowStep("Main"))))
        assertEquals(listOf("Region3"), dk.allowedNextRoles(listOf(FlowStep("Region2"))))
        assertEquals(listOf("Region4"), dk.allowedNextRoles(listOf(FlowStep("Region3"))))
        assertEquals(listOf("Region5"), dk.allowedNextRoles(listOf(FlowStep("Region4"))))
        assertEquals(listOf("Fast"), dk.allowedNextRoles(listOf(FlowStep("Region5"))))
    }

    @Test
    fun preDirectToFastInsertsFanfareAndSegmentSwitchFadeExtendsPostExitWithoutMovingCursor() {
        val minfCatalog = minf()
        val resolver = resolverWith(frames = 4_800_000L, sampleRate = 48_000)
        val inv = CourseFlowRecipeBuilder.inspectCourse("FirstCircuit", minfCatalog, medleyCatalog(), resolver)

        val preToFast = CoursePreset(
            id = "user:pre-fast",
            courseCode = "FirstCircuit",
            name = "Pre Fast",
            steps = listOf(
                FlowStep("Pre", exitFrame = 192_000L),
                FlowStep("Fast", exitFrame = 400_000L),
            ),
        )
        val expectedPreExit = inv.part("Pre")!!.exitCandidates.minBy { kotlin.math.abs(it.frame - 192_000L) }.frame
        val compiledNoFade = CourseFlowRecipeBuilder.compileUserPreset(preToFast, inv, minfCatalog, resolver, segmentSwitchFadeOutMs = 0)
        assertNotNull(compiledNoFade)
        val ffNoFade = compiledNoFade.medley.clips.firstOrNull { it.stem == "BGM_Seq_FinalLapFF" }
        assertNotNull(ffNoFade)
        assertEquals(expectedPreExit + FinalLapFanfareCompiler.FANFARE_DELAY_FRAMES, ffNoFade.start)
        assertEquals(FinalLapFanfareCompiler.FULL_FADE_OUT_FRAMES, compiledNoFade.medley.clips.first { it.stem.endsWith("_Pre") }.fadeOut)

        val compiledWithFade = CourseFlowRecipeBuilder.compileUserPreset(preToFast, inv, minfCatalog, resolver, segmentSwitchFadeOutMs = 1500)
        assertNotNull(compiledWithFade)
        val preClip = compiledWithFade.medley.clips.first { it.stem.endsWith("_Pre") }
        val ffWithFade = compiledWithFade.medley.clips.first { it.stem == "BGM_Seq_FinalLapFF" }
        val expectedExtra = 72_000L // 1.5s @ 48 kHz
        // Handover cursor stays at snapped exit point while tail extends AFTER exit point.
        assertEquals(expectedPreExit + FinalLapFanfareCompiler.FANFARE_DELAY_FRAMES, ffWithFade.start)
        assertEquals(expectedPreExit + expectedExtra, preClip.sourceLength)
        assertEquals(expectedExtra, preClip.fadeOut)
    }

    @Test
    fun inspectExtraGeneratesFineSyntheticBeatGridAndDenseCandidates() {
        val sampleRate = 48_000
        val frames = 960_000L // 20s
        val track = ExtraMusicCatalog.TwoStateTrack(
            id = "sfc_test",
            ostIndex = 1,
            artwork = "t_001.webp",
            names = mapOf("en" to "SFC Test"),
            mainStem = "BGM_Stage_Test_SFC",
            fastStem = "BGM_Stage_Test_SFC_Fast",
            searchAliases = emptyList(),
            main = ExtraMusicCatalog.AssetRef("BGM_Stage_Test_SFC", "sha1", frames, sampleRate, 2, true, 0L, frames),
            fast = ExtraMusicCatalog.AssetRef("BGM_Stage_Test_SFC_Fast", "sha2", frames, sampleRate, 2, true, 0L, frames),
            durationMs = 20_000L,
        )
        val catalog = ExtraMusicCatalog.Catalog(sfcCourses = listOf(track))
        val inv = CourseFlowRecipeBuilder.inspectExtra("extra:sfc_test", catalog, ExtraMedleyCatalog.Catalog(), packDir = null)
        assertNotNull(inv)
        val main = inv.part("Main")!!
        assertTrue(main.beats.size >= 30, "Expected 0.5s synthetic beat grid, got ${main.beats.size}")
        assertTrue(main.exitCandidates.size >= 9, "Expected dense bar candidates, got ${main.exitCandidates.size}")
    }

    @Test
    fun interactivePlansRespectAllowPreludeToFastPerfectBeatAndPostExitSwitchFade() {
        val minfCatalog = minf()
        val sr = 48_000
        fun fcPart(role: StageFlow.Role, minfRole: String, fileName: String): StageFlow.Part {
            val rec = minfCatalog.part("FirstCircuit", minfRole)!!
            val l0 = if (rec.loopStart < rec.loopEnd) rec.loopStart else null
            val l1 = if (rec.loopStart < rec.loopEnd) rec.loopEnd else null
            return StageFlow.Part(
                role = role,
                fileName = fileName,
                frames = maxOf(rec.loopEnd + 48_000L, 4_800_000L),
                sampleRate = rec.rate,
                channels = 2,
                loopStart = l0,
                loopEnd = l1,
            )
        }
        val flow = StageFlow(
            name = "BGM_Stage_FirstCircuit",
            parts = listOf(
                fcPart(StageFlow.Role.PreShort, "Pre_Short", "BGM_Stage_FirstCircuit_Pre_Short.bwav"),
                fcPart(StageFlow.Role.Pre, "Pre", "BGM_Stage_FirstCircuit_Pre.bwav"),
                fcPart(StageFlow.Role.Bridge, "Bridge", "BGM_Stage_FirstCircuit_Bridge.bwav"),
                fcPart(StageFlow.Role.Main, "Full", "BGM_Stage_FirstCircuit.bwav"),
                fcPart(StageFlow.Role.Fast, "Fast", "BGM_Stage_FirstCircuit_Fast.bwav"),
            ),
            timing = StageFlow.Timing(
                preludeExit = G.SyncPoint.NextBar,
                bridgeEntry = G.SyncPoint.NextBar,
                outroEntry = G.SyncPoint.NextBar,
                mainRepeats = 1,
                bridgeFadeOutBeats = 0.0,
                bridgeFadeInBeats = 0.0,
                fastFadeBeats = 0.0,
            ),
        )
        val digests = flow.parts.associate { it.fileName to ByteArray(32) }
        val prepOff = MinfInteractiveCompiler.prepare(flow, digests, minfCatalog, allowPreludeToFast = true, includeBeat1Cues = false)
        val prepOn = MinfInteractiveCompiler.prepare(flow, digests, minfCatalog, allowPreludeToFast = true, includeBeat1Cues = true)
        val segPre = prepOn.compiled.segmentByRole.getValue(StageFlow.Role.Pre)
        val segPreShort = prepOn.compiled.segmentByRole.getValue(StageFlow.Role.PreShort)
        val segFast = prepOn.compiled.segmentByRole.getValue(StageFlow.Role.Fast)

        // Pre and PreShort can transition directly to Fast when allowPreludeToFast=true
        assertTrue(prepOn.spec.transitions.any { segPre in it.sources && segFast in it.destinations })
        assertTrue(prepOn.spec.transitions.any { segPreShort in it.sources && segFast in it.destinations })

        // Perfect beat adds beat1.exit cues on segments with MINF beats
        assertFalse(prepOff.spec.segments.first { it.index == segPre }.cues.any { it.name == "beat1.exit" })
        assertTrue(prepOn.spec.segments.first { it.index == segPre }.cues.any { it.name == "beat1.exit" })

        // Post-exit switch fade-out applies cleanly with postExit=true and zero negative offset
        val fadedSpec = InteractivePlaybackPlan.applySegmentSwitchFadeOut(prepOn.spec, fadeOutMs = 2000)
        val fadedTransitions = fadedSpec.transitions.filter { it.fadeOut.beats > 0.0 }
        assertTrue(fadedTransitions.isNotEmpty())
        assertTrue(fadedTransitions.all { it.postExit && it.fadeOut.offset == 0.0 })

        // Rainbow compiler wires Area1_Pre & Area3_Pre directly to FinalLapFanfare -> Final
        val rainbowSources = RainbowInteractiveCompiler.roles.map { role ->
            val rec = minfCatalog.part("Rainbow", role)!!
            val l0 = if (rec.loopStart < rec.loopEnd) rec.loopStart else null
            val l1 = if (rec.loopStart < rec.loopEnd) rec.loopEnd else null
            RainbowInteractiveCompiler.Source(
                fileName = "BGM_Rainbow_$role.bwav",
                sampleRate = rec.rate,
                channels = 2,
                frames = maxOf(rec.loopEnd + 48_000L, 4_800_000L),
                loopStart = l0,
                loopEnd = l1,
                sha256 = ByteArray(32),
            )
        }
        val rbOff = RainbowInteractiveCompiler.prepare(rainbowSources, minfCatalog, allowPreludeToFast = true, perfectBeatEnabled = false)
        val rbOn = RainbowInteractiveCompiler.prepare(rainbowSources, minfCatalog, allowPreludeToFast = true, perfectBeatEnabled = true)
        val area1Pre = rbOn.segments.getValue("Area1_Pre")
        val area3Pre = rbOn.segments.getValue("Area3_Pre")
        val rbFf = rbOn.segments.getValue("FinalLapFanfare")
        val offTrans = rbOff.spec.transitions.first { area1Pre in it.sources && it.bridge == rbOff.segments.getValue("FinalLapFanfare") }
        val onTrans = rbOn.spec.transitions.first { area1Pre in it.sources && it.bridge == rbFf }
        assertEquals(G.SyncPoint.Immediate, offTrans.sync)
        assertEquals(G.SyncPoint.NextCustomCue, onTrans.sync)
        assertEquals("beat1.exit", onTrans.sourceCue)
        assertTrue(rbOn.spec.transitions.any { area3Pre in it.sources && it.bridge == rbFf })

        // TwoState compiler applies NextBar when perfectBeatEnabled=true and NextBeat when false
        val mainBytes = syntheticBwav(480_000, sr)
        val fastBytes = syntheticBwav(480_000, sr)
        val tsTrack = ExtraMusicCatalog.TwoStateTrack(
            id = "ts",
            ostIndex = null,
            artwork = "",
            names = mapOf("en" to "TS"),
            mainStem = "BGM_TS",
            fastStem = "BGM_TS_Fast",
            searchAliases = emptyList(),
            main = ExtraMusicCatalog.AssetRef("BGM_TS", "s1", 480_000L, sr, 2, true, 0L, 480_000L),
            fast = ExtraMusicCatalog.AssetRef("BGM_TS_Fast", "s2", 480_000L, sr, 2, true, 0L, 480_000L),
            durationMs = 10_000L,
        )
        val planTsOff = ExtraInteractiveCompilers.compileTwoStateLoopPlan(
            tsTrack,
            isBattle = false,
            bytesByStem = mapOf("BGM_TS" to mainBytes, "BGM_TS_Fast" to fastBytes),
            perfectBeatEnabled = false,
            segmentSwitchFadeOutMs = 1000,
        )
        val planTsOn = ExtraInteractiveCompilers.compileTwoStateLoopPlan(
            tsTrack,
            isBattle = false,
            bytesByStem = mapOf("BGM_TS" to mainBytes, "BGM_TS_Fast" to fastBytes),
            perfectBeatEnabled = true,
            segmentSwitchFadeOutMs = 1000,
        )
        assertEquals(G.SyncPoint.NextBeat, planTsOff.spec.transitions.first().sync)
        assertEquals(G.SyncPoint.NextBar, planTsOn.spec.transitions.first().sync)
        assertTrue(planTsOn.spec.transitions.first().postExit)
    }

    @Test
    fun playbackServiceCoverKeyHandlesPackArtworkAndCourseFallback() {
        assertEquals("pack:/tmp/pack|t_001.webp", PlaybackService.coverKeyFor("/tmp/pack", "t_001.webp", "FirstCircuit"))
        assertEquals("course:Rainbow", PlaybackService.coverKeyFor(null, null, "Rainbow"))
        assertNull(PlaybackService.coverKeyFor(null, "", ""))
    }

    @Test
    fun rainbowPlanKeepsFinalLapFanfareResidentForAllEntrySegments() {
        val minfCatalog = minf()
        val bytesByRole = RainbowInteractiveCompiler.roles.associate { role ->
            val rec = minfCatalog.part("Rainbow", role)!!
            val l0 = if (rec.loopStart < rec.loopEnd) rec.loopStart else null
            val l1 = if (rec.loopStart < rec.loopEnd) rec.loopEnd else null
            val totalFrames = maxOf(rec.loopEnd + 48_000L, 480_000L)
            "BGM_Rainbow_$role.bwav" to syntheticBwav(totalFrames.toInt(), rec.rate, loopStart = l0?.toInt(), loopEnd = l1?.let { (it + 1).toInt() })
        }
        val plan = InteractivePlaybackPlan.rainbow(bytesByRole, minfCatalog)
        val area1Pre = plan.spec.segments.first { plan.labels[it.index] == "Area1_Pre" }.index
        val area3Pre = plan.spec.segments.first { plan.labels[it.index] == "Area3_Pre" }.index
        assertTrue("BGM_Rainbow_FinalLapFanfare.bwav" in plan.preparedAssets)
        assertTrue("BGM_Rainbow_FinalLapFanfare.bwav" in plan.entryPreparedAssets[area1Pre].orEmpty())
        assertTrue("BGM_Rainbow_FinalLapFanfare.bwav" in plan.entryPreparedAssets[area3Pre].orEmpty())

        val planBeatFast = InteractivePlaybackPlan.rainbow(
            bytes = bytesByRole,
            catalog = minfCatalog,
            options = InteractivePlaybackPlan.Options(
                perfectBeatEnabled = true,
                allowPreludeToFast = true,
            ),
        )
        planBeatFast.spec.segments.forEach { seg ->
            val frames = seg.cues.map { it.frame }
            assertEquals(frames.sorted(), frames, "Rainbow segment ${planBeatFast.labels[seg.index]} cues must stay sorted")
        }
        assertTrue(planBeatFast.spec.segments.first { it.index == area1Pre }.cues.any { it.name == "beat1.exit" })
    }

    @Test
    fun rainbowArea2OutroTransitionAndPostExitFadeOutCompileAccurately() {
        val minfCatalog = minf()
        val resolver = resolverWith(frames = 5_000_000L)
        val rainbowInv = CourseFlowRecipeBuilder.inspectCourse("Rainbow", minfCatalog, medleyCatalog(), resolver)

        val afterArea2 = rainbowInv.allowedNextRoles(listOf(FlowStep("Area2")))
        assertTrue("Area2_Outro03" in afterArea2)
        assertEquals(listOf("Area3_Pre"), rainbowInv.allowedNextRoles(listOf(FlowStep("Area2_Outro03"))))

        val withArea2Outro = CoursePreset(
            id = "rb-a2-outro",
            courseCode = "Rainbow",
            name = "Rainbow Area 2 Outro",
            steps = listOf(
                FlowStep("Area1_Pre"),
                FlowStep("Area2"),
                FlowStep("Area2_Outro03"),
                FlowStep("Area3_Pre"),
                FlowStep("Area3"),
            ),
            outroNumber = null,
            fadeOutMs = 0,
        )
        val compiledRainbow = CourseFlowRecipeBuilder.compileUserPreset(withArea2Outro, rainbowInv, minfCatalog, resolver)
        assertNotNull(compiledRainbow)
        val rbClips = compiledRainbow!!.medley.clips
        val area2Clip = rbClips.first { it.stem == "BGM_Rainbow_Area2" }
        val outro03Clip = rbClips.first { it.stem == "BGM_Rainbow_Area2_Outro03" }
        val area3PreClip = rbClips.first { it.stem == "BGM_Rainbow_Area3_Pre" }
        assertEquals(3_055_436L, area2Clip.sourceLength)
        assertEquals(area2Clip.start + area2Clip.sourceLength, outro03Clip.start)
        assertEquals(5_000_000L, outro03Clip.sourceLength)
        assertEquals(outro03Clip.start + 382_784L, area3PreClip.start)

        // Post-exit ending fade-out extends AFTER the configured exit point instead of fading before it.
        val fcInv = CourseFlowRecipeBuilder.inspectCourse("FirstCircuit", minfCatalog, medleyCatalog(), resolver)
        val mainExit = 2_400_000L
        val fadePreset = CoursePreset(
            id = "fc-post-exit-fade",
            courseCode = "FirstCircuit",
            name = "Post Exit Fade",
            steps = listOf(FlowStep("Main", loopCount = 0, exitFrame = mainExit)),
            outroNumber = null,
            fadeOutMs = 3000,
        )
        val compiledFade = CourseFlowRecipeBuilder.compileUserPreset(fadePreset, fcInv, minfCatalog, resolver)
        assertNotNull(compiledFade)
        val fadedClip = compiledFade!!.medley.clips.last()
        val expectedFadeFrames = 3L * 48_000L
        assertEquals(mainExit + expectedFadeFrames, fadedClip.sourceLength)
        assertEquals(expectedFadeFrames, fadedClip.fadeOut)
    }

    @Test
    fun unlockUiExposesNumericProgressCountsForLocalizedUi() {
        val unlock = com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock(
            eligibleStages = setOf("A", "B", "C", "D"),
            state = com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock.UnlockState.initial().copy(
                completedStages = setOf("A", "C"),
            ),
        )
        val ui = com.mylittleblover.mkwplayer.player.UnlockUi.from(unlock, offered = false)
        assertEquals(2, ui.completedCount)
        assertEquals(4, ui.eligibleCount)
    }

    @Test
    fun flowEditorAppliesRuleCrossfadeToRainbowArea3ToFinalPre() {
        val minfCatalog = minf()
        val resolver = resolverWith(frames = 5_000_000L)
        val rainbowInv = CourseFlowRecipeBuilder.inspectCourse("Rainbow", minfCatalog, medleyCatalog(), resolver)

        val preset = CoursePreset(
            id = "rb-final-pre-fade",
            courseCode = "Rainbow",
            name = "Area3 to FinalPre",
            steps = listOf(
                FlowStep("Area3", loopCount = 0, exitFrame = 1_000_000L),
                FlowStep("Final_Pre", loopCount = 0, exitFrame = 500_000L),
            ),
            fadeOutMs = 0,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, rainbowInv, minfCatalog, resolver)
        assertNotNull(compiled)
        val clips = compiled!!.medley.clips
        assertEquals(2, clips.size)
        val area3Clip = clips[0]
        val finalPreClip = clips[1]
        assertEquals("BGM_Rainbow_Area3", area3Clip.stem)
        assertEquals("BGM_Rainbow_Final_Pre", finalPreClip.stem)
        assertEquals(1_201_600L, area3Clip.sourceLength) // 1.20s hold (57,600) + 3.00s fadeOut (144,000)
        assertEquals(144_000L, area3Clip.fadeOut)
        assertEquals(120_000L, finalPreClip.fadeIn) // 2.50s fadeIn
        assertEquals(1_000_000L, finalPreClip.start)
    }

    @Test
    fun flowEditorPostExitFadeOutWrapsLoopBoundariesForFullDuration() {
        val minfCatalog = minf()
        val sr = 48_000
        val loopStart = 200_000L
        val loopEnd = 680_000L
        val totalAssetFrames = 720_000L
        val exitFrame = loopEnd - 96_000L // only 2s left before loopEnd; EOF is only 2.83s away
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader {
                val stem = fileName.removeSuffix(".bwav")
                val digest = "f".repeat(64)
                return CourseFlowRecipeBuilder.AssetHeader(
                    stem = stem,
                    digest = digest,
                    frames = totalAssetFrames,
                    sampleRate = sr,
                    loopStart = loopStart,
                    loopEnd = loopEnd,
                )
            }
        }
        val inv = CourseFlowRecipeBuilder.inspectCourse("FirstCircuit", minfCatalog, medleyCatalog(), resolver)
        val preset = CoursePreset(
            id = "wrap-fade-10s",
            courseCode = "FirstCircuit",
            name = "10s Loop Wrap Fade",
            steps = listOf(FlowStep("Main", loopCount = 0, exitFrame = exitFrame)),
            fadeOutMs = 10_000,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, inv, minfCatalog, resolver)
        assertNotNull(compiled)
        val medley = compiled!!.medley
        val fadeFrames = 10L * sr
        // Full 10s past the configured exit point must be preserved across loop wrap!
        assertEquals(exitFrame + fadeFrames, medley.totalFrames)
        assertTrue(medley.clips.size >= 2)
        assertEquals(exitFrame, medley.clips[0].sourceLength - 96_000L)
        assertEquals(loopEnd, medley.clips[0].sourceLength)
        assertEquals(loopStart, medley.clips[1].sourceOffset)
        assertEquals(fadeFrames - 96_000L, medley.clips[1].sourceLength)
        assertTrue(medley.clips[0].volumePoints.isNotEmpty())
        assertTrue(medley.clips[1].volumePoints.isNotEmpty())
        assertEquals(0.0, medley.clips[0].volumePoints.first().value, 1e-6)
        assertEquals(-96.0, medley.clips.last().volumePoints.last().value, 1e-3)

        // Verify CourseMedleyCompiler builds a valid v13+ packet with clipEnvelopes
        val fakeBwav = syntheticBwav(totalAssetFrames.toInt(), sr, loopStart = loopStart.toInt(), loopEnd = loopEnd.toInt())
        val graph = com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.compileGraph(
            medley = medley,
            sampleRate = sr,
            bytesByStem = mapOf(medley.clips[0].stem to fakeBwav),
        )
        assertTrue(graph.packet.size > 16)
    }

    @Test
    fun rainbowDirectBridgeRespects128MiBBudgetWhenRealTailsExist() {
        val minfCatalog = minf()
        val bytesByRole = RainbowInteractiveCompiler.roles.associate { role ->
            val rec = minfCatalog.part("Rainbow", role)!!
            val l0 = if (rec.loopStart < rec.loopEnd) rec.loopStart else null
            val l1 = if (rec.loopStart < rec.loopEnd) rec.loopEnd else null
            // Push Area1_Inst & Area1_Melody slightly larger so Area1_Inst+Area1_Melody+Area1_Bridge+FinalLapFanfare > 128 MiB
            val extra = if (role in setOf("Area1_Inst", "Area1_Melody")) 600_000L else 48_000L
            val totalFrames = maxOf(rec.loopEnd + extra, 480_000L)
            "BGM_Rainbow_$role.bwav" to syntheticBwav(totalFrames.toInt(), rec.rate, loopStart = l0?.toInt(), loopEnd = l1?.let { (it + 1).toInt() })
        }
        val plan = InteractivePlaybackPlan.rainbow(
            bytes = bytesByRole,
            catalog = minfCatalog,
            options = InteractivePlaybackPlan.Options(perfectBeatEnabled = true, allowPreludeToFast = true),
        )
        val direct = assertNotNull(plan.directBridge)
        val directPrepared = plan.preparedAssets + plan.entryPreparedAssets[direct].orEmpty()
        val directBytes = plan.spec.assets.filter { it.path in directPrepared }.sumOf { it.frames * it.channels * 4L }
        assertTrue(directBytes <= 128L * 1024 * 1024, "Direct bridge prepared bytes ($directBytes) must stay <= 128 MiB")
        assertTrue("BGM_Rainbow_FinalLapFanfare.bwav" in plan.entryPreparedAssets[plan.entry].orEmpty())
    }

    @Test
    fun flowEditorAppliesExactOverlapCrossfadeForCinemaAndSnowMountain() {
        val minfCatalog = minf()
        val resolver = resolverWith(frames = 5_000_000L)

        // Verified v1.8.0: Cinema uses 0.5s (24,000 f) both ways; SnowMountain uses 0.5s enter / 2.0s exit Pattern2.
        val cinemaInv = CourseFlowRecipeBuilder.inspectCourse("Cinema", minfCatalog, medleyCatalog(), resolver)
        val cinemaPreset = CoursePreset(
            id = "cinema-exact-xfade",
            courseCode = "Cinema",
            name = "Cinema Main/Pattern2 Exact Xfade",
            steps = listOf(
                FlowStep("Main", loopCount = 0, exitFrame = 1_000_000L),
                FlowStep("Pattern2", loopCount = 0, exitFrame = 1_500_000L),
                FlowStep("Main", loopCount = 0, exitFrame = 2_000_000L),
            ),
            fadeOutMs = 0,
        )
        val cinemaCompiled = CourseFlowRecipeBuilder.compileUserPreset(cinemaPreset, cinemaInv, minfCatalog, resolver)
        assertNotNull(cinemaCompiled)
        val cinemaClips = cinemaCompiled.medley.clips
        assertEquals(3, cinemaClips.size)
        assertEquals("BGM_Stage_Cinema", cinemaClips[0].stem)
        assertEquals(1_024_000L, cinemaClips[0].sourceLength)
        assertEquals(24_000L, cinemaClips[0].fadeOut)
        assertEquals("BGM_Stage_Cinema_Pattern2", cinemaClips[1].stem)
        assertEquals(1_000_000L, cinemaClips[1].start)
        assertEquals(1_000_000L, cinemaClips[1].sourceOffset)
        assertEquals(524_000L, cinemaClips[1].sourceLength)
        assertEquals(24_000L, cinemaClips[1].fadeIn)
        assertEquals(24_000L, cinemaClips[1].fadeOut)
        assertEquals("BGM_Stage_Cinema", cinemaClips[2].stem)
        assertEquals(1_500_000L, cinemaClips[2].start)
        assertEquals(1_500_000L, cinemaClips[2].sourceOffset)
        assertEquals(500_000L, cinemaClips[2].sourceLength)
        assertEquals(24_000L, cinemaClips[2].fadeIn)
        assertEquals(
            cinemaCompiled.medley.totalFrames,
            CourseFlowRecipeBuilder.estimateTimelineFrames(cinemaPreset.steps, cinemaPreset.outroNumber, cinemaPreset.fadeOutMs, cinemaInv),
        )

        val cinemaMainPart = cinemaInv.part("Main")!!
        val cinemaPat2Part = cinemaInv.part("Pattern2")!!
        val mainLoopEnd = cinemaMainPart.loopEnd!!
        val pat2LoopStart = cinemaPat2Part.loopStart!!
        val cinemaLoopWrapPreset = CoursePreset(
            id = "cinema-wrap-xfade",
            courseCode = "Cinema",
            name = "Cinema Main LoopEnd -> Pattern2",
            steps = listOf(
                FlowStep("Main", loopCount = 0, exitFrame = mainLoopEnd),
                FlowStep("Pattern2", loopCount = 0, exitFrame = 1_200_000L),
            ),
            fadeOutMs = 0,
        )
        val cinemaLoopWrapCompiled = CourseFlowRecipeBuilder.compileUserPreset(cinemaLoopWrapPreset, cinemaInv, minfCatalog, resolver)!!
        val wrappedPat2 = cinemaLoopWrapCompiled.medley.clips.first { it.stem == "BGM_Stage_Cinema_Pattern2" }
        assertEquals(pat2LoopStart, wrappedPat2.sourceOffset, "Exiting Main at loopEnd must resume Pattern2 at shared loopStart")

        val pastLoopWrapPreset = CoursePreset(
            id = "cinema-wrap-past-xfade",
            courseCode = "Cinema",
            name = "Cinema Main Past LoopEnd -> Pattern2",
            steps = listOf(
                FlowStep("Main", loopCount = 0),
                FlowStep("Pattern2", loopCount = 0, exitFrame = 1_200_000L),
            ),
            fadeOutMs = 0,
        )
        val pastLoopCompiled = CourseFlowRecipeBuilder.compileUserPreset(pastLoopWrapPreset, cinemaInv, minfCatalog, resolver)!!
        val pastPat2 = pastLoopCompiled.medley.clips.first { it.stem == "BGM_Stage_Cinema_Pattern2" }
        assertEquals(pat2LoopStart + (cinemaMainPart.frames - mainLoopEnd), pastPat2.sourceOffset)

        val cinemaFastPreset = CoursePreset(
            id = "cinema-fast-xfade",
            courseCode = "Cinema",
            name = "Cinema Fast/Pattern2 Exact Xfade",
            steps = listOf(
                FlowStep("Fast", loopCount = 0, exitFrame = 1_000_000L),
                FlowStep("Fast_Pattern2", loopCount = 0, exitFrame = 1_500_000L),
            ),
            fadeOutMs = 0,
        )
        val cinemaFastCompiled = CourseFlowRecipeBuilder.compileUserPreset(cinemaFastPreset, cinemaInv, minfCatalog, resolver)
        assertNotNull(cinemaFastCompiled)
        val cfClips = cinemaFastCompiled.medley.clips
        assertEquals(1_024_000L, cfClips[0].sourceLength)
        assertEquals(24_000L, cfClips[0].fadeOut)
        assertEquals(1_000_000L, cfClips[1].start)
        assertEquals(1_000_000L, cfClips[1].sourceOffset)
        assertEquals(24_000L, cfClips[1].fadeIn)

        val snowInv = CourseFlowRecipeBuilder.inspectCourse("SnowMountain", minfCatalog, medleyCatalog(), resolver)
        val snowPreset = CoursePreset(
            id = "snow-xfade",
            courseCode = "SnowMountain",
            name = "SnowMountain Fast/Pattern2 Xfade",
            steps = listOf(
                FlowStep("Fast", loopCount = 0, exitFrame = 1_000_000L),
                FlowStep("Fast_Pattern2", loopCount = 0, exitFrame = 1_500_000L),
                FlowStep("Fast", loopCount = 0, exitFrame = 2_000_000L),
            ),
            fadeOutMs = 0,
        )
        val snowCompiled = CourseFlowRecipeBuilder.compileUserPreset(snowPreset, snowInv, minfCatalog, resolver)
        assertNotNull(snowCompiled)
        assertEquals(24_000L, snowCompiled.medley.clips[0].fadeOut)
        assertEquals(1_000_000L, snowCompiled.medley.clips[1].start)
        assertEquals(1_000_000L, snowCompiled.medley.clips[1].sourceOffset)
        assertEquals(24_000L, snowCompiled.medley.clips[1].fadeIn)
        assertEquals(96_000L, snowCompiled.medley.clips[1].fadeOut)
        assertEquals(96_000L, snowCompiled.medley.clips[2].fadeIn)
    }

    @Test
    fun flowEditorAppliesSpeakerTrackFadeInAndOutForKoopaTroopaBeach() {
        val minfCatalog = minf()
        val resolver = resolverWith(frames = 5_000_000L)
        val koopaInv = CourseFlowRecipeBuilder.inspectCourse("KoopaTroopaBeach", minfCatalog, medleyCatalog(), resolver)

        val preset = CoursePreset(
            id = "koopa-speaker-fades",
            courseCode = "KoopaTroopaBeach",
            name = "Koopa Speaker Fades",
            steps = listOf(
                FlowStep("Main", loopCount = 0, exitFrame = 1_000_000L),
                FlowStep("Main_Speaker", loopCount = 0, exitFrame = 1_500_000L),
                FlowStep("Main", loopCount = 0, exitFrame = 2_000_000L),
            ),
            fadeOutMs = 0,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, koopaInv, minfCatalog, resolver)
        assertNotNull(compiled)
        val clips = compiled.medley.clips
        // Step 0 Main stays crisp, Step 1 Main_Speaker continues at 1_000_000 and fades Speaker in 1s & out 1s
        val main0 = clips[0]
        val main1 = clips[1]
        val spk1 = clips[2]
        val main2 = clips[3]
        assertEquals(0L, main0.fadeOut)
        assertEquals(1_000_000L, main1.start)
        assertEquals(1_000_000L, main1.sourceOffset)
        assertEquals(0L, main1.fadeIn)
        assertEquals(0L, main1.fadeOut)
        assertEquals("SE_Stage_KoopaTroopaBeach_Speaker_Normal", spk1.stem)
        assertEquals(1_000_000L, spk1.start)
        assertEquals(1_000_000L, spk1.sourceOffset)
        assertEquals(48_000L, spk1.fadeIn)
        assertEquals(48_000L, spk1.fadeOut)
        val speakerExit = main1.start + main1.sourceLength
        assertEquals(main1.sourceLength + 48_000L, spk1.sourceLength)
        assertEquals(speakerExit, main2.start)
        assertEquals(1_500_000L, main2.sourceOffset)
        assertEquals(0L, main2.fadeIn)
        assertEquals(
            compiled.medley.totalFrames,
            CourseFlowRecipeBuilder.estimateTimelineFrames(preset.steps, preset.outroNumber, preset.fadeOutMs, koopaInv),
        )
    }

    @Test
    fun flowEditorAppliesRainbowArea1MelodyFadeOnlyBetweenInstAndNormal() {
        val minfCatalog = minf()
        val resolver = resolverWith(frames = 9_000_000L)
        val rbInv = CourseFlowRecipeBuilder.inspectCourse("Rainbow", minfCatalog, medleyCatalog(), resolver)

        // Bridge -> Area1: Melody enters at full volume (fadeIn = 0L), matching game behavior.
        val bridgeToArea1 = CoursePreset(
            id = "rb-bridge-area1",
            courseCode = "Rainbow",
            name = "Bridge to Area1",
            steps = listOf(
                FlowStep("Area1_Bridge", loopCount = 0),
                FlowStep("Area1", loopCount = 0, exitFrame = 2_500_000L),
            ),
            fadeOutMs = 0,
        )
        val bridgeCompiled = CourseFlowRecipeBuilder.compileUserPreset(bridgeToArea1, rbInv, minfCatalog, resolver)
        assertNotNull(bridgeCompiled)
        val melodyFromBridge = bridgeCompiled.medley.clips.first { it.stem == "BGM_Rainbow_Area1_Melody" }
        assertEquals(0L, melodyFromBridge.fadeIn)

        // Area1_Inst -> Area1 -> Area1_Inst: Melody fades in 1s on entry and fades out 1s on exit; Inst continues seamlessly without rewinding.
        val instToggle = CoursePreset(
            id = "rb-inst-toggle",
            courseCode = "Rainbow",
            name = "Area1 Inst <-> Normal",
            steps = listOf(
                FlowStep("Area1_Inst", loopCount = 0, exitFrame = 1_000_000L),
                FlowStep("Area1", loopCount = 0, exitFrame = 1_500_000L),
                FlowStep("Area1_Inst", loopCount = 0, exitFrame = 2_000_000L),
            ),
            fadeOutMs = 0,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(instToggle, rbInv, minfCatalog, resolver)
        assertNotNull(compiled)
        val clips = compiled.medley.clips
        assertEquals(4, clips.size)
        val inst0 = clips[0]
        val inst1 = clips[1]
        val mel1 = clips[2]
        val inst2 = clips[3]
        assertEquals(0L, inst0.fadeOut)
        assertEquals(1_000_000L, inst1.start)
        assertEquals(1_000_000L, inst1.sourceOffset)
        assertEquals(0L, inst1.fadeIn)
        assertEquals(0L, inst1.fadeOut)
        assertEquals("BGM_Rainbow_Area1_Melody", mel1.stem)
        assertEquals(1_000_000L, mel1.start)
        assertEquals(1_000_000L, mel1.sourceOffset)
        assertEquals(48_000L, mel1.fadeIn)
        assertEquals(48_000L, mel1.fadeOut)
        assertEquals(inst1.sourceLength + 48_000L, mel1.sourceLength)
        assertEquals(inst1.start + inst1.sourceLength, inst2.start)
        assertEquals(1_500_000L, inst2.sourceOffset)
        assertEquals(0L, inst2.fadeIn)
        assertEquals(
            compiled.medley.totalFrames,
            CourseFlowRecipeBuilder.estimateTimelineFrames(instToggle.steps, instToggle.outroNumber, instToggle.fadeOutMs, rbInv),
        )
    }

    @Test
    fun specialCupRulesExcludeBigTreeAndSfcMarioCircuitLeaving27Courses() {
        val officialJson = org.json.JSONObject(File("src/main/assets/official_routes.json").readText())
        val routeRows = officialJson.getJSONArray("routes")
        val courseCodes = buildSet {
            for (i in 0 until routeRows.length()) {
                val stops = routeRows.getJSONObject(i).getJSONArray("stops")
                for (j in 0 until stops.length()) {
                    add(stops.getJSONObject(j).getString("stage").removeSuffix("_00"))
                }
            }
        }
        assertEquals(30, courseCodes.size)
        val rawNames = courseCodes.map { "BGM_Stage_${it}_00.bwav" }
        val eligible = com.mylittleblover.mkwplayer.unlock.SpecialCupRules.eligibleStages(rawNames)
        assertEquals(27, eligible.size)
        assertFalse("Rainbow" in eligible)
        assertFalse("BigTree" in eligible)
        assertFalse("SFCMarioCircuit" in eligible)
    }

    @Test
    fun linearQueueAndPlaylistStoreSupportBatchOperations() {
        val q = com.mylittleblover.mkwplayer.player.LinearQueue()
        val base = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(key = "k0", title = "Zero")
        val a = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(key = "ka", title = "A")
        val b = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(key = "kb", title = "B")
        val c = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(key = "kc", title = "C")
        q.startSession(listOf(base), 0)
        q.enqueueNextBatch(listOf(a, b))
        q.enqueueEndBatch(listOf(c))
        val snap = q.snapshot()
        assertEquals(listOf("ka", "kb"), snap.tempQueue.map { it.key })
        assertEquals(listOf("kc"), snap.upNext.map { it.key })

        val dir = java.nio.file.Files.createTempDirectory("mkw-pl-batch").toFile()
        val store = com.mylittleblover.mkwplayer.library.PlaylistStore(java.io.File(dir, "playlists.json"))
        val created = store.createWithCourses("Mix", listOf("FirstCircuit", "jukebox:t1", "sfc:BigTree")).getOrThrow().single()
        assertEquals(listOf("FirstCircuit", "jukebox:t1", "sfc:BigTree"), created.courseIds)
        val appended = store.addCourses(created.id, listOf("Cinema", "SnowMountain")).getOrThrow().single()
        assertEquals(5, appended.courseIds.size)
        val removed = store.removeCourses(created.id, listOf(1, 3)).getOrThrow().single()
        assertEquals(listOf("FirstCircuit", "sfc:BigTree", "SnowMountain"), removed.courseIds)
    }
}
