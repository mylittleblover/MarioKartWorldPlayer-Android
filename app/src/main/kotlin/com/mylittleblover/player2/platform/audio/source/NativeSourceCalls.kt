package com.mylittleblover.player2.platform.audio.source

/**
 * JNI boundary for the standalone audio-source layer.
 *
 * The compiled library exports these as
 * `Java_com_mylittleblover_player2_platform_audio_source_NativeSourceCalls_*`,
 * so the package and object name are pinned by the native side and must not
 * change. Same rule as [com.mylittleblover.player2.platform.audio.NativeAudioLibrary].
 *
 * Used to reach the decoder directly, without the Engine, so a decode failure
 * can be told apart from an engine-side rejection.
 */
internal object NativeSourceCalls {
    init { System.loadLibrary("mkw_native") }

    external fun providerIds(): Array<String>
    external fun probe(bytes: ByteArray): String?

    /**
     * Returns `[status, handle]`. Every provider id here must match
     * `audioProviderIds()` exactly; a wrong id yields InvalidRequest, not a
     * fallback guess.
     */
    external fun open(
        provider: String, bytes: ByteArray,
        entry: String?, subsong: String?, variant: String?,
        resourceIds: Array<String>, resourceBytes: Array<ByteArray>,
        maximumInput: Int,
    ): LongArray

    /** Returns `[status, ...metadata fields]` for an open handle. */
    external fun metadata(value: Long): LongArray

    external fun entries(value: Long): Array<String>
    external fun subsongs(value: Long): Array<String>

    /** Renders into `output`; returns the number of frames produced. */
    external fun render(value: Long, frames: Int, output: FloatArray): Int
    external fun seek(value: Long, position: Long): Int
    external fun position(value: Long): Long
    external fun reset(value: Long): Int
    external fun close(value: Long)
    external fun streamInfo(value: Long): LongArray
    external fun setStreamGroups(value: Long, mute: Long, solo: Long): Int
}
