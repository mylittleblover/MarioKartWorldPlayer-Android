package com.mylittleblover.mkwplayer.ui.home

import android.content.Context
import org.json.JSONObject
import java.util.Locale

internal object ExtraMusicCatalog {
    data class AssetRef(
        val stem: String,
        val sha256: String,
        val frames: Long,
        val sampleRate: Int,
        val channels: Int,
        val loop: Boolean,
        val loopStart: Long?,
        val loopEnd: Long?,
    )

    data class KoopaSpeakerConfig(
        val stageCode: String,
        val normalAsset: AssetRef,
        val fastAsset: AssetRef,
    )

    data class TwoStateTrack(
        val id: String,
        val ostIndex: Int?,
        val artwork: String,
        val names: Map<String, String>,
        val mainStem: String,
        val fastStem: String,
        val searchAliases: List<String>,
        val main: AssetRef,
        val fast: AssetRef,
        val durationMs: Long,
    ) {
        fun localizedName(languageTag: String): String = localizedFrom(names, languageTag, id)
        fun matches(query: String): Boolean = matchesQuery(query, names, searchAliases, ostIndex)
    }

    data class TitleOutroSpec(
        val index: Int,
        val cueName: String,
        val mainExitFrame: Long,
        val brassCutExitFrame: Long,
        val asset: AssetRef,
    )

    data class ChainedPartSpec(
        val asset: AssetRef,
        val sourceOffset: Long,
        val sourceLength: Long,
    )

    sealed interface OtherMusicItem {
        val id: String
        val ostIndex: Int?
        val artwork: String
        val names: Map<String, String>
        val searchAliases: List<String>
        val durationMs: Long

        fun localizedName(languageTag: String): String = localizedFrom(names, languageTag, id)
        fun matches(query: String): Boolean = matchesQuery(query, names, searchAliases, ostIndex)

        data class InteractiveTitle(
            override val id: String,
            override val ostIndex: Int?,
            override val artwork: String,
            override val names: Map<String, String>,
            override val searchAliases: List<String>,
            override val durationMs: Long,
            val harmonica: AssetRef,
            val main: AssetRef,
            val brassCut: AssetRef,
            val outros: List<TitleOutroSpec>,
        ) : OtherMusicItem

        data class LinearAsset(
            override val id: String,
            override val ostIndex: Int?,
            override val artwork: String,
            override val names: Map<String, String>,
            override val searchAliases: List<String>,
            override val durationMs: Long,
            val asset: AssetRef,
        ) : OtherMusicItem

        data class OverlaidOpening(
            override val id: String,
            override val ostIndex: Int?,
            override val artwork: String,
            override val names: Map<String, String>,
            override val searchAliases: List<String>,
            override val durationMs: Long,
            val op: AssetRef,
            val telop: AssetRef,
            val deltaFrames: Long,
            val totalFrames: Long,
        ) : OtherMusicItem

        data class ChainedSequence(
            override val id: String,
            override val ostIndex: Int?,
            override val artwork: String,
            override val names: Map<String, String>,
            override val searchAliases: List<String>,
            override val durationMs: Long,
            val parts: List<ChainedPartSpec>,
            val totalFrames: Long,
        ) : OtherMusicItem
    }

    data class SimpleTrackItem(
        val id: String,
        val order: Int,
        val ostIndex: Int?,
        val artwork: String,
        val names: Map<String, String>,
        val searchAliases: List<String>,
        val asset: AssetRef,
        val durationMs: Long,
    ) {
        fun localizedName(languageTag: String): String = localizedFrom(names, languageTag, id)
        fun matches(query: String): Boolean = matchesQuery(query, names, searchAliases, ostIndex)
    }

    data class Catalog(
        val courseArtwork: Map<String, String> = emptyMap(),
        val jukeboxGameArtwork: Map<String, String> = emptyMap(),
        val koopaSpeaker: KoopaSpeakerConfig? = null,
        val sfcCourses: List<TwoStateTrack> = emptyList(),
        val battleTracks: List<TwoStateTrack> = emptyList(),
        val otherMusic: List<OtherMusicItem> = emptyList(),
        val shortSounds: List<SimpleTrackItem> = emptyList(),
        val nintendoMusicSpecial: List<SimpleTrackItem> = emptyList(),
    ) {
        fun digestForStem(stem: String): String? {
            sfcCourses.forEach { t ->
                if (t.main.stem.equals(stem, ignoreCase = true)) return t.main.sha256
                if (t.fast.stem.equals(stem, ignoreCase = true)) return t.fast.sha256
            }
            battleTracks.forEach { t ->
                if (t.main.stem.equals(stem, ignoreCase = true)) return t.main.sha256
                if (t.fast.stem.equals(stem, ignoreCase = true)) return t.fast.sha256
            }
            koopaSpeaker?.let { ks ->
                if (ks.normalAsset.stem.equals(stem, ignoreCase = true)) return ks.normalAsset.sha256
                if (ks.fastAsset.stem.equals(stem, ignoreCase = true)) return ks.fastAsset.sha256
            }
            otherMusic.forEach { item ->
                when (item) {
                    is OtherMusicItem.InteractiveTitle -> {
                        listOf(item.harmonica, item.main, item.brassCut)
                            .firstOrNull { it.stem.equals(stem, ignoreCase = true) }
                            ?.let { return it.sha256 }
                        item.outros.firstOrNull { it.asset.stem.equals(stem, ignoreCase = true) }
                            ?.let { return it.asset.sha256 }
                    }
                    is OtherMusicItem.ChainedSequence ->
                        item.parts.firstOrNull { it.asset.stem.equals(stem, ignoreCase = true) }
                            ?.let { return it.asset.sha256 }
                    is OtherMusicItem.OverlaidOpening -> {
                        if (item.op.stem.equals(stem, ignoreCase = true)) return item.op.sha256
                        if (item.telop.stem.equals(stem, ignoreCase = true)) return item.telop.sha256
                    }
                    is OtherMusicItem.LinearAsset ->
                        if (item.asset.stem.equals(stem, ignoreCase = true)) return item.asset.sha256
                }
            }
            shortSounds.firstOrNull { it.asset.stem.equals(stem, ignoreCase = true) }?.let { return it.asset.sha256 }
            nintendoMusicSpecial.firstOrNull { it.asset.stem.equals(stem, ignoreCase = true) }?.let { return it.asset.sha256 }
            return null
        }
    }

    @Volatile
    private var cached: Catalog? = null

    fun load(context: Context): Catalog {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                context.assets.open("extra_music_catalog.json").bufferedReader().use { parse(it.readText()) }
            }.getOrDefault(Catalog()).also { cached = it }
        }
    }

    fun parse(text: String): Catalog {
        val root = JSONObject(text)
        return Catalog(
            courseArtwork = parseStringMap(root.optJSONObject("courseArtwork")),
            jukeboxGameArtwork = parseStringMap(root.optJSONObject("jukeboxGameArtwork")),
            koopaSpeaker = root.optJSONObject("koopaSpeaker")?.let { ks ->
                KoopaSpeakerConfig(
                    stageCode = ks.getString("stageCode"),
                    normalAsset = parseAssetRef(ks.getJSONObject("normalAsset")),
                    fastAsset = parseAssetRef(ks.getJSONObject("fastAsset")),
                )
            },
            sfcCourses = parseTwoStateList(root.optJSONArray("sfcCourses")),
            battleTracks = parseTwoStateList(root.optJSONArray("battleTracks")),
            otherMusic = parseOtherMusicList(root.optJSONArray("otherMusic")),
            shortSounds = parseSimpleList(root.optJSONArray("shortSounds")),
            nintendoMusicSpecial = parseSimpleList(root.optJSONArray("nintendoMusicSpecial")),
        )
    }

    private fun parseStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            result[k] = obj.getString(k)
        }
        return result
    }

    private fun parseStringList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun parseAssetRef(obj: JSONObject): AssetRef = AssetRef(
        stem = obj.getString("stem"),
        sha256 = obj.getString("sha256"),
        frames = obj.getLong("frames"),
        sampleRate = obj.getInt("sampleRate"),
        channels = obj.getInt("channels"),
        loop = obj.optBoolean("loop", false),
        loopStart = obj.optLongOrNull("loopStart"),
        loopEnd = obj.optLongOrNull("loopEnd"),
    )

    private fun parseTwoStateList(arr: org.json.JSONArray?): List<TwoStateTrack> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TwoStateTrack(
                id = o.getString("id"),
                ostIndex = o.optIntOrNull("ostIndex"),
                artwork = o.getString("artwork"),
                names = parseStringMap(o.optJSONObject("names")),
                mainStem = o.getString("mainStem"),
                fastStem = o.getString("fastStem"),
                searchAliases = parseStringList(o.optJSONArray("searchAliases")),
                main = parseAssetRef(o.getJSONObject("main")),
                fast = parseAssetRef(o.getJSONObject("fast")),
                durationMs = o.getLong("durationMs"),
            )
        }
    }

    private fun parseOtherMusicList(arr: org.json.JSONArray?): List<OtherMusicItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val ostIndex = o.optIntOrNull("ostIndex")
            val artwork = o.getString("artwork")
            val names = parseStringMap(o.optJSONObject("names"))
            val aliases = parseStringList(o.optJSONArray("searchAliases"))
            val durationMs = o.getLong("durationMs")
            when (val type = o.getString("type")) {
                "interactive_title" -> {
                    val outrosArr = o.getJSONArray("outros")
                    val outros = (0 until outrosArr.length()).map { j ->
                        val oi = outrosArr.getJSONObject(j)
                        TitleOutroSpec(
                            index = oi.getInt("index"),
                            cueName = oi.getString("cueName"),
                            mainExitFrame = oi.getLong("mainExitFrame"),
                            brassCutExitFrame = oi.getLong("brassCutExitFrame"),
                            asset = parseAssetRef(oi.getJSONObject("asset")),
                        )
                    }
                    OtherMusicItem.InteractiveTitle(
                        id = id,
                        ostIndex = ostIndex,
                        artwork = artwork,
                        names = names,
                        searchAliases = aliases,
                        durationMs = durationMs,
                        harmonica = parseAssetRef(o.getJSONObject("harmonica")),
                        main = parseAssetRef(o.getJSONObject("main")),
                        brassCut = parseAssetRef(o.getJSONObject("brassCut")),
                        outros = outros,
                    )
                }
                "overlaid_opening" -> OtherMusicItem.OverlaidOpening(
                    id = id,
                    ostIndex = ostIndex,
                    artwork = artwork,
                    names = names,
                    searchAliases = aliases,
                    durationMs = durationMs,
                    op = parseAssetRef(o.getJSONObject("op")),
                    telop = parseAssetRef(o.getJSONObject("telop")),
                    deltaFrames = o.getLong("deltaFrames"),
                    totalFrames = o.getLong("totalFrames"),
                )
                "chained_sequence" -> {
                    val partsArr = o.getJSONArray("parts")
                    val parts = (0 until partsArr.length()).map { j ->
                        val p = partsArr.getJSONObject(j)
                        ChainedPartSpec(
                            asset = parseAssetRef(p.getJSONObject("asset")),
                            sourceOffset = p.getLong("sourceOffset"),
                            sourceLength = p.getLong("sourceLength"),
                        )
                    }
                    OtherMusicItem.ChainedSequence(
                        id = id,
                        ostIndex = ostIndex,
                        artwork = artwork,
                        names = names,
                        searchAliases = aliases,
                        durationMs = durationMs,
                        parts = parts,
                        totalFrames = o.getLong("totalFrames"),
                    )
                }
                "looping", "oneshot" -> OtherMusicItem.LinearAsset(
                    id = id,
                    ostIndex = ostIndex,
                    artwork = artwork,
                    names = names,
                    searchAliases = aliases,
                    durationMs = durationMs,
                    asset = parseAssetRef(o.getJSONObject("asset")),
                )
                else -> error("Unknown otherMusic type: $type")
            }
        }
    }

    private fun parseSimpleList(arr: org.json.JSONArray?): List<SimpleTrackItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SimpleTrackItem(
                id = o.getString("id"),
                order = o.optInt("order", i + 1),
                ostIndex = o.optIntOrNull("ostIndex"),
                artwork = o.getString("artwork"),
                names = parseStringMap(o.optJSONObject("names")),
                searchAliases = parseStringList(o.optJSONArray("searchAliases")),
                asset = parseAssetRef(o.getJSONObject("asset")),
                durationMs = o.getLong("durationMs"),
            )
        }
    }

    internal fun localizedFrom(names: Map<String, String>, languageTag: String, fallback: String): String {
        val lang = JukeboxCatalog.normalizeLanguage(languageTag)
        return names[lang] ?: names["en"] ?: names.values.firstOrNull() ?: fallback
    }

    internal fun matchesQuery(
        query: String,
        names: Map<String, String>,
        aliases: List<String>,
        ostIndex: Int?,
    ): Boolean {
        val tokens = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val haystack = buildList {
            addAll(names.values)
            addAll(aliases)
            if (ostIndex != null) {
                add(ostIndex.toString())
                add("#%03d".format(Locale.ROOT, ostIndex))
            }
        }
        return tokens.all { token -> haystack.any { it.contains(token, ignoreCase = true) } }
    }

    /**
     * Extra tracks directly tied to Special Cup / Rainbow Road that stay hidden until Rainbow Road
     * & Special Cup are unlocked. (`BigTree` and `SFCMarioCircuit` remain visible because they appear
     * in Knockout Tour.)
     */
    val HIDDEN_UNTIL_SPECIAL_UNLOCKED_IDS: Set<String> = setOf(
        "opening_special",
        "victory_lap_rainbow",
        "results_rainbow",
        "special_cup_appears",
        "rainbow_fl_fanfare",
        "staff_credits",
        "nm_special_092",
        "nm_special_111",
        "nm_special_119",
    )

    /** Staff Credits tracks require completing all 30 MKW courses (#25). */
    val STAFF_CREDITS_TRACK_IDS: Set<String> = setOf(
        "staff_credits",
        "nm_special_119",
    )

    fun isVisible(
        id: String,
        specialUnlocked: Boolean,
        staffCreditsUnlocked: Boolean = false,
    ): Boolean {
        if (id in STAFF_CREDITS_TRACK_IDS) return staffCreditsUnlocked
        return specialUnlocked || id !in HIDDEN_UNTIL_SPECIAL_UNLOCKED_IDS
    }

    fun extraIdFromEntryKey(key: String): String? {
        if (key.startsWith("extra:")) return key.removePrefix("extra:")
        val marker = ":opening:"
        val idx = key.indexOf(marker)
        return if (idx >= 0) key.substring(idx + marker.length) else null
    }

    fun openingIdForOfficialRoute(
        routeId: String,
        kind: com.mylittleblover.mkwplayer.routes.OfficialRouteKind,
    ): String? = when (kind) {
        com.mylittleblover.mkwplayer.routes.OfficialRouteKind.RALLY -> "opening_knockout"
        com.mylittleblover.mkwplayer.routes.OfficialRouteKind.GRAND_PRIX -> when (routeId) {
            "mushroom_cup" -> "opening_mushroom"
            "flower_cup" -> "opening_flower"
            "star_cup" -> "opening_star"
            "shell_cup" -> "opening_shell"
            "banana_cup" -> "opening_banana"
            "leaf_cup" -> "opening_leaf"
            "lightning_cup" -> "opening_lightning"
            "special_cup" -> "opening_special"
            else -> null
        }
    }
}
