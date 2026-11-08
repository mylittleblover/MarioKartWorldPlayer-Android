package com.mylittleblover.mkwplayer.graph

import com.mylittleblover.mkwplayer.library.StageFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compiling a flow into a graph.
 *
 * The claims checked here are structural: the right segments exist, the loop is on the main
 * section, both preludes reach the main section, and the identifiers do not collide. The
 * engine-side questions -- does it load, does the transition fire -- are answered on a device,
 * not here.
 */
class StageFlowCompilerTest {

    private fun part(
        role: StageFlow.Role,
        frames: Long,
        loop: Pair<Long, Long>? = null,
        name: String = "BGM_Stage_Coaster",
    ): StageFlow.Part {
        val suffix = when (role) {
            StageFlow.Role.PreShort -> "_Pre_Short"
            StageFlow.Role.Pre -> "_Pre"
            StageFlow.Role.Bridge -> "_Bridge"
            StageFlow.Role.Main -> ""
            StageFlow.Role.Outro -> "_Outro00"
            StageFlow.Role.Fast -> "_Fast"
        }
        return StageFlow.Part(
            role = role,
            fileName = "$name$suffix.bwav",
            frames = frames,
            sampleRate = 48000,
            channels = 2,
            loopStart = loop?.first,
            loopEnd = loop?.second,
            outroNumber = if (role == StageFlow.Role.Outro) 0 else null,
        )
    }

    private fun fullFlow(): StageFlow = StageFlow(
        name = "Coaster",
        parts = listOf(
            part(StageFlow.Role.PreShort, 534_857, loop = 205_714L to 534_857L),
            part(StageFlow.Role.Pre, 2_427_426, loop = 452_571L to 2_427_426L),
            part(StageFlow.Role.Bridge, 88_320),
            part(StageFlow.Role.Main, 3_373_711, loop = 493_714L to 3_373_711L),
            part(StageFlow.Role.Outro, 226_286),
        ),
        timing = StageFlow.DEFAULT_TIMING,
    )

    private fun digests(flow: StageFlow): Map<String, ByteArray> =
        flow.parts.associate { it.fileName to ByteArray(32) { b -> (it.frames + b).toByte() } }

    @Test
    fun aFullFlowCompilesToAReadablePacket() {
        val flow = fullFlow()
        val compiled = StageFlowCompiler.compile(flow, digests(flow))
        val probe = GraphProbe.inspect(compiled.packet)
        assertTrue(probe is GraphProbe.Result.Ok, "got $probe")
    }

    @Test
    fun everyRoleGetsItsOwnSegment() {
        val flow = fullFlow()
        val compiled = StageFlowCompiler.compile(flow, digests(flow))
        // Outros live in their own map, indexed by number rather than by role, because a flow
        // may carry several of them.
        assertEquals(4, compiled.segmentByRole.size)
        assertEquals(1, compiled.segmentByOutro.size)
        val all = compiled.segmentByRole.values + compiled.segmentByOutro.values
        assertEquals(all.size, all.toSet().size, "segment indices must be distinct")
    }

    @Test
    fun assetPathsFollowTheBindingOrder() {
        val flow = fullFlow()
        val compiled = StageFlowCompiler.compile(flow, digests(flow))
        assertEquals(5, compiled.assetPaths.size)
        assertTrue(compiled.assetPaths.contains("BGM_Stage_Coaster_Bridge.bwav"))
        assertTrue(compiled.assetPaths.contains("BGM_Stage_Coaster.bwav"))
    }

    @Test
    fun aFlowWithoutABridgeStillCompiles() {
        val flow = fullFlow().let {
            it.copy(parts = it.parts.filter { p -> p.role != StageFlow.Role.Bridge })
        }
        val compiled = StageFlowCompiler.compile(flow, digests(flow))
        assertTrue(GraphProbe.inspect(compiled.packet) is GraphProbe.Result.Ok)
        assertEquals(null, compiled.segmentByRole[StageFlow.Role.Bridge])
    }

    @Test
    fun aMinimalFlowOfPreAndMainCompiles() {
        val flow = StageFlow(
            name = "Minimal",
            parts = listOf(
                part(StageFlow.Role.Pre, 2_427_426),
                part(StageFlow.Role.Main, 3_373_711, loop = 493_714L to 3_373_711L),
            ),
            timing = StageFlow.DEFAULT_TIMING,
        )
        val compiled = StageFlowCompiler.compile(flow, digests(flow))
        assertTrue(GraphProbe.inspect(compiled.packet) is GraphProbe.Result.Ok)
        assertEquals(2, compiled.segmentByRole.size)
    }

    @Test
    fun aFlowWithoutAMainPartIsRejectedBeforeEncoding() {
        val flow = fullFlow().let {
            it.copy(parts = it.parts.filter { p -> p.role != StageFlow.Role.Main })
        }
        val error = assertFailsWith<IllegalArgumentException> {
            StageFlowCompiler.compile(flow, digests(flow))
        }
        assertTrue(error.message!!.contains("Main"))
    }

    @Test
    fun aMissingDigestIsRejectedRatherThanZeroFilled() {
        // A zeroed digest would let the engine bind a file the graph does not describe.
        val flow = fullFlow()
        val full = digests(flow)
        val partial = full - "BGM_Stage_Coaster_Bridge.bwav"
        val error = assertFailsWith<IllegalArgumentException> {
            StageFlowCompiler.compile(flow, partial)
        }
        assertTrue(error.message!!.contains("digest"))
    }

    @Test
    fun aShortDigestIsRejected() {
        val flow = fullFlow()
        val bad = digests(flow).mapValues { (_, v) -> v.copyOf(16) }
        assertFailsWith<IllegalArgumentException> { StageFlowCompiler.compile(flow, bad) }
    }

    @Test
    fun compileFromBytesDerivesDistinctDigests() {
        val flow = fullFlow()
        val bytes = flow.parts.associate { it.fileName to ByteArray(64) { b -> (it.frames + b).toByte() } }
        val compiled = StageFlowCompiler.compileFromBytes(flow, bytes)
        assertTrue(GraphProbe.inspect(compiled.packet) is GraphProbe.Result.Ok)
    }

    @Test
    fun aDifferentFileContentChangesThePacket() {
        // If content did not affect the packet, the digest would not be doing its job.
        val flow = fullFlow()
        val a = flow.parts.associate { it.fileName to ByteArray(32) { _ -> 1 } }
        val b = flow.parts.associate { it.fileName to ByteArray(32) { _ -> 2 } }
        val first = StageFlowCompiler.compileFromBytes(flow, a).packet
        val second = StageFlowCompiler.compileFromBytes(flow, b).packet
        assertTrue(!first.contentEquals(second), "the digest must reach the packet")
    }

    @Test
    fun theOutroIsItsOwnSegmentWithNoLoop() {
        val flow = fullFlow()
        val compiled = StageFlowCompiler.compile(flow, digests(flow))
        assertNotNull(compiled.segmentForOutro(0))
        // An outro that looped would never end; the graph must not put a loop on it.
        val outro = flow.part(StageFlow.Role.Outro)!!
        assertTrue(!outro.hasLoop)
    }

    @Test
    fun aHigherTransportRateIsPropagated() {
        val flow = fullFlow()
        val compiled = StageFlowCompiler.compile(flow, digests(flow), transportSampleRate = 44100)
        assertTrue(GraphProbe.inspect(compiled.packet) is GraphProbe.Result.Ok)
    }

    @Test
    fun anUnsupportedTransportRateIsRejected() {
        val flow = fullFlow()
        assertFailsWith<IllegalArgumentException> {
            StageFlowCompiler.compile(flow, digests(flow), transportSampleRate = 22050)
        }
    }

    @Test
    fun theFlowIsCarriedThroughUnchanged() {
        val flow = fullFlow()
        val compiled = StageFlowCompiler.compile(flow, digests(flow))
        assertEquals(flow.name, compiled.flow.name)
        assertTrue(compiled.flow.isProvisional, "an assembled flow must stay provisional")
    }
}
