package com.mylittleblover.mkwplayer.ui.art

import com.mylittleblover.mkwplayer.R

/**
 * The map icon for each stage.
 *
 * The icons are the game's own diorama art, cut out and shipped at 176 px. Hands off the same rights
 * position as the cup emblems: a private research build, not a redistributable one, and [CupArt.NOTICE] is
 * the statement the UI must show wherever they appear.
 *
 * Two things this file refuses to do, both because they would turn a missing icon into a wrong one:
 *
 * A stage with no icon gets **null**, never a placeholder and never a different stage's art. [iconFor]
 * returns null rather than falling back, because a wrong picture is harder to notice than an empty space.
 *
 * The anchor code carries a `_00` suffix on 25 of the 30 stages and not on the other five. Rather than
 * normalising that away and hoping it stays consistent, [resolve] tries the code as given and then without
 * the suffix, which handles either spelling and any future file that switches.
 */
internal object StageArt {

    /**
     * The drawable for each anchor code, exactly as `map_anchors.json` spells it.
     *
     * Keyed by the anchor code and not by a normalised form: the anchors are the authoritative list, and a
     * lookup table keyed by something else would silently miss when the two spellings diverge.
     */
    private val byAnchorCode: Map<String, Int> = mapOf(
        "AirshipFortress_00" to R.drawable.stage_airshipfortress_00,
        "BigTree_00" to R.drawable.stage_bigtree_00,
        "Cave_00" to R.drawable.stage_cave_00,
        "Central_00" to R.drawable.stage_central_00,
        "ChocoMountain_00" to R.drawable.stage_chocomountain_00,
        "Cinema_00" to R.drawable.stage_cinema_00,
        "Coaster" to R.drawable.stage_coaster,
        "DesertHills_00" to R.drawable.stage_deserthills_00,
        "DinoBones_00" to R.drawable.stage_dinobones_00,
        "DinoDinoJungle_00" to R.drawable.stage_dinodinojungle_00,
        "DKJungleParkway_00" to R.drawable.stage_dkjungleparkway_00,
        "DKPass_00" to R.drawable.stage_dkpass_00,
        "DonkeyKong" to R.drawable.stage_donkeykong,
        "FirstCircuit" to R.drawable.stage_firstcircuit,
        "IceBuildings_00" to R.drawable.stage_icebuildings_00,
        "KoopaTroopaBeach_00" to R.drawable.stage_koopatroopabeach_00,
        "LagoonCity_00" to R.drawable.stage_lagooncity_00,
        "Lava_00" to R.drawable.stage_lava_00,
        "MooMooFarm_00" to R.drawable.stage_moomoofarm_00,
        "PeachBeach_00" to R.drawable.stage_peachbeach_00,
        "RaftingRiver_00" to R.drawable.stage_raftingriver_00,
        "Rainbow_00" to R.drawable.stage_rainbow_00,
        "SFCMarioCircuit_00" to R.drawable.stage_sfcmariocircuit_00,
        "ShyGuyBazaar_00" to R.drawable.stage_shyguybazaar_00,
        "SnowMountain_00" to R.drawable.stage_snowmountain_00,
        "StormClouds_00" to R.drawable.stage_stormclouds_00,
        "ToadFactory_00" to R.drawable.stage_toadfactory_00,
        "WarioShipyard_00" to R.drawable.stage_warioshipyard_00,
        "WarioStadium" to R.drawable.stage_wariostadium,
        "WestCity" to R.drawable.stage_westcity,
    )

    /** How many stages have art. Checked against the anchor list by the tests. */
    val size: Int get() = byAnchorCode.size

    /** Every anchor code this build has art for. */
    fun codes(): Set<String> = byAnchorCode.keys

    /**
     * The icon for an anchor code, or null when there is none.
     *
     * Tries the code as given first, so the exact spelling in the anchor list always wins, then without a
     * trailing `_00`. A caller that passes a stage name rather than an anchor code gets null rather than a
     * guess.
     */
    fun iconFor(anchorCode: String): Int? = resolve(anchorCode)

    /**
     * The same lookup, for a stage name that may or may not carry the export's suffix.
     *
     * The transition research, the flow files and the anchor list each spell stages slightly differently;
     * this is the one function that reconciles them, so the reconciliation is stated in one place.
     */
    fun resolve(stageCode: String): Int? {
        val code = stageCode.trim()
        if (code.isEmpty()) return null
        byAnchorCode[code]?.let { return it }
        // The anchors' `_00` suffix is an export artefact, not part of the stage's name.
        if (code.endsWith("_00")) {
            byAnchorCode[code.removeSuffix("_00")]?.let { return it }
        } else {
            byAnchorCode["${code}_00"]?.let { return it }
        }
        return null
    }
}
