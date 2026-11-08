package com.mylittleblover.mkwplayer.ui.settings

import com.mylittleblover.mkwplayer.library.CatalogLoader
import com.mylittleblover.mkwplayer.library.CatalogMatcher
import com.mylittleblover.mkwplayer.library.FolderAudioSource
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.ui.library.PageState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four settings-family pages, tested without a device.
 *
 * None of what can go wrong on these pages is layout. It is a control that claims to do something it
 * cannot, a folder that was never read being reported as an import of zero files, a repair list that
 * puts the gaps before the files that are already right, a recovery route that starts by deleting the
 * user's library, or a first-run page implying that a soundtrack is included. Those are claims, and a
 * screenshot cannot tell a true one from a false one.
 *
 * Assertion argument order throughout is (expected, actual, message), which is the order this project
 * has got wrong before.
 */
class SettingsStatesTest {

    // ---------------------------------------------------------------- fixtures

    private val folder = "/data/user/0/com.mylittleblover.mkwplayer/files/stages"

    private fun rows(
        folderExists: Boolean = true,
        repeats: Int = 2,
    ): List<SettingRow> = settingsRows(
        stagesFolderPath = folder,
        folderExists = folderExists,
        mainLoopRepeats = repeats,
    )

    private fun row(key: String, folderExists: Boolean = true): SettingRow =
        rows(folderExists = folderExists).first { it.key == key }

    private fun entry(
        name: String,
        playable: Boolean = true,
        reason: String? = null,
        frames: Long = 3_373_711,
        rate: Int = 48_000,
        channels: Int = 2,
    ) = CatalogLoader.Entry(
        name = name,
        category = "COURSE",
        codecKind = if (playable) 1 else 2,
        sampleRate = rate,
        channels = channels,
        frames = frames,
        loopStart = null,
        loopEnd = null,
        playable = playable,
        reason = reason,
    )

    private fun catalog(vararg entries: CatalogLoader.Entry): CatalogLoader.Catalog =
        CatalogLoader.Catalog(
            entries = entries.toList(),
            playableCount = entries.count { it.playable },
            categoryCounts = mapOf("COURSE" to entries.size),
            playableByCategory = mapOf("COURSE" to entries.count { it.playable }),
            freezeStatus = "PROVISIONAL_INCOMPLETE",
            coverage = "INCOMPLETE",
            coverageReasons = listOf("only 4/499 archives read"),
            archivesRead = 4,
            archivesExpected = 499,
        )

    private fun file(name: String, frames: Long = 3_373_711): CatalogMatcher.PresentFile =
        CatalogMatcher.PresentFile(
            fileName = "$name.bwav",
            frames = frames,
            sampleRate = 48_000,
            channels = 2,
        )

    private fun part(name: String = "BGM_Stage_Coaster"): StageFlow.Part = StageFlow.Part(
        role = StageFlow.Role.Main,
        fileName = "$name.bwav",
        frames = 3_373_711,
        sampleRate = 48_000,
        channels = 2,
        loopStart = 493_714L,
        loopEnd = 3_373_711L,
    )

    private fun found(name: String = "BGM_Stage_Coaster"): FolderAudioSource.Found =
        FolderAudioSource.Found(part = part(name), bytes = ByteArray(0))

    private fun scan(
        found: List<FolderAudioSource.Found> = emptyList(),
        rejected: List<String> = emptyList(),
        missingFolder: Boolean = false,
    ) = FolderAudioSource.Scan(found = found, rejected = rejected, missingFolder = missingFolder)

    // ---------------------------------------------------------------- settings

    @Test
    fun everyUnavailableRowIsDisabledAndExplainsItself() {
        // The contract forbids a control that does nothing. A disabled row is only honest when the
        // reason is on screen too, so both halves are asserted together.
        val unavailable = rows().filterNot { it.enabled }

        assertTrue(unavailable.size >= 5, "this build implements almost nothing: ${unavailable.size} rows")
        for (setting in unavailable) {
            val reason = setting.disabledReason
            assertTrue(reason != null, "${setting.key} is disabled without saying why")
            assertTrue(!reason.isNullOrBlank(), "${setting.key} needs a reason a person can read")
        }
    }

    @Test
    fun anEnabledRowNeverCarriesADisabledReason() {
        // The two fields are one fact. A row that is both on and excused could be rendered either
        // way, and whichever way it was rendered would be wrong half the time.
        for (setting in rows()) {
            if (setting.enabled) {
                assertNull(setting.disabledReason, "${setting.key} is enabled but carries a reason")
            } else {
                assertTrue(
                    setting.disabledReason != null,
                    "${setting.key} is disabled and must carry a reason",
                )
            }
        }
    }

    @Test
    fun theHardwareRoutingRowIsOffBecauseItWouldDoNothing() {
        // Named directly rather than left to the loop above: routing is the exact switch the contract
        // singles out, and a future refactor that enables it must fail here.
        val routing = row("output-routing")

        assertFalse(routing.enabled, "output routing is not implemented, so the row must be off")
        val reason = routing.disabledReason
        assertTrue(reason != null, "the routing row must explain itself")
        assertTrue(
            reason!!.contains("not implemented", ignoreCase = true),
            "the reason must say the app cannot do it: $reason",
        )
    }

    @Test
    fun everyFeatureThisBuildLacksIsListedRatherThanOmitted() {
        // A missing row reads as "not a feature"; a disabled row reads as "not this build". The page
        // is supposed to distinguish those, so the rows must exist even though none of them work.
        val keys = rows().map { it.key }

        for (expected in listOf("music-folder", "output-routing", "duck-other-audio", "colour-theme")) {
            assertTrue(expected in keys, "expected a row for $expected, got $keys")
            assertFalse(row(expected).enabled, "$expected is not implemented and must be disabled")
        }
    }

    @Test
    fun theScanRowIsTheOneWorkingAction() {
        // Scanning is real: it is the path the folder source implements. Exactly one row may be
        // enabled, because anything else would be a second control with nothing behind it.
        val enabled = rows().filter { it.enabled }

        assertEquals(1, enabled.size, "exactly the scan row may be enabled, got $enabled")
        assertEquals("scan-folder", enabled.single().key)
    }

    @Test
    fun aMissingFolderChangesTheWordingNotTheAction() {
        // Scanning a folder that is not there is how the user finds out it is not there, and the
        // scanner reports that case without throwing. Disabling the button would remove that answer.
        val missing = row("scan-folder", folderExists = false)
        val present = row("scan-folder", folderExists = true)

        assertTrue(missing.enabled, "a scan must still be possible when the folder is absent")
        assertTrue(present.enabled, "a scan is possible when the folder exists too")
        assertTrue(
            missing.detail != present.detail,
            "the two cases must not share one sentence",
        )
        assertTrue(
            missing.detail.contains("not exist", ignoreCase = true),
            "the absent case must say the folder is not there: ${missing.detail}",
        )
    }

    @Test
    fun settingKeysAreUniqueAndAscii() {
        // Keys are list keys as well as test handles: a duplicate makes a LazyColumn throw, and an
        // identifier is not allowed to be anything but ASCII.
        val keys = rows().map { it.key }

        assertEquals(keys.size, keys.toSet().size, "two rows share a key: $keys")
        for (key in keys) {
            assertTrue(key.all { it.code in 0x20..0x7E }, "'$key' must be ASCII")
        }
    }

    @Test
    fun theRepeatsRowStatesTheCountActuallyUsed() {
        // The row is disabled, so it has to be useful: it reports the fixed value rather than only
        // saying that a control is missing.
        val repeats = row("main-loop-repeats")
        val detailed = settingsRows(folder, folderExists = true, mainLoopRepeats = 3)
            .first { it.key == "main-loop-repeats" }

        assertFalse(repeats.enabled, "the repeat count is not adjustable in this build")
        assertTrue(repeats.detail.contains("2"), "the detail must state the value used: ${repeats.detail}")
        assertTrue(
            detailed.detail.contains("3"),
            "a different count must change the sentence: ${detailed.detail}",
        )
    }

    // ---------------------------------------------------------------- import

    @Test
    fun aMissingFolderSaysWhatToDoInsteadOfReportingZero() {
        // The one mistake this function exists to prevent: "0 files imported" on a scan that never
        // read a folder states a measurement nobody took.
        val summary = importSummary(scan(missingFolder = true))

        assertEquals(0, summary.added, "nothing was imported")
        assertEquals(0, summary.rejected, "nothing was rejected either")
        assertTrue(summary.rejectedReasons.isEmpty(), "an absent folder rejects no file")

        val headline = summary.headline
        assertFalse(
            headline.contains("0 "),
            "the headline must not report a count for a folder that was never read: $headline",
        )
        assertTrue(
            headline.contains("Put", ignoreCase = true) ||
                headline.contains("choose", ignoreCase = true),
            "the headline has to tell the user what to do: $headline",
        )
        assertTrue(
            headline.contains("folder", ignoreCase = true),
            "the headline must name the missing thing: $headline",
        )
    }

    @Test
    fun anEmptyFolderIsADifferentFactFromAnAbsentOne() {
        // Parsed but empty is a third fact. Folding it into the missing-folder wording would hide a
        // folder the user did create behind the message for one that was never chosen.
        val absent = importSummary(scan(missingFolder = true))
        val empty = importSummary(scan())

        assertTrue(
            empty.headline != absent.headline,
            "an empty folder and an absent one must not share one sentence",
        )
        assertFalse(
            empty.headline.contains("0 "),
            "an empty folder must not report a measured zero either: ${empty.headline}",
        )
    }

    @Test
    fun theImportCountsComeFromTheScan() {
        val summary = importSummary(
            scan(
                found = listOf(found("A"), found("B")),
                rejected = listOf("C.bwav: codec 2 cannot be decoded"),
            ),
        )

        assertEquals(2, summary.added, "two readable parts were found")
        assertEquals(1, summary.rejected, "one file was skipped")
        assertTrue(
            summary.headline.contains("2") && summary.headline.contains("1"),
            "the headline must carry both counts: ${summary.headline}",
        )
    }

    @Test
    fun rejectedReasonsSurviveVerbatimAndInOrder() {
        // The scanner's line names the file and the measurement that failed. A reworded reason loses
        // the only information that lets the user fix the file.
        val reasons = listOf(
            "Bridge.bwav: the file is not a usable part",
            "Pre.bwav: codec 2 cannot be decoded",
        )

        val summary = importSummary(scan(rejected = reasons))

        assertEquals(reasons, summary.rejectedReasons, "the reasons must be passed through unchanged")
    }

    // ---------------------------------------------------------------- match repair

    @Test
    fun ownedRowsComeBeforeMismatchedThenMissing() {
        // Ordering is the product decision: what is already right first, what nearly is second, the
        // gaps last. The catalogue order below is deliberately the opposite.
        val report = CatalogMatcher.match(
            catalog(entry("Missing"), entry("Wrong"), entry("Owned")),
            listOf(file("Wrong", frames = 1), file("Owned")),
        )

        val shown = matchRows(report)

        assertEquals(
            listOf("Owned", "Wrong", "Missing"),
            shown.map { it.entry },
            "the repair list must lead with what is already present",
        )
        assertEquals(
            listOf("Present", "Mismatched", "Missing"),
            shown.map { it.status },
            "each row must carry the word for its state",
        )
    }

    @Test
    fun theLimitCapsTheList() {
        val report = CatalogMatcher.match(
            catalog(entry("A"), entry("B"), entry("C"), entry("D"), entry("E")),
            emptyList(),
        )

        assertEquals(5, matchRows(report).size, "the default cap leaves a short list alone")
        assertEquals(3, matchRows(report, limit = 3).size, "an explicit cap wins")
    }

    @Test
    fun aNonPositiveLimitYieldsNothingRatherThanThrowing() {
        // List.take(-1) throws. A settings value of -1 must not crash a page whose job is to be read.
        val report = CatalogMatcher.match(catalog(entry("A")), emptyList())

        assertEquals(emptyList(), matchRows(report, limit = 0), "zero is empty")
        assertEquals(emptyList(), matchRows(report, limit = -1), "negative is empty")
    }

    @Test
    fun eachStatusCarriesTheDetailThatRepairsIt() {
        // An owned row names the file, a mismatch names the disagreement, and a missing row repeats
        // the catalogue's own reason. None of them prints the enum name at the user.
        val report = CatalogMatcher.match(
            catalog(
                entry("Owned"),
                entry("Wrong"),
                entry("Gap", playable = false, reason = "unsupported codec"),
            ),
            listOf(file("Owned"), file("Wrong", frames = 1)),
        )

        val byEntry = matchRows(report).associateBy { it.entry }

        assertEquals("Owned.bwav", byEntry.getValue("Owned").detail, "an owned row names its file")
        val mismatch = byEntry.getValue("Wrong").detail
        assertTrue(mismatch != null, "a mismatch must say what disagreed")
        assertTrue(mismatch!!.contains("frames"), "the detail must name the field: $mismatch")
        assertEquals(
            "unsupported codec",
            byEntry.getValue("Gap").detail,
            "a missing row repeats the catalogue's own reason",
        )
    }

    // ---------------------------------------------------------------- recovery

    @Test
    fun everyProblemOffersAtLeastOneOption() {
        for (problem in Problem.entries) {
            assertTrue(
                recoveryFor(problem).isNotEmpty(),
                "$problem must offer something the user can do",
            )
        }
    }

    @Test
    fun noRecoveryOptionIsDestructive() {
        // The contract forbids silent data loss. No option may be worded as deleting, erasing,
        // resetting, uninstalling or clearing anything -- not first, not last, not anywhere.
        val forbidden = listOf(
            "delete", "erase", "reset", "uninstall", "clear", "wipe", "remove", "discard", "format",
        )

        for (problem in Problem.entries) {
            for (option in recoveryFor(problem)) {
                val label = option.label.lowercase()
                for (word in forbidden) {
                    assertFalse(
                        label.contains(word),
                        "$problem offers '${option.label}', which reads as a destructive default",
                    )
                }
            }
        }
    }

    @Test
    fun anOptionThatCannotBeAppliedExplainsWhy() {
        // A disabled button with no explanation is a dead end, which is the failure the recovery page
        // exists to avoid.
        val unavailable = Problem.entries.flatMap { recoveryFor(it) }.filterNot { it.canDo }

        assertTrue(unavailable.isNotEmpty(), "some routes really are not implemented yet")
        for (option in unavailable) {
            val reason = option.reason
            assertTrue(reason != null, "'${option.label}' cannot be applied but says nothing")
            assertTrue(!reason.isNullOrBlank(), "'${option.label}' needs a reason a person can read")
        }
    }

    @Test
    fun anApplicableOptionCarriesNoExcuse() {
        // The mirror of the test above: an option that works must not be hedged with a reason, which
        // would read as a warning about a control that is fine.
        val available = Problem.entries.flatMap { recoveryFor(it) }.filter { it.canDo }

        assertTrue(available.isNotEmpty(), "each problem must have something that works")
        for (option in available) {
            assertNull(option.reason, "'${option.label}' works, so it needs no excuse")
        }
    }

    @Test
    fun theFirstOptionForEveryProblemCanBeTaken() {
        // The first thing offered is what a user will press. It must be possible, and it must not be
        // the costly route: nothing here is destructive, so the first option is the cheap one.
        for (problem in Problem.entries) {
            val first = recoveryFor(problem).first()
            assertTrue(first.canDo, "$problem leads with '${first.label}', which cannot be applied")
        }
    }

    @Test
    fun everyProblemHasItsOwnTitleAndASharedPageState() {
        val titles = Problem.entries.map { problemTitle(it) }

        for (title in titles) {
            assertTrue(title.isNotBlank(), "every problem needs a heading")
        }
        assertEquals(titles.size, titles.toSet().size, "two problems share a heading: $titles")

        // The state word is the shared one, not a fourth vocabulary invented for this page.
        for (problem in Problem.entries) {
            assertTrue(
                pageStateOf(problem) in PageState.entries,
                "$problem must map onto the shared page states",
            )
            assertTrue(
                pageStateOf(problem).label.isNotBlank(),
                "$problem must map onto a state that has a word",
            )
        }
    }

    // ---------------------------------------------------------------- first run

    @Test
    fun theFirstRunStepSaysNoMusicIsBundled() {
        // The claim that would be worst to get wrong: a user installing this expecting a soundtrack.
        val steps = firstRunSteps(hasCatalogue = false, hasFolder = false, hasTracks = false)

        assertTrue(steps.isNotEmpty(), "a first launch has at least one step")
        val first = steps.first()
        val text = "${first.title} ${first.body}".lowercase()

        assertTrue(text.contains("no music"), "step one must say no music comes with the app: $text")
        assertTrue(
            text.contains("bundl") || text.contains("pre-install") || text.contains("preinstall"),
            "step one must say none is bundled or pre-installed: $text",
        )
        assertTrue(first.done, "that statement is a fact about the build, not a task")
    }

    @Test
    fun firstRunStepsFollowTheFactsTheCallerHas() {
        val fresh = firstRunSteps(hasCatalogue = false, hasFolder = false, hasTracks = false)
        val ready = firstRunSteps(hasCatalogue = true, hasFolder = true, hasTracks = true)

        assertEquals(fresh.size, ready.size, "the same steps, in the same number")
        assertEquals(
            listOf(true, false, false, false),
            fresh.map { it.done },
            "a fresh install has only the fact about bundled music done",
        )
        assertEquals(
            listOf(true, true, true, true),
            ready.map { it.done },
            "a prepared library marks every step done",
        )
        for (step in fresh + ready) {
            assertTrue(step.title.isNotBlank(), "every step needs a title")
            assertTrue(step.body.isNotBlank(), "every step needs a body")
        }
    }

    // ---------------------------------------------------------------- rights

    @Test
    fun theRightsNoticeIsTheArtworkSentenceVerbatim() {
        // 'Attribution shown' and 'rights cleared' are different claims. A reworded notice is a
        // different claim, so the two strings are pinned together here.
        assertEquals(CupArt.NOTICE, rightsNotice(), "the settings page must show the artwork's own sentence")
    }

    @Test
    fun theRightsNoticeNamesNintendoAndTheRestriction() {
        val notice = rightsNotice()

        assertTrue(notice.contains("Nintendo"), notice)
        assertTrue(notice.contains("research", ignoreCase = true), notice)
        assertTrue(notice.contains("Not redistributable"), notice)
    }
}
