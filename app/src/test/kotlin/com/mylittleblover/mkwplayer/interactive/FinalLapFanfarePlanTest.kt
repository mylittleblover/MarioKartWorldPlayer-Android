package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File
import kotlin.test.*
import org.junit.Assume.assumeTrue

class FinalLapFanfarePlanTest {
    @Test fun everyNormalFastEdgeCutsFullAndOverlapsSharedFanfareTail() {
        assumeTrue(File("/workspace/mkw-audio/sources/Stage_DinoBones").isDirectory &&
            File("src/main/assets/audio/BGM_Seq_FinalLapFF.bwav").isFile)
        val catalog=MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
        for(stage in listOf("DinoBones","WestCity","DinoDinoJungle","Cinema","SnowMountain","DonkeyKong")) {
            val bytes=File("/workspace/mkw-audio/sources/Stage_$stage").listFiles()!!.filter { it.extension=="bwav" }.associate { it.name to it.readBytes() }
            val groups=StageFlowDiscovery.group(bytes.map { (n,b)->StageFlowReader.readPart(n,b).getOrThrow() })
            val flow=StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(groups)!!).getOrThrow()
            val all=bytes+("BGM_Seq_FinalLapFF.bwav" to File("src/main/assets/audio/BGM_Seq_FinalLapFF.bwav").readBytes())
            val p=InteractivePlaybackPlan.normal(flow,all,catalog)
            val ff=p.labels.entries.singleOrNull { it.value=="FinalLapFF" }?.key
            assertNotNull(ff,"$stage must use the shared FinalLapFF")
            assertFalse(ff in p.initialSegments)
            val segment=p.spec.segments.single { it.index==ff }
            assertEquals(FinalLapFanfareCompiler.FANFARE_EXIT_FRAMES,segment.exit)
            assertNull(segment.loop)
            val clip=p.spec.tracks.single { it.segment==ff }.clips.single()
            assertEquals(268759L,clip.sourceLength)
            assertEquals("BGM_Seq_FinalLapFF.bwav",p.spec.assets[clip.asset].path)
            assertTrue("BGM_Seq_FinalLapFF.bwav" in p.preparedAssets)
            val edges=p.spec.transitions.filter { it.bridge==ff }
            assertEquals(if(stage in listOf("Cinema","SnowMountain")) 2 else 1,edges.size)
            for(rule in edges) {
                assertEquals(G.SyncPoint.Immediate,rule.sync)
                assertNull(rule.sourceCue)
                assertEquals(G.Fade(beats = FinalLapFanfareCompiler.FULL_FADE_OUT_SEC * p.spec.bpm / 60.0),rule.fadeOut)
                assertEquals(G.Fade(),rule.fadeIn)
                assertTrue(rule.postExit,"FF tail must survive Fast entry")
                assertEquals(
                    G.TransitionTimeline(
                        FinalLapFanfareCompiler.FANFARE_DELAY_FRAMES,
                        FinalLapFanfareCompiler.FAST_DELAY_FRAMES,
                        FinalLapFanfareCompiler.FAST_DELAY_FRAMES,
                        0L,
                    ),
                    rule.timeline,
                )
            }
            if(stage=="DonkeyKong") assertEquals(listOf(p.regions.getValue(5)),edges.single().sources)
            if(stage in listOf("Cinema","SnowMountain")) {
                val versionRules=p.spec.transitions.filter { it.entry==G.EntryMode.SameTime }
                assertEquals(4,versionRules.size)
                assertTrue(versionRules.all { it.bridge==-1 && it.fadeOut.beats>0 })
            }
            assertContentEquals(p.packet,p.standard!!.packet)
            assertEquals(p.assetPaths,p.standard.assetPaths)
            assertTrue(p.assetPaths.all { it in all })
        }
    }
}
