package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.BuildConfig
import com.example.callcenter.data.prefs.SecureTokenStore
import com.example.callcenter.data.remote.SetCookies
import com.example.callcenter.data.remote.api.AuthApi
import com.example.callcenter.data.remote.dto.SendOtpRequest
import com.example.callcenter.data.remote.dto.VerifyOtpRequest
import com.example.callcenter.data.remote.dto.VerifyOtpResponse
import com.example.callcenter.domain.model.AuthStatus
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: SecureTokenStore,
) {

    // A persisted access token means the user is already signed in.
    private val _authStatus = MutableStateFlow(
        if (tokenStore.getAccessToken().isNullOrBlank()) {
            AuthStatus.UNAUTHENTICATED
        } else {
            AuthStatus.AUTHENTICATED
        }
    )
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    // Owns the login-period countdown; survives ViewModels (singleton lifetime).
    private val repoScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )
    private var expiryJob: kotlinx.coroutines.Job? = null

    init {
        // The server's login period (verify-otp's expiry_in_utc) is enforced by
        // the APP, not just by requests failing: if it already lapsed while the
        // app was closed, log out right now instead of showing a zombie session
        // (stale "Agent" profile with every call 401ing). Otherwise arm the
        // countdown so the logout happens the moment the period ends.
        enforceSessionExpiry()
    }

    /**
     * Force logout if the login period has ended; otherwise (re-)arm the
     * countdown to fire exactly when it does. Cheap and idempotent — called at
     * process start, after login, and on every app foreground.
     */
    fun enforceSessionExpiry() {
        if (_authStatus.value != AuthStatus.AUTHENTICATED) return
        val expiry = tokenStore.getSessionExpiryMillis() ?: return
        val remaining = expiry - System.currentTimeMillis()
        if (remaining <= 0) {
            Log.w(TAG, "Login period ended (expiry passed) — logging out")
            sessionExpired()
            return
        }
        expiryJob?.cancel()
        expiryJob = repoScope.launch {
            kotlinx.coroutines.delay(remaining)
            if (_authStatus.value == AuthStatus.AUTHENTICATED) {
                Log.w(TAG, "Login period ended — automatic logout")
                sessionExpired()
            }
        }
    }

    /**
     * A successful token refresh extended the login period — persist the new
     * end and re-arm the countdown. No-op when the refresh response carries no
     * expiry (the previously stored one then stays authoritative).
     */
    fun sessionExtended(rawExpiry: String?) {
        val millis = rawExpiry?.let(::parseExpiryMillis) ?: return
        tokenStore.setSessionExpiry(millis)
        enforceSessionExpiry()
    }

    /**
     * Parse expiry_in_utc to epoch millis. The live server sends EPOCH SECONDS
     * as a number (1784369981); ISO-8601 is kept as a fallback in case the
     * format changes again.
     */
    private fun parseExpiryMillis(raw: String): Long? {
        raw.toLongOrNull()?.let { n ->
            // 10-digit epoch = seconds; 13-digit = already millis.
            return if (n < 1_000_000_000_000L) n * 1000 else n
        }
        return try {
            java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                // No offset in the string — the backend documents it as UTC.
                java.time.LocalDateTime.parse(raw).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                Log.w(TAG, "Unparseable expiry_in_utc: \"$raw\" — auto-logout disabled for this session")
                null
            }
        }
    }

    // The user profile returned at login (name/email/phone/role). Survives the
    // process and lets us show the real identity even if the dialer me/ 404s.
    @Volatile
    var cachedUser: VerifyOtpResponse.AuthUser? = null
        private set

    /** Step 1 — send an OTP to the registered email or phone. */
    suspend fun sendOtp(identifier: String): Result<Unit> {
        val id = identifier.trim()
        Log.d(TAG, "sendOtp → POST api/auth/send-otp-mobile/  body={email=$id, purpose=login}")
        return try {
            val resp = authApi.sendOtpMobile(SendOtpRequest(email = id))
            Log.d(TAG, "sendOtp ← 2xx  detail=${resp.detail} message=${resp.message}")
            Result.success(Unit)
        } catch (e: HttpException) {
            val body = e.errorBody()
            Log.e(TAG, "sendOtp ← HTTP ${e.code()}  body=$body", e)
            Result.failure(IllegalStateException(messageForSendOtp(e.code(), body), e))
        } catch (e: IOException) {
            Log.e(TAG, "sendOtp ← network error", e)
            Result.failure(IOException(NETWORK_ERROR, e))
        } catch (e: Exception) {
            Log.e(TAG, "sendOtp ← unexpected error", e)
            Result.failure(e)
        }
    }

    /** Step 2 — verify the OTP; on success the tokens are persisted. */
    suspend fun verifyOtp(identifier: String, otp: String): Result<Unit> {
        val id = identifier.trim()
        // OTP is a secret — only echo it in debug builds, masked otherwise.
        val otpForLog = if (BuildConfig.DEBUG) otp.trim() else "***"
        Log.d(TAG, "verifyOtp → POST api/auth/verify-otp-mobile/  body={email=$id, otp=$otpForLog, purpose=login, role=agent}")
        _authStatus.value = AuthStatus.LOADING
        return try {
            val response = authApi.verifyOtpMobile(
                VerifyOtpRequest(email = id, otp = otp.trim()),
            )
            if (!response.isSuccessful) {
                _authStatus.value = AuthStatus.UNAUTHENTICATED
                val body = try {
                    response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
                Log.e(TAG, "verifyOtp ← HTTP ${response.code()}  body=$body")
                return Result.failure(IllegalStateException(messageForVerifyOtp(response.code(), body)))
            }
            val payload = response.body()
            // The callcenter backend accepts ONLY the encrypted ("gAAAA…")
            // tokens, which arrive via Set-Cookie — the body JWTs get 401
            // "Invalid encrypted token" on every dialer call (2026-07-16
            // backend change). Prefer the cookie values; fall back to the
            // body so a backend revert to JWT auth keeps login working.
            val cookieAccess = SetCookies.value(response.headers(), "access_token")
            val cookieRefresh = SetCookies.value(response.headers(), "refresh_token")
            val access = cookieAccess ?: payload?.resolvedAccess
            val refresh = cookieRefresh ?: payload?.resolvedRefresh
            Log.d(
                TAG,
                "verifyOtp ← 2xx  hasAccess=${!access.isNullOrBlank()} hasRefresh=${!refresh.isNullOrBlank()} " +
                    "source=${if (cookieAccess != null) "cookie(encrypted)" else "body(jwt)"}",
            )
            if (access.isNullOrBlank()) {
                _authStatus.value = AuthStatus.UNAUTHENTICATED
                Log.e(TAG, "verifyOtp ← 2xx but no access token in response")
                Result.failure(IllegalStateException("Verification succeeded but no token was returned"))
            } else {
                if (refresh.isNullOrBlank()) {
                    // Login still proceeds (the access token works for now), but
                    // this session CANNOT survive an access-token expiry: the
                    // authenticator will find no refresh token and force logout.
                    // If this line shows up, the fix belongs on AUTH_Services —
                    // verify-otp-mobile must return a refresh token.
                    Log.e(TAG, "verifyOtp: backend returned NO refresh token — session will die when the access token expires")
                }
                tokenStore.setTokens(access = access, refresh = refresh.orEmpty())
                cachedUser = payload?.user
                // Enforce the server's login period: store when it ends and arm
                // the auto-logout countdown (enforceSessionExpiry below).
                payload?.resolvedExpiry?.let { raw ->
                    parseExpiryMillis(raw)?.let { millis ->
                        tokenStore.setSessionExpiry(millis)
                        Log.d(TAG, "login period ends at $raw")
                    }
                }
                Log.d(TAG, "login user: ${payload?.user?.fullName} (${payload?.user?.role})")
                _authStatus.value = AuthStatus.AUTHENTICATED
                enforceSessionExpiry()
                Result.success(Unit)
            }
        } catch (e: IOException) {
            _authStatus.value = AuthStatus.UNAUTHENTICATED
            Log.e(TAG, "verifyOtp ← network error", e)
            Result.failure(IOException(NETWORK_ERROR, e))
        } catch (e: Exception) {
            _authStatus.value = AuthStatus.UNAUTHENTICATED
            Log.e(TAG, "verifyOtp ← unexpected error", e)
            Result.failure(e)
        }
    }

    fun logout() {
        expiryJob?.cancel()
        tokenStore.clear()
        cachedUser = null
        _authStatus.value = AuthStatus.UNAUTHENTICATED
    }

    /**
     * The refresh token is dead (refresh itself 401'd) — the session is over.
     * Called from [com.example.callcenter.data.remote.TokenAuthenticator] on an
     * OkHttp thread, so it only touches thread-safe state. Flipping authStatus to
     * UNAUTHENTICATED makes the NavHost return to Login instead of leaving the UI
     * stuck "logged in" while every request 401s. Idempotent.
     */
    fun sessionExpired() {
        if (_authStatus.value == AuthStatus.UNAUTHENTICATED) return
        Log.w(TAG, "Session over (refresh rejected or login period ended) — forcing logout")
        expiryJob?.cancel()
        tokenStore.clear()
        cachedUser = null
        _authStatus.value = AuthStatus.UNAUTHENTICATED
    }

    /** Read the raw error body once (it can only be consumed a single time). */
    private fun HttpException.errorBody(): String? =
        try {
            response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun messageForSendOtp(code: Int, body: String?): String {
        val server = serverDetail(body)
        return when (code) {
            400, 404 -> server ?: "We couldn't find an account for that email or phone."
            429 -> server ?: "Too many attempts. Please wait a while before trying again."
            401 -> server ?: "This account is temporarily blocked. Try again later."
            in 500..599 -> SERVER_ERROR
            else -> server ?: "Couldn't send OTP (error $code)."
        } + debugSuffix(code)
    }

    private fun messageForVerifyOtp(code: Int, body: String?): String {
        val server = serverDetail(body)
        return when (code) {
            400 -> server ?: "Invalid or expired OTP. Please check and try again."
            401 -> server ?: "This account is blocked. Try again after the cooldown."
            429 -> server ?: "Too many attempts. Please wait a while before trying again."
            in 500..599 -> SERVER_ERROR
            else -> server ?: "Verification failed (error $code)."
        } + debugSuffix(code)
    }

    /** Pull a human-readable message out of a JSON error body, if present. */
    private fun serverDetail(body: String?): String? {
        if (body.isNullOrBlank()) return null
        // Common DRF / AUTH_Services shapes: {"detail":"..."} / {"message":"..."} / {"error":"..."}
        for (key in listOf("detail", "message", "error")) {
            val regex = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            regex.find(body)?.groupValues?.getOrNull(1)?.let { return it }
        }
        // Fall back to the trimmed raw body so nothing is hidden.
        return body.trim().take(160)
    }

    // In debug builds, append the HTTP code so it's visible on-screen without logcat.
    private fun debugSuffix(code: Int): String = if (BuildConfig.DEBUG) " [HTTP $code]" else ""

    private companion object {
        const val TAG = "Bol7Auth"
        const val NETWORK_ERROR = "Network error. Check your connection and try again."
        const val SERVER_ERROR = "Server error. Please try again later."
    }
}
