package com.mylittleblover.mkwplayer.library

import org.json.JSONObject

/**
 * Where each stage sits on the world map.
 *
 * The positions were measured on a 2500x2400 render and stored as fractions rather than pixels. That
 * is the whole point: the app draws the map at whatever size the screen allows, so a pixel coordinate
 * would be correct on exactly one device. A fraction is correct at every size.
 *
 * `provenance` travels with the data because these are measurements of a render, not the game's own map
 * coordinates. A future asset that claims otherwise must be rejected rather than believed.
 */
internal object MapAnchors {

    /** What the positions are, and what they are not. */
    enum class Provenance {
        /** Measured on a map render. Not the game's coordinate system. */
        EXTRACTED_ANCHORS,
    }

    /** A point on the map, as fractions of the drawn width and height. */
    data class Anchor(val code: String, val u: Float, val v: Float) {
        init {
            require(code.isNotBlank()) { "anchor code must not be blank" }
            // A fraction outside 0..1 would place the pin off the bitmap, which looks like a broken
            // map rather than bad data.
            require(u in 0f..1f) { "u must be within 0..1, got $u for $code" }
            require(v in 0f..1f) { "v must be within 0..1, got $v for $code" }
        }
    }

    data class Catalog(
        val provenance: Provenance,
        val provenanceNote: String,
        val canvasWidth: Int,
        val canvasHeight: Int,
        val stages: List<Anchor>,
        val cups: List<Anchor>,
        /** Knocked-out route markers, which the game places without a stage under them. */
        val survival: List<Anchor>,
    ) {
        init {
            // Duplicate codes would make "where is this stage" ambiguous, and a lookup would silently
            // return whichever came first.
            require(stages.map { it.code }.toSet().size == stages.size) { "duplicate stage codes" }
            require(cups.map { it.code }.toSet().size == cups.size) { "duplicate cup codes" }
            require(survival.map { it.code }.toSet().size == survival.size) { "duplicate survival codes" }
        }

        fun stage(code: String): Anchor? = stages.firstOrNull { it.code == code }

        /**
         * The anchor for a stage, tolerating the archive's `_00` suffix.
         *
         * The export names stages `Coaster` and `AirshipFortress_00` inconsistently, so a caller that
         * has one spelling and not the other would get a missing pin for no reason.
         */
        fun stageTolerant(code: String): Anchor? =
            stage(code) ?: stage(code.removeSuffix("_00")) ?: stage("${code}_00")

        /** True when every one of [codes] can be placed. */
        fun covers(codes: Collection<String>): Boolean = codes.all { stageTolerant(it) != null }

        /** Codes in [codes] that cannot be placed, for a "some stages are not on the map" notice. */
        fun missing(codes: Collection<String>): List<String> = codes.filter { stageTolerant(it) == null }
    }

    /** Parses the bundled asset. Returns null on anything unusable, so the caller can omit the map. */
    // `return try` rather than `= try`: the body uses early returns for malformed fields, and Kotlin
    // forbids `return` inside an expression body. Wrapping the try in an explicit return keeps both
    // the early exits and the single catch.
    fun parse(text: String): Catalog? = runCatching { parseOrThrow(text) }.getOrNull()

    private fun parseOrThrow(text: String): Catalog? {
        val root = JSONObject(text)
        val provenanceName = root.optString("provenance")
        val provenance = Provenance.entries.firstOrNull { it.name == provenanceName } ?: return null

        val canvas = root.optJSONArray("canvas") ?: return null
        val width = canvas.optInt(0, 0)
        val height = canvas.optInt(1, 0)
        if (width <= 0 || height <= 0) return null

        fun anchors(name: String): List<Anchor>? {
            val array = root.optJSONArray(name) ?: return emptyList()
            val list = ArrayList<Anchor>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val code = item.optString("code")
                if (code.isEmpty()) continue
                val u = item.optDouble("u", Double.NaN)
                val v = item.optDouble("v", Double.NaN)
                if (u.isNaN() || v.isNaN()) continue
                if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) continue
                list += Anchor(code, u.toFloat(), v.toFloat())
            }
            return list
        }

        val stages = anchors("stages") ?: return null
        val cups = anchors("cups") ?: return null
        val survival = anchors("survival") ?: return null
        if (stages.isEmpty()) return null

        return Catalog(
            provenance = provenance,
            provenanceNote = root.optString("provenanceNote"),
            canvasWidth = width,
            canvasHeight = height,
            stages = stages,
            cups = cups,
            survival = survival,
        )
    }
}
