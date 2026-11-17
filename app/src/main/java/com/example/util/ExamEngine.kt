package com.example.util

import com.example.data.model.AnswerOptionDto
import com.example.data.model.QuestionDto
import com.example.data.model.QuestionType
import com.example.data.model.StudentAnswerUpsertDto
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random

data class GradeInfo(
    val grade: String,
    val remark: String
)

data class ObjectiveGradingResult(
    val objectiveScore: Double,
    val maxObjectiveScore: Double,
    val correctCount: Int,
    val totalObjectiveCount: Int,
    val gradedAnswers: List<StudentAnswerUpsertDto>
)

object ExamEngine {

    fun prepareQuestionsForAttempt(
        questions: List<QuestionDto>,
        seed: Long,
        randomizeQuestions: Boolean,
        randomizeOptions: Boolean
    ): List<QuestionDto> {
        val rng = Random(seed)
        val orderedQuestions = if (randomizeQuestions) {
            questions.shuffled(rng)
        } else {
            questions.sortedBy { it.orderIndex }
        }
        return orderedQuestions.map { q ->
            val qType = QuestionType.fromDb(q.questionType)
            val opts = when {
                qType == QuestionType.MCQ && randomizeOptions -> q.answerOptions.shuffled(Random(seed + q.id.hashCode()))
                else -> q.answerOptions.sortedBy { it.orderIndex }
            }
            q.copy(answerOptions = opts)
        }
    }

    fun gradeObjectiveAnswers(
        attemptId: String,
        questions: List<QuestionDto>,
        selectedOptionIds: Map<String, String>,
        textResponses: Map<String, String>,
        markedForReview: Set<String>
    ): ObjectiveGradingResult {
        var score = 0.0
        var maxScore = 0.0
        var correctCount = 0
        var objectiveCount = 0
        val graded = mutableListOf<StudentAnswerUpsertDto>()

        for (question in questions) {
            val qType = QuestionType.fromDb(question.questionType)
            val selectedOptId = selectedOptionIds[question.id]
            val textResp = textResponses[question.id]?.trim()
            val isMarked = question.id in markedForReview

            when (qType) {
                QuestionType.MCQ -> {
                    objectiveCount++
                    maxScore += question.marks
                    val chosenOption: AnswerOptionDto? = question.answerOptions.firstOrNull { it.id == selectedOptId }
                    val isRight = chosenOption?.isCorrect == true
                    if (isRight) {
                        correctCount++
                        score += question.marks
                    }
                    graded += StudentAnswerUpsertDto(
                        attemptId = attemptId,
                        questionId = question.id,
                        selectedOptionId = selectedOptId,
                        textResponse = null,
                        isMarkedForReview = isMarked,
                        isCorrect = isRight,
                        marksAwarded = if (isRight) question.marks else 0.0
                    )
                }

                QuestionType.TRUE_FALSE -> {
                    objectiveCount++
                    maxScore += question.marks
                    val expected = question.correctTextAnswer?.trim()?.lowercase(Locale.ROOT)
                    val actual = textResp?.lowercase(Locale.ROOT)
                        ?: question.answerOptions.firstOrNull { it.id == selectedOptId }?.optionText?.trim()?.lowercase(Locale.ROOT)
                    val isRight = !expected.isNullOrBlank() && actual == expected
                    if (isRight) {
                        correctCount++
                        score += question.marks
                    }
                    graded += StudentAnswerUpsertDto(
                        attemptId = attemptId,
                        questionId = question.id,
                        selectedOptionId = selectedOptId,
                        textResponse = textResp,
                        isMarkedForReview = isMarked,
                        isCorrect = isRight,
                        marksAwarded = if (isRight) question.marks else 0.0
                    )
                }

                QuestionType.FILL_GAP -> {
                    objectiveCount++
                    maxScore += question.marks
                    val accepted = question.correctTextAnswer
                        ?.split("|")
                        ?.map { it.trim().lowercase(Locale.ROOT) }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    val actual = textResp?.lowercase(Locale.ROOT).orEmpty()
                    val isRight = actual.isNotBlank() && accepted.contains(actual)
                    if (isRight) {
                        correctCount++
                        score += question.marks
                    }
                    graded += StudentAnswerUpsertDto(
                        attemptId = attemptId,
                        questionId = question.id,
                        selectedOptionId = null,
                        textResponse = textResp,
                        isMarkedForReview = isMarked,
                        isCorrect = isRight,
                        marksAwarded = if (isRight) question.marks else 0.0
                    )
                }

                QuestionType.THEORY -> {
                    graded += StudentAnswerUpsertDto(
                        attemptId = attemptId,
                        questionId = question.id,
                        selectedOptionId = null,
                        textResponse = textResp,
                        isMarkedForReview = isMarked,
                        isCorrect = null,
                        marksAwarded = 0.0
                    )
                }
            }
        }

        return ObjectiveGradingResult(
            objectiveScore = score,
            maxObjectiveScore = maxScore,
            correctCount = correctCount,
            totalObjectiveCount = objectiveCount,
            gradedAnswers = graded
        )
    }

    fun calculateGrade(totalOutOf100: Double): GradeInfo = when {
        totalOutOf100 >= 75.0 -> GradeInfo("A1", "Distinction")
        totalOutOf100 >= 70.0 -> GradeInfo("B2", "Very Good")
        totalOutOf100 >= 65.0 -> GradeInfo("B3", "Good")
        totalOutOf100 >= 60.0 -> GradeInfo("C4", "Credit")
        totalOutOf100 >= 55.0 -> GradeInfo("C5", "Credit")
        totalOutOf100 >= 50.0 -> GradeInfo("C6", "Credit")
        totalOutOf100 >= 45.0 -> GradeInfo("D7", "Pass")
        totalOutOf100 >= 40.0 -> GradeInfo("E8", "Pass")
        else -> GradeInfo("F9", "Fail")
    }

    fun computePositions(scoresByStudentId: Map<String, Double>): Map<String, Int> {
        val sorted = scoresByStudentId.entries.sortedByDescending { it.value }
        val result = mutableMapOf<String, Int>()
        var lastScore: Double? = null
        var currentRank = 0
        sorted.forEachIndexed { index, entry ->
            if (lastScore == null || entry.value != lastScore) {
                currentRank = index + 1
                lastScore = entry.value
            }
            result[entry.key] = currentRank
        }
        return result
    }

    fun formatCountdown(totalSeconds: Int): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val hours = clamped / 3600
        val minutes = (clamped % 3600) / 60
        val seconds = clamped % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatNairaFromKobo(amountKobo: Long): String {
        val naira = amountKobo / 100.0
        val formatter = NumberFormat.getNumberInstance(Locale("en", "NG")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "₦${formatter.format(naira)}"
    }

    fun formatOrdinal(rank: Int?): String {
        if (rank == null || rank <= 0) return "-"
        val mod100 = rank % 100
        val suffix = if (mod100 in 11..13) "th" else when (rank % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$rank$suffix"
    }
}
