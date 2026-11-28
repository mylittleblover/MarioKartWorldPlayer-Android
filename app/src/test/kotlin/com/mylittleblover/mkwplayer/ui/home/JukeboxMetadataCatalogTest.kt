package com.mylittleblover.mkwplayer.ui.home

import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JukeboxMetadataCatalogTest {

    private fun bundledCatalogFile(): File = bundledAssetFile("jukebox_catalog.json")

    private fun bundledAssetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
            File("MKW-Player/app/src/main/assets/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate $name from ${File(".").absolutePath}")
    }

    private fun fakeTrack(
        key: String,
        title: String,
        sha256: String,
    ): PackManifest.Track {
        val asset = AudioAssetId(sha256)
        return PackManifest.Track(
            key = key,
            kind = ResourceKind.JUKEBOX,
            title = title,
            primary = asset,
            isFlow = false,
            parts = listOf(PackManifest.Part(asset, PackManifest.Role.MAIN)),
            timing = null,
        )
    }

    @Test
    fun parsesAll36ChronologicalGamesAnd227TracksFromBundledAsset() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())
        assertNotNull(metadata)
        assertEquals(36, metadata.games.size)
        assertEquals((1..36).toList(), metadata.games.map { it.order })
        assertEquals(227, metadata.tracks.size)
        assertEquals((1..227).toList(), metadata.tracks.map { it.jukeboxOrder })

        val packTracks = metadata.tracks.map { t ->
            fakeTrack(key = t.packTrackKey, title = t.bwavStem, sha256 = t.sha256)
        }
        val catalog = JukeboxCatalog.buildCatalog(packTracks, emptyMap(), metadata)

        assertEquals(36, catalog.games.size)
        assertEquals(227, catalog.trackCount)
        assertEquals((1..36).toList(), catalog.games.map { it.order })
        assertEquals("smb", catalog.games.first().id)

        // Tracks inside each game preserve official OST sequence.
        for (game in catalog.games) {
            val orders = game.items.map { it.jukeboxOrder }
            assertEquals(orders.sorted(), orders, "OST track order broke inside ${game.id}")
        }
    }

    @Test
    fun showsOnlyGamesPresentInInstalledPacksWhilePreservingChronology() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val last = metadata.tracks.last()
        val first = metadata.tracks.first()
        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = listOf(
                fakeTrack(last.packTrackKey, last.bwavStem, last.sha256),
                fakeTrack(first.packTrackKey, first.bwavStem, first.sha256),
            ),
            metadata = metadata,
        )

        assertEquals(listOf(first.gameId, last.gameId).distinct(), catalog.games.map { it.id })
        assertEquals(first.gameOrder, catalog.games.first().order)
    }

    @Test
    fun routesCrossGamePrefixAnomaliesToCanonicalOstGames() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val anomalyIndices = setOf(255, 258, 271, 324)
        val anomalyMeta = metadata.tracks.filter { it.ostIndex in anomalyIndices }
        assertEquals(4, anomalyMeta.size)
        assertTrue(anomalyMeta.any { it.bwavGameId.isNotBlank() && !it.bwavGameId.equals(it.gameId, ignoreCase = true) })

        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = anomalyMeta.map { fakeTrack(it.packTrackKey, it.bwavStem, it.sha256) },
            metadata = metadata,
        )

        for (meta in anomalyMeta) {
            val resolvedGame = catalog.gameForTrack(meta.packTrackKey)
            assertNotNull(resolvedGame)
            assertEquals(meta.gameId, resolvedGame.id, "OST #${meta.ostIndex} must stay in ${meta.gameId}")
        }
    }

    @Test
    fun keepsDistinctArrangementsWithSharedTitleAsIndependentRows() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val groupedByTitle = metadata.tracks.groupBy { it.gameId to it.ostTitle }
        val sharedGroup = groupedByTitle.values.firstOrNull { items ->
            items.size >= 2 && items.mapNotNull { it.ostArrangement }.distinct().size >= 2
        }
        assertNotNull(sharedGroup, "Expected at least one shared-title multi-arrangement pair in OST metadata")

        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = sharedGroup.map { fakeTrack(it.packTrackKey, it.bwavStem, it.sha256) },
            metadata = metadata,
        )
        val game = catalog.gameById(sharedGroup.first().gameId)!!
        assertEquals(sharedGroup.size, game.items.size)
        assertEquals(sharedGroup.map { it.packTrackKey }, game.items.map { it.key })
    }

    @Test
    fun supportsLocalizedGameNamesMultiKeywordSearchAndFallbackTracks() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val smwMeta = metadata.tracks.first { it.gameId == "smw" }
        val unknownTrack = fakeTrack(
            key = "jukebox:unknown:99",
            title = "BGM_Jukebox_Mystery_TestTheme",
            sha256 = "0123456789abcdef".repeat(4),
        )
        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = listOf(
                fakeTrack(smwMeta.packTrackKey, smwMeta.bwavStem, smwMeta.sha256),
                unknownTrack,
            ),
            metadata = metadata,
        )

        val smw = catalog.gameById("smw")!!
        assertEquals("Super Mario World", smw.localizedName("en"))
        assertEquals("超级马力欧世界", smw.localizedName("zh-CN"))
        assertEquals("超級瑪利歐世界", smw.localizedName("zh-TW"))
        assertEquals("スーパーマリオワールド", smw.localizedName("ja-JP"))

        val searchHits = catalog.search("1990 马力欧世界")
        assertTrue(searchHits.games.any { it.id == "smw" })
        assertTrue(searchHits.trackHits.any { it.second.key == smwMeta.packTrackKey })

        // Unknown tracks still appear cleanly via fallback rather than dropping.
        val fallbackGame = catalog.gameForTrack(unknownTrack.key)
        assertNotNull(fallbackGame)
        assertFalse(catalog.search("Mystery TestTheme").trackHits.isEmpty())
    }

    @Test
    fun localizesAllJukeboxGamesAndTracksPerMarioWikiAndSupportsMultiLanguageSearch() {
        val metadata = JukeboxCatalog.parseMetadata(bundledCatalogFile().readText())!!
        val languages = listOf("en", "ja", "zh-Hans", "zh-Hant")

        for (game in metadata.games) {
            for (lang in languages) {
                assertFalse(game.names[lang].isNullOrBlank(), "Missing $lang game name for ${game.id}")
            }
        }
        for (track in metadata.tracks) {
            for (lang in languages) {
                assertFalse(track.localizedNames[lang].isNullOrBlank(), "Missing $lang track name for #${track.ostIndex}")
                assertFalse(track.localizedTitles[lang].isNullOrBlank(), "Missing $lang track title for #${track.ostIndex}")
                if (track.ostArrangement != null) {
                    assertFalse(
                        track.localizedArrangements[lang].isNullOrBlank(),
                        "Missing $lang arrangement for #${track.ostIndex}",
                    )
                }
            }
        }

        val fullCatalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = metadata.tracks.map { fakeTrack(it.packTrackKey, it.bwavStem, it.sha256) },
            metadata = metadata,
        )

        assertEquals("超级咚奇刚", fullCatalog.gameById("dkc")!!.localizedName("zh-CN"))
        assertEquals("超级马力欧USA", fullCatalog.gameById("smb2")!!.localizedName("zh-CN"))
        assertEquals("瑪利歐賽車 Double Dash!!", fullCatalog.gameById("mkdd")!!.localizedName("zh-TW"))
        assertEquals("マリオカートアドバンス", fullCatalog.gameById("mksc")!!.localizedName("ja-JP"))
        assertEquals("超级马力欧阳光", fullCatalog.gameById("sms")!!.localizedName("zh-Hans"))
        assertEquals("New 超级马力欧兄弟 Wii", fullCatalog.gameById("nsmbwii")!!.localizedName("zh-Hans"))
        assertEquals("马力欧卡丁车实况：家庭赛车场", fullCatalog.gameById("mklhc")!!.localizedName("zh-Hans"))

        val track177 = fullCatalog.itemForTrack(metadata.tracks.first { it.ostIndex == 177 }.packTrackKey)!!
        assertEquals("ウォーターランド", track177.localizedDisplayName("ja"))
        assertEquals("ウォーターランド", track177.localizedTitle("ja"))
        assertEquals(null, track177.localizedArrangement("ja"))

        val track120 = fullCatalog.itemForTrack(metadata.tracks.first { it.ostIndex == 120 }.packTrackKey)!!
        assertEquals("地上BGM", track120.localizedDisplayName("ja"))
        assertEquals("地上BGM", track120.displayTitle("ja"))
        assertEquals("地上BGM", track120.localizedTitle("ja"))
        assertEquals("スカ＆レゲエ", track120.displayGenre("ja"))
        assertEquals("ラテンVer.", track120.localizedArrangement("ja"))

        val track246 = fullCatalog.itemForTrack(metadata.tracks.first { it.ostIndex == 246 }.packTrackKey)!!
        assertEquals("瓦力欧矿山", track246.localizedDisplayName("zh-Hans"))
        assertEquals("瓦力欧矿山", track246.localizedTitle("zh-Hans"))
        assertEquals(null, track246.localizedArrangement("zh-Hans"))

        val waluigiHits = fullCatalog.search("瓦路易吉").trackHits
        assertTrue(waluigiHits.any { it.second.ostIndex in setOf(225, 226) })

        val coconutHits = fullCatalog.search("椰子广场").trackHits
        assertTrue(coconutHits.any { it.second.ostIndex == 244 })

        val gustyHits = fullCatalog.search("ウィンドガーデン").trackHits
        assertTrue(gustyHits.any { it.second.ostIndex == 238 })

        val dkcGameHits = fullCatalog.search("超级森喜刚").games
        assertTrue(dkcGameHits.any { it.id == "dkc" })
    }

    @Test
    fun extraMusicCatalogHasCompleteMarioWikiLocalizations() {
        val catalog = ExtraMusicCatalog.parse(bundledAssetFile("extra_music_catalog.json").readText())
        val languages = listOf("en", "ja", "zh-Hans", "zh-Hant")

        val allEntries = buildList {
            catalog.sfcCourses.forEach { add(it.id to it.names) }
            catalog.battleTracks.forEach { add(it.id to it.names) }
            catalog.otherMusic.forEach { add(it.id to it.names) }
            catalog.shortSounds.forEach { add(it.id to it.names) }
            catalog.nintendoMusicSpecial.forEach { add(it.id to it.names) }
        }
        assertTrue(allEntries.size >= 80)
        for ((id, names) in allEntries) {
            for (lang in languages) {
                assertFalse(names[lang].isNullOrBlank(), "Missing $lang name in extra_music_catalog for $id")
            }
        }

        val choco = catalog.sfcCourses.first { it.id == "sfc_choco_island" }
        assertEquals("Choco Island (Mission)", choco.localizedName("en"))
        assertEquals("巧克力岛（任务）", choco.localizedName("zh-Hans"))
        assertEquals("巧克力島（任務）", choco.localizedName("zh-Hant"))
        assertEquals("チョコレーとう（ミッション）", choco.localizedName("ja"))

        val delfino = catalog.battleTracks.first { it.id == "battle_delfino_pier" }
        assertEquals("水上丽都（Mario Kart Wii）", delfino.localizedName("zh-Hans"))

        val openingFlower = catalog.otherMusic.first { it.id == "opening_flower" }
        assertEquals("开场（鲜花杯）", openingFlower.localizedName("zh-Hans"))

        val victoryLap = catalog.otherMusic.first { it.id == "victory_lap" }
        assertEquals("胜利圈", victoryLap.localizedName("zh-Hans"))
        assertEquals("馳往勝利", victoryLap.localizedName("zh-Hant"))

        val finalLapFf = catalog.shortSounds.first { it.id == "final_lap_fanfare" }
        assertEquals("最后一圈 鸣乐", finalLapFf.localizedName("zh-Hans"))

        val titleTheme = catalog.otherMusic.first { it.id == "title_theme" }
        assertEquals("Title Theme", titleTheme.localizedName("en"))
        assertEquals("Main Theme", titleTheme.localizedName("zh-Hans"))
    }
}
