package com.mylittleblover.mkwplayer.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The library must not advertise what the decoder cannot do.
 *
 * These rules come from measured facts: 253 BWAV entries use a codec this build
 * does not decode and they are nearly all JukeBox, while every course theme is
 * decodable. Getting this wrong means either greying out music that plays, or
 * offering rows that produce silence.
 */
class TrackEntryTest {

    private fun entry(
        name: String,
        category: TrackCategory = TrackCategory.COURSE,
        playable: Boolean = true,
    ) = TrackEntry(
        entryKey = "bars:0@$name",
        displayName = name,
        category = category,
        playable = playable,
        blockedReason = if (playable) null else Playability.REASON_UNSUPPORTED_CODEC,
    )

    @Test
    fun dspIsPlayable() {
        val result = Playability.evaluate(Playability.SUPPORTED_BWAV_CODEC_KIND)
        assertTrue(result.playable)
        assertNull(result.reason)
    }

    @Test
    fun kindTwoIsNotPlayable() {
        val result = Playability.evaluate(2)
        assertFalse(result.playable)
        assertEquals(Playability.REASON_UNSUPPORTED_CODEC, result.reason)
    }

    @Test
    fun unreadableSourceIsNotPlayable() {
        val result = Playability.evaluate(null)
        assertFalse(result.playable)
        assertTrue(result.reason != null)
    }

    @Test
    fun unsupportedReasonDoesNotBlameTheFile() {
        // A known limitation must not read as a corrupt file; users react
        // differently to "we cannot play this yet" than to "your file is broken".
        val reason = Playability.REASON_UNSUPPORTED_CODEC
        assertFalse(reason.contains("corrupt", ignoreCase = true))
        assertFalse(reason.contains("malformed", ignoreCase = true))
    }

    @Test
    fun everyIndexClassMapsToACategory() {
        val names = listOf(
            "COURSE", "JUKEBOX", "BATTLE", "SEQUENCE", "MISSION",
            "CUTSCENE", "DEMO", "SOUND_EFFECT", "BOOT", "UNCLASSIFIED",
        )
        for (name in names) {
            val category = TrackCategory.fromIndexName(name)
            assertTrue(category != TrackCategory.OTHER || name == "UNCLASSIFIED")
        }
    }

    @Test
    fun unknownIndexClassFallsBackInsteadOfThrowing() {
        assertEquals(TrackCategory.OTHER, TrackCategory.fromIndexName("SOMETHING_NEW"))
    }

    @Test
    fun classMatchingIgnoresCase() {
        assertEquals(TrackCategory.COURSE, TrackCategory.fromIndexName("course"))
        assertEquals(TrackCategory.JUKEBOX, TrackCategory.fromIndexName("Jukebox"))
    }

    @Test
    fun playableEntriesSortBeforeBlockedOnes() {
        val sorted = TrackOrdering.sort(
            listOf(
                entry("zzz", TrackCategory.JUKEBOX, playable = false),
                entry("aaa", TrackCategory.COURSE, playable = true),
            ),
        )
        assertEquals("aaa", sorted.first().displayName)
        assertEquals("zzz", sorted.last().displayName)
    }

    @Test
    fun blockedEntriesStillGroupUnderTheirCategory() {
        val grouped = TrackOrdering.group(
            listOf(
                entry("a", TrackCategory.COURSE, playable = true),
                entry("b", TrackCategory.JUKEBOX, playable = false),
            ),
        )
        assertEquals(2, grouped.size)
        assertEquals(TrackCategory.COURSE, grouped[0].first)
        assertEquals(TrackCategory.JUKEBOX, grouped[1].first)
    }

    @Test
    fun groupingLosesNoEntries() {
        val entries = listOf(
            entry("a", TrackCategory.COURSE),
            entry("b", TrackCategory.JUKEBOX, playable = false),
            entry("c", TrackCategory.BATTLE),
            entry("d", TrackCategory.COURSE),
        )
        val total = TrackOrdering.group(entries).sumOf { it.second.size }
        assertEquals(entries.size, total)
    }

    @Test
    fun groupingIsStableForEqualNames() {
        val entries = (1..5).map { entry("same$it", TrackCategory.COURSE) }
        val first = TrackOrdering.group(entries)
        val second = TrackOrdering.group(entries.reversed())
        assertEquals(
            first.flatMap { it.second }.map { it.displayName },
            second.flatMap { it.second }.map { it.displayName },
        )
    }

    @Test
    fun supportedCodecConstantIsOne() {
        // Kind 1 is DSP-ADPCM; kind 0 is raw PCM which no MKW file uses.
        assertEquals(1, Playability.SUPPORTED_BWAV_CODEC_KIND)
    }
}
