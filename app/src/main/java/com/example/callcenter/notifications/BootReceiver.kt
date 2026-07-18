package com.example.callcenter.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.callcenter.data.prefs.AppPreferences
import com.example.callcenter.data.repository.CallbacksRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarms don't survive a reboot, so re-arm callback reminders after boot.
 * Best-effort: refreshes callbacks from the backend, then reschedules. Uses a
 * Hilt EntryPoint since a BroadcastReceiver can't have members injected directly.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun callbacksRepository(): CallbacksRepository
        fun appPreferences(): AppPreferences
        fun reminderScheduler(): ReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootEntryPoint::class.java,
        )
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                entry.callbacksRepository().refresh()
                val minutes = entry.appPreferences().current().followUpTimingMinutes
                entry.reminderScheduler().rescheduleAll(
                    entry.callbacksRepository().snapshot(),
                    minutes,
                )
                Log.d("Bol7Reminder", "boot: reminders re-armed")
            } catch (e: Exception) {
                Log.w("Bol7Reminder", "boot re-arm failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
