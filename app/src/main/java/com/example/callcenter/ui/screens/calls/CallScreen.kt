package com.example.callcenter.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Note
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.ui.components.AppBottomSheet
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.theme.AccentRose
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Ink400
import com.example.callcenter.ui.theme.Ink800
import com.example.callcenter.ui.theme.Ink900
import com.example.callcenter.ui.theme.Success

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
        ) {
            TopRow(state)
            Spacer(Modifier.height(28.dp))
            CustomerSection(state)
            Spacer(Modifier.weight(1f))
            ControlsGrid(state, viewModel)
            Spacer(Modifier.height(28.dp))
            EndCallSection(onEnd = { viewModel.hangup(onEnded) })
            Spacer(Modifier.height(8.dp))
        }
    }

    if (state.keypadOpen) {
        AppBottomSheet(
            onDismiss = { viewModel.closeKeypad() },
            containerColor = Ink900,
        ) {
            KeypadSheet(
                dialed = state.dialed,
                onPress = viewModel::pressKey,
                onBackspace = viewModel::backspaceKey,
            )
        }
    }
    if (state.noteOpen) {
        AppBottomSheet(onDismiss = { viewModel.closeNote() }) {
            NoteSheet(
                value = state.note,
                onChange = viewModel::setNote,
                onSave = viewModel::saveNote,
            )
        }
    }
    if (state.transferOpen) {
        AppBottomSheet(onDismiss = { viewModel.closeTransfer() }) {
            TransferSheet(
                value = state.transferTo,
                onChange = viewModel::setTransfer,
                onSubmit = { viewModel.closeTransfer() },
            )
        }
    }
}

@Composable
private fun TopRow(state: CallUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(state.status)
        RecordingPill(state.recording)
    }
}

@Composable
private fun StatusPill(status: CallStatus) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (status == CallStatus.CONNECTED) Success else Color.White.copy(alpha = 0.7f)))
        Spacer(Modifier.size(8.dp))
        Text(
            text = status.label.uppercase(),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun RecordingPill(recording: Boolean) {
    val bg = if (recording) AccentRose.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (recording) AccentRose else Color.White.copy(alpha = 0.4f)))
        Spacer(Modifier.size(8.dp))
        Text(
            text = if (recording) "REC" else "REC OFF",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun CustomerSection(state: CallUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                state.customerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(state.customerName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(state.customerPhone, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
        if (state.campaignName.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(state.campaignName, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        if (state.status == CallStatus.CONNECTED) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = formatDuration(state.durationSec),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
    }
}

private fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}

@Composable
private fun ControlsGrid(state: CallUiState, vm: CallViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
            ControlTile("Mute", if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, state.muted, vm::toggleMute)
            ControlTile("Hold", if (state.onHold) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, state.onHold, vm::toggleHold)
            ControlTile("Speaker", if (state.speaker) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, state.speaker, vm::toggleSpeaker)
            ControlTile("Keypad", Icons.Outlined.Dialpad, false, vm::openKeypad)
        }
        Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
            ControlTile("Note", Icons.Outlined.Note, state.noteOpen, vm::openNote)
            ControlTile("Transfer", Icons.Outlined.SwapHoriz, false, vm::openTransfer)
            ControlTile("Record", if (state.recording) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                state.recording, vm::toggleRecording)
            ControlTile("Add", Icons.Outlined.PersonAdd, false, { /* noop */ })
        }
    }
}

@Composable
private fun ControlTile(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.1f))
                .border(
                    1.dp,
                    if (active) Color.Transparent else Color.White.copy(alpha = 0.18f),
                    CircleShape,
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) Ink900 else Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun EndCallSection(onEnd: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .shadow(elevation = 10.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(AccentRose)
                .clickable { onEnd() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.CallEnd,
                contentDescription = "End call",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("End call", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KeypadSheet(
    dialed: String,
    onPress: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Keypad", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dialed.ifEmpty { "Enter digits" },
                color = if (dialed.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            if (dialed.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Backspace,
                    contentDescription = "Backspace",
                    tint = Ink400,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onBackspace() },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        KeypadGrid(onPress = onPress)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun KeypadGrid(onPress: (String) -> Unit) {
    val keys = listOf(
        "1" to "", "2" to "ABC", "3" to "DEF",
        "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to "",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (n, sub) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { onPress(n) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(n, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            if (sub.isNotEmpty()) {
                                Text(
                                    sub,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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

@Composable
private fun TransferSheet(
    value: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Transfer call", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("Extension or phone number") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Spacer(Modifier.height(16.dp))
        AppButton("Transfer", onClick = onSubmit, fullWidth = true)
        Spacer(Modifier.height(20.dp))
    }
}
