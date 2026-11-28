package com.mylittleblover.mkwplayer.routes

import com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog
import com.mylittleblover.mkwplayer.library.StageConnections
import com.mylittleblover.mkwplayer.pack.PackManifest
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoadJukeboxCatalogTest {

    private fun connectionsCatalog(): StageConnections.Catalog {
        val file = File("src/main/assets/stage_connections.json")
        return StageConnections.parse(file.readText())!!
    }

    private fun trackItem(
        key: String,
        hexDigit: Char,
        jukeboxCode: String,
        name: String,
    ): JukeboxCatalog.TrackItem {
        val sha = hexDigit.toString().repeat(64)
        val assetId = AudioAssetId(sha)
        val track = PackManifest.Track(
            key = key,
            kind = ResourceKind.JUKEBOX,
            title = key,
            primary = assetId,
            isFlow = false,
            parts = listOf(PackManifest.Part(asset = assetId, role = PackManifest.Role.MAIN)),
        )
        return JukeboxCatalog.TrackItem(
            track = track,
            gameId = "mkw",
            gameOrder = 1,
            jukeboxOrder = 1,
            ostIndex = 1,
            title = name,
            arrangement = null,
            displayName = name,
            jukeboxCode = jukeboxCode,
            frames = 48_000,
            sampleRate = 48_000,
            durationMs = 1_000L,
            searchAliases = listOf("alias_$key"),
        )
    }

    @Test
    fun candidatePoolReplacementEnforcesMonotonicVersionAndDisallowsConflictingSameVersion() {
        val v1 = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 1,
            entries = mapOf(RoadJukeboxCatalog.EdgeKey("A", "B") to listOf("jb_1")),
        )
        val v1Same = RoadJukeboxCatalog.CandidatePool.parse(v1.toJsonString())!!
        val sameMerged = assertIs<RoadJukeboxCatalog.MergeResult.Replaced>(v1.mergeReplace(v1Same))
        assertEquals(1, sameMerged.pool.dataVersion)
        assertFalse(sameMerged.changed)

        val v1Conflict = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 1,
            entries = mapOf(RoadJukeboxCatalog.EdgeKey("A", "B") to listOf("jb_other")),
        )
        assertIs<RoadJukeboxCatalog.MergeResult.RejectedConflict>(v1.mergeReplace(v1Conflict))

        val v0Lower = RoadJukeboxCatalog.CandidatePool(dataVersion = 0)
        assertIs<RoadJukeboxCatalog.MergeResult.IgnoredOlderVersion>(v1.mergeReplace(v0Lower))

        val v2Higher = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 2,
            entries = mapOf(RoadJukeboxCatalog.EdgeKey("B", "C") to listOf("jb_2")),
        )
        val replaced = assertIs<RoadJukeboxCatalog.MergeResult.Replaced>(v1.mergeReplace(v2Higher))
        assertTrue(replaced.changed)
        assertEquals(2, replaced.pool.dataVersion)
        assertFalse(replaced.pool.entries.containsKey(RoadJukeboxCatalog.EdgeKey("A", "B")))
        assertEquals(listOf("jb_2"), replaced.pool.entries[RoadJukeboxCatalog.EdgeKey("B", "C")])
    }

    @Test
    fun customRouteInspectionEnforcesTopologyIncompletePoolBlockAndCustomSubstitute() {
        val connections = connectionsCatalog()
        val item1 = trackItem("track_1", '1', "JB01", "Road Tune 1")
        val item2 = trackItem("track_2", '2', "JB02", "Road Tune 2")
        val subItem = trackItem("sub_track", '3', "JB99", "Substitute Tune")
        val installed = listOf(item1, item2, subItem)

        // Known pool on DonkeyKong -> FirstCircuit requires ALL candidates present & readable (#82).
        val pool = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 1,
            entries = mapOf(
                RoadJukeboxCatalog.EdgeKey("DonkeyKong", "FirstCircuit") to listOf("JB01", "JB02"),
            ),
        )

        // Verified edges: DonkeyKong -> FirstCircuit (known pool) + FirstCircuit -> WestCity (no pool data).
        assertTrue(connections.connects("FirstCircuit", "WestCity"))
        val customRoute = CustomRoute(
            id = "cr1",
            name = "DK to WestCity",
            stops = listOf(
                CustomRouteStop("DonkeyKong"),
                CustomRouteStop("FirstCircuit"),
                CustomRouteStop("WestCity"),
            ),
            edges = listOf(
                CustomRouteEdgeConfig("DonkeyKong", "FirstCircuit", musicEnabled = true, selectedJukeboxKey = null),
                CustomRouteEdgeConfig("FirstCircuit", "WestCity", musicEnabled = true, selectedJukeboxKey = "JB99"), // #73 CustomSubstitute
            ),
        )

        val okReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = customRoute,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            isJukeboxReadable = { true },
        )
        assertTrue(okReport.canPlay)
        val leg0 = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(okReport.legs[0].resolution)
        assertEquals(2, leg0.candidates.size)
        val leg1 = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.CustomSubstitute>(okReport.legs[1].resolution)
        assertEquals("sub_track", leg1.track.key)

        // Missing/unreadable ANY candidate on DonkeyKong -> FirstCircuit blocks the whole route (#82).
        val blockedReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = customRoute,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            isJukeboxReadable = { it.key != "track_2" },
        )
        assertFalse(blockedReport.canPlay)
        assertTrue(blockedReport.incompletePoolEdges.contains(RoadJukeboxCatalog.EdgeKey("DonkeyKong", "FirstCircuit")))

        // Reversed one-way edge FirstCircuit -> DonkeyKong is rejected as invalid topology (#74, #75).
        val reversedRoute = CustomRoute(
            id = "bad_rev",
            name = "Invalid Reverse",
            stops = listOf(CustomRouteStop("FirstCircuit"), CustomRouteStop("DonkeyKong")),
            edges = listOf(CustomRouteEdgeConfig("FirstCircuit", "DonkeyKong")),
        )
        val badReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = reversedRoute,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
        )
        assertFalse(badReport.canPlay)
        assertEquals(listOf(RoadJukeboxCatalog.EdgeKey("FirstCircuit", "DonkeyKong")), badReport.invalidTopologyEdges)
    }

    @Test
    fun officialRouteNeverAcceptsCustomSubstituteAndCleanlySkipsWhenPoolMissing() {
        val connections = connectionsCatalog()
        val route = OfficialRoute(
            id = "mushroom_cup",
            kind = OfficialRouteKind.GRAND_PRIX,
            names = mapOf("en" to "Mushroom Cup"),
            stops = listOf(
                OfficialStop("s1", 1, "FirstCircuit"),
                OfficialStop("s2", 2, "WestCity"),
            ),
            requiresSpecialUnlock = false,
            sourceUrl = "https://www.mariowiki.com/Mushroom_Cup",
        )
        val report = RoadJukeboxCatalog.inspectOfficialRoute(
            route = route,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = emptyList(),
            isStagePlayable = { true },
            isStageHidden = { false },
        )
        assertTrue(report.canPlay)
        assertIs<RoadJukeboxCatalog.EdgeMusicResolution.MissingCandidateData>(report.legs.single().resolution)
    }

    @Test
    fun bundledRouteMusicCatalogMatchesWikiStructureAndJukeboxCatalog() {
        val routeCatalogText = File("src/main/assets/route_music_catalog.json").readText()
        val jukeboxJson = org.json.JSONObject(File("src/main/assets/jukebox_catalog.json").readText())
        val jukeboxCodes = buildSet {
            val tracks = jukeboxJson.getJSONArray("tracks")
            for (t in 0 until tracks.length()) {
                val code = tracks.getJSONObject(t).optString("jukeboxCode").trim()
                if (code.isNotEmpty()) add(code)
            }
        }
        val wiki = RoadJukeboxCatalog.RouteMusicCatalog.parse(routeCatalogText)!!
        assertEquals(23, wiki.gpEdges.size)
        assertEquals(1, wiki.specialEdges.size)
        assertEquals(133, wiki.tracks.size)
        assertEquals("BGM_Rainbow_Area1_Pre", wiki.specialFor("Central", "Rainbow")?.stem)
        for (rule in wiki.tracks) {
            assertTrue(rule.jukeboxCode in jukeboxCodes, "Missing jukebox code ${rule.jukeboxCode}")
        }
        for ((edge, candidates) in wiki.gpEdges) {
            assertTrue(candidates.isNotEmpty(), "GP edge $edge has no candidates")
            for (code in candidates) {
                assertTrue(code in jukeboxCodes, "GP edge $edge references unknown code $code")
            }
        }
    }

    @Test
    fun officialAndCustomRoutesFollowGpVsDayVsNightAndKnockoutDirectRules() {
        val connections = connectionsCatalog()
        val wiki = RoadJukeboxCatalog.RouteMusicCatalog.parse(
            File("src/main/assets/route_music_catalog.json").readText(),
        )!!
        val installed = wiki.tracks.map { rule ->
            trackItem(
                key = "jb_${rule.jukeboxCode}",
                hexDigit = 'a',
                jukeboxCode = rule.jukeboxCode,
                name = "Track ${rule.jukeboxCode}",
            )
        }

        // 1. Official GP: Special Cup includes BigTree -> SFCMarioCircuit -> Central -> Rainbow
        val specialCup = OfficialRoute(
            id = "special_cup",
            kind = OfficialRouteKind.GRAND_PRIX,
            names = mapOf("en" to "Special Cup"),
            stops = listOf(
                OfficialStop("s1", 1, "BigTree"),
                OfficialStop("s2", 2, "SFCMarioCircuit"),
                OfficialStop("s3", 3, "Central"),
                OfficialStop("s4", 4, "Rainbow"),
            ),
            requiresSpecialUnlock = true,
            sourceUrl = "https://www.mariowiki.com/Special_Cup",
        )
        val gpReport = RoadJukeboxCatalog.inspectOfficialRoute(
            route = specialCup,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
            ruleMode = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
        )
        assertTrue(gpReport.canPlay)
        val leg0 = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(gpReport.legs[0].resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.GRAND_PRIX, leg0.ruleModeUsed)
        assertTrue(leg0.candidates.isNotEmpty())
        val leg1 = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(gpReport.legs[1].resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.GRAND_PRIX, leg1.ruleModeUsed)
        val leg2Special = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.SpecialRoadApproach>(gpReport.legs[2].resolution)
        assertEquals("BGM_Rainbow_Area1_Pre", leg2Special.stem)

        // 2. Official Rally in AUTO uses KNOCKOUT_DIRECT; switching to VS_DAY yields weighted VS Day candidates
        val goldenRally = OfficialRoute(
            id = "golden_rally",
            kind = OfficialRouteKind.RALLY,
            names = mapOf("en" to "Golden Rally"),
            stops = listOf(
                OfficialStop("r1", 1, "DesertHills"),
                OfficialStop("r2", 2, "ShyGuyBazaar"),
            ),
            requiresSpecialUnlock = false,
            sourceUrl = "https://www.mariowiki.com/Golden_Rally",
        )
        val rallyAuto = RoadJukeboxCatalog.inspectOfficialRoute(
            route = goldenRally,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
            ruleMode = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
        )
        assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect>(rallyAuto.legs.single().resolution)

        val rallyVsDay = RoadJukeboxCatalog.inspectOfficialRoute(
            route = goldenRally,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
            ruleMode = RoadJukeboxCatalog.RoadMusicRuleMode.VS_DAY,
        )
        val desertDay = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(rallyVsDay.legs.single().resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.VS_DAY, desertDay.ruleModeUsed)
        assertTrue(desertDay.candidateTiers.values.any { it == RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER })
        // v1.8.0 romfs verified all 19 previously "likely" biome tracks as confirmed EXCLUSIVE_ORDER (0 guesses left).
        assertFalse(desertDay.candidateTiers.values.any { it == RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED })
        assertEquals(10, desertDay.candidateTiers.values.count { it == RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER })
        assertTrue(desertDay.candidateTiers.values.any { it == RoadJukeboxCatalog.CandidateTier.MAIN_ORDER })
        assertTrue(desertDay.candidateWeights.zip(desertDay.candidates).all { (w, item) ->
            val tier = desertDay.candidateTiers[item.key]
            if (tier == RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER || tier == RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED) {
                w == 4
            } else {
                w == 1
            }
        })

        // 3. Custom route in VS_NIGHT uses NIGHT_ORDER with exact 3x/2x slot weights from 24040117_J25YUWHR (111 slots / 38 tracks)
        val customNight = CustomRoute(
            id = "custom_night",
            name = "Night Cruise",
            stops = listOf(
                CustomRouteStop("DesertHills"),
                CustomRouteStop("ShyGuyBazaar"),
            ),
            edges = listOf(CustomRouteEdgeConfig("DesertHills", "ShyGuyBazaar")),
            roadRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.VS_NIGHT,
        )
        val nightReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = customNight,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
        )
        val nightRes = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(nightReport.legs.single().resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.VS_NIGHT, nightRes.ruleModeUsed)
        assertEquals(38, nightRes.candidates.size)
        assertEquals(111, nightRes.candidateWeights.sum())
        assertEquals(35, nightRes.candidateWeights.count { it == 3 })
        assertEquals(3, nightRes.candidateWeights.count { it == 2 })
        assertTrue(nightRes.candidateTiers.values.all { it == RoadJukeboxCatalog.CandidateTier.NIGHT_ORDER })
    }

    @Test
    fun linearQueueHonorsWeightedRoadCandidateDistribution() {
        val heavy = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(key = "heavy", title = "Heavy")
        val light = com.mylittleblover.mkwplayer.player.LinearQueue.Entry(key = "light", title = "Light")
        val template = heavy.copy(
            roadCandidateVariants = listOf(heavy, light),
            roadCandidateWeights = listOf(4, 1),
        )
        val queue = com.mylittleblover.mkwplayer.player.LinearQueue(kotlin.random.Random(12345))
        var heavyCount = 0
        var lightCount = 0
        repeat(500) {
            val picked = queue.startSession(listOf(template), 0)!!
            if (picked.key == "heavy") heavyCount++ else if (picked.key == "light") lightCount++
        }
        assertTrue(heavyCount > 350 && lightCount > 50, "Expected ~4:1 distribution, got heavy=$heavyCount light=$lightCount")
    }

    @Test
    fun routeSuiteModeSelectsCorrectVariantsForGrandPrixRallyCustomAndCompleteSuite() {
        val gpStops = listOf("FirstCircuit", "WestCity", "Coaster", "DonkeyKong")
        assertEquals(
            listOf("c", "a", "a", "a"),
            gpStops.mapIndexed { idx, code ->
                RoadJukeboxCatalog.resolveSuiteVariantForStop(
                    officialKind = OfficialRouteKind.GRAND_PRIX,
                    suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                    stopIndex = idx,
                    stopCount = gpStops.size,
                    stageCode = code,
                )
            },
        )
        // Non-first DonkeyKong in GP uses A (multi-stage rocket climb medley, no GP special-case)
        assertEquals(
            "a",
            RoadJukeboxCatalog.resolveSuiteVariantForStop(
                officialKind = OfficialRouteKind.GRAND_PRIX,
                suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                stopIndex = 1,
                stopCount = 4,
                stageCode = "DonkeyKong",
            ),
        )
        // Non-first GP stops use A, not A Alt (even for courses with a_alt like Cinema, SnowMountain, or KoopaTroopaBeach)
        for (course in listOf("Cinema", "SnowMountain", "KoopaTroopaBeach")) {
            assertEquals(
                "a",
                RoadJukeboxCatalog.resolveSuiteVariantForStop(
                    officialKind = OfficialRouteKind.GRAND_PRIX,
                    suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                    stopIndex = 3,
                    stopCount = 4,
                    stageCode = course,
                ),
            )
        }
        // First stop DonkeyKong still uses C
        assertEquals(
            "c",
            RoadJukeboxCatalog.resolveSuiteVariantForStop(
                officialKind = OfficialRouteKind.GRAND_PRIX,
                suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                stopIndex = 0,
                stopCount = 1,
                stageCode = "DonkeyKong",
            ),
        )

        val rallyStops = listOf(
            "DesertHills",
            "DonkeyKong",
            "SnowMountain",
            "Cinema",
            "KoopaTroopaBeach",
            "SFCMarioCircuit",
            "BigTree",
        )
        assertEquals(
            listOf("b", "a_alt", "a_alt", "a_alt", "a_alt", "a", "d"),
            rallyStops.mapIndexed { idx, code ->
                RoadJukeboxCatalog.resolveSuiteVariantForStop(
                    officialKind = OfficialRouteKind.RALLY,
                    suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                    stopIndex = idx,
                    stopCount = rallyStops.size,
                    stageCode = code,
                )
            },
        )

        // Custom route (officialKind = null) under Game Suite uses GP stop rule (0 = C, > 0 = A for all courses including DonkeyKong)
        assertEquals(
            listOf("c", "a", "a"),
            listOf("PeachBeach", "DonkeyKong", "WestCity").mapIndexed { idx, code ->
                RoadJukeboxCatalog.resolveSuiteVariantForStop(
                    officialKind = null,
                    suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                    stopIndex = idx,
                    stopCount = 3,
                    stageCode = code,
                )
            },
        )
        assertEquals(
            listOf("c", "a", "a"),
            listOf("PeachBeach", "WestCity", "DonkeyKong").mapIndexed { idx, code ->
                RoadJukeboxCatalog.resolveSuiteVariantForStop(
                    officialKind = null,
                    suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                    stopIndex = idx,
                    stopCount = 3,
                    stageCode = code,
                )
            },
        )

        // Complete Suite always resolves "a" regardless of route kind, index, or stageCode
        for (kind in listOf(OfficialRouteKind.GRAND_PRIX, OfficialRouteKind.RALLY, null)) {
            assertEquals(
                listOf("a", "a", "a"),
                listOf("FirstCircuit", "DonkeyKong", "BigTree").mapIndexed { idx, code ->
                    RoadJukeboxCatalog.resolveSuiteVariantForStop(
                        officialKind = kind,
                        suiteMode = RoadJukeboxCatalog.RouteSuiteMode.COMPLETE_SUITE,
                        stopIndex = idx,
                        stopCount = 3,
                        stageCode = code,
                    )
                },
            )
        }

        // Custom route with KNOCKOUT_DIRECT road rule follows Knockout Tour suite progression (b, a / a_alt, d)
        assertEquals(
            listOf("b", "a", "a_alt", "a_alt", "d"),
            listOf("DesertHills", "FirstCircuit", "DonkeyKong", "KoopaTroopaBeach", "ChocoMountain").mapIndexed { idx, code ->
                RoadJukeboxCatalog.resolveSuiteVariantForStop(
                    officialKind = null,
                    suiteMode = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
                    stopIndex = idx,
                    stopCount = 5,
                    stageCode = code,
                    roadRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.KNOCKOUT_DIRECT,
                )
            },
        )
    }

    @Test
    fun vsAutoClockAndKnockoutDirectWorkOnCustomAndOfficialRoutesWithBiomeMetadata() {
        val connections = connectionsCatalog()
        val wiki = RoadJukeboxCatalog.RouteMusicCatalog.parse(
            File("src/main/assets/route_music_catalog.json").readText(),
        )!!
        val installed = wiki.tracks.map { rule ->
            trackItem(
                key = "jb_${rule.jukeboxCode}",
                hexDigit = 'b',
                jukeboxCode = rule.jukeboxCode,
                name = "Track ${rule.jukeboxCode}",
            )
        }

        // 03:00–19:00 Day, 19:00–03:00 Night
        assertFalse(RoadJukeboxCatalog.isNightHour(3))
        assertFalse(RoadJukeboxCatalog.isNightHour(18))
        assertTrue(RoadJukeboxCatalog.isNightHour(19))
        assertTrue(RoadJukeboxCatalog.isNightHour(2))

        val route = CustomRoute(
            id = "auto_clock",
            name = "Auto Clock",
            stops = listOf(CustomRouteStop("FirstCircuit"), CustomRouteStop("WestCity")),
            edges = listOf(CustomRouteEdgeConfig("FirstCircuit", "WestCity")),
            roadRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO,
        )
        val dayReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = route,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
            isNightNow = false,
        )
        val dayRes = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(dayReport.legs.single().resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.VS_DAY, dayRes.ruleModeUsed)
        assertEquals("mesa", dayRes.originBiomeKey)

        val nightReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = route,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
            isNightNow = true,
        )
        val nightRes = assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete>(nightReport.legs.single().resolution)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.VS_NIGHT, nightRes.ruleModeUsed)
        assertEquals(null, nightRes.originBiomeKey)

        val knockoutCustom = route.copy(roadRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.KNOCKOUT_DIRECT)
        val koReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = knockoutCustom,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            wikiCatalog = wiki,
        )
        assertTrue(koReport.canPlay)
        assertIs<RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect>(koReport.legs.single().resolution)
    }

    @Test
    fun defaultRoadRuleIsVsAutoAndFromStoredPreservesExplicitAuto() {
        assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, RoadJukeboxCatalog.DEFAULT_ROAD_RULE_MODE)
        assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, RoadJukeboxCatalog.RoadMusicRuleMode.fromStored(null))
        assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, RoadJukeboxCatalog.RoadMusicRuleMode.fromStored(""))
        assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, RoadJukeboxCatalog.RoadMusicRuleMode.fromStored("unknown"))
        assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.AUTO, RoadJukeboxCatalog.RoadMusicRuleMode.fromStored("AUTO"))
        assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.GRAND_PRIX, RoadJukeboxCatalog.RoadMusicRuleMode.fromStored("GRAND_PRIX"))

        val defaultCustom = CustomRoute(
            id = "def",
            name = "Default Route",
            stops = listOf(CustomRouteStop("FirstCircuit"), CustomRouteStop("WestCity")),
            edges = listOf(CustomRouteEdgeConfig("FirstCircuit", "WestCity")),
        )
        assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, defaultCustom.roadRuleMode)
        assertFalse(defaultCustom.pureFreeRoamConfig.enabled)
    }

    @Test
    fun pureFreeRoamLegPlansRespectCountDurationOverridesAndKnockoutGpFallbacks() {
        val connections = connectionsCatalog()
        val wiki = RoadJukeboxCatalog.RouteMusicCatalog.parse(
            File("src/main/assets/route_music_catalog.json").readText(),
        )!!
        val installed = wiki.tracks.map { rule ->
            trackItem(
                key = "jb_${rule.jukeboxCode}",
                hexDigit = 'c',
                jukeboxCode = rule.jukeboxCode,
                name = "Track ${rule.jukeboxCode}",
            )
        }

        // Config normalization + JSON round-trip
        val clamped = RoadJukeboxCatalog.PureFreeRoamConfig(
            enabled = true,
            controlMode = RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION,
            defaultTracksPerLeg = 99,
            defaultMinutesPerLeg = 0,
        ).normalized()
        assertEquals(10, clamped.defaultTracksPerLeg)
        assertEquals(1, clamped.defaultMinutesPerLeg)
        val roundTripped = RoadJukeboxCatalog.PureFreeRoamConfig.fromJsonObject(clamped.toJsonObject())
        assertEquals(clamped, roundTripped)
        assertFalse(RoadJukeboxCatalog.PureFreeRoamConfig.fromJsonObject(null).enabled)

        // Custom route with 3 legs: leg 0 has custom track override, leg 1 muted, leg 2 uses default
        val custom = CustomRoute(
            id = "fr_custom",
            name = "Free Roam Tour",
            stops = listOf(
                CustomRouteStop("FirstCircuit"),
                CustomRouteStop("WestCity"),
                CustomRouteStop("Central"),
                CustomRouteStop("Rainbow"),
            ),
            edges = listOf(
                CustomRouteEdgeConfig("FirstCircuit", "WestCity", musicEnabled = true, freeRoamTrackCountOverride = 4, freeRoamDurationMinutesOverride = 12),
                CustomRouteEdgeConfig("WestCity", "Central", musicEnabled = false),
                CustomRouteEdgeConfig("Central", "Rainbow", musicEnabled = true),
            ),
            roadRuleMode = RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO,
            pureFreeRoamConfig = RoadJukeboxCatalog.PureFreeRoamConfig(
                enabled = true,
                controlMode = RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT,
                defaultTracksPerLeg = 2,
                defaultMinutesPerLeg = 5,
            ),
        )
        val dayPlans = RoadJukeboxCatalog.buildPureFreeRoamLegPlans(custom, wikiCatalog = wiki, isNightNow = false)
        assertEquals(2, dayPlans.size) // muted leg 1 skipped
        assertEquals(0, dayPlans[0].edgeIndex)
        assertEquals(4, dayPlans[0].trackCount)
        assertEquals(12 * 60_000L, dayPlans[0].durationMs)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.VS_DAY, dayPlans[0].effectiveRule)
        assertEquals(RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT, dayPlans[0].controlMode)
        assertTrue(dayPlans[0].rankedCandidates.isNotEmpty())

        assertEquals(2, dayPlans[1].edgeIndex)
        assertEquals(2, dayPlans[1].trackCount)
        assertEquals(5 * 60_000L, dayPlans[1].durationMs)
        assertTrue(dayPlans[1].rankedCandidates.isNotEmpty())

        // Official Rally under AUTO (normally KNOCKOUT_DIRECT) falls back to VS_NIGHT at night in Pure Free Roam
        val rally = OfficialRoute(
            id = "golden_rally",
            kind = OfficialRouteKind.RALLY,
            names = mapOf("en" to "Golden Rally"),
            stops = listOf(
                OfficialStop("r1", 1, "DesertHills"),
                OfficialStop("r2", 2, "ShyGuyBazaar"),
            ),
            requiresSpecialUnlock = false,
            sourceUrl = "https://www.mariowiki.com/Golden_Rally",
        )
        val rallyPlans = RoadJukeboxCatalog.buildPureFreeRoamLegPlans(
            route = rally,
            config = RoadJukeboxCatalog.PureFreeRoamConfig(
                enabled = true,
                controlMode = RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION,
                defaultMinutesPerLeg = 7,
            ),
            ruleMode = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
            edgeOverrides = mapOf(0 to RoadJukeboxCatalog.EdgeFreeRoamOverride(durationMinutesOverride = 15)),
            wikiCatalog = wiki,
            isNightNow = true,
        )
        assertEquals(1, rallyPlans.size)
        assertEquals(RoadJukeboxCatalog.EffectiveRuleMode.VS_NIGHT, rallyPlans[0].effectiveRule)
        assertEquals(RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION, rallyPlans[0].controlMode)
        assertEquals(15 * 60_000L, rallyPlans[0].durationMs)
        assertEquals(38, rallyPlans[0].rankedCandidates.size)

        // Inspection in Pure Free Roam skips course-audio playability check but still enforces Special Cup lock
        val playableEvenWithoutCourseAudio = RoadJukeboxCatalog.inspectCustomRoute(
            route = custom,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { false },
            isStageHidden = { false },
            wikiCatalog = wiki,
            isNightNow = false,
        )
        assertTrue(playableEvenWithoutCourseAudio.canPlay)

        val stillBlockedWhenLocked = RoadJukeboxCatalog.inspectCustomRoute(
            route = custom,
            connections = connections,
            pool = RoadJukeboxCatalog.CandidatePool(),
            installedJukebox = installed,
            isStagePlayable = { false },
            isStageHidden = { it == "Rainbow" },
            wikiCatalog = wiki,
            isNightNow = false,
        )
        assertFalse(stillBlockedWhenLocked.canPlay)
        assertIs<RoadJukeboxCatalog.BlockingCause.UnplayableStages>(stillBlockedWhenLocked.blockingCause)
    }

    @Test
    fun pureFreeRoamTrackPoolJsonRoundTripBackwardCompatAndInGameModeCoercion() {
        // Legacy JSON missing trackPool & freeRoamMode defaults cleanly to ROUTE_RULES + AUTO_CLOCK
        val legacyJson = JSONObject()
            .put("enabled", true)
            .put("controlMode", "DURATION")
            .put("defaultTracksPerLeg", 3)
            .put("defaultMinutesPerLeg", 8)
        val parsedLegacy = RoadJukeboxCatalog.PureFreeRoamConfig.fromJsonObject(legacyJson)
        assertTrue(parsedLegacy.enabled)
        assertEquals(RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION, parsedLegacy.controlMode)
        assertEquals(3, parsedLegacy.defaultTracksPerLeg)
        assertEquals(8, parsedLegacy.defaultMinutesPerLeg)
        assertEquals(RoadJukeboxCatalog.PureFreeRoamTrackPool.ROUTE_RULES, parsedLegacy.trackPool)
        assertEquals(FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK, parsedLegacy.freeRoamMode)

        // Full round-trip for GAME_FREE_ROAM + SOLO_ALL and FREEROAM_ONLY
        for (mode in listOf(
            FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK,
            FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_DAY,
            FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_NIGHT,
            FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_ALL,
            FreeRoamJukeboxCatalog.FreeRoamMode.FREEROAM_ONLY,
        )) {
            val cfg = RoadJukeboxCatalog.PureFreeRoamConfig(
                enabled = true,
                controlMode = RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT,
                defaultTracksPerLeg = 4,
                defaultMinutesPerLeg = 6,
                trackPool = RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM,
                freeRoamMode = mode,
            )
            val roundTripped = RoadJukeboxCatalog.PureFreeRoamConfig.fromJsonObject(cfg.toJsonObject())
            assertEquals(cfg, roundTripped)
        }

        // Storing ONLINE_DAY or ONLINE_NIGHT coerces back to AUTO_CLOCK for in-game Free Roam config
        val onlineCoerced = RoadJukeboxCatalog.PureFreeRoamConfig(
            enabled = true,
            trackPool = RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM,
            freeRoamMode = FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_NIGHT,
        ).normalized()
        assertEquals(FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK, onlineCoerced.freeRoamMode)

        val onlineJson = JSONObject()
            .put("enabled", true)
            .put("trackPool", "GAME_FREE_ROAM")
            .put("freeRoamMode", "ONLINE_DAY")
        val parsedOnline = RoadJukeboxCatalog.PureFreeRoamConfig.fromJsonObject(onlineJson)
        assertEquals(RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM, parsedOnline.trackPool)
        assertEquals(FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK, parsedOnline.freeRoamMode)
    }

    @Test
    fun pureFreeRoamGameFreeRoamPoolLegPlansAndRouteValidation() {
        val connections = connectionsCatalog()
        val wiki = RoadJukeboxCatalog.RouteMusicCatalog.parse(
            File("src/main/assets/route_music_catalog.json").readText(),
        )!!
        val item1 = trackItem("track_1", '1', "JB01", "Road Tune 1")
        val item2 = trackItem("track_2", '2', "JB02", "Road Tune 2")
        val installed = listOf(item1, item2)

        // Incomplete per-edge pool on DonkeyKong -> FirstCircuit (JB02 unreadable)
        val pool = RoadJukeboxCatalog.CandidatePool(
            dataVersion = 1,
            entries = mapOf(
                RoadJukeboxCatalog.EdgeKey("DonkeyKong", "FirstCircuit") to listOf("JB01", "JB02"),
            ),
        )

        val gamePoolConfig = RoadJukeboxCatalog.PureFreeRoamConfig(
            enabled = true,
            controlMode = RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT,
            defaultTracksPerLeg = 3,
            defaultMinutesPerLeg = 9,
            trackPool = RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM,
            freeRoamMode = FreeRoamJukeboxCatalog.FreeRoamMode.FREEROAM_ONLY,
        )
        val custom = CustomRoute(
            id = "fr_game_pool",
            name = "Game Free Roam Drive",
            stops = listOf(
                CustomRouteStop("DonkeyKong"),
                CustomRouteStop("FirstCircuit"),
                CustomRouteStop("WestCity"),
            ),
            edges = listOf(
                CustomRouteEdgeConfig("DonkeyKong", "FirstCircuit", musicEnabled = true, freeRoamTrackCountOverride = 5),
                CustomRouteEdgeConfig("FirstCircuit", "WestCity", musicEnabled = true),
            ),
            pureFreeRoamConfig = gamePoolConfig,
        )

        val plans = RoadJukeboxCatalog.buildPureFreeRoamLegPlans(custom, wikiCatalog = wiki, isNightNow = false)
        assertEquals(2, plans.size)
        for (plan in plans) {
            assertEquals(RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM, plan.trackPool)
            assertEquals(FreeRoamJukeboxCatalog.FreeRoamMode.FREEROAM_ONLY, plan.freeRoamMode)
            assertTrue(plan.rankedCandidates.isEmpty(), "GAME_FREE_ROAM leg plans must not pull route-rule candidates")
        }
        assertEquals(5, plans[0].trackCount)
        assertEquals(3, plans[1].trackCount)

        // With GAME_FREE_ROAM, incomplete per-edge road pool does NOT block validation while Jukebox is installed
        val gamePoolReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = custom,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { false },
            isStageHidden = { false },
            isJukeboxReadable = { it.key == "track_1" },
            wikiCatalog = wiki,
        )
        assertTrue(gamePoolReport.canPlay)
        assertTrue(gamePoolReport.incompletePoolEdges.isEmpty())

        // Without any readable Jukebox tracks at all, GAME_FREE_ROAM blocks playback
        val noJukeboxReport = RoadJukeboxCatalog.inspectCustomRoute(
            route = custom,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { true },
            isStageHidden = { false },
            isJukeboxReadable = { false },
            wikiCatalog = wiki,
        )
        assertFalse(noJukeboxReport.canPlay)

        // Official route with GAME_FREE_ROAM also bypasses incomplete per-edge pool when Jukebox is readable
        val official = OfficialRoute(
            id = "dk_test_cup",
            kind = OfficialRouteKind.GRAND_PRIX,
            names = mapOf("en" to "DK Test Cup"),
            stops = listOf(
                OfficialStop("s1", 1, "DonkeyKong"),
                OfficialStop("s2", 2, "FirstCircuit"),
            ),
            requiresSpecialUnlock = false,
            sourceUrl = "https://www.mariowiki.com/",
        )
        val officialGamePoolReport = RoadJukeboxCatalog.inspectOfficialRoute(
            route = official,
            connections = connections,
            pool = pool,
            installedJukebox = installed,
            isStagePlayable = { false },
            isStageHidden = { false },
            isJukeboxReadable = { it.key == "track_1" },
            wikiCatalog = wiki,
            pureFreeRoamEnabled = true,
            pureFreeRoamTrackPool = RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM,
        )
        assertTrue(officialGamePoolReport.canPlay)
        assertTrue(officialGamePoolReport.incompletePoolEdges.isEmpty())
    }
}

