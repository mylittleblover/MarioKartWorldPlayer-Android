package com.mylittleblover.mkwplayer.ui

import androidx.compose.ui.graphics.Color

/**
 * Contrast checking, because a palette that looks fine can still fail to be readable.
 *
 * The pack sets 4.5:1 for normal text and 3:1 for large text. Those are numbers a reviewer can check,
 * so they are checked here rather than assumed: a colour pair that fails is a real accessibility
 * defect, and it is invisible in a screenshot.
 *
 * The relative-luminance formula is WCAG 2.1's: linearise each channel, weight by 0.2126/0.7152/0.0722.
 */
internal object Contrast {

    /** Relative luminance, 0..1. */
    fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /** Contrast ratio between two opaque colours, 1..21. */
    fun ratio(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** True when the pair is readable as normal-size text. */
    fun passesNormalText(foreground: Color, background: Color): Boolean =
        ratio(foreground, background) >= Tokens.Contrast.normalTextMinimum

    /** True when the pair is readable as large text. */
    fun passesLargeText(foreground: Color, background: Color): Boolean =
        ratio(foreground, background) >= Tokens.Contrast.largeTextMinimum

    /** Rounded for display, since a raw double suggests more precision than the check has. */
    fun describe(foreground: Color, background: Color): String =
        "%.1f:1".format(ratio(foreground, background))
}
