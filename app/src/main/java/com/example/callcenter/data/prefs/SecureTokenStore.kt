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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Persists the auth tokens.
 *
 * Backed by plain Jetpack [DataStore] in the app's private storage — NOT
 * `EncryptedSharedPreferences`. The Jetpack Security `security-crypto` library
 * is deprecated and corrupts on many OEM devices (the Keystore key gets
 * invalidated, so the stored keyset can no longer be decrypted and
 * `EncryptedSharedPreferences.create()` throws `AEADBadTagException`, crashing
 * the app at startup). DataStore has no Keystore dependency and cannot fail that
 * way. On a non-rooted device the per-app sandbox already isolates this file.
 *
 * The OkHttp [com.example.callcenter.data.remote.AuthInterceptor] /
 * [com.example.callcenter.data.remote.TokenAuthenticator] read tokens
 * *synchronously* on network threads, so reads are served from an in-memory
 * cache (seeded once from disk); writes update the cache immediately and persist
 * to DataStore. The public API is unchanged from the previous implementation.
 */
@Singleton
class SecureTokenStore @Inject constructor(private val context: Context) {

    private val dataStore: DataStore<Preferences> get() = context.tokenDataStore

    // Synchronous read cache. Volatile so interceptor threads see writes.
    @Volatile private var cachedAccess: String? = null
    @Volatile private var cachedRefresh: String? = null
    @Volatile private var loaded = false

    /** Seed the cache from disk once. Safe to block — DataStore reads are fast. */
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
        runBlocking {
            dataStore.edit {
                it[KEY_ACCESS] = access
                it[KEY_REFRESH] = refresh
            }
        }
    }

    fun clear() {
        cachedAccess = null
        cachedRefresh = null
        loaded = true
        runBlocking { dataStore.edit { it.clear() } }
    }

    companion object {
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
    }
}

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore("auth_tokens")
