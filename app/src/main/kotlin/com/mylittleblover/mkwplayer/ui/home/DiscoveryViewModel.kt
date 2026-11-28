package com.mylittleblover.mkwplayer.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mylittleblover.mkwplayer.library.DailySelectionStore
import com.mylittleblover.mkwplayer.library.FavoriteStore
import com.mylittleblover.mkwplayer.library.RecommendationCandidate
import com.mylittleblover.mkwplayer.library.millisUntilNextMidnight
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    enum class Error { FAVORITES_READ, FAVORITES_WRITE, DAILY_SELECTION, PLAYLISTS, RECENT }
    data class State(
        val favorites: Set<String> = emptySet(), val day: LocalDate = LocalDate.now(),
        val dailyIds: List<String> = emptyList(), val favoritesReady: Boolean = false, val dailyReady: Boolean = false,
        val favoritesError: Error? = null, val dailyError: Error? = null,
        val playlists: List<com.mylittleblover.mkwplayer.library.UserPlaylist> = emptyList(), val playlistError: Error? = null,
        val presets: List<com.mylittleblover.mkwplayer.library.CoursePreset> = emptyList(), val presetError: Error? = null,
        val customRoutes: List<com.mylittleblover.mkwplayer.routes.CustomRoute> = emptyList(), val customRouteError: Error? = null,
        val officialRouteFreeRoam: Map<String, com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry> = emptyMap(),
        val recentIds: List<String> = emptyList(), val recentError: Error? = null,
    ) {
        val error: Error? get() = favoritesError ?: dailyError ?: playlistError ?: presetError ?: customRouteError ?: recentError
    }
    private val favorites = FavoriteStore(File(application.filesDir, "user-favorites.json"))
    private val recent = com.mylittleblover.mkwplayer.library.RecentCourseStore(File(application.filesDir, "recent-courses.json"))
    private val recentOperations = Mutex()
    private val playlists = com.mylittleblover.mkwplayer.library.PlaylistStore(File(application.filesDir, "user-playlists.json"))
    private val playlistOperations = Mutex()
    private val presetStore = com.mylittleblover.mkwplayer.library.CoursePresetStore(File(application.filesDir, "course-presets.json"))
    private val presetOperations = Mutex()
    private val customRouteStore = com.mylittleblover.mkwplayer.routes.CustomRouteStore(File(application.filesDir, "routes/custom_routes.json"))
    private val customRouteOperations = Mutex()
    private val officialFreeRoamPrefs = com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamPrefs(File(application.filesDir, "routes/official_free_roam_prefs.json"))
    private val officialFreeRoamOperations = Mutex()
    private val connections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? by lazy {
        runCatching {
            getApplication<Application>().assets.open("stage_connections.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.library.StageConnections.parse(it.readText())
            }
        }.getOrNull()
    }
    private val daily = DailySelectionStore(File(application.filesDir, "daily-selection.json"))
    private val candidates = MutableStateFlow<List<RecommendationCandidate>?>(null)
    private val favoriteOperations = Mutex()
    private val day = MutableStateFlow(LocalDate.now())
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    val playStats = com.mylittleblover.mkwplayer.library.PlayStatsRepository.stats
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshDate() }
    }
    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) application.registerReceiver(dateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else application.registerReceiver(dateReceiver, filter)
        reloadFavorites()
        updatePlaylists { playlists.read() }
        updateRecent { recent.read() }
        reloadPresets()
        updateCustomRoutes { customRouteStore.read() }
        reloadOfficialRouteFreeRoam()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                com.mylittleblover.mkwplayer.library.PlayStatsRepository.ensureLoadedSync(application)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshDate()
                delay(millisUntilNextMidnight(ZonedDateTime.now()).coerceAtMost(60_000L))
            }
        }
        viewModelScope.launch {
            combine(day, candidates) { date, pool -> pool?.let { date to it } }.filterNotNull().collectLatest { (date, pool) ->
                val selected = withContext(Dispatchers.IO) { daily.select(date, pool) }
                selected.onSuccess { ids -> _state.update { it.copy(day = date, dailyIds = ids, dailyReady = true, dailyError = null) } }
                    .onFailure { _state.update { it.copy(day = date, dailyReady = true, dailyError = Error.DAILY_SELECTION) } }
            }
        }
    }
    fun updateEligible(values: List<RecommendationCandidate>) { candidates.value = values.toList() }
    fun refreshDate() { day.value = LocalDate.now() }
    fun reloadAllAfterBackup() {
        reloadFavorites()
        updatePlaylists { playlists.read() }
        updateRecent { recent.read() }
        reloadPresets()
        updateCustomRoutes { customRouteStore.read() }
        reloadOfficialRouteFreeRoam()
        com.mylittleblover.mkwplayer.library.PlayStatsRepository.reload(getApplication())
    }
    fun reloadFavorites() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            favoriteOperations.withLock {
                val loaded = withContext(Dispatchers.IO) { favorites.read() }
                loaded.onSuccess { value -> _state.update { it.copy(favorites = value, favoritesReady = true, favoritesError = null) } }
                    .onFailure { _state.update { it.copy(favoritesReady = true, favoritesError = Error.FAVORITES_READ) } }
            }
        }
    }
    fun toggleFavorite(id: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                favoriteOperations.withLock {
                    val result = withContext(Dispatchers.IO) { favorites.toggle(id) }
                    result.onSuccess { value -> _state.update { it.copy(favorites = value, favoritesReady = true, favoritesError = null) } }
                        .onFailure { _state.update { it.copy(favoritesError = Error.FAVORITES_WRITE) } }
                }
            }
        }
    }
    private fun updateRecent(operation: () -> Result<List<String>>) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                recentOperations.withLock {
                    val result = withContext(Dispatchers.IO) { operation() }
                    result.onSuccess { value -> _state.update { it.copy(recentIds = value, recentError = null) } }
                        .onFailure { _state.update { it.copy(recentError = Error.RECENT) } }
                }
            }
        }
    }
    fun reloadRecent() = updateRecent { recent.read() }

    fun recordRecent(rawRefId: String) {
        val trimmed = rawRefId.trim()
        if (trimmed.isEmpty()) return
        val canonical = if (
            trimmed.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_JUKEBOX) ||
            trimmed.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_SFC) ||
            trimmed.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_BATTLE) ||
            trimmed.startsWith(com.mylittleblover.mkwplayer.library.PlaylistItemRef.PREFIX_EXTRA)
        ) {
            trimmed
        } else {
            trimmed.removeSuffix("_00")
        }
        updateRecent { recent.record(canonical) }
    }
    private fun updatePlaylists(operation: () -> Result<List<com.mylittleblover.mkwplayer.library.UserPlaylist>>) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                playlistOperations.withLock {
                    val result = withContext(Dispatchers.IO) { operation() }
                    result.onSuccess { value -> _state.update { it.copy(playlists = value, playlistError = null) } }
                        .onFailure { _state.update { it.copy(playlistError = Error.PLAYLISTS) } }
                }
            }
        }
    }
    fun createPlaylist(name: String) = updatePlaylists { playlists.create(name) }
    fun createPlaylistAndAdd(name: String, codes: List<String>) = updatePlaylists { playlists.createWithCourses(name, codes) }
    fun addToPlaylist(id: String, code: String) = updatePlaylists { playlists.addCourse(id, code) }
    fun addManyToPlaylist(id: String, codes: List<String>) = updatePlaylists { playlists.addCourses(id, codes) }
    fun removeFromPlaylist(id: String, index: Int) = updatePlaylists { playlists.removeCourse(id, index) }
    fun removeManyFromPlaylist(id: String, indices: Collection<Int>) = updatePlaylists { playlists.removeCourses(id, indices) }
    fun renamePlaylist(id: String, name: String) = updatePlaylists { playlists.rename(id, name) }
    fun moveInPlaylist(id: String, from: Int, to: Int) = updatePlaylists { playlists.moveCourse(id, from, to) }
    fun setPlaylistNativeTransitionDefault(id: String, enabled: Boolean) = updatePlaylists {
        playlists.setNativeTransitionDefault(id, enabled)
    }
    fun setPlaylistEntryPreset(id: String, index: Int, presetId: String?) = updatePlaylists {
        playlists.setEntryPreset(id, index, presetId)
    }
    fun setPlaylistEntryNativeTransition(id: String, index: Int, enabled: Boolean?) = updatePlaylists {
        playlists.setEntryNativeTransition(id, index, enabled)
    }
    fun deletePlaylist(id: String) = updatePlaylists { playlists.delete(id) }

    fun reloadPresets() {
        viewModelScope.launch {
            val loaded = presetOperations.withLock {
                withContext(Dispatchers.IO) { presetStore.read() }
            }
            _state.value = loaded.fold(
                onSuccess = { _state.value.copy(presets = it, presetError = null) },
                onFailure = { _state.value.copy(presetError = Error.PLAYLISTS) },
            )
        }
    }

    fun playlistsReferencingPreset(presetId: String): List<com.mylittleblover.mkwplayer.library.UserPlaylist> =
        _state.value.playlists.filter { playlist -> playlist.entryConfigs.any { it.presetId == presetId } }

    fun savePreset(
        preset: com.mylittleblover.mkwplayer.library.CoursePreset,
        onComplete: (Result<com.mylittleblover.mkwplayer.library.CoursePreset>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = presetOperations.withLock {
                withContext(Dispatchers.IO) {
                    presetStore.save(preset).map { list ->
                        val cleanId = if (preset.id.startsWith(com.mylittleblover.mkwplayer.library.PresetRefs.USER_PREFIX)) {
                            preset.id
                        } else {
                            "${com.mylittleblover.mkwplayer.library.PresetRefs.USER_PREFIX}${preset.id}"
                        }
                        list to list.first { it.id == cleanId }
                    }
                }
            }
            result.fold(
                onSuccess = { (list, saved) ->
                    _state.value = _state.value.copy(presets = list, presetError = null)
                    onComplete(Result.success(saved))
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(presetError = Error.PLAYLISTS)
                    onComplete(Result.failure(err))
                },
            )
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            val (presetResult, playlistResult, routeResult) = presetOperations.withLock {
                playlistOperations.withLock {
                    customRouteOperations.withLock {
                        withContext(Dispatchers.IO) {
                            val cleanedPlaylists = playlists.clearPresetReferences(id)
                            val cleanedRoutes = customRouteStore.clearPresetReferences(id)
                            val updatedPresets = presetStore.delete(id)
                            Triple(updatedPresets, cleanedPlaylists, cleanedRoutes)
                        }
                    }
                }
            }
            _state.value = _state.value.copy(
                presets = presetResult.getOrElse { _state.value.presets },
                presetError = if (presetResult.isFailure) Error.PLAYLISTS else null,
                playlists = playlistResult.getOrElse { _state.value.playlists },
                playlistError = if (playlistResult.isFailure) Error.PLAYLISTS else _state.value.playlistError,
                customRoutes = routeResult.getOrElse { _state.value.customRoutes },
                customRouteError = if (routeResult.isFailure) Error.PLAYLISTS else _state.value.customRouteError,
            )
        }
    }

    private fun updateCustomRoutes(
        operation: () -> Result<List<com.mylittleblover.mkwplayer.routes.CustomRoute>>,
    ) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                customRouteOperations.withLock {
                    val result = withContext(Dispatchers.IO) { operation() }
                    result.onSuccess { list ->
                        _state.update { it.copy(customRoutes = list, customRouteError = null) }
                    }.onFailure {
                        _state.update { it.copy(customRouteError = Error.PLAYLISTS) }
                    }
                }
            }
        }
    }

    fun reloadCustomRoutes() = updateCustomRoutes { customRouteStore.read() }

    fun saveCustomRoute(
        routeId: String?,
        name: String,
        stops: List<com.mylittleblover.mkwplayer.routes.CustomRouteStop>,
        edges: List<com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig>,
        loopWholeRoute: Boolean,
        roadRuleMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.DEFAULT_ROAD_RULE_MODE,
        routeSuiteMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteSuiteMode =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
        pureFreeRoamConfig: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamConfig =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamConfig(),
        onComplete: (Result<com.mylittleblover.mkwplayer.routes.CustomRoute>) -> Unit = {},
    ) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                val res = customRouteOperations.withLock {
                    withContext(Dispatchers.IO) {
                        val conn = connections ?: return@withContext Result.failure<com.mylittleblover.mkwplayer.routes.CustomRoute>(
                            IllegalStateException("Stage connections catalog unavailable"),
                        )
                        if (routeId == null) {
                            customRouteStore.create(
                                name = name,
                                stops = stops,
                                edges = edges,
                                connections = conn,
                                loopWholeRoute = loopWholeRoute,
                                roadRuleMode = roadRuleMode,
                                routeSuiteMode = routeSuiteMode,
                                pureFreeRoamConfig = pureFreeRoamConfig,
                            )
                        } else {
                            customRouteStore.saveDraftOver(
                                existingId = routeId,
                                name = name,
                                draftStops = stops,
                                draftEdges = edges,
                                connections = conn,
                                loopWholeRoute = loopWholeRoute,
                                roadRuleMode = roadRuleMode,
                                routeSuiteMode = routeSuiteMode,
                                pureFreeRoamConfig = pureFreeRoamConfig,
                            )
                        }
                    }
                }
                res.fold(
                    onSuccess = { saved ->
                        val refreshed = withContext(Dispatchers.IO) { customRouteStore.read() }
                        refreshed.onSuccess { list ->
                            _state.update { it.copy(customRoutes = list, customRouteError = null) }
                        }
                        onComplete(Result.success(saved))
                    },
                    onFailure = { err ->
                        _state.update { it.copy(customRouteError = Error.PLAYLISTS) }
                        onComplete(Result.failure(err))
                    },
                )
            }
        }
    }

    fun renameCustomRoute(id: String, name: String) = updateCustomRoutes { customRouteStore.rename(id, name) }
    fun deleteCustomRoute(id: String) = updateCustomRoutes { customRouteStore.delete(id) }
    fun appendCustomRouteStop(id: String, stageCode: String) = updateCustomRoutes {
        val conn = connections ?: return@updateCustomRoutes Result.failure(IllegalStateException("Stage connections catalog unavailable"))
        customRouteStore.appendStop(id, stageCode, conn)
    }
    fun removeCustomRouteLastStop(id: String) = updateCustomRoutes { customRouteStore.removeLastStop(id) }
    fun setCustomRouteStopPreset(id: String, stopIndex: Int, presetId: String?) =
        updateCustomRoutes { customRouteStore.setStopPreset(id, stopIndex, presetId) }
    fun setCustomRouteEdgeMusic(id: String, edgeIndex: Int, playMusic: Boolean, selectedJukeboxKey: String?) =
        updateCustomRoutes { customRouteStore.setEdgeMusic(id, edgeIndex, playMusic, selectedJukeboxKey) }
    fun setCustomRouteLoop(id: String, loop: Boolean) = updateCustomRoutes { customRouteStore.setLoopWholeRoute(id, loop) }
    fun setCustomRouteRuleMode(
        id: String,
        mode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode,
    ) = updateCustomRoutes { customRouteStore.setRoadRuleMode(id, mode) }

    fun setCustomRouteSuiteMode(
        id: String,
        mode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteSuiteMode,
    ) = updateCustomRoutes { customRouteStore.setRouteSuiteMode(id, mode) }

    fun setCustomRoutePureFreeRoamConfig(
        id: String,
        config: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamConfig,
    ) = updateCustomRoutes { customRouteStore.setPureFreeRoamConfig(id, config) }

    fun setCustomRouteEdgeFreeRoamOverride(
        id: String,
        edgeIndex: Int,
        override: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeFreeRoamOverride?,
    ) = updateCustomRoutes {
        customRouteStore.setEdgeFreeRoamOverride(
            id = id,
            edgeIndex = edgeIndex,
            trackCountOverride = override?.trackCountOverride,
            durationMinutesOverride = override?.durationMinutesOverride,
        )
    }

    fun reloadOfficialRouteFreeRoam() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            officialFreeRoamOperations.withLock {
                val loaded = withContext(Dispatchers.IO) { officialFreeRoamPrefs.readAll() }
                loaded.onSuccess { map -> _state.update { it.copy(officialRouteFreeRoam = map) } }
            }
        }
    }

    fun officialRouteFreeRoamEntry(routeId: String): com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry =
        _state.value.officialRouteFreeRoam[routeId]
            ?: com.mylittleblover.mkwplayer.routes.OfficialRouteFreeRoamEntry()

    fun setOfficialRouteFreeRoamConfig(
        routeId: String,
        config: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamConfig,
    ) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                officialFreeRoamOperations.withLock {
                    val res = withContext(Dispatchers.IO) { officialFreeRoamPrefs.setConfig(routeId, config) }
                    res.onSuccess { entry ->
                        val key = routeId.trim()
                        _state.update { it.copy(officialRouteFreeRoam = it.officialRouteFreeRoam + (key to entry)) }
                    }
                }
            }
        }
    }

    fun setOfficialRouteFreeRoamEdgeOverride(
        routeId: String,
        edgeIndex: Int,
        override: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeFreeRoamOverride?,
    ) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                officialFreeRoamOperations.withLock {
                    val res = withContext(Dispatchers.IO) { officialFreeRoamPrefs.setEdgeOverride(routeId, edgeIndex, override) }
                    res.onSuccess { entry ->
                        val key = routeId.trim()
                        _state.update { it.copy(officialRouteFreeRoam = it.officialRouteFreeRoam + (key to entry)) }
                    }
                }
            }
        }
    }

    fun duplicatePreset(id: String, newName: String) {
        viewModelScope.launch {
            val res: Result<List<com.mylittleblover.mkwplayer.library.CoursePreset>> = presetOperations.withLock {
                withContext(Dispatchers.IO) {
                    presetStore.duplicate(id, newName).mapCatching {
                        presetStore.read().getOrThrow()
                    }
                }
            }
            res.fold(
                onSuccess = { _state.value = _state.value.copy(presets = it, presetError = null) },
                onFailure = { _state.value = _state.value.copy(presetError = Error.PLAYLISTS) },
            )
        }
    }
    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(dateReceiver)
        super.onCleared()
    }
}
