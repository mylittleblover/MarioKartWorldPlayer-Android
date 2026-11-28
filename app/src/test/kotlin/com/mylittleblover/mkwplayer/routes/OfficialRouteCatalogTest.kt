package com.mylittleblover.mkwplayer.routes

import java.io.File
import org.json.JSONObject
import kotlin.test.*

class OfficialRouteCatalogTest {
    private val source get() = File("src/main/assets/official_routes.json").readText()
    private val anchors: Set<String> get() {
        val rows = JSONObject(File("src/main/assets/map_anchors.json").readText()).getJSONArray("stages")
        return (0 until rows.length()).map { rows.getJSONObject(it).getString("code") }.toSet()
    }
    private fun parse(text: String = source) = assertNotNull(OfficialRouteCatalog.parse(text, anchors))
    @Test fun firstAndLastStopsAndOccurrencesHaveExplicitStableIdentities() {
        val route = assertNotNull(parse().route("mushroom_cup"))
        assertEquals("FirstCircuit", route.start.stageCode)
        assertEquals("DonkeyKong", route.end.stageCode)
        assertEquals("mushroom_cup:2", route.stops[1].id)
        assertNull(parse().route("future_route"))
    }
    @Test fun officialCollectionsCannotBeEditedThroughMutableCasts() {
        val catalog = parse()
        assertFailsWith<UnsupportedOperationException> { (catalog.routes as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (catalog.routes.first().stops as MutableList).clear() }
    }
    @Test fun repeatedStageVisitsRetainSeparateOccurrences() {
        val json = JSONObject(source)
        val stops = json.getJSONArray("routes").getJSONObject(0).getJSONArray("stops")
        stops.getJSONObject(1).put("stage", "FirstCircuit")
        val route = parse(json.toString()).routes.first()
        assertEquals(route.stops[0].stageCode, route.stops[1].stageCode)
        assertNotEquals(route.stops[0].id, route.stops[1].id)
        assertEquals(4, route.stops.size)
    }
    @Test fun missingEntitlementKeepsSpecialClosedButNormalRallyCoursesVisible() {
        val catalog = parse()
        for (unlocked in listOf(null, false)) {
            assertEquals(21, catalog.visible(unlocked).size)
            assertFalse(catalog.visible(unlocked).any { it.id == "special_cup" })
            assertFalse("Rainbow" in catalog.stageCodes(unlocked))
            assertTrue(catalog.stageCodes(unlocked).containsAll(listOf("BigTree", "SFCMarioCircuit", "Central")))
        }
        assertEquals(22, catalog.visible(true).size)
        assertEquals(30, catalog.stageCodes(true).size)
    }
    @Test fun translatedNamesDoNotChangeIdentityOrStopOrder() {
        val route = assertNotNull(parse().route("mushroom_cup"))
        assertEquals("蘑菇杯", route.displayName("zh-CN"))
        assertEquals("蘑菇盃", route.displayName("zh-HK"))
        assertEquals("蘑菇盃", route.displayName("zh-MO"))
        assertEquals("蘑菇杯", route.displayName("zh-SG"))
        assertEquals("キノコカップ", route.displayName("ja-JP"))
        assertEquals("Mushroom Cup", route.displayName("fr-FR"))
        assertEquals("FirstCircuit", route.start.stageCode)
    }
    @Test fun unknownStagesAndMalformedOrdinalsAreRejected() {
        fun corrupt(change: (JSONObject) -> Unit): String = JSONObject(source).also(change).toString()
        assertNull(OfficialRouteCatalog.parse(corrupt {
            it.getJSONArray("routes").getJSONObject(0).getJSONArray("stops").getJSONObject(0).put("stage", "ImaginaryRoad")
        }, anchors))
        assertNull(OfficialRouteCatalog.parse(corrupt {
            it.getJSONArray("routes").getJSONObject(0).getJSONArray("stops").getJSONObject(0).put("ordinal", 9)
        }, anchors))
        assertNull(OfficialRouteCatalog.parse(corrupt {
            it.getJSONArray("routes").getJSONObject(0).put("kind", "announced_future_route")
        }, anchors))
    }
    @Test fun duplicatesBadProvenanceAndAnUnprotectedSpecialRouteAreRejected() {
        val duplicate = JSONObject(source)
        duplicate.getJSONArray("routes").getJSONObject(1).put("id", "mushroom_cup")
        assertNull(OfficialRouteCatalog.parse(duplicate.toString(), anchors))
        assertNull(OfficialRouteCatalog.parse(JSONObject(source).put("geometry", "inferred_roads").toString(), anchors))
        assertNull(OfficialRouteCatalog.parse(JSONObject(source).put("sourceSnapshotSha256", "unknown").toString(), anchors))
        val special = JSONObject(source)
        val rows = special.getJSONArray("routes")
        (0 until rows.length()).map(rows::getJSONObject).single { it.getString("id") == "special_cup" }.put("requiresSpecialUnlock", false)
        assertNull(OfficialRouteCatalog.parse(special.toString(), anchors))
    }
}
