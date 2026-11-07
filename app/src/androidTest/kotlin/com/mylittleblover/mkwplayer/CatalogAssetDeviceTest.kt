package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.library.CatalogLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bundled catalog asset must parse, and it must keep carrying its own coverage
 * disclaimer.
 *
 * These read the real asset that ships in the APK rather than a fixture, because the
 * failure being guarded is a build-time one: the generator could emit a document the
 * parser rejects, or drop the provisional status and let the UI present 650 entries as the
 * complete soundtrack.
 */
@RunWith(AndroidJUnit4::class)
class CatalogAssetDeviceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun load(): CatalogLoader.Catalog {
        val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val parsed = CatalogLoader.parse(text)
        assertNotNull("the bundled catalog did not parse", parsed)
        return parsed!!
    }

    @Test
    fun theAssetIsBundledAndParses() {
        val catalog = load()
        assertEquals(650, catalog.entries.size)
        assertEquals(397, catalog.playableCount)
    }

    @Test
    fun everyEntryCarriesMeasuredFields() {
        val catalog = load()
        val bad = catalog.entries.filter {
            it.sampleRate <= 0 || it.frames <= 0 || it.codecKind < 0 || it.name.isEmpty()
        }
        assertTrue("entries without measured fields: ${bad.take(5).map { it.name }}", bad.isEmpty())
    }

    @Test
    fun playabilityMatchesTheCodecKind() {
        // The single rule this build applies; a mismatch would advertise undecodable audio.
        val catalog = load()
        val bad = catalog.entries.filter { it.playable != (it.codecKind == 1) }
        assertTrue("playable flag disagrees with codec kind: ${bad.take(5).map { it.name }}", bad.isEmpty())
    }

    @Test
    fun unplayableEntriesExplainThemselves() {
        val catalog = load()
        val bad = catalog.entries.filter { !it.playable && it.reason.isNullOrBlank() }
        assertTrue("unplayable entries with no reason: ${bad.take(5).map { it.name }}", bad.isEmpty())
    }

    @Test
    fun theCoverageDisclaimerSurvivesTheBuild() {
        val catalog = load()
        assertTrue("the catalog must not present itself as complete", catalog.isPartial)
        assertEquals("PROVISIONAL_INCOMPLETE", catalog.freezeStatus)
        assertEquals(4, catalog.archivesRead)
        assertEquals(499, catalog.archivesExpected)
        assertTrue(catalog.coverageReasons.isNotEmpty())
    }

    @Test
    fun theMeasuredCourseCountIsPresent() {
        // 338 course themes, all playable, is the number the evidence documents rest on.
        val catalog = load()
        assertEquals(338, catalog.categoryCounts["COURSE"])
        assertEquals(338, catalog.playableByCategory["COURSE"])
    }

    @Test
    fun jukeBoxIsPresentAndEntirelyUnplayable() {
        // The limitation is visible in the catalog rather than silently missing.
        val catalog = load()
        assertEquals(227, catalog.categoryCounts["JUKEBOX"])
        assertEquals(0, catalog.playableByCategory["JUKEBOX"] ?: 0)
    }

    @Test
    fun aKnownTrackCanBeMatchedByFileName() {
        val catalog = load()
        val entry = catalog.match("BGM_Stage_Coaster.bwav")
        assertNotNull("Coaster is missing from the catalog", entry)
        assertTrue(entry!!.playable)
        assertEquals(48_000, entry.sampleRate)
    }

    @Test
    fun loopPointsArePresentForACourseTheme() {
        val catalog = load()
        val entry = catalog.match("BGM_Stage_Coaster.bwav")
        assertNotNull("Coaster is missing from the catalog", entry)
        assertNotNull("a course theme should carry loop points", entry!!.loopStart)
        assertNotNull(entry.loopEnd)
        assertTrue("loop must be ordered", entry!!.loopStart!! < entry!!.loopEnd!!)
    }
}
