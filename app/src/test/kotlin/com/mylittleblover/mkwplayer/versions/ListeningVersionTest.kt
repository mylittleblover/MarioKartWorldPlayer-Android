package com.mylittleblover.mkwplayer.versions

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A saved listening version is a promise: "this is how I want this track played".
 *
 * Every test here guards one way that promise can be broken without an error. Identity drifting on
 * rename, a duplicate sharing its original's slot, a snapshot that comes back in a different
 * order, a restore that passes itself off as an authored decision, a version that silently plays
 * three of its five parts, or two rows in a list that read the same -- none of those crash, and
 * all of them make the saved version a lie about what the user chose.
 */
class ListeningVersionTest {

    private fun digest(seed: Int): ByteArray = ByteArray(32) { (seed + it).toByte() }

    private val preAsset = AudioAssetId.of(digest(1))
    private val mainAsset = AudioAssetId.of(digest(2))
    private val bridgeAsset = AudioAssetId.of(digest(3))
    private val outro0Asset = AudioAssetId.of(digest(4))
    private val outro1Asset = AudioAssetId.of(digest(5))
    private val otherTrackAsset = AudioAssetId.of(digest(6))

    private val pre = Section(StageFlow.Role.Pre)
    private val bridge = Section(StageFlow.Role.Bridge)
    private val main = Section(StageFlow.Role.Main)
    private val outro0 = Section(StageFlow.Role.Outro, 0)
    private val outro1 = Section(StageFlow.Role.Outro, 1)

    private val structuredAssets = listOf(preAsset, bridgeAsset, mainAsset, outro0Asset)

    /**
     * A plausible multi-part version: prelude, bridge, main twice, outro 0.
     *
     * [repeats] defaults to one per section except Main, which follows `timing.mainRepeats`, so a
     * helper call is always a valid version unless a test deliberately breaks one rule.
     */
    private fun structured(
        id: VersionId = VersionId.fresh(),
        name: String = "Structured",
        assets: List<AudioAssetId> = structuredAssets,
        primary: AudioAssetId = assets.first(),
        sections: List<Section> = listOf(pre, bridge, main, outro0),
        timing: StageFlow.Timing = StageFlow.DEFAULT_TIMING,
        repeats: Map<Section, Int> = sections.associateWith { section ->
            if (section.role == StageFlow.Role.Main) timing.mainRepeats else 1
        },
        provenance: StageFlow.Provenance = StageFlow.Provenance.PROVISIONAL,
        origin: Origin = Origin.AUTHORED,
    ): ListeningVersion = ListeningVersion(
        id = id,
        name = name,
        primary = primary,
        assets = assets,
        sections = sections,
        repeats = repeats,
        timing = timing,
        provenance = provenance,
        origin = origin,
    )

    private fun linear(name: String = "Single file", asset: AudioAssetId = otherTrackAsset) =
        ListeningVersion.linear(VersionId.fresh(), name, asset)

    // ---------------------------------------------------------------- identity

    @Test
    fun renamingDoesNotChangeIdentity() {
        // Deriving the id from the name looks harmless and would break every snapshot and
        // selection the moment the user renames the version.
        val original = structured(name = "First take")
        val renamed = original.renamed("Second take")

        assertEquals(original.id, renamed.id, "a rename must not change identity")
        assertEquals(original.primary, renamed.primary, "a rename must not change the track")
        assertEquals(original.assets, renamed.assets, "a rename must not change the assets")
        assertEquals(original.sections, renamed.sections, "a rename must not change the order")
        assertEquals("Second take", renamed.name, "the new name must be the one that is kept")
        // The value the caller still holds keeps its name, or an undo restores an edit instead of
        // the state before it.
        assertEquals("First take", original.name, "renaming must return a new value, not mutate")
    }

    @Test
    fun duplicateGetsANewIdentity() {
        val original = structured(name = "Original").withRepeatCount(main, 4)
        val copy = original.duplicated("Copy")

        assertNotEquals(original.id, copy.id, "a duplicate must not share the original's identity")
        assertEquals("Copy", copy.name, "the duplicate keeps the name it was given")
        assertEquals(original.sections, copy.sections, "a duplicate copies the section order")
        assertEquals(original.repeats, copy.repeats, "a duplicate copies the repeat counts")
        assertEquals(original.timing, copy.timing, "a duplicate copies the timing choices")
        assertEquals(original.assets, copy.assets, "a duplicate must bind the same assets")
        assertEquals(original.provenance, copy.provenance, "a duplicate must keep the provenance")
        assertEquals(
            4,
            copy.repeats[main],
            "a duplicate must keep the main repeat count the original had been edited to",
        )
    }

    @Test
    fun duplicateRefusesToReuseTheIdentity() {
        val original = structured(name = "Original")
        val error = assertFailsWith<IllegalArgumentException> {
            original.duplicated("Copy", original.id)
        }
        assertTrue(
            error.message!!.contains("new identity"),
            "reusing an id leaves two saved versions find-by-id cannot tell apart",
        )
    }

    // ---------------------------------------------------------------- snapshots

    @Test
    fun aSnapshotRoundTripsTheSectionOrderExactly() {
        // Order is content here: the same sections in another order are another listen.
        val original = structured(name = "Ordered")
        val reordered = original.withSectionOrder(listOf(main, pre, outro0, bridge))

        assertEquals(
            original.sections,
            original.snapshot().restore().sections,
            "a snapshot must round-trip the order",
        )
        assertEquals(
            reordered.sections,
            reordered.snapshot("after reordering").restore().sections,
            "a snapshot must round-trip the reordered sequence, not a sorted copy of it",
        )
        assertNotEquals(
            original.sections,
            reordered.sections,
            "the reordered version must actually differ, or this test proves nothing",
        )
    }

    @Test
    fun aSnapshotRoundTripsTheRepeatCountsAndTiming() {
        val original = structured(name = "Counts").withRepeatCount(bridge, 3)
        val restored = original.snapshot().restore()

        assertEquals(original.repeats, restored.repeats, "a snapshot must round-trip every repeat count")
        assertEquals(original.timing, restored.timing, "a snapshot must round-trip the timing choices")
        assertEquals(original.assets, restored.assets, "a snapshot must round-trip the asset order")
        assertEquals(original.id, restored.id, "a restore puts the same saved slot back")
    }

    @Test
    fun aRestoredVersionReportsThatItIsARestorePoint() {
        // Presenting restored numbers as an authored decision claims the user chose values they
        // had already discarded.
        val original = structured(name = "Authored")
        assertTrue(!original.isRestorePoint, "a freshly authored version is not a restore point")

        val restored = original.snapshot("before trying another order").restore()
        assertTrue(restored.isRestorePoint, "a restored version must say that it is a restore point")
        assertEquals(Origin.RESTORED, restored.origin, "a restore must not claim to be authored")
    }

    @Test
    fun aSnapshotIsNotChangedByLaterEdits() {
        val original = structured(name = "Original")
        val snapshot = original.snapshot()

        original.renamed("Edited").withSectionOrder(listOf(main, pre, outro0, bridge))

        assertEquals(
            "Original",
            snapshot.captured.name,
            "editing the version must not reach into its snapshot",
        )
        assertEquals(
            original.sections,
            snapshot.captured.sections,
            "a snapshot must keep the order it captured",
        )
    }

    @Test
    fun restoringIntoTheLibraryKeepsTheSavedPosition() {
        // Appending would move the entry to the end of the list on every restore, which reads as
        // a different library.
        val first = structured(name = "First")
        val second = structured(name = "Second")
        val third = structured(name = "Third")
        val library = VersionLibrary().add(first).add(second).add(third)

        val snapshot = second.snapshot("before reordering")
        val edited = library.replace(second.withSectionOrder(listOf(main, pre, outro0, bridge)))
        val restored = edited.restore(snapshot)

        assertEquals(
            listOf(first.id, second.id, third.id),
            restored.versions.map { it.id },
            "a restore must keep the version in its saved slot",
        )
        assertEquals(
            second.sections,
            restored.find(second.id)!!.sections,
            "a restore must bring the captured order back",
        )
        assertTrue(
            restored.find(second.id)!!.isRestorePoint,
            "a restored version must be marked as one",
        )
    }

    // ---------------------------------------------------------------- the library

    @Test
    fun aNameCollisionWithinOneTrackIsRefused() {
        // Two rows reading the same name on one track cannot be told apart, so the user may pick
        // the one they did not mean.
        val library = VersionLibrary().add(structured(name = "Main mix"))
        val error = assertFailsWith<IllegalArgumentException> {
            library.add(structured(name = "Main mix"))
        }
        assertTrue(
            error.message!!.contains("already names"),
            "a same-track duplicate name must be refused, got: ${error.message}",
        )
    }

    @Test
    fun theSameNameOnDifferentTracksIsAllowed() {
        // The track is what separates the rows, so nothing is ambiguous here.
        val library = VersionLibrary()
            .add(linear("Main mix", otherTrackAsset))
            .add(linear("Main mix", outro1Asset))

        assertEquals(2, library.size, "the same name on two tracks must be allowed")
        assertEquals(1, library.forTrack(otherTrackAsset).size, "each track keeps its own version")
    }

    @Test
    fun aNameCollisionIgnoresCaseAndSurroundingSpaces() {
        // "Main mix" and " main mix " look identical in a list; storing both is the same problem
        // as storing the same string twice.
        val library = VersionLibrary().add(structured(name = "Main mix"))

        assertTrue(
            library.hasNameCollision(" main mix ", preAsset),
            "case and padding must not hide a collision",
        )
        assertFailsWith<IllegalArgumentException> { library.add(structured(name = "MAIN MIX")) }
    }

    @Test
    fun forTrackReturnsOnlyThatTracksVersionsInSavedOrder() {
        val trackA = structured(name = "A first")
        val trackB = linear("B only", otherTrackAsset)
        val trackA2 = structured(name = "A second")
        val library = VersionLibrary().add(trackA).add(trackB).add(trackA2)

        assertEquals(
            listOf(trackA.id, trackA2.id),
            library.forTrack(trackA.primary).map { it.id },
            "find-by-track must return that track's versions in the order they were saved",
        )
        assertEquals(
            listOf(trackA.primary, otherTrackAsset),
            library.tracks(),
            "the track list must follow first appearance",
        )
    }

    @Test
    fun findByIdReturnsTheVersionOrNull() {
        val saved = structured(name = "Saved")
        val library = VersionLibrary().add(saved)

        assertEquals(saved, library.find(saved.id), "find-by-id must return the saved value")
        assertNull(
            library.find(VersionId.fresh()),
            "an unknown id must report nothing, not another version",
        )
    }

    @Test
    fun removeDropsOnlyTheNamedVersion() {
        val keep = structured(name = "Keep")
        val drop = structured(name = "Drop")
        val library = VersionLibrary().add(keep).add(drop).remove(drop.id)

        assertEquals(
            listOf(keep.id),
            library.versions.map { it.id },
            "remove must drop exactly one version",
        )
        assertNull(library.find(drop.id), "the removed version must be gone")

        val error = assertFailsWith<IllegalArgumentException> { library.remove(drop.id) }
        assertTrue(
            error.message!!.contains("no version"),
            "removing an id that is not saved must be refused, not silently ignored",
        )
    }

    @Test
    fun replacingAnEditedVersionKeepsItsPosition() {
        val first = structured(name = "First")
        val second = structured(name = "Second")
        val third = structured(name = "Third")
        val library = VersionLibrary().add(first).add(second).add(third)

        val edited = library.replace(second.renamed("Second, edited"))

        assertEquals(
            listOf(first.id, second.id, third.id),
            edited.versions.map { it.id },
            "an edit must stay in its slot instead of moving to the end",
        )
        assertEquals(
            "Second, edited",
            edited.find(second.id)!!.name,
            "the edit must be the saved content",
        )
    }

    @Test
    fun addingAVersionWithAnIdentityAlreadySavedIsRefused() {
        val saved = structured(name = "Saved")
        val library = VersionLibrary().add(saved)

        val error = assertFailsWith<IllegalArgumentException> { library.add(saved) }
        assertTrue(
            error.message!!.contains("already saved"),
            "the same identity twice makes find-by-id ambiguous, got: ${error.message}",
        )
    }

    // ---------------------------------------------------------------- broken assets

    @Test
    fun aMissingAssetIsReportedRatherThanSkipped() {
        // Playing the three parts that remain and calling it the saved version hides which part
        // went missing, and the user never finds out.
        val version = structured()
        val present = setOf(mainAsset, outro0Asset)

        assertEquals(
            listOf(preAsset, bridgeAsset),
            version.missing(present),
            "missing assets must be reported in binding order",
        )
        assertTrue(!version.isPlayable(present), "a version with a missing part is not playable")
        assertTrue(
            version.isPlayable(version.assets.toSet()),
            "a version with every asset present is playable",
        )
    }

    @Test
    fun theLibraryReportsEveryVersionThatCannotBePlayed() {
        val intact = linear("Intact", otherTrackAsset)
        val broken = structured(name = "Broken")
        val library = VersionLibrary().add(intact).add(broken)

        val everything = structuredAssets.toSet() + otherTrackAsset
        assertEquals(
            emptyList<VersionLibrary.Broken>(),
            library.broken(everything),
            "a library whose assets are all present has nothing broken",
        )

        val partial = library.broken(setOf(preAsset, mainAsset, otherTrackAsset))
        assertEquals(1, partial.size, "only the version with missing assets may be reported")
        assertEquals(broken.id, partial.single().id, "the report must name the version that cannot play")
        assertEquals(broken.name, partial.single().name, "the report must name it as the user sees it")
        assertEquals(
            listOf(bridgeAsset, outro0Asset),
            partial.single().missing,
            "the report must list every asset that is gone",
        )
    }

    // ---------------------------------------------------------------- linear vs structured

    @Test
    fun aLinearVersionIsDistinguishableFromAStructuredOne() {
        // A linear version has no transitions; offering it the flow's sync-point controls invites
        // a change that cannot do anything.
        val one = ListeningVersion.linear(VersionId.fresh(), "One file", otherTrackAsset)
        val many = structured()

        assertTrue(one.structure is Structure.Linear, "a single-asset version must be linear in the type")
        assertEquals(Structure.Structured, many.structure, "a multi-asset version must be structured")
        assertTrue(one.isLinear, "the linear case must be visible without inspecting the assets")
        assertTrue(!many.isLinear, "a multi-part arrangement is not linear")
        assertEquals(1, one.sections.size, "a linear version plays exactly one section")
        assertEquals(main, one.sections.single(), "the single section of a linear version is Main")
    }

    @Test
    fun aLinearVersionRefusesASecondSection() {
        // One asset has no second segment: the extra section would be requested and never sound.
        val one = ListeningVersion.linear(VersionId.fresh(), "One file", otherTrackAsset)
        val error = assertFailsWith<IllegalArgumentException> {
            one.copy(sections = listOf(main, outro0), repeats = mapOf(main to 1, outro0 to 1))
        }
        assertTrue(
            error.message!!.contains("one section"),
            "a one-asset version must refuse a second section, got: ${error.message}",
        )
    }

    @Test
    fun provenanceSurvivesEveryEdit() {
        // The timings are a choice, not a measurement; an edit that dropped the marker would
        // present this app's guess as the game's behaviour.
        val verified = structured(name = "Verified", provenance = StageFlow.Provenance.VERIFIED)
        val edits = listOf(
            verified.renamed("Renamed"),
            verified.duplicated("Duplicated"),
            verified.withSectionOrder(listOf(main, pre, outro0, bridge)),
            verified.withRepeatCount(bridge, 2),
            verified.withTiming(verified.timing.copy(outroEntry = MusicGraphBuilder.SyncPoint.NextBeat)),
            verified.snapshot().restore(),
        )

        edits.forEach { edited ->
            assertEquals(
                StageFlow.Provenance.VERIFIED,
                edited.provenance,
                "provenance must survive every edit, lost by: ${edited.name}",
            )
        }
        assertEquals(
            StageFlow.Provenance.PROVISIONAL,
            linear().provenance,
            "a version built without evidence must default to provisional, never verified",
        )
    }

    // ---------------------------------------------------------------- validation

    @Test
    fun aBlankNameIsRefused() {
        // An unnamed version cannot be picked from a list or told apart from its neighbour.
        val empty = assertFailsWith<IllegalArgumentException> {
            ListeningVersion.linear(VersionId.fresh(), "", otherTrackAsset)
        }
        assertTrue(empty.message!!.contains("name"), "an empty name must be refused")

        assertFailsWith<IllegalArgumentException> {
            ListeningVersion.linear(VersionId.fresh(), "   ", otherTrackAsset)
        }
        assertFailsWith<IllegalArgumentException> { VersionId(" ") }
    }

    @Test
    fun aVersionWithNoAssetsOrARepeatedAssetIsRefused() {
        // No assets points at nothing and plays silence as if it were the track; a repeated digest
        // is bound twice and the second copy shadows the first.
        val none = assertFailsWith<IllegalArgumentException> {
            ListeningVersion(
                id = VersionId.fresh(),
                name = "Nothing",
                primary = mainAsset,
                assets = emptyList(),
                sections = listOf(main),
                repeats = mapOf(main to 1),
                timing = StageFlow.DEFAULT_TIMING.copy(mainRepeats = 1),
                provenance = StageFlow.Provenance.PROVISIONAL,
            )
        }
        assertTrue(none.message!!.contains("at least one asset"), "zero assets must be refused")

        val repeated = assertFailsWith<IllegalArgumentException> {
            structured(assets = listOf(preAsset, mainAsset, mainAsset), primary = mainAsset)
        }
        assertTrue(
            repeated.message!!.contains("must not repeat"),
            "a duplicate asset id must be refused",
        )
    }

    @Test
    fun aRepeatedOutroInTheSectionOrderIsRefused() {
        // Outro00 and Outro01 are different endings; an order that lists the same one twice names
        // one segment twice and silently doubles its repeats.
        val bothEndings = structured(
            assets = listOf(preAsset, mainAsset, outro0Asset, outro1Asset),
            sections = listOf(pre, main, outro0, outro1),
            repeats = mapOf(pre to 1, main to 2, outro0 to 1, outro1 to 1),
        )
        assertEquals(4, bothEndings.sections.size, "two different outros must be allowed")

        val error = assertFailsWith<IllegalArgumentException> {
            bothEndings.copy(sections = listOf(pre, main, outro0, outro0))
        }
        assertTrue(
            error.message!!.contains("twice"),
            "the same outro number twice in the order must be refused, got: ${error.message}",
        )
    }

    @Test
    fun aRepeatCountBelowOneIsRefused() {
        // Zero would drop the section from the listen while the order still lists it.
        val base = structured()
        val error = assertFailsWith<IllegalArgumentException> {
            base.copy(repeats = base.repeats + (bridge to 0))
        }
        assertTrue(
            error.message!!.contains("start at 1"),
            "a repeat count below 1 must be refused, got: ${error.message}",
        )
        assertEquals(
            2,
            base.withRepeatCount(main, 2).repeats[main],
            "a repeat count of 1 or more must be accepted",
        )
    }

    @Test
    fun aRepeatCountMissingForASectionIsRefused() {
        // Defaulting a missing count to 1 would pick a number nobody chose.
        val base = structured()
        val error = assertFailsWith<IllegalArgumentException> {
            base.copy(repeats = base.repeats - bridge)
        }
        assertTrue(
            error.message!!.contains("no repeat count"),
            "a section without a repeat count must be refused, got: ${error.message}",
        )
    }

    @Test
    fun aNegativeFadeIsRefused() {
        // A negative length would run the fade backwards.
        val base = structured()
        val out = assertFailsWith<IllegalArgumentException> {
            base.copy(timing = base.timing.copy(bridgeFadeOutBeats = -0.25))
        }
        assertTrue(out.message!!.contains("cannot be negative"), "a negative fade-out must be refused")

        assertFailsWith<IllegalArgumentException> {
            base.copy(timing = base.timing.copy(bridgeFadeInBeats = -1.0))
        }
        assertEquals(
            0.5,
            base.withTiming(base.timing.copy(bridgeFadeInBeats = 0.5)).timing.bridgeFadeInBeats,
            0.0001,
            "a non-negative fade must be accepted",
        )
    }

    @Test
    fun aReorderThatChangesTheSectionsIsRefused() {
        // Dropping a section here shortens the listen; adding one asks for a segment the track may
        // not have. Both are edits, not reorders.
        val base = structured()
        val error = assertFailsWith<IllegalArgumentException> {
            base.withSectionOrder(listOf(pre, main))
        }
        assertTrue(
            error.message!!.contains("exactly the sections"),
            "a reorder must be a permutation, got: ${error.message}",
        )
    }
}
