package com.mylittleblover.mkwplayer.ui.routes

import com.mylittleblover.mkwplayer.player.UnlockUi
import com.mylittleblover.mkwplayer.routes.Route
import com.mylittleblover.mkwplayer.routes.RouteStop
import com.mylittleblover.mkwplayer.routes.SavedRoute
import com.mylittleblover.mkwplayer.routes.StageNode
import com.mylittleblover.mkwplayer.ui.CupVisibility

/*
 * View state for UI-07 (world map) and UI-08 (my routes).
 *
 * Plain Kotlin, no Compose types: the decisions below are the ones a screenshot cannot check, and a
 * screen must not be able to make them differently. Two of them are not presentation choices at all:
 *
 *  - a stage with no established position still has to appear, or it looks like the stage does not
 *    exist (the same reasoning the route model uses for a null position);
 *  - a gated cup course must not appear at all. A greyed pin is still an announcement that
 *    something is hidden, which is why [CupVisibility] is asked rather than reimplemented here.
 */

/**
 * What the map can honestly draw.
 *
 * [PARTIAL] is the state that matters. Some stages have an established position and some do not; a
 * map that quietly shows only the placed ones looks complete, and the user has no way to tell that
 * the rest are missing rather than absent from the game. The label therefore says which case this
 * is, and the screen shows it next to the pins.
 *
 * [NO_BASEMAP] is different again: no stage has a position, so there is nothing to draw and a list
 * is the only honest view.
 */
internal enum class MapState(val label: String) {
    NO_BASEMAP("No map data: no stage has an established position."),
    PARTIAL("Partial map: some stages have no established position."),
    READY("All stages have an established position."),
}

/**
 * One stage marker.
 *
 * [xFraction] and [yFraction] are 0..1 positions inside the map frame, produced by the
 * normalisation documented on [pins]. They are only meaningful when [hasPosition] is true.
 *
 * [locked] is true when the pin's stage is gated and must not be opened. [pins] never returns such
 * a pin: a gated stage is removed rather than greyed, because a greyed marker announces that a
 * hidden course exists. The flag stays on the type because the screen also renders pin lists it did
 * not build itself (a route preview, a restored snapshot); such a pin must not become a tap target
 * that leads into playback the gate would then have to refuse.
 */
internal data class MapPin(
    val id: String,
    val label: String,
    val xFraction: Float,
    val yFraction: Float,
    val hasPosition: Boolean,
    val locked: Boolean,
)

/**
 * The rectangle a map position is normalised against, in the game map's own units.
 *
 * It is a parameter rather than a constant because the map's authored size is not established
 * anywhere in the model: positions are deliberately not latitude/longitude, and a fixed default
 * rectangle here would be a projection nobody measured. A caller with measured extents passes one;
 * otherwise [pins] derives the box from the data it was given.
 */
internal data class MapBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {

    init {
        require(minX.isFinite() && maxX.isFinite() && minY.isFinite() && maxY.isFinite()) {
            "map bounds must be finite, got ($minX, $minY)..($maxX, $maxY)"
        }
        require(minX <= maxX) {
            "map bounds must have minX <= maxX, got $minX..$maxX"
        }
        require(minY <= maxY) {
            "map bounds must have minY <= maxY, got $minY..$maxY"
        }
    }

    companion object {
        /**
         * The smallest box containing every positioned stage, or null when none has a position.
         *
         * Positions are in the map's own units, so this is the extent the data actually spans --
         * not a claim about the game's map size. Stages without a position contribute nothing:
         * treating them as the origin would drag the box to a coordinate the model says is real.
         */
        fun around(nodes: List<StageNode>): MapBounds? {
            val placed = nodes.mapNotNull { it.position }
            if (placed.isEmpty()) return null
            return MapBounds(
                minX = placed.minOf { it.x },
                minY = placed.minOf { it.y },
                maxX = placed.maxOf { it.x },
                maxY = placed.maxOf { it.y },
            )
        }
    }
}

/**
 * Turns stage nodes into map pins.
 *
 * ## Normalisation
 *
 * Each positioned stage is mapped linearly onto 0..1 against a bounding box. The box is [bounds]
 * when the caller states one. By default it is the axis-aligned box around every positioned node in
 * [nodes] that is allowed to be drawn, i.e. `(min x, min y) .. (max x, max y)` in the game map's own
 * units -- the extent the data actually spans. Gated cup stages are removed first and therefore do
 * not stretch the box, so the frame does not hint at where a hidden stage sits. For nodes at
 * (10, 20) and (110, 220) the box is x 10..110, y 20..220, so the first pin lands at (0.0, 0.0) and
 * the second at (1.0, 1.0). A node at exactly (0, 0) is placed like any other: the route model
 * treats the origin as a real coordinate, so only a null position means "unknown".
 *
 * An axis with no spread (one positioned stage, or every stage on the same line) has a zero-length
 * span; that axis is reported as 0.5 rather than divided by zero. Fractions are clamped to 0..1,
 * which matters when [bounds] is explicit and a node lies outside it.
 *
 * ## What still appears
 *
 * A node whose position is null still produces a pin, with [MapPin.hasPosition] false and the
 * neutral centre (0.5, 0.5) in the fraction fields. Omitting it would make the stage look
 * nonexistent; filling in 0,0 would claim the map origin for it. The screen places pins from
 * [MapPin.hasPosition], never from the fractions alone, and lists the unplaced ones beside the map.
 *
 * ## Gating
 *
 * Cup courses are removed before layout, through [CupVisibility.hides], so a locked cup cannot be
 * reached from the map while its card is hidden elsewhere. Every pin returned here therefore has
 * [MapPin.locked] false.
 *
 * @param nodes the stages to draw, filtered by the gate first.
 * @param specialCupCourses the set the caller believes belongs to the cup, passed to
 *   [CupVisibility.hides] rather than guessed from names.
 * @param unlock the cup state; null is treated as locked, the safe default.
 * @param bounds the rectangle to normalise against; null derives it from the visible nodes, as
 *   described above.
 */
internal fun pins(
    nodes: List<StageNode>,
    specialCupCourses: Set<String>,
    unlock: UnlockUi?,
    bounds: MapBounds? = null,
): List<MapPin> {
    val visible = nodes.filterNot { CupVisibility.hides(it.id.value, specialCupCourses, unlock) }
    val box = bounds ?: MapBounds.around(visible)

    return visible.map { node -> pinOf(node, box) }
}

/** One pin, placed against [box] when both the node and the box state a position. */
private fun pinOf(node: StageNode, box: MapBounds?): MapPin {
    val position = node.position
    val x = if (position != null && box != null) {
        fractionOf(position.x, box.minX, box.maxX)
    } else {
        UNPLACED_FRACTION
    }
    val y = if (position != null && box != null) {
        fractionOf(position.y, box.minY, box.maxY)
    } else {
        UNPLACED_FRACTION
    }
    return MapPin(
        id = node.id.value,
        label = node.displayName,
        xFraction = x,
        yFraction = y,
        hasPosition = position != null,
        locked = false,
    )
}

/**
 * How much of the map can be drawn.
 *
 * Describes the position data, not the visible pin count: cup gating removes pins the screen must
 * not show, and that must not make a fully placed map look incomplete. An empty list is
 * [MapState.NO_BASEMAP] -- there is no data to explain yet.
 */
internal fun mapState(nodes: List<StageNode>): MapState = when {
    nodes.none { it.hasKnownPosition } -> MapState.NO_BASEMAP
    nodes.any { !it.hasKnownPosition } -> MapState.PARTIAL
    else -> MapState.READY
}

/** One row of the fixed-order stop list in the route editor. */
internal data class RouteStopRow(
    /**
     * Zero-based position in the route's stop list, so a removal action can address the same list
     * the row came from. The screen shows `index + 1` as the sequence number.
     */
    val index: Int,
    val label: String,
    /** [KIND_STAGE] or [KIND_ROAD]. A road is a leg, not a stage with a missing name. */
    val kind: String,
    val locked: Boolean,
    val problem: String?,
) {
    companion object {
        const val KIND_STAGE = "Stage"
        const val KIND_ROAD = "Road"
    }
}

/**
 * The stop list a route editor shows.
 *
 * A route is a fixed linear sequence; there is no branch to draw and no reordering rule beyond what
 * [Route] itself enforces.
 *
 * The label is the stop's stable id (or `a -> b` for a road) rather than a display name: a route
 * stores ids only, and a name would have to be looked up somewhere this function is not given. The
 * id is what the saved form itself contains, so the row never invents a name the route cannot
 * resolve.
 *
 * A stop that references a gated cup course is flagged instead of dropped, because dropping it would
 * renumber the route and hide why playback is refused. Its [RouteStopRow.label] is replaced with a
 * neutral phrase as well as its [RouteStopRow.problem] being non-null: the course id would otherwise
 * leak the very thing [CupVisibility] hides, and naming the cup is the same leak as showing it.
 */
internal fun routeRows(
    route: Route?,
    specialCupCourses: Set<String>,
    unlock: UnlockUi?,
): List<RouteStopRow> {
    if (route == null) return emptyList()

    return route.stops.mapIndexed { index, stop ->
        when (stop) {
            is RouteStop.Stage -> {
                val locked = CupVisibility.hides(stop.stage.value, specialCupCourses, unlock)
                RouteStopRow(
                    index = index,
                    label = if (locked) NOT_AVAILABLE_LABEL else stop.stage.value,
                    kind = RouteStopRow.KIND_STAGE,
                    locked = locked,
                    problem = if (locked) lockedProblem(unlock) else null,
                )
            }

            is RouteStop.Road -> {
                val a = stop.connection.a.value
                val b = stop.connection.b.value
                val locked = CupVisibility.hides(a, specialCupCourses, unlock) ||
                    CupVisibility.hides(b, specialCupCourses, unlock)
                RouteStopRow(
                    index = index,
                    label = if (locked) NOT_AVAILABLE_LABEL else "$a -> $b",
                    kind = RouteStopRow.KIND_ROAD,
                    locked = locked,
                    problem = if (locked) lockedProblem(unlock) else null,
                )
            }
        }
    }
}

/** One saved route in the list page. */
internal data class SavedRouteRow(
    /**
     * Stable-ish handle for list keys.
     *
     * [SavedRoute] carries no identifier of its own -- only a schema version, a name and the stop
     * sequence -- so the id is derived from exactly those. Two saved routes with identical content
     * therefore share an id, which is the limit of what the model can distinguish.
     */
    val id: String,
    val name: String,
    /** Stops, roads included: the same unit the editor counts and the route rejects below two. */
    val stopCount: Int,
    val problem: String?,
)

/**
 * The saved-route list.
 *
 * A route that references a course the cup gate hides gets a [SavedRouteRow.problem]. Without one the
 * row would look playable, and the play attempt would silently substitute another course instead of
 * playing what the user saved -- the failure this whole page exists to prevent.
 */
internal fun savedRows(
    routes: List<SavedRoute>,
    specialCupCourses: Set<String>,
    unlock: UnlockUi?,
): List<SavedRouteRow> = routes.map { saved ->
    val blocked = saved.route.referencedStageIds().any { stage ->
        CupVisibility.hides(stage.value, specialCupCourses, unlock)
    }

    SavedRouteRow(
        id = savedRouteId(saved),
        name = saved.name,
        stopCount = saved.stops.size,
        problem = if (blocked) {
            lockedProblem(unlock) + " " + SAVED_ROUTE_BLOCKED_SUFFIX
        } else {
            null
        },
    )
}

/**
 * The route draft and whether it may be saved.
 *
 * [canSave] and [blockingReason] default to values derived from [stops] with [Route]'s own rules, so
 * a draft built with `RouteEditor(stops)` cannot show a save button that the model would refuse. The
 * two defaults come from one pure function and therefore cannot disagree.
 *
 * Both remain settable because a restored snapshot must be shown exactly as it was stored, including
 * a draft the current build would now refuse. Use [of] when the intent is explicitly "derive them".
 *
 * The wording is mirrored from [Route] on purpose. A second phrasing of the same rule would let the
 * editor say one thing while the route refuses for another reason.
 */
internal data class RouteEditor(
    val stops: List<RouteStop>,
    val canSave: Boolean = routeProblem(stops) == null,
    val blockingReason: String? = routeProblem(stops),
) {
    companion object {
        /** A draft whose save state is derived from its stops. Same as the default constructor. */
        fun of(stops: List<RouteStop>): RouteEditor = RouteEditor(stops)
    }
}

/**
 * Why [stop] cannot be appended to [editor], or null when it can.
 *
 * The only rule checked here is [Route]'s duplicate rule: a stage may not follow the same stage,
 * counting over stage visits with roads removed, so `Coaster -> road -> Coaster` is refused even
 * though the raw stop list has a road in between.
 *
 * The two-stop minimum is deliberately not applied per add. It is a rule about saving a route, and
 * enforcing it on every append would make the first stop impossible to add, so the editor could
 * never leave empty. [RouteEditor.blockingReason] carries that rule with the same wording.
 */
internal fun canAdd(editor: RouteEditor, stop: RouteStop): String? =
    duplicateStageProblem(editor.stops + stop)

// ---------------------------------------------------------------- helpers

/** Neutral fraction for a pin that has no position. Never used to place a pin. */
private const val UNPLACED_FRACTION = 0.5f

/** Label for a stop whose identity may not be shown. Says that it cannot be used, not why. */
private const val NOT_AVAILABLE_LABEL = "Not available"

/** Follows [lockedProblem] on a saved route; says the route will not play as saved. */
private const val SAVED_ROUTE_BLOCKED_SUFFIX = "This saved route cannot be played as saved."

private fun fractionOf(value: Double, min: Double, max: Double): Float {
    val span = max - min
    if (span <= 0.0) return 0.5f
    return ((value - min) / span).toFloat().coerceIn(0f, 1f)
}

/**
 * The reason a gated stop is shown instead of its label.
 *
 * Reuses [CupVisibility.hiddenReason] so the map, the routes and every other surface say the same
 * thing, and never names the cup.
 */
private fun lockedProblem(unlock: UnlockUi?): String =
    CupVisibility.hiddenReason(unlock) ?: NOT_AVAILABLE_LABEL

/**
 * [Route]'s own duplicate rule, message included.
 *
 * The wording is copied verbatim from the `require` in [Route]'s `init`: a reworded rule would read
 * as a different rule, and the editor must not refuse for a reason the model never states.
 */
private fun duplicateStageProblem(stops: List<RouteStop>): String? {
    val stageVisits = stops.mapNotNull { (it as? RouteStop.Stage)?.stage }
    val repeated = stageVisits.zipWithNext().firstOrNull { (left, right) -> left == right }
    return repeated?.let {
        "a route must not visit the same stage twice in a row, got '${it.first.value}'"
    }
}

/**
 * [Route]'s save preconditions, message included, or null when the stops would build a route.
 *
 * Mirrors the `init` block of [Route]: at least two stops first, then the duplicate rule.
 */
private fun routeProblem(stops: List<RouteStop>): String? {
    if (stops.size < 2) return "a route needs at least two stops, got ${stops.size}"
    return duplicateStageProblem(stops)
}

/**
 * A content-derived id for a saved route.
 *
 * The model stores no id, so deriving one from the version, the name and the stop sequence is the
 * most it can support. Two routes that differ in any of those get different ids.
 */
private fun savedRouteId(saved: SavedRoute): String {
    val stops = saved.stops.joinToString(separator = ",") { stop ->
        when (stop) {
            is RouteStop.Stage -> "s:${stop.stage.value}"
            is RouteStop.Road -> "r:${stop.connection.a.value}-${stop.connection.b.value}"
        }
    }
    return "v${saved.version}|${saved.name}|$stops"
}
