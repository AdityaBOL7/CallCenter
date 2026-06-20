package com.example.callcenter.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * Places a real outgoing call through the device SIM/carrier using
 * [Intent.ACTION_CALL]. Used by SIM-mode auto-dial; SIP mode uses the in-app
 * call screen instead.
 *
 * Requires the runtime CALL_PHONE permission. [canCall] lets the caller check
 * before attempting so we can prompt for the permission first.
 */
object PhoneCaller {

    fun canCall(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Dial [phone] via the carrier. Returns true if the call intent was fired.
     * Returns false if the permission is missing or the number is blank.
     */
    fun call(context: Context, phone: String): Boolean {
        if (phone.isBlank() || !canCall(context)) return false
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phone)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
