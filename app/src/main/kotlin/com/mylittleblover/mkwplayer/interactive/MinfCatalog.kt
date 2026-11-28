package com.mylittleblover.mkwplayer.interactive

import org.json.JSONObject

/** Raw records stay distinct by archive/offset/hash, even when names repeat. */
internal class MinfCatalog(val records: List<Record>) {
    data class Beat(val frame: Long, val label: Int)
    data class Event(val name: String, val frames: List<Long>)
    data class Record(
        val stage: String, val role: String, val name: String, val logicalName: String,
        val phase: Long, val rate: Int, val bpm: Double,
        val loopStart: Long, val loopEnd: Long,
        val archive: String, val offset: Long, val hash: String,
        val beats: List<Beat>, val events: List<Event>,
    ) {
        val loopCandidates: List<Long> get() = beats.filter {
            it.label == 1 && it.frame in loopStart..loopEnd
        }.map { it.frame }

        /**
         * Returns loop-window exit frames aligned to a verified AMTA `Pre_TransitionGrid:` beat
         * stride anchored to the first downbeat (`label == 1`), with ±2-sample tolerance for MINF
         * boundary rounding clamped inside `[loopStart, loopEnd]`.
         */
        fun gridLoopCandidates(gridBeats: Int): List<Long> {
            val step = gridBeats.coerceAtLeast(1)
            val firstDownbeatIdx = beats.indexOfFirst { it.label == 1 }
            if (firstDownbeatIdx < 0) return loopCandidates
            val minFrame = (loopStart - 2L).coerceAtLeast(0L)
            val maxFrame = loopEnd + 2L
            val matched = beats.mapIndexedNotNull { idx, beat ->
                if (beat.frame in minFrame..maxFrame && Math.floorMod(idx - firstDownbeatIdx, step) == 0) {
                    beat.frame.coerceIn(loopStart, loopEnd)
                } else {
                    null
                }
            }.distinct().sorted()
            return matched.ifEmpty { loopCandidates }
        }

        fun event(name: String): List<Long>? = events.singleOrNull { it.name == name }?.frames
    }

    /** No fuzzy name fallback or arbitrary selection among duplicate records. */
    fun part(stage: String, role: String): Record? {
        val logical = if(stage=="Rainbow") "BGM_Rainbow_$role" else {
            val suffix=if(role=="Full") "" else "_$role"
            val pattern=Regex("_Pattern\\d+$").find(stage)?.value.orEmpty()
            "BGM_Stage_${stage.removeSuffix(pattern)}$suffix$pattern"
        }
        return records.singleOrNull { it.logicalName==logical }

    }

    companion object {
        /**
         * Verified v1.8.0 AMTA `Pre_TransitionGrid:` beat stride per stage/prelude role:
         * - `Rainbow` `Area1_Pre`: 16 beats (4 bars)
         * - `Cinema` / `Lava` (`Pre`, `Pre_Short`): 8 beats (2 bars)
         * - `BigTree` / `DinoBones` (`Pre`, `Pre_Short`): 3 beats (3/4 bar)
         * - All other courses and `Rainbow` `Area3_Pre` / `Final_Pre`: 4 beats (1 bar)
         */
        fun preTransitionGridBeats(stage: String, role: String): Int {
            val baseStage = stage.removePrefix("BGM_Stage_").replace(Regex("_Pattern\\d+$"), "")
            return when {
                baseStage == "Rainbow" && role == "Area1_Pre" -> 16
                baseStage == "Cinema" || baseStage == "Lava" -> 8
                baseStage == "BigTree" || baseStage == "DinoBones" -> 3
                else -> 4
            }
        }

        fun parse(text: String): MinfCatalog {
            val root = JSONObject(text)
            require(root.getString("version") == "2026-11-12")
            val rows = root.getJSONArray("records")
            return MinfCatalog((0 until rows.length()).map { i ->
                val r = rows.getJSONObject(i)
                fun number(key: String) = r.getLong(key).also { require(it >= 0) }
                val beats = r.getJSONArray("beats")
                val events = r.getJSONArray("events")
                Record(r.getString("stage"), r.getString("role"), r.getString("name"), r.getString("logicalName"),
                    number("phase"), r.getInt("rate").also { require(it == 48000) },
                    r.getDouble("bpm").also { require(it.isFinite() && it > 0) },
                    number("loopStart"), number("loopEnd"), r.getString("archive"),
                    number("offset"), r.getString("minfHash"),
                    (0 until beats.length()).map { j -> beats.getJSONObject(j).let {
                        Beat(it.getLong("frame"), it.getInt("label"))
                    } },
                    (0 until events.length()).map { j -> events.getJSONObject(j).let {
                        val frames = it.getJSONArray("frames")
                        Event(it.getString("name"), (0 until frames.length()).map { k -> frames.getLong(k) })
                    } },
                )
            })
        }
    }
}
