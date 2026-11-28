package com.mylittleblover.mkwplayer.player

import kotlin.random.Random

/**
 * Pure Kotlin queue & history state machine for linear playback (Jukebox / standard lists).
 *
 * Implements the session rules specified in the design baseline:
 * - Freezes the base queue at session start (#61).
 * - Maintains a separate LIFO "Play next" temporary queue (#63, #64).
 * - Maintains real chronological playback history for Previous and forward history priority on Next (#58, #59).
 * - Shuffles each round as a complete permutation without repeats until exhausted (#55, #57).
 * - Gives Repeat-One priority over temporary/base queue on natural completion (#65), while manual
 *   Next still advances and preserves Repeat-One (#66).
 */
internal class LinearQueue(
    private val random: Random = Random.Default,
) {
    enum class RepeatMode {
        LIST,
        SINGLE,
        OFF,
        ;

        fun next(): RepeatMode = when (this) {
            LIST -> SINGLE
            SINGLE -> OFF
            OFF -> LIST
        }
    }

    data class Entry(
        val key: String,
        val title: String,
        val subtitle: String = "",
        val gameId: String? = null,
        val gameTitle: String? = null,
        val yearLabel: String? = null,
        val arrangement: String? = null,
        val ostIndex: Int? = null,
        val durationMs: Long = 0L,
        val artworkName: String? = null,
        val assetSha256: String? = null,
        val loop: Boolean = false,
        val loopStart: Long? = null,
        val loopEnd: Long? = null,
        val overlaidOpening: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.OverlaidOpening? = null,
        val chainedSequence: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.ChainedSequence? = null,
        val courseMedley: com.mylittleblover.mkwplayer.interactive.CourseMedleyCompiler.ResolvedMedley? = null,
        val nativeTransitionEnabled: Boolean = false,
        val nativeTransitionOverride: Boolean? = null,
        val nativeTransitionSourceLabel: String? = null,
        val sourceType: LinearSessionStore.SourceType = LinearSessionStore.SourceType.JUKEBOX,
        val courseCode: String? = null,
        val presetId: String? = null,
        val medleyId: String? = null,
        val sourceTrackKey: String? = null,
        val roadCandidateVariants: List<Entry> = emptyList(),
        val roadCandidateWeights: List<Int> = emptyList(),
        val roadCandidatePicker: (() -> Entry?)? = null,
    )

    data class Snapshot(
        val current: Entry? = null,
        val tempQueue: List<Entry> = emptyList(),
        val upNext: List<Entry> = emptyList(),
        val historyCount: Int = 0,
        val repeatMode: RepeatMode = RepeatMode.LIST,
        val shuffle: Boolean = false,
        val baseCount: Int = 0,
    ) {
        val hasQueue: Boolean
            get() = current != null && (tempQueue.isNotEmpty() || upNext.isNotEmpty() || historyCount > 0 || baseCount > 1)
    }

    sealed interface StepResult {
        data class Play(val entry: Entry) : StepResult
        data object RestartCurrent : StepResult
        data object Ended : StepResult
    }

    private var frozenBase: List<Entry> = emptyList()
    private var currentEntry: Entry? = null
    private var currentBaseIndex: Int = -1
    private val pastHistory = ArrayDeque<Pair<Entry, Int>>()
    private val forwardHistory = ArrayDeque<Pair<Entry, Int>>()
    private val tempNextQueue = ArrayDeque<Entry>()
    private val upcomingBase = ArrayDeque<Pair<Entry, Int>>()

    var repeatMode: RepeatMode = RepeatMode.LIST
        private set

    var shuffle: Boolean = false
        private set

    fun snapshot(): Snapshot = Snapshot(
        current = currentEntry,
        tempQueue = tempNextQueue.toList(),
        upNext = upcomingBase.map { it.first },
        historyCount = pastHistory.size,
        repeatMode = repeatMode,
        shuffle = shuffle,
        baseCount = frozenBase.size,
    )

    /**
     * Freezes [entries] as the session's base queue and starts at [startIndex].
     * Previous history and forward history reset; temporary queue clears for a fresh queue session.
     */
    fun startSession(entries: List<Entry>, startIndex: Int, shuffleOverride: Boolean? = null): Entry? {
        if (entries.isEmpty()) {
            clear()
            return null
        }
        val safeIndex = startIndex.coerceIn(0, entries.lastIndex)
        frozenBase = entries.toList()
        pastHistory.clear()
        forwardHistory.clear()
        tempNextQueue.clear()
        upcomingBase.clear()
        if (shuffleOverride != null) {
            shuffle = shuffleOverride
        }

        currentEntry = resolveRoundEntry(frozenBase[safeIndex])
        currentBaseIndex = safeIndex
        rebuildUpcomingAfterStart(safeIndex)
        return currentEntry
    }

    fun clear() {
        frozenBase = emptyList()
        currentEntry = null
        currentBaseIndex = -1
        pastHistory.clear()
        forwardHistory.clear()
        tempNextQueue.clear()
        upcomingBase.clear()
    }

    /** LIFO insertion at the front of the temporary queue (#64). */
    fun enqueueNext(entry: Entry) {
        if (currentEntry == null && frozenBase.isEmpty()) {
            startSession(listOf(entry), 0)
            return
        }
        tempNextQueue.addFirst(entry)
    }

    /** Inserts [entries] at the front of the temporary queue preserving list order. */
    fun enqueueNextBatch(entries: List<Entry>) {
        if (entries.isEmpty()) return
        if (currentEntry == null && frozenBase.isEmpty()) {
            startSession(entries, 0)
            return
        }
        for (i in entries.lastIndex downTo 0) {
            tempNextQueue.addFirst(entries[i])
        }
    }

    /** Appends [entry] to the end of the current playback queue. */
    fun enqueueEnd(entry: Entry) = enqueueEndBatch(listOf(entry))

    /** Appends [entries] to the end of the current playback queue. */
    fun enqueueEndBatch(entries: List<Entry>) {
        if (entries.isEmpty()) return
        if (currentEntry == null && frozenBase.isEmpty()) {
            startSession(entries, 0)
            return
        }
        val baseStart = frozenBase.size
        frozenBase = frozenBase + entries
        entries.forEachIndexed { offset, entry ->
            upcomingBase.addLast(entry to (baseStart + offset))
        }
    }

    /** Removes a pending item from the temporary "Play next" queue (#67). */
    fun removeTempAt(index: Int): Boolean {
        if (index !in tempNextQueue.indices) return false
        tempNextQueue.removeAt(index)
        return true
    }

    /** Removes a pending item from the upcoming base queue. */
    fun removeUpcomingAt(index: Int): Boolean {
        if (index !in upcomingBase.indices) return false
        upcomingBase.removeAt(index)
        return true
    }

    /** Reorders an item within the temporary "Play next" queue. */
    fun moveTemp(from: Int, to: Int): Boolean {
        if (from !in tempNextQueue.indices || to !in tempNextQueue.indices || from == to) return false
        val item = tempNextQueue.removeAt(from)
        tempNextQueue.add(to, item)
        return true
    }

    /** Reorders an item within the upcoming base queue. */
    fun moveUpcoming(from: Int, to: Int): Boolean {
        if (from !in upcomingBase.indices || to !in upcomingBase.indices || from == to) return false
        val item = upcomingBase.removeAt(from)
        upcomingBase.add(to, item)
        return true
    }

    /** Clears all upcoming items while keeping the currently active track intact. */
    fun clearUpcoming(): Boolean {
        if (tempNextQueue.isEmpty() && upcomingBase.isEmpty() && forwardHistory.isEmpty()) return false
        tempNextQueue.clear()
        upcomingBase.clear()
        forwardHistory.clear()
        return true
    }

    fun setShuffle(enabled: Boolean) {
        if (shuffle == enabled) return
        shuffle = enabled
        val active = currentEntry ?: return
        if (frozenBase.isEmpty()) return

        if (enabled) {
            // Keep remaining unplayed items from current round when toggling ON mid-session.
            val remaining = if (upcomingBase.isNotEmpty()) {
                upcomingBase.toList().shuffled(random)
            } else {
                frozenBase.indices
                    .filter { it != currentBaseIndex }
                    .map { frozenBase[it] to it }
                    .shuffled(random)
            }
            upcomingBase.clear()
            upcomingBase.addAll(remaining)
        } else {
            val anchorIndex = currentBaseIndex.takeIf { it in frozenBase.indices }
                ?: frozenBase.indexOfFirst { it.key == active.key }
            upcomingBase.clear()
            if (anchorIndex >= 0) {
                for (i in (anchorIndex + 1) until frozenBase.size) {
                    upcomingBase.addLast(frozenBase[i] to i)
                }
            }
        }
    }

    fun cycleRepeatMode(): RepeatMode {
        repeatMode = repeatMode.next()
        return repeatMode
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    /** Advances automatically when the active linear track reaches natural completion (#65). */
    fun onNaturalEnd(): StepResult {
        val active = currentEntry ?: return StepResult.Ended
        if (repeatMode == RepeatMode.SINGLE) {
            return StepResult.RestartCurrent
        }
        return advance(fromUser = false, current = active)
    }

    /** Manual Next (#59, #66). Preserves [repeatMode] even when RepeatMode.SINGLE is active. */
    fun next(): StepResult {
        val active = currentEntry ?: return StepResult.Ended
        return advance(fromUser = true, current = active)
    }

    /** Manual Previous (#58). Steps strictly along real playback history; restarts if at start. */
    fun previous(restartThresholdMs: Long = 0L, elapsedMs: Long = 0L): StepResult {
        val active = currentEntry ?: return StepResult.Ended
        if (pastHistory.isEmpty() || (restartThresholdMs > 0L && elapsedMs > restartThresholdMs)) {
            return StepResult.RestartCurrent
        }
        forwardHistory.addFirst(active to currentBaseIndex)
        val (prev, prevIdx) = pastHistory.removeLast()
        currentEntry = prev
        currentBaseIndex = prevIdx
        return StepResult.Play(prev)
    }

    /** Jumps directly to an item in the temporary queue (consuming prior temp items above it). */
    fun jumpToTemp(index: Int): Entry? {
        if (index !in tempNextQueue.indices) return null
        currentEntry?.let { pastHistory.addLast(it to currentBaseIndex) }
        forwardHistory.clear()
        val target = tempNextQueue[index]
        repeat(index + 1) { tempNextQueue.removeFirst() }
        currentEntry = target
        currentBaseIndex = frozenBase.indexOfFirst { it.key == target.key }
        return target
    }

    /** Jumps directly to an upcoming item in the frozen base queue. */
    fun jumpToUpcoming(index: Int): Entry? {
        if (index !in upcomingBase.indices) return null
        currentEntry?.let { pastHistory.addLast(it to currentBaseIndex) }
        forwardHistory.clear()
        val chosen = upcomingBase[index]
        repeat(index + 1) { upcomingBase.removeFirst() }
        currentEntry = chosen.first
        currentBaseIndex = chosen.second
        return chosen.first
    }

    private fun advance(fromUser: Boolean, current: Entry): StepResult {
        // 1. If we stepped backwards through history earlier, move forward through that history first (#59).
        if (forwardHistory.isNotEmpty()) {
            pastHistory.addLast(current to currentBaseIndex)
            val (target, targetIdx) = forwardHistory.removeFirst()
            currentEntry = target
            currentBaseIndex = targetIdx
            return StepResult.Play(target)
        }

        // 2. Temporary "Play next" queue takes priority over the frozen base queue (#63).
        if (tempNextQueue.isNotEmpty()) {
            pastHistory.addLast(current to currentBaseIndex)
            val target = tempNextQueue.removeFirst()
            currentEntry = target
            currentBaseIndex = frozenBase.indexOfFirst { it.key == target.key }
            return StepResult.Play(target)
        }

        // 3. Remaining tracks of the frozen base queue (sequential or current shuffled round).
        if (upcomingBase.isNotEmpty()) {
            pastHistory.addLast(current to currentBaseIndex)
            val (target, targetIdx) = upcomingBase.removeFirst()
            currentEntry = target
            currentBaseIndex = targetIdx
            return StepResult.Play(target)
        }

        // 4. End of round: List Loop restarts a new round (new shuffle permutation if Shuffle ON);
        // manual Next also wraps cleanly across the frozen list when List Loop or Single Loop is set
        // (or if base has >1 items on explicit Next skip).
        val shouldWrap = repeatMode == RepeatMode.LIST ||
            (fromUser && repeatMode == RepeatMode.SINGLE && frozenBase.isNotEmpty())
        if (!shouldWrap || frozenBase.isEmpty()) {
            return StepResult.Ended
        }

        pastHistory.addLast(current to currentBaseIndex)
        val round = if (shuffle) {
            frozenBase.indices.map { resolveRoundEntry(frozenBase[it]) to it }.shuffled(random).let { list ->
                // Avoid repeating the just-finished song at boundary when multiple tracks exist.
                if (list.size > 1 && list.first().second == currentBaseIndex) {
                    list.drop(1) + list.first()
                } else {
                    list
                }
            }
        } else {
            frozenBase.indices.map { resolveRoundEntry(frozenBase[it]) to it }
        }
        upcomingBase.clear()
        upcomingBase.addAll(round)
        val (nextItem, nextIdx) = upcomingBase.removeFirst()
        currentEntry = nextItem
        currentBaseIndex = nextIdx
        return StepResult.Play(nextItem)
    }

    /** Non-mutating lookahead of upcoming entries for native cross-track handover chaining (#51-#53). */
    fun peekNextEntries(limit: Int): List<Entry> {
        if (limit <= 0 || currentEntry == null || repeatMode == RepeatMode.SINGLE) return emptyList()
        val result = ArrayList<Entry>(limit)
        for ((entry, _) in forwardHistory) {
            if (result.size >= limit) return result
            result += entry
        }
        for (entry in tempNextQueue) {
            if (result.size >= limit) return result
            result += entry
        }
        for ((entry, _) in upcomingBase) {
            if (result.size >= limit) return result
            result += entry
        }
        if (result.size < limit && repeatMode == RepeatMode.LIST && !shuffle && frozenBase.isNotEmpty()) {
            for (entry in frozenBase) {
                if (result.size >= limit) break
                result += entry
            }
        }
        return result
    }

    /** Advances queue silently on a native segment boundary when the queue was not mutated mid-track. */
    fun advanceSilentForHandover(expectedKey: String): Entry? {
        if (repeatMode == RepeatMode.SINGLE) return null
        val peeked = peekNextEntries(1).firstOrNull() ?: return null
        if (peeked.key != expectedKey) return null
        val step = onNaturalEnd() as? StepResult.Play ?: return null
        return step.entry
    }

    data class ExportedState(
        val frozenBase: List<Entry>,
        val current: Entry,
        val currentBaseIndex: Int,
        val pastHistory: List<Pair<Entry, Int>>,
        val forwardHistory: List<Pair<Entry, Int>>,
        val tempQueue: List<Entry>,
        val upcomingBase: List<Pair<Entry, Int>>,
        val repeatMode: RepeatMode,
        val shuffle: Boolean,
    )

    fun exportState(): ExportedState? {
        val active = currentEntry ?: return null
        return ExportedState(
            frozenBase = frozenBase.toList(),
            current = active,
            currentBaseIndex = currentBaseIndex,
            pastHistory = pastHistory.toList(),
            forwardHistory = forwardHistory.toList(),
            tempQueue = tempNextQueue.toList(),
            upcomingBase = upcomingBase.toList(),
            repeatMode = repeatMode,
            shuffle = shuffle,
        )
    }

    fun restoreState(state: ExportedState) {
        frozenBase = state.frozenBase.toList()
        currentEntry = state.current
        currentBaseIndex = state.currentBaseIndex
        pastHistory.clear()
        pastHistory.addAll(state.pastHistory)
        forwardHistory.clear()
        forwardHistory.addAll(state.forwardHistory)
        tempNextQueue.clear()
        tempNextQueue.addAll(state.tempQueue)
        upcomingBase.clear()
        upcomingBase.addAll(state.upcomingBase)
        repeatMode = state.repeatMode
        shuffle = state.shuffle
    }

    private fun resolveRoundEntry(template: Entry): Entry {
        val pool = template.roadCandidateVariants
        val weights = template.roadCandidateWeights
        val picker = template.roadCandidatePicker
        if (picker != null) {
            picker.invoke()?.let { chosen ->
                return chosen.copy(
                    roadCandidateVariants = pool,
                    roadCandidateWeights = weights,
                    roadCandidatePicker = picker,
                )
            }
        }
        if (pool.isEmpty()) return template
        val picked = if (weights.size == pool.size && weights.any { it > 0 }) {
            val totalWeight = weights.sumOf { it.coerceAtLeast(0) }
            if (totalWeight > 0) {
                var roll = random.nextInt(totalWeight)
                var chosenIdx = pool.lastIndex
                for (i in pool.indices) {
                    val w = weights[i].coerceAtLeast(0)
                    if (roll < w) {
                        chosenIdx = i
                        break
                    }
                    roll -= w
                }
                pool[chosenIdx]
            } else {
                pool[random.nextInt(pool.size)]
            }
        } else {
            pool[random.nextInt(pool.size)]
        }
        return picked.copy(
            roadCandidateVariants = pool,
            roadCandidateWeights = weights,
            roadCandidatePicker = picker,
        )
    }

    private fun rebuildUpcomingAfterStart(startIndex: Int) {
        upcomingBase.clear()
        if (shuffle) {
            val rest = frozenBase.indices
                .filter { it != startIndex }
                .map { resolveRoundEntry(frozenBase[it]) to it }
                .shuffled(random)
            upcomingBase.addAll(rest)
        } else {
            for (i in (startIndex + 1) until frozenBase.size) {
                upcomingBase.addLast(resolveRoundEntry(frozenBase[i]) to i)
            }
        }
    }
}
