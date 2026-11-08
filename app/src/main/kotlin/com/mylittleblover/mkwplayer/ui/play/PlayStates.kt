package com.mylittleblover.mkwplayer.ui.play

import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SeekTarget
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.player.FlowPresentation
import com.mylittleblover.mkwplayer.player.PlayerViewModel.FlowUi
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.versions.ListeningVersion
import com.mylittleblover.mkwplayer.versions.Origin
import com.mylittleblover.mkwplayer.versions.Structure

/*
 * View state for UI-04 (the full listening player) and UI-17 (the version list and editor).
 *
 * Plain Kotlin with no Compose import, for the reason the other state files give: several decisions
 * on these pages are claims, not layout. Whether a transport may offer a seek target, whether a
 * section button is a control or a no-op, whether a saved version can be played as saved, and
 * whether a version's timings are this project's guess -- none of that can be asserted from a
 * composable, and a screenshot cannot tell a disabled control from a live one whose label happened
 * to be grey.
 *
 * Every function below is one-way: it turns engine frames, a [FlowUi] or a [ListeningVersion] into
 * something a screen may print, and never back. The screen holds no rules of its own.
 */

/** The one unknown-time rendering, shared by the transport and the section buttons. */
private const val UNKNOWN_TIME = "--:--"

/**
 * The transport as the player page shows it.
 *
 * [fraction] is 0..1 for the progress bar. The three operation flags are separate rather than one
 * enum because the transport can be neither playing nor paused (stopped) and the screen still has to
 * draw the same row; a "state" string here would have to be parsed again by the layout to decide
 * which buttons are live.
 *
 * [canSeek] is not "the bar is drawn": it reports whether a seek target exists at all. A page that
 * offered a drag gesture for a track of unknown length would send a target the engine cannot
 * address, and the user would see the handle move and nothing happen.
 */
internal data class TransportRow(
    /** Elapsed time, `m:ss`, or `--:--` when the position cannot be expressed. */
    val position: String,
    /** Total time, `m:ss`, or `--:--` when the length is unknown. */
    val total: String,
    /** Progress in the played fraction of the track, 0..1. Zero when the length is unknown. */
    val fraction: Float,
    val canPause: Boolean,
    val canResume: Boolean,
    val canStop: Boolean,
    val canSeek: Boolean,
)

/**
 * Builds the transport row from engine frames.
 *
 * Times go through [EngineSnapshot.formatPosition] rather than through a second formatter here, so
 * the player page and the existing player read a position the same way and cannot drift.
 *
 * Three decisions are deliberate:
 *
 * - An unknown total is `--:--`, never `0:00`. Zero seconds is a real length, and a reader who sees
 *   one concludes the track is empty instead of unmeasured. The elaboration is not in the time
 *   field: the screen adds a sentence when [TransportRow.canSeek] is false.
 * - [TransportRow.canSeek] is exactly `totalFrames > 0`, which is the same precondition
 *   [SeekTarget.fractionToFrame] and [SeekTarget.isAddressable] already refuse. Computing it from
 *   the formatted string would parse it back; guessing it from the sample rate would offer a target
 *   that does not exist.
 * - `canStop` is true when the transport is playing *or* paused, because both are states with audio
 *   to stop. A stopped transport offers nothing, which is what keeps Stop from looking like a
 *   control that resets a page it never started.
 *
 * A negative or absurd position is left to [EngineSnapshot.formatPosition], which reports `--:--`
 * rather than clamping it into a plausible-looking time.
 */
internal fun transportFor(
    positionFrames: Long,
    totalFrames: Long,
    sampleRate: Int,
    playing: Boolean,
    paused: Boolean,
): TransportRow = TransportRow(
    position = EngineSnapshot.formatPosition(positionFrames, sampleRate),
    total = totalLabel(totalFrames, sampleRate),
    fraction = SeekTarget.frameToFraction(positionFrames, totalFrames),
    canPause = playing,
    canResume = paused,
    canStop = playing || paused,
    canSeek = totalFrames > 0,
)

/**
 * The total time, or the unknown marker when no length is known.
 *
 * [EngineSnapshot.formatPosition] renders zero frames as `0:00` when the rate is usable, which is the
 * right answer for a known position and the wrong one for a track whose length has not been
 * measured. The check therefore happens before the formatter, not after it.
 */
private fun totalLabel(totalFrames: Long, sampleRate: Int): String =
    if (totalFrames <= 0) UNKNOWN_TIME else EngineSnapshot.formatPosition(totalFrames, sampleRate)

/**
 * One requestable section of a flow.
 *
 * [active] and [enabled] are not opposites in general -- a broken flow could disable everything --
 * but [sectionButtons] makes them opposites for now: the playing section is the one control that
 * would be a no-op if it stayed live.
 */
internal data class SectionButton(
    /** Segment index the engine addresses. */
    val segment: Int,
    /** Short label, e.g. `Pre`, `Bridge`, `Outro 01`. */
    val label: String,
    /** Measured length as `m:ss`, or `--:--` when the rate or frame count is unusable. */
    val seconds: String,
    /** True for the section currently being heard. */
    val active: Boolean,
    /** False when pressing the button would do nothing the user could see. */
    val enabled: Boolean,
)

/**
 * The section buttons for the open flow.
 *
 * Order is exactly the flow's own order, which [FlowPresentation.sections] has already put into
 * hearing order (preludes, bridge, main, outros). Re-sorting here -- say, alphabetically -- would
 * present a different structure from the one that will be heard, and the flow list is the only
 * place that order is decided.
 *
 * The active section is disabled rather than hidden or silently re-requested. Re-requesting it is a
 * no-op in the engine, so leaving the button live would make the page accept a press and then do
 * nothing, which reads as a broken control. Hiding it would make the section list look like the
 * flow lacks the part that is playing.
 *
 * Lengths are computed from the measured frames and rate instead of read from
 * [FlowPresentation.Section.seconds]. That convenience accessor folds an unusable sample rate into
 * `0`, and a section button reading `0 s` states an empty part where the truth is an unmeasured one.
 * The frames are the measurement, so they are what this page trusts.
 *
 * A null flow (single-track mode, or nothing open yet) has no sections to request and produces an
 * empty list.
 */
internal fun sectionButtons(flow: FlowUi?): List<SectionButton> {
    if (flow == null) return emptyList()
    return flow.sections.map { section ->
        val active = section.segment == flow.activeSegment
        SectionButton(
            segment = section.segment,
            label = section.label,
            seconds = sectionSeconds(section.frames, section.sampleRate),
            active = active,
            enabled = !active,
        )
    }
}

/** `m:ss` for a measured length, and the unknown marker when either half of the measurement is unusable. */
private fun sectionSeconds(frames: Long, sampleRate: Int): String =
    if (frames <= 0 || sampleRate <= 0) UNKNOWN_TIME else EngineSnapshot.formatPosition(frames, sampleRate)

/**
 * One saved version as UI-17 lists it.
 *
 * [broken] is true when at least one asset the version names is absent, and [missingCount] is how
 * many. Both are carried because they answer different questions: the flag decides whether Play is
 * offered at all, and the count is the sentence that tells the user what is wrong. A row that said
 * only "unavailable" would leave the user with a saved version that looks arbitrarily refused.
 */
internal data class VersionRow(
    /** [ListeningVersion.id] in its stored, opaque string form. */
    val id: String,
    val name: String,
    /** Single file or a multi-file arrangement, in words. */
    val structureLabel: String,
    /** Authored here, or brought back from a snapshot. */
    val originLabel: String,
    /** True when this version cannot be played as saved. */
    val broken: Boolean,
    /** How many of the version's assets are absent. Zero exactly when [broken] is false. */
    val missingCount: Int,
    /** True for the version the caller currently has selected. */
    val selected: Boolean,
)

/**
 * Builds the saved-version list.
 *
 * Order is the library's saved order, never sorted. [ListeningVersion] and `VersionLibrary` record
 * that the user established that order by saving, and a list that reshuffles between visits reads
 * as the app forgetting something.
 *
 * Brokenness comes from [ListeningVersion.missing], the same call the library's own `broken` report
 * uses. Counting differently here -- for example only checking the primary asset -- would call a
 * five-part version playable while four of its files are gone, and the listen would stop at the
 * first missing part while the page still called it ready.
 *
 * A broken version is reported, not dropped: the missing file may be something the user can put
 * back, and a version that vanished from the list would look deleted. The row is marked so the
 * screen can refuse to start a shorter listen silently, which is the whole reason [missing] exists.
 *
 * [selectedId] is compared against the version's own id value. An id that matches nothing simply
 * marks nothing; a selection that no longer resolves is the caller's stale state, and inventing a
 * fallback selection here would hide it.
 */
internal fun versionRows(
    versions: List<ListeningVersion>,
    present: Set<AudioAssetId>,
    selectedId: String?,
): List<VersionRow> = versions.map { version ->
    val missing = version.missing(present)
    VersionRow(
        id = version.id.value,
        name = version.name,
        structureLabel = structureLabel(version),
        originLabel = originLabel(version),
        broken = missing.isNotEmpty(),
        missingCount = missing.size,
        selected = version.id.value == selectedId,
    )
}

/**
 * The structure in words.
 *
 * [Structure] is derived from the asset count rather than stored, so this label cannot disagree with
 * the content it describes. The section count is included for the structured case because "two
 * files" and "a prelude plus a main loop" are different things to a listener deciding which version
 * to open; for a linear version there is exactly one file and one section, and saying both is noise.
 */
private fun structureLabel(version: ListeningVersion): String = when (version.structure) {
    Structure.Linear -> "Single file"
    Structure.Structured ->
        "Multi-file: ${version.assets.size} files, ${version.sections.size} sections"
}

/**
 * Where the version's content came from.
 *
 * [Origin.RESTORED] is not folded into "authored". A version rolled back from a snapshot is content
 * the user discarded earlier, and presenting it as their latest choice would attribute a decision
 * they did not make to them. The label says so without claiming what the snapshot contained.
 */
private fun originLabel(version: ListeningVersion): String = when (version.origin) {
    Origin.AUTHORED -> "Authored here"
    Origin.RESTORED -> "Restored from a snapshot"
}

/**
 * The draft version name and whether it may be saved.
 *
 * [canSave] is true exactly when [problem] is null, so a save button driven by the flag can never
 * be live while the sentence beside it refuses.
 */
internal data class VersionEditor(
    /** The draft name, exactly as typed. Not trimmed or normalised: the stored name is what is shown. */
    val name: String,
    val canSave: Boolean,
    /** The specific reason saving is refused, or null when it is not. */
    val problem: String?,
)

/**
 * Decides whether a draft version may be saved, and why not.
 *
 * Each refusal names its own cause rather than one generic "cannot save". A single "this version
 * cannot be saved" would leave the user to guess which of four rules they broke, and the guess is
 * usually the wrong one: a blank name looks like a duplicate because both leave the row unchanged,
 * and a repeat count of zero looks like a missing section because both shorten the listen.
 *
 * The checks run in the order the user meets them on the page: the name field first, then its
 * uniqueness on the track, then the section order, then the repeat counts. Only the first refused
 * rule is reported, which is what makes the message actionable; listing all four would make the page
 * scold someone about fields they have not touched.
 *
 * [nameTaken] is passed in rather than computed because the collision rule belongs to
 * `VersionLibrary.hasNameCollision`, which compares normalised names per track. The caller is
 * expected to call it; reimplementing the comparison here would be a second rule that could
 * disagree with the one the save itself enforces. [repeatsOk] is passed for the same reason: the
 * repeat rules live on [ListeningVersion], whose constructor refuses a count below one on the
 * section order it can see.
 *
 * A blank check uses `isBlank`, so a name of spaces is refused: it would store as an entry with no
 * readable label, and `ListeningVersion` itself refuses it one step later with a less specific
 * error.
 */
internal fun editorFor(
    name: String,
    sections: Int,
    repeatsOk: Boolean,
    nameTaken: Boolean,
): VersionEditor {
    val problem = when {
        name.isBlank() -> "A version needs a name before it can be saved."
        nameTaken -> "Another version on this track already has this name."
        sections < 1 -> "A version needs at least one section to play."
        !repeatsOk -> "Every section needs a repeat count of at least 1; 0 would drop it from the listen."
        else -> null
    }
    return VersionEditor(name = name, canSave = problem == null, problem = problem)
}

/**
 * The one line that says what a version's timings are.
 *
 * The sentence is produced by [FlowPresentation.provenanceLabel] rather than written again here. A
 * second copy of the disclaimer is exactly how a page ends up presenting a provisional timing as
 * verified: the two strings drift the first time either is edited, and the page whose copy says
 * "verified" is the one a listener believes.
 *
 * `provenanceLabel` reads only the provenance field, so the flow value built to call it carries no
 * parts. It is never played, saved or inspected further; it exists so this page cannot state the
 * disclaimer differently from the player.
 */
internal fun provenanceLine(version: ListeningVersion): String =
    FlowPresentation.provenanceLabel(
        StageFlow(
            name = version.name,
            parts = emptyList(),
            timing = version.timing,
            provenance = version.provenance,
        ),
    )
