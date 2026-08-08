package com.example.callcenter.ui.screens.leads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notes
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.navigation.Dest
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.AppButtonSize
import com.example.callcenter.ui.components.AppButtonVariant
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.StatusBadge
import com.example.callcenter.ui.components.colorForLeadPriority
import com.example.callcenter.ui.components.colorForLeadStatus
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Success
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
    private val agentRepo: AgentRepository,
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

    fun startCall(
        onCall: (callId: String, route: String) -> Unit,
        onNotAvailable: () -> Unit = {},
        onBlocked: (callId: String, leadId: Int) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            val lead = _state.value.lead ?: return@launch
            agentRepo.loadProfile()
            // Agents may only call while Available; use their assigned dial mode.
            if (!callsRepo.canCall()) {
                onNotAvailable()
                return@launch
            }
            // Block a new call while a previous one is still un-dispositioned, and
            // hand that call back so the screen can route to its outcome form.
            val openCall = callsRepo.openCall()
            if (openCall != null) {
                onBlocked(openCall.id, openCall.leadId)
                return@launch
            }
            val route = agentRepo.agent.value?.callMode ?: CallRouteType.SIP
            val call = callsRepo.initiate(lead, route)
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
    // Where a refused dial goes: the outcome form of the call that's still open.
    onResumeDisposition: (callId: String, leadId: Int) -> Unit = { _, _ -> },
    viewModel: LeadDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier
        .fillMaxSize()
        .background(AppColor.bg)) {
        Column(Modifier.fillMaxSize()) {
            val lead = state.lead
            // Extra gradient below the title so the identity card can straddle its
            // edge (top half on the gradient, like Home/Profile).
            GradientHeader(
                title = "Lead details",
                onBack = onBack,
                bottomExtra = if (lead != null) 44.dp else 0.dp,
            )
            if (lead == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lead not found", color = AppColor.ink500)
                }
            } else {
                val statusColor = colorForLeadStatus(lead.status)
                Column(Modifier.weight(1f)) {
                    // Identity card straddles the hero's edge. It MUST live outside
                    // the scroll Column — verticalScroll clips to its bounds, which
                    // would slice off the overlapping top of the card.
                    IdentityCard(
                        lead = lead,
                        statusColor = statusColor,
                        modifier = Modifier
                            .overlapTop(42.dp)
                            .padding(horizontal = 18.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Section("CONTACT") {
                                InfoRow(Icons.Outlined.Phone, Brand500, "Phone", lead.phone)
                                if (!lead.email.isNullOrBlank()) {
                                    RowDivider()
                                    InfoRow(Icons.Outlined.Email, Success, "Email", lead.email)
                                }
                                if (!lead.address.isNullOrBlank()) {
                                    RowDivider()
                                    InfoRow(Icons.Outlined.LocationOn, statusColor, "Address", lead.address)
                                }
                            }

                            Section("CAMPAIGN") {
                                InfoRow(
                                    Icons.Outlined.Campaign, Brand500, "Campaign",
                                    lead.campaignName.takeIf { it.isNotBlank() } ?: "Not set",
                                )
                                RowDivider()
                                InfoRow(
                                    Icons.Outlined.Flag,
                                    colorForLeadPriority(lead.priority),
                                    "Priority",
                                    valuePill = { PriorityPill(lead.priority.label, colorForLeadPriority(lead.priority)) },
                                )
                            }

                            if (!lead.notes.isNullOrBlank()) {
                                Section("NOTES") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        IconTile(Icons.Outlined.Notes, AppColor.ink500)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            lead.notes,
                                            color = AppColor.ink700,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 20.sp,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    // Docked action row.
                    Row(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppButton(
                            text = "Call now",
                            onClick = {
                                viewModel.startCall(
                                    onCall = onCall,
                                    onNotAvailable = {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Set your status to Available to start calling.",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    // Refusing alone stranded the agent — take them
                                    // to the outcome still owed instead.
                                    onBlocked = { openCallId, openLeadId ->
                                        android.widget.Toast.makeText(
                                            context,
                                            "Finish the current call's outcome first.",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        onResumeDisposition(openCallId, openLeadId)
                                    },
                                )
                            },
                            variant = AppButtonVariant.Success,
                            leadingIcon = Icons.Outlined.Phone,
                            size = AppButtonSize.Lg,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = "Schedule",
                            onClick = onScheduleCallback,
                            variant = AppButtonVariant.Ghost,
                            leadingIcon = Icons.Outlined.Schedule,
                            size = AppButtonSize.Lg,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** White identity card: initial tile + name + phone + status badge. */
@Composable
private fun IdentityCard(lead: Lead, statusColor: Color, modifier: Modifier = Modifier) {
    val statusLabel = if (lead.status.label.equals("Follow-up", true)) "CALLBACK" else lead.status.label.uppercase()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppColor.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(statusColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    lead.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor,
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(lead.name, fontWeight = FontWeight.ExtraBold, color = AppColor.ink900, fontSize = 17.sp)
                Spacer(Modifier.height(2.dp))
                Text(lead.phone, color = AppColor.ink500, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            StatusBadge(text = statusLabel, color = statusColor)
        }
    }
}

/** Micro-eyebrow label + a grouped white card of rows. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            color = AppColor.micro,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.05.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = AppColor.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 2.dp,
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/**
 * A "icon · label ......... value" row. Pass either a plain [value] string or a
 * [valuePill] composable (used for the Priority chip).
 */
@Composable
private fun InfoRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String? = null,
    valuePill: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, color = AppColor.ink900, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (valuePill != null) {
                valuePill()
            } else {
                Text(
                    value.orEmpty(),
                    color = AppColor.ink500,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun PriorityPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 58.dp)
            .background(AppColor.ink200),
    )
}

/**
 * Draws the element [overlap] higher than its layout slot and reports a height
 * reduced by the same amount — lets a card straddle the hero's bottom edge with
 * no gap in the flow below.
 */
private fun Modifier.overlapTop(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = overlap.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
        placeable.place(0, -shift)
    }
}
