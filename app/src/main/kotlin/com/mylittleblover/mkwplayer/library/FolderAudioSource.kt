package com.mylittleblover.mkwplayer.library

import com.mylittleblover.mkwplayer.audio.BwavHeader
import java.io.File

/**
 * Reads stage files from a folder inside app storage.
 *
 * The document picker is the right way for a user to add files, but it cannot be driven by an
 * automated test: it needs real user interaction, so every claim about the flow path above it has
 * stayed unverified. This reads a directory instead, which a test can populate.
 *
 * It is also a real fallback for a user whose picker is unusable -- some file managers return URIs
 * whose names the app cannot resolve, and a file placed in the app folder always works.
 *
 * Scans recursively but shallowly: a stage's parts are siblings, so nesting is only tolerated one
 * level to keep a stray deep tree from being walked at startup.
 */
internal object FolderAudioSource {

    /** Extensions this build can read. Anything else is ignored rather than reported. */
    private val EXTENSIONS = setOf("bwav", "wav")

    /** How deep to descend. One level covers a `stages/coaster/` tree of files. */
    const val MAX_DEPTH = 2

    /** A file that was readable and turned out to be a usable part. */
    data class Found(
        val part: StageFlow.Part,
        val bytes: ByteArray,
    ) {
        // ByteArray makes the generated equals/hashCode misleading; identity is the right
        // comparison for a payload that is never serialised into a set.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** What a scan produced. */
    data class Scan(
        val found: List<Found>,
        /** Files that were skipped, with the reason. */
        val rejected: List<String>,
        /** True when the folder does not exist at all, which is not an error. */
        val missingFolder: Boolean,
    ) {
        /** Groups the parts by stage, ready for [StageFlowDiscovery]. */
        fun groups(): List<StageFlowDiscovery.Group> =
            StageFlowDiscovery.group(found.map { it.part })

        /** Bytes keyed by the logical file name, for compiling a flow. */
        fun bytesByName(): Map<String, ByteArray> = found.associate { it.part.fileName to it.bytes }
    }

    /**
     * Scans [folder] for stage parts.
     *
     * Never throws: an absent folder is an empty scan, and a file that cannot be read is listed in
     * [Scan.rejected] rather than aborting the walk. One bad file must not hide the other four.
     */
    fun scan(folder: File): Scan {
        if (!folder.exists() || !folder.isDirectory) {
            return Scan(found = emptyList(), rejected = emptyList(), missingFolder = true)
        }

        val found = mutableListOf<Found>()
        val rejected = mutableListOf<String>()

        walk(folder, depth = 0).forEach { file ->
            val extension = file.extension.lowercase()
            if (extension !in EXTENSIONS) return@forEach

            // A file whose name carries no role is a candidate main section; the reader decides.
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                rejected += "${file.name}: could not be read (${e.message})"
                return@forEach
            }

            val header = when (val read = BwavHeader.read(bytes)) {
                is BwavHeader.Result.Rejected -> {
                    rejected += "${file.name}: ${read.reason}"
                    return@forEach
                }
                is BwavHeader.Result.Ok -> read.info
            }
            if (header.codecKind != 1) {
                rejected += "${file.name}: codec ${header.codecKind} cannot be decoded"
                return@forEach
            }

            val part = StageFlowReader.readPart(file.name, bytes).getOrElse {
                rejected += it.message ?: "${file.name}: not a usable part"
                return@forEach
            }
            found += Found(part = part, bytes = bytes)
        }

        return Scan(found = found, rejected = rejected, missingFolder = false)
    }

    /** Breadth-limited walk. Symlinks are not followed: a loop would hang the scan. */
    private fun walk(folder: File, depth: Int): List<File> {
        if (depth > MAX_DEPTH) return emptyList()
        val children = folder.listFiles() ?: return emptyList()
        val files = mutableListOf<File>()
        for (child in children) {
            when {
                child.isFile -> files += child
                child.isDirectory && !isSymlink(child) -> files += walk(child, depth + 1)
            }
        }
        return files
    }

    private fun isSymlink(file: File): Boolean = try {
        java.nio.file.Files.isSymbolicLink(file.toPath())
    } catch (e: Exception) {
        // If it cannot be determined, treat it as one: skipping is safer than looping.
        true
    }
}
