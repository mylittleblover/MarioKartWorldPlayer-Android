package com.mylittleblover.mkwplayer.pack

/**
 * Whether an archive member may be written to disk.
 *
 * This is the only thing standing between a pack and the user's storage. A ZIP member named
 * `../../files/library.json` is a perfectly legal ZIP entry, and a reader that joins it to a staging path
 * writes outside the staging directory. The defence is not a sanitised copy of the name: it is a refusal,
 * because a pack that needs `..` is not a pack.
 *
 * The layout is exactly one level deep, which makes the rule short enough to state completely:
 *
 * ```
 * manifest.json
 * checksums.json
 * catalog.json
 * audio/<sha256>
 * music-graphs/<trackKey>.json
 * markers/<stageCode>.json
 * routes/<routeId>.json
 * artwork/<sha256>.<ext>
 * ```
 *
 * Anything else is refused even when it is harmless, because "unknown extra members" is how a pack starts
 * carrying payloads nothing validates.
 */
internal object PackMember {

    /** The three root files the format names. */
    private val ROOT_FILES = setOf("manifest.json", "checksums.json", "catalog.json")

    /** The directories a member may live in, in the order the format lists them. */
    val DIRECTORIES: List<String> = listOf("audio", "music-graphs", "markers", "routes", "artwork")

    /** Why a member is refused, or null when it may be written. */
    sealed interface Verdict {
        /** The member is allowed. [maxBytes] is 0 when the format sets no ceiling. */
        data class Allowed(val directory: String?) : Verdict

        /** The member may not be written. [reason] names the problem in words a user could act on. */
        data class Refused(val reason: String) : Verdict
    }

    /**
     * Checks one member name.
     *
     * Deliberately does not consult the manifest: a name is legal or not on its own, and a check that
     * depended on the document would let a document authorise a path that leaves the staging directory.
     */
    fun check(name: String): Verdict {
        if (name.isEmpty()) {
            return Verdict.Refused("The archive has a member with an empty name.")
        }

        // Windows separators are not path separators on this platform, but a pack written on Windows might
        // carry them, and a reader on a platform that treats them as separators would then escape.
        if (name.contains('\\')) {
            return Verdict.Refused(
                "Archive member '$name' contains a backslash. A pack uses forward slashes only.",
            )
        }

        // Absolute paths, drive letters and UNC prefixes.
        if (name.startsWith("/") || name.startsWith("\\\\")) {
            return Verdict.Refused("Archive member '$name' is an absolute path.")
        }
        if (name.length >= 2 && name[1] == ':' && name[0].isLetter()) {
            return Verdict.Refused("Archive member '$name' starts with a drive letter.")
        }

        // The rule that actually matters. Checked on the raw name and on every segment, so `a/../b` and
        // `..` and `./x` are all caught.
        val segments = name.split('/')
        if (segments.any { it == ".." || it == "." || it.isEmpty() }) {
            return Verdict.Refused(
                "Archive member '$name' contains a relative path segment. A pack may not reach outside " +
                    "its own directory.",
            )
        }

        if (segments.size == 1) {
            return if (name in ROOT_FILES) {
                Verdict.Allowed(directory = null)
            } else {
                Verdict.Refused(
                    "Archive member '$name' is at the archive root but is not one of $ROOT_FILES. " +
                        "A pack may not carry files nothing validates.",
                )
            }
        }

        // Exactly one level deep: `audio/x` is legal, `audio/x/y` is not.
        if (segments.size > 2) {
            return Verdict.Refused(
                "Archive member '$name' is nested more than one level deep. A pack's layout is exactly " +
                    "one directory.",
            )
        }

        val directory = segments[0]
        if (directory !in DIRECTORIES) {
            return Verdict.Refused(
                "Archive member '$name' is in '$directory', which is not one of $DIRECTORIES.",
            )
        }

        val leaf = segments[1]
        if (leaf.isEmpty()) {
            return Verdict.Refused("Archive member '$name' has a directory but no file name.")
        }

        return when (directory) {
            "audio" -> checkAudio(leaf, name)
            "music-graphs" -> checkSuffixed(leaf, name, ".json")
            "markers" -> checkSuffixed(leaf, name, ".json")
            "routes" -> checkSuffixed(leaf, name, ".json")
            else -> Verdict.Allowed(directory = directory) // artwork: <sha256>.<ext>
        }
    }

    /** `audio/<sha256>`: 64 lowercase hex characters, no extension. */
    private fun checkAudio(leaf: String, name: String): Verdict {
        if (!isDigest(leaf)) {
            return Verdict.Refused(
                "Archive member '$name' is not named by a SHA-256 digest. Audio members are content " +
                    "addressed, so the name must be 64 lowercase hex characters with no extension.",
            )
        }
        return Verdict.Allowed(directory = "audio")
    }

    /** `<key>.json`, where the key is a file-system-safe identifier. */
    private fun checkSuffixed(leaf: String, name: String, suffix: String): Verdict {
        if (!leaf.endsWith(suffix)) {
            return Verdict.Refused("Archive member '$name' does not end in '$suffix'.")
        }
        val stem = leaf.removeSuffix(suffix)
        if (!isSafeStem(stem)) {
            return Verdict.Refused(
                "Archive member '$name' has a name that cannot be used as a file: '$stem'. " +
                    "Allowed characters are letters, digits, '_', '-' and '.'.",
            )
        }
        return Verdict.Allowed(directory = name.substringBefore('/'))
    }

    /** Characters a file name may contain. Deliberately narrower than what the filesystem allows. */
    private fun isSafeStem(stem: String): Boolean =
        stem.isNotEmpty() && stem.length <= 128 && stem.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }

    /** 64 lowercase hex characters. */
    fun isDigest(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    /**
     * Checks every member name in an archive listing and reports all the refusals at once.
     *
     * All of them rather than the first, because a pack with one bad name usually has a systematic problem
     * and a user fixing them one import at a time learns nothing about the pattern.
     */
    fun checkAll(names: List<String>): List<Verdict.Refused> =
        names.mapNotNull { check(it) as? Verdict.Refused }

    /**
     * True when the listing has a member at the root that is a manifest.
     *
     * The pack is identified by its manifest and never by its extension, so this is the check that decides
     * whether a file is a pack at all.
     */
    fun hasManifest(names: List<String>): Boolean = names.any { it == "manifest.json" }
}
