package com.mylittleblover.mkwplayer.ui.detail

import com.mylittleblover.mkwplayer.library.CueCatalog
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.ui.CourseDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three detail pages exist to stop one specific lie: that a marker position is a game event.
 *
 * A marker is a named offset in a source file (L1 metadata). The game's scheduling is not
 * established, and this project has no L3 evidence for it. Every function under test can make the
 * lie in one line -- by calling a cue an exit, by rounding an unknown duration to 0.0, by leaving a
 * chosen timing unmarked -- so each of those is asserted directly rather than trusted.
 */
class DetailStatesTest {

    // ---------------------------------------------------------------- fixtures

    private fun cue(name: String, markerId: Int, frames: Long, rate: Int = 48_000) =
        CueCatalog.Cue(name = name, markerId = markerId, frames = frames, sampleRate = rate)

    private fun course(
        code: String = "Coaster",
        displayName: String = "Coaster",
        cues: List<CueCatalog.Cue> = listOf(
            cue("Full_ToOutro00", markerId = 10, frames = 290_000),
            cue("Full_ToOutro01", markerId = 11, frames = 480_000),
        ),
        outroCount: Int = 2,
        hasBridge: Boolean = true,
        hasShortPre: Boolean = true,
        hasFast: Boolean = true,
    ) = CueCatalog.Course(
        code = code,
        displayName = displayName,
        cues = cues,
        outroCount = outroCount,
        hasBridge = hasBridge,
        hasShortPre = hasShortPre,
        hasFast = hasFast,
    )

    private fun catalog(
        note: String = "Cue positions are AMTA marker records (L1 metadata).",
        unmapped: List<String> = emptyList(),
    ) = CueCatalog.Catalog(
        provenance = CueCatalog.Provenance.L1_MARKER_ONLY,
        provenanceNote = note,
        courses = listOf(course()),
        unmappedCourses = unmapped,
    )

    private fun part(
        role: StageFlow.Role,
        frames: Long,
        rate: Int = 48_000,
        loop: Pair<Long, Long>? = null,
        outroNumber: Int? = null,
    ) = StageFlow.Part(
        role = role,
        fileName = "BGM_Stage_Coaster_${role.name}.bwav",
        frames = frames,
        sampleRate = rate,
        channels = 2,
        loopStart = loop?.first,
        loopEnd = loop?.second,
        outroNumber = outroNumber,
    )

    private fun flow(
        parts: List<StageFlow.Part> = listOf(
            part(StageFlow.Role.Pre, 2_427_426, loop = 452_571L to 2_427_426L),
            part(StageFlow.Role.Bridge, 88_320),
            part(StageFlow.Role.Main, 3_373_711, loop = 493_714L to 3_373_711L),
        ),
        provenance: StageFlow.Provenance = StageFlow.Provenance.PROVISIONAL,
    ) = StageFlow(
        name = "Coaster",
        parts = parts,
        timing = StageFlow.DEFAULT_TIMING,
        provenance = provenance,
    )

    // ---------------------------------------------------------------- cue rows

    @Test
    fun cueRowsListTheCoursesCuesInPositionOrder() {
        val rows = cueRows(course())
        assertEquals(2, rows.size, "both cues must be listed")
        assertEquals(listOf("Full_ToOutro00", "Full_ToOutro01"), rows.map { it.name })
        assertEquals(listOf(0, 1), rows.map { it.index }, "the rank is the row's place in the list")
    }

    @Test
    fun noCourseAndAnEmptyCueListBothProduceNoRows() {
        // No course and a course with no cues are different reasons for the same outcome: neither
        // may be rendered as though a cue list existed.
        assertEquals(emptyList(), cueRows(null), "an absent course has nothing to list")
        assertEquals(emptyList(), cueRows(course(cues = emptyList())), "no cues means no rows")
    }

    @Test
    fun everyCueRowLabelsThePositionAsATime() {
        val row = cueRows(course())[0]
        // 290,000 frames at 48 kHz is 6.041666... s, so the row must say 6.04.
        assertTrue(row.label.contains("6.04 s"), "the position must be readable as a time: ${row.label}")
        assertEquals(6.041666, row.seconds, 0.0001, "seconds must be the archive's own conversion")
    }

    @Test
    fun everyCueRowLabelsThePositionAsAMarker() {
        // The label is read far more often than the warning above the list, so it carries the
        // marker basis itself.
        for (row in cueRows(course())) {
            assertTrue(row.label.contains("Marker"), "a position must be named a marker: ${row.label}")
            assertTrue(row.isMarkerPosition, "every row built from the archive is a marker record")
        }
    }

    @Test
    fun aCueRowNeitherCallsAMarkerAnExitNorVerifiesIt() {
        // 'Exit point' and 'verified' are the two words that would turn metadata into a claim about
        // the game. Neither may appear in a row that only knows where a marker sits.
        for (row in cueRows(course())) {
            val lowered = row.label.lowercase()
            for (forbidden in listOf("exit", "transition", "verified", "scheduled at")) {
                assertFalse(
                    lowered.contains(forbidden),
                    "'$forbidden' turns a marker record into a game event: ${row.label}",
                )
            }
        }
    }

    @Test
    fun theMarkerIdIsKeptApartFromTheRowRank() {
        // Ids are archive identifiers and repeat across courses, so an id is not a rank. The row
        // keeps both: index 0 with marker id 77 must not become 'marker 1'.
        val row = cueRows(course(cues = listOf(cue("Only", markerId = 77, frames = 48_000))))[0]
        assertEquals(0, row.index, "the rank starts at zero")
        assertEquals(77, row.markerId, "the archive id is kept verbatim")
    }

    // ---------------------------------------------------------------- structure rows

    @Test
    fun structureRowsFollowTheOrderThePartsAreHeard() {
        // The flow's own list order is an assembly detail; Main before Pre would misdescribe the
        // music, so the page imposes the hearing order.
        val shuffled = listOf(
            part(StageFlow.Role.Main, 3_373_711),
            part(StageFlow.Role.Outro, 226_286, outroNumber = 1),
            part(StageFlow.Role.Pre, 2_427_426),
            part(StageFlow.Role.Outro, 226_286, outroNumber = 0),
            part(StageFlow.Role.Bridge, 88_320),
        )
        val labels = structureRows(flow(parts = shuffled)).map { it.label }
        assertEquals(listOf("Pre", "Bridge", "Main", "Outro00", "Outro01"), labels)
    }

    @Test
    fun anAbsentFlowAndUncarriedRolesProduceNoRows() {
        // Two reasons for no rows, and neither may become a placeholder row. An empty Bridge row
        // would read as 'this stage has no bridge', which is a claim about the game rather than
        // about this flow.
        assertEquals(emptyList(), structureRows(null), "no flow means no structure to show")

        val withoutBridge = listOf(
            part(StageFlow.Role.Pre, 2_427_426),
            part(StageFlow.Role.Main, 3_373_711),
        )
        assertEquals(listOf("Pre", "Main"), structureRows(flow(parts = withoutBridge)).map { it.label })
    }

    @Test
    fun durationsComeFromTheMeasuredFrames() {
        val pre = structureRows(flow()).first { it.role == "Pre" }
        val measured = assertNotNull(pre.seconds, "a part read from a file has a measurable length")
        // 2,427,426 frames at 48 kHz is 50.571375 s, the measured Coaster value.
        assertEquals(50.571375, measured, 0.000001, "the duration must be derived, not rounded away")
    }

    @Test
    fun anUnknownFrameCountStaysNullAndNeverBecomesZero() {
        // Two separate ways for the length to be unknown: no frame count, and no sample rate.
        // 0.0 would read as a real zero-length part and send the reader looking for a silent
        // section that does not exist.
        val noFrames = part(StageFlow.Role.Main, frames = 0)
        val noRate = part(StageFlow.Role.Pre, frames = 48_000, rate = 0)

        for (row in structureRows(flow(parts = listOf(noFrames, noRate)))) {
            assertNull(row.seconds, "${row.label} has no measured length, so it must not claim one")
        }
        assertFalse(
            secondsLabel(null).contains("0.00"),
            "an unknown duration must not be rendered as a real zero: ${secondsLabel(null)}",
        )
    }

    @Test
    fun onlyMainCarriesARepeatCountAndItIsAChoice() {
        val rows = structureRows(flow())
        val main = rows.first { it.role == "Main" }
        assertEquals(2, main.repeats, "the timing's mainRepeats is the only repeat count that exists")
        assertTrue(
            main.note!!.contains("choice"),
            "a repeat count is this app's setting, not a measured loop count: ${main.note}",
        )
        for (row in rows.filterNot { it.role == "Main" }) {
            assertNull(row.repeats, "${row.label} has no measured repeat count, so none is stated")
        }
    }

    @Test
    fun anOutroRowSaysWhichEndingIsUsedIsNotEstablished() {
        val withOutro = listOf(
            part(StageFlow.Role.Main, 3_373_711),
            part(StageFlow.Role.Outro, 226_286, outroNumber = 0),
        )
        val outro = structureRows(flow(parts = withOutro)).first { it.role == "Outro" }
        assertTrue(
            outro.note!!.contains("not established"),
            "which ending the game picks is unknown and must be said: ${outro.note}",
        )
    }

    // ---------------------------------------------------------------- provenance

    @Test
    fun provenanceSeparatesMeasuredClaimsFromChosenOnes() {
        val blocks = provenanceBlocks(course(), flow(), catalog().caveat())

        assertTrue(blocks.isNotEmpty(), "a course with cues and a flow has provenance to show")
        assertTrue(
            blocks.any { it.level == "L1" && it.isEstablished },
            "the archive's marker records are a measured fact",
        )
        assertTrue(
            blocks.any { it.level == "L2" && it.isEstablished },
            "durations read from the files are a measured fact",
        )
        assertTrue(
            blocks.any { !it.isEstablished },
            "a page of nothing but established claims would hide that a choice was made",
        )
        // Every measured block must say it measured, so the two kinds never look alike.
        for (block in blocks.filter { it.isEstablished }) {
            assertTrue(
                block.statement.contains("Measured"),
                "an established block must name its measurement: ${block.statement}",
            )
        }
    }

    @Test
    fun aProvisionalTimingIsNeverMarkedEstablished() {
        // The recurring failure: a project choice shown next to measured numbers borrows their
        // credibility. The timing block has to refuse that on its own.
        val timing = provenanceBlocks(course(), flow(), null)
            .first { it.level == StageFlow.Provenance.PROVISIONAL.name }
        assertFalse(timing.isEstablished, "a provisional timing is this app's choice, not evidence")
        assertTrue(
            timing.statement.contains("not the game's"),
            "the block must say the timings are not the game's: ${timing.statement}",
        )
    }

    @Test
    fun onlyAVerifiedFlowMarksItsTimingEstablished() {
        val verified = provenanceBlocks(
            course(),
            flow(provenance = StageFlow.Provenance.VERIFIED),
            null,
        ).first { it.level == StageFlow.Provenance.VERIFIED.name }
        assertTrue(verified.isEstablished, "external evidence is what makes the timing established")
    }

    @Test
    fun theSchedulingBlockSaysAMarkerIsNotAGameEvent() {
        val scheduling = provenanceBlocks(course(), null, null).first { it.level == "Scheduling" }
        assertFalse(scheduling.isEstablished, "no evidence establishes the game's scheduling")
        assertTrue(
            scheduling.statement.contains("not established"),
            "the block must state the missing evidence: ${scheduling.statement}",
        )
        assertTrue(
            scheduling.statement.contains("choice"),
            "the block must say that treating a marker as an exit is a choice: ${scheduling.statement}",
        )
    }

    @Test
    fun theCatalogCaveatIsCarriedRatherThanParaphrased() {
        // Replacing the asset's own note with our summary loses the source's wording, and then
        // only the summary is left to check.
        val note = "Cue positions are AMTA marker records (L1 metadata)."
        val caveat = provenanceBlocks(course(), flow(), note).first { it.level == "Caveat" }
        assertEquals(note, caveat.statement, "the archive's own note must appear verbatim")
        assertFalse(caveat.isEstablished, "a caveat is a limit, not a measurement")
    }

    @Test
    fun nothingKnownYieldsNoBlocksRatherThanAFabricatedOne() {
        // An empty page is honest only if the screen says so; inventing a 'verified' block here
        // would make the empty case look like a pass.
        assertEquals(emptyList(), provenanceBlocks(null, null, null))
        assertEquals(emptyList(), provenanceBlocks(course(cues = emptyList()), null, "   "))
    }

    // ---------------------------------------------------------------- the warning

    @Test
    fun aWarningExistsExactlyWhenThereIsACueList() {
        // Presence and absence are one contract: a list of marker positions must be preceded by
        // the warning, and a warning above nothing is noise, which is what gets dismissed.
        val warning = assertNotNull(
            cueWarning(course(), catalog()),
            "a list of marker positions must be preceded by a warning",
        )
        assertTrue(warning.contains("marker"), "the warning must name the record type: $warning")
        assertTrue(
            warning.contains("not established"),
            "the warning must say the game's scheduling is unknown: $warning",
        )

        assertNull(cueWarning(null, catalog()), "no course means no cue list, so no warning")
        assertNull(cueWarning(course(cues = emptyList()), catalog()), "no cues means no warning")
    }

    @Test
    fun theWarningNeverClaimsTheGameUsesThosePositions() {
        // The one sentence that would undo the whole page: 'the game uses these positions'. Every
        // state that produces a warning is checked, including the weaker ones where the source is
        // missing or the course was not mapped.
        val catalogs = listOf<CueCatalog.Catalog?>(
            catalog(),
            null,
            catalog(unmapped = listOf("Coaster")),
        )
        for (state in catalogs) {
            val warning = cueWarning(course(), state)!!
            val lowered = warning.lowercase()
            for (forbidden in listOf("the game uses", "verified exit", "scheduled at", "game event")) {
                assertFalse(lowered.contains(forbidden), "'$forbidden' is a claim we cannot make: $warning")
            }
            assertTrue(lowered.contains("not established"), "the limit must be stated: $warning")
        }
    }

    @Test
    fun aCatalogNoteCannotReplaceOrWeakenTheFixedWarning() {
        // A future asset, or a stripped one, could carry a note that mentions markers but says
        // nothing about scheduling, or one that sounds like a clearance. The warning shown above a
        // cue list must therefore be the app's own sentence and not the asset's, or the disclaimer
        // is only as strong as whatever data file ships next.
        for (note in listOf("Positions read from the archive.", "Game scheduling verified.")) {
            val warning = cueWarning(course(), catalog(note = note))
            assertEquals(
                CUE_MARKER_WARNING,
                warning,
                "an asset's note must not alter the warning the app shows",
            )
        }
    }

    @Test
    fun aMissingArchiveOrAnUnmappedCourseIsSaidOutLoud() {
        // Two ways the positions are weaker than 'the archive says so', and both must be stated
        // rather than left looking like an ordinary marker list.
        val states = listOf(null, catalog(unmapped = listOf("Coaster")))
        for (state in states) {
            val warning = assertNotNull(
                cueWarning(course(), state),
                "the cue list exists, so a warning must exist",
            )
            assertTrue(
                warning.startsWith(CUE_MARKER_WARNING),
                "the fixed sentence still leads: $warning",
            )
            assertTrue(
                warning.length > CUE_MARKER_WARNING.length,
                "the missing source must be stated too: $warning",
            )
        }
    }

    // ---------------------------------------------------------------- the header

    @Test
    fun theHeaderStatesTheCourseAndTheArchiveFacts() {
        val detail = CourseDetail.of("Coaster", catalog())!!
        val built = header(detail, catalog().course("Coaster"))

        assertEquals("Coaster", built.title)
        assertTrue(
            built.subtitle.contains("marker"),
            "the subtitle must not let the cue list look like a schedule: ${built.subtitle}",
        )
        assertTrue(
            built.metrics.any { it.contains("2 cue positions") },
            "the header must state how many positions the archive holds: ${built.metrics}",
        )
        assertTrue(
            built.metrics.any { it.contains("2 outros in the archive") },
            "an archive count must be attributed to the archive: ${built.metrics}",
        )
    }

    @Test
    fun aCourseMissingFromTheArchiveClaimsNoZeroCounts() {
        // With no archive entry, outroCount is 0 because nothing is known. Printing '0 outros'
        // would state that as a fact about the game; the page says the positions are unavailable.
        val detail = CourseDetail.of("Gone", catalog = null, displayName = "Gone Course")!!
        val built = header(detail, course = null)

        assertEquals("Gone Course", built.title)
        assertTrue(
            built.metrics.none { it.contains("0 outro") },
            "'0 outros' would be a fact about the game, not about the build: ${built.metrics}",
        )
        assertTrue(
            built.metrics.any { it.contains("unavailable") },
            "the header must say the positions are missing from this build: ${built.metrics}",
        )
        assertTrue(
            built.metrics.none { it.contains("marker record", ignoreCase = true) },
            "there is no cue list, so the header must not describe marker records: ${built.metrics}",
        )
    }
}
