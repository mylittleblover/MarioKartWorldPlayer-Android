package com.mylittleblover.mkwplayer.library

import com.mylittleblover.mkwplayer.routes.LocalizedNames
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import java.io.File
import org.json.JSONObject
import kotlin.test.*

class BrowseCatalogTest {
    private fun catalog(namesText: String = File("src/main/assets/localized_names.json").readText()): BrowseCatalog? {
        val anchors = assertNotNull(MapAnchors.parse(File("src/main/assets/map_anchors.json").readText()))
        val routes = assertNotNull(OfficialRouteCatalog.parse(File("src/main/assets/official_routes.json").readText(), anchors.stages.map { it.code }.toSet()))
        val names = assertNotNull(LocalizedNames.parse(namesText))
        return BrowseCatalog.create(routes, names, anchors)
    }
    @Test fun realCourseCatalogPreservesPublishedOrderAndTheActualUnlockBoundary() {
        val catalog = assertNotNull(catalog())
        assertEquals(30, catalog.courses(true).size)
        assertEquals(29, catalog.courses(false).size)
        assertEquals(listOf("FirstCircuit", "WestCity", "Coaster", "DonkeyKong"), catalog.courses(true).take(4))
        assertTrue(catalog.courses(false).containsAll(listOf("BigTree", "SFCMarioCircuit", "Central")))
        assertFalse("Rainbow" in catalog.courses(false))
        assertNull(catalog.route("special_cup", false))
        assertNotNull(catalog.route("special_cup", true))
        assertFailsWith<UnsupportedOperationException> { (catalog.courses(true) as MutableList).clear() }
    }
    @Test fun fullwidthEnglishChineseJapaneseAndInternalAliasesResolveToStableCourseIds() {
        val catalog = assertNotNull(catalog())
        assertTrue("FirstCircuit" in catalog.searchCourses("ｍａｒｉｏ", true))
        assertTrue("FirstCircuit" in catalog.searchCourses("马力欧兄弟", true))
        assertTrue("FirstCircuit" in catalog.searchCourses("瑪利歐兄弟", true))
        assertTrue("FirstCircuit" in catalog.searchCourses("マリオブラザーズ", true))
        assertEquals(listOf("IceBuildings"), catalog.searchCourses("IceBuildings", true))
        assertEquals(emptyList(), catalog.searchCourses("nonexistent music", true))
        assertEquals(emptyList(), catalog.searchCourses("Rainbow", false))
        assertEquals(catalog.courses(false), catalog.searchCourses("", false))
    }
    @Test fun searchNeverReordersResultsByTheDisplayedLanguage() {
        val catalog = assertNotNull(catalog())
        val results = catalog.searchCourses("mario", true)
        assertEquals(catalog.courses(true).filter { it in results }, results)
        assertEquals("Mario Bros. Circuit", catalog.displayName("FirstCircuit", "en"))
        assertEquals("马力欧兄弟赛道", catalog.displayName("FirstCircuit", "zh-Hans"))
        assertEquals(results, catalog.searchCourses("mario", true))
    }
    @Test fun incompleteIdentityMetadataIsNotSilentlyFilledWithAnUnrelatedStage() {
        val json = JSONObject(File("src/main/assets/localized_names.json").readText())
        val values = json.getJSONArray("entries")
        val index = (0 until values.length()).first { values.getJSONObject(it).getString("id") == "FirstCircuit" }
        values.remove(index)
        assertNull(catalog(json.toString()))
    }
    @Test fun officialGroupsAreSearchableWithoutLeakingLockedRoutes() {
        val catalog = assertNotNull(catalog())
        assertEquals(7, catalog.routes(OfficialRouteKind.GRAND_PRIX, false).size)
        assertEquals(8, catalog.routes(OfficialRouteKind.GRAND_PRIX, true).size)
        assertEquals(14, catalog.routes(OfficialRouteKind.RALLY, false).size)
        assertEquals(listOf("mushroom_cup"), catalog.routes(OfficialRouteKind.GRAND_PRIX, false, "キノコ").map { it.id })
        assertEquals(emptyList(), catalog.routes(OfficialRouteKind.GRAND_PRIX, false, "Special Cup"))
    }

    @Test fun supplementaryJapaneseCharactersRemainSearchable() {
        val root = JSONObject(File("src/main/assets/localized_names.json").readText())
        root.getJSONArray("entries").getJSONObject(0).getJSONObject("names").put("ja", "𠮷野のコース")
        assertEquals(listOf("FirstCircuit"), assertNotNull(catalog(root.toString())).searchCourses("𠮷", true))
    }

}
