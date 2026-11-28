package com.mylittleblover.mkwplayer.ui.theme

import android.content.Context
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens

/**
 * Persists the user's selected [AppThemeAccent] in SharedPreferences and synchronizes the live
 * Compose state in [MkwKitTokens] so theme switches re-theme every screen immediately with zero
 * process restart.
 */
internal object ThemeSettingsStore {
    private const val PREFS_FILE = "mkw-theme-settings"
    private const val KEY_ACCENT_ID = "accent_id"

    @Volatile
    private var loadedFromDisk: Boolean = false

    /** Live Compose-observable active theme accent. */
    val activeAccent: AppThemeAccent
        get() = MkwKitTokens.currentAccent

    private fun preferences(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Reads the persisted [AppThemeAccent] from SharedPreferences without mutating live state. */
    fun current(context: Context): AppThemeAccent = runCatching {
        val raw = preferences(context).getString(KEY_ACCENT_ID, null)
        AppThemeAccent.fromId(raw)
    }.getOrDefault(AppThemeAccent.Default)

    /**
     * Loads the persisted accent once on app start and applies it to [MkwKitTokens].
     * Subsequent calls return the live [activeAccent] without redundant disk reads.
     */
    fun ensureLoaded(context: Context): AppThemeAccent {
        if (!loadedFromDisk) {
            val stored = current(context)
            MkwKitTokens.applyAccent(stored)
            loadedFromDisk = true
        }
        return activeAccent
    }

    /** Persists [accent] and immediately updates live theme tokens across the entire UI. */
    fun select(context: Context, accent: AppThemeAccent): AppThemeAccent {
        runCatching {
            preferences(context).edit().putString(KEY_ACCENT_ID, accent.id).apply()
        }
        loadedFromDisk = true
        MkwKitTokens.applyAccent(accent)
        return accent
    }

    /** Resets in-memory state back to default (used in unit tests). */
    internal fun resetForTesting() {
        loadedFromDisk = false
        MkwKitTokens.applyAccent(AppThemeAccent.Default)
    }
}
