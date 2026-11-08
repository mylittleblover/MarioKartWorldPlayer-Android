package com.mylittleblover.mkwplayer.ui

import com.mylittleblover.mkwplayer.library.CueCatalog
import com.mylittleblover.mkwplayer.player.UnlockUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cup's visibility is a permission boundary, not a presentation choice.
 *
 * The contract says every entry point must use the same gate -- search, home, library, map pins,
 * favourites, recently played, deep links, route candidates -- because one back door leaks the cup and
 * its courses. These tests exist so a new screen cannot quietly open one.
 */
class CupVisibilityTest {

    private fun unlock(visible: Boolean) = UnlockUi(
        progress = "0 of 3 stages heard",
        remaining = "3 still to hear",
        ruleNotice = "Provisional rule",
        offered = visible,
        ceremonyPlaying = false,
        ceremonyCompleted = false,
        congratulationOwed = false,
        canEnter = visible,
        visible = visible,
        hiddenReason = if (visible) null else "Not yet",
    )

    private val cupCourses = setOf("Rainbow", "SpecialCup1")

    // ---------------------------------------------------------------- the gate

    @Test
    fun anAbsentCupStateRevealsNothing() {
        // No library, no catalogue, no progress: the safe default is hidden.
        assertFalse(CupVisibility.mayReveal(null))
    }

    @Test
    fun anUnearnedCupRevealsNothing() {
        assertFalse(CupVisibility.mayReveal(unlock(visible = false)))
    }

    @Test
    fun anEarnedCupReveals() {
        assertTrue(CupVisibility.mayReveal(unlock(visible = true)))
    }

    // ---------------------------------------------------------------- list filtering

    @Test
    fun lockedCupCoursesAreRemovedFromAList() {
        val shown = CupVisibility.visibleCourses(
            courses = listOf("Coaster", "Rainbow", "PeachBeach"),
            specialCupCourses = cupCourses,
            unlock = unlock(visible = false),
        )
        assertEquals(listOf("Coaster", "PeachBeach"), shown)
    }

    @Test
    fun unlockedCupCoursesStayInTheList() {
        val all = listOf("Coaster", "Rainbow", "PeachBeach")
        assertEquals(all, CupVisibility.visibleCourses(all, cupCourses, unlock(visible = true)))
    }

    @Test
    fun filteringAnEmptyListIsFine() {
        assertEquals(
            emptyList(),
            CupVisibility.visibleCourses(emptyList(), cupCourses, unlock(visible = false)),
        )
    }

    @Test
    fun aCourseNotInTheCupIsNeverHidden() {
        // The set is explicit, so an ordinary course cannot be hidden by accident.
        assertFalse(CupVisibility.hides("Coaster", cupCourses, unlock(visible = false)))
    }

    // ---------------------------------------------------------------- single-item checks

    @Test
    fun aCupCourseIsHiddenWhileLocked() {
        assertTrue(CupVisibility.hides("Rainbow", cupCourses, unlock(visible = false)))
    }

    @Test
    fun aCupCourseIsShownOnceUnlocked() {
        assertFalse(CupVisibility.hides("Rainbow", cupCourses, unlock(visible = true)))
    }

    @Test
    fun hidingAndVisibilityAgree() {
        // Two entry points that could disagree are two chances to leak.
        for (visible in listOf(true, false)) {
            val state = unlock(visible)
            for (code in cupCourses + "Coaster") {
                assertEquals(
                    !CupVisibility.mayReveal(state) && code in cupCourses,
                    CupVisibility.hides(code, cupCourses, state),
                    "hides() disagrees with mayReveal() for $code",
                )
            }
        }
    }

    // ---------------------------------------------------------------- the reason

    @Test
    fun theHiddenReasonNeverNamesTheCup() {
        // Saying 'there is a hidden cup' is the same leak as showing its card.
        val reason = CupVisibility.hiddenReason(unlock(visible = false))!!
        assertFalse(reason.contains("cup", ignoreCase = true), reason)
        assertFalse(reason.contains("special", ignoreCase = true), reason)
        assertFalse(reason.contains("unlock", ignoreCase = true), reason)
    }

    @Test
    fun aVisibleCupHasNoHiddenReason() {
        assertNull(CupVisibility.hiddenReason(unlock(visible = true)))
    }

    // ---------------------------------------------------------------- playback

    @Test
    fun aLockedCupCourseCannotBePlayed() {
        // A saved route could reference one; refusing is honest, substituting would not be.
        assertFalse(CupVisibility.mayPlay("Rainbow", cupCourses, unlock(visible = false)))
    }

    @Test
    fun anOrdinaryCourseIsPlayableWhileTheCupIsLocked() {
        assertTrue(CupVisibility.mayPlay("Coaster", cupCourses, unlock(visible = false)))
    }

    @Test
    fun aCupCourseIsPlayableOnceUnlocked() {
        assertTrue(CupVisibility.mayPlay("Rainbow", cupCourses, unlock(visible = true)))
    }

    // ---------------------------------------------------------------- course detail

    private fun catalog(): CueCatalog.Catalog = CueCatalog.Catalog(
        provenance = CueCatalog.Provenance.L1_MARKER_ONLY,
        provenanceNote = "Cue positions are AMTA marker records (L1 metadata).",
        courses = listOf(
            CueCatalog.Course(
                code = "Coaster",
                displayName = "Coaster",
                cues = listOf(
                    CueCatalog.Cue("Full_ToOutro00", 1, 1000, 48_000),
                    CueCatalog.Cue("Full_ToOutro01", 2, 5000, 48_000),
                ),
                outroCount = 2,
                hasBridge = true,
                hasShortPre = true,
                hasFast = true,
            ),
        ),
        unmappedCourses = emptyList(),
    )

    @Test
    fun aKnownCourseBuildsItsDetail() {
        val detail = assertNotNull(CourseDetail.of("Coaster", catalog()))
        assertEquals("Coaster", detail.displayName)
        assertEquals(2, detail.cues.size)
        assertFalse(detail.cuesMissing)
    }

    @Test
    fun aCourseMissingFromTheCueAssetIsStillShown() {
        // Its positions are missing, not the course; returning null would make it look nonexistent.
        val detail = assertNotNull(CourseDetail.of("Coaster", catalog = null, displayName = "Coaster"))
        assertTrue(detail.cuesMissing)
        assertFalse(detail.hasCues)
    }

    @Test
    fun anUnknownCourseWithNoNameIsNull() {
        assertNull(CourseDetail.of("Nothing", catalog()))
    }

    @Test
    fun aBlankCodeIsRefused() {
        assertNull(CourseDetail.of("", catalog()))
    }

    @Test
    fun theCaveatTravelsWithTheCueList() {
        val detail = CourseDetail.of("Coaster", catalog())!!
        assertTrue(detail.cueCaveat.contains("marker"), detail.cueCaveat)
    }

    @Test
    fun aMissingCueAssetHasItsOwnCaveat() {
        // 'not available in this build' is a different statement from 'the game has none'.
        val detail = CourseDetail.of("Coaster", catalog = null, displayName = "Coaster")!!
        assertTrue(detail.cueCaveat.contains("not available"), detail.cueCaveat)
    }

    // ---------------------------------------------------------------- the summary line

    @Test
    fun theSummaryStatesTheCountAndTheArchiveFact() {
        val detail = CourseDetail.of("Coaster", catalog())!!
        val summary = cueSummary(detail)!!
        assertTrue(summary.contains("2 cue positions"), summary)
        assertTrue(summary.contains("2 outros"), summary)
    }

    @Test
    fun theSummaryIsPluralisedCorrectly() {
        val one = CourseDetail(
            code = "C",
            displayName = "C",
            outroCount = 1,
            hasBridge = false,
            cues = listOf(CueCatalog.Cue("a", 1, 0, 48_000)),
            cueCaveat = "",
            cuesMissing = false,
        )
        val summary = cueSummary(one)!!
        assertTrue(summary.contains("1 cue position "), summary)
        assertFalse(summary.contains("positions"), summary)
    }

    @Test
    fun aMissingAssetSaysSoRatherThanShowingZero() {
        // '0 cues' would read as a fact about the game rather than about the build.
        val detail = CourseDetail.of("Coaster", catalog = null, displayName = "Coaster")!!
        val summary = cueSummary(detail)!!
        assertTrue(summary.contains("unavailable"), summary)
        assertFalse(summary.contains("0 cue"), summary)
    }

    @Test
    fun noCuesMeansNoLine() {
        val detail = CourseDetail(
            code = "C",
            displayName = "C",
            outroCount = 0,
            hasBridge = false,
            cues = emptyList(),
            cueCaveat = "",
            cuesMissing = false,
        )
        assertNull(cueSummary(detail))
    }
}
