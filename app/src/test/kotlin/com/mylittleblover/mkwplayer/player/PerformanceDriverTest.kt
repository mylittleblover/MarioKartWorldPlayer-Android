package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A transport that behaves like the real one: asking it to play a segment resets its position and its
 * length, and until a request is processed it keeps reporting the segment it was already on.
 *
 * The earlier version of this file returned a fixed state from a lambda, which modelled a transport that
 * ignores commands. That made a real bug look like a passing test -- the driver advanced twice for one
 * segment end and nothing complained, because the fake never moved.
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

    /** When false, the transport refuses every request, as a real one does when it is unrecoverable. */
    var accepts: Boolean = true

    /**
     * When true, a request is recorded but the reported state does not change until [commit].
     *
     * This is what the real engine does: `playSegment` goes through a command queue, so a poll taken
     * immediately afterwards still describes the segment that was playing before. A fake that updates
     * instantly cannot reproduce the failure, and that is how the double advance went unnoticed.
     */
    var lag: Boolean = false

    private var pending: Int? = null

    /** The length each segment reports once it starts. Keyed by segment; unknown segments get [total]. */
    var lengths: Map<Int, Long> = emptyMap()

    fun state(): PlaybackSession.State = PlaybackSession.State(
        positionFrames = position,
        totalFrames = total,
        playing = playing,
        paused = paused,
        loaded = loaded,
        detail = detail,
    )

    /** The callback the driver calls to start a segment. */
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

    /** Plays the current segment to its end, as the engine would. */
    fun playToEnd() {
        position = total
    }

    /** Advances the playhead by [frames], clamped at the end. */
    fun advance(frames: Long) {
        position = (position + frames).coerceAtMost(total)
    }
}

/**
 * The driver's whole job is deciding *when* to advance, and the two failures it exists to prevent are
 * advancing on a clock rather than on audio, and advancing twice for one segment end.
 *
 * Every advance test drives a [FakeTransport], because a driver that works only when a segment happens to
 * end at the right moment is not working.
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

    private fun transport() = FakeTransport().apply {
        lengths = mapOf(1101 to 400_000, 1103 to 1_000_000, 1110 to 220_000)
    }

    private fun driver(
        repeats: Int = 1,
        transport: FakeTransport = transport(),
    ): Pair<PerformanceDriver, FakeTransport> {
        val created = PerformanceDriver(
            performance(repeats),
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
        val again = driver.start()

        assertTrue(again is PerformanceDriver.Event.Waiting, again.toString())
        assertEquals(1, transport.requested.size, "the performance restarted itself")
    }

    @Test
    fun aTransportThatRefusesTheFirstSegmentStopsTheDriver() {
        // Otherwise the driver would sit waiting for audio that never started.
        val (driver, transport) = driver()
        transport.accepts = false
        val event = driver.start()

        assertTrue(event is PerformanceDriver.Event.Blocked, event.toString())
        assertFalse(driver.isRunning)
    }

    // ---------------------------------------------------------------- advancing

    @Test
    fun anIdleTransportDoesNotAdvance() {
        val (driver, transport) = driver()
        driver.start()
        transport.loaded = false
        transport.playing = false

        val event = driver.tick()
        assertTrue(event is PerformanceDriver.Event.Waiting, event.toString())
        assertEquals(1, transport.requested.size, "only the opening segment should have been requested")
    }

    @Test
    fun aSegmentMidPlaybackDoesNotAdvance() {
        // This is the clock bug: a timer would fire here, the audio has not finished.
        val (driver, transport) = driver()
        driver.start()
        transport.advance(100_000)

        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
    }

    @Test
    fun aPausedSegmentBeforeItsEndDoesNotAdvance() {
        // Paused audio has not been heard. Only reaching the end counts, and a pause before the end does
        // not reach it.
        val (driver, transport) = driver()
        driver.start()
        transport.advance(10_000)
        transport.playing = false
        transport.paused = true

        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
    }

    @Test
    fun anUnknownLengthNeverCountsAsFinished() {
        // A segment whose length is unknown cannot have ended, and treating it as ended would advance a
        // performance whose audio never loaded.
        val (driver, transport) = driver()
        driver.start()
        transport.total = 0
        transport.position = 0

        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
    }

    @Test
    fun reachingTheEndAdvancesToTheMainLoop() {
        val (driver, transport) = driver()
        driver.start()
        transport.playToEnd()

        val event = driver.tick()
        assertTrue(event is PerformanceDriver.Event.Advanced, event.toString())
        assertEquals(listOf(1101, 1103), transport.requested)
    }

    @Test
    fun aStaleStateAfterARequestDoesNotAdvanceTwice() {
        // The real failure: playSegment goes through a command queue, so a poll taken right after the
        // request still describes the segment that just ended. Without a guard the driver advances again
        // and skips a section -- between two similar musical sections, where it is hardest to hear.
        val (driver, transport) = driver()
        transport.lag = true
        driver.start()
        transport.commit()

        transport.playToEnd()
        assertTrue(driver.tick() is PerformanceDriver.Event.Advanced, "the prelude should have ended")
        assertEquals(listOf(1101, 1103), transport.requested)

        // The request is still queued: the transport keeps reporting the prelude at its end.
        val second = driver.tick()
        assertTrue(second is PerformanceDriver.Event.Waiting, second.toString())
        assertEquals(2, transport.requested.size, "the driver advanced twice for one segment end")

        // Once the engine picks it up, the driver follows the new segment.
        transport.commit()
        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
        assertEquals(2, transport.requested.size)
    }

    @Test
    fun theGuardClearsWhenTheTransportMoves() {
        // A guard that never cleared would stall the performance after one section.
        val (driver, transport) = driver(repeats = 1)
        transport.lag = true
        driver.start()
        transport.commit()

        transport.playToEnd()
        driver.tick()                        // prelude -> main, queued
        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)

        transport.commit()                   // the main loop starts
        transport.advance(10_000)
        assertTrue(driver.tick() is PerformanceDriver.Event.Waiting)
        assertEquals(listOf(1101, 1103), transport.requested)
    }

    @Test
    fun aTransportErrorBlocksRatherThanAdvancing() {
        val (driver, transport) = driver()
        driver.start()
        transport.detail = "decoder refused the file"

        val event = driver.tick()
        assertTrue(event is PerformanceDriver.Event.Blocked, event.toString())
        assertTrue((event as PerformanceDriver.Event.Blocked).reason.contains("decoder refused"))
        assertFalse(driver.isRunning, "a failed performance must not keep ticking")
    }

    // ---------------------------------------------------------------- outros

    @Test
    fun theMainLoopWaitsRatherThanChoosingAnOutro() {
        val (driver, transport) = driver(repeats = 1)
        driver.start()
        transport.playToEnd()
        driver.tick()                       // prelude -> main
        transport.commit()                  // the engine picks it up
        transport.advance(10_000)
        driver.tick()                       // clear the stale guard
        transport.playToEnd()               // one pass done

        val event = driver.tick()
        assertTrue(event is PerformanceDriver.Event.Waiting, event.toString())
        assertEquals(listOf(1101, 1103), transport.requested, "the driver chose an outro by itself")
    }

    @Test
    fun anOutroPlaysWhenTheCallerAsksForIt() {
        val (driver, transport) = driver(repeats = 1)
        driver.start()
        transport.playToEnd()
        driver.tick()
        transport.commit()
        transport.advance(10_000)
        driver.tick()
        transport.playToEnd()
        driver.tick()

        val outro = driver.requestOutro(0)
        assertTrue(outro is PerformanceDriver.Event.Advanced, outro.toString())
        assertEquals(1110, (outro as PerformanceDriver.Event.Advanced).segment)
        assertEquals(1110, transport.requested.last())
    }

    @Test
    fun requestingAnOutroTheFlowLacksIsReported() {
        val (driver, transport) = driver(repeats = 1)
        driver.start()
        transport.playToEnd()
        driver.tick()

        val event = driver.requestOutro(9)
        assertTrue(event is PerformanceDriver.Event.Blocked, event.toString())
    }

    @Test
    fun thePerformanceFinishesWhenTheOutroEnds() {
        val (driver, transport) = driver(repeats = 1)
        driver.start()
        transport.playToEnd()
        driver.tick()                       // prelude -> main
        transport.commit()
        transport.advance(10_000)
        driver.tick()
        transport.playToEnd()
        driver.tick()                       // main pass done, waiting
        driver.requestOutro(0)

        transport.playToEnd()               // the outro ends
        val event = driver.tick()

        assertTrue(event is PerformanceDriver.Event.Finished, event.toString())
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
        transport.playToEnd()
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
