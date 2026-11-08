package com.mylittleblover.mkwplayer.library

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Copies a picked pack into app storage so the importer can open it as a file.
 *
 * A pack is a ZIP, and the importer needs random access to its central directory, which a content URI does
 * not offer. So the document is materialised first -- but streamed, not buffered: a pack is routinely
 * larger than the app's whole memory budget, and reading ten gigabytes into a byte array would fail on the
 * device long before the importer ever saw it.
 *
 * The copy is verified by size against the provider's own reported size when it gives one, so a transfer
 * that was cut short fails here rather than inside the importer as an inexplicable archive error.
 */
internal object PackPicker {

    /** Where picked packs are staged before import. Deleted once the import finishes either way. */
    private const val CACHE_DIR = "picked-packs"

    /** Copy buffer, matching the importer's. */
    private const val BUFFER_BYTES = 64 * 1024

    sealed interface Result {
        /** [file] is a complete copy; the caller owns deleting it. */
        data class Ok(val file: File, val displayName: String, val bytes: Long) : Result

        /** [reason] says what was wrong in words the user can act on. */
        data class Rejected(val reason: String) : Result
    }

    /**
     * Copies [uri] into the cache directory.
     *
     * @param resolver the caller supplies the accessor so this stays testable without Android
     * @param displayNameOf the same, for the provider's display name
     * @param declaredSizeOf the size the provider reports, or null when it reports none
     */
    fun copy(
        context: Context,
        uri: Uri,
        resolver: (Uri) -> java.io.InputStream? = { open(context, it) },
        displayNameOf: (Uri) -> String? = { queryDisplayName(context, it) },
        declaredSizeOf: (Uri) -> Long? = { querySize(context, it) },
    ): Result {
        val name = displayNameOf(uri) ?: uri.lastPathSegment ?: "pack.mkwpack"

        val directory = File(context.cacheDir, CACHE_DIR)
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.Rejected("Could not create a staging directory for the pack.")
        }

        // A random name, so two attempts cannot collide even if the provider reports the same name twice.
        val target = File(directory, "pack-${System.nanoTime()}.mkwpack")
        val declared = declaredSizeOf(uri)

        return try {
            val source = resolver(uri) ?: return Result.Rejected("The file could not be opened.")
            val written = source.use { input ->
                target.outputStream().buffered(BUFFER_BYTES).use { sink ->
                    input.copyTo(sink, BUFFER_BYTES)
                }
            }

            if (declared != null && declared > 0 && written != declared) {
                target.delete()
                return Result.Rejected(
                    "Only $written of $declared bytes could be read. The copy is incomplete, so the pack " +
                        "was not imported.",
                )
            }
            if (written == 0L) {
                target.delete()
                return Result.Rejected("The chosen file is empty.")
            }

            Result.Ok(file = target, displayName = name, bytes = written)
        } catch (error: Exception) {
            target.delete()
            Result.Rejected("Could not store the pack: ${error.message ?: "unknown reason"}")
        }
    }

    /** Removes every staged copy. Called after an import, successful or not. */
    fun clear(context: Context) {
        File(context.cacheDir, CACHE_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun open(context: Context, uri: Uri): java.io.InputStream? = try {
        context.contentResolver.openInputStream(uri)
    } catch (error: Exception) {
        null
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    } catch (error: Exception) {
        null
    }

    private fun querySize(context: Context, uri: Uri): Long? = try {
        context.contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    null
                }
            }
    } catch (error: Exception) {
        null
    }

    /** Writes bytes to a staging file. Separated out so a test can exercise [copy]'s checks. */
    internal fun writeTo(target: File, input: java.io.InputStream): Long {
        var total = 0L
        val buffer = ByteArray(BUFFER_BYTES)
        FileOutputStream(target).use { sink ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                total += read
            }
        }
        return total
    }
}
