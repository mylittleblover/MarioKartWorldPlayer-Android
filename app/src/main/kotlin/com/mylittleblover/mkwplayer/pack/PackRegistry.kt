package com.mylittleblover.mkwplayer.pack

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What is installed.
 *
 * The pointer is the commit point of an import: until it names a pack, that pack's tree is unreferenced and
 * can be deleted. Writing it last and atomically is what makes "installed" mean something after a crash.
 *
 * Deliberately tiny. It records identity and location, never content: everything a pack contains is in its
 * own manifest, and a second copy here would eventually disagree with it.
 */
internal class PackRegistry(private val libraryRoot: File) {

    /** One installed pack. */
    data class Entry(
        val packId: String,
        val packVersion: Int,
        val name: String,
        val directory: String,
        /** True when this pack defines the Special Cup's frozen scope. */
        val definesUnlock: Boolean,
    )

    private val file: File get() = File(libraryRoot, FILE_NAME)

    /**
     * What was installed, or an empty list.
     *
     * Never throws: a corrupt pointer is reported through [loadProblem] and the packs on disk are still
     * listed from their directories, because losing the pointer should not hide every pack the user has.
     */
    fun load(): List<Entry> {
        if (!file.isFile) return emptyList()
        return try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray(ENTRIES) ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val row = array.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("packId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val version = row.optInt("packVersion", -1)
                if (version < 0) return@mapNotNull null
                Entry(
                    packId = id,
                    packVersion = version,
                    name = row.optString("name").ifBlank { id },
                    directory = row.optString("directory"),
                    definesUnlock = row.optBoolean("definesUnlock", false),
                )
            }
        } catch (error: org.json.JSONException) {
            emptyList()
        }
    }

    /** Why the pointer could not be read, or null when it was fine. */
    fun loadProblem(): String? {
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText())
            null
        } catch (error: org.json.JSONException) {
            "The record of installed packs could not be read, so only folders on disk are listed."
        }
    }

    /**
     * Records an installed pack.
     *
     * Written through a temp file and a rename, the same pattern [LibraryStore] uses: a half-written pointer
     * would parse as empty and silently orphan every pack the user has.
     */
    fun record(manifest: PackManifest, directory: File) {
        libraryRoot.mkdirs()
        val entries = load().toMutableList()
        entries.removeAll { it.packId == manifest.packId && it.packVersion == manifest.packVersion }
        entries += Entry(
            packId = manifest.packId,
            packVersion = manifest.packVersion,
            name = manifest.name,
            directory = directory.path,
            definesUnlock = manifest.unlock.frozen && manifest.unlock.stages.isNotEmpty(),
        )

        val array = JSONArray()
        for (entry in entries.sortedWith(compareBy({ it.packId }, { it.packVersion }))) {
            array.put(
                JSONObject()
                    .put("packId", entry.packId)
                    .put("packVersion", entry.packVersion)
                    .put("name", entry.name)
                    .put("directory", entry.directory)
                    .put("definesUnlock", entry.definesUnlock),
            )
        }
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(ENTRIES, array)

        // Temp then rename: a crash leaves either the old pointer or the new one.
        val temp = File(libraryRoot, "$FILE_NAME.tmp")
        temp.writeText(root.toString(2))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    /**
     * The pack that defines the Special Cup, or null.
     *
     * At most one, and chosen deterministically: the highest version of the alphabetically first pack that
     * froze a scope. Two packs claiming the cup would make eligibility depend on list order, which is the
     * kind of thing that changes silently when a pack is installed.
     */
    fun unlockPack(): Entry? = load()
        .filter { it.definesUnlock }
        .sortedWith(compareByDescending<Entry> { it.packVersion }.thenBy { it.packId })
        .firstOrNull()

    /**
     * Removes a pack's pointer entry and its tree.
     *
     * Refuses to remove the unlock pack while it is the only one, because deleting it would silently take
     * the Special Cup away from a user who had earned it.
     */
    fun remove(packId: String, packVersion: Int): Boolean {
        val entries = load().toMutableList()
        val target = entries.firstOrNull { it.packId == packId && it.packVersion == packVersion }
            ?: return false
        if (target.definesUnlock && entries.count { it.definesUnlock } == 1) return false

        entries.remove(target)
        File(target.directory).deleteRecursively()

        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject()
                    .put("packId", entry.packId)
                    .put("packVersion", entry.packVersion)
                    .put("name", entry.name)
                    .put("directory", entry.directory)
                    .put("definesUnlock", entry.definesUnlock),
            )
        }
        val root = JSONObject().put("schemaVersion", SCHEMA_VERSION).put(ENTRIES, array)
        val temp = File(libraryRoot, "$FILE_NAME.tmp")
        temp.writeText(root.toString(2))
        file.delete()
        return temp.renameTo(file)
    }

    companion object {
        const val FILE_NAME = "installed.json"
        const val SCHEMA_VERSION = 1
        private const val ENTRIES = "packs"
    }
}
