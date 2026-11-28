package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
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
import org.junit.Assume.assumeTrue

class TwoStateFinalLapFanfareTest {

    private fun bwavHeaderOnly(sampleRate: Int = 48000, channels: Int = 2, frames: Long = 48000L): ByteArray {
        val headerSize = 0x10 + channels * 0x4C
        val buf = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(byteArrayOf('B'.code.toByte(), 'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte()))
        buf.putShort(0xFEFF.toShort())
        buf.putShort(0x0100.toShort())
        buf.putInt(0)
        buf.putShort(0)
        buf.putShort(channels.toShort())
        for (ch in 0 until channels) {
            val base = 0x10 + ch * 0x4C
            buf.putShort(base + 0x00, 1) // PCM16
            buf.putShort(base + 0x02, if (channels > 1) ch.toShort() else 2.toShort())
            buf.putInt(base + 0x04, sampleRate)
            buf.putInt(base + 0x08, frames.toInt())
            buf.putInt(base + 0x0C, frames.toInt())
            buf.putInt(base + 0x3C, -1) // no loop
        }
        return buf.array()
    }

    private fun twoStateFixture(id: String, mainStem: String, fastStem: String) = ExtraMusicCatalog.TwoStateTrack(
        id = id,
        ostIndex = 1,
        artwork = "test.webp",
        names = mapOf("en" to id),
        mainStem = mainStem,
        fastStem = fastStem,
        searchAliases = emptyList(),
        main = ExtraMusicCatalog.AssetRef(mainStem, "a".repeat(64), 480_000L, 48000, 2, true, 0L, 480_000L),
        fast = ExtraMusicCatalog.AssetRef(fastStem, "b".repeat(64), 360_000L, 48000, 2, true, 0L, 360_000L),
        durationMs = 10_000L,
    )

    private data class TwoStateCase(
        val isBattle: Boolean,
        val id: String,
        val mainStem: String,
        val fastStem: String,
        val fastLabel: String,
    )

    @Test
    fun battleSwitchesDirectlyWithoutFinalLapFanfare() {
        val track = twoStateFixture("battle_smk", "BGM_Battle_01", "BGM_Battle_01_FL")
        val bytes = mapOf(
            "BGM_Battle_01" to bwavHeaderOnly(frames = 480_000L),
            "BGM_Battle_01_FL" to bwavHeaderOnly(frames = 360_000L),
        )
        val plan = ExtraInteractiveCompilers.compileTwoStateLoopPlan(
            track = track,
            isBattle = true,
            bytesByStem = bytes,
        )
        assertNull(plan.labels.entries.singleOrNull { it.value == "FinalLapFF" }, "Battle must not attach FinalLapFF")
        assertEquals("BattleFast", plan.labels[ExtraInteractiveCompilers.TWO_STATE_SEG_FAST])
        val rule = plan.spec.transitions.single {
            it.sources == listOf(ExtraInteractiveCompilers.TWO_STATE_SEG_MAIN) &&
                it.destinations == listOf(ExtraInteractiveCompilers.TWO_STATE_SEG_FAST)
        }
        assertEquals(-1, rule.bridge)
        assertEquals(G.SyncPoint.Immediate, rule.sync)
        assertTrue(rule.postExit)
        assertEquals(G.Fade(beats = FinalLapFanfareCompiler.FULL_FADE_OUT_SEC * 140.0 / 60.0), rule.fadeOut)

        val perfectPlan = ExtraInteractiveCompilers.compileTwoStateLoopPlan(
            track = track,
            isBattle = true,
            bytesByStem = bytes,
            perfectBeatEnabled = true,
        )
        val perfectRule = perfectPlan.spec.transitions.single()
        assertEquals(G.SyncPoint.NextBar, perfectRule.sync)
        assertEquals(-1, perfectRule.bridge)
    }

    @Test
    fun sfcPlanRoutesMainToFastThroughSharedFinalLapFanfare() {
        val ffFile = File("src/main/assets/audio/BGM_Seq_FinalLapFF.bwav")
        assumeTrue(ffFile.isFile)
        val ffBytes = ffFile.readBytes()

        val track = twoStateFixture("sfc_choco_island", "BGM_Stage_ChocoMountain_SFC", "BGM_Stage_ChocoMountain_SFC_Fast")
        val bytes = mapOf(
            "BGM_Stage_ChocoMountain_SFC" to bwavHeaderOnly(frames = 480_000L),
            "BGM_Stage_ChocoMountain_SFC_Fast" to bwavHeaderOnly(frames = 360_000L),
            FinalLapFanfareCompiler.ASSET to ffBytes,
        )
        val plan = ExtraInteractiveCompilers.compileTwoStateLoopPlan(
            track = track,
            isBattle = false,
            bytesByStem = bytes,
        )

        val ff = plan.labels.entries.singleOrNull { it.value == "FinalLapFF" }?.key
        assertNotNull(ff, "SFC track must attach FinalLapFF bridge")
        assertFalse(ff in plan.initialSegments, "FinalLapFF must not appear in starting stage selector")
        assertEquals("Fast", plan.labels[ExtraInteractiveCompilers.TWO_STATE_SEG_FAST])

        val ffSegment = plan.spec.segments.single { it.index == ff }
        assertEquals(FinalLapFanfareCompiler.FANFARE_EXIT_FRAMES, ffSegment.exit)
        assertNull(ffSegment.loop)

        val ffClip = plan.spec.tracks.single { it.segment == ff }.clips.single()
        assertEquals(268_759L, ffClip.sourceLength)
        assertEquals(FinalLapFanfareCompiler.ASSET, plan.spec.assets[ffClip.asset].path)

        val rule = plan.spec.transitions.single {
            it.sources == listOf(ExtraInteractiveCompilers.TWO_STATE_SEG_MAIN) &&
                it.destinations == listOf(ExtraInteractiveCompilers.TWO_STATE_SEG_FAST)
        }
        assertEquals(ff, rule.bridge)
        assertEquals(G.SyncPoint.Immediate, rule.sync)
        assertNull(rule.sourceCue)
        assertTrue(rule.postExit)
        assertFalse(rule.preEntry)
        assertEquals(G.Fade(beats = FinalLapFanfareCompiler.FULL_FADE_OUT_SEC * plan.spec.bpm / 60.0), rule.fadeOut)
        assertEquals(G.Fade(), rule.fadeIn)
        assertEquals(
            G.TransitionTimeline(
                FinalLapFanfareCompiler.FANFARE_DELAY_FRAMES,
                FinalLapFanfareCompiler.FAST_DELAY_FRAMES,
                FinalLapFanfareCompiler.FAST_DELAY_FRAMES,
                0L,
            ),
            rule.timeline,
        )

        assertTrue(FinalLapFanfareCompiler.ASSET in plan.preparedAssets)
        assertTrue(
            FinalLapFanfareCompiler.ASSET in plan.entryPreparedAssets.getValue(ExtraInteractiveCompilers.TWO_STATE_SEG_MAIN),
            "Switching back to Main must keep FinalLapFF prepared",
        )
    }
}
