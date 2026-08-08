package com.example.callcenter.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("call_center_prefs")

data class UserPrefs(
    val themeOverride: String = "system",
    val lastTab: String = "dashboard",
    val notificationsEnabled: Boolean = true,
    // Seconds to wait between auto-dial calls (agent-configurable, clamped 5–10).
    val callDelaySeconds: Int = 5,
    // Persisted SAF tree URI of the call-recording folder (empty = not set).
    val recordingFolderUri: String = "",
    // How many minutes before a callback the local reminder fires (2/5/15/60).
    val followUpTimingMinutes: Int = 5,
    // A disposition the agent still owes (call id + lead id). Written the moment
    // the call STARTS (CallsRepository.initiate) — not when the disposition screen
    // opens — because a call abandoned before that screen ever composed is exactly
    // the one that would escape. Cleared only on submit. On relaunch we route back
    // to the disposition form so a call can't be abandoned by killing the app.
    val pendingDispositionCall: String = "",
    val pendingDispositionLead: Int = 0,
    // An ANSWERED incoming SIM call whose outcome the agent still owes. Unlike an
    // app-dialed call there's no backend row yet: the sim-incoming POST is held
    // until the agent submits (status=completed) or cancels (status=no_answer),
    // so a personal call is never recorded as a completed business call. Blank
    // number = none.
    val pendingIncomingNumber: String = "",
    val pendingIncomingStartedAt: String = "",
    val pendingIncomingDuration: Int = 0,
    // Which WhatsApp app the disposition WhatsApp button opens: "whatsapp" | "business".
    val whatsappTarget: String = "whatsapp",
)

@Singleton
class AppPreferences @Inject constructor(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_override")
        val LAST_TAB = stringPreferencesKey("last_tab")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val CALL_DELAY = intPreferencesKey("call_delay_seconds")
        val RECORDING_FOLDER = stringPreferencesKey("recording_folder_uri")
        val FOLLOWUP_TIMING = intPreferencesKey("followup_timing_minutes")
        val PENDING_DISP_CALL = stringPreferencesKey("pending_disposition_call")
        val PENDING_DISP_LEAD = intPreferencesKey("pending_disposition_lead")
        val PENDING_INC_NUMBER = stringPreferencesKey("pending_incoming_number")
        val PENDING_INC_STARTED = stringPreferencesKey("pending_incoming_started_at")
        val PENDING_INC_DURATION = intPreferencesKey("pending_incoming_duration")
        val WHATSAPP_TARGET = stringPreferencesKey("whatsapp_target")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { p ->
        UserPrefs(
            themeOverride = p[Keys.THEME] ?: "system",
            lastTab = p[Keys.LAST_TAB] ?: "dashboard",
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            callDelaySeconds = p[Keys.CALL_DELAY] ?: 5,
            recordingFolderUri = p[Keys.RECORDING_FOLDER] ?: "",
            followUpTimingMinutes = p[Keys.FOLLOWUP_TIMING] ?: 5,
            pendingDispositionCall = p[Keys.PENDING_DISP_CALL] ?: "",
            pendingDispositionLead = p[Keys.PENDING_DISP_LEAD] ?: 0,
            pendingIncomingNumber = p[Keys.PENDING_INC_NUMBER] ?: "",
            pendingIncomingStartedAt = p[Keys.PENDING_INC_STARTED] ?: "",
            pendingIncomingDuration = p[Keys.PENDING_INC_DURATION] ?: 0,
            whatsappTarget = p[Keys.WHATSAPP_TARGET] ?: "whatsapp",
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

    /** Auto-dial delay in seconds (clamped 5–10). */
    suspend fun setCallDelaySeconds(value: Int) {
        context.dataStore.edit { it[Keys.CALL_DELAY] = value.coerceIn(5, 10) }
    }

    /** Persisted SAF tree URI of the call-recording folder ("" clears it). */
    suspend fun setRecordingFolderUri(uri: String) {
        context.dataStore.edit { it[Keys.RECORDING_FOLDER] = uri }
    }

    /** Minutes-before for the callback reminder (2/5/15/60). */
    suspend fun setFollowUpTimingMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.FOLLOWUP_TIMING] = minutes }
    }

    /** Which WhatsApp app the quick-action opens ("whatsapp" | "business"). */
    suspend fun setWhatsappTarget(target: String) {
        context.dataStore.edit { it[Keys.WHATSAPP_TARGET] = target }
    }

    /** Remember a disposition the agent still owes (survives app kill). */
    suspend fun setPendingDisposition(callId: String, leadId: Int) {
        context.dataStore.edit {
            it[Keys.PENDING_DISP_CALL] = callId
            it[Keys.PENDING_DISP_LEAD] = leadId
        }
    }

    /** Clear the pending disposition once it's submitted. */
    suspend fun clearPendingDisposition() {
        context.dataStore.edit {
            it.remove(Keys.PENDING_DISP_CALL)
            it.remove(Keys.PENDING_DISP_LEAD)
        }
    }

    /**
     * Remember an answered incoming SIM call whose outcome is still owed. Held
     * locally (no backend row yet) until the agent submits or cancels — see
     * [UserPrefs.pendingIncomingNumber].
     */
    suspend fun setPendingIncoming(number: String, startedAtIso: String, durationSec: Int) {
        context.dataStore.edit {
            it[Keys.PENDING_INC_NUMBER] = number
            it[Keys.PENDING_INC_STARTED] = startedAtIso
            it[Keys.PENDING_INC_DURATION] = durationSec
        }
    }

    /** Clear the pending incoming call once it's reported (submitted or cancelled). */
    suspend fun clearPendingIncoming() {
        context.dataStore.edit {
            it.remove(Keys.PENDING_INC_NUMBER)
            it.remove(Keys.PENDING_INC_STARTED)
            it.remove(Keys.PENDING_INC_DURATION)
        }
    }

    /** One-shot read of the current prefs (for non-Flow callers like the scheduler). */
    suspend fun current(): UserPrefs = prefs.first()
}
