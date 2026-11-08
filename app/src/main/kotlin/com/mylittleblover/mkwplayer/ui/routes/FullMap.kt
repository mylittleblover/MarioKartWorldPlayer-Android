package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.Tokens
import kotlin.math.roundToInt

/**
 * A full-screen map that can be dragged and zoomed, with the drag clamped so it cannot be lost.
 *
 * Three rules, and each is a failure this screen has to avoid:
 *
 * **The map never leaves the viewport.** At any zoom the pan is clamped so at least the whole image still
 * covers the viewport where it can, and where the image is smaller than the viewport it is centred instead
 * of being draggable into a corner. A map you can push off-screen with a flick is a map you have to reload.
 *
 * **Zoom is anchored at the pinch, then clamped the same way.** Anchoring at the centroid is what makes a
 * pinch feel like it is moving the map rather than scaling it about the corner; clamping afterwards means the
 * anchor cannot pull the map out of bounds.
 *
 * **Reset is a real control, not a gesture.** A listener who has zoomed into a corner and cannot find the way
 * back needs a button, and double-tap is not discoverable.
 *
 * The map's own aspect ratio is preserved. Stretching it would put every pin in the wrong place, which is
 * worse than letterboxing.
 */
@Composable
internal fun FullMap(
    image: ImageBitmap,
    /** Image width over height, so the frame can letterbox rather than stretch. */
    imageAspect: Float,
    pins: List<MapPin>,
    onPin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // The viewport size, needed by the clamp. Remembered so the reset button can recompute without a gesture.
    var canvas by remember { mutableStateOf(Size.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Palette.navy900)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val previous = scale
                    // Clamped here rather than at draw time, so the pins and the image always agree.
                    val next = (previous * zoom).coerceIn(MIN_SCALE, MAX_SCALE)

                    // Keep the point under the fingers fixed: the content moves by the pan, and by the
                    // difference the zoom would otherwise introduce at the pinch centre.
                    val anchorX = (centroid.x - offsetX) / previous
                    val anchorY = (centroid.y - offsetY) / previous
                    var candidateX = centroid.x - anchorX * next + pan.x
                    var candidateY = centroid.y - anchorY * next + pan.y

                    scale = next
                    val clamped = clampPan(candidateX, candidateY, next, canvas)
                    offsetX = clamped.first
                    offsetY = clamped.second
                }
            },
    ) {
        val density = LocalDensity.current
        canvas = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        // The image is fitted inside the viewport at scale 1, so a portrait screen letterboxes the sides
        // across the full width and a landscape one letterboxes the top and bottom.
        val fitted = fit(canvas, imageAspect)
        val drawnWidth = fitted.width * scale
        val drawnHeight = fitted.height * scale

        Canvas(Modifier.fillMaxSize()) {
            translate(offsetX, offsetY) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = androidx.compose.ui.unit.IntSize(
                        drawnWidth.roundToInt(),
                        drawnHeight.roundToInt(),
                    ),
                )
            }
        }

        // Pins are laid out in the same transformed space as the image, so they stay on their dioramas.
        pins.forEach { pin ->
            if (!pin.hasPosition || pin.locked) return@forEach
            val x = offsetX + pin.xFraction.coerceIn(0f, 1f) * drawnWidth
            val y = offsetY + pin.yFraction.coerceIn(0f, 1f) * drawnHeight
            // Off-screen pins are skipped: a clickable outside the viewport is still focusable and still
            // intercepts touches at the edges.
            if (x < -PIN_TOUCH || y < -PIN_TOUCH ||
                x > canvas.width + PIN_TOUCH || y > canvas.height + PIN_TOUCH
            ) {
                return@forEach
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset((x - PIN_TOUCH / 2).roundToInt(), (y - PIN_TOUCH / 2).roundToInt()) }
                    .size(with(density) { PIN_TOUCH.toDp() })
                    .clickable(role = Role.Button, onClickLabel = "Open ${pin.label}") { onPin(pin.id) }
                    .semantics { contentDescription = pin.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(PIN_DOT)
                        .clip(CircleShape)
                        .background(Tokens.Palette.yellow500),
                )
            }
        }

        // Reset. Bottom-right, above the navigation bar, so it does not cover a pin at the centre.
        Button(
            onClick = {
                scale = MIN_SCALE
                val centred = clampPan(0f, 0f, MIN_SCALE, canvas)
                offsetX = centred.first
                offsetY = centred.second
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Tokens.Spacing.md),
            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Palette.cyan400),
        ) {
            Text("Reset view", color = Tokens.Palette.navy900, fontWeight = FontWeight.Bold)
        }
    }
}

/** Smallest zoom. Below this the map is smaller than the viewport and a drag would only move empty space. */
internal const val MIN_SCALE = 1f

/** Largest zoom. Deep enough to separate two neighbouring dioramas, shallow enough to stay sharp. */
internal const val MAX_SCALE = 6f

/** Visual pin size. */
private val PIN_DOT = 18.dp

/** Touch target around a pin, in pixels before density conversion. */
private const val PIN_TOUCH = 48f

/** The rectangle an image of [aspect] occupies when fitted inside [viewport], centred. */
internal fun fit(viewport: Size, aspect: Float): Size {
    if (viewport.width <= 0f || viewport.height <= 0f || aspect <= 0f) return Size.Zero
    val viewportAspect = viewport.width / viewport.height
    return if (aspect > viewportAspect) {
        // Wider than the viewport: fill the width, letterbox top and bottom.
        Size(viewport.width, viewport.width / aspect)
    } else {
        Size(viewport.height * aspect, viewport.height)
    }
}

/**
 * Keeps the image covering the viewport where it can.
 *
 * When the scaled image is larger than the viewport, the pan is limited so its edges cannot come inside the
 * viewport; when it is smaller on an axis, that axis is centred instead, because letting a smaller image be
 * dragged would only move empty background.
 */
internal fun clampPan(x: Float, y: Float, scale: Float, viewport: Size): Pair<Float, Float> {
    if (viewport.width <= 0f || viewport.height <= 0f) return 0f to 0f
    val fitted = fit(viewport, MAP_ASPECT)
    val width = fitted.width * scale
    val height = fitted.height * scale

    val minX = if (width >= viewport.width) viewport.width - width else (viewport.width - width) / 2f
    val maxX = if (width >= viewport.width) 0f else (viewport.width - width) / 2f
    val minY = if (height >= viewport.height) viewport.height - height else (viewport.height - height) / 2f
    val maxY = if (height >= viewport.height) 0f else (viewport.height - height) / 2f

    return x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
}

/** The shipped map's own proportions. A pin's fractions refer to this frame. */
internal const val MAP_ASPECT = 2500f / 2400f

/** The same ratio under the name the screen uses, so the asset and the frame cannot drift apart. */
internal const val MAP_IMAGE_ASPECT = MAP_ASPECT
