package com.mylittleblover.mkwplayer.ui.kit

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion

/**
 * Shared motion tokens and choreography primitives for JukeKart ("Porcelain & Hairline Craft"),
 * aligned directly with [AppMotion] so every newer screen matches the crisp, zero-sluggishness
 * pacing of existing screens.
 *
 * Design principles:
 * - Delegates directly to [AppMotion.easeOut], [AppMotion.snapSpring], and [AppMotion.pressSpring].
 * - Snappy durations (`90–200ms`); exits are always faster than enters.
 * - Strictly non-bouncy springs (`DampingRatioNoBouncy`) and zero artificial list/hero stagger.
 * - Honors live Android reduced-motion observer via [rememberReducedMotion].
 */
internal object MkwMotion {

    /** Strong ease-out matching [AppMotion.easeOut]. */
    val EaseOut = AppMotion.easeOut

    /** Ease-in-out matching [AppMotion.easeInOut]. */
    val EaseInOut = AppMotion.easeInOut

    /** Crisp deceleration curve for sheets and drawers. */
    val EaseDrawer = AppMotion.easeOut

    const val PressMs = 90
    const val MicroMs = 120
    const val ExitMs = 120
    const val StandardMs = 160
    const val PanelMs = 180
    const val DrawerMs = 200
    const val CelebrationMs = 220

    fun <T> snappySpring(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

    fun <T> tactileSpring(): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = AppMotion.snapSpring.dampingRatio,
            stiffness = AppMotion.snapSpring.stiffness,
        )

    fun <T> gentleSpring(): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = AppMotion.snapSpring.dampingRatio,
            stiffness = AppMotion.snapSpring.stiffness,
        )

    fun <T> playfulSpring(): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = AppMotion.snapSpring.dampingRatio,
            stiffness = AppMotion.snapSpring.stiffness,
        )

    fun sizeSpring(): FiniteAnimationSpec<IntSize> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    @Composable
    fun rememberPrefersReducedMotion(): Boolean = rememberReducedMotion()

    /** Crisp vertical expand + fade enter transition for collapsible panels. */
    fun panelEnter(reducedMotion: Boolean = false): EnterTransition =
        if (reducedMotion) {
            fadeIn(tween(100))
        } else {
            fadeIn(tween(150, easing = AppMotion.easeOut)) +
                expandVertically(tween(PanelMs, easing = AppMotion.easeOut))
        }

    /** Faster vertical shrink + fade exit transition matching [panelEnter]. */
    fun panelExit(reducedMotion: Boolean = false): ExitTransition =
        if (reducedMotion) {
            fadeOut(tween(90))
        } else {
            fadeOut(tween(ExitMs)) +
                shrinkVertically(tween(150, easing = AppMotion.easeOut))
        }

    /**
     * Snappy direction-aware slide + fade (`150ms` enter / `120ms` exit) aligned with
     * [AppMotion.page] for multi-step flows and segmented panes.
     */
    fun <S> AnimatedContentTransitionScope<S>.directionalStep(
        forward: Boolean,
        reducedMotion: Boolean = false,
    ): ContentTransform {
        if (reducedMotion) {
            return (fadeIn(tween(100)) togetherWith fadeOut(tween(90)))
                .using(SizeTransform(clip = false))
        }
        val sign = if (forward) 1 else -1
        val enter = slideInHorizontally(tween(150, easing = AppMotion.easeOut)) { sign * it / 8 } +
            fadeIn(tween(150, easing = AppMotion.easeOut))
        val exit = slideOutHorizontally(tween(120, easing = AppMotion.easeOut)) { -sign * it / 14 } +
            fadeOut(tween(120))
        return (enter togetherWith exit).using(
            SizeTransform(clip = false) { _, _ -> sizeSpring() }
        )
    }

    /**
     * Crisp crossfade (`150ms` enter / `120ms` exit) matching [AppMotion.page] for swapping
     * views inside a single container without sluggish scale lag.
     */
    fun viewSwap(reducedMotion: Boolean = false): ContentTransform {
        if (reducedMotion) {
            return ContentTransform(
                targetContentEnter = fadeIn(tween(100)),
                initialContentExit = fadeOut(tween(90)),
                sizeTransform = SizeTransform(clip = false),
            )
        }
        return ContentTransform(
            targetContentEnter = fadeIn(tween(150, easing = AppMotion.easeOut)),
            initialContentExit = fadeOut(tween(120)),
            sizeTransform = SizeTransform(clip = false) { _, _ -> sizeSpring() },
        )
    }
}

/**
 * Tactile press scale (`0.97f` on press) rendered purely on the GPU layer (`graphicsLayer`)
 * using [AppMotion.pressSpring] so every press interaction matches existing MkwKit controls.
 */
internal fun Modifier.mkwPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
): Modifier = composed {
    val reducedMotion = rememberReducedMotion()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !reducedMotion) pressedScale else 1f,
        animationSpec = AppMotion.pressSpring,
        label = "mkwPressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Retained for backward compatibility. Existing screens render immediately without staggered
 * entrance delays, so this modifier intentionally acts as a zero-delay no-op.
 */
internal fun Modifier.mkwChoreographedEnter(
    @Suppress("UNUSED_PARAMETER") step: Int,
    @Suppress("UNUSED_PARAMETER") key: Any? = Unit,
): Modifier = this
