package com.example.callcenter.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Whatsapp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.prefs.AppPreferences
import com.example.callcenter.data.repository.CallbacksRepository
import com.example.callcenter.notifications.ReminderScheduler
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.AppButtonSize
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.components.UriSaver
import com.example.callcenter.ui.theme.AccentMint
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The follow-up reminder timing options (label → minutes-before). */
private val TIMING_OPTIONS = listOf(
    "2 minutes before" to 2,
    "5 minutes before" to 5,
    "15 minutes before" to 15,
    "1 hour before" to 60,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPrefs: AppPreferences,
    private val callbacksRepo: CallbacksRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {
    // NULL until DataStore emits the FIRST real value — the screen must seed its
    // editable fields from the real prefs, NOT from a placeholder default (which
    // would blank everything and then get locked in by the seed-once guard).
    val prefs = appPrefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Persist the recording folder immediately (a system grant, not part of Save). */
    fun setRecordingFolder(uri: String) = viewModelScope.launch {
        appPrefs.setRecordingFolderUri(uri)
    }

    /** Set the default WhatsApp target ("whatsapp" | "business"), persisted immediately. */
    fun setWhatsappTarget(target: String) = viewModelScope.launch {
        appPrefs.setWhatsappTarget(target)
    }

    /** Commit the editable fields on Save, then re-arm reminders with the new timing. */
    fun save(callDelaySeconds: Int, followUpTimingMinutes: Int) = viewModelScope.launch {
        appPrefs.setCallDelaySeconds(callDelaySeconds)
        appPrefs.setFollowUpTimingMinutes(followUpTimingMinutes)
        reminderScheduler.rescheduleAll(callbacksRepo.snapshot(), followUpTimingMinutes)
        // Diagnostic: read back straight from disk right after writing, so logcat
        // proves whether the values PERSISTED (storage OK → any blanking is a
        // display bug) or not (storage bug).
        val readBack = appPrefs.current()
        android.util.Log.d(
            "Bol7Settings",
            "SAVED delay=$callDelaySeconds timing=$followUpTimingMinutes " +
                "→ DISK NOW: delay=${readBack.callDelaySeconds} timing=${readBack.followUpTimingMinutes} " +
                "folder='${readBack.recordingFolderUri}' " +
                "whatsapp='${readBack.whatsappTarget}'",
        )
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.prefs.collectAsState()
    val context = LocalContext.current

    // Ask for notification permission (Android 13+) so reminders can actually show.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored; reminders simply won't post if denied */ }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Editable local state. NOTE: the seed guard uses plain `remember` (NOT
    // rememberSaveable) so every time the screen is (re)entered it re-seeds from
    // the freshly-loaded prefs. Using rememberSaveable for the guard was the bug:
    // on re-entry it could restore seeded=true while the fields restored to blank
    // defaults, so the effect was skipped and the saved values never re-appeared.
    var callDelay by rememberSaveable { mutableStateOf("5") }
    var timingMinutes by rememberSaveable { mutableIntStateOf(5) }
    var folderUri by rememberSaveable(stateSaver = UriSaver) { mutableStateOf<Uri?>(null) }
    var whatsappTarget by rememberSaveable { mutableStateOf("whatsapp") }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(prefs) {
        // Seed once per screen entry, from the FIRST real emission (prefs != null).
        val loaded = prefs
        if (!seeded && loaded != null) {
            callDelay = loaded.callDelaySeconds.toString()
            timingMinutes = loaded.followUpTimingMinutes
            folderUri = loaded.recordingFolderUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            whatsappTarget = loaded.whatsappTarget
            seeded = true
        }
    }

    // SAF folder picker — one-time grant, persisted so it survives restarts.
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            folderUri = uri
            viewModel.setRecordingFolder(uri.toString())
        }
    }

    // Derive a readable folder name from the SAF tree URI's last segment, e.g.
    // ".../tree/primary:Recordings/Call" → "Call". Avoids the documentfile dep.
    val folderName = remember(folderUri) {
        folderUri?.lastPathSegment
            ?.substringAfterLast(':')
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: ""
    }

    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Settings", onBack = onBack)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    // Root-NavHost route (no tab scaffold): keep the Save button
                    // clear of the system nav bar on edge-to-edge displays.
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ---- Card 1: Call settings ----
                SettingsCard {
                    FieldLabel("Call delay in seconds (5 - 10)")
                    OutlinedTextField(
                        value = callDelay,
                        onValueChange = { v -> callDelay = v.filter { it.isDigit() }.take(2) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = filledFieldColors(),
                    )

                    Spacer(Modifier.height(16.dp))
                    FieldLabel("Call recording folder")
                    FolderRow(
                        folderName = folderName.ifBlank { null },
                        onBrowse = { pickFolder.launch(null) },
                    )

                }

                // ---- Card 1b: Default WhatsApp app ----
                SettingsCard {
                    Text(
                        "Default WhatsApp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppColor.ink900,
                    )
                    Text(
                        "Which app the WhatsApp button opens",
                        color = AppColor.ink500,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Mutually exclusive: turning one ON selects it as the default.
                    WhatsAppToggleRow(
                        label = "WhatsApp",
                        tint = AccentMint,
                        selected = whatsappTarget == "whatsapp",
                        onSelect = { whatsappTarget = "whatsapp"; viewModel.setWhatsappTarget("whatsapp") },
                    )
                    WhatsAppToggleRow(
                        label = "WhatsApp Business",
                        tint = AppColor.ink500,
                        selected = whatsappTarget == "business",
                        onSelect = { whatsappTarget = "business"; viewModel.setWhatsappTarget("business") },
                    )
                }

                // ---- Card 2: Follow-up notification timing ----
                SettingsCard {
                    Text(
                        "Follow-up notification timing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppColor.ink900,
                    )
                    Spacer(Modifier.height(4.dp))
                    TIMING_OPTIONS.forEach { (label, minutes) ->
                        RadioRow(
                            label = label,
                            selected = timingMinutes == minutes,
                            onSelect = { timingMinutes = minutes },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                AppButton(
                    text = "Save",
                    onClick = {
                        val delay = (callDelay.toIntOrNull() ?: 5).coerceIn(5, 10)
                        callDelay = delay.toString()
                        viewModel.save(delay, timingMinutes)
                        android.widget.Toast.makeText(
                            context, "Settings saved", android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    fullWidth = true,
                    size = AppButtonSize.Lg,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** A bordered surface card matching the app's settings-row style. */
@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = AppColor.ink700,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** Soft-filled text field colors: gray fill, no border until focused. */
@Composable
private fun filledFieldColors() = OutlinedTextFieldDefaults.colors(
    // surfaceAlt is the dark-aware filled-input token (ink50 would blend
    // into the card surface in dark theme).
    focusedContainerColor = AppColor.surfaceAlt,
    unfocusedContainerColor = AppColor.surfaceAlt,
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = Brand500,
)

/** M3 switch with the app's green "on" state and a slightly compact feel. */
@Composable
private fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Success,
            checkedThumbColor = Color.White,
        ),
        modifier = Modifier.scale(0.85f),
    )
}

/** Folder path/name box + BROWSE button (modeled on the disposition RecordingPicker). */
@Composable
private fun FolderRow(folderName: String?, onBrowse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (folderName != null) {
                        Modifier.border(1.5.dp, Brand500, RoundedCornerShape(12.dp))
                    } else Modifier
                )
                .background(AppColor.surfaceAlt)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = if (folderName != null) Brand500 else AppColor.ink400,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                folderName ?: "Not set",
                color = if (folderName != null) AppColor.ink900 else AppColor.ink400,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(AppGradients.primaryCta())
                .clickable { onBrowse() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("BROWSE", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            color = AppColor.ink900,
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/**
 * Label + toggle for choosing the default WhatsApp app. Turning one ON selects it;
 * the other row reads the same [selected] source so it flips OFF automatically —
 * making the two toggles mutually exclusive (a single default). Turning the active
 * one OFF is a no-op (there's always a default).
 */
@Composable
private fun WhatsAppToggleRow(label: String, tint: Color, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Whatsapp, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(10.dp))
        Text(
            label,
            color = AppColor.ink900,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(
            checked = selected,
            onCheckedChange = { on -> if (on) onSelect() },
        )
    }
}
