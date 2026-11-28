package com.mylittleblover.mkwplayer.jukebox

import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FreeRoamJukeboxEngineTest {

    private fun loadBundledData(): FreeRoamJukeboxCatalog.Data {
        val file = File("src/main/assets/jukebox_playlists.json")
        assertTrue(file.exists(), "jukebox_playlists.json must ship in assets")
        return assertNotNull(FreeRoamJukeboxCatalog.parse(file.readText()))
    }

    @Test
    fun bundledPlaylistsMatchV180RomfsCountsAndJukeboxCatalog() {
        val data = loadBundledData()
        assertEquals(13, data.playlists.size)
        assertEquals(1_102, data.playlists.sumOf { it.slots.size })
        assertEquals(9, data.settings.historyCount)
        assertEquals(4, data.settings.selectListInterval)
        assertEquals(22, data.settings.maxHistoryRetries)
        assertEquals(1_500L, data.settings.freeRunIntervalMs)
        assertEquals(93, data.freerunOnlyCodes.size)
        assertEquals(25, data.raceOnlyCodes.size)

        assertEquals(581, data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT)!!.slots.size)
        assertEquals(172, data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT)!!.uniqueCodes.size)
        assertEquals(137, data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_NIGHT)!!.slots.size)
        assertEquals(39, data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_NIGHT)!!.uniqueCodes.size)
        assertEquals(127, data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_RACE)!!.slots.size)
        assertEquals(111, data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_RACE_NIGHT)!!.slots.size)
        assertEquals(38, data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_RACE_NIGHT)!!.uniqueCodes.size)
        assertEquals(52, data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_TYPE_ALL)!!.slots.size)

        val biomePlaylists = data.playlists.filter { it.isBiomeFirst }
        assertEquals(8, biomePlaylists.size)
        assertEquals(19, biomePlaylists.sumOf { it.startStageTypes.size })

        // Verify all codes resolve in jukebox_catalog.json and IsFreerunOnly never leaks into race playlists.
        val jukeboxJson = JSONObject(File("src/main/assets/jukebox_catalog.json").readText())
        val catalogCodes = buildSet {
            val arr = jukeboxJson.getJSONArray("tracks")
            for (i in 0 until arr.length()) {
                add(arr.getJSONObject(i).getString("jukeboxCode"))
            }
        }
        assertEquals(227, catalogCodes.size)
        for (pl in data.playlists) {
            for (code in pl.slots) {
                assertTrue(code in catalogCodes, "Playlist ${pl.id} has unknown code $code")
            }
            if (pl.id != FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT && pl.id != FreeRoamJukeboxCatalog.PLAYLIST_NIGHT) {
                for (code in pl.uniqueCodes) {
                    assertFalse(code in data.freerunOnlyCodes, "Freerun-only $code leaked into race playlist ${pl.id}")
                }
            }
        }
        val soloUnion = data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT)!!.uniqueCodes +
            data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_NIGHT)!!.uniqueCodes
        for (code in data.raceOnlyCodes) {
            assertFalse(code in soloUnion, "Race-only $code must not appear in Default or Night")
        }
    }

    @Test
    fun timeOfDayClockMatchesWorldInfoRules03To19DayAnd19To03Night() {
        val data = loadBundledData()
        for (h in 3..18) {
            assertFalse(data.isNightHour(h), "Hour $h should be Day")
            assertEquals(
                FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT,
                data.playlistIdForFreeRoam(FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK, h),
            )
        }
        for (h in listOf(0, 1, 2, 19, 20, 21, 22, 23)) {
            assertTrue(data.isNightHour(h), "Hour $h should be Night")
            assertEquals(
                FreeRoamJukeboxCatalog.PLAYLIST_NIGHT,
                data.playlistIdForFreeRoam(FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK, h),
            )
        }
    }

    @Test
    fun sequentialCursorWithNineTrackHistoryPreventsRecentRepeats() {
        val data = loadBundledData()
        val engine = FreeRoamJukeboxEngine(data, random = Random(42), hourProvider = { 12 })

        val draws = engine.drawFreeRoamSequence(FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_DAY, 200)
        assertEquals(200, draws.size)
        val defaultSet = data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT)!!.uniqueCodes
        assertTrue(draws.all { it in defaultSet })
        for (i in draws.indices) {
            val windowStart = (i - 9).coerceAtLeast(0)
            for (j in windowStart until i) {
                assertNotEquals(draws[j], draws[i], "Repeat within 9 tracks at $j and $i (${draws[i]})")
            }
        }
    }

    @Test
    fun autoClockSwitchesBetweenDayDefaultAndNightPlaylistAcross19And03() {
        val data = loadBundledData()
        var currentHour = 18
        val engine = FreeRoamJukeboxEngine(data, random = Random(7), hourProvider = { currentHour })
        val dayPool = data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT)!!.uniqueCodes
        val nightPool = data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_NIGHT)!!.uniqueCodes

        val dayDraws = engine.drawFreeRoamSequence(FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK, 15)
        assertTrue(dayDraws.all { it in dayPool })

        currentHour = 19
        val nightDraws = engine.drawFreeRoamSequence(FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK, 15)
        assertTrue(nightDraws.all { it in nightPool })
        // History carries seamlessly across Day -> Night switch (no immediate repeat across boundary).
        assertNotEquals(dayDraws.last(), nightDraws.first())
    }

    @Test
    fun interstageBiomeThrottleSwapsToTypeAllEveryFifthPickAndNightOverridesBiome() {
        val data = loadBundledData()
        val engine = FreeRoamJukeboxEngine(data, random = Random(99))
        val desertPl = data.biomePlaylistForStartStage("DesertHills")!!
        val typeAllCodes = data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_TYPE_ALL)!!.uniqueCodes
        val initialCounter = engine.currentBiomeCounter(desertPl.id)!!
        assertTrue(initialCounter in 2..4, "Initial biome counter must be in 2..4, got $initialCounter")

        // Consume initialCounter biome picks
        repeat(initialCounter) {
            val code = assertNotNull(engine.pickInterstageFirstSong("DesertHills", isNight = false))
            assertTrue(code in desertPl.uniqueCodes, "Expected desert biome track, got $code")
        }
        assertEquals(0, engine.currentBiomeCounter(desertPl.id))

        // Next pick must throttle to TypeAll and reset counter to selectListInterval (4)
        val throttled = assertNotNull(engine.pickInterstageFirstSong("DesertHills", isNight = false))
        assertTrue(throttled in typeAllCodes, "Expected TypeAll on throttled pick, got $throttled")
        assertEquals(4, engine.currentBiomeCounter(desertPl.id))

        // Subsequent 4 picks hit desert again, 5th throttles to TypeAll
        repeat(4) {
            val code = assertNotNull(engine.pickInterstageFirstSong("DesertHills", isNight = false))
            assertTrue(code in desertPl.uniqueCodes)
        }
        val throttledAgain = assertNotNull(engine.pickInterstageFirstSong("DesertHills", isNight = false))
        assertTrue(throttledAgain in typeAllCodes)
        assertEquals(4, engine.currentBiomeCounter(desertPl.id))

        // Night overrides biome list with 24040117_J25YUWHR (レース夜)
        val raceNightCodes = data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_RACE_NIGHT)!!.uniqueCodes
        repeat(12) {
            val code = assertNotNull(engine.pickInterstageFirstSong("DesertHills", isNight = true))
            assertTrue(code in raceNightCodes, "Expected RaceNight track, got $code")
        }
    }

    @Test
    fun partialPackWithFewerThanTenTracksStillPicksGracefullyWithoutLoopingSameSong() {
        val data = loadBundledData()
        val tinyAvailable = data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT)!!.uniqueCodes.take(3).toSet()
        val engine = FreeRoamJukeboxEngine(data, random = Random(123))
        val picks = engine.drawFreeRoamSequence(
            mode = FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_DAY,
            count = 10,
            availableCodes = tinyAvailable,
        )
        assertEquals(10, picks.size)
        assertTrue(picks.all { it in tinyAvailable })
        for (i in 1 until picks.size) {
            assertNotEquals(picks[i - 1], picks[i], "Tiny partial pack should avoid back-to-back repeat")
        }
    }

    @Test
    fun soloAllModeCombinesDefaultAndNightWith202UniqueTracks718SlotsAndZeroRaceOnly() {
        val data = loadBundledData()
        assertEquals(13, data.playlists.size)
        assertEquals(1_102, data.playlists.sumOf { it.slots.size })

        for (mode in FreeRoamJukeboxCatalog.FreeRoamMode.IN_GAME_FREE_ROAM_MODES) {
            assertTrue(FreeRoamJukeboxCatalog.isInGameFreeRoamLibrary(mode), "$mode must be in-game Free Roam")
            assertEquals(mode, FreeRoamJukeboxCatalog.coerceInGameFreeRoamMode(mode))
        }
        for (mode in FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_RACE_MODES) {
            assertFalse(FreeRoamJukeboxCatalog.isInGameFreeRoamLibrary(mode), "$mode must not be in-game Free Roam")
            assertEquals(
                FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK,
                FreeRoamJukeboxCatalog.coerceInGameFreeRoamMode(mode),
            )
        }

        val defaultPl = assertNotNull(data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT))
        val nightPl = assertNotNull(data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_NIGHT))
        val soloAllDay = assertNotNull(data.resolvedPlaylistForFreeRoam(FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_ALL, 12))
        val soloAllNight = assertNotNull(data.resolvedPlaylistForFreeRoam(FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_ALL, 22))
        assertEquals(FreeRoamJukeboxCatalog.PLAYLIST_SOLO_ALL, soloAllDay.id)
        assertEquals(soloAllDay.id, soloAllNight.id)
        assertEquals(718, soloAllDay.slots.size)
        assertEquals(202, soloAllDay.uniqueCodes.size)
        assertTrue(data.freerunOnlyCodes.all { it in soloAllDay.uniqueCodes })
        assertTrue("M344" in soloAllDay.uniqueCodes)
        assertTrue(data.raceOnlyCodes.none { it in soloAllDay.uniqueCodes })

        // Preserves exact combined slot weights from Default (581) + Night (137).
        val expectedCounts = (defaultPl.slots + nightPl.slots).groupingBy { it }.eachCount()
        val actualCounts = soloAllDay.slots.groupingBy { it }.eachCount()
        assertEquals(expectedCounts, actualCounts)

        val engine = FreeRoamJukeboxEngine(data, random = Random(2026), hourProvider = { 12 })
        assertNotNull(engine.currentCursor(FreeRoamJukeboxCatalog.PLAYLIST_SOLO_ALL))
        val draws = engine.drawFreeRoamSequence(FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_ALL, 250)
        assertEquals(250, draws.size)
        assertTrue(draws.all { it in soloAllDay.uniqueCodes })
        assertTrue(draws.none { it in data.raceOnlyCodes })
        val nightOnlyCodes = nightPl.uniqueCodes - defaultPl.uniqueCodes
        val dayOnlyCodes = defaultPl.uniqueCodes - nightPl.uniqueCodes
        assertTrue(draws.any { it in dayOnlyCodes }, "SOLO_ALL should draw day-only tracks")
        assertTrue(draws.any { it in nightOnlyCodes }, "SOLO_ALL should blend night-only tracks via interleaving")
        for (i in draws.indices) {
            val windowStart = (i - 9).coerceAtLeast(0)
            for (j in windowStart until i) {
                assertNotEquals(draws[j], draws[i], "SOLO_ALL repeat within 9 tracks at $j and $i (${draws[i]})")
            }
        }
    }

    @Test
    fun freeRoamOnlyModeContainsStrictlyAll93FreerunOnlyTracksWithWeightedSlots() {
        val data = loadBundledData()
        val defaultPl = assertNotNull(data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_DEFAULT))
        val nightPl = assertNotNull(data.playlist(FreeRoamJukeboxCatalog.PLAYLIST_NIGHT))
        val frPl = assertNotNull(data.resolvedPlaylistForFreeRoam(FreeRoamJukeboxCatalog.FreeRoamMode.FREEROAM_ONLY, 15))
        assertEquals(FreeRoamJukeboxCatalog.PLAYLIST_FREEROAM_ONLY, frPl.id)
        assertEquals(93, frPl.uniqueCodes.size)
        assertEquals(data.freerunOnlyCodes, frPl.uniqueCodes)
        assertEquals(369, frPl.slots.size)
        assertTrue(data.raceOnlyCodes.none { it in frPl.uniqueCodes })

        val expectedWeights = (defaultPl.slots + nightPl.slots)
            .filter { it in data.freerunOnlyCodes }
            .groupingBy { it }
            .eachCount()
        assertEquals(expectedWeights, frPl.slots.groupingBy { it }.eachCount())

        val engine = FreeRoamJukeboxEngine(data, random = Random(9393), hourProvider = { 21 })
        assertEquals(
            FreeRoamJukeboxCatalog.PLAYLIST_FREEROAM_ONLY,
            engine.activeFreeRoamPlaylist(FreeRoamJukeboxCatalog.FreeRoamMode.FREEROAM_ONLY)?.id,
        )
        val draws = engine.drawFreeRoamSequence(FreeRoamJukeboxCatalog.FreeRoamMode.FREEROAM_ONLY, 200)
        assertEquals(200, draws.size)
        assertTrue(draws.all { it in data.freerunOnlyCodes })
        assertTrue(draws.none { it in data.raceOnlyCodes })
        for (i in draws.indices) {
            val windowStart = (i - 9).coerceAtLeast(0)
            for (j in windowStart until i) {
                assertNotEquals(draws[j], draws[i], "FREEROAM_ONLY repeat within 9 tracks at $j and $i (${draws[i]})")
            }
        }
    }
}
