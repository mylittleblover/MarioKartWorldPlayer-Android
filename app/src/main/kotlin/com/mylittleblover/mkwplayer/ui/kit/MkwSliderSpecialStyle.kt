package com.mylittleblover.mkwplayer.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.AppMotion
import kotlin.math.floor

/**
 * Dynamic visual theme for special progress bars (such as the Staff Credits medley), supporting
 * single-course solid themes, two-segment split dual-course themes, and continuously flowing
 * rainbow gradients with a left-to-right slide-in wipe between chapters.
 */
internal sealed interface MkwSliderSpecialStyle {
    /** Stable identity for transition detection; changing [key] triggers a left-to-right wipe. */
    val key: String

    data class Solid(
        override val key: String,
        val color: Color,
    ) : MkwSliderSpecialStyle

    /**
     * Two distinct color segments across the progress bar (`[0, splitFraction]` in [leftColor],
     * `[splitFraction, 1]` in [rightColor]) for overlapping dual-course medley sections.
     */
    data class SplitDual(
        override val key: String,
        val leftColor: Color,
        val rightColor: Color,
        val splitFraction: Float = 0.5f,
    ) : MkwSliderSpecialStyle

    /**
     * Continuously flowing periodic horizontal rainbow gradient. For a seamless loop, [colors]
     * should begin and end with the same color.
     */
    data class RainbowFlow(
        override val key: String,
        val colors: List<Color>,
        val periodMillis: Int = 3800,
    ) : MkwSliderSpecialStyle
}

internal data class SpecialBarVisualState(
    val currentStyle: MkwSliderSpecialStyle?,
    val previousStyle: MkwSliderSpecialStyle?,
    val wipeProgress: Float,
    val rainbowPhase: Float,
)

private const val SPECIAL_WIPE_DURATION_MS = 420

@Composable
internal fun rememberSpecialBarVisualState(
    specialStyle: MkwSliderSpecialStyle?,
    resetKey: Any?,
    reduceMotion: Boolean,
): SpecialBarVisualState {
    var currentStyle by remember(resetKey) { mutableStateOf(specialStyle) }
    var previousStyle by remember(resetKey) { mutableStateOf<MkwSliderSpecialStyle?>(null) }
    val wipeAnim = remember(resetKey) { Animatable(1f) }

    LaunchedEffect(specialStyle?.key, resetKey, reduceMotion) {
        if (specialStyle == null) {
            previousStyle = null
            currentStyle = null
            wipeAnim.snapTo(1f)
            return@LaunchedEffect
        }
        val active = currentStyle
        if (active == null) {
            currentStyle = specialStyle
            previousStyle = null
            wipeAnim.snapTo(1f)
            return@LaunchedEffect
        }
        if (active.key != specialStyle.key) {
            if (reduceMotion) {
                previousStyle = null
                currentStyle = specialStyle
                wipeAnim.snapTo(1f)
            } else {
                previousStyle = active
                currentStyle = specialStyle
                wipeAnim.snapTo(0f)
                wipeAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = SPECIAL_WIPE_DURATION_MS, easing = AppMotion.easeOut),
                )
                previousStyle = null
            }
        } else if (active != specialStyle) {
            currentStyle = specialStyle
        }
    }

    val needsRainbowFlow = !reduceMotion && (
        currentStyle is MkwSliderSpecialStyle.RainbowFlow ||
            previousStyle is MkwSliderSpecialStyle.RainbowFlow
        )
    val rainbowPhase = if (needsRainbowFlow) {
        val period = (currentStyle as? MkwSliderSpecialStyle.RainbowFlow)?.periodMillis
            ?: (previousStyle as? MkwSliderSpecialStyle.RainbowFlow)?.periodMillis
            ?: 3800
        val transition = rememberInfiniteTransition(label = "special-bar-rainbow-flow")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = period.coerceAtLeast(1200), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "special-bar-rainbow-phase",
        )
        phase
    } else {
        0f
    }

    return SpecialBarVisualState(
        currentStyle = currentStyle,
        previousStyle = previousStyle,
        wipeProgress = wipeAnim.value.coerceIn(0f, 1f),
        rainbowPhase = rainbowPhase,
    )
}

internal fun sampleSpecialStyleColor(
    style: MkwSliderSpecialStyle,
    fraction: Float,
    rainbowPhase: Float,
): Color {
    val f = fraction.coerceIn(0f, 1f)
    return when (style) {
        is MkwSliderSpecialStyle.Solid -> style.color
        is MkwSliderSpecialStyle.SplitDual -> if (f < style.splitFraction) style.leftColor else style.rightColor
        is MkwSliderSpecialStyle.RainbowFlow -> {
            val cols = style.colors
            if (cols.isEmpty()) return MkwKitTokens.Palette.crimson
            if (cols.size == 1) return cols[0]
            val shifted = ((f - rainbowPhase) % 1f + 1f) % 1f
            val scaled = shifted * (cols.size - 1)
            val idx = floor(scaled).toInt().coerceIn(0, cols.size - 2)
            val frac = (scaled - idx).coerceIn(0f, 1f)
            lerp(cols[idx], cols[idx + 1], frac)
        }
    }
}

internal fun sampleSpecialBarThumbColor(
    visualState: SpecialBarVisualState,
    fraction: Float,
    fallbackColor: Color,
): Color {
    val current = visualState.currentStyle ?: return fallbackColor
    val prev = visualState.previousStyle
    val active = if (prev != null && visualState.wipeProgress < 0.999f && fraction > visualState.wipeProgress) {
        prev
    } else {
        current
    }
    return sampleSpecialStyleColor(active, fraction, visualState.rainbowPhase)
}

/**
 * Draws a themed progress bar track + active fill with optional left-to-right slide-in wipe
 * transition across the entire progress bar.
 */
internal fun DrawScope.drawSpecialProgressBar(
    visualState: SpecialBarVisualState,
    trackLeft: Float,
    barTop: Float,
    usableW: Float,
    barH: Float,
    corner: CornerRadius,
    fillLeft: Float,
    fillWidth: Float,
    baseTrackColor: Color,
    enabled: Boolean = true,
) {
    val current = visualState.currentStyle ?: return
    val prev = visualState.previousStyle
    val wipe = visualState.wipeProgress

    if (prev != null && wipe < 0.999f) {
        drawStyledLayer(
            style = prev,
            trackLeft = trackLeft,
            barTop = barTop,
            usableW = usableW,
            barH = barH,
            corner = corner,
            fillLeft = fillLeft,
            fillWidth = fillWidth,
            baseTrackColor = baseTrackColor,
            rainbowPhase = visualState.rainbowPhase,
            enabled = enabled,
        )
        val wipeX = trackLeft + usableW * wipe
        if (wipeX > trackLeft + 0.5f) {
            clipRect(left = trackLeft, top = 0f, right = wipeX, bottom = size.height) {
                drawStyledLayer(
                    style = current,
                    trackLeft = trackLeft,
                    barTop = barTop,
                    usableW = usableW,
                    barH = barH,
                    corner = corner,
                    fillLeft = fillLeft,
                    fillWidth = fillWidth,
                    baseTrackColor = baseTrackColor,
                    rainbowPhase = visualState.rainbowPhase,
                    enabled = enabled,
                )
            }
        }
        if (wipe in 0.015f..0.985f) {
            val sheenHalfW = 7.dp.toPx()
            val pillPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = trackLeft,
                        top = barTop,
                        right = trackLeft + usableW,
                        bottom = barTop + barH,
                        cornerRadius = corner,
                    ),
                )
            }
            clipPath(pillPath) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.52f),
                            Color.Transparent,
                        ),
                        startX = wipeX - sheenHalfW,
                        endX = wipeX + sheenHalfW,
                    ),
                    topLeft = Offset(wipeX - sheenHalfW, barTop),
                    size = Size(sheenHalfW * 2f, barH),
                )
            }
        }
    } else {
        drawStyledLayer(
            style = current,
            trackLeft = trackLeft,
            barTop = barTop,
            usableW = usableW,
            barH = barH,
            corner = corner,
            fillLeft = fillLeft,
            fillWidth = fillWidth,
            baseTrackColor = baseTrackColor,
            rainbowPhase = visualState.rainbowPhase,
            enabled = enabled,
        )
    }
}

private fun DrawScope.drawStyledLayer(
    style: MkwSliderSpecialStyle,
    trackLeft: Float,
    barTop: Float,
    usableW: Float,
    barH: Float,
    corner: CornerRadius,
    fillLeft: Float,
    fillWidth: Float,
    baseTrackColor: Color,
    rainbowPhase: Float,
    enabled: Boolean,
) {
    val trackEnd = trackLeft + usableW.coerceAtLeast(1f)
    val trackBrush: Brush
    val fillBrush: Brush

    when (style) {
        is MkwSliderSpecialStyle.Solid -> {
            val tint = lerp(baseTrackColor, style.color, 0.22f)
            val fill = if (enabled) style.color else style.color.copy(alpha = 0.4f)
            trackBrush = SolidColor(tint)
            fillBrush = SolidColor(fill)
        }
        is MkwSliderSpecialStyle.SplitDual -> {
            val split = style.splitFraction.coerceIn(0.1f, 0.9f)
            val edgeA = (split - 0.005f).coerceAtLeast(0f)
            val edgeB = (split + 0.005f).coerceAtMost(1f)
            val leftTint = lerp(baseTrackColor, style.leftColor, 0.22f)
            val rightTint = lerp(baseTrackColor, style.rightColor, 0.22f)
            val leftFill = if (enabled) style.leftColor else style.leftColor.copy(alpha = 0.4f)
            val rightFill = if (enabled) style.rightColor else style.rightColor.copy(alpha = 0.4f)
            trackBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to leftTint,
                    edgeA to leftTint,
                    edgeB to rightTint,
                    1f to rightTint,
                ),
                startX = trackLeft,
                endX = trackEnd,
            )
            fillBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to leftFill,
                    edgeA to leftFill,
                    edgeB to rightFill,
                    1f to rightFill,
                ),
                startX = trackLeft,
                endX = trackEnd,
            )
        }
        is MkwSliderSpecialStyle.RainbowFlow -> {
            val cols = if (style.colors.size >= 2) style.colors else listOf(MkwKitTokens.Palette.crimson, MkwKitTokens.Palette.crimson)
            val periodPx = usableW.coerceAtLeast(1f)
            val shiftPx = rainbowPhase * periodPx
            val trackCols = cols.map { lerp(baseTrackColor, it, 0.26f) }
            val fillCols = if (enabled) cols else cols.map { it.copy(alpha = 0.4f) }
            trackBrush = Brush.horizontalGradient(
                colors = trackCols,
                startX = trackLeft + shiftPx,
                endX = trackLeft + shiftPx + periodPx,
                tileMode = TileMode.Repeated,
            )
            fillBrush = Brush.horizontalGradient(
                colors = fillCols,
                startX = trackLeft + shiftPx,
                endX = trackLeft + shiftPx + periodPx,
                tileMode = TileMode.Repeated,
            )
        }
    }

    drawRoundRect(
        brush = trackBrush,
        topLeft = Offset(trackLeft, barTop),
        size = Size(usableW, barH),
        cornerRadius = corner,
    )
    if (fillWidth > 0.5f) {
        drawRoundRect(
            brush = fillBrush,
            topLeft = Offset(fillLeft, barTop),
            size = Size(fillWidth, barH),
            cornerRadius = corner,
        )
    }
}
