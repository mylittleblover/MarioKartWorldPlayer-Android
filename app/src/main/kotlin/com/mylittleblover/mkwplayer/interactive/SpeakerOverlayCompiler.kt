package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog
import java.security.MessageDigest

/**
 * Adds sample-locked Koopa Troopa Beach Speaker overlay tracks into Main and Fast segments.
 *
 * The Speaker tracks share identical frame counts and loop bounds with Main and Fast, so toggling
 * layer gain between 0 dB and -96 dB reveals/hides the speaker overlay in exact phase lock.
 */
internal object SpeakerOverlayCompiler {
    fun extend(
        base: InteractivePlaybackPlan,
        config: ExtraMusicCatalog.KoopaSpeakerConfig,
        normalBytes: ByteArray,
        fastBytes: ByteArray,
    ): InteractivePlaybackPlan {
        val normalPath = "${config.normalAsset.stem}.bwav"
        val fastPath = "${config.fastAsset.stem}.bwav"

        val normalDigest = sha256(normalBytes)
        val fastDigest = sha256(fastBytes)

        val normalAssetIdx = base.spec.assets.size
        val fastAssetIdx = normalAssetIdx + 1

        val usedIds = buildSet {
            base.spec.assets.indices.forEach { add(it) }
            base.spec.segments.forEach { s -> add(s.index); s.cues.forEach { add(it.index) } }
            base.spec.tracks.forEach { t -> add(t.index); t.clips.forEach { add(it.index) } }
            base.spec.transitions.forEach { add(it.index) }
        }
        var nextId = maxOf((usedIds.maxOrNull() ?: 0) + 1, fastAssetIdx + 1)

        val mainAssetIndices = base.spec.assets.withIndex()
            .filter { (_, a) -> a.path.endsWith("_KoopaTroopaBeach.bwav") || a.path == "BGM_Stage_KoopaTroopaBeach.bwav" }
            .map { it.index }
            .toSet()
        val fastAssetIndices = base.spec.assets.withIndex()
            .filter { (_, a) -> a.path.endsWith("_KoopaTroopaBeach_Fast.bwav") || a.path == "BGM_Stage_KoopaTroopaBeach_Fast.bwav" }
            .map { it.index }
            .toSet()

        val overlayTracks = mutableListOf<G.Track>()
        val overlayTrackIds = mutableListOf<Int>()

        for (track in base.spec.tracks) {
            val isMainTrack = track.clips.any { it.asset in mainAssetIndices }
            val isFastTrack = track.clips.any { it.asset in fastAssetIndices }
            if (!isMainTrack && !isFastTrack) continue

            val speakerIdx = if (isMainTrack) normalAssetIdx else fastAssetIdx
            val speakerFrames = if (isMainTrack) config.normalAsset.frames else config.fastAsset.frames
            val trackIdx = nextId++
            val clonedClips = track.clips.map { clip ->
                require(clip.sourceOffset + clip.sourceLength <= speakerFrames) {
                    "Speaker overlay bounds mismatch for ${if (isMainTrack) normalPath else fastPath}"
                }
                clip.copy(
                    index = nextId++,
                    asset = speakerIdx,
                    gainDb = 0.0,
                )
            }
            overlayTracks += G.Track(
                index = trackIdx,
                segment = track.segment,
                clips = clonedClips,
            )
            overlayTrackIds += trackIdx
        }

        require(overlayTracks.isNotEmpty()) { "No KoopaTroopaBeach Main/Fast tracks found for speaker overlay" }

        val newAssets = base.spec.assets + listOf(
            G.Asset(
                path = normalPath,
                sampleRate = config.normalAsset.sampleRate,
                channels = config.normalAsset.channels,
                frames = config.normalAsset.frames,
                sha256 = normalDigest,
                lookAheadFrames = config.normalAsset.sampleRate * 2L,
            ),
            G.Asset(
                path = fastPath,
                sampleRate = config.fastAsset.sampleRate,
                channels = config.fastAsset.channels,
                frames = config.fastAsset.frames,
                sha256 = fastDigest,
                lookAheadFrames = config.fastAsset.sampleRate * 2L,
            ),
        )
        val spec = base.spec.copy(
            assets = newAssets,
            tracks = base.spec.tracks + overlayTracks,
        )
        val packet = G.build(spec)
        val paths = base.assetPaths + listOf(normalPath, fastPath)
        val extraPrepared = setOf(normalPath, fastPath)
        return base.copy(
            spec = spec,
            packet = packet,
            assetPaths = paths,
            preparedAssets = base.preparedAssets + extraPrepared,
            overlayTrackIndices = base.overlayTrackIndices + overlayTrackIds,
            standard = base.standard?.copy(packet = packet, assetPaths = paths),
        )
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
