package com.mylittleblover.mkwplayer.audio

import android.content.Context

internal data class AudioExportConfig(
    /** Persisted Android SAF tree URI (`OpenDocumentTree`), or null for default MediaStore `Music/JukeKart`. */
    val exportTreeUri: String? = null,
    /** Human-readable folder label shown in Settings (e.g. `Music/JukeKart` or chosen SAF folder). */
    val exportDisplayPath: String? = null,
) {
    val usesCustomDirectory: Boolean get() = !exportTreeUri.isNullOrBlank()
}

/**
 * Persists the default destination directory used when exporting rendered flow audio.
 */
internal object AudioExportSettings {
    private const val FILE = "mkw-audio-export"
    private const val KEY_TREE_URI = "export-tree-uri"
    private const val KEY_DISPLAY_PATH = "export-display-path"
    const val DEFAULT_RELATIVE_PATH = "Music/JukeKart"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun current(context: Context): AudioExportConfig {
        val prefs = preferences(context)
        val uri = prefs.getString(KEY_TREE_URI, null)?.takeIf { it.isNotBlank() }
        val label = prefs.getString(KEY_DISPLAY_PATH, null)?.takeIf { it.isNotBlank() }
        return AudioExportConfig(
            exportTreeUri = uri,
            exportDisplayPath = label,
        )
    }

    fun setExportLocation(
        context: Context,
        treeUri: String?,
        displayPath: String?,
    ): AudioExportConfig {
        val cleanUri = treeUri?.takeIf { it.isNotBlank() }
        val cleanLabel = displayPath?.takeIf { it.isNotBlank() }
        preferences(context).edit().apply {
            if (cleanUri == null) {
                remove(KEY_TREE_URI)
                remove(KEY_DISPLAY_PATH)
            } else {
                putString(KEY_TREE_URI, cleanUri)
                if (cleanLabel != null) putString(KEY_DISPLAY_PATH, cleanLabel) else remove(KEY_DISPLAY_PATH)
            }
        }.apply()
        return current(context)
    }

    fun resetExportLocation(context: Context): AudioExportConfig =
        setExportLocation(context, null, null)
}
