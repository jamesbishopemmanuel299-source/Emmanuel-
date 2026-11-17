package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserRole
import com.example.ui.components.LoadingOverlay
import com.example.ui.components.ThemeToggleButton
import com.example.ui.screens.*
import com.example.ui.theme.GLACBTTheme
import com.example.ui.viewmodel.MainViewModel

private data class NavTab(
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GLACBTApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Launch Paystack checkout URL in browser when initialized from Edge Function
    LaunchedEffect(state.pendingPaystackUrl) {
        val url = state.pendingPaystackUrl
        if (!url.isNullOrBlank()) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            viewModel.clearPendingPaystackUrl()
        }
    }

    LaunchedEffect(state.statusMessage, state.errorMessage) {
        val msg = state.errorMessage ?: state.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    GLACBTTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitializing -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Loading Green Light Academy...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                state.activeExam != null -> {
                    ExamRunnerScreen(
                        examState = state.activeExam!!,
                        onSelectIndex = viewModel::selectExamQuestionIndex,
                        onSelectOption = viewModel::selectObjectiveOption,
                        onUpdateText = viewModel::updateTextAnswer,
                        onToggleReview = viewModel::toggleMarkForReview,
                        onFocusLost = viewModel::onExamFocusLost,
                        onDismissWarning = viewModel::dismissTabWarning,
                        onSubmitExam = { viewModel.submitActiveExam() },
                        onCloseSummary = viewModel::closeExamSummary
                    )
                }

                state.account == null -> {
                    AuthScreen(
                        classes = state.classes,
                        themeMode = themeMode,
                        onToggleTheme = viewModel::setThemeMode,
                        onSignIn = viewModel::signIn,
                        onRegister = viewModel::register,
                        onRecoverPassword = viewModel::recoverPassword
                    )
                }

                else -> {
                    val account = state.account!!
                    val role = UserRole.fromDb(account.profile.role)
                    var selectedTab by remember(role) { mutableIntStateOf(0) }

                    BackHandler(enabled = selectedTab != 0) {
                        selectedTab = 0
                    }

                    val tabs = remember(role) {
                        when (role) {
                            UserRole.STUDENT -> listOf(
                                NavTab("Home", Icons.Default.Home),
                                NavTab("CBT Exams", Icons.Default.Quiz),
                                NavTab("Academics", Icons.Default.School),
                                NavTab("Fees", Icons.Default.Payments)
                            )
                            UserRole.TEACHER -> listOf(
                                NavTab("Home", Icons.Default.Home),
                                NavTab("Exams", Icons.Default.Quiz),
                                NavTab("Classroom", Icons.Default.MenuBook)
                            )
                            UserRole.PARENT -> listOf(
                                NavTab("Home", Icons.Default.Home),
                                NavTab("Reports", Icons.Default.Assessment),
                                NavTab("Fees & Chat", Icons.Default.Payments)
                            )
                            UserRole.ADMIN -> listOf(
                                NavTab("Overview", Icons.Default.Dashboard),
                                NavTab("People", Icons.Default.People),
                                NavTab("Academics", Icons.Default.School),
                                NavTab("Finance", Icons.Default.AccountBalance)
                            )
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useRail = maxWidth >= 600.dp

                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Column {
                                            Text(
                                                text = state.schoolSettings?.schoolName ?: "GLA CBT",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "${role.displayName} Portal",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = viewModel::refreshPortalData,
                                            modifier = Modifier.testTag("refresh_portal_button")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Refresh portal")
                                        }
                                        ThemeToggleButton(themeMode = themeMode, onToggle = viewModel::setThemeMode)
                                        IconButton(
                                            onClick = viewModel::signOut,
                                            modifier = Modifier.testTag("sign_out_button")
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                                        }
                                    }
                                )
                            },
                            bottomBar = {
                                if (!useRail) {
                                    NavigationBar {
                                        tabs.forEachIndexed { idx, tab ->
                                            NavigationBarItem(
                                                selected = selectedTab == idx,
                                                onClick = { selectedTab = idx },
                                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                                label = { Text(tab.label) },
                                                modifier = Modifier.testTag("nav_tab_$idx")
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (useRail) {
                                    NavigationRail {
                                        tabs.forEachIndexed { idx, tab ->
                                            NavigationRailItem(
                                                selected = selectedTab == idx,
                                                onClick = { selectedTab = idx },
                                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                                label = { Text(tab.label) },
                                                modifier = Modifier.testTag("rail_tab_$idx")
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(modifier = Modifier.widthIn(max = 960.dp).fillMaxSize()) {
                                        when (role) {
                                            UserRole.STUDENT -> StudentPortalContent(
                                                tabIndex = selectedTab,
                                                state = state,
                                                onUploadPassport = viewModel::uploadStudentPassport,
                                                onStartExam = viewModel::startOrResumeExam,
                                                onSubmitAssignment = viewModel::submitAssignment,
                                                onPayInvoice = viewModel::startPaystackPayment,
                                                onVerifyPaystack = viewModel::verifyPaystackPayment,
                                                onMarkNotificationRead = viewModel::markNotificationRead
                                            )
                                            UserRole.TEACHER -> TeacherPortalContent(
                                                tabIndex = selectedTab,
                                                state = state,
                                                onCreateExam = viewModel::createExam,
                                                onAddQuestion = viewModel::addManualQuestion,
                                                onImportQuestionsCsv = viewModel::importQuestionsFromCsv,
                                                onFetchSubmissions = viewModel::fetchExamAttemptsWithAnswers,
                                                onFetchAnswers = viewModel::fetchAnswersForAttempt,
                                                onGradeTheory = viewModel::gradeTheoryAnswer,
                                                onPublishExamResults = viewModel::publishExamResults,
                                                onRecordAttendance = viewModel::recordClassAttendance,
                                                onSaveResult = viewModel::saveStudentSubjectResult,
                                                onCreateAssignment = viewModel::createAssignment,
                                                onUploadMaterial = viewModel::uploadLearningMaterial
                                            )
                                            UserRole.PARENT -> ParentPortalContent(
                                                tabIndex = selectedTab,
                                                state = state,
                                                onSelectChild = viewModel::selectParentChild,
                                                onLinkChild = viewModel::linkChildByAdmission,
                                                onPayInvoice = viewModel::startPaystackPayment,
                                                onVerifyPaystack = viewModel::verifyPaystackPayment,
                                                onContactTeacher = viewModel::sendParentTeacherMessage
                                            )
                                            UserRole.ADMIN -> AdminPortalContent(
                                                tabIndex = selectedTab,
                                                state = state,
                                                onToggleAccountActive = viewModel::toggleAccountActive,
                                                onCreateClass = viewModel::createClass,
                                                onCreateSubject = viewModel::createSubject,
                                                onCreateSession = viewModel::createSession,
                                                onApproveResult = viewModel::approveResult,
                                                onCreateInvoice = viewModel::createInvoice,
                                                onCreateAnnouncement = viewModel::createAnnouncement,
                                                onUpdateSettings = viewModel::updateSchoolSettings,
                                                onExportBackupJson = viewModel::exportBackupJson
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Global snackbar overlay for AuthScreen errors
            if (state.account == null && state.activeExam == null) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp)
                )
            }

            LoadingOverlay(visible = state.isLoading)
        }
    }
}
