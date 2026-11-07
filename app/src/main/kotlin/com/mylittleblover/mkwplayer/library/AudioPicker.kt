package com.mylittleblover.mkwplayer.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * A user-chosen audio file, already copied someplace the decoder can read.
 *
 * The engine decodes from bytes, so a picker result has to be materialised. Doing
 * it through a cache file rather than a byte array keeps a 3 MB track off the heap
 * and lets the file be re-read if the graph needs to rebuild.
 */
internal data class PickedFile(
    /** Stable key for persistence: the digest of the copied bytes. */
    val key: String,
    /** Name shown to the user, from the provider's display name. */
    val displayName: String,
    /** Cache file holding the bytes. */
    val file: File,
) {
    val sizeBytes: Long get() = file.length()

    fun readBytes(): ByteArray = file.readBytes()
}

/**
 * Copies a picked document into app storage.
 *
 * Deliberately not a streaming decode: the engine's prepared path wants the whole
 * encoded file, and a 3 MB track is not worth a partial-read abstraction. What does
 * matter is that the copy is verified by size and digest, so a truncated transfer
 * fails here instead of inside the decoder.
 */
internal object AudioPicker {

    private const val CACHE_DIR = "picked-audio"

    /** Refuses files larger than this: the engine's own encoded limit is 64 MiB. */
    const val MAX_BYTES = 64L * 1024 * 1024

    sealed interface Result {
        data class Ok(val picked: PickedFile) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Copies [uri] into the cache directory.
     *
     * @param resolver the caller supplies the accessor so this stays testable
     */
    fun copy(
        context: Context,
        uri: Uri,
        resolver: (Uri) -> ByteArray? = { readBytes(context, it) },
        displayNameOf: (Uri) -> String? = { queryDisplayName(context, it) },
    ): Result {
        val name = displayNameOf(uri) ?: uri.lastPathSegment ?: "audio"
        if (!name.endsWith(".bwav", ignoreCase = true) &&
            !name.endsWith(".wav", ignoreCase = true)
        ) {
            return Result.Rejected("Only .bwav and .wav files are supported; chose '$name'.")
        }

        val bytes = resolver(uri) ?: return Result.Rejected("The file could not be read.")
        if (bytes.isEmpty()) return Result.Rejected("The file is empty.")
        if (bytes.size > MAX_BYTES) {
            return Result.Rejected("The file is too large (${bytes.size} bytes).")
        }

        val directory = File(context.cacheDir, CACHE_DIR)
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.Rejected("Could not create a cache directory.")
        }

        val digest = sha256Hex(bytes)
        val target = File(directory, digest)
        return try {
            // Reuse an existing copy: the digest is the content, so the name is the
            // validation.
            if (!target.exists() || target.length() != bytes.size.toLong()) {
                FileOutputStream(target).use { it.write(bytes) }
            }
            if (target.length() != bytes.size.toLong()) {
                target.delete()
                return Result.Rejected("The copy was incomplete.")
            }
            Result.Ok(PickedFile(key = digest, displayName = name, file = target))
        } catch (e: Exception) {
            target.delete()
            Result.Rejected("Could not store the file: ${e.message}")
        }
    }

    /** Removes cached copies that no key references any more. */
    fun prune(context: Context, keep: Set<String>) {
        val directory = File(context.cacheDir, CACHE_DIR)
        directory.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (e: Exception) {
        null
    }

    internal fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
