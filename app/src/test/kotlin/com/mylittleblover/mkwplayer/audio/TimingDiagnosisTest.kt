package com.mylittleblover.mkwplayer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Timing decides whether audio is actually usable, so the interpretation is
 * pinned rather than left to the UI. A wrong verdict either hides dropouts or
 * triggers needless stream rebuilds.
 */
class TimingDiagnosisTest {

    /** Builds a packet with the native bridge's field order. */
    private fun packet(
        total: Int,
        missed: Int,
        cpuP99Nanos: Long = 100_000,
        budgetNanos: Long = 1_000_000,
        clockFailures: Int = 0,
        xruns: Int = 0,
    ): LongArray = longArrayOf(
        TimingDiagnosis.WIRE_VERSION,   // 0 wire version
        2048,                          // 1 capacity
        minOf(total, 2048).toLong(),   // 2 captured
        total.toLong(),                // 3 total
        missed.toLong(),               // 4 missed
        clockFailures.toLong(),        // 5 clock failures
        0,                             // 6 clipped
        0,                             // 7 saturated
        0, 0, cpuP99Nanos, 0, 0,       // 8..12 cpu p50/p95/p99/p999/max
        0, 0, 0, 0, 0,                 // 13..17 wall
        xruns.toLong(),                // 18 xruns
        budgetNanos,                   // 19 budget nanos
    )

    @Test
    fun healthyStreamIsReportedHealthy() {
        val report = TimingDiagnosis.read(packet(total = 1000, missed = 0))!!
        assertEquals(TimingDiagnosis.Verdict.HEALTHY, report.verdict)
        assertEquals(0.0, report.missRatio)
        assertTrue(report.explanation.contains("within budget"))
    }

    @Test
    fun noCallbacksYetIsNotAHealthClaim() {
        val report = TimingDiagnosis.read(packet(total = 0, missed = 0))!!
        assertEquals(TimingDiagnosis.Verdict.NO_DATA, report.verdict)
        assertEquals(0, report.callbacks)
    }

    @Test
    fun aFewMissesAreDegradedNotFailing() {
        // 2% misses: above the warning line, below critical.
        val report = TimingDiagnosis.read(packet(total = 1000, missed = 20))!!
        assertEquals(TimingDiagnosis.Verdict.DEGRADED, report.verdict)
    }

    @Test
    fun manyMissesAreFailing() {
        val report = TimingDiagnosis.read(packet(total = 1000, missed = 100))!!
        assertEquals(TimingDiagnosis.Verdict.FAILING, report.verdict)
        assertTrue(report.explanation.contains("failing"))
    }

    @Test
    fun highCpuShareIsFailingEvenWithNoMisses() {
        // No misses yet, but p99 uses 95% of the budget: the next hiccup drops out.
        val report = TimingDiagnosis.read(
            packet(total = 1000, missed = 0, cpuP99Nanos = 950_000, budgetNanos = 1_000_000),
        )!!
        assertEquals(TimingDiagnosis.Verdict.FAILING, report.verdict)
    }

    @Test
    fun moderateCpuShareIsDegraded() {
        val report = TimingDiagnosis.read(
            packet(total = 1000, missed = 0, cpuP99Nanos = 750_000, budgetNanos = 1_000_000),
        )!!
        assertEquals(TimingDiagnosis.Verdict.DEGRADED, report.verdict)
    }

    @Test
    fun xrunsAreSurfacedInTheExplanationWhenFailing() {
        val report = TimingDiagnosis.read(packet(total = 1000, missed = 200, xruns = 7))!!
        assertEquals(7, report.xruns)
        assertTrue(report.explanation.contains("7 xruns"))
    }

    @Test
    fun missingPacketIsNullNotHealthy() {
        assertNull(TimingDiagnosis.read(null))
    }

    @Test
    fun wrongWireVersionIsRefused() {
        // A future native packet may reorder fields; guessing offsets would
        // report a healthy stream as failing or the reverse.
        val stale = packet(total = 1000, missed = 0)
        stale[0] = 99
        assertNull(TimingDiagnosis.read(stale))
    }

    @Test
    fun shortPacketIsRefused() {
        assertNull(TimingDiagnosis.read(LongArray(8)))
    }

    @Test
    fun zeroBudgetDoesNotDivideByZero() {
        val report = TimingDiagnosis.read(packet(total = 10, missed = 0, budgetNanos = 0))
        assertNotNull(report)
        assertEquals(0.0, report.cpuShare)
    }

    @Test
    fun thresholdsAreOrdered() {
        assertTrue(TimingDiagnosis.MISS_RATIO_WARNING < TimingDiagnosis.MISS_RATIO_CRITICAL)
        assertTrue(TimingDiagnosis.CPU_SHARE_WARNING < TimingDiagnosis.CPU_SHARE_CRITICAL)
    }

    @Test
    fun exactWarningBoundaryIsDegraded() {
        // 1% is the warning line, and the comparison is inclusive.
        val report = TimingDiagnosis.read(packet(total = 100, missed = 1))!!
        assertEquals(TimingDiagnosis.Verdict.DEGRADED, report.verdict)
    }

    @Test
    fun justUnderTheWarningLineIsHealthy() {
        val report = TimingDiagnosis.read(packet(total = 1000, missed = 9))!!
        assertEquals(TimingDiagnosis.Verdict.HEALTHY, report.verdict)
    }
}
