package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.util.CsvExcelHelper
import com.example.util.PdfGenerator
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TeacherPortalContent(
    tabIndex: Int,
    state: MainUiState,
    onCreateExam: (
        title: String, instructions: String, subjectId: String, classId: String,
        durationMinutes: Int, passMark: Double, startIso: String, endIso: String,
        randomizeQuestions: Boolean, randomizeOptions: Boolean, instantResults: Boolean
    ) -> Unit,
    onAddQuestion: (
        examId: String, type: QuestionType, prompt: String, marks: Double,
        imageUri: Uri?, options: List<String>, correctIdx: Int, correctText: String?, guide: String?
    ) -> Unit,
    onImportQuestionsCsv: (examId: String, csvUri: Uri) -> Unit,
    onFetchSubmissions: suspend (examId: String) -> Pair<List<ExamAttemptDto>, List<QuestionDto>>,
    onFetchAnswers: suspend (attemptId: String) -> List<StudentAnswerDto>,
    onGradeTheory: (ExamAttemptDto, String, Double, String) -> Unit,
    onPublishExamResults: (examId: String) -> Unit,
    onRecordAttendance: (studentId: String, classId: String, date: String, status: String) -> Unit,
    onSaveResult: (studentId: String, subjectId: String, classId: String, ca: Double, exam: Double) -> Unit,
    onCreateAssignment: (title: String, desc: String, subjectId: String, classId: String, dueDate: String) -> Unit,
    onUploadMaterial: (title: String, desc: String, subjectId: String, classId: String, uri: Uri, fileType: String) -> Unit
) {
    when (tabIndex) {
        0 -> TeacherOverviewTab(state)
        1 -> TeacherExamsTab(
            state, onCreateExam, onAddQuestion, onImportQuestionsCsv,
            onFetchSubmissions, onFetchAnswers, onGradeTheory, onPublishExamResults
        )
        2 -> TeacherClassroomTab(state, onRecordAttendance, onSaveResult, onCreateAssignment, onUploadMaterial)
    }
}

@Composable
private fun TeacherOverviewTab(state: MainUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AcademyHeroBanner(
                title = "Welcome, ${state.account?.profile?.fullName ?: "Educator"}",
                subtitle = "Staff Code: ${state.account?.teacher?.staffCode ?: "-"} • ${state.account?.teacher?.department ?: "Faculty"}",
                badgeText = "TEACHER WORKSPACE"
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMetricCard("CBT Exams", "${state.examinations.size}", Icons.Default.Quiz, EmeraldPrimary, Modifier.weight(1f))
                KpiMetricCard("Students", "${state.students.size}", Icons.Default.Groups, SkyInfo, Modifier.weight(1f))
                KpiMetricCard("Subjects", "${state.subjects.size}", Icons.Default.MenuBook, GoldAccent, Modifier.weight(1f))
            }
        }
        item { Text("Parent Inquiries", style = MaterialTheme.typography.titleMedium) }
        if (state.parentMessages.isEmpty()) {
            item { EmptyStateCard("No Parent Messages", "Messages sent by parents from the Parent Portal appear here.") }
        } else {
            items(state.parentMessages.take(5)) { msg ->
                ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(msg.subject, style = MaterialTheme.typography.titleSmall)
                        Text(msg.body, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherExamsTab(
    state: MainUiState,
    onCreateExam: (String, String, String, String, Int, Double, String, String, Boolean, Boolean, Boolean) -> Unit,
    onAddQuestion: (String, QuestionType, String, Double, Uri?, List<String>, Int, String?, String?) -> Unit,
    onImportQuestionsCsv: (String, Uri) -> Unit,
    onFetchSubmissions: suspend (String) -> Pair<List<ExamAttemptDto>, List<QuestionDto>>,
    onFetchAnswers: suspend (String) -> List<StudentAnswerDto>,
    onGradeTheory: (ExamAttemptDto, String, Double, String) -> Unit,
    onPublishExamResults: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateExam by remember { mutableStateOf(false) }
    var examForQuestion by remember { mutableStateOf<ExaminationDto?>(null) }
    var examForCsv by remember { mutableStateOf<ExaminationDto?>(null) }
    var examForSubmissions by remember { mutableStateOf<ExaminationDto?>(null) }
    var loadedSubmissions by remember { mutableStateOf<Pair<List<ExamAttemptDto>, List<QuestionDto>>?>(null) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = examForCsv
        if (uri != null && target != null) {
            onImportQuestionsCsv(target.id, uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CBT Examinations", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { showCreateExam = true }, modifier = Modifier.testTag("teacher_create_exam_button")) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("New Exam")
                }
            }
            OutlinedButton(
                onClick = {
                    val csv = CsvExcelHelper.sampleQuestionsTemplate()
                    val file = PdfGenerator.saveTextExport(context, "GLA_Question_Template.csv", csv)
                    PdfGenerator.shareOrOpenFile(context, file, "text/csv")
                },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(6.dp))
                Text("Download CSV/Excel Question Template")
            }
        }

        if (state.examinations.isEmpty()) {
            item { EmptyStateCard("No Exams Created", "Tap 'New Exam' to create a CBT paper with MCQ, True/False, Fill-Gap, or Theory questions.") }
        } else {
            items(state.examinations) { exam ->
                ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(exam.title, style = MaterialTheme.typography.titleMedium)
                                Text("${exam.classes?.name ?: "Class"} • ${exam.subjects?.name ?: "Subject"} • ${exam.durationMinutes}m", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusPill(
                                text = if (exam.resultsPublished) "PUBLISHED" else "ACTIVE",
                                containerColor = EmeraldLightContainer,
                                contentColor = EmeraldOnContainer
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { examForQuestion = exam },
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Question") }
                            OutlinedButton(
                                onClick = {
                                    examForCsv = exam
                                    csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("CSV Upload") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    examForSubmissions = exam
                                    scope.launch { loadedSubmissions = onFetchSubmissions(exam.id) }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Submissions & Mark") }
                            if (!exam.resultsPublished) {
                                Button(
                                    onClick = { onPublishExamResults(exam.id) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Publish Results") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateExam) {
        var title by remember { mutableStateOf("") }
        var instructions by remember { mutableStateOf("Answer all questions honestly. Do not exit full-screen.") }
        var duration by remember { mutableStateOf((state.schoolSettings?.defaultExamDurationMinutes ?: 45).toString()) }
        var passMark by remember { mutableStateOf((state.schoolSettings?.defaultPassMark ?: 50.0).toString()) }
        var selectedClassId by remember { mutableStateOf(state.classes.firstOrNull()?.id.orEmpty()) }
        var selectedSubjectId by remember { mutableStateOf(state.subjects.firstOrNull()?.id.orEmpty()) }
        var randomizeQ by remember { mutableStateOf(true) }
        var randomizeOpts by remember { mutableStateOf(true) }
        var instantResults by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateExam = false },
            title = { Text("Create CBT Examination") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Exam Title") }, singleLine = true)
                    OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("Instructions") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Mins") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = passMark, onValueChange = { passMark = it }, label = { Text("Pass %") }, modifier = Modifier.weight(1f))
                    }
                    if (state.classes.isNotEmpty()) {
                        Text("Class", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.classes.take(3).forEach { c ->
                                FilterChip(selected = selectedClassId == c.id, onClick = { selectedClassId = c.id }, label = { Text(c.name) })
                            }
                        }
                    }
                    if (state.subjects.isNotEmpty()) {
                        Text("Subject", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.subjects.take(3).forEach { s ->
                                FilterChip(selected = selectedSubjectId == s.id, onClick = { selectedSubjectId = s.id }, label = { Text(s.code) })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = randomizeQ, onCheckedChange = { randomizeQ = it })
                        Text("Randomize Qs", style = MaterialTheme.typography.bodySmall)
                        Checkbox(checked = instantResults, onCheckedChange = { instantResults = it })
                        Text("Instant Score", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && selectedClassId.isNotBlank() && selectedSubjectId.isNotBlank()) {
                        val now = java.time.Instant.now()
                        onCreateExam(
                            title, instructions, selectedSubjectId, selectedClassId,
                            duration.toIntOrNull() ?: 45, passMark.toDoubleOrNull() ?: 50.0,
                            now.toString(), now.plusSeconds(7 * 86400).toString(),
                            randomizeQ, randomizeOpts, instantResults
                        )
                        showCreateExam = false
                    }
                }) { Text("Create Exam") }
            },
            dismissButton = { TextButton(onClick = { showCreateExam = false }) { Text("Cancel") } }
        )
    }

    examForQuestion?.let { exam ->
        var qType by remember { mutableStateOf(QuestionType.MCQ) }
        var prompt by remember { mutableStateOf("") }
        var marks by remember { mutableStateOf("2") }
        var optA by remember { mutableStateOf("") }
        var optB by remember { mutableStateOf("") }
        var optC by remember { mutableStateOf("") }
        var optD by remember { mutableStateOf("") }
        var correctIdx by remember { mutableIntStateOf(0) }
        var correctText by remember { mutableStateOf("true") }
        var guide by remember { mutableStateOf("") }
        var imageUri by remember { mutableStateOf<Uri?>(null) }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            imageUri = uri
        }

        AlertDialog(
            onDismissRequest = { examForQuestion = null },
            title = { Text("Add Question") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        QuestionType.entries.forEach { t ->
                            FilterChip(
                                selected = qType == t,
                                onClick = { qType = t },
                                label = { Text(t.name.take(4)) }
                            )
                        }
                    }
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Question Prompt") }, minLines = 2)
                    OutlinedTextField(value = marks, onValueChange = { marks = it }, label = { Text("Marks") }, singleLine = true)
                    OutlinedButton(onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (imageUri != null) "Diagram Attached" else "Attach Diagram (Optional)")
                    }
                    when (qType) {
                        QuestionType.MCQ -> {
                            OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Option A") }, singleLine = true)
                            OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Option B") }, singleLine = true)
                            OutlinedTextField(value = optC, onValueChange = { optC = it }, label = { Text("Option C") }, singleLine = true)
                            OutlinedTextField(value = optD, onValueChange = { optD = it }, label = { Text("Option D") }, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("A" to 0, "B" to 1, "C" to 2, "D" to 3).forEach { (lbl, idx) ->
                                    FilterChip(selected = correctIdx == idx, onClick = { correctIdx = idx }, label = { Text("Ans: $lbl") })
                                }
                            }
                        }
                        QuestionType.TRUE_FALSE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = correctText == "true", onClick = { correctText = "true" }, label = { Text("True") })
                                FilterChip(selected = correctText == "false", onClick = { correctText = "false" }, label = { Text("False") })
                            }
                        }
                        QuestionType.FILL_GAP -> {
                            OutlinedTextField(value = correctText, onValueChange = { correctText = it }, label = { Text("Accepted Answer(s) e.g. Au|Gold") })
                        }
                        QuestionType.THEORY -> {
                            OutlinedTextField(value = guide, onValueChange = { guide = it }, label = { Text("Marking Guide") })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (prompt.isNotBlank()) {
                        onAddQuestion(
                            exam.id, qType, prompt, marks.toDoubleOrNull() ?: 1.0, imageUri,
                            listOf(optA, optB, optC, optD).filter { it.isNotBlank() },
                            correctIdx,
                            if (qType == QuestionType.TRUE_FALSE || qType == QuestionType.FILL_GAP) correctText else null,
                            guide.ifBlank { null }
                        )
                        examForQuestion = null
                    }
                }) { Text("Save Question") }
            },
            dismissButton = { TextButton(onClick = { examForQuestion = null }) { Text("Cancel") } }
        )
    }

    examForSubmissions?.let { exam ->
        val data = loadedSubmissions
        var gradingAttempt by remember { mutableStateOf<ExamAttemptDto?>(null) }
        var attemptAnswers by remember { mutableStateOf<List<StudentAnswerDto>>(emptyList()) }

        AlertDialog(
            onDismissRequest = { examForSubmissions = null; loadedSubmissions = null },
            title = { Text("Submissions: ${exam.title}") },
            text = {
                if (data == null) {
                    CircularProgressIndicator()
                } else if (data.first.isEmpty()) {
                    Text("No student submissions recorded yet.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.first.forEach { att ->
                            OutlinedCard {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(att.students?.profiles?.fullName ?: "Student", style = MaterialTheme.typography.titleSmall)
                                        Text("Obj: ${att.objectiveScore} | Theory: ${att.theoryScore} | Tabs: ${att.tabSwitchCount}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = {
                                        gradingAttempt = att
                                        scope.launch { attemptAnswers = onFetchAnswers(att.id) }
                                    }) { Text("Mark Theory") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { examForSubmissions = null; loadedSubmissions = null }) { Text("Close") } }
        )

        gradingAttempt?.let { att ->
            val theoryQuestions = data?.second?.filter { QuestionType.fromDb(it.questionType) == QuestionType.THEORY }.orEmpty()
            var selectedQId by remember { mutableStateOf(theoryQuestions.firstOrNull()?.id.orEmpty()) }
            var scoreInput by remember { mutableStateOf("5") }
            var feedbackInput by remember { mutableStateOf("Good explanation") }

            AlertDialog(
                onDismissRequest = { gradingAttempt = null },
                title = { Text("Mark Theory Answers") },
                text = {
                    if (theoryQuestions.isEmpty()) {
                        Text("This exam has no theory questions.")
                    } else {
                        val currentAns = attemptAnswers.firstOrNull { it.questionId == selectedQId }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(theoryQuestions.firstOrNull { it.id == selectedQId }?.prompt.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Text("Student Response: ${currentAns?.textResponse ?: "(No response)"}", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value = scoreInput, onValueChange = { scoreInput = it }, label = { Text("Marks Awarded") })
                            OutlinedTextField(value = feedbackInput, onValueChange = { feedbackInput = it }, label = { Text("Teacher Feedback") })
                        }
                    }
                },
                confirmButton = {
                    if (theoryQuestions.isNotEmpty()) {
                        Button(onClick = {
                            onGradeTheory(att, selectedQId, scoreInput.toDoubleOrNull() ?: 0.0, feedbackInput)
                            gradingAttempt = null
                        }) { Text("Save Grade") }
                    }
                },
                dismissButton = { TextButton(onClick = { gradingAttempt = null }) { Text("Close") } }
            )
        }
    }
}

@Composable
private fun TeacherClassroomTab(
    state: MainUiState,
    onRecordAttendance: (String, String, String, String) -> Unit,
    onSaveResult: (String, String, String, Double, Double) -> Unit,
    onCreateAssignment: (String, String, String, String, String) -> Unit,
    onUploadMaterial: (String, String, String, String, Uri, String) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }
    var showAssignmentDialog by remember { mutableStateOf(false) }
    var showMaterialDialog by remember { mutableStateOf(false) }
    var resultStudent by remember { mutableStateOf<StudentDto?>(null) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Attendance") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Results") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Materials & Tasks") })
        }

        when (subTab) {
            0 -> {
                val today = remember { LocalDate.now().toString() }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Mark Attendance ($today)", style = MaterialTheme.typography.titleMedium) }
                    items(state.students) { st ->
                        ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${st.profiles?.fullName ?: "Student"} (${st.admissionNumber})", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("present", "absent", "late", "excused").forEach { status ->
                                        AssistChip(
                                            onClick = {
                                                st.classId?.let { cid -> onRecordAttendance(st.id, cid, today, status) }
                                            },
                                            label = { Text(status.replaceFirstChar { it.uppercase() }) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Enter CA (40) & Exam (60) Scores", style = MaterialTheme.typography.titleMedium) }
                    items(state.students) { st ->
                        ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(st.profiles?.fullName ?: "Student", style = MaterialTheme.typography.titleSmall)
                                    Text("${st.classes?.name ?: "Unassigned"} • ${st.admissionNumber}", style = MaterialTheme.typography.bodySmall)
                                }
                                FilledTonalButton(onClick = { resultStudent = st }) { Text("Record Score") }
                            }
                        }
                    }
                }
            }
            2 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { showAssignmentDialog = true }, modifier = Modifier.weight(1f)) {
                                Text("+ Assignment")
                            }
                            FilledTonalButton(onClick = { showMaterialDialog = true }, modifier = Modifier.weight(1f)) {
                                Text("+ Material")
                            }
                        }
                    }
                    items(state.materials) { mat ->
                        OutlinedCard {
                            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(mat.title, style = MaterialTheme.typography.titleSmall)
                                Text(mat.fileType, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    resultStudent?.let { st ->
        var subjectId by remember { mutableStateOf(state.subjects.firstOrNull()?.id.orEmpty()) }
        var ca by remember { mutableStateOf("30") }
        var exam by remember { mutableStateOf("50") }
        AlertDialog(
            onDismissRequest = { resultStudent = null },
            title = { Text("Record Result: ${st.profiles?.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.subjects.take(3).forEach { s ->
                            FilterChip(selected = subjectId == s.id, onClick = { subjectId = s.id }, label = { Text(s.code) })
                        }
                    }
                    OutlinedTextField(value = ca, onValueChange = { ca = it }, label = { Text("CA Score (0-40)") })
                    OutlinedTextField(value = exam, onValueChange = { exam = it }, label = { Text("Exam Score (0-60)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cid = st.classId ?: state.classes.firstOrNull()?.id.orEmpty()
                    if (cid.isNotBlank() && subjectId.isNotBlank()) {
                        onSaveResult(st.id, subjectId, cid, ca.toDoubleOrNull() ?: 0.0, exam.toDoubleOrNull() ?: 0.0)
                        resultStudent = null
                    }
                }) { Text("Save & Rank") }
            },
            dismissButton = { TextButton(onClick = { resultStudent = null }) { Text("Cancel") } }
        )
    }

    if (showAssignmentDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var due by remember { mutableStateOf(LocalDate.now().plusDays(5).toString()) }
        val classId = state.classes.firstOrNull()?.id.orEmpty()
        val subjectId = state.subjects.firstOrNull()?.id.orEmpty()
        AlertDialog(
            onDismissRequest = { showAssignmentDialog = false },
            title = { Text("Create Assignment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Instructions") })
                    OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("Due Date (YYYY-MM-DD)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && classId.isNotBlank() && subjectId.isNotBlank()) {
                        onCreateAssignment(title, desc, subjectId, classId, "${due}T23:59:00Z")
                        showAssignmentDialog = false
                    }
                }) { Text("Publish") }
            },
            dismissButton = { TextButton(onClick = { showAssignmentDialog = false }) { Text("Cancel") } }
        )
    }

    if (showMaterialDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var pickedUri by remember { mutableStateOf<Uri?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pickedUri = it }
        val classId = state.classes.firstOrNull()?.id.orEmpty()
        val subjectId = state.subjects.firstOrNull()?.id.orEmpty()
        AlertDialog(
            onDismissRequest = { showMaterialDialog = false },
            title = { Text("Upload Learning Material") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Material Title") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Summary") })
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Text(if (pickedUri != null) "Document Selected" else "Select PDF / Document")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val uri = pickedUri
                    if (title.isNotBlank() && uri != null && classId.isNotBlank() && subjectId.isNotBlank()) {
                        onUploadMaterial(title, desc, subjectId, classId, uri, "PDF")
                        showMaterialDialog = false
                    }
                }) { Text("Upload") }
            },
            dismissButton = { TextButton(onClick = { showMaterialDialog = false }) { Text("Cancel") } }
        )
    }
}
