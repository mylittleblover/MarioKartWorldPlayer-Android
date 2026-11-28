package com.mylittleblover.mkwplayer.ui.unlock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.Tokens
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.ui.art.ResourceArtwork
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import com.mylittleblover.mkwplayer.unlock.UnlockPresentation

/*
 * UI-11 (the persistent top ceremony notice), UI-12 (the ceremony) and UI-13 (the congratulations),
 * drawn from the state in CeremonyState.kt.
 *
 * Three rules owned by the layout:
 * - The top unlock notice is PERSISTENT and NON-DISMISSIBLE until the ceremony is watched.
 * - During ceremony playback, controls that cannot fire (pause, seek, skip, track change) are
 *   ABSENT rather than disabled, and the progress bar is read-only.
 * - The Special Cup emblem never appears without [CupArt.NOTICE] beside it.
 */

private val NOTICE_BADGE = Tokens.Component.miniPlayerArtwork
private val CEREMONY_EMBLEM = Tokens.Component.mainPlaybackButton
private val DIALOG_EMBLEM = Tokens.Component.miniPlayerArtwork

/**
 * UI-11, the persistent, non-dismissible top notification announcing that the Mystery Course
 * unlock ceremony is ready.
 *
 * Deliberately renders NO close/dismiss button: the banner stays pinned until the user watches
 * the unlock ceremony to completion. [onDismiss] is retained only as an optional unused parameter
 * for signature compatibility with existing callers.
 */
@Composable
internal fun UnlockNotice(
    view: NoticeView,
    onAction: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Tokens.Palette.navy900,
                shape = RoundedCornerShape(Tokens.Radius.md),
            )
            .padding(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(NOTICE_BADGE)
                    .clip(CircleShape)
                    .background(Tokens.Palette.yellow500),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.unlock_notice_badge_new),
                    color = Tokens.Palette.navy900,
                    fontSize = Tokens.Type.caption,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = view.message,
                modifier = Modifier.weight(1f),
                color = Tokens.Palette.cream100,
                fontSize = Tokens.Type.body,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onAction,
                shape = MkwKitTokens.Shape.pill,
                color = Tokens.Palette.yellow500,
                contentColor = Tokens.Palette.navy900,
                modifier = Modifier
                    .heightIn(min = Tokens.Component.touchTargetMin)
                    .semantics { role = Role.Button },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = view.actionLabel,
                        fontSize = Tokens.Type.body,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Porcelain & theme-accent persistent, non-dismissible top banner for the app shell (`MkwApp`).
 *
 * Announces that the Mystery Course unlock condition is met without revealing Rainbow Road or
 * Special Cup, and intentionally omits any close/dismiss control until the ceremony is watched.
 */
@Composable
internal fun PersistentUnlockCeremonyBanner(
    visible: Boolean,
    onEnterCeremony: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            fadeIn(tween(100))
        } else {
            expandVertically(
                animationSpec = tween(180, easing = AppMotion.easeOut),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(150, easing = AppMotion.easeOut))
        },
        exit = if (reducedMotion) {
            fadeOut(tween(90))
        } else {
            shrinkVertically(
                animationSpec = tween(140, easing = AppMotion.easeOut),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(120))
        },
        modifier = modifier,
    ) {
        Surface(
            color = MkwKitTokens.Palette.rose,
            contentColor = MkwKitTokens.Palette.crimsonDeep,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MkwKitTokens.Palette.crimson,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.unlock_mystery_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.crimsonDeep,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.unlock_mystery_banner_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimsonDeep.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MkwPrimaryButton(
                        text = stringResource(R.string.unlock_mystery_banner_action),
                        onClick = onEnterCeremony,
                        size = MkwButtonSize.Compact,
                    )
                }
                MkwDivider(color = MkwKitTokens.Palette.crimson.copy(alpha = 0.18f))
            }
        }
    }
}

/**
 * UI-12, the ceremony panel.
 */
@Composable
internal fun CeremonyScreen(
    view: CeremonyView,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Palette.navy900)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
    ) {
        Text(
            text = view.title,
            color = Tokens.Palette.cream100,
            fontSize = Tokens.Type.pageTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = view.subtitle,
            color = Tokens.Palette.cyan400,
            fontSize = Tokens.Type.body,
        )

        if (view.emblem != 0) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                ResourceArtwork(
                    resourceId = view.emblem,
                    modifier = Modifier.size(CEREMONY_EMBLEM),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = CupArt.NOTICE,
                color = Tokens.Palette.cream100,
                fontSize = Tokens.Type.caption,
            )
        }

        Text(
            text = UnlockPresentation.CEREMONY_AUDIO_CAVEAT,
            color = Tokens.Palette.warning,
            fontSize = Tokens.Type.caption,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Tokens.Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = view.progress.elapsed,
                color = Tokens.Palette.cream100,
                fontSize = Tokens.Type.sectionTitle,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = view.progress.total,
                color = Tokens.Palette.cream100,
                fontSize = Tokens.Type.sectionTitle,
                fontWeight = FontWeight.Bold,
            )
        }

        com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(
            progress = view.progress.fraction,
            modifier = Modifier.fillMaxWidth(),
            color = Tokens.Palette.yellow500,
            trackColor = Tokens.Palette.navy700,
        )

        if (!view.controls.canSeek) {
            Text(
                text = stringResource(R.string.unlock_ceremony_screen_readonly_hint),
                color = Tokens.Palette.cream100,
                fontSize = Tokens.Type.caption,
            )
        }

        if (!view.controls.canPause) {
            Text(
                text = stringResource(R.string.unlock_ceremony_screen_controls_hint),
                color = Tokens.Palette.cream100,
                fontSize = Tokens.Type.caption,
            )
        }

        view.exitHint?.let { hint ->
            Text(
                text = hint,
                color = Tokens.Palette.warning,
                fontSize = Tokens.Type.secondary,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            onClick = onExit,
            shape = MkwKitTokens.Shape.pill,
            color = Tokens.Palette.yellow500,
            contentColor = Tokens.Palette.navy900,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.Component.touchTargetMin)
                .semantics { role = Role.Button },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.unlock_ceremony_screen_leave),
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * UI-13, the first-time post-ceremony congratulations dialog.
 *
 * Displays the localized gratitude message inviting the listener to enjoy the final Grand Prix —
 * the Special Cup.
 */
@Composable
internal fun CongratulationDialog(
    visible: Boolean,
    cupName: String = "",
    onEnterCup: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val title = cupName.trim().ifEmpty { stringResource(R.string.unlock_ceremony_congrats_title) }

    MkwDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = {
            ResourceArtwork(
                resourceId = CupArt.special.drawable,
                modifier = Modifier.size(DIALOG_EMBLEM),
                contentScale = ContentScale.Fit,
            )
        },
        confirmButton = {
            MkwPrimaryButton(
                text = stringResource(R.string.unlock_ceremony_congrats_enter),
                onClick = onEnterCup,
                size = MkwButtonSize.Compact,
            )
        },
        dismissButton = {
            MkwGhostButton(
                text = stringResource(R.string.unlock_ceremony_congrats_close),
                onClick = onDismiss,
                size = MkwButtonSize.Compact,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.unlock_ceremony_congrats_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MkwKitTokens.Palette.ink,
        )
        Spacer(Modifier.size(Tokens.Spacing.xs))
        Text(
            text = CupArt.NOTICE,
            style = MaterialTheme.typography.labelSmall,
            color = MkwKitTokens.Palette.inkSecondary,
        )
    }
}
