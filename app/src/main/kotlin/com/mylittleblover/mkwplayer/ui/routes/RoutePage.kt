package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.routes.RouteStop
import com.mylittleblover.mkwplayer.routes.SavedRoute
import com.mylittleblover.mkwplayer.routes.StageId
import com.mylittleblover.mkwplayer.ui.Tokens
import com.mylittleblover.mkwplayer.ui.UnlockUi

/**
 * The route editor, with the stop list it edits and the stages it can add.
 *
 * The page decides nothing about what a route may contain. [routeRows] settles which stops exist and which
 * are hidden, [canAdd] settles whether a stage may be added, and [RouteEditor] settles whether the draft can
 * be saved. What is left here is where the buttons go.
 *
 * Two behaviours are worth stating because the alternative is a quiet bug:
 *
 * A stage already in the route is **not offered again**. The model's own check would refuse it, but offering
 * it and then refusing the press makes the app look like it is arguing with the listener.
 *
 * The index a removal carries is the row's own [RouteStopRow.index], not its position in the displayed list.
 * Those agree today; they would stop agreeing the moment anything hid a row, and the removal would then take
 * out the wrong stop.
 */
@Composable
internal fun RoutePage(
    stops: List<RouteStop>,
    saved: List<SavedRoute>,
    availableStages: List<Pair<String, String>>,
    specialCupCourses: Set<String>,
    unlock: UnlockUi?,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    onSave: () -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The editor is built from the stops, so its save state cannot disagree with the list shown.
    val editor = RouteEditor.of(stops)
    // A fake route wrapper would duplicate the model; rows are built from the stop list directly.
    val rows = routeRowsFrom(stops, specialCupCourses, unlock)
    val savedRows = savedRows(saved, specialCupCourses, unlock)
    val taken = stops.filterIsInstance<RouteStop.Stage>().map { it.stage.value }.toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        Text("Route", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Tokens.Spacing.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rows.isEmpty()) {
                    Text(
                        "Add two stages to make a route. The music between them is chosen by the game's " +
                            "own road rules, not by this app.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    rows.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${row.index + 1}. ${row.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(row.kind, style = MaterialTheme.typography.bodySmall)
                                row.problem?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            OutlinedButton(onClick = { onRemove(row.index) }) { Text("Remove") }
                        }
                    }
                }

                if (editor.blockingReason != null) {
                    // The reason is shown even though the button is also disabled: a disabled control with no
                    // explanation reads as a broken app.
                    Text(editor.blockingReason, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onSave, enabled = editor.canSave) { Text("Save this route") }
            }
        }

        Text("Add a stage", style = MaterialTheme.typography.titleSmall)
        val offerable = availableStages.filter { it.first !in taken }
        if (offerable.isEmpty()) {
            Text(
                "Every stage the map knows about is already in this route.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            offerable.forEach { (code, label) ->
                OutlinedButton(
                    onClick = { onAdd(code) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(label) }
            }
        }

        if (savedRows.isNotEmpty()) {
            Text("Saved routes", style = MaterialTheme.typography.titleSmall)
            savedRows.forEach { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Tokens.Spacing.sm)) {
                        Text(row.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${row.stopCount} stops", style = MaterialTheme.typography.bodySmall)
                        row.problem?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { onPlay(row.id) },
                            enabled = row.problem == null,
                        ) { Text("Play") }
                    }
                }
            }
        }
    }
}

/**
 * Stop rows for a bare stop list.
 *
 * [routeRows] takes a [com.mylittleblover.mkwplayer.routes.Route], and a caller editing a draft has stops and
 * no route object yet. Rather than inventing one -- which would let the row builder and the model disagree
 * about what the draft is -- the rows are built from the same list the editor was given.
 */
private fun routeRowsFrom(
    stops: List<RouteStop>,
    specialCupCourses: Set<String>,
    unlock: UnlockUi?,
): List<RouteStopRow> = stops.mapIndexed { index, stop ->
    when (stop) {
        is RouteStop.Stage -> {
            val locked = CupVisibility.hides(stop.stage.value, specialCupCourses, unlock)
            RouteStopRow(
                index = index,
                label = if (locked) NOT_AVAILABLE_LABEL else stop.stage.value,
                kind = RouteStopRow.KIND_STAGE,
                locked = locked,
                problem = if (locked) lockedProblem(unlock) else null,
            )
        }
        is RouteStop.Road -> {
            val a = stop.connection.a.value
            val b = stop.connection.b.value
            val locked = CupVisibility.hides(a, specialCupCourses, unlock) ||
                CupVisibility.hides(b, specialCupCourses, unlock)
            RouteStopRow(
                index = index,
                label = if (locked) NOT_AVAILABLE_LABEL else "$a -> $b",
                kind = RouteStopRow.KIND_ROAD,
                locked = locked,
                problem = if (locked) lockedProblem(unlock) else null,
            )
        }
    }
}

/** A stage the editor can offer, as the shell passes it in. */
internal fun stageChoice(code: String, label: String): Pair<String, String> = code to label

/** Convenience for a caller holding stage ids rather than codes. */
internal fun stageChoice(id: StageId, label: String): Pair<String, String> = id.value to label
