package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.LogicalTrack
import com.mylittleblover.mkwplayer.registry.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Validation is where a well-formed document is told apart from an installable one.
 *
 * The recurring failure in this project is a plausible implementation that quietly makes a claim it
 * cannot support. Each rule below closes one of those claims:
 *
 * - a primary asset outside its own parts makes a track open a file it does not own;
 * - a stage naming an absent track makes a library row that plays nothing;
 * - an empty unlock scope makes "every eligible stage is complete" true over nothing;
 * - a delta without a base applies itself to whatever happens to be installed;
 * - a timing without provenance is a guess the app would have to display as a fact.
 *
 * Every test asserts the *code*, not the wording, so the prose can be improved without rewriting
 * the contract.
 */
class PackValidationTest {

    private companion object {
        val MAIN: AudioAssetId = AudioAssetId("1".repeat(64))
        val PRE: AudioAssetId = AudioAssetId("2".repeat(64))
        val OTHER: AudioAssetId = AudioAssetId("3".repeat(64))

        /** Keys derived the way the library derives them, so the fixture passes that rule. */
        val FLOW_KEY: String = LogicalTrack.idOf(ResourceKind.COURSE, MAIN)
        val SINGLE_KEY: String = LogicalTrack.idOf(ResourceKind.BATTLE, OTHER)
    }

    private fun asset(
        digest: AudioAssetId,
        byteSize: Long = 4096,
        codecKind: Int = 1,
    ) = PackManifest.Asset(
        digest = digest,
        byteSize = byteSize,
        mediaType = "audio/x-bwav",
        codecKind = codecKind,
        sampleRate = 48_000,
        channels = 2,
        frames = 48_000,
    )

    private fun defaultTiming() = PackManifest.Timing(
        preludeExit = PackManifest.SyncPoint.EXIT_CUE,
        bridgeEntry = PackManifest.SyncPoint.NEXT_BAR,
        outroEntry = PackManifest.SyncPoint.NEXT_BAR,
        outroSwitch = PackManifest.SyncPoint.IMMEDIATE,
        mainRepeats = 2,
    )

    private fun flow(
        primary: AudioAssetId = MAIN,
        parts: List<PackManifest.Part> = listOf(
            PackManifest.Part(MAIN, PackManifest.Role.MAIN),
            PackManifest.Part(PRE, PackManifest.Role.PRE),
        ),
        isFlow: Boolean = true,
        timing: PackManifest.Timing? = defaultTiming(),
        key: String = LogicalTrack.idOf(ResourceKind.COURSE, primary),
    ) = PackManifest.Track(
        key = key,
        kind = ResourceKind.COURSE,
        title = "Coaster",
        primary = primary,
        isFlow = isFlow,
        parts = parts,
        timing = timing,
    )

    private fun single() = PackManifest.Track(
        key = SINGLE_KEY,
        kind = ResourceKind.BATTLE,
        title = "Battle",
        primary = OTHER,
        isFlow = false,
        parts = listOf(PackManifest.Part(OTHER, PackManifest.Role.MAIN)),
    )

    /** A manifest that must pass every rule: two tracks, three assets, one complete scope stage. */
    private fun manifest(
        assets: List<PackManifest.Asset> = listOf(asset(MAIN), asset(PRE), asset(OTHER)),
        tracks: List<PackManifest.Track> = listOf(flow(), single()),
        stages: List<PackManifest.Stage> = listOf(
            PackManifest.Stage("Coaster", "Coaster", listOf(FLOW_KEY)),
        ),
        provenance: List<PackManifest.Provenance> = listOf(
            PackManifest.Provenance(FLOW_KEY, "timing", PackManifest.Level.CHOSEN_BY_PROJECT),
        ),
        unlock: PackManifest.Unlock = PackManifest.Unlock(frozen = true, stages = listOf("Coaster")),
        kind: PackManifest.Kind = PackManifest.Kind.FULL,
        basePackId: String? = null,
        basePackVersion: Int? = null,
        packVersion: Int = 2,
        coverage: PackManifest.DeclaredCoverage = PackManifest.DeclaredCoverage(
            totalLogicalTracks = tracks.size,
            playableLogicalTracks = tracks.size,
            codecKinds = listOf(1),
        ),
    ) = PackManifest(
        formatVersion = PackManifest.FORMAT_VERSION,
        packId = "org.example.mkw.base",
        packVersion = packVersion,
        name = "Test pack",
        kind = kind,
        basePackId = basePackId,
        basePackVersion = basePackVersion,
        coverage = coverage,
        assets = assets,
        tracks = tracks,
        stages = stages,
        provenance = provenance,
        unlock = unlock,
    )

    private fun validate(manifest: PackManifest) = PackValidation.validate(manifest)

    // --------------------------------------------------------------------- the happy path

    /**
     * A consistent manifest has no failures.
     *
     * Without this, every refusal test below would pass for the wrong reason: a validator that
     * rejected everything would satisfy each one.
     */
    @Test
    fun aConsistentManifestIsInstallable() {
        val report = validate(manifest())
        assertTrue(report.isInstallable, "a consistent manifest must be installable: ${report.summary()}")
        assertEquals(0, report.failures.size, "no rules may fail")
        assertEquals("The pack is internally consistent.", report.summary(), "the empty summary")
        assertFalse(report.has(PackValidation.Code.NO_TRACKS), "the pack carries tracks")
    }

    // ------------------------------------------------------------------------ relationship rules

    /**
     * A primary asset outside its own parts is refused.
     *
     * This is the mistake a pack builder makes with no visible symptom: both rows read correctly,
     * the pack installs, and the track then opens a file it does not own while the parts it does own
     * are never bound. The runtime's own track type refuses the same shape, so a pack that reached
     * it would fail at playback instead of at import.
     */
    @Test
    fun aPrimaryOutsideItsOwnPartsIsRefused() {
        // The fixture derives the track key from its primary, so overriding the primary would otherwise
        // change the key and the assertion below would compare against the wrong track. Pinning the key
        // keeps the test about the primary, which is what it is testing.
        val broken = flow(
            primary = PRE,
            parts = listOf(PackManifest.Part(MAIN, PackManifest.Role.MAIN)),
            key = FLOW_KEY,
        )
        val report = validate(manifest(tracks = listOf(broken, single())))

        assertFalse(report.isInstallable, "a primary outside the parts must not install")
        val failure = report.forCode(PackValidation.Code.PRIMARY_NOT_IN_PARTS).singleOrNull()
        assertTrue(failure != null, "PRIMARY_NOT_IN_PARTS must be reported: ${report.messages()}")
        assertEquals(FLOW_KEY, failure!!.subject, "the failure names the track")
        assertTrue(
            failure.message.contains(PRE.short()),
            "the message names the offending digest, got: ${failure.message}",
        )
    }

    /**
     * A track referencing an asset the pack does not carry is refused.
     *
     * Left accepted, the track installs as playable and dies at the first part the decoder cannot
     * find -- after the library has already counted it as present.
     */
    @Test
    fun aTrackReferencingAMissingAssetIsRefused() {
        val broken = flow(
            parts = listOf(
                PackManifest.Part(MAIN, PackManifest.Role.MAIN),
                PackManifest.Part(PRE, PackManifest.Role.PRE),
            ),
        )
        // The PRE asset is deliberately left out of the asset list.
        val report = validate(manifest(assets = listOf(asset(MAIN), asset(OTHER)), tracks = listOf(broken, single())))

        assertFalse(report.isInstallable, "a missing asset must not install")
        val failure = report.forCode(PackValidation.Code.TRACK_ASSET_MISSING).singleOrNull()
        assertTrue(failure != null, "TRACK_ASSET_MISSING must be reported: ${report.messages()}")
        assertTrue(
            failure!!.message.contains(PRE.short()),
            "the message names the absent digest, got: ${failure.message}",
        )
    }

    /**
     * A stage naming a track the pack does not carry is refused.
     *
     * Otherwise the stage appears in the library with a row that plays nothing, and the user has no
     * way to tell that from a stage whose music is simply not to their taste.
     */
    @Test
    fun aStageNamingAnUnknownTrackIsRefused() {
        val report = validate(
            manifest(stages = listOf(PackManifest.Stage("Coaster", "Coaster", listOf("course:deadbeef")))),
        )

        assertFalse(report.isInstallable, "an unknown stage track must not install")
        assertTrue(
            report.has(PackValidation.Code.STAGE_TRACK_MISSING),
            "STAGE_TRACK_MISSING must be reported: ${report.messages()}",
        )
    }

    // ------------------------------------------------------------------------- asset size rules

    /**
     * A zero-byte audio asset is refused, and a negative size is refused for its own reason.
     *
     * A zero-byte entry is exactly what a truncated extraction leaves behind: the row exists, the
     * file is empty. Installed, it would count as present and play silence. The two rules have
     * separate codes because they mean different things about how the pack was produced.
     */
    @Test
    fun anEmptyOrNegativeAssetIsRefused() {
        val empty = validate(manifest(assets = listOf(asset(MAIN, byteSize = 0), asset(PRE), asset(OTHER))))
        assertFalse(empty.isInstallable, "a zero-byte asset must not install")
        assertEquals(
            1,
            empty.forCode(PackValidation.Code.ASSET_EMPTY).size,
            "exactly the empty asset is reported: ${empty.messages()}",
        )

        val negative = validate(
            manifest(assets = listOf(asset(MAIN, byteSize = -1), asset(PRE), asset(OTHER))),
        )
        assertFalse(negative.isInstallable, "a negative size must not install")
        assertEquals(
            1,
            negative.forCode(PackValidation.Code.ASSET_SIZE_NEGATIVE).size,
            "the negative size has its own code: ${negative.messages()}",
        )
    }

    // ------------------------------------------------------------------------- delta base rules

    /**
     * A delta without a base is refused; it cannot be applied anywhere.
     *
     * The obvious alternative -- treat a base-less delta as a full pack -- silently replaces the
     * user's library with the small overlay the delta carries.
     */
    @Test
    fun aDeltaWithoutABaseIsRefused() {
        val report = validate(
            manifest(
                kind = PackManifest.Kind.DELTA,
                basePackId = null,
                basePackVersion = null,
                packVersion = 3,
            ),
        )

        assertFalse(report.isInstallable, "a base-less delta must not install")
        val failure = report.forCode(PackValidation.Code.DELTA_WITHOUT_BASE).singleOrNull()
        assertTrue(failure != null, "DELTA_WITHOUT_BASE must be reported: ${report.messages()}")
        assertTrue(
            failure!!.message.contains("basePackId"),
            "the message names the missing field, got: ${failure.message}",
        )
    }

    /**
     * A full pack naming a base is refused; the two fields make it a delta in everything but label.
     *
     * Installed as a full pack it would replace the library with only the part this file carries,
     * which is the same damage as the previous test, arrived at from the other direction.
     */
    @Test
    fun aFullPackNamingABaseIsRefused() {
        val report = validate(
            manifest(
                kind = PackManifest.Kind.FULL,
                basePackId = "org.example.mkw.base",
                basePackVersion = 1,
            ),
        )

        assertFalse(report.isInstallable, "a full pack with a base must not install")
        assertTrue(
            report.has(PackValidation.Code.FULL_WITH_BASE),
            "FULL_WITH_BASE must be reported: ${report.messages()}",
        )
    }

    // --------------------------------------------------------------------------- unlock rules

    /**
     * An empty unlock scope is refused.
     *
     * This is the empty-set trap one layer above the gate itself: `all(emptyList())` is true, so an
     * empty scope would make "every eligible stage has been heard" true on a fresh install and the
     * cup would open before a note played.
     */
    @Test
    fun anEmptyUnlockScopeIsRefused() {
        val report = validate(
            manifest(unlock = PackManifest.Unlock(frozen = true, stages = emptyList())),
        )

        assertFalse(report.isInstallable, "an empty scope must not install")
        assertTrue(
            report.has(PackValidation.Code.UNLOCK_SCOPE_EMPTY),
            "UNLOCK_SCOPE_EMPTY must be reported: ${report.messages()}",
        )
    }

    /**
     * An unlock scope naming a stage the pack does not describe is refused.
     *
     * That stage could never be completed, so the cup would never open and the progress screen would
     * ask for something unobtainable. Killing the feature quietly is worse than refusing the pack.
     */
    @Test
    fun anUnlockScopeNamingAnUnknownStageIsRefused() {
        val report = validate(
            manifest(unlock = PackManifest.Unlock(frozen = true, stages = listOf("PeachBeach"))),
        )

        assertFalse(report.isInstallable, "an unknown scope stage must not install")
        val failure = report.forCode(PackValidation.Code.UNLOCK_STAGE_UNKNOWN).singleOrNull()
        assertTrue(failure != null, "UNLOCK_STAGE_UNKNOWN must be reported: ${report.messages()}")
        assertEquals("PeachBeach", failure!!.subject, "the failure names the stage")
    }

    // ------------------------------------------------------------------------------ provenance

    /**
     * A timing with no provenance record is refused.
     *
     * The timings are choices, not measurements. A pack that declares them without saying so would
     * force the app either to show them bare -- reading as game behaviour -- or to attach a warning
     * the data itself does not support. The format makes the pack say it.
     */
    @Test
    fun aTimingWithoutProvenanceIsRefused() {
        val report = validate(
            manifest(
                provenance = listOf(
                    PackManifest.Provenance(SINGLE_KEY, "timing", PackManifest.Level.CHOSEN_BY_PROJECT),
                ),
            ),
        )

        assertFalse(report.isInstallable, "uncovered timings must not install")
        val failure = report.forCode(PackValidation.Code.TIMING_WITHOUT_PROVENANCE).singleOrNull()
        assertTrue(
            failure != null,
            "TIMING_WITHOUT_PROVENANCE must be reported: ${report.messages()}",
        )
        assertEquals(FLOW_KEY, failure!!.subject, "the failure names the track whose timing is bare")
    }

    // ------------------------------------------------------------------------------ reporting

    /**
     * Every broken rule is reported, not only the first.
     *
     * The point of the whole report type: a pack author fixes one defect, re-exports a multi-gigabyte
     * archive and discovers the next one. Four defects must produce four failures -- and each must be
     * the *right* one, because a validator that reported one failure four times would pass a naive
     * count assertion.
     */
    @Test
    fun validationReportsEveryFailureRatherThanTheFirst() {
        val brokenFlow = flow(
            primary = PRE,
            parts = listOf(PackManifest.Part(MAIN, PackManifest.Role.MAIN)),
        )
        val report = validate(
            manifest(
                assets = listOf(asset(MAIN, byteSize = 0)),
                tracks = listOf(brokenFlow),
                stages = listOf(PackManifest.Stage("Coaster", "Coaster", listOf("course:missing"))),
                provenance = emptyList(),
                unlock = PackManifest.Unlock(frozen = true, stages = emptyList()),
            ),
        )

        assertFalse(report.isInstallable, "a manifest with four defects must not install")
        val codes = report.failures.map { it.code }.toSet()
        val expected = setOf(
            PackValidation.Code.ASSET_EMPTY,
            PackValidation.Code.PRIMARY_NOT_IN_PARTS,
            PackValidation.Code.STAGE_TRACK_MISSING,
            PackValidation.Code.PROVENANCE_MISSING,
            PackValidation.Code.UNLOCK_SCOPE_EMPTY,
            PackValidation.Code.TIMING_WITHOUT_PROVENANCE,
        )
        val absent = expected.filterNot { it in codes }
        assertTrue(
            absent.isEmpty(),
            "every broken rule must appear in one report; missing $absent from ${report.messages()}",
        )
        assertTrue(report.ruleCount >= 6, "six distinct rules failed, got ${report.ruleCount}")
        assertTrue(
            report.summary().contains("${report.failures.size}"),
            "the summary states how many problems there are, got: ${report.summary()}",
        )
    }
}
