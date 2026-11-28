package com.mylittleblover.mkwplayer.ui.settings

import androidx.compose.ui.graphics.Color
import com.mylittleblover.mkwplayer.ui.Contrast
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.theme.AppThemeAccent
import com.mylittleblover.mkwplayer.ui.theme.mkwLightColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppThemeAccentTest {

    @Test
    fun sevenCharacterThemesExistWithUniqueIdsAndSafeFallback() {
        assertEquals(7, AppThemeAccent.entries.size)
        assertEquals(7, AppThemeAccent.entries.map { it.id }.toSet().size)
        assertEquals(AppThemeAccent.MARIO_RED, AppThemeAccent.Default)
        assertEquals(AppThemeAccent.LUIGI_GREEN, AppThemeAccent.fromId("luigi_green"))
        assertEquals(AppThemeAccent.ROSALINA_BLUE, AppThemeAccent.fromId("ROSALINA_BLUE"))
        assertEquals(AppThemeAccent.MARIO_RED, AppThemeAccent.fromId("unknown_id"))
        assertEquals(AppThemeAccent.MARIO_RED, AppThemeAccent.fromId(null))
    }

    @Test
    fun everyCharacterThemePassesWcagAaNormalTextContrast() {
        for (accent in AppThemeAccent.entries) {
            assertTrue(
                Contrast.passesNormalText(Color.White, accent.primary),
                "${accent.name} white on primary failed: ${Contrast.describe(Color.White, accent.primary)}",
            )
            assertTrue(
                Contrast.passesNormalText(accent.primary, MkwKitTokens.Palette.ground),
                "${accent.name} primary on ground failed: ${Contrast.describe(accent.primary, MkwKitTokens.Palette.ground)}",
            )
            assertTrue(
                Contrast.passesNormalText(accent.primary, MkwKitTokens.Palette.card),
                "${accent.name} primary on card failed: ${Contrast.describe(accent.primary, MkwKitTokens.Palette.card)}",
            )
            assertTrue(
                Contrast.passesNormalText(accent.primaryDeep, accent.tint),
                "${accent.name} primaryDeep on tint failed: ${Contrast.describe(accent.primaryDeep, accent.tint)}",
            )
            assertTrue(
                Contrast.passesNormalText(accent.primaryDeep, accent.tintHover),
                "${accent.name} primaryDeep on tintHover failed: ${Contrast.describe(accent.primaryDeep, accent.tintHover)}",
            )
            val scheme = mkwLightColorScheme(accent)
            assertEquals(accent.primary, scheme.primary)
            assertEquals(accent.tint, scheme.primaryContainer)
            assertEquals(accent.primaryDeep, scheme.onPrimaryContainer)
        }
    }

    @Test
    fun applyAccentDynamicallyUpdatesMkwKitTokensPalette() {
        try {
            for (accent in AppThemeAccent.entries) {
                MkwKitTokens.applyAccent(accent)
                assertEquals(accent, MkwKitTokens.currentAccent)
                assertEquals(accent.primary, MkwKitTokens.Palette.crimson)
                assertEquals(accent.primaryDeep, MkwKitTokens.Palette.crimsonDeep)
                assertEquals(accent.tint, MkwKitTokens.Palette.rose)
                assertEquals(accent.tintHover, MkwKitTokens.Palette.roseHover)
            }
        } finally {
            MkwKitTokens.applyAccent(AppThemeAccent.Default)
        }
    }
}
