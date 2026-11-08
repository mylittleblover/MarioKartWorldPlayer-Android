package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A stage performance is an ordering over time, and the two ways to get it wrong are both plausible:
 * advancing a section the listener never heard, and choosing an outro on their behalf.
 *
 * Each test drives the driver the way the transport would, so what is checked is the sequence rather than
 * a single call in isolation.
 */
class StagePerformanceTest {

    private fun flow(prelude: Boolean = true, shortPrelude: Boolean = true, outros: Int = 2) = StageFlow(
        name = "Coaster",
        parts = buildList {
            add(part(StageFlow.Role.Main, 1_000_000))
            if (prelude) add(part(StageFlow.Role.Pre, 400_000))
            if (shortPrelude) add(part(StageFlow.Role.PreShort, 200_000))
            if (prelude || shortPrelude) add(part(StageFlow.Role.Bridge, 90_000))
            repeat(outros) { index ->
                add(
                    StageFlow.Part(
                        role = StageFlow.Role.Outro,
                        fileName = "Outro0$index.bwav",
                        frames = 220_000,
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

    private fun outros() = mapOf(0 to 1110, 1 to 1111)

    private fun performance(
        stageFlow: StageFlow = flow(),
        prelude: StagePerformance.Prelude = StagePerformance.Prelude.NORMAL,
        repeats: Int = 2,
    ) = StagePerformance(stageFlow, segments(), outros(), prelude, repeats)

    // ---------------------------------------------------------------- opening

    @Test
    fun aPerformanceOpensWithTheNormalPrelude() {
        val step = performance().start()
        assertTrue(step is StagePerformance.Step.Play, step.toString())
        assertEquals(1101, (step as StagePerformance.Step.Play).segment)
    }

    @Test
    fun theShortPreludeIsAChoiceNotAFallback() {
        // The game has both and uses them in circumstances this project has not established, so the short
        // one must be reachable on request rather than only when the normal one is missing.
        val step = performance(prelude = StagePerformance.Prelude.SHORT).start()
        assertEquals(1100, (step as StagePerformance.Step.Play).segment)
    }

    @Test
    fun askingForTheShortPreludeOnAFlowWithoutOneFallsBackToNormal() {
        // Not the other way round: the normal prelude is the longer listen, and a flow that has only a
        // short one would otherwise refuse to start.
        val step = performance(flow(prelude = true, shortPrelude = false), StagePerformance.Prelude.SHORT)
            .start()
        assertEquals(1101, (step as StagePerformance.Step.Play).segment)
    }

    @Test
    fun aFlowWithOnlyAMainSectionStartsThere() {
        val step = performance(flow(prelude = false, shortPrelude = false)).start()
        assertEquals(1103, (step as StagePerformance.Step.Play).segment)
    }

    @Test
    fun aFlowWithoutAMainSectionIsRefused() {
        // Starting the prelude alone would end in silence with no stated reason.
        val broken = StageFlow(
            name = "Coaster",
            parts = listOf(part(StageFlow.Role.Pre, 400_000)),
            timing = StageFlow.DEFAULT_TIMING,
            provenance = StageFlow.Provenance.PROVISIONAL,
        )
        val step = StagePerformance(broken, segments(), outros()).start()
        assertTrue(step is StagePerformance.Step.Blocked, step.toString())
    }

    // ---------------------------------------------------------------- ordering

    @Test
    fun thePreludeHandsOverToTheMainLoop() {
        val driver = performance()
        driver.start()
        val step = driver.segmentEnded()
        assertEquals(1103, (step as StagePerformance.Step.Play).segment)
        assertEquals(StagePerformance.Stage.MAIN, driver.current)
    }

    @Test
    fun theMainLoopRepeatsBeforeAnOutroIsOffered() {
        val driver = performance(repeats = 3)
        driver.start()
        driver.segmentEnded()   // prelude -> main
        assertFalse(driver.outroAvailable(), "no pass has completed yet")

        driver.segmentEnded()   // pass 1
        assertFalse(driver.outroAvailable())
        driver.segmentEnded()   // pass 2
        assertFalse(driver.outroAvailable())
        driver.segmentEnded()   // pass 3
        assertTrue(driver.outroAvailable(), "three passes should make an outro available")
    }

    @Test
    fun theMainLoopWaitsRatherThanChoosingAnOutro() {
        // The performance must not end on its own: which outro is taken is the listener's decision.
        val driver = performance(repeats = 1)
        driver.start()
        driver.segmentEnded()
        val step = driver.segmentEnded()
        assertTrue(step is StagePerformance.Step.Wait, step.toString())
        assertEquals(StagePerformance.Stage.MAIN, driver.current)
    }

    @Test
    fun anOutroEndsThePerformance() {
        val driver = performance(repeats = 1)
        driver.start()
        driver.segmentEnded()
        driver.segmentEnded()
        driver.requestOutro(0)
        val step = driver.segmentEnded()
        assertTrue(step is StagePerformance.Step.Done && step.finished, step.toString())
        assertTrue(driver.isComplete)
    }

    @Test
    fun theRequestedOutroIsTheOneThatPlays() {
        val driver = performance(repeats = 1)
        driver.start()
        driver.segmentEnded()
        driver.segmentEnded()
        val step = driver.requestOutro(1)
        assertEquals(1111, (step as StagePerformance.Step.Play).segment)
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun anOutroCannotBeTakenFromThePrelude() {
        val driver = performance()
        driver.start()
        val step = driver.requestOutro(0)
        assertTrue(step is StagePerformance.Step.Blocked, step.toString())
    }

    @Test
    fun anOutroTheFlowDoesNotCarryIsRefusedByName() {
        // Silently substituting a different outro changes what the listener hears last.
        val driver = performance(repeats = 1)
        driver.start()
        driver.segmentEnded()
        val step = driver.requestOutro(7)
        assertTrue(step is StagePerformance.Step.Blocked, step.toString())
        assertTrue((step as StagePerformance.Step.Blocked).reason.contains("no outro 7"))
    }

    @Test
    fun theRefusalListsWhatIsAvailable() {
        val driver = performance(repeats = 1)
        driver.start()
        driver.segmentEnded()
        val reason = (driver.requestOutro(7) as StagePerformance.Step.Blocked).reason
        assertTrue(reason.contains("0") && reason.contains("1"), reason)
    }

    @Test
    fun endingBeforeStartingIsRefused() {
        assertTrue(performance().segmentEnded() is StagePerformance.Step.Blocked)
    }

    @Test
    fun takingAnOutroAfterTheEndIsRefused() {
        val driver = performance(repeats = 1)
        driver.start()
        driver.segmentEnded()
        driver.segmentEnded()
        driver.requestOutro(0)
        driver.segmentEnded()
        val step = driver.requestOutro(1)
        assertTrue(step is StagePerformance.Step.Done, step.toString())
    }

    // ---------------------------------------------------------------- abandoning

    @Test
    fun stoppingBeforeAnOutroIsNotACompletedPerformance() {
        // The distinction matters: a listener who quits halfway has not heard a full stage, and anything
        // that counts completed performances must not count this one.
        val driver = performance()
        driver.start()
        val step = driver.abandon()
        assertTrue(step is StagePerformance.Step.Done && !step.finished, step.toString())
        assertFalse(driver.isComplete)
        assertEquals(StagePerformance.Stage.ABANDONED, driver.current)
    }

    @Test
    fun abandoningAfterTheEndDoesNotUndoTheCompletion() {
        val driver = performance(repeats = 1)
        driver.start()
        driver.segmentEnded()
        driver.segmentEnded()
        driver.requestOutro(0)
        driver.segmentEnded()
        val step = driver.abandon()
        assertTrue(step is StagePerformance.Step.Done && step.finished, step.toString())
    }

    // ---------------------------------------------------------------- reporting

    @Test
    fun theDescriptionNamesTheShortPreludeAsShort() {
        // A listener hearing it should be able to tell it was the short one, not a truncated normal one.
        val driver = performance(prelude = StagePerformance.Prelude.SHORT)
        driver.start()
        assertTrue(driver.describe().contains("short", ignoreCase = true), driver.describe())
    }

    @Test
    fun theDescriptionCountsMainPasses() {
        // Entering the main loop is not completing a pass. The first segmentEnded after the prelude only
        // arrives at the main loop, so the counter is still zero and the label says pass 1.
        val driver = performance(repeats = 3)
        driver.start()
        driver.segmentEnded()
        assertEquals(0, driver.mainPassesCompleted(), "entering the loop is not a completed pass")
        assertTrue(driver.describe().contains("pass 1 of 3"), driver.describe())

        driver.segmentEnded()
        assertEquals(1, driver.mainPassesCompleted())
        assertTrue(driver.describe().contains("pass 2 of 3"), driver.describe())
    }

    @Test
    fun everyOutroIsReportedAsAvailable() {
        assertEquals(listOf(0, 1), performance().availableOutros())
    }

    @Test
    fun theLastRequestedSegmentIsRemembered() {
        val driver = performance(prelude = StagePerformance.Prelude.SHORT)
        driver.start()
        assertEquals(1100, driver.playing)
        driver.segmentEnded()
        assertEquals(1103, driver.playing)
    }
}
