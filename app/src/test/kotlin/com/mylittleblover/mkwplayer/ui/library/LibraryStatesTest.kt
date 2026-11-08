package com.mylittleblover.mkwplayer.ui.library

import com.mylittleblover.mkwplayer.player.CatalogBrowser
import com.mylittleblover.mkwplayer.player.UnlockUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The library page's claims, tested without a device.
 *
 * Most of what can go wrong on this page is not layout. It is a count that includes something the cup
 * gate removed, a search that reaches a course the list hides, or a missing catalogue that gets
 * rendered as "0 of 0 tracks" and therefore reads as a measured empty library. Those are claims about
 * the soundtrack, and a screenshot cannot tell a true one from a false one.
 *
 * Assertion argument order throughout is (expected, actual, message), which is the order this project
 * has got wrong four times.
 */
class LibraryStatesTest {

    // ---------------------------------------------------------------- fixtures

    private fun row(
        name: String,
        kind: String = "COURSE",
        owned: Int = 0,
        total: Int = 1,
        playable: Boolean = false,
    ) = CatalogBrowser.Row(
        name = name,
        kind = kind,
        owned = owned,
        total = total,
        complete = total > 0 && owned == total,
        playable = playable,
    )

    private fun state(
        vararg rows: CatalogBrowser.Row,
        available: Boolean = true,
        coverageNotice: String? = null,
    ) = CatalogBrowser.State(
        rows = rows.toList(),
        available = available,
        coverageNotice = coverageNotice,
        // Deliberately unrelated to anything the header derives, so a test that passes because the
        // browser's own summary was copied cannot pass by accident.
        summary = "browser summary that must not be reused as the header",
    )

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
        hiddenReason = if (visible) null else "Some content is not available yet.",
    )

    private val cupCourses = setOf("Rainbow", "SpecialCourse")

    /** One row of the state matrix, so the table below stays a table instead of six near-identical asserts. */
    private data class StateCase(
        val hasCatalogue: Boolean,
        val hasTracks: Boolean,
        val isPartial: Boolean,
        val busy: Boolean,
        val expected: PageState,
        val why: String,
    )

    // ---------------------------------------------------------------- the header

    @Test
    fun theHeaderCountsTracksThatHaveAtLeastOnePartPresent() {
        val header = headerFor(
            state(
                row("Coaster", owned = 2, total = 3, playable = true),
                row("Beach", owned = 0, total = 1),
                row("Rainbow", owned = 1, total = 1, playable = true),
                // Owned but undecodable: owning an entry is not the same as being able to hear it, so
                // this row must lift `present` without lifting `playable`.
                row("JukeBox", owned = 1, total = 1, playable = false),
            ),
        )

        assertEquals(3, header.presentCount, "three of the four tracks have at least one part present")
        assertEquals(4, header.totalCount, "the total is the catalogue's track count, not its part count")
        assertEquals(2, header.playableCount, "only the rows this build can decode count as playable")
    }

    @Test
    fun aMissingCatalogueDoesNotReadAsAnEmptyLibrary() {
        // The whole point of the header derivation: null and "nothing present" are different facts.
        val header = headerFor(null)

        assertEquals(0, header.presentCount, "nothing can be counted, so the count is zero")
        assertEquals(0, header.totalCount, "no catalogue means no known total")
        assertFalse(
            header.summary.contains("0 of 0"),
            "a zero-of-zero line is exactly how a missing asset gets misread: ${header.summary}",
        )
        val notice = header.coverageNotice
        assertTrue(notice != null, "a missing catalogue must say so out loud")
        assertTrue(
            notice!!.contains("catalogue", ignoreCase = true),
            "the notice has to name the missing thing, not just be absent: $notice",
        )

        // `available = false` is the same fact arriving as a state instead of as null, so it must not
        // produce a different header -- a second wording would be a second thing to get wrong.
        assertEquals(
            header,
            headerFor(state(row("Coaster", owned = 1), available = false)),
            "available = false and null describe the same fact",
        )
    }

    @Test
    fun theCoverageNoticeIsCarriedIntoTheHeaderUnchanged() {
        // Dropping it would let a partial catalogue present its counts as the whole soundtrack.
        val notice = "Partial catalogue, 4 of 499 archives read."
        val header = headerFor(state(row("Coaster"), coverageNotice = notice))

        assertEquals(notice, header.coverageNotice, "the notice must survive the reduction verbatim")
    }

    @Test
    fun aCatalogueThatListsNoTracksSaysSomethingDifferentFromAMissingCatalogue() {
        // Parsed but empty is a third fact. Folding it into the missing-asset wording would hide a
        // broken asset behind the message for an absent one.
        val present = headerFor(state())
        val missing = headerFor(null)

        assertEquals(0, present.totalCount, "an empty catalogue has no tracks")
        assertTrue(
            present.summary != missing.summary,
            "an empty catalogue and an absent one must not share one sentence",
        )
        assertFalse(
            present.summary.contains("0 of 0"),
            "the empty catalogue must not claim a measured zero either: ${present.summary}",
        )
    }

    // ---------------------------------------------------------------- categories

    @Test
    fun aCategoryWithNoTracksIsOmitted() {
        // An empty category is noise: it says nothing about the soundtrack, only about the grouping.
        val rows = state(
            row("Coaster", kind = "COURSE"),
            row("Empty", kind = "ZERO_PARTS", total = 0),
        )

        val names = categories(rows, cupCourses, unlock(false)).map { it.name }
        assertEquals(listOf("COURSE"), names, "a category whose only row is not a track must vanish")
    }

    @Test
    fun categoriesCountLogicalTracksRatherThanParts() {
        val rows = state(
            row("Coaster", kind = "COURSE", owned = 3, total = 5, playable = true),
            // Unplayable because nothing of it is present: the browser only marks a row playable when
            // a present part decodes, so this fixture keeps that invariant instead of contradicting it.
            row("Beach", kind = "COURSE", owned = 0, total = 2, playable = false),
        )

        val category = categories(rows, cupCourses, unlock(false)).single()
        assertEquals(2, category.total, "two logical tracks, not seven parts")
        assertEquals(1, category.present, "one track has at least one part")
        assertEquals(1, category.playable, "playable counts tracks with a decodable part")
    }

    @Test
    fun aCategoryWhoseEveryTrackIsGatedDisappearsEntirely() {
        // Hiding the cup means the category is not there. A row reading 'HIDDEN CUP (0 tracks)' would
        // announce that something was removed, which is the same leak as showing it.
        val rows = state(
            row("Rainbow", kind = "CUP"),
            row("SpecialCourse", kind = "CUP"),
            row("Coaster", kind = "COURSE"),
        )

        val names = categories(rows, cupCourses, unlock(false)).map { it.name }
        assertEquals(listOf("COURSE"), names, "an all-gated category must not survive as an empty row")
    }

    @Test
    fun aPartlyGatedCategoryIsMarkedRatherThanTotalled() {
        // The count after gating is a floor. Presenting it as the category's real size is a quieter
        // version of the same false claim, so the row says a subtraction happened.
        val rows = state(
            row("Coaster", kind = "COURSE"),
            row("Rainbow", kind = "COURSE"),
        )

        val category = categories(rows, cupCourses, unlock(false)).single()
        assertEquals(1, category.total, "the gated track is not counted")
        assertTrue(category.locked, "the row must admit that it is showing less than the category holds")
    }

    @Test
    fun anEarnedCupKeepsItsCoursesAndDropsTheMark() {
        val rows = state(
            row("Coaster", kind = "COURSE"),
            row("Rainbow", kind = "COURSE"),
        )

        val category = categories(rows, cupCourses, unlock(true)).single()
        assertEquals(2, category.total, "nothing is gated once the cup is earned")
        assertFalse(category.locked, "a complete category is not partly hidden")
    }

    @Test
    fun noCatalogueMeansNoCategories() {
        assertEquals(
            emptyList(),
            categories(null, cupCourses, unlock(false)),
            "a null state cannot produce categories; an empty list is the honest answer",
        )
    }

    // ---------------------------------------------------------------- search

    @Test
    fun searchMatchesACaseInsensitiveSubstringOfTheName() {
        val rows = listOf(row("BGM_Stage_Coaster"), row("BGM_Stage_Beach"))

        val result = search(rows, "coast", cupCourses, unlock(false))

        assertEquals(listOf(0), result.rows, "the match is on a substring and ignores case")
        assertEquals("coast", result.query, "the query is echoed exactly as typed")
        assertNull(result.emptyReason, "a successful search needs no explanation")
    }

    @Test
    fun aBlankQueryReturnsEveryRowTheGateAllows() {
        // Pressing search with nothing typed must not reveal more than browsing already did.
        val rows = listOf(row("Coaster"), row("Rainbow"), row("Beach"))

        val result = search(rows, "   ", cupCourses, unlock(false))

        assertEquals(listOf(0, 2), result.rows, "blank returns the visible rows, in the caller's order")
        assertNull(result.emptyReason, "an empty library is a page fact, not a search verdict")
    }

    @Test
    fun aQueryWithNoMatchesSaysWhatWasSearchedFor() {
        val result = search(listOf(row("Coaster")), "volcano", cupCourses, unlock(false))

        assertEquals(emptyList(), result.rows, "nothing matches")
        val reason = result.emptyReason
        assertTrue(reason != null, "a search that finds nothing must say so")
        assertTrue(reason!!.contains("volcano"), "the reason must name what was looked for: $reason")
    }

    @Test
    fun searchDoesNotBypassTheCupGate() {
        // The back door the contract singles out: a query that reaches a hidden course must come back
        // empty. The same query against an earned cup must find the course, which proves the empty
        // result above came from the gate and not from a broken match.
        val rows = listOf(row("Rainbow"), row("Coaster"))

        val locked = search(rows, "rainbow", cupCourses, unlock(false))
        assertEquals(emptyList(), locked.rows, "search must apply the same gate as the list")

        val earned = search(rows, "rainbow", cupCourses, unlock(true))
        assertEquals(listOf(0), earned.rows, "the earned cup is reachable through search as well")
    }

    @Test
    fun aGatedMatchIsNotAnnouncedByTheEmptyReason() {
        // 'No result for Rainbow' would confirm the course exists, which is the leak the gate prevents.
        val result = search(listOf(row("Rainbow")), "rainbow", cupCourses, unlock(false))

        val reason = result.emptyReason
        assertTrue(reason != null, "an all-gated search still needs a reason")
        val text = reason!!.lowercase()
        assertFalse(text.contains("rainbow"), "the reason must not repeat the gated name: $reason")
        assertFalse(text.contains("cup"), "the reason must not name the cup: $reason")
        assertFalse(text.contains("special"), "the reason must not name the cup: $reason")
        assertFalse(text.contains("unlock"), "the reason must not hint at an unlock: $reason")
    }

    // ---------------------------------------------------------------- recently played

    private fun recentEntry(key: String) = RecentEntry(
        key = key,
        title = "Title for $key",
        subtitle = "Subtitle for $key",
    )

    @Test
    fun recentDropsLockedCupEntries() {
        val entries = listOf(recentEntry("Coaster"), recentEntry("Rainbow"), recentEntry("Beach"))

        val shown = recent(entries, cupCourses, unlock(false)).map { it.key }

        assertEquals(listOf("Coaster", "Beach"), shown, "a gated course must not appear in recents")
    }

    @Test
    fun recentKeepsTheCallersOrder() {
        // The only property a recency list has is its order. Sorting it would silently destroy the
        // meaning of the list while still looking plausible.
        val entries = listOf(recentEntry("Zeta"), recentEntry("Alpha"), recentEntry("Mike"))

        val shown = recent(entries, cupCourses, unlock(false)).map { it.key }

        assertEquals(listOf("Zeta", "Alpha", "Mike"), shown, "recent order belongs to the caller")
    }

    @Test
    fun recentIsCappedAtTheLimit() {
        val entries = (1..12).map { recentEntry("Course$it") }

        assertEquals(10, recent(entries, cupCourses, unlock(false)).size, "the default cap is ten")
        assertEquals(3, recent(entries, cupCourses, unlock(false), limit = 3).size, "an explicit cap wins")
    }

    @Test
    fun aNonPositiveLimitYieldsNothingInsteadOfThrowing() {
        // List.take(-1) throws. A settings value of -1 must not crash a presentational page.
        val entries = listOf(recentEntry("Coaster"))

        assertEquals(emptyList(), recent(entries, cupCourses, unlock(false), limit = 0), "zero is empty")
        assertEquals(emptyList(), recent(entries, cupCourses, unlock(false), limit = -1), "negative is empty")
    }

    // ---------------------------------------------------------------- page state

    @Test
    fun aBusyFirstReadIsLoadingRatherThanMissing() {
        // While the first read runs the app has not yet found out whether the asset exists. Announcing
        // 'catalogue unavailable' mid-read is a lie with a visible flash attached to it.
        assertEquals(
            PageState.LOADING,
            stateFor(hasCatalogue = false, hasTracks = false, isPartial = false, busy = true),
            "busy with no catalogue is a read in progress",
        )
        assertEquals(
            PageState.MISSING_ASSET,
            stateFor(hasCatalogue = false, hasTracks = false, isPartial = false, busy = false),
            "once the read finishes, no catalogue is a missing asset",
        )
    }

    @Test
    fun eachRemainingCombinationMapsToItsOwnState() {
        val cases = listOf(
            StateCase(true, false, false, false, PageState.EMPTY, "a catalogue with nothing present"),
            StateCase(true, false, true, false, PageState.EMPTY, "partial or not, nothing present is empty"),
            StateCase(true, true, true, false, PageState.UNVERIFIED, "a partial catalogue is not ready"),
            StateCase(true, true, false, false, PageState.READY, "a complete catalogue with tracks"),
            StateCase(true, true, false, true, PageState.READY, "a refresh must not blank a usable library"),
            StateCase(true, true, true, true, PageState.UNVERIFIED, "a refresh keeps the existing state"),
        )

        for (sample in cases) {
            assertEquals(
                sample.expected,
                stateFor(sample.hasCatalogue, sample.hasTracks, sample.isPartial, sample.busy),
                sample.why,
            )
        }
    }

    @Test
    fun onlyTheStatesWithRealRowsAreAllowedToDrawThem() {
        // Guards the two opposite mistakes at once: drawing placeholder rows for a failure, and
        // hiding real rows on a partial catalogue.
        val drawable = PageState.entries.filter { it.canShowRows }
        assertEquals(
            listOf(PageState.READY, PageState.UNVERIFIED, PageState.RESTORED),
            drawable,
            "exactly the states that describe a catalogue the caller holds may draw rows",
        )

        // MISSING_ASSET and ERROR are the ones most likely to be drawn as an empty list by mistake.
        assertFalse(PageState.MISSING_ASSET.canShowRows, "a missing asset has no rows to draw")
        assertFalse(PageState.ERROR.canShowRows, "a failed read has no rows to draw")
        assertFalse(PageState.LOADING.canShowRows, "nothing is known yet while loading")
    }

    @Test
    fun statesThatNeverMeasuredAnythingDoNotPrintACount() {
        // LOADING is the interesting one: printing '0 of 650' while the first read runs is a
        // measurement that has not happened yet.
        assertFalse(PageState.LOADING.canCountTracks, "nothing has been counted during the first read")
        assertFalse(PageState.MISSING_ASSET.canCountTracks, "a missing catalogue measured no zero")
        assertFalse(PageState.ERROR.canCountTracks, "a failed read measured no zero")
        assertFalse(PageState.REBIND_REQUIRED.canCountTracks, "a lost permission measured no zero")
        assertFalse(
            PageState.OFFLINE_OR_NO_PERMISSION.canCountTracks,
            "unreadable storage measured no zero",
        )

        // EMPTY is the exception that shows why the flag is separate: it has a real total and a real
        // present count of zero.
        assertTrue(PageState.EMPTY.canCountTracks, "an empty library has a genuine zero to report")
        assertTrue(PageState.UNVERIFIED.canCountTracks, "a partial catalogue still measured something")
    }

    @Test
    fun everyStateHasANonBlankLabel() {
        for (state in PageState.entries) {
            assertTrue(state.label.isNotBlank(), "$state needs a word a person can read")
        }
    }
}
