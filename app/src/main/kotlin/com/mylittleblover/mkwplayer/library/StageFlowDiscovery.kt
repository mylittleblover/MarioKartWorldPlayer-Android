package com.mylittleblover.mkwplayer.library

/**
 * Finds the parts of a stage flow among the files a user has picked.
 *
 * A flow needs up to five sibling files sharing a prefix:
 *
 * ```
 * BGM_Stage_Coaster_Pre_Short.bwav
 * BGM_Stage_Coaster_Pre.bwav
 * BGM_Stage_Coaster_Bridge.bwav
 * BGM_Stage_Coaster.bwav
 * BGM_Stage_Coaster_Outro00.bwav
 * ```
 *
 * Picking them one at a time is tedious and error-prone -- it is very easy to miss the bridge
 * or pick two outros. This groups whatever the user selected by the prefix before the first
 * role suffix, so one multi-select produces one flow.
 *
 * It does not invent parts: a group with only a main file yields a flow with one part, which is
 * a legitimate single-track listen rather than an error.
 */
internal object StageFlowDiscovery {

    /**
     * A group of files that belong to the same stage.
     *
     * @param prefix the shared stem, e.g. `BGM_Stage_Coaster`
     * @param parts parts grouped by role, in the order they were found
     */
    data class Group(
        val prefix: String,
        val parts: List<StageFlow.Part>,
    ) {
        /** True when the group has enough structure to be worth playing as a flow. */
        val hasStructure: Boolean
            get() = parts.count { it.role != StageFlow.Role.Main } > 0

        /** The roles present, for display. */
        val roles: List<StageFlow.Role> get() = parts.map { it.role }

        /** How many outros this group offers. */
        val outroCount: Int get() = parts.count { it.role == StageFlow.Role.Outro }
    }

    /** Suffixes that mark a structural part rather than part of the stage's base name. */
    private val SUFFIXES = listOf(
        "_Pre_Short",
        "_Pre",
        "_Bridge",
        "_Outro",
        "_Fast",
        "_SFC",
    )

    /**
     * The shared stem of a file name.
     *
     * Strips a role suffix, then any trailing outro number, so
     * `BGM_Stage_Coaster_Outro02.bwav` and `BGM_Stage_Coaster_Pre.bwav` both reduce to
     * `BGM_Stage_Coaster`.
     */
    fun prefixOf(fileName: String): String {
        val stem = fileName.substringBeforeLast('.')
        var result = stem
        // Longest suffix first: `_Pre_Short` must win over `_Pre`.
        for (suffix in SUFFIXES.sortedByDescending { it.length }) {
            val at = result.indexOf(suffix, ignoreCase = true)
            if (at > 0) {
                result = result.substring(0, at)
                break
            }
        }
        return result
    }

    /**
     * Groups parts by their stage prefix.
     *
     * A file that is not a structural part is skipped rather than guessed at: the alternative
     * would be putting a `_Fast` variant into the flow as if it were the main loop.
     */
    fun group(parts: List<StageFlow.Part>): List<Group> =
        parts.groupBy { prefixOf(it.fileName) }
            .map { (prefix, groupParts) ->
                Group(
                    prefix = prefix,
                    // Outros sorted by number so a picker shows them in a predictable order.
                    parts = groupParts.sortedWith(
                        compareBy({ it.role.ordinal }, { it.outroNumber ?: -1 }),
                    ),
                )
            }
            .sortedBy { it.prefix }

    /**
     * Chooses which group a user meant.
     *
     * The group with the most parts wins, then the alphabetically first: deterministic, and it
     * prefers the group that actually has structure over a stray single file.
     */
    fun dominant(groups: List<Group>): Group? =
        groups.sortedWith(compareByDescending<Group> { it.parts.size }.thenBy { it.prefix })
            .firstOrNull()

    /**
     * Builds a flow for a group.
     *
     * Returns the failure reason from [StageFlowReader.assemble] when the group cannot be used,
     * which happens for two mains or disagreeing sample rates -- both of which would otherwise
     * produce a graph the decoder rejects for unrelated reasons.
     */
    fun flowFor(
        group: Group,
        timing: StageFlow.Timing = StageFlow.DEFAULT_TIMING,
        provenance: StageFlow.Provenance = StageFlow.Provenance.PROVISIONAL,
    ): Result<StageFlow> = StageFlowReader
        .assemble(group.prefix, group.parts, timing)
        .map { it.copy(provenance = provenance) }
}
