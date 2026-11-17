package com.example.data.repository

import com.example.config.AppConfig
import com.example.data.local.SessionPreferences
import com.example.data.model.*
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseErrorParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuthenticatedAccount(
    val profile: ProfileDto,
    val student: StudentDto? = null,
    val teacher: TeacherDto? = null,
    val parent: ParentDto? = null
)

class AuthRepository(
    private val prefs: SessionPreferences,
    private val client: SupabaseClient,
    private val onTokenChanged: (String?) -> Unit
) {

    suspend fun restoreSession(): Result<AuthenticatedAccount?> = withContext(Dispatchers.IO) {
        if (!AppConfig.isSupabaseConfigured) return@withContext Result.success(null)
        val stored = prefs.getSession()
        if (!stored.isLoggedIn) return@withContext Result.success(null)

        onTokenChanged(stored.accessToken)
        var profileRes = client.api.getProfiles(idFilter = "eq.${stored.userId}")
        if (profileRes.code() == 401 && stored.refreshToken.isNotBlank()) {
            val refreshRes = client.api.refreshSession(AuthRefreshRequest(stored.refreshToken))
            val body = refreshRes.body()
            if (refreshRes.isSuccessful && body?.accessToken != null && body.user != null) {
                prefs.saveSession(
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken ?: stored.refreshToken,
                    userId = body.user.id,
                    email = body.user.email ?: stored.email
                )
                onTokenChanged(body.accessToken)
                profileRes = client.api.getProfiles(idFilter = "eq.${body.user.id}")
            } else {
                prefs.clearSession()
                onTokenChanged(null)
                return@withContext Result.success(null)
            }
        }

        val profile = profileRes.body()?.firstOrNull()
        if (profile == null) {
            prefs.clearSession()
            onTokenChanged(null)
            return@withContext Result.success(null)
        }
        if (!profile.isActive) {
            prefs.clearSession()
            onTokenChanged(null)
            return@withContext Result.failure(IllegalStateException("Account is deactivated. Contact Green Light Academy administration."))
        }

        Result.success(loadRoleRecord(profile))
    }

    suspend fun signIn(email: String, password: String): Result<AuthenticatedAccount> = withContext(Dispatchers.IO) {
        if (!AppConfig.isSupabaseConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets."))
        }
        return@withContext try {
            val response = client.api.signIn(AuthSignInRequest(email.trim(), password))
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException(SupabaseErrorParser.extractMessage(response, "Sign in failed")))
            }
            val session = response.body() ?: return@withContext Result.failure(IllegalStateException("Empty auth response"))
            val accessToken = session.accessToken ?: return@withContext Result.failure(IllegalStateException("Missing access token"))
            val userId = session.user?.id ?: return@withContext Result.failure(IllegalStateException("Missing user ID"))

            prefs.saveSession(
                accessToken = accessToken,
                refreshToken = session.refreshToken.orEmpty(),
                userId = userId,
                email = session.user.email ?: email.trim()
            )
            onTokenChanged(accessToken)

            val profile = client.api.getProfiles(idFilter = "eq.$userId").body()?.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Profile not found for user. Ensure SQL migrations are applied."))

            if (!profile.isActive) {
                prefs.clearSession()
                onTokenChanged(null)
                return@withContext Result.failure(IllegalStateException("Your account has been deactivated by the school administrator."))
            }

            client.api.insertAuditLog(
                AuditLogInsertDto(
                    actorId = profile.id,
                    action = "USER_SIGN_IN",
                    entityType = "profile",
                    entityId = profile.id,
                    metadata = mapOf("role" to profile.role)
                )
            )

            Result.success(loadRoleRecord(profile))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        role: UserRole,
        fullName: String,
        email: String,
        password: String,
        phone: String,
        admissionNumber: String = "",
        department: String = "General",
        classId: String? = null,
        staffCode: String = "",
        childAdmissionNumber: String = ""
    ): Result<AuthenticatedAccount?> = withContext(Dispatchers.IO) {
        if (!AppConfig.isSupabaseConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets."))
        }
        return@withContext try {
            val signUpRes = client.api.signUp(
                AuthSignUpRequest(
                    email = email.trim(),
                    password = password,
                    data = mapOf("full_name" to fullName.trim(), "role" to role.dbValue)
                )
            )
            if (!signUpRes.isSuccessful) {
                return@withContext Result.failure(IllegalStateException(SupabaseErrorParser.extractMessage(signUpRes, "Registration failed")))
            }
            val body = signUpRes.body()
            val userId = body?.user?.id ?: body?.id
                ?: return@withContext Result.failure(IllegalStateException("Registration succeeded, please confirm your email to sign in."))

            val token = body?.accessToken
            if (token.isNullOrBlank()) {
                return@withContext Result.success(null) // Email confirmation required by Supabase project settings
            }

            prefs.saveSession(token, body.refreshToken.orEmpty(), userId, email.trim())
            onTokenChanged(token)

            val profileRes = client.api.insertProfile(
                ProfileInsertDto(
                    id = userId,
                    email = email.trim(),
                    fullName = fullName.trim(),
                    phone = phone.trim().ifBlank { null },
                    role = role.dbValue,
                    isActive = true
                )
            )
            val profile = profileRes.body()?.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException(SupabaseErrorParser.extractMessage(profileRes, "Profile creation failed")))

            when (role) {
                UserRole.STUDENT -> {
                    val adm = admissionNumber.trim().ifBlank { "GLA-${System.currentTimeMillis().toString().takeLast(5)}" }
                    client.api.createStudent(
                        StudentInsertDto(
                            profileId = userId,
                            admissionNumber = adm,
                            classId = classId,
                            department = department.ifBlank { "General" },
                            guardianContact = phone.trim().ifBlank { null }
                        )
                    )
                }
                UserRole.TEACHER -> {
                    val code = staffCode.trim().ifBlank { "TCH-${System.currentTimeMillis().toString().takeLast(4)}" }
                    client.api.createTeacher(
                        TeacherInsertDto(
                            profileId = userId,
                            staffCode = code,
                            department = department.ifBlank { "General" }
                        )
                    )
                }
                UserRole.PARENT -> {
                    val parentCreated = client.api.createParent(ParentInsertDto(profileId = userId)).body()?.firstOrNull()
                    if (parentCreated != null && childAdmissionNumber.isNotBlank()) {
                        val matchedStudent = client.api.getStudents(admFilter = "eq.${childAdmissionNumber.trim()}").body()?.firstOrNull()
                        if (matchedStudent != null) {
                            client.api.createParentLink(
                                ParentStudentLinkInsertDto(
                                    parentId = parentCreated.id,
                                    studentId = matchedStudent.id
                                )
                            )
                        }
                    }
                }
                UserRole.ADMIN -> Unit
            }

            Result.success(loadRoleRecord(profile))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recoverPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!AppConfig.isSupabaseConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        }
        return@withContext try {
            val res = client.api.recoverPassword(AuthRecoverRequest(email.trim()))
            if (res.isSuccessful) Result.success(Unit)
            else Result.failure(IllegalStateException(SupabaseErrorParser.extractMessage(res, "Password reset failed")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            if (AppConfig.isSupabaseConfigured) client.api.logout()
        } catch (_: Exception) {
        }
        prefs.clearSession()
        onTokenChanged(null)
    }

    private suspend fun loadRoleRecord(profile: ProfileDto): AuthenticatedAccount {
        return when (UserRole.fromDb(profile.role)) {
            UserRole.STUDENT -> {
                val student = client.api.getStudents(profileIdFilter = "eq.${profile.id}").body()?.firstOrNull()
                AuthenticatedAccount(profile = profile, student = student)
            }
            UserRole.TEACHER -> {
                val teacher = client.api.getTeachers(profileIdFilter = "eq.${profile.id}").body()?.firstOrNull()
                AuthenticatedAccount(profile = profile, teacher = teacher)
            }
            UserRole.PARENT -> {
                val parent = client.api.getParents(profileIdFilter = "eq.${profile.id}").body()?.firstOrNull()
                AuthenticatedAccount(profile = profile, parent = parent)
            }
            UserRole.ADMIN -> AuthenticatedAccount(profile = profile)
        }
    }
}
