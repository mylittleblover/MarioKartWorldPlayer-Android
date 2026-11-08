package com.mylittleblover.mkwplayer.pack

import com.mylittleblover.mkwplayer.library.StageFlow
import com.mylittleblover.mkwplayer.library.StageFlowReader
import java.io.File

/**
 * Turns an installed pack's track into something the player can perform.
 *
 * A pack describes a track by digests and declared roles; the runtime plays a [StageFlow] built from
 * measured parts. This is the join, and it is deliberately narrow: it converts and it refuses, and it
 * never invents.
 *
 * **Every part is checked against the files on disk before the flow is built.** A pack whose manifest lists
 * an asset that is not there would otherwise become a flow the engine fails to bind at the first
 * transition -- after the prelude has already played, which is the worst place to discover it.
 *
 * **The frame count and the loop window come from the file, not from the manifest.** The manifest's numbers
 * were correct when the pack was built; the file's are correct now. When they disagree the file wins and the
 * disagreement is reported, because a manifest that has drifted is worth knowing about and a graph built on
 * drifted numbers seeks to the wrong place.
 */
internal object PackPlayback {

    /** A flow ready to compile, plus what had to be corrected to get there. */
    data class Playable(
        val flow: StageFlow,
        /** Absolute path per asset file name, in the order the compiler must bind them. */
        val bytes: Map<String, ByteArray>,
        /** Non-fatal differences between the manifest and the files, stated rather than hidden. */
        val notes: List<String>,
    ) {
        val hasNotes: Boolean get() = notes.isNotEmpty()
    }

    /** Why a track cannot be played. */
    sealed interface Result {
        data class Ok(val playable: Playable) : Result

        /** [reason] names what is wrong and, where possible, which asset. */
        data class Refused(val reason: String) : Result
    }

    /**
     * Builds the flow for one track of an installed pack.
     *
     * [directory] is the installed pack's root, the one [PackInstaller.Result.Installed] reports.
     */
    fun prepare(manifest: PackManifest, track: PackManifest.Track, directory: File): Result {
        val notes = mutableListOf<String>()
        val parts = mutableListOf<StageFlow.Part>()
        val bytes = mutableMapOf<String, ByteArray>()

        for (part in track.parts) {
            val asset = manifest.asset(part.asset)
                ?: return Result.Refused(
                    "Track '${track.title}' names an asset the manifest does not declare: " +
                        "${part.asset.short()}.",
                )

            val file = File(directory, "audio/${asset.digest.sha256}")
            if (!file.isFile) {
                return Result.Refused(
                    "The installed pack is missing ${part.asset.short()}, so '${track.title}' cannot be " +
                        "played. Re-import the pack.",
                )
            }

            // The file's own measurement, not the manifest's: the manifest is a record of what was true
            // when the pack was built.
            val content = file.readBytes()
            val header = when (val read = com.mylittleblover.mkwplayer.audio.BwavHeader.read(content)) {
                is com.mylittleblover.mkwplayer.audio.BwavHeader.Result.Rejected -> return Result.Refused(
                    "Asset ${part.asset.short()} of '${track.title}' is not a readable BWAV " +
                        "(${read.reason}). The pack is damaged.",
                )
                is com.mylittleblover.mkwplayer.audio.BwavHeader.Result.Ok -> read.info
            }

            if (header.frames.toLong() != asset.frames || header.sampleRate != asset.sampleRate) {
                notes += "The manifest declares ${asset.frames} frames at ${asset.sampleRate} Hz for " +
                    "${part.asset.short()}; the file has ${header.frames} at ${header.sampleRate} Hz. " +
                    "The file is used."
            }

            val fileName = "${part.role.name}_${part.outro ?: 0}_${part.asset.short()}.bwav"
            bytes[fileName] = content
            parts += StageFlow.Part(
                role = part.role.toStageFlowRole(),
                fileName = fileName,
                frames = header.frames.toLong(),
                sampleRate = header.sampleRate,
                channels = header.channels,
                loopStart = header.loopStart?.toLong(),
                loopEnd = header.loopEnd?.toLong(),
                outroNumber = part.outro,
            )
        }

        val timing = track.timing?.toStageFlowTiming()
            ?: if (track.isFlow) {
                return Result.Refused(
                    "Track '${track.title}' is a flow but the pack does not declare its timing, so when " +
                        "the sections change is unknown.",
                )
            } else {
                StageFlow.DEFAULT_TIMING
            }

        val assembled = StageFlowReader.assemble(track.title, parts, timing)
            .getOrElse { error ->
                return Result.Refused(
                    "Track '${track.title}' cannot be assembled: ${error.message ?: "unknown reason"}.",
                )
            }

        return Result.Ok(
            Playable(
                // A pack's timing is declared by its author, which is what PROVISIONAL means here: the
                // screen shows it rather than presenting it as the game's behaviour.
                flow = assembled.copy(provenance = StageFlow.Provenance.PROVISIONAL),
                bytes = bytes,
                notes = notes,
            ),
        )
    }

    /**
     * Every track in the pack that a DSP-ADPCM build can play, in the pack's own order.
     *
     * The order is the pack's, not the build's, because a pack's author arranged the catalogue and
     * reordering it here would present a different library than the one that was built.
     */
    fun playableTracks(manifest: PackManifest): List<PackManifest.Track> {
        val decodable = manifest.assets
            .filter { PackCoverage.isDecodable(it.codecKind) }
            .map { it.digest }
            .toSet()
        return manifest.tracks.filter { track ->
            track.parts.isNotEmpty() && track.parts.all { it.asset in decodable }
        }
    }

    /** Tracks the pack carries that this build cannot decode, with the reason. */
    fun unplayableTracks(manifest: PackManifest): List<PackManifest.Track> {
        val playable = playableTracks(manifest).map { it.key }.toSet()
        return manifest.tracks.filter { it.key !in playable }
    }
}
