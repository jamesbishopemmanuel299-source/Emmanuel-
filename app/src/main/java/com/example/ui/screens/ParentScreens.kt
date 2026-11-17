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
import com.example.util.ExamEngine
import com.example.util.PdfGenerator

@Composable
fun ParentPortalContent(
    tabIndex: Int,
    state: MainUiState,
    onSelectChild: (Int) -> Unit,
    onLinkChild: (admissionNo: String) -> Unit,
    onPayInvoice: (invoiceId: String) -> Unit,
    onVerifyPaystack: (reference: String) -> Unit,
    onContactTeacher: (teacherProfileId: String, studentId: String?, subject: String, body: String) -> Unit
) {
    when (tabIndex) {
        0 -> ParentOverviewTab(state, onSelectChild, onLinkChild)
        1 -> ParentResultsAttendanceTab(state)
        2 -> ParentFeesContactTab(state, onPayInvoice, onVerifyPaystack, onContactTeacher)
    }
}

@Composable
private fun ParentOverviewTab(
    state: MainUiState,
    onSelectChild: (Int) -> Unit,
    onLinkChild: (String) -> Unit
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    val selectedChild = state.linkedChildren.getOrNull(state.selectedChildIndex)
    val approvedResults = state.results.filter { it.isApproved }
    val avg = if (approvedResults.isNotEmpty()) approvedResults.map { it.totalScore }.average() else 0.0
    val present = state.attendance.count { it.status == "present" }
    val attPct = if (state.attendance.isNotEmpty()) (present * 100 / state.attendance.size) else 100
    val balanceKobo = state.invoices.sumOf { (it.amountKobo - it.amountPaidKobo).coerceAtLeast(0L) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AcademyHeroBanner(
                title = "Parent & Guardian Portal",
                subtitle = "Real-time visibility into academic progress, attendance, and school fees.",
                badgeText = "GREEN LIGHT ACADEMY"
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Linked Children", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { showLinkDialog = true }) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Link Child")
                }
            }
            if (state.linkedChildren.isEmpty()) {
                EmptyStateCard("No Linked Child", "Tap 'Link Child' and enter your ward's Green Light Academy admission number.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.linkedChildren.forEachIndexed { idx, child ->
                        FilterChip(
                            selected = idx == state.selectedChildIndex,
                            onClick = { onSelectChild(idx) },
                            label = { Text(child.profiles?.fullName ?: child.admissionNumber) }
                        )
                    }
                }
            }
        }

        if (selectedChild != null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricCard("Avg Score", "${"%.1f".format(avg)}%", Icons.Default.Grade, EmeraldPrimary, Modifier.weight(1f))
                    KpiMetricCard("Attendance", "$attPct%", Icons.Default.FactCheck, SkyInfo, Modifier.weight(1f))
                    KpiMetricCard("Fee Balance", ExamEngine.formatNairaFromKobo(balanceKobo), Icons.Default.AccountBalanceWallet, GoldAccent, Modifier.weight(1f))
                }
            }
            item {
                BarAnalyticsChart(
                    title = "${selectedChild.profiles?.fullName ?: "Student"}'s Performance",
                    entries = approvedResults.take(6).map { (it.subjects?.code ?: "Subj") to it.totalScore.toFloat() }
                )
            }
        }

        item { Text("School Announcements", style = MaterialTheme.typography.titleMedium) }
        items(state.announcements.take(4)) { ann ->
            ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ann.title, style = MaterialTheme.typography.titleSmall)
                    Text(ann.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showLinkDialog) {
        var adm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Link Student by Admission No") },
            text = {
                OutlinedTextField(
                    value = adm,
                    onValueChange = { adm = it },
                    label = { Text("Admission Number (e.g. GLA/2026/001)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (adm.isNotBlank()) {
                        onLinkChild(adm)
                        showLinkDialog = false
                    }
                }) { Text("Link Child") }
            },
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ParentResultsAttendanceTab(state: MainUiState) {
    val context = LocalContext.current
    val child = state.linkedChildren.getOrNull(state.selectedChildIndex)
    val approved = state.results.filter { it.isApproved }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (child != null && approved.isNotEmpty()) {
                Button(
                    onClick = {
                        val file = PdfGenerator.generateReportCardPdf(
                            context = context,
                            schoolName = state.schoolSettings?.schoolName ?: "Green Light Academy",
                            sessionLabel = state.activeSession?.let { "${it.name} - ${it.term}" } ?: "2026/2027",
                            student = child,
                            results = approved
                        )
                        PdfGenerator.shareOrOpenFile(context, file, "application/pdf")
                    },
                    modifier = Modifier.fillMaxWidth().testTag("parent_download_report_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download Child's Report Card PDF")
                }
            }
        }
        item { Text("Approved Subject Results", style = MaterialTheme.typography.titleMedium) }
        if (approved.isEmpty()) {
            item { EmptyStateCard("No Approved Results", "Results appear here once approved by school administration.") }
        } else {
            items(approved) { res ->
                ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(res.subjects?.name ?: "Subject", style = MaterialTheme.typography.titleSmall)
                            Text("CA ${res.caScore} + Exam ${res.examScore} • Pos ${ExamEngine.formatOrdinal(res.classPosition)}", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusPill("${res.totalScore}% (${res.grade})", EmeraldLightContainer, EmeraldOnContainer)
                    }
                }
            }
        }
        item { Text("Attendance Log", style = MaterialTheme.typography.titleMedium) }
        items(state.attendance.take(15)) { att ->
            OutlinedCard {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(att.date, style = MaterialTheme.typography.bodyMedium)
                    StatusPill(att.status.uppercase(), EmeraldLightContainer, EmeraldOnContainer)
                }
            }
        }
    }
}

@Composable
private fun ParentFeesContactTab(
    state: MainUiState,
    onPayInvoice: (String) -> Unit,
    onVerifyPaystack: (String) -> Unit,
    onContactTeacher: (String, String?, String, String) -> Unit
) {
    var showMsgDialog by remember { mutableStateOf(false) }
    val pendingRef = state.pendingPaystackReference
    val child = state.linkedChildren.getOrNull(state.selectedChildIndex)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!pendingRef.isNullOrBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = EmeraldLightContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paystack Reference: $pendingRef", style = MaterialTheme.typography.labelSmall)
                        Button(onClick = { onVerifyPaystack(pendingRef) }) { Text("Verify Payment") }
                    }
                }
            }
        }

        item { Text("Fee Invoices", style = MaterialTheme.typography.titleMedium) }
        if (state.invoices.isEmpty()) {
            item { EmptyStateCard("No Invoices", "Fee balances for linked children appear here.") }
        } else {
            items(state.invoices) { inv ->
                val bal = (inv.amountKobo - inv.amountPaidKobo).coerceAtLeast(0L)
                ElevatedCard(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(inv.title, style = MaterialTheme.typography.titleSmall)
                            StatusPill(inv.status.uppercase(), if (inv.status == "paid") EmeraldLightContainer else GoldContainer, Color(0xFF1E293B))
                        }
                        Text("Balance: ${ExamEngine.formatNairaFromKobo(bal)} • Due: ${inv.dueDate}", style = MaterialTheme.typography.bodySmall)
                        if (inv.status != "paid") {
                            Button(onClick = { onPayInvoice(inv.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Pay via Paystack")
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Teacher Communication", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showMsgDialog = true }) { Text("Contact Teacher") }
            }
        }
        items(state.parentMessages) { msg ->
            OutlinedCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(msg.subject, style = MaterialTheme.typography.titleSmall)
                    Text(msg.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showMsgDialog) {
        var selectedTeacherProfileId by remember { mutableStateOf(state.teachers.firstOrNull()?.profileId.orEmpty()) }
        var subject by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMsgDialog = false },
            title = { Text("Contact Class / Subject Teacher") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.teachers.take(3).forEach { t ->
                            FilterChip(
                                selected = selectedTeacherProfileId == t.profileId,
                                onClick = { selectedTeacherProfileId = t.profileId },
                                label = { Text(t.profiles?.fullName ?: t.staffCode) }
                            )
                        }
                    }
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") })
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (selectedTeacherProfileId.isNotBlank() && subject.isNotBlank() && body.isNotBlank()) {
                        onContactTeacher(selectedTeacherProfileId, child?.id, subject, body)
                        showMsgDialog = false
                    }
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { showMsgDialog = false }) { Text("Cancel") } }
        )
    }
}
