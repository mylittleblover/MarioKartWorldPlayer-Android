package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the playability rule against the measured shape of the real library.
 *
 * The point is the mixed categories. SEQUENCE and DEMO each contain both kind 1
 * and kind 2 entries, so any rule that decides playability per category is wrong
 * for them: it would either hide 33 playable sequences or offer 3 unplayable
 * demos.
 */
class PlayabilityAgainstMeasuredLibraryTest {

    @Test
    fun totalCountsMatchTheMeasuredIndex() {
        assertEquals(650, MeasuredLibrary.totalEntries)
        assertEquals(397, MeasuredLibrary.totalKindOne)
        assertEquals(253, MeasuredLibrary.totalKindTwo)
    }

    @Test
    fun kindOneMeansPlayableAndKindTwoDoesNot() {
        for ((category, mix) in MeasuredLibrary.mix) {
            if (mix.kindOne > 0) {
                val result = Playability.evaluate(Playability.SUPPORTED_BWAV_CODEC_KIND)
                assertTrue(result.playable, "$category has kind 1 entries that must play")
            }
            if (mix.kindTwo > 0) {
                val result = Playability.evaluate(2)
                assertTrue(!result.playable, "$category has kind 2 entries that must not play")
            }
        }
    }

    @Test
    fun courseThemesAreAllPlayable() {
        val course = MeasuredLibrary.mix.getValue(TrackCategory.COURSE)
        assertEquals(338, course.total)
        assertEquals(338, course.kindOne)
        assertEquals(0, course.kindTwo)
        assertEquals(1.0, course.playableShare)
    }

    @Test
    fun jukeboxIsFullyBlocked() {
        val jukebox = MeasuredLibrary.mix.getValue(TrackCategory.JUKEBOX)
        assertEquals(227, jukebox.total)
        assertEquals(0, jukebox.kindOne)
        assertEquals(227, jukebox.kindTwo)
        assertEquals(0.0, jukebox.playableShare)
    }

    @Test
    fun mixedCategoriesAreExactlySequenceAndDemo() {
        // Measured: SEQUENCE has 33 kind 1 + 1 kind 2, DEMO has 1 + 3.
        assertEquals(
            setOf(TrackCategory.SEQUENCE, TrackCategory.DEMO),
            MeasuredLibrary.mixedCategories.toSet(),
        )
    }

    @Test
    fun perCategoryRulesWouldBeWrongForMixedCategories() {
        // Demonstrate the mistake this test file exists to prevent: if the UI
        // greyed out a whole mixed category, these counts would be the cost.
        val sequence = MeasuredLibrary.mix.getValue(TrackCategory.SEQUENCE)
        val demo = MeasuredLibrary.mix.getValue(TrackCategory.DEMO)

        assertTrue(
            sequence.kindOne > 0,
            "SEQUENCE has playable entries, so blocking the whole category would hide them",
        )
        assertTrue(
            demo.kindTwo > 0,
            "DEMO has blocked entries, so allowing the whole category would offer silence",
        )
    }

    @Test
    fun blockedShareIsMeaningfulButNotDominant() {
        val blocked = MeasuredLibrary.totalKindTwo
        val total = MeasuredLibrary.totalEntries
        val share = blocked.toDouble() / total
        assertTrue(share > 0.0 && share < 0.5, "blocked share is ${share.coerceIn(0.0, 1.0)}")
        assertEquals(253.0 / 650.0, share, 1e-9)
    }

    @Test
    fun everyMeasuredCategoryMapsBackFromAnIndexName() {
        // Guards against a rename in either place without the other following.
        for (category in MeasuredLibrary.mix.keys) {
            if (category == TrackCategory.OTHER) continue
            val roundTrip = TrackCategory.fromIndexName(category.name)
            assertEquals(category, roundTrip, "category ${category.name} does not round trip")
        }
    }
}
