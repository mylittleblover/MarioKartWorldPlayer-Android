package com.mylittleblover.mkwplayer.ui.detail

import com.mylittleblover.mkwplayer.library.CueCatalog
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.player.FlowPresentation
import com.mylittleblover.mkwplayer.ui.CourseDetail
import java.util.Locale

/**
 * The decisions behind UI-03 (resource detail), UI-06 (music structure) and UI-16 (provenance).
 *
 * This file deliberately has no Compose import. What these pages say is what needs testing: a cue
 * position is a marker record, a duration was measured from a file, and a timing was chosen by this
 * project. None of that can be asserted from a composable, and no screenshot can tell a measured
 * number from an invented one.
 *
 * The recurring failure in this project is presenting a marker as a verified game event. Every
 * function below is written so it cannot make that claim: marker rows say "marker", an absent
 * measurement is null instead of zero, and a chosen value is marked as not established.
 */

/**
 * One cue position as the detail page lists it.
 *
 * [index] is the row's place in the course's ordered cue list, not the [markerId]. Two courses reuse
 * marker ids, so an id is not a rank; and a rank is not an id, so neither substitutes for the other.
 *
 * [seconds] is the archive's file position converted with the cue's own sample rate. It is what the
 * record says, not a time at which the game is known to do anything.
 */
internal data class CueRow(
    val index: Int,
    val name: String,
    val markerId: Int,
    val seconds: Double,
    val label: String,
) {

    /**
     * Always true, and deliberately not a constructor argument.
     *
     * [cueRows] can only build rows from AMTA marker records, so no caller can label a row as a
     * verified game event by passing `true` somewhere. The flag exists so a screen can draw a
     * "marker" badge without having to know where the row came from.
     */
    val isMarkerPosition: Boolean get() = true
}

/**
 * The cue list for [course], or nothing when there is no course or no cue.
 *
 * The label carries both facts at once: the position as a time, and the fact that the position is a
 * marker. It never says "exit point" or "transition", because the archive does not establish that
 * the game transitions there. A label is read far more often than the warning above it, so the
 * warning cannot be the only place the marker basis is stated.
 *
 * [CueCatalog.Cue.seconds] cannot be negative and cannot divide by zero, because [CueCatalog.Cue]
 * rejects both before this function sees it.
 */
internal fun cueRows(course: CueCatalog.Course?): List<CueRow> {
    if (course == null) return emptyList()
    return course.cues.mapIndexed { index, cue ->
        val seconds = cue.seconds()
        CueRow(
            index = index,
            name = cue.name,
            markerId = cue.markerId,
            seconds = seconds,
            label = "Marker position ${secondsLabel(seconds)}",
        )
    }
}

/**
 * One part of a music flow, as the structure page shows it.
 *
 * [role] is the stable role name for code; [label] is for people and keeps two outros apart.
 *
 * [seconds] is null when the frame count or the sample rate is unknown. It is never 0.0, because
 * 0.0 is a real length: a reader who sees one concludes the part is empty instead of unmeasured,
 * and then wonders why a silent part is in the flow at all.
 *
 * [repeats] is stated only for Main, because [StageFlow.Timing] holds only that count. Null means
 * "no count is stated", not "one pass"; the game's own pass count is not established.
 *
 * [note] carries the limit that belongs to this row: a chosen repeat count, an uncertain ending, or
 * whether a loop window was measured. Null only when there is nothing to disclose.
 */
internal data class StructureRow(
    val role: String,
    val label: String,
    val seconds: Double?,
    val repeats: Int?,
    val note: String?,
)

/**
 * Pre/Bridge/Main hearing order. Outros are appended last, as they are in the music.
 *
 * Declared before the function that reads it so the order is visible where the rule is stated.
 */
private val HEARD_ORDER = listOf(
    StageFlow.Role.PreShort,
    StageFlow.Role.Pre,
    StageFlow.Role.Bridge,
    StageFlow.Role.Main,
)

/**
 * The flow's parts in the order a listener hears them.
 *
 * The order is fixed rather than taken from [StageFlow.parts]: a caller's list order is an assembly
 * detail, and showing Main before Pre would misdescribe the music. Outros are sorted by their own
 * stable label (Outro00, Outro01, ...) for the same reason: the file list order depends on which
 * directory was scanned, and two visits to one stage must not reorder its endings.
 *
 * Roles the flow does not carry are skipped rather than filled with an empty row. An empty row
 * would read as "this stage has no bridge", which is a claim about the game, not about the flow.
 */
internal fun structureRows(flow: StageFlow?): List<StructureRow> {
    if (flow == null) return emptyList()
    val rows = ArrayList<StructureRow>(flow.parts.size)
    for (role in HEARD_ORDER) {
        val part = flow.part(role) ?: continue
        rows += structureRow(flow, part)
    }
    for (part in flow.outros().sortedBy { it.label }) rows += structureRow(flow, part)
    return rows
}

/**
 * Converts one measured part, refusing to turn an absent measurement into a number.
 *
 * [StageFlow.Part] has no invariants of its own, so frames and rate can be zero or negative when a
 * part is built by hand. A duration computed from those would be 0.0 or nonsense; null is the only
 * honest answer.
 */
private fun structureRow(flow: StageFlow, part: StageFlow.Part): StructureRow {
    val seconds = if (part.sampleRate <= 0 || part.frames <= 0) {
        null
    } else {
        part.frames.toDouble() / part.sampleRate
    }
    return StructureRow(
        role = part.role.name,
        label = labelFor(part),
        seconds = seconds,
        repeats = if (part.role == StageFlow.Role.Main) flow.timing.mainRepeats else null,
        note = noteFor(part),
    )
}

/** `Pre (short)` matches what the player calls it; outros use the part's own stable label. */
private fun labelFor(part: StageFlow.Part): String = when (part.role) {
    StageFlow.Role.PreShort -> "Pre (short)"
    StageFlow.Role.Outro -> part.label
    else -> part.role.name
}

/**
 * The per-row limit, or null when the row has nothing to qualify.
 *
 * Main comes first because its repeat count is the strongest choice on the page: it is a number the
 * user can change, so it must never look like a measured loop count.
 */
private fun noteFor(part: StageFlow.Part): String? = when {
    part.role == StageFlow.Role.Main -> "Repeat count is this app's choice, not the game's."
    part.role == StageFlow.Role.Outro -> "Which ending the game picks is not established."
    part.hasLoop -> "Loop window measured from the file."
    else -> "No loop point in the file."
}

/**
 * One claim on the provenance page (UI-16).
 *
 * [level] names the evidence level or the kind of claim: `L1` for archive markers, `L2` for measured
 * audio, the flow's own provenance name for a timing, `Scheduling` and `Caveat` for limits.
 *
 * [isEstablished] is true only for a statement that rests on something this project can point at: a
 * record in the source archive, a measurement of a file, or (for a verified flow) external evidence.
 * It is false for every choice this project made, every limit on what is known, and every caveat. A
 * choice block must say in [statement] that it is a choice; the flag is not left to imply it on its
 * own.
 */
internal data class ProvenanceBlock(
    val level: String,
    val statement: String,
    val isEstablished: Boolean,
)

/**
 * The provenance page, split into claims that were measured and claims that were chosen.
 *
 * Three rules make this page trustworthy:
 *
 * 1. A measured block says what was measured and where it came from. It does not borrow the
 *    authority of a measurement for a statement about the game.
 * 2. A chosen block is always [ProvenanceBlock.isEstablished] false and says so in words. The
 *    provisional timing label from [FlowPresentation.provenanceLabel] is used verbatim so the
 *    wording cannot drift from the player.
 * 3. The catalog's own caveat is appended as its own block rather than paraphrased or merged. A
 *    source note that is replaced by our summary is provenance lost, and the summary would then be
 *    the only thing left to check.
 *
 * Returns an empty list when nothing is known. The screen shows an explicit "not available" note in
 * that case; it must not show an empty page that looks like a clean verification.
 */
internal fun provenanceBlocks(
    course: CueCatalog.Course?,
    flow: StageFlow?,
    catalogCaveat: String?,
): List<ProvenanceBlock> {
    val blocks = ArrayList<ProvenanceBlock>(5)
    val caveat = catalogCaveat?.trim().orEmpty()

    if (course != null && course.cues.isNotEmpty()) {
        val count = course.cues.size
        val name = course.displayName.ifBlank { course.code }
        blocks += ProvenanceBlock(
            level = "L1",
            statement = "Measured: the source archive lists $count marker position" +
                "${plural(count)} for $name. Each one is a named offset in a file, not a time the " +
                "game is known to reach.",
            isEstablished = true,
        )
        blocks += ProvenanceBlock(
            level = "Scheduling",
            statement = "The game's own scheduling is not established: a marker position does not " +
                "show that the game triggers a transition there. Treating it as an exit is this " +
                "app's choice, and it is not a reproduction of the game.",
            isEstablished = false,
        )
    }

    if (flow != null) {
        val count = flow.parts.size
        blocks += ProvenanceBlock(
            level = "L2",
            statement = "Measured: frame counts, sample rates and loop windows for $count part" +
                "${plural(count)} were read from the BWAV headers. A value the header did not " +
                "state is shown as unknown rather than filled in.",
            isEstablished = true,
        )
        blocks += ProvenanceBlock(
            level = flow.provenance.name,
            statement = FlowPresentation.provenanceLabel(flow),
            isEstablished = flow.provenance == StageFlow.Provenance.VERIFIED,
        )
    }

    if (caveat.isNotEmpty()) {
        blocks += ProvenanceBlock(
            level = "Caveat",
            statement = caveat,
            isEstablished = false,
        )
    }

    return blocks
}

/**
 * The sentence that must sit above a cue list, or null when there is no cue list.
 *
 * The warning exists because the list itself cannot be trusted to say what it is: a column of
 * times with names looks like a schedule. The sentence therefore has to say both things every
 * time -- that the positions are marker records, and that the game's own scheduling is not
 * established -- no matter which screen asks.
 *
 * [CUE_MARKER_WARNING] always leads and is never replaced. An earlier draft returned the archive's
 * own note whenever that note happened to contain the right words, which meant a future or
 * stripped asset could weaken the warning by shipping wording that merely resembled it, or by
 * claiming more than the archive can support. A disclaimer any data file can rewrite is not a
 * guarantee, so the note is not used here at all: it appears as its own provenance block, where it
 * is attributed to the archive instead of standing in for the app's own warning.
 *
 * [catalog] is still needed, because two states make the warning incomplete:
 *
 * - No archive loaded means there is no source to check the positions against at all.
 * - A course the source could not map means [course]'s positions did not come from the archive,
 *   so calling them archive records would be wrong.
 *
 * The screen cannot dismiss this: it is a string, not a state.
 */
internal fun cueWarning(course: CueCatalog.Course?, catalog: CueCatalog.Catalog?): String? {
    if (course == null || course.cues.isEmpty()) return null
    val extra = when {
        catalog == null ->
            "No cue archive is loaded in this build, so these positions are checked against nothing."
        course.code in catalog.unmappedCourses ->
            "The source archive could not map this course, so these positions are not confirmed " +
                "against it."
        else -> null
    }
    return if (extra == null) CUE_MARKER_WARNING else "$CUE_MARKER_WARNING $extra"
}

/**
 * The fixed cue warning, shared with the screen so a caller cannot omit it by passing null.
 *
 * This is the one sentence in the app that says what a cue position is. Keeping it in a constant
 * means the screen's fallback and the state layer's warning cannot drift apart, and means a test
 * can assert that every warning begins with it.
 */
internal const val CUE_MARKER_WARNING: String =
    "Cue positions are marker records (L1 metadata). The game's own scheduling is not established."

/**
 * What the top of the resource detail page says (UI-03).
 *
 * [metrics] contains only facts about what the archive holds and what this build has. It never
 * contains a duration, a verified badge, or a claim about the game, because the header is read
 * before the provenance section and would be believed on its own.
 */
internal data class DetailHeader(
    val title: String,
    val subtitle: String,
    val metrics: List<String>,
)

/**
 * Builds the detail header.
 *
 * Archive facts come from [course], not from [detail]: a [CourseDetail] can be built from a display
 * name alone, in which case its `outroCount` of 0 means "unknown", not "the game has no outros".
 *
 * A zero archive count is omitted rather than shown. "0 outros" in a header reads as a fact about
 * the game, while the archive having no entry says only what this source recorded.
 */
internal fun header(detail: CourseDetail, course: CueCatalog.Course?): DetailHeader {
    val title = detail.displayName.ifBlank { detail.code }
    val subtitle = when {
        detail.cuesMissing ->
            "${detail.code}: cue positions are not available in this build."
        detail.hasCues ->
            "${detail.code}: cue positions are marker records from the source archive."
        else ->
            "${detail.code}: no cue positions are listed for this course."
    }

    val metrics = ArrayList<String>(5)
    if (detail.hasCues) {
        val count = detail.cues.size
        metrics += "$count cue position${plural(count)} (marker record${plural(count)})"
    } else if (detail.cuesMissing) {
        metrics += "Cue positions unavailable in this build"
    }

    if (course != null) {
        if (course.outroCount > 0) {
            metrics += "${course.outroCount} outro${plural(course.outroCount)} in the archive"
        }
        if (course.hasBridge) metrics += "Bridge entry in the archive"
        if (course.hasShortPre) metrics += "Short prelude in the archive"
        if (course.hasFast) metrics += "Fast variant in the archive"
    }

    return DetailHeader(title = title, subtitle = subtitle, metrics = metrics)
}

/**
 * A duration for display.
 *
 * Two decimal places, formatted with [Locale.ROOT] so the separator is always a dot. A machine
 * whose language uses a comma would otherwise show "6,04 s", and the same string in a test or a log
 * would depend on the machine that produced it.
 *
 * A null duration is stated in words. It must never be printed as "0.00 s": zero is a real length,
 * and "unknown" is the only honest rendering of a missing measurement.
 */
internal fun secondsLabel(seconds: Double?): String =
    if (seconds == null) "Duration unknown" else "%.2f s".format(Locale.ROOT, seconds)

/** English plural suffix for a count, so `1 cue` and `2 cues` are both right. */
private fun plural(count: Int): String = if (count == 1) "" else "s"
