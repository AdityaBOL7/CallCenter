package com.example.callcenter.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Manifest-registered PHONE_STATE receiver that feeds [IncomingCallCoordinator].
 *
 * Registered in the manifest (not at runtime) so incoming calls are captured even
 * when the app process was killed — PHONE_STATE is exempt from the implicit
 * broadcast restrictions and starts the process. Each broadcast gets a fresh
 * receiver instance; all cross-broadcast state lives in the @Singleton
 * coordinator, resolved via an explicit Hilt entry point (BroadcastReceiver
 * field injection isn't reliable without the Gradle-plugin bytecode transform).
 *
 * On IDLE the actual work (call-log read + backend POST) runs on a coroutine, so
 * goAsync() keeps the process alive until it completes.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun incomingCallCoordinator(): IncomingCallCoordinator
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val coordinator = EntryPointAccessors
            .fromApplication(context.applicationContext, Deps::class.java)
            .incomingCallCoordinator()
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING ->
                coordinator.onRinging(intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER))
            TelephonyManager.EXTRA_STATE_OFFHOOK -> coordinator.onOffhook()
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val pending = goAsync()
                coordinator.onIdle { pending.finish() }
            }
        }
    }
}
