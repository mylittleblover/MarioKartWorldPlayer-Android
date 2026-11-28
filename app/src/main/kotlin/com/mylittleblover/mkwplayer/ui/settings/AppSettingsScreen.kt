package com.mylittleblover.mkwplayer.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.BuildConfig
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.i18n.AppLanguage
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDangerButton
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwMotion
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRadioMark
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwSwitch
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import com.mylittleblover.mkwplayer.ui.rememberPressScale
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import com.mylittleblover.mkwplayer.ui.theme.AppThemeAccent
import com.mylittleblover.mkwplayer.ui.theme.ThemeSettingsStore
import com.mylittleblover.mkwplayer.unlock.UnlockTestMode
import kotlinx.coroutines.launch

private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Done(val result: UpdateCheckResult) : UpdateUiState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AppSettingsScreen(
    language: AppLanguage, onLanguage: (AppLanguage) -> Unit,
    busy: Boolean, sourceName: String?, onOpenPack: () -> Unit, onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
    testMode: UnlockTestMode = UnlockTestMode.NORMAL,
    testUnlockVisible: Boolean = false,
    onTestUnlock: (UnlockTestMode) -> Unit = {},
    onTestCeremony: () -> Unit = {},
    onTestUnlockVfx: () -> Unit = {},
    onTestUnlockNotification: () -> Unit = {},
    onResetUnlockProgress: () -> Unit = {},
    outputTrim: com.mylittleblover.mkwplayer.audio.OutputTrim = com.mylittleblover.mkwplayer.audio.OutputTrim.default,
    onOutputTrim: (com.mylittleblover.mkwplayer.audio.OutputTrim) -> Unit = {},
    playbackBehavior: com.mylittleblover.mkwplayer.audio.PlaybackBehaviorConfig = com.mylittleblover.mkwplayer.audio.PlaybackBehaviorConfig(),
    onSegmentSwitchFadeOutMs: (Int) -> Unit = {},
    onPerfectBeat: (Boolean) -> Unit = {},
    dspConfig: com.mylittleblover.mkwplayer.audio.MasterDspStateConfig = com.mylittleblover.mkwplayer.audio.MasterDspStateConfig(),
    onLiveVisualizer: (Boolean) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onExportM3u: () -> Unit = {},
    backupStatusMessage: String? = null,
    sourceCount: Int? = null, playableCount: Int? = null, importProgress: String = "", importDetail: String? = null,
    importState: com.mylittleblover.mkwplayer.pack.PackImportUiState = com.mylittleblover.mkwplayer.pack.PackImportUiState(),
    onCancelImport: () -> Unit = {},
    onResumeImport: () -> Unit = {},
    onRepickImport: () -> Unit = {},
    onDismissChangelog: () -> Unit = {},
    selectedThemeAccent: AppThemeAccent? = null,
    onSelectThemeAccent: ((AppThemeAccent) -> Unit)? = null,
    exportLocationLabel: String? = null,
    onPickExportLocation: (() -> Unit)? = null,
) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    var choosingThemeAccent by rememberSaveable { mutableStateOf(false) }
    var devUnlocked by rememberSaveable { mutableStateOf(testMode != UnlockTestMode.NORMAL) }
    var devExpanded by rememberSaveable { mutableStateOf(testMode != UnlockTestMode.NORMAL) }
    val tapTracker = remember { DevOptionsTapTracker() }
    LaunchedEffect(testMode) {
        if (testMode != UnlockTestMode.NORMAL) {
            devUnlocked = true
            tapTracker.seedUnlocked(true)
        }
    }
    LaunchedEffect(devUnlocked) {
        if (devUnlocked) tapTracker.seedUnlocked(true)
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    remember(context) { ThemeSettingsStore.ensureLoaded(context) }
    val activeThemeAccent = selectedThemeAccent ?: ThemeSettingsStore.activeAccent
    val handleSelectThemeAccent: (AppThemeAccent) -> Unit = { chosen ->
        if (onSelectThemeAccent != null) {
            onSelectThemeAccent(chosen)
        } else {
            ThemeSettingsStore.select(context, chosen)
        }
    }
    var updateUiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var activeToast by remember { mutableStateOf<Toast?>(null) }
    var confirmResetUnlock by rememberSaveable { mutableStateOf(false) }

    fun openExternalUrl(url: String) {
        val opened = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.isSuccess
        if (!opened) {
            activeToast?.cancel()
            activeToast = Toast.makeText(
                context,
                context.getString(R.string.settings_link_open_failed),
                Toast.LENGTH_SHORT,
            ).also { it.show() }
        }
    }

    if (confirmResetUnlock) {
        MkwDialog(
            onDismissRequest = { confirmResetUnlock = false },
            title = stringResource(R.string.settings_unlock_reset_confirm_title),
            confirmButton = {
                MkwDangerButton(
                    text = stringResource(R.string.settings_unlock_reset_confirm_action),
                    onClick = {
                        confirmResetUnlock = false
                        onResetUnlockProgress()
                        activeToast?.cancel()
                        activeToast = Toast.makeText(
                            context,
                            context.getString(R.string.settings_unlock_reset_done),
                            Toast.LENGTH_SHORT,
                        ).also { it.show() }
                    },
                    size = MkwButtonSize.Compact,
                )
            },
            dismissButton = {
                MkwGhostButton(
                    text = stringResource(R.string.discovery_cancel),
                    onClick = { confirmResetUnlock = false },
                )
            },
        ) {
            Text(
                stringResource(R.string.settings_unlock_reset_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MkwKitTokens.Palette.inkSecondary,
            )
        }
    }

    if (choosingLanguage) {
        MkwBottomSheet(
            onDismissRequest = { choosingLanguage = false },
            title = stringResource(R.string.app_language_title),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppLanguage.entries.forEach { choice ->
                    val selected = language == choice
                    MkwOptionRow(
                        headline = stringResource(choice.labelResource),
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            choosingLanguage = false
                            if (!selected) onLanguage(choice)
                        },
                        trailing = { MkwRadioMark(selected = selected) },
                    )
                }
            }
        }
    }

    if (choosingThemeAccent) {
        MkwBottomSheet(
            onDismissRequest = { choosingThemeAccent = false },
            title = stringResource(R.string.settings_theme_title),
            subtitle = stringResource(R.string.settings_theme_hint),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppThemeAccent.entries.forEach { choice ->
                    val selected = activeThemeAccent == choice
                    MkwOptionRow(
                        headline = stringResource(choice.labelRes),
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            if (!selected) handleSelectThemeAccent(choice)
                            choosingThemeAccent = false
                        },
                        leading = {
                            ThemeAccentSwatchDot(
                                accent = choice,
                                selected = selected,
                            )
                        },
                        trailing = { MkwRadioMark(selected = selected) },
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("library") {
            MkwCard(tone = MkwSurfaceTone.Porcelain, contentPadding = PaddingValues(18.dp)) {
                Text(stringResource(R.string.settings_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                Text(stringResource(R.string.settings_library_hint), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                Text(stringResource(R.string.settings_no_delete_hint), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                sourceName?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MkwKitTokens.Palette.ink) }
                if (sourceCount != null && playableCount != null && (!busy || sourceCount > 0)) {
                    Text(
                        stringResource(R.string.settings_library_counts, sourceCount, playableCount),
                        style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                        color = MkwKitTokens.Palette.crimson,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (busy && importState.totalMembers > 0) {
                    Text(
                        stringResource(
                            R.string.settings_import_progress_members,
                            importState.processedMembers,
                            importState.totalMembers,
                            importState.currentMember.ifBlank { importState.displayName },
                        ),
                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                } else if (busy && importProgress.isNotBlank()) {
                    Text(importProgress, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                }
                importDetail?.let { detail ->
                    var expanded by rememberSaveable(detail) { mutableStateOf(false) }
                    MkwGhostButton(
                        text = stringResource(R.string.settings_import_details),
                        onClick = { expanded = !expanded },
                    )
                    AnimatedVisibility(
                        visible = expanded,
                        enter = MkwMotion.panelEnter(reducedMotion),
                        exit = MkwMotion.panelExit(reducedMotion),
                    ) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                    }
                }
                if (busy) {
                    val progress = if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.PROBING) {
                        0.15f
                    } else {
                        importState.fraction
                    }
                    com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator(progress, Modifier.fillMaxWidth())
                    if (importState.canCancel) {
                        Text(stringResource(R.string.settings_importing), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                        MkwSecondaryButton(
                            text = stringResource(R.string.settings_import_cancel),
                            onClick = onCancelImport,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            stringResource(R.string.settings_import_committing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimson,
                        )
                    }
                } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.RESUMABLE && importState.checkpoint != null) {
                    val cp = importState.checkpoint
                    MkwCard(
                        tone = MkwSurfaceTone.RoseTint,
                        selected = true,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_import_resumable_hint,
                                cp.displayName,
                                cp.completedMembers.size,
                                cp.totalMembers,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimsonDeep,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MkwPrimaryButton(
                                text = stringResource(R.string.settings_import_resume),
                                onClick = onResumeImport,
                                size = MkwButtonSize.Compact,
                                modifier = Modifier.weight(1f),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_import_discard),
                                onClick = onCancelImport,
                                size = MkwButtonSize.Compact,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.PAUSED_NEEDS_REPICK && importState.checkpoint != null) {
                    val cp = importState.checkpoint
                    MkwCard(
                        tone = MkwSurfaceTone.RoseTint,
                        selected = true,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_import_repick_hint,
                                cp.displayName,
                                cp.completedMembers.size,
                                cp.totalMembers,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimsonDeep,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MkwPrimaryButton(
                                text = stringResource(R.string.settings_import_repick),
                                onClick = onRepickImport,
                                size = MkwButtonSize.Compact,
                                modifier = Modifier.weight(1f),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_import_discard),
                                onClick = onCancelImport,
                                size = MkwButtonSize.Compact,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else if (importState.phase == com.mylittleblover.mkwplayer.pack.PackImportPhase.COMPLETED && importState.changelog != null) {
                    val cl = importState.changelog
                    MkwCard(
                        tone = MkwSurfaceTone.White,
                        shape = MkwKitTokens.Shape.control,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_import_summary_title, cl.packName, cl.packVersion),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(
                                R.string.settings_import_summary_body,
                                cl.addedOrUpdatedTracks,
                                cl.addedOrUpdatedAssets,
                                cl.playableLibraryTracks,
                                cl.totalLibraryTracks,
                                cl.jukeboxTrackCount,
                            ),
                            style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                        if (cl.poolStatus == com.mylittleblover.mkwplayer.pack.PoolUpdateStatus.REPLACED && cl.poolDataVersion != null) {
                            Text(
                                stringResource(R.string.settings_import_pool_replaced, cl.poolDataVersion, cl.poolEdgeCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.crimson,
                            )
                        }
                        if (cl.invalidatedCustomRoutesCount > 0) {
                            Text(
                                stringResource(R.string.settings_import_invalidated_routes, cl.invalidatedCustomRoutesCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.danger,
                            )
                        }
                        if (cl.stoppedActivePlayback) {
                            Text(
                                stringResource(R.string.settings_import_stopped_playback),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                        if (cl.clearedContinueListening) {
                            Text(
                                stringResource(R.string.settings_import_cleared_continue),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            MkwGhostButton(
                                text = stringResource(R.string.settings_import_dismiss),
                                onClick = onDismissChangelog,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.settings_import_pack),
                        onClick = onOpenPack,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    MkwSecondaryButton(
                        text = stringResource(R.string.settings_open_files),
                        onClick = onOpenFiles,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item("output-trim") {
            MkwCard(
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 10.dp),
            ) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_output_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                    Text(
                        stringResource(R.string.settings_output_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.mylittleblover.mkwplayer.audio.OutputTrim.choices.forEach { choice ->
                        val selected = choice == outputTrim
                        fun fmtDb(v: Double): String =
                            if (v == v.toLong().toDouble()) "${v.toLong()} dB" else "$v dB"
                        val trimHeadline = when {
                            choice.gainDb == 0.0 && !choice.limits ->
                                stringResource(R.string.settings_output_unchanged)
                            choice.gainDb == 0.0 ->
                                stringResource(R.string.settings_output_unchanged_limited, fmtDb(choice.ceilingDb))
                            choice.limits ->
                                stringResource(R.string.settings_output_gain_limited, fmtDb(choice.gainDb), fmtDb(choice.ceilingDb))
                            else ->
                                stringResource(R.string.settings_output_gain_only, fmtDb(choice.gainDb))
                        }
                        MkwOptionRow(
                            headline = trimHeadline,
                            supporting = stringResource(
                                if (choice.limits) R.string.settings_output_limited else R.string.settings_output_flat,
                            ),
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { if (!selected) onOutputTrim(choice) },
                            trailing = { MkwRadioMark(selected = selected) },
                        )
                    }
                }
            }
        }
        item("segment-switch-fade") {
            val initialSecondsText = remember(playbackBehavior.segmentSwitchFadeOutMs) {
                val ms = playbackBehavior.segmentSwitchFadeOutMs
                when {
                    ms <= 0 -> "0"
                    ms % 1000 == 0 -> (ms / 1000).toString()
                    else -> String.format(java.util.Locale.ROOT, "%.2f", ms / 1000.0).trimEnd('0').trimEnd('.')
                }
            }
            var customInput by remember(playbackBehavior.segmentSwitchFadeOutMs) {
                mutableStateOf(initialSecondsText)
            }
            val parsedMs = remember(customInput) {
                val secs = customInput.trim().toDoubleOrNull()
                if (secs != null && secs.isFinite() && secs >= 0.0) {
                    kotlin.math.round(secs * 1000.0).toInt().coerceIn(
                        0,
                        com.mylittleblover.mkwplayer.audio.PlaybackBehaviorSettings.MAX_SEGMENT_SWITCH_FADEOUT_MS,
                    )
                } else null
            }
            MkwCard(tone = MkwSurfaceTone.Porcelain, contentPadding = PaddingValues(18.dp)) {
                Text(
                    stringResource(R.string.settings_switch_fade_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
                Text(
                    stringResource(R.string.settings_switch_fade_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
                Text(
                    text = if (playbackBehavior.segmentSwitchFadeOutMs <= 0) {
                        stringResource(R.string.settings_switch_fade_off)
                    } else {
                        stringResource(R.string.settings_switch_fade_seconds_fmt, initialSecondsText)
                    },
                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                    color = MkwKitTokens.Palette.crimson,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0 to "0", 500 to "0.5", 1000 to "1", 2000 to "2", 5000 to "5").forEach { (ms, secsText) ->
                        MkwFilterPill(
                            label = stringResource(R.string.settings_switch_fade_chip_sec_fmt, secsText),
                            selected = playbackBehavior.segmentSwitchFadeOutMs == ms,
                            onClick = { onSegmentSwitchFadeOutMs(ms) },
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
                        onValueChange = { raw ->
                            customInput = raw.filter { ch -> ch.isDigit() || ch == '.' }.take(6)
                        },
                        label = stringResource(R.string.settings_switch_fade_custom_label),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    MkwRoseButton(
                        text = stringResource(R.string.settings_switch_fade_apply),
                        onClick = { parsedMs?.let(onSegmentSwitchFadeOutMs) },
                        enabled = parsedMs != null && parsedMs != playbackBehavior.segmentSwitchFadeOutMs,
                        size = MkwButtonSize.Hero,
                    )
                }
            }
        }
        item("perfect-beat") {
            MkwCard(
                onClick = { onPerfectBeat(!playbackBehavior.perfectBeatEnabled) },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_perfect_beat_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(R.string.settings_perfect_beat_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                    MkwSwitch(
                        checked = playbackBehavior.perfectBeatEnabled,
                        onCheckedChange = onPerfectBeat,
                    )
                }
            }
        }
        item("live-visualizer") {
            MkwCard(
                onClick = { onLiveVisualizer(!playbackBehavior.liveVisualizerEnabled) },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_visualizer_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(R.string.settings_visualizer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                    MkwSwitch(
                        checked = playbackBehavior.liveVisualizerEnabled,
                        onCheckedChange = onLiveVisualizer,
                    )
                }
            }
        }
        item("equalizer-entry") {
            val eqActive = dspConfig.eqEnabled || dspConfig.bassBoostPercent > 0 || dspConfig.loudnessDb >= 0.05f
            val presetLabel = com.mylittleblover.mkwplayer.ui.components.EqualizerPresetLabel(dspConfig.preset)
            MkwCard(
                onClick = onOpenEqualizer,
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_eq_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(R.string.settings_eq_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                        Text(
                            if (eqActive) stringResource(R.string.settings_eq_summary_on, presetLabel)
                            else stringResource(R.string.settings_eq_summary_off),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (eqActive) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    MkwRoseButton(
                        text = stringResource(R.string.eq_short_label),
                        onClick = onOpenEqualizer,
                        size = MkwButtonSize.Compact,
                    )
                }
            }
        }
        item("backup-restore") {
            MkwCard(tone = MkwSurfaceTone.Porcelain, contentPadding = PaddingValues(18.dp)) {
                Text(
                    stringResource(R.string.settings_backup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
                Text(
                    stringResource(R.string.settings_backup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MkwPrimaryButton(
                        text = stringResource(R.string.settings_backup_export),
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f),
                    )
                    MkwRoseButton(
                        text = stringResource(R.string.settings_backup_import),
                        onClick = onImportBackup,
                        modifier = Modifier.weight(1f),
                    )
                }
                MkwSecondaryButton(
                    text = stringResource(R.string.settings_backup_m3u),
                    onClick = onExportM3u,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!backupStatusMessage.isNullOrBlank()) {
                    Text(
                        backupStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.crimson,
                    )
                }
            }
        }
        if (onPickExportLocation != null || !exportLocationLabel.isNullOrBlank()) {
            item("export-folder") {
                MkwCard(
                    tone = MkwSurfaceTone.Porcelain,
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
                ) {
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_export_folder_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(R.string.settings_export_folder_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                    MkwOptionRow(
                        headline = exportLocationLabel?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.settings_export_folder_default),
                        onClick = { onPickExportLocation?.invoke() },
                        enabled = onPickExportLocation != null,
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.crimson,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailing = if (onPickExportLocation != null) {
                            {
                                MkwFilterPill(
                                    label = stringResource(R.string.settings_export_folder_change),
                                    selected = true,
                                    onClick = onPickExportLocation,
                                    role = Role.Button,
                                )
                            }
                        } else null,
                    )
                }
            }
        }
        item("theme-accent") {
            MkwCard(
                onClick = { choosingThemeAccent = true },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_theme_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        Text(
                            stringResource(R.string.settings_theme_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                    ThemeAccentValuePill(
                        accent = activeThemeAccent,
                        onClick = { choosingThemeAccent = true },
                    )
                }
            }
        }
        item("language-heading") {
            MkwCard(
                onClick = { choosingLanguage = true },
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.app_language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                        Text(stringResource(R.string.settings_language_hint), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                    }
                    MkwFilterPill(
                        label = stringResource(language.labelResource),
                        selected = true,
                        onClick = { choosingLanguage = true },
                        trailingIcon = Icons.Filled.ArrowDropDown,
                        role = Role.Button,
                    )
                }
            }
        }
        item("updates-links") {
            MkwCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = MkwMotion.sizeSpring()),
                tone = MkwSurfaceTone.Porcelain,
                contentPadding = PaddingValues(18.dp),
            ) {
                Text(
                    stringResource(R.string.settings_updates_links_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
                Text(
                    stringResource(R.string.settings_updates_links_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
                MkwRoseButton(
                    text = if (updateUiState is UpdateUiState.Checking) {
                        stringResource(R.string.settings_update_checking)
                    } else {
                        stringResource(R.string.settings_update_check_btn)
                    },
                    onClick = {
                        if (updateUiState !is UpdateUiState.Checking) {
                            updateUiState = UpdateUiState.Checking
                            coroutineScope.launch {
                                val res = UpdateChecker.checkForUpdates(BuildConfig.VERSION_NAME)
                                updateUiState = UpdateUiState.Done(res)
                            }
                        }
                    },
                    leadingIcon = Icons.Filled.SystemUpdate,
                    enabled = updateUiState !is UpdateUiState.Checking,
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedContent(
                    targetState = updateUiState,
                    transitionSpec = { MkwMotion.viewSwap(reducedMotion) },
                    label = "settings-update-status",
                ) { state ->
                    when (state) {
                        UpdateUiState.Idle -> Spacer(Modifier.height(0.dp))

                        UpdateUiState.Checking -> MkwCard(
                            tone = MkwSurfaceTone.White,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MkwKitTokens.Palette.crimson,
                                )
                                Text(
                                    text = stringResource(R.string.settings_update_checking),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.inkSecondary,
                                )
                            }
                        }

                        is UpdateUiState.Done -> when (val res = state.result) {
                            is UpdateCheckResult.UpToDate -> MkwCard(
                                tone = MkwSurfaceTone.White,
                                shape = MkwKitTokens.Shape.control,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MkwKitTokens.Palette.crimson,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_update_up_to_date, res.currentVersion),
                                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MkwKitTokens.Palette.ink,
                                    )
                                }
                            }

                            is UpdateCheckResult.UpdateAvailable -> MkwCard(
                                tone = MkwSurfaceTone.RoseTint,
                                selected = true,
                                shape = MkwKitTokens.Shape.control,
                                contentPadding = PaddingValues(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_update_available_title, res.latestTag),
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.titleSmall),
                                    fontWeight = FontWeight.Bold,
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                )
                                Text(
                                    text = stringResource(R.string.settings_update_available_subtitle, res.currentVersion),
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                    color = MkwKitTokens.Palette.inkSecondary,
                                )
                                if (res.releaseNotes.isNotBlank()) {
                                    Text(
                                        text = res.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MkwKitTokens.Palette.ink,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                MkwPrimaryButton(
                                    text = stringResource(R.string.settings_update_download_btn),
                                    onClick = { openExternalUrl(res.htmlUrl) },
                                    leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                                    size = MkwButtonSize.Compact,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            is UpdateCheckResult.Error -> {
                                val errorMsg = when (res.kind) {
                                    UpdateErrorKind.NETWORK -> stringResource(R.string.settings_update_err_network)
                                    UpdateErrorKind.RATE_LIMITED -> stringResource(R.string.settings_update_err_rate_limited)
                                    UpdateErrorKind.HTTP_ERROR -> stringResource(
                                        R.string.settings_update_err_http,
                                        res.detail.orEmpty(),
                                    )
                                    UpdateErrorKind.NO_RELEASES -> stringResource(R.string.settings_update_err_no_releases)
                                    UpdateErrorKind.PARSE_ERROR -> stringResource(R.string.settings_update_err_parse)
                                }
                                MkwCard(
                                    tone = MkwSurfaceTone.Porcelain,
                                    error = true,
                                    shape = MkwKitTokens.Shape.control,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = MkwKitTokens.Palette.danger,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = errorMsg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MkwKitTokens.Palette.danger,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                MkwDivider(modifier = Modifier.padding(vertical = 4.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MkwOptionRow(
                        headline = stringResource(R.string.settings_link_github_title),
                        supporting = stringResource(R.string.settings_link_github_subtitle),
                        onClick = { openExternalUrl(UpdateChecker.GITHUB_REPO_URL) },
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.crimson,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.inkSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    MkwOptionRow(
                        headline = stringResource(R.string.settings_link_issues_title),
                        supporting = stringResource(R.string.settings_link_issues_subtitle),
                        onClick = { openExternalUrl(UpdateChecker.GITHUB_ISSUES_URL) },
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.crimson,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.inkSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    MkwOptionRow(
                        headline = stringResource(R.string.settings_link_bilibili_title),
                        supporting = stringResource(R.string.settings_link_bilibili_subtitle),
                        onClick = { openExternalUrl(UpdateChecker.AUTHOR_BILIBILI_URL) },
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.crimson,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.inkSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
        if (devUnlocked || testMode != UnlockTestMode.NORMAL) {
            item("unlock-test") {
                MkwCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(tween(180, easing = AppMotion.easeOut)),
                    tone = MkwSurfaceTone.Porcelain,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    MkwOptionRow(
                        headline = stringResource(R.string.settings_dev_title),
                        supporting = stringResource(R.string.settings_unlock_test_title),
                        selected = devExpanded,
                        onClick = { devExpanded = !devExpanded },
                        trailing = {
                            Icon(if (devExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MkwKitTokens.Palette.inkSecondary)
                        },
                    )
                    AnimatedVisibility(
                        visible = devExpanded,
                        enter = MkwMotion.panelEnter(reducedMotion),
                        exit = MkwMotion.panelExit(reducedMotion),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.settings_unlock_test_hint), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                                Text(
                                    stringResource(when (testMode) {
                                        UnlockTestMode.NORMAL -> R.string.settings_unlock_test_normal
                                        UnlockTestMode.FORCE_UNLOCKED -> R.string.settings_unlock_test_unlocked
                                        UnlockTestMode.FORCE_LOCKED -> R.string.settings_unlock_test_locked
                                    }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.crimson,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    stringResource(if (testUnlockVisible) R.string.settings_unlock_test_visible else R.string.settings_unlock_test_hidden),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.inkSecondary,
                                )
                            }
                            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    UnlockTestMode.NORMAL to R.string.settings_unlock_test_restore,
                                    UnlockTestMode.FORCE_UNLOCKED to R.string.settings_unlock_test_unlock,
                                    UnlockTestMode.FORCE_LOCKED to R.string.settings_unlock_test_lock,
                                ).forEach { (mode, label) ->
                                    val selected = mode == testMode
                                    MkwOptionRow(
                                        headline = stringResource(label),
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { if (!selected) onTestUnlock(mode) },
                                        trailing = { MkwRadioMark(selected = selected) },
                                    )
                                }
                            }
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_unlock_test_ceremony_btn),
                                onClick = onTestCeremony,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_unlock_test_vfx_btn),
                                onClick = onTestUnlockVfx,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MkwSecondaryButton(
                                text = stringResource(R.string.settings_unlock_test_notif_btn),
                                onClick = onTestUnlockNotification,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MkwDivider(modifier = Modifier.padding(vertical = 2.dp))
                            MkwDangerButton(
                                text = stringResource(R.string.settings_unlock_reset_btn),
                                onClick = { confirmResetUnlock = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        item("about") {
            var showLicensesDialog by rememberSaveable { mutableStateOf(false) }
            var selectedLicenseFile by rememberSaveable { mutableStateOf<String?>(null) }
            val licenseFiles = remember(context) {
                runCatching { context.assets.list("licenses")?.sorted().orEmpty() }.getOrDefault(emptyList())
            }
            val selectedLicenseText = remember(context, selectedLicenseFile) {
                val target = selectedLicenseFile ?: return@remember null
                runCatching {
                    context.assets.open("licenses/$target").bufferedReader().use { it.readText() }
                }.getOrNull()
            }

            if (showLicensesDialog) {
                MkwDialog(
                    onDismissRequest = {
                        selectedLicenseFile = null
                        showLicensesDialog = false
                    },
                    title = stringResource(R.string.settings_open_source_licenses),
                    confirmButton = {
                        MkwPrimaryButton(
                            text = if (selectedLicenseFile != null) {
                                stringResource(R.string.common_back)
                            } else {
                                stringResource(R.string.settings_licenses_close)
                            },
                            onClick = {
                                if (selectedLicenseFile != null) {
                                    selectedLicenseFile = null
                                } else {
                                    showLicensesDialog = false
                                }
                            },
                            size = MkwButtonSize.Compact,
                        )
                    },
                    dismissButton = if (selectedLicenseFile != null) {
                        {
                            MkwGhostButton(
                                text = stringResource(R.string.settings_licenses_close),
                                onClick = {
                                    selectedLicenseFile = null
                                    showLicensesDialog = false
                                },
                            )
                        }
                    } else null,
                ) {
                    if (selectedLicenseText != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                selectedLicenseFile.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.ink,
                            )
                            Text(
                                selectedLicenseText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_open_source_licenses_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MkwKitTokens.Palette.inkSecondary,
                            )
                            MkwDivider(modifier = Modifier.padding(vertical = 2.dp))
                            licenseFiles.forEach { fileName ->
                                MkwOptionRow(
                                    headline = fileName.removeSuffix(".txt"),
                                    supporting = "assets/licenses/$fileName",
                                    selected = false,
                                    onClick = { selectedLicenseFile = fileName },
                                )
                            }
                        }
                    }
                }
            }

            MkwCard(tone = MkwSurfaceTone.Porcelain, contentPadding = PaddingValues(18.dp)) {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MkwKitTokens.Palette.ink)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, color = MkwKitTokens.Palette.ink)
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MkwKitTokens.tabular(MaterialTheme.typography.bodyMedium),
                    color = MkwKitTokens.Palette.inkSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MkwKitTokens.Shape.thumb)
                        .clickable {
                            val res = tapTracker.tap(SystemClock.elapsedRealtime())
                            val msg = when (res) {
                                DevOptionsTapTracker.Result.Ignored -> null
                                is DevOptionsTapTracker.Result.Progress ->
                                    context.getString(R.string.settings_dev_taps_remaining, res.remaining)
                                DevOptionsTapTracker.Result.UnlockedNow -> {
                                    devUnlocked = true
                                    devExpanded = true
                                    context.getString(R.string.settings_dev_unlocked)
                                }
                                DevOptionsTapTracker.Result.AlreadyUnlocked -> {
                                    devUnlocked = true
                                    context.getString(R.string.settings_dev_already_unlocked)
                                }
                            }
                            if (msg != null) {
                                activeToast?.cancel()
                                activeToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT).also { it.show() }
                            }
                        }
                        .padding(vertical = 4.dp),
                )
                Text(stringResource(R.string.settings_non_affiliation), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                Text(stringResource(R.string.map_art_notice), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                Text(stringResource(R.string.settings_feedback_repo), style = MaterialTheme.typography.bodySmall, color = MkwKitTokens.Palette.inkSecondary)
                Spacer(Modifier.height(4.dp))
                MkwSecondaryButton(
                    text = stringResource(R.string.settings_open_source_licenses),
                    onClick = { showLicensesDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ThemeAccentValuePill(
    accent: AppThemeAccent,
    onClick: () -> Unit,
) {
    val (pressScale, interactionSource) = rememberPressScale(pressedScale = 0.97f)
    Box(
        modifier = Modifier.defaultMinSize(minHeight = MkwKitTokens.Size.pillHeight),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = MkwKitTokens.Shape.pill,
            color = MkwKitTokens.Palette.rose,
            border = BorderStroke(
                MkwKitTokens.Stroke.border,
                MkwKitTokens.Palette.crimson.copy(alpha = 0.55f),
            ),
            modifier = Modifier
                .heightIn(min = MkwKitTokens.Size.pillHeight)
                .then(pressScale)
                .semantics { role = Role.Button },
        ) {
            Row(
                modifier = Modifier.padding(start = 11.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent.swatchPreview)
                        .border(0.75.dp, Color.Black.copy(alpha = 0.16f), CircleShape),
                )
                Text(
                    text = stringResource(accent.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.crimsonDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MkwKitTokens.Palette.crimsonDeep,
                )
            }
        }
    }
}

@Composable
private fun ThemeAccentSwatchDot(
    accent: AppThemeAccent,
    selected: Boolean,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .border(
                width = if (selected) MkwKitTokens.Stroke.focused else MkwKitTokens.Stroke.hairline,
                color = if (selected) accent.primary else MkwKitTokens.Palette.hairline,
                shape = CircleShape,
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(accent.swatchPreview)
                .border(0.75.dp, Color.Black.copy(alpha = 0.14f), CircleShape),
        )
    }
}
