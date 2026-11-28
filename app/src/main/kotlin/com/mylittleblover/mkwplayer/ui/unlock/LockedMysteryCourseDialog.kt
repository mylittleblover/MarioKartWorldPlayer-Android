package com.mylittleblover.mkwplayer.ui.unlock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwMotion
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSegmentedBar
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import com.mylittleblover.mkwplayer.unlock.SpecialCupQuiz
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock

/**
 * One remaining course entry displayed in the left pane of [LockedMysteryCourseDialog].
 */
internal data class RemainingCourseItem(
    val stageCode: String,
    val displayName: String,
    val listenedSeconds: Int = 0,
    val requiredSeconds: Int = SpecialCupUnlock.REQUIRED_SECONDS,
)

private enum class MysteryDialogPane {
    REMAINING,
    QUIZ,
}

/**
 * Responsive Porcelain & Hairline dialog opened when tapping the locked Mystery Course (#24).
 *
 * - On screens >= `520.dp` wide: displays a two-column split layout with Remaining Unplayed Courses
 *   on the left and the 10-question Special Cup Knowledge Quiz on the right.
 * - On compact screens (`< 520.dp`): renders an [MkwSegmentedBar] with crisp direction-aware
 *   [MkwMotion.directionalStep] transitions between Remaining Courses and Knowledge Quiz while
 *   preserving quiz state across tab switches and configuration changes.
 * - Uses canonical MkwKit controls ([MkwCard], [MkwOptionRow], [MkwBadge], [MkwSegmentedBar],
 *   [SmoothProgressIndicator]) and snappy [AppMotion] timings honoring [rememberReducedMotion].
 */
@Composable
internal fun LockedMysteryCourseDialog(
    visible: Boolean,
    completedCount: Int,
    totalCount: Int,
    remainingCourses: List<RemainingCourseItem>,
    onDismiss: () -> Unit,
    onQuizPassedAndStartCeremony: () -> Unit,
    modifier: Modifier = Modifier,
    ceremonyReady: Boolean = false,
    onCourseClick: ((String) -> Unit)? = null,
) {
    if (!visible) return

    val reducedMotion = rememberReducedMotion()

    var compactPaneIndex by rememberSaveable { mutableIntStateOf(0) }
    var quizCurrentIndex by rememberSaveable { mutableIntStateOf(0) }
    var quizWrongOption by rememberSaveable { mutableIntStateOf(-1) }
    var quizWrongNonce by rememberSaveable { mutableIntStateOf(0) }
    var quizCompleted by rememberSaveable { mutableStateOf(false) }

    val quizState = SpecialCupQuiz.SessionState(
        currentIndex = quizCurrentIndex,
        wrongOptionIndex = quizWrongOption.takeIf { it >= 0 },
        completed = quizCompleted,
    )

    fun applyQuizState(next: SpecialCupQuiz.SessionState, outcome: SpecialCupQuiz.AnswerOutcome? = null) {
        quizCurrentIndex = next.currentIndex
        quizWrongOption = next.wrongOptionIndex ?: -1
        quizCompleted = next.completed
        if (outcome == SpecialCupQuiz.AnswerOutcome.WRONG_TRY_AGAIN) {
            quizWrongNonce += 1
        }
    }

    val safeTotal = totalCount.coerceAtLeast(1)
    val safeCompleted = completedCount.coerceIn(0, safeTotal)
    val listenProgress = if (ceremonyReady || quizCompleted) {
        1f
    } else if (totalCount > 0) {
        (safeCompleted.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedListenProgress by animateFloatAsState(
        targetValue = listenProgress,
        animationSpec = if (reducedMotion) {
            tween(0)
        } else {
            tween(durationMillis = 160, easing = AppMotion.easeOut)
        },
        label = "mystery-progress-ring",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 780.dp)
                .heightIn(max = 680.dp),
            shape = MkwKitTokens.Shape.dialog,
            color = MkwKitTokens.Palette.ground,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MkwKitTokens.Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(MkwKitTokens.Spacing.sm),
            ) {
                // Header with animated progress ring emblem
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ListeningProgressRingBadge(
                        progress = animatedListenProgress,
                        ready = ceremonyReady || quizCompleted,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.mystery_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (totalCount > 0) {
                            Text(
                                text = stringResource(R.string.mystery_dialog_progress_fmt, safeCompleted, totalCount),
                                style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                                fontWeight = FontWeight.SemiBold,
                                color = MkwKitTokens.Palette.crimson,
                            )
                        }
                    }
                    MkwIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.mystery_dialog_close),
                        onClick = onDismiss,
                        visualSize = 36.dp,
                        iconSize = 20.dp,
                        tintOverride = MkwKitTokens.Palette.inkSecondary,
                    )
                }

                Text(
                    text = stringResource(R.string.mystery_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )

                MkwDivider()

                // Responsive body: two-column split >= 520dp, segmented directional swap < 520dp
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    val isWide = maxWidth >= 520.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 320.dp, max = 460.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            RemainingCoursesPane(
                                remainingCourses = remainingCourses,
                                ceremonyReady = ceremonyReady,
                                onEnterCeremony = onQuizPassedAndStartCeremony,
                                onCourseClick = onCourseClick,
                                modifier = Modifier
                                    .weight(0.45f)
                                    .fillMaxHeight(),
                            )
                            Box(
                                modifier = Modifier
                                    .width(MkwKitTokens.Stroke.hairline)
                                    .fillMaxHeight()
                                    .background(MkwKitTokens.Palette.hairline),
                            )
                            SpecialCupQuizPane(
                                state = quizState,
                                wrongAttemptNonce = quizWrongNonce,
                                ceremonyReady = ceremonyReady,
                                reducedMotion = reducedMotion,
                                onSelectOption = { idx ->
                                    val (next, outcome) = quizState.submitAnswer(idx)
                                    applyQuizState(next, outcome)
                                },
                                onRetry = { applyQuizState(quizState.retryCurrent()) },
                                onEnterCeremony = onQuizPassedAndStartCeremony,
                                modifier = Modifier
                                    .weight(0.55f)
                                    .fillMaxHeight(),
                            )
                        }
                    } else {
                        val selectedPane = MysteryDialogPane.entries.getOrElse(compactPaneIndex) {
                            MysteryDialogPane.REMAINING
                        }
                        val tabRemainingLabel = stringResource(R.string.mystery_dialog_tab_remaining)
                        val tabQuizLabel = stringResource(R.string.mystery_dialog_tab_quiz)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MkwSegmentedBar(
                                items = MysteryDialogPane.entries,
                                selected = selectedPane,
                                labelOf = { pane ->
                                    when (pane) {
                                        MysteryDialogPane.REMAINING -> tabRemainingLabel
                                        MysteryDialogPane.QUIZ -> tabQuizLabel
                                    }
                                },
                                onSelect = { compactPaneIndex = it.ordinal },
                            )

                            AnimatedContent(
                                targetState = selectedPane,
                                transitionSpec = {
                                    with(MkwMotion) {
                                        directionalStep(
                                            forward = targetState.ordinal >= initialState.ordinal,
                                            reducedMotion = reducedMotion,
                                        )
                                    }
                                },
                                label = "mystery-dialog-pane",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 280.dp, max = 420.dp),
                            ) { pane ->
                                when (pane) {
                                    MysteryDialogPane.REMAINING -> RemainingCoursesPane(
                                        remainingCourses = remainingCourses,
                                        ceremonyReady = ceremonyReady,
                                        onEnterCeremony = onQuizPassedAndStartCeremony,
                                        onCourseClick = onCourseClick,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    MysteryDialogPane.QUIZ -> SpecialCupQuizPane(
                                        state = quizState,
                                        wrongAttemptNonce = quizWrongNonce,
                                        ceremonyReady = ceremonyReady,
                                        reducedMotion = reducedMotion,
                                        onSelectOption = { idx ->
                                            val (next, outcome) = quizState.submitAnswer(idx)
                                            applyQuizState(next, outcome)
                                        },
                                        onRetry = { applyQuizState(quizState.retryCurrent()) },
                                        onEnterCeremony = onQuizPassedAndStartCeremony,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    MkwGhostButton(
                        text = stringResource(R.string.mystery_dialog_close),
                        onClick = onDismiss,
                        size = MkwButtonSize.Compact,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningProgressRingBadge(
    progress: Float,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = MkwKitTokens.Palette.trackStrong
    val activeColor = MkwKitTokens.Palette.crimson
    val fillColor = MkwKitTokens.Palette.rose

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val strokePx = 3.5.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawCircle(
                color = fillColor,
                radius = (size.minDimension - strokePx * 2f) / 2f,
            )
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            val sweep = (progress.coerceIn(0f, 1f) * 360f)
            if (sweep > 0.5f) {
                drawArc(
                    color = activeColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Icon(
            imageVector = if (ready) Icons.Filled.AutoAwesome else Icons.Filled.Lock,
            contentDescription = null,
            tint = activeColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RemainingCoursesPane(
    remainingCourses: List<RemainingCourseItem>,
    ceremonyReady: Boolean,
    onEnterCeremony: () -> Unit,
    onCourseClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.mystery_dialog_remaining_header, remainingCourses.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MkwKitTokens.Palette.ink,
        )
        Text(
            text = stringResource(R.string.mystery_dialog_remaining_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MkwKitTokens.Palette.inkSecondary,
        )

        if (remainingCourses.isEmpty() || ceremonyReady) {
            MkwCard(
                tone = MkwSurfaceTone.RoseTint,
                selected = true,
                shape = MkwKitTokens.Shape.control,
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MkwKitTokens.Palette.crimson,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.mystery_dialog_remaining_all_done),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MkwKitTokens.Palette.crimsonDeep,
                )
                Spacer(Modifier.height(4.dp))
                MkwPrimaryButton(
                    text = stringResource(R.string.quiz_enter_ceremony_button),
                    onClick = onEnterCeremony,
                    leadingIcon = Icons.Filled.AutoAwesome,
                    size = MkwButtonSize.Compact,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(remainingCourses, key = { it.stageCode }) { item ->
                    val progressLabel = if (item.listenedSeconds > 0) {
                        stringResource(
                            R.string.mystery_dialog_course_seconds_fmt,
                            item.listenedSeconds.coerceAtMost(item.requiredSeconds),
                            item.requiredSeconds,
                        )
                    } else {
                        stringResource(R.string.mystery_dialog_course_unheard)
                    }
                    val noteTint = if (item.listenedSeconds > 0) {
                        MkwKitTokens.Palette.crimson
                    } else {
                        MkwKitTokens.Palette.inkSecondary
                    }
                    if (onCourseClick != null) {
                        MkwCard(
                            tone = MkwSurfaceTone.Porcelain,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            MkwOptionRow(
                                headline = item.displayName,
                                supporting = progressLabel,
                                onClick = { onCourseClick(item.stageCode) },
                                leading = {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = noteTint,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = MkwKitTokens.Palette.crimson,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    } else {
                        MkwCard(
                            tone = MkwSurfaceTone.Porcelain,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = noteTint,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MkwKitTokens.Palette.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = progressLabel,
                                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                        color = noteTint,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialCupQuizPane(
    state: SpecialCupQuiz.SessionState,
    wrongAttemptNonce: Int,
    ceremonyReady: Boolean,
    reducedMotion: Boolean,
    onSelectOption: (Int) -> Unit,
    onRetry: () -> Unit,
    onEnterCeremony: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showCongrats = state.completed || ceremonyReady
    val stepKey = if (showCongrats) SpecialCupQuiz.totalQuestions else state.currentIndex

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .animateContentSize(animationSpec = tween(160, easing = AppMotion.easeOut)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.quiz_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.ink,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MkwBadge(
                text = stringResource(R.string.quiz_progress_fmt, state.currentNumber, state.total),
                tone = if (showCongrats) MkwBadgeTone.Crimson else MkwBadgeTone.Rose,
            )
        }

        SmoothProgressIndicator(
            progress = if (ceremonyReady) 1f else state.progressFraction,
            modifier = Modifier.fillMaxWidth(),
            color = MkwKitTokens.Palette.crimson,
            trackColor = MkwKitTokens.Palette.trackStrong,
        )

        AnimatedContent(
            targetState = stepKey,
            transitionSpec = {
                with(MkwMotion) {
                    directionalStep(forward = true, reducedMotion = reducedMotion)
                }
            },
            label = "special-cup-quiz-step",
        ) { activeStep ->
            if (activeStep >= SpecialCupQuiz.totalQuestions) {
                QuizCongratsCard(
                    completedViaQuiz = state.completed,
                    reducedMotion = reducedMotion,
                    onEnterCeremony = onEnterCeremony,
                )
            } else {
                val question = SpecialCupQuiz.questions[activeStep]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.quiz_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )

                    if (question.isFinalChallenge) {
                        MkwBadge(
                            text = stringResource(R.string.quiz_final_challenge_badge),
                            tone = MkwBadgeTone.Crimson,
                            leadingIcon = Icons.Filled.AutoAwesome,
                            tabular = false,
                        )
                    }

                    Text(
                        text = stringResource(question.questionRes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MkwKitTokens.Palette.ink,
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        question.optionResIds.forEachIndexed { idx, optRes ->
                            QuizAnswerOptionCard(
                                letter = SpecialCupQuiz.optionLetters[idx],
                                label = stringResource(optRes),
                                isWrongSelected = state.wrongOptionIndex == idx,
                                shakeTrigger = if (state.wrongOptionIndex == idx) wrongAttemptNonce else 0,
                                reducedMotion = reducedMotion,
                                onClick = { onSelectOption(idx) },
                            )
                        }
                    }

                    if (state.hasWrongAttempt) {
                        MkwCard(
                            tone = MkwSurfaceTone.Porcelain,
                            error = true,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MkwKitTokens.Palette.danger,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.quiz_wrong_feedback),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.danger,
                                    modifier = Modifier.weight(1f),
                                )
                                MkwSecondaryButton(
                                    text = stringResource(R.string.quiz_retry_button),
                                    onClick = onRetry,
                                    leadingIcon = Icons.Filled.Refresh,
                                    size = MkwButtonSize.Compact,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizAnswerOptionCard(
    letter: String,
    label: String,
    isWrongSelected: Boolean,
    shakeTrigger: Int,
    reducedMotion: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger, reducedMotion) {
        if (shakeTrigger <= 0 || reducedMotion) {
            shakeOffset.snapTo(0f)
            return@LaunchedEffect
        }
        val ampPx = with(density) { 5.dp.toPx() }
        shakeOffset.snapTo(0f)
        shakeOffset.animateTo(ampPx, tween(35, easing = AppMotion.easeOut))
        shakeOffset.animateTo(-ampPx * 0.75f, tween(40, easing = AppMotion.easeOut))
        shakeOffset.animateTo(ampPx * 0.35f, tween(35, easing = AppMotion.easeOut))
        shakeOffset.animateTo(0f, tween(30, easing = AppMotion.easeOut))
    }

    MkwCard(
        tone = MkwSurfaceTone.Porcelain,
        error = isWrongSelected,
        shape = MkwKitTokens.Shape.control,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shakeOffset.value },
    ) {
        MkwOptionRow(
            headline = label,
            destructive = isWrongSelected,
            onClick = onClick,
            leading = {
                MkwBadge(
                    text = letter,
                    tone = if (isWrongSelected) MkwBadgeTone.Danger else MkwBadgeTone.Rose,
                    tabular = false,
                )
            },
        )
    }
}

@Composable
private fun QuizCongratsCard(
    completedViaQuiz: Boolean,
    reducedMotion: Boolean,
    onEnterCeremony: () -> Unit,
) {
    val emblemScale = remember { Animatable(if (reducedMotion) 1f else 0.96f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            emblemScale.snapTo(1f)
        } else {
            emblemScale.snapTo(0.96f)
            emblemScale.animateTo(1f, animationSpec = AppMotion.snapSpring)
        }
    }

    MkwCard(
        tone = MkwSurfaceTone.RoseTint,
        selected = true,
        shape = MkwKitTokens.Shape.card,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = emblemScale.value
                        scaleY = emblemScale.value
                    }
                    .clip(CircleShape)
                    .background(MkwKitTokens.Palette.crimson),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = stringResource(
                    if (completedViaQuiz) R.string.quiz_congrats_title else R.string.quiz_ready_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.crimsonDeep,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (completedViaQuiz) R.string.quiz_congrats_body else R.string.quiz_ready_body,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MkwKitTokens.Palette.inkSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            MkwPrimaryButton(
                text = stringResource(R.string.quiz_enter_ceremony_button),
                onClick = onEnterCeremony,
                leadingIcon = Icons.Filled.AutoAwesome,
                size = MkwButtonSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
