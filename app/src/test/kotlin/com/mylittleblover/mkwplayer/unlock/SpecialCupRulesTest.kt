package com.mylittleblover.mkwplayer.unlock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which stages the cup requires is a choice this project made, not a fact it found.
 *
 * These tests pin the choice and its label. The label matters more than the rule: if the rule ever
 * changes, the screen must still say it is provisional, because no evidence establishes the game's
 * own requirement.
 */
class SpecialCupRulesTest {

    @Test
    fun everyPickedStageIsEligible() {
        val stages = SpecialCupRules.eligibleStages(
            listOf(
                "BGM_Stage_Coaster.bwav",
                "BGM_Stage_PeachBeach.bwav",
            ),
        )
        assertEquals(setOf("BGM_Stage_Coaster", "BGM_Stage_PeachBeach"), stages)
    }

    @Test
    fun aStagesPartsDoNotBecomeSeparateStages() {
        // Pre, Bridge and Outro are one listen, not four requirements.
        val stages = SpecialCupRules.eligibleStages(
            listOf(
                "BGM_Stage_Coaster.bwav",
                "BGM_Stage_Coaster_Pre.bwav",
                "BGM_Stage_Coaster_Bridge.bwav",
                "BGM_Stage_Coaster_Outro00.bwav",
            ),
        )
        assertEquals(setOf("BGM_Stage_Coaster"), stages)
    }

    @Test
    fun blankNamesAreIgnored() {
        assertEquals(setOf("A"), SpecialCupRules.eligibleStages(listOf("A.bwav", "", "  ")))
    }

    @Test
    fun anEmptyLibraryMeansNothingToDoRatherThanBlocking() {
        // A user with no files has not failed anything; the cup simply has no requirement yet.
        assertTrue(SpecialCupRules.eligibleStages(emptyList()).isEmpty())
    }

    @Test
    fun theRuleIsLabelledProvisional() {
        assertEquals(
            SpecialCupRules.RuleProvenance.PROVISIONAL,
            SpecialCupRules.provenance,
            "the eligibility rule is not established, so it must say so",
        )
    }

    @Test
    fun theDescriptionNamesTheRuleAndItsUncertainty() {
        val text = SpecialCupRules.describe(3)
        assertTrue(text.contains("Provisional"), text)
        assertTrue(text.contains("3 stage"), text)
        assertTrue(text.contains("not established"), text)
    }

    @Test
    fun theRuleIsEveryStageTheUserHas() {
        // A defensible rule: it cannot be satisfied with a small library, and it invents no list of
        // stage names.
        assertEquals("every stage you have", SpecialCupRules.RULE)
    }
}
