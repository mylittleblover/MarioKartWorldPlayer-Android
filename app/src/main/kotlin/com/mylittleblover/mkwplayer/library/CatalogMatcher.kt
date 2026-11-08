package com.mylittleblover.mkwplayer.library

import com.mylittleblover.mkwplayer.registry.AudioAssetId
import com.mylittleblover.mkwplayer.registry.ResourceKind
import com.mylittleblover.mkwplayer.registry.TrackClassifier

/**
 * Matches the bundled catalogue against the files a user actually has.
 *
 * The catalogue says what exists; the library says what is present. Until they are joined, both
 * are only half useful: a 650-row catalogue where every row looks identical, and a file list with
 * no idea what it is missing.
 *
 * Matching is by **name first, then measurement**. A name match alone is not enough -- a user can
 * rename a file, and two stages can ship colliding names -- so a name match is confirmed against
 * the catalogue's own sample rate, channel count and frame count. A file whose measurement
 * disagrees is reported as a mismatch rather than silently claimed as present, because "you have
 * this track" is a claim the app should not make on a guess.
 */
internal object CatalogMatcher {

    /** How a catalogue entry relates to what the user has. */
    enum class Status {
        /** A file matches this entry by name and measurement. */
        OWNED,

        /** No file matches this entry. */
        MISSING,

        /** A file matches this entry by name but not by measurement. */
        MISMATCH,
    }

    /** What the user's file provides, independent of any name. */
    data class PresentFile(
        val fileName: String,
        val frames: Long,
        val sampleRate: Int,
        val channels: Int,
        /** Digest, when the caller hashed the bytes. Optional because a list can be matched without it. */
        val assetId: AudioAssetId? = null,
    ) {
        /** The catalogue-style stem, so a name comparison needs no per-call work. */
        val stem: String get() = fileName.substringBeforeLast('.')
    }

    data class Match(
        val entry: CatalogLoader.Entry,
        val status: Status,
        /** The file that satisfied or contradicted the entry, when one did. */
        val file: PresentFile? = null,
        /** Why a mismatch was reported; null for owned and missing. */
        val detail: String? = null,
    )

    data class Report(
        val matches: List<Match>,
        /** Files that matched no catalogue entry at all. */
        val unmatched: List<PresentFile>,
        /** True when the catalogue itself is partial, which the UI must keep saying. */
        val catalogIsPartial: Boolean,
    ) {
        val owned: List<Match> get() = matches.filter { it.status == Status.OWNED }
        val missing: List<Match> get() = matches.filter { it.status == Status.MISSING }
        val mismatched: List<Match> get() = matches.filter { it.status == Status.MISMATCH }

        val ownedPlayable: List<Match> get() = owned.filter { it.entry.playable }
        val ownedUnplayable: List<Match> get() = owned.filter { !it.entry.playable }

        /** One line for the library header. */
        fun summary(): String =
            "${owned.size} of ${matches.size} entries present, " +
                "${missing.size} missing, ${mismatched.size} mismatched"
    }

    /**
     * Joins a catalogue against present files.
     *
     * A file is consumed by at most one entry. Two catalogue entries that share a name (which
     * happens: one asset name appears in several archive entries) would otherwise both claim the
     * same file and both look owned, overstating what the user has.
     */
    fun match(catalog: CatalogLoader.Catalog, files: List<PresentFile>): Report {
        // Tracked by position, not by value: two entries can be satisfied by two files that look
        // identical (same name, same measurements), and a value-based set would collapse them into
        // one and under-report ownership.
        val byStem = files.withIndex().groupBy { it.value.stem }
        val consumed = mutableSetOf<Int>()
        val matches = ArrayList<Match>(catalog.entries.size)

        for (entry in catalog.entries) {
            val candidates = byStem[entry.name].orEmpty().filterNot { it.index in consumed }
            if (candidates.isEmpty()) {
                matches += Match(entry = entry, status = Status.MISSING)
                continue
            }

            val chosen = candidates.first()
            consumed += chosen.index
            val file = chosen.value
            val disagreement = disagreement(entry, file)
            matches += if (disagreement == null) {
                Match(entry = entry, status = Status.OWNED, file = file)
            } else {
                Match(
                    entry = entry,
                    status = Status.MISMATCH,
                    file = file,
                    detail = disagreement,
                )
            }
        }

        return Report(
            matches = matches,
            unmatched = files.filterIndexed { index, _ -> index !in consumed },
            catalogIsPartial = catalog.isPartial,
        )
    }

    /**
     * Describes how a file contradicts its catalogue entry, or null when it agrees.
     *
     * Only measured fields are compared. A file with unknown measurements (zero) is not called a
     * mismatch: absence of evidence is not contradiction, and rejecting such a file would make
     * every partially-read listing useless.
     */
    private fun disagreement(entry: CatalogLoader.Entry, file: PresentFile): String? {
        if (file.sampleRate > 0 && entry.sampleRate > 0 && file.sampleRate != entry.sampleRate) {
            return "sample rate ${file.sampleRate} Hz, catalogue says ${entry.sampleRate} Hz"
        }
        if (file.channels > 0 && entry.channels > 0 && file.channels != entry.channels) {
            return "${file.channels} channels, catalogue says ${entry.channels}"
        }
        if (file.frames > 0 && entry.frames > 0 && file.frames != entry.frames) {
            return "${file.frames} frames, catalogue says ${entry.frames}"
        }
        return null
    }

    /**
     * Groups matches by logical track, merging a stage's parts into one row.
     *
     * Without this the catalogue lists a course theme five times, once per part, and the user has
     * no way to tell that they are one listen. The grouping is the same prefix rule the flow
     * discovery uses, so a track shown here is a track that can actually be opened.
     */
    data class LogicalRow(
        val prefix: String,
        val kind: ResourceKind,
        val entries: List<Match>,
    ) {
        val ownedCount: Int get() = entries.count { it.status == Status.OWNED }
        val total: Int get() = entries.size
        val isComplete: Boolean get() = ownedCount == total

        /** Unowned but playable entries, for a "what am I missing" line. */
        val missingPlayable: Int
            get() = entries.count { it.status != Status.OWNED && it.entry.playable }
    }

    /** Merges part-level matches into logical rows, ordered by prefix. */
    fun logicalRows(report: Report): List<LogicalRow> {
        val grouped = report.matches.groupBy { StageFlowDiscovery.prefixOf(it.entry.name) }
        return grouped.map { (prefix, entries) ->
            LogicalRow(
                prefix = prefix,
                // Kind comes from the primary entry rather than the parts; parts of a course
                // theme classify as COURSE too, so this is stable.
                kind = TrackClassifier.classify("$prefix.bwav"),
                entries = entries.sortedBy { it.entry.name },
            )
        }.sortedBy { it.prefix }
    }
}
