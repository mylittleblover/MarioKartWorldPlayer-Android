package com.mylittleblover.mkwplayer.routes

import com.mylittleblover.mkwplayer.registry.AudioAssetId

/*
 * The route / map data layer.
 *
 * Five facts are modelled as five separate types on purpose:
 *
 *  1. where a stage sits on the map   -- [StageNode.position] / [MapPosition]
 *  2. which stages are actually linked -- [RoadConnection]
 *  3. what may play on a link          -- [RoadMusicCandidate]
 *  4. how a track hands over to the next -- [MusicTransition]
 *  5. what the user saved              -- [SavedRoute]
 *
 * Conflating them is the mistake this file exists to prevent. A geographic neighbour is not a
 * road; an `InterStageEx1_*` resource name proves a road relationship but not the music on it;
 * a candidate list is not the game's selection rule. Each layer therefore carries its own
 * uncertainty (a null position, INFERRED provenance, an empty candidate list) instead of
 * filling a gap with a plausible default.
 */

/**
 * Stable identity for a stage.
 *
 * The value is an id, not a label. Display names are translated and change as naming research
 * improves, and a file path changes the moment the user moves the library. A saved route that
 * used either would stop resolving for a reason that has nothing to do with the stage.
 *
 * A path is refused here for the same reason: accepting one would let a route work on the
 * machine that built it and silently fail everywhere else.
 */
internal data class StageId(val value: String) {

    init {
        require(value.isNotBlank()) { "stage id must not be blank" }
        require(value == value.trim()) {
            "stage id must not have surrounding whitespace, got '$value'"
        }
        require('/' !in value && '\\' !in value) {
            "stage id must be a stable identity, not a file path, got '$value'"
        }
    }

    /** Raw form, useful in failure messages where the quotes of [toString] are noise. */
    override fun toString(): String = value
}

/**
 * A point on the game's route map, in the map's own units.
 *
 * Deliberately not latitude/longitude: nothing ties a stage's placement to real-world
 * geography, and inventing a projection would add precision nobody measured.
 *
 * `(0.0, 0.0)` is a valid coordinate (the map origin). That is exactly why "placement
 * unknown" is represented by a null [StageNode.position] and never by this value: treating
 * the origin as unknown would stack every unresearched stage on one point.
 */
internal data class MapPosition(val x: Double, val y: Double) {

    init {
        require(x.isFinite() && y.isFinite()) {
            "map position must be finite, got ($x, $y)"
        }
    }
}

/**
 * A stage as the map and the music library know it.
 *
 * [position] is nullable because unknown placement is a real state during research. A default
 * of `(0, 0)` would be a lie with visible consequences: the map would claim every unresearched
 * stage is at the origin instead of showing that its placement still needs work.
 *
 * [musicAssets] may be empty. An empty list means "no music is known for this stage", which is
 * different from "the stage has no music" -- the first is a gap in the data, the second would
 * be a claim. Nothing downstream may turn the gap into a guess.
 */
internal data class StageNode(
    val id: StageId,
    val displayName: String,
    /** Map placement, or null when that placement is not established. */
    val position: MapPosition?,
    /** Asset ids of this stage's music, in flow order. Empty when unknown. */
    val musicAssets: List<AudioAssetId>,
) {

    init {
        require(displayName.isNotBlank()) { "a stage needs a display name" }
        require(musicAssets.size == musicAssets.toSet().size) {
            "a stage's music assets must not repeat"
        }
    }

    /** True only when the placement was actually stated. */
    val hasKnownPosition: Boolean get() = position != null
}

/**
 * How strongly the existence of a road is established.
 *
 * The distinction is not cosmetic. Research found real `InterStageEx1_<A>To<B>_*` resources,
 * so some roads are game facts. But turning a resource name into two stable stage ids, and
 * deciding what music plays on the road, are further claims that the name alone does not
 * support. Treating those claims as VERIFIED would launder a guess into evidence.
 */
internal enum class ConnectionProvenance {
    /**
     * Established by evidence other than a resource name: a game table, or a verified runtime
     * observation that both endpoints and the link are real.
     */
    VERIFIED,

    /**
     * Inferred from naming or proximity. Useful enough to model and display, but not proof:
     * the road may exist under a different pairing, and the name says nothing about music.
     */
    INFERRED,
}

/**
 * A road between two stages, with the evidence behind it.
 *
 * The constructor is private so a connection cannot be created without choosing a
 * [provenance]. [verified] additionally requires written [evidence], because "verified" is the
 * one value that can silently turn a naming inference into a fact.
 *
 * Endpoints are normalised into a fixed order. A road is one relationship, not two: without
 * normalisation, `inferred(A, B)` and `inferred(B, A)` would be different values and a music
 * lookup could miss the road merely because a caller wrote the pair the other way round.
 */
internal data class RoadConnection private constructor(
    /** First endpoint in normalised (lexicographic) order. */
    val a: StageId,
    /** Second endpoint in normalised (lexicographic) order. */
    val b: StageId,
    val provenance: ConnectionProvenance,
    /** Written support for [ConnectionProvenance.VERIFIED]; null for an inferred link. */
    val evidence: String? = null,
) {

    init {
        require(a != b) {
            "a road must connect two different stages, got '$a' on both ends"
        }
        require(provenance != ConnectionProvenance.VERIFIED || !evidence.isNullOrBlank()) {
            "a VERIFIED road must state the evidence that established it"
        }
    }

    /** True only when real evidence, not a name, established this road. */
    val isVerified: Boolean get() = provenance == ConnectionProvenance.VERIFIED

    /** True when this road is usable only as an explicitly marked inference. */
    val isInferred: Boolean get() = provenance == ConnectionProvenance.INFERRED

    /** True when [stage] is one of the two endpoints. */
    fun connects(stage: StageId): Boolean = stage == a || stage == b

    /** The endpoint opposite [stage], or null when [stage] is not part of this road. */
    fun otherEnd(stage: StageId): StageId? = when (stage) {
        a -> b
        b -> a
        else -> null
    }

    companion object {

        private const val RESOURCE_PREFIX = "InterStageEx1_"

        /**
         * A road established by evidence that names both stages.
         *
         * Requiring [evidence] is deliberate friction: a caller holding only a resource name
         * cannot reach for this factory without noticing that it has to claim what the
         * evidence was.
         */
        fun verified(a: StageId, b: StageId, evidence: String): RoadConnection {
            require(evidence.isNotBlank()) {
                "a VERIFIED road must state the evidence that established it"
            }
            val (first, second) = ordered(a, b)
            return RoadConnection(first, second, ConnectionProvenance.VERIFIED, evidence.trim())
        }

        /**
         * A road inferred from naming or proximity.
         *
         * This is the factory for anything derived from an `InterStageEx1_*` name: the naming
         * is useful, but it is not evidence that the stable ids or the music mapping are
         * correct.
         */
        fun inferred(a: StageId, b: StageId): RoadConnection {
            val (first, second) = ordered(a, b)
            return RoadConnection(first, second, ConnectionProvenance.INFERRED)
        }

        /**
         * Reads a road out of a resource name such as
         * `Work/Banc/Course/InterStageEx1_FirstCircuitToCoaster_A_00.bcett.json`.
         *
         * The result is always [ConnectionProvenance.INFERRED], never VERIFIED. The resource
         * name establishes that some road relationship was authored; it does not establish
         * that `FirstCircuit` is the id of the stage this app calls that, nor which music
         * plays on the road. A caller with stronger evidence must build that connection
         * explicitly through [verified] and state the evidence.
         *
         * @param resolve maps a name segment to a stable stage id, or null when the segment is
         *   not a known stage. Trying every `To` split through the resolver is necessary
         *   because stage names can themselves contain `To` (`ToadFactory`).
         * @return null when no split has two resolvable endpoints. Returning null is the
         *   honest result: inventing ids from the name would be exactly the guess this type
         *   is supposed to prevent.
         */
        fun fromInterStageName(
            resourceName: String,
            resolve: (String) -> StageId?,
        ): RoadConnection? {
            val fileName = resourceName.substringAfterLast('/').substringAfterLast('\\')
            val stem = fileName.substringBefore('.')
            if (!stem.startsWith(RESOURCE_PREFIX, ignoreCase = true)) return null

            val body = stem.substring(RESOURCE_PREFIX.length)
            for (at in separatorPositions(body)) {
                val leftName = body.substring(0, at)
                val rightPart = body.substring(at + 2)
                val left = resolve(leftName) ?: continue
                for (rightName in rightVariants(rightPart)) {
                    val right = resolve(rightName) ?: continue
                    if (left != right) return inferred(left, right)
                }
            }
            return null
        }

        private fun ordered(first: StageId, second: StageId): Pair<StageId, StageId> =
            if (first.value <= second.value) first to second else second to first

        /** Positions of `To` that could separate two stage names, ignoring an empty left end. */
        private fun separatorPositions(body: String): List<Int> {
            val positions = mutableListOf<Int>()
            var from = 1
            while (from < body.length) {
                val at = body.indexOf("To", from, ignoreCase = true)
                if (at < 0) break
                positions += at
                from = at + 1
            }
            return positions
        }

        /**
         * The right-hand name with any `_<variant>_<instance>` suffix removed.
         *
         * Longest prefixes are tried first so a real underscore in a stage name is not cut
         * into a shorter, wrong stage (`Peach_Beach_A_00` must not resolve as `Peach`).
         */
        private fun rightVariants(rightPart: String): List<String> {
            val cuts = mutableListOf<Int>()
            var cut = rightPart.indexOf('_')
            while (cut > 0) {
                cuts += cut
                cut = rightPart.indexOf('_', cut + 1)
            }
            return buildList {
                add(rightPart)
                for (position in cuts.sortedDescending()) {
                    add(rightPart.substring(0, position))
                }
            }.distinct()
        }
    }
}

/**
 * Tracks that may play on a road.
 *
 * This is a candidate set, not a rule. The research established that some `InterStageEx1_*`
 * roads exist; it did not establish which of several tracks the game chooses, or whether the
 * choice is random, positional, or fixed. [selectionNote] therefore always says the pick is
 * not established, even when candidates are present.
 *
 * An empty [musicAssets] list means no verified music is known for this road. It must stay
 * empty rather than borrow a stage theme or another road's track: a wrong track is audible,
 * while an empty list can still be shown to the user as a data gap.
 */
internal data class RoadMusicCandidate(
    val connection: RoadConnection,
    /** Candidate asset ids, in no established order. Empty when no candidate is verified. */
    val musicAssets: List<AudioAssetId>,
) {

    init {
        require(musicAssets.size == musicAssets.toSet().size) {
            "a road's music candidate list must not repeat a track"
        }
    }

    /** True when at least one track is associated with the road. Not a play guarantee. */
    val hasCandidates: Boolean get() = musicAssets.isNotEmpty()

    /**
     * Why this object is only a candidate set.
     *
     * Computed rather than stored so no caller can build an empty candidate list with a note
     * that sounds like a verified mapping, or a non-empty one that omits the unknown pick.
     */
    val selectionNote: String
        get() = if (hasCandidates) NOTE_PICK_NOT_ESTABLISHED else NOTE_NO_VERIFIED_MUSIC

    companion object {

        /**
         * Shown whenever candidates exist: it is still unknown which one the game selects on a
         * given pass.
         */
        const val NOTE_PICK_NOT_ESTABLISHED =
            "Music candidates only: which track the game picks for this road is not established."

        /**
         * Shown when the list is empty. Saying "no verified music" is a data gap, not a claim
         * that the road is silent.
         */
        const val NOTE_NO_VERIFIED_MUSIC =
            "No verified music candidate for this road; which track plays is not established."

        /** A road whose music is entirely unknown, with the gap stated explicitly. */
        fun unknown(connection: RoadConnection): RoadMusicCandidate =
            RoadMusicCandidate(connection, emptyList())
    }
}

/**
 * How one track hands over to the next.
 *
 * This is a separate layer from choosing the track ([RouteSelection]) and from the road's
 * candidates. The same transition can be reused across many roads, and a track choice does
 * not imply a particular handover.
 *
 * Durations are wall-clock milliseconds, not beats: nothing establishes a tempo or meter for
 * an arbitrary pair of tracks, so a beat-based value would invent a musical fact.
 *
 * A route does not carry a transition by default. Null at the call site means "not
 * configured"; inventing a fade here would claim a handover nobody chose.
 */
internal sealed interface MusicTransition {

    /** Hand over at once. Useful when the outgoing source is already silent. */
    data object Cut : MusicTransition

    /** The outgoing track fades out while the incoming one fades in. */
    data class Crossfade(val durationMs: Int) : MusicTransition {

        init {
            require(durationMs > 0) {
                "a crossfade needs a positive duration, got $durationMs ms"
            }
        }
    }

    /** The outgoing track fades out, then the incoming one fades in. No overlap. */
    data class FadeThrough(
        val fadeOutMs: Int,
        val fadeInMs: Int,
    ) : MusicTransition {

        init {
            require(fadeOutMs > 0) {
                "a fade-through needs a positive fade-out, got $fadeOutMs ms"
            }
            require(fadeInMs > 0) {
                "a fade-through needs a positive fade-in, got $fadeInMs ms"
            }
        }
    }

    /** Total handover time in milliseconds; zero for [Cut]. */
    val totalDurationMs: Int
        get() = when (this) {
            Cut -> 0
            is Crossfade -> durationMs
            is FadeThrough -> fadeOutMs + fadeInMs
        }
}

/**
 * One stop in a route: a stage, or the road between two stages.
 *
 * Two shapes rather than an enum plus optional fields, so a road can never be read as a stage
 * with a missing name -- the mistake that would put a road into a stage list.
 */
internal sealed interface RouteStop {

    /** A stage the route visits. */
    data class Stage(val stage: StageId) : RouteStop

    /** A road the route drives, named by the connection it uses. */
    data class Road(val connection: RoadConnection) : RouteStop
}

/**
 * What kind of route this is.
 *
 * The marker is explicit so a later change to a game-defined template cannot silently
 * reinterpret a route the user built or saved. [GRAND_PRIX] and [KNOCKOUT] are static formats;
 * [CUSTOM] is a user arrangement.
 */
internal enum class RouteKind {
    GRAND_PRIX,
    KNOCKOUT,
    CUSTOM,
    ;

    /** True for a game-defined format, false for a user arrangement. */
    val isStatic: Boolean get() = this != CUSTOM
}

/**
 * An ordered list of stops with an explicit [kind].
 *
 * At least two stops are required: a one-stop route has no handover and cannot exercise any
 * of the layers below it.
 *
 * A stage may not appear twice in a row. This is checked over the stage stops after roads are
 * removed, because `Coaster -> road -> Coaster` is still an immediate repeat of the same
 * stage music even though the raw stop sequence has a road in between.
 *
 * This type is not the map and not a playback plan. It records the order the user chose; where
 * a stage sits belongs to [StageNode], and which tracks may play belongs to
 * [RoadMusicCandidate].
 */
internal data class Route(
    val stops: List<RouteStop>,
    val kind: RouteKind,
) {

    init {
        require(stops.size >= 2) {
            "a route needs at least two stops, got ${stops.size}"
        }
        val stageVisits = stops.mapNotNull { (it as? RouteStop.Stage)?.stage }
        val repeated = stageVisits.zipWithNext().firstOrNull { (left, right) -> left == right }
        require(repeated == null) {
            "a route must not visit the same stage twice in a row, got '${repeated?.first?.value}'"
        }
    }

    /** Stage stops in route order. Roads contribute no stage of their own. */
    val stageIds: List<StageId>
        get() = stops.filterIsInstance<RouteStop.Stage>().map { it.stage }

    /** Road stops in route order. */
    val roads: List<RoadConnection>
        get() = stops.filterIsInstance<RouteStop.Road>().map { it.connection }

    /** True when this is a static game format rather than a user arrangement. */
    val isStatic: Boolean get() = kind.isStatic

    /** True when [stage] is visited or is an endpoint of a road in this route. */
    fun references(stage: StageId): Boolean =
        stops.any { stop ->
            when (stop) {
                is RouteStop.Stage -> stop.stage == stage
                is RouteStop.Road -> stop.connection.connects(stage)
            }
        }

    /**
     * Every stage id this route refers to, including road endpoints.
     *
     * Saved-route validation uses this rather than [stageIds] so a dangling road endpoint
     * cannot slip through when only stage stops are checked.
     */
    fun referencedStageIds(): Set<StageId> = buildSet {
        for (stop in stops) {
            when (stop) {
                is RouteStop.Stage -> add(stop.stage)
                is RouteStop.Road -> {
                    add(stop.connection.a)
                    add(stop.connection.b)
                }
            }
        }
    }

    companion object {

        /** A user arrangement, the only kind a [SavedRoute] is expected to hold. */
        fun custom(stops: List<RouteStop>): Route = Route(stops, RouteKind.CUSTOM)

        /**
         * The Grand Prix template built from its course list.
         *
         * Only stage stops are added. The list names courses, not the road legs driven between
         * them; inserting inferred roads here would present a guess as part of the static
         * format. A caller that has verified roads can build a mixed route explicitly.
         *
         * The number of courses is not hardcoded: the model has no evidence for a fixed count,
         * and rejecting a legitimate list would be worse than accepting the product's list.
         */
        fun grandPrix(stages: List<StageId>): Route =
            Route(stages.map { RouteStop.Stage(it) }, RouteKind.GRAND_PRIX)

        /**
         * The Knockout template built from its stage list.
         *
         * Same reasoning as [grandPrix]: stage order is data the caller supplies, while road
         * legs and a fixed size would be invented.
         */
        fun knockout(stages: List<StageId>): Route =
            Route(stages.map { RouteStop.Stage(it) }, RouteKind.KNOCKOUT)
    }
}

/**
 * Chooses the next track from a candidate list.
 *
 * The random index source is injected instead of using `kotlin.random.Random` here. A hidden
 * global source makes a "random" test either flaky or impossible; a fixture can pass `{ 0 }`,
 * `{ bound -> bound - 1 }`, or a recording function and assert the exact result.
 *
 * Fixed order walks after [next]'s previous track and wraps at the end. Random chooses from
 * the candidates that are not the previous track, so the avoid-repeat rule is structural
 * rather than a retry loop.
 *
 * The deliberate fallback: when every candidate equals the previous track, that track is
 * returned. Refusing would produce silence, and retrying would loop forever. Repeating the
 * only available track is the honest answer; the condition is documented here so it cannot be
 * mistaken for the avoid-repeat rule failing.
 */
internal class RouteSelection(
    /**
     * Returns an index in `0 until bound`. Injected for determinism; production supplies
     * `{ bound -> Random.nextInt(bound) }` at the composition root, not inside this class.
     */
    private val randomIndex: (Int) -> Int,
) {

    /** How the candidate list is traversed. */
    enum class Mode {
        /** Walk the list in order, wrapping at the end. */
        FIXED_ORDER,

        /** Pick from the candidates that are not the previous track. */
        RANDOM,
    }

    /**
     * Picks the next track, or null when [candidates] is empty.
     *
     * @param candidates tracks that may play. An empty list is an unknown road, so this
     *   returns null rather than repeating [previous] as if it were a candidate.
     * @param previous the track that just played, or null when nothing has played.
     * @param mode fixed order or random.
     * @return the chosen track; [previous] only when it is the sole candidate.
     */
    fun next(
        candidates: List<AudioAssetId>,
        previous: AudioAssetId?,
        mode: Mode,
    ): AudioAssetId? {
        if (candidates.isEmpty()) return null

        val alternatives = candidates.filter { it != previous }
        if (alternatives.isEmpty()) {
            // Every candidate was the previous track. Please see the class KDoc: returning it
            // is deliberate, not a failed search. There is no loop here to hang.
            return candidates.first()
        }

        return when (mode) {
            Mode.FIXED_ORDER -> fixedOrder(candidates, previous)
            Mode.RANDOM -> {
                val index = randomIndex(alternatives.size)
                require(index in alternatives.indices) {
                    "the random source must return an index in 0 until " +
                        "${alternatives.size}, got $index"
                }
                alternatives[index]
            }
        }
    }

    /**
     * The next candidate in list order after [previous], wrapping at the end.
     *
     * When [previous] is not in the list, the walk starts at the first candidate. The scan is
     * bounded by the list size and cannot spin when the previous track is the only candidate;
     * that case is already handled by [next].
     */
    private fun fixedOrder(
        candidates: List<AudioAssetId>,
        previous: AudioAssetId?,
    ): AudioAssetId {
        val previousAt = candidates.indexOf(previous)
        val start = if (previousAt < 0) 0 else previousAt + 1
        for (offset in candidates.indices) {
            val candidate = candidates[(start + offset) % candidates.size]
            if (candidate != previous) return candidate
        }
        // Unreachable while `alternatives` is non-empty, but returning beats throwing here.
        return candidates.first()
    }
}

/**
 * A user's route, saved at a schema [version].
 *
 * Stages are referenced by [StageId] only -- never by display name or file path. A later
 * library scan can rename a stage, move its file, or temporarily fail to find it, and the saved
 * route still points at the same stage.
 *
 * Unknown ids can only be checked against the catalogue that exists when the route is loaded,
 * so that check lives in [create]. Constructing through [create] is the load path; a direct
 * constructor is unavailable on purpose so a route cannot be loaded without stating which stage
 * ids the build knows.
 *
 * [version] is bounded by [CURRENT_VERSION]. Reading a newer file as if it were this version
 * could silently drop fields a later schema added, so the mismatch is refused instead.
 */
internal data class SavedRoute private constructor(
    val version: Int,
    val name: String,
    val route: Route,
) {

    /** The route's stops, so a loader does not need to reach through [route]. */
    val stops: List<RouteStop> get() = route.stops

    /** Stage stops in order; road endpoints are validated separately in [create]. */
    val stageIds: List<StageId> get() = route.stageIds

    init {
        require(version in 1..CURRENT_VERSION) {
            "saved route version must be between 1 and $CURRENT_VERSION, got $version"
        }
        require(name.isNotBlank()) { "a saved route needs a name" }
    }

    companion object {

        /** The only saved-route schema this build writes or reads. */
        const val CURRENT_VERSION = 1

        /**
         * Builds a saved route after checking it against the stage catalogue that currently
         * exists.
         *
         * @param knownStageIds every stage id the library can resolve. Both stage stops and
         *   road endpoints are checked; validating only stage stops would let a saved road
         *   point at a stage that no longer exists.
         * @throws IllegalArgumentException when the version is unreadable, the name is blank,
         *   or any referenced stage id is unknown. Refusing here prevents a later playback
         *   pass from silently skipping part of a route.
         */
        fun create(
            version: Int,
            name: String,
            route: Route,
            knownStageIds: Set<StageId>,
        ): SavedRoute {
            require(version in 1..CURRENT_VERSION) {
                "saved route version must be between 1 and $CURRENT_VERSION, got $version"
            }
            require(name.isNotBlank()) { "a saved route needs a name" }

            val unknown = route.referencedStageIds().filterNot { it in knownStageIds }
            require(unknown.isEmpty()) {
                "saved route '$name' references stage ids that the library does not know: " +
                    unknown.joinToString { it.value }
            }
            return SavedRoute(version, name, route)
        }
    }
}
