package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AnswerOptionDto
import com.example.data.model.QuestionDto
import com.example.util.CsvExcelHelper
import com.example.util.ExamEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GLA CBT", appName)
    }

    @Test
    fun `objective grading scores mcq true_false and fill_gap accurately`() {
        val questions = listOf(
            QuestionDto(
                id = "q1",
                examinationId = "e1",
                questionType = "mcq",
                prompt = "Capital of Nigeria?",
                marks = 2.0,
                answerOptions = listOf(
                    AnswerOptionDto(id = "o1", questionId = "q1", optionText = "Lagos", isCorrect = false),
                    AnswerOptionDto(id = "o2", questionId = "q1", optionText = "Abuja", isCorrect = true)
                )
            ),
            QuestionDto(
                id = "q2",
                examinationId = "e1",
                questionType = "true_false",
                prompt = "Water boils at 100C.",
                marks = 1.0,
                correctTextAnswer = "true"
            ),
            QuestionDto(
                id = "q3",
                examinationId = "e1",
                questionType = "fill_gap",
                prompt = "Symbol for Sodium",
                marks = 2.0,
                correctTextAnswer = "Na|Sodium"
            )
        )

        val result = ExamEngine.gradeObjectiveAnswers(
            attemptId = "att1",
            questions = questions,
            selectedOptionIds = mapOf("q1" to "o2"),
            textResponses = mapOf("q2" to "TRUE", "q3" to " na "),
            markedForReview = setOf("q3")
        )

        assertEquals(5.0, result.objectiveScore, 0.001)
        assertEquals(3, result.correctCount)
        assertEquals(3, result.totalObjectiveCount)
    }

    @Test
    fun `class positions rank ties properly`() {
        val ranks = ExamEngine.computePositions(
            mapOf("s1" to 92.0, "s2" to 85.0, "s3" to 85.0, "s4" to 70.0)
        )
        assertEquals(1, ranks["s1"])
        assertEquals(2, ranks["s2"])
        assertEquals(2, ranks["s3"])
        assertEquals(4, ranks["s4"])
    }

    @Test
    fun `csv helper parses sample template`() {
        val rows = CsvExcelHelper.parseQuestionsCsv(CsvExcelHelper.sampleQuestionsTemplate())
        assertEquals(4, rows.size)
        assertEquals("Mitochondrion", rows[0].options[1])
    }
}
