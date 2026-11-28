package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import java.io.File
import kotlin.test.*
import kotlin.math.roundToLong

class RainbowInteractiveCompilerTest {
    private val catalog = MinfCatalog.parse(File("src/main/assets/interactive_minf.json").readText())
    private fun prepare(): RainbowInteractiveCompiler.Prepared {
        val sources = RainbowInteractiveCompiler.roles.map { role ->
            val r = catalog.part("Rainbow", role)!!
            RainbowInteractiveCompiler.Source("BGM_Rainbow_$role.bwav",48000,2,r.loopEnd+10000,
                if(r.loopStart<r.loopEnd) r.loopStart else null,
                if(r.loopStart<r.loopEnd) r.loopEnd else null,ByteArray(32))
        }
        return RainbowInteractiveCompiler.prepare(sources,catalog)
    }
    @Test fun area1UsesTwoDistinctSynchronizedLayers() {
        val p=prepare(); val id=p.segments.getValue("Area1")
        val tracks=p.spec.tracks.filter { it.segment==id }
        assertEquals(2,tracks.size)
        assertNotEquals(tracks[0].clips.single().asset,tracks[1].clips.single().asset)
        assertTrue(tracks.all { it.clips.single().sourceOffset==0L })
    }
    @Test fun area1PreUsesSixteenBeatGridAndArea3PreUsesFourBeatGrid() {
        val p = prepare()
        val area1PreCues = p.spec.segments.single { it.index == p.segments.getValue("Area1_Pre") }
            .cues.filter { it.name == "pre.exit" }.map { it.frame }
        assertEquals(6, area1PreCues.size)
        assertTrue(4374673L in area1PreCues, "Area1_Pre 16-beat grid must include beat 240 (4374673)")
        val area3PreCues = p.spec.segments.single { it.index == p.segments.getValue("Area3_Pre") }
            .cues.filter { it.name == "pre.exit" }.map { it.frame }
        assertEquals(3, area3PreCues.size)
    }
    @Test fun area1ToArea2UsesBarExitWith300msInstFadeOutAndDestinationPhaseLead() {
        val p = prepare()
        val t = p.spec.transitions.single {
            it.sources == listOf(p.segments.getValue("Area1")) && it.destinations == listOf(p.segments.getValue("Area2"))
        }
        assertEquals(G.SyncPoint.NextCustomCue, t.sync)
        assertEquals("bar.exit", t.sourceCue)
        assertTrue(t.postExit)
        assertEquals(-11392L, t.timeline!!.destinationStart)
        assertEquals(0L, t.timeline!!.destinationCommit)
        assertEquals(14_400L, (t.fadeOut.beats * 60.0 * 48000 / p.spec.bpm).roundToLong())
        assertEquals(0.0, t.fadeOut.offset)
        assertEquals(0.0, t.fadeIn.beats)
        val meta = catalog.part("Rainbow", "Area1_Inst")!!
        val cues = p.spec.segments.single { it.index == p.segments.getValue("Area1") }.cues.filter { it.name == "bar.exit" }
        assertEquals(meta.beats.filter { it.label == 1 }.map { it.frame }, cues.map { it.frame })
    }
    @Test fun everyOutroHasItsOwnSourceExitAndNaturalTail() {
        val p=prepare()
        for(n in 0..4) {
            val id=p.segments.getValue("Area2_Outro%02d".format(n))
            val segment=p.spec.segments.single { it.index==id }
            val t=p.spec.transitions.single { it.sources==listOf(id) }
            assertEquals(382784L,segment.exit)
            assertEquals(G.SyncPoint.ExitCue,t.sync)
            assertTrue(t.postExit)
            assertEquals(p.segments.getValue("Area3_Pre"),t.destinations.single())
            assertTrue(p.spec.tracks.single { it.segment==id }.clips.single().sourceLength>segment.exit)
        }
    }
    @Test fun area3ToFinalPreUsesVerifiedV180DelayedFadeOutAnd2500msFadeIn() {
        val p = prepare()
        val t = p.spec.transitions.single {
            it.sources == listOf(p.segments.getValue("Area3")) &&
                it.destinations == listOf(p.segments.getValue("Final_Pre"))
        }
        assertEquals(G.SyncPoint.Immediate, t.sync)
        assertNull(t.sourceCue)
        assertTrue(t.postExit, "Area3 must remain audible during its 1.20s hold + 3.00s fade")
        assertEquals(57_600L, (t.fadeOut.offset * 60.0 * 48000 / p.spec.bpm).roundToLong())
        assertEquals(144_000L, (t.fadeOut.beats * 60.0 * 48000 / p.spec.bpm).roundToLong())
        assertEquals(120_000L, (t.fadeIn.beats * 60.0 * 48000 / p.spec.bpm).roundToLong())
        assertEquals(0.0, t.fadeIn.offset)

        // Perfect-beat mode aligns Area3 -> Final_Pre to bar.exit.
        val sources = RainbowInteractiveCompiler.roles.map { role ->
            val r = catalog.part("Rainbow", role)!!
            RainbowInteractiveCompiler.Source("BGM_Rainbow_$role.bwav", 48000, 2, r.loopEnd + 10000,
                if (r.loopStart < r.loopEnd) r.loopStart else null,
                if (r.loopStart < r.loopEnd) r.loopEnd else null, ByteArray(32))
        }
        val perfect = RainbowInteractiveCompiler.prepare(sources, catalog, perfectBeatEnabled = true)
        val tp = perfect.spec.transitions.single {
            it.sources == listOf(perfect.segments.getValue("Area3")) &&
                it.destinations == listOf(perfect.segments.getValue("Final_Pre"))
        }
        assertEquals(G.SyncPoint.NextCustomCue, tp.sync)
        assertEquals("bar.exit", tp.sourceCue)
    }
    @Test fun finalPreAlwaysPassesThroughFanfareWithVerified200msDelayAnd100msFadeOut() {
        val p=prepare();val id=p.segments.getValue("FinalLapFanfare")
        val pre=p.spec.transitions.single { it.sources==listOf(p.segments.getValue("Final_Pre")) }
        assertEquals(id,pre.bridge)
        assertEquals(p.segments.getValue("Final"),pre.destinations.single())
        assertEquals(9_600L,pre.timeline!!.bridgeStart)
        // Verified v1.8.0: Rainbow_FanfareDelay = 0.20s (9,600 f) + 218,734 f Fanfare->Final.
        val handover=30_914_669L-30_695_935L
        assertEquals(9_600L+handover,pre.timeline!!.destinationStart)
        assertEquals(9_600L+handover,pre.timeline!!.destinationCommit)
        assertEquals(0L,pre.timeline!!.destinationSource)
        assertEquals(4_800L,(pre.fadeOut.beats*60.0*48000/p.spec.bpm).roundToLong())
        assertEquals(0.0,pre.fadeOut.offset)
        val t=p.spec.transitions.single { it.sources==listOf(id) }
        assertEquals(G.SyncPoint.ExitCue,t.sync)
        assertEquals(p.segments.getValue("Final"),t.destinations.single())
        assertEquals(handover,p.spec.segments.single { it.index==id }.exit)
        assertTrue(t.postExit)
        assertTrue(p.spec.tracks.single { it.segment==id }.clips.single().sourceLength>handover)
        assertFalse(p.spec.transitions.any { it.sources==listOf(p.segments.getValue("Area3")) && id in it.destinations })
        assertNull(p.spec.segments.single { it.index==id }.loop)
    }
}
