package com.mylittleblover.mkwplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwInsetBox
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRadioMark
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSlider
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwSwitch
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import com.mylittleblover.mkwplayer.ui.unpadHorizontal
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.EqualizerPreset
import com.mylittleblover.mkwplayer.audio.MasterDspStateConfig
import com.mylittleblover.mkwplayer.audio.PlaybackRateConfig
import com.mylittleblover.mkwplayer.library.TrackSortOrder
import com.mylittleblover.mkwplayer.player.InteractiveTimedAction
import com.mylittleblover.mkwplayer.player.InteractiveTimerState
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import com.mylittleblover.mkwplayer.player.SleepTimerMode
import com.mylittleblover.mkwplayer.player.SleepTimerState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun formatCountdownMs(ms: Long): String {
    val totalSec = ((ms.coerceAtLeast(0L) + 999L) / 1000L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

@Composable
internal fun LiveSpectrumVisualizer(
    bands: FloatArray,
    modifier: Modifier = Modifier,
    barColorPrimary: Color = MaterialTheme.colorScheme.primary,
    barColorSecondary: Color = MaterialTheme.colorScheme.tertiary,
) {
    val smoothed = remember { FloatArray(24) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(bands) {
        for (i in 0 until minOf(smoothed.size, bands.size)) {
            val target = bands[i].coerceIn(0f, 1f)
            val prev = smoothed[i]
            smoothed[i] = if (target >= prev) {
                prev + (target - prev) * 0.65f
            } else {
                prev * 0.78f + target * 0.22f
            }
        }
        tick++
    }
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        tick
        val count = smoothed.size
        if (count == 0) return@Canvas
        val gap = (size.width / (count * 3.5f)).coerceIn(2f, 6f)
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
        val brush = Brush.verticalGradient(listOf(barColorSecondary, barColorPrimary))
        for (i in 0 until count) {
            val amp = smoothed[i].coerceIn(0.03f, 1f)
            val barHeight = (size.height * amp).coerceAtLeast(barWidth)
            val x = i * (barWidth + gap)
            val y = size.height - barHeight
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
internal fun EqualizerPresetLabel(preset: EqualizerPreset): String = when (preset) {
    EqualizerPreset.FLAT -> stringResource(R.string.eq_preset_flat)
    EqualizerPreset.BASS_BOOST -> stringResource(R.string.eq_preset_bass_boost)
    EqualizerPreset.RACING -> stringResource(R.string.eq_preset_racing)
    EqualizerPreset.VOCAL -> stringResource(R.string.eq_preset_vocal)
    EqualizerPreset.ELECTRONIC -> stringResource(R.string.eq_preset_electronic)
    EqualizerPreset.ACOUSTIC -> stringResource(R.string.eq_preset_acoustic)
    EqualizerPreset.TREBLE -> stringResource(R.string.eq_preset_treble)
    EqualizerPreset.CUSTOM -> stringResource(R.string.eq_preset_custom)
}

@Composable
private fun MkwLabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    centerZero: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary,
            )
            Text(
                text = valueText,
                style = MkwKitTokens.tabular(MaterialTheme.typography.titleSmall),
                color = if (enabled) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
        MkwSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            centerZero = centerZero,
        )
    }
}

@Composable
internal fun EqualizerContent(
    config: MasterDspStateConfig,
    onUpdate: (MasterDspStateConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val freqLabels = remember { listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "12 kHz") }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MkwCard(
            onClick = { onUpdate(config.copy(eqEnabled = !config.eqEnabled)) },
            selected = config.eqEnabled,
            tone = MkwSurfaceTone.Porcelain,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Equalizer,
                        contentDescription = null,
                        tint = if (config.eqEnabled) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                    )
                    Text(
                        text = stringResource(R.string.eq_enabled),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MkwKitTokens.Palette.ink,
                    )
                }
                MkwSwitch(
                    checked = config.eqEnabled,
                    onCheckedChange = { enabled -> onUpdate(config.copy(eqEnabled = enabled)) },
                )
            }
        }

        // Compact frequency curve preview
        val lineColor = if (config.eqEnabled) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.outlineInk
        val gridColor = MkwKitTokens.Palette.hairline
        MkwInsetBox(
            shape = MkwKitTokens.Shape.card,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Canvas(Modifier.fillMaxWidth().height(78.dp)) {
                val midY = size.height / 2f
                drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 2f)
                val path = Path()
                val pts = config.bandsDb.indices.map { idx ->
                    val x = size.width * (idx / (config.bandsDb.size - 1).coerceAtLeast(1).toFloat())
                    val dbNorm = (config.bandsDb[idx].coerceIn(-12f, 12f) / 12f)
                    val y = midY - dbNorm * (size.height * 0.44f)
                    Offset(x, y)
                }
                if (pts.isNotEmpty()) {
                    path.moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val cur = pts[i]
                        val cx = (prev.x + cur.x) / 2f
                        path.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    pts.forEach { p -> drawCircle(lineColor, radius = 7f, center = p) }
                }
            }
        }

        Text(
            text = stringResource(R.string.eq_preset_label),
            style = MaterialTheme.typography.labelLarge,
            color = MkwKitTokens.Palette.inkSecondary,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().unpadHorizontal(20.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val presets = EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM || config.preset == EqualizerPreset.CUSTOM }
            items(presets, key = { it.name }) { preset ->
                MkwFilterPill(
                    label = EqualizerPresetLabel(preset),
                    selected = config.preset == preset,
                    onClick = {
                        if (preset != EqualizerPreset.CUSTOM) {
                            onUpdate(
                                config.copy(
                                    eqEnabled = if (preset == EqualizerPreset.FLAT) config.eqEnabled else true,
                                    bandsDb = preset.bandsDb,
                                    preset = preset,
                                ),
                            )
                        }
                    },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            config.bandsDb.forEachIndexed { idx, gainDb ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = freqLabels.getOrElse(idx) { "" },
                        style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                        color = MkwKitTokens.Palette.inkSecondary,
                        modifier = Modifier.width(54.dp),
                    )
                    MkwSlider(
                        value = gainDb,
                        onValueChange = { raw ->
                            val snapped = (raw * 2f).roundToInt() / 2f
                            val nextBands = config.bandsDb.toMutableList().apply { set(idx, snapped.coerceIn(-12f, 12f)) }
                            onUpdate(
                                config.copy(
                                    eqEnabled = true,
                                    bandsDb = nextBands,
                                    preset = EqualizerPreset.matchingOrCustom(nextBands),
                                ),
                            )
                        },
                        valueRange = -12f..12f,
                        centerZero = true,
                        activeColor = if (config.eqEnabled) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.outlineInk,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.eq_db_fmt, gainDb),
                        style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                        color = if (config.eqEnabled) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(64.dp),
                    )
                }
            }
        }

        MkwDivider()

        MkwLabeledSlider(
            title = stringResource(R.string.eq_bass_boost, config.bassBoostPercent),
            valueText = "${config.bassBoostPercent}%",
            value = config.bassBoostPercent.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { raw ->
                onUpdate(config.copy(bassBoostPercent = raw.roundToInt().coerceIn(0, 100)))
            },
        )

        MkwLabeledSlider(
            title = stringResource(R.string.eq_loudness_gain, config.loudnessDb),
            valueText = stringResource(R.string.eq_db_fmt, config.loudnessDb),
            value = config.loudnessDb,
            valueRange = 0f..9f,
            onValueChange = { raw ->
                val snapped = ((raw * 2f).roundToInt() / 2f).coerceIn(0f, 9f)
                onUpdate(config.copy(loudnessDb = snapped))
            },
        )

        MkwSecondaryButton(
            text = stringResource(R.string.eq_reset),
            onClick = { onUpdate(MasterDspStateConfig()) },
            leadingIcon = Icons.Filled.Refresh,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun EqualizerScreen(
    config: MasterDspStateConfig,
    onUpdate: (MasterDspStateConfig) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(
                start = 20.dp,
                top = 16.dp,
                end = 20.dp,
                bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
            ),
    ) {
        EqualizerContent(config = config, onUpdate = onUpdate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerSheet(
    config: MasterDspStateConfig,
    onUpdate: (MasterDspStateConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    MkwBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.eq_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            EqualizerContent(config = config, onUpdate = onUpdate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedPitchSheet(
    rate: PlaybackRateConfig,
    onUpdate: (PlaybackRateConfig) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    MkwBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = MkwKitTokens.Palette.crimson)
                    Text(
                        text = stringResource(R.string.speed_pitch_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MkwKitTokens.Palette.ink,
                    )
                }
                MkwGhostButton(
                    text = stringResource(R.string.speed_pitch_reset),
                    onClick = onReset,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MkwLabeledSlider(
                    title = stringResource(R.string.speed_label),
                    valueText = String.format(Locale.ROOT, "%.2fx", rate.speed),
                    value = rate.speed,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { raw ->
                        val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(0.5f, 2.0f)
                        onUpdate(rate.copy(speed = snapped))
                    },
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().unpadHorizontal(20.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) { preset ->
                        MkwFilterPill(
                            label = String.format(Locale.ROOT, "%.2fx", preset),
                            selected = abs(rate.speed - preset) < 0.01f,
                            onClick = { onUpdate(rate.copy(speed = preset)) },
                        )
                    }
                }
            }

            MkwCard(
                onClick = { onUpdate(rate.copy(preservePitch = !rate.preservePitch)) },
                selected = rate.preservePitch,
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.preserve_pitch_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MkwKitTokens.Palette.ink,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    MkwSwitch(
                        checked = rate.preservePitch,
                        onCheckedChange = { preserve -> onUpdate(rate.copy(preservePitch = preserve)) },
                    )
                }
            }

            MkwLabeledSlider(
                title = stringResource(R.string.pitch_label),
                valueText = stringResource(R.string.speed_pitch_semitones_fmt, rate.pitchSemitones),
                value = rate.pitchSemitones,
                valueRange = -12f..12f,
                enabled = rate.preservePitch,
                centerZero = true,
                onValueChange = { raw ->
                    val snapped = ((raw * 2f).roundToInt() / 2f).coerceIn(-12f, 12f)
                    onUpdate(rate.copy(pitchSemitones = snapped))
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(
    state: SleepTimerState,
    canStopAfterTrack: Boolean,
    onStartMinutes: (Int) -> Unit,
    onStartAfterTrack: () -> Unit,
    onExtend5Min: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var customInput by rememberSaveable { mutableStateOf("20") }
    val parsedMin = customInput.trim().toIntOrNull()?.takeIf { it in 1..480 }

    MkwBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MkwKitTokens.Palette.crimson)
                Text(
                    text = stringResource(R.string.sleep_timer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
            }

            if (state.active) {
                MkwCard(
                    tone = MkwSurfaceTone.RoseTint,
                    selected = true,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text(
                        text = when (state.mode) {
                            SleepTimerMode.COUNTDOWN ->
                                stringResource(R.string.sleep_timer_remaining_fmt, formatCountdownMs(state.remainingMs))
                            SleepTimerMode.AFTER_CURRENT_TRACK ->
                                stringResource(R.string.sleep_timer_after_track)
                            SleepTimerMode.OFF -> stringResource(R.string.sleep_timer_off)
                        },
                        style = MkwKitTokens.tabular(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold,
                        color = MkwKitTokens.Palette.crimsonDeep,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.mode == SleepTimerMode.COUNTDOWN) {
                            MkwRoseButton(
                                text = stringResource(R.string.sleep_timer_extend_5m),
                                onClick = onExtend5Min,
                                size = MkwButtonSize.Compact,
                            )
                        }
                        MkwSecondaryButton(
                            text = stringResource(R.string.sleep_timer_cancel),
                            onClick = { onCancel(); onDismiss() },
                            size = MkwButtonSize.Compact,
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().unpadHorizontal(20.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf(10, 15, 30, 45, 60, 90)) { min ->
                    MkwFilterPill(
                        label = stringResource(R.string.sleep_timer_minutes_fmt, min),
                        selected = false,
                        onClick = { onStartMinutes(min); onDismiss() },
                    )
                }
            }

            if (canStopAfterTrack) {
                MkwCard(
                    onClick = { onStartAfterTrack(); onDismiss() },
                    selected = state.mode == SleepTimerMode.AFTER_CURRENT_TRACK,
                    tone = MkwSurfaceTone.Porcelain,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_after_track),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.mode == SleepTimerMode.AFTER_CURRENT_TRACK) {
                            MkwKitTokens.Palette.crimsonDeep
                        } else {
                            MkwKitTokens.Palette.ink
                        },
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_after_track_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MkwTextField(
                    value = customInput,
                    onValueChange = { customInput = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = stringResource(R.string.sleep_timer_custom_min),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                MkwPrimaryButton(
                    text = stringResource(R.string.sleep_timer_start),
                    onClick = { parsedMin?.let { onStartMinutes(it); onDismiss() } },
                    enabled = parsedMin != null,
                    size = MkwButtonSize.Hero,
                )
            }
        }
    }
}

@Composable
internal fun interactiveTimedActionLabel(action: InteractiveTimedAction): String = when (action) {
    is InteractiveTimedAction.FadeStop ->
        if (action.fadeSeconds <= 0) stringResource(R.string.interactive_timer_action_stop_now)
        else stringResource(R.string.interactive_timer_action_fade_stop_fmt, action.fadeSeconds)
    InteractiveTimedAction.Advance -> stringResource(R.string.interactive_timer_action_advance)
    InteractiveTimedAction.ToggleFast -> stringResource(R.string.interactive_timer_action_fast)
    is InteractiveTimedAction.TakeOutro ->
        if (action.number != null) stringResource(R.string.interactive_timer_action_outro_num_fmt, action.number)
        else stringResource(R.string.interactive_timer_action_outro)
    is InteractiveTimedAction.SelectRegion -> stringResource(R.string.interactive_timer_action_region_fmt, action.label)
    is InteractiveTimedAction.SelectVersion -> stringResource(R.string.interactive_timer_action_version_fmt, action.label)
    InteractiveTimedAction.ToggleSpeakerOverlay -> stringResource(R.string.interactive_timer_action_speaker)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InteractiveTimerSheet(
    timer: InteractiveTimerState,
    flow: PlayerViewModel.FlowUi,
    onStart: (Long, InteractiveTimedAction) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val availableActions = remember(
        flow.title,
        flow.hasFast,
        flow.outros,
        flow.regionNumbers,
        flow.versionNumbers,
        flow.hasSpeakerOverlay,
        flow.isRainbowMelodyOverlay,
    ) {
        buildList<InteractiveTimedAction> {
            add(InteractiveTimedAction.FadeStop(3))
            add(InteractiveTimedAction.FadeStop(1))
            add(InteractiveTimedAction.FadeStop(5))
            add(InteractiveTimedAction.FadeStop(0))
            if (flow.canAdvance || flow.sections.size > 1) {
                add(InteractiveTimedAction.Advance)
            }
            if (flow.hasFast) {
                add(InteractiveTimedAction.ToggleFast)
            }
            if (flow.outros.size == 1) {
                add(InteractiveTimedAction.TakeOutro(null))
            } else if (flow.outros.size > 1) {
                add(InteractiveTimedAction.TakeOutro(null))
                flow.outros.forEach { num -> add(InteractiveTimedAction.TakeOutro(num)) }
            }
            flow.regionNumbers.forEach { r -> add(InteractiveTimedAction.SelectRegion(r, r.toString())) }
            flow.versionNumbers.forEach { v -> add(InteractiveTimedAction.SelectVersion(v, v.toString())) }
            if (flow.hasSpeakerOverlay && !flow.isRainbowMelodyOverlay) {
                add(InteractiveTimedAction.ToggleSpeakerOverlay)
            }
        }
    }

    var selectedIdx by rememberSaveable(flow.title) { mutableIntStateOf(0) }
    val safeSelected = availableActions.getOrElse(selectedIdx) { InteractiveTimedAction.FadeStop(3) }
    var delaySecInput by rememberSaveable { mutableStateOf("60") }
    var selectedPresetSec by rememberSaveable { mutableLongStateOf(60L) }
    val parsedSec = delaySecInput.trim().toLongOrNull()?.takeIf { it in 1L..10_800L }

    MkwBottomSheet(
        onDismissRequest = onDismiss,
        footer = {
            MkwPrimaryButton(
                text = stringResource(R.string.interactive_timer_start),
                onClick = {
                    val secs = parsedSec ?: 60L
                    onStart(secs, safeSelected)
                    onDismiss()
                },
                enabled = parsedSec != null,
                size = MkwButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = MkwKitTokens.Palette.crimson)
                Text(
                    text = stringResource(R.string.interactive_timer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
            }
            Text(
                text = stringResource(R.string.interactive_timer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MkwKitTokens.Palette.inkSecondary,
            )

            if (timer.active && timer.action != null) {
                MkwCard(
                    tone = MkwSurfaceTone.RoseTint,
                    selected = true,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val timeStr = formatCountdownMs(timer.remainingMs)
                            Text(
                                text = if (timer.pausedByTransport) {
                                    stringResource(R.string.interactive_timer_paused_badge, timeStr)
                                } else {
                                    stringResource(
                                        R.string.interactive_timer_remaining_fmt,
                                        timeStr,
                                        interactiveTimedActionLabel(timer.action),
                                    )
                                },
                                style = MkwKitTokens.tabular(MaterialTheme.typography.titleSmall),
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.crimsonDeep,
                            )
                            if (timer.pausedByTransport) {
                                Text(
                                    text = interactiveTimedActionLabel(timer.action),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                )
                            }
                        }
                        MkwSecondaryButton(
                            text = stringResource(R.string.interactive_timer_cancel),
                            onClick = { onCancel(); onDismiss() },
                            size = MkwButtonSize.Compact,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.interactive_timer_duration_label),
                style = MaterialTheme.typography.labelLarge,
                color = MkwKitTokens.Palette.inkSecondary,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().unpadHorizontal(20.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = listOf(15L to true, 30L to true, 60L to false, 120L to false, 300L to false, 600L to false)
                items(presets, key = { it.first }) { (sec, isSecLabel) ->
                    val label = if (isSecLabel) {
                        stringResource(R.string.interactive_timer_sec_fmt, sec.toInt())
                    } else {
                        stringResource(R.string.interactive_timer_min_fmt, (sec / 60L).toInt())
                    }
                    MkwFilterPill(
                        label = label,
                        selected = selectedPresetSec == sec && parsedSec == sec,
                        onClick = {
                            selectedPresetSec = sec
                            delaySecInput = sec.toString()
                        },
                    )
                }
            }

            MkwTextField(
                value = delaySecInput,
                onValueChange = { raw -> delaySecInput = raw.filter { ch -> ch.isDigit() }.take(5) },
                label = stringResource(R.string.interactive_timer_custom_seconds),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.interactive_timer_action_label),
                style = MaterialTheme.typography.labelLarge,
                color = MkwKitTokens.Palette.inkSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableActions.forEachIndexed { idx, candidate ->
                    val selected = idx == selectedIdx
                    MkwOptionRow(
                        headline = interactiveTimedActionLabel(candidate),
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { selectedIdx = idx },
                        trailing = { MkwRadioMark(selected = selected) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AbLoopBar(
    aFrame: Long?,
    bFrame: Long?,
    sampleRate: Int,
    enabled: Boolean,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rate = sampleRate.coerceAtLeast(1)
    MkwCard(
        modifier = modifier.fillMaxWidth(),
        selected = aFrame != null && bFrame != null,
        shape = MkwKitTokens.Shape.card,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (aFrame != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Loop, null, Modifier.size(16.dp), tint = MkwKitTokens.Palette.crimson)
                val aText = EngineSnapshot.formatPosition(aFrame, rate)
                val bText = bFrame?.let { EngineSnapshot.formatPosition(it, rate) }
                Text(
                    if (bText != null) stringResource(R.string.ab_loop_badge_ab, aText, bText)
                    else stringResource(R.string.ab_loop_badge_a, aText),
                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                    color = MkwKitTokens.Palette.crimson,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MkwSecondaryButton(
                text = stringResource(R.string.ab_loop_set_a),
                onClick = onSetA,
                enabled = enabled,
                size = MkwButtonSize.Compact,
                modifier = Modifier.weight(1f),
            )
            MkwSecondaryButton(
                text = stringResource(R.string.ab_loop_set_b),
                onClick = onSetB,
                enabled = enabled,
                size = MkwButtonSize.Compact,
                modifier = Modifier.weight(1f),
            )
            if (aFrame != null || bFrame != null) {
                MkwRoseButton(
                    text = stringResource(R.string.ab_loop_clear),
                    onClick = onClear,
                    size = MkwButtonSize.Compact,
                )
            }
        }
    }
}

@Composable
internal fun TrackSortBar(
    selected: TrackSortOrder,
    includeOstOrder: Boolean,
    onSelect: (TrackSortOrder) -> Unit,
    modifier: Modifier = Modifier,
    horizontalEdgePadding: Dp = 16.dp,
) {
    val options = remember(includeOstOrder) {
        buildList {
            add(TrackSortOrder.DEFAULT to R.string.sort_default)
            if (includeOstOrder) add(TrackSortOrder.OST_INDEX to R.string.sort_ost_index)
            add(TrackSortOrder.NAME_ASC to R.string.sort_name_asc)
            add(TrackSortOrder.DURATION_DESC to R.string.sort_duration_desc)
            add(TrackSortOrder.MOST_PLAYED to R.string.sort_most_played)
        }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = horizontalEdgePadding),
    ) {
        items(options, key = { it.first.name }) { (order, labelRes) ->
            MkwFilterPill(
                label = stringResource(labelRes),
                selected = selected == order,
                onClick = { onSelect(order) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToTrackActionBox(
    enabled: Boolean = true,
    onSwipeStartToEnd: (() -> Unit)? = null,
    startToEndIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
    startToEndLabel: String = stringResource(R.string.swipe_action_play_next),
    onSwipeEndToStart: (() -> Unit)? = null,
    endToStartIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.FavoriteBorder,
    endToStartLabel: String = stringResource(R.string.swipe_action_favorite),
    endToStartDestructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!enabled || (onSwipeStartToEnd == null && onSwipeEndToStart == null)) {
        content()
        return
    }
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSwipeStartToEnd?.invoke()
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSwipeEndToStart?.invoke()
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // Snap back cleanly instead of removing the row visually
        },
        positionalThreshold = { distance -> distance * 0.28f },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = onSwipeStartToEnd != null,
        enableDismissFromEndToStart = onSwipeEndToStart != null,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bgColor by animateColorAsState(
                targetValue = when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> MkwKitTokens.Palette.rose
                    SwipeToDismissBoxValue.EndToStart ->
                        if (endToStartDestructive) MkwKitTokens.Palette.dangerTint
                        else MkwKitTokens.Palette.roseHover
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                animationSpec = tween(140),
                label = "swipe-bg",
            )
            val align = if (dir == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
            val contentTint = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> MkwKitTokens.Palette.crimsonDeep
                SwipeToDismissBoxValue.EndToStart ->
                    if (endToStartDestructive) MkwKitTokens.Palette.danger
                    else MkwKitTokens.Palette.crimsonDeep
                SwipeToDismissBoxValue.Settled -> MkwKitTokens.Palette.ink
            }
            Box(
                Modifier.fillMaxSize().clip(MkwKitTokens.Shape.control).background(bgColor).padding(horizontal = 20.dp),
                contentAlignment = align,
            ) {
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(startToEndIcon, null, tint = contentTint)
                        Text(startToEndLabel, color = contentTint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    SwipeToDismissBoxValue.EndToStart -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(endToStartLabel, color = contentTint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Icon(endToStartIcon, null, tint = contentTint)
                    }
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        },
        content = { content() },
    )
}

internal class DragReorderController(
    val listState: LazyListState,
    private val sectionStartIndex: () -> Int = { 0 },
    private val itemCountProvider: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragDeltaY by mutableFloatStateOf(0f)
        private set

    fun onDragStart(itemIndex: Int) {
        draggingIndex = itemIndex
        dragDeltaY = 0f
    }

    fun onDrag(deltaY: Float) {
        val currentIdx = draggingIndex ?: return
        val maxIdx = itemCountProvider() - 1
        if (currentIdx !in 0..maxIdx) return
        dragDeltaY += deltaY
        val baseOffset = sectionStartIndex()
        val adapterIdx = baseOffset + currentIdx
        val items: List<LazyListItemInfo> = listState.layoutInfo.visibleItemsInfo
        val currentInfo = items.firstOrNull { it.index == adapterIdx } ?: return
        val center = currentInfo.offset + dragDeltaY + currentInfo.size / 2f
        if (dragDeltaY > 0f && currentIdx < maxIdx) {
            val nextInfo = items.firstOrNull { it.index == adapterIdx + 1 } ?: return
            if (center > nextInfo.offset + nextInfo.size / 2f) {
                onMove(currentIdx, currentIdx + 1)
                draggingIndex = currentIdx + 1
                dragDeltaY -= nextInfo.size
            }
        } else if (dragDeltaY < 0f && currentIdx > 0) {
            val prevInfo = items.firstOrNull { it.index == adapterIdx - 1 } ?: return
            if (center < prevInfo.offset + prevInfo.size / 2f) {
                onMove(currentIdx, currentIdx - 1)
                draggingIndex = currentIdx - 1
                dragDeltaY += prevInfo.size
            }
        }
    }

    fun onDragEnd() {
        draggingIndex = null
        dragDeltaY = 0f
    }
}

@Composable
internal fun DragHandleButton(
    index: Int,
    controller: DragReorderController,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentIdx by rememberUpdatedState(index)
    Icon(
        imageVector = Icons.Filled.DragHandle,
        contentDescription = stringResource(R.string.queue_drag_reorder_hint),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(36.dp)
            .padding(6.dp)
            .pointerInput(controller) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.onDragStart(currentIdx)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        controller.onDrag(dragAmount.y)
                    },
                    onDragEnd = { controller.onDragEnd() },
                    onDragCancel = { controller.onDragEnd() },
                )
            },
    )
}
