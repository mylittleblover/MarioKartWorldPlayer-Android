package com.mylittleblover.mkwplayer.ui.art

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * A course's picture, with a defined order of preference.
 *
 * The plan calls for `CourseCover(stageCode, formalPainter?, modifier)`: a supplied formal photograph wins,
 * then the shipped stage-select cover, then the map crop. The first version of the app only had the map
 * crop, which draws a window of the world map around a pin -- a location marker, not a picture of the
 * course, and the reason every cover in the app looked like the same map.
 *
 * The order is the whole point, so it is stated once here rather than at each call site:
 *
 * 1. [formalArt] -- a painter the caller supplies, for art that arrives after this build.
 * 2. [CourseCoverArt] -- the archive's own stage-select image, shipped in `drawable-nodpi`.
 * 3. [CoursePlaceholder] -- the map crop, which always exists because the map always exists.
 *
 * A stage with none of the three shows the map crop rather than another course's photograph: a wrong course
 * photographed nicely is still a wrong course.
 */
@Composable
internal fun CourseCover(
    code: String?,
    modifier: Modifier = Modifier,
    formalArt: androidx.compose.ui.graphics.painter.Painter? = null,
    showIcon: Boolean = false,
    packDirectory: java.io.File? = null,
    artworkName: String? = null,
) {
    val cover = code?.let { CourseCoverArt.resolve(it) }
    val packArtNames = remember(artworkName, code) {
        (listOfNotNull(artworkName) + (code?.let { CourseCoverArt.packCandidates(it) }.orEmpty())).distinct()
    }

    if (formalArt != null) {
        Box(modifier) {
            Image(
                painter = formalArt,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (showIcon) code?.let { StageArt.resolve(it) }?.let {
                ResourceArtwork(it, Modifier.align(Alignment.BottomEnd).size(72.dp))
            }
        }
        return
    }

    ArtworkSlot(modifier) { edge ->
        Box(Modifier.fillMaxSize()) {
            val usePack = packDirectory != null && packArtNames.isNotEmpty()
            val packedLookup = if (usePack) {
                PackArtwork.rememberArtworkState(packDirectory, packArtNames, edge)
            } else {
                ArtworkLookup(null, fromCache = true)
            }
            val packed = packedLookup.loaded
            when {
                packed != null -> {
                    FadingArtwork(packed.bitmap, Modifier.fillMaxSize(), ContentScale.Crop, animate = !packedLookup.fromCache)
                }
                !usePack || !PackArtwork.hasAnyCandidate(packDirectory, packArtNames) -> {
                    if (cover != null) {
                        val coverLookup = rememberResourceArtworkState(cover, edge)
                        coverLookup.loaded?.let {
                            FadingArtwork(it.bitmap, Modifier.fillMaxSize(), ContentScale.Crop, animate = !coverLookup.fromCache)
                        }
                    } else {
                        CoursePlaceholderContent(code, edge = edge, showIcon = false)
                    }
                }
            }
            if (showIcon) code?.let { StageArt.resolve(it) }?.let {
                val iconLookup = rememberResourceArtworkState(it, minOf(edge, 256))
                iconLookup.loaded?.let { loadedIcon ->
                    FadingArtwork(
                        loadedIcon.bitmap,
                        Modifier.align(Alignment.BottomEnd).size(72.dp),
                        ContentScale.Fit,
                        animate = !iconLookup.fromCache,
                    )
                }
            }
        }
    }
}

/**
 * The same order, as a plain value, for callers that need the drawable id rather than a composable.
 *
 * Returned instead of painted so a caller that already owns a `Canvas` or a `ImageBitmap` can use it
 * without nesting a composable. Null means "no shipped cover": the caller decides whether to fall back to
 * the map crop.
 */
internal fun courseCoverDrawable(code: String?): Int? = code?.let { CourseCoverArt.resolve(it) }

/** A neutral backdrop for a course whose art is not available at all. */
@Composable
internal fun MissingCourseArt(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {}
}
