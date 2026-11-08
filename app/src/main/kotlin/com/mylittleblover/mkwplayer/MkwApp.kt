package com.mylittleblover.mkwplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.ui.NavState
import com.mylittleblover.mkwplayer.ui.Tokens
import com.mylittleblover.mkwplayer.ui.Tab
import com.mylittleblover.mkwplayer.ui.UiMapping
import com.mylittleblover.mkwplayer.ui.detail.DetailScreen
import com.mylittleblover.mkwplayer.ui.library.LibraryScreen
import com.mylittleblover.mkwplayer.ui.play.PlayScreen
import com.mylittleblover.mkwplayer.ui.routes.FullMap
import com.mylittleblover.mkwplayer.ui.routes.MAP_IMAGE_ASPECT
import com.mylittleblover.mkwplayer.ui.routes.RoutesScreen
import com.mylittleblover.mkwplayer.ui.settings.ImportScreen
import com.mylittleblover.mkwplayer.ui.settings.SettingsScreen

/**
 * The app: four tabs and the pages reached from them.
 *
 * Every screen in the build is reachable from here. Before this, each one existed and was tested but nothing
 * showed it -- a page nobody can open is not a feature, and a single scrolling test surface is not an app.
 *
 * The console deliberately sits above the tabs rather than inside one. It is the operator's controls for the
 * performance that is playing right now, and burying it inside the library would mean losing it whenever the
 * listener looks at the map.
 */
@Composable
internal fun MkwApp(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    modifier: Modifier = Modifier,
) {
    var nav by remember { mutableStateOf(NavState()) }

    // The pack picker. It is the only import path a normal listener needs: one file carries the audio, the
    // stage structures and the timings. Kept here rather than inside a page so the library's empty state and
    // the console's import button open the same picker.
    val packPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) model.importPack(uri)
    }
    val openPack = { packPicker.launch(arrayOf("*/*")) }

    // The single-file picker, kept because a listener who has a handful of .bwav files should not have to
    // build a pack to hear them.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) model.openFlow(uris)
    }
    val openFiles = { filePicker.launch(arrayOf("audio/*", "*/*")) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> model.onNotificationPermissionResult(granted) }

    LaunchedEffect(ui.needsNotificationPermission) {
        if (ui.needsNotificationPermission) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Tab.ordered().forEach { tab ->
                    NavigationBarItem(
                        selected = nav.tab == tab,
                        onClick = { nav = nav.select(tab) },
                        label = { Text(tab.label) },
                        icon = {},
                    )
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            // Back is handled by the system; the visible affordance matters because a detail page with no
            // way back reads as a dead end.
            if (nav.canGoBack) {
                TextButton(onClick = { nav = nav.back() ?: nav }) { Text("Back") }
            }

            val detail = nav.detail
            when {
                detail is NavState.Detail.Course -> CoursePage(model, ui, detail.code)
                else -> when (nav.tab) {
                    Tab.LIBRARY -> LibraryPage(model, ui, openPack, openFiles)
                    Tab.WORLD_MAP -> MapPage(model, ui)
                    Tab.INTERACTIVE -> ConsolePage(model, ui, openPack, openFiles)
                    Tab.MY_ROUTES -> RoutesPage()
                }
            }
        }
    }
}

/**
 * The library, with the two import entries at the top.
 *
 * The pack entry is first and named as the main path, because it is: one file, one action, and a whole
 * soundtrack. The file entry stays below it for a listener who has loose `.bwav` files and should not have to
 * build a pack to hear them.
 */
@Composable
private fun LibraryPage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    onOpenPack: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val catalogState by (model.catalogState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(Tokens.Spacing.md)) {
            Column(Modifier.padding(Tokens.Spacing.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Open your soundtrack", style = MaterialTheme.typography.titleMedium)

                if (ui.pack != null) {
                    Text(ui.pack!!.summary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "A pack carries the audio, the stage structures and the timings in one file, so one " +
                            "import is enough.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenPack, enabled = !ui.busy) {
                        Text(if (ui.busy) "Working..." else "Import a pack")
                    }
                    OutlinedButton(onClick = onOpenFiles, enabled = !ui.busy) {
                        Text("Add .bwav files")
                    }
                }

                if (ui.pack != null) {
                    OutlinedButton(onClick = { model.openFirstPackStage() }) {
                        Text("Open the first stage")
                    }
                }

                ui.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        LibraryScreen(
            header = UiMapping.libraryHeader(ui, catalogState),
            categories = UiMapping.libraryCategories(catalogState, ui.unlock),
            state = com.mylittleblover.mkwplayer.ui.library.stateFor(
                // The catalogue exists when the browser has rows; "available" tracks the parse, not the page.
                hasCatalogue = catalogState != null && catalogState!!.rows.isNotEmpty(),
                hasTracks = ui.tracks.isNotEmpty(),
                isPartial = ui.catalog?.isPartial == true,
                busy = ui.busy,
            ),
            onCategory = { model.toggleCategory(it) },
            onSearch = {},
            modifier = Modifier.weight(1f),
        )
    }
}

/** A course's detail page, reached from the map or the library. */
@Composable
private fun CoursePage(model: PlayerViewModel, ui: PlayerViewModel.Ui, code: String) {
    DetailScreen(
        header = UiMapping.detailHeader(ui.flow),
        cues = emptyList(),
        cueWarning = null,
        structure = UiMapping.structure(ui.flow),
        provenance = UiMapping.provenance(ui.flow),
        onPlay = { model.startPerformance() },
        onInteractive = {},
        canPlay = ui.flow?.performanceBlocker == null,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The world map: the shipped artwork, full screen, draggable and zoomable.
 *
 * The pins come from the anchor file the map was measured against, so a pin sits on its own diorama rather
 * than on a guess. The drag is clamped inside the map, and there is a reset button, because a map that can be
 * pushed off-screen with one flick is a map that has to be reloaded.
 */
@Composable
private fun MapPage(model: PlayerViewModel, ui: PlayerViewModel.Ui) {
    val anchors = remember { model.mapAnchors }
    val pins = UiMapping.pins(anchors, ui.unlock)
    val context = LocalContext.current
    // Decoded once and held: the map is redrawn on every frame of a drag, and decoding a 2500x2400 image per
    // frame would be the slowest thing on the screen.
    val image: androidx.compose.ui.graphics.ImageBitmap? = remember {
        runCatching {
            android.graphics.BitmapFactory
                .decodeResource(context.resources, R.drawable.world_map)
                .asImageBitmap()
        }.getOrNull()
    }

    Column(Modifier.fillMaxSize()) {
        if (image == null) {
            // The fallback states what is missing rather than showing an empty frame, which would read as
            // "the game has no map".
            Text(
                "The map artwork is missing from this build. The stage list is below.",
                modifier = Modifier.padding(Tokens.Spacing.md),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            FullMap(
                image = image,
                imageAspect = MAP_IMAGE_ASPECT,
                pins = pins,
                onPin = { model.selectStage(it) },
                modifier = Modifier.weight(1f),
            )
        }

        // The accessible equivalent, and the only rendering for a stage whose anchor is missing.
        val unplaced = pins.filter { !it.hasPosition }
        if (unplaced.isNotEmpty()) {
            Text(
                "Stages without an established position: ${unplaced.joinToString { it.label }}",
                modifier = Modifier.padding(Tokens.Spacing.md),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The routes page. No saved routes exist yet, and it says so rather than showing an empty list. */
@Composable
private fun RoutesPage() {
    RoutesScreen(
        rows = emptyList(),
        saved = emptyList(),
        editor = com.mylittleblover.mkwplayer.ui.routes.RouteEditor.of(emptyList()),
        onSave = {},
        onRemove = {},
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The performance console.
 *
 * Three events and one switch, each enabled only when it means something. A control that is always available
 * is a control that is sometimes a no-op, and the listener cannot tell which.
 */
@Composable
private fun ConsolePage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    onOpenPack: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val flow = ui.flow
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Console", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (flow == null) {
                    Text(
                        "No stage is open. Open one from the library first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenPack) { Text("Import a pack") }
                        OutlinedButton(onClick = onOpenFiles) { Text("Add .bwav files") }
                        OutlinedButton(onClick = { model.openFlowFromFolder() }) {
                            Text("Scan stages folder")
                        }
                    }
                } else {
                    Text(flow.title, style = MaterialTheme.typography.titleMedium)
                    Text(flow.summary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        flow.performanceStage.ifEmpty { "Not started" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    if (flow.performanceBlocker != null) {
                        // A disabled button with no explanation reads as a broken app.
                        Text(flow.performanceBlocker, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { model.startPerformance() },
                                enabled = !flow.performing,
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
                            // The same music at another tempo. The compiled rule crossfades it at a bar
                            // line, so the switch is not a cut.
                            OutlinedButton(
                                onClick = { model.toggleFast() },
                                enabled = flow.canEnd || flow.fastPlaying,
                            ) {
                                Text(if (flow.fastPlaying) "Back to normal" else "Fast version")
                            }
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

                    Text(flow.provenance, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        PlayScreen(
            transport = UiMapping.transport(ui),
            sections = UiMapping.sections(flow),
            title = ui.title,
            provenance = UiMapping.provenanceLine(flow),
            onPlay = { model.startPerformance() },
            onPause = { model.pause() },
            onResume = { model.resume() },
            onStop = { model.stopPerformance() },
            onSection = { model.playSection(it) },
            modifier = Modifier.fillMaxWidth(),
        )

        SettingsScreen(
            rows = UiMapping.settings(),
            rightsNotice = com.mylittleblover.mkwplayer.ui.art.CupArt.NOTICE,
            modifier = Modifier.fillMaxWidth(),
        )

        ImportScreen(
            summary = UiMapping.importSummary(ui),
            onScan = { model.openFlowFromFolder() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
