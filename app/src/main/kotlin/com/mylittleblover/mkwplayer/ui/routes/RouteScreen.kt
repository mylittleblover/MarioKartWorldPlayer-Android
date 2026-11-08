package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.Tokens

/*
 * UI-07 (world map) and UI-08 (my routes), laid out from the view state in RouteStates.kt.
 *
 * The composables decide nothing. Which pins exist, which are gated, whether the map is complete,
 * and whether a draft may be saved are all already settled by the state builders; what is left here
 * is where the pixels go and which elements are touch targets.
 */

/** Side of the map frame's visual pin dot. The touch target around it is the token minimum. */
private val PIN_DOT = 28.dp

/** The map frame's proportions. The world map has no established aspect ratio; this is layout only. */
private const val MAP_ASPECT = 1.6f

/**
 * The world map.
 *
 * Pins are placed from [MapPin.xFraction] / [MapPin.yFraction] inside the frame, both taken as 0..1
 * from the left/top. A pin without an established position is never placed: it appears below the
 * frame in the warning colour and in the list, which is also the map's accessible alternative. The
 * number of unplaced stages is stated there, so a frame holding only the placed ones cannot read as
 * the whole map.
 *
 * A pin marked [MapPin.locked] is not rendered at all, in the frame or the list: the gate removes
 * stages rather than advertising them. The `pins` state builder never produces one, but a restored
 * snapshot might.
 *
 * The frame is drawn whenever at least one pin has a position, not only when [state] is
 * [MapState.READY] or [MapState.PARTIAL]. [state] is derived from the same nodes by `mapState`, but
 * if a caller ever passes the two out of step, dropping the pins would hide stages -- the failure
 * this screen exists to prevent. The label still reports what the caller claims; only the fallback
 * sentence depends on whether anything could actually be placed.
 *
 * @param onPin called with [MapPin.id] for every rendered pin, placed or not.
 */
@Composable
internal fun MapScreen(
    pins: List<MapPin>,
    state: MapState,
    onPin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placed = pins.filter { it.hasPosition && !it.locked }
    val unplaced = pins.filter { !it.hasPosition && !it.locked }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        Text(
            text = "World map",
            fontSize = Tokens.Type.pageTitle,
            fontWeight = FontWeight.Bold,
            color = Tokens.Palette.ink900,
        )

        // Always shown, including READY: the line is the map's own statement of what it covers, and
        // a status that appears only when something is wrong is easy to misread as decoration.
        Text(
            text = state.label,
            fontSize = Tokens.Type.secondary,
            color = if (state == MapState.READY) {
                Tokens.Palette.ink900
            } else {
                Tokens.Palette.warningText
            },
        )

        if (placed.isEmpty()) {
            // The contract's map fallback wording. Saying the map cannot be drawn is what stops an
            // empty frame from reading as "no stages exist"; the list below is the actual fallback.
            Text(
                text = "The full map is not available. The stage list continues below.",
                fontSize = Tokens.Type.body,
                color = Tokens.Palette.ink900,
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(MAP_ASPECT)
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(Tokens.Palette.navy700)
                    .border(
                        width = 1.dp,
                        color = Tokens.Palette.cyan400,
                        shape = RoundedCornerShape(Tokens.Radius.md),
                    ),
            ) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight
                placed.forEach { pin ->
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (availableWidth - Tokens.Component.touchTargetMin) *
                                    pin.xFraction.coerceIn(0f, 1f),
                                y = (availableHeight - Tokens.Component.touchTargetMin) *
                                    pin.yFraction.coerceIn(0f, 1f),
                            )
                            .size(Tokens.Component.touchTargetMin)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Open ${pin.label}",
                            ) { onPin(pin.id) }
                            .semantics { contentDescription = pin.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(PIN_DOT)
                                .clip(CircleShape)
                                .background(Tokens.Palette.yellow500)
                                .border(2.dp, Tokens.Palette.navy900, CircleShape),
                        )
                    }
                }
            }
        }

        if (unplaced.isNotEmpty()) {
            Text(
                text = "Stages without an established position (${unplaced.size})",
                fontSize = Tokens.Type.sectionTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.ink900,
            )
        }

        // The map's equivalent list entry point. For an unplaced pin this is its only rendering, so
        // a stage whose placement is still being researched stays reachable and visibly present.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            // No key: the model does not require stage ids to be unique, and a duplicate key is a
            // crash rather than a layout glitch. Positional identity is enough for a flat list.
            items(unplaced) { pin ->
                UnplacedPinRow(pin = pin, onPin = onPin)
            }
        }
    }
}

/**
 * One positionless stage, as a row.
 *
 * The warning colour is carried from the pin so the same stage is recognisable in both places, and
 * the row still opens it: "no placement" is a fact about the map, not a closed door.
 */
@Composable
private fun UnplacedPinRow(pin: MapPin, onPin: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Component.touchTargetMin)
            .clickable(role = Role.Button, onClickLabel = "Open ${pin.label}") { onPin(pin.id) }
            .padding(horizontal = Tokens.Spacing.xs, vertical = Tokens.Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Spacing.xs)
                .clip(CircleShape)
                .background(Tokens.Palette.warningText),
        )
        Spacer(Modifier.width(Tokens.Spacing.xs))
        Text(
            text = pin.label,
            fontSize = Tokens.Type.body,
            color = Tokens.Palette.warningText,
        )
        Spacer(Modifier.width(Tokens.Spacing.xs))
        Text(
            text = "Placement not established",
            fontSize = Tokens.Type.caption,
            color = Tokens.Palette.warningText,
        )
    }
}

/**
 * My routes: the fixed-order draft editor and the saved routes.
 *
 * The stop list is the primary editor, per the contract; the map is only ever a preview. Stops are
 * numbered from 1 and shown in the order [RouteStopRow.index] gives, because the order is the route.
 *
 * @param onSave saves the current draft. Disabled whenever [RouteEditor.canSave] is false.
 * @param onRemove removes the stop at the given stop index, which is [RouteStopRow.index] rather
 *   than its position in the rendered list -- a gated row is flagged, not dropped, so the two agree.
 */
@Composable
internal fun RoutesScreen(
    rows: List<RouteStopRow>,
    saved: List<SavedRouteRow>,
    editor: RouteEditor,
    onSave: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
    ) {
        item {
            Text(
                text = "My routes",
                fontSize = Tokens.Type.pageTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.ink900,
            )
        }

        item {
            Text(
                text = "Route stops",
                fontSize = Tokens.Type.sectionTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.ink900,
                modifier = Modifier.padding(top = Tokens.Spacing.sm),
            )
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    text = "No stops yet. Add a stage to begin a route.",
                    fontSize = Tokens.Type.body,
                    color = Tokens.Palette.ink900,
                )
            }
        }

        // No key: removing a stop renumbers the rest, so an index-based key would change on every
        // removal anyway, and a duplicate key is a crash rather than a layout glitch.
        items(rows) { row ->
            StopRow(row = row, onRemove = onRemove)
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
            ) {
                // The reason is shown next to the disabled button rather than after a failed tap:
                // a disabled control with no explanation is indistinguishable from a broken one.
                editor.blockingReason?.let { reason ->
                    Text(
                        text = reason,
                        fontSize = Tokens.Type.secondary,
                        color = Tokens.Palette.errorText,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = editor.canSave,
                    modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
                ) {
                    Text(text = "Save route", fontSize = Tokens.Type.body)
                }
            }
        }

        item {
            Text(
                text = "Saved routes",
                fontSize = Tokens.Type.sectionTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.ink900,
                modifier = Modifier.padding(top = Tokens.Spacing.lg),
            )
        }

        if (saved.isEmpty()) {
            item {
                Text(
                    text = "No saved routes yet.",
                    fontSize = Tokens.Type.body,
                    color = Tokens.Palette.ink900,
                )
            }
        }

        // No key: a saved row's id is derived from its content, so two identical saved routes share
        // one, and a duplicate key is a crash rather than a layout glitch.
        items(saved) { row ->
            SavedRow(row = row)
        }
    }
}

/**
 * One stop in the draft.
 *
 * A gated stop keeps its number and its remove action: removing it is exactly how a user repairs a
 * route that can no longer be played, and hiding it would leave the route quietly different from
 * what is saved.
 */
@Composable
private fun StopRow(row: RouteStopRow, onRemove: (Int) -> Unit) {
    val accent = if (row.locked) Tokens.Palette.warningText else Tokens.Palette.ink900

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Component.routeNodeMinHeight)
            .background(Tokens.Palette.cream100, RoundedCornerShape(Tokens.Radius.sm))
            .padding(horizontal = Tokens.Spacing.sm, vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${row.index + 1}",
            fontSize = Tokens.Type.body,
            fontWeight = FontWeight.Bold,
            color = accent,
            // A minimum rather than a fixed width: at a larger font scale a two-digit number must be
            // allowed to grow, and a clipped sequence number would misnumber the route.
            modifier = Modifier.widthIn(min = Tokens.Spacing.xl),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.label, fontSize = Tokens.Type.body, color = accent)
            Text(text = row.kind, fontSize = Tokens.Type.caption, color = accent)
            row.problem?.let { problem ->
                Text(
                    text = problem,
                    fontSize = Tokens.Type.caption,
                    color = Tokens.Palette.warningText,
                )
            }
        }
        TextButton(
            onClick = { onRemove(row.index) },
            modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
        ) {
            Text(text = "Remove", fontSize = Tokens.Type.secondary)
        }
    }
}

/**
 * One saved route.
 *
 * A non-null [SavedRouteRow.problem] is the warning that playing the route as saved would not play
 * what the user saved; the row says so instead of looking identical to a playable one.
 */
@Composable
private fun SavedRow(row: SavedRouteRow) {
    val accent = if (row.problem != null) Tokens.Palette.warningText else Tokens.Palette.ink900

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Component.routeNodeMinHeight)
            .background(Tokens.Palette.cream100, RoundedCornerShape(Tokens.Radius.sm))
            .padding(Tokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
    ) {
        Text(
            text = row.name,
            fontSize = Tokens.Type.body,
            fontWeight = FontWeight.Bold,
            color = Tokens.Palette.ink900,
        )
        Text(
            text = "${row.stopCount} stop${if (row.stopCount == 1) "" else "s"}",
            fontSize = Tokens.Type.caption,
            color = Tokens.Palette.ink900,
        )
        row.problem?.let { problem ->
            Text(text = problem, fontSize = Tokens.Type.secondary, color = accent)
        }
    }
}
