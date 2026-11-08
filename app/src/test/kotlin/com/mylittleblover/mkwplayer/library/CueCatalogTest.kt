package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cue asset is metadata, and the whole risk is that metadata gets presented as behaviour.
 *
 * A marker position says "a record names this position". It does not say "the game schedules a
 * transition here". If the app confuses the two it can claim to reproduce the game's exits, which is
 * the L1-as-L3 substitution this project forbids.
 */
class CueCatalogTest {

    private val sample = """
        {
          "schemaVersion": 1,
          "provenance": "L1_MARKER_ONLY",
          "provenanceNote": "Cue positions are AMTA marker records (L1 metadata).",
          "courses": [
            {
              "code": "Coaster",
              "name": "Coaster",
              "outroCount": 7,
              "hasBridge": true,
              "hasShortPre": true,
              "hasFast": true,
              "cues": [
                {"name": "Full_ToOutro00", "markerId": 10, "frames": 1000, "sampleRate": 48000},
                {"name": "Full_ToOutro01", "markerId": 11, "frames": 5000, "sampleRate": 48000},
                {"name": "Full_ToOutro02", "markerId": 12, "frames": 9000, "sampleRate": 48000}
              ]
            },
            {
              "code": "DinoBones",
              "name": "Dino Bones",
              "outroCount": 2,
              "hasBridge": true,
              "hasShortPre": true,
              "hasFast": true,
              "cues": [{"name": "Full_ToOutro00", "markerId": 5, "frames": 800, "sampleRate": 48000}]
            }
          ],
          "unmappedCourses": []
        }
    """.trimIndent()

    // ---------------------------------------------------------------- parsing

    @Test
    fun theAssetParses() {
        val catalog = assertNotNull(CueCatalog.parse(sample))
        assertEquals(2, catalog.courses.size)
        assertEquals(4, catalog.totalCues())
    }

    @Test
    fun theProvenanceSurvivesParsing() {
        assertEquals(
            CueCatalog.Provenance.L1_MARKER_ONLY,
            CueCatalog.parse(sample)!!.provenance,
            "the marker-only basis must not be lost",
        )
    }

    @Test
    fun anUnknownProvenanceIsRefusedRatherThanDefaulted() {
        // A future asset claiming a stronger basis must fail loudly, not be read as L1.
        val bad = sample.replace("\"L1_MARKER_ONLY\"", "\"L3_VERIFIED_EVENTS\"")
        assertNull(CueCatalog.parse(bad), "an unknown basis cannot be assumed")
    }

    @Test
    fun aNonJsonAssetIsNullRatherThanAnException() {
        assertNull(CueCatalog.parse("not json at all"))
    }

    @Test
    fun missingCoursesIsNull() {
        assertNull(CueCatalog.parse("""{"provenance":"L1_MARKER_ONLY"}"""))
    }

    @Test
    fun aCourseWithoutCuesIsDropped() {
        // A course row with no cues would show an empty transition menu.
        val text = sheet(course("Empty", cues = ""))
        assertNull(CueCatalog.parse(text)?.course("Empty"))
    }

    @Test
    fun aCueWithoutAPositionIsDropped() {
        val text = sheet(
            course(
                "C",
                cues = """{"name":"Full_ToOutro00","markerId":1,"frames":-1,"sampleRate":48000}""",
            ),
        )
        assertNull(CueCatalog.parse(text)?.course("C"))
    }

    @Test
    fun aCueWithoutARateIsDropped() {
        // Without a rate the seconds cannot be shown, and defaulting to 48 kHz would be a guess.
        val text = sheet(
            course(
                "C",
                cues = """{"name":"Full_ToOutro00","markerId":1,"frames":1000,"sampleRate":0}""",
            ),
        )
        assertNull(CueCatalog.parse(text)?.course("C"))
    }

    @Test
    fun cuesAreParsedInPositionOrder() {
        val text = sheet(
            course(
                "C",
                cues = """
                    {"name":"b","markerId":2,"frames":9000,"sampleRate":48000},
                    {"name":"a","markerId":1,"frames":1000,"sampleRate":48000}
                """,
            ),
        )
        val cues = CueCatalog.parse(text)!!.course("C")!!.cues
        assertEquals(listOf(1000L, 9000L), cues.map { it.frames })
    }

    // ---------------------------------------------------------------- ordering rules

    @Test
    fun aCourseRefusesUnorderedCues() {
        assertTrue(
            runCatching {
                CueCatalog.Course(
                    code = "C",
                    displayName = "C",
                    cues = listOf(
                        CueCatalog.Cue("late", 1, 9000, 48_000),
                        CueCatalog.Cue("early", 2, 1000, 48_000),
                    ),
                    outroCount = 1,
                    hasBridge = false,
                    hasShortPre = false,
                    hasFast = false,
                )
            }.isFailure,
            "an unordered cue list would let 'next cue' skip one",
        )
    }

    @Test
    fun aBlankCourseCodeIsRefused() {
        assertTrue(
            runCatching {
                CueCatalog.Course("", "x", listOf(CueCatalog.Cue("a", 1, 0, 48_000)), 1, false, false, false)
            }.isFailure,
        )
    }

    // ---------------------------------------------------------------- navigation

    private fun course() = CueCatalog.parse(sample)!!.course("Coaster")!!

    @Test
    fun theNextCueIsStrictlyAfterThePosition() {
        // Strictly after: a cue at the current position has already been passed.
        assertEquals("Full_ToOutro01", course().cueAfter(1000)!!.name)
    }

    @Test
    fun aPositionBeforeEverythingHasTheFirstCueNext() {
        assertEquals("Full_ToOutro00", course().cueAfter(0)!!.name)
    }

    @Test
    fun aPositionAfterEverythingHasNoNextCue() {
        assertNull(course().cueAfter(20_000), "past the last cue there is nothing to offer")
    }

    @Test
    fun thePreviousCueIsAtOrBeforeThePosition() {
        assertEquals("Full_ToOutro00", course().cueBefore(1000)!!.name)
    }

    @Test
    fun aPositionBeforeEverythingHasNoPreviousCue() {
        assertNull(course().cueBefore(0))
    }

    // ---------------------------------------------------------------- the caveat

    @Test
    fun theCaveatIsAlwaysAvailable() {
        // Even when the asset has no note, the app must have something to show.
        val text = sample.replace(
            "\"provenanceNote\": \"Cue positions are AMTA marker records (L1 metadata).\",",
            "",
        )
        val catalog = CueCatalog.parse(text)!!
        assertTrue(catalog.caveat().isNotBlank(), "a missing note must not leave nothing to show")
        assertTrue(catalog.caveat().contains("not established"), catalog.caveat())
    }

    @Test
    fun theCaveatDoesNotClaimTheGameUsesTheseCues() {
        // Two different notes have to pass this: the asset's own wording, which is factual, and the
        // fallback, which is used when the asset carries none. Asserting on one specific phrase would
        // only test the phrase I happened to write.
        val own = CueCatalog.parse(sample)!!.caveat()
        val fallback = CueCatalog.parse(
            sample.replace(
                "\"provenanceNote\": \"Cue positions are AMTA marker records (L1 metadata).\",",
                "",
            ),
        )!!.caveat()

        for (text in listOf(own, fallback)) {
            val lowered = text.lowercase()
            assertTrue(
                !lowered.contains("the game uses") && !lowered.contains("verified exit"),
                "the caveat must not imply discovered behaviour: $text",
            )
        }
        // The fallback must still say what is unknown, because it is what a stripped asset shows.
        assertTrue(fallback.contains("not established"), fallback)
    }

    @Test
    fun anEmptyCatalogIsIdentifiable() {
        val empty = CueCatalog.Catalog(
            provenance = CueCatalog.Provenance.L1_MARKER_ONLY,
            provenanceNote = "",
            courses = emptyList(),
            unmappedCourses = emptyList(),
        )
        assertTrue(empty.isEmpty)
        assertNull(empty.course("Anything"))
    }

    // ---------------------------------------------------------------- helpers

    private fun course(code: String, cues: String) = """
        {"code":"$code","name":"$code","outroCount":1,"hasBridge":false,"hasShortPre":false,
         "hasFast":false,"cues":[$cues]}
    """.trimIndent()

    private fun sheet(vararg courses: String) = """
        {"provenance":"L1_MARKER_ONLY","provenanceNote":"",
         "courses":[${courses.joinToString(",")}],"unmappedCourses":[]}
    """.trimIndent()
}
