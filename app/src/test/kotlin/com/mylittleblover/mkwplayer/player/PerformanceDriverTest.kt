package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A transport that behaves like the real one: asking it to play a segment resets its position and its
 * length, a request goes through a queue so the reported state lags, and the engine ends a section by going
 * inactive.
 *
 * The earlier version of this fake returned a fixed state from a lambda, which modelled a transport that
 * ignores commands. That made a real bug look like a passing test.
 */
private class FakeTransport {

    var position: Long = 0
    var total: Long = 400_000
    var playing: Boolean = true
    var paused: Boolean = false
    var loaded: Boolean = true
    var detail: String? = null

    /** Segments the driver asked for, in order. */
    val requested = mutableListOf<Int>()

    /** When false, the transport refuses every request. */
    var accepts: Boolean = true

    /** When true, a request is recorded but the reported state does not change until [commit]. */
    var lag: Boolean = false

    private var pending: Int? = null

    /** The length each segment reports once it starts. */
    var lengths: Map<Int, Long> = emptyMap()

    fun state(): PlaybackSession.State = PlaybackSession.State(
        positionFrames = position,
        totalFrames = total,
        playing = playing,
        paused = paused,
        loaded = loaded,
        detail = detail,
    )

    fun start(segment: Int): Boolean {
        if (!accepts) return false
        requested += segment
        if (lag) {
            pending = segment
            return true
        }
        apply(segment)
        return true
    }

    /** Lets a lagged request take effect, as the engine does on its own thread. */
    fun commit() {
        pending?.let { apply(it) }
        pending = null
    }

    private fun apply(segment: Int) {
        position = 0
        total = lengths[segment] ?: total
        playing = true
        paused = false
        loaded = true
    }

    fun advance(frames: Long) {
        position = (position + frames).coerceAtMost(total)
    }
}

/**
 * The driver turns commands into segment requests and reports where the performance has got to.
 *
 * There is deliberately no "tick advances the show" test, because there is no such behaviour: the engine ends
 * a segment by going inactive and never follows a transition by itself, so a driver that advanced on its own
 * would be inventing a schedule. [PerformanceDriver.tick] exists only to notice the ending's natural end and
 * to report transport failures.
 */
class PerformanceDriverTest {

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

    private fun performance() = StagePerformance(
        flow = flow(),
        segmentByRole = mapOf(
            StageFlow.Role.Main to 1103,
            StageFlow.Role.Pre to 1101,
            StageFlow.Role.PreShort to 1100,
        ),
        segmentByOutro = mapOf(0 to 1110),
    )

    private fun transport() = FakeTransport().apply {
        lengths = mapOf(1101 to 400_000, 1103 to 1_000_000, 1110 to 220_000)
    }

    private fun driver(transport: FakeTransport = transport()): Pair<PerformanceDriver, FakeTransport> {
        val created = PerformanceDriver(
            performance(),
            { segment -> transport.start(segment) },
            { transport.state() },
        )
        return created to transport
    }

    // ---------------------------------------------------------------- start

    @Test
    fun startingOpensThePerformance() {
        val (driver, transport) = driver()
        val event = driver.start()

        assertTrue(event is PerformanceDriver.Event.Advanced, event.toString())
        assertEquals(listOf(1101), transport.requested)
        assertTrue(driver.isRunning)
    }

    @Test
    fun startingTwiceDoesNotRestartTheShow() {
        val (driver, transport) = driver()
        driver.start()
        assertTrue(driver.start() is PerformanceDriver.Event.Waiting)
        assertEquals(1, transport.requested.size)
    }

    @Test
    fun aTransportThatRefusesTheFirstSegmentStopsTheDriver() {
        val (driver, transport) = driver()
        transport.accepts = false
        assertTrue(driver.start() is PerformanceDriver.Event.Blocked)
        assertFalse(driver.isRunning)
    }

    // ---------------------------------------------------------------- the advance event

    @Test
    fun theAdvanceEventRequestsTheMainLoop() {
        val (driver, transport) = driver()
        driver.start()
        val event = driver.advance()

        assertTrue(event is PerformanceDriver.Event.Advanced, event.toString())
        assertEquals(listOf(1101, 1103), transport.requested)
    }

    @Test
    fun thePreludeIsNotAdvancedByTimePassing() {
        // The whole reason the earlier version stalled: a driver that waited for the prelude to end waited
        // for something that never happens, because the prelude loops.
        val (driver, transport) = driver()
        driver.start()
        transport.advance(390_000)

        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
        assertEquals(listOf(1101), transport.requested, "the prelude must not advance on its own")
    }

    // ---------------------------------------------------------------- the ending

    @Test
    fun theEndingIsChosenByTheCurrentPosition() {
        // The transport's own position decides which ending; the driver does not pick one arbitrarily.
        val (driver, transport) = driver()
        driver.start()
        driver.advance()
        transport.position = 200_000

        val event = driver.takeOutro()
        assertTrue(event is PerformanceDriver.Event.Advanced, event.toString())
        assertEquals(1110, (event as PerformanceDriver.Event.Advanced).segment)
    }

    @Test
    fun takingAnEndingOutsideTheMainLoopIsReported() {
        val (driver, transport) = driver()
        driver.start()
        assertTrue(driver.takeOutro() is PerformanceDriver.Event.Blocked)
        assertEquals(1, transport.requested.size)
    }

    @Test
    fun thePerformanceFinishesWhenTheEndingEnds() {
        val (driver, transport) = driver()
        driver.start()
        driver.advance()
        driver.takeOutro()

        // The engine ends a section by going inactive.
        transport.playing = false
        transport.loaded = true
        transport.position = transport.total

        val event = driver.tick()
        assertTrue(event is PerformanceDriver.Event.Finished, event.toString())
        assertFalse(driver.isRunning)
    }

    // ---------------------------------------------------------------- failures

    @Test
    fun aTransportErrorBlocksRatherThanAdvancing() {
        val (driver, transport) = driver()
        driver.start()
        transport.detail = "decoder refused the file"

        val event = driver.tick()
        assertTrue(event is PerformanceDriver.Event.Blocked, event.toString())
        assertTrue((event as PerformanceDriver.Event.Blocked).reason.contains("decoder refused"))
        assertFalse(driver.isRunning)
    }

    // ---------------------------------------------------------------- stopping

    @Test
    fun stoppingReportsAFinishedEventAndStopsTicking() {
        val (driver, transport) = driver()
        driver.start()
        val event = driver.stop()

        assertTrue(event is PerformanceDriver.Event.Finished, event.toString())
        assertFalse(driver.isRunning)
        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
        assertEquals(1, transport.requested.size)
    }

    @Test
    fun tickingBeforeStartingDoesNothing() {
        val (driver, transport) = driver()
        transport.position = 999_999
        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
        assertTrue(transport.requested.isEmpty())
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
        val roads = MapRoad.nearestNeighbourRoads(
            listOf(anchor("A", 0.10f, 0.5f), anchor("B", 0.20f, 0.5f), anchor("C", 0.90f, 0.5f)),
        )
        val pairs = roads.map { setOf(it.fromCode, it.toCode) }
        assertTrue(setOf("A", "B") in pairs, "A should join its nearest")
        assertTrue(setOf("B", "C") in pairs, "C should join its nearest")
    }

    @Test
    fun aRoadAppearsOnceEvenWhenBothEndsChooseEachOther() {
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
            runCatching { MapRoad.Segment("A", "A", 0.1f, 0.1f, 0.2f, 0.2f) }.isFailure,
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
