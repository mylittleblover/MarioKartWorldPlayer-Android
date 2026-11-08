package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The map's clamping is the kind of arithmetic that looks right and is wrong at the edges, so it is tested
 * without a device.
 *
 * The failure it prevents is specific: a flick that pushes the map off-screen leaves the listener with an
 * empty background and no way back except the reset button. The clamp is what makes the reset button a
 * convenience rather than the only escape.
 */
class FullMapTest {

    private val viewport = Size(1000f, 2000f)

    // ---------------------------------------------------------------- fitting

    @Test
    fun aWiderImageFillsTheWidth() {
        // 2500/2400 is slightly wider than a portrait phone, so it fills the width and letterboxes.
        val fitted = fit(viewport, MAP_ASPECT)
        assertEquals(1000f, fitted.width, 0.01f)
        assertTrue(fitted.height < viewport.height)
    }

    @Test
    fun aTallerImageFillsTheHeight() {
        val fitted = fit(Size(2000f, 500f), 0.5f) // viewport is much wider than the image
        assertEquals(500f, fitted.height, 0.01f)
        assertEquals(250f, fitted.width, 0.01f)
    }

    @Test
    fun aSquareImageInASquareViewportFillsIt() {
        val fitted = fit(Size(500f, 500f), 1f)
        assertEquals(500f, fitted.width, 0.01f)
        assertEquals(500f, fitted.height, 0.01f)
    }

    @Test
    fun anEmptyViewportFitsNothing() {
        assertEquals(Size.Zero, fit(Size.Zero, MAP_ASPECT))
        assertEquals(Size.Zero, fit(viewport, 0f))
    }

    // ---------------------------------------------------------------- clamping at scale 1

    @Test
    fun atDefaultZoomTheMapCannotBeDraggedSideways() {
        // The image already fills the width, so any horizontal pan would expose background.
        val (x, _) = clampPan(240f, 0f, MIN_SCALE, viewport)
        assertEquals(0f, x, 0.01f)
    }

    @Test
    fun atDefaultZoomTheLetterboxIsCentred() {
        // The image is shorter than the viewport, so the vertical offset is fixed at the centred value
        // rather than being draggable.
        val (_, y) = clampPan(0f, 999f, MIN_SCALE, viewport)
        val fitted = fit(viewport, MAP_ASPECT)
        val expected = (viewport.height - fitted.height) / 2f
        assertEquals(expected, y, 0.01f)
    }

    // ---------------------------------------------------------------- clamping when zoomed

    @Test
    fun aZoomedMapCannotBePushedOffTheLeftEdge() {
        val (x, _) = clampPan(5000f, 0f, 3f, viewport)
        assertEquals(0f, x, 0.01f)
    }

    @Test
    fun aZoomedMapCannotBePushedOffTheRightEdge() {
        val scale = 3f
        val width = fit(viewport, MAP_ASPECT).width * scale
        val (x, _) = clampPan(-5000f, 0f, scale, viewport)
        assertEquals(viewport.width - width, x, 0.01f)
    }

    @Test
    fun aZoomedMapCannotBePushedOffTheTop() {
        val (_, y) = clampPan(0f, 5000f, 3f, viewport)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun aZoomedMapCannotBePushedOffTheBottom() {
        val scale = 3f
        val height = fit(viewport, MAP_ASPECT).height * scale
        val (_, y) = clampPan(0f, -5000f, scale, viewport)
        assertEquals(viewport.height - height, y, 0.01f)
    }

    @Test
    fun aPanInsideTheBoundsIsLeftAlone() {
        // Clamping must not snap a legitimate drag back to zero, or the map would not move at all.
        val scale = 3f
        val width = fit(viewport, MAP_ASPECT).width * scale
        val (x, _) = clampPan((viewport.width - width) / 2f, 0f, scale, viewport)
        assertTrue(x < 0f, "a centred zoomed map has a negative offset by definition: $x")
    }

    @Test
    fun anEmptyViewportClampsToZero() {
        assertEquals(0f to 0f, clampPan(500f, 500f, 2f, Size.Zero))
    }

    // ---------------------------------------------------------------- the scale bounds

    @Test
    fun theZoomRangeIsUsable() {
        // Below 1 the map would be smaller than the viewport; above 6 a single diorama fills the screen and
        // the neighbouring stages cannot be found.
        assertEquals(1f, MIN_SCALE)
        assertEquals(6f, MAX_SCALE)
        assertTrue(MAX_SCALE > MIN_SCALE)
    }

    @Test
    fun theMapAspectIsTheShippedImagesOwn() {
        // Stretching the map would put every pin in the wrong place, so the constant must match the asset.
        assertEquals(2500f / 2400f, MAP_ASPECT, 1e-6f)
    }
}
