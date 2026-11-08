package com.mylittleblover.mkwplayer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen must offer the stage flow entry point, keep the single-file one, and not show flow
 * sections before a flow is opened.
 *
 * The provisional label is the part that matters most: `FlowPresentation` produces it and the
 * card shows it, so if that wiring broke the app would silently present a testing configuration as
 * the game's behaviour.
 */
@RunWith(AndroidJUnit4::class)
class FlowScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theStagePickerIsOffered() {
        rule.onNodeWithText("Open a stage").assertIsDisplayed()
    }

    @Test
    fun theSingleFilePickerStillWorks() {
        // Adding the flow path must not remove what already existed.
        rule.onNodeWithText("Add a .bwav file").assertIsDisplayed()
    }

    @Test
    fun noFlowSectionIsShownBeforeOneIsOpened() {
        // A phantom section row for a flow that was never opened would be worse than an empty
        // screen, so the absence is asserted rather than assumed.
        val sections = rule.onAllNodesWithText("Playing").fetchSemanticsNodes()
        org.junit.Assert.assertEquals("no section should be marked playing", 0, sections.size)
    }

    @Test
    fun theProvisionalWarningIsNotShownWithoutAFlow() {
        // It belongs to a loaded flow; showing it on an empty screen would be noise, and showing
        // a *verified* one is impossible until evidence exists.
        val warnings = rule.onAllNodesWithText("Testing configuration", substring = true)
            .fetchSemanticsNodes()
        org.junit.Assert.assertEquals("no flow means no provenance label", 0, warnings.size)
    }
}
