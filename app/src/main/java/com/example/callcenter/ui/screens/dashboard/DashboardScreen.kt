package com.example.callcenter.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.rounded.Notifications
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.components.colorForAgentStatus
import com.example.callcenter.ui.theme.AccentMint
import com.example.callcenter.ui.theme.AccentRose
import com.example.callcenter.ui.theme.AccentSky
import com.example.callcenter.ui.theme.AccentViolet
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.Emerald50
import com.example.callcenter.ui.theme.HeaderShape
import com.example.callcenter.ui.theme.Success
import com.example.callcenter.ui.theme.Warn

@Composable
fun DashboardScreen(
    onOpenLeads: () -> Unit,
    onOpenCallbacks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCampaigns: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColor.bg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardHero(
                agentName = state.agent?.name ?: "Agent",
                status = state.status,
                onStatusChange = viewModel::setStatus,
                onHistory = onOpenHistory,
                onNotifications = onOpenNotifications,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                PerformanceCard(
                    connected = state.stats.connectedCalls,
                    total = state.stats.totalCalls,
                    rate = state.stats.connectionRate,
                )
                Spacer(Modifier.height(16.dp))
                if (state.stats.newLeads > 0) {
                    NewLeadsBanner(count = state.stats.newLeads, onOpen = onOpenLeads)
                    Spacer(Modifier.height(16.dp))
                }
                SectionHeader("Today's overview")
                StatGrid(state)
                Spacer(Modifier.height(20.dp))
                SectionHeader("Quick actions")
                QuickActions(
                    onOpenLeads = onOpenLeads,
                    onOpenCallbacks = onOpenCallbacks,
                    onOpenHistory = onOpenHistory,
                    onOpenCampaigns = onOpenCampaigns,
                    onOpenReports = onOpenReports,
                    onOpenNotifications = onOpenNotifications,
                    onOpenSettings = onOpenSettings,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DashboardHero(
    agentName: String,
    status: AgentStatus,
    onStatusChange: (AgentStatus) -> Unit,
    onHistory: () -> Unit,
    onNotifications: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeaderShape)
            .background(AppGradients.brandHeader())
            .padding(WindowPaddings),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = agentName.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Welcome back", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    Text(
                        text = agentName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onHistory() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = "Call history",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onNotifications() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            StatusSelector(status, onStatusChange)
        }
    }
}

private val WindowPaddings = androidx.compose.foundation.layout.PaddingValues(
    start = 16.dp, end = 16.dp, top = 36.dp, bottom = 14.dp,
)

@Composable
private fun StatusSelector(current: AgentStatus, onChange: (AgentStatus) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppColor.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "YOUR STATUS",
                color = AppColor.ink500,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentStatus.entries.forEach { st ->
                    val selected = st == current
                    val color = colorForAgentStatus(st)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) color else color.copy(alpha = 0.12f))
                            .clickable { onChange(st) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = st.label,
                            color = if (selected) Color.White else color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceCard(connected: Int, total: Int, rate: Float) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Today's performance",
                        fontSize = 11.sp,
                        color = AppColor.ink500,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = connected.toString(),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColor.ink900,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "/ $total calls",
                            fontSize = 13.sp,
                            color = AppColor.ink500,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Emerald50),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${(rate * 100).toInt()}%",
                        color = Success,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { rate.coerceIn(0f, 1f) },
                color = Success,
                trackColor = AppColor.ink100,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun NewLeadsBanner(count: Int, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Brand500,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.WorkOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$count new leads",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    text = "Tap to review and start calling.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun StatGrid(state: DashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OverviewTile(
                label = "Total calls",
                value = state.stats.totalCalls.toString(),
                icon = Icons.Outlined.Phone,
                tint = Brand600,
                modifier = Modifier.weight(1f),
            )
            OverviewTile(
                label = "Missed",
                value = state.stats.missedCalls.toString(),
                icon = Icons.Outlined.CallMissed,
                tint = Danger,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OverviewTile(
                label = "Follow-ups",
                value = state.stats.callbacksDue.toString(),
                icon = Icons.Outlined.Schedule,
                tint = Warn,
                modifier = Modifier.weight(1f),
            )
            OverviewTile(
                label = "Conversions",
                value = state.stats.conversions.toString(),
                icon = Icons.Outlined.TrendingUp,
                tint = Success,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OverviewTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(10.dp))
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColor.ink900)
                Text(label, fontSize = 11.sp, color = AppColor.ink500)
            }
        }
    }
}

@Composable
private fun QuickActions(
    onOpenLeads: () -> Unit,
    onOpenCallbacks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCampaigns: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("Campaigns", Icons.Outlined.Campaign, AccentViolet, onOpenCampaigns, Modifier.weight(1f))
            QuickTile("Reports", Icons.Outlined.Assessment, AccentMint, onOpenReports, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColor.ink900)
        }
    }
}
