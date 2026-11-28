package com.mylittleblover.mkwplayer.ui.routes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.jukebox.FreeRoamJukeboxCatalog
import com.mylittleblover.mkwplayer.routes.OfficialRouteKind
import com.mylittleblover.mkwplayer.routes.RoadJukeboxCatalog
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.art.PackArtwork
import com.mylittleblover.mkwplayer.ui.home.JukeboxCatalog
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwBottomSheet
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwDivider
import com.mylittleblover.mkwplayer.ui.kit.MkwFilterPill
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButton
import com.mylittleblover.mkwplayer.ui.kit.MkwIconButtonTone
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwMotion
import com.mylittleblover.mkwplayer.ui.kit.MkwOptionRow
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSegmentedBar
import com.mylittleblover.mkwplayer.ui.kit.MkwSlider
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.kit.MkwSwitch
import com.mylittleblover.mkwplayer.ui.kit.MkwTextField
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

internal fun RoadJukeboxCatalog.EdgeMusicResolution.hasKnownPool(): Boolean =
    this is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete ||
        this is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolIncomplete

internal fun RoadJukeboxCatalog.EdgeMusicResolution.isMissingOrIncomplete(): Boolean =
    this is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolIncomplete ||
        this is RoadJukeboxCatalog.EdgeMusicResolution.SubstituteMissing

@Composable
internal fun localizedRouteBlockingReason(
    report: RoadJukeboxCatalog.RouteValidationReport?,
    fallbackResId: Int = R.string.route_blocked_incomplete_pool,
    stageName: (String) -> String = { it },
): String {
    val cause = report?.blockingCause
    return when (cause) {
        RoadJukeboxCatalog.BlockingCause.TooFewStops ->
            stringResource(R.string.route_block_min_stops)
        RoadJukeboxCatalog.BlockingCause.NoStops ->
            stringResource(R.string.route_block_no_stops)
        is RoadJukeboxCatalog.BlockingCause.InvalidEdges ->
            stringResource(
                R.string.route_block_invalid_edges,
                cause.edges.joinToString(", ") { "${stageName(it.from)} → ${stageName(it.to)}" },
            )
        is RoadJukeboxCatalog.BlockingCause.IncompletePools ->
            stringResource(
                R.string.route_block_incomplete_pool,
                cause.edges.joinToString(", ") { "${stageName(it.from)} → ${stageName(it.to)}" },
            )
        is RoadJukeboxCatalog.BlockingCause.MissingSubstitutes ->
            stringResource(
                R.string.route_block_missing_substitute,
                cause.edges.joinToString(", ") { "${stageName(it.from)} → ${stageName(it.to)}" },
            )
        is RoadJukeboxCatalog.BlockingCause.UnplayableStages ->
            stringResource(
                R.string.route_block_unplayable_stages,
                cause.stageCodes.joinToString(", ") { stageName(it) },
            )
        null -> report?.blockingReason ?: stringResource(fallbackResId)
    }
}

@Composable
internal fun biomeDisplayName(originBiomeKey: String?): String? = when (originBiomeKey?.trim()?.lowercase(Locale.ROOT)) {
    "beach" -> stringResource(R.string.route_biome_beach)
    "desert" -> stringResource(R.string.route_biome_desert)
    "evil" -> stringResource(R.string.route_biome_evil)
    "ice" -> stringResource(R.string.route_biome_ice)
    "mesa" -> stringResource(R.string.route_biome_mesa)
    "wooded" -> stringResource(R.string.route_biome_wooded)
    "cinema" -> stringResource(R.string.route_biome_cinema)
    "raftingriver" -> stringResource(R.string.route_biome_rafting)
    else -> null
}

@Composable
internal fun candidateTierLabel(tier: RoadJukeboxCatalog.CandidateTier?): String? = when (tier) {
    RoadJukeboxCatalog.CandidateTier.GRAND_PRIX -> stringResource(R.string.route_tier_gp)
    RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER -> stringResource(R.string.route_tier_exclusive)
    RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED -> stringResource(R.string.route_tier_boosted)
    RoadJukeboxCatalog.CandidateTier.MAIN_ORDER -> stringResource(R.string.route_tier_main)
    RoadJukeboxCatalog.CandidateTier.NIGHT_ORDER -> stringResource(R.string.route_tier_night)
    null -> null
}

@Composable
internal fun edgeResolutionSummary(
    resolution: RoadJukeboxCatalog.EdgeMusicResolution,
    language: String,
): String = when (resolution) {
    is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete -> {
        val override = resolution.selectedOverride
        if (override != null) {
            stringResource(R.string.route_edge_music_override, override.localizedDisplayName(language))
        } else {
            val biomeCount = resolution.candidateTiers.values.count {
                it == RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER ||
                    it == RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED
            }
            val generalCount = (resolution.candidates.size - biomeCount).coerceAtLeast(0)
            val biomeLabel = biomeDisplayName(resolution.originBiomeKey)
            when (resolution.ruleModeUsed) {
                RoadJukeboxCatalog.EffectiveRuleMode.GRAND_PRIX ->
                    stringResource(R.string.route_edge_music_gp_pool, resolution.candidates.size)
                RoadJukeboxCatalog.EffectiveRuleMode.VS_DAY -> when {
                    biomeLabel != null && biomeCount > 0 ->
                        stringResource(R.string.route_edge_music_vs_day_biome, biomeLabel, biomeCount, generalCount)
                    resolution.candidates.isNotEmpty() ->
                        stringResource(R.string.route_edge_music_vs_day_general, resolution.candidates.size)
                    else -> stringResource(R.string.route_edge_music_auto)
                }
                RoadJukeboxCatalog.EffectiveRuleMode.VS_NIGHT ->
                    stringResource(R.string.route_edge_music_vs_night_pool, resolution.candidates.size)
                else -> when {
                    resolution.candidates.isNotEmpty() && biomeCount > 0 ->
                        stringResource(R.string.route_edge_music_auto_weighted, resolution.candidates.size, biomeCount)
                    resolution.candidates.isNotEmpty() ->
                        stringResource(R.string.route_edge_music_auto_count, resolution.candidates.size)
                    else -> stringResource(R.string.route_edge_music_auto)
                }
            }
        }
    }
    is RoadJukeboxCatalog.EdgeMusicResolution.SpecialRoadApproach ->
        stringResource(R.string.route_edge_music_to_rainbow)
    is RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect ->
        stringResource(R.string.route_edge_music_knockout_direct)
    is RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolIncomplete,
    is RoadJukeboxCatalog.EdgeMusicResolution.SubstituteMissing ->
        stringResource(R.string.route_edge_music_incomplete_pool)
    is RoadJukeboxCatalog.EdgeMusicResolution.CustomSubstitute ->
        stringResource(R.string.route_edge_music_substitute, resolution.track.localizedDisplayName(language))
    is RoadJukeboxCatalog.EdgeMusicResolution.MissingCandidateData ->
        stringResource(R.string.route_edge_music_pool_empty)
    is RoadJukeboxCatalog.EdgeMusicResolution.DisabledByUser ->
        stringResource(R.string.route_edge_music_muted)
}

@Composable
internal fun RoadMusicRuleModeSelector(
    selectedMode: RoadJukeboxCatalog.RoadMusicRuleMode,
    officialKind: OfficialRouteKind?,
    onSelectMode: (RoadJukeboxCatalog.RoadMusicRuleMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val autoLabel = when (officialKind) {
        OfficialRouteKind.GRAND_PRIX -> stringResource(R.string.route_rule_auto_gp)
        OfficialRouteKind.RALLY -> stringResource(R.string.route_rule_auto_rally)
        null -> stringResource(R.string.route_rule_auto_vs)
    }
    val autoDesc = when (officialKind) {
        OfficialRouteKind.GRAND_PRIX -> stringResource(R.string.route_rule_auto_gp_desc)
        OfficialRouteKind.RALLY -> stringResource(R.string.route_rule_auto_rally_desc)
        null -> stringResource(R.string.route_rule_auto_vs_desc)
    }
    val options = listOf(
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.AUTO,
            label = autoLabel,
            subtitle = autoDesc,
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.GRAND_PRIX,
            label = stringResource(R.string.route_rule_gp),
            subtitle = stringResource(R.string.route_rule_gp_desc),
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.VS_AUTO,
            label = stringResource(R.string.route_rule_vs_auto),
            subtitle = stringResource(R.string.route_rule_vs_auto_desc),
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.VS_DAY,
            label = stringResource(R.string.route_rule_vs_day),
            subtitle = stringResource(R.string.route_rule_vs_day_desc),
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.VS_NIGHT,
            label = stringResource(R.string.route_rule_vs_night),
            subtitle = stringResource(R.string.route_rule_vs_night_desc),
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RoadMusicRuleMode.KNOCKOUT_DIRECT,
            label = stringResource(R.string.route_rule_knockout_direct),
            subtitle = stringResource(R.string.route_rule_knockout_direct_desc),
        ),
    )
    val selectedLabel = options.firstOrNull { it.value == selectedMode }?.label ?: autoLabel
    com.mylittleblover.mkwplayer.ui.interactive.SelectionDrawerRow(
        label = stringResource(R.string.route_rule_mode_label),
        selectedLabel = selectedLabel,
        sheetTitle = stringResource(R.string.route_rule_mode_label),
        options = options,
        selectedValue = selectedMode,
        onSelect = onSelectMode,
        modifier = modifier,
        compact = true,
    )
}

@Composable
internal fun RouteSuiteModeSelector(
    selectedMode: RoadJukeboxCatalog.RouteSuiteMode,
    onSelectMode: (RoadJukeboxCatalog.RouteSuiteMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val options = listOf(
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RouteSuiteMode.GAME_SUITE,
            label = stringResource(R.string.route_suite_mode_game),
            subtitle = stringResource(R.string.route_suite_mode_game_desc),
        ),
        com.mylittleblover.mkwplayer.ui.interactive.DrawerOption(
            value = RoadJukeboxCatalog.RouteSuiteMode.COMPLETE_SUITE,
            label = stringResource(R.string.route_suite_mode_complete),
            subtitle = stringResource(R.string.route_suite_mode_complete_desc),
        ),
    )
    val selectedLabel = options.firstOrNull { it.value == selectedMode }?.label
        ?: stringResource(R.string.route_suite_mode_game)
    com.mylittleblover.mkwplayer.ui.interactive.SelectionDrawerRow(
        label = stringResource(R.string.route_suite_mode_label),
        selectedLabel = selectedLabel,
        sheetTitle = stringResource(R.string.route_suite_mode_label),
        options = options,
        selectedValue = selectedMode,
        onSelect = onSelectMode,
        modifier = modifier,
        enabled = enabled,
        compact = true,
    )
}

/** Shared string resource lookup for built-in suite variants (`"a"`..`"d"`, `"knockout"`) and `PresetRefs`. */
@androidx.annotation.StringRes
internal fun builtInSuiteLabelRes(variantOrRef: String?): Int = when (variantOrRef?.trim()?.lowercase(java.util.Locale.ROOT)) {
    "", "medley", "a", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_DEFAULT -> R.string.preset_system_default
    "a_alt", "a alt", "alt", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_SUITE_A_ALT -> R.string.preset_system_suite_a_alt
    "b", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_SUITE_B -> R.string.preset_system_suite_b
    "c", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_SUITE_C -> R.string.preset_system_suite_c
    "d", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_SUITE_D -> R.string.preset_system_suite_d
    "knockout", com.mylittleblover.mkwplayer.library.PresetRefs.SYSTEM_KNOCKOUT -> R.string.preset_system_knockout
    else -> R.string.preset_system_default
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteEdgeMusicPickerSheet(
    legTitle: String,
    leg: RoadJukeboxCatalog.RouteLegInspection,
    installedJukebox: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game>>,
    allowCustomSubstitute: Boolean,
    allowMute: Boolean,
    sessionHint: Boolean,
    language: String,
    packDirectory: File?,
    currentSelectedKey: String?,
    currentMuted: Boolean,
    onSelectAuto: () -> Unit,
    onSelectMuted: () -> Unit,
    onSelectTrackKey: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(leg.index, legTitle) { mutableStateOf("") }
    val hasKnownPool = leg.resolution.hasKnownPool()
    val knownPool = leg.resolution as? RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete
    val isSpecialApproach = leg.resolution is RoadJukeboxCatalog.EdgeMusicResolution.SpecialRoadApproach
    val isKnockoutDirect = leg.resolution is RoadJukeboxCatalog.EdgeMusicResolution.KnockoutTourDirect
    val showAllInstalledFallback = !hasKnownPool && allowCustomSubstitute
    val substituteTag = stringResource(R.string.route_custom_substitute_tag)
    val tiersByKey = knownPool?.candidateTiers.orEmpty()
    val weightsByKey = remember(knownPool) {
        if (knownPool == null) emptyMap()
        else buildMap {
            knownPool.candidates.forEachIndexed { idx, item ->
                knownPool.candidateWeights.getOrNull(idx)?.let { put(item.key, it) }
            }
        }
    }
    val biomeLabel = biomeDisplayName(knownPool?.originBiomeKey)
    val poolRuleBanner: String? = when (knownPool?.ruleModeUsed) {
        RoadJukeboxCatalog.EffectiveRuleMode.GRAND_PRIX ->
            stringResource(R.string.route_picker_rule_gp)
        RoadJukeboxCatalog.EffectiveRuleMode.VS_DAY ->
            if (biomeLabel != null) {
                stringResource(R.string.route_picker_rule_vs_day_biome, biomeLabel)
            } else {
                stringResource(R.string.route_picker_rule_vs_day_general)
            }
        RoadJukeboxCatalog.EffectiveRuleMode.VS_NIGHT ->
            stringResource(R.string.route_picker_rule_vs_night)
        else -> null
    }

    val selectableTracks: List<Pair<JukeboxCatalog.TrackItem, JukeboxCatalog.Game?>> = remember(
        leg,
        installedJukebox,
        allowCustomSubstitute,
        query,
        language,
        hasKnownPool,
    ) {
        val trimmed = query.trim()
        val gamesByKey = installedJukebox.associate { (item, game) -> item.key to game }
        val base = when {
            hasKnownPool -> {
                val res = leg.resolution as? RoadJukeboxCatalog.EdgeMusicResolution.KnownPoolComplete
                res?.candidates.orEmpty().map { it to gamesByKey[it.key] }
            }
            allowCustomSubstitute -> installedJukebox.map { (item, game) -> item to game }
            else -> emptyList()
        }
        if (trimmed.isEmpty()) {
            base
        } else {
            base.filter { (item, game) ->
                val gameNames = game?.let { listOf(it.localizedName(language)) + it.localizedNames.values }.orEmpty()
                item.matches(trimmed, gameNames)
            }
        }
    }

    MkwBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.route_pick_edge_track),
        subtitle = legTitle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (sessionHint) {
                Text(
                    stringResource(R.string.route_pick_edge_session_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MkwKitTokens.Palette.inkSecondary,
                )
            }
            if (poolRuleBanner != null) {
                MkwCard(
                    tone = MkwSurfaceTone.Recessed,
                    shape = MkwKitTokens.Shape.control,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        poolRuleBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                }
            }
            if (showAllInstalledFallback) {
                MkwCard(
                    tone = MkwSurfaceTone.RoseTint,
                    selected = true,
                    shape = MkwKitTokens.Shape.control,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MkwBadge(text = substituteTag, tone = MkwBadgeTone.Rose, tabular = false)
                        Text(
                            when {
                                isSpecialApproach -> stringResource(R.string.route_edge_music_to_rainbow)
                                isKnockoutDirect -> stringResource(R.string.route_edge_music_knockout_direct)
                                else -> stringResource(R.string.route_edge_music_pool_empty)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.crimsonDeep,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MkwSecondaryButton(
                    text = stringResource(R.string.route_clear_override),
                    onClick = { onSelectAuto(); onDismiss() },
                    leadingIcon = Icons.Filled.Shuffle,
                    size = MkwButtonSize.Compact,
                    modifier = Modifier.weight(1f),
                )
                if (allowMute) {
                    MkwSecondaryButton(
                        text = stringResource(R.string.route_skip_edge),
                        onClick = { onSelectMuted(); onDismiss() },
                        leadingIcon = Icons.Filled.MusicOff,
                        size = MkwButtonSize.Compact,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (selectableTracks.size > 6 || query.isNotEmpty() || showAllInstalledFallback) {
                MkwTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.discovery_search),
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = MkwKitTokens.Palette.inkSecondary) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            MkwIconButton(
                                icon = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.discovery_cancel),
                                onClick = { query = "" },
                                visualSize = 32.dp,
                                iconSize = 18.dp,
                            )
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MkwDivider()

            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                if (selectableTracks.isEmpty()) {
                    item {
                        Text(
                            when {
                                isSpecialApproach -> stringResource(R.string.route_edge_music_to_rainbow)
                                isKnockoutDirect -> stringResource(R.string.route_edge_music_knockout_direct)
                                leg.resolution.isMissingOrIncomplete() -> stringResource(R.string.route_edge_music_incomplete_pool)
                                else -> stringResource(R.string.route_edge_music_pool_empty)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MkwKitTokens.Palette.inkSecondary,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(selectableTracks, key = { (item, _) -> "pick:${item.key}" }) { (item, game) ->
                    val isSelected = !currentMuted && (
                        currentSelectedKey == item.key ||
                            currentSelectedKey == item.track.key ||
                            currentSelectedKey == item.track.primary.sha256
                        )
                    val artworkName = item.ostIndex?.let { "t_%03d.webp".format(Locale.ROOT, it) }
                    val tier = tiersByKey[item.key]
                    val weight = weightsByKey[item.key]
                    val tierText = when {
                        (tier == RoadJukeboxCatalog.CandidateTier.EXCLUSIVE_ORDER ||
                            tier == RoadJukeboxCatalog.CandidateTier.THEMED_BOOSTED) && biomeLabel != null ->
                            stringResource(R.string.route_tier_exclusive_biome, biomeLabel)
                        tier == RoadJukeboxCatalog.CandidateTier.NIGHT_ORDER && weight != null ->
                            stringResource(R.string.route_tier_night_weight, weight)
                        else -> candidateTierLabel(tier)
                    }
                    val sub = buildList {
                        tierText?.let(::add)
                        game?.localizedName(language)?.takeIf { it.isNotBlank() }?.let(::add)
                        item.localizedArrangement(language)?.takeIf { it.isNotBlank() }?.let(::add)
                        if (showAllInstalledFallback) add(substituteTag)
                    }.joinToString(" · ")
                    MkwOptionRow(
                        headline = item.localizedDisplayName(language),
                        supporting = sub.takeIf { it.isNotBlank() },
                        selected = isSelected,
                        onClick = {
                            onSelectTrackKey(item.key)
                            onDismiss()
                        },
                        leading = {
                            PackArtwork.Thumbnail(
                                packDirectory,
                                listOfNotNull(artworkName),
                                Modifier.size(44.dp).clip(MkwKitTokens.Shape.thumb)
                                    .background(MkwKitTokens.Palette.track),
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, tint = MkwKitTokens.Palette.inkSecondary)
                            }
                        },
                        trailing = if (isSelected) {
                            { Icon(Icons.Filled.Check, null, tint = MkwKitTokens.Palette.crimson) }
                        } else null,
                    )
                }
            }
        }
    }
}

internal data class PureFreeRoamLegDescriptor(
    val edgeIndex: Int,
    val fromLabel: String,
    val toLabel: String,
    val override: RoadJukeboxCatalog.EdgeFreeRoamOverride? = null,
)

@Composable
internal fun freeRoamModeDisplayLabel(mode: FreeRoamJukeboxCatalog.FreeRoamMode): String = when (mode) {
    FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK -> stringResource(R.string.jukebox_freeroam_mode_auto)
    FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_DAY -> stringResource(R.string.jukebox_freeroam_mode_solo_day)
    FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_NIGHT -> stringResource(R.string.jukebox_freeroam_mode_solo_night)
    FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_ALL -> stringResource(R.string.jukebox_freeroam_mode_solo_all)
    FreeRoamJukeboxCatalog.FreeRoamMode.FREEROAM_ONLY -> stringResource(R.string.jukebox_freeroam_mode_freeroam_only)
    FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_DAY -> stringResource(R.string.jukebox_freeroam_mode_online_day)
    FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_NIGHT -> stringResource(R.string.jukebox_freeroam_mode_online_night)
}

@Composable
internal fun freeRoamModeChipLabel(mode: FreeRoamJukeboxCatalog.FreeRoamMode): String = when (mode) {
    FreeRoamJukeboxCatalog.FreeRoamMode.AUTO_CLOCK -> stringResource(R.string.jukebox_freeroam_mode_auto_chip)
    FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_DAY -> stringResource(R.string.jukebox_freeroam_mode_solo_day_chip)
    FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_NIGHT -> stringResource(R.string.jukebox_freeroam_mode_solo_night_chip)
    FreeRoamJukeboxCatalog.FreeRoamMode.SOLO_ALL -> stringResource(R.string.jukebox_freeroam_mode_solo_all)
    FreeRoamJukeboxCatalog.FreeRoamMode.FREEROAM_ONLY -> stringResource(R.string.jukebox_freeroam_mode_freeroam_only)
    FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_DAY -> stringResource(R.string.jukebox_freeroam_mode_online_day_chip)
    FreeRoamJukeboxCatalog.FreeRoamMode.ONLINE_NIGHT -> stringResource(R.string.jukebox_freeroam_mode_online_night_chip)
}

@Composable
internal fun pureFreeRoamGamePoolLegSummary(mode: FreeRoamJukeboxCatalog.FreeRoamMode): String =
    stringResource(
        R.string.route_pure_freeroam_leg_game_pool_fmt,
        freeRoamModeDisplayLabel(FreeRoamJukeboxCatalog.coerceInGameFreeRoamMode(mode)),
    )

@Composable
internal fun pureFreeRoamTracksLabel(count: Int): String =
    if (count == 1) stringResource(R.string.route_pure_freeroam_tracks_one)
    else stringResource(R.string.route_pure_freeroam_tracks_fmt, count)

@Composable
internal fun pureFreeRoamMinutesLabel(minutes: Int): String =
    stringResource(R.string.route_pure_freeroam_minutes_fmt, minutes)

@Composable
internal fun pureFreeRoamLegChipLabel(
    config: RoadJukeboxCatalog.PureFreeRoamConfig,
    override: RoadJukeboxCatalog.EdgeFreeRoamOverride?,
): String {
    val cleanConfig = config.normalized()
    val cleanOv = override?.normalized()
    return when (cleanConfig.controlMode) {
        RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT -> {
            val custom = cleanOv?.trackCountOverride
            if (custom != null) {
                if (custom == 1) stringResource(R.string.route_pure_freeroam_leg_custom_tracks_one)
                else stringResource(R.string.route_pure_freeroam_leg_custom_tracks_fmt, custom)
            } else {
                val def = cleanConfig.defaultTracksPerLeg
                if (def == 1) stringResource(R.string.route_pure_freeroam_leg_default_tracks_one)
                else stringResource(R.string.route_pure_freeroam_leg_default_tracks_fmt, def)
            }
        }
        RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION -> {
            val custom = cleanOv?.durationMinutesOverride
            if (custom != null) {
                stringResource(R.string.route_pure_freeroam_leg_custom_minutes_fmt, custom)
            } else {
                stringResource(R.string.route_pure_freeroam_leg_default_minutes_fmt, cleanConfig.defaultMinutesPerLeg)
            }
        }
    }
}

/**
 * Hero Porcelain & Hairline configuration card for **Pure Free Roam Music Mode** (#5).
 *
 * - Canonical [MkwCard] + [MkwOptionRow] + [MkwSwitch] toggle header with crisp [MkwMotion.panelEnter] /
 *   [MkwMotion.panelExit] expansion.
 * - Canonical [MkwSegmentedBar] mode switch (`TRACK_COUNT` vs `DURATION`) driven by
 *   `AnimatedContent(MkwMotion.viewSwap)`.
 * - Canonical [MkwIconButton] (`Bordered`) steppers, [MkwSlider], and [MkwFilterPill] presets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PureFreeRoamConfigCard(
    config: RoadJukeboxCatalog.PureFreeRoamConfig,
    onConfigChange: (RoadJukeboxCatalog.PureFreeRoamConfig) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val clean = config.normalized()
    val reducedMotion = rememberReducedMotion()

    MkwCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160, easing = AppMotion.easeOut)),
        tone = if (clean.enabled) MkwSurfaceTone.RoseTint else MkwSurfaceTone.Porcelain,
        selected = clean.enabled,
        shape = MkwKitTokens.Shape.control,
        contentPadding = PaddingValues(if (compact) 6.dp else 8.dp),
    ) {
        MkwOptionRow(
            headline = stringResource(R.string.route_pure_freeroam_title),
            supporting = stringResource(R.string.route_pure_freeroam_desc),
            role = Role.Switch,
            onClick = { onConfigChange(clean.copy(enabled = !clean.enabled)) },
            leading = {
                Box(
                    modifier = Modifier
                        .size(if (compact) 34.dp else 38.dp)
                        .clip(CircleShape)
                        .background(if (clean.enabled) MkwKitTokens.Palette.crimson else MkwKitTokens.Palette.inset)
                        .then(
                            if (clean.enabled) {
                                Modifier
                            } else {
                                Modifier.border(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline, CircleShape)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                        tint = if (clean.enabled) Color.White else MkwKitTokens.Palette.crimson,
                    )
                }
            },
            trailing = {
                MkwSwitch(
                    checked = clean.enabled,
                    onCheckedChange = { onConfigChange(clean.copy(enabled = it)) },
                )
            },
        )

        AnimatedVisibility(
            visible = clean.enabled,
            enter = MkwMotion.panelEnter(reducedMotion),
            exit = MkwMotion.panelExit(reducedMotion),
        ) {
            val isGameFreeRoamPool = clean.trackPool == RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MkwDivider(color = MkwKitTokens.Palette.crimson.copy(alpha = 0.16f))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.route_pure_freeroam_pool_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MkwKitTokens.Palette.ink,
                    )
                    PureFreeRoamTrackPoolSegmentedBar(
                        selectedPool = clean.trackPool,
                        onSelectPool = { pool ->
                            onConfigChange(clean.copy(trackPool = pool).normalized())
                        },
                    )
                }

                AnimatedVisibility(
                    visible = isGameFreeRoamPool,
                    enter = MkwMotion.panelEnter(reducedMotion),
                    exit = MkwMotion.panelExit(reducedMotion),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(
                                FreeRoamJukeboxCatalog.FreeRoamMode.IN_GAME_FREE_ROAM_MODES,
                                key = { "pfr-game-mode:${it.name}" },
                            ) { mode ->
                                MkwFilterPill(
                                    label = freeRoamModeChipLabel(mode),
                                    selected = clean.freeRoamMode == mode,
                                    onClick = {
                                        onConfigChange(clean.copy(freeRoamMode = mode).normalized())
                                    },
                                    role = Role.RadioButton,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.route_pure_freeroam_pool_game_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                }

                MkwDivider(color = MkwKitTokens.Palette.crimson.copy(alpha = 0.12f))

                PureFreeRoamControlModeSegmentedBar(
                    selectedMode = clean.controlMode,
                    onSelectMode = { mode -> onConfigChange(clean.copy(controlMode = mode)) },
                )

                AnimatedContent(
                    targetState = clean.controlMode,
                    transitionSpec = { MkwMotion.viewSwap(reducedMotion) },
                    label = "pure-freeroam-mode-swap",
                ) { mode ->
                    when (mode) {
                        RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT -> {
                            PureFreeRoamNumericStepperPanel(
                                label = stringResource(R.string.route_pure_freeroam_tracks_per_leg_label),
                                valueText = pureFreeRoamTracksLabel(clean.defaultTracksPerLeg),
                                value = clean.defaultTracksPerLeg,
                                range = RoadJukeboxCatalog.PURE_FREE_ROAM_MIN_TRACKS_PER_LEG..RoadJukeboxCatalog.PURE_FREE_ROAM_MAX_TRACKS_PER_LEG,
                                quickPresets = listOf(1, 2, 3, 5),
                                presetLabel = { pureFreeRoamTracksLabel(it) },
                                onValueChange = { onConfigChange(clean.copy(defaultTracksPerLeg = it).normalized()) },
                                note = if (isGameFreeRoamPool) {
                                    null
                                } else {
                                    stringResource(R.string.route_pure_freeroam_course_skipped_note)
                                },
                            )
                        }
                        RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION -> {
                            PureFreeRoamNumericStepperPanel(
                                label = stringResource(R.string.route_pure_freeroam_minutes_per_leg_label),
                                valueText = pureFreeRoamMinutesLabel(clean.defaultMinutesPerLeg),
                                value = clean.defaultMinutesPerLeg,
                                range = RoadJukeboxCatalog.PURE_FREE_ROAM_MIN_MINUTES_PER_LEG..RoadJukeboxCatalog.PURE_FREE_ROAM_MAX_MINUTES_PER_LEG,
                                quickPresets = listOf(3, 5, 10, 15),
                                presetLabel = { pureFreeRoamMinutesLabel(it) },
                                onValueChange = { onConfigChange(clean.copy(defaultMinutesPerLeg = it).normalized()) },
                                note = if (isGameFreeRoamPool) {
                                    null
                                } else {
                                    stringResource(R.string.route_pure_freeroam_duration_advance_note)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PureFreeRoamTrackPoolSegmentedBar(
    selectedPool: RoadJukeboxCatalog.PureFreeRoamTrackPool,
    onSelectPool: (RoadJukeboxCatalog.PureFreeRoamTrackPool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val routeLabel = stringResource(R.string.route_pure_freeroam_pool_route)
    val gameLabel = stringResource(R.string.route_pure_freeroam_pool_game)
    MkwSegmentedBar(
        items = listOf(
            RoadJukeboxCatalog.PureFreeRoamTrackPool.ROUTE_RULES,
            RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM,
        ),
        selected = selectedPool,
        labelOf = { pool ->
            when (pool) {
                RoadJukeboxCatalog.PureFreeRoamTrackPool.ROUTE_RULES -> routeLabel
                RoadJukeboxCatalog.PureFreeRoamTrackPool.GAME_FREE_ROAM -> gameLabel
            }
        },
        onSelect = onSelectPool,
        modifier = modifier,
    )
}

@Composable
private fun PureFreeRoamControlModeSegmentedBar(
    selectedMode: RoadJukeboxCatalog.PureFreeRoamControlMode,
    onSelectMode: (RoadJukeboxCatalog.PureFreeRoamControlMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byCountLabel = stringResource(R.string.route_pure_freeroam_by_count)
    val byDurationLabel = stringResource(R.string.route_pure_freeroam_by_duration)
    MkwSegmentedBar(
        items = listOf(
            RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT,
            RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION,
        ),
        selected = selectedMode,
        labelOf = { mode ->
            when (mode) {
                RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT -> byCountLabel
                RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION -> byDurationLabel
            }
        },
        onSelect = onSelectMode,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PureFreeRoamNumericStepperPanel(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    quickPresets: List<Int>,
    presetLabel: @Composable (Int) -> String,
    onValueChange: (Int) -> Unit,
    note: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MkwKitTokens.Palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PureFreeRoamStepperButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.route_pure_freeroam_decrease),
                    enabled = value > range.first,
                    onClick = { onValueChange((value - 1).coerceIn(range)) },
                )
                MkwBadge(
                    text = valueText,
                    tone = MkwBadgeTone.Crimson,
                    tabular = true,
                )
                PureFreeRoamStepperButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.route_pure_freeroam_increase),
                    enabled = value < range.last,
                    onClick = { onValueChange((value + 1).coerceIn(range)) },
                )
            }
        }

        MkwSlider(
            value = value.toFloat(),
            onValueChange = { snapped -> onValueChange(snapped.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            quickPresets.forEach { preset ->
                MkwFilterPill(
                    label = presetLabel(preset),
                    selected = value == preset,
                    onClick = { onValueChange(preset) },
                    role = Role.RadioButton,
                )
            }
        }

        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MkwKitTokens.Palette.inkSecondary,
            )
        }
    }
}

@Composable
private fun PureFreeRoamStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MkwIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        tone = MkwIconButtonTone.Bordered,
        visualSize = 34.dp,
        iconSize = 18.dp,
        enabled = enabled,
    )
}

/**
 * Compact canonical [MkwFilterPill] placed on route leg rows when Pure Free Roam mode is enabled.
 * Displays either the route default pacing or a highlighted custom per-leg override.
 */
@Composable
internal fun PureFreeRoamLegOverrideChip(
    config: RoadJukeboxCatalog.PureFreeRoamConfig,
    override: RoadJukeboxCatalog.EdgeFreeRoamOverride?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleanConfig = config.normalized()
    val cleanOv = override?.normalized()
    val hasCustom = when (cleanConfig.controlMode) {
        RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT -> cleanOv?.trackCountOverride != null
        RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION -> cleanOv?.durationMinutesOverride != null
    }
    val editDesc = stringResource(R.string.route_pure_freeroam_leg_override_cd)

    MkwFilterPill(
        label = pureFreeRoamLegChipLabel(cleanConfig, cleanOv),
        selected = expanded || hasCustom,
        onClick = onClick,
        leadingIcon = if (cleanConfig.controlMode == RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION) {
            Icons.Filled.Schedule
        } else {
            Icons.AutoMirrored.Filled.QueueMusic
        },
        trailingIcon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
        role = Role.Button,
        modifier = modifier.semantics { contentDescription = editDesc },
    )
}

/**
 * Inline expandable override panel for a single route leg using crisp [MkwMotion.panelEnter] /
 * [MkwMotion.panelExit]. Lets the listener set a per-leg track count (`1..10`) or duration
 * (`1..30 min`) or reset to follow the route default.
 */
@Composable
internal fun PureFreeRoamLegInlineEditor(
    visible: Boolean,
    config: RoadJukeboxCatalog.PureFreeRoamConfig,
    override: RoadJukeboxCatalog.EdgeFreeRoamOverride?,
    onOverrideChange: (RoadJukeboxCatalog.EdgeFreeRoamOverride?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleanConfig = config.normalized()
    val cleanOv = override?.normalized() ?: RoadJukeboxCatalog.EdgeFreeRoamOverride()
    val reducedMotion = rememberReducedMotion()

    AnimatedVisibility(
        visible = visible,
        enter = MkwMotion.panelEnter(reducedMotion),
        exit = MkwMotion.panelExit(reducedMotion),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MkwDivider()
            when (cleanConfig.controlMode) {
                RoadJukeboxCatalog.PureFreeRoamControlMode.TRACK_COUNT -> {
                    val effectiveValue = cleanOv.trackCountOverride ?: cleanConfig.defaultTracksPerLeg
                    val isCustom = cleanOv.trackCountOverride != null
                    PureFreeRoamNumericStepperPanel(
                        label = stringResource(R.string.route_pure_freeroam_custom_tracks_label),
                        valueText = pureFreeRoamTracksLabel(effectiveValue),
                        value = effectiveValue,
                        range = RoadJukeboxCatalog.PURE_FREE_ROAM_MIN_TRACKS_PER_LEG..RoadJukeboxCatalog.PURE_FREE_ROAM_MAX_TRACKS_PER_LEG,
                        quickPresets = listOf(1, 2, 3, 5),
                        presetLabel = { pureFreeRoamTracksLabel(it) },
                        onValueChange = { next ->
                            val updated = cleanOv.copy(trackCountOverride = next).normalized()
                            onOverrideChange(updated.takeUnless { it.isEmpty })
                        },
                    )
                    if (isCustom) {
                        val defaultBtnLabel = if (cleanConfig.defaultTracksPerLeg == 1) {
                            stringResource(R.string.route_pure_freeroam_use_default_tracks_one)
                        } else {
                            stringResource(R.string.route_pure_freeroam_use_default_tracks_fmt, cleanConfig.defaultTracksPerLeg)
                        }
                        MkwGhostButton(
                            text = defaultBtnLabel,
                            onClick = {
                                val updated = cleanOv.copy(trackCountOverride = null).normalized()
                                onOverrideChange(updated.takeUnless { it.isEmpty })
                            },
                            leadingIcon = Icons.Filled.Refresh,
                        )
                    }
                }
                RoadJukeboxCatalog.PureFreeRoamControlMode.DURATION -> {
                    val effectiveValue = cleanOv.durationMinutesOverride ?: cleanConfig.defaultMinutesPerLeg
                    val isCustom = cleanOv.durationMinutesOverride != null
                    PureFreeRoamNumericStepperPanel(
                        label = stringResource(R.string.route_pure_freeroam_custom_minutes_label),
                        valueText = pureFreeRoamMinutesLabel(effectiveValue),
                        value = effectiveValue,
                        range = RoadJukeboxCatalog.PURE_FREE_ROAM_MIN_MINUTES_PER_LEG..RoadJukeboxCatalog.PURE_FREE_ROAM_MAX_MINUTES_PER_LEG,
                        quickPresets = listOf(3, 5, 10, 15),
                        presetLabel = { pureFreeRoamMinutesLabel(it) },
                        onValueChange = { next ->
                            val updated = cleanOv.copy(durationMinutesOverride = next).normalized()
                            onOverrideChange(updated.takeUnless { it.isEmpty })
                        },
                    )
                    if (isCustom) {
                        MkwGhostButton(
                            text = stringResource(
                                R.string.route_pure_freeroam_use_default_minutes_fmt,
                                cleanConfig.defaultMinutesPerLeg,
                            ),
                            onClick = {
                                val updated = cleanOv.copy(durationMinutesOverride = null).normalized()
                                onOverrideChange(updated.takeUnless { it.isEmpty })
                            },
                            leadingIcon = Icons.Filled.Refresh,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standalone hairline leg card with inline expand/collapse override controls (zero list stagger).
 * Designed for use inside [PureFreeRoamSheet] and route detail lists.
 */
@Composable
internal fun PureFreeRoamLegOverrideRow(
    leg: PureFreeRoamLegDescriptor,
    config: RoadJukeboxCatalog.PureFreeRoamConfig,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOverrideChange: (RoadJukeboxCatalog.EdgeFreeRoamOverride?) -> Unit,
    modifier: Modifier = Modifier,
) {
    MkwCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160, easing = AppMotion.easeOut)),
        tone = MkwSurfaceTone.Porcelain,
        shape = MkwKitTokens.Shape.control,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MkwBadge(
                text = "${leg.edgeIndex + 1}",
                tone = MkwBadgeTone.Rose,
                tabular = true,
            )
            Text(
                text = "${leg.fromLabel} → ${leg.toLabel}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MkwKitTokens.Palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PureFreeRoamLegOverrideChip(
                config = config,
                override = leg.override,
                expanded = expanded,
                onClick = onToggleExpand,
            )
        }
        PureFreeRoamLegInlineEditor(
            visible = expanded,
            config = config,
            override = leg.override,
            onOverrideChange = onOverrideChange,
        )
    }
}

/**
 * Full Porcelain & Hairline bottom sheet configuring Pure Free Roam Music Mode and per-leg pacing
 * overrides for any official or custom route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PureFreeRoamSheet(
    routeTitle: String,
    config: RoadJukeboxCatalog.PureFreeRoamConfig,
    legs: List<PureFreeRoamLegDescriptor>,
    onConfigChange: (RoadJukeboxCatalog.PureFreeRoamConfig) -> Unit,
    onLegOverrideChange: (edgeIndex: Int, override: RoadJukeboxCatalog.EdgeFreeRoamOverride?) -> Unit,
    onDismiss: () -> Unit,
) {
    var expandedLegIndex by rememberSaveable(routeTitle) { mutableStateOf<Int?>(null) }
    MkwBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.route_pure_freeroam_title),
        subtitle = routeTitle,
        modifier = Modifier.fillMaxHeight(0.84f),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "pure-freeroam-hero") {
                PureFreeRoamConfigCard(
                    config = config,
                    onConfigChange = onConfigChange,
                )
            }
            if (config.enabled && legs.isNotEmpty()) {
                item(key = "pure-freeroam-legs-header") {
                    Text(
                        text = stringResource(R.string.route_pure_freeroam_leg_override_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MkwKitTokens.Palette.inkSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                itemsIndexed(legs, key = { _, leg -> "freeroam-leg:${leg.edgeIndex}" }) { _, leg ->
                    val isExpanded = expandedLegIndex == leg.edgeIndex
                    PureFreeRoamLegOverrideRow(
                        leg = leg,
                        config = config,
                        expanded = isExpanded,
                        onToggleExpand = {
                            expandedLegIndex = if (isExpanded) null else leg.edgeIndex
                        },
                        onOverrideChange = { ov -> onLegOverrideChange(leg.edgeIndex, ov) },
                    )
                }
            }
        }
    }
}
