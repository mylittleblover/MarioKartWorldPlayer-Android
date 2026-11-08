package com.mylittleblover.mkwplayer.ui.art

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The button frames are real assets with real geometry, and nine-slicing them wrong is the classic
 * visual bug that no test would otherwise catch.
 *
 * The slice inset has to be smaller than the frame, larger than the corner curve, and symmetrical,
 * because an asymmetric slice stretches one end of the pill and not the other.
 */
class ButtonArtTest {

    @Test
    fun everyStateHasAnAsset() {
        for (drawable in listOf(ButtonArt.IDLE, ButtonArt.FOCUSED, ButtonArt.PRESSED, ButtonArt.DISABLED)) {
            assertTrue(drawable != 0, "a button state with no frame renders as a blank box")
        }
    }

    @Test
    fun theFourStatesAreDistinct() {
        // Two states sharing one frame means the user cannot see the difference between idle and
        // pressed, which is the whole point of having a pressed state.
        val all = listOf(ButtonArt.IDLE, ButtonArt.FOCUSED, ButtonArt.PRESSED, ButtonArt.DISABLED)
        assertEquals(all.size, all.toSet().size, "two button states share a frame")
    }

    @Test
    fun theSliceInsetFitsInsideTheFrame() {
        assertTrue(ButtonArt.SLICE_INSET > 0, "a zero inset stretches the rounded ends")
        assertTrue(
            ButtonArt.SLICE_INSET * 2 < ButtonArt.FRAME_WIDTH,
            "left and right insets together must be narrower than the frame, or there is nothing to stretch",
        )
    }

    @Test
    fun theSliceInsetIsAtLeastTheCornerCurve() {
        // The pill's end is roughly half the frame height. A smaller inset cuts into the curve and the
        // corner visibly deforms at narrow widths.
        assertTrue(
            ButtonArt.SLICE_INSET >= ButtonArt.FRAME_HEIGHT / 2 - 1,
            "inset ${ButtonArt.SLICE_INSET} is smaller than the corner radius ${ButtonArt.FRAME_HEIGHT / 2}",
        )
    }

    @Test
    fun theSliceInsetLeavesAUsableStretch() {
        val stretch = ButtonArt.FRAME_WIDTH - ButtonArt.SLICE_INSET * 2
        assertTrue(stretch >= 200, "only $stretch px of stretchable middle; the plate would bunch up")
    }

    @Test
    fun theFrameIsAsWideAsTheSourceSprite() {
        // The numbers are used to derive text padding; a wrong width there clips the label.
        assertEquals(643, ButtonArt.FRAME_WIDTH)
        assertEquals(119, ButtonArt.FRAME_HEIGHT)
    }
}
