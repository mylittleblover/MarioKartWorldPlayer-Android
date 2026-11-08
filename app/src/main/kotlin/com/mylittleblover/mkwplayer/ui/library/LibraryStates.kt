package com.mylittleblover.mkwplayer.ui.library

import com.mylittleblover.mkwplayer.player.CatalogBrowser
import com.mylittleblover.mkwplayer.player.UnlockUi
import com.mylittleblover.mkwplayer.ui.CupVisibility

/*
 * The library page's vocabulary, deliberately kept out of Compose.
 *
 * Three of the decisions on this page are claims rather than layout -- whether a missing catalogue
 * means an empty library, whether a count includes something the cup gate hides, and whether a search
 * result can show what the list itself refuses to show. Claims are worth testing without a device,
 * so they live here and LibraryScreen only lays them out.
 *
 * No Compose import appears in this file on purpose: a @Composable cannot run on the JVM unit-test
 * classpath, and a rule that can only be checked by eye is a rule that will be broken.
 */

/**
 * The shared state names from the 21-page contract.
 *
 * [label] exists so a screen never has to invent a human word for a state, and so the same state
 * reads identically on every page that uses it.
 */
internal enum class PageState(val label: String) {
    LOADING("Loading"),
    EMPTY("No tracks yet"),
    READY("Ready"),
    MISSING_ASSET("Catalogue unavailable"),
    UNVERIFIED("Partly verified"),
    ERROR("Something went wrong"),
    REBIND_REQUIRED("Folder permission lost"),
    LOCKED("Not available yet"),
    PENDING("Waiting"),
    OFFLINE_OR_NO_PERMISSION("No permission to read your files"),
    RESTORED("Restored"),
    ;

    /**
     * Whether this page may lay out real rows.
     *
     * READY, UNVERIFIED and RESTORED all describe a catalogue the caller actually holds; they differ
     * in how far the counts can be trusted, not in whether tracks exist. Every other state has no
     * rows behind it, and drawing placeholder rows for those would be the empty page that reads as
     * success, which the contract forbids. Hiding real rows instead would be the opposite mistake:
     * a partial catalogue would then look like an empty one.
     */
    val canShowRows: Boolean
        get() = this == READY || this == UNVERIFIED || this == RESTORED

    /**
     * Whether a count can be printed at all.
     *
     * This is not the same question as [canShowRows] and the difference is the point: EMPTY has real
     * rows behind it (the catalogue knows N tracks and none are present), while MISSING_ASSET,
     * ERROR, REBIND_REQUIRED and OFFLINE_OR_NO_PERMISSION do not. Printing "0 of 650" in any of
     * those four would present a measured zero where no measurement happened -- the exact false
     * claim [headerFor] refuses to make about a missing catalogue.
     */
    val canCountTracks: Boolean
        get() = this == READY || this == UNVERIFIED || this == EMPTY || this == RESTORED
}

/**
 * What the library header says.
 *
 * Counts and [summary] are separate fields because the summary is prose and must never be parsed
 * back into numbers; a screen that derived a count by splitting this string would break the moment
 * the wording changed.
 */
internal data class LibraryHeader(
    val presentCount: Int,
    val totalCount: Int,
    val playableCount: Int,
    /** The catalogue's own partial-coverage notice, or null when it claims to be complete. */
    val coverageNotice: String?,
    /** One line of prose, ready to show. */
    val summary: String,
)

/**
 * The notice used when there is no catalogue at all.
 *
 * It names the catalogue rather than only the absence, because "no tracks" is what an empty library
 * looks like and a missing asset is a different fact about the build. If this ever gets shortened to
 * a bare "Unavailable", the distinction it exists to protect is gone.
 */
private const val NO_CATALOGUE_NOTICE =
    "No catalogue is available in this build, so the library cannot be counted."

/**
 * Derives the header from the browser state.
 *
 * A null state and an unavailable state both mean "this build cannot say what the soundtrack
 * contains", so both produce zero counts and a notice that names the missing catalogue. The counts
 * stay zero because there is nothing to count, not because a count of zero is the truth.
 *
 * Presence is counted per logical track that has at least one part present, which is the unit the
 * browser itself uses for a row. Counting parts here and tracks in the row list would print two
 * different totals for one library on one screen.
 */
internal fun headerFor(state: CatalogBrowser.State?): LibraryHeader {
    if (state == null || !state.available) {
        return LibraryHeader(
            presentCount = 0,
            totalCount = 0,
            playableCount = 0,
            coverageNotice = NO_CATALOGUE_NOTICE,
            // Deliberately not "0 of 0 tracks present": that phrasing is how a missing asset gets
            // read as a measured empty library, which is the mistake this branch exists to prevent.
            summary = "No track can be counted until the catalogue is available.",
        )
    }

    val present = state.rows.count { it.owned > 0 }
    val total = state.rows.size
    val playable = state.rows.count { it.playable }

    // An available catalogue with no rows is a third fact again: the asset parsed, and it lists
    // nothing. Saying "0 of 0 present" would fold it back into the missing-asset case.
    val summary = if (total == 0) {
        "The catalogue is present but lists no tracks."
    } else {
        "$present of $total tracks present, $playable playable in this build"
    }

    return LibraryHeader(
        presentCount = present,
        totalCount = total,
        playableCount = playable,
        coverageNotice = state.coverageNotice,
        summary = summary,
    )
}

/**
 * One category row.
 *
 * All three numbers count logical tracks, not parts: `total` is how many tracks the category shows,
 * `present` how many of those have at least one part, `playable` how many have a part this build can
 * decode. Mixing parts and tracks in one row makes a category holding one five-part stage read as
 * "1 of 5", which is a different claim about the game.
 */
internal data class CategoryRow(
    val name: String,
    val total: Int,
    val playable: Int,
    val present: Int,
    /**
     * True when the gate removed at least one track from this category.
     *
     * [total] is then a floor rather than the category's real size, and leaving the flag off would
     * present that floor as complete -- a quieter version of the leak, since the missing count still
     * implies a missing track. The flag says only that something was withheld, never what or how
     * many, which is the same restraint [CupVisibility.hiddenReason] keeps.
     *
     * Decided only by [CupVisibility], never by reading `unlock.visible` here: a second rule would
     * be a second chance to leak the cup.
     */
    val locked: Boolean,
)

/**
 * Builds the category list from the browser rows.
 *
 * The gate runs over individual course names *before* anything is counted, so no total can be one
 * too high. A category whose every track is gated then has nothing left in it and is dropped
 * entirely rather than shown as "0 tracks": a zero there looks like a fact about the soundtrack
 * instead of a fact about the gate, and an empty row also announces that something was removed.
 * Dropping it is what hiding the cup is supposed to look like.
 *
 * A browser row claiming zero parts is not a track and is removed first, so a category built only
 * from such rows disappears too. That case is separate from the gate: a future matcher change that
 * emits an empty logical row must not turn into an empty category heading.
 */
internal fun categories(
    state: CatalogBrowser.State?,
    specialCupCourses: Set<String>,
    unlock: UnlockUi?,
): List<CategoryRow> {
    if (state == null || !state.available) return emptyList()

    return state.rows
        .filter { it.total > 0 }
        .groupBy { it.kind }
        .entries
        .mapNotNull { (kind, rows) ->
            val visible = rows.filterNot { CupVisibility.hides(it.name, specialCupCourses, unlock) }
            if (visible.isEmpty()) return@mapNotNull null
            CategoryRow(
                name = kind,
                total = visible.size,
                present = visible.count { it.owned > 0 },
                playable = visible.count { it.playable },
                locked = visible.size != rows.size,
            )
        }
        .sortedBy { it.name }
}

/**
 * A search result.
 *
 * [rows] are indices into the caller's list rather than copies of the rows. A copy can go stale and
 * then be shown as if the track still existed; an index can only ever point at something the caller
 * currently has, and the caller already owns the list.
 *
 * [query] is echoed exactly as typed, so the screen shows the user what it searched for, while
 * [emptyReason] is prose that has to stand on its own.
 */
internal data class SearchResult(
    val rows: List<Int>,
    val query: String,
    val emptyReason: String?,
)

/**
 * Searches the rows by name, through the cup gate.
 *
 * The gate is applied to the matched names before the result is returned. This is the back door the
 * contract warns about: a search that filters only after the fact, or that reports "1 result" and
 * then refuses to show it, still tells the user that a hidden course exists.
 *
 * A blank query returns everything the gate allows. That is the same set the library list shows, so
 * pressing search without typing cannot reveal more than browsing already did. A blank query never
 * produces an empty reason, because an empty library is a page-level fact, not a search verdict.
 *
 * When every match was removed by the gate, the reason is built from [CupVisibility.hiddenReason]
 * and deliberately does not repeat the query: the cup must not be named, and a reason that quoted
 * the matched course name would leak it just as surely.
 */
internal fun search(
    rows: List<CatalogBrowser.Row>,
    query: String,
    specialCupCourses: Set<String>,
    unlock: UnlockUi?,
): SearchResult {
    val trimmed = query.trim()
    val matched = if (trimmed.isEmpty()) {
        rows.indices.toList()
    } else {
        rows.indices.filter { rows[it].name.contains(trimmed, ignoreCase = true) }
    }

    // Applied through the shared projection rather than a private `contains` check: a second gate is a
    // second chance to disagree with the list. Two rows sharing a name also share a verdict, so
    // filtering by the surviving names keeps the indices exact.
    val allowed = CupVisibility
        .visibleCourses(matched.map { rows[it].name }, specialCupCourses, unlock)
        .toSet()
    val visible = matched.filter { rows[it].name in allowed }
    val removedByGate = matched.size - visible.size

    val reason = when {
        visible.isNotEmpty() -> null
        trimmed.isEmpty() -> null
        removedByGate > 0 -> "Nothing can be shown for this search. " +
            (CupVisibility.hiddenReason(unlock) ?: "Some content is not available yet.")
        else -> "No track matches \"$trimmed\"."
    }

    return SearchResult(rows = visible, query = query, emptyReason = reason)
}

/**
 * One recently played entry.
 *
 * [key] is the course code, which is the identifier the playlist and the gate both understand.
 * The title is for display only and must never be used for the gate: a renamed title would silently
 * stop matching the cup set and the entry would leak through.
 */
internal data class RecentEntry(
    val key: String,
    val title: String,
    val subtitle: String,
)

/**
 * Filters and caps the recently played list.
 *
 * The gate runs over [RecentEntry.key], not the title, for the reason recorded on that type. Order is
 * the caller's, because "recent" means newest first and re-sorting a recency list destroys the only
 * property it has.
 *
 * [limit] is clamped from below rather than trusted. `List.take` throws on a negative count, and a
 * settings value of -1 would then crash a page whose whole job is to be presentational.
 */
internal fun recent(
    entries: List<RecentEntry>,
    specialCupCourses: Set<String>,
    unlock: UnlockUi?,
    limit: Int = 10,
): List<RecentEntry> {
    if (limit <= 0) return emptyList()
    return entries
        .filterNot { CupVisibility.hides(it.key, specialCupCourses, unlock) }
        .take(limit)
}

/**
 * The page-level state, so the screen does not invent its own.
 *
 * Order of precedence matters:
 *
 * 1. Busy with no catalogue is LOADING, not MISSING_ASSET: during the first read the app has not yet
 *    found out whether the asset exists, and announcing "unavailable" mid-read is a lie with a
 *    visible flash attached to it.
 * 2. No catalogue is MISSING_ASSET, never EMPTY. The distinction is the reason the browser exposes
 *    `available` separately from its row count.
 * 3. A catalogue with nothing present is EMPTY. `hasTracks` is the claim "the user has something",
 *    not "the catalogue has rows".
 * 4. A partial catalogue is UNVERIFIED even when tracks are present. READY is the state that says
 *    the page can be trusted as a whole, and a catalogue measured over a fraction of the archives
 *    cannot support that claim.
 * 5. Busy on a later refresh stays READY or UNVERIFIED. Blanking a library the user can already use
 *    would be a regression in exchange for no information.
 */
internal fun stateFor(
    hasCatalogue: Boolean,
    hasTracks: Boolean,
    isPartial: Boolean,
    busy: Boolean,
): PageState = when {
    busy && !hasCatalogue -> PageState.LOADING
    !hasCatalogue -> PageState.MISSING_ASSET
    !hasTracks -> PageState.EMPTY
    isPartial -> PageState.UNVERIFIED
    else -> PageState.READY
}

/** The page title. Kept here so the screen holds no literal copy of its own. */
internal fun libraryTitle(): String = "Library"

/** The search entry point's label. */
internal fun searchAction(): String = "Search your library"

/** Heading above the category list. */
internal fun categoriesHeading(): String = "Categories"

/** The search page's title. */
internal fun searchTitle(): String = "Search"

/** Placeholder inside the search field, so an empty box says what it is for. */
internal fun searchHint(): String = "Track name"

/** Heading above the recently played list. */
internal fun recentHeading(): String = "Recently played"

/**
 * Shown when there is no history yet.
 *
 * Says the list is empty rather than showing nothing: an empty area on a page that normally has rows
 * reads as a failure, and "you have not played anything" is a different and useful fact.
 */
internal fun noRecentMessage(): String =
    "Nothing has been played yet, so there is no recent list."

/** Shown when the gate emptied the recent list entirely. */
internal fun recentHiddenMessage(): String =
    "Nothing can be shown in this list yet. Some content is not available."

/** Shown when the page can draw rows but there are none to draw. */
internal fun emptyCategoriesMessage(): String =
    "The catalogue has no categories this page can lay out."

/**
 * Count labels.
 *
 * Built here rather than in the composable for one reason: a count is a claim, and a claim assembled
 * inline in a layout function is a claim nobody tests. The screen may only print these strings, so it
 * cannot print a count that skipped the derivation in [headerFor].
 */
internal fun presentCountLabel(header: LibraryHeader): String = "${header.presentCount} present"

/** How many of the known tracks this build can decode. */
internal fun playableCountLabel(header: LibraryHeader): String = "${header.playableCount} playable"

/** How many tracks the catalogue knows about, present or not. */
internal fun knownCountLabel(header: LibraryHeader): String = "${header.totalCount} known"

/**
 * The badge on a category that is hiding some of its tracks.
 *
 * It says a subtraction happened without saying what was subtracted or how many, matching the
 * restraint [CupVisibility.hiddenReason] keeps: a badge naming the cup is the same announcement the
 * gate exists to prevent.
 */
internal fun hiddenRowsBadge(): String = "Some tracks are not available yet"

/**
 * The line under a search result.
 *
 * Built from the row the caller currently holds, never from the query text: a result that described
 * what was typed rather than what is owned would claim the user has a track they do not.
 */
internal fun rowPartsLabel(row: CatalogBrowser.Row): String =
    "${row.owned} of ${row.total} part${if (row.total == 1) "" else "s"} present"

/**
 * The line under a category.
 *
 * Reads present-out-of-total and playable, never a bare count: "12" alone does not say 12 of what.
 * The zero branch exists because this type can be constructed by a caller that bypassed [categories],
 * and "0 of 0 present" would then look like a measured fact.
 */
internal fun categoryDetail(row: CategoryRow): String = if (row.total == 0) {
    "No tracks known in this category."
} else {
    "${row.present} of ${row.total} present, ${row.playable} playable in this build"
}

/**
 * The words a non-ready page shows instead of rows.
 *
 * Every branch is a sentence with a fact in it, because a spinner or a blank region is indistinguishable
 * from success. Several branches exist only to keep two different failures apart -- a missing asset,
 * a permission loss and an unreadable library all look like "nothing" if the page only draws an icon.
 *
 * READY returns null: a working page needs no explanation.
 */
internal fun pageMessage(state: PageState, header: LibraryHeader): String? = when (state) {
    PageState.READY -> null
    PageState.LOADING ->
        "Reading the catalogue and the files you picked. Nothing is counted yet."
    PageState.EMPTY -> if (header.totalCount == 0) {
        "The catalogue is present but lists no tracks, so there is nothing to count."
    } else {
        "None of the ${header.totalCount} tracks in the catalogue are present yet. " +
            "Add files to build your library."
    }
    PageState.MISSING_ASSET ->
        // Deliberately does not repeat the header notice, which already says the catalogue is absent.
        // This line is the interpretation instead: no count is shown because a count would be invented.
        "This is not an empty library. Without a catalogue the app cannot tell what the soundtrack " +
            "contains, so it shows no count rather than a zero."
    PageState.UNVERIFIED ->
        "The catalogue is only partly verified, so these numbers are a floor rather than the whole " +
            "soundtrack."
    PageState.ERROR ->
        "The library could not be read, so no count can be shown. A failed read is not an empty library."
    PageState.REBIND_REQUIRED ->
        "This app can no longer read the files you picked. Choose your music folder again; " +
            "nothing has been deleted."
    PageState.LOCKED ->
        "This part of the library is not available yet."
    PageState.PENDING ->
        "A step is still waiting to finish, so the library is not final."
    PageState.OFFLINE_OR_NO_PERMISSION ->
        "The files are on storage this app cannot read right now. Nothing is counted until access returns."
    PageState.RESTORED ->
        "Restored from your last session. Counts may be out of date until the files are checked again."
}
