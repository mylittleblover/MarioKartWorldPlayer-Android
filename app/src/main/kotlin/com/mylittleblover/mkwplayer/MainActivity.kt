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
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    PlayerScreen(Modifier.padding(inner))
                }
            }
        }
    }
}

/**
 * The player.
 *
 * Reads one state object and issues three commands. Every decision about what can
 * be played lives in the view model and the audio layer, so this composable holds no
 * policy of its own -- which is also why it has no tests of its own: the tests sit
 * where the rules are.
 */
@Composable
private fun PlayerScreen(modifier: Modifier = Modifier) {
    val model: PlayerViewModel = viewModel()
    val ui by model.ui.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) model.add(uri)
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
            Button(
                onClick = { picker.launch(arrayOf("audio/*", "*/*")) },
                enabled = !ui.busy,
            ) { Text(if (ui.busy) "Reading..." else "Add a .bwav file") }
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
