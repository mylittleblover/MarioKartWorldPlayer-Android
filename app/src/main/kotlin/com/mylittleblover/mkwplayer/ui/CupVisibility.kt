package com.mylittleblover.mkwplayer.ui

import com.mylittleblover.mkwplayer.library.CueCatalog
import com.mylittleblover.mkwplayer.unlock.CupEligibility
import com.mylittleblover.mkwplayer.player.UnlockUi

/**
 * Whether the Special Cup may be shown anywhere in the app.
 *
 * The contract is emphatic about this: hiding one card on one page is not enough. Search results, the
 * home page, the library, map pins, favourites, recently played, deep links and route candidates must
 * all use the same gate, because a back door in any one of them leaks the cup and its courses.
 *
 * Putting it in one object means a new screen cannot accidentally do it differently. Every projection
 * asks [mayReveal] and nothing else.
 */
internal object CupVisibility {

    /**
     * The gate.
     *
     * Takes the cup state rather than reading it, so a caller cannot hold a stale copy and a test can
     * drive every branch. Unlocked is the only state that reveals anything -- not pending, not
     * completed-but-unpersisted.
     */
    fun mayReveal(unlock: UnlockUi?): Boolean = unlock?.visible == true

    /**
     * Filters any list of course codes to what may be shown.
     *
     * [specialCupCourses] is the set the caller believes belongs to the cup. It is passed in rather
     * than guessed from names, because a name-based rule would silently stop hiding the cup the moment
     * a course was renamed.
     */
    fun visibleCourses(
        courses: List<String>,
        specialCupCourses: Set<String>,
        unlock: UnlockUi?,
    ): List<String> = if (mayReveal(unlock)) {
        courses
    } else {
        courses.filterNot { it in specialCupCourses }
    }

    /**
     * True when a specific course must be hidden.
     *
     * A single entry point for one-item checks, so a detail page cannot decide differently from a list.
     */
    fun hides(code: String, specialCupCourses: Set<String>, unlock: UnlockUi?): Boolean =
        !mayReveal(unlock) && code in specialCupCourses

    /**
     * A reason for the UI, or null when the cup is visible.
     *
     * The reason never names the cup: telling a user "there is a hidden cup" is the same leak as
     * showing its card. It says only that something is not available.
     */
    fun hiddenReason(unlock: UnlockUi?): String? =
        if (mayReveal(unlock)) null else "Some content is not available yet."

    /**
     * Whether the cup's own courses may be played.
     *
     * Separate from visibility because a route could reference a cup course while the cup is locked;
     * the route must then refuse to play it rather than silently substituting something else.
     */
    fun mayPlay(code: String, specialCupCourses: Set<String>, unlock: UnlockUi?): Boolean =
        !hides(code, specialCupCourses, unlock)
}

/**
 * What a course detail page shows, reduced from the catalogues.
 *
 * Kept as data so the wording and the decisions are testable; the composable only lays it out.
 */
internal data class CourseDetail(
    val code: String,
    val displayName: String,
    val outroCount: Int,
    val hasBridge: Boolean,
    /** Cue positions, if this course is in the cue asset. */
    val cues: List<CueCatalog.Cue>,
    /** The caveat that must accompany the cue list. */
    val cueCaveat: String,
    /** True when this course's cues are absent from the bundled asset. */
    val cuesMissing: Boolean,
) {
    val hasCues: Boolean get() = cues.isNotEmpty()

    companion object {
        /**
         * Builds a detail page, or null when the course is unknown.
         *
         * A course absent from the cue asset is still shown, with [cuesMissing] set: the course exists,
         * its positions do not. Returning null instead would make a known course look like it does not
         * exist.
         */
        fun of(
            code: String,
            catalog: CueCatalog.Catalog?,
            displayName: String? = null,
        ): CourseDetail? {
            if (code.isBlank()) return null
            val course = catalog?.course(code)
            if (course == null && displayName == null) return null

            return CourseDetail(
                code = code,
                displayName = course?.displayName ?: displayName ?: code,
                outroCount = course?.outroCount ?: 0,
                hasBridge = course?.hasBridge ?: false,
                cues = course?.cues.orEmpty(),
                cueCaveat = catalog?.caveat()
                    ?: "Cue positions are not available in this build.",
                cuesMissing = course == null,
            )
        }
    }
}

/**
 * The line a course row shows about its cues.
 *
 * Says what the numbers are rather than implying they are the game's exits, and says nothing at all
 * when there are none -- an empty "0 cues" reads as a fact about the game rather than about the build.
 */
internal fun cueSummary(detail: CourseDetail): String? = when {
    detail.cuesMissing -> "Cue positions unavailable in this build"
    !detail.hasCues -> null
    else -> "${detail.cues.size} cue position${if (detail.cues.size == 1) "" else "s"} " +
        "(${detail.outroCount} outro${if (detail.outroCount == 1) "" else "s"} in the archive)"
}
