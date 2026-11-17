package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.config.AppConfig
import com.example.data.model.SchoolClassDto
import com.example.data.model.UserRole
import com.example.ui.components.AcademyHeroBanner
import com.example.ui.components.SetupRequiredBanner
import com.example.ui.components.ThemeToggleButton
import com.example.ui.theme.ThemeMode

@Composable
fun AuthScreen(
    classes: List<SchoolClassDto>,
    themeMode: ThemeMode,
    onToggleTheme: (ThemeMode) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (
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
    ) -> Unit,
    onRecoverPassword: (email: String) -> Unit
) {
    var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var admissionNumber by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("Science") }
    var selectedClassId by remember { mutableStateOf<String?>(classes.firstOrNull()?.id) }
    var staffCode by remember { mutableStateOf("") }
    var childAdmission by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "GLA CBT Logo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "GLA CBT Portal",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                ThemeToggleButton(themeMode = themeMode, onToggle = onToggleTheme)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AcademyHeroBanner(
                    title = "Green Light Academy",
                    subtitle = "Computer-Based Testing, Academic Records, Attendance & Paystack Portal",
                    badgeText = "LIVE SUPABASE PORTAL"
                )

                if (!AppConfig.isSupabaseConfigured) {
                    SetupRequiredBanner(onOpenGuide = { showSetupDialog = true })
                }

                // Role Selector Cards
                Text("Select Portal Role", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val roles = listOf(
                        UserRole.STUDENT to Icons.Default.Person,
                        UserRole.TEACHER to Icons.Default.MenuBook,
                        UserRole.PARENT to Icons.Default.FamilyRestroom,
                        UserRole.ADMIN to Icons.Default.AdminPanelSettings
                    )
                    roles.forEach { (role, icon) ->
                        val active = selectedRole == role
                        FilterChip(
                            selected = active,
                            onClick = { selectedRole = role },
                            label = { Text(role.displayName.take(7), maxLines = 1) },
                            leadingIcon = { Icon(icon, contentDescription = role.displayName, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_chip_${role.dbValue}")
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${selectedRole.displayName} ${if (isRegisterMode) "Registration" else "Sign In"}",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = when (selectedRole) {
                                        UserRole.STUDENT -> "Access CBT exams, results, materials & ID card"
                                        UserRole.TEACHER -> "Build CBT exams, mark theory, record attendance"
                                        UserRole.PARENT -> "Monitor children, report cards & school fees"
                                        UserRole.ADMIN -> "Manage academy operations, approvals & settings"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Auth mode toggle
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !isRegisterMode,
                                onClick = { isRegisterMode = false; localError = null },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                modifier = Modifier.testTag("auth_mode_login")
                            ) { Text("Sign In") }
                            SegmentedButton(
                                selected = isRegisterMode,
                                onClick = { isRegisterMode = true; localError = null },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                modifier = Modifier.testTag("auth_mode_register")
                            ) { Text("Create Account") }
                        }

                        AnimatedVisibility(visible = isRegisterMode) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Full Name") },
                                    leadingIcon = { Icon(Icons.Default.Badge, null) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_fullname_input")
                                )
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("Phone Number (+234...)") },
                                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                when (selectedRole) {
                                    UserRole.STUDENT -> {
                                        OutlinedTextField(
                                            value = admissionNumber,
                                            onValueChange = { admissionNumber = it },
                                            label = { Text("Admission Number (e.g. GLA/2026/001)") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("auth_admission_input")
                                        )
                                        OutlinedTextField(
                                            value = department,
                                            onValueChange = { department = it },
                                            label = { Text("Department (General / Science / Arts)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (classes.isNotEmpty()) {
                                            Text("Select Class", style = MaterialTheme.typography.labelMedium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                classes.take(3).forEach { cls ->
                                                    FilterChip(
                                                        selected = selectedClassId == cls.id,
                                                        onClick = { selectedClassId = cls.id },
                                                        label = { Text(cls.name) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    UserRole.TEACHER -> {
                                        OutlinedTextField(
                                            value = staffCode,
                                            onValueChange = { staffCode = it },
                                            label = { Text("Staff Code (e.g. GLA-TCH-04)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = department,
                                            onValueChange = { department = it },
                                            label = { Text("Teaching Department") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    UserRole.PARENT -> {
                                        OutlinedTextField(
                                            value = childAdmission,
                                            onValueChange = { childAdmission = it },
                                            label = { Text("Child's Admission Number (Optional)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    UserRole.ADMIN -> Unit
                                }
                            }
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        localError?.let { msg ->
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                localError = null
                                if (email.isBlank() || !email.contains("@")) {
                                    localError = "Enter a valid email address."
                                    return@Button
                                }
                                if (password.length < 6) {
                                    localError = "Password must be at least 6 characters."
                                    return@Button
                                }
                                if (isRegisterMode) {
                                    if (fullName.isBlank()) {
                                        localError = "Enter your full name."
                                        return@Button
                                    }
                                    onRegister(
                                        selectedRole,
                                        fullName,
                                        email,
                                        password,
                                        phone,
                                        admissionNumber,
                                        department,
                                        selectedClassId,
                                        staffCode,
                                        childAdmission
                                    )
                                } else {
                                    onSignIn(email, password)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_submit_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = if (isRegisterMode) Icons.Default.PersonAdd else Icons.Default.Login,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRegisterMode) "Create ${selectedRole.displayName} Account" else "Sign In to Portal")
                        }

                        if (!isRegisterMode) {
                            TextButton(
                                onClick = { showForgotDialog = true },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("forgot_password_button")
                            ) {
                                Text("Forgot Password?")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForgotDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset Portal Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your registered Green Light Academy email to receive a password recovery link.")
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (resetEmail.contains("@")) {
                        onRecoverPassword(resetEmail)
                        showForgotDialog = false
                    }
                }) { Text("Send Reset Link") }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = { Text("Configure Supabase & Paystack") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Run supabase/migrations/001_initial_schema.sql in your Supabase SQL Editor.")
                    Text("2. Deploy initialize-paystack-payment & verify-paystack-payment Edge Functions.")
                    Text("3. In the AI Studio Secrets panel, set:\n• SUPABASE_URL\n• SUPABASE_ANON_KEY\n• PAYSTACK_PUBLIC_KEY")
                }
            },
            confirmButton = {
                Button(onClick = { showSetupDialog = false }) { Text("Got it") }
            }
        )
    }
}
