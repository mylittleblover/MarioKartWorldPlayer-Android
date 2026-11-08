package com.mylittleblover.mkwplayer.library

/**
 * How one stage's Bridge hands over to its Full section.
 *
 * The transition research found two distinct playback modes, and treating them the same is the specific
 * mistake this type exists to prevent. A SEQUENCED bridge plays through and hands over at a sample
 * position that is encoded in the archive; a LAYERED bridge has no switching point at all and is mixed
 * *on top of* the Full section. Running a layered bridge as if it were sequenced would insert a gap
 * where the game has none, and running a sequenced bridge as a loop would lose the handover entirely.
 *
 * The distinction is not a guess: it is established by three independent signals that agree perfectly
 * across the corpus — an empty exit record, an empty label anchor and an empty final node all at once
 * (4/4 layered, 0/16 sequenced), a duration gap of 1.59x with no overlap, and the end-of-bridge level
 * (~-35.5 dB layered against ~-55.8 dB sequenced, because a sequenced bridge fades to hand over and a
 * layered one does not need to).
 */
internal data class BridgeTransition(
    /** Stage code, e.g. `WestCity`. */
    val stage: String,
    val mode: Mode,
    val bpm: Double,
    /** Length of one beat in samples at 48 kHz, so a caller can convert positions to musical time. */
    val beatSamples: Double,
    val bridgeSamples: Long,
    /**
     * The switch point, in bridge-local samples. Null for [Mode.LAYERED], where the research found no
     * switching point rather than a missing one.
     */
    val switchSample: Long?,
    /**
     * Where the Full section starts playing relative to the switch point, in bridge-local samples.
     *
     * Not always equal to [switchSample]: in branch A the Full section has to be started early so that
     * it reaches its own grid origin exactly when the bridge reaches the switch, which produces a short
     * deliberate overlap.
     */
    val fullEntersAt: Long?,
    /** Where in the Full track playback begins, in its own samples. */
    val fullSourceOffset: Long,
    /** How many samples the two sections deliberately overlap. */
    val overlapSamples: Long,
    /** Where the switch point came from, because the research distinguishes encoded from inferred. */
    val switchSource: SwitchSource,
) {
    init {
        require(stage.isNotBlank()) { "stage must not be blank" }
        require(bpm > 0) { "bpm must be positive, got $bpm" }
        require(beatSamples > 0) { "beat samples must be positive, got $beatSamples" }
        require(bridgeSamples > 0) { "bridge length must be positive, got $bridgeSamples" }
        require(fullSourceOffset >= 0) { "a source offset cannot be negative, got $fullSourceOffset" }
        require(overlapSamples >= 0) { "an overlap cannot be negative, got $overlapSamples" }
        require(switchSample == null || switchSample in 0..bridgeSamples) {
            "switch $switchSample is outside the bridge 0..$bridgeSamples"
        }
        require(fullEntersAt == null || fullEntersAt >= 0) {
            "the Full entry cannot precede the bridge, got $fullEntersAt"
        }
        // A layered bridge has no switch point by design; claiming one would turn a mix into a splice.
        require(mode != Mode.LAYERED || switchSample == null) {
            "a layered bridge has no switch point, got $switchSample"
        }
        require(mode != Mode.SEQUENCED || switchSample != null) {
            "a sequenced bridge must state where it hands over"
        }
    }

    /** See the type comment. */
    enum class Mode {
        /** Plays through and hands over at [switchSample]. */
        SEQUENCED,

        /**
         * Mixed on top of the Full section, with no handover.
         *
         * The research could not establish the exact entry and exit instants because the metadata that
         * would carry them is empty by design. Anything this app does with a layered bridge is therefore
         * this app's arrangement, and must be labelled as such.
         */
        LAYERED,
    }

    /** Where the switch position was read from. */
    enum class SwitchSource {
        /** An encoded exit record. The strongest form. */
        EXIT_RECORD,

        /** An encoded final grid node. */
        FINAL_NODE,

        /**
         * A single label anchor with no final node.
         *
         * Inferred rather than encoded: the position is derived from the musical grid rather than read
         * out. Correct to a sample in the three cases checked against the game, but still an inference.
         */
        LABEL_ANCHOR_INFERRED,

        /** No switch point exists. Only for [Mode.LAYERED]. */
        NONE,
    }

    /** True when the switch position was read out rather than derived. */
    val isSwitchEncoded: Boolean
        get() = switchSource == SwitchSource.EXIT_RECORD || switchSource == SwitchSource.FINAL_NODE

    /** The switch point as musical time, for display; null when there is none. */
    fun switchBeats(): Double? = switchSample?.let { it / beatSamples }

    /** The overlap in seconds, so a UI can say "117 ms" rather than a sample count. */
    fun overlapSeconds(sampleRate: Int = 48_000): Double {
        require(sampleRate > 0) { "sample rate must be positive, got $sampleRate" }
        return overlapSamples.toDouble() / sampleRate
    }

    /**
     * A one-line description that never overstates what is known.
     *
     * A layered bridge says it is layered and that the timing is this app's choice; an inferred switch
     * says it was inferred. Presenting either as the game's encoded behaviour would be the same class
     * of error as showing a marker position as a scheduled event.
     */
    fun describe(): String = when (mode) {
        Mode.LAYERED ->
            "Layered bridge, no switch point in the data. Playing it as an overlay is this app's " +
                "arrangement, not the game's."
        Mode.SEQUENCED -> {
            val where = when (switchSource) {
                SwitchSource.EXIT_RECORD -> "encoded exit record"
                SwitchSource.FINAL_NODE -> "encoded grid node"
                else -> "inferred from the musical grid"
            }
            val beats = switchBeats()?.let { " (%.3f beats)".format(it) } ?: ""
            "Sequenced bridge, switches at sample $switchSample$beats, $where" +
                if (overlapSamples > 0) ", %.0f ms overlap".format(overlapSeconds() * 1000) else ""
        }
    }
}

/**
 * The per-stage bridge table, read from the transition research.
 *
 * The values are transcribed from `bgm_transition_research/Splice_Sheet_All_Bridges.csv`, which measured
 * them from the archive. Three of them were checked against the running game (WestCity to within one
 * sample, IceBuildings exactly, Lava by ear), which is why this table is treated as measured rather than
 * derived.
 *
 * Twenty stages are listed because twenty have a Bridge asset. The rest have no bridge and therefore no
 * row — an absent row means "no bridge exists", never "not yet researched".
 */
internal object BridgeCatalog {

    /** Where the table came from, carried so a UI can cite it. */
    const val SOURCE = "bgm_transition_research/Splice_Sheet_All_Bridges.csv"

    /** Stages checked against the running game rather than only against the archive. */
    val spotChecked: Set<String> = setOf("WestCity", "IceBuildings", "Lava")

    private val entries: List<BridgeTransition> = listOf(
        // mode, bpm, beat, bridge, switch M, full enters, source offset, overlap, provenance
        BridgeTransition(
            "AirshipFortress", BridgeTransition.Mode.SEQUENCED, 150.0, 19_200.0, 102_700,
            76_800, 76_800, 0, 0, BridgeTransition.SwitchSource.LABEL_ANCHOR_INFERRED,
        ),
        BridgeTransition(
            "Cave", BridgeTransition.Mode.SEQUENCED, 143.9999, 20_000.0, 153_243,
            40_000, 40_000, 0, 0, BridgeTransition.SwitchSource.FINAL_NODE,
        ),
        BridgeTransition(
            "Central", BridgeTransition.Mode.SEQUENCED, 160.0, 18_000.0, 306_000,
            90_000, 88_875, 0, 1_125, BridgeTransition.SwitchSource.FINAL_NODE,
        ),
        BridgeTransition(
            "ChocoMountain", BridgeTransition.Mode.SEQUENCED, 150.0, 19_200.0, 76_800,
            76_800, 76_800, 38_400, 0, BridgeTransition.SwitchSource.LABEL_ANCHOR_INFERRED,
        ),
        BridgeTransition(
            "Coaster", BridgeTransition.Mode.SEQUENCED, 140.0001, 20_571.4, 88_320,
            20_571, 20_571, 0, 0, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
        BridgeTransition(
            "DKJungleParkway", BridgeTransition.Mode.SEQUENCED, 156.0002, 18_461.5, 192_357,
            80_769, 75_500, 0, 5_269, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
        BridgeTransition(
            "DKPass", BridgeTransition.Mode.SEQUENCED, 123.0, 23_414.6, 146_850,
            46_829, 40_976, 0, 5_853, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
        BridgeTransition(
            "DesertHills", BridgeTransition.Mode.LAYERED, 130.0001, 22_153.8, 44_308,
            null, null, 0, 0, BridgeTransition.SwitchSource.NONE,
        ),
        BridgeTransition(
            "DinoBones", BridgeTransition.Mode.SEQUENCED, 130.0001, 22_153.8, 70_224,
            66_461, 60_923, 0, 5_538, BridgeTransition.SwitchSource.LABEL_ANCHOR_INFERRED,
        ),
        BridgeTransition(
            "DinoDinoJungle", BridgeTransition.Mode.SEQUENCED, 137.0001, 21_021.9, 168_175,
            168_175, 168_175, 420_438, 0, BridgeTransition.SwitchSource.FINAL_NODE,
        ),
        BridgeTransition(
            "FirstCircuit", BridgeTransition.Mode.LAYERED, 154.0002, 18_701.3, 40_219,
            null, null, 0, 0, BridgeTransition.SwitchSource.NONE,
        ),
        BridgeTransition(
            "IceBuildings", BridgeTransition.Mode.SEQUENCED, 168.0004, 17_142.8, 230_880,
            68_571, 68_571, 34_286, 0, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
        BridgeTransition(
            "KoopaTroopaBeach", BridgeTransition.Mode.SEQUENCED, 124.0003, 23_225.8, 196_600,
            23_226, 23_226, 0, 0, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
        BridgeTransition(
            "Lava", BridgeTransition.Mode.SEQUENCED, 180.0002, 16_000.0, 237_565,
            96_000, 94_001, 0, 1_999, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
        BridgeTransition(
            "MooMooFarm", BridgeTransition.Mode.LAYERED, 128.0, 22_500.0, 43_659,
            null, null, 0, 0, BridgeTransition.SwitchSource.NONE,
        ),
        BridgeTransition(
            "RaftingRiver", BridgeTransition.Mode.SEQUENCED, 152.0003, 18_947.3, 189_475,
            37_895, 37_895, 0, 0, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
        BridgeTransition(
            "SFCMarioCircuit", BridgeTransition.Mode.LAYERED, 130.0001, 22_153.8, 40_219,
            null, null, 0, 0, BridgeTransition.SwitchSource.NONE,
        ),
        BridgeTransition(
            "ToadFactory", BridgeTransition.Mode.SEQUENCED, 220.0002, 13_090.9, 471_272,
            471_272, 471_272, 110_454, 0, BridgeTransition.SwitchSource.FINAL_NODE,
        ),
        BridgeTransition(
            "WarioShipyard", BridgeTransition.Mode.SEQUENCED, 152.0003, 18_947.3, 209_132,
            75_789, 75_789, 0, 0, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
        BridgeTransition(
            "WestCity", BridgeTransition.Mode.SEQUENCED, 128.0, 22_500.0, 79_266,
            49_922, 44_297, 0, 5_625, BridgeTransition.SwitchSource.EXIT_RECORD,
        ),
    )

    /** Every researched bridge. */
    fun all(): List<BridgeTransition> = entries

    /** The transition for a stage, or null when the stage has no bridge. */
    fun forStage(stage: String): BridgeTransition? = entries.firstOrNull { it.stage == stage }

    fun sequenced(): List<BridgeTransition> = entries.filter { it.mode == BridgeTransition.Mode.SEQUENCED }

    fun layered(): List<BridgeTransition> = entries.filter { it.mode == BridgeTransition.Mode.LAYERED }

    /** Stages whose switch position was inferred rather than read out, for a UI to flag. */
    fun inferred(): List<BridgeTransition> =
        entries.filter { it.switchSource == BridgeTransition.SwitchSource.LABEL_ANCHOR_INFERRED }
}
