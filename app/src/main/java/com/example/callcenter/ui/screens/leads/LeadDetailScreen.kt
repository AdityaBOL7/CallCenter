package com.example.callcenter.ui.screens.leads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.navigation.Dest
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.AppButtonVariant
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.components.StatusBadge
import com.example.callcenter.ui.components.colorForLeadPriority
import com.example.callcenter.ui.components.colorForLeadStatus
import com.example.callcenter.ui.theme.AppBg
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Ink900
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LeadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val leadsRepo: LeadsRepository,
    private val callsRepo: CallsRepository,
) : ViewModel() {
    data class State(val lead: Lead? = null, val loading: Boolean = true)
    private val id: Int = savedStateHandle.get<String>(Dest.LeadDetail.ARG)?.toIntOrNull() ?: 0
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(lead = leadsRepo.byId(id), loading = false) }
        }
    }

    fun startCall(onCall: (callId: String, route: String) -> Unit) {
        viewModelScope.launch {
            val lead = _state.value.lead ?: return@launch
            val call = callsRepo.initiate(lead, CallRouteType.SIP)
            onCall(call.id, call.routeType.name)
        }
    }
}

@Composable
fun LeadDetailScreen(
    leadId: Int,
    onBack: () -> Unit,
    onCall: (callId: String, route: String) -> Unit,
    onScheduleCallback: () -> Unit,
    viewModel: LeadDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Box(Modifier
        .fillMaxSize()
        .background(AppBg)) {
        Column(Modifier.fillMaxSize()) {
            GradientHeader(title = "Lead details", onBack = onBack)
            val lead = state.lead
            if (lead == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lead not found", color = Ink500)
                }
            } else {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(colorForLeadStatus(lead.status).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                lead.name.first().uppercaseChar().toString(),
                                fontWeight = FontWeight.Bold,
                                color = colorForLeadStatus(lead.status),
                                fontSize = 22.sp,
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lead.name, fontWeight = FontWeight.Bold, color = Ink900, fontSize = 18.sp)
                            Text(lead.phone, color = Ink500)
                        }
                        StatusBadge(text = lead.status.label, color = colorForLeadStatus(lead.status))
                    }
                    SectionHeader("Contact")
                    KeyValueRow("Phone", lead.phone)
                    if (!lead.email.isNullOrBlank()) KeyValueRow("Email", lead.email)
                    if (!lead.address.isNullOrBlank()) KeyValueRow("Address", lead.address)
                    SectionHeader("Campaign")
                    KeyValueRow("Campaign", lead.campaignName)
                    KeyValueRow("Priority", lead.priority.label)
                    if (!lead.notes.isNullOrBlank()) {
                        SectionHeader("Notes")
                        Text(lead.notes, color = Ink500)
                    }
                    Spacer(Modifier.size(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppButton(
                            text = "Call now",
                            onClick = { viewModel.startCall(onCall) },
                            leadingIcon = Icons.Outlined.Phone,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = "Schedule",
                            onClick = onScheduleCallback,
                            variant = AppButtonVariant.Ghost,
                            leadingIcon = Icons.Outlined.Schedule,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Ink500)
        Text(value, color = Ink900, fontWeight = FontWeight.SemiBold)
    }
}
