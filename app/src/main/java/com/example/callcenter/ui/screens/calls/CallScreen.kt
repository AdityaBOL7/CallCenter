package com.example.callcenter.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Note
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.telephony.PhoneCaller
import com.example.callcenter.ui.components.AppBottomSheet
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.AppGradients

/**
 * The call handoff screen. There are NO in-app audio controls (mute/hold/speaker)
 * because the actual conversation runs in the phone's telephony stack, not here:
 *  - SIM: the carrier call lives in the phone's own dialer.
 *  - SIP: the server-side click-to-call originate (fired earlier in
 *         CallsRepository. initiate) calls THIS phone first (the agent leg),
 *         then bridges the lead in.
 * SIM shows nothing (the device dialer takes over) and advances to disposition
 * when the carrier call ends. SIP shows a live waiting screen that mirrors the
 * agent leg's REAL state — calling your phone → ringing → in call (with a true
 * pickup-based timer) — and auto-advances to disposition when the leg ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callId: String,
    leadId: Int,
    routeType: String,
    onEnded: () -> Unit,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // SIM: place the carrier call via the device dialer, then wait for it to end
    // and go straight to disposition — no in-app call screen at all.
    if (viewModel.isSim) {
        SimDialLauncher(
            phone = state.customerPhone,
            claimDial = viewModel::claimSimDial,
            onPlaced = viewModel::markSimConnected,
            onCallEnded = viewModel::markSimEnded,
            onProceed = { viewModel.hangup(onEnded) },
        )
        return
    }

    // SIP: the call was already originated server-side in initiate(); the backend
    // now calls THIS phone (the agent leg), then bridges the lead in. Watch the
    // real device call state to mirror that live and auto-advance to disposition
    // the moment the leg ends (hang-up, decline, or ring-out). There is NO end
    // button — the phone's own call UI owns accept/hangup; the only manual exit
    // is the escape hatch that appears when the auto-advance can't work.
    SipCallStateEffect(
        onState = viewModel::onSipCallState,
        onUnavailable = viewModel::onWatcherUnavailable,
    )
    // Back must not abandon the call: initiate() marked it "open" (hasOpenCall
    // blocks all new calls until it's dispositioned), so a plain pop here would
    // orphan it — the agent then hits "complete the ongoing call" everywhere
    // with no screen left to complete it from. Back = wrap up to disposition,
    // same accountability rule the disposition screen itself enforces.
    androidx.activity.compose.BackHandler { viewModel.proceedSip() }
    // Navigation is state-driven: proceedSip() flips the flag once the wrap-up
    // is done, and whatever composition is CURRENT at that moment navigates.
    // A captured callback would break on rotation (it closes over a NavController
    // that no longer drives the UI) — this survives it.
    LaunchedEffect(state.leaveForDisposition) {
        if (state.leaveForDisposition) onEnded()
    }
    SipWaitingScreen(
        customerName = state.customerName,
        customerPhone = state.customerPhone,
        campaignName = state.campaignName,
        status = state.status,
        connectedAtMs = state.connectedAtMs,
        showProceedFallback = state.showProceedFallback,
        onProceed = viewModel::proceedSip,
        onOpenNote = viewModel::openNote,
    )

    if (state.noteOpen) {
        AppBottomSheet(onDismiss = { viewModel.closeNote() }) {
            NoteSheet(
                value = state.note,
                onChange = viewModel::setNote,
                onSave = viewModel::saveNote,
            )
        }
    }
}

/**
 * Watches the REAL device call state while the SIP waiting screen is up and
 * feeds every transition to [onState] (the ViewModel's SIP phase machine). The
 * click-to-call agent leg arrives as a genuine incoming carrier call on this
 * phone, so telephony state is the honest source of "ringing / picked up /
 * ended". If READ_PHONE_STATE is missing it re-asks once at point of use; a
 * denial leaves the screen passive (manual End still works). No lifecycle
 * backstop here, unlike SIM: returning to the app MID-call is legitimate in SIP
 * (the conversation is on this same device — agents check lead details), so
 * "app resumed" must not be read as "call over".
 */
@Composable
private fun SipCallStateEffect(
    onState: (state: Int, isInitial: Boolean) -> Unit,
    onUnavailable: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val watcher = remember { com.example.callcenter.telephony.CallStateWatcher(context) }
    // The watcher registers once but must always call the LATEST lambdas.
    val currentOnState = androidx.compose.runtime.rememberUpdatedState(onState)
    val currentOnUnavailable = androidx.compose.runtime.rememberUpdatedState(onUnavailable)
    // relayOnly: the watcher must survive multiple ring cycles (a stale call
    // ending, then the real agent leg) — no OFFHOOK→IDLE self-stop. The phase
    // machine in the ViewModel owns all interpretation, including ignoring the
    // registration snapshot (isInitial). start() == false means telephony is
    // unobservable → the screen's auto-advance can never fire; report it so the
    // manual escape hatch shows (the screen has no end-call button).
    val startWatcher = {
        val ok = watcher.start(
            onState = { s, isInitial -> currentOnState.value(s, isInitial) },
            relayOnly = true,
        )
        if (!ok) currentOnUnavailable.value()
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startWatcher() else currentOnUnavailable.value()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        if (watcher.hasPermission()) {
            startWatcher()
        } else {
            permissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
        }
        onDispose { watcher.stop() }
    }
}

/**
 * SIP click-to-call waiting screen. Theoriginate already happened server-side,
 * so this screen places no call — it mirrors the agent leg's real progress:
 * "calling your phone" → "your phone is ringing" → "in call" (true elapsed
 * timer from the actual pickup). There are NO call controls at all — accepting,
 * rejecting and hanging up all happen in the phone's own call UI (product
 * decision 2026-07-17), and ending is automatic: the leg going IDLE advances to
 * disposition. The only actions here are the note, plus a "log outcome" escape
 * hatch shown ONLY when auto-advance can't work ([showProceedFallback]).
 */
@Composable
private fun SipWaitingScreen(
    customerName: String,
    customerPhone: String,
    campaignName: String,
    status: CallStatus,
    connectedAtMs: Long?,
    showProceedFallback: Boolean,
    onProceed: () -> Unit,
    onOpenNote: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppGradients.callBackground()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    customerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(customerName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (customerPhone.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(customerPhone, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }
            if (campaignName.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(campaignName, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))
            // Live agent-leg progress. Falls back to the "calling" copy when the
            // telephony watcher isn't running (permission denied) — which is also
            // the truthful default state right after the originate.
            val (chipText, hintText) = when (status) {
                CallStatus.RINGING ->
                    "Your phone is ringing" to
                        "Pick up to start the call — the lead is dialed right after."
                CallStatus.CONNECTED ->
                    "In call" to
                        "Hang up from your phone's call screen when you're done — the outcome log opens automatically."
                // Wrap-up: proceedSip() ran (call over / End tapped) and the note
                // PATCH may still be in flight before navigation. Without this
                // branch the chip regressed to "Calling your phone…" right after
                // the call ended.
                CallStatus.COMPLETED ->
                    "Wrapping up…" to
                        "Saving the call — the outcome log opens next."
                else ->
                    "Calling your phone…" to
                        "Answer the incoming call to be connected to the lead."
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    chipText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (status == CallStatus.CONNECTED && connectedAtMs != null) {
                Spacer(Modifier.height(12.dp))
                SipElapsedTimer(connectedAtMs)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                hintText,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            // Note action.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable { onOpenNote() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Note,
                        contentDescription = "Add note",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Note", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }

            // Escape hatch — ONLY when auto-advance can't fire (no phone-state
            // permission, or no call arrived within the timeout). A quiet text
            // pill, deliberately not a red end-call button: this screen never
            // controls the call itself.
            if (showProceedFallback &&
                (status == CallStatus.INITIATING || status == CallStatus.RINGING)
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Didn't get the call? Log outcome & continue",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onProceed() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * True elapsed time since the agent actually picked up the agent leg — ticks
 * every second. Its own composable so the 1 Hz state write recomposes only this
 * Text, not the whole waiting screen.
 */
@Composable
private fun SipElapsedTimer(connectedAtMs: Long) {
    var elapsedSec by remember { mutableStateOf(0L) }
    LaunchedEffect(connectedAtMs) {
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - connectedAtMs) / 1000L)
                .coerceAtLeast(0L)
            kotlinx.coroutines.delay(1_000)
        }
    }
    Text(
        "%d:%02d".format(elapsedSec / 60, elapsedSec % 60),
        color = Color.White,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * SIM mode: places the real carrier call via the device dialer, then waits for
 * the carrier call to ACTUALLY END before proceeding to the feedback page —
 * there is no in-app call screen for SIM.
 *
 * How "ended" is detected: a [CallStateWatcher] listens to the real telephony
 * call state and fires once the call goes OFFHOOK → IDLE (the agent hung up).
 * This replaces the old ON_RESUME heuristic, which proceeded on the very next
 * app resume — so disposition could pop before/during the call (e.g. when the
 * call screen first appeared, or the agent glanced at the notification shade).
 *
 * Fallback: if READ_PHONE_STATE isn't granted (watcher can't start), we fall
 * back to "proceed when the agent returns to the app", but only after we've seen
 * the app actually go to the background first — so it can't fire prematurely.
 */
@Composable
private fun SimDialLauncher(
    phone: String,
    claimDial: () -> Boolean,
    onPlaced: () -> Unit,
    onCallEnded: () -> Unit,
    onProceed: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Set true once the call has been fired and the dialer has taken over.
    var awaitingReturn by remember { mutableStateOf(false) }
    // True once the app has actually backgrounded after dialing, so a return
    // (ON_RESUME) genuinely means the agent came back from the call.
    var wentBackground by remember { mutableStateOf(false) }
    var failedNoDialer by remember { mutableStateOf(false) }
    val watcher = remember { com.example.callcenter.telephony.CallStateWatcher(context) }
    // One-shot guard: the call-state watcher and the lifecycle backstop both race
    // to detect "the call is over". Whichever fires first navigates; this stops a
    // double-navigate (and stops the OTHER path from ever firing into a dead screen).
    val proceeded = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    fun proceedOnce() {
        if (proceeded.compareAndSet(false, true)) {
            // Stamp "the carrier call just ended" FIRST — hangup later derives
            // the true pickup time from it (end − call-log talk seconds).
            onCallEnded()
            onProceed()
        }
    }

    // Back must not abandon the call. initiate() already marked it open, and this
    // screen's only exit was the auto-advance once the carrier call ends — so a
    // plain pop (easy to hit on the blank hand-off screen when the dialer is slow
    // to take over: dual-SIM chooser, OEM delay) orphaned the call: every later
    // dial answered "Finish the current call's outcome before starting a new one"
    // with no screen left to finish it on. Back now wraps up to disposition,
    // exactly like the SIP screen and the auto-advance do.
    androidx.activity.compose.BackHandler { proceedOnce() }

    fun fireDial() {
        val placed = PhoneCaller.call(context, phone)
        if (placed) {
            onPlaced()
            awaitingReturn = true
            // Prefer real call-state detection (OFFHOOK → IDLE). The lifecycle
            // backstop below ALSO runs — if the watcher misses the transition
            // (short call, OEM telephony quirks), returning to the app still
            // advances to disposition instead of stranding on a black screen.
            watcher.start(onEnded = ::proceedOnce)
        } else {
            // Couldn't place the call (blank number / denied permission / no
            // dialer) — there's nothing to return from, so go to feedback now so
            // the agent can disposition it (e.g. "wrong number").
            failedNoDialer = true
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) fireDial() else failedNoDialer = true }

    // Place the carrier call exactly once (claimDial() guards rotation/recompose).
    LaunchedEffect(Unit) {
        if (!claimDial()) return@LaunchedEffect
        if (phone.isNotBlank() && PhoneCaller.canCall(context)) {
            fireDial()
        } else if (phone.isNotBlank()) {
            permissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
        } else {
            failedNoDialer = true
        }
    }

    // If the dial couldn't be placed, go straight to feedback (nothing to wait for).
    LaunchedEffect(failedNoDialer) {
        if (failedNoDialer) proceedOnce()
    }

    // Lifecycle BACKSTOP — always active (not gated on the watcher). Once the app
    // has actually backgrounded (the dialer took over) and the agent returns, we
    // proceed to disposition. proceedOnce() makes this harmless if the watcher
    // already fired; it's the safety net that prevents the black-screen hang when
    // the telephony watcher misses the call-end transition.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, awaitingReturn) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (!awaitingReturn) return@LifecycleEventObserver
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> wentBackground = true
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (wentBackground) proceedOnce()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            watcher.stop()
        }
    }

    // No visible UI — the phone's own dialer is the only screen the agent sees.
    Box(Modifier.fillMaxSize().background(AppColor.bg))
}

@Composable
private fun NoteSheet(
    value: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Add call note", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("Notes for this call…") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(16.dp))
        AppButton("Save note", onClick = onSave, fullWidth = true)
        Spacer(Modifier.height(20.dp))
    }
}
