package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bridge table is the first place this project has established behaviour rather than guessed it, so
 * the tests do two jobs: check the transcription against the research, and check that the type cannot
 * silently present a layered bridge as a sequenced one.
 *
 * The second job is the important one. A layered bridge has no switch point by design; if the type ever
 * allowed one, the player would insert a handover the game does not have, and nothing about the audio
 * would sound broken enough to notice.
 */
class BridgeCatalogTest {

    // ---------------------------------------------------------------- the type's invariants

    @Test
    fun aLayeredBridgeCannotClaimASwitchPoint() {
        // The research says three separate signals are empty for exactly these four stages. A switch
        // point here would turn an overlay into a splice.
        assertFailsWith<IllegalArgumentException> {
            BridgeTransition(
                stage = "DesertHills",
                mode = BridgeTransition.Mode.LAYERED,
                bpm = 130.0,
                beatSamples = 22_153.8,
                bridgeSamples = 44_308,
                switchSample = 1_000,
                fullEntersAt = null,
                fullSourceOffset = 0,
                overlapSamples = 0,
                switchSource = BridgeTransition.SwitchSource.NONE,
            )
        }
    }

    @Test
    fun aSequencedBridgeMustStateItsSwitchPoint() {
        assertFailsWith<IllegalArgumentException> {
            BridgeTransition(
                stage = "WestCity",
                mode = BridgeTransition.Mode.SEQUENCED,
                bpm = 128.0,
                beatSamples = 22_500.0,
                bridgeSamples = 79_266,
                switchSample = null,
                fullEntersAt = null,
                fullSourceOffset = 0,
                overlapSamples = 0,
                switchSource = BridgeTransition.SwitchSource.EXIT_RECORD,
            )
        }
    }

    @Test
    fun aSwitchPointOutsideTheBridgeIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            BridgeTransition(
                stage = "X",
                mode = BridgeTransition.Mode.SEQUENCED,
                bpm = 120.0,
                beatSamples = 24_000.0,
                bridgeSamples = 10_000,
                switchSample = 20_000,
                fullEntersAt = 20_000,
                fullSourceOffset = 0,
                overlapSamples = 0,
                switchSource = BridgeTransition.SwitchSource.EXIT_RECORD,
            )
        }
    }

    @Test
    fun aNegativeOverlapIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            BridgeTransition(
                stage = "X",
                mode = BridgeTransition.Mode.SEQUENCED,
                bpm = 120.0,
                beatSamples = 24_000.0,
                bridgeSamples = 10_000,
                switchSample = 5_000,
                fullEntersAt = 5_000,
                fullSourceOffset = 0,
                overlapSamples = -1,
                switchSource = BridgeTransition.SwitchSource.EXIT_RECORD,
            )
        }
    }

    @Test
    fun aNegativeSourceOffsetIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            BridgeTransition(
                stage = "X",
                mode = BridgeTransition.Mode.SEQUENCED,
                bpm = 120.0,
                beatSamples = 24_000.0,
                bridgeSamples = 10_000,
                switchSample = 5_000,
                fullEntersAt = 5_000,
                fullSourceOffset = -5,
                overlapSamples = 0,
                switchSource = BridgeTransition.SwitchSource.EXIT_RECORD,
            )
        }
    }

    @Test
    fun aZeroBpmIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            BridgeTransition(
                stage = "X",
                mode = BridgeTransition.Mode.LAYERED,
                bpm = 0.0,
                beatSamples = 1.0,
                bridgeSamples = 10,
                switchSample = null,
                fullEntersAt = null,
                fullSourceOffset = 0,
                overlapSamples = 0,
                switchSource = BridgeTransition.SwitchSource.NONE,
            )
        }
    }

    // ---------------------------------------------------------------- the transcription

    @Test
    fun twentyBridgesAreListed() {
        // Twenty stages have a Bridge asset; the rest have none, and an absent row means that rather
        // than "not yet researched".
        assertEquals(20, BridgeCatalog.all().size)
    }

    @Test
    fun exactlyFourAreLayered() {
        // The research established this by three independent signals agreeing 4/4 against 0/16.
        assertEquals(4, BridgeCatalog.layered().size)
        assertEquals(16, BridgeCatalog.sequenced().size)
    }

    @Test
    fun theLayeredStagesAreTheOnesTheResearchNamed() {
        assertEquals(
            listOf("DesertHills", "FirstCircuit", "MooMooFarm", "SFCMarioCircuit"),
            BridgeCatalog.layered().map { it.stage },
        )
    }

    @Test
    fun everySequencedBridgeIsMusicallyConsistent() {
        // The invariant the research verified: the Full section must reach its own grid origin exactly
        // when the bridge reaches the switch, which is what the predicted overlap encodes.
        for (bridge in BridgeCatalog.sequenced()) {
            val switch = bridge.switchSample!!
            val enters = bridge.fullEntersAt
            assertTrue(enters != null, "${bridge.stage} has no Full entry")
            if (enters != null) {
                // Either the Full starts exactly at the switch (hard cut) or it starts earlier by the
                // overlap the research predicted. It must never start after the switch.
                assertTrue(
                    enters <= switch,
                    "${bridge.stage}: Full enters at $enters, after the switch at $switch",
                )
                assertEquals(
                    switch - enters,
                    bridge.overlapSamples,
                    "${bridge.stage}: overlap does not equal the early entry",
                )
            }
        }
    }

    @Test
    fun theSpotCheckedBridgesAreTheOnesVerifiedOnTheRunningGame() {
        // Worth pinning: these three are the reason the table is treated as measured rather than derived.
        assertEquals(setOf("WestCity", "IceBuildings", "Lava"), BridgeCatalog.spotChecked)
        for (stage in BridgeCatalog.spotChecked) {
            assertTrue(BridgeCatalog.forStage(stage) != null, "$stage is spot-checked but absent")
        }
    }

    @Test
    fun westCityMatchesTheVerifiedNumbers() {
        // The research's prediction was 44297; the running game reported 44298. The table stores the
        // researched value, and the one-sample difference is a table-construction rounding, not a model
        // error -- so the test asserts the researched number rather than pretending it is exact.
        val westCity = BridgeCatalog.forStage("WestCity")!!
        assertEquals(49_922L, westCity.switchSample)
        assertEquals(44_297L, westCity.fullSourceOffset + westCity.fullEntersAt!!)
        assertEquals(5_625L, westCity.overlapSamples)
        assertEquals(BridgeTransition.SwitchSource.EXIT_RECORD, westCity.switchSource)
    }

    @Test
    fun iceBuildingsIsABranchBHardCut() {
        // Branch B: the Full section resumes from its own Full_FromBridge offset, so there is no overlap.
        val ice = BridgeCatalog.forStage("IceBuildings")!!
        assertEquals(68_571L, ice.switchSample)
        assertEquals(68_571L, ice.fullEntersAt)
        assertEquals(34_286L, ice.fullSourceOffset)
        assertEquals(0L, ice.overlapSamples)
    }

    @Test
    fun lavaHasItsShortPredictedOverlap() {
        val lava = BridgeCatalog.forStage("Lava")!!
        assertEquals(96_000L, lava.switchSample)
        assertEquals(94_001L, lava.fullEntersAt)
        assertEquals(1_999L, lava.overlapSamples)
    }

    @Test
    fun theInferredSwitchPointsAreIdentified() {
        // Three stages carry a single label anchor and no final node; their switch position is derived
        // from the musical grid rather than read out, and the UI must be able to say so.
        assertEquals(
            listOf("AirshipFortress", "ChocoMountain", "DinoBones"),
            BridgeCatalog.inferred().map { it.stage },
        )
        for (bridge in BridgeCatalog.inferred()) {
            assertTrue(!bridge.isSwitchEncoded, "${bridge.stage} claims to be encoded")
        }
    }

    @Test
    fun everySequencedBridgeIsEitherEncodedOrMarkedInferred() {
        // No third state is allowed to exist silently.
        for (bridge in BridgeCatalog.sequenced()) {
            val source = bridge.switchSource
            assertTrue(
                source == BridgeTransition.SwitchSource.EXIT_RECORD ||
                    source == BridgeTransition.SwitchSource.FINAL_NODE ||
                    source == BridgeTransition.SwitchSource.LABEL_ANCHOR_INFERRED,
                "${bridge.stage} has an unexpected source $source",
            )
        }
    }

    @Test
    fun aStageWithoutABridgeHasNoRow() {
        // The absence is informative: it means no bridge asset exists, not that it is unknown.
        assertNull(BridgeCatalog.forStage("Rainbow"))
        assertNull(BridgeCatalog.forStage("PeachBeach"))
    }

    @Test
    fun stageCodesAreUnique() {
        val codes = BridgeCatalog.all().map { it.stage }
        assertEquals(codes.size, codes.toSet().size, "a stage appears twice")
    }

    // ---------------------------------------------------------------- wording

    @Test
    fun aLayeredBridgeSaysTheTimingIsThisAppsChoice() {
        // The research could not establish the overlay's entry and exit instants, so any arrangement is
        // this app's and the description has to say so.
        val text = BridgeCatalog.layered().first().describe()
        assertTrue(text.contains("this app's", ignoreCase = true), text)
        assertTrue(text.contains("layered", ignoreCase = true), text)
    }

    @Test
    fun aSequencedBridgeNamesItsSource() {
        val westCity = BridgeCatalog.forStage("WestCity")!!.describe()
        assertTrue(westCity.contains("encoded exit record"), westCity)

        val airship = BridgeCatalog.forStage("AirshipFortress")!!.describe()
        assertTrue(airship.contains("inferred"), airship)
    }

    @Test
    fun theOverlapIsReportedInMilliseconds() {
        val westCity = BridgeCatalog.forStage("WestCity")!!
        // overlapSeconds returns seconds -- 5625 samples at 48 kHz is 0.117 s. The millisecond figure
        // is what the description shows, so that is asserted separately rather than conflated.
        assertEquals(0.1171875, westCity.overlapSeconds(), 0.01)
        assertTrue(westCity.describe().contains("117 ms"), westCity.describe())
    }

    @Test
    fun switchBeatsAreDerivedFromTheStoredBeatLength() {
        val lava = BridgeCatalog.forStage("Lava")!!
        assertEquals(6.0, lava.switchBeats()!!, 0.01)
        assertNull(BridgeCatalog.layered().first().switchBeats())
    }

    @Test
    fun theSourceIsStatedSoAUiCanCiteIt() {
        assertTrue(BridgeCatalog.SOURCE.contains("Splice_Sheet_All_Bridges.csv"), BridgeCatalog.SOURCE)
    }
}
