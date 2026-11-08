package com.mylittleblover.mkwplayer.library

import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Matching decides whether the app tells a user they own a track.
 *
 * That is a claim, so it is only made when a name match is confirmed by measurement. A name-only
 * match would be wrong for a renamed file, and a measurement-only match would be wrong for two
 * entries sharing a name -- both are exercised below.
 */
class CatalogMatcherTest {

    private fun entry(
        name: String,
        frames: Long = 3_373_711,
        rate: Int = 48_000,
        channels: Int = 2,
        playable: Boolean = true,
        category: String = "COURSE",
    ) = CatalogLoader.Entry(
        name = name,
        category = category,
        codecKind = if (playable) 1 else 2,
        sampleRate = rate,
        channels = channels,
        frames = frames,
        loopStart = null,
        loopEnd = null,
        playable = playable,
        reason = if (playable) null else "unsupported codec",
    )

    private fun catalog(vararg entries: CatalogLoader.Entry, partial: Boolean = true) = CatalogLoader.Catalog(
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

    private fun file(
        name: String,
        frames: Long = 3_373_711,
        rate: Int = 48_000,
        channels: Int = 2,
    ) = CatalogMatcher.PresentFile(
        fileName = "$name.bwav",
        frames = frames,
        sampleRate = rate,
        channels = channels,
    )

    // ---------------------------------------------------------------- owned

    @Test
    fun aNameAndMeasurementMatchIsOwned() {
        val report = CatalogMatcher.match(
            catalog(entry("BGM_Stage_Coaster")),
            listOf(file("BGM_Stage_Coaster")),
        )
        assertEquals(1, report.owned.size)
        assertEquals(CatalogMatcher.Status.OWNED, report.matches[0].status)
        assertNotNull(report.matches[0].file)
    }

    @Test
    fun aMissingEntryIsReported() {
        val report = CatalogMatcher.match(
            catalog(entry("BGM_Stage_Coaster")),
            emptyList(),
        )
        assertEquals(1, report.missing.size)
        assertNull(report.matches[0].file)
    }

    // ---------------------------------------------------------------- mismatch

    @Test
    fun aDifferentFrameCountIsAMismatchNotOwnership() {
        // The whole point: a file with the right name but the wrong length is not the track.
        val report = CatalogMatcher.match(
            catalog(entry("BGM_Stage_Coaster")),
            listOf(file("BGM_Stage_Coaster", frames = 999)),
        )
        assertEquals(1, report.mismatched.size)
        assertEquals(CatalogMatcher.Status.MISMATCH, report.matches[0].status)
        assertTrue(report.mismatched[0].detail!!.contains("frames"))
    }

    @Test
    fun aDifferentSampleRateIsAMismatch() {
        val report = CatalogMatcher.match(
            catalog(entry("X")),
            listOf(file("X", rate = 44_100)),
        )
        assertTrue(report.mismatched[0].detail!!.contains("sample rate"))
    }

    @Test
    fun aDifferentChannelCountIsAMismatch() {
        val report = CatalogMatcher.match(
            catalog(entry("X")),
            listOf(file("X", channels = 1)),
        )
        assertTrue(report.mismatched[0].detail!!.contains("channels"))
    }

    @Test
    fun aFileWithNoMeasurementsIsNotCalledAMismatch() {
        // Absence of evidence is not contradiction. A listing that has not measured the file yet
        // must not produce a wall of false mismatches.
        val report = CatalogMatcher.match(
            catalog(entry("X")),
            listOf(file("X", frames = 0, rate = 0, channels = 0)),
        )
        assertEquals(1, report.owned.size)
        assertEquals(0, report.mismatched.size)
    }

    // ---------------------------------------------------------------- duplicates

    @Test
    fun twoEntriesSharingANameDoNotBothClaimTheSameFile() {
        // One asset name really can appear in several archive entries. If both claimed one file,
        // the user would be told they own more than they do.
        val report = CatalogMatcher.match(
            catalog(entry("Shared"), entry("Shared")),
            listOf(file("Shared")),
        )
        assertEquals(1, report.owned.size, "only one entry may be satisfied by one file")
        assertEquals(1, report.missing.size, "the second entry has no file of its own")
    }

    @Test
    fun twoFilesWithTheSameNameSatisfyTwoEntries() {
        val report = CatalogMatcher.match(
            catalog(entry("Shared"), entry("Shared")),
            listOf(file("Shared"), file("Shared")),
        )
        assertEquals(2, report.owned.size)
    }

    // ---------------------------------------------------------------- unmatched

    @Test
    fun aFileWithNoCatalogueEntryIsReportedSeparately() {
        val report = CatalogMatcher.match(
            catalog(entry("Known")),
            listOf(file("Known"), file("Unknown")),
        )
        assertEquals(1, report.unmatched.size)
        assertEquals("Unknown", report.unmatched[0].stem)
    }

    @Test
    fun anUnmatchedFileIsNotCountedAsOwnership() {
        val report = CatalogMatcher.match(catalog(), listOf(file("Unknown")))
        assertEquals(0, report.owned.size)
        assertEquals(1, report.unmatched.size)
    }

    // ---------------------------------------------------------------- summary and partials

    @Test
    fun theSummaryCountsAllThreeStates() {
        val report = CatalogMatcher.match(
            catalog(entry("Owned"), entry("Missing"), entry("Wrong")),
            listOf(file("Owned"), file("Wrong", frames = 1)),
        )
        val summary = report.summary()
        assertTrue(summary.contains("1 of 3 entries present"), summary)
        assertTrue(summary.contains("1 missing"), summary)
        assertTrue(summary.contains("1 mismatched"), summary)
    }

    @Test
    fun thePartialCatalogueFlagSurvivesMatching() {
        // A joined report must not quietly look complete just because every row has a file.
        val report = CatalogMatcher.match(catalog(entry("X"), partial = true), listOf(file("X")))
        assertTrue(report.catalogIsPartial)
    }

    @Test
    fun aFrozenCatalogueIsNotPartial() {
        val report = CatalogMatcher.match(catalog(entry("X"), partial = false), listOf(file("X")))
        assertTrue(!report.catalogIsPartial)
    }

    @Test
    fun playabilitySeparatesOwnedFromListenable() {
        // Owning an unplayable entry is not the same as being able to hear it.
        val report = CatalogMatcher.match(
            catalog(entry("Playable"), entry("Juke", playable = false)),
            listOf(file("Playable"), file("Juke")),
        )
        assertEquals(2, report.owned.size)
        assertEquals(1, report.ownedPlayable.size)
        assertEquals(1, report.ownedUnplayable.size)
    }

    // ---------------------------------------------------------------- logical rows

    @Test
    fun aStageSPartsMergeIntoOneRow() {
        val report = CatalogMatcher.match(
            catalog(
                entry("BGM_Stage_Coaster"),
                entry("BGM_Stage_Coaster_Pre", frames = 2_427_426),
                entry("BGM_Stage_Coaster_Bridge", frames = 88_320),
                entry("BGM_Stage_Coaster_Outro00", frames = 226_286),
            ),
            listOf(
                file("BGM_Stage_Coaster"),
                file("BGM_Stage_Coaster_Pre", frames = 2_427_426),
            ),
        )
        val rows = CatalogMatcher.logicalRows(report)
        assertEquals(1, rows.size, "four parts must be one track")
        assertEquals("BGM_Stage_Coaster", rows[0].prefix)
        assertEquals(4, rows[0].total)
        assertEquals(2, rows[0].ownedCount)
        assertTrue(!rows[0].isComplete)
    }

    @Test
    fun twoStagesProduceTwoRows() {
        val report = CatalogMatcher.match(
            catalog(
                entry("BGM_Stage_Coaster"),
                entry("BGM_Stage_PeachBeach"),
            ),
            listOf(file("BGM_Stage_Coaster")),
        )
        val rows = CatalogMatcher.logicalRows(report)
        assertEquals(2, rows.size)
        assertEquals(
            listOf("BGM_Stage_Coaster", "BGM_Stage_PeachBeach"),
            rows.map { it.prefix },
        )
    }

    @Test
    fun aRowKnowsHowManyPlayablePartsAreMissing() {
        val report = CatalogMatcher.match(
            catalog(
                entry("S"),
                entry("S_Pre"),
                entry("S_Outro00", playable = false),
            ),
            listOf(file("S")),
        )
        val row = CatalogMatcher.logicalRows(report).single()
        // S_Pre is playable and missing; the unplayable outro does not count.
        assertEquals(1, row.missingPlayable)
    }

    @Test
    fun aRowClassifierNamesTheKind() {
        val report = CatalogMatcher.match(
            catalog(entry("BGM_JukeBox_Track01")),
            emptyList(),
        )
        val row = CatalogMatcher.logicalRows(report).single()
        assertEquals(ResourceKind.JUKEBOX, row.kind)
    }

}
