package com.mylittleblover.mkwplayer.routes

import com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog
import com.mylittleblover.mkwplayer.library.StageConnections
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Resolves directed road edges to automatically identified Jukebox music candidates (#70-#73, #82-#83).
 *
 * Strictly enforces:
 * - Directed connections only (`StageConnections.connects(from, to)`, #74-#75).
 * - If a road has an established candidate pool and any track in that pool is missing or
 *   unreadable, launching any route containing that road is forbidden (#82).
 * - Automatic Jukebox recognition only (`sha256` / `trackKey` / `bwavStem` / `jukeboxCode`, #83).
 * - Missing candidate pool allows "自定义替代" on custom routes without mutating official pools (#73).
 */
internal object RoadJukeboxCatalog {

    data class EdgeKey(
        val from: String,
        val to: String,
    ) {
        val canonicalFrom: String = from.trim().removeSuffix("_00")
        val canonicalTo: String = to.trim().removeSuffix("_00")
        fun normalized(): EdgeKey = EdgeKey(canonicalFrom, canonicalTo)
    }

    sealed interface MergeResult {
        data class Replaced(val pool: CandidatePool, val changed: Boolean) : MergeResult
        data class IgnoredOlderVersion(val pool: CandidatePool) : MergeResult
        data class RejectedConflict(val reason: String) : MergeResult
    }

    data class CandidatePool(
        val schemaVersion: Int = 1,
        val dataVersion: Int = 0,
        val entries: Map<EdgeKey, List<String>> = emptyMap(),
    ) {
        fun candidatesFor(from: String, to: String): List<String>? =
            entries[EdgeKey(from.trim().removeSuffix("_00"), to.trim().removeSuffix("_00"))]

        /** Higher valid dataVersion replaces the older pool wholesale; never takes union (#86). */
        fun mergeReplace(incoming: CandidatePool): MergeResult {
            if (incoming.schemaVersion != 1 || incoming.dataVersion < 0) {
                return MergeResult.RejectedConflict("Invalid road candidate pool version")
            }
            return when {
                incoming.dataVersion > dataVersion -> MergeResult.Replaced(incoming, changed = true)
                incoming.dataVersion == dataVersion -> {
                    if (incoming.entries == entries) {
                        MergeResult.Replaced(this, changed = false)
                    } else {
                        MergeResult.RejectedConflict(
                            "Road candidate pool version ${incoming.dataVersion} conflicts with existing data (#86).",
                        )
                    }
                }
                else -> MergeResult.IgnoredOlderVersion(this)
            }
        }

        fun toJsonString(): String {
            val arr = JSONArray()
            entries.entries.sortedWith(compareBy({ it.key.from }, { it.key.to })).forEach { (key, cands) ->
                arr.put(
                    JSONObject()
                        .put("from", key.from)
                        .put("to", key.to)
                        .put("candidates", JSONArray(cands)),
                )
            }
            return JSONObject()
                .put("schemaVersion", schemaVersion)
                .put("dataVersion", dataVersion)
                .put("roads", arr)
                .toString()
        }

        companion object {
            fun parse(text: String): CandidatePool? = runCatching {
                val root = JSONObject(text)
                val schemaVersion = root.getInt("schemaVersion")
                require(schemaVersion == 1)
                val dataVersion = root.optInt("dataVersion", 0)
                require(dataVersion >= 0)
                val roads = root.optJSONArray("roads") ?: JSONArray()
                val map = linkedMapOf<EdgeKey, List<String>>()
                for (i in 0 until roads.length()) {
                    val obj = roads.getJSONObject(i)
                    val from = obj.getString("from").trim().removeSuffix("_00")
                    val to = obj.getString("to").trim().removeSuffix("_00")
                    require(from.isNotBlank() && to.isNotBlank() && from != to)
                    val candArr = obj.getJSONArray("candidates")
                    val cands = (0 until candArr.length())
                        .map { candArr.getString(it).trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    val key = EdgeKey(from, to)
                    require(!map.containsKey(key)) { "Duplicate candidate entry for ${key.from}->${key.to}" }
                    if (cands.isNotEmpty()) {
                        map[key] = cands
                    }
                }
                CandidatePool(schemaVersion, dataVersion, map)
            }.getOrNull()
        }
    }

    /** Playback preset selection strategy across stops in a route playlist. */
    enum class RouteSuiteMode {
        GAME_SUITE,
        COMPLETE_SUITE;

        companion object {
            fun fromStored(raw: String?): RouteSuiteMode = when (raw?.trim()?.uppercase(Locale.ROOT)) {
                COMPLETE_SUITE.name -> COMPLETE_SUITE
                else -> GAME_SUITE
            }
        }
    }

    /** Courses whose bundled medley catalog provides an `"a_alt"` variant (non-special-layer / rally variant). */
    private val COURSES_WITH_A_ALT: Set<String> = setOf("DonkeyKong", "SnowMountain", "Cinema", "KoopaTroopaBeach")

    /**
     * Resolves the target built-in suite variant (`"a"`, `"a_alt"`, `"b"`, `"c"`, `"d"`) for stop
     * [stopIndex] of [stopCount] under [suiteMode].
     */
    fun resolveSuiteVariantForStop(
        officialKind: OfficialRouteKind?,
        suiteMode: RouteSuiteMode,
        stopIndex: Int,
        stopCount: Int,
        stageCode: String,
        roadRuleMode: RoadMusicRuleMode? = null,
        availableVariants: Set<String>? = null,
    ): String {
        if (suiteMode == RouteSuiteMode.COMPLETE_SUITE) return "a"
        val canonical = stageCode.trim().removeSuffix("_00")
        val hasAAlt = availableVariants?.contains("a_alt") ?: (canonical in COURSES_WITH_A_ALT)
        val useRallyProgression = officialKind == OfficialRouteKind.RALLY ||
            (officialKind == null && roadRuleMode == RoadMusicRuleMode.KNOCKOUT_DIRECT)
        return if (useRallyProgression) {
            when {
                stopIndex == 0 -> "b"
                stopCount > 1 && stopIndex == stopCount - 1 -> "d"
                hasAAlt -> "a_alt"
                else -> "a"
            }
        } else {
            when {
                stopIndex == 0 -> "c"
                else -> "a"
            }
        }
    }

    val DEFAULT_ROAD_RULE_MODE: RoadMusicRuleMode = RoadMusicRuleMode.VS_AUTO

    const val PURE_FREE_ROAM_MIN_TRACKS_PER_LEG: Int = 1
    const val PURE_FREE_ROAM_MAX_TRACKS_PER_LEG: Int = 10
    const val PURE_FREE_ROAM_DEFAULT_TRACKS_PER_LEG: Int = 2

    const val PURE_FREE_ROAM_MIN_MINUTES_PER_LEG: Int = 1
    const val PURE_FREE_ROAM_MAX_MINUTES_PER_LEG: Int = 30
    const val PURE_FREE_ROAM_DEFAULT_MINUTES_PER_LEG: Int = 5

    enum class RoadMusicRuleMode {
        AUTO,
        GRAND_PRIX,
        VS_AUTO,
        VS_DAY,
        VS_NIGHT,
        KNOCKOUT_DIRECT,
        ;

        companion object {
            fun fromStored(raw: String?): RoadMusicRuleMode =
                entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: VS_AUTO
        }
    }

    enum class PureFreeRoamControlMode {
        TRACK_COUNT,
        DURATION,
        ;

        companion object {
            fun fromStored(raw: String?): PureFreeRoamControlMode = when (raw?.trim()?.uppercase(Locale.ROOT)) {
                DURATION.name -> DURATION
                else -> TRACK_COUNT
            }
        }
    }

    /**
     * Source track library for Pure Free Roam route playback:
     * - [ROUTE_RULES]: per-leg route road music pools (GP / VS Day biome + TypeAll / VS Night).
     * - [GAME_FREE_ROAM]: authentic in-game solo Free Roam `SoundJukeBoxPlaylist` tables
     *   (`Default` / `Night` / merged 202-track pool / 93 `IsFreerunOnly` tracks), never road pools.
     */
    enum class PureFreeRoamTrackPool {
        ROUTE_RULES,
        GAME_FREE_ROAM,
        ;

        companion object {
            fun fromStored(raw: String?): PureFreeRoamTrackPool = when (raw?.trim()?.uppercase(Locale.ROOT)) {
                GAME_FREE_ROAM.name -> GAME_FREE_ROAM
                else -> ROUTE_RULES
            }
        }
    }

    data class PureFreeRoamConfig(
        val enabled: Boolean = false,
        val controlMode: PureFreeRoamControlMode = PureFreeRoamControlMode.TRACK_COUNT,
        val defaultTracksPerLeg: Int = PURE_FREE_ROAM_DEFAULT_TRACKS_PER_LEG,
        val defaultMinutesPerLeg: Int = PURE_FREE_ROAM_DEFAULT_MINUTES_PER_LEG,
        val trackPool: PureFreeRoamTrackPool = PureFreeRoamTrackPool.ROUTE_RULES,
        val freeRoamMode: FreeRoamJukeboxCatalog.FreeRoamMode = FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK,
    ) {
        fun normalized(): PureFreeRoamConfig = copy(
            defaultTracksPerLeg = defaultTracksPerLeg.coerceIn(
                PURE_FREE_ROAM_MIN_TRACKS_PER_LEG,
                PURE_FREE_ROAM_MAX_TRACKS_PER_LEG,
            ),
            defaultMinutesPerLeg = defaultMinutesPerLeg.coerceIn(
                PURE_FREE_ROAM_MIN_MINUTES_PER_LEG,
                PURE_FREE_ROAM_MAX_MINUTES_PER_LEG,
            ),
            freeRoamMode = FreeRoamJukeboxCatalog.coerceInGameFreeRoamMode(freeRoamMode),
        )

        fun toJsonObject(): JSONObject {
            val clean = normalized()
            return JSONObject()
                .put("enabled", clean.enabled)
                .put("controlMode", clean.controlMode.name)
                .put("defaultTracksPerLeg", clean.defaultTracksPerLeg)
                .put("defaultMinutesPerLeg", clean.defaultMinutesPerLeg)
                .put("trackPool", clean.trackPool.name)
                .put("freeRoamMode", clean.freeRoamMode.name)
        }

        companion object {
            fun fromJsonObject(obj: JSONObject?): PureFreeRoamConfig {
                if (obj == null) return PureFreeRoamConfig()
                return PureFreeRoamConfig(
                    enabled = obj.optBoolean("enabled", false),
                    controlMode = PureFreeRoamControlMode.fromStored(obj.optString("controlMode", "")),
                    defaultTracksPerLeg = obj.optInt("defaultTracksPerLeg", PURE_FREE_ROAM_DEFAULT_TRACKS_PER_LEG),
                    defaultMinutesPerLeg = obj.optInt("defaultMinutesPerLeg", PURE_FREE_ROAM_DEFAULT_MINUTES_PER_LEG),
                    trackPool = PureFreeRoamTrackPool.fromStored(obj.optString("trackPool", "")),
                    freeRoamMode = FreeRoamJukeboxCatalog.FreeRoamMode.fromStored(obj.optString("freeRoamMode", "")),
                ).normalized()
            }
        }
    }

    data class EdgeFreeRoamOverride(
        val trackCountOverride: Int? = null,
        val durationMinutesOverride: Int? = null,
    ) {
        fun normalized(): EdgeFreeRoamOverride = EdgeFreeRoamOverride(
            trackCountOverride = trackCountOverride?.coerceIn(
                PURE_FREE_ROAM_MIN_TRACKS_PER_LEG,
                PURE_FREE_ROAM_MAX_TRACKS_PER_LEG,
            ),
            durationMinutesOverride = durationMinutesOverride?.coerceIn(
                PURE_FREE_ROAM_MIN_MINUTES_PER_LEG,
                PURE_FREE_ROAM_MAX_MINUTES_PER_LEG,
            ),
        )

        val isEmpty: Boolean
            get() = trackCountOverride == null && durationMinutesOverride == null
    }

    data class PureFreeRoamLegPlan(
        val edgeIndex: Int,
        val from: String,
        val to: String,
        val effectiveRule: EffectiveRuleMode,
        val controlMode: PureFreeRoamControlMode,
        val trackCount: Int,
        val durationMs: Long,
        val rankedCandidates: List<RouteMusicCatalog.RankedCandidate>,
        val trackPool: PureFreeRoamTrackPool = PureFreeRoamTrackPool.ROUTE_RULES,
        val freeRoamMode: FreeRoamJukeboxCatalog.FreeRoamMode = FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK,
    )

    /** Matches `WorldInfo::evalTimeOfDayEnum` (`0x007cd400`): Day = 03:00–19:00, Night = 19:00–03:00. */
    fun isNightHour(hourOfDay: Int): Boolean {
        val h = ((hourOfDay % 24) + 24) % 24
        return h < 3 || h >= 19
    }

    fun isCurrentLocalNight(): Boolean = isNightHour(java.time.LocalTime.now().hour)

    enum class EffectiveRuleMode {
        GRAND_PRIX,
        VS_DAY,
        VS_NIGHT,
        KNOCKOUT_DIRECT,
    }

    enum class CandidateTier {
        GRAND_PRIX,
        EXCLUSIVE_ORDER,
        THEMED_BOOSTED,
        MAIN_ORDER,
        NIGHT_ORDER,
    }

    /**
     * Bundled MKW v1.8.0 Route music ruleset (`assets/route_music_catalog.json`).
     *
     * Models:
     * - Grand Prix per-edge pools (23 `PrimaryCourse` Jukebox edges + `Central -> Rainbow` special approach)
     * - VS Race Daytime (8 biome first-song playlists throttled 4:1 with `TypeAll`, 03:00–19:00)
     * - VS Race Nighttime (`24040117_J25YUWHR` / `レース夜`, 38 tracks / 111 slots, 19:00–03:00, overrides biomes)
     */
    data class RouteMusicCatalog(
        val schemaVersion: Int = 1,
        val themesByStage: Map<String, String> = emptyMap(),
        val exclusiveOriginCourses: Set<String> = emptySet(),
        val specialEdges: Map<EdgeKey, SpecialEdgeRule> = emptyMap(),
        val gpEdges: Map<EdgeKey, List<String>> = emptyMap(),
        val tracks: List<TrackRule> = emptyList(),
    ) {
        data class SpecialEdgeRule(
            val from: String,
            val to: String,
            val stem: String,
            val title: String,
        )

        data class TrackRule(
            val jukeboxCode: String,
            val anyOriginDay: Boolean,
            val anyOriginNight: Boolean,
            val dayThemes: Set<String>,
            val dayCourses: Set<String>,
            val likelyDayThemes: Set<String>,
            val likelyDayCourses: Set<String>,
            val nightWeight: Int = if (anyOriginNight) 1 else 0,
            val inRacePool: Boolean = false,
        )

        data class RankedCandidate(
            val jukeboxCode: String,
            val tier: CandidateTier,
            val weight: Int,
        )

        fun specialFor(from: String, to: String): SpecialEdgeRule? =
            specialEdges[EdgeKey(from.trim().removeSuffix("_00"), to.trim().removeSuffix("_00"))]

        /**
         * Returns the origin biome key for [from] if it maps to one of the 8 v1.8.0 dedicated biome
         * first-song playlists (`beach`, `desert`, `evil`, `ice`, `mesa`, `wooded`, `Cinema`,
         * `RaftingRiver`), or null if [from] uses the general `TypeAll` playlist.
         */
        fun originBiomeKey(from: String): String? {
            val clean = from.trim().removeSuffix("_00")
            themesByStage[clean]?.let { return it }
            return clean.takeIf { it in exclusiveOriginCourses }
        }

        fun resolveRuleCandidates(
            from: String,
            to: String,
            mode: EffectiveRuleMode,
        ): List<RankedCandidate> {
            val cleanFrom = from.trim().removeSuffix("_00")
            val cleanTo = to.trim().removeSuffix("_00")
            return when (mode) {
                EffectiveRuleMode.KNOCKOUT_DIRECT -> emptyList()
                EffectiveRuleMode.GRAND_PRIX -> {
                    val refs = gpEdges[EdgeKey(cleanFrom, cleanTo)].orEmpty()
                    refs.map { RankedCandidate(it, CandidateTier.GRAND_PRIX, weight = 1) }
                }
                EffectiveRuleMode.VS_DAY -> {
                    val originTheme = themesByStage[cleanFrom]
                    val ranked = mutableListOf<RankedCandidate>()
                    for (t in tracks) {
                        val isExclusive = (originTheme != null && originTheme in t.dayThemes) ||
                            (cleanFrom in t.dayCourses)
                        val isLikelyBoost = (originTheme != null && originTheme in t.likelyDayThemes) ||
                            (cleanFrom in t.likelyDayCourses)
                        when {
                            isExclusive -> ranked += RankedCandidate(t.jukeboxCode, CandidateTier.EXCLUSIVE_ORDER, weight = 4)
                            isLikelyBoost -> ranked += RankedCandidate(t.jukeboxCode, CandidateTier.THEMED_BOOSTED, weight = 4)
                            t.anyOriginDay -> ranked += RankedCandidate(t.jukeboxCode, CandidateTier.MAIN_ORDER, weight = 1)
                        }
                    }
                    ranked.sortedBy { it.tier.ordinal }
                }
                EffectiveRuleMode.VS_NIGHT -> {
                    tracks
                        .filter { it.anyOriginNight }
                        .map {
                            RankedCandidate(
                                jukeboxCode = it.jukeboxCode,
                                tier = CandidateTier.NIGHT_ORDER,
                                weight = it.nightWeight.coerceAtLeast(1),
                            )
                        }
                }
            }
        }

        companion object {
            fun parse(text: String): RouteMusicCatalog? = runCatching {
                val root = JSONObject(text)
                val schemaVersion = root.getInt("schemaVersion")
                require(schemaVersion == 1)
                val themesByStage = linkedMapOf<String, String>()
                val themesObj = root.optJSONObject("themes")
                if (themesObj != null) {
                    val keys = themesObj.keys()
                    while (keys.hasNext()) {
                        val themeName = keys.next()
                        val arr = themesObj.getJSONArray(themeName)
                        for (i in 0 until arr.length()) {
                            themesByStage[arr.getString(i).trim().removeSuffix("_00")] = themeName
                        }
                    }
                }
                val exclArr = root.optJSONArray("exclusiveOriginCourses") ?: JSONArray()
                val exclusiveCourses = (0 until exclArr.length())
                    .map { exclArr.getString(it).trim().removeSuffix("_00") }
                    .toSet()

                val specialMap = linkedMapOf<EdgeKey, SpecialEdgeRule>()
                val specialArr = root.optJSONArray("specialEdges") ?: JSONArray()
                for (i in 0 until specialArr.length()) {
                    val o = specialArr.getJSONObject(i)
                    val from = o.getString("from").trim().removeSuffix("_00")
                    val to = o.getString("to").trim().removeSuffix("_00")
                    specialMap[EdgeKey(from, to)] = SpecialEdgeRule(
                        from = from,
                        to = to,
                        stem = o.getString("stem"),
                        title = o.getString("title"),
                    )
                }

                val gpMap = linkedMapOf<EdgeKey, List<String>>()
                val gpArr = root.optJSONArray("gpEdges") ?: JSONArray()
                for (i in 0 until gpArr.length()) {
                    val o = gpArr.getJSONObject(i)
                    val from = o.getString("from").trim().removeSuffix("_00")
                    val to = o.getString("to").trim().removeSuffix("_00")
                    val cArr = o.getJSONArray("candidates")
                    val cands = (0 until cArr.length()).map { cArr.getString(it).trim() }.filter { it.isNotEmpty() }.distinct()
                    gpMap[EdgeKey(from, to)] = cands
                }

                val tracksArr = root.optJSONArray("tracks") ?: JSONArray()
                val trackRules = (0 until tracksArr.length()).map { i ->
                    val o = tracksArr.getJSONObject(i)
                    fun strSet(key: String): Set<String> {
                        val a = o.optJSONArray(key) ?: return emptySet()
                        return (0 until a.length()).map { a.getString(it).trim() }.filter { it.isNotEmpty() }.toSet()
                    }
                    val anyNight = o.optBoolean("anyOriginNight", false)
                    TrackRule(
                        jukeboxCode = o.getString("jukeboxCode").trim(),
                        anyOriginDay = o.optBoolean("anyOriginDay", false),
                        anyOriginNight = anyNight,
                        dayThemes = strSet("dayThemes"),
                        dayCourses = strSet("dayCourses"),
                        likelyDayThemes = strSet("likelyDayThemes"),
                        likelyDayCourses = strSet("likelyDayCourses"),
                        nightWeight = o.optInt("nightWeight", if (anyNight) 1 else 0),
                        inRacePool = o.optBoolean("inRacePool", false),
                    )
                }

                RouteMusicCatalog(
                    schemaVersion = schemaVersion,
                    themesByStage = themesByStage,
                    exclusiveOriginCourses = exclusiveCourses,
                    specialEdges = specialMap,
                    gpEdges = gpMap,
                    tracks = trackRules,
                )
            }.getOrNull()
        }
    }

    sealed interface EdgeMusicResolution {
        val edge: StageConnections.Edge?

        data class KnownPoolComplete(
            override val edge: StageConnections.Edge?,
            val candidates: List<JukeboxCatalog.TrackItem>,
            val selectedOverride: JukeboxCatalog.TrackItem?,
            val candidateTiers: Map<String, CandidateTier> = emptyMap(),
            val candidateWeights: List<Int> = List(candidates.size) { 1 },
            val ruleModeUsed: EffectiveRuleMode? = null,
            val originBiomeKey: String? = null,
        ) : EdgeMusicResolution

        data class KnownPoolIncomplete(
            override val edge: StageConnections.Edge?,
            val missingCandidateRefs: List<String>,
        ) : EdgeMusicResolution

        data class SpecialRoadApproach(
            override val edge: StageConnections.Edge?,
            val stem: String,
            val title: String,
        ) : EdgeMusicResolution

        data class KnockoutTourDirect(
            override val edge: StageConnections.Edge?,
        ) : EdgeMusicResolution

        data class CustomSubstitute(
            override val edge: StageConnections.Edge?,
            val track: JukeboxCatalog.TrackItem,
        ) : EdgeMusicResolution

        data class SubstituteMissing(
            override val edge: StageConnections.Edge?,
            val missingRef: String,
        ) : EdgeMusicResolution

        data class MissingCandidateData(
            override val edge: StageConnections.Edge?,
        ) : EdgeMusicResolution

        data class DisabledByUser(
            override val edge: StageConnections.Edge?,
        ) : EdgeMusicResolution
    }

    data class RouteLegInspection(
        val index: Int,
        val from: String,
        val to: String,
        val edge: StageConnections.Edge?,
        val connectionValid: Boolean,
        val musicEnabled: Boolean,
        val resolution: EdgeMusicResolution,
    )

    sealed interface BlockingCause {
        data object TooFewStops : BlockingCause
        data object NoStops : BlockingCause
        data class InvalidEdges(val edges: List<EdgeKey>) : BlockingCause
        data class IncompletePools(val edges: List<EdgeKey>) : BlockingCause
        data class MissingSubstitutes(val edges: List<EdgeKey>) : BlockingCause
        data class UnplayableStages(val stageCodes: List<String>) : BlockingCause
    }

    data class RouteValidationReport(
        val canPlay: Boolean,
        val invalidTopologyEdges: List<EdgeKey>,
        val unplayableStops: List<String>,
        val incompletePoolEdges: List<EdgeKey>,
        val missingSubstituteEdges: List<EdgeKey>,
        val legs: List<RouteLegInspection>,
        val blockingReason: String? = null,
        val blockingCause: BlockingCause? = null,
    )

    fun effectiveModeForOfficial(
        routeKind: OfficialRouteKind,
        mode: RoadMusicRuleMode,
        isNightNow: Boolean = isCurrentLocalNight(),
    ): EffectiveRuleMode =
        when (mode) {
            RoadMusicRuleMode.AUTO -> when (routeKind) {
                OfficialRouteKind.GRAND_PRIX -> EffectiveRuleMode.GRAND_PRIX
                OfficialRouteKind.RALLY -> EffectiveRuleMode.KNOCKOUT_DIRECT
            }
            RoadMusicRuleMode.GRAND_PRIX -> EffectiveRuleMode.GRAND_PRIX
            RoadMusicRuleMode.VS_AUTO -> if (isNightNow) EffectiveRuleMode.VS_NIGHT else EffectiveRuleMode.VS_DAY
            RoadMusicRuleMode.VS_DAY -> EffectiveRuleMode.VS_DAY
            RoadMusicRuleMode.VS_NIGHT -> EffectiveRuleMode.VS_NIGHT
            RoadMusicRuleMode.KNOCKOUT_DIRECT -> EffectiveRuleMode.KNOCKOUT_DIRECT
        }

    fun effectiveModeForCustom(
        mode: RoadMusicRuleMode,
        isNightNow: Boolean = isCurrentLocalNight(),
    ): EffectiveRuleMode =
        when (mode) {
            RoadMusicRuleMode.AUTO,
            RoadMusicRuleMode.VS_DAY -> EffectiveRuleMode.VS_DAY
            RoadMusicRuleMode.VS_AUTO -> if (isNightNow) EffectiveRuleMode.VS_NIGHT else EffectiveRuleMode.VS_DAY
            RoadMusicRuleMode.VS_NIGHT -> EffectiveRuleMode.VS_NIGHT
            RoadMusicRuleMode.GRAND_PRIX -> EffectiveRuleMode.GRAND_PRIX
            RoadMusicRuleMode.KNOCKOUT_DIRECT -> EffectiveRuleMode.KNOCKOUT_DIRECT
        }

    /** Auto-identifies installed Jukebox track strictly by verified metadata (#83). */
    fun matchJukeboxItem(
        ref: String,
        installedItems: List<JukeboxCatalog.TrackItem>,
        readableCheck: (JukeboxCatalog.TrackItem) -> Boolean = { true },
    ): JukeboxCatalog.TrackItem? {
        val norm = ref.trim()
        if (norm.isEmpty()) return null
        val lower = norm.lowercase(Locale.ROOT)
        val match = installedItems.firstOrNull { item ->
            item.track.key == norm ||
                item.track.primary.sha256.equals(lower, ignoreCase = true) ||
                item.jukeboxCode?.equals(norm, ignoreCase = true) == true ||
                item.track.title.equals(norm, ignoreCase = true) ||
                item.searchAliases.any { it.equals(norm, ignoreCase = true) }
        } ?: return null
        return match.takeIf(readableCheck)
    }

    fun buildPureFreeRoamLegPlans(
        edges: List<EdgeKey>,
        config: PureFreeRoamConfig,
        ruleMode: RoadMusicRuleMode,
        officialKind: OfficialRouteKind? = null,
        edgeOverrides: Map<Int, EdgeFreeRoamOverride> = emptyMap(),
        disabledEdgeIndices: Set<Int> = emptySet(),
        wikiCatalog: RouteMusicCatalog? = null,
        isNightNow: Boolean = isCurrentLocalNight(),
    ): List<PureFreeRoamLegPlan> {
        if (edges.isEmpty()) return emptyList()
        val cleanConfig = config.normalized()
        val nominalRule = if (officialKind != null) {
            effectiveModeForOfficial(officialKind, ruleMode, isNightNow)
        } else {
            effectiveModeForCustom(ruleMode, isNightNow)
        }
        val vsFallbackRule = if (isNightNow) EffectiveRuleMode.VS_NIGHT else EffectiveRuleMode.VS_DAY
        val useGameFreeRoamPool = cleanConfig.trackPool == PureFreeRoamTrackPool.GAME_FREE_ROAM
        val plans = ArrayList<PureFreeRoamLegPlan>(edges.size)
        for ((index, rawEdge) in edges.withIndex()) {
            if (index in disabledEdgeIndices) continue
            val from = rawEdge.canonicalFrom
            val to = rawEdge.canonicalTo
            var effectiveRule = if (nominalRule == EffectiveRuleMode.KNOCKOUT_DIRECT) vsFallbackRule else nominalRule
            var ranked = if (useGameFreeRoamPool) {
                emptyList()
            } else {
                wikiCatalog?.resolveRuleCandidates(from, to, effectiveRule).orEmpty()
            }
            if (!useGameFreeRoamPool && ranked.isEmpty() && effectiveRule == EffectiveRuleMode.GRAND_PRIX) {
                effectiveRule = vsFallbackRule
                ranked = wikiCatalog?.resolveRuleCandidates(from, to, effectiveRule).orEmpty()
            }
            val override = edgeOverrides[index]?.normalized()
            val trackCount = (override?.trackCountOverride ?: cleanConfig.defaultTracksPerLeg)
                .coerceIn(PURE_FREE_ROAM_MIN_TRACKS_PER_LEG, PURE_FREE_ROAM_MAX_TRACKS_PER_LEG)
            val minutes = (override?.durationMinutesOverride ?: cleanConfig.defaultMinutesPerLeg)
                .coerceIn(PURE_FREE_ROAM_MIN_MINUTES_PER_LEG, PURE_FREE_ROAM_MAX_MINUTES_PER_LEG)
            plans += PureFreeRoamLegPlan(
                edgeIndex = index,
                from = from,
                to = to,
                effectiveRule = effectiveRule,
                controlMode = cleanConfig.controlMode,
                trackCount = trackCount,
                durationMs = minutes.toLong() * 60_000L,
                rankedCandidates = ranked,
                trackPool = cleanConfig.trackPool,
                freeRoamMode = cleanConfig.freeRoamMode,
            )
        }
        return plans
    }

    fun buildPureFreeRoamLegPlans(
        route: CustomRoute,
        wikiCatalog: RouteMusicCatalog?,
        ruleModeOverride: RoadMusicRuleMode? = null,
        configOverride: PureFreeRoamConfig? = null,
        isNightNow: Boolean = isCurrentLocalNight(),
    ): List<PureFreeRoamLegPlan> {
        val stopCodes = route.stops.map { it.stageCode.trim().removeSuffix("_00") }
        if (stopCodes.size < 2) return emptyList()
        val edges = (0 until stopCodes.lastIndex).map { idx -> EdgeKey(stopCodes[idx], stopCodes[idx + 1]) }
        val overrides = buildMap<Int, EdgeFreeRoamOverride> {
            for (idx in edges.indices) {
                val cfg = route.edges.getOrNull(idx) ?: continue
                val ov = EdgeFreeRoamOverride(
                    trackCountOverride = cfg.freeRoamTrackCountOverride,
                    durationMinutesOverride = cfg.freeRoamDurationMinutesOverride,
                ).normalized()
                if (!ov.isEmpty) put(idx, ov)
            }
        }
        val disabled = edges.indices.filterTo(mutableSetOf()) { idx ->
            route.edges.getOrNull(idx)?.musicEnabled == false
        }
        return buildPureFreeRoamLegPlans(
            edges = edges,
            config = configOverride ?: route.pureFreeRoamConfig,
            ruleMode = ruleModeOverride ?: route.roadRuleMode,
            officialKind = null,
            edgeOverrides = overrides,
            disabledEdgeIndices = disabled,
            wikiCatalog = wikiCatalog,
            isNightNow = isNightNow,
        )
    }

    fun buildPureFreeRoamLegPlans(
        route: OfficialRoute,
        config: PureFreeRoamConfig,
        ruleMode: RoadMusicRuleMode = DEFAULT_ROAD_RULE_MODE,
        edgeOverrides: Map<Int, EdgeFreeRoamOverride> = emptyMap(),
        wikiCatalog: RouteMusicCatalog?,
        isNightNow: Boolean = isCurrentLocalNight(),
    ): List<PureFreeRoamLegPlan> {
        val stopCodes = route.stops.map { it.stageCode.trim().removeSuffix("_00") }
        if (stopCodes.size < 2) return emptyList()
        val edges = (0 until stopCodes.lastIndex).map { idx -> EdgeKey(stopCodes[idx], stopCodes[idx + 1]) }
        return buildPureFreeRoamLegPlans(
            edges = edges,
            config = config,
            ruleMode = ruleMode,
            officialKind = route.kind,
            edgeOverrides = edgeOverrides,
            disabledEdgeIndices = emptySet(),
            wikiCatalog = wikiCatalog,
            isNightNow = isNightNow,
        )
    }

    fun inspectCustomRoute(
        route: CustomRoute,
        connections: StageConnections.Catalog?,
        pool: CandidatePool,
        installedJukebox: List<JukeboxCatalog.TrackItem>,
        isStagePlayable: (String) -> Boolean,
        isStageHidden: (String) -> Boolean,
        isJukeboxReadable: (JukeboxCatalog.TrackItem) -> Boolean = { true },
        wikiCatalog: RouteMusicCatalog? = null,
        ruleModeOverride: RoadMusicRuleMode? = null,
        isNightNow: Boolean = isCurrentLocalNight(),
        pureFreeRoamEnabled: Boolean = route.pureFreeRoamConfig.enabled,
        pureFreeRoamTrackPool: PureFreeRoamTrackPool = route.pureFreeRoamConfig.trackPool,
    ): RouteValidationReport {
        val stopCodes = route.stops.map { it.stageCode.removeSuffix("_00") }
        val unplayable = stopCodes.filter {
            isStageHidden(it) || (!pureFreeRoamEnabled && !isStagePlayable(it))
        }.distinct()
        val useGameFreeRoamPool = pureFreeRoamEnabled && pureFreeRoamTrackPool == PureFreeRoamTrackPool.GAME_FREE_ROAM
        val invalidEdges = mutableListOf<EdgeKey>()
        val incompletePools = mutableListOf<EdgeKey>()
        val missingSubstitutes = mutableListOf<EdgeKey>()
        val legs = mutableListOf<RouteLegInspection>()
        val rawEffectiveMode = effectiveModeForCustom(ruleModeOverride ?: route.roadRuleMode, isNightNow)
        val effectiveMode = if (pureFreeRoamEnabled && rawEffectiveMode == EffectiveRuleMode.KNOCKOUT_DIRECT) {
            if (isNightNow) EffectiveRuleMode.VS_NIGHT else EffectiveRuleMode.VS_DAY
        } else {
            rawEffectiveMode
        }

        if (stopCodes.size >= 2) {
            for (i in 0 until stopCodes.lastIndex) {
                val from = stopCodes[i]
                val to = stopCodes[i + 1]
                val edgeCfg = route.edges.getOrNull(i)
                val conn = connections?.between(from, to)
                val connValid = connections != null && conn != null && from != to
                if (!connValid) {
                    invalidEdges += EdgeKey(from, to)
                }
                val musicEnabled = edgeCfg?.musicEnabled ?: true
                val resolution = resolveEdge(
                    from = from,
                    to = to,
                    edge = conn,
                    musicEnabled = musicEnabled,
                    selectedRef = edgeCfg?.selectedJukeboxKey,
                    allowCustomSubstitute = true,
                    pool = pool,
                    installedJukebox = installedJukebox,
                    isJukeboxReadable = isJukeboxReadable,
                    wikiCatalog = wikiCatalog,
                    effectiveRuleMode = effectiveMode,
                )
                if (!useGameFreeRoamPool) {
                    if (resolution is EdgeMusicResolution.KnownPoolIncomplete) {
                        incompletePools += EdgeKey(from, to)
                    } else if (resolution is EdgeMusicResolution.SubstituteMissing) {
                        missingSubstitutes += EdgeKey(from, to)
                    }
                }
                legs += RouteLegInspection(i, from, to, conn, connValid, musicEnabled, resolution)
            }
        }

        val hasReadableJukebox = !useGameFreeRoamPool || installedJukebox.any(isJukeboxReadable)
        val cause: BlockingCause? = when {
            stopCodes.size < 2 -> BlockingCause.TooFewStops
            invalidEdges.isNotEmpty() -> BlockingCause.InvalidEdges(invalidEdges)
            incompletePools.isNotEmpty() -> BlockingCause.IncompletePools(incompletePools)
            missingSubstitutes.isNotEmpty() -> BlockingCause.MissingSubstitutes(missingSubstitutes)
            unplayable.isNotEmpty() -> BlockingCause.UnplayableStages(unplayable)
            else -> null
        }
        val missingGameFreeRoamJukebox = cause == null && useGameFreeRoamPool && !hasReadableJukebox
        val reason = when {
            cause != null -> when (cause) {
                BlockingCause.TooFewStops -> "Route needs at least 2 stops."
                is BlockingCause.InvalidEdges -> "Invalid road connection: ${cause.edges.joinToString { "${it.from}->${it.to}" }}."
                is BlockingCause.IncompletePools -> "Missing required road music on ${cause.edges.joinToString { "${it.from}->${it.to}" }}."
                is BlockingCause.MissingSubstitutes -> "Custom substitute track unavailable on ${cause.edges.joinToString { "${it.from}->${it.to}" }}."
                is BlockingCause.UnplayableStages -> "Missing or locked course audio: ${cause.stageCodes.joinToString()}."
                else -> null
            }
            missingGameFreeRoamJukebox -> "No playable Jukebox tracks installed for in-game Free Roam playback."
            else -> null
        }

        return RouteValidationReport(
            canPlay = cause == null && !missingGameFreeRoamJukebox,
            invalidTopologyEdges = invalidEdges,
            unplayableStops = unplayable,
            incompletePoolEdges = incompletePools,
            missingSubstituteEdges = missingSubstitutes,
            legs = legs,
            blockingReason = reason,
            blockingCause = cause,
        )
    }

    fun inspectOfficialRoute(
        route: OfficialRoute,
        connections: StageConnections.Catalog?,
        pool: CandidatePool,
        installedJukebox: List<JukeboxCatalog.TrackItem>,
        sessionOverrides: Map<Int, String> = emptyMap(),
        isStagePlayable: (String) -> Boolean,
        isStageHidden: (String) -> Boolean,
        isJukeboxReadable: (JukeboxCatalog.TrackItem) -> Boolean = { true },
        wikiCatalog: RouteMusicCatalog? = null,
        ruleMode: RoadMusicRuleMode = RoadMusicRuleMode.AUTO,
        isNightNow: Boolean = isCurrentLocalNight(),
        pureFreeRoamEnabled: Boolean = false,
        pureFreeRoamTrackPool: PureFreeRoamTrackPool = PureFreeRoamTrackPool.ROUTE_RULES,
    ): RouteValidationReport {
        val stopCodes = route.stops.map { it.stageCode.removeSuffix("_00") }
        val unplayable = stopCodes.filter {
            isStageHidden(it) || (!pureFreeRoamEnabled && !isStagePlayable(it))
        }.distinct()
        val useGameFreeRoamPool = pureFreeRoamEnabled && pureFreeRoamTrackPool == PureFreeRoamTrackPool.GAME_FREE_ROAM
        val incompletePools = mutableListOf<EdgeKey>()
        val legs = mutableListOf<RouteLegInspection>()
        val rawEffectiveMode = effectiveModeForOfficial(route.kind, ruleMode, isNightNow)
        val effectiveMode = if (pureFreeRoamEnabled && rawEffectiveMode == EffectiveRuleMode.KNOCKOUT_DIRECT) {
            if (isNightNow) EffectiveRuleMode.VS_NIGHT else EffectiveRuleMode.VS_DAY
        } else {
            rawEffectiveMode
        }

        for (i in 0 until stopCodes.lastIndex) {
            val from = stopCodes[i]
            val to = stopCodes[i + 1]
            val conn = connections?.between(from, to)
            val resolution = resolveEdge(
                from = from,
                to = to,
                edge = conn,
                musicEnabled = true,
                selectedRef = sessionOverrides[i],
                allowCustomSubstitute = false, // Official routes only pick valid pool candidates (#71)
                pool = pool,
                installedJukebox = installedJukebox,
                isJukeboxReadable = isJukeboxReadable,
                wikiCatalog = wikiCatalog,
                effectiveRuleMode = effectiveMode,
            )
            if (!useGameFreeRoamPool && resolution is EdgeMusicResolution.KnownPoolIncomplete) {
                incompletePools += EdgeKey(from, to)
            }
            legs += RouteLegInspection(i, from, to, conn, connectionValid = true, musicEnabled = true, resolution = resolution)
        }

        val hasReadableJukebox = !useGameFreeRoamPool || installedJukebox.any(isJukeboxReadable)
        val cause: BlockingCause? = when {
            stopCodes.isEmpty() -> BlockingCause.NoStops
            incompletePools.isNotEmpty() -> BlockingCause.IncompletePools(incompletePools)
            unplayable.isNotEmpty() -> BlockingCause.UnplayableStages(unplayable)
            else -> null
        }
        val missingGameFreeRoamJukebox = cause == null && useGameFreeRoamPool && !hasReadableJukebox
        val reason = when {
            cause != null -> when (cause) {
                BlockingCause.NoStops -> "This route has no stops."
                is BlockingCause.IncompletePools -> "Missing required road music on ${cause.edges.joinToString { "${it.from}->${it.to}" }}."
                is BlockingCause.UnplayableStages -> "Missing or locked course audio: ${cause.stageCodes.joinToString()}."
                else -> null
            }
            missingGameFreeRoamJukebox -> "No playable Jukebox tracks installed for in-game Free Roam playback."
            else -> null
        }

        return RouteValidationReport(
            canPlay = cause == null && !missingGameFreeRoamJukebox,
            invalidTopologyEdges = emptyList(),
            unplayableStops = unplayable,
            incompletePoolEdges = incompletePools,
            missingSubstituteEdges = emptyList(),
            legs = legs,
            blockingReason = reason,
            blockingCause = cause,
        )
    }

    private fun resolveEdge(
        from: String,
        to: String,
        edge: StageConnections.Edge?,
        musicEnabled: Boolean,
        selectedRef: String?,
        allowCustomSubstitute: Boolean,
        pool: CandidatePool,
        installedJukebox: List<JukeboxCatalog.TrackItem>,
        isJukeboxReadable: (JukeboxCatalog.TrackItem) -> Boolean,
        wikiCatalog: RouteMusicCatalog?,
        effectiveRuleMode: EffectiveRuleMode,
    ): EdgeMusicResolution {
        if (effectiveRuleMode == EffectiveRuleMode.KNOCKOUT_DIRECT) {
            return EdgeMusicResolution.KnockoutTourDirect(edge)
        }
        val poolRefs = pool.candidatesFor(from, to)
        if (!poolRefs.isNullOrEmpty()) {
            // Known candidate pool from pack: #82 requires ALL candidates to exist & be readable, even if toggled off.
            val matched = mutableListOf<JukeboxCatalog.TrackItem>()
            val missing = mutableListOf<String>()
            for (ref in poolRefs) {
                val item = matchJukeboxItem(ref, installedJukebox, isJukeboxReadable)
                if (item != null) matched += item else missing += ref
            }
            if (missing.isNotEmpty()) {
                return EdgeMusicResolution.KnownPoolIncomplete(edge, missing)
            }
            if (!musicEnabled) {
                return EdgeMusicResolution.DisabledByUser(edge)
            }
            val selected = selectedRef?.let { ref ->
                matched.firstOrNull {
                    it.track.key == ref ||
                        it.track.primary.sha256.equals(ref, ignoreCase = true) ||
                        it.jukeboxCode?.equals(ref, ignoreCase = true) == true
                }
            }
            return EdgeMusicResolution.KnownPoolComplete(edge, matched, selected)
        }

        if (wikiCatalog != null) {
            val special = wikiCatalog.specialFor(from, to)
            if (special != null) {
                if (!musicEnabled) return EdgeMusicResolution.DisabledByUser(edge)
                if (allowCustomSubstitute && !selectedRef.isNullOrBlank()) {
                    val found = matchJukeboxItem(selectedRef, installedJukebox, isJukeboxReadable)
                        ?: return EdgeMusicResolution.SubstituteMissing(edge, selectedRef)
                    return EdgeMusicResolution.CustomSubstitute(edge, found)
                }
                return EdgeMusicResolution.SpecialRoadApproach(edge, special.stem, special.title)
            }

            val ranked = wikiCatalog.resolveRuleCandidates(from, to, effectiveRuleMode)
            if (ranked.isNotEmpty()) {
                val matchedItems = mutableListOf<JukeboxCatalog.TrackItem>()
                val matchedTiers = linkedMapOf<String, CandidateTier>()
                val matchedWeights = mutableListOf<Int>()
                for (cand in ranked) {
                    val item = matchJukeboxItem(cand.jukeboxCode, installedJukebox, isJukeboxReadable)
                    if (item != null && !matchedTiers.containsKey(item.key)) {
                        matchedItems += item
                        matchedTiers[item.key] = cand.tier
                        matchedWeights += cand.weight
                    }
                }
                if (matchedItems.isNotEmpty()) {
                    if (!musicEnabled) {
                        return EdgeMusicResolution.DisabledByUser(edge)
                    }
                    val selectedInPool = selectedRef?.let { ref ->
                        matchedItems.firstOrNull {
                            it.track.key == ref ||
                                it.track.primary.sha256.equals(ref, ignoreCase = true) ||
                                it.jukeboxCode?.equals(ref, ignoreCase = true) == true
                        }
                    }
                    if (selectedInPool == null && allowCustomSubstitute && !selectedRef.isNullOrBlank()) {
                        val sub = matchJukeboxItem(selectedRef, installedJukebox, isJukeboxReadable)
                            ?: return EdgeMusicResolution.SubstituteMissing(edge, selectedRef)
                        return EdgeMusicResolution.CustomSubstitute(edge, sub)
                    }
                    val originBiome = if (effectiveRuleMode == EffectiveRuleMode.VS_DAY) {
                        wikiCatalog.originBiomeKey(from)
                    } else {
                        null
                    }
                    return EdgeMusicResolution.KnownPoolComplete(
                        edge = edge,
                        candidates = matchedItems,
                        selectedOverride = selectedInPool,
                        candidateTiers = matchedTiers,
                        candidateWeights = matchedWeights,
                        ruleModeUsed = effectiveRuleMode,
                        originBiomeKey = originBiome,
                    )
                }
            }
        }

        if (!musicEnabled) {
            return EdgeMusicResolution.DisabledByUser(edge)
        }
        if (allowCustomSubstitute && !selectedRef.isNullOrBlank()) {
            val found = matchJukeboxItem(selectedRef, installedJukebox, isJukeboxReadable)
                ?: return EdgeMusicResolution.SubstituteMissing(edge, selectedRef)
            return EdgeMusicResolution.CustomSubstitute(edge, found)
        }
        return EdgeMusicResolution.MissingCandidateData(edge)
    }

    fun loadInternalPool(filesDir: File): CandidatePool {
        val file = File(filesDir, "road-jukebox-pool.json")
        if (!file.isFile) return CandidatePool()
        return runCatching { CandidatePool.parse(file.readText()) }.getOrNull() ?: CandidatePool()
    }
}
