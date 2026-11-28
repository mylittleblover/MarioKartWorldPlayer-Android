package com.mylittleblover.mkwplayer.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.rememberPressScale

internal enum class MkwButtonTone {
    Primary,
    Ink,
    Rose,
    Secondary,
    Danger,
    Ghost,
}

internal enum class MkwButtonSize {
    Compact,
    Regular,
    Hero,
}

internal enum class MkwIconButtonTone {
    Plain,
    Bordered,
    Rose,
    Ink,
    Scrim,
}

private data class ResolvedButtonStyle(
    val container: Color,
    val content: Color,
    val border: BorderStroke?,
)

private fun resolveButtonStyle(tone: MkwButtonTone, enabled: Boolean): ResolvedButtonStyle {
    val base = when (tone) {
        MkwButtonTone.Primary -> Triple(MkwKitTokens.Palette.crimson, Color.White, null)
        MkwButtonTone.Ink -> Triple(MkwKitTokens.Palette.ink, Color.White, null)
        MkwButtonTone.Rose -> Triple(MkwKitTokens.Palette.rose, MkwKitTokens.Palette.crimsonDeep, null)
        MkwButtonTone.Secondary -> Triple(
            MkwKitTokens.Palette.ground,
            MkwKitTokens.Palette.ink,
            BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
        )
        MkwButtonTone.Danger -> Triple(
            MkwKitTokens.Palette.dangerTint,
            MkwKitTokens.Palette.danger,
            BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.danger.copy(alpha = 0.35f)),
        )
        MkwButtonTone.Ghost -> Triple(Color.Transparent, MkwKitTokens.Palette.crimson, null)
    }
    if (enabled) return ResolvedButtonStyle(base.first, base.second, base.third)

    val disabledContainer = when (tone) {
        MkwButtonTone.Ghost -> Color.Transparent
        else -> MkwKitTokens.Palette.trackStrong
    }
    val disabledBorder = if (base.third != null) {
        BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline)
    } else {
        null
    }
    return ResolvedButtonStyle(
        container = disabledContainer,
        content = MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.5f),
        border = disabledBorder,
    )
}

/**
 * Unified custom MKW pill button (`Primary`, `Ink`, `Rose`, `Secondary`, `Danger`, `Ghost`) with
 * consistent `CircleShape` silhouette, `1.dp` hairline borders, and tactile press-scale spring.
 */
@Composable
internal fun MkwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: MkwButtonTone = MkwButtonTone.Primary,
    size: MkwButtonSize = MkwButtonSize.Regular,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val style = resolveButtonStyle(tone = tone, enabled = enabled)
    val minHeight = when (size) {
        MkwButtonSize.Compact -> MkwKitTokens.Size.buttonCompact
        MkwButtonSize.Regular -> MkwKitTokens.Size.buttonRegular
        MkwButtonSize.Hero -> MkwKitTokens.Size.buttonHero
    }
    val horizontalPad = when (size) {
        MkwButtonSize.Compact -> 12.dp
        MkwButtonSize.Regular -> 18.dp
        MkwButtonSize.Hero -> 22.dp
    }
    val iconSize = if (size == MkwButtonSize.Compact) 16.dp else 18.dp
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.97f)

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = MkwKitTokens.Shape.pill,
        color = style.container,
        contentColor = style.content,
        border = style.border,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .then(if (enabled) pressScale else Modifier)
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPad, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = style.content,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = if (size == MkwButtonSize.Compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = style.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MkwPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: MkwButtonSize = MkwButtonSize.Regular,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) = MkwButton(text, onClick, modifier, MkwButtonTone.Primary, size, leadingIcon, enabled)

@Composable
internal fun MkwInkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: MkwButtonSize = MkwButtonSize.Regular,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) = MkwButton(text, onClick, modifier, MkwButtonTone.Ink, size, leadingIcon, enabled)

@Composable
internal fun MkwRoseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: MkwButtonSize = MkwButtonSize.Regular,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) = MkwButton(text, onClick, modifier, MkwButtonTone.Rose, size, leadingIcon, enabled)

@Composable
internal fun MkwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: MkwButtonSize = MkwButtonSize.Regular,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) = MkwButton(text, onClick, modifier, MkwButtonTone.Secondary, size, leadingIcon, enabled)

@Composable
internal fun MkwDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: MkwButtonSize = MkwButtonSize.Regular,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) = MkwButton(text, onClick, modifier, MkwButtonTone.Danger, size, leadingIcon, enabled)

@Composable
internal fun MkwGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: MkwButtonSize = MkwButtonSize.Compact,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) = MkwButton(text, onClick, modifier, MkwButtonTone.Ghost, size, leadingIcon, enabled)

/**
 * Circular custom icon button keeping a guaranteed `48.dp` touch target even when rendered visually
 * compact (`36.dp`–`40.dp` for photo overlays, map HUDs, or header actions).
 */
@Composable
internal fun MkwIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: MkwIconButtonTone = MkwIconButtonTone.Plain,
    visualSize: Dp = MkwKitTokens.Size.iconCircleVisual,
    iconSize: Dp = 20.dp,
    tintOverride: Color? = null,
    enabled: Boolean = true,
) {
    val (bg, fg, border) = when (tone) {
        MkwIconButtonTone.Plain -> Triple(Color.Transparent, MkwKitTokens.Palette.ink, null)
        MkwIconButtonTone.Bordered -> Triple(
            MkwKitTokens.Palette.ground.copy(alpha = 0.94f),
            MkwKitTokens.Palette.ink,
            BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
        )
        MkwIconButtonTone.Rose -> Triple(MkwKitTokens.Palette.rose, MkwKitTokens.Palette.crimsonDeep, null)
        MkwIconButtonTone.Ink -> Triple(MkwKitTokens.Palette.ink, Color.White, null)
        MkwIconButtonTone.Scrim -> Triple(MkwKitTokens.Palette.photoScrim, Color.White, null)
    }
    val resolvedFg = when {
        !enabled -> MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.38f)
        tintOverride != null -> tintOverride
        else -> fg
    }
    val resolvedBg = if (enabled) bg else if (tone == MkwIconButtonTone.Plain) Color.Transparent else MkwKitTokens.Palette.trackStrong
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.94f)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MkwKitTokens.Size.touchMin, minHeight = MkwKitTokens.Size.touchMin)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = MkwKitTokens.Shape.pill,
            color = resolvedBg,
            border = border,
            modifier = Modifier.size(visualSize).then(if (enabled) pressScale else Modifier),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = resolvedFg,
                )
            }
        }
    }
}

/**
 * Bottom porcelain action dock wrapping [MkwUtilityTile] buttons (`Shuffle`, `Repeat`, `Queue`, `Stop`,
 * etc.) inside one hairline-bordered `18.dp` container.
 */
@Composable
internal fun MkwUtilityDock(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(6.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MkwKitTokens.Shape.card,
        color = MkwKitTokens.Palette.card,
        tonalElevation = 0.dp,
        border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
            content()
        }
    }
}

/** Icon-above-label utility dock item with optional tabular badge count and press scale. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun MkwUtilityTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    onLongClick: (() -> Unit)? = null,
) {
    val tint = when {
        !enabled -> MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.38f)
        active -> MkwKitTokens.Palette.crimsonDeep
        else -> MkwKitTokens.Palette.inkSecondary
    }
    val semanticsLabel = if (badgeCount > 0) "$label ($badgeCount)" else label
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.96f)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Surface(
        shape = MkwKitTokens.Shape.control,
        color = if (active && enabled) MkwKitTokens.Palette.rose else Color.Transparent,
        border = if (active && enabled) {
            BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.35f))
        } else {
            null
        },
        modifier = modifier
            .heightIn(min = 54.dp)
            .then(if (enabled) pressScale else Modifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick?.let { longAction ->
                    {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        longAction()
                    }
                },
            )
            .semantics {
                role = Role.Button
                contentDescription = semanticsLabel
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
                if (badgeCount > 0) {
                    Surface(
                        shape = MkwKitTokens.Shape.pill,
                        color = MkwKitTokens.Palette.track,
                        border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                            fontWeight = FontWeight.SemiBold,
                            color = MkwKitTokens.Palette.ink,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
