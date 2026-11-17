package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.data.model.QuestionType
import com.example.ui.theme.*
import com.example.ui.viewmodel.ActiveExamSessionState
import com.example.util.ExamEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamRunnerScreen(
    examState: ActiveExamSessionState,
    onSelectIndex: (Int) -> Unit,
    onSelectOption: (questionId: String, optionId: String) -> Unit,
    onUpdateText: (questionId: String, text: String) -> Unit,
    onToggleReview: (questionId: String) -> Unit,
    onFocusLost: () -> Unit,
    onDismissWarning: () -> Unit,
    onSubmitExam: () -> Unit,
    onCloseSummary: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showNavSheet by remember { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }

    // Immersive full-screen examination mode
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Anti-cheat lifecycle observer for app/tab switching
    DisposableEffect(lifecycleOwner, examState.submittedAttempt) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && examState.submittedAttempt == null) {
                onFocusLost()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = examState.submittedAttempt == null) {
        showSubmitConfirm = true
    }

    val submitted = examState.submittedAttempt
    if (submitted != null) {
        // Post-submission view (instant results if enabled)
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    modifier = Modifier.widthIn(max = 520.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(EmeraldLightContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Submitted", tint = EmeraldPrimary, modifier = Modifier.size(40.dp))
                        }
                        Text("Examination Submitted", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = examState.exam.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (examState.exam.instantResultsEnabled) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Objective Score", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${submitted.objectiveScore}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Total Score", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${submitted.totalScore}",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Status", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = submitted.status.uppercase(),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            if (submitted.status == "submitted") {
                                Text(
                                    text = "Theory answers are queued for manual marking by your teacher.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldAccent
                                )
                            }
                        } else {
                            Text(
                                text = "Instant results are disabled for this examination. Results will appear after approval.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onCloseSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("exam_done_button")
                        ) {
                            Text("Return to Student Dashboard")
                        }
                    }
                }
            }
        }
        return
    }

    val currentQuestion = examState.questions.getOrNull(examState.currentIndex) ?: return
    val qType = QuestionType.fromDb(currentQuestion.questionType)
    val isMarked = currentQuestion.id in examState.markedForReview
    val timerLow = examState.remainingSeconds <= 300

    Scaffold(
        topBar = {
            Surface(color = NavyDeep, contentColor = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = examState.exam.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "Question ${examState.currentIndex + 1} of ${examState.questions.size} • Warnings: ${examState.tabSwitchWarnings}/${examState.exam.maxTabSwitches}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = if (timerLow) CrimsonError else EmeraldPrimary,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = "Timer", tint = Color.White, modifier = Modifier.size(16.dp))
                                Text(
                                    text = ExamEngine.formatCountdown(examState.remainingSeconds),
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = Color.White,
                                    modifier = Modifier.testTag("exam_countdown_timer")
                                )
                            }
                        }

                        IconButton(
                            onClick = { showNavSheet = true },
                            modifier = Modifier.testTag("exam_grid_button")
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = "Question Navigator", tint = Color.White)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onSelectIndex((examState.currentIndex - 1).coerceAtLeast(0)) },
                        enabled = examState.currentIndex > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("exam_prev_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Prev")
                    }

                    OutlinedButton(
                        onClick = { onToggleReview(currentQuestion.id) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isMarked) GoldContainer else Color.Transparent
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("exam_review_button")
                    ) {
                        Icon(
                            imageVector = if (isMarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Mark for review",
                            tint = if (isMarked) GoldAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (examState.currentIndex < examState.questions.lastIndex) {
                        Button(
                            onClick = { onSelectIndex(examState.currentIndex + 1) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("exam_next_button")
                        ) {
                            Text("Next")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    } else {
                        Button(
                            onClick = { showSubmitConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("exam_submit_button")
                        ) {
                            Icon(Icons.Default.DoneAll, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Submit")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${qType.label} • ${currentQuestion.marks} marks") }
                )
                Text(
                    text = "Auto-saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = currentQuestion.prompt,
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (!currentQuestion.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentQuestion.imageUrl,
                            contentDescription = "Question diagram",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            when (qType) {
                QuestionType.MCQ -> {
                    val selectedId = examState.selectedOptionIds[currentQuestion.id]
                    val labels = listOf("A", "B", "C", "D", "E", "F")
                    currentQuestion.answerOptions.forEachIndexed { idx, opt ->
                        val isSelected = selectedId == opt.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectOption(currentQuestion.id, opt.id) }
                                .testTag("mcq_option_$idx"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = labels.getOrElse(idx) { "${idx + 1}" },
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = opt.optionText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                QuestionType.TRUE_FALSE -> {
                    val currentText = examState.textResponses[currentQuestion.id].orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("True", "False").forEach { choice ->
                            val isSelected = currentText.equals(choice, ignoreCase = true)
                            ElevatedButton(
                                onClick = { onUpdateText(currentQuestion.id, choice.lowercase()) },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("tf_option_${choice.lowercase()}")
                            ) {
                                Text(choice, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                QuestionType.FILL_GAP -> {
                    val currentText = examState.textResponses[currentQuestion.id].orEmpty()
                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { onUpdateText(currentQuestion.id, it) },
                        label = { Text("Type your exact answer") },
                        placeholder = { Text("Enter missing word or value...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fill_gap_input")
                    )
                }

                QuestionType.THEORY -> {
                    val currentText = examState.textResponses[currentQuestion.id].orEmpty()
                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { onUpdateText(currentQuestion.id, it) },
                        label = { Text("Theory / Essay Response") },
                        placeholder = { Text("Write a clear, structured response...") },
                        minLines = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("theory_answer_input")
                    )
                }
            }
        }
    }

    if (showNavSheet) {
        ModalBottomSheet(onDismissRequest = { showNavSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Question Navigator", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("• Green = Answered", style = MaterialTheme.typography.bodySmall, color = EmeraldPrimary)
                    Text("• Amber = Review", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(52.dp),
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(examState.questions) { idx, q ->
                        val answered = examState.selectedOptionIds.containsKey(q.id) ||
                            !examState.textResponses[q.id].isNullOrBlank()
                        val marked = q.id in examState.markedForReview
                        val isCurrent = idx == examState.currentIndex

                        val bg = when {
                            isCurrent -> NavyDeep
                            marked -> GoldContainer
                            answered -> EmeraldLightContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val fg = when {
                            isCurrent -> Color.White
                            marked -> Color(0xFF78350F)
                            answered -> EmeraldOnContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectIndex(idx)
                                    showNavSheet = false
                                },
                            color = bg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${idx + 1}", color = fg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Button(
                    onClick = { showNavSheet = false; showSubmitConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finish & Submit Examination")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (examState.showTabWarningModal) {
        AlertDialog(
            onDismissRequest = onDismissWarning,
            icon = { Icon(Icons.Default.Security, null, tint = CrimsonError) },
            title = { Text("Anti-Cheat Warning (${examState.tabSwitchWarnings}/${examState.exam.maxTabSwitches})") },
            text = {
                Text("Leaving the examination screen or switching apps has been logged to the academy audit trail. Reaching ${examState.exam.maxTabSwitches} warnings automatically submits your paper.")
            },
            confirmButton = {
                Button(onClick = onDismissWarning) { Text("Return to Exam") }
            }
        )
    }

    if (showSubmitConfirm) {
        val answeredCount = examState.questions.count { q ->
            examState.selectedOptionIds.containsKey(q.id) || !examState.textResponses[q.id].isNullOrBlank()
        }
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Submit Examination?") },
            text = {
                Text("You have answered $answeredCount of ${examState.questions.size} questions. Once submitted, answers cannot be edited.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirm = false
                        onSubmitExam()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) { Text("Confirm Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) { Text("Keep Working") }
            }
        )
    }
}
