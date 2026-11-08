package com.mylittleblover.mkwplayer.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrast is a real defect that screenshots hide.
 *
 * A palette can look deliberate and still be unreadable for someone with low vision, so the pack's
 * 4.5:1 and 3:1 minimums are checked against the actual token pairs rather than trusted.
 */
class ContrastTest {

    // ---------------------------------------------------------------- the formula

    @Test
    fun blackOnWhiteIsVeryHighContrast() {
        // Not 21:1 -- that is pure black on pure white, and the ink token is #202935, a deliberate
        // near-black. Asserting 21 here was asserting a property the palette does not have.
        val ratio = Contrast.ratio(Tokens.Palette.ink900, Tokens.Palette.white)
        assertTrue(ratio > 12.0, "near-black on white should be very high, got $ratio")
    }

    @Test
    fun aColourAgainstItselfIsOne() {
        assertEquals(1.0, Contrast.ratio(Tokens.Palette.navy900, Tokens.Palette.navy900), 1e-9)
    }

    @Test
    fun theRatioIsSymmetric() {
        val forward = Contrast.ratio(Tokens.Palette.white, Tokens.Palette.navy900)
        val backward = Contrast.ratio(Tokens.Palette.navy900, Tokens.Palette.white)
        assertEquals(forward, backward, 1e-9, "lighter/darker is symmetric")
    }

    @Test
    fun luminanceOfWhiteIsOne() {
        assertEquals(1.0, Contrast.luminance(Tokens.Palette.white), 1e-6)
    }

    @Test
    fun luminanceOfBlackIsZero() {
        // Compose's Color, not a token: this is the formula's endpoint, not a palette member.
        assertEquals(0.0, Contrast.luminance(androidx.compose.ui.graphics.Color(0xFF000000)), 1e-6)
    }

    // ---------------------------------------------------------------- the real pairs

    @Test
    fun bodyTextOnCreamIsReadable() {
        // The most used pair in the design: ink on cream.
        assertTrue(
            Contrast.passesNormalText(Tokens.Palette.ink900, Tokens.Palette.cream100),
            "ink on cream is ${Contrast.describe(Tokens.Palette.ink900, Tokens.Palette.cream100)}",
        )
    }

    @Test
    fun whiteOnNavyIsReadable() {
        assertTrue(
            Contrast.passesNormalText(Tokens.Palette.white, Tokens.Palette.navy900),
            "white on navy is ${Contrast.describe(Tokens.Palette.white, Tokens.Palette.navy900)}",
        )
    }

    @Test
    fun inkOnYellowIsReadable() {
        // The brand accent as a button background.
        val ratio = Contrast.ratio(Tokens.Palette.ink900, Tokens.Palette.yellow500)
        assertTrue(ratio >= Tokens.Contrast.largeTextMinimum, "ink on yellow is $ratio")
    }

    @Test
    fun theFeedbackColoursAsTextFailTheRequirement() {
        // Not an aspiration: this is what the pack's own values measure. Recording it means the
        // reason for the text-safe variants is a fact in the test suite, not a comment someone can
        // delete.
        assertTrue(!Contrast.passesNormalText(Tokens.Palette.error, Tokens.Palette.cream100))
        assertTrue(!Contrast.passesNormalText(Tokens.Palette.success, Tokens.Palette.cream100))
        assertTrue(!Contrast.passesNormalText(Tokens.Palette.warning, Tokens.Palette.cream100))
    }

    @Test
    fun theTextSafeVariantsAreReadableOnCream() {
        assertTrue(
            Contrast.passesNormalText(Tokens.Palette.errorText, Tokens.Palette.cream100),
            "error text is ${Contrast.describe(Tokens.Palette.errorText, Tokens.Palette.cream100)}",
        )
        assertTrue(
            Contrast.passesNormalText(Tokens.Palette.successText, Tokens.Palette.cream100),
            "success text is ${Contrast.describe(Tokens.Palette.successText, Tokens.Palette.cream100)}",
        )
        assertTrue(
            Contrast.passesNormalText(Tokens.Palette.warningText, Tokens.Palette.cream100),
            "warning text is ${Contrast.describe(Tokens.Palette.warningText, Tokens.Palette.cream100)}",
        )
    }

    @Test
    fun theTextSafeVariantsKeepTheirHue() {
        // A 'readable' colour that turns grey would make error and success indistinguishable.
        assertTrue(Tokens.Palette.errorText.red > Tokens.Palette.errorText.green, "error stays reddish")
        assertTrue(Tokens.Palette.successText.green > Tokens.Palette.successText.red, "success stays greenish")
        assertTrue(Tokens.Palette.warningText.red > Tokens.Palette.warningText.blue, "warning stays warm")
    }

    @Test
    fun theUnknownContrastIsNotAssumed() {
        // The pack does not say which pairs each colour is used against, so this records the ones the
        // current screen actually uses rather than claiming the whole palette is safe.
        val pairs = listOf(
            Tokens.Palette.ink900 to Tokens.Palette.cream100,
            Tokens.Palette.white to Tokens.Palette.navy900,
            Tokens.Palette.errorText to Tokens.Palette.cream100,
            Tokens.Palette.successText to Tokens.Palette.cream100,
            Tokens.Palette.warningText to Tokens.Palette.cream100,
        )
        for ((foreground, background) in pairs) {
            assertTrue(
                Contrast.passesNormalText(foreground, background),
                "a pair in use fails: ${Contrast.describe(foreground, background)}",
            )
        }
    }

    @Test
    fun describeRoundsToSomethingReadable() {
        val text = Contrast.describe(Tokens.Palette.ink900, Tokens.Palette.cream100)
        assertTrue(text.endsWith(":1"), text)
        assertTrue(text.contains("."), "one decimal place, not a raw double: $text")
    }

    // ---------------------------------------------------------------- the tokens themselves

    @Test
    fun theTokenStatusIsNotForgotten() {
        // The pack marks the design as awaiting approval; code must not imply it is settled.
        assertEquals("design-candidate-awaiting-user-approval", Tokens.STATUS)
    }

    @Test
    fun theTouchTargetMeetsTheAndroidMinimum() {
        // 48 dp is the platform minimum; a smaller value here would be a real defect on every button.
        assertTrue(Tokens.Component.touchTargetMin.value >= 48f, "touch targets must be at least 48 dp")
    }

    @Test
    fun theMiniPlayerFitsBetweenItsBounds() {
        assertTrue(Tokens.Component.miniPlayerHeightMin.value <= Tokens.Component.miniPlayerHeightMax.value)
        assertTrue(Tokens.Component.miniPlayerArtwork.value <= Tokens.Component.miniPlayerHeightMin.value)
    }

    @Test
    fun theSpacingScaleIsAscending() {
        val scale = listOf(
            Tokens.Spacing.xxs.value,
            Tokens.Spacing.xs.value,
            Tokens.Spacing.sm.value,
            Tokens.Spacing.md.value,
            Tokens.Spacing.lg.value,
            Tokens.Spacing.xl.value,
        )
        assertEquals(scale.sorted(), scale, "a spacing scale that is not ascending is a typo, not a design")
    }

    @Test
    fun theBreakpointsAreOrdered() {
        assertTrue(
            Tokens.Breakpoint.compactMaxExclusive.value < Tokens.Breakpoint.mediumMaxExclusive.value,
            "compact must end before medium",
        )
    }

    @Test
    fun theTypeScaleIsAscending() {
        val scale = listOf(
            Tokens.Type.caption.value,
            Tokens.Type.secondary.value,
            Tokens.Type.body.value,
            Tokens.Type.sectionTitle.value,
            Tokens.Type.pageTitle.value,
        )
        assertEquals(scale.sorted(), scale)
    }
}
