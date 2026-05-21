package com.example.callcenter.ui.screens.callbacks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.CallbacksRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.Callback
import com.example.callcenter.domain.model.CallbackStatus
import com.example.callcenter.ui.components.EmptyState
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.LoadingState
import com.example.callcenter.ui.theme.AppBg
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.Ink200
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Ink900
import com.example.callcenter.ui.theme.Success
import com.example.callcenter.ui.theme.Warn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CallbackListViewModel @Inject constructor(
    private val callbacksRepo: CallbacksRepository,
    private val leadsRepo: LeadsRepository,
    private val callsRepo: CallsRepository,
) : ViewModel() {
    data class State(val items: List<Callback> = emptyList(), val loading: Boolean = true)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            callbacksRepo.observeAll().collect { _state.value = State(items = it, loading = false) }
        }
    }

    fun startCall(callback: Callback, onCall: (leadId: Int, callId: String, route: String) -> Unit) {
        viewModelScope.launch {
            val lead = leadsRepo.byId(callback.leadId) ?: return@launch
            val call = callsRepo.initiate(lead, CallRouteType.SIP)
            onCall(lead.id, call.id, call.routeType.name)
        }
    }
}

@Composable
fun CallbackListScreen(
    onSchedule: (Int) -> Unit,
    onCall: (leadId: Int, callId: String, route: String) -> Unit,
    viewModel: CallbackListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val now = LocalDateTime.now()
    val isOverdue: (Callback) -> Boolean = { it.status == CallbackStatus.OVERDUE || it.scheduledAt.isBefore(now) }
    val overdueCount = state.items.count(isOverdue)
    // Sort: overdue first (most overdue at top), then upcoming (soonest first)
    val sorted = state.items.sortedWith(
        compareByDescending<Callback> { isOverdue(it) }.thenBy { it.scheduledAt }
    )

    Box(Modifier
        .fillMaxSize()
        .background(AppBg)) {
        Column(Modifier.fillMaxSize()) {
            GradientHeader(
                title = "Follow-ups",
                subtitle = "${state.items.size} scheduled • $overdueCount overdue",
                rightSlot = {
                    if (overdueCount > 0) OverdueHeaderPill(count = overdueCount)
                },
            )
            when {
                state.loading -> LoadingState()
                state.items.isEmpty() -> EmptyState(title = "No follow-ups scheduled")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sorted, key = { it.id }) { cb ->
                        CallbackCard(
                            cb = cb,
                            overdue = isOverdue(cb),
                            onCall = { viewModel.startCall(cb, onCall) },
                            onReschedule = { onSchedule(cb.leadId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverdueHeaderPill(count: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(Danger))
        Spacer(Modifier.size(6.dp))
        Text(
            "$count OVERDUE",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun CallbackCard(
    cb: Callback,
    overdue: Boolean,
    onCall: () -> Unit,
    onReschedule: () -> Unit,
) {
    val fmt = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a")
    val accent = if (overdue) Danger else Warn

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (overdue) Icons.Outlined.NotificationsActive else Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cb.customerName, fontWeight = FontWeight.Bold, color = Ink900, fontSize = 15.sp)
                        Spacer(Modifier.size(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Phone, contentDescription = null, tint = Ink500, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(cb.phone, color = Ink500, fontSize = 12.sp)
                        }
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = buildScheduledText(cb.scheduledAt, overdue, fmt),
                            color = if (overdue) Danger else Ink500,
                            fontSize = 12.sp,
                            fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                    if (overdue) {
                        StatusDotBadge(text = "OVERDUE", color = Danger)
                    }
                }
                if (!cb.note.isNullOrBlank()) {
                    Spacer(Modifier.size(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(cb.note, color = Ink500, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryCallButton(onClick = onCall, modifier = Modifier.weight(1f))
                    SecondaryActionButton(
                        label = "Reschedule",
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = onReschedule,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun buildScheduledText(at: LocalDateTime, overdue: Boolean, fmt: DateTimeFormatter): String {
    val now = LocalDateTime.now()
    val base = at.format(fmt)
    val suffix = if (overdue) " (${relativeAgo(at, now)})" else ""
    return base + suffix
}

private fun relativeAgo(at: LocalDateTime, now: LocalDateTime): String {
    val mins = ChronoUnit.MINUTES.between(at, now)
    val hours = ChronoUnit.HOURS.between(at, now)
    val days = ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())
    return when {
        days >= 1 -> "ago ${days}d"
        hours >= 1 -> "ago ${hours}h"
        mins >= 1 -> "ago ${mins}m"
        else -> "just now"
    }
}

@Composable
private fun StatusDotBadge(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color))
        Spacer(Modifier.size(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun PrimaryCallButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Success)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text("Call", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun SecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Ink200, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Ink500, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = Ink900, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
