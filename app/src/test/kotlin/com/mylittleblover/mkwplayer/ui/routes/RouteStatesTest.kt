package com.mylittleblover.mkwplayer.ui.routes

import com.mylittleblover.mkwplayer.player.UnlockUi
import com.mylittleblover.mkwplayer.routes.MapPosition
import com.mylittleblover.mkwplayer.routes.RoadConnection
import com.mylittleblover.mkwplayer.routes.Route
import com.mylittleblover.mkwplayer.routes.RouteStop
import com.mylittleblover.mkwplayer.routes.SavedRoute
import com.mylittleblover.mkwplayer.routes.StageId
import com.mylittleblover.mkwplayer.routes.StageNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The map and the route editor decide two things a screenshot cannot check: whether a stage without
 * a position still exists as far as the user is concerned, and whether the gated cup can be reached
 * through a pin or a saved route.
 *
 * The editor group pins its reasons against [Route]'s own `require` messages. A second phrasing of
 * the same rule is how an editor ends up refusing for a reason the model never states.
 */
class RouteStatesTest {

    // ---------------------------------------------------------------- fixtures

    private fun node(
        id: String,
        x: Double? = null,
        y: Double? = null,
        name: String = id,
    ): StageNode = StageNode(
        id = StageId(id),
        displayName = name,
        position = if (x == null || y == null) null else MapPosition(x, y),
        musicAssets = emptyList(),
    )

    private fun stage(id: String): RouteStop = RouteStop.Stage(StageId(id))

    private fun unlock(visible: Boolean): UnlockUi = UnlockUi(
        progress = "0 of 3 stages heard",
        remaining = "3 still to hear",
        ruleNotice = "Provisional rule",
        offered = visible,
        ceremonyPlaying = false,
        ceremonyCompleted = false,
        congratulationOwed = false,
        canEnter = visible,
        visible = visible,
        hiddenReason = if (visible) null else "Not yet",
    )

    private val cupCourses = setOf("Rainbow", "SpecialCup1")

    private fun shown(nodes: List<StageNode>, visible: Boolean = true): List<MapPin> =
        pins(nodes, cupCourses, unlock(visible))

    private fun pin(nodes: List<StageNode>, id: String): MapPin =
        shown(nodes).first { it.id == id }

    // ---------------------------------------------------------------- pin normalisation

    @Test
    fun theDefaultBoxIsTheAxisAlignedExtentOfThePlacedStages() {
        // (10,20)..(110,220) is the box the data spans; the corners normalise to 0 and 1.
        val nodes = listOf(node("a", x = 10.0, y = 20.0), node("b", x = 110.0, y = 220.0))

        assertEquals(0f, pin(nodes, "a").xFraction)
        assertEquals(0f, pin(nodes, "a").yFraction)
        assertEquals(1f, pin(nodes, "b").xFraction)
        assertEquals(1f, pin(nodes, "b").yFraction)
    }

    @Test
    fun anInteriorStageIsNormalisedProportionally() {
        // The middle of the box must land at 0.5 on both axes, not at some rounded cell.
        val nodes = listOf(
            node("a", x = 0.0, y = 0.0),
            node("mid", x = 50.0, y = 25.0),
            node("b", x = 100.0, y = 50.0),
        )

        assertEquals(0.5f, pin(nodes, "mid").xFraction)
        assertEquals(0.5f, pin(nodes, "mid").yFraction)
    }

    @Test
    fun fractionsAreClampedWhenAnExplicitBoxIsSmallerThanTheData() {
        // A stated box is respected even when a stage falls outside it: showing 1.4 or -0.2 would
        // place the pin off the frame, where it is invisible rather than merely at the edge.
        val clamped = pins(
            listOf(node("edge", x = 200.0, y = -50.0)),
            cupCourses,
            unlock(visible = true),
            bounds = MapBounds(0.0, 0.0, 100.0, 100.0),
        ).first()

        assertEquals(1f, clamped.xFraction)
        assertEquals(0f, clamped.yFraction)
    }

    @Test
    fun aSinglePlacedStageSitsAtTheCentreRatherThanDividingByZero() {
        // The box has zero width and height; the honest answer is the middle, not NaN or a crash.
        val single = pin(listOf(node("only", x = 42.0, y = 7.0)), "only")

        assertEquals(0.5f, single.xFraction)
        assertEquals(0.5f, single.yFraction)
        assertTrue(single.hasPosition)
    }

    @Test
    fun aStageAtTheOriginIsPlacedLikeAnyOther() {
        // (0,0) is a real coordinate in this model; only a null position means "unknown".
        val nodes = listOf(node("origin", x = 0.0, y = 0.0), node("far", x = 10.0, y = 10.0))
        val origin = pin(nodes, "origin")

        assertTrue(origin.hasPosition)
        assertEquals(0f, origin.xFraction)
        assertEquals(0f, origin.yFraction)
    }

    // ---------------------------------------------------------------- what still appears

    @Test
    fun aStageWithoutAPositionStillAppearsWithItsLabel() {
        // Omitting the pin would make the stage look nonexistent, not merely unplaced.
        val nodes = listOf(node("placed", x = 1.0, y = 1.0), node("unplaced", name = "Peach Beach"))

        val all = shown(nodes)
        assertEquals(2, all.size, "the positionless stage must still have a pin")
        assertEquals("Peach Beach", all.first { it.id == "unplaced" }.label)
    }

    @Test
    fun aPositionlessPinIsFlaggedAndNotPlacedAtTheOrigin() {
        val unplaced = pin(listOf(node("placed", x = 1.0, y = 1.0), node("unplaced")), "unplaced")

        assertFalse(unplaced.hasPosition, "an unknown placement must not read as known")
        // The fractions are a neutral centre, never (0,0); the screen must not draw it at all.
        assertEquals(0.5f, unplaced.xFraction)
        assertEquals(0.5f, unplaced.yFraction)
    }

    // ---------------------------------------------------------------- the gate

    @Test
    fun theCupGateRemovesPinsRatherThanGreyingThem() {
        // A greyed or locked pin is still an announcement that a hidden course exists.
        val nodes = listOf(node("Coaster", x = 1.0, y = 1.0), node("Rainbow", x = 2.0, y = 2.0))

        val locked = shown(nodes, visible = false)
        assertEquals(listOf("Coaster"), locked.map { it.id })
        assertTrue(locked.none { it.locked }, "pins are removed, never returned as locked")

        assertEquals(listOf("Coaster", "Rainbow"), shown(nodes).map { it.id })
    }

    @Test
    fun anAbsentCupStateHidesTheSamePinsAsALockedOne() {
        // No unlock state is the safe default, not a permission.
        val nodes = listOf(node("Coaster", x = 1.0, y = 1.0), node("Rainbow", x = 2.0, y = 2.0))

        assertEquals(
            shown(nodes, visible = false).map { it.id },
            pins(nodes, cupCourses, null).map { it.id },
            "a missing cup state must hide what a locked one hides",
        )
    }

    // ---------------------------------------------------------------- map state

    @Test
    fun mapStateSeparatesEmptyPartialAndFull() {
        // The distinction that matters: a partial map must say it is incomplete rather than look full.
        assertEquals(MapState.NO_BASEMAP, mapState(emptyList()))
        assertEquals(MapState.NO_BASEMAP, mapState(listOf(node("a"), node("b"))))
        assertEquals(MapState.PARTIAL, mapState(listOf(node("a", x = 1.0, y = 1.0), node("b"))))
        assertEquals(
            MapState.READY,
            mapState(listOf(node("a", x = 1.0, y = 1.0), node("b", x = 2.0, y = 3.0))),
        )
    }

    @Test
    fun eachMapStateSaysWhatItMeans() {
        // A partial map without a label is indistinguishable from a complete one.
        assertTrue(
            MapState.PARTIAL.label.contains("some stages", ignoreCase = true),
            MapState.PARTIAL.label,
        )
        assertTrue(
            MapState.NO_BASEMAP.label.contains("no stage", ignoreCase = true),
            MapState.NO_BASEMAP.label,
        )
        assertFalse(
            MapState.READY.label.contains("some stages", ignoreCase = true),
            MapState.READY.label,
        )
    }

    // ---------------------------------------------------------------- route rows

    @Test
    fun routeRowsNumberTheStopsInOrderAndAnAbsentRouteHasNone() {
        val route = Route.custom(listOf(stage("a"), stage("b"), stage("c")))

        val rows = routeRows(route, cupCourses, unlock(visible = true))
        assertEquals(listOf(0, 1, 2), rows.map { it.index })
        assertEquals(listOf("a", "b", "c"), rows.map { it.label })
        assertTrue(rows.all { it.problem == null })
        assertTrue(routeRows(null, cupCourses, unlock(visible = true)).isEmpty())
    }

    @Test
    fun aLockedStopIsFlaggedAndExplained() {
        val route = Route.custom(listOf(stage("Coaster"), stage("Rainbow")))

        val lockedRow = assertNotNull(
            routeRows(route, cupCourses, unlock(visible = false)).firstOrNull { it.locked },
        )
        assertEquals(1, lockedRow.index, "the gated stop keeps its position in the route")
        assertEquals(RouteStopRow.KIND_STAGE, lockedRow.kind)
        assertNotNull(lockedRow.problem, "a locked stop must say why it cannot be used")
    }

    @Test
    fun aLockedStopDoesNotNameTheCourseOrTheCup() {
        // Naming the cup is the same leak as showing it; the course id is what the gate hides.
        val route = Route.custom(listOf(stage("Coaster"), stage("Rainbow")))
        val lockedRow = routeRows(route, cupCourses, unlock(visible = false)).first { it.locked }

        val text = lockedRow.label + " " + lockedRow.problem
        assertFalse(text.contains("Rainbow", ignoreCase = true), text)
        assertFalse(text.contains("cup", ignoreCase = true), text)
        assertFalse(text.contains("special", ignoreCase = true), text)
        assertFalse(text.contains("unlock", ignoreCase = true), text)
    }

    @Test
    fun aRoadStopIsLabelledAsARoadNotAsAStage() {
        val connection = RoadConnection.inferred(StageId("a"), StageId("b"))
        val route = Route.custom(listOf(stage("a"), RouteStop.Road(connection), stage("b")))

        val rows = routeRows(route, cupCourses, unlock(visible = true))
        assertEquals(RouteStopRow.KIND_ROAD, rows[1].kind)
        assertTrue(rows[1].label.contains("a"), rows[1].label)
        assertTrue(rows[1].label.contains("b"), rows[1].label)
    }

    @Test
    fun aRoadTouchingALockedCourseIsFlaggedToo() {
        // A road's endpoints are stage references; checking only stage stops would leak one.
        val connection = RoadConnection.inferred(StageId("Coaster"), StageId("Rainbow"))
        val route = Route.custom(listOf(stage("Coaster"), RouteStop.Road(connection)))

        assertTrue(routeRows(route, cupCourses, unlock(visible = false)).any { it.locked })
    }

    // ---------------------------------------------------------------- saved rows

    @Test
    fun aSavedRouteCarriesItsNameStopCountAndAnId() {
        val saved = SavedRoute.create(
            version = SavedRoute.CURRENT_VERSION,
            name = "Sunday",
            route = Route.custom(listOf(stage("a"), stage("b"))),
            knownStageIds = setOf(StageId("a"), StageId("b")),
        )

        val row = savedRows(listOf(saved), cupCourses, unlock(visible = true)).single()
        assertEquals("Sunday", row.name)
        assertEquals(2, row.stopCount)
        assertNull(row.problem)
        assertTrue(row.id.isNotBlank(), "a saved row needs an id for a stable list key")
    }

    @Test
    fun aSavedRouteReferencingALockedCourseCarriesAProblemUntilUnlocked() {
        // Without the problem the row looks playable, and playback would substitute a course.
        val saved = SavedRoute.create(
            version = SavedRoute.CURRENT_VERSION,
            name = "Cup detour",
            route = Route.custom(listOf(stage("Coaster"), stage("Rainbow"))),
            knownStageIds = setOf(StageId("Coaster"), StageId("Rainbow")),
        )

        val problem = assertNotNull(
            savedRows(listOf(saved), cupCourses, unlock(visible = false)).single().problem,
        )
        assertFalse(problem.contains("Rainbow", ignoreCase = true), problem)
        assertFalse(problem.contains("cup", ignoreCase = true), problem)
        assertFalse(problem.contains("special", ignoreCase = true), problem)

        assertNull(savedRows(listOf(saved), cupCourses, unlock(visible = true)).single().problem)
    }

    // ---------------------------------------------------------------- the editor

    @Test
    fun aDraftBelowTwoStopsIsRefusedWithRoutesOwnWording() {
        // The reason must match what Route itself throws, or the editor and the model disagree.
        for (stops in listOf(emptyList<RouteStop>(), listOf(stage("a")))) {
            val editor = RouteEditor.of(stops)
            assertFalse(editor.canSave, "a draft of ${stops.size} stop(s) cannot be saved")

            val expected = assertFailsWith<IllegalArgumentException> { Route.custom(stops) }.message
            assertEquals(expected, editor.blockingReason)
        }
    }

    @Test
    fun aTwoStopDraftCanBeSaved() {
        val editor = RouteEditor.of(listOf(stage("a"), stage("b")))

        assertTrue(editor.canSave)
        assertNull(editor.blockingReason)
    }

    @Test
    fun aDuplicateConsecutiveStageIsRefusedWithRoutesOwnWording() {
        val editor = RouteEditor.of(listOf(stage("a"), stage("b")))
        val reason = assertNotNull(canAdd(editor, stage("b")))
        assertTrue(reason.contains("twice in a row"), reason)

        val stops = listOf(stage("a"), stage("b"), stage("b"))
        val expected = assertFailsWith<IllegalArgumentException> { Route.custom(stops) }.message
        assertEquals(expected, RouteEditor.of(stops).blockingReason)
    }

    @Test
    fun aRoadBetweenTwoVisitsDoesNotHideTheRepeat() {
        // `b -> road -> b` is still the same stage music twice in a row for the listener.
        val connection = RoadConnection.inferred(StageId("a"), StageId("b"))
        val editor = RouteEditor.of(listOf(stage("a"), RouteStop.Road(connection), stage("b")))

        val reason = assertNotNull(canAdd(editor, stage("b")))
        assertTrue(reason.contains("twice in a row"), reason)
    }

    @Test
    fun aDifferentStageAndTheFirstStopCanBothBeAdded() {
        // The two-stop minimum is a save rule, not an append rule; enforcing it here would make the
        // editor impossible to start.
        assertNull(canAdd(RouteEditor.of(emptyList()), stage("a")))
        assertNull(canAdd(RouteEditor.of(listOf(stage("a"), stage("b"))), stage("c")))
    }
}
