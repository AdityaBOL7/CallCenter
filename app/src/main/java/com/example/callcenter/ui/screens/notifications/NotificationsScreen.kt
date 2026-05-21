package com.example.callcenter.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.NotificationsRepository
import com.example.callcenter.domain.model.AppNotification
import com.example.callcenter.domain.model.NotificationKind
import com.example.callcenter.ui.components.EmptyState
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.LoadingState
import com.example.callcenter.ui.theme.AccentSky
import com.example.callcenter.ui.theme.AccentViolet
import com.example.callcenter.ui.theme.AppBg
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Ink900
import com.example.callcenter.ui.theme.Warn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repo: NotificationsRepository,
) : ViewModel() {
    data class State(val items: List<AppNotification> = emptyList(), val loading: Boolean = true)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init { viewModelScope.launch { repo.observe().collect { _state.value = State(it, false) } } }
    fun markAllRead() = viewModelScope.launch { repo.markAllRead() }
    fun markRead(id: Int) = viewModelScope.launch { repo.markRead(id) }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val unread = state.items.count { !it.read }
    Box(Modifier
        .fillMaxSize()
        .background(AppBg)) {
        Column(Modifier.fillMaxSize()) {
            GradientHeader(
                title = "Notifications",
                subtitle = "$unread unread",
                onBack = onBack,
                rightSlot = {
                    if (unread > 0) {
                        Text(
                            "Mark all",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { viewModel.markAllRead() },
                        )
                    }
                },
            )
            when {
                state.loading -> LoadingState()
                state.items.isEmpty() -> EmptyState(title = "No notifications")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { n ->
                        NotificationCard(n, onClick = { viewModel.markRead(n.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: AppNotification, onClick: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")
    val (icon, tint) = when (n.kind) {
        NotificationKind.CALLBACK -> Icons.Outlined.Schedule to Warn
        NotificationKind.LEAD -> Icons.Outlined.Person to Brand500
        NotificationKind.SYSTEM -> Icons.Outlined.Notifications to AccentSky
        NotificationKind.CAMPAIGN -> Icons.Outlined.Campaign to AccentViolet
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (n.read) MaterialTheme.colorScheme.surface else tint.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(n.title, fontWeight = FontWeight.SemiBold, color = Ink900, fontSize = 14.sp)
                Text(n.body, color = Ink500, fontSize = 12.sp)
                Text(n.createdAt.format(fmt), color = Ink500, fontSize = 11.sp)
            }
            if (!n.read) {
                Box(modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint))
            }
        }
    }
}
