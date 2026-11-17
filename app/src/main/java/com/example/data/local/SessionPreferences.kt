package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gla_cbt_prefs")

data class StoredSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val email: String = ""
) {
    val isLoggedIn: Boolean get() = accessToken.isNotBlank() && userId.isNotBlank()
}

class SessionPreferences(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        fun examDraft(attemptId: String) = stringPreferencesKey("exam_draft_$attemptId")
    }

    val sessionFlow: Flow<StoredSession> = context.dataStore.data.map { prefs ->
        StoredSession(
            accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
            refreshToken = prefs[Keys.REFRESH_TOKEN].orEmpty(),
            userId = prefs[Keys.USER_ID].orEmpty(),
            email = prefs[Keys.EMAIL].orEmpty()
        )
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    suspend fun getSession(): StoredSession = sessionFlow.first()

    suspend fun saveSession(accessToken: String, refreshToken: String, userId: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = userId
            prefs[Keys.EMAIL] = email
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.EMAIL)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun saveExamDraft(attemptId: String, serializedDraft: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.examDraft(attemptId)] = serializedDraft
        }
    }

    suspend fun getExamDraft(attemptId: String): String? =
        context.dataStore.data.first()[Keys.examDraft(attemptId)]

    suspend fun clearExamDraft(attemptId: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.examDraft(attemptId))
        }
    }
}
