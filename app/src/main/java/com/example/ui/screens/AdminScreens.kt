package com.example.ui.screens

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
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.util.CsvExcelHelper
import com.example.util.ExamEngine
import com.example.util.PdfGenerator
import java.time.LocalDate

@Composable
fun AdminPortalContent(
    tabIndex: Int,
    state: MainUiState,
    onToggleAccountActive: (profileId: String, active: Boolean) -> Unit,
    onCreateClass: (name: String, level: String, department: String) -> Unit,
    onCreateSubject: (code: String, name: String, department: String, classId: String?, teacherId: String?) -> Unit,
    onCreateSession: (name: String, term: String, startDate: String, endDate: String) -> Unit,
    onApproveResult: (resultId: String) -> Unit,
    onCreateInvoice: (studentId: String, title: String, feeType: String, amountNaira: Double, dueDate: String) -> Unit,
    onCreateAnnouncement: (title: String, body: String, targetRole: String, priority: String) -> Unit,
    onUpdateSettings: (String, String, Int, Double, Int, Boolean, Boolean, String?) -> Unit,
    onExportBackupJson: () -> Unit
) {
    when (tabIndex) {
        0 -> AdminDashboardTab(state)
        1 -> AdminPeopleTab(state, onToggleAccountActive)
        2 -> AdminAcademicsTab(state, onCreateClass, onCreateSubject, onCreateSession, onApproveResult)
        3 -> AdminFinanceSystemTab(state, onCreateInvoice, onCreateAnnouncement, onUpdateSettings, onExportBackupJson)
    }
}

@Composable
private fun AdminDashboardTab(state: MainUiState) {
    val totalRevenueKobo = state.payments.filter { it.status == "verified" }.sumOf { it.amountKobo }
    val activeExams = state.examinations.count { it.isPublished }
    val classPerformance = state.classes.take(6).map { cls ->
        val scores = state.results.filter { it.classId == cls.id }.map { it.totalScore }
        val avg = if (scores.isNotEmpty()) scores.average().toFloat() else 0f
        cls.name to avg
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AcademyHeroBanner(
                title = state.schoolSettings?.schoolName ?: "Green Light Academy",
                subtitle = "Session: ${state.activeSession?.let { "${it.name} (${it.term})" } ?: "Not Set"} • School Revenue: ${ExamEngine.formatNairaFromKobo(totalRevenueKobo)}",
                badgeText = "ADMIN COMMAND CENTER"
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard("Students", "${state.students.size}", Icons.Default.Groups, EmeraldPrimary, Modifier.weight(1f))
                KpiMetricCard("Teachers", "${state.teachers.size}", Icons.Default.School, SkyInfo, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard("Classes", "${state.classes.size}", Icons.Default.MeetingRoom, GoldAccent, Modifier.weight(1f))
                KpiMetricCard("Active Exams", "$activeExams", Icons.Default.Quiz, EmeraldPrimary, Modifier.weight(1f))
            }
        }
        item {
            BarAnalyticsChart(
                title = "Class Performance Analytics (Avg %)",
                entries = classPerformance
            )
        }
        item { Text("Recent Audit Logs", style = MaterialTheme.typography.titleMedium) }
        items(state.auditLogs.take(8)) { log ->
            OutlinedCard(shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(log.action, style = MaterialTheme.typography.titleSmall)
                        Text("${log.entityType} • ${log.entityId ?: "-"}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(log.createdAt?.take(10).orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AdminPeopleTab(
    state: MainUiState,
    onToggleAccountActive: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Student Directory & ID Cards", style = MaterialTheme.typography.titleLarge) }
        items(state.students) { st ->
            ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(st.profiles?.fullName ?: "Student", style = MaterialTheme.typography.titleSmall)
                        Text("${st.admissionNumber} • ${st.classes?.name ?: "Unassigned"}", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = {
                        val file = PdfGenerator.generateStudentIdCardPdf(
                            context,
                            state.schoolSettings?.schoolName ?: "Green Light Academy",
                            state.activeSession?.name ?: "2026/2027",
                            st
                        )
                        PdfGenerator.shareOrOpenFile(context, file, "application/pdf")
                    }) { Text("ID Card") }
                }
            }
        }

        item { Text("User Account Activation Controls", style = MaterialTheme.typography.titleLarge) }
        items(state.profiles) { prof ->
            OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(prof.fullName, style = MaterialTheme.typography.titleSmall)
                        Text("${prof.role.uppercase()} • ${prof.email}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = prof.isActive,
                        onCheckedChange = { onToggleAccountActive(prof.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminAcademicsTab(
    state: MainUiState,
    onCreateClass: (String, String, String) -> Unit,
    onCreateSubject: (String, String, String, String?, String?) -> Unit,
    onCreateSession: (String, String, String, String) -> Unit,
    onApproveResult: (String) -> Unit
) {
    val context = LocalContext.current
    var showClassDialog by remember { mutableStateOf(false) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var showSessionDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showSessionDialog = true }, modifier = Modifier.weight(1f)) { Text("+ Session") }
                Button(onClick = { showClassDialog = true }, modifier = Modifier.weight(1f)) { Text("+ Class") }
                Button(onClick = { showSubjectDialog = true }, modifier = Modifier.weight(1f)) { Text("+ Subject") }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Result Approvals (${state.results.count { !it.isApproved }} pending)", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = {
                    val csv = CsvExcelHelper.exportResultsCsv(state.results)
                    val file = PdfGenerator.saveTextExport(context, "GLA_Results_Report.csv", csv)
                    PdfGenerator.shareOrOpenFile(context, file, "text/csv")
                }) { Text("Export CSV") }
            }
        }
        items(state.results) { res ->
            ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${res.students?.profiles?.fullName ?: "Student"} — ${res.subjects?.name ?: "Subject"}", style = MaterialTheme.typography.titleSmall)
                        Text("Total: ${res.totalScore}% (${res.grade}) • Pos: ${ExamEngine.formatOrdinal(res.classPosition)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (res.isApproved) {
                        StatusPill("APPROVED", EmeraldLightContainer, EmeraldOnContainer)
                    } else {
                        Button(onClick = { onApproveResult(res.id) }) { Text("Approve") }
                    }
                }
            }
        }
    }

    if (showClassDialog) {
        var name by remember { mutableStateOf("SSS 1A") }
        var level by remember { mutableStateOf("SSS 1") }
        var dept by remember { mutableStateOf("Science") }
        AlertDialog(
            onDismissRequest = { showClassDialog = false },
            title = { Text("Create Class") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Class Name") })
                    OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Level") })
                    OutlinedTextField(value = dept, onValueChange = { dept = it }, label = { Text("Department") })
                }
            },
            confirmButton = {
                Button(onClick = { onCreateClass(name, level, dept); showClassDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showClassDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSubjectDialog) {
        var code by remember { mutableStateOf("MTH-101") }
        var name by remember { mutableStateOf("Mathematics") }
        var dept by remember { mutableStateOf("General") }
        AlertDialog(
            onDismissRequest = { showSubjectDialog = false },
            title = { Text("Create Subject") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Subject Code") })
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Subject Name") })
                    OutlinedTextField(value = dept, onValueChange = { dept = it }, label = { Text("Department") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateSubject(code, name, dept, state.classes.firstOrNull()?.id, state.teachers.firstOrNull()?.id)
                    showSubjectDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSubjectDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSessionDialog) {
        var name by remember { mutableStateOf("2026/2027") }
        var term by remember { mutableStateOf("First Term") }
        val start = remember { LocalDate.now().toString() }
        val end = remember { LocalDate.now().plusMonths(3).toString() }
        AlertDialog(
            onDismissRequest = { showSessionDialog = false },
            title = { Text("Create Academic Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Session e.g. 2026/2027") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("First Term", "Second Term", "Third Term").forEach { t ->
                            FilterChip(selected = term == t, onClick = { term = t }, label = { Text(t.take(6)) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onCreateSession(name, term, start, end); showSessionDialog = false }) { Text("Activate") }
            },
            dismissButton = { TextButton(onClick = { showSessionDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminFinanceSystemTab(
    state: MainUiState,
    onCreateInvoice: (String, String, String, Double, String) -> Unit,
    onCreateAnnouncement: (String, String, String, String) -> Unit,
    onUpdateSettings: (String, String, Int, Double, Int, Boolean, Boolean, String?) -> Unit,
    onExportBackupJson: () -> Unit
) {
    val context = LocalContext.current
    var showInvoiceDialog by remember { mutableStateOf(false) }
    var showAnnDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showInvoiceDialog = true }, modifier = Modifier.weight(1f)) { Text("+ Invoice") }
                Button(onClick = { showAnnDialog = true }, modifier = Modifier.weight(1f)) { Text("+ Notice") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { showSettingsDialog = true }, modifier = Modifier.weight(1f).testTag("admin_settings_button")) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(6.dp))
                    Text("School & Exam Rules")
                }
                OutlinedButton(onClick = onExportBackupJson, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Backup, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Backup JSON")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Fee Invoices & Paystack Report", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = {
                    val csv = CsvExcelHelper.exportInvoicesCsv(state.invoices)
                    val file = PdfGenerator.saveTextExport(context, "GLA_Invoices_Report.csv", csv)
                    PdfGenerator.shareOrOpenFile(context, file, "text/csv")
                }) { Text("Export CSV") }
            }
        }
        items(state.invoices) { inv ->
            OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(inv.title, style = MaterialTheme.typography.titleSmall)
                        Text("${inv.students?.profiles?.fullName ?: "Student"} • ${ExamEngine.formatNairaFromKobo(inv.amountKobo)}", style = MaterialTheme.typography.bodySmall)
                    }
                    StatusPill(inv.status.uppercase(), if (inv.status == "paid") EmeraldLightContainer else GoldContainer, Color(0xFF1E293B))
                }
            }
        }
    }

    if (showInvoiceDialog) {
        var selectedStudentId by remember { mutableStateOf(state.students.firstOrNull()?.id.orEmpty()) }
        var title by remember { mutableStateOf("First Term Tuition Fee") }
        var amount by remember { mutableStateOf("75000") }
        val due = remember { LocalDate.now().plusDays(14).toString() }
        AlertDialog(
            onDismissRequest = { showInvoiceDialog = false },
            title = { Text("Issue School Fee Invoice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.students.take(3).forEach { st ->
                            FilterChip(selected = selectedStudentId == st.id, onClick = { selectedStudentId = st.id }, label = { Text(st.admissionNumber) })
                        }
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Fee Title") })
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (NGN)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (selectedStudentId.isNotBlank()) {
                        onCreateInvoice(selectedStudentId, title, "school_fee", amount.toDoubleOrNull() ?: 50000.0, due)
                        showInvoiceDialog = false
                    }
                }) { Text("Issue Invoice") }
            },
            dismissButton = { TextButton(onClick = { showInvoiceDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAnnDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var priority by remember { mutableStateOf("normal") }
        AlertDialog(
            onDismissRequest = { showAnnDialog = false },
            title = { Text("Publish School Announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Headline") })
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, minLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = priority == "normal", onClick = { priority = "normal" }, label = { Text("Normal") })
                        FilterChip(selected = priority == "urgent", onClick = { priority = "urgent" }, label = { Text("Urgent") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && body.isNotBlank()) {
                        onCreateAnnouncement(title, body, "all", priority)
                        showAnnDialog = false
                    }
                }) { Text("Broadcast") }
            },
            dismissButton = { TextButton(onClick = { showAnnDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSettingsDialog) {
        val current = state.schoolSettings
        var schoolName by remember { mutableStateOf(current?.schoolName ?: "Green Light Academy") }
        var motto by remember { mutableStateOf(current?.motto ?: "Excellence Through Knowledge & Integrity") }
        var duration by remember { mutableStateOf((current?.defaultExamDurationMinutes ?: 60).toString()) }
        var passMark by remember { mutableStateOf((current?.defaultPassMark ?: 50.0).toString()) }
        var maxTabs by remember { mutableStateOf((current?.maxTabSwitches ?: 3).toString()) }
        var instant by remember { mutableStateOf(current?.allowInstantResults ?: true) }
        var resume by remember { mutableStateOf(current?.allowExamResume ?: true) }
        var subaccount by remember { mutableStateOf(current?.paystackSubaccountCode.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("System & CBT Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = schoolName, onValueChange = { schoolName = it }, label = { Text("School Name") })
                    OutlinedTextField(value = motto, onValueChange = { motto = it }, label = { Text("School Motto") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Default Mins") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = maxTabs, onValueChange = { maxTabs = it }, label = { Text("Max Tab Warn") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = passMark, onValueChange = { passMark = it }, label = { Text("Default Pass Mark %") })
                    OutlinedTextField(value = subaccount, onValueChange = { subaccount = it }, label = { Text("Paystack Subaccount Code (Optional)") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = instant, onCheckedChange = { instant = it })
                        Text("Instant Results", style = MaterialTheme.typography.bodySmall)
                        Checkbox(checked = resume, onCheckedChange = { resume = it })
                        Text("Allow Resume", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateSettings(
                        schoolName, motto, duration.toIntOrNull() ?: 60,
                        passMark.toDoubleOrNull() ?: 50.0, maxTabs.toIntOrNull() ?: 3,
                        instant, resume, subaccount
                    )
                    showSettingsDialog = false
                }) { Text("Save Settings") }
            },
            dismissButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") } }
        )
    }
}
