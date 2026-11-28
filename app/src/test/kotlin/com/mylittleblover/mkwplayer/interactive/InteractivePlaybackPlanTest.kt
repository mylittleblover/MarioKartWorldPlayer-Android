package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import kotlin.test.*
import org.junit.Assume.assumeTrue

class InteractivePlaybackPlanTest {
    @Test fun allThirtyRealSourceGroupsPrepareTheGraphsUsedByTheApplication() {
        val root=File(System.getenv("MKW_AUDIO_ROOT") ?: "/workspace/mkw-audio/sources")
        assumeTrue(root.isDirectory)
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        var count=0
        for(dir in root.listFiles()!!.filter { it.isDirectory && (it.name.startsWith("Stage_") || it.name=="Rainbow") }) {
            val bytes=dir.listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
            val p=if(dir.name=="Rainbow") InteractivePlaybackPlan.rainbow(bytes,catalog) else {
                val parts=bytes.map { (name,data) -> StageFlowReader.readPart(name,data).getOrThrow() }
                val group=StageFlowDiscovery.dominant(StageFlowDiscovery.group(parts))!!
                InteractivePlaybackPlan.normal(StageFlowDiscovery.flowFor(group).getOrThrow(),bytes,catalog)
            }
            assertTrue(p.packet.isNotEmpty(),dir.name)
            assertTrue(p.assetPaths.all { it in bytes },dir.name)
            assertTrue(p.entry in p.labels,dir.name)
            if(dir.name=="Rainbow") {
                assertTrue(p.streamed);assertEquals(15,p.assetPaths.size)
                listOf("Area1", "Area2", "Area3_Pre", "Area3").forEach { label ->
                    assertEquals(label, p.labels[p.startingAt(label).entry])
                }
                listOf("Final_Pre", "Final").forEach { label ->
                    val segId = p.labels.entries.single { it.value == label }.key
                    assertFalse(segId in p.initialSegments, "Rainbow must not allow direct entry at $label")
                    assertFailsWith<IllegalStateException> { p.startingAt(label) }
                }
                assertEquals(setOf(p.labels.entries.single { it.value == "Area2" }.key), p.advanceViaOutro)
                assertEquals(6,p.automaticNext.size)
                assertNotNull(p.directBridge)
                assertTrue(p.directBridge in p.initialSegments)
                val bridgeEntry = p.startingAt("Area1_Bridge")
                assertEquals(p.directBridge, bridgeEntry.entry)
                assertTrue(p.entry in bridgeEntry.initialSegments)
                assertTrue(p.next.values.all { it in p.labels })
                assertTrue(p.overlayDefaultEnabled)
                assertTrue(p.overlayTrackIndices.isNotEmpty())
            } else {
                assertNotNull(p.fast,"${dir.name}: importer lost Fast arrangement")
                assertNotNull(p.standard)
                assertEquals(p.entryAutomaticNext, p.automaticNext)
                p.directBridge?.let { assertTrue(it in p.initialSegments) }
                p.regions.values.forEach { assertTrue(it in p.initialSegments) }
                p.versions.forEach { version -> assertTrue(version.main in p.initialSegments) }
            }
            count++
        }
        assertEquals(30,count)
    }
}
