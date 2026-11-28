package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlayerViewModel

private val patternLabel = Regex("^(Main|Full|Fast|Outro[0-9]*) Pattern([0-9]+)$")
private val regionLabel = Regex("^Region ?([0-9]+)$")
private val areaLabel = Regex("^Area([0-9]+)(?:_(Pre|Bridge|Inst|Outro[0-9]*))?$")

/** Normalizes stage/course identifiers (`BGM_Stage_SnowMountain`, `SnowMountain_00`, etc.). */
internal fun normalizeCourseCode(courseCode: String?): String? =
    courseCode?.trim()
        ?.removePrefix("BGM_Stage_")
        ?.removeSuffix("_00")
        ?.takeIf { it.isNotEmpty() }

/** Infers known multi-phase courses from [PlayerViewModel.FlowUi] when callers omit `courseCode`. */
internal fun inferCourseCode(flow: PlayerViewModel.FlowUi): String? = when {
    flow.isRainbowMelodyOverlay ||
        flow.stageKey?.startsWith("Area") == true ||
        flow.availableStageKeys.any { it.startsWith("Area") } -> "Rainbow"
    flow.regionNumbers.isNotEmpty() ||
        flow.stageKey?.startsWith("Region ") == true ||
        flow.availableStageKeys.any { it.startsWith("Region ") } -> "DonkeyKong"
    else -> null
}

/** Localized display label for a recording version selector (`Normal`/`On the Water`/`Inside the Screen`). */
@Composable
internal fun recordingVersionLabel(versionNumber: Int, courseCode: String? = null): String {
    val course = normalizeCourseCode(courseCode)
    return when {
        course == "SnowMountain" && versionNumber == 1 -> stringResource(R.string.stage_pattern_normal)
        course == "SnowMountain" && versionNumber == 2 -> stringResource(R.string.stage_pattern_water)
        course == "Cinema" && versionNumber == 1 -> stringResource(R.string.stage_pattern_normal)
        course == "Cinema" && versionNumber == 2 -> stringResource(R.string.stage_pattern_inside_screen)
        else -> stringResource(R.string.recording_version_number, versionNumber)
    }
}

/** Source labels stay stable in the engine; only their presentation is translated. */
@Composable
internal fun stageText(raw: String, courseCode: String? = null): String {
    val value = raw.trim()
    val course = normalizeCourseCode(courseCode)

    val pattern = patternLabel.matchEntire(value)
    if (pattern != null) {
        val base = pattern.groupValues[1]
        val number = pattern.groupValues[2].toInt()
        if (number == 2 && (course == "SnowMountain" || course == "Cinema")) {
            val variant = if (course == "SnowMountain") {
                stringResource(R.string.stage_pattern_water)
            } else {
                stringResource(R.string.stage_pattern_inside_screen)
            }
            return when (base) {
                "Main", "Full" -> variant
                "Fast" -> stringResource(R.string.stage_join, stringResource(R.string.stage_final_lap), variant)
                else -> stringResource(R.string.stage_join, stageText(base, null), variant)
            }
        }
        return stringResource(
            R.string.stage_join,
            stageText(base, null),
            stringResource(R.string.stage_pattern, number),
        )
    }

    if (value == "Pattern2") {
        return when (course) {
            "SnowMountain" -> stringResource(R.string.stage_pattern_water)
            "Cinema" -> stringResource(R.string.stage_pattern_inside_screen)
            else -> stringResource(R.string.stage_pattern, 2)
        }
    }
    if (value == "Fast_Pattern2") {
        val variant = when (course) {
            "SnowMountain" -> stringResource(R.string.stage_pattern_water)
            "Cinema" -> stringResource(R.string.stage_pattern_inside_screen)
            else -> stringResource(R.string.stage_pattern, 2)
        }
        return stringResource(R.string.stage_join, stringResource(R.string.stage_final_lap), variant)
    }

    val region = regionLabel.matchEntire(value)
    if (region != null) {
        val number = region.groupValues[1].toInt()
        return if (course == null || course == "DonkeyKong") {
            stringResource(R.string.stage_dk_lap, number)
        } else {
            stringResource(R.string.stage_region, number)
        }
    }

    val area = areaLabel.matchEntire(value)
    if (area != null) {
        val areaNum = area.groupValues[1].toInt()
        val phase = area.groupValues[2]
        if (areaNum == 1 && phase == "Inst") {
            return stringResource(R.string.stage_rainbow_area1_inst)
        }
        val location = when (areaNum) {
            1 -> stringResource(R.string.stage_rainbow_area1)
            2 -> stringResource(R.string.stage_pattern_water)
            3 -> stringResource(R.string.stage_rainbow_area3)
            else -> stringResource(R.string.stage_region, areaNum)
        }
        return if (phase.isEmpty()) location else stringResource(R.string.stage_join, location, stageText(phase, course))
    }

    if (value == "Final_Pre") {
        return stringResource(
            R.string.stage_join,
            stringResource(R.string.stage_final_lap),
            stringResource(R.string.stage_prelude),
        )
    }
    if (value.startsWith("Outro")) {
        val number = value.removePrefix("Outro").trim().toIntOrNull()
        return if (number != null) stringResource(R.string.stage_outro_numbered, number + 1)
        else stringResource(R.string.stage_outro)
    }
    return when (value.lowercase()) {
        "titleextended" -> stringResource(R.string.stage_title_extended)
        "titlenormal" -> stringResource(R.string.stage_title_normal)
        "titlebrasscut" -> stringResource(R.string.stage_title_brasscut)
        "battlefast" -> stringResource(R.string.stage_battle_fast)
        "pre", "prelude", "prelude, looping" -> stringResource(R.string.stage_prelude)
        "preshort", "pre (short)", "short prelude", "short prelude, looping" -> stringResource(R.string.stage_short_prelude)
        "full", "main", "main loop" -> when (course) {
            "SnowMountain", "Cinema" -> stringResource(R.string.stage_pattern_normal)
            "DonkeyKong" -> stringResource(R.string.stage_dk_lap, 1)
            else -> stringResource(R.string.stage_main)
        }
        "main_speaker" -> stringResource(R.string.flow_editor_role_main_speaker)
        "fast_speaker" -> stringResource(R.string.flow_editor_role_fast_speaker)
        "fast" -> when (course) {
            "SnowMountain", "Cinema" -> stringResource(
                R.string.stage_join,
                stringResource(R.string.stage_final_lap),
                stringResource(R.string.stage_pattern_normal),
            )
            else -> stringResource(R.string.stage_final_lap)
        }
        "final", "final lap" -> stringResource(R.string.stage_final_lap)
        "bridge" -> stringResource(R.string.stage_bridge)
        "outro", "ending" -> stringResource(R.string.stage_outro)
        "finallapff", "finallapfanfare" -> stringResource(R.string.stage_fanfare)
        "stopped" -> stringResource(R.string.stage_stopped)
        "finished" -> stringResource(R.string.stage_finished)
        "waiting", "not started", "" -> stringResource(R.string.stage_not_started)
        else -> value
    }
}

@Composable
internal fun performanceStageText(flow: PlayerViewModel.FlowUi, courseCode: String? = null): String {
    // During a bridge the foreground source may already have ended; do not claim it is still playing.
    if (flow.performing && flow.voiceCount > 0 && flow.sourceFrame == null)
        return stringResource(R.string.stage_transitioning)
    val resolvedCourse = courseCode ?: inferCourseCode(flow)
    val current = stageText(flow.stageKey ?: flow.performanceStage, resolvedCourse)
    val pending = flow.pendingStageKey ?: return current
    return stringResource(R.string.stage_pending, current, stageText(pending, resolvedCourse))
}
