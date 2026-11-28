package com.mylittleblover.mkwplayer.ui.theme

import androidx.compose.runtime.Composable

/**
 * Convenience re-export of [com.mylittleblover.mkwplayer.ui.MkwTheme] inside `ui.theme` so callers
 * importing from either `ui` or `ui.theme` share one single dynamic theme entry point.
 */
@Composable
internal fun MkwTheme(
    accent: AppThemeAccent? = null,
    content: @Composable () -> Unit,
) {
    com.mylittleblover.mkwplayer.ui.MkwTheme(accent = accent, content = content)
}
