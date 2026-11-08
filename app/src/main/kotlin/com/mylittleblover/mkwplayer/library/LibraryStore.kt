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

    /**
     * One persisted logical track.
     *
     * References assets by digest, so a renamed or moved file still resolves. A flow's parts are
     * listed so a missing part can be reported rather than silently skipped.
     */
    data class TrackRow(
        val id: String,
        val title: String,
        val kind: String,
        val primary: String,
        val assets: List<String>,
        val frames: Long,
        val sampleRate: Int,
        val isFlow: Boolean,
    )

    data class Loaded(
        val rows: List<Row>,
        val tracks: List<TrackRow> = emptyList(),
        val problem: String? = null,
    )

    companion object {
        /**
         * Bumped only when a field's meaning changes, not when one is added.
         *
         * v2 added the `tracks` array. A v1 file has no tracks and still loads: its rows become
         * single-file tracks on read, so an upgrade does not lose the user's list.
         */
        const val SCHEMA_VERSION = 2

        private const val ROOT_VERSION = "schemaVersion"
        private const val ROWS = "rows"
        private const val TRACKS = "tracks"

        /** Version this reader still accepts, upgraded in memory. */
        const val LEGACY_SCHEMA_VERSION = 1
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
            return Loaded(emptyList(), problem = "The library file could not be read: ${e.message}")
        }
        if (text.isBlank()) return Loaded(emptyList())

        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            quarantine()
            return Loaded(emptyList(), problem = "The library file was unreadable and has been set aside.")
        }

        val version = root.optInt(ROOT_VERSION, 0)
        if (version != SCHEMA_VERSION && version != LEGACY_SCHEMA_VERSION) {
            quarantine()
            return Loaded(
                emptyList(),
                problem = "The library was written by a different version ($version) and has been set aside.",
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

        // v1 files carry no tracks. Rather than dropping their rows or refusing the file, each row
        // becomes a single-file track: the user's list survives an upgrade, and the first save
        // writes the new shape.
        val tracks = if (version >= SCHEMA_VERSION) {
            readTracks(root)
        } else {
            rows.map { row ->
                TrackRow(
                    id = "single:${row.key.take(32)}",
                    title = row.name,
                    kind = "UNCLASSIFIED",
                    primary = row.key,
                    assets = listOf(row.key),
                    frames = row.frames,
                    sampleRate = row.sampleRate,
                    isFlow = false,
                )
            }
        }

        return Loaded(
            rows = rows,
            tracks = tracks,
            problem = if (skipped > 0) "$skipped library entries were unreadable." else null,
        )
    }

    /** Reads the v2 track array, skipping entries that cannot be used. */
    private fun readTracks(root: JSONObject): List<TrackRow> {
        val array = root.optJSONArray(TRACKS) ?: return emptyList()
        val tracks = ArrayList<TrackRow>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val assets = item.optJSONArray("assets") ?: continue
            val assetList = ArrayList<String>(assets.length())
            for (position in 0 until assets.length()) {
                assets.optString(position).takeIf { it.isNotEmpty() }?.let { assetList += it }
            }
            val primary = item.optString("primary")
            if (primary.isEmpty() || assetList.isEmpty() || primary !in assetList) continue
            val id = item.optString("id")
            if (id.isEmpty()) continue

            tracks += TrackRow(
                id = id,
                title = item.optString("title").ifEmpty { id },
                kind = item.optString("kind").ifEmpty { "UNCLASSIFIED" },
                primary = primary,
                assets = assetList,
                frames = item.optLong("frames", 0),
                sampleRate = item.optInt("sampleRate", 0),
                isFlow = item.optBoolean("isFlow", assetList.size > 1),
            )
        }
        return tracks
    }

    /**
     * Writes [rows], replacing the file atomically.
     *
     * Returns null on success or a displayable problem. Storage failing must not crash
     * playback, so the caller keeps working with the in-memory list either way.
     */
    fun save(rows: List<Row>, tracks: List<TrackRow> = emptyList()): String? {
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

            val trackArray = org.json.JSONArray()
            tracks.forEach { track ->
                val item = JSONObject()
                item.put("id", track.id)
                item.put("title", track.title)
                item.put("kind", track.kind)
                item.put("primary", track.primary)
                item.put("assets", org.json.JSONArray(track.assets))
                item.put("frames", track.frames)
                item.put("sampleRate", track.sampleRate)
                item.put("isFlow", track.isFlow)
                trackArray.put(item)
            }
            root.put(TRACKS, trackArray)
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
