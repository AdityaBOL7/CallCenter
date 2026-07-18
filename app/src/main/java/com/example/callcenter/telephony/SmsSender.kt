package com.example.callcenter.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Sends an SMS directly via [SmsManager] (silent, no app switch). Used for the
 * optional after-call message on the disposition screen. Best-effort: returns
 * false on blank input, missing SEND_SMS permission, or any send failure — the
 * caller should never let a failed SMS block the disposition.
 *
 * Note: SEND_SMS is a Play-restricted permission; sending works technically but
 * the store listing may require a permissions declaration.
 */
object SmsSender {

    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Send [message] to [phone]. Long messages are split into multiple parts. */
    fun send(context: Context, phone: String, message: String): Boolean {
        if (phone.isBlank() || message.isBlank() || !canSend(context)) return false
        val dest = PhoneNumbers.toDialable(phone).ifBlank { phone }
        return try {
            val manager = smsManager(context) ?: return false
            val parts = manager.divideMessage(message)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(dest, null, parts, null, null)
            } else {
                manager.sendTextMessage(dest, null, message, null, null)
            }
            Log.d(TAG, "SMS sent to $phone (${parts.size} part(s))")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    private const val TAG = "Bol7Sms"
}
