package com.mylittleblover.mkwplayer.library

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the picked library so it survives a restart.
 *
 * Before this, every launch started empty: the user re-picked the same files and the
 * app forgot everything it had already measured about them. That is not a bug in any
 * one screen, it is a missing layer.
 *
 * Takes a [File] rather than a Context so it can be exercised on the JVM with a temp
 * directory; the Android side just passes `filesDir/library.json`.
 *
 * Written atomically through a temp file and a rename, because a half-written library
 * file would be worse than none: it would parse as empty and silently lose everything.
 * A file that fails to parse is moved aside rather than deleted, so a schema change
 * does not destroy the user's list without a trace.
 */
internal class LibraryStore(private val file: File) {

    /** One persisted row. Deliberately flat and versionless-free: every field has a default. */
    data class Row(
        val key: String,
        val name: String,
        val frames: Long,
        val sampleRate: Int,
        val playable: Boolean,
        val reason: String? = null,
    )

    data class Loaded(val rows: List<Row>, val problem: String? = null)

    companion object {
        /** Bumped only when a field's meaning changes, not when one is added. */
        const val SCHEMA_VERSION = 1

        private const val ROOT_VERSION = "schemaVersion"
        private const val ROWS = "rows"
    }

    /**
     * Reads the stored rows.
     *
     * Never throws: a missing file is an empty library, and a corrupt one is reported
     * with the rows that could be salvaged.
     */
    fun load(): Loaded {
        if (!file.exists()) return Loaded(emptyList())
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return Loaded(emptyList(), "The library file could not be read: ${e.message}")
        }
        if (text.isBlank()) return Loaded(emptyList())

        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            quarantine()
            return Loaded(emptyList(), "The library file was unreadable and has been set aside.")
        }

        val version = root.optInt(ROOT_VERSION, 0)
        if (version != SCHEMA_VERSION) {
            quarantine()
            return Loaded(
                emptyList(),
                "The library was written by a different version ($version) and has been set aside.",
            )
        }

        val array = root.optJSONArray(ROWS) ?: return Loaded(emptyList())
        val rows = ArrayList<Row>(array.length())
        var skipped = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            val key = item?.optString("key").orEmpty()
            if (item == null || key.isEmpty()) {
                skipped++
                continue
            }
            rows += Row(
                key = key,
                name = item.optString("name").ifEmpty { key },
                frames = item.optLong("frames", 0),
                sampleRate = item.optInt("sampleRate", 0),
                playable = item.optBoolean("playable", false),
                reason = item.optString("reason").takeIf { it.isNotEmpty() },
            )
        }

        return Loaded(
            rows = rows,
            problem = if (skipped > 0) "$skipped library entries were unreadable." else null,
        )
    }

    /**
     * Writes [rows], replacing the file atomically.
     *
     * Returns null on success or a displayable problem. Storage failing must not crash
     * playback, so the caller keeps working with the in-memory list either way.
     */
    fun save(rows: List<Row>): String? {
        val root = JSONObject()
        try {
            root.put(ROOT_VERSION, SCHEMA_VERSION)
            val array = JSONArray()
            rows.forEach { row ->
                val item = JSONObject()
                item.put("key", row.key)
                item.put("name", row.name)
                item.put("frames", row.frames)
                item.put("sampleRate", row.sampleRate)
                item.put("playable", row.playable)
                row.reason?.let { item.put("reason", it) }
                array.put(item)
            }
            root.put(ROWS, array)
        } catch (e: Exception) {
            return "The library could not be serialised: ${e.message}"
        }

        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return "The library directory could not be created."
        }

        val temp = File(file.parentFile, "${file.name}.tmp")
        return try {
            temp.writeText(root.toString())
            // Replace through Files.move rather than File.renameTo: renameTo does not
            // overwrite an existing destination on Windows, so the second save would
            // silently fail there while working on Android. REPLACE_EXISTING makes the
            // behaviour the same on both, and still atomic enough that either the old
            // file or the complete new one is present.
            java.nio.file.Files.move(
                temp.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            null
        } catch (e: Exception) {
            temp.delete()
            "The library could not be written: ${e.message}"
        }
    }

    /** Moves an unreadable file aside instead of deleting it. */
    private fun quarantine() {
        try {
            file.renameTo(File(file.parentFile, "${file.name}.corrupt"))
        } catch (_: Exception) {
            // Losing the quarantine rename is acceptable; losing the data twice is not.
        }
    }
}
