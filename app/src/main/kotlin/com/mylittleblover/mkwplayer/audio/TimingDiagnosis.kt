package com.mylittleblover.mkwplayer.audio

/**
 * Decides what to do when the device reports that it cannot keep up.
 *
 * The engine already collects timing for every callback, but nothing in the app
 * read it. On a real phone this is the difference between "it plays" and "it plays
 * without audible dropouts", so the thresholds below are explicit rather than
 * scattered through the UI.
 *
 * The numbers are policy, not measurements: a callback that misses its budget
 * occasionally is inaudible, while a sustained miss is a dropout. They are
 * conservative because the cost of reacting late is worse than rebuilding early.
 */
object TimingDiagnosis {

    /** Fraction of callbacks allowed to miss their budget before it is a problem. */
    const val MISS_RATIO_WARNING = 0.01

    /** Fraction above which a dropout is audible and the stream should be rebuilt. */
    const val MISS_RATIO_CRITICAL = 0.05

    /** p99 CPU share of the callback budget above which headroom is too thin. */
    const val CPU_SHARE_WARNING = 0.70

    /** p99 CPU share above which a rebuild is likely to help. */
    const val CPU_SHARE_CRITICAL = 0.90

    enum class Verdict {
        /** No callbacks yet; nothing to say. */
        NO_DATA,

        /** Everything is within budget. */
        HEALTHY,

        /** Close to the limit, or a few misses: keep running, but report. */
        DEGRADED,

        /** Audible dropouts likely; the caller should rebuild the stream. */
        FAILING,
    }

    data class Report(
        val verdict: Verdict,
        val callbacks: Int,
        val missed: Int,
        val missRatio: Double,
        val cpuP99Micros: Long,
        val budgetMicros: Long,
        val cpuShare: Double,
        val clockFailures: Int,
        val xruns: Int,
        val explanation: String,
    )

    /**
     * Interprets an `outputTimingSnapshot` packet.
     *
     * Layout is fixed by the native bridge: `[0]` wire version, `[1]` capacity,
     * `[2]` captured, `[3]` total, `[4]` missed, `[5]` clockFailures,
     * `[6]` clipped, `[7]` saturated, `[8..12]` CPU p50/p95/p99/p999/max,
     * `[13..17]` wall p50/p95/p99/p999/max, `[18]` xruns, `[19]` budget nanos.
     *
     * Returns null for a packet that is absent or the wrong shape rather than
     * guessing offsets: a shifted read would report a healthy stream as failing.
     */
    fun read(snapshot: LongArray?): Report? {
        if (snapshot == null || snapshot.size < 20) return null
        if (snapshot[0] != WIRE_VERSION) return null

        val total = snapshot[3].toInt()
        val missed = snapshot[4].toInt()
        val clockFailures = snapshot[5].toInt()
        val cpuP99Nanos = snapshot[10]
        val xruns = snapshot[18].toInt()
        val budgetNanos = snapshot[19]

        if (total <= 0) {
            return Report(
                verdict = Verdict.NO_DATA,
                callbacks = 0,
                missed = 0,
                missRatio = 0.0,
                cpuP99Micros = 0,
                budgetMicros = budgetNanos / 1000,
                cpuShare = 0.0,
                clockFailures = clockFailures,
                xruns = xruns,
                explanation = "No audio callbacks recorded yet.",
            )
        }

        val missRatio = missed.toDouble() / total
        val cpuShare = if (budgetNanos > 0) cpuP99Nanos.toDouble() / budgetNanos else 0.0

        val verdict = when {
            missRatio >= MISS_RATIO_CRITICAL || cpuShare >= CPU_SHARE_CRITICAL -> Verdict.FAILING
            missRatio >= MISS_RATIO_WARNING || cpuShare >= CPU_SHARE_WARNING -> Verdict.DEGRADED
            else -> Verdict.HEALTHY
        }

        return Report(
            verdict = verdict,
            callbacks = total,
            missed = missed,
            missRatio = missRatio,
            cpuP99Micros = cpuP99Nanos / 1000,
            budgetMicros = budgetNanos / 1000,
            cpuShare = cpuShare,
            clockFailures = clockFailures,
            xruns = xruns,
            explanation = explain(verdict, missRatio, cpuShare, xruns),
        )
    }

    /** Wire version this reader understands; a different value is refused. */
    const val WIRE_VERSION = 1L

    private fun explain(verdict: Verdict, missRatio: Double, cpuShare: Double, xruns: Int): String {
        val misses = "%.2f%% of callbacks over budget".format(missRatio * 100)
        val cpu = "p99 CPU at %.0f%% of budget".format(cpuShare * 100)
        return when (verdict) {
            Verdict.HEALTHY -> "Playback timing is within budget ($misses, $cpu)."
            Verdict.DEGRADED -> "Timing is marginal ($misses, $cpu); dropouts may be audible."
            Verdict.FAILING -> "Timing is failing ($misses, $cpu, $xruns xruns); rebuilding the stream may help."
            Verdict.NO_DATA -> "No audio callbacks recorded yet."
        }
    }
}
