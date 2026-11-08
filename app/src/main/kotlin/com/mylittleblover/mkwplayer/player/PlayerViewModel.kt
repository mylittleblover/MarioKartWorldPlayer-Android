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
import com.mylittleblover.mkwplayer.library.LibraryStore
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.FolderAudioSource
import com.mylittleblover.mkwplayer.library.StageFlowReader
import com.mylittleblover.mkwplayer.unlock.ListeningProgress
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock
import com.mylittleblover.mkwplayer.unlock.SpecialCupRules
import com.mylittleblover.mkwplayer.unlock.UnlockStore
import com.mylittleblover.mkwplayer.library.Playability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        /** What the last pack import did, or null when none has run. */
        val pack: PackUi? = null,
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
        /**
         * The next-stage event is available.
         *
         * True only while the prelude is looping, because that is the one moment the event means anything.
         * A button that is always enabled is a button that is sometimes a no-op.
         */
        val canAdvance: Boolean = false,
        /** The ending event is available: the main loop is playing, so an ending can be chosen. */
        val canEnd: Boolean = false,
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
        val match = _ui.value.tracks.firstOrNull {
            it.name.contains(code.removeSuffix("_00"), ignoreCase = true)
        }
        if (match == null) {
            _ui.value = _ui.value.copy(
                detail = "No files for $code yet. Import a soundtrack pack or open the stage folder.",
            )
            return
        }
        select(match.key)
    }

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

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

    /** Special Cup progress, persisted separately from the library so a corrupt one cannot lose both. */
    private val unlockStore = UnlockStore(java.io.File(application.filesDir, SPECIAL_CUP_FILE))

    /** The cup's rules and progress, or null until there is a library to derive eligibility from. */
    private var cup: SpecialCupUnlock? = null

    /** Bridges the transport to the rules; null in the same condition as [cup]. */
    private var listening: ListeningProgress? = null

    /**
     * Feeds a transport sample to the cup.
     *
     * Only playing samples count, and only for the stage the sample belongs to. The result is
     * persisted immediately: losing thirty seconds of listening to a crash would be indistinguishable
     * from the rule not working.
     */
    private fun creditListening(state: PlaybackSession.State) {
        val bridge = listening ?: return
        val stage = state.title.takeIf { it.isNotEmpty() } ?: return
        val prefix = StageFlowDiscovery.prefixOf(stage)

        if (bridge.snapshot.currentStage != prefix) {
            bridge.begin(prefix, state.totalFrames, state.sampleRate.coerceAtLeast(48_000))
        }
        if (!state.playing && !state.paused) bridge.stopped()

        val outcome = bridge.sample(state.positionFrames, state.playing)
        val offered = outcome.offeredNow || _ui.value.unlock?.offered == true

        if (outcome.ignoredReason == null) {
            _ui.value = _ui.value.copy(unlock = UnlockUi.from(bridge, offered))
            unlockStore.save(outcome.state)?.let { _ui.value = _ui.value.copy(detail = it) }
        }
    }

    init {
        // The catalog is bundled data, so it is read once rather than re-parsed on every
        // recomposition. A missing or broken asset leaves it null and the UI omits the
        // section instead of showing an empty one.
        val catalog = try {
            getApplication<Application>().assets.open(CATALOG_ASSET).bufferedReader().use {
                CatalogLoader.parse(it.readText())
            }
        } catch (e: Exception) {
            null
        }
        if (catalog != null) {
            _ui.value = _ui.value.copy(catalog = catalog)
            browser = CatalogBrowser(catalog)
            refreshCatalog()
        }

        // Restore before the first frame so the list is never briefly empty on launch.
        val loaded = store.load()
        if (loaded.rows.isNotEmpty()) {
            _ui.value = _ui.value.copy(
                tracks = loaded.rows.map {
                    Track(
                        key = it.key,
                        name = it.name,
                        frames = it.frames,
                        sampleRate = it.sampleRate,
                        playable = it.playable,
                        reason = it.reason,
                    )
                },
            )
        }
        loaded.problem?.let { _ui.value = _ui.value.copy(detail = it) }

        // The cup's eligibility depends on what the user actually has, so it is derived after the
        // library is restored. Reading the stored progress first means the offer survives a restart.
        establishCup(loaded.tracks)
    }

    /**
     * Builds the cup from the library and restores its progress.
     *
     * Eligibility is derived from file names rather than stored, because the rule is "every stage you
     * have" -- a stored list would go stale the moment a stage was added, and the cup would then be
     * earnable without hearing it.
     */
    private fun establishCup(tracks: List<LibraryStore.TrackRow> = emptyList()) {
        val names = _ui.value.tracks.map { it.name }.ifEmpty { tracks.map { it.title } }
        val eligible = SpecialCupRules.eligibleStages(names)
        if (eligible.isEmpty()) {
            cup = null
            listening = null
            _ui.value = _ui.value.copy(unlock = null)
            return
        }

        val restored = unlockStore.load()
        restored.problem?.let { _ui.value = _ui.value.copy(detail = it) }

        val rules = SpecialCupUnlock(eligible).let { fresh ->
            restored.state?.let { fresh.withState(it) } ?: fresh
        }
        cup = rules

        val bridge = ListeningProgress(rules)
        listening = bridge
        // The offer is only claimed if the stored phase says the user was already offered it, so a
        // restart does not re-notify.
        val offered = restored.state?.ceremony == SpecialCupUnlock.CeremonyPhase.PENDING ||
            restored.state?.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING ||
            restored.state?.ceremony == SpecialCupUnlock.CeremonyPhase.COMPLETED
        _ui.value = _ui.value.copy(unlock = UnlockUi.from(bridge, offered))
    }

    /** Enters the ceremony. Fails loudly rather than pretending when the cup is not open. */
    fun enterCeremony(): Boolean {
        val rules = cup ?: return false
        val bridge = listening ?: return false
        if (bridge.snapshot.ceremony == SpecialCupUnlock.CeremonyPhase.PLAYING) return false
        val next = rules.withState(bridge.snapshot).beginCeremony()
        bridge.seed(next)
        unlockStore.save(next)?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = UnlockUi.from(bridge, offered = true))
        return true
    }

    /** Leaves the ceremony early. The entitlement is preserved by the rules. */
    fun leaveCeremony() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (bridge.snapshot.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING) return
        val next = rules.withState(bridge.snapshot).leaveCeremonyEarly()
        bridge.seed(next)
        unlockStore.save(next)?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = UnlockUi.from(bridge, offered = true))
    }

    /**
     * Reports that the ceremony audio reached its natural end.
     *
     * Called by whoever owns the ceremony track's completion, not by a timer: a UI countdown would be
     * the fake-completion the specification forbids.
     */
    fun ceremonyReachedEnd() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (bridge.snapshot.ceremony != SpecialCupUnlock.CeremonyPhase.PLAYING) return
        val next = rules.withState(bridge.snapshot).finishCeremony(reachedEnd = true)
        bridge.seed(next)
        unlockStore.save(next)?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = UnlockUi.from(bridge, offered = true))
    }

    /** Consumes the first-time congratulations, if it is owed. */
    fun dismissCongratulations() {
        val rules = cup ?: return
        val bridge = listening ?: return
        if (!rules.withState(bridge.snapshot).owesCongratulations) return
        val next = rules.withState(bridge.snapshot).markCongratulated()
        bridge.seed(next)
        unlockStore.save(next)?.let { _ui.value = _ui.value.copy(detail = it) }
        _ui.value = _ui.value.copy(unlock = UnlockUi.from(bridge, offered = true))
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
        _ui.value = _ui.value.copy(busy = true, status = "Reading file...", detail = null)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { inspect(uri) }
            _ui.value = _ui.value.copy(busy = false)

            when (outcome) {
                is Outcome.Refused -> {
                    _ui.value = _ui.value.copy(status = "Cannot play this file", detail = outcome.reason)
                }
                is Outcome.Added -> {
                    _ui.value = _ui.value.copy(
                        tracks = _ui.value.tracks + outcome.track,
                        status = if (outcome.track.playable) "Ready to play" else "Unsupported codec",
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
        val track = _ui.value.tracks.firstOrNull { it.key == key } ?: return
        val wasSelected = _ui.value.selectedKey == key
        if (wasSelected) {
            releaseTransport()
        }
        prepared.remove(key)
        _ui.value = _ui.value.copy(
            tracks = _ui.value.tracks.filterNot { it.key == key },
            selectedKey = if (wasSelected) null else _ui.value.selectedKey,
            status = if (wasSelected) "No track loaded" else _ui.value.status,
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
        if (uris.isEmpty()) return
        _ui.value = _ui.value.copy(busy = true, status = "Reading files...", detail = null)
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
                    status = "No usable parts",
                    detail = rejected.firstOrNull() ?: "None of the chosen files could be read.",
                )
                return@launch
            }

            val group = StageFlowDiscovery.dominant(StageFlowDiscovery.group(parts))
            if (group == null) {
                _ui.value = _ui.value.copy(status = "No usable parts", detail = rejected.firstOrNull())
                return@launch
            }

            val assembled = StageFlowDiscovery.flowFor(group)
            if (assembled.isFailure) {
                _ui.value = _ui.value.copy(
                    status = "Cannot open this flow",
                    detail = assembled.exceptionOrNull()?.message,
                )
                return@launch
            }
            startFlow(assembled.getOrThrow(), bytes, rejected)
        }
    }

    private fun startFlow(flow: StageFlow, bytes: Map<String, ByteArray>, rejected: List<String>) {
        val compiled = try {
            StageFlowCompiler.compileFromBytes(flow, bytes)
        } catch (e: IllegalArgumentException) {
            _ui.value = _ui.value.copy(
                status = "Cannot prepare this flow",
                detail = e.message,
            )
            return
        }

        val entry = FlowPresentation.entrySection(compiled)
        val state = session.loadFlow(
            title = flow.name,
            packet = compiled.packet,
            assets = compiled.assetPaths.map { path -> path to bytes[path]!! },
            frames = FlowPresentation.totalFrames(flow),
            startSegment = entry,
        )
        if (state.detail != null) {
            _ui.value = _ui.value.copy(status = "Cannot play this flow", detail = state.detail)
            return
        }

        this.flow = flow
        this.flowCompiled = compiled
        this.flowBytes = bytes
        totalFrames = FlowPresentation.totalFrames(flow)
        rate = state.sampleRate

        _ui.value = _ui.value.copy(
            title = flow.name,
            status = "Playing",
            total = EngineSnapshot.formatPosition(totalFrames, rate),
            canPause = true,
            canStop = true,
            outputActive = session.hasOutput,
            flow = FlowUi(
                title = flow.name,
                sections = FlowPresentation.sections(flow, compiled),
                activeSegment = entry,
                summary = FlowPresentation.summary(flow),
                provenance = FlowPresentation.provenanceLabel(flow),
                isProvisional = flow.isProvisional,
                outros = compiled.segmentByOutro.keys.sorted(),
                hasFast = compiled.segmentByRole.containsKey(StageFlow.Role.Fast),
            ),
            detail = buildList {
                if (rejected.isNotEmpty()) add("Skipped: " + rejected.joinToString("; "))
                if (!session.hasOutput) {
                    add("No device output on this system; the engine is running without a speaker.")
                }
            }.joinToString(" ").ifEmpty { null },
        )

        PlaybackService.start(getApplication())
        requestNotificationPermissionIfNeeded()
        startPolling()
    }

    /**
     * Opens the stage files found in a folder inside app storage.
     *
     * Same path as [openFlow], minus the picker: this is what makes the flow reachable from an
     * automated test, and it is a working fallback for a user whose file manager returns names the
     * app cannot read.
     */
    fun openFlowFromFolder() {
        val folder = java.io.File(getApplication<Application>().filesDir, FLOW_FOLDER)
        _ui.value = _ui.value.copy(busy = true, status = "Scanning folder...", detail = null)
        viewModelScope.launch {
            val scan = withContext(Dispatchers.IO) { FolderAudioSource.scan(folder) }
            _ui.value = _ui.value.copy(busy = false)

            if (scan.missingFolder) {
                _ui.value = _ui.value.copy(
                    status = "No folder",
                    detail = "Put the stage files in ${folder.path} and try again.",
                )
                return@launch
            }
            if (scan.found.isEmpty()) {
                _ui.value = _ui.value.copy(
                    status = "No usable files",
                    detail = scan.rejected.firstOrNull() ?: "The folder holds no readable stage parts.",
                )
                return@launch
            }

            val group = StageFlowDiscovery.dominant(scan.groups())
            if (group == null) {
                _ui.value = _ui.value.copy(status = "No usable files", detail = scan.rejected.firstOrNull())
                return@launch
            }

            val assembled = StageFlowDiscovery.flowFor(group)
            if (assembled.isFailure) {
                _ui.value = _ui.value.copy(
                    status = "Cannot open this flow",
                    detail = assembled.exceptionOrNull()?.message,
                )
                return@launch
            }
            startFlow(assembled.getOrThrow(), scan.bytesByName(), scan.rejected)
        }
    }

    /** Requests a section of the open flow. */
    fun playSection(segment: Int) {
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
                performanceStage = "",
                canAdvance = false,
                canEnd = false,
                fastPlaying = false,
            ),
            status = "Playing",
        )
    }

    /**
     * Performs the whole open stage: prelude, main loop, then the outro the listener picks.
     *
     * Refuses with a stated reason rather than doing nothing, because a Start button that silently fails is
     * indistinguishable from a broken app.
     */
    fun startPerformance() {
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

    /** The next-stage event: leave the looping prelude for the main loop. */
    fun advancePerformance() {
        val driver = performance ?: return
        apply(driver.advance(), driver)
    }

    /** Switches between the main loop and its fast arrangement. */
    fun toggleFast() {
        val driver = performance ?: return
        apply(driver.toggleFast(), driver)
    }

    /** Takes the ending nearest to where the main loop has got to. */
    fun takeOutro() {
        val driver = performance ?: return
        apply(driver.takeOutro(), driver)
    }

    /** Stops a performance without ending the stage. */
    fun stopPerformance() {
        val driver = performance ?: return
        performance = null
        driver.stop()
        _ui.value = _ui.value.copy(
            flow = _ui.value.flow?.copy(
                performing = false,
                performanceStage = "Stopped",
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
                performance = null
                _ui.value = _ui.value.copy(
                    flow = _ui.value.flow?.copy(
                        performing = false,
                        performanceStage = if (event.reason.contains("stopped")) "Stopped" else "Finished",
                        canAdvance = false,
                        canEnd = false,
                        fastPlaying = false,
                    ),
                )
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
                performanceStage = driver.describe(),
                performanceBlocker = null,
                fastPlaying = driver.isFast,
                canAdvance = driver.canAdvance,
                canEnd = driver.canEnd,
            ),
            status = "Playing",
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
        val track = _ui.value.tracks.firstOrNull { it.key == key } ?: return
        if (!track.playable) {
            _ui.value = _ui.value.copy(
                selectedKey = key,
                title = track.name,
                status = "Unsupported codec",
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
                status = "Not prepared",
                detail = "Pick this file again to reopen it.",
            )
            return
        }

        start(track, ready)
    }

    private fun start(track: Track, ready: Prepared) {
        val state = session.load(
            title = ready.name,
            packet = ready.packet,
            audio = ready.bytes,
            assetId = ready.name,
            frames = ready.frames,
        )
        if (state.detail != null) {
            _ui.value = _ui.value.copy(status = "Cannot play this file", detail = state.detail)
            return
        }

        totalFrames = ready.frames
        rate = ready.sampleRate

        _ui.value = _ui.value.copy(
            selectedKey = track.key,
            title = ready.name,
            status = "Playing",
            total = EngineSnapshot.formatPosition(totalFrames, rate),
            canPause = true,
            canStop = true,
            outputActive = session.hasOutput,
            detail = if (session.hasOutput) {
                null
            } else {
                "No device output on this system; the engine is running without a speaker."
            },
        )

        // Start the foreground service so playback survives the activity, and on
        // Android 13+ ask for the notification the service needs.
        // Only starts when the session is actually playing; see PlaybackService.start.
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
                "Notifications are off, so the media controls will not appear."
            },
        )
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                publish(session.poll())
                followPerformance()
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
        val driver = performance ?: return
        apply(driver.tick(), driver)
        // The console's availability flags can change without a segment changing -- entering the main loop
        // happens on a command, but the flags are read from the driver, so they are refreshed every poll.
        val current = _ui.value.flow ?: return
        if (current.performing || driver.isRunning) {
            _ui.value = _ui.value.copy(
                flow = current.copy(
                    performanceStage = driver.describe(),
                    fastPlaying = driver.isFast,
                    canAdvance = driver.canAdvance,
                    canEnd = driver.canEnd,
                ),
            )
        }
    }
    private fun publish(state: PlaybackSession.State) {
        creditListening(state)
        val progress = if (state.totalFrames > 0) {
            (state.positionFrames.toDouble() / state.totalFrames).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

        // Timing only exists while this screen holds the output handle; the service does
        // not expose it, so an absent packet stays NO DATA rather than being invented.
        val timing = TimingDiagnosis.read(session.timingSnapshot())

        _ui.value = _ui.value.copy(
            status = when {
                state.detail != null -> "Failed"
                state.playing -> "Playing"
                state.paused -> "Paused"
                state.title.isEmpty() -> "No track loaded"
                else -> "Stopped"
            },
            position = EngineSnapshot.formatPosition(state.positionFrames, state.sampleRate),
            total = EngineSnapshot.formatPosition(state.totalFrames, state.sampleRate),
            progress = progress,
            canPause = state.playing,
            canResume = state.paused,
            canStop = state.active,
            timing = timing?.explanation ?: "Timing unavailable",
            timingVerdict = timing?.verdict ?: TimingDiagnosis.Verdict.NO_DATA,
            outputActive = session.hasOutput,
            detail = state.detail ?: _ui.value.detail,
        )
    }

    fun pause() {
        publish(session.pause())
    }

    fun resume() {
        publish(session.resume())
    }

    /** Seeks by fraction of the track. The UI only knows a 0..1 drag value. */
    fun seekTo(fraction: Float) {
        val frame = SeekTarget.fractionToFrame(fraction, totalFrames)
        if (!SeekTarget.isAddressable(frame, totalFrames)) return
        publish(session.seek(frame))
    }

    fun stop() {
        publish(session.stop())
        _ui.value = _ui.value.copy(progress = 0f, position = EngineSnapshot.formatPosition(0, rate))
        PlaybackService.stop(getApplication())
    }

    private fun releaseTransport() {
        pollJob?.cancel()
        pollJob = null
        flow = null
        flowCompiled = null
        flowBytes = emptyMap()
        _ui.value = _ui.value.copy(flow = null)
        session.release()
        PlaybackService.stop(getApplication())
    }

    override fun onCleared() {
        // Playback deliberately continues: the foreground service keeps the session alive
        // and the notification keeps offering controls. Only the screen stops observing.
        pollJob?.cancel()
        pollJob = null
        super.onCleared()
    }

    companion object {
        /** 200 ms keeps the progress bar smooth without touching the audio thread. */
        const val POLL_INTERVAL_MS = 200L

        /** Bundled compatibility manifest; see tools/gen_catalog.py. */
        const val CATALOG_ASSET = "catalog.json"

        /** Folder inside app storage that [openFlowFromFolder] reads. */
        const val FLOW_FOLDER = "stages"

        /** Bundled map anchors. Named here so the missing case can be stated once. */
        const val MAP_ANCHORS_ASSET = "map_anchors.json"

        /** Where Special Cup progress lives, beside the library rather than inside it. */
        const val SPECIAL_CUP_FILE = "special-cup.json"
    }
}