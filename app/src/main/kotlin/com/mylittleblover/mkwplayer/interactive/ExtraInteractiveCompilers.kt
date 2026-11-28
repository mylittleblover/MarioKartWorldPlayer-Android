package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import java.security.MessageDigest

internal object ExtraInteractiveCompilers {
    const val TITLE_SEG_EXTENDED = 100
    const val TITLE_SEG_NORMAL = 101
    const val TITLE_SEG_BRASSCUT = 102
    private const val TITLE_SEG_OUTRO_BASE = 110

    const val TWO_STATE_SEG_MAIN = 100
    const val TWO_STATE_SEG_FAST = 101

    private const val PREPARED_BUDGET_BYTES = 96L * 1024 * 1024

    data class CompiledLinearGraph(
        val packet: ByteArray,
        val assets: List<Pair<String, ByteArray>>,
        val totalFrames: Long,
    )

    fun compileTitlePlan(
        item: ExtraMusicCatalog.OtherMusicItem.InteractiveTitle,
        bytesByStem: Map<String, ByteArray>,
        initialSegment: Int = TITLE_SEG_NORMAL,
        segmentSwitchFadeOutMs: Int = 0,
    ): InteractivePlaybackPlan {
        val outroRefs = item.outros.map { it.asset }
        val orderedRefs = listOf(item.harmonica, item.main, item.brassCut) + outroRefs
        val assetSpecs = orderedRefs.map { ref ->
            val path = "${ref.stem}.bwav"
            val bytes = bytesByStem[ref.stem] ?: bytesByStem.getValue(path)
            G.Asset(
                path = path,
                sampleRate = ref.sampleRate,
                channels = projectedChannels(ref.channels),
                frames = ref.frames,
                sha256 = sha256(bytes),
                lookAheadFrames = ref.sampleRate * 2L,
            )
        }

        val harmonicaSeg = G.Segment(
            index = TITLE_SEG_EXTENDED,
            entry = 0L,
            exit = item.harmonica.frames,
            loop = null,
        )
        val mainCues = item.outros.mapIndexed { idx, outro ->
            G.Cue(500 + idx, outro.cueName, outro.mainExitFrame)
        }.sortedBy { it.frame }
        val mainSeg = G.Segment(
            index = TITLE_SEG_NORMAL,
            entry = 0L,
            exit = item.main.frames,
            loop = item.main.toLoop(),
            cues = mainCues,
        )
        val brassCutCues = item.outros.mapIndexed { idx, outro ->
            G.Cue(510 + idx, outro.cueName, outro.brassCutExitFrame)
        }.sortedBy { it.frame }
        val brassCutSeg = G.Segment(
            index = TITLE_SEG_BRASSCUT,
            entry = 0L,
            exit = item.brassCut.frames,
            loop = item.brassCut.toLoop(),
            cues = brassCutCues,
        )
        val outroSegs = item.outros.mapIndexed { idx, outro ->
            G.Segment(
                index = TITLE_SEG_OUTRO_BASE + idx,
                entry = 0L,
                exit = outro.asset.frames,
                loop = null,
            )
        }

        val tracks = buildList {
            add(G.Track(200, TITLE_SEG_EXTENDED, listOf(G.Clip(300, 0, sourceLength = item.harmonica.frames))))
            add(G.Track(201, TITLE_SEG_NORMAL, listOf(G.Clip(301, 1, sourceLength = item.main.frames))))
            add(G.Track(202, TITLE_SEG_BRASSCUT, listOf(G.Clip(302, 2, sourceLength = item.brassCut.frames))))
            item.outros.forEachIndexed { idx, outro ->
                add(
                    G.Track(
                        index = 210 + idx,
                        segment = TITLE_SEG_OUTRO_BASE + idx,
                        clips = listOf(G.Clip(310 + idx, 3 + idx, sourceLength = outro.asset.frames)),
                    ),
                )
            }
        }

        val transitions = buildList {
            // Harmonica plays first and chains seamlessly into the normal (unprefixed) Title Theme.
            add(
                G.Transition(
                    index = 400,
                    sources = listOf(TITLE_SEG_EXTENDED),
                    destinations = listOf(TITLE_SEG_NORMAL),
                    sync = G.SyncPoint.ExitCue,
                    entry = G.EntryMode.EntryCue,
                    postExit = true,
                    timeline = G.TransitionTimeline(0, 0, 0, 0),
                ),
            )
            var nextTransId = 410
            item.outros.forEachIndexed { idx, outro ->
                val outroSegId = TITLE_SEG_OUTRO_BASE + idx
                add(
                    G.Transition(
                        index = nextTransId++,
                        sources = listOf(TITLE_SEG_NORMAL),
                        destinations = listOf(outroSegId),
                        sync = G.SyncPoint.NextCustomCue,
                        sourceCue = outro.cueName,
                        entry = G.EntryMode.EntryCue,
                    ),
                )
                add(
                    G.Transition(
                        index = nextTransId++,
                        sources = listOf(TITLE_SEG_BRASSCUT),
                        destinations = listOf(outroSegId),
                        sync = G.SyncPoint.NextCustomCue,
                        sourceCue = outro.cueName,
                        entry = G.EntryMode.EntryCue,
                    ),
                )
            }
        }

        val spec = G.Spec(
            assets = assetSpecs,
            segments = listOf(harmonicaSeg, mainSeg, brassCutSeg) + outroSegs,
            tracks = tracks,
            transitions = transitions,
            bpm = 140.0,
        )
        val packet = G.build(spec)
        val assetPaths = assetSpecs.map { it.path }

        val outroPaths = outroRefs.map { "${it.stem}.bwav" }
        val extendedPrepared = (listOf("${item.harmonica.stem}.bwav", "${item.main.stem}.bwav") + outroPaths).toSet()
        val normalPrepared = (listOf("${item.main.stem}.bwav") + outroPaths).toSet()
        val brassCutPrepared = (listOf("${item.brassCut.stem}.bwav") + outroPaths).toSet()
        val entryPrepared = mapOf(
            TITLE_SEG_EXTENDED to extendedPrepared,
            TITLE_SEG_NORMAL to normalPrepared,
            TITLE_SEG_BRASSCUT to brassCutPrepared,
        )
        val chosenEntry = if (initialSegment in entryPrepared) initialSegment else TITLE_SEG_NORMAL

        return InteractivePlaybackPlan.applySwitchFadeToPlan(
            InteractivePlaybackPlan(
                name = item.id,
                spec = spec,
                packet = packet,
                assetPaths = assetPaths,
                entry = chosenEntry,
                next = mapOf(TITLE_SEG_EXTENDED to TITLE_SEG_NORMAL),
                outros = item.outros.mapIndexed { idx, outro -> outro.index to (TITLE_SEG_OUTRO_BASE + idx) }.toMap(),
                fast = null,
                labels = mapOf(
                    TITLE_SEG_EXTENDED to "TitleExtended",
                    TITLE_SEG_NORMAL to "TitleNormal",
                    TITLE_SEG_BRASSCUT to "TitleBrassCut",
                ) + item.outros.mapIndexed { idx, outro ->
                    (TITLE_SEG_OUTRO_BASE + idx) to "Outro%02d".format(outro.index)
                }.toMap(),
                streamed = true,
                preparedAssets = entryPrepared.getValue(chosenEntry),
                entryPreparedAssets = entryPrepared,
                entryAutomaticNext = mapOf(TITLE_SEG_EXTENDED to TITLE_SEG_NORMAL),
                customInitialSegments = listOf(TITLE_SEG_EXTENDED, TITLE_SEG_NORMAL, TITLE_SEG_BRASSCUT),
            ),
            segmentSwitchFadeOutMs,
        )
    }

    fun compileTwoStateLoopPlan(
        track: ExtraMusicCatalog.TwoStateTrack,
        isBattle: Boolean,
        bytesByStem: Map<String, ByteArray>,
        initialSegment: Int = TWO_STATE_SEG_MAIN,
        perfectBeatEnabled: Boolean = false,
        segmentSwitchFadeOutMs: Int = 0,
    ): InteractivePlaybackPlan {
        val mainPath = "${track.main.stem}.bwav"
        val fastPath = "${track.fast.stem}.bwav"
        val mainBytes = bytesByStem[track.main.stem] ?: bytesByStem.getValue(mainPath)
        val fastBytes = bytesByStem[track.fast.stem] ?: bytesByStem.getValue(fastPath)

        val assets = listOf(
            G.Asset(mainPath, track.main.sampleRate, projectedChannels(track.main.channels), track.main.frames, sha256(mainBytes), lookAheadFrames = track.main.sampleRate * 2L),
            G.Asset(fastPath, track.fast.sampleRate, projectedChannels(track.fast.channels), track.fast.frames, sha256(fastBytes), lookAheadFrames = track.fast.sampleRate * 2L),
        )
        val mainSeg = G.Segment(
            index = TWO_STATE_SEG_MAIN,
            entry = 0L,
            exit = track.main.frames,
            loop = track.main.toLoop(),
        )
        val fastSeg = G.Segment(
            index = TWO_STATE_SEG_FAST,
            entry = 0L,
            exit = track.fast.frames,
            loop = track.fast.toLoop(),
        )
        val tracks = listOf(
            G.Track(200, TWO_STATE_SEG_MAIN, listOf(G.Clip(300, 0, sourceLength = track.main.frames))),
            G.Track(201, TWO_STATE_SEG_FAST, listOf(G.Clip(301, 1, sourceLength = track.fast.frames))),
        )
        val transition = if (isBattle) {
            // Verified v1.8.0 (0x0243a478): Battle final minute switches directly to BattleFL without FinalLapFF.
            G.Transition(
                index = 400,
                sources = listOf(TWO_STATE_SEG_MAIN),
                destinations = listOf(TWO_STATE_SEG_FAST),
                sync = if (perfectBeatEnabled) G.SyncPoint.NextBar else G.SyncPoint.Immediate,
                entry = G.EntryMode.EntryCue,
                fadeOut = G.Fade(beats = FinalLapFanfareCompiler.FULL_FADE_OUT_SEC * 140.0 / 60.0, offset = 0.0, shape = G.Shape.Linear),
                postExit = true,
            )
        } else {
            G.Transition(
                index = 400,
                sources = listOf(TWO_STATE_SEG_MAIN),
                destinations = listOf(TWO_STATE_SEG_FAST),
                sync = if (perfectBeatEnabled) G.SyncPoint.NextBar else G.SyncPoint.NextBeat,
                entry = G.EntryMode.EntryCue,
                fadeOut = G.Fade(beats = 0.5),
                postExit = true,
            )
        }
        val spec = G.Spec(
            assets = assets,
            segments = listOf(mainSeg, fastSeg),
            tracks = tracks,
            transitions = listOf(transition),
            bpm = 140.0,
        )
        val packet = G.build(spec)
        val totalPcmBytes =
            track.main.frames * track.main.channels * 4L + track.fast.frames * track.fast.channels * 4L
        val allPrepared = setOf(mainPath, fastPath)
        val entryPrepared = if (totalPcmBytes <= PREPARED_BUDGET_BYTES) {
            mapOf(TWO_STATE_SEG_MAIN to allPrepared, TWO_STATE_SEG_FAST to allPrepared)
        } else {
            mapOf(TWO_STATE_SEG_MAIN to setOf(mainPath), TWO_STATE_SEG_FAST to setOf(fastPath))
        }
        val chosenEntry = if (initialSegment == TWO_STATE_SEG_FAST) TWO_STATE_SEG_FAST else TWO_STATE_SEG_MAIN

        val basePlan = InteractivePlaybackPlan(
            name = track.id,
            spec = spec,
            packet = packet,
            assetPaths = listOf(mainPath, fastPath),
            entry = chosenEntry,
            next = emptyMap(),
            outros = emptyMap(),
            fast = TWO_STATE_SEG_FAST,
            labels = mapOf(
                TWO_STATE_SEG_MAIN to "Main",
                TWO_STATE_SEG_FAST to if (isBattle) "BattleFast" else "Fast",
            ),
            streamed = true,
            preparedAssets = entryPrepared.getValue(chosenEntry),
            entryPreparedAssets = entryPrepared,
            customInitialSegments = listOf(TWO_STATE_SEG_MAIN, TWO_STATE_SEG_FAST),
        )
        val extendedPlan = if (isBattle) {
            basePlan
        } else {
            val ffBytes = bytesByStem[FinalLapFanfareCompiler.ASSET]
                ?: bytesByStem[FinalLapFanfareCompiler.ASSET.removeSuffix(".bwav")]
            val extendBytes = if (ffBytes != null) {
                bytesByStem + (FinalLapFanfareCompiler.ASSET to ffBytes)
            } else {
                bytesByStem
            }
            FinalLapFanfareCompiler.extend(basePlan, extendBytes, perfectBeatEnabled = perfectBeatEnabled)
        }
        return InteractivePlaybackPlan.applySwitchFadeToPlan(
            extendedPlan,
            segmentSwitchFadeOutMs,
        )
    }

    fun compileOverlaidOpeningGraph(
        item: ExtraMusicCatalog.OtherMusicItem.OverlaidOpening,
        opBytes: ByteArray,
        telopBytes: ByteArray,
    ): CompiledLinearGraph {
        val opPath = "${item.op.stem}.bwav"
        val telopPath = "${item.telop.stem}.bwav"
        val totalFrames = maxOf(item.totalFrames, item.op.frames, item.deltaFrames + item.telop.frames)
        val assets = listOf(
            G.Asset(opPath, item.op.sampleRate, projectedChannels(item.op.channels), item.op.frames, sha256(opBytes), lookAheadFrames = item.op.sampleRate * 2L),
            G.Asset(telopPath, item.telop.sampleRate, projectedChannels(item.telop.channels), item.telop.frames, sha256(telopBytes), lookAheadFrames = item.telop.sampleRate * 2L),
        )
        val segment = G.Segment(index = 100, entry = 0L, exit = totalFrames, loop = null)
        val tracks = listOf(
            G.Track(
                index = 200,
                segment = 100,
                clips = listOf(G.Clip(300, 0, start = 0L, sourceOffset = 0L, sourceLength = item.op.frames)),
            ),
            G.Track(
                index = 201,
                segment = 100,
                clips = listOf(
                    G.Clip(
                        index = 301,
                        asset = 1,
                        start = item.deltaFrames,
                        sourceOffset = 0L,
                        sourceLength = item.telop.frames,
                    ),
                ),
            ),
        )
        val spec = G.Spec(
            assets = assets,
            segments = listOf(segment),
            tracks = tracks,
            transitions = emptyList(),
            transportSampleRate = item.op.sampleRate,
        )
        return CompiledLinearGraph(
            packet = G.build(spec),
            assets = listOf(opPath to opBytes, telopPath to telopBytes),
            totalFrames = totalFrames,
        )
    }

    fun compileChainedSequenceGraph(
        item: ExtraMusicCatalog.OtherMusicItem.ChainedSequence,
        bytesByStem: Map<String, ByteArray>,
    ): CompiledLinearGraph {
        val uniqueAssets = linkedMapOf<String, Pair<ExtraMusicCatalog.AssetRef, ByteArray>>()
        item.parts.forEach { part ->
            val path = "${part.asset.stem}.bwav"
            if (path !in uniqueAssets) {
                val bytes = bytesByStem[part.asset.stem] ?: bytesByStem.getValue(path)
                uniqueAssets[path] = part.asset to bytes
            }
        }
        val pathToIndex = uniqueAssets.keys.mapIndexed { idx, path -> path to idx }.toMap()
        val assetSpecs = uniqueAssets.entries.map { (path, pair) ->
            val (ref, bytes) = pair
            G.Asset(path, ref.sampleRate, projectedChannels(ref.channels), ref.frames, sha256(bytes), lookAheadFrames = ref.sampleRate * 2L)
        }

        var cursor = 0L
        val tracks = item.parts.mapIndexed { idx, part ->
            val path = "${part.asset.stem}.bwav"
            val assetIdx = pathToIndex.getValue(path)
            val clip = G.Clip(
                index = 300 + idx,
                asset = assetIdx,
                start = cursor,
                sourceOffset = part.sourceOffset,
                sourceLength = part.sourceLength,
            )
            cursor += part.sourceLength
            G.Track(index = 200 + idx, segment = 100, clips = listOf(clip))
        }
        val totalFrames = maxOf(item.totalFrames, cursor)
        val segment = G.Segment(index = 100, entry = 0L, exit = totalFrames, loop = null)
        val sampleRate = item.parts.firstOrNull()?.asset?.sampleRate ?: 48000
        val spec = G.Spec(
            assets = assetSpecs,
            segments = listOf(segment),
            tracks = tracks,
            transitions = emptyList(),
            transportSampleRate = sampleRate,
        )
        return CompiledLinearGraph(
            packet = G.build(spec),
            assets = uniqueAssets.entries.map { (path, pair) -> path to pair.second },
            totalFrames = totalFrames,
        )
    }

    private fun projectedChannels(channels: Int): Int =
        if (channels > 2) 2 else channels

    private fun ExtraMusicCatalog.AssetRef.toLoop(): G.Loop? {
        val start = loopStart ?: return null
        val end = loopEnd ?: return null
        return if (start in 0 until end && end <= frames) G.Loop(start, end) else null
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
