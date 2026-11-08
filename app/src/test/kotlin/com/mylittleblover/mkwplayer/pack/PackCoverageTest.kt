package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.library.Playability
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.LogicalTrack
import com.mylittleblover.mkwplayer.registry.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage is the layer that stops the app printing a number it cannot support.
 *
 * The specific failure it exists to prevent: a pack whose header says "all tracks playable", a
 * decoder that only handles one codec, and a line of UI that therefore renders "100 %" for a library
 * the user can only half hear. Every test below either closes one of those false claims or pins the
 * two-completeness distinction the UI has to keep:
 *
 * - `isPackComplete` is a property of the file;
 * - `isComplete` is a property of the file *and* this build.
 */
class PackCoverageTest {

    private companion object {
        val MAIN: AudioAssetId = AudioAssetId("1".repeat(64))
        val PRE: AudioAssetId = AudioAssetId("2".repeat(64))
        val OTHER: AudioAssetId = AudioAssetId("3".repeat(64))

        val FLOW_KEY: String = LogicalTrack.idOf(ResourceKind.COURSE, MAIN)
        val SINGLE_KEY: String = LogicalTrack.idOf(ResourceKind.BATTLE, OTHER)

        /** DSP-ADPCM, the one codec this build decodes. */
        const val DECODABLE = 1

        /** The kind-2 container the measured catalogue shows 253 entries of; not decodable. */
        const val UNDECODABLE = 2
    }

    private fun asset(digest: AudioAssetId, codecKind: Int = DECODABLE) = PackManifest.Asset(
        digest = digest,
        byteSize = 4096,
        mediaType = "audio/x-bwav",
        codecKind = codecKind,
        sampleRate = 48_000,
        channels = 2,
        frames = 48_000,
    )

    private fun flow() = PackManifest.Track(
        key = FLOW_KEY,
        kind = ResourceKind.COURSE,
        title = "Coaster",
        primary = MAIN,
        isFlow = true,
        parts = listOf(
            PackManifest.Part(MAIN, PackManifest.Role.MAIN),
            PackManifest.Part(PRE, PackManifest.Role.PRE),
        ),
        timing = PackManifest.Timing(
            preludeExit = PackManifest.SyncPoint.EXIT_CUE,
            bridgeEntry = PackManifest.SyncPoint.NEXT_BAR,
            outroEntry = PackManifest.SyncPoint.NEXT_BAR,
            outroSwitch = PackManifest.SyncPoint.IMMEDIATE,
            mainRepeats = 2,
        ),
    )

    private fun single() = PackManifest.Track(
        key = SINGLE_KEY,
        kind = ResourceKind.BATTLE,
        title = "Battle",
        primary = OTHER,
        isFlow = false,
        parts = listOf(PackManifest.Part(OTHER, PackManifest.Role.MAIN)),
    )

    /**
     * A whole pack carrying two tracks.
     *
     * @param preCodec codec of the flow's second part, so a test can make one part undecodable
     *   without changing anything else -- the shape of the failure the real catalogue has.
     * @param stageTracks the tracks a stage claims; defaults to the flow.
     * @param frozen whether the unlock scope is frozen.
     * @param declaredPlayable what the manifest claims; defaults to every track.
     */
    private fun manifest(
        preCodec: Int = DECODABLE,
        stageTracks: List<String> = listOf(FLOW_KEY),
        frozen: Boolean = true,
        declaredPlayable: Int = 2,
        kind: PackManifest.Kind = PackManifest.Kind.FULL,
    ): PackManifest {
        val tracks = listOf(flow(), single())
        val assets = listOf(asset(MAIN), asset(PRE, preCodec), asset(OTHER))
        return PackManifest(
            formatVersion = PackManifest.FORMAT_VERSION,
            packId = "org.example.mkw.base",
            packVersion = 1,
            name = "Test pack",
            kind = kind,
            basePackId = if (kind == PackManifest.Kind.DELTA) "org.example.mkw.base" else null,
            basePackVersion = if (kind == PackManifest.Kind.DELTA) 0 else null,
            coverage = PackManifest.DeclaredCoverage(
                totalLogicalTracks = tracks.size,
                playableLogicalTracks = declaredPlayable,
                // Derived from the assets, as a correct pack must: the declared codec list is one of
                // the claims coverage checks, so a fixture that guessed it would test the wrong rule.
                codecKinds = assets.map { it.codecKind }.distinct(),
            ),
            assets = assets,
            tracks = tracks,
            stages = listOf(PackManifest.Stage("Coaster", "Coaster", stageTracks)),
            provenance = listOf(
                PackManifest.Provenance(FLOW_KEY, "timing", PackManifest.Level.CHOSEN_BY_PROJECT),
            ),
            unlock = PackManifest.Unlock(frozen = frozen, stages = listOf("Coaster")),
        )
    }

    private fun coverageOf(manifest: PackManifest): PackCoverage.Coverage =
        PackCoverage.of(manifest, PackValidation.validate(manifest))

    // ------------------------------------------------------------------------ fully playable

    /**
     * A pack this build can play through is complete, and may define the cup's eligible stages.
     *
     * The cup assertion belongs here rather than in a separate positive test because the two are one
     * decision: eligibility requires a pack that is complete *and* playable, so a fixture that
     * satisfies one and not the other would not exercise the rule.
     */
    @Test
    fun aFullyPlayableFrozenPackIsCompleteAndDefinesTheCup() {
        val coverage = coverageOf(manifest())

        assertTrue(coverage.isPackComplete, "a whole pack is pack-complete: ${coverage.summary}")
        assertTrue(coverage.isComplete, "every track is decodable: ${coverage.summary}")
        assertEquals(2, coverage.trackCount, "track count")
        assertEquals(2, coverage.playableCount, "playable count")
        assertEquals(0, coverage.unplayableCount, "nothing is left unplayable")
        assertTrue(coverage.unsupportedCodecs.isEmpty(), "no unsupported codec is present")
        assertNull(coverage.limitation, "a fully playable pack has no limitation line")
        assertTrue(coverage.canDefineCup, "a complete, frozen, playable pack defines the cup")
        assertEquals(setOf("Coaster"), coverage.cupEligibleStages, "the frozen scope is used verbatim")
        assertTrue(
            coverage.summary.contains("2 of 2"),
            "the summary states both numbers rather than a percentage, got: ${coverage.summary}",
        )
        assertFalse(
            coverage.summary.contains("%"),
            "no percentage is ever assembled, so none can be rounded up: ${coverage.summary}",
        )
    }

    // --------------------------------------------------------------------- the undecodable case

    /**
     * One undecodable part makes its track unplayable, the pack still complete, and the library not.
     *
     * This is the normal case in this project: the pack is a well-formed file, and the build simply
     * lacks a codec. Three claims must stay separate, and this test pins all three:
     *
     * - `isPackComplete` stays true -- the file is whole;
     * - `isComplete` becomes false -- the build cannot play it all;
     * - the summary says how many, never "100 %".
     *
     * A track is playable only if *every* part decodes. Counting the primary alone would report the
     * flow as playable and the user would hear it start and stop at the first undecodable section.
     */
    @Test
    fun anUndecodablePartMakesTheLibraryIncompleteAndSaysHowMany() {
        val coverage = coverageOf(manifest(preCodec = UNDECODABLE))

        assertTrue(
            coverage.isPackComplete,
            "the file is still whole; only the build's capability changed: ${coverage.summary}",
        )
        assertFalse(
            coverage.isComplete,
            "a pack with an undecodable track is not complete for this build",
        )
        assertEquals(1, coverage.playableCount, "only the single-file track fully decodes")
        assertEquals(1, coverage.unplayableCount, "one track is unplayable")

        assertTrue(
            coverage.summary.contains("1 of 2"),
            "the summary states the playable count out of the total, got: ${coverage.summary}",
        )
        assertFalse(
            coverage.summary.contains("%"),
            "no percentage can appear for a partially decodable pack: ${coverage.summary}",
        )

        val codec = assertNotNull(
            coverage.unsupportedCodecs.singleOrNull(),
            "the undecodable codec must be listed: ${coverage.unsupportedCodecs}",
        )
        assertEquals(UNDECODABLE, codec.codecKind, "the codec is named")
        assertEquals(1, codec.affectedTracks, "one track is affected")
        assertEquals(
            Playability.REASON_UNSUPPORTED_CODEC,
            codec.reason,
            "the reason is the same verdict the library already shows",
        )

        val limitation = assertNotNull(coverage.limitation, "a limitation line is required")
        assertTrue(
            limitation.contains("codec 2"),
            "the limitation names the codec, got: $limitation",
        )
        assertTrue(
            limitation.contains("this app") || limitation.contains("this build"),
            "the limitation says the shortfall is the app's, not the pack's: $limitation",
        )

        val distinction = assertNotNull(
            PackCoverage.completeButNotPlayableLine(coverage),
            "the pack complete / library not complete wording must exist",
        )
        assertTrue(
            distinction.contains("complete") && distinction.contains("1 of its 2"),
            "the wording keeps the two claims apart, got: $distinction",
        )
    }

    // ------------------------------------------------------------------- the two completenesses

    /**
     * A structurally broken pack is neither complete nor complete for the build, and says why.
     *
     * The distinction the previous test pins is only useful if it can also be false for the pack
     * itself. A stage that names a track the pack does not carry makes the file inconsistent, so no
     * count taken from it may be presented as a coverage figure at all.
     */
    @Test
    fun aBrokenPackIsNeitherCompleteNorSafeToShow() {
        val coverage = coverageOf(manifest(stageTracks = listOf("course:not-in-this-pack")))

        assertFalse(coverage.isPackComplete, "a stage naming an absent track breaks the pack")
        assertFalse(coverage.isComplete, "an inconsistent pack is never complete")
        assertTrue(
            coverage.summary.contains("not internally consistent"),
            "the summary says the file itself is wrong, got: ${coverage.summary}",
        )
        assertEquals(
            PackCoverage.CupBlocker.PACK_NOT_COMPLETE,
            coverage.cupBlocker,
            "an inconsistent pack cannot define the cup",
        )
        assertTrue(coverage.cupEligibleStages.isEmpty(), "no stage may become eligible")
        assertTrue(
            PackCoverage.completeLine(coverage).contains("must not be imported"),
            "the one line refuses the import rather than softening the claim",
        )
    }

    /**
     * A declared count the content does not support is reported, not repeated.
     *
     * The pack is structurally fine and its tracks are all decodable, so `isPackComplete` would be
     * true on structure alone. The declared "2 playable" against one track of content is what makes
     * it false: the claim is checked against the content instead of being copied to the screen.
     */
    @Test
    fun aDeclaredCoverageThatDoesNotMatchTheContentIsReported() {
        val coverage = coverageOf(manifest(declaredPlayable = 1, stageTracks = listOf(FLOW_KEY)))

        assertFalse(
            coverage.declaredCoverageMatches,
            "2 tracks of content against a declared 1 playable must not match",
        )
        assertFalse(coverage.isPackComplete, "an unsupported claim makes a pack incomplete")
        assertTrue(
            coverage.declarationIsWrong,
            "the mismatch is exposed, so a screen can show the declared number beside the real one",
        )
        assertTrue(
            coverage.summary.contains("do not match"),
            "the summary names the contradiction, got: ${coverage.summary}",
        )
        assertEquals(2, coverage.trackCount, "the real count still comes from the content")
        assertEquals(2, coverage.playableCount, "the real playable count still comes from the content")
        assertEquals(1, coverage.declaredPlayable, "the claim is kept, not overwritten")
    }

    // ----------------------------------------------------------------------------- the cup gate

    /**
     * A frozen scope is not enough: the pack must also be whole and fully playable within the scope.
     *
     * Each refusal below is a different way the cup could open without the user hearing anything:
     * an unfrozen list is provisional by its own admission, a delta states only a fragment, and a
     * scope whose stage cannot be decoded could never be completed -- so quietly dropping it would
     * satisfy "every eligible stage" with the user hearing less than the pack says exists.
     */
    @Test
    fun theCupIsRefusedUnlessThePackIsFrozenCompleteAndPlayable() {
        val unfrozen = coverageOf(manifest(frozen = false))
        assertEquals(
            PackCoverage.CupBlocker.SCOPE_NOT_FROZEN,
            unfrozen.cupBlocker,
            "an unfrozen scope is provisional",
        )
        assertTrue(unfrozen.cupEligibleStages.isEmpty(), "a provisional scope yields no stages")

        val delta = coverageOf(
            manifest(kind = PackManifest.Kind.DELTA, stageTracks = listOf(FLOW_KEY)),
        )
        assertEquals(
            PackCoverage.CupBlocker.PACK_NOT_FULL,
            delta.cupBlocker,
            "a delta does not state a whole soundtrack",
        )
        assertTrue(delta.cupEligibleStages.isEmpty(), "a delta yields no stages")

        val undecodable = coverageOf(manifest(preCodec = UNDECODABLE))
        assertEquals(
            PackCoverage.CupBlocker.UNPLAYABLE_TRACKS,
            undecodable.cupBlocker,
            "a scope whose stage cannot be decoded can never be completed",
        )
        assertTrue(
            undecodable.cupEligibleStages.isEmpty(),
            "a partially playable pack must not shrink the requirement to what it can play",
        )
        assertFalse(undecodable.canDefineCup, "canDefineCup agrees with the blocker")
    }
}
