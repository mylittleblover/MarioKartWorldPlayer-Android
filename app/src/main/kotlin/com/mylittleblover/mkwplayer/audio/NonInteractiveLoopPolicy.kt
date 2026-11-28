package com.mylittleblover.mkwplayer.audio

/**
 * Policy controlling playback duration and tail fade-out for non-interactive tracks that carry
 * BWAV loop points (e.g., Extra Music looping tracks, Short Sounds, standalone looping BWAVs)
 * when Repeat-One is OFF.
 *
 * Semantics (matching JukeKart's existing [FlowStep.loopCount] convention):
 * - [NonInteractiveLoopMode.LOOP_COUNT]: plays `[0 .. loopEnd)` once, then loops `[loopStart .. loopEnd)`
 *   [loopCount] additional times (`loopCount = 1` = 1 extra loop = 2 passes of the loop body),
 *   followed by [fadeOutMs] (default 10 s) fading linearly to silence while continuing seamlessly
 *   through `[loopStart .. loopEnd)` (wrapping as many times as needed if `fadeOutMs > loopLen`).
 * - [NonInteractiveLoopMode.TARGET_DURATION]: plays from `0`, wrapping seamlessly through
 *   `[loopStart .. loopEnd)` until [targetDurationMs], with the final [fadeOutMs] (clamped to
 *   [targetDurationMs]) fading linearly to silence.
 *
 * When Repeat-One is ON, finite fade-out is disabled and the track loops infinitely across
 * `[loopStart .. loopEnd)`.
 */
internal enum class NonInteractiveLoopMode {
    LOOP_COUNT,
    TARGET_DURATION,
}

internal data class NonInteractiveLoopConfig(
    val mode: NonInteractiveLoopMode = NonInteractiveLoopMode.LOOP_COUNT,
    val loopCount: Int = DEFAULT_LOOP_COUNT,
    val targetDurationMs: Long = DEFAULT_TARGET_DURATION_MS,
    val fadeOutMs: Long = DEFAULT_FADEOUT_MS,
) {
    fun normalized(): NonInteractiveLoopConfig = copy(
        loopCount = loopCount.coerceIn(MIN_LOOP_COUNT, MAX_LOOP_COUNT),
        targetDurationMs = targetDurationMs.coerceIn(MIN_TARGET_DURATION_MS, MAX_TARGET_DURATION_MS),
        fadeOutMs = fadeOutMs.coerceIn(0L, MAX_FADEOUT_MS),
    )

    companion object {
        const val DEFAULT_LOOP_COUNT = 1
        const val MIN_LOOP_COUNT = 0
        const val MAX_LOOP_COUNT = 99
        const val DEFAULT_FADEOUT_MS = 10_000L
        const val MAX_FADEOUT_MS = 60_000L
        const val DEFAULT_TARGET_DURATION_MS = 180_000L
        const val MIN_TARGET_DURATION_MS = 1_000L
        const val MAX_TARGET_DURATION_MS = 4L * 60L * 60L * 1000L // 4 hours

        val Default: NonInteractiveLoopConfig = NonInteractiveLoopConfig()

        fun byLoopCount(
            loops: Int = DEFAULT_LOOP_COUNT,
            fadeOutMs: Long = DEFAULT_FADEOUT_MS,
        ): NonInteractiveLoopConfig = NonInteractiveLoopConfig(
            mode = NonInteractiveLoopMode.LOOP_COUNT,
            loopCount = loops,
            fadeOutMs = fadeOutMs,
        ).normalized()

        fun byTargetDurationMs(
            durationMs: Long,
            fadeOutMs: Long = DEFAULT_FADEOUT_MS,
        ): NonInteractiveLoopConfig = NonInteractiveLoopConfig(
            mode = NonInteractiveLoopMode.TARGET_DURATION,
            targetDurationMs = durationMs,
            fadeOutMs = fadeOutMs,
        ).normalized()
    }
}

internal data class EffectiveLoopTiming(
    val totalFrames: Long,
    val fadeOutFrames: Long,
    val fadeStartFrame: Long,
    val usesLoopFade: Boolean,
) {
    fun durationMs(sampleRate: Int): Long =
        if (sampleRate > 0 && totalFrames > 0L) (totalFrames * 1000L) / sampleRate else 0L
}

internal object NonInteractiveLoopPolicy {

    fun hasValidLoopPoints(
        baseFrames: Long,
        loopStart: Long?,
        loopEnd: Long?,
        hasLoop: Boolean = true,
    ): Boolean = hasLoop &&
        baseFrames > 0L &&
        loopStart != null &&
        loopEnd != null &&
        loopStart >= 0L &&
        loopEnd > loopStart &&
        loopEnd <= baseFrames

    fun computeEffectiveFrames(
        baseFrames: Long,
        loopStart: Long?,
        loopEnd: Long?,
        hasLoop: Boolean,
        sampleRate: Int,
        config: NonInteractiveLoopConfig = NonInteractiveLoopConfig.Default,
    ): EffectiveLoopTiming {
        val safeBase = baseFrames.coerceAtLeast(0L)
        if (safeBase == 0L || sampleRate <= 0 || !hasValidLoopPoints(safeBase, loopStart, loopEnd, hasLoop)) {
            return EffectiveLoopTiming(
                totalFrames = safeBase,
                fadeOutFrames = 0L,
                fadeStartFrame = safeBase,
                usesLoopFade = false,
            )
        }
        val clean = config.normalized()
        val lStart = loopStart!!
        val lEnd = loopEnd!!
        val loopLen = lEnd - lStart
        val requestedFadeFrames = ((clean.fadeOutMs * sampleRate.toLong()) / 1000L).coerceAtLeast(0L)

        return when (clean.mode) {
            NonInteractiveLoopMode.LOOP_COUNT -> {
                val bodyFrames = lEnd + clean.loopCount.toLong() * loopLen
                val total = (bodyFrames + requestedFadeFrames).coerceAtLeast(1L)
                val effectiveFade = requestedFadeFrames.coerceAtMost(total)
                EffectiveLoopTiming(
                    totalFrames = total,
                    fadeOutFrames = effectiveFade,
                    fadeStartFrame = (total - effectiveFade).coerceAtLeast(0L),
                    usesLoopFade = true,
                )
            }
            NonInteractiveLoopMode.TARGET_DURATION -> {
                val total = ((clean.targetDurationMs * sampleRate.toLong()) / 1000L).coerceAtLeast(1L)
                val effectiveFade = requestedFadeFrames.coerceAtMost(total)
                EffectiveLoopTiming(
                    totalFrames = total,
                    fadeOutFrames = effectiveFade,
                    fadeStartFrame = (total - effectiveFade).coerceAtLeast(0L),
                    usesLoopFade = true,
                )
            }
        }
    }

    fun computeEffectiveDurationMs(
        baseFrames: Long,
        loopStart: Long?,
        loopEnd: Long?,
        hasLoop: Boolean,
        sampleRate: Int,
        config: NonInteractiveLoopConfig = NonInteractiveLoopConfig.Default,
    ): Long = computeEffectiveFrames(
        baseFrames = baseFrames,
        loopStart = loopStart,
        loopEnd = loopEnd,
        hasLoop = hasLoop,
        sampleRate = sampleRate,
        config = config,
    ).durationMs(sampleRate)

    fun computeEffectiveDurationMs(
        baseDurationMs: Long,
        baseFrames: Long,
        loopStart: Long?,
        loopEnd: Long?,
        hasLoop: Boolean,
        sampleRate: Int,
        config: NonInteractiveLoopConfig = NonInteractiveLoopConfig.Default,
    ): Long {
        if (!hasValidLoopPoints(baseFrames, loopStart, loopEnd, hasLoop) || sampleRate <= 0) {
            return baseDurationMs.coerceAtLeast(0L)
        }
        return computeEffectiveDurationMs(baseFrames, loopStart, loopEnd, true, sampleRate, config)
    }
}
