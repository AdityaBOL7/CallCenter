package com.example.callcenter.data.remote

import okhttp3.Headers

/**
 * Pulls a named cookie's VALUE out of a response's Set-Cookie headers.
 *
 * AUTH_Services delivers the ENCRYPTED tokens ("gAAAA…", the only format the
 * callcenter backend accepts as a Bearer since 2026-07-16) exclusively via
 * Set-Cookie — the JSON body carries plain JWTs the dialer rejects with
 * "Invalid encrypted token". These headers are parsed manually so the values
 * are read as DATA and never replayed as ambient cookies (replaying identity
 * cookies on auth endpoints is what caused the user_inactive login hijack —
 * see InMemoryCookieJar).
 */
object SetCookies {

    /** The value of cookie [name] from [headers]' Set-Cookie lines, or null. */
    fun value(headers: Headers, name: String): String? =
        headers.values("Set-Cookie").firstNotNullOfOrNull { raw ->
            val nameValue = raw.substringBefore(';')
            val eq = nameValue.indexOf('=')
            if (eq <= 0) return@firstNotNullOfOrNull null
            val n = nameValue.substring(0, eq).trim()
            // Deletion cookies (value "" / expires 1970) must not win: an
            // empty value means "this cookie was cleared", not a token.
            val v = nameValue.substring(eq + 1).trim().trim('"')
            if (n == name && v.isNotBlank()) v else null
        }
}
