package com.mylittleblover.mkwplayer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen must start in a state that does not lie: no track, no transport
 * available, and an explicit statement that no device output exists.
 *
 * These are the assertions that would have caught the earlier confusion where the
 * UI reported success while nothing played.
 */
@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startsWithNoTrackLoaded() {
        rule.onNodeWithText("No track loaded").assertIsDisplayed()
    }

    @Test
    fun transportControlsAreDisabledUntilATrackPlays() {
        rule.onNodeWithText("Pause").assertIsNotEnabled()
        rule.onNodeWithText("Resume").assertIsNotEnabled()
        rule.onNodeWithText("Stop").assertIsNotEnabled()
    }

    @Test
    fun pickerButtonIsPresent() {
        rule.onNodeWithText("Add a .bwav file").assertIsDisplayed()
    }

    @Test
    fun timingPanelReportsNoDataRatherThanHealthy() {
        // A fresh screen has no callbacks, so it must not claim healthy playback.
        rule.onNodeWithText("NO DATA").assertIsDisplayed()
    }

    @Test
    fun theCatalogStatesItsOwnLimitations() {
        // Two cards carry the notice now -- the catalogue summary and the ownership reduction --
        // so the assertion counts nodes rather than demanding exactly one. What matters is that
        // the claim appears on screen at all: the bundled data covers 4 of 499 archives.
        val notices = rule.onAllNodesWithText("Partial catalog", substring = true)
            .fetchSemanticsNodes()
        org.junit.Assert.assertTrue(
            "the coverage notice must be visible somewhere",
            notices.isNotEmpty(),
        )
    }

    @Test
    fun theCatalogSeparatesKnownFromPlayable() {
        // "650 tracks known, 397 playable" is the distinction the whole catalog exists for.
        rule.onNodeWithText("397 playable in this build", substring = true).assertIsDisplayed()
    }

    @Test
    fun anEmptyLibrarySaysSoBeforeTheCatalogAppears() {
        rule.onNodeWithText("No track loaded").assertIsDisplayed()
        rule.onNodeWithText("Catalog").assertIsDisplayed()
    }
}
