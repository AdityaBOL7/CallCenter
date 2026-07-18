package com.example.callcenter.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Shared notification-channel setup for callback reminders. */
object ReminderNotifications {
    const val CHANNEL_ID = "callback_reminders"
    const val CHANNEL_NAME = "Callback reminders"

    /** Idempotent: create the reminders channel (Android 8+ requires one). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminders before your scheduled callbacks"
        }
        manager.createNotificationChannel(channel)
    }
}
