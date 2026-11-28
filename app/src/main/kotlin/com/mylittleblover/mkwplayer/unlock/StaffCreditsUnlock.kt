package com.mylittleblover.mkwplayer.unlock

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Action the playback coordinator should take when a main course completes listening (#25).
 */
internal enum class StaffCreditsTrigger {
    /** No auto-queue action needed (either < 30 courses heard or already triggered once). */
    NONE,

    /** All 30 courses just completed in linear mode: insert/queue Staff Credits next once. */
    QUEUE_NEXT_LINEAR,

    /** All 30 courses just completed in interactive mode: defer Staff Credits until interactive exit. */
    DEFER_UNTIL_INTERACTIVE_EXIT,
}

/**
 * Tracks the unlock condition and one-shot auto-queue trigger for Staff Credits (#25).
 *
 * Staff Credits unlocks ONLY once all 30 Mario Kart World main courses (including `Rainbow`) have
 * been effectively listened to. When the user transitions from 29 -> 30 courses for the first time:
 * - In linear mode, [onCourseCompleted] returns [StaffCreditsTrigger.QUEUE_NEXT_LINEAR].
 * - In interactive mode, [onCourseCompleted] returns [StaffCreditsTrigger.DEFER_UNTIL_INTERACTIVE_EXIT]
 *   and arms a one-shot deferred trigger that the caller consumes via [consumeDeferredInteractiveTrigger]
 *   when exiting interactive playback.
 *
 * [State.staffCreditsAutoQueued] is persisted so the auto-insert fires at most ONCE across the life
 * of the unlock and never spams on subsequent plays.
 */
internal class StaffCreditsUnlock(
    initialState: State = State(),
) {
    data class State(
        val completedCourses: Set<String> = emptySet(),
        val staffCreditsAutoQueued: Boolean = false,
        val deferredInteractiveTrigger: Boolean = false,
    ) {
        val canonicalCompleted: Set<String>
            get() = canonicalCompleted(completedCourses)

        val completedCount: Int
            get() = canonicalCompleted.size

        val totalRequired: Int
            get() = ALL_MAIN_COURSE_CODES.size

        val remainingCourses: Set<String>
            get() = ALL_MAIN_COURSE_CODES - canonicalCompleted

        val isUnlocked: Boolean
            get() = isUnlocked(completedCourses)
    }

    private var current: State = initialState.copy(
        completedCourses = canonicalCompleted(initialState.completedCourses),
    )

    val snapshot: State get() = current

    val isUnlocked: Boolean get() = current.isUnlocked

    val staffCreditsAutoQueued: Boolean get() = current.staffCreditsAutoQueued

    val hasDeferredInteractiveTrigger: Boolean get() = current.deferredInteractiveTrigger

    /**
     * Merges previously recorded course listens (e.g. from `UnlockStore` on startup) without firing
     * the one-shot auto-queue trigger.
     */
    fun seedCompletedCourses(listenedCourses: Set<String>): State {
        val merged = current.completedCourses + canonicalCompleted(listenedCourses)
        if (merged != current.completedCourses) {
            current = current.copy(completedCourses = merged)
        }
        return current
    }

    /**
     * Records a completed main course listen and decides whether Staff Credits should auto-queue.
     *
     * Non-main-course identifiers (extra tracks, jukebox items, unknown strings) are ignored and
     * return [StaffCreditsTrigger.NONE].
     */
    fun onCourseCompleted(stageCode: String, isInteractiveMode: Boolean): StaffCreditsTrigger {
        val canonical = resolveMainCourseCode(stageCode) ?: return StaffCreditsTrigger.NONE
        val wasUnlocked = current.isUnlocked
        val nextCompleted = current.completedCourses + canonical
        val nowUnlocked = isUnlocked(nextCompleted)

        if (!wasUnlocked && nowUnlocked && !current.staffCreditsAutoQueued) {
            return if (isInteractiveMode) {
                current = current.copy(
                    completedCourses = nextCompleted,
                    staffCreditsAutoQueued = true,
                    deferredInteractiveTrigger = true,
                )
                StaffCreditsTrigger.DEFER_UNTIL_INTERACTIVE_EXIT
            } else {
                current = current.copy(
                    completedCourses = nextCompleted,
                    staffCreditsAutoQueued = true,
                    deferredInteractiveTrigger = false,
                )
                StaffCreditsTrigger.QUEUE_NEXT_LINEAR
            }
        }

        if (nextCompleted != current.completedCourses) {
            current = current.copy(completedCourses = nextCompleted)
        }
        return StaffCreditsTrigger.NONE
    }

    /**
     * Consumes and clears a pending deferred interactive trigger when leaving interactive mode.
     * Returns `true` exactly once if Staff Credits should now be queued/played, `false` otherwise.
     */
    fun consumeDeferredInteractiveTrigger(): Boolean {
        if (!current.deferredInteractiveTrigger) return false
        current = current.copy(deferredInteractiveTrigger = false)
        return true
    }

    /** Resets Staff Credits unlock progress and trigger flags (used for developer reset). */
    fun reset(): State {
        current = State()
        return current
    }

    companion object {
        const val STAFF_CREDITS_TRACK_ID = "staff_credits"

        /** All 30 Mario Kart World main course codes (including `Rainbow`). */
        val ALL_MAIN_COURSE_CODES: Set<String> = linkedSetOf(
            "FirstCircuit",
            "WestCity",
            "Coaster",
            "DonkeyKong",
            "DesertHills",
            "ShyGuyBazaar",
            "WarioStadium",
            "AirshipFortress",
            "DKPass",
            "SnowMountain",
            "IceBuildings",
            "WarioShipyard",
            "KoopaTroopaBeach",
            "DKJungleParkway",
            "Central",
            "PeachBeach",
            "LagoonCity",
            "DinoDinoJungle",
            "StormClouds",
            "RaftingRiver",
            "Cave",
            "Cinema",
            "DinoBones",
            "MooMooFarm",
            "ChocoMountain",
            "ToadFactory",
            "Lava",
            "BigTree",
            "SFCMarioCircuit",
            "Rainbow",
        )

        /** All 29 non-Rainbow main course codes. */
        val NON_RAINBOW_MAIN_COURSE_CODES: Set<String> =
            ALL_MAIN_COURSE_CODES.filterNotTo(LinkedHashSet()) { it == "Rainbow" }

        private val canonicalLookup: Map<String, String> =
            ALL_MAIN_COURSE_CODES.associateBy { it.lowercase(Locale.ROOT) }

        /**
         * Resolves a raw stage code or file name (e.g. `BGM_Stage_Rainbow_00.bwav`, `Cinema_SFC`,
         * `SnowMountain_Pattern2`) to its canonical key in [ALL_MAIN_COURSE_CODES], or null if not
         * one of the 30 MKW main courses.
         */
        fun resolveMainCourseCode(rawStageCode: String): String? {
            if (rawStageCode.isBlank()) return null
            if (rawStageCode in ALL_MAIN_COURSE_CODES) return rawStageCode
            val normalized = SpecialCupRules.normalizeStageCode(rawStageCode)
            if (normalized.isEmpty()) return null
            return canonicalLookup[normalized.lowercase(Locale.ROOT)]
        }

        /** Normalizes [listenedCourses] into canonical keys from [ALL_MAIN_COURSE_CODES]. */
        fun canonicalCompleted(listenedCourses: Set<String>): Set<String> {
            if (listenedCourses.isEmpty()) return emptySet()
            val out = LinkedHashSet<String>(listenedCourses.size)
            for (raw in listenedCourses) {
                resolveMainCourseCode(raw)?.let { out += it }
            }
            return out
        }

        /** True ONLY when all 30 MKW main courses (including `Rainbow`) have been listened to. */
        fun isUnlocked(listenedCourses: Set<String>): Boolean =
            canonicalCompleted(listenedCourses).containsAll(ALL_MAIN_COURSE_CODES)
    }
}

/**
 * Atomic JSON persistence for [StaffCreditsUnlock.State], mirroring [UnlockStore].
 */
internal class StaffCreditsStore(private val file: File) {

    data class Loaded(
        val state: StaffCreditsUnlock.State?,
        val problem: String? = null,
    )

    companion object {
        const val SCHEMA_VERSION = 1
        private const val ROOT_VERSION = "schemaVersion"
        private const val COMPLETED = "completedCourses"
        private const val AUTO_QUEUED = "staffCreditsAutoQueued"
        private const val DEFERRED_INTERACTIVE = "deferredInteractiveTrigger"
    }

    fun load(): Loaded {
        if (!file.exists()) return Loaded(null)
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return Loaded(null, problem = "Staff Credits progress could not be read: ${e.message}")
        }
        if (text.isBlank()) return Loaded(null)

        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            quarantine()
            return Loaded(null, problem = "Staff Credits progress was unreadable and has been set aside.")
        }

        val version = root.optInt(ROOT_VERSION, 0)
        if (version != SCHEMA_VERSION) {
            quarantine()
            return Loaded(
                null,
                problem = "Staff Credits progress came from another version ($version) and has been set aside.",
            )
        }

        val completed = LinkedHashSet<String>()
        root.optJSONArray(COMPLETED)?.let { arr ->
            for (i in 0 until arr.length()) {
                val raw = arr.optString(i)
                StaffCreditsUnlock.resolveMainCourseCode(raw)?.let { completed += it }
            }
        }

        return Loaded(
            state = StaffCreditsUnlock.State(
                completedCourses = completed,
                staffCreditsAutoQueued = root.optBoolean(AUTO_QUEUED, false),
                deferredInteractiveTrigger = root.optBoolean(DEFERRED_INTERACTIVE, false),
            ),
        )
    }

    fun save(state: StaffCreditsUnlock.State): String? {
        val root = try {
            JSONObject().apply {
                put(ROOT_VERSION, SCHEMA_VERSION)
                put(COMPLETED, JSONArray(state.canonicalCompleted.toList()))
                put(AUTO_QUEUED, state.staffCreditsAutoQueued)
                put(DEFERRED_INTERACTIVE, state.deferredInteractiveTrigger)
            }
        } catch (e: Exception) {
            return "Staff Credits progress could not be serialised: ${e.message}"
        }

        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return "The Staff Credits progress directory could not be created."
        }

        val temp = File(file.parentFile, "${file.name}.tmp")
        return try {
            temp.writeText(root.toString())
            java.nio.file.Files.move(
                temp.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            null
        } catch (e: Exception) {
            temp.delete()
            "Staff Credits progress could not be written: ${e.message}"
        }
    }

    fun reset(): String? = try {
        val temp = File(file.parentFile, "${file.name}.tmp")
        if (temp.exists()) temp.delete()
        if (file.exists() && !file.delete()) {
            "Staff Credits progress could not be cleared."
        } else null
    } catch (e: Exception) {
        "Staff Credits progress could not be cleared: ${e.message}"
    }

    private fun quarantine() {
        try {
            file.renameTo(File(file.parentFile, "${file.name}.corrupt"))
        } catch (_: Exception) {
            // Keep failure non-fatal.
        }
    }
}
