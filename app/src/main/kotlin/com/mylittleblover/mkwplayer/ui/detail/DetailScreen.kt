package com.mylittleblover.mkwplayer.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.mylittleblover.mkwplayer.ui.Tokens

/**
 * UI-03 (resource detail), UI-06 (music structure) and UI-16 (provenance) in one scroll.
 *
 * The page is a layout and nothing else: every sentence it shows was decided in `DetailStates.kt`,
 * because the failure this project keeps making is a wording decision, not a drawing one. A marker
 * that gets described as a scheduled event compiles and renders perfectly; only the state layer can
 * be tested for it.
 *
 * Two rules are enforced here rather than trusted to callers:
 *
 * - The cue warning is not dismissible. There is no close control, no collapsed state and no
 *   "don't show again": it is a persistent statement about what the numbers next to it are, and a
 *   user who hides it would be left with a column of times that looks like a schedule.
 * - The warning cannot be dropped by passing `null`. When cues are present but no warning arrived,
 *   [CUE_MARKER_WARNING] is shown instead. A screen that silently omits the disclaimer is exactly
 *   how a marker becomes a "verified exit" in a screenshot.
 *
 * `canPlay` disables the actions and explains why; it never hides the page. A resource whose audio
 * is missing still has structure, provenance and archive facts worth reading, and hiding them would
 * make the app look like it had never heard of the course.
 *
 * Internal to the module, matching the state types it takes: a public composable cannot expose
 * [DetailHeader], [CueRow], [StructureRow] or [ProvenanceBlock] while those stay internal, and
 * keeping the page module-private is what the rest of `ui` does.
 */
@Composable
internal fun DetailScreen(
    header: DetailHeader,
    cues: List<CueRow>,
    cueWarning: String?,
    structure: List<StructureRow>,
    provenance: List<ProvenanceBlock>,
    onPlay: () -> Unit,
    onInteractive: () -> Unit,
    canPlay: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Tokens.Palette.cream100)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = Tokens.Spacing.md,
                vertical = Tokens.Spacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.lg),
    ) {
        PageHeader(header)
        Actions(canPlay = canPlay, onPlay = onPlay, onInteractive = onInteractive)
        CueSection(cues = cues, cueWarning = cueWarning)
        StructureSection(rows = structure)
        ProvenanceSection(blocks = provenance)
    }
}

/** The title block. Metrics are archive counts, so they say "in the archive" rather than "in the game". */
@Composable
private fun PageHeader(header: DetailHeader) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
        Text(
            text = header.title,
            color = Tokens.Palette.ink900,
            fontSize = Tokens.Type.pageTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = header.subtitle,
            color = Tokens.Palette.navy700,
            fontSize = Tokens.Type.body,
        )
        if (header.metrics.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
                for (metric in header.metrics) {
                    Text(
                        text = metric,
                        color = Tokens.Palette.ink900,
                        fontSize = Tokens.Type.secondary,
                    )
                }
            }
        }
    }
}

/**
 * The two actions, plus the reason when they are unavailable.
 *
 * A disabled control with no explanation is indistinguishable from a broken one, so the reason is
 * always rendered beside the buttons rather than as a tooltip or a toast. Both actions are disabled
 * together because both start audio: leaving Interactive live on a resource that cannot play would
 * offer a path that fails one screen later, which reads as a different bug.
 *
 * The two buttons share the row rather than being sized to their text. At a 200% font scale a
 * fixed-width button clips the label, and a clipped label is not a control a reader can identify;
 * sharing the width lets the text wrap instead.
 */
@Composable
private fun Actions(canPlay: Boolean, onPlay: () -> Unit, onInteractive: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
            Button(
                onClick = onPlay,
                enabled = canPlay,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Tokens.Component.touchTargetMin),
            ) {
                Text(text = "Play", fontSize = Tokens.Type.body)
            }
            OutlinedButton(
                onClick = onInteractive,
                enabled = canPlay,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Tokens.Component.touchTargetMin),
            ) {
                Text(text = "Interactive", fontSize = Tokens.Type.body)
            }
        }
        if (!canPlay) {
            Text(
                text = "Playback is unavailable for this resource in this build, so Play and " +
                    "Interactive are off. The structure and provenance below are still what the " +
                    "archive and the files say.",
                color = Tokens.Palette.warningText,
                fontSize = Tokens.Type.secondary,
            )
        }
    }
}

/**
 * UI-06's cue columns, with the warning fixed above them.
 *
 * The banner is placed directly above the row card, with no collapsible state, because a cue list
 * read without it is a list of times that looks like a schedule. The fallback warning is the point
 * of this composable: `cueWarning == null` with a non-empty list means a caller forgot, and the
 * page must still not show bare times.
 */
@Composable
private fun CueSection(cues: List<CueRow>, cueWarning: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        Text(
            text = "Cue positions",
            color = Tokens.Palette.ink900,
            fontSize = Tokens.Type.sectionTitle,
            fontWeight = FontWeight.Bold,
        )

        if (cues.isEmpty()) {
            Text(
                text = "No cue positions are listed for this course in this build. That is a gap " +
                    "in what is bundled, not evidence that the course has none.",
                color = Tokens.Palette.navy700,
                fontSize = Tokens.Type.secondary,
            )
        } else {
            CueWarningBanner(text = cueWarning ?: CUE_MARKER_WARNING)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
                shape = RoundedCornerShape(Tokens.Radius.sm),
            ) {
                Column(
                    modifier = Modifier.padding(Tokens.Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                ) {
                    for (row in cues) {
                        CueRowView(row)
                    }
                }
            }
        }
    }
}

/**
 * The warning itself.
 *
 * Dark fill, light text, full width: it has to read as a warning at a glance, and it must survive
 * the cream page background. `warningText` (#8A5B00) is the text-safe variant from `Tokens`, and
 * white on it clears the pack's 4.5:1 normal-text minimum; `warning` (#F5AD43) would not.
 */
@Composable
private fun CueWarningBanner(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Tokens.Palette.warningText,
                shape = RoundedCornerShape(Tokens.Radius.sm),
            )
            .padding(Tokens.Spacing.sm),
    ) {
        Text(
            text = text,
            color = Tokens.Palette.white,
            fontSize = Tokens.Type.body,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * One cue row.
 *
 * The position comes from [CueRow.label], which already contains both the time and the marker
 * basis, and the marker badge repeats the basis as a shape rather than only as prose. The id is
 * shown separately because a marker id is an archive identifier and not a rank: two courses reuse
 * ids, so numbering rows by id would invent an order.
 */
@Composable
private fun CueRowView(row: CueRow) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.name,
                modifier = Modifier.weight(1f),
                color = Tokens.Palette.ink900,
                fontSize = Tokens.Type.body,
                fontWeight = FontWeight.Bold,
            )
            if (row.isMarkerPosition) {
                MarkerBadge()
            }
        }
        Text(
            text = row.label,
            color = Tokens.Palette.navy700,
            fontSize = Tokens.Type.secondary,
        )
        Text(
            text = "Marker id ${row.markerId}",
            color = Tokens.Palette.navy700,
            fontSize = Tokens.Type.caption,
        )
    }
}

/** The word that has to travel with a position, as a badge so it is readable without the prose. */
@Composable
private fun MarkerBadge() {
    Box(
        modifier = Modifier
            .background(
                color = Tokens.Palette.warningText,
                shape = RoundedCornerShape(Tokens.Radius.pill),
            )
            .padding(horizontal = Tokens.Spacing.xs, vertical = Tokens.Spacing.xxs),
    ) {
        Text(
            text = "MARKER",
            color = Tokens.Palette.white,
            fontSize = Tokens.Type.caption,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * UI-06's structure list, in hearing order.
 *
 * A missing duration is printed as "Duration unknown", never as 0 s. The order arrives from the
 * state layer because a caller's list order could put Main before Pre, which would misdescribe the
 * music; this composable only renders.
 */
@Composable
private fun StructureSection(rows: List<StructureRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        Text(
            text = "Music structure",
            color = Tokens.Palette.ink900,
            fontSize = Tokens.Type.sectionTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Parts are listed in the order they are heard. Durations come from the files; " +
                "the order the game switches between them is not shown here because it is not " +
                "established.",
            color = Tokens.Palette.navy700,
            fontSize = Tokens.Type.secondary,
        )

        if (rows.isEmpty()) {
            Text(
                text = "No music structure is available: no flow has been measured for this " +
                    "resource. This is missing data, not a verified single-part track.",
                color = Tokens.Palette.navy700,
                fontSize = Tokens.Type.secondary,
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
                shape = RoundedCornerShape(Tokens.Radius.sm),
            ) {
                Column(
                    modifier = Modifier.padding(Tokens.Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                ) {
                    for (row in rows) {
                        StructureRowView(row)
                    }
                }
            }
        }
    }
}

/** One part: label, measured length, and the note that says what is a choice. */
@Composable
private fun StructureRowView(row: StructureRow) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label,
                modifier = Modifier.weight(1f),
                color = Tokens.Palette.ink900,
                fontSize = Tokens.Type.body,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = secondsLabel(row.seconds),
                color = Tokens.Palette.navy700,
                fontSize = Tokens.Type.secondary,
            )
        }
        row.repeats?.let { count ->
            Text(
                text = "Repeats: $count (this app's setting)",
                color = Tokens.Palette.navy700,
                fontSize = Tokens.Type.caption,
            )
        }
        row.note?.let { note ->
            Text(
                text = note,
                color = Tokens.Palette.navy700,
                fontSize = Tokens.Type.caption,
            )
        }
    }
}

/**
 * UI-16's evidence blocks.
 *
 * Each block carries its own level and its own established/not-established state, so a chosen value
 * cannot borrow credibility from a measured one by sitting next to it. The accent for a chosen block
 * is deliberately the warning colour and not the success colour: a green edge and a green badge
 * around a project decision would undo the label in one glance, which is exactly how a provisional
 * timing starts to look verified.
 */
@Composable
private fun ProvenanceSection(blocks: List<ProvenanceBlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        Text(
            text = "Provenance",
            color = Tokens.Palette.ink900,
            fontSize = Tokens.Type.sectionTitle,
            fontWeight = FontWeight.Bold,
        )

        if (blocks.isEmpty()) {
            Text(
                text = "Provenance is not available for this resource yet. Nothing here is " +
                    "verified: missing evidence must not be read as everything having checked out.",
                color = Tokens.Palette.warningText,
                fontSize = Tokens.Type.secondary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                for (block in blocks) {
                    ProvenanceBlockView(block)
                }
            }
        }
    }
}

/** One claim. The level is a label, not a badge of approval, and the state is always spelled out. */
@Composable
private fun ProvenanceBlockView(block: ProvenanceBlock) {
    val accent = if (block.isEstablished) Tokens.Palette.successText else Tokens.Palette.warningText
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
        shape = RoundedCornerShape(Tokens.Radius.sm),
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.level,
                    color = accent,
                    fontSize = Tokens.Type.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .background(color = accent, shape = RoundedCornerShape(Tokens.Radius.pill))
                        .padding(horizontal = Tokens.Spacing.xs, vertical = Tokens.Spacing.xxs),
                ) {
                    Text(
                        text = if (block.isEstablished) "MEASURED" else "NOT ESTABLISHED",
                        color = Tokens.Palette.white,
                        fontSize = Tokens.Type.caption,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = block.statement,
                color = Tokens.Palette.ink900,
                fontSize = Tokens.Type.secondary,
            )
        }
    }
}
