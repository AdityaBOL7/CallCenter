package com.example.callcenter.ui.screens.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.ModeEdit
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.AuthRepository
import com.example.callcenter.domain.model.Agent
import com.example.callcenter.domain.model.AgentStats
import com.example.callcenter.ui.components.colorForAgentStatus
import com.example.callcenter.ui.theme.AccentSky
import com.example.callcenter.ui.theme.AccentViolet
import com.example.callcenter.ui.theme.AppBg
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.HeaderShape
import com.example.callcenter.ui.theme.Ink100
import com.example.callcenter.ui.theme.Ink200
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Ink700
import com.example.callcenter.ui.theme.Ink900
import com.example.callcenter.ui.theme.Success
import com.example.callcenter.ui.theme.Warn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val agentRepo: AgentRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {
    data class State(val agent: Agent? = null, val stats: AgentStats = AgentStats())
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(agentRepo.agent, agentRepo.stats) { a, s -> a to s }.collect { (a, s) ->
                _state.update { it.copy(agent = a, stats = s) }
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        authRepo.logout()
        onDone()
    }
}

@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onReports: () -> Unit,
    onHelp: () -> Unit,
    onTerms: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Box(
        Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        Column(Modifier.fillMaxSize()) {
            ProfileHero(agent = state.agent, onEdit = onEditProfile)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SnapshotCard(stats = state.stats)

                SectionLabel("TELEPHONY")
                GroupedCard {
                    InfoRow(
                        icon = Icons.Outlined.AlternateEmail,
                        tint = AccentSky,
                        label = "Username",
                        value = state.agent?.username ?: "—",
                    )
                    RowDivider()
                    InfoRow(
                        icon = Icons.Outlined.Phone,
                        tint = Success,
                        label = "Extension",
                        value = state.agent?.extension ?: "—",
                    )
                    RowDivider()
                    InfoRow(
                        icon = Icons.Outlined.Storage,
                        tint = AccentViolet,
                        label = "SIP user",
                        value = state.agent?.sipUsername ?: "—",
                    )
                }

                SectionLabel("PREFERENCES")
                GroupedCard {
                    NavRow(
                        icon = Icons.Outlined.NotificationsNone,
                        tint = Warn,
                        label = "Notifications",
                        onClick = onSettings,
                    )
                    RowDivider()
                    NavRow(
                        icon = Icons.Outlined.Settings,
                        tint = Ink700,
                        label = "App settings",
                        onClick = onSettings,
                    )
                }

                SectionLabel("SUPPORT")
                GroupedCard {
                    NavRow(
                        icon = Icons.Outlined.HelpOutline,
                        tint = AccentSky,
                        label = "Help & support",
                        onClick = onHelp,
                    )
                    RowDivider()
                    NavRow(
                        icon = Icons.Outlined.Article,
                        tint = AccentViolet,
                        label = "Terms & privacy",
                        onClick = onTerms,
                    )
                }

                Spacer(Modifier.size(4.dp))
                SignOutButton(onClick = { viewModel.signOut(onSignOut) })

                Text(
                    "Agent Dialer • v1.0.0",
                    color = Ink500,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProfileHero(agent: Agent?, onEdit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeaderShape)
            .background(AppGradients.brandHeader())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 18.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Profile",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                EditPill(onClick = onEdit)
            }
            Spacer(Modifier.size(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        agent?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        agent?.name ?: "Agent",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        agent?.email ?: "",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                    if (agent != null) {
                        Spacer(Modifier.size(6.dp))
                        StatusInlinePill(label = agent.status.label.uppercase(), color = colorForAgentStatus(agent.status))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ModeEdit, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(6.dp))
        Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusInlinePill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.22f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color))
        Spacer(Modifier.size(6.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun SnapshotCard(stats: AgentStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TODAY'S SNAPSHOT",
                        color = Ink500,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            stats.connectedCalls.toString(),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink900,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "/ ${stats.totalCalls}",
                            fontSize = 14.sp,
                            color = Ink500,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.size(2.dp))
                    Text("Connected of total", color = Ink500, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Success.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${(stats.connectionRate * 100).toInt()}%",
                        color = Success,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            LinearProgressIndicator(
                progress = { stats.connectionRate.coerceIn(0f, 1f) },
                color = Success,
                trackColor = Ink100,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.size(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                MiniStat(icon = Icons.Outlined.Schedule, tint = AccentSky, value = stats.callbacksDue.toString(), label = "FOLLOW-UPS")
                MiniStat(icon = Icons.Outlined.CallMissed, tint = Danger, value = stats.missedCalls.toString(), label = "MISSED")
                MiniStat(icon = Icons.Outlined.EmojiEvents, tint = AccentViolet, value = stats.conversions.toString(), label = "WINS")
            }
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, tint: Color, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(6.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink900)
        Text(label, color = Ink500, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Ink500,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 56.dp)
            .background(Ink200),
    )
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(label, color = Ink700, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ink900, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(label, color = Ink900, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Ink500, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Danger)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Sign out", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
