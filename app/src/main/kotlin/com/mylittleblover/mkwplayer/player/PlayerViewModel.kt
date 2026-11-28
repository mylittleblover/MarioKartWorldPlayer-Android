package com.mylittleblover.mkwplayer.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SeekTarget
import com.mylittleblover.mkwplayer.audio.TimingDiagnosis
import com.mylittleblover.mkwplayer.graph.GraphBuilder
import com.mylittleblover.mkwplayer.graph.GraphProbe
import com.mylittleblover.mkwplayer.library.AudioPicker
import com.mylittleblover.mkwplayer.library.CatalogLoader
import com.mylittleblover.mkwplayer.library.CatalogMatcher
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.interactive.InteractivePlaybackPlan
import com.mylittleblover.mkwplayer.interactive.NativeInteractiveDriver
import com.mylittleblover.mkwplayer.interactive.InteractiveAudioIdentities
import com.mylittleblover.mkwplayer.interactive.PackInteractiveSources
import com.mylittleblover.mkwplayer.interactive.RainbowInteractiveCompiler
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.CoursePresetStore
import com.mylittleblover.mkwplayer.library.PlaylistEntryConfig
import com.mylittleblover.mkwplayer.library.PlaylistItemRef
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.library.UserPlaylist
import com.mylittleblover.mkwplayer.library.LibraryStore
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.FolderAudioSource
import com.mylittleblover.mkwplayer.library.StageFlowReader
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock
import com.mylittleblover.mkwplayer.unlock.SpecialCupRules
import com.mylittleblover.mkwplayer.unlock.UnlockStore
import com.mylittleblover.mkwplayer.unlock.UnlockTestMode
import com.mylittleblover.mkwplayer.unlock.UnlockTestStore
import com.mylittleblover.mkwplayer.library.Playability
import com.mylittleblover.mkwplayer.pack.InternalPackLibrary
import com.mylittleblover.mkwplayer.pack.LibraryInvalidation
import com.mylittleblover.mkwplayer.pack.PackCoverage
import com.mylittleblover.mkwplayer.pack.PackImportChangelog
import com.mylittleblover.mkwplayer.pack.PackImportCoordinator
import com.mylittleblover.mkwplayer.pack.PackImportPhase
import com.mylittleblover.mkwplayer.pack.PackImportUiState
import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.pack.PackPlayback
import com.mylittleblover.mkwplayer.pack.PackRegistry
import com.mylittleblover.mkwplayer.pack.PoolUpdateStatus
import com.mylittleblover.mkwplayer.pack.StreamingPackInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Holds the picked tracks and drives the one open transport.
 *
 * Responsibilities stay narrow: accept a picked file, prepare it once, remember what
 * was learned about it, and drive the transport through [PlaybackController]. All
 * audio semantics stay in the engine; all transport rules stay in the controller.
 *
 * Preparation is cached per digest because it costs a hash and a graph build over
 * the whole file, and re-selecting a track should not repeat that.
 */
internal class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    /** One row in the library. */
    data class Track(
        val key: String,
        val name: String,
        val frames: Long,
        val sampleRate: Int,
        val playable: Boolean,
        val reason: String?,
    )

    /** Everything the screen needs, as one value. */
    data class Ui(
        val tracks: List<Track> = emptyList(),
        val selectedKey: String? = null,
        val title: String = "",
        val status: String = "No track loaded",
        val position: String = "--:--",
        val total: String = "--:--",
        val progress: Float = 0f,
        val canPause: Boolean = false,
        val canResume: Boolean = false,
        val canStop: Boolean = false,
        val timing: String = "Timing unavailable",
        val timingVerdict: TimingDiagnosis.Verdict = TimingDiagnosis.Verdict.NO_DATA,
        val outputActive: Boolean = false,
        val detail: String? = null,
        val busy: Boolean = false,
        /** True while opening/compiling an interactive track before the new flow commits. */
        val trackLoading: Boolean = false,
        val loadingTrackCode: String? = null,
        val loadingTrackTitle: String? = null,
        /** Canonical favorite identifier (course code or PlaylistItemRef.raw) for active playback. */
        val activeFavoriteId: String? = null,
        /** Bundled catalog, or null when the asset is missing or unreadable. */
        val catalog: CatalogLoader.Catalog? = null,
        /** Category currently expanded in the catalog browser. */
        val openCategory: String? = null,
        /** True when the activity should ask for POST_NOTIFICATIONS. */
        val needsNotificationPermission: Boolean = false,
        /** The loaded multi-part flow, if one is open. Null in single-track mode. */
        val flow: FlowUi? = null,
        /** Special Cup progress and the offer, or null before a library exists. */
        val unlock: UnlockUi? = null,
        val unlockTestMode: UnlockTestMode = UnlockTestMode.NORMAL,
        val debugUnlockBannerVisible: Boolean = false,
        /** What the last pack import did, or null when none has run. */
        val pack: PackUi? = null,
        /** Cached chronological jukebox catalog built from installed packs + bundled OST metadata. */
        val jukebox: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog =
            com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog(emptyList(), emptyMap()),
        /** True while the active playback session uses standalone linear decoding (Jukebox, etc.). */
        val linearMode: Boolean = false,
        val linearTrack: LinearQueue.Entry? = null,
        val queue: LinearQueue.Snapshot = LinearQueue.Snapshot(),
        val canSeek: Boolean = false,
        val totalFrames: Long = 0L,
        val sampleRate: Int = 0,
        val artworkName: String? = null,
        val linearContextKind: LinearSessionStore.ContextKind = LinearSessionStore.ContextKind.NONE,
        val linearContextId: String? = null,
        val linearContextTitle: String? = null,
        val allowPlayNext: Boolean = true,
        val allowShuffle: Boolean = true,
        val allowSingleRepeat: Boolean = true,
        val allowRepeatToggle: Boolean = true,
        val continueListening: ContinueListeningUi? = null,
        /** Monotonic counter incremented on every library commit to re-evaluate UI state (#84-#89). */
        val libraryGeneration: Int = 0,
        /** Live single-task streaming pack import state (#91-#97). */
        val importState: PackImportUiState = PackImportUiState(),
        val playbackRate: com.mylittleblover.mkwplayer.audio.PlaybackRateConfig =
            com.mylittleblover.mkwplayer.audio.PlaybackRateConfig(),
        val dspConfig: com.mylittleblover.mkwplayer.audio.MasterDspStateConfig =
            com.mylittleblover.mkwplayer.audio.MasterDspStateConfig(),
        val liveVisualizerEnabled: Boolean = false,
        val abLoopStartFrame: Long? = null,
        val abLoopEndFrame: Long? = null,
        val playStats: Map<String, com.mylittleblover.mkwplayer.library.TrackPlayStat> = emptyMap(),
        val sleepTimer: SleepTimerState = SleepTimerState(),
        val interactiveTimer: InteractiveTimerState = InteractiveTimerState(),
        val libraryReady: Boolean = false,
        val freeRoamMode: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode =
            com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK,
        val staffCreditsUnlocked: Boolean = false,
        val staffCreditsToastEvent: Long = 0L,
        val isLoopingTrack: Boolean = false,
        val nonInteractiveLoopConfig: com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig =
            com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig.Default,
        val isRenderingAudio: Boolean = false,
        val renderAudioProgress: Float = 0f,
        val renderAudioStatus: String? = null,
        val renderAudioError: String? = null,
        val renderAudioOutputPath: String? = null,
        val exportFolderDisplay: String = com.mylittleblover.mkwplayer.audio.AudioExportSettings.DEFAULT_RELATIVE_PATH,
        val outputTrimEnabled: Boolean = true,
    )

    /** Fast-ticking playback position/source clock separated from discrete [Ui] to prevent 200ms root recompositions. */
    data class ProgressUi(
        val position: String = "--:--",
        val total: String = "--:--",
        val progress: Float = 0f,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val sourceFrame: Long? = null,
        val fadeRemainingFrames: Long? = null,
    )

    data class ContinueListeningUi(
        val key: String,
        val title: String,
        val subtitle: String,
        val artworkName: String?,
        val positionText: String,
        val totalText: String,
        val progress: Float,
        val positionFrames: Long,
        val totalFrames: Long,
        val sampleRate: Int,
        val contextKind: LinearSessionStore.ContextKind,
        val contextId: String?,
        val contextTitle: String?,
    )

    /**
     * The imported soundtrack pack, as the screen sees it.
     *
     * [trackCount] and [playableCount] are the pack's own numbers, not the catalogue's: a pack the user
     * imported is a smaller library than everything that exists, and presenting the pack's contents as the
     * whole soundtrack is how a partial import reads as a complete one.
     */
    data class PackUi(
        val name: String,
        /** Every logical track the pack carries. */
        val trackCount: Int,
        /** How many of them this build can decode. */
        val playableCount: Int,
        /** Non-fatal differences between the pack's manifest and the files on disk. */
        val notes: List<String> = emptyList(),
        /** True while an import is running. */
        val importing: Boolean = false,
        /** Progress line, e.g. "asset 12 of 610". */
        val progress: String = "",
    ) {
        /** One line the screen can show, stating both numbers. */
        val summary: String
            get() = "$trackCount tracks, $playableCount playable in this build"
    }

    /**
     * A multi-part flow, as the screen sees it.
     *
     * `sections` are the requestable parts and `activeSegment` marks the one playing. The
     * `provenance` string is not decoration: it is the difference between showing a testing
     * configuration and presenting it as the game's behaviour.
     */
    data class FlowUi(
        val title: String,
        val sections: List<FlowPresentation.Section>,
        val activeSegment: Int?,
        val summary: String,
        val provenance: String,
        val isProvisional: Boolean,
        /** Outros this flow can end with, in the order the flow lists them. */
        val outros: List<Int> = emptyList(),
        /** True while a whole-stage performance is running. */
        val performing: Boolean = false,
        /** Where the performance is, in words. Empty when nothing is performing. */
        val performanceStage: String = "",
        /** Stable source identifiers; localized only when rendered. */
        val stageKey: String? = null,
        val pendingStageKey: String? = null,
        /** Stages present in the selected source graph, for read-only structure display. */
        val availableStageKeys: List<String> = emptyList(),
        /**
         * Why the performance cannot start, or null when it can.
         *
         * A reason rather than a disabled button: a control that is greyed with no explanation makes the
         * listener guess, and the usual guess is that the app is broken.
         */
        val performanceBlocker: String? = null,
        /** True when the flow carries a fast arrangement, so the console can show the switch. */
        val hasFast: Boolean = false,
        /** True when the fast arrangement is the one playing. */
        val fastPlaying: Boolean = false,
        val regionNumbers: List<Int> = emptyList(),
        val selectedRegion: Int? = null,
        val availableRegions: Set<Int> = emptySet(),
        val versionNumbers: List<Int> = emptyList(),
        val selectedVersion: Int? = null,
        val availableVersions: Set<Int> = emptySet(),
        val canFast: Boolean = false,
        val canCancelPending: Boolean = false,
        val sourceFrame: Long? = null,
        val sourceFrames: Long? = null,
        val loopStart: Long? = null,
        val loopEnd: Long? = null,
        val voiceCount: Int = 0,
        val transportRate: Int = 48000,
        val fading: Boolean = false,
        val fadeRemainingFrames: Long? = null,
        /**
         * The next-stage event is available.
         *
         * True only while the prelude is looping, because that is the one moment the event means anything.
         * A button that is always enabled is a button that is sometimes a no-op.
         */
        val canAdvance: Boolean = false,
        /** The ending event is available: the main loop is playing, so an ending can be chosen. */
        val canEnd: Boolean = false,
        val hasSpeakerOverlay: Boolean = false,
        val speakerEnabled: Boolean = false,
        val isRainbowMelodyOverlay: Boolean = false,
        val isBattleTrack: Boolean = false,
        val pendingSyncPoint: com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.SyncPoint? = null,
        val pendingSourceCue: String? = null,
        val nextAdvanceTargetKey: String? = null,
        val nextAdvanceSyncPoint: com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.SyncPoint? = null,
    )

    /**
     * Catalogue rows with ownership, rebuilt whenever the library changes.
     *
     * Separate from [catalog] because ownership changes as files are added or removed, while the
     * catalogue itself is bundled and static.
     */
    /**
     * Catalogue rows with ownership, rebuilt whenever the library changes.
     *
     * Owns no Android state, so it is constructed once the bundled catalogue has been parsed in
     * [init]; ownership changes as files are added or removed while the catalogue stays static.
     */
    private var browser: CatalogBrowser? = null

    /** Catalogue browser state, or null before the bundled catalogue is available. */
    val catalogState: StateFlow<CatalogBrowser.State>?
        get() = browser?.state

    /**
     * The map anchors, or null when the asset is missing.
     *
     * Loaded once and held: the map is drawn on every frame of a scroll, and re-parsing a JSON file per frame
     * would be the most expensive thing on the screen.
     */
    val mapAnchors: com.mylittleblover.mkwplayer.library.MapAnchors.Catalog? by lazy {
        runCatching {
            getApplication<Application>().assets.open(MAP_ANCHORS_ASSET)
                .bufferedReader().use { it.readText() }
                .let { com.mylittleblover.mkwplayer.library.MapAnchors.parse(it) }
        }.getOrNull()
    }

    /**
     * Selects a stage from the map.
     *
     * A stage the map names but which has no files is reported rather than silently ignored: pressing a pin
     * and getting nothing is indistinguishable from a broken app.
     */
    fun selectStage(code: String) {
        if (isCeremonyLocked) return
        if(isSpecialStageLocked(code)) return
        if(nativePerformance?.isFading==true) return
        packPlaybackRequest++
        pack?.let { installed ->
            val stage=installed.manifest.stages.firstOrNull { it.code.removeSuffix("_00").equals(code.removeSuffix("_00"),true) }
            if(stage!=null) {
                stage.tracks.firstNotNullOfOrNull { installed.manifest.track(it) }?.let { openPackTrack(it) }
                return
            }
        }
        val match = _ui.value.tracks.firstOrNull {
            it.name.contains(code.removeSuffix("_00"), ignoreCase = true)
        }
        if (match == null) {
            _ui.value = _ui.value.copy(
                detail = str(com.mylittleblover.mkwplayer.R.string.player_detail_no_stage_files, code),
            )
            return
        }
        select(match.key)
    }

    internal val currentStageCode: String?
        get() = interactivePlan?.name?.removePrefix("BGM_Stage_")?.removeSuffix("_00")

    internal fun isStageOpen(code: String): Boolean =
        interactivePlan?.name?.removePrefix("BGM_Stage_")?.removeSuffix("_00")?.equals(code.removeSuffix("_00"), true) == true

    fun canStartStage(code: String): Boolean {
        if (isSpecialStageLocked(code)) return false
        val logical = code.removeSuffix("_00").lowercase(java.util.Locale.ROOT)
        if (interactivePlan?.name?.removePrefix("BGM_Stage_")?.equals(logical, true) == true) return true
        return logical in playableStageSet
    }

    private fun rebuildPlayableStageSet(manifest: PackManifest?) {
        playableStageSet = if (manifest == null) {
            emptySet()
        } else {
            manifest.stages.asSequence()
                .filter { stage -> stage.tracks.any { manifest.track(it) != null } }
                .map { it.code.removeSuffix("_00").lowercase(java.util.Locale.ROOT) }
                .toHashSet()
        }
    }

    private fun localizedStageTitle(rawCodeOrTitle: String, fallback: String): String {
        val clean = rawCodeOrTitle.removePrefix("BGM_Stage_").removeSuffix("_00").trim()
        val lang = java.util.Locale.getDefault().toLanguageTag()
        return clean.takeIf { it.isNotEmpty() }?.let { localizedNames?.name(it, lang) } ?: fallback
    }

    private fun canonicalFavoriteRef(entry: LinearQueue.Entry?): String? {
        entry ?: return null
        if (entry.key == CEREMONY_ENTRY_KEY) return null
        if (entry.sourceType == LinearSessionStore.SourceType.JUKEBOX) {
            val rawKey = (entry.sourceTrackKey ?: entry.key).removePrefix(PlaylistItemRef.PREFIX_JUKEBOX)
            return "${PlaylistItemRef.PREFIX_JUKEBOX}$rawKey"
        }
        val code = entry.courseCode
        if (!code.isNullOrBlank()) {
            if (!code.startsWith(PlaylistItemRef.PREFIX_EXTRA)) {
                return code.removeSuffix("_00")
            }
            val tid = code.removePrefix(PlaylistItemRef.PREFIX_EXTRA)
            return resolveExtraFavoriteRef(tid)
        }
        return when {
            entry.key.startsWith(PlaylistItemRef.PREFIX_SFC) ||
                entry.key.startsWith(PlaylistItemRef.PREFIX_BATTLE) ||
                entry.key.startsWith(PlaylistItemRef.PREFIX_JUKEBOX) -> entry.key
            entry.key.startsWith(PlaylistItemRef.PREFIX_EXTRA) ->
                resolveExtraFavoriteRef(entry.key.removePrefix(PlaylistItemRef.PREFIX_EXTRA))
            ":${PlaylistItemRef.PREFIX_EXTRA}" in entry.key -> {
                val tid = entry.key.substringAfter(":${PlaylistItemRef.PREFIX_EXTRA}").substringBefore(':')
                resolveExtraFavoriteRef(tid)
            }
            else -> null
        }
    }

    private fun resolveExtraFavoriteRef(targetId: String): String = when {
        extraCatalog.sfcCourses.any { it.id == targetId } -> "${PlaylistItemRef.PREFIX_SFC}$targetId"
        extraCatalog.battleTracks.any { it.id == targetId } -> "${PlaylistItemRef.PREFIX_BATTLE}$targetId"
        else -> "${PlaylistItemRef.PREFIX_EXTRA}$targetId"
    }

    private fun beginInteractiveLoad(targetCode: String?, targetTitle: String?, favoriteId: String?) {
        _ui.value = _ui.value.copy(
            trackLoading = true,
            loadingTrackCode = targetCode,
            loadingTrackTitle = targetTitle,
            activeFavoriteId = favoriteId ?: targetCode,
            title = targetTitle.orEmpty(),
            artworkName = targetCode?.let { extraCatalog.courseArtwork[it] ?: extraCatalog.courseArtwork["${it}_00"] },
            flow = null,
            detail = null,
            linearMode = false,
            linearTrack = null,
            canSeek = false,
        )
    }

    private fun clearInteractiveLoad() {
        if (_ui.value.trackLoading || _ui.value.loadingTrackCode != null || _ui.value.loadingTrackTitle != null) {
            _ui.value = _ui.value.copy(
                trackLoading = false,
                loadingTrackCode = null,
                loadingTrackTitle = null,
            )
        }
    }

    /** Options are derived from verified identities in the installed pack without opening playback. */
    internal fun initialStageLabels(code: String): List<String> {
        if (isSpecialStageLocked(code)) return emptyList()
        val installed = pack ?: return emptyList()
        val logical = code.removeSuffix("_00")
        val stage = installed.manifest.stages.singleOrNull { it.code.removeSuffix("_00").equals(logical, true) } ?: return emptyList()
        val identities = stage.tracks.flatMap { installed.manifest.track(it)?.parts.orEmpty() }
            .mapNotNull { interactiveIdentities.identityForDigest(it.asset.sha256) }
            .filter { it.belongsToCourse(logical) }
        if (logical.equals("Rainbow", true)) {
            return listOf("Area1_Pre", "Area1_Bridge", "Area1", "Area2", "Area3_Pre", "Area3")
                .filter { label -> identities.any { it.fileName == "BGM_Rainbow_${label}.bwav" ||
                    label in listOf("Area1", "Area2", "Area3") && it.fileName == "BGM_Rainbow_${label}_Inst.bwav" } }
        }
        val primary = stage.tracks.firstNotNullOfOrNull { installed.manifest.track(it) } ?: return emptyList()
        val primaryRoles = primary.parts.mapNotNull { interactiveIdentities.identityForDigest(it.asset.sha256) }
            .filter { it.stage.equals(logical, true) }.map { it.role }.toSet()
        val groupedRoles = identities.map { it.role }.toSet()
        val basic = listOf("Pre", "PreShort", "Bridge", "Main", "Fast").filter { role ->
            (if (role == "Fast") role in groupedRoles else role in primaryRoles)
        }
        val extra = if (logical.equals("DonkeyKong", true)) (2..5).mapNotNull { number ->
            "Region $number".takeIf { identities.any { it.fileName == "BGM_Stage_DonkeyKong_Pattern$number.bwav" } }
        } else if (logical.equals("Cinema", true) || logical.equals("SnowMountain", true))
            listOf("Main Pattern2" to "Pattern2", "Fast Pattern2" to "Fast_Pattern2").mapNotNull { (label, role) ->
                label.takeIf { identities.any { it.fileName == "BGM_Stage_${logical}_$role.bwav" } }
            } else emptyList()
        return basic + extra
    }

    /** Resolve the selected course by its stable code before starting any playback. */
    fun startStagePerformance(code: String, startingStage: String? = null) {
        if (isCeremonyLocked) return
        if(isSpecialStageLocked(code)) return
        if(nativePerformance?.isFading==true) return
        val logical=code.removeSuffix("_00")
        if(interactivePlan?.name?.removePrefix("BGM_Stage_")?.equals(logical,true)==true) {
            if (startingStage != null && !_ui.value.canStop) {
                val candidate = interactivePlan?.initialSegments?.singleOrNull { interactivePlan?.labels?.get(it) == startingStage }
                if (candidate == null) { _ui.value = _ui.value.copy(detail = str(com.mylittleblover.mkwplayer.R.string.interactive_err_start_unavailable)); return }
                selectInitialEntry(candidate)
            }
            packPlaybackRequest++;startPerformance();return
        }
        val installed=pack
        val stage=installed?.manifest?.stages?.singleOrNull { it.code.removeSuffix("_00").equals(logical,true) }
        val track=stage?.tracks?.firstNotNullOfOrNull { installed?.manifest?.track(it) }
        if(track==null) { _ui.value=_ui.value.copy(detail=str(com.mylittleblover.mkwplayer.R.string.interactive_err_no_source, code));return }
        openPackTrack(track,perform=true,startingStage=startingStage)
    }

    private fun localizedAppContext(): android.content.Context =
        com.mylittleblover.mkwplayer.i18n.AppLanguageSettings.wrapLegacy(getApplication())

    private fun str(@androidx.annotation.StringRes resId: Int, vararg args: Any): String {
        val ctx = localizedAppContext()
        return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
    }

    private fun localizedBlockingReason(
        report: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteValidationReport,
        @androidx.annotation.StringRes fallbackResId: Int,
        languageTag: String,
    ): String {
        fun stageTitle(code: String): String = localizedNames?.name(code, languageTag) ?: code
        return when (val cause = report.blockingCause) {
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.BlockingCause.TooFewStops ->
                str(com.mylittleblover.mkwplayer.R.string.route_block_min_stops)
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.BlockingCause.NoStops ->
                str(com.mylittleblover.mkwplayer.R.string.route_block_no_stops)
            is com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.BlockingCause.InvalidEdges ->
                str(
                    com.mylittleblover.mkwplayer.R.string.route_block_invalid_edges,
                    cause.edges.joinToString(", ") { "${stageTitle(it.from)} → ${stageTitle(it.to)}" },
                )
            is com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.BlockingCause.IncompletePools ->
                str(
                    com.mylittleblover.mkwplayer.R.string.route_block_incomplete_pool,
                    cause.edges.joinToString(", ") { "${stageTitle(it.from)} → ${stageTitle(it.to)}" },
                )
            is com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.BlockingCause.MissingSubstitutes ->
                str(
                    com.mylittleblover.mkwplayer.R.string.route_block_missing_substitute,
                    cause.edges.joinToString(", ") { "${stageTitle(it.from)} → ${stageTitle(it.to)}" },
                )
            is com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.BlockingCause.UnplayableStages ->
                str(
                    com.mylittleblover.mkwplayer.R.string.route_block_unplayable_stages,
                    cause.stageCodes.joinToString(", ") { stageTitle(it) },
                )
            null -> report.blockingReason ?: str(fallbackResId)
        }
    }

    private val _ui = MutableStateFlow(Ui(status = str(com.mylittleblover.mkwplayer.R.string.player_status_no_track)))
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private val _progressUi = MutableStateFlow(ProgressUi())
    val progressUi: StateFlow<ProgressUi> = _progressUi.asStateFlow()

    private val _browseCatalog = MutableStateFlow<com.mylittleblover.mkwplayer.library.BrowseCatalog?>(null)
    val browseCatalog: StateFlow<com.mylittleblover.mkwplayer.library.BrowseCatalog?> = _browseCatalog.asStateFlow()

    private val _spectrumBars = MutableStateFlow(FloatArray(SPECTRUM_BAND_COUNT))
    val spectrumBars: StateFlow<FloatArray> = _spectrumBars.asStateFlow()
    private var visualizerJob: Job? = null

    private data class Prepared(
        val name: String,
        val bytes: ByteArray,
        val packet: ByteArray,
        val frames: Long,
        val sampleRate: Int,
    )

    private val prepared = mutableMapOf<String, Prepared>()

    /** The open flow, if any: its compiled graph and the parts it will bind. */
    private var flow: StageFlow? = null
    private var flowCompiled: StageFlowCompiler.Compiled? = null
    private var flowBytes: Map<String, ByteArray> = emptyMap()
    private var interactivePlan: InteractivePlaybackPlan? = null
    private var indexedInteractivePlan: InteractivePlaybackPlan? = null
    private var indexedPlanSegments: Map<Int, com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Segment> = emptyMap()
    private var indexedPlanTransitions: Map<Long, com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Transition> = emptyMap()
    private var cachedFlowPosSec: Long = -1L; private var cachedFlowPosRate: Int = -1; private var cachedFlowPosText: String = "--:--"
    private var cachedFlowTotalFrames: Long = -1L; private var cachedFlowTotalRate: Int = -1; private var cachedFlowTotalText: String = "--:--"
    private var cachedLinearPosSec: Long = -1L; private var cachedLinearPosRate: Int = -1; private var cachedLinearPosText: String = "--:--"
    private var cachedLinearTotalFrames: Long = -1L; private var cachedLinearTotalRate: Int = -1; private var cachedLinearTotalText: String = "--:--"
    private fun ensurePlanIndex(plan: InteractivePlaybackPlan) {
        if (indexedInteractivePlan === plan) return
        indexedInteractivePlan = plan
        indexedPlanSegments = plan.spec.segments.associateBy { it.index }
        val edges = HashMap<Long, com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.Transition>(plan.spec.transitions.size * 2)
        for (t in plan.spec.transitions) {
            for (src in t.sources) {
                for (dst in t.destinations) {
                    val key = (src.toLong() shl 32) or (dst.toLong() and 0xFFFFFFFFL)
                    if (key !in edges) edges[key] = t
                }
            }
        }
        indexedPlanTransitions = edges
    }
    private fun formatFlowPos(frames: Long, rate: Int): String {
        val sec = if (rate > 0) frames / rate else -1L
        if (sec == cachedFlowPosSec && rate == cachedFlowPosRate) return cachedFlowPosText
        cachedFlowPosSec = sec; cachedFlowPosRate = rate
        cachedFlowPosText = EngineSnapshot.formatPosition(frames, rate)
        return cachedFlowPosText
    }
    private fun formatFlowTotal(frames: Long, rate: Int): String {
        if (frames == cachedFlowTotalFrames && rate == cachedFlowTotalRate) return cachedFlowTotalText
        cachedFlowTotalFrames = frames; cachedFlowTotalRate = rate
        cachedFlowTotalText = EngineSnapshot.formatPosition(frames, rate)
        return cachedFlowTotalText
    }
    private fun formatLinearPos(frames: Long, rate: Int): String {
        val sec = if (rate > 0) frames / rate else -1L
        if (sec == cachedLinearPosSec && rate == cachedLinearPosRate) return cachedLinearPosText
        cachedLinearPosSec = sec; cachedLinearPosRate = rate
        cachedLinearPosText = EngineSnapshot.formatPosition(frames, rate)
        return cachedLinearPosText
    }
    private fun formatLinearTotal(frames: Long, rate: Int): String {
        if (frames == cachedLinearTotalFrames && rate == cachedLinearTotalRate) return cachedLinearTotalText
        cachedLinearTotalFrames = frames; cachedLinearTotalRate = rate
        cachedLinearTotalText = EngineSnapshot.formatPosition(frames, rate)
        return cachedLinearTotalText
    }
    private var nativePerformance: NativeInteractiveDriver? = null
    private val minfCatalog by lazy {
        getApplication<Application>().assets.open("interactive_minf.json").bufferedReader().use { MinfCatalog.parse(it.readText()) }
    }
    private val interactiveIdentities by lazy {
        getApplication<Application>().assets.open("interactive_audio_identities.json").bufferedReader().use { InteractiveAudioIdentities.parse(it.readText()) }
    }
    @Volatile
    private var cachedFinalLapFanfare: ByteArray? = null
    @Volatile
    private var finalLapFanfareChecked: Boolean = false

    private fun loadFinalLapFanfareBytes(): ByteArray? {
        cachedFinalLapFanfare?.let { return it }
        if (finalLapFanfareChecked && pack?.directory == null) return null
        val dir = pack?.directory
        if (dir != null) {
            val packFile = java.io.File(dir, "audio/${com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.SHA256}")
            if (packFile.isFile) {
                runCatching { packFile.readBytes() }.getOrNull()?.let {
                    cachedFinalLapFanfare = it
                    finalLapFanfareChecked = true
                    return it
                }
            }
        }
        val assetPath = "audio/${com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET}"
        runCatching {
            getApplication<Application>().assets.open(assetPath).use { it.readBytes() }
        }.getOrNull()?.let {
            cachedFinalLapFanfare = it
            finalLapFanfareChecked = true
            return it
        }
        finalLapFanfareChecked = true
        return null
    }

    /** The unified merged internal pack library, or null when using loose files. */
    private var pack: InstalledPack? = null
    @Volatile
    private var playableStageSet: Set<String> = emptySet()
    private var mergedFingerprints: List<String> = emptyList()

    /**
     * The jukebox the installed pack carries, grouped by source game.
     *
     * Computed from the verified manifest rather than from the file names on disk: a name states where a
     * track came from, but only the manifest states that the pack actually carries it.
     */
    /**
     * The jukebox every installed pack carries, grouped by source game.
     *
     * Read from all installed packs rather than only the active one: the active pack is whichever playback
     * uses, and a listener with both a course pack and a jukebox pack must see the jukebox tracks.
     *
     * Recomputed on read, which is a handful of small JSON parses; the alternative is invalidating a cache at
     * every install and restore path, and a stale cache here is a category that silently offers nothing.
     */
    private val jukeboxMetadata: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Metadata? by lazy {
        runCatching {
            getApplication<Application>().assets.open(JUKEBOX_CATALOG_ASSET)
                .bufferedReader().use { it.readText() }
                .let { com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.parseMetadata(it) }
        }.getOrNull()
    }

    private val linearCoordinator: LinearPlaybackCoordinator
        get() = PlaybackHolder.linear()

    private val linearSessionStore: LinearSessionStore by lazy {
        LinearSessionStore(java.io.File(getApplication<Application>().filesDir, "linear-session.json"))
    }

    private var lastPersistedLinearProgressMs: Long = 0L

    /** The jukebox, arranged by chronological source game and official OST track order. */
    val jukebox: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog
        get() = _ui.value.jukebox

    val extraCatalog: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog by lazy {
        com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.load(getApplication())
    }

    val courseMedleyCatalog: com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog.Catalog by lazy {
        com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog.load(getApplication())
    }

    internal val extraMedleyCatalog: com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.Catalog by lazy {
        com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.load(getApplication())
    }

    val localizedNames: com.mylittleblover.mkwplayer.routes.LocalizedNames? by lazy {
        runCatching {
            getApplication<Application>().assets.open("localized_names.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.routes.LocalizedNames.parse(it.readText())
            }
        }.getOrNull()
    }

    val stageConnections: com.mylittleblover.mkwplayer.library.StageConnections.Catalog? by lazy {
        runCatching {
            getApplication<Application>().assets.open("stage_connections.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.library.StageConnections.parse(it.readText())
            }
        }.getOrNull()
    }

    val officialRouteCatalog: com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog? by lazy {
        val knownStages = mapAnchors?.stages?.map { it.code }?.toSet().orEmpty()
        runCatching {
            getApplication<Application>().assets.open("official_routes.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog.parse(it.readText(), knownStages)
            }
        }.getOrNull()
    }

    val routeMusicCatalog: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteMusicCatalog? by lazy {
        runCatching {
            getApplication<Application>().assets.open("route_music_catalog.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteMusicCatalog.parse(it.readText())
            }
        }.getOrNull()
    }

    val freeRoamJukeboxCatalog: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.Data? by lazy {
        runCatching {
            getApplication<Application>().assets.open("jukebox_playlists.json").bufferedReader().use {
                com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.parse(it.readText())
            }
        }.getOrNull()
    }

    private val freeRoamEngine: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxEngine? by lazy {
        freeRoamJukeboxCatalog?.let {
            com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxEngine(it)
        }
    }

    @Volatile
    var roadJukeboxPool: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.CandidatePool =
        com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.CandidatePool()
        private set

    fun reloadRoadJukeboxPool() {
        roadJukeboxPool = com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.loadInternalPool(getApplication<Application>().filesDir)
    }

    val packDirectory: java.io.File?
        get() = pack?.directory

    private val _nmStaffCreditsOffsetMs = MutableStateFlow(
        com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.DEFAULT_NM_SPECIAL_119_OFFSET_MS,
    )
    val nmStaffCreditsOffsetMs: StateFlow<Long> = _nmStaffCreditsOffsetMs.asStateFlow()

    @Volatile
    private var lastCalibratedNmStaffPath: String? = null

    fun ensureNmStaffCreditsOffsetCalibrated() {
        val dir = packDirectory ?: return
        val sha = com.mylittleblover.mkwplayer.ui.components.StaffCreditsProgressTheme.NM_SPECIAL_SHA256
        val file = java.io.File(dir, "audio/$sha")
        if (!file.isFile) return
        val signature = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        if (lastCalibratedNmStaffPath == signature) return
        lastCalibratedNmStaffPath = signature
        viewModelScope.launch(Dispatchers.IO) {
            val offset = com.mylittleblover.mkwplayer.ui.components.StaffCreditsOffsetCalibrator
                .calibrateFromPackFile(file, sha)
            _nmStaffCreditsOffsetMs.value = offset
        }
    }

    private var speakerEnabled: Boolean = false

    private fun currentLayerGains(plan: InteractivePlaybackPlan?): Map<Int, Double> {
        if (plan == null || plan.overlayTrackIndices.isEmpty()) return emptyMap()
        val db = if (speakerEnabled) 0.0 else -96.0
        return plan.overlayTrackIndices.associateWith { db }
    }

    private fun loadJukeboxCatalogSync(): com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog {
        val registry = com.mylittleblover.mkwplayer.pack.PackRegistry(
            java.io.File(getApplication<Application>().filesDir, PACK_ROOT),
        )
        return com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.load(registry, jukeboxMetadata)
    }

    private fun applyJukeboxCatalog(loaded: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog) {
        _ui.value = _ui.value.copy(jukebox = loaded)
        linearCoordinator.configureSessionStore(linearSessionStore)
        linearCoordinator.attach(getApplication(), loaded)
        refreshContinueListening()
    }

    private fun refreshJukeboxCatalog() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { loadJukeboxCatalogSync() }
            applyJukeboxCatalog(loaded)
        }
    }

    /** Plays a single Jukebox track by its pack key, freezing its source game's list as the queue. */
    fun openJukeboxTrack(key: String, languageTag: String = java.util.Locale.getDefault().toLanguageTag()) {
        if (isCeremonyLocked) return
        val catalog = jukebox
        if (!catalog.tracksByKey.containsKey(key)) {
            _ui.value = _ui.value.copy(
                status = str(com.mylittleblover.mkwplayer.R.string.player_err_open_track),
                detail = str(com.mylittleblover.mkwplayer.R.string.player_err_no_pack_track),
            )
            return
        }
        val game = catalog.gameForTrack(key) ?: run {
            _ui.value = _ui.value.copy(
                status = str(com.mylittleblover.mkwplayer.R.string.player_err_open_track),
                detail = str(com.mylittleblover.mkwplayer.R.string.player_err_no_pack_track),
            )
            return
        }
        val startIndex = game.items.indexOfFirst { it.key == key }.coerceAtLeast(0)
        playJukeboxQueue(game, startIndex, languageTag)
    }

    /** Plays a game's Jukebox tracks in OST order, freezing the queue at session start (#61). */
    fun playJukeboxQueue(
        game: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game,
        startIndex: Int,
        languageTag: String,
        shuffle: Boolean? = null,
    ) {
        if (isCeremonyLocked) return
        if (nativePerformance?.isFading == true) return
        val entries = game.items.map { item -> LinearPlaybackCoordinator.entryFor(item, game, languageTag) }
        if (entries.isEmpty()) return
        game.items.getOrNull(startIndex)?.jukeboxCode?.let { freeRoamEngine?.recordPlayed(it) }
        prepareForLinearPlayback()
        linearCoordinator.startQueue(
            entries = entries,
            startIndex = startIndex,
            shuffleOverride = shuffle,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.JUKEBOX_GAME,
                contextId = game.id,
                contextTitle = game.localizedName(languageTag),
                packFingerprints = currentPackFingerprints(),
            ),
        )
    }

    private fun installedJukeboxByCode(): Map<String, Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>> {
        val out = LinkedHashMap<String, Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>>()
        for (game in jukebox.games) {
            for (item in game.items) {
                val code = item.jukeboxCode?.trim().orEmpty()
                if (code.isNotEmpty() && code !in out) {
                    out[code] = item to game
                }
            }
        }
        return out
    }

    /** Selects the active v1.8.0 Free Roam Jukebox mode and live-updates upcoming tracks if Free Roam radio is playing. */
    fun selectFreeRoamMode(
        mode: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode,
        languageTag: String = java.util.Locale.getDefault().toLanguageTag(),
        contextTitle: String? = null,
    ) {
        if (_ui.value.freeRoamMode == mode) return
        _ui.value = _ui.value.copy(freeRoamMode = mode)
        val lin = linearCoordinator.state.value
        val engine = freeRoamEngine ?: return
        if (lin.active && lin.contextKind == LinearSessionStore.ContextKind.JUKEBOX_ALL && lin.contextId == FREE_ROAM_CONTEXT_ID) {
            val byCode = installedJukeboxByCode()
            val availableCodes = byCode.keys
            if (availableCodes.isNotEmpty()) {
                val supplier: () -> LinearQueue.Entry? = {
                    val nextCode = engine.pickNextFreeRoam(mode = mode, availableCodes = availableCodes)
                    nextCode?.let { byCode[it] }?.let { (item, game) ->
                        LinearPlaybackCoordinator.entryFor(item, game, languageTag)
                    }
                }
                linearCoordinator.updateDynamicSession(
                    contextTitle = contextTitle ?: lin.contextTitle,
                    supplier = supplier,
                    interTrackGapMs = engine.data.settings.freeRunIntervalMs,
                )
            }
        }
    }

    /**
     * Launches authentic MKW v1.8.0 Free Roam radio using `SoundJukeBoxPlaylist` ordered slot
     * tables, sequential cursor stepping, 9-track history anti-repeat, and 1.5 s inter-track gap.
     */
    fun playFreeRoamRadio(
        mode: com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog.FreeRoamMode = _ui.value.freeRoamMode,
        languageTag: String = java.util.Locale.getDefault().toLanguageTag(),
        contextTitle: String? = null,
    ): Boolean {
        if (isCeremonyLocked) return false
        if (nativePerformance?.isFading == true) return false
        _ui.value = _ui.value.copy(freeRoamMode = mode)
        val engine = freeRoamEngine ?: return false
        val byCode = installedJukeboxByCode()
        val availableCodes = byCode.keys
        if (availableCodes.isEmpty()) return false
        val initialCodes = engine.drawFreeRoamSequence(
            mode = mode,
            count = 12,
            availableCodes = availableCodes,
        )
        val initialEntries = initialCodes.mapNotNull { code ->
            byCode[code]?.let { (item, game) -> LinearPlaybackCoordinator.entryFor(item, game, languageTag) }
        }
        if (initialEntries.isEmpty()) return false
        val supplier: () -> LinearQueue.Entry? = {
            val activeMode = _ui.value.freeRoamMode
            val currentByCode = installedJukeboxByCode()
            val nextCode = engine.pickNextFreeRoam(mode = activeMode, availableCodes = currentByCode.keys)
            nextCode?.let { currentByCode[it] }?.let { (item, game) ->
                LinearPlaybackCoordinator.entryFor(item, game, languageTag)
            }
        }
        prepareForLinearPlayback()
        linearCoordinator.startQueue(
            entries = initialEntries,
            startIndex = 0,
            shuffleOverride = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.JUKEBOX_ALL,
                contextId = FREE_ROAM_CONTEXT_ID,
                contextTitle = contextTitle,
                packFingerprints = currentPackFingerprints(),
                allowShuffle = false,
                interTrackGapMs = engine.data.settings.freeRunIntervalMs,
                dynamicNextSupplier = supplier,
                dynamicTargetBuffer = 12,
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    /** Plays all tracks belonging to a Free Roam musical genre (#9). */
    fun playJukeboxGenre(
        genre: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.GenreInfo,
        startIndex: Int,
        shuffle: Boolean,
        languageTag: String,
    ) {
        if (isCeremonyLocked) return
        if (nativePerformance?.isFading == true) return
        val pairs = jukebox.itemsWithGamesByGenre(genre.id)
        if (pairs.isEmpty()) return
        val entries = pairs.map { (item, game) ->
            LinearPlaybackCoordinator.entryFor(item, game, languageTag).copy(
                title = item.displayTitle(languageTag),
                arrangement = item.displayArrangement(languageTag),
            )
        }
        val clampedStart = startIndex.coerceIn(0, entries.lastIndex)
        prepareForLinearPlayback()
        linearCoordinator.startQueue(
            entries = entries,
            startIndex = clampedStart,
            shuffleOverride = shuffle,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.JUKEBOX_ALL,
                contextId = "genre:${genre.id}",
                contextTitle = genre.displayName(languageTag),
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
    }

    /** Resolves the canonical Free Roam genre metadata for a queued/playing track, if any (#9). */
    fun genreForLinearEntry(entry: LinearQueue.Entry?): com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.GenreInfo? {
        entry ?: return null
        if (entry.sourceType != LinearSessionStore.SourceType.JUKEBOX) return null
        val key = (entry.sourceTrackKey ?: entry.key).removePrefix(PlaylistItemRef.PREFIX_JUKEBOX)
        val item = jukebox.itemsByKey[key]
            ?: jukebox.allItems.firstOrNull { it.first.track.primary.sha256 == entry.assetSha256 }?.first
            ?: return null
        return item.genre
    }

    // ---- Track Markers (#16) ----
    private val trackMarkerStore by lazy {
        com.mylittleblover.mkwplayer.library.TrackMarkerStore.fromContext(getApplication())
    }
    private val _trackMarkers = MutableStateFlow<Map<String, List<com.mylittleblover.mkwplayer.library.TrackMarker>>>(emptyMap())
    val trackMarkers: StateFlow<Map<String, List<com.mylittleblover.mkwplayer.library.TrackMarker>>> = _trackMarkers.asStateFlow()

    @Volatile private var pendingMarkerSeekMs: Long? = null
    @Volatile private var pendingMarkerSeekAtMs: Long = 0L

    fun reloadTrackMarkers() {
        viewModelScope.launch(Dispatchers.IO) {
            val grouped = trackMarkerStore.allMarkers()
                .groupBy { it.trackId }
                .mapValues { (_, list) -> list.sortedBy { it.positionMs } }
            _trackMarkers.value = grouped
        }
    }

    fun markerTrackIdFor(entry: LinearQueue.Entry?): String? = canonicalFavoriteRef(entry)

    fun currentPositionMs(): Long {
        val sr = rate.coerceAtLeast(1)
        val frames = session.state.value.positionFrames.takeIf { it > 0L }
            ?: (_progressUi.value.progress.toDouble() * totalFrames.coerceAtLeast(0L)).toLong()
        return ((frames.coerceAtLeast(0L) * 1000L) / sr).coerceAtLeast(0L)
    }

    fun addTrackMarker(trackId: String, positionMs: Long, label: String, colorArgb: Long) {
        if (trackId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            trackMarkerStore.add(
                trackId = trackId,
                positionMs = positionMs.coerceAtLeast(0L),
                label = label,
                colorArgb = colorArgb,
            )
            val grouped = trackMarkerStore.allMarkers()
                .groupBy { it.trackId }
                .mapValues { (_, list) -> list.sortedBy { it.positionMs } }
            _trackMarkers.value = grouped
        }
    }

    fun deleteTrackMarker(markerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            trackMarkerStore.delete(markerId)
            val grouped = trackMarkerStore.allMarkers()
                .groupBy { it.trackId }
                .mapValues { (_, list) -> list.sortedBy { it.positionMs } }
            _trackMarkers.value = grouped
        }
    }

    fun seekToPositionMs(positionMs: Long) {
        if (isCeremonyLocked) return
        val sr = rate.coerceAtLeast(1)
        if (totalFrames <= 0L) return
        val targetFrame = ((positionMs.coerceAtLeast(0L).toDouble() / 1000.0) * sr).toLong()
            .coerceIn(0L, (totalFrames - 1L).coerceAtLeast(0L))
        if (_ui.value.linearMode) {
            linearCoordinator.seekToFrame(targetFrame)
        } else {
            viewModelScope.launch {
                publish(session.seekAsync(targetFrame))
            }
        }
    }

    /** Schedules a seek to [positionMs] once the next linear track finishes loading (or immediately if [trackId] is already active). */
    fun scheduleMarkerSeek(positionMs: Long, trackId: String? = null) {
        val clamped = positionMs.coerceAtLeast(0L)
        if (trackId != null && _ui.value.linearMode && canonicalFavoriteRef(_ui.value.linearTrack) == trackId && totalFrames > 0L) {
            seekToPositionMs(clamped)
            return
        }
        pendingMarkerSeekMs = clamped
        pendingMarkerSeekAtMs = System.currentTimeMillis()
    }

    private fun consumePendingMarkerSeekMs(): Long? {
        val ms = pendingMarkerSeekMs ?: return null
        pendingMarkerSeekMs = null
        val age = System.currentTimeMillis() - pendingMarkerSeekAtMs
        return if (age in 0L..12_000L) ms else null
    }

    /** Plays all installed Jukebox arrangements; when [shuffle] is true, uses authentic MKW v1.8.0 Free Roam selection. */
    fun playAllJukebox(languageTag: String, shuffle: Boolean = false, contextTitle: String? = null) {
        if (isCeremonyLocked) return
        if (nativePerformance?.isFading == true) return
        if (shuffle && playFreeRoamRadio(_ui.value.freeRoamMode, languageTag, contextTitle)) {
            return
        }
        val catalog = jukebox
        val entries = catalog.games.flatMap { game ->
            game.items.map { item ->
                LinearPlaybackCoordinator.entryFor(item, game, languageTag).copy(
                    title = item.displayTitle(languageTag),
                    arrangement = item.displayArrangement(languageTag),
                )
            }
        }
        if (entries.isEmpty()) return
        prepareForLinearPlayback()
        linearCoordinator.startQueue(
            entries = entries,
            startIndex = 0,
            shuffleOverride = shuffle,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.JUKEBOX_ALL,
                contextId = "all",
                contextTitle = contextTitle,
                packFingerprints = currentPackFingerprints(),
            ),
        )
    }

    /** Inserts [item] at the front (LIFO) of the temporary "Play next" queue (#63, #64, blocked by #68 on fixed routes). */
    fun playJukeboxNext(
        item: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem,
        game: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game,
        languageTag: String,
    ) {
        if (isCeremonyLocked) return
        if (!_ui.value.allowPlayNext) return
        val entry = LinearPlaybackCoordinator.entryFor(item, game, languageTag).copy(
            title = item.displayTitle(languageTag),
            arrangement = item.displayArrangement(languageTag),
        )
        val wasIdle = !_ui.value.canStop && _ui.value.queue.current == null
        if (wasIdle) {
            prepareForLinearPlayback()
        }
        linearCoordinator.enqueueNext(entry)
    }

    fun skipNext() {
        if (isCeremonyLocked) return
        linearCoordinator.skipNext()
    }

    fun skipPrevious() {
        if (isCeremonyLocked) return
        linearCoordinator.skipPrevious()
    }

    fun toggleShuffle() {
        if (isCeremonyLocked) return
        linearCoordinator.toggleShuffle()
    }

    fun cycleRepeatMode() {
        if (isCeremonyLocked) return
        linearCoordinator.cycleRepeatMode()
    }

    fun replayOrResumeLinear() {
        if (isCeremonyLocked) return
        if (_ui.value.canResume) {
            resume()
        } else {
            linearCoordinator.replayCurrent()
        }
    }

    fun removeTempQueueItem(index: Int) {
        if (isCeremonyLocked) return
        linearCoordinator.removeTempAt(index)
    }

    fun jumpToTempQueueItem(index: Int) {
        if (isCeremonyLocked) return
        linearCoordinator.jumpToTemp(index)
    }

    fun jumpToUpcomingQueueItem(index: Int) {
        if (isCeremonyLocked) return
        linearCoordinator.jumpToUpcoming(index)
    }

    private fun prepareForLinearPlayback() {
        packPlaybackRequest++
        nativePerformance = null
        interactivePlan = null
        performance = null
        flow = null
        flowCompiled = null
        flowBytes = emptyMap()
        _ui.value = _ui.value.copy(
            flow = null,
            linearMode = true,
            artworkName = null,
            detail = null,
        )
    }

    /**
     * Applies the master output trim to the running session.
     *
     * Idempotent on purpose: the screen calls this from a `LaunchedEffect` keyed on the chosen value, so it runs
     * once at start-up and again on every change. Re-applying the same numbers costs a queued command and
     * changes nothing audible, which is cheaper than tracking whether it was applied and getting that wrong.
     */
    private var playbackBehavior: com.mylittleblover.mkwplayer.audio.PlaybackBehaviorConfig =
        com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings.current(getApplication())

    fun applyOutputTrim(trim: com.mylittleblover.mkwplayer.audio.OutputTrim) {
        PlaybackHolder.get().setOutputTrim(trim)
        _ui.value = _ui.value.copy(outputTrimEnabled = trim.gainDb > 0.5)
    }

    fun setNonInteractiveLoopConfig(config: com.mylittleblover.mkwplayer.audio.NonInteractiveLoopConfig) {
        val normalized = config.normalized()
        linearCoordinator.setNonInteractiveLoopConfig(normalized)
        _ui.value = _ui.value.copy(nonInteractiveLoopConfig = normalized)
    }

    fun setExportFolderUri(uri: android.net.Uri?, displayLabel: String? = null) {
        val ctx = getApplication<Application>()
        if (uri == null) {
            com.mylittleblover.mkwplayer.audio.AudioExportSettings.resetExportLocation(ctx)
            _ui.value = _ui.value.copy(exportFolderDisplay = com.mylittleblover.mkwplayer.audio.AudioExportSettings.DEFAULT_RELATIVE_PATH)
            return
        }
        runCatching {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val label = displayLabel?.takeIf { it.isNotBlank() }
            ?: runCatching {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
                    ?.substringAfter(':')
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
            ?: uri.lastPathSegment
            ?: com.mylittleblover.mkwplayer.audio.AudioExportSettings.DEFAULT_RELATIVE_PATH
        com.mylittleblover.mkwplayer.audio.AudioExportSettings.setExportLocation(ctx, uri.toString(), label)
        _ui.value = _ui.value.copy(exportFolderDisplay = label)
    }

    fun applyPlaybackBehavior(config: com.mylittleblover.mkwplayer.audio.PlaybackBehaviorConfig) {
        playbackBehavior = config
        setLiveVisualizerEnabled(config.liveVisualizerEnabled)
    }

    fun setPlaybackRate(config: com.mylittleblover.mkwplayer.audio.PlaybackRateConfig) {
        val saved = com.mylittleblover.mkwplayer.audio.AudioEffectSettings.saveRate(getApplication(), config)
        session.setPlaybackRate(saved)
        _ui.value = _ui.value.copy(playbackRate = saved)
    }

    fun resetPlaybackRate() {
        setPlaybackRate(com.mylittleblover.mkwplayer.audio.PlaybackRateConfig())
    }

    fun setMasterDsp(config: com.mylittleblover.mkwplayer.audio.MasterDspStateConfig) {
        val saved = com.mylittleblover.mkwplayer.audio.AudioEffectSettings.saveDsp(getApplication(), config)
        session.setMasterDsp(saved)
        _ui.value = _ui.value.copy(dspConfig = saved)
    }

    fun setLiveVisualizerEnabled(enabled: Boolean) {
        val updatedBehavior = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings
            .setLiveVisualizerEnabled(getApplication(), enabled)
        playbackBehavior = updatedBehavior
        session.setVisualizerEnabled(enabled)
        _ui.value = _ui.value.copy(liveVisualizerEnabled = enabled)
        syncVisualizerPolling(session.state.value.playing)
    }

    fun syncPreferencesAfterBackupRestore() {
        val ctx = getApplication<Application>()
        val trim = com.mylittleblover.mkwplayer.audio.OutputTrimSettings.current(ctx)
        val behavior = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings.current(ctx)
        val dsp = com.mylittleblover.mkwplayer.audio.AudioEffectSettings.currentDsp(ctx)
        val rateCfg = com.mylittleblover.mkwplayer.audio.AudioEffectSettings.currentRate(ctx)
        val exportCfg = com.mylittleblover.mkwplayer.audio.AudioExportSettings.current(ctx)
        playbackBehavior = behavior
        session.setOutputTrim(trim)
        session.setMasterDsp(dsp)
        session.setPlaybackRate(rateCfg)
        session.setVisualizerEnabled(behavior.liveVisualizerEnabled)
        linearCoordinator.setNonInteractiveLoopConfig(behavior.nonInteractiveLoopConfig)
        com.mylittleblover.mkwplayer.library.PlayStatsRepository.reload(ctx)
        reloadTrackMarkers()
        _ui.value = _ui.value.copy(
            dspConfig = dsp,
            playbackRate = rateCfg,
            liveVisualizerEnabled = behavior.liveVisualizerEnabled,
            outputTrimEnabled = trim.gainDb > 0.5,
            nonInteractiveLoopConfig = behavior.nonInteractiveLoopConfig,
            exportFolderDisplay = exportCfg.exportDisplayPath
                ?: com.mylittleblover.mkwplayer.audio.AudioExportSettings.DEFAULT_RELATIVE_PATH,
            playStats = com.mylittleblover.mkwplayer.library.PlayStatsRepository.readSnapshot(ctx),
        )
        syncVisualizerPolling(session.state.value.playing)
    }

    fun removeUpcomingQueueItem(index: Int) {
        if (isCeremonyLocked) return
        linearCoordinator.removeUpcomingAt(index)
    }

    fun moveTempQueueItem(from: Int, to: Int) {
        if (isCeremonyLocked) return
        linearCoordinator.moveTemp(from, to)
    }

    fun moveUpcomingQueueItem(from: Int, to: Int) {
        if (isCeremonyLocked) return
        linearCoordinator.moveUpcoming(from, to)
    }

    fun clearUpcomingQueue() {
        if (isCeremonyLocked) return
        linearCoordinator.clearUpcoming()
    }

    fun setAbLoopA() {
        if (isCeremonyLocked) return
        linearCoordinator.setAbLoopPointA()
    }

    fun setAbLoopB() {
        if (isCeremonyLocked) return
        linearCoordinator.setAbLoopPointB()
    }

    fun clearAbLoop() {
        if (isCeremonyLocked) return
        linearCoordinator.clearAbLoop()
    }

    fun startSleepTimerMinutes(minutes: Int) {
        val cleanMinutes = minutes.coerceIn(1, 480)
        PlaybackTimers.engine.startSleepCountdown(cleanMinutes * 60_000L)
    }

    fun startSleepTimerAfterCurrentTrack() {
        PlaybackTimers.engine.startSleepAfterCurrentTrack()
    }

    fun extendSleepTimerMinutes(minutes: Int = 5) {
        PlaybackTimers.engine.extendSleep(minutes.coerceIn(1, 120) * 60_000L)
    }

    fun cancelSleepTimer() {
        PlaybackTimers.engine.cancelSleep()
    }

    fun startInteractiveTimerSeconds(seconds: Long, action: InteractiveTimedAction) {
        if (isCeremonyLocked || _ui.value.linearMode || !_ui.value.canStop) return
        val courseKey = currentStageCode ?: interactivePlan?.name ?: _ui.value.title.takeIf { it.isNotBlank() }
        PlaybackTimers.engine.startInteractiveTimer(
            durationMs = seconds.coerceIn(1L, 10_800L) * 1_000L,
            action = action,
            courseKey = courseKey,
            currentlyPlaying = session.state.value.playing,
        )
    }

    fun cancelInteractiveTimer() {
        PlaybackTimers.engine.cancelInteractiveTimer()
    }

    private fun recordCanonicalPlayStat(canonicalId: String) {
        val clean = canonicalId.trim()
        if (clean.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            com.mylittleblover.mkwplayer.library.PlayStatsRepository.recordPlay(ctx, clean)
        }
    }

    private fun fadeOutLinearAndStop() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (isCeremonyLocked || !session.state.value.active) return@launch
            val savedTrim = com.mylittleblover.mkwplayer.audio.OutputTrimSettings.current(getApplication())
            try {
                if (session.state.value.playing) {
                    val steps = listOf(-3.0, -7.0, -12.0, -18.0, -26.0, -38.0)
                    for (delta in steps) {
                        if (!session.state.value.active || !session.state.value.playing) break
                        session.setOutputTrim(
                            com.mylittleblover.mkwplayer.audio.OutputTrim(
                                gainDb = (savedTrim.gainDb + delta).coerceAtLeast(-54.0),
                                ceilingDb = savedTrim.ceilingDb,
                            ),
                        )
                        delay(140L)
                    }
                }
                stopTransport(cancelPendingOpen = true)
            } finally {
                session.setOutputTrim(savedTrim)
            }
        }
    }

    private fun triggerInteractiveTimedAction(action: InteractiveTimedAction) {
        if (isCeremonyLocked || _ui.value.linearMode || !_ui.value.canStop) return
        when (action) {
            is InteractiveTimedAction.FadeStop -> {
                if (action.fadeSeconds <= 0) {
                    stop()
                } else if (canFadeStop) {
                    val closest = listOf(1, 3, 5, 10).minByOrNull { kotlin.math.abs(it - action.fadeSeconds) } ?: 3
                    fadeStopPerformance(closest)
                } else {
                    stop()
                }
            }
            InteractiveTimedAction.Advance -> advancePerformance()
            InteractiveTimedAction.ToggleFast -> toggleFast()
            is InteractiveTimedAction.TakeOutro -> {
                if (action.number != null) takeOutro(action.number) else takeOutro()
            }
            is InteractiveTimedAction.SelectRegion -> selectMusicRegion(action.region)
            is InteractiveTimedAction.SelectVersion -> selectRecordingVersion(action.version)
            InteractiveTimedAction.ToggleSpeakerOverlay -> toggleSpeakerOverlay()
        }
    }

    private fun syncVisualizerPolling(playing: Boolean) {
        val wantPoll = _ui.value.liveVisualizerEnabled && playing && session.state.value.active
        if (!wantPoll) {
            visualizerJob?.cancel()
            visualizerJob = null
            if (_spectrumBars.value.any { it != 0f }) {
                _spectrumBars.value = FloatArray(SPECTRUM_BAND_COUNT)
            }
            return
        }
        if (visualizerJob?.isActive == true) return
        visualizerJob = viewModelScope.launch {
            val scratch = FloatArray(SPECTRUM_BAND_COUNT)
            while (true) {
                if (_ui.value.liveVisualizerEnabled && session.state.value.playing && session.visualizerSpectrum(scratch)) {
                    _spectrumBars.value = scratch.copyOf()
                } else {
                    break
                }
                delay(40L)
            }
            _spectrumBars.value = FloatArray(SPECTRUM_BAND_COUNT)
            visualizerJob = null
        }
    }

    private fun currentInteractiveOptions(allowPreludeToFast: Boolean = true): InteractivePlaybackPlan.Options =
        InteractivePlaybackPlan.Options(
            segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
            perfectBeatEnabled = playbackBehavior.perfectBeatEnabled,
            allowPreludeToFast = allowPreludeToFast,
        )

    /** A pack that has been installed and verified, kept together so a track can be prepared on demand. */
    private data class InstalledPack(val manifest: PackManifest, val directory: java.io.File)

    /**
     * The running stage performance, or null when the listener is driving sections by hand.
     *
     * One driver at a time: two would both advance on the same transport end and between them skip every
     * other section.
     */
    private var performance: PerformanceDriver? = null
    private var pollJob: Job? = null
    private var totalFrames: Long = 0
    private var rate: Int = 0

    /**
     * The shared audio session.
     *
     * Not owned by this view model any more: a foreground service has to be able to keep
     * playing after the activity is gone, so the engine lives in the process-wide holder
     * and both the service and this class read it.
     */
    private val session: PlaybackSession get() = PlaybackHolder.get()

    private val store = LibraryStore(java.io.File(application.filesDir, "library.json"))

    private val unlockCoordinator: UnlockProgressCoordinator
        get() = PlaybackHolder.unlock()

    private val unlockTestStore = UnlockTestStore(java.io.File(application.filesDir, "unlock-test-mode.json"))
    private var naturalUnlockUi: UnlockUi? = null
    private var testingCeremonyPlaying: Boolean = false
    private val isCeremonyLocked: Boolean
        get() = testingCeremonyPlaying ||
            naturalUnlockUi?.ceremonyPlaying == true ||
            _ui.value.unlock?.ceremonyPlaying == true ||
            session.uninterruptible

    private var lastTimingSnapshotMs: Long = 0L

    private fun projectUnlock(natural: UnlockUi?): UnlockUi? {
        naturalUnlockUi = natural
        // A research pack with an unfinished scope must never earn the formal gate by accident.
        val formal = if (pack?.manifest?.unlock?.frozen == false) natural?.copy(visible = false, canEnter = false) else natural
        val projected = _ui.value.unlockTestMode.apply(formal)
        return if (testingCeremonyPlaying) {
            (projected ?: formal)?.copy(ceremonyPlaying = true, canEnter = false)
                ?: UnlockUi(
                    progress = "",
                    remaining = null,
                    ruleNotice = "",
                    offered = true,
                    ceremonyPlaying = true,
                    ceremonyCompleted = false,
                    congratulationOwed = false,
                    canEnter = false,
                    visible = false,
                    hiddenReason = null,
                )
        } else {
            projected
        }
    }

    private fun isStaffCreditsEffectivelyUnlocked(): Boolean =
        _ui.value.staffCreditsUnlocked || _ui.value.unlockTestMode == UnlockTestMode.FORCE_UNLOCKED

    private fun isExtraVisible(id: String, specialUnlocked: Boolean = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)): Boolean =
        com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.isVisible(
            id = id,
            specialUnlocked = specialUnlocked,
            staffCreditsUnlocked = isStaffCreditsEffectivelyUnlocked(),
        )

    fun setUnlockTestMode(mode: UnlockTestMode) {
        if (isCeremonyLocked) return
        _ui.value = _ui.value.copy(unlockTestMode = mode)
        _ui.value = _ui.value.copy(unlock = projectUnlock(naturalUnlockUi))
        val activeStage = currentStageCode
            ?: _ui.value.linearTrack?.courseCode
            ?: _ui.value.linearTrack?.key?.let(com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog::extraIdFromEntryKey)
            ?: ""
        val activeExtraLocked = activeStage.isNotEmpty() && !isExtraVisible(activeStage)
        val activeSpecialRouteLocked = _ui.value.linearContextId == "special_cup" &&
            !com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)
        if (isSpecialStageLocked(activeStage) || activeExtraLocked || activeSpecialRouteLocked) {
            releaseTransport()
        }
        refreshContinueListening()
        viewModelScope.launch(Dispatchers.IO) {
            unlockTestStore.write(mode).onFailure { err ->
                withContext(Dispatchers.Main.immediate) {
                    _ui.value = _ui.value.copy(detail = err.message)
                }
            }
        }
    }

    private fun isSpecialStageLocked(code: String): Boolean =
        code.removePrefix("BGM_Stage_").removeSuffix("_00").equals("Rainbow", true) &&
            !com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)

    /**
     * Feeds a transport sample to the shared unlock coordinator.
     *
     * Only playing samples count, and only for the stage the sample belongs to. Counts both
     * interactive stage playback and linear course medleys / route stops / presets.
     */
    private fun creditListening(state: PlaybackSession.State) {
        if (isCeremonyLocked) return
        unlockCoordinator.creditSample(
            state = state,
            isCeremonyLocked = isCeremonyLocked,
            seekSequence = session.seekSequence,
            playbackSpeed = session.currentPlaybackSpeed,
        )
        val projected = projectUnlock(unlockCoordinator.naturalUnlockUi.value)
        if (projected != _ui.value.unlock) {
            _ui.value = _ui.value.copy(unlock = projected)
        }
    }

    init {
        val appCtx = getApplication<Application>()
        linearCoordinator.configureSessionStore(linearSessionStore)
        linearCoordinator.attach(appCtx, _ui.value.jukebox)
        val initTrim = com.mylittleblover.mkwplayer.audio.OutputTrimSettings.current(appCtx)
        val initRate = com.mylittleblover.mkwplayer.audio.AudioEffectSettings.currentRate(appCtx)
        val initDsp = com.mylittleblover.mkwplayer.audio.AudioEffectSettings.currentDsp(appCtx)
        val initBehavior = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings.current(appCtx)
        playbackBehavior = initBehavior
        session.setOutputTrim(initTrim)
        session.setPlaybackRate(initRate)
        session.setMasterDsp(initDsp)
        session.setVisualizerEnabled(initBehavior.liveVisualizerEnabled)
        val initExportCfg = com.mylittleblover.mkwplayer.audio.AudioExportSettings.current(appCtx)
        reloadTrackMarkers()
        _ui.value = _ui.value.copy(
            playbackRate = initRate,
            dspConfig = initDsp,
            liveVisualizerEnabled = initBehavior.liveVisualizerEnabled,
            playStats = com.mylittleblover.mkwplayer.library.PlayStatsRepository.stats.value,
            sleepTimer = PlaybackTimers.sleepState.value,
            interactiveTimer = PlaybackTimers.interactiveState.value,
            staffCreditsUnlocked = unlockCoordinator.isStaffCreditsUnlocked(),
            nonInteractiveLoopConfig = initBehavior.nonInteractiveLoopConfig,
            outputTrimEnabled = initTrim.gainDb > 0.5,
            exportFolderDisplay = initExportCfg.exportDisplayPath
                ?: com.mylittleblover.mkwplayer.audio.AudioExportSettings.DEFAULT_RELATIVE_PATH,
        )

        PlaybackTimers.onSleepExpired = {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (isCeremonyLocked || !session.state.value.active) return@launch
                if (!_ui.value.linearMode && canFadeStop) {
                    fadeStopPerformance(3)
                } else {
                    fadeOutLinearAndStop()
                }
            }
        }
        PlaybackTimers.onInteractiveActionTriggered = { action ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                triggerInteractiveTimedAction(action)
            }
        }
        viewModelScope.launch {
            unlockCoordinator.naturalUnlockUi.collect { natural ->
                val projected = projectUnlock(natural)
                if (projected != _ui.value.unlock) {
                    _ui.value = _ui.value.copy(unlock = projected)
                }
            }
        }
        viewModelScope.launch {
            unlockCoordinator.staffCreditsUnlocked.collect { unlocked ->
                if (_ui.value.staffCreditsUnlocked != unlocked) {
                    _ui.value = _ui.value.copy(staffCreditsUnlocked = unlocked)
                }
            }
        }
        viewModelScope.launch {
            var lastSeenEvent = unlockCoordinator.staffCreditsLinearUnlockEvent.value
            unlockCoordinator.staffCreditsLinearUnlockEvent.collect { eventId ->
                if (eventId > lastSeenEvent) {
                    lastSeenEvent = eventId
                    val lang = getApplication<Application>().resources.configuration.locales[0].toLanguageTag()
                    val entry = buildStaffCreditsEntry(lang)
                    if (entry != null) {
                        linearCoordinator.enqueueNext(entry)
                    }
                    _ui.value = _ui.value.copy(
                        staffCreditsUnlocked = true,
                        staffCreditsToastEvent = _ui.value.staffCreditsToastEvent + 1L,
                    )
                }
            }
        }
        viewModelScope.launch {
            linearCoordinator.nonInteractiveLoopConfig.collect { cfg ->
                if (_ui.value.nonInteractiveLoopConfig != cfg) {
                    _ui.value = _ui.value.copy(nonInteractiveLoopConfig = cfg)
                }
            }
        }
        viewModelScope.launch {
            unlockCoordinator.needsNotificationPermission.collect { needed ->
                if (needed) {
                    requestNotificationPermissionIfNeeded()
                    unlockCoordinator.consumeNotificationPermissionEvent()
                }
            }
        }
        viewModelScope.launch {
            unlockCoordinator.saveError.collect { problem ->
                if (problem != null) {
                    _ui.value = _ui.value.copy(detail = problem)
                }
            }
        }
        viewModelScope.launch {
            com.mylittleblover.mkwplayer.library.PlayStatsRepository.stats.collect { stats ->
                _ui.value = _ui.value.copy(playStats = stats)
            }
        }
        viewModelScope.launch {
            PlaybackTimers.sleepState.collect { timer ->
                _ui.value = _ui.value.copy(sleepTimer = timer)
            }
        }
        viewModelScope.launch {
            PlaybackTimers.interactiveState.collect { timer ->
                _ui.value = _ui.value.copy(interactiveTimer = timer)
            }
        }

        PackImportCoordinator.configureCommitHandler { installed ->
            withContext(Dispatchers.Main.immediate) {
                applyCommittedPackUpdate(installed)
            }
        }
        linearCoordinator.onSessionCompletedNaturally = { endedEntry ->
            if (endedEntry?.key == CEREMONY_ENTRY_KEY || isCeremonyLocked) {
                onCeremonyAudioCompleted()
            }
        }

        viewModelScope.launch {
            data class PrimaryInitResult(
                val testMode: UnlockTestMode,
                val testModeError: String?,
                val loaded: LibraryStore.Loaded,
                val merged: InternalPackLibrary.MergedLibrary?,
                val builtBrowse: com.mylittleblover.mkwplayer.library.BrowseCatalog?,
                val restoredUnlock: UnlockStore.Loaded,
            )
            // Start heavy secondary catalogs concurrently without delaying initial Home display.
            val catalogDef = async(Dispatchers.IO) {
                runCatching {
                    appCtx.assets.open(CATALOG_ASSET).bufferedReader().use {
                        CatalogLoader.parse(it.readText())
                    }
                }.getOrNull()
            }
            val poolDef = async(Dispatchers.IO) {
                com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.loadInternalPool(appCtx.filesDir)
            }
            val jukeboxMetaDef = async(Dispatchers.IO) { jukeboxMetadata }

            val primaryResult = withContext(Dispatchers.IO) {
                coroutineScope {
                    val packRoot = java.io.File(appCtx.filesDir, PACK_ROOT)
                    val statsDef = async { com.mylittleblover.mkwplayer.library.PlayStatsRepository.ensureLoadedSync(appCtx) }
                    val testResDef = async { unlockTestStore.read() }
                    val loadedDef = async { store.load() }
                    val mergedDef = async {
                        InternalPackLibrary(packRoot).loadOrMaterialize().also { mergedLib ->
                            com.mylittleblover.mkwplayer.ui.art.PackArtwork.warmArtworkIndex(mergedLib?.directory)
                        }
                    }
                    val anchorsDef = async {
                        mapAnchors.also { com.mylittleblover.mkwplayer.ui.art.MapAnchorsCache.seed(it) }
                    }
                    val officialDef = async {
                        anchorsDef.await()
                        officialRouteCatalog
                    }
                    val namesDef = async { localizedNames }
                    val extraDef = async { extraCatalog }
                    val unlockDef = async {
                        UnlockStore(java.io.File(appCtx.filesDir, SPECIAL_CUP_FILE)).load()
                    }
                    val checkpointDef = async { PackImportCoordinator.checkPendingCheckpoint(appCtx, packRoot) }

                    val anchors = anchorsDef.await()
                    val official = officialDef.await()
                    val names = namesDef.await()
                    extraDef.await()
                    val builtBrowse = if (official != null && names != null && anchors != null) {
                        com.mylittleblover.mkwplayer.library.BrowseCatalog.create(official, names, anchors)
                    } else {
                        null
                    }
                    statsDef.await()
                    checkpointDef.await()
                    val testRes = testResDef.await()
                    PrimaryInitResult(
                        testMode = testRes.getOrDefault(UnlockTestMode.NORMAL),
                        testModeError = testRes.exceptionOrNull()?.message,
                        loaded = loadedDef.await(),
                        merged = mergedDef.await(),
                        builtBrowse = builtBrowse,
                        restoredUnlock = unlockDef.await(),
                    )
                }
            }

            primaryResult.merged?.let { merged ->
                pack = InstalledPack(merged.manifest, merged.directory)
                rebuildPlayableStageSet(merged.manifest)
                mergedFingerprints = merged.activePackFingerprints
            } ?: rebuildPlayableStageSet(null)
            _browseCatalog.value = primaryResult.builtBrowse
            _ui.value = _ui.value.copy(
                unlockTestMode = primaryResult.testMode,
                tracks = primaryResult.loaded.rows.map {
                    Track(
                        key = it.key,
                        name = it.name,
                        frames = it.frames,
                        sampleRate = it.sampleRate,
                        playable = it.playable,
                        reason = it.reason,
                    )
                },
                pack = primaryResult.merged?.let { merged ->
                    PackUi(
                        name = merged.manifest.name,
                        trackCount = merged.manifest.tracks.size,
                        playableCount = PackPlayback.playableTracks(merged.manifest).size,
                    )
                },
                libraryReady = true,
                detail = primaryResult.testModeError ?: primaryResult.loaded.problem ?: _ui.value.detail,
            )
            establishCup(primaryResult.loaded.tracks, primaryResult.restoredUnlock)

            // Finalize Jukebox & legacy catalog without blocking first Home render.
            val secondaryJob = launch(Dispatchers.IO) {
                val jukeCatalog = primaryResult.merged?.let { merged ->
                    com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.buildFromManifest(
                        merged.manifest,
                        merged.directory,
                        jukeboxMetaDef.await(),
                    )
                } ?: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog(emptyList(), emptyMap())
                val parsedCatalog = catalogDef.await()
                val roadPool = poolDef.await()
                withContext(Dispatchers.Main) {
                    roadJukeboxPool = roadPool
                    parsedCatalog?.let {
                        browser = CatalogBrowser(it)
                        refreshCatalog()
                        _ui.value = _ui.value.copy(catalog = it)
                    }
                    applyJukeboxCatalog(jukeCatalog)
                }
            }
            launch(Dispatchers.IO) {
                secondaryJob.join()
                minfCatalog
                interactiveIdentities
                stageConnections
                routeMusicCatalog
                courseMedleyCatalog
                extraMedleyCatalog
                loadFinalLapFanfareBytes()
            }
        }

        viewModelScope.launch {
            PackImportCoordinator.state.collect { imp ->
                val importingNow = imp.isActive
                val progressText = when {
                    imp.phase == PackImportPhase.COMMITTING -> str(com.mylittleblover.mkwplayer.R.string.settings_import_applying)
                    imp.totalMembers > 0 -> "${imp.processedMembers}/${imp.totalMembers} · ${imp.currentMember}"
                    imp.isActive -> str(com.mylittleblover.mkwplayer.R.string.settings_import_reading_fmt, imp.displayName)
                    else -> ""
                }
                val currentPack = _ui.value.pack
                _ui.value = _ui.value.copy(
                    busy = importingNow,
                    status = when (imp.phase) {
                        PackImportPhase.FAILED -> str(com.mylittleblover.mkwplayer.R.string.settings_import_failed_status)
                        PackImportPhase.CANCELLED -> str(com.mylittleblover.mkwplayer.R.string.settings_import_cancelled_status)
                        PackImportPhase.COMPLETED -> str(com.mylittleblover.mkwplayer.R.string.settings_import_updated_status)
                        else -> _ui.value.status
                    },
                    detail = when (imp.phase) {
                        PackImportPhase.FAILED,
                        PackImportPhase.PAUSED_NEEDS_REPICK -> imp.errorReason
                        PackImportPhase.CANCELLED -> imp.errorReason ?: _ui.value.detail
                        else -> _ui.value.detail
                    },
                    importState = imp,
                    pack = currentPack?.copy(importing = importingNow, progress = progressText)
                        ?: if (importingNow) {
                            PackUi(
                                name = imp.displayName,
                                trackCount = 0,
                                playableCount = 0,
                                importing = true,
                                progress = progressText,
                            )
                        } else null,
                )
            }
        }

        viewModelScope.launch {
            linearCoordinator.state.collect { linear ->
                if (linear.active && linear.current != null) {
                    flow = null
                    flowCompiled = null
                    flowBytes = emptyMap()
                    interactivePlan = null
                    nativePerformance = null
                    performance = null
                    if (linear.totalFrames > 0L && linear.sampleRate > 0) {
                        totalFrames = linear.totalFrames
                        rate = linear.sampleRate
                    }
                    _ui.value = _ui.value.copy(
                        selectedKey = linear.current.key,
                        flow = null,
                        trackLoading = false,
                        loadingTrackCode = null,
                        loadingTrackTitle = null,
                        activeFavoriteId = canonicalFavoriteRef(linear.current),
                        title = linear.current.title,
                        status = if (linear.loading) str(com.mylittleblover.mkwplayer.R.string.player_status_loading) else _ui.value.status,
                        total = EngineSnapshot.formatPosition(totalFrames, rate),
                        linearMode = true,
                        linearTrack = linear.current,
                        queue = linear.queue,
                        canSeek = !isCeremonyLocked && totalFrames > 0L,
                        totalFrames = totalFrames,
                        sampleRate = rate,
                        artworkName = linear.current.artworkName
                            ?: linear.current.courseCode?.let { extraCatalog.courseArtwork[it.removeSuffix("_00")] },
                        linearContextKind = linear.contextKind,
                        linearContextId = linear.contextId,
                        linearContextTitle = linear.contextTitle,
                        allowPlayNext = linear.allowPlayNext,
                        allowShuffle = linear.allowShuffle,
                        allowSingleRepeat = linear.allowSingleRepeat,
                        allowRepeatToggle = linear.allowRepeatToggle,
                        abLoopStartFrame = linear.abLoopStartFrame,
                        abLoopEndFrame = linear.abLoopEndFrame,
                        isLoopingTrack = linear.isLoopingTrack,
                        nonInteractiveLoopConfig = linear.nonInteractiveLoopConfig,
                        continueListening = null,
                        detail = linear.error,
                    )
                    if (!linear.loading && linear.error == null) {
                        val seekMs = consumePendingMarkerSeekMs()
                        if (seekMs != null && linear.totalFrames > 0L && linear.sampleRate > 0) {
                            val targetFrame = ((seekMs.toDouble() / 1000.0) * linear.sampleRate).toLong()
                                .coerceIn(0L, (linear.totalFrames - 1L).coerceAtLeast(0L))
                            linearCoordinator.seekToFrame(targetFrame)
                        }
                        requestNotificationPermissionIfNeeded()
                        startPolling()
                    }
                } else {
                    if (isCeremonyLocked && linear.error != null) {
                        session.uninterruptible = false
                        val wasTesting = testingCeremonyPlaying
                        testingCeremonyPlaying = false
                        if (unlockCoordinator.snapshot()?.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING) {
                            leaveCeremony()
                        } else if (wasTesting) {
                            _ui.value = _ui.value.copy(unlock = projectUnlock(naturalUnlockUi))
                        }
                    }
                    _ui.value = _ui.value.copy(
                        linearTrack = linear.current,
                        queue = linear.queue,
                        linearContextKind = linear.contextKind,
                        linearContextId = linear.contextId,
                        linearContextTitle = linear.contextTitle,
                        allowPlayNext = linear.allowPlayNext,
                        allowShuffle = linear.allowShuffle,
                        allowSingleRepeat = linear.allowSingleRepeat,
                        allowRepeatToggle = linear.allowRepeatToggle,
                        abLoopStartFrame = linear.abLoopStartFrame,
                        abLoopEndFrame = linear.abLoopEndFrame,
                        detail = linear.error ?: _ui.value.detail,
                        status = if (linear.error != null) str(com.mylittleblover.mkwplayer.R.string.player_err_open_track) else _ui.value.status,
                    )
                }
            }
        }
    }

    /**
     * Builds the cup from the library and restores its progress.
     *
     * Eligibility is derived from file names rather than stored, because the rule is "every stage you
     * have" -- a stored list would go stale the moment a stage was added, and the cup would then be
     * earnable without hearing it.
     */
    private fun establishCup(
        tracks: List<LibraryStore.TrackRow> = emptyList(),
        preloadedUnlock: UnlockStore.Loaded? = null,
    ) {
        val names = _ui.value.tracks.map { it.name }.ifEmpty { tracks.map { it.title } }
        val rawEligible = pack?.manifest?.let { manifest ->
            manifest.unlock.stages.toSet().ifEmpty { manifest.stages.map { it.code }.toSet() }
        } ?: SpecialCupRules.eligibleStages(names)
        val eligible = rawEligible
            .map { SpecialCupRules.normalizeStageCode(it) }
            .filter { it.isNotBlank() && !SpecialCupRules.isExcludedFromUnlock(it) }
            .toSet()

        val outcome = unlockCoordinator.configure(
            context = getApplication(),
            eligibleStages = eligible,
            preloadedUnlock = preloadedUnlock,
        )
        outcome.problem?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = projectUnlock(unlockCoordinator.naturalUnlockUi.value))
        if (outcome.repairedCeremony && pack?.directory != null) {
            startSpecialCupUnlockCeremony()
        }
    }

    private fun findSpecialCupAppearsItem(): com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.LinearAsset? =
        extraCatalog.otherMusic
            .filterIsInstance<com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.LinearAsset>()
            .firstOrNull { it.id == "special_cup_appears" }

    /** Starts the uninterruptible Special Cup unlock ceremony and plays #111 Special Cup Appears. */
    fun startSpecialCupUnlockCeremony(): Boolean {
        if (isCeremonyLocked) return false
        val item = findSpecialCupAppearsItem()
        val dir = pack?.directory
        val audioFile = if (item != null && dir != null) java.io.File(dir, "audio/${item.asset.sha256}") else null
        if (item == null || dir == null || audioFile == null || !audioFile.isFile) {
            _ui.value = _ui.value.copy(detail = str(com.mylittleblover.mkwplayer.R.string.unlock_err_audio_missing))
            return false
        }
        _ui.value = _ui.value.copy(debugUnlockBannerVisible = false)
        if (!enterCeremony()) return false
        cancelUnlockNotification()
        playCeremonyAudio(item, dir)
        return true
    }

    private fun postUnlockReadyNotification() {
        unlockCoordinator.postUnlockReadyNotification(getApplication())
    }

    /** Developer setting helper to pop up the Special Cup unlock reminder notification and top in-app banner. */
    fun triggerUnlockNotificationForTesting() {
        postUnlockReadyNotification()
        _ui.value = _ui.value.copy(debugUnlockBannerVisible = true)
    }

    /** Developer setting helper to reset Special Cup & Rainbow Road unlock progress back to locked state. */
    fun resetUnlockProgressForTesting(): Boolean {
        if (isCeremonyLocked) {
            releaseTransport()
        }
        val resetErr = unlockCoordinator.reset(getApplication())
        val testReset = unlockTestStore.reset()
        testingCeremonyPlaying = false
        establishCup(preloadedUnlock = UnlockStore.Loaded(null))
        val combinedErr = resetErr ?: testReset.exceptionOrNull()?.message
        _ui.value = _ui.value.copy(
            unlockTestMode = UnlockTestMode.NORMAL,
            debugUnlockBannerVisible = false,
            detail = combinedErr ?: _ui.value.detail,
            libraryGeneration = _ui.value.libraryGeneration + 1,
        )
        refreshCatalog()
        return combinedErr == null
    }

    /** Enters the unlock ceremony from the top banner (supports debug banner preview when locked). */
    fun enterUnlockFromBanner() {
        _ui.value = _ui.value.copy(debugUnlockBannerVisible = false)
        cancelUnlockNotification()
        val snap = unlockCoordinator.snapshot()
        if (snap != null && snap.entitled && snap.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING) {
            startSpecialCupUnlockCeremony()
        } else {
            replayCeremonyForTesting()
        }
    }

    fun dismissDebugUnlockBanner() {
        _ui.value = _ui.value.copy(debugUnlockBannerVisible = false)
    }

    private fun cancelUnlockNotification() {
        unlockCoordinator.cancelUnlockNotification(getApplication())
    }

    private fun syncSessionArtworkMetadata() {
        val uiNow = _ui.value
        session.updateMetadata(
            artworkName = uiNow.artworkName ?: currentStageCode?.let { extraCatalog.courseArtwork[it.removeSuffix("_00")] },
            courseCode = currentStageCode ?: uiNow.linearTrack?.courseCode,
            packDirPath = pack?.directory?.path,
            subtitle = uiNow.flow?.summary?.takeIf { it.isNotBlank() } ?: uiNow.linearTrack?.subtitle,
        )
    }

    /** Developer setting helper to preview/replay the Special Cup unlock ceremony VFX & uninterruptible audio. */
    fun replayCeremonyForTesting() {
        if (isCeremonyLocked) return
        val snap = unlockCoordinator.snapshot()
        if (unlockCoordinator.isEntitled() && snap?.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING) {
            startSpecialCupUnlockCeremony()
            return
        }
        val item = findSpecialCupAppearsItem()
        val dir = pack?.directory
        if (item == null || dir == null || !java.io.File(dir, "audio/${item.asset.sha256}").isFile) {
            _ui.value = _ui.value.copy(detail = str(com.mylittleblover.mkwplayer.R.string.unlock_test_err_missing_111))
            return
        }
        testingCeremonyPlaying = true
        _ui.value = _ui.value.copy(unlock = projectUnlock(naturalUnlockUi))
        playCeremonyAudio(item, dir)
    }

    private fun playCeremonyAudio(
        item: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.LinearAsset,
        dir: java.io.File,
    ) {
        val title = item.localizedName(java.util.Locale.getDefault().toLanguageTag())
        val entry = LinearQueue.Entry(
            key = CEREMONY_ENTRY_KEY,
            title = title,
            subtitle = "",
            sourceType = LinearSessionStore.SourceType.EXTRA,
            ostIndex = item.ostIndex,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            assetSha256 = item.asset.sha256,
            loop = false,
        )
        prepareForLinearPlayback()
        session.uninterruptible = true
        linearCoordinator.startExtraQueue(
            entries = listOf(entry),
            startIndex = 0,
            packDir = dir,
            repeatMode = LinearQueue.RepeatMode.OFF,
            shuffleOverride = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.EXTRA,
                contextId = item.id,
                contextTitle = title,
                packFingerprints = currentPackFingerprints(),
                allowPlayNext = false,
                allowShuffle = false,
                allowSingleRepeat = false,
                allowRepeatToggle = false,
            ),
        )
        startPolling()
    }

    private fun onCeremonyAudioCompleted() {
        session.uninterruptible = false
        val wasTesting = testingCeremonyPlaying
        testingCeremonyPlaying = false
        if (wasTesting) {
            _ui.value = _ui.value.copy(unlock = projectUnlock(naturalUnlockUi))
        }
        if (unlockCoordinator.snapshot()?.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING) {
            ceremonyReachedEnd()
        }
    }

    /** Marks the Special Cup quiz passed and immediately enters the unlock ceremony (#24). */
    fun passUnlockQuizAndStartCeremony(): Boolean {
        if (!unlockCoordinator.passQuiz()) return false
        _ui.value = _ui.value.copy(unlock = projectUnlock(unlockCoordinator.naturalUnlockUi.value))
        return startSpecialCupUnlockCeremony()
    }

    private fun buildStaffCreditsEntry(languageTag: String): LinearQueue.Entry? {
        val item = extraCatalog.otherMusic
            .filterIsInstance<com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.LinearAsset>()
            .firstOrNull { it.id == com.mylittleblover.mkwplayer.unlock.StaffCreditsUnlock.STAFF_CREDITS_TRACK_ID }
            ?: return null
        val dir = pack?.directory ?: return null
        if (!java.io.File(dir, "audio/${item.asset.sha256}").isFile) return null
        return LinearQueue.Entry(
            key = "${PlaylistItemRef.PREFIX_EXTRA}${item.id}",
            title = item.localizedName(languageTag),
            subtitle = str(com.mylittleblover.mkwplayer.R.string.discovery_other),
            sourceType = LinearSessionStore.SourceType.EXTRA,
            durationMs = item.durationMs,
            artworkName = item.artwork,
            assetSha256 = item.asset.sha256,
            sourceTrackKey = item.id,
            loop = item.asset.loop,
            loopStart = item.asset.loopStart,
            loopEnd = item.asset.loopEnd,
        )
    }

    /**
     * Called when leaving the interactive player screen. If the 30th main course was completed during
     * interactive playback, automatically starts Staff Credits and surfaces the unlock toast (#25).
     */
    fun onExitedInteractiveMode(languageTag: String): Boolean {
        if (!unlockCoordinator.consumeDeferredInteractiveStaffCredits()) return false
        val entry = buildStaffCreditsEntry(languageTag) ?: return false
        stopTransport(cancelPendingOpen = true)
        playExtraLinearQueue(
            entries = listOf(entry),
            startIndex = 0,
            repeatMode = LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.EXTRA,
                contextId = entry.key,
                contextTitle = entry.title,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        _ui.value = _ui.value.copy(
            staffCreditsUnlocked = true,
            staffCreditsToastEvent = _ui.value.staffCreditsToastEvent + 1L,
        )
        return true
    }

    /** Enters the ceremony. Fails loudly rather than pretending when the cup is not open. */
    fun enterCeremony(): Boolean {
        val entered = unlockCoordinator.enterCeremony()
        if (entered) {
            _ui.value = _ui.value.copy(unlock = projectUnlock(unlockCoordinator.naturalUnlockUi.value))
        }
        return entered
    }

    /** Leaves the ceremony early. The entitlement is preserved by the rules. */
    fun leaveCeremony() {
        unlockCoordinator.leaveCeremony()
        _ui.value = _ui.value.copy(unlock = projectUnlock(unlockCoordinator.naturalUnlockUi.value))
    }

    /**
     * Reports that the ceremony audio reached its natural end.
     *
     * Called by whoever owns the ceremony track's completion, not by a timer: a UI countdown would be
     * the fake-completion the specification forbids.
     */
    fun ceremonyReachedEnd() {
        unlockCoordinator.ceremonyReachedEnd()
        _ui.value = _ui.value.copy(unlock = projectUnlock(unlockCoordinator.naturalUnlockUi.value))
    }

    /** Consumes the first-time congratulations, if it is owed. */
    fun dismissCongratulations() {
        if (!unlockCoordinator.owesCongratulations()) return
        unlockCoordinator.markCongratulated()
        _ui.value = _ui.value.copy(unlock = projectUnlock(unlockCoordinator.naturalUnlockUi.value))
    }

    /**
     * Rebuilds the catalogue rows from the library.
     *
     * Only measured values are offered to the matcher: a track row that has never been opened has
     * no frames yet, and passing zeros would make it look like a mismatch rather than unknown.
     */
    private fun refreshCatalog() {
        val active = browser ?: return
        val files = _ui.value.tracks.map {
            CatalogMatcher.PresentFile(
                fileName = it.name,
                frames = it.frames,
                sampleRate = it.sampleRate,
                channels = 2,
            )
        }
        active.refresh(files)
    }

    /** Writes the current list. Storage failing is reported, never fatal. */
    private fun persist() {
        val problem = store.save(
            _ui.value.tracks.map {
                LibraryStore.Row(
                    key = it.key,
                    name = it.name,
                    frames = it.frames,
                    sampleRate = it.sampleRate,
                    playable = it.playable,
                    reason = it.reason,
                )
            },
        )
        if (problem != null) _ui.value = _ui.value.copy(detail = problem)
    }
    /**
     * Adds a picked file to the library. Copies, inspects and prepares it off the
     * main thread, then selects it.
     */
    fun add(uri: Uri) {
        if (isCeremonyLocked) return
        _ui.value = _ui.value.copy(busy = true, status = str(com.mylittleblover.mkwplayer.R.string.player_status_reading_file), detail = null)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { inspect(uri) }
            _ui.value = _ui.value.copy(busy = false)

            when (outcome) {
                is Outcome.Refused -> {
                    _ui.value = _ui.value.copy(status = str(com.mylittleblover.mkwplayer.R.string.player_err_play_file), detail = outcome.reason)
                }
                is Outcome.Added -> {
                    _ui.value = _ui.value.copy(
                        tracks = _ui.value.tracks + outcome.track,
                        status = if (outcome.track.playable) str(com.mylittleblover.mkwplayer.R.string.player_status_ready) else str(com.mylittleblover.mkwplayer.R.string.player_status_unsupported_codec),
                        detail = outcome.track.reason,
                    )
                    persist()
                    if (outcome.track.playable) select(outcome.track.key)
                }
            }
        }
    }

    private sealed interface Outcome {
        data class Added(val track: Track) : Outcome
        data class Refused(val reason: String) : Outcome
    }

    private fun inspect(uri: Uri): Outcome {
        val picked = when (val copied = AudioPicker.copy(getApplication(), uri)) {
            is AudioPicker.Result.Rejected -> return Outcome.Refused(copied.reason)
            is AudioPicker.Result.Ok -> copied.picked
        }

        val bytes = try {
            picked.readBytes()
        } catch (e: Exception) {
            return Outcome.Refused("The file could not be opened: ${e.message}")
        }

        val header = when (val read = BwavHeader.read(bytes)) {
            is BwavHeader.Result.Rejected -> return Outcome.Refused(read.reason)
            is BwavHeader.Result.Ok -> read.info
        }

        val playability = Playability.evaluate(header.codecKind)
        val track = Track(
            key = picked.key,
            name = picked.displayName,
            frames = header.frames.toLong(),
            sampleRate = header.sampleRate,
            playable = playability.playable,
            reason = playability.reason,
        )

        if (!playability.playable) {
            // Kept in the library so the limitation is visible instead of the file
            // silently vanishing after the picker closes.
            return Outcome.Added(track)
        }

        val packet = try {
            GraphBuilder.build(
                GraphBuilder.Spec(
                    asset = GraphBuilder.Asset(
                        path = picked.displayName,
                        sampleRate = header.sampleRate,
                        channels = header.channels,
                        frames = header.frames.toLong(),
                        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes),
                        loop = if (header.hasLoop) {
                            GraphBuilder.Loop(header.loopStart!!.toLong(), header.loopEnd!!.toLong())
                        } else {
                            null
                        },
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            return Outcome.Refused("This file cannot be prepared: ${e.message}")
        }

        val probed = GraphProbe.inspect(packet)
        if (probed is GraphProbe.Result.Rejected) {
            return Outcome.Refused("Internal check failed: ${probed.reason}")
        }

        prepared[picked.key] = Prepared(
            name = picked.displayName,
            bytes = bytes,
            packet = packet,
            frames = header.frames.toLong(),
            sampleRate = header.sampleRate,
        )
        return Outcome.Added(track)
    }

    /**
     * Removes a track from the library and forgets its cached copy.
     *
     * Stops playback first when the removed track is the one playing, otherwise the
     * transport would keep running against a file nobody can see or select.
     */
    fun remove(key: String) {
        if (isCeremonyLocked) return
        val track = _ui.value.tracks.firstOrNull { it.key == key } ?: return
        val wasSelected = _ui.value.selectedKey == key
        if (wasSelected) {
            releaseTransport()
        }
        prepared.remove(key)
        _ui.value = _ui.value.copy(
            tracks = _ui.value.tracks.filterNot { it.key == key },
            selectedKey = if (wasSelected) null else _ui.value.selectedKey,
            status = if (wasSelected) str(com.mylittleblover.mkwplayer.R.string.player_status_no_track) else _ui.value.status,
            detail = if (wasSelected) null else _ui.value.detail,
            canPause = if (wasSelected) false else _ui.value.canPause,
            canResume = if (wasSelected) false else _ui.value.canResume,
            canStop = if (wasSelected) false else _ui.value.canStop,
            progress = if (wasSelected) 0f else _ui.value.progress,
        )
        AudioPicker.prune(getApplication(), _ui.value.tracks.map { it.key }.toSet())
        persist()
    }

    /**
     * Opens the flow contained in the given files.
     *
     * Expects several files from one stage; a single main file is accepted and plays as a
     * one-part flow, which keeps "open these five" and "open this one" on the same path rather
     * than giving the flow code a special case it would then have to keep working.
     */
    fun openFlow(uris: List<Uri>) {
        if (isCeremonyLocked || nativePerformance?.isFading == true) return
        val request = ++packPlaybackRequest
        if (uris.isEmpty()) return
        _ui.value = _ui.value.copy(busy = true, status = str(com.mylittleblover.mkwplayer.R.string.player_status_reading_files), detail = null)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val bytes = LinkedHashMap<String, ByteArray>()
                val parts = mutableListOf<StageFlow.Part>()
                val rejected = mutableListOf<String>()

                for (uri in uris) {
                    val picked = when (val copied = AudioPicker.copy(getApplication(), uri)) {
                        is AudioPicker.Result.Rejected -> {
                            rejected += copied.reason
                            continue
                        }
                        is AudioPicker.Result.Ok -> copied.picked
                    }
                    val data = try {
                        picked.readBytes()
                    } catch (e: Exception) {
                        rejected += "${picked.displayName}: ${e.message}"
                        continue
                    }
                    // The logical name must be the game file name so part roles can be found;
                    // the cache name is a digest and carries no role.
                    val result = StageFlowReader.readPart(picked.displayName, data)
                    if (result.isFailure) {
                        rejected += result.exceptionOrNull()!!.message ?: picked.displayName
                        continue
                    }
                    bytes[picked.displayName] = data
                    parts += result.getOrThrow()
                }

                Triple(parts, bytes, rejected)
            }

            val (parts, bytes, rejected) = outcome
            _ui.value = _ui.value.copy(busy = false)

            if (parts.isEmpty()) {
                _ui.value = _ui.value.copy(
                    status = str(com.mylittleblover.mkwplayer.R.string.player_err_no_usable_parts),
                    detail = rejected.firstOrNull() ?: str(com.mylittleblover.mkwplayer.R.string.player_detail_unreadable_files),
                )
                return@launch
            }

            if (request != packPlaybackRequest) return@launch
            if(bytes.keys.any { it.startsWith("BGM_Rainbow_") }) {
                startRainbow(bytes,rejected,request = request)
                return@launch
            }
            val group = StageFlowDiscovery.dominant(StageFlowDiscovery.group(parts))
            if (group == null) {
                _ui.value = _ui.value.copy(status = str(com.mylittleblover.mkwplayer.R.string.player_err_no_usable_parts), detail = rejected.firstOrNull())
                return@launch
            }

            val assembled = StageFlowDiscovery.flowFor(group)
            if (assembled.isFailure) {
                _ui.value = _ui.value.copy(
                    status = str(com.mylittleblover.mkwplayer.R.string.player_err_open_flow),
                    detail = assembled.exceptionOrNull()?.message,
                )
                return@launch
            }
            startFlow(assembled.getOrThrow(), bytes, rejected, request = request)
        }
    }

    private data class PreparedFlowBundle(
        val bytes: Map<String, ByteArray>,
        val plan: InteractivePlaybackPlan?,
        val compiled: StageFlowCompiler.Compiled,
        val entry: Int,
        val wantSpeaker: Boolean,
        val prepared: PlaybackSession.Prepared,
    )

    private sealed interface FlowPrepareOutcome {
        data class Ok(val bundle: PreparedFlowBundle) : FlowPrepareOutcome
        data class Failed(val status: String, val detail: String?) : FlowPrepareOutcome
    }

    private suspend fun startFlow(
        flow: StageFlow,
        sourceBytes: Map<String, ByteArray>,
        rejected: List<String>,
        displayTitle: String = flow.name,
        startingStage: String? = null,
        request: Long = ++packPlaybackRequest,
    ): Boolean {
        if (nativePerformance?.isFading == true) return false
        val previousPlanName = interactivePlan?.name
        val previousSpeaker = speakerEnabled
        val packDir = pack?.directory
        val ffAsset = com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET

        val outcome = withContext(Dispatchers.IO) {
            val bytes = try {
                val ffBytes = loadFinalLapFanfareBytes()
                val withFf = if (ffBytes != null) sourceBytes + (ffAsset to ffBytes) else sourceBytes
                val ks = extraCatalog.koopaSpeaker
                if (ks != null && packDir != null && flow.name == "BGM_Stage_${ks.stageCode}") {
                    val normalFile = java.io.File(packDir, "audio/${ks.normalAsset.sha256}")
                    val fastFile = java.io.File(packDir, "audio/${ks.fastAsset.sha256}")
                    if (normalFile.isFile && fastFile.isFile) {
                        withFf + mapOf(
                            "${ks.normalAsset.stem}.bwav" to normalFile.readBytes(),
                            "${ks.fastAsset.stem}.bwav" to fastFile.readBytes(),
                        )
                    } else {
                        withFf
                    }
                } else {
                    withFf
                }
            } catch (e: Exception) {
                return@withContext FlowPrepareOutcome.Failed(str(com.mylittleblover.mkwplayer.R.string.player_err_load_interactive), e.message)
            }

            val plan = try {
                if (minfCatalog.part(flow.name.removePrefix("BGM_Stage_"), "Full") != null) {
                    var built = InteractivePlaybackPlan.normal(
                        flow = flow,
                        bytes = bytes,
                        catalog = minfCatalog,
                        options = currentInteractiveOptions(),
                    ).startingAt(startingStage)
                    val ks = extraCatalog.koopaSpeaker
                    if (ks != null && flow.name == "BGM_Stage_${ks.stageCode}") {
                        val normalBytes = bytes["${ks.normalAsset.stem}.bwav"]
                        val fastBytes = bytes["${ks.fastAsset.stem}.bwav"]
                        if (normalBytes != null && fastBytes != null) {
                            built = com.mylittleblover.mkwplayer.interactive.SpeakerOverlayCompiler.extend(
                                built,
                                ks,
                                normalBytes,
                                fastBytes,
                            )
                        }
                    }
                    built
                } else null
            } catch (e: Exception) {
                return@withContext FlowPrepareOutcome.Failed(str(com.mylittleblover.mkwplayer.R.string.player_err_prepare_flow), e.message)
            }

            val compiled = try {
                plan?.standard ?: StageFlowCompiler.compileFromBytes(flow, bytes)
            } catch (e: IllegalArgumentException) {
                return@withContext FlowPrepareOutcome.Failed(str(com.mylittleblover.mkwplayer.R.string.player_err_prepare_flow), e.message)
            }

            val wantSpeaker = when {
                plan?.overlayTrackIndices.isNullOrEmpty() -> false
                previousPlanName != plan?.name -> plan?.overlayDefaultEnabled == true
                else -> previousSpeaker
            }
            val initialGains = if (plan == null || plan.overlayTrackIndices.isEmpty()) {
                emptyMap()
            } else {
                val db = if (wantSpeaker) 0.0 else -96.0
                plan.overlayTrackIndices.associateWith { db }
            }

            val entry = plan?.entry ?: FlowPresentation.entrySection(compiled)
            val packetBytes = plan?.packet ?: compiled.packet
            val assetPaths = plan?.assetPaths ?: compiled.assetPaths
            val frames = FlowPresentation.totalFrames(flow)
            when (
                val prep = session.prepareFlow(
                    title = displayTitle,
                    packet = packetBytes,
                    assets = assetPaths.map { path -> path to bytes[path]!! },
                    frames = frames,
                    startSegment = entry,
                    autoAdvance = plan?.automaticNext ?: emptyMap(),
                    streamed = plan?.streamed ?: false,
                    preparedAssets = plan?.let { it.preparedAssets + it.entryPreparedAssets[it.entry].orEmpty() } ?: emptySet(),
                    prequeueInitial = plan?.let { it.entry in it.entryAutomaticNext } ?: false,
                    initialLayerGains = initialGains,
                )
            ) {
                is PlaybackSession.PrepareResult.Failed ->
                    FlowPrepareOutcome.Failed(str(com.mylittleblover.mkwplayer.R.string.player_err_play_flow), prep.reason)
                is PlaybackSession.PrepareResult.Ok ->
                    FlowPrepareOutcome.Ok(PreparedFlowBundle(bytes, plan, compiled, entry, wantSpeaker, prep.prepared))
            }
        }

        val bundle = when (outcome) {
            is FlowPrepareOutcome.Failed -> {
                if (request == packPlaybackRequest) {
                    _ui.value = _ui.value.copy(
                        trackLoading = false,
                        loadingTrackCode = null,
                        loadingTrackTitle = null,
                        status = outcome.status,
                        detail = outcome.detail,
                    )
                }
                return false
            }
            is FlowPrepareOutcome.Ok -> outcome.bundle
        }

        if (request != packPlaybackRequest || nativePerformance?.isFading == true) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { bundle.prepared.discard() }
            return false
        }

        speakerEnabled = bundle.wantSpeaker
        val state = session.commitPrepared(bundle.prepared)
        if (state.detail != null) {
            _ui.value = _ui.value.copy(
                trackLoading = false,
                loadingTrackCode = null,
                loadingTrackTitle = null,
                status = str(com.mylittleblover.mkwplayer.R.string.player_err_play_flow),
                detail = state.detail,
            )
            return false
        }

        val plan = bundle.plan
        val compiled = bundle.compiled
        val entry = bundle.entry
        linearCoordinator.yieldToExternalTransport()
        this.flow = flow
        this.interactivePlan = plan
        this.nativePerformance = null
        this.performance = null
        this.flowCompiled = compiled
        this.flowBytes = bundle.bytes
        totalFrames = FlowPresentation.totalFrames(flow)
        rate = state.sampleRate
        val stageId = flow.name.removePrefix("BGM_Stage_").removeSuffix("_00")
        PlaybackTimers.engine.syncInteractiveContext(true, stageId)
        recordCanonicalPlayStat(stageId)

        _ui.value = _ui.value.copy(
            title = displayTitle,
            trackLoading = false,
            loadingTrackCode = null,
            loadingTrackTitle = null,
            activeFavoriteId = stageId,
            status = str(com.mylittleblover.mkwplayer.R.string.discovery_playing),
            total = EngineSnapshot.formatPosition(totalFrames, rate),
            canPause = true,
            canStop = true,
            outputActive = session.hasOutput,
            linearMode = false,
            linearTrack = null,
            canSeek = false,
            totalFrames = totalFrames,
            sampleRate = rate,
            artworkName = extraCatalog.courseArtwork[flow.name.removePrefix("BGM_Stage_")],
            flow = FlowUi(
                title = displayTitle,
                sections = FlowPresentation.sections(flow, compiled),
                activeSegment = entry,
                summary = FlowPresentation.summary(flow),
                provenance = if(plan!=null) "MINF sample timing with confirmed Outro exits" else FlowPresentation.provenanceLabel(flow),
                isProvisional = flow.isProvisional,
                outros = compiled.segmentByOutro.keys.sorted(),
                hasFast = compiled.segmentByRole.containsKey(StageFlow.Role.Fast),
                versionNumbers = plan?.versions?.map { it.number }.orEmpty(),
                regionNumbers = plan?.regions?.keys?.sorted().orEmpty(),
                availableStageKeys = plan?.let { current -> current.spec.segments.mapNotNull { current.labels[it.index] } }
                    ?: FlowPresentation.sections(flow, compiled).map { it.label },
                hasSpeakerOverlay = plan?.overlayTrackIndices?.isNotEmpty() == true,
                speakerEnabled = speakerEnabled,
            ),
            detail = buildList {
                if (rejected.isNotEmpty()) add(str(com.mylittleblover.mkwplayer.R.string.player_detail_skipped_fmt, rejected.joinToString("; ")))
                if (!session.hasOutput) {
                    add(str(com.mylittleblover.mkwplayer.R.string.player_detail_no_output))
                }
            }.joinToString(" ").ifEmpty { null },
        )

        syncSessionArtworkMetadata()
        PlaybackService.start(getApplication())
        requestNotificationPermissionIfNeeded()
        startPolling()
        return true
    }

    private sealed interface RainbowPrepareOutcome {
        data class Ok(val plan: InteractivePlaybackPlan, val frames: Long, val prepared: PlaybackSession.Prepared) : RainbowPrepareOutcome
        data class Failed(val status: String, val detail: String?) : RainbowPrepareOutcome
    }

    private suspend fun startRainbow(
        bytes: Map<String,ByteArray>,
        notes: List<String>,
        displayTitle: String="Rainbow",
        startingStage: String?=null,
        request: Long = ++packPlaybackRequest,
    ): Boolean {
        if(isSpecialStageLocked("Rainbow")) return false
        if(nativePerformance?.isFading==true) return false
        val previousPlanName = interactivePlan?.name
        val previousSpeaker = speakerEnabled
        var preparedWantSpeaker = true
        val outcome = withContext(Dispatchers.IO) {
            val plan = try {
                InteractivePlaybackPlan.rainbow(
                    bytes = bytes,
                    catalog = minfCatalog,
                    options = currentInteractiveOptions(),
                ).startingAt(startingStage)
            } catch (e: Exception) {
                return@withContext RainbowPrepareOutcome.Failed(str(com.mylittleblover.mkwplayer.R.string.player_err_prepare_flow), e.message)
            }
            val wantSpeaker = when {
                plan.overlayTrackIndices.isEmpty() -> false
                previousPlanName != plan.name -> plan.overlayDefaultEnabled
                else -> previousSpeaker
            }
            preparedWantSpeaker = wantSpeaker
            val initialGains = if (plan.overlayTrackIndices.isEmpty()) {
                emptyMap()
            } else {
                val db = if (wantSpeaker) 0.0 else -96.0
                plan.overlayTrackIndices.associateWith { db }
            }
            val frames = plan.spec.segments.sumOf { it.exit }
            when (
                val prep = session.prepareFlow(
                    title = displayTitle,
                    packet = plan.packet,
                    assets = plan.assetPaths.map { it to bytes.getValue(it) },
                    frames = frames,
                    startSegment = plan.entry,
                    streamed = true,
                    autoAdvance = plan.automaticNext,
                    preparedAssets = plan.preparedAssets + plan.entryPreparedAssets[plan.entry].orEmpty(),
                    prequeueInitial = plan.entry in plan.entryAutomaticNext,
                    initialLayerGains = initialGains,
                )
            ) {
                is PlaybackSession.PrepareResult.Failed ->
                    RainbowPrepareOutcome.Failed(str(com.mylittleblover.mkwplayer.R.string.player_err_play_flow), prep.reason)
                is PlaybackSession.PrepareResult.Ok ->
                    RainbowPrepareOutcome.Ok(plan, frames, prep.prepared)
            }
        }
        val (plan, frames, prepared) = when (outcome) {
            is RainbowPrepareOutcome.Failed -> {
                if (request == packPlaybackRequest) {
                    _ui.value = _ui.value.copy(
                        trackLoading = false,
                        loadingTrackCode = null,
                        loadingTrackTitle = null,
                        status = outcome.status,
                        detail = outcome.detail,
                    )
                }
                return false
            }
            is RainbowPrepareOutcome.Ok -> Triple(outcome.plan, outcome.frames, outcome.prepared)
        }
        if (request != packPlaybackRequest || nativePerformance?.isFading == true) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { prepared.discard() }
            return false
        }
        val state = session.commitPrepared(prepared)
        if(state.detail!=null) { _ui.value=_ui.value.copy(trackLoading=false,loadingTrackCode=null,loadingTrackTitle=null,status=str(com.mylittleblover.mkwplayer.R.string.player_err_play_flow),detail=state.detail);return false }
        linearCoordinator.yieldToExternalTransport()
        PlaybackTimers.engine.syncInteractiveContext(true, "Rainbow")
        recordCanonicalPlayStat("Rainbow")
        speakerEnabled = preparedWantSpeaker
        interactivePlan=plan;nativePerformance=null;performance=null;flow=null;flowCompiled=null;flowBytes=bytes
        totalFrames=frames;rate=48000
        val sections=plan.spec.segments.map { FlowPresentation.Section(it.index,plan.labels.getValue(it.index),it.exit,48000) }
        _ui.value=_ui.value.copy(title=displayTitle,trackLoading=false,loadingTrackCode=null,loadingTrackTitle=null,activeFavoriteId="Rainbow",status=str(com.mylittleblover.mkwplayer.R.string.discovery_playing),detail=notes.joinToString("; ").ifEmpty { null },
            canPause=true,canStop=true,outputActive=session.hasOutput,
            linearMode=false,linearTrack=null,canSeek=false,totalFrames=frames,sampleRate=48000,artworkName=extraCatalog.courseArtwork["Rainbow"],
            flow=FlowUi(displayTitle,sections,plan.entry,"Area1 → Area2 → Area3 → Final","MINF timing; bar exits confirmed",true,
                outros=plan.outros.keys.sorted(), hasFast = plan.fast != null, availableStageKeys=sections.map { it.label },
                hasSpeakerOverlay=false,
                speakerEnabled=speakerEnabled,
                isRainbowMelodyOverlay=true))
        syncSessionArtworkMetadata()
        PlaybackService.start(getApplication());requestNotificationPermissionIfNeeded();startPolling()
        return true
    }

    /**
     * Opens the stage files found in a folder inside app storage.
     *
     * Same path as [openFlow], minus the picker: this is what makes the flow reachable from an
     * automated test, and it is a working fallback for a user whose file manager returns names the
     * app cannot read.
     */
    fun openFlowFromFolder() {
        if (isCeremonyLocked || nativePerformance?.isFading == true) return
        val request = ++packPlaybackRequest
        val folder = java.io.File(getApplication<Application>().filesDir, FLOW_FOLDER)
        _ui.value = _ui.value.copy(busy = true, status = str(com.mylittleblover.mkwplayer.R.string.player_status_scanning), detail = null)
        viewModelScope.launch {
            val scan = withContext(Dispatchers.IO) { FolderAudioSource.scan(folder) }
            _ui.value = _ui.value.copy(busy = false)

            if (scan.missingFolder) {
                _ui.value = _ui.value.copy(
                    status = str(com.mylittleblover.mkwplayer.R.string.player_err_no_folder),
                    detail = str(com.mylittleblover.mkwplayer.R.string.player_detail_no_folder_fmt, folder.path),
                )
                return@launch
            }
            if (scan.found.isEmpty()) {
                _ui.value = _ui.value.copy(
                    status = str(com.mylittleblover.mkwplayer.R.string.player_err_no_usable_files),
                    detail = scan.rejected.firstOrNull() ?: str(com.mylittleblover.mkwplayer.R.string.player_detail_no_readable_parts),
                )
                return@launch
            }

            if (request != packPlaybackRequest) return@launch
            if(scan.bytesByName().keys.any { it.startsWith("BGM_Rainbow_") }) {
                startRainbow(scan.bytesByName(),scan.rejected,request = request)
                return@launch
            }
            val group = StageFlowDiscovery.dominant(scan.groups())
            if (group == null) {
                _ui.value = _ui.value.copy(status = str(com.mylittleblover.mkwplayer.R.string.player_err_no_usable_files), detail = scan.rejected.firstOrNull())
                return@launch
            }

            val assembled = StageFlowDiscovery.flowFor(group)
            if (assembled.isFailure) {
                _ui.value = _ui.value.copy(
                    status = str(com.mylittleblover.mkwplayer.R.string.player_err_open_flow),
                    detail = assembled.exceptionOrNull()?.message,
                )
                return@launch
            }
            startFlow(assembled.getOrThrow(), scan.bytesByName(), scan.rejected, request = request)
        }
    }

    /**
     * Imports a `.mkwpack` directly via persistable SAF FileChannel streaming (#91-#97).
     *
     * Enforces strictly one active import at a time (#94), never copies the archive into cache
     * storage (#96), supports pre-commit cancellation (#91, #93), crash-safe checkpoint resume
     * (#95, #97), and automatic commit without confirmation dialogs (#92).
     */
    fun importPack(uri: Uri) {
        if (isCeremonyLocked || nativePerformance?.isFading == true) return
        requestNotificationPermissionIfNeeded()
        val app = getApplication<Application>()
        val result = PackImportCoordinator.startUriImport(
            context = app,
            uri = uri,
            libraryRoot = java.io.File(app.filesDir, PACK_ROOT),
            appFilesDir = app.filesDir,
            startForeground = true,
        )
        when (result) {
            is PackImportCoordinator.StartResult.Started -> Unit
            is PackImportCoordinator.StartResult.RejectedBusy -> {
                _ui.value = _ui.value.copy(detail = result.reason)
            }
            is PackImportCoordinator.StartResult.RejectedInvalid -> {
                _ui.value = _ui.value.copy(detail = result.reason)
            }
        }
    }

    fun cancelPackImport(): Boolean {
        val packRoot = java.io.File(getApplication<Application>().filesDir, PACK_ROOT)
        return PackImportCoordinator.requestCancel(packRoot)
    }

    fun resumePackImport() {
        requestNotificationPermissionIfNeeded()
        val app = getApplication<Application>()
        val result = PackImportCoordinator.resumePending(
            context = app,
            libraryRoot = java.io.File(app.filesDir, PACK_ROOT),
            appFilesDir = app.filesDir,
            startForeground = true,
        )
        if (result is PackImportCoordinator.StartResult.RejectedInvalid) {
            _ui.value = _ui.value.copy(detail = result.reason)
        } else if (result is PackImportCoordinator.StartResult.RejectedBusy) {
            _ui.value = _ui.value.copy(detail = result.reason)
        }
    }

    fun resumePackImportWithRepick(uri: Uri) {
        requestNotificationPermissionIfNeeded()
        val app = getApplication<Application>()
        val result = PackImportCoordinator.resumeWithRepickedUri(
            context = app,
            newUri = uri,
            libraryRoot = java.io.File(app.filesDir, PACK_ROOT),
            appFilesDir = app.filesDir,
            startForeground = true,
        )
        if (result is PackImportCoordinator.StartResult.RejectedInvalid) {
            _ui.value = _ui.value.copy(detail = result.reason)
        } else if (result is PackImportCoordinator.StartResult.RejectedBusy) {
            _ui.value = _ui.value.copy(detail = result.reason)
        }
    }

    fun dismissImportChangelog() {
        PackImportCoordinator.dismissCompletedOrError()
    }

    /**
     * Applies committed pack + candidate pool updates to active library state, stops any
     * invalidated active playback (#88), clears invalidated Continue Listening (#89), and counts
     * saved custom routes flagged invalid without mutating user edits (#87).
     */
    private fun applyCommittedPackUpdate(
        installed: StreamingPackInstaller.Result.Installed,
    ): PackImportChangelog {
        val merged = installed.mergedLibrary
        val effectiveManifest = merged?.manifest ?: installed.manifest
        val effectiveDir = merged?.directory ?: installed.directory
        this.pack = InstalledPack(effectiveManifest, effectiveDir)
        rebuildPlayableStageSet(effectiveManifest)
        this.mergedFingerprints = merged?.activePackFingerprints
            ?: listOf("${installed.manifest.packId}:${installed.manifest.packVersion}")

        com.mylittleblover.mkwplayer.ui.art.PackArtwork.evictAll()
        com.mylittleblover.mkwplayer.ui.art.PackArtwork.warmArtworkIndex(effectiveDir)
        reloadRoadJukeboxPool()
        establishCup()

        val registry = PackRegistry(java.io.File(getApplication<Application>().filesDir, PACK_ROOT))
        val updatedJukebox = com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.load(registry, jukeboxMetadata)
        _ui.value = _ui.value.copy(jukebox = updatedJukebox)
        linearCoordinator.configureSessionStore(linearSessionStore)
        linearCoordinator.attach(getApplication(), updatedJukebox)

        val customRoutes = com.mylittleblover.mkwplayer.routes.CustomRouteStore(customRoutesFile())
            .read().getOrDefault(emptyList())

        val invalidEnv = buildInvalidationEnvironment(
            manifest = effectiveManifest,
            directory = effectiveDir,
            jukeboxCatalog = updatedJukebox,
            customRoutes = customRoutes,
        )

        // #88: Stop active playback immediately if updated data made its route, stage, or assets invalid.
        var stoppedActivePlayback = false
        var stopDetail: String? = null
        val linearNow = linearCoordinator.state.value
        if (linearNow.active && linearNow.current != null) {
            val allQueue = linearCoordinator.queue.snapshot().let { snap ->
                buildList {
                    snap.current?.let { add(it) }
                    addAll(snap.upNext)
                }
            }
            val decision = LibraryInvalidation.shouldStopActiveLinear(
                contextKind = linearNow.contextKind,
                contextId = linearNow.contextId,
                currentEntry = linearNow.current,
                allQueueEntries = allQueue,
                env = invalidEnv,
            )
            if (decision.shouldStop) {
                releaseTransport()
                stoppedActivePlayback = true
                stopDetail = decision.reason
            }
        } else {
            val stageDecision = LibraryInvalidation.shouldStopInteractiveStage(currentStageCode, invalidEnv)
            if (stageDecision.shouldStop) {
                releaseTransport()
                stoppedActivePlayback = true
                stopDetail = stageDecision.reason
            }
        }

        // #89: Clear Continue Listening immediately if updated data invalidated saved route/pool/tracks.
        var clearedContinueListening = false
        val saved = linearSessionStore.read().getOrNull()
        if (saved != null && !LibraryInvalidation.isSavedSessionStillValid(saved, invalidEnv)) {
            linearSessionStore.clear()
            clearedContinueListening = true
            _ui.value = _ui.value.copy(continueListening = null)
        } else {
            refreshContinueListening()
        }

        // #87: Identify invalidated custom routes without mutating/deleting user route edits.
        val invalidRoutesCount = LibraryInvalidation.findInvalidCustomRoutes(invalidEnv).size

        val playable = PackPlayback.playableTracks(effectiveManifest)
        val notes = buildList {
            if (installed.coverage.unsupportedCodecs.isNotEmpty()) {
                add(str(com.mylittleblover.mkwplayer.R.string.settings_import_unsupported_codecs, installed.coverage.unsupportedCodecs.sumOf { it.affectedTracks }))
            }
            if (stopDetail != null) add(stopDetail)
        }

        val previousGeneration = _ui.value.libraryGeneration
        val hadNothingActiveBefore = session.state.value.active.not() &&
            !linearCoordinator.state.value.active &&
            interactivePlan == null &&
            flow == null

        _ui.value = _ui.value.copy(
            busy = false,
            status = if (playable.isEmpty()) str(com.mylittleblover.mkwplayer.R.string.settings_import_none_playable) else str(com.mylittleblover.mkwplayer.R.string.settings_import_updated_status),
            detail = notes.firstOrNull(),
            libraryGeneration = previousGeneration + 1,
            pack = PackUi(
                name = effectiveManifest.name.ifBlank { installed.manifest.name },
                trackCount = effectiveManifest.tracks.size,
                playableCount = playable.size,
                notes = notes,
                importing = false,
                progress = "",
            ),
        )

        // Do not auto-play or auto-open the first stage after importing a pack (#12).
        // Users choose what to play explicitly from Home, Media Library, or World Map.

        return PackImportChangelog(
            packId = installed.manifest.packId,
            packVersion = installed.manifest.packVersion,
            packName = installed.manifest.name.ifBlank { installed.manifest.packId },
            isDelta = installed.manifest.isDelta,
            addedOrUpdatedAssets = installed.addedOrUpdatedAssets,
            addedOrUpdatedTracks = installed.addedOrUpdatedTracks,
            totalLibraryTracks = effectiveManifest.tracks.size,
            playableLibraryTracks = playable.size,
            jukeboxTrackCount = updatedJukebox.itemsByKey.size,
            poolStatus = installed.poolStatus,
            poolDataVersion = installed.poolDataVersion,
            poolEdgeCount = installed.poolEdgeCount,
            stoppedActivePlayback = stoppedActivePlayback,
            clearedContinueListening = clearedContinueListening,
            invalidatedCustomRoutesCount = invalidRoutesCount,
        )
    }

    private var packPlaybackRequest=0L

    private sealed interface PackOpenOutcome {
        data class Refused(val reason: String) : PackOpenOutcome
        data class IdentityError(val reason: String?) : PackOpenOutcome
        data class Ready(
            val playable: PackPlayback.Playable,
            val resolved: PackInteractiveSources.Resolved?,
        ) : PackOpenOutcome
    }

    /** Newest selection waits without blocking the service's foreground promotion. */
    fun openPackTrack(track: PackManifest.Track,perform: Boolean=false,startingStage: String?=null) {
        if (isCeremonyLocked || nativePerformance?.isFading == true) return
        val request=++packPlaybackRequest
        val stageCode = pack?.manifest?.stages?.firstOrNull { track.key in it.tracks }?.code?.removeSuffix("_00")
            ?: track.title.removePrefix("BGM_Stage_").removeSuffix("_00").takeIf { track.title.startsWith("BGM_Stage_") }
        val targetTitle = stageCode?.let { localizedStageTitle(it, track.title) } ?: track.title
        beginInteractiveLoad(stageCode, targetTitle, stageCode)
        viewModelScope.launch {
            PlaybackService.awaitForegroundPromotion()
            if (isCeremonyLocked || request != packPlaybackRequest || nativePerformance?.isFading == true) {
                if (request == packPlaybackRequest) clearInteractiveLoad()
                return@launch
            }
            if(openPackTrackNow(track,startingStage,request) && perform) startPerformance()
        }
    }

    private suspend fun openPackTrackNow(track: PackManifest.Track,startingStage: String?=null,request: Long): Boolean {
        if (isCeremonyLocked || nativePerformance?.isFading == true) {
            if (request == packPlaybackRequest) clearInteractiveLoad()
            return false
        }
        val installed = pack ?: run {
            if (request == packPlaybackRequest) clearInteractiveLoad()
            return false
        }
        val outcome = withContext(Dispatchers.IO) {
            when (val prepared = PackPlayback.prepare(installed.manifest, track, installed.directory)) {
                is PackPlayback.Result.Refused -> PackOpenOutcome.Refused(prepared.reason)
                is PackPlayback.Result.Ok -> {
                    val playable = prepared.playable
                    val resolved = try {
                        PackInteractiveSources.resolve(installed.manifest, track, installed.directory, playable, interactiveIdentities)
                    } catch (e: Exception) {
                        return@withContext PackOpenOutcome.IdentityError(e.message)
                    }
                    PackOpenOutcome.Ready(playable, resolved)
                }
            }
        }
        if (isCeremonyLocked || request != packPlaybackRequest || nativePerformance?.isFading == true) return false
        return when (outcome) {
            is PackOpenOutcome.Refused -> {
                _ui.value = _ui.value.copy(trackLoading = false, loadingTrackCode = null, loadingTrackTitle = null, status = str(com.mylittleblover.mkwplayer.R.string.player_err_open_track), detail = outcome.reason)
                false
            }
            is PackOpenOutcome.IdentityError -> {
                _ui.value = _ui.value.copy(trackLoading = false, loadingTrackCode = null, loadingTrackTitle = null, status = str(com.mylittleblover.mkwplayer.R.string.player_err_identify_sources), detail = outcome.reason)
                false
            }
            is PackOpenOutcome.Ready -> {
                val playable = outcome.playable
                when (val resolved = outcome.resolved) {
                    is PackInteractiveSources.Resolved.Rainbow ->
                        startRainbow(resolved.bytes, playable.notes, localizedStageTitle("Rainbow", resolved.title), startingStage, request = request)
                    is PackInteractiveSources.Resolved.Normal ->
                        startFlow(resolved.flow, resolved.bytes, playable.notes, localizedStageTitle(resolved.flow.name, resolved.title), startingStage, request = request)
                    null ->
                        startFlow(playable.flow, playable.bytes, playable.notes, displayTitle = localizedStageTitle(playable.flow.name, track.title), startingStage = startingStage, request = request)
                }
            }
        }
    }

    /** Opens the first track of the imported pack. */
    fun openFirstPackStage() {
        if (isCeremonyLocked) return
        val installed = pack ?: return
        val playable = PackPlayback.playableTracks(installed.manifest).toSet()
        val track = installed.manifest.stages
            .filter { !isSpecialStageLocked(it.code) }
            .firstNotNullOfOrNull { stage ->
                installed.manifest.tracksOf(stage).firstOrNull { it in playable }
            } ?: playable.firstOrNull { !isSpecialStageLocked(it.title) }
        if (track == null) {
            _ui.value = _ui.value.copy(
                status = str(com.mylittleblover.mkwplayer.R.string.player_err_nothing_playable),
                detail = str(com.mylittleblover.mkwplayer.R.string.player_detail_no_decodable_track),
            )
            return
        }
        openPackTrack(track)
    }

    /** Requests a section of the open flow. */
    fun playSection(segment: Int) {
        if (isCeremonyLocked) return
        if(interactivePlan!=null) {
            startPerformance()
            val plan=interactivePlan!!
            val driver=nativePerformance ?: return
            val number=plan.outros.entries.firstOrNull { it.value==segment }?.key
            val error=when {
                number!=null -> driver.takeOutro(number)
                segment==plan.fast -> driver.enterFast()
                plan.next[driver.current]==segment -> driver.advance()
                else -> str(com.mylittleblover.mkwplayer.R.string.interactive_err_no_transition)
            }
            publishNativePerformance(error,true)
            return
        }
        val compiled = flowCompiled ?: return
        // Taking a section by hand ends the performance: the listener has taken over, and a driver still
        // following the transport would fight them for the next section.
        performance = null
        val state = session.playSegment(segment)
        if (state.detail != null) {
            _ui.value = _ui.value.copy(detail = state.detail)
            return
        }
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(
                activeSegment = segment,
                performing = false,
                performanceStage = "", stageKey = null, pendingStageKey = null,
                canAdvance = false,
                canEnd = false,
                fastPlaying = false,
            ),
            status = str(com.mylittleblover.mkwplayer.R.string.discovery_playing),
        )
    }

    /**
     * Performs the whole open stage: prelude, main loop, then the outro the listener picks.
     *
     * Refuses with a stated reason rather than doing nothing, because a Start button that silently fails is
     * indistinguishable from a broken app.
     */
    private var pendingInteractiveStatId: String? = null

    fun startPerformance() {
        if (isCeremonyLocked) return
        if(isSpecialStageLocked(currentStageCode.orEmpty())) return
        interactivePlan?.let { plan ->
            if(!session.state.value.loaded) {
                val request=++packPlaybackRequest
                val flowTitle = _ui.value.flow?.title ?: plan.name
                val snapshotBytes = flowBytes
                val snapshotTotalFrames = totalFrames
                val snapshotGains = currentLayerGains(plan)
                _ui.value = _ui.value.copy(trackLoading = true)
                viewModelScope.launch {
                    PlaybackService.awaitForegroundPromotion()
                    if(request!=packPlaybackRequest || interactivePlan!==plan || nativePerformance?.isFading == true) {
                        if (request == packPlaybackRequest) _ui.value = _ui.value.copy(trackLoading = false)
                        return@launch
                    }
                    val prep = withContext(Dispatchers.IO) {
                        session.prepareFlow(
                            title = flowTitle,
                            packet = plan.packet,
                            assets = plan.assetPaths.map { it to snapshotBytes.getValue(it) },
                            frames = snapshotTotalFrames,
                            startSegment = plan.entry,
                            streamed = plan.streamed,
                            autoAdvance = plan.automaticNext,
                            preparedAssets = plan.preparedAssets + plan.entryPreparedAssets[plan.entry].orEmpty(),
                            prequeueInitial = plan.entry in plan.entryAutomaticNext,
                            initialLayerGains = snapshotGains,
                        )
                    }
                    when (prep) {
                        is PlaybackSession.PrepareResult.Failed -> {
                            if (request == packPlaybackRequest && interactivePlan === plan) {
                                _ui.value = _ui.value.copy(trackLoading = false, detail = prep.reason)
                            }
                            return@launch
                        }
                        is PlaybackSession.PrepareResult.Ok -> {
                            if (request != packPlaybackRequest || interactivePlan !== plan || nativePerformance?.isFading == true) {
                                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { prep.prepared.discard() }
                                if (request == packPlaybackRequest) _ui.value = _ui.value.copy(trackLoading = false)
                                return@launch
                            }
                            val state = session.commitPrepared(prep.prepared)
                            if (state.detail != null) {
                                _ui.value = _ui.value.copy(trackLoading = false, detail = state.detail)
                                return@launch
                            }
                            _ui.value = _ui.value.copy(trackLoading = false)
                            nativePerformance = null
                            startPerformance()
                        }
                    }
                }
                return
            }
            pendingInteractiveStatId?.let { id ->
                pendingInteractiveStatId = null
                recordCanonicalPlayStat(id)
            }
            if (nativePerformance == null) {
                nativePerformance = NativeInteractiveDriver(
                    spec = plan.spec,
                    entry = plan.entry,
                    next = plan.next,
                    outros = plan.outros,
                    fast = plan.fast,
                    request = { segment -> session.playSegment(segment).detail },
                    versions = plan.versions,
                    outroVariants = plan.outroVariants,
                    regions = plan.regions,
                    cancelRequest = session::cancelPendingTransition,
                    mandatoryAutomaticSources = plan.entryAutomaticNext.keys,
                    advanceViaOutro = plan.advanceViaOutro,
                    onTransitionRequested = { fromSeg, toSeg ->
                        if (RainbowInteractiveCompiler.isArea1ToArea2(plan, fromSeg, toSeg)) {
                            RainbowInteractiveCompiler.area1MelodyTrackId(plan.spec)?.let { melodyId ->
                                session.setLayerGain(melodyId, -96.0, RainbowInteractiveCompiler.AREA1_MELODY_FADE_MS)
                            }
                        }
                    },
                    onTransitionCancelled = { fromSeg, cancelledSeg ->
                        if (RainbowInteractiveCompiler.isArea1ToArea2(plan, fromSeg, cancelledSeg) &&
                            _ui.value.flow?.speakerEnabled == true
                        ) {
                            RainbowInteractiveCompiler.area1MelodyTrackId(plan.spec)?.let { melodyId ->
                                session.setLayerGain(melodyId, 0.0, RainbowInteractiveCompiler.AREA1_MELODY_FADE_MS)
                            }
                        }
                    },
                )
            }
            session.interactiveObservation()?.let { nativePerformance!!.observe(it) }
            publishNativePerformance(fromAction=true)
            syncSessionArtworkMetadata()
            PlaybackService.start(getApplication())
            requestNotificationPermissionIfNeeded()
            startPolling()
            return
        }
        val compiled = flowCompiled ?: return
        val open = flow ?: return
        if (performance?.isRunning == true) return

        val segments = compiled.segmentByRole
        val driver = PerformanceDriver(
            performance = StagePerformance(
                flow = open,
                segmentByRole = segments,
                segmentByOutro = compiled.segmentByOutro,
            ),
            startSegment = { segment -> session.playSegment(segment).detail == null },
            transport = { session.state.value },
        )

        apply(driver.start(), driver)
        if (driver.isRunning) performance = driver else driver.stop()
    }

    /** Estimates frames remaining until a queued interactive transition fires (#11). */
    fun estimatedFramesUntilPendingSwitch(sourceFrame: Long?): Long? =
        nativePerformance?.estimatedFramesUntilPendingSwitch(sourceFrame)

    /** The next-stage event: leave the looping prelude for the main loop. */
    fun advancePerformance() {
        if (isCeremonyLocked) return
        nativePerformance?.let { publishNativePerformance(it.advance(),true);return }
        val driver = performance ?: return
        apply(driver.advance(), driver)
    }

    /** Switches between the main loop and its fast arrangement. */
    fun toggleFast() {
        if (isCeremonyLocked) return
        nativePerformance?.let { publishNativePerformance(it.enterFast(),true);return }
        val driver = performance ?: return
        apply(driver.toggleFast(), driver)
    }

    /** Takes the ending nearest to where the main loop has got to. */
    fun takeOutro() {
        if (isCeremonyLocked) return
        nativePerformance?.let { publishNativePerformance(it.takeOutro(),true);return }
        val driver = performance ?: return
        apply(driver.takeOutro(), driver)
    }

    /** Takes a specific Outro number (0-indexed, e.g. 0, 1, 2 for Title Outros). */
    fun takeOutro(number: Int) {
        if (isCeremonyLocked) return
        nativePerformance?.let {
            publishNativePerformance(it.takeOutro(number), true)
            return
        }
    }

    fun toggleSpeakerOverlay() {
        if (isCeremonyLocked) return
        val plan = interactivePlan ?: return
        if (plan.overlayTrackIndices.isEmpty()) return
        if (plan.name.equals("Rainbow", ignoreCase = true) || _ui.value.flow?.isRainbowMelodyOverlay == true) return
        speakerEnabled = !speakerEnabled
        val db = if (speakerEnabled) 0.0 else -96.0
        if (session.state.value.loaded) {
            plan.overlayTrackIndices.forEach { trackIdx ->
                session.setLayerGain(trackIdx, db, fadeMs = 1000)
            }
        }
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(speakerEnabled = speakerEnabled),
        )
    }

    fun openInteractiveTitle(
        languageTag: String = java.util.Locale.getDefault().toLanguageTag(),
        autoStart: Boolean = false,
    ) {
        if (isCeremonyLocked || nativePerformance?.isFading == true) return
        val item = extraCatalog.otherMusic
            .filterIsInstance<com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle>()
            .firstOrNull()
        val dir = pack?.directory
        if (item == null || dir == null) {
            _ui.value = _ui.value.copy(status = str(com.mylittleblover.mkwplayer.R.string.player_err_open_track), detail = str(com.mylittleblover.mkwplayer.R.string.player_err_no_pack_track))
            return
        }
        val localizedTitle = item.localizedName(languageTag)
        val request = ++packPlaybackRequest
        beginInteractiveLoad(targetCode = null, targetTitle = localizedTitle, favoriteId = "extra:title")
        _ui.value = _ui.value.copy(artworkName = item.artwork)
        viewModelScope.launch {
            val loaded = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val refs = listOf(item.harmonica, item.main, item.brassCut) + item.outros.map { it.asset }
                    val byStem = mutableMapOf<String, ByteArray>()
                    val byPath = mutableMapOf<String, ByteArray>()
                    refs.forEach { ref ->
                        if (ref.stem !in byStem) {
                            val file = java.io.File(dir, "audio/${ref.sha256}")
                            if (!file.isFile) error("Missing audio file ${ref.stem}")
                            val bytes = file.readBytes()
                            byStem[ref.stem] = bytes
                            byPath["${ref.stem}.bwav"] = bytes
                        }
                    }
                    val plan = com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers.compileTitlePlan(
                        item = item,
                        bytesByStem = byStem,
                        segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
                    )
                    plan to byPath
                }
            }
            if (request != packPlaybackRequest) return@launch
            val (plan, bytesByPath) = loaded.getOrElse { err ->
                _ui.value = _ui.value.copy(trackLoading = false, loadingTrackCode = null, loadingTrackTitle = null, status = str(com.mylittleblover.mkwplayer.R.string.player_err_prepare_track), detail = err.message)
                return@launch
            }

            linearCoordinator.yieldToExternalTransport()
            session.stop()
            this@PlayerViewModel.flow = null
            this@PlayerViewModel.flowCompiled = null
            this@PlayerViewModel.performance = null
            this@PlayerViewModel.nativePerformance = null
            this@PlayerViewModel.interactivePlan = plan
            this@PlayerViewModel.flowBytes = bytesByPath
            this@PlayerViewModel.totalFrames = item.main.frames
            this@PlayerViewModel.rate = item.main.sampleRate
            pendingInteractiveStatId = "extra:title"
            PlaybackTimers.engine.syncInteractiveContext(true, "extra:title")

            _ui.value = _ui.value.copy(
                title = localizedTitle,
                trackLoading = false,
                loadingTrackCode = null,
                loadingTrackTitle = null,
                activeFavoriteId = "extra:title",
                status = str(com.mylittleblover.mkwplayer.R.string.stage_stopped),
                total = EngineSnapshot.formatPosition(item.main.frames, item.main.sampleRate),
                position = "0:00",
                progress = 0f,
                canPause = false,
                canResume = false,
                canStop = false,
                outputActive = session.hasOutput,
                linearMode = false,
                linearTrack = null,
                canSeek = false,
                totalFrames = item.main.frames,
                sampleRate = item.main.sampleRate,
                artworkName = item.artwork,
                detail = null,
                flow = FlowUi(
                    title = localizedTitle,
                    sections = emptyList(),
                    activeSegment = null,
                    summary = "",
                    provenance = "",
                    isProvisional = false,
                    outros = plan.outros.keys.sorted(),
                    hasFast = false,
                    availableStageKeys = plan.customInitialSegments.mapNotNull { plan.labels[it] },
                ),
            )
            if (autoStart) {
                startPerformance()
            }
        }
    }

    fun openInteractiveTwoState(
        track: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.TwoStateTrack,
        isBattle: Boolean,
        languageTag: String = java.util.Locale.getDefault().toLanguageTag(),
        autoStart: Boolean = true,
    ) {
        if (isCeremonyLocked || nativePerformance?.isFading == true) return
        val dir = pack?.directory
        if (dir == null) {
            _ui.value = _ui.value.copy(status = str(com.mylittleblover.mkwplayer.R.string.player_err_open_track), detail = str(com.mylittleblover.mkwplayer.R.string.player_err_no_pack_track))
            return
        }
        val canonicalTrackId = if (isBattle) "battle:${track.id}" else "sfc:${track.id}"
        val localizedTitle = track.localizedName(languageTag)
        val request = ++packPlaybackRequest
        beginInteractiveLoad(targetCode = null, targetTitle = localizedTitle, favoriteId = canonicalTrackId)
        _ui.value = _ui.value.copy(artworkName = track.artwork)
        viewModelScope.launch {
            val loaded = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val mainFile = java.io.File(dir, "audio/${track.main.sha256}")
                    val fastFile = java.io.File(dir, "audio/${track.fast.sha256}")
                    if (!mainFile.isFile || !fastFile.isFile) error("Missing audio files for ${track.id}")
                    val mainBytes = mainFile.readBytes()
                    val fastBytes = fastFile.readBytes()
                    val ffAsset = com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET
                    val ffBytes = loadFinalLapFanfareBytes()
                    val byStem = buildMap {
                        put(track.main.stem, mainBytes)
                        put(track.fast.stem, fastBytes)
                        if (ffBytes != null) put(ffAsset, ffBytes)
                    }
                    val byPath = buildMap {
                        put("${track.main.stem}.bwav", mainBytes)
                        put("${track.fast.stem}.bwav", fastBytes)
                        if (ffBytes != null) put(ffAsset, ffBytes)
                    }
                    val plan = com.mylittleblover.mkwplayer.interactive.ExtraInteractiveCompilers.compileTwoStateLoopPlan(
                        track = track,
                        isBattle = isBattle,
                        bytesByStem = byStem,
                        perfectBeatEnabled = playbackBehavior.perfectBeatEnabled,
                        segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
                    )
                    plan to byPath
                }
            }
            if (isCeremonyLocked || request != packPlaybackRequest) return@launch
            val (plan, bytesByPath) = loaded.getOrElse { err ->
                _ui.value = _ui.value.copy(trackLoading = false, loadingTrackCode = null, loadingTrackTitle = null, status = str(com.mylittleblover.mkwplayer.R.string.player_err_prepare_track), detail = err.message)
                return@launch
            }

            linearCoordinator.yieldToExternalTransport()
            session.stop()
            this@PlayerViewModel.flow = null
            this@PlayerViewModel.flowCompiled = null
            this@PlayerViewModel.performance = null
            this@PlayerViewModel.nativePerformance = null
            this@PlayerViewModel.interactivePlan = plan
            this@PlayerViewModel.flowBytes = bytesByPath
            this@PlayerViewModel.totalFrames = track.main.frames
            this@PlayerViewModel.rate = track.main.sampleRate
            pendingInteractiveStatId = canonicalTrackId
            PlaybackTimers.engine.syncInteractiveContext(true, plan.name)

            _ui.value = _ui.value.copy(
                title = localizedTitle,
                trackLoading = false,
                loadingTrackCode = null,
                loadingTrackTitle = null,
                activeFavoriteId = canonicalTrackId,
                status = str(com.mylittleblover.mkwplayer.R.string.stage_stopped),
                total = EngineSnapshot.formatPosition(track.main.frames, track.main.sampleRate),
                position = "0:00",
                progress = 0f,
                canPause = false,
                canResume = false,
                canStop = false,
                outputActive = session.hasOutput,
                linearMode = false,
                linearTrack = null,
                canSeek = false,
                totalFrames = track.main.frames,
                sampleRate = track.main.sampleRate,
                artworkName = track.artwork,
                detail = null,
                flow = FlowUi(
                    title = localizedTitle,
                    sections = emptyList(),
                    activeSegment = null,
                    summary = "",
                    provenance = "",
                    isProvisional = false,
                    outros = emptyList(),
                    hasFast = true,
                    isBattleTrack = isBattle,
                    availableStageKeys = plan.customInitialSegments.mapNotNull { plan.labels[it] },
                ),
            )
            if (autoStart) {
                startPerformance()
            }
        }
    }

    fun playExtraLinearQueue(
        entries: List<LinearQueue.Entry>,
        startIndex: Int,
        repeatMode: LinearQueue.RepeatMode = LinearQueue.RepeatMode.LIST,
        shuffle: Boolean = false,
        context: LinearPlaybackCoordinator.SessionContext = LinearPlaybackCoordinator.SessionContext(
            contextKind = LinearSessionStore.ContextKind.EXTRA,
            packFingerprints = currentPackFingerprints(),
        ),
    ) {
        if (isCeremonyLocked) return
        val dir = pack?.directory
        if (dir == null || entries.isEmpty()) return
        if (nativePerformance?.isFading == true) return
        val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)
        val requestedEntry = entries.getOrNull(startIndex)
        val visibleEntries = entries.filter { entry ->
            val extraId = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.extraIdFromEntryKey(entry.key)
            val hiddenExtra = extraId != null && !isExtraVisible(extraId, unlocked)
            val hiddenCourse = entry.courseCode != null && isSpecialStageLocked(entry.courseCode)
            !hiddenExtra && !hiddenCourse
        }
        if (visibleEntries.isEmpty()) return
        val mappedStart = requestedEntry?.let { visibleEntries.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        packPlaybackRequest++
        nativePerformance = null
        performance = null
        interactivePlan = null
        flow = null
        flowCompiled = null
        val target = visibleEntries[mappedStart]
        _ui.value = _ui.value.copy(
            title = target.title,
            trackLoading = false,
            loadingTrackCode = null,
            loadingTrackTitle = null,
            activeFavoriteId = canonicalFavoriteRef(target),
            artworkName = target.artworkName,
            flow = null,
            linearMode = true,
            canSeek = true,
            detail = null,
        )
        linearCoordinator.startExtraQueue(
            entries = visibleEntries,
            startIndex = mappedStart,
            packDir = dir,
            repeatMode = repeatMode,
            shuffleOverride = shuffle,
            context = context,
        )
        startPolling()
    }

    private fun builtInSuiteLabel(variantOrRef: String?, fallbackSuiteLabel: String): String {
        val app = getApplication<Application>()
        return when (variantOrRef?.trim()?.lowercase(java.util.Locale.ROOT)) {
            "", "medley", "a", PresetRefs.SYSTEM_DEFAULT ->
                app.getString(com.mylittleblover.mkwplayer.R.string.preset_system_default)
            "a_alt", "a alt", "alt", PresetRefs.SYSTEM_SUITE_A_ALT ->
                app.getString(com.mylittleblover.mkwplayer.R.string.preset_system_suite_a_alt)
            "b", PresetRefs.SYSTEM_SUITE_B ->
                app.getString(com.mylittleblover.mkwplayer.R.string.preset_system_suite_b)
            "c", PresetRefs.SYSTEM_SUITE_C ->
                app.getString(com.mylittleblover.mkwplayer.R.string.preset_system_suite_c)
            "d", PresetRefs.SYSTEM_SUITE_D ->
                app.getString(com.mylittleblover.mkwplayer.R.string.preset_system_suite_d)
            "knockout", PresetRefs.SYSTEM_KNOCKOUT ->
                app.getString(com.mylittleblover.mkwplayer.R.string.preset_system_knockout)
            else -> fallbackSuiteLabel
        }
    }

    private fun resolveQueuePresetDisplay(
        courseCode: String,
        requestedPresetRef: String?,
        resolvedMedley: CourseMedleyCatalog.Medley,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
        fallbackSuiteLabel: String,
    ): Pair<String, String> {
        val isCustom = requestedPresetRef != null && !requestedPresetRef.startsWith("sys:") &&
            userPresets.any { it.id == requestedPresetRef && it.courseCode.equals(courseCode, ignoreCase = true) }
        if (isCustom) {
            val customName = userPresets.firstOrNull {
                it.id == requestedPresetRef && it.courseCode.equals(courseCode, ignoreCase = true)
            }?.name ?: fallbackSuiteLabel
            return requestedPresetRef!! to customName
        }
        val effectiveRef = PresetRefs.forSuiteVariant(resolvedMedley.normalizedSuiteVariant)
        return effectiveRef to builtInSuiteLabel(effectiveRef, fallbackSuiteLabel)
    }

    internal fun effectiveStopPresetRef(
        stageCode: String,
        explicitPresetId: String?,
        stopIndex: Int,
        stopCount: Int,
        officialKind: com.mylittleblover.mkwplayer.routes.OfficialRouteKind?,
        suiteMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteSuiteMode,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset> = emptyList(),
        roadRuleMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode? = null,
    ): String {
        val canonical = stageCode.trim().removeSuffix("_00")
        val medleys = courseMedleyCatalog.forCourse(canonical)
        if (explicitPresetId != null) {
            if (!explicitPresetId.startsWith("sys:")) {
                val hasCustom = userPresets.any {
                    it.id == explicitPresetId && it.courseCode.equals(canonical, ignoreCase = true)
                }
                if (hasCustom) return explicitPresetId
            } else {
                val requestedVariant = when (explicitPresetId) {
                    PresetRefs.SYSTEM_SUITE_A_ALT -> "a_alt"
                    PresetRefs.SYSTEM_SUITE_B -> "b"
                    PresetRefs.SYSTEM_SUITE_C -> "c"
                    PresetRefs.SYSTEM_SUITE_D -> "d"
                    PresetRefs.SYSTEM_KNOCKOUT -> "knockout"
                    else -> "a"
                }
                val resolvedVariant = medleys.firstOrNull { it.normalizedSuiteVariant == requestedVariant }?.normalizedSuiteVariant
                    ?: medleys.firstOrNull { it.normalizedSuiteVariant == "a" }?.normalizedSuiteVariant
                    ?: "a"
                return PresetRefs.forSuiteVariant(resolvedVariant)
            }
        }
        val desiredVariant = com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.resolveSuiteVariantForStop(
            officialKind = officialKind,
            suiteMode = suiteMode,
            stopIndex = stopIndex,
            stopCount = stopCount,
            stageCode = canonical,
            roadRuleMode = roadRuleMode,
        )
        val actualVariant = medleys.firstOrNull { it.normalizedSuiteVariant == desiredVariant }?.normalizedSuiteVariant
            ?: medleys.firstOrNull { it.normalizedSuiteVariant == "a" }?.normalizedSuiteVariant
            ?: "a"
        return PresetRefs.forSuiteVariant(actualVariant)
    }

    internal fun courseMedleysForCourse(code: String): List<Pair<CourseMedleyCatalog.Medley, Boolean>> {
        if (isSpecialStageLocked(code)) return emptyList()
        val dir = pack?.directory
        val catalog = courseMedleyCatalog
        return catalog.forCourse(code).map { medley ->
            val playable = dir != null && com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.resolveMedley(
                medley = medley,
                sampleRate = catalog.sampleRate,
                identities = interactiveIdentities,
                packDir = dir,
                extraCatalog = extraCatalog,
            ) != null
            medley to playable
        }
    }

    fun playCourseMedley(
        medleyId: String,
        languageTag: String,
        unlockedCourses: List<String>,
        suiteLabel: String,
        rallyLabel: String,
    ): Boolean {
        if (isCeremonyLocked) return false
        val dir = pack?.directory ?: return false
        val catalog = courseMedleyCatalog
        val unlockedSet = unlockedCourses.map { it.removeSuffix("_00") }.toSet()
        val entries = catalog.medleys.mapNotNull { medley ->
            if (isSpecialStageLocked(medley.courseCode)) return@mapNotNull null
            if (unlockedSet.isNotEmpty() && medley.courseCode !in unlockedSet) return@mapNotNull null
            val resolved = com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.resolveMedley(
                medley = medley,
                sampleRate = catalog.sampleRate,
                identities = interactiveIdentities,
                packDir = dir,
                extraCatalog = extraCatalog,
            ) ?: return@mapNotNull null
            val variantLabel = builtInSuiteLabel(medley.variant, suiteLabel)
            val courseName = localizedNames?.name(medley.courseCode, languageTag) ?: medley.courseCode
            val title = courseName
            val subtitle = variantLabel
            LinearQueue.Entry(
                key = "medley:${medley.id}",
                title = title,
                subtitle = subtitle,
                sourceType = LinearSessionStore.SourceType.MEDLEY,
                courseCode = medley.courseCode,
                presetId = PresetRefs.forSuiteVariant(medley.normalizedSuiteVariant),
                medleyId = medley.id,
                arrangement = variantLabel,
                durationMs = medley.durationMs,
                artworkName = extraCatalog.courseArtwork[medley.courseCode],
                courseMedley = resolved,
            )
        }
        val startIndex = entries.indexOfFirst { it.key == "medley:$medleyId" }
        if (startIndex < 0) return false
        playExtraLinearQueue(
            entries = entries,
            startIndex = startIndex,
            repeatMode = LinearQueue.RepeatMode.LIST,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.COURSE_MEDLEY,
                contextId = medleyId,
                contextTitle = suiteLabel,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    private fun isEditorTargetLocked(code: String): Boolean {
        return if (code.startsWith("extra:")) {
            val targetId = code.removePrefix("extra:")
            val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)
            !isExtraVisible(targetId, unlocked)
        } else {
            isSpecialStageLocked(code)
        }
    }

    private fun editorSourceResolver(dir: java.io.File): CourseFlowRecipeBuilder.SourceResolver {
        val base = CourseFlowRecipeBuilder.packSourceResolver(pack?.manifest, interactiveIdentities, dir, extraCatalog)
        val manifestAssets = pack?.manifest?.assets?.associateBy { it.digest.sha256 }.orEmpty()
        return object : CourseFlowRecipeBuilder.SourceResolver {
            override fun findByFileName(fileName: String): CourseFlowRecipeBuilder.AssetHeader? {
                base.findByFileName(fileName)?.let { return it }
                val stem = fileName.removeSuffix(".bwav")
                if (fileName.equals(com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.ASSET, ignoreCase = true) ||
                    stem.equals("BGM_Seq_FinalLapFF", ignoreCase = true)
                ) {
                    val bytes = loadFinalLapFanfareBytes()
                    if (bytes != null) {
                        val header = (com.mylittleblover.mkwplayer.audio.BwavHeader.read(bytes)
                            as? com.mylittleblover.mkwplayer.audio.BwavHeader.Result.Ok)?.info
                        return CourseFlowRecipeBuilder.AssetHeader(
                            stem = "BGM_Seq_FinalLapFF",
                            digest = com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.SHA256,
                            frames = header?.frames?.toLong() ?: 268_759L,
                            sampleRate = header?.sampleRate ?: 48_000,
                            loopStart = null,
                            loopEnd = null,
                        )
                    }
                }
                val digest = resolveAudioSha256(stem) ?: return null
                val audioFile = java.io.File(dir, "audio/$digest")
                if (!audioFile.isFile) return null
                val header = runCatching {
                    audioFile.inputStream().use { input ->
                        val head = ByteArray(1024)
                        val count = input.read(head)
                        if (count <= 0) return@use null
                        (com.mylittleblover.mkwplayer.audio.BwavHeader.read(head.copyOf(count))
                            as? com.mylittleblover.mkwplayer.audio.BwavHeader.Result.Ok)?.info
                    }
                }.getOrNull()
                if (header != null) {
                    return CourseFlowRecipeBuilder.AssetHeader(
                        stem = stem,
                        digest = digest,
                        frames = header.frames.toLong(),
                        sampleRate = header.sampleRate,
                        loopStart = header.loopStart?.toLong(),
                        loopEnd = header.loopEnd?.toLong(),
                    )
                }
                val asset = manifestAssets[digest] ?: return null
                return CourseFlowRecipeBuilder.AssetHeader(
                    stem = stem,
                    digest = digest,
                    frames = asset.frames,
                    sampleRate = asset.sampleRate,
                    loopStart = null,
                    loopEnd = null,
                )
            }
        }
    }

    private fun inspectTargetForEditor(
        code: String,
        dir: java.io.File,
        resolver: CourseFlowRecipeBuilder.SourceResolver,
    ): CourseFlowRecipeBuilder.CourseInventory? {
        return if (code.startsWith("extra:")) {
            CourseFlowRecipeBuilder.inspectExtra(
                extraId = code,
                extraCatalog = extraCatalog,
                extraMedleyCatalog = extraMedleyCatalog,
                packDir = dir,
                hasFanfareAsset = loadFinalLapFanfareBytes() != null,
            )
        } else {
            CourseFlowRecipeBuilder.inspectCourse(code, minfCatalog, courseMedleyCatalog, resolver)
        }
    }

    private fun artworkForEditorTarget(code: String): String? {
        if (!code.startsWith("extra:")) return extraCatalog.courseArtwork[code.removeSuffix("_00")]
        val targetId = code.removePrefix("extra:")
        return extraCatalog.sfcCourses.firstOrNull { it.id == targetId }?.artwork
            ?: extraCatalog.battleTracks.firstOrNull { it.id == targetId }?.artwork
            ?: extraCatalog.otherMusic.firstOrNull { it.id == targetId }?.artwork
    }

    internal fun inspectCourseForEditor(code: String): CourseFlowRecipeBuilder.CourseInventory? {
        val dir = pack?.directory ?: return null
        if (isEditorTargetLocked(code)) return null
        val resolver = editorSourceResolver(dir)
        return inspectTargetForEditor(code, dir, resolver)
    }

    internal fun previewCustomPreset(
        preset: CoursePreset,
        courseTitle: String,
        suiteLabel: String,
    ): Boolean {
        if (isCeremonyLocked) return false
        val dir = pack?.directory ?: return false
        if (isEditorTargetLocked(preset.courseCode)) return false
        val resolver = editorSourceResolver(dir)
        val inventory = inspectTargetForEditor(preset.courseCode, dir, resolver) ?: return false
        val resolved = CourseFlowRecipeBuilder.compileUserPreset(
            preset = preset,
            inventory = inventory,
            minf = minfCatalog,
            resolver = resolver,
            segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
        )
            ?: return false
        val logical = if (preset.courseCode.startsWith("extra:")) {
            preset.courseCode
        } else {
            preset.courseCode.removeSuffix("_00")
        }
        val entry = LinearQueue.Entry(
            key = "preset:${preset.id}",
            title = courseTitle,
            subtitle = "$suiteLabel · ${preset.name}",
            sourceType = LinearSessionStore.SourceType.PRESET,
            courseCode = logical,
            presetId = preset.id,
            durationMs = resolved.medley.durationMs,
            artworkName = artworkForEditorTarget(logical),
            courseMedley = resolved,
        )
        playExtraLinearQueue(
            entries = listOf(entry),
            startIndex = 0,
            repeatMode = LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.COURSE_MEDLEY,
                contextId = preset.id,
                contextTitle = preset.name,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    private fun resolveAudioSha256(stem: String): String? =
        interactiveIdentities.digestForFileName("$stem.bwav") ?: extraCatalog.digestForStem(stem)

    internal suspend fun loadWaveformForStem(
        stem: String,
        bucketCount: Int = 1200,
    ): com.mylittleblover.mkwplayer.audio.BwavWaveformExtractor.WaveformPeaks? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = pack?.directory ?: return@withContext null
            val digest = resolveAudioSha256(stem) ?: return@withContext null
            val file = java.io.File(dir, "audio/$digest")
            com.mylittleblover.mkwplayer.audio.BwavWaveformExtractor.extract(file, digest, bucketCount)
        }

    internal fun compileAndExportPreset(
        preset: CoursePreset,
    ): Pair<com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.ResolvedMedley, String>? {
        val dir = pack?.directory ?: return null
        if (isEditorTargetLocked(preset.courseCode)) return null
        val resolver = editorSourceResolver(dir)
        val inventory = inspectTargetForEditor(preset.courseCode, dir, resolver) ?: return null
        val resolved = CourseFlowRecipeBuilder.compileUserPreset(
            preset = preset,
            inventory = inventory,
            minf = minfCatalog,
            resolver = resolver,
            segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
        ) ?: return null
        return resolved to CourseFlowRecipeBuilder.exportPresetConfigJson(preset, resolved)
    }

    private val flowAudioRenderer = com.mylittleblover.mkwplayer.audio.FlowAudioRenderer()
    private var renderAudioJob: Job? = null

    fun cancelRenderFlowAudio() {
        renderAudioJob?.cancel()
        renderAudioJob = null
        _ui.value = _ui.value.copy(
            isRenderingAudio = false,
            renderAudioStatus = null,
            renderAudioError = null,
        )
    }

    fun dismissRenderFlowDialog() {
        if (_ui.value.isRenderingAudio) return
        _ui.value = _ui.value.copy(
            renderAudioProgress = 0f,
            renderAudioStatus = null,
            renderAudioError = null,
            renderAudioOutputPath = null,
        )
    }

    /** Renders a Flow Editor preset offline into a 48 kHz 16-bit stereo WAV honoring OutputTrim & export location (#17). */
    internal fun renderFlowPresetToAudio(
        preset: CoursePreset,
        languageTag: String,
    ) {
        if (_ui.value.isRenderingAudio) return
        val dir = pack?.directory
        if (dir == null) {
            _ui.value = _ui.value.copy(renderAudioError = str(com.mylittleblover.mkwplayer.R.string.flow_render_error_title))
            return
        }
        if (isEditorTargetLocked(preset.courseCode)) {
            _ui.value = _ui.value.copy(renderAudioError = str(com.mylittleblover.mkwplayer.R.string.player_blocked_special_locked))
            return
        }
        val appCtx = getApplication<Application>()
        val trim = com.mylittleblover.mkwplayer.audio.OutputTrimSettings.current(appCtx)
        val exportCfg = com.mylittleblover.mkwplayer.audio.AudioExportSettings.current(appCtx)
        _ui.value = _ui.value.copy(
            isRenderingAudio = true,
            renderAudioProgress = 0f,
            renderAudioStatus = str(com.mylittleblover.mkwplayer.R.string.flow_render_in_progress),
            renderAudioError = null,
            renderAudioOutputPath = null,
            outputTrimEnabled = trim.gainDb > 0.5,
            exportFolderDisplay = exportCfg.exportDisplayPath
                ?: com.mylittleblover.mkwplayer.audio.AudioExportSettings.DEFAULT_RELATIVE_PATH,
        )
        renderAudioJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = editorSourceResolver(dir)
                val inventory = inspectTargetForEditor(preset.courseCode, dir, resolver)
                if (inventory == null) {
                    withContext(Dispatchers.Main) {
                        _ui.value = _ui.value.copy(
                            isRenderingAudio = false,
                            renderAudioError = str(com.mylittleblover.mkwplayer.R.string.flow_render_error_title),
                        )
                    }
                    return@launch
                }
                val resolved = CourseFlowRecipeBuilder.compileUserPreset(
                    preset = preset,
                    inventory = inventory,
                    minf = minfCatalog,
                    resolver = resolver,
                    segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
                )
                if (resolved == null) {
                    withContext(Dispatchers.Main) {
                        _ui.value = _ui.value.copy(
                            isRenderingAudio = false,
                            renderAudioError = str(com.mylittleblover.mkwplayer.R.string.flow_render_error_title),
                        )
                    }
                    return@launch
                }
                val filesByStem = linkedMapOf<String, java.io.File>()
                val fallbackBytes = linkedMapOf<String, ByteArray>()
                for ((stem, sha) in resolved.stemDigests) {
                    if (stem.equals("BGM_Seq_FinalLapFF", ignoreCase = true)) {
                        loadFinalLapFanfareBytes()?.let { fallbackBytes[stem] = it }
                    } else {
                        val f = java.io.File(dir, "audio/$sha")
                        if (f.isFile) filesByStem[stem] = f
                    }
                }
                val compiledGraph = com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.compileGraphFromFiles(
                    medley = resolved.medley,
                    sampleRate = resolved.sampleRate,
                    filesByStem = filesByStem,
                    knownDigestsHex = resolved.stemDigests,
                    fallbackBytesByStem = fallbackBytes,
                )
                val spec = com.mylittleblover.mkwplayer.audio.FlowAudioRenderer.CompiledFlowSpec.from(
                    compiled = compiledGraph,
                    sampleRate = resolved.sampleRate,
                )
                val baseCourse = localizedNames?.name(preset.courseCode.removePrefix("extra:").removeSuffix("_00"), languageTag)
                    ?: preset.courseCode.removePrefix("extra:").removeSuffix("_00")
                val safeName = "${baseCourse}_${preset.name}"
                    .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
                    .trim('_')
                    .ifBlank { "JukeKart_Flow" }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT)
                    .format(java.util.Date())
                val fileName = "${safeName}_$stamp.wav"

                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(
                        renderAudioStatus = str(com.mylittleblover.mkwplayer.R.string.flow_render_in_progress),
                    )
                }

                var outputDisplayPath = ""
                var renderResult: com.mylittleblover.mkwplayer.audio.FlowAudioRenderer.RenderResult =
                    com.mylittleblover.mkwplayer.audio.FlowAudioRenderer.RenderResult.Failed("Export failed")
                var lastProgressPush = 0L
                val progressCb: (Float) -> Unit = { p ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (p >= 0.999f || now - lastProgressPush >= 60L) {
                        lastProgressPush = now
                        viewModelScope.launch(Dispatchers.Main) {
                            if (_ui.value.isRenderingAudio) {
                                _ui.value = _ui.value.copy(renderAudioProgress = p.coerceIn(0f, 1f))
                            }
                        }
                    }
                }

                val treeUriRaw = exportCfg.exportTreeUri
                if (!treeUriRaw.isNullOrBlank()) {
                    val treeUri = android.net.Uri.parse(treeUriRaw)
                    val parentDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                    val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                    val createdUri = android.provider.DocumentsContract.createDocument(
                        appCtx.contentResolver,
                        parentDocUri,
                        "audio/wav",
                        fileName,
                    ) ?: error("Unable to create file in selected folder.")
                    outputDisplayPath = "${exportCfg.exportDisplayPath ?: "Folder"}/$fileName"
                    appCtx.contentResolver.openOutputStream(createdUri, "w")?.use { out ->
                        renderResult = flowAudioRenderer.renderFlowToWav(spec, out, trim, progressCb)
                    } ?: error("Unable to open output stream for selected folder.")
                    if (renderResult !is com.mylittleblover.mkwplayer.audio.FlowAudioRenderer.RenderResult.Success) {
                        runCatching { android.provider.DocumentsContract.deleteDocument(appCtx.contentResolver, createdUri) }
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                        put(
                            android.provider.MediaStore.Audio.Media.RELATIVE_PATH,
                            "${android.os.Environment.DIRECTORY_MUSIC}/JukeKart",
                        )
                        put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                    val resolverMedia = appCtx.contentResolver
                    val uri = resolverMedia.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("Unable to create MediaStore audio entry.")
                    outputDisplayPath = "${com.mylittleblover.mkwplayer.audio.AudioExportSettings.DEFAULT_RELATIVE_PATH}/$fileName"
                    try {
                        resolverMedia.openOutputStream(uri, "w")?.use { out ->
                            renderResult = flowAudioRenderer.renderFlowToWav(spec, out, trim, progressCb)
                        } ?: error("Unable to open MediaStore output stream.")
                        if (renderResult is com.mylittleblover.mkwplayer.audio.FlowAudioRenderer.RenderResult.Success) {
                            val done = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                            }
                            resolverMedia.update(uri, done, null, null)
                        } else {
                            resolverMedia.delete(uri, null, null)
                        }
                    } catch (t: Throwable) {
                        runCatching { resolverMedia.delete(uri, null, null) }
                        throw t
                    }
                } else {
                    val baseDir = java.io.File(
                        appCtx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: appCtx.filesDir,
                        "JukeKart",
                    )
                    baseDir.mkdirs()
                    val destFile = java.io.File(baseDir, fileName)
                    outputDisplayPath = destFile.absolutePath
                    renderResult = flowAudioRenderer.renderFlowToFile(spec, destFile, trim, progressCb)
                }

                withContext(Dispatchers.Main) {
                    when (val res = renderResult) {
                        is com.mylittleblover.mkwplayer.audio.FlowAudioRenderer.RenderResult.Success -> {
                            _ui.value = _ui.value.copy(
                                isRenderingAudio = false,
                                renderAudioProgress = 1f,
                                renderAudioStatus = str(com.mylittleblover.mkwplayer.R.string.flow_render_success_title),
                                renderAudioError = null,
                                renderAudioOutputPath = outputDisplayPath,
                            )
                        }
                        is com.mylittleblover.mkwplayer.audio.FlowAudioRenderer.RenderResult.Cancelled -> {
                            _ui.value = _ui.value.copy(
                                isRenderingAudio = false,
                                renderAudioProgress = 0f,
                                renderAudioStatus = null,
                                renderAudioError = null,
                            )
                        }
                        is com.mylittleblover.mkwplayer.audio.FlowAudioRenderer.RenderResult.Failed -> {
                            _ui.value = _ui.value.copy(
                                isRenderingAudio = false,
                                renderAudioStatus = null,
                                renderAudioError = res.reason,
                            )
                        }
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(
                        isRenderingAudio = false,
                        renderAudioStatus = null,
                        renderAudioError = null,
                    )
                }
                throw ce
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(
                        isRenderingAudio = false,
                        renderAudioStatus = null,
                        renderAudioError = t.message ?: t.javaClass.simpleName,
                    )
                }
            }
        }
    }

    internal fun previewCourseSeam(
        preset: CoursePreset,
        seamIndex: Int,
        courseTitle: String,
        suiteLabel: String,
    ): Boolean {
        if (isCeremonyLocked) return false
        val dir = pack?.directory ?: return false
        if (isEditorTargetLocked(preset.courseCode)) return false
        val resolver = editorSourceResolver(dir)
        val inventory = inspectTargetForEditor(preset.courseCode, dir, resolver) ?: return false
        val resolved = CourseFlowRecipeBuilder.compileSeamPreview(
            preset = preset,
            seamIndex = seamIndex,
            inventory = inventory,
            minf = minfCatalog,
            resolver = resolver,
            segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
        ) ?: return false
        val logical = if (preset.courseCode.startsWith("extra:")) {
            preset.courseCode
        } else {
            preset.courseCode.removeSuffix("_00")
        }
        val entry = LinearQueue.Entry(
            key = "seam:${preset.id}:$seamIndex",
            title = courseTitle,
            subtitle = suiteLabel,
            sourceType = LinearSessionStore.SourceType.PRESET,
            courseCode = logical,
            presetId = preset.id,
            durationMs = resolved.medley.durationMs,
            artworkName = artworkForEditorTarget(logical),
            courseMedley = resolved,
        )
        playExtraLinearQueue(
            entries = listOf(entry),
            startIndex = 0,
            repeatMode = LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.COURSE_MEDLEY,
                contextId = "${preset.id}_seam_$seamIndex",
                contextTitle = suiteLabel,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    internal fun extraMedleysForTarget(
        targetId: String,
    ): List<com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.ExtraMedleyEntry> {
        val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)
        if (!isExtraVisible(targetId, unlocked)) {
            return emptyList()
        }
        return extraMedleyCatalog.forTarget(targetId)
    }

    private fun resolveExtraMedleyEntry(
        entry: com.mylittleblover.mkwplayer.interactive.ExtraMedleyCatalog.ExtraMedleyEntry?,
        dir: java.io.File,
    ): com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.ResolvedMedley? {
        if (entry == null) return null
        val digests = linkedMapOf<String, String>()
        for (stem in entry.medley.uniqueStems) {
            if (stem.equals("BGM_Seq_FinalLapFF", ignoreCase = true)) {
                digests[stem] = com.mylittleblover.mkwplayer.interactive.FinalLapFanfareCompiler.SHA256
                continue
            }
            val sha = resolveAudioSha256(stem) ?: return null
            if (!java.io.File(dir, "audio/$sha").isFile) return null
            digests[stem] = sha
        }
        return com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.ResolvedMedley(
            medley = entry.medley,
            sampleRate = extraMedleyCatalog.sampleRate,
            stemDigests = digests,
        )
    }

    internal fun playExtraMedley(
        medleyId: String,
        title: String,
        subtitle: String,
        artworkName: String?,
    ): Boolean {
        if (isCeremonyLocked) return false
        val dir = pack?.directory ?: return false
        val item = extraMedleyCatalog.byMedleyId(medleyId) ?: return false
        val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)
        if (!isExtraVisible(item.targetId, unlocked)) {
            return false
        }
        val resolved = resolveExtraMedleyEntry(item, dir) ?: return false
        val entry = LinearQueue.Entry(
            key = "extra_medley:${item.medley.id}",
            title = title,
            subtitle = subtitle,
            sourceType = LinearSessionStore.SourceType.MEDLEY,
            courseCode = item.medley.courseCode,
            medleyId = item.medley.id,
            durationMs = item.medley.durationMs,
            artworkName = item.artwork ?: artworkName,
            courseMedley = resolved,
        )
        playExtraLinearQueue(
            entries = listOf(entry),
            startIndex = 0,
            repeatMode = LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.COURSE_MEDLEY,
                contextId = item.medley.id,
                contextTitle = title,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    /**
     * Launches linear playback of a user playlist (#50-#53, #61) with frozen queue state,
     * supporting courses (with presets & optional native cross-track handover), Jukebox tracks,
     * Two-State retro/battle tracks, and Other/Short/Special extra tracks.
     */
    fun playUserPlaylist(
        playlist: UserPlaylist,
        presets: List<CoursePreset>,
        startIndex: Int,
        shuffle: Boolean?,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        nativeTransitionPrefix: String,
    ): Boolean {
        if (isCeremonyLocked) return false
        val dir = pack?.directory ?: return false
        if (playlist.courseIds.isEmpty()) return false
        val resolver = editorSourceResolver(dir)
        val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)
        val gamesById = jukebox.games.associateBy { it.id }

        fun compileExtraInteractiveEntry(
            idx: Int,
            targetId: String,
            title: String,
            artwork: String,
            defaultAsset: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef,
            config: PlaylistEntryConfig,
        ): LinearQueue.Entry? {
            if (!isExtraVisible(targetId, unlocked)) return null
            val editorCode = "extra:$targetId"
            val userPreset = if (PresetRefs.isUser(config.presetId)) {
                presets.firstOrNull { it.id == config.presetId && it.courseCode.equals(editorCode, ignoreCase = true) }
            } else {
                null
            }
            if (userPreset != null) {
                val inventory = inspectTargetForEditor(editorCode, dir, resolver)
                val resolved = inventory?.let {
                    CourseFlowRecipeBuilder.compileUserPreset(
                        preset = userPreset,
                        inventory = it,
                        minf = minfCatalog,
                        resolver = resolver,
                        segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
                    )
                }
                if (resolved != null) {
                    return LinearQueue.Entry(
                        key = "pl:${playlist.id}:$idx:$editorCode:${userPreset.id}",
                        title = title,
                        subtitle = "${playlist.name} · ${userPreset.name}",
                        sourceType = LinearSessionStore.SourceType.PRESET,
                        courseCode = editorCode,
                        presetId = userPreset.id,
                        arrangement = userPreset.name,
                        durationMs = resolved.medley.durationMs,
                        artworkName = artwork,
                        courseMedley = resolved,
                    )
                }
            }
            val builtInMedley = extraMedleyCatalog.forTarget(targetId).firstOrNull()
            val resolvedExtra = resolveExtraMedleyEntry(builtInMedley, dir)
            if (builtInMedley != null && resolvedExtra != null) {
                return LinearQueue.Entry(
                    key = "pl:${playlist.id}:$idx:$editorCode:${builtInMedley.medley.id}",
                    title = title,
                    subtitle = "${playlist.name} · $suiteLabel",
                    sourceType = LinearSessionStore.SourceType.MEDLEY,
                    courseCode = editorCode,
                    medleyId = builtInMedley.medley.id,
                    arrangement = suiteLabel,
                    durationMs = builtInMedley.medley.durationMs,
                    artworkName = artwork,
                    courseMedley = resolvedExtra,
                )
            }
            if (!java.io.File(dir, "audio/${defaultAsset.sha256}").isFile) return null
            val durationMs = if (defaultAsset.sampleRate > 0) {
                defaultAsset.frames * 1000L / defaultAsset.sampleRate
            } else 0L
            return LinearQueue.Entry(
                key = "pl:${playlist.id}:$idx:extra:$targetId",
                title = title,
                subtitle = playlist.name,
                sourceType = LinearSessionStore.SourceType.EXTRA,
                durationMs = durationMs,
                artworkName = artwork,
                assetSha256 = defaultAsset.sha256,
                loop = defaultAsset.loop,
                loopStart = defaultAsset.loopStart,
                loopEnd = defaultAsset.loopEnd,
            )
        }

        val rawEntries = playlist.courseIds.mapIndexedNotNull { idx, rawItemId ->
            val config = playlist.configAt(idx)
            when (val ref = PlaylistItemRef.parse(rawItemId)) {
                is PlaylistItemRef.Course -> {
                    val logical = ref.logicalCode
                    if (isSpecialStageLocked(logical)) return@mapIndexedNotNull null
                    val resolved = CourseFlowRecipeBuilder.resolvePresetRef(
                        courseCode = logical,
                        presetRef = config.presetId,
                        userPresets = presets,
                        minf = minfCatalog,
                        medleyCatalog = courseMedleyCatalog,
                        resolver = resolver,
                        segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
                    ) ?: return@mapIndexedNotNull null

                    val (effectiveRef, presetDisplayName) = resolveQueuePresetDisplay(
                        courseCode = logical,
                        requestedPresetRef = config.presetId,
                        resolvedMedley = resolved.medley,
                        userPresets = presets,
                        fallbackSuiteLabel = suiteLabel,
                    )
                    val matchingUserPreset = if (PresetRefs.isUser(effectiveRef)) {
                        presets.firstOrNull { it.id == effectiveRef && it.courseCode.equals(logical, ignoreCase = true) }
                    } else {
                        null
                    }
                    val courseName = localizedNames?.name(logical, languageTag) ?: logical
                    val nativeEnabled = config.nativeTransitionEnabled ?: playlist.nativeTransitionDefault
                    LinearQueue.Entry(
                        key = "pl:${playlist.id}:$idx:$logical:$effectiveRef",
                        title = courseName,
                        subtitle = "${playlist.name} · $presetDisplayName",
                        sourceType = if (matchingUserPreset != null) {
                            LinearSessionStore.SourceType.PRESET
                        } else {
                            LinearSessionStore.SourceType.MEDLEY
                        },
                        courseCode = logical,
                        presetId = effectiveRef,
                        medleyId = resolved.medley.id.takeIf { matchingUserPreset == null },
                        nativeTransitionEnabled = nativeEnabled,
                        nativeTransitionOverride = config.nativeTransitionEnabled,
                        nativeTransitionSourceLabel = if (nativeEnabled && resolved.outroHandoverFrame != null) {
                            "$nativeTransitionPrefix · $presetDisplayName"
                        } else {
                            null
                        },
                        arrangement = presetDisplayName,
                        durationMs = resolved.medley.durationMs,
                        artworkName = extraCatalog.courseArtwork[logical],
                        courseMedley = resolved,
                    )
                }
                is PlaylistItemRef.Jukebox -> {
                    val item = jukebox.itemsByKey[ref.trackKey] ?: return@mapIndexedNotNull null
                    val trackDir = jukebox.packByKey[ref.trackKey] ?: dir
                    if (!java.io.File(trackDir, "audio/${item.track.primary.sha256}").isFile) return@mapIndexedNotNull null
                    val game = gamesById[item.gameId]
                    val gameTitle = game?.localizedName(languageTag) ?: item.gameId
                    LinearQueue.Entry(
                        key = "pl:${playlist.id}:$idx:${item.key}",
                        title = item.displayTitle(languageTag),
                        subtitle = "${playlist.name} · $gameTitle",
                        sourceType = LinearSessionStore.SourceType.JUKEBOX,
                        gameId = item.gameId,
                        gameTitle = gameTitle,
                        yearLabel = game?.yearLabel?.takeIf { it.isNotBlank() },
                        arrangement = item.displayArrangement(languageTag),
                        durationMs = item.durationMs,
                        artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
                        assetSha256 = item.track.primary.sha256,
                        sourceTrackKey = item.key,
                    )
                }
                is PlaylistItemRef.SfcTrack -> {
                    val track = extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId } ?: return@mapIndexedNotNull null
                    compileExtraInteractiveEntry(
                        idx = idx,
                        targetId = track.id,
                        title = track.localizedName(languageTag),
                        artwork = track.artwork,
                        defaultAsset = track.main,
                        config = config,
                    )
                }
                is PlaylistItemRef.BattleTrack -> {
                    val track = extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId } ?: return@mapIndexedNotNull null
                    compileExtraInteractiveEntry(
                        idx = idx,
                        targetId = track.id,
                        title = track.localizedName(languageTag),
                        artwork = track.artwork,
                        defaultAsset = track.main,
                        config = config,
                    )
                }
                is PlaylistItemRef.ExtraTrack -> {
                    val extraId = ref.extraId
                    if (!isExtraVisible(extraId, unlocked)) {
                        return@mapIndexedNotNull null
                    }
                    val other = extraCatalog.otherMusic.firstOrNull { it.id == extraId }
                    if (other != null) {
                        val title = other.localizedName(languageTag)
                        when (other) {
                            is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle -> {
                                compileExtraInteractiveEntry(
                                    idx = idx,
                                    targetId = other.id,
                                    title = title,
                                    artwork = other.artwork,
                                    defaultAsset = other.main,
                                    config = config,
                                )
                            }
                            is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.LinearAsset -> {
                                if (!java.io.File(dir, "audio/${other.asset.sha256}").isFile) return@mapIndexedNotNull null
                                LinearQueue.Entry(
                                    key = "pl:${playlist.id}:$idx:extra:${other.id}",
                                    title = title,
                                    subtitle = playlist.name,
                                    sourceType = LinearSessionStore.SourceType.EXTRA,
                                    durationMs = other.durationMs,
                                    artworkName = other.artwork,
                                    assetSha256 = other.asset.sha256,
                                    loop = other.asset.loop,
                                    loopStart = other.asset.loopStart,
                                    loopEnd = other.asset.loopEnd,
                                )
                            }
                            is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.OverlaidOpening -> {
                                if (!java.io.File(dir, "audio/${other.op.sha256}").isFile ||
                                    !java.io.File(dir, "audio/${other.telop.sha256}").isFile
                                ) return@mapIndexedNotNull null
                                LinearQueue.Entry(
                                    key = "pl:${playlist.id}:$idx:extra:${other.id}",
                                    title = title,
                                    subtitle = playlist.name,
                                    sourceType = LinearSessionStore.SourceType.EXTRA,
                                    durationMs = other.durationMs,
                                    artworkName = other.artwork,
                                    overlaidOpening = other,
                                )
                            }
                            is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> {
                                if (other.parts.any { !java.io.File(dir, "audio/${it.asset.sha256}").isFile }) {
                                    return@mapIndexedNotNull null
                                }
                                LinearQueue.Entry(
                                    key = "pl:${playlist.id}:$idx:extra:${other.id}",
                                    title = title,
                                    subtitle = playlist.name,
                                    sourceType = LinearSessionStore.SourceType.EXTRA,
                                    durationMs = other.durationMs,
                                    artworkName = other.artwork,
                                    chainedSequence = other,
                                )
                            }
                        }
                    } else {
                        val simple = extraCatalog.shortSounds.firstOrNull { it.id == extraId }
                            ?: extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == extraId }
                            ?: return@mapIndexedNotNull null
                        if (!java.io.File(dir, "audio/${simple.asset.sha256}").isFile) return@mapIndexedNotNull null
                        LinearQueue.Entry(
                            key = "pl:${playlist.id}:$idx:extra:${simple.id}",
                            title = simple.localizedName(languageTag),
                            subtitle = playlist.name,
                            sourceType = LinearSessionStore.SourceType.EXTRA,
                            durationMs = simple.durationMs,
                            artworkName = simple.artwork,
                            assetSha256 = simple.asset.sha256,
                            loop = simple.asset.loop,
                            loopStart = simple.asset.loopStart,
                            loopEnd = simple.asset.loopEnd,
                        )
                    }
                }
            }
        }
        if (rawEntries.isEmpty()) return false
        val clampedStart = startIndex.coerceIn(0, rawEntries.lastIndex)
        playExtraLinearQueue(
            entries = rawEntries,
            startIndex = clampedStart,
            repeatMode = LinearQueue.RepeatMode.LIST,
            shuffle = shuffle == true,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.PLAYLIST,
                contextId = playlist.id,
                contextTitle = playlist.name,
                nativeTransitionDefault = playlist.nativeTransitionDefault,
                packFingerprints = currentPackFingerprints(),
            ),
        )
        requestNotificationPermissionIfNeeded()
        return true
    }

    private fun resolveEntriesForRefs(
        rawRefs: List<String>,
        presets: List<CoursePreset>,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        queueSubtitle: String,
        nativeTransitionPrefix: String,
    ): List<LinearQueue.Entry> {
        val dir = pack?.directory ?: return emptyList()
        val resolver = editorSourceResolver(dir)
        val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)
        val gamesById = jukebox.games.associateBy { it.id }

        fun compileExtraEntry(
            idx: Int,
            targetId: String,
            title: String,
            artwork: String,
            defaultAsset: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef,
        ): LinearQueue.Entry? {
            if (!isExtraVisible(targetId, unlocked)) return null
            val editorCode = "extra:$targetId"
            val builtInMedley = extraMedleyCatalog.forTarget(targetId).firstOrNull()
            val resolvedExtra = resolveExtraMedleyEntry(builtInMedley, dir)
            if (builtInMedley != null && resolvedExtra != null) {
                return LinearQueue.Entry(
                    key = "ref:$idx:$editorCode:${builtInMedley.medley.id}:${java.util.UUID.randomUUID()}",
                    title = title,
                    subtitle = "$queueSubtitle · $suiteLabel",
                    sourceType = LinearSessionStore.SourceType.MEDLEY,
                    courseCode = editorCode,
                    medleyId = builtInMedley.medley.id,
                    arrangement = suiteLabel,
                    durationMs = builtInMedley.medley.durationMs,
                    artworkName = artwork,
                    courseMedley = resolvedExtra,
                )
            }
            if (!java.io.File(dir, "audio/${defaultAsset.sha256}").isFile) return null
            val durationMs = if (defaultAsset.sampleRate > 0) {
                defaultAsset.frames * 1000L / defaultAsset.sampleRate
            } else 0L
            return LinearQueue.Entry(
                key = "ref:$idx:extra:$targetId:${java.util.UUID.randomUUID()}",
                title = title,
                subtitle = queueSubtitle,
                sourceType = LinearSessionStore.SourceType.EXTRA,
                durationMs = durationMs,
                artworkName = artwork,
                assetSha256 = defaultAsset.sha256,
                loop = defaultAsset.loop,
                loopStart = defaultAsset.loopStart,
                loopEnd = defaultAsset.loopEnd,
            )
        }

        return rawRefs.mapIndexedNotNull { idx, rawItemId ->
            when (val ref = PlaylistItemRef.parse(rawItemId)) {
                is PlaylistItemRef.Course -> {
                    val logical = ref.logicalCode
                    if (isSpecialStageLocked(logical)) return@mapIndexedNotNull null
                    val resolved = CourseFlowRecipeBuilder.resolvePresetRef(
                        courseCode = logical,
                        presetRef = PresetRefs.SYSTEM_DEFAULT,
                        userPresets = presets,
                        minf = minfCatalog,
                        medleyCatalog = courseMedleyCatalog,
                        resolver = resolver,
                        segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
                    ) ?: return@mapIndexedNotNull null
                    val (effectiveRef, presetDisplayName) = resolveQueuePresetDisplay(
                        courseCode = logical,
                        requestedPresetRef = PresetRefs.SYSTEM_DEFAULT,
                        resolvedMedley = resolved.medley,
                        userPresets = presets,
                        fallbackSuiteLabel = suiteLabel,
                    )
                    val courseName = localizedNames?.name(logical, languageTag) ?: logical
                    LinearQueue.Entry(
                        key = "ref:$idx:$logical:${java.util.UUID.randomUUID()}",
                        title = courseName,
                        subtitle = presetDisplayName,
                        sourceType = LinearSessionStore.SourceType.MEDLEY,
                        courseCode = logical,
                        presetId = effectiveRef,
                        medleyId = resolved.medley.id,
                        arrangement = presetDisplayName,
                        durationMs = resolved.medley.durationMs,
                        artworkName = extraCatalog.courseArtwork[logical],
                        courseMedley = resolved,
                    )
                }
                is PlaylistItemRef.Jukebox -> {
                    val item = jukebox.itemsByKey[ref.trackKey] ?: return@mapIndexedNotNull null
                    val trackDir = jukebox.packByKey[ref.trackKey] ?: dir
                    if (!java.io.File(trackDir, "audio/${item.track.primary.sha256}").isFile) return@mapIndexedNotNull null
                    val game = gamesById[item.gameId]
                    val gameTitle = game?.localizedName(languageTag) ?: item.gameId
                    LinearQueue.Entry(
                        key = "ref:$idx:${item.key}:${java.util.UUID.randomUUID()}",
                        title = item.displayTitle(languageTag),
                        subtitle = gameTitle,
                        sourceType = LinearSessionStore.SourceType.JUKEBOX,
                        gameId = item.gameId,
                        gameTitle = gameTitle,
                        yearLabel = game?.yearLabel?.takeIf { it.isNotBlank() },
                        arrangement = item.displayArrangement(languageTag),
                        durationMs = item.durationMs,
                        artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
                        assetSha256 = item.track.primary.sha256,
                        sourceTrackKey = item.key,
                    )
                }
                is PlaylistItemRef.SfcTrack -> {
                    val track = extraCatalog.sfcCourses.firstOrNull { it.id == ref.trackId } ?: return@mapIndexedNotNull null
                    compileExtraEntry(idx, track.id, track.localizedName(languageTag), track.artwork, track.main)
                }
                is PlaylistItemRef.BattleTrack -> {
                    val track = extraCatalog.battleTracks.firstOrNull { it.id == ref.trackId } ?: return@mapIndexedNotNull null
                    compileExtraEntry(idx, track.id, track.localizedName(languageTag), track.artwork, track.main)
                }
                is PlaylistItemRef.ExtraTrack -> {
                    val extraId = ref.extraId
                    if (!isExtraVisible(extraId, unlocked)) {
                        return@mapIndexedNotNull null
                    }
                    val other = extraCatalog.otherMusic.firstOrNull { it.id == extraId }
                    if (other != null) {
                        val title = other.localizedName(languageTag)
                        when (other) {
                            is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle ->
                                compileExtraEntry(idx, other.id, title, other.artwork, other.main)
                            is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.LinearAsset -> {
                                if (!java.io.File(dir, "audio/${other.asset.sha256}").isFile) return@mapIndexedNotNull null
                                LinearQueue.Entry(
                                    key = "ref:$idx:extra:${other.id}:${java.util.UUID.randomUUID()}",
                                    title = title,
                                    subtitle = queueSubtitle,
                                    sourceType = LinearSessionStore.SourceType.EXTRA,
                                    durationMs = other.durationMs,
                                    artworkName = other.artwork,
                                    assetSha256 = other.asset.sha256,
                                    loop = other.asset.loop,
                                    loopStart = other.asset.loopStart,
                                    loopEnd = other.asset.loopEnd,
                                )
                            }
                            is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.OverlaidOpening -> {
                                if (!java.io.File(dir, "audio/${other.op.sha256}").isFile ||
                                    !java.io.File(dir, "audio/${other.telop.sha256}").isFile
                                ) return@mapIndexedNotNull null
                                LinearQueue.Entry(
                                    key = "ref:$idx:extra:${other.id}:${java.util.UUID.randomUUID()}",
                                    title = title,
                                    subtitle = queueSubtitle,
                                    sourceType = LinearSessionStore.SourceType.EXTRA,
                                    durationMs = other.durationMs,
                                    artworkName = other.artwork,
                                    overlaidOpening = other,
                                )
                            }
                            is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> {
                                if (other.parts.any { !java.io.File(dir, "audio/${it.asset.sha256}").isFile }) {
                                    return@mapIndexedNotNull null
                                }
                                LinearQueue.Entry(
                                    key = "ref:$idx:extra:${other.id}:${java.util.UUID.randomUUID()}",
                                    title = title,
                                    subtitle = queueSubtitle,
                                    sourceType = LinearSessionStore.SourceType.EXTRA,
                                    durationMs = other.durationMs,
                                    artworkName = other.artwork,
                                    chainedSequence = other,
                                )
                            }
                        }
                    } else {
                        val simple = extraCatalog.shortSounds.firstOrNull { it.id == extraId }
                            ?: extraCatalog.nintendoMusicSpecial.firstOrNull { it.id == extraId }
                            ?: return@mapIndexedNotNull null
                        if (!java.io.File(dir, "audio/${simple.asset.sha256}").isFile) return@mapIndexedNotNull null
                        LinearQueue.Entry(
                            key = "ref:$idx:extra:${simple.id}:${java.util.UUID.randomUUID()}",
                            title = simple.localizedName(languageTag),
                            subtitle = queueSubtitle,
                            sourceType = LinearSessionStore.SourceType.EXTRA,
                            durationMs = simple.durationMs,
                            artworkName = simple.artwork,
                            assetSha256 = simple.asset.sha256,
                            loop = simple.asset.loop,
                            loopStart = simple.asset.loopStart,
                            loopEnd = simple.asset.loopEnd,
                        )
                    }
                }
            }
        }
    }

    fun playNextRefs(
        rawRefs: List<String>,
        presets: List<CoursePreset>,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        queueSubtitle: String,
        nativeTransitionPrefix: String,
    ): Boolean {
        if (isCeremonyLocked || !_ui.value.allowPlayNext) return false
        val dir = pack?.directory ?: return false
        val entries = resolveEntriesForRefs(rawRefs, presets, languageTag, suiteLabel, rallyLabel, queueSubtitle, nativeTransitionPrefix)
        if (entries.isEmpty()) return false
        if (_ui.value.queue.current == null || !_ui.value.linearMode) {
            playExtraLinearQueue(entries, 0, LinearQueue.RepeatMode.LIST, false)
        } else {
            linearCoordinator.enqueueNextBatch(entries, dir)
        }
        return true
    }

    fun addToQueueRefs(
        rawRefs: List<String>,
        presets: List<CoursePreset>,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        queueSubtitle: String,
        nativeTransitionPrefix: String,
    ): Boolean {
        if (isCeremonyLocked || !_ui.value.allowPlayNext) return false
        val dir = pack?.directory ?: return false
        val entries = resolveEntriesForRefs(rawRefs, presets, languageTag, suiteLabel, rallyLabel, queueSubtitle, nativeTransitionPrefix)
        if (entries.isEmpty()) return false
        if (_ui.value.queue.current == null || !_ui.value.linearMode) {
            playExtraLinearQueue(entries, 0, LinearQueue.RepeatMode.LIST, false)
        } else {
            linearCoordinator.enqueueEndBatch(entries, dir)
        }
        return true
    }

    fun playSelectedRefs(
        rawRefs: List<String>,
        presets: List<CoursePreset>,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        queueSubtitle: String,
        nativeTransitionPrefix: String,
        shuffle: Boolean = false,
        startIndex: Int = 0,
        contextId: String? = null,
        contextTitle: String? = null,
    ): Boolean {
        if (isCeremonyLocked) return false
        val entries = resolveEntriesForRefs(rawRefs, presets, languageTag, suiteLabel, rallyLabel, queueSubtitle, nativeTransitionPrefix)
        if (entries.isEmpty()) return false
        val clampedStart = startIndex.coerceIn(0, entries.lastIndex)
        val context = if (contextId != null) {
            LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.PLAYLIST,
                contextId = contextId,
                contextTitle = contextTitle,
                packFingerprints = currentPackFingerprints(),
            )
        } else {
            LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.EXTRA,
                packFingerprints = currentPackFingerprints(),
            )
        }
        playExtraLinearQueue(entries, clampedStart, LinearQueue.RepeatMode.LIST, shuffle, context)
        requestNotificationPermissionIfNeeded()
        return true
    }

    sealed interface RoutePlayResult {
        data object Started : RoutePlayResult
        data class Blocked(val reason: String) : RoutePlayResult
    }

    fun allInstalledJukeboxItems(): List<Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>> =
        jukebox.allItems

    internal fun isPresetSelectableForCourse(
        courseCode: String,
        presetRef: String?,
        userPresets: List<CoursePreset>,
    ): Boolean {
        val logical = courseCode.removeSuffix("_00")
        if (isSpecialStageLocked(logical)) return false
        val catalog = courseMedleyCatalog
        val medleys = catalog.forCourse(logical)
        val dir = pack?.directory
        fun isBuiltInVariantSelectable(targetVariant: String): Boolean {
            val target = medleys.firstOrNull { it.normalizedSuiteVariant == targetVariant } ?: return false
            return dir == null || com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.resolveMedley(
                medley = target,
                sampleRate = catalog.sampleRate,
                identities = interactiveIdentities,
                packDir = dir,
                extraCatalog = extraCatalog,
            ) != null
        }
        return when (presetRef) {
            null, PresetRefs.SYSTEM_DEFAULT ->
                isBuiltInVariantSelectable("a") || medleys.firstOrNull { !it.isKnockoutVariant }?.let { target ->
                    dir == null || com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.resolveMedley(
                        medley = target,
                        sampleRate = catalog.sampleRate,
                        identities = interactiveIdentities,
                        packDir = dir,
                        extraCatalog = extraCatalog,
                    ) != null
                } == true
            PresetRefs.SYSTEM_SUITE_A_ALT -> isBuiltInVariantSelectable("a_alt")
            PresetRefs.SYSTEM_SUITE_B -> isBuiltInVariantSelectable("b")
            PresetRefs.SYSTEM_SUITE_C -> isBuiltInVariantSelectable("c")
            PresetRefs.SYSTEM_SUITE_D -> isBuiltInVariantSelectable("d")
            PresetRefs.SYSTEM_KNOCKOUT -> isBuiltInVariantSelectable("knockout")
            else -> {
                val preset = userPresets.firstOrNull {
                    it.id == presetRef && it.courseCode.equals(logical, ignoreCase = true)
                } ?: return false
                if (dir == null) return true
                val resolver = editorSourceResolver(dir)
                val inventory = CourseFlowRecipeBuilder.inspectCourse(logical, minfCatalog, catalog, resolver)
                CourseFlowRecipeBuilder.compileUserPreset(preset, inventory, minfCatalog, resolver) != null
            }
        }
    }

    private var pureFreeRoamDurationJob: Job? = null

    private fun cancelPureFreeRoamDurationJob() {
        pureFreeRoamDurationJob?.cancel()
        pureFreeRoamDurationJob = null
    }

    fun inspectOfficialRoute(
        route: com.mylittleblover.mkwplayer.routes.OfficialRoute,
        sessionOverrides: Map<Int, String> = emptyMap(),
        ruleMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
        pureFreeRoamEnabled: Boolean = false,
        pureFreeRoamTrackPool: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamTrackPool =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamTrackPool.ROUTE_RULES,
    ): com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteValidationReport {
        val dir = pack?.directory
        val items = jukebox.games.flatMap { it.items }
        return com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.inspectOfficialRoute(
            route = route,
            connections = stageConnections,
            pool = roadJukeboxPool,
            installedJukebox = items,
            sessionOverrides = sessionOverrides,
            isStagePlayable = ::canStartStage,
            isStageHidden = ::isSpecialStageLocked,
            isJukeboxReadable = { item ->
                val resolvedDir = jukebox.packByKey[item.track.key] ?: dir
                resolvedDir != null && java.io.File(resolvedDir, "audio/${item.track.primary.sha256}").isFile
            },
            wikiCatalog = routeMusicCatalog,
            ruleMode = ruleMode,
            pureFreeRoamEnabled = pureFreeRoamEnabled,
            pureFreeRoamTrackPool = pureFreeRoamTrackPool,
        )
    }

    fun inspectCustomRoute(
        route: com.mylittleblover.mkwplayer.routes.CustomRoute,
        ruleModeOverride: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode? = null,
    ): com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteValidationReport {
        val dir = pack?.directory
        val items = jukebox.games.flatMap { it.items }
        val effectiveRoute = ruleModeOverride?.let { route.copy(roadRuleMode = it) } ?: route
        return com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.inspectCustomRoute(
            route = effectiveRoute,
            connections = stageConnections,
            pool = roadJukeboxPool,
            installedJukebox = items,
            isStagePlayable = ::canStartStage,
            isStageHidden = ::isSpecialStageLocked,
            isJukeboxReadable = { item ->
                val resolvedDir = jukebox.packByKey[item.track.key] ?: dir
                resolvedDir != null && java.io.File(resolvedDir, "audio/${item.track.primary.sha256}").isFile
            },
            wikiCatalog = routeMusicCatalog,
            pureFreeRoamEnabled = effectiveRoute.pureFreeRoamConfig.enabled,
        )
    }

    private fun startPureFreeRoamRoutePlayback(
        routeId: String,
        contextKind: LinearSessionStore.ContextKind,
        contextId: String,
        routeTitle: String,
        legPlans: List<com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamLegPlan>,
        config: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamConfig,
        loopRoute: Boolean,
        languageTag: String,
        roadPrefix: String,
    ): RoutePlayResult {
        cancelPureFreeRoamDurationJob()
        val dir = pack?.directory
            ?: return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.route_missing_medley_audio))
        if (legPlans.isEmpty()) {
            return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.route_cannot_play))
        }
        val gamesById = jukebox.games.associateBy { it.id }
        val readableItems = jukebox.allItems.filter { (item, _) ->
            val resolvedDir = jukebox.packByKey[item.track.key] ?: dir
            java.io.File(resolvedDir, "audio/${item.track.primary.sha256}").isFile
        }
        if (readableItems.isEmpty()) {
            return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.route_missing_medley_audio))
        }
        val byCode = HashMap<String, Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>>(readableItems.size * 2)
        val exactByCode = LinkedHashMap<String, Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>>(readableItems.size * 2)
        for (pair in readableItems) {
            val rawCode = pair.first.jukeboxCode?.trim().orEmpty()
            if (rawCode.isNotEmpty()) {
                if (!exactByCode.containsKey(rawCode)) {
                    exactByCode[rawCode] = pair
                }
                val upperCode = rawCode.uppercase(java.util.Locale.ROOT)
                if (!byCode.containsKey(upperCode)) {
                    byCode[upperCode] = pair
                }
            }
        }

        data class ResolvedLeg(
            val plan: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamLegPlan,
            val label: String,
            val candidates: List<Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>>,
            val weights: List<Int>,
            val byCodeMap: Map<String, Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>>,
            val orderedCodes: List<String>,
        )

        val resolvedLegs = legPlans.map { plan ->
            val fromName = localizedNames?.name(plan.from, languageTag) ?: plan.from
            val toName = localizedNames?.name(plan.to, languageTag) ?: plan.to
            val label = "$roadPrefix $fromName → $toName"
            val matched = mutableListOf<Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>>()
            val weights = mutableListOf<Int>()
            val seen = HashSet<String>()
            for (cand in plan.rankedCandidates) {
                val found = byCode[cand.jukeboxCode.trim().uppercase(java.util.Locale.ROOT)] ?: continue
                if (seen.add(found.first.key)) {
                    matched += found
                    weights += cand.weight.coerceAtLeast(1)
                }
            }
            val finalCandidates = if (matched.isNotEmpty()) matched else readableItems
            val finalWeights = if (matched.isNotEmpty()) weights else List(finalCandidates.size) { 1 }
            val legByCode = LinkedHashMap<String, Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>>()
            val codes = mutableListOf<String>()
            for (pair in finalCandidates) {
                val c = pair.first.jukeboxCode?.trim().orEmpty()
                if (c.isNotEmpty() && !legByCode.containsKey(c)) {
                    legByCode[c] = pair
                    codes += c
                }
            }
            ResolvedLeg(plan, label, finalCandidates, finalWeights, legByCode, codes)
        }

        val recentKeys = ArrayDeque<String>()
        var seqCounter = 0L
        val rng = java.util.Random()
        fun pickEntryForLeg(leg: ResolvedLeg): LinearQueue.Entry {
            val engine = freeRoamEngine
            val isGameFreeRoamPool =
                leg.plan.trackPool == com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM
            var chosen: Pair<com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Game>? = null
            if (isGameFreeRoamPool && engine != null && exactByCode.isNotEmpty()) {
                val pickedCode = engine.pickNextFreeRoam(
                    mode = leg.plan.freeRoamMode,
                    availableCodes = exactByCode.keys,
                )
                chosen = pickedCode?.let { exactByCode[it] ?: byCode[it.uppercase(java.util.Locale.ROOT)] }
            } else if (engine != null && leg.byCodeMap.isNotEmpty()) {
                val available = leg.byCodeMap.keys
                val pickedCode = when (leg.plan.effectiveRule) {
                    com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EffectiveRuleMode.VS_DAY ->
                        engine.pickInterstageFirstSong(leg.plan.from, isNight = false, availableCodes = available)
                    com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EffectiveRuleMode.VS_NIGHT ->
                        engine.pickInterstageFirstSong(leg.plan.from, isNight = true, availableCodes = available)
                    com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EffectiveRuleMode.GRAND_PRIX ->
                        engine.pickGrandPrixEdgeSong(leg.orderedCodes)
                    else -> null
                }
                chosen = pickedCode?.let { leg.byCodeMap[it] }
            }
            if (chosen == null || (!isGameFreeRoamPool && chosen.first.key in recentKeys && leg.candidates.size > 1)) {
                val eligibleIndices = leg.candidates.indices.filter { leg.candidates[it].first.key !in recentKeys }
                    .ifEmpty { leg.candidates.indices.toList() }
                val totalW = eligibleIndices.sumOf { leg.weights.getOrElse(it) { 1 } }.coerceAtLeast(1)
                var roll = rng.nextInt(totalW)
                var pickedIdx = eligibleIndices.first()
                for (i in eligibleIndices) {
                    roll -= leg.weights.getOrElse(i) { 1 }
                    if (roll < 0) {
                        pickedIdx = i
                        break
                    }
                }
                chosen = leg.candidates[pickedIdx]
                chosen.first.jukeboxCode?.let { engine?.recordPlayed(it) }
            }
            val (item, game) = chosen
            recentKeys.addLast(item.key)
            while (recentKeys.size > 6) recentKeys.removeFirst()
            val gameName = gamesById[item.gameId]?.localizedName(languageTag) ?: game.localizedName(languageTag)
            seqCounter++
            return LinearQueue.Entry(
                key = "$routeId:pfr:${leg.plan.edgeIndex}:$seqCounter:${item.key}",
                title = item.displayTitle(languageTag),
                subtitle = leg.label,
                sourceType = LinearSessionStore.SourceType.JUKEBOX,
                gameId = item.gameId,
                gameTitle = gameName,
                yearLabel = game.yearLabel.takeIf { it.isNotBlank() },
                arrangement = item.displayArrangement(languageTag),
                ostIndex = item.ostIndex,
                durationMs = item.durationMs,
                artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
                assetSha256 = item.track.primary.sha256,
                sourceTrackKey = item.track.key,
            )
        }

        val cleanConfig = config.normalized()
        val gapMs = if (cleanConfig.trackPool == com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM) {
            freeRoamEngine?.data?.settings?.freeRunIntervalMs ?: 1_500L
        } else {
            0L
        }
        if (cleanConfig.controlMode == com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT) {
            val entries = mutableListOf<LinearQueue.Entry>()
            for (leg in resolvedLegs) {
                repeat(leg.plan.trackCount.coerceAtLeast(1)) {
                    entries += pickEntryForLeg(leg)
                }
            }
            if (entries.isEmpty()) {
                return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.route_missing_medley_audio))
            }
            playExtraLinearQueue(
                entries = entries,
                startIndex = 0,
                repeatMode = if (loopRoute) LinearQueue.RepeatMode.LIST else LinearQueue.RepeatMode.OFF,
                shuffle = false,
                context = LinearPlaybackCoordinator.SessionContext(
                    contextKind = contextKind,
                    contextId = contextId,
                    contextTitle = routeTitle,
                    nativeTransitionDefault = false,
                    packFingerprints = currentPackFingerprints(),
                    allowPlayNext = false,
                    allowShuffle = false,
                    allowSingleRepeat = false,
                    allowRepeatToggle = true,
                    interTrackGapMs = gapMs,
                ),
            )
            requestNotificationPermissionIfNeeded()
            return RoutePlayResult.Started
        }

        // DURATION mode: dynamically supply Free Roam tracks for the active leg and switch legs
        // automatically once the leg's configured playback duration has elapsed (#5).
        var activeLegIdx = 0
        val initialEntry = pickEntryForLeg(resolvedLegs[0])
        val supplier: () -> LinearQueue.Entry? = {
            val leg = resolvedLegs.getOrNull(activeLegIdx)
            if (leg != null) pickEntryForLeg(leg) else null
        }
        prepareForLinearPlayback()
        linearCoordinator.startExtraQueue(
            entries = listOf(initialEntry),
            startIndex = 0,
            packDir = dir,
            repeatMode = if (loopRoute) LinearQueue.RepeatMode.LIST else LinearQueue.RepeatMode.OFF,
            shuffleOverride = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = contextKind,
                contextId = contextId,
                contextTitle = routeTitle,
                nativeTransitionDefault = false,
                packFingerprints = currentPackFingerprints(),
                allowPlayNext = true,
                allowShuffle = false,
                allowSingleRepeat = false,
                allowRepeatToggle = true,
                interTrackGapMs = gapMs,
                dynamicNextSupplier = supplier,
                dynamicTargetBuffer = 2,
            ),
        )
        pureFreeRoamDurationJob = viewModelScope.launch {
            var legElapsedMs = 0L
            var lastWallMs = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                delay(500L)
                val nowWall = android.os.SystemClock.elapsedRealtime()
                val delta = (nowWall - lastWallMs).coerceIn(0L, 2_000L)
                lastWallMs = nowWall
                val uiNow = _ui.value
                if (!uiNow.linearMode || uiNow.linearContextId != contextId) break
                if (!session.state.value.playing) continue
                legElapsedMs += delta
                val currentLeg = resolvedLegs.getOrNull(activeLegIdx) ?: break
                if (legElapsedMs >= currentLeg.plan.durationMs) {
                    legElapsedMs = 0L
                    val nextLegIdx = activeLegIdx + 1
                    if (nextLegIdx < resolvedLegs.size) {
                        activeLegIdx = nextLegIdx
                        val nextEntry = pickEntryForLeg(resolvedLegs[activeLegIdx])
                        linearCoordinator.enqueueNext(nextEntry)
                        linearCoordinator.skipNext()
                    } else if (loopRoute) {
                        activeLegIdx = 0
                        val nextEntry = pickEntryForLeg(resolvedLegs[0])
                        linearCoordinator.enqueueNext(nextEntry)
                        linearCoordinator.skipNext()
                    } else {
                        // Finished final leg duration: stop cleanly after reaching configured time.
                        fadeOutLinearAndStop()
                        break
                    }
                }
            }
        }
        requestNotificationPermissionIfNeeded()
        return RoutePlayResult.Started
    }

    fun playOfficialRoute(
        route: com.mylittleblover.mkwplayer.routes.OfficialRoute,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
        loopRoute: Boolean,
        sessionOverrides: Map<Int, String> = emptyMap(),
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        roadPrefix: String,
        ruleMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
        suiteMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteSuiteMode =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
        pureFreeRoamConfig: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamConfig =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.PureFreeRoamConfig(),
        pureFreeRoamEdgeOverrides: Map<Int, com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeFreeRoamOverride> = emptyMap(),
    ): RoutePlayResult {
        if (isCeremonyLocked) {
            return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.player_blocked_ceremony))
        }
        if (route.requiresSpecialUnlock && !com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)) {
            return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.player_blocked_special_locked))
        }
        if (nativePerformance?.isFading == true) {
            return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.player_blocked_fading))
        }
        val routeTitle = route.displayName(languageTag)
        if (pureFreeRoamConfig.enabled) {
            val legPlans = com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.buildPureFreeRoamLegPlans(
                route = route,
                config = pureFreeRoamConfig,
                ruleMode = ruleMode,
                edgeOverrides = pureFreeRoamEdgeOverrides,
                wikiCatalog = routeMusicCatalog,
            )
            return startPureFreeRoamRoutePlayback(
                routeId = "official:${route.id}",
                contextKind = LinearSessionStore.ContextKind.OFFICIAL_ROUTE,
                contextId = route.id,
                routeTitle = routeTitle,
                legPlans = legPlans,
                config = pureFreeRoamConfig,
                loopRoute = loopRoute,
                languageTag = languageTag,
                roadPrefix = roadPrefix,
            )
        }
        val report = inspectOfficialRoute(route, sessionOverrides, ruleMode, pureFreeRoamEnabled = false)
        if (!report.canPlay) {
            val reason = localizedBlockingReason(report, com.mylittleblover.mkwplayer.R.string.route_cannot_play, languageTag)
            _ui.value = _ui.value.copy(detail = reason)
            return RoutePlayResult.Blocked(reason)
        }
        val unlocked = com.mylittleblover.mkwplayer.ui.CupVisibility.mayReveal(_ui.value.unlock)
        val openingId = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.openingIdForOfficialRoute(route.id, route.kind)
        val openingItem = if (openingId != null && isExtraVisible(openingId, unlocked)) {
            extraCatalog.otherMusic
                .filterIsInstance<com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.OverlaidOpening>()
                .firstOrNull { it.id == openingId }
        } else {
            null
        }
        val stops = route.stops.map {
            com.mylittleblover.mkwplayer.routes.CustomRouteStop(it.stageCode, null)
        }
        val entries = buildRouteQueueEntries(
            routeId = "official:${route.id}",
            stops = stops,
            report = report,
            userPresets = userPresets,
            languageTag = languageTag,
            suiteLabel = suiteLabel,
            rallyLabel = rallyLabel,
            roadPrefix = roadPrefix,
            customSubstituteTag = "",
            opening = openingItem,
            routeTitle = routeTitle,
            officialKind = route.kind,
            suiteMode = suiteMode,
        ) ?: return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.route_missing_medley_audio)).also {
            _ui.value = _ui.value.copy(detail = it.reason)
        }
        cancelPureFreeRoamDurationJob()
        playExtraLinearQueue(
            entries = entries,
            startIndex = 0,
            repeatMode = if (loopRoute) LinearQueue.RepeatMode.LIST else LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.OFFICIAL_ROUTE,
                contextId = route.id,
                contextTitle = routeTitle,
                nativeTransitionDefault = false,
                packFingerprints = currentPackFingerprints(),
                allowPlayNext = false,
                allowShuffle = false,
                allowSingleRepeat = false,
                allowRepeatToggle = true,
            ),
        )
        requestNotificationPermissionIfNeeded()
        return RoutePlayResult.Started
    }

    fun playCustomRoute(
        route: com.mylittleblover.mkwplayer.routes.CustomRoute,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        roadPrefix: String,
        customSubstituteTag: String,
    ): RoutePlayResult {
        if (isCeremonyLocked) {
            return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.player_blocked_ceremony))
        }
        if (nativePerformance?.isFading == true) {
            return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.player_blocked_fading))
        }
        if (route.pureFreeRoamConfig.enabled) {
            val legPlans = com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.buildPureFreeRoamLegPlans(
                route = route,
                wikiCatalog = routeMusicCatalog,
            )
            return startPureFreeRoamRoutePlayback(
                routeId = "custom:${route.id}",
                contextKind = LinearSessionStore.ContextKind.CUSTOM_ROUTE,
                contextId = route.id,
                routeTitle = route.name,
                legPlans = legPlans,
                config = route.pureFreeRoamConfig,
                loopRoute = route.loopWholeRoute,
                languageTag = languageTag,
                roadPrefix = roadPrefix,
            )
        }
        val report = inspectCustomRoute(route)
        if (!report.canPlay) {
            val reason = localizedBlockingReason(report, com.mylittleblover.mkwplayer.R.string.route_custom_cannot_play, languageTag)
            _ui.value = _ui.value.copy(detail = reason)
            return RoutePlayResult.Blocked(reason)
        }
        val entries = buildRouteQueueEntries(
            routeId = "custom:${route.id}",
            stops = route.stops,
            report = report,
            userPresets = userPresets,
            languageTag = languageTag,
            suiteLabel = suiteLabel,
            rallyLabel = rallyLabel,
            roadPrefix = roadPrefix,
            customSubstituteTag = customSubstituteTag,
            officialKind = null,
            suiteMode = route.routeSuiteMode,
            roadRuleMode = route.roadRuleMode,
        ) ?: return RoutePlayResult.Blocked(str(com.mylittleblover.mkwplayer.R.string.route_missing_medley_audio)).also {
            _ui.value = _ui.value.copy(detail = it.reason)
        }
        cancelPureFreeRoamDurationJob()
        playExtraLinearQueue(
            entries = entries,
            startIndex = 0,
            repeatMode = if (route.loopWholeRoute) LinearQueue.RepeatMode.LIST else LinearQueue.RepeatMode.OFF,
            shuffle = false,
            context = LinearPlaybackCoordinator.SessionContext(
                contextKind = LinearSessionStore.ContextKind.CUSTOM_ROUTE,
                contextId = route.id,
                contextTitle = route.name,
                nativeTransitionDefault = false,
                packFingerprints = currentPackFingerprints(),
                allowPlayNext = false,
                allowShuffle = false,
                allowSingleRepeat = false,
                allowRepeatToggle = true,
            ),
        )
        requestNotificationPermissionIfNeeded()
        return RoutePlayResult.Started
    }

    private fun buildRouteQueueEntries(
        routeId: String,
        stops: List<com.mylittleblover.mkwplayer.routes.CustomRouteStop>,
        report: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteValidationReport,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
        languageTag: String,
        suiteLabel: String,
        rallyLabel: String,
        roadPrefix: String,
        customSubstituteTag: String,
        opening: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.OverlaidOpening? = null,
        routeTitle: String? = null,
        officialKind: com.mylittleblover.mkwplayer.routes.OfficialRouteKind? = null,
        suiteMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteSuiteMode =
            com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
        roadRuleMode: com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode? = null,
    ): List<LinearQueue.Entry>? {
        val dir = pack?.directory ?: return null
        val resolver = editorSourceResolver(dir)
        val gamesById = jukebox.games.associateBy { it.id }
        val entries = mutableListOf<LinearQueue.Entry>()

        if (opening != null) {
            val opFile = java.io.File(dir, "audio/${opening.op.sha256}")
            val telopFile = java.io.File(dir, "audio/${opening.telop.sha256}")
            if (opFile.isFile && telopFile.isFile) {
                entries += LinearQueue.Entry(
                    key = "$routeId:opening:${opening.id}",
                    title = opening.localizedName(languageTag),
                    subtitle = routeTitle.orEmpty(),
                    sourceType = LinearSessionStore.SourceType.EXTRA,
                    ostIndex = opening.ostIndex,
                    durationMs = opening.durationMs,
                    artworkName = opening.artwork,
                    overlaidOpening = opening,
                )
            }
        }

        for (i in stops.indices) {
            val stop = stops[i]
            val logical = stop.stageCode.removeSuffix("_00")
            val presetRef = effectiveStopPresetRef(
                stageCode = logical,
                explicitPresetId = stop.presetId,
                stopIndex = i,
                stopCount = stops.size,
                officialKind = officialKind,
                suiteMode = suiteMode,
                userPresets = userPresets,
                roadRuleMode = roadRuleMode,
            )
            val resolved = CourseFlowRecipeBuilder.resolvePresetRef(
                courseCode = logical,
                presetRef = presetRef,
                userPresets = userPresets,
                minf = minfCatalog,
                medleyCatalog = courseMedleyCatalog,
                resolver = resolver,
                segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
            ) ?: return null
            val (effectiveRef, presetDisplayName) = resolveQueuePresetDisplay(
                courseCode = logical,
                requestedPresetRef = presetRef,
                resolvedMedley = resolved.medley,
                userPresets = userPresets,
                fallbackSuiteLabel = suiteLabel,
            )
            val matchingUserPreset = if (PresetRefs.isUser(effectiveRef)) {
                userPresets.firstOrNull { it.id == effectiveRef && it.courseCode.equals(logical, ignoreCase = true) }
            } else {
                null
            }
            val displayCourse = localizedNames?.name(logical, languageTag) ?: logical
            val outgoingDirect = report.legs.getOrNull(i)?.resolution is
                com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect
            val incomingDirect = i > 0 && report.legs.getOrNull(i - 1)?.resolution is
                com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect
            val handoverOut = outgoingDirect && resolved.outroHandoverFrame != null
            val enableHandover = handoverOut || incomingDirect
            entries += LinearQueue.Entry(
                key = "$routeId:stop:$i:$logical",
                title = displayCourse,
                subtitle = presetDisplayName,
                sourceType = if (matchingUserPreset != null) {
                    LinearSessionStore.SourceType.PRESET
                } else {
                    LinearSessionStore.SourceType.MEDLEY
                },
                courseCode = logical,
                presetId = effectiveRef,
                medleyId = resolved.medley.id.takeIf { matchingUserPreset == null },
                nativeTransitionEnabled = enableHandover,
                nativeTransitionSourceLabel = if (handoverOut) str(com.mylittleblover.mkwplayer.R.string.route_handover_outro_next_pre) else null,
                arrangement = presetDisplayName,
                durationMs = resolved.medley.durationMs,
                artworkName = extraCatalog.courseArtwork[logical],
                courseMedley = resolved,
            )

            val leg = report.legs.getOrNull(i) ?: continue
            val fromName = localizedNames?.name(leg.from, languageTag) ?: leg.from
            val toName = localizedNames?.name(leg.to, languageTag) ?: leg.to
            val legLabel = "$roadPrefix $fromName → $toName"

            fun roadEntry(item: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.TrackItem, subLabel: String): LinearQueue.Entry {
                val game = gamesById[item.gameId]
                val gameName = game?.localizedName(languageTag) ?: item.gameId
                return LinearQueue.Entry(
                    key = "$routeId:edge:$i:${item.key}",
                    title = item.displayTitle(languageTag),
                    subtitle = subLabel,
                    sourceType = LinearSessionStore.SourceType.JUKEBOX,
                    gameId = item.gameId,
                    gameTitle = gameName,
                    yearLabel = game?.yearLabel?.takeIf { it.isNotBlank() },
                    arrangement = item.displayArrangement(languageTag),
                    ostIndex = item.ostIndex,
                    durationMs = item.durationMs,
                    artworkName = item.ostIndex?.let { "t_%03d.webp".format(java.util.Locale.ROOT, it) },
                    assetSha256 = item.track.primary.sha256,
                    sourceTrackKey = item.track.key,
                )
            }

            when (val res = leg.resolution) {
                is com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete -> {
                    val override = res.selectedOverride
                    if (override != null) {
                        override.jukeboxCode?.let { freeRoamEngine?.recordPlayed(it) }
                        entries += roadEntry(override, legLabel)
                    } else if (res.candidates.isNotEmpty()) {
                        val variants = res.candidates.map { roadEntry(it, legLabel) }
                        val weights = if (res.candidateWeights.size == variants.size) {
                            res.candidateWeights
                        } else {
                            emptyList()
                        }
                        val engine = freeRoamEngine
                        val variantByCode = LinkedHashMap<String, LinearQueue.Entry>()
                        res.candidates.forEachIndexed { candIdx, item ->
                            val code = item.jukeboxCode?.trim().orEmpty()
                            if (code.isNotEmpty() && code !in variantByCode) {
                                variantByCode[code] = variants[candIdx]
                            }
                        }
                        val enginePicker: (() -> LinearQueue.Entry?)? = if (engine != null && variantByCode.isNotEmpty()) {
                            val fromStage = leg.from
                            val ruleUsed = res.ruleModeUsed
                            val available = variantByCode.keys
                            val orderedCodes = res.candidates.mapNotNull { it.jukeboxCode?.trim()?.takeIf { c -> c.isNotEmpty() } }
                            when (ruleUsed) {
                                com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EffectiveRuleMode.VS_DAY -> ({
                                    engine.pickInterstageFirstSong(fromStage = fromStage, isNight = false, availableCodes = available)
                                        ?.let { variantByCode[it] }
                                })
                                com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EffectiveRuleMode.VS_NIGHT -> ({
                                    engine.pickInterstageFirstSong(fromStage = fromStage, isNight = true, availableCodes = available)
                                        ?.let { variantByCode[it] }
                                })
                                com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EffectiveRuleMode.GRAND_PRIX -> ({
                                    engine.pickGrandPrixEdgeSong(orderedCodes)?.let { variantByCode[it] }
                                })
                                else -> null
                            }
                        } else {
                            null
                        }
                        entries += variants.first().copy(
                            roadCandidateVariants = variants,
                            roadCandidateWeights = weights,
                            roadCandidatePicker = enginePicker,
                        )
                    }
                }
                is com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.EdgeMusicResolution.CustomSubstitute -> {
                    val subLabel = if (customSubstituteTag.isNotBlank()) "$legLabel · $customSubstituteTag" else legLabel
                    res.track.jukeboxCode?.let { freeRoamEngine?.recordPlayed(it) }
                    entries += roadEntry(res.track, subLabel)
                }
                else -> Unit // Special approach / Knockout Direct / Missing / Muted -> cleanly handled
            }
        }
        return entries.takeIf { it.isNotEmpty() }
    }

    /** Inspects any saved linear session on cold start and exposes "Continue listening" if still valid (#62, #89). */
    fun refreshContinueListening() {
        val currentState = _ui.value
        if (linearCoordinator.state.value.active || session.state.value.active) {
            if (currentState.continueListening != null) {
                _ui.value = currentState.copy(continueListening = null)
            }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val saved = linearSessionStore.read().getOrNull() ?: return@withContext null
                val presetFile = java.io.File(getApplication<Application>().filesDir, "course-presets.json")
                val userPresets = CoursePresetStore(presetFile).read().getOrDefault(emptyList())
                if (!verifySavedSessionAvailable(saved, pack?.directory, userPresets)) {
                    linearSessionStore.clear()
                    return@withContext null
                }
                val rateHz = saved.sampleRate.coerceAtLeast(1)
                val progress = if (saved.totalFrames > 0L) {
                    (saved.positionFrames.toDouble() / saved.totalFrames).coerceIn(0.0, 1.0).toFloat()
                } else {
                    0f
                }
                ContinueListeningUi(
                    key = saved.current.key,
                    title = saved.current.title,
                    subtitle = saved.contextTitle?.takeIf { it.isNotBlank() } ?: saved.current.subtitle,
                    artworkName = saved.current.artworkName,
                    positionText = EngineSnapshot.formatPosition(saved.positionFrames, rateHz),
                    totalText = EngineSnapshot.formatPosition(saved.totalFrames, rateHz),
                    progress = progress,
                    positionFrames = saved.positionFrames,
                    totalFrames = saved.totalFrames,
                    sampleRate = rateHz,
                    contextKind = saved.contextKind,
                    contextId = saved.contextId,
                    contextTitle = saved.contextTitle,
                )
            }
            if (!linearCoordinator.state.value.active && !session.state.value.active) {
                _ui.value = _ui.value.copy(continueListening = result)
            }
        }
    }

    fun dismissContinueListening() {
        linearCoordinator.clearSavedSession()
        _ui.value = _ui.value.copy(continueListening = null)
    }

    /** Restores a paused linear queue snapshot from disk and resumes at the exact frame (#62, #89). */
    fun resumeContinueListening(onReady: () -> Unit = {}) {
        if (isCeremonyLocked || nativePerformance?.isFading == true) return
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                val saved = linearSessionStore.read().getOrNull() ?: return@withContext null
                val presetFile = java.io.File(getApplication<Application>().filesDir, "course-presets.json")
                val userPresets = CoursePresetStore(presetFile).read().getOrDefault(emptyList())
                val dir = pack?.directory
                val juke = jukebox
                val resolver = dir?.let { editorSourceResolver(it) }
                fun toEntry(se: LinearSessionStore.SavedEntry): LinearQueue.Entry? =
                    resolveSavedEntry(se, dir, juke, userPresets, resolver, saved.nativeTransitionDefault)

                val frozen = saved.frozenBase.mapNotNull(::toEntry)
                val current = toEntry(saved.current) ?: return@withContext null
                if (frozen.isEmpty() && saved.frozenBase.isNotEmpty()) return@withContext null
                val past = saved.pastHistory.mapNotNull { item -> toEntry(item.entry)?.let { it to item.baseIndex } }
                val forward = saved.forwardHistory.mapNotNull { item -> toEntry(item.entry)?.let { it to item.baseIndex } }
                val temp = saved.tempQueue.mapNotNull(::toEntry)
                val upcoming = saved.upcomingBase.mapNotNull { item -> toEntry(item.entry)?.let { it to item.baseIndex } }
                val exported = LinearQueue.ExportedState(
                    frozenBase = frozen,
                    current = current,
                    currentBaseIndex = saved.currentBaseIndex,
                    pastHistory = past,
                    forwardHistory = forward,
                    tempQueue = temp,
                    upcomingBase = upcoming,
                    repeatMode = saved.repeatMode,
                    shuffle = saved.shuffle,
                )
                val isFixedRoute = saved.contextKind == LinearSessionStore.ContextKind.OFFICIAL_ROUTE ||
                    saved.contextKind == LinearSessionStore.ContextKind.CUSTOM_ROUTE
                val context = LinearPlaybackCoordinator.SessionContext(
                    contextKind = saved.contextKind,
                    contextId = saved.contextId,
                    contextTitle = saved.contextTitle,
                    nativeTransitionDefault = saved.nativeTransitionDefault,
                    packFingerprints = currentPackFingerprints(),
                    allowPlayNext = !isFixedRoute,
                    allowShuffle = !isFixedRoute,
                    allowSingleRepeat = !isFixedRoute,
                )
                Triple(exported, context, saved.positionFrames)
            }
            if (isCeremonyLocked) return@launch
            if (restored == null) {
                linearCoordinator.clearSavedSession()
                _ui.value = _ui.value.copy(continueListening = null)
                return@launch
            }
            prepareForLinearPlayback()
            val (exported, context, posFrames) = restored
            linearCoordinator.restoreAndPlay(
                state = exported,
                context = context,
                packDir = pack?.directory,
                resumePositionFrames = posFrames,
            )
            startPolling()
            onReady()
        }
    }

    private fun customRoutesFile(): java.io.File =
        java.io.File(getApplication<Application>().filesDir, "routes/custom_routes.json")

    private fun buildInvalidationEnvironment(
        manifest: com.mylittleblover.mkwplayer.pack.PackManifest?,
        directory: java.io.File?,
        jukeboxCatalog: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog,
        customRoutes: List<com.mylittleblover.mkwplayer.routes.CustomRoute>,
    ): LibraryInvalidation.Environment = LibraryInvalidation.Environment(
        mergedManifest = manifest,
        mergedDirectory = directory,
        jukeboxCatalog = jukeboxCatalog,
        roadPool = roadJukeboxPool,
        stageConnections = stageConnections,
        officialRoutes = officialRouteCatalog,
        customRoutes = customRoutes,
        extraCatalog = extraCatalog,
        isStageHidden = ::isSpecialStageLocked,
        routeMusicCatalog = routeMusicCatalog,
    )

    private fun currentPackFingerprints(): List<String> = if (mergedFingerprints.isNotEmpty()) {
        mergedFingerprints
    } else {
        listOfNotNull(pack?.manifest?.let { "${it.packId}:${it.packVersion}" })
    }

    private fun verifySavedSessionAvailable(
        saved: LinearSessionStore.Snapshot,
        packDir: java.io.File?,
        userPresets: List<com.mylittleblover.mkwplayer.library.CoursePreset>,
    ): Boolean {
        val juke = jukebox
        val customRoutes = com.mylittleblover.mkwplayer.routes.CustomRouteStore(customRoutesFile())
            .read().getOrDefault(emptyList())
        val invalidEnv = buildInvalidationEnvironment(
            manifest = pack?.manifest,
            directory = packDir,
            jukeboxCatalog = juke,
            customRoutes = customRoutes,
        )
        if (!LibraryInvalidation.isSavedSessionStillValid(saved, invalidEnv)) return false
        val resolver = packDir?.let { editorSourceResolver(it) }
        val toCheck = buildList {
            add(saved.current)
            addAll(saved.frozenBase)
            addAll(saved.tempQueue)
        }
        return toCheck.all { se ->
            resolveSavedEntry(se, packDir, juke, userPresets, resolver, saved.nativeTransitionDefault) != null
        }
    }

    private fun resolveSavedEntry(
        se: LinearSessionStore.SavedEntry,
        packDir: java.io.File?,
        juke: com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog.Catalog,
        userPresets: List<CoursePreset>,
        resolver: CourseFlowRecipeBuilder.SourceResolver?,
        defaultNative: Boolean,
    ): LinearQueue.Entry? {
        val nativeEnabled = se.nativeTransitionOverride ?: defaultNative
        return when (se.sourceType) {
            LinearSessionStore.SourceType.JUKEBOX -> {
                val lookupKey = se.sourceTrackKey ?: se.key
                val item = juke.tracksByKey[lookupKey] ?: return null
                val sha = se.assetSha256 ?: item.primary.sha256
                val dir = juke.packByKey[lookupKey] ?: packDir ?: return null
                if (!java.io.File(dir, "audio/$sha").isFile) return null
                LinearQueue.Entry(
                    key = se.key,
                    title = se.title,
                    subtitle = se.subtitle,
                    sourceType = se.sourceType,
                    gameId = se.gameId,
                    gameTitle = se.gameTitle,
                    yearLabel = se.yearLabel,
                    arrangement = se.arrangement,
                    ostIndex = se.ostIndex,
                    durationMs = se.durationMs,
                    artworkName = se.artworkName,
                    assetSha256 = sha,
                    sourceTrackKey = lookupKey,
                    loop = se.loop,
                    loopStart = se.loopStart,
                    loopEnd = se.loopEnd,
                )
            }
            LinearSessionStore.SourceType.EXTRA -> {
                val dir = packDir ?: return null
                val extraId = com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.extraIdFromEntryKey(se.key)
                    ?: se.key.removePrefix("extra:")
                if (!isExtraVisible(extraId)
                ) return null
                val extraItem = extraCatalog.otherMusic.firstOrNull { it.id == extraId }
                when (extraItem) {
                    is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.OverlaidOpening -> {
                        if (!java.io.File(dir, "audio/${extraItem.op.sha256}").isFile ||
                            !java.io.File(dir, "audio/${extraItem.telop.sha256}").isFile
                        ) return null
                        LinearQueue.Entry(
                            key = se.key,
                            title = se.title,
                            subtitle = se.subtitle,
                            sourceType = se.sourceType,
                            ostIndex = se.ostIndex,
                            durationMs = se.durationMs,
                            artworkName = se.artworkName,
                            overlaidOpening = extraItem,
                        )
                    }
                    is com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> {
                        if (extraItem.parts.any { !java.io.File(dir, "audio/${it.asset.sha256}").isFile }) return null
                        LinearQueue.Entry(
                            key = se.key,
                            title = se.title,
                            subtitle = se.subtitle,
                            sourceType = se.sourceType,
                            ostIndex = se.ostIndex,
                            durationMs = se.durationMs,
                            artworkName = se.artworkName,
                            chainedSequence = extraItem,
                        )
                    }
                    else -> {
                        val sha = se.assetSha256 ?: return null
                        if (!java.io.File(dir, "audio/$sha").isFile) return null
                        LinearQueue.Entry(
                            key = se.key,
                            title = se.title,
                            subtitle = se.subtitle,
                            sourceType = se.sourceType,
                            ostIndex = se.ostIndex,
                            durationMs = se.durationMs,
                            artworkName = se.artworkName,
                            assetSha256 = sha,
                            loop = se.loop,
                            loopStart = se.loopStart,
                            loopEnd = se.loopEnd,
                        )
                    }
                }
            }
            LinearSessionStore.SourceType.MEDLEY,
            LinearSessionStore.SourceType.PRESET -> {
                val res = resolver ?: return null
                val code = se.courseCode ?: return null
                if (isEditorTargetLocked(code)) return null
                val resolved = if (code.startsWith("extra:")) {
                    val dir = packDir ?: return null
                    val targetId = code.removePrefix("extra:")
                    val extraRes = editorSourceResolver(dir)
                    val presetRef = se.presetId
                    val preset = if (PresetRefs.isUser(presetRef)) {
                        userPresets.firstOrNull { it.id == presetRef && it.courseCode.equals(code, ignoreCase = true) }
                    } else null
                    if (preset != null) {
                        val inv = inspectTargetForEditor(code, dir, extraRes)
                        inv?.let {
                            CourseFlowRecipeBuilder.compileUserPreset(
                                preset = preset,
                                inventory = it,
                                minf = minfCatalog,
                                resolver = extraRes,
                                segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
                            )
                        }
                    } else {
                        val item = se.medleyId?.let { extraMedleyCatalog.byMedleyId(it) }
                            ?: extraMedleyCatalog.forTarget(targetId).firstOrNull()
                        resolveExtraMedleyEntry(item, dir)
                    }
                } else {
                    val presetRef = se.presetId ?: se.medleyId?.let { id ->
                        courseMedleyCatalog.byId(id)?.normalizedSuiteVariant?.let(PresetRefs::forSuiteVariant)
                    } ?: PresetRefs.SYSTEM_DEFAULT
                    CourseFlowRecipeBuilder.resolvePresetRef(
                        courseCode = code,
                        presetRef = presetRef,
                        userPresets = userPresets,
                        minf = minfCatalog,
                        medleyCatalog = courseMedleyCatalog,
                        resolver = packDir?.let(::editorSourceResolver) ?: res,
                        segmentSwitchFadeOutMs = playbackBehavior.segmentSwitchFadeOutMs,
                    )
                } ?: return null
                LinearQueue.Entry(
                    key = se.key,
                    title = se.title,
                    subtitle = se.subtitle,
                    sourceType = se.sourceType,
                    courseCode = code,
                    presetId = se.presetId,
                    medleyId = se.medleyId,
                    nativeTransitionEnabled = nativeEnabled,
                    nativeTransitionOverride = se.nativeTransitionOverride,
                    arrangement = se.arrangement,
                    durationMs = se.durationMs,
                    artworkName = se.artworkName,
                    courseMedley = resolved,
                )
            }
        }
    }

    internal val initialEntryChoices: Map<Int,String>
        get() = interactivePlan?.let { plan -> plan.initialSegments.associateWith { plan.labels[it].orEmpty() } }.orEmpty()
    internal val selectedEntrySegment: Int? get() = interactivePlan?.entry

    fun selectInitialEntry(segment: Int) {
        if (isCeremonyLocked) return
        val plan = interactivePlan ?: return
        if (_ui.value.canStop || nativePerformance?.isFading == true || segment !in plan.initialSegments) return
        packPlaybackRequest++
        publish(session.stop())
        nativePerformance = null
        val entryAssets = plan.entryPreparedAssets[segment]
        val mergedPrepared = if (entryAssets != null) {
            val assetsByPath = plan.spec.assets.associateBy { it.path }
            fun assetPcmBytes(path: String): Long =
                assetsByPath[path]?.let { it.frames * it.channels * 4L } ?: 0L
            val combined = LinkedHashSet<String>(entryAssets)
            var totalBytes = combined.sumOf(::assetPcmBytes)
            val budgetBytes = 128L * 1024L * 1024L
            for (path in plan.preparedAssets) {
                if (path !in combined) {
                    val cost = assetPcmBytes(path)
                    if (totalBytes + cost <= budgetBytes) {
                        combined += path
                        totalBytes += cost
                    }
                }
            }
            combined
        } else {
            plan.preparedAssets
        }
        interactivePlan = plan.copy(
            entry = segment,
            preparedAssets = mergedPrepared,
        )
        _ui.value = _ui.value.copy(flow = _ui.value.flow?.copy(activeSegment=null, performanceStage="", stageKey=null, pendingStageKey=null, performing=false))
    }

    internal val canFadeStop: Boolean
        get() = !isCeremonyLocked && interactivePlan != null && session.state.value.loaded && _ui.value.canStop && nativePerformance?.isFading != true

    fun fadeStopPerformance(seconds: Int) {
        if (isCeremonyLocked) return
        if(seconds !in listOf(1,3,5,10) || !canFadeStop) return
        startPerformance()
        val driver=nativePerformance ?: return
        if(driver.isFading) return
        if(_ui.value.canResume) resume()
        val plan=interactivePlan ?: return
        val error=session.fadeStop(seconds*plan.spec.bpm/60.0)
        if(error==null) { packPlaybackRequest++;driver.markFadeRequested() }
        publishNativePerformance(error,true)
    }

    /** Stops a performance without ending the stage. */
    fun stopPerformance() {
        if (isCeremonyLocked) return
        packPlaybackRequest++
        if(nativePerformance!=null) {
            nativePerformance=null
            publish(session.stop())
            _ui.value=_ui.value.copy(flow=_ui.value.flow?.copy(performing=false,performanceStage="Stopped",stageKey="Stopped",pendingStageKey=null,canAdvance=false,canEnd=false,fastPlaying=false,
                canFast=false,canCancelPending=false,fading=false,fadeRemainingFrames=null,voiceCount=0,sourceFrame=null,availableRegions=emptySet(),availableVersions=emptySet(),selectedRegion=null,selectedVersion=null))
            return
        }
        val driver = performance ?: return
        performance = null
        driver.stop()
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(
                performing = false,
                performanceStage = "Stopped", stageKey = "Stopped", pendingStageKey = null,
                canAdvance = false,
                canEnd = false,
                fastPlaying = false,
            ),
        )
    }

    /**
     * Carries a driver event into the screen state.
     *
     * One place, so every control reports the same way. The blocker is only ever cleared by a successful
     * move: clearing it on every event would erase the reason before the listener could read it.
     */
    private fun apply(event: PerformanceDriver.Event, driver: PerformanceDriver) {
        when (event) {
            is PerformanceDriver.Event.Advanced -> publishPerformance(event.segment, driver)
            is PerformanceDriver.Event.Finished -> {
                val finished = !event.reason.contains("stopped", ignoreCase = true)
                if (finished) { stopTransport(cancelPendingOpen = false); markPerformanceFinished() } else stop()
            }
            is PerformanceDriver.Event.Blocked -> {
                // A refused move does not stop the performance: the prelude keeps looping, which is exactly
                // the state a mistaken press should leave the console in.
                _ui.value = _ui.value.copy(detail = event.reason)
            }
            is PerformanceDriver.Event.Waiting -> Unit
        }
    }

    /** Publishes the performance's position after a successful move. */
    private fun publishPerformance(segment: Int, driver: PerformanceDriver) {
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(
                activeSegment = segment,
                performing = driver.isRunning,
                performanceStage = driver.describe(), stageKey = null, pendingStageKey = null,
                performanceBlocker = null,
                fastPlaying = driver.isFast,
                canAdvance = driver.canAdvance,
                canEnd = driver.canEnd,
            ),
            status = str(com.mylittleblover.mkwplayer.R.string.discovery_playing),
        )
    }

    /** Opens or closes a catalog category in the browser. */
    fun toggleCategory(category: String) {
        _ui.value = _ui.value.copy(
            openCategory = if (_ui.value.openCategory == category) null else category,
        )
    }

    /** Starts the track with the given key, or reports why it cannot start. */
    fun select(key: String) {
        if (isCeremonyLocked || nativePerformance?.isFading == true) return
        packPlaybackRequest++
        val track = _ui.value.tracks.firstOrNull { it.key == key } ?: return
        if (!track.playable) {
            _ui.value = _ui.value.copy(
                selectedKey = key,
                title = track.name,
                status = str(com.mylittleblover.mkwplayer.R.string.player_status_unsupported_codec),
                detail = track.reason,
                canPause = false,
                canResume = false,
                canStop = false,
            )
            return
        }

        val ready = prepared[key]
        if (ready == null) {
            _ui.value = _ui.value.copy(
                selectedKey = key,
                status = str(com.mylittleblover.mkwplayer.R.string.player_status_not_prepared),
                detail = str(com.mylittleblover.mkwplayer.R.string.player_detail_repick_file),
            )
            return
        }

        start(track, ready)
    }

    private fun start(track: Track, ready: Prepared) {
        nativePerformance=null
        interactivePlan=null
        performance=null
        val state = session.load(
            title = ready.name,
            packet = ready.packet,
            audio = ready.bytes,
            assetId = ready.name,
            frames = ready.frames,
        )
        if (state.detail != null) {
            _ui.value = _ui.value.copy(status = str(com.mylittleblover.mkwplayer.R.string.player_err_play_file), detail = state.detail)
            return
        }

        linearCoordinator.yieldToExternalTransport()
        flow = null
        flowCompiled = null
        flowBytes = emptyMap()
        totalFrames = ready.frames
        rate = ready.sampleRate

        _ui.value = _ui.value.copy(
            selectedKey = track.key,
            flow = null,
            title = ready.name,
            status = str(com.mylittleblover.mkwplayer.R.string.discovery_playing),
            total = EngineSnapshot.formatPosition(totalFrames, rate),
            canPause = true,
            canResume = false,
            canStop = true,
            linearMode = false,
            linearTrack = null,
            canSeek = true,
            totalFrames = totalFrames,
            sampleRate = rate,
            outputActive = session.hasOutput,
            detail = if (session.hasOutput) {
                null
            } else {
                str(com.mylittleblover.mkwplayer.R.string.player_detail_no_output)
            },
        )

        // Start the foreground service so playback survives the activity, and on
        // Android 13+ ask for the notification the service needs.
        // Only starts when the session is actually playing; see PlaybackService.start.
        syncSessionArtworkMetadata()
        PlaybackService.start(getApplication())
        requestNotificationPermissionIfNeeded()

        startPolling()
    }

    private fun requestNotificationPermissionIfNeeded() {
        // The activity asks; the view model only records that asking is needed, because
        // a permission request needs an Activity. See MainActivity.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val context = getApplication<Application>()
        val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            _ui.value = _ui.value.copy(needsNotificationPermission = true)
        }
    }

    /** Called by the activity once the notification permission result is known. */
    fun onNotificationPermissionResult(granted: Boolean) {
        _ui.value = _ui.value.copy(
            needsNotificationPermission = false,
            detail = if (granted) {
                _ui.value.detail
            } else {
                str(com.mylittleblover.mkwplayer.R.string.player_detail_notif_off)
            },
        )
        unlockCoordinator.onNotificationPermissionResult(getApplication(), granted)
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                val st = session.poll()
                PlaybackTimers.tickFromSession(session)
                PlaybackTimers.engine.syncInteractiveContext(
                    isInteractiveActive = st.active && !_ui.value.linearMode,
                    courseKey = currentStageCode ?: interactivePlan?.name,
                )
                if (st.linear && st.active) {
                    linearCoordinator.onPositionObserved(st.positionFrames, st.playing)
                }
                syncVisualizerPolling(st.playing)
                publish(st)
                followPerformance()
                if (!st.active && !linearCoordinator.state.value.active && !isCeremonyLocked) {
                    pollJob = null
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Lets the running performance react to the transport.
     *
     * Runs after the state has been published, so an advance is decided on the same reading the screen is
     * about to show rather than on one the listener never saw.
     */
    private fun followPerformance() {
        if(nativePerformance!=null) {
            session.interactiveObservation()?.let { nativePerformance!!.observe(it) }
            if (nativePerformance?.finished == true) {
                val fading = nativePerformance?.isFading == true
                if (fading) stop() else { stopTransport(cancelPendingOpen = false); markPerformanceFinished() }
                return
            }
            publishNativePerformance()
            return
        }
        val driver = performance ?: return
        apply(driver.tick(), driver)
        // The console's availability flags can change without a segment changing -- entering the main loop
        // happens on a command, but the flags are read from the driver, so they are refreshed every poll.
        val current = _ui.value.flow ?: return
        if (current.performing || driver.isRunning) {
            _ui.value = _ui.value.copy(
                flow = current.copy(
                    performanceStage = driver.describe(), stageKey = null, pendingStageKey = null,
                    fastPlaying = driver.isFast,
                    canAdvance = driver.canAdvance,
                    canEnd = driver.canEnd,
                ),
            )
        }
    }
    fun cancelPendingPerformance() {
        if (isCeremonyLocked) return
        val driver=nativePerformance ?: return
        publishNativePerformance(driver.cancelPending(),true)
    }
    fun selectMusicRegion(number: Int) {
        if (isCeremonyLocked) return
        startPerformance()
        val driver=nativePerformance ?: return
        publishNativePerformance(driver.selectRegion(number),true)
    }
    fun selectRecordingVersion(number: Int) {
        if (isCeremonyLocked) return
        startPerformance()
        val driver=nativePerformance ?: return
        publishNativePerformance(driver.selectVersion(number),true)
    }
    private fun publishNativePerformance(error: String?=null,fromAction: Boolean=false) {
        val driver=nativePerformance ?: return
        val plan=interactivePlan ?: return
        val current=_ui.value.flow ?: return
        val observation=driver.observationState
        ensurePlanIndex(plan)
        val segment=indexedPlanSegments[driver.current]
        val label=plan.labels[driver.current] ?: "Waiting"
        val pendingId=driver.pending
        val pendingTrans=pendingId?.let { target ->
            indexedPlanTransitions[(driver.current.toLong() shl 32) or (target.toLong() and 0xFFFFFFFFL)]
        }
        val upcomingId=if (pendingId == null && driver.canAdvance) plan.next[driver.current] else null
        val upcomingTrans=upcomingId?.let { target ->
            indexedPlanTransitions[(driver.current.toLong() shl 32) or (target.toLong() and 0xFFFFFFFFL)]
        }
        val pending=pendingId?.let { " → ${plan.labels[it]} (queued)" }.orEmpty()
        val liveSourceFrame = observation?.sourceFrame
        val liveFadeRemaining = observation?.fadeEndFrame?.let { end -> observation.transportFrame?.let { (end - it).coerceAtLeast(0) } }
        val srcLen = segment?.exit
        val flowProg = if (liveSourceFrame != null && liveSourceFrame >= 0L && srcLen != null && srcLen > 0L) {
            (liveSourceFrame.toDouble() / srcLen.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            _progressUi.value.progress
        }
        _progressUi.value = _progressUi.value.copy(
            position = if (liveSourceFrame != null && liveSourceFrame >= 0L) {
                formatFlowPos(liveSourceFrame, plan.spec.transportSampleRate)
            } else _progressUi.value.position,
            total = if (srcLen != null && srcLen > 0L) {
                formatFlowTotal(srcLen, plan.spec.transportSampleRate)
            } else _progressUi.value.total,
            progress = flowProg,
            sourceFrame = liveSourceFrame,
            fadeRemainingFrames = liveFadeRemaining,
        )
        // Keep exact fadeRemainingFrames during active fade-out for InteractiveFadeStopDeviceTest,
        // and keep constant presence marker for sourceFrame so non-timeline UI skips 200ms recompositions.
        val updated = _ui.value.copy(detail=if(fromAction) error else driver.diagnostic ?: _ui.value.detail,flow=current.copy(
            activeSegment=driver.current,performing=!driver.finished,
            performanceStage=if(driver.finished) "Finished" else label+pending,
            stageKey=if(driver.finished) "Finished" else label,
            pendingStageKey=if(driver.finished) null else pendingId?.let { plan.labels[it] },
            pendingSyncPoint=if(driver.finished) null else pendingTrans?.sync,
            pendingSourceCue=if(driver.finished) null else pendingTrans?.sourceCue,
            nextAdvanceTargetKey=if(driver.finished) null else upcomingId?.let { plan.labels[it] },
            nextAdvanceSyncPoint=if(driver.finished) null else upcomingTrans?.sync,
            canAdvance=driver.canAdvance,canEnd=driver.canEnd,fastPlaying=driver.isFast,canFast=driver.canFast,
            outros=driver.reachableOutros.sorted(),
            selectedVersion=driver.currentVersion,availableVersions=driver.availableVersions,
            selectedRegion=driver.currentRegion,availableRegions=driver.availableRegions,canCancelPending=driver.canCancelPending,
            sourceFrame=liveSourceFrame?.let { 0L },sourceFrames=srcLen,loopStart=segment?.loop?.start,loopEnd=segment?.loop?.end,
            voiceCount=observation?.voices ?: 0,transportRate=plan.spec.transportSampleRate,fading=driver.isFading,
            fadeRemainingFrames=if (driver.isFading) liveFadeRemaining else null))
        if (updated != _ui.value) _ui.value = updated
    }
    private fun publish(state: PlaybackSession.State) {
        creditListening(state)
        val progress = if (state.totalFrames > 0) {
            (state.positionFrames.toDouble() / state.totalFrames).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }
        val formattedPos = formatLinearPos(state.positionFrames, state.sampleRate)
        val formattedTotal = formatLinearTotal(state.totalFrames, state.sampleRate)
        val posMs = if (state.sampleRate > 0) EngineSnapshot.framesToMillis(state.positionFrames, state.sampleRate) else 0L
        if (_ui.value.flow == null) {
            _progressUi.value = ProgressUi(
                position = formattedPos,
                total = formattedTotal,
                progress = progress,
                positionMs = posMs,
                durationMs = if (state.sampleRate > 0) {
                    EngineSnapshot.framesToMillis(state.totalFrames, state.sampleRate)
                } else {
                    0L
                },
                sourceFrame = null,
                fadeRemainingFrames = null,
            )
        }

        val elapsed = android.os.SystemClock.elapsedRealtime()
        val shouldReadTiming = !state.playing || elapsed - lastTimingSnapshotMs >= 2_000L || _ui.value.timingVerdict == TimingDiagnosis.Verdict.NO_DATA
        val timing = if (shouldReadTiming) {
            lastTimingSnapshotMs = elapsed
            TimingDiagnosis.read(session.timingSnapshot())
        } else {
            null
        }

        val currentUi = _ui.value
        val updated = currentUi.copy(
            status = when {
                state.detail != null -> str(com.mylittleblover.mkwplayer.R.string.player_status_failed)
                state.playing -> str(com.mylittleblover.mkwplayer.R.string.discovery_playing)
                state.paused -> str(com.mylittleblover.mkwplayer.R.string.discovery_paused)
                state.title.isEmpty() -> str(com.mylittleblover.mkwplayer.R.string.player_status_no_track)
                else -> str(com.mylittleblover.mkwplayer.R.string.stage_stopped)
            },
            position = if (!state.playing || currentUi.position == "--:--") formattedPos else currentUi.position,
            total = formattedTotal,
            progress = if (!state.playing) progress else currentUi.progress,
            canPause = !isCeremonyLocked && state.playing,
            canResume = !isCeremonyLocked && state.paused,
            canStop = !isCeremonyLocked && state.active,
            timing = timing?.explanation ?: currentUi.timing,
            timingVerdict = timing?.verdict ?: currentUi.timingVerdict,
            outputActive = session.hasOutput,
            detail = state.detail ?: currentUi.detail,
            totalFrames = state.totalFrames.takeIf { it > 0L } ?: totalFrames,
            sampleRate = state.sampleRate.takeIf { it > 0 } ?: rate,
        )
        if (currentUi.linearMode && state.playing && !isCeremonyLocked && elapsed - lastPersistedLinearProgressMs >= 2_000L) {
            lastPersistedLinearProgressMs = elapsed
            linearCoordinator.saveCurrentProgress(state.positionFrames, updated.totalFrames, updated.sampleRate)
        }
        if (updated != currentUi) _ui.value = updated
    }

    fun pause() {
        if (isCeremonyLocked) return
        val st = session.pause()
        publish(st)
        if (_ui.value.linearMode) {
            linearCoordinator.saveCurrentProgress(st.positionFrames, _ui.value.totalFrames, _ui.value.sampleRate)
        }
    }

    fun resume() {
        if (isCeremonyLocked) return
        publish(session.resume())
    }

    /** Seeks by fraction of the track. The UI only knows a 0..1 drag value. */
    fun seekTo(fraction: Float) {
        if (isCeremonyLocked) return
        val frame = SeekTarget.fractionToFrame(fraction, totalFrames)
        if (!SeekTarget.isAddressable(frame, totalFrames)) return
        if (_ui.value.linearMode) {
            linearCoordinator.seekToFrame(frame)
        } else {
            viewModelScope.launch {
                publish(session.seekAsync(frame))
            }
        }
    }

    private fun markPerformanceFinished() {
        _ui.value = _ui.value.copy(flow = _ui.value.flow?.copy(
            performing = false, performanceStage = "Finished", stageKey = "Finished", pendingStageKey = null))
    }

    fun stop() {
        if (isCeremonyLocked) return
        stopTransport(cancelPendingOpen = true)
    }

    /** Natural completion stops only this session; it must not cancel a newer user selection. */
    private fun stopTransport(cancelPendingOpen: Boolean) {
        cancelPureFreeRoamDurationJob()
        if (cancelPendingOpen) {
            packPlaybackRequest++
            pollJob?.cancel()
            pollJob = null
            visualizerJob?.cancel()
            visualizerJob = null
        }
        nativePerformance=null
        performance?.stop()
        performance=null
        PlaybackTimers.engine.cancelInteractiveTimer()
        syncVisualizerPolling(false)
        if (cancelPendingOpen) {
            linearCoordinator.clearSession()
            linearCoordinator.clearSavedSession()
        }
        val stoppedState = session.stop()
        publish(stoppedState)
        val zeroPos = EngineSnapshot.formatPosition(0, rate)
        _progressUi.value = ProgressUi(position = zeroPos, total = _ui.value.total, progress = 0f)
        _ui.value = _ui.value.copy(
            progress = 0f,
            position = zeroPos,
            canPause = false,
            canResume = false,
            canStop = false,
            trackLoading = if (cancelPendingOpen) false else _ui.value.trackLoading,
            loadingTrackCode = if (cancelPendingOpen) null else _ui.value.loadingTrackCode,
            loadingTrackTitle = if (cancelPendingOpen) null else _ui.value.loadingTrackTitle,
            linearMode = false,
            linearTrack = null,
            canSeek = false,
            abLoopStartFrame = null,
            abLoopEndFrame = null,
            continueListening = if (cancelPendingOpen) null else _ui.value.continueListening,
            flow = _ui.value.flow?.copy(
                performing = false,
                performanceStage = "Stopped",
                stageKey = "Stopped",
                pendingStageKey = null,
                canAdvance = false,
                canEnd = false,
                fastPlaying = false,
                canFast = false,
                canCancelPending = false,
                fading = false,
                fadeRemainingFrames = null,
                voiceCount = 0,
                sourceFrame = null,
                availableRegions = emptySet(),
                availableVersions = emptySet(),
                selectedRegion = null,
                selectedVersion = null,
            ),
        )
        PlaybackService.stop(getApplication())
    }

    private fun releaseTransport() {
        pollJob?.cancel()
        pollJob = null
        visualizerJob?.cancel()
        visualizerJob = null
        PlaybackTimers.engine.cancelInteractiveTimer()
        session.uninterruptible = false
        testingCeremonyPlaying = false
        linearCoordinator.clearSession()
        flow = null
        flowCompiled = null
        flowBytes = emptyMap()
        nativePerformance = null
        interactivePlan = null
        _progressUi.value = ProgressUi()
        _spectrumBars.value = FloatArray(SPECTRUM_BAND_COUNT)
        _ui.value = _ui.value.copy(
            flow = null,
            trackLoading = false,
            loadingTrackCode = null,
            loadingTrackTitle = null,
            activeFavoriteId = null,
            linearMode = false,
            linearTrack = null,
            canSeek = false,
            abLoopStartFrame = null,
            abLoopEndFrame = null,
        )
        session.release()
        PlaybackService.stop(getApplication())
    }

    override fun onCleared() {
        // Playback deliberately continues: the foreground service keeps the session alive
        // and the notification keeps offering controls. Only the screen stops observing.
        pollJob?.cancel()
        pollJob = null
        visualizerJob?.cancel()
        visualizerJob = null
        PlaybackTimers.onSleepExpired = null
        PlaybackTimers.onInteractiveActionTriggered = null
        super.onCleared()
    }

    companion object {
        const val SPECTRUM_BAND_COUNT = 24

        /** Context id identifying authentic v1.8.0 Free Roam dynamic radio sessions. */
        const val FREE_ROAM_CONTEXT_ID = "freeroam"

        /** 200 ms keeps the progress bar smooth without touching the audio thread. */
        const val POLL_INTERVAL_MS = 200L

        /** Entry key reserved for the uninterruptible Special Cup reveal track (#111). */
        const val CEREMONY_ENTRY_KEY = "extra:special_cup_appears"

        /** Bundled compatibility manifest; see tools/gen_catalog.py. */
        const val CATALOG_ASSET = "catalog.json"

        /** Bundled chronological OST-to-BWAV Jukebox catalog; see tools/gen_jukebox_catalog.py. */
        const val JUKEBOX_CATALOG_ASSET = "jukebox_catalog.json"

        /** Folder inside app storage that [openFlowFromFolder] reads. */
        const val FLOW_FOLDER = "stages"

        /** Bundled map anchors. Named here so the missing case can be stated once. */
        const val MAP_ANCHORS_ASSET = "map_anchors.json"

        /** Where installed packs live, under app storage. */
        const val PACK_ROOT = "packs"

        /** Where Special Cup progress lives, beside the library rather than inside it. */
        const val SPECIAL_CUP_FILE = "special-cup.json"
    }
}
