package com.mylittleblover.mkwplayer.library

import org.json.JSONObject

/**
 * The cue positions read out of the 30-course research export.
 *
 * These are AMTA marker records: a named position inside a source file. That is L1 metadata. It does
 * **not** establish that the game schedules a transition at that position, and this app must not
 * present it as if it did -- so [provenance] travels with the data and the screen shows it.
 *
 * The distinction has a concrete consequence. If a cue is treated as a verified exit point, the app
 * can claim to reproduce the game's behaviour; if it is treated as what it is, the app offers a
 * transition the user chose at a position the archive records. Only the second is true.
 */
internal object CueCatalog {

    /** Marker provenance, matching the generator. Unknown values are rejected, not assumed. */
    enum class Provenance {
        /** Marker positions only. No claim about game scheduling. */
        L1_MARKER_ONLY,
    }

    /** One named position in a track. */
    data class Cue(
        val name: String,
        val markerId: Int,
        val frames: Long,
        val sampleRate: Int,
    ) {
        init {
            require(frames >= 0) { "cue position must not be negative, got $frames" }
            require(sampleRate > 0) { "cue rate must be positive, got $sampleRate" }
        }

        /** Position in seconds, for display only; scheduling uses [frames]. */
        fun seconds(): Double = frames.toDouble() / sampleRate
    }

    /** Everything known about one course's cues. */
    data class Course(
        val code: String,
        val displayName: String,
        val cues: List<Cue>,
        val outroCount: Int,
        /** True when the source archive carries a Bridge entry. Not a routing claim. */
        val hasBridge: Boolean,
        val hasShortPre: Boolean,
        val hasFast: Boolean,
    ) {
        init {
            require(code.isNotBlank()) { "course code must not be blank" }
            // Cues are ordered; an unordered list would let a "next cue" calculation skip one.
            require(cues.zipWithNext().all { (a, b) -> a.frames <= b.frames }) {
                "cues must be ordered by position"
            }
        }

        /** The first cue strictly after [frames], or null when there is none. */
        fun cueAfter(frames: Long): Cue? = cues.firstOrNull { it.frames > frames }

        /** The last cue at or before [frames], or null when [frames] precedes them all. */
        fun cueBefore(frames: Long): Cue? = cues.lastOrNull { it.frames <= frames }
    }

    data class Catalog(
        val provenance: Provenance,
        val provenanceNote: String,
        val courses: List<Course>,
        /** Course codes the source could not map; present so the app can say so. */
        val unmappedCourses: List<String>,
    ) {
        fun course(code: String): Course? = courses.firstOrNull { it.code == code }
        fun totalCues(): Int = courses.sumOf { it.cues.size }
        val isEmpty: Boolean get() = courses.isEmpty()

        /** The line the screen must show wherever a cue is offered. */
        fun caveat(): String = provenanceNote.ifBlank {
            "Cue positions are marker records. The game's own scheduling is not established."
        }
    }

    /** Parses the bundled asset. Returns null on anything unusable, so the caller can omit the UI. */
    fun parse(text: String): Catalog? = try {
        val root = JSONObject(text)
        val provenanceName = root.optString("provenance")
        // An unknown provenance is refused rather than defaulted: guessing would let a future asset
        // claim a stronger basis than it has.
        val provenance = Provenance.entries.firstOrNull { it.name == provenanceName } ?: return null

        val array = root.optJSONArray("courses") ?: return null
        val courses = ArrayList<Course>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val code = item.optString("code")
            if (code.isEmpty()) continue

            val cueArray = item.optJSONArray("cues") ?: continue
            val cues = ArrayList<Cue>(cueArray.length())
            for (position in 0 until cueArray.length()) {
                val cue = cueArray.optJSONObject(position) ?: continue
                val name = cue.optString("name")
                val frames = cue.optLong("frames", -1)
                val rate = cue.optInt("sampleRate", 0)
                if (name.isEmpty() || frames < 0 || rate <= 0) continue
                cues += Cue(
                    name = name,
                    markerId = cue.optInt("markerId", -1),
                    frames = frames,
                    sampleRate = rate,
                )
            }
            if (cues.isEmpty()) continue

            courses += Course(
                code = code,
                displayName = item.optString("name").ifEmpty { code },
                cues = cues.sortedBy { it.frames },
                outroCount = item.optInt("outroCount", 0),
                hasBridge = item.optBoolean("hasBridge", false),
                hasShortPre = item.optBoolean("hasShortPre", false),
                hasFast = item.optBoolean("hasFast", false),
            )
        }

        val unmapped = ArrayList<String>()
        root.optJSONArray("unmappedCourses")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotEmpty() }?.let { unmapped += it }
            }
        }

        Catalog(
            provenance = provenance,
            provenanceNote = root.optString("provenanceNote"),
            courses = courses,
            unmappedCourses = unmapped,
        )
    } catch (e: Exception) {
        null
    }
}
