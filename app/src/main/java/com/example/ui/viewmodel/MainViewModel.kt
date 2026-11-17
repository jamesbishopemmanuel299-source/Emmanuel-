package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.local.SessionPreferences
import com.example.data.model.*
import com.example.data.remote.SupabaseClient
import com.example.data.repository.AcademyRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthenticatedAccount
import com.example.ui.theme.ThemeMode
import com.example.util.CsvExcelHelper
import com.example.util.ExamEngine
import com.example.util.PdfGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ActiveExamSessionState(
    val exam: ExaminationDto,
    val attempt: ExamAttemptDto,
    val questions: List<QuestionDto>,
    val currentIndex: Int = 0,
    val selectedOptionIds: Map<String, String> = emptyMap(),
    val textResponses: Map<String, String> = emptyMap(),
    val markedForReview: Set<String> = emptySet(),
    val remainingSeconds: Int = 0,
    val tabSwitchWarnings: Int = 0,
    val showTabWarningModal: Boolean = false,
    val submittedAttempt: ExamAttemptDto? = null
)

data class MainUiState(
    val isInitializing: Boolean = true,
    val isLoading: Boolean = false,
    val account: AuthenticatedAccount? = null,
    val schoolSettings: SchoolSettingsDto? = null,
    val activeSession: AcademicSessionDto? = null,
    val sessions: List<AcademicSessionDto> = emptyList(),
    val classes: List<SchoolClassDto> = emptyList(),
    val subjects: List<SubjectDto> = emptyList(),
    val students: List<StudentDto> = emptyList(),
    val teachers: List<TeacherDto> = emptyList(),
    val profiles: List<ProfileDto> = emptyList(),
    val linkedChildren: List<StudentDto> = emptyList(),
    val selectedChildIndex: Int = 0,
    val examinations: List<ExaminationDto> = emptyList(),
    val studentAttempts: List<ExamAttemptDto> = emptyList(),
    val results: List<ResultDto> = emptyList(),
    val attendance: List<AttendanceDto> = emptyList(),
    val assignments: List<AssignmentDto> = emptyList(),
    val submissions: List<AssignmentSubmissionDto> = emptyList(),
    val materials: List<LearningMaterialDto> = emptyList(),
    val announcements: List<AnnouncementDto> = emptyList(),
    val timetable: List<TimetableEntryDto> = emptyList(),
    val calendarEvents: List<CalendarEventDto> = emptyList(),
    val parentMessages: List<ParentMessageDto> = emptyList(),
    val invoices: List<InvoiceDto> = emptyList(),
    val payments: List<PaymentDto> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
    val auditLogs: List<AuditLogDto> = emptyList(),
    val activeExam: ActiveExamSessionState? = null,
    val pendingPaystackReference: String? = null,
    val pendingPaystackUrl: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SessionPreferences(application)
    @Volatile
    private var currentAccessToken: String? = null
    private val supabaseClient = SupabaseClient { currentAccessToken }
    private val authRepo = AuthRepository(prefs, supabaseClient) { token -> currentAccessToken = token }
    private val academyRepo = AcademyRepository(supabaseClient)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private var timerJob: Job? = null

    init {
        restoreSession()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun clearPendingPaystackUrl() {
        _uiState.update { it.copy(pendingPaystackUrl = null) }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isInitializing = true) }
            if (AppConfig.isSupabaseConfigured) {
                loadPublicMetadata()
            }
            val result = authRepo.restoreSession()
            result.onSuccess { account ->
                _uiState.update { it.copy(isInitializing = false, account = account) }
                if (account != null) refreshPortalData()
            }.onFailure { err ->
                _uiState.update { it.copy(isInitializing = false, errorMessage = err.message) }
            }
        }
    }

    private suspend fun loadPublicMetadata() {
        runCatching {
            val classes = academyRepo.getClasses()
            val settings = academyRepo.getSchoolSettings()
            val sessions = academyRepo.getSessions()
            _uiState.update {
                it.copy(
                    classes = classes,
                    schoolSettings = settings,
                    sessions = sessions,
                    activeSession = sessions.firstOrNull { s -> s.isActive } ?: sessions.firstOrNull()
                )
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepo.signIn(email, password)
                .onSuccess { account ->
                    _uiState.update { it.copy(isLoading = false, account = account, statusMessage = "Welcome, ${account.profile.fullName}") }
                    refreshPortalData()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Sign in failed") }
                }
        }
    }

    fun register(
        role: UserRole,
        fullName: String,
        email: String,
        password: String,
        phone: String,
        admissionNumber: String,
        department: String,
        classId: String?,
        staffCode: String,
        childAdmissionNumber: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepo.register(
                role = role,
                fullName = fullName,
                email = email,
                password = password,
                phone = phone,
                admissionNumber = admissionNumber,
                department = department,
                classId = classId,
                staffCode = staffCode,
                childAdmissionNumber = childAdmissionNumber
            ).onSuccess { account ->
                if (account == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Account registered. Check your email inbox to confirm before signing in."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            account = account,
                            statusMessage = "Registration complete! Welcome to Green Light Academy."
                        )
                    }
                    refreshPortalData()
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Registration failed") }
            }
        }
    }

    fun recoverPassword(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepo.recoverPassword(email)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Password reset link sent to $email") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Failed to send reset link") }
                }
        }
    }

    fun signOut() {
        timerJob?.cancel()
        viewModelScope.launch {
            authRepo.signOut()
            _uiState.update {
                MainUiState(
                    isInitializing = false,
                    classes = it.classes,
                    schoolSettings = it.schoolSettings
                )
            }
        }
    }

    fun selectParentChild(index: Int) {
        val children = _uiState.value.linkedChildren
        if (index !in children.indices) return
        _uiState.update { it.copy(selectedChildIndex = index) }
        val child = children[index]
        viewModelScope.launch {
            val results = academyRepo.getResults(studentId = child.id)
            val attendance = academyRepo.getAttendance(studentId = child.id)
            val invoices = academyRepo.getInvoices(studentId = child.id)
            val payments = academyRepo.getPayments(studentId = child.id)
            val exams = academyRepo.getExaminations(classId = child.classId)
            _uiState.update {
                it.copy(results = results, attendance = attendance, invoices = invoices, payments = payments, examinations = exams)
            }
        }
    }

    fun refreshPortalData() {
        val account = _uiState.value.account ?: return
        val role = UserRole.fromDb(account.profile.role)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val settings = academyRepo.getSchoolSettings()
                val sessions = academyRepo.getSessions()
                val classes = academyRepo.getClasses()
                val subjects = academyRepo.getSubjects()
                val announcements = academyRepo.getAnnouncements()
                val calendar = academyRepo.getCalendarEvents()
                val notifications = academyRepo.getNotifications(account.profile.id)

                when (role) {
                    UserRole.STUDENT -> {
                        val st = account.student
                        val classId = st?.classId
                        val exams = academyRepo.getExaminations(classId = classId)
                        val attempts = if (st != null) academyRepo.getStudentAttempts(st.id) else emptyList()
                        val results = if (st != null) academyRepo.getResults(studentId = st.id) else emptyList()
                        val attendance = if (st != null) academyRepo.getAttendance(studentId = st.id) else emptyList()
                        val assignments = academyRepo.getAssignments(classId = classId)
                        val submissions = if (st != null) academyRepo.getSubmissions(studentId = st.id) else emptyList()
                        val materials = academyRepo.getMaterials(classId = classId)
                        val timetable = academyRepo.getTimetable(classId = classId)
                        val invoices = if (st != null) academyRepo.getInvoices(studentId = st.id) else emptyList()
                        val payments = if (st != null) academyRepo.getPayments(studentId = st.id) else emptyList()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                schoolSettings = settings,
                                sessions = sessions,
                                activeSession = sessions.firstOrNull { s -> s.isActive } ?: sessions.firstOrNull(),
                                classes = classes,
                                subjects = subjects,
                                announcements = announcements,
                                calendarEvents = calendar,
                                notifications = notifications,
                                examinations = exams,
                                studentAttempts = attempts,
                                results = results,
                                attendance = attendance,
                                assignments = assignments,
                                submissions = submissions,
                                materials = materials,
                                timetable = timetable,
                                invoices = invoices,
                                payments = payments
                            )
                        }
                    }
                    UserRole.TEACHER -> {
                        val students = academyRepo.getStudents()
                        val exams = academyRepo.getExaminations()
                        val results = academyRepo.getResults()
                        val attendance = academyRepo.getAttendance()
                        val assignments = academyRepo.getAssignments()
                        val materials = academyRepo.getMaterials()
                        val messages = academyRepo.getParentMessages()
                        val timetable = academyRepo.getTimetable()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                schoolSettings = settings,
                                sessions = sessions,
                                activeSession = sessions.firstOrNull { s -> s.isActive } ?: sessions.firstOrNull(),
                                classes = classes,
                                subjects = subjects,
                                students = students,
                                examinations = exams,
                                results = results,
                                attendance = attendance,
                                assignments = assignments,
                                materials = materials,
                                announcements = announcements,
                                calendarEvents = calendar,
                                timetable = timetable,
                                parentMessages = messages,
                                notifications = notifications
                            )
                        }
                    }
                    UserRole.PARENT -> {
                        val children = academyRepo.getLinkedChildren(account.profile.id)
                        val selected = children.getOrNull(_uiState.value.selectedChildIndex) ?: children.firstOrNull()
                        val teachers = academyRepo.getTeachers()
                        val messages = academyRepo.getParentMessages()
                        val results = if (selected != null) academyRepo.getResults(studentId = selected.id) else emptyList()
                        val attendance = if (selected != null) academyRepo.getAttendance(studentId = selected.id) else emptyList()
                        val invoices = if (selected != null) academyRepo.getInvoices(studentId = selected.id) else emptyList()
                        val payments = if (selected != null) academyRepo.getPayments(studentId = selected.id) else emptyList()
                        val exams = if (selected != null) academyRepo.getExaminations(classId = selected.classId) else emptyList()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                schoolSettings = settings,
                                sessions = sessions,
                                activeSession = sessions.firstOrNull { s -> s.isActive } ?: sessions.firstOrNull(),
                                classes = classes,
                                subjects = subjects,
                                teachers = teachers,
                                linkedChildren = children,
                                selectedChildIndex = 0,
                                examinations = exams,
                                results = results,
                                attendance = attendance,
                                invoices = invoices,
                                payments = payments,
                                announcements = announcements,
                                calendarEvents = calendar,
                                parentMessages = messages,
                                notifications = notifications
                            )
                        }
                    }
                    UserRole.ADMIN -> {
                        val profiles = academyRepo.getAllProfiles()
                        val students = academyRepo.getStudents()
                        val teachers = academyRepo.getTeachers()
                        val exams = academyRepo.getExaminations()
                        val results = academyRepo.getResults()
                        val attendance = academyRepo.getAttendance()
                        val invoices = academyRepo.getInvoices()
                        val payments = academyRepo.getPayments()
                        val timetable = academyRepo.getTimetable()
                        val auditLogs = academyRepo.getAuditLogs()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                schoolSettings = settings,
                                sessions = sessions,
                                activeSession = sessions.firstOrNull { s -> s.isActive } ?: sessions.firstOrNull(),
                                classes = classes,
                                subjects = subjects,
                                profiles = profiles,
                                students = students,
                                teachers = teachers,
                                examinations = exams,
                                results = results,
                                attendance = attendance,
                                invoices = invoices,
                                payments = payments,
                                announcements = announcements,
                                calendarEvents = calendar,
                                timetable = timetable,
                                notifications = notifications,
                                auditLogs = auditLogs
                            )
                        }
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Failed to refresh portal data") }
            }
        }
    }

    // --- CBT Examination Engine ---
    fun startOrResumeExam(exam: ExaminationDto) {
        val student = _uiState.value.account?.student ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val attemptRes = academyRepo.startOrResumeAttempt(exam, student.id)
            val attempt = attemptRes.getOrElse { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Cannot launch exam") }
                return@launch
            }
            val rawQuestions = academyRepo.getQuestions(exam.id)
            if (rawQuestions.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "This examination has no questions yet.") }
                return@launch
            }
            val preparedQuestions = ExamEngine.prepareQuestionsForAttempt(
                questions = rawQuestions,
                seed = attempt.seed,
                randomizeQuestions = exam.randomizeQuestions,
                randomizeOptions = exam.randomizeOptions
            )
            val savedAnswers = academyRepo.getSavedAnswers(attempt.id)
            val selectedOpts = mutableMapOf<String, String>()
            val textResps = mutableMapOf<String, String>()
            val marked = mutableSetOf<String>()

            savedAnswers.forEach { ans ->
                ans.selectedOptionId?.let { selectedOpts[ans.questionId] = it }
                ans.textResponse?.let { textResps[ans.questionId] = it }
                if (ans.isMarkedForReview) marked += ans.questionId
            }

            // Merge local draft backup if present
            prefs.getExamDraft(attempt.id)?.let { draftJson ->
                runCatching {
                    val obj = JSONObject(draftJson)
                    val optsObj = obj.optJSONObject("opts")
                    optsObj?.keys()?.forEach { k -> selectedOpts[k] = optsObj.getString(k) }
                    val txtObj = obj.optJSONObject("txt")
                    txtObj?.keys()?.forEach { k -> textResps[k] = txtObj.getString(k) }
                }
            }

            val initialRemaining = attempt.remainingSeconds.takeIf { it > 0 } ?: (exam.durationMinutes * 60)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    activeExam = ActiveExamSessionState(
                        exam = exam,
                        attempt = attempt,
                        questions = preparedQuestions,
                        selectedOptionIds = selectedOpts,
                        textResponses = textResps,
                        markedForReview = marked,
                        remainingSeconds = initialRemaining,
                        tabSwitchWarnings = attempt.tabSwitchCount
                    )
                )
            }
            startExamTimer()
        }
    }

    private fun startExamTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _uiState.value.activeExam ?: break
                if (current.submittedAttempt != null) break
                val next = current.remainingSeconds - 1
                if (next <= 0) {
                    _uiState.update { it.copy(activeExam = current.copy(remainingSeconds = 0)) }
                    submitActiveExam(autoReason = "Time expired — automatically submitted.")
                    break
                } else {
                    _uiState.update { it.copy(activeExam = current.copy(remainingSeconds = next)) }
                }
            }
        }
    }

    fun selectExamQuestionIndex(index: Int) {
        val current = _uiState.value.activeExam ?: return
        if (index in current.questions.indices) {
            _uiState.update { it.copy(activeExam = current.copy(currentIndex = index)) }
        }
    }

    fun selectObjectiveOption(questionId: String, optionId: String) {
        val current = _uiState.value.activeExam ?: return
        val updated = current.selectedOptionIds + (questionId to optionId)
        _uiState.update { it.copy(activeExam = current.copy(selectedOptionIds = updated)) }
        persistAnswerAsync(current.attempt.id, questionId, optionId, current.textResponses[questionId], questionId in current.markedForReview)
    }

    fun updateTextAnswer(questionId: String, text: String) {
        val current = _uiState.value.activeExam ?: return
        val updated = current.textResponses + (questionId to text)
        _uiState.update { it.copy(activeExam = current.copy(textResponses = updated)) }
        persistAnswerAsync(current.attempt.id, questionId, current.selectedOptionIds[questionId], text, questionId in current.markedForReview)
    }

    fun toggleMarkForReview(questionId: String) {
        val current = _uiState.value.activeExam ?: return
        val updated = if (questionId in current.markedForReview) current.markedForReview - questionId else current.markedForReview + questionId
        _uiState.update { it.copy(activeExam = current.copy(markedForReview = updated)) }
        persistAnswerAsync(current.attempt.id, questionId, current.selectedOptionIds[questionId], current.textResponses[questionId], questionId in updated)
    }

    private fun persistAnswerAsync(
        attemptId: String,
        questionId: String,
        selectedOptionId: String?,
        textResponse: String?,
        marked: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value.activeExam?.let { state ->
                val draft = JSONObject().apply {
                    put("opts", JSONObject(state.selectedOptionIds))
                    put("txt", JSONObject(state.textResponses))
                }
                prefs.saveExamDraft(attemptId, draft.toString())
            }
            academyRepo.saveSingleAnswer(
                StudentAnswerUpsertDto(
                    attemptId = attemptId,
                    questionId = questionId,
                    selectedOptionId = selectedOptionId,
                    textResponse = textResponse,
                    isMarkedForReview = marked
                )
            )
        }
    }

    fun onExamFocusLost() {
        val current = _uiState.value.activeExam ?: return
        if (current.submittedAttempt != null) return
        val actorId = _uiState.value.account?.profile?.id ?: return
        val nextCount = current.tabSwitchWarnings + 1
        _uiState.update {
            it.copy(activeExam = current.copy(tabSwitchWarnings = nextCount, showTabWarningModal = true))
        }
        viewModelScope.launch {
            academyRepo.logTabSwitch(current.attempt.id, nextCount, current.remainingSeconds, actorId)
            if (nextCount >= current.exam.maxTabSwitches) {
                submitActiveExam(autoReason = "Maximum anti-cheat tab-switch warnings (${current.exam.maxTabSwitches}) reached.")
            }
        }
    }

    fun dismissTabWarning() {
        val current = _uiState.value.activeExam ?: return
        _uiState.update { it.copy(activeExam = current.copy(showTabWarningModal = false)) }
    }

    fun submitActiveExam(autoReason: String? = null) {
        val current = _uiState.value.activeExam ?: return
        if (current.submittedAttempt != null) return
        timerJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            academyRepo.submitAttempt(
                attempt = current.attempt,
                questions = current.questions,
                selectedOptionIds = current.selectedOptionIds,
                textResponses = current.textResponses,
                markedForReview = current.markedForReview,
                remainingSeconds = current.remainingSeconds
            ).onSuccess { submitted ->
                prefs.clearExamDraft(current.attempt.id)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeExam = current.copy(submittedAttempt = submitted, showTabWarningModal = false),
                        statusMessage = autoReason ?: "Examination submitted successfully!"
                    )
                }
                refreshPortalData()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Submission failed") }
            }
        }
    }

    fun closeExamSummary() {
        timerJob?.cancel()
        _uiState.update { it.copy(activeExam = null) }
    }

    // --- Student Actions ---
    fun uploadStudentPassport(uri: Uri) {
        val student = _uiState.value.account?.student ?: return
        val profile = _uiState.value.account?.profile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val path = "${profile.id}/passport_${System.currentTimeMillis()}.jpg"
            academyRepo.uploadUriToStorage(getApplication(), "passports", path, uri, "image/jpeg")
                .onSuccess { url ->
                    academyRepo.updateStudentPassportUrl(student.id, profile.id, url)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            account = it.account?.copy(
                                profile = profile.copy(avatarUrl = url),
                                student = student.copy(passportUrl = url)
                            ),
                            statusMessage = "Passport photograph updated"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    fun submitAssignment(assignmentId: String, text: String, attachmentUri: Uri?) {
        val student = _uiState.value.account?.student ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var uploadedUrl: String? = null
            if (attachmentUri != null) {
                val uploadRes = academyRepo.uploadUriToStorage(
                    getApplication(),
                    "submissions",
                    "${student.id}/${assignmentId}_${System.currentTimeMillis()}",
                    attachmentUri
                )
                uploadedUrl = uploadRes.getOrNull()
            }
            academyRepo.submitAssignment(
                AssignmentSubmissionInsertDto(
                    assignmentId = assignmentId,
                    studentId = student.id,
                    submissionText = text,
                    attachmentUrl = uploadedUrl
                )
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Assignment submitted!") }
                refreshPortalData()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    // --- Paystack Payments ---
    fun startPaystackPayment(invoiceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            academyRepo.initializePaystackPayment(invoiceId)
                .onSuccess { init ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingPaystackReference = init.reference,
                            pendingPaystackUrl = init.authorizationUrl,
                            statusMessage = "Paystack checkout ready. Complete payment then tap Verify Payment."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Paystack error") }
                }
        }
    }

    fun verifyPaystackPayment(reference: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            academyRepo.verifyPaystackPayment(reference)
                .onSuccess { resp ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingPaystackReference = null,
                            statusMessage = resp.message ?: "Payment verified and receipt issued!"
                        )
                    }
                    refreshPortalData()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Verification failed") }
                }
        }
    }

    // --- Teacher Actions ---
    fun createExam(
        title: String,
        instructions: String,
        subjectId: String,
        classId: String,
        durationMinutes: Int,
        passMark: Double,
        startIso: String,
        endIso: String,
        randomizeQuestions: Boolean,
        randomizeOptions: Boolean,
        instantResults: Boolean
    ) {
        val profileId = _uiState.value.account?.profile?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            academyRepo.createExamination(
                ExaminationInsertDto(
                    title = title,
                    instructions = instructions,
                    subjectId = subjectId,
                    classId = classId,
                    sessionId = _uiState.value.activeSession?.id,
                    createdBy = profileId,
                    durationMinutes = durationMinutes,
                    passMark = passMark,
                    scheduledStart = startIso,
                    scheduledEnd = endIso,
                    randomizeQuestions = randomizeQuestions,
                    randomizeOptions = randomizeOptions,
                    instantResultsEnabled = instantResults,
                    allowResume = _uiState.value.schoolSettings?.allowExamResume ?: true,
                    maxTabSwitches = _uiState.value.schoolSettings?.maxTabSwitches ?: 3,
                    isPublished = true
                ),
                actorId = profileId
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Examination '$title' created!") }
                refreshPortalData()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun addManualQuestion(
        examId: String,
        type: QuestionType,
        prompt: String,
        marks: Double,
        imageUri: Uri?,
        options: List<String>,
        correctOptionIndex: Int,
        correctTextAnswer: String?,
        markingGuide: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var imageUrl: String? = null
            if (imageUri != null) {
                val res = academyRepo.uploadUriToStorage(
                    getApplication(),
                    "question-images",
                    "$examId/q_${System.currentTimeMillis()}.jpg",
                    imageUri,
                    "image/jpeg"
                )
                imageUrl = res.getOrNull()
            }
            academyRepo.addQuestionWithOptions(
                examId = examId,
                type = type,
                prompt = prompt,
                marks = marks,
                imageUrl = imageUrl,
                options = options,
                correctOptionIndex = correctOptionIndex,
                correctTextAnswer = correctTextAnswer,
                markingGuide = markingGuide,
                orderIndex = 0
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Question added to examination!") }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun importQuestionsFromCsv(examId: String, csvUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val content = getApplication<Application>().contentResolver.openInputStream(csvUri)?.bufferedReader()?.readText().orEmpty()
            val rows = CsvExcelHelper.parseQuestionsCsv(content)
            if (rows.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No valid question rows found in CSV.") }
                return@launch
            }
            academyRepo.importQuestionsBulk(examId, rows)
                .onSuccess { count ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Imported $count questions from CSV!") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    suspend fun fetchExamAttemptsWithAnswers(examId: String): Pair<List<ExamAttemptDto>, List<QuestionDto>> {
        val attempts = academyRepo.getExamAttemptsForExam(examId)
        val questions = academyRepo.getQuestions(examId)
        return attempts to questions
    }

    suspend fun fetchAnswersForAttempt(attemptId: String): List<StudentAnswerDto> =
        academyRepo.getSavedAnswers(attemptId)

    fun gradeTheoryAnswer(attempt: ExamAttemptDto, questionId: String, marks: Double, feedback: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            academyRepo.gradeTheoryAnswer(attempt, questionId, marks, feedback)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Theory score saved!") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    fun publishExamResults(examId: String) {
        viewModelScope.launch {
            academyRepo.publishExamResults(examId, true)
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Exam results published to students!") }
                    refreshPortalData()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun recordClassAttendance(studentId: String, classId: String, date: String, status: String) {
        val recorderId = _uiState.value.account?.profile?.id ?: return
        viewModelScope.launch {
            academyRepo.recordAttendance(
                AttendanceUpsertDto(
                    studentId = studentId,
                    classId = classId,
                    sessionId = _uiState.value.activeSession?.id,
                    date = date,
                    status = status,
                    recordedBy = recorderId
                )
            ).onSuccess {
                _uiState.update { it.copy(statusMessage = "Attendance saved ($status)") }
                refreshPortalData()
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun saveStudentSubjectResult(studentId: String, subjectId: String, classId: String, caScore: Double, examScore: Double) {
        val sessionId = _uiState.value.activeSession?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            academyRepo.saveResultWithPosition(studentId, subjectId, classId, sessionId, caScore, examScore)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Result & class position updated!") }
                    refreshPortalData()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    fun createAssignment(title: String, description: String, subjectId: String, classId: String, dueDate: String) {
        val teacherId = _uiState.value.account?.teacher?.id ?: return
        viewModelScope.launch {
            academyRepo.createAssignment(
                AssignmentInsertDto(title, description, subjectId, classId, teacherId, dueDate = dueDate)
            ).onSuccess {
                _uiState.update { it.copy(statusMessage = "Assignment published!") }
                refreshPortalData()
            }.onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun uploadLearningMaterial(title: String, description: String, subjectId: String, classId: String, fileUri: Uri, fileType: String) {
        val profileId = _uiState.value.account?.profile?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val path = "$classId/${System.currentTimeMillis()}_${title.replace(" ", "_")}"
            academyRepo.uploadUriToStorage(getApplication(), "materials", path, fileUri)
                .onSuccess { url ->
                    academyRepo.createMaterial(
                        LearningMaterialInsertDto(title, description, subjectId, classId, profileId, url, fileType)
                    ).onSuccess {
                        _uiState.update { it.copy(isLoading = false, statusMessage = "Learning material uploaded!") }
                        refreshPortalData()
                    }.onFailure { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    // --- Parent Actions ---
    fun linkChildByAdmission(admissionNumber: String) {
        val parentId = _uiState.value.account?.parent?.id ?: return
        viewModelScope.launch {
            academyRepo.linkParentToStudent(parentId, admissionNumber)
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Child linked successfully!") }
                    refreshPortalData()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun sendParentTeacherMessage(teacherProfileId: String, studentId: String?, subject: String, body: String) {
        val parentProfileId = _uiState.value.account?.profile?.id ?: return
        viewModelScope.launch {
            academyRepo.sendParentMessage(
                ParentMessageInsertDto(parentProfileId, teacherProfileId, studentId, subject, body)
            ).onSuccess {
                _uiState.update { it.copy(statusMessage = "Message sent to teacher!") }
                refreshPortalData()
            }.onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    // --- Admin Actions ---
    fun toggleAccountActive(profileId: String, active: Boolean) {
        val actorId = _uiState.value.account?.profile?.id ?: return
        viewModelScope.launch {
            academyRepo.toggleAccountActive(profileId, active, actorId)
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = if (active) "Account activated" else "Account deactivated") }
                    refreshPortalData()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun createClass(name: String, level: String, department: String) {
        viewModelScope.launch {
            academyRepo.createClass(SchoolClassInsertDto(name, level, department))
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Class '$name' added") }
                    refreshPortalData()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun createSubject(code: String, name: String, department: String, classId: String?, teacherId: String?) {
        viewModelScope.launch {
            academyRepo.createSubject(SubjectInsertDto(code, name, department, classId, teacherId))
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Subject '$name' added") }
                    refreshPortalData()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun createSession(name: String, term: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            academyRepo.createSession(AcademicSessionInsertDto(name, term, startDate, endDate, true))
                .onSuccess { created ->
                    academyRepo.activateSession(created.id, _uiState.value.sessions + created)
                    _uiState.update { it.copy(statusMessage = "Academic session $name ($term) created") }
                    refreshPortalData()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun approveResult(resultId: String) {
        val actorId = _uiState.value.account?.profile?.id ?: return
        viewModelScope.launch {
            academyRepo.approveResult(resultId, true, actorId)
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Result approved for publication") }
                    refreshPortalData()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun createInvoice(studentId: String, title: String, feeType: String, amountNaira: Double, dueDate: String) {
        viewModelScope.launch {
            val kobo = (amountNaira * 100).toLong()
            academyRepo.createInvoice(
                InvoiceInsertDto(
                    studentId = studentId,
                    sessionId = _uiState.value.activeSession?.id,
                    title = title,
                    feeType = feeType,
                    amountKobo = kobo,
                    dueDate = dueDate
                )
            ).onSuccess {
                _uiState.update { it.copy(statusMessage = "Invoice issued to student") }
                refreshPortalData()
            }.onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun createAnnouncement(title: String, body: String, targetRole: String, priority: String) {
        val authorId = _uiState.value.account?.profile?.id ?: return
        viewModelScope.launch {
            academyRepo.createAnnouncement(AnnouncementInsertDto(title, body, targetRole, priority, authorId))
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Announcement published") }
                    refreshPortalData()
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun updateSchoolSettings(
        schoolName: String,
        motto: String,
        defaultDuration: Int,
        defaultPassMark: Double,
        maxTabSwitches: Int,
        instantResults: Boolean,
        allowResume: Boolean,
        paystackSubaccount: String?
    ) {
        val settingsId = _uiState.value.schoolSettings?.id ?: return
        viewModelScope.launch {
            academyRepo.updateSchoolSettings(
                settingsId,
                SchoolSettingsUpdateDto(
                    schoolName = schoolName,
                    motto = motto,
                    logoUrl = _uiState.value.schoolSettings?.logoUrl,
                    defaultExamDurationMinutes = defaultDuration,
                    defaultPassMark = defaultPassMark,
                    maxTabSwitches = maxTabSwitches,
                    allowInstantResults = instantResults,
                    allowExamResume = allowResume,
                    paystackSubaccountCode = paystackSubaccount?.ifBlank { null }
                )
            ).onSuccess {
                _uiState.update { it.copy(statusMessage = "School settings saved!") }
                refreshPortalData()
            }.onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            academyRepo.markNotificationRead(id)
            refreshPortalData()
        }
    }

    fun exportBackupJson() {
        val st = _uiState.value
        val backup = JSONObject().apply {
            put("exported_at", java.time.Instant.now().toString())
            put("school_name", st.schoolSettings?.schoolName ?: "Green Light Academy")
            put("students_count", st.students.size)
            put("teachers_count", st.teachers.size)
            put("classes", JSONArray(st.classes.map { it.name }))
            put("results_count", st.results.size)
            put("invoices_count", st.invoices.size)
        }
        val file = PdfGenerator.saveTextExport(getApplication(), "GLA_Backup_${System.currentTimeMillis()}.json", backup.toString(2))
        PdfGenerator.shareOrOpenFile(getApplication(), file, "application/json")
    }
}
