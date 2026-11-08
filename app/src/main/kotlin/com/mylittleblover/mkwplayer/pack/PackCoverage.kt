package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.library.Playability
import com.mylittleblover.mkwplayer.registry.AudioAssetId

/**
 * What the pack actually contains, what this build can actually play, and what the UI may say about
 * the difference.
 *
 * The manifest states coverage as a claim. Checking a claim by re-reading the claim is the mistake
 * this object exists to prevent, so every number here is *derived from the pack's own content* and
 * then compared to what the manifest declared. A pack that says "650 tracks, all playable" while
 * carrying 412 has its declaration reported as a mismatch rather than repeated to the user.
 *
 * Three quantities are kept apart because folding them together is how "100 %" gets printed for a
 * pack nobody can fully play:
 *
 * | Quantity | Meaning |
 * |---|---|
 * | the manifest's declared coverage | what the pack author claims |
 * | [Coverage.trackCount] / [Coverage.playableCount] | what the content supports and this build can decode |
 * | [Coverage.declaredPlayable] | the claim, kept only so a mismatch can be shown |
 *
 * And two different completenesses:
 *
 * | Property | Question | Governed by |
 * |---|---|---|
 * | [Coverage.isPackComplete] | is the file whole and internally consistent? | structural validation only |
 * | [Coverage.isComplete] | can this build play everything in it? | decoder capability too |
 *
 * A pack can be well-formed and still hold assets this build cannot decode. That is the normal case
 * here: the measured catalogue shows 253 of 650 entries in a codec (kind 2) this build does not
 * implement. Saying "the pack is complete" is true of the file; saying "the library is complete"
 * would be false about the build. The app must show both, with the counts.
 *
 * "Playable" means every asset of the logical track is decodable, not merely its primary. A flow
 * whose Pre is decodable while its Main is not would produce a track that starts and dies in the
 * middle, which is worse than one clearly marked unplayable.
 */
internal object PackCoverage {

    /**
     * A codec this pack uses that this build cannot decode, with how much it costs.
     *
     * [affectedTracks] counts logical tracks, because that is the unit the library lists and the cup
     * counts. Reporting raw asset counts instead would report a five-part stage as five broken
     * things and make the limitation look five times worse than it is.
     */
    data class UnsupportedCodec(
        val codecKind: Int,
        val affectedTracks: Int,
        /** One-line reason, shared with the existing per-entry playability verdict. */
        val reason: String,
    )

    /**
     * Why the Special Cup may not use this pack's scope, or null when it may.
     *
     * These are separate from validation failures because a pack can be perfectly installable and
     * still not be allowed to define cup eligibility: an unfrozen scope is a valid pack that has not
     * promised its list is final, and a partially decodable pack is valid but cannot require a stage
     * nobody can hear.
     */
    enum class CupBlocker {
        /** Eligibility may only come from a full pack; a delta does not state a whole soundtrack. */
        PACK_NOT_FULL,

        /** The pack states no unlock scope. */
        NO_UNLOCK_SCOPE,

        /** The scope exists but the pack has not frozen it, so its list is still provisional. */
        SCOPE_NOT_FROZEN,

        /** The scope names a stage the pack does not describe. */
        SCOPE_STAGE_UNKNOWN,

        /** The pack is not structurally complete, so its scope describes content it cannot back. */
        PACK_NOT_COMPLETE,

        /** A stage in the scope has a track this build cannot decode. */
        UNPLAYABLE_TRACKS,
    }

    /**
     * Everything the library and unlock screens need to say about one pack.
     *
     * [summary] is prose and must never be parsed back into numbers; the counts beside it are the
     * machine-readable form. A screen that split [summary] to recover a count would break the moment
     * the wording changed.
     */
    data class Coverage(
        /** How many logical tracks the pack's own content supports. */
        val trackCount: Int,
        /** How many of those this build can decode, in full. */
        val playableCount: Int,
        /** The playable count the manifest claimed, kept so a mismatch can be shown. */
        val declaredPlayable: Int,
        /** The total the manifest claimed. */
        val declaredTotal: Int,
        /** Codecs used by this pack that this build cannot decode. Empty when fully playable. */
        val unsupportedCodecs: List<UnsupportedCodec>,
        /** True when the manifest's own numbers describe the content it carries. */
        val declaredCoverageMatches: Boolean,
        /** True when the pack's structure is whole, independent of what this build can decode. */
        val isPackComplete: Boolean,
        /** True when this build can play every track the pack contains. */
        val isComplete: Boolean,
        /** One line, ready to show. */
        val summary: String,
        /** The line to show when [isComplete] is false; null when it is true. */
        val limitation: String?,
        /** Stage codes this pack contributes to the Special Cup, or empty when it may not. */
        val cupEligibleStages: Set<String>,
        /** Why [cupEligibleStages] is empty, or null when it is not. */
        val cupBlocker: CupBlocker?,
    ) {
        /** How many tracks this build cannot fully play. */
        val unplayableCount: Int get() = trackCount - playableCount

        /** True when the manifest claimed a count the content does not support. */
        val declarationIsWrong: Boolean get() = !declaredCoverageMatches

        /**
         * True when the cup may be offered from this pack.
         *
         * A single predicate rather than a nullable-blocker check at each call site, so no screen can
         * decide for itself that a partially decodable pack is "close enough".
         */
        val canDefineCup: Boolean get() = cupBlocker == null
    }

    /**
     * Computes coverage.
     *
     * @param manifest the parsed manifest. Passing [validation] is preferred: [isPackComplete] then
     *   reflects every rule rather than the subset this object can re-derive alone. Without it, an
     *   unvalidated manifest gets a weaker structural check -- still never a "complete" verdict for
     *   a pack whose own numbers contradict its content.
     * @param validation the report previously obtained from [PackValidation.validate].
     */
    fun of(
        manifest: PackManifest,
        validation: PackValidation.Report? = null,
    ): Coverage {
        val decodable = manifest.assets
            .filter { isDecodable(it.codecKind) }
            .map { it.digest }
            .toSet()

        // A track is playable only when every asset it needs is. The primary alone would be enough
        // for a single-file track and wrong for a flow: a flow that loses its Main is not "mostly
        // playable".
        val playableTracks = manifest.tracks.filter { track ->
            track.parts.isNotEmpty() && track.parts.all { it.asset in decodable }
        }
        val playableKeys = playableTracks.map { it.key }.toSet()

        val unsupported = unsupportedCodecs(manifest)
        val declaredMatches = declaredCoverageMatches(manifest)

        val structurallySound = if (validation != null) {
            validation.isInstallable
        } else {
            reDerivableProblems(manifest).isEmpty()
        }
        val packComplete = structurallySound && declaredMatches

        val blocker = cupBlocker(
            manifest = manifest,
            playableKeys = playableKeys,
            packComplete = packComplete,
        )

        return Coverage(
            trackCount = manifest.tracks.size,
            playableCount = playableTracks.size,
            declaredPlayable = manifest.coverage.playableLogicalTracks,
            declaredTotal = manifest.coverage.totalLogicalTracks,
            unsupportedCodecs = unsupported,
            declaredCoverageMatches = declaredMatches,
            isPackComplete = packComplete,
            isComplete = packComplete && playableTracks.size == manifest.tracks.size,
            summary = summaryOf(
                trackCount = manifest.tracks.size,
                playableCount = playableTracks.size,
                structurallySound = structurallySound,
                declaredMatches = declaredMatches,
            ),
            limitation = limitationOf(
                trackCount = manifest.tracks.size,
                playableCount = playableTracks.size,
                unsupported = unsupported,
                structurallySound = structurallySound,
            ),
            cupEligibleStages = if (blocker == null) manifest.unlock.stages.toSet() else emptySet(),
            cupBlocker = blocker,
        )
    }

    /**
     * True when this build can decode [codecKind].
     *
     * Delegates to [Playability] instead of comparing to 1 here. That object is the one place the
     * decoder's capability is written down, and a second copy would eventually disagree with the
     * first -- which is how a track gets listed as playable and produces noise.
     */
    fun isDecodable(codecKind: Int): Boolean = Playability.evaluate(codecKind).playable

    /**
     * Which codecs in the pack this build cannot decode, with the number of tracks each affects.
     *
     * A track is counted once per codec even if it uses that codec twice, and a track using two
     * unsupported codecs appears under both: the counts answer "what would I gain by supporting this
     * codec", not "how many tracks are unplayable", which is [Coverage.unplayableCount].
     */
    private fun unsupportedCodecs(manifest: PackManifest): List<UnsupportedCodec> {
        val codecsUsed = manifest.assets.map { it.codecKind }.distinct()
        val reasons = codecsUsed
            .filterNot { isDecodable(it) }
            .associateWith { codec ->
                Playability.evaluate(codec).reason ?: "This build does not decode this codec."
            }
        if (reasons.isEmpty()) return emptyList()

        return reasons.map { (codec, reason) ->
            val affected = manifest.tracks.count { track ->
                track.parts.any { part ->
                    manifest.assets.any { it.digest == part.asset && it.codecKind == codec }
                }
            }
            UnsupportedCodec(codecKind = codec, affectedTracks = affected, reason = reason)
        }.sortedBy { it.codecKind }
    }

    /**
     * The tracks this pack's own definition calls playable: every part present in the asset list and
     * non-empty.
     *
     * Deliberately *not* the decodability test. The manifest cannot know what a given build decodes,
     * and letting it try would put the app's capability inside a distributable document. The pack
     * claims "I contain all of this, and none of it is empty"; the build then decides what it can
     * play. That is the only split that stays true when a decoder is added.
     */
    private fun tracksWithCompleteAssets(manifest: PackManifest): List<PackManifest.Track> {
        val present = manifest.assets
            .filter { it.byteSize > 0 }
            .map { it.digest }
            .toSet()
        return manifest.tracks.filter { track ->
            track.parts.isNotEmpty() && track.parts.all { it.asset in present }
        }
    }

    /**
     * Whether the manifest's declared numbers describe the content it carries.
     *
     * Two comparisons, both necessary:
     *
     * - `totalLogicalTracks` must equal the number of tracks actually listed. A total larger than
     *   the list means tracks were lost in packing; a total smaller means the list carries rows the
     *   count does not cover.
     * - `playableLogicalTracks` must equal the number of tracks whose assets the pack really holds.
     *   This is the claim that would otherwise let a pack advertise 650 playable tracks while
     *   holding 412.
     */
    private fun declaredCoverageMatches(manifest: PackManifest): Boolean =
        manifest.coverage.totalLogicalTracks == manifest.tracks.size &&
            manifest.coverage.playableLogicalTracks == tracksWithCompleteAssets(manifest).size

    /**
     * Structural defects this object can establish without the full validation report.
     *
     * Only used when the caller passes no report, and deliberately narrow: it checks the facts that
     * would make a "complete" verdict actively misleading -- no tracks at all, a track whose assets
     * are absent, a stage whose tracks are absent. It is not a substitute for [PackValidation]; it
     * exists so a caller that forgets to validate does not get a confident verdict out of an empty
     * check.
     */
    private fun reDerivableProblems(manifest: PackManifest): List<String> {
        val problems = ArrayList<String>()
        val knownAssets: Set<AudioAssetId> = manifest.assets.map { it.digest }.toSet()
        val knownTracks = manifest.tracks.map { it.key }.toSet()

        if (manifest.tracks.isEmpty()) problems += "the pack carries no logical tracks"
        manifest.tracks.forEach { track ->
            if (track.parts.isEmpty()) {
                problems += "track '${track.key}' has no parts"
            } else if (track.parts.any { it.asset !in knownAssets }) {
                problems += "track '${track.key}' references an asset that is not in the pack"
            }
            if (track.timing != null && manifest.provenanceFor(track.key, TIMING_FIELD) == null) {
                problems += "track '${track.key}' declares timings without provenance"
            }
        }
        manifest.stages.forEach { stage ->
            if (stage.tracks.any { it !in knownTracks }) {
                problems += "stage '${stage.code}' references a track that is not in the pack"
            }
        }
        if (manifest.provenance.isEmpty()) problems += "the pack declares no provenance"
        return problems
    }

    /**
     * Whether this pack may define the Special Cup's eligible stages.
     *
     * Every clause is a distinct way the cup could open without the user having heard the soundtrack:
     *
     * - a delta does not state a whole soundtrack, so its scope is a fragment;
     * - an empty scope would make "all complete" true over nothing;
     * - an unfrozen scope is provisional by its own admission, and a provisional list that later
     *   grows turns a completed cup into an incomplete one;
     * - a scope naming an unknown stage can never be satisfied;
     * - an incomplete pack cannot back its own scope;
     * - a stage whose tracks cannot all be decoded can never be completed, so requiring it would
     *   make the cup unreachable -- and quietly dropping it would open the cup for a library the
     *   user cannot hear.
     *
     * The order matters for the message shown: "the pack is not final yet" and "this build cannot
     * play it" are different facts, and the user's next step differs.
     */
    private fun cupBlocker(
        manifest: PackManifest,
        playableKeys: Set<String>,
        packComplete: Boolean,
    ): CupBlocker? {
        if (manifest.isDelta) return CupBlocker.PACK_NOT_FULL
        if (manifest.unlock.stages.isEmpty()) return CupBlocker.NO_UNLOCK_SCOPE
        if (!manifest.unlock.frozen) return CupBlocker.SCOPE_NOT_FROZEN

        val stagesByCode = manifest.stages.associateBy { it.code }
        if (manifest.unlock.stages.any { it !in stagesByCode }) return CupBlocker.SCOPE_STAGE_UNKNOWN
        if (!packComplete) return CupBlocker.PACK_NOT_COMPLETE

        val scopeTracks = manifest.unlock.stages
            .distinct()
            .flatMap { code -> stagesByCode.getValue(code).tracks }
            .distinct()
        if (scopeTracks.any { it !in playableKeys }) return CupBlocker.UNPLAYABLE_TRACKS

        return null
    }

    /**
     * The honest headline.
     *
     * It always states both numbers, even when they are equal: "2 of 2 tracks playable in this
     * build" is a claim a reader can check, while "100 %" is one they cannot. There is deliberately
     * no percentage anywhere in this type -- there is nothing to round up, so no code path can print
     * 100 % for a pack with an undecodable asset.
     */
    private fun summaryOf(
        trackCount: Int,
        playableCount: Int,
        structurallySound: Boolean,
        declaredMatches: Boolean,
    ): String {
        if (trackCount == 0) {
            return "The pack lists no tracks, so there is nothing to play and nothing to count."
        }
        val head = when {
            !structurallySound -> "The pack is not internally consistent: "
            !declaredMatches -> "The pack's own numbers do not match its contents: "
            else -> "The pack is complete: "
        }
        val tracks = if (trackCount == 1) "track" else "tracks"
        return head + "$playableCount of $trackCount $tracks playable in this build."
    }

    /**
     * The limitation line, or null when there is none.
     *
     * Null rather than an empty string, so a screen that prints it cannot print a blank line and
     * imply a limitation was checked and dismissed.
     */
    private fun limitationOf(
        trackCount: Int,
        playableCount: Int,
        unsupported: List<UnsupportedCodec>,
        structurallySound: Boolean,
    ): String? {
        if (trackCount == 0) return null
        if (playableCount == trackCount) {
            return if (structurallySound) null else {
                "The counts above cannot be trusted: the pack is not internally consistent."
            }
        }

        val codecList = unsupported.joinToString(", ") { codec ->
            val tracks = if (codec.affectedTracks == 1) "track" else "tracks"
            "codec ${codec.codecKind} (${codec.affectedTracks} $tracks)"
        }
        val codecs = if (codecList.isEmpty()) "an unsupported codec" else codecList
        val unplayable = trackCount - playableCount
        val noun = if (unplayable == 1) "track" else "tracks"
        return "$playableCount of $trackCount tracks play in this build. $unplayable $noun cannot, " +
            "because this build does not decode $codecs. That is a limit of this app, not of the pack."
    }

    /**
     * The wording the app must use when the pack is whole but not fully playable by this build.
     *
     * Kept as a named function so the two claims cannot be merged into one sentence by accident: a
     * single line saying "complete" over a partially playable pack is the exact false claim this
     * object exists to prevent. Returns null when the distinction does not apply, so a caller that
     * prints it unconditionally prints nothing rather than a contradiction.
     */
    fun completeButNotPlayableLine(coverage: Coverage): String? {
        if (!coverage.isPackComplete || coverage.isComplete) return null
        return "The pack is complete. This build can play ${coverage.playableCount} of its " +
            "${coverage.trackCount} tracks."
    }

    /** The one line to show for a pack: fully playable, complete but limited, or neither. */
    fun completeLine(coverage: Coverage): String = when {
        coverage.isComplete ->
            "The pack and this build agree: all ${coverage.trackCount} tracks play."

        coverage.isPackComplete ->
            completeButNotPlayableLine(coverage)
                ?: "The pack is complete, but this build cannot play all of it."

        else -> "The pack is not complete, so it must not be imported; see the problem list."
    }

    /** Field name a timing's provenance record must carry, as in the format. */
    private const val TIMING_FIELD = PackValidation.TIMING_FIELD
}
