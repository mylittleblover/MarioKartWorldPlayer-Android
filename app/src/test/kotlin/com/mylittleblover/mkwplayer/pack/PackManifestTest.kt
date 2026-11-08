package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parsing is the boundary between "a file someone handed us" and "a pack this build will act on".
 *
 * Every test here exists because the plausible implementation gets the case wrong in a way that is
 * invisible afterwards. A parser that defaults an unknown format version reads a v2 pack as v1 and
 * silently drops every field v2 added. A parser that accepts a 63-character digest installs a pack
 * whose assets can never resolve, and reports success. A parser that reads an unknown provenance
 * level as the weakest one launders a future `L3_VERIFIED` claim into displayable data.
 *
 * The fixture is the specification's own example, so a change that breaks the documented shape
 * breaks these tests.
 */
class PackManifestTest {

    private companion object {
        val MAIN: String = "a".repeat(64)
        val OUTRO: String = "b".repeat(64)
        val BATTLE: String = "c".repeat(64)

        /** The key the format derives for [MAIN] under [ResourceKind.COURSE]. */
        val MAIN_KEY: String = "course:" + MAIN.take(32)
        val BATTLE_KEY: String = "battle:" + BATTLE.take(32)
    }

    /**
     * A valid v1 document: two tracks, three assets, one stage, one frozen unlock scope.
     *
     * Written compactly so a test can `replace` one token and change exactly one fact.
     */
    private fun document(): String = """
        {
          "formatVersion":1,
          "packId":"org.example.mkw.base",
          "packVersion":3,
          "name":"MKW Soundtrack Pack",
          "kind":"FULL",
          "coverage":{"totalLogicalTracks":2,"playableLogicalTracks":2,"codecKinds":[1,2]},
          "assets":[
            {"digest":"$MAIN","byteSize":1024,"mediaType":"audio/x-bwav","codecKind":1,
             "sampleRate":48000,"channels":2,"frames":48000,"loopStart":12000,"loopEnd":48000},
            {"digest":"$OUTRO","byteSize":512,"mediaType":"audio/x-bwav","codecKind":2,
             "sampleRate":48000,"channels":2,"frames":24000},
            {"digest":"$BATTLE","byteSize":2048,"mediaType":"audio/x-bwav","codecKind":1,
             "sampleRate":48000,"channels":2,"frames":96000}
          ],
          "tracks":[
            {"key":"$MAIN_KEY","kind":"COURSE","title":"Coaster","primary":"$MAIN","isFlow":true,
             "parts":[
               {"asset":"$MAIN","role":"MAIN"},
               {"asset":"$OUTRO","role":"OUTRO","outro":0}
             ],
             "timing":{"preludeExit":"EXIT_CUE","bridgeEntry":"NEXT_BAR","outroEntry":"NEXT_BAR",
                       "outroSwitch":"IMMEDIATE","mainRepeats":2}},
            {"key":"$BATTLE_KEY","kind":"BATTLE","title":"Battle","primary":"$BATTLE","isFlow":false,
             "parts":[{"asset":"$BATTLE","role":"MAIN"}]}
          ],
          "stages":[{"code":"Coaster","name":"Coaster","tracks":["$MAIN_KEY"]}],
          "provenance":[
            {"subject":"$MAIN_KEY","field":"timing","level":"CHOSEN_BY_PROJECT",
             "note":"this project's choice"}
          ],
          "unlock":{"frozen":true,"stages":["Coaster"]}
        }
    """.trimIndent()

    private fun parseOk(text: String = document()): PackManifest {
        val result = PackManifest.parse(text)
        val manifest = assertNotNull(
            result.manifestOrNull(),
            "expected the manifest to parse, got: ${result.reasonOrNull()}",
        )
        return manifest
    }

    // ------------------------------------------------------------------- the happy path

    /**
     * The documented shape parses, and every field survives.
     *
     * This is the test that would fail if a field were quietly dropped from the model: the pack would
     * still install, and the missing value would be re-derived by the runtime, which is exactly what
     * the format forbids.
     */
    @Test
    fun aWellFormedManifestParses() {
        val manifest = parseOk()

        assertEquals(1, manifest.formatVersion, "format version")
        assertEquals("org.example.mkw.base", manifest.packId, "pack id")
        assertEquals(3, manifest.packVersion, "pack version")
        assertEquals("MKW Soundtrack Pack", manifest.name, "display name")
        assertEquals(PackManifest.Kind.FULL, manifest.kind, "kind")
        assertFalse(manifest.isDelta, "a FULL pack is not a delta")
        assertNull(manifest.basePackId, "a FULL pack names no base")

        assertEquals(3, manifest.assets.size, "asset count")
        assertEquals(2, manifest.tracks.size, "track count")

        val main = manifest.asset(AudioAssetId(MAIN))
        assertNotNull(main, "the main asset must be addressable by digest")
        assertEquals(1024L, main.byteSize, "byte size")
        assertEquals(1, main.codecKind, "codec kind")
        assertEquals(48_000, main.sampleRate, "sample rate")
        assertEquals(2, main.channels, "channels")
        assertEquals(48_000L, main.frames, "frames")
        assertEquals(12_000L, main.loopStart, "loop start")
        assertEquals(48_000L, main.loopEnd, "loop end")
        assertTrue(main.hasLoop, "the loop window is complete")

        val coaster = manifest.track(MAIN_KEY)
        assertNotNull(coaster, "the documented track key must resolve")
        assertEquals(ResourceKind.COURSE, coaster.kind, "track kind")
        assertEquals("Coaster", coaster.title, "track title")
        assertTrue(coaster.isFlow, "the coaster track is a flow")
        assertEquals(2, coaster.parts.size, "part count")
        assertEquals(PackManifest.Role.OUTRO, coaster.parts[1].role, "second part is an outro")
        assertEquals(0, coaster.parts[1].outro, "outro number")
        assertNotNull(coaster.timing, "a flow states its timings")
        assertEquals(PackManifest.SyncPoint.EXIT_CUE, coaster.timing.preludeExit, "prelude exit")
        assertEquals(2, coaster.timing.mainRepeats, "main repeats")

        val stage = manifest.stage("Coaster")
        assertNotNull(stage, "the stage must resolve by code")
        assertEquals(listOf(MAIN_KEY), stage.tracks, "stage tracks")
        assertEquals(1, manifest.tracksOf(stage).size, "both stage tracks resolve")

        assertEquals(2, manifest.coverage.totalLogicalTracks, "declared total")
        assertEquals(2, manifest.coverage.playableLogicalTracks, "declared playable")
        assertEquals(listOf(1, 2), manifest.coverage.codecKinds, "declared codecs")

        assertTrue(manifest.unlock.frozen, "the scope is frozen")
        assertEquals(listOf("Coaster"), manifest.unlock.stages, "scope")

        val provenance = manifest.provenanceFor(MAIN_KEY, "timing")
        assertNotNull(provenance, "the timing must have a provenance record")
        assertEquals(PackManifest.Level.CHOSEN_BY_PROJECT, provenance.level, "provenance level")
    }

    /**
     * An absent loop is null, not 0.
     *
     * Frame 0 is a real position. A parser that defaulted the loop to 0 would claim the BATTLE asset
     * loops over nothing, and the runtime would either loop forever over zero frames or refuse the
     * asset for a reason that has nothing to do with the pack.
     */
    @Test
    fun anAbsentLoopStaysNull() {
        val manifest = parseOk()
        val battle = assertNotNull(manifest.asset(AudioAssetId(BATTLE)))
        assertNull(battle.loopStart, "an asset with no loop has no start")
        assertNull(battle.loopEnd, "an absent loop has no end")
        assertFalse(battle.hasLoop, "no loop was measured")
    }

    // ---------------------------------------------------------------- refusals: the document

    /**
     * A format version this build does not implement is refused, and the reason says so.
     *
     * The alternative -- reading the fields that happen to match -- produces a plausible pack whose
     * missing facts the runtime fills in. That is how a format silently diverges between versions.
     */
    @Test
    fun anUnknownFormatVersionIsRefused() {
        val result = PackManifest.parse(document().replace("\"formatVersion\":1", "\"formatVersion\":2"))
        val reason = assertNotNull(result.reasonOrNull(), "a v2 pack must be refused")
        assertTrue(
            reason.contains("format version 2"),
            "the reason must name the version the pack declared, got: $reason",
        )
        assertTrue(
            reason.contains("version 1"),
            "the reason must name the version this build reads, got: $reason",
        )
    }

    /**
     * A missing manifest is refused by name, not reported as an empty pack.
     *
     * "No manifest" and "a manifest that lists nothing" are different facts, and the import screen
     * has to be able to tell the user which one happened.
     */
    @Test
    fun aMissingManifestIsRefusedByItsName() {
        val result = PackManifest.read(null)
        val reason = assertNotNull(result.reasonOrNull(), "a null manifest must be refused")
        assertEquals(PackManifest.MISSING_REASON, reason, "the fixed wording is used")
        assertTrue(reason.contains("manifest.json"), "the reason names the member")
    }

    /**
     * Text that is not JSON is refused with a reason that names the member.
     *
     * A parser that returned an empty manifest here would install an empty library and report
     * success, which is the failure this whole layer exists to prevent.
     */
    @Test
    fun aDocumentThatIsNotJsonIsRefused() {
        val reason = assertNotNull(
            PackManifest.parse("not a manifest").reasonOrNull(),
            "invalid JSON must be refused",
        )
        assertTrue(
            reason.contains("manifest.json") && reason.contains("JSON"),
            "the reason must say the manifest is not JSON, got: $reason",
        )
    }

    // ------------------------------------------------------------------ refusals: the content

    /**
     * An uppercase digest is refused.
     *
     * The digest is a case-sensitive content address. Accepting `ABCDEF...` would resolve to a
     * different key than the lowercase form everywhere the app compares strings, so the asset would
     * pass parsing and never be found.
     */
    @Test
    fun aMalformedDigestIsRefused() {
        val upper = document().replace(MAIN, MAIN.uppercase())
        val upperReason = assertNotNull(
            PackManifest.parse(upper).reasonOrNull(),
            "an uppercase digest must be refused",
        )
        assertTrue(
            upperReason.contains("64 lowercase hex"),
            "the reason must say what a digest has to look like, got: $upperReason",
        )

        val short = document().replace(MAIN, MAIN.take(63))
        val shortReason = assertNotNull(
            PackManifest.parse(short).reasonOrNull(),
            "a truncated digest must be refused",
        )
        assertTrue(
            shortReason.contains("64 lowercase hex"),
            "the shortened digest is refused for the same stated reason, got: $shortReason",
        )
    }

    /**
     * A playable count above the total is refused at parse time.
     *
     * The document contradicts itself, and every number taken from it would then be untrustworthy.
     * Refusing here means no later screen has to decide which of the two numbers to believe.
     */
    @Test
    fun aPlayableCountAboveTheTotalIsRefused() {
        val text = document().replace(
            "\"totalLogicalTracks\":2,\"playableLogicalTracks\":2",
            "\"totalLogicalTracks\":2,\"playableLogicalTracks\":3",
        )
        val reason = assertNotNull(
            PackManifest.parse(text).reasonOrNull(),
            "3 playable of 2 total must be refused",
        )
        assertTrue(
            reason.contains("3") && reason.contains("2"),
            "the reason must quote both numbers, got: $reason",
        )
    }

    /**
     * An unknown section role is refused rather than treated as a Main part.
     *
     * Defaulting an unrecognised role to Main would put an intro into the main loop slot and shorten
     * every listen, with nothing in the UI able to show that a part moved.
     */
    @Test
    fun anUnknownRoleIsRefused() {
        val text = document().replace("{\"asset\":\"$MAIN\",\"role\":\"MAIN\"}",
            "{\"asset\":\"$MAIN\",\"role\":\"INTRO\"}")
        val reason = assertNotNull(
            PackManifest.parse(text).reasonOrNull(),
            "an unknown role must be refused",
        )
        assertTrue(
            reason.contains("INTRO"),
            "the reason must quote the unknown role so a pack author can find it, got: $reason",
        )
    }

    /**
     * An unknown provenance level is refused, never read as the weakest known one.
     *
     * This is the laundering mistake: a future pack claiming `L3_VERIFIED_EVENTS` must fail loudly,
     * because being read as `UNVERIFIED` would let it pass validation while the app displayed
     * marker data as if the pack had claimed nothing.
     */
    @Test
    fun anUnknownProvenanceLevelIsRefused() {
        val text = document().replace("\"level\":\"CHOSEN_BY_PROJECT\"", "\"level\":\"L3_VERIFIED\"")
        val reason = assertNotNull(
            PackManifest.parse(text).reasonOrNull(),
            "an unknown provenance level must be refused",
        )
        assertTrue(
            reason.contains("L3_VERIFIED"),
            "the reason must quote the unknown level, got: $reason",
        )
    }

    // ------------------------------------------------------------------- pack kind: the delta

    /**
     * A delta parses with its base, and the base survives.
     *
     * If the parser dropped `basePackId` the installer could only compare version numbers, so a
     * delta for one product could be applied to another product that happened to be at the same
     * version.
     */
    @Test
    fun aDeltaCarriesItsBase() {
        val text = document()
            .replace(
                "\"kind\":\"FULL\",",
                "\"kind\":\"DELTA\",\"basePackId\":\"org.example.mkw.base\",\"basePackVersion\":2,",
            )
            .replace("\"packVersion\":3", "\"packVersion\":4")

        val manifest = parseOk(text)
        assertTrue(manifest.isDelta, "the pack is a delta")
        assertEquals(PackManifest.Kind.DELTA, manifest.kind, "kind")
        assertEquals("org.example.mkw.base", manifest.basePackId, "base pack id")
        assertEquals(2, manifest.basePackVersion, "base version")
        assertEquals(4, manifest.packVersion, "delta version moves past its base")
    }
}
