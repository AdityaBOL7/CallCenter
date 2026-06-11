package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.BuildConfig
import com.example.callcenter.data.prefs.SecureTokenStore
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
            val access = response.resolvedAccess
            val refresh = response.resolvedRefresh
            Log.d(TAG, "verifyOtp ← 2xx  hasAccess=${!access.isNullOrBlank()} hasRefresh=${!refresh.isNullOrBlank()}")
            if (access.isNullOrBlank()) {
                _authStatus.value = AuthStatus.UNAUTHENTICATED
                Log.e(TAG, "verifyOtp ← 2xx but no access token in response")
                Result.failure(IllegalStateException("Verification succeeded but no token was returned"))
            } else {
                tokenStore.setTokens(access = access, refresh = refresh.orEmpty())
                cachedUser = response.user
                Log.d(TAG, "login user: ${response.user?.fullName} (${response.user?.role})")
                _authStatus.value = AuthStatus.AUTHENTICATED
                Result.success(Unit)
            }
        } catch (e: HttpException) {
            _authStatus.value = AuthStatus.UNAUTHENTICATED
            val body = e.errorBody()
            Log.e(TAG, "verifyOtp ← HTTP ${e.code()}  body=$body", e)
            Result.failure(IllegalStateException(messageForVerifyOtp(e.code(), body), e))
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
