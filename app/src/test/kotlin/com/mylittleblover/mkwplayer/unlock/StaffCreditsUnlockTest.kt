package com.mylittleblover.mkwplayer.unlock

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffCreditsUnlockTest {

    @Test
    fun catalogTracksAllThirtyMainCoursesIncludingRainbow() {
        assertEquals(30, StaffCreditsUnlock.ALL_MAIN_COURSE_CODES.size)
        assertEquals(29, StaffCreditsUnlock.NON_RAINBOW_MAIN_COURSE_CODES.size)
        assertTrue("Rainbow" in StaffCreditsUnlock.ALL_MAIN_COURSE_CODES)
        assertTrue("BigTree" in StaffCreditsUnlock.ALL_MAIN_COURSE_CODES)
        assertTrue("SFCMarioCircuit" in StaffCreditsUnlock.ALL_MAIN_COURSE_CODES)
        assertFalse("Rainbow" in StaffCreditsUnlock.NON_RAINBOW_MAIN_COURSE_CODES)
    }

    @Test
    fun unlocksOnlyWhenAllThirtyCoursesHaveBeenListened() {
        assertFalse(StaffCreditsUnlock.isUnlocked(emptySet()))
        assertFalse(StaffCreditsUnlock.isUnlocked(StaffCreditsUnlock.NON_RAINBOW_MAIN_COURSE_CODES))
        assertTrue(StaffCreditsUnlock.isUnlocked(StaffCreditsUnlock.ALL_MAIN_COURSE_CODES))

        // Variant & prefix stage identifiers resolve cleanly.
        val variantSet = StaffCreditsUnlock.NON_RAINBOW_MAIN_COURSE_CODES + "BGM_Stage_Rainbow_00.bwav"
        assertTrue(StaffCreditsUnlock.isUnlocked(variantSet))
    }

    @Test
    fun linearModeTransition29To30QueuesNextOnceAndNeverSpams() {
        val model = StaffCreditsUnlock()
        model.seedCompletedCourses(StaffCreditsUnlock.NON_RAINBOW_MAIN_COURSE_CODES)
        assertFalse(model.isUnlocked)
        assertFalse(model.staffCreditsAutoQueued)

        // Non-main courses never trigger anything.
        assertEquals(StaffCreditsTrigger.NONE, model.onCourseCompleted("jukebox:smb_01", isInteractiveMode = false))
        assertEquals(StaffCreditsTrigger.NONE, model.onCourseCompleted("extra:title_main", isInteractiveMode = false))

        // 29 -> 30 in linear mode fires QUEUE_NEXT_LINEAR exactly once.
        val trigger = model.onCourseCompleted("Rainbow_00", isInteractiveMode = false)
        assertEquals(StaffCreditsTrigger.QUEUE_NEXT_LINEAR, trigger)
        assertTrue(model.isUnlocked)
        assertTrue(model.staffCreditsAutoQueued)
        assertFalse(model.hasDeferredInteractiveTrigger)

        // Replaying any course afterwards never fires again.
        assertEquals(StaffCreditsTrigger.NONE, model.onCourseCompleted("Rainbow", isInteractiveMode = false))
        assertEquals(StaffCreditsTrigger.NONE, model.onCourseCompleted("FirstCircuit", isInteractiveMode = true))
    }

    @Test
    fun interactiveModeTransition29To30DefersUntilInteractiveExitAndConsumesOnce() {
        val model = StaffCreditsUnlock()
        model.seedCompletedCourses(StaffCreditsUnlock.NON_RAINBOW_MAIN_COURSE_CODES)

        val trigger = model.onCourseCompleted("Rainbow", isInteractiveMode = true)
        assertEquals(StaffCreditsTrigger.DEFER_UNTIL_INTERACTIVE_EXIT, trigger)
        assertTrue(model.isUnlocked)
        assertTrue(model.staffCreditsAutoQueued)
        assertTrue(model.hasDeferredInteractiveTrigger)

        assertTrue(model.consumeDeferredInteractiveTrigger(), "deferred trigger should fire once on interactive exit")
        assertFalse(model.hasDeferredInteractiveTrigger)
        assertFalse(model.consumeDeferredInteractiveTrigger(), "second consumption must return false")

        // Further plays never re-trigger.
        assertEquals(StaffCreditsTrigger.NONE, model.onCourseCompleted("Rainbow", isInteractiveMode = true))
    }

    @Test
    fun storePersistsStateAtomicallyAndSupportsReset() {
        val dir = createTempDir(prefix = "staff-credits-store").also { it.deleteOnExit() }
        val store = StaffCreditsStore(File(dir, "staff-credits.json"))

        val model = StaffCreditsUnlock()
        model.seedCompletedCourses(StaffCreditsUnlock.NON_RAINBOW_MAIN_COURSE_CODES)
        assertEquals(
            StaffCreditsTrigger.DEFER_UNTIL_INTERACTIVE_EXIT,
            model.onCourseCompleted("Rainbow", isInteractiveMode = true),
        )

        assertNull(store.save(model.snapshot))
        val loaded = store.load()
        assertNull(loaded.problem)
        val restored = loaded.state!!
        assertEquals(30, restored.completedCount)
        assertTrue(restored.isUnlocked)
        assertTrue(restored.staffCreditsAutoQueued)
        assertTrue(restored.deferredInteractiveTrigger)

        assertNull(store.reset())
        assertNull(store.load().state)
    }
}
