package com.mylittleblover.mkwplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.theme.AppThemeAccent
import com.mylittleblover.mkwplayer.ui.theme.LocalAppThemeAccent
import com.mylittleblover.mkwplayer.ui.theme.ThemeSettingsStore
import com.mylittleblover.mkwplayer.ui.theme.mkwLightColorScheme

/**
 * Porcelain & Hairline light theme parameterized by the active character [AppThemeAccent].
 *
 * When [accent] is omitted (default), observes [ThemeSettingsStore.activeAccent] and loads the
 * persisted user preference once on startup so switching accents in Settings re-themes every screen
 * immediately without restarting.
 */
@Composable
internal fun MkwTheme(
    accent: AppThemeAccent? = null,
    content: @Composable () -> Unit,
) {
    val resolvedAccent = if (accent != null) {
        remember(accent) {
            MkwKitTokens.applyAccent(accent)
            accent
        }
        accent
    } else {
        val context = LocalContext.current
        remember(context) { ThemeSettingsStore.ensureLoaded(context) }
        ThemeSettingsStore.activeAccent
    }

    val colorScheme = remember(resolvedAccent) { mkwLightColorScheme(resolvedAccent) }
    CompositionLocalProvider(LocalAppThemeAccent provides resolvedAccent) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
