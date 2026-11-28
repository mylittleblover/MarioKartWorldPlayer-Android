package com.mylittleblover.mkwplayer.routes

import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.library.StageConnections
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomRouteStoreTest {

    private fun connectionsCatalog(): StageConnections.Catalog =
        StageConnections.parse(File("src/main/assets/stage_connections.json").readText())!!

    @Test
    fun crudTailOnlyTopologyRevisitStagesAndPresetCleanupWorkAtomically() {
        val connections = connectionsCatalog()
        val file = Files.createTempDirectory("mkw-custom-routes").resolve("routes/custom_routes.json").toFile()
        val store = CustomRouteStore(file)

        // Revisiting stages in the same route (FirstCircuit -> WestCity -> FirstCircuit) keeps distinct stop occurrences (#77).
        val stops = listOf(
            CustomRouteStop("FirstCircuit_00", "user:p1"),
            CustomRouteStop("WestCity", null),
            CustomRouteStop("FirstCircuit", PresetRefs.SYSTEM_KNOCKOUT),
        )
        val edges = listOf(
            CustomRouteEdgeConfig("FirstCircuit", "WestCity", musicEnabled = true, selectedJukeboxKey = "jb_leg_0"),
            CustomRouteEdgeConfig("WestCity", "FirstCircuit", musicEnabled = false, selectedJukeboxKey = null),
        )
        val created = store.create(
            name = "Looping Circuit",
            stops = stops,
            edges = edges,
            connections = connections,
            loopWholeRoute = true,
            nowEpochMs = 100L,
        ).getOrThrow()
        assertEquals(1, store.read().getOrThrow().size)
        assertEquals(listOf("FirstCircuit", "WestCity", "FirstCircuit"), created.stops.map { it.stageCode })
        assertEquals("user:p1", created.stops[0].presetId)
        assertEquals(PresetRefs.SYSTEM_KNOCKOUT, created.stops[2].presetId)
        assertTrue(created.loopWholeRoute)

        // Cannot delete an interior node (index 0 or 1); only tail deletion (last stop) is permitted (#79).
        assertFalse(store.canRemoveStopAt(created, 0))
        assertFalse(store.canRemoveStopAt(created, 1))
        assertTrue(store.canRemoveStopAt(created, 2))
        assertTrue(store.removeStopAt(created.id, 1).isFailure)

        // Reject saving a modified middle stop (#79), even if length stays the same.
        val modifiedMiddle = listOf(
            CustomRouteStop("FirstCircuit", "user:p1"),
            CustomRouteStop("DonkeyKong", null),
            CustomRouteStop("FirstCircuit", PresetRefs.SYSTEM_KNOCKOUT),
        )
        val modifiedEdges = listOf(
            CustomRouteEdgeConfig("FirstCircuit", "DonkeyKong"),
            CustomRouteEdgeConfig("DonkeyKong", "FirstCircuit"),
        )
        assertTrue(store.saveDraftOver(created.id, "Looping Circuit", modifiedMiddle, modifiedEdges, connections, true).isFailure)

        // Tail removal drops stop 2 and edge 1 cleanly.
        val afterTailRemove = store.removeLastStop(created.id).getOrThrow().single()
        assertEquals(listOf("FirstCircuit", "WestCity"), afterTailRemove.stops.map { it.stageCode })
        assertEquals(1, afterTailRemove.edges.size)
        assertFalse(store.canRemoveStopAt(afterTailRemove, 1))

        // Tail append + edge music update + per-stop preset override (#78, #79).
        val appended = store.appendStop(created.id, "Central", connections).getOrThrow().single()
        assertEquals(listOf("FirstCircuit", "WestCity", "Central"), appended.stops.map { it.stageCode })
        assertEquals(2, appended.edges.size)

        val updatedPreset = store.setStopPreset(created.id, 2, "user:p2").getOrThrow().single()
        assertEquals("user:p2", updatedPreset.stops[2].presetId)

        // Deleting preset "user:p1" resets only stop 0 referencing it, preserving stop 2's "user:p2" (#50).
        val afterClear = store.clearPresetReferences("user:p1").getOrThrow().single()
        assertNull(afterClear.stops[0].presetId)
        assertEquals("user:p2", afterClear.stops[2].presetId)

        // Reload from disk verifies atomic persistence.
        val reloaded = CustomRouteStore(file).read().getOrThrow().single()
        assertEquals(afterClear, reloaded)
        assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, reloaded.roadRuleMode)
        assertNotNull(store.find(created.id).getOrThrow())
    }

    @Test
    fun pureFreeRoamConfigEdgeOverridesLegacyJsonAndOfficialPrefsPersistAtomically() {
        val connections = connectionsCatalog()
        val tempDir = Files.createTempDirectory("mkw-freeroam-store").toFile()
        try {
            val routeFile = File(tempDir, "routes/custom_routes.json")
            val store = CustomRouteStore(routeFile)
            val created = store.create(
                name = "Scenic Cruise",
                stops = listOf(CustomRouteStop("FirstCircuit"), CustomRouteStop("WestCity"), CustomRouteStop("Central")),
                edges = listOf(
                    CustomRouteEdgeConfig("FirstCircuit", "WestCity", freeRoamTrackCountOverride = 5, freeRoamDurationMinutesOverride = 12),
                    CustomRouteEdgeConfig("WestCity", "Central"),
                ),
                connections = connections,
                pureFreeRoamConfig = RoadJukeboxCatalog.PureFreeRoamConfig(
                    enabled = true,
                    controlMode = RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION,
                    defaultTracksPerLeg = 3,
                    defaultMinutesPerLeg = 8,
                ),
            ).getOrThrow()

            assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, created.roadRuleMode)
            assertTrue(created.pureFreeRoamConfig.enabled)
            assertEquals(RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION, created.pureFreeRoamConfig.controlMode)
            assertEquals(5, created.edges[0].freeRoamTrackCountOverride)
            assertEquals(12, created.edges[0].freeRoamDurationMinutesOverride)
            assertNull(created.edges[1].freeRoamTrackCountOverride)

            // setEdgeMusic preserves existing free roam overrides on that edge.
            store.setEdgeMusic(created.id, 0, musicEnabled = true, selectedJukeboxKey = "jb_pinned").getOrThrow()
            val afterMusic = store.find(created.id).getOrThrow()!!
            assertEquals(5, afterMusic.edges[0].freeRoamTrackCountOverride)
            assertEquals(12, afterMusic.edges[0].freeRoamDurationMinutesOverride)
            assertEquals("jb_pinned", afterMusic.edges[0].selectedJukeboxKey)

            // setEdgeFreeRoamOverride clamps out-of-range values and updates cleanly.
            store.setEdgeFreeRoamOverride(created.id, 1, trackCountOverride = 99, durationMinutesOverride = 0).getOrThrow()
            store.setPureFreeRoamConfig(
                created.id,
                RoadJukeboxCatalog.PureFreeRoamConfig(
                    enabled = true,
                    controlMode = RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT,
                    defaultTracksPerLeg = 4,
                    defaultMinutesPerLeg = 10,
                ),
            ).getOrThrow()

            val reloaded = CustomRouteStore(routeFile).read().getOrThrow().single()
            assertEquals(RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT, reloaded.pureFreeRoamConfig.controlMode)
            assertEquals(4, reloaded.pureFreeRoamConfig.defaultTracksPerLeg)
            assertEquals(10, reloaded.edges[1].freeRoamTrackCountOverride)
            assertEquals(1, reloaded.edges[1].freeRoamDurationMinutesOverride)

            // Legacy JSON without pureFreeRoamConfig or roadRuleMode decodes cleanly with VS_AUTO + disabled config.
            val legacyFile = File(tempDir, "routes/legacy_routes.json").apply {
                parentFile?.mkdirs()
                writeText(
                    """
                    {
                      "schemaVersion": 1,
                      "routes": [
                        {
                          "id": "legacy-1",
                          "name": "Legacy Route",
                          "stops": [{"stageCode":"FirstCircuit"},{"stageCode":"WestCity"}],
                          "edges": [{"from":"FirstCircuit","to":"WestCity","musicEnabled":true}]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            }
            val legacy = CustomRouteStore(legacyFile).read().getOrThrow().single()
            assertEquals(RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, legacy.roadRuleMode)
            assertFalse(legacy.pureFreeRoamConfig.enabled)
            assertNull(legacy.edges.single().freeRoamTrackCountOverride)
            assertNull(legacy.edges.single().freeRoamDurationMinutesOverride)

            // OfficialRouteFreeRoamPrefs persistence & clearing.
            val prefsFile = File(tempDir, "routes/official_free_roam_prefs.json")
            val prefs = OfficialRouteFreeRoamPrefs(prefsFile)
            assertFalse(prefs.get("mushroom_cup").config.enabled)

            prefs.setConfig(
                "mushroom_cup",
                RoadJukeboxCatalog.PureFreeRoamConfig(
                    enabled = true,
                    controlMode = RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION,
                    defaultMinutesPerLeg = 9,
                ),
            ).getOrThrow()
            prefs.setEdgeOverride(
                "mushroom_cup",
                1,
                RoadJukeboxCatalog.EdgeFreeRoamOverride(durationMinutesOverride = 15),
            ).getOrThrow()

            val reloadedOfficial = OfficialRouteFreeRoamPrefs(prefsFile).get("mushroom_cup")
            assertTrue(reloadedOfficial.config.enabled)
            assertEquals(9, reloadedOfficial.config.defaultMinutesPerLeg)
            assertEquals(15, reloadedOfficial.edgeOverrides[1]?.durationMinutesOverride)

            prefs.setEdgeOverride("mushroom_cup", 1, null).getOrThrow()
            assertTrue(OfficialRouteFreeRoamPrefs(prefsFile).get("mushroom_cup").edgeOverrides.isEmpty())

            prefs.clear("mushroom_cup").getOrThrow()
            assertFalse(OfficialRouteFreeRoamPrefs(prefsFile).get("mushroom_cup").config.enabled)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

