package com.example.callcenter.ui.screens.callbacks

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
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.callcenter.data.repository.CallbacksRepository
import com.example.callcenter.data.repository.LeadsRepository
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.navigation.Dest
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.AppButtonSize
import com.example.callcenter.ui.components.DateTimeField
import com.example.callcenter.ui.components.LocalDateTimeSaver
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleCallbackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val callbacksRepo: CallbacksRepository,
    private val leadsRepo: LeadsRepository,
) : ViewModel() {

    private val leadId: Int = savedStateHandle.get<String>(Dest.ScheduleCallback.ARG)?.toIntOrNull() ?: 0

    data class State(val lead: Lead? = null, val saving: Boolean = false, val done: Boolean = false)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(lead = leadsRepo.byId(leadId)) }
        }
    }

    fun submit(at: LocalDateTime, note: String, onDone: () -> Unit) {
        val lead = _state.value.lead ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            callbacksRepo.schedule(lead.id, lead.name, lead.phone, lead.campaignName, at, note.takeIf { it.isNotBlank() })
            _state.update { it.copy(saving = false, done = true) }
            onDone()
        }
    }
}

@Composable
fun ScheduleCallbackScreen(
    leadId: Int,
    onDone: () -> Unit,
    viewModel: ScheduleCallbackViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // rememberSaveable so the chosen time + note survive rotation / process death.
    var at by rememberSaveable(stateSaver = LocalDateTimeSaver) {
        mutableStateOf<LocalDateTime?>(LocalDateTime.now().plusHours(1))
    }
    var note by rememberSaveable { mutableStateOf("") }

    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Schedule callback", onBack = onDone)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LeadSummaryCard(
                    name = state.lead?.name ?: "Lead",
                    phone = state.lead?.phone ?: "",
                )
                DateTimeField(value = at, onValueChange = { at = it }, label = "Callback at")
                Column {
                    Text(
                        "NOTES",
                        color = AppColor.micro,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.05.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Optional notes…", color = AppColor.ink400) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = Brand500,
                        ),
                    )
                }
            }
            // Docked save CTA. This route lives on the root NavHost (no tab
            // scaffold), so pad for the system nav bar ourselves.
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 18.dp, end = 18.dp, bottom = 12.dp, top = 4.dp),
            ) {
                AppButton(
                    text = "Save callback",
                    onClick = { at?.let { viewModel.submit(it, note, onDone) } },
                    enabled = at != null && state.lead != null,
                    loading = state.saving,
                    fullWidth = true,
                    size = AppButtonSize.Lg,
                )
            }
        }
    }
}

/** Compact lead identity card: soft-indigo initial tile + name + phone. */
@Composable
private fun LeadSummaryCard(name: String, phone: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brand500.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Brand600,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColor.ink900)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = null,
                        tint = AppColor.ink500,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(phone, color = AppColor.ink500, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
