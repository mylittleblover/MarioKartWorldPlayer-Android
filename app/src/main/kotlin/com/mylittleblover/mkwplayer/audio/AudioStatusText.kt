package com.mylittleblover.mkwplayer.audio

/**
 * Human-readable names for the native status codes.
 *
 * The engine reports `EngineStatus` ordinals (0..11) and `SourceStatus` ordinals.
 * Showing a raw number in the UI is how a real failure gets mistaken for a
 * successful no-op, so every code a user can encounter is named here.
 *
 * Anything outside the known range is reported as UNKNOWN with the raw value,
 * never silently coerced to "Ok".
 */
object AudioStatusText {

    /** EngineStatus, from engine/native/api/EngineTypes.hpp. */
    private val engine = mapOf(
        0 to "Ok",
        1 to "InvalidRequest",
        2 to "MissingAsset",
        3 to "QueueFull",
        4 to "UnsupportedExecution",
        5 to "VoiceLimit",
        6 to "ResourceLimit",
        7 to "OutputActive",
        8 to "RuntimeActive",
        9 to "MissingCue",
        10 to "DeadlineMiss",
        11 to "SourceMismatch",
    )

    fun engine(code: Int): String = engine[code] ?: "UNKNOWN($code)"

    fun engineIsOk(code: Int): Boolean = code == 0

    /**
     * Explains the two statuses users will actually hit, in terms of what they
     * can do about it rather than what the enum is called.
     */
    fun engineHint(code: Int): String? = when (code) {
        0 -> null
        1 -> "The engine rejected the request. The file may be a format this build cannot decode."
        2 -> "The graph has no asset with that id."
        6 -> "Ran out of memory or a decode budget."
        7 -> "Stop the current output before changing sources."
        8 -> "Stop playback before changing the runtime."
        11 -> "The file does not match the graph's expected checksum."
        else -> null
    }

    /** SourceStatus, from engine/native/api/AudioSourceTypes.hpp. */
    private val source = mapOf(
        0 to "Ok",
        1 to "UnsupportedFormat",
        2 to "MalformedSource",
        3 to "ResourceLimit",
        4 to "UnsupportedCodec",
        5 to "UnsupportedChannels",
        6 to "UnsupportedSampleRate",
        7 to "SourceLoopBounds",
        8 to "SeekUnsupported",
        9 to "EntryMissing",
        10 to "SubsongMissing",
        11 to "VariantMissing",
        12 to "InvalidRequest",
        13 to "InputUnavailable",
        14 to "InputChanged",
    )

    fun source(code: Int): String = source[code] ?: "UNKNOWN($code)"

    /**
     * `UnsupportedCodec` is what the 253 kind=2 BWAV files return. Saying so
     * plainly matters: it is a known limitation, not a corrupt file.
     */
    fun sourceHint(code: Int): String? = when (code) {
        0 -> null
        1 -> "This container format is not supported yet."
        2 -> "The file is malformed or truncated."
        4 -> "This audio codec is not supported yet."
        5 -> "Unsupported channel count."
        6 -> "Unsupported sample rate."
        9 -> "No matching entry inside the container."
        else -> null
    }
}
