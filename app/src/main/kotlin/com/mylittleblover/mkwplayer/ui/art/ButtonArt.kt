package com.mylittleblover.mkwplayer.ui.art

import com.mylittleblover.mkwplayer.R

/**
 * The nine-slice button frames, taken from the game's own Layout sprites.
 *
 * The frames are 643x119 with a pill-shaped plate and two small bolts, which stretch cleanly at any
 * width once the rounded ends are sliced. Using the game's frame instead of a Material one is not
 * decoration: it is the difference between a player reading the screen as "this belongs to the game I
 * am listening to" and reading it as a generic app.
 *
 * The sprite names in the unpacked assets carry the game's own numbering (`DialogBtnS_01_s`,
 * `DialogBtnS_03_s`, ...), which says nothing about which is idle and which is pressed. The mapping
 * below was established by looking at them: `_01` is a blank white plate, `_03` adds a glow, `_04` is
 * the dark pressed plate, `_05` is the flat disabled one. Naming them by state here means a future
 * reader of the res folder does not have to rediscover that.
 */
internal object ButtonArt {

    // Declared `val`, not `const val`: the constant evaluator tries to fold `R.drawable.x` and the R
    // class in a unit-test compilation is a stub, which crashes the compiler rather than failing a
    // test. A resource id is not a compile-time constant anyway.

    /** The frame for a resting button. */
    val IDLE = R.drawable.button_idle

    /** The frame for a focused button, which the game draws as a lit edge. */
    val FOCUSED = R.drawable.button_focused

    /** The frame for a press in progress. */
    val PRESSED = R.drawable.button_pressed

    /**
     * The frame for a button that cannot be pressed.
     *
     * Kept as a distinct asset rather than an alpha applied to the idle frame, because a dimmed normal
     * button still looks like a button and this one does not.
     */
    val DISABLED = R.drawable.button_disabled

    /**
     * Slice inset, in source pixels.
     *
     * The plate's rounded end is about 60 px of the 119 px height, so a 60 px inset on the left and
     * right keeps the curve undistorted while the middle stretches. Slicing at the wrong inset is the
     * classic nine-slice bug: the rounded end squashes at narrow widths and the plate grows a bulge at
     * wide ones.
     */
    const val SLICE_INSET = 60

    /** Frame aspect, used to size a button without guessing. */
    const val FRAME_WIDTH = 643

    /** Frame height, so a caller can derive the slice inset rather than hardcoding it twice. */
    const val FRAME_HEIGHT = 119
}
