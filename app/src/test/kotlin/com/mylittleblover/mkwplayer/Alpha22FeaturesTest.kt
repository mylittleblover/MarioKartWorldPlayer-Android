package com.mylittleblover.mkwplayer

import androidx.compose.ui.geometry.Offset
import com.mylittleblover.mkwplayer.audio.BwavWaveformExtractor
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers
import com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import com.mylittleblover.mkwplayer.ui.routes.MapCamera
import com.mylittleblover.mkwplayer.ui.routes.MapTransform
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Alpha22FeaturesTest {

    private fun minf(): MinfCatalog =
        MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())

    private fun medleyCatalog(): CourseMedleyCatalog.Catalog =
        CourseMedleyCatalog.parse(File("src/main/assets/course_medley_catalog.json").readText())

    private fun mockResolver(sampleRate: Int = 48_000) = object : CourseFlowRecipeBuilder.SourceResolver {
        override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? = when {
            fileName.endsWith(".bwav") -> CourseFlowRecipeBuilder.AssetHeader(
                stem = fileName.removeSuffix(".bwav"),
                digest = "sha-${fileName.removeSuffix(".bwav")}",
                frames = 480_000L,
                sampleRate = sampleRate,
                loopStart = 48_000L,
                loopEnd = 432_000L,
            )
            else -> null
        }
    }

    @Test
    fun flowBuilderLocksBridgeExitAndLoopsAndEnforcesEventTransitions() {
        val inventory = CourseFlowRecipeBuilder.inspectCourse(
            courseCode = "FirstCircuit",
            minf = minf(),
            medleyCatalog = medleyCatalog(),
            resolver = mockResolver(),
        )
        val bridgePart = inventory.part("Bridge")
        assertNotNull(bridgePart)
        assertTrue(bridgePart.isBridgeLocked)
        assertFalse(bridgePart.supportsLoop)
        assertEquals(1, bridgePart.exitCandidates.size)

        // Event-driven next roles strictly follow the interactive graph (Pre cannot skip Bridge, Pre/Bridge can enter Fast)
        assertEquals(listOf("Bridge", "Fast"), inventory.allowedNextRoles(listOf(FlowStep("Pre"))))
        assertEquals(listOf("Main", "Fast"), inventory.allowedNextRoles(listOf(FlowStep("Pre"), FlowStep("Bridge"))))
        assertTrue(inventory.allowedNextRoles(listOf(FlowStep("Pre"), FlowStep("Main"), FlowStep("Fast"))).isEmpty())

        // Attempting to shorten or loop Bridge is ignored during compilation
        val maliciousPreset = CoursePreset(
            id = "user:bridge-lock",
            courseCode = "FirstCircuit",
            name = "Bridge Lock Check",
            steps = listOf(
                FlowStep("Pre", loopCount = 0, exitFrame = 96_000L),
                FlowStep("Bridge", loopCount = 4, exitFrame = 1_234L),
                FlowStep("Main", loopCount = 1, exitFrame = 240_000L),
            ),
            outroNumber = 1,
        )
        val resolved = CourseFlowRecipeBuilder.compileUserPreset(maliciousPreset, inventory, minf(), mockResolver())
        assertNotNull(resolved)
        val bridgeClips = resolved.medley.clips.filter { it.stem == bridgePart.stem }
        assertEquals(1, bridgeClips.size, "Locked Bridge must never loop")
        assertEquals(bridgePart.frames, bridgeClips.single().sourceLength, "Locked Bridge must keep full length")
    }

    @Test
    fun compileSeamPreviewAndExportConfigJsonWorkAcrossStepsAndOutro() {
        val minfCatalog = minf()
        val inventory = CourseFlowRecipeBuilder.inspectCourse(
            courseCode = "FirstCircuit",
            minf = minfCatalog,
            medleyCatalog = medleyCatalog(),
            resolver = mockResolver(),
        )
        val preset = CoursePreset(
            id = "user:export-check",
            courseCode = "FirstCircuit",
            name = "Export Check",
            steps = listOf(
                FlowStep("Pre"),
                FlowStep("Bridge"),
                FlowStep("Main", loopCount = 1),
            ),
            outroNumber = inventory.outros.firstOrNull()?.number,
        )
        val resolved = CourseFlowRecipeBuilder.compileUserPreset(preset, inventory, minfCatalog, mockResolver())
        assertNotNull(resolved)

        // Seam 0 (Pre -> Bridge), Seam 1 (Bridge -> Main), Seam 2 (Main -> Outro) should all compile
        for (seamIndex in 0 until preset.steps.size) {
            val seam = CourseFlowRecipeBuilder.compileSeamPreview(preset, seamIndex, inventory, minfCatalog, mockResolver())
            assertNotNull(seam, "Seam $seamIndex should compile")
            assertTrue(seam.medley.clips.isNotEmpty())
            assertTrue(seam.medley.totalFrames in 1L..(12L * 48_000L))
        }

        val jsonText = CourseFlowRecipeBuilder.exportPresetConfigJson(preset, resolved)
        val root = JSONObject(jsonText)
        assertEquals(1, root.getInt("schemaVersion"))
        assertEquals(48_000, root.getInt("sampleRate"))
        assertTrue(root.has("medley"))
        assertTrue(root.has("editorPreset"))

        val rewrapped = JSONObject()
            .put("schemaVersion", 1)
            .put("sampleRate", 48_000)
            .put("medleys", org.json.JSONArray().put(root.getJSONObject("medley")))
            .toString()
        val parsed = CourseMedleyCatalog.parse(rewrapped)
        assertEquals(1, parsed.medleys.size)
        assertEquals(resolved.medley.totalFrames, parsed.medleys.single().totalFrames)
    }

    @Test
    fun extraMedleyCatalogParsesScaffoldAndPopulatedMedleys() {
        val bundled = ExtraMedleyCatalog.parse(File("src/main/assets/extra_medley_catalog.json").readText())
        assertEquals(1, bundled.schemaVersion)
        assertEquals(48_000, bundled.sampleRate)
        assertEquals(14, bundled.entries.size)
        assertEquals(5, bundled.entries.count { it.targetType == ExtraMedleyCatalog.TargetType.TWO_STATE_COURSE })
        assertEquals(8, bundled.entries.count { it.targetType == ExtraMedleyCatalog.TargetType.BATTLE })
        val title = bundled.forTarget("title_theme").single()
        assertEquals(ExtraMedleyCatalog.TargetType.TITLE, title.targetType)
        assertEquals(86_666L, title.medley.durationMs)
        assertEquals(listOf("BGM_Seq_Title"), title.medley.uniqueStems)
        for (entry in bundled.entries.filter { it.targetType == ExtraMedleyCatalog.TargetType.TWO_STATE_COURSE }) {
            val ffClip = entry.medley.clips.firstOrNull { it.stem == "BGM_Seq_FinalLapFF" }
            assertNotNull(ffClip, "Missing BGM_Seq_FinalLapFF in ${entry.medley.id}")
            val fastClip = entry.medley.clips.first { it.stem.endsWith("_Fast") }
            assertEquals(
                FinalLapFanfareCompiler.FANFARE_EXIT_FRAMES,
                fastClip.start - ffClip.start,
                "Unexpected v1.8.0 FinalLapFF -> Fast spacing in ${entry.medley.id}",
            )
            assertEquals(480_000L, entry.medley.clips.last().fadeOut, "Expected 10s fadeOut in ${entry.medley.id}")
        }
        for (entry in bundled.entries.filter { it.targetType == ExtraMedleyCatalog.TargetType.BATTLE }) {
            assertTrue(
                entry.medley.clips.none { it.stem == "BGM_Seq_FinalLapFF" } &&
                    "BGM_Seq_FinalLapFF" !in entry.medley.uniqueStems,
                "Battle medley ${entry.medley.id} must not include BGM_Seq_FinalLapFF under v1.8.0",
            )
            assertEquals(480_000L, entry.medley.clips.last().fadeOut, "Expected 10s fadeOut in ${entry.medley.id}")
        }

        val emptyScaffold = ExtraMedleyCatalog.parse("""{"schemaVersion":1,"sampleRate":48000,"medleys":[]}""")
        assertTrue(emptyScaffold.entries.isEmpty())

        val populatedJson = """
            {
              "schemaVersion": 1,
              "sampleRate": 48000,
              "medleys": [
                {
                  "id": "extra_01_title",
                  "index": 1,
                  "targetType": "title",
                  "targetId": "title_main",
                  "courseCode": "Title",
                  "variant": "medley",
                  "totalFrames": 480000,
                  "durationMs": 10000,
                  "names": { "en": "Title Full Medley", "zh-Hans": "标题界面完整组曲" },
                  "clips": [
                    { "stem": "BGM_Title", "start": 0, "sourceOffset": 0, "sourceLength": 480000 }
                  ]
                }
              ]
            }
        """.trimIndent()
        val populated = ExtraMedleyCatalog.parse(populatedJson)
        assertEquals(1, populated.entries.size)
        val entry = populated.forTarget("title_main").single()
        assertEquals(ExtraMedleyCatalog.TargetType.TITLE, entry.targetType)
        assertEquals("标题界面完整组曲", entry.localizedLabel("zh-CN", "Fallback"))
        assertEquals("Title Full Medley", entry.localizedLabel("fr", "Fallback"))
    }

    @Test
    fun bwavWaveformExtractorDecodesPcm16AndDspAdpcmAndHandlesMissingFiles() {
        val dir = createTempDirectory("bwav_waveform_test").toFile()
        try {
            // Synthetic PCM16 mono BWAV (96 frames, data starts at offset 128)
            val frames = 96
            val dataOffset = 128
            val pcmFile = File(dir, "pcm.bwav")
            val pcmBytes = ByteArray(dataOffset + frames * 2)
            val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            pcmBytes[0] = 'B'.code.toByte()
            pcmBytes[1] = 'W'.code.toByte()
            pcmBytes[2] = 'A'.code.toByte()
            pcmBytes[3] = 'V'.code.toByte()
            buf.putShort(4, 0xFEFF.toShort())
            buf.putShort(14, 1.toShort())
            buf.putShort(16 + 0, 0.toShort()) // PCM16
            buf.putInt(16 + 4, 48_000)
            buf.putInt(16 + 12, frames)
            buf.putInt(16 + 52, dataOffset)
            for (i in 0 until frames) {
                val sample = if (i % 2 == 0) 16384 else -16384
                buf.putShort(dataOffset + i * 2, sample.toShort())
            }
            pcmFile.writeBytes(pcmBytes)

            val pcmPeaks = BwavWaveformExtractor.extract(pcmFile, "digest-pcm", bucketCount = 16)
            assertNotNull(pcmPeaks)
            assertEquals(16, pcmPeaks.bucketCount)
            assertTrue(pcmPeaks.maxPeaks.maxOrNull()!! >= 0.45f)
            assertTrue(pcmPeaks.minPeaks.minOrNull()!! <= -0.45f)

            // Synthetic DSP-ADPCM mono BWAV (28 frames = 2 packets of 8 bytes)
            val adpcmFile = File(dir, "adpcm.bwav")
            val adpcmFrames = 28
            val adpcmBytes = ByteArray(dataOffset + 16)
            val abuf = ByteBuffer.wrap(adpcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            adpcmBytes[0] = 'B'.code.toByte()
            adpcmBytes[1] = 'W'.code.toByte()
            adpcmBytes[2] = 'A'.code.toByte()
            adpcmBytes[3] = 'V'.code.toByte()
            abuf.putShort(4, 0xFEFF.toShort())
            abuf.putShort(14, 1.toShort())
            abuf.putShort(16 + 0, 1.toShort()) // DSP-ADPCM
            abuf.putInt(16 + 4, 48_000)
            abuf.putInt(16 + 12, adpcmFrames)
            abuf.putInt(16 + 52, dataOffset)
            // Identity coef pair [2048, 0] in coef slot 0
            abuf.putShort(16 + 20, 2048.toShort())
            adpcmBytes[dataOffset] = 0x04 // pred=0, scale=4 (shift=16)
            for (b in 1..7) adpcmBytes[dataOffset + b] = 0x5A
            adpcmFile.writeBytes(adpcmBytes)

            val adpcmPeaks = BwavWaveformExtractor.extract(adpcmFile, "digest-adpcm", bucketCount = 8)
            assertNotNull(adpcmPeaks)
            assertEquals(8, adpcmPeaks.bucketCount)
            assertTrue(adpcmPeaks.maxPeaks.maxOf { abs(it) } > 0f)

            assertNull(BwavWaveformExtractor.extract(File(dir, "missing.bwav"), "none"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun interactiveTwoStateCompilesGracefullyWhenFinalLapFanfareIsAbsent() {
        assertEquals(64, FinalLapFanfareCompiler.SHA256.length)
        fun headerBytes(frames: Int = 480_000): ByteArray {
            val headerSize = 0x10 + 2 * 0x4C
            val buf = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
            buf.put(byteArrayOf('B'.code.toByte(), 'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte()))
            buf.putShort(0xFEFF.toShort())
            buf.putShort(0x0100.toShort())
            buf.putInt(0)
            buf.putShort(0)
            buf.putShort(2.toShort())
            for (ch in 0 until 2) {
                val base = 0x10 + ch * 0x4C
                buf.putShort(base + 0x00, 1)
                buf.putShort(base + 0x02, ch.toShort())
                buf.putInt(base + 0x04, 48_000)
                buf.putInt(base + 0x08, frames)
                buf.putInt(base + 0x0C, frames)
                buf.putInt(base + 0x3C, -1)
            }
            return buf.array()
        }
        val track = ExtraMusicCatalog.TwoStateTrack(
            id = "test_sfc",
            ostIndex = 1,
            artwork = "test.webp",
            names = mapOf("en" to "Test SFC"),
            mainStem = "BGM_Test_Main",
            fastStem = "BGM_Test_Fast",
            searchAliases = emptyList(),
            main = ExtraMusicCatalog.AssetRef("BGM_Test_Main", "a".repeat(64), 480_000L, 48_000, 2, true, 0L, 480_000L),
            fast = ExtraMusicCatalog.AssetRef("BGM_Test_Fast", "b".repeat(64), 360_000L, 48_000, 2, true, 0L, 360_000L),
            durationMs = 10_000L,
        )
        val plan = ExtraInteractiveCompilers.compileTwoStateLoopPlan(
            track = track,
            isBattle = false,
            bytesByStem = mapOf(
                track.main.stem to headerBytes(480_000),
                track.fast.stem to headerBytes(360_000),
            ),
        )
        assertTrue(plan.packet.isNotEmpty())
        assertEquals(2, plan.assetPaths.size)
        assertFalse(FinalLapFanfareCompiler.ASSET in plan.assetPaths)
    }

    @Test
    fun mapTransformFitToRespectsMaxZoomAndScaleMultiplierForOfficialRoutes() {
        val transform = MapTransform.create(1080f, 1920f, 2500f / 2400f, MapCamera(1f), 24f)
        val tightCluster = listOf(
            Offset(0.45f, 0.45f),
            Offset(0.50f, 0.50f),
        )
        val defaultFit = transform.fitTo(tightCluster, padding = 48f)
        val restrainedOfficialFit = transform.fitTo(
            tightCluster,
            padding = 72f,
            maxZoom = 2.55f,
            scaleMultiplier = 0.78f,
        )
        assertTrue(defaultFit.zoom > restrainedOfficialFit.zoom)
        assertTrue(restrainedOfficialFit.zoom <= 2.55f)
    }
}
