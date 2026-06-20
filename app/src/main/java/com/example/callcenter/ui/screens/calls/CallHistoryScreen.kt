package com.example.callcenter.ui.screens.calls

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.domain.model.Call
import com.example.callcenter.domain.model.CallDirection
import com.example.callcenter.ui.components.EmptyState
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.LoadingState
import com.example.callcenter.ui.components.colorForCallStatus
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryRange(val label: String) {
    Today("Today"),
    Yesterday("Yesterday"),
    Last7("Last 7 days"),
}

@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val callsRepo: CallsRepository,
) : ViewModel() {
    data class State(
        val calls: List<Call> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val range: HistoryRange = HistoryRange.Today,
        val hasRecording: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            callsRepo.observeHistory().collect { list ->
                _state.update { it.copy(calls = list, loading = false) }
            }
        }
        viewModelScope.launch { callsRepo.refreshHistory() }
    }

    /** Pull-to-refresh: re-fetch history without blanking the visible list. */
    fun pullRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                callsRepo.refreshHistory()
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun setRange(r: HistoryRange) = _state.update { it.copy(range = r) }
    fun toggleHasRecording() = _state.update { it.copy(hasRecording = !it.hasRecording) }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CallHistoryScreen(viewModel: CallHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val filtered = remember(state.calls, state.range, state.hasRecording) {
        applyHistoryFilters(state.calls, state.range, state.hasRecording)
    }

    Box(Modifier
        .fillMaxSize()
        .background(AppColor.bg)) {
        Column(Modifier.fillMaxSize()) {
            GradientHeader(
                title = "Call History",
                subtitle = if (filtered.size == 1) "1 call" else "${filtered.size} calls",
            )
            FilterBar(
                range = state.range,
                hasRecording = state.hasRecording,
                onRange = viewModel::setRange,
                onToggleRecording = viewModel::toggleHasRecording,
            )
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::pullRefresh,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.loading -> LoadingState()
                    // Empty state inside a LazyColumn so the pull gesture still works.
                    filtered.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item { EmptyState(title = "No calls in this range", modifier = Modifier.fillParentMaxSize()) }
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { call -> CallHistoryRow(call) }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun applyHistoryFilters(
    calls: List<Call>,
    range: HistoryRange,
    hasRecording: Boolean,
): List<Call> {
    val today = LocalDate.now()
    return calls.asSequence()
        .filter {
            val d = it.startedAt.toLocalDate()
            when (range) {
                HistoryRange.Today -> d == today
                HistoryRange.Yesterday -> d == today.minusDays(1)
                HistoryRange.Last7 -> !d.isBefore(today.minusDays(6)) && !d.isAfter(today)
            }
        }
        .filter { !hasRecording || !it.recordingUrl.isNullOrBlank() }
        .toList()
}

@Composable
private fun FilterBar(
    range: HistoryRange,
    hasRecording: Boolean,
    onRange: (HistoryRange) -> Unit,
    onToggleRecording: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HistoryRange.entries.toList()) { r ->
            HistoryChip(
                label = r.label,
                selected = r == range,
                onClick = { onRange(r) },
            )
        }
        item {
            HistoryChip(
                label = "Has recording",
                selected = hasRecording,
                onClick = onToggleRecording,
                leading = Icons.Outlined.Mic,
            )
        }
    }
}

@Composable
private fun HistoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: ImageVector? = null,
) {
    val bg = if (selected) Brand600 else MaterialTheme.colorScheme.surface
    val fg = if (selected) Color.White else AppColor.ink700
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, if (selected) Brand600 else AppColor.ink200, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun CallHistoryRow(call: Call) {
    val outgoing = call.direction == CallDirection.OUTGOING
    val arrowColor = if (outgoing) Success else Danger
    val arrowIcon = if (outgoing) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward
    val dateFmt = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(arrowColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(arrowIcon, contentDescription = null, tint = arrowColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(call.leadName, fontWeight = FontWeight.Bold, color = AppColor.ink900, fontSize = 15.sp)
                    Text(call.leadPhone, color = AppColor.ink500, fontSize = 12.sp)
                    if (call.campaignName.isNotBlank()) {
                        Text(call.campaignName, color = Brand500, fontSize = 11.sp)
                    }
                }
                StatusDotBadge(
                    text = call.status.label.uppercase(),
                    color = colorForCallStatus(call.status),
                )
            }
            Spacer(Modifier.size(10.dp))
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColor.ink200))
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaText(icon = Icons.Outlined.CalendarMonth, text = call.startedAt.format(dateFmt))
                Spacer(Modifier.weight(1f))
                if (call.durationSec > 0) {
                    MetaText(
                        icon = Icons.Outlined.AccessTime,
                        text = "%d:%02d".format(call.durationSec / 60, call.durationSec % 60),
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(call.routeType.name, color = AppColor.ink700, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (call.disposition != null) {
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = AppColor.ink500, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(call.disposition.label.lowercase(), color = AppColor.ink500, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MetaText(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppColor.ink500, modifier = Modifier.size(12.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, color = AppColor.ink500, fontSize = 11.sp)
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
