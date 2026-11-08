package com.mylittleblover.mkwplayer.ui.unlock

import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.audio.SeekTarget
import com.mylittleblover.mkwplayer.player.UnlockUi
import com.mylittleblover.mkwplayer.ui.CupVisibility
import com.mylittleblover.mkwplayer.ui.art.CupArt
import com.mylittleblover.mkwplayer.unlock.SpecialCupUnlock.CeremonyPhase
import com.mylittleblover.mkwplayer.unlock.UnlockPresentation

/*
 * The decisions behind UI-11 (the top notification), UI-12 (the ceremony) and UI-13 (the
 * congratulations).
 *
 * No Compose import, for the reason every other state file gives: what these pages say is what needs
 * checking, and a screenshot cannot tell a locked control from a live one that happened to be grey.
 *
 * Three rules live here because breaking any of them is silent:
 *
 * - Pause, seek, skip and track changes do not exist during the ceremony. The audio layer will not
 *   perform them, so a screen that draws them -- even disabled -- tells the user they paused
 *   something they did not pause. [controlsFor] is the only place that decision is made.
 * - A ceremony of unknown length is `--:--`, never `00:00`. Zero minutes is a real length, and a
 *   reader who sees one concludes the ceremony has already finished.
 * - The first-time congratulations is shown once. A replay must not re-send it, because the dialog
 *   claims something new happened. [shouldShowCongratulation] is that rule.
 */

/**
 * What the ceremony panel may offer, per phase.
 *
 * Five separate flags rather than one enum, because the panel and the notification read them at
 * different moments and a single "state" would have to be parsed again by the layout to decide which
 * control is live.
 *
 * [canExit] is separate from the rest on purpose: leaving is always allowed. Exiting early stops the
 * music without granting completion, and the entitlement survives, so there is no phase in which
 * trapping the user would be correct.
 */
internal data class CeremonyControls(
    val canPause: Boolean,
    val canSeek: Boolean,
    val canSkip: Boolean,
    val canExit: Boolean,
    val canChangeTrack: Boolean,
)

/**
 * Resolves a stored phase name.
 *
 * The names come from [CeremonyPhase] itself, because `UnlockStore` writes `phase.name` and reads it
 * back. Parsing is still defensive: callers also receive names from restored snapshots and, later,
 * from routes and deep links, and a crash on an unexpected string would take the whole screen down.
 *
 * An unknown name reads as [CeremonyPhase.NONE], never as [CeremonyPhase.PLAYING]. NONE is the phase
 * with nothing to withhold, and the ceremony panel is only ever shown after a caller has decided to
 * show it, so the fallback cannot mislead on this screen. Defaulting to PLAYING would be worse in the
 * other direction: it would lock controls and show an "exit" warning for a ceremony that is not
 * running.
 *
 * Matching ignores case and surrounding whitespace. A case slip is the likeliest way a real phase
 * name arrives malformed, and falling back to NONE for "playing" would quietly re-enable pause during
 * actual playback -- the exact contradiction this file exists to prevent.
 */
internal fun ceremonyPhaseOf(phase: String): CeremonyPhase {
    val name = phase.trim()
    return CeremonyPhase.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: CeremonyPhase.NONE
}

/**
 * The controls for [phase].
 *
 * During PLAYING every control except exit is off. That is not a layout preference: the ceremony
 * audio is deliberately uninterruptible so the completion it grants cannot be manufactured by
 * seeking, muting or a fake timer. A screen offering pause there contradicts the audio layer, and the
 * user then believes they paused something that is still playing.
 *
 * Every other phase has nothing playing, so nothing is withheld.
 */
internal fun controlsFor(phase: String): CeremonyControls = when (ceremonyPhaseOf(phase)) {
    CeremonyPhase.PLAYING -> CeremonyControls(
        canPause = false,
        canSeek = false,
        canSkip = false,
        canExit = true,
        canChangeTrack = false,
    )

    // Listed one by one rather than an `else`: if a phase is ever added, this file must be looked at
    // instead of inheriting whichever branch happened to sort last.
    CeremonyPhase.NONE,
    CeremonyPhase.PENDING,
    CeremonyPhase.COMPLETED -> CeremonyControls(
        canPause = true,
        canSeek = true,
        canSkip = true,
        canExit = true,
        canChangeTrack = true,
    )
}

/**
 * The ceremony's progress as the panel shows it.
 *
 * [isReadOnly] is always true and is deliberately not a constructor caller's choice: see
 * [progressFor].
 */
internal data class CeremonyProgress(
    /** Elapsed time as `m:ss`, or `--:--` when there is no length to measure against. */
    val elapsed: String,
    /** Total time as `m:ss`, or `--:--` when the length is unknown. */
    val total: String,
    /** 0..1 for the bar. Zero when the length is unknown. */
    val fraction: Float,
    /** Always true: the ceremony's bar reports a position, it does not accept one. */
    val isReadOnly: Boolean,
)

/** The one unknown-time rendering, shared by both labels so they cannot drift apart. */
private const val UNKNOWN_TIME = "--:--"

/**
 * The ceremony's times and bar.
 *
 * Times go through [EngineSnapshot.formatPosition] rather than a second formatter here, so the
 * ceremony and the player read a position the same way. The fraction goes through
 * [SeekTarget.frameToFraction] for the same reason, and because that function already clamps instead
 * of overflowing the bar or emitting NaN.
 *
 * The length counts as usable only when both the frame count and the sample rate are. Frames alone
 * cannot be printed as a time, and printing a total while the elapsed time reads `--:--` would be a
 * bar that moves under labels that deny knowing where it is. When the length is not usable both
 * labels read `--:--` and the fraction is 0: a zero-length ceremony must never look finished, and
 * the unknown marker is the only honest rendering of a length nobody measured.
 *
 * A zero-length ceremony is a real case: the cue may be missing, unresolved, or still unimported,
 * and the caller arrives with `totalFrames == 0` rather than with a track.
 */
internal fun progressFor(
    positionFrames: Long,
    totalFrames: Long,
    sampleRate: Int,
): CeremonyProgress {
    val lengthKnown = totalFrames > 0 && sampleRate > 0
    return CeremonyProgress(
        elapsed = if (lengthKnown) EngineSnapshot.formatPosition(positionFrames, sampleRate) else UNKNOWN_TIME,
        total = if (lengthKnown) EngineSnapshot.formatPosition(totalFrames, sampleRate) else UNKNOWN_TIME,
        fraction = if (lengthKnown) SeekTarget.frameToFraction(positionFrames, totalFrames) else 0f,
        // Not negotiable. The screen has no seek callback and this flag is what makes a future caller
        // notice that adding one means changing this function first.
        isReadOnly = true,
    )
}

/**
 * One ceremony panel, as the screen draws it.
 *
 * [emblem] is [CupArt.special]'s drawable: the ceremony is the one place the hidden cup may be shown
 * before the user has entered it, because the cup has already been earned by the time this panel is
 * reachable.
 */
internal data class CeremonyView(
    val title: String,
    val subtitle: String,
    val emblem: Int,
    val controls: CeremonyControls,
    val progress: CeremonyProgress,
    val showCongratulation: Boolean,
    /** The consequence of leaving early, or null when leaving costs nothing. */
    val exitHint: String?,
)

/**
 * Builds the ceremony panel.
 *
 * [congratulationOwed] is passed in rather than read from the phase, because "the ceremony finished"
 * and "the user has not yet been congratulated" are separate facts: a replay finishes the ceremony
 * again without owing anything. Routing it through [shouldShowCongratulation] keeps the panel's
 * banner and the first-time dialog from ever disagreeing.
 *
 * A blank [cupName] falls back to the artwork's own label rather than drawing an empty heading: an
 * empty title is indistinguishable from a rendering failure.
 */
internal fun viewFor(
    phase: String,
    positionFrames: Long,
    totalFrames: Long,
    sampleRate: Int,
    congratulationOwed: Boolean,
    cupName: String,
): CeremonyView {
    val parsed = ceremonyPhaseOf(phase)
    return CeremonyView(
        title = cupName.trim().ifEmpty { CupArt.special.label },
        subtitle = subtitleFor(parsed),
        emblem = CupArt.special.drawable,
        controls = controlsFor(phase),
        progress = progressFor(positionFrames, totalFrames, sampleRate),
        showCongratulation = shouldShowCongratulation(phase, congratulated = !congratulationOwed),
        exitHint = exitHintFor(parsed),
    )
}

/** What the panel says under the title. It never claims a completion the phase has not reached. */
private fun subtitleFor(phase: CeremonyPhase): String = when (phase) {
    CeremonyPhase.PLAYING -> "Playing the unlock music."
    CeremonyPhase.COMPLETED -> "The unlock music played to the end."
    CeremonyPhase.PENDING -> "The unlock music is ready. It starts from the beginning."
    CeremonyPhase.NONE -> "The unlock music is not playing."
}

/**
 * The exit warning, during playback only.
 *
 * Leaving mid-ceremony stops the music and keeps the cup, and the next visit starts again from 00:00.
 * Both facts are surprising enough to state before the user leaves rather than after: a user who
 * expects exit to pause is a user who comes back to a ceremony they thought they had finished.
 */
private fun exitHintFor(phase: CeremonyPhase): String? =
    if (phase == CeremonyPhase.PLAYING) {
        "Leaving now stops the music and keeps the cup. Opening it again starts from the beginning."
    } else {
        null
    }

/**
 * One top notification, as the screen draws it.
 *
 * [dismissible] is a fact rather than a preference: closing the notification only hides it. The cup
 * stays earned and keeps its entry point, so there is no state in which withholding the close control
 * would be correct.
 */
internal data class NoticeView(
    val message: String,
    val actionLabel: String,
    val dismissible: Boolean,
)

/**
 * The notification's action.
 *
 * It says what the tap does without naming what is behind it. `UnlockPresentation.offerLabel()` is
 * the app's whole disclosure -- a hidden cup has appeared -- and it deliberately stops short of the
 * cup's name. This label matches that discretion: no "unlock" (there is no unlock button anywhere
 * else in the app, and the word invites the user to look for one), and no "Special Cup", which is the
 * one name the notification must not be the first place to reveal.
 */
private const val NOTICE_ACTION_LABEL = "Open the hidden cup"

/**
 * The top notification for the Special Cup offer, or null when there is nothing to announce.
 *
 * Null unless the cup is offered. `offered` is the caller's record of having told the user at all; it
 * is passed in rather than derived from the phase precisely because announcing and earning are
 * separate decisions. Merging them would make the notification fire again after a restart, and a
 * notification that comes back every launch is one the user learns to ignore.
 *
 * The wording is [UnlockPresentation.offerLabel] rather than a string here, so the notice cannot drift
 * from the app's one statement of what has happened. The action label is deliberately in the same
 * register: no "unlock", and no "Special Cup", because the notification is not the place to reveal a
 * cup the user has not opened yet.
 *
 * Where the notice may be drawn is the caller's decision, and callers should render it only on a page
 * where it is not already stale -- not over the ceremony itself, where "open" would be a control that
 * does nothing.
 */
internal fun noticeFor(unlock: UnlockUi?): NoticeView? {
    if (unlock?.offered != true) return null

    return NoticeView(
        message = UnlockPresentation.offerLabel(),
        actionLabel = NOTICE_ACTION_LABEL,
        dismissible = true,
    )
}

/**
 * Whether the first-time congratulations belongs on screen.
 *
 * True only for COMPLETED and not yet congratulated. This is the rule that stops a replay re-sending
 * the first-time dialog: after the first viewing the ceremony can be replayed as often as the user
 * likes, and each replay reaches COMPLETED again. Showing the dialog on a replay would tell the user
 * their cup is newly unlocked, which is false the second time.
 *
 * [congratulated] is the persisted flag, not a this-session one. An in-memory "shown already" would
 * come back false after a restart and re-send the dialog.
 */
internal fun shouldShowCongratulation(phase: String, congratulated: Boolean): Boolean =
    ceremonyPhaseOf(phase) == CeremonyPhase.COMPLETED && !congratulated
