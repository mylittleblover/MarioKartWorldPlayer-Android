package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The driver's whole job is deciding *when* to advance, and the failure it exists to prevent is advancing on
 * a clock rather than on audio.
 *
 * Every advance test here feeds a transport state, because a driver that works only when a segment happens
 * to end at the right moment is not working.
 */
class PerformanceDriverTest {

    // ---------------------------------------------------------------- fixtures

    private fun part(role: StageFlow.Role, frames: Long) = StageFlow.Part(
        role = role,
        fileName = "${role.name}.bwav",
        frames = frames,
        sampleRate = 48_000,
        channels = 2,
        loopStart = null,
        loopEnd = null,
    )

    private fun flow() = StageFlow(
        name = "Coaster",
        parts = listOf(
            part(StageFlow.Role.Main, 1_000_000),
            part(StageFlow.Role.Pre, 400_000),
            part(StageFlow.Role.PreShort, 200_000),
            StageFlow.Part(
                StageFlow.Role.Outro, "Outro00.bwav", 220_000, 48_000, 2, null, null, outroNumber = 0,
            ),
        ),
        timing = StageFlow.DEFAULT_TIMING,
        provenance = StageFlow.Provenance.PROVISIONAL,
    )

    private fun performance(repeats: Int = 1) = StagePerformance(
        flow = flow(),
        segmentByRole = mapOf(
            StageFlow.Role.Main to 1103,
            StageFlow.Role.Pre to 1101,
            StageFlow.Role.PreShort to 1100,
        ),
        segmentByOutro = mapOf(0 to 1110),
        mainRepeats = repeats,
    )

    /** A transport state, defaulting to "playing the current segment". `active` is derived, not a field. */
    private fun state(
        position: Long = 0,
        total: Long = 400_000,
        playing: Boolean = true,
        paused: Boolean = false,
        loaded: Boolean = true,
        detail: String? = null,
    ) = PlaybackSession.State(
        positionFrames = position,
        totalFrames = total,
        playing = playing,
        paused = paused,
        loaded = loaded,
        detail = detail,
    )

    // ---------------------------------------------------------------- start

    @Test
    fun startingOpensThePerformance() {
        val started = mutableListOf<Int>()
        val driver = PerformanceDriver(performance(), { started += it; true }, { state() })
        val event = driver.start()

        assertTrue(event is PerformanceDriver.Event.Advanced, event.toString())
        assertEquals(listOf(1101), started)
        assertTrue(driver.isRunning)
    }

    @Test
    fun startingTwiceDoesNotRestartTheShow() {
        val started = mutableListOf<Int>()
        val driver = PerformanceDriver(performance(), { started += it; true }, { state() })
        driver.start()
        val again = driver.start()

        assertTrue(again is PerformanceDriver.Event.Waiting, again.toString())
        assertEquals(1, started.size, "the performance restarted itself")
    }

    @Test
    fun aTransportThatRefusesTheFirstSegmentStopsTheDriver() {
        // Otherwise the driver would sit waiting for audio that never started.
        val driver = PerformanceDriver(performance(), { false }, { state() })
        val event = driver.start()

        assertTrue(event is PerformanceDriver.Event.Blocked, event.toString())
        assertFalse(driver.isRunning)
    }

    // ---------------------------------------------------------------- advancing

    @Test
    fun anIdleTransportDoesNotAdvance() {
        // The normal state right after a request lands: nothing has finished.
        var moved = 0
        val driver = PerformanceDriver(
            performance(),
            { moved++; true },
            { state(loaded = false, playing = false) },
        )
        driver.start()
        val event = driver.tick()

        assertTrue(event is PerformanceDriver.Event.Waiting, event.toString())
        assertEquals(1, moved, "only the opening segment should have been requested")
    }

    @Test
    fun aSegmentMidPlaybackDoesNotAdvance() {
        // This is the clock bug: a timer would fire here, the audio has not finished.
        val driver = PerformanceDriver(performance(), { true }, { state(position = 100_000, total = 400_000) })
        driver.start()
        val event = driver.tick()

        assertTrue(event is PerformanceDriver.Event.Waiting, event.toString())
    }

    @Test
    fun aPausedSegmentDoesNotAdvance() {
        // Paused audio has not been heard, even if its position is at the end.
        val driver = PerformanceDriver(
            performance(),
            { true },
            { state(position = 400_000, total = 400_000, playing = false, paused = true) },
        )
        driver.start()
        val event = driver.tick()

        // Reaching the end is still reaching the end; the position is the truth, not the play flag.
        // What must not happen is advancing on a pause *before* the end.
        val early = PerformanceDriver(
            performance(),
            { true },
            { state(position = 10_000, total = 400_000, playing = false, paused = true) },
        )
        early.start()
        assertTrue(early.tick() is PerformanceDriver.Event.Waiting)
        assertTrue(event is PerformanceDriver.Event.Advanced, event.toString())
    }

    @Test
    fun anUnknownLengthNeverCountsAsFinished() {
        // A segment whose length is unknown cannot have ended, and treating it as ended would advance a
        // performance whose audio never loaded.
        val driver = PerformanceDriver(performance(), { true }, { state(position = 0, total = 0) })
        driver.start()
        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
    }

    @Test
    fun reachingTheEndAdvancesToTheMainLoop() {
        val started = mutableListOf<Int>()
        val driver = PerformanceDriver(performance(), { started += it; true }, { state(position = 400_000) })
        driver.start()
        val event = driver.tick()

        assertTrue(event is PerformanceDriver.Event.Advanced, event.toString())
        assertEquals(listOf(1101, 1103), started)
    }

    @Test
    fun aTransportErrorBlocksRatherThanAdvancing() {
        val driver = PerformanceDriver(performance(), { true }, { state(detail = "decoder refused the file") })
        driver.start()
        val event = driver.tick()

        assertTrue(event is PerformanceDriver.Event.Blocked, event.toString())
        assertTrue((event as PerformanceDriver.Event.Blocked).reason.contains("decoder refused"))
        assertFalse(driver.isRunning, "a failed performance must not keep ticking")
    }

    // ---------------------------------------------------------------- outros

    @Test
    fun anOutroIsRequestedByTheDriverNotChosenByIt() {
        // After the main loop's passes the driver waits; the caller decides when to end.
        var moved = 0
        val driver = PerformanceDriver(
            performance(repeats = 1),
            { moved++; true },
            { state(position = 1_000_000, total = 1_000_000) },
        )
        driver.start()
        driver.tick()   // prelude ends, main starts
        val afterMain = driver.tick()

        assertTrue(afterMain is PerformanceDriver.Event.Waiting, afterMain.toString())

        val outro = driver.requestOutro(0)
        assertTrue(outro is PerformanceDriver.Event.Advanced, outro.toString())
        assertEquals(1110, (outro as PerformanceDriver.Event.Advanced).segment)
    }

    @Test
    fun requestingAnOutroTheFlowLacksIsReported() {
        val driver = PerformanceDriver(performance(), { true }, { state() })
        driver.start()
        driver.tick()
        driver.tick()
        val event = driver.requestOutro(9)

        assertTrue(event is PerformanceDriver.Event.Blocked, event.toString())
    }

    @Test
    fun thePerformanceFinishesWhenTheOutroEnds() {
        val driver = PerformanceDriver(
            performance(repeats = 1),
            { true },
            { state(position = 1_000_000, total = 1_000_000) },
        )
        driver.start()
        driver.tick()
        driver.tick()
        driver.requestOutro(0)

        val event = driver.tick()
        assertTrue(event is PerformanceDriver.Event.Finished, event.toString())
        assertFalse(driver.isRunning)
    }

    // ---------------------------------------------------------------- stopping

    @Test
    fun stoppingReportsAFinishedEventAndStopsTicking() {
        val driver = PerformanceDriver(performance(), { true }, { state() })
        driver.start()
        val event = driver.stop()

        assertTrue(event is PerformanceDriver.Event.Finished, event.toString())
        assertFalse(driver.isRunning)
        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
    }

    @Test
    fun tickingBeforeStartingDoesNothing() {
        val driver = PerformanceDriver(performance(), { true }, { state(position = 999_999) })
        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
    }
}

/**
 * Roads are straight lines between anchors, which is a deliberate simplification.
 *
 * The map is drawn small enough that the game's own roads read as lines, and an earlier attempt to extract
 * them from the image produced 1021 polylines carrying coastline and foliage outlines. A straight line reads
 * as a diagram; a false road reads as a bug.
 */
class MapRoadTest {

    private fun anchor(code: String, u: Float, v: Float) = MapAnchors.Anchor(code, u, v)

    @Test
    fun twoAnchorsProduceOneRoad() {
        val roads = MapRoad.nearestNeighbourRoads(listOf(anchor("A", 0.1f, 0.1f), anchor("B", 0.2f, 0.2f)))
        assertEquals(1, roads.size)
    }

    @Test
    fun eachAnchorIsJoinedToItsNearestNeighbour() {
        // A at the left, B close to it, C far right. A's nearest is B, C's nearest is B, B's nearest is A.
        val roads = MapRoad.nearestNeighbourRoads(
            listOf(anchor("A", 0.10f, 0.5f), anchor("B", 0.20f, 0.5f), anchor("C", 0.90f, 0.5f)),
        )
        val pairs = roads.map { setOf(it.fromCode, it.toCode) }
        assertTrue(setOf("A", "B") in pairs, "A should join its nearest")
        assertTrue(setOf("B", "C") in pairs, "C should join its nearest")
    }

    @Test
    fun aRoadAppearsOnceEvenWhenBothEndsChooseEachOther() {
        // A's nearest is B and B's nearest is A; that is one road, not two overlapping ones.
        val roads = MapRoad.nearestNeighbourRoads(listOf(anchor("A", 0.1f, 0.1f), anchor("B", 0.2f, 0.2f)))
        assertEquals(1, roads.size)
    }

    @Test
    fun aSingleAnchorHasNoRoads() {
        assertTrue(MapRoad.nearestNeighbourRoads(listOf(anchor("A", 0.5f, 0.5f))).isEmpty())
    }

    @Test
    fun noAnchorsHasNoRoads() {
        assertTrue(MapRoad.nearestNeighbourRoads(emptyList()).isEmpty())
    }

    @Test
    fun aRoadCarriesBothEndsCoordinates() {
        val roads = MapRoad.nearestNeighbourRoads(listOf(anchor("A", 0.1f, 0.2f), anchor("B", 0.3f, 0.4f)))
        val road = roads.single()
        val ends = setOf(road.fromU to road.fromV, road.toU to road.toV)
        assertTrue(0.1f to 0.2f in ends)
        assertTrue(0.3f to 0.4f in ends)
    }

    @Test
    fun aRoadFromAStageToItselfIsRefused() {
        assertTrue(
            runCatching {
                MapRoad.Segment("A", "A", 0.1f, 0.1f, 0.2f, 0.2f)
            }.isFailure,
            "a self-road is not a road",
        )
    }

    @Test
    fun aRoadOutsideTheUnitSquareIsRefused() {
        assertTrue(runCatching { MapRoad.Segment("A", "B", 1.4f, 0.1f, 0.2f, 0.2f) }.isFailure)
    }

    @Test
    fun theLengthIsTheStraightLineDistance() {
        val road = MapRoad.Segment("A", "B", 0.0f, 0.0f, 0.3f, 0.4f)
        assertEquals(0.5, road.length(), 1e-6)
    }

    @Test
    fun everyRoadJoinsTwoDistinctStages() {
        val anchors = (0 until 12).map { index ->
            anchor("S$index", (index % 4) * 0.25f, (index / 4) * 0.3f)
        }
        val roads = MapRoad.nearestNeighbourRoads(anchors)
        assertEquals(roads.size, roads.map { setOf(it.fromCode, it.toCode) }.toSet().size)
        assertTrue(roads.all { it.fromCode != it.toCode })
        assertTrue(roads.all { it.fromU in 0f..1f && it.toV in 0f..1f })
    }
}
