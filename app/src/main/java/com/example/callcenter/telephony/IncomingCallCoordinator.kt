package com.example.callcenter.telephony

import android.content.Context
import android.util.Log
import com.example.callcenter.data.prefs.AppPreferences
import com.example.callcenter.data.prefs.SecureTokenStore
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.notifications.IncomingCallNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Tracks a normal (non-app) INCOMING carrier call from RINGING to IDLE and, once
 * it ends, reports it to the backend (calls/sim-incoming/ — which finds-or-creates
 * a Lead from the caller's number) and routes the agent to the same disposition
 * screen used for app-dialed calls.
 *
 * Fed by [IncomingCallReceiver] (manifest-registered PHONE_STATE receiver), so it
 * works whether the app is foregrounded, backgrounded, or was killed — a
 * @Singleton survives across the RINGING/OFFHOOK/IDLE broadcasts of one call
 * because they all arrive in the same (possibly freshly started) process.
 *
 * Outcomes:
 *  - Answered (RINGING → OFFHOOK → IDLE): the call is held LOCALLY (persisted as
 *    a pending incoming, plus a notification and — when the UI is alive — direct
 *    navigation) and the disposition screen opens. The sim-incoming POST is
 *    deliberately deferred to the agent's decision there, so exactly one row is
 *    ever created per call: status=completed (+ true talk seconds) on submit, or
 *    status=no_answer on cancel. That way a personal call the agent picks up is
 *    never recorded as a completed business call.
 *  - Missed/rejected (RINGING → IDLE): NOT recorded at all — no POST, no lead,
 *    nothing in calls-today or history (product decision: an unanswered call
 *    isn't business data).
 *
 * Dropped (never reported): no caller number available (READ_CALL_LOG denied),
 * not logged in, or the agent's call_mode is known to not be SIM.
 */
@Singleton
class IncomingCallCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callsRepo: CallsRepository,
    private val agentRepo: AgentRepository,
    private val appPrefs: AppPreferences,
    private val tokenStore: SecureTokenStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Emits when an answered incoming call needs its disposition screen opened.
     * Collected by AppNavGraph while the UI is alive; a cold start instead routes
     * via the persisted pending incoming (written before emitting). The screen
     * reads the call's details from prefs, so no payload is needed here.
     */
    private val _dispositionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val dispositionRequests: SharedFlow<Unit> = _dispositionRequests.asSharedFlow()

    // ---- Ring-cycle state (one telephony session at a time) ----
    // Guarded by synchronized(this): broadcasts arrive on the main thread but the
    // state is also reset from onIdle before handing off to the IO coroutine.
    private var ringing = false
    private var number: String? = null
    private var ringStartMs = 0L
    private var offhookAtMs = 0L
    private var answered = false

    private data class Ended(
        val number: String?,
        val ringStartMs: Long,
        val offhookAtMs: Long,
        val idleAtMs: Long,
        val answered: Boolean,
    )

    /**
     * RINGING fired. On API 28+ the system sends the broadcast twice — once with
     * EXTRA_INCOMING_NUMBER (apps holding READ_CALL_LOG) and once without — so the
     * number is captured from whichever copy carries it, but the ring-start clock
     * only starts on the first.
     */
    @Synchronized
    fun onRinging(rawNumber: String?) {
        if (!ringing) {
            ringing = true
            answered = false
            number = null
            ringStartMs = System.currentTimeMillis()
            offhookAtMs = 0L
        }
        if (!rawNumber.isNullOrBlank()) number = rawNumber
    }

    /** OFFHOOK during a ring = the agent picked up. (OFFHOOK without a preceding RINGING is an outgoing call — ignored.) */
    @Synchronized
    fun onOffhook() {
        if (ringing && !answered) {
            answered = true
            offhookAtMs = System.currentTimeMillis()
        }
    }

    /**
     * IDLE = the call ended (answered) or stopped ringing (missed/rejected).
     * Handles the report on an IO coroutine; [onFinished] releases the receiver's
     * goAsync() once done. Called for every IDLE — a no-op unless a ring cycle is
     * actually open.
     */
    fun onIdle(onFinished: () -> Unit) {
        val ended: Ended? = synchronized(this) {
            val snapshot = if (ringing) {
                Ended(number, ringStartMs, offhookAtMs, System.currentTimeMillis(), answered)
            } else {
                null
            }
            ringing = false
            number = null
            ringStartMs = 0L
            offhookAtMs = 0L
            answered = false
            snapshot
        }
        if (ended == null) {
            onFinished()
            return
        }
        scope.launch {
            try {
                handleEnded(ended)
            } catch (e: Exception) {
                Log.e(TAG, "incoming-call handling failed", e)
            } finally {
                onFinished()
            }
        }
    }

    private suspend fun handleEnded(ended: Ended) {
        val raw = ended.number
        if (raw.isNullOrBlank()) {
            Log.w(TAG, "incoming call ended but no caller number (READ_CALL_LOG denied?) — dropped")
            return
        }
        // Only report for a logged-in agent…
        if (tokenStore.getAccessToken().isNullOrBlank()) {
            Log.d(TAG, "incoming call ignored — not logged in")
            return
        }
        // …whose dial mode is SIM (unknown after a process restart → let the
        // backend decide; it rejects non-SIM agents).
        val mode = agentRepo.agent.value?.callMode
        if (mode != null && mode != CallRouteType.SIM) {
            Log.d(TAG, "incoming call ignored — agent call_mode=$mode is not SIM")
            return
        }

        // Backend format is digits-only E.164 (e.g. "919876543210").
        val fromNumber = PhoneNumbers.toWhatsApp(raw)
        if (fromNumber.isBlank()) return

        // True talk seconds from the call log. The log row can land a moment
        // after IDLE, so retry briefly; fall back to OFFHOOK→IDLE elapsed time.
        var talkSeconds: Int? = null
        if (ended.answered) {
            for (attempt in 0 until 3) {
                talkSeconds = CallLogReader.latestIncomingEntry(context, raw, ended.ringStartMs)
                    ?.durationSec
                if (talkSeconds != null) break
                delay(1_200)
            }
            if (talkSeconds == null && ended.offhookAtMs > 0) {
                talkSeconds = ((ended.idleAtMs - ended.offhookAtMs) / 1000L).toInt()
            }
        }

        val startedAtIso = OffsetDateTime
            .ofInstant(Instant.ofEpochMilli(ended.ringStartMs), ZoneId.systemDefault())
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        // Never picked up → not recorded AT ALL (no POST, no lead, no history,
        // no calls-today count). Product decision (2026-07-15): an unanswered
        // incoming call isn't business data; only answered calls reach the
        // backend (as completed on submit, or no_answer via the cancel cross).
        if (!ended.answered) {
            Log.d(TAG, "incoming call from …${fromNumber.takeLast(4)} missed — not recorded")
            return
        }

        // Never hijack a call that's still awaiting its disposition: an open
        // in-app call or a persisted pending disposition keeps ownership of the
        // disposition screen, so this one can't collect an outcome. It's a real
        // answered call though, so report it as completed and log it only.
        val prefs = appPrefs.current()
        val owedElsewhere = callsRepo.hasOpenCall() ||
            prefs.pendingDispositionCall.isNotBlank() ||
            prefs.pendingIncomingNumber.isNotBlank()
        if (owedElsewhere) {
            callsRepo.reportSimIncoming(
                fromNumber = fromNumber,
                status = "completed",
                startedAtIso = startedAtIso,
                durationSec = talkSeconds,
                adopt = false,
            )
            Log.d(TAG, "incoming call logged only — another disposition is already owed")
            return
        }

        // Answered and nothing else owed: hold it locally and let the agent decide.
        // Persisted FIRST so even an immediate app kill still routes back to the
        // disposition on relaunch (same guarantee as app-dialed calls); the POST
        // happens there, once, as completed (submit) or no_answer (cancel).
        appPrefs.setPendingIncoming(fromNumber, startedAtIso, talkSeconds ?: 0)
        IncomingCallNotifications.post(context, callerLabel = PhoneNumbers.toDialable(raw))
        _dispositionRequests.tryEmit(Unit)
        Log.d(TAG, "incoming call answered (${talkSeconds ?: 0}s) → disposition pending")
    }

    private companion object {
        const val TAG = "Bol7Incoming"
    }
}
