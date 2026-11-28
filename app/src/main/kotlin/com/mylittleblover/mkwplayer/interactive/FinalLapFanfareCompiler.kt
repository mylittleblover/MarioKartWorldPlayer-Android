package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.graph.MusicGraphBuilder as G
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sin

/** Ordinary courses only. Rainbow has its own independently verified fanfare graph. */
internal object FinalLapFanfareCompiler {
    const val ASSET = "BGM_Seq_FinalLapFF.bwav"
    // Verified v1.8.0 Default.game__SoundBgmSetting.bgyml values at 48 kHz:
    // stopAllBgm fadeout = 0.10s (4,800 f), FinalLapFanfareDelay = 0.133s (6,384 f),
    // FastDelay = 4.05s (194,400 f; i.e. 188,016 f after FinalLapFF frame 0).
    const val FULL_FADE_OUT_SEC = 0.10
    const val FULL_FADE_OUT_FRAMES = 4_800L
    const val FANFARE_DELAY_FRAMES = 6_384L
    const val FAST_DELAY_FRAMES = 194_400L
    const val FANFARE_EXIT_FRAMES = FAST_DELAY_FRAMES - FANFARE_DELAY_FRAMES
    internal const val SHA256 = "60790cdf84a2e04c9fffca7ccee8bafa5be2bfb6ceb6dfd4f98ecfc8bdd8ce94"

    /**
     * Synthesizes a minimal valid 48 kHz stereo planar-PCM16 BWAV (codec 0) so medleys that span
     * the FinalLapFF slot continue smoothly even when an imported partial `.mkwpack` omits it.
     */
    fun silentFallbackBwav(frames: Int = 268_759, sampleRate: Int = 48_000): ByteArray =
        createPlanarPcm16Bwav(frames = frames, sampleRate = sampleRate, toneHz = 0.0)

    fun createPlanarPcm16Bwav(
        frames: Int = 48_000,
        sampleRate: Int = 48_000,
        channels: Int = 2,
        toneHz: Double = 0.0,
        amplitude: Double = 0.25,
        looping: Boolean = false,
    ): ByteArray {
        val alignedFrames = if ((frames and 1) == 0) frames else frames + 1
        val headerEnd = 16 + channels * 76
        val channelBytes = alignedFrames * 2
        val out = ByteArray(headerEnd + channels * channelBytes)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        out[0] = 'B'.code.toByte()
        out[1] = 'W'.code.toByte()
        out[2] = 'A'.code.toByte()
        out[3] = 'V'.code.toByte()
        buf.putShort(4, 0xFEFF.toShort())
        buf.putShort(6, 1)
        buf.putShort(12, 0)
        buf.putShort(14, channels.toShort())
        for (ch in 0 until channels) {
            val h = 16 + ch * 76
            val dataOffset = headerEnd + ch * channelBytes
            buf.putShort(h, 0) // codec = 0 (planar PCM16)
            buf.putShort(h + 2, ch.toShort())
            buf.putInt(h + 4, sampleRate)
            buf.putInt(h + 8, alignedFrames)
            buf.putInt(h + 12, alignedFrames)
            buf.putInt(h + 48, dataOffset)
            buf.putInt(h + 52, dataOffset)
            buf.putInt(h + 56, 1)
            buf.putInt(h + 60, if (looping) alignedFrames else -1)
            buf.putInt(h + 64, 0)
            if (toneHz > 0.0) {
                for (i in 0 until alignedFrames) {
                    val sample = (sin(2.0 * Math.PI * toneHz * i / sampleRate) * amplitude * 32767.0)
                        .toInt().coerceIn(-32768, 32767).toShort()
                    buf.putShort(dataOffset + i * 2, sample)
                }
            }
        }
        return out
    }
    @Volatile
    private var verifiedAssetBytes: ByteArray? = null
    @Volatile
    private var verifiedDigest: ByteArray? = null

    fun extend(
        base: InteractivePlaybackPlan,
        bytes: Map<String,ByteArray>,
        perfectBeatEnabled: Boolean = false,
    ): InteractivePlaybackPlan {
        val fastTargets=(listOfNotNull(base.fast)+base.versions.mapNotNull { it.fast }).toSet()
        val rules=base.spec.transitions.filter { rule ->
            rule.entry!=G.EntryMode.SameTime && rule.sources.none { it in fastTargets } &&
                rule.destinations.singleOrNull() in fastTargets
        }
        if(rules.isEmpty()) return base
        // Offline research graphs may omit shared assets. Android always supplies the bundled source.
        val data=bytes[ASSET] ?: return base
        require(base.name!="Rainbow") { "Rainbow must keep its dedicated FinalLapFanfare" }
        val digest = if (data === verifiedAssetBytes && verifiedDigest != null) {
            verifiedDigest!!
        } else {
            val computed = MessageDigest.getInstance("SHA-256").digest(data)
            require(computed.joinToString("") { "%02x".format(it.toInt() and 255) } == SHA256) { "Unexpected FinalLapFF source" }
            verifiedDigest = computed
            verifiedAssetBytes = data
            computed
        }
        val info=(BwavHeader.read(data) as? BwavHeader.Result.Ok)?.info ?: error("Unreadable FinalLapFF")
        require(info.sampleRate==48000 && info.channels==2 && info.frames.toLong()==268759L)
        require(base.spec.transportSampleRate==48000)
        var id=(base.spec.segments.map { it.index }+base.spec.tracks.map { it.index }+
            base.spec.transitions.map { it.index }+base.spec.segments.flatMap { it.cues.map { c -> c.index } }+
            base.spec.tracks.flatMap { it.clips.map { c -> c.index } }).max()+1
        val ff=id++
        val segmentByIndex = base.spec.segments.associateBy { it.index }
        fun downbeatSyncFor(sourceId: Int?): Pair<G.SyncPoint, String?> {
            if (!perfectBeatEnabled) return G.SyncPoint.Immediate to null
            val seg = sourceId?.let { segmentByIndex[it] }
            val cueName = seg?.let { s ->
                listOf("beat1.exit", "fast.exit", "pre.exit", "region.exit", "bar.exit")
                    .firstOrNull { name -> s.cues.any { it.name == name } }
            }
            return if (cueName != null) {
                G.SyncPoint.NextCustomCue to cueName
            } else {
                G.SyncPoint.NextBar to null
            }
        }
        val fullFadeOut = G.Fade(
            beats = FULL_FADE_OUT_SEC * base.spec.bpm / 60.0,
            offset = 0.0,
            shape = G.Shape.Linear,
        )
        val spec=base.spec.copy(
            assets=base.spec.assets+G.Asset(ASSET,48000,2,268759,digest,lookAheadFrames=48000L*2L),
            segments=base.spec.segments+G.Segment(ff,exit=FANFARE_EXIT_FRAMES),
            tracks=base.spec.tracks+G.Track(id++,ff,listOf(G.Clip(id++,base.spec.assets.size,sourceLength=268759))),
            transitions=base.spec.transitions.map { rule ->
                if(rule !in rules) rule else {
                    val (syncPoint, cueName) = downbeatSyncFor(rule.sources.firstOrNull())
                    rule.copy(sync=syncPoint,sourceCue=cueName,
                        bridge=ff,fadeOut=fullFadeOut,fadeIn=G.Fade(),postExit=true,preEntry=false,
                        timeline=G.TransitionTimeline(FANFARE_DELAY_FRAMES,FAST_DELAY_FRAMES,FAST_DELAY_FRAMES,0L))
                }
            },
        )
        // Outgoing Full fades out over 0.10s (4,800 f) from sourceSync while FinalLapFF enters at
        // +0.133s (6,384 f) and Fast enters at +4.05s (194,400 f); postExit retains FF's natural tail.
        val packet=G.build(spec)
        val paths=base.assetPaths+ASSET
        val bridgeSources = rules.flatMapTo(mutableSetOf()) { it.sources }
        val updatedEntryPrepared = base.entryPreparedAssets.mapValues { (segment, assets) ->
            if (segment in bridgeSources) assets + ASSET else assets
        }
        return base.copy(spec=spec,packet=packet,assetPaths=paths,labels=base.labels+(ff to "FinalLapFF"),
            preparedAssets=base.preparedAssets+ASSET,
            entryPreparedAssets=updatedEntryPrepared,
            standard=base.standard?.copy(packet=packet,assetPaths=paths))
    }
}
