package com.example.callcenter.ui.screens.callbacks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.components.SectionHeader
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
    var at by remember { mutableStateOf<LocalDateTime?>(LocalDateTime.now().plusHours(1)) }
    var note by remember { mutableStateOf("") }

    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Schedule callback", onBack = onDone)
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionHeader(state.lead?.name ?: "Lead")
                Text(state.lead?.phone ?: "")
                DateTimeField(value = at, onValueChange = { at = it }, label = "Callback at")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Optional notes…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(14.dp),
                )
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
