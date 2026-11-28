package com.mylittleblover.mkwplayer.ui.home

import androidx.compose.animation.AnimatedVisibility
import com.mylittleblover.mkwplayer.ui.art.PackArtwork
import com.mylittleblover.mkwplayer.ui.art.ResourceArtwork
import com.mylittleblover.mkwplayer.ui.art.CourseCover
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mylittleblover.mkwplayer.ui.unpadHorizontal
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.zIndex
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwCheckboxMark
import com.mylittleblover.mkwplayer.ui.kit.MkwDangerButton
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwDropdownItem
import com.mylittleblover.mkwplayer.ui.kit.MkwDropdownMenu
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwFloatingPanel
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButtonTone
import com.mylittleblover.mkwplayer.ui.kit.MkwInsetBox
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwMotion
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRadioMark
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSegmentedBar
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwSwitch
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import kotlinx.coroutines.launch
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.BrowseCatalog
import com.mylittleblover.mkwplayer.library.PlaylistItemRef
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.library.TrackPlayStat
import com.mylittleblover.mkwplayer.library.TrackSortOrder
import com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog
import com.mylittleblover.mkwplayer.routes.CustomRoute
import com.mylittleblover.mkwplayer.routes.OfficialRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.art.StageArt
import com.mylittleblover.mkwplayer.ui.routes.RoadMusicRuleModeSelector
import com.mylittleblover.mkwplayer.ui.routes.RouteEdgeMusicPickerSheet
import com.mylittleblover.mkwplayer.ui.routes.edgeResolutionSummary
import com.mylittleblover.mkwplayer.ui.routes.freeRoamModeChipLabel
import com.mylittleblover.mkwplayer.ui.routes.freeRoamModeDisplayLabel
import com.mylittleblover.mkwplayer.ui.routes.hasKnownPool
import com.mylittleblover.mkwplayer.ui.routes.isMissingOrIncomplete
import com.mylittleblover.mkwplayer.ui.routes.pureFreeRoamGamePoolLegSummary

/** Process-local bridge providing saved track markers & seek-on-play to track long-press menus (#16). */
internal data class TrackMarkerMenuBridge(
    val markersByTrackId: Map<String, List<com.mylittleblover.mkwplayer.library.TrackMarker>> = emptyMap(),
    val onPlayFromMarker: (trackId: String, positionMs: Long, playTrack: () -> Unit) -> Unit = { _, _, _ -> },
    val onDeleteMarker: (String) -> Unit = {},
)

internal val LocalTrackMarkerBridge = compositionLocalOf { TrackMarkerMenuBridge() }
internal val LocalStaffCreditsUnlocked = compositionLocalOf { false }
internal val LocalNonInteractiveLoopConfig = compositionLocalOf {
    com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.Default
}

internal fun effectiveExtraDurationMs(
    item: ExtraMusicCatalog.OtherMusicItem,
    loopConfig: com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig,
): Long = when (item) {
    is ExtraMusicCatalog.OtherMusicItem.LinearAsset ->
        com.mylittleblover.mkwplayer.audio.NonInteractiveLoopPolicy.computeEffectiveDurationMs(
            baseDurationMs = item.durationMs,
            baseFrames = item.asset.frames,
            loopStart = item.asset.loopStart,
            loopEnd = item.asset.loopEnd,
            hasLoop = item.asset.loop,
            sampleRate = item.asset.sampleRate,
            config = loopConfig,
        )
    else -> item.durationMs
}

internal fun effectiveSimpleDurationMs(
    item: ExtraMusicCatalog.SimpleTrackItem,
    loopConfig: com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig,
): Long = com.mylittleblover.mkwplayer.audio.NonInteractiveLoopPolicy.computeEffectiveDurationMs(
    baseDurationMs = item.durationMs,
    baseFrames = item.asset.frames,
    loopStart = item.asset.loopStart,
    loopEnd = item.asset.loopEnd,
    hasLoop = item.asset.loop,
    sampleRate = item.asset.sampleRate,
    config = loopConfig,
)

@Composable
private fun RecentTrackTile(
    title: String,
    subtitle: String,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    packDirectory: java.io.File? = null,
    artworkName: String? = null,
    fallbackCourseCode: String? = null,
) {
    val tileInteraction = remember { MutableInteractionSource() }
    val pressScale = com.mylittleblover.mkwplayer.ui.rememberPressScale(tileInteraction, pressedScale = 0.97f)
    Column(
        Modifier.width(160.dp).graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(
                    interactionSource = tileInteraction,
                    indication = LocalIndication.current,
                    onClick = onOpen,
                ),
        ) {
            if (fallbackCourseCode != null) {
                com.mylittleblover.mkwplayer.ui.art.CourseCover(
                    code = fallbackCourseCode,
                    modifier = Modifier.fillMaxSize(),
                    packDirectory = packDirectory,
                    artworkName = artworkName,
                )
            } else {
                PackArtwork.Thumbnail(
                    packDirectory = packDirectory,
                    artworkNames = listOfNotNull(artworkName),
                    modifier = Modifier.fillMaxSize().background(MkwKitTokens.Palette.rose),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = MkwKitTokens.Palette.crimsonDeep,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            MkwIconButton(
                icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (favorite) R.string.discovery_favorite_remove else R.string.discovery_favorite_add),
                onClick = onFavorite,
                tone = MkwIconButtonTone.Bordered,
                visualSize = 34.dp,
                iconSize = 18.dp,
                tintOverride = if (favorite) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(38.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().clickable(
                interactionSource = tileInteraction,
                indication = null,
                onClick = onOpen,
            ).padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiscoveryHomeProgressBar(
    flow: com.mylittleblover.mkwplayer.player.PlayerViewModel.FlowUi?,
    fallbackProgress: Float,
    progressFlow: kotlinx.coroutines.flow.StateFlow<com.mylittleblover.mkwplayer.player.PlayerViewModel.ProgressUi>?,
) {
    val progressUi = progressFlow?.collectAsState()?.value
    val srcFrame = progressUi?.sourceFrame ?: flow?.sourceFrame
    val fraction = if (srcFrame != null && flow?.sourceFrames != null && flow.sourceFrames > 0) {
        (srcFrame.toDouble() / flow.sourceFrames).toFloat().coerceIn(0f, 1f)
    } else {
        (progressUi?.progress ?: fallbackProgress).coerceIn(0f, 1f)
    }
    com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(fraction, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun DiscoveryHome(catalog: BrowseCatalog?, state: DiscoveryViewModel.State, language: String, unlocked: Boolean,
                           onOpen: (String) -> Unit, onFavorite: (String) -> Unit,
                           onImport: () -> Unit, onPlayer: () -> Unit, onToggle: () -> Unit, onMedia: () -> Unit,
                           session: com.mylittleblover.mkwplayer.player.PlayerViewModel.Ui, currentCode: String?,
                           packDirectory: java.io.File? = null, artworkNames: List<String> = emptyList(),
                           courseArtwork: Map<String, String> = emptyMap(),
                           jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
                           extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
                           progressFlow: kotlinx.coroutines.flow.StateFlow<com.mylittleblover.mkwplayer.player.PlayerViewModel.ProgressUi>? = null,
                           onQuickPlay: (String) -> Unit = {},
                           onResumeSaved: () -> Unit = {},
                           onDismissSaved: () -> Unit = {},
                           playStats: Map<String, TrackPlayStat> = emptyMap(),
                           onPlaySelectedRefs: (List<String>) -> Boolean = { false }) {
    val courseMusicSubtitle = stringResource(R.string.discovery_course_music)
    val coursesSubtitle = stringResource(R.string.discovery_courses)
    val sfcSubtitle = stringResource(R.string.discovery_vs_sfc)
    val battleSubtitle = stringResource(R.string.discovery_battle)
    val freeSubtitle = stringResource(R.string.discovery_free)
    val otherSubtitle = stringResource(R.string.discovery_other)
    val soundsSubtitle = stringResource(R.string.discovery_sounds)
    val nmSubtitle = stringResource(R.string.discovery_nintendo_music)
    data class DailyCardInfo(
        val rawId: String,
        val title: String,
        val subtitle: String,
        val artworkName: String?,
        val fallbackCourseCode: String?,
    )
    val staffCreditsUnlocked = LocalStaffCreditsUnlocked.current
    val visibleCourseSet = catalog?.courses(unlocked).orEmpty().toSet()
    val gamesById = remember(jukebox) { jukebox.games.associateBy { it.id } }
    val visibleDaily = remember(
        state.dailyIds,
        catalog,
        unlocked,
        staffCreditsUnlocked,
        language,
        jukebox,
        extraCatalog,
        courseArtwork,
        courseMusicSubtitle,
        sfcSubtitle,
        battleSubtitle,
        freeSubtitle,
        otherSubtitle,
    ) {
        state.dailyIds.mapNotNull { rawId ->
            when (val ref = com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(rawId)) {
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> {
                    val logical = ref.logicalCode
                    if (logical !in visibleCourseSet && rawId !in visibleCourseSet) null
                    else DailyCardInfo(
                        rawId = rawId,
                        title = catalog?.displayName(logical, language) ?: logical,
                        subtitle = courseMusicSubtitle,
                        artworkName = courseArtwork[rawId] ?: courseArtwork[logical],
                        fallbackCourseCode = logical,
                    )
                }
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack -> {
                    val track = extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId }
                    if (track == null || !ExtraMusicCatalog.isVisible(track.id, unlocked, staffCreditsUnlocked)) null
                    else DailyCardInfo(
                        rawId = rawId,
                        title = track.localizedName(language),
                        subtitle = sfcSubtitle,
                        artworkName = track.artwork,
                        fallbackCourseCode = null,
                    )
                }
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack -> {
                    val track = extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId }
                    if (track == null || !ExtraMusicCatalog.isVisible(track.id, unlocked, staffCreditsUnlocked)) null
                    else DailyCardInfo(
                        rawId = rawId,
                        title = track.localizedName(language),
                        subtitle = battleSubtitle,
                        artworkName = track.artwork,
                        fallbackCourseCode = null,
                    )
                }
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox -> {
                    val item = jukebox.itemsByKey[ref.trackKey]
                    if (item == null) null
                    else {
                        val game = gamesById[item.gameId]
                        DailyCardInfo(
                            rawId = rawId,
                            title = item.displayTitle(language),
                            subtitle = game?.localizedName(language) ?: freeSubtitle,
                            artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
                                ?: extraCatalog.jukeboxGameArtwork[item.gameId],
                            fallbackCourseCode = null,
                        )
                    }
                }
                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack -> {
                    val other = extraCatalog.otherMusic.firstOrNull { it.id == ref.extraId }
                    if (other == null || !ExtraMusicCatalog.isVisible(other.id, unlocked, staffCreditsUnlocked)) null
                    else DailyCardInfo(
                        rawId = rawId,
                        title = other.localizedName(language),
                        subtitle = otherSubtitle,
                        artworkName = other.artwork,
                        fallbackCourseCode = null,
                    )
                }
            }
        }
    }
    val mostPlayedHome = remember(playStats, catalog, unlocked, staffCreditsUnlocked, language, extraCatalog, jukebox) {
        playStats.values
            .asSequence()
            .filter { it.playCount > 0 }
            .sortedWith(compareByDescending<TrackPlayStat> { it.playCount }.thenByDescending { it.lastPlayedEpochMs })
            .mapNotNull { stat ->
                resolveSmartPlaylistEntry(
                    rawId = stat.trackId,
                    catalog = catalog,
                    unlocked = unlocked,
                    staffCreditsUnlocked = staffCreditsUnlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukebox.itemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesSubtitle,
                    sfcLabel = sfcSubtitle,
                    battleLabel = battleSubtitle,
                    freeLabel = freeSubtitle,
                    otherLabel = otherSubtitle,
                    soundsLabel = soundsSubtitle,
                    nmLabel = nmSubtitle,
                    playCount = stat.playCount,
                )
            }
            .distinctBy { it.rawRef }
            .take(30)
            .toList()
    }
    val favoritesMixHome = remember(state.favorites, playStats, catalog, unlocked, staffCreditsUnlocked, language, extraCatalog, jukebox) {
        state.favorites
            .mapNotNull { fav ->
                val plays = playStats[canonicalStatIdForRef(fav)]?.playCount ?: 0
                resolveSmartPlaylistEntry(
                    rawId = fav,
                    catalog = catalog,
                    unlocked = unlocked,
                    staffCreditsUnlocked = staffCreditsUnlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukebox.itemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesSubtitle,
                    sfcLabel = sfcSubtitle,
                    battleLabel = battleSubtitle,
                    freeLabel = freeSubtitle,
                    otherLabel = otherSubtitle,
                    soundsLabel = soundsSubtitle,
                    nmLabel = nmSubtitle,
                    playCount = plays,
                )
            }
            .distinctBy { it.rawRef }
    }
    val recent = remember(
        state.recentIds,
        visibleCourseSet,
        catalog,
        unlocked,
        staffCreditsUnlocked,
        language,
        extraCatalog,
        jukebox,
        coursesSubtitle,
        sfcSubtitle,
        battleSubtitle,
        freeSubtitle,
        otherSubtitle,
        soundsSubtitle,
        nmSubtitle,
    ) {
        state.recentIds
            .mapNotNull { rawId ->
                resolveSmartPlaylistEntry(
                    rawId = rawId,
                    catalog = catalog,
                    unlocked = unlocked,
                    staffCreditsUnlocked = staffCreditsUnlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukebox.itemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesSubtitle,
                    sfcLabel = sfcSubtitle,
                    battleLabel = battleSubtitle,
                    freeLabel = freeSubtitle,
                    otherLabel = otherSubtitle,
                    soundsLabel = soundsSubtitle,
                    nmLabel = nmSubtitle,
                    playCount = 0,
                )
            }
            .distinctBy { it.rawRef }
            .take(12)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 14.dp,
            end = 18.dp,
            bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(14.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.discovery_home_headline), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.discovery_home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.favoritesError != null || state.dailyError != null || state.recentError != null) item {
            Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error)
        }
        if (catalog == null || visibleDaily.isEmpty()) {
            if (!session.libraryReady) {
                item {
                    com.mylittleblover.mkwplayer.ui.MkwLinearIndeterminateBar(
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        color = MkwKitTokens.Palette.crimson,
                        trackColor = MkwKitTokens.Palette.rose,
                    )
                }
            } else {
                item { Text(stringResource(R.string.discovery_empty)) }
            }
        }
        else item {
            val pager = rememberPagerState(pageCount = { visibleDaily.size })
            val scope = rememberCoroutineScope()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().height(236.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.15f)),
                    pageSpacing = 12.dp,
                ) { index ->
                    val card = visibleDaily[index]
                    val id = card.rawId
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onQuickPlay(id) },
                    ) {
                        if (card.fallbackCourseCode != null) {
                            CourseCover(
                                code = card.fallbackCourseCode,
                                modifier = Modifier.fillMaxSize(),
                                packDirectory = packDirectory,
                                artworkName = card.artworkName,
                            )
                        } else {
                            PackArtwork.Thumbnail(
                                packDirectory = packDirectory,
                                artworkNames = listOfNotNull(card.artworkName),
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp),
                                    )
                                }
                            }
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xCC000000)))))
                        card.fallbackCourseCode?.let { StageArt.resolve(it) }?.let {
                            ResourceArtwork(it, Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 12.dp).size(88.dp))
                        }
                        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            MkwBadge(
                                text = "${stringResource(R.string.discovery_home_today)} · ${index + 1} / ${visibleDaily.size}",
                                tone = MkwBadgeTone.Scrim,
                                tabular = true,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(card.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(card.subtitle, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                MkwIconButton(
                                    icon = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.discovery_start),
                                    onClick = { onQuickPlay(id) },
                                    tone = MkwIconButtonTone.Scrim,
                                    visualSize = 44.dp,
                                    iconSize = 22.dp,
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(visibleDaily.size) { index ->
                        Box(
                            Modifier.padding(horizontal = 2.dp).width(if (index == pager.currentPage) 18.dp else 6.dp).height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (index == pager.currentPage) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.trackStrong),
                        )
                    }
                }
            }
        }
        if (session.libraryReady && visibleDaily.isEmpty()) item {
            MkwSecondaryButton(
                text = stringResource(R.string.discovery_import),
                onClick = onImport,
                size = MkwButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!session.canStop && session.continueListening != null) {
            val saved = session.continueListening
            item { Text(stringResource(R.string.discovery_continue_listening), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink) }
            item {
                MkwCard(
                    onClick = onResumeSaved,
                    tone = MkwSurfaceTone.Porcelain,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PackArtwork.Thumbnail(
                            packDirectory,
                            listOfNotNull(saved.artworkName),
                            Modifier.size(72.dp).clip(MkwKitTokens.Shape.card),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(MkwKitTokens.Palette.rose),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.crimsonDeep, modifier = Modifier.size(30.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(saved.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                saved.subtitle.takeIf { it.isNotBlank() },
                                "${stringResource(R.string.discovery_continue_paused)} · ${saved.positionText} / ${saved.totalText}",
                            ).joinToString(" · ")
                            Text(meta, style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall), color = MkwKitTokens.Palette.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(saved.progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                        }
                        MkwIconButton(
                            icon = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.discovery_continue_resume),
                            onClick = onResumeSaved,
                            tone = MkwIconButtonTone.Ink,
                            visualSize = 42.dp,
                            iconSize = 22.dp,
                        )
                        MkwIconButton(
                            icon = Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.discovery_continue_dismiss),
                            onClick = onDismissSaved,
                            visualSize = 36.dp,
                            iconSize = 18.dp,
                            tintOverride = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                }
            }
        } else if (session.canStop) {
            item { Text(stringResource(R.string.discovery_continue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink) }
            item {
                MkwCard(
                    onClick = onPlayer,
                    tone = MkwSurfaceTone.Porcelain,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PackArtwork.Thumbnail(packDirectory, artworkNames, Modifier.size(72.dp).clip(MkwKitTokens.Shape.card)) {
                            if (currentCode != null) {
                                com.mylittleblover.mkwplayer.ui.art.CourseCover(currentCode, Modifier.fillMaxSize(), showIcon = false)
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .background(MkwKitTokens.Palette.rose),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.crimsonDeep, modifier = Modifier.size(30.dp))
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(currentCode?.let { catalog?.displayName(it, language) } ?: session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val flow = session.flow
                            val subtitle = flow?.let { com.mylittleblover.mkwplayer.ui.interactive.performanceStageText(it, currentCode) }
                                ?: session.linearTrack?.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                                    "$sub · ${stringResource(if (session.canResume) R.string.discovery_paused else R.string.discovery_playing)}"
                                }
                                ?: stringResource(if (session.canResume) R.string.discovery_paused else R.string.discovery_playing)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            DiscoveryHomeProgressBar(
                                flow = flow,
                                fallbackProgress = session.progress,
                                progressFlow = progressFlow,
                            )
                        }
                        val label = stringResource(if (session.canPause) R.string.interactive_pause else R.string.interactive_resume)
                        MkwIconButton(
                            icon = if (session.canPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = label,
                            onClick = onToggle,
                            enabled = session.canPause || session.canResume,
                            tone = MkwIconButtonTone.Ink,
                            visualSize = 44.dp,
                            iconSize = 22.dp,
                        )
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { Text(stringResource(R.string.discovery_recent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink) }
            item {
                LazyRow(
                    modifier = Modifier.unpadHorizontal(18.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recent, key = { "recent:${it.rawRef}" }) { entry ->
                        RecentTrackTile(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            favorite = entry.rawRef in state.favorites,
                            onOpen = { onOpen(entry.rawRef) },
                            onFavorite = { onFavorite(entry.rawRef) },
                            packDirectory = packDirectory,
                            artworkName = entry.artworkName,
                            fallbackCourseCode = entry.fallbackCourseCode,
                        )
                    }
                }
            }
        }
        if (mostPlayedHome.isNotEmpty() || favoritesMixHome.isNotEmpty()) {
            item { Text(stringResource(R.string.smart_playlists_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (mostPlayedHome.isNotEmpty()) {
                item {
                    SmartPlaylistCard(
                        title = stringResource(R.string.smart_most_played),
                        subtitle = stringResource(R.string.smart_most_played_subtitle),
                        emptyText = stringResource(R.string.smart_most_played_empty),
                        entries = mostPlayedHome,
                        packDirectory = packDirectory,
                        onPlayRefs = onPlaySelectedRefs,
                    )
                }
            }
            if (favoritesMixHome.isNotEmpty()) {
                item {
                    SmartPlaylistCard(
                        title = stringResource(R.string.smart_favorites_mix),
                        subtitle = stringResource(R.string.smart_favorites_mix_subtitle),
                        emptyText = stringResource(R.string.smart_favorites_mix_empty),
                        entries = favoritesMixHome,
                        packDirectory = packDirectory,
                        onPlayRefs = onPlaySelectedRefs,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryMedia(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
    category: Int, onCategory: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
    onCourse: (String) -> Unit, onCup: (String) -> Unit,
    jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    packDirectory: java.io.File? = null,
    activeTrackKey: String? = null,
    allowPlayNext: Boolean = true,
    unlockProgress: String? = null,
    unlockCompleted: Int = 0,
    unlockTotal: Int = 0,
    onJukeboxGame: (String) -> Unit = {},
    onPlayJukeboxTrack: (JukeboxCatalog.Game, Int) -> Unit = { _, _ -> },
    onPlayJukeboxGenre: (JukeboxCatalog.GenreInfo, Int, Boolean) -> Unit = { _, _, _ -> },
    onPlayAllJukebox: (Boolean) -> Unit = {},
    freeRoamMode: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode =
        com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK,
    freeRoamCatalog: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.Data? = null,
    onSelectFreeRoamMode: (com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode, String) -> Unit = { _, _ -> },
    onPlayFreeRoamRadio: (com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode, String) -> Unit = { _, _ -> },
    onPlayJukeboxNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit = { _, _ -> },
    onJukeboxTrack: (String) -> Unit = {},
    onSfcCourse: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
    onBattleTrack: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
    onOpenTitle: () -> Unit = {},
    onPlayExtraQueue: (List<com.mylittleblover.mkwplayer.player.LinearQueue.Entry>, Int, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode, Boolean) -> Unit = { _, _, _, _ -> },
    onAuditionExtra: (com.mylittleblover.mkwplayer.player.LinearQueue.Entry) -> Unit = {},
    extraMedleyCatalog: com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog = com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog(),
    onPlayExtraMedley: (id: String, title: String, subtitle: String, artwork: String?) -> Unit = { _, _, _, _ -> },
    onOpenExtraFlowEditor: (String) -> Unit = {},
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
    onPlayNextRefs: (List<String>) -> Boolean = { false },
    onAddToQueueRefs: (List<String>) -> Boolean = { false },
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
    onOpenCourseFlowEditor: (String) -> Unit = {},
    onLockedMysteryCourseClick: (() -> Unit)? = null,
    playStats: Map<String, TrackPlayStat> = emptyMap()) {
    val categoryStateHolder = rememberSaveableStateHolder()
    androidx.compose.animation.AnimatedContent(category, transitionSpec = {
        com.mylittleblover.mkwplayer.ui.AppMotion.page(if (targetState < 0) -1 else 1)
    }, label = "media-category") { shown ->
        categoryStateHolder.SaveableStateProvider(shown) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                DiscoveryMediaContent(catalog, unlocked, language, shown, onCategory, query, onQuery, onCourse, onCup,
                    jukebox = jukebox,
                    extraCatalog = extraCatalog,
                    packDirectory = packDirectory,
                    activeTrackKey = activeTrackKey,
                    allowPlayNext = allowPlayNext,
                    unlockProgress = unlockProgress,
                    unlockCompleted = unlockCompleted,
                    unlockTotal = unlockTotal,
                    onJukeboxGame = onJukeboxGame,
                    onPlayJukeboxTrack = onPlayJukeboxTrack,
                    onPlayJukeboxGenre = onPlayJukeboxGenre,
                    onPlayAllJukebox = onPlayAllJukebox,
                    freeRoamMode = freeRoamMode,
                    freeRoamCatalog = freeRoamCatalog,
                    onSelectFreeRoamMode = onSelectFreeRoamMode,
                    onPlayFreeRoamRadio = onPlayFreeRoamRadio,
                    onPlayJukeboxNext = onPlayJukeboxNext,
                    onJukeboxTrack = onJukeboxTrack,
                    onSfcCourse = onSfcCourse,
                    onBattleTrack = onBattleTrack,
                    onOpenTitle = onOpenTitle,
                    onPlayExtraQueue = onPlayExtraQueue,
                    onAuditionExtra = onAuditionExtra,
                    extraMedleyCatalog = extraMedleyCatalog,
                    onPlayExtraMedley = onPlayExtraMedley,
                    onOpenExtraFlowEditor = onOpenExtraFlowEditor,
                    playlists = playlists,
                    favorites = favorites,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
                    onPlayNextRefs = onPlayNextRefs,
                    onAddToQueueRefs = onAddToQueueRefs,
                    onPlaySelectedRefs = onPlaySelectedRefs,
                    onOpenCourseFlowEditor = onOpenCourseFlowEditor,
                    onLockedMysteryCourseClick = onLockedMysteryCourseClick,
                    playStats = playStats)
            }
        }
    }
}

@Composable
private fun DiscoveryMediaContent(catalog: BrowseCatalog?, unlocked: Boolean, language: String,
                            category: Int, onCategory: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
                            onCourse: (String) -> Unit, onCup: (String) -> Unit,
                            jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
                            extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
                            packDirectory: java.io.File? = null,
                            activeTrackKey: String? = null,
                            allowPlayNext: Boolean = true,
                            unlockProgress: String? = null,
                            unlockCompleted: Int = 0,
                            unlockTotal: Int = 0,
                            onJukeboxGame: (String) -> Unit = {},
                            onPlayJukeboxTrack: (JukeboxCatalog.Game, Int) -> Unit = { _, _ -> },
                            onPlayJukeboxGenre: (JukeboxCatalog.GenreInfo, Int, Boolean) -> Unit = { _, _, _ -> },
                            onPlayAllJukebox: (Boolean) -> Unit = {},
                            freeRoamMode: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode =
                                com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK,
                            freeRoamCatalog: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.Data? = null,
                            onSelectFreeRoamMode: (com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode, String) -> Unit = { _, _ -> },
                            onPlayFreeRoamRadio: (com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode, String) -> Unit = { _, _ -> },
                            onPlayJukeboxNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit = { _, _ -> },
                            onJukeboxTrack: (String) -> Unit = {},
                            onSfcCourse: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
                            onBattleTrack: (ExtraMusicCatalog.TwoStateTrack) -> Unit = {},
                            onOpenTitle: () -> Unit = {},
                            onPlayExtraQueue: (List<com.mylittleblover.mkwplayer.player.LinearQueue.Entry>, Int, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode, Boolean) -> Unit = { _, _, _, _ -> },
                            onAuditionExtra: (com.mylittleblover.mkwplayer.player.LinearQueue.Entry) -> Unit = {},
                            extraMedleyCatalog: com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog = com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog(),
                            onPlayExtraMedley: (id: String, title: String, subtitle: String, artwork: String?) -> Unit = { _, _, _, _ -> },
                            onOpenExtraFlowEditor: (String) -> Unit = {},
                            playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
                            favorites: Set<String> = emptySet(),
                            onToggleFavorite: (String) -> Unit = {},
                            onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
                            onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
                            onPlayNextRefs: (List<String>) -> Boolean = { false },
                            onAddToQueueRefs: (List<String>) -> Boolean = { false },
                            onPlaySelectedRefs: (List<String>) -> Boolean = { false },
                            onOpenCourseFlowEditor: (String) -> Unit = {},
                            onLockedMysteryCourseClick: (() -> Unit)? = null,
                            playStats: Map<String, TrackPlayStat> = emptyMap()) {
    val staffCreditsUnlocked = LocalStaffCreditsUnlocked.current
    val loopConfig = LocalNonInteractiveLoopConfig.current
    androidx.activity.compose.BackHandler(enabled = category >= 0) { onCategory(-1) }
    var sortOrder by rememberSaveable(category) { mutableStateOf(TrackSortOrder.DEFAULT) }
    val categories = listOf(
        R.string.discovery_courses,
        R.string.discovery_battle,
        R.string.discovery_free,
        R.string.discovery_other,
        R.string.discovery_sounds,
        R.string.discovery_nintendo_music,
    )
    val routes = if (category < 0 && query.isNotBlank()) {
        catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked, query).orEmpty() +
            catalog?.routes(OfficialRouteKind.RALLY, unlocked, query).orEmpty()
    } else {
        emptyList()
    }
    val rawCourses = if (category == 0 || (category < 0 && query.isNotBlank())) catalog?.searchCourses(query, unlocked).orEmpty() else emptyList()
    val courses = remember(rawCourses, sortOrder, playStats, catalog, language) {
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> rawCourses.sortedBy { (catalog?.displayName(it, language) ?: it).lowercase() }
            TrackSortOrder.MOST_PLAYED -> rawCourses.sortedByDescending { playStats[it.removeSuffix("_00")]?.playCount ?: 0 }
            else -> rawCourses
        }
    }
    val showLockedRainbow = remember(unlocked, catalog, category, query, rawCourses) {
        !unlocked && catalog != null &&
            (category == 0 || (category < 0 && query.isNotBlank())) &&
            "Rainbow" !in rawCourses &&
            "Rainbow" in catalog.searchCourses("", specialUnlocked = true) &&
            (query.isBlank() || "?" in query || "？" in query)
    }
    val sfcCourses = remember(extraCatalog, query, category, sortOrder, playStats, language) {
        val base = if (category == 0 || (category < 0 && query.isNotBlank())) extraCatalog.sfcCourses.filter { it.matches(query) } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.SfcTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val battleTracks = remember(extraCatalog, query, category, sortOrder, playStats, language) {
        val base = if (category == 1 || (category < 0 && query.isNotBlank())) extraCatalog.battleTracks.filter { it.matches(query) } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.BattleTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val otherItems = remember(extraCatalog, query, category, unlocked, staffCreditsUnlocked, loopConfig, sortOrder, playStats, language) {
        val base = if (category == 3 || (category < 0 && query.isNotBlank())) {
            extraCatalog.otherMusic.filter {
                ExtraMusicCatalog.isVisible(it.id, unlocked, staffCreditsUnlocked) && it.matches(query)
            }
        } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.DURATION_DESC -> base.sortedByDescending { effectiveExtraDurationMs(it, loopConfig) }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.ExtraTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val shortItems = remember(extraCatalog, query, category, unlocked, staffCreditsUnlocked, loopConfig, sortOrder, playStats, language) {
        val base = if (category == 4 || (category < 0 && query.isNotBlank())) {
            extraCatalog.shortSounds.filter {
                ExtraMusicCatalog.isVisible(it.id, unlocked, staffCreditsUnlocked) && it.matches(query)
            }
        } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.DURATION_DESC -> base.sortedByDescending { effectiveSimpleDurationMs(it, loopConfig) }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.ExtraTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val nmItems = remember(extraCatalog, query, category, unlocked, staffCreditsUnlocked, loopConfig, sortOrder, playStats, language) {
        val base = if (category == 5 || (category < 0 && query.isNotBlank())) {
            extraCatalog.nintendoMusicSpecial.filter {
                ExtraMusicCatalog.isVisible(it.id, unlocked, staffCreditsUnlocked) && it.matches(query)
            }
        } else emptyList()
        when (sortOrder) {
            TrackSortOrder.NAME_ASC -> base.sortedBy { it.localizedName(language).lowercase() }
            TrackSortOrder.DURATION_DESC -> base.sortedByDescending { effectiveSimpleDurationMs(it, loopConfig) }
            TrackSortOrder.MOST_PLAYED -> base.sortedByDescending { playStats[PlaylistItemRef.ExtraTrack(it.id).raw]?.playCount ?: 0 }
            else -> base
        }
    }
    val jukeboxSearch = remember(jukebox, query, sortOrder, playStats, language) {
        if (query.isBlank()) {
            JukeboxCatalog.SearchResult(jukebox.games, emptyList())
        } else {
            val raw = jukebox.search(query)
            val sortedHits = when (sortOrder) {
                TrackSortOrder.NAME_ASC -> raw.trackHits.sortedBy { it.second.localizedTitle(language).lowercase() }
                TrackSortOrder.DURATION_DESC -> raw.trackHits.sortedByDescending { it.second.durationMs }
                TrackSortOrder.MOST_PLAYED -> raw.trackHits.sortedByDescending {
                    playStats[PlaylistItemRef.Jukebox(it.second.key).raw]?.playCount ?: 0
                }
                else -> raw.trackHits
            }
            raw.copy(trackHits = sortedHits)
        }
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val addedNextText = stringResource(R.string.jukebox_added_next)
    val addedQueueText = stringResource(R.string.track_action_added_to_queue)
    val interactiveSubtitle = stringResource(R.string.discovery_interactive)
    val coursesCatLabel = stringResource(R.string.discovery_courses)
    val sfcCatLabel = stringResource(R.string.discovery_vs_sfc)
    val battleCatLabel = stringResource(R.string.discovery_battle)
    val otherCategoryLabel = stringResource(R.string.discovery_other)
    val shortLabel = stringResource(R.string.discovery_sounds)
    val nmLabel = stringResource(R.string.discovery_nintendo_music)
    val extraSuiteLabel = stringResource(R.string.discovery_suite)
    val listState = rememberSaveable(category, saver = LazyListState.Saver) { LazyListState() }
    var selectedJukeboxDecade by rememberSaveable { mutableStateOf<Int?>(null) }
    var jukeboxBrowseByGenre by rememberSaveable { mutableStateOf(false) }
    var selectedGenreId by rememberSaveable { mutableStateOf<String?>(null) }

    var menuTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var infoTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var playlistPickerRefs by remember { mutableStateOf<List<String>?>(null) }
    var multiSelectMode by rememberSaveable(category) { mutableStateOf(false) }
    var selectedKeys by rememberSaveable(category) { mutableStateOf(listOf<String>()) }
    val selectedSet = remember(selectedKeys) { selectedKeys.toSet() }

    androidx.activity.compose.BackHandler(enabled = category == 2 && jukeboxBrowseByGenre && selectedGenreId != null && !multiSelectMode) {
        selectedGenreId = null
    }
    androidx.activity.compose.BackHandler(enabled = multiSelectMode) {
        multiSelectMode = false
        selectedKeys = emptyList()
    }

    val visibleRefEntries = remember(
        courses, sfcCourses, battleTracks, otherItems, shortItems, nmItems,
        category, query, jukeboxSearch,
    ) {
        buildList<Pair<String, String>> {
            courses.forEach { code -> add("course:$code" to code) }
            sfcCourses.forEach { t -> add("sfc:${t.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack(t.id).raw) }
            battleTracks.forEach { t -> add("battle:${t.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack(t.id).raw) }
            otherItems.forEach { item -> add("other:${item.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack(item.id).raw) }
            shortItems.forEach { item -> add("short:${item.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack(item.id).raw) }
            nmItems.forEach { item -> add("nm:${item.id}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack(item.id).raw) }
            if (category == 2 && query.isNotBlank()) {
                jukeboxSearch.trackHits.forEach { (_, item) ->
                    add("jb:${item.key}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw)
                }
            }
            if (category < 0 && query.isNotBlank()) {
                jukeboxSearch.trackHits.take(15).forEach { (_, item) ->
                    add("jb:${item.key}" to com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw)
                }
            }
        }
    }
    val visibleRefByKey = remember(visibleRefEntries) { visibleRefEntries.toMap() }
    val allSelectableKeys = remember(visibleRefEntries) { visibleRefEntries.map { it.first } }

    fun toggleSelected(key: String) {
        selectedKeys = if (key in selectedSet) selectedKeys - key else selectedKeys + key
    }

    TrackActionOverlayHosts(
        menuTarget = menuTarget,
        onDismissMenu = { menuTarget = null },
        playlistPickerRefs = playlistPickerRefs,
        onOpenPlaylistPicker = { refs -> playlistPickerRefs = refs },
        onDismissPlaylistPicker = { playlistPickerRefs = null },
        infoTarget = infoTarget,
        onOpenInfo = { infoTarget = it },
        onDismissInfo = { infoTarget = null },
        packDirectory = packDirectory,
        allowPlayNext = allowPlayNext,
        playlists = playlists,
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        onOpenCourse = onCourse,
        onOpenFlowEditor = { editorCode ->
            if (editorCode.startsWith("extra:")) {
                onOpenExtraFlowEditor(editorCode.removePrefix("extra:"))
            } else {
                onOpenCourseFlowEditor(editorCode)
            }
        },
        onEnterMultiSelect = { key ->
            multiSelectMode = true
            selectedKeys = (selectedKeys + key).distinct()
        },
        onPlayNextRefs = onPlayNextRefs,
        onAddToQueueRefs = onAddToQueueRefs,
        onAddToPlaylist = { pid, refs ->
            onAddToPlaylist(pid, refs)
            multiSelectMode = false
            selectedKeys = emptyList()
        },
        onCreatePlaylistAndAdd = { name, refs ->
            onCreatePlaylistAndAdd(name, refs)
            multiSelectMode = false
            selectedKeys = emptyList()
        },
        snackbarHostState = snackbarHostState,
        addedNextText = addedNextText,
        addedQueueText = addedQueueText,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = multiSelectMode) {
                val allSelected = allSelectableKeys.isNotEmpty() && selectedSet.containsAll(allSelectableKeys)
                MultiSelectTopBanner(
                    selectedCount = selectedSet.size,
                    allSelected = allSelected,
                    onToggleSelectAll = {
                        selectedKeys = if (allSelected) emptyList() else allSelectableKeys
                    },
                    onClose = {
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val searchHintRes = when (category) {
                    0 -> R.string.discovery_search_courses
                    2 -> R.string.discovery_search_jukebox
                    1, 3, 4, 5 -> R.string.discovery_search_tracks
                    else -> R.string.discovery_search_all
                }
                MkwTextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = stringResource(searchHintRes),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MkwKitTokens.Palette.inkSecondary) },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            MkwIconButton(
                                icon = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.discovery_cancel),
                                onClick = { onQuery("") },
                                visualSize = 32.dp,
                                iconSize = 18.dp,
                            )
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if ((category >= 0 && category != 2) || query.isNotBlank()) {
                com.mylittleblover.mkwplayer.ui.components.TrackSortBar(
                    selected = sortOrder,
                    includeOstOrder = false,
                    onSelect = { sortOrder = it },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (category >= 0) {
                MkwDivider()
            }
            val categoryHeight = 132.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(12.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (category < 0 && query.isBlank()) {
                    for (row in 0..2) item(key = "cat-row:$row") {
                        Row(
                            Modifier.fillMaxWidth().height(categoryHeight),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            for (column in 0..1) {
                                val index = row * 2 + column
                                MkwCard(
                                    onClick = { onCategory(index) },
                                    tone = MkwSurfaceTone.Porcelain,
                                    contentPadding = PaddingValues(12.dp),
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    Box(Modifier.fillMaxWidth().weight(1f)) {
                                        when (index) {
                                            0 -> ResourceArtwork(R.drawable.mode_vs, Modifier.align(Alignment.TopEnd).size(40.dp))
                                            1 -> ResourceArtwork(R.drawable.mode_balloon, Modifier.align(Alignment.TopEnd).size(40.dp))
                                            2 -> Surface(
                                                shape = MkwKitTokens.Shape.thumb,
                                                color = MkwKitTokens.Palette.crimson,
                                                modifier = Modifier.align(Alignment.TopEnd).size(40.dp),
                                            ) {
                                                ResourceArtwork(R.drawable.media_free_roam, Modifier.fillMaxSize().padding(4.dp))
                                            }
                                            3 -> ResourceArtwork(R.drawable.media_other_music, Modifier.align(Alignment.TopEnd).size(40.dp))
                                            4 -> ResourceArtwork(R.drawable.media_short_sfx, Modifier.align(Alignment.TopEnd).size(40.dp))
                                            else -> ResourceArtwork(R.drawable.media_nintendo_music, Modifier.align(Alignment.TopEnd).size(40.dp))
                                        }
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.BottomStart,
                                        ) {
                                            val categoryRes = categories[index]
                                            val rawCategoryLabel = stringResource(categoryRes)
                                            val categoryLabel = if (
                                                categoryRes == R.string.discovery_nintendo_music &&
                                                rawCategoryLabel.startsWith("Nintendo Music ")
                                            ) {
                                                "Nintendo Music\n" + rawCategoryLabel.removePrefix("Nintendo Music ")
                                            } else {
                                                rawCategoryLabel
                                            }
                                            Text(
                                                categoryLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MkwKitTokens.Palette.ink,
                                                minLines = 2,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                items(routes, key = { it.id }) { route -> RouteRow(route, catalog, language, packDirectory, extraCatalog.courseArtwork) { onCup(route.id) } }
                if ((courses.isNotEmpty() || showLockedRainbow) && (category == 0 || sfcCourses.isNotEmpty() || query.isNotBlank())) {
                    item(key = "vs-standard-header") {
                        Text(
                            stringResource(R.string.discovery_vs_standard),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                items(courses, key = { "course:$it" }) { code ->
                    val courseTitle = catalog?.displayName(code, language) ?: code
                    val selKey = "course:$code"
                    val plays = playStats[code.removeSuffix("_00")]?.playCount ?: 0
                    val subWithPlays = if (plays > 0) {
                        "$interactiveSubtitle · ${pluralStringResource(R.plurals.play_count_plurals, plays, plays)}"
                    } else {
                        interactiveSubtitle
                    }
                    val favId = code.removeSuffix("_00")
                    val isFav = favId in favorites || code in favorites
                    val spec = TrackMenuSpec(
                        selectionKey = selKey,
                        playlistRef = code,
                        title = courseTitle,
                        subtitle = subWithPlays,
                        artworkName = extraCatalog.courseArtwork[code],
                        fallbackCourseCode = code,
                        favoriteId = code,
                        courseCodeForDetail = code,
                        editorTargetCode = code,
                        infoPairs = listOf(
                            R.string.track_info_category to coursesCatLabel,
                            R.string.track_info_ref to code,
                        ),
                        onPlayNow = { onPlaySelectedRefs(listOf(code)) },
                    )
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                if (onPlayNextRefs(listOf(code))) {
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(code) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        ExtraSquareRow(
                            title = courseTitle,
                            subtitle = subWithPlays,
                            packDirectory = packDirectory,
                            artworkName = extraCatalog.courseArtwork[code],
                            fallbackCourseCode = code,
                            selectionMode = multiSelectMode,
                            selected = selKey in selectedSet,
                            onLongClick = {
                                if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                            },
                            onClick = {
                                if (multiSelectMode) toggleSelected(selKey) else onCourse(code)
                            },
                        )
                    }
                }
                if (showLockedRainbow && catalog != null) {
                    item(key = "course:Rainbow:locked") {
                        val toastContext = LocalContext.current
                        val lockedName = stringResource(R.string.media_course_locked_name)
                        val lockedBadge = stringResource(R.string.media_course_locked_badge)
                        val localizedProgress = if (unlockTotal > 0) {
                            stringResource(R.string.media_course_locked_progress, unlockCompleted, unlockTotal)
                        } else {
                            unlockProgress?.takeIf { it.isNotBlank() }
                        }
                        val lockedHint = if (localizedProgress.isNullOrBlank()) {
                            lockedBadge
                        } else {
                            stringResource(R.string.media_course_locked_hint, localizedProgress)
                        }
                        MkwCard(
                            onClick = {
                                if (onLockedMysteryCourseClick != null) {
                                    onLockedMysteryCourseClick()
                                } else {
                                    android.widget.Toast.makeText(toastContext, lockedHint, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            tone = MkwSurfaceTone.Porcelain,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(MkwKitTokens.Shape.thumb)
                                        .background(MkwKitTokens.Palette.track),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = lockedBadge,
                                        tint = MkwKitTokens.Palette.inkSecondary,
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = lockedName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MkwKitTokens.Palette.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        MkwBadge(text = lockedBadge, tone = MkwBadgeTone.Neutral, tabular = false)
                                    }
                                    if (!localizedProgress.isNullOrBlank()) {
                                        Text(
                                            text = localizedProgress,
                                            style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                            color = MkwKitTokens.Palette.inkSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (unlockTotal > 0) {
                                        com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(
                                            progress = (unlockCompleted.toFloat() / unlockTotal.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp),
                                            barHeight = 6.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (sfcCourses.isNotEmpty()) {
                    item(key = "vs-sfc-header") {
                        Text(
                            stringResource(R.string.discovery_vs_sfc),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                items(sfcCourses, key = { "sfc:${it.id}" }) { track ->
                    val trackTitle = track.localizedName(language)
                    val selKey = "sfc:${track.id}"
                    val refRaw = PlaylistItemRef.SfcTrack(track.id).raw
                    val plays = playStats[refRaw]?.playCount ?: 0
                    val subWithPlays = if (plays > 0) {
                        "$interactiveSubtitle · ${pluralStringResource(R.plurals.play_count_plurals, plays, plays)}"
                    } else {
                        interactiveSubtitle
                    }
                    val isFav = refRaw in favorites
                    val spec = TrackMenuSpec(
                        selectionKey = selKey,
                        playlistRef = refRaw,
                        title = trackTitle,
                        subtitle = subWithPlays,
                        artworkName = track.artwork,
                        favoriteId = refRaw,
                        courseCodeForDetail = "extra:${track.id}",
                        editorTargetCode = "extra:${track.id}",
                        infoPairs = listOf(
                            R.string.track_info_category to sfcCatLabel,
                            R.string.track_info_ref to track.id,
                        ),
                        onPlayNow = { onSfcCourse(track) },
                    )
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                if (onPlayNextRefs(listOf(refRaw))) {
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(refRaw) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        ExtraSquareRow(
                            title = trackTitle,
                            subtitle = subWithPlays,
                            packDirectory = packDirectory,
                            artworkName = track.artwork,
                            selectionMode = multiSelectMode,
                            selected = selKey in selectedSet,
                            onLongClick = {
                                if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                            },
                            onClick = {
                                if (multiSelectMode) toggleSelected(selKey) else onSfcCourse(track)
                            },
                        )
                    }
                }
                items(battleTracks, key = { "battle:${it.id}" }) { track ->
                    val trackTitle = track.localizedName(language)
                    val selKey = "battle:${track.id}"
                    val refRaw = PlaylistItemRef.BattleTrack(track.id).raw
                    val plays = playStats[refRaw]?.playCount ?: 0
                    val subWithPlays = if (plays > 0) {
                        "$interactiveSubtitle · ${pluralStringResource(R.plurals.play_count_plurals, plays, plays)}"
                    } else {
                        interactiveSubtitle
                    }
                    val isFav = refRaw in favorites
                    val spec = TrackMenuSpec(
                        selectionKey = selKey,
                        playlistRef = refRaw,
                        title = trackTitle,
                        subtitle = subWithPlays,
                        artworkName = track.artwork,
                        favoriteId = refRaw,
                        courseCodeForDetail = "extra:${track.id}",
                        editorTargetCode = "extra:${track.id}",
                        infoPairs = listOf(
                            R.string.track_info_category to battleCatLabel,
                            R.string.track_info_ref to track.id,
                        ),
                        onPlayNow = { onBattleTrack(track) },
                    )
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                if (onPlayNextRefs(listOf(refRaw))) {
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(refRaw) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        ExtraSquareRow(
                            title = trackTitle,
                            subtitle = subWithPlays,
                            packDirectory = packDirectory,
                            artworkName = track.artwork,
                            selectionMode = multiSelectMode,
                            selected = selKey in selectedSet,
                            onLongClick = {
                                if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                            },
                            onClick = {
                                if (multiSelectMode) toggleSelected(selKey) else onBattleTrack(track)
                            },
                        )
                    }
                }
                if (otherItems.isNotEmpty()) {
                    val linearOther = otherItems.filterNot { it is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle }
                    val linearEntries = linearOther.map { item -> extraOtherToQueueEntry(item, language, otherCategoryLabel) }
                    if (category == 3 && linearEntries.isNotEmpty()) {
                        item(key = "other-actions") {
                            ExtraQueueActionsRow(
                                count = otherItems.size,
                                onPlayAll = { onPlayExtraQueue(linearEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                                onShuffle = { onPlayExtraQueue(linearEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                            )
                        }
                    }
                    items(otherItems, key = { "other:${it.id}" }) { item ->
                        val itemTitle = item.localizedName(language)
                        val selKey = "other:${item.id}"
                        val refRaw = PlaylistItemRef.ExtraTrack(item.id).raw
                        val plays = playStats[refRaw]?.playCount ?: 0
                        val effDurMs = effectiveExtraDurationMs(item, loopConfig)
                        val baseSub = if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) {
                            interactiveSubtitle
                        } else {
                            buildList {
                                add(stringResource(R.string.discovery_normal_playback))
                                if (effDurMs > 0L) add(formatExtraDuration(effDurMs))
                            }.joinToString(" · ")
                        }
                        val sub = if (plays > 0) {
                            listOfNotNull(baseSub.takeIf { it.isNotBlank() }, pluralStringResource(R.plurals.play_count_plurals, plays, plays)).joinToString(" · ")
                        } else {
                            baseSub
                        }
                        val isFav = refRaw in favorites
                        val playAction: () -> Unit = {
                            if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) {
                                onOpenTitle()
                            } else {
                                val idx = linearOther.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                onPlayExtraQueue(linearEntries, idx, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false)
                            }
                        }
                        val spec = TrackMenuSpec(
                            selectionKey = selKey,
                            playlistRef = refRaw,
                            title = itemTitle,
                            subtitle = sub,
                            artworkName = item.artwork,
                            favoriteId = refRaw,
                            courseCodeForDetail = if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) "extra:${item.id}" else null,
                            editorTargetCode = if (item is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) "extra:${item.id}" else null,
                            infoPairs = buildList {
                                add(R.string.track_info_category to otherCategoryLabel)
                                if (effDurMs > 0L) add(R.string.track_info_duration to formatExtraDuration(effDurMs))
                                add(R.string.track_info_ref to item.id)
                            },
                            onPlayNow = playAction,
                        )
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode,
                            onSwipeStartToEnd = if (allowPlayNext) {
                                {
                                    if (onPlayNextRefs(listOf(refRaw))) {
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                }
                            } else null,
                            onSwipeEndToStart = { onToggleFavorite(refRaw) },
                            endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                        ) {
                            ExtraSquareRow(
                                title = itemTitle,
                                subtitle = sub,
                                packDirectory = packDirectory,
                                artworkName = item.artwork,
                                selectionMode = multiSelectMode,
                                selected = selKey in selectedSet,
                                onLongClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                },
                                onClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else playAction()
                                },
                            )
                        }
                    }
                }
                if (shortItems.isNotEmpty()) {
                    val shortEntries = shortItems.map { item -> extraSimpleToQueueEntry(item, language, shortLabel) }
                    if (category == 4) {
                        item(key = "short-actions") {
                            ExtraQueueActionsRow(
                                count = shortItems.size,
                                onPlayAll = { onPlayExtraQueue(shortEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                                onShuffle = { onPlayExtraQueue(shortEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                            )
                        }
                    }
                    itemsIndexed(shortItems, key = { _, item -> "short:${item.id}" }) { idx, item ->
                        val effDurMs = effectiveSimpleDurationMs(item, loopConfig)
                        val baseSub = if (effDurMs > 0L) formatExtraDuration(effDurMs) else ""
                        val itemTitle = item.localizedName(language)
                        val selKey = "short:${item.id}"
                        val refRaw = PlaylistItemRef.ExtraTrack(item.id).raw
                        val plays = playStats[refRaw]?.playCount ?: 0
                        val sub = if (plays > 0) {
                            listOfNotNull(baseSub.takeIf { it.isNotBlank() }, pluralStringResource(R.plurals.play_count_plurals, plays, plays)).joinToString(" · ")
                        } else {
                            baseSub
                        }
                        val isFav = refRaw in favorites
                        val playAction: () -> Unit = {
                            onPlayExtraQueue(shortEntries, idx, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false)
                        }
                        val spec = TrackMenuSpec(
                            selectionKey = selKey,
                            playlistRef = refRaw,
                            title = itemTitle,
                            subtitle = if (sub.isNotBlank()) sub else shortLabel,
                            artworkName = item.artwork,
                            favoriteId = refRaw,
                            infoPairs = buildList {
                                add(R.string.track_info_category to shortLabel)
                                if (effDurMs > 0L) add(R.string.track_info_duration to formatExtraDuration(effDurMs))
                                add(R.string.track_info_ref to item.id)
                            },
                            onPlayNow = playAction,
                        )
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode,
                            onSwipeStartToEnd = if (allowPlayNext) {
                                {
                                    if (onPlayNextRefs(listOf(refRaw))) {
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                }
                            } else null,
                            onSwipeEndToStart = { onToggleFavorite(refRaw) },
                            endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                        ) {
                            ExtraSquareRow(
                                title = itemTitle,
                                subtitle = sub,
                                packDirectory = packDirectory,
                                artworkName = item.artwork,
                                onAction = { onAuditionExtra(shortEntries[idx]) },
                                actionContentDescription = stringResource(R.string.discovery_audition),
                                selectionMode = multiSelectMode,
                                selected = selKey in selectedSet,
                                onLongClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                },
                                onClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else playAction()
                                },
                            )
                        }
                    }
                }
            if (nmItems.isNotEmpty()) {
                val nmEntries = nmItems.map { item -> extraSimpleToQueueEntry(item, language, nmLabel) }
                if (category == 5) {
                    item(key = "nm-actions") {
                        ExtraQueueActionsRow(
                            count = nmItems.size,
                            onPlayAll = { onPlayExtraQueue(nmEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false) },
                            onShuffle = { onPlayExtraQueue(nmEntries, 0, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, true) },
                        )
                    }
                }
                items(nmItems, key = { "nm:${it.id}" }) { item ->
                    val effDurMs = effectiveSimpleDurationMs(item, loopConfig)
                    val baseSub = if (effDurMs > 0L) formatExtraDuration(effDurMs) else ""
                    val itemTitle = item.localizedName(language)
                    val selKey = "nm:${item.id}"
                    val refRaw = PlaylistItemRef.ExtraTrack(item.id).raw
                    val plays = playStats[refRaw]?.playCount ?: 0
                    val sub = if (plays > 0) {
                        listOfNotNull(baseSub.takeIf { it.isNotBlank() }, pluralStringResource(R.plurals.play_count_plurals, plays, plays)).joinToString(" · ")
                    } else {
                        baseSub
                    }
                    val isFav = refRaw in favorites
                    val idx = nmItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                    val playAction: () -> Unit = {
                        onPlayExtraQueue(nmEntries, idx, com.mylittleblover.mkwplayer.player.LinearQueue.RepeatMode.LIST, false)
                    }
                    val spec = TrackMenuSpec(
                        selectionKey = selKey,
                        playlistRef = refRaw,
                        title = itemTitle,
                        subtitle = if (sub.isNotBlank()) sub else nmLabel,
                        artworkName = item.artwork,
                        favoriteId = refRaw,
                        infoPairs = buildList {
                            add(R.string.track_info_category to nmLabel)
                            if (effDurMs > 0L) add(R.string.track_info_duration to formatExtraDuration(effDurMs))
                            add(R.string.track_info_ref to item.id)
                        },
                        onPlayNow = playAction,
                    )
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                if (onPlayNextRefs(listOf(refRaw))) {
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(refRaw) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        ExtraSquareRow(
                            title = itemTitle,
                            subtitle = sub,
                            packDirectory = packDirectory,
                            artworkName = item.artwork,
                            selectionMode = multiSelectMode,
                            selected = selKey in selectedSet,
                            onLongClick = {
                                if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                            },
                            onClick = {
                                if (multiSelectMode) toggleSelected(selKey) else playAction()
                            },
                        )
                    }
                }
            }
            if (category == 2) {
                if (!jukebox.isEmpty) {
                    item(key = "jukebox-head") {
                        val freeRoamModeLabels = LinkedHashMap<FreeRoamJukeboxCatalog.FreeRoamMode, String>(
                            FreeRoamJukeboxCatalog.FreeRoamMode.entries.size,
                        )
                        val freeRoamContextTitles = LinkedHashMap<FreeRoamJukeboxCatalog.FreeRoamMode, String>(
                            FreeRoamJukeboxCatalog.FreeRoamMode.entries.size,
                        )
                        for (mode in FreeRoamJukeboxCatalog.FreeRoamMode.entries) {
                            val label = freeRoamModeDisplayLabel(mode)
                            freeRoamModeLabels[mode] = label
                            freeRoamContextTitles[mode] = stringResource(R.string.jukebox_freeroam_context_fmt, label)
                        }
                        val activeModeLabel = freeRoamModeLabels[freeRoamMode]
                            ?: stringResource(R.string.jukebox_freeroam_mode_auto)
                        val activeContextTitle = freeRoamContextTitles[freeRoamMode]
                            ?: stringResource(R.string.jukebox_freeroam_context_fmt, activeModeLabel)
                        val isInGameGroup = FreeRoamJukeboxCatalog.isInGameFreeRoamLibrary(freeRoamMode)
                        var lastInGameModeName by rememberSaveable {
                            mutableStateOf(
                                if (isInGameGroup) freeRoamMode.name else FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK.name,
                            )
                        }
                        var lastOnlineModeName by rememberSaveable {
                            mutableStateOf(
                                if (!isInGameGroup) freeRoamMode.name else FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_DAY.name,
                            )
                        }
                        LaunchedEffect(freeRoamMode) {
                            if (FreeRoamJukeboxCatalog.isInGameFreeRoamLibrary(freeRoamMode)) {
                                lastInGameModeName = freeRoamMode.name
                            } else {
                                lastOnlineModeName = freeRoamMode.name
                            }
                        }
                        val activeHour = remember(freeRoamMode) { java.time.LocalTime.now().hour }
                        val activePlaylist = remember(freeRoamCatalog, freeRoamMode, activeHour) {
                            freeRoamCatalog?.resolvedPlaylistForFreeRoam(freeRoamMode, activeHour)
                        }
                        val resolvedPlaylistLabel = when (activePlaylist?.id) {
                            FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT ->
                                stringResource(R.string.jukebox_freeroam_mode_solo_day)
                            FreeRoamJukeboxCatalog.PLAYLIST_NIGHT ->
                                stringResource(R.string.jukebox_freeroam_mode_solo_night)
                            FreeRoamJukeboxCatalog.PLAYLIST_SOLO_ALL ->
                                stringResource(R.string.jukebox_freeroam_mode_solo_all)
                            FreeRoamJukeboxCatalog.PLAYLIST_FREEROAM_ONLY ->
                                stringResource(R.string.jukebox_freeroam_mode_freeroam_only)
                            FreeRoamJukeboxCatalog.PLAYLIST_RACE ->
                                stringResource(R.string.jukebox_freeroam_mode_online_day)
                            FreeRoamJukeboxCatalog.PLAYLIST_RACE_NIGHT ->
                                stringResource(R.string.jukebox_freeroam_mode_online_night)
                            else -> activeModeLabel
                        }
                        val inGameGroupLabel = stringResource(R.string.jukebox_freeroam_group_ingame)
                        val onlineRaceGroupLabel = stringResource(R.string.jukebox_freeroam_group_online_race)
                        val visibleModes = if (isInGameGroup) {
                            FreeRoamJukeboxCatalog.FreeRoamMode.IN_GAME_FREE_ROAM_MODES
                        } else {
                            FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_RACE_MODES
                        }
                        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.jukebox_games_summary, jukebox.games.size, jukebox.trackCount),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MkwRoseButton(
                                    text = stringResource(R.string.jukebox_play_all),
                                    onClick = { onPlayAllJukebox(false) },
                                    leadingIcon = Icons.Filled.PlayArrow,
                                    size = MkwButtonSize.Compact,
                                    modifier = Modifier.weight(1f),
                                )
                                MkwSecondaryButton(
                                    text = stringResource(
                                        if (freeRoamCatalog != null) R.string.jukebox_freeroam_play else R.string.jukebox_shuffle,
                                    ),
                                    onClick = {
                                        if (freeRoamCatalog != null) {
                                            onPlayFreeRoamRadio(freeRoamMode, activeContextTitle)
                                        } else {
                                            onPlayAllJukebox(true)
                                        }
                                    },
                                    leadingIcon = Icons.Filled.Shuffle,
                                    size = MkwButtonSize.Compact,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (freeRoamCatalog != null) {
                                MkwCard(
                                    tone = MkwSurfaceTone.Recessed,
                                    shape = MkwKitTokens.Shape.control,
                                    contentPadding = PaddingValues(12.dp),
                                ) {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            stringResource(R.string.jukebox_freeroam_title),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MkwKitTokens.Palette.ink,
                                        )
                                        MkwSegmentedBar(
                                            items = listOf(true, false),
                                            selected = isInGameGroup,
                                            labelOf = { inGame ->
                                                if (inGame) inGameGroupLabel else onlineRaceGroupLabel
                                            },
                                            onSelect = { wantInGame ->
                                                if (wantInGame != isInGameGroup) {
                                                    val targetMode = if (wantInGame) {
                                                        FreeRoamJukeboxCatalog.coerceInGameFreeRoamMode(
                                                            FreeRoamJukeboxCatalog.FreeRoamMode.fromStored(lastInGameModeName),
                                                        )
                                                    } else {
                                                        FreeRoamJukeboxCatalog.FreeRoamMode.fromStored(lastOnlineModeName)
                                                            .takeUnless { FreeRoamJukeboxCatalog.isInGameFreeRoamLibrary(it) }
                                                            ?: FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_DAY
                                                    }
                                                    val ctxTitle = freeRoamContextTitles[targetMode] ?: activeContextTitle
                                                    onSelectFreeRoamMode(targetMode, ctxTitle)
                                                }
                                            },
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(
                                                visibleModes,
                                                key = { "fr-mode:${it.name}" },
                                            ) { mode ->
                                                val ctxTitle = freeRoamContextTitles[mode] ?: activeContextTitle
                                                MkwFilterPill(
                                                    label = freeRoamModeChipLabel(mode),
                                                    selected = freeRoamMode == mode,
                                                    onClick = { onSelectFreeRoamMode(mode, ctxTitle) },
                                                    role = Role.RadioButton,
                                                )
                                            }
                                        }
                                        if (activePlaylist != null) {
                                            Text(
                                                stringResource(
                                                    R.string.jukebox_freeroam_rule_hint,
                                                    resolvedPlaylistLabel,
                                                    activePlaylist.uniqueCodes.size,
                                                    activePlaylist.slots.size,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MkwKitTokens.Palette.ink,
                                            )
                                        }
                                        Text(
                                            stringResource(
                                                if (isInGameGroup) {
                                                    R.string.jukebox_freeroam_group_ingame_hint
                                                } else {
                                                    R.string.jukebox_freeroam_group_online_hint
                                                },
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MkwKitTokens.Palette.inkSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (query.isBlank()) {
                    val allGenres = jukebox.allGenres
                    if (allGenres.isNotEmpty()) {
                        item(key = "jukebox-group-toggle") {
                            val byGameLabel = stringResource(R.string.jukebox_browse_by_game)
                            val byGenreLabel = stringResource(R.string.jukebox_browse_by_genre)
                            MkwSegmentedBar(
                                items = listOf(false, true),
                                selected = jukeboxBrowseByGenre,
                                labelOf = { isGenreTab -> if (isGenreTab) byGenreLabel else byGameLabel },
                                onSelect = { isGenreTab ->
                                    jukeboxBrowseByGenre = isGenreTab
                                    if (!isGenreTab) selectedGenreId = null
                                },
                            )
                        }
                    }

                    if (!jukeboxBrowseByGenre || allGenres.isEmpty()) {
                        val decades = jukebox.games.mapNotNull { g -> g.year.takeIf { it > 0 }?.let { (it / 10) * 10 } }.distinct().sorted()
                        val activeDecade = selectedJukeboxDecade?.takeIf { it in decades }
                        if (decades.size > 1) {
                            item(key = "jukebox-chronology-index") {
                                LazyRow(
                                    modifier = Modifier.unpadHorizontal(16.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item(key = "era-all") {
                                        MkwFilterPill(
                                            label = stringResource(R.string.jukebox_all_eras),
                                            selected = activeDecade == null,
                                            onClick = { selectedJukeboxDecade = null },
                                        )
                                    }
                                    items(decades, key = { "era:$it" }) { decade ->
                                        MkwFilterPill(
                                            label = stringResource(R.string.jukebox_decade_label, decade),
                                            selected = activeDecade == decade,
                                            onClick = {
                                                selectedJukeboxDecade = if (activeDecade == decade) null else decade
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        val shownGames = if (activeDecade == null) jukebox.games else {
                            jukebox.games.filter { g -> g.year > 0 && (g.year / 10) * 10 == activeDecade }
                        }
                        items(shownGames, key = { "game:${it.id}" }) { game ->
                            JukeboxGameRow(
                                game = game,
                                language = language,
                                packDirectory = packDirectory,
                                artworkName = extraCatalog.jukeboxGameArtwork[game.id],
                            ) { onJukeboxGame(game.id) }
                        }
                    } else {
                        val activeGenre = selectedGenreId?.let { jukebox.genre(it) }
                        if (activeGenre == null) {
                            val genrePairs = allGenres.chunked(2)
                            items(genrePairs, key = { pair -> "genre-row:${pair.joinToString("-") { it.id }}" }) { rowGenres ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    rowGenres.forEach { genre ->
                                        val count = remember(jukebox, genre.id) { jukebox.trackCountByGenre(genre.id) }
                                        MkwCard(
                                            onClick = { selectedGenreId = genre.id },
                                            tone = MkwSurfaceTone.Porcelain,
                                            contentPadding = PaddingValues(14.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(genre.tint),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = genre.icon.imageVector,
                                                    contentDescription = null,
                                                    tint = genre.color,
                                                    modifier = Modifier.size(21.dp),
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = genre.displayName(language),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MkwKitTokens.Palette.ink,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = pluralStringResource(R.plurals.jukebox_tracks_count, count, count),
                                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                                                    color = genre.color,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                    if (rowGenres.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            // Horizontal quick-switch genre strip
                            item(key = "jukebox-genre-filter-strip") {
                                LazyRow(
                                    modifier = Modifier.unpadHorizontal(16.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item(key = "genre-pill-all") {
                                        MkwFilterPill(
                                            label = stringResource(R.string.jukebox_all_genres),
                                            selected = false,
                                            onClick = { selectedGenreId = null },
                                            role = Role.RadioButton,
                                        )
                                    }
                                    items(allGenres, key = { "genre-pill:${it.id}" }) { genre ->
                                        MkwFilterPill(
                                            label = genre.displayName(language),
                                            selected = genre.id == activeGenre.id,
                                            onClick = { selectedGenreId = genre.id },
                                            leadingIcon = genre.icon.imageVector,
                                            role = Role.RadioButton,
                                        )
                                    }
                                }
                            }

                            val baseGenreTracks = jukebox.itemsWithGamesByGenre(activeGenre.id)
                            val genreTracks = when (sortOrder) {
                                TrackSortOrder.NAME_ASC -> baseGenreTracks.sortedBy { it.first.displayTitle(language).lowercase() }
                                TrackSortOrder.DURATION_DESC -> baseGenreTracks.sortedByDescending { it.first.durationMs }
                                TrackSortOrder.MOST_PLAYED -> baseGenreTracks.sortedByDescending {
                                    playStats[PlaylistItemRef.Jukebox(it.first.key).raw]?.playCount ?: 0
                                }
                                else -> baseGenreTracks
                            }

                            // Genre Hero Header Card
                            item(key = "genre-hero:${activeGenre.id}") {
                                MkwCard(
                                    tone = MkwSurfaceTone.Porcelain,
                                    contentPadding = PaddingValues(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(activeGenre.color),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = activeGenre.icon.imageVector,
                                                    contentDescription = null,
                                                    tint = MkwKitTokens.Palette.ground,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = activeGenre.displayName(language),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MkwKitTokens.Palette.ink,
                                                )
                                                Text(
                                                    text = pluralStringResource(R.plurals.jukebox_tracks_count, genreTracks.size, genreTracks.size),
                                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                                                    color = activeGenre.color,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            MkwRoseButton(
                                                text = stringResource(R.string.jukebox_play_all),
                                                onClick = { onPlayJukeboxGenre(activeGenre, 0, false) },
                                                leadingIcon = Icons.Filled.PlayArrow,
                                                size = MkwButtonSize.Compact,
                                                modifier = Modifier.weight(1f),
                                            )
                                            MkwSecondaryButton(
                                                text = stringResource(R.string.jukebox_shuffle),
                                                onClick = { onPlayJukeboxGenre(activeGenre, 0, true) },
                                                leadingIcon = Icons.Filled.Shuffle,
                                                size = MkwButtonSize.Compact,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }

                            itemsIndexed(genreTracks, key = { _, pair -> "genre-track:${activeGenre.id}:${pair.first.key}" }) { idx, (item, game) ->
                                val selKey = "jb:${item.key}"
                                val refRaw = PlaylistItemRef.Jukebox(item.key).raw
                                val plays = playStats[refRaw]?.playCount ?: 0
                                val isFav = refRaw in favorites
                                val art = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
                                val gameTitle = game.localizedName(language)
                                val playAction: () -> Unit = { onPlayJukeboxGenre(activeGenre, idx, false) }
                                val spec = TrackMenuSpec(
                                    selectionKey = selKey,
                                    playlistRef = refRaw,
                                    title = item.displayTitle(language),
                                    subtitle = listOfNotNull(item.displayArrangement(language), gameTitle).joinToString(" · "),
                                    artworkName = art,
                                    favoriteId = refRaw,
                                    infoPairs = buildList {
                                        add(R.string.track_info_game to gameTitle)
                                        if (item.durationMs > 0L) add(R.string.track_info_duration to item.durationFormatted)
                                        item.ostIndex?.let { add(R.string.track_info_ost to "#$it") }
                                        add(R.string.track_info_ref to item.key)
                                    },
                                    onPlayNow = playAction,
                                    onPlayNextDirect = { onPlayJukeboxNext(item, game) },
                                )
                                com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                                    enabled = !multiSelectMode,
                                    onSwipeStartToEnd = if (allowPlayNext) {
                                        {
                                            onPlayJukeboxNext(item, game)
                                            scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                        }
                                    } else null,
                                    onSwipeEndToStart = { onToggleFavorite(refRaw) },
                                    endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                                ) {
                                    JukeboxTrackRow(
                                        item = item,
                                        language = language,
                                        subtitleExtra = gameTitle,
                                        playCount = plays,
                                        packDirectory = packDirectory,
                                        playing = activeTrackKey == item.key,
                                        allowPlayNext = allowPlayNext,
                                        selectionMode = multiSelectMode,
                                        selected = selKey in selectedSet,
                                        onLongClick = {
                                            if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                        },
                                        onOpenMenu = { menuTarget = spec },
                                        onPlay = {
                                            if (multiSelectMode) toggleSelected(selKey) else playAction()
                                        },
                                        onPlayNext = {
                                            onPlayJukeboxNext(item, game)
                                            scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                        },
                                        onPlayFromHere = playAction,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (jukeboxSearch.games.isNotEmpty()) {
                        item(key = "jb-search-games-h") {
                            Text(stringResource(R.string.jukebox_games_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(jukeboxSearch.games, key = { "game-s:${it.id}" }) { game ->
                            JukeboxGameRow(
                                game = game,
                                language = language,
                                packDirectory = packDirectory,
                                artworkName = extraCatalog.jukeboxGameArtwork[game.id],
                            ) { onJukeboxGame(game.id) }
                        }
                    }
                    if (jukeboxSearch.trackHits.isNotEmpty()) {
                        item(key = "jb-search-tracks-h") {
                            Text(stringResource(R.string.jukebox_tracks_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(jukeboxSearch.trackHits, key = { "track-s:${it.second.key}" }) { (game, item) ->
                            val idx = game.items.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
                            val selKey = "jb:${item.key}"
                            val refRaw = PlaylistItemRef.Jukebox(item.key).raw
                            val plays = playStats[refRaw]?.playCount ?: 0
                            val isFav = refRaw in favorites
                            val art = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
                            val gameTitle = game.localizedName(language)
                            val playAction: () -> Unit = { onPlayJukeboxTrack(game, idx) }
                            val spec = TrackMenuSpec(
                                selectionKey = selKey,
                                playlistRef = refRaw,
                                title = item.displayTitle(language),
                                subtitle = listOfNotNull(item.displayArrangement(language), gameTitle).joinToString(" · "),
                                artworkName = art,
                                favoriteId = refRaw,
                                infoPairs = buildList {
                                    add(R.string.track_info_game to gameTitle)
                                    if (item.durationMs > 0L) add(R.string.track_info_duration to item.durationFormatted)
                                    item.ostIndex?.let { add(R.string.track_info_ost to "#$it") }
                                    add(R.string.track_info_ref to item.key)
                                },
                                onPlayNow = playAction,
                                onPlayNextDirect = { onPlayJukeboxNext(item, game) },
                            )
                            com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                                enabled = !multiSelectMode,
                                onSwipeStartToEnd = if (allowPlayNext) {
                                    {
                                        onPlayJukeboxNext(item, game)
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                } else null,
                                onSwipeEndToStart = { onToggleFavorite(refRaw) },
                                endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                            ) {
                                JukeboxTrackRow(
                                    item = item,
                                    language = language,
                                    subtitleExtra = gameTitle,
                                    playCount = plays,
                                    packDirectory = packDirectory,
                                    playing = activeTrackKey == item.key,
                                    allowPlayNext = allowPlayNext,
                                    selectionMode = multiSelectMode,
                                    selected = selKey in selectedSet,
                                    onLongClick = {
                                        if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                    },
                                    onOpenMenu = { menuTarget = spec },
                                    onPlay = {
                                        if (multiSelectMode) toggleSelected(selKey) else playAction()
                                    },
                                    onPlayNext = {
                                        onPlayJukeboxNext(item, game)
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    },
                                    onPlayFromHere = playAction,
                                )
                            }
                        }
                    }
                }
            }
            if (category < 0 && query.isNotBlank()) {
                if (jukeboxSearch.games.isNotEmpty()) {
                    item(key = "root-jb-games-h") {
                        Text(stringResource(R.string.jukebox_games_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(jukeboxSearch.games.take(6), key = { "root-game:${it.id}" }) { game ->
                        JukeboxGameRow(
                            game = game,
                            language = language,
                            packDirectory = packDirectory,
                            artworkName = extraCatalog.jukeboxGameArtwork[game.id],
                        ) { onJukeboxGame(game.id) }
                    }
                }
                if (jukeboxSearch.trackHits.isNotEmpty()) {
                    item(key = "root-jb-tracks-h") {
                        Text(stringResource(R.string.jukebox_tracks_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(jukeboxSearch.trackHits.take(15), key = { "root-track:${it.second.key}" }) { (game, item) ->
                        val idx = game.items.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
                        val selKey = "jb:${item.key}"
                        val refRaw = PlaylistItemRef.Jukebox(item.key).raw
                        val plays = playStats[refRaw]?.playCount ?: 0
                        val isFav = refRaw in favorites
                        val art = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
                        val gameTitle = game.localizedName(language)
                        val playAction: () -> Unit = { onPlayJukeboxTrack(game, idx) }
                        val spec = TrackMenuSpec(
                            selectionKey = selKey,
                            playlistRef = refRaw,
                            title = item.localizedTitle(language),
                            subtitle = listOfNotNull(item.localizedArrangement(language), gameTitle).joinToString(" · "),
                            artworkName = art,
                            favoriteId = refRaw,
                            infoPairs = buildList {
                                add(R.string.track_info_game to gameTitle)
                                if (item.durationMs > 0L) add(R.string.track_info_duration to item.durationFormatted)
                                item.ostIndex?.let { add(R.string.track_info_ost to "#$it") }
                                add(R.string.track_info_ref to item.key)
                            },
                            onPlayNow = playAction,
                            onPlayNextDirect = { onPlayJukeboxNext(item, game) },
                        )
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode,
                            onSwipeStartToEnd = if (allowPlayNext) {
                                {
                                    onPlayJukeboxNext(item, game)
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                }
                            } else null,
                            onSwipeEndToStart = { onToggleFavorite(refRaw) },
                            endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                        ) {
                            JukeboxTrackRow(
                                item = item,
                                language = language,
                                subtitleExtra = gameTitle,
                                playCount = plays,
                                packDirectory = packDirectory,
                                playing = activeTrackKey == item.key,
                                allowPlayNext = allowPlayNext,
                                selectionMode = multiSelectMode,
                                selected = selKey in selectedSet,
                                onLongClick = {
                                    if (multiSelectMode) toggleSelected(selKey) else menuTarget = spec
                                },
                                onOpenMenu = { menuTarget = spec },
                                onPlay = {
                                    if (multiSelectMode) toggleSelected(selKey) else playAction()
                                },
                                onPlayNext = {
                                    onPlayJukeboxNext(item, game)
                                    scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                },
                                onPlayFromHere = playAction,
                            )
                        }
                    }
                }
            }
            val emptyHere = when (category) {
                0 -> courses.isEmpty() && sfcCourses.isEmpty()
                1 -> battleTracks.isEmpty()
                2 -> if (query.isBlank()) jukebox.isEmpty else (jukeboxSearch.games.isEmpty() && jukeboxSearch.trackHits.isEmpty())
                3 -> otherItems.isEmpty()
                4 -> shortItems.isEmpty()
                5 -> nmItems.isEmpty()
                else -> category >= 0 && routes.isEmpty() && courses.isEmpty()
            }
            if (emptyHere) {
                item {
                    Text(
                        stringResource(if (category == 2 && query.isNotBlank()) R.string.jukebox_search_empty else R.string.discovery_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (category < 0 && query.isNotBlank() && routes.isEmpty() && courses.isEmpty() &&
                sfcCourses.isEmpty() && battleTracks.isEmpty() && otherItems.isEmpty() &&
                shortItems.isEmpty() && nmItems.isEmpty() && jukeboxSearch.games.isEmpty() && jukeboxSearch.trackHits.isEmpty()
            ) item {
                Text(stringResource(R.string.discovery_search_empty), Modifier.padding(vertical = 16.dp))
            }
            }
            AnimatedVisibility(visible = multiSelectMode) {
                val chosenRefs = selectedKeys.mapNotNull { visibleRefByKey[it] }
                MultiSelectActionBar(
                    hasSelection = chosenRefs.isNotEmpty(),
                    allowPlayNext = allowPlayNext,
                    onPlaySelected = {
                        if (chosenRefs.isNotEmpty() && onPlaySelectedRefs(chosenRefs)) {
                            multiSelectMode = false
                            selectedKeys = emptyList()
                        }
                    },
                    onPlayNextSelected = {
                        if (chosenRefs.isNotEmpty() && onPlayNextRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        }
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                    onAddToQueueSelected = {
                        if (chosenRefs.isNotEmpty() && onAddToQueueRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                        }
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                    onAddToPlaylistSelected = {
                        if (chosenRefs.isNotEmpty()) playlistPickerRefs = chosenRefs
                    },
                )
            }
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(0.dp)),
        )
    }
}

@Composable
private fun CourseMosaicBox(
    route: OfficialRoute,
    packDirectory: java.io.File?,
    courseArtwork: Map<String, String>,
    modifier: Modifier = Modifier,
    badgeSize: androidx.compose.ui.unit.Dp = 36.dp,
    badgePadding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val emblem = com.mylittleblover.mkwplayer.ui.art.CupArt.routeEmblem(route.id)
    val mosaicCodes = route.stops.take(4).map { it.stageCode }
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (rowIdx in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (colIdx in 0..1) {
                        val tileCode = mosaicCodes.getOrNull(rowIdx * 2 + colIdx)
                        if (tileCode != null) {
                            com.mylittleblover.mkwplayer.ui.art.CourseCover(
                                code = tileCode,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                showIcon = false,
                                packDirectory = packDirectory,
                                artworkName = courseArtwork[tileCode] ?: courseArtwork[tileCode.removeSuffix("_00")],
                            )
                        } else {
                            Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                }
            }
        }
        emblem?.let {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(badgePadding).size(badgeSize),
            ) {
                ResourceArtwork(it, Modifier.fillMaxSize().padding(badgePadding / 2))
            }
        }
    }
}

@Composable
private fun CupMosaicCard(
    route: OfficialRoute,
    language: String,
    packDirectory: java.io.File? = null,
    courseArtwork: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    MkwCard(
        onClick = onClick,
        tone = MkwSurfaceTone.Porcelain,
        contentPadding = PaddingValues(10.dp),
        modifier = modifier,
    ) {
        CourseMosaicBox(
            route = route,
            packDirectory = packDirectory,
            courseArtwork = courseArtwork,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(MkwKitTokens.Shape.thumb),
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                route.displayName(language),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.ink,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.map_route_stops, route.stops.size),
                style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                color = MkwKitTokens.Palette.inkSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RouteRow(
    route: OfficialRoute,
    catalog: BrowseCatalog?,
    language: String,
    packDirectory: java.io.File? = null,
    courseArtwork: Map<String, String> = emptyMap(),
    onClick: () -> Unit,
) {
    MkwCard(
        onClick = onClick,
        tone = MkwSurfaceTone.Porcelain,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CourseMosaicBox(
                route = route,
                packDirectory = packDirectory,
                courseArtwork = courseArtwork,
                modifier = Modifier.size(64.dp).clip(MkwKitTokens.Shape.thumb),
                badgeSize = 24.dp,
                badgePadding = 3.dp,
            )
            val stopsText = stringResource(R.string.map_route_stops, route.stops.size)
            val subtitle = if (route.kind == OfficialRouteKind.RALLY && route.stops.size >= 2) {
                val startName = catalog?.displayName(route.stops.first().stageCode, language) ?: route.stops.first().stageCode
                val endName = catalog?.displayName(route.stops.last().stageCode, language) ?: route.stops.last().stageCode
                "$startName → $endName · $stopsText"
            } else {
                stopsText
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(route.displayName(language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun DiscoveryCup(
    route: OfficialRoute?,
    catalog: BrowseCatalog?,
    language: String,
    installedJukebox: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game>> = emptyList(),
    packDirectory: java.io.File? = null,
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    inspectOfficialRoute: (OfficialRoute, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode) -> RoadJukeboxCatalog.RouteValidationReport? = { _, _, _ -> null },
    resolveStopPresetRef: (stageCode: String, stopIndex: Int, stopCount: Int, suiteMode: RoadJukeboxCatalog.RouteSuiteMode) -> String = { code, idx, count, mode ->
        val kind = route?.kind ?: com.mylittleblover.mkwplayer.routes.OfficialRouteKind.GRAND_PRIX
        PresetRefs.forSuiteVariant(RoadJukeboxCatalog.resolveSuiteVariantForStop(kind, mode, idx, count, code))
    },
    pureFreeRoamEntry: com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry =
        com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry(),
    onPureFreeRoamConfigChange: (RoadJukeboxCatalog.PureFreeRoamConfig) -> Unit = {},
    onPureFreeRoamEdgeOverrideChange: (Int, RoadJukeboxCatalog.EdgeFreeRoamOverride?) -> Unit = { _, _ -> },
    onPlayRoute: (OfficialRoute, Boolean, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode, RoadJukeboxCatalog.RouteSuiteMode) -> Unit = { _, _, _, _, _ -> },
    onPlayRouteWithFreeRoam: ((OfficialRoute, Boolean, Map<Int, String>, RoadJukeboxCatalog.RoadMusicRuleMode, RoadJukeboxCatalog.RouteSuiteMode, RoadJukeboxCatalog.PureFreeRoamConfig, Map<Int, RoadJukeboxCatalog.EdgeFreeRoamOverride>) -> Unit)? = null,
    onMap: (String) -> Unit,
    onCourse: (String) -> Unit,
) {
    var loopRoute by rememberSaveable(route?.id) { mutableStateOf(false) }
    var ruleMode by rememberSaveable(route?.id) { mutableStateOf(RoadJukeboxCatalog.RoadMusicRuleMode.AUTO) }
    var suiteMode by rememberSaveable(route?.id) { mutableStateOf(RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE) }
    val sessionOverrides = remember(route?.id) { mutableStateMapOf<Int, String>() }
    var pickingLegIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedFreeRoamLeg by rememberSaveable(route?.id) { mutableStateOf<Int?>(null) }
    val pfrConfig = pureFreeRoamEntry.config
    val useGameFreeRoamPool = pfrConfig.enabled &&
        pfrConfig.trackPool == RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM
    val report = remember(
        route,
        sessionOverrides.toMap(),
        ruleMode,
        pfrConfig.enabled,
        pfrConfig.trackPool,
        packDirectory,
        installedJukebox,
    ) {
        route?.let { inspectOfficialRoute(it, sessionOverrides.toMap(), ruleMode) }
    }
    val openingItem = remember(route, extraCatalog) {
        val r = route ?: return@remember null
        val openingId = ExtraMusicCatalog.openingIdForOfficialRoute(r.id, r.kind) ?: return@remember null
        extraCatalog.otherMusic.filterIsInstance<ExtraMusicCatalog.OtherMusicItem.OverlaidOpening>()
            .firstOrNull { it.id == openingId }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (route == null) item { Text(stringResource(R.string.discovery_empty)) }
        else {
            item {
                MkwCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val emblem = com.mylittleblover.mkwplayer.ui.art.CupArt.routeEmblem(route.id)
                            if (emblem != null) ResourceArtwork(emblem, Modifier.size(64.dp))
                            else Icon(Icons.Outlined.GraphicEq, null, Modifier.size(48.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(route.displayName(language), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.map_route_stops, route.stops.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!pfrConfig.enabled && report?.canPlay == false) {
                            Text(
                                com.mylittleblover.mkwplayer.ui.routes.localizedRouteBlockingReason(
                                    report,
                                    R.string.route_blocked_incomplete_pool,
                                ) { code -> catalog?.displayName(code, language) ?: code },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MkwPrimaryButton(
                                text = stringResource(R.string.route_play),
                                leadingIcon = Icons.Filled.PlayArrow,
                                onClick = {
                                    if (onPlayRouteWithFreeRoam != null) {
                                        onPlayRouteWithFreeRoam(
                                            route,
                                            loopRoute,
                                            sessionOverrides.toMap(),
                                            ruleMode,
                                            suiteMode,
                                            pfrConfig,
                                            pureFreeRoamEntry.edgeOverrides,
                                        )
                                    } else {
                                        onPlayRoute(route, loopRoute, sessionOverrides.toMap(), ruleMode, suiteMode)
                                    }
                                },
                                enabled = pfrConfig.enabled || report?.canPlay != false,
                                modifier = Modifier.weight(1f),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.discovery_view_map),
                                leadingIcon = Icons.Filled.Map,
                                onClick = { onMap(route.id) },
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { loopRoute = !loopRoute },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.route_loop_whole),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                            )
                            MkwSwitch(checked = loopRoute, onCheckedChange = { loopRoute = it })
                        }
                        com.mylittleblover.mkwplayer.ui.routes.PureFreeRoamConfigCard(
                            config = pfrConfig,
                            onConfigChange = onPureFreeRoamConfigChange,
                            compact = true,
                        )
                        val cupReducedMotion = rememberReducedMotion()
                        AnimatedVisibility(
                            visible = !useGameFreeRoamPool,
                            enter = MkwMotion.panelEnter(cupReducedMotion),
                            exit = MkwMotion.panelExit(cupReducedMotion),
                        ) {
                            RoadMusicRuleModeSelector(
                                selectedMode = ruleMode,
                                officialKind = route.kind,
                                onSelectMode = {
                                    ruleMode = it
                                    sessionOverrides.clear()
                                },
                            )
                        }
                        AnimatedVisibility(
                            visible = !pfrConfig.enabled,
                            enter = MkwMotion.panelEnter(cupReducedMotion),
                            exit = MkwMotion.panelExit(cupReducedMotion),
                        ) {
                            com.mylittleblover.mkwplayer.ui.routes.RouteSuiteModeSelector(
                                selectedMode = suiteMode,
                                onSelectMode = { suiteMode = it },
                            )
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.discovery_course_order), style = MaterialTheme.typography.titleLarge) }
            if (openingItem != null && !pfrConfig.enabled) {
                item(key = "opening:${openingItem.id}") {
                    MkwCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            PackArtwork.Thumbnail(
                                packDirectory,
                                listOf(openingItem.artwork),
                                Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                                    .background(MkwKitTokens.Palette.inset),
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MkwBadge(
                                    text = stringResource(R.string.route_opening_badge),
                                    tone = MkwBadgeTone.Crimson,
                                )
                                Text(
                                    openingItem.localizedName(language),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (openingItem.durationMs > 0L) {
                                    Text(
                                        formatExtraDuration(openingItem.durationMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(route.stops, key = { _, stop -> stop.id }) { idx, stop ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MkwCard(
                        onClick = { onCourse(stop.stageCode) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(stop.ordinal.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.crimson)
                            StageArt.resolve(stop.stageCode)?.let { ResourceArtwork(it, Modifier.size(52.dp)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(catalog?.displayName(stop.stageCode, language) ?: stop.stageCode, style = MaterialTheme.typography.titleMedium)
                                if (!pfrConfig.enabled) {
                                    val stopPresetRef = resolveStopPresetRef(stop.stageCode, idx, route.stops.size, suiteMode)
                                    Text(
                                        stringResource(com.mylittleblover.mkwplayer.ui.routes.builtInSuiteLabelRes(stopPresetRef)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MkwKitTokens.Palette.crimson,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    val leg = report?.legs?.getOrNull(idx)
                    if (leg != null) {
                        val fromTitle = catalog?.displayName(leg.from, language) ?: leg.from
                        val toTitle = catalog?.displayName(leg.to, language) ?: leg.to
                        val summary = if (useGameFreeRoamPool) {
                            pureFreeRoamGamePoolLegSummary(pfrConfig.freeRoamMode)
                        } else {
                            edgeResolutionSummary(leg.resolution, language)
                        }
                        val showLegError = !useGameFreeRoamPool && leg.resolution.isMissingOrIncomplete()
                        MkwCard(
                            onClick = {
                                if (useGameFreeRoamPool) {
                                    expandedFreeRoamLeg = if (expandedFreeRoamLeg == idx) null else idx
                                } else {
                                    pickingLegIndex = idx
                                }
                            },
                            tone = MkwSurfaceTone.Recessed,
                            shape = MkwKitTokens.Shape.control,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp), tint = MkwKitTokens.Palette.crimson)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${stringResource(R.string.route_road_prefix)} $fromTitle → $toTitle",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (showLegError) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (pfrConfig.enabled) {
                                    com.mylittleblover.mkwplayer.ui.routes.PureFreeRoamLegOverrideChip(
                                        config = pfrConfig,
                                        override = pureFreeRoamEntry.edgeOverrides[idx],
                                        expanded = expandedFreeRoamLeg == idx,
                                        onClick = {
                                            expandedFreeRoamLeg = if (expandedFreeRoamLeg == idx) null else idx
                                        },
                                    )
                                } else if (leg.resolution.hasKnownPool()) {
                                    Icon(Icons.Filled.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (pfrConfig.enabled) {
                                com.mylittleblover.mkwplayer.ui.routes.PureFreeRoamLegInlineEditor(
                                    visible = expandedFreeRoamLeg == idx,
                                    config = pfrConfig,
                                    override = pureFreeRoamEntry.edgeOverrides[idx],
                                    onOverrideChange = { ov -> onPureFreeRoamEdgeOverrideChange(idx, ov) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    val pickIdx = pickingLegIndex
    val activeLeg = if (pickIdx != null) report?.legs?.getOrNull(pickIdx) else null
    if (activeLeg != null && pickIdx != null) {
        val fromTitle = catalog?.displayName(activeLeg.from, language) ?: activeLeg.from
        val toTitle = catalog?.displayName(activeLeg.to, language) ?: activeLeg.to
        RouteEdgeMusicPickerSheet(
            legTitle = "$fromTitle → $toTitle",
            leg = activeLeg,
            installedJukebox = installedJukebox,
            allowCustomSubstitute = false,
            allowMute = false,
            sessionHint = true,
            language = language,
            packDirectory = packDirectory,
            currentSelectedKey = sessionOverrides[pickIdx],
            currentMuted = false,
            onSelectAuto = { sessionOverrides.remove(pickIdx) },
            onSelectMuted = {},
            onSelectTrackKey = { trackKey -> sessionOverrides[pickIdx] = trackKey },
            onDismiss = { pickingLegIndex = null },
        )
    }
}

private data class SmartPlaylistEntry(
    val rawRef: String,
    val title: String,
    val subtitle: String,
    val artworkName: String?,
    val fallbackCourseCode: String?,
    val playCount: Int,
)

private fun canonicalStatIdForRef(rawId: String): String = when (val ref = PlaylistItemRef.parse(rawId)) {
    is PlaylistItemRef.Course -> ref.logicalCode
    else -> ref.raw
}

private fun resolveSmartPlaylistEntry(
    rawId: String,
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    extraCatalog: ExtraMusicCatalog.Catalog,
    jukeboxItemsByKey: Map<String, JukeboxCatalog.TrackItem>,
    gamesById: Map<String, JukeboxCatalog.Game>,
    visibleCourseSet: Set<String>,
    coursesLabel: String,
    sfcLabel: String,
    battleLabel: String,
    freeLabel: String,
    otherLabel: String,
    soundsLabel: String,
    nmLabel: String,
    playCount: Int,
    staffCreditsUnlocked: Boolean = false,
): SmartPlaylistEntry? {
    return when (val ref = PlaylistItemRef.parse(rawId)) {
        is PlaylistItemRef.Course -> {
            val logical = ref.logicalCode
            if (logical !in visibleCourseSet && rawId !in visibleCourseSet) null
            else SmartPlaylistEntry(
                rawRef = logical,
                title = catalog?.displayName(logical, language) ?: logical,
                subtitle = coursesLabel,
                artworkName = extraCatalog.courseArtwork[rawId] ?: extraCatalog.courseArtwork[logical],
                fallbackCourseCode = logical,
                playCount = playCount,
            )
        }
        is PlaylistItemRef.Jukebox -> {
            val item = jukeboxItemsByKey[ref.trackKey] ?: return null
            val gameName = gamesById[item.gameId]?.localizedName(language) ?: freeLabel
            SmartPlaylistEntry(
                rawRef = ref.raw,
                title = item.displayTitle(language),
                subtitle = gameName,
                artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
                    ?: extraCatalog.jukeboxGameArtwork[item.gameId],
                fallbackCourseCode = null,
                playCount = playCount,
            )
        }
        is PlaylistItemRef.SfcTrack -> {
            if (!ExtraMusicCatalog.isVisible(ref.trackId, unlocked, staffCreditsUnlocked)) null
            else {
                val track = extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId } ?: return null
                SmartPlaylistEntry(
                    rawRef = ref.raw,
                    title = track.localizedName(language),
                    subtitle = sfcLabel,
                    artworkName = track.artwork,
                    fallbackCourseCode = null,
                    playCount = playCount,
                )
            }
        }
        is PlaylistItemRef.BattleTrack -> {
            if (!ExtraMusicCatalog.isVisible(ref.trackId, unlocked, staffCreditsUnlocked)) null
            else {
                val track = extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId } ?: return null
                SmartPlaylistEntry(
                    rawRef = ref.raw,
                    title = track.localizedName(language),
                    subtitle = battleLabel,
                    artworkName = track.artwork,
                    fallbackCourseCode = null,
                    playCount = playCount,
                )
            }
        }
        is PlaylistItemRef.ExtraTrack -> {
            if (!ExtraMusicCatalog.isVisible(ref.extraId, unlocked, staffCreditsUnlocked)) null
            else {
                val other = extraCatalog.otherMusic.firstOrNull { it.id == ref.extraId }
                val simple = extraCatalog.shortSounds.firstOrNull { it.id == ref.extraId }
                    ?: extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == ref.extraId }
                val title = other?.localizedName(language) ?: simple?.localizedName(language) ?: return null
                val sub = when {
                    other != null -> otherLabel
                    extraCatalog.shortSounds.any { it.id == ref.extraId } -> soundsLabel
                    else -> nmLabel
                }
                SmartPlaylistEntry(
                    rawRef = ref.raw,
                    title = title,
                    subtitle = sub,
                    artworkName = other?.artwork ?: simple?.artwork,
                    fallbackCourseCode = null,
                    playCount = playCount,
                )
            }
        }
    }
}

@Composable
private fun SmartPlaylistCard(
    title: String,
    subtitle: String,
    emptyText: String,
    entries: List<SmartPlaylistEntry>,
    packDirectory: java.io.File?,
    onPlayRefs: (List<String>) -> Boolean,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    MkwCard(
        tone = MkwSurfaceTone.Porcelain,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = MkwKitTokens.Shape.thumb,
                color = MkwKitTokens.Palette.rose,
                modifier = Modifier.size(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val top = entries.firstOrNull()
                    if (top != null && (top.artworkName != null || top.fallbackCourseCode != null)) {
                        PackArtwork.Thumbnail(packDirectory, listOfNotNull(top.artworkName), Modifier.fillMaxSize()) {
                            if (top.fallbackCourseCode != null) CourseCover(top.fallbackCourseCode, Modifier.fillMaxSize())
                            else Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.crimsonDeep)
                        }
                    } else {
                        Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.crimsonDeep)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MkwKitTokens.Palette.ink,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entries.isNotEmpty()) {
                        MkwBadge(
                            text = pluralStringResource(R.plurals.tracks_count_plurals, entries.size, entries.size),
                            tone = MkwBadgeTone.Neutral,
                        )
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entries.isNotEmpty()) {
                MkwIconButton(
                    icon = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    onClick = { expanded = !expanded },
                    visualSize = 36.dp,
                    iconSize = 20.dp,
                )
            }
        }
        if (entries.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
        } else {
            val refs = remember(entries) { entries.map { it.rawRef } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MkwPrimaryButton(
                    text = stringResource(R.string.discovery_playlist_play_all),
                    onClick = { onPlayRefs(refs) },
                    leadingIcon = Icons.Filled.PlayArrow,
                    size = MkwButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                )
                MkwSecondaryButton(
                    text = stringResource(R.string.jukebox_shuffle),
                    onClick = { onPlayRefs(refs.shuffled()) },
                    leadingIcon = Icons.Filled.Shuffle,
                    size = MkwButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MkwDivider()
                    entries.take(10).forEachIndexed { idx, entry ->
                        MkwCard(
                            onClick = {
                                val rotated = listOf(entry.rawRef) + refs.filterIndexed { i, _ -> i != idx }
                                onPlayRefs(rotated)
                            },
                            tone = MkwSurfaceTone.White,
                            shape = MkwKitTokens.Shape.thumb,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    "${idx + 1}",
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                                    color = MkwKitTokens.Palette.crimson,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(20.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val meta = if (entry.playCount > 0) {
                                        "${entry.subtitle} · ${pluralStringResource(R.plurals.play_count_plurals, entry.playCount, entry.playCount)}"
                                    } else {
                                        entry.subtitle
                                    }
                                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MkwKitTokens.Palette.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryPlaylists(
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    state: DiscoveryViewModel.State,
    onCreate: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onCup: (String) -> Unit,
    jukeboxGames: List<JukeboxCatalog.Game> = emptyList(),
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    packDirectory: java.io.File? = null,
    onViewAll: (Int) -> Unit = {},
    onJukeboxGame: (String) -> Unit = {},
    onCustomRoute: (String) -> Unit = {},
    onPlanRouteOnMap: () -> Unit = {},
    inspectCustomRoute: (CustomRoute) -> RoadJukeboxCatalog.RouteValidationReport? = { null },
    playStats: Map<String, TrackPlayStat> = emptyMap(),
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
) {
    val rallies = catalog?.routes(OfficialRouteKind.RALLY, unlocked).orEmpty()
    val gpRoutes = catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked).orEmpty()
    val visibleCourseSet = remember(catalog, unlocked) { catalog?.courses(unlocked).orEmpty().toSet() }
    val gamesById = remember(jukeboxGames) { jukeboxGames.associateBy { it.id } }
    val jukeboxItemsByKey = remember(jukeboxGames) { jukeboxGames.flatMap { it.items }.associateBy { it.key } }
    val coursesCatLabel = stringResource(R.string.discovery_courses)
    val sfcCatLabel = stringResource(R.string.discovery_vs_sfc)
    val battleCatLabel = stringResource(R.string.discovery_battle)
    val freeCatLabel = stringResource(R.string.discovery_free)
    val otherCatLabel = stringResource(R.string.discovery_other)
    val soundsCatLabel = stringResource(R.string.discovery_sounds)
    val nmCatLabel = stringResource(R.string.discovery_nintendo_music)
    val staffCreditsUnlocked = LocalStaffCreditsUnlocked.current
    val mostPlayedEntries = remember(playStats, catalog, unlocked, staffCreditsUnlocked, language, extraCatalog, jukeboxGames) {
        playStats.values
            .asSequence()
            .filter { it.playCount > 0 }
            .sortedWith(compareByDescending<TrackPlayStat> { it.playCount }.thenByDescending { it.lastPlayedEpochMs })
            .mapNotNull { stat ->
                resolveSmartPlaylistEntry(
                    rawId = stat.trackId,
                    catalog = catalog,
                    unlocked = unlocked,
                    staffCreditsUnlocked = staffCreditsUnlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukeboxItemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesCatLabel,
                    sfcLabel = sfcCatLabel,
                    battleLabel = battleCatLabel,
                    freeLabel = freeCatLabel,
                    otherLabel = otherCatLabel,
                    soundsLabel = soundsCatLabel,
                    nmLabel = nmCatLabel,
                    playCount = stat.playCount,
                )
            }
            .distinctBy { it.rawRef }
            .take(30)
            .toList()
    }
    val favoritesMixEntries = remember(state.favorites, playStats, catalog, unlocked, staffCreditsUnlocked, language, extraCatalog, jukeboxGames) {
        state.favorites
            .mapNotNull { fav ->
                val plays = playStats[canonicalStatIdForRef(fav)]?.playCount ?: 0
                resolveSmartPlaylistEntry(
                    rawId = fav,
                    catalog = catalog,
                    unlocked = unlocked,
                    staffCreditsUnlocked = staffCreditsUnlocked,
                    language = language,
                    extraCatalog = extraCatalog,
                    jukeboxItemsByKey = jukeboxItemsByKey,
                    gamesById = gamesById,
                    visibleCourseSet = visibleCourseSet,
                    coursesLabel = coursesCatLabel,
                    sfcLabel = sfcCatLabel,
                    battleLabel = battleCatLabel,
                    freeLabel = freeCatLabel,
                    otherLabel = otherCatLabel,
                    soundsLabel = soundsCatLabel,
                    nmLabel = nmCatLabel,
                    playCount = plays,
                )
            }
            .distinctBy { it.rawRef }
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (creating) {
        MkwDialog(
            onDismissRequest = { creating = false },
            title = stringResource(R.string.discovery_create_playlist),
            confirmButton = {
                MkwPrimaryButton(
                    text = stringResource(R.string.discovery_create),
                    onClick = { onCreate(name); name = ""; creating = false },
                    enabled = name.isNotBlank(),
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(
                    text = stringResource(R.string.discovery_cancel),
                    onClick = { creating = false },
                )
            },
        ) {
            MkwTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                label = stringResource(R.string.discovery_playlist_name),
                supportingText = if (name.isBlank()) stringResource(R.string.discovery_create_name_hint) else null,
                singleLine = true,
            )
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "smart-playlists-hdr") {
            Text(stringResource(R.string.smart_playlists_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
        }
        item(key = "smart-most-played") {
            SmartPlaylistCard(
                title = stringResource(R.string.smart_most_played),
                subtitle = stringResource(R.string.smart_most_played_subtitle),
                emptyText = stringResource(R.string.smart_most_played_empty),
                entries = mostPlayedEntries,
                packDirectory = packDirectory,
                onPlayRefs = onPlaySelectedRefs,
            )
        }
        item(key = "smart-favorites-mix") {
            SmartPlaylistCard(
                title = stringResource(R.string.smart_favorites_mix),
                subtitle = stringResource(R.string.smart_favorites_mix_subtitle),
                emptyText = stringResource(R.string.smart_favorites_mix_empty),
                entries = favoritesMixEntries,
                packDirectory = packDirectory,
                onPlayRefs = onPlaySelectedRefs,
            )
        }
        item { MkwDivider() }
        item {
            PlaylistGroupHeader(
                title = stringResource(R.string.discovery_playlists_group_user),
                count = state.playlists.size + 1,
                onViewAll = { onViewAll(0) },
                action = {
                    MkwSecondaryButton(
                        text = stringResource(R.string.discovery_create_playlist),
                        onClick = { creating = true },
                        leadingIcon = Icons.Filled.Add,
                        size = MkwButtonSize.Compact,
                    )
                },
            )
        }
        if (state.playlistError != null) item { Text(stringResource(R.string.discovery_save_failed), color = MkwKitTokens.Palette.danger) }
        item {
            val firstFav = favoritesMixEntries.firstOrNull()
            LazyRow(
                modifier = Modifier.unpadHorizontal(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "playlist:${com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID}") {
                    MkwCard(
                        onClick = { onPlaylist(com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID) },
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.width(164.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp)
                                .clip(MkwKitTokens.Shape.thumb)
                                .background(MkwKitTokens.Palette.rose),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (firstFav != null && (firstFav.artworkName != null || firstFav.fallbackCourseCode != null)) {
                                if (firstFav.artworkName != null && packDirectory != null) {
                                    PackArtwork.Thumbnail(packDirectory, listOf(firstFav.artworkName), Modifier.fillMaxSize()) {
                                        val art = firstFav.fallbackCourseCode?.let { StageArt.resolve(it) }
                                        if (art != null) ResourceArtwork(art, Modifier.size(90.dp))
                                        else Icon(Icons.Filled.Favorite, null, Modifier.size(40.dp), tint = MkwKitTokens.Palette.crimson)
                                    }
                                } else {
                                    val art = firstFav.fallbackCourseCode?.let { StageArt.resolve(it) }
                                    if (art != null) ResourceArtwork(art, Modifier.size(90.dp))
                                    else Icon(Icons.Filled.Favorite, null, Modifier.size(40.dp), tint = MkwKitTokens.Palette.crimson)
                                }
                            } else {
                                Icon(Icons.Filled.Favorite, null, Modifier.size(42.dp), tint = MkwKitTokens.Palette.crimson)
                            }
                            Surface(
                                shape = CircleShape,
                                color = MkwKitTokens.Palette.ground.copy(alpha = 0.92f),
                                shadowElevation = 2.dp,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Favorite, null, Modifier.size(15.dp), tint = MkwKitTokens.Palette.crimson)
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.pinned_favorites_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MkwKitTokens.Palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MkwBadge(text = stringResource(R.string.pinned_favorites_badge), tone = MkwBadgeTone.Rose, tabular = false)
                            Text(
                                pluralStringResource(R.plurals.tracks_count_plurals, favoritesMixEntries.size, favoritesMixEntries.size),
                                style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                color = MkwKitTokens.Palette.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                items(state.playlists, key = { "playlist:${it.id}" }) { playlist ->
                        MkwCard(
                            onClick = { onPlaylist(playlist.id) },
                            tone = MkwSurfaceTone.Porcelain,
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier.width(164.dp),
                        ) {
                            val firstRef = playlist.courseIds.firstOrNull()?.let {
                                com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(it)
                            }
                            val courseCode = (firstRef as? com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course)?.logicalCode
                            val artworkName = when (firstRef) {
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course ->
                                    extraCatalog.courseArtwork[firstRef.raw] ?: extraCatalog.courseArtwork[firstRef.logicalCode]
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack ->
                                    extraCatalog.sfcCourses.firstOrNull { it.id == firstRef.trackId }?.artwork
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack ->
                                    extraCatalog.battleTracks.firstOrNull { it.id == firstRef.trackId }?.artwork
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack ->
                                    extraCatalog.otherMusic.firstOrNull { it.id == firstRef.extraId }?.artwork
                                        ?: extraCatalog.shortSounds.firstOrNull { it.id == firstRef.extraId }?.artwork
                                        ?: extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == firstRef.extraId }?.artwork
                                is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox, null -> null
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(112.dp)
                                    .clip(MkwKitTokens.Shape.thumb)
                                    .background(MkwKitTokens.Palette.rose),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (artworkName != null && packDirectory != null) {
                                    PackArtwork.Thumbnail(packDirectory, listOf(artworkName), Modifier.fillMaxSize()) {
                                        Icon(Icons.Outlined.MusicNote, null, Modifier.size(40.dp), tint = MkwKitTokens.Palette.crimsonDeep)
                                    }
                                } else {
                                    val art = courseCode?.let { StageArt.resolve(it) }
                                    if (art != null) ResourceArtwork(art, Modifier.size(90.dp))
                                    else Icon(Icons.Outlined.MusicNote, null, Modifier.size(40.dp), tint = MkwKitTokens.Palette.crimsonDeep)
                                }
                            }
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                pluralStringResource(R.plurals.tracks_count_plurals, playlist.courseIds.size, playlist.courseIds.size),
                                style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                    }
            }
        }

        item { MkwDivider() }
        item {
            PlaylistGroupHeader(
                title = stringResource(R.string.discovery_playlists_group_routes),
                count = state.customRoutes.size,
                onViewAll = { onViewAll(1) },
                action = {
                    MkwSecondaryButton(
                        text = stringResource(R.string.route_plan_on_map),
                        onClick = onPlanRouteOnMap,
                        leadingIcon = Icons.Filled.Map,
                        size = MkwButtonSize.Compact,
                    )
                },
            )
        }
        if (state.customRoutes.isEmpty()) {
            item {
                MkwInsetBox(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.discovery_no_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MkwKitTokens.Palette.inkSecondary,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        } else {
            item {
                LazyRow(
                    modifier = Modifier.unpadHorizontal(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.customRoutes, key = { "my-route:${it.id}" }) { customRoute ->
                        val report = inspectCustomRoute(customRoute)
                        val isInvalid = report?.canPlay == false
                        val firstCode = customRoute.stops.firstOrNull()?.stageCode?.removeSuffix("_00")
                        val artwork = firstCode?.let { extraCatalog.courseArtwork[it] ?: extraCatalog.courseArtwork["${it}_00"] }
                        MkwCard(
                            onClick = { onCustomRoute(customRoute.id) },
                            tone = MkwSurfaceTone.Porcelain,
                            error = isInvalid,
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier.width(164.dp),
                        ) {
                            PackArtwork.Thumbnail(
                                packDirectory,
                                listOfNotNull(artwork),
                                Modifier.fillMaxWidth().height(112.dp).clip(MkwKitTokens.Shape.thumb)
                                    .background(MkwKitTokens.Palette.rose),
                            ) {
                                if (firstCode != null) CourseCover(firstCode, Modifier.fillMaxSize())
                                else Icon(Icons.Filled.Map, null, Modifier.size(36.dp), tint = MkwKitTokens.Palette.crimson)
                            }
                            Text(customRoute.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (isInvalid) {
                                    com.mylittleblover.mkwplayer.ui.routes.localizedRouteBlockingReason(
                                        report,
                                        R.string.route_invalid_topology,
                                    ) { code -> catalog?.displayName(code, language) ?: code }
                                } else {
                                    pluralStringResource(R.plurals.route_stops_plurals, customRoute.stops.size, customRoute.stops.size)
                                },
                                style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                color = if (isInvalid) MkwKitTokens.Palette.danger else MkwKitTokens.Palette.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (gpRoutes.isNotEmpty() || rallies.isNotEmpty()) {
            item { MkwDivider() }
        }
        if (gpRoutes.isNotEmpty()) {
            item {
                PlaylistGroupHeader(
                    title = stringResource(R.string.discovery_gp),
                    count = gpRoutes.size,
                    onViewAll = { onViewAll(2) },
                )
            }
            item { OfficialRouteStrip(gpRoutes, language, packDirectory, extraCatalog.courseArtwork, onCup) }
        }
        if (rallies.isNotEmpty()) {
            item {
                PlaylistGroupHeader(
                    title = stringResource(R.string.discovery_rally),
                    count = rallies.size,
                    onViewAll = { onViewAll(2) },
                )
            }
            item { OfficialRouteStrip(rallies, language, packDirectory, extraCatalog.courseArtwork, onCup) }
        }

        if (jukeboxGames.isNotEmpty()) {
            item { MkwDivider() }
            item {
                PlaylistGroupHeader(
                    title = stringResource(R.string.discovery_playlists_group_jukebox),
                    count = jukeboxGames.size,
                    onViewAll = { onViewAll(3) },
                )
            }
            item {
                LazyRow(
                    modifier = Modifier.unpadHorizontal(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(jukeboxGames, key = { "jb-group:${it.id}" }) { game ->
                        MkwCard(
                            onClick = { onJukeboxGame(game.id) },
                            tone = MkwSurfaceTone.Porcelain,
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier.width(164.dp),
                        ) {
                            PackArtwork.Thumbnail(
                                packDirectory,
                                listOfNotNull(extraCatalog.jukeboxGameArtwork[game.id]),
                                Modifier.fillMaxWidth().height(112.dp).clip(MkwKitTokens.Shape.thumb)
                                    .background(MkwKitTokens.Palette.rose),
                            ) {
                                Text(
                                    game.yearLabel.ifBlank { "OST" },
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.titleMedium),
                                    fontWeight = FontWeight.Bold,
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                )
                            }
                            Text(game.localizedName(language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(pluralStringResource(R.plurals.tracks_count_plurals, game.items.size, game.items.size), style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall), color = MkwKitTokens.Palette.inkSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGroupHeader(
    title: String,
    count: Int,
    onViewAll: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MkwKitTokens.Palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (count > 0) {
                MkwGhostButton(
                    text = stringResource(R.string.discovery_view_all_count, count),
                    onClick = onViewAll,
                )
            }
            action?.invoke()
        }
    }
}

@Composable
internal fun DiscoveryAllPlaylists(
    initialSection: Int,
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    state: DiscoveryViewModel.State,
    jukeboxGames: List<JukeboxCatalog.Game>,
    extraCatalog: ExtraMusicCatalog.Catalog,
    packDirectory: java.io.File?,
    onCreate: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onCup: (String) -> Unit,
    onJukeboxGame: (String) -> Unit,
    onCreateRouteOnMap: () -> Unit = {},
    onCustomRoute: (String) -> Unit = {},
    inspectCustomRoute: (CustomRoute) -> RoadJukeboxCatalog.RouteValidationReport? = { null },
) {
    var section by rememberSaveable(initialSection) { mutableIntStateOf(initialSection.coerceIn(0, 3)) }
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val sectionTitles = listOf(
        stringResource(R.string.discovery_playlists_group_user),
        stringResource(R.string.discovery_playlists_group_routes),
        stringResource(R.string.discovery_playlists_group_rallies),
        stringResource(R.string.discovery_playlists_group_jukebox),
    )
    if (creating) {
        MkwDialog(
            onDismissRequest = { creating = false },
            title = stringResource(R.string.discovery_create_playlist),
            confirmButton = {
                MkwPrimaryButton(
                    text = stringResource(R.string.discovery_create),
                    onClick = { onCreate(newName); newName = ""; creating = false },
                    enabled = newName.isNotBlank(),
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(
                    text = stringResource(R.string.discovery_cancel),
                    onClick = { creating = false },
                )
            },
        ) {
            MkwTextField(
                value = newName,
                onValueChange = { newName = it.take(120) },
                label = stringResource(R.string.discovery_playlist_name),
                singleLine = true,
            )
        }
    }
    Column(Modifier.fillMaxSize()) {
        val options = sectionTitles.mapIndexed { idx, label ->
            com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(idx, label)
        }
        com.mylittleblover.mkwplayer.ui.interactive.SelectionDrawerRow(
            label = stringResource(R.string.discovery_playlist_category_label),
            selectedLabel = sectionTitles.getOrElse(section) { sectionTitles.first() },
            sheetTitle = stringResource(R.string.discovery_playlist_category_label),
            options = options,
            selectedValue = section,
            onSelect = { section = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            compact = true,
        )
        MkwTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.discovery_search_playlists),
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MkwKitTokens.Palette.inkSecondary) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    MkwIconButton(
                        icon = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.discovery_cancel),
                        onClick = { query = "" },
                        visualSize = 32.dp,
                        iconSize = 18.dp,
                    )
                }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val pinnedTitle = stringResource(R.string.pinned_favorites_title)
        val pinnedSubtitle = stringResource(R.string.pinned_favorites_badge)
        val staffCreditsUnlocked = LocalStaffCreditsUnlocked.current
        val visibleCourses = catalog?.courses(unlocked).orEmpty().toSet()
        val visibleFavCount = remember(state.favorites, visibleCourses, unlocked, staffCreditsUnlocked, jukeboxGames, extraCatalog) {
            val jbByKey = jukeboxGames.flatMap { it.items }.associateBy { it.key }
            state.favorites.count { raw ->
                when (val ref = com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(raw)) {
                    is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> ref.logicalCode in visibleCourses || raw in visibleCourses
                    is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox -> ref.trackKey in jbByKey
                    is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack -> ExtraMusicCatalog.isVisible(ref.trackId, unlocked, staffCreditsUnlocked)
                    is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack -> ExtraMusicCatalog.isVisible(ref.trackId, unlocked, staffCreditsUnlocked)
                    is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack -> ExtraMusicCatalog.isVisible(ref.extraId, unlocked, staffCreditsUnlocked)
                }
            }
        }
        val favCountText = pluralStringResource(R.plurals.tracks_count_plurals, visibleFavCount, visibleFavCount)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val trimmed = query.trim()
            when (section) {
                0 -> {
                    item {
                        MkwSecondaryButton(
                            text = stringResource(R.string.discovery_create_playlist),
                            onClick = { creating = true },
                            leadingIcon = Icons.Filled.Add,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val showPinned = trimmed.isEmpty() || pinnedTitle.contains(trimmed, ignoreCase = true)
                    if (showPinned) {
                        item(key = "all-pl:${com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID}") {
                            MkwCard(
                                onClick = { onPlaylist(com.mylittleblover.mkwplayer.library.PlaylistConstants.PINNED_FAVORITES_ID) },
                                tone = MkwSurfaceTone.Porcelain,
                                contentPadding = PaddingValues(12.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(MkwKitTokens.Shape.thumb)
                                            .background(MkwKitTokens.Palette.rose),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Favorite, null, Modifier.size(26.dp), tint = MkwKitTokens.Palette.crimson)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(pinnedTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            MkwBadge(text = pinnedSubtitle, tone = MkwBadgeTone.Rose, tabular = false)
                                        }
                                        Text(favCountText, style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall), color = MkwKitTokens.Palette.inkSecondary)
                                    }
                                }
                            }
                        }
                    }
                    val list = state.playlists.filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
                    if (list.isEmpty() && !showPinned) item { Text(stringResource(R.string.discovery_no_saved), color = MkwKitTokens.Palette.inkSecondary) }
                    items(list, key = { "all-pl:${it.id}" }) { playlist ->
                        val first = playlist.courseIds.firstOrNull { it in catalog?.courses(unlocked).orEmpty() }
                        ExtraSquareRow(
                            title = playlist.name,
                            subtitle = pluralStringResource(R.plurals.tracks_count_plurals, playlist.courseIds.size, playlist.courseIds.size),
                            packDirectory = packDirectory,
                            artworkName = first?.let { extraCatalog.courseArtwork[it] ?: extraCatalog.courseArtwork[it.removeSuffix("_00")] },
                            fallbackCourseCode = first,
                            onClick = { onPlaylist(playlist.id) },
                        )
                    }
                }
                1 -> {
                    item {
                        MkwSecondaryButton(
                            text = stringResource(R.string.route_plan_on_map),
                            onClick = onCreateRouteOnMap,
                            leadingIcon = Icons.Filled.Map,
                        )
                    }
                    val matchingRoutes = state.customRoutes.filter {
                        trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true)
                    }
                    if (matchingRoutes.isEmpty()) {
                        item { Text(stringResource(R.string.discovery_no_saved)) }
                    }
                    items(matchingRoutes, key = { "all-route:${it.id}" }) { customRoute ->
                        val report = inspectCustomRoute(customRoute)
                        val first = customRoute.stops.firstOrNull()?.stageCode?.removeSuffix("_00")
                        val legCount = (customRoute.stops.size - 1).coerceAtLeast(0)
                        val legsSummary = pluralStringResource(R.plurals.route_legs_plurals, legCount, legCount)
                        val stopsSummary = pluralStringResource(R.plurals.route_stops_plurals, customRoute.stops.size, customRoute.stops.size)
                        val sub = if (report?.canPlay == false) {
                            val blockMsg = com.mylittleblover.mkwplayer.ui.routes.localizedRouteBlockingReason(
                                report,
                                R.string.route_invalid_topology,
                            ) { code -> catalog?.displayName(code, language) ?: code }
                            "$stopsSummary · $legsSummary · $blockMsg"
                        } else if (customRoute.loopWholeRoute) {
                            "$stopsSummary · $legsSummary · ${stringResource(R.string.route_loop_whole)}"
                        } else {
                            "$stopsSummary · $legsSummary"
                        }
                        ExtraSquareRow(
                            title = customRoute.name,
                            subtitle = sub,
                            packDirectory = packDirectory,
                            artworkName = first?.let { extraCatalog.courseArtwork[it] ?: extraCatalog.courseArtwork["${it}_00"] },
                            fallbackCourseCode = first,
                            onClick = { onCustomRoute(customRoute.id) },
                        )
                    }
                }
                2 -> {
                    val gpList = catalog?.routes(OfficialRouteKind.GRAND_PRIX, unlocked).orEmpty()
                        .filter { trimmed.isEmpty() || it.displayName(language).contains(trimmed, ignoreCase = true) }
                    val rallyList = catalog?.routes(OfficialRouteKind.RALLY, unlocked).orEmpty()
                        .filter { trimmed.isEmpty() || it.displayName(language).contains(trimmed, ignoreCase = true) }
                    if (gpList.isNotEmpty()) {
                        item(key = "all-official-gp-hdr") {
                            Text(
                                text = stringResource(R.string.discovery_gp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.crimson,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }
                        items(gpList, key = { "all-gp:${it.id}" }) { route ->
                            RouteRow(route, catalog, language, packDirectory, extraCatalog.courseArtwork) { onCup(route.id) }
                        }
                    }
                    if (rallyList.isNotEmpty()) {
                        item(key = "all-official-rally-hdr") {
                            Text(
                                text = stringResource(R.string.discovery_rally),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.crimson,
                                modifier = Modifier.padding(top = if (gpList.isNotEmpty()) 12.dp else 4.dp, bottom = 2.dp),
                            )
                        }
                        items(rallyList, key = { "all-rally:${it.id}" }) { route ->
                            RouteRow(route, catalog, language, packDirectory, extraCatalog.courseArtwork) { onCup(route.id) }
                        }
                    }
                }
                3 -> {
                    val games = jukeboxGames.filter {
                        trimmed.isEmpty() || it.localizedName(language).contains(trimmed, ignoreCase = true) || it.yearLabel.contains(trimmed, ignoreCase = true)
                    }
                    items(games, key = { "all-jb:${it.id}" }) { game ->
                        JukeboxGameRow(
                            game = game,
                            language = language,
                            packDirectory = packDirectory,
                            artworkName = extraCatalog.jukeboxGameArtwork[game.id],
                        ) { onJukeboxGame(game.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialRouteStrip(
    routes: List<OfficialRoute>,
    language: String,
    packDirectory: java.io.File?,
    courseArtwork: Map<String, String>,
    onCup: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.unpadHorizontal(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(routes, key = { it.id }) { route ->
            CupMosaicCard(
                route = route,
                language = language,
                packDirectory = packDirectory,
                courseArtwork = courseArtwork,
                modifier = Modifier.width(164.dp),
                onClick = { onCup(route.id) },
            )
        }
    }
}

private data class ResolvedPlaylistRowDisplay(
    val ref: com.mylittleblover.mkwplayer.library.PlaylistItemRef,
    val available: Boolean,
    val title: String,
    val categorySubtitle: String,
    val artworkName: String?,
    val fallbackCourseCode: String? = null,
    val editorTargetCode: String? = null,
    val isCourse: Boolean = false,
)

private fun resolvePlaylistRowDisplay(
    rawId: String,
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    extraCatalog: ExtraMusicCatalog.Catalog,
    jukebox: JukeboxCatalog.Catalog,
    gamesById: Map<String, JukeboxCatalog.Game>,
    visibleCourseSet: Set<String>,
    courseArtwork: Map<String, String>,
    coursesCatLabel: String,
    sfcCatLabel: String,
    battleCatLabel: String,
    freeCatLabel: String,
    otherCatLabel: String,
    soundsCatLabel: String,
    nmCatLabel: String,
    staffCreditsUnlocked: Boolean = false,
): ResolvedPlaylistRowDisplay {
    return when (val ref = com.mylittleblover.mkwplayer.library.PlaylistItemRef.parse(rawId)) {
        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> {
            val logical = ref.logicalCode
            val ok = logical in visibleCourseSet || rawId in visibleCourseSet
            ResolvedPlaylistRowDisplay(
                ref = ref,
                available = ok,
                title = if (ok) catalog?.displayName(logical, language) ?: logical else "",
                categorySubtitle = coursesCatLabel,
                artworkName = courseArtwork[rawId] ?: courseArtwork[logical],
                fallbackCourseCode = logical,
                editorTargetCode = logical,
                isCourse = true,
            )
        }
        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox -> {
            val item = jukebox.itemsByKey[ref.trackKey]
            val gameName = item?.let { gamesById[it.gameId]?.localizedName(language) } ?: freeCatLabel
            ResolvedPlaylistRowDisplay(
                ref = ref,
                available = item != null,
                title = item?.displayTitle(language).orEmpty(),
                categorySubtitle = gameName,
                artworkName = item?.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
            )
        }
        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.SfcTrack -> {
            val ok = ExtraMusicCatalog.isVisible(ref.trackId, unlocked, staffCreditsUnlocked)
            val track = extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId }
            ResolvedPlaylistRowDisplay(
                ref = ref,
                available = ok && track != null,
                title = track?.localizedName(language).orEmpty(),
                categorySubtitle = sfcCatLabel,
                artworkName = track?.artwork,
                editorTargetCode = "extra:${ref.trackId}",
            )
        }
        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.BattleTrack -> {
            val ok = ExtraMusicCatalog.isVisible(ref.trackId, unlocked, staffCreditsUnlocked)
            val track = extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId }
            ResolvedPlaylistRowDisplay(
                ref = ref,
                available = ok && track != null,
                title = track?.localizedName(language).orEmpty(),
                categorySubtitle = battleCatLabel,
                artworkName = track?.artwork,
                editorTargetCode = "extra:${ref.trackId}",
            )
        }
        is com.mylittleblover.mkwplayer.library.PlaylistItemRef.ExtraTrack -> {
            val ok = ExtraMusicCatalog.isVisible(ref.extraId, unlocked, staffCreditsUnlocked)
            val other = extraCatalog.otherMusic.firstOrNull { it.id == ref.extraId }
            val simple = extraCatalog.shortSounds.firstOrNull { it.id == ref.extraId }
                ?: extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == ref.extraId }
            val sub = when {
                other != null -> otherCatLabel
                extraCatalog.shortSounds.any { it.id == ref.extraId } -> soundsCatLabel
                else -> nmCatLabel
            }
            ResolvedPlaylistRowDisplay(
                ref = ref,
                available = ok && (other != null || simple != null),
                title = other?.localizedName(language) ?: simple?.localizedName(language).orEmpty(),
                categorySubtitle = sub,
                artworkName = other?.artwork ?: simple?.artwork,
                editorTargetCode = if (other is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle) {
                    "extra:${other.id}"
                } else null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DiscoveryPlaylist(
    playlist: com.mylittleblover.mkwplayer.library.UserPlaylist?,
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    error: Boolean,
    onRemove: (Int) -> Unit,
    onRemoveMany: (Collection<Int>) -> Unit = {},
    onRename: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    onCourse: (String) -> Unit,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset> = emptyList(),
    packDirectory: java.io.File? = null,
    courseArtwork: Map<String, String> = emptyMap(),
    jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    isThisPlaylistPlaying: Boolean = false,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onPlayFrom: (Int) -> Unit = {},
    onToggleNativeDefault: (Boolean) -> Unit = {},
    onUpdateEntryPreset: (Int, String?) -> Unit = { _, _ -> },
    onUpdateEntryNative: (Int, Boolean?) -> Unit = { _, _ -> },
    onOpenFlowEditor: (String, String?) -> Unit = { _, _ -> },
    isPresetSelectable: (String, String?) -> Boolean = { _, ref -> ref != PresetRefs.SYSTEM_KNOCKOUT },
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
    allowPlayNext: Boolean = true,
    onPlayNextRefs: (List<String>) -> Boolean = { false },
    onAddToQueueRefs: (List<String>) -> Boolean = { false },
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
    playStats: Map<String, com.mylittleblover.mkwplayer.library.TrackPlayStat> = emptyMap(),
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editingEntryIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    val visibleCourses = catalog?.courses(unlocked).orEmpty()
    val visibleCourseSet = remember(visibleCourses) { visibleCourses.toSet() }
    val configSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val coursesCatLabel = stringResource(R.string.discovery_courses)
    val sfcCatLabel = stringResource(R.string.discovery_vs_sfc)
    val battleCatLabel = stringResource(R.string.discovery_battle)
    val freeCatLabel = stringResource(R.string.discovery_free)
    val otherCatLabel = stringResource(R.string.discovery_other)
    val soundsCatLabel = stringResource(R.string.discovery_sounds)
    val nmCatLabel = stringResource(R.string.discovery_nintendo_music)
    val addedNextText = stringResource(R.string.jukebox_added_next)
    val addedQueueText = stringResource(R.string.track_action_added_to_queue)
    val gamesById = remember(jukebox) { jukebox.games.associateBy { it.id } }

    var trackMenuTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var infoTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var playlistPickerRefs by remember { mutableStateOf<List<String>?>(null) }
    var multiSelectMode by rememberSaveable(playlist?.id) { mutableStateOf(false) }
    var selectedIndices by rememberSaveable(playlist?.id) { mutableStateOf(listOf<Int>()) }
    val selectedIndexSet = remember(selectedIndices) { selectedIndices.toSet() }

    androidx.activity.compose.BackHandler(enabled = multiSelectMode) {
        multiSelectMode = false
        selectedIndices = emptyList()
    }

    val staffCreditsUnlocked = LocalStaffCreditsUnlocked.current
    fun resolveEntry(rawId: String): ResolvedPlaylistRowDisplay = resolvePlaylistRowDisplay(
        rawId = rawId,
        catalog = catalog,
        unlocked = unlocked,
        language = language,
        extraCatalog = extraCatalog,
        jukebox = jukebox,
        gamesById = gamesById,
        visibleCourseSet = visibleCourseSet,
        courseArtwork = courseArtwork,
        coursesCatLabel = coursesCatLabel,
        sfcCatLabel = sfcCatLabel,
        battleCatLabel = battleCatLabel,
        freeCatLabel = freeCatLabel,
        otherCatLabel = otherCatLabel,
        soundsCatLabel = soundsCatLabel,
        nmCatLabel = nmCatLabel,
        staffCreditsUnlocked = staffCreditsUnlocked,
    )

    val entryIdx = editingEntryIndex
    if (playlist != null && entryIdx != null && entryIdx in playlist.courseIds.indices) {
        val rawCode = playlist.courseIds[entryIdx]
        val rowInfo = resolveEntry(rawCode)
        val editorTarget = rowInfo.editorTargetCode
        if (editorTarget != null) {
            val logicalCode = editorTarget
            val cfg = playlist.configAt(entryIdx)
            val itemTitle = rowInfo.title.ifBlank { rawCode }
            MkwBottomSheet(
                onDismissRequest = { editingEntryIndex = null },
                title = "$itemTitle · ${stringResource(R.string.discovery_playlist_entry_config)}",
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.discovery_playlist_entry_preset),
                        style = MaterialTheme.typography.labelLarge,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CoursePresetOptionsColumn(
                            logicalCode = logicalCode,
                            isCourse = rowInfo.isCourse,
                            selectedPresetId = cfg.presetId,
                            presets = presets,
                            isPresetSelectable = isPresetSelectable,
                            onSelectPreset = { onUpdateEntryPreset(entryIdx, it) },
                        )
                        Spacer(Modifier.height(4.dp))
                        MkwSecondaryButton(
                            text = stringResource(R.string.preset_new),
                            onClick = {
                                editingEntryIndex = null
                                onOpenFlowEditor(logicalCode, cfg.presetId)
                            },
                            leadingIcon = Icons.Filled.Edit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (rowInfo.isCourse) {
                        MkwDivider()
                        Text(
                            stringResource(R.string.discovery_playlist_native_transition),
                            style = MaterialTheme.typography.labelLarge,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                null to stringResource(R.string.discovery_playlist_entry_native_inherit),
                                true to stringResource(R.string.discovery_playlist_entry_native_on),
                                false to stringResource(R.string.discovery_playlist_entry_native_off),
                            ).forEach { (value, label) ->
                                val selected = cfg.nativeTransitionEnabled == value
                                MkwOptionRow(
                                    headline = label,
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onUpdateEntryNative(entryIdx, value) },
                                    trailing = { MkwRadioMark(selected = selected) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            editingEntryIndex = null
        }
    }

    if (renaming) MkwDialog(
        onDismissRequest = { renaming = false },
        title = stringResource(R.string.discovery_rename),
        confirmButton = {
            MkwPrimaryButton(
                text = stringResource(R.string.discovery_rename),
                onClick = { onRename(newName); renaming = false },
                enabled = newName.isNotBlank(),
                size = MkwButtonSize.Compact,
            )
        },
        dismissButton = {
            MkwGhostButton(text = stringResource(R.string.discovery_cancel), onClick = { renaming = false })
        },
    ) {
        MkwTextField(
            value = newName,
            onValueChange = { newName = it.take(120) },
            label = stringResource(R.string.discovery_playlist_name),
            supportingText = if (newName.isBlank()) stringResource(R.string.discovery_create_name_hint) else null,
            singleLine = true,
        )
    }
    if (deleting) MkwDialog(
        onDismissRequest = { deleting = false },
        title = stringResource(R.string.discovery_delete_playlist),
        confirmButton = {
            MkwDangerButton(
                text = stringResource(R.string.discovery_delete_playlist),
                onClick = { deleting = false; onDelete() },
                size = MkwButtonSize.Compact,
            )
        },
        dismissButton = {
            MkwGhostButton(text = stringResource(R.string.discovery_cancel), onClick = { deleting = false })
        },
    ) {
        Text(playlist?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MkwKitTokens.Palette.ink)
    }

    TrackActionOverlayHosts(
        menuTarget = trackMenuTarget,
        onDismissMenu = { trackMenuTarget = null },
        playlistPickerRefs = playlistPickerRefs,
        onOpenPlaylistPicker = { refs -> playlistPickerRefs = refs },
        onDismissPlaylistPicker = { playlistPickerRefs = null },
        infoTarget = infoTarget,
        onOpenInfo = { infoTarget = it },
        onDismissInfo = { infoTarget = null },
        packDirectory = packDirectory,
        allowPlayNext = allowPlayNext,
        playlists = playlists.filter { it.id != playlist?.id },
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        onOpenCourse = onCourse,
        onOpenFlowEditor = { editorCode -> onOpenFlowEditor(editorCode, null) },
        onEnterMultiSelect = { key ->
            val idx = key.toIntOrNull()
            multiSelectMode = true
            if (idx != null) selectedIndices = (selectedIndices + idx).distinct()
        },
        onPlayNextRefs = onPlayNextRefs,
        onAddToQueueRefs = onAddToQueueRefs,
        onAddToPlaylist = { pid, refs ->
            onAddToPlaylist(pid, refs)
            multiSelectMode = false
            selectedIndices = emptyList()
        },
        onCreatePlaylistAndAdd = { name, refs ->
            onCreatePlaylistAndAdd(name, refs)
            multiSelectMode = false
            selectedIndices = emptyList()
        },
        snackbarHostState = snackbarHostState,
        addedNextText = addedNextText,
        addedQueueText = addedQueueText,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = multiSelectMode && playlist != null) {
                val allIndices = playlist?.courseIds?.indices?.toList().orEmpty()
                val allSelected = allIndices.isNotEmpty() && selectedIndexSet.containsAll(allIndices)
                MultiSelectTopBanner(
                    selectedCount = selectedIndexSet.size,
                    allSelected = allSelected,
                    onToggleSelectAll = {
                        selectedIndices = if (allSelected) emptyList() else allIndices
                    },
                    onClose = {
                        multiSelectMode = false
                        selectedIndices = emptyList()
                    },
                )
            }
            val plListState = androidx.compose.foundation.lazy.rememberLazyListState()
            val latestPlaylist by rememberUpdatedState(playlist)
            val latestMove by rememberUpdatedState(onMove)
            val latestPlaying by rememberUpdatedState(isThisPlaylistPlaying)
            val latestError by rememberUpdatedState(error)
            val latestMultiSelect by rememberUpdatedState(multiSelectMode)
            val rowKeys = remember(playlist?.courseIds) {
                val ids = playlist?.courseIds.orEmpty()
                val seen = HashMap<String, Int>(ids.size)
                ids.map { raw ->
                    val n = (seen[raw] ?: 0) + 1
                    seen[raw] = n
                    "$raw#$n"
                }
            }
            val dragController = remember(plListState, playlist?.id) {
                com.mylittleblover.mkwplayer.ui.components.DragReorderController(
                    listState = plListState,
                    sectionStartIndex = {
                        val pl = latestPlaylist ?: return@DragReorderController 0
                        1 +
                            (if (latestPlaying) 1 else 0) +
                            (if (latestError) 1 else 0) +
                            (if (pl.courseIds.size > 1 && !latestMultiSelect) 1 else 0)
                    },
                    itemCountProvider = { latestPlaylist?.courseIds?.size ?: 0 },
                    onMove = { from, to -> latestMove(from, to) },
                )
            }
            LazyColumn(
                state = plListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (playlist == null) item { Text(stringResource(R.string.discovery_empty)) }
                else {
                    val resolvedEntries = playlist.courseIds.map(::resolveEntry)
                    val hasCourses = resolvedEntries.any { it.isCourse }
                    item(key = "pl-hero") {
                        val firstHero = resolvedEntries.firstOrNull { it.available }
                        MkwCard(
                            tone = MkwSurfaceTone.Porcelain,
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                PackArtwork.Thumbnail(
                                    packDirectory,
                                    listOfNotNull(firstHero?.artworkName),
                                    Modifier.size(72.dp).clip(MkwKitTokens.Shape.card)
                                        .background(MkwKitTokens.Palette.rose),
                                ) {
                                    val fallbackCourse = firstHero?.fallbackCourseCode
                                    val art = fallbackCourse?.let { StageArt.resolve(it) }
                                    if (art != null) ResourceArtwork(art, Modifier.size(56.dp))
                                    else Icon(Icons.Outlined.MusicNote, null, modifier = Modifier.size(32.dp), tint = MkwKitTokens.Palette.crimsonDeep)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                                    Text(
                                        pluralStringResource(R.plurals.tracks_count_plurals, playlist.courseIds.size, playlist.courseIds.size),
                                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                        color = MkwKitTokens.Palette.inkSecondary,
                                    )
                                }
                                Box {
                                    MkwIconButton(
                                        icon = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.jukebox_more_actions),
                                        onClick = { menuOpen = true },
                                    )
                                    MkwDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        MkwDropdownItem(
                                            text = stringResource(R.string.track_action_multi_select),
                                            leadingIcon = Icons.Outlined.CheckCircle,
                                            enabled = playlist.courseIds.isNotEmpty(),
                                            onClick = {
                                                menuOpen = false
                                                multiSelectMode = true
                                                selectedIndices = emptyList()
                                            },
                                        )
                                        MkwDropdownItem(
                                            text = stringResource(R.string.discovery_rename),
                                            leadingIcon = Icons.Filled.Edit,
                                            onClick = {
                                                menuOpen = false
                                                newName = playlist.name
                                                renaming = true
                                            },
                                        )
                                        MkwDropdownItem(
                                            text = stringResource(R.string.discovery_delete_playlist),
                                            leadingIcon = Icons.Filled.DeleteOutline,
                                            destructive = true,
                                            onClick = {
                                                menuOpen = false
                                                deleting = true
                                            },
                                        )
                                    }
                                }
                            }

                            val playableCount = resolvedEntries.count { it.available }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MkwPrimaryButton(
                                    text = stringResource(R.string.discovery_playlist_play_all),
                                    onClick = onPlayAll,
                                    enabled = playableCount > 0,
                                    leadingIcon = Icons.Filled.PlayArrow,
                                    modifier = Modifier.weight(1f),
                                )
                                MkwSecondaryButton(
                                    text = stringResource(R.string.jukebox_shuffle),
                                    onClick = onShuffle,
                                    enabled = playableCount > 0,
                                    leadingIcon = Icons.Filled.Shuffle,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            if (hasCourses) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.discovery_playlist_native_transition), style = MaterialTheme.typography.titleSmall, color = MkwKitTokens.Palette.ink)
                                        Text(
                                            stringResource(R.string.discovery_playlist_native_transition_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MkwKitTokens.Palette.inkSecondary,
                                        )
                                    }
                                    MkwSwitch(
                                        checked = playlist.nativeTransitionDefault,
                                        onCheckedChange = onToggleNativeDefault,
                                    )
                                }
                            }
                        }
                    }
                    if (isThisPlaylistPlaying) item(key = "pl-playing") {
                        MkwCard(
                            tone = MkwSurfaceTone.RoseTint,
                            selected = true,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.discovery_playlist_frozen_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.crimsonDeep,
                            )
                        }
                    }
                    if (error) item(key = "pl-error") { Text(stringResource(R.string.discovery_save_failed), color = MaterialTheme.colorScheme.error) }
                    if (playlist.courseIds.isEmpty()) item(key = "pl-empty") { Text(stringResource(R.string.discovery_playlist_tracks_empty)) }
                    else if (playlist.courseIds.size > 1 && !multiSelectMode) item(key = "pl-drag-hint") {
                        Text(
                            stringResource(R.string.playlist_drag_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(playlist.courseIds, key = { idx, _ -> rowKeys[idx] }) { index, rawId ->
                        val info = resolvedEntries[index]
                        val cfg = playlist.configAt(index)
                        val title = if (info.available) info.title.ifBlank { rawId } else stringResource(R.string.discovery_empty)
                        val editorTarget = info.editorTargetCode
                        val baseMetaLine = if (editorTarget != null) {
                            val presetName = resolveStopOrEntryPresetLabel(
                                targetCode = editorTarget,
                                isCourse = info.isCourse,
                                presetId = cfg.presetId,
                                presets = presets,
                                isPresetSelectable = isPresetSelectable,
                            )
                            val nativeStatus = if (info.isCourse) {
                                when (cfg.nativeTransitionEnabled) {
                                    true -> stringResource(R.string.discovery_playlist_entry_native_on)
                                    false -> stringResource(R.string.discovery_playlist_entry_native_off)
                                    null -> if (playlist.nativeTransitionDefault) stringResource(R.string.discovery_playlist_entry_native_inherit) else null
                                }
                            } else null
                            listOfNotNull(info.categorySubtitle, presetName, nativeStatus).joinToString(" · ")
                        } else {
                            info.categorySubtitle
                        }
                        val statId = when (val r = info.ref) {
                            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> r.logicalCode
                            else -> r.raw
                        }
                        val plays = playStats[statId]?.playCount ?: 0
                        val metaLine = if (plays > 0) {
                            listOfNotNull(baseMetaLine.takeIf { it.isNotBlank() }, pluralStringResource(R.plurals.play_count_plurals, plays, plays)).joinToString(" · ")
                        } else {
                            baseMetaLine
                        }
                        val courseCodeOrNull = (info.ref as? com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course)?.logicalCode
                        val spec = TrackMenuSpec(
                            selectionKey = index.toString(),
                            playlistRef = rawId,
                            title = title,
                            subtitle = metaLine,
                            artworkName = info.artworkName,
                            fallbackCourseCode = info.fallbackCourseCode,
                            favoriteId = if (info.isCourse) courseCodeOrNull else rawId,
                            courseCodeForDetail = if (info.isCourse && info.available) courseCodeOrNull else null,
                            editorTargetCode = if (info.available) editorTarget else null,
                            infoPairs = listOf(
                                R.string.track_info_category to info.categorySubtitle,
                                R.string.track_info_ref to rawId,
                            ),
                            onPlayNow = { if (info.available) onPlayFrom(index) },
                            playlistEntryIndex = index,
                            playlistCanMoveUp = index > 0,
                            playlistCanMoveDown = index + 1 < playlist.courseIds.size,
                            onPlaylistConfig = if (editorTarget != null && info.available) ({ editingEntryIndex = index }) else null,
                            onPlaylistMoveUp = if (index > 0) ({ onMove(index, index - 1) }) else null,
                            onPlaylistMoveDown = if (index + 1 < playlist.courseIds.size) ({ onMove(index, index + 1) }) else null,
                            onPlaylistRemove = { onRemove(index) },
                        )
                        val isSelected = index in selectedIndexSet
                        val dragging = dragController.draggingIndex == index
                        val rowMod = if (dragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = dragController.dragDeltaY }
                        } else {
                            Modifier
                        }
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode && dragController.draggingIndex == null,
                            onSwipeStartToEnd = if (allowPlayNext && info.available) {
                                {
                                    if (onPlayNextRefs(listOf(rawId))) {
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                }
                            } else null,
                            onSwipeEndToStart = { onRemove(index) },
                            endToStartIcon = Icons.Filled.DeleteOutline,
                            endToStartLabel = stringResource(R.string.swipe_action_remove),
                            endToStartDestructive = true,
                        ) {
                            val rowSelected = dragging || isSelected
                            MkwCard(
                                selected = rowSelected,
                                tone = MkwSurfaceTone.Porcelain,
                                shape = MkwKitTokens.Shape.control,
                                contentPadding = PaddingValues(10.dp),
                                modifier = rowMod
                                    .fillMaxWidth()
                                    .clip(MkwKitTokens.Shape.control)
                                    .combinedClickable(
                                        onClick = {
                                            if (multiSelectMode) {
                                                selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                            } else if (info.available) {
                                                onPlayFrom(index)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (multiSelectMode) {
                                                selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                            } else {
                                                trackMenuTarget = spec
                                            }
                                        },
                                    ),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (multiSelectMode) {
                                        MkwCheckboxMark(checked = isSelected)
                                    }
                                    PackArtwork.Thumbnail(
                                        packDirectory,
                                        listOfNotNull(info.artworkName),
                                        Modifier.size(44.dp).clip(MkwKitTokens.Shape.thumb),
                                    ) {
                                        val fallback = info.fallbackCourseCode
                                        if (fallback != null) CourseCover(fallback, Modifier.fillMaxSize())
                                        else Icon(Icons.Outlined.MusicNote, null, modifier = Modifier.size(22.dp), tint = MkwKitTokens.Palette.inkSecondary)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(metaLine, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (!multiSelectMode) {
                                        if (editorTarget != null) {
                                            MkwIconButton(
                                                icon = Icons.Filled.Tune,
                                                contentDescription = stringResource(R.string.discovery_playlist_entry_config),
                                                onClick = { editingEntryIndex = index },
                                                enabled = info.available,
                                                visualSize = 36.dp,
                                                iconSize = 19.dp,
                                            )
                                        }
                                        MkwIconButton(
                                            icon = Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.jukebox_more_actions),
                                            onClick = { trackMenuTarget = spec },
                                            visualSize = 36.dp,
                                            iconSize = 19.dp,
                                        )
                                        if (playlist.courseIds.size > 1) {
                                            com.mylittleblover.mkwplayer.ui.components.DragHandleButton(
                                                index = index,
                                                controller = dragController,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = multiSelectMode && playlist != null) {
                val chosenRefs = selectedIndices
                    .sorted()
                    .mapNotNull { idx -> playlist?.courseIds?.getOrNull(idx) }
                MultiSelectActionBar(
                    hasSelection = chosenRefs.isNotEmpty(),
                    allowPlayNext = allowPlayNext,
                    onPlaySelected = {
                        if (chosenRefs.isNotEmpty() && onPlaySelectedRefs(chosenRefs)) {
                            multiSelectMode = false
                            selectedIndices = emptyList()
                        }
                    },
                    onPlayNextSelected = {
                        if (chosenRefs.isNotEmpty() && onPlayNextRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        }
                        multiSelectMode = false
                        selectedIndices = emptyList()
                    },
                    onAddToQueueSelected = {
                        if (chosenRefs.isNotEmpty() && onAddToQueueRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                        }
                        multiSelectMode = false
                        selectedIndices = emptyList()
                    },
                    onAddToPlaylistSelected = {
                        if (chosenRefs.isNotEmpty()) playlistPickerRefs = chosenRefs
                    },
                    onRemoveSelected = {
                        if (selectedIndexSet.isNotEmpty()) {
                            onRemoveMany(selectedIndexSet)
                        }
                        multiSelectMode = false
                        selectedIndices = emptyList()
                    },
                )
            }
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(0.dp)),
        )
    }
}

/**
 * Dedicated screen for the pinned "My Favorites" permanent playlist inside My Playlists.
 * Reuses the exact track resolution, swipe actions, multi-select, and context sheet model as
 * user playlists while locking out destructive playlist-level actions (rename/delete).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DiscoveryFavoritesPlaylist(
    favorites: Set<String>,
    catalog: BrowseCatalog?,
    unlocked: Boolean,
    language: String,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset> = emptyList(),
    packDirectory: java.io.File? = null,
    courseArtwork: Map<String, String> = emptyMap(),
    jukebox: JukeboxCatalog.Catalog = JukeboxCatalog.Catalog(emptyList(), emptyMap()),
    extraCatalog: ExtraMusicCatalog.Catalog = ExtraMusicCatalog.Catalog(),
    isThisPlaylistPlaying: Boolean = false,
    onPlayAll: (playableRefs: List<String>) -> Unit = {},
    onShuffle: (playableRefs: List<String>) -> Unit = {},
    onPlayFrom: (playableRefs: List<String>, index: Int) -> Unit = { _, _ -> },
    onCourse: (String) -> Unit = {},
    onOpenFlowEditor: (String, String?) -> Unit = { _, _ -> },
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
    onToggleFavorite: (String) -> Unit = {},
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
    allowPlayNext: Boolean = true,
    onPlayNextRefs: (List<String>) -> Boolean = { false },
    onAddToQueueRefs: (List<String>) -> Boolean = { false },
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
    playStats: Map<String, com.mylittleblover.mkwplayer.library.TrackPlayStat> = emptyMap(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    val visibleCourses = catalog?.courses(unlocked).orEmpty()
    val visibleCourseSet = remember(visibleCourses) { visibleCourses.toSet() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val coursesCatLabel = stringResource(R.string.discovery_courses)
    val sfcCatLabel = stringResource(R.string.discovery_vs_sfc)
    val battleCatLabel = stringResource(R.string.discovery_battle)
    val freeCatLabel = stringResource(R.string.discovery_free)
    val otherCatLabel = stringResource(R.string.discovery_other)
    val soundsCatLabel = stringResource(R.string.discovery_sounds)
    val nmCatLabel = stringResource(R.string.discovery_nintendo_music)
    val addedNextText = stringResource(R.string.jukebox_added_next)
    val addedQueueText = stringResource(R.string.track_action_added_to_queue)
    val gamesById = remember(jukebox) { jukebox.games.associateBy { it.id } }

    var trackMenuTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var infoTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var playlistPickerRefs by remember { mutableStateOf<List<String>?>(null) }
    var multiSelectMode by rememberSaveable("favorites-pl") { mutableStateOf(false) }
    var selectedRefs by rememberSaveable("favorites-pl-sel") { mutableStateOf(listOf<String>()) }
    val selectedRefSet = remember(selectedRefs) { selectedRefs.toSet() }

    val staffCreditsUnlocked = LocalStaffCreditsUnlocked.current
    val favoriteRows = remember(
        favorites,
        catalog,
        unlocked,
        staffCreditsUnlocked,
        language,
        extraCatalog,
        jukebox,
        courseArtwork,
        visibleCourseSet,
    ) {
        favorites.toList().mapNotNull { rawId ->
            val info = resolvePlaylistRowDisplay(
                rawId = rawId,
                catalog = catalog,
                unlocked = unlocked,
                language = language,
                extraCatalog = extraCatalog,
                jukebox = jukebox,
                gamesById = gamesById,
                visibleCourseSet = visibleCourseSet,
                courseArtwork = courseArtwork,
                coursesCatLabel = coursesCatLabel,
                sfcCatLabel = sfcCatLabel,
                battleCatLabel = battleCatLabel,
                freeCatLabel = freeCatLabel,
                otherCatLabel = otherCatLabel,
                soundsCatLabel = soundsCatLabel,
                nmCatLabel = nmCatLabel,
                staffCreditsUnlocked = staffCreditsUnlocked,
            )
            if (!info.available) null else (rawId to info)
        }
    }
    val playableRefs = remember(favoriteRows) { favoriteRows.map { it.first } }

    androidx.activity.compose.BackHandler(enabled = multiSelectMode) {
        multiSelectMode = false
        selectedRefs = emptyList()
    }

    TrackActionOverlayHosts(
        menuTarget = trackMenuTarget,
        onDismissMenu = { trackMenuTarget = null },
        playlistPickerRefs = playlistPickerRefs,
        onOpenPlaylistPicker = { refs -> playlistPickerRefs = refs },
        onDismissPlaylistPicker = { playlistPickerRefs = null },
        infoTarget = infoTarget,
        onOpenInfo = { infoTarget = it },
        onDismissInfo = { infoTarget = null },
        packDirectory = packDirectory,
        allowPlayNext = allowPlayNext,
        playlists = playlists,
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        onOpenCourse = onCourse,
        onOpenFlowEditor = { editorCode -> onOpenFlowEditor(editorCode, null) },
        onEnterMultiSelect = { key ->
            multiSelectMode = true
            selectedRefs = (selectedRefs + key).distinct()
        },
        onPlayNextRefs = onPlayNextRefs,
        onAddToQueueRefs = onAddToQueueRefs,
        onAddToPlaylist = { pid, refs ->
            onAddToPlaylist(pid, refs)
            multiSelectMode = false
            selectedRefs = emptyList()
        },
        onCreatePlaylistAndAdd = { name, refs ->
            onCreatePlaylistAndAdd(name, refs)
            multiSelectMode = false
            selectedRefs = emptyList()
        },
        snackbarHostState = snackbarHostState,
        addedNextText = addedNextText,
        addedQueueText = addedQueueText,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = multiSelectMode) {
                val allSelected = playableRefs.isNotEmpty() && selectedRefSet.containsAll(playableRefs)
                MultiSelectTopBanner(
                    selectedCount = selectedRefSet.size,
                    allSelected = allSelected,
                    onToggleSelectAll = {
                        selectedRefs = if (allSelected) emptyList() else playableRefs
                    },
                    onClose = {
                        multiSelectMode = false
                        selectedRefs = emptyList()
                    },
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "fav-pl-hero") {
                    val firstHero = favoriteRows.firstOrNull()?.second
                    MkwCard(
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MkwKitTokens.Shape.card)
                                    .background(MkwKitTokens.Palette.rose),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (firstHero != null && (firstHero.artworkName != null || firstHero.fallbackCourseCode != null)) {
                                    PackArtwork.Thumbnail(
                                        packDirectory,
                                        listOfNotNull(firstHero.artworkName),
                                        Modifier.fillMaxSize(),
                                    ) {
                                        val fallbackCourse = firstHero.fallbackCourseCode
                                        val art = fallbackCourse?.let { StageArt.resolve(it) }
                                        if (art != null) ResourceArtwork(art, Modifier.size(56.dp))
                                        else Icon(Icons.Filled.Favorite, null, modifier = Modifier.size(32.dp), tint = MkwKitTokens.Palette.crimson)
                                    }
                                } else {
                                    Icon(Icons.Filled.Favorite, null, modifier = Modifier.size(34.dp), tint = MkwKitTokens.Palette.crimson)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.pinned_favorites_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MkwKitTokens.Palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MkwBadge(text = stringResource(R.string.pinned_favorites_badge), tone = MkwBadgeTone.Rose, tabular = false)
                                    Text(
                                        pluralStringResource(R.plurals.tracks_count_plurals, favoriteRows.size, favoriteRows.size),
                                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                        color = MkwKitTokens.Palette.inkSecondary,
                                    )
                                }
                            }
                            Box {
                                MkwIconButton(
                                    icon = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.jukebox_more_actions),
                                    onClick = { menuOpen = true },
                                )
                                MkwDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    MkwDropdownItem(
                                        text = stringResource(R.string.track_action_multi_select),
                                        leadingIcon = Icons.Outlined.CheckCircle,
                                        enabled = favoriteRows.isNotEmpty(),
                                        onClick = {
                                            menuOpen = false
                                            multiSelectMode = true
                                            selectedRefs = emptyList()
                                        },
                                    )
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MkwPrimaryButton(
                                text = stringResource(R.string.discovery_playlist_play_all),
                                onClick = { onPlayAll(playableRefs) },
                                enabled = playableRefs.isNotEmpty(),
                                leadingIcon = Icons.Filled.PlayArrow,
                                modifier = Modifier.weight(1f),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.jukebox_shuffle),
                                onClick = { onShuffle(playableRefs) },
                                enabled = playableRefs.isNotEmpty(),
                                leadingIcon = Icons.Filled.Shuffle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (isThisPlaylistPlaying) item(key = "fav-pl-playing") {
                    MkwCard(
                        tone = MkwSurfaceTone.RoseTint,
                        selected = true,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.discovery_playlist_frozen_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimsonDeep,
                        )
                    }
                }
                if (favoriteRows.isEmpty()) {
                    item(key = "fav-pl-empty") {
                        MkwInsetBox(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.FavoriteBorder, null, tint = MkwKitTokens.Palette.crimson)
                                Text(
                                    stringResource(R.string.pinned_favorites_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MkwKitTokens.Palette.inkSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(favoriteRows, key = { _, (rawId, _) -> "fav-row:$rawId" }) { index, (rawId, info) ->
                        val title = info.title.ifBlank { rawId }
                        val statId = when (val r = info.ref) {
                            is com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course -> r.logicalCode
                            else -> r.raw
                        }
                        val plays = playStats[statId]?.playCount ?: 0
                        val metaLine = if (plays > 0) {
                            listOfNotNull(
                                info.categorySubtitle.takeIf { it.isNotBlank() },
                                pluralStringResource(R.plurals.play_count_plurals, plays, plays),
                            ).joinToString(" · ")
                        } else {
                            info.categorySubtitle
                        }
                        val courseCodeOrNull = (info.ref as? com.mylittleblover.mkwplayer.library.PlaylistItemRef.Course)?.logicalCode
                        val favId = if (info.isCourse) (courseCodeOrNull ?: rawId) else rawId
                        val spec = TrackMenuSpec(
                            selectionKey = rawId,
                            playlistRef = rawId,
                            title = title,
                            subtitle = metaLine,
                            artworkName = info.artworkName,
                            fallbackCourseCode = info.fallbackCourseCode,
                            favoriteId = favId,
                            courseCodeForDetail = if (info.isCourse && info.available) courseCodeOrNull else null,
                            editorTargetCode = if (info.available) info.editorTargetCode else null,
                            infoPairs = listOf(
                                R.string.track_info_category to info.categorySubtitle,
                                R.string.track_info_ref to rawId,
                            ),
                            onPlayNow = { onPlayFrom(playableRefs, index) },
                        )
                        val isSelected = rawId in selectedRefSet
                        com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                            enabled = !multiSelectMode,
                            onSwipeStartToEnd = if (allowPlayNext) {
                                {
                                    if (onPlayNextRefs(listOf(rawId))) {
                                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                                    }
                                }
                            } else null,
                            onSwipeEndToStart = { onToggleFavorite(favId) },
                            endToStartIcon = Icons.Filled.Favorite,
                            endToStartLabel = stringResource(R.string.favorites_remove),
                            endToStartDestructive = true,
                        ) {
                            MkwCard(
                                selected = isSelected,
                                tone = MkwSurfaceTone.Porcelain,
                                shape = MkwKitTokens.Shape.control,
                                contentPadding = PaddingValues(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MkwKitTokens.Shape.control)
                                    .combinedClickable(
                                        onClick = {
                                            if (multiSelectMode) {
                                                selectedRefs = if (isSelected) selectedRefs - rawId else selectedRefs + rawId
                                            } else {
                                                onPlayFrom(playableRefs, index)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (multiSelectMode) {
                                                selectedRefs = if (isSelected) selectedRefs - rawId else selectedRefs + rawId
                                            } else {
                                                trackMenuTarget = spec
                                            }
                                        },
                                    ),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (multiSelectMode) {
                                        MkwCheckboxMark(checked = isSelected)
                                    }
                                    PackArtwork.Thumbnail(
                                        packDirectory,
                                        listOfNotNull(info.artworkName),
                                        Modifier.size(44.dp).clip(MkwKitTokens.Shape.thumb),
                                    ) {
                                        val fallback = info.fallbackCourseCode
                                        if (fallback != null) CourseCover(fallback, Modifier.fillMaxSize())
                                        else Icon(Icons.Outlined.MusicNote, null, modifier = Modifier.size(22.dp), tint = MkwKitTokens.Palette.inkSecondary)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(metaLine, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (!multiSelectMode) {
                                        MkwIconButton(
                                            icon = Icons.Filled.Favorite,
                                            contentDescription = stringResource(R.string.favorites_remove),
                                            onClick = { onToggleFavorite(favId) },
                                            visualSize = 36.dp,
                                            iconSize = 19.dp,
                                            tintOverride = MkwKitTokens.Palette.crimson,
                                        )
                                        MkwIconButton(
                                            icon = Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.jukebox_more_actions),
                                            onClick = { trackMenuTarget = spec },
                                            visualSize = 36.dp,
                                            iconSize = 19.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = multiSelectMode) {
                val chosenRefs = playableRefs.filter { it in selectedRefSet }
                MultiSelectActionBar(
                    hasSelection = chosenRefs.isNotEmpty(),
                    allowPlayNext = allowPlayNext,
                    onPlaySelected = {
                        if (chosenRefs.isNotEmpty() && onPlaySelectedRefs(chosenRefs)) {
                            multiSelectMode = false
                            selectedRefs = emptyList()
                        }
                    },
                    onPlayNextSelected = {
                        if (chosenRefs.isNotEmpty() && onPlayNextRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        }
                        multiSelectMode = false
                        selectedRefs = emptyList()
                    },
                    onAddToQueueSelected = {
                        if (chosenRefs.isNotEmpty() && onAddToQueueRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                        }
                        multiSelectMode = false
                        selectedRefs = emptyList()
                    },
                    onAddToPlaylistSelected = {
                        if (chosenRefs.isNotEmpty()) playlistPickerRefs = chosenRefs
                    },
                    onRemoveSelected = {
                        chosenRefs.forEach { rawId -> onToggleFavorite(rawId) }
                        multiSelectMode = false
                        selectedRefs = emptyList()
                    },
                )
            }
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(0.dp)),
        )
    }
}

@Composable
private fun JukeboxGameRow(
    game: JukeboxCatalog.Game,
    language: String,
    packDirectory: java.io.File? = null,
    artworkName: String? = null,
    chronologicalIndex: Int? = null,
    onClick: () -> Unit,
) {
    val localized = game.localizedName(language)
    val countLabel = pluralStringResource(R.plurals.tracks_count_plurals, game.items.size, game.items.size)
    val sub = listOfNotNull(
        game.yearLabel.takeIf { it.isNotBlank() },
        countLabel,
    ).joinToString(" · ")
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PackArtwork.Thumbnail(
                packDirectory, listOfNotNull(artworkName),
                Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            ) {
                val badge = game.year.takeIf { it > 0 }?.toString()
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    localized,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Compact color-coded MD3 genre pill shared across Free Roam rows, PlayScreen, and Queue (#9). */
@Composable
internal fun JukeboxGenrePill(
    genre: JukeboxCatalog.GenreInfo,
    language: String,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    Surface(
        color = genre.tint,
        shape = MkwKitTokens.Shape.pill,
        border = BorderStroke(MkwKitTokens.Stroke.hairline, genre.color.copy(alpha = 0.25f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = genre.icon.imageVector,
                contentDescription = null,
                tint = genre.color,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
            )
            Text(
                text = genre.displayName(language),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = genre.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JukeboxTrackRow(
    item: JukeboxCatalog.TrackItem,
    language: String = "en",
    subtitleExtra: String? = null,
    indexNumber: Int? = null,
    playCount: Int = 0,
    packDirectory: java.io.File? = null,
    playing: Boolean = false,
    allowPlayNext: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onOpenMenu: (() -> Unit)? = null,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayFromHere: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val (pressModifier, interactionSource) = com.mylittleblover.mkwplayer.ui.rememberPressScale(0.98f)
    val artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
    val highlighted = playing || selected
    val shape = MkwKitTokens.Shape.control
    val borderCol = if (highlighted) MkwKitTokens.Palette.crimson.copy(alpha = 0.55f) else MkwKitTokens.Palette.hairline
    val bgCol = if (highlighted) MkwKitTokens.Palette.rose.copy(alpha = 0.45f) else MkwKitTokens.Palette.card
    Surface(
        color = bgCol,
        shape = shape,
        tonalElevation = 0.dp,
        border = BorderStroke(MkwKitTokens.Stroke.border, borderCol),
        modifier = Modifier
            .fillMaxWidth()
            .then(pressModifier)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onPlay,
                onLongClick = {
                    val action = onLongClick ?: onOpenMenu
                    if (action != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectionMode) {
                MkwCheckboxMark(checked = selected)
            }
            PackArtwork.Thumbnail(
                packDirectory, listOfNotNull(artworkName), Modifier.size(48.dp).clip(MkwKitTokens.Shape.thumb),
            ) {
                if (playing) {
                    Icon(Icons.Filled.PlayArrow, null, tint = MkwKitTokens.Palette.crimson)
                } else {
                    Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.inkSecondary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.displayTitle(language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (playing) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (playing) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaParts = buildList {
                    if (item.genre == null) {
                        item.localizedArrangement(language)?.let { add(it) }
                    }
                    subtitleExtra?.let { add(it) }
                    if (item.durationMs > 0L) add(item.durationFormatted)
                    if (playCount > 0) add(pluralStringResource(R.plurals.play_count_plurals, playCount, playCount))
                }
                if (item.genre != null || metaParts.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.genre?.let { genre ->
                            JukeboxGenrePill(genre = genre, language = language, compact = true)
                        }
                        if (metaParts.isNotEmpty()) {
                            Text(
                                metaParts.joinToString(" · "),
                                style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                color = MkwKitTokens.Palette.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
            }
            if (!selectionMode) {
                Box {
                    MkwIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.jukebox_more_actions),
                        onClick = { if (onOpenMenu != null) onOpenMenu() else menuOpen = true },
                        visualSize = 36.dp,
                        iconSize = 20.dp,
                        tintOverride = MkwKitTokens.Palette.inkSecondary,
                    )
                    if (onOpenMenu == null) {
                        MkwDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MkwDropdownItem(
                                text = if (allowPlayNext) stringResource(R.string.jukebox_play_next)
                                else "${stringResource(R.string.jukebox_play_next)} (${stringResource(R.string.route_play_next_disabled)})",
                                leadingIcon = Icons.AutoMirrored.Filled.QueueMusic,
                                enabled = allowPlayNext,
                                onClick = {
                                    menuOpen = false
                                    onPlayNext()
                                },
                            )
                            MkwDropdownItem(
                                text = stringResource(R.string.jukebox_play_from_here),
                                leadingIcon = Icons.Filled.PlayArrow,
                                onClick = {
                                    menuOpen = false
                                    onPlayFromHere()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Detail page for one original reference game in the Jukebox (#98-#104), aligned with
 * the HTML prototype's `gamepage()`: chronological game header, OST track sequence,
 * independent rows for every arrangement, and Play All / Shuffle / Play Next actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryJukeboxGame(
    game: JukeboxCatalog.Game?,
    language: String,
    activeTrackKey: String?,
    packDirectory: java.io.File? = null,
    allowPlayNext: Boolean = true,
    onPlayFromIndex: (JukeboxCatalog.Game, Int, Boolean) -> Unit,
    onPlayNext: (JukeboxCatalog.TrackItem, JukeboxCatalog.Game) -> Unit,
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(),
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit = { _, _ -> },
    onAddToQueueRefs: (List<String>) -> Boolean = { false },
    onPlaySelectedRefs: (List<String>) -> Boolean = { false },
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    playStats: Map<String, com.mylittleblover.mkwplayer.library.TrackPlayStat> = emptyMap(),
) {
    if (game == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.discovery_empty))
        }
        return
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val addedNextText = stringResource(R.string.jukebox_added_next)
    val addedQueueText = stringResource(R.string.track_action_added_to_queue)
    val localizedTitle = game.localizedName(language)
    val englishTitle = game.localizedNames["en"]

    var sortOrder by rememberSaveable(game.id) { mutableStateOf(com.mylittleblover.mkwplayer.library.TrackSortOrder.DEFAULT) }
    var menuTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var infoTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }
    var playlistPickerRefs by remember { mutableStateOf<List<String>?>(null) }
    var multiSelectMode by rememberSaveable(game.id) { mutableStateOf(false) }
    var selectedKeys by rememberSaveable(game.id) { mutableStateOf(listOf<String>()) }
    val selectedSet = remember(selectedKeys) { selectedKeys.toSet() }

    androidx.activity.compose.BackHandler(enabled = multiSelectMode) {
        multiSelectMode = false
        selectedKeys = emptyList()
    }

    val trackSpecs = remember(game, language) {
        game.items.mapIndexed { idx, item ->
            val refRaw = com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw
            val art = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) }
            val infoPairs = buildList {
                add(R.string.track_info_game to localizedTitle)
                if (item.durationMs > 0L) add(R.string.track_info_duration to item.durationFormatted)
                item.ostIndex?.let { add(R.string.track_info_ost to "#$it") }
                add(R.string.track_info_ref to item.key)
            }
            TrackMenuSpec(
                selectionKey = item.key,
                playlistRef = refRaw,
                title = item.localizedTitle(language),
                subtitle = listOfNotNull(item.localizedArrangement(language), localizedTitle).joinToString(" · "),
                artworkName = art,
                favoriteId = refRaw,
                infoPairs = infoPairs,
                onPlayNow = { onPlayFromIndex(game, idx, false) },
                onPlayNextDirect = { onPlayNext(item, game) },
            )
        }
    }

    TrackActionOverlayHosts(
        menuTarget = menuTarget,
        onDismissMenu = { menuTarget = null },
        playlistPickerRefs = playlistPickerRefs,
        onOpenPlaylistPicker = { refs -> playlistPickerRefs = refs },
        onDismissPlaylistPicker = { playlistPickerRefs = null },
        infoTarget = infoTarget,
        onOpenInfo = { infoTarget = it },
        onDismissInfo = { infoTarget = null },
        packDirectory = packDirectory,
        allowPlayNext = allowPlayNext,
        playlists = playlists,
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        onEnterMultiSelect = { key ->
            multiSelectMode = true
            selectedKeys = (selectedKeys + key).distinct()
        },
        onPlayNextRefs = { false },
        onAddToQueueRefs = { refs -> onAddToQueueRefs(refs) },
        onAddToPlaylist = { pid, refs ->
            onAddToPlaylist(pid, refs)
            multiSelectMode = false
            selectedKeys = emptyList()
        },
        onCreatePlaylistAndAdd = { name, refs ->
            onCreatePlaylistAndAdd(name, refs)
            multiSelectMode = false
            selectedKeys = emptyList()
        },
        snackbarHostState = snackbarHostState,
        addedNextText = addedNextText,
        addedQueueText = addedQueueText,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = multiSelectMode) {
                val allKeys = trackSpecs.map { it.selectionKey }
                val allSelected = allKeys.isNotEmpty() && selectedSet.containsAll(allKeys)
                MultiSelectTopBanner(
                    selectedCount = selectedSet.size,
                    allSelected = allSelected,
                    onToggleSelectAll = {
                        selectedKeys = if (allSelected) emptyList() else allKeys
                    },
                    onClose = {
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                )
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") {
                    MkwCard(
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.jukebox_game_eyebrow),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.crimson,
                        )
                        Text(
                            localizedTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        if (englishTitle != null && !englishTitle.equals(localizedTitle, ignoreCase = true)) {
                            Text(
                                englishTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                        val countStr = pluralStringResource(R.plurals.tracks_count_plurals, game.items.size, game.items.size)
                        val yearAndCount = if (game.yearLabel.isNotBlank()) "${game.yearLabel} · $countStr" else countStr
                        Text(
                            yearAndCount,
                            style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MkwPrimaryButton(
                                text = stringResource(R.string.jukebox_play_all),
                                onClick = { onPlayFromIndex(game, 0, false) },
                                enabled = game.items.isNotEmpty(),
                                leadingIcon = Icons.Filled.PlayArrow,
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.jukebox_shuffle_play),
                                onClick = {
                                    val randomStart = game.items.indices.random()
                                    onPlayFromIndex(game, randomStart, true)
                                },
                                enabled = game.items.isNotEmpty(),
                                leadingIcon = Icons.Filled.Shuffle,
                            )
                        }
                    }
                }
                item(key = "arr-section") {
                    Text(
                        stringResource(R.string.jukebox_arrangements_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                if (game.items.size > 1) {
                    item(key = "arr-sort-bar") {
                        com.mylittleblover.mkwplayer.ui.components.TrackSortBar(
                            selected = sortOrder,
                            includeOstOrder = false,
                            onSelect = { sortOrder = it },
                            modifier = Modifier.unpadHorizontal(16.dp),
                        )
                    }
                }
                val indexedItems = when (sortOrder) {
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.DEFAULT ->
                        game.items.mapIndexed { idx, item -> idx to item }
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.OST_INDEX ->
                        game.items.mapIndexed { idx, item -> idx to item }
                            .sortedWith(compareBy<Pair<Int, JukeboxCatalog.TrackItem>> { it.second.ostIndex ?: Int.MAX_VALUE }.thenBy { it.first })
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.NAME_ASC ->
                        game.items.mapIndexed { idx, item -> idx to item }
                            .sortedWith(compareBy<Pair<Int, JukeboxCatalog.TrackItem>> { it.second.localizedTitle(language).lowercase() }.thenBy { it.first })
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.DURATION_DESC ->
                        game.items.mapIndexed { idx, item -> idx to item }
                            .sortedWith(compareByDescending<Pair<Int, JukeboxCatalog.TrackItem>> { it.second.durationMs }.thenBy { it.first })
                    com.mylittleblover.mkwplayer.library.TrackSortOrder.MOST_PLAYED ->
                        game.items.mapIndexed { idx, item -> idx to item }
                            .sortedWith(
                                compareByDescending<Pair<Int, JukeboxCatalog.TrackItem>> {
                                    playStats[com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(it.second.key).raw]?.playCount ?: 0
                                }.thenBy { it.first },
                            )
                }
                items(indexedItems, key = { (_, item) -> "jb-game:${item.key}" }) { (origIdx, item) ->
                    val spec = trackSpecs[origIdx]
                    val isChecked = spec.selectionKey in selectedSet
                    val refRaw = com.mylittleblover.mkwplayer.library.PlaylistItemRef.Jukebox(item.key).raw
                    val plays = playStats[refRaw]?.playCount ?: 0
                    val isFav = refRaw in favorites
                    com.mylittleblover.mkwplayer.ui.components.SwipeToTrackActionBox(
                        enabled = !multiSelectMode,
                        onSwipeStartToEnd = if (allowPlayNext) {
                            {
                                onPlayNext(item, game)
                                scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                            }
                        } else null,
                        onSwipeEndToStart = { onToggleFavorite(refRaw) },
                        endToStartIcon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        endToStartLabel = stringResource(if (isFav) R.string.swipe_action_unfavorite else R.string.swipe_action_favorite),
                    ) {
                        JukeboxTrackRow(
                            item = item,
                            language = language,
                            playCount = plays,
                            packDirectory = packDirectory,
                            playing = activeTrackKey == item.key,
                            allowPlayNext = allowPlayNext,
                            selectionMode = multiSelectMode,
                            selected = isChecked,
                            onLongClick = {
                                if (multiSelectMode) {
                                    selectedKeys = if (isChecked) selectedKeys - spec.selectionKey else selectedKeys + spec.selectionKey
                                } else {
                                    menuTarget = spec
                                }
                            },
                            onOpenMenu = { menuTarget = spec },
                            onPlay = {
                                if (multiSelectMode) {
                                    selectedKeys = if (isChecked) selectedKeys - spec.selectionKey else selectedKeys + spec.selectionKey
                                } else {
                                    onPlayFromIndex(game, origIdx, false)
                                }
                            },
                            onPlayNext = {
                                onPlayNext(item, game)
                                scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                            },
                            onPlayFromHere = { onPlayFromIndex(game, origIdx, false) },
                        )
                    }
                }
            }
            AnimatedVisibility(visible = multiSelectMode) {
                val chosenSpecs = trackSpecs.filter { it.selectionKey in selectedSet }
                val chosenRefs = chosenSpecs.mapNotNull { it.playlistRef }
                MultiSelectActionBar(
                    hasSelection = chosenRefs.isNotEmpty(),
                    allowPlayNext = allowPlayNext,
                    onPlaySelected = {
                        if (chosenRefs.isNotEmpty() && onPlaySelectedRefs(chosenRefs)) {
                            multiSelectMode = false
                            selectedKeys = emptyList()
                        }
                    },
                    onPlayNextSelected = {
                        chosenSpecs.asReversed().forEach { s -> s.onPlayNextDirect?.invoke() }
                        scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                    onAddToQueueSelected = {
                        if (chosenRefs.isNotEmpty() && onAddToQueueRefs(chosenRefs)) {
                            scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                        }
                        multiSelectMode = false
                        selectedKeys = emptyList()
                    },
                    onAddToPlaylistSelected = {
                        if (chosenRefs.isNotEmpty()) playlistPickerRefs = chosenRefs
                    },
                )
            }
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(0.dp)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtraSquareRow(
    title: String,
    subtitle: String,
    packDirectory: java.io.File?,
    artworkName: String?,
    fallbackCourseCode: String? = null,
    onAction: (() -> Unit)? = null,
    actionContentDescription: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionDescription: String? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val (pressModifier, interactionSource) = com.mylittleblover.mkwplayer.ui.rememberPressScale(0.98f)
    val haptic = LocalHapticFeedback.current
    val shape = MkwKitTokens.Shape.card
    val bgColor = if (selected) {
        MkwKitTokens.Palette.rose.copy(alpha = 0.45f)
    } else {
        MkwKitTokens.Palette.card
    }
    val borderColor = if (selected) {
        MkwKitTokens.Palette.crimson.copy(alpha = 0.55f)
    } else {
        MkwKitTokens.Palette.hairline
    }
    Surface(
        color = bgColor,
        shape = shape,
        tonalElevation = 0.dp,
        border = BorderStroke(MkwKitTokens.Stroke.border, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .then(pressModifier)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    if (onLongClick != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectionMode) {
                MkwCheckboxMark(checked = selected)
            }
            PackArtwork.Thumbnail(
                packDirectory, listOfNotNull(artworkName),
                Modifier.size(56.dp).clip(MkwKitTokens.Shape.thumb)
                    .background(MkwKitTokens.Palette.track),
            ) {
                if (fallbackCourseCode != null) CourseCover(fallbackCourseCode, Modifier.fillMaxSize())
                else Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.inkSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                        color = MkwKitTokens.Palette.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!selectionMode) {
                if (onSecondaryAction != null) {
                    MkwIconButton(
                        icon = Icons.Filled.Tune,
                        contentDescription = secondaryActionDescription,
                        onClick = onSecondaryAction,
                        visualSize = 36.dp,
                        iconSize = 20.dp,
                        tintOverride = MkwKitTokens.Palette.inkSecondary,
                    )
                }
                if (onAction != null) {
                    MkwIconButton(
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = actionContentDescription,
                        onClick = onAction,
                        tone = MkwIconButtonTone.Rose,
                        visualSize = 42.dp,
                        iconSize = 20.dp,
                    )
                } else if (onSecondaryAction == null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MkwKitTokens.Palette.inkSecondary)
                }
            }
        }
    }
}

internal data class TrackMenuSpec(
    val selectionKey: String,
    val playlistRef: String?,
    val title: String,
    val subtitle: String,
    val artworkName: String? = null,
    val fallbackCourseCode: String? = null,
    val favoriteId: String? = null,
    val courseCodeForDetail: String? = null,
    val editorTargetCode: String? = null,
    val infoPairs: List<Pair<Int, String>> = emptyList(),
    val onPlayNow: () -> Unit = {},
    val onPlayNextDirect: (() -> Unit)? = null,
    val playlistEntryIndex: Int? = null,
    val playlistCanMoveUp: Boolean = false,
    val playlistCanMoveDown: Boolean = false,
    val onPlaylistConfig: (() -> Unit)? = null,
    val onPlaylistMoveUp: (() -> Unit)? = null,
    val onPlaylistMoveDown: (() -> Unit)? = null,
    val onPlaylistRemove: (() -> Unit)? = null,
)

@Composable
private fun MultiSelectTopBanner(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MkwKitTokens.Palette.rose,
        contentColor = MkwKitTokens.Palette.crimsonDeep,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MkwIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.discovery_cancel),
                    onClick = onClose,
                    visualSize = 36.dp,
                    iconSize = 20.dp,
                    tintOverride = MkwKitTokens.Palette.crimsonDeep,
                )
                Text(
                    text = stringResource(R.string.track_select_count, selectedCount),
                    style = MkwKitTokens.tabular(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.crimsonDeep,
                    modifier = Modifier.weight(1f),
                )
                MkwGhostButton(
                    text = stringResource(if (allSelected) R.string.track_select_clear else R.string.track_select_all),
                    onClick = onToggleSelectAll,
                    leadingIcon = Icons.Filled.SelectAll,
                )
            }
            MkwDivider(color = MkwKitTokens.Palette.crimson.copy(alpha = 0.2f))
        }
    }
}

@Composable
private fun MultiSelectActionBar(
    hasSelection: Boolean,
    allowPlayNext: Boolean,
    onPlaySelected: () -> Unit,
    onPlayNextSelected: () -> Unit,
    onAddToQueueSelected: () -> Unit,
    onAddToPlaylistSelected: () -> Unit,
    onRemoveSelected: (() -> Unit)? = null,
) {
    MkwFloatingPanel(
        shape = androidx.compose.ui.graphics.RectangleShape,
        shadowElevation = 4.dp,
        alpha = 0.99f,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = com.mylittleblover.mkwplayer.ui.LocalMiniPlayerOverlayPadding.current),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                MkwPrimaryButton(
                    text = stringResource(R.string.track_select_play),
                    onClick = onPlaySelected,
                    enabled = hasSelection,
                    leadingIcon = Icons.Filled.PlayArrow,
                    size = MkwButtonSize.Compact,
                )
            }
            item {
                MkwSecondaryButton(
                    text = stringResource(R.string.jukebox_play_next),
                    onClick = onPlayNextSelected,
                    enabled = hasSelection && allowPlayNext,
                    leadingIcon = Icons.AutoMirrored.Filled.QueueMusic,
                    size = MkwButtonSize.Compact,
                )
            }
            item {
                MkwSecondaryButton(
                    text = stringResource(R.string.track_action_add_to_queue),
                    onClick = onAddToQueueSelected,
                    enabled = hasSelection && allowPlayNext,
                    leadingIcon = Icons.Filled.AddToQueue,
                    size = MkwButtonSize.Compact,
                )
            }
            item {
                MkwRoseButton(
                    text = stringResource(R.string.track_action_add_to_playlist),
                    onClick = onAddToPlaylistSelected,
                    enabled = hasSelection,
                    leadingIcon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    size = MkwButtonSize.Compact,
                )
            }
            if (onRemoveSelected != null) {
                item {
                    MkwDangerButton(
                        text = stringResource(R.string.track_select_remove_playlist),
                        onClick = onRemoveSelected,
                        enabled = hasSelection,
                        leadingIcon = Icons.Filled.DeleteOutline,
                        size = MkwButtonSize.Compact,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackActionOverlayHosts(
    menuTarget: TrackMenuSpec?,
    onDismissMenu: () -> Unit,
    playlistPickerRefs: List<String>?,
    onOpenPlaylistPicker: (List<String>) -> Unit,
    onDismissPlaylistPicker: () -> Unit,
    infoTarget: TrackMenuSpec?,
    onOpenInfo: (TrackMenuSpec) -> Unit,
    onDismissInfo: () -> Unit,
    packDirectory: java.io.File?,
    allowPlayNext: Boolean,
    playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist>,
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onOpenCourse: ((String) -> Unit)? = null,
    onOpenFlowEditor: ((String) -> Unit)? = null,
    onEnterMultiSelect: (String) -> Unit,
    onPlayNextRefs: (List<String>) -> Boolean,
    onAddToQueueRefs: (List<String>) -> Boolean,
    onAddToPlaylist: (playlistId: String, refs: List<String>) -> Unit,
    onCreatePlaylistAndAdd: (name: String, refs: List<String>) -> Unit,
    snackbarHostState: SnackbarHostState,
    addedNextText: String,
    addedQueueText: String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val markerBridge = LocalTrackMarkerBridge.current
    var markerSheetTarget by remember { mutableStateOf<TrackMenuSpec?>(null) }

    markerSheetTarget?.let { target ->
        val trackId = target.playlistRef
        val markers = if (trackId != null) markerBridge.markersByTrackId[trackId].orEmpty() else emptyList()
        com.mylittleblover.mkwplayer.ui.markers.PlayFromMarkerSheet(
            trackTitle = target.title,
            markers = markers,
            onSelectMarker = { marker ->
                markerSheetTarget = null
                if (trackId != null) {
                    markerBridge.onPlayFromMarker(trackId, marker.positionMs, target.onPlayNow)
                }
            },
            onDeleteMarker = { marker ->
                markerBridge.onDeleteMarker(marker.id)
            },
            onDismiss = { markerSheetTarget = null },
        )
    }

    if (menuTarget != null) {
        val isFav = menuTarget.favoriteId != null && menuTarget.favoriteId in favorites
        val trackMarkersForTarget = menuTarget.playlistRef?.let { markerBridge.markersByTrackId[it] }.orEmpty()
        MkwBottomSheet(onDismissRequest = onDismissMenu) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PackArtwork.Thumbnail(
                        packDirectory,
                        listOfNotNull(menuTarget.artworkName),
                        Modifier.size(52.dp).clip(MkwKitTokens.Shape.thumb)
                            .background(MkwKitTokens.Palette.track),
                    ) {
                        if (menuTarget.fallbackCourseCode != null) {
                            CourseCover(menuTarget.fallbackCourseCode, Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.inkSecondary)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            menuTarget.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (menuTarget.subtitle.isNotBlank()) {
                            Text(
                                menuTarget.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (menuTarget.favoriteId != null) {
                        MkwIconButton(
                            icon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(
                                if (isFav) R.string.discovery_favorite_remove else R.string.discovery_favorite_add,
                            ),
                            onClick = { onToggleFavorite(menuTarget.favoriteId) },
                            tintOverride = if (isFav) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                }
                MkwDivider(Modifier.padding(vertical = 4.dp))
                MkwOptionRow(
                    headline = stringResource(R.string.track_action_play_now),
                    leading = { Icon(Icons.Filled.PlayArrow, null, tint = MkwKitTokens.Palette.ink) },
                    onClick = {
                        val action = menuTarget.onPlayNow
                        onDismissMenu()
                        action()
                    },
                )
                if (trackMarkersForTarget.isNotEmpty()) {
                    MkwOptionRow(
                        headline = stringResource(R.string.marker_play_from_menu),
                        leading = { Icon(Icons.Filled.Loop, null, tint = MkwKitTokens.Palette.crimson) },
                        trailing = {
                            MkwBadge(
                                text = trackMarkersForTarget.size.toString(),
                                tone = MkwBadgeTone.Rose,
                            )
                        },
                        onClick = {
                            val target = menuTarget
                            onDismissMenu()
                            markerSheetTarget = target
                        },
                    )
                }
                if (menuTarget.onPlayNextDirect != null || menuTarget.playlistRef != null) {
                    MkwOptionRow(
                        headline = if (allowPlayNext) stringResource(R.string.jukebox_play_next)
                        else "${stringResource(R.string.jukebox_play_next)} (${stringResource(R.string.route_play_next_disabled)})",
                        leading = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = if (allowPlayNext) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary) },
                        enabled = allowPlayNext,
                        onClick = {
                            val direct = menuTarget.onPlayNextDirect
                            val ref = menuTarget.playlistRef
                            onDismissMenu()
                            val ok = if (direct != null) {
                                direct(); true
                            } else if (ref != null) {
                                onPlayNextRefs(listOf(ref))
                            } else false
                            if (ok) scope.launch { snackbarHostState.showSnackbar(addedNextText) }
                        },
                    )
                }
                if (menuTarget.playlistRef != null) {
                    MkwOptionRow(
                        headline = if (allowPlayNext) stringResource(R.string.track_action_add_to_queue)
                        else "${stringResource(R.string.track_action_add_to_queue)} (${stringResource(R.string.route_play_next_disabled)})",
                        leading = { Icon(Icons.Filled.AddToQueue, null, tint = if (allowPlayNext) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary) },
                        enabled = allowPlayNext,
                        onClick = {
                            val ref = menuTarget.playlistRef!!
                            onDismissMenu()
                            if (onAddToQueueRefs(listOf(ref))) {
                                scope.launch { snackbarHostState.showSnackbar(addedQueueText) }
                            }
                        },
                    )
                    MkwOptionRow(
                        headline = stringResource(R.string.track_action_add_to_playlist),
                        leading = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MkwKitTokens.Palette.ink) },
                        onClick = {
                            val ref = menuTarget.playlistRef!!
                            onDismissMenu()
                            onOpenPlaylistPicker(listOf(ref))
                        },
                    )
                }
                if (menuTarget.onPlaylistConfig != null) {
                    MkwOptionRow(
                        headline = stringResource(R.string.discovery_playlist_entry_config),
                        leading = { Icon(Icons.Filled.Tune, null, tint = MkwKitTokens.Palette.ink) },
                        onClick = {
                            val cfg = menuTarget.onPlaylistConfig
                            onDismissMenu()
                            cfg()
                        },
                    )
                }
                if (menuTarget.courseCodeForDetail != null && onOpenCourse != null) {
                    MkwOptionRow(
                        headline = stringResource(R.string.track_action_open_course),
                        leading = { Icon(Icons.Outlined.Album, null, tint = MkwKitTokens.Palette.ink) },
                        onClick = {
                            val code = menuTarget.courseCodeForDetail
                            onDismissMenu()
                            onOpenCourse(code)
                        },
                    )
                }
                if (menuTarget.editorTargetCode != null && onOpenFlowEditor != null) {
                    MkwOptionRow(
                        headline = stringResource(R.string.discovery_open_flow_editor),
                        leading = { Icon(Icons.Filled.Tune, null, tint = MkwKitTokens.Palette.ink) },
                        onClick = {
                            val editor = menuTarget.editorTargetCode
                            onDismissMenu()
                            onOpenFlowEditor(editor)
                        },
                    )
                }
                if (menuTarget.onPlaylistMoveUp != null && menuTarget.playlistCanMoveUp) {
                    MkwOptionRow(
                        headline = stringResource(R.string.discovery_move_up),
                        leading = { Icon(Icons.Filled.KeyboardArrowUp, null, tint = MkwKitTokens.Palette.ink) },
                        onClick = {
                            val act = menuTarget.onPlaylistMoveUp
                            onDismissMenu()
                            act()
                        },
                    )
                }
                if (menuTarget.onPlaylistMoveDown != null && menuTarget.playlistCanMoveDown) {
                    MkwOptionRow(
                        headline = stringResource(R.string.discovery_move_down),
                        leading = { Icon(Icons.Filled.KeyboardArrowDown, null, tint = MkwKitTokens.Palette.ink) },
                        onClick = {
                            val act = menuTarget.onPlaylistMoveDown
                            onDismissMenu()
                            act()
                        },
                    )
                }
                MkwOptionRow(
                    headline = stringResource(R.string.track_action_multi_select),
                    leading = { Icon(Icons.Outlined.CheckCircle, null, tint = MkwKitTokens.Palette.ink) },
                    onClick = {
                        val key = menuTarget.selectionKey
                        onDismissMenu()
                        onEnterMultiSelect(key)
                    },
                )
                MkwOptionRow(
                    headline = stringResource(R.string.track_action_info),
                    leading = { Icon(Icons.Outlined.Info, null, tint = MkwKitTokens.Palette.ink) },
                    onClick = {
                        val info = menuTarget
                        onDismissMenu()
                        onOpenInfo(info)
                    },
                )
                if (menuTarget.onPlaylistRemove != null) {
                    MkwDivider(Modifier.padding(vertical = 4.dp))
                    MkwOptionRow(
                        headline = stringResource(R.string.discovery_remove),
                        destructive = true,
                        leading = { Icon(Icons.Filled.DeleteOutline, null, tint = MkwKitTokens.Palette.danger) },
                        onClick = {
                            val rem = menuTarget.onPlaylistRemove
                            onDismissMenu()
                            rem()
                        },
                    )
                }
            }
        }
    }

    if (playlistPickerRefs != null) {
        val refsToAdd = playlistPickerRefs
        var creatingNew by remember { mutableStateOf(false) }
        var newListName by remember { mutableStateOf("") }
        MkwBottomSheet(
            onDismissRequest = onDismissPlaylistPicker,
            title = stringResource(R.string.track_action_add_to_playlist),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MkwSecondaryButton(
                    text = stringResource(R.string.track_action_new_playlist_and_add),
                    onClick = { creatingNew = true },
                    leadingIcon = Icons.Filled.Add,
                    size = MkwButtonSize.Hero,
                    modifier = Modifier.fillMaxWidth(),
                )
                MkwDivider()
                if (playlists.isEmpty()) {
                    Text(
                        stringResource(R.string.track_playlist_pick_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MkwKitTokens.Palette.inkSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            MkwOptionRow(
                                headline = pl.name,
                                supporting = pluralStringResource(R.plurals.tracks_count_plurals, pl.courseIds.size, pl.courseIds.size),
                                leading = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MkwKitTokens.Palette.crimson) },
                                onClick = {
                                    onAddToPlaylist(pl.id, refsToAdd)
                                    val msg = context.getString(R.string.track_action_added_to_playlist, refsToAdd.size, pl.name)
                                    onDismissPlaylistPicker()
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (creatingNew) {
            MkwDialog(
                onDismissRequest = { creatingNew = false },
                title = stringResource(R.string.track_action_new_playlist_and_add),
                confirmButton = {
                    MkwPrimaryButton(
                        text = stringResource(R.string.discovery_create),
                        enabled = newListName.isNotBlank(),
                        size = MkwButtonSize.Compact,
                        onClick = {
                            val cleanName = newListName.trim()
                            onCreatePlaylistAndAdd(cleanName, refsToAdd)
                            val msg = context.getString(R.string.track_action_added_to_playlist, refsToAdd.size, cleanName)
                            creatingNew = false
                            onDismissPlaylistPicker()
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                },
                dismissButton = {
                    MkwGhostButton(text = stringResource(R.string.discovery_cancel), onClick = { creatingNew = false })
                },
            ) {
                MkwTextField(
                    value = newListName,
                    onValueChange = { newListName = it.take(120) },
                    label = stringResource(R.string.discovery_playlist_name),
                    singleLine = true,
                )
            }
        }
    }

    if (infoTarget != null) {
        MkwDialog(
            onDismissRequest = onDismissInfo,
            title = stringResource(R.string.track_action_info),
            confirmButton = {
                MkwGhostButton(text = stringResource(R.string.discovery_cancel), onClick = onDismissInfo)
            },
        ) {
            Text(infoTarget.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
            if (infoTarget.subtitle.isNotBlank()) {
                Text(infoTarget.subtitle, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
            }
            MkwDivider()
            infoTarget.infoPairs.forEach { (labelRes, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MkwKitTokens.Palette.crimson,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MkwKitTokens.Palette.ink)
                }
            }
        }
    }
}

@Composable
private fun ExtraQueueActionsRow(
    count: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.tracks_count_plurals, count, count),
            style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
            color = MkwKitTokens.Palette.crimson,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        MkwRoseButton(
            text = stringResource(R.string.jukebox_play_all),
            onClick = onPlayAll,
            leadingIcon = Icons.Filled.PlayArrow,
            size = MkwButtonSize.Compact,
        )
        MkwSecondaryButton(
            text = stringResource(R.string.jukebox_shuffle),
            onClick = onShuffle,
            leadingIcon = Icons.Filled.Shuffle,
            size = MkwButtonSize.Compact,
        )
    }
}

private fun extraOtherToQueueEntry(
    item: ExtraMusicCatalog.OtherMusicItem,
    language: String,
    categoryLabel: String,
): com.mylittleblover.mkwplayer.player.LinearQueue.Entry {
    val title = item.localizedName(language)
    return when (item) {
        is ExtraMusicCatalog.OtherMusicItem.LinearAsset -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            assetSha256 = item.asset.sha256,
            loop = item.asset.loop,
            loopStart = item.asset.loopStart,
            loopEnd = item.asset.loopEnd,
        )
        is ExtraMusicCatalog.OtherMusicItem.OverlaidOpening -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            overlaidOpening = item,
        )
        is ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            chainedSequence = item,
        )
        is ExtraMusicCatalog.OtherMusicItem.InteractiveTitle -> com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
            key = "extra:${item.id}",
            title = title,
            subtitle = categoryLabel,
            sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
        )
    }
}

private fun extraSimpleToQueueEntry(
    item: ExtraMusicCatalog.SimpleTrackItem,
    language: String,
    categoryLabel: String,
): com.mylittleblover.mkwplayer.player.LinearQueue.Entry = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(
    key = "extra:${item.id}",
    title = item.localizedName(language),
    subtitle = categoryLabel,
    sourceType = com.mylittleblover.mkwplayer.player.LinearSessionStore.SourceType.EXTRA,
    ostIndex = item.ostIndex,
    durationMs = item.durationMs,
    artworkName = item.artwork,
    assetSha256 = item.asset.sha256,
    loop = item.asset.loop,
    loopStart = item.asset.loopStart,
    loopEnd = item.asset.loopEnd,
)

private fun formatExtraDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(java.util.Locale.ROOT, min, sec)
}

@Composable
private fun CoursePresetOptionsColumn(
    logicalCode: String,
    isCourse: Boolean,
    selectedPresetId: String?,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
    isPresetSelectable: (String, String?) -> Boolean,
    onSelectPreset: (String?) -> Unit,
    autoOptionLabel: String? = null,
) {
    val matchingPresets = presets.filter {
        it.courseCode.equals(logicalCode, ignoreCase = true) && isPresetSelectable(logicalCode, it.id)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (autoOptionLabel != null) {
            val isAutoSelected = selectedPresetId == null
            MkwOptionRow(
                headline = autoOptionLabel,
                selected = isAutoSelected,
                role = Role.RadioButton,
                onClick = { onSelectPreset(null) },
                trailing = { MkwRadioMark(selected = isAutoSelected) },
            )
        }
        val builtInRefs = listOf(
            PresetRefs.SYSTEM_DEFAULT,
            PresetRefs.SYSTEM_SUITE_A_ALT,
            PresetRefs.SYSTEM_SUITE_B,
            PresetRefs.SYSTEM_SUITE_C,
            PresetRefs.SYSTEM_SUITE_D,
            PresetRefs.SYSTEM_KNOCKOUT,
        )
        builtInRefs.forEach { ref ->
            val selectable = when (ref) {
                PresetRefs.SYSTEM_DEFAULT -> !isCourse || isPresetSelectable(logicalCode, ref)
                else -> isCourse && isPresetSelectable(logicalCode, ref)
            }
            val selected = if (autoOptionLabel == null && ref == PresetRefs.SYSTEM_DEFAULT) {
                selectedPresetId == null || selectedPresetId == PresetRefs.SYSTEM_DEFAULT
            } else {
                selectedPresetId == ref
            }
            if (selectable || selected) {
                MkwOptionRow(
                    headline = stringResource(com.mylittleblover.mkwplayer.ui.routes.builtInSuiteLabelRes(ref)),
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = {
                        onSelectPreset(
                            if (autoOptionLabel == null && ref == PresetRefs.SYSTEM_DEFAULT) null else ref,
                        )
                    },
                    trailing = { MkwRadioMark(selected = selected) },
                )
            }
        }
        matchingPresets.forEach { preset ->
            val selected = selectedPresetId == preset.id
            MkwOptionRow(
                headline = preset.name,
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelectPreset(preset.id) },
                trailing = { MkwRadioMark(selected = selected) },
            )
        }
    }
}

@Composable
private fun resolveStopOrEntryPresetLabel(
    targetCode: String,
    isCourse: Boolean,
    presetId: String?,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
    isPresetSelectable: (String, String?) -> Boolean,
    autoResolvedRef: String? = null,
): String {
    if (presetId == null && autoResolvedRef != null) {
        val resolvedLabel = stringResource(com.mylittleblover.mkwplayer.ui.routes.builtInSuiteLabelRes(autoResolvedRef))
        return stringResource(R.string.route_stop_preset_auto_fmt, resolvedLabel)
    }
    val builtInRefs = setOf(
        PresetRefs.SYSTEM_DEFAULT,
        PresetRefs.SYSTEM_SUITE_A_ALT,
        PresetRefs.SYSTEM_SUITE_B,
        PresetRefs.SYSTEM_SUITE_C,
        PresetRefs.SYSTEM_SUITE_D,
        PresetRefs.SYSTEM_KNOCKOUT,
    )
    return when {
        presetId == null || presetId == PresetRefs.SYSTEM_DEFAULT ->
            stringResource(R.string.preset_system_default)
        presetId in builtInRefs && isCourse && isPresetSelectable(targetCode, presetId) ->
            stringResource(com.mylittleblover.mkwplayer.ui.routes.builtInSuiteLabelRes(presetId))
        else -> presets.firstOrNull {
            it.id == presetId && it.courseCode.equals(targetCode, ignoreCase = true)
        }?.name ?: stringResource(R.string.preset_system_default)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryCustomRouteDetail(
    route: CustomRoute?,
    catalog: BrowseCatalog?,
    language: String,
    presets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
    installedJukebox: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game>>,
    packDirectory: java.io.File?,
    courseArtwork: Map<String, String>,
    report: RoadJukeboxCatalog.RouteValidationReport?,
    onPlayRoute: (CustomRoute) -> Unit,
    onEditOnMap: (CustomRoute) -> Unit,
    onToggleLoop: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemoveLastStop: () -> Unit,
    onUpdateStopPreset: (Int, String?) -> Unit,
    onUpdateEdgeMusic: (Int, Boolean, String?) -> Unit,
    onOpenCourse: (String) -> Unit,
    onOpenFlowEditor: (String, String?) -> Unit,
    isPresetSelectable: (String, String?) -> Boolean = { _, ref -> ref != PresetRefs.SYSTEM_KNOCKOUT },
    onUpdateRuleMode: (RoadJukeboxCatalog.RoadMusicRuleMode) -> Unit = {},
    onUpdateSuiteMode: (RoadJukeboxCatalog.RouteSuiteMode) -> Unit = {},
    onUpdatePureFreeRoamConfig: (RoadJukeboxCatalog.PureFreeRoamConfig) -> Unit = {},
    onUpdateEdgeFreeRoamOverride: (Int, RoadJukeboxCatalog.EdgeFreeRoamOverride?) -> Unit = { _, _ -> },
    resolveAutoStopPresetRef: (stageCode: String, stopIndex: Int, stopCount: Int, suiteMode: RoadJukeboxCatalog.RouteSuiteMode) -> String = { code, idx, count, mode ->
        PresetRefs.forSuiteVariant(
            RoadJukeboxCatalog.resolveSuiteVariantForStop(null, mode, idx, count, code, route?.roadRuleMode),
        )
    },
) {
    if (route == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.discovery_empty))
        }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable(route.id) { mutableStateOf(route.name) }
    var editingStopIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingEdgeIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedFreeRoamLeg by rememberSaveable(route.id) { mutableStateOf<Int?>(null) }
    val pfrConfig = route.pureFreeRoamConfig
    val useGameFreeRoamPool = pfrConfig.enabled &&
        pfrConfig.trackPool == RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM
    val presetSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (renaming) {
        MkwDialog(
            onDismissRequest = { renaming = false },
            title = stringResource(R.string.route_rename),
            confirmButton = {
                MkwPrimaryButton(
                    text = stringResource(R.string.route_save_changes),
                    onClick = { onRename(newName); renaming = false },
                    enabled = newName.isNotBlank(),
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(text = stringResource(R.string.discovery_cancel), onClick = { renaming = false })
            },
        ) {
            MkwTextField(
                value = newName,
                onValueChange = { newName = it.take(120) },
                label = stringResource(R.string.route_name_label),
                singleLine = true,
            )
        }
    }

    if (deleting) {
        MkwDialog(
            onDismissRequest = { deleting = false },
            title = stringResource(R.string.route_delete_confirm_title),
            confirmButton = {
                MkwDangerButton(
                    text = stringResource(R.string.route_delete),
                    onClick = { deleting = false; onDelete() },
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(text = stringResource(R.string.discovery_cancel), onClick = { deleting = false })
            },
        ) {
            Text(route.name, style = MaterialTheme.typography.bodyMedium, color = MkwKitTokens.Palette.ink)
        }
    }

    val stopPresetIdx = editingStopIndex
    if (stopPresetIdx != null && stopPresetIdx in route.stops.indices) {
        val stop = route.stops[stopPresetIdx]
        val logicalCode = stop.stageCode.removeSuffix("_00")
        val courseTitle = catalog?.displayName(logicalCode, language) ?: logicalCode
        val autoRef = resolveAutoStopPresetRef(logicalCode, stopPresetIdx, route.stops.size, route.routeSuiteMode)
        val autoOptionLabel = stringResource(
            R.string.route_stop_preset_auto_fmt,
            stringResource(com.mylittleblover.mkwplayer.ui.routes.builtInSuiteLabelRes(autoRef)),
        )
        MkwBottomSheet(
            onDismissRequest = { editingStopIndex = null },
            title = stringResource(R.string.route_stop_preset_title, stopPresetIdx + 1, courseTitle),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.discovery_playlist_entry_preset),
                    style = MaterialTheme.typography.labelLarge,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CoursePresetOptionsColumn(
                        logicalCode = logicalCode,
                        isCourse = true,
                        selectedPresetId = stop.presetId,
                        presets = presets,
                        isPresetSelectable = isPresetSelectable,
                        onSelectPreset = {
                            onUpdateStopPreset(stopPresetIdx, it)
                            editingStopIndex = null
                        },
                        autoOptionLabel = autoOptionLabel,
                    )
                    Spacer(Modifier.height(6.dp))
                    MkwSecondaryButton(
                        text = stringResource(R.string.preset_new),
                        onClick = {
                            editingStopIndex = null
                            onOpenFlowEditor(logicalCode, stop.presetId)
                        },
                        leadingIcon = Icons.Filled.Edit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    val pickEdgeIdx = pickingEdgeIndex
    val activeLeg = if (pickEdgeIdx != null) report?.legs?.getOrNull(pickEdgeIdx) else null
    if (activeLeg != null && pickEdgeIdx != null) {
        val edgeCfg = route.edgeFor(pickEdgeIdx)
        val fromTitle = catalog?.displayName(activeLeg.from, language) ?: activeLeg.from
        val toTitle = catalog?.displayName(activeLeg.to, language) ?: activeLeg.to
        RouteEdgeMusicPickerSheet(
            legTitle = "$fromTitle → $toTitle",
            leg = activeLeg,
            installedJukebox = installedJukebox,
            allowCustomSubstitute = true,
            allowMute = true,
            sessionHint = false,
            language = language,
            packDirectory = packDirectory,
            currentSelectedKey = edgeCfg?.selectedJukeboxKey,
            currentMuted = edgeCfg?.musicEnabled == false,
            onSelectAuto = { onUpdateEdgeMusic(pickEdgeIdx, true, null) },
            onSelectMuted = { onUpdateEdgeMusic(pickEdgeIdx, false, null) },
            onSelectTrackKey = { trackKey -> onUpdateEdgeMusic(pickEdgeIdx, true, trackKey) },
            onDismiss = { pickingEdgeIndex = null },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MkwCard(
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(route.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                        val legCount = (route.stops.size - 1).coerceAtLeast(0)
                        Text(
                            "${pluralStringResource(R.plurals.route_stops_plurals, route.stops.size, route.stops.size)} · ${pluralStringResource(R.plurals.route_legs_plurals, legCount, legCount)}",
                            style = MkwKitTokens.tabular(MaterialTheme.typography.bodyMedium),
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                    Box {
                        MkwIconButton(
                            icon = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.jukebox_more_actions),
                            onClick = { menuOpen = true },
                        )
                        MkwDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MkwDropdownItem(
                                text = stringResource(R.string.route_rename),
                                leadingIcon = Icons.Filled.Edit,
                                onClick = { menuOpen = false; newName = route.name; renaming = true },
                            )
                            MkwDropdownItem(
                                text = stringResource(R.string.route_delete),
                                leadingIcon = Icons.Filled.DeleteOutline,
                                destructive = true,
                                onClick = { menuOpen = false; deleting = true },
                            )
                        }
                    }
                }

                if (report?.canPlay == false) {
                    MkwCard(
                        tone = MkwSurfaceTone.White,
                        error = true,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.WarningAmber, null, tint = MkwKitTokens.Palette.danger)
                            Text(
                                com.mylittleblover.mkwplayer.ui.routes.localizedRouteBlockingReason(
                                    report,
                                    R.string.route_invalid_topology,
                                ) { code -> catalog?.displayName(code, language) ?: code },
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.danger,
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.route_play),
                        onClick = { onPlayRoute(route) },
                        enabled = report?.canPlay != false,
                        leadingIcon = Icons.Filled.PlayArrow,
                        modifier = Modifier.weight(1f),
                    )
                    MkwSecondaryButton(
                        text = stringResource(R.string.route_edit_on_map),
                        onClick = { onEditOnMap(route) },
                        leadingIcon = Icons.Filled.Map,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.route_loop_whole), style = MaterialTheme.typography.titleSmall, color = MkwKitTokens.Palette.ink)
                        Text(
                            stringResource(R.string.route_loop_whole_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                    MkwSwitch(checked = route.loopWholeRoute, onCheckedChange = onToggleLoop)
                }

                com.mylittleblover.mkwplayer.ui.routes.PureFreeRoamConfigCard(
                    config = pfrConfig,
                    onConfigChange = onUpdatePureFreeRoamConfig,
                    compact = true,
                )

                val routeReducedMotion = rememberReducedMotion()
                AnimatedVisibility(
                    visible = !useGameFreeRoamPool,
                    enter = MkwMotion.panelEnter(routeReducedMotion),
                    exit = MkwMotion.panelExit(routeReducedMotion),
                ) {
                    RoadMusicRuleModeSelector(
                        selectedMode = route.roadRuleMode,
                        officialKind = null,
                        onSelectMode = onUpdateRuleMode,
                    )
                }

                AnimatedVisibility(
                    visible = !pfrConfig.enabled,
                    enter = MkwMotion.panelEnter(routeReducedMotion),
                    exit = MkwMotion.panelExit(routeReducedMotion),
                ) {
                    com.mylittleblover.mkwplayer.ui.routes.RouteSuiteModeSelector(
                        selectedMode = route.routeSuiteMode,
                        onSelectMode = onUpdateSuiteMode,
                    )
                }

                Text(
                    stringResource(R.string.route_tail_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }
        }

        itemsIndexed(route.stops, key = { idx, stop -> "route-stop:$idx:${stop.stageCode}" }) { idx, stop ->
            val logical = stop.stageCode.removeSuffix("_00")
            val stopTitle = catalog?.displayName(logical, language) ?: logical
            val autoResolvedRef = if (stop.presetId == null) {
                resolveAutoStopPresetRef(logical, idx, route.stops.size, route.routeSuiteMode)
            } else null
            val presetLabel = resolveStopOrEntryPresetLabel(
                targetCode = logical,
                isCourse = true,
                presetId = stop.presetId,
                presets = presets,
                isPresetSelectable = isPresetSelectable,
                autoResolvedRef = autoResolvedRef,
            )
            val artwork = courseArtwork[logical] ?: courseArtwork["${logical}_00"]
            val isLastRemovableTail = idx == route.stops.lastIndex && route.stops.size > 2
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MkwCard(
                    onClick = { onOpenCourse(logical) },
                    tone = MkwSurfaceTone.Porcelain,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "${idx + 1}",
                            style = MkwKitTokens.tabular(MaterialTheme.typography.titleMedium),
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.crimson,
                        )
                        PackArtwork.Thumbnail(
                            packDirectory,
                            listOfNotNull(artwork),
                            Modifier.size(48.dp).clip(MkwKitTokens.Shape.thumb),
                        ) {
                            CourseCover(logical, Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stopTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!pfrConfig.enabled) {
                                Text(presetLabel, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.crimson, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (!pfrConfig.enabled) {
                            MkwIconButton(
                                icon = Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.discovery_playlist_entry_preset),
                                onClick = { editingStopIndex = idx },
                                visualSize = 36.dp,
                                iconSize = 20.dp,
                            )
                        }
                        if (isLastRemovableTail) {
                            MkwIconButton(
                                icon = Icons.Filled.RemoveCircleOutline,
                                contentDescription = stringResource(R.string.route_tail_delete_stop),
                                onClick = onRemoveLastStop,
                                visualSize = 36.dp,
                                iconSize = 20.dp,
                                tintOverride = MkwKitTokens.Palette.danger,
                            )
                        }
                    }
                }

                val leg = report?.legs?.getOrNull(idx)
                val edgeCfg = if (idx < route.stops.lastIndex) route.edgeFor(idx) else null
                if (idx < route.stops.lastIndex) {
                    val nextLogical = route.stops[idx + 1].stageCode.removeSuffix("_00")
                    val fromName = catalog?.displayName(logical, language) ?: logical
                    val toName = catalog?.displayName(nextLogical, language) ?: nextLogical
                    val summary = when {
                        useGameFreeRoamPool && edgeCfg?.musicEnabled == false ->
                            stringResource(R.string.route_edge_music_muted)
                        useGameFreeRoamPool ->
                            pureFreeRoamGamePoolLegSummary(pfrConfig.freeRoamMode)
                        leg != null ->
                            edgeResolutionSummary(leg.resolution, language)
                        else ->
                            stringResource(R.string.route_edge_music_auto)
                    }
                    val showLegError = !useGameFreeRoamPool && leg?.resolution?.isMissingOrIncomplete() == true
                    MkwCard(
                        onClick = { pickingEdgeIndex = idx },
                        tone = MkwSurfaceTone.White,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (edgeCfg?.musicEnabled != false) Icons.Outlined.MusicNote else Icons.Filled.MusicOff,
                                null,
                                Modifier.size(18.dp),
                                tint = if (showLegError || leg?.connectionValid == false) {
                                    MkwKitTokens.Palette.danger
                                } else {
                                    MkwKitTokens.Palette.crimson
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${stringResource(R.string.route_road_prefix)} $fromName → $toName",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MkwKitTokens.Palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (showLegError) {
                                        MkwKitTokens.Palette.danger
                                    } else {
                                        MkwKitTokens.Palette.inkSecondary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (pfrConfig.enabled) {
                                com.mylittleblover.mkwplayer.ui.routes.PureFreeRoamLegOverrideChip(
                                    config = pfrConfig,
                                    override = edgeCfg?.toFreeRoamOverride(),
                                    expanded = expandedFreeRoamLeg == idx,
                                    onClick = {
                                        expandedFreeRoamLeg = if (expandedFreeRoamLeg == idx) null else idx
                                    },
                                )
                            } else {
                                Icon(Icons.Filled.Tune, null, Modifier.size(18.dp), tint = MkwKitTokens.Palette.inkSecondary)
                            }
                        }
                        if (pfrConfig.enabled) {
                            com.mylittleblover.mkwplayer.ui.routes.PureFreeRoamLegInlineEditor(
                                visible = expandedFreeRoamLeg == idx,
                                config = pfrConfig,
                                override = edgeCfg?.toFreeRoamOverride(),
                                onOverrideChange = { ov -> onUpdateEdgeFreeRoamOverride(idx, ov) },
                            )
                        }
                    }
                }
            }
        }
    }
}
