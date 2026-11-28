package com.mylittleblover.mkwplayer.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.kit.drawSpecialProgressBar

/** Android's animator duration scale is honored by Compose, including disabled system animations. */
internal object AppMotion {
    val easeOut = CubicBezierEasing(.23f, 1f, .32f, 1f)
    val easeInOut = CubicBezierEasing(.77f, 0f, .175f, 1f)
    const val duration = 220

    val snapSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val snapDpSpring = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Bouncy spring reserved strictly for release after a user drag/flick gesture. */
    val dragReleaseSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val pressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    fun page(direction: Int, reduceMotion: Boolean = false): ContentTransform {
        if (reduceMotion || direction == 0) {
            return (fadeIn(tween(150)) togetherWith fadeOut(tween(130)))
        }
        return ((slideInHorizontally(tween(duration, easing = easeOut)) { direction * it / 6 } +
            fadeIn(tween(180, easing = easeOut))) togetherWith
            (slideOutHorizontally(tween(180, easing = easeOut)) { -direction * it / 12 } +
                fadeOut(tween(140))))
    }

    /** Symmetric vertical rise/close transition paired with MiniPlayer expansion/collapse. */
    fun playerSheet(opening: Boolean, reduceMotion: Boolean = false): ContentTransform {
        if (reduceMotion) {
            return (fadeIn(tween(160)) togetherWith fadeOut(tween(140)))
        }
        return if (opening) {
            (slideInVertically(tween(260, easing = easeOut)) { (it * 0.24f).toInt().coerceAtLeast(1) } +
                fadeIn(tween(210, easing = easeOut))) togetherWith
                fadeOut(tween(150))
        } else {
            (fadeIn(tween(190, easing = easeOut)) togetherWith
                (slideOutVertically(tween(220, easing = easeOut)) { (it * 0.24f).toInt().coerceAtLeast(1) } +
                    fadeOut(tween(170))))
        }.apply {
            targetContentZIndex = if (opening) 1f else 0f
        }
    }
}

private object ReducedMotionTracker {
    private val reducedState = mutableStateOf<Boolean?>(null)
    @Volatile
    private var registered = false

    private fun readScale(context: android.content.Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)

    @Composable
    fun current(context: android.content.Context): Boolean {
        val current = reducedState.value
        if (current != null) return current
        val appContext = context.applicationContext ?: context
        val initial = readScale(appContext)
        reducedState.value = initial
        if (!registered) {
            synchronized(this) {
                if (!registered) {
                    registered = true
                    val resolver = appContext.contentResolver
                    val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            reducedState.value = readScale(appContext)
                        }
                    }
                    runCatching { resolver.registerContentObserver(uri, false, observer) }
                }
            }
        }
        return initial
    }
}

@Composable
internal fun rememberReducedMotion(): Boolean =
    ReducedMotionTracker.current(LocalContext.current)

@Composable
internal fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Float {
    val reduceMotion = rememberReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = AppMotion.pressSpring,
        label = "press-scale",
    )
    return scale
}

@Composable
internal fun rememberPressScale(
    pressedScale: Float = 0.97f,
): Pair<Modifier, MutableInteractionSource> {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource = interactionSource, pressedScale = pressedScale)
    return Modifier.graphicsLayer { scaleX = scale; scaleY = scale } to interactionSource
}

@Composable
internal fun DragHandlePill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.outlineVariant,
        content = {},
    )
}

/**
 * Expands a horizontal scroller edge-to-edge past its parent's [horizontal] padding so chips/cards
 * never look hard-clipped at the inner gutter while still aligning cleanly with sibling content
 * via `contentPadding = PaddingValues(horizontal = horizontal)`.
 */
internal fun Modifier.unpadHorizontal(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val padPx = horizontal.roundToPx()
    val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else return@layout measurable.measure(constraints).let { p ->
        layout(p.width, p.height) { p.placeRelative(0, 0) }
    }
    val expandedWidth = maxW + padPx * 2
    val placeable = measurable.measure(constraints.copy(minWidth = expandedWidth, maxWidth = expandedWidth))
    layout(maxW, placeable.height) {
        placeable.placeRelative(-padPx, 0)
    }
}

@Composable
internal fun SmoothProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    barHeight: Dp = 4.dp,
    specialStyle: com.mylittleblover.mkwplayer.ui.kit.MkwSliderSpecialStyle? = null,
    resetKey: Any? = null,
) {
    val reduceMotion = rememberReducedMotion()
    val safe = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val value by animateFloatAsState(safe, tween(AppMotion.duration, easing = LinearEasing), label = "playback-progress")
    val specialState = com.mylittleblover.mkwplayer.ui.kit.rememberSpecialBarVisualState(
        specialStyle = specialStyle,
        resetKey = resetKey,
        reduceMotion = reduceMotion,
    )
    Canvas(modifier = modifier.height(barHeight)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        val fillWidth = (size.width * value).coerceIn(0f, size.width)
        if (specialState.currentStyle != null) {
            drawSpecialProgressBar(
                visualState = specialState,
                trackLeft = 0f,
                barTop = 0f,
                usableW = size.width,
                barH = size.height,
                corner = radius,
                fillLeft = 0f,
                fillWidth = fillWidth,
                baseTrackColor = trackColor,
                enabled = true,
            )
        } else {
            drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
            if (fillWidth > 0.5f) {
                drawRoundRect(color = color, size = Size(fillWidth, size.height), cornerRadius = radius)
            }
        }
    }
}

/**
 * Top horizontal marquee progress bar shown while preparing interactive audio graphs.
 */
@Composable
internal fun MkwLinearIndeterminateBar(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    barHeight: Dp = 4.dp,
) {
    val reduceMotion = rememberReducedMotion()
    if (reduceMotion) {
        Canvas(modifier = modifier.height(barHeight)) {
            drawRoundRect(color = trackColor, size = size)
            drawRoundRect(
                color = color.copy(alpha = 0.72f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, 0f),
                size = Size(size.width * 0.6f, size.height),
            )
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "indeterminate-linear")
    val phase by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "indeterminate-phase",
    )
    Canvas(modifier = modifier.height(barHeight)) {
        drawRect(color = trackColor, size = size)
        val segWidth = size.width * 0.36f
        val left = (phase * size.width).coerceAtLeast(0f)
        val right = ((phase * size.width) + segWidth).coerceAtMost(size.width)
        val visibleWidth = right - left
        if (visibleWidth > 0.5f) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                size = Size(visibleWidth, size.height),
                cornerRadius = radius,
            )
        }
    }
}
