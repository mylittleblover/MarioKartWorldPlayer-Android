package com.mylittleblover.mkwplayer.ui.interactive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.audio.AudioExportSettings
import com.mylittleblover.mkwplayer.audio.BwavWaveformExtractor
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder
import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDangerButton
import com.mylittleblover.mkwplayer.ui.kit.MkwDialog
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButtonTone
import com.mylittleblover.mkwplayer.ui.kit.MkwInkButton
import com.mylittleblover.mkwplayer.ui.kit.MkwInsetBox
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwRoseButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import com.mylittleblover.mkwplayer.ui.routes.builtInSuiteLabelRes
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Event-driven course flow preset builder.
 *
 * Enforces authentic interactive graph transitions ([CourseFlowRecipeBuilder.CourseInventory.allowedNextRoles]),
 * keeps Bridge parts locked to MINF `Bridge_ToFull` handover markers, provides per-step expandable
 * zoomable BWAV waveforms with magnetic exit snapping, inline seam auditioning, and JSON export.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FlowEditorScreen(
    courseTitle: String,
    inventory: CourseFlowRecipeBuilder.CourseInventory,
    existingPreset: CoursePreset?,
    onAudition: (CoursePreset) -> Unit,
    onSave: (CoursePreset) -> Unit,
    modifier: Modifier = Modifier,
    onLoadWaveform: suspend (stem: String) -> BwavWaveformExtractor.WaveformPeaks? = { null },
    onPreviewSeam: (CoursePreset, seamIndex: Int) -> Unit = { _, _ -> },
    onCompileExport: (CoursePreset) -> String? = { null },
    isRenderingAudio: Boolean = false,
    renderAudioProgress: Float = 0f,
    renderAudioStatus: String? = null,
    renderAudioError: String? = null,
    renderAudioOutputPath: String? = null,
    outputTrimEnabled: Boolean = true,
    exportFolderDisplay: String? = null,
    onRenderAudio: ((CoursePreset) -> Unit)? = null,
    onCancelRenderAudio: (() -> Unit)? = null,
    onDismissRenderDialog: (() -> Unit)? = null,
) {
    val localizedDefaultName = stringResource(R.string.flow_editor_default_preset_name, courseTitle)
    val defaultName = remember(localizedDefaultName, existingPreset) {
        existingPreset?.name ?: localizedDefaultName
    }
    var presetName by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf(defaultName)
    }
    val stepsSaver = remember(inventory.courseCode) {
        androidx.compose.runtime.saveable.listSaver<List<FlowStep>, Any>(
            save = { list ->
                list.flatMap { step ->
                    listOf(step.roleKey, step.loopCount, step.exitFrame ?: -1L)
                }
            },
            restore = { flat ->
                flat.chunked(3).mapNotNull { chunk ->
                    if (chunk.size < 3) null
                    else {
                        val roleKey = chunk[0] as? String ?: return@mapNotNull null
                        val loopCount = (chunk[1] as? Number)?.toInt() ?: 0
                        val rawExit = (chunk[2] as? Number)?.toLong() ?: -1L
                        FlowStep(roleKey, loopCount, rawExit.takeIf { it > 0L })
                    }
                }
            },
        )
    }
    var steps by rememberSaveable(existingPreset?.id, inventory.courseCode, stateSaver = stepsSaver) {
        mutableStateOf(
            (existingPreset?.steps ?: inventory.defaultEditorSteps()).map { step ->
                val part = inventory.part(step.roleKey)
                if (part?.isBridgeLocked == true) {
                    step.copy(loopCount = 0, exitFrame = null)
                } else {
                    step
                }
            },
        )
    }
    var selectedOutroNumber by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf(existingPreset?.outroNumber)
    }
    var fadeOutMs by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableIntStateOf(existingPreset?.fadeOutMs ?: 3000)
    }
    var outroToNextPre by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf(existingPreset?.outroToNextPre ?: true)
    }
    var expandedWaveformStep by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf<Int?>(null)
    }
    var activeBuiltInVariant by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf<String?>(null)
    }
    var showRenderDialog by rememberSaveable(existingPreset?.id, inventory.courseCode) {
        mutableStateOf(false)
    }

    val waveformCache = remember(inventory.courseCode) {
        mutableStateMapOf<String, BwavWaveformExtractor.WaveformPeaks?>()
    }
    val waveformLoaded = remember(inventory.courseCode) {
        mutableStateMapOf<String, Boolean>()
    }

    val stepSourceOffsets = remember(steps, selectedOutroNumber, inventory) {
        CourseFlowRecipeBuilder.stepSourceOffsets(steps, selectedOutroNumber, inventory)
    }

    val tailOutros = remember(steps, inventory) {
        inventory.availableOutrosForTail(steps)
    }

    // Ensure outro selection remains strictly event-legal and locks/snaps the host exit point on courses
    LaunchedEffect(steps, selectedOutroNumber, inventory, stepSourceOffsets, tailOutros) {
        val outroNum = selectedOutroNumber
        if (outroNum != null && tailOutros.none { it.number == outroNum }) {
            selectedOutroNumber = null
            return@LaunchedEffect
        }
        if (inventory.courseCode.startsWith("extra:")) return@LaunchedEffect
        if (outroNum == null) return@LaunchedEffect
        val outro = tailOutros.firstOrNull { it.number == outroNum } ?: return@LaunchedEffect
        if (outro.mainTriggerPoints.isEmpty() || steps.isEmpty()) return@LaunchedEffect
        val lastStep = steps.last()
        val lastPart = inventory.part(lastStep.roleKey)
        val lastSourceOffset = stepSourceOffsets.lastOrNull() ?: 0L
        val validTriggers = CourseFlowRecipeBuilder.reachableOutroTriggers(
            step = lastStep,
            sourceOffset = lastSourceOffset,
            part = lastPart,
            outro = outro,
        )
        if (validTriggers.isNotEmpty() && lastStep.exitFrame !in validTriggers) {
            val snapped = if (validTriggers.size == 1) {
                validTriggers.single()
            } else {
                validTriggers.firstOrNull { it > lastSourceOffset }
                    ?: validTriggers.firstOrNull { it >= (lastStep.exitFrame ?: 0L) }
                    ?: validTriggers.last()
            }
            steps = steps.dropLast(1) + lastStep.copy(exitFrame = snapped)
        }
    }

    // Preload waveform lazily whenever a step waveform panel is opened
    LaunchedEffect(expandedWaveformStep, steps) {
        val idx = expandedWaveformStep ?: return@LaunchedEffect
        val roleKey = steps.getOrNull(idx)?.roleKey ?: return@LaunchedEffect
        val stem = inventory.part(roleKey)?.stem ?: return@LaunchedEffect
        if (waveformLoaded[stem] != true) {
            waveformCache[stem] = onLoadWaveform(stem)
            waveformLoaded[stem] = true
        }
    }

    fun draftPreset(): CoursePreset {
        val stableId = existingPreset?.id ?: PresetRefs.USER_PREFIX + UUID.randomUUID().toString()
        val effectiveOutro = selectedOutroNumber?.takeIf { num ->
            inventory.availableOutrosForTail(steps).any { it.number == num }
        }
        return CoursePreset(
            id = stableId,
            courseCode = inventory.courseCode,
            name = presetName.trim().ifBlank { defaultName },
            steps = steps.map { step ->
                if (inventory.part(step.roleKey)?.isBridgeLocked == true) {
                    step.copy(loopCount = 0, exitFrame = null)
                } else {
                    step
                }
            },
            outroNumber = effectiveOutro,
            fadeOutMs = if (effectiveOutro == null) fadeOutMs else 0,
            outroToNextPre = effectiveOutro != null && outroToNextPre,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    val compiledStats = remember(steps, selectedOutroNumber, fadeOutMs, inventory) {
        CourseFlowRecipeBuilder.estimateTimelineFrames(steps, selectedOutroNumber, fadeOutMs, inventory)
    }
    val builtInSuites = remember(inventory) {
        inventory.builtInPresets
            .filterNot { it.isKnockout }
            .distinctBy { it.medley.normalizedSuiteVariant }
            .sortedBy { it.medley.suiteSortOrder }
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var exportedJson by remember { mutableStateOf<String?>(null) }

    val msgCopied = stringResource(R.string.flow_editor_export_copied)
    val msgSaved = stringResource(R.string.flow_editor_export_saved)
    val msgExportFailed = stringResource(R.string.flow_editor_export_failed)

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = exportedJson ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(json)
                }
            }.onSuccess {
                scope.launch { snackbarHostState.showSnackbar(msgSaved) }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(0.dp)),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = com.mylittleblover.mkwplayer.ui.rememberMiniPlayerBottomClearance(16.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = courseTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.flow_editor_timeline_total,
                                    EngineSnapshot.formatPosition(compiledStats, inventory.sampleRate),
                                    steps.size,
                                ),
                                style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                                color = MkwKitTokens.Palette.crimson,
                            )
                        }
                        MkwRoseButton(
                            text = stringResource(R.string.flow_editor_export_button),
                            onClick = {
                                val json = onCompileExport(draftPreset())
                                if (json != null) {
                                    exportedJson = json
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar(msgExportFailed) }
                                }
                            },
                            enabled = steps.isNotEmpty(),
                            leadingIcon = Icons.Default.Share,
                            size = MkwButtonSize.Compact,
                        )
                    }

                    MkwTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(64) },
                        label = stringResource(R.string.flow_editor_name_label),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (onRenderAudio != null || isRenderingAudio || renderAudioOutputPath != null || renderAudioError != null) {
                        val renderPct = (renderAudioProgress.coerceIn(0f, 1f) * 100f).roundToInt()
                        MkwInkButton(
                            text = if (isRenderingAudio) {
                                stringResource(R.string.flow_render_button_progress_fmt, renderPct)
                            } else {
                                stringResource(R.string.flow_render_button)
                            },
                            onClick = { showRenderDialog = true },
                            enabled = steps.isNotEmpty() || isRenderingAudio,
                            leadingIcon = Icons.Default.LibraryMusic,
                            size = MkwButtonSize.Regular,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Built-in medley suite presets strictly scoped to variants available for this course
            if (builtInSuites.isNotEmpty()) {
                item {
                    MkwCard(
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.flow_editor_builtin_suites_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MkwKitTokens.Palette.ink,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            builtInSuites.forEach { summary ->
                                val variantKey = summary.medley.normalizedSuiteVariant
                                val suiteLabel = stringResource(builtInSuiteLabelRes(variantKey))
                                MkwFilterPill(
                                    label = suiteLabel,
                                    selected = activeBuiltInVariant == variantKey,
                                    onClick = {
                                        val draft = decodeMedleyToFlowDraft(summary.medley, inventory)
                                        steps = draft.steps
                                        selectedOutroNumber = draft.endingOutro
                                        fadeOutMs = draft.fadeOutMs
                                        outroToNextPre = draft.outroToNextPre
                                        expandedWaveformStep = null
                                        activeBuiltInVariant = variantKey
                                    },
                                    role = Role.Button,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.flow_editor_steps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.ink,
                )
            }

            if (steps.isEmpty()) {
                item {
                    MkwInsetBox(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.flow_editor_no_steps),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MkwKitTokens.Palette.inkSecondary,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }

            itemsIndexed(steps, key = { idx, step -> "$idx:${step.roleKey}" }) { index, step ->
                val part = inventory.part(step.roleKey)
                val prevRole = steps.getOrNull(index - 1)?.roleKey
                val canRemoveStep = index == steps.lastIndex || canSafelyRemoveMiddle(steps, index, inventory)
                val isWaveformOpen = expandedWaveformStep == index
                val peaks = part?.stem?.let { waveformCache[it] }
                val waveformReady = part?.stem?.let { waveformLoaded[it] == true } ?: false
                val nextStep = steps.getOrNull(index + 1)
                val middlePairedOutro = if (inventory.courseCode == "Rainbow" &&
                    step.roleKey == "Area2" &&
                    nextStep?.roleKey?.startsWith("Area2_Outro") == true
                ) {
                    nextStep.roleKey.removePrefix("Area2_Outro").toIntOrNull()
                        ?.let { n -> inventory.outros.firstOrNull { it.number == n } }
                } else null
                val area2OutroStepInfo = if (inventory.courseCode == "Rainbow" && step.roleKey.startsWith("Area2_Outro")) {
                    step.roleKey.removePrefix("Area2_Outro").toIntOrNull()
                        ?.let { n -> inventory.outros.firstOrNull { it.number == n } }
                } else null
                val isArea2OutroHandover = area2OutroStepInfo != null &&
                    index < steps.lastIndex &&
                    nextStep?.roleKey == "Area3_Pre"
                val isTailOutroHost = !inventory.courseCode.startsWith("extra:") &&
                    index == steps.lastIndex &&
                    tailOutros.isNotEmpty()
                val activeOutro = middlePairedOutro ?: if (isTailOutroHost) {
                    selectedOutroNumber?.let { n -> tailOutros.firstOrNull { it.number == n } }
                } else null
                val outroTriggers = if (activeOutro != null && part != null) {
                    CourseFlowRecipeBuilder.reachableOutroTriggers(
                        step = step,
                        sourceOffset = stepSourceOffsets.getOrElse(index) { 0L },
                        part = part,
                        outro = activeOutro,
                    )
                } else emptyList()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowStepEventCard(
                        courseCode = inventory.courseCode,
                        index = index,
                        step = step,
                        part = part,
                        prevRole = prevRole,
                        incomingEventLabel = localizedTransitionEventLabel(inventory, prevRole, step.roleKey),
                        canRemove = canRemoveStep,
                        isWaveformExpanded = isWaveformOpen,
                        waveformReady = waveformReady,
                        peaks = peaks,
                        activeOutroNumber = activeOutro?.number,
                        outroTriggers = outroTriggers,
                        area2OutroHandoverFrame = if (isArea2OutroHandover) area2OutroStepInfo?.outroToNextPreFrame else null,
                        rainbowArea2Outros = if (inventory.courseCode == "Rainbow" && step.roleKey == "Area2") inventory.outros else emptyList(),
                        selectedRainbowArea2Outro = middlePairedOutro?.number,
                        onSelectRainbowArea2Outro = { chosenNum ->
                            val updatedSteps = steps.toMutableList()
                            val hasMiddleOutro = updatedSteps.getOrNull(index + 1)?.roleKey?.startsWith("Area2_Outro") == true
                            if (chosenNum == null) {
                                if (hasMiddleOutro) {
                                    updatedSteps.removeAt(index + 1)
                                }
                                updatedSteps[index] = step.copy(exitFrame = part?.defaultExitFrame)
                                if (index == updatedSteps.lastIndex) selectedOutroNumber = null
                            } else {
                                val targetRole = "Area2_Outro%02d".format(chosenNum)
                                val chosenOutro = inventory.outros.firstOrNull { it.number == chosenNum }
                                val outroPart = inventory.part(targetRole)
                                val lockedExit = chosenOutro?.mainTriggerPoints?.firstOrNull() ?: step.exitFrame ?: part?.defaultExitFrame
                                updatedSteps[index] = step.copy(exitFrame = lockedExit)
                                val outroStep = FlowStep(
                                    roleKey = targetRole,
                                    loopCount = 0,
                                    exitFrame = outroPart?.frames,
                                )
                                if (hasMiddleOutro) {
                                    updatedSteps[index + 1] = outroStep
                                } else {
                                    updatedSteps.add(index + 1, outroStep)
                                }
                                selectedOutroNumber = null
                            }
                            steps = updatedSteps
                            activeBuiltInVariant = null
                        },
                        onToggleWaveform = {
                            expandedWaveformStep = if (isWaveformOpen) null else index
                        },
                        onRemove = {
                            if (canRemoveStep) {
                                steps = steps.toMutableList().apply { removeAt(index) }
                                activeBuiltInVariant = null
                                if (expandedWaveformStep == index) expandedWaveformStep = null
                                else if ((expandedWaveformStep ?: -1) > index) expandedWaveformStep = expandedWaveformStep!! - 1
                            }
                        },
                        onUpdate = { updated ->
                            if (part?.isBridgeLocked != true && !isArea2OutroHandover) {
                                val nextStepVal = if (outroTriggers.size == 1) {
                                    updated.copy(exitFrame = outroTriggers.single())
                                } else {
                                    updated
                                }
                                steps = steps.toMutableList().apply { set(index, nextStepVal) }
                                activeBuiltInVariant = null
                            }
                        },
                    )

                    // Seam connector & Seam Preview ("衔接预览") between adjacent steps
                    val updatedNext = steps.getOrNull(index + 1)
                    if (updatedNext != null) {
                        SeamTransitionRow(
                            eventLabel = localizedTransitionEventLabel(inventory, step.roleKey, updatedNext.roleKey),
                            onPreviewSeam = { onPreviewSeam(draftPreset(), index) },
                        )
                    } else if (selectedOutroNumber != null && tailOutros.any { it.number == selectedOutroNumber }) {
                        SeamTransitionRow(
                            eventLabel = stringResource(R.string.flow_editor_outro_handover, selectedOutroNumber!!),
                            onPreviewSeam = { onPreviewSeam(draftPreset(), index) },
                        )
                    }
                }
            }

            // Event-driven next transition picker
            item {
                val allowedNext = remember(steps, inventory) {
                    inventory.allowedNextRoles(steps)
                }
                MkwCard(
                    tone = MkwSurfaceTone.Porcelain,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.flow_editor_next_event_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MkwKitTokens.Palette.ink,
                    )
                    if (allowedNext.isEmpty()) {
                        Text(
                            text = stringResource(R.string.flow_editor_no_next_event),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    } else {
                        val tailRole = steps.lastOrNull()?.roleKey
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            allowedNext.forEach { role ->
                                val part = inventory.part(role)
                                val eventText = if (tailRole == null) {
                                    roleDisplayLabel(role, inventory.courseCode)
                                } else {
                                    localizedTransitionEventLabel(inventory, tailRole, role)
                                }
                                MkwFilterPill(
                                    label = eventText,
                                    selected = false,
                                    onClick = {
                                        val defaultLoop = if (part?.supportsLoop == true &&
                                            (role in setOf("Main", "Main_Speaker", "Pattern2", "Area1_Inst", "Area1", "Area2", "Area3", "Final") || role.startsWith("Region"))
                                        ) 1 else 0
                                        steps = steps + FlowStep(
                                            roleKey = role,
                                            loopCount = defaultLoop,
                                            exitFrame = if (part?.isBridgeLocked == true) null else part?.defaultExitFrame,
                                        )
                                        activeBuiltInVariant = null
                                    },
                                    leadingIcon = Icons.Default.Add,
                                    role = Role.Button,
                                )
                            }
                        }
                    }
                }
            }

            // Verified Outro / Fade ending
            item {
                val noFadeLabel = stringResource(R.string.flow_editor_no_fade)
                val options = buildList<DrawerOption<Pair<Int?, Int>>> {
                    tailOutros.forEach { outro ->
                        add(
                            DrawerOption(
                                value = outro.number to 0,
                                label = stringResource(R.string.flow_editor_outro_item, outro.number),
                                subtitle = if (outro.supportsNativeHandover) {
                                    stringResource(R.string.flow_editor_outro_native_ready)
                                } else null,
                            ),
                        )
                    }
                    listOf(1000, 2000, 3000, 5000, 10000, 0).forEach { ms ->
                        add(
                            DrawerOption(
                                value = null to ms,
                                label = if (ms == 0) noFadeLabel
                                else stringResource(R.string.flow_editor_outro_none_fade, ms / 1000),
                            ),
                        )
                    }
                }
                val currentSelection: Pair<Int?, Int> =
                    if (selectedOutroNumber != null && tailOutros.any { it.number == selectedOutroNumber }) {
                        selectedOutroNumber to 0
                    } else {
                        null to fadeOutMs
                    }
                val currentLabel = options.firstOrNull { it.value == currentSelection }?.label
                    ?: stringResource(R.string.flow_editor_outro_none_fade, fadeOutMs / 1000)
                SelectionDrawerRow(
                    label = stringResource(R.string.flow_editor_outro_and_fade_title),
                    selectedLabel = currentLabel,
                    sheetTitle = stringResource(R.string.flow_editor_outro_and_fade_title),
                    options = options,
                    selectedValue = currentSelection,
                    onSelect = { (outroNum, fadeMsChosen) ->
                        selectedOutroNumber = outroNum
                        if (outroNum == null) {
                            fadeOutMs = fadeMsChosen
                            outroToNextPre = false
                        } else {
                            outroToNextPre = tailOutros
                                .firstOrNull { it.number == outroNum }
                                ?.supportsNativeHandover
                                ?: true
                        }
                        activeBuiltInVariant = null
                    },
                )
            }

            // Bottom audition & save bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MkwSecondaryButton(
                        text = stringResource(R.string.flow_editor_audition_step),
                        onClick = { onAudition(draftPreset()) },
                        enabled = steps.isNotEmpty(),
                        leadingIcon = Icons.Default.PlayArrow,
                        size = MkwButtonSize.Hero,
                        modifier = Modifier.weight(1f),
                    )
                    MkwPrimaryButton(
                        text = stringResource(R.string.flow_editor_save),
                        onClick = { onSave(draftPreset()) },
                        enabled = steps.isNotEmpty() && presetName.isNotBlank(),
                        size = MkwButtonSize.Hero,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // Export Flow Config Bottom Sheet
    val jsonToExport = exportedJson
    if (jsonToExport != null) {
        MkwBottomSheet(
            onDismissRequest = { exportedJson = null },
            title = stringResource(R.string.flow_editor_export_title),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MkwInsetBox(
                    shape = MkwKitTokens.Shape.control,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = jsonToExport,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MkwKitTokens.Palette.ink,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MkwSecondaryButton(
                        text = stringResource(R.string.flow_editor_export_copy),
                        onClick = {
                            clipboard.setText(AnnotatedString(jsonToExport))
                            scope.launch { snackbarHostState.showSnackbar(msgCopied) }
                        },
                        leadingIcon = Icons.Default.ContentCopy,
                        size = MkwButtonSize.Hero,
                        modifier = Modifier.weight(1f),
                    )
                    MkwPrimaryButton(
                        text = stringResource(R.string.flow_editor_export_save_file),
                        onClick = {
                            val fileName = "${inventory.courseCode.lowercase()}_medley_flow.json"
                            saveJsonLauncher.launch(fileName)
                        },
                        leadingIcon = Icons.Default.FileDownload,
                        size = MkwButtonSize.Hero,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    val renderDialogVisible = showRenderDialog || isRenderingAudio || renderAudioError != null || renderAudioOutputPath != null
    if (renderDialogVisible) {
        FlowRenderAudioDialog(
            presetName = presetName.trim().ifBlank { defaultName },
            sampleRate = inventory.sampleRate,
            durationFormatted = EngineSnapshot.formatPosition(compiledStats, inventory.sampleRate),
            outputTrimEnabled = outputTrimEnabled,
            exportFolderDisplay = exportFolderDisplay?.takeIf { it.isNotBlank() } ?: AudioExportSettings.DEFAULT_RELATIVE_PATH,
            isRenderingAudio = isRenderingAudio,
            renderAudioProgress = renderAudioProgress,
            renderAudioStatus = renderAudioStatus,
            renderAudioError = renderAudioError,
            renderAudioOutputPath = renderAudioOutputPath,
            canStartRender = onRenderAudio != null && steps.isNotEmpty(),
            onStartRender = { onRenderAudio?.invoke(draftPreset()) },
            onCancelRender = { onCancelRenderAudio?.invoke() },
            onDismiss = {
                if (!isRenderingAudio) {
                    showRenderDialog = false
                    onDismissRenderDialog?.invoke()
                }
            },
        )
    }
}

@Composable
private fun SeamTransitionRow(
    eventLabel: String,
    onPreviewSeam: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MkwKitTokens.Palette.crimson,
        )
        Text(
            text = eventLabel.replace(" -> ", " \u2192 "),
            style = MaterialTheme.typography.labelSmall,
            color = MkwKitTokens.Palette.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MkwRoseButton(
            text = stringResource(R.string.flow_editor_preview_seam),
            onClick = onPreviewSeam,
            leadingIcon = Icons.Default.PlayArrow,
            size = MkwButtonSize.Compact,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowStepEventCard(
    courseCode: String,
    index: Int,
    step: FlowStep,
    part: CourseFlowRecipeBuilder.StagePartInfo?,
    prevRole: String?,
    incomingEventLabel: String,
    canRemove: Boolean,
    isWaveformExpanded: Boolean,
    waveformReady: Boolean,
    peaks: BwavWaveformExtractor.WaveformPeaks?,
    activeOutroNumber: Int?,
    outroTriggers: List<Long>,
    area2OutroHandoverFrame: Long? = null,
    rainbowArea2Outros: List<CourseFlowRecipeBuilder.OutroOption> = emptyList(),
    selectedRainbowArea2Outro: Int? = null,
    onSelectRainbowArea2Outro: (Int?) -> Unit = {},
    onToggleWaveform: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (FlowStep) -> Unit,
) {
    val isLockedBridge = part?.isBridgeLocked == true
    val isOutroHandoverLocked = area2OutroHandoverFrame != null
    val isOutroSingleLocked = !isLockedBridge && !isOutroHandoverLocked && activeOutroNumber != null && outroTriggers.size == 1
    val isOutroMultiConstrained = !isLockedBridge && !isOutroHandoverLocked && activeOutroNumber != null && outroTriggers.size > 1
    val isExitLocked = isLockedBridge || isOutroHandoverLocked || isOutroSingleLocked
    val activeExitFrame = when {
        isLockedBridge -> part?.bridgeHandoverFrame ?: part?.frames ?: 1L
        isOutroHandoverLocked -> area2OutroHandoverFrame!!
        isOutroSingleLocked -> outroTriggers.single()
        isOutroMultiConstrained -> step.exitFrame?.takeIf { it in outroTriggers } ?: outroTriggers.last()
        else -> step.exitFrame ?: part?.defaultExitFrame ?: part?.frames ?: 1L
    }

    MkwCard(
        tone = MkwSurfaceTone.Porcelain,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${index + 1}. ${roleDisplayLabel(step.roleKey, courseCode)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MkwKitTokens.Palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isExitLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MkwKitTokens.Palette.crimson,
                        )
                    }
                }
                if (part != null) {
                    val exitTime = EngineSnapshot.formatPosition(activeExitFrame, part.sampleRate)
                    val totalTime = EngineSnapshot.formatPosition(part.frames, part.sampleRate)
                    Text(
                        text = when {
                            isLockedBridge -> stringResource(R.string.flow_editor_bridge_handover_fmt, exitTime, totalTime)
                            isOutroHandoverLocked -> stringResource(R.string.flow_editor_area2_outro_handover, exitTime, totalTime)
                            isOutroSingleLocked && activeOutroNumber != null ->
                                stringResource(R.string.flow_editor_outro_exit_fmt, activeOutroNumber, exitTime, totalTime)
                            isOutroMultiConstrained && activeOutroNumber != null ->
                                stringResource(R.string.flow_editor_outro_fixed_cue_fmt, activeOutroNumber, exitTime, totalTime)
                            else -> "$exitTime / $totalTime"
                        },
                        style = MkwKitTokens.tabular(MaterialTheme.typography.labelSmall),
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }
            if (canRemove) {
                MkwIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.flow_editor_remove),
                    onClick = onRemove,
                    visualSize = 36.dp,
                    iconSize = 18.dp,
                )
            }
        }

        if (isLockedBridge) {
            MkwCard(
                tone = MkwSurfaceTone.RoseTint,
                selected = true,
                shape = MkwKitTokens.Shape.thumb,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MkwKitTokens.Palette.crimsonDeep,
                    )
                    Text(
                        text = stringResource(R.string.flow_editor_bridge_locked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MkwKitTokens.Palette.crimsonDeep,
                    )
                }
            }
        } else {
            if (isOutroSingleLocked && activeOutroNumber != null) {
                MkwCard(
                    tone = MkwSurfaceTone.RoseTint,
                    selected = true,
                    shape = MkwKitTokens.Shape.thumb,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MkwKitTokens.Palette.crimsonDeep,
                        )
                        Text(
                            text = stringResource(R.string.flow_editor_outro_locked, activeOutroNumber),
                            style = MaterialTheme.typography.labelSmall,
                            color = MkwKitTokens.Palette.crimsonDeep,
                        )
                    }
                }
            } else if (isOutroMultiConstrained && activeOutroNumber != null) {
                MkwInsetBox(
                    shape = MkwKitTokens.Shape.thumb,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.flow_editor_outro_multi_trigger_hint, activeOutroNumber, outroTriggers.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }

            if (rainbowArea2Outros.isNotEmpty()) {
                val noneLabel = stringResource(R.string.flow_editor_area2_outro_none)
                val area2Options = buildList<DrawerOption<Int?>> {
                    add(DrawerOption(value = null, label = noneLabel))
                    rainbowArea2Outros.forEach { outro ->
                        add(
                            DrawerOption(
                                value = outro.number,
                                label = stringResource(R.string.flow_editor_outro_item, outro.number),
                                subtitle = if (outro.supportsNativeHandover) {
                                    stringResource(R.string.flow_editor_outro_native_ready)
                                } else null,
                            ),
                        )
                    }
                }
                val chosenLabel = area2Options.firstOrNull { it.value == selectedRainbowArea2Outro }?.label ?: noneLabel
                SelectionDrawerRow(
                    label = stringResource(R.string.flow_editor_area2_outro_title),
                    selectedLabel = chosenLabel,
                    sheetTitle = stringResource(R.string.flow_editor_area2_outro_title),
                    options = area2Options,
                    selectedValue = selectedRainbowArea2Outro,
                    onSelect = onSelectRainbowArea2Outro,
                )
            }

            if (part != null && part.supportsLoop) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.flow_editor_loop_count, step.loopCount),
                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                        color = MkwKitTokens.Palette.ink,
                        modifier = Modifier.weight(1f),
                    )
                    MkwIconButton(
                        icon = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.flow_editor_loop_decrease),
                        onClick = { onUpdate(step.copy(loopCount = (step.loopCount - 1).coerceAtLeast(0))) },
                        enabled = step.loopCount > 0,
                        tone = MkwIconButtonTone.Bordered,
                        visualSize = 36.dp,
                        iconSize = 18.dp,
                    )
                    MkwIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.flow_editor_loop_increase),
                        onClick = { onUpdate(step.copy(loopCount = (step.loopCount + 1).coerceAtMost(8))) },
                        enabled = step.loopCount < 8,
                        tone = MkwIconButtonTone.Bordered,
                        visualSize = 36.dp,
                        iconSize = 18.dp,
                    )
                }
            }
        }

        if (part != null) {
            MkwCard(
                onClick = onToggleWaveform,
                selected = isWaveformExpanded,
                tone = MkwSurfaceTone.Recessed,
                shape = MkwKitTokens.Shape.thumb,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MkwKitTokens.Palette.crimson,
                    )
                    Text(
                        text = stringResource(
                            if (isWaveformExpanded) R.string.flow_editor_waveform_hide else R.string.flow_editor_waveform_show,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MkwKitTokens.Palette.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isWaveformExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }

            AnimatedVisibility(
                visible = isWaveformExpanded,
                enter = fadeIn(tween(160, easing = AppMotion.easeOut)) + expandVertically(tween(180, easing = AppMotion.easeOut)),
                exit = fadeOut(tween(120, easing = AppMotion.easeOut)) + shrinkVertically(tween(160, easing = AppMotion.easeOut)),
            ) {
                val waveformCandidates = if (isOutroMultiConstrained) {
                    outroTriggers.map {
                        CourseFlowRecipeBuilder.ExitCandidate(
                            it,
                            EngineSnapshot.formatPosition(it, part.sampleRate),
                        )
                    }
                } else {
                    part.exitCandidates
                }
                val waveformLockLabel = when {
                    isLockedBridge -> stringResource(R.string.flow_editor_lock_bridge_handover)
                    isOutroHandoverLocked -> stringResource(R.string.flow_editor_lock_outro_handover)
                    isOutroSingleLocked && activeOutroNumber != null ->
                        stringResource(R.string.flow_editor_outro_item, activeOutroNumber)
                    else -> null
                }
                val waveformLockedHint = when {
                    isLockedBridge -> stringResource(R.string.flow_editor_bridge_locked)
                    isOutroHandoverLocked -> stringResource(R.string.flow_editor_outro_handover_locked)
                    isOutroSingleLocked && activeOutroNumber != null ->
                        stringResource(R.string.flow_editor_outro_locked, activeOutroNumber)
                    else -> null
                }
                val stepGridBeats = if (!isOutroMultiConstrained && part.isGridConstrained) {
                    when {
                        courseCode == "DonkeyKong" && step.roleKey == "Main" -> 4
                        courseCode == "DonkeyKong" && step.roleKey in setOf("Region2", "Region3", "Region4") -> 8
                        else -> com.mylittleblover.mkwplayer.interactive.MinfCatalog.preTransitionGridBeats(courseCode, step.roleKey)
                    }
                } else {
                    null
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoomableWaveformEditor(
                        peaks = if (waveformReady) peaks else null,
                        frames = part.frames,
                        sampleRate = part.sampleRate,
                        beats = part.beats,
                        exitCandidates = waveformCandidates,
                        selectedExitFrame = activeExitFrame,
                        bridgeLocked = isExitLocked,
                        loopStart = part.loopStart,
                        loopEnd = part.loopEnd,
                        constrainToCandidatesOnly = isOutroMultiConstrained || part.isGridConstrained,
                        lockLabel = waveformLockLabel,
                        gridBeats = stepGridBeats,
                        lockedHint = waveformLockedHint,
                        onSelectExitFrame = { chosenFrame ->
                            if (!isExitLocked) {
                                onUpdate(step.copy(exitFrame = chosenFrame))
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun canSafelyRemoveMiddle(
    steps: List<FlowStep>,
    index: Int,
    inventory: CourseFlowRecipeBuilder.CourseInventory,
): Boolean {
    if (steps.size <= 1) return true
    if (index == steps.lastIndex) return true
    val prefix = steps.subList(0, index)
    val nextRole = steps[index + 1].roleKey
    return nextRole in inventory.allowedNextRoles(prefix)
}

@Composable
private fun roleDisplayLabel(roleKey: String, courseCode: String? = null): String {
    val course = normalizeCourseCode(courseCode)
    return when {
        roleKey == "Pre_Short" -> stringResource(R.string.stage_short_prelude)
        roleKey == "Pre" -> stringResource(R.string.flow_editor_role_pre)
        roleKey == "Bridge" -> stringResource(R.string.stage_bridge)
        roleKey == "Main" && course in setOf("SnowMountain", "Cinema", "DonkeyKong") -> stageText("Main", course)
        roleKey == "Main" -> stringResource(R.string.flow_editor_role_main)
        roleKey == "Main_Speaker" -> stringResource(R.string.flow_editor_role_main_speaker)
        roleKey == "Fast" && course in setOf("SnowMountain", "Cinema") -> stageText("Fast", course)
        roleKey == "Fast" -> stringResource(R.string.stage_final_lap)
        roleKey == "Fast_Speaker" -> stringResource(R.string.flow_editor_role_fast_speaker)
        roleKey == "Pattern2" -> stageText("Pattern2", course)
        roleKey == "Fast_Pattern2" -> stageText("Fast_Pattern2", course)
        roleKey == "Area1_Pre" || roleKey == "Area1_Bridge" || roleKey == "Area1_Inst" ||
            roleKey == "Area1" || roleKey == "Area2" || roleKey.startsWith("Area2_Outro") ||
            roleKey == "Area3_Pre" || roleKey == "Area3" || roleKey == "Final_Pre" || roleKey == "Final" ->
            stageText(roleKey, "Rainbow")
        roleKey == "Harmonica" -> stringResource(R.string.stage_title_extended)
        roleKey == "BrassCut" -> stringResource(R.string.stage_title_brasscut)
        roleKey.startsWith("Region") -> stageText(roleKey, course ?: "DonkeyKong")
        else -> roleKey.replace('_', ' ')
    }
}

@Composable
private fun localizedTransitionEventLabel(
    inventory: CourseFlowRecipeBuilder.CourseInventory,
    prevRole: String?,
    nextRole: String,
): String {
    val courseCode = inventory.courseCode
    val nextLabel = roleDisplayLabel(nextRole, courseCode)
    return when {
        prevRole == null -> stringResource(R.string.flow_editor_event_entry, nextLabel)
        prevRole == "Pre_Short" && nextRole == "Pre" ->
            stringResource(R.string.flow_editor_event_pre_short_aligned)
        prevRole == "Harmonica" && nextRole == "Main" ->
            stringResource(R.string.flow_editor_event_exit_to_title_normal)
        nextRole in setOf("Bridge", "Area1_Bridge") ->
            stringResource(R.string.flow_editor_event_phase_overlap, nextLabel)
        prevRole == "Area2" && nextRole.startsWith("Area2_Outro") ->
            stringResource(R.string.flow_editor_event_full_to_outro, nextRole.removePrefix("Area2_Outro"), nextLabel)
        prevRole.startsWith("Area2_Outro") && nextRole == "Area3_Pre" ->
            stringResource(R.string.flow_editor_event_outro_to_next_pre, nextLabel)
        prevRole in setOf("Bridge", "Area1_Bridge") ->
            stringResource(R.string.flow_editor_event_bridge_to_full, nextLabel)
        courseCode in setOf("Cinema", "SnowMountain") &&
            prevRole in setOf("Main", "Pattern2", "Fast", "Fast_Pattern2") &&
            nextRole in setOf("Main", "Pattern2", "Fast", "Fast_Pattern2") &&
            (prevRole in setOf("Main", "Pattern2")) == (nextRole in setOf("Main", "Pattern2")) -> {
            val exitingSnowP2 = courseCode == "SnowMountain" && prevRole in setOf("Pattern2", "Fast_Pattern2")
            val fadeText = stringResource(R.string.settings_switch_fade_chip_sec_fmt, if (exitingSnowP2) "2.0" else "0.5")
            stringResource(R.string.flow_editor_event_parallel_sync, fadeText, nextLabel)
        }
        courseCode == "Rainbow" && prevRole in setOf("Area1", "Area1_Inst") && nextRole == "Area2" ->
            stringResource(R.string.flow_editor_event_split_stem_fade, nextLabel)
        courseCode == "Rainbow" && prevRole == "Area3" && nextRole == "Final_Pre" ->
            stringResource(R.string.flow_editor_event_rainbow_area3_crossfade, nextLabel)
        courseCode == "KoopaTroopaBeach" &&
            ((prevRole == "Main" && nextRole == "Main_Speaker") ||
                (prevRole == "Main_Speaker" && nextRole == "Main") ||
                (prevRole == "Fast" && nextRole == "Fast_Speaker") ||
                (prevRole == "Fast_Speaker" && nextRole == "Fast")) ->
            stringResource(R.string.flow_editor_event_speaker_overlay, nextLabel)
        courseCode == "Rainbow" &&
            ((prevRole == "Area1_Inst" && nextRole == "Area1") ||
                (prevRole == "Area1" && nextRole == "Area1_Inst")) ->
            stringResource(R.string.flow_editor_event_melody_overlay, nextLabel)
        courseCode == "Rainbow" && nextRole == "Final" &&
            prevRole in setOf("Area1_Pre", "Area1_Inst", "Area1", "Area2", "Area3_Pre", "Area3", "Final_Pre") &&
            inventory.hasFanfareAsset ->
            stringResource(R.string.flow_editor_event_rainbow_fanfare, nextLabel)
        nextRole in setOf("Fast", "Fast_Pattern2", "Fast_Speaker") &&
            prevRole !in setOf("Bridge", "Fast", "Fast_Pattern2", "Fast_Speaker") &&
            inventory.hasFanfareAsset && courseCode != "Rainbow" ->
            stringResource(R.string.flow_editor_event_final_lap_ff, nextLabel)
        nextRole in setOf("Fast", "Fast_Pattern2", "Fast_Speaker") ->
            stringResource(R.string.flow_editor_event_direct_fade, nextLabel)
        else -> stringResource(R.string.flow_editor_event_next_bar_sync, nextLabel)
    }
}

/**
 * Decoded editable flow structure extracted from a built-in [CourseMedleyCatalog.Medley].
 */
internal data class FlowDraft(
    val steps: List<FlowStep>,
    val endingOutro: Int?,
    val fadeOutMs: Int,
    val outroToNextPre: Boolean,
)

private val OUTRO_STEM_NUMBER_REGEX = Regex("Outro(\\d{2})$", RegexOption.IGNORE_CASE)

private data class DecodedClipSlice(
    val start: Long,
    val sourceOffset: Long,
    var effectiveLength: Long,
    val fadeOut: Long,
    val volumePoints: List<MusicGraphBuilder.EnvelopePoint>,
)

private data class DecodedTaggedClip(
    val roleKey: String?,
    val outroOnlyNumber: Int?,
    val rainbowOutroNumber: Int?,
    val slice: DecodedClipSlice,
)

private data class DecodedRoleGroup(
    val roleKey: String,
    val slices: MutableList<DecodedClipSlice>,
)

private sealed interface StemClassification {
    data object Skip : StemClassification
    data class Secondary(val stemLower: String) : StemClassification
    data class Part(val roleKey: String, val rainbowOutroNumber: Int? = null) : StemClassification
    data class OutroOnly(val outroNumber: Int) : StemClassification
}

/**
 * Decodes a built-in [CourseMedleyCatalog.Medley] back into an editable [FlowDraft] (steps,
 * loop counts, exit frames, ending outro, native handover flag, and trailing fade-out duration)
 * strictly aligned with [CourseFlowRecipeBuilder] compilation rules.
 */
internal fun decodeMedleyToFlowDraft(
    medley: CourseMedleyCatalog.Medley,
    inventory: CourseFlowRecipeBuilder.CourseInventory,
): FlowDraft {
    val logicalCourse = inventory.courseCode.removeSuffix("_00")
    val sampleRate = inventory.sampleRate.coerceAtLeast(1)

    val primaryRoleByStem = inventory.parts
        .filter { it.secondaryStem == null }
        .associate { it.stem.lowercase(Locale.ROOT) to it.roleKey }
    val pairedRoleByStems = inventory.parts
        .filter { it.secondaryStem != null }
        .associate { (it.stem.lowercase(Locale.ROOT) to it.secondaryStem!!.lowercase(Locale.ROOT)) to it.roleKey }
    val secondaryStems = inventory.parts
        .mapNotNull { it.secondaryStem?.lowercase(Locale.ROOT) }
        .toMutableSet()
        .apply {
            add("se_stage_koopatroopabeach_speaker_normal")
            add("se_stage_koopatroopabeach_speaker_fast")
            add("bgm_rainbow_area1_melody")
        }
    val outroByStem = inventory.outros
        .associate { it.stem.lowercase(Locale.ROOT) to it.number }

    fun classifyStem(stem: String): StemClassification {
        val lower = stem.lowercase(Locale.ROOT)
        if (lower == "bgm_seq_finallapff" || lower == "bgm_rainbow_finallapfanfare") {
            return StemClassification.Skip
        }
        if (lower in secondaryStems) {
            return StemClassification.Secondary(lower)
        }
        primaryRoleByStem[lower]?.let { role ->
            val rainbowOutroNum = if (logicalCourse == "Rainbow" && role.startsWith("Area2_Outro")) {
                role.removePrefix("Area2_Outro").toIntOrNull()
            } else {
                null
            }
            return StemClassification.Part(role, rainbowOutroNum)
        }
        outroByStem[lower]?.let { num ->
            return StemClassification.OutroOnly(num)
        }
        // Fallback resolution from canonical stem naming
        if (logicalCourse == "Rainbow" && stem.startsWith("BGM_Rainbow_", ignoreCase = true)) {
            val suf = stem.substring("BGM_Rainbow_".length)
            val rainbowOutro = if (suf.startsWith("Area2_Outro", ignoreCase = true)) {
                suf.substring("Area2_Outro".length).toIntOrNull()
            } else null
            return StemClassification.Part(suf, rainbowOutro)
        }
        val stageBase = "BGM_Stage_$logicalCourse"
        if (stem.equals(stageBase, ignoreCase = true)) {
            return StemClassification.Part("Main")
        }
        val stagePrefix = "${stageBase}_"
        if (stem.startsWith(stagePrefix, ignoreCase = true)) {
            val suf = stem.substring(stagePrefix.length)
            OUTRO_STEM_NUMBER_REGEX.matchEntire(suf)?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
                return StemClassification.OutroOnly(num)
            }
            val mapped = when {
                suf.equals("Pre_Short", ignoreCase = true) -> "Pre_Short"
                suf.equals("Pre", ignoreCase = true) -> "Pre"
                suf.equals("Bridge", ignoreCase = true) -> "Bridge"
                suf.equals("Fast", ignoreCase = true) || suf.equals("FL", ignoreCase = true) -> "Fast"
                suf.equals("Fast_Pattern2", ignoreCase = true) -> "Fast_Pattern2"
                suf.equals("Pattern2", ignoreCase = true) -> if (logicalCourse == "DonkeyKong") "Region2" else "Pattern2"
                logicalCourse == "DonkeyKong" && suf.startsWith("Pattern", ignoreCase = true) ->
                    "Region${suf.substring("Pattern".length)}"
                else -> suf
            }
            return StemClassification.Part(mapped)
        }
        OUTRO_STEM_NUMBER_REGEX.find(stem)?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
            return StemClassification.OutroOnly(num)
        }
        return StemClassification.Skip
    }

    fun resolvePairedRole(primaryStemLower: String, baseRole: String, secondaryStemLower: String): String? {
        pairedRoleByStems[primaryStemLower to secondaryStemLower]?.let { return it }
        return when {
            baseRole == "Main" && secondaryStemLower == "se_stage_koopatroopabeach_speaker_normal" -> "Main_Speaker"
            baseRole == "Fast" && secondaryStemLower == "se_stage_koopatroopabeach_speaker_fast" -> "Fast_Speaker"
            baseRole == "Area1_Inst" && secondaryStemLower == "bgm_rainbow_area1_melody" -> "Area1"
            else -> null
        }
    }

    // Index secondary overlay clips by timeline start so paired primary+secondary tracks merge cleanly
    val secondaryIndicesByStart = HashMap<Long, MutableList<Pair<Int, String>>>()
    medley.clips.forEachIndexed { idx, clip ->
        val cls = classifyStem(clip.stem)
        if (cls is StemClassification.Secondary) {
            secondaryIndicesByStart.getOrPut(clip.start) { mutableListOf() }.add(idx to cls.stemLower)
        }
    }

    val consumedSecondary = HashSet<Int>()
    val tagged = mutableListOf<DecodedTaggedClip>()
    medley.clips.forEachIndexed { idx, clip ->
        if (idx in consumedSecondary) return@forEachIndexed
        when (val cls = classifyStem(clip.stem)) {
            StemClassification.Skip, is StemClassification.Secondary -> Unit
            is StemClassification.OutroOnly -> {
                tagged += DecodedTaggedClip(
                    roleKey = null,
                    outroOnlyNumber = cls.outroNumber,
                    rainbowOutroNumber = null,
                    slice = DecodedClipSlice(
                        start = clip.start,
                        sourceOffset = clip.sourceOffset,
                        effectiveLength = clip.sourceLength,
                        fadeOut = clip.fadeOut,
                        volumePoints = clip.volumePoints,
                    ),
                )
            }
            is StemClassification.Part -> {
                var resolvedRole = cls.roleKey
                val primaryLower = clip.stem.lowercase(Locale.ROOT)
                val matchingSecondaries = secondaryIndicesByStart[clip.start]
                if (matchingSecondaries != null) {
                    for ((secIdx, secStemLower) in matchingSecondaries) {
                        if (secIdx in consumedSecondary) continue
                        val paired = resolvePairedRole(primaryLower, resolvedRole, secStemLower)
                        if (paired != null) {
                            resolvedRole = paired
                            consumedSecondary += secIdx
                            break
                        }
                    }
                }
                tagged += DecodedTaggedClip(
                    roleKey = resolvedRole,
                    outroOnlyNumber = null,
                    rainbowOutroNumber = cls.rainbowOutroNumber,
                    slice = DecodedClipSlice(
                        start = clip.start,
                        sourceOffset = clip.sourceOffset,
                        effectiveLength = clip.sourceLength,
                        fadeOut = clip.fadeOut,
                        volumePoints = clip.volumePoints,
                    ),
                )
            }
        }
    }

    // Separate a terminal Outro clip from the main step list (middle Rainbow Area2_Outro stays in steps)
    var rawEndingOutro: Int? = null
    if (tagged.isNotEmpty()) {
        val last = tagged.last()
        when {
            last.outroOnlyNumber != null -> {
                rawEndingOutro = last.outroOnlyNumber
                tagged.removeAt(tagged.lastIndex)
            }
            last.rainbowOutroNumber != null &&
                tagged.size >= 2 &&
                tagged[tagged.lastIndex - 1].roleKey == "Area2" -> {
                rawEndingOutro = last.rainbowOutroNumber
                tagged.removeAt(tagged.lastIndex)
            }
        }
    }

    // Group consecutive clips belonging to the same role
    val groups = mutableListOf<DecodedRoleGroup>()
    tagged.forEach { entry ->
        val role = entry.roleKey ?: return@forEach
        val prev = groups.lastOrNull()
        if (prev != null && prev.roleKey == role) {
            prev.slices += entry.slice
        } else {
            groups += DecodedRoleGroup(role, mutableListOf(entry.slice))
        }
    }

    // Extract trailing post-exit fade-out frames when the medley does not end with an Outro
    var trailingFadeFrames = 0L
    if (rawEndingOutro == null && groups.isNotEmpty()) {
        val lastSlices = groups.last().slices
        if (lastSlices.any { it.volumePoints.isNotEmpty() }) {
            while (lastSlices.isNotEmpty() && lastSlices.last().volumePoints.isNotEmpty()) {
                val slice = lastSlices.last()
                var fadeStartLocal: Long? = null
                for (pt in slice.volumePoints) {
                    if (pt.value >= -1e-9) {
                        fadeStartLocal = pt.frame
                    } else {
                        break
                    }
                }
                if (fadeStartLocal == null || fadeStartLocal <= 0L) {
                    trailingFadeFrames += slice.effectiveLength
                    if (lastSlices.size > 1) {
                        lastSlices.removeAt(lastSlices.lastIndex)
                    } else {
                        slice.effectiveLength = 1L
                        break
                    }
                } else {
                    trailingFadeFrames += (slice.effectiveLength - fadeStartLocal).coerceAtLeast(0L)
                    slice.effectiveLength = fadeStartLocal.coerceAtLeast(1L)
                    break
                }
            }
        } else {
            while (lastSlices.size > 1 &&
                lastSlices.last().fadeOut > 0L &&
                lastSlices.last().fadeOut == lastSlices.last().effectiveLength
            ) {
                trailingFadeFrames += lastSlices.last().fadeOut
                lastSlices.removeAt(lastSlices.lastIndex)
            }
            val tail = lastSlices.lastOrNull()
            if (tail != null && tail.fadeOut > 0L) {
                trailingFadeFrames += tail.fadeOut
                if (tail.fadeOut < tail.effectiveLength) {
                    tail.effectiveLength = (tail.effectiveLength - tail.fadeOut).coerceAtLeast(1L)
                }
            }
        }
    }

    // Build FlowSteps while stripping mid-medley post-exit crossfade/handover extensions
    val steps = buildList {
        groups.forEachIndexed { gIdx, group ->
            val slices = group.slices
            if (slices.isEmpty()) return@forEachIndexed
            val isLast = gIdx == groups.lastIndex
            if (!isLast) {
                while (slices.size > 1 &&
                    slices.last().fadeOut > 0L &&
                    slices.last().fadeOut == slices.last().effectiveLength
                ) {
                    slices.removeAt(slices.lastIndex)
                }
                val lastSlice = slices.last()
                val nextGroup = groups[gIdx + 1]
                if (logicalCourse == "Rainbow" && group.roleKey == "Area3" && nextGroup.roleKey == "Final_Pre") {
                    val nextStart = nextGroup.slices.first().start
                    while (slices.size > 1 && slices.last().start >= nextStart) {
                        slices.removeAt(slices.lastIndex)
                    }
                    val curLast = slices.last()
                    val trimmed = nextStart - curLast.start
                    if (trimmed > 0L) {
                        curLast.effectiveLength = trimmed
                    }
                } else if (lastSlice.fadeOut in 1L until lastSlice.effectiveLength) {
                    lastSlice.effectiveLength -= lastSlice.fadeOut
                }
            }
            val part = inventory.part(group.roleKey)
            val isBridgeLocked = part?.isBridgeLocked == true || group.roleKey in setOf("Bridge", "Area1_Bridge")
            if (isBridgeLocked) {
                add(FlowStep(roleKey = group.roleKey, loopCount = 0, exitFrame = null))
            } else {
                val supportsLoop = part?.supportsLoop ?: true
                val loopCount = if (supportsLoop) (slices.size - 1).coerceIn(0, 8) else 0
                val lastSlice = slices.last()
                val rawExit = (lastSlice.sourceOffset + lastSlice.effectiveLength).coerceAtLeast(1L)
                val clampedExit = if (part != null) rawExit.coerceIn(1L, part.frames) else rawExit
                add(FlowStep(roleKey = group.roleKey, loopCount = loopCount, exitFrame = clampedExit))
            }
        }
    }.ifEmpty { inventory.defaultEditorSteps() }

    val effectiveOutro = rawEndingOutro?.takeIf { num ->
        val hostOk = steps.isNotEmpty() && CourseFlowRecipeBuilder.isOutroHostRole(steps.last().roleKey)
        if (!hostOk) false
        else if (inventory.outros.isEmpty()) true
        else inventory.outros.any { it.number == num }
    }
    val outroToNextPre = effectiveOutro != null &&
        (inventory.outros.firstOrNull { it.number == effectiveOutro }?.supportsNativeHandover ?: true)
    val fadeOutMs = if (effectiveOutro != null) {
        0
    } else {
        ((trailingFadeFrames * 1000L + sampleRate / 2L) / sampleRate).toInt().coerceIn(0, 10_000)
    }

    return FlowDraft(
        steps = steps,
        endingOutro = effectiveOutro,
        fadeOutMs = fadeOutMs,
        outroToNextPre = outroToNextPre,
    )
}

private enum class FlowRenderDialogPhase {
    READY,
    RENDERING,
    SUCCESS,
    ERROR,
}

@Composable
private fun FlowRenderAudioDialog(
    presetName: String,
    sampleRate: Int,
    durationFormatted: String,
    outputTrimEnabled: Boolean,
    exportFolderDisplay: String,
    isRenderingAudio: Boolean,
    renderAudioProgress: Float,
    renderAudioStatus: String?,
    renderAudioError: String?,
    renderAudioOutputPath: String?,
    canStartRender: Boolean,
    onStartRender: () -> Unit,
    onCancelRender: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val clampedProgress = renderAudioProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val progressPct = (clampedProgress * 100f).roundToInt()
    val phase = when {
        isRenderingAudio -> FlowRenderDialogPhase.RENDERING
        !renderAudioOutputPath.isNullOrBlank() -> FlowRenderDialogPhase.SUCCESS
        !renderAudioError.isNullOrBlank() -> FlowRenderDialogPhase.ERROR
        else -> FlowRenderDialogPhase.READY
    }

    MkwDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.flow_render_dialog_title),
        dismissButton = when (phase) {
            FlowRenderDialogPhase.RENDERING, FlowRenderDialogPhase.SUCCESS -> null
            FlowRenderDialogPhase.ERROR -> {
                {
                    MkwGhostButton(
                        text = stringResource(R.string.flow_render_close),
                        onClick = onDismiss,
                    )
                }
            }
            FlowRenderDialogPhase.READY -> {
                {
                    MkwGhostButton(
                        text = stringResource(R.string.marker_action_cancel),
                        onClick = onDismiss,
                    )
                }
            }
        },
        confirmButton = {
            when (phase) {
                FlowRenderDialogPhase.RENDERING -> {
                    MkwDangerButton(
                        text = stringResource(R.string.flow_render_cancel),
                        onClick = onCancelRender,
                        size = MkwButtonSize.Compact,
                    )
                }
                FlowRenderDialogPhase.SUCCESS -> {
                    MkwPrimaryButton(
                        text = stringResource(R.string.flow_render_done),
                        onClick = onDismiss,
                        size = MkwButtonSize.Compact,
                    )
                }
                FlowRenderDialogPhase.ERROR -> {
                    MkwPrimaryButton(
                        text = stringResource(R.string.flow_render_retry),
                        onClick = onStartRender,
                        enabled = canStartRender,
                        size = MkwButtonSize.Compact,
                    )
                }
                FlowRenderDialogPhase.READY -> {
                    MkwPrimaryButton(
                        text = stringResource(R.string.flow_render_start),
                        onClick = onStartRender,
                        enabled = canStartRender,
                        leadingIcon = Icons.Default.LibraryMusic,
                        size = MkwButtonSize.Compact,
                    )
                }
            }
        },
    ) {
        // Preset & audio format summary stays anchored across all phases
        MkwInsetBox(
            shape = MkwKitTokens.Shape.control,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = presetName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MkwKitTokens.Palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.flow_render_format_summary_fmt,
                        (sampleRate / 1000).coerceAtLeast(1),
                        durationFormatted,
                    ),
                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }
        }

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(100))
                } else {
                    fadeIn(tween(160, easing = AppMotion.easeOut)) togetherWith
                        fadeOut(tween(120, easing = AppMotion.easeOut))
                }
            },
            label = "flow-render-phase",
            modifier = Modifier.fillMaxWidth(),
        ) { currentPhase ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (currentPhase) {
                    FlowRenderDialogPhase.READY -> {
                        FlowRenderConfigCard(
                            outputTrimEnabled = outputTrimEnabled,
                            exportFolderDisplay = exportFolderDisplay,
                        )
                    }

                    FlowRenderDialogPhase.RENDERING -> {
                        FlowRenderConfigCard(
                            outputTrimEnabled = outputTrimEnabled,
                            exportFolderDisplay = exportFolderDisplay,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = renderAudioStatus?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.flow_render_in_progress),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "$progressPct%",
                                    style = MkwKitTokens.tabular(MaterialTheme.typography.labelLarge),
                                    fontWeight = FontWeight.Bold,
                                    color = MkwKitTokens.Palette.crimson,
                                )
                            }
                            SmoothProgressIndicator(
                                progress = clampedProgress,
                                modifier = Modifier.fillMaxWidth(),
                                color = MkwKitTokens.Palette.crimson,
                                trackColor = MkwKitTokens.Palette.trackStrong,
                                barHeight = 8.dp,
                            )
                        }
                    }

                    FlowRenderDialogPhase.SUCCESS -> {
                        val checkScale = remember(renderAudioOutputPath, reducedMotion) {
                            Animatable(if (reducedMotion) 1f else 0.92f)
                        }
                        LaunchedEffect(renderAudioOutputPath, reducedMotion) {
                            if (reducedMotion) {
                                checkScale.snapTo(1f)
                            } else {
                                checkScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = AppMotion.snapSpring,
                                )
                            }
                        }
                        MkwCard(
                            tone = MkwSurfaceTone.Porcelain,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            scaleX = checkScale.value
                                            scaleY = checkScale.value
                                        },
                                    tint = Color(0xFF0B7D55L),
                                )
                                Text(
                                    text = stringResource(R.string.flow_render_success_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0B7D55L),
                                )
                            }
                            if (!renderAudioOutputPath.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.flow_render_output_path_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MkwKitTokens.Palette.inkSecondary,
                                )
                                MkwInsetBox(
                                    shape = MkwKitTokens.Shape.thumb,
                                    contentPadding = PaddingValues(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = renderAudioOutputPath,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MkwKitTokens.Palette.ink,
                                    )
                                }
                            }
                        }
                    }

                    FlowRenderDialogPhase.ERROR -> {
                        MkwCard(
                            tone = MkwSurfaceTone.Porcelain,
                            error = true,
                            shape = MkwKitTokens.Shape.control,
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MkwKitTokens.Palette.danger,
                                )
                                Text(
                                    text = stringResource(R.string.flow_render_error_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MkwKitTokens.Palette.danger,
                                )
                            }
                            if (!renderAudioError.isNullOrBlank()) {
                                Text(
                                    text = renderAudioError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.danger,
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
private fun FlowRenderConfigCard(
    outputTrimEnabled: Boolean,
    exportFolderDisplay: String,
) {
    MkwCard(
        tone = MkwSurfaceTone.Porcelain,
        shape = MkwKitTokens.Shape.control,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (outputTrimEnabled) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inkSecondary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.flow_render_gain_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
                Text(
                    text = stringResource(
                        if (outputTrimEnabled) R.string.flow_render_gain_enabled else R.string.flow_render_gain_flat,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (outputTrimEnabled) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.ink,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MkwKitTokens.Palette.inkSecondary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.flow_render_folder_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
                Text(
                    text = exportFolderDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MkwKitTokens.Palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
