package com.mylittleblover.mkwplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.mylittleblover.mkwplayer.ui.Tokens

/*
 * UI-10 (settings), UI-15 (import and match repair), UI-20 (error recovery) and UI-21 (first launch).
 *
 * Every sentence these four pages show was decided in `SettingsStates.kt`, where it can be tested on
 * the JVM. What is left here is where the pixels go: which element is a touch target, and which text
 * sits beside a control. The screens make no claim of their own, because a claim assembled inside a
 * layout function is a claim nobody tests -- and these four pages contain the ones that would be
 * worst to get wrong: that a disabled control does something, that an absent folder imported zero
 * files, and that the app ships music it does not ship.
 *
 * Two rules are enforced by the layout rather than left to a caller:
 *
 *  - A disabled row always renders its reason. Greying the title and stopping there is the failure
 *    the contract singles out, since a greyed control with no explanation is indistinguishable from
 *    a broken one.
 *  - No screen draws a control with nothing behind it. Settings rows carry no switch at all: this
 *    build has no settings store, so a switch would be decoration that lies. The one row that is
 *    enabled points at the scan that exists.
 */

/**
 * UI-10: the settings page.
 *
 * Rows are laid out in the order [settingsRows] returns, which is the order a reader expects: the
 * folder first, then the things this build cannot do.
 *
 * [rightsNotice] is printed at the foot of the page, last, and cannot be dropped by a caller. The
 * artwork this app shows is Nintendo's, and the page has to say so; a settings page that omitted it
 * would leave the provenance visible only on screens a user might never open.
 *
 * There is deliberately no switch, checkbox or slider here. A [SettingRow] that is `enabled` is a row
 * whose action lives elsewhere; a disabled one says why. Drawing a toggle for either would be the
 * "control that does nothing" the contract forbids.
 */
@Composable
internal fun SettingsScreen(
    rows: List<SettingRow>,
    rightsNotice: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Palette.cream100),
        contentPadding = PaddingValues(
            horizontal = Tokens.Spacing.md,
            vertical = Tokens.Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        item(key = "settings-heading") {
            Text(
                text = "Settings",
                fontSize = Tokens.Type.pageTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.navy900,
            )
        }

        // Shown above the list rather than only on the rows that need it: a page of mostly
        // unavailable controls looks broken, and this line says it is not.
        item(key = "settings-intro") {
            Text(
                text = "Only the rows marked available do anything in this build. Every other row " +
                    "says what is missing rather than hiding the feature.",
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.ink900,
            )
        }

        items(rows, key = { it.key }) { row ->
            SettingRowView(row)
        }

        item(key = "rights") {
            RightsNotice(text = rightsNotice)
        }
    }
}

/**
 * One settings row.
 *
 * The state is a word plus a colour, never a colour alone: the palette's warning colour as text
 * fails the contrast minimum the pack itself sets, so the readable variant is used and the sentence
 * carries the meaning. A disabled row's [SettingRow.disabledReason] is printed in full -- the reason
 * is the content of the row, not a footnote.
 */
@Composable
private fun SettingRowView(row: SettingRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.Radius.md),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.Component.touchTargetMin)
                .padding(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.title,
                    modifier = Modifier.weight(1f),
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.Palette.ink900,
                )
                Text(
                    text = if (row.enabled) "AVAILABLE" else "UNAVAILABLE",
                    fontSize = Tokens.Type.caption,
                    fontWeight = FontWeight.Bold,
                    color = if (row.enabled) {
                        Tokens.Palette.successText
                    } else {
                        Tokens.Palette.warningText
                    },
                )
            }

            Text(
                text = row.detail,
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.ink900,
            )

            // Null on an enabled row by construction; the branch exists so a caller-built row that
            // claims to be enabled while carrying a reason still shows the reason instead of losing
            // it. Dropping the text is the only outcome this composable must not have.
            row.disabledReason?.let { reason ->
                Text(
                    text = reason,
                    fontSize = Tokens.Type.secondary,
                    color = Tokens.Palette.warningText,
                )
            }
        }
    }
}

/**
 * The provenance statement.
 *
 * A plain block rather than a dismissible banner: there is no close control and no "do not show
 * again", because a rights notice the user can hide is a rights notice that is not shown.
 */
@Composable
private fun RightsNotice(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.Radius.md),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.navy900),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            Text(
                text = "About the artwork",
                fontSize = Tokens.Type.secondary,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.cream100,
            )
            Text(
                text = text,
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.cream100,
            )
        }
    }
}

/**
 * UI-15: import and match repair.
 *
 * The page is a report and one action. [ImportSummary.headline] is already a sentence -- the screen
 * does not assemble one from [ImportSummary.added] and [ImportSummary.rejected], because a
 * zero built here from a folder that was never read is exactly the false measurement the summary
 * exists to prevent.
 *
 * [ImportSummary.rejectedReasons] are printed verbatim and in full. They are the only thing that
 * tells a user which file failed and why, and a truncated reason ("could not be read") would remove
 * the file name that makes it actionable.
 *
 * The scan button is always enabled: a scan is how a user finds out that the folder is missing, and
 * a disabled button with no way to learn why is a worse answer than a scan that reports it.
 */
@Composable
internal fun ImportScreen(
    summary: ImportSummary,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Palette.cream100),
        contentPadding = PaddingValues(
            horizontal = Tokens.Spacing.md,
            vertical = Tokens.Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        item(key = "import-heading") {
            Text(
                text = "Import and repair",
                fontSize = Tokens.Type.pageTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.navy900,
            )
        }

        item(key = "import-summary") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Tokens.Radius.md),
                colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
            ) {
                Column(
                    modifier = Modifier.padding(Tokens.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
                ) {
                    Text(
                        text = summary.headline,
                        fontSize = Tokens.Type.body,
                        color = Tokens.Palette.ink900,
                    )
                    // The two counts are only printed when the scan actually measured something.
                    // They are never printed on their own as "0 imported": that is the reading the
                    // headline's missing-folder branch exists to make impossible.
                    if (summary.added > 0 || summary.rejected > 0) {
                        Text(
                            text = "${summary.added} imported, ${summary.rejected} skipped",
                            fontSize = Tokens.Type.secondary,
                            fontWeight = FontWeight.Bold,
                            color = Tokens.Palette.ink900,
                        )
                    }
                }
            }
        }

        item(key = "import-scan") {
            OutlinedButton(
                onClick = onScan,
                // The token, not the Material default: the default is 40 dp, below the 48 dp the
                // pack requires, on the page's only control.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Component.touchTargetMin),
            ) {
                Text(
                    text = "Scan the stages folder",
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (summary.rejectedReasons.isNotEmpty()) {
            item(key = "import-rejected-heading") {
                Text(
                    text = "Files that were skipped",
                    modifier = Modifier.padding(top = Tokens.Spacing.xs),
                    fontSize = Tokens.Type.sectionTitle,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.Palette.ink900,
                )
            }

            // The reasons are the scanner's own lines, each naming a file. No key: two files can
            // produce the same sentence, and a duplicate key makes LazyColumn throw.
            items(summary.rejectedReasons) { reason ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Tokens.Radius.sm),
                    colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
                ) {
                    Text(
                        text = reason,
                        modifier = Modifier.padding(Tokens.Spacing.sm),
                        fontSize = Tokens.Type.secondary,
                        color = Tokens.Palette.errorText,
                    )
                }
            }
        }
    }
}

/**
 * UI-20: error recovery.
 *
 * [problemTitle] is passed as a string rather than as a problem enum because the screen must show the
 * heading it was given, not a second wording derived here from the same value. The caller asks the
 * state layer for it.
 *
 * Every option is rendered, including the ones that cannot be applied. That is the point: a route
 * that exists but is not implemented should read as "not yet", not as a feature that vanished. A
 * [RecoveryOption] with `canDo = false` is a disabled button with its [RecoveryOption.reason] printed
 * underneath -- the reason is never a tooltip, a toast or a log line.
 *
 * [onChoose] receives [RecoveryOption.label], so the caller's `when` is over the same strings the
 * user pressed and cannot drift from what is on screen.
 */
@Composable
internal fun RecoveryScreen(
    problemTitle: String,
    options: List<RecoveryOption>,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Palette.cream100),
        contentPadding = PaddingValues(
            horizontal = Tokens.Spacing.md,
            vertical = Tokens.Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        item(key = "recovery-heading") {
            Text(
                text = "Something went wrong",
                fontSize = Tokens.Type.pageTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.navy900,
            )
        }

        item(key = "recovery-problem") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Tokens.Radius.md),
                colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
            ) {
                Text(
                    text = problemTitle,
                    modifier = Modifier.padding(Tokens.Spacing.md),
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.Palette.errorText,
                )
            }
        }

        // The guarantee the page has to make out loud, since the options below it never include a
        // destructive one: nothing here removes files.
        item(key = "recovery-reassurance") {
            Text(
                text = "None of these options deletes anything. Your files stay where they are.",
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.ink900,
            )
        }

        if (options.isEmpty()) {
            // A caller with no options is a bug, not a state: say so rather than drawing a page that
            // looks like the problem has no answer.
            item(key = "recovery-empty") {
                Text(
                    text = "No recovery step is available for this problem in this build.",
                    fontSize = Tokens.Type.body,
                    color = Tokens.Palette.warningText,
                )
            }
        } else {
            items(options, key = { it.label }) { option ->
                RecoveryOptionView(option = option, onChoose = onChoose)
            }
        }
    }
}

/**
 * One recovery option: a full-width button, and the reason under it when it cannot be applied.
 *
 * The disabled button keeps its label rather than being hidden, so the sentence under it has
 * something to be about. Its text colour is the readable warning variant, not the palette's raw
 * warning colour, which fails the contrast minimum as text.
 */
@Composable
private fun RecoveryOptionView(option: RecoveryOption, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Button(
            onClick = { onChoose(option.label) },
            enabled = option.canDo,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.Component.touchTargetMin),
        ) {
            Text(text = option.label, fontSize = Tokens.Type.body)
        }

        // Null on an applicable option by construction. The branch also catches a caller-built
        // option that cannot be applied but forgot its reason, so the page always explains itself
        // rather than leaving a dead control with no text.
        if (!option.canDo) {
            Text(
                text = option.reason ?: "Not available in this build.",
                modifier = Modifier.padding(horizontal = Tokens.Spacing.xs),
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.warningText,
            )
        }
    }
}

/**
 * UI-21: first launch.
 *
 * The steps are drawn in the order [firstRunSteps] returns, and the first one is always the statement
 * that nothing is bundled. It is not conditional on anything: this app ships no music, and a
 * first-run page that opened with an invitation to browse a soundtrack would be the one false
 * promise a user would remember.
 *
 * [FirstRunStep.done] is shown as a word as well as a colour, for the same contrast reason as
 * everywhere else. A step that is not done is not an error -- it is a step, and the page says which.
 */
@Composable
internal fun FirstRunScreen(
    steps: List<FirstRunStep>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Palette.cream100),
        contentPadding = PaddingValues(
            horizontal = Tokens.Spacing.md,
            vertical = Tokens.Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        item(key = "first-run-heading") {
            Text(
                text = "Before you start",
                fontSize = Tokens.Type.pageTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.navy900,
            )
        }

        item(key = "first-run-intro") {
            Text(
                text = "These are the steps this app needs. Nothing is downloaded for you, and no " +
                    "music comes with it.",
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.ink900,
            )
        }

        items(steps, key = { it.title }) { step ->
            FirstRunStepView(step)
        }

        item(key = "first-run-continue") {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Component.touchTargetMin),
            ) {
                Text(
                    text = "Continue",
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * One first-run step.
 *
 * The state word is deliberately "DONE" / "TO DO": a tick alone would be conveyed by colour and
 * shape only, and the pack requires the meaning to survive a reader who cannot see either.
 */
@Composable
private fun FirstRunStepView(step: FirstRunStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.Radius.md),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = step.title,
                    modifier = Modifier.weight(1f),
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.Palette.ink900,
                )
                Text(
                    text = if (step.done) "DONE" else "TO DO",
                    fontSize = Tokens.Type.caption,
                    fontWeight = FontWeight.Bold,
                    color = if (step.done) {
                        Tokens.Palette.successText
                    } else {
                        Tokens.Palette.warningText
                    },
                )
            }

            Text(
                text = step.body,
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.ink900,
            )
        }
    }
}
