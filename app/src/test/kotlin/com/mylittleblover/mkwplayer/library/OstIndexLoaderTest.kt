package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The loader decides what the UI shows, so it must not quietly fix up a broken
 * index. These tests feed it the shapes it will actually see, plus the ones that
 * should be refused.
 */
class OstIndexLoaderTest {

    private fun index(
        items: String,
        entryCount: Int = -1,
        coverage: String = "INCOMPLETE",
        freezeStatus: String = "PROVISIONAL_INCOMPLETE",
        archivesRead: String = "[\"a.bars.zs\"]",
        archivesExpected: Int = 499,
        reasons: String = "[\"only 1/499 archives read\"]",
    ): String = """
        {
          "schemaVersion": "provisional-v1",
          "freezeStatus": "$freezeStatus",
          "coverage": "$coverage",
          "archivesRead": $archivesRead,
          "archivesExpected": $archivesExpected,
          "entryCount": ${if (entryCount >= 0) entryCount else 0},
          "counts": {},
          "reasons": $reasons,
          "items": [ $items ]
        }
    """.trimIndent()

    private fun item(
        key: String,
        name: String,
        category: String,
        sha: String = "a".repeat(64),
    ): String = """
        {"entryKey":"$key","internalName":"$name","class":"$category",
         "assetPath":"C:/x/$name.wav","assetSha256":"$sha","verified":true}
    """.trimIndent()

    @Test
    fun loadsAPlayableEntry() {
        val json = index(item("bars:0@1", "Theme", "COURSE"), entryCount = 1)
        val loaded = OstIndexLoader.load(json) { if (it == "a".repeat(64)) 1 else null }

        assertEquals(1, loaded.entries.size)
        assertTrue(loaded.entries[0].playable)
        assertEquals(TrackCategory.COURSE, loaded.entries[0].category)
        assertEquals("Theme", loaded.entries[0].displayName)
    }

    @Test
    fun kindTwoEntryLoadsAsBlockedRatherThanDropped() {
        val json = index(item("bars:0@2", "Juke", "JUKEBOX"), entryCount = 1)
        val loaded = OstIndexLoader.load(json) { 2 }

        assertEquals(1, loaded.entries.size, "a blocked entry must still be listed")
        assertFalse(loaded.entries[0].playable)
        assertEquals(Playability.REASON_UNSUPPORTED_CODEC, loaded.entries[0].blockedReason)
    }

    @Test
    fun entryWithNoResolvableDigestIsBlockedNotAssumedPlayable() {
        val json = index(item("bars:0@3", "Unknown", "DEMO"), entryCount = 1)
        val loaded = OstIndexLoader.load(json) { null }

        assertEquals(1, loaded.entries.size)
        assertFalse(loaded.entries[0].playable)
        assertNotNull(loaded.entries[0].blockedReason)
    }

    @Test
    fun entryWithEmptyDigestIsBlocked() {
        val json = index(item("bars:0@4", "NoDigest", "COURSE", sha = ""), entryCount = 1)
        val loaded = OstIndexLoader.load(json) { 1 }

        assertEquals(1, loaded.entries.size)
        assertFalse(loaded.entries[0].playable)
    }

    @Test
    fun entryWithoutAStableKeyIsSkipped() {
        // Without entryKey the row cannot be persisted, so listing it would create
        // a selection the app cannot remember.
        val json = index(
            """{"entryKey":"","internalName":"Broken","class":"COURSE",""" +
                """"assetPath":"x.wav","assetSha256":"${"a".repeat(64)}","verified":true}""",
            entryCount = 1,
        )
        val loaded = OstIndexLoader.load(json) { 1 }
        assertEquals(0, loaded.entries.size)
        assertEquals(1, loaded.skippedCount, "a skipped row must be reported, not hidden")
    }

    @Test
    fun skippedCountIsZeroWhenEverythingLoads() {
        val json = index(item("bars:0@99", "Fine", "COURSE"), entryCount = 1)
        val loaded = OstIndexLoader.load(json) { 1 }
        assertEquals(0, loaded.skippedCount)
    }

    @Test
    fun nameFallsBackToTheKeyWhenInternalNameIsEmpty() {
        val json = index(
            item("bars:0@5", "", "COURSE"),
            entryCount = 1,
        )
        val loaded = OstIndexLoader.load(json) { 1 }
        assertEquals("bars:0@5", loaded.entries[0].displayName)
    }

    @Test
    fun entryCountMismatchIsRejected() {
        // The declared count is compared against what the array carries, not
        // against what survived loading: a mismatch means the file is truncated.
        val json = index(
            item("bars:0@6", "A", "COURSE") + "," + item("bars:0@61", "B", "COURSE"),
            entryCount = 5,
        )
        val error = assertFailsWith<OstIndexLoader.LoadError> {
            OstIndexLoader.load(json) { 1 }
        }
        assertTrue(error.message!!.contains("declares 5"))
    }

    @Test
    fun zeroEntryCountIsNotTreatedAsAMismatch() {
        // A provisional index may omit the count entirely; that is not a fault.
        val json = index(item("bars:0@7", "A", "COURSE"), entryCount = 0)
        val loaded = OstIndexLoader.load(json) { 1 }
        assertEquals(1, loaded.entries.size)
    }

    @Test
    fun invalidJsonIsRejected() {
        assertFailsWith<OstIndexLoader.LoadError> {
            OstIndexLoader.load("{not json") { 1 }
        }
    }

    @Test
    fun missingItemsArrayIsRejected() {
        assertFailsWith<OstIndexLoader.LoadError> {
            OstIndexLoader.load("""{"entryCount":0}""") { 1 }
        }
    }

    @Test
    fun incompleteIndexReportsItselfAsIncomplete() {
        val json = index(item("bars:0@8", "A", "COURSE"), entryCount = 1)
        val loaded = OstIndexLoader.load(json) { 1 }

        assertFalse(loaded.status.complete, "4/499 archives is not complete")
        assertEquals(1, loaded.status.archivesRead)
        assertEquals(499, loaded.status.archivesExpected)
        assertTrue(loaded.status.reasons.isNotEmpty())
    }

    @Test
    fun fullyCoveredIndexReportsComplete() {
        val json = index(
            item("bars:0@9", "A", "COURSE"),
            entryCount = 1,
            coverage = "COMPLETE",
            freezeStatus = "FROZEN",
            archivesRead = "[\"a.bars.zs\",\"b.bars.zs\"]",
            archivesExpected = 2,
            reasons = "[]",
        )
        val loaded = OstIndexLoader.load(json) { 1 }
        assertTrue(loaded.status.complete)
    }

    @Test
    fun unknownClassBecomesOtherInsteadOfFailing() {
        val json = index(item("bars:0@10", "New", "SOMETHING_NEW"), entryCount = 1)
        val loaded = OstIndexLoader.load(json) { 1 }
        assertEquals(TrackCategory.OTHER, loaded.entries[0].category)
    }

    @Test
    fun everyEntryKeepsItsOwnPlayability() {
        // The mixed-category case from the real index: two entries, same class,
        // different codecs. A per-category shortcut would give them one answer.
        val items = listOf(
            item("bars:0@11", "SeqPlay", "SEQUENCE", sha = "1".repeat(64)),
            item("bars:0@12", "SeqBlocked", "SEQUENCE", sha = "2".repeat(64)),
        ).joinToString(",")
        val json = index(items, entryCount = 2)
        val loaded = OstIndexLoader.load(json) {
            when (it.first()) {
                '1' -> 1
                '2' -> 2
                else -> null
            }
        }

        assertEquals(2, loaded.entries.size)
        assertEquals(1, loaded.entries.count { it.playable })
        assertEquals(1, loaded.entries.count { !it.playable })
    }
}
