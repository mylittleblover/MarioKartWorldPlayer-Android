package com.mylittleblover.mkwplayer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Kotlin owner for the device audio output.
 *
 * Ownership rules come straight from the engine's contract and are enforced
 * here rather than assumed by callers:
 *  - one engine has at most one output consumer at a time
 *  - mutations, binding and seek require the consumer to be stopped
 *  - each handle is destroyed exactly once
 *
 * Uses Android's standard [AudioTrack] (`USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` /
 * `CHANNEL_OUT_STEREO` / `ENCODING_PCM_16BIT` / `MODE_STREAM` / `PERFORMANCE_MODE_NONE`)
 * so AudioFlinger routes speaker, wired, USB-C, and Bluetooth TWS earbuds through the exact
 * same deep-buffer stereo pipeline as mainstream Android music players, avoiding OEM AAudio
 * single-ear / stutter quirks on MIUI/Qualcomm Bluetooth stacks. Falls back to Oboe only if
 * [AudioTrack] cannot initialize.
 */
internal class AudioOutput private constructor(
    private var engine: Long,
    private val handle: Long,
    sampleRate: Int,
    @Volatile
    private var usingAudioTrack: Boolean,
) : SessionAudioOutput {
    var sampleRate: Int = sampleRate
        private set
    @Volatile
    private var closed = false
    @Volatile
    private var suspended = false

    private val lifecycleLock = Any()
    private val trackLock = Any()
    private val pauseLock = Object()
    @Volatile
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var audioTrackBufferBytes: Int = 0
    private var writerThread: Thread? = null
    private val writerRunning = AtomicBoolean(false)
    @Volatile
    private var writerPaused = false
    @Volatile
    private var writerIdle = false
    @Volatile
    private var resumePendingPcm: ShortArray? = null
    @Volatile
    private var resumePendingOffset: Int = 0
    @Volatile
    private var accumulatedUnderruns: Long = 0L

    companion object {
        private const val CHUNK_FRAMES = 2048
        private const val TARGET_BUFFER_FRAMES = 16384
        private const val PREFILL_CHUNKS = 2
        private const val STEREO_PCM16_FRAME_BYTES = 4
        private const val MAX_CONSECUTIVE_ZERO_WRITES = 25

        /**
         * Creates and starts the device output for [engine].
         *
         * Returns null when the device cannot be opened, which is a normal
         * condition on emulators and on devices with no audio route. Callers must
         * treat null as "no device output" and fall back to control rendering,
         * not as a fatal error.
         */
        fun open(engine: Long, sampleRate: Int): AudioOutput? {
            if (engine == 0L) return null
            val targetRate = if (sampleRate in 8000..192_000) sampleRate else 48_000
            val handle = NativeAudioLibrary.createOutput(engine)
            if (handle == 0L) return null

            val out = AudioOutput(engine, handle, targetRate, usingAudioTrack = false)
            if (out.startAudioTrack(targetRate)) {
                return out
            }
            if (NativeAudioLibrary.startOutput(handle, targetRate)) {
                return out
            }
            NativeAudioLibrary.destroyOutput(handle)
            return null
        }

        private fun createTrack(sampleRate: Int, bufferBytes: Int, performanceMode: Int): AudioTrack? {
            return runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferBytes)
                    .setPerformanceMode(performanceMode)
                    .build()
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    track.release()
                    null
                } else {
                    track
                }
            }.getOrNull()
        }

        private fun buildAudioTrack(sampleRate: Int): Pair<AudioTrack, Int>? {
            val minBytes = runCatching {
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            }.getOrDefault(AudioTrack.ERROR_BAD_VALUE)
            if (minBytes <= 0) return null
            val rawBytes = maxOf(minBytes * 4, TARGET_BUFFER_FRAMES * STEREO_PCM16_FRAME_BYTES)
            val bufferBytes = (rawBytes + STEREO_PCM16_FRAME_BYTES - 1) and (STEREO_PCM16_FRAME_BYTES - 1).inv()
            // Request deep-buffer output (AUDIO_OUTPUT_FLAG_DEEP_BUFFER) first for maximum
            // Bluetooth A2DP / TWS stability on Qualcomm/MIUI HALs; fall back to NONE if refused.
            val track = createTrack(sampleRate, bufferBytes, AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                ?: createTrack(sampleRate, bufferBytes, AudioTrack.PERFORMANCE_MODE_NONE)
                ?: return null
            return track to bufferBytes
        }
    }

    /**
     * Pre-fills [track] with rendered PCM before calling [AudioTrack.play] so AudioFlinger never
     * starts pulling from an empty ring buffer (which triggers immediate underruns during BT A2DP
     * stream startup). Returns the start offset of any unconsumed tail remaining in [pcm]
     * (`pcm.size` when all pre-filled chunks wrote completely), or -1 on fatal write/render error.
     */
    private fun prefillAndPlay(track: AudioTrack, bufferBytes: Int, pcm: ShortArray): Int {
        if (track.state != AudioTrack.STATE_INITIALIZED) return -1
        val maxPrefillShorts = ((bufferBytes / 4) and 1.inv()).coerceAtLeast(0)
        var prefilledShorts = 0
        var pendingOffset = pcm.size
        var chunks = 0
        while (chunks < PREFILL_CHUNKS && prefilledShorts + pcm.size <= maxPrefillShorts) {
            if (!NativeAudioLibrary.renderOutputPcm16(handle, pcm, CHUNK_FRAMES)) {
                return -1
            }
            val written = runCatching {
                track.write(pcm, 0, pcm.size, AudioTrack.WRITE_NON_BLOCKING)
            }.getOrDefault(AudioTrack.ERROR_DEAD_OBJECT)
            if (written < 0) return -1
            prefilledShorts += written
            chunks++
            if (written < pcm.size) {
                pendingOffset = written
                break
            }
        }
        val started = runCatching {
            track.play()
            track.playState == AudioTrack.PLAYSTATE_PLAYING
        }.getOrDefault(false)
        return if (started) pendingOffset else -1
    }

    private fun pauseAndFlushWriter(): Boolean {
        if (!writerRunning.get() || audioTrack == null) return false
        synchronized(pauseLock) {
            writerPaused = true
        }
        synchronized(trackLock) {
            val track = audioTrack ?: return false
            runCatching { track.pause() }
            runCatching { track.flush() }
        }
        val parked = synchronized(pauseLock) {
            val deadline = System.nanoTime() + 500_000_000L
            while (!writerIdle && writerRunning.get()) {
                val remainingMs = (deadline - System.nanoTime()) / 1_000_000L
                if (remainingMs <= 0L) break
                runCatching { pauseLock.wait(remainingMs) }
            }
            writerIdle && writerRunning.get()
        }
        if (!parked) return false
        // Flush a second time once the writer is guaranteed parked so any chunk that unblocked
        // from WRITE_BLOCKING during the first flush is completely discarded before resume.
        synchronized(trackLock) {
            val track = audioTrack ?: return false
            runCatching { track.flush() }
        }
        synchronized(pauseLock) {
            resumePendingPcm = null
            resumePendingOffset = 0
        }
        return true
    }

    private fun resumePausedWriter(track: AudioTrack, bufferBytes: Int): Boolean {
        val prefillPcm = ShortArray(CHUNK_FRAMES * 2)
        val pendingOffset = prefillAndPlay(track, bufferBytes, prefillPcm)
        if (pendingOffset < 0) return false
        synchronized(pauseLock) {
            resumePendingPcm = if (pendingOffset < prefillPcm.size) prefillPcm else null
            resumePendingOffset = pendingOffset
            writerPaused = false
            writerIdle = false
            pauseLock.notifyAll()
        }
        return true
    }

    private fun startAudioTrack(rate: Int): Boolean {
        val targetRate = if (rate in 8000..192_000) rate else 48_000
        val built = buildAudioTrack(targetRate) ?: return false
        val (track, bufferBytes) = built
        if (!NativeAudioLibrary.attachExternalOutput(handle, targetRate)) {
            runCatching { track.release() }
            return false
        }
        val initialPcm = ShortArray(CHUNK_FRAMES * 2)
        val initialPendingOffset = prefillAndPlay(track, bufferBytes, initialPcm)
        if (initialPendingOffset < 0) {
            runCatching { track.release() }
            NativeAudioLibrary.stopOutput(handle)
            return false
        }
        synchronized(trackLock) {
            accumulatedUnderruns = 0L
            audioTrack = track
            audioTrackBufferBytes = bufferBytes
        }
        synchronized(pauseLock) {
            writerPaused = false
            writerIdle = false
            resumePendingPcm = null
            resumePendingOffset = 0
        }
        usingAudioTrack = true
        this.sampleRate = targetRate
        writerRunning.set(true)
        val thread = Thread(
            {
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
                val pcm = initialPcm
                var activeTrack = track
                var pendingOffset = initialPendingOffset

                fun writeRemaining(startOffset: Int): Boolean {
                    var offset = startOffset
                    var zeroRetries = 0
                    while (offset < pcm.size && writerRunning.get() && !writerPaused) {
                        val written = runCatching {
                            activeTrack.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                        }.getOrDefault(AudioTrack.ERROR_DEAD_OBJECT)
                        if (writerPaused) return true
                        when {
                            written < 0 -> {
                                if (!writerRunning.get() || writerPaused) return writerPaused
                                val aligned = offset and 1.inv()
                                val recovered = recoverTrack(activeTrack, targetRate, pcm, aligned) ?: return false
                                activeTrack = recovered.first
                                offset = recovered.second
                                zeroRetries = 0
                            }
                            written == 0 -> {
                                if (!writerPaused && runCatching { activeTrack.playState != AudioTrack.PLAYSTATE_PLAYING }.getOrDefault(false)) {
                                    runCatching { activeTrack.play() }
                                }
                                zeroRetries++
                                if (zeroRetries >= MAX_CONSECUTIVE_ZERO_WRITES) {
                                    if (!writerRunning.get() || writerPaused) return writerPaused
                                    val aligned = offset and 1.inv()
                                    val recovered = recoverTrack(activeTrack, targetRate, pcm, aligned) ?: return false
                                    activeTrack = recovered.first
                                    offset = recovered.second
                                    zeroRetries = 0
                                } else {
                                    try {
                                        Thread.sleep(2L)
                                    } catch (_: InterruptedException) {
                                        return writerPaused
                                    }
                                }
                            }
                            else -> {
                                offset += written
                                zeroRetries = 0
                            }
                        }
                    }
                    return writerRunning.get()
                }

                fun parkIfPaused(): Boolean {
                    if (!writerPaused) return true
                    var pending: ShortArray? = null
                    var offset = 0
                    synchronized(pauseLock) {
                        while (writerPaused && writerRunning.get()) {
                            writerIdle = true
                            pauseLock.notifyAll()
                            try {
                                pauseLock.wait()
                            } catch (_: InterruptedException) {
                                if (!writerRunning.get()) break
                            }
                        }
                        writerIdle = false
                        pending = resumePendingPcm
                        offset = resumePendingOffset
                        resumePendingPcm = null
                        resumePendingOffset = 0
                    }
                    if (!writerRunning.get()) return false
                    activeTrack = synchronized(trackLock) { audioTrack } ?: return false
                    val pendingBuf = pending
                    if (pendingBuf != null && offset < pendingBuf.size) {
                        System.arraycopy(pendingBuf, 0, pcm, 0, minOf(pendingBuf.size, pcm.size))
                        if (!writeRemaining(offset)) return false
                    }
                    return true
                }

                try {
                    if (pendingOffset < pcm.size && !writeRemaining(pendingOffset)) {
                        return@Thread
                    }
                    while (writerRunning.get()) {
                        if (writerPaused) {
                            if (!parkIfPaused()) break
                            continue
                        }
                        if (!NativeAudioLibrary.renderOutputPcm16(handle, pcm, CHUNK_FRAMES)) {
                            if (writerPaused) continue
                            break
                        }
                        if (writerPaused) continue
                        if (!writeRemaining(0)) {
                            break
                        }
                    }
                } finally {
                    synchronized(pauseLock) {
                        writerIdle = true
                        pauseLock.notifyAll()
                    }
                    // Abnormal exit (e.g. recoverTrack exhausted all retries or native render stopped):
                    // release any remaining track and stop native output so isRunning() does not stay true.
                    if (writerRunning.compareAndSet(true, false)) {
                        synchronized(trackLock) {
                            val current = audioTrack
                            audioTrack = null
                            audioTrackBufferBytes = 0
                            if (current != null) {
                                accumulatedUnderruns += runCatching { current.underrunCount.toLong() }.getOrDefault(0L)
                                runCatching { current.stop() }
                                runCatching { current.release() }
                            }
                            usingAudioTrack = false
                        }
                        if (!closed) {
                            NativeAudioLibrary.stopOutput(handle)
                        }
                    }
                }
            },
            "mkw-audiotrack",
        ).apply {
            isDaemon = true
            start()
        }
        writerThread = thread
        return true
    }

    /**
     * Rebuilds a dead [AudioTrack] and pre-fills the in-flight [pendingPcm] slice from [pendingOffset]
     * before calling [AudioTrack.play], preserving chronological sample order without dropping data.
     */
    private fun recoverTrack(
        failed: AudioTrack,
        targetRate: Int,
        pendingPcm: ShortArray,
        pendingOffset: Int,
    ): Pair<AudioTrack, Int>? {
        synchronized(trackLock) {
            if (audioTrack === failed) {
                accumulatedUnderruns += runCatching { failed.underrunCount.toLong() }.getOrDefault(0L)
                audioTrack = null
            }
            runCatching { failed.stop() }
            runCatching { failed.release() }
        }
        val retryDelays = longArrayOf(120L, 200L, 350L, 500L)
        for (delayMs in retryDelays) {
            if (!writerRunning.get() || writerPaused) return null
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                return null
            }
            if (!writerRunning.get() || writerPaused) return null
            val built = buildAudioTrack(targetRate) ?: continue
            val (rebuilt, bufferBytes) = built
            var nextOffset = pendingOffset
            if (nextOffset < pendingPcm.size) {
                val prefilled = runCatching {
                    rebuilt.write(pendingPcm, nextOffset, pendingPcm.size - nextOffset, AudioTrack.WRITE_NON_BLOCKING)
                }.getOrDefault(AudioTrack.ERROR_DEAD_OBJECT)
                if (prefilled < 0) {
                    runCatching { rebuilt.release() }
                    continue
                }
                nextOffset += prefilled
            }
            val started = runCatching {
                rebuilt.play()
                true
            }.getOrDefault(false)
            if (!started) {
                runCatching { rebuilt.release() }
                continue
            }
            synchronized(trackLock) {
                if (!writerRunning.get() || writerPaused) {
                    runCatching { rebuilt.stop() }
                    runCatching { rebuilt.release() }
                    return null
                }
                audioTrack = rebuilt
                audioTrackBufferBytes = bufferBytes
            }
            return rebuilt to nextOffset
        }
        return null
    }

    private fun stopAudioTrack() {
        writerRunning.set(false)
        synchronized(pauseLock) {
            writerPaused = false
            pauseLock.notifyAll()
        }
        synchronized(trackLock) {
            val track = audioTrack
            if (track != null) {
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.stop() }
            }
        }
        val thread = writerThread
        if (thread != null && thread !== Thread.currentThread()) {
            thread.interrupt()
            runCatching { thread.join(1000L) }
        }
        writerThread = null
        synchronized(trackLock) {
            val track = audioTrack
            audioTrack = null
            audioTrackBufferBytes = 0
            if (track != null) {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
        usingAudioTrack = false
    }

    /** Bytes-per-frame the callback writes: stereo interleaved PCM. */
    val channelCount: Int = 2

    /**
     * True once the stream reports a non-zero device rate.
     *
     * `outputSnapshot` returns `[rate, lastError, rebuilds, callbacks]`; a zero
     * rate means the device never opened, not that audio is silent.
     */
    fun isRunning(): Boolean =
        !closed && !suspended && (!usingAudioTrack || writerRunning.get()) && (snapshotRate() ?: 0) > 0

    private fun snapshotRate(): Int? =
        NativeAudioLibrary.outputSnapshot(handle)?.takeIf { it.isNotEmpty() }?.get(0)?.toInt()

    /** Output-reported fields: rate, last error, rebuild count, callback count. */
    fun snapshot(): LongArray? = if (closed) null else NativeAudioLibrary.outputSnapshot(handle)

    /**
     * Callback timing packet: `[wire, capacity, captured, total, missed,
     * clockFailures, clipped, saturated, cpu p50/p95/p99/p999/max, wall p50/p95/
     * p99/p999/max, xruns, budgetNanos]`.
     *
     * Returns null once closed, or when the native side has no packet. Callers pass
     * this to [TimingDiagnosis.read], which refuses an unexpected wire version.
     */
    override fun timingSnapshot(): LongArray? {
        if (closed) return null
        val snapshot = NativeAudioLibrary.outputTimingSnapshot(handle) ?: return null
        if (usingAudioTrack && snapshot.size >= 19) {
            val totalUnderruns = synchronized(trackLock) {
                accumulatedUnderruns + runCatching { audioTrack?.underrunCount?.toLong() ?: 0L }.getOrDefault(0L)
            }
            snapshot[18] = totalUnderruns
        }
        return snapshot
    }

    /**
     * Rebuilds the stream explicitly. Returns false when the device refused;
     * the caller should then stop and reopen rather than render into a dead stream.
     */
    fun rebuild(rate: Int): Boolean = synchronized(lifecycleLock) {
        if (closed || suspended) return false
        if (usingAudioTrack) {
            val targetRate = if (rate in 8000..192_000) rate else sampleRate
            stopAudioTrack()
            NativeAudioLibrary.stopOutput(handle)
            return startAudioTrack(targetRate)
        }
        return NativeAudioLibrary.rebuildOutput(handle, rate)
    }

    /**
     * Route-change hook from [PlaybackService]. Standard [AudioTrack] is rerouted seamlessly by
     * AudioFlinger across speaker, wired, and Bluetooth TWS earbuds without tearing down the
     * stream (tearing down mid-TWS handshake causes single-ear dropouts); only Oboe fallback needs
     * an explicit stream rebuild.
     */
    override fun rebuildStream(sampleRate: Int): Boolean = synchronized(lifecycleLock) {
        if (closed || suspended) return false
        if (usingAudioTrack) return true
        return NativeAudioLibrary.rebuildOutput(handle, sampleRate)
    }

    /**
     * Pauses and flushes the active [AudioTrack] in place and releases the engine output lease
     * without destroying the [AudioTrack], AudioFlinger session, or writer thread (eliminating
     * 80-250 ms teardown/reopen latency on every seek).
     */
    override fun suspendStream(): Boolean = synchronized(lifecycleLock) {
        if (closed) return false
        if (suspended) return true
        val wasAudioTrack = usingAudioTrack
        if (wasAudioTrack && pauseAndFlushWriter()) {
            NativeAudioLibrary.stopOutput(handle)
            suspended = true
            return true
        }
        stopAudioTrack()
        NativeAudioLibrary.stopOutput(handle)
        usingAudioTrack = wasAudioTrack
        suspended = true
        return true
    }

    /** Restarts a previously suspended stream, reusing the live paused [AudioTrack] when rate matches. */
    override fun resumeStream(sampleRate: Int): Boolean = synchronized(lifecycleLock) {
        if (closed) return false
        if (!suspended) return true
        val targetRate = if (sampleRate in 8000..192_000) sampleRate else this.sampleRate
        val existingTrack = synchronized(trackLock) { audioTrack }
        if (usingAudioTrack &&
            existingTrack != null &&
            targetRate == this.sampleRate &&
            writerRunning.get() &&
            writerPaused
        ) {
            if (NativeAudioLibrary.attachExternalOutput(handle, targetRate) &&
                resumePausedWriter(existingTrack, audioTrackBufferBytes)
            ) {
                suspended = false
                return true
            }
            stopAudioTrack()
            NativeAudioLibrary.stopOutput(handle)
        }
        if (startAudioTrack(targetRate)) {
            suspended = false
            return true
        }
        usingAudioTrack = false
        if (!NativeAudioLibrary.startOutput(handle, targetRate)) {
            return false
        }
        this.sampleRate = targetRate
        suspended = false
        return true
    }

    private fun swapExternalEngineLocked(engineHandle: Long, targetRate: Int): Boolean {
        if (engineHandle == engine) return true
        if (NativeAudioLibrary.attachExternalOutput(handle, targetRate)) {
            if (!NativeAudioLibrary.rebindOutput(handle, engineHandle, targetRate)) {
                NativeAudioLibrary.stopOutput(handle)
                return false
            }
            engine = engineHandle
            return true
        }
        if (!NativeAudioLibrary.rebindOutput(handle, engineHandle, targetRate)) {
            return false
        }
        engine = engineHandle
        NativeAudioLibrary.stopOutput(handle)
        return true
    }

    private fun rebuildAudioTrackForEngineLocked(engineHandle: Long, targetRate: Int): Boolean {
        stopAudioTrack()
        if (!swapExternalEngineLocked(engineHandle, targetRate)) {
            return false
        }
        if (startAudioTrack(targetRate)) {
            suspended = false
            return true
        }
        NativeAudioLibrary.stopOutput(handle)
        usingAudioTrack = false
        if (NativeAudioLibrary.startOutput(handle, targetRate)) {
            this.sampleRate = targetRate
            suspended = false
            return true
        }
        return false
    }

    /**
     * Hot-swaps the bound native engine. When [flush] is true (manual skip / track tap), pauses and
     * flushes the deep-buffer [AudioTrack] in place so the new track is audible immediately instead
     * of draining 341-600 ms of the previous track; when false (natural end), swaps gaplessly.
     */
    override fun rebindEngine(engineHandle: Long, sampleRate: Int): Boolean =
        rebindEngine(engineHandle, sampleRate, flush = false)

    override fun rebindEngine(engineHandle: Long, sampleRate: Int, flush: Boolean): Boolean = synchronized(lifecycleLock) {
        if (closed || engineHandle == 0L) return false
        val targetRate = if (sampleRate in 8000..192_000) sampleRate else this.sampleRate
        if (usingAudioTrack && !suspended) {
            val liveTrack = synchronized(trackLock) { audioTrack }
            if (liveTrack != null && targetRate == this.sampleRate && writerRunning.get() && !writerPaused) {
                if (flush) {
                    if (pauseAndFlushWriter()) {
                        if (!NativeAudioLibrary.rebindOutput(handle, engineHandle, targetRate)) {
                            if (!resumePausedWriter(liveTrack, audioTrackBufferBytes)) {
                                stopAudioTrack()
                                NativeAudioLibrary.stopOutput(handle)
                                startAudioTrack(this.sampleRate)
                            }
                            return false
                        }
                        engine = engineHandle
                        if (resumePausedWriter(liveTrack, audioTrackBufferBytes)) {
                            return true
                        }
                    }
                    // If pauseAndFlushWriter or resumePausedWriter failed, never leave the writer
                    // paused on the old track; tear down and reopen cleanly on the new engine.
                    return rebuildAudioTrackForEngineLocked(engineHandle, targetRate)
                }
                if (!NativeAudioLibrary.rebindOutput(handle, engineHandle, targetRate)) {
                    return false
                }
                engine = engineHandle
                return true
            }
            return rebuildAudioTrackForEngineLocked(engineHandle, targetRate)
        }
        if (usingAudioTrack && suspended) {
            val pausedTrack = synchronized(trackLock) { audioTrack }
            if (pausedTrack != null && targetRate == this.sampleRate && writerRunning.get() && writerPaused) {
                if (NativeAudioLibrary.attachExternalOutput(handle, targetRate)) {
                    if (engineHandle != engine && !NativeAudioLibrary.rebindOutput(handle, engineHandle, targetRate)) {
                        NativeAudioLibrary.stopOutput(handle)
                        return false
                    }
                    engine = engineHandle
                    if (resumePausedWriter(pausedTrack, audioTrackBufferBytes)) {
                        suspended = false
                        return true
                    }
                }
            }
            return rebuildAudioTrackForEngineLocked(engineHandle, targetRate)
        }
        if (!NativeAudioLibrary.rebindOutput(handle, engineHandle, targetRate)) {
            return false
        }
        engine = engineHandle
        this.sampleRate = targetRate
        suspended = false
        return true
    }

    /** Stops and releases the consumer. Safe to call more than once. */
    override fun close() = synchronized(lifecycleLock) {
        if (closed) return
        closed = true
        stopAudioTrack()
        if (!suspended) {
            NativeAudioLibrary.stopOutput(handle)
        }
        suspended = false
        NativeAudioLibrary.destroyOutput(handle)
    }
}
