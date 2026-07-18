package com.example.callcenter.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.CallDto
import com.example.callcenter.data.remote.dto.ClickToCallRequest
import com.example.callcenter.data.remote.dto.HangupRequest
import com.example.callcenter.data.remote.dto.StartCallRequest
import com.example.callcenter.data.remote.dto.UpdateCallRequest
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.domain.model.Call
import com.example.callcenter.domain.model.CallDirection
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.Lead
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody

@Singleton
class CallsRepository @Inject constructor(
    private val dialerApi: DialerApi,
    private val json: Json,
    private val agentRepo: AgentRepository,
    @ApplicationContext private val context: Context,
) {

    private val _active = MutableStateFlow<Call?>(null)
    val active: StateFlow<Call?> = _active.asStateFlow()

    /**
     * Agents may only place calls while Available. Every call entry point (manual
     * dial, auto-dial start, auto-dial advance) checks this first so no call ever
     * starts on Break/Busy/Offline.
     */
    fun canCall(): Boolean = agentRepo.agent.value?.status == AgentStatus.AVAILABLE

    /**
     * True while there's a call that hasn't been dispositioned yet. `_active` is set
     * on [initiate] and only cleared by [clearActive] (which runs after the agent
     * submits the disposition), so a non-null active call means the previous call is
     * still "open". Every manual call-start point checks this to block placing a new
     * call before the current one is wrapped up — no spam-dialing, no orphaned calls.
     */
    fun hasOpenCall(): Boolean = _active.value != null

    // Backend integer id of the active call, for PATCH/hangup. Null if start failed.
    private var activeCallId: Int? = null

    // The instant the SIM carrier call actually ENDED (OFFHOOK → IDLE), stamped
    // by the call screen the moment its CallStateWatcher fires. Used at hangup
    // to derive the true pickup time: answered_at = end − call-log talk seconds.
    // Null after a process death → answered_at is simply not sent (backend
    // accepts that; it no longer guesses).
    @Volatile private var simEndedAtMs: Long? = null

    /** Stamp "the carrier call just ended" — see [simEndedAtMs]. */
    fun markSimCallEnded() {
        simEndedAtMs = System.currentTimeMillis()
    }

    // Facts the SIP waiting screen observed about the click-to-call agent leg
    // (real telephony transitions on THIS phone). They feed hangup() the same
    // honesty the SIM path gets from the call log: true talk seconds instead of
    // a backend-computed duration that includes disposition-screen time, and
    // no_answer instead of a fabricated "completed" when the leg was never
    // picked up. Reset in initiate()/clearActive() like simEndedAtMs.
    @Volatile private var sipTalkSeconds: Int? = null
    @Volatile private var sipNeverAnswered = false

    /** The SIP agent leg was answered and then ended: real pickup→hangup seconds. */
    fun markSipTalkSeconds(seconds: Int) {
        sipTalkSeconds = seconds.coerceAtLeast(0)
        sipNeverAnswered = false
    }

    /** The SIP agent leg rang on this phone but was never picked up. */
    fun markSipNeverAnswered() {
        sipNeverAnswered = true
    }

    /**
     * Re-hydrate the active-call id after a process death. The disposition screen,
     * when restored on relaunch, calls this with the persisted call id so a submitted
     * outcome actually reaches the backend (hangup PATCH) instead of only recording
     * locally. No-op if the id is blank or a local-only fallback (non-numeric).
     */
    fun restoreActiveCall(callId: String) {
        val id = callId.toIntOrNull() ?: return
        if (activeCallId == null) {
            activeCallId = id
            Log.d(TAG, "restored activeCallId=$id from persisted pending disposition")
        }
    }

    private val _history = MutableStateFlow<List<Call>>(emptyList())

    fun observeHistory(): Flow<List<Call>> = _history

    // ---- Auto-dial session ----
    // When the agent taps "Start auto-dial", a session holds the queued lead ids,
    // the chosen route (SIP/SIM), and a cursor. After each disposition the UI calls
    // advanceAutoDialAfter() to advance to the next lead automatically.
    private val _autoDial = MutableStateFlow<AutoDialSession?>(null)
    val autoDial: StateFlow<AutoDialSession?> = _autoDial.asStateFlow()

    data class AutoDialSession(
        val leadIds: List<Int>,
        val route: CallRouteType,
        val index: Int = 0,
    ) {
        val active: Boolean get() = index < leadIds.size
        val remaining: Int get() = (leadIds.size - index).coerceAtLeast(0)
    }

    /** Begin an auto-dial session over the given leads with the chosen route. */
    fun startAutoDial(leadIds: List<Int>, route: CallRouteType) {
        _autoDial.value = if (leadIds.isEmpty()) null
        else AutoDialSession(leadIds = leadIds, route = route)
    }

    /**
     * Advance the auto-dial cursor to the next lead, but ONLY if [justDialedLeadId]
     * is the session's current lead — i.e. the disposition that just finished
     * really belongs to this auto-dial loop. A manual one-off call (whose lead
     * isn't the session's current cursor) returns null and leaves the session
     * untouched, so it can't hijack the call into the queue.
     *
     * Returns the next lead id to dial, or null if this wasn't an auto-dial call
     * or the queue is now drained (in which case the session is cleared).
     */
    @Synchronized
    fun advanceAutoDialAfter(justDialedLeadId: Int): Int? {
        val session = _autoDial.value ?: return null
        if (!session.active || session.leadIds[session.index] != justDialedLeadId) {
            // Disposition belongs to a manual call, not this session — don't move.
            return null
        }
        val next = session.copy(index = session.index + 1)
        return if (next.active) {
            _autoDial.value = next
            next.leadIds[next.index]
        } else {
            _autoDial.value = null
            null
        }
    }

    /** The route for the running session, defaulting to SIP. */
    fun autoDialRoute(): CallRouteType = _autoDial.value?.route ?: CallRouteType.SIP

    fun stopAutoDial() {
        _autoDial.value = null
    }

    /** Refresh call history from the backend. */
    suspend fun refreshHistory(): Result<Unit> = try {
        val element = dialerApi.listCalls()
        val dtos = parseCallList(element)
        Log.d(TAG, "calls/ ← ${dtos.size} calls")
        _history.value = dtos.mapNotNull { it.toCall() }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "calls/ failed", e)
        Result.failure(e)
    }

    /**
     * Start a call. POSTs metadata to the backend; the returned integer id is
     * stored for later PATCH/hangup. Falls back to a local-only Call if the
     * backend call fails, so the in-call UI still works (Path B: app drives SIP).
     */
    suspend fun initiate(lead: Lead, route: CallRouteType): Call {
        // Stale facts from the previous call must never feed this one's
        // answered_at / duration / status derivation.
        simEndedAtMs = null
        sipTalkSeconds = null
        sipNeverAnswered = false
        val local = Call(
            id = "local-${lead.id}",
            leadId = lead.id,
            leadName = lead.name,
            leadPhone = lead.phone,
            campaignName = lead.campaignName,
            status = CallStatus.INITIATING,
            routeType = route,
            startedAt = LocalDateTime.now(),
        )
        return try {
            val request = StartCallRequest(
                leadId = lead.id,
                toNumber = lead.phone.takeIf { it.isNotBlank() },
                direction = "outbound",
            )
            // Each dial mode has its own endpoint:
            //  - SIM  → sim-dial/ (logs the row, then the app fires ACTION_CALL),
            //  - SIP/VOIP → click-to-call/ (server-side originate: calls the
            //    agent's phone first, then bridges the lead in).
            // click-to-call sends from_number as an EMPTY string — the key must
            // be on the wire but the value is blank so the backend fills in the
            // agent's provisioned caller id itself (product decision 2026-07-17;
            // the body shape is exactly {to_number, from_number, note}).
            // to_number is the RAW lead.phone — this is a server originate, not a
            // device tel: intent, so it is NOT run through PhoneNumbers.toDialable
            // (the working curl sends a bare number and the gateway resolves it).
            val dto = when (route) {
                CallRouteType.SIM -> dialerApi.simDial(request)
                else -> parseClickToCall(
                    dialerApi.clickToCall(
                        ClickToCallRequest(
                            toNumber = lead.phone.takeIf { it.isNotBlank() },
                            fromNumber = "",
                            // No note yet at originate; the real one is PATCHed at
                            // disposition. Blank keeps the key present in the body.
                            note = "",
                        ),
                    ),
                )
            }
            activeCallId = dto.id
            Log.d(TAG, "calls/${if (route == CallRouteType.SIM) "sim-dial" else "click-to-call"} ← id=${dto.id} rpdp_uid=${dto.rpdpUid}")
            val call = local.copy(id = dto.id?.toString() ?: local.id)
            _active.value = call
            call
        } catch (e: Exception) {
            // Surface the backend's validation complaint (e.g. a 422's field
            // errors) directly in this tag — otherwise it only exists inside
            // the okhttp wire log and failures are undiagnosable from here.
            val errorBody = (e as? retrofit2.HttpException)
                ?.response()?.errorBody()?.let { body -> runCatching { body.string() }.getOrNull() }
                ?.take(600)
            Log.e(TAG, "calls/ start failed — running call locally" + (errorBody?.let { " ← $it" } ?: ""), e)
            activeCallId = null
            _active.value = local
            local
        }
    }

    fun updateActive(call: Call) {
        _active.value = call
    }

    /**
     * Report an inbound carrier (SIM) call to the backend AFTER it ended. The
     * backend finds-or-creates a Lead from [fromNumber] and returns the Call row.
     *
     * When [adopt] is true (answered call, no other call awaiting disposition)
     * the returned call becomes the active one — exactly like [initiate] — so the
     * disposition screen's normal submit path (recording upload + hangup PATCH)
     * targets this backend row. Returns null if the POST fails or the response
     * has no id (nothing to disposition against).
     */
    suspend fun reportSimIncoming(
        fromNumber: String,
        status: String,
        startedAtIso: String,
        durationSec: Int?,
        adopt: Boolean,
    ): Call? = try {
        val dto = dialerApi.simIncoming(
            com.example.callcenter.data.remote.dto.SimIncomingRequest(
                fromNumber = fromNumber,
                status = status,
                startedAt = startedAtIso,
                durationSeconds = durationSec,
            ),
        )
        Log.d(TAG, "calls/sim-incoming ← id=${dto.id} lead=${dto.resolvedLeadId} status=${dto.status}")
        val mapped = dto.toCall()?.copy(
            direction = CallDirection.INCOMING,
            routeType = CallRouteType.SIM,
            // Keep the call-log talk time we just reported: hangup() reuses it as
            // duration_seconds so the backend doesn't recompute the duration from
            // ended_at (which would include disposition-screen time).
            durationSec = durationSec ?: dto.durationSeconds ?: 0,
        )
        // toCall() derives its name fallback from the RESPONSE's direction field,
        // which the backend may omit — re-apply the inbound naming here.
        val call = mapped?.let { if (it.leadName == "Unknown") it.copy(leadName = "Incoming") else it }
        if (call != null) {
            // Only ANSWERED business calls surface in the app's local history —
            // a cancelled (personal) call is reported to the backend as no_answer
            // but stays out of the agent's in-app lists.
            if (status == "completed") {
                _history.update { listOf(call) + it.filter { c -> c.id != call.id } }
            }
            if (adopt) {
                activeCallId = dto.id
                _active.value = call
            }
        }
        call
    } catch (e: Exception) {
        Log.e(TAG, "calls/sim-incoming failed", e)
        null
    }

    /** PATCH an in-call state transition (best-effort; no-op without a backend id). */
    suspend fun patchCall(
        status: CallStatus? = null,
        muted: Boolean? = null,
        onHold: Boolean? = null,
        recording: Boolean? = null,
        note: String? = null,
    ) {
        val id = activeCallId ?: return
        try {
            dialerApi.updateCall(
                id,
                UpdateCallRequest(
                    status = status?.toApi(),
                    isMuted = muted,
                    isOnHold = onHold,
                    isRecorded = recording,
                    note = note,
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "calls/$id PATCH failed (ignored)", e)
        }
    }

    /**
     * @return true when the device call log CONFIRMED zero talk seconds — the
     * callee never picked up, so the caller can undo any lead re-bucketing the
     * backend derives from the disposition (a 0-second attempt is not a contact).
     */
    suspend fun hangup(disposition: Disposition? = null, note: String? = null): Boolean {
        val id = activeCallId
        val active = _active.value
        // For SIM calls, read the TRUE talk seconds from the device call log
        // (0 for busy/missed). Telephony state can't detect pickup, so this is
        // the only accurate source. Null when unavailable → backend falls back.
        // Run the ContentResolver query on IO — it must NOT block the main thread.
        // Kept separate from realDuration: a CONFIRMED 0 (log entry found, nobody
        // talked) downgrades the status below; "no entry found" (null) must not.
        val outgoingLogDuration = active
            ?.takeIf { it.routeType == CallRouteType.SIM && it.direction != CallDirection.INCOMING }
            ?.let { call ->
                val startedMs = call.startedAt
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                withContext(Dispatchers.IO) {
                    com.example.callcenter.telephony.CallLogReader
                        .latestOutgoingDuration(context, call.leadPhone, startedMs)?.durationSec
                }
            }
        val realDuration = if (active?.direction == CallDirection.INCOMING) {
            // Incoming SIM call: the true talk time was already read from the
            // call log when the call ended (reportSimIncoming) — reuse it.
            active.durationSec.takeIf { it > 0 }
        } else {
            // Outgoing: SIM reads the call log; SIP uses the waiting screen's
            // observed pickup→hangup seconds of the agent leg (null when the
            // watcher couldn't run — backend falls back to its own computation).
            outgoingLogDuration ?: sipTalkSeconds
        }
        // Confirmed "never picked up" (agent cut it / busy / declined). Report
        // status=no_answer, not "completed": the backend stamps answered_at
        // and computes duration as ended_at − answered_at (which includes
        // disposition-screen time), so a "completed" here would fabricate talk
        // time and make a never-answered call count as Connected in Reports.
        // SIM: the call log confirmed 0 talk seconds. SIP: the waiting screen
        // watched the agent leg ring out without a pickup.
        val neverAnswered = outgoingLogDuration == 0 || sipNeverAnswered
        val completedLocally = active?.copy(
            status = if (neverAnswered) CallStatus.NO_ANSWER else CallStatus.COMPLETED,
            endedAt = LocalDateTime.now(),
            disposition = disposition,
            note = note,
        )
        if (id != null) {
            // Backend contract 2026-07-16: the server no longer guesses
            // answered_at for SIM calls — PATCH the real pickup time, derived as
            // (call end − true talk seconds). Best-effort and only when a pickup
            // is CONFIRMED (talk > 0) and we saw the call actually end this
            // process (simEndedAtMs survives neither restarts nor SIP calls).
            val talk = outgoingLogDuration ?: 0
            val endMs = simEndedAtMs
            if (talk > 0 && endMs != null) {
                val answeredAtIso = java.time.OffsetDateTime
                    .ofInstant(java.time.Instant.ofEpochMilli(endMs - talk * 1000L), ZoneId.systemDefault())
                    .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                try {
                    dialerApi.updateCall(id, UpdateCallRequest(answeredAt = answeredAtIso))
                    Log.d(TAG, "calls/$id answered_at=$answeredAtIso PATCHed (end−${talk}s)")
                } catch (e: Exception) {
                    Log.w(TAG, "calls/$id answered_at PATCH failed (ignored)", e)
                }
            }
            try {
                // status/duration go on the wire for SIM only (device call-log
                // truth). SIP omits both — the backend/provider CDR owns them
                // (user decision 2026-07-18); its body is just {disposition, note?}.
                val isSim = active?.routeType == CallRouteType.SIM
                val sentStatus = if (!isSim) null else if (neverAnswered) "no_answer" else "completed"
                val sentDuration = if (isSim) realDuration else null
                val dto = dialerApi.hangupCall(
                    id,
                    HangupRequest(
                        disposition = disposition?.toApi(),
                        note = note,
                        status = sentStatus,
                        durationSeconds = sentDuration,
                    ),
                )
                Log.d(
                    TAG,
                    "calls/$id hangup → sent{disposition=${disposition?.toApi()}, " +
                        "status=${sentStatus ?: "OMITTED"}, duration=${sentDuration ?: "OMITTED"}} " +
                        "← server{status=${dto.status}, dur=${dto.durationSeconds}}",
                )
                val merged = dto.toCall() ?: completedLocally
                _active.value = merged
                if (merged != null) _history.update { listOf(merged) + it.filter { c -> c.id != merged.id } }
                return neverAnswered
            } catch (e: Exception) {
                Log.e(TAG, "calls/$id hangup failed — recording locally", e)
            }
        }
        // Fallback: no backend id or the call failed — keep a local record.
        _active.value = completedLocally
        if (completedLocally != null) _history.update { listOf(completedLocally) + it }
        return neverAnswered
    }

    /** True once there's a backend call id to attach a recording to. */
    fun hasActiveCallId(): Boolean = activeCallId != null

    /**
     * Upload an agent-picked recording file (content:// uri) for the active call.
     * Must be called BEFORE clearActive() (it uses the active call id). Reads the
     * file bytes via ContentResolver and POSTs them as multipart.
     */
    suspend fun uploadRecording(uri: Uri): Result<Unit> {
        val id = activeCallId
            ?: return Result.failure(IllegalStateException("No active call to attach a recording to."))
        return try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "audio/*"
            val fileName = queryDisplayName(uri) ?: "recording"
            // Stream the file straight from the content resolver instead of buffering
            // the whole thing into a ByteArray — a ~100 MiB recording would OOM on
            // low-RAM devices. The body opens a fresh InputStream when OkHttp writes it.
            val body = ContentUriRequestBody(context, uri, mime)
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            dialerApi.uploadRecording(id, part)
            Log.d(TAG, "calls/$id recording uploaded ($fileName)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "calls/$id recording upload failed", e)
            Result.failure(e)
        }
    }

    /** Resolve a content:// uri's display name for the multipart filename. */
    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }

    fun clearActive() {
        _active.value = null
        activeCallId = null
        simEndedAtMs = null
        sipTalkSeconds = null
        sipNeverAnswered = false
    }

    // --- mapping helpers ---

    /**
     * Parse the click-to-call response in BOTH known shapes:
     *  - since 2026-07-17: {"call": {...Call row...}, "provider": {success, message, ref_id}}
     *  - original:         the bare Call row object
     * The wrapped shape silently broke id extraction (id=null → every later
     * PATCH/hangup for the call was skipped, so dispositions never reached the
     * backend and leads never re-bucketed).
     */
    private fun parseClickToCall(element: JsonElement): CallDto {
        val obj = element as? JsonObject
        val callObj = obj?.get("call") as? JsonObject
        (obj?.get("provider") as? JsonObject)?.let { provider ->
            Log.d(TAG, "click-to-call provider ← $provider")
        }
        return json.decodeFromJsonElement(CallDto.serializer(), callObj ?: element)
    }

    private fun parseCallList(element: JsonElement): List<CallDto> = when (element) {
        is JsonArray -> json.decodeFromJsonElement(ListSerializer(CallDto.serializer()), element)
        is JsonObject -> {
            val results = element["results"]
            if (results is JsonArray) {
                json.decodeFromJsonElement(ListSerializer(CallDto.serializer()), results)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    private fun CallDto.toCall(): Call? {
        val callId = id ?: return null
        val dir = directionFromApi(direction)
        return Call(
            id = callId.toString(),
            leadId = resolvedLeadId ?: 0,
            // A nameless inbound caller reads "Incoming" (they're not a known
            // lead yet); "Unknown" is kept for nameless outbound rows.
            leadName = resolvedName ?: if (dir == CallDirection.INCOMING) "Incoming" else "Unknown",
            leadPhone = resolvedPhone.orEmpty(),
            campaignName = campaignName.orEmpty(),
            direction = dir,
            status = statusFromApi(status),
            // The server reports how the call was carried (`mode`) — show that,
            // not a hardcoded SIP badge on a SIM agent's history.
            routeType = when (mode?.lowercase()?.trim()) {
                "sim" -> CallRouteType.SIM
                "pstn" -> CallRouteType.PSTN
                else -> CallRouteType.SIP
            },
            startedAt = parseDate(startedAt ?: createdAt) ?: LocalDateTime.now(),
            endedAt = parseDate(endedAt),
            durationSec = durationSeconds ?: 0,
            recordingUrl = recordingUrl,
            // A recording exists if there's a URL, OR a non-"none" source, OR a
            // stored size — device uploads report source+size but a blank URL.
            hasRecording = !recordingUrl.isNullOrBlank() ||
                (recordingSource != null && !recordingSource.equals("none", ignoreCase = true)) ||
                (recordingSizeBytes != null && recordingSizeBytes > 0),
            disposition = dispositionFromApi(disposition),
            note = note,
        )
    }

    private fun directionFromApi(d: String?): CallDirection = when (d?.lowercase()) {
        "inbound", "incoming" -> CallDirection.INCOMING
        else -> CallDirection.OUTGOING
    }

    private fun statusFromApi(s: String?): CallStatus = when (s?.lowercase()) {
        "initiating" -> CallStatus.INITIATING
        "ringing" -> CallStatus.RINGING
        "connected" -> CallStatus.CONNECTED
        "on_hold", "on-hold", "hold" -> CallStatus.ON_HOLD
        "completed" -> CallStatus.COMPLETED
        "failed" -> CallStatus.FAILED
        "no_answer", "no-answer", "noanswer" -> CallStatus.NO_ANSWER
        "busy" -> CallStatus.BUSY
        else -> CallStatus.COMPLETED
    }

    private fun CallStatus.toApi(): String = when (this) {
        CallStatus.INITIATING -> "initiating"
        CallStatus.RINGING -> "ringing"
        CallStatus.CONNECTED -> "connected"
        CallStatus.ON_HOLD -> "on_hold"
        CallStatus.COMPLETED -> "completed"
        CallStatus.FAILED -> "failed"
        CallStatus.NO_ANSWER -> "no_answer"
        CallStatus.BUSY -> "busy"
    }

    private fun dispositionFromApi(d: String?): Disposition? = when (d?.lowercase()) {
        null, "" -> null
        "interested" -> Disposition.INTERESTED
        "not_interested", "not-interested" -> Disposition.NOT_INTERESTED
        "follow_up", "follow-up", "followup" -> Disposition.FOLLOW_UP
        "no_answer", "no-answer" -> Disposition.NO_ANSWER
        "busy" -> Disposition.BUSY
        "wrong_number", "wrong-number" -> Disposition.WRONG_NUMBER
        "converted" -> Disposition.CONVERTED
        "closed" -> Disposition.CLOSED
        else -> null
    }

    private fun Disposition.toApi(): String = when (this) {
        Disposition.INTERESTED -> "interested"
        Disposition.NOT_INTERESTED -> "not_interested"
        Disposition.FOLLOW_UP -> "follow_up"
        Disposition.NO_ANSWER -> "no_answer"
        Disposition.BUSY -> "busy"
        Disposition.WRONG_NUMBER -> "wrong_number"
        Disposition.CONVERTED -> "converted"
        Disposition.CLOSED -> "closed"
    }

    private fun parseDate(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            // Backend timestamps are UTC (…Z). Convert the *instant* to the device's
            // zone so 08:20Z shows as 13:50 IST — not as a raw 08:20 wall-clock.
            // toLocalDateTime() alone would drop the offset without converting.
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private companion object {
        const val TAG = "Bol7Calls"
    }
}
