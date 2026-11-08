package com.mylittleblover.mkwplayer.pack

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Installs a soundtrack pack.
 *
 * Every rule here exists because the alternative damages something the user already has.
 *
 * **The manifest is read and validated before a single audio byte is written.** A structurally invalid pack
 * must cost nothing: not time, not storage, and above all not the pack it was going to replace. A user who
 * downloads a broken 10 GB pack should learn that in a second, not forty minutes.
 *
 * **Each asset is verified against its own name while it streams.** Audio members are content addressed, so
 * the entry name is the digest and the check needs no lookup table. A member whose bytes do not hash to its
 * own name is not "slightly wrong": the whole archive is suspect, and the import stops.
 *
 * **Nothing is written where the installed pack lives until the very end.** Everything lands in a staging
 * directory with a random name, and one atomic rename promotes it. A crash mid-import leaves the previous
 * pack intact and a staging directory that can be deleted; it never leaves a mixture.
 *
 * **Streaming, not buffering.** A pack is routinely larger than the app's whole memory budget, so each
 * member is copied through a fixed buffer while being hashed.
 */
internal object PackInstaller {

    /** Copy buffer. 64 KiB: large enough that syscall overhead disappears, small enough to stay out of the way. */
    const val BUFFER_BYTES = 64 * 1024

    /** What happened. */
    sealed interface Result {
        /**
         * The pack is installed at [directory].
         *
         * [manifest] is returned because the caller needs its stages and coverage to decide what to show,
         * and re-reading it from disk would risk the two disagreeing.
         */
        data class Installed(
            val manifest: PackManifest,
            val directory: File,
            val coverage: PackCoverage.Coverage,
        ) : Result

        /**
         * The import did not happen and nothing was changed.
         *
         * [reason] is shown to the user verbatim: it names the member or the rule that failed, because "the
         * pack could not be installed" tells them nothing they can act on.
         */
        data class Refused(val reason: String) : Result
    }

    /** Progress, so a long import can say what it is doing rather than appearing frozen. */
    interface Progress {
        /** Called before each member is written. [index] is 1-based. */
        fun onMember(index: Int, total: Int, name: String)

        /** Called after the whole pack is verified, before the rename. */
        fun onVerified() {}

        companion object {
            /** Does nothing. The default for tests and for callers with no screen. */
            val NONE: Progress = object : Progress {
                override fun onMember(index: Int, total: Int, name: String) = Unit
            }
        }
    }

    /**
     * Installs [archive] under [libraryRoot].
     *
     * The layout produced is `<libraryRoot>/packs/<packId>/<packVersion>/`. The caller owns [libraryRoot];
     * this function creates the two levels below it and nothing above.
     */
    fun install(
        archive: File,
        libraryRoot: File,
        progress: Progress = Progress.NONE,
    ): Result {
        if (!archive.isFile) {
            return Result.Refused("There is no file at ${archive.name}.")
        }

        // A pack is identified by its manifest, never by its extension or by what the platform calls it.
        val zip = try {
            ZipFile(archive)
        } catch (error: IOException) {
            return Result.Refused(
                "This file could not be opened as an archive: ${error.message ?: "unknown reason"}. " +
                    "A pack is a ZIP64 file; a renamed folder or a truncated download is not.",
            )
        }

        return zip.use { open ->
            val names = open.entries().asSequence().map { it.name }.toList()

            if (!PackMember.hasManifest(names)) {
                return@use Result.Refused(PackManifest.MISSING_REASON)
            }

            // Every name is checked before any is written. A pack with one bad name usually has a
            // systematic problem, and stopping at the first would hide the pattern.
            val badNames = PackMember.checkAll(names)
            if (badNames.isNotEmpty()) {
                return@use Result.Refused(badNames.joinToString(" ") { it.reason })
            }

            // The manifest before the audio: a structurally invalid pack must cost nothing.
            val manifestText = readText(open, PackManifest.MEMBER_NAME)
                ?: return@use Result.Refused(PackManifest.MISSING_REASON)

            val parsed = when (val result = PackManifest.parse(manifestText)) {
                is PackManifest.Result.Rejected -> return@use Result.Refused(result.reason)
                is PackManifest.Result.Ok -> result.manifest
            }

            val validation = PackValidation.validate(parsed)
            if (!validation.isInstallable) {
                return@use Result.Refused(
                    validation.failures.joinToString(" ") { it.message },
                )
            }

            // Declared sizes are checked against the archive's own central directory before extracting, so a
            // pack whose manifest disagrees with its contents is refused without writing anything.
            val sizeMismatch = declaredSizeMismatch(open, parsed)
            if (sizeMismatch != null) return@use Result.Refused(sizeMismatch)

            installVerified(open, parsed, libraryRoot, progress)
        }
    }

    /** Extracts, verifies and promotes. Split out so the checks above read as checks. */
    private fun installVerified(
        zip: ZipFile,
        manifest: PackManifest,
        libraryRoot: File,
        progress: Progress,
    ): Result {
        val packsRoot = File(libraryRoot, "packs")
        val stagingRoot = File(libraryRoot, ".staging")

        // A random staging name so a previous failed attempt cannot be resumed into a half-merged state.
        val staging = File(stagingRoot, "import-${System.nanoTime()}-${(0..999).random()}")
        if (!staging.mkdirs()) {
            return Result.Refused("Could not create a staging directory under ${stagingRoot.path}.")
        }

        try {
            val audio = manifest.assets.toList()
            for ((index, asset) in audio.withIndex()) {
                val name = "audio/${asset.digest.sha256}"
                progress.onMember(index + 1, audio.size, name)

                val entry = zip.getEntry(name)
                    ?: return Result.Refused(
                        "The manifest lists an asset this archive does not contain: ${asset.digest.short()}.",
                    )

                val target = File(staging, name)
                target.parentFile?.mkdirs()

                // The digest is computed while copying, so verification costs no second read.
                val written = zip.getInputStream(entry).use { source ->
                    copyHashed(source, target)
                }

                if (written.bytes != asset.byteSize) {
                    return Result.Refused(
                        "Asset ${asset.digest.short()} is ${written.bytes} bytes but the manifest declares " +
                            "${asset.byteSize}.",
                    )
                }
                // The entry name IS the digest, so a mismatch means the bytes are not what the archive says
                // they are. Stopping is the only safe response: the whole pack is suspect.
                if (written.sha256 != asset.digest.sha256) {
                    return Result.Refused(
                        "Asset ${asset.digest.short()} does not match its own name. The archive is " +
                            "damaged or was built incorrectly.",
                    )
                }
            }

            // Nothing may be missing and nothing may be extra: an unlisted asset is content the manifest
            // cannot describe, and describing it would mean trusting a file name.
            val archiveAudio = zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("audio/") }
                .toSet()
            val declaredAudio = manifest.assets.map { "audio/${it.digest.sha256}" }.toSet()
            val extra = archiveAudio - declaredAudio
            if (extra.isNotEmpty()) {
                return Result.Refused(
                    "The archive contains ${extra.size} audio member(s) the manifest does not list, " +
                        "starting with ${extra.first()}.",
                )
            }

            // Non-audio members: copied as they are. Their integrity is `checksums.json`'s job, which cannot
            // cover itself; the format makes no claim beyond damage detection and no signature.
            for (entry in zip.entries()) {
                val name = entry.name
                if (name.startsWith("audio/")) continue
                val target = File(staging, name)
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { source ->
                    target.outputStream().use { sink -> source.copyTo(sink, BUFFER_BYTES) }
                }
            }

            progress.onVerified()

            // The rename is the commit point: within one filesystem it is atomic, so after a crash either
            // the old tree or the new one exists, never a mixture.
            val destination = File(File(packsRoot, manifest.packId), manifest.packVersion.toString())
            destination.parentFile?.mkdirs()
            if (destination.exists() && !destination.deleteRecursively()) {
                return Result.Refused(
                    "A pack at ${manifest.packId} version ${manifest.packVersion} already exists and could " +
                        "not be replaced.",
                )
            }
            if (!staging.renameTo(destination)) {
                // Fall back to a copy only when the rename crosses a filesystem, which is rare but real.
                if (!staging.copyRecursively(destination, overwrite = true)) {
                    return Result.Refused("The verified pack could not be moved into place.")
                }
                staging.deleteRecursively()
            }

            // Record what was installed, so the pack survives a restart and a saved listening version can
            // name the pack it came from.
            PackRegistry(libraryRoot).record(manifest, destination)

            return Result.Installed(
                manifest = manifest,
                directory = destination,
                coverage = PackCoverage.of(manifest, validation = null),
            )
        } catch (error: IOException) {
            return Result.Refused("Reading the pack failed: ${error.message ?: "unknown reason"}.")
        } finally {
            // On any failure the staging tree goes, and the previously installed pack is untouched.
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    /** A manifest that disagrees with its own central directory is refused before extraction. */
    private fun declaredSizeMismatch(zip: ZipFile, manifest: PackManifest): String? {
        for (asset in manifest.assets) {
            val entry = zip.getEntry("audio/${asset.digest.sha256}")
                ?: return "The manifest lists an asset this archive does not contain: " +
                    "${asset.digest.short()}."
            // STORED entries report the same size; a discrepancy here is cheaper to find than one found
            // after writing a hundred megabytes.
            if (entry.size != -1L && entry.size != asset.byteSize) {
                return "Asset ${asset.digest.short()} is ${entry.size} bytes in the archive but " +
                    "${asset.byteSize} in the manifest."
            }
        }
        return null
    }

    /** Bytes written and their digest. */
    private data class Written(val bytes: Long, val sha256: String)

    /**
     * Copies [source] to [target] while hashing it.
     *
     * One pass, because a second read of a hundred-megabyte asset is pure cost and a second read of a
     * network-backed file may not even see the same bytes.
     */
    private fun copyHashed(source: InputStream, target: File): Written {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(BUFFER_BYTES)
        target.outputStream().use { sink ->
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                sink.write(buffer, 0, read)
                total += read
            }
        }
        return Written(total, digest.digest().toHex())
    }

    /** Reads a small text member, or null when it is absent. */
    private fun readText(zip: ZipFile, name: String): String? {
        val entry = zip.getEntry(name) ?: return null
        return zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Lowercase hex, matching how the format writes digests. */
    internal fun ByteArray.toHex(): String {
        val hex = CharArray(size * 2)
        for ((index, byte) in withIndex()) {
            val value = byte.toInt() and 0xFF
            hex[index * 2] = HEX[value ushr 4]
            hex[index * 2 + 1] = HEX[value and 0x0F]
        }
        return String(hex)
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
