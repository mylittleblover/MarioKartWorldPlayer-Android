package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.player.LinearQueue
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

class CourseMedleyCompilerTest {
    private fun catalog(): CourseMedleyCatalog.Catalog =
        CourseMedleyCatalog.parse(File("src/main/assets/course_medley_catalog.json").readText())

    private fun identities(): InteractiveAudioIdentities =
        InteractiveAudioIdentities.parse(File("src/main/assets/interactive_audio_identities.json").readText())

    private fun extraMusic(): ExtraMusicCatalog.Catalog =
        ExtraMusicCatalog.parse(File("src/main/assets/extra_music_catalog.json").readText())

    private fun syntheticBwav(frames: Int, sampleRate: Int = 48_000, channels: Int = 2): ByteArray {
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
            buf.putInt(base + 60, -1) // no loop (NO_LOOP sentinel at loopLast)
            buf.putInt(base + 64, 0)
        }
        return bytes
    }

    @Test
    fun catalogCoversAll120MedleysAnd14ExtraMedleysAndEveryStemResolvesInIdentitiesOrAssets() {
        val cat = catalog()
        val extra = ExtraMedleyCatalog.parse(File("src/main/assets/extra_medley_catalog.json").readText())
        val ids = identities()
        val extraAudio = extraMusic()
        val aAltCourses = setOf("DonkeyKong", "SnowMountain", "Cinema", "KoopaTroopaBeach")
        val courseCodes = cat.medleys.map { it.courseCode }.distinct()
        assertEquals(30, courseCodes.size)
        assertEquals(121, cat.medleys.size)
        assertEquals(14, extra.entries.size)
        for (code in courseCodes) {
            val expected = when (code) {
                "Rainbow" -> listOf("a")
                in aAltCourses -> listOf("a", "a_alt", "b", "c", "d")
                else -> listOf("a", "b", "c", "d")
            }
            assertEquals(expected, cat.forCourse(code).map { it.normalizedSuiteVariant }, "variants mismatch for $code")
        }

        for (medley in cat.medleys + extra.entries.map { it.medley }) {
            assertTrue(medley.clips.isNotEmpty(), "medley ${medley.id} has no clips")
            assertTrue(medley.totalFrames > 0L, "medley ${medley.id} totalFrames <= 0")
            assertTrue(medley.durationMs > 0L, "medley ${medley.id} durationMs <= 0")
            for (stem in medley.uniqueStems) {
                if (stem == "BGM_Seq_FinalLapFF") {
                    assertTrue(
                        Regex("^[0-9a-f]{64}$").matches(FinalLapFanfareCompiler.SHA256),
                        "FinalLapFanfare must have a pinned SHA-256 identity",
                    )
                } else {
                    val digest = ids.digestForFileName("$stem.bwav") ?: extraAudio.digestForStem(stem)
                    assertNotNull(digest, "stem '$stem.bwav' in medley '${medley.id}' missing from identities and extra_music_catalog")
                }
            }
        }
    }

    @Test
    fun compileGraphProducesValidProbeablePacketsForAll120CourseAnd14ExtraMedleys() {
        val cat = catalog()
        val extra = ExtraMedleyCatalog.parse(File("src/main/assets/extra_medley_catalog.json").readText())
        for (medley in cat.medleys + extra.entries.map { it.medley }) {
            val maxRequiredFrames = medley.clips.maxOf { (it.sourceOffset + it.sourceLength).toInt() } + 1000
            val bytesByStem = medley.uniqueStems.associateWith { syntheticBwav(frames = maxRequiredFrames) }
            val compiled = CourseMedleyCompiler.compileGraph(medley, cat.sampleRate, bytesByStem)
            assertEquals(medley.uniqueStems.size, compiled.assets.size)
            assertTrue(compiled.totalFrames >= medley.totalFrames)
            val probed = GraphProbe.inspect(compiled.packet)
            assertTrue(probed is GraphProbe.Result.Ok, "GraphProbe failed for ${medley.id}: $probed")
        }
    }

    @Test
    fun resolveMedleyRequiresAllStemFilesOnDisk() {
        val cat = catalog()
        val ids = identities()
        val first = cat.medleys.first()
        val tmpDir = kotlin.io.path.createTempDirectory("medley-test-").toFile()
        try {
            val audioDir = File(tmpDir, "audio").apply { mkdirs() }
            assertNull(CourseMedleyCompiler.resolveMedley(first, cat.sampleRate, ids, tmpDir))
            for (stem in first.uniqueStems) {
                val sha = ids.digestForFileName("$stem.bwav")!!
                File(audioDir, sha).writeBytes(byteArrayOf(1, 2, 3))
            }
            val resolved = CourseMedleyCompiler.resolveMedley(first, cat.sampleRate, ids, tmpDir)
            assertNotNull(resolved)
            assertEquals(first.uniqueStems.toSet(), resolved.stemDigests.keys)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun linearQueueSupportsNextAndPreviousAcrossMedleys() {
        val cat = catalog()
        val ids = identities()
        val tmpDir = kotlin.io.path.createTempDirectory("medley-queue-test-").toFile()
        try {
            val audioDir = File(tmpDir, "audio").apply { mkdirs() }
            val entries = cat.medleys.take(3).map { medley ->
                for (stem in medley.uniqueStems) {
                    if (stem != "BGM_Seq_FinalLapFF") {
                        val sha = ids.digestForFileName("$stem.bwav")!!
                        val file = File(audioDir, sha)
                        if (!file.exists()) file.writeBytes(byteArrayOf(1))
                    }
                }
                val resolved = CourseMedleyCompiler.resolveMedley(medley, cat.sampleRate, ids, tmpDir)!!
                LinearQueue.Entry(
                    key = "medley:${medley.id}",
                    title = medley.courseCode,
                    subtitle = "Complete suite",
                    durationMs = medley.durationMs,
                    courseMedley = resolved,
                )
            }
            val queue = LinearQueue()
            val start = queue.startSession(entries, startIndex = 0)
            assertEquals("medley:${cat.medleys[0].id}", start?.key)
            val next = queue.next() as LinearQueue.StepResult.Play
            assertEquals("medley:${cat.medleys[1].id}", next.entry.key)
            val prev = queue.previous(restartThresholdMs = 3000L, elapsedMs = 500L) as LinearQueue.StepResult.Play
            assertEquals("medley:${cat.medleys[0].id}", prev.entry.key)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun strictTransitionRulesAllowOnlyValidFadesVolumeEnvelopesAndPreservedCourses() {
        val cat = catalog()
        assertEquals(121, cat.medleys.size)
        for (medley in cat.medleys) {
            assertEquals(
                medley.clips.maxOf { it.start + it.sourceLength },
                medley.totalFrames,
                "totalFrames mismatch in ${medley.id}",
            )
            for (clip in medley.clips) {
                assertTrue(clip.start >= 0L, "negative start in ${medley.id}: ${clip.stem}")
                assertTrue(clip.sourceOffset >= 0L, "negative sourceOffset in ${medley.id}: ${clip.stem}")
                assertTrue(clip.sourceLength > 0L, "non-positive sourceLength in ${medley.id}: ${clip.stem}")
                assertTrue(clip.fadeIn in 0L..480_000L, "fadeIn out of bounds in ${medley.id}: ${clip.fadeIn}")
                assertTrue(clip.fadeOut in 0L..480_000L, "fadeOut out of bounds in ${medley.id}: ${clip.fadeOut}")
                for (pt in clip.volumePoints) {
                    assertTrue(pt.frame in 0L..clip.sourceLength, "volumePoint frame out of bounds in ${medley.id}: ${pt.frame}")
                    assertTrue(pt.value in -96.0..0.0, "volumePoint dB out of bounds in ${medley.id}: ${pt.value}")
                }
            }
        }
        val cinemaClips = cat.forCourse("Cinema").single { it.normalizedSuiteVariant == "a" }.clips
        assertEquals(24_000L, cinemaClips.last { it.stem == "BGM_Stage_Cinema" }.fadeOut)
        assertEquals(24_000L, cinemaClips.first { it.stem == "BGM_Stage_Cinema_Pattern2" }.fadeIn)

        val rainbowClips = cat.forCourse("Rainbow").single { it.normalizedSuiteVariant == "a" }.clips
        assertEquals(14_400L, rainbowClips.single { it.stem == "BGM_Rainbow_Area1_Inst" }.fadeOut)
        assertEquals(14_400L, rainbowClips.single { it.stem == "BGM_Rainbow_Area1_Melody" }.fadeOut)
        assertEquals(144_000L, rainbowClips.single { it.stem == "BGM_Rainbow_Area3" }.fadeOut)
        assertEquals(120_000L, rainbowClips.single { it.stem == "BGM_Rainbow_Final_Pre" }.fadeIn)
        assertEquals(4_800L, rainbowClips.single { it.stem == "BGM_Rainbow_Final_Pre" }.fadeOut)
        val rbFf = rainbowClips.single { it.stem == "BGM_Rainbow_FinalLapFanfare" }
        val rbFinal = rainbowClips.first { it.stem == "BGM_Rainbow_Final" }
        assertEquals(RainbowInteractiveCompiler.FINAL_FROM_FANFARE_FRAMES, rbFinal.start - rbFf.start)

        val cinemaAAlt = cat.forCourse("Cinema").single { it.normalizedSuiteVariant == "a_alt" }
        assertEquals(1_815_426L, cinemaAAlt.clips.first().sourceLength, "Cinema Suite A Alt Pre must exit on verified 8-beat grid")
        assertEquals(7_794_855L, cinemaAAlt.totalFrames, "Cinema Suite A Alt totalFrames must align with Cinema Suite A")

        val koopaA = cat.forCourse("KoopaTroopaBeach").single { it.normalizedSuiteVariant == "a" }
        val koopaAAlt = cat.forCourse("KoopaTroopaBeach").single { it.normalizedSuiteVariant == "a_alt" }
        val koopaB = cat.forCourse("KoopaTroopaBeach").single { it.normalizedSuiteVariant == "b" }
        val koopaSpeakers = koopaA.clips.filter { it.stem == "SE_Stage_KoopaTroopaBeach_Speaker_Normal" }
        assertEquals(2, koopaSpeakers.size)
        assertEquals(48_000L, koopaSpeakers.first().fadeIn)
        assertEquals(48_000L, koopaSpeakers.last().fadeOut)
        assertEquals(
            koopaAAlt.clips.drop(1).map { it.copy(start = it.start - 1L) },
            koopaB.clips.drop(2),
            "KoopaTroopaBeach Suite B after Pre_Short must match non-Speaker Suite A Alt after Pre",
        )

        val dkJpA = cat.forCourse("DKJungleParkway").single { it.normalizedSuiteVariant == "a" }
        val dkJpB = cat.forCourse("DKJungleParkway").single { it.normalizedSuiteVariant == "b" }
        val preShortClips = dkJpB.clips.take(2)
        assertTrue(preShortClips.all { it.stem == "BGM_Stage_DKJungleParkway_Pre_Short" })
        assertEquals(0L to 738_461L, preShortClips[0].sourceOffset to preShortClips[0].sourceLength)
        assertEquals(147_692L to 516_922L, preShortClips[1].sourceOffset to preShortClips[1].sourceLength)
        assertEquals(738_461L, preShortClips[1].start)
        val dkJpShift = dkJpA.clips.drop(2).first().start - dkJpB.clips.drop(2).first().start
        assertEquals(
            dkJpA.clips.drop(2).map { it.copy(start = it.start - dkJpShift) },
            dkJpB.clips.drop(2),
            "DKJungleParkway Suite B after Pre_Short must match Suite A after Pre",
        )
    }

    @Test
    fun suiteAMedleysStartFromZeroWithoutPreShortAndPreserveChocoMountainPhase() {
        val cat = catalog()
        val minf = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        for (medley in cat.medleys.filter { it.normalizedSuiteVariant == "a" }) {
            assertTrue(
                medley.clips.none { it.stem.contains("Pre_Short") },
                "Suite A medley ${medley.id} must not contain Pre_Short",
            )
            val firstClip = medley.clips.first()
            assertEquals(0L, firstClip.start, "First clip of ${medley.id} must start at frame 0")
            assertEquals(0L, firstClip.sourceOffset, "Expected sourceOffset 0 for ${medley.id}")
            if (medley.courseCode == "ChocoMountain") {
                assertEquals(191_400L, CourseFlowRecipeBuilder.resolveIncomingPhase(medley, minf))
            }
        }
    }

    @Test
    fun mergedCatalogIncludesAll1412And1147VariantsAndDonkeyKongAAlt() {
        val cat = catalog()
        assertTrue(cat.medleys.none { it.isKnockoutVariant })
        assertEquals(listOf("a"), cat.forCourse("Rainbow").map { it.normalizedSuiteVariant })
        for (altCourse in listOf("DonkeyKong", "SnowMountain", "Cinema", "KoopaTroopaBeach")) {
            assertEquals(listOf("a", "a_alt", "b", "c", "d"), cat.forCourse(altCourse).map { it.normalizedSuiteVariant })
        }

        val dkA = cat.forCourse("DonkeyKong").single { it.normalizedSuiteVariant == "a" }
        assertEquals("13_DonkeyKong_a", dkA.id)
        assertEquals(10_235_514L, dkA.totalFrames)
        assertEquals(
            listOf(
                "BGM_Stage_DonkeyKong_Pre",
                "BGM_Stage_DonkeyKong",
                "BGM_Stage_DonkeyKong_Pattern2",
                "BGM_Stage_DonkeyKong_Pattern3",
                "BGM_Stage_DonkeyKong_Pattern4",
                "BGM_Stage_DonkeyKong_Pattern5",
                "BGM_Seq_FinalLapFF",
                "BGM_Stage_DonkeyKong_Fast",
                "BGM_Stage_DonkeyKong_Fast",
                "BGM_Stage_DonkeyKong_Fast",
            ),
            dkA.clips.map { it.stem },
            "DK A must be the 10-clip multi-stage climb (Pre -> Main -> Pattern2..5 -> FinalLapFF -> Fast)",
        )

        val dkAAlt = cat.forCourse("DonkeyKong").single { it.normalizedSuiteVariant == "a_alt" }
        assertEquals("14_DonkeyKong_a_alt", dkAAlt.id)
        assertEquals(8_151_710L, dkAAlt.totalFrames)
        assertEquals(
            listOf(
                "BGM_Stage_DonkeyKong_Pre",
                "BGM_Stage_DonkeyKong",
                "BGM_Stage_DonkeyKong",
                "BGM_Stage_DonkeyKong_Outro00",
            ),
            dkAAlt.clips.map { it.stem },
            "DK A Alt must be the 4-clip 1-loop Main + Outro00 rally arrangement",
        )

        val dkB = cat.forCourse("DonkeyKong").single { it.normalizedSuiteVariant == "b" }
        assertEquals("15_DonkeyKong_b", dkB.id)
        assertEquals(7_092_401L, dkB.totalFrames)
        assertEquals(
            listOf(
                "BGM_Stage_DonkeyKong_Pre_Short",
                "BGM_Stage_DonkeyKong_Pre_Short",
                "BGM_Stage_DonkeyKong",
                "BGM_Stage_DonkeyKong",
                "BGM_Stage_DonkeyKong_Outro00",
            ),
            dkB.clips.map { it.stem },
            "DK B must be the restored 5-clip Pre_Short arrangement",
        )

        val dkC = cat.forCourse("DonkeyKong").single { it.normalizedSuiteVariant == "c" }
        assertEquals("16_DonkeyKong_c", dkC.id)
        assertEquals("BGM_Stage_DonkeyKong", dkC.clips.first().stem, "DK C from 1412 backup should start directly at Main")
        assertTrue(dkC.clips.last().volumePoints.isNotEmpty(), "DK C from 1412 backup should end with 10s fade-out envelope")

        // Verify KoopaTroopaBeach A, B, A Alt match corrected full-length Speaker / non-Speaker arrangements
        val koopaA = cat.forCourse("KoopaTroopaBeach").single { it.normalizedSuiteVariant == "a" }
        val koopaAAlt = cat.forCourse("KoopaTroopaBeach").single { it.normalizedSuiteVariant == "a_alt" }
        val koopaB = cat.forCourse("KoopaTroopaBeach").single { it.normalizedSuiteVariant == "b" }
        assertEquals("51_KoopaTroopaBeach_a", koopaA.id)
        assertEquals("52_KoopaTroopaBeach_a_alt", koopaAAlt.id)
        assertEquals("53_KoopaTroopaBeach_b", koopaB.id)
        assertEquals(5_436_595L, koopaA.totalFrames)
        assertEquals(5_436_595L, koopaAAlt.totalFrames)
        assertEquals(5_436_594L, koopaB.totalFrames)
        assertEquals(8, koopaA.clips.size)
        assertEquals(5, koopaAAlt.clips.size)
        assertEquals(6, koopaB.clips.size)
        assertEquals("BGM_Stage_KoopaTroopaBeach_Pre", koopaA.clips.first().stem)
        assertEquals("BGM_Stage_KoopaTroopaBeach_Pre", koopaAAlt.clips.first().stem)
        assertEquals("BGM_Stage_KoopaTroopaBeach_Pre_Short", koopaB.clips.first().stem)
        assertEquals("BGM_Stage_KoopaTroopaBeach_Outro00", koopaA.clips.last().stem)
        assertEquals("BGM_Stage_KoopaTroopaBeach_Outro00", koopaAAlt.clips.last().stem)
        assertEquals("BGM_Stage_KoopaTroopaBeach_Outro00", koopaB.clips.last().stem)
        assertTrue(koopaA.uniqueStems.contains("SE_Stage_KoopaTroopaBeach_Speaker_Normal"))
        assertFalse(koopaB.uniqueStems.contains("SE_Stage_KoopaTroopaBeach_Speaker_Normal"))
        assertFalse(koopaAAlt.uniqueStems.contains("SE_Stage_KoopaTroopaBeach_Speaker_Normal"))
        val ids = identities()
        val extraAudio = extraMusic()
        val tmpDir = kotlin.io.path.createTempDirectory("koopa-resolve-test-").toFile()
        try {
            val audioDir = File(tmpDir, "audio").apply { mkdirs() }
            for (stem in koopaA.uniqueStems) {
                val sha = ids.digestForFileName("$stem.bwav") ?: extraAudio.digestForStem(stem)!!
                File(audioDir, sha).writeBytes(byteArrayOf(1, 2, 3))
            }
            assertNull(CourseMedleyCompiler.resolveMedley(koopaA, cat.sampleRate, ids, tmpDir))
            val resolved = CourseMedleyCompiler.resolveMedley(koopaA, cat.sampleRate, ids, tmpDir, extraAudio)
            assertNotNull(resolved)
            assertEquals(koopaA.uniqueStems.toSet(), resolved.stemDigests.keys)
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}

