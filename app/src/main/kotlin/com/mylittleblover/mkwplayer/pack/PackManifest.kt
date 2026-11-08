package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * The manifest of an MKW Soundtrack Pack (`.mkwpack`), and its parser.
 *
 * A pack replaces the hand-picked import: one file carries every audio asset, the stage
 * structures, the playback configurations, the cue data, the routes and the checksums. One import
 * is supposed to produce a library, so the manifest is the only place that can state what the pack
 * holds. Everything a runtime needs must therefore be *in* the document; nothing in here is a
 * default that a reader is allowed to improve on.
 *
 * Three properties of the type are deliberate and load-bearing:
 *
 * 1. **Assets are identified by [AudioAssetId] only.** Never by path, file name, or position in the
 *    asset list. A pack that named files would break the moment the user moved the library, and a
 *    list position would silently point at a different asset after an edit.
 * 2. **The model does not validate itself.** There are no `require` calls in the data classes below.
 *    A parser that threw on the first defect could only ever tell a user one problem at a time, and
 *    the installer would have to guess whether the remaining problems were worse. Every rule lives
 *    in [PackValidation], which reports all of them with stable codes. Parsing here answers a
 *    narrower question: "can this document be read as a pack at all?"
 * 3. **Unknown vocabulary is refused, not defaulted.** An unknown role, sync point, provenance level
 *    or track kind is a rejection. Reading an unknown provenance level as the weakest known one is
 *    how a future `L3_VERIFIED` claim would get laundered into displayable data; the bundled cue
 *    catalogue already refuses that, and this parser follows it.
 *
 * The wire vocabulary is frozen here as strings rather than reused from the Kotlin enums. The
 * enums' *names* are not the protocol: renaming `SyncPoint.ExitCue` to `SyncPoint.OnExit` must be a
 * refactor, not a format break. The mapping to the runtime types is explicit in
 * [Role.toStageFlowRole], [SyncPoint.toGraphSyncPoint] and [Timing.toStageFlowTiming], which is also
 * where a mismatch would become a compile error rather than a silent reinterpretation.
 */
internal data class PackManifest(
    /** Wire version of this document. A reader refuses anything it does not implement. */
    val formatVersion: Int,
    /** Stable identity of the *product*, not of one edition of it. */
    val packId: String,
    /** Edition number, increasing per [packId]. A delta names one as its base. */
    val packVersion: Int,
    /** Human name. Falls back to [packId] when the document leaves it blank. */
    val name: String,
    /** Whether this file is a whole pack or an overlay on one. */
    val kind: Kind,
    /** Base product a [Kind.DELTA] applies to. Must be null for [Kind.FULL]. */
    val basePackId: String?,
    /** Base edition a [Kind.DELTA] applies to. Must be null for [Kind.FULL]. */
    val basePackVersion: Int?,
    /** What the pack claims about itself. [PackCoverage] checks those claims against the content. */
    val coverage: DeclaredCoverage,
    /** Every audio file the pack carries, addressed by digest. */
    val assets: List<Asset>,
    /** Every logical track, i.e. every thing a user can play. */
    val tracks: List<Track>,
    /** Stage rows. May be empty; the unlock scope is what decides the cup. */
    val stages: List<Stage>,
    /** Provenance record per declared timing. Empty is invalid; see [PackValidation]. */
    val provenance: List<Provenance>,
    /** The Special Cup's eligible stage list, as the pack fixed it. */
    val unlock: Unlock,
) {

    /** True when this pack is an update to another pack rather than a complete one. */
    val isDelta: Boolean get() = kind == Kind.DELTA

    /** The asset with [digest], or null when the pack does not carry it. */
    fun asset(digest: AudioAssetId): Asset? = assets.firstOrNull { it.digest == digest }

    /** The logical track with [key], or null when the pack does not carry it. */
    fun track(key: String): Track? = tracks.firstOrNull { it.key == key }

    /** The stage with [code], or null when the pack does not describe it. */
    fun stage(code: String): Stage? = stages.firstOrNull { it.code == code }

    /**
     * The tracks of [stage], skipping keys that do not resolve.
     *
     * Skipping is for display only; [PackValidation] rejects a stage whose track is missing, so a
     * caller that has an installed pack never sees a partial list here. Returning null entries
     * instead would push a "which of these is missing" decision into every screen.
     */
    fun tracksOf(stage: Stage): List<Track> = stage.tracks.mapNotNull { track(it) }

    /** Every codec kind any asset in the pack actually uses. */
    fun codecKindsInUse(): Set<Int> = assets.map { it.codecKind }.toSet()

    /**
     * The provenance record for one subject and field, or null when the pack states none.
     *
     * Provenance is looked up rather than stored on each value because one record can cover a whole
     * group. A missing record is a validation failure, not a reason to default: a timing shown
     * without its level reads as a fact.
     */
    fun provenanceFor(subject: String, field: String): Provenance? =
        provenance.firstOrNull { it.subject == subject && it.field == field }

    /** How the pack says it stands apart from how it was assembled. */
    enum class Kind {
        /** A complete pack on its own. */
        FULL,

        /** An overlay on a named base pack, refused unless the base matches exactly. */
        DELTA,
    }

    /**
     * The structural part of a logical track.
     *
     * These are *stated*, never derived from a file name. `StageFlowDiscovery` guesses roles from
     * names for the development import path; that guess must not run against pack content, because
     * a guess inside an accepted pack is indistinguishable from a recorded fact.
     */
    enum class Role {
        /** Short prelude, used when the race starts abruptly. */
        PRE_SHORT,

        /** Full prelude, used on a normal start. */
        PRE,

        /** Bridge between prelude and main loop. */
        BRIDGE,

        /** The main loop. Every flow needs one. */
        MAIN,

        /** One of the endings. Which one the game picks is not established by the pack either. */
        OUTRO,
        ;

        /**
         * The runtime role this part fills.
         *
         * One mapping, written once, so a pack part cannot be played as a different section than
         * the one it declares. An exhaustive `when` makes a new role a compile error here rather
         * than a silently dropped section.
         */
        fun toStageFlowRole(): StageFlow.Role = when (this) {
            PRE_SHORT -> StageFlow.Role.PreShort
            PRE -> StageFlow.Role.Pre
            BRIDGE -> StageFlow.Role.Bridge
            MAIN -> StageFlow.Role.Main
            OUTRO -> StageFlow.Role.Outro
        }
    }

    /**
     * Where a transition may happen.
     *
     * The names are protocol tokens; they are not the runtime enum's identifiers, so a Kotlin
     * rename cannot change what a pack means. [toGraphSyncPoint] is the single place the two
     * vocabularies are tied together.
     */
    enum class SyncPoint {
        IMMEDIATE,
        NEXT_BEAT,
        NEXT_BAR,
        NEXT_GRID,
        EXIT_CUE,
        NEXT_CUE,
        NEXT_CUSTOM_CUE,
        ;

        /** The runtime sync point this token means. */
        fun toGraphSyncPoint(): MusicGraphBuilder.SyncPoint = when (this) {
            IMMEDIATE -> MusicGraphBuilder.SyncPoint.Immediate
            NEXT_BEAT -> MusicGraphBuilder.SyncPoint.NextBeat
            NEXT_BAR -> MusicGraphBuilder.SyncPoint.NextBar
            NEXT_GRID -> MusicGraphBuilder.SyncPoint.NextGrid
            EXIT_CUE -> MusicGraphBuilder.SyncPoint.ExitCue
            NEXT_CUE -> MusicGraphBuilder.SyncPoint.NextCue
            NEXT_CUSTOM_CUE -> MusicGraphBuilder.SyncPoint.NextCustomCue
        }
    }

    /**
     * How well established a declared value is.
     *
     * The level is part of the data because the app has to show it. The distinction is not
     * cosmetic: a marker position is an L1 fact about a container, while a section timing is a
     * choice this project made. Presenting the second as the first would let the app claim it
     * reproduces the game's behaviour, which no evidence in this project supports.
     */
    enum class Level {
        /** A resource-structure fact: a field in a container, a marker record at a named offset. */
        MEASURED_L1,

        /** A fact established by measuring the audio: a header field, a decoded waveform match. */
        MEASURED_L2,

        /** A decision this project made because no evidence establishes the alternative. */
        CHOSEN_BY_PROJECT,

        /** Recorded but nothing establishes it. Shown as unverified rather than hidden. */
        UNVERIFIED,
        ;

        /**
         * True when a value at this level claims to rest on a measurement.
         *
         * Exposed as a function rather than left to a `when` at each call site: the distinction
         * decides whether a value may be displayed as established, and two copies of it would
         * eventually disagree. It also gives [PackValidation] one definition to test against.
         */
        fun isMeasured(): Boolean = this == MEASURED_L1 || this == MEASURED_L2
    }

    /** One audio file in the pack, described by its bytes rather than by a name. */
    data class Asset(
        /** The bytes' SHA-256. The only way this asset is ever referenced. */
        val digest: AudioAssetId,
        /** Exact stored size. Checked against the archive before a byte is read. */
        val byteSize: Long,
        /** Declared media type. There is no file extension to infer it from. */
        val mediaType: String,
        /** Container codec kind; `1` is DSP-ADPCM, the only kind this build decodes. */
        val codecKind: Int,
        /** Frames per second the asset was measured at. Never 0. */
        val sampleRate: Int,
        /** Channel count as measured. Never 0. */
        val channels: Int,
        /** Measured length. Never 0: a zero-frame asset is an absent one. */
        val frames: Long,
        /**
         * Measured loop window, or null when the asset does not loop.
         *
         * Absent is null and never 0: frame 0 is a real position, so a loop of `0..0` would be a
         * claim that the asset loops over nothing rather than a statement that it does not loop.
         */
        val loopStart: Long? = null,
        /** Loop end, in frames. Present exactly when [loopStart] is. */
        val loopEnd: Long? = null,
    ) {
        /** True only when both ends of the loop were measured. */
        val hasLoop: Boolean get() = loopStart != null && loopEnd != null

        /** Length in seconds, for display only; 0 when the rate is unusable. */
        val seconds: Double get() = if (sampleRate <= 0) 0.0 else frames.toDouble() / sampleRate
    }

    /**
     * One asset's place in a logical track.
     *
     * [outro] is what keeps several endings apart. Without it, `Outro00` and `Outro01` would be the
     * same part and one of them would silently disappear from the flow -- the exact bug the runtime
     * already documents for [StageFlow.Part].
     */
    data class Part(
        /** The file this section plays. Must be one of the track's assets. */
        val asset: AudioAssetId,
        /** Which structural section this is. Stated, never inferred from a file name. */
        val role: Role,
        /** Which ending this is, for [Role.OUTRO] only. Null for every other role. */
        val outro: Int? = null,
    ) {
        /** Stable label so two outros never look alike in a list or in a failure message. */
        val label: String
            get() {
                if (role != Role.OUTRO) return role.name
                val number = outro ?: return "Outro(unnumbered)"
                return "Outro%02d".format(number)
            }
    }

    /**
     * How a flow is assembled, as declared by the pack.
     *
     * Every field is a choice, not a measurement, which is why the whole timing object needs a
     * provenance record whose level is [Level.CHOSEN_BY_PROJECT] or [Level.UNVERIFIED]. The pack
     * states these because the alternative -- letting the runtime default them -- would make the
     * timing depend on the app version rather than on the pack, and two listeners on two builds
     * would hear two different arrangements of the same declared content.
     */
    data class Timing(
        /** Allowed position for leaving a prelude. */
        val preludeExit: SyncPoint,
        /** Allowed position for entering the bridge, or handing straight to Main. */
        val bridgeEntry: SyncPoint,
        /** Allowed position for requesting an ending. */
        val outroEntry: SyncPoint,
        /** Allowed position for replacing an ending that was requested but not yet started. */
        val outroSwitch: SyncPoint,
        /** How many main-loop passes before an ending is offered. At least 1. */
        val mainRepeats: Int,
        /** Fade applied when a bridge transition starts, in beats. */
        val bridgeFadeOutBeats: Double = 0.0,
        /** Fade applied when the bridge hands over to Main, in beats. */
        val bridgeFadeInBeats: Double = 0.0,
    ) {
        /** The runtime timing, assembled from stated values only. */
        fun toStageFlowTiming(): StageFlow.Timing = StageFlow.Timing(
            preludeExit = preludeExit.toGraphSyncPoint(),
            bridgeEntry = bridgeEntry.toGraphSyncPoint(),
            outroEntry = outroEntry.toGraphSyncPoint(),
            outroSwitch = outroSwitch.toGraphSyncPoint(),
            mainRepeats = mainRepeats,
            bridgeFadeOutBeats = bridgeFadeOutBeats,
            bridgeFadeInBeats = bridgeFadeInBeats,
        )
    }

    /**
     * One thing a user listens to, made of one or more assets.
     *
     * [key] is derived from [kind] and [primary] and is the only identity a saved listening version
     * or a stage may use. It is checked against that derivation
     * (`TRACK_KEY_INCONSISTENT`), because a key a pack is free to choose is not a stable identity:
     * the same content re-issued under a different key would orphan every saved version that
     * pointed at it.
     */
    data class Track(
        /** The derived key. Checked against its own derivation, never trusted as given. */
        val key: String,
        /** Catalogue kind, part of the key derivation. */
        val kind: ResourceKind,
        /** Display only. Never used to match anything, because a translated title is not an id. */
        val title: String,
        /** Asset that plays first. Must be one of [parts]. */
        val primary: AudioAssetId,
        /** True when [parts] are switched at sync points rather than being separate tracks. */
        val isFlow: Boolean,
        /** Every asset the track needs, primary included. */
        val parts: List<Part>,
        /** Required for a flow, absent for a single-file track. */
        val timing: Timing? = null,
    ) {
        /** The part filling [role], or null when the track does not carry it. */
        fun part(role: Role): Part? = parts.firstOrNull { it.role == role }

        /** Every ending this track offers, in declared order. */
        fun outros(): List<Part> = parts.filter { it.role == Role.OUTRO }

        /** True when the primary asset is itself one of the parts. */
        val primaryIsPart: Boolean get() = parts.any { it.asset == primary }
    }

    /**
     * One stage, and the logical tracks that belong to it.
     *
     * [code] is an identifier, not a label: it becomes a member name (`markers/<code>.json`) and
     * the key the unlock scope uses. A display name would be translated and would stop matching.
     */
    data class Stage(
        /** Identifier, used as a member name and as the unlock scope's key. Never translated. */
        val code: String,
        /** Display name. Falls back to [code] when the document leaves it blank. */
        val name: String,
        /** Logical track keys. More than one is legal: a stage can carry variants as tracks. */
        val tracks: List<String>,
    )

    /**
     * Where one declared value came from.
     *
     * [subject] and [field] are required so a record cannot float free of the value it describes.
     * A pack that says "timings are provisional" without saying which track's timings would be a
     * disclaimer the UI cannot attach to anything, which is how a warning taken from the data it
     * disclaims gets written.
     */
    data class Provenance(
        /** Track key, stage code, or `pack`. */
        val subject: String,
        /** The field this covers, e.g. `timing`. */
        val field: String,
        /** How well established the value is. Shown wherever the value is shown. */
        val level: Level,
        /** Optional human explanation of where the value came from. */
        val note: String? = null,
    )

    /**
     * The Special Cup's eligible stage list, frozen by the pack.
     *
     * This list is the whole defence against a cup that opens for no reason. If eligibility were
     * derived from what the user imported, importing one stage and listening for thirty seconds
     * would satisfy "every eligible stage is complete" over a one-element set -- and `isNotEmpty`
     * cannot help, because a one-stage set is not empty. Only a frozen list the import cannot
     * shrink stands in the way.
     */
    data class Unlock(
        /** Only a frozen list may define cup eligibility. */
        val frozen: Boolean,
        /** Stage codes the cup requires. Empty is invalid. */
        val stages: List<String>,
    )

    /**
     * What the pack claims about its own coverage.
     *
     * These are claims, so [PackCoverage] checks each one against the content. A pack that says
     * "650 tracks, 650 playable" while carrying 412 is not merely wrong: it would let the UI print
     * a number the file cannot support.
     */
    data class DeclaredCoverage(
        /** How many logical tracks the pack says it carries. */
        val totalLogicalTracks: Int,
        /** How many of those the pack claims are playable, by its own definition. */
        val playableLogicalTracks: Int,
        /** Every codec kind any asset uses. A codec left out here is a hidden asset. */
        val codecKinds: List<Int>,
    )

    /** Result of reading a manifest. A rejection carries a reason a user could act on. */
    sealed interface Result {
        /** The document was read and is structurally a v1 manifest. Validation is still required. */
        data class Ok(val manifest: PackManifest) : Result

        /** The document could not be read. [reason] is shown to the user verbatim. */
        data class Rejected(val reason: String) : Result

        /** The manifest, or null when this was a rejection. */
        fun manifestOrNull(): PackManifest? = (this as? Ok)?.manifest

        /** The rejection reason, or null when the manifest was read. */
        fun reasonOrNull(): String? = (this as? Rejected)?.reason
    }

    companion object {

        /** The only wire version this build reads or writes. */
        const val FORMAT_VERSION = 1

        /** Fixed member name of the manifest. Named in the format so a reader can seek to it. */
        const val MEMBER_NAME = "manifest.json"

        /**
         * Shown when the archive has no manifest at all.
         *
         * Written out rather than assembled from [MEMBER_NAME]: a user reads this sentence, and
         * keeping it a literal means what they read is exactly what the source says.
         */
        const val MISSING_REASON: String =
            "This file has no manifest.json, so it is not an MKW Soundtrack Pack. " +
                "A pack is identified by its manifest, never by its file extension."

        /**
         * A pack id is a directory name.
         *
         * The strict pattern is not cosmetic: the installer extracts to
         * `<library>/packs/<packId>/<packVersion>/`, so a pack id of `..` or `../other` would write
         * outside the library. Refusing it here means the staging path never has to trust a value
         * from the document.
         */
        private val PACK_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

        /**
         * A stage code becomes a member name (`markers/<code>.json`), so it is restricted to a
         * token that cannot contain a separator or a traversal segment.
         */
        private val STAGE_CODE_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

        /**
         * Reads the manifest from its raw bytes.
         *
         * @param bytes the member's bytes, or null when the archive has no manifest. Null produces
         *   [MISSING_REASON] rather than an exception, because "not a pack" is a normal condition
         *   the import screen has to display.
         */
        fun read(bytes: ByteArray?): Result {
            if (bytes == null) return Result.Rejected(MISSING_REASON)
            val text = try {
                // Strict decoding: String(bytes, UTF_8) replaces malformed sequences silently, so a
                // truncated member would parse as a document full of U+FFFD and could pass a
                // digest-shaped field by accident. A corrupt manifest has to be refused, not
                // repaired.
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (e: Exception) {
                return Result.Rejected(
                    "manifest.json is not valid UTF-8 (${e.message ?: "decoding failed"}); " +
                        "the pack is damaged.",
                )
            }
            return parse(text)
        }

        /**
         * Parses the manifest text.
         *
         * Order matters. The format version is read before any other field, because a version this
         * build does not implement may have changed the meaning of every field below it; reading
         * the parts that happen to match would produce a plausible pack whose missing facts become
         * guesses. That is the same rule the library store applies to an unknown library version.
         *
         * Never throws: every defect becomes a [Result.Rejected] with a specific reason.
         */
        fun parse(text: String): Result {
            if (text.isBlank()) {
                return Result.Rejected(
                    "manifest.json is empty. An empty manifest is not an empty pack: " +
                        "it is a pack whose contents cannot be stated at all.",
                )
            }
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                return Result.Rejected(
                    "manifest.json is not valid JSON (${e.message ?: "parse failed"}).",
                )
            }
            return try {
                Result.Ok(readManifest(root))
            } catch (e: PackProblem) {
                Result.Rejected(e.message ?: "manifest.json could not be read.")
            } catch (e: Exception) {
                // org.json can throw for shapes the helpers below do not foresee. A crash on the
                // import path is a failed import with no reason; this branch keeps it reportable.
                Result.Rejected("manifest.json could not be read: ${e.message ?: e.javaClass.name}.")
            }
        }

        /** Reads every field of a document whose [formatVersion] is already known to be good. */
        private fun readManifest(root: JSONObject): PackManifest {
            val formatVersion = root.requiredInt("formatVersion", "manifest")
            if (formatVersion != FORMAT_VERSION) {
                fail(
                    "This pack declares format version $formatVersion, but this build reads only " +
                        "version $FORMAT_VERSION. It is refused rather than partly read: a pack " +
                        "read with the wrong rules would turn the fields it does not understand " +
                        "into guesses.",
                )
            }

            val packId = root.requiredString("packId", "manifest")
            if (!PACK_ID_PATTERN.matches(packId)) {
                fail(
                    "manifest.packId '${packId.take(64)}' is not a usable pack id. It becomes a " +
                        "directory name, so it must be 1-128 characters of letters, digits, " +
                        "'.', '_' or '-', starting with a letter or digit.",
                )
            }

            val packVersion = root.requiredInt("packVersion", "manifest")
            if (packVersion < 1) {
                fail("manifest.packVersion must be at least 1, got $packVersion.")
            }

            val name = root.optionalString("name", "manifest") ?: packId

            val kindText = root.requiredString("kind", "manifest")
            val kind = Kind.entries.firstOrNull { it.name.equals(kindText, ignoreCase = true) }
                ?: fail(
                    "manifest.kind '$kindText' is not FULL or DELTA. The kind decides whether this " +
                        "file is a complete pack or an overlay on another one, so it must be " +
                        "understood before anything is installed.",
                )

            val basePackId = if (root.has("basePackId") && !root.isNull("basePackId")) {
                val value = root.requiredString("basePackId", "manifest")
                if (!PACK_ID_PATTERN.matches(value)) {
                    fail("manifest.basePackId '${value.take(64)}' is not a valid pack id.")
                }
                value
            } else {
                null
            }
            val basePackVersion = root.optionalLong("basePackVersion", "manifest")?.let { value ->
                if (value < 0 || value > Int.MAX_VALUE) {
                    fail("manifest.basePackVersion must be a non-negative whole number, got $value.")
                }
                value.toInt()
            }

            val coverageObject = root.requiredObject("coverage", "manifest")
            val totalTracks = coverageObject.requiredInt("totalLogicalTracks", "manifest.coverage")
            if (totalTracks < 0) {
                fail("manifest.coverage.totalLogicalTracks must not be negative, got $totalTracks.")
            }
            val playableTracks =
                coverageObject.requiredInt("playableLogicalTracks", "manifest.coverage")
            if (playableTracks < 0) {
                fail(
                    "manifest.coverage.playableLogicalTracks must not be negative, got " +
                        "$playableTracks.",
                )
            }
            if (playableTracks > totalTracks) {
                fail(
                    "manifest.coverage declares $playableTracks playable tracks out of " +
                        "$totalTracks. A playable count cannot exceed the total, so the document " +
                        "contradicts itself and no count taken from it can be shown.",
                )
            }
            val codecArray = coverageObject.requiredArray("codecKinds", "manifest.coverage")
            val codecKinds = ArrayList<Int>(codecArray.length())
            for (index in 0 until codecArray.length()) {
                val codec = codecArray.requiredIntAt(index, "manifest.coverage.codecKinds")
                if (codec < 0) {
                    fail("manifest.coverage.codecKinds[$index] must not be negative, got $codec.")
                }
                codecKinds += codec
            }

            val assetArray = root.requiredArray("assets", "manifest")
            val assets = ArrayList<Asset>(assetArray.length())
            for (index in 0 until assetArray.length()) {
                assets += readAsset(assetArray.objectAt(index, "manifest.assets"), index)
            }

            val trackArray = root.requiredArray("tracks", "manifest")
            val tracks = ArrayList<Track>(trackArray.length())
            for (index in 0 until trackArray.length()) {
                tracks += readTrack(trackArray.objectAt(index, "manifest.tracks"), index)
            }

            val stageArray = root.requiredArray("stages", "manifest")
            val stages = ArrayList<Stage>(stageArray.length())
            for (index in 0 until stageArray.length()) {
                stages += readStage(stageArray.objectAt(index, "manifest.stages"), index)
            }

            val provenanceArray = root.requiredArray("provenance", "manifest")
            val provenance = ArrayList<Provenance>(provenanceArray.length())
            for (index in 0 until provenanceArray.length()) {
                provenance += readProvenance(
                    provenanceArray.objectAt(index, "manifest.provenance"),
                    index,
                )
            }

            val unlockObject = root.requiredObject("unlock", "manifest")
            val frozen = unlockObject.requiredBoolean("frozen", "manifest.unlock")
            val unlockArray = unlockObject.requiredArray("stages", "manifest.unlock")
            val unlockStages = ArrayList<String>(unlockArray.length())
            for (index in 0 until unlockArray.length()) {
                val code = unlockArray.requiredStringAt(index, "manifest.unlock.stages")
                if (!STAGE_CODE_PATTERN.matches(code)) {
                    fail(
                        "manifest.unlock.stages[$index] '$code' is not a valid stage code " +
                            "(letters, digits, '_' and '-', up to 64 characters).",
                    )
                }
                unlockStages += code
            }

            return PackManifest(
                formatVersion = formatVersion,
                packId = packId,
                packVersion = packVersion,
                name = name,
                kind = kind,
                basePackId = basePackId,
                basePackVersion = basePackVersion,
                coverage = DeclaredCoverage(
                    totalLogicalTracks = totalTracks,
                    playableLogicalTracks = playableTracks,
                    codecKinds = codecKinds,
                ),
                assets = assets,
                tracks = tracks,
                stages = stages,
                provenance = provenance,
                unlock = Unlock(frozen = frozen, stages = unlockStages),
            )
        }

        private fun readAsset(source: JSONObject, index: Int): Asset {
            val where = "manifest.assets[$index]"
            val assetDigest = digest(source.requiredString("digest", where), "$where.digest")
            val byteSize = source.requiredLong("byteSize", where)
            val mediaType = source.requiredString("mediaType", where)
            val codecKind = source.requiredInt("codecKind", where)
            if (codecKind < 0) {
                fail("$where.codecKind must not be negative, got $codecKind.")
            }
            val sampleRate = source.requiredInt("sampleRate", where)
            if (sampleRate <= 0) {
                fail(
                    "$where.sampleRate must be positive, got $sampleRate. Every duration " +
                        "derived from a zero rate would be a division by zero presented as 0 s.",
                )
            }
            val channels = source.requiredInt("channels", where)
            if (channels <= 0) {
                fail("$where.channels must be positive, got $channels.")
            }
            val frames = source.requiredLong("frames", where)
            if (frames <= 0) {
                fail(
                    "$where.frames must be positive, got $frames. An asset with no frames cannot " +
                        "be played, and a zero would make it look like an empty file that loaded.",
                )
            }
            val loopStart = source.optionalLong("loopStart", where)
            val loopEnd = source.optionalLong("loopEnd", where)
            if ((loopStart == null) != (loopEnd == null)) {
                fail(
                    "$where declares only one of loopStart and loopEnd. Half a loop is not a " +
                        "loop: playing it would either loop from 0 or stop at an unstated point.",
                )
            }
            if (loopStart != null && loopEnd != null) {
                if (loopStart < 0) {
                    fail("$where.loopStart must not be negative, got $loopStart.")
                }
                if (loopStart >= loopEnd) {
                    fail(
                        "$where.loopStart ($loopStart) must be before loopEnd ($loopEnd); " +
                            "otherwise the loop window is empty or reversed.",
                    )
                }
                if (loopEnd > frames) {
                    fail(
                        "$where.loopEnd ($loopEnd) is past the frame count ($frames); the loop " +
                            "would run into audio the asset does not contain.",
                    )
                }
            }
            return Asset(
                digest = assetDigest,
                byteSize = byteSize,
                mediaType = mediaType,
                codecKind = codecKind,
                sampleRate = sampleRate,
                channels = channels,
                frames = frames,
                loopStart = loopStart,
                loopEnd = loopEnd,
            )
        }

        private fun readTrack(source: JSONObject, index: Int): Track {
            val where = "manifest.tracks[$index]"
            val key = source.requiredString("key", where)
            val kindText = source.requiredString("kind", where)
            val kind = ResourceKind.entries.firstOrNull {
                it.name.equals(kindText, ignoreCase = true)
            } ?: fail(
                "$where.kind '$kindText' is not a track kind this build knows " +
                    "(${ResourceKind.entries.joinToString { it.name }}).",
            )
            val title = source.optionalString("title", where) ?: key
            val primary = digest(source.requiredString("primary", where), "$where.primary")
            val isFlow = source.requiredBoolean("isFlow", where)

            val partArray = source.requiredArray("parts", where)
            val parts = ArrayList<Part>(partArray.length())
            for (position in 0 until partArray.length()) {
                parts += readPart(partArray.objectAt(position, where), "$where.parts[$position]")
            }

            val timing = if (source.has("timing") && !source.isNull("timing")) {
                readTiming(source.requiredObject("timing", where), "$where.timing")
            } else {
                null
            }

            return Track(
                key = key,
                kind = kind,
                title = title,
                primary = primary,
                isFlow = isFlow,
                parts = parts,
                timing = timing,
            )
        }

        private fun readPart(source: JSONObject, where: String): Part {
            val asset = digest(source.requiredString("asset", where), "$where.asset")
            val roleText = source.requiredString("role", where)
            val role = Role.entries.firstOrNull { it.name.equals(roleText, ignoreCase = true) }
                ?: fail(
                    "$where.role '$roleText' is not a section this build knows " +
                        "(${Role.entries.joinToString { it.name }}). Guessing a role from the " +
                        "asset would put a part in the wrong place in every listen.",
                )
            val outro = source.optionalLong("outro", where)?.let { value ->
                if (value < 0 || value > 99) {
                    fail(
                        "$where.outro must be between 0 and 99, got $value. The runtime addresses " +
                            "endings by number, and 100 would run into the segment block above it.",
                    )
                }
                value.toInt()
            }
            return Part(asset = asset, role = role, outro = outro)
        }

        private fun readTiming(source: JSONObject, where: String): Timing {
            fun sync(key: String): SyncPoint {
                val text = source.requiredString(key, where)
                return SyncPoint.entries.firstOrNull {
                    it.name.equals(text, ignoreCase = true)
                } ?: fail(
                    "$where.$key '$text' is not a sync point this build knows " +
                        "(${SyncPoint.entries.joinToString { it.name }}). An unknown sync point " +
                        "must not fall back to IMMEDIATE: that would silently move a transition " +
                        "the pack declared elsewhere.",
                )
            }

            val mainRepeats = source.requiredInt("mainRepeats", where)
            if (mainRepeats < 1) {
                fail(
                    "$where.mainRepeats must be at least 1, got $mainRepeats. Zero would drop " +
                        "the main loop from the listen while the track still lists it.",
                )
            }
            val fadeOut = source.optionalDouble("bridgeFadeOutBeats", where)
            val fadeIn = source.optionalDouble("bridgeFadeInBeats", where)
            if (fadeOut < 0.0) {
                fail("$where.bridgeFadeOutBeats must not be negative, got $fadeOut.")
            }
            if (fadeIn < 0.0) {
                fail("$where.bridgeFadeInBeats must not be negative, got $fadeIn.")
            }
            return Timing(
                preludeExit = sync("preludeExit"),
                bridgeEntry = sync("bridgeEntry"),
                outroEntry = sync("outroEntry"),
                outroSwitch = sync("outroSwitch"),
                mainRepeats = mainRepeats,
                bridgeFadeOutBeats = fadeOut,
                bridgeFadeInBeats = fadeIn,
            )
        }

        private fun readStage(source: JSONObject, index: Int): Stage {
            val where = "manifest.stages[$index]"
            val code = source.requiredString("code", where)
            if (!STAGE_CODE_PATTERN.matches(code)) {
                fail(
                    "$where.code '$code' is not a usable stage code: it becomes a member name " +
                        "(markers/$code.json), so only letters, digits, '_' and '-', up to 64 " +
                        "characters, are allowed.",
                )
            }
            val name = source.optionalString("name", where) ?: code
            val trackArray = source.requiredArray("tracks", where)
            val tracks = ArrayList<String>(trackArray.length())
            for (position in 0 until trackArray.length()) {
                tracks += trackArray.requiredStringAt(position, "$where.tracks")
            }
            return Stage(code = code, name = name, tracks = tracks)
        }

        private fun readProvenance(source: JSONObject, index: Int): Provenance {
            val where = "manifest.provenance[$index]"
            val subject = source.requiredString("subject", where)
            val fieldName = source.requiredString("field", where)
            val levelText = source.requiredString("level", where)
            val level = Level.entries.firstOrNull { it.name.equals(levelText, ignoreCase = true) }
                ?: fail(
                    "$where.level '$levelText' is not a provenance level this build knows " +
                        "(${Level.entries.joinToString { it.name }}). An unknown level is refused " +
                        "rather than read as the weakest one: that is how a stronger claim would " +
                        "be laundered into displayable data.",
                )
            return Provenance(
                subject = subject,
                field = fieldName,
                level = level,
                note = source.optionalString("note", where),
            )
        }

        /**
         * Parses one digest.
         *
         * A malformed digest is refused here rather than in validation because [AudioAssetId]
         * cannot represent one. The message names the value and the reason, since a pack author is
         * the only person who can fix it.
         */
        private fun digest(value: String, where: String): AudioAssetId {
            return AudioAssetId.parse(value) ?: fail(
                "$where '$value' is not a SHA-256 digest. It must be exactly 64 lowercase hex " +
                    "characters; an uppercase or shortened value would resolve to a different " +
                    "asset than the one the pack carries.",
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// org.json helpers
//
// Typed accessors rather than the opt* family. `optInt` returns a default for a value of the wrong
// type, which is exactly the silent coercion this format cannot allow: an asset whose size was
// written as the string "1024" would be read as 0 and reported as an empty file instead of a
// malformed pack.
// ---------------------------------------------------------------------------------------------

/**
 * Raised while reading a manifest defect.
 *
 * A private control-flow exception, caught once in [PackManifest.parse] and converted into a
 * rejection. It is not an error type any caller handles, so keeping it private means a caller
 * cannot branch on how a manifest failed to parse -- only on the reason, which is what a user sees.
 */
private class PackProblem(message: String) : Exception(message)

private fun fail(message: String): Nothing = throw PackProblem(message)

private fun JSONObject.requiredValue(key: String, where: String): Any {
    if (!has(key) || isNull(key)) fail("$where has no '$key'.")
    return get(key)
}

private fun JSONObject.requiredString(key: String, where: String): String {
    val raw = requiredValue(key, where)
    val text = raw as? String ?: fail("$where.'$key' must be text, got ${describe(raw)}.")
    if (text.isBlank()) fail("$where.'$key' is blank.")
    return text
}

private fun JSONObject.optionalString(key: String, where: String): String? {
    if (!has(key) || isNull(key)) return null
    val raw = get(key)
    val text = raw as? String ?: fail("$where.'$key' must be text, got ${describe(raw)}.")
    return text.takeIf { it.isNotBlank() }
}

private fun JSONObject.requiredLong(key: String, where: String): Long {
    val raw = requiredValue(key, where)
    return wholeNumber(raw, "$where.'$key'")
}

private fun JSONObject.optionalLong(key: String, where: String): Long? {
    if (!has(key) || isNull(key)) return null
    return wholeNumber(get(key), "$where.'$key'")
}

private fun JSONObject.requiredInt(key: String, where: String): Int {
    val value = requiredLong(key, where)
    if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
        fail("$where.'$key' is out of range for a 32-bit integer: $value.")
    }
    return value.toInt()
}

private fun JSONObject.optionalDouble(key: String, where: String): Double {
    if (!has(key) || isNull(key)) return 0.0
    val raw = get(key)
    val number = raw as? Number ?: fail("$where.'$key' must be a number, got ${describe(raw)}.")
    val value = number.toDouble()
    if (!value.isFinite()) fail("$where.'$key' must be finite, got $value.")
    return value
}

private fun JSONObject.requiredBoolean(key: String, where: String): Boolean {
    val raw = requiredValue(key, where)
    return raw as? Boolean ?: fail("$where.'$key' must be true or false, got ${describe(raw)}.")
}

private fun JSONObject.requiredObject(key: String, where: String): JSONObject {
    val raw = requiredValue(key, where)
    return raw as? JSONObject ?: fail("$where.'$key' must be an object, got ${describe(raw)}.")
}

private fun JSONObject.requiredArray(key: String, where: String): JSONArray {
    val raw = requiredValue(key, where)
    return raw as? JSONArray ?: fail("$where.'$key' must be an array, got ${describe(raw)}.")
}

private fun JSONArray.objectAt(index: Int, where: String): JSONObject {
    val raw = if (index in 0 until length()) opt(index) else null
    return raw as? JSONObject ?: fail("$where[$index] must be an object, got ${describe(raw)}.")
}

private fun JSONArray.requiredStringAt(index: Int, where: String): String {
    val raw = if (index in 0 until length()) opt(index) else null
    val text = raw as? String ?: fail("$where[$index] must be text, got ${describe(raw)}.")
    if (text.isBlank()) fail("$where[$index] is blank.")
    return text
}

private fun JSONArray.requiredIntAt(index: Int, where: String): Int {
    val raw = if (index in 0 until length()) opt(index) else null
    val value = wholeNumber(raw, "$where[$index]")
    if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
        fail("$where[$index] is out of range for a 32-bit integer: $value.")
    }
    return value.toInt()
}

/**
 * A JSON value that is a whole number.
 *
 * A fractional number is refused rather than truncated: a `byteSize` of `1.5` is a broken document,
 * and reading it as 1 would hide the defect behind a size that nearly matches.
 *
 * Integral values are read through their own type rather than through `Double`, because a frame
 * count or byte size near `2^53` would otherwise lose its last digits and a size check would then
 * compare against a number the manifest never wrote.
 */
private fun wholeNumber(raw: Any?, where: String): Long = when (raw) {
    is Byte -> raw.toLong()
    is Short -> raw.toLong()
    is Int -> raw.toLong()
    is Long -> raw
    is Number -> {
        val asDouble = raw.toDouble()
        if (!asDouble.isFinite() || asDouble % 1.0 != 0.0) {
            fail("$where must be a whole number, got $raw.")
        }
        // A value this large cannot describe a size or a frame count, and `toLong()` would silently
        // clamp it to Long.MAX_VALUE -- a number the manifest never wrote, which would then be
        // compared against a real file.
        if (asDouble < Long.MIN_VALUE.toDouble() || asDouble > Long.MAX_VALUE.toDouble()) {
            fail("$where is out of range for a 64-bit integer: $raw.")
        }
        raw.toLong()
    }

    else -> fail("$where must be a whole number, got ${describe(raw)}.")
}

private fun describe(value: Any?): String = when {
    value == null -> "null"
    value == JSONObject.NULL -> "null"
    value is String -> "text"
    value is Boolean -> "a boolean"
    value is Number -> "a number"
    value is JSONObject -> "an object"
    value is JSONArray -> "an array"
    else -> "an unsupported value"
}
