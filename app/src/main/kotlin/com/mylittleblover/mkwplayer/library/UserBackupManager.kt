package com.mylittleblover.mkwplayer.library

import android.content.Context
import com.mylittleblover.mkwplayer.audio.AudioEffectSettings
import com.mylittleblover.mkwplayer.audio.EqualizerPreset
import com.mylittleblover.mkwplayer.audio.MasterDspStateConfig
import com.mylittleblover.mkwplayer.audio.OutputTrim
import com.mylittleblover.mkwplayer.audio.OutputTrimSettings
import com.mylittleblover.mkwplayer.audio.PlaybackBehaviorConfig
import com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings
import com.mylittleblover.mkwplayer.audio.PlaybackRateConfig
import com.mylittleblover.mkwplayer.routes.CustomRoute
import com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig
import com.mylittleblover.mkwplayer.routes.CustomRouteStop
import com.mylittleblover.mkwplayer.routes.CustomRouteStore
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal enum class BackupRestoreMode {
    MERGE,
    REPLACE,
}

internal data class BackupPreferencesSnapshot(
    val outputTrimGainDb: Double = OutputTrim.default.gainDb,
    val outputTrimCeilingDb: Double = OutputTrim.default.ceilingDb,
    val segmentSwitchFadeOutMs: Int = 0,
    val perfectBeatEnabled: Boolean = false,
    val liveVisualizerEnabled: Boolean = false,
    val dspConfig: MasterDspStateConfig = MasterDspStateConfig(),
    val rateConfig: PlaybackRateConfig = PlaybackRateConfig(),
)

internal data class UserBackupPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val appVersion: String = "",
    val playlists: List<UserPlaylist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val presets: List<CoursePreset> = emptyList(),
    val customRoutes: List<CustomRoute> = emptyList(),
    val recentCourses: List<String> = emptyList(),
    val playStats: List<TrackPlayStat> = emptyList(),
    val preferences: BackupPreferencesSnapshot = BackupPreferencesSnapshot(),
    val markers: List<TrackMarker> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

internal data class BackupRestoreSummary(
    val playlistsCount: Int,
    val favoritesCount: Int,
    val presetsCount: Int,
    val routesCount: Int,
    val recentCount: Int,
    val statsCount: Int,
    val markersCount: Int = 0,
)

internal object UserBackupManager {

    fun exportToJson(
        playlists: List<UserPlaylist>,
        favorites: Set<String>,
        presets: List<CoursePreset>,
        customRoutes: List<CustomRoute>,
        recentCourses: List<String>,
        playStats: Map<String, TrackPlayStat>,
        preferences: BackupPreferencesSnapshot,
        appVersion: String = "",
        exportedAtEpochMs: Long = System.currentTimeMillis(),
        markers: List<TrackMarker> = emptyList(),
    ): String {
        val playlistsArr = JSONArray()
        playlists.forEach { playlist ->
            val cfgArr = JSONArray()
            playlist.entryConfigs.forEach { cfg ->
                val c = JSONObject()
                if (cfg.presetId != null) c.put("presetId", cfg.presetId)
                if (cfg.nativeTransitionEnabled != null) c.put("nativeTransitionEnabled", cfg.nativeTransitionEnabled)
                cfgArr.put(c)
            }
            playlistsArr.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("courses", JSONArray(playlist.courseIds))
                    .put("nativeTransitionDefault", playlist.nativeTransitionDefault)
                    .put("entryConfigs", cfgArr),
            )
        }

        val presetsArr = JSONArray()
        presets.forEach { preset ->
            val stepsArr = JSONArray()
            preset.steps.forEach { step ->
                val obj = JSONObject()
                    .put("roleKey", step.roleKey)
                    .put("loopCount", step.loopCount)
                if (step.exitFrame != null) obj.put("exitFrame", step.exitFrame)
                stepsArr.put(obj)
            }
            presetsArr.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("courseCode", preset.courseCode)
                    .put("name", preset.name)
                    .put("steps", stepsArr)
                    .put("outroNumber", preset.outroNumber ?: JSONObject.NULL)
                    .put("fadeOutMs", preset.fadeOutMs)
                    .put("outroToNextPre", preset.outroToNextPre)
                    .put("updatedAtEpochMs", preset.updatedAtEpochMs),
            )
        }

        val routesArr = JSONArray()
        customRoutes.forEach { route ->
            val stopsArr = JSONArray()
            route.stops.forEach { stop ->
                stopsArr.put(
                    JSONObject()
                        .put("stageCode", stop.stageCode)
                        .put("presetId", stop.presetId ?: JSONObject.NULL),
                )
            }
            val edgesArr = JSONArray()
            route.edges.forEach { edge ->
                edgesArr.put(
                    JSONObject()
                        .put("from", edge.from)
                        .put("to", edge.to)
                        .put("musicEnabled", edge.musicEnabled)
                        .put("selectedJukeboxKey", edge.selectedJukeboxKey ?: JSONObject.NULL),
                )
            }
            routesArr.put(
                JSONObject()
                    .put("id", route.id)
                    .put("name", route.name)
                    .put("stops", stopsArr)
                    .put("edges", edgesArr)
                    .put("loopWholeRoute", route.loopWholeRoute)
                    .put("updatedAtEpochMs", route.updatedAtEpochMs)
                    .put("lastUsedEpochMs", route.lastUsedEpochMs)
                    .put("roadRuleMode", route.roadRuleMode.name)
                    .put("routeSuiteMode", route.routeSuiteMode.name),
            )
        }

        val statsArr = JSONArray()
        playStats.values
            .filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .forEach { stat ->
                statsArr.put(
                    JSONObject()
                        .put("trackId", stat.trackId)
                        .put("playCount", stat.playCount)
                        .put("lastPlayedEpochMs", stat.lastPlayedEpochMs),
                )
            }

        val bandsArr = JSONArray()
        preferences.dspConfig.bandsDb.forEach { bandsArr.put(it.toDouble()) }

        val prefsObj = JSONObject()
            .put("outputTrimGainDb", preferences.outputTrimGainDb)
            .put("outputTrimCeilingDb", preferences.outputTrimCeilingDb)
            .put("segmentSwitchFadeOutMs", preferences.segmentSwitchFadeOutMs)
            .put("perfectBeatEnabled", preferences.perfectBeatEnabled)
            .put("liveVisualizerEnabled", preferences.liveVisualizerEnabled)
            .put("eqEnabled", preferences.dspConfig.eqEnabled)
            .put("eqPreset", preferences.dspConfig.preset.name)
            .put("eqBandsDb", bandsArr)
            .put("bassBoostPercent", preferences.dspConfig.bassBoostPercent)
            .put("loudnessDb", preferences.dspConfig.loudnessDb.toDouble())
            .put("playbackSpeed", preferences.rateConfig.speed.toDouble())
            .put("playbackPitch", preferences.rateConfig.pitchSemitones.toDouble())
            .put("preservePitch", preferences.rateConfig.preservePitch)

        val markersArr = JSONArray()
        markers
            .sortedWith(compareBy<TrackMarker>({ it.trackId }, { it.positionMs }, { it.createdAtEpochMs }, { it.id }))
            .forEach { marker -> markersArr.put(TrackMarkerStore.encodeMarkerJson(marker)) }

        return JSONObject()
            .put("schemaVersion", UserBackupPayload.SCHEMA_VERSION)
            .put("kind", "mkw-player-user-backup")
            .put("exportedAtEpochMs", exportedAtEpochMs)
            .put("appVersion", appVersion)
            .put("playlists", playlistsArr)
            .put("favorites", JSONArray(favorites.toList()))
            .put("presets", presetsArr)
            .put("customRoutes", routesArr)
            .put("recentCourses", JSONArray(recentCourses))
            .put("playStats", statsArr)
            .put("preferences", prefsObj)
            .put("markers", markersArr)
            .toString(2)
    }

    fun parseBackupJson(raw: String): Result<UserBackupPayload> = runCatching {
        val root = JSONObject(raw)
        val version = root.getInt("schemaVersion")
        require(version == UserBackupPayload.SCHEMA_VERSION) {
            "Unsupported backup schemaVersion $version"
        }

        val playlists = mutableListOf<UserPlaylist>()
        val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until playlistsArr.length()) {
            val row = playlistsArr.getJSONObject(i)
            val id = row.optString("id", "").trim()
            val name = row.optString("name", "").trim()
            if (name.isEmpty()) continue
            val coursesArr = row.optJSONArray("courses") ?: JSONArray()
            val courseIds = (0 until coursesArr.length())
                .map { coursesArr.optString(it, "").trim() }
                .filter { it.isNotEmpty() }
            val cfgArr = row.optJSONArray("entryConfigs")
            val cfgs = if (cfgArr != null && cfgArr.length() > 0) {
                (0 until courseIds.size).map { idx ->
                    val cObj = cfgArr.optJSONObject(idx)
                    if (cObj == null) {
                        PlaylistEntryConfig()
                    } else {
                        val presetId = if (cObj.has("presetId") && !cObj.isNull("presetId")) {
                            cObj.optString("presetId", "").takeIf { it.isNotBlank() }
                        } else null
                        val nativeOverride = if (cObj.has("nativeTransitionEnabled") && !cObj.isNull("nativeTransitionEnabled")) {
                            cObj.optBoolean("nativeTransitionEnabled")
                        } else null
                        PlaylistEntryConfig(presetId, nativeOverride)
                    }
                }
            } else {
                emptyList()
            }
            playlists += UserPlaylist(
                id = id,
                name = name,
                courseIds = courseIds,
                nativeTransitionDefault = row.optBoolean("nativeTransitionDefault", false),
                entryConfigs = cfgs,
            )
        }

        val favorites = mutableListOf<String>()
        val favArr = root.optJSONArray("favorites") ?: JSONArray()
        for (i in 0 until favArr.length()) {
            val id = favArr.optString(i, "").trim()
            if (id.isNotEmpty()) favorites += id
        }

        val presets = mutableListOf<CoursePreset>()
        val presetsArr = root.optJSONArray("presets") ?: JSONArray()
        for (i in 0 until presetsArr.length()) {
            val obj = presetsArr.getJSONObject(i)
            val id = obj.optString("id", "").trim()
            val course = obj.optString("courseCode", "").trim()
            val name = obj.optString("name", "").trim()
            val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
            val steps = mutableListOf<FlowStep>()
            for (sIdx in 0 until stepsArr.length()) {
                val s = stepsArr.getJSONObject(sIdx)
                val roleKey = s.optString("roleKey", "").trim()
                if (roleKey.isEmpty()) continue
                steps += FlowStep(
                    roleKey = roleKey,
                    loopCount = s.optInt("loopCount", 0).coerceIn(0, 8),
                    exitFrame = if (s.has("exitFrame") && !s.isNull("exitFrame")) s.optLong("exitFrame", 0L).coerceAtLeast(0L) else null,
                )
            }
            if (id.isEmpty() || course.isEmpty() || name.isEmpty() || steps.isEmpty()) continue
            presets += CoursePreset(
                id = id,
                courseCode = course,
                name = name,
                steps = steps,
                outroNumber = if (obj.has("outroNumber") && !obj.isNull("outroNumber")) obj.optInt("outroNumber") else null,
                fadeOutMs = obj.optInt("fadeOutMs", 0).coerceIn(0, 10_000),
                outroToNextPre = obj.optBoolean("outroToNextPre", true),
                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
            )
        }

        val customRoutes = mutableListOf<CustomRoute>()
        val routesArr = root.optJSONArray("customRoutes") ?: JSONArray()
        for (i in 0 until routesArr.length()) {
            val obj = routesArr.getJSONObject(i)
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            val stopsArr = obj.optJSONArray("stops") ?: JSONArray()
            val stops = (0 until stopsArr.length()).mapNotNull { idx ->
                val s = stopsArr.getJSONObject(idx)
                val code = s.optString("stageCode", "").trim().removeSuffix("_00")
                if (code.isEmpty()) null else CustomRouteStop(
                    stageCode = code,
                    presetId = if (s.has("presetId") && !s.isNull("presetId")) s.optString("presetId", "").takeIf { it.isNotBlank() } else null,
                )
            }
            if (name.isEmpty() || stops.size < 2) continue
            val edgesArr = obj.optJSONArray("edges") ?: JSONArray()
            val edges = (0 until edgesArr.length()).map { idx ->
                val e = edgesArr.getJSONObject(idx)
                CustomRouteEdgeConfig(
                    from = e.optString("from", "").trim().removeSuffix("_00"),
                    to = e.optString("to", "").trim().removeSuffix("_00"),
                    musicEnabled = e.optBoolean("musicEnabled", true),
                    selectedJukeboxKey = if (e.has("selectedJukeboxKey") && !e.isNull("selectedJukeboxKey")) {
                        e.optString("selectedJukeboxKey", "").takeIf { it.isNotBlank() }
                    } else null,
                )
            }
            customRoutes += CustomRoute(
                id = id,
                name = name,
                stops = stops,
                edges = edges,
                loopWholeRoute = obj.optBoolean("loopWholeRoute", false),
                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
                lastUsedEpochMs = obj.optLong("lastUsedEpochMs", 0L),
                roadRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.fromStored(obj.optString("roadRuleMode", "")),
                routeSuiteMode = RoadJukeboxCatalog.RouteSuiteMode.fromStored(obj.optString("routeSuiteMode", "")),
            )
        }

        val recent = mutableListOf<String>()
        val recentArr = root.optJSONArray("recentCourses") ?: JSONArray()
        for (i in 0 until recentArr.length()) {
            val id = recentArr.optString(i, "").trim()
            if (id.isNotEmpty()) recent += id
        }

        val playStats = mutableListOf<TrackPlayStat>()
        val statsArr = root.optJSONArray("playStats") ?: JSONArray()
        for (i in 0 until statsArr.length()) {
            val s = statsArr.getJSONObject(i)
            val id = s.optString("trackId", "").trim()
            val count = s.optInt("playCount", 0).coerceAtLeast(0)
            if (id.isEmpty() || count <= 0) continue
            playStats += TrackPlayStat(
                trackId = id,
                playCount = count,
                lastPlayedEpochMs = s.optLong("lastPlayedEpochMs", 0L).coerceAtLeast(0L),
            )
        }

        val prefsObj = root.optJSONObject("preferences")
        val prefs = if (prefsObj != null) {
            val bandsArr = prefsObj.optJSONArray("eqBandsDb")
            val bands = (0 until 5).map { idx ->
                (bandsArr?.optDouble(idx, 0.0) ?: 0.0).toFloat().coerceIn(-12f, 12f)
            }
            val presetName = prefsObj.optString("eqPreset", "")
            val preset = EqualizerPreset.entries.firstOrNull { it.name == presetName }
                ?: EqualizerPreset.matchingOrCustom(bands)
            BackupPreferencesSnapshot(
                outputTrimGainDb = prefsObj.optDouble("outputTrimGainDb", OutputTrim.default.gainDb),
                outputTrimCeilingDb = prefsObj.optDouble("outputTrimCeilingDb", OutputTrim.default.ceilingDb),
                segmentSwitchFadeOutMs = prefsObj.optInt("segmentSwitchFadeOutMs", 0).coerceIn(0, PlaybackBehaviorSettings.MAX_SEGMENT_SWITCH_FADEOUT_MS),
                perfectBeatEnabled = prefsObj.optBoolean("perfectBeatEnabled", false),
                liveVisualizerEnabled = prefsObj.optBoolean("liveVisualizerEnabled", false),
                dspConfig = MasterDspStateConfig(
                    eqEnabled = prefsObj.optBoolean("eqEnabled", false),
                    bandsDb = bands,
                    preset = preset,
                    bassBoostPercent = prefsObj.optInt("bassBoostPercent", 0).coerceIn(0, 100),
                    loudnessDb = prefsObj.optDouble("loudnessDb", 0.0).toFloat().coerceIn(0f, 9f),
                ),
                rateConfig = PlaybackRateConfig(
                    speed = prefsObj.optDouble("playbackSpeed", 1.0).toFloat(),
                    pitchSemitones = prefsObj.optDouble("playbackPitch", 0.0).toFloat(),
                    preservePitch = prefsObj.optBoolean("preservePitch", true),
                ).normalized(),
            )
        } else {
            BackupPreferencesSnapshot()
        }

        val markers = mutableListOf<TrackMarker>()
        val markersArr = root.optJSONArray("markers")
        if (markersArr != null) {
            for (i in 0 until markersArr.length()) {
                val obj = markersArr.optJSONObject(i) ?: continue
                TrackMarkerStore.decodeMarkerJson(obj)?.let { markers += it }
            }
        }

        UserBackupPayload(
            schemaVersion = version,
            exportedAtEpochMs = root.optLong("exportedAtEpochMs", 0L),
            appVersion = root.optString("appVersion", ""),
            playlists = playlists,
            favorites = favorites,
            presets = presets,
            customRoutes = customRoutes,
            recentCourses = recent,
            playStats = playStats,
            preferences = prefs,
            markers = markers,
        )
    }

    fun exportAllFromContext(context: Context, appVersion: String): Result<String> = runCatching {
        val dir = context.applicationContext.filesDir
        val playlists = PlaylistStore(File(dir, "user-playlists.json")).read().getOrDefault(emptyList())
        val favorites = FavoriteStore(File(dir, "user-favorites.json")).read().getOrDefault(emptySet())
        val presets = CoursePresetStore(File(dir, "course-presets.json")).read().getOrDefault(emptyList())
        val routes = CustomRouteStore(File(dir, "routes/custom_routes.json")).read().getOrDefault(emptyList())
        val recent = RecentCourseStore(File(dir, "recent-courses.json")).read().getOrDefault(emptyList())
        val playStats = PlayStatsRepository.readSnapshot(context)
        val markers = TrackMarkerStore.inFilesDir(dir).allMarkers()
        val trim = OutputTrimSettings.current(context)
        val behavior: PlaybackBehaviorConfig = PlaybackBehaviorSettings.current(context)
        val dsp = AudioEffectSettings.currentDsp(context)
        val rate = AudioEffectSettings.currentRate(context)
        exportToJson(
            playlists = playlists,
            favorites = favorites,
            presets = presets,
            customRoutes = routes,
            recentCourses = recent,
            playStats = playStats,
            preferences = BackupPreferencesSnapshot(
                outputTrimGainDb = trim.gainDb,
                outputTrimCeilingDb = trim.ceilingDb,
                segmentSwitchFadeOutMs = behavior.segmentSwitchFadeOutMs,
                perfectBeatEnabled = behavior.perfectBeatEnabled,
                liveVisualizerEnabled = behavior.liveVisualizerEnabled,
                dspConfig = dsp,
                rateConfig = rate,
            ),
            appVersion = appVersion,
            markers = markers,
        )
    }

    fun applyBackupToContext(
        context: Context,
        payload: UserBackupPayload,
        mode: BackupRestoreMode,
    ): Result<BackupRestoreSummary> = runCatching {
        val dir = context.applicationContext.filesDir
        val playlistStore = PlaylistStore(File(dir, "user-playlists.json"))
        val favoriteStore = FavoriteStore(File(dir, "user-favorites.json"))
        val presetStore = CoursePresetStore(File(dir, "course-presets.json"))
        val routeStore = CustomRouteStore(File(dir, "routes/custom_routes.json"))
        val recentStore = RecentCourseStore(File(dir, "recent-courses.json"))
        val markerStore = TrackMarkerStore.inFilesDir(dir)

        val playlists = when (mode) {
            BackupRestoreMode.REPLACE -> playlistStore.replaceAll(payload.playlists)
            BackupRestoreMode.MERGE -> playlistStore.mergeAll(payload.playlists)
        }.getOrThrow()
        val favorites = when (mode) {
            BackupRestoreMode.REPLACE -> favoriteStore.replaceAll(payload.favorites)
            BackupRestoreMode.MERGE -> favoriteStore.mergeAll(payload.favorites)
        }.getOrThrow()
        val presets = when (mode) {
            BackupRestoreMode.REPLACE -> presetStore.replaceAll(payload.presets)
            BackupRestoreMode.MERGE -> presetStore.mergeAll(payload.presets)
        }.getOrThrow()
        val routes = when (mode) {
            BackupRestoreMode.REPLACE -> routeStore.replaceAll(payload.customRoutes)
            BackupRestoreMode.MERGE -> routeStore.mergeAll(payload.customRoutes)
        }.getOrThrow()
        val recent = when (mode) {
            BackupRestoreMode.REPLACE -> recentStore.replaceAll(payload.recentCourses)
            BackupRestoreMode.MERGE -> recentStore.mergeAll(payload.recentCourses)
        }.getOrThrow()
        val statMap = payload.playStats.associateBy { it.trackId }
        val stats = when (mode) {
            BackupRestoreMode.REPLACE -> PlayStatsRepository.replaceAll(context, statMap)
            BackupRestoreMode.MERGE -> PlayStatsRepository.merge(context, statMap)
        }.getOrThrow()
        val markers = when (mode) {
            BackupRestoreMode.REPLACE -> markerStore.replaceAll(payload.markers)
            BackupRestoreMode.MERGE -> markerStore.mergeAll(payload.markers)
        }

        if (mode == BackupRestoreMode.REPLACE) {
            OutputTrimSettings.select(
                context,
                OutputTrim.of(payload.preferences.outputTrimGainDb, payload.preferences.outputTrimCeilingDb),
            )
            PlaybackBehaviorSettings.setSegmentSwitchFadeOutMs(context, payload.preferences.segmentSwitchFadeOutMs)
            PlaybackBehaviorSettings.setPerfectBeatEnabled(context, payload.preferences.perfectBeatEnabled)
            PlaybackBehaviorSettings.setLiveVisualizerEnabled(context, payload.preferences.liveVisualizerEnabled)
            AudioEffectSettings.saveDsp(context, payload.preferences.dspConfig)
            AudioEffectSettings.saveRate(context, payload.preferences.rateConfig)
        }

        BackupRestoreSummary(
            playlistsCount = playlists.size,
            favoritesCount = favorites.size,
            presetsCount = presets.size,
            routesCount = routes.size,
            recentCount = recent.size,
            statsCount = stats.size,
            markersCount = markers.size,
        )
    }

    fun exportPlaylistsToM3u(
        playlists: List<UserPlaylist>,
        catalog: JukeboxCatalog.Catalog? = null,
        titleResolver: (String) -> Pair<String, Int>,
    ): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        playlists.forEach { playlist ->
            sb.append("#PLAYLIST:").append(playlist.name.replace('\n', ' ')).append('\n')
            playlist.courseIds.forEach { entryId ->
                val resolved = titleResolver(entryId)
                val title = resolved.first.ifBlank {
                    catalog?.tracksByKey?.get(entryId)?.title ?: entryId
                }.replace('\n', ' ')
                val sec = resolved.second.coerceAtLeast(-1)
                sb.append("#EXTINF:").append(sec).append(',').append(title).append('\n')
                sb.append("mkwplayer://track/").append(entryId).append('\n')
            }
        }
        return sb.toString()
    }
}
