package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catalog is bundled data the build produces, not something the app measures at
 * runtime, so the parser has to be strict: a misread entry would tell the user a track is
 * playable when it is not, or hide the fact that the catalog is incomplete.
 */
class CatalogLoaderTest {

    private fun entry(
        name: String,
        category: String = "COURSE",
        kind: Int = 1,
        playable: Boolean = true,
        reason: String? = null,
        loop: Boolean = true,
    ): String {
        val loopStart = if (loop) "\"loopStart\": 493714," else "\"loopStart\": null,"
        val loopEnd = if (loop) "\"loopEnd\": 3373711," else "\"loopEnd\": null,"
        val reasonJson = if (reason == null) "null" else "\"$reason\""
        return """
            {"name":"$name","category":"$category","codecKind":$kind,
             "sampleRate":48000,"channels":2,"frames":3373711,
             $loopStart $loopEnd "playable":$playable,"reason":$reasonJson}
        """.trimIndent()
    }

    private fun document(entries: List<String>, extra: String = ""): String = """
        {"schemaVersion":1,
         "source":"test",
         "freezeStatus":"PROVISIONAL_INCOMPLETE",
         "coverage":"INCOMPLETE",
         "coverageReasons":["only 4/499 archives read"],
         "archivesRead":["a.bars.zs","b.bars.zs"],
         "archivesExpected":499,
         "entryCount":${entries.size},
         "playableCount":${entries.count { it.contains("\"playable\":true") }},
         "categoryCounts":{"COURSE":${entries.size}},
         "playableByCategory":{"COURSE":${entries.count { it.contains("\"playable\":true") }}},
         $extra
         "entries":[${entries.joinToString(",")}]}
    """.trimIndent()

    @Test
    fun parsesTheDocument() {
        val catalog = assertNotNull(CatalogLoader.parse(document(listOf(entry("A"), entry("B")))))
        assertEquals(2, catalog.entries.size)
        assertEquals("A", catalog.entries[0].name)
        assertEquals("COURSE", catalog.entries[0].category)
        assertEquals(1, catalog.entries[0].codecKind)
        assertEquals(48_000, catalog.entries[0].sampleRate)
        assertEquals(3_373_711L, catalog.entries[0].frames)
    }

    @Test
    fun carriesTheCoverageDisclaimer() {
        // Dropping this would let the UI present 650 entries as the complete soundtrack.
        val catalog = assertNotNull(CatalogLoader.parse(document(listOf(entry("A")))))
        assertEquals("PROVISIONAL_INCOMPLETE", catalog.freezeStatus)
        assertEquals("INCOMPLETE", catalog.coverage)
        assertTrue(catalog.isPartial)
        assertEquals(1, catalog.coverageReasons.size)
        assertEquals(2, catalog.archivesRead)
        assertEquals(499, catalog.archivesExpected)
    }

    @Test
    fun aFrozenCompleteCatalogIsNotPartial() {
        val text = document(listOf(entry("A")))
            .replace("PROVISIONAL_INCOMPLETE", "FROZEN")
            .replace("\"INCOMPLETE\"", "\"COMPLETE\"")
        val catalog = assertNotNull(CatalogLoader.parse(text))
        assertFalse(catalog.isPartial)
    }

    @Test
    fun unsupportedKindIsNotPlayableAndExplainsWhy() {
        val catalog = assertNotNull(
            CatalogLoader.parse(
                document(listOf(entry("Juke", kind = 2, playable = false, reason = "codec 2"))),
            ),
        )
        assertFalse(catalog.entries[0].playable)
        assertEquals("codec 2", catalog.entries[0].reason)
    }

    @Test
    fun loopIsAbsentRatherThanZero() {
        // Frame 0 is a real position, so a missing loop must not become 0.
        val catalog = assertNotNull(CatalogLoader.parse(document(listOf(entry("NoLoop", loop = false)))))
        assertNull(catalog.entries[0].loopStart)
        assertNull(catalog.entries[0].loopEnd)
    }

    @Test
    fun loopPointsAreParsedWhenPresent() {
        val catalog = assertNotNull(CatalogLoader.parse(document(listOf(entry("Loop")))))
        assertEquals(493_714L, catalog.entries[0].loopStart)
        assertEquals(3_373_711L, catalog.entries[0].loopEnd)
    }

    @Test
    fun wrongSchemaVersionIsRefused() {
        val text = document(listOf(entry("A"))).replace("\"schemaVersion\":1", "\"schemaVersion\":9")
        assertNull(CatalogLoader.parse(text))
    }

    @Test
    fun malformedJsonIsNull() {
        assertNull(CatalogLoader.parse("not json"))
    }

    @Test
    fun aDocumentWithNoEntriesArrayIsNull() {
        assertNull(CatalogLoader.parse("""{"schemaVersion":1}"""))
    }

    @Test
    fun anEmptyCatalogParsesToZeroEntries() {
        // Distinct from null: a bundled catalog that happens to be empty is still bundled.
        val catalog = assertNotNull(CatalogLoader.parse(document(emptyList())))
        assertEquals(0, catalog.entries.size)
    }

    @Test
    fun anEntryWithoutANameIsSkipped() {
        val text = document(listOf(entry("A"), """{"category":"COURSE","playable":true}"""))
        val catalog = assertNotNull(CatalogLoader.parse(text))
        assertEquals(1, catalog.entries.size)
    }

    @Test
    fun categoriesAreDistinctAndSorted() {
        val catalog = assertNotNull(
            CatalogLoader.parse(
                document(
                    listOf(
                        entry("A", category = "COURSE"),
                        entry("B", category = "JUKEBOX"),
                        entry("C", category = "COURSE"),
                    ),
                ),
            ),
        )
        assertEquals(listOf("COURSE", "JUKEBOX"), catalog.categories())
        assertEquals(2, catalog.inCategory("COURSE").size)
        assertEquals(1, catalog.inCategory("JUKEBOX").size)
    }

    @Test
    fun aPickedFileMatchesByStem() {
        val catalog = assertNotNull(CatalogLoader.parse(document(listOf(entry("BGM_Stage_Coaster")))))
        assertNotNull(catalog.match("BGM_Stage_Coaster.bwav"))
        assertNull(catalog.match("BGM_Stage_PeachBeach.bwav"))
    }

    @Test
    fun durationIsDerivedFromFramesAndRate() {
        val catalog = assertNotNull(CatalogLoader.parse(document(listOf(entry("A")))))
        assertEquals(70L, catalog.entries[0].durationSeconds)
    }

    @Test
    fun aZeroRateDoesNotDivideByZero() {
        val text = document(listOf(entry("A"))).replace("\"sampleRate\":48000", "\"sampleRate\":0")
        val catalog = assertNotNull(CatalogLoader.parse(text))
        assertEquals(0L, catalog.entries[0].durationSeconds)
    }
}
