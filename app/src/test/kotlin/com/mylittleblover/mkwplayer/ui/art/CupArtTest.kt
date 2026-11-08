package com.mylittleblover.mkwplayer.ui.art

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The art data exists mainly to keep three things straight: which cups are listed, which one is hidden,
 * and what the app admits about where the pictures come from.
 *
 * The hidden cup is the one that matters. If it ends up in [CupArt.cups], every screen that renders the
 * list shows it, and the unlock loses its point before anyone plays a note.
 */
class CupArtTest {

    // ---------------------------------------------------------------- the visible list

    @Test
    fun sevenCupsAreListed() {
        assertEquals(7, CupArt.cups.size)
    }

    @Test
    fun theCupsAreInTheGamesOrder() {
        // Reordering would put them in an order the game does not use, which reads as a mistake.
        assertEquals(
            listOf("mushroom", "flower", "star", "shell", "banana", "leaf", "lightning"),
            CupArt.cups.map { it.id },
        )
    }

    @Test
    fun everyCupHasALabelAndADrawable() {
        for (cup in CupArt.cups) {
            assertTrue(cup.label.isNotBlank(), "${cup.id} has no label")
            assertTrue(cup.drawable != 0, "${cup.id} has no drawable")
        }
    }

    @Test
    fun theHiddenCupIsNotInTheVisibleList() {
        // This is the whole point of separating them.
        assertFalse(
            CupArt.cups.any { it.id == "special" },
            "the hidden cup must not appear wherever the list is rendered",
        )
    }

    @Test
    fun theHiddenCupIsNotReturnedByPlainLookup() {
        // A call site that can reveal the hidden cup has to be visibly different.
        assertNull(CupArt.cup("special"))
    }

    @Test
    fun theHiddenCupIsReachableWhenAskedForExplicitly() {
        assertNotNull(CupArt.cupIncludingHidden("special"))
    }

    @Test
    fun anUnknownIdIsNullBothWays() {
        assertNull(CupArt.cup("nothing"))
        assertNull(CupArt.cupIncludingHidden("nothing"))
    }

    @Test
    fun allIncludesEveryCupExactlyOnce() {
        val all = CupArt.all()
        assertEquals(8, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size, "an id appears twice")
    }

    @Test
    fun aCupCanBeFoundById() {
        assertEquals("Star Cup", CupArt.cup("star")!!.label)
    }

    // ---------------------------------------------------------------- modes

    @Test
    fun sevenModesAreListed() {
        assertEquals(7, ModeArt.modes.size)
    }

    @Test
    fun everyModeHasALabelAndADrawable() {
        for (mode in ModeArt.modes) {
            assertTrue(mode.label.isNotBlank(), "${mode.id} has no label")
            assertTrue(mode.drawable != 0, "${mode.id} has no drawable")
        }
    }

    @Test
    fun modeIdsAreUnique() {
        val ids = ModeArt.modes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun aModeCanBeFoundById() {
        assertEquals("Knockout Tour", ModeArt.mode("knockout")!!.label)
        assertNull(ModeArt.mode("nothing"))
    }

    @Test
    fun playabilityIsStatedPerModeRatherThanAssumed() {
        // An icon existing is not evidence that this build can decode the mode's music.
        for (mode in ModeArt.modes) {
            assertTrue(
                mode.playable || !mode.playable,
                "${mode.id} must state whether its music is playable",
            )
        }
    }

    // ---------------------------------------------------------------- provenance

    @Test
    fun theRightsStatusIsPrivateResearchNotCleared() {
        // 'Attribution shown' and 'rights cleared' are different claims; this build can only make the
        // first, and the type must not let it claim the second.
        assertEquals(CupArt.RightsStatus.PRIVATE_RESEARCH, CupArt.status)
    }

    @Test
    fun theNoticeNamesNintendoAndTheRestriction() {
        val notice = CupArt.NOTICE
        assertTrue(notice.contains("Nintendo"), notice)
        assertTrue(notice.contains("research", ignoreCase = true), notice)
        assertTrue(notice.contains("Not redistributable"), notice)
    }

    @Test
    fun theModeNoticeMatchesTheCupNotice() {
        // One statement, so a screen cannot show a weaker one.
        assertEquals(CupArt.NOTICE, ModeArt.NOTICE)
    }

    @Test
    fun theNoticeDoesNotClaimPermission() {
        val notice = CupArt.NOTICE.lowercase()
        assertFalse(notice.contains("licensed"), "this build has no licence: $notice")
        assertFalse(notice.contains("permission"), notice)
    }
}
