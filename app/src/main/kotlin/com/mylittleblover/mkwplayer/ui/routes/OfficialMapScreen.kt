package com.mylittleblover.mkwplayer.ui.routes

import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwFloatingPanel
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButtonTone
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwSwitch
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig
import com.mylittleblover.mkwplayer.routes.CustomRouteStop
import com.mylittleblover.mkwplayer.routes.LocalizedNames
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.rememberPressScale
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import com.mylittleblover.mkwplayer.ui.unpadHorizontal
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.ui.art.MapArtworkCache
import com.mylittleblover.mkwplayer.ui.art.ArtworkSlot
import com.mylittleblover.mkwplayer.ui.art.ResourceArtwork
import com.mylittleblover.mkwplayer.ui.art.artworkFade
import com.mylittleblover.mkwplayer.ui.art.rememberResourceArtwork
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.mylittleblover.mkwplayer.ui.art.StageArt
import java.io.File
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry
import com.mylittleblover.mkwplayer.ui.kit.MkwMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun OfficialMapScreen(
    image: ImageBitmap,
    anchors: MapAnchors.Catalog,
    routes: OfficialRouteCatalog,
    names: LocalizedNames,
    languageTag: String,
    specialUnlocked: Boolean,
    playableStages: Set<String>,
    state: WorldMapState,
    onOpenCourse: (String) -> Unit,
    modifier: Modifier = Modifier,
    connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? = null,
    installedJukebox: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game>> = emptyList(),
    packDirectory: File? = null,
    inspectOfficialRoute: (OfficialRoute, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode) -> RoadJukeboxCatalog.RouteValidationReport? = { _, _, _ -> null },
    inspectDraftRoute: (List<Pair<String, String>>, Map<Int, CustomRouteEdgeConfig>, RoadJukeboxCatalog.RoadMusicRuleMode) -> RoadJukeboxCatalog.RouteValidationReport? = { _, _, _ -> null },
    onPlayOfficialRoute: (OfficialRoute, Boolean, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode, RoadJukeboxCatalog.RouteSuiteMode) -> Unit = { _, _, _, _, _ -> },
    onSaveCustomRoute: (routeId: String?, name: String, stops: List<CustomRouteStop>, edges: List<CustomRouteEdgeConfig>, loopWholeRoute: Boolean, roadRuleMode: RoadJukeboxCatalog.RoadMusicRuleMode, routeSuiteMode: RoadJukeboxCatalog.RouteSuiteMode) -> Unit = { _, _, _, _, _, _, _ -> },
    onPlayCustomDraft: (name: String, stops: List<CustomRouteStop>, edges: List<CustomRouteEdgeConfig>, loopWholeRoute: Boolean, roadRuleMode: RoadJukeboxCatalog.RoadMusicRuleMode, routeSuiteMode: RoadJukeboxCatalog.RouteSuiteMode) -> Unit = { _, _, _, _, _, _ -> },
    officialFreeRoamEntryFor: (routeId: String) -> OfficialRouteFreeRoamEntry = { OfficialRouteFreeRoamEntry() },
    onOfficialFreeRoamConfigChange: (routeId: String, config: RoadJukeboxCatalog.PureFreeRoamConfig) -> Unit = { _, _ -> },
    onOfficialFreeRoamEdgeOverrideChange: (routeId: String, edgeIndex: Int, override: RoadJukeboxCatalog.EdgeFreeRoamOverride?) -> Unit = { _, _, _ -> },
    onPlayOfficialRouteWithFreeRoam: ((OfficialRoute, Boolean, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode, RoadJukeboxCatalog.RouteSuiteMode, RoadJukeboxCatalog.PureFreeRoamConfig, Map<Int, RoadJukeboxCatalog.EdgeFreeRoamOverride>) -> Unit)? = null,
    onSaveCustomRouteWithFreeRoam: ((routeId: String?, name: String, stops: List<CustomRouteStop>, edges: List<CustomRouteEdgeConfig>, loopWholeRoute: Boolean, roadRuleMode: RoadJukeboxCatalog.RoadMusicRuleMode, routeSuiteMode: RoadJukeboxCatalog.RouteSuiteMode, pureFreeRoamConfig: RoadJukeboxCatalog.PureFreeRoamConfig) -> Unit)? = null,
    onPlayCustomDraftWithFreeRoam: ((name: String, stops: List<CustomRouteStop>, edges: List<CustomRouteEdgeConfig>, loopWholeRoute: Boolean, roadRuleMode: RoadJukeboxCatalog.RoadMusicRuleMode, routeSuiteMode: RoadJukeboxCatalog.RouteSuiteMode, pureFreeRoamConfig: RoadJukeboxCatalog.PureFreeRoamConfig) -> Unit)? = null,
) {
    val visibleRoutes = routes.visible(specialUnlocked)
    val visibleStages = routes.stageCodes(specialUnlocked)
    val pins = anchors.stages.filter { it.code.removeSuffix("_00") in visibleStages }
    val route = visibleRoutes.firstOrNull { it.id == state.selectedRouteId }
    val course = state.selectedCourseCode?.takeIf { it in visibleStages }
    // Text metadata for the selected course's detail card; NEVER drawn on the map outside edit mode (#2).
    val courseNeighbours = remember(course, connections, visibleStages) {
        course?.let { connections?.neighbours(it) }
            ?.filter { it in visibleStages }
            .orEmpty()
    }
    // Map canvas outgoing connections: shown ONLY for the active stop while connecting/editing (#2).
    val mapReachableOrigin = remember(state.editing, state.editFrom, visibleStages) {
        activeMapReachableOrigin(state.editing, state.editFrom, visibleStages)
    }
    val mapReachableNeighbours = remember(state.editing, state.editFrom, connections, visibleStages) {
        activeMapReachableNeighbours(state.editing, state.editFrom, connections, visibleStages)
    }
    val sink = course != null && connections != null && connections.outgoing(course).isEmpty()
    var picker by rememberSaveable { mutableStateOf(false) }
    var stops by rememberSaveable { mutableStateOf(false) }
    var draftSettingsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.editing) {
        if (!state.editing) draftSettingsOpen = false
    }
    var officialLoop by rememberSaveable(route?.id) { mutableStateOf(false) }
    var officialRuleMode by rememberSaveable(route?.id) { mutableStateOf(RoadJukeboxCatalog.RoadMusicRuleMode.AUTO) }
    var officialSuiteMode by rememberSaveable(route?.id) { mutableStateOf(RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE) }
    var officialFreeRoamConfig by remember(route?.id) {
        mutableStateOf(
            route?.let { officialFreeRoamEntryFor(it.id).config.normalized() }
                ?: RoadJukeboxCatalog.PureFreeRoamConfig(),
        )
    }
    val officialFreeRoamOverrides = remember(route?.id) {
        mutableStateMapOf<Int, RoadJukeboxCatalog.EdgeFreeRoamOverride>().apply {
            route?.let { putAll(officialFreeRoamEntryFor(it.id).edgeOverrides) }
        }
    }
    val sessionOverrides = remember(route?.id) { mutableStateMapOf<Int, String>() }
    var pickingEdgeIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingOfficialLeg by rememberSaveable { mutableStateOf(false) }
    var saveDialogOpen by rememberSaveable { mutableStateOf(false) }
    var saveDraftName by rememberSaveable(state.editingRouteId) { mutableStateOf(state.editingRouteName) }
    val defaultRouteName = stringResource(R.string.route_default_name)

    val playActiveOfficialRoute: (OfficialRoute) -> Unit = { targetRoute ->
        val customCb = onPlayOfficialRouteWithFreeRoam
        if (customCb != null) {
            customCb(
                targetRoute,
                officialLoop,
                sessionOverrides.toMap(),
                officialRuleMode,
                officialSuiteMode,
                officialFreeRoamConfig.normalized(),
                officialFreeRoamOverrides.toMap(),
            )
        } else {
            onPlayOfficialRoute(targetRoute, officialLoop, sessionOverrides.toMap(), officialRuleMode, officialSuiteMode)
        }
    }
    val playActiveCustomDraft: () -> Unit = {
        val routeName = state.editingRouteName.ifBlank { defaultRouteName }
        val customCb = onPlayCustomDraftWithFreeRoam
        if (customCb != null) {
            customCb(
                routeName,
                state.toCustomRouteStops(),
                state.toCustomRouteEdges(),
                state.draftLoopWholeRoute,
                state.draftRoadRuleMode,
                state.draftRouteSuiteMode,
                state.draftPureFreeRoamConfig.normalized(),
            )
        } else {
            onPlayCustomDraft(
                routeName,
                state.toCustomRouteStops(),
                state.toCustomRouteEdges(),
                state.draftLoopWholeRoute,
                state.draftRoadRuleMode,
                state.draftRouteSuiteMode,
            )
        }
    }
    val saveActiveCustomDraft: (String) -> Unit = { trimmed ->
        val customCb = onSaveCustomRouteWithFreeRoam
        if (customCb != null) {
            customCb(
                state.editingRouteId,
                trimmed,
                state.toCustomRouteStops(),
                state.toCustomRouteEdges(),
                state.draftLoopWholeRoute,
                state.draftRoadRuleMode,
                state.draftRouteSuiteMode,
                state.draftPureFreeRoamConfig.normalized(),
            )
        } else {
            onSaveCustomRoute(
                state.editingRouteId,
                trimmed,
                state.toCustomRouteStops(),
                state.toCustomRouteEdges(),
                state.draftLoopWholeRoute,
                state.draftRoadRuleMode,
                state.draftRouteSuiteMode,
            )
        }
    }

    val officialReport = remember(route, sessionOverrides.toMap(), officialRuleMode, officialFreeRoamConfig.enabled, officialFreeRoamConfig.trackPool, packDirectory, installedJukebox) {
        route?.let { inspectOfficialRoute(it, sessionOverrides.toMap(), officialRuleMode) }
    }
    val draftReport = remember(state.draft, state.draftEdgeConfigs, state.draftRoadRuleMode, state.draftPureFreeRoamConfig.enabled, state.draftPureFreeRoamConfig.trackPool, packDirectory, installedJukebox) {
        if (state.draft.isNotEmpty()) inspectDraftRoute(state.draft, state.draftEdgeConfigs, state.draftRoadRuleMode) else null
    }
    val fallbackName = stringResource(R.string.map_course)
    val name: (String) -> String = { names.name(it, languageTag) ?: fallbackName }

    /**
     * What a pin tap means.
     *
     * In edit mode a tap is not a selection: it either starts a connection or completes one, and the game's
     * own table decides whether that is allowed. A refused pair states why instead of doing nothing, and the
     * reverse of a real connection is refused rather than silently accepted with the ends swapped.
     *
     * The refusal sentences are resolved here, outside the lambda: `stringResource` is a composable call and
     * a tap happens after composition has finished.
     */
    val refusalReverse = stringResource(R.string.map_edit_reverse_only)
    val refusalSink = stringResource(R.string.map_edit_sink)
    val refusalMissing = stringResource(R.string.map_edit_refusal_missing)
    val onPinTap: (String) -> Unit = { code ->
        val tapped = code.removeSuffix("_00")
        if (!state.editing || connections == null) {
            state.selectCourse(code)
        } else if (state.editFrom == null) {
            state.editStart(tapped)
        } else {
            val from = state.editFrom!!
            when {
                from == tapped -> if (state.draft.isEmpty()) state.editStart(null)
                connections.connects(from, tapped) -> state.editAppend(from, tapped)
                connections.connects(tapped, from) -> state.editRefuse(refusalReverse)
                connections.outgoing(from).isEmpty() -> state.editRefuse("$refusalSink ${name(from)}")
                else -> state.editRefuse(refusalMissing)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 540.dp && maxHeight < 480.dp)
        val paneWidth = if (maxWidth >= 840.dp) 320.dp else 248.dp
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val viewportMaxHeight = maxHeight
                MapCanvas(
                    image = image,
                    sourceCanvasWidth = anchors.canvasWidth.toFloat(),
                    pins = pins,
                    route = route,
                    state = state,
                    name = name,
                    modifier = Modifier.fillMaxSize(),
                    reachableOrigin = mapReachableOrigin,
                    reachableNeighbours = mapReachableNeighbours,
                    onPinTap = onPinTap,
                    draft = state.draft,
                    editFrom = state.editFrom,
                    draftEdgeConfigs = state.draftEdgeConfigs,
                    packDirectory = packDirectory,
                    onDraftEdgeTap = { idx ->
                        pickingOfficialLeg = false
                        pickingEdgeIndex = idx
                    },
                )
                val horizontalControls = maxHeight < 240.dp || (!wide && maxHeight < 400.dp)
                MkwFloatingPanel(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .widthIn(max = (maxWidth - if (horizontalControls) 196.dp else 72.dp).coerceIn(140.dp, 340.dp)),
                    shape = MkwKitTokens.Shape.pill,
                    shadowElevation = 3.dp,
                    alpha = 0.96f,
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = if (wide) 14.dp else 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                stringResource(R.string.map_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (wide && !horizontalControls) {
                                Text(
                                    stringResource(R.string.map_subtitle, pins.size),
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                                    color = MkwKitTokens.Palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (!wide) {
                            MkwFilterPill(
                                label = stringResource(R.string.map_official_routes),
                                selected = true,
                                onClick = { picker = true },
                                role = Role.Button,
                            )
                        }
                    }
                }
                val controls: @Composable () -> Unit = {
                    MapControl("+", stringResource(R.string.map_zoom_in), state.camera.zoom < MapTransform.MAX_ZOOM) { state.zoomBy(1.3f) }
                    MapControl("−", stringResource(R.string.map_zoom_out), state.camera.zoom > MapTransform.MIN_ZOOM) { state.zoomBy(1f / 1.3f) }
                    MapControl("⌖", stringResource(R.string.map_reset)) { state.reset() }
                    if (connections != null) {
                        // The editor is only offered when a connection table shipped: without one, every tap
                        // would be refused, which is a broken button rather than a missing feature.
                        MapControl(
                            symbol = if (state.editing) "✕" else "✎",
                            label = stringResource(if (state.editing) R.string.map_edit_exit else R.string.map_edit_enter),
                            active = state.editing,
                        ) {
                            if (state.editing) state.exitEdit() else state.enterEdit()
                        }
                    }
                }
                MkwFloatingPanel(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    shape = MkwKitTokens.Shape.pill,
                    shadowElevation = 3.dp,
                    alpha = 0.96f,
                ) {
                    if (horizontalControls) {
                        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) { controls() }
                    } else {
                        Column(Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { controls() }
                    }
                }
                if (!wide) {
                    val bottomKey = when {
                        state.editing -> "edit"
                        course != null -> "course:$course"
                        route != null -> "route:${route.id}"
                        else -> "browse"
                    }
                    val miniPlayerClearance = com.mylittleblover.mkwplayer.ui.LocalMiniPlayerOverlayPadding.current
                    val availablePanelHeight = (viewportMaxHeight - miniPlayerClearance).coerceAtLeast(180.dp)
                    val mapReducedMotion = rememberReducedMotion()
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(
                                start = 10.dp,
                                top = 10.dp,
                                end = 10.dp,
                                bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(10.dp),
                            )
                            .widthIn(max = 640.dp)
                            .animateContentSize(tween(if (mapReducedMotion) 0 else 240, easing = AppMotion.easeOut)),
                    ) {
                        AnimatedContent(
                            targetState = bottomKey,
                            transitionSpec = {
                                if (mapReducedMotion) {
                                    fadeIn(snap()) togetherWith fadeOut(snap())
                                } else {
                                    (slideInVertically(tween(220, easing = AppMotion.easeOut)) { it / 5 } +
                                        fadeIn(tween(200, easing = AppMotion.easeOut))) togetherWith
                                        (slideOutVertically(tween(180, easing = AppMotion.easeInOut)) { it / 6 } +
                                            fadeOut(tween(150)))
                                }
                            },
                            label = "map-bottom-card",
                        ) { key ->
                            if (key == "browse") {
                                MkwFloatingPanel(
                                    shape = MkwKitTokens.Shape.pill,
                                    shadowElevation = 3.dp,
                                    alpha = 0.96f,
                                ) {
                                    Text(
                                        stringResource(R.string.map_browse_hint),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MkwKitTokens.Palette.inkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                MkwFloatingPanel(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = availablePanelHeight * if (state.editing) .48f else .56f),
                                    shape = MkwKitTokens.Shape.card,
                                    shadowElevation = 4.dp,
                                    alpha = 0.98f,
                                ) {
                                    if (state.editing) {
                                        DraftRouteCompactDock(
                                            state = state,
                                            name = name,
                                            draftReport = draftReport,
                                            onSaveClick = {
                                                saveDraftName = state.editingRouteName.ifBlank { defaultRouteName }
                                                saveDialogOpen = true
                                            },
                                            onPlayDraft = playActiveCustomDraft,
                                            onOpenSettings = { draftSettingsOpen = true },
                                            onDone = {
                                                stops = false
                                                draftSettingsOpen = false
                                            },
                                        )
                                    } else if (course == null && route != null) {
                                        OfficialRouteCompactDock(
                                            route = route,
                                            languageTag = languageTag,
                                            name = name,
                                            officialReport = officialReport,
                                            pureFreeRoamEnabled = officialFreeRoamConfig.enabled,
                                            onPlayRoute = { playActiveOfficialRoute(route) },
                                            onOpenStopsSheet = { stops = true },
                                            onClearRoute = { state.clearSelection() },
                                        )
                                    } else {
                                        MapSelectionPanel(
                                            route = route,
                                            course = course,
                                            name = name,
                                            languageTag = languageTag,
                                            playable = playableStages,
                                            state = state,
                                            onStops = { stops = true },
                                            onOpenCourse = onOpenCourse,
                                            neighbours = courseNeighbours,
                                            sink = sink,
                                            connections = connections,
                                            officialReport = officialReport,
                                            loopRoute = officialLoop,
                                            onLoopChange = { officialLoop = it },
                                            ruleMode = officialRuleMode,
                                            onRuleModeChange = {
                                                officialRuleMode = it
                                                sessionOverrides.clear()
                                            },
                                            suiteMode = officialSuiteMode,
                                            onSuiteModeChange = { officialSuiteMode = it },
                                            pureFreeRoamConfig = officialFreeRoamConfig,
                                            onPureFreeRoamConfigChange = { next ->
                                                val clean = next.normalized()
                                                officialFreeRoamConfig = clean
                                                route?.let { onOfficialFreeRoamConfigChange(it.id, clean) }
                                            },
                                            onPlayRoute = {
                                                if (route != null) playActiveOfficialRoute(route)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (wide) {
                Column(
                    Modifier
                        .width(paneWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(12.dp)),
                ) {
                    MkwGhostButton(
                        text = stringResource(R.string.map_official_routes),
                        onClick = { picker = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    if (state.editing) {
                        ConnectionEditorPanel(
                            state = state,
                            name = name,
                            languageTag = languageTag,
                            draftReport = draftReport,
                            onPickEdgeMusic = { idx ->
                                pickingOfficialLeg = false
                                pickingEdgeIndex = idx
                            },
                            onSaveClick = {
                                saveDraftName = state.editingRouteName.ifBlank { defaultRouteName }
                                saveDialogOpen = true
                            },
                            onPlayDraft = playActiveCustomDraft,
                            onDone = { stops = false },
                            scrollable = false,
                        )
                    } else {
                        MapSelectionPanel(
                            route = route,
                            course = course,
                            name = name,
                            languageTag = languageTag,
                            playable = playableStages,
                            state = state,
                            onStops = { stops = true },
                            onOpenCourse = onOpenCourse,
                            neighbours = courseNeighbours,
                            sink = sink,
                            connections = connections,
                            officialReport = officialReport,
                            loopRoute = officialLoop,
                            onLoopChange = { officialLoop = it },
                            ruleMode = officialRuleMode,
                            onRuleModeChange = {
                                officialRuleMode = it
                                sessionOverrides.clear()
                            },
                            suiteMode = officialSuiteMode,
                            onSuiteModeChange = { officialSuiteMode = it },
                            pureFreeRoamConfig = officialFreeRoamConfig,
                            onPureFreeRoamConfigChange = { next ->
                                val clean = next.normalized()
                                officialFreeRoamConfig = clean
                                route?.let { onOfficialFreeRoamConfigChange(it.id, clean) }
                            },
                            onPlayRoute = {
                                if (route != null) playActiveOfficialRoute(route)
                            },
                            scrollable = false,
                        )
                    }
                }
            }
        }
    }
    if (picker) {
        val gpRoutes = remember(visibleRoutes) { visibleRoutes.filter { it.kind == OfficialRouteKind.GRAND_PRIX } }
        val rallyRoutes = remember(visibleRoutes) { visibleRoutes.filter { it.kind == OfficialRouteKind.RALLY } }
        var pickerKind by rememberSaveable(route?.id) {
            mutableStateOf(route?.kind ?: OfficialRouteKind.GRAND_PRIX)
        }
        val activePickerRoutes = if (pickerKind == OfficialRouteKind.GRAND_PRIX) gpRoutes else rallyRoutes
        MkwBottomSheet(
            onDismissRequest = { picker = false },
            title = stringResource(R.string.map_official_routes),
            subtitle = stringResource(R.string.map_picker_subtitle),
            modifier = Modifier.fillMaxHeight(0.82f),
        ) {
            Surface(
                shape = MkwKitTokens.Shape.pill,
                color = MkwKitTokens.Palette.inset,
                border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        OfficialRouteKind.GRAND_PRIX to gpRoutes.size,
                        OfficialRouteKind.RALLY to rallyRoutes.size,
                    ).forEach { (kind, count) ->
                        val selectedTab = pickerKind == kind
                        val label = stringResource(
                            R.string.map_kind_count_fmt,
                            stringResource(if (kind == OfficialRouteKind.GRAND_PRIX) R.string.map_grand_prix else R.string.map_rallies),
                            count,
                        )
                        Surface(
                            onClick = { pickerKind = kind },
                            shape = MkwKitTokens.Shape.pill,
                            color = if (selectedTab) MkwKitTokens.Palette.ground else Color.Transparent,
                            shadowElevation = if (selectedTab) 1.dp else 0.dp,
                            border = if (selectedTab) BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.28f)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .semantics { role = Role.Tab; selected = selectedTab },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                                    fontWeight = if (selectedTab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            MkwDivider(modifier = Modifier.padding(top = 4.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                items(activePickerRoutes, key = { it.id }) { item ->
                    val isSelected = route?.id == item.id
                    MkwCard(
                        onClick = {
                            val points = item.stops.mapNotNull { anchors.stageTolerant(it.stageCode)?.let { p -> Offset(p.u, p.v) } }
                            state.selectRoute(item.id, points)
                            stops = false
                            picker = false
                        },
                        tone = if (isSelected) MkwSurfaceTone.RoseTint else MkwSurfaceTone.Porcelain,
                        selected = isSelected,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CupArt.routeEmblem(item.id)?.let { emblem ->
                                ResourceArtwork(emblem, Modifier.size(46.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = item.displayName(languageTag),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${name(item.start.stageCode)} → ${name(item.end.stageCode)} · ${stringResource(R.string.map_route_stops, item.stops.size)}",
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                    color = MkwKitTokens.Palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MkwKitTokens.Palette.crimson,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (stops && route != null) {
        var expandedOfficialFreeRoamLeg by rememberSaveable(route.id) { mutableStateOf<Int?>(null) }
        val sheetReducedMotion = MkwMotion.rememberPrefersReducedMotion()
        MkwBottomSheet(
            onDismissRequest = { stops = false },
            title = route.displayName(languageTag),
            subtitle = "${name(route.start.stageCode)} → ${name(route.end.stageCode)} · ${stringResource(R.string.map_route_stops, route.stops.size)}",
            modifier = Modifier.fillMaxHeight(0.84f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (officialReport?.canPlay == false) {
                    Text(
                        localizedRouteBlockingReason(officialReport, R.string.route_blocked_incomplete_pool, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.danger,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CupArt.routeEmblem(route.id)?.let { emblem ->
                        ResourceArtwork(emblem, Modifier.size(40.dp))
                    }
                    MkwPrimaryButton(
                        text = stringResource(R.string.route_play),
                        onClick = { playActiveOfficialRoute(route) },
                        enabled = officialReport?.canPlay != false,
                        leadingIcon = Icons.Filled.PlayArrow,
                        size = MkwButtonSize.Compact,
                        modifier = Modifier.weight(1f),
                    )
                    MkwFilterPill(
                        label = stringResource(R.string.route_loop_whole),
                        selected = officialLoop,
                        onClick = { officialLoop = !officialLoop },
                    )
                }
            }
            MkwDivider(modifier = Modifier.padding(top = 4.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "official-route-rules") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .animateContentSize(animationSpec = MkwMotion.sizeSpring()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PureFreeRoamConfigCard(
                            config = officialFreeRoamConfig,
                            onConfigChange = { next ->
                                val clean = next.normalized()
                                officialFreeRoamConfig = clean
                                if (!clean.enabled) expandedOfficialFreeRoamLeg = null
                                onOfficialFreeRoamConfigChange(route.id, clean)
                            },
                            compact = true,
                        )
                        RoadMusicRuleModeSelector(
                            selectedMode = officialRuleMode,
                            officialKind = route.kind,
                            onSelectMode = {
                                officialRuleMode = it
                                sessionOverrides.clear()
                            },
                        )
                        AnimatedVisibility(
                            visible = !officialFreeRoamConfig.enabled,
                            enter = if (sheetReducedMotion) fadeIn(tween(120)) else fadeIn(tween(MkwMotion.StandardMs, easing = MkwMotion.EaseOut)) + expandVertically(MkwMotion.sizeSpring()),
                            exit = if (sheetReducedMotion) fadeOut(tween(100)) else fadeOut(tween(MkwMotion.ExitMs, easing = MkwMotion.EaseOut)) + shrinkVertically(MkwMotion.sizeSpring()),
                        ) {
                            RouteSuiteModeSelector(
                                selectedMode = officialSuiteMode,
                                onSelectMode = { officialSuiteMode = it },
                            )
                        }
                    }
                }
                item(key = "stops-schematic-note") {
                    Text(
                        "${stringResource(R.string.map_route_readonly)} ${stringResource(R.string.map_schematic)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(route.stops.size, key = { idx -> route.stops[idx].id }) { idx ->
                    val stop = route.stops[idx]
                    MkwCard(
                        onClick = {
                            state.selectCourse(stop.stageCode)
                            stops = false
                        },
                        tone = MkwSurfaceTone.Porcelain,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${stop.ordinal}",
                                style = MkwKitTokens.tabular(MaterialTheme.typography.titleSmall),
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.crimson,
                            )
                            StageArt.resolve(stop.stageCode)?.let { artRes ->
                                ResourceArtwork(artRes, Modifier.size(36.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    name(stop.stageCode),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (stop == route.start || stop == route.end) {
                                    Text(
                                        stringResource(if (stop == route.start) R.string.map_start_badge else R.string.map_finish_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MkwKitTokens.Palette.crimsonDeep,
                                    )
                                }
                            }
                        }
                    }
                    val leg = officialReport?.legs?.getOrNull(idx)
                    if (leg != null) {
                        val summary = edgeResolutionSummary(leg.resolution, languageTag)
                        val isLegFreeRoamExpanded = officialFreeRoamConfig.enabled && expandedOfficialFreeRoamLeg == idx
                        val legOverride = officialFreeRoamOverrides[idx]
                        MkwCard(
                            onClick = {
                                pickingOfficialLeg = true
                                pickingEdgeIndex = idx
                            },
                            tone = MkwSurfaceTone.White,
                            shape = MkwKitTokens.Shape.thumb,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 4.dp)
                                .animateContentSize(animationSpec = MkwMotion.sizeSpring()),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.MusicNote, null, Modifier.size(16.dp), tint = MkwKitTokens.Palette.crimson)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${stringResource(R.string.route_road_prefix)} ${name(leg.from)} → ${name(leg.to)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MkwKitTokens.Palette.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (leg.resolution.isMissingOrIncomplete()) {
                                            MkwKitTokens.Palette.danger
                                        } else {
                                            MkwKitTokens.Palette.inkSecondary
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (officialFreeRoamConfig.enabled) {
                                    PureFreeRoamLegOverrideChip(
                                        config = officialFreeRoamConfig,
                                        override = legOverride,
                                        expanded = isLegFreeRoamExpanded,
                                        onClick = {
                                            expandedOfficialFreeRoamLeg = if (isLegFreeRoamExpanded) null else idx
                                        },
                                    )
                                } else if (leg.resolution.hasKnownPool()) {
                                    Text(
                                        stringResource(R.string.preset_edit),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MkwKitTokens.Palette.crimson,
                                    )
                                }
                            }
                            if (officialFreeRoamConfig.enabled) {
                                PureFreeRoamLegInlineEditor(
                                    visible = isLegFreeRoamExpanded,
                                    config = officialFreeRoamConfig,
                                    override = legOverride,
                                    onOverrideChange = { nextOv ->
                                        val cleanOv = nextOv?.normalized()?.takeUnless { it.isEmpty }
                                        if (cleanOv == null) {
                                            officialFreeRoamOverrides.remove(idx)
                                        } else {
                                            officialFreeRoamOverrides[idx] = cleanOv
                                        }
                                        onOfficialFreeRoamEdgeOverrideChange(route.id, idx, cleanOv)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (draftSettingsOpen && state.editing) {
        DraftRouteSettingsSheet(
            state = state,
            name = name,
            languageTag = languageTag,
            draftReport = draftReport,
            onPickEdgeMusic = { idx ->
                pickingOfficialLeg = false
                pickingEdgeIndex = idx
            },
            onSaveClick = {
                saveDraftName = state.editingRouteName.ifBlank { defaultRouteName }
                saveDialogOpen = true
            },
            onPlayDraft = playActiveCustomDraft,
            onDismiss = { draftSettingsOpen = false },
        )
    }

    val activeLegIndex = pickingEdgeIndex
    val activeReport = if (pickingOfficialLeg) officialReport else draftReport
    val activeLeg = if (activeLegIndex != null) activeReport?.legs?.getOrNull(activeLegIndex) else null
    if (activeLeg != null && activeLegIndex != null) {
        val currentCfg = if (!pickingOfficialLeg) {
            state.edgeConfigAt(activeLegIndex)
        } else {
            CustomRouteEdgeConfig(from = activeLeg.from, to = activeLeg.to)
        }
        val currentOverrideKey = if (pickingOfficialLeg) sessionOverrides[activeLegIndex] else currentCfg.selectedJukeboxKey
        val currentMuted = if (pickingOfficialLeg) false else !currentCfg.musicEnabled
        RouteEdgeMusicPickerSheet(
            legTitle = "${name(activeLeg.from)} → ${name(activeLeg.to)}",
            leg = activeLeg,
            installedJukebox = installedJukebox,
            allowCustomSubstitute = !pickingOfficialLeg,
            allowMute = !pickingOfficialLeg,
            sessionHint = pickingOfficialLeg,
            language = languageTag,
            packDirectory = packDirectory,
            currentSelectedKey = currentOverrideKey,
            currentMuted = currentMuted,
            onSelectAuto = {
                if (pickingOfficialLeg) {
                    sessionOverrides.remove(activeLegIndex)
                } else {
                    state.setEdgeMusic(activeLegIndex, musicEnabled = true, selectedJukeboxKey = null)
                }
            },
            onSelectMuted = {
                if (!pickingOfficialLeg) {
                    state.setEdgeMusic(activeLegIndex, musicEnabled = false, selectedJukeboxKey = null)
                }
            },
            onSelectTrackKey = { trackKey ->
                if (pickingOfficialLeg) {
                    sessionOverrides[activeLegIndex] = trackKey
                } else {
                    state.setEdgeMusic(activeLegIndex, musicEnabled = true, selectedJukeboxKey = trackKey)
                }
            },
            onDismiss = { pickingEdgeIndex = null },
        )
    }

    if (saveDialogOpen) {
        MkwDialog(
            onDismissRequest = { saveDialogOpen = false },
            title = stringResource(R.string.map_edit_save),
            confirmButton = {
                MkwPrimaryButton(
                    text = stringResource(R.string.route_save_changes),
                    onClick = {
                        val trimmed = saveDraftName.trim().ifEmpty { defaultRouteName }
                        state.editingRouteName = trimmed
                        saveActiveCustomDraft(trimmed)
                        saveDialogOpen = false
                    },
                    enabled = state.draft.isNotEmpty(),
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(
                    text = stringResource(R.string.discovery_cancel),
                    onClick = { saveDialogOpen = false },
                )
            },
        ) {
            MkwTextField(
                value = saveDraftName,
                onValueChange = { saveDraftName = it.take(120) },
                label = stringResource(R.string.route_name_label),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun DraftRouteCompactDock(
    state: WorldMapState,
    name: (String) -> String,
    draftReport: RoadJukeboxCatalog.RouteValidationReport?,
    onSaveClick: () -> Unit,
    onPlayDraft: () -> Unit,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit,
) {
    val stops = state.draftStops()
    val effectiveStops = remember(stops, state.editFrom) {
        if (stops.isNotEmpty()) stops else listOfNotNull(state.editFrom)
    }
    val reduceMotion = rememberReducedMotion()
    val listState = rememberLazyListState()
    LaunchedEffect(effectiveStops.size) {
        if (effectiveStops.isNotEmpty()) {
            listState.animateScrollToItem(effectiveStops.lastIndex)
        }
    }

    val badgeText = if (effectiveStops.isEmpty()) {
        stringResource(R.string.map_edit_badge_active)
    } else {
        stringResource(R.string.map_route_stops, effectiveStops.size)
    }
    val statusLine = when {
        effectiveStops.isEmpty() -> stringResource(R.string.map_edit_tap_start)
        effectiveStops.size == 1 -> stringResource(R.string.map_edit_tap_next_fmt, name(effectiveStops.first()))
        state.editingRouteName.isNotBlank() -> state.editingRouteName
        else -> "${name(effectiveStops.first())} → ${name(effectiveStops.last())}"
    }
    val errorText = state.editRefusal ?: if (draftReport?.canPlay == false) {
        localizedRouteBlockingReason(draftReport, R.string.route_blocked_incomplete_pool, name)
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MkwBadge(
                        text = badgeText,
                        tone = MkwBadgeTone.Crimson,
                        tabular = effectiveStops.isNotEmpty(),
                    )
                    if (state.draftPureFreeRoamConfig.enabled) {
                        MkwBadge(
                            text = stringResource(R.string.route_pure_freeroam_badge),
                            tone = MkwBadgeTone.Rose,
                            tabular = false,
                        )
                    }
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MkwKitTokens.Palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MkwKitTokens.Palette.danger,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DockToolIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.map_edit_undo),
                    enabled = state.draft.isNotEmpty(),
                    onClick = state::editUndo,
                )
                AnimatedVisibility(
                    visible = state.draft.isNotEmpty() && state.editingRouteId == null,
                    enter = if (reduceMotion) {
                        fadeIn(snap())
                    } else {
                        fadeIn(tween(180, easing = AppMotion.easeOut)) +
                            scaleIn(tween(180, easing = AppMotion.easeOut), initialScale = 0.92f)
                    },
                    exit = if (reduceMotion) {
                        fadeOut(snap())
                    } else {
                        fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.92f)
                    },
                ) {
                    DockToolIconButton(
                        icon = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.map_edit_clear),
                        enabled = state.draft.isNotEmpty() && state.editingRouteId == null,
                        onClick = state::editClear,
                    )
                }
                DockToolIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.map_edit_exit),
                    enabled = true,
                    onClick = {
                        state.exitEdit()
                        onDone()
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = effectiveStops.isNotEmpty(),
            enter = if (reduceMotion) {
                fadeIn(snap())
            } else {
                expandVertically(tween(220, easing = AppMotion.easeOut)) +
                    fadeIn(tween(180, easing = AppMotion.easeOut))
            },
            exit = if (reduceMotion) {
                fadeOut(snap())
            } else {
                shrinkVertically(tween(160)) + fadeOut(tween(120))
            },
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .unpadHorizontal(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(effectiveStops, key = { index, code -> "$index:$code" }) { index, code ->
                    val isTail = index == effectiveStops.lastIndex
                    DraftStopPill(
                        stepNumber = index + 1,
                        label = name(code),
                        isTail = isTail,
                    )
                    if (index < effectiveStops.lastIndex) {
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.labelSmall,
                            color = MkwKitTokens.Palette.outlineInk,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MkwPrimaryButton(
                text = stringResource(R.string.map_edit_save_short),
                onClick = onSaveClick,
                enabled = state.draft.isNotEmpty(),
                leadingIcon = Icons.Filled.Save,
                size = MkwButtonSize.Compact,
            )
            MkwRoseButton(
                text = stringResource(R.string.map_edit_play_short),
                onClick = onPlayDraft,
                enabled = state.draft.isNotEmpty() && draftReport?.canPlay != false,
                leadingIcon = Icons.Filled.PlayArrow,
                size = MkwButtonSize.Compact,
            )
            MkwSecondaryButton(
                text = stringResource(R.string.map_edit_settings_btn),
                onClick = onOpenSettings,
                leadingIcon = Icons.Filled.Tune,
                size = MkwButtonSize.Compact,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockToolIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberReducedMotion()
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.94f)
    val bgColor by animateColorAsState(
        targetValue = if (enabled) MkwKitTokens.Palette.inset else MkwKitTokens.Palette.card,
        animationSpec = tween(if (reduceMotion) 0 else 200, easing = AppMotion.easeOut),
        label = "dock-tool-bg",
    )
    val fgColor by animateColorAsState(
        targetValue = if (enabled) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.38f),
        animationSpec = tween(if (reduceMotion) 0 else 200, easing = AppMotion.easeOut),
        label = "dock-tool-fg",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = MkwKitTokens.Shape.pill,
        color = bgColor,
        border = BorderStroke(
            MkwKitTokens.Stroke.hairline,
            if (enabled) MkwKitTokens.Palette.hairline else MkwKitTokens.Palette.hairline.copy(alpha = 0.6f),
        ),
        modifier = Modifier
            .size(34.dp)
            .then(if (enabled) pressScale else Modifier)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = fgColor,
            )
        }
    }
}

@Composable
private fun DraftStopPill(
    stepNumber: Int,
    label: String,
    isTail: Boolean,
) {
    val reduceMotion = rememberReducedMotion()
    val bgColor by animateColorAsState(
        targetValue = if (isTail) MkwKitTokens.Palette.rose else MkwKitTokens.Palette.inset,
        animationSpec = tween(if (reduceMotion) 0 else 200, easing = AppMotion.easeOut),
        label = "stop-pill-bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isTail) MkwKitTokens.Palette.crimson.copy(alpha = 0.55f) else MkwKitTokens.Palette.hairline,
        animationSpec = tween(if (reduceMotion) 0 else 200, easing = AppMotion.easeOut),
        label = "stop-pill-border",
    )
    Surface(
        shape = MkwKitTokens.Shape.pill,
        color = bgColor,
        border = BorderStroke(MkwKitTokens.Stroke.hairline, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stepNumber.toString(),
                style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                fontWeight = FontWeight.Bold,
                color = if (isTail) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.crimson,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isTail) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isTail) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DraftEdgeLegCard(
    edgeIdx: Int,
    fromName: String,
    toName: String,
    leg: RoadJukeboxCatalog.RouteLegInspection?,
    musicEnabled: Boolean,
    languageTag: String,
    onClick: () -> Unit,
    pureFreeRoamConfig: RoadJukeboxCatalog.PureFreeRoamConfig = RoadJukeboxCatalog.PureFreeRoamConfig(),
    freeRoamOverride: RoadJukeboxCatalog.EdgeFreeRoamOverride? = null,
    freeRoamExpanded: Boolean = false,
    onToggleFreeRoamExpand: () -> Unit = {},
    onFreeRoamOverrideChange: (RoadJukeboxCatalog.EdgeFreeRoamOverride?) -> Unit = {},
) {
    val edgeTitle = "${edgeIdx + 1}. $fromName → $toName"
    val edgeSub = if (leg != null) {
        edgeResolutionSummary(leg.resolution, languageTag)
    } else {
        stringResource(R.string.route_edge_music_auto)
    }
    val hasIssue = leg?.resolution?.isMissingOrIncomplete() == true
    MkwCard(
        onClick = onClick,
        tone = MkwSurfaceTone.Porcelain,
        shape = MkwKitTokens.Shape.thumb,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MkwMotion.sizeSpring()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (musicEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (hasIssue) MkwKitTokens.Palette.danger else MkwKitTokens.Palette.crimson,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = edgeTitle,
                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = edgeSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasIssue) MkwKitTokens.Palette.danger else MkwKitTokens.Palette.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pureFreeRoamConfig.enabled && musicEnabled) {
                PureFreeRoamLegOverrideChip(
                    config = pureFreeRoamConfig,
                    override = freeRoamOverride,
                    expanded = freeRoamExpanded,
                    onClick = onToggleFreeRoamExpand,
                )
            } else if (leg?.resolution?.hasKnownPool() == true) {
                Text(
                    text = stringResource(R.string.preset_edit),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.crimson,
                )
            }
        }
        if (pureFreeRoamConfig.enabled && musicEnabled) {
            PureFreeRoamLegInlineEditor(
                visible = freeRoamExpanded,
                config = pureFreeRoamConfig,
                override = freeRoamOverride,
                onOverrideChange = onFreeRoamOverrideChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftRouteSettingsSheet(
    state: WorldMapState,
    name: (String) -> String,
    languageTag: String,
    draftReport: RoadJukeboxCatalog.RouteValidationReport?,
    onPickEdgeMusic: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onPlayDraft: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stops = state.draftStops()
    val reducedMotion = MkwMotion.rememberPrefersReducedMotion()
    var expandedFreeRoamLeg by rememberSaveable { mutableStateOf<Int?>(null) }
    val summarySubtitle = if (stops.isNotEmpty()) {
        "${stringResource(R.string.map_route_stops, stops.size)} · ${stringResource(R.string.route_legs_count, state.draft.size)}"
    } else if (state.editFrom != null) {
        "${stringResource(R.string.map_route_stops, 1)} · ${name(state.editFrom!!)}"
    } else {
        stringResource(R.string.map_edit_tap_start)
    }
    MkwBottomSheet(
        onDismissRequest = onDismiss,
        title = state.editingRouteName.ifBlank { stringResource(R.string.map_edit_settings_title) },
        subtitle = summarySubtitle,
        modifier = Modifier.fillMaxHeight(0.84f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (draftReport?.canPlay == false) {
                Text(
                    text = localizedRouteBlockingReason(draftReport, R.string.route_blocked_incomplete_pool, name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.danger,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MkwPrimaryButton(
                    text = stringResource(R.string.map_edit_save_short),
                    onClick = onSaveClick,
                    enabled = state.draft.isNotEmpty(),
                    leadingIcon = Icons.Filled.Save,
                    size = MkwButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                )
                MkwRoseButton(
                    text = stringResource(R.string.map_edit_play_short),
                    onClick = onPlayDraft,
                    enabled = state.draft.isNotEmpty() && draftReport?.canPlay != false,
                    leadingIcon = Icons.Filled.PlayArrow,
                    size = MkwButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        MkwDivider(modifier = Modifier.padding(top = 4.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "draft-settings-controls") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = MkwMotion.sizeSpring()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PureFreeRoamConfigCard(
                        config = state.draftPureFreeRoamConfig,
                        onConfigChange = { next ->
                            val clean = next.normalized()
                            state.draftPureFreeRoamConfig = clean
                            if (!clean.enabled) expandedFreeRoamLeg = null
                        },
                        compact = true,
                    )
                    MkwCard(
                        tone = MkwSurfaceTone.Recessed,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(10.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MkwKitTokens.Shape.thumb)
                                .clickable { state.draftLoopWholeRoute = !state.draftLoopWholeRoute }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Loop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MkwKitTokens.Palette.crimson,
                                )
                                Text(
                                    text = stringResource(R.string.route_loop_whole),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MkwKitTokens.Palette.ink,
                                )
                            }
                            MkwSwitch(
                                checked = state.draftLoopWholeRoute,
                                onCheckedChange = { state.draftLoopWholeRoute = it },
                            )
                        }
                        RoadMusicRuleModeSelector(
                            selectedMode = state.draftRoadRuleMode,
                            officialKind = null,
                            onSelectMode = { state.draftRoadRuleMode = it },
                        )
                        AnimatedVisibility(
                            visible = !state.draftPureFreeRoamConfig.enabled,
                            enter = if (reducedMotion) fadeIn(tween(120)) else fadeIn(tween(MkwMotion.StandardMs, easing = MkwMotion.EaseOut)) + expandVertically(MkwMotion.sizeSpring()),
                            exit = if (reducedMotion) fadeOut(tween(100)) else fadeOut(tween(MkwMotion.ExitMs, easing = MkwMotion.EaseOut)) + shrinkVertically(MkwMotion.sizeSpring()),
                        ) {
                            RouteSuiteModeSelector(
                                selectedMode = state.draftRouteSuiteMode,
                                onSelectMode = { state.draftRouteSuiteMode = it },
                            )
                        }
                    }
                }
            }
            if (state.editingRouteId != null) {
                item(key = "tail-only-note") {
                    Text(
                        text = stringResource(R.string.route_tail_only_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (state.draft.isEmpty()) {
                item(key = "draft-empty") {
                    MkwCard(
                        tone = MkwSurfaceTone.Recessed,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.map_edit_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            text = stringResource(R.string.map_edit_tap_start),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                }
            } else {
                items(
                    count = state.draft.size,
                    key = { idx -> "draft-leg-$idx-${state.draft[idx].first}-${state.draft[idx].second}" },
                ) { edgeIdx ->
                    val edge = state.draft[edgeIdx]
                    val leg = draftReport?.legs?.getOrNull(edgeIdx)
                    val cfg = state.edgeConfigAt(edgeIdx)
                    val isExpanded = state.draftPureFreeRoamConfig.enabled && expandedFreeRoamLeg == edgeIdx
                    DraftEdgeLegCard(
                        edgeIdx = edgeIdx,
                        fromName = name(edge.first),
                        toName = name(edge.second),
                        leg = leg,
                        musicEnabled = cfg.musicEnabled,
                        languageTag = languageTag,
                        onClick = { onPickEdgeMusic(edgeIdx) },
                        pureFreeRoamConfig = state.draftPureFreeRoamConfig,
                        freeRoamOverride = cfg.toFreeRoamOverride(),
                        freeRoamExpanded = isExpanded,
                        onToggleFreeRoamExpand = {
                            expandedFreeRoamLeg = if (isExpanded) null else edgeIdx
                        },
                        onFreeRoamOverrideChange = { ov ->
                            state.setEdgeFreeRoamOverride(
                                edgeIndex = edgeIdx,
                                trackCountOverride = ov?.trackCountOverride,
                                durationMinutesOverride = ov?.durationMinutesOverride,
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionEditorPanel(
    state: WorldMapState,
    name: (String) -> String,
    languageTag: String,
    draftReport: RoadJukeboxCatalog.RouteValidationReport?,
    onPickEdgeMusic: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onPlayDraft: () -> Unit,
    onDone: () -> Unit,
    scrollable: Boolean = true,
) {
    val stops = state.draftStops()
    val effectiveStops = remember(stops, state.editFrom) {
        if (stops.isNotEmpty()) stops else listOfNotNull(state.editFrom)
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.editingRouteName.ifBlank { stringResource(R.string.map_edit_title) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MkwBadge(
                text = if (effectiveStops.isEmpty()) {
                    stringResource(R.string.map_edit_badge_active)
                } else {
                    stringResource(R.string.map_route_stops, effectiveStops.size)
                },
                tone = MkwBadgeTone.Crimson,
                tabular = effectiveStops.isNotEmpty(),
            )
        }
        Text(
            text = if (state.editingRouteId != null) {
                stringResource(R.string.route_tail_only_note)
            } else if (effectiveStops.isEmpty()) {
                stringResource(R.string.map_edit_tap_start)
            } else {
                stringResource(R.string.map_edit_tap_next_fmt, name(effectiveStops.last()))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MkwKitTokens.Palette.inkSecondary,
        )
        state.editRefusal?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.danger)
        }
        val bodyScrollMod = if (scrollable) {
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }
        val editorReducedMotion = MkwMotion.rememberPrefersReducedMotion()
        var expandedFreeRoamLeg by rememberSaveable { mutableStateOf<Int?>(null) }
        Column(
            modifier = bodyScrollMod.animateContentSize(animationSpec = MkwMotion.sizeSpring()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PureFreeRoamConfigCard(
                config = state.draftPureFreeRoamConfig,
                onConfigChange = { next ->
                    val clean = next.normalized()
                    state.draftPureFreeRoamConfig = clean
                    if (!clean.enabled) expandedFreeRoamLeg = null
                },
                compact = true,
            )
            MkwCard(
                tone = MkwSurfaceTone.Recessed,
                shape = MkwKitTokens.Shape.control,
                contentPadding = PaddingValues(10.dp),
            ) {
                RoadMusicRuleModeSelector(
                    selectedMode = state.draftRoadRuleMode,
                    officialKind = null,
                    onSelectMode = { state.draftRoadRuleMode = it },
                )
                AnimatedVisibility(
                    visible = !state.draftPureFreeRoamConfig.enabled,
                    enter = if (editorReducedMotion) fadeIn(tween(120)) else fadeIn(tween(MkwMotion.StandardMs, easing = MkwMotion.EaseOut)) + expandVertically(MkwMotion.sizeSpring()),
                    exit = if (editorReducedMotion) fadeOut(tween(100)) else fadeOut(tween(MkwMotion.ExitMs, easing = MkwMotion.EaseOut)) + shrinkVertically(MkwMotion.sizeSpring()),
                ) {
                    RouteSuiteModeSelector(
                        selectedMode = state.draftRouteSuiteMode,
                        onSelectMode = { state.draftRouteSuiteMode = it },
                    )
                }
                if (stops.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MkwKitTokens.Shape.thumb)
                            .clickable { state.draftLoopWholeRoute = !state.draftLoopWholeRoute }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.Loop, null, Modifier.size(16.dp), tint = MkwKitTokens.Palette.crimson)
                            Text(stringResource(R.string.route_loop_whole), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.ink)
                        }
                        MkwSwitch(
                            checked = state.draftLoopWholeRoute,
                            onCheckedChange = { state.draftLoopWholeRoute = it },
                        )
                    }
                }
            }
            if (effectiveStops.isEmpty()) {
                Text(
                    stringResource(R.string.map_edit_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    effectiveStops.forEachIndexed { index, code ->
                        DraftStopPill(
                            stepNumber = index + 1,
                            label = name(code),
                            isTail = index == effectiveStops.lastIndex,
                        )
                    }
                }
                for ((edgeIdx, edge) in state.draft.withIndex()) {
                    val leg = draftReport?.legs?.getOrNull(edgeIdx)
                    val cfg = state.edgeConfigAt(edgeIdx)
                    val isExpanded = state.draftPureFreeRoamConfig.enabled && expandedFreeRoamLeg == edgeIdx
                    DraftEdgeLegCard(
                        edgeIdx = edgeIdx,
                        fromName = name(edge.first),
                        toName = name(edge.second),
                        leg = leg,
                        musicEnabled = cfg.musicEnabled,
                        languageTag = languageTag,
                        onClick = { onPickEdgeMusic(edgeIdx) },
                        pureFreeRoamConfig = state.draftPureFreeRoamConfig,
                        freeRoamOverride = cfg.toFreeRoamOverride(),
                        freeRoamExpanded = isExpanded,
                        onToggleFreeRoamExpand = {
                            expandedFreeRoamLeg = if (isExpanded) null else edgeIdx
                        },
                        onFreeRoamOverrideChange = { ov ->
                            state.setEdgeFreeRoamOverride(
                                edgeIndex = edgeIdx,
                                trackCountOverride = ov?.trackCountOverride,
                                durationMinutesOverride = ov?.durationMinutesOverride,
                            )
                        },
                    )
                }
                if (draftReport?.canPlay == false) {
                    Text(
                        localizedRouteBlockingReason(draftReport, R.string.route_blocked_incomplete_pool, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.danger,
                    )
                }
            }
        }
        MkwDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MkwPrimaryButton(
                text = stringResource(R.string.map_edit_save_short),
                onClick = onSaveClick,
                enabled = state.draft.isNotEmpty(),
                leadingIcon = Icons.Filled.Save,
                size = MkwButtonSize.Compact,
                modifier = Modifier.weight(1f),
            )
            MkwRoseButton(
                text = stringResource(R.string.map_edit_play_short),
                onClick = onPlayDraft,
                enabled = state.draft.isNotEmpty() && draftReport?.canPlay != false,
                leadingIcon = Icons.Filled.PlayArrow,
                size = MkwButtonSize.Compact,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MkwGhostButton(
                text = stringResource(R.string.map_edit_undo),
                onClick = state::editUndo,
                enabled = state.draft.isNotEmpty(),
            )
            if (state.editingRouteId == null) {
                MkwGhostButton(
                    text = stringResource(R.string.map_edit_clear),
                    onClick = state::editClear,
                    enabled = state.draft.isNotEmpty(),
                )
            }
            MkwGhostButton(
                text = stringResource(R.string.map_edit_exit),
                onClick = { state.exitEdit(); onDone() },
            )
        }
    }
}

@Composable
private fun OfficialRouteCompactDock(
    route: OfficialRoute,
    languageTag: String,
    name: (String) -> String,
    officialReport: RoadJukeboxCatalog.RouteValidationReport?,
    pureFreeRoamEnabled: Boolean = false,
    onPlayRoute: () -> Unit,
    onOpenStopsSheet: () -> Unit,
    onClearRoute: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CupArt.routeEmblem(route.id)?.let { emblem ->
                ResourceArtwork(emblem, Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = route.displayName(languageTag),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MkwKitTokens.Palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (pureFreeRoamEnabled) {
                        MkwBadge(
                            text = stringResource(R.string.route_pure_freeroam_badge),
                            tone = MkwBadgeTone.Rose,
                            tabular = false,
                        )
                    }
                }
                Text(
                    text = "${name(route.start.stageCode)} → ${name(route.end.stageCode)} · ${stringResource(R.string.map_route_stops, route.stops.size)}",
                    style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                    color = MkwKitTokens.Palette.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MkwIconButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.map_clear_route),
                onClick = onClearRoute,
                visualSize = 36.dp,
                iconSize = 18.dp,
                modifier = Modifier.size(40.dp),
            )
        }
        if (officialReport?.canPlay == false) {
            Text(
                text = localizedRouteBlockingReason(officialReport, R.string.route_blocked_incomplete_pool, name),
                style = MaterialTheme.typography.bodySmall,
                color = MkwKitTokens.Palette.danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MkwPrimaryButton(
                text = stringResource(R.string.route_play),
                onClick = onPlayRoute,
                enabled = officialReport?.canPlay != false,
                leadingIcon = Icons.Filled.PlayArrow,
                size = MkwButtonSize.Compact,
            )
            MkwSecondaryButton(
                text = stringResource(R.string.map_stops_sheet_action),
                onClick = onOpenStopsSheet,
                leadingIcon = Icons.Filled.Tune,
                size = MkwButtonSize.Compact,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapSelectionPanel(
    route: OfficialRoute?, course: String?, name: (String) -> String, languageTag: String,
    playable: Set<String>, state: WorldMapState, onStops: () -> Unit, onOpenCourse: (String) -> Unit,
    neighbours: List<String> = emptyList(), sink: Boolean = false,
    connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? = null,
    officialReport: RoadJukeboxCatalog.RouteValidationReport? = null,
    loopRoute: Boolean = false,
    onLoopChange: (Boolean) -> Unit = {},
    ruleMode: RoadJukeboxCatalog.RoadMusicRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
    onRuleModeChange: (RoadJukeboxCatalog.RoadMusicRuleMode) -> Unit = {},
    suiteMode: RoadJukeboxCatalog.RouteSuiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
    onSuiteModeChange: (RoadJukeboxCatalog.RouteSuiteMode) -> Unit = {},
    pureFreeRoamConfig: RoadJukeboxCatalog.PureFreeRoamConfig = RoadJukeboxCatalog.PureFreeRoamConfig(),
    onPureFreeRoamConfigChange: (RoadJukeboxCatalog.PureFreeRoamConfig) -> Unit = {},
    onPlayRoute: () -> Unit = {},
    scrollable: Boolean = true,
) {
    var connectionsExpanded by remember(course) { mutableStateOf(false) }
    val panelMod = if (scrollable) {
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }
    Column(panelMod.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            course != null -> {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        name(course),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MkwBadge(
                        text = stringResource(if (course in playable) R.string.map_audio_ready else R.string.map_audio_missing),
                        tone = if (course in playable) MkwBadgeTone.Rose else MkwBadgeTone.Neutral,
                        tabular = false,
                    )
                }
                // The game's own connections for this course. A course with none says so, because
                // "nowhere" is the answer for a finish line and pretending otherwise would show a road
                // the game does not have.
                when {
                    sink -> Text(
                        stringResource(R.string.map_course_sink),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    neighbours.isNotEmpty() -> {
                        // The count is the table's own, not the number drawn. A locked destination is hidden
                        // from the map but still exists in the export, and reporting "7 connections" when the
                        // game defines 8 would be a quieter lie than hiding the pin.
                        val total = connections?.outgoing(course)?.size ?: neighbours.size
                        val oneWaySuffix = stringResource(R.string.map_connection_one_way_suffix)
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(MkwKitTokens.Shape.thumb)
                                .clickable { connectionsExpanded = !connectionsExpanded }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.map_course_connections,
                                    total,
                                    neighbours.joinToString(" · ") { target ->
                                        if (connections?.isOneWay(course, target) == true) {
                                            name(target) + oneWaySuffix
                                        } else {
                                            name(target)
                                        }
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (connectionsExpanded) 4 else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (connectionsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (connectionsExpanded && total > neighbours.size) {
                            Text(
                                stringResource(R.string.map_course_connections_hidden, total - neighbours.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> Text(
                        stringResource(R.string.map_course_no_connections),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.map_course_details),
                        onClick = { onOpenCourse(course) },
                        size = MkwButtonSize.Compact,
                    )
                    MkwGhostButton(
                        text = stringResource(if (route != null) R.string.map_back_route else R.string.map_close),
                        onClick = { state.clearCourse() },
                    )
                }
            }
            route != null -> {
                Text(
                    route.displayName(languageTag),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.map_start, name(route.start.stageCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                    Text(
                        stringResource(R.string.map_finish, name(route.end.stageCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
                if (officialReport?.canPlay == false) {
                    Text(
                        localizedRouteBlockingReason(officialReport, R.string.route_blocked_incomplete_pool, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.danger,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.route_play),
                        onClick = onPlayRoute,
                        enabled = officialReport?.canPlay != false,
                        leadingIcon = Icons.Filled.PlayArrow,
                        size = MkwButtonSize.Compact,
                    )
                    MkwSecondaryButton(
                        text = stringResource(R.string.map_view_stops),
                        onClick = onStops,
                        size = MkwButtonSize.Compact,
                    )
                    MkwGhostButton(
                        text = stringResource(R.string.map_clear_route),
                        onClick = { state.clearSelection() },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onLoopChange(!loopRoute) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.route_loop_whole),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MkwKitTokens.Palette.ink,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    MkwSwitch(checked = loopRoute, onCheckedChange = onLoopChange)
                }
                PureFreeRoamConfigCard(
                    config = pureFreeRoamConfig,
                    onConfigChange = onPureFreeRoamConfigChange,
                    compact = true,
                )
                RoadMusicRuleModeSelector(
                    selectedMode = ruleMode,
                    officialKind = route.kind,
                    onSelectMode = onRuleModeChange,
                )
                val panelReducedMotion = MkwMotion.rememberPrefersReducedMotion()
                AnimatedVisibility(
                    visible = !pureFreeRoamConfig.enabled,
                    enter = if (panelReducedMotion) fadeIn(tween(120)) else fadeIn(tween(MkwMotion.StandardMs, easing = MkwMotion.EaseOut)) + expandVertically(MkwMotion.sizeSpring()),
                    exit = if (panelReducedMotion) fadeOut(tween(100)) else fadeOut(tween(MkwMotion.ExitMs, easing = MkwMotion.EaseOut)) + shrinkVertically(MkwMotion.sizeSpring()),
                ) {
                    RouteSuiteModeSelector(
                        selectedMode = suiteMode,
                        onSelectMode = onSuiteModeChange,
                    )
                }
            }
            else -> Text(stringResource(R.string.map_browse_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MapControl(
    symbol: String,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    // Every symbol the caller passes must have an icon: an unmapped one used to fall through to the
    // location glyph, so the edit button looked like a second "recentre" control.
    val icon = when (symbol) {
        "+" -> Icons.Filled.Add
        "−" -> Icons.Filled.Remove
        "✎" -> Icons.Filled.Edit
        "✕" -> Icons.Filled.Close
        else -> Icons.Filled.MyLocation
    }
    MkwIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        enabled = enabled,
        tone = if (active) MkwIconButtonTone.Rose else MkwIconButtonTone.Plain,
        visualSize = 36.dp,
        iconSize = 20.dp,
        modifier = Modifier.size(40.dp),
    )
}

@Composable
private fun MapCanvas(
    image: ImageBitmap, sourceCanvasWidth: Float, pins: List<MapAnchors.Anchor>, route: OfficialRoute?, state: WorldMapState,
    name: (String) -> String, modifier: Modifier,
    reachableOrigin: String? = null,
    reachableNeighbours: List<String> = emptyList(),
    onPinTap: (String) -> Unit = { state.selectCourse(it) },
    draft: List<Pair<String, String>> = emptyList(),
    editFrom: String? = null,
    draftEdgeConfigs: Map<Int, CustomRouteEdgeConfig> = emptyMap(),
    packDirectory: File? = null,
    onDraftEdgeTap: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val context = LocalContext.current.applicationContext
    val padding = with(density) { 24.dp.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    // Connections are drawn in the secondary colour so a real road never reads as an official route line.
    val connectionAccent = MaterialTheme.colorScheme.secondary.copy(alpha = .85f)
    val routeSprites = if (route != null || draft.isNotEmpty() || (state.editing && reachableOrigin != null)) {
        val sprites by produceState<MapArtworkCache.RouteSprites?>(null, context, packDirectory) {
            value = MapArtworkCache.loadRouteSprites(context, packDirectory)
        }
        sprites
    } else null

    val reducedMotion = rememberReducedMotion()

    // Non-flickering official highlight mask loading + true dual-layer crossfade
    val highlightRouteId = if (!state.editing) route?.id else null
    val loadedMask by produceState<Pair<String, ImageBitmap>?>(null, context, packDirectory, highlightRouteId) {
        value = if (highlightRouteId != null) {
            MapArtworkCache.loadHighlightMask(context, packDirectory, highlightRouteId)?.let { highlightRouteId to it }
        } else null
    }
    var currentMask by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    var outgoingMask by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    val currentMaskAlpha = remember { Animatable(0f) }
    val outgoingMaskAlpha = remember { Animatable(0f) }
    LaunchedEffect(highlightRouteId, loadedMask, reducedMotion) {
        val fadeDuration = if (reducedMotion) 0 else 360
        if (highlightRouteId == null || loadedMask == null) {
            outgoingMask?.let {
                launch {
                    outgoingMaskAlpha.animateTo(0f, tween(fadeDuration, easing = FastOutSlowInEasing))
                    outgoingMask = null
                }
            }
            if (currentMask != null) {
                currentMaskAlpha.animateTo(0f, tween(if (reducedMotion) 0 else 280, easing = FastOutSlowInEasing))
                currentMask = null
            }
        } else if (loadedMask?.first == highlightRouteId) {
            if (currentMask?.first != highlightRouteId) {
                val prev = currentMask
                val prevAlpha = currentMaskAlpha.value
                if (prev != null && prevAlpha > 0.01f && !reducedMotion) {
                    outgoingMask = prev
                    outgoingMaskAlpha.snapTo(prevAlpha)
                    launch {
                        outgoingMaskAlpha.animateTo(0f, tween(fadeDuration, easing = FastOutSlowInEasing))
                        outgoingMask = null
                    }
                } else {
                    outgoingMask = null
                    outgoingMaskAlpha.snapTo(0f)
                }
                currentMask = loadedMask
                currentMaskAlpha.snapTo(0f)
                currentMaskAlpha.animateTo(1f, tween(fadeDuration, easing = FastOutSlowInEasing))
            }
        }
    }

    // Dynamic spotlight shadow mask when inspecting a route, course, or actively editing a custom route
    val spotlightActive = loadedMask == null && (
        (!state.editing && (route != null || state.selectedCourseCode != null)) ||
            (state.editing && (editFrom != null || draft.isNotEmpty()))
        )
    val spotlightAlpha by animateFloatAsState(
        targetValue = if (spotlightActive) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 280, easing = FastOutSlowInEasing),
        label = "map-spotlight-mask",
    )

    // Smooth sequential reveal AND retraction for official routes (on switch or cancel)
    var renderedRoute by remember { mutableStateOf<OfficialRoute?>(route) }
    val routeReveal = remember { Animatable(if (route != null) 1f else 0f) }
    LaunchedEffect(route?.id, reducedMotion) {
        if (route != null) {
            if (renderedRoute != null && renderedRoute?.id != route.id && routeReveal.value > 0.01f && !reducedMotion) {
                routeReveal.animateTo(0f, tween(durationMillis = 150, easing = AppMotion.easeOut))
            }
            renderedRoute = route
            if (reducedMotion) {
                routeReveal.snapTo(1f)
            } else {
                routeReveal.snapTo(0f)
                val durationMs = ((route.stops.size - 1).coerceAtLeast(1) * 140).coerceIn(360, 920)
                routeReveal.animateTo(1f, tween(durationMillis = durationMs, easing = FastOutSlowInEasing))
            }
        } else if (renderedRoute != null) {
            if (!reducedMotion && routeReveal.value > 0.01f) {
                routeReveal.animateTo(0f, tween(durationMillis = 220, easing = AppMotion.easeOut))
            } else {
                routeReveal.snapTo(0f)
            }
            renderedRoute = null
        }
    }

    // Smooth radial growth AND retraction strictly for the active stop's reachable outgoing roads in edit mode (#2).
    // Outside edit mode, reachableOrigin is always null so tapping a course never displays outgoing segments.
    var renderedOrigin by remember { mutableStateOf(reachableOrigin) }
    var renderedNeighbours by remember { mutableStateOf(reachableNeighbours) }
    val neighbourReveal = remember { Animatable(if (reachableOrigin != null && reachableNeighbours.isNotEmpty()) 1f else 0f) }
    LaunchedEffect(reachableOrigin, reachableNeighbours, reducedMotion) {
        if (reachableOrigin != null) {
            renderedOrigin = reachableOrigin
            renderedNeighbours = reachableNeighbours
            if (reachableNeighbours.isNotEmpty()) {
                if (reducedMotion) {
                    neighbourReveal.snapTo(1f)
                } else {
                    neighbourReveal.snapTo(0f)
                    neighbourReveal.animateTo(1f, tween(durationMillis = 260, easing = MkwMotion.EaseOut))
                }
            } else {
                neighbourReveal.snapTo(0f)
            }
        } else if (renderedOrigin != null) {
            if (!reducedMotion && renderedNeighbours.isNotEmpty() && neighbourReveal.value > 0.01f) {
                neighbourReveal.animateTo(0f, tween(durationMillis = 160, easing = MkwMotion.EaseOut))
            } else {
                neighbourReveal.snapTo(0f)
            }
            renderedOrigin = null
            renderedNeighbours = emptyList()
        }
    }

    // Progressive growth for each newly appended edge in custom draft editing
    val draftLastReveal = remember { Animatable(1f) }
    var previousDraftSize by remember { mutableIntStateOf(draft.size) }
    LaunchedEffect(draft.size, reducedMotion) {
        if (draft.size > previousDraftSize && draft.isNotEmpty() && !reducedMotion) {
            draftLastReveal.snapTo(0f)
            draftLastReveal.animateTo(1f, tween(durationMillis = 260, easing = AppMotion.easeOut))
        } else {
            draftLastReveal.snapTo(1f)
        }
        previousDraftSize = draft.size
    }

    // Gentle breathing pulse for active/editing pin halo
    val activeFocusCode = if (state.editing) editFrom else state.selectedCourseCode
    val hasActivePin = activeFocusCode != null
    val infiniteTransition = rememberInfiniteTransition(label = "pin-halo-pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pin-pulse-phase",
    )

    // Camera animation: instant snap while finger dragging, flinging, or reducedMotion; smooth spring on buttons/fit/cancel
    val gestureScope = rememberCoroutineScope()
    var isPointerInteracting by remember { mutableStateOf(false) }
    var isFlinging by remember { mutableStateOf(false) }
    var flingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val directControl = isPointerInteracting || isFlinging || reducedMotion
    val zoomSpec = if (directControl) snap<Float>() else spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
    val panSpec = if (directControl) snap<Float>() else spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
    val animZoom by animateFloatAsState(state.camera.zoom, animationSpec = zoomSpec, label = "map-cam-zoom")
    val animCenterU by animateFloatAsState(state.camera.centerU, animationSpec = panSpec, label = "map-cam-u")
    val animCenterV by animateFloatAsState(state.camera.centerV, animationSpec = panSpec, label = "map-cam-v")

    val imageAspect = image.width.toFloat() / image.height
    val occurrences = route?.stops?.groupBy { it.stageCode }.orEmpty()
    BoxWithConstraints(
        modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF214E6B), Color(0xFF14354C)),
                ),
            )
            .clipToBounds()
            .pointerInput(state) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        flingJob?.cancel()
                        isFlinging = false
                        isPointerInteracting = false
                        if (state.camera.zoom >= 2.1f) {
                            state.reset()
                        } else {
                            val targetZoom = (state.camera.zoom * 2.2f).coerceIn(1.9f, MapTransform.MAX_ZOOM)
                            state.gesture(tapOffset, Offset.Zero, targetZoom / state.camera.zoom)
                        }
                    },
                )
            }
            .pointerInput(state) {
                val touchSlop = viewConfig.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    flingJob?.cancel()
                    isFlinging = false
                    var zoom = 1f
                    var panTotal = Offset.Zero
                    var pastTouchSlop = false
                    var usedMultiTouch = false
                    var lastCentroid = Offset.Zero
                    var cumulativePan = Offset.Zero
                    val velocityTracker = VelocityTracker()
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.count { it.pressed }
                            if (pointerCount >= 2) usedMultiTouch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (centroid != Offset.Unspecified) lastCentroid = centroid
                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                panTotal += panChange
                                val centroidSize = event.calculateCentroid(useCurrent = true)
                                if (centroidSize != Offset.Unspecified) lastCentroid = centroidSize
                                val motion = panTotal.getDistance()
                                val zoomMotion = kotlin.math.abs(1f - zoom) * 180f
                                if (motion > touchSlop || zoomMotion > touchSlop || pointerCount >= 2) {
                                    pastTouchSlop = true
                                    isPointerInteracting = true
                                }
                            }
                            if (pastTouchSlop) {
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    state.gesture(lastCentroid, panChange, zoomChange)
                                    val timeMs = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                                    if (!usedMultiTouch && pointerCount == 1) {
                                        cumulativePan += panChange
                                        velocityTracker.addPosition(timeMs, cumulativePan)
                                    }
                                }
                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })

                    if (pastTouchSlop) {
                        isPointerInteracting = false
                        val velocity = velocityTracker.calculateVelocity()
                        val vx = velocity.x
                        val vy = velocity.y
                        val speed = hypot(vx, vy)
                        if (!usedMultiTouch && speed > 140f) {
                            val anchor = if (lastCentroid != Offset.Unspecified) lastCentroid else Offset.Zero
                            flingJob = gestureScope.launch {
                                isFlinging = true
                                try {
                                    var curVx = vx
                                    var curVy = vy
                                    var lastNanos = withFrameNanos { it }
                                    while (hypot(curVx, curVy) > 24f) {
                                        val now = withFrameNanos { it }
                                        val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                        lastNanos = now
                                        val factor = exp(-5.0f * dt)
                                        state.gesture(anchor, Offset(curVx * dt, curVy * dt), 1f)
                                        curVx *= factor
                                        curVy *= factor
                                    }
                                } finally {
                                    isFlinging = false
                                }
                            }
                        }
                    }
                }
            },
    ) {
        val targetTransform = state.configure(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), imageAspect, padding)
        val transform = remember(targetTransform.viewportWidth, targetTransform.viewportHeight, imageAspect, animZoom, animCenterU, animCenterV, padding) {
            MapTransform.create(
                targetTransform.viewportWidth,
                targetTransform.viewportHeight,
                imageAspect,
                MapCamera(animZoom, animCenterU, animCenterV),
                padding,
            )
        }
        val selectedRouteStages = remember(route?.id) {
            route?.stops?.mapTo(linkedSetOf()) { it.stageCode.removeSuffix("_00") }.orEmpty()
        }
        val draftStages = remember(draft) {
            buildSet {
                draft.forEach { (a, b) ->
                    add(a.removeSuffix("_00"))
                    add(b.removeSuffix("_00"))
                }
            }
        }
        val reachableEditNeighbourSet = remember(state.editing, reachableOrigin, reachableNeighbours) {
            if (state.editing && reachableOrigin != null) {
                reachableNeighbours.mapTo(linkedSetOf()) { it.removeSuffix("_00") }
            } else {
                emptySet()
            }
        }
        val highlightedStages = remember(state.editing, selectedRouteStages, state.selectedCourseCode, editFrom, draftStages, reachableEditNeighbourSet) {
            buildSet {
                if (!state.editing) {
                    addAll(selectedRouteStages)
                    state.selectedCourseCode?.let { add(it.removeSuffix("_00")) }
                } else {
                    editFrom?.let { add(it.removeSuffix("_00")) }
                    addAll(draftStages)
                    addAll(reachableEditNeighbourSet)
                }
            }
        }
        var lastSpotlightStages by remember { mutableStateOf<Set<String>>(emptySet()) }
        var lastSpotlightFocus by remember { mutableStateOf<String?>(null) }
        if (highlightedStages.isNotEmpty()) {
            lastSpotlightStages = highlightedStages
            lastSpotlightFocus = activeFocusCode?.removeSuffix("_00")
        } else if (spotlightAlpha <= 0.01f) {
            lastSpotlightStages = emptySet()
            lastSpotlightFocus = null
        }
        val activeSpotlightStages = highlightedStages.ifEmpty { lastSpotlightStages }

        Canvas(Modifier.fillMaxSize().then(artworkFade(image))) {
            if (transform.drawnWidth > 0f && transform.drawnHeight > 0f) {
                withTransform({
                    translate(transform.left, transform.top)
                    scale(transform.drawnWidth / image.width, transform.drawnHeight / image.height, pivot = Offset.Zero)
                }) { drawImage(image, filterQuality = FilterQuality.High) }

                val outBmp = outgoingMask?.second
                val outAlpha = if (outBmp != null) outgoingMaskAlpha.value else 0f
                val curBmp = currentMask?.second
                val curAlpha = if (curBmp != null) currentMaskAlpha.value else 0f
                val combinedLayerAlpha = (outAlpha + curAlpha).coerceIn(0f, 1f)
                if (combinedLayerAlpha > 0.01f) {
                    val layerPaint = Paint().apply { alpha = combinedLayerAlpha }
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                    drawRect(Color(0x91060C1A))
                    if (outBmp != null && outAlpha > 0.01f) {
                        val normOut = (outAlpha / combinedLayerAlpha).coerceIn(0f, 1f)
                        withTransform({
                            translate(transform.left, transform.top)
                            scale(transform.drawnWidth / outBmp.width, transform.drawnHeight / outBmp.height, pivot = Offset.Zero)
                        }) {
                            drawImage(outBmp, alpha = normOut, blendMode = BlendMode.DstOut, filterQuality = FilterQuality.High)
                        }
                    }
                    if (curBmp != null && curAlpha > 0.01f) {
                        val normCur = (curAlpha / combinedLayerAlpha).coerceIn(0f, 1f)
                        withTransform({
                            translate(transform.left, transform.top)
                            scale(transform.drawnWidth / curBmp.width, transform.drawnHeight / curBmp.height, pivot = Offset.Zero)
                        }) {
                            drawImage(curBmp, alpha = normCur, blendMode = BlendMode.DstOut, filterQuality = FilterQuality.High)
                        }
                    }
                    drawContext.canvas.restore()
                }

                if (spotlightAlpha > 0.01f && activeSpotlightStages.isNotEmpty()) {
                    val layerPaint = Paint().apply { alpha = spotlightAlpha }
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                    drawRect(Color(0x66060C1A))
                    for (code in activeSpotlightStages) {
                        val pin = pins.firstOrNull { it.code.removeSuffix("_00") == code } ?: continue
                        val center = transform.project(pin.u, pin.v)
                        val focusCode = activeFocusCode?.removeSuffix("_00") ?: lastSpotlightFocus
                        val isPrimaryFocus = code == focusCode
                        val spotRadius = transform.sourcePixelsToScreen(
                            pin.artSize * if (isPrimaryFocus) 1.55f else 1.05f,
                            sourceCanvasWidth,
                        ).coerceAtLeast(36f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color.White,
                                0.55f to Color.White.copy(alpha = 0.78f),
                                1f to Color.Transparent,
                                center = center,
                                radius = spotRadius,
                            ),
                            radius = spotRadius,
                            center = center,
                            blendMode = BlendMode.DstOut,
                        )
                    }
                    drawContext.canvas.restore()
                }
            }
            val officialLegs = renderedRoute?.stops?.zipWithNext().orEmpty()
            val officialLegCount = officialLegs.size.coerceAtLeast(1)
            for ((legIdx, pair) in officialLegs.withIndex()) {
                val (from, to) = pair
                val a = pins.firstOrNull { it.code.removeSuffix("_00") == from.stageCode } ?: continue
                val b = pins.firstOrNull { it.code.removeSuffix("_00") == to.stageCode } ?: continue
                val legProgress = ((routeReveal.value * officialLegCount - legIdx) * 1.15f).coerceIn(0f, 1f)
                if (legProgress > 0.01f) {
                    drawRouteConnection(
                        start = transform.project(a.u, a.v),
                        end = transform.project(b.u, b.v),
                        startRadius = transform.sourcePixelsToScreen(a.artSize * .38f, sourceCanvasWidth),
                        endRadius = transform.sourcePixelsToScreen(b.artSize * .38f, sourceCanvasWidth),
                        roadSprite = routeSprites?.officialRoad,
                        arrowSprite = routeSprites?.arrowGlow,
                        roadThickness = transform.sourcePixelsToScreen(42f, sourceCanvasWidth),
                        arrowSize = transform.sourcePixelsToScreen(48f, sourceCanvasWidth),
                        arrowFraction = .56f,
                        progress = legProgress,
                        fallbackColor = accent,
                        fallbackStroke = transform.sourcePixelsToScreen(8f, sourceCanvasWidth),
                        fallbackArrowSize = transform.sourcePixelsToScreen(28f, sourceCanvasWidth),
                    )
                }
            }
            // Reachable outgoing road connections drawn ONLY in edit/connecting mode for the active stop (#2).
            val activeOrigin = renderedOrigin
            if (activeOrigin != null && renderedNeighbours.isNotEmpty() && neighbourReveal.value > 0.01f) {
                val origin = pins.firstOrNull { it.code.removeSuffix("_00") == activeOrigin }
                if (origin != null) {
                    val start = transform.project(origin.u, origin.v)
                    val startRadius = transform.sourcePixelsToScreen(origin.artSize * .38f, sourceCanvasWidth)
                    for ((nIdx, target) in renderedNeighbours.withIndex()) {
                        val pin = pins.firstOrNull { it.code.removeSuffix("_00") == target } ?: continue
                        val stagger = (nIdx * 0.05f).coerceAtMost(0.24f)
                        val prog = ((neighbourReveal.value * (1f + stagger)) - stagger).coerceIn(0f, 1f)
                        if (prog > 0.01f) {
                            drawRouteConnection(
                                start = start,
                                end = transform.project(pin.u, pin.v),
                                startRadius = startRadius,
                                endRadius = transform.sourcePixelsToScreen(pin.artSize * .38f, sourceCanvasWidth),
                                roadSprite = routeSprites?.neighbourRoad,
                                arrowSprite = routeSprites?.arrowSharp,
                                roadThickness = transform.sourcePixelsToScreen(30f, sourceCanvasWidth),
                                arrowSize = transform.sourcePixelsToScreen(36f, sourceCanvasWidth),
                                arrowFraction = .62f,
                                alpha = .92f,
                                progress = prog,
                                fallbackColor = connectionAccent,
                                fallbackStroke = transform.sourcePixelsToScreen(5f, sourceCanvasWidth),
                            )
                        }
                    }
                }
            }
            // User custom route connections drawn last so the draft ribbon sits above base road links.
            if (draft.isNotEmpty()) {
                for ((edgeIdx, pair) in draft.withIndex()) {
                    val (from, to) = pair
                    val a = pins.firstOrNull { it.code.removeSuffix("_00") == from } ?: continue
                    val b = pins.firstOrNull { it.code.removeSuffix("_00") == to } ?: continue
                    val prog = if (edgeIdx == draft.lastIndex) draftLastReveal.value else 1f
                    if (prog > 0.01f) {
                        drawRouteConnection(
                            start = transform.project(a.u, a.v),
                            end = transform.project(b.u, b.v),
                            startRadius = transform.sourcePixelsToScreen(a.artSize * .38f, sourceCanvasWidth),
                            endRadius = transform.sourcePixelsToScreen(b.artSize * .38f, sourceCanvasWidth),
                            roadSprite = routeSprites?.draftRoad,
                            arrowSprite = routeSprites?.arrowGlow,
                            roadThickness = transform.sourcePixelsToScreen(44f, sourceCanvasWidth),
                            arrowSize = transform.sourcePixelsToScreen(46f, sourceCanvasWidth),
                            arrowFraction = .55f,
                            progress = prog,
                            fallbackColor = accent,
                            fallbackStroke = transform.sourcePixelsToScreen(9f, sourceCanvasWidth),
                        )
                    }
                }
            }
            // Pulsing halo ring around currently selected or active-editing course pin
            if (hasActivePin) {
                val focusCode = activeFocusCode?.removeSuffix("_00")
                val focusPin = focusCode?.let { fc -> pins.firstOrNull { it.code.removeSuffix("_00") == fc } }
                if (focusPin != null) {
                    val center = transform.project(focusPin.u, focusPin.v)
                    val baseR = transform.sourcePixelsToScreen(focusPin.artSize * .56f, sourceCanvasWidth).coerceAtLeast(14f)
                    val ringRadius = baseR * (1f + pulsePhase * 0.45f)
                    val ringAlpha = (1f - pulsePhase) * 0.42f
                    drawCircle(
                        color = accent.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        center = center,
                    )
                }
            }
        }
        for (pin in pins) {
            val code = pin.code.removeSuffix("_00")
            val point = transform.project(pin.u, pin.v)
            val iconPixels = transform.sourcePixelsToScreen(pin.artSize, sourceCanvasWidth)
            val iconSize = with(density) { iconPixels.toDp() }
            val visits = if (state.editing) emptyList() else occurrences[code].orEmpty()
            val pinVisual = computeMapPinVisualState(
                stageCode = code,
                editing = state.editing,
                selectedCourseCode = state.selectedCourseCode,
                selectedRouteStages = selectedRouteStages,
                editFrom = editFrom,
                draftStages = draftStages,
                reachableEditNeighbours = reachableEditNeighbourSet,
            )
            val isActive = pinVisual.isSelectedOrActive
            val selectionScale by animateFloatAsState(
                targetValue = if (isActive) 1.24f else 1f,
                animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "course-selection",
            )
            val outlineAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = tween(durationMillis = if (reducedMotion) 0 else MkwMotion.MicroMs, easing = MkwMotion.EaseOut),
                label = "course-outline-alpha",
            )
            val dimProgress by animateFloatAsState(
                targetValue = if (pinVisual.isDimmed) 1f else 0f,
                animationSpec = tween(durationMillis = if (reducedMotion) 0 else 280, easing = FastOutSlowInEasing),
                label = "course-dim",
            )
            val badgeScale by animateFloatAsState(
                targetValue = if (visits.isNotEmpty()) 1f else 0f,
                animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                label = "visit-badge-scale",
            )
            val description = if (visits.isEmpty()) name(code) else stringResource(R.string.map_pin_occurrences, name(code), visits.joinToString(stringResource(R.string.map_stop_separator)) { it.ordinal.toString() })
            Box(
                Modifier.offset { IntOffset((point.x - iconPixels / 2f).roundToInt(), (point.y - iconPixels / 2f).roundToInt()) }
                    .size(iconSize)
                    .zIndex(if (isActive) 1f else 0f)
                    .clickable(role = Role.Button) { onPinTap(code) }
                    .semantics { contentDescription = description; this.selected = isActive },
                contentAlignment = Alignment.Center,
            ) {
                StageArt.resolve(code)?.let { art ->
                    ArtworkSlot(Modifier.fillMaxSize()) { edgePx ->
                        val artwork = rememberResourceArtwork(art, edgePx)
                        if (artwork != null) {
                            val painter = remember(artwork) { BitmapPainter(artwork.bitmap, filterQuality = FilterQuality.High) }
                            val smoothOutline = rememberSmoothIconOutline(
                                code = code,
                                bitmap = artwork.bitmap,
                                needed = isActive || outlineAlpha > 0.005f,
                            )
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(artworkFade(artwork.bitmap))
                                    .graphicsLayer { scaleX = selectionScale; scaleY = selectionScale }
                                    .drawWithContent {
                                        val padX = smoothOutline?.let { size.width * it.padFracX } ?: 0f
                                        val padY = smoothOutline?.let { size.height * it.padFracY } ?: 0f
                                        val layerRect = Rect(
                                            left = -padX,
                                            top = -padY,
                                            right = size.width + padX,
                                            bottom = size.height + padY,
                                        )
                                        val applyDimLayer = dimProgress > 0.005f
                                        if (applyDimLayer) {
                                            // Composite outline + icon atomically so both dim together (#3).
                                            val layerPaint = Paint().apply {
                                                alpha = (1f - 0.16f * dimProgress).coerceIn(0f, 1f)
                                            }
                                            drawContext.canvas.saveLayer(layerRect, layerPaint)
                                        }
                                        if (smoothOutline != null && outlineAlpha > 0.005f) {
                                            val dstW = size.width + padX * 2f
                                            val dstH = size.height + padY * 2f
                                            withTransform({
                                                translate(left = -padX, top = -padY)
                                                scale(
                                                    scaleX = dstW / smoothOutline.bitmap.width.coerceAtLeast(1),
                                                    scaleY = dstH / smoothOutline.bitmap.height.coerceAtLeast(1),
                                                    pivot = Offset.Zero,
                                                )
                                            }) {
                                                drawImage(
                                                    image = smoothOutline.bitmap,
                                                    alpha = outlineAlpha.coerceIn(0f, 1f),
                                                    filterQuality = FilterQuality.High,
                                                )
                                            }
                                        }
                                        drawContent()
                                        if (applyDimLayer) {
                                            drawRect(
                                                color = Color(0xFF060C1A).copy(alpha = 0.56f * dimProgress),
                                                topLeft = layerRect.topLeft,
                                                size = layerRect.size,
                                                blendMode = BlendMode.SrcAtop,
                                            )
                                            drawContext.canvas.restore()
                                        }
                                    },
                            )
                        }
                    }
                }
                if (visits.isNotEmpty() && badgeScale > 0.02f) {
                    Surface(
                        Modifier.align(Alignment.TopEnd)
                            .offset(x = iconSize * .1f, y = -iconSize * .1f)
                            .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale; alpha = badgeScale.coerceIn(0f, 1f) },
                        shape = CircleShape,
                        color = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(visits.joinToString("·") { it.ordinal.toString() }, Modifier.padding(horizontal = iconSize * .08f, vertical = iconSize * .025f),
                            fontSize = (iconSize.value * .22f).sp, lineHeight = (iconSize.value * .25f).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (draft.isNotEmpty()) {
            val badgePx = with(density) { 26.dp.toPx() }
            for ((edgeIdx, edge) in draft.withIndex()) {
                val a = pins.firstOrNull { it.code.removeSuffix("_00") == edge.first } ?: continue
                val b = pins.firstOrNull { it.code.removeSuffix("_00") == edge.second } ?: continue
                val mid = transform.project((a.u + b.u) / 2f, (a.v + b.v) / 2f)
                val cfg = draftEdgeConfigs[edgeIdx]
                val musicOn = cfg?.musicEnabled ?: true
                val badgeVisibleProgress = if (edgeIdx == draft.lastIndex) {
                    ((draftLastReveal.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
                } else 1f
                if (badgeVisibleProgress > 0.02f) {
                    Surface(
                        onClick = { onDraftEdgeTap(edgeIdx) },
                        shape = CircleShape,
                        color = if (musicOn) MkwKitTokens.Palette.rose else MkwKitTokens.Palette.card,
                        contentColor = if (musicOn) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                        border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .offset { IntOffset((mid.x - badgePx / 2f).roundToInt(), (mid.y - badgePx / 2f).roundToInt()) }
                            .graphicsLayer {
                                scaleX = badgeVisibleProgress
                                scaleY = badgeVisibleProgress
                                alpha = badgeVisibleProgress
                            }
                            .size(26.dp)
                            .zIndex(2f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (musicOn) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                                contentDescription = stringResource(R.string.route_pick_edge_track),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRouteConnection(
    start: Offset,
    end: Offset,
    startRadius: Float,
    endRadius: Float,
    roadSprite: ImageBitmap?,
    arrowSprite: ImageBitmap?,
    roadThickness: Float,
    arrowSize: Float,
    arrowFraction: Float,
    alpha: Float = 1f,
    progress: Float = 1f,
    fallbackColor: Color,
    fallbackStroke: Float,
    fallbackArrowSize: Float = 0f,
) {
    val difference = end - start
    val length = difference.getDistance()
    if (length <= startRadius + endRadius) return
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (clampedProgress <= 0f) return
    val unit = difference / length
    val fullSegLen = length - startRadius - endRadius
    val visibleSegLen = fullSegLen * clampedProgress
    if (visibleSegLen < 1f) return
    val segStart = start + unit * startRadius
    val visibleSegEnd = segStart + unit * visibleSegLen
    if (roadSprite == null) {
        drawLine(fallbackColor.copy(alpha = fallbackColor.alpha * alpha), segStart, visibleSegEnd, strokeWidth = fallbackStroke, cap = StrokeCap.Round)
        val arrowAlpha = ((clampedProgress - (arrowFraction - 0.12f)) / 0.2f).coerceIn(0f, 1f) * alpha
        if (fallbackArrowSize > 0f && arrowAlpha > 0.01f) {
            val middle = segStart + unit * (fullSegLen * arrowFraction)
            val perpendicular = Offset(-unit.y, unit.x)
            val arrow = Path().apply {
                val tip = middle + unit * fallbackArrowSize
                val left = middle - unit * fallbackArrowSize * .7f + perpendicular * fallbackArrowSize * .7f
                val right = middle - unit * fallbackArrowSize * .7f - perpendicular * fallbackArrowSize * .7f
                moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
            }
            drawPath(arrow, fallbackColor.copy(alpha = fallbackColor.alpha * arrowAlpha))
        }
        return
    }
    val angleDeg = Math.toDegrees(atan2(unit.y.toDouble(), unit.x.toDouble())).toFloat()
    val tileH = roadThickness.coerceAtLeast(4f)
    val tileW = (tileH * roadSprite.width.toFloat() / roadSprite.height.toFloat()).coerceAtLeast(tileH)
    val topY = (-tileH / 2f).roundToInt()
    val dstH = tileH.roundToInt().coerceAtLeast(1)
    withTransform({
        translate(left = segStart.x, top = segStart.y)
        rotate(degrees = angleDeg, pivot = Offset.Zero)
    }) {
        var offsetX = 0f
        while (offsetX < visibleSegLen - .5f) {
            val chunkW = minOf(tileW, visibleSegLen - offsetX)
            val srcW = ((chunkW / tileW) * roadSprite.width).roundToInt().coerceIn(1, roadSprite.width)
            val dstStart = offsetX.roundToInt()
            val dstEnd = (offsetX + chunkW).roundToInt()
            val dstW = (dstEnd - dstStart).coerceAtLeast(1)
            drawImage(
                image = roadSprite,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(srcW, roadSprite.height),
                dstOffset = IntOffset(dstStart, topY),
                dstSize = IntSize(dstW, dstH),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
            offsetX += chunkW
        }
        val arrowRevealAlpha = ((clampedProgress - (arrowFraction - 0.12f)) / 0.22f).coerceIn(0f, 1f)
        if (arrowSprite != null && arrowSize >= 4f && fullSegLen > arrowSize * .6f && arrowRevealAlpha > 0.01f) {
            val px = arrowSize.roundToInt().coerceAtLeast(1)
            val arrowX = (fullSegLen * arrowFraction - px / 2f).roundToInt()
            drawImage(
                image = arrowSprite,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(arrowSprite.width, arrowSprite.height),
                dstOffset = IntOffset(arrowX, (-px / 2f).roundToInt()),
                dstSize = IntSize(px, px),
                alpha = alpha * arrowRevealAlpha,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

private const val EDT_INF = 1_000_000f
private const val OUTLINE_WORK_MAX_EDGE = 256
private const val OUTLINE_STROKE_RATIO = 0.044f
private const val OUTLINE_AA_FEATHER_PX = 2.0f

private val smoothIconOutlineCache = LruCache<String, SmoothIconOutline>(48)

internal data class SmoothIconOutline(
    val bitmap: ImageBitmap,
    val padFracX: Float,
    val padFracY: Float,
)

/**
 * 1D squared Euclidean distance transform (Felzenszwalb & Huttenlocher, 2012):
 * computes `out[q] = min_p ((q - p)^2 + f[p])` in O(n) time without Inf-Inf precision issues.
 */
internal fun edt1d(
    f: FloatArray,
    out: FloatArray,
    n: Int,
    v: IntArray,
    z: FloatArray,
) {
    var k = -1
    for (q in 0 until n) {
        val fq = f[q]
        if (fq >= EDT_INF) continue
        if (k < 0) {
            k = 0
            v[0] = q
            z[0] = Float.NEGATIVE_INFINITY
            z[1] = Float.POSITIVE_INFINITY
            continue
        }
        var s = 0f
        while (k >= 0) {
            val vk = v[k]
            s = (((fq.toDouble() + q.toDouble() * q) - (f[vk].toDouble() + vk.toDouble() * vk)) / (2.0 * (q - vk))).toFloat()
            if (s <= z[k]) {
                k--
            } else {
                break
            }
        }
        k++
        v[k] = q
        z[k] = if (k == 0) Float.NEGATIVE_INFINITY else s
        z[k + 1] = Float.POSITIVE_INFINITY
    }
    if (k < 0) {
        for (q in 0 until n) out[q] = EDT_INF
        return
    }
    var idx = 0
    for (q in 0 until n) {
        while (z[idx + 1] < q) idx++
        val d = (q - v[idx]).toFloat()
        out[q] = d * d + f[v[idx]]
    }
}

/**
 * Computes a smooth 2D Euclidean distance field outside an icon's alpha silhouette (`alpha >= 0.45f`),
 * followed by two separable `[1, 2, 1]/4` Gaussian smoothing passes that preserve linear distances
 * while eliminating pixel-grid stair-step aliasing along arbitrary curves and corners (#3).
 */
internal fun computeSmoothedEuclideanDistance2d(
    alpha: FloatArray,
    width: Int,
    height: Int,
    maxCap: Float,
): FloatArray {
    val total = width * height
    val grid = FloatArray(total) { i -> if (alpha[i] >= 0.45f) 0f else EDT_INF }
    val maxDim = maxOf(width, height).coerceAtLeast(1)
    val fBuf = FloatArray(maxDim)
    val outBuf = FloatArray(maxDim)
    val vBuf = IntArray(maxDim)
    val zBuf = FloatArray(maxDim + 1)

    for (y in 0 until height) {
        val rowOff = y * width
        for (x in 0 until width) fBuf[x] = grid[rowOff + x]
        edt1d(fBuf, outBuf, width, vBuf, zBuf)
        for (x in 0 until width) grid[rowOff + x] = outBuf[x]
    }

    for (x in 0 until width) {
        for (y in 0 until height) fBuf[y] = grid[y * width + x]
        edt1d(fBuf, outBuf, height, vBuf, zBuf)
        for (y in 0 until height) {
            val sq = outBuf[y]
            grid[y * width + x] = if (sq >= EDT_INF) maxCap else sqrt(sq).coerceAtMost(maxCap)
        }
    }

    val scratch = FloatArray(total)
    repeat(2) {
        for (y in 0 until height) {
            val rowOff = y * width
            for (x in 0 until width) {
                val xm = if (x > 0) x - 1 else 0
                val xp = if (x + 1 < width) x + 1 else width - 1
                scratch[rowOff + x] = (grid[rowOff + xm] + 2f * grid[rowOff + x] + grid[rowOff + xp]) * 0.25f
            }
        }
        for (y in 0 until height) {
            val ym = if (y > 0) y - 1 else 0
            val yp = if (y + 1 < height) y + 1 else height - 1
            val r0 = ym * width
            val r1 = y * width
            val r2 = yp * width
            for (x in 0 until width) {
                grid[r1 + x] = (scratch[r0 + x] + 2f * scratch[r1 + x] + scratch[r2 + x]) * 0.25f
            }
        }
    }
    return grid
}

private fun buildSmoothWhiteOutline(src: ImageBitmap): SmoothIconOutline? = runCatching {
    val rawAndroid = src.asAndroidBitmap()
    val swSrc = if (rawAndroid.config == Bitmap.Config.HARDWARE) {
        rawAndroid.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        rawAndroid
    }
    val origW = swSrc.width.coerceAtLeast(1)
    val origH = swSrc.height.coerceAtLeast(1)
    val longest = maxOf(origW, origH)
    val scale = OUTLINE_WORK_MAX_EDGE.toFloat() / longest.toFloat()
    val workW = (origW * scale).roundToInt().coerceAtLeast(1)
    val workH = (origH * scale).roundToInt().coerceAtLeast(1)
    val working = if (workW != origW || workH != origH) {
        Bitmap.createScaledBitmap(swSrc, workW, workH, true)
    } else {
        swSrc
    }
    try {
        val strokeRadius = maxOf(workW, workH) * OUTLINE_STROKE_RATIO
        val pad = ceil(strokeRadius + OUTLINE_AA_FEATHER_PX + 3f).toInt().coerceAtLeast(4)
        val pw = workW + pad * 2
        val ph = workH + pad * 2
        val srcPixels = IntArray(workW * workH)
        working.getPixels(srcPixels, 0, workW, 0, 0, workW, workH)
        val alpha = FloatArray(pw * ph)
        for (y in 0 until workH) {
            val dstRow = (y + pad) * pw + pad
            val srcRow = y * workW
            for (x in 0 until workW) {
                alpha[dstRow + x] = ((srcPixels[srcRow + x] ushr 24) and 0xFF) / 255f
            }
        }
        val cap = strokeRadius + OUTLINE_AA_FEATHER_PX + 4f
        val dist = computeSmoothedEuclideanDistance2d(alpha, pw, ph, maxCap = cap)
        val outPixels = IntArray(pw * ph)
        val halfFeather = OUTLINE_AA_FEATHER_PX * 0.5f
        for (i in dist.indices) {
            val cov = ((strokeRadius + halfFeather - dist[i]) / OUTLINE_AA_FEATHER_PX).coerceIn(0f, 1f)
            if (cov > 0f) {
                val smoothA = cov * cov * (3f - 2f * cov)
                val aByte = (smoothA * 255f).roundToInt().coerceIn(0, 255)
                if (aByte > 0) {
                    outPixels[i] = (aByte shl 24) or 0x00FFFFFF
                }
            }
        }
        val outBmp = Bitmap.createBitmap(outPixels, pw, ph, Bitmap.Config.ARGB_8888)
        SmoothIconOutline(
            bitmap = outBmp.asImageBitmap(),
            padFracX = pad.toFloat() / workW.toFloat(),
            padFracY = pad.toFloat() / workH.toFloat(),
        )
    } finally {
        if (working !== swSrc) working.recycle()
        if (swSrc !== rawAndroid) swSrc.recycle()
    }
}.getOrNull()

@Composable
private fun rememberSmoothIconOutline(
    code: String,
    bitmap: ImageBitmap,
    needed: Boolean,
): SmoothIconOutline? {
    val cacheKey = remember(code, bitmap) {
        "$code@${bitmap.width}x${bitmap.height}#${System.identityHashCode(bitmap)}"
    }
    val cached = remember(cacheKey) { smoothIconOutlineCache.get(cacheKey) }
    if (cached != null) return cached
    if (!needed) return null
    val generated by produceState<SmoothIconOutline?>(null, cacheKey, bitmap) {
        value = smoothIconOutlineCache.get(cacheKey) ?: withContext(Dispatchers.Default) {
            buildSmoothWhiteOutline(bitmap)?.also { outline ->
                smoothIconOutlineCache.put(cacheKey, outline)
            }
        }
    }
    return generated
}
