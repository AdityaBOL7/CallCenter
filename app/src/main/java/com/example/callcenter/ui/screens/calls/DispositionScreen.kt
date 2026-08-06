package com.example.callcenter.ui.screens.calls

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneMissed
import androidx.compose.material.icons.outlined.PhonePaused
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Whatsapp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.CallbacksRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.DispositionsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.DispositionCode
import com.example.callcenter.ui.components.DateTimeField
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.components.UriSaver
import com.example.callcenter.ui.components.LocalDateTimeSaver
import com.example.callcenter.ui.components.colorForDisposition
import com.example.callcenter.ui.theme.AccentRose
import com.example.callcenter.ui.theme.AccentViolet
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.Success
import com.example.callcenter.ui.theme.Warn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DispositionViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val callsRepo: CallsRepository,
    private val leadsRepo: LeadsRepository,
    private val callbacksRepo: CallbacksRepository,
    private val dispositionsRepo: DispositionsRepository,
    private val agentRepo: AgentRepository,
    private val appPrefs: com.example.callcenter.data.prefs.AppPreferences,
) : ViewModel() {

    val codes: StateFlow<List<DispositionCode>> = dispositionsRepo.codes

    // True while a submit() is in flight, so the UI can disable the button and a
    // double-tap can't launch a second submission (which would skip a lead).
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    // The lead being dispositioned — shown in the identity card + quick actions.
    private val _lead = MutableStateFlow<com.example.callcenter.domain.model.Lead?>(null)
    val lead: StateFlow<com.example.callcenter.domain.model.Lead?> = _lead.asStateFlow()

    // Which WhatsApp app the quick-action opens ("whatsapp" | "business").
    private val _whatsappTarget = MutableStateFlow("whatsapp")
    val whatsappTarget: StateFlow<String> = _whatsappTarget.asStateFlow()

    // Auto-found recording (uri + display name) from the Settings folder, or null.
    private val _autoRecording = MutableStateFlow<com.example.callcenter.telephony.RecordingFinder.Found?>(null)
    val autoRecording: StateFlow<com.example.callcenter.telephony.RecordingFinder.Found?> = _autoRecording.asStateFlow()

    /**
     * Device-recording attach is SIM-ONLY. SIP calls are recorded centrally by
     * the provider (Tata/Smartflo panel) — the phone never carries their audio,
     * so showing the attach section there is meaningless and the auto-attach
     * could even grab an unrelated device recording (user decision 2026-07-18).
     * Unknown route (active call lost to a process death) keeps it visible:
     * harmless for SIP, still needed for SIM.
     */
    val showRecordingAttach: Boolean
        get() = callsRepo.active.value?.routeType != com.example.callcenter.domain.model.CallRouteType.SIP

    /**
     * An answered incoming SIM call that has NOT been reported to the backend yet
     * (see AppPreferences.pendingIncomingNumber). Non-null only in incoming mode;
     * its presence is what makes this screen cancellable and makes submitting POST
     * calls/sim-incoming/ first.
     */
    data class IncomingInfo(val number: String, val startedAtIso: String, val durationSec: Int)

    private val _incoming = MutableStateFlow<IncomingInfo?>(null)
    val incoming: StateFlow<IncomingInfo?> = _incoming.asStateFlow()

    init {
        viewModelScope.launch { dispositionsRepo.refresh() }
        viewModelScope.launch {
            val prefs = appPrefs.current()
            _whatsappTarget.value = prefs.whatsappTarget
        }
        viewModelScope.launch { autoFindRecording() }
    }

    /** Load the held incoming call (incoming mode only). */
    fun loadIncoming() {
        viewModelScope.launch {
            val prefs = appPrefs.current()
            _incoming.value = prefs.pendingIncomingNumber.takeIf { it.isNotBlank() }?.let {
                IncomingInfo(
                    number = it,
                    startedAtIso = prefs.pendingIncomingStartedAt,
                    durationSec = prefs.pendingIncomingDuration,
                )
            }
            // The screen is open — the "log the outcome" nudge has done its job.
            com.example.callcenter.notifications.IncomingCallNotifications.cancel(appContext)
        }
    }

    /**
     * Not a business call (a personal call the agent had to pick up) — report it
     * as `no_answer` with no duration and leave without an outcome. Best-effort:
     * a failed POST still releases the agent, since demanding a disposition for
     * someone's private call is never right.
     */
    fun cancelIncoming(onDone: () -> Unit) {
        if (_submitting.value) return
        _submitting.value = true
        viewModelScope.launch {
            try {
                _incoming.value?.let { info ->
                    callsRepo.reportSimIncoming(
                        fromNumber = info.number,
                        status = "no_answer",
                        startedAtIso = info.startedAtIso,
                        durationSec = null,
                        adopt = false,
                    )
                }
                appPrefs.clearPendingIncoming()
                _incoming.value = null
                com.example.callcenter.notifications.IncomingCallNotifications.cancel(appContext)
                onDone()
            } finally {
                _submitting.value = false
            }
        }
    }

    /**
     * Create the backend row for a held incoming call, now that the agent has an
     * outcome to record: POST sim-incoming as `completed` with the true talk
     * seconds. Returns the backend lead id to disposition against, or null if the
     * POST failed (the call stays held so the agent can retry).
     */
    private suspend fun reportIncomingAsCompleted(): Int? {
        val info = _incoming.value ?: return null
        val call = callsRepo.reportSimIncoming(
            fromNumber = info.number,
            status = "completed",
            startedAtIso = info.startedAtIso,
            durationSec = info.durationSec.takeIf { it > 0 },
            adopt = true,
        )
        if (call == null) {
            _error.value = "Couldn't log this call. Check your connection and try again."
            return null
        }
        // Hand the accountability over to the normal pending-disposition record:
        // the row now exists, so an app-kill before the outcome lands routes back
        // to the standard disposition screen for this call.
        appPrefs.setPendingDisposition(call.id, call.leadId)
        appPrefs.clearPendingIncoming()
        _incoming.value = null
        com.example.callcenter.notifications.IncomingCallNotifications.cancel(appContext)
        return call.leadId
    }

    /**
     * Scan the configured recording folder for the newest audio file since the call
     * started and publish it so the disposition screen can auto-attach it. No-op if
     * no folder is set or nothing matches (the agent can still Browse manually).
     */
    private suspend fun autoFindRecording() {
        // SIP: provider records centrally — never scan/auto-attach a device file.
        if (!showRecordingAttach) return
        val prefs = appPrefs.current()
        val folder = prefs.recordingFolderUri
        if (folder.isBlank()) return
        // Bound the scan to files from this call onward; if the active call is gone
        // (restored after a kill), fall back to the last few minutes. An incoming
        // call has no active-call row yet, so use its recorded ring time.
        val startedAt = callsRepo.active.value?.startedAt
        val incomingStart = prefs.pendingIncomingStartedAt.takeIf { it.isNotBlank() }?.let {
            try {
                java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()
            } catch (_: java.time.format.DateTimeParseException) {
                null
            }
        }
        val sinceMillis = startedAt
            ?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: incomingStart
            ?: (System.currentTimeMillis() - 10 * 60 * 1000L)
        _autoRecording.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.callcenter.telephony.RecordingFinder.findLatest(appContext, folder, sinceMillis)
        }
    }

    /** Load the lead for the identity card / quick-action buttons. */
    fun loadLead(leadId: Int) {
        viewModelScope.launch { _lead.value = leadsRepo.byId(leadId) }
    }

    /**
     * Remember this disposition as "owed" so that if the app is killed/backgrounded
     * on this screen, relaunch routes back here — the agent can't abandon a call by
     * force-closing the app. Cleared in [submit] once the outcome is recorded.
     */
    fun markPending(callId: String, leadId: Int) {
        // Re-hydrate the backend call id in case we were restored after an app-kill,
        // so submitting actually PATCHes the server (not just a local record).
        callsRepo.restoreActiveCall(callId)
        viewModelScope.launch { appPrefs.setPendingDisposition(callId, leadId) }
        // The screen is open — the incoming-call "log the outcome" nudge is done.
        com.example.callcenter.notifications.IncomingCallNotifications.cancel(appContext)
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() { _error.value = null }

    /** What to do once the outcome is recorded — the three buttons differ here. */
    private enum class After { NEXT_OR_HOME, REDIAL, PAUSE }

    /**
     * Submit the call outcome, then advance the auto-dial queue if one is running
     * (else return home). Recording upload is OPTIONAL but uploaded FIRST — a
     * failed upload aborts so the attached file isn't lost.
     */
    fun submit(
        leadId: Int, disposition: DispositionCode, note: String, callbackAt: LocalDateTime?,
        recordingUri: Uri?,
        onDone: () -> Unit, onNextCall: (leadId: Int, callId: String, route: String) -> Unit,
    ) = finalize(After.NEXT_OR_HOME, leadId, disposition, note, callbackAt, recordingUri, onDone, onNextCall)

    /**
     * Submit the outcome, then immediately re-dial the SAME lead (for a
     * no-answer/busy the agent wants to retry). Routes to the call screen via
     * [onNextCall]; stops the auto-dial loop so the retry is a clean one-off.
     */
    fun submitAndRedial(
        leadId: Int, disposition: DispositionCode, note: String, callbackAt: LocalDateTime?,
        recordingUri: Uri?,
        onDone: () -> Unit, onNextCall: (leadId: Int, callId: String, route: String) -> Unit,
    ) = finalize(After.REDIAL, leadId, disposition, note, callbackAt, recordingUri, onDone, onNextCall)

    /**
     * Submit the outcome, stop any auto-dial loop, and go home — for when the
     * agent needs to step away (washroom / urgent task). Their status is left
     * untouched; going on Break is a manual choice from the home screen.
     */
    fun submitAndPause(
        leadId: Int, disposition: DispositionCode, note: String, callbackAt: LocalDateTime?,
        recordingUri: Uri?,
        onDone: () -> Unit, onNextCall: (leadId: Int, callId: String, route: String) -> Unit,
    ) = finalize(After.PAUSE, leadId, disposition, note, callbackAt, recordingUri, onDone, onNextCall)

    private fun finalize(
        after: After,
        leadId: Int,
        disposition: DispositionCode,
        note: String,
        callbackAt: LocalDateTime?,
        recordingUri: Uri?,
        onDone: () -> Unit,
        onNextCall: (leadId: Int, callId: String, route: String) -> Unit,
    ) {
        // Reentrancy guard: ignore taps while a submission is already running.
        if (_submitting.value) return
        _submitting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                // Incoming mode: the call has no backend row yet (the POST is held
                // until the agent decides). Create it as `completed` now — this is
                // the only path that reports an incoming call that way — and use
                // the lead the backend matched/created from the caller's number.
                val targetLeadId = if (_incoming.value != null) {
                    reportIncomingAsCompleted() ?: return@launch
                } else {
                    leadId
                }

                // Upload the attached recording FIRST (while the active call id
                // still exists). A failed upload aborts so the file isn't lost.
                if (recordingUri != null) {
                    val upload = callsRepo.uploadRecording(recordingUri)
                    if (upload.isFailure) {
                        _error.value = "Recording upload failed. Check your connection and try again."
                        return@launch
                    }
                }

                // Captured BEFORE hangup: the backend mirrors the disposition onto
                // the lead's status when the call ends, and byId() would then return
                // the already re-bucketed lead.
                val preCallStatus = leadsRepo.byId(targetLeadId)?.status

                val neverAnswered =
                    callsRepo.hangup(disposition = disposition.mapped, note = note.takeIf { it.isNotBlank() })

                // A confirmed 0-second call is NOT a contact: whatever outcome the
                // agent picked (e.g. Closed in frustration), the lead's bucket must
                // stay truthful. An untried (NEW) lead moves to NO_ANSWER — "tried,
                // nobody picked up" — so New keeps meaning "never dialed"; a lead
                // with real history keeps its pre-call status (a failed retry can't
                // erase an earlier conversation). The call's own record keeps the
                // agent's disposition + no_answer status either way, and any
                // scheduled callback still fires. (Agreed with Aditya 2026-07-16.)
                if (neverAnswered && preCallStatus != null) {
                    val statusAfterMiss = when (preCallStatus) {
                        com.example.callcenter.domain.model.LeadStatus.NEW ->
                            com.example.callcenter.domain.model.LeadStatus.NO_ANSWER
                        else -> preCallStatus
                    }
                    leadsRepo.updateLead(targetLeadId, status = statusAfterMiss)
                }

                // The backend re-buckets the lead's status from the disposition at
                // hangup — pull the fresh list now so the change is visible without
                // a manual pull-to-refresh. Fire-and-forget on the REPOSITORY's own
                // scope: this ViewModel is about to be cleared by navigation, so a
                // viewModelScope launch would be cancelled mid-flight.
                leadsRepo.refreshSoon()

                // This call fulfils any open follow-up for the lead: complete those
                // callbacks so they leave the list. MUST run before the FOLLOW_UP
                // branch below, which may schedule a fresh one that has to survive.
                callbacksRepo.completeOpenForLead(targetLeadId)

                if (disposition.mapped == Disposition.FOLLOW_UP && callbackAt != null) {
                    val lead = leadsRepo.byId(targetLeadId)
                    if (lead != null) {
                        callbacksRepo.schedule(lead.id, lead.name, lead.phone, lead.campaignName, callbackAt, note)
                    }
                }
                callsRepo.clearActive()
                appPrefs.clearPendingDisposition()

                when (after) {
                    // Retry the SAME lead: stop the auto-dial loop (this is a
                    // deliberate one-off), place a fresh call, go to the call screen.
                    After.REDIAL -> {
                        callsRepo.stopAutoDial()
                        val lead = leadsRepo.byId(targetLeadId)
                        if (lead != null && callsRepo.canCall()) {
                            val route = agentRepo.agent.value?.callMode ?: callsRepo.autoDialRoute()
                            val call = callsRepo.initiate(lead, route)
                            onNextCall(lead.id, call.id, route.name)
                            return@launch
                        }
                        // Can't redial (lead gone or not Available) → just go home.
                        onDone()
                    }
                    // Step away: stop any loop and go home. The agent's status
                    // is deliberately NOT changed — they stay as they are.
                    After.PAUSE -> {
                        callsRepo.stopAutoDial()
                        onDone()
                    }
                    // Normal submit: advance the auto-dial queue if one's running.
                    After.NEXT_OR_HOME -> {
                        val nextLeadId = callsRepo.advanceAutoDialAfter(targetLeadId)
                        if (nextLeadId != null) {
                            val lead = leadsRepo.byId(nextLeadId)
                            if (lead != null && callsRepo.canCall()) {
                                val route = callsRepo.autoDialRoute()
                                val call = callsRepo.initiate(lead, route)
                                onNextCall(lead.id, call.id, route.name)
                                return@launch
                            }
                            callsRepo.stopAutoDial()
                        }
                        onDone()
                    }
                }
            } finally {
                _submitting.value = false
            }
        }
    }
}

/**
 * The call-outcome form. Two modes:
 *  - Outbound (default): [callId]/[leadId] identify an existing backend call, and
 *    the disposition is MANDATORY — back is blocked, there's no way out but Submit.
 *  - Incoming ([incoming] = true): an answered inbound SIM call held locally, not
 *    yet POSTed. Same form, but a red cross in the header lets the agent bail out
 *    (reporting `no_answer`) — a personal call they had to pick up has no business
 *    outcome to record.
 */
@Composable
fun DispositionScreen(
    callId: String,
    leadId: Int,
    onDone: () -> Unit,
    onNextCall: (leadId: Int, callId: String, route: String) -> Unit = { _, _, _ -> },
    incoming: Boolean = false,
    viewModel: DispositionViewModel = hiltViewModel(),
) {
    val codes by viewModel.codes.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val lead by viewModel.lead.collectAsState()
    val incomingInfo by viewModel.incoming.collectAsState()
    val whatsappTarget by viewModel.whatsappTarget.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    if (incoming) {
        // Incoming: no backend lead yet (it's created from the caller's number on
        // submit), so the caller's details come from the held call.
        androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadIncoming() }
    } else {
        // Load the lead once for the identity card + quick actions.
        androidx.compose.runtime.LaunchedEffect(leadId) { viewModel.loadLead(leadId) }
        // Persist this as an owed disposition so an app-kill can't skip it (relaunch
        // routes back here). Cleared when the outcome is submitted.
        androidx.compose.runtime.LaunchedEffect(callId, leadId) { viewModel.markPending(callId, leadId) }
    }

    // Who was on the call: the loaded lead, else the incoming caller's number.
    val callerPhone = lead?.phone
        ?: incomingInfo?.number?.let { com.example.callcenter.telephony.PhoneNumbers.toDialable(it) }
    val callerName = lead?.name ?: incomingInfo?.let { "Incoming call" }

    // rememberSaveable so the agent's outcome/notes/recording survive rotation &
    // process death on this critical data-entry screen. The selected disposition is
    // persisted by its stable code string and re-resolved against [codes].
    var selectedCode by rememberSaveable { mutableStateOf<String?>(null) }
    val disposition = remember(codes, selectedCode) { codes.firstOrNull { it.code == selectedCode } }
    var note by rememberSaveable { mutableStateOf("") }
    var followUpAt by rememberSaveable(stateSaver = LocalDateTimeSaver) { mutableStateOf<LocalDateTime?>(null) }
    var recordingUri by rememberSaveable(stateSaver = UriSaver) { mutableStateOf<Uri?>(null) }
    var recordingName by rememberSaveable { mutableStateOf<String?>(null) }

    // Auto-attach the recording found in the Settings folder (if the agent hasn't
    // already picked one manually). They can still Browse to override.
    val autoRecording by viewModel.autoRecording.collectAsState()
    androidx.compose.runtime.LaunchedEffect(autoRecording) {
        val found = autoRecording
        if (found != null && recordingUri == null) {
            recordingUri = found.uri
            recordingName = found.name
        }
    }

    // File picker for the call recording (audio files only).
    val pickRecording = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            recordingUri = uri
            recordingName = queryFileName(context, uri) ?: "recording"
        }
    }

    // Surface upload / validation errors as a toast.
    androidx.compose.runtime.LaunchedEffect(error) {
        error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // HARD BLOCK: every call must be dispositioned. The system Back button cannot
    // leave this screen — the only way out is selecting an outcome and submitting
    // (or, for an incoming call, the explicit cancel cross). This stops agents
    // skipping the outcome and leaving calls open / un-attributed.
    BackHandler(enabled = !submitting) {
        android.widget.Toast.makeText(
            context,
            if (incoming) {
                "Select an outcome, or tap ✕ if this wasn't a business call."
            } else {
                "Please select an outcome and submit before leaving."
            },
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            // No back arrow — the disposition is mandatory; leaving is via Submit
            // only. Incoming calls get an explicit cancel cross instead: a personal
            // call the agent had to answer has no outcome to log.
            PageHeader(
                title = "Call outcome",
                rightSlot = if (incoming) {
                    { CancelIncomingButton(enabled = !submitting) { viewModel.cancelIncoming(onDone) } }
                } else {
                    null
                },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    // Keep the Submit button clear of the system navigation bar
                    // (gesture pill / 3-button bar) on edge-to-edge displays.
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Identity card — who was on the call.
                LeadIdentityCard(name = callerName, phone = callerPhone)

                // Quick actions — open WhatsApp / SMS / Gmail for this lead.
                QuickActionsRow(
                    onWhatsApp = { callerPhone?.let { com.example.callcenter.telephony.AppLinks.openWhatsApp(context, it, whatsappTarget) } },
                    onSms = { callerPhone?.let { com.example.callcenter.telephony.AppLinks.openSms(context, it) } },
                    onEmail = { com.example.callcenter.telephony.AppLinks.openEmail(context, lead?.email) },
                )

                SectionHeader("How did the call go?")
                DispositionGrid(codes, disposition) { selectedCode = it.code }

                if (disposition?.mapped == Disposition.FOLLOW_UP) {
                    DateTimeField(
                        value = followUpAt,
                        onValueChange = { followUpAt = it },
                        label = "Callback at",
                    )
                }

                Text(
                    "NOTES",
                    color = AppColor.micro,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.05.sp,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Optional notes…", color = AppColor.ink400) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = whiteFieldColors(),
                )

                // SIM only: SIP calls are recorded centrally by the provider
                // (Tata/Smartflo panel), so there is no device file to attach.
                if (viewModel.showRecordingAttach) {
                    RecordingPicker(
                        fileName = recordingName,
                        onBrowse = {
                            // Audio mime types; "*/*" fallback so all recordings show.
                            pickRecording.launch(
                                arrayOf("audio/*", "application/octet-stream"),
                            )
                        },
                        onClear = { recordingUri = null; recordingName = null },
                    )
                }

                // Three identical actions, side by side: Redial · Submit · Pause.
                // All require a disposition; disabled + dimmed while submitting.
                // In incoming mode they also wait for the held call to load — it
                // carries the caller's number, without which submitting would
                // silently fail to create the backend row.
                val actionsEnabled = disposition != null && !submitting &&
                    (!incoming || incomingInfo != null)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max), // all three match the tallest
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionButton(
                        label = "Redial",
                        icon = Icons.Outlined.Replay,
                        container = Warn,
                        enabled = actionsEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                            val d = disposition ?: return@ActionButton
                            viewModel.submitAndRedial(leadId, d, note, followUpAt, recordingUri, onDone, onNextCall)
                        },
                    )
                    ActionButton(
                        label = "Submit",
                        icon = Icons.Outlined.Check,
                        container = Brand600,
                        enabled = actionsEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                            val d = disposition ?: return@ActionButton
                            viewModel.submit(leadId, d, note, followUpAt, recordingUri, onDone, onNextCall)
                        },
                    )
                    ActionButton(
                        label = "Submit & Pause",
                        icon = Icons.Outlined.PauseCircle,
                        container = AccentViolet,
                        enabled = actionsEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                            val d = disposition ?: return@ActionButton
                            viewModel.submitAndPause(leadId, d, note, followUpAt, recordingUri, onDone, onNextCall)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Header cross that abandons an INCOMING call's disposition — for a personal call
 * the agent had to pick up, which has no business outcome. Reports the call as
 * `no_answer` and leaves. Only ever shown in incoming mode; an app-dialed call's
 * disposition stays mandatory.
 */
@Composable
private fun CancelIncomingButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Danger.copy(alpha = if (enabled) 0.12f else 0.06f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            Icons.Outlined.Close,
            contentDescription = "Not a business call — skip the outcome",
            tint = if (enabled) Danger else Danger.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * One of the three bottom actions. All three are identical in size/shape: a
 * filled pill with a centered icon over a small label, two lines tall so the
 * long "Submit & Pause" label fits the same footprint as the short ones.
 */
@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dim by MUTING the fill color when disabled — not a subtree .alpha(), which
    // can wash the whole button out. The fill stays fully opaque either way.
    val fill = if (enabled) container else container.copy(alpha = 0.4f)
    val content = if (enabled) Color.White else Color.White.copy(alpha = 0.85f)
    Column(
        modifier = modifier
            .shadow(if (enabled) 3.dp else 0.dp, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .background(fill)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = label,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            label,
            color = content,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 13.sp,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Top card showing who was called — avatar initials + name + phone. */
@Composable
private fun LeadIdentityCard(name: String?, phone: String?) {
    val initials = (name ?: "?").trim().split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifBlank { "?" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .background(AppColor.surface)
            .border(1.dp, AppColor.ink200, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brand500.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, color = Brand600, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name ?: "Lead", color = AppColor.ink900, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = AppColor.ink500,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(phone ?: "—", color = AppColor.ink500, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Row of quick-action buttons that open WhatsApp / SMS / Gmail for the lead. */
@Composable
private fun QuickActionsRow(
    onWhatsApp: () -> Unit,
    onSms: () -> Unit,
    onEmail: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionButton("WhatsApp", Icons.Outlined.Whatsapp, Success, onWhatsApp, Modifier.weight(1f))
        QuickActionButton("SMS", Icons.Outlined.Sms, Brand500, onSms, Modifier.weight(1f))
        QuickActionButton("Email", Icons.Outlined.Email, AccentRose, onEmail, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .background(AppColor.surface)
            .border(1.dp, AppColor.ink200, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(6.dp))
        Text(label, color = AppColor.ink700, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** White-filled text field colors (fields that sit directly on the page bg). */
@Composable
private fun whiteFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppColor.surface,
    unfocusedContainerColor = AppColor.surface,
    focusedBorderColor = Brand500,
)

/** Resolve a content:// uri's display name (for showing the picked file). */
private fun queryFileName(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
} catch (_: Exception) {
    null
}

@Composable
private fun RecordingPicker(
    fileName: String?,
    onBrowse: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // File box — shows the picked file name (or a placeholder).
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    if (fileName != null) 1.5.dp else 1.dp,
                    if (fileName != null) Brand500 else AppColor.ink200,
                    RoundedCornerShape(14.dp),
                )
                .background(AppColor.surface)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.AudioFile,
                contentDescription = null,
                tint = if (fileName != null) Brand500 else AppColor.ink400,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                fileName ?: "No file selected",
                color = if (fileName != null) AppColor.ink900 else AppColor.ink400,
                fontSize = 13.sp,
                fontWeight = if (fileName != null) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (fileName != null) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove",
                    tint = AppColor.ink500,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onClear() },
                )
            }
        }
        // Browse button — opens the device file picker.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(AppGradients.primaryCta())
                .clickable { onBrowse() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text("Browse", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DispositionGrid(
    items: List<DispositionCode>,
    selected: DispositionCode?,
    onSelect: (DispositionCode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { d ->
                    DispositionTile(d, selected?.code == d.code, { onSelect(d) }, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Outcome-specific glyph for each disposition tile. */
private fun dispositionIcon(d: Disposition): ImageVector = when (d) {
    Disposition.INTERESTED -> Icons.Outlined.ThumbUp
    Disposition.NOT_INTERESTED -> Icons.Outlined.ThumbDown
    Disposition.FOLLOW_UP -> Icons.Outlined.Schedule
    Disposition.NO_ANSWER -> Icons.Outlined.PhoneMissed
    Disposition.BUSY -> Icons.Outlined.PhonePaused
    Disposition.WRONG_NUMBER -> Icons.Outlined.Block
    Disposition.CONVERTED -> Icons.Outlined.CheckCircle
    Disposition.CLOSED -> Icons.Outlined.TaskAlt
}

@Composable
private fun DispositionTile(
    d: DispositionCode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = colorForDisposition(d.mapped)
    // Gray-mapped outcomes (No answer / Closed) take the brand indigo as their
    // selection accent, so picking them is as unmistakable as the colored ones.
    val accent = if (d.mapped == Disposition.NO_ANSWER || d.mapped == Disposition.CLOSED) Brand600 else color
    val bg = if (selected) accent.copy(alpha = 0.10f) else AppColor.surface
    val borderColor = if (selected) accent else AppColor.ink200
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                dispositionIcon(d.mapped),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.size(9.dp))
        Text(
            d.label,
            color = if (selected) accent else AppColor.ink900,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.5.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
