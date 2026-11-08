package com.mylittleblover.mkwplayer.unlock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The gate exists because of one specific trap: in Kotlin `all(emptyList())` is `true`, so a naive
 * "is everything heard" check opens the cup on a fresh install.
 *
 * Every clause is tested on its own, because each one is a different way the cup could appear for no
 * reason.
 */
class CupEligibilityTest {

    private fun conditions(
        frozen: Boolean = true,
        stages: Set<String> = setOf("A"),
        verified: Set<String> = setOf("A"),
        completed: Set<String> = setOf("A"),
    ) = CupEligibility.Conditions(
        isManifestFrozen = frozen,
        eligibleStages = stages,
        verifiedStages = verified,
        completedStages = completed,
    )

    // ---------------------------------------------------------------- the empty-set trap

    @Test
    fun anEmptyLibraryDoesNotOpenTheCup() {
        // The bug this class exists to prevent: all(emptyList()) is true.
        assertFalse(
            CupEligibility.isEligible(conditions(stages = emptySet(), verified = emptySet(), completed = emptySet())),
            "an empty set must never satisfy 'everything is heard'",
        )
    }

    @Test
    fun anEmptyLibraryReportsThatThereIsNothingToHear() {
        assertEquals(
            CupEligibility.Blocker.NO_ELIGIBLE_TRACKS,
            CupEligibility.blocker(conditions(stages = emptySet(), verified = emptySet(), completed = emptySet())),
        )
    }

    // ---------------------------------------------------------------- each clause

    @Test
    fun anUnfrozenManifestBlocksTheCup() {
        // A user who heard every track so far has not heard every track.
        assertEquals(
            CupEligibility.Blocker.MANIFEST_NOT_FROZEN,
            CupEligibility.blocker(conditions(frozen = false)),
        )
    }

    @Test
    fun aFrozenManifestWithEverythingHeardOpensTheCup() {
        assertNull(CupEligibility.blocker(conditions()))
    }

    @Test
    fun anOutstandingStageBlocksTheCup() {
        // Both stages are playable, so the only thing missing is listening.
        assertEquals(
            CupEligibility.Blocker.INCOMPLETE,
            CupEligibility.blocker(
                conditions(
                    stages = setOf("A", "B"),
                    verified = setOf("A", "B"),
                    completed = setOf("A"),
                ),
            ),
        )
    }

    @Test
    fun anUnplayableStageIsReportedBeforeAnOutstandingOne() {
        // When a required track cannot be played at all, saying "keep listening" would be advice the
        // user cannot follow. The unplayable fact is the one worth showing.
        assertEquals(
            CupEligibility.Blocker.UNVERIFIED_TRACKS,
            CupEligibility.blocker(
                conditions(
                    stages = setOf("A", "B"),
                    verified = setOf("A"),
                    completed = emptySet(),
                ),
            ),
        )
    }

    @Test
    fun anUnverifiedStageBlocksTheCupAndSaysSo() {
        // It can never be completed, so the alternative is a permanently unearnable cup.
        assertEquals(
            CupEligibility.Blocker.UNVERIFIED_TRACKS,
            CupEligibility.blocker(
                conditions(stages = setOf("A", "B"), verified = setOf("A"), completed = setOf("A")),
            ),
        )
    }

    @Test
    fun blockOrderPutsTheManifestFirst() {
        // The manifest being unfinished is a different statement from 'you have not listened enough',
        // and showing the second when the first is true makes the requirement look arbitrary.
        val blocked = conditions(
            frozen = false,
            stages = setOf("A", "B"),
            verified = setOf("A"),
            completed = emptySet(),
        )
        assertEquals(CupEligibility.Blocker.MANIFEST_NOT_FROZEN, CupEligibility.blocker(blocked))
    }

    // ---------------------------------------------------------------- normal completion

    @Test
    fun completingEveryVerifiedStageOpensTheCup() {
        val done = conditions(
            stages = setOf("A", "B"),
            verified = setOf("A", "B"),
            completed = setOf("A", "B"),
        )
        assertTrue(CupEligibility.isEligible(done))
        assertNull(CupEligibility.blocker(done))
    }

    @Test
    fun listeningToMoreThanRequiredIsFine() {
        val extra = conditions(
            stages = setOf("A"),
            verified = setOf("A", "B"),
            completed = setOf("A", "B", "C"),
        )
        assertTrue(CupEligibility.isEligible(extra))
    }

    // ---------------------------------------------------------------- wording

    @Test
    fun everyBlockerHasAnExplanation() {
        for (blocker in CupEligibility.Blocker.entries) {
            val text = CupEligibility.explain(blocker)
            assertTrue(text.isNotBlank(), "$blocker has no explanation")
            assertTrue(text.endsWith("."), "$blocker should read as a sentence: $text")
        }
    }

    @Test
    fun noExplanationClaimsTheCupIsUnlocked() {
        // A blocked gate must never read as good news.
        for (blocker in CupEligibility.Blocker.entries) {
            val text = CupEligibility.explain(blocker).lowercase()
            assertFalse(text.contains("unlock"), "$blocker wrongly mentions unlocking: $text")
        }
    }
}
