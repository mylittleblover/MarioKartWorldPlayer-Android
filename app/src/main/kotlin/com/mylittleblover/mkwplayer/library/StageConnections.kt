package com.mylittleblover.mkwplayer.library

import org.json.JSONObject

/**
 * The stage-to-stage connections the game itself defines.
 *
 * Verified from Mario Kart World v1.8.0 (`RSDB/Course.Product.180.rstbl` & `CourseAreaGParam.CourseEx`):
 * 202 directed inter-stage connections (137 `InterStageEx1` + 65 `InterStageEx2`), 29 source stages,
 * 30 destination stages. The 12 multi-stop `LongRally` Knockout Tour course IDs are excluded because
 * they are 6-stage rallies rather than direct single-hop connections.
 *
 * Two facts about this data are load-bearing and are preserved rather than tidied:
 *
 * - **It is directed.** 96 pairs are bidirectional (192 edges) and 10 edges are strictly one-way
 *   (1 `InterStageEx1`: `Central -> Rainbow`, plus 9 `InterStageEx2` routes). A route from A to B
 *   says nothing about B to A, so the app never reverses an edge to fill a gap.
 * - **Rainbow is a sink.** It has exactly 1 incoming edge (`Central -> Rainbow`) and 0 outgoing
 *   edges. That is the data saying the world tour ends at Rainbow, not a missing row, so [outgoing]
 *   returns empty for it and the UI treats "no outgoing connection" as a real answer.
 */
internal object StageConnections {

    /** One directed connection, exactly as the v1.8.0 game data defines it. */
    data class Edge(
        val from: String,
        val to: String,
        val kind: String,
        val rule: String,
        val start: String,
        val traffic: String,
        val rowId: String,
        val displayName: String,
        val overrideJukeBoxEndRatio: Double? = null,
    )

    data class Catalog(
        /** Every edge, ordered by source then destination. */
        val edges: List<Edge>,
        /** The sentence that says where this came from. */
        val provenanceNote: String,
    ) {
        init {
            require(edges.isNotEmpty()) { "a connection table with no edges is not a catalog" }
            // A duplicate ordered pair would make `between(a,b)` ambiguous and a drawn line ambiguous too.
            require(edges.map { it.from to it.to }.toSet().size == edges.size) { "duplicate stage pair" }
        }

        private val bySource: Map<String, List<Edge>> = edges.groupBy { it.from }

        /** Stages this one can lead to. Empty for a sink such as Rainbow, which is a real answer. */
        fun outgoing(from: String): List<Edge> = bySource[normalise(from)].orEmpty()

        /** Every edge that ends at [to]. */
        fun incoming(to: String): List<Edge> = edges.filter { normalise(it.to) == normalise(to) }

        /** The connection from [from] to [to], when the game defines one in that direction. */
        fun between(from: String, to: String): Edge? =
            outgoing(from).firstOrNull { normalise(it.to) == normalise(to) }

        /** True when the game defines [from] to [to]. Never true just because it defines the reverse. */
        fun connects(from: String, to: String): Boolean = between(from, to) != null

        /** True when both [from] -> [to] and [to] -> [from] exist in the directed connection graph. */
        fun isBidirectional(from: String, to: String): Boolean = connects(from, to) && connects(to, from)

        /** True when [from] -> [to] exists but [to] -> [from] does not. */
        fun isOneWay(from: String, to: String): Boolean = connects(from, to) && !connects(to, from)

        /** Source stages that have at least one outgoing connection. */
        fun sources(): List<String> = bySource.keys.sorted()

        /** Stages that only ever appear as destinations, sorted so the answer is stable. */
        fun sinks(): List<String> =
            (edges.map { normalise(it.to) }.toSet() - bySource.keys).sorted()

        /**
         * The stages [from] reaches directly.
         *
         * This is what the map draws when a course is selected: the real neighbours, not a nearest-distance
         * guess. A stage with no outgoing edges returns empty and the map draws nothing.
         */
        fun neighbours(from: String): List<String> = outgoing(from).map { normalise(it.to) }

        /** The archive spells a stage `AirshipFortress_00`; the export spells it `AirshipFortress`. */
        private fun normalise(code: String): String = code.trim().removeSuffix("_00")
    }

    /** Parses the bundled asset. Null on anything unusable, so the caller can run without connections. */
    fun parse(text: String): Catalog? = runCatching {
        val root = JSONObject(text)
        val array = root.optJSONArray("edges") ?: return@runCatching null
        val edges = ArrayList<Edge>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val from = item.optString("from")
            val to = item.optString("to")
            val kind = item.optString("kind")
            // An edge missing an endpoint is not a connection; LongRally IDs are 6-stage rallies, not direct edges.
            if (from.isBlank() || to.isBlank()) continue
            if (kind.equals("LongRally", ignoreCase = true)) continue
            val overrideRatio = if (item.has("overrideJukeBoxEndRatio") && !item.isNull("overrideJukeBoxEndRatio")) {
                item.optDouble("overrideJukeBoxEndRatio").takeIf { it.isFinite() && it in 0.0..1.0 }
            } else {
                null
            }
            edges += Edge(
                from = from,
                to = to,
                kind = kind,
                rule = item.optString("rule"),
                start = item.optString("start"),
                traffic = item.optString("traffic"),
                rowId = item.optString("rowId"),
                displayName = item.optString("displayName"),
                overrideJukeBoxEndRatio = overrideRatio,
            )
        }
        if (edges.isEmpty()) return@runCatching null
        Catalog(
            edges = edges.sortedWith(compareBy({ it.from }, { it.to }, { it.kind })),
            provenanceNote = root.optString("provenanceNote"),
        )
    }.getOrNull()
}
