package com.mylittleblover.mkwplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The design tokens from the v3.0 handoff pack.
 *
 * The pack marks these `design-candidate-awaiting-user-approval`, and that status is recorded here
 * rather than dropped: they are a starting point the user has not signed off, and a later change of
 * mind must not look like a regression against a settled design.
 *
 * Values are copied rather than invented. The pack also says scene controls and track metadata must
 * be verified rather than read from concept images, which is why nothing here describes behaviour --
 * only measurements and colours.
 */
internal object Tokens {

    /** The pack's own status line, kept so the code does not imply approval. */
    const val STATUS = "design-candidate-awaiting-user-approval"

    object Palette {
        val navy900 = Color(0xFF153753)
        val navy700 = Color(0xFF246582)
        val yellow500 = Color(0xFFFFD13B)
        val orange500 = Color(0xFFF07838)
        val cyan400 = Color(0xFF56BFD2)
        val cream100 = Color(0xFFF7F1DD)
        val ink900 = Color(0xFF202935)
        val white = Color(0xFFFFFFFF)
        val success = Color(0xFF62C58B)
        val warning = Color(0xFFF5AD43)
        val error = Color(0xFFF16A6A)
        val scrim = Color(0xFF091A2B)

        /**
         * Text-safe variants of the three feedback colours.
         *
         * These are NOT from the pack, and they exist because the pack's own values fail its own
         * contrast requirement when used as text. Measured on the cream background:
         *
         *   error   #F16A6A  2.65:1
         *   success #62C58B  1.88:1
         *   warning #F5AD43  1.70:1
         *
         * Against a 4.5:1 minimum that is unreadable for normal-size text, and a warning nobody can
         * read is not a warning. The originals stay for fills, borders and icons, where the ratio rule
         * does not apply to the shape the colour paints.
         *
         * These darker variants keep each hue recognisable and reach the minimum.
         */
        val successText = Color(0xFF1F7A4C)
        val warningText = Color(0xFF8A5B00)
        val errorText = Color(0xFFB3261E)
    }

    object Spacing {
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
    }

    object Radius {
        val sm = 12.dp
        val md = 16.dp
        val lg = 24.dp
        val pill = 999.dp
    }

    object Type {
        val pageTitle = 24.sp
        val sectionTitle = 20.sp
        val body = 16.sp
        val secondary = 14.sp
        val caption = 12.sp
    }

    object Component {
        /**
         * Minimum touch target.
         *
         * Kept as a named value because the pack requires 48 dp and a hand-picked 40 looks fine until
         * someone tries it with gloves on.
         */
        val touchTargetMin = 48.dp
        val mainPlaybackButton = 64.dp
        val miniPlayerHeightMin = 68.dp
        val miniPlayerHeightMax = 80.dp
        val miniPlayerArtwork = 44.dp
        val mainTabBarHeight = 64.dp
        val topBarBaseHeight = 56.dp
        val routeNodeMinHeight = 76.dp
    }

    object Breakpoint {
        /** Widths strictly below this are compact. */
        val compactMaxExclusive = 600.dp

        /** Widths strictly below this are medium. */
        val mediumMaxExclusive = 840.dp
    }

    /**
     * Contrast minimums, from the pack.
     *
     * These are ratios rather than colours, so a palette change has to keep meeting them.
     */
    object Contrast {
        const val normalTextMinimum = 4.5
        const val largeTextMinimum = 3.0
    }
}
