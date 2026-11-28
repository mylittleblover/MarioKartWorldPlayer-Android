package com.mylittleblover.mkwplayer.unlock

import androidx.annotation.StringRes
import com.mylittleblover.mkwplayer.R

/**
 * One question in the 10-question Special Cup Unlock Challenge (#24).
 *
 * All user-visible text is referenced via `@StringRes` ids localized across all 4 app languages
 * (`en`, `zh-Hans`, `zh-Hant`, `ja`) in `unlock_quiz_theme_strings.xml`.
 */
internal data class SpecialCupQuizQuestion(
    /** 1-based question number (`1..10`). */
    val number: Int,
    @StringRes val questionRes: Int,
    @StringRes val optionResIds: List<Int>,
    /** 0-based index into [optionResIds]: 0=A, 1=B, 2=C, 3=D. */
    val correctOptionIndex: Int,
    val isFinalChallenge: Boolean = false,
) {
    init {
        require(optionResIds.size == 4) { "Quiz question #$number must have exactly 4 options" }
        require(correctOptionIndex in optionResIds.indices) {
            "Correct option index $correctOptionIndex out of range for question #$number"
        }
    }

    fun isCorrect(optionIndex: Int): Boolean = optionIndex == correctOptionIndex
}

/**
 * Official 10-question Special Cup Unlock Challenge bank and stateful quiz session.
 *
 * Answer key: 1:C, 2:B, 3:A, 4:D, 5:C, 6:B, 7:D, 8:A, 9:C, 10:B.
 */
internal object SpecialCupQuiz {

    val optionLetters: List<String> = listOf("A", "B", "C", "D")

    val questions: List<SpecialCupQuizQuestion> = listOf(
        SpecialCupQuizQuestion(
            number = 1,
            questionRes = R.string.quiz_q1_question,
            optionResIds = listOf(R.string.quiz_q1_a, R.string.quiz_q1_b, R.string.quiz_q1_c, R.string.quiz_q1_d),
            correctOptionIndex = 2, // C: 2025年6月5日
        ),
        SpecialCupQuizQuestion(
            number = 2,
            questionRes = R.string.quiz_q2_question,
            optionResIds = listOf(R.string.quiz_q2_a, R.string.quiz_q2_b, R.string.quiz_q2_c, R.string.quiz_q2_d),
            correctOptionIndex = 1, // B: 8个
        ),
        SpecialCupQuizQuestion(
            number = 3,
            questionRes = R.string.quiz_q3_question,
            optionResIds = listOf(R.string.quiz_q3_a, R.string.quiz_q3_b, R.string.quiz_q3_c, R.string.quiz_q3_d),
            correctOptionIndex = 0, // A: 8条
        ),
        SpecialCupQuizQuestion(
            number = 4,
            questionRes = R.string.quiz_q4_question,
            optionResIds = listOf(R.string.quiz_q4_a, R.string.quiz_q4_b, R.string.quiz_q4_c, R.string.quiz_q4_d),
            correctOptionIndex = 3, // D: 4条
        ),
        SpecialCupQuizQuestion(
            number = 5,
            questionRes = R.string.quiz_q5_question,
            optionResIds = listOf(R.string.quiz_q5_a, R.string.quiz_q5_b, R.string.quiz_q5_c, R.string.quiz_q5_d),
            correctOptionIndex = 2, // C: 24名
        ),
        SpecialCupQuizQuestion(
            number = 6,
            questionRes = R.string.quiz_q6_question,
            optionResIds = listOf(R.string.quiz_q6_a, R.string.quiz_q6_b, R.string.quiz_q6_c, R.string.quiz_q6_d),
            correctOptionIndex = 1, // B: 4名
        ),
        SpecialCupQuizQuestion(
            number = 7,
            questionRes = R.string.quiz_q7_question,
            optionResIds = listOf(R.string.quiz_q7_a, R.string.quiz_q7_b, R.string.quiz_q7_c, R.string.quiz_q7_d),
            correctOptionIndex = 3, // D: 自由行驶
        ),
        SpecialCupQuizQuestion(
            number = 8,
            questionRes = R.string.quiz_q8_question,
            optionResIds = listOf(R.string.quiz_q8_a, R.string.quiz_q8_b, R.string.quiz_q8_c, R.string.quiz_q8_d),
            correctOptionIndex = 0, // A: 奖杯城
        ),
        SpecialCupQuizQuestion(
            number = 9,
            questionRes = R.string.quiz_q9_question,
            optionResIds = listOf(R.string.quiz_q9_a, R.string.quiz_q9_b, R.string.quiz_q9_c, R.string.quiz_q9_d),
            correctOptionIndex = 2, // C: 进入速度更快的最后一圈音乐版本
        ),
        SpecialCupQuizQuestion(
            number = 10,
            questionRes = R.string.quiz_q10_question,
            optionResIds = listOf(R.string.quiz_q10_a, R.string.quiz_q10_b, R.string.quiz_q10_c, R.string.quiz_q10_d),
            correctOptionIndex = 1, // B: 彩虹之路，特别杯
            isFinalChallenge = true,
        ),
    )

    val totalQuestions: Int get() = questions.size

    enum class AnswerOutcome {
        CORRECT_NEXT,
        ALL_PASSED,
        WRONG_TRY_AGAIN,
        ALREADY_COMPLETED,
        INVALID_OPTION,
    }

    /** Immutable snapshot of a quiz walkthrough, safe to hold in Compose state or unit tests. */
    data class SessionState(
        val currentIndex: Int = 0,
        val wrongOptionIndex: Int? = null,
        val completed: Boolean = false,
    ) {
        val total: Int get() = questions.size

        val currentQuestion: SpecialCupQuizQuestion?
            get() = if (completed) null else questions.getOrNull(currentIndex)

        val currentNumber: Int
            get() = if (completed) total else (currentIndex + 1).coerceIn(1, total)

        val hasWrongAttempt: Boolean
            get() = !completed && wrongOptionIndex != null

        val progressFraction: Float
            get() = if (completed) 1f else currentIndex.toFloat() / total.coerceAtLeast(1).toFloat()

        fun submitAnswer(optionIndex: Int): Pair<SessionState, AnswerOutcome> {
            if (completed) return this to AnswerOutcome.ALREADY_COMPLETED
            val q = questions.getOrNull(currentIndex) ?: return this to AnswerOutcome.ALREADY_COMPLETED
            if (optionIndex !in q.optionResIds.indices) {
                return this to AnswerOutcome.INVALID_OPTION
            }
            if (!q.isCorrect(optionIndex)) {
                return copy(wrongOptionIndex = optionIndex) to AnswerOutcome.WRONG_TRY_AGAIN
            }
            val nextIndex = currentIndex + 1
            return if (nextIndex >= questions.size) {
                copy(
                    currentIndex = questions.lastIndex,
                    wrongOptionIndex = null,
                    completed = true,
                ) to AnswerOutcome.ALL_PASSED
            } else {
                copy(
                    currentIndex = nextIndex,
                    wrongOptionIndex = null,
                    completed = false,
                ) to AnswerOutcome.CORRECT_NEXT
            }
        }

        fun retryCurrent(): SessionState = copy(wrongOptionIndex = null)

        fun reset(): SessionState = SessionState()
    }
}
