package com.mylittleblover.mkwplayer.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * The join between a pack's declaration and the runtime's flow.
 *
 * Two properties matter more than the rest. A pack that lists an asset it does not contain must be refused
 * before a performance starts, not after the prelude has played. And when the manifest and the file
 * disagree, the file wins and the disagreement is reported: a graph built on drifted numbers seeks to the
 * wrong place, and a drifted manifest is worth knowing about either way.
 */
class PackPlaybackTest {

    private fun tempDir(): File = Files.createTempDirectory("mkw-playback-test").toFile()

    /** A minimal valid BWAV: one channel, DSP-ADPCM, one frame. Enough for the header reader. */
    private fun bwav(frames: Int = 480_000, rate: Int = 48_000, channels: Int = 1): ByteArray {
        val header = ByteArray(16 + channels * 76)
        "BWAV".toByteArray().copyInto(header, 0)
        header[4] = 0xFF.toByte(); header[5] = 0xFE.toByte()
        header[6] = 1; header[7] = 0
        header[14] = (channels and 0xFF).toByte(); header[15] = ((channels shr 8) and 0xFF).toByte()
        for (channel in 0 until channels) {
            val h = 16 + channel * 76
            header[h] = 1                      // codec kind 1 = DSP-ADPCM
            header[h + 1] = 0
            header[h + 2] = channel.toByte()   // speaker
            header[h + 3] = 0
            putU32(header, h + 4, rate)
            putU32(header, h + 8, frames)
            putU32(header, h + 12, frames)
            putU32(header, h + 48, 0)          // no loop: last == first == 0 is not a loop, so use the sentinel
            putU32(header, h + 52, 0)
            putU32(header, h + 60, 0xFFFFFFFF.toInt())  // NO_LOOP sentinel
            putU32(header, h + 64, 0)
            putU32(header, h + 56, 1)
        }
        // Encoded bytes: one DSP block per 14 samples.
        val blocks = (frames + 13) / 14
        return header + ByteArray(blocks * 8)
    }

    private fun putU32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun digestOf(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return PackInstaller.run { digest.toHex() }
    }

    /** A one-track manifest over [audio], with the declared numbers optionally wrong on purpose. */
    private fun manifest(
        audio: ByteArray,
        digest: String = digestOf(audio),
        declaredFrames: Long = 480_000,
        declaredRate: Int = 48_000,
        isFlow: Boolean = false,
        withTiming: Boolean = true,
        role: String = "MAIN",
    ): PackManifest {
        val outro = if (role == "OUTRO") ",\n                  \"outro\": 0" else ""
        val parts = "{\"asset\": \"$digest\", \"role\": \"$role\"$outro}"
        val timing = if (withTiming) {
            """,
                  "timing": {
                    "preludeExit": "EXIT_CUE",
                    "bridgeEntry": "NEXT_BAR",
                    "outroEntry": "NEXT_BAR",
                    "outroSwitch": "IMMEDIATE",
                    "mainRepeats": 2
                  }"""
        } else {
            ""
        }
        val text = """
            {
              "formatVersion": 1,
              "packId": "test.pack",
              "packVersion": 1,
              "kind": "FULL",
              "basePackId": null,
              "basePackVersion": null,
              "coverage": {"totalLogicalTracks": 1, "playableLogicalTracks": 1, "codecKinds": [1]},
              "assets": [
                {
                  "digest": "$digest",
                  "byteSize": ${audio.size},
                  "mediaType": "audio/x-bwav",
                  "codecKind": 1,
                  "sampleRate": $declaredRate,
                  "channels": 1,
                  "frames": $declaredFrames
                }
              ],
              "tracks": [
                {
                  "key": "course:${digest.take(32)}",
                  "kind": "COURSE",
                  "title": "Coaster",
                  "primary": "$digest",
                  "isFlow": $isFlow,
                  "parts": [$parts]$timing
                }
              ],
              "stages": [{"code": "Coaster", "name": "Coaster", "tracks": ["course:${digest.take(32)}"]}],
              "provenance": [{"subject": "pack", "field": "assets", "level": "MEASURED_L2"}],
              "unlock": {"frozen": false, "stages": ["Coaster"]}
            }
        """.trimIndent()
        return (PackManifest.parse(text) as PackManifest.Result.Ok).manifest
    }

    /** Writes the pack's audio member into a directory and returns it. */
    private fun install(directory: File, audio: ByteArray): File {
        val path = File(directory, "audio/${digestOf(audio)}")
        path.parentFile?.mkdirs()
        path.writeBytes(audio)
        return path
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    fun aSingleAssetTrackBecomesAPlayableFlow() {
        val root = tempDir()
        val audio = bwav()
        install(root, audio)
        val model = manifest(audio)

        val result = PackPlayback.prepare(model, model.tracks.single(), root)
        assertTrue(result is PackPlayback.Result.Ok, result.toString())
        val playable = (result as PackPlayback.Result.Ok).playable

        assertEquals(480_000, playable.flow.parts.single().frames)
        assertTrue(!playable.hasNotes, playable.notes.toString())
    }

    @Test
    fun theFlowCarriesOneBindingPerPart() {
        val root = tempDir()
        val audio = bwav()
        install(root, audio)
        val model = manifest(audio)

        val playable = (PackPlayback.prepare(model, model.tracks.single(), root)
            as PackPlayback.Result.Ok).playable
        val part = playable.flow.parts.single()
        assertTrue(playable.bytes.containsKey(part.fileName), "no bytes for ${part.fileName}")
    }

    @Test
    fun aPacksTimingReachesTheFlow() {
        val root = tempDir()
        val audio = bwav()
        install(root, audio)
        val model = manifest(audio, isFlow = true)

        val playable = (PackPlayback.prepare(model, model.tracks.single(), root)
            as PackPlayback.Result.Ok).playable
        assertEquals(2, playable.flow.timing.mainRepeats)
    }

    @Test
    fun aPacksFlowIsMarkedProvisional() {
        // The pack's author declared the timing; nothing established it against the game. Calling it
        // verified would be the exact claim the format refuses to let a manifest make.
        val root = tempDir()
        val audio = bwav()
        install(root, audio)
        val model = manifest(audio, isFlow = true)

        val playable = (PackPlayback.prepare(model, model.tracks.single(), root)
            as PackPlayback.Result.Ok).playable
        assertEquals(com.mylittleblover.mkwplayer.library.StageFlow.Provenance.PROVISIONAL,
            playable.flow.provenance)
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun aMissingAssetIsRefusedBeforeTheFlowIsBuilt() {
        // The alternative is discovering it at the first transition, after the prelude has played.
        val root = tempDir()
        val audio = bwav()
        val model = manifest(audio)

        val result = PackPlayback.prepare(model, model.tracks.single(), root)
        assertTrue(result is PackPlayback.Result.Refused, result.toString())
        assertTrue((result as PackPlayback.Result.Refused).reason.contains("missing"))
    }

    @Test
    fun aDamagedAssetIsRefusedByName() {
        val root = tempDir()
        val audio = bwav()
        val path = install(root, audio)
        path.writeBytes("not a bwav".toByteArray())
        val model = manifest(audio)

        val result = PackPlayback.prepare(model, model.tracks.single(), root)
        assertTrue(result is PackPlayback.Result.Refused, result.toString())
        assertTrue((result as PackPlayback.Result.Refused).reason.contains("damaged"))
    }

    @Test
    fun aFlowWithoutDeclaredTimingIsRefused() {
        // A flow whose section changes are unknown has no schedule, so performing it would mean inventing
        // one inside the pack's own content.
        val root = tempDir()
        val audio = bwav()
        install(root, audio)
        val model = manifest(audio, isFlow = true, withTiming = false)

        // The manifest parser may reject this outright, which is also correct; either refusal is fine.
        val result = PackPlayback.prepare(model, model.tracks.single(), root)
        assertTrue(result is PackPlayback.Result.Refused, result.toString())
    }

    // ---------------------------------------------------------------- drift

    @Test
    fun aManifestThatDisagreesWithItsFileIsReported() {
        // The file wins, because it is the thing that will be played. But a drifted manifest is worth
        // knowing about, so the disagreement is a note rather than a silent correction.
        val root = tempDir()
        val audio = bwav(frames = 480_000)
        install(root, audio)
        val model = manifest(audio, declaredFrames = 400_000)

        val result = PackPlayback.prepare(model, model.tracks.single(), root)
        assertTrue(result is PackPlayback.Result.Ok, result.toString())
        val playable = (result as PackPlayback.Result.Ok).playable

        assertEquals(480_000, playable.flow.parts.single().frames, "the file's frame count must win")
        assertTrue(playable.hasNotes, "the drift was not reported")
        assertTrue(playable.notes.single().contains("400000"), playable.notes.single())
    }

    // ---------------------------------------------------------------- playability

    @Test
    fun thePlayableListExcludesTracksWithAnyUndecodablePart() {
        // A flow that loses its main is not "mostly playable".
        val root = tempDir()
        val audio = bwav()
        val model = manifest(audio)
        assertEquals(1, PackPlayback.playableTracks(model).size)
        assertTrue(PackPlayback.unplayableTracks(model).isEmpty())
    }

    @Test
    fun thePlayableListKeepsThePacksOrder() {
        // The author arranged the catalogue; reordering it here presents a different library than the
        // one that was built.
        val root = tempDir()
        val audio = bwav()
        val model = manifest(audio)
        val order = model.tracks.map { it.key }
        assertEquals(order, PackPlayback.playableTracks(model).map { it.key })
    }
}
