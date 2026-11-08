package com.mylittleblover.mkwplayer.library

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder

/**
 * The five parts of a game track's music, as separate files.
 *
 * A Mario Kart World stage theme is not one file. It is a set of parts the game switches
 * between while you race: a short prelude, the full prelude, a bridge, the main loop, and one
 * or more outros. Measured on `BGM_Stage_Coaster`:
 *
 * ```
 * Pre_Short   534,857 frames   11.143 s   loop 205714..534857
 * Pre       2,427,426 frames   50.571 s   loop 452571..2427426
 * Bridge       88,320 frames    1.840 s   no loop
 * Main      3,373,711 frames   70.286 s   loop 493714..3373711
 * Outro00     226,286 frames    4.714 s   no loop
 * ```
 *
 * [timing] is the part that is *not* measured. When the real game enters the bridge, or
 * which outro it picks on which lap, is game behaviour, and this project has resource facts
 * and audio facts but no game-behaviour evidence. So a flow always carries an explicit
 * [Provenance] and the UI has to show it.
 */
internal data class StageFlow(
    /** Logical name, for display and for matching files. */
    val name: String,
    val parts: List<Part>,
    val timing: Timing,
    val provenance: Provenance = Provenance.PROVISIONAL,
) {
    enum class Role {
        /** Short prelude, used when the race starts abruptly. */
        PreShort,

        /** Full prelude, used on a normal start. */
        Pre,

        /** Bridge section, played between the prelude and the main loop. */
        Bridge,

        /** The main loop. */
        Main,

        /** An ending. The game has several; which one is used is not established. */
        Outro,

        /**
         * The fast arrangement of [Main].
         *
         * The archive carries `BGM_Stage_<Stage>_Fast.bwav` alongside the main file for 61 stages. It is the
         * same music at a faster tempo, played while the race is running behind, and the game switches to it
         * and back without stopping.
         */
        Fast,
    }

    data class Part(
        val role: Role,
        /** File name as picked, e.g. `BGM_Stage_Coaster_Bridge.bwav`. */
        val fileName: String,
        /** Measured from the BWAV header. */
        val frames: Long,
        val sampleRate: Int,
        val channels: Int,
        /** Measured loop window, or null when the part does not loop. */
        val loopStart: Long?,
        val loopEnd: Long?,
        /**
         * Which outro this is, for [Role.Outro] parts.
         *
         * A stage has several endings and the game picks one; keeping the number lets a flow
         * offer them as choices instead of silently keeping whichever file was read first.
         * `null` for every non-outro role.
         */
        val outroNumber: Int? = null,
        /**
         * Which arrangement of the same music this part is.
         *
         * `BGM_Stage_DonkeyKong_Pattern2.bwav` and `BGM_Stage_DonkeyKong.bwav` are the same course's music
         * in different versions, and the game switches between them while playing. 8 files carry a pattern
         * number today. `null` means the part is the base arrangement.
         *
         * Kept apart from [outroNumber] because the two answer different questions: an outro is where the
         * performance ends, a pattern is which version of the same section is playing.
         */
        val pattern: Int? = null,
    ) {
        val hasLoop: Boolean get() = loopStart != null && loopEnd != null
        val seconds: Double get() = if (sampleRate <= 0) 0.0 else frames.toDouble() / sampleRate

        /** Stable label, so two outros never look like the same part in a list. */
        val label: String
            get() = when {
                outroNumber != null -> "Outro%02d".format(outroNumber)
                pattern != null -> "${role.name} %02d".format(pattern)
                else -> role.name
            }
    }

    /**
     * How a flow is assembled.
     *
     * Every field here is a *choice this project made*, not a fact read from the game. They
     * are named so that a later reader can tell them apart from the measured numbers in
     * [Part].
     */
    data class Timing(
        /** Sync point for leaving the prelude. */
        val preludeExit: MusicGraphBuilder.SyncPoint,
        /** Sync point for the prelude-to-main move, and whether the bridge is used. */
        val bridgeEntry: MusicGraphBuilder.SyncPoint,
        /** Sync point for requesting the outro. */
        val outroEntry: MusicGraphBuilder.SyncPoint,
        /**
         * Sync point for switching from one outro to another.
         *
         * A request that has not been executed yet can be replaced; once the source has left,
         * it cannot. Using `Immediate` here makes a replacement land on the next buffer.
         */
        val outroSwitch: MusicGraphBuilder.SyncPoint = MusicGraphBuilder.SyncPoint.Immediate,
        /** How many main-loop passes the storefront plays before offering the outro. */
        val mainRepeats: Int,
        /** Fade applied when a bridge transition starts. */
        val bridgeFadeOutBeats: Double,
        /** Fade applied when the bridge hands over to the main loop. */
        val bridgeFadeInBeats: Double,
        /**
         * Fade applied when crossing between the main loop and its fast arrangement.
         *
         * A switch between two arrangements of the same music has to be a crossfade, not a cut: the two
         * parts do not share a sample position, so a hard cut lands mid-phrase. Zero means "no fade", which
         * is a choice worth stating explicitly rather than defaulting to silence.
         */
        val fastFadeBeats: Double = 0.0,
    )

    /** Where the numbers came from. */
    enum class Provenance {
        /**
         * Assembled by this project from measured parts. The timings are plausible and
         * playable, but they are **not** a reproduction of the game's behaviour.
         */
        PROVISIONAL,

        /** Some external evidence established the transitions. None exists yet. */
        VERIFIED,
    }

    val isProvisional: Boolean get() = provenance == Provenance.PROVISIONAL

    fun part(role: Role): Part? = parts.firstOrNull { it.role == role }

    /** Every outro this flow offers, in file order. Empty when the flow has none. */
    fun outros(): List<Part> = parts.filter { it.role == Role.Outro }

    /** A specific outro by number, or null when the flow does not carry it. */
    fun outro(number: Int): Part? = parts.firstOrNull { it.role == Role.Outro && it.outroNumber == number }

    /** True when every part the flow needs is present. */
    fun isComplete(): Boolean =
        part(Role.Pre) != null && part(Role.Main) != null &&
            (part(Role.Bridge) == null || part(Role.Main) != null)

    companion object {
        /**
         * The default provisional timing.
         *
         * Chosen so a listener hears the structure clearly: the prelude exits at its own end,
         * the bridge is entered on the next bar so the switch is audible, and the outro is
         * requested on a bar for the same reason.
         */
        val DEFAULT_TIMING = Timing(
            preludeExit = MusicGraphBuilder.SyncPoint.ExitCue,
            bridgeEntry = MusicGraphBuilder.SyncPoint.NextBar,
            outroEntry = MusicGraphBuilder.SyncPoint.NextBar,
            outroSwitch = MusicGraphBuilder.SyncPoint.Immediate,
            mainRepeats = 2,
            bridgeFadeOutBeats = 0.0,
            bridgeFadeInBeats = 0.0,
        )
    }
}

/**
 * Reads the parts of a flow from real BWAV bytes.
 *
 * Only measured values are taken from the files: frames, rate, channels and the loop window.
 * Anything the header does not state stays absent rather than defaulting to a plausible number.
 */
internal object StageFlowReader {

    /** Role guessed from a file name. Returns null for names that carry no role. */
    fun roleOf(fileName: String): StageFlow.Role? {
        val stem = fileName.substringBeforeLast('.').lowercase()
        return when {
            stem.contains("_pre_short") -> StageFlow.Role.PreShort
            stem.contains("_pre") && !stem.contains("_pre_short") -> StageFlow.Role.Pre
            stem.contains("_bridge") -> StageFlow.Role.Bridge
            stem.contains("_outro") -> StageFlow.Role.Outro
            stem.contains("_fast") -> null   // a tempo variant, not a structural part
            stem.contains("_sfc") -> null     // an arrangement, not a structural part
            else -> StageFlow.Role.Main
        }
    }

    /**
     * Builds one part from bytes, or explains why it cannot be used.
     *
     * A part that cannot be decoded is rejected here rather than producing a silent gap in
     * the middle of a flow.
     */
    fun readPart(fileName: String, bytes: ByteArray): Result<StageFlow.Part> {
        val role = roleOf(fileName)
            ?: return Result.failure(IllegalArgumentException("'$fileName' is not a structural part"))

        val header = when (val read = BwavHeader.read(bytes)) {
            is BwavHeader.Result.Rejected -> return Result.failure(
                IllegalArgumentException("$fileName: ${read.reason}"),
            )
            is BwavHeader.Result.Ok -> read.info
        }

        // An outro's number comes from its file name, so a flow can offer several without
        // the caller having to track which file was which.
        val outroNumber = if (role == StageFlow.Role.Outro) {
            Regex("_outro(\\d+)", RegexOption.IGNORE_CASE)
                .find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } else {
            null
        }

        if (header.codecKind != 1) {
            return Result.failure(
                IllegalArgumentException(
                    "$fileName uses codec ${header.codecKind}, which this build cannot decode",
                ),
            )
        }

        return Result.success(
            StageFlow.Part(
                role = role,
                fileName = fileName,
                frames = header.frames.toLong(),
                sampleRate = header.sampleRate,
                channels = header.channels,
                loopStart = header.loopStart?.toLong(),
                loopEnd = header.loopEnd?.toLong(),
                outroNumber = outroNumber,
            ),
        )
    }

    /** Assembles a flow from named parts, or reports every part that failed. */
    fun assemble(
        name: String,
        parts: List<StageFlow.Part>,
        timing: StageFlow.Timing = StageFlow.DEFAULT_TIMING,
    ): Result<StageFlow> {
        if (parts.isEmpty()) return Result.failure(IllegalArgumentException("no parts supplied"))

        // Outros are the one role a flow may carry several of, so the duplicate check counts
        // distinct outro numbers rather than assuming at most one.
        val duplicated = parts
            .filter { it.role != StageFlow.Role.Outro }
            .groupBy { it.role }
            .filterValues { it.size > 1 }
            .keys
        if (duplicated.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException("more than one part for role(s) $duplicated"),
            )
        }

        val outroNumbers = parts.filter { it.role == StageFlow.Role.Outro }.map { it.outroNumber }
        if (outroNumbers.any { it == null }) {
            return Result.failure(
                IllegalArgumentException("every outro needs a number in its file name"),
            )
        }
        if (outroNumbers.toSet().size != outroNumbers.size) {
            return Result.failure(
                IllegalArgumentException("the same outro number appears twice: $outroNumbers"),
            )
        }

        val rates = parts.map { it.sampleRate }.distinct()
        if (rates.size > 1) {
            return Result.failure(
                IllegalArgumentException("parts disagree on sample rate: $rates"),
            )
        }

        val flow = StageFlow(name = name, parts = parts, timing = timing)
        if (flow.part(StageFlow.Role.Main) == null) {
            return Result.failure(IllegalArgumentException("a flow needs a Main part"))
        }
        return Result.success(flow)
    }
}
