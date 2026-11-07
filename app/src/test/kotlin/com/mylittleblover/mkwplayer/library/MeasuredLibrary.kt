package com.mylittleblover.mkwplayer.library

/**
 * The measured codec distribution of the OST index, as a test fixture.
 *
 * These numbers are not invented: they were produced by reading the codec kind
 * out of the real BWAV header for every indexed entry. They exist here so the
 * playability rule can be tested against the actual shape of the library instead
 * of a convenient one.
 *
 * If a future index changes these counts, a test will notice and the expectation
 * gets updated deliberately rather than silently.
 */
data class CategoryCodecMix(val course: Int, val kindOne: Int, val kindTwo: Int) {
    val total: Int get() = kindOne + kindTwo
    val playableShare: Double get() = if (total == 0) 0.0 else kindOne.toDouble() / total
}

object MeasuredLibrary {

    /** Counts per index class, measured on 2026-11-08. */
    val mix: Map<TrackCategory, CategoryCodecMix> = mapOf(
        TrackCategory.COURSE to CategoryCodecMix(0, kindOne = 338, kindTwo = 0),
        TrackCategory.JUKEBOX to CategoryCodecMix(0, kindOne = 0, kindTwo = 227),
        TrackCategory.CUTSCENE to CategoryCodecMix(0, kindOne = 0, kindTwo = 22),
        TrackCategory.BATTLE to CategoryCodecMix(0, kindOne = 16, kindTwo = 0),
        TrackCategory.SEQUENCE to CategoryCodecMix(0, kindOne = 33, kindTwo = 1),
        TrackCategory.DEMO to CategoryCodecMix(0, kindOne = 1, kindTwo = 3),
        TrackCategory.MISSION to CategoryCodecMix(0, kindOne = 5, kindTwo = 0),
        TrackCategory.SOUND_EFFECT to CategoryCodecMix(0, kindOne = 3, kindTwo = 0),
        TrackCategory.BOOT to CategoryCodecMix(0, kindOne = 1, kindTwo = 0),
    )

    val totalEntries: Int get() = mix.values.sumOf { it.total }
    val totalKindOne: Int get() = mix.values.sumOf { it.kindOne }
    val totalKindTwo: Int get() = mix.values.sumOf { it.kindTwo }

    /** Classes that contain both playable and blocked entries. */
    val mixedCategories: List<TrackCategory>
        get() = mix.filter { (_, m) -> m.kindOne > 0 && m.kindTwo > 0 }.keys.toList()

    /** Builds one entry per mixed category, playable side and blocked side. */
    fun mixedCategoryProbes(): List<TrackEntry> = mixedCategories.map { category ->
        val m = mix.getValue(category)
        TrackEntry(
            entryKey = "probe:${category.name}",
            displayName = "${category.name} probe",
            category = category,
            playable = m.kindOne > 0,
        )
    }
}
