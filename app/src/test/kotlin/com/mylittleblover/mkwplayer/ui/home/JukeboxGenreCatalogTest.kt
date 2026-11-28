package com.mylittleblover.mkwplayer.ui.home

import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JukeboxGenreCatalogTest {

    private fun bundledCatalogFile(): File {
        val candidates = listOf(
            File("src/main/assets/jukebox_catalog.json"),
            File("app/src/main/assets/jukebox_catalog.json"),
            File("MKW-Player/app/src/main/assets/jukebox_catalog.json"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate jukebox_catalog.json from ${File(".").absolutePath}")
    }

    private fun fakeTrack(meta: JukeboxCatalog.TrackMetadata): PackManifest.Track {
        val asset = AudioAssetId(meta.sha256)
        return PackManifest.Track(
            key = meta.packTrackKey,
            kind = ResourceKind.JUKEBOX,
            title = meta.bwavStem,
            primary = asset,
            isFlow = false,
            parts = listOf(PackManifest.Part(asset, PackManifest.Role.MAIN)),
            timing = null,
        )
    }

    private fun loadFullCatalog(): Pair<JukeboxCatalog.Metadata, JukeboxCatalog.Catalog> {
        val metadata = assertNotNull(JukeboxCatalog.parseMetadata(bundledCatalogFile().readText()))
        val catalog = JukeboxCatalog.buildCatalog(
            jukeboxTracks = metadata.tracks.map(::fakeTrack),
            metadata = metadata,
        )
        return metadata to catalog
    }

    private fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFFL).toDouble() / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrastRatio(fgArgb: Long, bgArgb: Long): Double {
        val l1 = relativeLuminance(fgArgb)
        val l2 = relativeLuminance(bgArgb)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    @Test
    fun all227TracksHaveValidGenreIdAndVerifiedSubGenre() {
        val (metadata, catalog) = loadFullCatalog()
        assertEquals(227, metadata.tracks.size)
        assertEquals(17, metadata.genres.size)
        assertEquals(17, catalog.allGenres.size)

        val validIds = metadata.genres.map { it.id }.toSet()
        for (track in metadata.tracks) {
            assertTrue(track.genreId in validIds, "OST #${track.ostIndex} has unknown genreId '${track.genreId}'")
            assertFalse(track.subGenre.isNullOrBlank(), "OST #${track.ostIndex} is missing Wiki subGenre")
            val item = assertNotNull(catalog.itemForTrack(track.packTrackKey))
            assertEquals(track.genreId, item.genreId)
            assertEquals(track.subGenre, item.subGenre)
            assertNotNull(item.genre, "Resolved GenreInfo missing on OST #${track.ostIndex}")
            assertEquals(catalog.genre(track.genreId), item.genre)
        }

        val totalByGenre = catalog.allGenres.sumOf { catalog.entriesByGenre(it.id).size }
        assertEquals(227, totalByGenre)
        for (genre in catalog.allGenres) {
            val entries = catalog.entriesByGenre(genre.id)
            assertTrue(entries.isNotEmpty(), "Genre '${genre.id}' has 0 tracks")
            assertEquals(entries.size, catalog.trackCountByGenre(genre.id))
            assertEquals(entries.size, catalog.itemsWithGamesByGenre(genre.id).size)
        }
    }

    @Test
    fun allGenresHave4LanguageNamesUniqueAccessibleColorsAndValidDistinctIcons() {
        val (metadata, _) = loadFullCatalog()
        val requiredLanguages = listOf("en", "zh-Hans", "zh-Hant", "ja")
        val seenIds = hashSetOf<String>()
        val seenColors = hashSetOf<Long>()
        val seenTints = hashSetOf<Long>()
        val seenIcons = hashSetOf<JukeboxGenreIcon>()

        for (genre in metadata.genres) {
            assertTrue(seenIds.add(genre.id), "Duplicate genre id: ${genre.id}")
            for (lang in requiredLanguages) {
                assertFalse(
                    genre.names[lang].isNullOrBlank(),
                    "Missing $lang display name for genre ${genre.id}",
                )
            }
            assertEquals(genre.names["en"], genre.displayName("en-US"))
            assertEquals(genre.names["zh-Hans"], genre.displayName("zh-CN"))
            assertEquals(genre.names["zh-Hant"], genre.displayName("zh-TW"))
            assertEquals(genre.names["ja"], genre.displayName("ja-JP"))

            assertTrue(seenColors.add(genre.colorArgb), "Duplicate accent color on genre ${genre.id}")
            assertTrue(seenTints.add(genre.tintArgb), "Duplicate tint color on genre ${genre.id}")
            assertTrue((genre.colorArgb ushr 24) == 0xFFL, "Accent color must be opaque ARGB on ${genre.id}")
            assertTrue((genre.tintArgb ushr 24) == 0xFFL, "Tint color must be opaque ARGB on ${genre.id}")

            // WCAG AA >= 4.5:1 against white, porcelain card (#F8F8FA), and its own soft tint background.
            assertTrue(
                contrastRatio(genre.colorArgb, 0xFFFFFFFFL) >= 4.5,
                "Genre ${genre.id} accent fails WCAG AA on white",
            )
            assertTrue(
                contrastRatio(genre.colorArgb, 0xFFF8F8FAL) >= 4.5,
                "Genre ${genre.id} accent fails WCAG AA on porcelain card",
            )
            assertTrue(
                contrastRatio(genre.colorArgb, genre.tintArgb) >= 4.5,
                "Genre ${genre.id} accent fails WCAG AA on its own tint",
            )

            val resolvedIcon = assertNotNull(
                JukeboxGenreIcon.fromKey(genre.iconKey),
                "Invalid iconKey '${genre.iconKey}' for genre ${genre.id}",
            )
            assertNotEquals(JukeboxGenreIcon.DEFAULT, resolvedIcon, "Genre ${genre.id} must not use DEFAULT icon")
            assertTrue(seenIcons.add(resolvedIcon), "Duplicate icon $resolvedIcon on genre ${genre.id}")
            assertNotNull(resolvedIcon.imageVector)
            assertEquals(resolvedIcon.imageVector, JukeboxGenreIcon.resolveImageVector(genre.iconKey))
        }
    }

    @Test
    fun zeroSongTitlesContainGenreSuffixesWhileCleanVersionDisambiguatorsRemain() {
        val (metadata, catalog) = loadFullCatalog()
        val locales = listOf("en", "zh-Hans", "zh-Hant", "ja")
        val forbiddenGenreSuffixes = listOf(
            Regex("\\((Latin|Acoustic|Jazz Fusion|Jazz|Rock|Rock 'n' Roll|Ska|Country|Fusion|Latin Jazz|Samba|Bossa Nova|Big Band|Electro|Soul|Funk|Lounge Pop)(\\s+\\d+)?\\)", RegexOption.IGNORE_CASE),
            Regex("(ラテン|アコースティック|フュージョン|ロックンロール|スカ|カントリー|ラテンジャズ|サンバ|ボサノヴァ|ジャズ|ビックバンド|ビッグバンド|ロック|エレクトロ|ソウル|ファンク|ラウンジポップ)Ver\\."),
            Regex("(拉丁|原声音乐|融合爵士乐|摇滚乐|摇滚|斯卡|乡村音乐|拉丁爵士乐|桑巴|巴萨诺瓦|爵士乐|大乐队|电子乐|灵魂乐|放克|休闲流行乐)Ver\\."),
            Regex("(不插電|融合爵士樂|搖滾樂|搖滾|鄉村音樂|拉丁爵士樂|森巴|巴薩諾瓦|爵士樂|大樂團|電子音樂|靈魂樂|休閒流行樂)Ver\\."),
        )

        for (track in metadata.tracks) {
            val item = assertNotNull(catalog.itemForTrack(track.packTrackKey))
            for (locale in locales) {
                val displayTitle = item.displayTitle(locale)
                val localizedDisplay = item.localizedDisplayName(locale)
                val baseTitle = item.localizedTitle(locale)
                assertEquals(displayTitle, localizedDisplay)
                assertTrue(displayTitle.isNotBlank())
                assertTrue(baseTitle.isNotBlank())
                for (rx in forbiddenGenreSuffixes) {
                    assertFalse(
                        rx.containsMatchIn(displayTitle),
                        "OST #${track.ostIndex} displayTitle($locale)='$displayTitle' contains genre suffix",
                    )
                    assertFalse(
                        rx.containsMatchIn(baseTitle),
                        "OST #${track.ostIndex} localizedTitle($locale)='$baseTitle' contains genre suffix",
                    )
                }
            }
        }

        // Verify clean non-genre disambiguators on identical game+title numbered versions and #206 Humming.
        fun itemByOst(ost: Int) = catalog.itemForTrack(metadata.tracks.first { it.ostIndex == ost }.packTrackKey)!!
        assertEquals("Ground BGM", itemByOst(120).displayTitle("en"))
        assertEquals("Ground BGM", itemByOst(121).displayTitle("en"))
        assertEquals("Ground BGM (Ver. 1)", itemByOst(139).displayTitle("en"))
        assertEquals("Ground BGM (Ver. 2)", itemByOst(140).displayTitle("en"))
        assertEquals("地上BGM (Ver. 1)", itemByOst(139).displayTitle("zh-Hans"))
        assertEquals("地上BGM (Ver. 2)", itemByOst(140).displayTitle("zh-Hant"))
        assertEquals("Mario Kart 8 (Ver. 1)", itemByOst(299).displayTitle("en"))
        assertEquals("Mario Kart 8 (Ver. 2)", itemByOst(300).displayTitle("en"))
        assertEquals("Title Theme (Humming)", itemByOst(206).displayTitle("en"))
        assertEquals("メインテーマ（鼻歌）", itemByOst(206).displayTitle("ja"))
    }

    @Test
    fun verifiedMarioWikiGenresMatchSpotCheckedTracksAndSupportFilteringAndSearch() {
        val (metadata, catalog) = loadFullCatalog()
        fun itemByOst(ost: Int) = catalog.itemForTrack(metadata.tracks.first { it.ostIndex == ost }.packTrackKey)!!

        assertEquals("ska_reggae", itemByOst(120).genreId)
        assertEquals("Calypso", itemByOst(120).subGenre)
        assertEquals("latin", itemByOst(121).genreId)
        assertEquals("Samba", itemByOst(121).subGenre)
        assertEquals("metal", itemByOst(146).genreId)
        assertEquals("Groove metal", itemByOst(146).subGenre)
        assertEquals("bossa_nova", itemByOst(157).genreId)
        assertEquals("Bossa nova", itemByOst(157).subGenre)
        assertEquals("rock", itemByOst(225).genreId)
        assertEquals("Funk rock", itemByOst(225).subGenre)
        assertEquals("funk", itemByOst(226).genreId)
        assertEquals("Jazz-funk", itemByOst(226).subGenre)
        assertEquals("pop_lounge", itemByOst(248).genreId)
        assertEquals("Shibuya-kei", itemByOst(248).subGenre)
        assertEquals("lofi", itemByOst(302).genreId)
        assertEquals("Lofi hip-hop", itemByOst(302).subGenre)
        assertEquals("metal", itemByOst(346).genreId)
        assertEquals("Death metal", itemByOst(346).subGenre)

        // displayGenre & displayArrangement provide localized genre on all tracks (even those with null ostArrangement).
        assertEquals("Metal", itemByOst(146).displayGenre("en"))
        assertEquals("金属", itemByOst(146).displayArrangement("zh-Hans"))
        assertEquals("金屬", itemByOst(146).displayArrangement("zh-Hant"))
        assertEquals("メタル", itemByOst(146).displayArrangement("ja"))

        // Genre & subgenre search works across all 4 languages.
        assertTrue(catalog.search("融合爵士").trackHits.any { it.second.ostIndex == 151 })
        assertTrue(catalog.search("Shibuya-kei").trackHits.any { it.second.ostIndex == 248 })
        assertTrue(catalog.search("ボサノヴァ").trackHits.any { it.second.ostIndex == 157 })
        assertTrue(catalog.search("Death metal").trackHits.any { it.second.ostIndex == 346 })

        // Partial catalog exposes only genres present in installed tracks.
        val subset = listOf(metadata.tracks.first { it.ostIndex == 146 }, metadata.tracks.first { it.ostIndex == 157 })
        val partialCatalog = JukeboxCatalog.buildCatalog(subset.map(::fakeTrack), metadata = metadata)
        assertEquals(listOf("metal", "bossa_nova"), partialCatalog.allGenres.map { it.id })
        assertEquals(1, partialCatalog.entriesByGenre("metal").size)
        assertEquals(0, partialCatalog.entriesByGenre("jazz_fusion").size)
        assertNotNull(partialCatalog.genre("jazz_fusion"))
    }

    @Test
    fun parsesLegacyMetadataWithoutGenresArrayGracefully() {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "games": [
                {
                  "id": "smb",
                  "order": 1,
                  "year": 1985,
                  "yearLabel": "1985",
                  "ostSeries": "Super Mario Bros.",
                  "bwavGameIds": ["Bros"],
                  "names": { "en": "Super Mario Bros." }
                }
              ],
              "tracks": [
                {
                  "jukeboxOrder": 1,
                  "ostIndex": 120,
                  "gameId": "smb",
                  "gameOrder": 1,
                  "ostTitle": "Ground BGM",
                  "ostArrangement": "Latin",
                  "displayName": "Ground BGM",
                  "bwavStem": "BGM_JukeBox_M551_Bros_Overworld_OutroFull",
                  "packTrackKey": "jukebox:legacy1",
                  "sha256": "${"a".repeat(64)}",
                  "frames": 48000,
                  "sampleRate": 48000,
                  "durationMs": 1000,
                  "names": { "en": "Ground BGM" },
                  "titles": { "en": "Ground BGM" },
                  "arrangements": { "en": "Latin" }
                }
              ]
            }
        """.trimIndent()
        val meta = assertNotNull(JukeboxCatalog.parseMetadata(legacyJson))
        assertTrue(meta.genres.isEmpty())
        assertEquals("", meta.tracks.single().genreId)
        assertNull(meta.tracks.single().subGenre)

        val catalog = JukeboxCatalog.buildCatalog(meta.tracks.map(::fakeTrack), metadata = meta)
        assertTrue(catalog.allGenres.isEmpty())
        val item = assertNotNull(catalog.itemForTrack("jukebox:legacy1"))
        assertNull(item.displayGenre("en"))
        assertEquals("Latin", item.displayArrangement("en"))
    }
}
