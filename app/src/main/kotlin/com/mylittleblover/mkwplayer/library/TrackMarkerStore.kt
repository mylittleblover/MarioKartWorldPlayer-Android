package com.mylittleblover.mkwplayer.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Timestamped bookmark attached to any playable track (`courseCode`, `jb:<key>`, `sfc:<id>`,
 * `battle:<id>`, `extra:<id>`, etc.).
 */
internal data class TrackMarker(
    val id: String,
    val trackId: String,
    val positionMs: Long,
    val label: String,
    val colorArgb: Long,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    init {
        require(id.isNotBlank()) { "TrackMarker id must not be blank" }
        require(trackId.isNotBlank()) { "TrackMarker trackId must not be blank" }
        require(positionMs >= 0L) { "TrackMarker positionMs must be >= 0, got $positionMs" }
        require(label.trim().isNotEmpty()) { "TrackMarker label must not be blank" }
    }
}

internal data class TrackMarkerColorOption(
    val key: String,
    val argb: Long,
)

/**
 * Curated 8-color MD3 marker palette — every swatch meets WCAG AA (>= 4.5:1) against pure white
 * while maintaining crisp chromatic separation on Porcelain surfaces.
 */
internal object TrackMarkerColors {
    const val CRIMSON: Long = 0xFFC52D49L
    const val AMBER: Long = 0xFFA85A00L
    const val EMERALD: Long = 0xFF0B7D55L
    const val SKY: Long = 0xFF0277BDL
    const val VIOLET: Long = 0xFF7C3AEDL
    const val PINK: Long = 0xFFC82368L
    const val ORANGE: Long = 0xFFCE3F11L
    const val SLATE: Long = 0xFF475569L

    val DEFAULT: Long = CRIMSON

    val palette: List<Long> = listOf(
        CRIMSON,
        AMBER,
        EMERALD,
        SKY,
        VIOLET,
        PINK,
        ORANGE,
        SLATE,
    )

    val options: List<TrackMarkerColorOption> = listOf(
        TrackMarkerColorOption("crimson", CRIMSON),
        TrackMarkerColorOption("amber", AMBER),
        TrackMarkerColorOption("emerald", EMERALD),
        TrackMarkerColorOption("sky", SKY),
        TrackMarkerColorOption("violet", VIOLET),
        TrackMarkerColorOption("pink", PINK),
        TrackMarkerColorOption("orange", ORANGE),
        TrackMarkerColorOption("slate", SLATE),
    )

    /** Ensures full opaque alpha (`0xFF......`) and masks cleanly into 32-bit unsigned ARGB. */
    fun normalize(argb: Long): Long {
        val masked = argb and 0xFFFFFFFFL
        return if ((masked and 0xFF000000L) == 0L) (masked or 0xFF000000L) else masked
    }
}

/**
 * Thread-safe, crash-safe JSON persistence for per-track markers/bookmarks (`track_markers.json`).
 *
 * Uses [MetadataFile.lock] + atomic rename writes so concurrent reads/writes across UI, ViewModel,
 * and [UserBackupManager] stay consistent and never truncate existing markers.
 */
internal class TrackMarkerStore(private val file: File) {

    fun read(): Result<List<TrackMarker>> = synchronized(MetadataFile.lock(file)) {
        runCatching { decodeStrict() }
    }

    fun allMarkers(): List<TrackMarker> = synchronized(MetadataFile.lock(file)) {
        runCatching { decodeStrict() }.getOrDefault(emptyList())
    }

    fun markersFor(trackId: String): List<TrackMarker> {
        val cleanTrackId = trackId.trim()
        if (cleanTrackId.isEmpty()) return emptyList()
        return allMarkers()
            .filter { it.trackId == cleanTrackId }
            .sortedWith(MARKER_COMPARATOR)
    }

    fun trackIdsWithMarkers(): Set<String> =
        allMarkers().mapTo(linkedSetOf()) { it.trackId }

    fun add(
        trackId: String,
        positionMs: Long,
        label: String,
        colorArgb: Long = TrackMarkerColors.DEFAULT,
        createdAtEpochMs: Long = System.currentTimeMillis(),
    ): TrackMarker = synchronized(MetadataFile.lock(file)) {
        val cleanTrackId = trackId.trim()
        require(cleanTrackId.isNotEmpty()) { "trackId must not be blank" }
        val cleanLabel = label.trim().take(MAX_LABEL_LENGTH)
        require(cleanLabel.isNotEmpty()) { "label must not be blank" }
        val created = TrackMarker(
            id = UUID.randomUUID().toString(),
            trackId = cleanTrackId,
            positionMs = positionMs.coerceAtLeast(0L),
            label = cleanLabel,
            colorArgb = TrackMarkerColors.normalize(colorArgb),
            createdAtEpochMs = createdAtEpochMs.coerceAtLeast(0L),
        )
        val updated = (decodeStrict() + created).sortedWith(GLOBAL_MARKER_COMPARATOR)
        writeMarkers(updated)
        created
    }

    fun update(marker: TrackMarker): TrackMarker? = synchronized(MetadataFile.lock(file)) {
        val cleanTrackId = marker.trackId.trim()
        require(cleanTrackId.isNotEmpty()) { "trackId must not be blank" }
        val cleanLabel = marker.label.trim().take(MAX_LABEL_LENGTH)
        require(cleanLabel.isNotEmpty()) { "label must not be blank" }
        val current = decodeStrict()
        val idx = current.indexOfFirst { it.id == marker.id }
        if (idx < 0) return@synchronized null
        val sanitized = marker.copy(
            trackId = cleanTrackId,
            positionMs = marker.positionMs.coerceAtLeast(0L),
            label = cleanLabel,
            colorArgb = TrackMarkerColors.normalize(marker.colorArgb),
            createdAtEpochMs = marker.createdAtEpochMs.coerceAtLeast(0L),
        )
        val updated = current.toMutableList().apply { set(idx, sanitized) }.sortedWith(GLOBAL_MARKER_COMPARATOR)
        writeMarkers(updated)
        sanitized
    }

    fun delete(markerId: String): Boolean = synchronized(MetadataFile.lock(file)) {
        val cleanId = markerId.trim()
        if (cleanId.isEmpty()) return@synchronized false
        val current = decodeStrict()
        val updated = current.filterNot { it.id == cleanId }
        if (updated.size == current.size) return@synchronized false
        writeMarkers(updated)
        true
    }

    fun deleteForTrack(trackId: String): Int = synchronized(MetadataFile.lock(file)) {
        val cleanTrackId = trackId.trim()
        if (cleanTrackId.isEmpty()) return@synchronized 0
        val current = decodeStrict()
        val updated = current.filterNot { it.trackId == cleanTrackId }
        val removed = current.size - updated.size
        if (removed > 0) writeMarkers(updated)
        removed
    }

    fun replaceAll(markers: List<TrackMarker>): List<TrackMarker> = synchronized(MetadataFile.lock(file)) {
        val normalized = normalizeList(markers)
        writeMarkers(normalized)
        normalized
    }

    fun mergeAll(incoming: List<TrackMarker>): List<TrackMarker> = synchronized(MetadataFile.lock(file)) {
        val existing = decodeStrict()
        if (incoming.isEmpty() && !file.exists()) return@synchronized emptyList()
        val byId = LinkedHashMap<String, TrackMarker>()
        existing.forEach { byId[it.id] = it }
        normalizeList(incoming).forEach { marker ->
            val prev = byId[marker.id]
            if (prev == null || marker.createdAtEpochMs >= prev.createdAtEpochMs) {
                byId[marker.id] = marker
            }
        }
        val merged = byId.values.sortedWith(GLOBAL_MARKER_COMPARATOR)
        writeMarkers(merged)
        merged
    }

    private fun normalizeList(markers: List<TrackMarker>): List<TrackMarker> {
        val byId = LinkedHashMap<String, TrackMarker>()
        markers.forEach { raw ->
            val id = raw.id.trim().ifEmpty { UUID.randomUUID().toString() }
            val trackId = raw.trackId.trim()
            val label = raw.label.trim().take(MAX_LABEL_LENGTH)
            if (trackId.isEmpty() || label.isEmpty()) return@forEach
            byId[id] = TrackMarker(
                id = id,
                trackId = trackId,
                positionMs = raw.positionMs.coerceAtLeast(0L),
                label = label,
                colorArgb = TrackMarkerColors.normalize(raw.colorArgb),
                createdAtEpochMs = raw.createdAtEpochMs.coerceAtLeast(0L),
            )
        }
        return byId.values.sortedWith(GLOBAL_MARKER_COMPARATOR)
    }

    private fun decodeStrict(): List<TrackMarker> {
        if (!file.exists()) return emptyList()
        check(file.isFile) { "Track markers record is not a file" }
        val root = JSONObject(file.readText())
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) {
            "Unsupported track markers schemaVersion"
        }
        val arr = root.optJSONArray("markers") ?: JSONArray()
        val out = ArrayList<TrackMarker>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            decodeMarkerJson(obj)?.let { out += it }
        }
        return out.sortedWith(GLOBAL_MARKER_COMPARATOR)
    }

    private fun writeMarkers(markers: List<TrackMarker>) {
        val arr = JSONArray()
        markers.forEach { arr.put(encodeMarkerJson(it)) }
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("markers", arr)
        MetadataFile.write(file, root.toString())
    }

    companion object {
        const val FILE_NAME = "track_markers.json"
        const val SCHEMA_VERSION = 1
        const val MAX_LABEL_LENGTH = 80

        private val MARKER_COMPARATOR: Comparator<TrackMarker> =
            compareBy<TrackMarker>({ it.positionMs }, { it.createdAtEpochMs }, { it.id })

        private val GLOBAL_MARKER_COMPARATOR: Comparator<TrackMarker> =
            compareBy<TrackMarker>({ it.trackId }, { it.positionMs }, { it.createdAtEpochMs }, { it.id })

        fun inFilesDir(filesDir: File): TrackMarkerStore =
            TrackMarkerStore(File(filesDir, FILE_NAME))

        fun fromContext(context: Context): TrackMarkerStore =
            inFilesDir(context.applicationContext.filesDir)

        fun encodeMarkerJson(marker: TrackMarker): JSONObject = JSONObject()
            .put("id", marker.id)
            .put("trackId", marker.trackId)
            .put("positionMs", marker.positionMs)
            .put("label", marker.label)
            .put("colorArgb", TrackMarkerColors.normalize(marker.colorArgb))
            .put("createdAtEpochMs", marker.createdAtEpochMs)

        fun decodeMarkerJson(obj: JSONObject): TrackMarker? {
            val id = obj.optString("id", "").trim()
            val trackId = obj.optString("trackId", "").trim()
            val label = obj.optString("label", "").trim().take(MAX_LABEL_LENGTH)
            if (id.isEmpty() || trackId.isEmpty() || label.isEmpty()) return null
            val positionMs = obj.optLong("positionMs", -1L)
            if (positionMs < 0L) return null
            val rawColor = if (obj.has("colorArgb") && !obj.isNull("colorArgb")) {
                obj.optLong("colorArgb", TrackMarkerColors.DEFAULT)
            } else {
                TrackMarkerColors.DEFAULT
            }
            val createdAt = obj.optLong("createdAtEpochMs", 0L).coerceAtLeast(0L)
            return TrackMarker(
                id = id,
                trackId = trackId,
                positionMs = positionMs,
                label = label,
                colorArgb = TrackMarkerColors.normalize(rawColor),
                createdAtEpochMs = createdAt,
            )
        }
    }
}
