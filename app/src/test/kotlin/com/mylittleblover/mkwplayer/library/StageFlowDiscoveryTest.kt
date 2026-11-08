package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Discovery groups picked files into stages.
 *
 * The failure it exists to prevent is picking four of five parts and hearing a flow that
 * silently skips the bridge, so the prefix rules are pinned case by case.
 */
class StageFlowDiscoveryTest {

    private fun part(name: String, role: StageFlow.Role, outroNumber: Int? = null) =
        StageFlow.Part(
            role = role,
            fileName = name,
            frames = 1000,
            sampleRate = 48000,
            channels = 2,
            loopStart = null,
            loopEnd = null,
            outroNumber = outroNumber,
        )

    private fun coasterParts() = listOf(
        part("BGM_Stage_Coaster_Pre_Short.bwav", StageFlow.Role.PreShort),
        part("BGM_Stage_Coaster_Pre.bwav", StageFlow.Role.Pre),
        part("BGM_Stage_Coaster_Bridge.bwav", StageFlow.Role.Bridge),
        part("BGM_Stage_Coaster.bwav", StageFlow.Role.Main),
        part("BGM_Stage_Coaster_Outro00.bwav", StageFlow.Role.Outro, 0),
        part("BGM_Stage_Coaster_Outro01.bwav", StageFlow.Role.Outro, 1),
    )

    // ---------------------------------------------------------------- prefix

    @Test
    fun everyPartOfAStageSharesOnePrefix() {
        for (name in listOf(
            "BGM_Stage_Coaster_Pre_Short.bwav",
            "BGM_Stage_Coaster_Pre.bwav",
            "BGM_Stage_Coaster_Bridge.bwav",
            "BGM_Stage_Coaster.bwav",
            "BGM_Stage_Coaster_Outro00.bwav",
            "BGM_Stage_Coaster_Outro05.bwav",
        )) {
            assertEquals(
                "BGM_Stage_Coaster",
                StageFlowDiscovery.prefixOf(name),
                "prefix for $name",
            )
        }
    }

    @Test
    fun preShortSuffixWinsOverPre() {
        // Longest suffix first. Matching `_Pre` against `..._Pre_Short` would leave `_Short`
        // stuck to the prefix and split one stage into two groups.
        assertEquals(
            "BGM_Stage_Coaster",
            StageFlowDiscovery.prefixOf("BGM_Stage_Coaster_Pre_Short.bwav"),
        )
    }

    @Test
    fun aFileWithNoRoleSuffixIsItsOwnPrefix() {
        assertEquals("SomeSong", StageFlowDiscovery.prefixOf("SomeSong.bwav"))
    }

    @Test
    fun aTempoVariantSharesTheStagePrefix() {
        // `_Fast` is not a structural part, but it still shares the prefix, so grouping by it
        // does not scatter the stage across two names.
        assertEquals("BGM_Stage_Coaster", StageFlowDiscovery.prefixOf("BGM_Stage_Coaster_Fast.bwav"))
    }

    // ---------------------------------------------------------------- grouping

    @Test
    fun oneStageBecomesOneGroup() {
        val groups = StageFlowDiscovery.group(coasterParts())
        assertEquals(1, groups.size)
        assertEquals("BGM_Stage_Coaster", groups[0].prefix)
        assertEquals(6, groups[0].parts.size)
        assertTrue(groups[0].hasStructure)
        assertEquals(2, groups[0].outroCount)
    }

    @Test
    fun twoStagesBecomeTwoGroups() {
        val parts = coasterParts() + listOf(
            part("BGM_Stage_PeachBeach.bwav", StageFlow.Role.Main),
            part("BGM_Stage_PeachBeach_Pre.bwav", StageFlow.Role.Pre),
        )
        val groups = StageFlowDiscovery.group(parts)
        assertEquals(2, groups.size)
        assertEquals(
            listOf("BGM_Stage_Coaster", "BGM_Stage_PeachBeach"),
            groups.map { it.prefix },
        )
    }

    @Test
    fun outrosAreOrderedByNumber() {
        val shuffled = listOf(
            part("S_Outro02.bwav", StageFlow.Role.Outro, 2),
            part("S.bwav", StageFlow.Role.Main),
            part("S_Outro00.bwav", StageFlow.Role.Outro, 0),
            part("S_Outro01.bwav", StageFlow.Role.Outro, 1),
        )
        val group = StageFlowDiscovery.group(shuffled).single()
        val outros = group.parts.filter { it.role == StageFlow.Role.Outro }.map { it.outroNumber }
        assertEquals(listOf(0, 1, 2), outros)
    }

    @Test
    fun aLoneMainFileHasNoStructure() {
        val group = StageFlowDiscovery.group(
            listOf(part("Solo.bwav", StageFlow.Role.Main)),
        ).single()
        assertTrue(!group.hasStructure, "a single main file is not a flow")
        assertEquals(1, group.parts.size)
    }

    // ---------------------------------------------------------------- dominant

    @Test
    fun theRicherGroupWins() {
        val groups = StageFlowDiscovery.group(
            coasterParts() + listOf(part("Other.bwav", StageFlow.Role.Main)),
        )
        val dominant = StageFlowDiscovery.dominant(groups)
        assertNotNull(dominant)
        assertEquals("BGM_Stage_Coaster", dominant!!.prefix)
    }

    @Test
    fun tiesAreBrokenAlphabeticallySoTheChoiceIsStable() {
        val groups = StageFlowDiscovery.group(
            listOf(
                part("Zeta.bwav", StageFlow.Role.Main),
                part("Alpha.bwav", StageFlow.Role.Main),
            ),
        )
        assertEquals("Alpha", StageFlowDiscovery.dominant(groups)!!.prefix)
    }

    @Test
    fun anEmptyGroupListHasNoDominant() {
        assertNull(StageFlowDiscovery.dominant(emptyList()))
    }

    // ---------------------------------------------------------------- flow building

    @Test
    fun aGroupBecomesAProvisionalFlow() {
        val group = StageFlowDiscovery.group(coasterParts()).single()
        val flow = StageFlowDiscovery.flowFor(group).getOrThrow()

        assertEquals("BGM_Stage_Coaster", flow.name)
        assertEquals(6, flow.parts.size)
        assertTrue(flow.isProvisional)
        assertNotNull(flow.outro(0))
        assertNotNull(flow.outro(1))
    }

    @Test
    fun aVerifiedFlagCanBePassedThrough() {
        // Nothing sets this today; the parameter exists so the label is never hardcoded to
        // PROVISIONAL in a way a caller cannot override once evidence exists.
        val group = StageFlowDiscovery.group(coasterParts()).single()
        val flow = StageFlowDiscovery.flowFor(group, provenance = StageFlow.Provenance.VERIFIED)
            .getOrThrow()
        assertTrue(!flow.isProvisional)
    }

    @Test
    fun aGroupWithTwoMainsFails() {
        // Discovery groups by prefix, and an unknown suffix becomes its own prefix, so two mains
        // cannot arise through the normal path. A hand-built group can still hold them -- e.g.
        // once a caller starts assembling groups from a saved list -- so the refusal is checked
        // on the group directly rather than pretended to be reachable from file names.
        val group = StageFlowDiscovery.Group(
            prefix = "BGM_Stage_Coaster",
            parts = listOf(
                part("BGM_Stage_Coaster.bwav", StageFlow.Role.Main),
                part("BGM_Stage_Coaster_A.bwav", StageFlow.Role.Main),
            ),
        )
        val result = StageFlowDiscovery.flowFor(group)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("more than one"))
    }

    @Test
    fun aGroupWithoutAMainFails() {
        val group = StageFlowDiscovery.group(
            listOf(part("S_Pre.bwav", StageFlow.Role.Pre)),
        ).single()
        val result = StageFlowDiscovery.flowFor(group)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Main"))
    }
}
