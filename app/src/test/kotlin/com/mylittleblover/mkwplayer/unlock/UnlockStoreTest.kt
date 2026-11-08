package com.mylittleblover.mkwplayer.unlock

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Progress has to survive a restart, and a restart in the middle of the ceremony is the case that
 * punishes a naive serialiser: writing back PLAYING would leave a ceremony that can never finish,
 * and writing back COMPLETED would hand out the first-time congratulations for a viewing that did
 * not happen.
 *
 * The playback cursor is deliberately not restored, which is also asserted here.
 */
class UnlockStoreTest {

    private fun tempDir(): File =
        createTempDir(prefix = "unlock-store").also { it.deleteOnExit() }

    private fun store(dir: File = tempDir()) = UnlockStore(File(dir, "special-cup.json"))

    private val rate = 48_000
    private val required = SpecialCupUnlock.requiredFrames(rate)

    private fun state(
        listened: Map<String, Long> = emptyMap(),
        completed: Set<String> = emptySet(),
        entitled: Boolean = false,
        ceremony: SpecialCupUnlock.CeremonyPhase = SpecialCupUnlock.CeremonyPhase.NONE,
        congratulated: Boolean = false,
    ) = SpecialCupUnlock.UnlockState(
        listenedFrames = listened,
        completedStages = completed,
        entitled = entitled,
        ceremony = ceremony,
        congratulated = congratulated,
        currentStage = null,
        lastReportedFrames = -1,
        currentTrackFrames = 0,
        currentSampleRate = 0,
    )

    // ---------------------------------------------------------------- round trip

    @Test
    fun progressSurvivesASaveAndReload() {
        val s = store()
        assertNull(s.save(state(listened = mapOf("S" to required), completed = setOf("S"))))

        val loaded = s.load()
        assertNull(loaded.problem)
        val restored = loaded.state!!
        assertEquals(required, restored.listenedFrames["S"])
        assertEquals(setOf("S"), restored.completedStages)
    }

    @Test
    fun theEntitlementSurvivesARestart() {
        val s = store()
        s.save(state(entitled = true, ceremony = SpecialCupUnlock.CeremonyPhase.COMPLETED, congratulated = true))

        val restored = s.load().state!!
        assertTrue(restored.entitled)
        assertTrue(restored.congratulated)
    }

    @Test
    fun severalStagesRoundTrip() {
        val s = store()
        s.save(state(listened = mapOf("A" to 100, "B" to 200), completed = setOf("A")))

        val restored = s.load().state!!
        assertEquals(2, restored.listenedFrames.size)
        assertEquals(setOf("A"), restored.completedStages)
    }

    @Test
    fun noFileIsNoProgressRatherThanAProblem() {
        val loaded = store().load()
        assertNull(loaded.state)
        assertNull(loaded.problem)
    }

    @Test
    fun aBlankFileIsTreatedAsNoProgress() {
        val dir = tempDir()
        File(dir, "special-cup.json").writeText("   ")
        val loaded = UnlockStore(File(dir, "special-cup.json")).load()
        assertNull(loaded.state)
        assertNull(loaded.problem)
    }

    // ---------------------------------------------------------------- the crash case

    @Test
    fun aCeremonyThatWasPlayingReadsBackAsOfferedNotPlaying() {
        // The process died mid-show. Playing would be unfinishable; completed would be a lie.
        val s = store()
        s.save(state(entitled = true, ceremony = SpecialCupUnlock.CeremonyPhase.PLAYING))

        val loaded = s.load()
        assertTrue(loaded.repairedCeremony, "the correction is reported, not silent")
        assertEquals(SpecialCupUnlock.CeremonyPhase.PENDING, loaded.state!!.ceremony)
    }

    @Test
    fun aRepairedCeremonyDoesNotClaimToHaveBeenWatched() {
        val s = store()
        s.save(state(entitled = true, ceremony = SpecialCupUnlock.CeremonyPhase.PLAYING))

        val restored = s.load().state!!
        assertFalse(restored.congratulated, "a viewing that did not finish spends nothing")
    }

    @Test
    fun aCompletedCeremonyIsNotRepaired() {
        // Only PLAYING is impossible across a restart; COMPLETED is a durable outcome.
        val s = store()
        s.save(state(entitled = true, ceremony = SpecialCupUnlock.CeremonyPhase.COMPLETED))

        val loaded = s.load()
        assertFalse(loaded.repairedCeremony)
        assertEquals(SpecialCupUnlock.CeremonyPhase.COMPLETED, loaded.state!!.ceremony)
    }

    // ---------------------------------------------------------------- the cursor

    @Test
    fun thePlaybackCursorIsNotRestored() {
        // Restoring it would let progress accumulate from a position never actually reached.
        val s = store()
        s.save(
            state(listened = mapOf("S" to 48_000)).copy(
                currentStage = "S",
                lastReportedFrames = 48_000,
                currentTrackFrames = 9_000_000,
                currentSampleRate = 48_000,
            ),
        )

        val restored = s.load().state!!
        assertNull(restored.currentStage, "a cursor cannot outlive the process")
        assertEquals(-1L, restored.lastReportedFrames)
        assertEquals(0, restored.currentSampleRate)
    }

    @Test
    fun theStoredFileDoesNotContainTheCursor() {
        // Belt and braces: the field is not in the format at all, so no future reader can revive it.
        val dir = tempDir()
        val file = File(dir, "special-cup.json")
        UnlockStore(file).save(
            state().copy(currentStage = "S", lastReportedFrames = 12_345, currentSampleRate = 48_000),
        )
        val text = file.readText()
        assertFalse(text.contains("12,345"), text.take(200))
        assertFalse(text.contains("currentStage"), text.take(200))
    }

    // ---------------------------------------------------------------- damage

    @Test
    fun garbageIsQuarantinedAndReported() {
        val dir = tempDir()
        val file = File(dir, "special-cup.json")
        file.writeText("this is not json")

        val loaded = UnlockStore(file).load()
        assertNull(loaded.state)
        assertNotNull(loaded.problem)
        assertTrue(File(dir, "special-cup.json.corrupt").exists(), "the bad file is kept")
    }

    @Test
    fun anUnknownVersionIsQuarantined() {
        val dir = tempDir()
        val file = File(dir, "special-cup.json")
        file.writeText("""{"schemaVersion":99,"entitled":true}""")

        val loaded = UnlockStore(file).load()
        assertNull(loaded.state, "a version this build does not know must not be trusted")
        assertNotNull(loaded.problem)
    }

    @Test
    fun anUnknownCeremonyNameFallsBackAtTheStart() {
        // A renamed phase must not be read as PLAYING by accident.
        val dir = tempDir()
        val file = File(dir, "special-cup.json")
        file.writeText("""{"schemaVersion":1,"ceremony":"SOMETHING_NEW","entitled":true}""")

        val loaded = UnlockStore(file).load()
        assertEquals(SpecialCupUnlock.CeremonyPhase.NONE, loaded.state!!.ceremony)
    }

    @Test
    fun aNegativeFrameCountIsSkippedRatherThanTrusted() {
        val dir = tempDir()
        val file = File(dir, "special-cup.json")
        file.writeText("""{"schemaVersion":1,"listenedFrames":{"A":-5,"B":100},"completedStages":[]}""")

        val restored = UnlockStore(file).load().state!!
        assertNull(restored.listenedFrames["A"], "a negative credit is not progress")
        assertEquals(100L, restored.listenedFrames["B"])
    }

    @Test
    fun anEmptyStageNameIsSkipped() {
        val dir = tempDir()
        val file = File(dir, "special-cup.json")
        file.writeText("""{"schemaVersion":1,"completedStages":["","A"]}""")

        val restored = UnlockStore(file).load().state!!
        assertEquals(setOf("A"), restored.completedStages)
    }

    // ---------------------------------------------------------------- saving

    @Test
    fun aSecondSaveReplacesTheFirst() {
        // The renameTo trap: a silently failed second save is how progress goes missing on Windows.
        val s = store()
        s.save(state(completed = setOf("A")))
        s.save(state(completed = setOf("A", "B")))

        assertEquals(setOf("A", "B"), s.load().state!!.completedStages)
    }

    @Test
    fun theFileRecordsTheSchemaVersion() {
        val dir = tempDir()
        val file = File(dir, "special-cup.json")
        UnlockStore(file).save(state())
        assertTrue(file.readText().contains("\"schemaVersion\":1"))
    }

    @Test
    fun savingCreatesTheDirectory() {
        val dir = tempDir()
        val nested = File(dir, "a/b")
        val error = UnlockStore(File(nested, "special-cup.json")).save(state(entitled = true))
        assertNull(error)
        assertTrue(nested.exists())
    }

    // ---------------------------------------------------------------- integration

    @Test
    fun aFullUnlockRoundTripsThroughTheStore() {
        // End to end at the logic layer: earn it, save it, restore it, and it is still open.
        var u = SpecialCupUnlock(setOf("A"))
        u = u.withState(u.beginStage("A", 10_000_000, rate))
        var position = 0L
        while (position <= required) {
            u = u.withState(u.report(position).state)
            position += 4_800
        }
        assertTrue(u.isEntitled)

        val s = store()
        assertNull(s.save(u.snapshot))

        val restored = SpecialCupUnlock(setOf("A")).withState(s.load().state!!)
        assertTrue(restored.isEntitled, "the cup is still open after a restart")
        assertEquals(SpecialCupUnlock.CeremonyPhase.PENDING, restored.snapshot.ceremony)
        assertTrue(restored.isPending, "the offer is still waiting")
    }
}
