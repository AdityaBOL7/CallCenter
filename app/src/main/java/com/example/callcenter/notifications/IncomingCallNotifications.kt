package com.example.callcenter.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.callcenter.R

/**
 * Notification for "an incoming call just ended — log its outcome". Posted when
 * an answered incoming SIM call is adopted for disposition while the app may be
 * in the background; tapping it opens the app, which routes to the disposition
 * screen via the persisted pending-disposition. Cancelled when the disposition
 * screen actually opens.
 */
object IncomingCallNotifications {
    const val CHANNEL_ID = "incoming_call_outcomes"
    const val CHANNEL_NAME = "Incoming call outcomes"

    /** Single slot: at most one incoming-call disposition is owed at a time. */
    const val NOTIFICATION_ID = 9101

    /** Idempotent: create the channel (Android 8+ requires one). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Log the outcome after an incoming call ends"
        }
        manager.createNotificationChannel(channel)
    }

    /** Post the "log this call" notification. No-op without POST_NOTIFICATIONS. */
    fun post(context: Context, callerLabel: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("Call ended — log the outcome")
            .setContentText("Select a disposition for the call with $callerLabel")
            // Sticky until the disposition screen opens (cancel()) — same
            // accountability as the back-blocked screen itself.
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPending)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Remove the notification once the disposition screen is actually open. */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
