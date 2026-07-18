package com.example.callcenter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.Brand500
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    value: LocalDateTime?,
    onValueChange: (LocalDateTime) -> Unit,
    label: String = "When",
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }

    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a") }
    // "3 Jul 2026, 5:42 pm" — lowercase meridiem per the design.
    val display = value?.format(fmt)?.replace(" AM", " am")?.replace(" PM", " pm")
        ?: "Pick date & time"

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            color = AppColor.micro,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.05.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable { showDate = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = Brand500, modifier = Modifier.size(20.dp))
            Text(
                display,
                color = if (value == null) AppColor.ink500 else AppColor.ink900,
                fontSize = 14.sp,
                fontWeight = if (value == null) FontWeight.Medium else FontWeight.SemiBold,
            )
        }
    }

    if (showDate) {
        // Material3 DatePicker stores the picked date as UTC-midnight epoch millis.
        // Seed AND read back both at UTC (not the device zone) so the round-trip is
        // symmetric — otherwise a pre-filled early-morning time (00:00–05:29 IST)
        // seeds the PREVIOUS UTC day and the picker opens on the wrong date.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.toLocalDate()
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        pendingDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        showDate = false
                        showTime = true
                    }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime) {
        val now = value ?: LocalDateTime.now()
        val state = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute)
        Dialog(onDismissRequest = { showTime = false }, properties = DialogProperties()) {
            androidx.compose.material3.Surface(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Pick a time", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                    TimePicker(state = state)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showTime = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val date = pendingDate ?: LocalDate.now()
                            val combined = LocalDateTime.of(date, LocalTime.of(state.hour, state.minute))
                            onValueChange(combined)
                            showTime = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}
