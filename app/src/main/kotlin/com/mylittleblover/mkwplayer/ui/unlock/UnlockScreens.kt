package com.mylittleblover.mkwplayer.ui.unlock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.Tokens
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.unlock.UnlockPresentation

/*
 * UI-11 (the top notification), UI-12 (the ceremony) and UI-13 (the congratulations), drawn from the
 * state in CeremonyState.kt.
 *
 * These composables decide nothing they could get wrong. Which controls exist, whether the progress
 * bar accepts a drag, whether the first-time dialog belongs on screen and what the notification may
 * reveal are all settled before the layout sees them; what is left here is where the pixels go and
 * which elements are touch targets.
 *
 * Three things the layout does own, because they are about honesty rather than measurement:
 *
 * - A control whose flag is false is ABSENT, not disabled. A greyed-out pause button still tells the
 *   user that pause exists and that something is currently holding it back; the audio layer will
 *   never perform that pause, so the control is a promise nothing keeps. The ceremony screen has no
 *   pause, skip, seek or track-change callback at all, and it says so in words instead.
 * - The progress bar is read-only. There is no drag gesture, no clickable and no slider: the bar
 *   reports where the ceremony is, and the only way out is the exit button.
 * - The cup artwork never appears without [CupArt.NOTICE] beside it. The notice is the statement that
 *   the emblem is Nintendo's and that this is a private research build; a screen that dropped it
 *   would present borrowed art as the app's own.
 */

/** The notification's badge mark. A gold disc, not the cup's emblem: the cup must not be revealed. */
private val NOTICE_BADGE = 32.dp

/** The ceremony's emblem. Large, because it is the panel's main visual. */
private val CEREMONY_EMBLEM = 96.dp

/** The dialog's emblem. Smaller than the ceremony's, because the dialog also carries two actions. */
private val DIALOG_EMBLEM = 64.dp

/**
 * UI-11, the top notification announcing that the hidden cup has appeared.
 *
 * Drawn as a solid navy card so it reads as an announcement rather than as page content, with the
 * action in the pack's yellow. It never cuts the current music: the caller decides when the ceremony
 * starts, and the offer explicitly waits.
 *
 * The badge is a plain gold disc rather than the cup's emblem on purpose. An emblem here would reveal
 * the hidden cup before the user has opened it, which is the one thing the notification must not do;
 * `noticeFor` refuses to produce a view at all until the cup has been earned, and the artwork follows
 * the same rule.
 *
 * The close control is drawn only when [NoticeView.dismissible] is true. Closing hides the
 * notification and nothing else -- the cup stays earned and keeps its entry point -- so this is a
 * display fact, not a permission the screen can withhold.
 */
@Composable
internal fun UnlockNotice(
    view: NoticeView,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
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
                // A word, not only a colour: the disc has to mean something to a reader who cannot
                // tell gold from navy.
                Text(
                    text = "NEW",
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
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Tokens.Palette.yellow500,
                    contentColor = Tokens.Palette.navy900,
                ),
            ) {
                Text(
                    text = view.actionLabel,
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (view.dismissible) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
                    colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Palette.cream100),
                ) {
                    Text(text = "Close", fontSize = Tokens.Type.secondary)
                }
            }
        }
    }
}

/**
 * UI-12, the ceremony panel.
 *
 * The bar is a [LinearProgressIndicator] with a plain progress lambda rather than a slider: a slider
 * would be draggable by default, and offering a drag during the one piece of audio whose completion
 * cannot be faked is exactly how the completion becomes fake.
 *
 * What is drawn is driven by [CeremonyView.controls], and only in the negative direction: the panel
 * states the absence of pause, seek, skip and track change when the flags are false. It never draws
 * those controls at all -- the only action this screen accepts is [onExit] -- so a reader cannot press
 * something that does nothing, and a screenshot cannot suggest a control that does not exist.
 *
 * The exit button is unconditional. Leaving stops the music without granting completion and the cup
 * stays earned, so there is no phase in which holding the user here would be right.
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
            // Scrolls rather than clips: at a large font scale the caveat and the exit control must
            // stay reachable, and the pack's layout note allows the information to scroll.
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
                Image(
                    painter = painterResource(view.emblem),
                    contentDescription = "${view.title} emblem",
                    modifier = Modifier.size(CEREMONY_EMBLEM),
                    contentScale = ContentScale.Fit,
                )
            }
            // The provenance statement travels with the artwork. Without it the emblem reads as the
            // app's own image, which this build cannot claim.
            Text(
                text = CupArt.NOTICE,
                color = Tokens.Palette.cream100,
                fontSize = Tokens.Type.caption,
            )
        }

        // The caveat is the reason the panel exists in this cautious form: the cue is this app's
        // confirmed file, not a verified copy of the game's own ceremony audio. Dropping it would
        // present a guess as the game's behaviour.
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

        // Read-only by construction: no gesture modifier of any kind is attached to this bar.
        LinearProgressIndicator(
            progress = { view.progress.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = Tokens.Palette.yellow500,
            trackColor = Tokens.Palette.navy700,
        )

        if (!view.controls.canSeek) {
            Text(
                text = "The progress bar is read-only. There is nothing here to drag or tap.",
                color = Tokens.Palette.cream100,
                fontSize = Tokens.Type.caption,
            )
        }

        if (!view.controls.canPause) {
            // One sentence covering the whole absent row. Four "Pause is unavailable" lines would
            // read as four broken controls rather than as a deliberate design.
            Text(
                text = "There is no pause, skip or track change while the ceremony plays. " +
                    "Only leaving is offered.",
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

        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.Component.touchTargetMin),
            colors = ButtonDefaults.buttonColors(
                containerColor = Tokens.Palette.yellow500,
                contentColor = Tokens.Palette.navy900,
            ),
        ) {
            Text(
                text = "Leave the ceremony",
                fontSize = Tokens.Type.body,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * UI-13, the first-time congratulations.
 *
 * Only ever shown for a completed ceremony that has not been congratulated yet; the caller passes
 * [visible], which comes from [shouldShowCongratulation]. This composable does not re-decide that:
 * a second copy of the rule is how a replay ends up re-sending the first-time dialog.
 *
 * Both actions are offered as peers. "Enter the cup" is primary because that is what the dialog is
 * announcing, and "Back to the library" is a real choice rather than an escape hatch -- the cup stays
 * open, so leaving the dialog loses nothing.
 *
 * The provenance line under the explanation is not decoration: the emblem is Nintendo artwork, and the
 * statement that fact is the condition on which the pack allows the art in the build.
 */
@Composable
internal fun CongratulationDialog(
    visible: Boolean,
    cupName: String,
    onEnterCup: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Composed nothing at all when hidden, so the dialog cannot appear in the semantics tree before
    // the ceremony has actually finished.
    if (!visible) return

    val name = cupName.trim().ifEmpty { CupArt.special.label }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onEnterCup,
                modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Tokens.Palette.navy700,
                    contentColor = Tokens.Palette.white,
                ),
            ) {
                Text(text = "Enter the cup", fontSize = Tokens.Type.body)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Tokens.Component.touchTargetMin),
            ) {
                Text(text = "Back to the library", fontSize = Tokens.Type.secondary)
            }
        },
        icon = {
            Image(
                painter = painterResource(CupArt.special.drawable),
                contentDescription = "$name emblem",
                modifier = Modifier.size(DIALOG_EMBLEM),
                contentScale = ContentScale.Fit,
            )
        },
        title = {
            Text(
                text = "$name unlocked!",
                color = Tokens.Palette.ink900,
                fontSize = Tokens.Type.sectionTitle,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                Text(
                    text = "Every stage you had music for has been heard, and the ceremony played " +
                        "to the end. The cup is open from now on, and this message appears once.",
                    color = Tokens.Palette.ink900,
                    fontSize = Tokens.Type.secondary,
                )
                Text(
                    text = CupArt.NOTICE,
                    color = Tokens.Palette.navy700,
                    fontSize = Tokens.Type.caption,
                )
            }
        },
        containerColor = Tokens.Palette.cream100,
        titleContentColor = Tokens.Palette.ink900,
        textContentColor = Tokens.Palette.ink900,
    )
}
