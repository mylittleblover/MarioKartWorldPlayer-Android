package com.mylittleblover.mkwplayer.interactive

import com.mylittleblover.mkwplayer.audio.BwavHeader
import com.mylittleblover.mkwplayer.audio.EngineSnapshot
import com.mylittleblover.mkwplayer.library.CoursePreset
import com.mylittleblover.mkwplayer.library.FlowStep
import com.mylittleblover.mkwplayer.library.PresetRefs
import com.mylittleblover.mkwplayer.pack.PackManifest
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Inspects verified per-course stage assets and compiles built-in or user-authored
 * [CoursePreset] flows strictly from measured BWAV headers, MINF beats/events,
 * [ConfirmedOutroCatalog], and [CourseMedleyCatalog] — never inventing transition points
 * or hardcoding 48 kHz timing (#48-#53).
 */
internal object CourseFlowRecipeBuilder {

    data class ExitCandidate(
        val frame: Long,
        val label: String,
    )

    data class StagePartInfo(
        val roleKey: String,
        val stem: String,
        val secondaryStem: String? = null,
        val digest: String,
        val frames: Long,
        val sampleRate: Int,
        val loopStart: Long?,
        val loopEnd: Long?,
        val supportsLoop: Boolean,
        val exitCandidates: List<ExitCandidate>,
        val beats: List<MinfCatalog.Beat> = emptyList(),
        val phase: Long = 0L,
        val bridgeHandoverFrame: Long? = null,
        val fromBridgeFrame: Long? = null,
        val preAfterShortOffset: Long? = null,
        val isGridConstrained: Boolean = false,
    ) {
        val isBridgeLocked: Boolean
            get() = roleKey in setOf("Bridge", "Area1_Bridge")

        val defaultExitFrame: Long
            get() = if (isBridgeLocked) frames else (exitCandidates.lastOrNull()?.frame ?: loopEnd ?: frames)
    }

    data class OutroOption(
        val number: Int,
        val stem: String,
        val digest: String,
        val frames: Long,
        val sampleRate: Int,
        val mainTriggerPoints: List<Long>,
        val outroToNextPreFrame: Long?,
    ) {
        val supportsNativeHandover: Boolean
            get() = outroToNextPreFrame != null && outroToNextPreFrame in 1L..frames
    }

    data class BuiltInPresetSummary(
        val ref: String,
        val medley: CourseMedleyCatalog.Medley,
        val isKnockout: Boolean,
        val outroToNextPreFrame: Long?,
        val incomingPrePhase: Long,
    )

    data class CourseInventory(
        val courseCode: String,
        val parts: List<StagePartInfo>,
        val outros: List<OutroOption>,
        val builtInPresets: List<BuiltInPresetSummary>,
        val hasFanfareAsset: Boolean,
        val sampleRate: Int,
    ) {
        fun part(roleKey: String): StagePartInfo? = parts.firstOrNull { it.roleKey == roleKey }

        fun defaultEditorSteps(): List<FlowStep> {
            val preferred = when {
                courseCode == "Rainbow" -> listOf("Area1_Pre", "Area1_Bridge", "Area1", "Area2", "Area2_Outro04", "Area3_Pre", "Area3", "Final_Pre", "Final")
                courseCode.startsWith("extra:") && part("Harmonica") != null -> listOf("Harmonica", "Main")
                courseCode.startsWith("extra:") -> listOf("Main")
                else -> listOf("Pre", "Bridge", "Main")
            }
            val present = preferred.mapNotNull { role ->
                part(role)?.let { p ->
                    val exitFrame = when {
                        p.isBridgeLocked -> null
                        courseCode == "Rainbow" && role == "Area2" && part("Area2_Outro04") != null ->
                            outros.firstOrNull { it.number == 4 }?.mainTriggerPoints?.firstOrNull() ?: p.defaultExitFrame
                        else -> p.defaultExitFrame
                    }
                    FlowStep(
                        roleKey = role,
                        loopCount = if (p.supportsLoop && role in setOf("Main", "Area1", "Area2", "Area3", "Final", "BrassCut")) 1 else 0,
                        exitFrame = exitFrame,
                    )
                }
            }
            return present.ifEmpty {
                parts.firstOrNull()?.let { listOf(FlowStep(it.roleKey, exitFrame = if (it.isBridgeLocked) null else it.defaultExitFrame)) } ?: emptyList()
            }
        }

        /** Standard full-medley template or quick-entry template following exact interactive events. */
        fun standardMedleyTemplate(): List<FlowStep> = defaultEditorSteps()

        fun fastRaceTemplate(): List<FlowStep> {
            if (courseCode == "Rainbow") return defaultEditorSteps()
            if (courseCode.startsWith("extra:")) {
                val preferred = when {
                    part("BrassCut") != null -> listOf("Main", "BrassCut")
                    part("Fast") != null -> listOf("Main", "Fast")
                    else -> listOf("Main")
                }
                return preferred.mapNotNull { role ->
                    val p = part(role) ?: return@mapNotNull null
                    FlowStep(
                        roleKey = role,
                        loopCount = if (p.supportsLoop && role == "Main") 1 else 0,
                        exitFrame = p.defaultExitFrame,
                    )
                }
            }
            val preferred = buildList {
                if (part("Pre_Short") != null) add("Pre_Short") else if (part("Pre") != null) add("Pre")
                if (part("Bridge") != null) add("Bridge")
                add("Main")
                if (courseCode == "DonkeyKong") {
                    addAll(listOf("Region2", "Region3", "Region4", "Region5"))
                }
                if (part("Fast") != null) add("Fast")
            }
            return preferred.mapNotNull { role ->
                val p = part(role) ?: return@mapNotNull null
                FlowStep(
                    roleKey = role,
                    loopCount = if (p.supportsLoop && role == "Main") 1 else 0,
                    exitFrame = if (p.isBridgeLocked) null else p.defaultExitFrame,
                )
            }
        }

        /** Strict interactive state-machine transitions allowed from the current tail step. */
        fun allowedNextRoles(steps: List<FlowStep>): List<String> {
            val hasSpeaker = courseCode == "KoopaTroopaBeach"
            val hasPattern2 = courseCode in setOf("Cinema", "SnowMountain")
            val hasRegions = courseCode == "DonkeyKong"
            val raw = when {
                courseCode.startsWith("extra:") -> when (steps.lastOrNull()?.roleKey) {
                    null -> listOf("Harmonica", "Main", "BrassCut", "Fast")
                    "Harmonica" -> listOf("Main")
                    "Main" -> listOf("BrassCut", "Fast")
                    "BrassCut" -> listOf("Main")
                    else -> emptyList()
                }
                courseCode == "Rainbow" -> when (steps.lastOrNull()?.roleKey) {
                    null -> listOf("Area1_Pre", "Area1_Bridge", "Area1_Inst", "Area1", "Area2", "Area3_Pre", "Area3", "Final_Pre", "Final")
                    "Area1_Pre" -> if (part("Area1_Bridge") != null) listOf("Area1_Bridge", "Final") else listOf("Area1", "Area1_Inst", "Final")
                    "Area1_Bridge" -> listOf("Area1", "Area1_Inst")
                    "Area1_Inst" -> listOf("Area1", "Area2", "Final")
                    "Area1" -> listOf("Area1_Inst", "Area2", "Final")
                    "Area2" -> buildList {
                        for (n in 0..9) {
                            part("Area2_Outro%02d".format(n))?.let { add(it.roleKey) }
                        }
                        add("Area3_Pre")
                        add("Final")
                    }
                    "Area3_Pre" -> listOf("Area3", "Final")
                    "Area3" -> listOf("Final_Pre", "Final")
                    "Final_Pre" -> listOf("Final")
                    else -> {
                        val tail = steps.lastOrNull()?.roleKey
                        if (tail != null && tail.startsWith("Area2_Outro")) listOf("Area3_Pre")
                        else emptyList()
                    }
                }
                else -> when (val tail = steps.lastOrNull()?.roleKey) {
                    null -> listOf("Pre", "Pre_Short", "Bridge", "Main", "Fast")
                    "Pre_Short" -> buildList {
                        add("Pre")
                        if (part("Bridge") != null) add("Bridge") else add("Main")
                        add("Fast")
                    }
                    "Pre" -> buildList {
                        if (part("Bridge") != null) add("Bridge") else add("Main")
                        add("Fast")
                    }
                    "Bridge" -> listOf("Main", "Fast")
                    "Main" -> when {
                        hasRegions -> listOf("Region2")
                        hasPattern2 -> listOf("Pattern2", "Fast", "Fast_Pattern2")
                        hasSpeaker -> listOf("Main_Speaker", "Fast")
                        else -> listOf("Fast")
                    }
                    "Main_Speaker" -> listOf("Main", "Fast_Speaker", "Fast")
                    "Pattern2" -> listOf("Main", "Fast_Pattern2", "Fast")
                    "Fast" -> when {
                        hasPattern2 -> listOf("Fast_Pattern2")
                        hasSpeaker -> listOf("Fast_Speaker")
                        else -> emptyList()
                    }
                    "Fast_Speaker" -> listOf("Fast")
                    "Fast_Pattern2" -> listOf("Fast")
                    else -> if (hasRegions && tail.startsWith("Region")) {
                        val n = tail.removePrefix("Region").toIntOrNull() ?: 2
                        if (n in 2..4) listOf("Region${n + 1}")
                        else if (n == 5) listOf("Fast")
                        else emptyList()
                    } else {
                        emptyList()
                    }
                }
            }
            return raw.filter { part(it) != null }.distinct()
        }

        /** Verified Outros legally reachable from the tail step (e.g. SnowMountain Pattern2 only has Outro00..05). */
        fun availableOutrosForTail(steps: List<FlowStep>): List<OutroOption> {
            if (outros.isEmpty() || steps.isEmpty()) return emptyList()
            val tailRole = steps.last().roleKey
            if (!isOutroHostRole(tailRole)) return emptyList()
            if (tailRole == "Pattern2" && courseCode in setOf("Cinema", "SnowMountain")) {
                val pattern2Triggers = ConfirmedOutroCatalog.forStage("${courseCode}_Pattern2") ?: return emptyList()
                return outros.mapNotNull { outro ->
                    val triggers = pattern2Triggers[outro.number] ?: return@mapNotNull null
                    outro.copy(mainTriggerPoints = triggers.distinct().sorted())
                }
            }
            return outros
        }

        fun supportsOutroEnding(steps: List<FlowStep>): Boolean =
            availableOutrosForTail(steps).isNotEmpty()

        fun describeTransitionEvent(prevRole: String?, nextRole: String): String = when {
            prevRole == null -> "Entry -> $nextRole"
            prevRole == "Pre_Short" && nextRole == "Pre" -> "Pre_Short -> Pre (Aligned)"
            prevRole == "Harmonica" && nextRole == "Main" -> "ExitCue -> TitleNormal"
            nextRole in setOf("Bridge", "Area1_Bridge") -> "Phase Overlap -> $nextRole"
            prevRole == "Area2" && nextRole.startsWith("Area2_Outro") ->
                "Full_ToOutro${nextRole.removePrefix("Area2_Outro")} -> $nextRole"
            prevRole?.startsWith("Area2_Outro") == true && nextRole == "Area3_Pre" ->
                "Outro_ToNextPre -> Area3_Pre"
            prevRole in setOf("Bridge", "Area1_Bridge") -> "Bridge_ToFull -> $nextRole"
            courseCode in setOf("Cinema", "SnowMountain") &&
                prevRole in setOf("Main", "Pattern2", "Fast", "Fast_Pattern2") &&
                nextRole in setOf("Main", "Pattern2", "Fast", "Fast_Pattern2") &&
                (prevRole in setOf("Main", "Pattern2")) == (nextRole in setOf("Main", "Pattern2")) -> {
                val exitingSnowP2 = courseCode == "SnowMountain" && prevRole in setOf("Pattern2", "Fast_Pattern2")
                val fadeText = if (exitingSnowP2) "2.0s" else "0.5s"
                "Parallel Sync ($fadeText Crossfade) -> $nextRole"
            }
            courseCode == "Rainbow" && prevRole in setOf("Area1", "Area1_Inst") && nextRole == "Area2" ->
                "Split-Stem 0.3s Fade -> Area2"
            courseCode == "Rainbow" && prevRole == "Area3" && nextRole == "Final_Pre" ->
                "1.2s+3.0s / 2.5s Crossfade -> Final_Pre"
            courseCode == "KoopaTroopaBeach" &&
                ((prevRole == "Main" && nextRole == "Main_Speaker") ||
                    (prevRole == "Main_Speaker" && nextRole == "Main") ||
                    (prevRole == "Fast" && nextRole == "Fast_Speaker") ||
                    (prevRole == "Fast_Speaker" && nextRole == "Fast")) ->
                "Speaker Overlay Fade (1.0s) -> $nextRole"
            courseCode == "Rainbow" &&
                ((prevRole == "Area1_Inst" && nextRole == "Area1") ||
                    (prevRole == "Area1" && nextRole == "Area1_Inst")) ->
                "Melody Overlay Fade -> $nextRole"
            courseCode == "Rainbow" && nextRole == "Final" &&
                prevRole in setOf("Area1_Pre", "Area1_Inst", "Area1", "Area2", "Area3_Pre", "Area3", "Final_Pre") &&
                hasFanfareAsset ->
                "FinalLapFanfare (+0.20s) -> Final"
            nextRole in setOf("Fast", "Fast_Pattern2", "Fast_Speaker") &&
                prevRole !in setOf("Bridge", "Fast", "Fast_Pattern2", "Fast_Speaker") &&
                hasFanfareAsset && courseCode != "Rainbow" ->
                "FinalLapFF (+0.133s / +4.05s) -> $nextRole"
            nextRole in setOf("Fast", "Fast_Pattern2", "Fast_Speaker") ->
                "Direct 0.1s Fade -> $nextRole"
            else -> "NextBar / Phase Sync -> $nextRole"
        }
    }

    data class AssetHeader(
        val stem: String,
        val digest: String,
        val frames: Long,
        val sampleRate: Int,
        val loopStart: Long?,
        val loopEnd: Long?,
    )

    interface SourceResolver {
        fun findByFileName(fileName: String): AssetHeader?
    }

    fun packSourceResolver(
        manifest: PackManifest?,
        identities: InteractiveAudioIdentities,
        packDir: File,
        extraCatalog: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog? = null,
    ): SourceResolver {
        val manifestAssets = manifest?.assets?.associateBy { it.digest.sha256 }.orEmpty()
        return object : SourceResolver {
            override fun findByFileName(fileName: String): AssetHeader? {
                val stem = fileName.removeSuffix(".bwav")
                val digest = identities.digestForFileName(fileName)
                    ?: extraCatalog?.digestForStem(stem)
                    ?: return null
                val audioFile = File(packDir, "audio/$digest")
                if (!audioFile.isFile) return null
                val header = runCatching {
                    audioFile.inputStream().use { input ->
                        val head = ByteArray(1024)
                        val count = input.read(head)
                        if (count <= 0) return@use null
                        (BwavHeader.read(head.copyOf(count)) as? BwavHeader.Result.Ok)?.info
                    }
                }.getOrNull()
                if (header != null) {
                    return AssetHeader(
                        stem = stem,
                        digest = digest,
                        frames = header.frames.toLong(),
                        sampleRate = header.sampleRate,
                        loopStart = header.loopStart?.toLong(),
                        loopEnd = header.loopEnd?.toLong(),
                    )
                }
                val asset = manifestAssets[digest] ?: return null
                return AssetHeader(
                    stem = stem,
                    digest = digest,
                    frames = asset.frames,
                    sampleRate = asset.sampleRate,
                    loopStart = null,
                    loopEnd = null,
                )
            }
        }
    }

    fun inspectCourse(
        courseCode: String,
        minf: MinfCatalog,
        medleyCatalog: CourseMedleyCatalog.Catalog,
        resolver: SourceResolver,
    ): CourseInventory {
        val logical = courseCode.removeSuffix("_00")
        val medleys = medleyCatalog.forCourse(logical).sortedBy { it.suiteSortOrder }
        val builtIns = medleys.map { medley ->
            val ref = PresetRefs.forSuiteVariant(medley.normalizedSuiteVariant)
            val handover = resolveMedleyHandoverFrame(medley, minf)
            val introPhase = resolveIncomingPhase(medley, minf)
            BuiltInPresetSummary(
                ref = ref,
                medley = medley,
                isKnockout = medley.isKnockoutVariant,
                outroToNextPreFrame = handover,
                incomingPrePhase = introPhase,
            )
        }

        val roleDefinitions = if (logical == "Rainbow") rainbowRoleDefinitions() else standardRoleDefinitions(logical)
        val parts = roleDefinitions.mapNotNull { def ->
            val primaryHeader = def.candidateFileNames.firstNotNullOfOrNull(resolver::findByFileName)
                ?: return@mapNotNull null
            val secondaryHeader = def.secondaryFileName?.let(resolver::findByFileName)
            val record = findMinfRecord(minf, def.minfStage, def.minfRoles, primaryHeader.stem)
            val loopStart = primaryHeader.loopStart ?: record?.loopStart?.takeIf { it < (record.loopEnd) }
            val loopEnd = primaryHeader.loopEnd ?: record?.loopEnd?.takeIf { it > (loopStart ?: Long.MAX_VALUE) && it <= primaryHeader.frames }
            val bridgeHandover = if (def.roleKey in setOf("Bridge", "Area1_Bridge")) {
                record?.event("Bridge_ToFull")?.singleOrNull()?.takeIf { it in 0L..primaryHeader.frames }
            } else null
            val fromBridge = if (def.roleKey in setOf("Main", "Main_Speaker", "Pattern2", "Area1", "Area1_Inst")) {
                record?.event("Full_FromBridge")?.singleOrNull()
                    ?: record?.event("Full_SkipForBridge")?.singleOrNull()
            } else null
            val preAfterShort = if (def.roleKey == "Pre") {
                builtIns.firstOrNull { !it.isKnockout }?.medley?.clips
                    ?.firstOrNull { it.stem == primaryHeader.stem }
                    ?.sourceOffset
                    ?.takeIf { it > 0L }
                    ?: record?.phase?.takeIf { it > 0L }
            } else null
            val gridConstrained = isGridConstrainedRole(logical, def.roleKey, record)
            val candidates = collectExitCandidates(
                courseCode = logical,
                roleKey = def.roleKey,
                record = record,
                frames = primaryHeader.frames,
                sampleRate = primaryHeader.sampleRate,
                gridConstrained = gridConstrained,
            )
            val beats = record?.beats?.takeIf { it.isNotEmpty() }
                ?: if (def.roleKey in setOf("Bridge", "Area1_Bridge")) emptyList()
                else buildSyntheticBeats(primaryHeader.frames, primaryHeader.sampleRate)
            StagePartInfo(
                roleKey = def.roleKey,
                stem = primaryHeader.stem,
                secondaryStem = secondaryHeader?.stem,
                digest = primaryHeader.digest,
                frames = primaryHeader.frames,
                sampleRate = primaryHeader.sampleRate,
                loopStart = loopStart,
                loopEnd = loopEnd,
                supportsLoop = loopStart != null && loopEnd != null && loopEnd > loopStart &&
                    def.roleKey !in setOf("Bridge", "Area1_Bridge") &&
                    !def.roleKey.startsWith("Area2_Outro"),
                exitCandidates = candidates,
                beats = beats,
                phase = record?.phase ?: 0L,
                bridgeHandoverFrame = bridgeHandover,
                fromBridgeFrame = fromBridge,
                preAfterShortOffset = preAfterShort,
                isGridConstrained = gridConstrained,
            )
        }

        val confirmedMap = if (logical == "Rainbow") {
            val area2Record = findMinfRecord(minf, "Rainbow", listOf("Area2"), "BGM_Rainbow_Area2")
            buildMap {
                if (area2Record != null) {
                    for (n in 0..9) {
                        val triggers = area2Record.event("Full_ToOutro%02d".format(n)).orEmpty()
                        if (triggers.isNotEmpty()) put(n, triggers)
                    }
                }
            }
        } else {
            ConfirmedOutroCatalog.forStage(logical).orEmpty()
        }
        val outros = confirmedMap.keys.sorted().mapNotNull { n ->
            val fileName = if (logical == "Rainbow") {
                "BGM_Rainbow_Area2_Outro%02d.bwav".format(n)
            } else {
                "BGM_Stage_${logical}_Outro%02d.bwav".format(n)
            }
            val header = resolver.findByFileName(fileName) ?: return@mapNotNull null
            val handover = findOutroHandoverFrame(logical, n, header.stem, minf)
                ?.takeIf { it in 1L..header.frames }
            OutroOption(
                number = n,
                stem = header.stem,
                digest = header.digest,
                frames = header.frames,
                sampleRate = header.sampleRate,
                mainTriggerPoints = confirmedMap[n].orEmpty().sorted(),
                outroToNextPreFrame = handover,
            )
        }

        val fanfarePresent = if (logical == "Rainbow") {
            resolver.findByFileName("BGM_Rainbow_FinalLapFanfare.bwav") != null
        } else {
            resolver.findByFileName(FinalLapFanfareCompiler.ASSET) != null
        }
        val sampleRate = parts.firstOrNull()?.sampleRate
            ?: builtIns.firstOrNull()?.let { medleyCatalog.sampleRate }
            ?: 48_000
        return CourseInventory(
            courseCode = logical,
            parts = parts,
            outros = outros,
            builtInPresets = builtIns,
            hasFanfareAsset = fanfarePresent,
            sampleRate = sampleRate,
        )
    }

    /**
     * Builds a [CourseInventory] for non-course interactive tracks (Interactive Title,
     * Retro/SFC two-state courses, Battle two-state tracks) so they can use the full
     * waveform/seam Flow Editor and export `extra_medley_catalog`-compatible JSON.
     */
    fun inspectExtra(
        extraId: String,
        extraCatalog: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.Catalog,
        extraMedleyCatalog: ExtraMedleyCatalog.Catalog,
        packDir: File?,
        hasFanfareAsset: Boolean = false,
    ): CourseInventory? {
        val cleanId = extraId.removePrefix("extra:")
        val editorCode = "extra:$cleanId"

        fun assetAvailable(sha256: String): Boolean =
            packDir == null || File(packDir, "audio/$sha256").isFile

        fun buildPart(
            roleKey: String,
            ref: com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.AssetRef,
            customCues: List<Long> = emptyList(),
        ): StagePartInfo {
            val frames = ref.frames.coerceAtLeast(1L)
            val rate = ref.sampleRate.coerceAtLeast(1)
            val l0 = ref.loopStart?.takeIf { it in 0L until (ref.loopEnd ?: frames) }
            val l1 = ref.loopEnd?.takeIf { it > (l0 ?: Long.MAX_VALUE) && it <= frames }
            val priority = buildSet {
                addAll(customCues.filter { it in 1L..frames })
                l1?.let { add(it) }
                add(frames)
            }
            val syntheticBeats = buildSyntheticBeats(frames, rate)
            val barDownbeats = syntheticBeats.filter { it.label == 1 }.map { it.frame }
            val downbeatBudget = (64 - priority.size).coerceAtLeast(0)
            val sampledBars = if (downbeatBudget == 0 || barDownbeats.isEmpty()) {
                emptyList()
            } else {
                val stride = (barDownbeats.size - 1).coerceAtLeast(1).toDouble() / downbeatBudget.coerceAtLeast(1)
                (0 until downbeatBudget).map { idx ->
                    barDownbeats[(idx * stride).toInt().coerceIn(0, barDownbeats.lastIndex)]
                }
            }
            val rawCues = (priority + sampledBars).distinct().sorted()
            return StagePartInfo(
                roleKey = roleKey,
                stem = ref.stem,
                digest = ref.sha256,
                frames = frames,
                sampleRate = rate,
                loopStart = l0,
                loopEnd = l1,
                supportsLoop = ref.loop && l0 != null && l1 != null && l1 > l0,
                exitCandidates = rawCues.map { ExitCandidate(it, formatTime(it, rate)) },
                beats = syntheticBeats,
            )
        }

        val builtIns = extraMedleyCatalog.forTarget(cleanId).map { entry ->
            BuiltInPresetSummary(
                ref = PresetRefs.SYSTEM_DEFAULT,
                medley = entry.medley,
                isKnockout = false,
                outroToNextPreFrame = null,
                incomingPrePhase = 0L,
            )
        }

        val titleItem = extraCatalog.otherMusic
            .filterIsInstance<com.mylittleblover.mkwplayer.ui.home.ExtraMusicCatalog.OtherMusicItem.InteractiveTitle>()
            .firstOrNull { it.id == cleanId }
        if (titleItem != null) {
            if (!assetAvailable(titleItem.main.sha256)) return null
            val parts = buildList {
                if (assetAvailable(titleItem.harmonica.sha256)) {
                    add(buildPart("Harmonica", titleItem.harmonica))
                }
                add(buildPart("Main", titleItem.main, titleItem.outros.map { it.mainExitFrame }))
                if (assetAvailable(titleItem.brassCut.sha256)) {
                    add(buildPart("BrassCut", titleItem.brassCut, titleItem.outros.map { it.brassCutExitFrame }))
                }
            }
            val outros = titleItem.outros.mapNotNull { outro ->
                if (!assetAvailable(outro.asset.sha256)) return@mapNotNull null
                OutroOption(
                    number = outro.index,
                    stem = outro.asset.stem,
                    digest = outro.asset.sha256,
                    frames = outro.asset.frames,
                    sampleRate = outro.asset.sampleRate,
                    mainTriggerPoints = listOf(outro.mainExitFrame).distinct().sorted(),
                    outroToNextPreFrame = null,
                )
            }
            return CourseInventory(
                courseCode = editorCode,
                parts = parts,
                outros = outros,
                builtInPresets = builtIns,
                hasFanfareAsset = false,
                sampleRate = titleItem.main.sampleRate,
            )
        }

        val sfcTrack = extraCatalog.sfcCourses.firstOrNull { it.id == cleanId }
        val battleTrack = if (sfcTrack == null) extraCatalog.battleTracks.firstOrNull { it.id == cleanId } else null
        val twoState = sfcTrack ?: battleTrack ?: return null
        if (!assetAvailable(twoState.main.sha256) || !assetAvailable(twoState.fast.sha256)) return null
        val parts = listOf(
            buildPart("Main", twoState.main),
            buildPart("Fast", twoState.fast),
        )
        return CourseInventory(
            courseCode = editorCode,
            parts = parts,
            outros = emptyList(),
            builtInPresets = builtIns,
            // Verified v1.8.0: Battle mode switches directly to BattleFL without FinalLapFF.
            hasFanfareAsset = if (battleTrack != null) false else hasFanfareAsset,
            sampleRate = twoState.main.sampleRate,
        )
    }

    /** Resolves a preset reference (`sys:default`, `sys:knockout`, or `user:<id>`) into a runnable medley. */
    fun resolvePresetRef(
        courseCode: String,
        presetRef: String?,
        userPresets: List<CoursePreset>,
        minf: MinfCatalog,
        medleyCatalog: CourseMedleyCatalog.Catalog,
        resolver: SourceResolver,
        segmentSwitchFadeOutMs: Int = 0,
    ): CourseMedleyCompiler.ResolvedMedley? {
        val logical = courseCode.removeSuffix("_00")
        if (PresetRefs.isUser(presetRef)) {
            val found = userPresets.firstOrNull {
                it.id == presetRef && it.courseCode.equals(logical, ignoreCase = true)
            }
            if (found != null) {
                val inventory = inspectCourse(logical, minf, medleyCatalog, resolver)
                compileUserPreset(
                    found,
                    inventory,
                    minf,
                    resolver,
                    segmentSwitchFadeOutMs = segmentSwitchFadeOutMs,
                )?.let { return it }
            }
            // Deleted or unavailable user preset falls back cleanly to system default (#50).
        }
        val builtIns = medleyCatalog.forCourse(logical)
            .sortedBy { it.suiteSortOrder }
            .map { medley ->
                BuiltInPresetSummary(
                    ref = PresetRefs.forSuiteVariant(medley.normalizedSuiteVariant),
                    medley = medley,
                    isKnockout = medley.isKnockoutVariant,
                    outroToNextPreFrame = resolveMedleyHandoverFrame(medley, minf),
                    incomingPrePhase = resolveIncomingPhase(medley, minf),
                )
            }
        val targetBuiltIn = when (presetRef) {
            PresetRefs.SYSTEM_KNOCKOUT -> builtIns.firstOrNull { it.isKnockout }
                ?: builtIns.firstOrNull { it.ref == PresetRefs.SYSTEM_DEFAULT }
                ?: builtIns.firstOrNull()
            PresetRefs.SYSTEM_SUITE_A_ALT,
            PresetRefs.SYSTEM_SUITE_B,
            PresetRefs.SYSTEM_SUITE_C,
            PresetRefs.SYSTEM_SUITE_D,
            -> builtIns.firstOrNull { it.ref == presetRef }
                ?: builtIns.firstOrNull { it.ref == PresetRefs.SYSTEM_DEFAULT && !it.isKnockout }
                ?: builtIns.firstOrNull()
            else -> builtIns.firstOrNull { it.ref == PresetRefs.SYSTEM_DEFAULT && !it.isKnockout }
                ?: builtIns.firstOrNull()
        } ?: return null

        val medley = targetBuiltIn.medley
        val digests = linkedMapOf<String, String>()
        var sampleRate = 48_000
        var capturedRate = false
        for (stem in medley.uniqueStems) {
            if (stem.equals("BGM_Seq_FinalLapFF", ignoreCase = true)) {
                digests[stem] = FinalLapFanfareCompiler.SHA256
                continue
            }
            val header = resolver.findByFileName("$stem.bwav") ?: return null
            if (!capturedRate && header.sampleRate > 0) {
                sampleRate = header.sampleRate
                capturedRate = true
            }
            digests[stem] = header.digest
        }
        return CourseMedleyCompiler.ResolvedMedley(
            medley = medley,
            sampleRate = sampleRate,
            stemDigests = digests,
            outroHandoverFrame = targetBuiltIn.outroToNextPreFrame,
            incomingPrePhase = targetBuiltIn.incomingPrePhase,
        )
    }

    fun compileUserPreset(
        preset: CoursePreset,
        inventory: CourseInventory,
        minf: MinfCatalog,
        resolver: SourceResolver,
        segmentSwitchFadeOutMs: Int = 0,
    ): CourseMedleyCompiler.ResolvedMedley? =
        compileUserPresetWithSeams(
            preset,
            inventory,
            minf,
            resolver,
            segmentSwitchFadeOutMs = segmentSwitchFadeOutMs,
        )?.first

    /**
     * Compiles a short preview medley centered on seam [seamIndex] (in seconds: [leadInSec] before
     * and [tailSec] after the transition boundary between step `seamIndex` and step `seamIndex + 1`,
     * or between the last step and the Outro when `seamIndex == preset.steps.lastIndex`).
     */
    fun compileSeamPreview(
        preset: CoursePreset,
        seamIndex: Int,
        inventory: CourseInventory,
        minf: MinfCatalog,
        resolver: SourceResolver,
        leadInSec: Int = 4,
        tailSec: Int = 6,
        segmentSwitchFadeOutMs: Int = 0,
    ): CourseMedleyCompiler.ResolvedMedley? {
        val (resolved, seamAnchors) = compileUserPresetWithSeams(
            preset,
            inventory,
            minf,
            resolver,
            segmentSwitchFadeOutMs = segmentSwitchFadeOutMs,
        ) ?: return null
        val anchorFrame = seamAnchors.getOrNull(seamIndex) ?: return null
        val sr = resolved.sampleRate.toLong().coerceAtLeast(1L)
        val leadInFrames = leadInSec.coerceAtLeast(1) * sr
        val tailFrames = tailSec.coerceAtLeast(2) * sr
        val windowStart = (anchorFrame - leadInFrames).coerceAtLeast(0L)
        val windowEnd = (anchorFrame + tailFrames).coerceAtMost(resolved.medley.totalFrames)
        val windowLen = (windowEnd - windowStart).coerceAtLeast(1L)

        val edgeFadeFrames = (sr / 40L).coerceAtLeast(1L) // ~25 ms click-free edge envelope
        val slicedClips = resolved.medley.clips.mapNotNull { clip ->
            val clipEnd = clip.start + clip.sourceLength
            if (clipEnd <= windowStart || clip.start >= windowEnd) return@mapNotNull null
            val clippedStart = maxOf(clip.start, windowStart)
            val clippedEnd = minOf(clipEnd, windowEnd)
            val length = (clippedEnd - clippedStart).coerceAtLeast(1L)
            val trimHead = clippedStart - clip.start
            val newStart = clippedStart - windowStart
            val newOffset = clip.sourceOffset + trimHead
            val fadeIn = when {
                clip.fadeIn > trimHead -> (clip.fadeIn - trimHead).coerceAtMost(length)
                clippedStart == windowStart && newOffset > 0L -> edgeFadeFrames.coerceAtMost(length / 2L)
                else -> 0L
            }
            val remainingTail = clipEnd - clippedEnd
            val fadeOut = when {
                clip.fadeOut > remainingTail -> (clip.fadeOut - remainingTail).coerceAtMost(length - fadeIn)
                clippedEnd == windowEnd && clip.fadeOut == 0L -> edgeFadeFrames.coerceAtMost((length - fadeIn).coerceAtLeast(0L))
                else -> 0L
            }
            CourseMedleyCatalog.MedleyClip(
                stem = clip.stem,
                start = newStart,
                sourceOffset = newOffset,
                sourceLength = length,
                fadeIn = fadeIn,
                fadeOut = fadeOut,
                volumePoints = sliceVolumePoints(clip.volumePoints, trimHead, length),
            )
        }
        if (slicedClips.isEmpty()) return null
        val stems = slicedClips.map { it.stem }.distinct()
        val digests = stems.mapNotNull { stem -> resolved.stemDigests[stem]?.let { stem to it } }.toMap()
        if (digests.size != stems.size) return null

        val previewMedley = CourseMedleyCatalog.Medley(
            id = "${resolved.medley.id}_seam_$seamIndex",
            index = 0,
            courseCode = resolved.medley.courseCode,
            variant = "seam_preview",
            totalFrames = windowLen,
            durationMs = EngineSnapshot.framesToMillis(windowLen, resolved.sampleRate),
            clips = slicedClips,
            uniqueStems = stems,
        )
        return CourseMedleyCompiler.ResolvedMedley(
            medley = previewMedley,
            sampleRate = resolved.sampleRate,
            stemDigests = digests,
            outroHandoverFrame = null,
            incomingPrePhase = 0L,
        )
    }

    /**
     * Exports a formatted JSON configuration bundle containing both a `course_medley_catalog`-compatible
     * `medley` object and the structured `editorPreset` definition.
     */
    fun exportPresetConfigJson(
        preset: CoursePreset,
        resolved: CourseMedleyCompiler.ResolvedMedley,
    ): String {
        val m = resolved.medley
        val clipsArr = JSONArray()
        m.clips.forEach { clip ->
            val obj = JSONObject()
                .put("stem", clip.stem)
                .put("start", clip.start)
                .put("sourceOffset", clip.sourceOffset)
                .put("sourceLength", clip.sourceLength)
            if (clip.fadeIn > 0L) obj.put("fadeIn", clip.fadeIn)
            if (clip.fadeOut > 0L) obj.put("fadeOut", clip.fadeOut)
            if (clip.volumePoints.isNotEmpty()) {
                val vpArr = JSONArray()
                clip.volumePoints.forEach { pt ->
                    vpArr.put(
                        JSONObject()
                            .put("frame", pt.frame)
                            .put("value", pt.value)
                            .put("shape", pt.shape.name),
                    )
                }
                obj.put("volumePoints", vpArr)
            }
            clipsArr.put(obj)
        }
        val medleyObj = JSONObject()
            .put("id", m.id)
            .put("index", m.index)
            .put("courseCode", m.courseCode)
            .put("variant", m.variant)
            .put("totalFrames", m.totalFrames)
            .put("durationMs", m.durationMs)
            .put("clips", clipsArr)
            .put("uniqueStems", JSONArray(m.uniqueStems))

        val stepsArr = JSONArray()
        preset.steps.forEach { step ->
            val stepObj = JSONObject()
                .put("roleKey", step.roleKey)
                .put("loopCount", step.loopCount)
            if (step.exitFrame != null) stepObj.put("exitFrame", step.exitFrame)
            stepsArr.put(stepObj)
        }
        val presetObj = JSONObject()
            .put("id", preset.id)
            .put("courseCode", preset.courseCode)
            .put("name", preset.name)
            .put("steps", stepsArr)
            .put("outroNumber", preset.outroNumber ?: JSONObject.NULL)
            .put("fadeOutMs", preset.fadeOutMs)
            .put("outroToNextPre", preset.outroToNextPre)
            .put("updatedAtEpochMs", preset.updatedAtEpochMs)

        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("sampleRate", resolved.sampleRate)
            .put("medley", medleyObj)
            .put("outroHandoverFrame", resolved.outroHandoverFrame ?: JSONObject.NULL)
            .put("incomingPrePhase", resolved.incomingPrePhase)
            .put("editorPreset", presetObj)
        if (m.courseCode.startsWith("extra:")) {
            root.put("targetId", m.courseCode.removePrefix("extra:"))
        }
        return root.toString(2)
    }

    private fun computeParallelResumeOffset(part: StagePartInfo, prevExitSourcePos: Long): Long {
        val l0 = part.loopStart
        val l1 = part.loopEnd
        val resumed = if (part.supportsLoop && l0 != null && l1 != null && prevExitSourcePos >= l1) {
            val span = (l1 - l0).coerceAtLeast(1L)
            l0 + ((prevExitSourcePos - l1) % span)
        } else {
            prevExitSourcePos
        }
        return resumed.coerceIn(0L, (part.frames - 1L).coerceAtLeast(0L))
    }

    private data class StepLoopExit(
        val targetExit: Long,
        val effectiveLoopCount: Int,
    )

    internal fun stepSourceOffsets(
        steps: List<FlowStep>,
        outroNumber: Int?,
        inventory: CourseInventory,
    ): List<Long> {
        if (steps.isEmpty()) return emptyList()
        val offsets = LongArray(steps.size)
        val endingOutro = outroNumber
            ?.let { num -> inventory.availableOutrosForTail(steps).firstOrNull { it.number == num } }
        var prevExitSourcePos = 0L
        steps.forEachIndexed { idx, step ->
            val part = inventory.part(step.roleKey)
            if (part == null) {
                offsets[idx] = 0L
                prevExitSourcePos = 0L
                return@forEachIndexed
            }
            val prevRole = steps.getOrNull(idx - 1)?.roleKey
            val nextRole = steps.getOrNull(idx + 1)?.roleKey
            val isFirst = idx == 0
            val sourceOffset = when {
                !isFirst && prevRole == "Pre_Short" && step.roleKey == "Pre" ->
                    (part.preAfterShortOffset ?: part.phase).coerceIn(0L, (part.frames - 1L).coerceAtLeast(0L))
                !isFirst && step.roleKey in setOf("Bridge", "Area1_Bridge") -> 0L
                !isFirst && prevRole in setOf("Bridge", "Area1_Bridge") &&
                    step.roleKey in setOf("Main", "Main_Speaker", "Area1", "Area1_Inst", "Pattern2") ->
                    part.fromBridgeFrame?.coerceIn(0L, (part.frames - 1L).coerceAtLeast(0L)) ?: 0L
                !isFirst && inventory.courseCode == "Rainbow" && prevRole == "Area3" && step.roleKey == "Final_Pre" ->
                    part.phase.coerceIn(0L, (part.frames - 1L).coerceAtLeast(0L))
                !isFirst && isParallelSameTimePair(inventory.courseCode, prevRole, step.roleKey) ->
                    computeParallelResumeOffset(part, prevExitSourcePos)
                else -> 0L
            }
            offsets[idx] = sourceOffset
            val isHandoverArea2Outro = step.roleKey.startsWith("Area2_Outro") && idx < steps.lastIndex
            val pairedNextOutro = if (step.roleKey == "Area2" && nextRole?.startsWith("Area2_Outro") == true) {
                nextRole.removePrefix("Area2_Outro").toIntOrNull()
                    ?.let { num -> inventory.outros.firstOrNull { it.number == num } }
            } else null
            val stepOutro = pairedNextOutro
                ?: if (idx == steps.lastIndex && endingOutro != null && isOutroHostRole(step.roleKey)) endingOutro else null
            val resolved = resolveStepLoopAndExit(
                courseCode = inventory.courseCode,
                step = step,
                sourceOffset = sourceOffset,
                part = part,
                isHandoverArea2Outro = isHandoverArea2Outro,
                stepOutro = stepOutro,
            )
            prevExitSourcePos = when {
                part.isBridgeLocked -> 0L
                !isHandoverArea2Outro && part.supportsLoop && resolved.effectiveLoopCount > 0 -> resolved.targetExit
                isHandoverArea2Outro -> part.frames
                else -> resolved.targetExit.coerceIn(sourceOffset + 1L, part.frames)
            }
        }
        return offsets.toList()
    }

    internal fun reachableOutroTriggers(
        step: FlowStep,
        sourceOffset: Long,
        part: StagePartInfo?,
        outro: OutroOption?,
    ): List<Long> {
        val base = outro?.mainTriggerPoints.orEmpty().distinct().sorted()
        if (base.isEmpty() || part == null) return base
        val l0 = part.loopStart
        return when {
            step.loopCount > 0 && part.supportsLoop && l0 != null ->
                base.filter { it > l0 && it <= part.frames }.ifEmpty { base }
            sourceOffset > 0L && part.supportsLoop && l0 != null ->
                base.filter { it > sourceOffset || (it > l0 && it <= part.frames) }.ifEmpty { base }
            sourceOffset > 0L ->
                base.filter { it > sourceOffset && it <= part.frames }.ifEmpty { base }
            else -> base
        }
    }

    private fun resolveStepLoopAndExit(
        courseCode: String,
        step: FlowStep,
        sourceOffset: Long,
        part: StagePartInfo,
        isHandoverArea2Outro: Boolean,
        stepOutro: OutroOption?,
    ): StepLoopExit {
        val hasExplicitCustomExit = step.exitFrame != null &&
            (courseCode.startsWith("extra:") || step.exitFrame != part.defaultExitFrame)
        val rawExit = (step.exitFrame ?: part.defaultExitFrame).coerceIn(1L, part.frames)
        val rawTarget = if (!part.isBridgeLocked &&
            !(courseCode.startsWith("extra:") && hasExplicitCustomExit) &&
            stepOutro != null &&
            stepOutro.mainTriggerPoints.isNotEmpty()
        ) {
            val validTriggers = reachableOutroTriggers(step, sourceOffset, part, stepOutro)
            when {
                validTriggers.size == 1 -> validTriggers.single()
                step.exitFrame != null && step.exitFrame in validTriggers -> step.exitFrame
                hasExplicitCustomExit ->
                    validTriggers.minByOrNull { kotlin.math.abs(it - rawExit) } ?: validTriggers.last()
                step.loopCount > 0 || sourceOffset > 0L ->
                    validTriggers.firstOrNull { it > sourceOffset } ?: validTriggers.last()
                else ->
                    validTriggers.firstOrNull { it >= rawExit } ?: validTriggers.last()
            }.coerceIn(1L, part.frames)
        } else if (part.isGridConstrained && part.exitCandidates.isNotEmpty() && !part.isBridgeLocked) {
            part.exitCandidates.minByOrNull { kotlin.math.abs(it.frame - rawExit) }?.frame ?: rawExit
        } else {
            rawExit
        }

        val l0 = part.loopStart
        val l1 = part.loopEnd
        val isPreLoopOnlyOutro = !part.isBridgeLocked && !isHandoverArea2Outro &&
            stepOutro != null && l0 != null &&
            rawTarget <= l0 && rawTarget > sourceOffset
        val needsLoopWrap = !part.isBridgeLocked && !isHandoverArea2Outro &&
            part.supportsLoop && l0 != null && l1 != null &&
            step.loopCount == 0 &&
            (stepOutro != null || hasExplicitCustomExit) &&
            rawTarget <= sourceOffset && rawTarget > l0
        val effectiveLoopCount = when {
            isPreLoopOnlyOutro -> 0
            needsLoopWrap -> 1
            else -> step.loopCount
        }
        val looping = !part.isBridgeLocked && !isHandoverArea2Outro && part.supportsLoop && effectiveLoopCount > 0
        val exitFloor = if (looping) {
            ((l0 ?: 0L) + 1L).coerceAtMost(part.frames)
        } else {
            (sourceOffset + 1L).coerceAtMost(part.frames)
        }
        return StepLoopExit(
            targetExit = rawTarget.coerceIn(exitFloor, part.frames),
            effectiveLoopCount = effectiveLoopCount,
        )
    }

    private fun isPureOverlayToggle(courseCode: String, from: String?, to: String?): Boolean {
        if (from == null || to == null || from == to) return false
        return (courseCode == "KoopaTroopaBeach" &&
            ((from == "Main" && to == "Main_Speaker") ||
                (from == "Main_Speaker" && to == "Main") ||
                (from == "Fast" && to == "Fast_Speaker") ||
                (from == "Fast_Speaker" && to == "Fast"))) ||
            (courseCode == "Rainbow" &&
                ((from == "Area1_Inst" && to == "Area1") ||
                    (from == "Area1" && to == "Area1_Inst")))
    }

    private fun isParallelSameTimePair(courseCode: String, from: String?, to: String?): Boolean {
        if (from == null || to == null || from == to) return false
        return (courseCode in setOf("Cinema", "SnowMountain") &&
            ((from == "Main" && to == "Pattern2") ||
                (from == "Pattern2" && to == "Main") ||
                (from == "Fast" && to == "Fast_Pattern2") ||
                (from == "Fast_Pattern2" && to == "Fast"))) ||
            isPureOverlayToggle(courseCode, from, to)
    }

    private fun framesForSeconds(seconds: Double, sampleRate: Int): Long =
        kotlin.math.round(seconds * sampleRate.toDouble()).toLong().coerceAtLeast(0L)

    private fun isRainbowFinalWithFanfareTransition(
        courseCode: String,
        prevRole: String?,
        roleKey: String,
    ): Boolean = courseCode == "Rainbow" &&
        roleKey == "Final" &&
        prevRole in setOf("Area1_Pre", "Area1_Inst", "Area1", "Area2", "Area3_Pre", "Area3", "Final_Pre")

    private fun isStandardFinalLapWithFanfareTransition(
        courseCode: String,
        prevRole: String?,
        roleKey: String,
        hasFanfareAsset: Boolean,
    ): Boolean = courseCode != "Rainbow" &&
        roleKey in setOf("Fast", "Fast_Pattern2", "Fast_Speaker") &&
        prevRole !in setOf("Bridge", "Fast", "Fast_Pattern2", "Fast_Speaker") &&
        hasFanfareAsset

    private fun patternCrossfadeFrames(
        courseCode: String,
        from: String?,
        to: String?,
        sampleRate: Int,
    ): Long {
        if (courseCode !in setOf("Cinema", "SnowMountain") || from == null || to == null) return 0L
        val enteringPattern2 = (from == "Main" && to == "Pattern2") || (from == "Fast" && to == "Fast_Pattern2")
        val exitingPattern2 = (from == "Pattern2" && to == "Main") || (from == "Fast_Pattern2" && to == "Fast")
        return when {
            enteringPattern2 -> framesForSeconds(FullVersionCompiler.CHANGE_PATTERN_FADE_SEC, sampleRate)
            exitingPattern2 -> framesForSeconds(
                if (courseCode == "SnowMountain") {
                    FullVersionCompiler.CHANGE_PATTERN_FADE_SNOW_SEC
                } else {
                    FullVersionCompiler.CHANGE_PATTERN_FADE_SEC
                },
                sampleRate,
            )
            else -> 0L
        }
    }

    /** Estimates exact timeline duration in frames matching [compileUserPreset] calculations. */
    fun estimateTimelineFrames(
        steps: List<FlowStep>,
        outroNumber: Int?,
        fadeOutMs: Int = 0,
        inventory: CourseInventory,
    ): Long {
        if (steps.isEmpty()) return 0L
        val sampleRate = inventory.sampleRate.coerceAtLeast(1)
        val endingOutro = outroNumber
            ?.let { num -> inventory.availableOutrosForTail(steps).firstOrNull { it.number == num } }
        var cursor = 0L
        var maxEnd = 0L
        var prevExitSourcePos = 0L
        var lastPart: StagePartInfo? = null
        var lastFirstExit = 0L
        var lastTargetExit = 0L
        var lastLooping = false
        var lastEnd = 0L

        steps.forEachIndexed { idx, step ->
            val part = inventory.part(step.roleKey) ?: return@forEachIndexed
            val prevRole = steps.getOrNull(idx - 1)?.roleKey
            val nextRole = steps.getOrNull(idx + 1)?.roleKey
            val isFirst = idx == 0

            var clipStart = cursor
            var sourceOffset = 0L

            if (!isFirst && prevRole == "Pre_Short" && step.roleKey == "Pre") {
                sourceOffset = (part.preAfterShortOffset ?: part.phase).coerceIn(0L, (part.frames - 1L).coerceAtLeast(0L))
            } else if (!isFirst && step.roleKey in setOf("Bridge", "Area1_Bridge")) {
                clipStart = (cursor - part.phase).coerceAtLeast(0L)
            } else if (!isFirst && prevRole in setOf("Bridge", "Area1_Bridge") &&
                step.roleKey in setOf("Main", "Main_Speaker", "Area1", "Area1_Inst", "Pattern2")
            ) {
                val fromBridge = part.fromBridgeFrame
                if (fromBridge != null) {
                    sourceOffset = fromBridge.coerceIn(0L, (part.frames - 1L).coerceAtLeast(0L))
                    clipStart = cursor
                } else {
                    sourceOffset = 0L
                    clipStart = (cursor - part.phase).coerceAtLeast(0L)
                }
            } else if (!isFirst && step.roleKey in setOf(
                    "Main", "Main_Speaker", "Area1_Inst", "Area1", "Area2", "Area3",
                    "Final_Pre", "Final", "Fast", "Fast_Speaker", "Pattern2", "Fast_Pattern2",
                    "Region2", "Region3", "Region4", "Region5",
                )
            ) {
                val isRainbowFinalWithFanfare = isRainbowFinalWithFanfareTransition(
                    inventory.courseCode, prevRole, step.roleKey,
                )
                val isStandardFinalLapWithFanfare = isStandardFinalLapWithFanfareTransition(
                    inventory.courseCode, prevRole, step.roleKey, inventory.hasFanfareAsset,
                )
                val isRainbowArea3Crossfade = inventory.courseCode == "Rainbow" &&
                    prevRole == "Area3" && step.roleKey == "Final_Pre"
                when {
                    isRainbowFinalWithFanfare && inventory.hasFanfareAsset -> {
                        val ffDelay = RainbowInteractiveCompiler.RAINBOW_FANFARE_DELAY_FRAMES * sampleRate / 48_000L
                        val finalDelay = (RainbowInteractiveCompiler.RAINBOW_FANFARE_DELAY_FRAMES +
                            RainbowInteractiveCompiler.FINAL_FROM_FANFARE_FRAMES) * sampleRate / 48_000L
                        maxEnd = maxOf(maxEnd, cursor + ffDelay + (338_824L * sampleRate / 48_000L))
                        clipStart = cursor + finalDelay
                    }
                    isStandardFinalLapWithFanfare -> {
                        val ffDelay = FinalLapFanfareCompiler.FANFARE_DELAY_FRAMES * sampleRate / 48_000L
                        val fastDelay = FinalLapFanfareCompiler.FAST_DELAY_FRAMES * sampleRate / 48_000L
                        maxEnd = maxOf(maxEnd, cursor + ffDelay + (268_759L * sampleRate / 48_000L))
                        clipStart = cursor + fastDelay
                    }
                    isRainbowArea3Crossfade -> {
                        clipStart = cursor
                        sourceOffset = part.phase.coerceIn(0L, (part.frames - 1L).coerceAtLeast(0L))
                    }
                    isParallelSameTimePair(inventory.courseCode, prevRole, step.roleKey) -> {
                        clipStart = cursor
                        sourceOffset = computeParallelResumeOffset(part, prevExitSourcePos)
                    }
                    else -> {
                        clipStart = (cursor - part.phase).coerceAtLeast(0L)
                    }
                }
            }

            val isHandoverArea2Outro = step.roleKey.startsWith("Area2_Outro") && idx < steps.lastIndex
            val pairedNextOutro = if (step.roleKey == "Area2" && nextRole?.startsWith("Area2_Outro") == true) {
                nextRole.removePrefix("Area2_Outro").toIntOrNull()
                    ?.let { num -> inventory.outros.firstOrNull { it.number == num } }
            } else null
            val stepOutro = pairedNextOutro
                ?: if (idx == steps.lastIndex && endingOutro != null && isOutroHostRole(step.roleKey)) endingOutro else null
            val resolvedExit = resolveStepLoopAndExit(
                courseCode = inventory.courseCode,
                step = step,
                sourceOffset = sourceOffset,
                part = part,
                isHandoverArea2Outro = isHandoverArea2Outro,
                stepOutro = stepOutro,
            )
            val targetExit = resolvedExit.targetExit
            val effectiveLoops = resolvedExit.effectiveLoopCount
            val loopingWithFinalExit = !part.isBridgeLocked && !isHandoverArea2Outro && part.supportsLoop && effectiveLoops > 0

            val firstExit = when {
                part.isBridgeLocked || isHandoverArea2Outro -> part.frames
                loopingWithFinalExit -> part.loopEnd!!
                else -> targetExit
            }.coerceIn(sourceOffset + 1L, part.frames)
            var stepEnd = clipStart + (firstExit - sourceOffset).coerceAtLeast(1L)
            if (loopingWithFinalExit) {
                val loopStart = part.loopStart!!
                val loopEnd = part.loopEnd!!
                repeat(effectiveLoops) { loopIdx ->
                    val isLastCycle = loopIdx == effectiveLoops - 1
                    val (cycleOffset, cycleEnd) = when {
                        isLastCycle && targetExit > loopStart -> loopStart to targetExit
                        isLastCycle -> 0L to targetExit
                        else -> loopStart to loopEnd
                    }
                    stepEnd += (cycleEnd - cycleOffset).coerceAtLeast(1L)
                }
            }
            maxEnd = maxOf(maxEnd, stepEnd)
            val willSwitchAfterThisStep = idx < steps.lastIndex || (idx == steps.lastIndex && endingOutro != null)
            val isPureOverlaySwitch = nextRole != null && isPureOverlayToggle(inventory.courseCode, step.roleKey, nextRole)
            val isSpeakerOutroFade = stepOutro != null && part.secondaryStem != null &&
                step.roleKey in setOf("Main_Speaker", "Fast_Speaker")
            val ruleFadeTail = when {
                nextRole == null -> 0L
                inventory.courseCode == "Rainbow" && step.roleKey == "Area3" && nextRole == "Final_Pre" ->
                    framesForSeconds(
                        RainbowInteractiveCompiler.AREA3_FADEOUT_DELAY_SEC + RainbowInteractiveCompiler.AREA3_FADEOUT_SEC,
                        sampleRate,
                    )
                inventory.courseCode == "Rainbow" && step.roleKey in setOf("Area1", "Area1_Inst") && nextRole == "Area2" ->
                    framesForSeconds(RainbowInteractiveCompiler.AREA1_TO_AREA2_FADE_SEC, sampleRate)
                inventory.courseCode == "Rainbow" && inventory.hasFanfareAsset &&
                    isRainbowFinalWithFanfareTransition(inventory.courseCode, step.roleKey, nextRole) ->
                    framesForSeconds(RainbowInteractiveCompiler.RAINBOW_FINAL_PRE_FADEOUT_SEC, sampleRate)
                isStandardFinalLapWithFanfareTransition(inventory.courseCode, step.roleKey, nextRole, inventory.hasFanfareAsset) ->
                    framesForSeconds(FinalLapFanfareCompiler.FULL_FADE_OUT_SEC, sampleRate)
                else -> patternCrossfadeFrames(inventory.courseCode, step.roleKey, nextRole, sampleRate)
            }
            if (ruleFadeTail > 0L && willSwitchAfterThisStep && !part.isBridgeLocked && !isHandoverArea2Outro && !isPureOverlaySwitch) {
                if (part.supportsLoop) {
                    maxEnd = maxOf(maxEnd, stepEnd + ruleFadeTail)
                } else {
                    val exitSrc = if (loopingWithFinalExit) targetExit else firstExit
                    val avail = (part.frames - exitSrc).coerceAtLeast(0L)
                    maxEnd = maxOf(maxEnd, stepEnd + minOf(ruleFadeTail, avail))
                }
            }
            if (isSpeakerOutroFade && endingOutro != null) {
                val spkFade = minOf(sampleRate.toLong(), endingOutro.frames)
                maxEnd = maxOf(maxEnd, stepEnd + spkFade)
            }

            prevExitSourcePos = when {
                part.isBridgeLocked -> 0L
                loopingWithFinalExit -> targetExit
                else -> firstExit
            }
            lastPart = part
            lastFirstExit = firstExit
            lastTargetExit = targetExit
            lastLooping = loopingWithFinalExit
            lastEnd = stepEnd

            cursor = when {
                part.isBridgeLocked && idx < steps.lastIndex -> clipStart + (part.bridgeHandoverFrame ?: part.frames)
                isHandoverArea2Outro && nextRole == "Area3_Pre" -> {
                    val outroNum = step.roleKey.removePrefix("Area2_Outro").toIntOrNull()
                    val handover = outroNum?.let { num -> inventory.outros.firstOrNull { it.number == num }?.outroToNextPreFrame }
                    clipStart + (handover ?: part.frames)
                }
                else -> stepEnd
            }
        }

        if (endingOutro != null) {
            maxEnd = maxOf(maxEnd, cursor + endingOutro.frames)
        } else if (fadeOutMs > 0 && lastPart != null) {
            val fadeFrames = (fadeOutMs.toLong() * sampleRate / 1000L).coerceAtLeast(0L)
            if (lastPart!!.supportsLoop) {
                maxEnd = maxOf(maxEnd, lastEnd + fadeFrames)
            } else {
                val exitSrc = if (lastLooping) lastTargetExit else lastFirstExit
                val avail = (lastPart!!.frames - exitSrc).coerceAtLeast(0L)
                maxEnd = maxOf(maxEnd, lastEnd + minOf(fadeFrames, avail))
            }
        }
        return maxEnd.coerceAtLeast(0L)
    }

    private fun compileUserPresetWithSeams(
        preset: CoursePreset,
        inventory: CourseInventory,
        minf: MinfCatalog,
        resolver: SourceResolver,
        segmentSwitchFadeOutMs: Int = 0,
    ): Pair<CourseMedleyCompiler.ResolvedMedley, List<Long>>? {
        val clips = mutableListOf<CourseMedleyCatalog.MedleyClip>()
        val digests = linkedMapOf<String, String>()
        val seamAnchors = mutableListOf<Long>()
        val sampleRate = inventory.sampleRate
        val endingOutro = preset.outroNumber
            ?.let { num -> inventory.availableOutrosForTail(preset.steps).firstOrNull { it.number == num } }
        val switchFadeFrames = (segmentSwitchFadeOutMs.toLong() * sampleRate / 1000L).coerceAtLeast(0L)
        var cursor = 0L
        var prevExitSourcePos = 0L
        var lastStepPart: StagePartInfo? = null
        var lastStepPrimaryIdx = -1
        var lastStepSecondaryIdx: Int? = null
        var lastStepSecHeader: AssetHeader? = null
        var lastStepEnd = 0L

        preset.steps.forEachIndexed { idx, step ->
            val part = inventory.part(step.roleKey) ?: return null
            val prevRole = preset.steps.getOrNull(idx - 1)?.roleKey
            val nextRole = preset.steps.getOrNull(idx + 1)?.roleKey
            val isFirst = idx == 0

            val minfRole = mapRoleKeyToMinfRole(inventory.courseCode, step.roleKey)
            val record = findMinfRecord(minf, minfStageFor(inventory.courseCode, step.roleKey), minfRole, part.stem)

            var clipStart = cursor
            var sourceOffset = 0L

            if (!isFirst && prevRole == "Pre_Short" && step.roleKey == "Pre") {
                // Strictly align Pre's verified entry bar after Pre_Short without crossfade (#6/#7).
                val refMedley = inventory.builtInPresets.firstOrNull { !it.isKnockout }?.medley
                val refPre = refMedley?.clips?.firstOrNull { it.stem == part.stem }
                sourceOffset = refPre?.sourceOffset?.takeIf { it > 0L }
                    ?: record?.phase?.coerceIn(0L, part.frames - 1L)
                    ?: 0L
                seamAnchors += clipStart
            } else if (!isFirst && step.roleKey in setOf("Bridge", "Area1_Bridge")) {
                val bridgePhase = record?.phase ?: 0L
                clipStart = (cursor - bridgePhase).coerceAtLeast(0L)
                seamAnchors += cursor
            } else if (!isFirst && prevRole in setOf("Bridge", "Area1_Bridge") &&
                step.roleKey in setOf("Main", "Main_Speaker", "Area1", "Area1_Inst", "Pattern2")
            ) {
                val prevBridgePart = inventory.part(prevRole!!)
                val prevBridgeMinf = findMinfRecord(
                    minf,
                    inventory.courseCode,
                    mapRoleKeyToMinfRole(inventory.courseCode, prevRole),
                    prevBridgePart?.stem,
                )
                val prevBridgeClip = clips.lastOrNull()
                val bridgeExit = prevBridgeMinf?.event("Bridge_ToFull")?.singleOrNull()
                    ?: prevBridgePart?.frames
                    ?: 0L
                val bridgeStart = prevBridgeClip?.start ?: cursor
                val handover = bridgeStart + bridgeExit
                val fromBridge = record?.event("Full_FromBridge")?.singleOrNull()
                    ?: record?.event("Full_SkipForBridge")?.singleOrNull()
                val fullPhase = record?.phase ?: 0L
                if (fromBridge != null) {
                    sourceOffset = fromBridge.coerceIn(0L, part.frames - 1L)
                    clipStart = handover.coerceAtLeast(0L)
                } else {
                    sourceOffset = 0L
                    clipStart = (handover - fullPhase).coerceAtLeast(0L)
                }
                seamAnchors += handover
            } else if (!isFirst && step.roleKey in setOf(
                    "Main", "Main_Speaker", "Area1_Inst", "Area1", "Area2", "Area3",
                    "Final_Pre", "Final", "Fast", "Fast_Speaker", "Pattern2", "Fast_Pattern2",
                    "Region2", "Region3", "Region4", "Region5",
                )
            ) {
                val phase = record?.phase ?: 0L
                val isRainbowFinalWithFanfare = isRainbowFinalWithFanfareTransition(
                    inventory.courseCode, prevRole, step.roleKey,
                )
                val isStandardFinalLapWithFanfare = isStandardFinalLapWithFanfareTransition(
                    inventory.courseCode, prevRole, step.roleKey, inventory.hasFanfareAsset,
                )
                val isRainbowArea3Crossfade = inventory.courseCode == "Rainbow" && prevRole == "Area3" && step.roleKey == "Final_Pre"
                val isParallelSameTimeSwitch = isParallelSameTimePair(inventory.courseCode, prevRole, step.roleKey)
                if (isRainbowFinalWithFanfare) {
                    val rainbowFf = resolver.findByFileName("BGM_Rainbow_FinalLapFanfare.bwav")
                    if (rainbowFf != null) {
                        val ffDelay = RainbowInteractiveCompiler.RAINBOW_FANFARE_DELAY_FRAMES * sampleRate / 48_000L
                        val finalDelay = (RainbowInteractiveCompiler.RAINBOW_FANFARE_DELAY_FRAMES +
                            RainbowInteractiveCompiler.FINAL_FROM_FANFARE_FRAMES) * sampleRate / 48_000L
                        digests[rainbowFf.stem] = rainbowFf.digest
                        clips += CourseMedleyCatalog.MedleyClip(
                            stem = rainbowFf.stem,
                            start = cursor + ffDelay,
                            sourceOffset = 0L,
                            sourceLength = rainbowFf.frames,
                        )
                        // Verified v1.8.0: Rainbow_FanfareDelay = 0.20s (9,600 f) + 218,734 f Fanfare->Final.
                        clipStart = cursor + finalDelay
                    } else {
                        clipStart = (cursor - phase).coerceAtLeast(0L)
                    }
                } else if (isStandardFinalLapWithFanfare) {
                    val fanfareHeader = resolver.findByFileName(FinalLapFanfareCompiler.ASSET)
                    if (fanfareHeader != null) {
                        val ffDelay = FinalLapFanfareCompiler.FANFARE_DELAY_FRAMES * sampleRate / 48_000L
                        val fastDelay = FinalLapFanfareCompiler.FAST_DELAY_FRAMES * sampleRate / 48_000L
                        digests[fanfareHeader.stem] = fanfareHeader.digest
                        clips += CourseMedleyCatalog.MedleyClip(
                            stem = fanfareHeader.stem,
                            start = cursor + ffDelay,
                            sourceOffset = 0L,
                            sourceLength = fanfareHeader.frames,
                        )
                        // Verified v1.8.0: FinalLapFanfareDelay = 0.133s (6,384 f), FastDelay = 4.05s (194,400 f).
                        clipStart = cursor + fastDelay
                    } else {
                        clipStart = (cursor - phase).coerceAtLeast(0L)
                    }
                } else if (isRainbowArea3Crossfade) {
                    // Verified v1.8.0: Final_Pre starts immediately at cursor (2.50s fade-in) while Area3 holds 1.20s + fades 3.00s.
                    clipStart = cursor
                    sourceOffset = phase.coerceIn(0L, part.frames - 1L)
                } else if (isParallelSameTimeSwitch) {
                    // SameTime two-track parallel progress: stay on outgoing step's source frame (wrapping cleanly across loopEnd -> loopStart).
                    clipStart = cursor
                    sourceOffset = computeParallelResumeOffset(part, prevExitSourcePos)
                } else {
                    clipStart = (cursor - phase).coerceAtLeast(0L)
                }
                seamAnchors += cursor
            } else if (!isFirst) {
                seamAnchors += cursor
            }

            val isHandoverArea2Outro = step.roleKey.startsWith("Area2_Outro") && idx < preset.steps.lastIndex
            val pairedNextOutro = if (step.roleKey == "Area2" && nextRole?.startsWith("Area2_Outro") == true) {
                nextRole.removePrefix("Area2_Outro").toIntOrNull()
                    ?.let { num -> inventory.outros.firstOrNull { it.number == num } }
            } else {
                null
            }
            val stepOutro = pairedNextOutro
                ?: if (idx == preset.steps.lastIndex && endingOutro != null && isOutroHostRole(step.roleKey)) endingOutro else null
            val resolvedExit = resolveStepLoopAndExit(
                courseCode = inventory.courseCode,
                step = step,
                sourceOffset = sourceOffset,
                part = part,
                isHandoverArea2Outro = isHandoverArea2Outro,
                stepOutro = stepOutro,
            )
            val targetExit = resolvedExit.targetExit
            val effectiveLoops = resolvedExit.effectiveLoopCount
            val loopingWithFinalExit = !part.isBridgeLocked && !isHandoverArea2Outro && part.supportsLoop && effectiveLoops > 0

            // Bridge and middle Area2_Outro steps play their full tail cleanly while handover advances cursor.
            val firstExit = when {
                part.isBridgeLocked || isHandoverArea2Outro -> part.frames
                loopingWithFinalExit -> part.loopEnd!!
                else -> targetExit
            }.coerceIn(sourceOffset + 1L, part.frames)

            val secHeader = part.secondaryStem?.let { resolver.findByFileName("$it.bwav") }
            if (part.secondaryStem != null && secHeader == null) return null

            val firstLength = (firstExit - sourceOffset).coerceAtLeast(1L)
            val incomingRuleFadeIn = when {
                isFirst -> 0L
                inventory.courseCode == "Rainbow" && prevRole == "Area3" && step.roleKey == "Final_Pre" ->
                    framesForSeconds(RainbowInteractiveCompiler.FINAL_PRE_FADEIN_SEC, sampleRate)
                else -> patternCrossfadeFrames(inventory.courseCode, prevRole, step.roleKey, sampleRate)
            }
            val incomingFadeIn = if (incomingRuleFadeIn > 0L) {
                minOf(incomingRuleFadeIn, firstLength)
            } else {
                0L
            }
            digests[part.stem] = part.digest
            var lastPrimaryIdx = clips.size
            clips += CourseMedleyCatalog.MedleyClip(
                stem = part.stem,
                start = clipStart,
                sourceOffset = sourceOffset,
                sourceLength = firstLength,
                fadeIn = incomingFadeIn,
            )

            // Paired sample-locked secondary layer (Rainbow Area1_Melody / Koopa Speaker overlay).
            var lastSecondaryIdx: Int? = null
            if (secHeader != null) {
                digests[secHeader.stem] = secHeader.digest
                val secLen = firstLength.coerceAtMost(secHeader.frames - sourceOffset)
                val isOverlayFadeIn =
                    step.roleKey in setOf("Main_Speaker", "Fast_Speaker") ||
                        (inventory.courseCode == "Rainbow" && step.roleKey == "Area1" && prevRole == "Area1_Inst")
                val secFadeIn = if (isOverlayFadeIn) {
                    minOf(sampleRate.toLong(), secLen)
                } else {
                    incomingFadeIn.coerceAtMost(secLen)
                }
                lastSecondaryIdx = clips.size
                clips += CourseMedleyCatalog.MedleyClip(
                    stem = secHeader.stem,
                    start = clipStart,
                    sourceOffset = sourceOffset,
                    sourceLength = secLen,
                    fadeIn = secFadeIn,
                )
            }

            var stepEnd = clipStart + firstLength
            if (loopingWithFinalExit) {
                val loopStart = part.loopStart!!
                val loopEnd = part.loopEnd!!
                repeat(effectiveLoops) { loopIdx ->
                    val isLastCycle = loopIdx == effectiveLoops - 1
                    val (cycleOffset, cycleEnd) = when {
                        isLastCycle && targetExit > loopStart -> loopStart to targetExit
                        isLastCycle -> 0L to targetExit
                        else -> loopStart to loopEnd
                    }
                    val len = (cycleEnd - cycleOffset).coerceAtLeast(1L)
                    lastPrimaryIdx = clips.size
                    clips += CourseMedleyCatalog.MedleyClip(
                        stem = part.stem,
                        start = stepEnd,
                        sourceOffset = cycleOffset,
                        sourceLength = len,
                    )
                    if (secHeader != null) {
                        lastSecondaryIdx = clips.size
                        clips += CourseMedleyCatalog.MedleyClip(
                            stem = secHeader.stem,
                            start = stepEnd,
                            sourceOffset = cycleOffset,
                            sourceLength = len.coerceAtMost((secHeader.frames - cycleOffset).coerceAtLeast(1L)),
                        )
                    }
                    stepEnd += len
                }
            }

            // Trailing post-exit fade-out: always extends past targetExit without shifting next segment cursor.
            val willSwitchAfterThisStep = idx < preset.steps.lastIndex ||
                (idx == preset.steps.lastIndex && endingOutro != null)
            val isPureOverlaySwitch = nextRole != null && isPureOverlayToggle(inventory.courseCode, step.roleKey, nextRole)
            val isRainbowArea3ToFinalPre = inventory.courseCode == "Rainbow" && step.roleKey == "Area3" && nextRole == "Final_Pre"
            val isRainbowArea1ToArea2 = inventory.courseCode == "Rainbow" &&
                step.roleKey in setOf("Area1", "Area1_Inst") && nextRole == "Area2"
            val isRainbowFinalLapExit = nextRole != null && inventory.hasFanfareAsset &&
                isRainbowFinalWithFanfareTransition(inventory.courseCode, step.roleKey, nextRole)
            val isStandardFinalLapExit = nextRole != null &&
                isStandardFinalLapWithFanfareTransition(inventory.courseCode, step.roleKey, nextRole, inventory.hasFanfareAsset)
            val ruleOutgoingDelay = if (isRainbowArea3ToFinalPre) {
                framesForSeconds(RainbowInteractiveCompiler.AREA3_FADEOUT_DELAY_SEC, sampleRate)
            } else {
                0L
            }
            val ruleOutgoingFade = when {
                nextRole == null -> 0L
                isRainbowArea3ToFinalPre -> framesForSeconds(RainbowInteractiveCompiler.AREA3_FADEOUT_SEC, sampleRate)
                isRainbowArea1ToArea2 -> framesForSeconds(RainbowInteractiveCompiler.AREA1_TO_AREA2_FADE_SEC, sampleRate)
                isRainbowFinalLapExit -> framesForSeconds(RainbowInteractiveCompiler.RAINBOW_FINAL_PRE_FADEOUT_SEC, sampleRate)
                isStandardFinalLapExit -> framesForSeconds(FinalLapFanfareCompiler.FULL_FADE_OUT_SEC, sampleRate)
                else -> patternCrossfadeFrames(inventory.courseCode, step.roleKey, nextRole, sampleRate)
            }
            // Verified v1.8.0 Rainbow Area1 -> Area2 split fade (0x0243d500):
            // Area1_Melody fades out over 0.30s starting one bar (~72,911 frames @ 158 BPM) before
            // bar.exit so pure Area1_Inst finishes the bar before fading out 0.30s post-exit into Area2.
            if (isRainbowArea1ToArea2 && step.roleKey == "Area1" && lastSecondaryIdx != null) {
                val melodyFade = framesForSeconds(RainbowInteractiveCompiler.AREA1_TO_AREA2_FADE_SEC, sampleRate)
                val bpm = record?.bpm?.takeIf { it.isFinite() && it > 0.0 } ?: 158.0
                val barFrames = kotlin.math.round(4.0 * 60.0 / bpm * sampleRate.toDouble()).toLong()
                    .coerceAtLeast(melodyFade)
                val secClip = clips[lastSecondaryIdx]
                val minLen = (secClip.fadeIn + melodyFade).coerceAtMost(secClip.sourceLength)
                val targetLen = (secClip.sourceLength - barFrames + melodyFade).coerceIn(minLen, secClip.sourceLength)
                val clamped = melodyFade.coerceIn(0L, (targetLen - secClip.fadeIn).coerceAtLeast(0L))
                if (clamped > 0L) {
                    clips[lastSecondaryIdx] = secClip.copy(
                        sourceLength = targetLen,
                        fadeOut = clamped,
                    )
                }
            }
            val isOverlayFadeOut = (nextRole != null && (
                (inventory.courseCode == "Rainbow" && step.roleKey == "Area1" && nextRole == "Area1_Inst") ||
                    (inventory.courseCode == "KoopaTroopaBeach" &&
                        ((step.roleKey == "Main_Speaker" && nextRole == "Main") ||
                            (step.roleKey == "Fast_Speaker" && nextRole == "Fast")))
                )) || (stepOutro != null && secHeader != null && step.roleKey in setOf("Main_Speaker", "Fast_Speaker"))
            val ruleSecondaryOutgoingFade = when {
                isRainbowArea1ToArea2 && step.roleKey == "Area1" -> 0L
                isOverlayFadeOut && stepOutro != null && endingOutro != null ->
                    minOf(sampleRate.toLong(), endingOutro.frames)
                isOverlayFadeOut -> sampleRate.toLong()
                else -> ruleOutgoingFade
            }
            val primaryFadeFrames = if (isPureOverlaySwitch || (stepOutro != null && step.roleKey in setOf("Main_Speaker", "Fast_Speaker"))) {
                0L
            } else {
                maxOf(switchFadeFrames, ruleOutgoingFade)
            }
            val secondaryFadeFrames = if ((isPureOverlaySwitch && !isOverlayFadeOut) || (isRainbowArea1ToArea2 && step.roleKey == "Area1")) {
                0L
            } else if (stepOutro != null && step.roleKey in setOf("Main_Speaker", "Fast_Speaker") && secHeader != null) {
                ruleSecondaryOutgoingFade
            } else {
                maxOf(switchFadeFrames, ruleSecondaryOutgoingFade)
            }
            if ((primaryFadeFrames > 0L || secondaryFadeFrames > 0L) && willSwitchAfterThisStep && !part.isBridgeLocked && !isHandoverArea2Outro) {
                applyPostExitFade(
                    clips = clips,
                    primaryIdx = lastPrimaryIdx,
                    secondaryIdx = lastSecondaryIdx,
                    part = part,
                    secHeader = secHeader,
                    fadeStartTimeline = stepEnd,
                    fadeFrames = primaryFadeFrames,
                    delayFrames = if (primaryFadeFrames == ruleOutgoingFade) ruleOutgoingDelay else 0L,
                    sampleRate = sampleRate,
                    allowInClipFallback = ruleOutgoingFade > 0L,
                    secondaryFadeFrames = secondaryFadeFrames,
                    allowSecondaryInClipFallback = ruleSecondaryOutgoingFade > 0L,
                )
            }

            prevExitSourcePos = when {
                part.isBridgeLocked -> 0L
                loopingWithFinalExit -> targetExit
                else -> firstExit
            }
            lastStepPart = part
            lastStepPrimaryIdx = lastPrimaryIdx
            lastStepSecondaryIdx = lastSecondaryIdx
            lastStepSecHeader = secHeader
            lastStepEnd = stepEnd

            cursor = when {
                part.isBridgeLocked && idx < preset.steps.lastIndex ->
                    // Next step aligns against Bridge_ToFull rather than waiting for full Bridge file tail.
                    clipStart + (record?.event("Bridge_ToFull")?.singleOrNull() ?: part.frames)
                isHandoverArea2Outro && nextRole == "Area3_Pre" -> {
                    val outroNum = step.roleKey.removePrefix("Area2_Outro").toIntOrNull()
                    val handover = outroNum?.let { num ->
                        findOutroHandoverFrame(inventory.courseCode, num, part.stem, minf)
                    }?.takeIf { it in 1L..part.frames }
                    clipStart + (handover ?: part.frames)
                }
                else -> stepEnd
            }
        }

        var outroHandoverFrame: Long? = null
        if (endingOutro != null) {
            digests[endingOutro.stem] = endingOutro.digest
            val outroStart = cursor
            seamAnchors += outroStart
            clips += CourseMedleyCatalog.MedleyClip(
                stem = endingOutro.stem,
                start = outroStart,
                sourceOffset = 0L,
                sourceLength = endingOutro.frames,
            )
            if (preset.outroToNextPre) {
                endingOutro.outroToNextPreFrame?.let { evt ->
                    outroHandoverFrame = outroStart + evt
                }
            }
            cursor = outroStart + endingOutro.frames
        } else if (preset.fadeOutMs > 0 && lastStepPrimaryIdx >= 0 && lastStepPart != null) {
            // Post-exit fade-out: extends past configured end point and wraps loop boundaries to keep full fade duration.
            val fadeFrames = (preset.fadeOutMs.toLong() * sampleRate / 1000L).coerceAtLeast(0L)
            if (fadeFrames > 0L) {
                applyPostExitFade(
                    clips = clips,
                    primaryIdx = lastStepPrimaryIdx,
                    secondaryIdx = lastStepSecondaryIdx,
                    part = lastStepPart!!,
                    secHeader = lastStepSecHeader,
                    fadeStartTimeline = lastStepEnd,
                    fadeFrames = fadeFrames,
                    sampleRate = sampleRate,
                    allowInClipFallback = true,
                )
            }
        }

        val totalFrames = clips.maxOfOrNull { it.start + it.sourceLength } ?: return null
        val firstClip = clips.first()
        val firstRole = preset.steps.first().roleKey
        val firstMinf = findMinfRecord(
            minf,
            minfStageFor(inventory.courseCode, firstRole),
            mapRoleKeyToMinfRole(inventory.courseCode, firstRole),
        )
        val medley = CourseMedleyCatalog.Medley(
            id = "${inventory.courseCode}_${preset.id}",
            index = 0,
            courseCode = inventory.courseCode,
            variant = "custom",
            totalFrames = totalFrames,
            durationMs = EngineSnapshot.framesToMillis(totalFrames, sampleRate),
            clips = clips,
            uniqueStems = digests.keys.toList(),
        )
        val resolved = CourseMedleyCompiler.ResolvedMedley(
            medley = medley,
            sampleRate = sampleRate,
            stemDigests = digests,
            outroHandoverFrame = outroHandoverFrame?.takeIf { it in 1L..totalFrames },
            incomingPrePhase = if (firstRole in setOf("Pre_Short", "Pre", "Area1_Pre", "Bridge")) {
                ((firstMinf?.phase ?: 0L) - firstClip.sourceOffset).coerceAtLeast(0L)
            } else {
                0L
            },
        )
        return resolved to seamAnchors
    }

    fun resolveMedleyHandoverFrame(medley: CourseMedleyCatalog.Medley, minf: MinfCatalog): Long? {
        val lastClip = medley.clips.lastOrNull() ?: return null
        val outroEvt = findOutroHandoverFrameForStem(medley.courseCode, lastClip.stem, minf) ?: return null
        val timelineHandover = lastClip.start + (outroEvt - lastClip.sourceOffset)
        return timelineHandover.takeIf { it in 1L..medley.totalFrames }
    }

    fun resolveIncomingPhase(medley: CourseMedleyCatalog.Medley, minf: MinfCatalog): Long {
        val firstClip = medley.clips.firstOrNull() ?: return 0L
        val role = inferMinfRoleFromStem(medley.courseCode, firstClip.stem) ?: return 0L
        if (!role.startsWith("Pre") && role != "Area1_Pre" && !role.startsWith("Bridge")) return 0L
        val record = findMinfRecord(minf, medley.courseCode, listOf(role)) ?: return 0L
        return (record.phase - firstClip.sourceOffset).coerceAtLeast(0L)
    }

    private fun inferMinfRoleFromStem(stage: String, stem: String): String? {
        if (stage == "Rainbow" && stem.startsWith("BGM_Rainbow_")) {
            return stem.removePrefix("BGM_Rainbow_")
        }
        val prefix = "BGM_Stage_${stage}_"
        if (stem == "BGM_Stage_$stage") return "Full"
        if (!stem.startsWith(prefix)) return null
        return when (val suffix = stem.removePrefix(prefix)) {
            "Pre_Short" -> if (stage == "IceBuildings") "Pre_Short_2" else "Pre_Short"
            "Pre" -> if (stage == "IceBuildings") "Pre_2" else "Pre"
            "FL" -> "FL"
            "Fast" -> "Fast"
            else -> suffix
        }
    }

    private fun findOutroHandoverFrame(stage: String, outroNumber: Int, stem: String, minf: MinfCatalog): Long? {
        findOutroHandoverFrameForStem(stage, stem, minf)?.let { return it }
        val explicit = "Outro%02d".format(outroNumber)
        return minf.records
            .firstOrNull { it.stage == stage && (it.role == explicit || it.role == "Area2_Outro") }
            ?.event("Outro_ToNextPre")
            ?.singleOrNull()
    }

    private fun findOutroHandoverFrameForStem(stage: String, stem: String, minf: MinfCatalog): Long? {
        val byLogical = minf.records.filter {
            it.stage == stage && it.logicalName.equals(stem, ignoreCase = true)
        }
        for (record in byLogical) {
            record.event("Outro_ToNextPre")?.singleOrNull()?.let { return it }
        }
        val role = inferMinfRoleFromStem(stage, stem) ?: return null
        val byRole = minf.records.filter { it.stage == stage && it.role == role }
        for (record in byRole) {
            record.event("Outro_ToNextPre")?.singleOrNull()?.let { return it }
        }
        // Handle grouped outro records like Outro / Outro00020305 / Outro010203 or event-tagged Outro00/Outro05/Outro06
        val numMatch = Regex("Outro(\\d{2})$").find(stem)?.groupValues?.get(1)
        if (numMatch != null) {
            val grouped = minf.records.filter {
                it.stage == stage && it.role.startsWith("Outro") &&
                    (it.role == "Outro" || it.role.contains(numMatch) || it.event("Outro$numMatch") != null)
            }
            for (record in grouped) {
                record.event("Outro_ToNextPre")?.singleOrNull()?.let { return it }
            }
            if (stage == "Lava" && numMatch == "01") {
                val lavaFallback = minf.records.filter { it.stage == "Lava" && it.role == "Outro00" }
                for (record in lavaFallback) {
                    record.event("Outro_ToNextPre")?.singleOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    internal fun isOutroHostRole(roleKey: String): Boolean =
        roleKey in setOf("Main", "Main_Speaker", "Area2", "Pattern2", "BrassCut")

    private fun buildSyntheticBeats(frames: Long, sampleRate: Int): List<MinfCatalog.Beat> {
        val sr = sampleRate.coerceAtLeast(1)
        val beatStep = (sr / 2L).coerceAtLeast(1L) // 0.5s fine grid, bar downbeat every 2.0s (4 beats)
        if (frames <= beatStep) return emptyList()
        return buildList {
            var pos = beatStep
            var idx = 1
            while (pos < frames) {
                val label = if (idx % 4 == 0) 1 else ((idx % 4) + 1)
                add(MinfCatalog.Beat(pos, label))
                pos += beatStep
                idx++
            }
        }
    }

    private fun findMinfRecord(
        minf: MinfCatalog,
        stage: String,
        roles: List<String>,
        preferredStem: String? = null,
    ): MinfCatalog.Record? {
        if (!preferredStem.isNullOrBlank()) {
            minf.records.firstOrNull {
                it.stage == stage && it.logicalName.equals(preferredStem, ignoreCase = true)
            }?.let { return it }
        }
        for (role in roles) {
            minf.part(stage, role)?.let { return it }
            minf.records.firstOrNull { it.stage == stage && it.role == role }?.let { return it }
        }
        return null
    }

    private fun isGridConstrainedRole(
        courseCode: String,
        roleKey: String,
        record: MinfCatalog.Record?,
    ): Boolean {
        if (record == null || record.beats.isEmpty()) return false
        if (roleKey in setOf("Pre", "Pre_Short", "Area1_Pre", "Area3_Pre")) return true
        if (courseCode == "DonkeyKong" && roleKey in setOf("Main", "Region2", "Region3", "Region4")) return true
        return false
    }

    private fun collectExitCandidates(
        courseCode: String = "",
        roleKey: String,
        record: MinfCatalog.Record?,
        frames: Long,
        sampleRate: Int,
        gridConstrained: Boolean = isGridConstrainedRole(courseCode, roleKey, record),
    ): List<ExitCandidate> {
        if (roleKey in setOf("Bridge", "Area1_Bridge")) {
            val handover = record?.event("Bridge_ToFull")?.singleOrNull()?.takeIf { it in 0L..frames } ?: frames
            return listOf(ExitCandidate(handover, formatTime(handover, sampleRate)))
        }
        if (gridConstrained && record != null) {
            val strictFrames = when {
                roleKey in setOf("Pre", "Pre_Short", "Area1_Pre", "Area3_Pre") ->
                    record.gridLoopCandidates(MinfCatalog.preTransitionGridBeats(courseCode, roleKey))
                courseCode == "DonkeyKong" && roleKey == "Main" ->
                    DonkeyRegionCompiler.regionExitFrames(record, 1)
                courseCode == "DonkeyKong" && roleKey in setOf("Region2", "Region3", "Region4") ->
                    DonkeyRegionCompiler.regionExitFrames(record, roleKey.removePrefix("Region").toInt())
                else -> record.loopCandidates
            }.filter { it in 1L..frames }.distinct().sorted()
            val chosen = strictFrames.ifEmpty { listOf(frames) }
            return chosen.map { ExitCandidate(it, formatTime(it, sampleRate)) }
        }
        val priority = buildSet {
            if (record != null) {
                val baseCandidates = if (roleKey == "Final_Pre" && courseCode.isNotBlank()) {
                    record.gridLoopCandidates(MinfCatalog.preTransitionGridBeats(courseCode, roleKey))
                } else {
                    record.loopCandidates
                }
                addAll(baseCandidates.filter { it in 1L..frames })
            }
            add(frames)
        }
        val downbeats = if (record != null && record.beats.isNotEmpty()) {
            record.beats.filter { it.label == 1 && it.frame in 1L..frames }.map { it.frame }
        } else {
            buildSyntheticBeats(frames, sampleRate).filter { it.label == 1 }.map { it.frame }
        }
        val combined = (priority + downbeats).distinct().sorted()
        val selected = if (combined.size <= 64) {
            combined
        } else {
            val downbeatBudget = (64 - priority.size).coerceAtLeast(0)
            val sampledDownbeats = if (downbeatBudget == 0 || downbeats.isEmpty()) {
                emptyList()
            } else {
                val stride = (downbeats.size - 1).coerceAtLeast(1).toDouble() / downbeatBudget.coerceAtLeast(1)
                (0 until downbeatBudget).map { idx ->
                    downbeats[(idx * stride).toInt().coerceIn(0, downbeats.lastIndex)]
                }
            }
            (priority + sampledDownbeats).distinct().sorted()
        }
        return selected.map { ExitCandidate(it, formatTime(it, sampleRate)) }
    }

    private fun formatTime(frames: Long, sampleRate: Int): String =
        EngineSnapshot.formatPosition(frames, sampleRate)

    private fun applyPostExitFade(
        clips: MutableList<CourseMedleyCatalog.MedleyClip>,
        primaryIdx: Int,
        secondaryIdx: Int?,
        part: StagePartInfo,
        secHeader: AssetHeader?,
        fadeStartTimeline: Long,
        fadeFrames: Long,
        sampleRate: Int,
        allowInClipFallback: Boolean,
        delayFrames: Long = 0L,
        secondaryFadeFrames: Long = fadeFrames,
        allowSecondaryInClipFallback: Boolean = allowInClipFallback,
    ) {
        if (fadeFrames > 0L) {
            applyClipPostExitFade(
                clips = clips,
                clipIdx = primaryIdx,
                stem = part.stem,
                maxFrames = part.frames,
                supportsLoop = part.supportsLoop,
                loopStart = part.loopStart,
                loopEnd = part.loopEnd,
                fadeStartTimeline = fadeStartTimeline,
                fadeFrames = fadeFrames,
                delayFrames = delayFrames,
                sampleRate = sampleRate,
                allowInClipFallback = allowInClipFallback,
            )
        }
        if (secondaryFadeFrames > 0L && secondaryIdx != null && secHeader != null) {
            val secLoopStart = secHeader.loopStart ?: part.loopStart
            val secLoopEnd = secHeader.loopEnd ?: part.loopEnd
            val secSupportsLoop = part.supportsLoop && secLoopStart != null && secLoopEnd != null && secLoopEnd > secLoopStart && secLoopEnd <= secHeader.frames
            applyClipPostExitFade(
                clips = clips,
                clipIdx = secondaryIdx,
                stem = secHeader.stem,
                maxFrames = secHeader.frames,
                supportsLoop = secSupportsLoop,
                loopStart = secLoopStart,
                loopEnd = secLoopEnd,
                fadeStartTimeline = fadeStartTimeline,
                fadeFrames = secondaryFadeFrames,
                delayFrames = 0L,
                sampleRate = sampleRate,
                allowInClipFallback = allowSecondaryInClipFallback,
            )
        }
    }

    private fun applyClipPostExitFade(
        clips: MutableList<CourseMedleyCatalog.MedleyClip>,
        clipIdx: Int,
        stem: String,
        maxFrames: Long,
        supportsLoop: Boolean,
        loopStart: Long?,
        loopEnd: Long?,
        fadeStartTimeline: Long,
        fadeFrames: Long,
        sampleRate: Int,
        allowInClipFallback: Boolean,
        delayFrames: Long = 0L,
    ) {
        if (fadeFrames <= 0L) return
        val baseClip = clips[clipIdx]
        val exitSrcPos = baseClip.sourceOffset + baseClip.sourceLength
        val availAfterExit = (maxFrames - exitSrcPos).coerceAtLeast(0L)
        val totalTail = delayFrames + fadeFrames

        if (!supportsLoop || loopStart == null || loopEnd == null || loopEnd <= loopStart) {
            if (availAfterExit > 0L) {
                val extendFrames = minOf(totalTail, availAfterExit)
                val effectiveFade = (extendFrames - delayFrames).coerceAtLeast(0L)
                clips[clipIdx] = baseClip.copy(
                    sourceLength = baseClip.sourceLength + extendFrames,
                    fadeOut = effectiveFade,
                )
            } else if (allowInClipFallback) {
                val clampedFade = fadeFrames.coerceIn(0L, (baseClip.sourceLength - baseClip.fadeIn).coerceAtLeast(0L))
                clips[clipIdx] = baseClip.copy(fadeOut = clampedFade)
            }
            return
        }

        val l0 = loopStart
        val l1 = minOf(loopEnd, maxFrames)
        val loopLen = (l1 - l0).coerceAtLeast(1L)
        val firstAvail = when {
            exitSrcPos < l1 -> l1 - exitSrcPos
            exitSrcPos == l1 -> 0L
            else -> availAfterExit
        }

        if (totalTail <= firstAvail) {
            clips[clipIdx] = baseClip.copy(
                sourceLength = baseClip.sourceLength + totalTail,
                fadeOut = fadeFrames,
            )
            return
        }

        if (firstAvail == 0L && totalTail <= loopLen) {
            clips += CourseMedleyCatalog.MedleyClip(
                stem = stem,
                start = fadeStartTimeline,
                sourceOffset = l0,
                sourceLength = totalTail,
                fadeOut = fadeFrames,
            )
            return
        }

        if (delayFrames > 0L) {
            // Emit the full-volume hold portion first (wrapping across loopEnd if needed),
            // then run the standard post-exit fade starting at fadeStartTimeline + delayFrames.
            var holdRem = delayFrames
            var curTimeline = fadeStartTimeline
            var curClipIdx = clipIdx
            if (firstAvail > 0L) {
                val holdHere = minOf(holdRem, firstAvail)
                clips[curClipIdx] = baseClip.copy(sourceLength = baseClip.sourceLength + holdHere)
                holdRem -= holdHere
                curTimeline += holdHere
            }
            while (holdRem > 0L) {
                val chunk = minOf(holdRem, loopLen)
                curClipIdx = clips.size
                clips += CourseMedleyCatalog.MedleyClip(
                    stem = stem,
                    start = curTimeline,
                    sourceOffset = l0,
                    sourceLength = chunk,
                )
                holdRem -= chunk
                curTimeline += chunk
            }
            applyClipPostExitFade(
                clips = clips,
                clipIdx = curClipIdx,
                stem = stem,
                maxFrames = maxFrames,
                supportsLoop = true,
                loopStart = l0,
                loopEnd = l1,
                fadeStartTimeline = curTimeline,
                fadeFrames = fadeFrames,
                sampleRate = sampleRate,
                allowInClipFallback = allowInClipFallback,
                delayFrames = 0L,
            )
            return
        }

        if (firstAvail > 0L) {
            val baseLen = baseClip.sourceLength
            clips[clipIdx] = baseClip.copy(
                sourceLength = baseLen + firstAvail,
                fadeOut = 0L,
                volumePoints = buildFadeVolumePoints(
                    localFadeStart = baseLen,
                    fadeOffset = 0L,
                    fadeSpan = firstAvail,
                    totalFadeFrames = fadeFrames,
                    includeHeadUnity = baseLen > 0L,
                ),
            )
        }

        var emitted = firstAvail
        var wrapCursor = fadeStartTimeline + firstAvail
        while (emitted < fadeFrames) {
            val chunk = minOf(fadeFrames - emitted, loopLen)
            val isLastChunk = emitted + chunk == fadeFrames
            val tailMicroFade = if (isLastChunk) minOf(chunk, (sampleRate / 40L).coerceAtLeast(1L)) else 0L
            val vPoints = buildFadeVolumePoints(
                localFadeStart = 0L,
                fadeOffset = emitted,
                fadeSpan = chunk,
                totalFadeFrames = fadeFrames,
                includeHeadUnity = false,
            )
            clips += CourseMedleyCatalog.MedleyClip(
                stem = stem,
                start = wrapCursor,
                sourceOffset = l0,
                sourceLength = chunk,
                fadeIn = 0L,
                fadeOut = tailMicroFade,
                volumePoints = vPoints,
            )
            emitted += chunk
            wrapCursor += chunk
        }
    }

    private fun buildFadeVolumePoints(
        localFadeStart: Long,
        fadeOffset: Long,
        fadeSpan: Long,
        totalFadeFrames: Long,
        includeHeadUnity: Boolean,
    ): List<com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint> {
        if (fadeSpan <= 0L || totalFadeFrames <= 0L) return emptyList()
        val pts = ArrayList<com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint>(36)
        if (includeHeadUnity && localFadeStart > 0L) {
            pts += com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint(0L, 0.0)
        }
        val steps = minOf(32, fadeSpan.toInt().coerceAtLeast(1))
        var prevFrame = if (pts.isEmpty()) -1L else pts.last().frame
        for (k in 0..steps) {
            val delta = (fadeSpan * k) / steps
            val localFrame = localFadeStart + delta
            if (localFrame <= prevFrame) continue
            val fadePos = (fadeOffset + delta).coerceIn(0L, totalFadeFrames)
            val amp = (1.0 - fadePos.toDouble() / totalFadeFrames.toDouble()).coerceIn(0.0, 1.0)
            val db = if (amp <= 1.585e-5) -96.0 else (20.0 * kotlin.math.log10(amp)).coerceIn(-96.0, 0.0)
            pts += com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint(localFrame, db)
            prevFrame = localFrame
        }
        return pts
    }

    private fun sliceVolumePoints(
        points: List<com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint>,
        trimHead: Long,
        length: Long,
    ): List<com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint> {
        if (points.isEmpty() || length <= 0L) return emptyList()
        fun valueAt(frame: Long): Double {
            if (frame <= points.first().frame) return points.first().value
            if (frame >= points.last().frame) return points.last().value
            for (i in 1 until points.size) {
                val b = points[i]
                if (frame <= b.frame) {
                    val a = points[i - 1]
                    val span = (b.frame - a.frame).toDouble()
                    val t = if (span <= 0.0) 0.0 else (frame - a.frame).toDouble() / span
                    return (a.value + (b.value - a.value) * t).coerceIn(-96.0, 0.0)
                }
            }
            return points.last().value
        }
        val trimEnd = trimHead + length
        val out = ArrayList<com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint>(points.size + 2)
        out += com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint(0L, valueAt(trimHead))
        var prevFrame = 0L
        points.forEach { p ->
            if (p.frame in (trimHead + 1L) until trimEnd) {
                val f = p.frame - trimHead
                if (f > prevFrame) {
                    out += com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint(f, p.value.coerceIn(-96.0, 0.0), p.shape)
                    prevFrame = f
                }
            }
        }
        if (length > prevFrame) {
            out += com.mylittleblover.mkwplayer.graph.MusicGraphBuilder.EnvelopePoint(length, valueAt(trimEnd))
        }
        return out
    }

    private data class RoleDef(
        val roleKey: String,
        val minfStage: String,
        val minfRoles: List<String>,
        val candidateFileNames: List<String>,
        val secondaryFileName: String? = null,
    )

    private fun standardRoleDefinitions(stage: String): List<RoleDef> = buildList {
        add(RoleDef("Pre_Short", stage, listOf("Pre_Short", "Pre_Short_2"), listOf("BGM_Stage_${stage}_Pre_Short.bwav")))
        add(RoleDef("Pre", stage, listOf("Pre", "Pre_2"), listOf("BGM_Stage_${stage}_Pre.bwav")))
        add(RoleDef("Bridge", stage, listOf("Bridge", "Bridge_Short_1"), listOf("BGM_Stage_${stage}_Bridge.bwav")))
        add(RoleDef("Main", stage, listOf("Full"), listOf("BGM_Stage_$stage.bwav")))
        if (stage == "KoopaTroopaBeach") {
            add(
                RoleDef(
                    "Main_Speaker",
                    stage,
                    listOf("Full"),
                    listOf("BGM_Stage_KoopaTroopaBeach.bwav"),
                    "SE_Stage_KoopaTroopaBeach_Speaker_Normal.bwav",
                ),
            )
        }
        if (stage == "DonkeyKong") {
            for (n in 2..5) {
                add(RoleDef("Region$n", stage, listOf("Pattern$n", "Full"), listOf("BGM_Stage_${stage}_Pattern$n.bwav")))
            }
        }
        if (stage in setOf("Cinema", "SnowMountain")) {
            add(RoleDef("Pattern2", stage, listOf("Full"), listOf("BGM_Stage_${stage}_Pattern2.bwav")))
            add(RoleDef("Fast_Pattern2", stage, listOf("Fast", "FL"), listOf("BGM_Stage_${stage}_Fast_Pattern2.bwav")))
        }
        add(RoleDef("Fast", stage, listOf("Fast", "FL"), listOf("BGM_Stage_${stage}_Fast.bwav", "BGM_Stage_${stage}_FL.bwav")))
        if (stage == "KoopaTroopaBeach") {
            add(
                RoleDef(
                    "Fast_Speaker",
                    stage,
                    listOf("Fast", "FL"),
                    listOf("BGM_Stage_KoopaTroopaBeach_Fast.bwav"),
                    "SE_Stage_KoopaTroopaBeach_Speaker_Fast.bwav",
                ),
            )
        }
    }

    private fun rainbowRoleDefinitions(): List<RoleDef> = buildList {
        add(RoleDef("Area1_Pre", "Rainbow", listOf("Area1_Pre"), listOf("BGM_Rainbow_Area1_Pre.bwav")))
        add(RoleDef("Area1_Bridge", "Rainbow", listOf("Area1_Bridge"), listOf("BGM_Rainbow_Area1_Bridge.bwav")))
        add(RoleDef("Area1_Inst", "Rainbow", listOf("Area1_Inst", "Area1"), listOf("BGM_Rainbow_Area1_Inst.bwav")))
        add(RoleDef("Area1", "Rainbow", listOf("Area1"), listOf("BGM_Rainbow_Area1_Inst.bwav"), "BGM_Rainbow_Area1_Melody.bwav"))
        add(RoleDef("Area2", "Rainbow", listOf("Area2"), listOf("BGM_Rainbow_Area2.bwav")))
        for (n in 0..4) {
            add(RoleDef("Area2_Outro%02d".format(n), "Rainbow", listOf("Area2_Outro", "Outro%02d".format(n)), listOf("BGM_Rainbow_Area2_Outro%02d.bwav".format(n))))
        }
        add(RoleDef("Area3_Pre", "Rainbow", listOf("Area3_Pre"), listOf("BGM_Rainbow_Area3_Pre.bwav")))
        add(RoleDef("Area3", "Rainbow", listOf("Area3"), listOf("BGM_Rainbow_Area3.bwav")))
        add(RoleDef("Final_Pre", "Rainbow", listOf("Final_Pre"), listOf("BGM_Rainbow_Final_Pre.bwav")))
        add(RoleDef("Final", "Rainbow", listOf("Final"), listOf("BGM_Rainbow_Final.bwav")))
    }

    private fun minfStageFor(courseCode: String, roleKey: String): String =
        if (courseCode == "Rainbow") "Rainbow" else courseCode

    private fun mapRoleKeyToMinfRole(courseCode: String, roleKey: String): List<String> = when {
        courseCode == "Rainbow" && roleKey == "Area1_Inst" -> listOf("Area1_Inst", "Area1")
        courseCode == "Rainbow" && roleKey.startsWith("Area2_Outro") -> listOf("Area2_Outro", "Outro${roleKey.removePrefix("Area2_Outro")}")
        courseCode == "Rainbow" -> listOf(roleKey)
        roleKey == "Pre_Short" -> listOf("Pre_Short", "Pre_Short_2")
        roleKey == "Pre" -> listOf("Pre", "Pre_2")
        roleKey == "Bridge" -> listOf("Bridge", "Bridge_Short_1")
        roleKey.startsWith("Region") -> listOf("Pattern${roleKey.removePrefix("Region")}", "Full")
        roleKey in setOf("Main", "Main_Speaker", "Pattern2") -> listOf("Full")
        roleKey in setOf("Fast", "Fast_Speaker", "Fast_Pattern2") -> listOf("Fast", "FL")
        else -> listOf(roleKey)
    }
}
