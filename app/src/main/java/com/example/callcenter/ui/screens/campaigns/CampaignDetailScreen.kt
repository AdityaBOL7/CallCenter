package com.example.callcenter.ui.screens.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.CampaignsRepository
import com.example.callcenter.domain.model.Campaign
import com.example.callcenter.navigation.Dest
import com.example.callcenter.ui.components.AppCard
import com.example.callcenter.ui.components.PageHeader
import com.example.callcenter.ui.components.ScreenContainer
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.theme.AccentMint
import com.example.callcenter.ui.theme.AppColor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CampaignDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val campaignsRepo: CampaignsRepository,
) : ViewModel() {
    private val id: Int = savedStateHandle.get<String>(Dest.CampaignDetail.ARG)?.toIntOrNull() ?: 0
    private val _state = MutableStateFlow<Campaign?>(null)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { campaignsRepo.byId(id) } }
    }
}

@Composable
fun CampaignDetailScreen(
    campaignId: Int,
    onBack: () -> Unit,
    viewModel: CampaignDetailViewModel = hiltViewModel(),
) {
    val c by viewModel.state.collectAsState()
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = c?.name ?: "Campaign", onBack = onBack)
            if (c != null) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(c!!.description, color = AppColor.ink500)
                            Spacer(Modifier.size(6.dp))
                            LinearProgressIndicator(
                                progress = { c!!.progress },
                                color = AccentMint,
                                trackColor = AppColor.ink100,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${c!!.contactedLeads}/${c!!.totalLeads} contacted", color = AppColor.ink500, fontSize = 12.sp)
                                Text("${(c!!.progress * 100).toInt()}%", color = AppColor.ink500, fontSize = 12.sp)
                            }
                        }
                    }
                    SectionHeader("Performance")
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Conversions: ${c!!.conversions}", fontWeight = FontWeight.SemiBold)
                            Text("Conversion rate: ${"%.1f".format(c!!.conversions.toFloat() / c!!.totalLeads * 100)}%")
                        }
                    }
                }
            }
        }
    }
}
