package com.mylittleblover.mkwplayer.library

import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads the OST index into [TrackEntry] values.
 *
 * The index is produced by the Python tooling (`mkw_tools/inventory`) and is
 * marked PROVISIONAL in its own header fields. This loader therefore trusts the
 * structure but not the completeness: it never invents entries, and it surfaces
 * the index's own freeze status so the UI can say the library is partial.
 *
 * Codec kind is not in the index. `assetSha256` is the soundness anchor instead:
 * the index says the digest matched a file on disk, and the caller resolves the
 * actual bytes. Playability is decided from those bytes, never guessed from the
 * entry's class -- SEQUENCE and DEMO proved that guessing is wrong.
 */
object OstIndexLoader {

    /** What the index says about its own completeness. */
    data class IndexStatus(
        val schemaVersion: String,
        val freezeStatus: String,
        val coverage: String,
        val archivesRead: Int,
        val archivesExpected: Int,
        val entryCount: Int,
        val reasons: List<String>,
    ) {
        /** False whenever the index admits it does not cover everything. */
        val complete: Boolean
            get() = coverage.equals("COMPLETE", ignoreCase = true) &&
                freezeStatus.equals("FROZEN", ignoreCase = true) &&
                archivesRead == archivesExpected
    }

    /**
     * @param entries rows that could be loaded, in index order
     * @param skippedCount rows the index carried but that could not be listed,
     *   because an entry without a stable key cannot be persisted or selected.
     *   Reported rather than silently dropped so a caller can surface it.
     */
    data class Loaded(
        val status: IndexStatus,
        val entries: List<TrackEntry>,
        val skippedCount: Int,
    )

    class LoadError(message: String) : Exception(message)

    /**
     * Parses the index JSON.
     *
     * @param codecKindBySha256 maps an asset digest to the BWAV codec kind read
     *   from the real file. Entries whose digest is absent are loaded as
     *   not-playable rather than assumed playable.
     */
    fun load(
        json: String,
        codecKindBySha256: (String) -> Int?,
    ): Loaded {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw LoadError("index is not valid JSON: ${e.message}")
        }

        val status = IndexStatus(
            schemaVersion = root.optString("schemaVersion", "unknown"),
            freezeStatus = root.optString("freezeStatus", "unknown"),
            coverage = root.optString("coverage", "unknown"),
            archivesRead = root.optJSONArray("archivesRead")?.length() ?: 0,
            archivesExpected = root.optInt("archivesExpected", 0),
            entryCount = root.optInt("entryCount", 0),
            reasons = root.optJSONArray("reasons")?.let { array ->
                (0 until array.length()).map { array.optString(it) }
            } ?: emptyList(),
        )

        val items: JSONArray = root.optJSONArray("items")
            ?: throw LoadError("index has no items array")

        val entries = ArrayList<TrackEntry>(items.length())
        var skipped = 0
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val entryKey = item.optString("entryKey")
            if (entryKey.isEmpty()) {
                // No stable key means no way to persist a selection, so the row
                // cannot be listed. Counted, never silently dropped.
                skipped++
                continue
            }

            val internalName = item.optString("internalName")
            val category = TrackCategory.fromIndexName(item.optString("class"))
            val digest = item.optString("assetSha256")
            val codecKind = if (digest.isEmpty()) null else codecKindBySha256(digest)
            val verdict = Playability.evaluate(codecKind)

            entries += TrackEntry(
                entryKey = entryKey,
                displayName = internalName.ifEmpty { entryKey },
                category = category,
                playable = verdict.playable,
                blockedReason = verdict.reason,
            )
        }

        if (status.entryCount != 0 && status.entryCount != items.length()) {
            throw LoadError(
                "index declares ${status.entryCount} entries but carries ${items.length()}",
            )
        }

        return Loaded(status = status, entries = entries, skippedCount = skipped)
    }
}
