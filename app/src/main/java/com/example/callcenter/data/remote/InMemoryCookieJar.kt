package com.example.callcenter.data.remote

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Process-lifetime cookie store.
 *
 * AUTH_Services ties the OTP flow together with an `HttpOnly` session cookie
 * (`otp_req_uuid__login=...`, Domain=.bol7.com) set by send-otp-mobile and read
 * back by verify-otp-mobile. A real browser / Postman replays that cookie
 * automatically; OkHttp does not unless given a CookieJar, so we provide one.
 *
 * Cookies are keyed by host and kept in memory (cleared when the process dies),
 * which is the right scope for a short-lived OTP session.
 */
@Singleton
class InMemoryCookieJar @Inject constructor() : CookieJar {

    // host -> (cookie name -> cookie)
    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val byName = store.getOrPut(host) { ConcurrentHashMap() }
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            // ONLY the OTP-flow cookie is ever stored. AUTH_Services also sets
            // identity cookies (session / JWT) on login+refresh responses; if we
            // replayed one of those on a later send-otp, DRF would authenticate
            // the COOKIE instead of the anonymous request — and a stale cookie
            // for a deactivated user bricks login with 401 user_inactive no
            // matter what email is typed. Bearer auth is handled by headers, so
            // no other cookie has any business persisting here.
            if (!cookie.name.startsWith(OTP_COOKIE_PREFIX)) continue
            if (cookie.expiresAt <= now) {
                byName.remove(cookie.name)
            } else {
                byName[cookie.name] = cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val matching = mutableListOf<Cookie>()
        // Match cookies stored under this host or a parent domain (e.g. .bol7.com).
        for ((host, byName) in store) {
            if (url.host == host || url.host.endsWith(".$host") || host.endsWith(url.host)) {
                val expired = mutableListOf<String>()
                for (cookie in byName.values) {
                    if (cookie.expiresAt <= now) {
                        expired += cookie.name
                    } else if (cookie.matches(url)) {
                        matching += cookie
                    }
                }
                expired.forEach { byName.remove(it) }
            }
        }
        return matching
    }

    fun clear() = store.clear()

    private companion object {
        // otp_req_uuid__login, otp_req_uuid__<purpose>… — the cookies that tie
        // send-otp-mobile to verify-otp-mobile (the jar's whole reason to exist).
        const val OTP_COOKIE_PREFIX = "otp_req_uuid"
    }
}
