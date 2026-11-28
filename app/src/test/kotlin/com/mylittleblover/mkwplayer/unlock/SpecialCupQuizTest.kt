package com.mylittleblover.mkwplayer.unlock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpecialCupQuizTest {

    @Test
    fun quizContainsExactlyTenQuestionsWithVerifiedAnswerKey() {
        assertEquals(10, SpecialCupQuiz.totalQuestions)
        assertEquals(10, SpecialCupQuiz.questions.size)

        // Expected answer key: 1:C, 2:B, 3:A, 4:D, 5:C, 6:B, 7:D, 8:A, 9:C, 10:B
        val expectedIndices = listOf(2, 1, 0, 3, 2, 1, 3, 0, 2, 1)
        SpecialCupQuiz.questions.forEachIndexed { idx, q ->
            assertEquals(idx + 1, q.number)
            assertEquals(4, q.optionResIds.size)
            assertEquals(expectedIndices[idx], q.correctOptionIndex, "Mismatch on Q${idx + 1}")
            assertEquals(idx == 9, q.isFinalChallenge, "Only Q10 is the Final Challenge")
        }
    }

    @Test
    fun wrongAnswerGivesImmediateFeedbackAndSupportsRetry() {
        val initial = SpecialCupQuiz.SessionState()
        assertEquals(1, initial.currentNumber)
        assertFalse(initial.hasWrongAttempt)

        val (afterWrong, outcome) = initial.submitAnswer(0) // Q1 correct is C (2)
        assertEquals(SpecialCupQuiz.AnswerOutcome.WRONG_TRY_AGAIN, outcome)
        assertEquals(0, afterWrong.currentIndex)
        assertEquals(0, afterWrong.wrongOptionIndex)
        assertTrue(afterWrong.hasWrongAttempt)
        assertFalse(afterWrong.completed)

        val afterRetry = afterWrong.retryCurrent()
        assertFalse(afterRetry.hasWrongAttempt)
        assertNull(afterRetry.wrongOptionIndex)

        // Answering right directly after a wrong attempt also advances cleanly.
        val (advanced, nextOutcome) = afterWrong.submitAnswer(2)
        assertEquals(SpecialCupQuiz.AnswerOutcome.CORRECT_NEXT, nextOutcome)
        assertEquals(1, advanced.currentIndex)
        assertEquals(2, advanced.currentNumber)
        assertFalse(advanced.hasWrongAttempt)
    }

    @Test
    fun answeringAllTenQuestionsCorrectlyCompletesTheQuiz() {
        val key = listOf(2, 1, 0, 3, 2, 1, 3, 0, 2, 1)
        var session = SpecialCupQuiz.SessionState()

        key.forEachIndexed { idx, correctIndex ->
            val (next, outcome) = session.submitAnswer(correctIndex)
            if (idx < key.lastIndex) {
                assertEquals(SpecialCupQuiz.AnswerOutcome.CORRECT_NEXT, outcome)
                assertFalse(next.completed)
            } else {
                assertEquals(SpecialCupQuiz.AnswerOutcome.ALL_PASSED, outcome)
                assertTrue(next.completed)
                assertEquals(1f, next.progressFraction)
                assertNull(next.currentQuestion)
            }
            session = next
        }

        val (afterExtra, extraOutcome) = session.submitAnswer(0)
        assertEquals(SpecialCupQuiz.AnswerOutcome.ALREADY_COMPLETED, extraOutcome)
        assertEquals(session, afterExtra)
    }

    @Test
    fun outOfBoundsOptionIsRejectedSafely() {
        val s = SpecialCupQuiz.SessionState()
        val (same, outcome) = s.submitAnswer(99)
        assertEquals(SpecialCupQuiz.AnswerOutcome.INVALID_OPTION, outcome)
        assertEquals(s, same)
    }
}
