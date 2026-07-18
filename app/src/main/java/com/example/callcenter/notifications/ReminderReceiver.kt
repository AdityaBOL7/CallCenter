package com.example.callcenter.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.callcenter.R

/**
 * Fires when a callback reminder alarm goes off; posts a local notification.
 * Tapping it opens the app (the launcher activity).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callbackId = intent.getIntExtra(EXTRA_CALLBACK_ID, 0)
        val name = intent.getStringExtra(EXTRA_CUSTOMER_NAME)?.takeIf { it.isNotBlank() } ?: "a customer"
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 5)

        ReminderNotifications.ensureChannel(context)

        // Android 13+: don't post if the user hasn't granted notifications.
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
            callbackId,
            launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable(),
        )

        val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("Callback reminder")
            .setContentText("Call $name in $minutes min")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPending)
            .build()

        NotificationManagerCompat.from(context).notify(callbackId, notification)
    }

    private fun flagImmutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    companion object {
        const val EXTRA_CALLBACK_ID = "cb_id"
        const val EXTRA_CUSTOMER_NAME = "cb_name"
        const val EXTRA_PHONE = "cb_phone"
        const val EXTRA_MINUTES = "cb_minutes"
    }
}
