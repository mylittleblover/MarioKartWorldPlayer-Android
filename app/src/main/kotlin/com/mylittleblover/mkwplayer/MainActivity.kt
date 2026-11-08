package com.mylittleblover.mkwplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mylittleblover.mkwplayer.audio.TimingDiagnosis
import com.mylittleblover.mkwplayer.player.PlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val model: PlayerViewModel = viewModel()
                val ui by model.ui.collectAsStateWithLifecycle()
                MkwApp(model = model, ui = ui)
            }
        }
    }
}

/**
 * The old single-surface test screen.
 *
 * Kept, and kept unreachable, because its parts are still the fastest way to drive one file through the
 * engine by hand and it is what the instrumentation tests know. The app itself is [MkwApp]; a listener never
 * sees this.
 */
@Composable
private fun LegacyPlayerScreen(modifier: Modifier = Modifier) {
    val model: PlayerViewModel = viewModel()
    val ui by model.ui.collectAsStateWithLifecycle()
    val catalogState by (model.catalogState?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) model.add(uri)
    }

    // Android 13+ needs the user to allow notifications before a media notification can
    // appear; the view model only records that asking is needed, because the request
    // itself requires an Activity.
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) model.openFlow(uris)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> model.onNotificationPermissionResult(granted) }

    LaunchedEffect(ui.needsNotificationPermission) {
        if (ui.needsNotificationPermission) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("MKW Player", style = MaterialTheme.typography.headlineMedium)
                Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ui.status, style = MaterialTheme.typography.titleMedium)
                    if (ui.title.isNotEmpty()) {
                        Text(ui.title, style = MaterialTheme.typography.bodyMedium)
                    }

                    // Draggable: the bar is the seek control, so tapping or dragging
                    // it must move the playhead rather than only reporting it.
                    Slider(
                        value = ui.progress,
                        onValueChange = { model.seekTo(it) },
                        enabled = ui.canStop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(ui.position, style = MaterialTheme.typography.bodySmall)
                        Text(ui.total, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { model.pause() },
                            enabled = ui.canPause,
                        ) { Text("Pause") }
                        OutlinedButton(
                            onClick = { model.resume() },
                            enabled = ui.canResume,
                        ) { Text("Resume") }
                        OutlinedButton(
                            onClick = { model.stop() },
                            enabled = ui.canStop,
                        ) { Text("Stop") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker.launch(arrayOf("audio/*", "*/*")) },
                    enabled = !ui.busy,
                ) { Text(if (ui.busy) "Reading..." else "Add a .bwav file") }

                // Multi-select: a stage is five sibling files, and picking them one at a time is
                // how the bridge or an outro gets left out.
                OutlinedButton(
                    onClick = { multiPicker.launch(arrayOf("audio/*", "*/*")) },
                    enabled = !ui.busy,
                ) { Text("Open a stage") }
            }
        }

        item {
            // Reads filesDir/stages. Kept separate from the picker because it is the one path an
            // automated test can drive, and because some file managers return names the app
            // cannot resolve.
            OutlinedButton(
                onClick = { model.openFlowFromFolder() },
                enabled = !ui.busy,
            ) { Text("Scan the stages folder") }
        }

        ui.flow?.let { flow ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(flow.title, style = MaterialTheme.typography.titleSmall)
                        Text(flow.summary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            flow.provenance,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = if (flow.isProvisional) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            item {
                // The console. Three events, and each is only enabled when it means something: a control that
                // is always available is a control that is sometimes a no-op, and the listener cannot tell
                // which.
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Console", style = MaterialTheme.typography.titleSmall)

                        if (flow.performanceBlocker != null) {
                            // A disabled button with no explanation reads as a broken app.
                            Text(flow.performanceBlocker, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { model.startPerformance() },
                                    enabled = !flow.performing && !ui.busy,
                                ) { Text("Start") }

                                // The next-stage event. The prelude loops until this is pressed, which is what
                                // the game does: the race start is an event, not a timer.
                                OutlinedButton(
                                    onClick = { model.advancePerformance() },
                                    enabled = flow.canAdvance,
                                ) { Text("Next stage") }

                                OutlinedButton(
                                    onClick = { model.stopPerformance() },
                                    enabled = flow.performing,
                                ) { Text("Stop") }
                            }

                            if (flow.hasFast) {
                                // The same music at a different tempo. The compiled rule crossfades it at a
                                // bar line, so the switch is not a cut.
                                OutlinedButton(
                                    onClick = { model.toggleFast() },
                                    enabled = flow.canEnd || flow.fastPlaying,
                                ) {
                                    Text(if (flow.fastPlaying) "Back to normal" else "Fast version")
                                }
                            }

                            if (flow.performanceStage.isNotEmpty()) {
                                Text(flow.performanceStage, style = MaterialTheme.typography.bodySmall)
                            }

                            if (flow.canEnd) {
                                // The ending is chosen by how far into the loop playback has got; the console
                                // offers the event, not a menu of files.
                                Button(onClick = { model.takeOutro() }) { Text("End the stage") }
                                Text(
                                    "The ending nearest to the current position is chosen.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            items(flow.sections, key = { "sec-${it.segment}" }) { section ->
                val active = section.segment == flow.activeSegment
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                section.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                "${section.seconds}s",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(
                            onClick = { model.playSection(section.segment) },
                            enabled = !active,
                        ) { Text(if (active) "Playing" else "Play") }
                    }
                }
            }
        }

        ui.detail?.let { detail ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        detail,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Device timing", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (ui.timingVerdict) {
                                TimingDiagnosis.Verdict.HEALTHY -> "HEALTHY"
                                TimingDiagnosis.Verdict.DEGRADED -> "DEGRADED"
                                TimingDiagnosis.Verdict.FAILING -> "FAILING"
                                TimingDiagnosis.Verdict.NO_DATA -> "NO DATA"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(ui.timing, style = MaterialTheme.typography.bodySmall)
                    if (!ui.outputActive) {
                        Text(
                            "No audio device output on this system.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (ui.tracks.isNotEmpty()) {
            item {
                Text("Library", style = MaterialTheme.typography.titleMedium)
            }
            items(ui.tracks, key = { it.key }) { track ->
                val selected = track.key == ui.selectedKey
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            track.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            if (track.playable) {
                                "${track.sampleRate} Hz - ${formatFrames(track.frames)}"
                            } else {
                                "Not playable: unsupported codec"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (track.playable && !selected) {
                                OutlinedButton(onClick = { model.select(track.key) }) { Text("Play") }
                            }
                            OutlinedButton(onClick = { model.remove(track.key) }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        ui.catalog?.let { catalog ->
            item {
                Text("Catalog", style = MaterialTheme.typography.titleMedium)
            }
            catalogState?.let { state ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                if (state.available) state.summary else "No catalogue bundled",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            // The coverage notice is the one line that must never be dropped: without
                            // it these rows read as the complete soundtrack.
                            state.coverageNotice?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${catalog.entries.size} tracks known, " +
                                "${catalog.playableCount} playable in this build",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (catalog.isPartial) {
                            // Stated, not buried: the source index covers 4 of 499 archives
                            // and its own reasons say verification was not performed.
                            Text(
                                "Partial catalog: ${catalog.freezeStatus.lowercase().replace('_', ' ')}, " +
                                    "${catalog.archivesRead} of ${catalog.archivesExpected} archives read.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            catalog.coverageReasons.forEach { reason ->
                                Text("- $reason", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            "The audio files are not bundled. Pick a file to add it to your " +
                                "library; the catalog only says what exists and what this build can decode.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            items(catalog.categories(), key = { "cat-$it" }) { category ->
                val total = catalog.categoryCounts[category] ?: 0
                val playable = catalog.playableByCategory[category] ?: 0
                val open = ui.openCategory == category
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(category, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "$playable/$total playable",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (playable == 0) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        OutlinedButton(onClick = { model.toggleCategory(category) }) {
                            Text(if (open) "Hide" else "Show")
                        }
                        if (open) {
                            catalog.inCategory(category).forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        if (entry.playable) "playable" else "codec ${entry.codecKind}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "This build decodes Nintendo DSP-ADPCM BWAV, which covers every course " +
                    "theme. JukeBox and cutscene entries use a different codec and are " +
                    "listed as not playable rather than failing silently.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Frame count as `m:ss`, using the file's own rate is not possible here, so 48 kHz. */
private fun formatFrames(frames: Long): String {
    val seconds = frames / 48_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
