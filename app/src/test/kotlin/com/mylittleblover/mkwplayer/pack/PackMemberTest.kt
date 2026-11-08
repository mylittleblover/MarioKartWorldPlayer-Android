package com.mylittleblover.mkwplayer.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The member-name check is the only thing between a pack and the user's storage, so every test here is a
 * path that would otherwise reach outside the staging directory.
 *
 * The tests are written as attacks rather than as a list of legal names: a rule that only accepts the right
 * names is easy to get wrong in the direction that matters.
 */
class PackMemberTest {

    private fun refused(name: String): String? = (PackMember.check(name) as? PackMember.Verdict.Refused)?.reason

    private fun allowed(name: String): Boolean = PackMember.check(name) is PackMember.Verdict.Allowed

    // ---------------------------------------------------------------- the attacks

    @Test
    fun aParentTraversalIsRefused() {
        // The one that would write outside the staging directory and overwrite the library file.
        assertNotNull(refused("../../files/library.json"))
    }

    @Test
    fun aTraversalHiddenInsideAudioIsRefused() {
        // A legal prefix does not make the rest of the path legal.
        assertNotNull(refused("audio/../../library.json"))
    }

    @Test
    fun aSingleDotSegmentIsRefused() {
        assertNotNull(refused("./manifest.json"))
    }

    @Test
    fun anAbsolutePathIsRefused() {
        assertNotNull(refused("/etc/passwd"))
    }

    @Test
    fun aDriveLetterIsRefused() {
        // Legal on Windows, and would be interpreted as absolute there.
        assertNotNull(refused("C:/Windows/system32/evil.dll"))
    }

    @Test
    fun aBackslashSeparatorIsRefused() {
        // A pack written on Windows might carry one, and a reader that treats it as a separator escapes.
        assertNotNull(refused("audio\\..\\..\\library.json"))
    }

    @Test
    fun anUnknownRootFileIsRefused() {
        assertNotNull(refused("extra.json"))
    }

    @Test
    fun anUnknownDirectoryIsRefused() {
        assertNotNull(refused("scripts/evil.js"))
    }

    @Test
    fun nestingDeeperThanOneLevelIsRefused() {
        assertNotNull(refused("audio/aa/bb"))
    }

    @Test
    fun anEmptyNameIsRefused() {
        assertNotNull(refused(""))
    }

    @Test
    fun aDoubleSlashIsRefused() {
        // An empty segment collapses to a shorter path on some systems.
        assertNotNull(refused("audio//5d41402abc4b2a76b9719d911017c592"))
    }

    // ---------------------------------------------------------------- the legal names

    @Test
    fun theThreeRootFilesAreAllowed() {
        for (name in listOf("manifest.json", "checksums.json", "catalog.json")) {
            assertTrue(allowed(name), "$name should be allowed")
        }
    }

    @Test
    fun aContentAddressedAssetIsAllowed() {
        val digest = "5d41402abc4b2a76b9719d911017c5920d1c19f6c43824d0b2adb4e9848496cb"
        assertTrue(allowed("audio/$digest"))
    }

    @Test
    fun aGraphMemberIsAllowed() {
        assertTrue(allowed("music-graphs/course_5d41402abc4b2a76b9719d911017c592.json"))
    }

    @Test
    fun aMarkerMemberIsAllowed() {
        assertTrue(allowed("markers/Coaster.json"))
    }

    @Test
    fun aRouteMemberIsAllowed() {
        assertTrue(allowed("routes/GP00.json"))
    }

    @Test
    fun anArtworkMemberIsAllowed() {
        assertTrue(allowed("artwork/5d41402abc4b2a76b9719d911017c592.png"))
    }

    // ---------------------------------------------------------------- the audio-specific rules

    @Test
    fun anAssetWithAnExtensionIsRefused() {
        // Content addressing means the name is the digest and nothing else; an extension is a claim the
        // format deliberately does not carry.
        val digest = "5d41402abc4b2a76b9719d911017c5920d1c19f6c43824d0b2adb4e9848496cb"
        assertNotNull(refused("audio/$digest.bwav"))
    }

    @Test
    fun anUppercaseDigestIsRefused() {
        // Two spellings of one digest would be two members holding identical bytes.
        val digest = "5D41402ABC4B2A76B9719D911017C5920D1C19F6C43824D0B2ADB4E9848496CB"
        assertNotNull(refused("audio/$digest"))
    }

    @Test
    fun aShortDigestIsRefused() {
        assertNotNull(refused("audio/5d41402abc4b2a76b9719d911017c59"))
    }

    @Test
    fun aNonHexDigestIsRefused() {
        assertNotNull(refused("audio/zzzz402abc4b2a76b9719d911017c5920d1c19f6c43824d0b2adb4e9848496cb"))
    }

    @Test
    fun aGraphWithoutTheJsonSuffixIsRefused() {
        assertNotNull(refused("music-graphs/course_5d41402abc4b2a76b9719d911017c592"))
    }

    @Test
    fun aGraphNameWithASlashLikeCharacterIsRefused() {
        // The separator is gone by the time this check runs, so what is tested is that the stem itself is
        // restricted to characters a file name can hold.
        assertNotNull(refused("music-graphs/course:5d41402abc4b2a76b9719d911017c592.json"))
    }

    // ---------------------------------------------------------------- aggregation

    @Test
    fun everyRefusalIsReportedRatherThanTheFirst() {
        // A pack with one bad name usually has a systematic problem, and fixing them one import at a time
        // teaches the user nothing about the pattern.
        val refusals = PackMember.checkAll(
            listOf("manifest.json", "../evil", "audio/short", "scripts/x.js"),
        )
        assertEquals(3, refusals.size)
    }

    @Test
    fun aCleanListingReportsNothing() {
        val digest = "5d41402abc4b2a76b9719d911017c5920d1c19f6c43824d0b2adb4e9848496cb"
        val refusals = PackMember.checkAll(
            listOf("manifest.json", "checksums.json", "catalog.json", "audio/$digest"),
        )
        assertTrue(refusals.isEmpty(), refusals.toString())
    }

    // ---------------------------------------------------------------- identity

    @Test
    fun aPackIsIdentifiedByItsManifestNotItsExtension() {
        // A file named .mkwpack with no manifest is not a pack, and a file named .zip with one is.
        assertTrue(PackMember.hasManifest(listOf("manifest.json", "catalog.json")))
        assertTrue(!PackMember.hasManifest(listOf("catalog.json", "checksums.json")))
    }

    @Test
    fun theDigestCheckIsExact() {
        assertTrue(PackMember.isDigest("5d41402abc4b2a76b9719d911017c5920d1c19f6c43824d0b2adb4e9848496cb"))
        assertTrue(!PackMember.isDigest("5d41402abc4b2a76b9719d911017c592"))
        assertTrue(!PackMember.isDigest(""))
        assertTrue(!PackMember.isDigest("5d41402abc4b2a76b9719d911017c5920d1c19f6c43824d0b2adb4e9848496cZ"))
    }

    @Test
    fun theAllowedAnswerNamesTheDirectory() {
        // The importer needs the directory to know whether to hash an audio member; a boolean would force it
        // to re-parse the name.
        val verdict = PackMember.check("audio/5d41402abc4b2a76b9719d911017c5920d1c19f6c43824d0b2adb4e9848496cb")
        assertEquals("audio", (verdict as PackMember.Verdict.Allowed).directory)

        val root = PackMember.check("manifest.json")
        assertNull((root as PackMember.Verdict.Allowed).directory)
    }
}
