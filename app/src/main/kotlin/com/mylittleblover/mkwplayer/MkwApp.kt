package com.mylittleblover.mkwplayer

import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import com.mylittleblover.mkwplayer.ui.unpadHorizontal
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomNavDock
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDangerButton
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwFloatingPanel
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButtonTone
import com.mylittleblover.mkwplayer.ui.kit.MkwInsetBox
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwMotion
import com.mylittleblover.mkwplayer.ui.kit.MkwNavDestination
import com.mylittleblover.mkwplayer.ui.kit.mkwPressScale
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSegmentedBar
import com.mylittleblover.mkwplayer.ui.kit.MkwSlider
import com.mylittleblover.mkwplayer.ui.kit.MkwSliderMarker
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwSwitch
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import com.mylittleblover.mkwplayer.ui.kit.MkwUtilityDock
import com.mylittleblover.mkwplayer.ui.kit.MkwUtilityTile
import com.mylittleblover.mkwplayer.ui.markers.toSliderMarkers
import com.mylittleblover.mkwplayer.ui.tabIconVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.asImageBitmap
import com.mylittleblover.mkwplayer.ui.art.ArtworkSlot
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.ui.art.FadingArtwork
import com.mylittleblover.mkwplayer.ui.art.PackArtwork
import com.mylittleblover.mkwplayer.ui.art.ResourceArtwork
import com.mylittleblover.mkwplayer.ui.art.rememberArtworkRatio
import com.mylittleblover.mkwplayer.ui.art.rememberResourceArtwork
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.player.LinearQueue
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.ui.NavState
import com.mylittleblover.mkwplayer.ui.Tokens
import com.mylittleblover.mkwplayer.ui.Tab
import com.mylittleblover.mkwplayer.ui.UiMapping
import com.mylittleblover.mkwplayer.ui.detail.DetailScreen
import com.mylittleblover.mkwplayer.ui.library.LibraryScreen
import com.mylittleblover.mkwplayer.ui.play.PlayScreen
import com.mylittleblover.mkwplayer.ui.AppHeader
import com.mylittleblover.mkwplayer.ui.tabTitle
import com.mylittleblover.mkwplayer.ui.TabIcon
import com.mylittleblover.mkwplayer.ui.settings.AppSettingsScreen
import com.mylittleblover.mkwplayer.i18n.AppLanguageSettings
import com.mylittleblover.mkwplayer.ui.routes.OfficialMapScreen
import com.mylittleblover.mkwplayer.ui.routes.WorldMapState
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.LocalizedNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.mylittleblover.mkwplayer.ui.routes.RoutesScreen
import com.mylittleblover.mkwplayer.ui.onboarding.OnboardingStore
import com.mylittleblover.mkwplayer.ui.onboarding.WelcomeOnboardingScreen
import com.mylittleblover.mkwplayer.ui.settings.importItems
import com.mylittleblover.mkwplayer.ui.settings.settingsItems

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
    pendingEnterCeremony: Boolean = false,
    onConsumedEnterCeremony: () -> Unit = {},
) {
    var nav by rememberSaveable(stateSaver = listSaver<NavState, String>(save = { it.snapshot() }, restore = { NavState.restore(it) })) { mutableStateOf(NavState()) }
    val mapState = rememberSaveable(saver = WorldMapState.Saver) { WorldMapState() }
    val pageStateHolder = rememberSaveableStateHolder()
    var mediaCategory by rememberSaveable { mutableIntStateOf(-1) }
    var mediaQuery by rememberSaveable { mutableStateOf("") }

    // The pack picker. It is the only import path a normal listener needs: one file carries the audio, the
    // stage structures and the timings. Kept here rather than inside a page so the library's empty state and
    // the console's import button open the same picker.
    val packPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) model.importPack(uri)
    }
    val openPack = { packPicker.launch(arrayOf("*/*")) }

    val repickPackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) model.resumePackImportWithRepick(uri)
    }
    val openRepickPack = { repickPackPicker.launch(arrayOf("*/*")) }

    // The single-file picker, kept because a listener who has a handful of .bwav files should not have to
    // build a pack to hear them.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) model.openFlow(uris)
    }
    val openFiles = { filePicker.launch(arrayOf("audio/*", "*/*")) }

    val context = LocalContext.current
    val packDir = model.packDirectory
    SideEffect {
        if (PackArtwork.activePackDirectory.value != packDir) {
            PackArtwork.activePackDirectory.value = packDir
        }
    }
    LaunchedEffect(packDir, ui.libraryGeneration) {
        PackArtwork.activePackDirectory.value = packDir
        if (packDir != null) {
            withContext(Dispatchers.IO) {
                PackArtwork.warmArtworkIndex(packDir)
            }
        }
    }

    fun isNotificationPermissionGranted(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    var notificationGranted by remember { mutableStateOf(isNotificationPermissionGranted()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val effectiveGranted = granted || isNotificationPermissionGranted()
        notificationGranted = effectiveGranted
        model.onNotificationPermissionResult(effectiveGranted)
    }

    LaunchedEffect(ui.needsNotificationPermission) {
        if (ui.needsNotificationPermission && android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pendingEnterCeremony, ui.unlock?.canEnter, ui.unlock?.ceremonyCompleted) {
        if (pendingEnterCeremony) {
            val unlock = ui.unlock
            if (unlock != null) {
                if (unlock.canEnter && !unlock.ceremonyCompleted && !unlock.ceremonyPlaying) {
                    model.startSpecialCupUnlockCeremony()
                }
                onConsumedEnterCeremony()
            }
        }
    }

    var onboardingChecked by remember { mutableStateOf(false) }
    var onboardingCompleted by remember { mutableStateOf(true) }
    LaunchedEffect(context, ui.libraryReady, ui.pack, ui.libraryGeneration) {
        if (!ui.libraryReady) return@LaunchedEffect
        val done = withContext(Dispatchers.IO) {
            if (ui.pack != null) {
                if (!OnboardingStore.isCompleted(context)) {
                    OnboardingStore.markCompleted(context)
                }
                true
            } else {
                OnboardingStore.isCompleted(context)
            }
        }
        onboardingCompleted = done
        onboardingChecked = true
    }
    val showOnboarding = onboardingChecked && !onboardingCompleted && ui.pack == null

    val ceremonyPlaying = ui.unlock?.ceremonyPlaying == true
    var previewUnlockVfx by remember { mutableStateOf(false) }
    LaunchedEffect(previewUnlockVfx) {
        if (previewUnlockVfx) {
            kotlinx.coroutines.delay(6500L)
            previewUnlockVfx = false
        }
    }
    BackHandler(enabled = nav.canGoBack && !ceremonyPlaying && !showOnboarding) { nav = nav.back() ?: nav }
    BackHandler(enabled = ceremonyPlaying) {}
    val language = AppLanguageSettings.current(context)
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    // The master trim is a device preference, read once and pushed to the running engine when it changes.
    // `remember` on the context keeps it stable across recompositions; the session owns the live value.
    var outputTrim by remember(context) {
        mutableStateOf(com.mylittleblover.mkwplayer.audio.OutputTrimSettings.current(context))
    }
    LaunchedEffect(outputTrim) { model.applyOutputTrim(outputTrim) }
    var playbackBehavior by remember(context) {
        mutableStateOf(com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings.current(context))
    }
    LaunchedEffect(playbackBehavior) { model.applyPlaybackBehavior(playbackBehavior) }
    val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val discoveryState by discovery.state.collectAsStateWithLifecycle()
    DisposableEffect(lifecycleOwner, context, discovery, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = isNotificationPermissionGranted()
                notificationGranted = granted
                if (granted && com.mylittleblover.mkwplayer.player.PlaybackHolder.unlock().pendingUnlockNotification) {
                    model.onNotificationPermissionResult(true)
                }
                discovery.reloadRecent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val coroutineScope = rememberCoroutineScope()
    var showSleepTimerSheet by rememberSaveable { mutableStateOf(false) }
    var showLockedMysteryDialog by rememberSaveable { mutableStateOf(false) }
    var backupStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBackupPayload by remember { mutableStateOf<com.mylittleblover.mkwplayer.library.UserBackupPayload?>(null) }
    val exportFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) model.setExportFolderUri(uri)
    }
    val allTrackMarkers by model.trackMarkers.collectAsStateWithLifecycle()
    val staffCreditsUnlocked = ui.staffCreditsUnlocked ||
        ui.unlockTestMode == com.mylittleblover.mkwplayer.unlock.UnlockTestMode.FORCE_UNLOCKED
    var shownStaffCreditsToastEvent by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(ui.staffCreditsToastEvent) {
        if (ui.staffCreditsToastEvent > shownStaffCreditsToastEvent) {
            shownStaffCreditsToastEvent = ui.staffCreditsToastEvent
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.staff_credits_unlocked_toast),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
    val inInteractiveDetail = nav.detail is NavState.Detail.Interactive || nav.detail == NavState.Detail.Flow
    var wasInInteractiveDetail by remember { mutableStateOf(inInteractiveDetail) }
    LaunchedEffect(inInteractiveDetail) {
        if (wasInInteractiveDetail && !inInteractiveDetail) {
            if (model.onExitedInteractiveMode(languageTag)) {
                nav = nav.open(NavState.Detail.Track)
            }
        }
        wasInInteractiveDetail = inInteractiveDetail
    }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = com.mylittleblover.mkwplayer.library.UserBackupManager
                        .exportAllFromContext(context, BuildConfig.VERSION_NAME).getOrThrow()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error(context.getString(R.string.settings_backup_err_open_output))
                }
            }
            backupStatusMessage = result.fold(
                onSuccess = { context.getString(R.string.settings_backup_export_ok) },
                onFailure = { context.getString(R.string.settings_backup_failed, it.message ?: context.getString(R.string.settings_backup_err_unknown)) },
            )
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error(context.getString(R.string.settings_backup_err_read_input))
                    com.mylittleblover.mkwplayer.library.UserBackupManager.parseBackupJson(raw).getOrThrow()
                }
            }
            result.onSuccess { payload ->
                pendingBackupPayload = payload
            }.onFailure { err ->
                backupStatusMessage = context.getString(R.string.settings_backup_failed, err.message ?: context.getString(R.string.settings_backup_err_unknown))
            }
        }
    }
    val m3uExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val lists = discoveryState.playlists
            if (lists.isEmpty()) {
                backupStatusMessage = context.getString(R.string.settings_backup_m3u_empty)
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val m3u = com.mylittleblover.mkwplayer.library.UserBackupManager.exportPlaylistsToM3u(
                        playlists = lists,
                        catalog = ui.jukebox,
                    ) { rawId ->
                        when (val ref = com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(rawId)) {
                            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> {
                                val title = model.localizedNames?.name(ref.logicalCode, languageTag) ?: ref.logicalCode
                                title to -1
                            }
                            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox -> {
                                val item = ui.jukebox.itemsByKey[ref.trackKey]
                                val sec = item?.durationMs?.div(1000L)?.toInt() ?: -1
                                (item?.displayTitle(languageTag) ?: ref.trackKey) to sec
                            }
                            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack -> {
                                val tr = model.extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId }
                                (tr?.localizedName(languageTag) ?: ref.trackId) to (tr?.main?.frames?.div(48000)?.toInt() ?: -1)
                            }
                            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack -> {
                                val tr = model.extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId }
                                (tr?.localizedName(languageTag) ?: ref.trackId) to (tr?.main?.frames?.div(48000)?.toInt() ?: -1)
                            }
                            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack -> {
                                val tr = model.extraCatalog.otherMusic.firstOrNull { it.id == ref.extraId }
                                (tr?.localizedName(languageTag) ?: ref.extraId) to (tr?.durationMs?.div(1000L)?.toInt() ?: -1)
                            }
                        }
                    }
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(m3u.toByteArray(Charsets.UTF_8))
                    } ?: error(context.getString(R.string.settings_backup_err_open_output))
                }
            }
            backupStatusMessage = result.fold(
                onSuccess = { context.getString(R.string.settings_backup_m3u_ok) },
                onFailure = { context.getString(R.string.settings_backup_failed, it.message ?: context.getString(R.string.settings_backup_err_unknown)) },
            )
        }
    }
    pendingBackupPayload?.let { incoming ->
        fun commitRestore(mode: com.mylittleblover.mkwplayer.library.BackupRestoreMode) {
            pendingBackupPayload = null
            coroutineScope.launch {
                val res = withContext(Dispatchers.IO) {
                    com.mylittleblover.mkwplayer.library.UserBackupManager.applyBackupToContext(context, incoming, mode)
                }
                res.onSuccess { summary ->
                    discovery.reloadAllAfterBackup()
                    model.syncPreferencesAfterBackupRestore()
                    model.reloadTrackMarkers()
                    outputTrim = com.mylittleblover.mkwplayer.audio.OutputTrimSettings.current(context)
                    playbackBehavior = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings.current(context)
                    backupStatusMessage = context.getString(
                        R.string.settings_backup_import_ok_fmt,
                        summary.playlistsCount,
                        summary.favoritesCount,
                        summary.presetsCount,
                        summary.routesCount,
                    )
                }.onFailure { err ->
                    backupStatusMessage = context.getString(R.string.settings_backup_failed, err.message ?: context.getString(R.string.settings_backup_err_unknown))
                }
            }
        }
        MkwDialog(
            onDismissRequest = { pendingBackupPayload = null },
            title = androidx.compose.ui.res.stringResource(R.string.settings_backup_mode_title),
            confirmButton = {
                MkwGhostButton(
                    text = androidx.compose.ui.res.stringResource(R.string.discovery_cancel),
                    onClick = { pendingBackupPayload = null },
                )
            },
        ) {
            MkwCard(
                onClick = { commitRestore(com.mylittleblover.mkwplayer.library.BackupRestoreMode.MERGE) },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(14.dp),
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.settings_backup_mode_merge),
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.settings_backup_mode_merge_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }
            MkwCard(
                onClick = { commitRestore(com.mylittleblover.mkwplayer.library.BackupRestoreMode.REPLACE) },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(14.dp),
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.settings_backup_mode_replace),
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.settings_backup_mode_replace_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }
        }
    }
    if (showSleepTimerSheet) {
        com.mylittleblover.mkwplayer.ui.components.SleepTimerSheet(
            state = ui.sleepTimer,
            canStopAfterTrack = ui.linearMode && ui.canStop,
            onStartMinutes = model::startSleepTimerMinutes,
            onStartAfterTrack = model::startSleepTimerAfterCurrentTrack,
            onExtend5Min = { model.extendSleepTimerMinutes(5) },
            onCancel = model::cancelSleepTimer,
            onDismiss = { showSleepTimerSheet = false },
        )
    }
    val browse = model.browseCatalog.collectAsStateWithLifecycle().value
    val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock)
    LaunchedEffect(browse, unlocked, staffCreditsUnlocked, ui.pack, ui.jukebox, ui.libraryGeneration) {
        val computed = withContext(Dispatchers.Default) {
            val extra = model.extraCatalog
            buildList {
                browse?.courses(unlocked).orEmpty().forEach { id ->
                    add(
                        com.mylittleblover.mkwplayer.library.RecommendationCandidate(
                            id,
                            com.mylittleblover.mkwplayer.library.RecommendationKind.COURSE,
                            model.canStartStage(id),
                        ),
                    )
                }
                val packPresent = ui.pack != null
                extra.sfcCourses.filter {
                    com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.isVisible(it.id, unlocked, staffCreditsUnlocked)
                }.forEach { track ->
                    add(
                        com.mylittleblover.mkwplayer.library.RecommendationCandidate(
                            com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack(track.id).raw,
                            com.mylittleblover.mkwplayer.library.RecommendationKind.COURSE,
                            packPresent,
                        ),
                    )
                }
                extra.battleTracks.filter {
                    com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.isVisible(it.id, unlocked, staffCreditsUnlocked)
                }.forEach { track ->
                    add(
                        com.mylittleblover.mkwplayer.library.RecommendationCandidate(
                            com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack(track.id).raw,
                            com.mylittleblover.mkwplayer.library.RecommendationKind.COURSE,
                            packPresent,
                        ),
                    )
                }
                extra.otherMusic.filter {
                    com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.isVisible(it.id, unlocked, staffCreditsUnlocked)
                }.forEach { item ->
                    add(
                        com.mylittleblover.mkwplayer.library.RecommendationCandidate(
                            com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack(item.id).raw,
                            com.mylittleblover.mkwplayer.library.RecommendationKind.COURSE,
                            packPresent,
                        ),
                    )
                }
                ui.jukebox.games.flatMap { it.items }.forEach { item ->
                    add(
                        com.mylittleblover.mkwplayer.library.RecommendationCandidate(
                            com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw,
                            com.mylittleblover.mkwplayer.library.RecommendationKind.FREE_DRIVING,
                            true,
                        ),
                    )
                }
            }
        }
        discovery.updateEligible(computed)
    }
    val playingCourse = model.currentStageCode
    val activeRecentId = remember(playingCourse, ui.activeFavoriteId, ui.linearTrack?.key, ui.linearMode) {
        activeRecentRefId(playingCourse, ui, model.extraCatalog)
    }
    var lastRecordedRecentId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.canStop, ui.canPause, ui.linearMode, ui.flow?.voiceCount, activeRecentId, ui.linearTrack?.key) {
        discovery.reloadRecent()
        if (!ui.canStop) {
            lastRecordedRecentId = null
        } else {
            val activeReady = if (ui.linearMode) {
                ui.linearTrack != null && !ui.trackLoading
            } else {
                (ui.flow?.voiceCount ?: 0) > 0
            }
            if (ui.canPause && activeRecentId != null && activeReady && lastRecordedRecentId != activeRecentId) {
                discovery.recordRecent(activeRecentId)
                lastRecordedRecentId = activeRecentId
            }
        }
    }
    val configuration = LocalConfiguration.current
    val sideNavigation = configuration.screenWidthDp >= 600 ||
        (configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenHeightDp < 500)
    val reduceMotion = com.mylittleblover.mkwplayer.ui.rememberReducedMotion()
    val playerDetail = nav.detail == NavState.Detail.Flow || nav.detail == NavState.Detail.Track ||
        nav.detail is NavState.Detail.Interactive
    val bottomDockVisible = !sideNavigation && !playerDetail && nav.detail !is NavState.Detail.Interactive &&
        nav.detail != NavState.Detail.Settings &&
        nav.detail != NavState.Detail.Equalizer &&
        nav.detail !is NavState.Detail.FlowEditor
    val navChromeEnter = if (reduceMotion) {
        fadeIn(tween(150))
    } else {
        expandVertically(
            animationSpec = tween(240, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
            expandFrom = Alignment.Bottom,
        ) + fadeIn(tween(200, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut))
    }
    val navChromeExit = if (reduceMotion) {
        fadeOut(tween(120))
    } else {
        shrinkVertically(
            animationSpec = tween(190, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
            shrinkTowards = Alignment.Bottom,
        ) + fadeOut(tween(150))
    }
    Box(modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val activeMediaCategory = nav.detail == null && nav.tab == Tab.MEDIA && mediaCategory >= 0
            val mediaCategoryTitleRes = when (mediaCategory) {
                0 -> R.string.discovery_courses
                1 -> R.string.discovery_battle
                2 -> R.string.discovery_free
                3 -> R.string.discovery_other
                4 -> R.string.discovery_sounds
                5 -> R.string.discovery_nintendo_music
                else -> null
            }
            val title = when (val detail = nav.detail) {
                NavState.Detail.Settings -> androidx.compose.ui.res.stringResource(R.string.settings_title)
                NavState.Detail.Equalizer -> androidx.compose.ui.res.stringResource(R.string.eq_title)
                is NavState.Detail.Course -> androidx.compose.ui.res.stringResource(R.string.nav_course)
                is NavState.Detail.Cup -> androidx.compose.ui.res.stringResource(
                    if (browse?.route(detail.id, unlocked)?.kind == com.mylittleblover.mkwplayer.routes.OfficialRouteKind.RALLY) {
                        R.string.discovery_rally
                    } else {
                        R.string.discovery_gp
                    },
                )
                NavState.Detail.Flow -> androidx.compose.ui.res.stringResource(R.string.discovery_interactive)
                NavState.Detail.Track -> androidx.compose.ui.res.stringResource(R.string.jukebox_now_playing)
                is NavState.Detail.Interactive -> androidx.compose.ui.res.stringResource(R.string.discovery_interactive)
                is NavState.Detail.Playlist -> androidx.compose.ui.res.stringResource(R.string.discovery_playlists)
                NavState.Detail.FavoritesPlaylist -> androidx.compose.ui.res.stringResource(R.string.pinned_favorites_title)
                is NavState.Detail.AllPlaylists -> androidx.compose.ui.res.stringResource(R.string.discovery_all_lists_title)
                is NavState.Detail.CustomRoute -> androidx.compose.ui.res.stringResource(R.string.nav_routes)
                is NavState.Detail.FlowEditor -> androidx.compose.ui.res.stringResource(R.string.flow_editor_title)
                is NavState.Detail.JukeboxGame -> ui.jukebox.gameById(detail.id)?.localizedName(languageTag)
                    ?: androidx.compose.ui.res.stringResource(R.string.discovery_free)
                else -> if (activeMediaCategory && mediaCategoryTitleRes != null) {
                    androidx.compose.ui.res.stringResource(mediaCategoryTitleRes)
                } else {
                    tabTitle(nav.tab)
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                AppHeader(
                    title = title,
                    canBack = nav.canGoBack || activeMediaCategory,
                    showSettings = nav.detail != NavState.Detail.Settings,
                    onBack = {
                        if (nav.canGoBack) {
                            nav = nav.back() ?: nav
                        } else if (activeMediaCategory) {
                            mediaCategory = -1
                        }
                    },
                    onSettings = { nav = nav.open(NavState.Detail.Settings) },
                    sleepTimerRemainingMs = if (ui.sleepTimer.mode == com.mylittleblover.mkwplayer.player.SleepTimerMode.COUNTDOWN) {
                        ui.sleepTimer.remainingMs
                    } else null,
                    sleepAfterCurrentTrack = ui.sleepTimer.mode == com.mylittleblover.mkwplayer.player.SleepTimerMode.AFTER_CURRENT_TRACK,
                    onSleepTimer = { showSleepTimerSheet = true },
                )
                val persistentUnlockBanner = !ceremonyPlaying && ui.unlock?.shouldShowPersistentBanner == true
                com.mylittleblover.mkwplayer.ui.unlock.PersistentUnlockCeremonyBanner(
                    visible = persistentUnlockBanner,
                    onEnterCeremony = { model.enterUnlockFromBanner() },
                )
                AnimatedVisibility(
                    visible = !ceremonyPlaying && ui.debugUnlockBannerVisible && !persistentUnlockBanner,
                    enter = navChromeEnter,
                    exit = navChromeExit,
                ) {
                    Surface(
                        color = MkwKitTokens.Palette.rose,
                        contentColor = MkwKitTokens.Palette.crimsonDeep,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MkwKitTokens.Palette.crimson,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = androidx.compose.ui.res.stringResource(R.string.unlock_mystery_banner_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                MkwPrimaryButton(
                                    text = androidx.compose.ui.res.stringResource(R.string.unlock_mystery_banner_action),
                                    onClick = { model.enterUnlockFromBanner() },
                                    size = MkwButtonSize.Compact,
                                )
                                MkwIconButton(
                                    icon = Icons.Filled.Clear,
                                    contentDescription = androidx.compose.ui.res.stringResource(R.string.discovery_cancel),
                                    onClick = { model.dismissDebugUnlockBanner() },
                                    visualSize = 32.dp,
                                    iconSize = 18.dp,
                                    tintOverride = MkwKitTokens.Palette.crimsonDeep,
                                )
                            }
                            MkwDivider(color = MkwKitTokens.Palette.crimson.copy(alpha = 0.18f))
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = playerDetail && ui.flow != null && !ui.trackLoading,
                    enter = navChromeEnter,
                    exit = navChromeExit,
                ) {
                    val flow = ui.flow
                    val transportLabel = when {
                        ui.canPause -> androidx.compose.ui.res.stringResource(R.string.interactive_pause)
                        ui.canResume -> androidx.compose.ui.res.stringResource(R.string.interactive_resume)
                        else -> androidx.compose.ui.res.stringResource(R.string.interactive_start)
                    }
                    val canToggleTransport = flow != null && !ui.busy && (ui.canPause || ui.canResume || !flow.performing)
                    com.mylittleblover.mkwplayer.ui.interactive.InteractiveStopBar(
                        canStop = ui.canStop, canFade = !ui.busy && model.canFadeStop, fading = ui.flow?.fading == true,
                        onStop = {
                            model.stop()
                            if (model.onExitedInteractiveMode(languageTag)) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onFade = model::fadeStopPerformance,
                        paused = ui.canResume,
                        fadeRemainingFrames = ui.flow?.fadeRemainingFrames,
                        transportRate = ui.flow?.transportRate ?: 48000,
                        progressFlow = model.progressUi,
                        leadingTransport = {
                            MkwIconButton(
                                icon = if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = transportLabel,
                                onClick = {
                                    when {
                                        ui.canPause -> model.pause()
                                        ui.canResume -> model.resume()
                                        else -> model.startPerformance()
                                    }
                                },
                                tone = MkwIconButtonTone.Ink,
                                visualSize = 48.dp,
                                iconSize = 24.dp,
                                enabled = canToggleTransport,
                            )
                        },
                    )
                }
                AnimatedVisibility(
                    visible = bottomDockVisible,
                    enter = navChromeEnter,
                    exit = navChromeExit,
                ) {
                    val destinations = Tab.ordered().map { tab ->
                        MkwNavDestination(
                            id = tab,
                            label = tabTitle(tab),
                            selectedIcon = tabIconVector(tab, selected = true),
                            unselectedIcon = tabIconVector(tab, selected = false),
                        )
                    }
                    MkwBottomNavDock(
                        destinations = destinations,
                        selected = nav.tab,
                        onSelect = { tab ->
                            if (tab == Tab.MEDIA) { mediaCategory = -1; mediaQuery = "" }
                            nav = nav.select(tab)
                        },
                    )
                }
            }
        },
    ) { inner ->
        val showMiniPlayer = ui.canStop && !playerDetail
        Row(Modifier.padding(inner).fillMaxSize()) {
            if (sideNavigation) androidx.compose.material3.NavigationRail(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Tab.ordered().forEach { tab ->
                    androidx.compose.material3.NavigationRailItem(selected = nav.tab == tab,
                        onClick = { if (tab == Tab.MEDIA) { mediaCategory = -1; mediaQuery = "" }; nav = nav.select(tab) }, icon = { TabIcon(tab, nav.tab == tab) }, label = { Text(tabTitle(tab)) })
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
            val extraNavBottom = if (showMiniPlayer && !bottomDockVisible) {
                androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
            } else 0.dp
            val markerBridge = remember(allTrackMarkers, model) {
                com.mylittleblover.mkwplayer.ui.home.TrackMarkerMenuBridge(
                    markersByTrackId = allTrackMarkers,
                    onPlayFromMarker = { trackId, posMs, playTrack ->
                        model.scheduleMarkerSeek(posMs, trackId)
                        playTrack()
                    },
                    onDeleteMarker = model::deleteTrackMarker,
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(
                com.mylittleblover.mkwplayer.ui.LocalMiniPlayerOverlayPadding provides (
                    if (showMiniPlayer) 78.dp + extraNavBottom else 0.dp
                ),
                com.mylittleblover.mkwplayer.ui.home.LocalTrackMarkerBridge provides markerBridge,
                com.mylittleblover.mkwplayer.ui.home.LocalStaffCreditsUnlocked provides staffCreditsUnlocked,
                com.mylittleblover.mkwplayer.ui.home.LocalNonInteractiveLoopConfig provides ui.nonInteractiveLoopConfig,
            ) {
            AnimatedContent(
                targetState = nav.snapshot(),
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val from = NavState.restore(initialState)
                    val to = NavState.restore(targetState)
                    val fromIsPlayer = from.detail == NavState.Detail.Flow ||
                        from.detail == NavState.Detail.Track ||
                        from.detail is NavState.Detail.Interactive
                    val toIsPlayer = to.detail == NavState.Detail.Flow ||
                        to.detail == NavState.Detail.Track ||
                        to.detail is NavState.Detail.Interactive
                    when {
                        toIsPlayer && !fromIsPlayer ->
                            com.mylittleblover.mkwplayer.ui.AppMotion.playerSheet(opening = true, reduceMotion = reduceMotion)
                        fromIsPlayer && !toIsPlayer ->
                            com.mylittleblover.mkwplayer.ui.AppMotion.playerSheet(opening = false, reduceMotion = reduceMotion)
                        else ->
                            com.mylittleblover.mkwplayer.ui.AppMotion.page(to.motionFrom(from), reduceMotion = reduceMotion)
                    }
                },
                label = "page-transition",
            ) { snapshot ->
            val page = NavState.restore(snapshot)
            val detail = page.detail
            pageStateHolder.SaveableStateProvider(snapshot.joinToString("") { "${it.length}:$it" }) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                detail == NavState.Detail.Settings -> AppSettingsScreen(
                    language = language, onLanguage = { AppLanguageSettings.select(context, it) },
                    busy = ui.busy, sourceName = ui.pack?.name, onOpenPack = openPack, onOpenFiles = openFiles,
                    testMode = ui.unlockTestMode,
                    testUnlockVisible = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock),
                    onTestUnlock = model::setUnlockTestMode,
                    onTestCeremony = model::replayCeremonyForTesting,
                    onTestUnlockVfx = { previewUnlockVfx = true },
                    onTestUnlockNotification = model::triggerUnlockNotificationForTesting,
                    onResetUnlockProgress = { model.resetUnlockProgressForTesting() },
                    outputTrim = outputTrim,
                    onOutputTrim = { chosen ->
                        outputTrim = chosen
                        com.mylittleblover.mkwplayer.audio.OutputTrimSettings.select(context, chosen)
                        model.applyOutputTrim(chosen)
                    },
                    playbackBehavior = playbackBehavior,
                    onSegmentSwitchFadeOutMs = { ms ->
                        playbackBehavior = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings
                            .setSegmentSwitchFadeOutMs(context, ms)
                    },
                    onPerfectBeat = { enabled ->
                        playbackBehavior = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings
                            .setPerfectBeatEnabled(context, enabled)
                    },
                    dspConfig = ui.dspConfig,
                    onLiveVisualizer = { enabled ->
                        playbackBehavior = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings
                            .setLiveVisualizerEnabled(context, enabled)
                        model.setLiveVisualizerEnabled(enabled)
                    },
                    onOpenEqualizer = { nav = nav.open(NavState.Detail.Equalizer) },
                    onExportBackup = {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date())
                        backupExportLauncher.launch("mkw-player-backup-$stamp.json")
                    },
                    onImportBackup = {
                        backupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onExportM3u = {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date())
                        m3uExportLauncher.launch("mkw-player-playlists-$stamp.m3u")
                    },
                    backupStatusMessage = backupStatusMessage,
                    sourceCount = ui.pack?.trackCount, playableCount = ui.pack?.playableCount,
                    importProgress = ui.pack?.progress.orEmpty(), importDetail = ui.detail,
                    importState = ui.importState,
                    onCancelImport = { model.cancelPackImport() },
                    onResumeImport = model::resumePackImport,
                    onRepickImport = openRepickPack,
                    onDismissChangelog = model::dismissImportChangelog,
                    exportLocationLabel = ui.exportFolderDisplay,
                    onPickExportLocation = { exportFolderLauncher.launch(null) },
                )
                detail == NavState.Detail.Equalizer -> com.mylittleblover.mkwplayer.ui.components.EqualizerScreen(
                    config = ui.dspConfig,
                    onUpdate = model::setMasterDsp,
                    onBack = { nav = nav.back() ?: nav },
                )
                detail is NavState.Detail.Course -> {
                    if (detail.code.startsWith("extra:")) {
                        val extraId = detail.code.removePrefix("extra:")
                        ExtraInteractiveDetailPage(
                            model = model,
                            ui = ui,
                            extraId = extraId,
                            presets = discoveryState.presets,
                            playlistsReferencingPreset = discovery::playlistsReferencingPreset,
                            onOpenFlowEditor = { editorCode, presetId ->
                                nav = nav.open(NavState.Detail.FlowEditor(editorCode, presetId))
                            },
                            onPlayCustomPreset = { preset, trackTitle, suiteLabel ->
                                if (model.previewCustomPreset(preset, trackTitle, suiteLabel)) {
                                    nav = nav.open(NavState.Detail.Track)
                                }
                            },
                            onDuplicatePreset = { preset ->
                                discovery.duplicatePreset(preset.id, context.getString(R.string.preset_copy_name_fmt, preset.name))
                            },
                            onDeletePreset = { preset -> discovery.deletePreset(preset.id) },
                            onPlayExtraMedley = { medleyId, medleyTitle, medleySubtitle, artworkName ->
                                if (model.playExtraMedley(medleyId, medleyTitle, medleySubtitle, artworkName)) {
                                    nav = nav.open(NavState.Detail.Track)
                                }
                            },
                            onInteractive = { nav = nav.open(NavState.Detail.Flow) },
                        )
                    } else if (browse == null || detail.code.removeSuffix("_00") !in browse.courses(unlocked)) {
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_empty), Modifier.padding(24.dp))
                    } else CoursePage(
                        model = model,
                        ui = ui,
                        code = detail.code,
                        presets = discoveryState.presets,
                        playlistsReferencingPreset = discovery::playlistsReferencingPreset,
                        onOpenFlowEditor = { courseCode, presetId ->
                            nav = nav.open(NavState.Detail.FlowEditor(courseCode, presetId))
                        },
                        onPlayCustomPreset = { preset, courseTitle, suiteLabel ->
                            if (model.previewCustomPreset(preset, courseTitle, suiteLabel)) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onDuplicatePreset = { preset ->
                            discovery.duplicatePreset(preset.id, context.getString(R.string.preset_copy_name_fmt, preset.name))
                        },
                        onDeletePreset = { preset -> discovery.deletePreset(preset.id) },
                        onPlayMedley = { medleyId, suiteLabel, rallyLabel ->
                            val courses = browse.courses(unlocked)
                            if (model.playCourseMedley(medleyId, languageTag, courses, suiteLabel, rallyLabel)) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onInteractive = { nav = nav.open(NavState.Detail.Interactive(detail.code)) },
                        onOpenPack = openPack,
                    )
                }
                detail is NavState.Detail.FlowEditor -> {
                    val inventory = remember(detail.courseCode, ui.pack) {
                        model.inspectCourseForEditor(detail.courseCode)
                    }
                    val existing = remember(discoveryState.presets, detail.presetId) {
                        discoveryState.presets.firstOrNull { it.id == detail.presetId }
                    }
                    val courseTitle = remember(detail.courseCode, browse, languageTag, model.extraCatalog) {
                        if (detail.courseCode.startsWith("extra:")) {
                            val id = detail.courseCode.removePrefix("extra:")
                            model.extraCatalog.sfcCourses.firstOrNull { it.id == id }?.localizedName(languageTag)
                                ?: model.extraCatalog.battleTracks.firstOrNull { it.id == id }?.localizedName(languageTag)
                                ?: model.extraCatalog.otherMusic.firstOrNull { it.id == id }?.localizedName(languageTag)
                                ?: detail.courseCode
                        } else {
                            browse?.displayName(detail.courseCode, languageTag) ?: detail.courseCode
                        }
                    }
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    if (inventory == null) {
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_empty), Modifier.padding(24.dp))
                    } else {
                        com.mylittleblover.mkwplayer.ui.interactive.FlowEditorScreen(
                            courseTitle = courseTitle,
                            inventory = inventory,
                            existingPreset = existing,
                            onAudition = { draft ->
                                if (model.previewCustomPreset(draft, courseTitle, suiteLabel)) {
                                    nav = nav.open(NavState.Detail.Track)
                                }
                            },
                            onSave = { savedPreset ->
                                discovery.savePreset(savedPreset) {
                                    nav = nav.back() ?: nav
                                }
                            },
                            onLoadWaveform = { stem -> model.loadWaveformForStem(stem) },
                            onPreviewSeam = { draft, seamIndex ->
                                val seamTitle = "$courseTitle · ${context.getString(R.string.flow_editor_preview_seam)}"
                                model.previewCourseSeam(draft, seamIndex, seamTitle, suiteLabel)
                            },
                            onCompileExport = { draft ->
                                model.compileAndExportPreset(draft)?.second
                            },
                            isRenderingAudio = ui.isRenderingAudio,
                            renderAudioProgress = ui.renderAudioProgress,
                            renderAudioStatus = ui.renderAudioStatus,
                            renderAudioError = ui.renderAudioError,
                            renderAudioOutputPath = ui.renderAudioOutputPath,
                            outputTrimEnabled = ui.outputTrimEnabled,
                            exportFolderDisplay = ui.exportFolderDisplay,
                            onRenderAudio = { draft -> model.renderFlowPresetToAudio(draft, languageTag) },
                            onCancelRenderAudio = model::cancelRenderFlowAudio,
                            onDismissRenderDialog = model::dismissRenderFlowDialog,
                        )
                    }
                }
                detail is NavState.Detail.Interactive -> {
                    if (browse == null || detail.code.removeSuffix("_00") !in browse.courses(unlocked)) {
                        Text(androidx.compose.ui.res.stringResource(R.string.discovery_empty), Modifier.padding(24.dp))
                    } else {
                        PlayerDismissContainer(onDismiss = { nav = nav.back() ?: nav }) {
                            ConsolePage(model, ui, openPack, openFiles, onOpenSleepTimer = { showSleepTimerSheet = true })
                        }
                    }
                }
                detail is NavState.Detail.AllPlaylists -> com.mylittleblover.mkwplayer.ui.home.DiscoveryAllPlaylists(
                    initialSection = detail.section,
                    catalog = browse,
                    unlocked = unlocked,
                    language = languageTag,
                    state = discoveryState,
                    jukeboxGames = ui.jukebox.games,
                    extraCatalog = model.extraCatalog,
                    packDirectory = model.packDirectory,
                    onCreate = discovery::createPlaylist,
                    onPlaylist = { id ->
                        nav = nav.open(
                            if (id == com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID) {
                                NavState.Detail.FavoritesPlaylist
                            } else {
                                NavState.Detail.Playlist(id)
                            },
                        )
                    },
                    onCup = { nav = nav.open(NavState.Detail.Cup(it)) },
                    onJukeboxGame = { nav = nav.open(NavState.Detail.JukeboxGame(it)) },
                    onCreateRouteOnMap = {
                        mapState.enterEdit()
                        nav = nav.select(Tab.WORLD_MAP)
                    },
                    onCustomRoute = { nav = nav.open(NavState.Detail.CustomRoute(it)) },
                    inspectCustomRoute = model::inspectCustomRoute,
                )
                detail is NavState.Detail.CustomRoute -> {
                    val activeRoute = discoveryState.customRoutes.firstOrNull { it.id == detail.id }
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val roadPrefix = androidx.compose.ui.res.stringResource(R.string.route_road_prefix)
                    val subTag = androidx.compose.ui.res.stringResource(R.string.route_custom_substitute_tag)
                    val routeReport = remember(activeRoute, ui.pack, ui.jukebox) {
                        activeRoute?.let(model::inspectCustomRoute)
                    }
                    com.mylittleblover.mkwplayer.ui.home.DiscoveryCustomRouteDetail(
                        route = activeRoute,
                        catalog = browse,
                        language = languageTag,
                        presets = discoveryState.presets,
                        installedJukebox = model.allInstalledJukeboxItems(),
                        packDirectory = model.packDirectory,
                        courseArtwork = model.extraCatalog.courseArtwork,
                        report = routeReport,
                        onPlayRoute = { route ->
                            if (model.playCustomRoute(
                                    route = route,
                                    userPresets = discoveryState.presets,
                                    languageTag = languageTag,
                                    suiteLabel = suiteLabel,
                                    rallyLabel = rallyLabel,
                                    roadPrefix = roadPrefix,
                                    customSubstituteTag = subTag,
                                ) is PlayerViewModel.RoutePlayResult.Started
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onEditOnMap = { route ->
                            val points = route.stops.mapNotNull { stop ->
                                model.mapAnchors?.stageTolerant(stop.stageCode)?.let {
                                    androidx.compose.ui.geometry.Offset(it.u, it.v)
                                }
                            }
                            mapState.loadCustomRoute(route, points)
                            nav = nav.select(Tab.WORLD_MAP)
                        },
                        onToggleLoop = { discovery.setCustomRouteLoop(detail.id, it) },
                        onRename = { discovery.renameCustomRoute(detail.id, it) },
                        onDelete = { discovery.deleteCustomRoute(detail.id); nav = nav.back() ?: nav },
                        onRemoveLastStop = { discovery.removeCustomRouteLastStop(detail.id) },
                        onUpdateStopPreset = { stopIdx, presetId ->
                            discovery.setCustomRouteStopPreset(detail.id, stopIdx, presetId)
                        },
                        onUpdateEdgeMusic = { edgeIdx, playMusic, trackKey ->
                            discovery.setCustomRouteEdgeMusic(detail.id, edgeIdx, playMusic, trackKey)
                        },
                        onOpenCourse = { code -> nav = nav.open(NavState.Detail.Course(code)) },
                        onOpenFlowEditor = { courseCode, presetId ->
                            nav = nav.open(NavState.Detail.FlowEditor(courseCode, presetId))
                        },
                        isPresetSelectable = { courseCode, presetRef ->
                            model.isPresetSelectableForCourse(courseCode, presetRef, discoveryState.presets)
                        },
                        onUpdateRuleMode = { mode ->
                            discovery.setCustomRouteRuleMode(detail.id, mode)
                        },
                        onUpdateSuiteMode = { mode ->
                            discovery.setCustomRouteSuiteMode(detail.id, mode)
                        },
                        onUpdatePureFreeRoamConfig = { cfg ->
                            discovery.setCustomRoutePureFreeRoamConfig(detail.id, cfg)
                        },
                        onUpdateEdgeFreeRoamOverride = { edgeIdx, ov ->
                            discovery.setCustomRouteEdgeFreeRoamOverride(detail.id, edgeIdx, ov)
                        },
                        resolveAutoStopPresetRef = { courseCode, stopIndex, stopCount, mode ->
                            model.effectiveStopPresetRef(
                                stageCode = courseCode,
                                explicitPresetId = null,
                                stopIndex = stopIndex,
                                stopCount = stopCount,
                                officialKind = null,
                                suiteMode = mode,
                                userPresets = discoveryState.presets,
                                roadRuleMode = activeRoute?.roadRuleMode,
                            )
                        },
                    )
                }
                detail is NavState.Detail.Playlist -> {
                    val activePlaylist = discoveryState.playlists.firstOrNull { it.id == detail.id }
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val nativePrefix = androidx.compose.ui.res.stringResource(R.string.discovery_native_transition_prefix)
                    val queueSubtitle = androidx.compose.ui.res.stringResource(R.string.discovery_other)
                    com.mylittleblover.mkwplayer.ui.home.DiscoveryPlaylist(
                        playlist = activePlaylist,
                        catalog = browse,
                        unlocked = unlocked,
                        language = languageTag,
                        error = discoveryState.playlistError != null,
                        onRemove = { discovery.removeFromPlaylist(detail.id, it) },
                        onRemoveMany = { discovery.removeManyFromPlaylist(detail.id, it) },
                        onRename = { discovery.renamePlaylist(detail.id, it) },
                        onMove = { from, to -> discovery.moveInPlaylist(detail.id, from, to) },
                        onDelete = { discovery.deletePlaylist(detail.id); nav = nav.back() ?: nav },
                        onCourse = { nav = nav.open(NavState.Detail.Course(it)) },
                        presets = discoveryState.presets,
                        packDirectory = model.packDirectory,
                        courseArtwork = model.extraCatalog.courseArtwork,
                        jukebox = ui.jukebox,
                        extraCatalog = model.extraCatalog,
                        isThisPlaylistPlaying = ui.linearMode &&
                            ui.linearContextKind == com.mylittleblover.mkwplayer.player.LinearSessionStore.ContextKind.PLAYLIST &&
                            ui.linearContextId == detail.id,
                        onPlayAll = {
                            if (activePlaylist != null && model.playUserPlaylist(
                                    activePlaylist, discoveryState.presets, 0, false,
                                    languageTag, suiteLabel, rallyLabel, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onShuffle = {
                            if (activePlaylist != null && model.playUserPlaylist(
                                    activePlaylist, discoveryState.presets, 0, true,
                                    languageTag, suiteLabel, rallyLabel, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onPlayFrom = { idx ->
                            if (activePlaylist != null && model.playUserPlaylist(
                                    activePlaylist, discoveryState.presets, idx, false,
                                    languageTag, suiteLabel, rallyLabel, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onToggleNativeDefault = { enabled ->
                            discovery.setPlaylistNativeTransitionDefault(detail.id, enabled)
                        },
                        onUpdateEntryPreset = { idx, presetId ->
                            discovery.setPlaylistEntryPreset(detail.id, idx, presetId)
                        },
                        onUpdateEntryNative = { idx, override ->
                            discovery.setPlaylistEntryNativeTransition(detail.id, idx, override)
                        },
                        onOpenFlowEditor = { courseCode, presetId ->
                            nav = nav.open(NavState.Detail.FlowEditor(courseCode, presetId))
                        },
                        isPresetSelectable = { courseCode, presetRef ->
                            model.isPresetSelectableForCourse(courseCode, presetRef, discoveryState.presets)
                        },
                        playlists = discoveryState.playlists,
                        favorites = discoveryState.favorites,
                        onToggleFavorite = discovery::toggleFavorite,
                        onAddToPlaylist = { pid, refs -> discovery.addManyToPlaylist(pid, refs) },
                        onCreatePlaylistAndAdd = { name, refs -> discovery.createPlaylistAndAdd(name, refs) },
                        allowPlayNext = ui.allowPlayNext,
                        onPlayNextRefs = { refs ->
                            model.playNextRefs(
                                refs, discoveryState.presets, languageTag,
                                suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                            )
                        },
                        onAddToQueueRefs = { refs ->
                            model.addToQueueRefs(
                                refs, discoveryState.presets, languageTag,
                                suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                            )
                        },
                        onPlaySelectedRefs = { refs ->
                            if (model.playSelectedRefs(
                                    refs, discoveryState.presets, languageTag,
                                    suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                                true
                            } else {
                                false
                            }
                        },
                        playStats = ui.playStats,
                    )
                }
                detail == NavState.Detail.FavoritesPlaylist -> {
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val nativePrefix = androidx.compose.ui.res.stringResource(R.string.discovery_native_transition_prefix)
                    val queueSubtitle = androidx.compose.ui.res.stringResource(R.string.discovery_other)
                    val favTitle = androidx.compose.ui.res.stringResource(R.string.pinned_favorites_title)
                    com.mylittleblover.mkwplayer.ui.home.DiscoveryFavoritesPlaylist(
                        favorites = discoveryState.favorites,
                        catalog = browse,
                        unlocked = unlocked,
                        language = languageTag,
                        presets = discoveryState.presets,
                        packDirectory = model.packDirectory,
                        courseArtwork = model.extraCatalog.courseArtwork,
                        jukebox = ui.jukebox,
                        extraCatalog = model.extraCatalog,
                        isThisPlaylistPlaying = ui.linearMode &&
                            ui.linearContextKind == com.mylittleblover.mkwplayer.player.LinearSessionStore.ContextKind.PLAYLIST &&
                            ui.linearContextId == com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID,
                        onPlayAll = { refs ->
                            if (model.playSelectedRefs(
                                    refs, discoveryState.presets, languageTag,
                                    suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                    shuffle = false,
                                    startIndex = 0,
                                    contextId = com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID,
                                    contextTitle = favTitle,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onShuffle = { refs ->
                            if (model.playSelectedRefs(
                                    refs, discoveryState.presets, languageTag,
                                    suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                    shuffle = true,
                                    startIndex = 0,
                                    contextId = com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID,
                                    contextTitle = favTitle,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onPlayFrom = { refs, idx ->
                            if (model.playSelectedRefs(
                                    refs, discoveryState.presets, languageTag,
                                    suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                    shuffle = false,
                                    startIndex = idx,
                                    contextId = com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID,
                                    contextTitle = favTitle,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onCourse = { nav = nav.open(NavState.Detail.Course(it)) },
                        onOpenFlowEditor = { courseCode, presetId ->
                            nav = nav.open(NavState.Detail.FlowEditor(courseCode, presetId))
                        },
                        playlists = discoveryState.playlists,
                        onToggleFavorite = discovery::toggleFavorite,
                        onAddToPlaylist = { pid, refs -> discovery.addManyToPlaylist(pid, refs) },
                        onCreatePlaylistAndAdd = { name, refs -> discovery.createPlaylistAndAdd(name, refs) },
                        allowPlayNext = ui.allowPlayNext,
                        onPlayNextRefs = { refs ->
                            model.playNextRefs(
                                refs, discoveryState.presets, languageTag,
                                suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                            )
                        },
                        onAddToQueueRefs = { refs ->
                            model.addToQueueRefs(
                                refs, discoveryState.presets, languageTag,
                                suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                            )
                        },
                        onPlaySelectedRefs = { refs ->
                            if (model.playSelectedRefs(
                                    refs, discoveryState.presets, languageTag,
                                    suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                                true
                            } else {
                                false
                            }
                        },
                        playStats = ui.playStats,
                    )
                }
                detail is NavState.Detail.Cup -> {
                    val cupRoute = browse?.route(detail.id, unlocked)
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val roadPrefix = androidx.compose.ui.res.stringResource(R.string.route_road_prefix)
                    val cupFreeRoamEntry = cupRoute?.let { discoveryState.officialRouteFreeRoam[it.id] }
                        ?: com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry()
                    com.mylittleblover.mkwplayer.ui.home.DiscoveryCup(
                        route = cupRoute,
                        catalog = browse,
                        language = languageTag,
                        installedJukebox = model.allInstalledJukeboxItems(),
                        packDirectory = model.packDirectory,
                        extraCatalog = model.extraCatalog,
                        inspectOfficialRoute = { route, overrides, ruleMode ->
                            model.inspectOfficialRoute(
                                route = route,
                                sessionOverrides = overrides,
                                ruleMode = ruleMode,
                                pureFreeRoamEnabled = cupFreeRoamEntry.config.enabled,
                                pureFreeRoamTrackPool = cupFreeRoamEntry.config.trackPool,
                            )
                        },
                        resolveStopPresetRef = { stageCode, stopIndex, stopCount, mode ->
                            model.effectiveStopPresetRef(
                                stageCode = stageCode,
                                explicitPresetId = null,
                                stopIndex = stopIndex,
                                stopCount = stopCount,
                                officialKind = cupRoute?.kind,
                                suiteMode = mode,
                                userPresets = discoveryState.presets,
                            )
                        },
                        pureFreeRoamEntry = cupFreeRoamEntry,
                        onPureFreeRoamConfigChange = { cfg ->
                            cupRoute?.let { discovery.setOfficialRouteFreeRoamConfig(it.id, cfg) }
                        },
                        onPureFreeRoamEdgeOverrideChange = { edgeIdx, ov ->
                            cupRoute?.let { discovery.setOfficialRouteFreeRoamEdgeOverride(it.id, edgeIdx, ov) }
                        },
                        onPlayRoute = { route, loop, overrides, ruleMode, suiteMode ->
                            if (model.playOfficialRoute(
                                    route = route,
                                    userPresets = discoveryState.presets,
                                    loopRoute = loop,
                                    sessionOverrides = overrides,
                                    languageTag = languageTag,
                                    suiteLabel = suiteLabel,
                                    rallyLabel = rallyLabel,
                                    roadPrefix = roadPrefix,
                                    ruleMode = ruleMode,
                                    suiteMode = suiteMode,
                                    pureFreeRoamConfig = cupFreeRoamEntry.config,
                                    pureFreeRoamEdgeOverrides = cupFreeRoamEntry.edgeOverrides,
                                ) is PlayerViewModel.RoutePlayResult.Started
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onPlayRouteWithFreeRoam = { route, loop, overrides, ruleMode, suiteMode, pfrCfg, pfrOverrides ->
                            if (model.playOfficialRoute(
                                    route = route,
                                    userPresets = discoveryState.presets,
                                    loopRoute = loop,
                                    sessionOverrides = overrides,
                                    languageTag = languageTag,
                                    suiteLabel = suiteLabel,
                                    rallyLabel = rallyLabel,
                                    roadPrefix = roadPrefix,
                                    ruleMode = ruleMode,
                                    suiteMode = suiteMode,
                                    pureFreeRoamConfig = pfrCfg,
                                    pureFreeRoamEdgeOverrides = pfrOverrides,
                                ) is PlayerViewModel.RoutePlayResult.Started
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                            }
                        },
                        onMap = { id ->
                            val route = browse?.route(id, unlocked)
                            val points = route?.stops.orEmpty().mapNotNull { stop -> model.mapAnchors?.stageTolerant(stop.stageCode)?.let { androidx.compose.ui.geometry.Offset(it.u, it.v) } }
                            mapState.selectRoute(id, points)
                            nav = nav.select(Tab.WORLD_MAP)
                        },
                        onCourse = { code -> nav = nav.open(NavState.Detail.Course(code)) },
                    )
                }
                detail is NavState.Detail.JukeboxGame -> {
                    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                    val nativePrefix = androidx.compose.ui.res.stringResource(R.string.discovery_native_transition_prefix)
                    val queueSubtitle = androidx.compose.ui.res.stringResource(R.string.discovery_other)
                    com.mylittleblover.mkwplayer.ui.home.DiscoveryJukeboxGame(
                        game = ui.jukebox.gameById(detail.id),
                        language = languageTag,
                        activeTrackKey = ui.selectedKey,
                        packDirectory = model.packDirectory,
                        allowPlayNext = ui.allowPlayNext,
                        onPlayFromIndex = { game, index, shuffle ->
                            model.playJukeboxQueue(game, index, languageTag, shuffle = shuffle)
                            nav = nav.open(NavState.Detail.Track)
                        },
                        onPlayNext = { item, game ->
                            model.playJukeboxNext(item, game, languageTag)
                        },
                        playlists = discoveryState.playlists,
                        onAddToPlaylist = { pid, refs -> discovery.addManyToPlaylist(pid, refs) },
                        onCreatePlaylistAndAdd = { name, refs -> discovery.createPlaylistAndAdd(name, refs) },
                        onAddToQueueRefs = { refs ->
                            model.addToQueueRefs(
                                refs, discoveryState.presets, languageTag,
                                suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                            )
                        },
                        onPlaySelectedRefs = { refs ->
                            if (model.playSelectedRefs(
                                    refs, discoveryState.presets, languageTag,
                                    suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                )
                            ) {
                                nav = nav.open(NavState.Detail.Track)
                                true
                            } else {
                                false
                            }
                        },
                        favorites = discoveryState.favorites,
                        onToggleFavorite = discovery::toggleFavorite,
                        playStats = ui.playStats,
                    )
                }
                detail == NavState.Detail.Flow -> PlayerDismissContainer(onDismiss = { nav = nav.back() ?: nav }) {
                    ConsolePage(model, ui, openPack, openFiles, onOpenSleepTimer = { showSleepTimerSheet = true })
                }
                detail == NavState.Detail.Track -> PlayerDismissContainer(onDismiss = { nav = nav.back() ?: nav }) {
                    SingleTrackPlayer(
                        model = model,
                        ui = ui,
                        onOpenSleepTimer = { showSleepTimerSheet = true },
                        onStopped = { nav = nav.back() ?: nav },
                    )
                }
                else -> when (page.tab) {
                    Tab.HOME -> {
                        val otherCategoryLabel = androidx.compose.ui.res.stringResource(R.string.discovery_other)
                        fun handleDailyPick(rawId: String, quickPlay: Boolean) {
                            when (val ref = com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(rawId)) {
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> {
                                    nav = nav.open(NavState.Detail.Course(ref.logicalCode))
                                }
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack -> {
                                    model.extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId }?.let { track ->
                                        nav = nav.open(NavState.Detail.Course("extra:${track.id}"))
                                    }
                                }
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack -> {
                                    model.extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId }?.let { track ->
                                        nav = nav.open(NavState.Detail.Course("extra:${track.id}"))
                                    }
                                }
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox -> {
                                    model.openJukeboxTrack(ref.trackKey, languageTag)
                                    nav = nav.open(NavState.Detail.Track)
                                }
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack -> {
                                    val item = model.extraCatalog.otherMusic.firstOrNull { it.id == ref.extraId }
                                    when (item) {
                                        is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle -> {
                                            nav = nav.open(NavState.Detail.Course("extra:${item.id}"))
                                        }
                                        is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.LinearAsset -> {
                                            val entry = LinearQueue.Entry(
                                                key = "extra:${item.id}",
                                                title = item.localizedName(languageTag),
                                                subtitle = otherCategoryLabel,
                                                sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
                                                durationMs = item.durationMs,
                                                artworkName = item.artwork,
                                                assetSha256 = item.asset.sha256,
                                                loop = item.asset.loop,
                                                loopStart = item.asset.loopStart,
                                                loopEnd = item.asset.loopEnd,
                                            )
                                            model.playExtraLinearQueue(listOf(entry), 0, LinearQueue.RepeatMode.OFF, false)
                                            nav = nav.open(NavState.Detail.Track)
                                        }
                                        is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.OverlaidOpening -> {
                                            val entry = LinearQueue.Entry(
                                                key = "extra:${item.id}",
                                                title = item.localizedName(languageTag),
                                                subtitle = otherCategoryLabel,
                                                sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
                                                durationMs = item.durationMs,
                                                artworkName = item.artwork,
                                                overlaidOpening = item,
                                            )
                                            model.playExtraLinearQueue(listOf(entry), 0, LinearQueue.RepeatMode.OFF, false)
                                            nav = nav.open(NavState.Detail.Track)
                                        }
                                        is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> {
                                            val entry = LinearQueue.Entry(
                                                key = "extra:${item.id}",
                                                title = item.localizedName(languageTag),
                                                subtitle = otherCategoryLabel,
                                                sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
                                                durationMs = item.durationMs,
                                                artworkName = item.artwork,
                                                chainedSequence = item,
                                            )
                                            model.playExtraLinearQueue(listOf(entry), 0, LinearQueue.RepeatMode.OFF, false)
                                            nav = nav.open(NavState.Detail.Track)
                                        }
                                        null -> {
                                            val simple = model.extraCatalog.shortSounds.firstOrNull { it.id == ref.extraId }
                                                ?: model.extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == ref.extraId }
                                            if (simple != null) {
                                                val sub = if (model.extraCatalog.shortSounds.any { it.id == ref.extraId }) {
                                                    context.getString(R.string.discovery_sounds)
                                                } else {
                                                    context.getString(R.string.discovery_nintendo_music)
                                                }
                                                val entry = LinearQueue.Entry(
                                                    key = "extra:${simple.id}",
                                                    title = simple.localizedName(languageTag),
                                                    subtitle = sub,
                                                    sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
                                                    ostIndex = simple.ostIndex,
                                                    durationMs = simple.durationMs,
                                                    artworkName = simple.artwork,
                                                    assetSha256 = simple.asset.sha256,
                                                    loop = simple.asset.loop,
                                                    loopStart = simple.asset.loopStart,
                                                    loopEnd = simple.asset.loopEnd,
                                                )
                                                model.playExtraLinearQueue(listOf(entry), 0, LinearQueue.RepeatMode.OFF, false)
                                                nav = nav.open(NavState.Detail.Track)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val homeSuiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                        val homeRallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                        val homeNativePrefix = androidx.compose.ui.res.stringResource(R.string.discovery_native_transition_prefix)
                        com.mylittleblover.mkwplayer.ui.home.DiscoveryHome(browse, discoveryState, languageTag, unlocked,
                            onOpen = { id -> handleDailyPick(id, quickPlay = false) },
                            onFavorite = discovery::toggleFavorite,
                            onImport = openPack, onPlayer = { nav = nav.open(if (ui.flow == null) NavState.Detail.Track else NavState.Detail.Flow) },
                            onToggle = { if (ui.canPause) model.pause() else if (ui.canResume) model.resume() },
                            onMedia = { mediaCategory = -1; mediaQuery = ""; nav = nav.select(Tab.MEDIA) },
                            session = ui, currentCode = model.currentStageCode,
                            packDirectory = model.packDirectory, artworkNames = playbackArtworkNames(model, ui),
                            courseArtwork = model.extraCatalog.courseArtwork,
                            jukebox = ui.jukebox,
                            extraCatalog = model.extraCatalog,
                            progressFlow = model.progressUi,
                            onQuickPlay = { id -> handleDailyPick(id, quickPlay = true) },
                            onResumeSaved = { model.resumeContinueListening { nav = nav.open(NavState.Detail.Track) } },
                            onDismissSaved = model::dismissContinueListening,
                            playStats = ui.playStats,
                            onPlaySelectedRefs = { refs ->
                                if (model.playSelectedRefs(
                                        refs, discoveryState.presets, languageTag,
                                        homeSuiteLabel, homeRallyLabel, otherCategoryLabel, homeNativePrefix,
                                    )
                                ) {
                                    nav = nav.open(NavState.Detail.Track)
                                    true
                                } else {
                                    false
                                }
                            })
                    }
                    Tab.MEDIA -> {
                        val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                        val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                        val nativePrefix = androidx.compose.ui.res.stringResource(R.string.discovery_native_transition_prefix)
                        val queueSubtitle = androidx.compose.ui.res.stringResource(R.string.discovery_other)
                        com.mylittleblover.mkwplayer.ui.home.DiscoveryMedia(browse, unlocked, languageTag,
                            category = mediaCategory, onCategory = { mediaCategory = it }, query = mediaQuery, onQuery = { mediaQuery = it },
                            onCourse = { nav = nav.open(NavState.Detail.Course(it)) }, onCup = { nav = nav.open(NavState.Detail.Cup(it)) },
                            jukebox = ui.jukebox,
                            extraCatalog = model.extraCatalog,
                            packDirectory = model.packDirectory,
                            activeTrackKey = ui.selectedKey,
                            allowPlayNext = ui.allowPlayNext,
                            unlockProgress = ui.unlock?.progress,
                            unlockCompleted = ui.unlock?.completedCount ?: 0,
                            unlockTotal = ui.unlock?.eligibleCount ?: 0,
                            onJukeboxGame = { gameId -> nav = nav.open(NavState.Detail.JukeboxGame(gameId)) },
                            onPlayJukeboxTrack = { game, index ->
                                model.playJukeboxQueue(game, index, languageTag, shuffle = false)
                                nav = nav.open(NavState.Detail.Track)
                            },
                            onPlayJukeboxGenre = { genre, index, shuffle ->
                                model.playJukeboxGenre(genre, index, shuffle, languageTag)
                                nav = nav.open(NavState.Detail.Track)
                            },
                            onPlayAllJukebox = { shuffle ->
                                model.playAllJukebox(languageTag, shuffle = shuffle)
                                nav = nav.open(NavState.Detail.Track)
                            },
                            freeRoamMode = ui.freeRoamMode,
                            freeRoamCatalog = model.freeRoamJukeboxCatalog,
                            onSelectFreeRoamMode = { mode, contextTitle ->
                                model.selectFreeRoamMode(mode, languageTag, contextTitle)
                            },
                            onPlayFreeRoamRadio = { mode, contextTitle ->
                                if (model.playFreeRoamRadio(mode, languageTag, contextTitle)) {
                                    nav = nav.open(NavState.Detail.Track)
                                }
                            },
                            onPlayJukeboxNext = { item, game ->
                                model.playJukeboxNext(item, game, languageTag)
                            },
                            onJukeboxTrack = { key ->
                                model.openJukeboxTrack(key, languageTag)
                                // The free-driving pieces are single tracks, not stage flows, so they belong on
                                // the single-track screen. Sending them to the console would offer stage events
                                // for music that has no stages.
                                nav = nav.open(NavState.Detail.Track)
                            },
                            onSfcCourse = { track ->
                                nav = nav.open(NavState.Detail.Course("extra:${track.id}"))
                            },
                            onBattleTrack = { track ->
                                nav = nav.open(NavState.Detail.Course("extra:${track.id}"))
                            },
                            onOpenTitle = {
                                val titleId = model.extraCatalog.otherMusic
                                    .filterIsInstance<com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle>()
                                    .firstOrNull()?.id ?: "title_theme"
                                nav = nav.open(NavState.Detail.Course("extra:$titleId"))
                            },
                            onPlayExtraQueue = { entries, startIndex, repeatMode, shuffle ->
                                model.playExtraLinearQueue(entries, startIndex, repeatMode, shuffle)
                                nav = nav.open(NavState.Detail.Track)
                            },
                            onAuditionExtra = { entry ->
                                model.playExtraLinearQueue(listOf(entry), 0, LinearQueue.RepeatMode.OFF, false)
                            },
                            extraMedleyCatalog = model.extraMedleyCatalog,
                            onPlayExtraMedley = { medleyId, medleyTitle, medleySubtitle, artworkName ->
                                if (model.playExtraMedley(medleyId, medleyTitle, medleySubtitle, artworkName)) {
                                    nav = nav.open(NavState.Detail.Track)
                                }
                            },
                            onOpenExtraFlowEditor = { targetId ->
                                nav = nav.open(NavState.Detail.FlowEditor("extra:$targetId", null))
                            },
                            playlists = discoveryState.playlists,
                            favorites = discoveryState.favorites,
                            onToggleFavorite = discovery::toggleFavorite,
                            onAddToPlaylist = { pid, refs -> discovery.addManyToPlaylist(pid, refs) },
                            onCreatePlaylistAndAdd = { name, refs -> discovery.createPlaylistAndAdd(name, refs) },
                            onPlayNextRefs = { refs ->
                                model.playNextRefs(
                                    refs, discoveryState.presets, languageTag,
                                    suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                )
                            },
                            onAddToQueueRefs = { refs ->
                                model.addToQueueRefs(
                                    refs, discoveryState.presets, languageTag,
                                    suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                )
                            },
                            onPlaySelectedRefs = { refs ->
                                if (model.playSelectedRefs(
                                        refs, discoveryState.presets, languageTag,
                                        suiteLabel, rallyLabel, queueSubtitle, nativePrefix,
                                    )
                                ) {
                                    nav = nav.open(NavState.Detail.Track)
                                    true
                                } else {
                                    false
                                }
                            },
                            onOpenCourseFlowEditor = { courseCode ->
                                nav = nav.open(NavState.Detail.FlowEditor(courseCode, null))
                            },
                            onLockedMysteryCourseClick = { showLockedMysteryDialog = true },
                            playStats = ui.playStats,
                        )
                    }
                    Tab.WORLD_MAP -> MapPage(
                        model = model,
                        ui = ui,
                        state = mapState,
                        discovery = discovery,
                        presets = discoveryState.presets,
                        officialRouteFreeRoam = discoveryState.officialRouteFreeRoam,
                        onOpenCourse = { code -> nav = nav.open(NavState.Detail.Course(code)) },
                        onOpenCustomRoute = { routeId -> nav = nav.open(NavState.Detail.CustomRoute(routeId)) },
                        onPlayStarted = { nav = nav.open(NavState.Detail.Track) },
                    )
                    Tab.PLAYLISTS -> {
                        val plSuiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                        val plRallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                        val plNativePrefix = androidx.compose.ui.res.stringResource(R.string.discovery_native_transition_prefix)
                        val plQueueSubtitle = androidx.compose.ui.res.stringResource(R.string.discovery_other)
                        com.mylittleblover.mkwplayer.ui.home.DiscoveryPlaylists(
                            catalog = browse,
                            unlocked = unlocked,
                            language = languageTag,
                            state = discoveryState,
                            onCreate = discovery::createPlaylist,
                            onPlaylist = { id ->
                        nav = nav.open(
                            if (id == com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID) {
                                NavState.Detail.FavoritesPlaylist
                            } else {
                                NavState.Detail.Playlist(id)
                            },
                        )
                    },
                            onCup = { nav = nav.open(NavState.Detail.Cup(it)) },
                            jukeboxGames = ui.jukebox.games,
                            extraCatalog = model.extraCatalog,
                            packDirectory = model.packDirectory,
                            onViewAll = { section -> nav = nav.open(NavState.Detail.AllPlaylists(section)) },
                            onJukeboxGame = { gameId -> nav = nav.open(NavState.Detail.JukeboxGame(gameId)) },
                            onCustomRoute = { routeId -> nav = nav.open(NavState.Detail.CustomRoute(routeId)) },
                            onPlanRouteOnMap = {
                                mapState.enterEdit()
                                nav = nav.select(Tab.WORLD_MAP)
                            },
                            inspectCustomRoute = remember(ui.libraryGeneration) { { route -> model.inspectCustomRoute(route) } },
                            playStats = ui.playStats,
                            onPlaySelectedRefs = { refs ->
                                if (model.playSelectedRefs(
                                        refs, discoveryState.presets, languageTag,
                                        plSuiteLabel, plRallyLabel, plQueueSubtitle, plNativePrefix,
                                    )
                                ) {
                                    nav = nav.open(NavState.Detail.Track)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                    }
                }
            }
            }
            }
            }
            }
            FloatingMiniPlayerOverlay(
                visible = showMiniPlayer,
                enter = navChromeEnter,
                exit = navChromeExit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (!bottomDockVisible) Modifier.navigationBarsPadding() else Modifier),
            ) {
                val miniTitle = if (ui.trackLoading) {
                    ui.loadingTrackTitle?.takeIf { it.isNotBlank() } ?: ui.title
                } else {
                    model.currentStageCode?.let { browse?.displayName(it, languageTag) }
                        ?: ui.linearTrack?.title?.takeIf { it.isNotBlank() }
                        ?: ui.title
                }
                val miniCode = if (ui.trackLoading) ui.loadingTrackCode else model.currentStageCode
                val miniStaffVariant = remember(ui.linearTrack, ui.selectedKey) {
                    com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.detectVariant(
                        canonicalTrackId = model.markerTrackIdFor(ui.linearTrack),
                        selectedKey = ui.selectedKey,
                        assetSha256 = ui.linearTrack?.assetSha256,
                    )
                }
                val nmStaffOffsetMs by model.nmStaffCreditsOffsetMs.collectAsStateWithLifecycle()
                LaunchedEffect(miniStaffVariant, model.packDirectory) {
                    if (miniStaffVariant == com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC) {
                        model.ensureNmStaffCreditsOffsetCalibrated()
                    }
                }
                MiniPlayer(
                    miniTitle,
                    miniCode,
                    model.packDirectory,
                    playbackArtworkNames(model, ui),
                    ui,
                    progressFlow = model.progressUi,
                    staffCreditsVariant = miniStaffVariant,
                    nmStaffCreditsOffsetMs = nmStaffOffsetMs,
                    onOpen = {
                        nav = nav.open(
                            if (ui.trackLoading || ui.flow != null) NavState.Detail.Flow else NavState.Detail.Track,
                        )
                    },
                    onToggle = { if (ui.canPause) model.pause() else if (ui.canResume) model.resume() },
                    onNext = if (ui.linearMode && ui.linearTrack != null) model::skipNext else null,
                )
            }
            }
        }
    }
    AnimatedVisibility(
        visible = showOnboarding,
        enter = fadeIn(tween(if (reduceMotion) 0 else 200, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
        exit = fadeOut(tween(if (reduceMotion) 0 else 220, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
    ) {
        WelcomeOnboardingScreen(
            notificationGranted = notificationGranted,
            busy = ui.busy,
            importState = ui.importState,
            onRequestNotificationPermission = {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onPickPack = openPack,
            onResumeImport = model::resumePackImport,
            onRepickImport = openRepickPack,
            onCancelImport = { model.cancelPackImport() },
            onSkipForLater = {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        OnboardingStore.markCompleted(context)
                    }
                    onboardingCompleted = true
                }
            },
        )
    }
    com.mylittleblover.mkwplayer.ui.unlock.SpecialCupUnlockCeremonyStage(
        visible = ceremonyPlaying || previewUnlockVfx,
        progressFlow = model.progressUi,
        packDirectory = model.packDirectory,
        isPreviewOnly = previewUnlockVfx && !ceremonyPlaying,
        onDismissPreview = if (previewUnlockVfx && !ceremonyPlaying) {
            { previewUnlockVfx = false }
        } else null,
    )
    SpecialCupCongratsDialog(
        visible = ui.unlock?.congratulationOwed == true && !ceremonyPlaying,
        onEnterCup = {
            model.dismissCongratulations()
            nav = NavState(Tab.PLAYLISTS, NavState.Detail.Cup("special_cup"))
        },
        onDismiss = model::dismissCongratulations,
    )
    val unlockState = ui.unlock
    val remainingMysteryCourses = remember(unlockState?.remainingStages, unlockState?.stageSeconds, browse, languageTag) {
        unlockState?.remainingStages.orEmpty().map { code ->
            com.mylittleblover.mkwplayer.ui.unlock.RemainingCourseItem(
                stageCode = code,
                displayName = browse?.displayName(code, languageTag) ?: code,
                listenedSeconds = unlockState?.stageSeconds?.get(code) ?: 0,
            )
        }
    }
    com.mylittleblover.mkwplayer.ui.unlock.LockedMysteryCourseDialog(
        visible = showLockedMysteryDialog && !unlocked && !ceremonyPlaying,
        completedCount = unlockState?.completedCount ?: 0,
        totalCount = unlockState?.eligibleCount ?: 0,
        remainingCourses = remainingMysteryCourses,
        ceremonyReady = unlockState?.ceremonyReady == true,
        onDismiss = { showLockedMysteryDialog = false },
        onCourseClick = { stageCode ->
            showLockedMysteryDialog = false
            nav = nav.open(NavState.Detail.Course(stageCode))
        },
        onQuizPassedAndStartCeremony = {
            showLockedMysteryDialog = false
            if (unlockState?.ceremonyReady == true) {
                model.startSpecialCupUnlockCeremony()
            } else {
                model.passUnlockQuizAndStartCeremony()
            }
        },
    )
    }
}

@Composable
private fun FloatingMiniPlayerOverlay(
    visible: Boolean,
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        content()
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
        MkwCard(
            modifier = Modifier.fillMaxWidth().padding(Tokens.Spacing.md),
            contentPadding = PaddingValues(Tokens.Spacing.md),
        ) {
            Text("Open your soundtrack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (ui.pack != null) {
                Text(ui.pack!!.summary, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
            } else {
                Text(
                    "A pack carries the audio, the stage structures and the timings in one file, so one " +
                        "import is enough.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MkwPrimaryButton(
                    text = if (ui.busy) "Working..." else "Import a pack",
                    onClick = onOpenPack,
                    enabled = !ui.busy,
                )
                MkwSecondaryButton(
                    text = "Add .bwav files",
                    onClick = onOpenFiles,
                    enabled = !ui.busy,
                )
            }

            if (ui.pack != null) {
                MkwSecondaryButton(
                    text = "Open the first stage",
                    onClick = { model.openFirstPackStage() },
                )
            }

            ui.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractiveEntryBottomSheet(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    code: String,
    courseTitle: String,
    onOpenPack: () -> Unit,
    onStartAndEnterConsole: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    MkwBottomSheet(
        onDismissRequest = onDismiss,
        title = androidx.compose.ui.res.stringResource(R.string.interactive_entry_sheet_title),
        subtitle = courseTitle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MkwCard(
                tone = MkwSurfaceTone.Recessed,
                contentPadding = PaddingValues(16.dp),
            ) {
                if (model.canStartStage(code)) {
                    val entryNames = remember(code, ui.pack) { model.initialStageLabels(code) }
                    var selectedEntry by rememberSaveable(code) { mutableStateOf(entryNames.firstOrNull()) }
                    val choices = entryNames.mapIndexed { index, label -> index to label }.toMap()
                    val effectiveEntry = selectedEntry?.takeIf { it in entryNames } ?: entryNames.firstOrNull()
                    com.mylittleblover.mkwplayer.ui.interactive.InteractiveEntryChooser(
                        choices = choices,
                        selected = entryNames.indexOf(effectiveEntry).takeIf { it >= 0 },
                        enabled = !ui.busy && ui.flow?.fading != true,
                        onSelect = { index -> selectedEntry = entryNames.getOrNull(index) },
                        courseCode = code,
                    )
                    Spacer(Modifier.height(4.dp))
                    MkwPrimaryButton(
                        text = androidx.compose.ui.res.stringResource(R.string.discovery_start),
                        onClick = { onStartAndEnterConsole(effectiveEntry) },
                        enabled = !ui.busy && ui.flow?.fading != true,
                        leadingIcon = Icons.Filled.PlayArrow,
                        size = MkwButtonSize.Hero,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.interactive_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                    MkwSecondaryButton(
                        text = androidx.compose.ui.res.stringResource(R.string.discovery_import),
                        onClick = {
                            onDismiss()
                            onOpenPack()
                        },
                        size = MkwButtonSize.Hero,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** A course's detail page, reached from the map or the library. */
@Composable
private fun CoursePage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    code: String,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset> = emptyList(),
    playlistsReferencingPreset: (String) -> List<com.mylittleblover.mkwplayer.library.UserPlaylist> = { emptyList() },
    onOpenFlowEditor: (courseCode: String, presetId: String?) -> Unit = { _, _ -> },
    onPlayCustomPreset: (com.mylittleblover.mkwplayer.library.CoursePreset, courseTitle: String, suiteLabel: String) -> Unit = { _, _, _ -> },
    onDuplicatePreset: (com.mylittleblover.mkwplayer.library.CoursePreset) -> Unit = {},
    onDeletePreset: (com.mylittleblover.mkwplayer.library.CoursePreset) -> Unit = {},
    onPlayMedley: (medleyId: String, suiteLabel: String, rallyLabel: String) -> Unit = { _, _, _ -> },
    onInteractive: () -> Unit,
    onOpenPack: () -> Unit = {},
) {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val names = remember(model) { model.localizedNames }
    val title = names?.name(code, languageTag) ?: androidx.compose.ui.res.stringResource(R.string.map_course)
    val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val saved by discovery.state.collectAsStateWithLifecycle()
    val stableCode = code.removeSuffix("_00")
    val coursePresets = remember(presets, stableCode) {
        presets.filter { it.courseCode.equals(stableCode, ignoreCase = true) }
    }
    var pendingDeletePreset by remember { mutableStateOf<com.mylittleblover.mkwplayer.library.CoursePreset?>(null) }
    var showEntryDrawer by rememberSaveable(stableCode) { mutableStateOf(false) }

    if (showEntryDrawer) {
        InteractiveEntryBottomSheet(
            model = model,
            ui = ui,
            code = code,
            courseTitle = title,
            onOpenPack = onOpenPack,
            onStartAndEnterConsole = { entry ->
                showEntryDrawer = false
                discovery.recordRecent(stableCode)
                model.startStagePerformance(code, entry)
                onInteractive()
            },
            onDismiss = { showEntryDrawer = false },
        )
    }

    pendingDeletePreset?.let { preset ->
        val refs = remember(preset.id, saved.playlists) { playlistsReferencingPreset(preset.id) }
        MkwDialog(
            onDismissRequest = { pendingDeletePreset = null },
            title = androidx.compose.ui.res.stringResource(R.string.preset_delete_warning_title),
            confirmButton = {
                MkwDangerButton(
                    text = androidx.compose.ui.res.stringResource(R.string.preset_delete),
                    onClick = {
                        onDeletePreset(preset)
                        pendingDeletePreset = null
                    },
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(
                    text = androidx.compose.ui.res.stringResource(R.string.discovery_cancel),
                    onClick = { pendingDeletePreset = null },
                )
            },
        ) {
            Text(
                if (refs.isEmpty()) preset.name
                else androidx.compose.ui.res.stringResource(
                    R.string.preset_delete_warning_body,
                    refs.joinToString(", ") { it.name },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MkwKitTokens.Palette.inkSecondary,
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                val favorite = stableCode in saved.favorites
                val label = androidx.compose.ui.res.stringResource(if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add)
                MkwIconButton(
                    icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = label,
                    onClick = { discovery.toggleFavorite(stableCode) },
                    tintOverride = if (favorite) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                )
            }
        }
        item {
            MkwCard(
                modifier = Modifier.fillMaxWidth(),
                tone = MkwSurfaceTone.White,
                contentPadding = PaddingValues(0.dp),
            ) {
                val artNames = (
                    listOfNotNull(
                        model.extraCatalog.courseArtwork[code],
                        model.extraCatalog.courseArtwork[code.removeSuffix("_00")],
                    ) + com.mylittleblover.mkwplayer.ui.art.CourseCoverArt.packCandidates(code)
                ).distinct()
                val cover = com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(code)
                ArtworkSlot(Modifier.fillMaxWidth()) { edge ->
                    val packed = PackArtwork.rememberArtwork(model.packDirectory, artNames, edge)
                    val art = packed ?: cover?.let { rememberResourceArtwork(it, edge) }
                    val frame = Modifier.fillMaxWidth().aspectRatio(rememberArtworkRatio(code, art, 16f / 9f))
                    when {
                        art != null -> FadingArtwork(art.bitmap, frame)
                        cover == null -> com.mylittleblover.mkwplayer.ui.art.CourseCover(
                            code = code,
                            modifier = frame,
                            packDirectory = model.packDirectory,
                            artworkName = artNames.firstOrNull(),
                        )
                        else -> Box(frame)
                    }
                }
            }
        }
        item {
            MkwCard(
                modifier = Modifier.fillMaxWidth(),
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(20.dp),
            ) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(32.dp), tint = MkwKitTokens.Palette.crimson)
                Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                Text(androidx.compose.ui.res.stringResource(R.string.discovery_interactive_note), style = MaterialTheme.typography.bodyMedium, color = MkwKitTokens.Palette.inkSecondary)
                Spacer(Modifier.height(2.dp))
                MkwPrimaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.discovery_open_player),
                    onClick = {
                        if (model.isStageOpen(code)) onInteractive() else showEntryDrawer = true
                    },
                    size = MkwButtonSize.Hero,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            MkwCard(
                modifier = Modifier.fillMaxWidth(),
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Headphones, contentDescription = null, modifier = Modifier.size(20.dp), tint = MkwKitTokens.Palette.inkSecondary)
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_normal_playback), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink)
                }
                val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
                val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
                val medleys = remember(code, ui.pack, ui.unlock) { model.courseMedleysForCourse(code) }
                if (medleys.isEmpty()) {
                    Text(suiteLabel, style = MaterialTheme.typography.labelLarge, color = MkwKitTokens.Palette.crimson, fontWeight = FontWeight.SemiBold)
                    Text(androidx.compose.ui.res.stringResource(R.string.discovery_suite_note), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                } else {
                    Spacer(Modifier.height(4.dp))
                    medleys.forEach { (medley, playable) ->
                        val label = androidx.compose.ui.res.stringResource(
                            com.mylittleblover.mkwplayer.ui.routes.builtInSuiteLabelRes(medley.variant),
                        )
                        val totalSec = (medley.durationMs / 1000L).coerceAtLeast(0L)
                        val dur = "%d:%02d".format(java.util.Locale.ROOT, totalSec / 60L, totalSec % 60L)
                        MkwCard(
                            onClick = {
                                discovery.recordRecent(stableCode)
                                onPlayMedley(medley.id, suiteLabel, rallyLabel)
                            },
                            enabled = playable,
                            tone = MkwSurfaceTone.RoseTint,
                            selected = playable,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = if (playable) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (playable) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = dur,
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                                    color = if (playable) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                                )
                            }
                        }
                    }
                    if (medleys.none { it.second }) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.discovery_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                }

                MkwDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.preset_user_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.ink,
                )
                coursePresets.forEach { preset ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        MkwCard(
                            onClick = {
                                discovery.recordRecent(stableCode)
                                onPlayCustomPreset(preset, title, suiteLabel)
                            },
                            enabled = model.canStartStage(code),
                            tone = MkwSurfaceTone.White,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp), tint = MkwKitTokens.Palette.crimson)
                                Text(
                                    preset.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.ink,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        MkwIconButton(
                            icon = Icons.Filled.Edit,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_edit),
                            onClick = { onOpenFlowEditor(stableCode, preset.id) },
                            visualSize = 36.dp,
                            iconSize = 18.dp,
                        )
                        MkwIconButton(
                            icon = Icons.Filled.ContentCopy,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_duplicate),
                            onClick = { onDuplicatePreset(preset) },
                            visualSize = 36.dp,
                            iconSize = 18.dp,
                        )
                        MkwIconButton(
                            icon = Icons.Filled.DeleteOutline,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_delete),
                            onClick = { pendingDeletePreset = preset },
                            visualSize = 36.dp,
                            iconSize = 18.dp,
                            tintOverride = MkwKitTokens.Palette.danger,
                        )
                    }
                }
                MkwSecondaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.preset_new),
                    onClick = { onOpenFlowEditor(stableCode, null) },
                    enabled = model.canStartStage(code),
                    leadingIcon = Icons.Filled.Add,
                    size = MkwButtonSize.Hero,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Detail page for extra interactive music (SFC retro courses, Battle tracks, and Title Theme). */
@Composable
private fun ExtraInteractiveDetailPage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    extraId: String,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset> = emptyList(),
    playlistsReferencingPreset: (String) -> List<com.mylittleblover.mkwplayer.library.UserPlaylist> = { emptyList() },
    onOpenFlowEditor: (editorCode: String, presetId: String?) -> Unit = { _, _ -> },
    onPlayCustomPreset: (com.mylittleblover.mkwplayer.library.CoursePreset, trackTitle: String, suiteLabel: String) -> Unit = { _, _, _ -> },
    onDuplicatePreset: (com.mylittleblover.mkwplayer.library.CoursePreset) -> Unit = {},
    onDeletePreset: (com.mylittleblover.mkwplayer.library.CoursePreset) -> Unit = {},
    onPlayExtraMedley: (medleyId: String, medleyTitle: String, medleySubtitle: String, artworkName: String?) -> Unit = { _, _, _, _ -> },
    onInteractive: () -> Unit,
) {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val saved by discovery.state.collectAsStateWithLifecycle()

    val sfcTrack = remember(extraId, model.extraCatalog) {
        model.extraCatalog.sfcCourses.firstOrNull { it.id == extraId }
    }
    val battleTrack = remember(extraId, model.extraCatalog) {
        model.extraCatalog.battleTracks.firstOrNull { it.id == extraId }
    }
    val titleTrack = remember(extraId, model.extraCatalog) {
        model.extraCatalog.otherMusic
            .filterIsInstance<com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle>()
            .firstOrNull { it.id == extraId }
    }

    val title = sfcTrack?.localizedName(languageTag)
        ?: battleTrack?.localizedName(languageTag)
        ?: titleTrack?.localizedName(languageTag)
        ?: extraId
    val artworkName = sfcTrack?.artwork ?: battleTrack?.artwork ?: titleTrack?.artwork
    val favoriteRef = when {
        sfcTrack != null -> com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack(sfcTrack.id).raw
        battleTrack != null -> com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack(battleTrack.id).raw
        else -> com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack(extraId).raw
    }
    val editorCode = "extra:$extraId"
    val extraPresets = remember(presets, editorCode) {
        presets.filter { it.courseCode.equals(editorCode, ignoreCase = true) }
    }
    val builtInMedleys = remember(extraId, model.extraMedleyCatalog) {
        model.extraMedleyCatalog.forTarget(extraId)
    }
    var pendingDeletePreset by remember { mutableStateOf<com.mylittleblover.mkwplayer.library.CoursePreset?>(null) }

    if (sfcTrack == null && battleTrack == null && titleTrack == null) {
        Text(androidx.compose.ui.res.stringResource(R.string.discovery_empty), Modifier.padding(24.dp))
        return
    }

    pendingDeletePreset?.let { preset ->
        val refs = remember(preset.id, saved.playlists) { playlistsReferencingPreset(preset.id) }
        MkwDialog(
            onDismissRequest = { pendingDeletePreset = null },
            title = androidx.compose.ui.res.stringResource(R.string.preset_delete_warning_title),
            confirmButton = {
                MkwDangerButton(
                    text = androidx.compose.ui.res.stringResource(R.string.preset_delete),
                    onClick = {
                        onDeletePreset(preset)
                        pendingDeletePreset = null
                    },
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(
                    text = androidx.compose.ui.res.stringResource(R.string.discovery_cancel),
                    onClick = { pendingDeletePreset = null },
                )
            },
        ) {
            Text(
                if (refs.isEmpty()) preset.name
                else androidx.compose.ui.res.stringResource(
                    R.string.preset_delete_warning_body,
                    refs.joinToString(", ") { it.name },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MkwKitTokens.Palette.inkSecondary,
            )
        }
    }

    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
    val categoryLabel = when {
        sfcTrack != null -> androidx.compose.ui.res.stringResource(R.string.discovery_vs_sfc)
        battleTrack != null -> androidx.compose.ui.res.stringResource(R.string.discovery_battle)
        else -> androidx.compose.ui.res.stringResource(R.string.discovery_other)
    }
    val canOpenEditor = remember(editorCode, ui.pack) { model.inspectCourseForEditor(editorCode) != null }
    val favorite = favoriteRef in saved.favorites
    val favoriteLabel = androidx.compose.ui.res.stringResource(
        if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add,
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(20.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "extra-hero-$editorCode") {
            MkwCard(
                modifier = Modifier.fillMaxWidth(),
                tone = MkwSurfaceTone.White,
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val artNames = listOfNotNull(artworkName)
                    ArtworkSlot(Modifier.fillMaxWidth()) { edge ->
                        val packed = PackArtwork.rememberArtwork(model.packDirectory, artNames, edge)
                        val frame = Modifier
                            .fillMaxWidth()
                            .aspectRatio(rememberArtworkRatio(editorCode, packed, 16f / 9f))
                        if (packed != null) {
                            FadingArtwork(packed.bitmap, frame)
                        } else {
                            Box(
                                frame.background(MkwKitTokens.Palette.rose),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = MkwKitTokens.Palette.crimsonDeep,
                                    modifier = Modifier.size(56.dp),
                                )
                            }
                        }
                    }
                    // Subtle top scrim & frosted category pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                    ) {
                        Surface(
                            color = MkwKitTokens.Palette.ground.copy(alpha = 0.92f),
                            shape = MkwKitTokens.Shape.pill,
                            border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = MkwKitTokens.Palette.crimson,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = categoryLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.ink,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, top = 14.dp, end = 10.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        if (builtInMedleys.isNotEmpty()) {
                            val totalSec = (builtInMedleys.first().medley.durationMs / 1000L).coerceAtLeast(0L)
                            val dur = "%d:%02d".format(java.util.Locale.ROOT, totalSec / 60L, totalSec % 60L)
                            Text(
                                text = "$suiteLabel · $dur",
                                style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                    }
                    MkwIconButton(
                        icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = favoriteLabel,
                        onClick = { discovery.toggleFavorite(favoriteRef) },
                        tintOverride = if (favorite) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }
        }

        item(key = "extra-interactive-$editorCode") {
            MkwCard(
                modifier = Modifier.fillMaxWidth(),
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MkwKitTokens.Palette.rose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MkwKitTokens.Palette.crimson,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.discovery_interactive),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.discovery_interactive_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                MkwPrimaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.discovery_open_player),
                    onClick = {
                        discovery.recordRecent(favoriteRef)
                        when {
                            sfcTrack != null -> model.openInteractiveTwoState(sfcTrack, isBattle = false, languageTag = languageTag, autoStart = true)
                            battleTrack != null -> model.openInteractiveTwoState(battleTrack, isBattle = true, languageTag = languageTag, autoStart = true)
                            titleTrack != null -> model.openInteractiveTitle(languageTag = languageTag, autoStart = false)
                        }
                        onInteractive()
                    },
                    size = MkwButtonSize.Hero,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "extra-normal-$editorCode") {
            MkwCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(180, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MkwKitTokens.Palette.inkSecondary,
                    )
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.discovery_normal_playback),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MkwKitTokens.Palette.ink,
                    )
                }
                if (builtInMedleys.isEmpty()) {
                    Text(
                        suiteLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MkwKitTokens.Palette.crimson,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.discovery_suite_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    builtInMedleys.forEach { entry ->
                        val label = entry.localizedLabel(languageTag, title)
                        val totalSec = (entry.medley.durationMs / 1000L).coerceAtLeast(0L)
                        val dur = "%d:%02d".format(java.util.Locale.ROOT, totalSec / 60L, totalSec % 60L)
                        val interaction = remember(entry.medley.id) { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                discovery.recordRecent(favoriteRef)
                                onPlayExtraMedley(entry.medley.id, label, suiteLabel, entry.artwork ?: artworkName)
                            },
                            enabled = ui.pack != null,
                            interactionSource = interaction,
                            color = if (ui.pack != null) MkwKitTokens.Palette.rose else MkwKitTokens.Palette.inset,
                            shape = MkwKitTokens.Shape.control,
                            border = BorderStroke(
                                MkwKitTokens.Stroke.border,
                                if (ui.pack != null) MkwKitTokens.Palette.crimson.copy(alpha = 0.22f) else MkwKitTokens.Palette.hairline,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MkwKitTokens.Size.optionRowMin)
                                .mkwPressScale(interaction, enabled = ui.pack != null),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (ui.pack != null) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.trackStrong,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = MkwKitTokens.Palette.ground,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (ui.pack != null) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = dur,
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                                    color = if (ui.pack != null) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                                )
                            }
                        }
                    }
                }

                MkwDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.preset_user_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.ink,
                )
                extraPresets.forEach { preset ->
                    val presetInteraction = remember(preset.id) { MutableInteractionSource() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(
                            onClick = {
                                discovery.recordRecent(favoriteRef)
                                onPlayCustomPreset(preset, title, suiteLabel)
                            },
                            enabled = canOpenEditor,
                            interactionSource = presetInteraction,
                            color = MkwKitTokens.Palette.ground,
                            shape = MkwKitTokens.Shape.control,
                            border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = MkwKitTokens.Size.touchMin)
                                .mkwPressScale(presetInteraction, enabled = canOpenEditor),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MkwKitTokens.Palette.crimson,
                                )
                                Text(
                                    preset.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.ink,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        MkwIconButton(
                            icon = Icons.Filled.Edit,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_edit),
                            onClick = { onOpenFlowEditor(editorCode, preset.id) },
                            visualSize = 36.dp,
                            iconSize = 18.dp,
                        )
                        MkwIconButton(
                            icon = Icons.Filled.ContentCopy,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_duplicate),
                            onClick = { onDuplicatePreset(preset) },
                            visualSize = 36.dp,
                            iconSize = 18.dp,
                        )
                        MkwIconButton(
                            icon = Icons.Filled.DeleteOutline,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.preset_delete),
                            onClick = { pendingDeletePreset = preset },
                            visualSize = 36.dp,
                            iconSize = 18.dp,
                            tintOverride = MkwKitTokens.Palette.danger,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                MkwSecondaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.preset_new),
                    onClick = { onOpenFlowEditor(editorCode, null) },
                    enabled = canOpenEditor,
                    leadingIcon = Icons.Filled.Add,
                    size = MkwButtonSize.Hero,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The world map: the shipped artwork, full screen, draggable and zoomable.
 *
 * The pins come from the anchor file the map was measured against, so a pin sits on its own diorama rather
 * than on a guess. The drag is clamped inside the map, and there is a reset button, because a map that can be
 * pushed off-screen with one flick is a map that has to be reloaded.
 */
@Composable
private fun MapPage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    state: WorldMapState,
    discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
    officialRouteFreeRoam: Map<String, com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry>,
    onOpenCourse: (String) -> Unit,
    onOpenCustomRoute: (String) -> Unit,
    onPlayStarted: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val suiteLabel = androidx.compose.ui.res.stringResource(R.string.discovery_suite)
    val rallyLabel = androidx.compose.ui.res.stringResource(R.string.discovery_rally)
    val roadPrefix = androidx.compose.ui.res.stringResource(R.string.route_road_prefix)
    val subTag = androidx.compose.ui.res.stringResource(R.string.route_custom_substitute_tag)
    val loaded by produceState<Result<MapResources>?>(null, model, model.packDirectory, ui.libraryGeneration) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val anchors = requireNotNull(model.mapAnchors)
                val routes = model.officialRouteCatalog ?: requireNotNull(OfficialRouteCatalog.parse(
                    context.assets.open("official_routes.json").bufferedReader().use { it.readText() },
                    anchors.stages.map { it.code }.toSet(),
                ))
                val names = model.localizedNames ?: requireNotNull(LocalizedNames.parse(
                    context.assets.open("localized_names.json").bufferedReader().use { it.readText() },
                ))
                val connections = model.stageConnections ?: runCatching {
                    com.mylittleblover.mkwplayer.library.StageConnections.parse(
                        context.assets.open("stage_connections.json").bufferedReader().use { it.readText() })
                }.getOrNull()
                val image = com.mylittleblover.mkwplayer.ui.art.MapArtworkCache.load(
                    context = context,
                    packDirectory = model.packDirectory,
                    anchors = anchors,
                    connections = connections,
                )
                MapResources(image, anchors, routes, names, connections)
            }
        }
    }
    val data = loaded?.getOrNull()
    if (data == null) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loaded == null) androidx.compose.material3.CircularProgressIndicator()
                Text(androidx.compose.ui.res.stringResource(if (loaded == null) R.string.map_loading else R.string.map_data_missing))
            }
        }
        return
    }
    val playable = remember(ui.pack, ui.flow?.title, ui.libraryGeneration, data) { data.routes.stageCodes(true).filter(model::canStartStage).toSet() }
    OfficialMapScreen(
        image = data.image, anchors = data.anchors, routes = data.routes, names = data.names,
        languageTag = languageTag, specialUnlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(ui.unlock),
        playableStages = playable, state = state, onOpenCourse = onOpenCourse,
        connections = data.connections,
        installedJukebox = remember(ui.jukebox) { model.allInstalledJukeboxItems() },
        packDirectory = model.packDirectory,
        inspectOfficialRoute = { route, overrides, ruleMode ->
            val pfrCfg = officialRouteFreeRoam[route.id]?.config
            model.inspectOfficialRoute(
                route = route,
                sessionOverrides = overrides,
                ruleMode = ruleMode,
                pureFreeRoamEnabled = pfrCfg?.enabled == true,
                pureFreeRoamTrackPool = pfrCfg?.trackPool
                    ?: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamTrackPool.ROUTE_RULES,
            )
        },
        inspectDraftRoute = { pairs, edgeCfgs, ruleMode ->
            val stopCodes = if (pairs.isEmpty()) emptyList() else listOf(pairs.first().first) + pairs.map { it.second }
            val synthetic = com.mylittleblover.mkwplayer.routes.CustomRoute(
                id = state.editingRouteId ?: "draft",
                name = state.editingRouteName,
                stops = stopCodes.mapIndexed { idx, code ->
                    com.mylittleblover.mkwplayer.routes.CustomRouteStop(code, state.draftStopPresets[idx])
                },
                edges = pairs.indices.map { idx ->
                    edgeCfgs[idx] ?: com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig(
                        from = pairs[idx].first,
                        to = pairs[idx].second,
                    )
                },
                loopWholeRoute = state.draftLoopWholeRoute,
                roadRuleMode = ruleMode,
                routeSuiteMode = state.draftRouteSuiteMode,
                pureFreeRoamConfig = state.draftPureFreeRoamConfig,
            )
            model.inspectCustomRoute(synthetic)
        },
        onPlayOfficialRoute = { route, loop, overrides, ruleMode, suiteMode ->
            val entry = officialRouteFreeRoam[route.id]
                ?: com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry()
            if (model.playOfficialRoute(
                    route = route,
                    userPresets = presets,
                    loopRoute = loop,
                    sessionOverrides = overrides,
                    languageTag = languageTag,
                    suiteLabel = suiteLabel,
                    rallyLabel = rallyLabel,
                    roadPrefix = roadPrefix,
                    ruleMode = ruleMode,
                    suiteMode = suiteMode,
                    pureFreeRoamConfig = entry.config,
                    pureFreeRoamEdgeOverrides = entry.edgeOverrides,
                ) is PlayerViewModel.RoutePlayResult.Started
            ) {
                onPlayStarted()
            }
        },
        onSaveCustomRoute = { routeId, routeName, stops, edges, loop, ruleMode, routeSuiteMode ->
            discovery.saveCustomRoute(routeId, routeName, stops, edges, loop, ruleMode, routeSuiteMode, state.draftPureFreeRoamConfig) { res ->
                res.onSuccess { saved ->
                    state.exitEdit()
                    onOpenCustomRoute(saved.id)
                }
            }
        },
        onPlayCustomDraft = { routeName, stops, edges, loop, ruleMode, routeSuiteMode ->
            val draftRoute = com.mylittleblover.mkwplayer.routes.CustomRoute(
                id = state.editingRouteId ?: "draft",
                name = routeName,
                stops = stops,
                edges = edges,
                loopWholeRoute = loop,
                roadRuleMode = ruleMode,
                routeSuiteMode = routeSuiteMode,
                pureFreeRoamConfig = state.draftPureFreeRoamConfig,
            )
            if (model.playCustomRoute(
                    route = draftRoute,
                    userPresets = presets,
                    languageTag = languageTag,
                    suiteLabel = suiteLabel,
                    rallyLabel = rallyLabel,
                    roadPrefix = roadPrefix,
                    customSubstituteTag = subTag,
                ) is PlayerViewModel.RoutePlayResult.Started
            ) {
                onPlayStarted()
            }
        },
        officialFreeRoamEntryFor = { routeId ->
            officialRouteFreeRoam[routeId]
                ?: com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry()
        },
        onOfficialFreeRoamConfigChange = { routeId, cfg ->
            discovery.setOfficialRouteFreeRoamConfig(routeId, cfg)
        },
        onOfficialFreeRoamEdgeOverrideChange = { routeId, edgeIdx, ov ->
            discovery.setOfficialRouteFreeRoamEdgeOverride(routeId, edgeIdx, ov)
        },
        onPlayOfficialRouteWithFreeRoam = { route, loop, overrides, ruleMode, suiteMode, pfrCfg, pfrOverrides ->
            if (model.playOfficialRoute(
                    route = route,
                    userPresets = presets,
                    loopRoute = loop,
                    sessionOverrides = overrides,
                    languageTag = languageTag,
                    suiteLabel = suiteLabel,
                    rallyLabel = rallyLabel,
                    roadPrefix = roadPrefix,
                    ruleMode = ruleMode,
                    suiteMode = suiteMode,
                    pureFreeRoamConfig = pfrCfg,
                    pureFreeRoamEdgeOverrides = pfrOverrides,
                ) is PlayerViewModel.RoutePlayResult.Started
            ) {
                onPlayStarted()
            }
        },
        onSaveCustomRouteWithFreeRoam = { routeId, routeName, stops, edges, loop, ruleMode, routeSuiteMode, pfrCfg ->
            discovery.saveCustomRoute(routeId, routeName, stops, edges, loop, ruleMode, routeSuiteMode, pfrCfg) { res ->
                res.onSuccess { saved ->
                    state.exitEdit()
                    onOpenCustomRoute(saved.id)
                }
            }
        },
        onPlayCustomDraftWithFreeRoam = { routeName, stops, edges, loop, ruleMode, routeSuiteMode, pfrCfg ->
            val draftRoute = com.mylittleblover.mkwplayer.routes.CustomRoute(
                id = state.editingRouteId ?: "draft",
                name = routeName,
                stops = stops,
                edges = edges,
                loopWholeRoute = loop,
                roadRuleMode = ruleMode,
                routeSuiteMode = routeSuiteMode,
                pureFreeRoamConfig = pfrCfg,
            )
            if (model.playCustomRoute(
                    route = draftRoute,
                    userPresets = presets,
                    languageTag = languageTag,
                    suiteLabel = suiteLabel,
                    rallyLabel = rallyLabel,
                    roadPrefix = roadPrefix,
                    customSubstituteTag = subTag,
                ) is PlayerViewModel.RoutePlayResult.Started
            ) {
                onPlayStarted()
            }
        },
    )
}

private data class MapResources(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val anchors: com.mylittleblover.mkwplayer.library.MapAnchors.Catalog,
    val routes: OfficialRouteCatalog,
    val names: LocalizedNames,
    val connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? = null,
)

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

@Composable
private fun PlayerDismissContainer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 88.dp.toPx() }
    val maxDragPx = with(density) { 260.dp.toPx() }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }

    fun finishDrag(velocityY: Float) {
        if (dismissed) return
        scope.launch {
            if (offsetY.value > dismissThresholdPx || (offsetY.value > 18f && velocityY > 900f)) {
                dismissed = true
                onDismiss()
            } else {
                offsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = com.mylittleblover.mkwplayer.ui.AppMotion.dragReleaseSpring,
                    initialVelocity = velocityY,
                )
            }
        }
    }

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dismissed || offsetY.value <= 0f) return Offset.Zero
                val next = (offsetY.value + available.y).coerceIn(0f, maxDragPx)
                val consumedY = next - offsetY.value
                scope.launch { offsetY.snapTo(next) }
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (dismissed || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val next = (offsetY.value + available.y).coerceIn(0f, maxDragPx)
                val consumedY = next - offsetY.value
                if (consumedY > 0f) scope.launch { offsetY.snapTo(next) }
                return Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 0f) {
                    finishDrag(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                val fraction = (offsetY.value / maxDragPx).coerceIn(0f, 1f)
                alpha = 1f - fraction * 0.25f
            }
            .nestedScroll(nestedScroll),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (!dismissed) {
                            scope.launch {
                                offsetY.snapTo((offsetY.value + delta).coerceIn(0f, maxDragPx))
                            }
                        }
                    },
                    onDragStopped = { velocity -> finishDrag(velocity) },
                )
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            com.mylittleblover.mkwplayer.ui.DragHandlePill()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

/**
 * The performance console.
 *
 * Three events and one switch, each enabled only when it means something. A control that is always available
 * is a control that is sometimes a no-op, and the listener cannot tell which.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ConsolePage(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    onOpenPack: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSleepTimer: () -> Unit = {},
) {
    val flow = ui.flow
    val isLoading = ui.trackLoading
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val names = remember(model) { model.localizedNames }
    val activeStageCode = if (isLoading) ui.loadingTrackCode else model.currentStageCode
    val displayTitle = if (isLoading) {
        ui.loadingTrackTitle?.takeIf { it.isNotBlank() }
            ?: activeStageCode?.let { names?.name(it, languageTag) }
            ?: androidx.compose.ui.res.stringResource(R.string.interactive_loading_title)
    } else {
        model.currentStageCode?.let { names?.name(it, languageTag) } ?: ui.title
    }

    val coverArtNames = playbackArtworkNames(model, ui)
    var showInteractiveTimerSheet by rememberSaveable { mutableStateOf(false) }
    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }
    var showEqSheet by rememberSaveable { mutableStateOf(false) }

    if (showInteractiveTimerSheet && flow != null && !isLoading) {
        com.mylittleblover.mkwplayer.ui.components.InteractiveTimerSheet(
            timer = ui.interactiveTimer,
            flow = flow,
            onStart = model::startInteractiveTimerSeconds,
            onCancel = model::cancelInteractiveTimer,
            onDismiss = { showInteractiveTimerSheet = false },
        )
    }
    if (showSpeedSheet) {
        com.mylittleblover.mkwplayer.ui.components.SpeedPitchSheet(
            rate = ui.playbackRate,
            onUpdate = model::setPlaybackRate,
            onReset = model::resetPlaybackRate,
            onDismiss = { showSpeedSheet = false },
        )
    }
    if (showEqSheet) {
        com.mylittleblover.mkwplayer.ui.components.EqualizerSheet(
            config = ui.dspConfig,
            onUpdate = model::setMasterDsp,
            onDismiss = { showEqSheet = false },
        )
    }

    val reducedMotion = MkwMotion.rememberPrefersReducedMotion()
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isLoading) {
            com.mylittleblover.mkwplayer.ui.MkwLinearIndeterminateBar(
                color = MkwKitTokens.Palette.crimson,
                trackColor = MkwKitTokens.Palette.rose,
                barHeight = 4.dp,
                modifier = Modifier.fillMaxWidth().testTag("interactive-loading-bar"),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("interactive-page"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 640.dp) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerCover(activeStageCode, model.packDirectory, coverArtNames, Modifier.weight(1f), isLoading = isLoading)
                            PlayerInformation(model, ui, displayTitle, Modifier.weight(1f), isLoading = isLoading)
                        }
                    } else {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlayerCover(activeStageCode, model.packDirectory, coverArtNames, Modifier.fillMaxWidth(), maxCoverWidth = 216.dp, isLoading = isLoading)
                            PlayerInformation(model, ui, displayTitle, Modifier.fillMaxWidth(), isLoading = isLoading)
                        }
                    }
                }
            }
            if (!isLoading && ui.liveVisualizerEnabled) {
                item(key = "interactive-visualizer") {
                    val bars by model.spectrumBars.collectAsStateWithLifecycle()
                    MkwInsetBox(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        com.mylittleblover.mkwplayer.ui.components.LiveSpectrumVisualizer(
                            bands = bars,
                            barColorPrimary = MkwKitTokens.Palette.crimson,
                            barColorSecondary = MkwKitTokens.Palette.crimsonDeep,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                        )
                    }
                }
            }
            if (isLoading) {
                item(key = "interactive-loading-card") {
                    MkwCard(
                        modifier = Modifier.fillMaxWidth().testTag("interactive-loading-card"),
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.interactive_loading_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MkwKitTokens.Palette.ink,
                            )
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.interactive_loading_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                    }
                }
            } else if (flow != null) {
                item(key = "interactive-stage-card") {
                    MkwCard(
                        modifier = Modifier.fillMaxWidth().animateContentSize(tween(200, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        if (flow.performanceBlocker != null) {
                            Text(flow.performanceBlocker, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.danger)
                        } else {
                            com.mylittleblover.mkwplayer.ui.interactive.InteractivePhaseControlZone(
                                flow = flow,
                                paused = ui.canResume,
                                onAdvance = model::advancePerformance,
                                onFast = model::toggleFast,
                                onCancel = model::cancelPendingPerformance,
                                onEnd = model::takeOutro,
                                progressFlow = model.progressUi,
                                courseCode = activeStageCode,
                                pendingCountdownFramesProvider = model::estimatedFramesUntilPendingSwitch,
                            )
                        }
                    }
                }

                val showEntryCard = !ui.canStop && model.initialEntryChoices.isNotEmpty()
                val showSpeakerToggle = flow.hasSpeakerOverlay && !flow.isRainbowMelodyOverlay
                val showExtraControls = flow.performanceBlocker == null &&
                    ((flow.outros.size > 1 && flow.canEnd) || flow.versionNumbers.isNotEmpty() || showSpeakerToggle)
                if (showEntryCard || showExtraControls) {
                    item(key = "interactive-arrangement-card") {
                        MkwCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(tween(180, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
                            tone = MkwSurfaceTone.White,
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            AnimatedVisibility(
                                visible = showEntryCard,
                                enter = MkwMotion.panelEnter(reducedMotion),
                                exit = MkwMotion.panelExit(reducedMotion),
                            ) {
                                com.mylittleblover.mkwplayer.ui.interactive.InteractiveEntryChooser(
                                    choices = model.initialEntryChoices,
                                    selected = model.selectedEntrySegment,
                                    enabled = !ui.canStop && !ui.busy,
                                    onSelect = model::selectInitialEntry,
                                    courseCode = activeStageCode,
                                )
                            }
                            if (flow.performanceBlocker == null) {
                                if (flow.outros.size > 1 && flow.canEnd) {
                                    com.mylittleblover.mkwplayer.ui.interactive.InteractiveOutroChooser(
                                        outros = flow.outros,
                                        enabled = flow.canEnd,
                                        onSelect = { number -> model.takeOutro(number) },
                                    )
                                }
                                if (flow.versionNumbers.isNotEmpty()) {
                                    com.mylittleblover.mkwplayer.ui.interactive.InteractiveVersionChooser(
                                        versionNumbers = flow.versionNumbers,
                                        selectedVersion = flow.selectedVersion,
                                        availableVersions = flow.availableVersions,
                                        onSelect = { number -> model.selectRecordingVersion(number) },
                                        courseCode = activeStageCode,
                                    )
                                }
                                AnimatedVisibility(
                                    visible = showSpeakerToggle,
                                    enter = MkwMotion.panelEnter(reducedMotion),
                                    exit = MkwMotion.panelExit(reducedMotion),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(R.string.discovery_speaker_overlay),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MkwKitTokens.Palette.ink,
                                            )
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(R.string.discovery_speaker_overlay_hint),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MkwKitTokens.Palette.inkSecondary,
                                            )
                                        }
                                        MkwSwitch(
                                            checked = flow.speakerEnabled,
                                            onCheckedChange = { model.toggleSpeakerOverlay() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (ui.interactiveTimer.active && ui.interactiveTimer.action != null) {
                    item(key = "interactive-active-timer") {
                        MkwCard(
                            onClick = { showInteractiveTimerSheet = true },
                            tone = MkwSurfaceTone.RoseTint,
                            selected = true,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                val remain = com.mylittleblover.mkwplayer.ui.components.formatCountdownMs(ui.interactiveTimer.remainingMs)
                                val actText = com.mylittleblover.mkwplayer.ui.components.interactiveTimedActionLabel(ui.interactiveTimer.action)
                                Text(
                                    text = if (ui.interactiveTimer.pausedByTransport) {
                                        androidx.compose.ui.res.stringResource(R.string.interactive_timer_paused_badge, remain) + " · " + actText
                                    } else {
                                        androidx.compose.ui.res.stringResource(R.string.interactive_timer_remaining_fmt, remain, actText)
                                    },
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                MkwIconButton(
                                    icon = Icons.Filled.Close,
                                    contentDescription = androidx.compose.ui.res.stringResource(R.string.interactive_timer_cancel),
                                    onClick = model::cancelInteractiveTimer,
                                    visualSize = 30.dp,
                                    iconSize = 16.dp,
                                    tintOverride = MkwKitTokens.Palette.crimsonDeep,
                                )
                            }
                        }
                    }
                }

                item(key = "interactive-utility-dock") {
                    MkwUtilityDock {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MkwUtilityTile(
                                icon = Icons.Filled.Schedule,
                                label = if (ui.interactiveTimer.active) {
                                    com.mylittleblover.mkwplayer.ui.components.formatCountdownMs(ui.interactiveTimer.remainingMs)
                                } else {
                                    androidx.compose.ui.res.stringResource(R.string.interactive_timer_button)
                                },
                                active = ui.interactiveTimer.active,
                                enabled = ui.canStop && flow.performanceBlocker == null,
                                onClick = { showInteractiveTimerSheet = true },
                                modifier = Modifier.weight(1f),
                            )
                            val rateModified = abs(ui.playbackRate.speed - 1f) > 0.01f || abs(ui.playbackRate.pitchSemitones) > 0.05f
                            MkwUtilityTile(
                                icon = Icons.Filled.Speed,
                                label = if (rateModified) {
                                    java.lang.String.format(java.util.Locale.ROOT, "%.2fx", ui.playbackRate.speed)
                                } else {
                                    androidx.compose.ui.res.stringResource(R.string.speed_short_label)
                                },
                                active = rateModified,
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier.weight(1f),
                            )
                            val eqOn = ui.dspConfig.eqEnabled || ui.dspConfig.bassBoostPercent > 0 || ui.dspConfig.loudnessDb >= 0.05f
                            MkwUtilityTile(
                                icon = Icons.Filled.Equalizer,
                                label = androidx.compose.ui.res.stringResource(R.string.eq_short_label),
                                active = eqOn,
                                onClick = { showEqSheet = true },
                                modifier = Modifier.weight(1f),
                            )
                            MkwUtilityTile(
                                icon = Icons.Filled.Bedtime,
                                label = if (ui.sleepTimer.mode == com.mylittleblover.mkwplayer.player.SleepTimerMode.COUNTDOWN) {
                                    com.mylittleblover.mkwplayer.ui.components.formatCountdownMs(ui.sleepTimer.remainingMs)
                                } else {
                                    androidx.compose.ui.res.stringResource(R.string.sleep_short_label)
                                },
                                active = ui.sleepTimer.active,
                                onClick = onOpenSleepTimer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (!isLoading && ui.busy && flow == null) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = MkwKitTokens.Palette.crimson)
                    }
                }
            } else if (!isLoading && (flow == null || ui.detail != null)) {
                item {
                    if (flow == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MkwSecondaryButton(
                            text = androidx.compose.ui.res.stringResource(R.string.interactive_import),
                            onClick = onOpenPack,
                        )
                        MkwSecondaryButton(
                            text = androidx.compose.ui.res.stringResource(R.string.interactive_files),
                            onClick = onOpenFiles,
                        )
                    }
                    ui.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary) }
                }
            }
        }
    }
}

@Composable
private fun FavoriteToggleButton(
    favoriteId: String?,
    favorites: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (favoriteId == null) return
    val favorite = favoriteId in favorites
    val label = androidx.compose.ui.res.stringResource(
        if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add,
    )
    MkwIconButton(
        icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = label,
        onClick = { onToggle(favoriteId) },
        tintOverride = if (favorite) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
    )
}

@Composable
private fun PlayerCover(
    code: String?,
    packDirectory: java.io.File?,
    artworkNames: List<String>,
    modifier: Modifier = Modifier,
    maxCoverWidth: Dp = 440.dp,
    isLoading: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArtworkSlot(Modifier.widthIn(max = maxCoverWidth).fillMaxWidth()) { edge ->
            val packLookup = PackArtwork.rememberArtworkState(packDirectory, artworkNames, edge)
            val loadedArt = packLookup.loaded
            val ratio = rememberArtworkRatio(
                artworkNames to code,
                loadedArt,
                if (code != null) 16f / 9f else 1f,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                shape = MkwKitTokens.Shape.dialog,
                color = MkwKitTokens.Palette.card,
                border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
            ) {
                when {
                    loadedArt != null -> {
                        FadingArtwork(loadedArt.bitmap, Modifier.fillMaxSize(), animate = !packLookup.fromCache)
                    }
                    code != null && !PackArtwork.hasAnyCandidate(packDirectory, artworkNames) -> {
                        val coverRes = com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(code)
                        if (coverRes != null) {
                            val coverLookup = com.mylittleblover.mkwplayer.ui.art.rememberResourceArtworkState(coverRes, edge)
                            coverLookup.loaded?.let {
                                FadingArtwork(it.bitmap, Modifier.fillMaxSize(), ContentScale.Crop, animate = !coverLookup.fromCache)
                            }
                        } else {
                            com.mylittleblover.mkwplayer.ui.art.CoursePlaceholderContent(code, edge = edge, showIcon = false)
                        }
                    }
                    isLoading && artworkNames.isEmpty() && code == null -> {
                        Box(
                            Modifier.fillMaxSize().background(MkwKitTokens.Palette.rose),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.crimson,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                    code != null -> {
                        // Pack artwork exists and is decoding; keep neutral card while loading cleanly.
                        Box(Modifier.fillMaxSize().background(MkwKitTokens.Palette.inset))
                    }
                    else -> {
                        Box(
                            Modifier.fillMaxSize().background(MkwKitTokens.Palette.inset),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.inkSecondary,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerInformation(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    title: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val flow = ui.flow
    val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val saved by discovery.state.collectAsStateWithLifecycle()
    val favoriteId = if (isLoading) {
        ui.activeFavoriteId ?: ui.loadingTrackCode?.removeSuffix("_00")
    } else {
        ui.activeFavoriteId ?: model.currentStageCode?.removeSuffix("_00")
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.discovery_interactive),
            color = MkwKitTokens.Palette.crimson,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FavoriteToggleButton(
                favoriteId = favoriteId,
                favorites = saved.favorites,
                onToggle = discovery::toggleFavorite,
            )
        }
        when {
            isLoading -> {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.interactive_loading_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MkwKitTokens.Palette.crimsonDeep,
                    fontWeight = FontWeight.Medium,
                )
            }
            flow != null -> {
                com.mylittleblover.mkwplayer.ui.interactive.InteractivePlaybackTimeline(ui.position, flow, model.progressUi)
            }
            !ui.busy -> {
                Text(androidx.compose.ui.res.stringResource(R.string.interactive_empty), color = MkwKitTokens.Palette.inkSecondary)
            }
        }
    }
}

private fun playbackArtworkNames(model: PlayerViewModel, ui: PlayerViewModel.Ui): List<String> {
    if (ui.trackLoading) {
        return listOfNotNull(
            ui.artworkName,
            ui.loadingTrackCode?.let { model.extraCatalog.courseArtwork[it] ?: model.extraCatalog.courseArtwork["${it}_00"] },
        ).distinct()
    }
    val track = ui.linearTrack
    val names = if (track != null) {
        listOfNotNull(
            track.artworkName,
            track.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
            track.gameId?.let { model.extraCatalog.jukeboxGameArtwork[it] },
        )
    } else {
        listOfNotNull(ui.artworkName, model.currentStageCode?.let { model.extraCatalog.courseArtwork[it] })
    }
    val courseCode = if (track != null) track.courseCode else model.currentStageCode
    val packCandidates = courseCode?.let { com.mylittleblover.mkwplayer.ui.art.CourseCoverArt.packCandidates(it) }.orEmpty()
    val courseCover = courseCode?.let { com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(it) }
    return (if (courseCover == null && track == null) names + packCandidates + "album.webp" else names + packCandidates).distinct()
}

@Composable
private fun MiniPlayerProgressBar(
    flow: PlayerViewModel.FlowUi?,
    fallbackProgress: Float,
    progressFlow: StateFlow<PlayerViewModel.ProgressUi>?,
    selectedKey: String? = null,
    staffCreditsVariant: com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.Variant? = null,
    nmStaffCreditsOffsetMs: Long = com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.DEFAULT_NM_SPECIAL_119_OFFSET_MS,
) {
    val live = progressFlow?.collectAsStateWithLifecycle()?.value
    val frame = live?.sourceFrame ?: flow?.sourceFrame
    val length = flow?.sourceFrames
    val progress = if (frame != null && frame >= 0 && length != null && length > 0)
        (frame.toDouble() / length).toFloat().coerceIn(0f, 1f) else (live?.progress ?: fallbackProgress).coerceIn(0f, 1f)
    val specialStyle = staffCreditsVariant?.let { variant ->
        val posMs = live?.positionMs?.takeIf { it > 0L }
            ?: live?.durationMs?.takeIf { it > 0L }?.let { (progress.toDouble() * it).toLong() }
            ?: 0L
        com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.resolveStyle(
            variant = variant,
            positionMs = posMs,
            nmOffsetMs = nmStaffCreditsOffsetMs,
        )
    }
    com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(
        progress = progress,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 5.dp),
        barHeight = 3.dp,
        specialStyle = specialStyle,
        resetKey = selectedKey,
    )
}

@Composable
private fun MiniPlayer(
    title: String,
    courseCode: String?,
    packDirectory: java.io.File?,
    artworkNames: List<String>,
    ui: PlayerViewModel.Ui,
    progressFlow: StateFlow<PlayerViewModel.ProgressUi>? = null,
    staffCreditsVariant: com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.Variant? = null,
    nmStaffCreditsOffsetMs: Long = com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.DEFAULT_NM_SPECIAL_119_OFFSET_MS,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: (() -> Unit)? = null,
) {
    val flow = ui.flow
    val label = androidx.compose.ui.res.stringResource(if (ui.canPause) R.string.interactive_pause else R.string.interactive_resume)
    val stateLabel = androidx.compose.ui.res.stringResource(if (ui.canResume) R.string.discovery_paused else R.string.discovery_playing)
    val subtitle = flow?.let { com.mylittleblover.mkwplayer.ui.interactive.performanceStageText(it, courseCode) }
        ?: ui.linearTrack?.subtitle?.takeIf { it.isNotBlank() }?.let { "$it · $stateLabel" }
        ?: stateLabel
    val density = LocalDensity.current
    val swipeOpenThresholdPx = with(density) { 36.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val rowInteraction = remember { MutableInteractionSource() }
    val pressScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(rowInteraction, pressedScale = 0.985f)

    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        MkwFloatingPanel(
            shape = MkwKitTokens.Shape.card,
            shadowElevation = 4.dp,
            alpha = 0.98f,
            modifier = Modifier
                .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .testTag("mini-player")
                .fillMaxWidth()
                .height(64.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val adjusted = if (delta > 0f && dragOffsetY.value >= 0f) delta * 0.25f else delta
                            dragOffsetY.snapTo((dragOffsetY.value + adjusted).coerceIn(-swipeOpenThresholdPx * 1.6f, 6f))
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            if (dragOffsetY.value < -swipeOpenThresholdPx || velocity < -600f) {
                                dragOffsetY.snapTo(0f)
                                onOpen()
                            } else {
                                dragOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = com.mylittleblover.mkwplayer.ui.AppMotion.dragReleaseSpring,
                                    initialVelocity = velocity,
                                )
                            }
                        }
                    },
                ),
        ) {
            Column {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = rowInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = onOpen,
                        )
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PackArtwork.Thumbnail(packDirectory, artworkNames, Modifier.size(44.dp).clip(MkwKitTokens.Shape.thumb)) {
                        val courseArt = com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(courseCode)
                        if (courseArt != null) {
                            ResourceArtwork(courseArt, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MkwKitTokens.Palette.rose,
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.crimsonDeep,
                                    )
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink)
                        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MkwKitTokens.Palette.inkSecondary)
                    }
                    MkwIconButton(
                        icon = if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = label,
                        onClick = onToggle,
                        enabled = ui.canPause || ui.canResume,
                        visualSize = 36.dp,
                        iconSize = 20.dp,
                    )
                    if (onNext != null) {
                        MkwIconButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.jukebox_next),
                            onClick = onNext,
                            visualSize = 36.dp,
                            iconSize = 20.dp,
                        )
                    }
                }
                MiniPlayerProgressBar(
                    flow = flow,
                    fallbackProgress = ui.progress,
                    progressFlow = progressFlow,
                    selectedKey = ui.selectedKey,
                    staffCreditsVariant = staffCreditsVariant,
                    nmStaffCreditsOffsetMs = nmStaffCreditsOffsetMs,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SingleTrackPlayer(
    model: PlayerViewModel,
    ui: PlayerViewModel.Ui,
    onOpenSleepTimer: () -> Unit = {},
    onStopped: () -> Unit = {},
) {
    val stageCode = model.currentStageCode
    val linearTrack = ui.linearTrack
    val hasLinearTrack = ui.linearMode && linearTrack != null
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val allTrackMarkers by model.trackMarkers.collectAsStateWithLifecycle()
    val activeMarkerTrackId = remember(linearTrack) { model.markerTrackIdFor(linearTrack) }
    val activeTrackMarkers = remember(activeMarkerTrackId, allTrackMarkers) {
        activeMarkerTrackId?.let { allTrackMarkers[it] }.orEmpty()
    }
    val activeGenre = remember(linearTrack, ui.jukebox) { model.genreForLinearEntry(linearTrack) }
    val currentDurationMs = remember(ui.totalFrames, ui.sampleRate, linearTrack?.durationMs) {
        if (ui.totalFrames > 0L && ui.sampleRate > 0) {
            (ui.totalFrames * 1000L) / ui.sampleRate.toLong()
        } else {
            linearTrack?.durationMs ?: 0L
        }
    }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }
    var showEqSheet by rememberSaveable { mutableStateOf(false) }
    var showLoopConfigDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAddMarkerPosMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var markerSheetTarget by remember { mutableStateOf<Pair<LinearQueue.Entry, () -> Unit>?>(null) }
    var abPanelOpen by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = com.mylittleblover.mkwplayer.ui.rememberReducedMotion()

    if (showSpeedSheet) {
        com.mylittleblover.mkwplayer.ui.components.SpeedPitchSheet(
            rate = ui.playbackRate,
            onUpdate = model::setPlaybackRate,
            onReset = model::resetPlaybackRate,
            onDismiss = { showSpeedSheet = false },
        )
    }
    if (showEqSheet) {
        com.mylittleblover.mkwplayer.ui.components.EqualizerSheet(
            config = ui.dspConfig,
            onUpdate = model::setMasterDsp,
            onDismiss = { showEqSheet = false },
        )
    }
    if (showLoopConfigDialog) {
        LoopDurationConfigDialog(
            initialConfig = ui.nonInteractiveLoopConfig,
            onDismiss = { showLoopConfigDialog = false },
            onApply = { cfg ->
                model.setNonInteractiveLoopConfig(cfg)
                showLoopConfigDialog = false
            },
        )
    }
    val addMarkerPos = pendingAddMarkerPosMs
    if (addMarkerPos != null && activeMarkerTrackId != null) {
        com.mylittleblover.mkwplayer.ui.markers.AddTrackMarkerDialog(
            positionMs = addMarkerPos,
            suggestedIndex = activeTrackMarkers.size + 1,
            onDismiss = { pendingAddMarkerPosMs = null },
            onConfirm = { label, colorArgb ->
                model.addTrackMarker(activeMarkerTrackId, addMarkerPos, label, colorArgb)
                pendingAddMarkerPosMs = null
            },
        )
    }
    val queueMarkerTarget = markerSheetTarget
    if (queueMarkerTarget != null) {
        val (entry, jumpAction) = queueMarkerTarget
        val targetId = model.markerTrackIdFor(entry)
        val targetMarkers = targetId?.let { allTrackMarkers[it] }.orEmpty()
        com.mylittleblover.mkwplayer.ui.markers.PlayFromMarkerSheet(
            trackTitle = entry.title,
            markers = targetMarkers,
            onSelectMarker = { marker ->
                markerSheetTarget = null
                showQueueSheet = false
                if (entry.key == linearTrack?.key) {
                    model.seekToPositionMs(marker.positionMs)
                } else {
                    model.scheduleMarkerSeek(marker.positionMs, targetId)
                    jumpAction()
                }
            },
            onDeleteMarker = { marker -> model.deleteTrackMarker(marker.id) },
            onDismiss = { markerSheetTarget = null },
        )
    }

    val playing = ui.canPause
    val artScale by animateFloatAsState(
        targetValue = if (playing || reduceMotion) 1f else 0.93f,
        animationSpec = com.mylittleblover.mkwplayer.ui.AppMotion.snapSpring,
        label = "single-track-art-scale",
    )
    val artElevation by animateDpAsState(
        targetValue = if (playing) 14.dp else 3.dp,
        animationSpec = tween(if (reduceMotion) 0 else 280, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
        label = "single-track-art-elevation",
    )

    val artworkBlock: @Composable (Modifier) -> Unit = { artworkModifier ->
        Box(
            artworkModifier.fillMaxWidth().height(248.dp),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkSlot(
                Modifier.fillMaxWidth().height(248.dp)
                    .graphicsLayer { scaleX = artScale; scaleY = artScale },
            ) { edge ->
                val artNames = playbackArtworkNames(model, ui)
                val artLookup = PackArtwork.rememberArtworkState(model.packDirectory, artNames, edge)
                val coverCode = stageCode ?: linearTrack?.courseCode ?: linearTrack?.courseMedley?.medley?.courseCode
                val hasPackCandidate = remember(model.packDirectory, artNames) {
                    PackArtwork.hasAnyCandidate(model.packDirectory, artNames)
                }
                var retainedArt by remember { mutableStateOf<PackArtwork.Loaded?>(null) }
                if (artLookup.loaded != null) {
                    retainedArt = artLookup.loaded
                } else if (!hasPackCandidate) {
                    retainedArt = null
                }
                val art = artLookup.loaded ?: retainedArt
                val targetRatio = when {
                    art != null && art.bitmap.height > 0 ->
                        (art.bitmap.width.toFloat() / art.bitmap.height.toFloat()).coerceIn(0.85f, 1.85f)
                    coverCode != null -> 16f / 9f
                    else -> 1f
                }
                val animatedRatio by animateFloatAsState(
                    targetValue = targetRatio,
                    animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else tween(200, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut),
                    label = "single-track-art-ratio",
                )
                val boxHeight = 236.dp
                val boxWidth = (boxHeight * animatedRatio).coerceAtMost(320.dp)
                val finalHeight = (boxWidth / animatedRatio).coerceAtMost(boxHeight)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        Modifier.width(boxWidth).height(finalHeight),
                        shape = MkwKitTokens.Shape.dialog,
                        shadowElevation = artElevation,
                        color = MkwKitTokens.Palette.card,
                        border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
                    ) {
                        when {
                            art != null -> FadingArtwork(art.bitmap, Modifier.fillMaxSize(), animate = false)
                            coverCode != null && !hasPackCandidate -> {
                                val coverRes = com.mylittleblover.mkwplayer.ui.art.courseCoverDrawable(coverCode)
                                if (coverRes != null) {
                                    val coverLookup = com.mylittleblover.mkwplayer.ui.art.rememberResourceArtworkState(coverRes, edge)
                                    coverLookup.loaded?.let {
                                        FadingArtwork(it.bitmap, Modifier.fillMaxSize(), ContentScale.Crop, animate = false)
                                    }
                                } else {
                                    com.mylittleblover.mkwplayer.ui.art.CoursePlaceholderContent(coverCode, edge = edge, showIcon = false)
                                }
                            }
                            coverCode != null -> Box(Modifier.fillMaxSize().background(MkwKitTokens.Palette.inset))
                            else -> JukeboxArtworkCard(linearTrack)
                        }
                    }
                }
            }
        }
    }

    val controlsBlock: @Composable (Modifier) -> Unit = { controlsModifier ->
        Column(controlsModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.discovery_normal_playback),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MkwKitTokens.Palette.crimson,
                    )
                    linearTrack?.nativeTransitionSourceLabel?.let { transitionLabel ->
                        MkwBadge(text = transitionLabel, tone = MkwBadgeTone.Rose, tabular = false)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        linearTrack?.title ?: ui.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MkwKitTokens.Palette.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val favoriteId = ui.activeFavoriteId ?: stageCode?.removeSuffix("_00")
                    if (favoriteId != null) {
                        val discovery: com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel =
                            androidx.lifecycle.viewmodel.compose.viewModel()
                        val saved by discovery.state.collectAsStateWithLifecycle()
                        FavoriteToggleButton(
                            favoriteId = favoriteId,
                            favorites = saved.favorites,
                            onToggle = discovery::toggleFavorite,
                        )
                    }
                }
                if (linearTrack != null) {
                    val genreLabel = activeGenre?.localizedName(languageTag)
                    val metaParts = buildList {
                        if (linearTrack.subtitle.isNotBlank()) add(linearTrack.subtitle)
                        linearTrack.arrangement
                            ?.takeIf { arr ->
                                arr.isNotBlank() &&
                                    !linearTrack.title.contains(arr, ignoreCase = true) &&
                                    !linearTrack.subtitle.contains(arr, ignoreCase = true) &&
                                    (genreLabel == null || !arr.equals(genreLabel, ignoreCase = true))
                            }
                            ?.let { add(it) }
                    }
                    if (activeGenre != null || metaParts.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (activeGenre != null) {
                                com.mylittleblover.mkwplayer.ui.home.JukeboxGenrePill(
                                    genre = activeGenre,
                                    language = languageTag,
                                    compact = false,
                                )
                            }
                            if (metaParts.isNotEmpty()) {
                                Text(
                                    metaParts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MkwKitTokens.Palette.inkSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                            }
                        }
                    }
                }
            }

            // The status line is not decoration: without it a refused track shows an empty player with a clock
            // reading --:-- and no explanation, which reads as a broken app rather than a stated refusal.
            if (!ui.canPause && !ui.canResume && !hasLinearTrack) {
                Text(ui.status, style = MaterialTheme.typography.bodyMedium, color = MkwKitTokens.Palette.inkSecondary)
            }
            ui.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.danger)
            }

            if (ui.liveVisualizerEnabled) {
                val bars by model.spectrumBars.collectAsStateWithLifecycle()
                MkwInsetBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MkwKitTokens.Shape.control,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    com.mylittleblover.mkwplayer.ui.components.LiveSpectrumVisualizer(
                        bands = bars,
                        barColorPrimary = MkwKitTokens.Palette.crimson,
                        barColorSecondary = MkwKitTokens.Palette.crimsonDeep,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                    )
                }
            }

            val sliderTrackMarkers = remember(activeTrackMarkers, currentDurationMs) {
                activeTrackMarkers.toSliderMarkers(currentDurationMs)
            }
            val staffCreditsVariant = remember(activeMarkerTrackId, ui.selectedKey, linearTrack?.assetSha256) {
                com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.detectVariant(
                    canonicalTrackId = activeMarkerTrackId,
                    selectedKey = ui.selectedKey,
                    assetSha256 = linearTrack?.assetSha256,
                )
            }
            val nmStaffCreditsOffsetMs by model.nmStaffCreditsOffsetMs.collectAsStateWithLifecycle()
            LaunchedEffect(staffCreditsVariant, model.packDirectory) {
                if (staffCreditsVariant == com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.Variant.NINTENDO_MUSIC) {
                    model.ensureNmStaffCreditsOffsetCalibrated()
                }
            }
            SingleTrackProgressSection(
                selectedKey = ui.selectedKey,
                canSeek = ui.canSeek,
                totalFrames = ui.totalFrames,
                sampleRate = ui.sampleRate,
                fallbackProgress = ui.progress,
                fallbackPosition = ui.position,
                fallbackTotal = ui.total,
                reduceMotion = reduceMotion,
                progressFlow = model.progressUi,
                onSeekTo = model::seekTo,
                abLoopStartFrame = ui.abLoopStartFrame,
                abLoopEndFrame = ui.abLoopEndFrame,
                extraMarkers = sliderTrackMarkers,
                staffCreditsVariant = staffCreditsVariant,
                nmStaffCreditsOffsetMs = nmStaffCreditsOffsetMs,
            )

            // Primary Hero Transport directly below scrubber for immediate thumb reach
            val playLabel = androidx.compose.ui.res.stringResource(if (ui.canPause) R.string.interactive_pause else R.string.interactive_resume)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            ) {
                if (hasLinearTrack) {
                    MkwIconButton(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.jukebox_previous),
                        onClick = model::skipPrevious,
                        tone = MkwIconButtonTone.Bordered,
                        visualSize = 50.dp,
                        iconSize = 24.dp,
                    )
                }
                MkwIconButton(
                    icon = if (ui.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = playLabel,
                    onClick = {
                        when {
                            ui.canPause -> model.pause()
                            hasLinearTrack -> model.replayOrResumeLinear()
                            else -> model.resume()
                        }
                    },
                    enabled = ui.canPause || ui.canResume || hasLinearTrack,
                    tone = MkwIconButtonTone.Ink,
                    visualSize = 68.dp,
                    iconSize = 32.dp,
                )
                if (hasLinearTrack) {
                    MkwIconButton(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.jukebox_next),
                        onClick = model::skipNext,
                        tone = MkwIconButtonTone.Bordered,
                        visualSize = 50.dp,
                        iconSize = 24.dp,
                    )
                }
            }

            if (hasLinearTrack && activeMarkerTrackId != null) {
                com.mylittleblover.mkwplayer.ui.markers.PlayScreenMarkerStrip(
                    markers = activeTrackMarkers,
                    durationMs = currentDurationMs,
                    onAddMarker = {
                        pendingAddMarkerPosMs = model.progressUi.value.positionMs.coerceAtLeast(0L)
                    },
                    onJumpToMarker = { marker -> model.seekToPositionMs(marker.positionMs) },
                    onDeleteMarker = { marker -> model.deleteTrackMarker(marker.id) },
                )
            }

            AnimatedVisibility(visible = hasLinearTrack && (abPanelOpen || ui.abLoopStartFrame != null || ui.abLoopEndFrame != null)) {
                com.mylittleblover.mkwplayer.ui.components.AbLoopBar(
                    aFrame = ui.abLoopStartFrame,
                    bFrame = ui.abLoopEndFrame,
                    sampleRate = ui.sampleRate,
                    enabled = ui.canSeek && ui.totalFrames > 0L,
                    onSetA = model::setAbLoopA,
                    onSetB = model::setAbLoopB,
                    onClear = model::clearAbLoop,
                )
            }

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.fillMaxWidth().unpadHorizontal(24.dp),
            ) {
                if (hasLinearTrack) {
                    item {
                        val abActive = abPanelOpen || ui.abLoopStartFrame != null || ui.abLoopEndFrame != null
                        MkwFilterPill(
                            label = androidx.compose.ui.res.stringResource(R.string.ab_loop_button),
                            selected = abActive,
                            onClick = { abPanelOpen = !abPanelOpen },
                            leadingIcon = Icons.Filled.Loop,
                        )
                    }
                }
                item {
                    val rateModified = abs(ui.playbackRate.speed - 1f) > 0.01f || abs(ui.playbackRate.pitchSemitones) > 0.05f
                    MkwFilterPill(
                        label = if (rateModified) {
                            java.lang.String.format(java.util.Locale.ROOT, "%.2fx", ui.playbackRate.speed)
                        } else {
                            androidx.compose.ui.res.stringResource(R.string.speed_short_label)
                        },
                        selected = rateModified,
                        onClick = { showSpeedSheet = true },
                    )
                }
                item {
                    val eqOn = ui.dspConfig.eqEnabled || ui.dspConfig.bassBoostPercent > 0 || ui.dspConfig.loudnessDb >= 0.05f
                    MkwFilterPill(
                        label = androidx.compose.ui.res.stringResource(R.string.eq_short_label),
                        selected = eqOn,
                        onClick = { showEqSheet = true },
                    )
                }
                item {
                    MkwFilterPill(
                        label = when (ui.sleepTimer.mode) {
                            com.mylittleblover.mkwplayer.player.SleepTimerMode.COUNTDOWN ->
                                com.mylittleblover.mkwplayer.ui.components.formatCountdownMs(ui.sleepTimer.remainingMs)
                            com.mylittleblover.mkwplayer.player.SleepTimerMode.AFTER_CURRENT_TRACK ->
                                androidx.compose.ui.res.stringResource(R.string.sleep_timer_after_track)
                            com.mylittleblover.mkwplayer.player.SleepTimerMode.OFF ->
                                androidx.compose.ui.res.stringResource(R.string.sleep_short_label)
                        },
                        selected = ui.sleepTimer.active,
                        onClick = onOpenSleepTimer,
                    )
                }
            }

            MkwUtilityDock {
                if (hasLinearTrack) {
                    val repeatLabel = androidx.compose.ui.res.stringResource(
                        when (ui.queue.repeatMode) {
                            LinearQueue.RepeatMode.LIST -> R.string.jukebox_repeat_list
                            LinearQueue.RepeatMode.SINGLE -> R.string.jukebox_repeat_single
                            LinearQueue.RepeatMode.OFF -> R.string.jukebox_repeat_off
                        },
                    )
                    val repeatIcon = if (ui.queue.repeatMode == LinearQueue.RepeatMode.SINGLE) {
                        Icons.Filled.RepeatOne
                    } else {
                        Icons.Filled.Repeat
                    }
                    val shuffleLabel = androidx.compose.ui.res.stringResource(
                        if (ui.queue.shuffle) R.string.jukebox_shuffle_on else R.string.jukebox_shuffle,
                    )
                    val queuedTotal = ui.queue.tempQueue.size + ui.queue.upNext.size
                    val queueLabel = androidx.compose.ui.res.stringResource(R.string.jukebox_queue)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SingleTrackUtilityButton(
                            icon = Icons.Filled.Shuffle,
                            label = shuffleLabel,
                            active = ui.queue.shuffle,
                            enabled = ui.allowShuffle,
                            onClick = model::toggleShuffle,
                            modifier = Modifier.weight(1f),
                        )
                        SingleTrackUtilityButton(
                            icon = repeatIcon,
                            label = repeatLabel,
                            active = ui.queue.repeatMode != LinearQueue.RepeatMode.OFF,
                            enabled = ui.allowRepeatToggle,
                            onClick = model::cycleRepeatMode,
                            onLongClick = { showLoopConfigDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                        SingleTrackUtilityButton(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            label = queueLabel,
                            active = false,
                            badgeCount = queuedTotal,
                            onClick = { showQueueSheet = true },
                            modifier = Modifier.weight(1f),
                        )
                        SingleTrackUtilityButton(
                            icon = Icons.Filled.Stop,
                            label = androidx.compose.ui.res.stringResource(R.string.interactive_stop),
                            active = false,
                            enabled = ui.canStop,
                            onClick = {
                                model.stop()
                                onStopped()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    SingleTrackUtilityButton(
                        icon = Icons.Filled.Stop,
                        label = androidx.compose.ui.res.stringResource(R.string.interactive_stop),
                        active = false,
                        enabled = ui.canStop,
                        onClick = {
                            model.stop()
                            onStopped()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            Row(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artworkBlock(Modifier.weight(1f))
                controlsBlock(Modifier.weight(1f))
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                artworkBlock(Modifier.fillMaxWidth())
                controlsBlock(Modifier.fillMaxWidth())
            }
        }
    }

    if (showQueueSheet && hasLinearTrack) {
        LinearQueueBottomSheet(
            queue = ui.queue,
            languageTag = languageTag,
            genreForEntry = model::genreForLinearEntry,
            markerCountForEntry = { entry ->
                model.markerTrackIdFor(entry)?.let { allTrackMarkers[it]?.size } ?: 0
            },
            onOpenMarkersForEntry = { entry, jump ->
                markerSheetTarget = entry to jump
            },
            onDismiss = { showQueueSheet = false },
            onJumpToTemp = { index ->
                model.jumpToTempQueueItem(index)
                showQueueSheet = false
            },
            onRemoveTemp = model::removeTempQueueItem,
            onMoveTemp = model::moveTempQueueItem,
            onJumpToUpcoming = { index ->
                model.jumpToUpcomingQueueItem(index)
                showQueueSheet = false
            },
            onRemoveUpcoming = model::removeUpcomingQueueItem,
            onMoveUpcoming = model::moveUpcomingQueueItem,
            onClearUpcoming = model::clearUpcomingQueue,
        )
    }
}

@Composable
private fun JukeboxArtworkCard(entry: LinearQueue.Entry?) {
    val gradient = Brush.linearGradient(
        listOf(
            MkwKitTokens.Palette.rose,
            MkwKitTokens.Palette.card,
        ),
    )
    Box(
        Modifier.fillMaxSize().background(gradient).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entry != null) {
                MkwBadge(
                    text = androidx.compose.ui.res.stringResource(R.string.jukebox_eyebrow),
                    tone = MkwBadgeTone.Rose,
                    tabular = false,
                )
            }
            Surface(
                modifier = Modifier.size(104.dp),
                shape = MkwKitTokens.Shape.dialog,
                color = MkwKitTokens.Palette.ground.copy(alpha = 0.92f),
                border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MkwKitTokens.Palette.crimson,
                    )
                }
            }
            entry?.yearLabel?.takeIf { it.isNotBlank() }?.let { year ->
                Text(
                    year,
                    style = MkwKitTokens.tabular(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.ink,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinearQueueBottomSheet(
    queue: LinearQueue.Snapshot,
    languageTag: String = "",
    genreForEntry: (LinearQueue.Entry) -> com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.GenreInfo? = { null },
    markerCountForEntry: (LinearQueue.Entry) -> Int = { 0 },
    onOpenMarkersForEntry: ((LinearQueue.Entry, () -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    onJumpToTemp: (Int) -> Unit,
    onRemoveTemp: (Int) -> Unit,
    onMoveTemp: (Int, Int) -> Unit,
    onJumpToUpcoming: (Int) -> Unit,
    onRemoveUpcoming: (Int) -> Unit,
    onMoveUpcoming: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
) {
    val listState = rememberLazyListState()
    val latestQueue by androidx.compose.runtime.rememberUpdatedState(queue)
    val latestMoveTemp by androidx.compose.runtime.rememberUpdatedState(onMoveTemp)
    val latestMoveUpcoming by androidx.compose.runtime.rememberUpdatedState(onMoveUpcoming)

    val tempKeys = remember(queue.tempQueue) {
        val seen = HashMap<String, Int>(queue.tempQueue.size)
        queue.tempQueue.map { entry ->
            val n = (seen[entry.key] ?: 0) + 1
            seen[entry.key] = n
            "temp:${entry.key}#$n"
        }
    }
    val upNextKeys = remember(queue.upNext) {
        val seen = HashMap<String, Int>(queue.upNext.size)
        queue.upNext.map { entry ->
            val n = (seen[entry.key] ?: 0) + 1
            seen[entry.key] = n
            "upnext:${entry.key}#$n"
        }
    }

    val tempDrag = remember(listState) {
        com.mylittleblover.mkwplayer.ui.components.DragReorderController(
            listState = listState,
            sectionStartIndex = {
                val curCount = if (latestQueue.current != null) 2 else 0
                curCount + 1
            },
            itemCountProvider = { latestQueue.tempQueue.size },
            onMove = { from, to -> latestMoveTemp(from, to) },
        )
    }
    val upNextDrag = remember(listState) {
        com.mylittleblover.mkwplayer.ui.components.DragReorderController(
            listState = listState,
            sectionStartIndex = {
                val q = latestQueue
                val curCount = if (q.current != null) 2 else 0
                val tempCount = if (q.tempQueue.isNotEmpty()) 1 + q.tempQueue.size else 0
                curCount + tempCount + 1
            },
            itemCountProvider = { latestQueue.upNext.size },
            onMove = { from, to -> latestMoveUpcoming(from, to) },
        )
    }

    MkwBottomSheet(
        onDismissRequest = onDismiss,
        title = androidx.compose.ui.res.stringResource(R.string.jukebox_queue),
        subtitle = androidx.compose.ui.res.stringResource(R.string.jukebox_queue_subtitle),
    ) {
        MkwDivider()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(380.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        ) {
            queue.current?.let { now ->
                item(key = "now-hdr") {
                    QueueSectionHeader(androidx.compose.ui.res.stringResource(R.string.jukebox_now_playing))
                }
                item(key = "now-item") {
                    val mCount = markerCountForEntry(now)
                    QueueEntryRow(
                        entry = now,
                        highlight = true,
                        genre = genreForEntry(now),
                        languageTag = languageTag,
                        markerCount = mCount,
                        onClick = onDismiss,
                        onLongClick = if (mCount > 0 && onOpenMarkersForEntry != null) {
                            { onOpenMarkersForEntry(now, onDismiss) }
                        } else null,
                    )
                }
            }
            if (queue.tempQueue.isNotEmpty()) {
                item(key = "temp-hdr") {
                    QueueSectionHeader(androidx.compose.ui.res.stringResource(R.string.jukebox_up_next_temp))
                }
                itemsIndexed(queue.tempQueue, key = { idx, _ -> tempKeys[idx] }) { idx, item ->
                    val dragging = tempDrag.draggingIndex == idx
                    val rowMod = if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = tempDrag.dragDeltaY }
                    } else {
                        Modifier
                    }
                    val mCount = markerCountForEntry(item)
                    QueueEntryRow(
                        entry = item,
                        highlight = false,
                        genre = genreForEntry(item),
                        languageTag = languageTag,
                        markerCount = mCount,
                        onClick = { onJumpToTemp(idx) },
                        onLongClick = if (mCount > 0 && onOpenMarkersForEntry != null) {
                            { onOpenMarkersForEntry(item) { onJumpToTemp(idx) } }
                        } else null,
                        onRemove = { onRemoveTemp(idx) },
                        dragHandle = if (queue.tempQueue.size > 1) {
                            { com.mylittleblover.mkwplayer.ui.components.DragHandleButton(idx, tempDrag) }
                        } else null,
                        isDragging = dragging,
                        rowModifier = rowMod,
                    )
                }
            }
            if (queue.upNext.isNotEmpty()) {
                item(key = "upnext-hdr") {
                    Row(
                        Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QueueSectionHeader(androidx.compose.ui.res.stringResource(R.string.jukebox_up_next_list))
                        Spacer(Modifier.weight(1f))
                        MkwGhostButton(
                            text = androidx.compose.ui.res.stringResource(R.string.queue_clear_upcoming),
                            onClick = onClearUpcoming,
                        )
                    }
                }
                itemsIndexed(queue.upNext, key = { idx, _ -> upNextKeys[idx] }) { idx, item ->
                    val dragging = upNextDrag.draggingIndex == idx
                    val rowMod = if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = upNextDrag.dragDeltaY }
                    } else {
                        Modifier
                    }
                    val mCount = markerCountForEntry(item)
                    QueueEntryRow(
                        entry = item,
                        highlight = false,
                        genre = genreForEntry(item),
                        languageTag = languageTag,
                        markerCount = mCount,
                        onClick = { onJumpToUpcoming(idx) },
                        onLongClick = if (mCount > 0 && onOpenMarkersForEntry != null) {
                            { onOpenMarkersForEntry(item) { onJumpToUpcoming(idx) } }
                        } else null,
                        onRemove = { onRemoveUpcoming(idx) },
                        dragHandle = if (queue.upNext.size > 1) {
                            { com.mylittleblover.mkwplayer.ui.components.DragHandleButton(idx, upNextDrag) }
                        } else null,
                        isDragging = dragging,
                        rowModifier = rowMod,
                    )
                }
            }
            if (queue.tempQueue.isEmpty() && queue.upNext.isEmpty()) {
                item(key = "queue-empty") {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.jukebox_queue_empty),
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MkwKitTokens.Palette.crimson,
    )
}

@Composable
private fun SingleTrackUtilityButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    MkwUtilityTile(
        icon = icon,
        label = label,
        active = active,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        enabled = enabled,
        badgeCount = badgeCount,
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueueEntryRow(
    entry: LinearQueue.Entry,
    highlight: Boolean,
    onClick: () -> Unit,
    genre: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.GenreInfo? = null,
    languageTag: String = "",
    markerCount: Int = 0,
    onLongClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
    isDragging: Boolean = false,
    rowModifier: Modifier = Modifier,
) {
    val genreName = genre?.localizedName(languageTag)
    val sub = buildList {
        val seen = LinkedHashSet<String>()
        fun addSegments(raw: String?) {
            raw?.split("·")?.forEach { piece ->
                val clean = piece.trim()
                if (clean.isNotEmpty() &&
                    (genreName == null || !clean.equals(genreName, ignoreCase = true)) &&
                    seen.add(clean.lowercase())
                ) {
                    add(clean)
                }
            }
        }
        addSegments(entry.subtitle)
        addSegments(entry.arrangement)
        if (entry.durationMs > 0L) add(EngineSnapshot.formatPosition(entry.durationMs / 1000L, 1))
    }.joinToString(" · ")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val cardModifier = if (onLongClick != null) {
        rowModifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(MkwKitTokens.Shape.control)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
    } else {
        rowModifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)
    }
    MkwCard(
        onClick = if (onLongClick == null) onClick else null,
        selected = highlight || isDragging,
        tone = MkwSurfaceTone.White,
        shape = MkwKitTokens.Shape.control,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = cardModifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (dragHandle != null) dragHandle()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (highlight) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.ink,
                )
                if (genre != null || sub.isNotEmpty() || markerCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (genre != null) {
                            com.mylittleblover.mkwplayer.ui.home.JukeboxGenrePill(
                                genre = genre,
                                language = languageTag,
                                compact = true,
                            )
                        }
                        if (sub.isNotEmpty()) {
                            Text(
                                text = sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                                color = MkwKitTokens.Palette.inkSecondary,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (markerCount > 0) {
                            Surface(
                                shape = MkwKitTokens.Shape.pill,
                                color = MkwKitTokens.Palette.rose,
                                border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.25f)),
                            ) {
                                Text(
                                    text = markerCount.toString(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                )
                            }
                        }
                    }
                }
            }
            if (onRemove != null) {
                MkwIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.discovery_remove),
                    onClick = onRemove,
                    visualSize = 32.dp,
                    iconSize = 18.dp,
                    tintOverride = MkwKitTokens.Palette.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun SingleTrackProgressSection(
    selectedKey: String?,
    canSeek: Boolean,
    totalFrames: Long,
    sampleRate: Int,
    fallbackProgress: Float,
    fallbackPosition: String,
    fallbackTotal: String,
    reduceMotion: Boolean,
    progressFlow: StateFlow<PlayerViewModel.ProgressUi>,
    onSeekTo: (Float) -> Unit,
    abLoopStartFrame: Long? = null,
    abLoopEndFrame: Long? = null,
    extraMarkers: List<MkwSliderMarker> = emptyList(),
    staffCreditsVariant: com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.Variant? = null,
    nmStaffCreditsOffsetMs: Long = com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.DEFAULT_NM_SPECIAL_119_OFFSET_MS,
) {
    val live by progressFlow.collectAsStateWithLifecycle()
    val liveProgress = live.progress
    val livePosition = live.position
    val liveTotal = if (live.total != "--:--") live.total else fallbackTotal

    var scrubbing by remember(selectedKey) { mutableStateOf(false) }
    var scrubFraction by remember(selectedKey) { mutableStateOf(0f) }
    var pendingSeek by remember(selectedKey) { mutableStateOf<Float?>(null) }

    LaunchedEffect(liveProgress, pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (abs(liveProgress - target) < 0.02f) {
            pendingSeek = null
        }
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek != null) {
            delay(2500L)
            pendingSeek = null
        }
    }

    val displayedProgress = when {
        scrubbing -> scrubFraction
        pendingSeek != null -> pendingSeek!!
        else -> liveProgress.coerceIn(0f, 1f)
    }
    val displayedPosition = if ((scrubbing || pendingSeek != null) && totalFrames > 0L && sampleRate > 0) {
        EngineSnapshot.formatPosition((displayedProgress.toDouble() * totalFrames).toLong(), sampleRate)
    } else {
        if (livePosition != "--:--") livePosition else fallbackPosition
    }
    val activeScrub = scrubbing || pendingSeek != null
    val totalDurationMs = if (totalFrames > 0L && sampleRate > 0) {
        (totalFrames * 1000L) / sampleRate.toLong()
    } else {
        live.durationMs
    }
    val effectivePositionMs = when {
        activeScrub && totalDurationMs > 0L -> (displayedProgress.toDouble() * totalDurationMs).toLong()
        live.positionMs > 0L -> live.positionMs
        totalDurationMs > 0L -> (displayedProgress.toDouble() * totalDurationMs).toLong()
        else -> 0L
    }
    val specialSliderStyle = staffCreditsVariant?.let { variant ->
        com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.resolveStyle(
            variant = variant,
            positionMs = effectivePositionMs,
            nmOffsetMs = nmStaffCreditsOffsetMs,
        )
    }

    Column(Modifier.fillMaxWidth()) {
        if (canSeek && totalFrames > 0L) {
            val aFraction = abLoopStartFrame?.let { (it.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f) }
            val bFraction = abLoopEndFrame?.let { (it.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f) }
            val markers = remember(aFraction, bFraction, extraMarkers) {
                val ab = when {
                    aFraction != null && bFraction != null ->
                        listOf(MkwSliderMarker(aFraction, bFraction, MkwKitTokens.Palette.crimsonDeep))
                    aFraction != null ->
                        listOf(MkwSliderMarker(aFraction, null, MkwKitTokens.Palette.crimsonDeep))
                    bFraction != null ->
                        listOf(MkwSliderMarker(bFraction, null, MkwKitTokens.Palette.crimsonDeep))
                    else -> emptyList()
                }
                if (extraMarkers.isEmpty()) ab else ab + extraMarkers
            }
            MkwSlider(
                value = displayedProgress,
                onValueChange = { fraction ->
                    scrubbing = true
                    scrubFraction = fraction.coerceIn(0f, 1f)
                },
                onValueChangeFinished = {
                    val target = scrubFraction
                    pendingSeek = target
                    scrubbing = false
                    onSeekTo(target)
                },
                valueRange = 0f..1f,
                showThumb = false,
                markers = markers,
                smoothProgress = true,
                snapImmediate = scrubbing || pendingSeek != null,
                resetKey = selectedKey,
                specialStyle = specialSliderStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(
                progress = liveProgress.coerceIn(0f, 1f).let { if (it > 0f) it else fallbackProgress },
                specialStyle = specialSliderStyle,
                resetKey = selectedKey,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val timeStyle = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                displayedPosition,
                style = timeStyle,
                fontWeight = if (activeScrub) FontWeight.SemiBold else FontWeight.Normal,
                color = if (activeScrub) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary,
            )
            AnimatedContent(
                targetState = liveTotal,
                transitionSpec = { MkwMotion.viewSwap(reduceMotion) },
                label = "single-track-total-duration",
            ) { totalText ->
                Text(totalText, style = timeStyle, color = MkwKitTokens.Palette.inkSecondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoopDurationConfigDialog(
    initialConfig: com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig,
    onDismiss: () -> Unit,
    onApply: (com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig) -> Unit,
) {
    val reducedMotion = MkwMotion.rememberPrefersReducedMotion()
    var mode by rememberSaveable { mutableStateOf(initialConfig.mode) }
    var loopCount by rememberSaveable {
        mutableIntStateOf(
            initialConfig.loopCount.coerceIn(1, com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.MAX_LOOP_COUNT),
        )
    }
    var loopInput by rememberSaveable { mutableStateOf(loopCount.toString()) }
    var durationSec by rememberSaveable {
        mutableIntStateOf((initialConfig.targetDurationMs / 1000L).toInt().coerceIn(10, 14_400))
    }
    var durationInput by rememberSaveable { mutableStateOf(durationSec.toString()) }

    val loopCountPresets = remember { listOf(1, 2, 3, 5, 10) }
    val durationPresetsSec = remember { listOf(60, 120, 180, 300, 600, 900, 1800) }
    val countModeLabel = androidx.compose.ui.res.stringResource(R.string.loop_config_mode_count)
    val durationModeLabel = androidx.compose.ui.res.stringResource(R.string.loop_config_mode_duration)

    MkwDialog(
        onDismissRequest = onDismiss,
        title = androidx.compose.ui.res.stringResource(R.string.loop_config_dialog_title),
        confirmButton = {
            MkwPrimaryButton(
                text = androidx.compose.ui.res.stringResource(R.string.loop_config_apply),
                onClick = {
                    val result = when (mode) {
                        com.mylittleblover.mkwplayer.audio.NonInteractiveLoopMode.LOOP_COUNT -> {
                            val parsed = loopInput.trim().toIntOrNull()?.coerceIn(1, com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.MAX_LOOP_COUNT)
                                ?: loopCount
                            com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.byLoopCount(parsed)
                        }
                        com.mylittleblover.mkwplayer.audio.NonInteractiveLoopMode.TARGET_DURATION -> {
                            val parsedSec = durationInput.trim().toIntOrNull()?.coerceIn(10, 14_400) ?: durationSec
                            com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.byTargetDurationMs(parsedSec.toLong() * 1000L)
                        }
                    }
                    onApply(result)
                },
                size = MkwButtonSize.Compact,
            )
        },
        dismissButton = {
            MkwGhostButton(
                text = androidx.compose.ui.res.stringResource(R.string.discovery_cancel),
                onClick = onDismiss,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(180, easing = com.mylittleblover.mkwplayer.ui.AppMotion.easeOut)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.loop_config_dialog_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MkwKitTokens.Palette.inkSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MkwBadge(
                    text = androidx.compose.ui.res.stringResource(R.string.loop_config_fade_badge),
                    tone = MkwBadgeTone.Rose,
                    tabular = false,
                )
            }

            MkwSegmentedBar(
                items = listOf(
                    com.mylittleblover.mkwplayer.audio.NonInteractiveLoopMode.LOOP_COUNT,
                    com.mylittleblover.mkwplayer.audio.NonInteractiveLoopMode.TARGET_DURATION,
                ),
                selected = mode,
                labelOf = { item ->
                    when (item) {
                        com.mylittleblover.mkwplayer.audio.NonInteractiveLoopMode.LOOP_COUNT -> countModeLabel
                        com.mylittleblover.mkwplayer.audio.NonInteractiveLoopMode.TARGET_DURATION -> durationModeLabel
                    }
                },
                onSelect = { mode = it },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedContent(
                targetState = mode,
                transitionSpec = { MkwMotion.viewSwap(reducedMotion) },
                label = "loop-config-mode-pane",
            ) { currentMode ->
                MkwCard(
                    tone = MkwSurfaceTone.Recessed,
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (currentMode) {
                        com.mylittleblover.mkwplayer.audio.NonInteractiveLoopMode.LOOP_COUNT -> {
                            Text(
                                text = androidx.compose.ui.res.stringResource(R.string.loop_config_count_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MkwIconButton(
                                    icon = androidx.compose.material.icons.Icons.Filled.Remove,
                                    contentDescription = "−",
                                    onClick = {
                                        val next = (loopCount - 1).coerceAtLeast(1)
                                        loopCount = next
                                        loopInput = next.toString()
                                    },
                                    enabled = loopCount > 1,
                                    tone = MkwIconButtonTone.Bordered,
                                    visualSize = 40.dp,
                                    iconSize = 20.dp,
                                )
                                MkwTextField(
                                    value = loopInput,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(2)
                                        loopInput = digits
                                        digits.toIntOrNull()?.let { v ->
                                            loopCount = v.coerceIn(1, com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.MAX_LOOP_COUNT)
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                MkwIconButton(
                                    icon = Icons.Filled.Add,
                                    contentDescription = "+",
                                    onClick = {
                                        val next = (loopCount + 1).coerceAtMost(com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.MAX_LOOP_COUNT)
                                        loopCount = next
                                        loopInput = next.toString()
                                    },
                                    enabled = loopCount < com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.MAX_LOOP_COUNT,
                                    tone = MkwIconButtonTone.Bordered,
                                    visualSize = 40.dp,
                                    iconSize = 20.dp,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                loopCountPresets.forEach { preset ->
                                    MkwFilterPill(
                                        label = "${preset}×",
                                        selected = loopCount == preset,
                                        onClick = {
                                            loopCount = preset
                                            loopInput = preset.toString()
                                        },
                                    )
                                }
                            }
                        }

                        com.mylittleblover.mkwplayer.audio.NonInteractiveLoopMode.TARGET_DURATION -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(R.string.loop_config_duration_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.inkSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = EngineSnapshot.formatPosition(durationSec.toLong(), 1),
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                                    fontWeight = FontWeight.Bold,
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            MkwTextField(
                                value = durationInput,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(5)
                                    durationInput = digits
                                    digits.toIntOrNull()?.let { v ->
                                        durationSec = v.coerceIn(10, 14_400)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                durationPresetsSec.forEach { presetSec ->
                                    MkwFilterPill(
                                        label = EngineSnapshot.formatPosition(presetSec.toLong(), 1),
                                        selected = durationSec == presetSec,
                                        onClick = {
                                            durationSec = presetSec
                                            durationInput = presetSec.toString()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            MkwGhostButton(
                text = androidx.compose.ui.res.stringResource(R.string.loop_config_reset_default),
                onClick = {
                    val def = com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.Default
                    mode = def.mode
                    loopCount = def.loopCount
                    loopInput = def.loopCount.toString()
                    durationSec = (def.targetDurationMs / 1000L).toInt()
                    durationInput = durationSec.toString()
                },
                modifier = Modifier.align(Alignment.Start),
            )
        }
    }
}

@Composable
private fun SpecialCupCongratsDialog(
    visible: Boolean,
    onEnterCup: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    MkwDialog(
        onDismissRequest = onDismiss,
        title = androidx.compose.ui.res.stringResource(R.string.unlock_ceremony_congrats_title),
        icon = {
            ResourceArtwork(
                resourceId = CupArt.special.drawable,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
            )
        },
        confirmButton = {
            MkwPrimaryButton(
                text = androidx.compose.ui.res.stringResource(R.string.unlock_ceremony_congrats_enter),
                onClick = onEnterCup,
                size = MkwButtonSize.Compact,
            )
        },
        dismissButton = {
            MkwGhostButton(
                text = androidx.compose.ui.res.stringResource(R.string.unlock_ceremony_congrats_close),
                onClick = onDismiss,
            )
        },
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.unlock_ceremony_congrats_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MkwKitTokens.Palette.ink,
        )
        Text(
            text = CupArt.NOTICE,
            style = MaterialTheme.typography.labelSmall,
            color = MkwKitTokens.Palette.inkSecondary,
        )
    }
}

private fun activeRecentRefId(
    playingCourse: String?,
    ui: PlayerViewModel.Ui,
    extraCatalog: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog,
): String? {
    ui.activeFavoriteId?.takeIf { it.isNotBlank() }?.let { return it }
    if (!playingCourse.isNullOrBlank()) return playingCourse.removeSuffix("_00")
    val entry = ui.linearTrack ?: return null
    if (entry.key == "__special_cup_ceremony__") return null
    if (entry.sourceType == com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.JUKEBOX) {
        val rawKey = (entry.sourceTrackKey ?: entry.key).removePrefix(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_JUKEBOX)
        return "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_JUKEBOX}$rawKey"
    }
    val code = entry.courseCode
    if (!code.isNullOrBlank()) {
        if (!code.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA)) {
            return code.removeSuffix("_00")
        }
        val tid = code.removePrefix(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA)
        return when {
            extraCatalog.sfcCourses.any { it.id == tid } -> "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_SFC}$tid"
            extraCatalog.battleTracks.any { it.id == tid } -> "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_BATTLE}$tid"
            else -> "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA}$tid"
        }
    }
    entry.overlaidOpening?.id?.let { return "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA}$it" }
    return when {
        entry.key.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_SFC) ||
            entry.key.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_BATTLE) ||
            entry.key.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_JUKEBOX) -> entry.key
        entry.key.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA) -> {
            val tid = entry.key.removePrefix(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA)
            when {
                extraCatalog.sfcCourses.any { it.id == tid } -> "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_SFC}$tid"
                extraCatalog.battleTracks.any { it.id == tid } -> "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_BATTLE}$tid"
                else -> "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA}$tid"
            }
        }
        ":opening:" in entry.key -> {
            val tid = entry.key.substringAfter(":opening:").substringBefore(':')
            "${com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA}$tid"
        }
        else -> null
    }
}

