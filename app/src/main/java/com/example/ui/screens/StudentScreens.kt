package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.util.ExamEngine
import com.example.util.PdfGenerator

@Composable
fun StudentPortalContent(
    tabIndex: Int,
    state: MainUiState,
    onUploadPassport: (Uri) -> Unit,
    onStartExam: (ExaminationDto) -> Unit,
    onSubmitAssignment: (assignmentId: String, text: String, attachmentUri: Uri?) -> Unit,
    onPayInvoice: (invoiceId: String) -> Unit,
    onVerifyPaystack: (reference: String) -> Unit,
    onMarkNotificationRead: (id: String) -> Unit
) {
    when (tabIndex) {
        0 -> StudentOverviewTab(state, onUploadPassport)
        1 -> StudentExamsTab(state, onStartExam)
        2 -> StudentAcademicsTab(state, onSubmitAssignment)
        3 -> StudentFeesTab(state, onPayInvoice, onVerifyPaystack, onMarkNotificationRead)
    }
}

@Composable
private fun StudentOverviewTab(
    state: MainUiState,
    onUploadPassport: (Uri) -> Unit
) {
    val context = LocalContext.current
    val student = state.account?.student
    val profile = state.account?.profile
    val passportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onUploadPassport) }

    val approvedResults = state.results.filter { it.isApproved }
    val avgScore = if (approvedResults.isNotEmpty()) approvedResults.map { it.totalScore }.average() else 0.0
    val presentCount = state.attendance.count { it.status == "present" }
    val attendanceRate = if (state.attendance.isNotEmpty()) (presentCount * 100 / state.attendance.size) else 100

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(EmeraldLightContainer)
                            .clickable {
                                passportPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .testTag("student_passport_avatar"),
                        contentAlignment = Alignment.Center
                    ) {
                        val photoUrl = student?.passportUrl ?: profile?.avatarUrl
                        if (!photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Student passport photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Upload passport", tint = EmeraldPrimary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile?.fullName ?: "Student", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Adm No: ${student?.admissionNumber ?: "-"}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${student?.classes?.name ?: "Unassigned"} • ${student?.department ?: "General"} Dept",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (student != null) {
                        FilledTonalIconButton(
                            onClick = {
                                val file = PdfGenerator.generateStudentIdCardPdf(
                                    context = context,
                                    schoolName = state.schoolSettings?.schoolName ?: "Green Light Academy",
                                    sessionLabel = state.activeSession?.let { "${it.name} (${it.term})" } ?: "2026/2027",
                                    student = student
                                )
                                PdfGenerator.shareOrOpenFile(context, file, "application/pdf")
                            },
                            modifier = Modifier.testTag("print_id_card_button")
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = "Printable Student ID Card")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMetricCard(
                    label = "Average Score",
                    value = "${"%.1f".format(avgScore)}%",
                    icon = Icons.Default.Assessment,
                    accentColor = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Attendance",
                    value = "$attendanceRate%",
                    icon = Icons.Default.EventAvailable,
                    accentColor = SkyInfo,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Active Exams",
                    value = "${state.examinations.count { it.isPublished }}",
                    icon = Icons.Default.Quiz,
                    accentColor = GoldAccent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            BarAnalyticsChart(
                title = "Subject Performance Overview",
                entries = approvedResults.take(6).map { (it.subjects?.code ?: it.subjects?.name ?: "Subj") to it.totalScore.toFloat() }
            )
        }

        if (state.timetable.isNotEmpty()) {
            item { Text("Class Timetable", style = MaterialTheme.typography.titleMedium) }
            items(state.timetable.take(5)) { slot ->
                OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(slot.subjects?.name ?: "Subject", style = MaterialTheme.typography.titleSmall)
                            Text("${slot.dayOfWeek} • ${slot.room ?: "Block A"}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${slot.startTime} - ${slot.endTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item { Text("School Announcements", style = MaterialTheme.typography.titleMedium) }
        if (state.announcements.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Announcements Yet",
                    description = "Important notices from school leadership will appear here."
                )
            }
        } else {
            items(state.announcements.take(4)) { ann ->
                ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(ann.title, style = MaterialTheme.typography.titleSmall)
                            if (ann.priority == "urgent") {
                                StatusPill("URGENT", CrimsonContainer, CrimsonError)
                            }
                        }
                        Text(ann.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentExamsTab(
    state: MainUiState,
    onStartExam: (ExaminationDto) -> Unit
) {
    var selectedExamForInstructions by remember { mutableStateOf<ExaminationDto?>(null) }
    val attemptsByExam = state.studentAttempts.associateBy { it.examinationId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("CBT Examination Timetable", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Full-screen anti-cheat mode activates once you begin an examination.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.examinations.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Scheduled CBT Exams",
                    description = "Your teachers have not published an examination for your class yet."
                )
            }
        } else {
            items(state.examinations) { exam ->
                val attempt = attemptsByExam[exam.id]
                ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exam.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${exam.subjects?.name ?: "Subject"} • ${exam.durationMinutes} mins • Pass: ${exam.passMark}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            when (attempt?.status) {
                                "graded", "submitted" -> StatusPill(
                                    text = if (exam.instantResultsEnabled || exam.resultsPublished) "${attempt.totalScore} pts" else "SUBMITTED",
                                    containerColor = EmeraldLightContainer,
                                    contentColor = EmeraldOnContainer
                                )
                                "in_progress" -> StatusPill("IN PROGRESS", GoldContainer, Color(0xFF78350F))
                                else -> StatusPill("READY", SkyContainer, SkyInfo)
                            }
                        }
                        Text(
                            text = "Window: ${exam.scheduledStart.take(16)} to ${exam.scheduledEnd.take(16)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (attempt == null || attempt.status == "in_progress") {
                            Button(
                                onClick = { selectedExamForInstructions = exam },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("launch_exam_${exam.id}")
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (attempt?.status == "in_progress") "Resume Examination" else "Read Instructions & Start")
                            }
                        }
                    }
                }
            }
        }
    }

    selectedExamForInstructions?.let { exam ->
        AlertDialog(
            onDismissRequest = { selectedExamForInstructions = null },
            title = { Text("Examination Instructions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exam.title, fontWeight = FontWeight.Bold)
                    Text(exam.instructions)
                    Text("• Duration: ${exam.durationMinutes} minutes (auto-submits on timeout)")
                    Text("• Anti-Cheat: Do not switch apps. Max ${exam.maxTabSwitches} warnings allowed.")
                    Text("• Answers save automatically as you progress.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedExamForInstructions = null
                        onStartExam(exam)
                    },
                    modifier = Modifier.testTag("confirm_start_exam")
                ) { Text("Enter Full-Screen CBT") }
            },
            dismissButton = {
                TextButton(onClick = { selectedExamForInstructions = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StudentAcademicsTab(
    state: MainUiState,
    onSubmitAssignment: (assignmentId: String, text: String, attachmentUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) }
    var activeAssignment by remember { mutableStateOf<AssignmentDto?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Results") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Materials") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Tasks") })
            Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text("Attend.") })
        }

        when (subTab) {
            0 -> {
                val approved = state.results.filter { it.isApproved }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        val student = state.account?.student
                        if (student != null && approved.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val file = PdfGenerator.generateReportCardPdf(
                                        context = context,
                                        schoolName = state.schoolSettings?.schoolName ?: "Green Light Academy",
                                        sessionLabel = state.activeSession?.let { "${it.name} - ${it.term}" } ?: "2026/2027",
                                        student = student,
                                        results = approved
                                    )
                                    PdfGenerator.shareOrOpenFile(context, file, "application/pdf")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_report_card_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Download Official Report Card PDF")
                            }
                        }
                    }
                    if (approved.isEmpty()) {
                        item {
                            EmptyStateCard("No Approved Results Yet", "Published terminal results will appear here once approved by the school admin.")
                        }
                    } else {
                        items(approved) { res ->
                            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(res.subjects?.name ?: "Subject", style = MaterialTheme.typography.titleMedium)
                                        Text("CA: ${res.caScore} • Exam: ${res.examScore} • Pos: ${ExamEngine.formatOrdinal(res.classPosition)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${res.totalScore}%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                        StatusPill("${res.grade} (${res.remark})", EmeraldLightContainer, EmeraldOnContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.materials.isEmpty()) {
                        item { EmptyStateCard("No Learning Materials", "Uploaded lecture notes and study guides appear here.") }
                    } else {
                        items(state.materials) { mat ->
                            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mat.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${mat.subjects?.name ?: "Course"} • ${mat.fileType}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    FilledTonalButton(onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mat.fileUrl))) }
                                    }) { Text("Open") }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                val submittedIds = state.submissions.map { it.assignmentId }.toSet()
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.assignments.isEmpty()) {
                        item { EmptyStateCard("No Assignments", "Class homework and project tasks appear here.") }
                    } else {
                        items(state.assignments) { task ->
                            val isDone = task.id in submittedIds
                            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(task.title, style = MaterialTheme.typography.titleMedium)
                                        StatusPill(
                                            text = if (isDone) "SUBMITTED" else "DUE ${task.dueDate.take(10)}",
                                            containerColor = if (isDone) EmeraldLightContainer else GoldContainer,
                                            contentColor = if (isDone) EmeraldOnContainer else Color(0xFF78350F)
                                        )
                                    }
                                    Text(task.description, style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = { activeAssignment = task }, enabled = !isDone) {
                                        Text(if (isDone) "Submitted" else "Submit Assignment")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.attendance.isEmpty()) {
                        item { EmptyStateCard("No Attendance Records", "Daily roll-call records appear here.") }
                    } else {
                        items(state.attendance) { att ->
                            OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(att.date, style = MaterialTheme.typography.titleSmall)
                                    StatusPill(
                                        text = att.status.uppercase(),
                                        containerColor = if (att.status == "present") EmeraldLightContainer else GoldContainer,
                                        contentColor = if (att.status == "present") EmeraldOnContainer else Color(0xFF78350F)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activeAssignment?.let { task ->
        var answerText by remember { mutableStateOf("") }
        var pickedUri by remember { mutableStateOf<Uri?>(null) }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickedUri = uri
        }
        AlertDialog(
            onDismissRequest = { activeAssignment = null },
            title = { Text("Submit: ${task.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it },
                        label = { Text("Submission Notes / Answer") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (pickedUri != null) "Attachment Selected" else "Attach File (Optional)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (answerText.isNotBlank() || pickedUri != null) {
                        onSubmitAssignment(task.id, answerText, pickedUri)
                        activeAssignment = null
                    }
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { activeAssignment = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StudentFeesTab(
    state: MainUiState,
    onPayInvoice: (String) -> Unit,
    onVerifyPaystack: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit
) {
    val context = LocalContext.current
    val pendingRef = state.pendingPaystackReference

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!pendingRef.isNullOrBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = EmeraldLightContainer), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paystack Verification Pending", style = MaterialTheme.typography.titleSmall, color = EmeraldOnContainer)
                        Text("Reference: $pendingRef", style = MaterialTheme.typography.labelSmall, color = EmeraldOnContainer)
                        Button(onClick = { onVerifyPaystack(pendingRef) }) {
                            Text("Verify Payment on Server")
                        }
                    }
                }
            }
        }

        item { Text("Invoices & Fee Balances", style = MaterialTheme.typography.titleLarge) }
        if (state.invoices.isEmpty()) {
            item { EmptyStateCard("No Outstanding Invoices", "School and examination fee invoices appear here.") }
        } else {
            items(state.invoices) { inv ->
                val balance = (inv.amountKobo - inv.amountPaidKobo).coerceAtLeast(0L)
                ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(inv.title, style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                text = inv.status.uppercase(),
                                containerColor = if (inv.status == "paid") EmeraldLightContainer else GoldContainer,
                                contentColor = if (inv.status == "paid") EmeraldOnContainer else Color(0xFF78350F)
                            )
                        }
                        Text("Total: ${ExamEngine.formatNairaFromKobo(inv.amountKobo)} • Balance: ${ExamEngine.formatNairaFromKobo(balance)}", style = MaterialTheme.typography.bodyMedium)
                        if (inv.status != "paid") {
                            Button(
                                onClick = { onPayInvoice(inv.id) },
                                modifier = Modifier.fillMaxWidth().testTag("pay_invoice_${inv.id}")
                            ) {
                                Icon(Icons.Default.Payment, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pay Now with Paystack")
                            }
                        }
                    }
                }
            }
        }

        item { Text("Payment History & Receipts", style = MaterialTheme.typography.titleLarge) }
        items(state.payments.filter { it.status == "verified" }) { pay ->
            OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ExamEngine.formatNairaFromKobo(pay.amountKobo), style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                        Text("Ref: ${pay.reference}", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = {
                        val file = PdfGenerator.generatePaymentReceiptPdf(
                            context = context,
                            schoolName = state.schoolSettings?.schoolName ?: "Green Light Academy",
                            studentName = state.account?.profile?.fullName ?: "Student",
                            admissionNumber = state.account?.student?.admissionNumber ?: "-",
                            payment = pay,
                            invoiceTitle = state.invoices.firstOrNull { it.id == pay.invoiceId }?.title ?: "School Fee"
                        )
                        PdfGenerator.shareOrOpenFile(context, file, "application/pdf")
                    }) { Text("Receipt PDF") }
                }
            }
        }

        item { Text("Notifications", style = MaterialTheme.typography.titleLarge) }
        items(state.notifications) { note ->
            ElevatedCard(
                modifier = Modifier.clickable { if (!note.isRead) onMarkNotificationRead(note.id) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (note.isRead) Icons.Default.NotificationsNone else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (note.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(note.title, style = MaterialTheme.typography.titleSmall)
                        Text(note.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
