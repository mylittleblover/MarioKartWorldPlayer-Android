package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.library.StageConnections
import com.mylittleblover.mkwplayer.player.LinearQueue
import com.mylittleblover.mkwplayer.player.LinearSessionStore
import com.mylittleblover.mkwplayer.routes.CustomRoute
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import java.io.File
import java.util.Locale

/**
 * Pure JVM-testable library update invalidation rules (#87, #88, #89).
 *
 * When a pack or candidate-pool update commits:
 * - #87: Saved custom routes that become topologically invalid or incomplete stay preserved on
 *   disk (no silent deletion or mutation) while being flagged unplayable until tail repair.
 * - #88: Active playback (route, course medley, jukebox, extra, or interactive stage) whose
 *   route, pool, stage, or audio assets became invalid or unavailable must stop immediately.
 * - #89: Stored Continue-Listening session snapshots must be cleared immediately whenever any
 *   referenced stage, asset, route topology, or route road candidate pool became invalid.
 */
internal object LibraryInvalidation {

    data class Environment(
        val mergedManifest: PackManifest?,
        val mergedDirectory: File?,
        val jukeboxCatalog: JukeboxCatalog.Catalog,
        val roadPool: RoadJukeboxCatalog.CandidatePool,
        val stageConnections: StageConnections.Catalog?,
        val officialRoutes: OfficialRouteCatalog?,
        val customRoutes: List<CustomRoute>,
        val extraCatalog: ExtraMusicCatalog.Catalog? = null,
        val isStageHidden: (String) -> Boolean = { false },
        val routeMusicCatalog: RoadJukeboxCatalog.RouteMusicCatalog? = null,
    ) {
        val playableStageCodes: Set<String> by lazy {
            val manifest = mergedManifest ?: return@lazy emptySet()
            manifest.stages.mapNotNull { stage ->
                val canonical = stage.code.removeSuffix("_00")
                val ok = stage.tracks.any { key ->
                    val track = manifest.track(key) ?: return@any false
                    mergedDirectory != null && track.parts.all { part ->
                        File(mergedDirectory, "audio/${part.asset.sha256}").isFile
                    }
                }
                canonical.takeIf { ok }
            }.toSet()
        }

        fun isStagePlayable(stageCode: String): Boolean {
            val canonical = stageCode.removeSuffix("_00")
            if (isStageHidden(canonical)) return false
            val manifest = mergedManifest ?: return false
            val dir = mergedDirectory ?: return false
            return manifest.stages.any { stage ->
                stage.code.removeSuffix("_00").equals(canonical, ignoreCase = true) &&
                    stage.tracks.any { key ->
                        val track = manifest.track(key) ?: return@any false
                        track.parts.all { File(dir, "audio/${it.asset.sha256}").isFile }
                    }
            }
        }

        fun isJukeboxReadable(item: JukeboxCatalog.TrackItem): Boolean {
            val dir = jukeboxCatalog.packByKey[item.track.key] ?: mergedDirectory ?: return false
            return File(dir, "audio/${item.track.primary.sha256}").isFile
        }
    }

    data class ActivePlaybackStopDecision(
        val shouldStop: Boolean,
        val reason: String? = null,
    )

    /** Inspects all saved custom routes and returns those now blocked (#87), never deleting any route. */
    fun findInvalidCustomRoutes(env: Environment): List<Pair<CustomRoute, RoadJukeboxCatalog.RouteValidationReport>> {
        val items = env.jukeboxCatalog.games.flatMap { it.items }
        return env.customRoutes.mapNotNull { route ->
            val report = RoadJukeboxCatalog.inspectCustomRoute(
                route = route,
                connections = env.stageConnections,
                pool = env.roadPool,
                installedJukebox = items,
                isStagePlayable = env::isStagePlayable,
                isStageHidden = env.isStageHidden,
                isJukeboxReadable = env::isJukeboxReadable,
                wikiCatalog = env.routeMusicCatalog,
            )
            if (!report.canPlay || !route.hasValidTopology(env.stageConnections)) {
                route to report
            } else {
                null
            }
        }
    }

    /** Evaluates whether the currently playing interactive stage must stop after an update (#88). */
    fun shouldStopInteractiveStage(
        stageCode: String?,
        env: Environment,
    ): ActivePlaybackStopDecision {
        if (stageCode.isNullOrBlank()) return ActivePlaybackStopDecision(false)
        val canonical = stageCode.removeSuffix("_00")
        if (env.isStageHidden(canonical)) {
            return ActivePlaybackStopDecision(true, "Stage '$canonical' is locked under updated pack rules.")
        }
        if (!env.isStagePlayable(canonical)) {
            return ActivePlaybackStopDecision(true, "Active stage '$canonical' is unavailable after library update.")
        }
        return ActivePlaybackStopDecision(false)
    }

    /** Evaluates whether an active linear session (including routes) must stop after an update (#88). */
    fun shouldStopActiveLinear(
        contextKind: LinearSessionStore.ContextKind,
        contextId: String?,
        currentEntry: LinearQueue.Entry?,
        allQueueEntries: List<LinearQueue.Entry>,
        env: Environment,
    ): ActivePlaybackStopDecision {
        if (currentEntry == null) return ActivePlaybackStopDecision(false)
        val items = env.jukeboxCatalog.games.flatMap { it.items }
        when (contextKind) {
            LinearSessionStore.ContextKind.OFFICIAL_ROUTE -> {
                val route = contextId?.let { env.officialRoutes?.route(it) }
                    ?: return ActivePlaybackStopDecision(true, "Active official route is no longer available.")
                val report = RoadJukeboxCatalog.inspectOfficialRoute(
                    route = route,
                    connections = env.stageConnections,
                    pool = env.roadPool,
                    installedJukebox = items,
                    isStagePlayable = env::isStagePlayable,
                    isStageHidden = env.isStageHidden,
                    isJukeboxReadable = env::isJukeboxReadable,
                    wikiCatalog = env.routeMusicCatalog,
                )
                if (!report.canPlay) {
                    return ActivePlaybackStopDecision(
                        true,
                        report.blockingReason ?: "Active official route became invalid after library update.",
                    )
                }
                val alternateReports = if (env.routeMusicCatalog != null) {
                    listOf(
                        RoadJukeboxCatalog.RoadMusicRuleMode.GRAND_PRIX,
                        RoadJukeboxCatalog.RoadMusicRuleMode.VS_DAY,
                        RoadJukeboxCatalog.RoadMusicRuleMode.VS_NIGHT,
                        RoadJukeboxCatalog.RoadMusicRuleMode.KNOCKOUT_DIRECT,
                    ).map { mode ->
                        RoadJukeboxCatalog.inspectOfficialRoute(
                            route = route,
                            connections = env.stageConnections,
                            pool = env.roadPool,
                            installedJukebox = items,
                            isStagePlayable = env::isStagePlayable,
                            isStageHidden = env.isStageHidden,
                            isJukeboxReadable = env::isJukeboxReadable,
                            wikiCatalog = env.routeMusicCatalog,
                            ruleMode = mode,
                        )
                    }
                } else {
                    emptyList()
                }
                if (!edgeEntriesMatchReport(allQueueEntries.map { it.key to it.sourceTrackKey }, report, alternateReports)) {
                    return ActivePlaybackStopDecision(
                        true,
                        "Active route road candidates or stage sources changed after library update.",
                    )
                }
            }

            LinearSessionStore.ContextKind.CUSTOM_ROUTE -> {
                val route = contextId?.let { id -> env.customRoutes.firstOrNull { it.id == id } }
                    ?: return ActivePlaybackStopDecision(true, "Active custom route is unavailable.")
                val report = RoadJukeboxCatalog.inspectCustomRoute(
                    route = route,
                    connections = env.stageConnections,
                    pool = env.roadPool,
                    installedJukebox = items,
                    isStagePlayable = env::isStagePlayable,
                    isStageHidden = env.isStageHidden,
                    isJukeboxReadable = env::isJukeboxReadable,
                    wikiCatalog = env.routeMusicCatalog,
                )
                if (!report.canPlay || !route.hasValidTopology(env.stageConnections)) {
                    return ActivePlaybackStopDecision(
                        true,
                        report.blockingReason ?: "Active custom route became invalid after library update.",
                    )
                }
                if (!edgeEntriesMatchReport(allQueueEntries.map { it.key to it.sourceTrackKey }, report)) {
                    return ActivePlaybackStopDecision(
                        true,
                        "Active custom route data changed incompatibly after library update.",
                    )
                }
            }

            else -> {
                // Verify active track assets / course remain readable.
                val playable = isEntrySourcePlayable(
                    key = currentEntry.key,
                    sourceTrackKey = currentEntry.sourceTrackKey,
                    sourceType = currentEntry.sourceType,
                    courseCode = currentEntry.courseCode,
                    assetSha256 = currentEntry.assetSha256,
                    env = env,
                )
                if (!playable) {
                    return ActivePlaybackStopDecision(
                        true,
                        "Current track '${currentEntry.title}' is unavailable after library update.",
                    )
                }
            }
        }
        return ActivePlaybackStopDecision(false)
    }

    /** Evaluates whether a saved Continue-Listening snapshot remains 100% valid (#89). */
    fun isSavedSessionStillValid(
        snapshot: LinearSessionStore.Snapshot,
        env: Environment,
    ): Boolean {
        val items = env.jukeboxCatalog.games.flatMap { it.items }
        when (snapshot.contextKind) {
            LinearSessionStore.ContextKind.OFFICIAL_ROUTE -> {
                val route = snapshot.contextId?.let { env.officialRoutes?.route(it) } ?: return false
                val report = RoadJukeboxCatalog.inspectOfficialRoute(
                    route = route,
                    connections = env.stageConnections,
                    pool = env.roadPool,
                    installedJukebox = items,
                    isStagePlayable = env::isStagePlayable,
                    isStageHidden = env.isStageHidden,
                    isJukeboxReadable = env::isJukeboxReadable,
                    wikiCatalog = env.routeMusicCatalog,
                )
                if (!report.canPlay) return false
                val alternateReports = if (env.routeMusicCatalog != null) {
                    listOf(
                        RoadJukeboxCatalog.RoadMusicRuleMode.GRAND_PRIX,
                        RoadJukeboxCatalog.RoadMusicRuleMode.VS_DAY,
                        RoadJukeboxCatalog.RoadMusicRuleMode.VS_NIGHT,
                        RoadJukeboxCatalog.RoadMusicRuleMode.KNOCKOUT_DIRECT,
                    ).map { mode ->
                        RoadJukeboxCatalog.inspectOfficialRoute(
                            route = route,
                            connections = env.stageConnections,
                            pool = env.roadPool,
                            installedJukebox = items,
                            isStagePlayable = env::isStagePlayable,
                            isStageHidden = env.isStageHidden,
                            isJukeboxReadable = env::isJukeboxReadable,
                            wikiCatalog = env.routeMusicCatalog,
                            ruleMode = mode,
                        )
                    }
                } else {
                    emptyList()
                }
                if (!edgeEntriesMatchReport(snapshot.frozenBase.map { it.key to it.sourceTrackKey }, report, alternateReports)) return false
            }

            LinearSessionStore.ContextKind.CUSTOM_ROUTE -> {
                val route = snapshot.contextId?.let { id -> env.customRoutes.firstOrNull { it.id == id } } ?: return false
                val report = RoadJukeboxCatalog.inspectCustomRoute(
                    route = route,
                    connections = env.stageConnections,
                    pool = env.roadPool,
                    installedJukebox = items,
                    isStagePlayable = env::isStagePlayable,
                    isStageHidden = env.isStageHidden,
                    isJukeboxReadable = env::isJukeboxReadable,
                    wikiCatalog = env.routeMusicCatalog,
                )
                if (!report.canPlay || !route.hasValidTopology(env.stageConnections)) return false
                if (!edgeEntriesMatchReport(snapshot.frozenBase.map { it.key to it.sourceTrackKey }, report)) return false
            }

            else -> Unit
        }
        val toVerify = buildList {
            add(snapshot.current)
            addAll(snapshot.frozenBase)
            addAll(snapshot.tempQueue)
        }
        return toVerify.all { entry ->
            isEntrySourcePlayable(
                key = entry.key,
                sourceTrackKey = entry.sourceTrackKey,
                sourceType = entry.sourceType,
                courseCode = entry.courseCode,
                assetSha256 = entry.assetSha256,
                env = env,
            )
        }
    }

    private fun edgeEntriesMatchReport(
        keyAndTrackPairs: List<Pair<String, String?>>,
        report: RoadJukeboxCatalog.RouteValidationReport,
        alternateReports: List<RoadJukeboxCatalog.RouteValidationReport> = emptyList(),
    ): Boolean {
        val allowedRoadKeysByEdge = mutableMapOf<Int, MutableSet<String>>()
        for (rep in listOf(report) + alternateReports) {
            for (leg in rep.legs) {
                val set = allowedRoadKeysByEdge.getOrPut(leg.index) { mutableSetOf() }
                when (val res = leg.resolution) {
                    is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete ->
                        set.addAll(res.candidates.map { it.track.key })
                    is RoadJukeboxCatalog.EdgeMusicResolution.CustomSubstitute -> set.add(res.track.track.key)
                    else -> Unit
                }
            }
        }
        val marker = ":edge:"
        for ((entryKey, sourceTrackKey) in keyAndTrackPairs) {
            val idxPos = entryKey.indexOf(marker)
            if (idxPos < 0) continue
            val after = entryKey.substring(idxPos + marker.length)
            val edgeIndex = after.substringBefore(':').toIntOrNull() ?: continue
            val trackKey = sourceTrackKey ?: continue
            val allowed = allowedRoadKeysByEdge[edgeIndex] ?: return false
            if (trackKey !in allowed) return false
        }
        return true
    }

    private fun isEntrySourcePlayable(
        key: String,
        sourceTrackKey: String?,
        sourceType: LinearSessionStore.SourceType,
        courseCode: String?,
        assetSha256: String?,
        env: Environment,
    ): Boolean = when (sourceType) {
        LinearSessionStore.SourceType.JUKEBOX -> {
            val lookupKey = sourceTrackKey ?: key
            val item = env.jukeboxCatalog.tracksByKey[lookupKey] ?: return false
            val sha = assetSha256 ?: item.primary.sha256
            val dir = env.jukeboxCatalog.packByKey[lookupKey] ?: env.mergedDirectory ?: return false
            File(dir, "audio/$sha").isFile
        }
        LinearSessionStore.SourceType.MEDLEY,
        LinearSessionStore.SourceType.PRESET -> {
            val code = courseCode ?: return false
            env.isStagePlayable(code)
        }
        LinearSessionStore.SourceType.EXTRA -> {
            val dir = env.mergedDirectory
            val extraId = ExtraMusicCatalog.extraIdFromEntryKey(key)
            if (dir == null || (extraId != null && !ExtraMusicCatalog.isVisible(extraId, !env.isStageHidden("Rainbow")))) {
                false
            } else {
                val extraItem = if (extraId != null && env.extraCatalog != null) {
                    env.extraCatalog.otherMusic.firstOrNull { it.id == extraId }
                } else {
                    null
                }
                when (extraItem) {
                    is ExtraMusicCatalog.OtherMusicItem.OverlaidOpening -> {
                        File(dir, "audio/${extraItem.op.sha256}").isFile &&
                            File(dir, "audio/${extraItem.telop.sha256}").isFile
                    }
                    is ExtraMusicCatalog.OtherMusicItem.ChainedSequence -> {
                        extraItem.parts.isNotEmpty() && extraItem.parts.all { part ->
                            File(dir, "audio/${part.asset.sha256}").isFile
                        }
                    }
                    else -> assetSha256 == null || File(dir, "audio/$assetSha256").isFile
                }
            }
        }
    }
}
