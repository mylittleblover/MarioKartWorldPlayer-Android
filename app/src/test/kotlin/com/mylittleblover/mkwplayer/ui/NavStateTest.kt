package com.mylittleblover.mkwplayer.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Navigation is where "the back button went somewhere odd" lives, and no screenshot catches that.
 *
 * The rule that matters most is the null from [NavState.back]: returning a state instead would trap
 * the user inside the app with no way out.
 */
class NavStateTest {

    // ---------------------------------------------------------------- tabs

    @Test
    fun theFourContractTabsExistInOrder() {
        assertEquals(
            listOf("Library", "Map", "Interactive", "Routes"),
            Tab.ordered().map { it.label },
        )
    }

    @Test
    fun theDefaultTabIsTheLibrary() {
        assertEquals(Tab.LIBRARY, Tab.default())
    }

    @Test
    fun selectingATabLeavesTheDetail() {
        val state = NavState().open(NavState.Detail.Course("Coaster"))
        assertTrue(state.canGoBack)

        val switched = state.select(Tab.WORLD_MAP)
        assertEquals(Tab.WORLD_MAP, switched.tab)
        assertNull(switched.detail, "switching tabs must not keep the old tab's detail")
    }

    @Test
    fun selectingTheCurrentTabKeepsTheDetail() {
        // Re-tapping the active tab should not silently close what the user is reading.
        val state = NavState().open(NavState.Detail.Course("Coaster"))
        assertEquals(state, state.select(state.tab))
    }

    // ---------------------------------------------------------------- details

    @Test
    fun aCourseDetailCarriesItsCode() {
        val detail = NavState.Detail.Course("Coaster")
        assertEquals("Coaster", detail.code)
    }

    @Test
    fun openingADetailKeepsTheTab() {
        val state = NavState(tab = Tab.MY_ROUTES).open(NavState.Detail.Flow)
        assertEquals(Tab.MY_ROUTES, state.tab)
        assertEquals(NavState.Detail.Flow, state.detail)
    }

    // ---------------------------------------------------------------- back

    @Test
    fun backFromADetailReturnsToTheTabRoot() {
        val state = NavState(tab = Tab.LIBRARY).open(NavState.Detail.Course("Coaster"))
        val back = state.back()

        assertTrue(back != null, "back from a detail must stay in the app")
        assertEquals(Tab.LIBRARY, back!!.tab)
        assertNull(back.detail)
    }

    @Test
    fun backFromATabRootHasNothingToClose() {
        // null is the signal to let the system finish the activity. Returning a state here would trap
        // the user in the app with no way out.
        assertNull(NavState(tab = Tab.LIBRARY).back())
    }

    @Test
    fun backFromEveryRootTabHasNothingToClose() {
        for (tab in Tab.entries) {
            assertNull(NavState(tab = tab).back(), "$tab should let the system take over")
        }
    }

    @Test
    fun theCanGoBackFlagAgreesWithBack() {
        val root = NavState()
        assertFalse(root.canGoBack)
        assertNull(root.back())

        val detail = root.open(NavState.Detail.Flow)
        assertTrue(detail.canGoBack)
        assertTrue(detail.back() != null)
    }

    @Test
    fun backTwiceExits() {
        // The sequence a user actually performs: open, back, back.
        val opened = NavState().open(NavState.Detail.Course("Coaster"))
        val first = opened.back()
        assertTrue(first != null)
        assertNull(first!!.back(), "the second back must leave the app")
    }

    @Test
    fun openingAnotherDetailFromADetailReplacesRatherThanStacks() {
        // There is no deep stack in this app: a course detail opens another course detail by replacing
        // it, so back always returns to the tab rather than unwinding an invisible history.
        val state = NavState()
            .open(NavState.Detail.Course("Coaster"))
            .open(NavState.Detail.Course("Rainbow"))

        assertEquals(NavState.Detail.Course("Rainbow"), state.detail)
        assertNull(state.back()!!.detail)
    }
}
