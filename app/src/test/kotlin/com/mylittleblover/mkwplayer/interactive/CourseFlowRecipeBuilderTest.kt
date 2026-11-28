package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.PresetRefs
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CourseFlowRecipeBuilderTest {
    private fun minf(): MinfCatalog =
        MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())

    private fun medleyCatalog(): CourseMedleyCatalog.Catalog =
        CourseMedleyCatalog.parse(File("src/main/assets/course_medley_catalog.json").readText())

    private fun syntheticBwav(
        frames: Int,
        sampleRate: Int,
        channels: Int = 2,
        loopStart: Int? = null,
        loopEnd: Int? = null,
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

    @Test
    fun compileUserPresetUsesMeasuredSampleRateAndVerifiedTransitions() {
        val sampleRate = 44_100
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when {
                fileName == "BGM_Stage_FirstCircuit.bwav" ||
                    fileName.endsWith("_Pre_Short.bwav") || fileName.endsWith("_Pre.bwav") ||
                    fileName.endsWith("_Bridge.bwav") || fileName.endsWith("_Full.bwav") ||
                    fileName.endsWith("_Fast.bwav") || fileName.contains("_Outro") -> {
                    CourseFlowRecipeBuilder.AssetHeader(
                        stem = fileName.removeSuffix(".bwav"),
                        digest = "sha-${fileName.removeSuffix(".bwav")}",
                        frames = 200_000L,
                        sampleRate = sampleRate,
                        loopStart = 10_000L,
                        loopEnd = 150_000L,
                    )
                }
                else -> null
            }
        }

        val minfCatalog = minf()
        val inventory = CourseFlowRecipeBuilder.inspectCourse(
            courseCode = "FirstCircuit",
            minf = minfCatalog,
            medleyCatalog = medleyCatalog(),
            resolver = resolver,
        )
        assertEquals(sampleRate, inventory.sampleRate)
        assertTrue(inventory.parts.any { it.roleKey == "Pre_Short" })
        assertTrue(inventory.parts.any { it.roleKey == "Pre" })
        assertTrue(inventory.outros.any { it.supportsNativeHandover })

        val preset = CoursePreset(
            id = "user:fc-test",
            courseCode = "FirstCircuit",
            name = "Test Flow",
            steps = listOf(
                FlowStep("Pre_Short"),
                FlowStep("Pre"),
                FlowStep("Main", loopCount = 1),
            ),
            outroNumber = null,
            fadeOutMs = 2000,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, inventory, minfCatalog, resolver)
        assertNotNull(compiled)
        assertEquals(sampleRate, compiled.sampleRate)
        assertTrue(compiled.medley.clips.any { it.fadeOut == 2L * sampleRate })

        val bytesByStem = compiled.medley.uniqueStems.associateWith {
            syntheticBwav(frames = 250_000, sampleRate = sampleRate)
        }
        val packetResult = CourseMedleyCompiler.compileGraph(compiled.medley, compiled.sampleRate, bytesByStem)
        assertTrue(GraphProbe.inspect(packetResult.packet) is GraphProbe.Result.Ok)
    }

    @Test
    fun deletedUserPresetRefFallsBackToSystemDefault() {
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader =
                CourseFlowRecipeBuilder.AssetHeader(
                    stem = fileName.removeSuffix(".bwav"),
                    digest = "sha-${fileName.removeSuffix(".bwav")}",
                    frames = 2_000_000L,
                    sampleRate = 48_000,
                    loopStart = null,
                    loopEnd = null,
                )
        }
        val resolved = CourseFlowRecipeBuilder.resolvePresetRef(
            courseCode = "FirstCircuit",
            presetRef = "${PresetRefs.USER_PREFIX}deleted-id",
            userPresets = emptyList(),
            minf = minf(),
            medleyCatalog = medleyCatalog(),
            resolver = resolver,
        )
        assertNotNull(resolved)
        assertEquals("FirstCircuit", resolved.medley.courseCode)
    }

    @Test
    fun inspectExtraSupportsTwoStateAndInteractiveTitle() {
        val packDir = java.nio.file.Files.createTempDirectory("mkw-extra-pack").toFile()
        val audioDir = File(packDir, "audio").apply { mkdirs() }
        val mainBytes = syntheticBwav(frames = 240_000, sampleRate = 48_000, loopStart = 0, loopEnd = 240_000)
        val fastBytes = syntheticBwav(frames = 192_000, sampleRate = 48_000, loopStart = 0, loopEnd = 192_000)
        File(audioDir, "sha-main").writeBytes(mainBytes)
        File(audioDir, "sha-fast").writeBytes(fastBytes)
        val mainRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Battle_Main", "sha-main", 240_000L, 48_000, 2, true, 0L, 240_000L)
        val fastRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Battle_Fast", "sha-fast", 192_000L, 48_000, 2, true, 0L, 192_000L)
        val battleTrack = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.TwoStateTrack(
            id = "battle_test",
            ostIndex = null,
            artwork = "",
            names = mapOf("en" to "Battle Test"),
            mainStem = mainRef.stem,
            fastStem = fastRef.stem,
            searchAliases = emptyList(),
            main = mainRef,
            fast = fastRef,
            durationMs = 5000L,
        )
        val catalog = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog(battleTracks = listOf(battleTrack))
        val inv = CourseFlowRecipeBuilder.inspectExtra(
            extraId = "extra:battle_test",
            extraCatalog = catalog,
            extraMedleyCatalog = ExtraMedleyCatalog.Catalog(),
            packDir = packDir,
            hasFanfareAsset = false,
        )
        assertNotNull(inv)
        assertTrue(inv.parts.any { it.roleKey == "Main" })
        assertTrue(inv.parts.any { it.roleKey == "Fast" })

        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when (fileName) {
                "${mainRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(mainRef.stem, mainRef.sha256, mainRef.frames, mainRef.sampleRate, 0L, mainRef.frames)
                "${fastRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(fastRef.stem, fastRef.sha256, fastRef.frames, fastRef.sampleRate, 0L, fastRef.frames)
                else -> null
            }
        }
        val preset = CoursePreset(
            id = "user:extra-1",
            courseCode = "extra:battle_test",
            name = "Battle Mix",
            steps = listOf(FlowStep("Main", loopCount = 1), FlowStep("Fast", loopCount = 1)),
            outroNumber = null,
            fadeOutMs = 2000,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, inv, minf(), resolver)
        assertNotNull(compiled)
        val exported = org.json.JSONObject(CourseFlowRecipeBuilder.exportPresetConfigJson(preset, compiled))
        assertEquals("battle_test", exported.getString("targetId"))
        assertEquals("extra:battle_test", exported.getJSONObject("medley").getString("courseCode"))
    }

    @Test
    fun finalLapFanfareInsertsBeforeFastAndFollowsInteractiveOverlapRule() {
        val sampleRate = 48_000
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
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
                fileName == "BGM_Stage_FirstCircuit.bwav" || fileName == "BGM_Stage_FirstCircuit_Fast.bwav" ->
                    CourseFlowRecipeBuilder.AssetHeader(
                        stem = fileName.removeSuffix(".bwav"),
                        digest = "sha-${fileName.removeSuffix(".bwav")}",
                        frames = 400_000L,
                        sampleRate = sampleRate,
                        loopStart = 20_000L,
                        loopEnd = 380_000L,
                    )
                else -> null
            }
        }
        val minfCatalog = minf()
        val inv = CourseFlowRecipeBuilder.inspectCourse("FirstCircuit", minfCatalog, medleyCatalog(), resolver)
        assertTrue(inv.hasFanfareAsset)
        assertTrue(inv.describeTransitionEvent("Main", "Fast").contains("FinalLapFF"))

        val mainPart = inv.part("Main")!!
        val beatExit = inv.part("Main")!!.exitCandidates.first().frame
        val preset = CoursePreset(
            id = "user:fc-fast",
            courseCode = "FirstCircuit",
            name = "Main To Fast",
            steps = listOf(
                FlowStep("Main", loopCount = 0, exitFrame = beatExit),
                FlowStep("Fast", loopCount = 0),
            ),
            outroNumber = null,
            fadeOutMs = 2000,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, inv, minfCatalog, resolver)
        assertNotNull(compiled)
        val mainClip = compiled.medley.clips.firstOrNull { it.stem == "BGM_Stage_FirstCircuit" }
        val ffClip = compiled.medley.clips.firstOrNull { it.stem == "BGM_Seq_FinalLapFF" }
        val fastClip = compiled.medley.clips.firstOrNull { it.stem == "BGM_Stage_FirstCircuit_Fast" }
        assertNotNull(mainClip)
        assertNotNull(ffClip)
        assertNotNull(fastClip)
        assertEquals(4_800L, mainClip.fadeOut)
        assertEquals(beatExit + 6_384L, ffClip.start)
        assertEquals(268_759L, ffClip.sourceLength)
        assertEquals(beatExit + 194_400L, fastClip.start)
        assertEquals(0L, fastClip.sourceOffset)
        assertTrue(mainPart.beats.isNotEmpty())
    }

    @Test
    fun extraTrackEditedExitPointChangesSeamPreviewWhenLoopingOrBeforeOutro() {
        val sampleRate = 48_000
        val packDir = java.nio.file.Files.createTempDirectory("mkw-extra-seam").toFile()
        val audioDir = File(packDir, "audio").apply { mkdirs() }
        val mainFrames = 960_000L // 20s
        val fastFrames = 480_000L // 10s
        val outroFrames = 384_000L // 8s
        val harmFrames = 480_000L // 10s
        File(audioDir, "sha-main").writeBytes(syntheticBwav(mainFrames.toInt(), sampleRate, loopStart = 0, loopEnd = mainFrames.toInt()))
        File(audioDir, "sha-fast").writeBytes(syntheticBwav(fastFrames.toInt(), sampleRate, loopStart = 0, loopEnd = fastFrames.toInt()))
        File(audioDir, "sha-outro").writeBytes(syntheticBwav(outroFrames.toInt(), sampleRate))
        File(audioDir, "sha-harm").writeBytes(syntheticBwav(harmFrames.toInt(), sampleRate))

        val mainRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Battle_Main", "sha-main", mainFrames, sampleRate, 2, true, 0L, mainFrames)
        val fastRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Battle_Fast", "sha-fast", fastFrames, sampleRate, 2, true, 0L, fastFrames)
        val harmRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Title_Harm", "sha-harm", harmFrames, sampleRate, 2, false, null, null)
        val outroRef = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef("BGM_Title_Outro01", "sha-outro", outroFrames, sampleRate, 2, false, null, null)

        val battleTrack = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.TwoStateTrack(
            id = "battle_seam",
            ostIndex = null,
            artwork = "",
            names = mapOf("en" to "Battle Seam"),
            mainStem = mainRef.stem,
            fastStem = fastRef.stem,
            searchAliases = emptyList(),
            main = mainRef,
            fast = fastRef,
            durationMs = 20_000L,
        )
        val titleTrack = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle(
            id = "title_seam",
            ostIndex = null,
            artwork = "",
            names = mapOf("en" to "Title Seam"),
            searchAliases = emptyList(),
            durationMs = 20_000L,
            harmonica = harmRef,
            main = mainRef,
            brassCut = fastRef,
            outros = listOf(
                com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.TitleOutroSpec(
                    index = 1,
                    cueName = "Outro01",
                    mainExitFrame = 720_000L,
                    brassCutExitFrame = 240_000L,
                    asset = outroRef,
                ),
            ),
        )
        val catalog = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog(
            battleTracks = listOf(battleTrack),
            otherMusic = listOf(titleTrack),
        )
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when (fileName) {
                "${mainRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(mainRef.stem, mainRef.sha256, mainRef.frames, sampleRate, 0L, mainFrames)
                "${fastRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(fastRef.stem, fastRef.sha256, fastRef.frames, sampleRate, 0L, fastFrames)
                "${harmRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(harmRef.stem, harmRef.sha256, harmRef.frames, sampleRate, null, null)
                "${outroRef.stem}.bwav" -> CourseFlowRecipeBuilder.AssetHeader(outroRef.stem, outroRef.sha256, outroRef.frames, sampleRate, null, null)
                else -> null
            }
        }

        // 1. Two-state extra track with loopCount = 1: editing Main exitFrame must move the seam preview slice.
        val battleInv = CourseFlowRecipeBuilder.inspectExtra("extra:battle_seam", catalog, ExtraMedleyCatalog.Catalog(), packDir)!!
        val earlyExit = 288_000L // 6s
        val lateExit = 624_000L // 13s
        val seamEarly = CourseFlowRecipeBuilder.compileSeamPreview(
            preset = CoursePreset("user:b1", "extra:battle_seam", "B1", listOf(FlowStep("Main", loopCount = 1, exitFrame = earlyExit), FlowStep("Fast", loopCount = 0))),
            seamIndex = 0,
            inventory = battleInv,
            minf = minf(),
            resolver = resolver,
        )!!
        val seamLate = CourseFlowRecipeBuilder.compileSeamPreview(
            preset = CoursePreset("user:b1", "extra:battle_seam", "B1", listOf(FlowStep("Main", loopCount = 1, exitFrame = lateExit), FlowStep("Fast", loopCount = 0))),
            seamIndex = 0,
            inventory = battleInv,
            minf = minf(),
            resolver = resolver,
        )!!
        val earlyMainClip = seamEarly.medley.clips.last { it.stem == mainRef.stem }
        val lateMainClip = seamLate.medley.clips.last { it.stem == mainRef.stem }
        assertEquals(earlyExit, earlyMainClip.sourceOffset + earlyMainClip.sourceLength)
        assertEquals(lateExit, lateMainClip.sourceOffset + lateMainClip.sourceLength)

        // 2. InteractiveTitle before Outro 01: editing Main exitFrame must not snap back to fixed mainExitFrame (720_000).
        val titleInv = CourseFlowRecipeBuilder.inspectExtra("extra:title_seam", catalog, ExtraMedleyCatalog.Catalog(), packDir)!!
        val titleSeamCustom = CourseFlowRecipeBuilder.compileSeamPreview(
            preset = CoursePreset(
                id = "user:t1",
                courseCode = "extra:title_seam",
                name = "T1",
                steps = listOf(FlowStep("Main", loopCount = 1, exitFrame = 384_000L)),
                outroNumber = 1,
            ),
            seamIndex = 0,
            inventory = titleInv,
            minf = minf(),
            resolver = resolver,
        )!!
        val titleMainClip = titleSeamCustom.medley.clips.last { it.stem == mainRef.stem }
        assertEquals(384_000L, titleMainClip.sourceOffset + titleMainClip.sourceLength)
    }

    @Test
    fun koopaTroopaBeachSpeakerToOutroWrapsWhenNeededAndFadesSpeakerCleanly() {
        val sr = 48_000
        val mainFrames = 2_206_447L
        val loopStart = 348_386L
        val loopEnd = 2_206_447L
        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                if (!stem.contains("KoopaTroopaBeach")) return null
                return when {
                    stem == "BGM_Stage_KoopaTroopaBeach_Pre" -> CourseFlowRecipeBuilder.AssetHeader(stem, "sha-$stem", 384_000L, sr, 0L, 384_000L)
                    stem == "BGM_Stage_KoopaTroopaBeach" -> CourseFlowRecipeBuilder.AssetHeader(stem, "sha-$stem", mainFrames, sr, loopStart, loopEnd)
                    stem == "SE_Stage_KoopaTroopaBeach_Speaker_Normal" -> CourseFlowRecipeBuilder.AssetHeader(stem, "sha-$stem", mainFrames, sr, loopStart, loopEnd)
                    stem.contains("_Fast") -> CourseFlowRecipeBuilder.AssetHeader(stem, "sha-$stem", 2_000_000L, sr, 0L, 2_000_000L)
                    stem.contains("_Outro") -> CourseFlowRecipeBuilder.AssetHeader(stem, "sha-$stem", 240_000L, sr, null, null)
                    else -> null
                }
            }
        }
        val minfCatalog = minf()
        val medleys = medleyCatalog()
        val inv = CourseFlowRecipeBuilder.inspectCourse("KoopaTroopaBeach", minfCatalog, medleys, resolver)

        val steps = listOf(
            FlowStep("Pre"),
            FlowStep("Main", loopCount = 0, exitFrame = 2_100_000L),
            FlowStep("Main_Speaker", loopCount = 0, exitFrame = 700_000L),
        )
        // Reachable triggers after wrap in loop: any pre-loop trigger (< loopStart 348386) must be filtered out when loop triggers exist
        val outro04 = inv.outros.first { it.number == 4 }
        val offsets = CourseFlowRecipeBuilder.stepSourceOffsets(steps, 4, inv)
        val outro04Triggers = CourseFlowRecipeBuilder.reachableOutroTriggers(
            step = steps[2],
            sourceOffset = offsets[2],
            part = inv.part("Main_Speaker"),
            outro = outro04,
        )
        assertTrue(outro04Triggers.isNotEmpty())
        if (outro04.mainTriggerPoints.any { it > loopStart }) {
            assertTrue(outro04Triggers.all { it > loopStart }, "Pre-loop trigger (< $loopStart) must be filtered: $outro04Triggers")
        }

        // Find an Outro trigger inside (loopStart, 2_100_000) so Main_Speaker (loopCount = 0, sourceOffset = 2_100_000)
        // must wrap once across loopEnd -> loopStart without fading primary stem across the overlay seam!
        val mainSpeakerPart = inv.part("Main_Speaker")!!
        val wrapOutro = inv.outros.first { outro ->
            CourseFlowRecipeBuilder.reachableOutroTriggers(steps[2], offsets[2], mainSpeakerPart, outro)
                .any { it < offsets[2] }
        }
        val wrapTriggers = CourseFlowRecipeBuilder.reachableOutroTriggers(steps[2], offsets[2], mainSpeakerPart, wrapOutro)
        val earlyTarget = wrapTriggers.first { it < offsets[2] }
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(
            preset = CoursePreset(
                id = "user:koopa-wrap",
                courseCode = "KoopaTroopaBeach",
                name = "Koopa Wrap",
                steps = listOf(
                    steps[0],
                    steps[1],
                    FlowStep("Main_Speaker", loopCount = 0, exitFrame = earlyTarget),
                ),
                outroNumber = wrapOutro.number,
            ),
            inventory = inv,
            minf = minfCatalog,
            resolver = resolver,
        )!!
        val clips = compiled.medley.clips
        // Primary stem is NOT double-faded at pure overlay switch or Outro entry
        val primaryClips = clips.filter { it.stem == "BGM_Stage_KoopaTroopaBeach" }
        assertTrue(primaryClips.all { it.fadeIn == 0L && it.fadeOut == 0L }, "Primary stem must stay clean without fadeIn/fadeOut: $primaryClips")
        // Secondary Speaker stem fades in on first clip at switch and fades out post-exit across Outro (~1s) on the wrapped clip
        val speakerClips = clips.filter { it.stem == "SE_Stage_KoopaTroopaBeach_Speaker_Normal" }
        assertEquals(2, speakerClips.size, "Expected 2 Speaker clips when wrapping loop: $speakerClips")
        assertEquals(sr.toLong(), speakerClips.first().fadeIn)
        assertEquals(0L, speakerClips.first().fadeOut)
        assertEquals(0L, speakerClips.last().fadeIn)
        assertEquals(sr.toLong(), speakerClips.last().fadeOut)
    }

    @Test
    fun snowMountainAndCinemaCrossPatternFastTransitionsAndSuiteFallbacksCompile() {
        val sr = 48_000
        fun patternResolver(course: String) = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                if (!stem.contains(course)) return null
                return CourseFlowRecipeBuilder.AssetHeader(
                    stem = stem,
                    digest = "sha-$stem",
                    frames = 2_600_000L,
                    sampleRate = sr,
                    loopStart = if (stem.contains("Outro") || stem.contains("Pre")) null else 200_000L,
                    loopEnd = if (stem.contains("Outro") || stem.contains("Pre")) null else 2_400_000L,
                )
            }
        }

        val minfCatalog = minf()
        val medleys = medleyCatalog()
        for (course in listOf("SnowMountain", "Cinema")) {
            val res = patternResolver(course)
            val inv = CourseFlowRecipeBuilder.inspectCourse(course, minfCatalog, medleys, res)
            val mainToFast2 = CourseFlowRecipeBuilder.compileUserPreset(
                preset = CoursePreset(
                    id = "user:$course-m2fp2",
                    courseCode = course,
                    name = "Main->FastPattern2",
                    steps = listOf(FlowStep("Pre"), FlowStep("Main"), FlowStep("Fast_Pattern2")),
                    outroNumber = 1,
                ),
                inventory = inv,
                minf = minfCatalog,
                resolver = res,
            )
            assertNotNull(mainToFast2, "$course Main -> Fast_Pattern2 must compile")

            val p2ToFast = CourseFlowRecipeBuilder.compileUserPreset(
                preset = CoursePreset(
                    id = "user:$course-p22f",
                    courseCode = course,
                    name = "Pattern2->Fast",
                    steps = listOf(FlowStep("Pre"), FlowStep("Main"), FlowStep("Pattern2"), FlowStep("Fast")),
                    outroNumber = 1,
                ),
                inventory = inv,
                minf = minfCatalog,
                resolver = res,
            )
            assertNotNull(p2ToFast, "$course Pattern2 -> Fast must compile")
        }

        // Verify resolvePresetRef picks Suite C for FirstCircuit & KoopaTroopaBeach, A Alt for DonkeyKong,
        // and falls back to Suite A when a course lacks the requested variant (e.g. Rainbow -> C, FirstCircuit -> A Alt)
        val anyResolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                return CourseFlowRecipeBuilder.AssetHeader(stem, "sha-$stem", 3_000_000L, sr, 0L, 2_500_000L)
            }
        }
        val fcC = CourseFlowRecipeBuilder.resolvePresetRef(
            courseCode = "FirstCircuit",
            presetRef = PresetRefs.SYSTEM_SUITE_C,
            userPresets = emptyList(),
            minf = minfCatalog,
            medleyCatalog = medleys,
            resolver = anyResolver,
        )!!
        assertEquals("c", fcC.medley.normalizedSuiteVariant)

        val koopaC = CourseFlowRecipeBuilder.resolvePresetRef(
            courseCode = "KoopaTroopaBeach",
            presetRef = PresetRefs.SYSTEM_SUITE_C,
            userPresets = emptyList(),
            minf = minfCatalog,
            medleyCatalog = medleys,
            resolver = anyResolver,
        )!!
        assertEquals("c", koopaC.medley.normalizedSuiteVariant)

        val rainbowFallback = CourseFlowRecipeBuilder.resolvePresetRef(
            courseCode = "Rainbow",
            presetRef = PresetRefs.SYSTEM_SUITE_C,
            userPresets = emptyList(),
            minf = minfCatalog,
            medleyCatalog = medleys,
            resolver = anyResolver,
        )!!
        assertEquals("a", rainbowFallback.medley.normalizedSuiteVariant)

        val dkAlt = CourseFlowRecipeBuilder.resolvePresetRef(
            courseCode = "DonkeyKong",
            presetRef = PresetRefs.SYSTEM_SUITE_A_ALT,
            userPresets = emptyList(),
            minf = minfCatalog,
            medleyCatalog = medleys,
            resolver = anyResolver,
        )!!
        assertEquals("a_alt", dkAlt.medley.normalizedSuiteVariant)

        val fcAltFallback = CourseFlowRecipeBuilder.resolvePresetRef(
            courseCode = "FirstCircuit",
            presetRef = PresetRefs.SYSTEM_SUITE_A_ALT,
            userPresets = emptyList(),
            minf = minfCatalog,
            medleyCatalog = medleys,
            resolver = anyResolver,
        )!!
        assertEquals("a", fcAltFallback.medley.normalizedSuiteVariant)
    }

    @Test
    fun acornHeightsBigTreeOutro03PreLoopTriggerCompilesCleanlyWithoutClamping() {
        val sr = 48_000
        val minfCatalog = minf()
        val medleys = medleyCatalog()
        val fullMinf = minfCatalog.part("BigTree", "Full")!!
        val preMinf = minfCatalog.part("BigTree", "Pre")!!
        val outro03Trigger = ConfirmedOutroCatalog.forStage("BigTree")!!.getValue(3).single()
        val outro00Trigger = ConfirmedOutroCatalog.forStage("BigTree")!!.getValue(0).single()
        assertEquals(417_857L, outro03Trigger)
        assertTrue(
            outro03Trigger < fullMinf.loopStart,
            "BigTree Outro03 trigger ($outro03Trigger) must be strictly before Main loopStart (${fullMinf.loopStart})",
        )
        assertTrue(outro00Trigger > fullMinf.loopStart)

        val resolver = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                if (!stem.contains("BigTree")) return null
                val rec = minfCatalog.records.firstOrNull {
                    it.stage == "BigTree" && it.logicalName.equals(stem, ignoreCase = true)
                }
                val isOutro = stem.contains("_Outro")
                val frames = rec?.loopEnd ?: 240_000L
                val hasLoop = !isOutro && rec != null && rec.loopStart < rec.loopEnd
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

        val inv = CourseFlowRecipeBuilder.inspectCourse("BigTree", minfCatalog, medleys, resolver)
        val prePart = inv.part("Pre")!!
        val mainPart = inv.part("Main")!!
        val outro03 = inv.outros.first { it.number == 3 }
        assertEquals(listOf(417_857L), outro03.mainTriggerPoints)
        assertEquals(237_858L, outro03.frames)
        assertEquals(173_571L, outro03.outroToNextPreFrame)
        assertTrue(outro03.supportsNativeHandover)

        // Default editor flow (Pre -> Main with loopCount = 1) entering pre-loop-only Outro03 must NOT clamp
        // targetExit up to loopStart + 1 (578,572) and must emit valid non-negative splice frames aligned to 417,857.
        val preset = CoursePreset(
            id = "user:bigtree-outro03",
            courseCode = "BigTree",
            name = "Acorn Heights Outro03",
            steps = listOf(
                FlowStep("Pre", loopCount = 0, exitFrame = prePart.defaultExitFrame),
                FlowStep("Main", loopCount = 1, exitFrame = mainPart.defaultExitFrame),
            ),
            outroNumber = 3,
            fadeOutMs = 0,
            outroToNextPre = true,
        )
        val compiled = CourseFlowRecipeBuilder.compileUserPreset(preset, inv, minfCatalog, resolver)
        assertNotNull(compiled, "BigTree Pre -> Main(loop=1) -> Outro03 must compile cleanly")
        val estimatedFrames = CourseFlowRecipeBuilder.estimateTimelineFrames(preset.steps, outroNumber = 3, fadeOutMs = 0, inventory = inv)
        assertEquals(estimatedFrames, compiled.medley.totalFrames, "estimateTimelineFrames must match compiled totalFrames")

        for (clip in compiled.medley.clips) {
            assertTrue(clip.start >= 0L, "negative clip.start in ${clip.stem}: ${clip.start}")
            assertTrue(clip.sourceOffset >= 0L, "negative clip.sourceOffset in ${clip.stem}: ${clip.sourceOffset}")
            assertTrue(clip.sourceLength > 0L, "non-positive clip.sourceLength in ${clip.stem}: ${clip.sourceLength}")
        }

        val preClip = compiled.medley.clips.single { it.stem == "BGM_Stage_BigTree_Pre" }
        val mainClips = compiled.medley.clips.filter { it.stem == "BGM_Stage_BigTree" }
        val outroClip = compiled.medley.clips.single { it.stem == "BGM_Stage_BigTree_Outro03" }
        assertEquals(0L, preClip.start)
        assertEquals(prePart.defaultExitFrame, preClip.sourceLength)
        assertEquals(1, mainClips.size, "Pre-loop-only Outro03 must emit a single Main clip without looping past trigger")
        assertEquals(preClip.sourceLength, mainClips.single().start)
        assertEquals(0L, mainClips.single().sourceOffset)
        assertEquals(outro03Trigger, mainClips.single().sourceLength, "Main must splice cleanly at Outro03 trigger 417,857")
        assertEquals(preClip.sourceLength + outro03Trigger, outroClip.start)
        assertEquals(0L, outroClip.sourceOffset)
        assertEquals(outro03.frames, outroClip.sourceLength)
        assertEquals(outroClip.start + 173_571L, compiled.outroHandoverFrame)

        // Graph compilation & probe must succeed cleanly.
        val bytesByStem = compiled.medley.uniqueStems.associateWith { stem ->
            val maxNeeded = compiled.medley.clips
                .filter { it.stem == stem }
                .maxOf { (it.sourceOffset + it.sourceLength).toInt() } + 1_000
            syntheticBwav(frames = maxNeeded, sampleRate = sr)
        }
        val graph = CourseMedleyCompiler.compileGraph(compiled.medley, compiled.sampleRate, bytesByStem)
        assertTrue(GraphProbe.inspect(graph.packet) is GraphProbe.Result.Ok, "GraphProbe must pass for BigTree Outro03")

        // Seam preview centered on Main -> Outro03 (seamIndex = 1) must align Main tail and Outro03 head cleanly.
        val seamPreview = CourseFlowRecipeBuilder.compileSeamPreview(
            preset = preset,
            seamIndex = 1,
            inventory = inv,
            minf = minfCatalog,
            resolver = resolver,
        )
        assertNotNull(seamPreview, "Main -> Outro03 seam preview must compile")
        for (clip in seamPreview.medley.clips) {
            assertTrue(clip.start >= 0L && clip.sourceOffset >= 0L && clip.sourceLength > 0L)
        }
        val seamMain = seamPreview.medley.clips.last { it.stem == "BGM_Stage_BigTree" }
        val seamOutro = seamPreview.medley.clips.single { it.stem == "BGM_Stage_BigTree_Outro03" }
        assertEquals(outro03Trigger, seamMain.sourceOffset + seamMain.sourceLength)
        assertEquals(seamMain.start + seamMain.sourceLength, seamOutro.start)

        // Contrast: an in-loop outro (Outro00 at 2,584,284 > loopStart) still honors loopCount = 1.
        val loopPreset = preset.copy(id = "user:bigtree-outro00", outroNumber = 0)
        val compiledLoop = CourseFlowRecipeBuilder.compileUserPreset(loopPreset, inv, minfCatalog, resolver)!!
        val loopMainClips = compiledLoop.medley.clips.filter { it.stem == "BGM_Stage_BigTree" }
        val loopOutroClip = compiledLoop.medley.clips.single { it.stem == "BGM_Stage_BigTree_Outro00" }
        assertEquals(2, loopMainClips.size, "In-loop Outro00 must preserve requested loopCount = 1")
        assertEquals(fullMinf.loopStart, loopMainClips[1].sourceOffset)
        assertEquals(outro00Trigger - fullMinf.loopStart, loopMainClips[1].sourceLength)
        assertEquals(loopMainClips[1].start + loopMainClips[1].sourceLength, loopOutroClip.start)
        assertEquals(preMinf.loopEnd, prePart.frames)
    }

    @Test
    fun snowMountainCinemaAndKoopaTroopaBeachFlowRulesAndLabelsMatchVerifiedV180Spec() {
        val sr = 48_000
        val minfCatalog = minf()
        val medleys = medleyCatalog()

        fun resolverFor(course: String) = object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                if (!stem.contains(course)) return null
                val rec = minfCatalog.records.firstOrNull { it.logicalName.equals(stem, ignoreCase = true) }
                val isOutroOrBridge = stem.contains("_Outro") || stem.contains("_Bridge")
                val frames = maxOf(rec?.loopEnd ?: 2_600_000L, if (isOutroOrBridge) 240_000L else 2_600_000L)
                val hasLoop = !isOutroOrBridge && rec != null && rec.loopStart < rec.loopEnd
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

        // 1. SnowMountain: Pattern2 restricts outros strictly to Outro00..05 (rejects Outro06/Outro07),
        //    and crossfades are asymmetric (0.5s enter Pattern2, 2.0s exit back to Main).
        val snowRes = resolverFor("SnowMountain")
        val snowInv = CourseFlowRecipeBuilder.inspectCourse("SnowMountain", minfCatalog, medleys, snowRes)
        val snowPattern2Steps = listOf(FlowStep("Main", exitFrame = 500_000L), FlowStep("Pattern2", exitFrame = 1_500_000L))
        assertEquals((0..7).toList(), snowInv.availableOutrosForTail(listOf(FlowStep("Main"))).map { it.number })
        assertEquals((0..5).toList(), snowInv.availableOutrosForTail(snowPattern2Steps).map { it.number })
        val illegalOutro6 = CourseFlowRecipeBuilder.compileUserPreset(
            preset = CoursePreset("user:snow-p2-o6", "SnowMountain", "Snow P2 O6", snowPattern2Steps, outroNumber = 6),
            inventory = snowInv,
            minf = minfCatalog,
            resolver = snowRes,
        )!!
        assertTrue(
            illegalOutro6.medley.clips.none { it.stem.contains("Outro06") },
            "SnowMountain Pattern2 must reject illegal Outro06 during compilation",
        )
        val legalOutro5 = CourseFlowRecipeBuilder.compileUserPreset(
            preset = CoursePreset("user:snow-p2-o5", "SnowMountain", "Snow P2 O5", snowPattern2Steps, outroNumber = 5),
            inventory = snowInv,
            minf = minfCatalog,
            resolver = snowRes,
        )!!
        assertTrue(legalOutro5.medley.clips.any { it.stem == "BGM_Stage_SnowMountain_Outro05" })
        val snowRoundTrip = CourseFlowRecipeBuilder.compileUserPreset(
            preset = CoursePreset(
                "user:snow-asym-check",
                "SnowMountain",
                "Snow Asym",
                listOf(FlowStep("Main", exitFrame = 500_000L), FlowStep("Pattern2", exitFrame = 1_000_000L), FlowStep("Main", exitFrame = 1_500_000L)),
            ),
            inventory = snowInv,
            minf = minfCatalog,
            resolver = snowRes,
        )!!
        assertEquals(24_000L, snowRoundTrip.medley.clips[0].fadeOut)
        assertEquals(24_000L, snowRoundTrip.medley.clips[1].fadeIn)
        assertEquals(96_000L, snowRoundTrip.medley.clips[1].fadeOut)
        assertEquals(96_000L, snowRoundTrip.medley.clips[2].fadeIn)

        // 2. Cinema: Pre (16) and Pre_Short (4) strictly use 8-beat Pre_TransitionGrid, and Pattern2 is bidirectional 0.5s.
        val cinRes = resolverFor("Cinema")
        val cinInv = CourseFlowRecipeBuilder.inspectCourse("Cinema", minfCatalog, medleys, cinRes)
        assertTrue(cinInv.part("Pre")!!.isGridConstrained)
        assertTrue(cinInv.part("Pre_Short")!!.isGridConstrained)
        assertEquals(16, cinInv.part("Pre")!!.exitCandidates.size)
        assertEquals(4, cinInv.part("Pre_Short")!!.exitCandidates.size)
        assertEquals((0..5).toList(), cinInv.availableOutrosForTail(listOf(FlowStep("Main"), FlowStep("Pattern2"))).map { it.number })
        val cinRoundTrip = CourseFlowRecipeBuilder.compileUserPreset(
            preset = CoursePreset(
                "user:cin-sym-check",
                "Cinema",
                "Cinema Sym",
                listOf(FlowStep("Main", exitFrame = 500_000L), FlowStep("Pattern2", exitFrame = 1_000_000L), FlowStep("Main", exitFrame = 1_500_000L)),
            ),
            inventory = cinInv,
            minf = minfCatalog,
            resolver = cinRes,
        )!!
        assertEquals(24_000L, cinRoundTrip.medley.clips[0].fadeOut)
        assertEquals(24_000L, cinRoundTrip.medley.clips[1].fadeIn)
        assertEquals(24_000L, cinRoundTrip.medley.clips[1].fadeOut)
        assertEquals(24_000L, cinRoundTrip.medley.clips[2].fadeIn)

        // 3. KoopaTroopaBeach: Speaker overlay event label explicitly includes 1.0s fade timing.
        val koopaInv = CourseFlowRecipeBuilder.inspectCourse("KoopaTroopaBeach", minfCatalog, medleys, resolverFor("KoopaTroopaBeach"))
        assertTrue(koopaInv.describeTransitionEvent("Main", "Main_Speaker").contains("1.0s"))
        assertTrue(koopaInv.describeTransitionEvent("Fast", "Fast_Speaker").contains("1.0s"))

        // 4. Course-aware sync beat-grid label resolution (3 / 4 / 8 / 16 beats).
        assertEquals(8, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("Cinema", "Pre", "pre.exit"))
        assertEquals(8, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("Cinema", "Pre_Short", "pre.exit"))
        assertEquals(8, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("Lava", "Pre", "pre.exit"))
        assertEquals(3, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("BigTree", "Pre", "pre.exit"))
        assertEquals(3, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("DinoBones", "Pre_Short", "pre.exit"))
        assertEquals(16, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("Rainbow", "Area1_Pre", "pre.exit"))
        assertEquals(4, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("Rainbow", "Area3_Pre", "pre.exit"))
        assertEquals(4, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("SnowMountain", "Pre", "pre.exit"))
        assertEquals(4, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("DonkeyKong", "Main", "region.exit"))
        assertEquals(8, com.mylittleblover.mkwplayer.ui.interactive.resolvedGridBeatsForSync("DonkeyKong", "Region 2", "region.exit"))
    }
}
