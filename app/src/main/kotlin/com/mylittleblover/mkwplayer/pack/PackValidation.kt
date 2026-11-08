package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.registry.LogicalTrack

/**
 * The rules a parsed manifest must satisfy before it may be installed, and every place it breaks
 * them.
 *
 * Validation is a separate step from parsing on purpose. Parsing answers "can this document be read
 * as a v1 manifest at all?", and a document that cannot is refused immediately. Validation answers
 * "is what it says internally consistent?", and there may be many answers at once.
 *
 * **Every failure is reported, never only the first.** A pack author fixes one defect, re-exports a
 * multi-gigabyte archive, imports it, and discovers the next defect; doing that eleven times is how
 * a format gets abandoned. The gate can supply the whole list in one pass because validation is
 * cheap: it reads the manifest and never touches the audio.
 *
 * The division of labour between this object and the parser is deliberate:
 *
 * | Question | Answered by |
 * |---|---|
 * | Is `digest` 64 lowercase hex? | parser (an [AudioAssetId][com.mylittleblover.mkwplayer.registry.AudioAssetId] cannot be built otherwise) |
 * | Is `formatVersion` one we implement? | parser (reading on would be half-reading) |
 * | Does `primary` appear in the track's own parts? | this object |
 * | Does a stage name a track that is not in the pack? | this object |
 *
 * A rule that needs the *relationship* between two rows cannot live in the parser, because the
 * parser reads rows in isolation. That is exactly the class of mistake this file exists to catch: a
 * primary asset that is not in its own asset list looks fine on both rows and would make the track
 * open a file it does not own.
 */
internal object PackValidation {

    /**
     * A stable identifier for one rule.
     *
     * Codes are stable and messages are for people. A UI can group or count by code without parsing
     * the prose, and a test can assert the rule that failed rather than a sentence that might be
     * reworded. The names match `docs/PACK-FORMAT.md` section 14, which is the list a pack author
     * works from.
     */
    enum class Code {
        /** The pack carries no logical tracks at all. */
        NO_TRACKS,

        /** An asset declares a negative byte size, which cannot describe a file. */
        ASSET_SIZE_NEGATIVE,

        /** An asset is zero bytes. A sound file with no bytes is not a short track, it is absent. */
        ASSET_EMPTY,

        /** Two assets share a digest, two tracks share a key, or two stages share a code. */
        DUPLICATE_IDENTITY,

        /** A track key is not the derivation of its own kind and primary digest. */
        TRACK_KEY_INCONSISTENT,

        /** A track's primary asset is not among its own parts. */
        PRIMARY_NOT_IN_PARTS,

        /** A track references an asset the pack does not contain. */
        TRACK_ASSET_MISSING,

        /** A stage references a logical track the pack does not contain. */
        STAGE_TRACK_MISSING,

        /** A flow has no Main part, so it cannot be assembled. */
        FLOW_WITHOUT_MAIN,

        /** A track that is not a flow carries several parts, which would be silently dropped. */
        NON_FLOW_WITH_MANY_PARTS,

        /** An outro is unnumbered, a non-outro carries a number, or two outros share one. */
        OUTRO_NUMBER_INVALID,

        /** A flow declares no transition timings. */
        FLOW_TIMING_MISSING,

        /** A delta names no base pack. */
        DELTA_WITHOUT_BASE,

        /** A full pack names a base, which makes it a delta in everything but the label. */
        FULL_WITH_BASE,

        /** A delta's own version does not come after its base's. */
        DELTA_NOT_FORWARD,

        /** The pack states no provenance at all. */
        PROVENANCE_MISSING,

        /** A track declares timings with no provenance record covering them. */
        TIMING_WITHOUT_PROVENANCE,

        /** The unlock scope lists no stages. */
        UNLOCK_SCOPE_EMPTY,

        /** The unlock scope names a stage the pack does not describe. */
        UNLOCK_STAGE_UNKNOWN,

        /** An unlock stage has no tracks, or names tracks whose assets are not all in the pack. */
        UNLOCK_STAGE_INCOMPLETE,
    }

    /**
     * One broken rule.
     *
     * [subject] names the row, so a message list can be acted on without reading the prose: a track
     * key, an asset digest, a stage code, or the pack id for a pack-level rule.
     */
    data class Failure(val code: Code, val subject: String, val message: String)

    /**
     * The complete result of validating one manifest.
     *
     * [isInstallable] is the single predicate an installer should branch on. It is true only when
     * there are no failures, never when validation was skipped: a caller that forgets to validate
     * gets a report it has to construct, which is harder than forgetting a boolean check.
     */
    data class Report(val failures: List<Failure>) {

        init {
            // A Report is only ever produced by validate(), which cannot omit a failed rule silently.
            // An empty list is the success case and is what every caller checks.
            require(failures.none { it.message.isBlank() }) {
                "a validation failure without a message gives the user nothing to act on"
            }
        }

        val isInstallable: Boolean get() = failures.isEmpty()

        /** How many distinct rules failed. */
        val ruleCount: Int get() = failures.map { it.code }.distinct().size

        /** Every failure carrying [code], in the order they were found. */
        fun forCode(code: Code): List<Failure> = failures.filter { it.code == code }

        /** True when [code] is among the failures. */
        fun has(code: Code): Boolean = failures.any { it.code == code }

        /**
         * The complete list, ready to show.
         *
         * One message per failure, prefixed by nothing: a bulleted list of sentences is more useful
         * than a numbered one, because the user's next step is to fix all of them, not to work
         * through a sequence.
         */
        fun messages(): List<String> = failures.map { it.message }

        /**
         * One line for a header, and the whole list when it is short.
         *
         * The count is always stated, so a user who sees three lines knows they are looking at
         * three of them and not at the first three of an unknown number.
         */
        fun summary(): String = when (failures.size) {
            0 -> "The pack is internally consistent."
            1 -> "The pack has 1 problem: ${failures[0].message}"
            else -> "The pack has ${failures.size} problems: " +
                failures.joinToString(separator = " ") { it.message }
        }
    }

    /**
     * Validates [manifest].
     *
     * Never throws and never short-circuits: a manifest with eleven broken rules produces eleven
     * failures. The order of the checks is the order the failures are reported in, which groups the
     * asset rules, then the track rules, then the pack-kind rules, then the unlock contract.
     *
     * @param manifest a value produced by [PackManifest.parse]. Rules the parser already enforces
     *   (digest shape, known vocabulary, playable count above total) are not repeated here.
     */
    fun validate(manifest: PackManifest): Report {
        val failures = ArrayList<Failure>()

        validatePackKind(manifest, failures)
        validateAssets(manifest, failures)
        validateTracks(manifest, failures)
        validateStages(manifest, failures)
        validateProvenance(manifest, failures)
        validateUnlock(manifest, failures)

        return Report(failures)
    }

    /** Convenience for callers that only need the verdict. */
    fun isInstallable(manifest: PackManifest): Boolean = validate(manifest).isInstallable

    // ------------------------------------------------------------------ pack kind and delta base

    /**
     * FULL and DELTA rules.
     *
     * The pair is checked together because the mistake is the *mismatch*, not either field alone. A
     * full pack that carries a base would be installed as a complete library and silently drop
     * everything the named base held; a delta with no base cannot be applied anywhere at all.
     *
     * A delta that does not move forward is refused too: applying an older delta over a newer base
     * would resurrect content the base had already replaced, and the result would be a pack nobody
     * authored.
     */
    private fun validatePackKind(manifest: PackManifest, into: MutableList<Failure>) {
        val hasBaseId = manifest.basePackId != null
        val hasBaseVersion = manifest.basePackVersion != null

        when {
            manifest.isDelta && (!hasBaseId || !hasBaseVersion) -> {
                val missing = buildList {
                    if (!hasBaseId) add("basePackId")
                    if (!hasBaseVersion) add("basePackVersion")
                }
                into += Failure(
                    code = Code.DELTA_WITHOUT_BASE,
                    subject = manifest.packId,
                    message = "This is a DELTA pack but it does not name ${missing.joinToString(" and ")}. " +
                        "A delta can only be applied to the pack it was built against; importing it " +
                        "without a base would mix two products together.",
                )
            }

            !manifest.isDelta && (hasBaseId || hasBaseVersion) -> {
                into += Failure(
                    code = Code.FULL_WITH_BASE,
                    subject = manifest.packId,
                    message = "This is a FULL pack but it names a base pack " +
                        "(${manifest.basePackId ?: "no id"}, version " +
                        "${manifest.basePackVersion ?: "none"}). A pack that names a base is a delta: " +
                        "installing it as a full pack would replace the installed library with only " +
                        "the part this file carries.",
                )
            }
        }

        if (manifest.isDelta && manifest.basePackVersion != null &&
            manifest.packVersion <= manifest.basePackVersion
        ) {
            into += Failure(
                code = Code.DELTA_NOT_FORWARD,
                subject = manifest.packId,
                message = "This delta is version ${manifest.packVersion}, which does not come after " +
                    "its base version ${manifest.basePackVersion}. Applying it would move the " +
                    "library backwards to content that has already been replaced.",
            )
        }
    }

    // ------------------------------------------------------------------------------------ assets

    /**
     * Asset rules.
     *
     * Sizes are checked here rather than at parse time so that *all* bad sizes are reported
     * together. A zero-byte audio asset is invalid specifically because it is the shape a truncated
     * extraction leaves behind: the entry exists, the manifest lists it, and the file is empty. Left
     * accepted, it would be installed and would play silence while the library counted the track as
     * present.
     */
    private fun validateAssets(manifest: PackManifest, into: MutableList<Failure>) {
        manifest.assets.forEachIndexed { index, asset ->
            val where = "manifest.assets[$index] (${asset.digest.short()})"
            if (asset.byteSize < 0) {
                into += Failure(
                    code = Code.ASSET_SIZE_NEGATIVE,
                    subject = asset.digest.sha256,
                    message = "$where declares a byte size of ${asset.byteSize}. A negative size " +
                        "cannot describe a file, so the manifest is damaged or was written by a " +
                        "different tool than the one that packed it.",
                )
            } else if (asset.byteSize == 0L) {
                into += Failure(
                    code = Code.ASSET_EMPTY,
                    subject = asset.digest.sha256,
                    message = "$where is zero bytes. An audio asset with no bytes is an absent " +
                        "file, not a silent one: it would install, count as present, and play nothing.",
                )
            }
        }

        // Duplicate digests mean the same content is described twice, possibly with two different
        // sizes. Whichever row a reader picked would then be a coincidence.
        manifest.assets.groupBy { it.digest }.forEach { (digest, rows) ->
            if (rows.size > 1) {
                into += Failure(
                    code = Code.DUPLICATE_IDENTITY,
                    subject = digest.sha256,
                    message = "Asset ${digest.short()} appears ${rows.size} times in the asset " +
                        "list. Two rows for one digest can disagree about size or frames, and the " +
                        "one that was read would depend on list order.",
                )
            }
        }
    }

    // ------------------------------------------------------------------------------------ tracks

    /**
     * Track rules.
     *
     * The primary/parts check is the most important one here and the easiest to get wrong when
     * producing a pack: a `primary` that is not in `parts` reads perfectly well on both rows, and
     * the failure only shows up at playback, when the track opens a file it did not declare. The
     * runtime's own [LogicalTrack] refuses that in its constructor; this rule gives the same
     * rejection a message a pack author can act on, before anything is installed.
     */
    private fun validateTracks(manifest: PackManifest, into: MutableList<Failure>) {
        if (manifest.tracks.isEmpty()) {
            into += Failure(
                code = Code.NO_TRACKS,
                subject = manifest.packId,
                message = "The pack declares no logical tracks, so importing it would produce an " +
                    "empty library while reporting success.",
            )
        }

        manifest.tracks.groupBy { it.key }.forEach { (key, rows) ->
            if (rows.size > 1) {
                into += Failure(
                    code = Code.DUPLICATE_IDENTITY,
                    subject = key,
                    message = "Track key '$key' appears ${rows.size} times. A key is the identity a " +
                        "saved listening version and a stage both use, so two tracks cannot share one.",
                )
            }
        }

        val knownAssets = manifest.assets.map { it.digest }.toSet()

        manifest.tracks.forEachIndexed { index, track ->
            val where = "manifest.tracks[$index] ('${track.key}')"

            // Derived, not declared. LogicalTrack.idOf is the same function the existing library
            // uses, so a pack key and a stored listening version's track key can never disagree.
            val derived = LogicalTrack.idOf(track.kind, track.primary)
            if (track.key != derived) {
                into += Failure(
                    code = Code.TRACK_KEY_INCONSISTENT,
                    subject = track.key,
                    message = "$where declares a key that does not follow from its own contents: " +
                        "kind ${track.kind.name} and primary ${track.primary.short()} derive " +
                        "'$derived'. A key a pack is free to choose is not a stable identity; the " +
                        "same audio re-issued under another key would orphan every saved version " +
                        "that pointed at it.",
                )
            }

            if (track.parts.none { it.asset == track.primary }) {
                into += Failure(
                    code = Code.PRIMARY_NOT_IN_PARTS,
                    subject = track.key,
                    message = "$where names primary ${track.primary.short()}, which is not one of " +
                        "its own ${track.parts.size} part(s). Opening the track would play a file " +
                        "it does not own, while the parts it does own would never be bound.",
                )
            }

            val missingAssets = track.parts.map { it.asset }.filterNot { it in knownAssets }.distinct()
            missingAssets.forEach { digest ->
                into += Failure(
                    code = Code.TRACK_ASSET_MISSING,
                    subject = track.key,
                    message = "$where references asset ${digest.short()}, which is not in the " +
                        "pack's asset list. The track would install as playable and fail at the " +
                        "first part the decoder could not find.",
                )
            }

            validateParts(track, where, into)
            validateTiming(manifest, track, where, into)
        }
    }

    /** Roles, outros and the shape a flow versus a single-file track must have. */
    private fun validateParts(
        track: PackManifest.Track,
        where: String,
        into: MutableList<Failure>,
    ) {
        val outros = track.parts.filter { it.role == PackManifest.Role.OUTRO }
        outros.forEach { part ->
            val number = part.outro
            when {
                number == null -> into += Failure(
                    code = Code.OUTRO_NUMBER_INVALID,
                    subject = track.key,
                    message = "$where has an outro part (${part.asset.short()}) with no outro " +
                        "number. Unnumbered endings compare equal, so one of them would disappear " +
                        "from every listen.",
                )

                number < 0 -> into += Failure(
                    code = Code.OUTRO_NUMBER_INVALID,
                    subject = track.key,
                    message = "$where has an outro numbered $number; ending numbers start at 0.",
                )
            }
        }

        track.parts.filter { it.role != PackManifest.Role.OUTRO && it.outro != null }
            .forEach { part ->
                into += Failure(
                    code = Code.OUTRO_NUMBER_INVALID,
                    subject = track.key,
                    message = "$where marks its ${part.role.name} part as outro number " +
                        "${part.outro}. Only an OUTRO role carries a number; a number on another " +
                        "role would address a section that is not an ending.",
                )
            }

        outros.mapNotNull { it.outro }.groupBy { it }.forEach { (number, rows) ->
            if (rows.size > 1) {
                into += Failure(
                    code = Code.OUTRO_NUMBER_INVALID,
                    subject = track.key,
                    message = "$where has ${rows.size} parts numbered as outro $number. The runtime " +
                        "addresses an ending by its number, so two parts under one number means one " +
                        "of them can never be requested.",
                )
            }
        }

        if (track.isFlow) {
            if (track.parts.none { it.role == PackManifest.Role.MAIN }) {
                into += Failure(
                    code = Code.FLOW_WITHOUT_MAIN,
                    subject = track.key,
                    message = "$where is marked as a flow but has no MAIN part. A flow is " +
                        "assembled around its main loop; without one there is nothing to return to.",
                )
            }
        } else if (track.parts.size > 1) {
            into += Failure(
                code = Code.NON_FLOW_WITH_MANY_PARTS,
                subject = track.key,
                message = "$where is marked as a single-file track but carries " +
                    "${track.parts.size} parts (${track.parts.joinToString { it.label }}). " +
                    "A non-flow plays one asset, so the rest would be silently dropped -- mark the " +
                    "track as a flow or remove the extra parts.",
            )
        }
    }

    /**
     * Timings and the provenance that must cover them.
     *
     * A flow without timings has no stated sync points, and the runtime must not supply defaults:
     * `StageFlow.Timing` has no default constructor precisely so that a caller has to choose, and
     * a pack is a caller. The provenance rule is the same one applied to the whole document: a
     * timing shown without its level would be read as a fact about the game.
     */
    private fun validateTiming(
        manifest: PackManifest,
        track: PackManifest.Track,
        where: String,
        into: MutableList<Failure>,
    ) {
        if (track.isFlow && track.timing == null) {
            into += Failure(
                code = Code.FLOW_TIMING_MISSING,
                subject = track.key,
                message = "$where is a flow but declares no timing. The sync points, the main " +
                    "repeat count and the bridge fades are choices only the pack can state; a " +
                    "runtime default would make two builds play the same pack differently.",
            )
        }

        if (track.timing != null && manifest.provenanceFor(track.key, TIMING_FIELD) == null) {
            into += Failure(
                code = Code.TIMING_WITHOUT_PROVENANCE,
                subject = track.key,
                message = "$where declares section timings but no provenance record covers them. " +
                    "These timings are this project's or the pack author's choice, not the game's " +
                    "behaviour, and the app must be able to say so wherever it shows them.",
            )
        }
    }

    // ------------------------------------------------------------------------------------ stages

    /** A stage may not name a logical track the pack does not carry. */
    private fun validateStages(manifest: PackManifest, into: MutableList<Failure>) {
        val knownTracks = manifest.tracks.map { it.key }.toSet()

        manifest.stages.groupBy { it.code }.forEach { (code, rows) ->
            if (rows.size > 1) {
                into += Failure(
                    code = Code.DUPLICATE_IDENTITY,
                    subject = code,
                    message = "Stage code '$code' appears ${rows.size} times. The code names the " +
                        "stage's marker member and the unlock scope, so it has to be unique.",
                )
            }
        }

        manifest.stages.forEachIndexed { index, stage ->
            val where = "manifest.stages[$index] ('${stage.code}')"
            stage.tracks.filterNot { it in knownTracks }.distinct().forEach { key ->
                into += Failure(
                    code = Code.STAGE_TRACK_MISSING,
                    subject = stage.code,
                    message = "$where references logical track '$key', which the pack does not " +
                        "contain. The stage would appear in the library with a row that plays " +
                        "nothing.",
                )
            }
        }
    }

    // ------------------------------------------------------------------------------- provenance

    /**
     * The document must carry provenance at all.
     *
     * Per-timing coverage is checked where the timing is read. This rule catches the pack that
     * states no provenance anywhere: without it, a pack could pass validation by carrying no
     * timings at all, and every later reader would find nothing to qualify.
     */
    private fun validateProvenance(manifest: PackManifest, into: MutableList<Failure>) {
        if (manifest.provenance.isEmpty()) {
            into += Failure(
                code = Code.PROVENANCE_MISSING,
                subject = manifest.packId,
                message = "The pack declares no provenance records. Every stated timing has to say " +
                    "whether it was measured or chosen, or the app cannot show it without implying " +
                    "a certainty the pack never claimed.",
            )
        }
    }

    // ----------------------------------------------------------------------------------- unlock

    /**
     * The unlock contract.
     *
     * The scope must be non-empty, must name stages that exist, and those stages must be complete
     * inside this pack. Each clause closes a way the Special Cup could open wrongly:
     *
     * - an empty scope would make `allCompleted` true over nothing (the `all(emptyList())` trap the
     *   gate itself guards, repeated one layer up);
     * - a scope naming an unknown stage would make that stage permanently uncompletable, so the cup
     *   could never open and the user would be told to keep listening forever;
     * - a scope naming a stage whose tracks are only partly in the pack would let the cup count a
     *   stage the user can never hear in full.
     */
    private fun validateUnlock(manifest: PackManifest, into: MutableList<Failure>) {
        if (manifest.unlock.stages.isEmpty()) {
            into += Failure(
                code = Code.UNLOCK_SCOPE_EMPTY,
                subject = manifest.packId,
                message = "The pack's unlock scope lists no stages. An empty scope makes 'every " +
                    "eligible stage has been heard' true over nothing, which would open the Special " +
                    "Cup on a fresh install.",
            )
            return
        }

        val stagesByCode = manifest.stages.associateBy { it.code }
        val tracksByKey = manifest.tracks.associateBy { it.key }
        val knownAssets = manifest.assets.map { it.digest }.toSet()

        manifest.unlock.stages.distinct().forEach { code ->
            val stage = stagesByCode[code]
            if (stage == null) {
                into += Failure(
                    code = Code.UNLOCK_STAGE_UNKNOWN,
                    subject = code,
                    message = "The unlock scope names stage '$code', which the pack does not " +
                        "describe. That stage could never be completed, so the cup would never " +
                        "open and the progress screen would keep asking for something unobtainable.",
                )
                return@forEach
            }

            val trackKeys = stage.tracks.distinct()
            val resolved = trackKeys.mapNotNull { tracksByKey[it] }
            val missingTracks = trackKeys.filterNot { it in tracksByKey }
            val missingAssets = resolved.flatMap { track ->
                track.parts.map { it.asset }.filterNot { it in knownAssets }
            }.distinct()

            if (trackKeys.isEmpty() || missingTracks.isNotEmpty() || missingAssets.isNotEmpty()) {
                val detail = buildList {
                    if (trackKeys.isEmpty()) add("it has no logical tracks")
                    if (missingTracks.isNotEmpty()) {
                        add("track(s) ${missingTracks.joinToString()} are not in the pack")
                    }
                    if (missingAssets.isNotEmpty()) {
                        add("asset(s) ${missingAssets.joinToString { it.short() }} are not in the pack")
                    }
                }.joinToString("; ")
                into += Failure(
                    code = Code.UNLOCK_STAGE_INCOMPLETE,
                    subject = code,
                    message = "Unlock stage '$code' is not fully contained in this pack: $detail. " +
                        "A stage the user cannot hear in full must not be required by the cup, and " +
                        "must not count as complete for it.",
                )
            }
        }
    }

    /** Field name a timing's provenance record must carry. */
    const val TIMING_FIELD = "timing"
}
