package com.mylittleblover.mkwplayer.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Identity for user files is the digest, and the track layer groups those files into one thing a
 * person would call a song.
 *
 * Both rules exist because the obvious alternative -- identity by name -- breaks: two stages can
 * ship colliding names, and a stage's five parts are not five songs.
 */
class AudioAssetIdTest {

    private fun digest(seed: Int): ByteArray = ByteArray(32) { (seed + it).toByte() }

    private fun hex(seed: Int): String = digest(seed).joinToString("") { "%02x".format(it) }

    // ---------------------------------------------------------------- asset ids

    @Test
    fun aDigestBecomesAnId() {
        val id = AudioAssetId.of(digest(1))
        assertEquals(hex(1), id.sha256)
        assertEquals(64, id.sha256.length)
    }

    @Test
    fun theSameBytesProduceTheSameId() {
        // This is the whole point: identity survives a rename, because it never uses the name.
        assertEquals(AudioAssetId.of(digest(7)), AudioAssetId.of(digest(7)))
    }

    @Test
    fun differentBytesProduceDifferentIds() {
        assertTrue(AudioAssetId.of(digest(1)) != AudioAssetId.of(digest(2)))
    }

    @Test
    fun aWrongLengthDigestIsRefused() {
        assertFailsWith<IllegalArgumentException> { AudioAssetId.of(ByteArray(16)) }
    }

    @Test
    fun aNonHexStringIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            AudioAssetId("z".repeat(64))
        }
    }

    @Test
    fun anUppercaseDigestIsRefused() {
        // Normalising silently would make two ids for one file; refusing keeps one form.
        assertFailsWith<IllegalArgumentException> {
            AudioAssetId("A".repeat(64))
        }
    }

    @Test
    fun parseAcceptsAGoodDigestAndRejectsJunk() {
        assertNotNull(AudioAssetId.parse(hex(3)))
        assertNull(AudioAssetId.parse("not-a-digest"))
        assertNull(AudioAssetId.parse(""))
    }

    @Test
    fun theShortFormIsUsablyDistinct() {
        val a = AudioAssetId.of(digest(1)).short()
        val b = AudioAssetId.of(digest(2)).short()
        assertEquals(12, a.length)
        assertTrue(a != b)
    }

    // ---------------------------------------------------------------- classification

    @Test
    fun courseThemesAreClassified() {
        assertEquals(ResourceKind.COURSE, TrackClassifier.classify("BGM_Stage_Coaster.bwav"))
        assertEquals(ResourceKind.COURSE, TrackClassifier.classify("BGM_Stage_PeachBeach_Pre.bwav"))
    }

    @Test
    fun jukeBoxIsClassified() {
        assertEquals(ResourceKind.JUKEBOX, TrackClassifier.classify("BGM_JukeBox_M001B_SMW_Ground_RnR_Outro.bwav"))
    }

    @Test
    fun sequencesAndDemosAreClassified() {
        assertEquals(ResourceKind.SEQUENCE, TrackClassifier.classify("BGM_Seq_Title.bwav"))
        assertEquals(ResourceKind.DEMO, TrackClassifier.classify("BGM_Demo_Staffroll.bwav"))
    }

    @Test
    fun anUnknownNameIsUnclassifiedRatherThanGuessed() {
        // A wrong kind puts a track in the wrong list; unclassified is a visible, correctable state.
        assertEquals(ResourceKind.UNCLASSIFIED, TrackClassifier.classify("some_random_file.bwav"))
    }

    @Test
    fun classificationIgnoresCase() {
        assertEquals(ResourceKind.JUKEBOX, TrackClassifier.classify("bgm_jukebox_M001.bwav"))
    }

    // ---------------------------------------------------------------- flow parts

    @Test
    fun partsOfAFlowAreRecognised() {
        assertTrue(TrackClassifier.isPartOfFlow("BGM_Stage_Coaster_Pre.bwav"))
        assertTrue(TrackClassifier.isPartOfFlow("BGM_Stage_Coaster_Pre_Short.bwav"))
        assertTrue(TrackClassifier.isPartOfFlow("BGM_Stage_Coaster_Bridge.bwav"))
        assertTrue(TrackClassifier.isPartOfFlow("BGM_Stage_Coaster_Outro00.bwav"))
    }

    @Test
    fun aMainFileIsNotAPart() {
        // The main section is the track itself; only its satellites are parts.
        assertTrue(!TrackClassifier.isPartOfFlow("BGM_Stage_Coaster.bwav"))
    }

    // ---------------------------------------------------------------- logical tracks

    private fun track(
        assets: List<AudioAssetId>,
        primary: AudioAssetId = assets.first(),
        kind: ResourceKind = ResourceKind.COURSE,
        frames: Long = 3_373_711,
    ) = LogicalTrack(
        id = LogicalTrack.idOf(kind, primary),
        title = "Coaster",
        kind = kind,
        primary = primary,
        assets = assets,
        isFlow = assets.size > 1,
        frames = frames,
        sampleRate = 48000,
    )

    @Test
    fun aFlowReferencesEveryPart() {
        val assets = (1..5).map { AudioAssetId.of(digest(it)) }
        val t = track(assets)
        assertTrue(t.isFlow)
        assertEquals(5, t.assets.size)
    }

    @Test
    fun aTrackWithNoAssetsIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            track(emptyList(), primary = AudioAssetId.of(digest(1)))
        }
    }

    @Test
    fun thePrimaryMustBeAmongTheAssets() {
        assertFailsWith<IllegalArgumentException> {
            track(
                assets = listOf(AudioAssetId.of(digest(1))),
                primary = AudioAssetId.of(digest(9)),
            )
        }
    }

    @Test
    fun duplicateAssetsAreRefused() {
        val one = AudioAssetId.of(digest(1))
        assertFailsWith<IllegalArgumentException> { track(listOf(one, one)) }
    }

    @Test
    fun aTrackIsResolvableOnlyWhenEveryPartIsPresent() {
        val assets = (1..3).map { AudioAssetId.of(digest(it)) }
        val t = track(assets)

        assertTrue(t.isResolvable(assets.toSet()))
        assertTrue(!t.isResolvable(assets.dropLast(1).toSet()))
    }

    @Test
    fun missingPartsAreListedForRepair() {
        val assets = (1..3).map { AudioAssetId.of(digest(it)) }
        val t = track(assets)
        val present = setOf(assets[0], assets[2])

        val missing = t.missing(present)
        assertEquals(listOf(assets[1]), missing)
    }

    @Test
    fun movingAFileKeepsTheTrackIntact() {
        // A rename changes the display name, not the digest, so the track still resolves.
        val assets = (1..2).map { AudioAssetId.of(digest(it)) }
        val t = track(assets)
        val renamed = t.copy(title = "Something else entirely")
        assertTrue(renamed.isResolvable(assets.toSet()))
        assertEquals(t.id, renamed.id, "a rename must not change identity")
    }

    @Test
    fun theSamePrimaryUnderTwoKindsStaysTwoTracks() {
        // A course theme also compiled into the jukebox is two catalogue entries over one file.
        val primary = AudioAssetId.of(digest(1))
        val asCourse = LogicalTrack.idOf(ResourceKind.COURSE, primary)
        val asJuke = LogicalTrack.idOf(ResourceKind.JUKEBOX, primary)
        assertTrue(asCourse != asJuke, "kind must participate in identity")
    }

    @Test
    fun idsAreStableAcrossInstances() {
        val assets = (1..2).map { AudioAssetId.of(digest(it)) }
        assertEquals(track(assets).id, track(assets).id)
    }
}
