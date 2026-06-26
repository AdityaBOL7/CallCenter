package com.example.callcenter.ui.screens.calls

import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.CallbacksRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.DispositionsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.DispositionCode
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.AppButtonSize
import com.example.callcenter.ui.components.DateTimeField
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.components.colorForDisposition
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.Brand500
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DispositionViewModel @Inject constructor(
    private val callsRepo: CallsRepository,
    private val leadsRepo: LeadsRepository,
    private val callbacksRepo: CallbacksRepository,
    private val dispositionsRepo: DispositionsRepository,
) : ViewModel() {

    val codes: StateFlow<List<DispositionCode>> = dispositionsRepo.codes

    // True while a submit() is in flight, so the UI can disable the button and a
    // double-tap can't launch a second submission (which would skip a lead).
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init {
        viewModelScope.launch { dispositionsRepo.refresh() }
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() { _error.value = null }

    /**
     * Submit the call outcome. Attaching a recording [recordingUri] is OPTIONAL:
     * if one is picked it's uploaded first, and a failed upload aborts the submit
     * so the attached file isn't silently dropped; if none is picked the form
     * submits normally. If an auto-dial session is running, advance to the next
     * queued lead ([onNextCall]) instead of returning home ([onDone]).
     */
    fun submit(
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
                // If the agent attached a recording, upload it FIRST (while the
                // active call id still exists). A failed upload aborts the submit
                // so the attached file isn't lost. No file → skip, submit normally.
                if (recordingUri != null) {
                    val upload = callsRepo.uploadRecording(recordingUri)
                    if (upload.isFailure) {
                        _error.value = "Recording upload failed. Check your connection and try again."
                        return@launch
                    }
                }

                callsRepo.hangup(disposition = disposition.mapped, note = note.takeIf { it.isNotBlank() })
                if (disposition.mapped == Disposition.FOLLOW_UP && callbackAt != null) {
                    val lead = leadsRepo.byId(leadId)
                    if (lead != null) {
                        callbacksRepo.schedule(lead.id, lead.name, lead.phone, lead.campaignName, callbackAt, note)
                    }
                }
                callsRepo.clearActive()

                // Auto-dial: advance ONLY if this disposition belongs to the active
                // auto-dial session (its current lead == the lead we just handled).
                // A manual one-off call returns null here and falls through to onDone.
                val nextLeadId = callsRepo.advanceAutoDialAfter(leadId)
                if (nextLeadId != null) {
                    val lead = leadsRepo.byId(nextLeadId)
                    // Stop the loop if the agent went off-Available mid-session, or
                    // the next lead vanished — either way, don't dial; go home.
                    if (lead != null && callsRepo.canCall()) {
                        val route = callsRepo.autoDialRoute()
                        val call = callsRepo.initiate(lead, route)
                        onNextCall(lead.id, call.id, route.name)
                        return@launch
                    }
                    callsRepo.stopAutoDial()
                }
                onDone()
            } finally {
                _submitting.value = false
            }
        }
    }
}

@Composable
fun DispositionScreen(
    callId: String,
    leadId: Int,
    onDone: () -> Unit,
    onNextCall: (leadId: Int, callId: String, route: String) -> Unit = { _, _, _ -> },
    viewModel: DispositionViewModel = hiltViewModel(),
) {
    val codes by viewModel.codes.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var disposition by remember { mutableStateOf<DispositionCode?>(null) }
    var note by remember { mutableStateOf("") }
    var followUpAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var recordingUri by remember { mutableStateOf<Uri?>(null) }
    var recordingName by remember { mutableStateOf<String?>(null) }

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

    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Call outcome", onBack = onDone)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    // Keep the Submit button clear of the system navigation bar
                    // (gesture pill / 3-button bar) on edge-to-edge displays.
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionHeader("How did the call go?")
                DispositionGrid(codes, disposition) { disposition = it }

                if (disposition?.mapped == Disposition.FOLLOW_UP) {
                    DateTimeField(
                        value = followUpAt,
                        onValueChange = { followUpAt = it },
                        label = "Callback at",
                    )
                }

                Text(
                    "Notes",
                    color = AppColor.ink500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Optional notes…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(14.dp),
                )

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

                AppButton(
                    text = "Submit outcome",
                    onClick = {
                        val d = disposition ?: return@AppButton
                        viewModel.submit(leadId, d, note, followUpAt, recordingUri, onDone, onNextCall)
                    },
                    enabled = disposition != null && !submitting,
                    loading = submitting,
                    fullWidth = true,
                    size = AppButtonSize.Lg,
                )
            }
        }
    }
}

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
    Text(
        "Call recording (optional)",
        color = AppColor.ink500,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    )
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
                .border(1.5.dp, if (fileName != null) Brand500 else AppColor.ink200, RoundedCornerShape(14.dp))
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
                .background(Brand500)
                .clickable { onBrowse() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text("Browse", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun DispositionTile(
    d: DispositionCode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = colorForDisposition(d.mapped)
    val bg = if (selected) color.copy(alpha = 0.14f) else AppColor.surface
    val borderColor = if (selected) color else AppColor.ink200
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Notes,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(d.label, color = AppColor.ink900, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
