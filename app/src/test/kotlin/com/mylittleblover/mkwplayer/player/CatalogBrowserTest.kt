package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.CatalogLoader
import com.mylittleblover.mkwplayer.library.CatalogMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The browser reduces a 650-entry report to rows, and two of its outputs are claims rather than
 * layout: whether a row is complete, and whether the catalogue calls itself partial.
 */
class CatalogBrowserTest {

    private fun entry(
        name: String,
        frames: Long = 1000,
        playable: Boolean = true,
        category: String = "COURSE",
    ) = CatalogLoader.Entry(
        name = name,
        category = category,
        codecKind = if (playable) 1 else 2,
        sampleRate = 48_000,
        channels = 2,
        frames = frames,
        loopStart = null,
        loopEnd = null,
        playable = playable,
        reason = if (playable) null else "unsupported codec",
    )

    private fun catalog(vararg entries: CatalogLoader.Entry, partial: Boolean = true) =
        CatalogLoader.Catalog(
            entries = entries.toList(),
            playableCount = entries.count { it.playable },
            categoryCounts = mapOf("COURSE" to entries.size),
            playableByCategory = mapOf("COURSE" to entries.count { it.playable }),
            freezeStatus = if (partial) "PROVISIONAL_INCOMPLETE" else "FROZEN",
            coverage = if (partial) "INCOMPLETE" else "COMPLETE",
            coverageReasons = if (partial) listOf("only 4/499 archives read") else emptyList(),
            archivesRead = 4,
            archivesExpected = 499,
        )

    private fun file(name: String, frames: Long = 1000) = CatalogMatcher.PresentFile(
        fileName = "$name.bwav",
        frames = frames,
        sampleRate = 48_000,
        channels = 2,
    )

    @Test
    fun noCatalogIsReportedAsUnavailableNotAsEmpty() {
        val browser = CatalogBrowser(null)
        val state = browser.state.value
        assertTrue(!state.available)
        assertEquals(0, state.rows.size)
        assertNotNull(state.summary)
    }

    @Test
    fun aCatalogWithNoFilesHasEveryRowEmpty() {
        val browser = CatalogBrowser(catalog(entry("A"), entry("B")))
        val state = browser.state.value
        assertTrue(state.available)
        assertTrue(state.rows.all { it.owned == 0 && !it.complete })
    }

    @Test
    fun owningEveryPartMarksTheRowComplete() {
        val browser = CatalogBrowser(catalog(entry("S"), entry("S_Pre")))
        browser.refresh(listOf(file("S"), file("S_Pre")))

        val row = browser.state.value.rows.single()
        assertEquals(2, row.owned)
        assertEquals(2, row.total)
        assertTrue(row.complete)
    }

    @Test
    fun owningSomePartsLeavesTheRowIncomplete() {
        val browser = CatalogBrowser(catalog(entry("S"), entry("S_Pre"), entry("S_Bridge")))
        browser.refresh(listOf(file("S")))

        val row = browser.state.value.rows.single()
        assertEquals(1, row.owned)
        assertEquals(3, row.total)
        assertTrue(!row.complete)
    }

    @Test
    fun thePartialNoticeSurvivesTheReduction() {
        // The single most important field: dropping it would let the screen present the catalogue
        // as complete.
        val browser = CatalogBrowser(catalog(entry("A"), partial = true))
        val notice = browser.state.value.coverageNotice
        assertNotNull(notice)
        assertTrue(notice!!.contains("Partial"), notice)
        assertTrue(notice.contains("4 of 499"), notice)
    }

    @Test
    fun aFrozenCatalogHasNoNotice() {
        val browser = CatalogBrowser(catalog(entry("A"), partial = false))
        assertNull(browser.state.value.coverageNotice)
    }

    @Test
    fun theSummaryCountsPresentEntries() {
        val browser = CatalogBrowser(catalog(entry("A"), entry("B"), entry("C")))
        browser.refresh(listOf(file("A"), file("C")))
        assertTrue(browser.state.value.summary.contains("2 of 3"), browser.state.value.summary)
    }

    @Test
    fun aPartRowIsPlayableOnlyWhenAPresentPartDecodes() {
        // Owning a JukeBox entry is not the same as being able to hear it.
        val browser = CatalogBrowser(catalog(entry("J", playable = false)))
        browser.refresh(listOf(file("J")))

        val row = browser.state.value.rows.single()
        assertTrue(row.complete, "the part is present")
        assertTrue(!row.playable, "but this build cannot decode it")
    }

    @Test
    fun aWrongLengthFileDoesNotCountAsOwned() {
        val browser = CatalogBrowser(catalog(entry("A", frames = 1000)))
        browser.refresh(listOf(file("A", frames = 999)))

        val row = browser.state.value.rows.single()
        assertEquals(0, row.owned)
        assertTrue(!row.complete)
    }

    @Test
    fun rowsAreOrderedByPrefix() {
        val browser = CatalogBrowser(catalog(entry("Zeta"), entry("Alpha")))
        assertEquals(listOf("Alpha", "Zeta"), browser.state.value.rows.map { it.name })
    }

    @Test
    fun refreshingWithFewerFilesReducesOwnership() {
        // The browser is rebuilt from files each time, so a removed file must stop counting.
        val browser = CatalogBrowser(catalog(entry("A")))
        browser.refresh(listOf(file("A")))
        assertEquals(1, browser.state.value.rows.single().owned)

        browser.refresh(emptyList())
        assertEquals(0, browser.state.value.rows.single().owned)
    }

    @Test
    fun theKindComesFromTheName() {
        val browser = CatalogBrowser(catalog(entry("BGM_JukeBox_T01")))
        assertEquals("JUKEBOX", browser.state.value.rows.single().kind)
    }
}
