package com.mylittleblover.mkwplayer.ui.art

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

internal object PackArtwork {
    val activePackDirectory = MutableStateFlow<File?>(null)

    data class Loaded(
        val bitmap: ImageBitmap,
        val aspectRatio: Float,
    )

    private data class CachedEntry(
        val loaded: Loaded,
        val byteCount: Int,
    )

    private data class BucketMatch(
        val loaded: Loaded,
        val exactOrLarger: Boolean,
    )

    private val maxCacheBytes = minOf(
        40 * 1024 * 1024,
        (Runtime.getRuntime().maxMemory() / 6L).toInt().coerceAtLeast(8 * 1024 * 1024),
    )

    private val bitmapCache = object : LruCache<String, CachedEntry>(maxCacheBytes) {
        override fun sizeOf(key: String, value: CachedEntry): Int = value.byteCount.coerceAtLeast(1)
    }

    private val resolvedNameCache = LruCache<String, String>(256)
    private val artworkIndexCache = ConcurrentHashMap<String, Set<String>>()
    private val fileLastModCache = ConcurrentHashMap<String, Long>()

    fun evictAll() {
        activePackDirectory.value = null
        resolvedNameCache.evictAll()
        bitmapCache.evictAll()
        artworkIndexCache.clear()
        fileLastModCache.clear()
        evictResourceArtworkCache()
    }

    /** Indexes available artwork filenames and lastModified timestamps once on I/O. */
    fun warmArtworkIndex(packDirectory: File?): Set<String>? {
        activePackDirectory.value = packDirectory
        packDirectory ?: return null
        val key = packDirectory.path
        artworkIndexCache[key]?.let { return it }
        val artDir = File(packDirectory, "artwork")
        val files = if (artDir.isDirectory) artDir.listFiles().orEmpty() else emptyArray()
        val names = HashSet<String>(files.size)
        for (file in files) {
            if (file.isFile) {
                names += file.name
                fileLastModCache[fileModKey(packDirectory, file.name)] = file.lastModified()
            }
        }
        artworkIndexCache[key] = names
        return names
    }

    fun hasAnyCandidate(packDirectory: File?, artworkNames: List<String>): Boolean {
        if (packDirectory == null || artworkNames.isEmpty()) return false
        val indexed = artworkIndexCache[packDirectory.path] ?: return true
        return artworkNames.any { it in indexed }
    }

    fun warmThumbnails(packDirectory: File?, artworkNames: List<String>, maxEdge: Int = 512) {
        packDirectory ?: return
        val bucket = normalizeArtworkBucket(maxEdge)
        warmArtworkIndex(packDirectory)
        artworkNames.forEach { name ->
            load(packDirectory, name, bucket)
        }
    }

    private fun fileModKey(packDirectory: File, artworkName: String): String =
        "${packDirectory.path}\u0000$artworkName"

    private fun fileKey(packDirectory: File, artworkName: String, lastModified: Long, bucket: Int): String =
        "${packDirectory.path}\u0000$lastModified\u0000$bucket\u0000$artworkName"

    private fun requestKey(packDirectory: File, artworkNames: List<String>): String =
        "${packDirectory.path}\u0000${artworkNames.joinToString("\u0000")}"

    private fun lookupBucketMatch(packDirectory: File, artworkNames: List<String>, requestedBucket: Int): BucketMatch? {
        val reqKey = requestKey(packDirectory, artworkNames)
        val resolved = resolvedNameCache.get(reqKey)
        val ordered = if (resolved != null) listOf(resolved) + artworkNames else artworkNames

        // 1. Prefer exact or larger already-cached bucket (instant display, no re-decode needed).
        for (name in ordered) {
            val lastMod = fileLastModCache[fileModKey(packDirectory, name)] ?: continue
            for (bucket in ARTWORK_CANONICAL_BUCKETS) {
                if (bucket >= requestedBucket) {
                    bitmapCache.get(fileKey(packDirectory, name, lastMod, bucket))?.loaded?.let { hit ->
                        if (resolved != name) resolvedNameCache.put(reqKey, name)
                        return BucketMatch(hit, exactOrLarger = true)
                    }
                }
            }
        }

        // 2. Otherwise return any smaller cached bucket synchronously while upgrading asynchronously.
        for (i in ARTWORK_CANONICAL_BUCKETS.lastIndex downTo 0) {
            val bucket = ARTWORK_CANONICAL_BUCKETS[i]
            if (bucket >= requestedBucket) continue
            for (name in ordered) {
                val lastMod = fileLastModCache[fileModKey(packDirectory, name)] ?: continue
                bitmapCache.get(fileKey(packDirectory, name, lastMod, bucket))?.loaded?.let { hit ->
                    if (resolved != name) resolvedNameCache.put(reqKey, name)
                    return BucketMatch(hit, exactOrLarger = false)
                }
            }
        }
        return null
    }

    private fun load(packDirectory: File, artworkName: String, maxEdge: Int): Loaded? {
        val bucket = normalizeArtworkBucket(maxEdge)
        val indexed = artworkIndexCache[packDirectory.path]
        if (indexed != null && artworkName !in indexed) return null
        val file = File(packDirectory, "artwork/$artworkName")
        if (!file.isFile) return null
        val lastMod = file.lastModified()
        fileLastModCache[fileModKey(packDirectory, artworkName)] = lastMod
        val fKey = fileKey(packDirectory, artworkName, lastMod, bucket)
        bitmapCache.get(fKey)?.loaded?.let { return it }
        val loaded = decodeArtwork(bucket) { BitmapFactory.decodeFile(file.path, it) } ?: return null
        val bytes = runCatching { loaded.bitmap.asAndroidBitmap().allocationByteCount }
            .getOrDefault(loaded.bitmap.width * loaded.bitmap.height * 4)
        bitmapCache.put(fKey, CachedEntry(loaded, bytes))
        return loaded
    }

    @Composable
    fun rememberArtwork(packDirectory: File?, artworkNames: List<String>, maxEdge: Int): Loaded? =
        rememberArtworkState(packDirectory, artworkNames, maxEdge).loaded

    @Composable
    internal fun rememberArtworkState(packDirectory: File?, artworkNames: List<String>, maxEdge: Int): ArtworkLookup {
        var retained by remember { mutableStateOf<Loaded?>(null) }
        if (packDirectory == null || artworkNames.isEmpty()) {
            SideEffect { retained = null }
            return ArtworkLookup(null, fromCache = true)
        }
        val bucket = normalizeArtworkBucket(maxEdge)
        val immediate = lookupBucketMatch(packDirectory, artworkNames, bucket)
        if (immediate != null && immediate.exactOrLarger) {
            SideEffect { retained = immediate.loaded }
            return ArtworkLookup(immediate.loaded, fromCache = true)
        }
        val hasCandidate = hasAnyCandidate(packDirectory, artworkNames)
        if (immediate == null && !hasCandidate) {
            SideEffect { retained = null }
            return ArtworkLookup(null, fromCache = true)
        }

        var asyncLoaded by remember(packDirectory, artworkNames, bucket) { mutableStateOf<Loaded?>(null) }
        var decodeFinished by remember(packDirectory, artworkNames, bucket) { mutableStateOf(false) }

        LaunchedEffect(packDirectory, artworkNames, bucket) {
            val fresh = lookupBucketMatch(packDirectory, artworkNames, bucket)
            if (fresh != null && fresh.exactOrLarger) {
                asyncLoaded = fresh.loaded
                retained = fresh.loaded
                decodeFinished = true
                return@LaunchedEffect
            }
            val decoded = withContext(artworkDecoder) {
                warmArtworkIndex(packDirectory)
                val reqKey = requestKey(packDirectory, artworkNames)
                artworkNames.firstNotNullOfOrNull { name ->
                    load(packDirectory, name, bucket)?.also {
                        resolvedNameCache.put(reqKey, name)
                    }
                }
            }
            asyncLoaded = decoded
            retained = decoded
            decodeFinished = true
        }

        val effective = asyncLoaded ?: immediate?.loaded ?: if (!decodeFinished) retained else null
        val fromCache = immediate != null || (asyncLoaded == null && retained != null)
        return ArtworkLookup(effective, fromCache = fromCache)
    }

    @Composable
    fun Thumbnail(packDirectory: File?, artworkNames: List<String>, modifier: Modifier, fallback: @Composable () -> Unit) {
        ArtworkSlot(modifier) { edge ->
            val lookup = rememberArtworkState(packDirectory, artworkNames, edge)
            if (lookup.loaded != null) {
                FadingArtwork(lookup.loaded.bitmap, Modifier.fillMaxSize(), ContentScale.Crop, animate = !lookup.fromCache)
            } else {
                fallback()
            }
        }
    }
}
