package com.example.callcenter.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.AppButtonSize
import com.example.callcenter.ui.components.DateTimeField
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.components.colorForDisposition
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Ink200
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Ink900
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DispositionViewModel @Inject constructor(
    private val callsRepo: CallsRepository,
    private val leadsRepo: LeadsRepository,
    private val callbacksRepo: CallbacksRepository,
) : ViewModel() {
    fun submit(
        leadId: Int,
        disposition: Disposition,
        note: String,
        callbackAt: LocalDateTime?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            callsRepo.hangup(disposition = disposition, note = note.takeIf { it.isNotBlank() })
            if (disposition == Disposition.FOLLOW_UP && callbackAt != null) {
                val lead = leadsRepo.byId(leadId)
                if (lead != null) {
                    callbacksRepo.schedule(lead.id, lead.name, lead.phone, lead.campaignName, callbackAt, note)
                }
            }
            callsRepo.clearActive()
            onDone()
        }
    }
}

@Composable
fun DispositionScreen(
    callId: String,
    leadId: Int,
    onDone: () -> Unit,
    viewModel: DispositionViewModel = hiltViewModel(),
) {
    var disposition by remember { mutableStateOf<Disposition?>(null) }
    var note by remember { mutableStateOf("") }
    var followUpAt by remember { mutableStateOf<LocalDateTime?>(null) }

    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Call outcome", onBack = onDone)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionHeader("How did the call go?")
                DispositionGrid(disposition) { disposition = it }

                if (disposition == Disposition.FOLLOW_UP) {
                    DateTimeField(
                        value = followUpAt,
                        onValueChange = { followUpAt = it },
                        label = "Callback at",
                    )
                }

                Text(
                    "Notes",
                    color = Ink500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
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
                    text = "Submit outcome",
                    onClick = {
                        val d = disposition ?: return@AppButton
                        viewModel.submit(leadId, d, note, followUpAt, onDone)
                    },
                    enabled = disposition != null,
                    fullWidth = true,
                    size = AppButtonSize.Lg,
                )
            }
        }
    }
}

@Composable
private fun DispositionGrid(selected: Disposition?, onSelect: (Disposition) -> Unit) {
    val items = Disposition.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { d ->
                    DispositionTile(d, selected == d, { onSelect(d) }, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DispositionTile(
    d: Disposition,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = colorForDisposition(d)
    val bg = if (selected) color.copy(alpha = 0.14f) else Color.White
    val borderColor = if (selected) color else Ink200
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Notes,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(d.label, color = Ink900, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
