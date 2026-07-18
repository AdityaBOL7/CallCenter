package com.example.callcenter.telephony

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens external apps for the disposition quick-actions: WhatsApp, SMS, Gmail.
 * Each pre-fills the lead's number/email with a blank message. Best-effort — if
 * no app can handle the intent, a toast is shown instead of crashing.
 * Mirrors the defensive style of [PhoneCaller].
 */
object AppLinks {

    const val PKG_WHATSAPP = "com.whatsapp"
    const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /**
     * Open a WhatsApp chat with [phone] in the agent's chosen app. [target] is the
     * saved preference: "business" → WhatsApp Business, anything else → WhatsApp.
     * If the chosen app isn't installed, falls back to letting Android pick.
     */
    fun openWhatsApp(context: Context, phone: String, target: String = "whatsapp") {
        val digits = PhoneNumbers.toWhatsApp(phone)
        if (digits.isBlank()) {
            toast(context, "No phone number for this lead")
            return
        }
        val pkg = if (target == "business") PKG_WHATSAPP_BUSINESS else PKG_WHATSAPP
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Prefer the chosen app; if it isn't installed, retry without the package
        // so Android resolves whichever WhatsApp is present.
        try {
            context.startActivity(Intent(intent).setPackage(pkg))
        } catch (_: Exception) {
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                toast(context, "WhatsApp isn't installed")
            }
        }
    }

    /** Open the SMS composer addressed to [phone]. */
    fun openSms(context: Context, phone: String) {
        if (phone.isBlank()) {
            toast(context, "No phone number for this lead")
            return
        }
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")), "No messaging app found")
    }

    /** Open an email composer to [email] (blank To if null/blank). */
    fun openEmail(context: Context, email: String?) {
        val to = email?.trim().orEmpty()
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(to)}")), "No email app found")
    }

    private fun launch(context: Context, intent: Intent, failMsg: String) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            toast(context, failMsg)
        }
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
