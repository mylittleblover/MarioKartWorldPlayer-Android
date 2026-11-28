package com.mylittleblover.mkwplayer.ui.components

import com.mylittleblover.mkwplayer.ui.kit.MkwSliderSpecialStyle
import com.mylittleblover.mkwplayer.ui.kit.sampleSpecialStyleColor
import com.mylittleblover.mkwplayer.ui.theme.AppThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StaffCreditsProgressThemeTest {

    @Test
    fun allEighteenCuesMatchExactChronologicalUserMarkers() {
        val expectedTimestamps = listOf(
            0L,
            15_974L,
            33_049L,
            58_051L,
            76_553L,
            87_749L,
            120_871L,
            133_268L,
            144_276L,
            152_929L,
            159_926L,
            176_437L,
            190_862L,
            198_243L,
            233_275L,
            241_751L,
            250_434L,
            275_205L,
        )
        assertEquals(18, StaffCreditsProgressTheme.CUES.size)
        assertEquals(expectedTimestamps, StaffCreditsProgressTheme.CUES.map { it.baseStartMs })
        assertEquals(
            StaffCreditsProgressTheme.CUES.size,
            StaffCreditsProgressTheme.CUES.map { it.style.key }.distinct().size,
        )
    }

    @Test
    fun mainMenuSectionsAlwaysUseDefaultMarioRed() {
        val m1 = StaffCreditsProgressTheme.resolveStyle(StaffCreditsProgressTheme.Variant.GAME_RIP, 0L)
        val m1Mid = StaffCreditsProgressTheme.resolveStyle(StaffCreditsProgressTheme.Variant.GAME_RIP, 15_973L)
        val m2 = StaffCreditsProgressTheme.resolveStyle(StaffCreditsProgressTheme.Variant.GAME_RIP, 198_243L)
        val m2Mid = StaffCreditsProgressTheme.resolveStyle(StaffCreditsProgressTheme.Variant.GAME_RIP, 233_274L)

        assertTrue(m1 is MkwSliderSpecialStyle.Solid)
        assertEquals(AppThemeAccent.Default.primary, (m1 as MkwSliderSpecialStyle.Solid).color)
        assertEquals(m1.key, m1Mid.key)

        assertTrue(m2 is MkwSliderSpecialStyle.Solid)
        assertEquals(AppThemeAccent.Default.primary, (m2 as MkwSliderSpecialStyle.Solid).color)
        assertEquals(m2.key, m2Mid.key)
    }

    @Test
    fun dualCourseOverlapsRenderAsTwoDistinctSegments() {
        val acornDandelion = StaffCreditsProgressTheme.resolveStyle(
            StaffCreditsProgressTheme.Variant.GAME_RIP,
            241_751L,
        )
        assertTrue(acornDandelion is MkwSliderSpecialStyle.SplitDual)
        acornDandelion as MkwSliderSpecialStyle.SplitDual
        assertEquals(StaffCreditsProgressTheme.COLOR_ACORN_HEIGHTS, acornDandelion.leftColor)
        assertEquals(StaffCreditsProgressTheme.COLOR_DANDELION_DEPTHS, acornDandelion.rightColor)
        assertTrue("Dandelion Depths should be cool-toned", acornDandelion.rightColor.blue > acornDandelion.rightColor.red)
        assertNotEquals(acornDandelion.leftColor, acornDandelion.rightColor)
        assertEquals(0.5f, acornDandelion.splitFraction, 0.001f)
        assertEquals(acornDandelion.leftColor, sampleSpecialStyleColor(acornDandelion, 0.25f, 0f))
        assertEquals(acornDandelion.rightColor, sampleSpecialStyleColor(acornDandelion, 0.75f, 0f))

        val whistleOasis = StaffCreditsProgressTheme.resolveStyle(
            StaffCreditsProgressTheme.Variant.GAME_RIP,
            250_434L,
        )
        assertTrue(whistleOasis is MkwSliderSpecialStyle.SplitDual)
        whistleOasis as MkwSliderSpecialStyle.SplitDual
        assertEquals(StaffCreditsProgressTheme.COLOR_WHISTLESTOP_SUMMIT, whistleOasis.leftColor)
        assertEquals(StaffCreditsProgressTheme.COLOR_FARAWAY_OASIS, whistleOasis.rightColor)
        assertTrue("Whistlestop Summit should be warm-toned", whistleOasis.leftColor.red > whistleOasis.leftColor.blue)
        assertNotEquals(whistleOasis.leftColor, whistleOasis.rightColor)
        assertEquals(whistleOasis.leftColor, sampleSpecialStyleColor(whistleOasis, 0.20f, 0f))
        assertEquals(whistleOasis.rightColor, sampleSpecialStyleColor(whistleOasis, 0.80f, 0f))
    }

    @Test
    fun rainbowRoadSectionUsesSeamlessClosedLoopRainbowFlow() {
        val rainbow = StaffCreditsProgressTheme.resolveStyle(
            StaffCreditsProgressTheme.Variant.GAME_RIP,
            275_205L,
        )
        assertTrue(rainbow is MkwSliderSpecialStyle.RainbowFlow)
        rainbow as MkwSliderSpecialStyle.RainbowFlow
        assertTrue(rainbow.colors.size >= 7)
        assertEquals(rainbow.colors.first(), rainbow.colors.last())
        assertEquals(
            sampleSpecialStyleColor(rainbow, 0f, 0f),
            sampleSpecialStyleColor(rainbow, 1f, 0f),
        )
    }

    @Test
    fun detectVariantMatchesBothGameRipAndNintendoMusicAcrossAllKeyFormats() {
        assertEquals(
            StaffCreditsProgressTheme.Variant.GAME_RIP,
            StaffCreditsProgressTheme.detectVariant(canonicalTrackId = "extra:staff_credits"),
        )
        assertEquals(
            StaffCreditsProgressTheme.Variant.GAME_RIP,
            StaffCreditsProgressTheme.detectVariant(selectedKey = "pl:fav:0:extra:staff_credits"),
        )
        assertEquals(
            StaffCreditsProgressTheme.Variant.GAME_RIP,
            StaffCreditsProgressTheme.detectVariant(selectedKey = "BGM_Demo_Staffroll"),
        )
        assertEquals(
            StaffCreditsProgressTheme.Variant.GAME_RIP,
            StaffCreditsProgressTheme.detectVariant(assetSha256 = StaffCreditsProgressTheme.GAME_RIP_SHA256),
        )

        assertEquals(
            StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
            StaffCreditsProgressTheme.detectVariant(canonicalTrackId = "extra:nm_special_119"),
        )
        assertEquals(
            StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
            StaffCreditsProgressTheme.detectVariant(selectedKey = "ref:4:extra:nm_special_119:uuid"),
        )
        assertEquals(
            StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
            StaffCreditsProgressTheme.detectVariant(selectedKey = "NM_Special_119"),
        )
        assertEquals(
            StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
            StaffCreditsProgressTheme.detectVariant(assetSha256 = StaffCreditsProgressTheme.NM_SPECIAL_SHA256),
        )

        assertNull(StaffCreditsProgressTheme.detectVariant(canonicalTrackId = "extra:title"))
        assertNull(StaffCreditsProgressTheme.detectVariant(canonicalTrackId = "Central_00"))
    }

    @Test
    fun nintendoMusicVariantShiftsAllCourseCuesWhilePinningMainMenuAtZero() {
        val offset = -2_920L
        assertEquals(
            0L,
            StaffCreditsProgressTheme.effectiveStartMs(
                StaffCreditsProgressTheme.CUES[0],
                StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
                offset,
            ),
        )
        // Peach Stadium: 15,974 - 2,920 = 13,054 ms
        assertEquals(
            "Main Menu",
            StaffCreditsProgressTheme.resolveCue(
                StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
                13_053L,
                offset,
            ).label,
        )
        assertEquals(
            "Peach Stadium",
            StaffCreditsProgressTheme.resolveCue(
                StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
                13_054L,
                offset,
            ).label,
        )
        // Rainbow Road: 275,205 - 2,920 = 272,285 ms
        assertEquals(
            "Whistlestop Summit + Faraway Oasis",
            StaffCreditsProgressTheme.resolveCue(
                StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
                272_284L,
                offset,
            ).label,
        )
        assertEquals(
            "Rainbow Road",
            StaffCreditsProgressTheme.resolveCue(
                StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC,
                272_285L,
                offset,
            ).label,
        )
    }

    @Test
    fun rmsEnvelopeCalibratorRecoversExactShiftAndRejectsFlatNoise() {
        val tpl = StaffCreditsOffsetCalibrator.referenceTemplateForTest()
        assertEquals(800, tpl.size)

        // Build shifted synthetic envelopes (offset = targetMs - staffrollMs)
        for (expectedShiftMs in listOf(0L, -2_910L, -2_550L, -2_260L, 400L)) {
            val env = FloatArray(1600) { 0.01f }
            val startBin = 300 + (expectedShiftMs / 10L).toInt()
            for (i in tpl.indices) {
                env[startBin + i] = (tpl[i] + 3f).coerceAtLeast(0.01f) * 0.25f
            }
            val result = StaffCreditsOffsetCalibrator.estimateOffsetMsFromEnvelope(env)
            assertNotNull("Expected match for shift $expectedShiftMs", result)
            assertTrue(
                "Expected |${result!!.offsetMs} - $expectedShiftMs| <= 10ms",
                abs(result.offsetMs - expectedShiftMs) <= 10L,
            )
            assertTrue("Expected high correlation, got ${result.correlation}", result.correlation > 0.99f)
        }

        // Flat silence must return null instead of bogus shift
        val silence = FloatArray(1600) { 0f }
        assertNull(StaffCreditsOffsetCalibrator.estimateOffsetMsFromEnvelope(silence))
    }
}
