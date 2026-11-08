package com.mylittleblover.mkwplayer.versions

import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import java.util.UUID

/**
 * A name for one saved listening version.
 *
 * Allocated when the version is first saved and never derived from what the version contains.
 * Deriving it -- from the content, or from a hash of the display name -- looks harmless and breaks
 * on the first rename: the id would change with the name, every snapshot and every selection that
 * referred to it would stop resolving, and a "duplicate" would go on sharing the original's
 * identity until someone happened to edit it.
 *
 * The value is opaque on purpose. Anything that parses meaning out of an id (a counter, a track
 * prefix) becomes a second place that has to be kept in step with the version itself.
 */
internal data class VersionId(val value: String) {

    init {
        require(value.isNotBlank()) {
            "a version id must not be blank: an unnamed id cannot be saved, found or removed"
        }
    }

    /** Prints as the token alone, so a rejection message names the version and not the wrapper. */
    override fun toString(): String = value

    companion object {
        /**
         * A fresh token for a version nobody has saved before.
         *
         * Random rather than sequential because two screens can create versions independently of
         * each other; a sequence owned by one of them would hand out the same number twice, and
         * the collision would surface later, in the library, as an unreadable failure.
         */
        fun fresh(): VersionId = VersionId(UUID.randomUUID().toString())
    }
}

/**
 * One section in the order the user wants to hear it.
 *
 * A section is a role plus, for outros, which ending. It is deliberately **not** an asset index:
 * the same role can be filled by a different file after the user re-picks a folder, and a saved
 * version that remembered a position in an old pick would play whichever file now sits at that
 * position.
 */
internal data class Section(
    val role: StageFlow.Role,
    /** Which ending, for [StageFlow.Role.Outro]. `null` for every other role. */
    val outroNumber: Int? = null,
) {

    init {
        val isOutro = role == StageFlow.Role.Outro
        require(isOutro == (outroNumber != null)) {
            if (isOutro) {
                "an outro section must carry its number: without one Outro00 and Outro01 compare " +
                    "equal, and one of the endings silently disappears from the order"
            } else {
                "role $role is not an outro, so it cannot carry outro number $outroNumber"
            }
        }
        require(outroNumber == null || outroNumber >= 0) {
            "outro numbers are file suffixes starting at 0, got $outroNumber"
        }
    }

    /** `Main`, or `Outro02` for endings, so two sections never look alike in a list. */
    val label: String get() = outroNumber?.let { "Outro%02d".format(it) } ?: role.name
}

/**
 * Whether a version is one continuous file or several sections switched at sync points.
 *
 * A sealed type rather than a boolean because the two cases have to stay distinguishable when
 * both sit in the same list: a linear version has nothing to transition to, so offering it the
 * "prelude exits on ExitCue" controls of a multi-part flow invites the user to change a setting
 * that cannot do anything.
 *
 * It is *derived* from the asset count rather than stored as a flag a caller passes, because a
 * flag can disagree with the content it labels. A five-part arrangement marked `Linear` would
 * hide four assets behind a single-file player, and nothing in the type would catch it.
 */
internal sealed interface Structure {

    /** Exactly one asset and one Main section: an ordinary, single-file track. */
    data object Linear : Structure

    /** More than one asset, so at least one transition is possible. */
    data object Structured : Structure
}

/**
 * Where a version's current content came from.
 *
 * [RESTORED] is not a synonym for [AUTHORED]. Rolling a version back to a snapshot and then
 * showing it as the user's latest choice would present numbers the user had already discarded as
 * a decision they made; the marker is what lets a screen say "restored" instead.
 */
internal enum class Origin {
    AUTHORED,
    RESTORED,
}

/**
 * A version as it was at one moment, kept so it can be brought back.
 *
 * Because [ListeningVersion] is immutable, capturing costs nothing and cannot be half-done: there
 * is no deep copy to get wrong, and no later edit can reach back into the snapshot.
 *
 * [reason] is for display only ("before trying another order"). A restore never consults it: a
 * restore whose result depended on its label would return different content under the same label,
 * which is exactly what a snapshot exists to rule out.
 */
internal data class Snapshot(
    val captured: ListeningVersion,
    val reason: String? = null,
) {

    init {
        require(reason == null || reason.isNotBlank()) {
            "a blank snapshot reason explains nothing; pass null when there is nothing to say"
        }
    }

    /**
     * The captured content, marked as restored.
     *
     * The identity is the captured version's, because a restore puts the same saved slot back.
     * Giving it a new identity would turn "undo my edit" into "save a second copy", and the
     * library would then hold the user's one version twice.
     */
    fun restore(): ListeningVersion = captured.copy(origin = Origin.RESTORED)
}

/**
 * One saved way of playing one track: which assets, which section order, which timing.
 *
 * The parts and the measured numbers live in [StageFlow]; this type holds only the choices a user
 * makes about them. Keeping those apart is what lets someone re-edit an arrangement without
 * touching measurements, and lets a snapshot restore the arrangement without pretending the
 * measurements moved.
 *
 * Assets are referenced by [AudioAssetId] and by nothing else. A path or a cache file name would
 * be the obvious key and is wrong twice over: a cache name changes between installs, and a path
 * can name a *different* file after the user moves or replaces one. Either mistake ends with a
 * saved version playing audio the user did not choose, under the name of the version that did.
 * When a digest is absent the version is reported as broken -- see [missing] -- and never trimmed
 * to the assets that happen to exist, because the three parts that are left are not the
 * arrangement that was saved.
 *
 * Every edit returns a new value. A library holding values that an editor mutates in place cannot
 * tell "the user changed this" from "the list object changed under me", and the first bug that
 * produces is a saved version that silently differs from the one the user approved.
 *
 * [timing] is a choice, not a measurement, so [provenance] has no default: defaulting to
 * `VERIFIED` would claim game behaviour this project has no evidence for, and defaulting to
 * `PROVISIONAL` in the constructor would hide the decision at the one place it is actually made.
 */
internal data class ListeningVersion(
    val id: VersionId,
    /** Shown in the list. Unique per track, which [VersionLibrary] enforces. */
    val name: String,
    /** The asset that plays first. Also the track this version is grouped under. */
    val primary: AudioAssetId,
    /** Every asset this version needs, in binding order, primary included. */
    val assets: List<AudioAssetId>,
    /** The sections the user wants, in the order they want them. */
    val sections: List<Section>,
    /**
     * How many passes each section gets, keyed by section.
     *
     * Keyed by section rather than stored positionally beside it because the two are edited
     * independently: reorder by index, reuse the old index for the count, and the repeat has
     * moved from Main to Bridge with nothing in the type noticing. A missing entry is refused
     * below rather than defaulting to one, so a new section cannot arrive with a count nobody
     * chose.
     */
    val repeats: Map<Section, Int>,
    /** Sync points and fade lengths: this project's or the user's choices, never measurements. */
    val timing: StageFlow.Timing,
    /** Whether [timing] is this project's guess or backed by external evidence. */
    val provenance: StageFlow.Provenance,
    /** Authored here, or rolled back from a snapshot. */
    val origin: Origin = Origin.AUTHORED,
) {

    init {
        require(name.isNotBlank()) {
            "a listening version needs a name: an unnamed version cannot be picked from a list or " +
                "told apart from the entry next to it"
        }
        require(assets.isNotEmpty()) {
            "a version needs at least one asset; with none it points at nothing and would play " +
                "silence as if that were the track"
        }
        require(assets.size == assets.toSet().size) {
            "asset ids must not repeat: a duplicate would be bound twice, and the second copy " +
                "would shadow the first while the graph still reported success"
        }
        require(primary in assets) {
            "the primary asset must be one of the version's assets, or the first thing played " +
                "would be a file this version does not own"
        }
        require(sections.isNotEmpty()) {
            "a version needs at least one section to play"
        }

        // One entry per section. Two entries for the same ending would name one graph segment
        // twice: the order lists it twice and the repeats silently double.
        val repeated = sections.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(repeated.isEmpty()) {
            "the same section appears twice in the order: ${repeated.map { it.label }}; two " +
                "entries for one section double its repeats without saying so"
        }
        require(sections.any { it.role == StageFlow.Role.Main }) {
            "every version needs a Main section: without the main loop this is a fragment, not a " +
                "listen"
        }

        val missingRepeats = sections.filterNot { repeats.containsKey(it) }
        require(missingRepeats.isEmpty()) {
            "no repeat count for ${missingRepeats.map { it.label }}; a missing count would quietly " +
                "default to one behind the user's back"
        }
        val strayRepeats = repeats.keys.filterNot { it in sections }
        require(strayRepeats.isEmpty()) {
            "repeat counts for sections that are not in the order: " +
                "${strayRepeats.map { it.label }}; they would be applied to nothing"
        }
        val belowOne = repeats.filterValues { it < 1 }
        require(belowOne.isEmpty()) {
            "repeat counts start at 1, got $belowOne; 0 would drop the section from the listen " +
                "while the order still lists it"
        }

        require(timing.bridgeFadeOutBeats >= 0.0) {
            "a fade cannot be negative, got ${timing.bridgeFadeOutBeats} beats; a negative length " +
                "would run the fade backwards"
        }
        require(timing.bridgeFadeInBeats >= 0.0) {
            "a fade cannot be negative, got ${timing.bridgeFadeInBeats} beats; a negative length " +
                "would run the fade backwards"
        }

        // Two answers to "how many times does Main repeat" means whichever the player reads, the
        // screen shows the other. Requiring agreement is the only way to keep them from drifting.
        val mainRepeats = repeats[Section(StageFlow.Role.Main)]
        require(mainRepeats == timing.mainRepeats) {
            "the Main section repeats $mainRepeats times but the timing says " +
                "${timing.mainRepeats}; the list and the player would disagree about one listen"
        }

        if (assets.size == 1) {
            require(sections.size == 1 && sections[0].role == StageFlow.Role.Main) {
                "a one-asset version can only play one section, but the order lists " +
                    "${sections.map { it.label }}; the graph has no segment for the others, so " +
                    "they would be requested and never sound"
            }
        }
    }

    /** How this version is listened to; derived from the asset count, never asserted by a flag. */
    val structure: Structure
        get() = if (assets.size == 1) Structure.Linear else Structure.Structured

    /** True for an ordinary single-file track, which needs none of the flow machinery. */
    val isLinear: Boolean get() = assets.size == 1

    /** True when this content came back from a snapshot rather than being authored here. */
    val isRestorePoint: Boolean get() = origin == Origin.RESTORED

    /** The track this version belongs to, as [VersionLibrary] groups it. */
    val track: AudioAssetId get() = primary

    /** The form two display names are compared in, so `Main` and ` main ` cannot both exist. */
    val nameKey: String get() = normalisedName(name)

    /**
     * The same version under another name.
     *
     * Returns a new value and leaves this one untouched: callers that still hold the old value go
     * on seeing the old name, which is what a snapshot of it has to restore.
     */
    fun renamed(newName: String): ListeningVersion = copy(name = newName)

    /**
     * A second version with the same content and its own identity.
     *
     * A new display name is required rather than defaulted, because [VersionLibrary] refuses two
     * versions of one track sharing a name: a duplicate that quietly kept the name could be built
     * and then never saved, which reads as the app losing the copy.
     *
     * Reusing this version's id is refused rather than silently honoured. Two saved versions with
     * one id make find-by-id return whichever was stored first, so an edit applied through the
     * lookup can land on the wrong version.
     */
    fun duplicated(
        newName: String,
        newId: VersionId = VersionId.fresh(),
    ): ListeningVersion {
        require(newId != id) {
            "a duplicate needs a new identity; reusing $id would leave two saved versions that " +
                "find-by-id cannot tell apart"
        }
        return copy(id = newId, name = newName)
    }

    /**
     * The same version with its sections in another order.
     *
     * The new order must be a permutation of the current one. Dropping a section here would
     * silently shorten the listen, and adding one would ask the player for a segment the track
     * may not have; both are edits, not reorders, and belong where they can be checked against
     * the track's parts.
     */
    fun withSectionOrder(order: List<Section>): ListeningVersion {
        require(order.size == sections.size && order.toSet() == sections.toSet()) {
            "a reorder must contain exactly the sections this version already has: had " +
                "${sections.map { it.label }}, got ${order.map { it.label }}"
        }
        return copy(sections = order)
    }

    /**
     * The same version with one section's repeat count changed.
     *
     * Editing Main also moves `timing.mainRepeats`, because the constructor refuses the two
     * disagreeing and the alternative -- leaving a stale count in the timing -- would make the
     * saved player behave differently from the description it is played from.
     */
    fun withRepeatCount(section: Section, count: Int): ListeningVersion {
        require(section in sections) {
            "section ${section.label} is not in this version's order, so its repeat count would " +
                "apply to nothing"
        }
        val nextRepeats = repeats + (section to count)
        val nextTiming =
            if (section.role == StageFlow.Role.Main) timing.copy(mainRepeats = count) else timing
        return copy(repeats = nextRepeats, timing = nextTiming)
    }

    /**
     * The same version with different transition choices.
     *
     * A [StageFlow.Timing] whose `mainRepeats` disagrees with the Main section is refused by the
     * constructor below rather than quietly overwritten in either direction, so this method does
     * not decide which of the two the user meant.
     */
    fun withTiming(newTiming: StageFlow.Timing): ListeningVersion = copy(timing = newTiming)

    /**
     * Assets this version needs that are not in [present], in binding order.
     *
     * Reported rather than played around. Skipping a missing part would play a shorter
     * arrangement the user did not choose while still calling it their saved version, and there
     * is no later moment at which the user would find out which part vanished.
     */
    fun missing(present: Set<AudioAssetId>): List<AudioAssetId> = assets.filterNot { it in present }

    /** True only when every asset the version names is present. */
    fun isPlayable(present: Set<AudioAssetId>): Boolean = missing(present).isEmpty()

    /** Captures this value so it can be restored later. */
    fun snapshot(reason: String? = null): Snapshot = Snapshot(this, reason)

    companion object {

        /**
         * A version for an ordinary, single-file track.
         *
         * Most tracks are one asset and should not have to describe sections, sync points or
         * transitions to be saved. The result is a [Structure.Linear] version with one Main
         * section, which is what makes it distinguishable from a saved multi-part arrangement in
         * the type rather than only in the name.
         *
         * The timing is carried because the field is not nullable, but only its repeat count means
         * anything here: with nothing to transition to, the sync points cannot fire. They stay at
         * the project's provisional defaults instead of being tuned to look meaningful.
         *
         * [provenance] defaults to `PROVISIONAL` because that is the only value that cannot
         * overclaim; a caller with external evidence passes `VERIFIED` explicitly.
         */
        fun linear(
            id: VersionId,
            name: String,
            asset: AudioAssetId,
            repeats: Int = 1,
            provenance: StageFlow.Provenance = StageFlow.Provenance.PROVISIONAL,
        ): ListeningVersion {
            val main = Section(StageFlow.Role.Main)
            return ListeningVersion(
                id = id,
                name = name,
                primary = asset,
                assets = listOf(asset),
                sections = listOf(main),
                repeats = mapOf(main to repeats),
                timing = StageFlow.DEFAULT_TIMING.copy(mainRepeats = repeats),
                provenance = provenance,
            )
        }

        /**
         * The form two display names are compared in.
         *
         * Case and surrounding spaces are folded because those differences are invisible in a
         * list: two rows reading `Main mix` and ` main mix ` look like the same name twice, and a
         * user who saved both has no way to tell which is which.
         */
        fun normalisedName(name: String): String = name.trim().lowercase()
    }
}

/**
 * Every saved version, across every track.
 *
 * Immutable like [ListeningVersion]: each operation returns a new library, and the constructor
 * refuses a library that breaks an invariant. The rules live in this one place so no caller can
 * produce a valid-looking holder by assembling a list itself; validation that exists only on the
 * convenience path gets bypassed the first time someone builds the thing directly.
 *
 * Versions come back in the order they were saved, not sorted by name. The user established that
 * order by saving; a list that reshuffles between visits reads as the app forgetting something.
 *
 * A track is identified by its [ListeningVersion.primary] asset. A version cannot state a
 * catalogue kind, and inventing one would make the same file saved under two kinds look like two
 * tracks; the primary digest is the only identity a version can honestly offer.
 */
internal class VersionLibrary(initial: List<ListeningVersion> = emptyList()) {

    /** The saved versions, in the order they were added. */
    val versions: List<ListeningVersion> = initial.toList()

    init {
        val repeatedIds = versions.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(repeatedIds.isEmpty()) {
            "two versions share the identity $repeatedIds; find-by-id would return whichever was " +
                "stored first"
        }

        versions.groupBy { it.primary }.forEach { (track, ofTrack) ->
            val repeatedNames =
                ofTrack.groupingBy { it.nameKey }.eachCount().filterValues { it > 1 }.keys
            require(repeatedNames.isEmpty()) {
                "track ${track.short()} has two versions named $repeatedNames; one display name on " +
                    "one track is a row the user cannot tell from its neighbour"
            }
        }
    }

    /** True when the library holds nothing. */
    val isEmpty: Boolean get() = versions.isEmpty()

    /** How many versions are saved. */
    val size: Int get() = versions.size

    /**
     * Saves [version], appended so the newest is last.
     *
     * A name collision on the same track is refused here rather than resolved by renaming: two
     * versions of one track with one display name are indistinguishable in the list, so whichever
     * the user picks may not be the one they meant. The same name on a *different* track is fine,
     * because the track is what tells the two rows apart.
     */
    fun add(version: ListeningVersion): VersionLibrary {
        require(!hasNameCollision(version.name, version.primary, excluding = version.id)) {
            "'${version.name}' already names a version of track ${version.primary.short()}; two " +
                "versions of one track cannot share a display name"
        }
        require(versions.none { it.id == version.id }) {
            "a version with identity ${version.id} is already saved; adding it again would make " +
                "find-by-id ambiguous"
        }
        return VersionLibrary(versions + version)
    }

    /**
     * Drops the version with [id].
     *
     * An unknown id is refused rather than ignored: a remove that silently succeeds usually means
     * the caller holds an id that no longer exists, and hiding that turns a stale screen into a
     * mystery.
     */
    fun remove(id: VersionId): VersionLibrary {
        require(versions.any { it.id == id }) {
            "there is no version $id to remove"
        }
        return VersionLibrary(versions.filterNot { it.id == id })
    }

    /**
     * Puts an edited version back in the slot it came from.
     *
     * Position is preserved. Appending instead would move the entry to the end of the list every
     * time the user renamed it, which reads as a different library.
     */
    fun replace(version: ListeningVersion): VersionLibrary {
        val index = versions.indexOfFirst { it.id == version.id }
        require(index >= 0) {
            "there is no version ${version.id} to replace; a replace that quietly appended would " +
                "make an edit look like a new save"
        }
        val next = versions.toMutableList()
        next[index] = version
        return VersionLibrary(next)
    }

    /** The version with [id], or null when it is not saved. */
    fun find(id: VersionId): ListeningVersion? = versions.firstOrNull { it.id == id }

    /** Every version of the track whose primary asset is [track], in saved order. */
    fun forTrack(track: AudioAssetId): List<ListeningVersion> =
        versions.filter { it.primary == track }

    /** The tracks that have at least one saved version, in the order they first appear. */
    fun tracks(): List<AudioAssetId> = versions.map { it.primary }.distinct()

    /**
     * True when [name] is already used on [track], after [ListeningVersion.normalisedName].
     *
     * Exposed so a screen can warn while the user types; [add] enforces the same rule through the
     * same comparison, so the warning and the refusal cannot disagree.
     */
    fun hasNameCollision(
        name: String,
        track: AudioAssetId,
        excluding: VersionId? = null,
    ): Boolean {
        val key = ListeningVersion.normalisedName(name)
        return versions.any { it.primary == track && it.id != excluding && it.nameKey == key }
    }

    /**
     * Brings a snapshot back into the library.
     *
     * Replaces the same identity when it is still saved, and re-adds it when the user removed it
     * after taking the snapshot. Either way the restored content passes the same collision rules
     * as any other version, so restoring an old name that has since been taken is refused instead
     * of producing two identical-looking rows.
     */
    fun restore(snapshot: Snapshot): VersionLibrary {
        val restored = snapshot.restore()
        return if (versions.any { it.id == restored.id }) replace(restored) else add(restored)
    }

    /** One version that cannot be played as saved, and the assets that are missing. */
    data class Broken(
        val id: VersionId,
        val name: String,
        val track: AudioAssetId,
        val missing: List<AudioAssetId>,
    )

    /**
     * Every version that cannot be played with only [present] assets.
     *
     * This is the whole answer to "why can't I play this?". A caller that only checked each
     * track's primary asset would call a version fine while four of its five parts are gone, and
     * one that trusted the presence of the assets it happened to see would offer a listen that
     * stops at the first missing file.
     */
    fun broken(present: Set<AudioAssetId>): List<Broken> {
        val result = ArrayList<Broken>()
        for (version in versions) {
            val missing = version.missing(present)
            if (missing.isNotEmpty()) {
                result += Broken(version.id, version.name, version.primary, missing)
            }
        }
        return result
    }
}
