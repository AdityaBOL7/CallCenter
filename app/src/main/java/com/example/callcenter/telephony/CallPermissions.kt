package com.example.callcenter.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed

/**
 * Requests the runtime permissions the app needs for calling, once, the first
 * time an authenticated agent lands in the app (after login). This is preferred
 * over asking at cold launch: the prompt arrives in context, right before the
 * agent starts dialing, rather than on a login screen they haven't engaged with.
 *
 * Only CALL_PHONE and READ_PHONE_STATE are dangerous permissions that must be
 * requested; INTERNET and ACCESS_NETWORK_STATE are normal and granted at install.
 *
 * Why it's gated on the RESUMED state rather than a bare LaunchedEffect(Unit):
 * this composable first appears on the same frame the Login → MainTabs navigation
 * runs (with popUpTo(Login){inclusive=true} destroying the login screen). Launching
 * the permission request during that transition can get the launch cancelled before
 * the system dialog shows — the same way the in-flight me/ request gets cancelled by
 * the nav teardown. Waiting until the host is actually RESUMED avoids that race.
 *
 * Anything the user denies is re-requested later at the point of use — e.g. the SIM
 * dialer in CallScreen re-asks for CALL_PHONE when a call is placed — so a denial is
 * never a dead end. Already-granted permissions are skipped, so re-entry is harmless.
 */
@Composable
fun RequestCallPermissions() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Per-permission outcomes are handled just-in-time at each point of use. */ }

    LaunchedEffect(Unit) {
        // Suspend until the host is actually RESUMED, then launch exactly once.
        // withResumed runs its block when the lifecycle is at least RESUMED (or
        // immediately if it already is) and returns — so this is a true one-shot,
        // unlike repeatOnLifecycle which would re-fire on every resume. This
        // sidesteps the navigation-transition window where a launch gets cancelled.
        lifecycleOwner.withResumed {
            val missing = requiredCallPermissions().filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            // Nothing to ask → don't launch (avoids a no-op system round-trip).
            if (missing.isNotEmpty()) {
                launcher.launch(missing.toTypedArray())
            }
        }
    }
}

/** The runtime permissions calling needs, accounting for API level. */
private fun requiredCallPermissions(): List<String> = buildList {
    add(Manifest.permission.CALL_PHONE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        add(Manifest.permission.READ_PHONE_STATE)
    }
}

/** True if both calling permissions are already granted (for callers that want to check). */
fun hasAllCallPermissions(context: Context): Boolean =
    requiredCallPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
