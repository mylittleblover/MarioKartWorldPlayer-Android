package com.mylittleblover.mkwplayer.unlock

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists Special Cup progress across app restarts.
 *
 * Two things make this more than a serialiser.
 *
 * First, a killed process cannot be mid-ceremony. If the app dies while the cup is being revealed,
 * the honest state on the next launch is "not yet watched, offered" -- not "playing", which would
 * leave the user with a ceremony that can never be finished, and not "completed", which would grant
 * the first-time congratulations for a viewing that did not happen.
 *
 * Second, the playback cursor is deliberately not restored. A restored cursor would let progress
 * accumulate from a position the user never actually reached, which is the fake-timing hole the
 * unlock rules exist to close.
 */
internal class UnlockStore(private val file: File) {

    /** What was read, and whether anything had to be corrected. */
    data class Loaded(
        val state: SpecialCupUnlock.UnlockState?,
        /** Null when nothing was wrong. */
        val problem: String? = null,
        /** True when a stale PLAYING phase was corrected on read. */
        val repairedCeremony: Boolean = false,
    )

    companion object {
        /**
         * Bumped only when a field's meaning changes, not when one is added.
         *
         * An unknown version is set aside rather than half-read: a partial unlock file could grant
         * or withhold the cup incorrectly, and a cup that appears for no reason is worse than one
         * that has to be earned again.
         */
        const val SCHEMA_VERSION = 1

        private const val ROOT_VERSION = "schemaVersion"
        private const val LISTENED = "listenedFrames"
        private const val COMPLETED = "completedStages"
        private const val ENTITLED = "entitled"
        private const val CEREMONY = "ceremony"
        private const val CONGRATULATED = "congratulated"
        private const val QUIZ_PASSED = "quizPassed"
        private const val SPECIAL_UNLOCKED = "specialUnlocked"
    }

    /**
     * Reads stored progress.
     *
     * Never throws. A missing file is "no progress", a corrupt one is quarantined and reported, and
     * an unreadable individual entry is skipped rather than discarding everything.
     */
    fun load(): Loaded {
        if (!file.exists()) return Loaded(null)
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return Loaded(null, problem = "Special Cup progress could not be read: ${e.message}")
        }
        if (text.isBlank()) return Loaded(null)

        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            quarantine()
            return Loaded(null, problem = "Special Cup progress was unreadable and has been set aside.")
        }

        val version = root.optInt(ROOT_VERSION, 0)
        if (version != SCHEMA_VERSION) {
            quarantine()
            return Loaded(
                null,
                problem = "Special Cup progress came from another version ($version) and has been set aside.",
            )
        }

        val listened = LinkedHashMap<String, Long>()
        root.optJSONObject(LISTENED)?.let { map ->
            for (stage in map.keys()) {
                val frames = map.optLong(stage, -1)
                if (frames >= 0) listened[stage] = frames
            }
        }

        val completed = LinkedHashSet<String>()
        root.optJSONArray(COMPLETED)?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotEmpty() }?.let { completed += it }
            }
        }

        val stored = root.optString(CEREMONY)
        val storedPhase = SpecialCupUnlock.CeremonyPhase.entries
            .firstOrNull { it.name == stored }
            ?: SpecialCupUnlock.CeremonyPhase.NONE

        // A ceremony cannot survive the process that was playing it, so it reads back as offered.
        val repaired = storedPhase == SpecialCupUnlock.CeremonyPhase.PLAYING
        val phase = if (repaired) SpecialCupUnlock.CeremonyPhase.PENDING else storedPhase

        val entitled = root.optBoolean(ENTITLED, false)
        val quizPassed = root.optBoolean(QUIZ_PASSED, false)
        val specialUnlocked = if (root.has(SPECIAL_UNLOCKED)) {
            root.optBoolean(SPECIAL_UNLOCKED, false) || phase == SpecialCupUnlock.CeremonyPhase.COMPLETED
        } else {
            // Backward compatibility: older versions wrote `entitled = true` without a separate
            // `specialUnlocked` flag. Preserve existing unlocked users so upgrading never re-locks
            // Special Cup for them.
            entitled || phase == SpecialCupUnlock.CeremonyPhase.COMPLETED
        }

        return Loaded(
            state = SpecialCupUnlock.UnlockState(
                listenedFrames = listened,
                completedStages = completed,
                entitled = entitled || quizPassed,
                ceremony = phase,
                congratulated = root.optBoolean(CONGRATULATED, false),
                // Not restored: see the class comment.
                currentStage = null,
                lastReportedFrames = -1,
                currentTrackFrames = 0,
                currentSampleRate = 0,
                quizPassed = quizPassed,
                specialUnlocked = specialUnlocked,
            ),
            repairedCeremony = repaired,
        )
    }

    /**
     * Writes progress, atomically.
     *
     * Only the durable fields are stored. The playback cursor is intentionally omitted so a restart
     * cannot credit time the user did not hear.
     */
    fun save(state: SpecialCupUnlock.UnlockState): String? {
        val root = try {
            JSONObject().apply {
                put(ROOT_VERSION, SCHEMA_VERSION)
                put(ENTITLED, state.entitled)
                put(CEREMONY, state.ceremony.name)
                put(CONGRATULATED, state.congratulated)
                put(QUIZ_PASSED, state.quizPassed)
                put(SPECIAL_UNLOCKED, state.specialUnlocked)
                put(LISTENED, JSONObject().apply {
                    state.listenedFrames.forEach { (stage, frames) -> put(stage, frames) }
                })
                put(COMPLETED, JSONArray(state.completedStages.toList()))
            }
        } catch (e: Exception) {
            return "Special Cup progress could not be serialised: ${e.message}"
        }

        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return "The progress directory could not be created."
        }

        val temp = File(file.parentFile, "${file.name}.tmp")
        return try {
            temp.writeText(root.toString())
            // REPLACE_EXISTING again: renameTo does not overwrite on Windows, and a silently failed
            // second save is exactly how progress would go missing.
            java.nio.file.Files.move(
                temp.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            null
        } catch (e: Exception) {
            temp.delete()
            "Special Cup progress could not be written: ${e.message}"
        }
    }

    /** Clears stored Special Cup progress for developer testing resets. */
    fun reset(): String? = try {
        val temp = File(file.parentFile, "${file.name}.tmp")
        if (temp.exists()) temp.delete()
        if (file.exists() && !file.delete()) {
            "Special Cup progress could not be cleared."
        } else null
    } catch (e: Exception) {
        "Special Cup progress could not be cleared: ${e.message}"
    }

    /** Moves an unreadable file aside instead of deleting it. */
    private fun quarantine() {
        try {
            file.renameTo(File(file.parentFile, "${file.name}.corrupt"))
        } catch (_: Exception) {
            // Losing the quarantine rename is acceptable; losing the data twice is not.
        }
    }
}
