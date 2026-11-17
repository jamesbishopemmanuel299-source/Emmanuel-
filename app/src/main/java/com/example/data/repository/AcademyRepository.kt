package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.model.*
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseErrorParser
import com.example.util.ExamEngine
import com.example.util.ParsedQuestionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class AcademyRepository(
    private val client: SupabaseClient
) {
    private val api get() = client.api

    private fun <T> unwrapList(res: Response<List<T>>, fallback: String): Result<List<T>> =
        if (res.isSuccessful) Result.success(res.body().orEmpty())
        else Result.failure(IllegalStateException(SupabaseErrorParser.extractMessage(res, fallback)))

    private fun <T> unwrapFirst(res: Response<List<T>>, fallback: String): Result<T> {
        if (!res.isSuccessful) {
            return Result.failure(IllegalStateException(SupabaseErrorParser.extractMessage(res, fallback)))
        }
        val first = res.body()?.firstOrNull()
        return if (first != null) Result.success(first)
        else Result.failure(IllegalStateException("$fallback: Empty server response"))
    }

    // --- Core Metadata & Settings ---
    suspend fun getSchoolSettings(): SchoolSettingsDto? = withContext(Dispatchers.IO) {
        api.getSchoolSettings().body()?.firstOrNull()
    }

    suspend fun updateSchoolSettings(id: String, body: SchoolSettingsUpdateDto): Result<SchoolSettingsDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.updateSchoolSettings("eq.$id", body), "Failed to update settings") }

    suspend fun getSessions(): List<AcademicSessionDto> = withContext(Dispatchers.IO) {
        api.getAcademicSessions().body().orEmpty()
    }

    suspend fun createSession(dto: AcademicSessionInsertDto): Result<AcademicSessionDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createAcademicSession(dto), "Failed to create session") }

    suspend fun activateSession(id: String, allSessions: List<AcademicSessionDto>): Result<Unit> =
        withContext(Dispatchers.IO) {
            allSessions.forEach { session ->
                api.updateAcademicSession("eq.${session.id}", mapOf("is_active" to (session.id == id)))
            }
            Result.success(Unit)
        }

    suspend fun getClasses(): List<SchoolClassDto> = withContext(Dispatchers.IO) {
        api.getClasses().body().orEmpty()
    }

    suspend fun createClass(dto: SchoolClassInsertDto): Result<SchoolClassDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createClass(dto), "Failed to create class") }

    suspend fun getSubjects(): List<SubjectDto> = withContext(Dispatchers.IO) {
        api.getSubjects().body().orEmpty()
    }

    suspend fun createSubject(dto: SubjectInsertDto): Result<SubjectDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createSubject(dto), "Failed to create subject") }

    suspend fun getStudents(classId: String? = null): List<StudentDto> = withContext(Dispatchers.IO) {
        api.getStudents(classFilter = classId?.let { "eq.$it" }).body().orEmpty()
    }

    suspend fun getTeachers(): List<TeacherDto> = withContext(Dispatchers.IO) {
        api.getTeachers().body().orEmpty()
    }

    suspend fun getAllProfiles(): List<ProfileDto> = withContext(Dispatchers.IO) {
        api.getProfiles().body().orEmpty()
    }

    suspend fun toggleAccountActive(profileId: String, active: Boolean, actorId: String): Result<ProfileDto> =
        withContext(Dispatchers.IO) {
            val res = unwrapFirst(api.updateProfile("eq.$profileId", ProfileUpdateDto(isActive = active)), "Failed to update account")
            if (res.isSuccess) {
                api.insertAuditLog(AuditLogInsertDto(actorId, if (active) "ACCOUNT_ACTIVATED" else "ACCOUNT_DEACTIVATED", "profile", profileId))
            }
            res
        }

    suspend fun updateStudentPassportUrl(studentId: String, profileId: String, publicUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            api.updateStudent("eq.$studentId", mapOf("passport_url" to publicUrl))
            api.updateProfile("eq.$profileId", ProfileUpdateDto(avatarUrl = publicUrl))
            Result.success(Unit)
        }

    // --- Examinations & Questions ---
    suspend fun getExaminations(classId: String? = null): List<ExaminationDto> = withContext(Dispatchers.IO) {
        api.getExaminations(classFilter = classId?.let { "eq.$it" }).body().orEmpty()
    }

    suspend fun createExamination(dto: ExaminationInsertDto, actorId: String): Result<ExaminationDto> =
        withContext(Dispatchers.IO) {
            val res = unwrapFirst(api.createExamination(dto), "Failed to create examination")
            if (res.isSuccess) {
                api.insertAuditLog(AuditLogInsertDto(actorId, "EXAM_CREATED", "examination", res.getOrNull()?.id))
            }
            res
        }

    suspend fun publishExamResults(examId: String, published: Boolean): Result<ExaminationDto> =
        withContext(Dispatchers.IO) {
            unwrapFirst(api.updateExamination("eq.$examId", mapOf("results_published" to published)), "Failed to update exam result visibility")
        }

    suspend fun getQuestions(examId: String): List<QuestionDto> = withContext(Dispatchers.IO) {
        api.getQuestions(examFilter = "eq.$examId").body().orEmpty()
    }

    suspend fun addQuestionWithOptions(
        examId: String,
        type: QuestionType,
        prompt: String,
        marks: Double,
        imageUrl: String?,
        options: List<String>,
        correctOptionIndex: Int,
        correctTextAnswer: String?,
        markingGuide: String?,
        orderIndex: Int
    ): Result<QuestionDto> = withContext(Dispatchers.IO) {
        val qRes = api.createQuestion(
            QuestionInsertDto(
                examinationId = examId,
                questionType = type.dbValue,
                prompt = prompt,
                imageUrl = imageUrl,
                marks = marks,
                correctTextAnswer = correctTextAnswer,
                markingGuide = markingGuide,
                orderIndex = orderIndex
            )
        )
        val createdQ = unwrapFirst(qRes, "Failed to create question").getOrElse { return@withContext Result.failure(it) }

        if (type == QuestionType.MCQ && options.isNotEmpty()) {
            val optionInserts = options.mapIndexed { idx, text ->
                AnswerOptionInsertDto(
                    questionId = createdQ.id,
                    optionText = text,
                    isCorrect = idx == correctOptionIndex,
                    orderIndex = idx
                )
            }
            api.createAnswerOptions(optionInserts)
        }
        Result.success(createdQ)
    }

    suspend fun importQuestionsBulk(examId: String, rows: List<ParsedQuestionRow>): Result<Int> =
        withContext(Dispatchers.IO) {
            var count = 0
            rows.forEachIndexed { idx, row ->
                val correctIdx = when (row.correctAnswer.uppercase()) {
                    "A" -> 0
                    "B" -> 1
                    "C" -> 2
                    "D" -> 3
                    else -> row.options.indexOfFirst { it.equals(row.correctAnswer, ignoreCase = true) }.coerceAtLeast(0)
                }
                val textAnswer = if (row.type == QuestionType.TRUE_FALSE || row.type == QuestionType.FILL_GAP) {
                    row.correctAnswer
                } else null

                val res = addQuestionWithOptions(
                    examId = examId,
                    type = row.type,
                    prompt = row.prompt,
                    marks = row.marks,
                    imageUrl = null,
                    options = row.options,
                    correctOptionIndex = correctIdx,
                    correctTextAnswer = textAnswer,
                    markingGuide = row.markingGuide,
                    orderIndex = idx
                )
                if (res.isSuccess) count++
            }
            Result.success(count)
        }

    // --- Attempts & Answers ---
    suspend fun getStudentAttempts(studentId: String): List<ExamAttemptDto> = withContext(Dispatchers.IO) {
        api.getExamAttempts(studentFilter = "eq.$studentId").body().orEmpty()
    }

    suspend fun getExamAttemptsForExam(examId: String): List<ExamAttemptDto> = withContext(Dispatchers.IO) {
        api.getExamAttempts(examFilter = "eq.$examId").body().orEmpty()
    }

    suspend fun startOrResumeAttempt(exam: ExaminationDto, studentId: String): Result<ExamAttemptDto> =
        withContext(Dispatchers.IO) {
            val existing = api.getExamAttempts(
                examFilter = "eq.${exam.id}",
                studentFilter = "eq.$studentId"
            ).body()?.firstOrNull()

            if (existing != null) {
                if (existing.status != "in_progress") {
                    return@withContext Result.failure(IllegalStateException("You have already submitted this examination."))
                }
                if (!exam.allowResume) {
                    return@withContext Result.failure(IllegalStateException("Exam resumption is disabled by school rules."))
                }
                return@withContext Result.success(existing)
            }

            val seed = System.currentTimeMillis() % 999983L + 17L
            unwrapFirst(
                api.createExamAttempt(
                    ExamAttemptInsertDto(
                        examinationId = exam.id,
                        studentId = studentId,
                        status = "in_progress",
                        seed = seed,
                        remainingSeconds = exam.durationMinutes * 60
                    )
                ),
                "Unable to start examination"
            )
        }

    suspend fun getSavedAnswers(attemptId: String): List<StudentAnswerDto> = withContext(Dispatchers.IO) {
        api.getStudentAnswers(attemptFilter = "eq.$attemptId").body().orEmpty()
    }

    suspend fun saveSingleAnswer(dto: StudentAnswerUpsertDto): Result<StudentAnswerDto> = withContext(Dispatchers.IO) {
        unwrapFirst(api.upsertStudentAnswer(dto), "Failed to save answer")
    }

    suspend fun logTabSwitch(attemptId: String, newCount: Int, remainingSeconds: Int, actorId: String) =
        withContext(Dispatchers.IO) {
            api.updateExamAttempt(
                "eq.$attemptId",
                ExamAttemptUpdateDto(tabSwitchCount = newCount, remainingSeconds = remainingSeconds)
            )
            api.insertAuditLog(
                AuditLogInsertDto(
                    actorId = actorId,
                    action = "CBT_ANTI_CHEAT_TAB_SWITCH",
                    entityType = "exam_attempt",
                    entityId = attemptId,
                    metadata = mapOf("violation_count" to newCount.toString())
                )
            )
        }

    suspend fun submitAttempt(
        attempt: ExamAttemptDto,
        questions: List<QuestionDto>,
        selectedOptionIds: Map<String, String>,
        textResponses: Map<String, String>,
        markedForReview: Set<String>,
        remainingSeconds: Int
    ): Result<ExamAttemptDto> = withContext(Dispatchers.IO) {
        val grading = ExamEngine.gradeObjectiveAnswers(
            attemptId = attempt.id,
            questions = questions,
            selectedOptionIds = selectedOptionIds,
            textResponses = textResponses,
            markedForReview = markedForReview
        )
        grading.gradedAnswers.forEach { ans ->
            api.upsertStudentAnswer(ans)
        }
        val hasTheory = questions.any { QuestionType.fromDb(it.questionType) == QuestionType.THEORY }
        val status = if (hasTheory) "submitted" else "graded"
        val updateRes = api.updateExamAttempt(
            "eq.${attempt.id}",
            ExamAttemptUpdateDto(
                status = status,
                submittedAt = java.time.Instant.now().toString(),
                remainingSeconds = remainingSeconds,
                objectiveScore = grading.objectiveScore,
                theoryScore = 0.0,
                totalScore = grading.objectiveScore
            )
        )
        unwrapFirst(updateRes, "Failed to submit examination")
    }

    suspend fun gradeTheoryAnswer(
        attempt: ExamAttemptDto,
        questionId: String,
        marksAwarded: Double,
        feedback: String
    ): Result<ExamAttemptDto> = withContext(Dispatchers.IO) {
        api.upsertStudentAnswer(
            StudentAnswerUpsertDto(
                attemptId = attempt.id,
                questionId = questionId,
                marksAwarded = marksAwarded,
                teacherFeedback = feedback
            )
        )
        val answers = api.getStudentAnswers("eq.${attempt.id}").body().orEmpty()
        val totalAwarded = answers.sumOf { it.marksAwarded }
        val theorySum = (totalAwarded - attempt.objectiveScore).coerceAtLeast(0.0)
        unwrapFirst(
            api.updateExamAttempt(
                "eq.${attempt.id}",
                ExamAttemptUpdateDto(
                    status = "graded",
                    theoryScore = theorySum,
                    totalScore = totalAwarded
                )
            ),
            "Failed to update theory grade"
        )
    }

    // --- Results & Approvals ---
    suspend fun getResults(studentId: String? = null, classId: String? = null): List<ResultDto> =
        withContext(Dispatchers.IO) {
            api.getResults(
                studentFilter = studentId?.let { "eq.$it" },
                classFilter = classId?.let { "eq.$it" }
            ).body().orEmpty()
        }

    suspend fun saveResultWithPosition(
        studentId: String,
        subjectId: String,
        classId: String,
        sessionId: String,
        caScore: Double,
        examScore: Double
    ): Result<ResultDto> = withContext(Dispatchers.IO) {
        val total = (caScore + examScore).coerceIn(0.0, 100.0)
        val gradeInfo = ExamEngine.calculateGrade(total)
        val saved = unwrapFirst(
            api.upsertResult(
                ResultUpsertDto(
                    studentId = studentId,
                    subjectId = subjectId,
                    classId = classId,
                    sessionId = sessionId,
                    caScore = caScore,
                    examScore = examScore,
                    grade = gradeInfo.grade,
                    remark = gradeInfo.remark,
                    isApproved = false
                )
            ),
            "Failed to save result"
        )
        // Recompute class positions for this class & subject
        if (saved.isSuccess) {
            val classResults = api.getResults(classFilter = "eq.$classId").body().orEmpty()
                .filter { it.subjectId == subjectId && it.sessionId == sessionId }
            val ranks = ExamEngine.computePositions(classResults.associate { it.studentId to it.totalScore })
            classResults.forEach { r ->
                val pos = ranks[r.studentId]
                if (pos != null) {
                    api.upsertResult(
                        ResultUpsertDto(
                            studentId = r.studentId,
                            subjectId = r.subjectId,
                            classId = r.classId,
                            sessionId = r.sessionId,
                            caScore = r.caScore,
                            examScore = r.examScore,
                            grade = r.grade,
                            remark = r.remark,
                            classPosition = pos,
                            isApproved = r.isApproved
                        )
                    )
                }
            }
        }
        saved
    }

    suspend fun approveResult(resultId: String, approved: Boolean, actorId: String): Result<ResultDto> =
        withContext(Dispatchers.IO) {
            val res = unwrapFirst(api.approveResult("eq.$resultId", mapOf("is_approved" to approved)), "Failed to approve result")
            if (res.isSuccess) {
                api.insertAuditLog(AuditLogInsertDto(actorId, "RESULT_APPROVAL_UPDATED", "result", resultId))
            }
            res
        }

    // --- Attendance, Assignments, Learning Materials ---
    suspend fun getAttendance(studentId: String? = null, classId: String? = null): List<AttendanceDto> =
        withContext(Dispatchers.IO) {
            api.getAttendance(
                studentFilter = studentId?.let { "eq.$it" },
                classFilter = classId?.let { "eq.$it" }
            ).body().orEmpty()
        }

    suspend fun recordAttendance(dto: AttendanceUpsertDto): Result<AttendanceDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.upsertAttendance(dto), "Failed to save attendance") }

    suspend fun getAssignments(classId: String? = null): List<AssignmentDto> = withContext(Dispatchers.IO) {
        api.getAssignments(classFilter = classId?.let { "eq.$it" }).body().orEmpty()
    }

    suspend fun createAssignment(dto: AssignmentInsertDto): Result<AssignmentDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createAssignment(dto), "Failed to create assignment") }

    suspend fun getSubmissions(studentId: String? = null): List<AssignmentSubmissionDto> = withContext(Dispatchers.IO) {
        api.getSubmissions(studentFilter = studentId?.let { "eq.$it" }).body().orEmpty()
    }

    suspend fun submitAssignment(dto: AssignmentSubmissionInsertDto): Result<AssignmentSubmissionDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.submitAssignment(dto), "Failed to submit assignment") }

    suspend fun getMaterials(classId: String? = null): List<LearningMaterialDto> = withContext(Dispatchers.IO) {
        api.getLearningMaterials(classFilter = classId?.let { "eq.$it" }).body().orEmpty()
    }

    suspend fun createMaterial(dto: LearningMaterialInsertDto): Result<LearningMaterialDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createLearningMaterial(dto), "Failed to upload material") }

    // --- Parent Portal, Announcements, Timetable, Calendar, Notifications, Audit ---
    suspend fun getLinkedChildren(parentProfileId: String): List<StudentDto> = withContext(Dispatchers.IO) {
        val parent = api.getParents(profileIdFilter = "eq.$parentProfileId").body()?.firstOrNull()
            ?: return@withContext emptyList()
        api.getParentLinks(parentIdFilter = "eq.${parent.id}").body().orEmpty().mapNotNull { it.students }
    }

    suspend fun linkParentToStudent(parentId: String, studentAdmissionNo: String): Result<ParentStudentLinkDto> =
        withContext(Dispatchers.IO) {
            val student = api.getStudents(admFilter = "eq.${studentAdmissionNo.trim()}").body()?.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No student found with admission number $studentAdmissionNo"))
            unwrapFirst(api.createParentLink(ParentStudentLinkInsertDto(parentId, student.id)), "Failed to link child")
        }

    suspend fun getAnnouncements(): List<AnnouncementDto> = withContext(Dispatchers.IO) {
        api.getAnnouncements().body().orEmpty()
    }

    suspend fun createAnnouncement(dto: AnnouncementInsertDto): Result<AnnouncementDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createAnnouncement(dto), "Failed to publish announcement") }

    suspend fun getTimetable(classId: String? = null): List<TimetableEntryDto> = withContext(Dispatchers.IO) {
        api.getTimetable(classFilter = classId?.let { "eq.$it" }).body().orEmpty()
    }

    suspend fun createTimetableEntry(dto: TimetableInsertDto): Result<TimetableEntryDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createTimetableEntry(dto), "Failed to add timetable slot") }

    suspend fun getCalendarEvents(): List<CalendarEventDto> = withContext(Dispatchers.IO) {
        api.getCalendarEvents().body().orEmpty()
    }

    suspend fun createCalendarEvent(dto: CalendarEventInsertDto): Result<CalendarEventDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createCalendarEvent(dto), "Failed to add calendar event") }

    suspend fun getParentMessages(): List<ParentMessageDto> = withContext(Dispatchers.IO) {
        api.getParentMessages().body().orEmpty()
    }

    suspend fun sendParentMessage(dto: ParentMessageInsertDto): Result<ParentMessageDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createParentMessage(dto), "Failed to send message") }

    suspend fun getNotifications(userId: String): List<NotificationDto> = withContext(Dispatchers.IO) {
        api.getNotifications(userFilter = "eq.$userId").body().orEmpty()
    }

    suspend fun markNotificationRead(id: String) = withContext(Dispatchers.IO) {
        api.markNotificationRead("eq.$id", mapOf("is_read" to true))
    }

    suspend fun getAuditLogs(): List<AuditLogDto> = withContext(Dispatchers.IO) {
        api.getAuditLogs().body().orEmpty()
    }

    // --- Invoices & Paystack ---
    suspend fun getInvoices(studentId: String? = null): List<InvoiceDto> = withContext(Dispatchers.IO) {
        api.getInvoices(studentFilter = studentId?.let { "eq.$it" }).body().orEmpty()
    }

    suspend fun createInvoice(dto: InvoiceInsertDto): Result<InvoiceDto> =
        withContext(Dispatchers.IO) { unwrapFirst(api.createInvoice(dto), "Failed to create invoice") }

    suspend fun getPayments(studentId: String? = null): List<PaymentDto> = withContext(Dispatchers.IO) {
        api.getPayments(studentFilter = studentId?.let { "eq.$it" }).body().orEmpty()
    }

    suspend fun initializePaystackPayment(invoiceId: String): Result<PaystackInitResponse> =
        withContext(Dispatchers.IO) {
            try {
                val res = api.initializePaystack(PaystackInitRequest(invoiceId))
                if (res.isSuccessful && res.body()?.authorizationUrl != null) {
                    Result.success(res.body()!!)
                } else {
                    Result.failure(IllegalStateException(SupabaseErrorParser.extractMessage(res, "Paystack initialization failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun verifyPaystackPayment(reference: String): Result<PaystackVerifyResponse> =
        withContext(Dispatchers.IO) {
            try {
                val res = api.verifyPaystack(PaystackVerifyRequest(reference))
                val body = res.body()
                if (res.isSuccessful && body?.verified == true) {
                    Result.success(body)
                } else {
                    Result.failure(IllegalStateException(body?.message ?: SupabaseErrorParser.extractMessage(res, "Payment verification failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // --- Supabase Storage Uploads ---
    suspend fun uploadUriToStorage(
        context: Context,
        bucket: String,
        objectPath: String,
        uri: Uri,
        mimeType: String = "application/octet-stream"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Could not read selected file"))
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val res = api.uploadStorageObject(bucket = bucket, path = objectPath, body = body)
            if (res.isSuccessful) {
                Result.success(client.publicStorageUrl(bucket, objectPath))
            } else {
                Result.failure(IllegalStateException(SupabaseErrorParser.extractMessage(res, "Storage upload failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
