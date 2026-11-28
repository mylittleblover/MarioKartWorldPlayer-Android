package com.mylittleblover.mkwplayer.routes

import java.io.File
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Published route sequences are data, never inferred from map proximity. */
class OfficialRouteAssetsTest {
    private fun asset(name: String): JSONObject {
        val file = File("src/main/assets/$name")
        assertTrue(file.isFile, "The app must bundle the source-backed $name")
        return JSONObject(file.readText())
    }
    private fun routes(): List<JSONObject> {
        val rows = asset("official_routes.json").getJSONArray("routes")
        return (0 until rows.length()).map(rows::getJSONObject)
    }
    @Test fun onlyTheEightPublishedCupsAndFourteenPublishedRalliesAreBundled() {
        val rows = routes()
        assertEquals(22, rows.size)
        assertEquals(22, rows.map { it.getString("id") }.toSet().size)
        assertEquals(8, rows.count { it.getString("kind") == "grand_prix" })
        assertEquals(14, rows.count { it.getString("kind") == "rally" })
        for (row in rows) assertEquals(if (row.getString("kind") == "grand_prix") 4 else 6, row.getJSONArray("stops").length())
    }
    @Test fun everyPublishedStopHasAnActualMapAnchorAndItsOwnOccurrence() {
        val anchors = asset("map_anchors.json").getJSONArray("stages")
        val known = (0 until anchors.length()).map { anchors.getJSONObject(it).getString("code").removeSuffix("_00") }.toSet()
        for (route in routes()) {
            val stops = route.getJSONArray("stops")
            for (index in 0 until stops.length()) {
                val stop = stops.getJSONObject(index)
                assertEquals(index + 1, stop.getInt("ordinal"))
                assertTrue(stop.getString("stage") in known)
            }
        }
    }
    @Test fun knownOfficialOrdersArePreserved() {
        val rows = routes().associateBy { it.getString("id") }
        fun stops(id: String): List<String> {
            val values = rows.getValue(id).getJSONArray("stops")
            return (0 until values.length()).map { values.getJSONObject(it).getString("stage") }
        }
        assertEquals(listOf("FirstCircuit", "WestCity", "Coaster", "DonkeyKong"), stops("mushroom_cup"))
        assertEquals(listOf("BigTree", "SFCMarioCircuit", "Central", "Rainbow"), stops("special_cup"))
        assertEquals(listOf("DesertHills", "FirstCircuit", "ChocoMountain", "MooMooFarm", "SFCMarioCircuit", "BigTree"), stops("golden_rally"))
        assertEquals(listOf("FirstCircuit", "WarioStadium", "ToadFactory", "SFCMarioCircuit", "BigTree", "Cinema"), stops("feather_rally"))
        assertEquals(listOf("StormClouds", "LagoonCity", "RaftingRiver", "Cave", "SnowMountain", "IceBuildings"), stops("lucky_cat_rally"))
    }
    @Test fun onlySpecialCupRequiresItsEntitlement() {
        assertEquals(listOf("special_cup"), routes().filter { it.getBoolean("requiresSpecialUnlock") }.map { it.getString("id") })
    }
    @Test fun publishedRoutesHaveFourLanguageNamesAndSourceProvenance() {
        val root = asset("official_routes.json")
        assertEquals(5544799, root.getInt("sourceRevision"))
        assertEquals("ordered_stops_only", root.getString("geometry"))
        assertTrue(root.getString("sourceSnapshotSha256").matches(Regex("[0-9a-f]{64}")))
        for (row in routes()) {
            for (language in listOf("en", "zh-Hans", "zh-Hant", "ja")) assertTrue(row.getJSONObject("names").getString(language).isNotBlank())
            assertTrue(row.getString("sourceUrl").startsWith("https://www.mariowiki.com/"))
        }
    }
}
