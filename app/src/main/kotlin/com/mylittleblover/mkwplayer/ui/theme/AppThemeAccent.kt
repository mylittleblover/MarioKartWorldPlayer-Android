package com.mylittleblover.mkwplayer.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mylittleblover.mkwplayer.R

/**
 * 7 character-inspired light accent themes for JukeKart (#28).
 *
 * Every primary token is WCAG AA compliant (>= 4.5:1) against both pure white (`#FFFFFF`) and
 * porcelain card surfaces (`#F8F8FA`), as well as with pure white foreground text; every
 * [primaryDeep] token is WCAG AA compliant (>= 4.5:1) against both [tint] and [tintHover].
 */
internal enum class AppThemeAccent(
    val id: String,
    @StringRes val labelRes: Int,
    val primary: Color,
    val primaryDeep: Color,
    val tint: Color,
    val tintHover: Color,
    val swatchPreview: Color,
) {
    MARIO_RED(
        id = "mario_red",
        labelRes = R.string.theme_accent_mario_red,
        primary = Color(0xFFD91E2A),
        primaryDeep = Color(0xFFA8121D),
        tint = Color(0xFFFCE8EA),
        tintHover = Color(0xFFF8D4D8),
        swatchPreview = Color(0xFFE52521),
    ),
    LUIGI_GREEN(
        id = "luigi_green",
        labelRes = R.string.theme_accent_luigi_green,
        primary = Color(0xFF16803C),
        primaryDeep = Color(0xFF0F5C2B),
        tint = Color(0xFFE6F6EC),
        tintHover = Color(0xFFD1EDD9),
        swatchPreview = Color(0xFF22A84A),
    ),
    PEACH_PINK(
        id = "peach_pink",
        labelRes = R.string.theme_accent_peach_pink,
        primary = Color(0xFFC83276),
        primaryDeep = Color(0xFF961E54),
        tint = Color(0xFFFCE8F2),
        tintHover = Color(0xFFF7D2E5),
        swatchPreview = Color(0xFFF062A2),
    ),
    WARIO_YELLOW(
        id = "wario_yellow",
        labelRes = R.string.theme_accent_wario_yellow,
        // Warm golden-amber tuned to 5.00:1 on #FFFFFF and 4.72:1 on #F8F8FA (WCAG AA compliant).
        primary = Color(0xFF966600),
        primaryDeep = Color(0xFF7A5300),
        tint = Color(0xFFFDF5DF),
        tintHover = Color(0xFFF9E8BC),
        swatchPreview = Color(0xFFF2C200),
    ),
    WALUIGI_PURPLE(
        id = "waluigi_purple",
        labelRes = R.string.theme_accent_waluigi_purple,
        primary = Color(0xFF6B2FA0),
        primaryDeep = Color(0xFF4C1E75),
        tint = Color(0xFFF1E8FA),
        tintHover = Color(0xFFE2D1F5),
        swatchPreview = Color(0xFF7E3BBF),
    ),
    DAISY_ORANGE(
        id = "daisy_orange",
        labelRes = R.string.theme_accent_daisy_orange,
        // Vibrant burnt orange tuned to 5.10:1 on #FFFFFF and 4.80:1 on #F8F8FA (WCAG AA compliant).
        primary = Color(0xFFBA4C00),
        primaryDeep = Color(0xFF963B00),
        tint = Color(0xFFFDEEE3),
        tintHover = Color(0xFFFADBC4),
        swatchPreview = Color(0xFFF27405),
    ),
    ROSALINA_BLUE(
        id = "rosalina_blue",
        labelRes = R.string.theme_accent_rosalina_blue,
        primary = Color(0xFF0E788A),
        primaryDeep = Color(0xFF095664),
        tint = Color(0xFFE4F5F8),
        tintHover = Color(0xFFCAEAF0),
        swatchPreview = Color(0xFF29B6C9),
    );

    companion object {
        val Default: AppThemeAccent = MARIO_RED

        fun fromId(raw: String?): AppThemeAccent {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty()) return Default
            return entries.firstOrNull {
                it.id.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
            } ?: Default
        }
    }
}

internal val LocalAppThemeAccent = compositionLocalOf { AppThemeAccent.Default }

/** Builds the Porcelain & Hairline light Material3 [ColorScheme] parameterized by [accent]. */
internal fun mkwLightColorScheme(accent: AppThemeAccent = AppThemeAccent.Default): ColorScheme =
    lightColorScheme(
        primary = accent.primary,
        onPrimary = Color.White,
        primaryContainer = accent.tint,
        onPrimaryContainer = accent.primaryDeep,
        secondary = Color(0xFF5E626E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF0F1F5),
        onSecondaryContainer = Color(0xFF30343E),
        background = Color.White,
        onBackground = Color(0xFF20232B),
        surface = Color.White,
        onSurface = Color(0xFF20232B),
        surfaceVariant = Color(0xFFF0F1F5),
        onSurfaceVariant = Color(0xFF626672),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8F8FA),
        surfaceContainer = Color(0xFFF5F5F8),
        surfaceContainerHigh = Color(0xFFF0F1F5),
        surfaceContainerHighest = Color(0xFFE8E9EF),
        outline = Color(0xFF747884),
        outlineVariant = Color(0xFFE2E4EA),
    )
