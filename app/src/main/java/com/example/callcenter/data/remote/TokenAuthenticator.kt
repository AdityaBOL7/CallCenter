package com.example.callcenter.data.remote

import android.util.Log
import com.example.callcenter.data.prefs.SecureTokenStore
import com.example.callcenter.data.remote.api.AuthApi
import com.example.callcenter.data.remote.dto.RefreshRequest
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
        val resp = runBlocking { authApi.get().refresh(RefreshRequest(refresh = refresh)) }
        val access = resp.resolvedAccess
        if (access.isNullOrBlank()) {
            Log.e(TAG, "Refresh returned no access token")
            null
        } else {
            tokenStore.setTokens(access = access, refresh = refresh)
            Log.d(TAG, "Access token refreshed")
            access
        }
    } catch (e: Exception) {
        Log.e(TAG, "Refresh failed", e)
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
