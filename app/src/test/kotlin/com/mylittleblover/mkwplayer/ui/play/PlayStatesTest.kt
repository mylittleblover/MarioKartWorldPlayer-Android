package com.mylittleblover.mkwplayer.ui.play

import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.player.FlowPresentation
import com.mylittleblover.mkwplayer.player.PlayerViewModel.FlowUi
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.versions.ListeningVersion
import com.mylittleblover.mkwplayer.versions.Origin
import com.mylittleblover.mkwplayer.versions.Section
import com.mylittleblover.mkwplayer.versions.VersionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The player page and the version page exist to stop four specific mistakes.
 *
 * A transport that offers a seek target for a track of unknown length sends the engine a frame that
 * does not exist; a section button left live on the section already playing accepts a press and does
 * nothing; a version with missing files presented as playable plays a shorter arrangement than the
 * user saved; and a timing that this project chose presented without a statement reads as the game's
 * behaviour. None of those crash, and no screenshot distinguishes them from success, so each is
 * asserted here.
 *
 * All assertions use the `(value, message)` order, and every message says what the failure would
 * mean rather than repeating the expression.
 */
class PlayStatesTest {

    // ---------------------------------------------------------------- fixtures

    private fun asset(seed: Int): AudioAssetId = AudioAssetId.of(ByteArray(32) { (seed + it).toByte() })

    private val preAsset = asset(1)
    private val bridgeAsset = asset(2)
    private val mainAsset = asset(3)
    private val outro0Asset = asset(4)

    private val pre = Section(StageFlow.Role.Pre)
    private val bridge = Section(StageFlow.Role.Bridge)
    private val main = Section(StageFlow.Role.Main)
    private val outro0 = Section(StageFlow.Role.Outro, 0)

    private val allAssets = listOf(preAsset, bridgeAsset, mainAsset, outro0Asset)

    /**
     * A plausible multi-part version: prelude, bridge, main twice, outro 0.
     *
     * Main follows `timing.mainRepeats` so the version is valid unless a test deliberately breaks a
     * rule; the constructor refuses the list and the timing disagreeing.
     */
    private fun structured(
        id: VersionId = VersionId.fresh(),
        name: String = "Structured",
        assets: List<AudioAssetId> = allAssets,
        sections: List<Section> = listOf(pre, bridge, main, outro0),
        origin: Origin = Origin.AUTHORED,
        provenance: StageFlow.Provenance = StageFlow.Provenance.PROVISIONAL,
    ): ListeningVersion {
        val timing = StageFlow.DEFAULT_TIMING
        return ListeningVersion(
            id = id,
            name = name,
            primary = assets.first(),
            assets = assets,
            sections = sections,
            repeats = sections.associateWith { section ->
                if (section.role == StageFlow.Role.Main) timing.mainRepeats else 1
            },
            timing = timing,
            provenance = provenance,
            origin = origin,
        )
    }

    private fun flowUi(
        sections: List<FlowPresentation.Section> = listOf(
            FlowPresentation.Section(segment = 1, label = "Pre", frames = 2_427_426, sampleRate = 48_000),
            FlowPresentation.Section(segment = 2, label = "Bridge", frames = 88_320, sampleRate = 48_000),
            FlowPresentation.Section(segment = 3, label = "Main", frames = 3_373_711, sampleRate = 48_000),
            FlowPresentation.Section(segment = 4, label = "Outro 00", frames = 226_286, sampleRate = 48_000),
        ),
        activeSegment: Int? = 3,
    ): FlowUi = FlowUi(
        title = "Coaster",
        sections = sections,
        activeSegment = activeSegment,
        summary = "4 parts, 1 outro",
        provenance = FlowPresentation.provenanceLabel(StageFlow("Coaster", emptyList(), StageFlow.DEFAULT_TIMING)),
        isProvisional = true,
    )

    // ---------------------------------------------------------------- the transport

    @Test
    fun theTransportReportsThePositionAndTheTotalAsTimes() {
        // Both times go through the engine's own formatter, so the page cannot invent a second one.
        val row = transportFor(
            positionFrames = 96_000,
            totalFrames = 3_840_000,
            sampleRate = 48_000,
            playing = true,
            paused = false,
        )

        assertEquals("0:02", row.position, "two seconds of frames must read as 0:02")
        assertEquals("1:20", row.total, "80 seconds of frames must read as 1:20")
    }

    @Test
    fun anUnknownLengthReadsAsUnknownRatherThanZero() {
        // 0:00 is a real length; a reader who sees it concludes the track is empty.
        val row = transportFor(
            positionFrames = 0,
            totalFrames = 0,
            sampleRate = 48_000,
            playing = false,
            paused = false,
        )

        assertEquals("--:--", row.total, "an unmeasured length must not be shown as 0:00")
        assertEquals(0f, row.fraction, "an unknown length has no progress to draw")
    }

    @Test
    fun canSeekFollowsWhetherASeekTargetExists() {
        // A target that does not exist must not be offered, for a zero length and for a negative one.
        for (total in listOf(0L, -1L)) {
            val row = transportFor(0, total, 48_000, playing = false, paused = false)
            assertFalse(
                row.canSeek,
                "a transport of unknown length ($total) must not offer a seek target",
            )
        }

        val known = transportFor(1_000, 3_840_000, 48_000, playing = true, paused = false)
        assertTrue(known.canSeek, "a measured length must offer a seek target")
    }

    @Test
    fun theOperationFlagsFollowTheStateThatWasReported() {
        val playing = transportFor(96_000, 3_840_000, 48_000, playing = true, paused = false)
        assertTrue(playing.canPause, "a playing transport must be pausable")
        assertFalse(playing.canResume, "a playing transport has nothing to resume")
        assertTrue(playing.canStop, "a playing transport has audio to stop")

        val paused = transportFor(96_000, 3_840_000, 48_000, playing = false, paused = true)
        assertFalse(paused.canPause, "a paused transport cannot pause again")
        assertTrue(paused.canResume, "a paused transport must be resumable")
        assertTrue(paused.canStop, "a paused transport still has audio to stop")

        val idle = transportFor(0, 3_840_000, 48_000, playing = false, paused = false)
        assertFalse(idle.canPause, "an idle transport must not offer Pause")
        assertFalse(idle.canResume, "an idle transport must not offer Resume")
        assertFalse(idle.canStop, "an idle transport has nothing to stop")
    }

    @Test
    fun theFractionIsThePlayedPartOfTheTrack() {
        val quarter = transportFor(960_000, 3_840_000, 48_000, playing = true, paused = false)
        assertEquals(0.25f, quarter.fraction, "a quarter of the frames is a quarter of the bar")

        val pastTheEnd = transportFor(4_800_000, 3_840_000, 48_000, playing = true, paused = false)
        assertEquals(1f, pastTheEnd.fraction, "a position past the end must clamp, not overflow the bar")
    }

    // ---------------------------------------------------------------- section buttons

    @Test
    fun noFlowHasNoSectionButtons() {
        assertEquals(
            emptyList(),
            sectionButtons(null),
            "single-track mode has nothing to jump between, and an empty list is not an error",
        )
    }

    @Test
    fun sectionsKeepTheOrderTheyWillBeHeardIn() {
        // The flow decides hearing order; the page must not re-sort it into a different structure.
        val buttons = sectionButtons(flowUi())

        assertEquals(
            listOf("Pre", "Bridge", "Main", "Outro 00"),
            buttons.map { it.label },
            "the buttons must follow the flow's own hearing order",
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            buttons.map { it.segment },
            "each button must keep the segment the engine addresses",
        )
    }

    @Test
    fun theActiveSectionIsDisabledAndEveryOtherSectionIsRequestable() {
        val buttons = sectionButtons(flowUi(activeSegment = 3))

        val active = buttons.first { it.segment == 3 }
        assertTrue(active.active, "the playing section must be marked active")
        assertFalse(
            active.enabled,
            "the active section must be disabled: requesting it again is a no-op the user cannot see",
        )

        for (button in buttons.filterNot { it.segment == 3 }) {
            assertFalse(button.active, "only one section is playing at a time")
            assertTrue(button.enabled, "every other section must stay requestable")
        }
    }

    @Test
    fun aSectionLengthIsTheMeasuredDuration() {
        val preButton = sectionButtons(flowUi()).first { it.segment == 1 }
        assertEquals("0:50", preButton.seconds, "2,427,426 frames at 48 kHz is 50 seconds")

        val bridgeButton = sectionButtons(flowUi()).first { it.segment == 2 }
        assertEquals("0:01", bridgeButton.seconds, "88,320 frames at 48 kHz is 1.84 s")
    }

    @Test
    fun anUnmeasurableSectionSaysUnknownRatherThanZero() {
        // A zero duration states an empty part; the truth is that nothing was measured.
        val broken = flowUi(
            sections = listOf(
                FlowPresentation.Section(segment = 1, label = "Main", frames = 0, sampleRate = 48_000),
                FlowPresentation.Section(segment = 2, label = "Outro 00", frames = 226_286, sampleRate = 0),
            ),
            activeSegment = null,
        )

        assertEquals(
            listOf("--:--", "--:--"),
            sectionButtons(broken).map { it.seconds },
            "a length with no frames or no sample rate must be stated as unknown, not as 0:00",
        )
    }

    // ---------------------------------------------------------------- version rows

    @Test
    fun aVersionWithEveryAssetPresentIsPlayable() {
        val version = structured(name = "Full")
        val row = versionRows(listOf(version), allAssets.toSet(), selectedId = null).single()

        assertFalse(row.broken, "a version with every asset present must not be marked broken")
        assertEquals(0, row.missingCount, "a playable version is missing no files")
        assertEquals("Full", row.name, "the row must show the version's own name")
        assertEquals(version.id.value, row.id, "the row must carry the version's identity")
    }

    @Test
    fun aVersionWithMissingAssetsIsBrokenAndSaysHowMany() {
        // Only the main asset is present, so three of the four files are gone.
        val version = structured(name = "Half gone")
        val row = versionRows(listOf(version), setOf(mainAsset), selectedId = null).single()

        assertTrue(row.broken, "a version with missing files must be marked broken")
        assertEquals(3, row.missingCount, "the row must count every missing file, not just the primary")
    }

    @Test
    fun rowsKeepTheOrderTheLibrarySaved() {
        // Sorting would read as the app forgetting the order the user established by saving.
        val versions = listOf(
            structured(name = "First"),
            structured(name = "Second"),
            structured(name = "Third"),
        )

        assertEquals(
            listOf("First", "Second", "Third"),
            versionRows(versions, allAssets.toSet(), selectedId = null).map { it.name },
            "the list must stay in saved order",
        )
    }

    @Test
    fun theSelectedVersionIsMarkedAndOnlyThatOne() {
        val first = structured(name = "First")
        val second = structured(name = "Second")

        val rows = versionRows(listOf(first, second), allAssets.toSet(), selectedId = second.id.value)
        assertEquals(listOf(false, true), rows.map { it.selected }, "exactly the selected version is marked")
        assertEquals("Second", rows.single { it.selected }.name, "the marked row must be the selected one")
    }

    @Test
    fun aSelectionThatMatchesNothingMarksNothing() {
        // A stale selection is the caller's state to repair; inventing a fallback would hide it.
        val rows = versionRows(listOf(structured(name = "Only")), allAssets.toSet(), selectedId = "stale")

        assertTrue(rows.none { it.selected }, "an unresolvable selection must not select a row by accident")
    }

    @Test
    fun theStructureLabelComesFromTheAssetCount() {
        // Structure is derived, not stored, so the label cannot disagree with the content it names.
        val single = ListeningVersion.linear(VersionId.fresh(), "Single", mainAsset)
        val multi = structured(name = "Multi")
        val rows = versionRows(listOf(single, multi), allAssets.toSet(), selectedId = null)

        assertEquals("Single file", rows[0].structureLabel, "one asset is a single-file version")
        assertTrue(
            rows[1].structureLabel.contains("4 files"),
            "a multi-file version must state how many files it binds: ${rows[1].structureLabel}",
        )
        assertTrue(
            rows[1].structureLabel.contains("4 sections"),
            "a multi-file version must state how many sections it plays: ${rows[1].structureLabel}",
        )
    }

    @Test
    fun aRestoredVersionSaysItWasRestored() {
        // A restore is not the user's latest choice, and presenting it as authored would be a lie
        // about a decision they already discarded.
        val authored = structured(name = "Authored")
        val restored = structured(name = "Restored", origin = Origin.RESTORED)
        val rows = versionRows(listOf(authored, restored), allAssets.toSet(), selectedId = null)

        assertEquals("Authored here", rows[0].originLabel, "an authored version says so")
        assertTrue(
            rows[1].originLabel.contains("Restored"),
            "a restored version must say it came from a snapshot: ${rows[1].originLabel}",
        )
    }

    // ---------------------------------------------------------------- the editor

    @Test
    fun aDraftThatBreaksNoRuleCanBeSaved() {
        val editor = editorFor(name = "Evening mix", sections = 4, repeatsOk = true, nameTaken = false)

        assertTrue(editor.canSave, "a named, unique draft with valid repeats must be savable")
        assertNull(editor.problem, "a savable draft has nothing to complain about")
        assertEquals("Evening mix", editor.name, "the draft name must be kept exactly as typed")
    }

    @Test
    fun aBlankNameIsRefusedWithItsOwnReason() {
        val editor = editorFor(name = "   ", sections = 4, repeatsOk = true, nameTaken = false)

        assertFalse(editor.canSave, "a whitespace-only name must not be savable")
        val problem = assertNotNull(editor.problem, "a refused save must say why")
        assertTrue(
            problem.contains("name", ignoreCase = true),
            "the blank-name refusal must name the field: $problem",
        )
    }

    @Test
    fun aDuplicateNameIsRefusedWithItsOwnReason() {
        // The collision itself is VersionLibrary's rule; the editor only has to state it.
        val editor = editorFor(name = "Main mix", sections = 4, repeatsOk = true, nameTaken = true)

        assertFalse(editor.canSave, "a name already used on this track must be refused")
        val problem = assertNotNull(editor.problem, "a refused save must say why")
        assertTrue(
            problem.contains("already", ignoreCase = true),
            "the duplicate-name refusal must say the name is taken: $problem",
        )
    }

    @Test
    fun aRepeatCountBelowOneIsRefusedWithItsOwnReason() {
        val editor = editorFor(name = "Main mix", sections = 4, repeatsOk = false, nameTaken = false)

        assertFalse(editor.canSave, "a repeat count of zero would drop a section from the listen")
        val problem = assertNotNull(editor.problem, "a refused save must say why")
        assertTrue(
            problem.contains("repeat", ignoreCase = true),
            "the repeat refusal must name the repeat count: $problem",
        )
    }

    @Test
    fun aVersionWithNoSectionsIsRefusedWithItsOwnReason() {
        val editor = editorFor(name = "Empty", sections = 0, repeatsOk = true, nameTaken = false)

        assertFalse(editor.canSave, "a version with no sections has nothing to play")
        val problem = assertNotNull(editor.problem, "a refused save must say why")
        assertTrue(
            problem.contains("section", ignoreCase = true),
            "the empty-order refusal must name the sections: $problem",
        )
    }

    @Test
    fun theRefusalsAreSpecificRatherThanOneGenericSentence() {
        // A single "cannot save" leaves the user guessing which of four rules they broke, and the
        // guess is usually the wrong one.
        val blank = editorFor(name = "", sections = 4, repeatsOk = true, nameTaken = false).problem
        val taken = editorFor(name = "Main mix", sections = 4, repeatsOk = true, nameTaken = true).problem
        val repeats = editorFor(name = "Main mix", sections = 4, repeatsOk = false, nameTaken = false).problem
        val empty = editorFor(name = "Main mix", sections = 0, repeatsOk = true, nameTaken = false).problem

        val messages = listOf(blank, taken, repeats, empty)
        assertTrue(messages.all { it != null }, "every refusal must carry a reason")
        assertEquals(
            messages.size,
            messages.toSet().size,
            "the four refusals must be four different sentences: $messages",
        )
    }

    // ---------------------------------------------------------------- provenance

    @Test
    fun provisionalTimingsSayTheyAreThisAppsChoice() {
        // The sentence is the player's own, not a second copy: a page that worded it differently is
        // how a chosen timing starts to read as verified.
        val version = structured(provenance = StageFlow.Provenance.PROVISIONAL)
        val line = provenanceLine(version)

        assertTrue(
            line.contains("this app's choice", ignoreCase = true),
            "a provisional version must say the timings are this app's choice: $line",
        )
        assertFalse(
            line.contains("verified", ignoreCase = true),
            "a provisional version must not claim verification: $line",
        )
        assertEquals(
            FlowPresentation.provenanceLabel(
                StageFlow("x", emptyList(), version.timing, StageFlow.Provenance.PROVISIONAL),
            ),
            line,
            "the page must state provenance exactly as the player does",
        )
    }

    @Test
    fun verifiedTimingsSayTheyWereVerified() {
        val provisional = provenanceLine(structured(provenance = StageFlow.Provenance.PROVISIONAL))
        val verified = provenanceLine(structured(provenance = StageFlow.Provenance.VERIFIED))

        assertTrue(
            verified.contains("verified", ignoreCase = true),
            "a verified version must say the timings were verified: $verified",
        )
        assertNotEquals(
            provisional,
            verified,
            "the two provenances must not read the same, or the marker conveys nothing",
        )
    }
}
