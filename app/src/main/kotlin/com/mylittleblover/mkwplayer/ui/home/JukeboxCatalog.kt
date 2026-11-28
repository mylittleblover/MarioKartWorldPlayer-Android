package com.mylittleblover.mkwplayer.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mylittleblover.mkwplayer.library.BrowseCatalog
import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.pack.PackRegistry
import com.mylittleblover.mkwplayer.registry.ResourceKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Distinct Material Design 3 icon registry for every canonical Free Roam jukebox genre.
 * Every icon resolves to a verified symbol in `androidx.compose.material.icons.rounded`.
 */
internal enum class JukeboxGenreIcon(val key: String) {
    JAZZ_FUSION("jazz_fusion"),
    JAZZ("jazz"),
    SWING("swing"),
    FUNK("funk"),
    SOUL("soul"),
    ROCK("rock"),
    METAL("metal"),
    BLUES("blues"),
    LATIN("latin"),
    BOSSA_NOVA("bossa_nova"),
    SKA_REGGAE("ska_reggae"),
    COUNTRY("country"),
    FOLK("folk"),
    ELECTRONIC("electronic"),
    LOFI("lofi"),
    POP_LOUNGE("pop_lounge"),
    DISCO("disco"),
    DEFAULT("music_note");

    val imageVector: ImageVector
        get() = when (this) {
            JAZZ_FUSION -> Icons.Rounded.AutoAwesome
            JAZZ -> Icons.Rounded.Piano
            SWING -> Icons.Rounded.Nightlife
            FUNK -> Icons.Rounded.GraphicEq
            SOUL -> Icons.Rounded.Favorite
            ROCK -> Icons.Rounded.Bolt
            METAL -> Icons.Rounded.LocalFireDepartment
            BLUES -> Icons.Rounded.NightsStay
            LATIN -> Icons.Rounded.Celebration
            BOSSA_NOVA -> Icons.Rounded.BeachAccess
            SKA_REGGAE -> Icons.Rounded.WbSunny
            COUNTRY -> Icons.Rounded.Landscape
            FOLK -> Icons.Rounded.Park
            ELECTRONIC -> Icons.Rounded.Memory
            LOFI -> Icons.Rounded.Headphones
            POP_LOUNGE -> Icons.Rounded.LocalCafe
            DISCO -> Icons.Rounded.Album
            DEFAULT -> Icons.Rounded.MusicNote
        }

    companion object {
        private val byKey: Map<String, JukeboxGenreIcon> = entries.associateBy { it.key }

        fun fromKey(key: String): JukeboxGenreIcon? = byKey[key.trim().lowercase(Locale.ROOT)]

        fun resolveImageVector(key: String): ImageVector = (fromKey(key) ?: DEFAULT).imageVector
    }
}

/**
 * The jukebox, arranged according to the official OST hierarchy, chronological source games,
 * and verified Super Mario Wiki musical genres.
 *
 * Key invariants:
 * - Reads every verified installed `.mkwpack`, never only the single "active" pack.
 * - Matches each `JUKEBOX` track in installed packs against bundled verified OST metadata
 *   (`assets/jukebox_catalog.json`, built from `docs/research/2026-11-15-jukebox-ost-bwav-mapping.json`)
 *   by BWAV SHA-256 -> `pack_track_key` -> BWAV stem, avoiding BWAV cross-game mislabeling
 *   (e.g. Flipnote `#255`, Castle Boss `#258`, Melty Monster Galaxy `#271`, Squeaky Clean Sprint `#324`).
 * - Groups primarily by original source game ordered chronologically by release date (`gameOrder`),
 *   and orders arrangements within each game strictly by official OST track order (`jukeboxOrder`),
 *   keeping every distinct arrangement as an independent item (#98-#104).
 * - Keeps song titles clean and free of genre suffixes (`displayTitle` / `localizedTitle`),
 *   exposing verified genre metadata separately via `genreId`, `subGenre`, `genre`, and `displayGenre`.
 * - Only shows source games and genres for which at least one track is actually installed in a pack.
 */
internal object JukeboxCatalog {

    data class GenreInfo(
        val id: String,
        val names: Map<String, String>,
        val colorArgb: Long,
        val tintArgb: Long,
        val iconKey: String,
        val order: Int = Int.MAX_VALUE,
    ) {
        val icon: JukeboxGenreIcon
            get() = JukeboxGenreIcon.fromKey(iconKey) ?: JukeboxGenreIcon.DEFAULT

        val color: Color
            get() = Color(colorArgb)

        val tint: Color
            get() = Color(tintArgb)

        fun displayName(localeTag: String): String {
            val lang = normalizeLanguage(localeTag)
            return names[lang] ?: names["en"] ?: id
        }

        fun localizedName(languageTag: String): String = displayName(languageTag)

        fun searchTerms(): List<String> = buildList {
            add(id)
            addAll(names.values)
        }
    }

    data class GameMetadata(
        val id: String,
        val order: Int,
        val year: Int,
        val yearLabel: String,
        val ostSeries: String,
        val bwavGameIds: List<String>,
        val names: Map<String, String>,
        val searchAliases: List<String> = emptyList(),
    ) {
        fun localizedName(languageTag: String): String {
            val lang = normalizeLanguage(languageTag)
            return names[lang] ?: names["en"] ?: ostSeries
        }

        fun searchTerms(): List<String> = buildList {
            add(ostSeries)
            add(yearLabel)
            add(year.toString())
            addAll(names.values)
            addAll(bwavGameIds)
            addAll(searchAliases)
        }
    }

    data class TrackMetadata(
        val jukeboxOrder: Int,
        val ostIndex: Int,
        val gameId: String,
        val gameOrder: Int,
        val ostTitle: String,
        val ostArrangement: String?,
        val displayName: String,
        val bwavStem: String,
        val jukeboxCode: String,
        val bwavGameId: String,
        val packTrackKey: String,
        val sha256: String,
        val frames: Long,
        val sampleRate: Int,
        val durationMs: Long,
        val localizedNames: Map<String, String> = emptyMap(),
        val localizedTitles: Map<String, String> = emptyMap(),
        val localizedArrangements: Map<String, String> = emptyMap(),
        val searchAliases: List<String> = emptyList(),
        val genreId: String = "",
        val subGenre: String? = null,
    )

    data class Metadata(
        val games: List<GameMetadata>,
        val tracks: List<TrackMetadata>,
        val genres: List<GenreInfo> = emptyList(),
    ) {
        val gamesById: Map<String, GameMetadata> = games.associateBy { it.id }
        val genresById: Map<String, GenreInfo> = genres.associateBy { it.id }
        private val bySha256: Map<String, TrackMetadata> = tracks.associateBy { it.sha256.lowercase(Locale.ROOT) }
        private val byPackKey: Map<String, TrackMetadata> = tracks.associateBy { it.packTrackKey }
        private val byStem: Map<String, TrackMetadata> = tracks.associateBy { it.bwavStem }

        fun findTrack(track: PackManifest.Track): TrackMetadata? =
            bySha256[track.primary.sha256.lowercase(Locale.ROOT)]
                ?: byPackKey[track.key]
                ?: byStem[track.title]

        fun genre(id: String): GenreInfo? = genresById[id]

        fun tracksByGenre(genreId: String): List<TrackMetadata> = tracks.filter { it.genreId == genreId }
    }

    /** A resolved jukebox entry pairing a pack track with its verified OST metadata (when known). */
    data class TrackItem(
        val track: PackManifest.Track,
        val gameId: String,
        val gameOrder: Int,
        val jukeboxOrder: Int,
        val ostIndex: Int?,
        val title: String,
        val arrangement: String?,
        val displayName: String,
        val jukeboxCode: String?,
        val frames: Long,
        val sampleRate: Int,
        val durationMs: Long,
        val localizedNames: Map<String, String> = emptyMap(),
        val localizedTitles: Map<String, String> = emptyMap(),
        val localizedArrangements: Map<String, String> = emptyMap(),
        val searchAliases: List<String> = emptyList(),
        val genreId: String = "",
        val subGenre: String? = null,
        val genre: GenreInfo? = null,
    ) {
        val key: String get() = track.key

        val durationFormatted: String
            get() = formatDurationMs(durationMs)

        /**
         * Clean localized song title without any genre suffix.
         * Preserves only non-genre disambiguators (`(Ver. 1)` / `(Ver. 2)` for identical title+game
         * numbered variants, and `(Humming)` for Luigi's Mansion).
         */
        fun displayTitle(localeTag: String): String {
            val lang = normalizeLanguage(localeTag)
            return localizedNames[lang] ?: localizedNames["en"] ?: displayName
        }

        /** Backward-compatible alias for [displayTitle]; never embeds genre labels into the song title. */
        fun localizedDisplayName(languageTag: String): String = displayTitle(languageTag)

        /** Pure base song title without version disambiguators or genre suffixes. */
        fun localizedTitle(languageTag: String): String {
            val lang = normalizeLanguage(languageTag)
            return localizedTitles[lang] ?: localizedTitles["en"] ?: title
        }

        /** Localized primary musical genre name from the verified Mario Wiki catalog (all 227 tracks). */
        fun displayGenre(localeTag: String): String? = genre?.displayName(localeTag)

        /**
         * Recommended secondary style subtitle for PlayScreen, Queue, and Track rows.
         * Prefers the verified localized primary genre (available on all 227 tracks) and falls back
         * to [localizedArrangement] for unmapped pack tracks.
         */
        fun displayArrangement(localeTag: String): String? =
            displayGenre(localeTag) ?: localizedArrangement(localeTag)

        /** Legacy official OST arrangement label (only present on the 44 multi-arrangement OST entries). */
        fun localizedArrangement(languageTag: String): String? {
            val lang = normalizeLanguage(languageTag)
            return localizedArrangements[lang] ?: localizedArrangements["en"] ?: arrangement
        }

        fun matches(query: String, gameNames: Collection<String> = emptyList()): Boolean {
            val tokens = query.trim().lowercase(Locale.ROOT)
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return true
            val haystack = buildList {
                add(displayName)
                add(title)
                arrangement?.let { add(it) }
                if (genreId.isNotBlank()) add(genreId)
                subGenre?.let { add(it) }
                genre?.let { addAll(it.searchTerms()) }
                addAll(localizedNames.values)
                addAll(localizedTitles.values)
                addAll(localizedArrangements.values)
                addAll(searchAliases)
                add(track.title)
                jukeboxCode?.let { add(it) }
                ostIndex?.let { add(it.toString()); add("#$it") }
                addAll(gameNames)
            }.joinToString(" ").lowercase(Locale.ROOT)
            return tokens.all { token -> haystack.contains(token) }
        }
    }

    /** One source game, with the tracks the installed packs actually carry for it. */
    data class Game(
        /** Stable identity, never translated: e.g. `smw`, `smb`, or raw archive tag for fallback. */
        val id: String,
        val tracks: List<PackManifest.Track>,
        val order: Int = Int.MAX_VALUE,
        val year: Int = 0,
        val yearLabel: String = "",
        val localizedNames: Map<String, String> = emptyMap(),
        val items: List<TrackItem> = tracks.mapIndexed { idx, t ->
            val clean = displayTitle(t.title)
            TrackItem(
                track = t,
                gameId = id,
                gameOrder = order,
                jukeboxOrder = idx + 1,
                ostIndex = null,
                title = clean,
                arrangement = null,
                displayName = clean,
                jukeboxCode = null,
                frames = 0L,
                sampleRate = 0,
                durationMs = 0L,
            )
        },
        val searchAliases: List<String> = emptyList(),
    ) {
        fun localizedName(languageTag: String): String {
            val lang = normalizeLanguage(languageTag)
            return localizedNames[lang] ?: localizedNames["en"] ?: id
        }

        fun matches(query: String): Boolean {
            val tokens = query.trim().lowercase(Locale.ROOT)
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return true
            val haystack = buildList {
                add(id)
                if (year > 0) add(year.toString())
                if (yearLabel.isNotBlank()) add(yearLabel)
                addAll(localizedNames.values)
                addAll(searchAliases)
            }.joinToString(" ").lowercase(Locale.ROOT)
            return tokens.all { token -> haystack.contains(token) }
        }
    }

    data class SearchResult(
        val games: List<Game>,
        val trackHits: List<Pair<Game, TrackItem>>,
    )

    /** Every jukebox track in every installed pack, grouped by source game and indexed by genre. */
    data class Catalog(
        val games: List<Game>,
        val tracksByKey: Map<String, PackManifest.Track>,
        /** Which pack directory each track lives in, so opening it reads the right file. */
        val packByKey: Map<String, File> = emptyMap(),
        val itemsByKey: Map<String, TrackItem> = games.flatMap { it.items }.associateBy { it.key },
        val genres: List<GenreInfo> = emptyList(),
    ) {
        val genresById: Map<String, GenreInfo> = genres.associateBy { it.id }

        val allItems: List<Pair<TrackItem, Game>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            games.flatMap { game -> game.items.map { item -> item to game } }
        }

        /** All genres that have at least one installed track in this catalog, in canonical display order. */
        val allGenres: List<GenreInfo> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val presentIds = itemsByKey.values
                .asSequence()
                .map { it.genreId }
                .filter { it.isNotBlank() }
                .toHashSet()
            genres
                .filter { it.id in presentIds }
                .sortedWith(compareBy<GenreInfo> { it.order }.thenBy { it.id })
        }

        val isEmpty: Boolean get() = games.isEmpty()

        /** How many jukebox tracks the installed packs carry. */
        val trackCount: Int get() = tracksByKey.size

        fun gameById(gameId: String): Game? = games.firstOrNull { it.id == gameId }

        fun gameForTrack(key: String): Game? = games.firstOrNull { game ->
            game.items.any { it.key == key } || game.tracks.any { it.key == key }
        }

        fun itemForTrack(key: String): TrackItem? = itemsByKey[key]

        fun genre(id: String): GenreInfo? = genresById[id]

        fun entriesByGenre(genreId: String): List<TrackItem> =
            allItems.asSequence()
                .map { it.first }
                .filter { it.genreId == genreId }
                .toList()

        fun itemsWithGamesByGenre(genreId: String): List<Pair<TrackItem, Game>> =
            allItems.filter { it.first.genreId == genreId }

        fun trackCountByGenre(genreId: String): Int =
            itemsByKey.values.count { it.genreId == genreId }

        fun search(query: String): SearchResult {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return SearchResult(games, emptyList())
            val matchedGames = games.filter { it.matches(trimmed) }
            val matchedTracks = buildList {
                for (game in games) {
                    val gameTerms = buildList {
                        add(game.id)
                        if (game.yearLabel.isNotBlank()) add(game.yearLabel)
                        addAll(game.localizedNames.values)
                        addAll(game.searchAliases)
                    }
                    for (item in game.items) {
                        if (item.matches(trimmed, gameTerms)) {
                            add(game to item)
                        }
                    }
                }
            }
            return SearchResult(matchedGames, matchedTracks)
        }
    }

    fun parseMetadata(json: String): Metadata? = runCatching {
        val root = JSONObject(json)
        val gamesJson = root.optJSONArray("games") ?: return@runCatching null
        val tracksJson = root.optJSONArray("tracks") ?: return@runCatching null
        val genresJson = root.optJSONArray("genres")

        val genres = buildList {
            if (genresJson != null) {
                for (i in 0 until genresJson.length()) {
                    val g = genresJson.getJSONObject(i)
                    val colorArgb = when {
                        g.has("colorArgb") -> g.optLong("colorArgb", DEFAULT_GENRE_COLOR_ARGB)
                        else -> parseArgbHex(g.optString("colorHex", ""), DEFAULT_GENRE_COLOR_ARGB)
                    }
                    val tintArgb = when {
                        g.has("tintArgb") -> g.optLong("tintArgb", DEFAULT_GENRE_TINT_ARGB)
                        else -> parseArgbHex(g.optString("tintHex", ""), DEFAULT_GENRE_TINT_ARGB)
                    }
                    add(
                        GenreInfo(
                            id = g.getString("id"),
                            names = readStringMap(g.optJSONObject("names")),
                            colorArgb = colorArgb,
                            tintArgb = tintArgb,
                            iconKey = g.optString("iconKey", JukeboxGenreIcon.DEFAULT.key),
                            order = g.optInt("order", i + 1),
                        ),
                    )
                }
            }
        }

        val games = buildList {
            for (i in 0 until gamesJson.length()) {
                val g = gamesJson.getJSONObject(i)
                add(
                    GameMetadata(
                        id = g.getString("id"),
                        order = g.getInt("order"),
                        year = g.optInt("year", 0),
                        yearLabel = g.optString("yearLabel", ""),
                        ostSeries = g.optString("ostSeries", g.getString("id")),
                        bwavGameIds = readStringList(g.optJSONArray("bwavGameIds")),
                        names = readStringMap(g.optJSONObject("names")),
                        searchAliases = readStringList(g.optJSONArray("searchAliases")),
                    ),
                )
            }
        }

        val tracks = buildList {
            for (i in 0 until tracksJson.length()) {
                val t = tracksJson.getJSONObject(i)
                val arrRaw = if (t.isNull("ostArrangement")) null else t.optString("ostArrangement", "")
                    .takeIf { it.isNotBlank() }
                val subGenreRaw = if (t.isNull("subGenre")) null else t.optString("subGenre", "")
                    .trim()
                    .takeIf { it.isNotEmpty() }
                add(
                    TrackMetadata(
                        jukeboxOrder = t.getInt("jukeboxOrder"),
                        ostIndex = t.getInt("ostIndex"),
                        gameId = t.getString("gameId"),
                        gameOrder = t.getInt("gameOrder"),
                        ostTitle = t.getString("ostTitle"),
                        ostArrangement = arrRaw,
                        displayName = t.getString("displayName"),
                        bwavStem = t.getString("bwavStem"),
                        jukeboxCode = t.optString("jukeboxCode", ""),
                        bwavGameId = t.optString("bwavGameId", ""),
                        packTrackKey = t.getString("packTrackKey"),
                        sha256 = t.getString("sha256"),
                        frames = t.optLong("frames", 0L),
                        sampleRate = t.optInt("sampleRate", 48_000),
                        durationMs = t.optLong("durationMs", 0L),
                        localizedNames = readStringMap(t.optJSONObject("names")),
                        localizedTitles = readStringMap(t.optJSONObject("titles")),
                        localizedArrangements = readStringMap(t.optJSONObject("arrangements")),
                        searchAliases = readStringList(t.optJSONArray("searchAliases")),
                        genreId = t.optString("genreId", "").trim(),
                        subGenre = subGenreRaw,
                    ),
                )
            }
        }
        Metadata(games = games, tracks = tracks, genres = genres)
    }.getOrNull()

    private fun readStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key, "").trim()
                if (value.isNotEmpty()) put(key, value)
            }
        }
    }

    private fun readStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i, "").trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    fun load(registry: PackRegistry, metadata: Metadata? = null): Catalog {
        val tracks = linkedMapOf<String, PackManifest.Track>()
        val packs = linkedMapOf<String, File>()
        val parsed = registry.load().mapNotNull { entry ->
            val directory = File(entry.directory)
            val manifestFile = File(directory, PackManifest.MEMBER_NAME)
            val manifest = runCatching {
                (PackManifest.parse(manifestFile.readText()) as? PackManifest.Result.Ok)?.manifest
            }.getOrNull() ?: return@mapNotNull null
            Triple(entry, manifest, directory)
        }
        // Per packId keep the highest FULL version and newer DELTAs (#84, #85); highest version wins.
        val retained = parsed.groupBy { it.first.packId }.values.flatMap { group ->
            val sorted = group.sortedBy { it.first.packVersion }
            val latestFull = sorted.indexOfLast { !it.second.isDelta }
            if (latestFull >= 0) sorted.drop(latestFull) else sorted
        }.sortedWith(
            compareByDescending<Triple<PackRegistry.Entry, PackManifest, File>> { it.first.packVersion }
                .thenBy { it.first.packId },
        )
        for ((_, manifest, directory) in retained) {
            for (track in manifest.tracks) {
                if (track.kind != ResourceKind.JUKEBOX) continue
                tracks.putIfAbsent(track.key, track)
                packs.putIfAbsent(track.key, directory)
            }
        }
        return buildCatalog(tracks.values.toList(), packs, metadata)
    }

    /** Builds catalog directly from a single merged manifest directory. */
    fun buildFromManifest(manifest: PackManifest, directory: File, metadata: Metadata? = null): Catalog {
        val jukeboxTracks = manifest.tracks.filter { it.kind == ResourceKind.JUKEBOX }
        val packByKey = jukeboxTracks.associate { it.key to directory }
        return buildCatalog(jukeboxTracks, packByKey, metadata)
    }

    /** Pure catalog assembly over already-resolved JUKEBOX pack tracks, sorted chronologically and by OST order. */
    fun buildCatalog(
        jukeboxTracks: List<PackManifest.Track>,
        packByKey: Map<String, File> = emptyMap(),
        metadata: Metadata? = null,
    ): Catalog {
        val tracksMap = linkedMapOf<String, PackManifest.Track>()
        val grouped = linkedMapOf<String, MutableList<TrackItem>>()

        for (track in jukeboxTracks) {
            if (track.kind != ResourceKind.JUKEBOX) continue
            if (tracksMap.containsKey(track.key)) continue
            tracksMap[track.key] = track

            val meta = metadata?.findTrack(track)
            val item = if (meta != null) {
                TrackItem(
                    track = track,
                    gameId = meta.gameId,
                    gameOrder = meta.gameOrder,
                    jukeboxOrder = meta.jukeboxOrder,
                    ostIndex = meta.ostIndex,
                    title = meta.ostTitle,
                    arrangement = meta.ostArrangement,
                    displayName = meta.displayName,
                    jukeboxCode = meta.jukeboxCode.ifBlank { null },
                    frames = meta.frames,
                    sampleRate = meta.sampleRate,
                    durationMs = meta.durationMs,
                    localizedNames = meta.localizedNames,
                    localizedTitles = meta.localizedTitles,
                    localizedArrangements = meta.localizedArrangements,
                    searchAliases = meta.searchAliases,
                    genreId = meta.genreId,
                    subGenre = meta.subGenre,
                    genre = metadata?.genre(meta.genreId),
                )
            } else {
                val fallbackGame = sourceGameOf(track.title)
                val clean = displayTitle(track.title)
                TrackItem(
                    track = track,
                    gameId = fallbackGame,
                    gameOrder = Int.MAX_VALUE,
                    jukeboxOrder = Int.MAX_VALUE,
                    ostIndex = null,
                    title = clean,
                    arrangement = null,
                    displayName = clean,
                    jukeboxCode = null,
                    frames = 0L,
                    sampleRate = 0,
                    durationMs = 0L,
                )
            }
            grouped.getOrPut(item.gameId) { mutableListOf() }.add(item)
        }

        val orderedGames = grouped.entries.map { (gameId, items) ->
            val gameMeta = metadata?.gamesById?.get(gameId)
            val sortedItems = items.sortedWith(
                compareBy<TrackItem> { it.jukeboxOrder }
                    .thenBy { it.ostIndex ?: Int.MAX_VALUE }
                    .thenBy { it.displayName }
                    .thenBy { it.track.title },
            )
            Game(
                id = gameId,
                tracks = sortedItems.map { it.track },
                order = gameMeta?.order ?: Int.MAX_VALUE,
                year = gameMeta?.year ?: 0,
                yearLabel = gameMeta?.yearLabel.orEmpty(),
                localizedNames = gameMeta?.names ?: mapOf("en" to gameId),
                items = sortedItems,
                searchAliases = gameMeta?.searchTerms().orEmpty(),
            )
        }.sortedWith(
            compareBy<Game> { it.order }
                .thenBy { it.year }
                .thenBy { it.id },
        )

        val itemsByKey = orderedGames.flatMap { it.items }.associateBy { it.key }
        return Catalog(
            games = orderedGames,
            tracksByKey = tracksMap,
            packByKey = packByKey,
            itemsByKey = itemsByKey,
            genres = metadata?.genres.orEmpty(),
        )
    }

    /**
     * The source game a jukebox title names when no verified OST table overrides it.
     *
     * The pack carries the archive's file names verbatim, so the shape is
     * `BGM_JukeBox_M001B_SMW_Ground_RnR_Outro` (`SMW` at index 3).
     */
    fun sourceGameOf(title: String): String {
        val fields = title.trim().split('_')
        if (fields.size >= 5 && fields[0].equals("BGM", true) && fields[1].startsWith("JukeBox", true)) {
            return fields[3].ifBlank { "Unfiled" }
        }
        val first = title.trim().substringBefore(' ').substringBefore('_')
        return first.ifBlank { "Unfiled" }
    }

    /**
     * The fallback title without archive bookkeeping when no verified OST entry applies.
     */
    fun displayTitle(title: String): String {
        val trimmed = title.trim()
        val fields = trimmed.split('_')
        if (fields.size >= 6 && fields[0].equals("BGM", true) && fields[1].startsWith("JukeBox", true)) {
            val tail = fields.drop(4).toMutableList()
            while (tail.isNotEmpty() && tail.last().startsWith("Outro", true)) tail.removeAt(tail.size - 1)
            val joined = tail.joinToString(" ").trim()
            if (joined.isNotBlank()) return joined
        }
        return trimmed
    }

    fun formatDurationMs(durationMs: Long): String {
        if (durationMs <= 0L) return "--:--"
        val totalSeconds = (durationMs / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(Locale.US, minutes, seconds)
    }

    /** The browse catalogue's name for a course code, or the code itself. */
    fun name(catalog: BrowseCatalog?, code: String, language: String): String =
        catalog?.displayName(code, language) ?: code

    private const val DEFAULT_GENRE_COLOR_ARGB = 0xFF565A66L
    private const val DEFAULT_GENRE_TINT_ARGB = 0xFFF5F5F8L

    internal fun parseArgbHex(hex: String, fallback: Long): Long {
        val clean = hex.trim().removePrefix("#")
        val parsed = clean.toLongOrNull(16) ?: return fallback
        return when (clean.length) {
            6 -> 0xFF000000L or parsed
            8 -> parsed
            else -> fallback
        }
    }

    internal fun normalizeLanguage(languageTag: String): String {
        val lower = languageTag.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("ja") -> "ja"
            lower.startsWith("zh") -> {
                if ("hant" in lower || "-tw" in lower || "-hk" in lower || "-mo" in lower) "zh-Hant"
                else "zh-Hans"
            }
            else -> "en"
        }
    }
}
