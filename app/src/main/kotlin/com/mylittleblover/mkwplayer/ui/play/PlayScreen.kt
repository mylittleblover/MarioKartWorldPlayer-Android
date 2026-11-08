package com.mylittleblover.mkwplayer.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.mylittleblover.mkwplayer.ui.Tokens

/*
 * UI-04 (the full listening player) and UI-17 (saved versions and their editor), laid out from the
 * view state in PlayStates.kt.
 *
 * These composables decide nothing they could get wrong. Which sections exist, which one is live,
 * whether a seek target exists, whether a version can be played as saved and why a save is refused
 * are all settled before the layout sees them; what is left here is where the pixels go and which
 * elements are touch targets.
 *
 * Two things the layout does own, because they are about honesty rather than measurement:
 *
 * - The provenance statement is never dropped. When the caller passes null the page shows a sentence
 *   saying the statement is missing, not a blank space: a listener who is not told what the timings
 *   are will assume they are the game's.
 * - The progress bar is not drawn when no seek target exists. A bar at 0 % of an unknown length is a
 *   track that looks empty, and a handle that moves nowhere is a control that looks broken.
 */

/**
 * The sentence used when no provenance statement arrived.
 *
 * It says the statement is missing rather than showing nothing, for the same reason `DetailScreen`
 * refuses to drop its cue warning: a page of times with no disclaimer reads as a schedule, and a
 * blank area where a disclaimer belongs reads as "there was nothing to disclose".
 */
private const val NO_PROVENANCE_STATEMENT =
    "No statement about these timings is available. Missing provenance is not evidence that the " +
        "timings were verified."

/**
 * The full listening player (UI-04).
 *
 * @param transport the elapsed and total times, the progress and which transport actions exist.
 * @param sections the flow's requestable sections, in hearing order.
 * @param title the track's display name. A blank title is drawn as "Nothing is open", because an
 *   empty heading is indistinguishable from a rendering failure.
 * @param provenance the one line saying whose choice the section timings are, or null when the
 *   caller has none; see [NO_PROVENANCE_STATEMENT].
 * @param onSection called with [SectionButton.segment] for a enabled section button.
 */
@Composable
internal fun PlayScreen(
    transport: TransportRow,
    sections: List<SectionButton>,
    title: String,
    provenance: String?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Tokens.Palette.cream100)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.lg),
    ) {
        Text(
            text = title.ifBlank { "Nothing is open" },
            color = Tokens.Palette.navy900,
            fontSize = Tokens.Type.pageTitle,
            fontWeight = FontWeight.Bold,
        )

        ProvenanceNotice(provenance = provenance)

        TransportBlock(
            transport = transport,
            onPlay = onPlay,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
        )

        SectionsBlock(sections = sections, onSection = onSection)
    }
}

/**
 * What the timings are, permanently, above the transport.
 *
 * It sits above the controls rather than below the section list because it qualifies everything
 * under it: a listener who reads the times first and the disclaimer later has already formed the
 * wrong idea about them.
 *
 * The block keeps a neutral fill for a stated provenance and switches only the text colour for a
 * missing one. A success-coloured frame here would imply approval of the numbers, which is the one
 * thing this block exists to avoid.
 */
@Composable
private fun ProvenanceNotice(provenance: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
        shape = RoundedCornerShape(Tokens.Radius.sm),
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            Text(
                text = "What these timings are",
                color = Tokens.Palette.navy700,
                fontSize = Tokens.Type.caption,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = provenance ?: NO_PROVENANCE_STATEMENT,
                color = if (provenance == null) {
                    Tokens.Palette.warningText
                } else {
                    Tokens.Palette.ink900
                },
                fontSize = Tokens.Type.secondary,
            )
        }
    }
}

/**
 * The times, the progress and the transport actions.
 *
 * The primary control is the pack's one large playback target ([Tokens.Component.mainPlaybackButton])
 * and carries whichever action is the next one: Play when the transport is idle, Pause while it is
 * playing, Resume while it is paused. Three separate buttons of the same size would leave two of them
 * disabled at all times, and a row of mostly-dead controls is harder to read than one live one.
 *
 * Stop is secondary and states, when it is off, that it applies only to something already playing or
 * paused. A disabled control with no explanation is indistinguishable from a broken one.
 */
@Composable
private fun TransportBlock(
    transport: TransportRow,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
        shape = RoundedCornerShape(Tokens.Radius.md),
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = transport.position,
                    color = Tokens.Palette.ink900,
                    fontSize = Tokens.Type.sectionTitle,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = transport.total,
                    modifier = Modifier.weight(1f),
                    color = Tokens.Palette.navy700,
                    fontSize = Tokens.Type.sectionTitle,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (transport.canSeek) {
                LinearProgressIndicator(
                    progress = { transport.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = Tokens.Palette.navy700,
                    trackColor = Tokens.Palette.cyan400,
                )
            } else {
                // No bar at all: zero progress on an unknown length is a real-looking measurement of
                // nothing, and the sentence is what turns the missing control into a stated fact.
                Text(
                    text = "The total length is not known, so there is no position to jump to. " +
                        "The bar is left out rather than drawn at 0 %.",
                    color = Tokens.Palette.warningText,
                    fontSize = Tokens.Type.secondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One target, labelled with the action it will perform. The three flags cannot
                // disagree with the label because every branch reads the same field it acts on.
                val (label, action) = when {
                    transport.canPause -> "Pause" to onPause
                    transport.canResume -> "Resume" to onResume
                    else -> "Play" to onPlay
                }
                Button(
                    onClick = action,
                    modifier = Modifier.size(Tokens.Component.mainPlaybackButton),
                    // The pill radius rather than a hand-picked circle: at the 64 dp token the two
                    // render identically, and the token keeps the shape with the rest of the pack's
                    // rounded surfaces if the size ever changes.
                    shape = RoundedCornerShape(Tokens.Radius.pill),
                    contentPadding = PaddingValues(Tokens.Spacing.xxs),
                ) {
                    // The caption size, not the body size: the pack fixes this control at the
                    // playback token, and "Resume" at body size would be clipped by a target whose
                    // width is not allowed to grow. A clipped label is not a control a reader can
                    // identify.
                    Text(
                        text = label,
                        fontSize = Tokens.Type.caption,
                        fontWeight = FontWeight.Bold,
                    )
                }

                OutlinedButton(
                    onClick = onStop,
                    enabled = transport.canStop,
                    modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
                ) {
                    Text(text = "Stop", fontSize = Tokens.Type.body)
                }
            }

            if (!transport.canStop) {
                Text(
                    text = "Stop applies once something is playing or paused.",
                    color = Tokens.Palette.navy700,
                    fontSize = Tokens.Type.secondary,
                )
            }
        }
    }
}

/**
 * The flow's sections, in hearing order, each with its measured length.
 *
 * A section that is already playing is drawn disabled. The engine treats a repeat request for the
 * current segment as a no-op, so leaving that button live would accept a press and do nothing the
 * user could see -- which reads as an unresponsive control rather than as the state it is.
 *
 * The order is the state layer's, never re-sorted here: it is the order the sections will be heard
 * in, and a layout that sorted differently would describe a different arrangement.
 */
@Composable
private fun SectionsBlock(sections: List<SectionButton>, onSection: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        Text(
            text = "Sections",
            color = Tokens.Palette.ink900,
            fontSize = Tokens.Type.sectionTitle,
            fontWeight = FontWeight.Bold,
        )

        if (sections.isEmpty()) {
            Text(
                text = "No sections are loaded, so there is nothing to jump between. An ordinary " +
                    "single-file track plays as one section.",
                color = Tokens.Palette.navy700,
                fontSize = Tokens.Type.secondary,
            )
        } else {
            for (section in sections) {
                OutlinedButton(
                    onClick = { onSection(section.segment) },
                    enabled = section.enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tokens.Component.touchTargetMin),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = section.label,
                            modifier = Modifier.weight(1f),
                            fontSize = Tokens.Type.body,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = section.seconds,
                            fontSize = Tokens.Type.secondary,
                        )
                        if (section.active) {
                            // The word, not only the dimmed label: a reader who cannot see that the
                            // button is disabled still needs to know which section is sounding.
                            Text(
                                text = "Playing",
                                color = Tokens.Palette.successText,
                                fontSize = Tokens.Type.caption,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The saved versions and the editor for the one being saved (UI-17).
 *
 * The list keeps the library's saved order and marks the selected version. A broken version cannot be
 * selected, because selecting is how a version gets played and playing a version with missing parts
 * would sound like a shorter arrangement the user never chose; the row names the number of missing
 * files instead of offering the action. Duplicating it stays available, which is the repair path.
 *
 * The draft name is drawn as text rather than as a text field. This composable receives no
 * name-change callback, so a field that could not accept typing would be a worse lie than a label;
 * the caller owns the draft and will place the real input beside it if it needs one.
 *
 * @param onSelect called with [VersionRow.id] for a playable version that is not already selected.
 * @param onDuplicate called with [VersionRow.id]; a duplicate gets a fresh identity and name from the
 *   caller, never from this screen.
 */
@Composable
internal fun VersionsScreen(
    rows: List<VersionRow>,
    editor: VersionEditor,
    onSelect: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Palette.cream100),
        contentPadding = PaddingValues(
            horizontal = Tokens.Spacing.md,
            vertical = Tokens.Spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        item(key = "versions-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
                Text(
                    text = "Listening versions",
                    color = Tokens.Palette.navy900,
                    fontSize = Tokens.Type.pageTitle,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Saved in the order you saved them. The one marked Selected is the " +
                        "version the player is using.",
                    color = Tokens.Palette.navy700,
                    fontSize = Tokens.Type.secondary,
                )
            }
        }

        if (rows.isEmpty()) {
            item(key = "versions-empty") {
                Text(
                    text = "No versions are saved for this track yet, so there is nothing to choose " +
                        "between.",
                    color = Tokens.Palette.ink900,
                    fontSize = Tokens.Type.body,
                )
            }
        }

        // No key: a caller that assembled its own rows can repeat an id, and a duplicate key is a
        // crash rather than a layout glitch. Position is enough for a flat list.
        items(rows) { row ->
            VersionCard(row = row, onSelect = onSelect, onDuplicate = onDuplicate)
        }

        item(key = "versions-editor") {
            EditorCard(editor = editor, onSave = onSave)
        }
    }
}

/**
 * One saved version.
 *
 * The selected row's Select button is disabled as well as badged. Selecting the version that is
 * already selected is the same class of no-op as requesting the section that is already playing, and
 * a live button that changes nothing is indistinguishable from a broken one.
 */
@Composable
private fun VersionCard(
    row: VersionRow,
    onSelect: (String) -> Unit,
    onDuplicate: (String) -> Unit,
) {
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
                    text = row.name,
                    modifier = Modifier.weight(1f),
                    color = Tokens.Palette.ink900,
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                )
                if (row.selected) {
                    SelectedBadge()
                }
            }

            Text(
                text = "${row.structureLabel} - ${row.originLabel}",
                color = Tokens.Palette.navy700,
                fontSize = Tokens.Type.caption,
            )

            if (row.broken) {
                // The count, not a bare "unavailable": the user has to know whether this is one file
                // they can put back or an arrangement that is gone.
                Text(
                    text = "${row.missingCount} file" +
                        "${if (row.missingCount == 1) " is" else "s are"} missing, so this version " +
                        "cannot be played as saved.",
                    color = Tokens.Palette.warningText,
                    fontSize = Tokens.Type.secondary,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.Spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onSelect(row.id) },
                    enabled = !row.broken && !row.selected,
                    modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
                ) {
                    Text(text = "Select", fontSize = Tokens.Type.body)
                }
                TextButton(
                    onClick = { onDuplicate(row.id) },
                    modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
                ) {
                    Text(text = "Duplicate", fontSize = Tokens.Type.secondary)
                }
            }
        }
    }
}

/** The word that marks the version in use. A shape as well as a colour, so it survives at a glance. */
@Composable
private fun SelectedBadge() {
    Box(
        modifier = Modifier
            .background(
                color = Tokens.Palette.navy700,
                shape = RoundedCornerShape(Tokens.Radius.pill),
            )
            .padding(horizontal = Tokens.Spacing.xs, vertical = Tokens.Spacing.xxs),
    ) {
        Text(
            text = "Selected",
            color = Tokens.Palette.white,
            fontSize = Tokens.Type.caption,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The draft version and its save control.
 *
 * The refusal is printed next to the disabled button rather than surfaced after a failed tap: the
 * reason is one of four specific ones from [editorFor], and a user who is told "cannot save" after
 * pressing has no way to tell which.
 */
@Composable
private fun EditorCard(editor: VersionEditor, onSave: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
        shape = RoundedCornerShape(Tokens.Radius.md),
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            Text(
                text = "Version name",
                color = Tokens.Palette.ink900,
                fontSize = Tokens.Type.sectionTitle,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = editor.name.ifBlank { "No name yet" },
                color = if (editor.name.isBlank()) {
                    Tokens.Palette.navy700
                } else {
                    Tokens.Palette.ink900
                },
                fontSize = Tokens.Type.body,
            )
            editor.problem?.let { problem ->
                Text(
                    text = problem,
                    color = Tokens.Palette.errorText,
                    fontSize = Tokens.Type.secondary,
                )
            }
            Button(
                onClick = onSave,
                enabled = editor.canSave,
                modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
            ) {
                Text(text = "Save version", fontSize = Tokens.Type.body)
            }
        }
    }
}
