package com.example.callcenter.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Persists the auth tokens.
 *
 * Backed by plain Jetpack [DataStore] in the app's private storage — NOT
 * `EncryptedSharedPreferences` (deprecated; crashes via AEADBadTagException on
 * OEM keystore invalidation). The per-app sandbox already isolates this file on
 * a non-rooted device.
 *
 * Threading: the OkHttp [com.example.callcenter.data.remote.AuthInterceptor] /
 * [com.example.callcenter.data.remote.TokenAuthenticator] read tokens
 * *synchronously* on network threads, so reads are served from an in-memory
 * cache. The cache is seeded once — eagerly from [CallCenterApp] on a background
 * thread via [prime] so the very first read on the MAIN thread never blocks on
 * disk (that was an ANR source at cold start). Writes update the cache
 * immediately (so any synchronous read right after is correct) and persist to
 * DataStore off the main thread, fire-and-forget.
 */
@Singleton
class SecureTokenStore @Inject constructor(private val context: Context) {

    private val dataStore: DataStore<Preferences> get() = context.tokenDataStore

    // IO scope for non-blocking persistence; lives for the process (token store
    // is a singleton). Never touches the main thread.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Synchronous read cache. Volatile so interceptor threads see writes.
    @Volatile private var cachedAccess: String? = null
    @Volatile private var cachedRefresh: String? = null
    @Volatile private var cachedExpiryMillis: Long? = null
    @Volatile private var loaded = false

    /**
     * Seed the cache from disk. Call once at app startup from a background
     * coroutine (see CallCenterApp) so the first synchronous read on the main
     * thread is served from memory and never blocks. Safe to call repeatedly.
     */
    suspend fun prime() {
        if (loaded) return
        val prefs = dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        synchronized(this) {
            if (!loaded) {
                cachedAccess = prefs[KEY_ACCESS]
                cachedRefresh = prefs[KEY_REFRESH]
                cachedExpiryMillis = prefs[KEY_EXPIRY]?.toLongOrNull()
                loaded = true
            }
        }
    }

    /**
     * Last-resort synchronous seed for a read that happens before [prime] has
     * completed (rare: only if the network layer reads tokens within the first
     * few ms of launch). Blocks the *calling* thread — which for the network
     * path is an OkHttp worker, not the main thread.
     */
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = runBlocking {
                dataStore.data
                    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                    .first()
            }
            cachedAccess = prefs[KEY_ACCESS]
            cachedRefresh = prefs[KEY_REFRESH]
            cachedExpiryMillis = prefs[KEY_EXPIRY]?.toLongOrNull()
            loaded = true
        }
    }

    fun getAccessToken(): String? {
        ensureLoaded()
        return cachedAccess
    }

    fun getRefreshToken(): String? {
        ensureLoaded()
        return cachedRefresh
    }

    fun setTokens(access: String, refresh: String) {
        // Update the cache first so any immediate synchronous read is correct.
        cachedAccess = access
        cachedRefresh = refresh
        loaded = true
        // Persist off the main thread, fire-and-forget.
        ioScope.launch {
            dataStore.edit {
                it[KEY_ACCESS] = access
                it[KEY_REFRESH] = refresh
            }
        }
    }

    /** Epoch millis when the login period ends, or null if the server never said. */
    fun getSessionExpiryMillis(): Long? {
        ensureLoaded()
        return cachedExpiryMillis
    }

    /** Persist the end of the login period (from verify-otp's expiry_in_utc). */
    fun setSessionExpiry(epochMillis: Long) {
        cachedExpiryMillis = epochMillis
        ioScope.launch { dataStore.edit { it[KEY_EXPIRY] = epochMillis.toString() } }
    }

    fun clear() {
        cachedAccess = null
        cachedRefresh = null
        cachedExpiryMillis = null
        loaded = true
        ioScope.launch { dataStore.edit { it.clear() } }
    }

    companion object {
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
        private val KEY_EXPIRY = stringPreferencesKey("session_expiry_millis")
    }
}

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore("auth_tokens")
