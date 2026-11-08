package com.mylittleblover.mkwplayer.registry

/**
 * Identity for a user-supplied audio file.
 *
 * The existing [ResourceId] identifies a *catalogue* resource, derived from position in a source
 * archive. That identity is not available for a file the user picked: the app never sees the
 * archive. What it does see is the bytes, and their digest is the only honest identity -- two
 * picks of the same file are the same resource, and a re-encoded or truncated file is not.
 *
 * Names cannot be identity here. A user can rename anything, and two different stages can ship
 * files whose names collide after a language change. The digest survives both.
 */
internal data class AudioAssetId(val sha256: String) {

    init {
        require(sha256.length == 64 && sha256.all { it in "0123456789abcdef" }) {
            "asset digest must be 64 lowercase hex characters, got ${sha256.length}"
        }
    }

    /** Short form for display, long enough to stay distinct in practice. */
    fun short(): String = sha256.take(12)

    companion object {
        /** Builds an id from raw digest bytes, which is what the hashing code produces. */
        fun of(digest: ByteArray): AudioAssetId {
            require(digest.size == 32) { "digest must be 32 bytes, got ${digest.size}" }
            return AudioAssetId(digest.joinToString("") { "%02x".format(it) })
        }

        /** Parses the stored form, rejecting anything that is not a digest. */
        fun parse(value: String): AudioAssetId? =
            if (value.length == 64 && value.all { it in "0123456789abcdef" }) {
                AudioAssetId(value)
            } else {
                null
            }
    }
}

/**
 * A logical track: one thing a user listens to, as distinct from the files that make it up.
 *
 * A stage theme is five assets. Treating the five as five tracks would make the library five
 * times as long and mislabel parts as songs. This is the layer that says "these files are one
 * track", and it references assets by digest so a rename or a moved file does not break it.
 */
internal data class LogicalTrack(
    /** Stable identity, derived from the primary asset and the track kind. */
    val id: String,
    /** Name shown to the user, from the primary file's name. */
    val title: String,
    val kind: ResourceKind,
    /** Asset that plays first when the track is opened. */
    val primary: AudioAssetId,
    /** Every asset the track needs, primary included. */
    val assets: List<AudioAssetId>,
    /** True when this track is a multi-part flow rather than a single file. */
    val isFlow: Boolean,
    /** Measured total length, in frames at [sampleRate]. */
    val frames: Long,
    val sampleRate: Int,
) {
    init {
        require(assets.isNotEmpty()) { "a track needs at least one asset" }
        require(assets.contains(primary)) { "the primary asset must be among the assets" }
        require(assets.size == assets.toSet().size) { "assets must not repeat" }
        require(frames >= 0) { "frames must not be negative, got $frames" }
    }

    /** True when every asset this track needs is still present. */
    fun isResolvable(present: Set<AudioAssetId>): Boolean = present.containsAll(assets)

    /** Assets that are missing, for a repair prompt. */
    fun missing(present: Set<AudioAssetId>): List<AudioAssetId> = assets.filterNot { it in present }

    companion object {
        /**
         * Derives a track id.
         *
         * Built from the primary digest plus the kind, so the same primary under two kinds (a
         * course theme also compiled into the jukebox) stays two tracks instead of silently
         * merging.
         */
        fun idOf(kind: ResourceKind, primary: AudioAssetId): String =
            "${kind.name.lowercase()}:${primary.sha256.take(32)}"
    }
}

/**
 * Classifies a file name into a catalogue kind.
 *
 * Only what the name states. `BGM_Stage_*` is a course theme, `BGM_JukeBox_*` a jukebox track,
 * and so on. A name that matches nothing is [ResourceKind.UNCLASSIFIED] rather than guessed: a
 * wrong kind changes which list a user finds the track in.
 */
internal object TrackClassifier {

    fun classify(fileName: String): ResourceKind {
        val stem = fileName.substringBeforeLast('.').lowercase()
        return when {
            stem.contains("juke") -> ResourceKind.JUKEBOX
            stem.contains("stage_") || stem.contains("battle_") && stem.contains("stage") ->
                ResourceKind.COURSE
            stem.startsWith("bgm_battle") -> ResourceKind.BATTLE
            stem.startsWith("bgm_cutscene") || stem.contains("_cutscene") -> ResourceKind.CUTSCENE
            stem.startsWith("bgm_demo") -> ResourceKind.DEMO
            stem.startsWith("bgm_seq") -> ResourceKind.SEQUENCE
            stem.startsWith("bgm_mission") -> ResourceKind.MISSION
            stem.startsWith("bgm_boot") -> ResourceKind.BOOT
            stem.startsWith("se_") || stem.contains("_se_") -> ResourceKind.SOUND_EFFECT
            else -> ResourceKind.UNCLASSIFIED
        }
    }

    /** True when the name marks one part of a multi-part stage rather than a whole track. */
    fun isPartOfFlow(fileName: String): Boolean {
        val stem = fileName.substringBeforeLast('.').lowercase()
        return stem.contains("_pre") || stem.contains("_bridge") || stem.contains("_outro")
    }
}
