package com.mylittleblover.mkwplayer.registry

/**
 * Stable identifiers for OST resources.
 *
 * The rule this package enforces: an ID is derived from POSITION IN THE SOURCE
 * CONTAINER, never from a display name.
 *
 * Why that matters here: real archives contain distinct entries that share one
 * asset name (one name appears in six entries of M_Bgm alone), and the mapping
 * from a marker position to an "OutroNN" name is unconfirmed -- the two
 * available analyses disagree on 127 of 165 comparable cues. Building IDs on
 * names or on Outro numbers would therefore collapse distinct resources or
 * invent a semantic that is not established.
 *
 * The source coordinate that IS established:
 *   archive sha256 + entry ordinal + entry AMTA offset + marker index
 */
data class ArchiveId(val sha256: String) {
    init {
        require(sha256.length == 64 && sha256.all { it in "0123456789abcdef" }) {
            "archive sha256 must be 64 lowercase hex characters, got '${sha256.length}' chars"
        }
    }
}

/**
 * Identity of one AMTA entry inside an archive.
 *
 * `ordinal` and `amtaOffset` must agree: they are two facts about the same
 * entry, and a mismatch means one of the readers is wrong.
 */
data class EntryId(
    val archive: ArchiveId,
    val ordinal: Int,
    val amtaOffset: Int,
) {
    init {
        require(ordinal >= 0) { "ordinal must not be negative, got $ordinal" }
        require(amtaOffset > 0) { "amtaOffset must be positive, got $amtaOffset" }
    }

    /** Opaque, stable, and safe as a map key or a persisted string. */
    fun stableKey(): String = "${archive.sha256.take(16)}:$ordinal:$amtaOffset"
}

/**
 * Identity of one marker record inside an entry.
 *
 * Deliberately does NOT carry an "OutroNN" name: that binding is unconfirmed,
 * so putting it in the identity would make the ID semver-dependent on something
 * nobody has verified.
 */
data class MarkerId(
    val entry: EntryId,
    val index: Int,
    val markerId: Int,
    val sourceSamplePosition: Long,
) {
    init {
        require(index >= 0) { "marker index must not be negative, got $index" }
        require(sourceSamplePosition >= 0) {
            "marker position must not be negative, got $sourceSamplePosition"
        }
    }

    fun stableKey(): String = "${entry.stableKey()}#$index"
}

/**
 * A logical playback resource.
 *
 * Separate from [EntryId] on purpose: one logical resource may reference several
 * entries (for example a course theme plus its Pre), and one entry may serve
 * several logical resources. The link is recorded, not merged.
 */
data class ResourceId(
    val kind: ResourceKind,
    val slug: String,
) {
    init {
        require(slug.matches(Regex("[a-z0-9][a-z0-9_.-]*"))) {
            "slug must be lowercase ascii, digits, dot, dash or underscore: '$slug'"
        }
    }

    fun stableKey(): String = "${kind.name.lowercase()}:$slug"
}

enum class ResourceKind {
    /** Course theme or one of its variants. */
    COURSE,
    /** Jukebox compilation track. */
    JUKEBOX,
    /** Title / results / goal sequence music. */
    SEQUENCE,
    /** Cutscene music. */
    CUTSCENE,
    BATTLE,
    MISSION,
    DEMO,
    SOUND_EFFECT,
    BOOT,
    UNCLASSIFIED,
}
