package com.mylittleblover.mkwplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped connection table, checked against Mario Kart World v1.8.0 reverse-engineered data
 * (`REPORT_TRACK_CONNECTIONS.md` / `RSDB/Course.Product.180.rstbl` & `CourseAreaGParam.CourseEx`).
 *
 * 202 directed inter-stage edges (137 `InterStageEx1` + 65 `InterStageEx2`), 29 sources,
 * 30 destinations, 96 bidirectional pairs + 10 strictly one-way edges, and Rainbow as sole sink.
 */
class StageConnectionsTest {

    private val catalog: StageConnections.Catalog by lazy {
        val file = File("src/main/assets/stage_connections.json")
        assertTrue("the connection asset must ship with the app", file.exists())
        assertNotNull(StageConnections.parse(file.readText()))
        StageConnections.parse(file.readText())!!
    }

    @Test
    fun `the exported table is intact`() {
        assertEquals(202, catalog.edges.size)
        assertEquals(29, catalog.sources().size)
        assertEquals(137, catalog.edges.count { it.kind == "InterStageEx1" })
        assertEquals(65, catalog.edges.count { it.kind == "InterStageEx2" })
    }

    @Test
    fun `rainbow is entered only from peach stadium`() {
        val entrances = catalog.incoming("Rainbow")
        assertEquals("Rainbow has exactly one entrance", 1, entrances.size)
        assertEquals("Central", entrances.single().from)
        assertEquals("InterStageEx1", entrances.single().kind)
        assertTrue(catalog.connects("Central", "Rainbow"))
        assertTrue(catalog.isOneWay("Central", "Rainbow"))
    }

    @Test
    fun `rainbow is a finish line and never a start`() {
        assertTrue("Rainbow must have no outgoing connection", catalog.outgoing("Rainbow").isEmpty())
        assertTrue("Rainbow must have no neighbours", catalog.neighbours("Rainbow").isEmpty())
        assertTrue("Rainbow must be reported as a sink", catalog.sinks().contains("Rainbow"))
        assertEquals(listOf("Rainbow"), catalog.sinks())
    }

    @Test
    fun `connections are directed and match exact 96 bidirectional plus 10 one-way topology`() {
        val bidirectional = catalog.edges.filter { catalog.isBidirectional(it.from, it.to) }
        val oneWay = catalog.edges.filter { catalog.isOneWay(it.from, it.to) }
        assertEquals(192, bidirectional.size)
        assertEquals(10, oneWay.size)
        assertEquals(
            setOf(
                "Central" to "Rainbow",
                "Coaster" to "KoopaTroopaBeach",
                "DesertHills" to "KoopaTroopaBeach",
                "DonkeyKong" to "Central",
                "DonkeyKong" to "DesertHills",
                "DonkeyKong" to "FirstCircuit",
                "IceBuildings" to "LagoonCity",
                "IceBuildings" to "RaftingRiver",
                "SFCMarioCircuit" to "Central",
                "StormClouds" to "KoopaTroopaBeach",
            ),
            oneWay.map { it.from to it.to }.toSet(),
        )
        assertTrue("Donkey Kong to First Circuit is in the export", catalog.connects("DonkeyKong", "FirstCircuit"))
        assertFalse(
            "the reverse is not implied by the forward edge",
            catalog.connects("FirstCircuit", "DonkeyKong"),
        )
        assertTrue(catalog.connects("AirshipFortress", "DinoBones"))
        assertTrue(catalog.connects("DinoBones", "AirshipFortress"))
    }

    @Test
    fun `long rally full-tour courses are excluded from direct connections`() {
        assertFalse(catalog.connects("DesertHills", "BigTree"))
        assertFalse(catalog.connects("AirshipFortress", "StormClouds"))
        assertFalse(catalog.connects("BigTree", "AirshipFortress"))
        assertFalse(catalog.connects("Cinema", "WarioShipyard"))
        assertFalse(catalog.connects("PeachBeach", "DesertHills"))
        assertEquals(0, catalog.edges.count { it.kind == "LongRally" })
        // Defensive parser guard also rejects LongRally rows even if present in custom JSON.
        val mixed = """{"edges":[
            {"from":"DesertHills","to":"BigTree","kind":"LongRally","rowId":"LongRally_DesertHillsToBigTree_A_00"},
            {"from":"Central","to":"Rainbow","kind":"InterStageEx1","rowId":"InterStageEx1_CentralToRainbow_A_00"}
        ]}"""
        val parsed = StageConnections.parse(mixed)!!
        assertEquals(1, parsed.edges.size)
        assertTrue(parsed.connects("Central", "Rainbow"))
        assertFalse(parsed.connects("DesertHills", "BigTree"))
    }

    @Test
    fun `a one way connection is reported as one way`() {
        val edge = catalog.between("IceBuildings", "LagoonCity")
        assertNotNull("Ice Buildings to Lagoon City is in the export", edge)
        assertEquals("InterStageEx2", edge!!.kind)
        assertNull("Lagoon City to Ice Buildings is not", catalog.between("LagoonCity", "IceBuildings"))
        assertTrue(catalog.outgoing("LagoonCity").isNotEmpty())
    }

    @Test
    fun `stage connect end ratio overrides match v1_8_0 StageConnectInfos`() {
        val overridden = catalog.edges.filter { it.overrideJukeBoxEndRatio != null }
            .associate { (it.from to it.to) to it.overrideJukeBoxEndRatio }
        assertEquals(
            mapOf(
                ("Cave" to "Cinema") to 0.8,
                ("DKPass" to "SnowMountain") to 0.75,
                ("ShyGuyBazaar" to "WarioStadium") to 0.75,
                ("SFCMarioCircuit" to "Central") to 0.8,
            ),
            overridden,
        )
    }

    @Test
    fun `outgoing returns the real neighbours ordered by the export`() {
        val neighbours = catalog.neighbours("BigTree")
        assertEquals(5, neighbours.size)
        assertTrue(neighbours.contains("Cave"))
        assertTrue(neighbours.contains("Cinema"))
        assertFalse(neighbours.contains("BigTree"))
    }

    @Test
    fun `every edge carries its own kind and row id`() {
        for (edge in catalog.edges) {
            assertTrue("edge ${edge.rowId} must name a source", edge.from.isNotBlank())
            assertTrue("edge ${edge.rowId} must name a destination", edge.to.isNotBlank())
            assertTrue("edge ${edge.rowId} must state its kind", edge.kind.isNotBlank())
            assertTrue(
                "edge ${edge.rowId} must state its row id",
                edge.rowId.isNotBlank(),
            )
        }
        val kinds = catalog.edges.map { it.kind }.toSet()
        assertEquals(setOf("InterStageEx1", "InterStageEx2"), kinds)
    }

    @Test
    fun `the archive suffix spelling resolves to the same stage`() {
        val withSuffix = catalog.outgoing("AirshipFortress_00")
        val without = catalog.outgoing("AirshipFortress")
        assertEquals(without.size, withSuffix.size)
        assertEquals(without.map { it.rowId }, withSuffix.map { it.rowId })
    }

    @Test
    fun `an unknown stage has no neighbours rather than a guess`() {
        assertTrue(catalog.outgoing("NoSuchCourse").isEmpty())
        assertTrue(catalog.neighbours("NoSuchCourse").isEmpty())
        assertNull(catalog.between("BigTree", "NoSuchCourse"))
    }

    @Test
    fun `a malformed asset is refused instead of half loaded`() {
        assertNull(StageConnections.parse("not json"))
        assertNull(StageConnections.parse("""{"edges":[]}"""))
        val onlyBroken = """{"edges":[{"from":"","to":"Cave","kind":"InterStageEx1"}]}"""
        assertNull(StageConnections.parse(onlyBroken))
    }
}
