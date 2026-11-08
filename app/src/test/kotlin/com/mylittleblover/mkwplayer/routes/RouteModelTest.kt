package com.mylittleblover.mkwplayer.routes

import com.mylittleblover.mkwplayer.registry.AudioAssetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The route model keeps five facts apart: placement, road existence, road music candidates,
 * transitions, and saved routes.
 *
 * Each group below pins the failure that arises when two of those facts are conflated: a
 * missing position read as the map origin, an `InterStageEx1_*` name promoted to a verified
 * road, an arbitrary candidate presented as the game's pick, or a saved route that stops
 * resolving after a rename.
 */
class RouteModelTest {

    // ---------------------------------------------------------------- fixtures

    /** A valid 64-char lowercase hex digest, distinct per small seed. */
    private fun asset(seed: Int): AudioAssetId = AudioAssetId("%02x".format(seed).repeat(32))

    private fun stageNode(
        name: String,
        position: MapPosition? = null,
        music: List<AudioAssetId> = emptyList(),
    ): StageNode = StageNode(
        id = StageId(name),
        displayName = name.replace('-', ' '),
        position = position,
        musicAssets = music,
    )

    private fun road(
        left: String = "central",
        right: String = "rainbow",
    ): RoadConnection = RoadConnection.inferred(StageId(left), StageId(right))

    private fun stageRoute(vararg ids: String): Route =
        Route.custom(ids.map { RouteStop.Stage(StageId(it)) })

    // ---------------------------------------------------------------- layer 1: geography

    @Test
    fun aStageWithUnresearchedPlacementKeepsPositionNull() {
        // The temptation is to default to (0.0, 0.0). That is a real coordinate, so every
        // unresearched stage would be drawn on top of it and look researched.
        val node = stageNode("coaster")
        assertNull(node.position, "an unresearched stage must keep a null position")
        assertFalse(node.hasKnownPosition, "a null position must not read as known placement")
        assertTrue(node.musicAssets.isEmpty(), "music may be unknown at the same time as placement")
    }

    @Test
    fun anUnknownPositionIsNotTheMapOrigin() {
        val origin = MapPosition(0.0, 0.0)
        val placed = stageNode("coaster", position = origin)
        val unplaced = stageNode("coaster")

        assertTrue(placed.hasKnownPosition, "the map origin is a legitimate coordinate")
        assertEquals(origin, placed.position, "a stated origin must be carried through unchanged")
        assertNull(unplaced.position, "unknown placement must stay null even though the origin exists")
        assertTrue(placed != unplaced, "unknown placement must not equal placement at (0,0)")
    }

    @Test
    fun aNonFiniteMapPositionIsRefused() {
        // A NaN coordinate silently breaks any distance or layout computation downstream.
        assertFailsWith<IllegalArgumentException> { MapPosition(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { MapPosition(0.0, Double.POSITIVE_INFINITY) }
    }

    // ---------------------------------------------------------------- layer 2: connections

    @Test
    fun aRoadInferredFromAResourceNameIsMarkedInferred() {
        // The game really does ship InterStageEx1_* resources. That establishes a road
        // relationship was authored; it does not establish the stable ids used here, and it
        // says nothing about music. Promoting it to VERIFIED would launder a guess.
        val stages = mapOf(
            "FirstCircuit" to StageId("first-circuit"),
            "Coaster" to StageId("coaster"),
        )
        val connection = RoadConnection.fromInterStageName(
            "Work/Banc/Course/InterStageEx1_FirstCircuitToCoaster_A_00.bcett.json",
        ) { name -> stages[name] }

        assertNotNull(connection, "a resource name with two known stages must resolve")
        assertEquals(
            ConnectionProvenance.INFERRED,
            connection!!.provenance,
            "a naming inference must be labelled INFERRED",
        )
        assertTrue(connection.isInferred, "the naming inference must report itself as inferred")
        assertFalse(connection.isVerified, "a resource name must never yield a verified road")
        assertTrue(connection.connects(StageId("first-circuit")), "the left endpoint must be kept")
        assertTrue(connection.connects(StageId("coaster")), "the right endpoint must be kept")
    }

    @Test
    fun aStageNameBeginningWithToStillResolves() {
        // `To` is both the separator and the start of real stage names (Toad Factory). A naive
        // first-match split would read an empty left end and lose the road.
        val stages = mapOf(
            "ToadFactory" to StageId("toad-factory"),
            "ToadHarbor" to StageId("toad-harbor"),
        )
        val connection = RoadConnection.fromInterStageName(
            "InterStageEx1_ToadFactoryToToadHarbor_A_00.bcett.json",
        ) { name -> stages[name] }

        assertNotNull(connection, "a stage name starting with 'To' must still split correctly")
        assertTrue(connection!!.connects(StageId("toad-factory")))
        assertTrue(connection.connects(StageId("toad-harbor")))
    }

    @Test
    fun aVerifiedConnectionCarriesTheEvidenceThatEstablishedIt() {
        val connection = RoadConnection.verified(
            StageId("central"),
            StageId("rainbow"),
            "Course.Product.180.rstbl: InterStageEx1 record",
        )
        assertEquals(ConnectionProvenance.VERIFIED, connection.provenance, "VERIFIED must be stated")
        assertTrue(connection.isVerified, "an evidenced road must report itself as verified")
        assertEquals(
            "Course.Product.180.rstbl: InterStageEx1 record",
            connection.evidence,
            "the evidence must be preserved for the reader of the map",
        )
    }

    @Test
    fun aVerifiedConnectionWithoutEvidenceIsRefused() {
        // Without this rule, `verified` is a one-word upgrade from a naming inference.
        assertFailsWith<IllegalArgumentException> {
            RoadConnection.verified(StageId("central"), StageId("rainbow"), "   ")
        }
    }

    @Test
    fun aRoadConnectionRefusesTheSameStageOnBothEnds() {
        val same = StageId("coaster")
        assertFailsWith<IllegalArgumentException> { RoadConnection.inferred(same, same) }
    }

    @Test
    fun aRoadHasOneFormWhateverOrderItsEndsAreGiven() {
        // A road is one relationship. Two values for it would make a music lookup miss the road
        // merely because a caller happened to write the endpoints the other way round.
        val central = StageId("central")
        val rainbow = StageId("rainbow")
        assertEquals(
            RoadConnection.inferred(central, rainbow),
            RoadConnection.inferred(rainbow, central),
            "the same physical road must not have two values",
        )
        assertEquals(
            rainbow,
            RoadConnection.inferred(central, rainbow).otherEnd(central),
            "otherEnd must return the opposite endpoint",
        )
        assertNull(
            RoadConnection.inferred(central, rainbow).otherEnd(StageId("elsewhere")),
            "a stage that is not on the road has no opposite endpoint",
        )
    }

    @Test
    fun aResourceNameWithAnUnknownStageResolvesToNothing() {
        // Inventing an id from the name would create a stage the library cannot resolve, and the
        // failure would appear much later as a route that silently skips a stop.
        val connection = RoadConnection.fromInterStageName(
            "InterStageEx1_FirstCircuitToNowhere_A_00.bcett.json",
        ) { name -> if (name == "FirstCircuit") StageId("first-circuit") else null }

        assertNull(connection, "an unresolved endpoint must not be guessed")
    }

    // ---------------------------------------------------------------- layer 3: road music

    @Test
    fun aRoadWithoutVerifiedMusicDoesNotGuess() {
        val candidate = RoadMusicCandidate.unknown(road())

        assertFalse(candidate.hasCandidates, "an unresearched road has no candidates")
        assertTrue(candidate.musicAssets.isEmpty(), "no track may be borrowed from a stage or another road")
        assertEquals(
            RoadMusicCandidate.NOTE_NO_VERIFIED_MUSIC,
            candidate.selectionNote,
            "the data gap must be stated on the candidate set itself",
        )
        assertTrue(
            candidate.selectionNote.contains("not established"),
            "the note must say the choice is not established: ${candidate.selectionNote}",
        )
    }

    @Test
    fun candidatesAreNotPresentedAsTheGameChoice() {
        // Candidates answer "what may play", never "what plays". That second question has no
        // verified answer, so it must stay visible even when candidates exist.
        val candidate = RoadMusicCandidate(road(), listOf(asset(1), asset(2)))

        assertTrue(candidate.hasCandidates, "listed tracks must be visible as candidates")
        assertEquals(
            RoadMusicCandidate.NOTE_PICK_NOT_ESTABLISHED,
            candidate.selectionNote,
            "a non-empty candidate list must still state that the pick is unknown",
        )
        assertTrue(
            candidate.selectionNote.contains("which track", ignoreCase = true),
            "the note must name what is unknown: ${candidate.selectionNote}",
        )
    }

    @Test
    fun aRoadRefusesDuplicateCandidateTracks() {
        val one = asset(1)
        assertFailsWith<IllegalArgumentException> {
            RoadMusicCandidate(road(), listOf(one, one))
        }
    }

    // ---------------------------------------------------------------- layer 4: transitions

    @Test
    fun aTransitionRefusesANonPositiveDuration() {
        // A zero-length fade is a Cut written by accident; the audible result is a hard jump
        // where the caller believed it had configured a ramp.
        assertFailsWith<IllegalArgumentException> { MusicTransition.Crossfade(0) }
        assertFailsWith<IllegalArgumentException> { MusicTransition.FadeThrough(0, 200) }
        assertFailsWith<IllegalArgumentException> { MusicTransition.FadeThrough(200, 0) }
    }

    @Test
    fun transitionDurationsAddUp() {
        assertEquals(0, MusicTransition.Cut.totalDurationMs, "a cut takes no time")
        assertEquals(400, MusicTransition.Crossfade(400).totalDurationMs, "a crossfade lasts its duration")
        assertEquals(
            400,
            MusicTransition.FadeThrough(150, 250).totalDurationMs,
            "a fade-through lasts the sum of its two ramps",
        )
    }

    // ---------------------------------------------------------------- layer 5: routes

    @Test
    fun aRouteNeedsAtLeastTwoStops() {
        // One stop has no handover, so every layer below would be untested by the route.
        assertFailsWith<IllegalArgumentException> { Route.custom(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            Route.custom(listOf(RouteStop.Stage(StageId("coaster"))))
        }
        assertFailsWith<IllegalArgumentException> { Route.grandPrix(listOf(StageId("coaster"))) }
    }

    @Test
    fun aRouteRefusesTwoConsecutiveVisitsToTheSameStage() {
        val coaster = StageId("coaster")
        assertFailsWith<IllegalArgumentException> {
            Route.custom(listOf(RouteStop.Stage(coaster), RouteStop.Stage(coaster)))
        }
    }

    @Test
    fun aRoadStopDoesNotHideARepeatedStage() {
        // The raw stop list has a road between the two visits, so a naive check on adjacent
        // stops would pass while the listener hears the same stage music twice in a row.
        val central = StageId("central")
        val rainbow = StageId("rainbow")
        assertFailsWith<IllegalArgumentException> {
            Route.custom(
                listOf(
                    RouteStop.Stage(central),
                    RouteStop.Road(RoadConnection.inferred(central, rainbow)),
                    RouteStop.Stage(central),
                ),
            )
        }
    }

    @Test
    fun aRoadStopIsPartOfTheRouteButNotAStage() {
        val central = StageId("central")
        val rainbow = StageId("rainbow")
        val connection = RoadConnection.inferred(central, rainbow)
        val route = Route.custom(
            listOf(
                RouteStop.Stage(central),
                RouteStop.Road(connection),
                RouteStop.Stage(rainbow),
            ),
        )

        assertEquals(listOf(central, rainbow), route.stageIds, "roads must not appear in the stage list")
        assertEquals(listOf(connection), route.roads, "the road leg must be kept in order")
        assertEquals(3, route.stops.size, "all three stops must remain part of the route")
        assertTrue(route.references(central), "a road endpoint counts as referenced")
        assertTrue(route.references(rainbow), "a road endpoint counts as referenced")
        assertFalse(route.references(StageId("elsewhere")), "an absent stage must not be referenced")
    }

    @Test
    fun grandPrixBuildsFromItsStageListAndIsStatic() {
        val stages = listOf(StageId("a"), StageId("b"), StageId("c"))
        val route = Route.grandPrix(stages)

        assertEquals(RouteKind.GRAND_PRIX, route.kind, "the builder must mark the static kind")
        assertTrue(route.isStatic, "a grand prix is a game-defined format")
        assertEquals(stages, route.stageIds, "the builder must keep the given order")
        assertTrue(route.roads.isEmpty(), "the static builder must not invent road legs")
    }

    @Test
    fun knockoutBuildsFromItsStageListAndIsStatic() {
        val stages = listOf(StageId("x"), StageId("y"))
        val route = Route.knockout(stages)

        assertEquals(RouteKind.KNOCKOUT, route.kind, "the builder must mark the static kind")
        assertTrue(route.isStatic, "a knockout is a game-defined format")
        assertEquals(stages, route.stageIds, "the builder must keep the given order")
        assertFalse(
            stageRoute("a", "b").isStatic,
            "a user route must not claim to be a static format",
        )
    }

    // ---------------------------------------------------------------- music selection

    @Test
    fun fixedOrderWalksPastThePreviousTrackAndWraps() {
        val a = asset(1)
        val b = asset(2)
        val c = asset(3)
        val selection = RouteSelection { 0 }
        val candidates = listOf(a, b, c)

        assertEquals(
            c,
            selection.next(candidates, previous = b, mode = RouteSelection.Mode.FIXED_ORDER),
            "fixed order must continue after the previous track",
        )
        assertEquals(
            a,
            selection.next(candidates, previous = c, mode = RouteSelection.Mode.FIXED_ORDER),
            "the walk must wrap at the end",
        )
        assertEquals(
            a,
            selection.next(candidates, previous = asset(9), mode = RouteSelection.Mode.FIXED_ORDER),
            "an unknown previous track must start the walk at the head",
        )
    }

    @Test
    fun randomSelectionIsDeterministicBecauseTheIndexSourceIsInjected() {
        // A class that calls kotlin.random.Random internally cannot be pinned by a test. The
        // recorded bounds also prove the previous track was removed before the draw.
        val bounds = mutableListOf<Int>()
        val selection = RouteSelection { bound ->
            bounds += bound
            bound - 1
        }
        val a = asset(1)
        val b = asset(2)
        val c = asset(3)

        val picked = selection.next(listOf(a, b, c), previous = a, mode = RouteSelection.Mode.RANDOM)

        assertEquals(c, picked, "the injected source's last index must select the last alternative")
        assertEquals(listOf(2), bounds, "the draw must range over the alternatives, excluding the previous")
    }

    @Test
    fun randomSelectionNeverRepeatsThePreviousTrackWhenAnAlternativeExists() {
        val a = asset(1)
        val b = asset(2)
        val c = asset(3)
        val candidates = listOf(a, b, c)

        val first = RouteSelection { 0 }
            .next(candidates, previous = b, mode = RouteSelection.Mode.RANDOM)
        assertEquals(a, first, "index 0 of the alternatives [a, c] is a")
        assertTrue(first != b, "the draw must not land on the previous track")

        val last = RouteSelection { bound -> bound - 1 }
            .next(candidates, previous = b, mode = RouteSelection.Mode.RANDOM)
        assertEquals(c, last, "the last alternative must remain selectable")
        assertTrue(last != b, "the draw must not land on the previous track")
    }

    @Test
    fun avoidRepeatFallsBackWhenEveryCandidateWasPrevious() {
        // Documented deliberate fallback: refusing would produce silence, and retrying would
        // loop forever. Repeating the single available track is the honest answer.
        val only = asset(1)
        val selection = RouteSelection { error("the random source must not be consulted") }

        assertEquals(
            only,
            selection.next(listOf(only), previous = only, mode = RouteSelection.Mode.FIXED_ORDER),
            "fixed order must return the sole candidate rather than looping",
        )
        assertEquals(
            only,
            selection.next(listOf(only), previous = only, mode = RouteSelection.Mode.RANDOM),
            "random must return the sole candidate rather than looping",
        )
    }

    @Test
    fun anEmptyCandidateListYieldsNoTrack() {
        // An empty list means the road's music is unknown. Repeating the previous track would
        // present that guess as a candidate the road does not have.
        val selection = RouteSelection { 0 }
        assertNull(
            selection.next(emptyList(), previous = asset(1), mode = RouteSelection.Mode.FIXED_ORDER),
            "no candidates means no next track",
        )
        assertNull(
            selection.next(emptyList(), previous = null, mode = RouteSelection.Mode.RANDOM),
            "no candidates means no next track even with no previous track",
        )
    }

    @Test
    fun aRandomIndexOutsideTheCandidateRangeIsRefused() {
        // Clamping would bias the draw toward the ends of the list without saying so.
        val selection = RouteSelection { 99 }
        assertFailsWith<IllegalArgumentException> {
            selection.next(
                listOf(asset(1), asset(2)),
                previous = asset(1),
                mode = RouteSelection.Mode.RANDOM,
            )
        }
    }

    // ---------------------------------------------------------------- saved routes

    @Test
    fun aSavedRouteReferencesStagesByStableIdNotByPath() {
        val known = setOf(StageId("coaster"), StageId("peach-beach"))
        val saved = SavedRoute.create(
            version = SavedRoute.CURRENT_VERSION,
            name = "Sunday cup",
            route = stageRoute("coaster", "peach-beach"),
            knownStageIds = known,
        )

        assertEquals(SavedRoute.CURRENT_VERSION, saved.version, "the saved form must carry its schema version")
        assertEquals(
            listOf(StageId("coaster"), StageId("peach-beach")),
            saved.stageIds,
            "a saved route must reference stable ids only",
        )
        assertFailsWith<IllegalArgumentException> {
            StageId("C:/library/BGM_Stage_Coaster.bwav")
        }
        assertFailsWith<IllegalArgumentException> {
            SavedRoute.create(
                version = SavedRoute.CURRENT_VERSION + 1,
                name = "future",
                route = stageRoute("coaster", "peach-beach"),
                knownStageIds = known,
            )
        }
    }

    @Test
    fun aSavedRouteReferencingAnUnknownStageIsRefused() {
        // Silently dropping the unknown stop would play a different route than the one the user
        // saved, with no visible reason.
        assertFailsWith<IllegalArgumentException> {
            SavedRoute.create(
                version = SavedRoute.CURRENT_VERSION,
                name = "dangling",
                route = stageRoute("coaster", "removed-stage"),
                knownStageIds = setOf(StageId("coaster")),
            )
        }
    }

    @Test
    fun anUnknownRoadEndpointAlsoBlocksLoading() {
        // Checking only stage stops would let a saved road point at a stage that no longer
        // exists, and the road music layer would silently lose its endpoints.
        val central = StageId("central")
        val rainbow = StageId("rainbow")
        val ghost = StageId("ghost")
        val route = Route.custom(
            listOf(
                RouteStop.Stage(central),
                RouteStop.Road(RoadConnection.inferred(central, ghost)),
                RouteStop.Stage(rainbow),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            SavedRoute.create(
                version = SavedRoute.CURRENT_VERSION,
                name = "ghost road",
                route = route,
                knownStageIds = setOf(central, rainbow),
            )
        }
    }
}
