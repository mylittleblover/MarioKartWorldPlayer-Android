package com.mylittleblover.mkwplayer.graph

import com.mylittleblover.mkwplayer.library.BridgeCatalog
import com.mylittleblover.mkwplayer.library.BridgeTransition
import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bridge plan is where measurement replaces a guess, so the tests are about two things: that the
 * measured numbers reach the builder, and that nothing claims more certainty than it has.
 *
 * The layered case is the one worth the most attention. Four stages have a bridge that is mixed over the
 * main loop rather than handing over to it; scheduling that as a pre-entry splice would put a gap where
 * the game has an overlap, and the audio would still sound plausible.
 */
class MeasuredTransitionTest {

    private fun flow(name: String, bridge: Boolean = true) = StageFlow(
        name = name,
        parts = buildList {
            add(StageFlow.Part(StageFlow.Role.Main, "$name.bwav", 1_000_000, 48_000, 2, null, null))
            if (bridge) {
                add(StageFlow.Part(StageFlow.Role.Bridge, "${name}_Bridge.bwav", 88_320, 48_000, 2, null, null))
            }
        },
        timing = StageFlow.Timing(
            preludeExit = MusicGraphBuilder.SyncPoint.NextBar,
            bridgeEntry = MusicGraphBuilder.SyncPoint.NextBeat,
            outroEntry = MusicGraphBuilder.SyncPoint.ExitCue,
            outroSwitch = MusicGraphBuilder.SyncPoint.Immediate,
            mainRepeats = 2,
            bridgeFadeOutBeats = 0.25,
            bridgeFadeInBeats = 0.25,
        ),
        provenance = StageFlow.Provenance.PROVISIONAL,
    )

    private val bridgeSegment = 1102

    // ---------------------------------------------------------------- measured stages

    @Test
    fun aMeasuredStageUsesTheArchiveNumbers() {
        val plan = MeasuredTransition.planFor(flow("WestCity"), bridgeSegment)
        assertTrue(plan.isMeasured, plan.explanation)
        assertEquals(bridgeSegment, plan.bridgeSegment)
        assertEquals(MeasuredTransition.Basis.MEASURED_BRIDGE, plan.basis)
    }

    @Test
    fun aMeasuredStageDropsTheGuessedFades() {
        // The research found no section-level fade values anywhere in the corpus, so a measured splice
        // must not carry this app's invented fade.
        val plan = MeasuredTransition.planFor(flow("WestCity"), bridgeSegment)
        assertEquals(0.0, plan.fadeOutBeats)
        assertEquals(0.0, plan.fadeInBeats)
    }

    @Test
    fun westCityUsesPreEntryForItsMeasuredOverlap() {
        // WestCity's Full section starts 5625 samples before the switch, which is the deliberate overlap.
        // preEntry is the engine mechanism that reproduces "start early so it lands on time".
        val plan = MeasuredTransition.planFor(flow("WestCity"), bridgeSegment)
        assertTrue(plan.preEntry, plan.explanation)
        assertTrue(plan.explanation.contains("5625 samples early"), plan.explanation)
        assertTrue(plan.explanation.contains("117 ms"), plan.explanation)
    }

    @Test
    fun aHardCutDoesNotAskForPreEntry() {
        // IceBuildings is branch B: the destination resumes at its own offset, so there is no overlap and
        // nothing to start early.
        val plan = MeasuredTransition.planFor(flow("IceBuildings"), bridgeSegment)
        assertTrue(!plan.preEntry, plan.explanation)
        assertEquals(0.0, plan.explanation.contains("overlap").let { if (it) 1.0 else 0.0 })
    }

    @Test
    fun branchBIsMarkedAsResuming() {
        val ice = MeasuredTransition.planFor(flow("IceBuildings"), bridgeSegment)
        assertTrue(ice.destinationResumes, "IceBuildings resumes the Full track at 34286")

        val westCity = MeasuredTransition.planFor(flow("WestCity"), bridgeSegment)
        assertTrue(!westCity.destinationResumes, "WestCity starts the Full track at its beginning")
    }

    @Test
    fun theExplanationNamesTheSwitchSource() {
        assertTrue(
            MeasuredTransition.planFor(flow("WestCity"), bridgeSegment).explanation
                .contains("exit record"),
        )
        assertTrue(
            MeasuredTransition.planFor(flow("Cave"), bridgeSegment).explanation.contains("grid node"),
        )
        // AirshipFortress is one of the three inferred positions and must say so.
        assertTrue(
            MeasuredTransition.planFor(flow("AirshipFortress"), bridgeSegment).explanation
                .contains("inferred"),
        )
    }

    // ---------------------------------------------------------------- layered stages

    @Test
    fun aLayeredBridgeIsNotScheduledAsAHandover() {
        // The whole point: a layered bridge has no switch point, so wiring it as a pre-entry transition
        // would insert a gap the game does not have.
        val plan = MeasuredTransition.planFor(flow("DesertHills"), bridgeSegment)
        assertEquals(-1, plan.bridgeSegment, "a layered bridge must not be scheduled")
        assertTrue(!plan.preEntry)
        assertEquals(MeasuredTransition.Basis.MEASURED_BRIDGE, plan.basis)
    }

    @Test
    fun theLayeredExplanationSaysItIsLayered() {
        val plan = MeasuredTransition.planFor(flow("FirstCircuit"), bridgeSegment)
        assertTrue(plan.explanation.contains("layered", ignoreCase = true), plan.explanation)
        assertTrue(plan.explanation.contains("no switch point"), plan.explanation)
    }

    @Test
    fun allFourLayeredStagesAreHandledTheSameWay() {
        for (stage in listOf("DesertHills", "FirstCircuit", "MooMooFarm", "SFCMarioCircuit")) {
            val plan = MeasuredTransition.planFor(flow(stage), bridgeSegment)
            assertEquals(-1, plan.bridgeSegment, "$stage should not schedule its layered bridge")
        }
    }

    // ---------------------------------------------------------------- unmeasured stages

    @Test
    fun aStageWithoutAMeasuredBridgeKeepsTheProjectDefault() {
        val plan = MeasuredTransition.planFor(flow("PeachBeach"), bridgeSegment)
        assertEquals(MeasuredTransition.Basis.PROJECT_DEFAULT, plan.basis)
        assertTrue(!plan.isMeasured)
        assertEquals(bridgeSegment, plan.bridgeSegment)
        assertTrue(plan.explanation.contains("this app's choice"), plan.explanation)
    }

    @Test
    fun anUnmeasuredStageKeepsItsFades() {
        // Unlike a measured splice, the default is a musical handover and a fade is part of it.
        val plan = MeasuredTransition.planFor(flow("PeachBeach"), bridgeSegment)
        assertEquals(0.25, plan.fadeOutBeats)
        assertEquals(0.25, plan.fadeInBeats)
    }

    @Test
    fun aFlowWithNoBridgeAtAllSaysSo() {
        val plan = MeasuredTransition.planFor(flow("PeachBeach", bridge = false), null)
        assertEquals(-1, plan.bridgeSegment)
        assertTrue(plan.explanation.contains("No bridge", ignoreCase = true), plan.explanation)
    }

    @Test
    fun anUnmeasuredSyncPointComesFromTheFlow() {
        // A default plan must respect the flow's own timing choice rather than hardcoding one.
        val plan = MeasuredTransition.planFor(flow("PeachBeach"), bridgeSegment)
        assertEquals(MusicGraphBuilder.SyncPoint.NextBeat, plan.sync)
    }

    // ---------------------------------------------------------------- lookup

    @Test
    fun theBridgeIsFoundByTheFlowName() {
        val found = MeasuredTransition.measuredBridgeFor(flow("WestCity"))
        assertNotNull(found)
        assertEquals("WestCity", found.stage)
    }

    @Test
    fun aSuffixedFlowStillFindsItsBridge() {
        // The archive names stages plainly; a flow whose name carries a variant suffix must still match,
        // or the measurement would quietly not apply to the one case it is needed.
        val found = MeasuredTransition.measuredBridgeFor(flow("WestCity_Extra"))
        assertNotNull(found)
        assertEquals("WestCity", found.stage)
    }

    @Test
    fun aStageWithNoBridgeReturnsNull() {
        assertNull(MeasuredTransition.measuredBridgeFor(flow("PeachBeach")))
    }

    // ---------------------------------------------------------------- every measured stage

    @Test
    fun everyMeasuredStageProducesAPlanWithoutThrowing() {
        // Cheap coverage over the whole table: the plan must exist for every one of the twenty, and a
        // sequenced one must always name its switch.
        for (bridge in BridgeCatalog.all()) {
            val plan = MeasuredTransition.planFor(flow(bridge.stage), bridgeSegment)
            assertEquals(MeasuredTransition.Basis.MEASURED_BRIDGE, plan.basis, bridge.stage)
            if (bridge.mode == BridgeTransition.Mode.SEQUENCED) {
                assertTrue(plan.bridgeSegment != -1, "${bridge.stage} should schedule its bridge")
                assertTrue(
                    plan.explanation.contains("switch at sample"),
                    "${bridge.stage}: ${plan.explanation}",
                )
            }
        }
    }
}
