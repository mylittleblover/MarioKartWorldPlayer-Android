package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flow assembly is where measured facts and project choices get mixed together.
 *
 * The measured part is the numbers, which come from BWAV headers; the choice part is the
 * timing, which comes from nowhere and is labelled as such. These tests keep the two from
 * drifting into each other: a part's frames must be exactly what the header said, and a flow
 * must never claim to be verified.
 */
class StageFlowTest {

    private fun part(
        role: StageFlow.Role,
        frames: Long,
        loop: Pair<Long, Long>? = null,
        rate: Int = 48000,
        name: String = "BGM_Stage_Coaster",
    ) = StageFlow.Part(
        role = role,
        fileName = "$name${suffix(role)}.bwav",
        frames = frames,
        sampleRate = rate,
        channels = 2,
        loopStart = loop?.first,
        loopEnd = loop?.second,
        // Outros carry their number, as the reader derives it from the file name.
        outroNumber = if (role == StageFlow.Role.Outro) 0 else null,
    )

    private fun suffix(role: StageFlow.Role): String = when (role) {
        StageFlow.Role.PreShort -> "_Pre_Short"
        StageFlow.Role.Pre -> "_Pre"
        StageFlow.Role.Bridge -> "_Bridge"
        StageFlow.Role.Main -> ""
        StageFlow.Role.Outro -> "_Outro00"
    }

    /** The measured Coaster values, used as the reference flow. */
    private fun coasterParts(): List<StageFlow.Part> = listOf(
        part(StageFlow.Role.PreShort, 534_857, loop = 205_714L to 534_857L),
        part(StageFlow.Role.Pre, 2_427_426, loop = 452_571L to 2_427_426L),
        part(StageFlow.Role.Bridge, 88_320),
        part(StageFlow.Role.Main, 3_373_711, loop = 493_714L to 3_373_711L),
        part(StageFlow.Role.Outro, 226_286),
    )

    // ---------------------------------------------------------------- roles

    @Test
    fun roleOfRecognisesEveryStructuralPart() {
        assertEquals(StageFlow.Role.PreShort, StageFlowReader.roleOf("BGM_Stage_Coaster_Pre_Short.bwav"))
        assertEquals(StageFlow.Role.Pre, StageFlowReader.roleOf("BGM_Stage_Coaster_Pre.bwav"))
        assertEquals(StageFlow.Role.Bridge, StageFlowReader.roleOf("BGM_Stage_Coaster_Bridge.bwav"))
        assertEquals(StageFlow.Role.Main, StageFlowReader.roleOf("BGM_Stage_Coaster.bwav"))
        assertEquals(StageFlow.Role.Outro, StageFlowReader.roleOf("BGM_Stage_Coaster_Outro00.bwav"))
    }

    @Test
    fun preShortIsNotMistakenForPre() {
        // Prefix matching alone would classify Pre_Short as Pre and silently drop the short
        // prelude from every flow.
        assertEquals(StageFlow.Role.PreShort, StageFlowReader.roleOf("X_Pre_Short.bwav"))
        assertEquals(StageFlow.Role.Pre, StageFlowReader.roleOf("X_Pre.bwav"))
    }

    @Test
    fun tempoAndArrangementVariantsAreNotStructuralParts() {
        // A speed variant is not a section; treating it as one would put a second, faster main
        // loop into the graph.
        assertNull(StageFlowReader.roleOf("BGM_Stage_Coaster_Fast.bwav"))
        assertNull(StageFlowReader.roleOf("BGM_Stage_Coaster_SFC.bwav"))
    }

    // ---------------------------------------------------------------- measured values

    @Test
    fun beatsComeFromTheHeaderVerbatim() {
        val flow = StageFlowReader.assemble("Coaster", coasterParts()).getOrThrow()
        val main = flow.part(StageFlow.Role.Main)!!
        assertEquals(3_373_711L, main.frames)
        assertEquals(48_000, main.sampleRate)
        assertEquals(2, main.channels)
        assertEquals(493_714L, main.loopStart)
        assertEquals(3_373_711L, main.loopEnd)
    }

    @Test
    fun durationIsDerivedNotStored() {
        val flow = StageFlowReader.assemble("Coaster", coasterParts()).getOrThrow()
        val bridge = flow.part(StageFlow.Role.Bridge)!!
        assertEquals(88_320L, bridge.frames)
        assertEquals(1.84, bridge.seconds, 0.001)
    }

    @Test
    fun aPartWithoutALoopReportsNone() {
        // The bridge genuinely does not loop; 0 would be a real frame number.
        val flow = StageFlowReader.assemble("Coaster", coasterParts()).getOrThrow()
        val bridge = flow.part(StageFlow.Role.Bridge)!!
        assertNull(bridge.loopStart)
        assertNull(bridge.loopEnd)
        assertTrue(!bridge.hasLoop)
    }

    @Test
    fun theMainPartLoops() {
        val flow = StageFlowReader.assemble("Coaster", coasterParts()).getOrThrow()
        assertTrue(flow.part(StageFlow.Role.Main)!!.hasLoop)
    }

    // ---------------------------------------------------------------- provenance

    @Test
    fun anAssembledFlowIsProvisional() {
        // The timings are ours. Presenting them as the game's behaviour would be a false claim.
        val flow = StageFlowReader.assemble("Coaster", coasterParts()).getOrThrow()
        assertEquals(StageFlow.Provenance.PROVISIONAL, flow.provenance)
        assertTrue(flow.isProvisional)
    }

    @Test
    fun onlyAnExplicitVerifiedFlowIsNotProvisional() {
        val flow = StageFlowReader.assemble("Coaster", coasterParts()).getOrThrow()
            .copy(provenance = StageFlow.Provenance.VERIFIED)
        assertTrue(!flow.isProvisional)
    }

    @Test
    fun theDefaultTimingStatesItsChoices() {
        val timing = StageFlow.DEFAULT_TIMING
        assertEquals(2, timing.mainRepeats)
        assertEquals(MusicGraphBuilderSyncPointExitCue, timing.preludeExit)
    }

    /** Local alias so the assertion reads clearly without importing the graph package. */
    private companion object {
        val MusicGraphBuilderSyncPointExitCue =
            com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.SyncPoint.ExitCue
    }

    // ---------------------------------------------------------------- assembly refusals

    @Test
    fun aFlowWithoutAMainPartIsRefused() {
        val parts = coasterParts().filter { it.role != StageFlow.Role.Main }
        val result = StageFlowReader.assemble("NoMain", parts)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Main"))
    }

    @Test
    fun twoPartsForOneRoleAreRefused() {
        val parts = coasterParts() + part(StageFlow.Role.Main, 1000)
        val result = StageFlowReader.assemble("Duplicated", parts)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("more than one"))
    }

    @Test
    fun partsDisagreeingOnSampleRateAreRefused() {
        val parts = coasterParts().map {
            if (it.role == StageFlow.Role.Bridge) it.copy(sampleRate = 44_100) else it
        }
        val result = StageFlowReader.assemble("Rates", parts)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("sample rate"))
    }

    @Test
    fun anEmptyFlowIsRefused() {
        assertTrue(StageFlowReader.assemble("Empty", emptyList()).isFailure)
    }

    // ---------------------------------------------------------------- header reading

    @Test
    fun aNonBwavPartIsRejectedWithTheParseReason() {
        val result = StageFlowReader.readPart("BGM_Stage_Coaster_Pre.bwav", ByteArray(64))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Pre.bwav"))
    }

    @Test
    fun aNonStructuralFileNameIsRejected() {
        val result = StageFlowReader.readPart("BGM_Stage_Coaster_Fast.bwav", ByteArray(64))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("not a structural part"))
    }

    // ---------------------------------------------------------------- completeness

    @Test
    fun aFlowWithPreAndMainIsComplete() {
        val parts = listOf(
            part(StageFlow.Role.Pre, 2_427_426),
            part(StageFlow.Role.Main, 3_373_711),
        )
        assertTrue(StageFlowReader.assemble("Minimal", parts).getOrThrow().isComplete())
    }

    @Test
    fun aPartCanBeLookedUpByRole() {
        val flow = StageFlowReader.assemble("Coaster", coasterParts()).getOrThrow()
        assertEquals(StageFlow.Role.Bridge, flow.part(StageFlow.Role.Bridge)!!.role)
        assertNotNull(flow.part(StageFlow.Role.Outro))
    }
}
