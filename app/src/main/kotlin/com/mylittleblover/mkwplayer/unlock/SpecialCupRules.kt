package com.mylittleblover.mkwplayer.unlock

import com.mylittleblover.mkwplayer.library.StageFlowDiscovery

/**
 * Decides which stages count toward the Special Cup.
 *
 * This is a deliberate, documented choice, not a discovered fact. No L3 evidence establishes which
 * tracks the game requires for its hidden cup, so this app uses a rule it can defend: every stage the
 * user has music for must be listened to. That is stricter than "any N tracks", cannot be gamed by
 * having a small library, and does not invent a list of stage names.
 *
 * The rule is labelled PROVISIONAL wherever it is shown, for the same reason the section timings are:
 * presenting a guess as the game's rule would be the worse failure.
 */
internal object SpecialCupRules {

    /** Provenance marker for the eligibility rule. */
    enum class RuleProvenance { PROVISIONAL }

    /**
     * The eligibility rule in force.
     *
     * A single constant rather than a function so callers cannot quietly substitute a different rule
     * and still present it as the same feature.
     */
    const val RULE = "every stage you have"

    val provenance: RuleProvenance get() = RuleProvenance.PROVISIONAL

    /**
     * Eligible stages, from the file names the user has.
     *
     * Takes names rather than tracks so it accepts both the database and a folder scan; both end up
     * as `BGM_Stage_*` prefixes via the shared discovery rule.
     */
    fun eligibleStages(fileNames: Collection<String>): Set<String> =
        fileNames
            .filterNot { it.isBlank() }
            .map { StageFlowDiscovery.prefixOf(it) }
            .filter { it.isNotEmpty() }
            .toSet()

    /** One line for the screen, so the rule is visible where the progress is shown. */
    fun describe(eligibleCount: Int): String =
        "Provisional rule ($RULE): $eligibleCount stage(s) to listen to. " +
            "The game's own requirement is not established."
}
