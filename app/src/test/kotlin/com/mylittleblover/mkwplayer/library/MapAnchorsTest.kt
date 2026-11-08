package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Map anchors are positions on a bitmap, and the two ways to get them wrong are both silent: a pin
 * outside the image, or a fraction treated as a pixel.
 *
 * These tests pin the arithmetic and the refusal rules. The positions themselves came from a
 * measurement of a map render, so what is checked here is that the file is internally consistent, not
 * that any particular stage sits where the game puts it.
 */
class MapAnchorsTest {

    private val sample = """
        {
          "schemaVersion": 1,
          "provenance": "EXTRACTED_ANCHORS",
          "provenanceNote": "Normalised positions measured on the map canvas.",
          "canvas": [2500, 2400],
          "stages": [
            {"code": "Coaster", "u": 0.292, "v": 0.72},
            {"code": "AirshipFortress_00", "u": 0.216, "v": 0.364},
            {"code": "Central_00", "u": 0.514, "v": 0.548}
          ],
          "cups": [{"code": "GP00", "u": 0.347, "v": 0.6671}],
          "survival": [{"code": "SV00", "u": 0.4083, "v": 0.4509}]
        }
    """.trimIndent()

    private fun catalog() = assertNotNull(MapAnchors.parse(sample))

    // ---------------------------------------------------------------- parsing

    @Test
    fun theAssetParses() {
        val catalog = catalog()
        assertEquals(3, catalog.stages.size)
        assertEquals(1, catalog.cups.size)
        assertEquals(1, catalog.survival.size)
    }

    @Test
    fun theCanvasIsCarried() {
        // Not used for placement, but it documents which render the fractions came from.
        assertEquals(2500, catalog().canvasWidth)
        assertEquals(2400, catalog().canvasHeight)
    }

    @Test
    fun theProvenanceIsRequired() {
        val catalog = catalog()
        assertEquals(MapAnchors.Provenance.EXTRACTED_ANCHORS, catalog.provenance)
    }

    @Test
    fun anUnknownProvenanceIsRefused() {
        // A future file claiming the game's own coordinates must fail loudly rather than be believed.
        val bad = sample.replace("\"EXTRACTED_ANCHORS\"", "\"GAME_COORDINATES\"")
        assertNull(MapAnchors.parse(bad))
    }

    @Test
    fun aMissingCanvasIsRefused() {
        val bad = sample.replace("\"canvas\": [2500, 2400],", "")
        assertNull(MapAnchors.parse(bad))
    }

    @Test
    fun aZeroCanvasIsRefused() {
        assertNull(MapAnchors.parse(sample.replace("[2500, 2400]", "[0, 0]")))
    }

    @Test
    fun aNonJsonAssetIsNullRatherThanAnException() {
        assertNull(MapAnchors.parse("not json"))
    }

    @Test
    fun anEmptyStageListIsRefused() {
        // A map with no stages is a basemap with nothing on it; treating it as valid hides the mistake.
        // Built by emptying the array rather than by a text replacement, because the replacement in the
        // first draft did not match the multi-line JSON and silently left the list intact -- the test
        // then failed for the right reason but for the wrong cause.
        val bad = sample.replaceFirst(
            Regex(""""stages": \[.*?\]""", RegexOption.DOT_MATCHES_ALL),
            """"stages": []""",
        )
        assertTrue(bad.contains(""""stages": []"""), "the fixture edit did not apply")
        assertNull(MapAnchors.parse(bad))
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun aFractionOutsideTheUnitSquareIsRefused() {
        // Off the bitmap reads as a broken map, not as bad data.
        assertTrue(
            runCatching { MapAnchors.Anchor("X", 1.4f, 0.5f) }.isFailure,
            "u above 1 must be refused",
        )
        assertTrue(runCatching { MapAnchors.Anchor("X", 0.5f, -0.1f) }.isFailure)
    }

    @Test
    fun aBlankCodeIsRefused() {
        assertTrue(runCatching { MapAnchors.Anchor("", 0.5f, 0.5f) }.isFailure)
    }

    @Test
    fun duplicateStageCodesAreRefused() {
        // A lookup would silently return whichever came first.
        assertTrue(
            runCatching {
                MapAnchors.Catalog(
                    provenance = MapAnchors.Provenance.EXTRACTED_ANCHORS,
                    provenanceNote = "",
                    canvasWidth = 10,
                    canvasHeight = 10,
                    stages = listOf(
                        MapAnchors.Anchor("A", 0.1f, 0.1f),
                        MapAnchors.Anchor("A", 0.2f, 0.2f),
                    ),
                    cups = emptyList(),
                    survival = emptyList(),
                )
            }.isFailure,
            "duplicate stage codes must be refused",
        )
    }

    @Test
    fun anOutOfRangeEntryIsDroppedNotClamped() {
        // Clamping would place the pin on the edge and look deliberate.
        val text = sample.replace(
            """{"code": "Central_00", "u": 0.514, "v": 0.548}""",
            """{"code": "Central_00", "u": 1.7, "v": 0.548}""",
        )
        val catalog = assertNotNull(MapAnchors.parse(text))
        assertEquals(2, catalog.stages.size)
        assertNull(catalog.stage("Central_00"))
    }

    @Test
    fun anEntryWithoutCoordinatesIsDropped() {
        val text = sample.replace(
            """{"code": "Central_00", "u": 0.514, "v": 0.548}""",
            """{"code": "Central_00"}""",
        )
        assertEquals(2, assertNotNull(MapAnchors.parse(text)).stages.size)
    }

    // ---------------------------------------------------------------- lookup

    @Test
    fun aStageCanBeFoundByItsCode() {
        val coaster = assertNotNull(catalog().stage("Coaster"))
        assertEquals(0.292f, coaster.u)
        assertEquals(0.72f, coaster.v)
    }

    @Test
    fun anUnknownCodeIsNull() {
        assertNull(catalog().stage("Nowhere"))
    }

    @Test
    fun theArchiveSuffixIsToleratedEitherWay() {
        // The export mixes `Coaster` and `AirshipFortress_00`; a caller with the other spelling
        // should not get a missing pin for no reason.
        val catalog = catalog()
        assertNotNull(catalog.stageTolerant("AirshipFortress"))
        assertNotNull(catalog.stageTolerant("AirshipFortress_00"))
        assertNotNull(catalog.stageTolerant("Central"))
    }

    @Test
    fun coverageReportsWhetherEveryStageCanBePlaced() {
        val catalog = catalog()
        assertTrue(catalog.covers(listOf("Coaster", "AirshipFortress")), "both are present")
        assertTrue(!catalog.covers(listOf("Coaster", "Nowhere")))
    }

    @Test
    fun missingListsOnlyTheUnplaceable() {
        assertEquals(
            listOf("Nowhere", "AlsoNowhere"),
            catalog().missing(listOf("Coaster", "Nowhere", "AlsoNowhere")),
        )
    }

    @Test
    fun coveringEverythingMissingNothingIsEmpty() {
        assertTrue(catalog().missing(listOf("Coaster")).isEmpty())
    }
}
