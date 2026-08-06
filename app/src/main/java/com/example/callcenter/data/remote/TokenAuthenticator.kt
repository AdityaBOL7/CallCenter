package com.example.callcenter.data.remote

import android.util.Log
import com.example.callcenter.data.prefs.SecureTokenStore
import com.example.callcenter.data.remote.api.AuthApi
import com.example.callcenter.data.remote.dto.RefreshRequest
import com.example.callcenter.data.repository.AuthRepository
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Handles 401s on dialer requests by trading the refresh token for a new
 * access token (POST api/refresh/) and retrying the original request once.
 *
 * OkHttp calls this only on a 401; it retries automatically with the request
 * we return, or gives up if we return null.
 *
 * `AuthApi` is injected lazily to avoid a DI cycle: AuthApi ← Retrofit ← the
 * auth OkHttpClient, which is independent of the dialer client this guards.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: SecureTokenStore,
    private val authApi: Lazy<AuthApi>,
    // Lazy to avoid a DI init cycle. AuthRepository uses the AUTH client, not the
    // dialer client this authenticator guards, so there is no real runtime cycle.
    private val authRepository: Lazy<AuthRepository>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Give up if we've already retried (avoid infinite loops).
        if (responseCount(response) >= 2) {
            Log.w(TAG, "Refresh already attempted; giving up on ${response.request.url}")
            return null
        }

        val refresh = tokenStore.getRefreshToken()
        if (refresh.isNullOrBlank()) {
            Log.w(TAG, "No refresh token; cannot refresh")
            authRepository.get().sessionExpired()
            return null
        }

        val newAccess = synchronized(this) {
            // Another thread may have refreshed while we waited on the lock.
            val current = tokenStore.getAccessToken()
            val staleAccess = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (!current.isNullOrBlank() && current != staleAccess) {
                current
            } else {
                tryRefresh(refresh)
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private fun tryRefresh(refresh: String): String? = try {
        // The refresh is sent BOTH in the body and as a Cookie: the cookie is
        // the web frontend's flow, and presenting it makes the server answer
        // with Set-Cookie ENCRYPTED tokens — the only format the callcenter
        // backend accepts as Bearer (2026-07-16 backend change).
        val resp = runBlocking {
            authApi.get().refresh(
                RefreshRequest(refresh = refresh),
                cookie = "refresh_token=$refresh",
            )
        }
        if (!resp.isSuccessful) {
            // The refresh token itself was rejected (401/400 invalid_grant /
            // revoked): the session is genuinely over → force logout. A 5xx is
            // treated as transient.
            if (resp.code() == 401 || resp.code() == 400) {
                Log.e(TAG, "Refresh token rejected (HTTP ${resp.code()})")
                authRepository.get().sessionExpired()
            } else {
                Log.e(TAG, "Refresh failed (HTTP ${resp.code()})")
            }
            null
        } else {
            val body = resp.body()
            // Prefer the encrypted tokens from Set-Cookie; fall back to body.
            val access = SetCookies.value(resp.headers(), "access_token")
                ?: body?.resolvedAccess
            // ROTATION: every successful refresh REVOKES the old refresh token.
            // Failing to store the replacement guarantees a forced logout on
            // the next refresh (the 2026-07-16 dead-session incident).
            val newRefresh = SetCookies.value(resp.headers(), "refresh_token")
                ?: body?.resolvedRefresh
                ?: refresh
            if (access.isNullOrBlank()) {
                Log.e(TAG, "Refresh returned no access token")
                authRepository.get().sessionExpired()
                null
            } else {
                tokenStore.setTokens(access = access, refresh = newRefresh)
                Log.d(TAG, "Access token refreshed (refresh rotated=${newRefresh != refresh})")
                access
            }
        }
    } catch (e: Exception) {
        // Network/transient error — don't log the user out; let them retry.
        Log.e(TAG, "Refresh failed (transient)", e)
        null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val TAG = "Bol7Auth"
    }
}
