package com.example.callcenter.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Observes the device call state for SIM-mode dialing so the app can advance to
 * the disposition screen only AFTER the carrier call has actually ended — i.e.
 * the call went OFFHOOK (connected/dialing) and then back to IDLE (hung up).
 *
 * Why not just use the activity lifecycle: an ON_RESUME fires for many reasons
 * (the screen first appearing, the notification shade, app switching) that have
 * nothing to do with the call finishing. Listening to the real telephony state
 * is the only reliable "the call is over" signal.
 *
 * Requires READ_PHONE_STATE. If it's missing, [start] returns false and the
 * caller should fall back to the lifecycle heuristic.
 */
class CallStateWatcher(private val context: Context) {

    private val telephony =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    // We only fire onEnded once we've actually SEEN the call go off-hook. A dial
    // that never connects (declined instantly) still ends IDLE, so we also treat
    // a transition into IDLE as "ended" once started.
    private var sawOffHook = false
    private var ended = false

    // API 31+ uses TelephonyCallback; older uses the deprecated PhoneStateListener.
    private var callback: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Begin watching. [onEnded] is invoked exactly once, on the main thread, when
     * the call finishes. Returns false (and never calls back) if telephony or the
     * permission is unavailable — the caller should then use a fallback.
     */
    fun start(onEnded: () -> Unit): Boolean {
        val tm = telephony ?: return false
        if (!hasPermission()) return false

        fun handleState(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> sawOffHook = true
                TelephonyManager.CALL_STATE_IDLE -> {
                    // Ignore the initial IDLE that fires immediately on registration
                    // (before any call). Only treat IDLE as "ended" once we've been
                    // off-hook — that's a genuine hang-up.
                    if (sawOffHook && !ended) {
                        ended = true
                        stop()
                        onEnded()
                    }
                }
            }
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = handleState(state)
                }
                callback = cb
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                        handleState(state)
                }
                legacyListener = l
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            }
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Stop watching. Safe to call multiple times. */
    fun stop() {
        val tm = telephony ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callback?.let { tm.unregisterTelephonyCallback(it) }
            callback = null
        } else {
            @Suppress("DEPRECATION")
            legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            legacyListener = null
        }
    }
}
