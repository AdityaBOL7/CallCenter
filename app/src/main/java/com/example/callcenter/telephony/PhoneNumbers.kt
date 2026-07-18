package com.example.callcenter.telephony

/**
 * Normalizes lead phone numbers to a dialable E.164 form.
 *
 * The backend stores bare local numbers (e.g. "9990599905"). Some OEM telecom
 * stacks reject a bare number with no region ("missing a country code or has the
 * wrong one") when dialing via ACTION_CALL, and WhatsApp's wa.me needs the country
 * code too. So we default a 10-digit Indian number to +91 and pass through numbers
 * that already carry a country code.
 */
object PhoneNumbers {

    /** Default country calling code when a number has none (India). */
    private const val DEFAULT_CC = "91"

    /**
     * Return an E.164-ish string like "+919990599905".
     * - Already starts with "+"           → keep digits after +, re-prefix "+".
     * - Digits start with the country code → prefix "+".
     * - Bare 10-digit local number         → prefix "+<DEFAULT_CC>".
     * - Anything else                       → best-effort "+<digits>".
     * Blank input returns "" (callers already guard against blank).
     */
    fun toDialable(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        val hadPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isBlank()) return ""

        return when {
            hadPlus -> "+$digits"
            digits.length == 10 -> "+$DEFAULT_CC$digits"
            digits.startsWith(DEFAULT_CC) && digits.length == 12 -> "+$digits"
            // Some numbers arrive with a leading 0 (national trunk prefix): 0XXXXXXXXXX.
            digits.length == 11 && digits.startsWith("0") -> "+$DEFAULT_CC${digits.drop(1)}"
            else -> "+$digits"
        }
    }

    /** Digits-only E.164 (no "+") for wa.me links, e.g. "919990599905". */
    fun toWhatsApp(raw: String): String = toDialable(raw).removePrefix("+")
}
