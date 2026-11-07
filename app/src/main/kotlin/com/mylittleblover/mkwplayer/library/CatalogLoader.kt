package com.mylittleblover.mkwplayer.library

import org.json.JSONObject

/**
 * The catalog of tracks this build knows about, bundled as an asset.
 *
 * This is a *compatibility manifest*, not a library of files. It says which tracks exist
 * in the game data that was measured, and for each one whether this build can decode it.
 * The files themselves are never bundled -- they are the user's to supply -- so every
 * entry is "known" rather than "available".
 *
 * The provisional status travels with the data instead of being dropped at build time.
 * The source index covers 4 of 499 archives and its own reasons array says verification
 * was not performed, so presenting the list as the complete soundtrack would be a false
 * claim the UI could not walk back.
 */
internal object CatalogLoader {

    data class Entry(
        val name: String,
        val category: String,
        val codecKind: Int,
        val sampleRate: Int,
        val channels: Int,
        val frames: Long,
        val loopStart: Long?,
        val loopEnd: Long?,
        val playable: Boolean,
        val reason: String?,
    ) {
        /** Duration for display; 0 when the rate is unusable. */
        val durationSeconds: Long
            get() = if (sampleRate <= 0) 0 else frames / sampleRate
    }

    data class Catalog(
        val entries: List<Entry>,
        val playableCount: Int,
        val categoryCounts: Map<String, Int>,
        val playableByCategory: Map<String, Int>,
        val freezeStatus: String,
        val coverage: String,
        val coverageReasons: List<String>,
        val archivesRead: Int,
        val archivesExpected: Int,
    ) {
        /** True when the catalog is known to be partial; the UI must say so. */
        val isPartial: Boolean
            get() = freezeStatus != "FROZEN" || coverage != "COMPLETE"

        fun categories(): List<String> = entries.map { it.category }.distinct().sorted()

        fun inCategory(category: String): List<Entry> = entries.filter { it.category == category }

        /** Entries whose internal name matches a picked file name. */
        fun match(fileName: String): Entry? {
            val stem = fileName.substringBeforeLast('.')
            return entries.firstOrNull { it.name == stem }
        }
    }

    /**
     * Parses the catalog JSON.
     *
     * Returns null rather than an empty catalog when the document is unusable, so the UI
     * can distinguish "no catalog bundled" from "a catalog with no entries".
     */
    fun parse(text: String): Catalog? {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        if (root.optInt("schemaVersion", 0) != SCHEMA_VERSION) return null

        val array = root.optJSONArray("entries") ?: return null
        val entries = ArrayList<Entry>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.isEmpty()) continue
            entries += Entry(
                name = name,
                category = item.optString("category").ifEmpty { "UNKNOWN" },
                codecKind = item.optInt("codecKind", -1),
                sampleRate = item.optInt("sampleRate", 0),
                channels = item.optInt("channels", 0),
                frames = item.optLong("frames", 0),
                loopStart = item.optLongOrNull("loopStart"),
                loopEnd = item.optLongOrNull("loopEnd"),
                playable = item.optBoolean("playable", false),
                reason = item.optString("reason").takeIf { it.isNotEmpty() },
            )
        }

        return Catalog(
            entries = entries,
            playableCount = root.optInt("playableCount", entries.count { it.playable }),
            categoryCounts = readCounts(root.optJSONObject("categoryCounts")),
            playableByCategory = readCounts(root.optJSONObject("playableByCategory")),
            freezeStatus = root.optString("freezeStatus").ifEmpty { "UNKNOWN" },
            coverage = root.optString("coverage").ifEmpty { "UNKNOWN" },
            coverageReasons = buildList {
                val array = root.optJSONArray("coverageReasons") ?: return@buildList
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            },
            archivesRead = root.optJSONArray("archivesRead")?.length() ?: 0,
            archivesExpected = root.optInt("archivesExpected", 0),
        )
    }

    /** Wire version this parser understands. */
    const val SCHEMA_VERSION = 1

    private fun readCounts(source: JSONObject?): Map<String, Int> {
        if (source == null) return emptyMap()
        val result = LinkedHashMap<String, Int>()
        source.keys().forEach { key -> result[key] = source.optInt(key, 0) }
        return result
    }

    /** Absent loops come back as null, not as 0, which would be a real frame number. */
    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key, 0)
}
