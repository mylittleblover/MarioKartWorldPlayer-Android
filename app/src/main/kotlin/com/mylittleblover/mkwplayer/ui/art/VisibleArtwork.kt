package com.mylittleblover.mkwplayer.ui.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val artworkDecoder = Dispatchers.IO.limitedParallelism(4)

internal val ARTWORK_CANONICAL_BUCKETS: IntArray = intArrayOf(256, 512, 1024)

/** Normalizes arbitrary layout pixel edges to canonical decode buckets (256, 512, 1024). */
internal fun normalizeArtworkBucket(rawEdgePx: Int): Int = when {
    rawEdgePx <= 0 -> 512
    rawEdgePx <= 256 -> 256
    rawEdgePx <= 512 -> 512
    else -> 1024
}

/**
 * Snaps near-standard aspect ratios (1:1, 4:3, 3:2, 16:9) within a small tolerance so minor
 * pixel-dimension differences across covers never cause layout height jitter on PlayScreen.
 */
internal fun stabilizeArtworkRatio(raw: Float): Float {
    val safe = if (raw.isFinite() && raw > 0f) raw else 1f
    val clamped = safe.coerceIn(0.65f, 2.2f)
    val anchors = floatArrayOf(1f, 4f / 3f, 3f / 2f, 16f / 9f)
    for (anchor in anchors) {
        if (abs(clamped - anchor) <= 0.05f) return anchor
    }
    return clamped
}

internal data class ArtworkLookup(
    val loaded: PackArtwork.Loaded?,
    val fromCache: Boolean,
)

private data class CachedResourceArtwork(
    val loaded: PackArtwork.Loaded,
    val byteCount: Int,
)

private data class ResourceBucketMatch(
    val loaded: PackArtwork.Loaded,
    val exactOrLarger: Boolean,
)

private val resourceArtworkCache = object : LruCache<String, CachedResourceArtwork>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: CachedResourceArtwork): Int = value.byteCount.coerceAtLeast(1)
}

internal fun evictResourceArtworkCache() {
    resourceArtworkCache.evictAll()
}

private fun resourceCacheKey(packPath: String?, resourceId: Int, bucket: Int): String =
    if (packPath != null) "$packPath|$resourceId@$bucket" else "$resourceId@$bucket"

private fun lookupResourceBucket(packPath: String?, resourceId: Int, requestedBucket: Int): ResourceBucketMatch? {
    for (bucket in ARTWORK_CANONICAL_BUCKETS) {
        if (bucket >= requestedBucket) {
            resourceArtworkCache.get(resourceCacheKey(packPath, resourceId, bucket))?.loaded?.let {
                return ResourceBucketMatch(it, exactOrLarger = true)
            }
        }
    }
    for (i in ARTWORK_CANONICAL_BUCKETS.lastIndex downTo 0) {
        val bucket = ARTWORK_CANONICAL_BUCKETS[i]
        if (bucket < requestedBucket) {
            resourceArtworkCache.get(resourceCacheKey(packPath, resourceId, bucket))?.loaded?.let {
                return ResourceBucketMatch(it, exactOrLarger = false)
            }
        }
    }
    return null
}

/**
 * Resolves the decode bucket synchronously on frame 1 from layout constraints via [BoxWithConstraints],
 * avoiding 1-frame zero-edge blank flashes and never resetting to zero during scroll or navigation.
 */
@Composable
internal fun ArtworkSlot(modifier: Modifier, content: @Composable (maxEdge: Int) -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boundedW = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val boundedH = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val bucket = normalizeArtworkBucket(maxOf(boundedW, boundedH))
        content(bucket)
    }
}

@Composable
internal fun rememberResourceArtwork(resourceId: Int?, maxEdge: Int): PackArtwork.Loaded? =
    rememberResourceArtworkState(resourceId, maxEdge).loaded

@Composable
internal fun rememberResourceArtworkState(resourceId: Int?, maxEdge: Int): ArtworkLookup {
    val context = LocalContext.current
    val activePackDir by PackArtwork.activePackDirectory.collectAsState()
    var retained by remember { mutableStateOf<PackArtwork.Loaded?>(null) }
    if (resourceId == null) {
        SideEffect { retained = null }
        return ArtworkLookup(null, fromCache = true)
    }
    val bucket = normalizeArtworkBucket(maxEdge)
    val packPath = activePackDir?.path
    val immediate = lookupResourceBucket(packPath, resourceId, bucket)
    if (immediate != null && immediate.exactOrLarger) {
        SideEffect { retained = immediate.loaded }
        return ArtworkLookup(immediate.loaded, fromCache = true)
    }

    var asyncLoaded by remember(context, resourceId, bucket, packPath) { mutableStateOf<PackArtwork.Loaded?>(null) }
    var decodeFinished by remember(context, resourceId, bucket, packPath) { mutableStateOf(false) }

    LaunchedEffect(context, resourceId, bucket, packPath) {
        val fresh = lookupResourceBucket(packPath, resourceId, bucket)
        if (fresh != null && fresh.exactOrLarger) {
            asyncLoaded = fresh.loaded
            retained = fresh.loaded
            decodeFinished = true
            return@LaunchedEffect
        }
        val cacheKey = resourceCacheKey(packPath, resourceId, bucket)
        val decoded = withContext(artworkDecoder) {
            decodePackOrDrawableArtwork(context, activePackDir, resourceId, bucket)?.also { loaded ->
                val bytes = runCatching { loaded.bitmap.asAndroidBitmap().allocationByteCount }
                    .getOrDefault(loaded.bitmap.width * loaded.bitmap.height * 4)
                resourceArtworkCache.put(cacheKey, CachedResourceArtwork(loaded, bytes))
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

private fun decodePackOrDrawableArtwork(
    context: Context,
    packDir: File?,
    resourceId: Int,
    maxEdge: Int,
): PackArtwork.Loaded? {
    if (packDir != null) {
        val entryName = runCatching { context.resources.getResourceEntryName(resourceId) }.getOrNull()
        if (!entryName.isNullOrBlank()) {
            val candidates = listOf(
                File(packDir, "artwork/$entryName.png"),
                File(packDir, "artwork/$entryName.webp"),
            )
            for (file in candidates) {
                if (file.isFile) {
                    decodeArtwork(maxEdge) { BitmapFactory.decodeFile(file.path, it) }?.let { return it }
                }
            }
        }
    }
    return decodeDrawableArtwork(context, resourceId, maxEdge)
}

internal fun decodeDrawableArtwork(context: Context, resourceId: Int, maxEdge: Int): PackArtwork.Loaded? {
    decodeArtwork(maxEdge) { BitmapFactory.decodeResource(context.resources, resourceId, it) }?.let { return it }
    val drawable = ContextCompat.getDrawable(context, resourceId) ?: return null
    val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxEdge
    val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxEdge
    val longest = maxOf(rawW, rawH).coerceAtLeast(1)
    val targetEdge = maxEdge.coerceIn(128, 512)
    val scale = targetEdge.toFloat() / longest.toFloat()
    val w = (rawW * scale).toInt().coerceAtLeast(1)
    val h = (rawH * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return PackArtwork.Loaded(bitmap.asImageBitmap(), rawW.toFloat() / rawH.toFloat())
}

/**
 * Stabilizes cover aspect ratios across track skips and brief decoding windows so PlayScreen and
 * cover cards never jump abruptly to a fallback aspect ratio while replacement artwork decodes.
 */
@Composable
internal fun rememberArtworkRatio(id: Any?, artwork: PackArtwork.Loaded?, fallback: Float): Float {
    val stableFallback = stabilizeArtworkRatio(fallback)
    val actual = artwork?.aspectRatio?.takeIf { it.isFinite() && it > 0f }?.let(::stabilizeArtworkRatio)
    var ratio by remember { mutableFloatStateOf(actual ?: stableFallback) }
    if (actual != null) {
        SideEffect {
            if (ratio != actual) ratio = actual
        }
        return actual
    }
    LaunchedEffect(id, stableFallback) {
        ratio = stableFallback
    }
    return ratio
}

internal fun decodeArtwork(maxEdge: Int, decode: (BitmapFactory.Options) -> Bitmap?): PackArtwork.Loaded? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        inScaled = false
    }
    decode(options)
    val width = options.outWidth
    val height = options.outHeight
    if (width <= 0 || height <= 0) return null
    val shortest = minOf(width, height)
    var sample = 1
    while (shortest / sample / 2 >= maxEdge) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    val bitmap = decode(options) ?: return null
    return PackArtwork.Loaded(bitmap.asImageBitmap(), width.toFloat() / height)
}

@Composable
internal fun artworkFade(bitmap: ImageBitmap): Modifier {
    val alpha = remember(bitmap) { Animatable(0f) }
    LaunchedEffect(bitmap) { alpha.animateTo(1f, tween(160)) }
    return Modifier.graphicsLayer { this.alpha = alpha.value }
}

/**
 * Renders artwork without flashing transparent when cached or replacing a previous track's cover.
 * Cached hits and bucket upgrades swap instantaneously; uncached replacements crossfade over the
 * previously settled bitmap so the slot never dips to blank or fallback.
 */
@Composable
internal fun FadingArtwork(
    bitmap: ImageBitmap,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    animate: Boolean = true,
) {
    var settledBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    if (!animate) {
        SideEffect { settledBitmap = bitmap }
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            filterQuality = FilterQuality.High,
        )
        return
    }
    val previous = settledBitmap?.takeIf { it !== bitmap }
    val alpha = remember(bitmap) { Animatable(0f) }
    LaunchedEffect(bitmap) {
        alpha.animateTo(1f, tween(160))
        settledBitmap = bitmap
    }
    if (previous != null) {
        val prevRatio = previous.width.toFloat() / previous.height.coerceAtLeast(1)
        val currRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val sameAspect = abs(prevRatio - currRatio) <= 0.06f
        Box(modifier, contentAlignment = Alignment.Center) {
            Image(
                bitmap = previous,
                contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer {
                    this.alpha = if (sameAspect) 1f else (1f - alpha.value)
                },
                contentScale = contentScale,
                filterQuality = FilterQuality.High,
            )
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer {
                    this.alpha = alpha.value
                },
                contentScale = contentScale,
                filterQuality = FilterQuality.High,
            )
        }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.graphicsLayer { this.alpha = alpha.value },
            contentScale = contentScale,
            filterQuality = FilterQuality.High,
        )
    }
}

@Composable
internal fun ResourceArtwork(
    resourceId: Int,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    ArtworkSlot(modifier) { edge ->
        val lookup = rememberResourceArtworkState(resourceId, edge)
        lookup.loaded?.let {
            FadingArtwork(it.bitmap, Modifier.fillMaxSize(), contentScale, animate = !lookup.fromCache)
        }
    }
}
