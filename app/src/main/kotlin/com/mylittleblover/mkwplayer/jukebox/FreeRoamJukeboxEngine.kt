package com.mylittleblover.mkwplayer.jukebox

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.util.Locale
import kotlin.random.Random

/**
 * Authentic Mario Kart World v1.8.0 Jukebox & Free Roam track selection engine.
 *
 * Implements the exact selection state machine reverse-engineered from `SoundBgmMgr` (`main.bin`):
 * - 13 official `SoundJukeBoxPlaylist` ordered slot tables (1,102 total slots across 227 tracks).
 * - Random starting cursor per playlist (`loadPlaylist`, `0x003d3030`) with sequential step
 *   `cursor = (cursor + 1) % size` (`tryPickFromPlaylistWithHistory`, `0x0243f300`), preserving
 *   the designer-authored 4x/3x/2x/1x weights and minimum same-track spacing (e.g. 22 slots in `Default`).
 * - Recent history ring buffer of depth `JukeBox_HistoryCount = 9` with up to 22 retries
 *   (`0x0243ee14..0x0243ef60`) and `TypeAll` fallback for biome playlists.
 * - 24-hour clock rule (`WorldInfo::evalTimeOfDayEnum`, `0x007cd400`):
 *   Day = 03:00–19:00 (`Morning` 03–05 + `Noon` 05–19),
 *   Night = 19:00–03:00 (`Evening` 19–21 + `Night` 21–03).
 * - 8 biome first-song playlists throttled by `JukeBox_SelectListInterval = 4` (every 5th biome
 *   request switches to `TypeAll` and resets counter to 4; night mode overrides biome lists with
 *   `24040117_J25YUWHR` / `レース夜`).
 * - Free Roam inter-track natural breathing interval `JukeBox_FreeRunInterval = 1.5s` (`1500 ms`).
 */
internal object FreeRoamJukeboxCatalog {

    const val PLAYLIST_DEFAULT = "Default"
    const val PLAYLIST_NIGHT = "Night"
    const val PLAYLIST_RACE = "Race"
    const val PLAYLIST_RACE_NIGHT = "24040117_J25YUWHR"
    const val PLAYLIST_TYPE_ALL = "TypeAll"
    const val PLAYLIST_SOLO_ALL = "__SoloAllFreeRoam"
    const val PLAYLIST_FREEROAM_ONLY = "__FreeRoamOnly"

    enum class TimeOfDay(val enumId: Int) {
        MORNING(0), // 03:00–05:00 (Day bucket)
        NOON(1),    // 05:00–19:00 (Day bucket)
        EVENING(2), // 19:00–21:00 (Night bucket)
        NIGHT(3),   // 21:00–03:00 (Night bucket)
        ;

        val isNightBucket: Boolean get() = (enumId and 1.inv()) == 2
    }

    enum class FreeRoamMode {
        AUTO_CLOCK,
        SOLO_DAY,
        SOLO_NIGHT,
        SOLO_ALL,
        FREEROAM_ONLY,
        ONLINE_DAY,
        ONLINE_NIGHT,
        ;

        val isInGameFreeRoamLibrary: Boolean
            get() = FreeRoamJukeboxCatalog.isInGameFreeRoamLibrary(this)

        companion object {
            val IN_GAME_FREE_ROAM_MODES: List<FreeRoamMode> = listOf(
                AUTO_CLOCK,
                SOLO_DAY,
                SOLO_NIGHT,
                SOLO_ALL,
                FREEROAM_ONLY,
            )

            val ONLINE_RACE_MODES: List<FreeRoamMode> = listOf(
                ONLINE_DAY,
                ONLINE_NIGHT,
            )

            fun fromStored(raw: String?): FreeRoamMode =
                entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: AUTO_CLOCK
        }
    }

    /**
     * True for the 5 authentic in-game solo Free Roam playlists (`Default`, `Night`, merged
     * `Default ∪ Night`, and the 93 `IsFreerunOnly` exclusive tracks); false for online waiting /
     * interstage race playlists (`Race`, `24040117_J25YUWHR`).
     */
    fun isInGameFreeRoamLibrary(mode: FreeRoamMode): Boolean = when (mode) {
        FreeRoamMode.AUTO_CLOCK,
        FreeRoamMode.SOLO_DAY,
        FreeRoamMode.SOLO_NIGHT,
        FreeRoamMode.SOLO_ALL,
        FreeRoamMode.FREEROAM_ONLY -> true
        FreeRoamMode.ONLINE_DAY,
        FreeRoamMode.ONLINE_NIGHT -> false
    }

    fun coerceInGameFreeRoamMode(mode: FreeRoamMode): FreeRoamMode =
        if (isInGameFreeRoamLibrary(mode)) mode else FreeRoamMode.AUTO_CLOCK

    /**
     * Deterministically interleaves [primary] (e.g. `Default`, 581 slots) and [secondary] (e.g.
     * `Night`, 137 slots) proportionally by their sizes (~4.24:1) while preserving each source
     * list's exact slot multiplicity and internal order.
     */
    fun interleaveWeightedSlots(primary: List<String>, secondary: List<String>): List<String> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val na = primary.size
        val nb = secondary.size
        val out = ArrayList<String>(na + nb)
        var ia = 0
        var ib = 0
        while (ia < na || ib < nb) {
            val pickSecondary = ib < nb && (ia >= na || (2L * ib + 1L) * na < (2L * ia + 1L) * nb)
            if (pickSecondary) {
                out.add(secondary[ib++])
            } else {
                out.add(primary[ia++])
            }
        }
        return out
    }

    data class Settings(
        val historyCount: Int = 9,
        val selectListInterval: Int = 4,
        val maxHistoryRetries: Int = 22,
        val freeRunIntervalMs: Long = 1_500L,
        val toNextFadeMs: Long = 300L,
        val toNextDelayMs: Long = 500L,
        val freeRunVolume: Float = 0.70f,
        val freeRunVolumeOnBoost: Float = 0.7854f,
        val boostVolume: Float = 1.412f,
        val stageConnectFadeOutMs: Long = 3_000L,
        val stageConnectIntervalToStageMs: Long = 2_400L,
        val stageConnectStopByArrivalSec: Double = 20.0,
        val dayStartHour: Int = 3,
        val nightStartHour: Int = 19,
    )

    data class Playlist(
        val id: String,
        val internalNameJp: String,
        val isBiomeFirst: Boolean,
        val startStageTypes: Set<String>,
        val slots: List<String>,
    ) {
        val uniqueCodes: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { slots.toSet() }
    }

    data class Data(
        val schemaVersion: Int = 1,
        val settings: Settings = Settings(),
        val freerunOnlyCodes: Set<String> = emptySet(),
        val raceOnlyCodes: Set<String> = emptySet(),
        val playlists: List<Playlist> = emptyList(),
    ) {
        val playlistsById: Map<String, Playlist> = playlists.associateBy { it.id }

        /**
         * Synthetic in-game Free Roam playlists derived from the raw romfs `Default` & `Night`
         * slot tables (`__SoloAllFreeRoam`: 718 slots / 202 unique tracks; `__FreeRoamOnly`:
         * 369 slots / 93 `IsFreerunOnly` tracks). Kept separate from [playlists] so raw romfs
         * counts (13 playlists / 1,102 slots) remain untouched.
         */
        val virtualPlaylists: List<Playlist> = buildList {
            val defaultSlots = playlistsById[PLAYLIST_DEFAULT]?.slots.orEmpty()
            val nightSlots = playlistsById[PLAYLIST_NIGHT]?.slots.orEmpty()
            val soloAllSlots = interleaveWeightedSlots(defaultSlots, nightSlots)
            if (soloAllSlots.isNotEmpty()) {
                add(
                    Playlist(
                        id = PLAYLIST_SOLO_ALL,
                        internalNameJp = "デフォルト+夜",
                        isBiomeFirst = false,
                        startStageTypes = emptySet(),
                        slots = soloAllSlots,
                    ),
                )
                val freeRoamOnlySlots = soloAllSlots.filter { it in freerunOnlyCodes }
                if (freeRoamOnlySlots.isNotEmpty()) {
                    add(
                        Playlist(
                            id = PLAYLIST_FREEROAM_ONLY,
                            internalNameJp = "フリーラン専用",
                            isBiomeFirst = false,
                            startStageTypes = emptySet(),
                            slots = freeRoamOnlySlots,
                        ),
                    )
                }
            }
        }

        val virtualPlaylistsById: Map<String, Playlist> = virtualPlaylists.associateBy { it.id }

        private val biomePlaylistByStage: Map<String, Playlist> = buildMap {
            for (pl in playlists) {
                if (!pl.isBiomeFirst) continue
                for (stage in pl.startStageTypes) {
                    put(stage.trim().removeSuffix("_00"), pl)
                }
            }
        }

        fun playlist(id: String): Playlist? = playlistsById[id] ?: virtualPlaylistsById[id]

        fun biomePlaylistForStartStage(stageCode: String): Playlist? =
            biomePlaylistByStage[stageCode.trim().removeSuffix("_00")]

        fun timeOfDayForHour(hourOfDay: Int): TimeOfDay {
            val h = ((hourOfDay % 24) + 24) % 24
            return when {
                h in settings.dayStartHour until 5 -> TimeOfDay.MORNING
                h in 5 until settings.nightStartHour -> TimeOfDay.NOON
                h in settings.nightStartHour until 21 -> TimeOfDay.EVENING
                else -> TimeOfDay.NIGHT
            }
        }

        fun isNightHour(hourOfDay: Int): Boolean = timeOfDayForHour(hourOfDay).isNightBucket

        fun playlistIdForFreeRoam(mode: FreeRoamMode, hourOfDay: Int): String = when (mode) {
            FreeRoamMode.AUTO_CLOCK -> if (isNightHour(hourOfDay)) PLAYLIST_NIGHT else PLAYLIST_DEFAULT
            FreeRoamMode.SOLO_DAY -> PLAYLIST_DEFAULT
            FreeRoamMode.SOLO_NIGHT -> PLAYLIST_NIGHT
            FreeRoamMode.SOLO_ALL -> PLAYLIST_SOLO_ALL
            FreeRoamMode.FREEROAM_ONLY -> PLAYLIST_FREEROAM_ONLY
            FreeRoamMode.ONLINE_DAY -> PLAYLIST_RACE
            FreeRoamMode.ONLINE_NIGHT -> PLAYLIST_RACE_NIGHT
        }

        fun resolvedPlaylistForFreeRoam(mode: FreeRoamMode, hourOfDay: Int): Playlist? =
            playlist(playlistIdForFreeRoam(mode, hourOfDay))
    }

    fun parse(text: String): Data? = runCatching {
        val root = JSONObject(text)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion == 1)
        val sObj = root.optJSONObject("settings")
        val settings = if (sObj != null) {
            Settings(
                historyCount = sObj.optInt("historyCount", 9).coerceAtLeast(0),
                selectListInterval = sObj.optInt("selectListInterval", 4).coerceAtLeast(1),
                maxHistoryRetries = sObj.optInt("maxHistoryRetries", 22).coerceAtLeast(1),
                freeRunIntervalMs = sObj.optLong("freeRunIntervalMs", 1_500L).coerceAtLeast(0L),
                toNextFadeMs = sObj.optLong("toNextFadeMs", 300L).coerceAtLeast(0L),
                toNextDelayMs = sObj.optLong("toNextDelayMs", 500L).coerceAtLeast(0L),
                freeRunVolume = sObj.optDouble("freeRunVolume", 0.70).toFloat(),
                freeRunVolumeOnBoost = sObj.optDouble("freeRunVolumeOnBoost", 0.7854).toFloat(),
                boostVolume = sObj.optDouble("boostVolume", 1.412).toFloat(),
                stageConnectFadeOutMs = sObj.optLong("stageConnectFadeOutMs", 3_000L).coerceAtLeast(0L),
                stageConnectIntervalToStageMs = sObj.optLong("stageConnectIntervalToStageMs", 2_400L).coerceAtLeast(0L),
                stageConnectStopByArrivalSec = sObj.optDouble("stageConnectStopByArrivalSec", 20.0),
                dayStartHour = sObj.optInt("dayStartHour", 3).coerceIn(0, 23),
                nightStartHour = sObj.optInt("nightStartHour", 19).coerceIn(0, 23),
            )
        } else {
            Settings()
        }

        fun stringList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }

        val playlistsArr = root.getJSONArray("playlists")
        val playlists = buildList(playlistsArr.length()) {
            for (i in 0 until playlistsArr.length()) {
                val p = playlistsArr.getJSONObject(i)
                val id = p.getString("id").trim()
                val slots = stringList(p.optJSONArray("slots"))
                require(id.isNotBlank() && slots.isNotEmpty())
                add(
                    Playlist(
                        id = id,
                        internalNameJp = p.optString("internalNameJp", ""),
                        isBiomeFirst = p.optBoolean("isBiomeFirst", false),
                        startStageTypes = stringList(p.optJSONArray("startStageTypes")).toSet(),
                        slots = slots,
                    ),
                )
            }
        }
        Data(
            schemaVersion = schemaVersion,
            settings = settings,
            freerunOnlyCodes = stringList(root.optJSONArray("freerunOnlyCodes")).toSet(),
            raceOnlyCodes = stringList(root.optJSONArray("raceOnlyCodes")).toSet(),
            playlists = playlists,
        )
    }.getOrNull()
}

/**
 * Stateful runtime cursor & history engine mirroring `SoundBgmMgr`.
 */
internal class FreeRoamJukeboxEngine(
    val data: FreeRoamJukeboxCatalog.Data,
    private val random: Random = Random.Default,
    private val hourProvider: () -> Int = { LocalTime.now().hour },
) {
    private val cursors = LinkedHashMap<String, Int>(data.playlists.size + data.virtualPlaylists.size)
    private val biomeCounters = LinkedHashMap<String, Int>()
    private val history = ArrayDeque<String>(data.settings.historyCount + 1)

    init {
        resetCursors()
    }

    /**
     * Re-initializes all 13 official playlist cursors, 8 biome counters (`SoundBgmMgr::loadPlaylist`),
     * and the synthetic in-game Free Roam virtual playlist cursors (`__SoloAllFreeRoam`, `__FreeRoamOnly`).
     */
    fun resetCursors(seedRandom: Random = random) {
        cursors.clear()
        biomeCounters.clear()
        val interval = data.settings.selectListInterval
        val minInit = (interval + 1) / 2 // (4 + 1) / 2 = 2 -> rand(2, 4) inclusive
        for (pl in data.playlists) {
            cursors[pl.id] = seedRandom.nextInt(pl.slots.size)
            if (pl.isBiomeFirst) {
                biomeCounters[pl.id] = seedRandom.nextInt(minInit, interval + 1)
            }
        }
        for (pl in data.virtualPlaylists) {
            if (pl.slots.isNotEmpty()) {
                cursors[pl.id] = seedRandom.nextInt(pl.slots.size)
            }
        }
    }

    fun clearHistory() {
        history.clear()
    }

    fun recentHistory(): List<String> = history.toList()

    fun currentCursor(playlistId: String): Int? = cursors[playlistId]

    fun currentBiomeCounter(playlistId: String): Int? = biomeCounters[playlistId]

    /** Records a track played outside the engine (e.g. manual tap) so history dedupe avoids repeating it. */
    fun recordPlayed(jukeboxCode: String?) {
        val code = jukeboxCode?.trim().orEmpty()
        if (code.isEmpty() || data.settings.historyCount <= 0) return
        history.addLast(code)
        while (history.size > data.settings.historyCount) {
            history.removeFirst()
        }
    }

    fun currentHour(): Int = hourProvider()

    fun isNightNow(): Boolean = data.isNightHour(hourProvider())

    fun activeFreeRoamPlaylist(
        mode: FreeRoamJukeboxCatalog.FreeRoamMode,
        hourOfDay: Int = hourProvider(),
    ): FreeRoamJukeboxCatalog.Playlist? = data.resolvedPlaylistForFreeRoam(mode, hourOfDay)

    /**
     * Picks one track from a Grand Prix `PrimaryCourse` edge pool (3–6 tracks), avoiding the
     * recent 9-track history ring buffer when possible (`SoundBgmMgr::setupForRace` 0x00d89260).
     */
    fun pickGrandPrixEdgeSong(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        val fresh = candidates.filter { it !in history }
        val pool = if (fresh.isNotEmpty()) fresh else candidates.filter { it != history.lastOrNull() }.ifEmpty { candidates }
        val picked = pool[random.nextInt(pool.size)]
        recordPlayed(picked)
        return picked
    }

    /**
     * Picks the next Free Roam track following `SoundBgmMgr::calc` + `playNextJukeBoxBgm`
     * (`Default` / `Night` / `__SoloAllFreeRoam` / `__FreeRoamOnly` / `Race` / `24040117_J25YUWHR`).
     */
    fun pickNextFreeRoam(
        mode: FreeRoamJukeboxCatalog.FreeRoamMode,
        availableCodes: Set<String>? = null,
        hourOfDay: Int = hourProvider(),
    ): String? {
        val playlistId = data.playlistIdForFreeRoam(mode, hourOfDay)
        return pickFromPlaylist(playlistId, availableCodes, fallbackToTypeAll = false)
    }

    /**
     * Generates a sequence of [count] upcoming Free Roam track codes using the stateful sequential
     * cursor and 9-track history ring buffer.
     */
    fun drawFreeRoamSequence(
        mode: FreeRoamJukeboxCatalog.FreeRoamMode,
        count: Int,
        availableCodes: Set<String>? = null,
        hourOfDay: Int = hourProvider(),
    ): List<String> {
        if (count <= 0) return emptyList()
        return buildList(count) {
            repeat(count) {
                val picked = pickNextFreeRoam(mode, availableCodes, hourOfDay) ?: return@buildList
                add(picked)
            }
        }
    }

    /**
     * Picks the first inter-stage (VS / online) road song for a route departing [fromStage]:
     * - Night (19:00–03:00): `24040117_J25YUWHR` (`レース夜`, 111 slots with 3x/2x weights) overrides biome lists.
     * - Day (03:00–19:00): matches one of the 8 biome first-song lists with `JukeBox_SelectListInterval = 4`
     *   throttling (when counter == 0, resets to 4 and picks from `TypeAll`); falls back to `TypeAll`
     *   for the 11 non-biome stages or if 22 history retries fail.
     */
    fun pickInterstageFirstSong(
        fromStage: String,
        isNight: Boolean,
        availableCodes: Set<String>? = null,
    ): String? {
        if (isNight) {
            return pickFromPlaylist(
                playlistId = FreeRoamJukeboxCatalog.PLAYLIST_RACE_NIGHT,
                availableCodes = availableCodes,
                fallbackToTypeAll = false,
            )
        }
        val biome = data.biomePlaylistForStartStage(fromStage)
        if (biome != null) {
            val counter = biomeCounters[biome.id] ?: data.settings.selectListInterval
            if (counter <= 0) {
                biomeCounters[biome.id] = data.settings.selectListInterval
                return pickFromPlaylist(
                    playlistId = FreeRoamJukeboxCatalog.PLAYLIST_TYPE_ALL,
                    availableCodes = availableCodes,
                    fallbackToTypeAll = false,
                )
            }
            biomeCounters[biome.id] = counter - 1
            return pickFromPlaylist(
                playlistId = biome.id,
                availableCodes = availableCodes,
                fallbackToTypeAll = true,
            )
        }
        return pickFromPlaylist(
            playlistId = FreeRoamJukeboxCatalog.PLAYLIST_TYPE_ALL,
            availableCodes = availableCodes,
            fallbackToTypeAll = false,
        )
    }

    /** Picks a 2nd+ inter-stage road song (`Race` in Day, `24040117_J25YUWHR` in Night). */
    fun pickInterstageSubsequentSong(
        isNight: Boolean,
        availableCodes: Set<String>? = null,
    ): String? {
        val playlistId = if (isNight) {
            FreeRoamJukeboxCatalog.PLAYLIST_RACE_NIGHT
        } else {
            FreeRoamJukeboxCatalog.PLAYLIST_RACE
        }
        return pickFromPlaylist(playlistId, availableCodes, fallbackToTypeAll = false)
    }

    /**
     * Picks from [playlistId] via `tryPickFromPlaylistWithHistory`:
     * sequential cursor step + `JukeBox_HistoryCount` (9) dedupe, up to 22 attempts.
     */
    fun pickFromPlaylist(
        playlistId: String,
        availableCodes: Set<String>? = null,
        fallbackToTypeAll: Boolean = false,
    ): String? {
        val primary = tryPickWithHistory(playlistId, availableCodes)
        if (primary != null) return primary

        if (fallbackToTypeAll && playlistId != FreeRoamJukeboxCatalog.PLAYLIST_TYPE_ALL) {
            val typeAllPick = tryPickWithHistory(FreeRoamJukeboxCatalog.PLAYLIST_TYPE_ALL, availableCodes)
            if (typeAllPick != null) return typeAllPick
        }

        // Graceful fallback when a user's partial pack has fewer tracks than historyCount (e.g. < 10 tracks):
        // scan once from current cursor ignoring history, preferring something different from the immediately last track.
        val pl = data.playlist(playlistId) ?: return null
        val size = pl.slots.size
        if (size == 0) return null
        val start = cursors[playlistId] ?: 0
        var fallbackAny: String? = null
        val lastPlayed = history.lastOrNull()
        for (step in 0 until size) {
            val idx = (start + step) % size
            val candidate = pl.slots[idx]
            if (availableCodes != null && candidate !in availableCodes) continue
            if (fallbackAny == null) fallbackAny = candidate
            if (candidate != lastPlayed) {
                cursors[playlistId] = (idx + 1) % size
                recordPlayed(candidate)
                return candidate
            }
        }
        if (fallbackAny != null) {
            cursors[playlistId] = (start + 1) % size
            recordPlayed(fallbackAny)
            return fallbackAny
        }
        return null
    }

    private fun tryPickWithHistory(
        playlistId: String,
        availableCodes: Set<String>?,
    ): String? {
        val pl = data.playlist(playlistId) ?: return null
        val slots = pl.slots
        if (slots.isEmpty()) return null
        var idx = (cursors[playlistId] ?: 0) % slots.size
        val retries = data.settings.maxHistoryRetries
        var attempts = 0
        var scanned = 0
        val maxScan = maxOf(retries, slots.size)
        // Scan unavailable (not-installed) slots without burning history retries; count installed slots against 22 retries.
        while (attempts < retries && scanned < maxScan) {
            val code = slots[idx]
            idx = (idx + 1) % slots.size
            scanned++
            if (availableCodes != null && code !in availableCodes) continue
            attempts++
            if (code !in history) {
                cursors[playlistId] = idx
                recordPlayed(code)
                return code
            }
        }
        cursors[playlistId] = idx
        return null
    }
}
