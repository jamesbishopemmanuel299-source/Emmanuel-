package com.example.data.remote

import com.example.data.model.*
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApi {

    // --- Supabase Auth ---
    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body body: AuthSignInRequest): Response<AuthSessionResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: AuthSignUpRequest): Response<AuthSignUpResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshSession(@Body body: AuthRefreshRequest): Response<AuthSessionResponse>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(@Body body: AuthRecoverRequest): Response<Unit>

    @POST("auth/v1/logout")
    suspend fun logout(): Response<Unit>

    // --- Profiles & Settings ---
    @GET("rest/v1/profiles")
    suspend fun getProfiles(
        @Query("id") idFilter: String? = null,
        @Query("role") roleFilter: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<ProfileDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/profiles")
    suspend fun insertProfile(@Body body: ProfileInsertDto): Response<List<ProfileDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Body body: ProfileUpdateDto
    ): Response<List<ProfileDto>>

    @GET("rest/v1/school_settings")
    suspend fun getSchoolSettings(@Query("limit") limit: Int = 1): Response<List<SchoolSettingsDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/school_settings")
    suspend fun updateSchoolSettings(
        @Query("id") idFilter: String,
        @Body body: SchoolSettingsUpdateDto
    ): Response<List<SchoolSettingsDto>>

    // --- Sessions, Classes, Subjects ---
    @GET("rest/v1/academic_sessions")
    suspend fun getAcademicSessions(@Query("order") order: String = "start_date.desc"): Response<List<AcademicSessionDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/academic_sessions")
    suspend fun createAcademicSession(@Body body: AcademicSessionInsertDto): Response<List<AcademicSessionDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/academic_sessions")
    suspend fun updateAcademicSession(
        @Query("id") idFilter: String,
        @Body body: Map<String, Boolean>
    ): Response<List<AcademicSessionDto>>

    @GET("rest/v1/classes")
    suspend fun getClasses(@Query("order") order: String = "name.asc"): Response<List<SchoolClassDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/classes")
    suspend fun createClass(@Body body: SchoolClassInsertDto): Response<List<SchoolClassDto>>

    @GET("rest/v1/subjects")
    suspend fun getSubjects(@Query("order") order: String = "name.asc"): Response<List<SubjectDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/subjects")
    suspend fun createSubject(@Body body: SubjectInsertDto): Response<List<SubjectDto>>

    // --- Students, Teachers, Parents ---
    @GET("rest/v1/students")
    suspend fun getStudents(
        @Query("select") select: String = "*,profiles(*),classes(*)",
        @Query("profile_id") profileIdFilter: String? = null,
        @Query("admission_number") admFilter: String? = null,
        @Query("class_id") classFilter: String? = null
    ): Response<List<StudentDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/students")
    suspend fun createStudent(@Body body: StudentInsertDto): Response<List<StudentDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/students")
    suspend fun updateStudent(
        @Query("id") idFilter: String,
        @Body body: Map<String, String>
    ): Response<List<StudentDto>>

    @GET("rest/v1/teachers")
    suspend fun getTeachers(
        @Query("select") select: String = "*,profiles(*)",
        @Query("profile_id") profileIdFilter: String? = null
    ): Response<List<TeacherDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/teachers")
    suspend fun createTeacher(@Body body: TeacherInsertDto): Response<List<TeacherDto>>

    @GET("rest/v1/parents")
    suspend fun getParents(
        @Query("profile_id") profileIdFilter: String? = null
    ): Response<List<ParentDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/parents")
    suspend fun createParent(@Body body: ParentInsertDto): Response<List<ParentDto>>

    @GET("rest/v1/parent_student_links")
    suspend fun getParentLinks(
        @Query("select") select: String = "*,students(*,profiles(*),classes(*))",
        @Query("parent_id") parentIdFilter: String? = null
    ): Response<List<ParentStudentLinkDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/parent_student_links")
    suspend fun createParentLink(@Body body: ParentStudentLinkInsertDto): Response<List<ParentStudentLinkDto>>

    // --- Examinations, Questions, Options, Attempts, Answers ---
    @GET("rest/v1/examinations")
    suspend fun getExaminations(
        @Query("select") select: String = "*,subjects(*),classes(*)",
        @Query("class_id") classFilter: String? = null,
        @Query("created_by") createdByFilter: String? = null,
        @Query("order") order: String = "scheduled_start.desc"
    ): Response<List<ExaminationDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/examinations")
    suspend fun createExamination(@Body body: ExaminationInsertDto): Response<List<ExaminationDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/examinations")
    suspend fun updateExamination(
        @Query("id") idFilter: String,
        @Body body: Map<String, Boolean>
    ): Response<List<ExaminationDto>>

    @GET("rest/v1/questions")
    suspend fun getQuestions(
        @Query("select") select: String = "*,answer_options(*)",
        @Query("examination_id") examFilter: String,
        @Query("order") order: String = "order_index.asc"
    ): Response<List<QuestionDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/questions")
    suspend fun createQuestion(@Body body: QuestionInsertDto): Response<List<QuestionDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/answer_options")
    suspend fun createAnswerOptions(@Body body: List<AnswerOptionInsertDto>): Response<List<AnswerOptionDto>>

    @GET("rest/v1/exam_attempts")
    suspend fun getExamAttempts(
        @Query("select") select: String = "*,students(*,profiles(*),classes(*))",
        @Query("examination_id") examFilter: String? = null,
        @Query("student_id") studentFilter: String? = null
    ): Response<List<ExamAttemptDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/exam_attempts")
    suspend fun createExamAttempt(@Body body: ExamAttemptInsertDto): Response<List<ExamAttemptDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/exam_attempts")
    suspend fun updateExamAttempt(
        @Query("id") idFilter: String,
        @Body body: ExamAttemptUpdateDto
    ): Response<List<ExamAttemptDto>>

    @GET("rest/v1/student_answers")
    suspend fun getStudentAnswers(
        @Query("attempt_id") attemptFilter: String
    ): Response<List<StudentAnswerDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/student_answers?on_conflict=attempt_id,question_id")
    suspend fun upsertStudentAnswer(@Body body: StudentAnswerUpsertDto): Response<List<StudentAnswerDto>>

    // --- Results, Attendance, Assignments, Materials ---
    @GET("rest/v1/results")
    suspend fun getResults(
        @Query("select") select: String = "*,subjects(*),students(*,profiles(*),classes(*))",
        @Query("student_id") studentFilter: String? = null,
        @Query("class_id") classFilter: String? = null,
        @Query("order") order: String = "total_score.desc"
    ): Response<List<ResultDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/results?on_conflict=student_id,subject_id,session_id")
    suspend fun upsertResult(@Body body: ResultUpsertDto): Response<List<ResultDto>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/results")
    suspend fun approveResult(
        @Query("id") idFilter: String,
        @Body body: Map<String, Boolean>
    ): Response<List<ResultDto>>

    @GET("rest/v1/attendance")
    suspend fun getAttendance(
        @Query("student_id") studentFilter: String? = null,
        @Query("class_id") classFilter: String? = null,
        @Query("date") dateFilter: String? = null,
        @Query("order") order: String = "date.desc"
    ): Response<List<AttendanceDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/attendance?on_conflict=student_id,date")
    suspend fun upsertAttendance(@Body body: AttendanceUpsertDto): Response<List<AttendanceDto>>

    @GET("rest/v1/assignments")
    suspend fun getAssignments(
        @Query("select") select: String = "*,subjects(*)",
        @Query("class_id") classFilter: String? = null,
        @Query("order") order: String = "due_date.asc"
    ): Response<List<AssignmentDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/assignments")
    suspend fun createAssignment(@Body body: AssignmentInsertDto): Response<List<AssignmentDto>>

    @GET("rest/v1/assignment_submissions")
    suspend fun getSubmissions(
        @Query("student_id") studentFilter: String? = null,
        @Query("assignment_id") assignmentFilter: String? = null
    ): Response<List<AssignmentSubmissionDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/assignment_submissions?on_conflict=assignment_id,student_id")
    suspend fun submitAssignment(@Body body: AssignmentSubmissionInsertDto): Response<List<AssignmentSubmissionDto>>

    @GET("rest/v1/learning_materials")
    suspend fun getLearningMaterials(
        @Query("select") select: String = "*,subjects(*)",
        @Query("class_id") classFilter: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<LearningMaterialDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/learning_materials")
    suspend fun createLearningMaterial(@Body body: LearningMaterialInsertDto): Response<List<LearningMaterialDto>>

    // --- Announcements, Timetable, Calendar, Messages, Notifications, Audit ---
    @GET("rest/v1/announcements")
    suspend fun getAnnouncements(@Query("order") order: String = "created_at.desc"): Response<List<AnnouncementDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/announcements")
    suspend fun createAnnouncement(@Body body: AnnouncementInsertDto): Response<List<AnnouncementDto>>

    @GET("rest/v1/timetable_entries")
    suspend fun getTimetable(
        @Query("select") select: String = "*,subjects(*),classes(*)",
        @Query("class_id") classFilter: String? = null
    ): Response<List<TimetableEntryDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/timetable_entries")
    suspend fun createTimetableEntry(@Body body: TimetableInsertDto): Response<List<TimetableEntryDto>>

    @GET("rest/v1/calendar_events")
    suspend fun getCalendarEvents(@Query("order") order: String = "event_date.asc"): Response<List<CalendarEventDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/calendar_events")
    suspend fun createCalendarEvent(@Body body: CalendarEventInsertDto): Response<List<CalendarEventDto>>

    @GET("rest/v1/parent_messages")
    suspend fun getParentMessages(@Query("order") order: String = "created_at.desc"): Response<List<ParentMessageDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/parent_messages")
    suspend fun createParentMessage(@Body body: ParentMessageInsertDto): Response<List<ParentMessageDto>>

    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Query("user_id") userFilter: String,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<NotificationDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/notifications")
    suspend fun createNotification(@Body body: NotificationInsertDto): Response<List<NotificationDto>>

    @PATCH("rest/v1/notifications")
    suspend fun markNotificationRead(
        @Query("id") idFilter: String,
        @Body body: Map<String, Boolean>
    ): Response<Unit>

    @GET("rest/v1/audit_logs")
    suspend fun getAuditLogs(
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<AuditLogDto>>

    @POST("rest/v1/audit_logs")
    suspend fun insertAuditLog(@Body body: AuditLogInsertDto): Response<Unit>

    // --- Invoices & Paystack ---
    @GET("rest/v1/invoices")
    suspend fun getInvoices(
        @Query("select") select: String = "*,students(*,profiles(*),classes(*))",
        @Query("student_id") studentFilter: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<InvoiceDto>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/invoices")
    suspend fun createInvoice(@Body body: InvoiceInsertDto): Response<List<InvoiceDto>>

    @GET("rest/v1/payments")
    suspend fun getPayments(
        @Query("student_id") studentFilter: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<PaymentDto>>

    @POST("functions/v1/initialize-paystack-payment")
    suspend fun initializePaystack(@Body body: PaystackInitRequest): Response<PaystackInitResponse>

    @POST("functions/v1/verify-paystack-payment")
    suspend fun verifyPaystack(@Body body: PaystackVerifyRequest): Response<PaystackVerifyResponse>

    // --- Supabase Storage ---
    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadStorageObject(
        @Path("bucket") bucket: String,
        @Path("path", encoded = true) path: String,
        @Header("x-upsert") upsert: String = "true",
        @Body body: RequestBody
    ): Response<Unit>
}
