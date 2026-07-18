package com.example.callcenter.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.callcenter.domain.model.Callback
import com.example.callcenter.domain.model.CallbackStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules local (on-device, no FCM) reminder notifications that fire a
 * configurable number of minutes before each pending callback. Alarms are keyed
 * by callback id so re-scheduling the same callback replaces its alarm.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Re-arm alarms for every pending, still-future callback; cancel the rest. */
    fun rescheduleAll(callbacks: List<Callback>, minutesBefore: Int) {
        callbacks.forEach { cb ->
            if (cb.status == CallbackStatus.PENDING || cb.status == CallbackStatus.OVERDUE) {
                schedule(cb, minutesBefore)
            } else {
                cancel(cb.id)
            }
        }
    }

    /** Arm (or replace) the reminder for one callback. No-op if it's in the past. */
    fun schedule(callback: Callback, minutesBefore: Int) {
        val fireAtMillis = callback.scheduledAt
            .minusMinutes(minutesBefore.toLong())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Nothing to remind about if the reminder time has already passed.
        if (fireAtMillis <= System.currentTimeMillis()) {
            cancel(callback.id)
            return
        }

        val pending = pendingIntentFor(callback, minutesBefore, create = true) ?: return
        try {
            // Prefer an exact alarm; fall back to inexact if the OS withholds the
            // exact-alarm privilege (Android 12+ can require user consent).
            if (canExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pending)
            }
            Log.d(TAG, "reminder armed: cb=${callback.id} at=$fireAtMillis (-${minutesBefore}m)")
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pending)
            Log.w(TAG, "exact alarm denied, used inexact for cb=${callback.id}", e)
        }
    }

    /** Cancel the reminder for a callback id (if any). */
    fun cancel(callbackId: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            callbackId,
            intent,
            PendingIntent.FLAG_NO_CREATE or flagImmutable(),
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    private fun pendingIntentFor(
        callback: Callback,
        minutesBefore: Int,
        create: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_CALLBACK_ID, callback.id)
            putExtra(ReminderReceiver.EXTRA_CUSTOMER_NAME, callback.customerName)
            putExtra(ReminderReceiver.EXTRA_PHONE, callback.phone)
            putExtra(ReminderReceiver.EXTRA_MINUTES, minutesBefore)
        }
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            flagImmutable()
        return PendingIntent.getBroadcast(context, callback.id, intent, flags)
    }

    private fun canExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun flagImmutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private companion object {
        const val TAG = "Bol7Reminder"
    }
}
