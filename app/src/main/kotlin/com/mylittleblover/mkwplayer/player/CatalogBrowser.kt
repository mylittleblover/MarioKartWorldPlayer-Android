package com.mylittleblover.mkwplayer.player

import com.mylittleblover.mkwplayer.library.CatalogLoader
import com.mylittleblover.mkwplayer.library.CatalogMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The catalogue browser's view of which entries the user already has.
 *
 * `CatalogMatcher` produces a full report over 650 entries, which is far more than a list row needs.
 * This reduces it to what is displayed and keeps the one claim that must never be dropped: the
 * catalogue is partial.
 *
 * Kept separate from the view model so the reduction is testable without Android.
 */
internal class CatalogBrowser(initial: CatalogLoader.Catalog?) {

    /** One row in the browser. */
    data class Row(
        /** Logical track name, from the shared prefix. */
        val name: String,
        val kind: String,
        /** How many of its parts the user has. */
        val owned: Int,
        val total: Int,
        /** True when every part is present. */
        val complete: Boolean,
        /** True when at least one present part can actually be decoded. */
        val playable: Boolean,
    )

    data class State(
        val rows: List<Row> = emptyList(),
        /** Where the catalogue came from; null means no catalogue is bundled. */
        val available: Boolean = false,
        /** Partial-coverage notice, carried through every reduction. */
        val coverageNotice: String? = null,
        /** One-line count, e.g. `12 of 650 present`. */
        val summary: String = "",
    )

    private val catalog: CatalogLoader.Catalog? = initial
    private val _state = MutableStateFlow(buildState(emptyList(), initial))
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Rebuilds the rows from the files the user has.
     *
     * Takes measured files rather than names so the reduction inherits the matcher's rule: a name
     * match alone does not count as ownership.
     */
    fun refresh(files: List<CatalogMatcher.PresentFile>) {
        _state.value = buildState(files, catalog)
    }

    private fun buildState(
        files: List<CatalogMatcher.PresentFile>,
        catalog: CatalogLoader.Catalog?,
    ): State {
        if (catalog == null) {
            return State(available = false, coverageNotice = null, summary = "No catalogue bundled")
        }

        val report = CatalogMatcher.match(catalog, files)
        val rows = CatalogMatcher.logicalRows(report).map { logical ->
            Row(
                name = logical.prefix,
                kind = logical.kind.name,
                owned = logical.ownedCount,
                total = logical.total,
                complete = logical.isComplete,
                playable = logical.entries.any {
                    it.status == CatalogMatcher.Status.OWNED && it.entry.playable
                },
            )
        }

        return State(
            rows = rows,
            available = true,
            // Carried through: a screen that dropped this would present 650 rows as the whole
            // soundtrack even once every row matched.
            coverageNotice = if (catalog.isPartial) {
                "Partial catalogue, ${catalog.archivesRead} of ${catalog.archivesExpected} archives read."
            } else {
                null
            },
            summary = "${report.owned.size} of ${report.matches.size} present",
        )
    }
}
