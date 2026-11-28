package com.mylittleblover.mkwplayer.ui.components

import androidx.compose.ui.graphics.Color
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.ui.kit.MkwSliderSpecialStyle
import com.mylittleblover.mkwplayer.ui.theme.AppThemeAccent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Staff Credits medley progress-bar theme timeline and Nintendo Music Special offset alignment.
 *
 * Course colors were extracted directly from the Mario Kart World v1.8.0 in-game course diorama
 * icons (`MapStage_*.png`) and course emblem signs (`Sign_*.png`); the Rainbow Road flowing
 * spectrum was extracted from the official Rainbow Road course cover art
 * (`C_StageL_Rainbow_00.png` and `NM_MKWorld_Rainbow_Road.png`).
 *
 * Dual-course overlap sections (`Acorn Heights + Dandelion Depths`, `Whistlestop Summit +
 * Faraway Oasis`) render as two distinct color segments ([MkwSliderSpecialStyle.SplitDual]).
 * Both Main Menu sections always use the canonical Mario Red default ([AppThemeAccent.Default]).
 */
internal object StaffCreditsProgressTheme {

    enum class Variant {
        /** In-game `BGM_Demo_Staffroll` (`extra:staff_credits`, 322,878 ms, 3,020 ms leading silence). */
        GAME_RIP,

        /** Nintendo Music Special `NM_Special_119` (`extra:nm_special_119`, 320,620 ms). */
        NINTENDO_MUSIC,
    }

    data class Cue(
        val index: Int,
        val baseStartMs: Long,
        val label: String,
        val courseCodes: List<String>,
        val style: MkwSliderSpecialStyle,
    )

    const val GAME_RIP_TRACK_ID = "extra:staff_credits"
    const val GAME_RIP_SHA256 = "46fb33ea6bfe63bd82ba48530d45a9675860fb7cdff419b8d07f574460b03660"

    const val NM_SPECIAL_TRACK_ID = "extra:nm_special_119"
    const val NM_SPECIAL_SHA256 = "e04fce97f0bdba127741a362e0236be1f20ac0b2725a5ed07038ed7150f5b956"

    /**
     * Fallback millisecond offset (`nmPositionMs - gameRipPositionMs`) applied to Nintendo Music
     * Special `nm_special_119` before/if on-device pack audio calibration runs.
     *
     * `BGM_Demo_Staffroll` has exactly 3,020 ms of leading digital silence before its first note,
     * while Nintendo Music masters trim the lead-in to ~100 ms (~-2,920 ms shift; Mario Wiki also
     * notes Mario Bros. Circuit enters at 0:30 vs 0:33.05 in `BGM_Demo_Staffroll`). When the
     * installed `.mkwpack` audio file is readable on-device, [StaffCreditsOffsetCalibrator] refines
     * this automatically to +-10 ms via 10 ms RMS envelope cross-correlation.
     */
    const val DEFAULT_NM_SPECIAL_119_OFFSET_MS = -2_920L

    /** Canonical default Mario Red used for Main Menu and Main Menu 2 regardless of user theme. */
    val MAIN_MENU_RED: Color = AppThemeAccent.Default.primary // #D91E2A

    /** Course theme colors extracted from MKW in-game `MapStage_*.png` & `Sign_*.png` icons. */
    val COLOR_PEACH_STADIUM = Color(0xFFD83A76)        // Central_00: royal peach rose-pink
    val COLOR_MARIO_BROS_CIRCUIT = Color(0xFFD95A2B)   // FirstCircuit: canyon terracotta orange
    val COLOR_DK_SPACEPORT = Color(0xFF0A78B8)         // DonkeyKong: launchpad steel-cobalt blue
    val COLOR_SALTY_SALTY_SPEEDWAY = Color(0xFF1FBCE0) // LagoonCity_00: Venetian lagoon turquoise
    val COLOR_CROWN_CITY = Color(0xFFD19E22)           // WestCity: crown monument gold
    val COLOR_CHEEP_CHEEP_FALLS = Color(0xFF16B88D)    // RaftingRiver_00: jade waterfall teal
    val COLOR_BOWSERS_CASTLE = Color(0xFFC83518)       // Lava_00: molten magma crimson
    val COLOR_DRY_BONES_BURNOUT = Color(0xFFEB7F21)    // DinoBones_00: scorched flame orange
    val COLOR_BOO_CINEMA = Color(0xFF274775)           // Cinema_00: haunted midnight indigo
    val COLOR_STARVIEW_PEAK = Color(0xFF38C4D4)        // SnowMountain_00: glacial aurora cyan
    val COLOR_GREAT_BLOCK_RUINS = Color(0xFFD49B1A)    // StormClouds_00: ancient ? Block gold
    val COLOR_MARIO_CIRCUIT = Color(0xFF39A320)        // SFCMarioCircuit_00: classic warp-pipe/turf green
    val COLOR_ACORN_HEIGHTS = Color(0xFF258536)        // BigTree_00: canopy forest green
    val COLOR_DANDELION_DEPTHS = Color(0xFF2858B8)     // Cave_00: stalactite cavern indigo-blue (Sign_Cave #243EBA)
    val COLOR_WHISTLESTOP_SUMMIT = Color(0xFFDC4A1C)   // Coaster: sunlit canyon rail orange-red (Sign_Coaster #E9581D / MapStage_Coaster)
    val COLOR_FARAWAY_OASIS = Color(0xFFD8A22B)        // DKJungleParkway_00: sunlit savanna gold (MapStage_DKJungleParkway)

    /**
     * Seamless closed-loop rainbow spectrum extracted from Rainbow Road's official course cover
     * artwork (`C_StageL_Rainbow_00.png` and `NM_MKWorld_Rainbow_Road.png`).
     */
    val RAINBOW_ROAD_SPECTRUM: List<Color> = listOf(
        Color(0xFFE56B9A), // Rose Pink / Magenta
        Color(0xFFF29268), // Coral Peach
        Color(0xFFF2D25B), // Starlight Gold
        Color(0xFF4AD99B), // Aurora Mint
        Color(0xFF4EC4E6), // Crystal Aqua
        Color(0xFF5C86DF), // Periwinkle Sky
        Color(0xFFB96CE0), // Prism Violet
        Color(0xFFE56B9A), // Closed loop back to Rose Pink for zero-seam TileMode.Repeated
    )

    val CUES: List<Cue> = listOf(
        Cue(
            index = 0,
            baseStartMs = 0L,
            label = "Main Menu",
            courseCodes = emptyList(),
            style = MkwSliderSpecialStyle.Solid("sc_00_main_menu", MAIN_MENU_RED),
        ),
        Cue(
            index = 1,
            baseStartMs = 15_974L,
            label = "Peach Stadium",
            courseCodes = listOf("Central_00"),
            style = MkwSliderSpecialStyle.Solid("sc_01_central", COLOR_PEACH_STADIUM),
        ),
        Cue(
            index = 2,
            baseStartMs = 33_049L,
            label = "Mario Bros. Circuit",
            courseCodes = listOf("FirstCircuit"),
            style = MkwSliderSpecialStyle.Solid("sc_02_firstcircuit", COLOR_MARIO_BROS_CIRCUIT),
        ),
        Cue(
            index = 3,
            baseStartMs = 58_051L,
            label = "DK Spaceport",
            courseCodes = listOf("DonkeyKong"),
            style = MkwSliderSpecialStyle.Solid("sc_03_donkeykong", COLOR_DK_SPACEPORT),
        ),
        Cue(
            index = 4,
            baseStartMs = 76_553L,
            label = "Salty Salty Speedway",
            courseCodes = listOf("LagoonCity_00"),
            style = MkwSliderSpecialStyle.Solid("sc_04_lagooncity", COLOR_SALTY_SALTY_SPEEDWAY),
        ),
        Cue(
            index = 5,
            baseStartMs = 87_749L,
            label = "Crown City",
            courseCodes = listOf("WestCity"),
            style = MkwSliderSpecialStyle.Solid("sc_05_westcity", COLOR_CROWN_CITY),
        ),
        Cue(
            index = 6,
            baseStartMs = 120_871L,
            label = "Cheep Cheep Falls",
            courseCodes = listOf("RaftingRiver_00"),
            style = MkwSliderSpecialStyle.Solid("sc_06_raftingriver", COLOR_CHEEP_CHEEP_FALLS),
        ),
        Cue(
            index = 7,
            baseStartMs = 133_268L,
            label = "Bowser's Castle",
            courseCodes = listOf("Lava_00"),
            style = MkwSliderSpecialStyle.Solid("sc_07_lava", COLOR_BOWSERS_CASTLE),
        ),
        Cue(
            index = 8,
            baseStartMs = 144_276L,
            label = "Dry Bones Burnout",
            courseCodes = listOf("DinoBones_00"),
            style = MkwSliderSpecialStyle.Solid("sc_08_dinobones", COLOR_DRY_BONES_BURNOUT),
        ),
        Cue(
            index = 9,
            baseStartMs = 152_929L,
            label = "Boo Cinema",
            courseCodes = listOf("Cinema_00"),
            style = MkwSliderSpecialStyle.Solid("sc_09_cinema", COLOR_BOO_CINEMA),
        ),
        Cue(
            index = 10,
            baseStartMs = 159_926L,
            label = "Starview Peak",
            courseCodes = listOf("SnowMountain_00"),
            style = MkwSliderSpecialStyle.Solid("sc_10_snowmountain", COLOR_STARVIEW_PEAK),
        ),
        Cue(
            index = 11,
            baseStartMs = 176_437L,
            label = "Great ? Block Ruins",
            courseCodes = listOf("StormClouds_00"),
            style = MkwSliderSpecialStyle.Solid("sc_11_stormclouds", COLOR_GREAT_BLOCK_RUINS),
        ),
        Cue(
            index = 12,
            baseStartMs = 190_862L,
            label = "Mario Circuit",
            courseCodes = listOf("SFCMarioCircuit_00"),
            style = MkwSliderSpecialStyle.Solid("sc_12_sfcmariocircuit", COLOR_MARIO_CIRCUIT),
        ),
        Cue(
            index = 13,
            baseStartMs = 198_243L,
            label = "Main Menu 2",
            courseCodes = emptyList(),
            style = MkwSliderSpecialStyle.Solid("sc_13_main_menu_2", MAIN_MENU_RED),
        ),
        Cue(
            index = 14,
            baseStartMs = 233_275L,
            label = "Acorn Heights",
            courseCodes = listOf("BigTree_00"),
            style = MkwSliderSpecialStyle.Solid("sc_14_bigtree", COLOR_ACORN_HEIGHTS),
        ),
        Cue(
            index = 15,
            baseStartMs = 241_751L,
            label = "Acorn Heights + Dandelion Depths",
            courseCodes = listOf("BigTree_00", "Cave_00"),
            style = MkwSliderSpecialStyle.SplitDual(
                key = "sc_15_bigtree_cave",
                leftColor = COLOR_ACORN_HEIGHTS,
                rightColor = COLOR_DANDELION_DEPTHS,
                splitFraction = 0.5f,
            ),
        ),
        Cue(
            index = 16,
            baseStartMs = 250_434L,
            label = "Whistlestop Summit + Faraway Oasis",
            courseCodes = listOf("Coaster", "DKJungleParkway_00"),
            style = MkwSliderSpecialStyle.SplitDual(
                key = "sc_16_coaster_oasis",
                leftColor = COLOR_WHISTLESTOP_SUMMIT,
                rightColor = COLOR_FARAWAY_OASIS,
                splitFraction = 0.5f,
            ),
        ),
        Cue(
            index = 17,
            baseStartMs = 275_205L,
            label = "Rainbow Road",
            courseCodes = listOf("Rainbow_00"),
            style = MkwSliderSpecialStyle.RainbowFlow(
                key = "sc_17_rainbow",
                colors = RAINBOW_ROAD_SPECTRUM,
                periodMillis = 3800,
            ),
        ),
    )

    /**
     * Recognizes either Staff Credits recording across all playback entry paths (Other Music,
     * Nintendo Music Special, Favorites, Playlists, Search, Raw Catalog, Continue Listening).
     */
    fun detectVariant(
        canonicalTrackId: String? = null,
        selectedKey: String? = null,
        assetSha256: String? = null,
    ): Variant? {
        val sha = assetSha256?.trim()?.lowercase()
        if (sha == NM_SPECIAL_SHA256) return Variant.NINTENDO_MUSIC
        if (sha == GAME_RIP_SHA256) return Variant.GAME_RIP

        val candidates = listOfNotNull(canonicalTrackId, selectedKey)
        for (raw in candidates) {
            val s = raw.trim().lowercase()
            if (s.isEmpty()) continue
            if (s == NM_SPECIAL_TRACK_ID ||
                s == "nm_special_119" ||
                s.endsWith(":nm_special_119") ||
                ":nm_special_119:" in s ||
                "nm_special_119" in s
            ) {
                return Variant.NINTENDO_MUSIC
            }
            if (s == GAME_RIP_TRACK_ID ||
                s == "staff_credits" ||
                s.endsWith(":staff_credits") ||
                ":staff_credits:" in s ||
                "bgm_demo_staffroll" in s
            ) {
                return Variant.GAME_RIP
            }
        }
        return null
    }

    /** Returns the effective start timestamp (ms) for [cue] under [variant] and [nmOffsetMs]. */
    fun effectiveStartMs(
        cue: Cue,
        variant: Variant,
        nmOffsetMs: Long = DEFAULT_NM_SPECIAL_119_OFFSET_MS,
    ): Long {
        if (cue.index == 0) return 0L
        val shift = if (variant == Variant.NINTENDO_MUSIC) nmOffsetMs else 0L
        return (cue.baseStartMs + shift).coerceAtLeast(0L)
    }

    /** Resolves the active Staff Credits cue for [positionMs]. */
    fun resolveCue(
        variant: Variant,
        positionMs: Long,
        nmOffsetMs: Long = DEFAULT_NM_SPECIAL_119_OFFSET_MS,
    ): Cue {
        val pos = positionMs.coerceAtLeast(0L)
        var active = CUES.first()
        for (i in 1 until CUES.size) {
            val cue = CUES[i]
            if (pos >= effectiveStartMs(cue, variant, nmOffsetMs)) {
                active = cue
            } else {
                break
            }
        }
        return active
    }

    /** Resolves the [MkwSliderSpecialStyle] for [positionMs] on the given Staff Credits [variant]. */
    fun resolveStyle(
        variant: Variant,
        positionMs: Long,
        nmOffsetMs: Long = DEFAULT_NM_SPECIAL_119_OFFSET_MS,
    ): MkwSliderSpecialStyle = resolveCue(variant, positionMs, nmOffsetMs).style
}

/**
 * Sample-accurate runtime offset calibrator for Nintendo Music Special `NM_Special_119`.
 *
 * Embeds a compact 800-bin (10 ms hop, covering `3.0 s .. 11.0 s`) RMS amplitude envelope
 * fingerprint derived from `BGM_Demo_Staffroll`. When `NM_Special_119` is present in the user's
 * mounted `.mkwpack`, decoding just its opening 16 seconds offline via [AudioEngine] and running
 * normalized zero-mean cross-correlation recovers the exact timing shift (`nmMs - gameRipMs`)
 * within +-10 ms regardless of lead-in mastering trim.
 */
internal object StaffCreditsOffsetCalibrator {

    data class CalibrationResult(
        val offsetMs: Long,
        val correlation: Float,
    )

    private const val HOP_MS = 10
    private const val TEMPLATE_START_BIN = 300 // 3,000 ms in BGM_Demo_Staffroll
    private const val MAX_LAG_BINS = 450       // +-4,500 ms search window
    private const val MIN_CORRELATION = 0.65f
    private const val PROBE_SECONDS = 16

    /**
     * 800-byte quantized (0..255) 10 ms RMS envelope of `BGM_Demo_Staffroll` from 3.00 s to 11.00 s.
     */
    private const val STAFFROLL_ENV_10MS_B64 =
        "AAAqR29PWVNRVU1MS0RCRkcXCwsNM2tVSkxXWFJZTUw6Pjo8QUFDQUFAQx4MEAoXZ0hSUkErIBsTDAoHBwYHBAYEBQY" +
            "DBAUFAgQKDhASGSQ3Rl5kXlleVkdMUlNXVFBMQCYLCQYGCAcGBgcICAYFBBJAWXBxdnB0YWJYSkpFPjk8NiUTKE" +
            "lXWFtcWFZNKxALCgkKCgYGBQYFBgcHBgoseoR4a2NWST9DPjk3MjE2QE9fZGpqYVNJS1BMRDkyKiUsMz9dW0pfW" +
            "UdQSU9UTE1UU09LT0U8KBgUFypEWllYNSpRY2t8ZFVlXFlTT1BUWSQZJFhaX3x+gHpsX1dNRzpERklLSEVHRUtN" +
            "VjkUERAge5mJf25aRi0VDg4MCwwKCAcICQkIBwkHCREbLU5YVWZXVFpcW1pWWGJfaWpYUFZQSTojEhsqKRkPDQs" +
            "KCAsZM1heVFlnV1ZhXj0/RmBnY2tgYnSfrqeYfmdNQTM/PD0+RVJSOikoOEE9M1pYV0ZGKysfMEFtgIuHfnZyaF" +
            "hLPTAnKycmJjFUXEM4P09LSU9PR0U+QEZPUVJQUFFJRjAuLjQ6PUFCPj1HbbvCm2dbP1RiV3BfaWFcYllfWlBbS" +
            "VVEIRwqcHaPpJyZiIKCgHZlVFNjU1ZXU1FLPzk0LRcYGkGYmJuKe1otFg8QEBEQDA4MCAoLCgkICAUJCQcKDx8q" +
            "OFBhYmloa2xlclp0f3JbUUFGOzYrIiQfJSQnKCgeEyhRWXOJh8OliHmghn6CgXtzX11gWlxGk6KouLOknY9iMCE" +
            "ZFhQREA4MDg0MDixUX3KysY+qiXiDU0taU1tKREFEV15NVnB9iWhTaXJeSj87NDhCYWx/ioyVmpGIbIaQc2BmV1" +
            "RKN0E4QlFvg3N5dl1Pi4t6h3l0ZmZYV1lWVEkpKyk/ep2wpJuXlIqCfmZRVlZZWVdPT1hYW1YoKCgxVaqjm6CVh" +
            "2c1GhYUFhgSDg0PDQsLDg8MCgsKDA8OEhALGR4uGEFjh7HKxuHPxuTvxqionYuNgmh1PTpHR2xHQlKz+OPB2v/w" +
            "s6/Ex7yXb4SDcXyIvao="

    private val templateNorm: FloatArray by lazy {
        val raw = decodeBase64(STAFFROLL_ENV_10MS_B64)
        val floats = FloatArray(raw.size) { (raw[it].toInt() and 0xFF).toFloat() }
        normalizeZeroMeanUnitVar(floats)
    }

    private val fileCache = ConcurrentHashMap<String, Long>()

    /** Exposes the normalized reference template copy for deterministic unit testing. */
    internal fun referenceTemplateForTest(): FloatArray = templateNorm.copyOf()

    /**
     * Estimates the offset (`targetMs - staffrollMs`) from a 10 ms RMS amplitude envelope covering
     * at least the opening 12 seconds of a candidate Staff Credits recording.
     */
    fun estimateOffsetMsFromEnvelope(targetEnv10ms: FloatArray): CalibrationResult? {
        val tpl = templateNorm
        val tplLen = tpl.size
        if (targetEnv10ms.size < tplLen) return null

        var bestLagBins = 0
        var bestCorr = Float.NEGATIVE_INFINITY

        for (lag in -MAX_LAG_BINS..MAX_LAG_BINS) {
            val start = TEMPLATE_START_BIN + lag
            if (start < 0 || start + tplLen > targetEnv10ms.size) continue
            val corr = normalizedDot(tpl, targetEnv10ms, start, tplLen)
            if (corr > bestCorr) {
                bestCorr = corr
                bestLagBins = lag
            }
        }

        if (!bestCorr.isFinite() || bestCorr < MIN_CORRELATION) return null
        return CalibrationResult(
            offsetMs = bestLagBins.toLong() * HOP_MS.toLong(),
            correlation = bestCorr,
        )
    }

    /**
     * Synchronously decodes the opening [PROBE_SECONDS] seconds of [nmFile] via a short-lived
     * offline [AudioEngine] on the caller's thread and caches the calibrated millisecond offset.
     *
     * Must be invoked on an IO/background thread without suspending between engine creation and
     * teardown so the native engine's single-thread affinity contract is preserved.
     */
    fun calibrateFromPackFile(
        nmFile: File,
        sha256: String = StaffCreditsProgressTheme.NM_SPECIAL_SHA256,
    ): Long {
        if (!nmFile.isFile) return StaffCreditsProgressTheme.DEFAULT_NM_SPECIAL_119_OFFSET_MS
        val cacheKey = "${nmFile.absolutePath}:${nmFile.length()}:${nmFile.lastModified()}"
        fileCache[cacheKey]?.let { return it }

        val computed = runCatching {
            val env = decodeOpeningEnvelope10ms(nmFile, sha256) ?: return@runCatching null
            estimateOffsetMsFromEnvelope(env)?.offsetMs
        }.getOrNull() ?: StaffCreditsProgressTheme.DEFAULT_NM_SPECIAL_119_OFFSET_MS

        fileCache[cacheKey] = computed
        return computed
    }

    private fun decodeOpeningEnvelope10ms(file: File, sha256: String): FloatArray? {
        val engine = AudioEngine.create() ?: return null
        return try {
            val loaded = engine.loadLinearFile(
                file = file,
                assetId = sha256,
                loop = false,
                streamed = true,
            )
            if (loaded != AudioEngine.STATUS_OK) return null
            engine.setOutputGain(0.0, 0.0)
            if (engine.play(AudioEngine.PLAY_LINEAR) != AudioEngine.STATUS_OK) return null

            val sr = engine.sampleRate.takeIf { it in 8_000..192_000 } ?: 48_000
            val hopFrames = ((sr.toLong() * HOP_MS) / 1000L).toInt().coerceAtLeast(1)
            val totalBins = (PROBE_SECONDS * 1000) / HOP_MS
            val chunk = FloatArray(hopFrames * 2)
            val env = FloatArray(totalBins)

            for (bin in 0 until totalBins) {
                val status = engine.renderInto(chunk, hopFrames)
                if (status != AudioEngine.STATUS_OK) {
                    return if (bin >= templateNorm.size) env.copyOf(bin) else null
                }
                var sumSq = 0.0
                for (i in 0 until hopFrames) {
                    val mono = (chunk[i * 2] + chunk[i * 2 + 1]) * 0.5f
                    sumSq += (mono * mono).toDouble()
                }
                env[bin] = sqrt(sumSq / hopFrames).toFloat()
            }
            env
        } finally {
            engine.close()
        }
    }

    private fun normalizeZeroMeanUnitVar(values: FloatArray): FloatArray {
        if (values.isEmpty()) return FloatArray(0)
        var sum = 0.0
        for (v in values) sum += v
        val mean = (sum / values.size).toFloat()
        var varSum = 0.0
        for (v in values) {
            val d = v - mean
            varSum += (d * d).toDouble()
        }
        val std = sqrt(varSum / values.size).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(values.size) { (values[it] - mean) / std }
    }

    private fun normalizedDot(
        templateNorm: FloatArray,
        target: FloatArray,
        offset: Int,
        length: Int,
    ): Float {
        var sum = 0.0
        for (i in 0 until length) sum += target[offset + i]
        val mean = (sum / length).toFloat()

        var varSum = 0.0
        var dot = 0.0
        for (i in 0 until length) {
            val d = target[offset + i] - mean
            varSum += (d * d).toDouble()
            dot += (templateNorm[i] * d).toDouble()
        }
        val std = sqrt(varSum / length)
        if (std < 1e-6 || !std.isFinite()) return Float.NEGATIVE_INFINITY
        return (dot / (length * std)).toFloat().coerceIn(-1f, 1f)
    }

    /** Pure-Kotlin RFC 4648 Base64 decoder (API-level and JVM-unit-test independent). */
    private fun decodeBase64(input: String): ByteArray {
        val out = ArrayList<Byte>((input.length * 3) / 4)
        var acc = 0
        var bits = 0
        for (ch in input) {
            val v = when (ch) {
                in 'A'..'Z' -> ch.code - 'A'.code
                in 'a'..'z' -> ch.code - 'a'.code + 26
                in '0'..'9' -> ch.code - '0'.code + 52
                '+' -> 62
                '/' -> 63
                '=' -> break
                else -> continue
            }
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((acc ushr bits) and 0xFF).toByte())
            }
        }
        return ByteArray(out.size) { out[it] }
    }
}
