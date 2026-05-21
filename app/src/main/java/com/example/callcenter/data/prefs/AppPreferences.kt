package com.example.callcenter.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("call_center_prefs")

data class UserPrefs(
    val themeOverride: String = "system",
    val lastTab: String = "dashboard",
    val notificationsEnabled: Boolean = true,
)

@Singleton
class AppPreferences @Inject constructor(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_override")
        val LAST_TAB = stringPreferencesKey("last_tab")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { p ->
        UserPrefs(
            themeOverride = p[Keys.THEME] ?: "system",
            lastTab = p[Keys.LAST_TAB] ?: "dashboard",
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
        )
    }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[Keys.THEME] = value }
    }

    suspend fun setLastTab(tab: String) {
        context.dataStore.edit { it[Keys.LAST_TAB] = tab }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }
}
