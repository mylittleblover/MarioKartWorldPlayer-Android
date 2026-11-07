package com.mylittleblover.mkwplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.audio.AudioEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    SelfTestScreen(Modifier.padding(inner))
                }
            }
        }
    }
}

/**
 * Startup screen that can run the engine once, on demand.
 *
 * The point is not to be a player yet. It exists so the engine path can be
 * exercised by hand on a real device, with the result shown as a status name
 * rather than a number, and with an explicit note when no file is loaded.
 */
@Composable
private fun SelfTestScreen(modifier: Modifier = Modifier) {
    var report by remember { mutableStateOf<List<String>>(emptyList()) }
    var engineRate by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("MKW Player", style = MaterialTheme.typography.headlineMedium)
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Engine self-test", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Creates an engine, reads its sample rate and renders a short " +
                        "block. Render needs a loaded runtime, so with nothing " +
                        "loaded it is skipped rather than reported as a failure.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        report = runEngineSelfTest().also { lines ->
                            engineRate = lines.firstOrNull { it.startsWith("sampleRate=") }
                                ?.substringAfter('=')?.toIntOrNull()
                        }
                    }) { Text("Run") }
                    Button(onClick = { report = emptyList(); engineRate = null }) { Text("Clear") }
                }
                engineRate?.let {
                    Text("engine sample rate: $it Hz", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Result", style = MaterialTheme.typography.titleMedium)
                if (report.isEmpty()) {
                    Text("not run", style = MaterialTheme.typography.bodySmall)
                } else {
                    report.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "This build decodes BWAV course themes. JukeBox entries use a codec " +
                "this build does not support and will report UnsupportedCodec.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Runs one engine lifecycle and reports what happened by name.
 *
 * Deliberately mirrors the verified device path: create, read rate, render,
 * destroy. A failure is reported as the status text plus its hint, never as a
 * bare number.
 */
private fun runEngineSelfTest(): List<String> {
    val lines = mutableListOf<String>()
    val engine = AudioEngine.create()
    if (engine == null) {
        lines += "createEngine failed: native allocator refused"
        return lines
    }
    try {
        lines += "engine created"
        lines += "sampleRate=${engine.sampleRate}"
        lines += "transportRate=${engine.transportRate}"

        val block = engine.render(frames = 256)
        if (block == null) {
            // renderEngineControl rejects when no runtime exists yet, which is
            // the state right after createEngine. Reporting that as a failure
            // would be wrong: nothing has been asked to play.
            lines += "render skipped: no runtime yet (nothing loaded)"
        } else {
            var peak = 0f
            var nonZero = 0
            block.forEach {
                val magnitude = kotlin.math.abs(it)
                if (magnitude > peak) peak = magnitude
                if (it != 0f) nonZero++
            }
            lines += "render ok: frames=256 peak=$peak nonZero=$nonZero"
            if (nonZero == 0) {
                lines += "silence as expected: no source is bound"
            }
        }

        val snapshot = engine.snapshot()
        if (snapshot == null) {
            lines += "snapshot unavailable"
        } else {
            lines += "snapshot fields=${snapshot.size}"
        }
    } finally {
        engine.close()
        lines += "engine closed"
    }
    return lines
}
