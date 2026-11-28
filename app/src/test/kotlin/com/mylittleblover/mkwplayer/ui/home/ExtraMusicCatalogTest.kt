package com.mylittleblover.mkwplayer.ui.home

import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtraMusicCatalogTest {

    @Test
    fun specialCupHiddenIdsAreConcealedWhenLockedAndStaffCreditsRequiresItsOwnUnlock() {
        val specialOnlyIds = listOf(
            "opening_special",
            "victory_lap_rainbow",
            "results_rainbow",
            "special_cup_appears",
            "rainbow_fl_fanfare",
            "nm_special_092",
            "nm_special_111",
        )
        val staffCreditsIds = listOf(
            "staff_credits",
            "nm_special_119",
        )
        assertEquals((specialOnlyIds + staffCreditsIds).toSet(), ExtraMusicCatalog.HIDDEN_UNTIL_SPECIAL_UNLOCKED_IDS)
        assertEquals(staffCreditsIds.toSet(), ExtraMusicCatalog.STAFF_CREDITS_TRACK_IDS)

        for (id in specialOnlyIds) {
            assertFalse(ExtraMusicCatalog.isVisible(id, specialUnlocked = false), "$id must be hidden when locked")
            assertFalse(
                ExtraMusicCatalog.isVisible(id, specialUnlocked = false, staffCreditsUnlocked = true),
                "$id must remain hidden when only staffCreditsUnlocked is true",
            )
            assertTrue(ExtraMusicCatalog.isVisible(id, specialUnlocked = true), "$id must be visible when Special Cup unlocked")
            assertTrue(ExtraMusicCatalog.isVisible(id, specialUnlocked = true, staffCreditsUnlocked = true))
        }

        // Staff Credits (#25) tracks stay hidden when staffCreditsUnlocked = false, even when specialUnlocked = true.
        for (id in staffCreditsIds) {
            assertFalse(
                ExtraMusicCatalog.isVisible(id, specialUnlocked = false),
                "$id must be hidden when locked",
            )
            assertFalse(
                ExtraMusicCatalog.isVisible(id, specialUnlocked = true),
                "$id must remain hidden with default staffCreditsUnlocked = false even when Special Cup is unlocked",
            )
            assertFalse(
                ExtraMusicCatalog.isVisible(id, specialUnlocked = true, staffCreditsUnlocked = false),
                "$id must remain hidden while staffCreditsUnlocked = false",
            )
            assertTrue(
                ExtraMusicCatalog.isVisible(id, specialUnlocked = false, staffCreditsUnlocked = true),
                "$id must be visible once staffCreditsUnlocked = true",
            )
            assertTrue(
                ExtraMusicCatalog.isVisible(id, specialUnlocked = true, staffCreditsUnlocked = true),
                "$id must be visible when both Special Cup and Staff Credits are unlocked",
            )
        }
    }

    @Test
    fun knockoutSpecialCoursesRemainVisibleWhenLocked() {
        // Acorn Heights (BigTree) & Mario Circuit (SFCMarioCircuit) also appear in Knockout Tour.
        for (id in listOf("BigTree", "SFCMarioCircuit", "opening_mushroom", "opening_knockout", "title_main")) {
            assertTrue(ExtraMusicCatalog.isVisible(id, specialUnlocked = false), "$id must remain visible when locked")
            assertTrue(ExtraMusicCatalog.isVisible(id, specialUnlocked = true))
            assertTrue(ExtraMusicCatalog.isVisible(id, specialUnlocked = true, staffCreditsUnlocked = true))
        }
    }

    @Test
    fun extraIdFromEntryKeyParsesBothExtraAndOpeningKeys() {
        assertEquals("special_cup_appears", ExtraMusicCatalog.extraIdFromEntryKey("extra:special_cup_appears"))
        assertEquals("opening_special", ExtraMusicCatalog.extraIdFromEntryKey("official:special_cup:opening:opening_special"))
        assertEquals("opening_knockout", ExtraMusicCatalog.extraIdFromEntryKey("official:golden_rally:opening:opening_knockout"))
        assertNull(ExtraMusicCatalog.extraIdFromEntryKey("medley:Mushroom"))
    }

    @Test
    fun openingIdForOfficialRouteCoversAllGrandPrixAndKnockoutRallies() {
        val gpExpected = mapOf(
            "mushroom_cup" to "opening_mushroom",
            "flower_cup" to "opening_flower",
            "star_cup" to "opening_star",
            "shell_cup" to "opening_shell",
            "banana_cup" to "opening_banana",
            "leaf_cup" to "opening_leaf",
            "lightning_cup" to "opening_lightning",
            "special_cup" to "opening_special",
        )
        gpExpected.forEach { (routeId, openingId) ->
            assertEquals(openingId, ExtraMusicCatalog.openingIdForOfficialRoute(routeId, OfficialRouteKind.GRAND_PRIX))
        }
        assertNull(ExtraMusicCatalog.openingIdForOfficialRoute("unknown_cup", OfficialRouteKind.GRAND_PRIX))
        assertEquals("opening_knockout", ExtraMusicCatalog.openingIdForOfficialRoute("golden_rally", OfficialRouteKind.RALLY))
        assertEquals("opening_knockout", ExtraMusicCatalog.openingIdForOfficialRoute("turnip_rally", OfficialRouteKind.RALLY))
    }
}
