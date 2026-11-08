package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The presentation layer decides what the screen says about a flow.
 *
 * Two claims it makes are load-bearing: the provenance label, because a provisional flow shown
 * without a warning reads as a reproduction, and the entry section, because starting at an outro
 * would present a fragment as a listen.
 */
class FlowPresentationTest {

    private fun part(
        role: StageFlow.Role,
        frames: Long,
        outroNumber: Int? = null,
        name: String = "BGM_Stage_Coaster",
    ): StageFlow.Part {
        val suffix = when (role) {
            StageFlow.Role.PreShort -> "_Pre_Short"
            StageFlow.Role.Pre -> "_Pre"
            StageFlow.Role.Bridge -> "_Bridge"
            StageFlow.Role.Main -> ""
            StageFlow.Role.Outro -> "_Outro%02d".format(outroNumber ?: 0)
        }
        return StageFlow.Part(
            role = role,
            fileName = "$name$suffix.bwav",
            frames = frames,
            sampleRate = 48000,
            channels = 2,
            loopStart = null,
            loopEnd = null,
            outroNumber = outroNumber,
        )
    }

    private fun flow(outros: Int = 2, withBridge: Boolean = true): StageFlow {
        val parts = mutableListOf(
            part(StageFlow.Role.PreShort, 534_857),
            part(StageFlow.Role.Pre, 2_427_426),
        )
        if (withBridge) parts += part(StageFlow.Role.Bridge, 88_320)
        parts += part(StageFlow.Role.Main, 3_373_711)
        for (n in 0 until outros) parts += part(StageFlow.Role.Outro, 226_286, outroNumber = n)
        return StageFlow("Coaster", parts, StageFlow.DEFAULT_TIMING)
    }

    private fun compiled(flow: StageFlow): StageFlowCompiler.Compiled {
        val digests = flow.parts.associate { it.fileName to ByteArray(32) }
        return StageFlowCompiler.compile(flow, digests)
    }

    // ---------------------------------------------------------------- sections

    @Test
    fun sectionsAreOrderedAsTheyAreHeard() {
        val flow = flow()
        val sections = FlowPresentation.sections(flow, compiled(flow))
        val labels = sections.map { it.label }

        // Bridge before Main: that is the order in the music, and a button row in a different
        // order would mislead about the structure.
        assertEquals("Pre (short)", labels[0])
        assertEquals("Pre", labels[1])
        assertEquals("Bridge", labels[2])
        assertEquals("Main", labels[3])
        assertEquals(listOf("Outro 00", "Outro 01"), labels.drop(4))
    }

    @Test
    fun everySectionCarriesItsOwnSegmentAndLength() {
        val flow = flow()
        val sections = FlowPresentation.sections(flow, compiled(flow))
        assertEquals(sections.size, sections.map { it.segment }.toSet().size)
        val main = sections.first { it.label == "Main" }
        assertEquals(3_373_711L, main.frames)
        assertEquals(70, main.seconds)
    }

    @Test
    fun aFlowWithoutABridgeSkipsIt() {
        val flow = flow(withBridge = false)
        val labels = FlowPresentation.sections(flow, compiled(flow)).map { it.label }
        assertTrue(!labels.contains("Bridge"), labels.toString())
    }

    @Test
    fun aFlowWithoutOutrosHasOnlyStructuralSections() {
        val flow = flow(outros = 0)
        val labels = FlowPresentation.sections(flow, compiled(flow)).map { it.label }
        assertEquals(4, labels.size)   // Pre_Short, Pre, Bridge, Main
    }

    // ---------------------------------------------------------------- provenance

    @Test
    fun aProvisionalFlowSaysSoInPlainWords() {
        val label = FlowPresentation.provenanceLabel(flow())
        assertTrue(label.contains("not the game's"), label)
    }

    @Test
    fun aVerifiedFlowSaysSomethingDifferent() {
        val verified = flow().copy(provenance = StageFlow.Provenance.VERIFIED)
        val label = FlowPresentation.provenanceLabel(verified)
        assertTrue(label.contains("verified"), label)
        assertTrue(!label.contains("not the game's"), label)
    }

    // ---------------------------------------------------------------- summary

    @Test
    fun theSummaryCountsPartsAndOutros() {
        assertEquals("6 parts, 2 outros", FlowPresentation.summary(flow()))
    }

    @Test
    fun theSummaryHandlesASingleOutro() {
        assertTrue(FlowPresentation.summary(flow(outros = 1)).contains("1 outro"))
    }

    @Test
    fun theSummaryOmitsOutrosWhenThereAreNone() {
        assertEquals("4 parts", FlowPresentation.summary(flow(outros = 0)))
    }

    // ---------------------------------------------------------------- entry

    @Test
    fun theEntrySectionIsTheFullPrelude() {
        val flow = flow()
        val c = compiled(flow)
        assertEquals(c.segmentByRole[StageFlow.Role.Pre], FlowPresentation.entrySection(c))
    }

    @Test
    fun withoutAPreludeTheEntryIsTheMainSection() {
        val onlyMain = StageFlow(
            "Solo",
            listOf(part(StageFlow.Role.Main, 1000)),
            StageFlow.DEFAULT_TIMING,
        )
        val c = compiled(onlyMain)
        assertEquals(c.segmentByRole[StageFlow.Role.Main], FlowPresentation.entrySection(c))
    }

    @Test
    fun theEntryIsNeverAnOutro() {
        // Starting at an ending would present a fragment as a listen.
        val flow = flow(outros = 3)
        val c = compiled(flow)
        val entry = FlowPresentation.entrySection(c)
        assertTrue(!c.segmentByOutro.containsValue(entry), "entry must not be an outro segment")
    }

    // ---------------------------------------------------------------- duration

    @Test
    fun totalFramesSumsTheStructuralPartsOnly() {
        // Outros are optional endings; adding them would overstate how long a listen runs.
        val flow = flow(outros = 2)
        val expected = 534_857L + 2_427_426L + 88_320L + 3_373_711L
        assertEquals(expected, FlowPresentation.totalFrames(flow))
    }

    @Test
    fun aFlowOfOnlyAnOutroStillReportsItsLength() {
        val onlyOutro = StageFlow(
            "Fragment",
            listOf(part(StageFlow.Role.Outro, 226_286, outroNumber = 0)),
            StageFlow.DEFAULT_TIMING,
        )
        assertEquals(226_286L, FlowPresentation.totalFrames(onlyOutro))
    }
}
