package com.mylittleblover.mkwplayer.unlock

/**
 * The screen's view of Special Cup progress.
 *
 * Kept separate from the rules so the wording lives in one place and can be tested. Every line here
 * has to carry one of two uncertainties, and getting either wrong is a lie the user cannot check:
 *
 * - The eligibility rule is this app's choice, because the game's own requirement is not established.
 * - The ceremony's audio is whichever confirmed cue the caller supplies, because which file the game
 *   uses is unverified in this project.
 */
internal object UnlockPresentation {

    /** One-line progress, e.g. `2 of 5 stages heard`. */
    fun progressLabel(unlock: SpecialCupUnlock): String {
        val total = eligible(unlock)
        val done = total - unlock.remainingStages().size
        return "$done of $total stage${plural(total)} heard"
    }

    /** What is still outstanding, or null when nothing is. */
    fun remainingLabel(unlock: SpecialCupUnlock): String? {
        val remaining = unlock.remainingStages().sorted()
        if (remaining.isEmpty()) return null
        return "Still to hear: " + remaining.joinToString(", ")
    }

    /**
     * The offer shown once the cup is earned.
     *
     * Says the cup is hidden until now, because that is the feature: a user who is never told it was
     * hidden experiences it as an ordinary cup that appeared.
     */
    fun offerLabel(): String = "A hidden cup has appeared"

    /** The line under the offer, explaining what entering does. */
    fun offerDetail(): String =
        "This is the only place it can be entered. Leave at any time; the cup stays open."

    /** The provisional notice that must accompany the requirement. */
    fun ruleNotice(unlock: SpecialCupUnlock): String =
        SpecialCupRules.describe(eligible(unlock))

    /** The ceremony's own caveat, which the caller must show rather than hide. */
    const val CEREMONY_AUDIO_CAVEAT: String =
        "Playing this app's confirmed cue. Which audio the game itself uses is not established."

    /** Nothing to configure. */
    fun emptyLabel(): String = "No stages to hear yet. Add some music first."

    private fun eligible(unlock: SpecialCupUnlock): Int =
        unlock.remainingStages().size + unlock.snapshot.completedStages.size

    private fun plural(count: Int): String = if (count == 1) "" else "s"
}
