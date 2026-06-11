package com.example.callcenter.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.domain.model.AgentStats
import com.example.callcenter.ui.components.AppCard
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.components.StatCard
import com.example.callcenter.ui.components.StatTone
import com.example.callcenter.ui.theme.AppColor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val agentRepo: AgentRepository,
) : ViewModel() {
    private val _stats = MutableStateFlow(AgentStats())
    val stats = _stats.asStateFlow()
    init {
        viewModelScope.launch { agentRepo.stats.collect { _stats.value = it } }
    }
}

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    Box(Modifier
        .fillMaxSize()
        .background(AppColor.bg)) {
        Column(Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())) {
            GradientHeader(title = "Reports", subtitle = "Last 7 days", onBack = onBack)
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(label = "Total calls", value = stats.totalCalls.toString(),
                        icon = Icons.Outlined.Phone, tone = StatTone.Brand, modifier = Modifier.weight(1f))
                    StatCard(label = "Missed", value = stats.missedCalls.toString(),
                        icon = Icons.Outlined.CallMissed, tone = StatTone.Danger, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(label = "Follow-ups", value = stats.callbacksDue.toString(),
                        icon = Icons.Outlined.Schedule, tone = StatTone.Warn, modifier = Modifier.weight(1f))
                    StatCard(label = "Conversions", value = stats.conversions.toString(),
                        icon = Icons.Outlined.TrendingUp, tone = StatTone.Success, modifier = Modifier.weight(1f))
                }
                SectionHeader("Activity breakdown")
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Connection rate: ${(stats.connectionRate * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Connected: ${stats.connectedCalls} / ${stats.totalCalls}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("New leads today: ${stats.newLeads}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
