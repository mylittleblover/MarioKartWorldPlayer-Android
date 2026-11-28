package com.mylittleblover.mkwplayer.ui.markers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.library.TrackMarker
import com.mylittleblover.mkwplayer.library.TrackMarkerColorOption
import com.mylittleblover.mkwplayer.library.TrackMarkerColors
import com.mylittleblover.mkwplayer.library.TrackMarkerStore
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwDangerButton
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwInsetBox
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSliderMarker
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import com.mylittleblover.mkwplayer.ui.rememberPressScale
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import java.util.Locale

private const val SWATCH_TRANSITION_MS = 140

/** Formats a marker timestamp in milliseconds as `mm:ss` (or `h:mm:ss` when >= 1 hour). */
internal fun formatMarkerTimestampMs(positionMs: Long): String {
    val totalSec = positionMs.coerceAtLeast(0L) / 1000L
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** Projects saved [TrackMarker] positions onto [com.mylittleblover.mkwplayer.ui.kit.MkwSlider] tick markers. */
internal fun List<TrackMarker>.toSliderMarkers(durationMs: Long): List<MkwSliderMarker> {
    if (durationMs <= 0L || isEmpty()) return emptyList()
    return map { marker ->
        MkwSliderMarker(
            startFraction = (marker.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
            endFraction = null,
            color = Color(TrackMarkerColors.normalize(marker.colorArgb)),
        )
    }
}

/**
 * Porcelain dialog for saving a timestamped bookmark on the currently playing track.
 *
 * Displays the formatted current timestamp (`mm:ss`), marker name text field with a localized
 * default placeholder (e.g. `Marker 1` / `标记 1`), an 8-color MD3 circular swatch selector with
 * animated focus ring & checkmark (`>= 48.dp` touch targets), and Save/Cancel actions.
 */
@Composable
internal fun AddTrackMarkerDialog(
    positionMs: Long,
    initialLabel: String = "",
    onDismiss: () -> Unit,
    onConfirm: (label: String, colorArgb: Long) -> Unit,
    initialColorArgb: Long = TrackMarkerColors.DEFAULT,
    suggestedIndex: Int = 1,
) {
    val reducedMotion = rememberReducedMotion()
    val normalizedInitialColor = remember(initialColorArgb) {
        TrackMarkerColors.normalize(initialColorArgb)
    }
    var labelText by rememberSaveable(positionMs, initialLabel) {
        mutableStateOf(initialLabel.take(TrackMarkerStore.MAX_LABEL_LENGTH))
    }
    var selectedColorArgb by rememberSaveable(positionMs, normalizedInitialColor) {
        mutableLongStateOf(normalizedInitialColor)
    }

    val defaultPlaceholder = stringResource(R.string.marker_default_name_fmt, suggestedIndex.coerceAtLeast(1))
    val formattedTime = remember(positionMs) { formatMarkerTimestampMs(positionMs) }
    val selectedColor by animateColorAsState(
        targetValue = Color(TrackMarkerColors.normalize(selectedColorArgb)),
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else SWATCH_TRANSITION_MS,
            easing = AppMotion.easeOut,
        ),
        label = "marker-dialog-accent",
    )

    fun commit() {
        val resolvedLabel = labelText.trim().ifEmpty { defaultPlaceholder }.take(TrackMarkerStore.MAX_LABEL_LENGTH)
        onConfirm(resolvedLabel, TrackMarkerColors.normalize(selectedColorArgb))
    }

    MkwDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.marker_add_dialog_title),
        dismissButton = {
            MkwSecondaryButton(
                text = stringResource(R.string.marker_action_cancel),
                onClick = onDismiss,
                size = MkwButtonSize.Compact,
            )
        },
        confirmButton = {
            MkwPrimaryButton(
                text = stringResource(R.string.marker_action_save),
                onClick = ::commit,
                size = MkwButtonSize.Compact,
            )
        },
    ) {
        MkwInsetBox(
            shape = MkwKitTokens.Shape.control,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = selectedColor,
                    )
                    Text(
                        text = stringResource(R.string.marker_timestamp_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
                Surface(
                    shape = MkwKitTokens.Shape.pill,
                    color = selectedColor.copy(alpha = 0.14f),
                    border = BorderStroke(MkwKitTokens.Stroke.hairline, selectedColor.copy(alpha = 0.42f)),
                ) {
                    Text(
                        text = formattedTime,
                        style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                        fontWeight = FontWeight.Bold,
                        color = selectedColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        MkwTextField(
            value = labelText,
            onValueChange = { labelText = it.take(TrackMarkerStore.MAX_LABEL_LENGTH) },
            label = stringResource(R.string.marker_name_label),
            placeholder = defaultPlaceholder,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.marker_color_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MkwKitTokens.Palette.inkSecondary,
            )
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TrackMarkerColors.options.chunked(4).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowOptions.forEach { option ->
                            MarkerColorSwatch(
                                option = option,
                                selected = TrackMarkerColors.normalize(selectedColorArgb) == option.argb,
                                reducedMotion = reducedMotion,
                                onSelect = { selectedColorArgb = option.argb },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerColorSwatch(
    option: TrackMarkerColorOption,
    selected: Boolean,
    reducedMotion: Boolean,
    onSelect: () -> Unit,
) {
    val swatchColor = Color(option.argb)
    val colorName = localizedMarkerColorName(option.key)
    val duration = if (reducedMotion) 0 else SWATCH_TRANSITION_MS
    val ringColor by animateColorAsState(
        targetValue = if (selected) swatchColor else MkwKitTokens.Palette.hairline,
        animationSpec = tween(durationMillis = duration, easing = AppMotion.easeOut),
        label = "marker-swatch-ring-color",
    )
    val ringScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = tween(durationMillis = duration, easing = AppMotion.easeOut),
        label = "marker-swatch-ring-scale",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = duration, easing = AppMotion.easeOut),
        label = "marker-swatch-check-alpha",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = tween(durationMillis = duration, easing = AppMotion.easeOut),
        label = "marker-swatch-check-scale",
    )
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.94f)

    Box(
        modifier = Modifier
            .size(MkwKitTokens.Size.touchMin)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .then(pressScale)
            .semantics { contentDescription = colorName },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = ringScale
                    scaleY = ringScale
                }
                .border(
                    width = if (selected) 2.dp else MkwKitTokens.Stroke.hairline,
                    color = ringColor,
                    shape = CircleShape,
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(swatchColor),
                contentAlignment = Alignment.Center,
            ) {
                if (checkAlpha > 0.02f) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                alpha = checkAlpha.coerceIn(0f, 1f)
                                scaleX = checkScale
                                scaleY = checkScale
                            },
                    )
                }
            }
        }
    }
}

/**
 * Material 3 bottom sheet shown when long-pressing a track and choosing "Play from marker".
 *
 * Uses [MkwBottomSheet] (drag handle, pure white ground, hairline Porcelain rows >= 52.dp, zero
 * artificial list stagger for high-frequency track picking) and active empty-state guidance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayFromMarkerSheet(
    trackTitle: String,
    markers: List<TrackMarker>,
    onSelectMarker: (TrackMarker) -> Unit,
    onDeleteMarker: ((TrackMarker) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val sortedMarkers = remember(markers) {
        markers.sortedWith(compareBy<TrackMarker>({ it.positionMs }, { it.createdAtEpochMs }, { it.id }))
    }

    MkwBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.marker_play_from_title),
        subtitle = trackTitle.takeIf { it.isNotBlank() },
    ) {
        if (sortedMarkers.isEmpty()) {
            MkwInsetBox(
                shape = MkwKitTokens.Shape.control,
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MkwKitTokens.Spacing.lg, vertical = MkwKitTokens.Spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MkwKitTokens.Palette.crimson,
                    )
                    Text(
                        text = stringResource(R.string.marker_sheet_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MkwKitTokens.Palette.inkSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sortedMarkers, key = { it.id }) { marker ->
                    MarkerSheetRow(
                        marker = marker,
                        onSelect = { onSelectMarker(marker) },
                        onDelete = onDeleteMarker?.let { cb -> { cb(marker) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkerSheetRow(
    marker: TrackMarker,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val markerColor = Color(TrackMarkerColors.normalize(marker.colorArgb))
    val timeText = formatMarkerTimestampMs(marker.positionMs)

    MkwOptionRow(
        headline = marker.label,
        supporting = timeText,
        onClick = onSelect,
        leading = {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(markerColor.copy(alpha = 0.14f))
                    .border(MkwKitTokens.Stroke.hairline, markerColor.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(markerColor),
                )
            }
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MkwKitTokens.Palette.crimson,
                )
                if (onDelete != null) {
                    MkwIconButton(
                        icon = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.marker_delete_cd, marker.label),
                        onClick = onDelete,
                        visualSize = 36.dp,
                        iconSize = 18.dp,
                        tintOverride = MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }
        },
    )
}

/**
 * Compact PlayScreen strip showing a "+ Add marker" pill ([MkwFilterPill]) followed by horizontally
 * scrollable colored marker pills (`[dot] label · mm:ss`). Tap a marker pill to seek immediately;
 * long-press to delete it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayScreenMarkerStrip(
    markers: List<TrackMarker>,
    durationMs: Long,
    onAddMarker: () -> Unit,
    onJumpToMarker: (TrackMarker) -> Unit,
    onDeleteMarker: (TrackMarker) -> Unit,
    modifier: Modifier = Modifier,
    confirmDelete: Boolean = true,
) {
    val reducedMotion = rememberReducedMotion()
    val sortedMarkers = remember(markers) {
        markers.sortedWith(compareBy<TrackMarker>({ it.positionMs }, { it.createdAtEpochMs }, { it.id }))
    }
    val haptic = LocalHapticFeedback.current
    var pendingDelete by remember { mutableStateOf<TrackMarker?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160, easing = AppMotion.easeOut)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MkwFilterPill(
            label = stringResource(R.string.marker_add_chip),
            selected = true,
            onClick = onAddMarker,
            leadingIcon = Icons.Filled.BookmarkAdd,
            role = Role.Button,
        )

        AnimatedVisibility(
            visible = sortedMarkers.isNotEmpty(),
            enter = if (reducedMotion) fadeIn(tween(100)) else fadeIn(tween(150, easing = AppMotion.easeOut)),
            exit = if (reducedMotion) fadeOut(tween(90)) else fadeOut(tween(120)),
            modifier = Modifier.weight(1f),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(sortedMarkers, key = { it.id }) { marker ->
                    val clampedMs = if (durationMs > 0L) marker.positionMs.coerceIn(0L, durationMs) else marker.positionMs
                    val timeText = formatMarkerTimestampMs(clampedMs)
                    val markerColor = Color(TrackMarkerColors.normalize(marker.colorArgb))
                    val chipCd = stringResource(R.string.marker_chip_cd_fmt, marker.label, timeText)
                    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.97f)

                    Surface(
                        shape = MkwKitTokens.Shape.pill,
                        color = markerColor.copy(alpha = 0.09f),
                        border = BorderStroke(MkwKitTokens.Stroke.border, markerColor.copy(alpha = 0.42f)),
                        modifier = Modifier
                            .animateItem()
                            .defaultMinSize(minHeight = MkwKitTokens.Size.pillHeight)
                            .clip(MkwKitTokens.Shape.pill)
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                role = Role.Button,
                                onClick = { onJumpToMarker(marker) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (confirmDelete) {
                                        pendingDelete = marker
                                    } else {
                                        onDeleteMarker(marker)
                                    }
                                },
                            )
                            .then(pressScale)
                            .semantics { contentDescription = chipCd },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(markerColor),
                            )
                            Text(
                                text = marker.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MkwKitTokens.Palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 124.dp),
                            )
                            Text(
                                text = "\u00B7",
                                style = MaterialTheme.typography.labelSmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                            Text(
                                text = timeText,
                                style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                                fontWeight = FontWeight.Medium,
                                color = MkwKitTokens.Palette.inkSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    val targetDelete = pendingDelete
    if (targetDelete != null) {
        val deleteTime = formatMarkerTimestampMs(targetDelete.positionMs)
        MkwDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.marker_delete_title),
            dismissButton = {
                MkwSecondaryButton(
                    text = stringResource(R.string.marker_action_cancel),
                    onClick = { pendingDelete = null },
                    size = MkwButtonSize.Compact,
                )
            },
            confirmButton = {
                MkwDangerButton(
                    text = stringResource(R.string.marker_action_delete),
                    onClick = {
                        pendingDelete = null
                        onDeleteMarker(targetDelete)
                    },
                    size = MkwButtonSize.Compact,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.marker_delete_body_fmt, targetDelete.label, deleteTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MkwKitTokens.Palette.inkSecondary,
            )
        }
    }
}

@Composable
private fun localizedMarkerColorName(key: String): String = when (key) {
    "crimson" -> stringResource(R.string.marker_color_crimson)
    "amber" -> stringResource(R.string.marker_color_amber)
    "emerald" -> stringResource(R.string.marker_color_emerald)
    "sky" -> stringResource(R.string.marker_color_sky)
    "violet" -> stringResource(R.string.marker_color_violet)
    "pink" -> stringResource(R.string.marker_color_pink)
    "orange" -> stringResource(R.string.marker_color_orange)
    "slate" -> stringResource(R.string.marker_color_slate)
    else -> stringResource(R.string.marker_color_crimson)
}
