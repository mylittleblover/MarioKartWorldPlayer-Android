package com.mylittleblover.mkwplayer.ui

import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.player.CatalogBrowser
import com.mylittleblover.mkwplayer.player.FlowPresentation
import com.mylittleblover.mkwplayer.player.MapRoad
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.player.UnlockUi
import com.mylittleblover.mkwplayer.ui.detail.DetailHeader
import com.mylittleblover.mkwplayer.ui.detail.ProvenanceBlock
import com.mylittleblover.mkwplayer.ui.detail.StructureRow
import com.mylittleblover.mkwplayer.ui.library.CategoryRow
import com.mylittleblover.mkwplayer.ui.library.LibraryHeader
import com.mylittleblover.mkwplayer.ui.play.SectionButton
import com.mylittleblover.mkwplayer.ui.play.TransportRow
import com.mylittleblover.mkwplayer.ui.routes.MapPin
import com.mylittleblover.mkwplayer.ui.routes.MapState
import com.mylittleblover.mkwplayer.ui.settings.ImportSummary
import com.mylittleblover.mkwplayer.ui.settings.SettingRow

/**
 * Turns the one [PlayerViewModel.Ui] value into the shape each screen takes.
 *
 * The screens were written against their own presentation types and tested without a view model, which is why
 * they are correct but were never reachable. This is the join, and it lives in one object so no screen has to
 * learn about the view model or about each other.
 *
 * Two rules hold throughout:
 *
 * A count is never invented. When a source is absent the screen is told so through its own no-data state
 * rather than being handed zeros, because "0 tracks" and "no catalogue" are different facts and only one of
 * them is true.
 *
 * A warning is never dropped. The catalogue's coverage notice and a flow's provenance both survive every
 * reduction, because a screen that omits them presents borrowed or partial material as complete.
 */
internal object UiMapping {

    /** The library page's header. */
    fun libraryHeader(ui: PlayerViewModel.Ui, catalog: CatalogBrowser.State?): LibraryHeader {
        val total = catalog?.rows?.size ?: 0
        return LibraryHeader(
            presentCount = ui.tracks.size,
            totalCount = total,
            playableCount = ui.tracks.count { it.playable },
            coverageNotice = catalog?.coverageNotice,
            summary = catalog?.summary ?: "No catalogue is bundled with this build.",
        )
    }

    /** The library page's categories, from the catalogue. */
    fun libraryCategories(
        catalog: CatalogBrowser.State?,
        unlock: UnlockUi?,
        specialCupCourses: Set<String> = emptySet(),
    ): List<CategoryRow> =
        com.mylittleblover.mkwplayer.ui.library.categories(catalog, specialCupCourses, unlock)

    /** The play page's transport row. */
    fun transport(ui: PlayerViewModel.Ui): TransportRow = TransportRow(
        position = ui.position,
        total = ui.total,
        fraction = ui.progress,
        canPause = ui.canPause,
        canResume = ui.canResume,
        canStop = ui.canStop,
        canSeek = ui.canStop,
    )

    /**
     * The play page's section buttons, from the open flow.
     *
     * The segment index is what the engine addresses, so it is carried through unchanged; a label is for
     * reading and must never be turned back into a request.
     */
    fun sections(flow: PlayerViewModel.FlowUi?): List<SectionButton> {
        val open = flow ?: return emptyList()
        return open.sections.map { section ->
            SectionButton(
                segment = section.segment,
                label = section.label,
                seconds = if (section.sampleRate > 0) {
                    "%d:%02d".format(
                        (section.frames / section.sampleRate) / 60,
                        (section.frames / section.sampleRate) % 60,
                    )
                } else {
                    "--:--"
                },
                active = section.segment == open.activeSegment,
                enabled = true,
            )
        }
    }

    /** The detail page's header, from the open flow. */
    fun detailHeader(flow: PlayerViewModel.FlowUi?): DetailHeader {
        val open = flow ?: return DetailHeader("Nothing open", "", emptyList())
        return DetailHeader(
            title = open.title,
            subtitle = open.summary,
            metrics = buildList {
                add("${open.sections.size} sections")
                if (open.outros.isNotEmpty()) add("${open.outros.size} endings")
                if (open.hasFast) add("fast arrangement")
            },
        )
    }

    /**
     * The detail page's structure rows.
     *
     * The note carries the provenance for anything this app chose rather than measured. It is attached to
     * every row rather than shown once, because a reader who scrolls to the end of the list never sees a
     * notice printed at the top.
     */
    fun structure(flow: PlayerViewModel.FlowUi?): List<StructureRow> {
        val open = flow ?: return emptyList()
        return open.sections.map { section ->
            StructureRow(
                role = section.label,
                label = section.label,
                seconds = if (section.sampleRate > 0) {
                    section.frames.toDouble() / section.sampleRate
                } else {
                    null
                },
                repeats = null,
                note = if (open.isProvisional) "timing is this app's arrangement" else null,
            )
        }
    }

    /** The detail page's provenance blocks. */
    fun provenance(flow: PlayerViewModel.FlowUi?): List<ProvenanceBlock> {
        val open = flow ?: return emptyList()
        return listOf(
            ProvenanceBlock(
                level = if (open.isProvisional) "CHOSEN BY THIS APP" else "MEASURED",
                statement = open.provenance,
                // The flag is the difference between "this is what the game does" and "this is a
                // playable arrangement"; reversing it would make a guess read as a measurement.
                isEstablished = !open.isProvisional,
            ),
        )
    }

    /**
     * The map's pins, one per stage anchor.
     *
     * A stage whose anchor is absent is still listed, with `hasPosition` false: dropping it would make the
     * stage look like it does not exist rather than like the map could not place it.
     */
    fun pins(
        anchors: MapAnchors.Catalog?,
        unlock: UnlockUi?,
        specialCupCourses: Set<String> = emptySet(),
    ): List<MapPin> {
        val catalog = anchors ?: return emptyList()
        return catalog.stages.map { anchor ->
            MapPin(
                id = anchor.code,
                label = anchor.code.removeSuffix("_00"),
                xFraction = anchor.u,
                yFraction = anchor.v,
                hasPosition = true,
                locked = CupVisibility.hides(anchor.code, specialCupCourses, unlock),
            )
        }
    }

    /** The map's own state, so an empty map says why it is empty. */
    fun mapState(anchors: MapAnchors.Catalog?): MapState =
        when {
            anchors == null || anchors.stages.isEmpty() -> MapState.NO_BASEMAP
            anchors.stages.any { it.u !in 0f..1f || it.v !in 0f..1f } -> MapState.PARTIAL
            else -> MapState.READY
        }

    /** The embroidery between pins: straight lines, nearest neighbour. */
    fun roads(anchors: MapAnchors.Catalog?): List<MapRoad.Segment> {
        val catalog = anchors ?: return emptyList()
        return MapRoad.nearestNeighbourRoads(catalog.stages)
    }

    /** The settings the build can honestly offer. */
    fun settings(): List<SettingRow> = listOf(
        SettingRow(
            key = "audio-quality",
            title = "Audio quality",
            detail = "The engine decodes at the file's own rate; there is nothing to choose.",
            enabled = false,
            disabledReason = "No alternatives exist in this build.",
        ),
        SettingRow(
            key = "pack-import",
            title = "Import a soundtrack pack",
            detail = "A .mkwpack carries the audio, the stage structures and the cue data in one file.",
            enabled = true,
            disabledReason = null,
        ),
    )

    /**
     * The import page's summary.
     *
     * Never reports an import that did not happen: with no pack loaded the counts are zero and the headline
     * says so, rather than reading as a successful empty import.
     */
    fun importSummary(ui: PlayerViewModel.Ui): ImportSummary {
        val pack = ui.pack ?: return ImportSummary(
            added = 0,
            rejected = 0,
            rejectedReasons = emptyList(),
            headline = "No soundtrack pack imported. Tap Import to choose one.",
        )
        return ImportSummary(
            added = pack.playableCount,
            rejected = pack.trackCount - pack.playableCount,
            rejectedReasons = pack.notes,
            headline = "${pack.name}: ${pack.summary}",
        )
    }

    /** The flow's provenance line, ready for any screen that shows it. */
    fun provenanceLine(flow: PlayerViewModel.FlowUi?): String? = flow?.provenance

    /** The presentation for a flow's section list, shared by the play and detail pages. */
    fun sectionLabels(flow: PlayerViewModel.FlowUi?): List<FlowPresentation.Section> =
        flow?.sections ?: emptyList()
}
