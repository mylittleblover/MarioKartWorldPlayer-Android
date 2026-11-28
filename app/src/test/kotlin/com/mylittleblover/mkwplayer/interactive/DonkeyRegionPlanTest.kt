package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import kotlin.test.*
import org.junit.Assume.assumeTrue

class DonkeyRegionPlanTest {
    @Test fun everyRegionUsesItsOwnExactBarGridAndTargetPhaseWithoutInventedOutros() {
        val dir=File("/workspace/mkw-audio/sources/Stage_DonkeyKong")
        assumeTrue(dir.isDirectory)
        val bytes=dir.listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        val groups=StageFlowDiscovery.group(bytes.map { (n,b)->StageFlowReader.readPart(n,b).getOrThrow() })
        val plan=InteractivePlaybackPlan.normal(StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(groups)!!).getOrThrow(),bytes,catalog)
        assertEquals(5,plan.regions.size)
        assertTrue(plan.standard!!.segmentByRole.getValue(com.mylittleblover.mkwplayer.library.StageFlow.Role.PreShort) in plan.initialSegments)
        assertTrue(plan.regions.getValue(1) in plan.initialSegments)
        assertTrue(plan.fast in plan.initialSegments, "Final Lap is an explicitly selectable starting point")
        plan.standard.segmentByRole[com.mylittleblover.mkwplayer.library.StageFlow.Role.Bridge]?.let { assertFalse(it in plan.initialSegments) }
        assertTrue(plan.streamed)
        for((number,source) in plan.regions) {
            val part=catalog.part(if(number==1) "DonkeyKong" else "DonkeyKong_Pattern$number","Full")!!
            val segment=plan.spec.segments.single { it.index==source }
            assertEquals(segment.cues.map { it.frame }.sorted(),segment.cues.map { it.frame },"Native requires chronological cues")
            assertEquals(DonkeyRegionCompiler.regionExitFrames(part, number),segment.cues.filter { it.name=="region.exit" }.map { it.frame })
            for((targetNumber,target) in plan.regions.filterKeys { it==number+1 }) {
                val targetPart=catalog.part(if(targetNumber==1) "DonkeyKong" else "DonkeyKong_Pattern$targetNumber","Full")!!
                val rule=plan.spec.transitions.single { it.sources==listOf(source) && it.destinations==listOf(target) }
                assertEquals(G.SyncPoint.NextCustomCue,rule.sync)
                assertEquals("region.exit",rule.sourceCue)
                assertEquals(-targetPart.phase,rule.timeline!!.destinationStart)
                assertEquals(0,rule.timeline.destinationCommit)
                assertEquals(0,rule.timeline.destinationSource)
            }
            val progression=plan.spec.transitions.filter { source in it.sources && (it.destinations.single() in plan.regions.values || it.destinations.single()==plan.fast) }
            val expected=if(number==5) plan.fast else plan.regions[number+1]
            assertEquals(listOf(expected),progression.map { it.destinations.single() })
            assertEquals(expected,plan.next[source])
        }
        assertEquals(5,plan.spec.transitions.count { it.sourceCue=="region.exit" })
        assertContentEquals(plan.packet,plan.standard!!.packet)
    }
}
