package com.mylittleblover.player2.platform.audio

/**
 * Android library-loading boundary. The native Engine owns all audio semantics;
 * this object only marshals calls across JNI.
 *
 * ## Why this package name
 *
 * The compiled native library exports 74 `Java_com_mylittleblover_player2_*`
 * symbols (verified with llvm-nm on the built arm64-v8a artifact). A JNI symbol
 * is bound to a fully qualified Kotlin package and class name, so this object
 * MUST stay at `com.mylittleblover.player2.platform.audio.NativeAudioLibrary`.
 * Renaming it to the app's own package would require editing every native
 * function name in seven C++ files, which buys nothing and risks a silent
 * `NoSuchMethodError` at first call.
 *
 * The app's own code lives under `com.mylittleblover.mkwplayer`; this is the
 * only package that mirrors upstream, and only because JNI pins it.
 *
 * ## Provenance
 *
 * Copied from PLAYER2 (mylittleblover/player2-android-new) with the repository
 * owner's explicit authorization. See `LICENSE` and `NOTICE-THIRD-PARTY.md`.
 *
 * ## Threading
 *
 * Every call belongs to the owning control thread. The single output consumer is
 * created by [createOutput] and is unique by construction.
 */
internal object NativeAudioLibrary {
    init { System.loadLibrary("mkw_native") }

    external fun transportSampleRate(): Int

    // Control-thread calls; the owner must destroy each handle exactly once.
    external fun createEngine(): Long
    external fun destroyEngine(handle: Long)
    external fun loadCompiledGraph(handle: Long, packet: ByteArray): Int
    external fun engineSampleRate(handle: Long): Int

    external fun loadLinear(
        handle: Long, provider: String, bytes: ByteArray, loop: Boolean, preparation: Int,
        assetId: String, entryId: String?, subsongId: String?, variantId: String?,
    ): Int

    external fun bindSource(
        handle: Long, assetIndex: Int, provider: String, bytes: ByteArray, preparation: Int,
        assetId: String, entryId: String?, subsongId: String?, variantId: String?,
    ): Int

    external fun command(
        handle: Long, kind: Int, target: Int, value: Int, number: Double, at: Long,
    ): Int

    external fun loadLinearInput(
        handle: Long, provider: String, input: java.io.FileDescriptor, loop: Boolean,
        preparation: Int, assetId: String, entryId: String?, subsongId: String?,
        variantId: String?, expectedDigest: ByteArray?,
    ): Int

    external fun bindSourceInput(
        handle: Long, assetIndex: Int, provider: String, input: java.io.FileDescriptor,
        preparation: Int, assetId: String, entryId: String?, subsongId: String?,
        variantId: String?, expectedDigest: ByteArray?,
    ): Int

    external fun render(handle: Long, output: FloatArray, frames: Int): Int
    external fun renderRealtime(handle: Long, output: FloatArray, frames: Int): Int
    external fun pauseDecodeWorkerHarness(handle: Long, pause: Boolean): Int

    external fun busSnapshot(handle: Long, index: Int): LongArray
    external fun playlistGroupSnapshot(handle: Long, index: Int): LongArray
    external fun playlistDecisionSnapshot(handle: Long, index: Int): LongArray
    external fun parameterSnapshot(handle: Long, index: Int): LongArray
    external fun snapshot(handle: Long): LongArray
    external fun enableCueSearchMonitorV9(handle: Long): Int
    external fun transitionSnapshotV9(handle: Long): LongArray
    external fun streamingSnapshot(handle: Long): LongArray
    external fun entryStateSnapshot(handle: Long): LongArray
    external fun performanceSnapshot(handle: Long): LongArray
    external fun layerGainSnapshot(handle: Long, index: Int): LongArray
    external fun effectSnapshot(handle: Long, index: Int): LongArray
    external fun albumFlowSnapshot(handle: Long): LongArray
    external fun gameSyncSnapshot(handle: Long): LongArray
    external fun automaticSnapshot(handle: Long): LongArray
    external fun musicalLoopSnapshot(handle: Long): LongArray
    external fun sourceSnapshot(handle: Long, voiceSlot: Int): LongArray

    external fun setLinearStreamGroups(
        handle: Long, mute: Long, solo: Long, expectedRoot: ByteArray,
    ): Int

    external fun setLinearModuleChannels(handle: Long, mute: Long, solo: Long): Int
    external fun setLinearRepeat(handle: Long, enabled: Boolean, begin: Long, end: Long): Int
    external fun seek(handle: Long, frame: Long): Int

    // Output lifetime. Exactly one consumer may exist at a time.
    external fun createOutput(handle: Long): Long
    external fun destroyOutput(handle: Long)
    external fun startOutput(handle: Long, rate: Int): Boolean
    external fun stopOutput(handle: Long)
    external fun rebuildOutput(handle: Long, rate: Int): Boolean
    external fun outputSnapshot(handle: Long): LongArray
    external fun outputTimingSnapshot(handle: Long): LongArray?
}
