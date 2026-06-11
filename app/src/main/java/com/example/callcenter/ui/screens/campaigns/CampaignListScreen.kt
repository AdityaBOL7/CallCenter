package com.example.callcenter.ui.screens.campaigns

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.CampaignsRepository
import com.example.callcenter.domain.model.Campaign
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.LoadingState
import com.example.callcenter.ui.components.StatusBadge
import com.example.callcenter.ui.theme.AccentMint
import com.example.callcenter.ui.theme.AppColor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CampaignListViewModel @Inject constructor(
    private val campaignsRepo: CampaignsRepository,
) : ViewModel() {
    data class State(val items: List<Campaign> = emptyList(), val loading: Boolean = true)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            campaignsRepo.observeCampaigns().collect { _state.value = State(items = it, loading = false) }
        }
        viewModelScope.launch { campaignsRepo.refresh() }
    }
}

@Composable
fun CampaignListScreen(
    onBack: () -> Unit,
    onCampaign: (Int) -> Unit,
    viewModel: CampaignListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Box(Modifier
        .fillMaxSize()
        .background(AppColor.bg)) {
        Column(Modifier.fillMaxSize()) {
            GradientHeader(title = "Campaigns", subtitle = "${state.items.size} total", onBack = onBack)
            if (state.loading) LoadingState() else LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { c ->
                    CampaignRow(c, onClick = { onCampaign(c.id) })
                }
            }
        }
    }
}

@Composable
private fun CampaignRow(c: Campaign, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Bold, color = AppColor.ink900, fontSize = 15.sp)
                    Text(c.description, color = AppColor.ink500, fontSize = 12.sp)
                }
                StatusBadge(
                    text = if (c.active) "Active" else "Paused",
                    color = if (c.active) AccentMint else AppColor.ink500,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { c.progress.coerceIn(0f, 1f) },
                color = AccentMint,
                trackColor = AppColor.ink100,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${c.contactedLeads}/${c.totalLeads} contacted • ${c.conversions} wins",
                color = AppColor.ink500,
                fontSize = 11.sp,
            )
        }
    }
}
