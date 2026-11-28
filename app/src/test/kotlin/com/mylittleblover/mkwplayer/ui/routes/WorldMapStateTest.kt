package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.*

class WorldMapStateTest {
    @Test fun routeSelectedBeforeFirstLayoutFitsOnceAndKeepsLaterGestures() {
        val state = WorldMapState()
        val points = listOf(androidx.compose.ui.geometry.Offset(.1f, .2f), androidx.compose.ui.geometry.Offset(.9f, .8f))
        state.selectRoute("mushroom_cup", points)
        val transform = state.configure(400f, 800f, 1f, 16f)
        for (point in points) {
            val screen = transform.project(point.x, point.y)
            assertTrue(screen.x in 0f..400f && screen.y in 0f..800f)
        }
        state.zoomBy(1.5f)
        val camera = state.camera
        state.configure(400f, 800f, 1f, 16f)
        assertEquals(camera, state.camera)
    }
    @Test fun cameraAndSelectedIdentitiesSurviveSavedStateRestoration() {
        val original = WorldMapState(MapCamera(3.2f, .45f, .55f), "mushroom_cup", "WestCity")
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(WorldMapState.Saver) { scope.save(original) }
        val restored = assertNotNull(WorldMapState.Saver.restore(assertNotNull(saved)))
        assertEquals(original.camera, restored.camera)
        assertEquals("mushroom_cup", restored.selectedRouteId)
        assertEquals("WestCity", restored.selectedCourseCode)
    }
    @Test fun selectingACourseNeverChangesTheOfficialRoute() {
        val state = WorldMapState(routeId = "mushroom_cup")
        state.selectCourse("FirstCircuit_00")
        assertEquals("FirstCircuit", state.selectedCourseCode)
        assertEquals("mushroom_cup", state.selectedRouteId)
        state.clearCourse()
        assertNull(state.selectedCourseCode)
        assertEquals("mushroom_cup", state.selectedRouteId)
        state.selectCourse("WestCity")
        state.selectRoute("flower_cup")
        assertNull(state.selectedCourseCode)
        assertEquals("flower_cup", state.selectedRouteId)
        state.reset()
        assertEquals(MapCamera(1f), state.camera)
        assertEquals("flower_cup", state.selectedRouteId)
    }

    @Test fun customRouteDraftSupportsEdgeMusicAndTailUndo() {
        val state = WorldMapState()
        state.enterEdit()
        state.editStart("FirstCircuit")
        state.editAppend("FirstCircuit", "CrownCity")
        state.editAppend("CrownCity", "Central")
        state.setEdgeMusic(0, musicEnabled = true, selectedJukeboxKey = "jb_a")
        state.setEdgeMusic(1, musicEnabled = false, selectedJukeboxKey = null)
        assertEquals(listOf("FirstCircuit", "CrownCity", "Central"), state.draftStops())
        assertEquals("jb_a", state.edgeConfigAt(0).selectedJukeboxKey)
        assertFalse(state.edgeConfigAt(1).musicEnabled)

        state.editUndo()
        assertEquals(listOf("FirstCircuit", "CrownCity"), state.draftStops())
        assertEquals("CrownCity", state.editFrom)
        assertEquals("jb_a", state.edgeConfigAt(0).selectedJukeboxKey)
        assertTrue(state.draftEdgeConfigs.containsKey(0))
        assertFalse(state.draftEdgeConfigs.containsKey(1))

        val savedRoute = com.mylittleblover.mkwplayer.routes.CustomRoute(
            id = "route_1",
            name = "My Scenic Drive",
            stops = listOf(
                com.mylittleblover.mkwplayer.routes.CustomRouteStop("FirstCircuit", "user:p1"),
                com.mylittleblover.mkwplayer.routes.CustomRouteStop("Central", null),
            ),
            edges = listOf(com.mylittleblover.mkwplayer.routes.CustomRouteEdgeConfig(from = "FirstCircuit", to = "Central", musicEnabled = true, selectedJukeboxKey = "jb_x")),
            loopWholeRoute = true,
        )
        state.loadCustomRoute(savedRoute)
        assertEquals("route_1", state.editingRouteId)
        assertEquals("My Scenic Drive", state.editingRouteName)
        assertTrue(state.draftLoopWholeRoute)
        assertEquals("user:p1", state.draftStopPresets[0])
        assertEquals("jb_x", state.edgeConfigAt(0).selectedJukeboxKey)
    }

    @Test fun mapInteractionShowsOutgoingRoadsOnlyForActiveEditStopAndDimsNonIncludedPins() {
        val connections = com.mylittleblover.mkwplayer.library.StageConnections.parse(
            java.io.File("src/main/assets/stage_connections.json").readText(),
        )!!
        val visibleStages = connections.sources().toSet() + connections.sinks().toSet()

        // #2: Outside edit mode, selecting a course NEVER produces map reachable outgoing segments.
        val state = WorldMapState()
        state.selectCourse("FirstCircuit")
        assertNull(activeMapReachableOrigin(state.editing, state.editFrom, visibleStages))
        assertTrue(activeMapReachableNeighbours(state.editing, state.editFrom, connections, visibleStages).isEmpty())

        // #3: Outside edit mode with single course selected, only that course stays bright; others (even neighbours) dim.
        val selPin = computeMapPinVisualState(
            stageCode = "FirstCircuit",
            editing = false,
            selectedCourseCode = "FirstCircuit",
            selectedRouteStages = emptySet(),
            editFrom = null,
            draftStages = emptySet(),
            reachableEditNeighbours = emptySet(),
        )
        assertTrue(selPin.isSelectedOrActive && selPin.isHighlighted && !selPin.isDimmed)
        val nonSelNeighbour = computeMapPinVisualState(
            stageCode = "WestCity",
            editing = false,
            selectedCourseCode = "FirstCircuit",
            selectedRouteStages = emptySet(),
            editFrom = null,
            draftStages = emptySet(),
            reachableEditNeighbours = emptySet(),
        )
        assertFalse(nonSelNeighbour.isSelectedOrActive)
        assertTrue(nonSelNeighbour.isDimmed)

        // #2: Entering edit mode clears prior browse selection and defaults roadRuleMode to VS_AUTO.
        state.enterEdit()
        assertNull(state.selectedCourseCode)
        assertEquals(com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO, state.draftRoadRuleMode)
        assertNull(activeMapReachableOrigin(state.editing, state.editFrom, visibleStages))

        // Selecting starting stop A shows ONLY A's outgoing connections.
        state.editStart("FirstCircuit")
        assertEquals("FirstCircuit", activeMapReachableOrigin(state.editing, state.editFrom, visibleStages))
        val firstNeighbours = activeMapReachableNeighbours(state.editing, state.editFrom, connections, visibleStages)
        assertTrue("WestCity" in firstNeighbours)

        // Extending to stop B immediately switches outgoing connections from A to B only.
        state.editAppend("FirstCircuit", "WestCity")
        assertEquals("WestCity", activeMapReachableOrigin(state.editing, state.editFrom, visibleStages))
        val secondNeighbours = activeMapReachableNeighbours(state.editing, state.editFrom, connections, visibleStages).toSet()
        assertEquals(connections.neighbours("WestCity").filter { it in visibleStages }.toSet(), secondNeighbours)

        // #3: In edit mode after A -> B, A (in draft) and B (active) and B's reachable neighbours stay bright;
        // an unchosen old neighbour of A that is not reachable from B dims immediately.
        val draftStages = state.draftStops().toSet()
        val oldStopState = computeMapPinVisualState("FirstCircuit", true, null, emptySet(), state.editFrom, draftStages, secondNeighbours)
        assertFalse(oldStopState.isSelectedOrActive)
        assertTrue(oldStopState.isHighlighted && !oldStopState.isDimmed)

        val activeStopState = computeMapPinVisualState("WestCity", true, null, emptySet(), state.editFrom, draftStages, secondNeighbours)
        assertTrue(activeStopState.isSelectedOrActive && !activeStopState.isDimmed)

        val unchosen = (firstNeighbours.toSet() - draftStages - secondNeighbours).firstOrNull()
        if (unchosen != null) {
            val dimmedOldBranch = computeMapPinVisualState(unchosen, true, null, emptySet(), state.editFrom, draftStages, secondNeighbours)
            assertTrue(dimmedOldBranch.isDimmed)
        }

        // #5: Per-edge Free Roam overrides survive setEdgeMusic and loadCustomRoute.
        state.setEdgeFreeRoamOverride(0, trackCountOverride = 6, durationMinutesOverride = 14)
        state.setEdgeMusic(0, musicEnabled = true, selectedJukeboxKey = "jb_road")
        assertEquals(6, state.edgeConfigAt(0).freeRoamTrackCountOverride)
        assertEquals(14, state.edgeConfigAt(0).freeRoamDurationMinutesOverride)
        assertEquals("jb_road", state.edgeConfigAt(0).selectedJukeboxKey)

        // Exiting edit mode immediately hides outgoing segments.
        state.exitEdit()
        assertTrue(activeMapReachableNeighbours(state.editing, state.editFrom, connections, visibleStages).isEmpty())
    }

    @Test fun smoothEuclideanDistanceFieldIsIsotropicAcrossCardinalAndDiagonalAngles() {
        val size = 65
        val cx = 32
        val cy = 32
        val radius = 14f
        val alpha = FloatArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - cx).toFloat()
                val dy = (y - cy).toFloat()
                if (dx * dx + dy * dy <= radius * radius) {
                    alpha[y * size + x] = 1f
                }
            }
        }
        val dist = computeSmoothedEuclideanDistance2d(alpha, size, size, maxCap = 20f)
        // Inside disk center must be 0.
        assertEquals(0f, dist[cy * size + cx], 0.05f)
        // At equal radial distance 22px (8px outside circle): cardinal (32+22, 32) vs diagonal (32+15.56, 32+15.56).
        val cardinal = dist[cy * size + (cx + 22)]
        val diagonal = dist[(cy + 16) * size + (cx + 15)] // hypot(16, 15) = 21.93
        assertTrue(kotlin.math.abs(cardinal - diagonal) < 0.6f, "Expected isotropic EDT, got cardinal=$cardinal vs diagonal=$diagonal")
        assertTrue(cardinal in 7.2f..8.8f, "Expected distance ~8px outside circle, got $cardinal")
    }
}

