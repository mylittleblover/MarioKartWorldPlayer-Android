package com.mylittleblover.mkwplayer.ui.art

import com.mylittleblover.mkwplayer.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The icon table has to agree with the anchor list exactly, because the map draws an icon at each anchor's
 * coordinates. A missing pair is a stage with a hole where its picture should be; a wrong pair is a stage
 * showing a different course's art, which is harder to notice and worse to have.
 *
 * The anchor list is read from the same asset the app ships, so this test fails when either side changes
 * without the other.
 */
class StageArtTest {

    private fun anchorCodes(): List<String> {
        // The asset is not on the unit-test classpath, so it is read from the source tree. Searching
        // upwards rather than hardcoding a path keeps this working from the module directory and the repo
        // root, which are the two working directories Gradle uses.
        val relative = "app/src/main/assets/map_anchors.json"
        var directory: java.io.File? = java.io.File(".").absoluteFile
        var file: java.io.File? = null
        while (directory != null) {
            val candidate = java.io.File(directory, relative)
            if (candidate.isFile) {
                file = candidate
                break
            }
            directory = directory.parentFile
        }
        val text = (file ?: error("could not find $relative from ${java.io.File(".").absolutePath}"))
            .readText()

        // The parser is the real one; the test uses it rather than a regex so a change in the file's shape
        // fails here rather than silently matching nothing.
        val anchors = com.mylittleblover.mkwplayer.library.MapAnchors.parse(text)
            ?: error("map_anchors.json did not parse")
        return anchors.stages.map { it.code }
    }

    @Test
    fun everyAnchorHasAnIcon() {
        // A stage with no art is a hole in the map, and the honest response is for this test to fail rather
        // than for the map to draw a placeholder.
        val missing = anchorCodes().filter { StageArt.iconFor(it) == null }
        assertTrue(missing.isEmpty(), "no icon for: $missing")
    }

    @Test
    fun everyIconHasAnAnchor() {
        // The other direction: an icon nobody draws is dead weight in the APK, and usually means a renamed
        // stage that the map will now show without art.
        val anchors = anchorCodes().toSet()
        val orphans = StageArt.codes().filter { it !in anchors }
        assertTrue(orphans.isEmpty(), "icon with no anchor: $orphans")
    }

    @Test
    fun theTableSizeMatchesTheAnchorCount() {
        assertEquals(anchorCodes().size, StageArt.size)
    }

    @Test
    fun theSuffixIsOptionalOnLookup() {
        // 25 of the 30 anchors carry an export suffix and 5 do not. Callers elsewhere spell stages without
        // it, so the lookup has to work both ways or half the map loses its art.
        val suffixed = StageArt.iconFor("StormClouds_00")
        val plain = StageArt.iconFor("StormClouds")
        assertNotNull(suffixed, "StormClouds_00 has no icon")
        assertEquals(suffixed, plain, "the suffix changed the answer")

        val fortress = StageArt.iconFor("AirshipFortress_00")
        assertNotNull(fortress, "AirshipFortress_00 has no icon")
        assertEquals(fortress, StageArt.iconFor("AirshipFortress"))
    }

    @Test
    fun aStageWithNoArtReturnsNullRatherThanAnotherStagesArt() {
        // The important one. A fallback would put a wrong picture on the map where an empty space would be
        // obvious, and wrong art is the kind of mistake nobody notices for months.
        assertNull(StageArt.iconFor("RainbowRoadDeluxe"))
        assertNull(StageArt.iconFor(""))
        assertNull(StageArt.iconFor("   "))
    }

    @Test
    fun aBlankCodeDoesNotMatchTheFirstEntry() {
        // A lookup that trims to empty and then falls through would return the first table entry.
        assertNull(StageArt.iconFor(""))
        assertNotNull(StageArt.iconFor("Coaster"))
    }

    @Test
    fun theFiveUnSuffixedStagesResolve() {
        // These five exist only without `_00`, which is the export inconsistency worth pinning: a future
        // normalisation pass that assumes the suffix would drop exactly these.
        for (code in listOf("Coaster", "DonkeyKong", "FirstCircuit", "WarioStadium", "WestCity")) {
            assertNotNull(StageArt.iconFor(code), "$code lost its icon")
        }
    }

    @Test
    fun eachCodeMapsToItsOwnDrawable() {
        // Every drawable id must be distinct; a copy-paste in the table would give two stages one picture.
        val ids = anchorCodes().mapNotNull { StageArt.iconFor(it) }
        assertEquals(ids.size, ids.toSet().size, "two anchors share one drawable")
    }

    @Test
    fun theDrawablesAreTheStageFamily() {
        // Namespaced under stage_, so a stage icon cannot silently be a cup emblem.
        val ids: Set<Int> = anchorCodes().mapNotNull { StageArt.iconFor(it) }.toSet()
        assertTrue(ids.contains(R.drawable.stage_coaster))
        assertTrue(!ids.contains(R.drawable.cup_mushroom), "a cup emblem leaked into the stage table")
    }
}
