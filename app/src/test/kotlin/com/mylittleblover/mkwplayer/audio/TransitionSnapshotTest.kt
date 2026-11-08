package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transition block's field order is copied from `EngineRuntime::snapshot()`.
 *
 * A shift here would not crash: it would report a request frame as a result code, or an
 * inactive transition as an active one. Those are the errors this project has paid for before,
 * so the order is pinned field by field.
 */
class TransitionSnapshotTest {

    /**
     * Builds a packet in the engine's order:
     * `{active, transition, source, destination, bridge, requestedAt, sourceSync,
     *   destinationEntry, preStart, candidates, specificity, priority, queued, phase}`.
     */
    private fun packet(
        active: Long = 1,
        transition: Long = 40,
        source: Long = 10,
        destination: Long = 12,
        bridge: Long = 11,
        requestedAt: Long = 1000,
        sourceSync: Long = 4800,
        destinationEntry: Long = 0,
        preStart: Long = -1,
        candidates: Long = 1,
        specificity: Long = 0,
        priority: Long = 0,
        queued: Long = -1,
        phase: Long = TransitionSnapshot.PHASE_PENDING,
        version: Long = TransitionSnapshot.WIRE_VERSION,
    ): LongArray {
        val values = LongArray(65)
        values[0] = version
        values[24] = active
        values[25] = transition
        values[26] = source
        values[27] = destination
        values[28] = bridge
        values[29] = requestedAt
        values[30] = sourceSync
        values[31] = destinationEntry
        values[32] = preStart
        values[33] = candidates
        values[34] = specificity
        values[35] = priority
        values[36] = queued
        values[37] = phase
        return values
    }

    @Test
    fun readsTheEngineFieldOrder() {
        val fields = TransitionSnapshot.read(packet())!!
        assertTrue(fields.active)
        assertEquals(40L, fields.transition)
        assertEquals(10L, fields.source)
        assertEquals(12L, fields.destination)
        assertEquals(11L, fields.bridge)
        assertEquals(1000L, fields.requestedAt)
        assertEquals(4800L, fields.sourceSync)
        assertEquals(1L, fields.candidates)
        assertEquals(TransitionSnapshot.PHASE_PENDING, fields.phase)
    }

    @Test
    fun anInactiveBlockIsNotReportedAsActive() {
        val fields = TransitionSnapshot.read(
            packet(active = 0, transition = 0, source = -1, destination = -1, phase = TransitionSnapshot.PHASE_INACTIVE),
        )!!
        assertFalse(fields.active)
        assertEquals(-1L, fields.source)
        assertEquals(TransitionSnapshot.PHASE_INACTIVE, fields.phase)
        assertEquals("inactive", TransitionSnapshot.phaseName(fields.phase))
    }

    @Test
    fun requestedAtIsNotAStatus() {
        // The bug this guards: reading index 24 as the transition index and 29 as a phase
        // would make 1000 look like a phase code.
        val fields = TransitionSnapshot.read(packet(requestedAt = 123456))!!
        assertEquals(123456L, fields.requestedAt)
        assertTrue(fields.phase < 4, "phase must be one of the four codes, got ${fields.phase}")
    }

    @Test
    fun phaseCodesAreNamed() {
        assertEquals("pending", TransitionSnapshot.phaseName(TransitionSnapshot.PHASE_PENDING))
        assertEquals("entered", TransitionSnapshot.phaseName(TransitionSnapshot.PHASE_ENTERED))
        assertEquals("source left", TransitionSnapshot.phaseName(TransitionSnapshot.PHASE_SOURCE_LEFT))
        assertEquals("inactive", TransitionSnapshot.phaseName(TransitionSnapshot.PHASE_INACTIVE))
    }

    @Test
    fun anUnknownPhaseIsReportedNotCoerced() {
        assertEquals("unknown phase 9", TransitionSnapshot.phaseName(9))
    }

    @Test
    fun absentOrShortPacketsReadAsNull() {
        assertNull(TransitionSnapshot.read(null))
        assertNull(TransitionSnapshot.read(LongArray(20)))
    }

    @Test
    fun aWrongWireVersionReadsAsNull() {
        // A reordered bridge would put different fields at 24..37.
        assertNull(TransitionSnapshot.read(packet(version = 7)))
        assertNull(TransitionSnapshot.read(packet(version = 9)))
    }

    @Test
    fun theBlockDoesNotOverlapTheTransportFields() {
        // Transport owns 0..7 (version, frame, status, voices, object, dropped, rejected,
        // lastResult); the transition block must start after it.
        assertEquals(24..37, TransitionSnapshot.range())
        assertTrue(
            TransitionSnapshot.range().first > 7,
            "the transition block must not overlap the transport fields 0..7",
        )
    }

    @Test
    fun bridgeAndDestinationAreSeparate() {
        // A bridge is a segment in its own right, not a synonym for the destination.
        val fields = TransitionSnapshot.read(packet(bridge = 11, destination = 12))!!
        assertEquals(11L, fields.bridge)
        assertEquals(12L, fields.destination)
    }

    @Test
    fun aQueuedTargetIsVisible() {
        val fields = TransitionSnapshot.read(packet(queued = 99))!!
        assertEquals(99L, fields.queued)
    }

    @Test
    fun preEntryStartIsAbsentWhenUnused() {
        val fields = TransitionSnapshot.read(packet(preStart = -1))!!
        assertEquals(-1L, fields.preStart)
    }
}
