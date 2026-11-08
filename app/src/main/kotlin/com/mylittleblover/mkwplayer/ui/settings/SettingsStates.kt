package com.mylittleblover.mkwplayer.ui.settings

import com.mylittleblover.mkwplayer.library.CatalogMatcher
import com.mylittleblover.mkwplayer.library.FolderAudioSource
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.ui.library.PageState

/*
 * The vocabulary behind UI-10 (settings), UI-15 (import and match repair), UI-20 (error recovery)
 * and UI-21 (first launch).
 *
 * Deliberately plain Kotlin with no Compose import. Every decision on these four pages is a claim
 * rather than a layout choice -- whether a control would do anything, whether a missing folder is a
 * measured zero, what order a repair list should take, and whether a recovery option costs the user
 * data. A screenshot cannot check any of those, so they live here and the screens only lay them out.
 *
 * Two contract rules are enforced by construction rather than by care:
 *
 *  - A setting that is not implemented cannot be turned on by any caller. [settingsRows] takes no
 *    capability flags; the rows for hardware routing, ducking and the rest are hard-disabled and
 *    always carry a reason. A hardware-routing switch that does nothing is exactly what is banned.
 *  - No recovery path starts with data loss. [recoveryFor] never emits a delete, erase or reset
 *    option at all: the contract forbids silent data loss, and a "delete everything" button offered
 *    first is how a bad day becomes an empty library.
 */

// ---------------------------------------------------------------- settings (UI-10)

/**
 * One settings row.
 *
 * [enabled] and [disabledReason] are two views of one fact and must never disagree: an enabled row
 * carries no reason, a disabled one carries one. The screen prints [disabledReason] beside the row
 * instead of grey text, because a greyed control with no explanation is indistinguishable from a
 * broken one.
 *
 * [key] is a stable identifier for list keys and tests; [title] and [detail] are for people and may
 * be reworded without breaking a caller.
 */
internal data class SettingRow(
    val key: String,
    val title: String,
    val detail: String,
    val enabled: Boolean,
    val disabledReason: String?,
)

/**
 * The settings list this build can honestly show.
 *
 * ## No store is read here
 *
 * Every fact comes in as a parameter. The function does not look at a preferences file, an asset or
 * the filesystem, so it can be exercised on the JVM and so the screen cannot disagree with the state
 * it was handed.
 *
 * ## Why almost everything is off
 *
 * This build has no settings store at all. The rows below exist so the page can say what is missing
 * rather than omit it: a missing row reads as "not a feature", while a disabled row with a reason
 * reads as "not this build". The one action that really exists is scanning the app's own stages
 * folder, which is why exactly that row is enabled.
 *
 * [stagesFolderPath] is where the app reads stage files from; it is a fact the caller knows.
 * [folderExists] only changes the wording of the scan row, never whether scanning is available:
 * scanning a folder that is not there is how the user finds out it is not there, and the scanner
 * reports that case without throwing.
 * [mainLoopRepeats] is the flow timing's repeat count, shown so the disabled row can state the value
 * that is actually used.
 */
internal fun settingsRows(
    stagesFolderPath: String,
    folderExists: Boolean,
    mainLoopRepeats: Int,
): List<SettingRow> {
    val folder = stagesFolderPath.ifBlank { "the app's stages folder" }
    val repeats = "$mainLoopRepeats time${plural(mainLoopRepeats)}"

    return listOf(
        SettingRow(
            key = KEY_MUSIC_FOLDER,
            title = "Music folder",
            detail = "Tracks are read from $folder. This build reads that one folder only.",
            enabled = false,
            disabledReason = "Not available in this build: choosing another folder is not implemented, " +
                "so the control would not change where the app reads. Put your stage files in $folder instead.",
        ),
        SettingRow(
            key = KEY_SCAN_FOLDER,
            title = "Scan the stages folder",
            detail = if (folderExists) {
                "Reads $folder again and adds any stage parts it has not seen."
            } else {
                "$folder does not exist yet. A scan reports that and adds nothing; no file is deleted."
            },
            enabled = true,
            disabledReason = null,
        ),
        SettingRow(
            key = KEY_OUTPUT_ROUTING,
            title = "Headphones and Bluetooth",
            detail = "Which device the music comes out of.",
            enabled = false,
            disabledReason = "Not available in this build: output routing is not implemented, so a " +
                "switch here would do nothing. The app plays through whatever output Android is using.",
        ),
        SettingRow(
            key = KEY_DUCKING,
            title = "Lower the volume for other apps",
            detail = "Ducking under a call or a navigation prompt.",
            enabled = false,
            disabledReason = "Not available in this build: ducking is not implemented. Other audio " +
                "pauses this app instead, so a switch here would promise something the engine cannot do.",
        ),
        SettingRow(
            key = KEY_MAIN_REPEATS,
            title = "Main loop repeats",
            detail = "This build plays the main loop $repeats before it offers an ending.",
            enabled = false,
            disabledReason = "Not available in this build: the repeat count is fixed at $repeats and " +
                "no control is wired to the flow builder, so changing it here would change nothing.",
        ),
        SettingRow(
            key = KEY_COLOUR_THEME,
            title = "Colour theme",
            detail = "Light and dark palettes for the app.",
            enabled = false,
            disabledReason = "Not available in this build: the palette is a design candidate the user " +
                "has not approved, and no second theme exists to switch to.",
        ),
        SettingRow(
            key = KEY_NOTIFICATION,
            title = "Now-playing notification",
            detail = "The media controls shown while playback continues outside the app.",
            enabled = false,
            disabledReason = "Not available in this build: background playback needs this notification " +
                "on Android, so it cannot be turned off.",
        ),
    )
}

private const val KEY_MUSIC_FOLDER = "music-folder"
private const val KEY_SCAN_FOLDER = "scan-folder"
private const val KEY_OUTPUT_ROUTING = "output-routing"
private const val KEY_DUCKING = "duck-other-audio"
private const val KEY_MAIN_REPEATS = "main-loop-repeats"
private const val KEY_COLOUR_THEME = "colour-theme"
private const val KEY_NOTIFICATION = "now-playing-notification"

// ---------------------------------------------------------------- import (UI-15)

/**
 * What an import produced, ready to show.
 *
 * [rejectedReasons] are the scanner's own lines, kept verbatim: a rewritten reason loses the file
 * name and the measurement that made the file unusable, which is the only thing that helps the user
 * fix it.
 *
 * [headline] is prose and must never be parsed back into numbers; the counts are the fields above.
 */
internal data class ImportSummary(
    val added: Int,
    val rejected: Int,
    val rejectedReasons: List<String>,
    val headline: String,
)

/**
 * Reduces a folder scan to the import page's copy.
 *
 * The missing-folder branch is the reason this function exists. A scan of a folder that is not there
 * has `found = 0`, and reporting that as "0 files imported" states a measured zero where nothing was
 * measured at all -- the folder was never read. So the headline says what to do instead of counting.
 *
 * The empty-folder branch is kept separate from it for the same reason: a folder that was read and
 * held nothing is a different fact from a folder that is absent, and one sentence cannot be true of
 * both.
 */
internal fun importSummary(scan: FolderAudioSource.Scan): ImportSummary {
    val added = scan.found.size
    val rejected = scan.rejected.size

    val headline = when {
        // Nothing was read, so no number belongs in this sentence.
        scan.missingFolder -> NO_FOLDER_HEADLINE

        // The folder was read and held nothing usable. Also no count: the count would be zero.
        added == 0 && rejected == 0 -> EMPTY_FOLDER_HEADLINE

        added == 0 ->
            "None of the $rejected file${plural(rejected)} in the folder could be used, so nothing " +
                "was imported. The reasons are listed below."

        rejected == 0 ->
            "Imported $added stage part${plural(added)}. Every file in the folder was usable."

        else ->
            "Imported $added stage part${plural(added)} and skipped $rejected file${plural(rejected)}. " +
                "The reasons are listed below."
    }

    return ImportSummary(
        added = added,
        rejected = rejected,
        rejectedReasons = scan.rejected,
        headline = headline,
    )
}

/**
 * What a missing folder says.
 *
 * Every clause is an instruction: where the files go, what to press, and that nothing was lost.
 * There is no number in it on purpose -- a zero here would be a measurement that never happened.
 */
private const val NO_FOLDER_HEADLINE =
    "There is no folder to scan: the one this app reads from does not exist yet. Put your stage " +
        "files in the app's stages folder, then scan again. Nothing has been lost."

/** What a folder that was read and held no stage parts says. */
private const val EMPTY_FOLDER_HEADLINE =
    "The folder holds no stage parts. Add the .bwav files for one stage and scan again; nothing has " +
        "been imported."

// ---------------------------------------------------------------- match repair (UI-15)

/**
 * One catalogue entry in the repair list.
 *
 * [entry] is the catalogue's own name, because that is what the user has to find a file for.
 * [status] is a word, not a colour, so the row is readable without seeing it. [detail] is the file
 * that satisfied an owned entry, the measurement that contradicted a mismatch, or the catalogue's
 * own reason a missing entry cannot be decoded; null when there is nothing to add.
 */
internal data class MatchRow(
    val entry: String,
    val status: String,
    val detail: String?,
)

/**
 * The repair list, most useful first: owned, then mismatched, then missing.
 *
 * The order is a product decision, not a sort. An owned row is the only thing on this page that is
 * already right, and a user repairing a library needs to see that their files were understood
 * before being shown a wall of gaps. Mismatched rows come next because they are nearly right -- a
 * file exists under the name, and the measurement is the only disagreement -- and they are the ones
 * a user can actually fix. Missing rows are last because there is no file to repair yet.
 *
 * Within a group the report's own order is preserved: it reflects the catalogue's order, and
 * re-sorting would make two visits to the page disagree.
 *
 * [limit] is clamped from below rather than trusted. `List.take` throws on a negative count, and a
 * settings value of -1 would then crash a page whose whole job is to be read.
 */
internal fun matchRows(report: CatalogMatcher.Report, limit: Int = 50): List<MatchRow> {
    if (limit <= 0) return emptyList()

    val ordered = report.owned + report.mismatched + report.missing
    return ordered.take(limit).map { match ->
        MatchRow(
            entry = match.entry.name,
            status = statusLabel(match.status),
            detail = when (match.status) {
                CatalogMatcher.Status.OWNED -> match.file?.fileName
                CatalogMatcher.Status.MISMATCH -> match.detail
                CatalogMatcher.Status.MISSING -> match.entry.reason
            },
        )
    }
}

/** The word a status is shown as. "Present" matches the library page's own vocabulary. */
private fun statusLabel(status: CatalogMatcher.Status): String = when (status) {
    CatalogMatcher.Status.OWNED -> "Present"
    CatalogMatcher.Status.MISMATCH -> "Mismatched"
    CatalogMatcher.Status.MISSING -> "Missing"
}

// ---------------------------------------------------------------- recovery (UI-20)

/**
 * The failures UI-20 has to recover from.
 *
 * These are problems, not page states: one of them can occur while the page is otherwise fine. The
 * screen's label for the page still comes from the shared [PageState] vocabulary through
 * [pageStateOf], so a fourth set of state names does not appear in the app.
 */
internal enum class Problem {
    /** The bundled catalogue is absent or unreadable. */
    MISSING_ASSET,

    /** The persisted library file could not be parsed. */
    UNREADABLE_LIBRARY_FILE,

    /** The folder the user chose is gone. */
    MISSING_FOLDER,

    /** The file is present but uses a codec this build does not decode. */
    UNPLAYABLE_CODEC,

    /** The headphones or Bluetooth device the music was going to disappeared. */
    OUTPUT_DEVICE_GONE,
}

/** A heading for the recovery page. Kept here so the screen holds no copy of its own. */
internal fun problemTitle(problem: Problem): String = when (problem) {
    Problem.MISSING_ASSET -> "The catalogue is missing from this build"
    Problem.UNREADABLE_LIBRARY_FILE -> "Your library file could not be read"
    Problem.MISSING_FOLDER -> "The music folder is not there"
    Problem.UNPLAYABLE_CODEC -> "This file uses a codec this build cannot decode"
    Problem.OUTPUT_DEVICE_GONE -> "The audio output device was disconnected"
}

/**
 * The shared page-state word for each problem.
 *
 * Deliberately a projection onto [PageState] rather than a new enum: the 21-page contract has one
 * state vocabulary, and a recovery page that invented "BROKEN" for the same fact would be the kind
 * of second wording this project keeps getting wrong. The labels stay the ones a user has already
 * seen on the library and search pages.
 */
internal fun pageStateOf(problem: Problem): PageState = when (problem) {
    Problem.MISSING_ASSET -> PageState.MISSING_ASSET

    // A failed parse is a failed read, and the library page already says what that means: no count
    // can be shown, and a failed read is not an empty library.
    Problem.UNREADABLE_LIBRARY_FILE -> PageState.ERROR

    // REBIND_REQUIRED, not EMPTY. EMPTY is the state that says the catalogue knows N tracks and none
    // are present -- a measured zero. A folder that is gone was never measured, and the shared
    // wording for it already says the one thing that matters here: choose it again, nothing deleted.
    Problem.MISSING_FOLDER -> PageState.REBIND_REQUIRED

    // LOCKED is the page's existing word for "present but not available in this build", which is
    // exactly what an undecodable file is.
    Problem.UNPLAYABLE_CODEC -> PageState.LOCKED

    Problem.OUTPUT_DEVICE_GONE -> PageState.ERROR
}

/**
 * One thing the user can do about a [Problem].
 *
 * [canDo] is the honest answer to "would pressing this work in this build", not an optimistic one:
 * an option this build cannot perform is still listed so the user knows the route exists, but it is
 * marked and its [reason] says why. A greyed button with no explanation is a dead end.
 *
 * When [canDo] is true, [reason] is null: an applicable option needs no excuse.
 */
internal data class RecoveryOption(
    val label: String,
    val canDo: Boolean,
    val reason: String?,
)

/**
 * What UI-20 offers for each problem.
 *
 * ## Ordering
 *
 * The first option is always something the user can actually do, and the least costly one: look at
 * the data that was set aside, choose the folder again, play a different track. Nothing here deletes,
 * erases, resets or uninstalls anything. That is a hard rule, not a preference -- the contract
 * forbids silent data loss, and a destructive action offered first is how a recovery screen turns a
 * bad afternoon into an empty library. Where a destructive action might help (starting over), it is
 * not offered at all: the files are kept and the user can move them aside themselves.
 *
 * ## Options that cannot be applied
 *
 * Some of these are things a future build could do (reload the catalogue at runtime, read the
 * quarantined library copy back, switch output automatically). They are listed with `canDo = false`
 * so the page can say "this is not possible yet" instead of hiding the route entirely, and each
 * carries the concrete reason.
 */
internal fun recoveryFor(problem: Problem): List<RecoveryOption> = when (problem) {
    Problem.MISSING_ASSET -> listOf(
        RecoveryOption(
            label = "Install this build again to restore the catalogue",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Play the tracks you already have from the stages folder",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Reload the catalogue without restarting the app",
            canDo = false,
            reason = "Not implemented: the catalogue is read once when the app starts, so it cannot " +
                "be reloaded while the app is running.",
        ),
    )

    Problem.UNREADABLE_LIBRARY_FILE -> listOf(
        RecoveryOption(
            label = "Look for the copy that was set aside",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Scan your music folder again to rebuild the list",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Read the set-aside copy back automatically",
            canDo = false,
            reason = "Not implemented: the unreadable file is kept beside the library as a .corrupt " +
                "copy, and this build has no step that merges it back.",
        ),
    )

    Problem.MISSING_FOLDER -> listOf(
        RecoveryOption(
            label = "Choose the folder your stage files are in",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Put the files in the app's stages folder and scan it",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Keep using the folder from last time",
            canDo = false,
            reason = "Not implemented: this build does not keep a folder that is gone. The files have " +
                "to be chosen again.",
        ),
    )

    Problem.UNPLAYABLE_CODEC -> listOf(
        RecoveryOption(
            label = "Play a track this build can decode",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Keep the file listed so the reason stays visible",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Convert the file to a codec this build decodes",
            canDo = false,
            reason = "Not offered: this build has no converter, and converting would also change the " +
                "audio.",
        ),
        RecoveryOption(
            label = "Decode the file anyway",
            canDo = false,
            reason = "Not possible: the codec (kind 2) has no established decoder, so playing it " +
                "would be a guess rather than playback.",
        ),
    )

    Problem.OUTPUT_DEVICE_GONE -> listOf(
        RecoveryOption(
            label = "Reconnect the device, then press play again",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Play again through the speaker the phone is using",
            canDo = true,
            reason = null,
        ),
        RecoveryOption(
            label = "Follow the output device automatically",
            canDo = false,
            reason = "Not implemented: when the output disappears this build pauses instead of " +
                "guessing where to send the sound.",
        ),
    )
}

// ---------------------------------------------------------------- first launch (UI-21)

/**
 * One step of the first-run page.
 *
 * [done] reports a fact the caller already knows (a catalogue is available, a folder was chosen,
 * tracks were found), never a guess. The first step is a statement of what this app does not
 * contain, so it is always done and must never block the user.
 */
internal data class FirstRunStep(
    val title: String,
    val body: String,
    val done: Boolean,
)

/**
 * The first-run page.
 *
 * The first step is fixed and is the one the contract insists on: this app bundles no music and
 * pre-installs none. Presenting a full soundtrack as included is the claim that would be worst to
 * get wrong, because a user would install the app expecting music and find silence.
 *
 * The remaining steps follow what the app actually needs: a catalogue to name tracks, a folder to
 * read them from, and files the user supplies. A missing catalogue is stated as a fact about the
 * build rather than as a user error -- nothing the user does makes an absent asset appear.
 */
internal fun firstRunSteps(
    hasCatalogue: Boolean,
    hasFolder: Boolean,
    hasTracks: Boolean,
): List<FirstRunStep> = listOf(
    FirstRunStep(
        title = "This app brings no music",
        body = "No music is bundled with this app and none is pre-installed. Every track you hear " +
            "is a file you add yourself.",
        // A fact about the build, not a task: it can never be "not done".
        done = true,
    ),
    FirstRunStep(
        title = "A catalogue says what exists",
        body = if (hasCatalogue) {
            "The catalogue is available, so the app can name tracks and say which ones it can decode."
        } else {
            "No catalogue is available in this build, so the app cannot name tracks. The library " +
                "stays empty until a build with the catalogue is installed."
        },
        done = hasCatalogue,
    ),
    FirstRunStep(
        title = "Choose the folder with your stage files",
        body = "Pick one folder. A stage is up to five sibling files sharing a name, from the prelude " +
            "to the outro.",
        done = hasFolder,
    ),
    FirstRunStep(
        title = "Add the files you have",
        body = "Scan the folder and this app lists the parts it found. Files are read where they are; " +
            "nothing is copied or downloaded.",
        done = hasTracks,
    ),
)

// ---------------------------------------------------------------- rights (UI-10)

/**
 * The provenance statement the settings page must show.
 *
 * Returned verbatim from [CupArt.NOTICE], with no wrapping, prefix or paraphrase. "Attribution
 * shown" and "rights cleared" are different claims, and a reworded notice is a different legal
 * claim; a test pins the two strings together.
 */
internal fun rightsNotice(): String = CupArt.NOTICE

// ---------------------------------------------------------------- helpers

/** English plural suffix for a count, so `1 part` and `2 parts` are both right. */
private fun plural(count: Int): String = if (count == 1) "" else "s"
