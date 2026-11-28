package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.BwavWaveformExtractor
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.interactive.MinfCatalog
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButtonTone
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive zoomable & pannable BWAV waveform editor with magnetic snapping onto
 * verified MINF exit markers and bar boundaries.
 *
 * When [bridgeLocked] is true, the waveform remains zoomable/inspectable around `Bridge_ToFull`,
 * while exit point modification is strictly prohibited.
 */
@Composable
internal fun ZoomableWaveformEditor(
    peaks: BwavWaveformExtractor.WaveformPeaks?,
    frames: Long,
    sampleRate: Int,
    beats: List<MinfCatalog.Beat>,
    exitCandidates: List<CourseFlowRecipeBuilder.ExitCandidate>,
    selectedExitFrame: Long,
    bridgeLocked: Boolean,
    loopStart: Long?,
    loopEnd: Long?,
    onSelectExitFrame: (Long) -> Unit,
    modifier: Modifier = Modifier,
    constrainToCandidatesOnly: Boolean = false,
    lockLabel: String? = null,
    gridBeats: Int? = null,
    lockedHint: String? = null,
) {
    val totalFrames = frames.coerceAtLeast(1L)
    var zoom by remember(frames, exitCandidates.size) { mutableFloatStateOf(1f) }
    var scrollFraction by remember(frames) { mutableFloatStateOf(0f) }

    fun clampScroll(rawScroll: Float, z: Float): Float {
        val maxScroll = (1f - 1f / z).coerceAtLeast(0f)
        return rawScroll.coerceIn(0f, maxScroll)
    }

    fun applyZoom(newZoom: Float, focusFractionInView: Float = 0.5f) {
        val clampedZoom = newZoom.coerceIn(1f, 16f)
        val oldWindow = 1f / zoom
        val newWindow = 1f / clampedZoom
        val anchorAbs = (scrollFraction + focusFractionInView * oldWindow).coerceIn(0f, 1f)
        scrollFraction = clampScroll(anchorAbs - focusFractionInView * newWindow, clampedZoom)
        zoom = clampedZoom
    }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentExitFrame by rememberUpdatedState(selectedExitFrame)
    val currentCandidates by rememberUpdatedState(exitCandidates)
    val currentBeats by rememberUpdatedState(beats)
    val currentLocked by rememberUpdatedState(bridgeLocked)
    val currentConstrainOnly by rememberUpdatedState(constrainToCandidatesOnly)
    val currentOnSelect by rememberUpdatedState(onSelectExitFrame)
    val currentZoom by rememberUpdatedState(zoom)
    val currentScroll by rememberUpdatedState(scrollFraction)

    var lastSnappedFrame by remember(frames) { mutableLongStateOf(selectedExitFrame) }
    var isDraggingHandle by remember { mutableStateOf(false) }

    fun nearestCandidateFrame(targetFrame: Long): Long {
        val candidateFrames = currentCandidates.map { it.frame }
        val pool = if (currentConstrainOnly) {
            candidateFrames
        } else {
            val beatFrames = if (currentZoom >= 2.2f) {
                currentBeats.mapNotNull { b -> b.frame.takeIf { it in 1L..totalFrames } }
            } else {
                currentBeats.mapNotNull { b -> b.frame.takeIf { b.label == 1 && it in 1L..totalFrames } }
            }
            candidateFrames + beatFrames
        }
        if (pool.isEmpty()) return targetFrame.coerceIn(1L, totalFrames)
        return pool.minByOrNull { abs(it - targetFrame) }
            ?: targetFrame.coerceIn(1L, totalFrames)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header toolbar: active snapped time + zoom controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MkwBadge(
                text = EngineSnapshot.formatPosition(selectedExitFrame, sampleRate),
                tone = if (bridgeLocked) MkwBadgeTone.Crimson else MkwBadgeTone.Rose,
                leadingIcon = if (bridgeLocked) Icons.Default.Lock else null,
                tabular = true,
            )
            Text(
                text = when {
                    bridgeLocked -> lockLabel ?: stringResource(R.string.flow_editor_lock_bridge_handover)
                    constrainToCandidatesOnly && gridBeats != null ->
                        stringResource(R.string.flow_editor_snap_grid_markers, gridBeats, exitCandidates.size)
                    else -> stringResource(R.string.flow_editor_snap_markers, exitCandidates.size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MkwKitTokens.Palette.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val zoomLabel = remember(zoom) { String.format(Locale.ROOT, "%.1f×", zoom) }
            MkwGhostButton(
                text = zoomLabel,
                onClick = { applyZoom(1f, 0.5f) },
                enabled = zoom > 1.01f,
            )
            MkwIconButton(
                icon = Icons.Default.ZoomOut,
                contentDescription = stringResource(R.string.flow_editor_zoom_out),
                onClick = { applyZoom(zoom / 1.6f, 0.5f) },
                enabled = zoom > 1.01f,
                tone = MkwIconButtonTone.Rose,
                visualSize = 34.dp,
                iconSize = 18.dp,
                modifier = Modifier.size(36.dp),
            )
            MkwIconButton(
                icon = Icons.Default.ZoomIn,
                contentDescription = stringResource(R.string.flow_editor_zoom_in),
                onClick = { applyZoom(zoom * 1.6f, 0.5f) },
                enabled = zoom < 15.9f,
                tone = MkwIconButtonTone.Rose,
                visualSize = 34.dp,
                iconSize = 18.dp,
                modifier = Modifier.size(36.dp),
            )
        }

        val primaryColor = MkwKitTokens.Palette.crimson
        val activeBarColor = if (bridgeLocked) MkwKitTokens.Palette.crimsonDeep else primaryColor
        val dimmedBarColor = MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.25f)
        val markerColor = MkwKitTokens.Palette.crimsonDeep
        val beatLineColor = MkwKitTokens.Palette.hairline
        val barLineColor = MkwKitTokens.Palette.outlineInk.copy(alpha = 0.45f)
        val loopBandColor = MkwKitTokens.Palette.rose.copy(alpha = 0.5f)
        val canvasBg = MkwKitTokens.Palette.inset

        val handleHitSlopPx = with(density) { 26.dp.toPx() }
        val dragSlopPx = viewConfigSlop()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(MkwKitTokens.Shape.thumb)
                .background(canvasBg)
                .padding(horizontal = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(totalFrames) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val z0 = currentZoom
                        val s0 = currentScroll
                        val win0 = 1f / z0
                        val handleFrac = (currentExitFrame.toFloat() / totalFrames.toFloat())
                        val handleX = ((handleFrac - s0) / win0) * widthPx
                        val grabbedHandle = !currentLocked &&
                            currentCandidates.size > 1 &&
                            abs(down.position.x - handleX) <= handleHitSlopPx

                        var totalMoveX = 0f
                        var isMultiTouch = false
                        var movedPastSlop = false
                        isDraggingHandle = grabbedHandle

                        do {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.count { it.pressed }
                            if (activePointers >= 2) {
                                isMultiTouch = true
                                isDraggingHandle = false
                                val zoomChange = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (zoomChange != 1f || pan.x != 0f) {
                                    val newZ = (currentZoom * zoomChange).coerceIn(1f, 16f)
                                    val oldWin = 1f / currentZoom
                                    val newWin = 1f / newZ
                                    val centroidFrac = if (centroid != Offset.Unspecified) {
                                        (centroid.x / widthPx).coerceIn(0f, 1f)
                                    } else 0.5f
                                    val anchorAbs = (currentScroll + centroidFrac * oldWin).coerceIn(0f, 1f)
                                    val panFrac = -(pan.x / widthPx) * newWin
                                    scrollFraction = clampScroll(anchorAbs - centroidFrac * newWin + panFrac, newZ)
                                    zoom = newZ
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } else if (!isMultiTouch && event.changes.size == 1) {
                                val change = event.changes[0]
                                val dx = change.position.x - change.previousPosition.x
                                totalMoveX += abs(dx)
                                if (totalMoveX > dragSlopPx) movedPastSlop = true
                                if (grabbedHandle && change.pressed) {
                                    val win = 1f / currentZoom
                                    val absFrac = (currentScroll + (change.position.x / widthPx).coerceIn(0f, 1f) * win)
                                        .coerceIn(0f, 1f)
                                    val rawFrame = (absFrac * totalFrames).toLong().coerceIn(1L, totalFrames)
                                    val snapped = nearestCandidateFrame(rawFrame)
                                    if (snapped != lastSnappedFrame) {
                                        lastSnappedFrame = snapped
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    currentOnSelect(snapped)
                                    change.consume()
                                } else if (!grabbedHandle && currentZoom > 1.01f && movedPastSlop && change.pressed) {
                                    val win = 1f / currentZoom
                                    scrollFraction = clampScroll(currentScroll - (dx / widthPx) * win, currentZoom)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        isDraggingHandle = false
                        // Single tap (without drag/pinch) snaps exit point to nearest verified cue
                        if (!isMultiTouch && !movedPastSlop && !currentLocked && currentCandidates.size > 1) {
                            val win = 1f / currentZoom
                            val absFrac = (currentScroll + (down.position.x / widthPx).coerceIn(0f, 1f) * win)
                                .coerceIn(0f, 1f)
                            val rawFrame = (absFrac * totalFrames).toLong().coerceIn(1L, totalFrames)
                            val snapped = nearestCandidateFrame(rawFrame)
                            if (snapped != currentExitFrame) {
                                lastSnappedFrame = snapped
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(snapped)
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h * 0.5f
                val viewWindow = 1f / zoom
                val viewStartFrame = (scrollFraction * totalFrames).toLong().coerceIn(0L, totalFrames - 1L)
                val viewEndFrame = ((scrollFraction + viewWindow) * totalFrames).toLong().coerceIn(viewStartFrame + 1L, totalFrames)
                val viewSpan = (viewEndFrame - viewStartFrame).coerceAtLeast(1L).toFloat()

                fun frameToX(f: Long): Float = ((f - viewStartFrame).toFloat() / viewSpan) * w

                // Loop region background tint
                if (loopStart != null && loopEnd != null && loopEnd > loopStart) {
                    val x0 = frameToX(loopStart).coerceIn(0f, w)
                    val x1 = frameToX(loopEnd).coerceIn(0f, w)
                    if (x1 > x0) {
                        drawRect(
                            color = loopBandColor,
                            topLeft = Offset(x0, 0f),
                            size = Size(x1 - x0, h),
                        )
                    }
                }

                // Beat / Bar grid lines inside visible window
                beats.forEach { beat ->
                    if (beat.frame in viewStartFrame..viewEndFrame) {
                        val x = frameToX(beat.frame)
                        val isBar = beat.label == 1
                        if (isBar || zoom >= 2.2f) {
                            drawLine(
                                color = if (isBar) barLineColor else beatLineColor,
                                start = Offset(x, if (isBar) 0f else h * 0.25f),
                                end = Offset(x, if (isBar) h else h * 0.75f),
                                strokeWidth = if (isBar) 1.2f else 0.8f,
                            )
                        }
                    }
                }

                // Center baseline
                drawLine(
                    color = beatLineColor,
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1f,
                )

                // Waveform amplitude bars (or fallback pulse silhouette if peaks unavailable)
                val exitX = frameToX(selectedExitFrame)
                drawWaveformBars(
                    peaks = peaks,
                    viewStartFrame = viewStartFrame,
                    viewEndFrame = viewEndFrame,
                    totalFrames = totalFrames,
                    exitX = exitX,
                    activeColor = activeBarColor,
                    dimmedColor = dimmedBarColor,
                )

                // Verified exit snap cues
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                exitCandidates.forEach { candidate ->
                    if (candidate.frame in viewStartFrame..viewEndFrame) {
                        val cx = frameToX(candidate.frame)
                        val isSelected = candidate.frame == selectedExitFrame
                        if (!isSelected) {
                            drawLine(
                                color = markerColor.copy(alpha = 0.58f),
                                start = Offset(cx, h * 0.14f),
                                end = Offset(cx, h - 10f),
                                strokeWidth = 1.4f,
                                pathEffect = dashEffect,
                            )
                            drawCircle(
                                color = markerColor.copy(alpha = 0.85f),
                                radius = 3.5.dp.toPx(),
                                center = Offset(cx, h - 7.dp.toPx()),
                            )
                        }
                    }
                }

                // Active exit handle + vertical needle
                if (selectedExitFrame in viewStartFrame..viewEndFrame) {
                    drawLine(
                        color = activeBarColor,
                        start = Offset(exitX, 0f),
                        end = Offset(exitX, h),
                        strokeWidth = if (isDraggingHandle) 3.dp.toPx() else 2.dp.toPx(),
                    )
                    val handleRadius = if (isDraggingHandle) 9.dp.toPx() else 7.dp.toPx()
                    drawCircle(
                        color = activeBarColor,
                        radius = handleRadius,
                        center = Offset(exitX, handleRadius + 4.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius * 0.42f,
                        center = Offset(exitX, handleRadius + 4.dp.toPx()),
                    )
                }
            }
            }
        }

        // Minimap overview bar when zoomed in
        if (zoom > 1.05f) {
            val winFrac = (1f / zoom).coerceIn(0.05f, 1f)
            val exitAbs = (selectedExitFrame.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                val vx = scrollFraction * size.width
                val vw = max(winFrac * size.width, 12.dp.toPx())
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.35f),
                    topLeft = Offset(vx.coerceIn(0f, size.width - vw), 0f),
                    size = Size(min(vw, size.width), size.height),
                    cornerRadius = CornerRadius(999f, 999f),
                )
                drawCircle(
                    color = activeBarColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(exitAbs * size.width, size.height / 2f),
                )
            }
        }

        Text(
            text = when {
                bridgeLocked -> lockedHint ?: stringResource(R.string.flow_editor_bridge_locked)
                peaks == null -> stringResource(R.string.flow_editor_waveform_unavailable)
                else -> stringResource(R.string.flow_editor_waveform_hint)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun viewConfigSlop(): Float = with(LocalDensity.current) { 8.dp.toPx() }

private fun DrawScope.drawWaveformBars(
    peaks: BwavWaveformExtractor.WaveformPeaks?,
    viewStartFrame: Long,
    viewEndFrame: Long,
    totalFrames: Long,
    exitX: Float,
    activeColor: Color,
    dimmedColor: Color,
) {
    val w = size.width
    val h = size.height
    val midY = h * 0.5f
    val maxAmp = (h * 0.42f).coerceAtLeast(8f)
    val barPitch = 4.dp.toPx()
    val barWidth = 2.4.dp.toPx()
    val barCount = (w / barPitch).toInt().coerceIn(24, 160)

    if (peaks == null || peaks.bucketCount <= 0) {
        // Fallback flat envelope ribbon when waveform unavailable
        for (i in 0 until barCount) {
            val x = i * barPitch + barPitch * 0.5f
            val color = if (x <= exitX) activeColor.copy(alpha = 0.45f) else dimmedColor
            val halfH = maxAmp * 0.25f
            drawLine(color = color, start = Offset(x, midY - halfH), end = Offset(x, midY + halfH), strokeWidth = barWidth)
        }
        return
    }

    val buckets = peaks.bucketCount
    val startBucket = ((viewStartFrame.toDouble() / totalFrames) * buckets).toInt().coerceIn(0, buckets - 1)
    val endBucket = ((viewEndFrame.toDouble() / totalFrames) * buckets).toInt().coerceIn(startBucket + 1, buckets)
    val spanBuckets = (endBucket - startBucket).coerceAtLeast(1)

    for (i in 0 until barCount) {
        val b0 = startBucket + (i * spanBuckets) / barCount
        val b1 = (startBucket + ((i + 1) * spanBuckets) / barCount).coerceIn(b0 + 1, buckets)
        var negPeak = 0f
        var posPeak = 0f
        for (b in b0 until b1) {
            if (peaks.minPeaks[b] < negPeak) negPeak = peaks.minPeaks[b]
            if (peaks.maxPeaks[b] > posPeak) posPeak = peaks.maxPeaks[b]
        }
        val amp = max(abs(negPeak), abs(posPeak)).coerceIn(0.06f, 1f)
        val x = i * barPitch + barPitch * 0.5f
        val topY = midY - amp * maxAmp
        val botY = midY + amp * maxAmp
        val color = if (x <= exitX) activeColor.copy(alpha = 0.86f) else dimmedColor
        drawRoundRect(
            color = color,
            topLeft = Offset(x - barWidth * 0.5f, topY),
            size = Size(barWidth, (botY - topY).coerceAtLeast(2f)),
            cornerRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f),
        )
    }
}
