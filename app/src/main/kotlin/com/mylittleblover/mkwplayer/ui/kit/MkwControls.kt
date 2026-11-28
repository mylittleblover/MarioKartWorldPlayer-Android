package com.mylittleblover.mkwplayer.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.rememberPressScale
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure math helpers for [MkwSlider], kept separate from Compose state for deterministic tests. */
internal object MkwSliderMath {
    fun fractionForValue(value: Float, valueRange: ClosedFloatingPointRange<Float>): Float {
        val span = valueRange.endInclusive - valueRange.start
        if (span <= 0f || !value.isFinite()) return 0f
        return ((value - valueRange.start) / span).coerceIn(0f, 1f)
    }

    fun valueForFraction(
        fraction: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
    ): Float {
        val clamped = fraction.coerceIn(0f, 1f)
        val raw = valueRange.start + (valueRange.endInclusive - valueRange.start) * clamped
        return snapValue(raw, valueRange, steps)
    }

    fun snapValue(
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
    ): Float {
        val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return clamped
        val intervals = steps + 1
        val span = valueRange.endInclusive - valueRange.start
        if (span <= 0f) return valueRange.start
        val stepSize = span / intervals
        val snapped = valueRange.start + (((clamped - valueRange.start) / stepSize).roundToInt() * stepSize)
        return snapped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    /** Returns normalized `(startFraction, endFraction)` for both left-to-right and center-zero bars. */
    fun fillFractions(
        fraction: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        centerZero: Boolean,
    ): Pair<Float, Float> {
        val f = fraction.coerceIn(0f, 1f)
        if (!centerZero) return 0f to f
        val zeroFraction = fractionForValue(0f, valueRange)
        return minOf(zeroFraction, f) to maxOf(zeroFraction, f)
    }
}

internal data class MkwSliderMarker(
    val startFraction: Float,
    val endFraction: Float? = null,
    val color: Color = MkwKitTokens.Palette.crimson,
)

/**
 * Unified pill replacing raw MD3 `FilterChip` and `AssistChip`: full pill geometry matching
 * [MkwButton], `1.dp` hairline border, rose active fill, and tactile press-scale spring.
 */
@Composable
internal fun MkwFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    role: Role = Role.Checkbox,
) {
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> MkwKitTokens.Palette.trackStrong
            selected -> MkwKitTokens.Palette.rose
            else -> MkwKitTokens.Palette.ground
        },
        animationSpec = tween(150),
        label = "pill-bg",
    )
    val borderCol = when {
        !enabled -> MkwKitTokens.Palette.hairline
        selected -> MkwKitTokens.Palette.crimson.copy(alpha = 0.55f)
        else -> MkwKitTokens.Palette.hairline
    }
    val contentCol = when {
        !enabled -> MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.45f)
        selected -> MkwKitTokens.Palette.crimsonDeep
        else -> MkwKitTokens.Palette.ink
    }
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.97f)

    Box(
        modifier = modifier.defaultMinSize(minHeight = MkwKitTokens.Size.pillHeight),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = MkwKitTokens.Shape.pill,
            color = bg,
            border = BorderStroke(MkwKitTokens.Stroke.border, borderCol),
            modifier = Modifier
                .heightIn(min = MkwKitTokens.Size.pillHeight)
                .then(if (enabled) pressScale else Modifier)
                .semantics { this.role = role },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentCol)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentCol,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (trailingIcon != null) {
                    Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentCol)
                }
            }
        }
    }
}

/**
 * Custom tactile spring toggle capsule (`48.dp x 28.dp`) replacing chunky stock MD3 `Switch`.
 * Preserves [Role.Switch] and `48.dp` accessible tap bounds.
 */
@Composable
internal fun MkwSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduceMotion = rememberReducedMotion()
    val thumbFraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else AppMotion.snapSpring,
        label = "mkw-switch-thumb",
    )
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> MkwKitTokens.Palette.crimson.copy(alpha = 0.35f)
            !enabled -> MkwKitTokens.Palette.trackStrong
            checked -> MkwKitTokens.Palette.crimson
            else -> MkwKitTokens.Palette.trackStrong
        },
        animationSpec = tween(if (reduceMotion) 0 else 160),
        label = "mkw-switch-track",
    )
    val borderCol = when {
        checked && enabled -> MkwKitTokens.Palette.crimson
        else -> MkwKitTokens.Palette.hairline
    }
    val density = LocalDensity.current
    val travelPx = with(density) {
        (MkwKitTokens.Size.switchWidth - MkwKitTokens.Size.switchThumb - 6.dp).toPx()
    }
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.96f)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MkwKitTokens.Size.touchMin, minHeight = MkwKitTokens.Size.touchMin)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .then(if (enabled) pressScale else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = MkwKitTokens.Size.switchWidth, height = MkwKitTokens.Size.switchHeight)
                .clip(MkwKitTokens.Shape.pill)
                .background(trackColor)
                .border(MkwKitTokens.Stroke.border, borderCol, MkwKitTokens.Shape.pill)
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) MkwKitTokens.Palette.ground else MkwKitTokens.Palette.ground.copy(alpha = 0.85f),
                shadowElevation = if (enabled) 1.5.dp else 0.dp,
                border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.08f)),
                modifier = Modifier
                    .size(MkwKitTokens.Size.switchThumb)
                    .offset { IntOffset((thumbFraction * travelPx).roundToInt(), 0) },
                content = {},
            )
        }
    }
}

/** Crisp custom Canvas circular radio indicator replacing stock MD3 `RadioButton`. */
@Composable
internal fun MkwRadioMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduceMotion = rememberReducedMotion()
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else AppMotion.snapSpring,
        label = "mkw-radio-dot",
    )
    val accent = if (enabled) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.38f)
    val idleRing = if (enabled) MkwKitTokens.Palette.outlineInk else MkwKitTokens.Palette.hairline

    Canvas(modifier = modifier.size(MkwKitTokens.Size.indicator)) {
        val radius = size.minDimension / 2f
        val strokePx = 1.8.dp.toPx()
        drawCircle(
            color = if (selected) accent else idleRing,
            radius = radius - strokePx / 2f,
            style = Stroke(width = strokePx),
        )
        if (dotScale > 0.02f) {
            drawCircle(
                color = accent,
                radius = (radius - 4.5.dp.toPx()).coerceAtLeast(1f) * dotScale,
            )
        }
    }
}

/** Crisp custom Canvas rounded checkbox indicator replacing stock MD3 `Checkbox`. */
@Composable
internal fun MkwCheckboxMark(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduceMotion = rememberReducedMotion()
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else AppMotion.snapSpring,
        label = "mkw-check",
    )
    val fillCol by animateColorAsState(
        targetValue = when {
            !enabled && checked -> MkwKitTokens.Palette.crimson.copy(alpha = 0.38f)
            checked -> MkwKitTokens.Palette.crimson
            else -> MkwKitTokens.Palette.ground
        },
        animationSpec = tween(if (reduceMotion) 0 else 140),
        label = "mkw-check-bg",
    )
    val borderCol = if (checked) fillCol else if (enabled) MkwKitTokens.Palette.outlineInk else MkwKitTokens.Palette.hairline

    Canvas(modifier = modifier.size(MkwKitTokens.Size.indicator)) {
        val cornerPx = 6.dp.toPx()
        val strokePx = 1.6.dp.toPx()
        drawRoundRect(
            color = fillCol,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        drawRoundRect(
            color = borderCol,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            style = Stroke(width = strokePx),
        )
        if (progress > 0.05f) {
            val checkPath = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.69f)
                lineTo(size.width * 0.75f, size.height * 0.34f)
            }
            drawPath(
                path = checkPath,
                color = Color.White.copy(alpha = progress.coerceIn(0f, 1f)),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * Unified rounded `14.dp` option/list row replacing raw MD3 `ListItem`. Highlights with soft rose
 * tint + subtle crimson hairline border when [selected].
 */
@Composable
internal fun MkwOptionRow(
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    role: Role = Role.Button,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val bgColor = when {
        selected -> MkwKitTokens.Palette.rose.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val borderStroke = if (selected) {
        BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.4f))
    } else {
        null
    }
    val titleColor = when {
        !enabled -> MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.45f)
        destructive -> MkwKitTokens.Palette.danger
        selected -> MkwKitTokens.Palette.crimsonDeep
        else -> MkwKitTokens.Palette.ink
    }
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.985f)
    val rowShape = MkwKitTokens.Shape.control
    val actionModifier = if (role == Role.RadioButton || role == Role.Tab) {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = role,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier.clickable(
            enabled = enabled,
            role = role,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    }

    Surface(
        shape = rowShape,
        color = bgColor,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MkwKitTokens.Size.optionRowMin)
            .clip(rowShape)
            .then(actionModifier)
            .then(if (enabled) pressScale else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) {
                leading()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supporting.isNullOrBlank()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

/**
 * Custom porcelain `14.dp` flat/recessed text field replacing MD3 cutout-border `OutlinedTextField`.
 */
@Composable
internal fun MkwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderCol by animateColorAsState(
        targetValue = when {
            isError -> MkwKitTokens.Palette.danger
            focused -> MkwKitTokens.Palette.crimson
            else -> MkwKitTokens.Palette.hairline
        },
        animationSpec = tween(150),
        label = "mkw-field-border",
    )
    val bgCol by animateColorAsState(
        targetValue = when {
            !enabled -> MkwKitTokens.Palette.trackStrong
            focused -> MkwKitTokens.Palette.ground
            else -> MkwKitTokens.Palette.card
        },
        animationSpec = tween(150),
        label = "mkw-field-bg",
    )
    val strokeWidth = if (focused || isError) MkwKitTokens.Stroke.focused else MkwKitTokens.Stroke.border

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) MkwKitTokens.Palette.danger else MkwKitTokens.Palette.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            interactionSource = interactionSource,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary,
            ),
            cursorBrush = SolidColor(MkwKitTokens.Palette.crimson),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MkwKitTokens.Shape.control)
                        .background(bgCol)
                        .border(strokeWidth, borderCol, MkwKitTokens.Shape.control)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (leadingIcon != null) leadingIcon()
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) trailingIcon()
                }
            },
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MkwKitTokens.Palette.danger else MkwKitTokens.Palette.inkSecondary,
            )
        }
    }
}

/**
 * Custom Canvas slider unifying continuous playback scrubbers (`6.dp -> 10.dp` active swell +
 * optional A-B marker bands) and center-zero diverging parameter controls (`Equalizer`, `Pitch`).
 */
@Composable
internal fun MkwSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    centerZero: Boolean = false,
    showThumb: Boolean = true,
    steps: Int = 0,
    markers: List<MkwSliderMarker> = emptyList(),
    activeColor: Color = MkwKitTokens.Palette.crimson,
    smoothProgress: Boolean = false,
    snapImmediate: Boolean = false,
    resetKey: Any? = null,
    specialStyle: MkwSliderSpecialStyle? = null,
) {
    val reduceMotion = rememberReducedMotion()
    var dragging by remember { mutableStateOf(false) }
    val rawTargetFraction = MkwSliderMath.fractionForValue(value, valueRange)
    var dragFraction by remember { mutableFloatStateOf(rawTargetFraction) }
    val latestValueChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)
    val latestRange by rememberUpdatedState(valueRange)
    val latestSteps by rememberUpdatedState(steps)

    val smoothAnimatable = remember(resetKey) { Animatable(rawTargetFraction) }
    LaunchedEffect(smoothProgress, rawTargetFraction, dragging, snapImmediate, reduceMotion, resetKey) {
        if (!smoothProgress) return@LaunchedEffect
        val target = if (dragging) dragFraction else rawTargetFraction
        if (dragging || snapImmediate || reduceMotion) {
            smoothAnimatable.snapTo(target)
        } else {
            val current = smoothAnimatable.value
            val delta = target - current
            when {
                delta < -0.04f -> {
                    // Loop wrap (e.g. A-B point or natural track loop return): smooth glide-back translation.
                    smoothAnimatable.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 280, easing = AppMotion.easeOut),
                    )
                }
                delta >= -0.005f && delta < 0.08f -> {
                    // Normal forward poll advancement: continuous linear interpolation matching interactive bar.
                    smoothAnimatable.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = AppMotion.duration, easing = LinearEasing),
                    )
                }
                else -> {
                    smoothAnimatable.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 180, easing = AppMotion.easeOut),
                    )
                }
            }
        }
    }

    val displayedFraction = when {
        dragging -> dragFraction
        smoothProgress -> smoothAnimatable.value
        else -> rawTargetFraction
    }
    val trackHeight by animateDpAsState(
        targetValue = if (dragging && !reduceMotion) 10.dp else 6.dp,
        animationSpec = if (reduceMotion) tween(0) else AppMotion.snapDpSpring,
        label = "mkw-slider-height",
    )
    val dragThumbScale by animateFloatAsState(
        targetValue = if (showThumb || dragging) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else AppMotion.snapSpring,
        label = "mkw-slider-thumb-scale",
    )
    val fillCol = if (enabled) activeColor else MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.4f)
    val trackCol = MkwKitTokens.Palette.trackStrong
    val specialState = rememberSpecialBarVisualState(
        specialStyle = specialStyle,
        resetKey = resetKey,
        reduceMotion = reduceMotion,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .semantics(mergeDescendants = true) {
                setProgress { targetValue ->
                    if (!enabled) return@setProgress false
                    val clamped = MkwSliderMath.snapValue(targetValue, valueRange, steps)
                    latestValueChange(clamped)
                    latestFinished()
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val fraction = (offset.x / width).coerceIn(0f, 1f)
                    latestValueChange(MkwSliderMath.valueForFraction(fraction, latestRange, latestSteps))
                    latestFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        dragging = true
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = (startOffset.x / width).coerceIn(0f, 1f)
                        latestValueChange(MkwSliderMath.valueForFraction(dragFraction, latestRange, latestSteps))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = (change.position.x / width).coerceIn(0f, 1f)
                        latestValueChange(MkwSliderMath.valueForFraction(dragFraction, latestRange, latestSteps))
                    },
                    onDragEnd = {
                        dragging = false
                        latestFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        latestFinished()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val thumbPadPx = with(LocalDensity.current) { if (showThumb) 8.dp.toPx() else 0f }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showThumb) 24.dp else 14.dp),
        ) {
            val usableW = (size.width - thumbPadPx * 2f).coerceAtLeast(1f)
            val barH = trackHeight.toPx()
            val barTop = (size.height - barH) / 2f
            val corner = CornerRadius(barH / 2f, barH / 2f)
            val (fillStart, fillEnd) = MkwSliderMath.fillFractions(displayedFraction, valueRange, centerZero)
            val fillLeft = thumbPadPx + fillStart * usableW
            val fillWidth = (fillEnd - fillStart) * usableW

            if (specialState.currentStyle != null) {
                drawSpecialProgressBar(
                    visualState = specialState,
                    trackLeft = thumbPadPx,
                    barTop = barTop,
                    usableW = usableW,
                    barH = barH,
                    corner = corner,
                    fillLeft = fillLeft,
                    fillWidth = fillWidth,
                    baseTrackColor = trackCol,
                    enabled = enabled,
                )
            } else {
                drawRoundRect(
                    color = trackCol,
                    topLeft = Offset(thumbPadPx, barTop),
                    size = Size(usableW, barH),
                    cornerRadius = corner,
                )
            }

            markers.forEach { marker ->
                val startF = marker.startFraction.coerceIn(0f, 1f)
                val endF = marker.endFraction?.coerceIn(0f, 1f)
                if (endF != null && abs(endF - startF) > 0.002f) {
                    val leftX = thumbPadPx + minOf(startF, endF) * usableW
                    val spanW = abs(endF - startF) * usableW
                    drawRoundRect(
                        color = marker.color.copy(alpha = 0.28f),
                        topLeft = Offset(leftX, barTop),
                        size = Size(spanW, barH),
                        cornerRadius = corner,
                    )
                }
                val tickX = thumbPadPx + startF * usableW
                drawLine(
                    color = marker.color,
                    start = Offset(tickX, barTop - 3.dp.toPx()),
                    end = Offset(tickX, barTop + barH + 3.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (endF != null) {
                    val tickEndX = thumbPadPx + endF * usableW
                    drawLine(
                        color = marker.color,
                        start = Offset(tickEndX, barTop - 3.dp.toPx()),
                        end = Offset(tickEndX, barTop + barH + 3.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            if (centerZero) {
                val zeroX = thumbPadPx + MkwSliderMath.fractionForValue(0f, valueRange) * usableW
                drawLine(
                    color = MkwKitTokens.Palette.outlineInk.copy(alpha = 0.65f),
                    start = Offset(zeroX, barTop - 4.dp.toPx()),
                    end = Offset(zeroX, barTop + barH + 4.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            if (specialState.currentStyle == null && fillWidth > 1f) {
                drawRoundRect(
                    color = fillCol,
                    topLeft = Offset(fillLeft, barTop),
                    size = Size(fillWidth, barH),
                    cornerRadius = corner,
                )
            }

            if (dragThumbScale > 0.02f) {
                val baseThumbR = if (dragging) 9.dp.toPx() else 7.dp.toPx()
                val thumbR = baseThumbR * dragThumbScale
                val minX = maxOf(thumbPadPx, thumbR)
                val maxX = minOf(size.width - thumbPadPx, size.width - thumbR).coerceAtLeast(minX)
                val thumbX = (thumbPadPx + displayedFraction * usableW).coerceIn(minX, maxX)
                val thumbY = size.height / 2f
                val thumbStrokeCol = sampleSpecialBarThumbColor(specialState, displayedFraction, fillCol)
                drawCircle(
                    color = MkwKitTokens.Palette.ground,
                    radius = thumbR,
                    center = Offset(thumbX, thumbY),
                )
                drawCircle(
                    color = thumbStrokeCol,
                    radius = thumbR,
                    center = Offset(thumbX, thumbY),
                    style = Stroke(width = 2.2.dp.toPx()),
                )
            }
        }
    }
}
