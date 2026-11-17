package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class UserRole(val dbValue: String, val displayName: String) {
    ADMIN("admin", "Administrator"),
    TEACHER("teacher", "Teacher"),
    STUDENT("student", "Student"),
    PARENT("parent", "Parent");

    companion object {
        fun fromDb(value: String?): UserRole =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: STUDENT
    }
}

enum class QuestionType(val dbValue: String, val label: String) {
    MCQ("mcq", "Multiple Choice"),
    TRUE_FALSE("true_false", "True / False"),
    FILL_GAP("fill_gap", "Fill in the Gap"),
    THEORY("theory", "Theory / Essay");

    companion object {
        fun fromDb(value: String?): QuestionType =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: MCQ
    }
}

// --- Supabase Auth Models ---

@JsonClass(generateAdapter = true)
data class AuthSignInRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthSignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class AuthRefreshRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class AuthRecoverRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class AuthUserDto(
    val id: String,
    val email: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthSessionResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    val user: AuthUserDto? = null
)

@JsonClass(generateAdapter = true)
data class AuthSignUpResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    val user: AuthUserDto? = null,
    val id: String? = null,
    val email: String? = null
)

// --- Core School Domain Models ---

@JsonClass(generateAdapter = true)
data class ProfileDto(
    val id: String,
    val email: String,
    @Json(name = "full_name") val fullName: String,
    val phone: String? = null,
    val role: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileInsertDto(
    val id: String,
    val email: String,
    @Json(name = "full_name") val fullName: String,
    val phone: String? = null,
    val role: String,
    @Json(name = "is_active") val isActive: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateDto(
    @Json(name = "full_name") val fullName: String? = null,
    val phone: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_active") val isActive: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class SchoolSettingsDto(
    val id: String,
    @Json(name = "school_name") val schoolName: String = "Green Light Academy",
    val motto: String = "Excellence Through Knowledge & Integrity",
    @Json(name = "logo_url") val logoUrl: String? = null,
    val address: String? = null,
    @Json(name = "contact_email") val contactEmail: String? = null,
    @Json(name = "contact_phone") val contactPhone: String? = null,
    @Json(name = "default_exam_duration_minutes") val defaultExamDurationMinutes: Int = 60,
    @Json(name = "default_pass_mark") val defaultPassMark: Double = 50.0,
    @Json(name = "max_tab_switches") val maxTabSwitches: Int = 3,
    @Json(name = "allow_instant_results") val allowInstantResults: Boolean = true,
    @Json(name = "allow_exam_resume") val allowExamResume: Boolean = true,
    @Json(name = "paystack_subaccount_code") val paystackSubaccountCode: String? = null
)

@JsonClass(generateAdapter = true)
data class SchoolSettingsUpdateDto(
    @Json(name = "school_name") val schoolName: String,
    val motto: String,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "default_exam_duration_minutes") val defaultExamDurationMinutes: Int,
    @Json(name = "default_pass_mark") val defaultPassMark: Double,
    @Json(name = "max_tab_switches") val maxTabSwitches: Int,
    @Json(name = "allow_instant_results") val allowInstantResults: Boolean,
    @Json(name = "allow_exam_resume") val allowExamResume: Boolean,
    @Json(name = "paystack_subaccount_code") val paystackSubaccountCode: String? = null
)

@JsonClass(generateAdapter = true)
data class AcademicSessionDto(
    val id: String = "",
    val name: String,
    val term: String,
    @Json(name = "start_date") val startDate: String,
    @Json(name = "end_date") val endDate: String,
    @Json(name = "is_active") val isActive: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AcademicSessionInsertDto(
    val name: String,
    val term: String,
    @Json(name = "start_date") val startDate: String,
    @Json(name = "end_date") val endDate: String,
    @Json(name = "is_active") val isActive: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SchoolClassDto(
    val id: String = "",
    val name: String,
    val level: String,
    val department: String = "General",
    val capacity: Int = 40
)

@JsonClass(generateAdapter = true)
data class SchoolClassInsertDto(
    val name: String,
    val level: String,
    val department: String = "General",
    val capacity: Int = 40
)

@JsonClass(generateAdapter = true)
data class TeacherDto(
    val id: String = "",
    @Json(name = "profile_id") val profileId: String,
    @Json(name = "staff_code") val staffCode: String,
    val department: String = "General",
    val qualification: String? = null,
    val profiles: ProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class TeacherInsertDto(
    @Json(name = "profile_id") val profileId: String,
    @Json(name = "staff_code") val staffCode: String,
    val department: String = "General",
    val qualification: String? = null
)

@JsonClass(generateAdapter = true)
data class SubjectDto(
    val id: String = "",
    val code: String,
    val name: String,
    val department: String = "General",
    @Json(name = "class_id") val classId: String? = null,
    @Json(name = "teacher_id") val teacherId: String? = null
)

@JsonClass(generateAdapter = true)
data class SubjectInsertDto(
    val code: String,
    val name: String,
    val department: String = "General",
    @Json(name = "class_id") val classId: String? = null,
    @Json(name = "teacher_id") val teacherId: String? = null
)

@JsonClass(generateAdapter = true)
data class StudentDto(
    val id: String = "",
    @Json(name = "profile_id") val profileId: String,
    @Json(name = "admission_number") val admissionNumber: String,
    @Json(name = "class_id") val classId: String? = null,
    val department: String = "General",
    @Json(name = "passport_url") val passportUrl: String? = null,
    val gender: String? = null,
    @Json(name = "date_of_birth") val dateOfBirth: String? = null,
    @Json(name = "admission_status") val admissionStatus: String = "approved",
    @Json(name = "guardian_contact") val guardianContact: String? = null,
    val profiles: ProfileDto? = null,
    val classes: SchoolClassDto? = null
)

@JsonClass(generateAdapter = true)
data class StudentInsertDto(
    @Json(name = "profile_id") val profileId: String,
    @Json(name = "admission_number") val admissionNumber: String,
    @Json(name = "class_id") val classId: String? = null,
    val department: String = "General",
    @Json(name = "admission_status") val admissionStatus: String = "approved",
    @Json(name = "guardian_contact") val guardianContact: String? = null
)

@JsonClass(generateAdapter = true)
data class ParentDto(
    val id: String = "",
    @Json(name = "profile_id") val profileId: String,
    val occupation: String? = null,
    val address: String? = null
)

@JsonClass(generateAdapter = true)
data class ParentInsertDto(
    @Json(name = "profile_id") val profileId: String,
    val occupation: String? = null,
    val address: String? = null
)

@JsonClass(generateAdapter = true)
data class ParentStudentLinkDto(
    val id: String = "",
    @Json(name = "parent_id") val parentId: String,
    @Json(name = "student_id") val studentId: String,
    val relationship: String = "Guardian",
    val students: StudentDto? = null
)

@JsonClass(generateAdapter = true)
data class ParentStudentLinkInsertDto(
    @Json(name = "parent_id") val parentId: String,
    @Json(name = "student_id") val studentId: String,
    val relationship: String = "Guardian"
)

@JsonClass(generateAdapter = true)
data class EnrollmentInsertDto(
    @Json(name = "student_id") val studentId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "session_id") val sessionId: String
)

// --- Examinations & CBT Models ---

@JsonClass(generateAdapter = true)
data class ExaminationDto(
    val id: String = "",
    val title: String,
    val instructions: String = "",
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "session_id") val sessionId: String? = null,
    @Json(name = "created_by") val createdBy: String,
    @Json(name = "duration_minutes") val durationMinutes: Int,
    @Json(name = "pass_mark") val passMark: Double = 50.0,
    @Json(name = "total_marks") val totalMarks: Double = 100.0,
    @Json(name = "scheduled_start") val scheduledStart: String,
    @Json(name = "scheduled_end") val scheduledEnd: String,
    @Json(name = "randomize_questions") val randomizeQuestions: Boolean = true,
    @Json(name = "randomize_options") val randomizeOptions: Boolean = true,
    @Json(name = "instant_results_enabled") val instantResultsEnabled: Boolean = true,
    @Json(name = "allow_resume") val allowResume: Boolean = true,
    @Json(name = "max_tab_switches") val maxTabSwitches: Int = 3,
    @Json(name = "is_published") val isPublished: Boolean = false,
    @Json(name = "results_published") val resultsPublished: Boolean = false,
    val subjects: SubjectDto? = null,
    val classes: SchoolClassDto? = null
)

@JsonClass(generateAdapter = true)
data class ExaminationInsertDto(
    val title: String,
    val instructions: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "session_id") val sessionId: String? = null,
    @Json(name = "created_by") val createdBy: String,
    @Json(name = "duration_minutes") val durationMinutes: Int,
    @Json(name = "pass_mark") val passMark: Double,
    @Json(name = "total_marks") val totalMarks: Double = 100.0,
    @Json(name = "scheduled_start") val scheduledStart: String,
    @Json(name = "scheduled_end") val scheduledEnd: String,
    @Json(name = "randomize_questions") val randomizeQuestions: Boolean = true,
    @Json(name = "randomize_options") val randomizeOptions: Boolean = true,
    @Json(name = "instant_results_enabled") val instantResultsEnabled: Boolean = true,
    @Json(name = "allow_resume") val allowResume: Boolean = true,
    @Json(name = "max_tab_switches") val maxTabSwitches: Int = 3,
    @Json(name = "is_published") val isPublished: Boolean = true
)

@JsonClass(generateAdapter = true)
data class AnswerOptionDto(
    val id: String = "",
    @Json(name = "question_id") val questionId: String,
    @Json(name = "option_text") val optionText: String,
    @Json(name = "is_correct") val isCorrect: Boolean = false,
    @Json(name = "order_index") val orderIndex: Int = 0
)

@JsonClass(generateAdapter = true)
data class AnswerOptionInsertDto(
    @Json(name = "question_id") val questionId: String,
    @Json(name = "option_text") val optionText: String,
    @Json(name = "is_correct") val isCorrect: Boolean,
    @Json(name = "order_index") val orderIndex: Int
)

@JsonClass(generateAdapter = true)
data class QuestionDto(
    val id: String = "",
    @Json(name = "examination_id") val examinationId: String,
    @Json(name = "question_type") val questionType: String,
    val prompt: String,
    @Json(name = "image_url") val imageUrl: String? = null,
    val marks: Double = 1.0,
    @Json(name = "correct_text_answer") val correctTextAnswer: String? = null,
    @Json(name = "marking_guide") val markingGuide: String? = null,
    @Json(name = "order_index") val orderIndex: Int = 0,
    @Json(name = "answer_options") val answerOptions: List<AnswerOptionDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class QuestionInsertDto(
    @Json(name = "examination_id") val examinationId: String,
    @Json(name = "question_type") val questionType: String,
    val prompt: String,
    @Json(name = "image_url") val imageUrl: String? = null,
    val marks: Double = 1.0,
    @Json(name = "correct_text_answer") val correctTextAnswer: String? = null,
    @Json(name = "marking_guide") val markingGuide: String? = null,
    @Json(name = "order_index") val orderIndex: Int = 0
)

@JsonClass(generateAdapter = true)
data class ExamAttemptDto(
    val id: String = "",
    @Json(name = "examination_id") val examinationId: String,
    @Json(name = "student_id") val studentId: String,
    val status: String = "in_progress",
    val seed: Long = 1L,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "submitted_at") val submittedAt: String? = null,
    @Json(name = "remaining_seconds") val remainingSeconds: Int = 0,
    @Json(name = "tab_switch_count") val tabSwitchCount: Int = 0,
    @Json(name = "objective_score") val objectiveScore: Double = 0.0,
    @Json(name = "theory_score") val theoryScore: Double = 0.0,
    @Json(name = "total_score") val totalScore: Double = 0.0,
    val students: StudentDto? = null
)

@JsonClass(generateAdapter = true)
data class ExamAttemptInsertDto(
    @Json(name = "examination_id") val examinationId: String,
    @Json(name = "student_id") val studentId: String,
    val status: String = "in_progress",
    val seed: Long,
    @Json(name = "remaining_seconds") val remainingSeconds: Int
)

@JsonClass(generateAdapter = true)
data class ExamAttemptUpdateDto(
    val status: String? = null,
    @Json(name = "submitted_at") val submittedAt: String? = null,
    @Json(name = "remaining_seconds") val remainingSeconds: Int? = null,
    @Json(name = "tab_switch_count") val tabSwitchCount: Int? = null,
    @Json(name = "objective_score") val objectiveScore: Double? = null,
    @Json(name = "theory_score") val theoryScore: Double? = null,
    @Json(name = "total_score") val totalScore: Double? = null
)

@JsonClass(generateAdapter = true)
data class StudentAnswerDto(
    val id: String = "",
    @Json(name = "attempt_id") val attemptId: String,
    @Json(name = "question_id") val questionId: String,
    @Json(name = "selected_option_id") val selectedOptionId: String? = null,
    @Json(name = "text_response") val textResponse: String? = null,
    @Json(name = "is_marked_for_review") val isMarkedForReview: Boolean = false,
    @Json(name = "is_correct") val isCorrect: Boolean? = null,
    @Json(name = "marks_awarded") val marksAwarded: Double = 0.0,
    @Json(name = "teacher_feedback") val teacherFeedback: String? = null
)

@JsonClass(generateAdapter = true)
data class StudentAnswerUpsertDto(
    @Json(name = "attempt_id") val attemptId: String,
    @Json(name = "question_id") val questionId: String,
    @Json(name = "selected_option_id") val selectedOptionId: String? = null,
    @Json(name = "text_response") val textResponse: String? = null,
    @Json(name = "is_marked_for_review") val isMarkedForReview: Boolean = false,
    @Json(name = "is_correct") val isCorrect: Boolean? = null,
    @Json(name = "marks_awarded") val marksAwarded: Double = 0.0,
    @Json(name = "teacher_feedback") val teacherFeedback: String? = null
)

// --- Results, Attendance, Assignments, Materials ---

@JsonClass(generateAdapter = true)
data class ResultDto(
    val id: String = "",
    @Json(name = "student_id") val studentId: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "ca_score") val caScore: Double = 0.0,
    @Json(name = "exam_score") val examScore: Double = 0.0,
    @Json(name = "total_score") val totalScore: Double = 0.0,
    val grade: String,
    val remark: String,
    @Json(name = "class_position") val classPosition: Int? = null,
    @Json(name = "is_approved") val isApproved: Boolean = false,
    @Json(name = "published_at") val publishedAt: String? = null,
    val subjects: SubjectDto? = null,
    val students: StudentDto? = null
)

@JsonClass(generateAdapter = true)
data class ResultUpsertDto(
    @Json(name = "student_id") val studentId: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "ca_score") val caScore: Double,
    @Json(name = "exam_score") val examScore: Double,
    val grade: String,
    val remark: String,
    @Json(name = "class_position") val classPosition: Int? = null,
    @Json(name = "is_approved") val isApproved: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AttendanceDto(
    val id: String = "",
    @Json(name = "student_id") val studentId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "session_id") val sessionId: String? = null,
    val date: String,
    val status: String,
    @Json(name = "recorded_by") val recordedBy: String,
    val note: String? = null
)

@JsonClass(generateAdapter = true)
data class AttendanceUpsertDto(
    @Json(name = "student_id") val studentId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "session_id") val sessionId: String? = null,
    val date: String,
    val status: String,
    @Json(name = "recorded_by") val recordedBy: String,
    val note: String? = null
)

@JsonClass(generateAdapter = true)
data class AssignmentDto(
    val id: String = "",
    val title: String,
    val description: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "teacher_id") val teacherId: String,
    @Json(name = "attachment_url") val attachmentUrl: String? = null,
    @Json(name = "due_date") val dueDate: String,
    @Json(name = "max_score") val maxScore: Double = 20.0,
    val subjects: SubjectDto? = null
)

@JsonClass(generateAdapter = true)
data class AssignmentInsertDto(
    val title: String,
    val description: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "teacher_id") val teacherId: String,
    @Json(name = "attachment_url") val attachmentUrl: String? = null,
    @Json(name = "due_date") val dueDate: String,
    @Json(name = "max_score") val maxScore: Double = 20.0
)

@JsonClass(generateAdapter = true)
data class AssignmentSubmissionDto(
    val id: String = "",
    @Json(name = "assignment_id") val assignmentId: String,
    @Json(name = "student_id") val studentId: String,
    @Json(name = "submission_text") val submissionText: String? = null,
    @Json(name = "attachment_url") val attachmentUrl: String? = null,
    val score: Double? = null,
    val feedback: String? = null,
    @Json(name = "submitted_at") val submittedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AssignmentSubmissionInsertDto(
    @Json(name = "assignment_id") val assignmentId: String,
    @Json(name = "student_id") val studentId: String,
    @Json(name = "submission_text") val submissionText: String,
    @Json(name = "attachment_url") val attachmentUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class LearningMaterialDto(
    val id: String = "",
    val title: String,
    val description: String? = null,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "uploaded_by") val uploadedBy: String,
    @Json(name = "file_url") val fileUrl: String,
    @Json(name = "file_type") val fileType: String = "PDF",
    @Json(name = "created_at") val createdAt: String? = null,
    val subjects: SubjectDto? = null
)

@JsonClass(generateAdapter = true)
data class LearningMaterialInsertDto(
    val title: String,
    val description: String? = null,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "class_id") val classId: String,
    @Json(name = "uploaded_by") val uploadedBy: String,
    @Json(name = "file_url") val fileUrl: String,
    @Json(name = "file_type") val fileType: String = "PDF"
)

// --- Announcements, Timetable, Calendar, Messages, Notifications, Audit ---

@JsonClass(generateAdapter = true)
data class AnnouncementDto(
    val id: String = "",
    val title: String,
    val body: String,
    @Json(name = "target_role") val targetRole: String = "all",
    val priority: String = "normal",
    @Json(name = "author_id") val authorId: String,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AnnouncementInsertDto(
    val title: String,
    val body: String,
    @Json(name = "target_role") val targetRole: String = "all",
    val priority: String = "normal",
    @Json(name = "author_id") val authorId: String
)

@JsonClass(generateAdapter = true)
data class TimetableEntryDto(
    val id: String = "",
    @Json(name = "class_id") val classId: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "day_of_week") val dayOfWeek: String,
    @Json(name = "start_time") val startTime: String,
    @Json(name = "end_time") val endTime: String,
    val room: String? = null,
    val subjects: SubjectDto? = null,
    val classes: SchoolClassDto? = null
)

@JsonClass(generateAdapter = true)
data class TimetableInsertDto(
    @Json(name = "class_id") val classId: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "day_of_week") val dayOfWeek: String,
    @Json(name = "start_time") val startTime: String,
    @Json(name = "end_time") val endTime: String,
    val room: String? = null
)

@JsonClass(generateAdapter = true)
data class CalendarEventDto(
    val id: String = "",
    val title: String,
    val description: String? = null,
    @Json(name = "event_date") val eventDate: String,
    val category: String = "Academic"
)

@JsonClass(generateAdapter = true)
data class CalendarEventInsertDto(
    val title: String,
    val description: String? = null,
    @Json(name = "event_date") val eventDate: String,
    val category: String = "Academic"
)

@JsonClass(generateAdapter = true)
data class ParentMessageDto(
    val id: String = "",
    @Json(name = "parent_profile_id") val parentProfileId: String,
    @Json(name = "teacher_profile_id") val teacherProfileId: String,
    @Json(name = "student_id") val studentId: String? = null,
    val subject: String,
    val body: String,
    @Json(name = "reply_body") val replyBody: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ParentMessageInsertDto(
    @Json(name = "parent_profile_id") val parentProfileId: String,
    @Json(name = "teacher_profile_id") val teacherProfileId: String,
    @Json(name = "student_id") val studentId: String? = null,
    val subject: String,
    val body: String
)

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: String = "",
    @Json(name = "user_id") val userId: String,
    val title: String,
    val message: String,
    val category: String = "general",
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class NotificationInsertDto(
    @Json(name = "user_id") val userId: String,
    val title: String,
    val message: String,
    val category: String = "general"
)

@JsonClass(generateAdapter = true)
data class AuditLogDto(
    val id: String = "",
    @Json(name = "actor_id") val actorId: String? = null,
    val action: String,
    @Json(name = "entity_type") val entityType: String,
    @Json(name = "entity_id") val entityId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AuditLogInsertDto(
    @Json(name = "actor_id") val actorId: String?,
    val action: String,
    @Json(name = "entity_type") val entityType: String,
    @Json(name = "entity_id") val entityId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

// --- Invoices & Paystack Payments ---

@JsonClass(generateAdapter = true)
data class InvoiceDto(
    val id: String = "",
    @Json(name = "student_id") val studentId: String,
    @Json(name = "session_id") val sessionId: String? = null,
    val title: String,
    @Json(name = "fee_type") val feeType: String,
    @Json(name = "amount_kobo") val amountKobo: Long,
    @Json(name = "amount_paid_kobo") val amountPaidKobo: Long = 0,
    val status: String = "unpaid",
    @Json(name = "due_date") val dueDate: String,
    val students: StudentDto? = null
)

@JsonClass(generateAdapter = true)
data class InvoiceInsertDto(
    @Json(name = "student_id") val studentId: String,
    @Json(name = "session_id") val sessionId: String? = null,
    val title: String,
    @Json(name = "fee_type") val feeType: String,
    @Json(name = "amount_kobo") val amountKobo: Long,
    @Json(name = "due_date") val dueDate: String
)

@JsonClass(generateAdapter = true)
data class PaymentDto(
    val id: String = "",
    @Json(name = "invoice_id") val invoiceId: String,
    @Json(name = "student_id") val studentId: String,
    val reference: String,
    @Json(name = "amount_kobo") val amountKobo: Long,
    val currency: String = "NGN",
    val channel: String? = null,
    val status: String = "pending",
    @Json(name = "gateway_response") val gatewayResponse: String? = null,
    @Json(name = "paid_at") val paidAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class PaystackInitRequest(
    @Json(name = "invoice_id") val invoiceId: String
)

@JsonClass(generateAdapter = true)
data class PaystackInitResponse(
    @Json(name = "authorization_url") val authorizationUrl: String? = null,
    @Json(name = "access_code") val accessCode: String? = null,
    val reference: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class PaystackVerifyRequest(
    val reference: String
)

@JsonClass(generateAdapter = true)
data class PaystackVerifyResponse(
    val verified: Boolean = false,
    val message: String? = null,
    val reference: String? = null,
    val error: String? = null
)
