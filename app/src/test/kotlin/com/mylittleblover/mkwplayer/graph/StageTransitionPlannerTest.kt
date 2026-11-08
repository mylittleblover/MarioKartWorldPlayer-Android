package com.mylittleblover.mkwplayer.graph

import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The planner is the whole rule set for one stage in one place, so a mistake here is a mistake in every
 * flow. Three properties carry the weight: every section can be reached, the outro a caller asks for is
 * the outro it gets, and a layered bridge is not wired as a handover.
 */
class StageTransitionPlannerTest {

    private fun flow(name: String, bridge: Boolean = true, outros: Int = 2) = StageFlow(
        name = name,
        parts = buildList {
            add(part(StageFlow.Role.Main, "$name.bwav", 1_000_000))
            add(part(StageFlow.Role.Pre, "${name}_Pre.bwav", 400_000))
            add(part(StageFlow.Role.PreShort, "${name}_Pre_Short.bwav", 200_000))
            if (bridge) add(part(StageFlow.Role.Bridge, "${name}_Bridge.bwav", 90_000))
            repeat(outros) { index ->
                add(
                    StageFlow.Part(
                        role = StageFlow.Role.Outro,
                        fileName = "${name}_Outro0$index.bwav",
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

    private fun part(role: StageFlow.Role, name: String, frames: Long) = StageFlow.Part(
        role = role,
        fileName = name,
        frames = frames,
        sampleRate = 48_000,
        channels = 2,
        loopStart = null,
        loopEnd = null,
    )

    private val segments = StageTransitionPlanner.Segments(
        main = 1100,
        bridge = 1102,
        prelude = 1101,
        preludeShort = 1103,
        outros = mapOf(0 to 1110, 1 to 1111),
    )

    private fun plan(name: String, bridge: Boolean = true) =
        StageTransitionPlanner.plan(flow(name, bridge), segments, firstIndex = 1400)

    // ---------------------------------------------------------------- coverage

    @Test
    fun everySectionIsReachable() {
        // A section with no inbound rule is a dead end; the flow would enter it never.
        val plan = plan("Coaster")
        val destinations = plan.transitions.flatMap { it.destinations }.toSet()
        for (segment in listOf(1100, 1110, 1111)) {
            assertTrue(segment in destinations, "segment $segment is unreachable")
        }
    }

    @Test
    fun bothPreludesCanReachTheMainLoop() {
        val plan = plan("Coaster")
        val sources = plan.transitions.flatMap { it.sources }.toSet()
        assertTrue(1101 in sources, "the prelude has no way out")
        assertTrue(1103 in sources, "the short prelude has no way out")
    }

    @Test
    fun theBridgeHasItsOwnWayOut() {
        // Without this, a flow that enters the bridge never reaches the main loop.
        val plan = plan("Coaster")
        val bridgeOut = plan.transitions.filter { 1102 in it.sources }
        assertTrue(bridgeOut.isNotEmpty(), "the bridge has no outbound rule")
        assertTrue(bridgeOut.any { it.destinations == listOf(1100) })
    }

    @Test
    fun everyOutroIsAddressableByItsOwnNumber() {
        val plan = plan("Coaster")
        for ((number, segment) in segments.outros) {
            val rule = plan.transitions.firstOrNull {
                it.sources == listOf(1100) && it.destinations == listOf(segment)
            }
            assertTrue(rule != null, "outro $number has no rule of its own")
        }
    }

    @Test
    fun outroReplacementRulesExistForEveryOrderedPair() {
        // Two outros means two ordered pairs. A request that has not run yet must be replaceable.
        val plan = plan("Coaster")
        val replacements = plan.transitions.filter {
            it.sources.size == 1 && it.sources[0] in segments.outros.values &&
                it.destinations.size == 1 && it.destinations[0] in segments.outros.values
        }
        assertEquals(2, replacements.size, "expected one rule per ordered pair")
        assertTrue(replacements.none { it.sources == it.destinations }, "a rule from a section to itself")
    }

    @Test
    fun transitionIndicesAreUnique() {
        // Two rules sharing an index makes one of them unreachable in the engine's table.
        val plan = plan("Coaster")
        val indices = plan.transitions.map { it.index }
        assertEquals(indices.size, indices.toSet().size, "duplicate transition index")
    }

    @Test
    fun indicesStartWhereTheCallerAsked() {
        val plan = plan("Coaster")
        assertEquals(1400, plan.transitions.minOf { it.index })
    }

    // ---------------------------------------------------------------- measured bridges

    @Test
    fun aMeasuredBridgeIsWiredThroughPreEntry() {
        // WestCity's handover has a measured overlap, and preEntry is the engine mechanism that produces
        // it: start the bridge early so the destination lands on time.
        val plan = plan("WestCity")
        assertTrue(plan.hasMeasuredBridge, "WestCity should be measured")

        val preludeRules = plan.transitions.filter { it.sources.contains(1101) || it.sources.contains(1103) }
        assertTrue(preludeRules.isNotEmpty())
        assertTrue(preludeRules.all { it.preEntry }, "a measured overlap needs preEntry")
        assertTrue(preludeRules.all { it.bridge == 1102 })
    }

    @Test
    fun aMeasuredBridgeCarriesNoInventedFade() {
        // The research found no section-level fade values, so a measured splice must not carry one.
        val plan = plan("WestCity")
        val preludeRules = plan.transitions.filter { it.sources.contains(1101) }
        assertTrue(preludeRules.all { it.fadeOut.beats == 0.0 && it.fadeIn.beats == 0.0 })
    }

    @Test
    fun aProjectChosenBridgeKeepsItsFades() {
        // PeachBeach has no measured bridge, so the flow's own timing applies, fades included.
        val peach = flow("PeachBeach").copy(
            timing = StageFlow.DEFAULT_TIMING.copy(
                bridgeFadeOutBeats = 0.5,
                bridgeFadeInBeats = 0.25,
            ),
        )
        val plan = StageTransitionPlanner.plan(peach, segments, firstIndex = 0)
        assertTrue(!plan.hasMeasuredBridge)
        val preludeRule = plan.transitions.first { it.sources.contains(1101) }
        assertEquals(0.5, preludeRule.fadeOut.beats)
        assertEquals(0.25, preludeRule.fadeIn.beats)
    }

    @Test
    fun aHardCutMeasuredBridgeDoesNotUsePreEntry() {
        // IceBuildings is branch B: the destination resumes at its own offset, so there is no overlap and
        // nothing to start early.
        val plan = plan("IceBuildings")
        val preludeRules = plan.transitions.filter { it.sources.contains(1101) }
        assertTrue(preludeRules.all { !it.preEntry }, "a hard cut must not pre-enter")
    }

    // ---------------------------------------------------------------- layered bridges

    @Test
    fun aLayeredBridgeIsNotWiredAtAll() {
        // The whole point. Scheduling a layered bridge as a handover would insert a gap where the game
        // has an overlap, and the audio would still sound plausible.
        val plan = plan("DesertHills")
        assertTrue(!plan.hasMeasuredBridge, "a layered bridge is not a measured handover")
        val preludeRules = plan.transitions.filter { it.sources.contains(1101) || it.sources.contains(1103) }
        assertTrue(
            preludeRules.all { it.bridge == -1 },
            "a layered bridge must not be declared on the prelude rule",
        )
        assertTrue(
            plan.transitions.none { it.sources == listOf(1102) },
            "a layered bridge must have no outbound rule, because it never enters",
        )
    }

    @Test
    fun allFourLayeredStagesAreTreatedTheSame() {
        for (stage in StageTransitionPlanner.layeredStages()) {
            val plan = plan(stage)
            val preludeRules = plan.transitions.filter { it.sources.contains(1101) }
            assertTrue(preludeRules.all { it.bridge == -1 }, "$stage wired its layered bridge")
        }
    }

    @Test
    fun theLayeredStageSetIsTheOneTheResearchNamed() {
        assertEquals(
            setOf("DesertHills", "FirstCircuit", "MooMooFarm", "SFCMarioCircuit"),
            StageTransitionPlanner.layeredStages(),
        )
    }

    // ---------------------------------------------------------------- no bridge at all

    @Test
    fun aFlowWithoutABridgeHasNoBridgePlan() {
        val plan = plan("PeachBeach", bridge = false)
        assertNull(plan.bridge)
        assertTrue(!plan.hasMeasuredBridge)
    }

    @Test
    fun aFlowWithoutABridgeStillReachesTheMainLoop() {
        // The prelude rule must point straight at main, with no bridge declared.
        val plan = plan("PeachBeach", bridge = false)
        val preludeRule = plan.transitions.firstOrNull { it.sources.contains(1101) }
        assertNotNull(preludeRule)
        assertEquals(listOf(1100), preludeRule.destinations)
        assertEquals(-1, preludeRule.bridge)
    }

    // ---------------------------------------------------------------- wording

    @Test
    fun theProvenanceLineNeverClaimsTheGamesHandoverForAGuess() {
        val plan = plan("PeachBeach")
        val line = StageTransitionPlanner.provenanceLine(flow("PeachBeach"), plan)
        assertTrue(line.contains("this app's choice"), line)
    }

    @Test
    fun theProvenanceLineStatesTheMeasuredSample() {
        val plan = plan("WestCity")
        val line = StageTransitionPlanner.provenanceLine(flow("WestCity"), plan)
        assertTrue(line.contains("switch at sample"), line)
        assertTrue(line.contains("measured", ignoreCase = true), line)
    }

    @Test
    fun theProvenanceLineSaysWhenThereIsNoBridge() {
        val noBridge = flow("PeachBeach", bridge = false)
        val plan = StageTransitionPlanner.plan(noBridge, segments, firstIndex = 0)
        assertTrue(
            StageTransitionPlanner.provenanceLine(noBridge, plan).contains("No bridge"),
        )
    }
}
