package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The performance the user described: a looping prelude, an event that moves it on, a main loop that plays
 * the bridge on the way in, and an ending chosen by how far in the listener has got.
 *
 * The tests drive it as the transport would -- by commands, never by a clock -- because the engine ends a
 * segment by going inactive and never follows a transition by itself.
 */
class StagePerformanceTest {

    private fun flow(prelude: Boolean = true, shortPrelude: Boolean = true, outros: Int = 2) = StageFlow(
        name = "Coaster",
        parts = buildList {
            add(part(StageFlow.Role.Main, 1_000_000))
            if (prelude) add(part(StageFlow.Role.Pre, 400_000))
            if (shortPrelude) add(part(StageFlow.Role.PreShort, 200_000))
            if (prelude || shortPrelude) add(part(StageFlow.Role.Bridge, 90_000))
            // Two endings of clearly different length, so "nearest" has something to choose between.
            repeat(outros) { index ->
                add(
                    StageFlow.Part(
                        role = StageFlow.Role.Outro,
                        fileName = "Outro0$index.bwav",
                        frames = if (index == 0) 220_000 else 900_000,
                        sampleRate = 48_000,
                        channels = 2,
                        loopStart = null,
                        loopEnd = null,
                        outroNumber = index,
                    ),
                )
            }
        },
        timing = StageFlow.DEFAULT_TIMING,
        provenance = StageFlow.Provenance.PROVISIONAL,
    )

    private fun part(role: StageFlow.Role, frames: Long) = StageFlow.Part(
        role = role,
        fileName = "${role.name}.bwav",
        frames = frames,
        sampleRate = 48_000,
        channels = 2,
        loopStart = null,
        loopEnd = null,
    )

    private fun segments() = mapOf(
        StageFlow.Role.Main to 1103,
        StageFlow.Role.Pre to 1101,
        StageFlow.Role.PreShort to 1100,
        StageFlow.Role.Bridge to 1102,
    )

    private fun performance(
        stageFlow: StageFlow = flow(),
        prelude: StagePerformance.Prelude = StagePerformance.Prelude.NORMAL,
        outros: Map<Int, Int> = mapOf(0 to 1110, 1 to 1111),
    ) = StagePerformance(stageFlow, segments(), outros, prelude)

    // ---------------------------------------------------------------- opening

    @Test
    fun aPerformanceOpensWithTheNormalPrelude() {
        val step = performance().start()
        assertEquals(1101, (step as StagePerformance.Step.Play).segment)
    }

    @Test
    fun theShortPreludeIsAChoiceNotAFallback() {
        val step = performance(prelude = StagePerformance.Prelude.SHORT).start()
        assertEquals(1100, (step as StagePerformance.Step.Play).segment)
    }

    @Test
    fun theDescriptionSaysThePreludeLoops() {
        // The word matters: a prelude that ends is what made the performance stop dead.
        val driver = performance()
        driver.start()
        assertTrue(driver.describe().contains("looping"), driver.describe())
    }

    @Test
    fun aFlowWithoutAMainSectionIsRefused() {
        val broken = StageFlow(
            name = "Coaster",
            parts = listOf(part(StageFlow.Role.Pre, 400_000)),
            timing = StageFlow.DEFAULT_TIMING,
            provenance = StageFlow.Provenance.PROVISIONAL,
        )
        assertTrue(StagePerformance(broken, segments(), emptyMap()).start() is StagePerformance.Step.Blocked)
    }

    // ---------------------------------------------------------------- the advance event

    @Test
    fun thePreludeStaysLoopingUntilTheEventArrives() {
        // Nothing advances it: the prelude is the section the game repeats until the race starts.
        val driver = performance()
        driver.start()
        assertEquals(StagePerformance.Stage.PRELUDE, driver.current)
        assertEquals(1101, driver.playing)
    }

    @Test
    fun theAdvanceEventMovesToTheMainLoop() {
        val driver = performance()
        driver.start()
        val step = driver.advance()

        assertEquals(1103, (step as StagePerformance.Step.Play).segment)
        assertEquals(StagePerformance.Stage.MAIN, driver.current)
    }

    @Test
    fun theBridgeIsNotTheDriversBusiness() {
        // The transition rule names the bridge, so requesting main is what makes the engine play it. A
        // driver that scheduled the bridge itself would disagree with the compiled graph.
        val driver = performance()
        driver.start()
        val step = driver.advance()
        assertEquals(1103, (step as StagePerformance.Step.Play).segment, "the driver must request main")
        assertTrue(!step.reason.contains("bridge", ignoreCase = true), step.reason)
    }

    @Test
    fun advancingTwiceDoesNotRestartTheLoop() {
        val driver = performance()
        driver.start()
        driver.advance()
        val again = driver.advance()
        assertTrue(again is StagePerformance.Step.Hold, again.toString())
    }

    @Test
    fun advancingBeforeStartingStartsThePerformance() {
        // "Begin" is a sensible first command, so it is not an error.
        val step = performance().advance()
        assertTrue(step is StagePerformance.Step.Play, step.toString())
    }

    // ---------------------------------------------------------------- the ending

    @Test
    fun theNearestEndingIsChosenByPosition() {
        // Ending 0 is 220,000 frames long; ending 1 is 900,000. A position near the start must pick 0.
        val driver = performance()
        driver.start()
        driver.advance()

        assertEquals(0, driver.nearestOutro(100_000))
        assertEquals(1, driver.nearestOutro(800_000))
    }

    @Test
    fun aLongPositionPicksTheLongerEnding() {
        val driver = performance()
        driver.start()
        driver.advance()
        assertEquals(1, driver.nearestOutro(5_000_000))
    }

    @Test
    fun tiesGoToTheLowestNumber() {
        // Deterministic rather than depending on map iteration order. With two endings of equal length the
        // lowest number wins.
        val even = StageFlow(
            name = "Coaster",
            parts = listOf(
                part(StageFlow.Role.Main, 1_000_000),
                StageFlow.Part(StageFlow.Role.Outro, "a.bwav", 100, 48_000, 2, null, null, 0),
                StageFlow.Part(StageFlow.Role.Outro, "b.bwav", 100, 48_000, 2, null, null, 1),
            ),
            timing = StageFlow.DEFAULT_TIMING,
            provenance = StageFlow.Provenance.PROVISIONAL,
        )
        val driver = StagePerformance(even, segments(), mapOf(0 to 1110, 1 to 1111))
        assertEquals(0, driver.nearestOutro(100))
    }

    @Test
    fun theChosenEndingPlays() {
        val driver = performance()
        driver.start()
        driver.advance()
        val step = driver.takeOutro(100_000)

        assertEquals(1110, (step as StagePerformance.Step.Play).segment)
        assertEquals(StagePerformance.Stage.OUTRO, driver.current)
    }

    @Test
    fun anEndingCannotBeTakenFromThePrelude() {
        val driver = performance()
        driver.start()
        assertTrue(driver.takeOutro(0) is StagePerformance.Step.Blocked)
    }

    @Test
    fun aFlowWithNoEndingsSaysSoRatherThanFinishingSilently() {
        val driver = performance(outros = mapOf())
        driver.start()
        driver.advance()
        val step = driver.takeOutro(0)
        assertTrue(step is StagePerformance.Step.Blocked, step.toString())
        assertTrue((step as StagePerformance.Step.Blocked).reason.contains("no endings"))
    }

    // ---------------------------------------------------------------- the end

    @Test
    fun theEndingEndsThePerformance() {
        val driver = performance()
        driver.start()
        driver.advance()
        driver.takeOutro(100_000)
        val step = driver.finalEnded()

        assertTrue(step is StagePerformance.Step.Done && step.finished, step.toString())
        assertTrue(driver.isComplete)
    }

    @Test
    fun aLoopingSectionHasNoNaturalEnd() {
        // The prelude and main loop loop, so reporting them as ended is a caller bug worth naming.
        val driver = performance()
        driver.start()
        assertTrue(driver.finalEnded() is StagePerformance.Step.Blocked)

        driver.advance()
        assertTrue(driver.finalEnded() is StagePerformance.Step.Blocked)
    }

    // ---------------------------------------------------------------- stopping

    @Test
    fun stoppingIsNotACompletedPerformance() {
        val driver = performance()
        driver.start()
        val step = driver.abandon()

        assertTrue(step is StagePerformance.Step.Done && !step.finished, step.toString())
        assertFalse(driver.isComplete)
    }

    @Test
    fun takingAnEndingAfterTheEndIsRefused() {
        val driver = performance()
        driver.start()
        driver.advance()
        driver.takeOutro(100_000)
        driver.finalEnded()
        assertTrue(driver.takeOutro(100_000) is StagePerformance.Step.Done)
    }

    @Test
    fun everyEndingIsReportedAsAvailable() {
        assertEquals(listOf(0, 1), performance().availableOutros())
    }
}
