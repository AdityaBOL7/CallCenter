package com.example.callcenter

import android.app.Application
import com.example.callcenter.data.prefs.SecureTokenStore
import com.example.callcenter.notifications.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CallCenterApp : Application() {

    @Inject lateinit var tokenStore: SecureTokenStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Warm the token cache off the main thread so the first synchronous read
        // (AuthRepository deciding the start destination, AuthInterceptor on the
        // first request) is served from memory instead of blocking on disk I/O.
        appScope.launch { tokenStore.prime() }
        // Create the notification channels once at startup.
        ReminderNotifications.ensureChannel(this)
        com.example.callcenter.notifications.IncomingCallNotifications.ensureChannel(this)
    }
}
