package com.example.callcenter.ui.screens.leads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.callcenter.domain.model.Campaign
import com.example.callcenter.domain.model.LeadSort
import com.example.callcenter.domain.model.LeadStatus
import com.example.callcenter.ui.components.AppBottomSheet
import com.example.callcenter.ui.components.AppButton
import com.example.callcenter.ui.components.EmptyState
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.LoadingState
import com.example.callcenter.ui.components.SearchInput
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600

private enum class PickerKind { Status, Campaign, Sort }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadListScreen(
    onLeadClick: (Int) -> Unit,
    onStartCall: (leadId: Int, callId: String, route: String) -> Unit = { _, _, _ -> },
    viewModel: LeadListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val startingAutoDial by viewModel.startingAutoDial.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    var activePicker by remember { mutableStateOf<PickerKind?>(null) }
    // Transient message shown over the list (empty queue / not-available).
    var banner by remember { mutableStateOf<String?>(null) }

    val statusLabel = state.filters.status?.label ?: "All statuses"
    val campaignLabel = state.campaigns.firstOrNull { it.id == state.filters.campaignId }?.name
        ?: "All campaigns"
    val sortLabel = state.filters.sort.label

    Box(Modifier
        .fillMaxSize()
        .background(AppColor.bg)) {
        Column(Modifier.fillMaxSize()) {
            GradientHeader(
                title = "My Leads",
                subtitle = "${state.leads.size} leads",
                rightSlot = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { viewModel.openImport() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Import", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
            FiltersSection(
                search = state.filters.search,
                onSearch = viewModel::setSearch,
                statusLabel = statusLabel,
                campaignLabel = campaignLabel,
                sortLabel = sortLabel,
                onStatusClick = { activePicker = PickerKind.Status },
                onCampaignClick = { activePicker = PickerKind.Campaign },
                onSortClick = { activePicker = PickerKind.Sort },
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::pullRefresh,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.loading -> LoadingState()
                    // Empty states inside a LazyColumn so the pull gesture works
                    // even with nothing in the list (the most common refresh case).
                    state.leads.isEmpty() && state.error != null -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            EmptyState(
                                title = "Couldn't load leads",
                                description = state.error!!,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                    state.leads.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            EmptyState(
                                title = "No leads match",
                                description = "Try clearing filters or your search.",
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                    else -> LazyColumn(
                        // Extra bottom padding so the floating Start button never
                        // covers the last lead card.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.leads, key = { it.id }) { lead ->
                            LeadCard(lead = lead, onClick = { onLeadClick(lead.id) })
                        }
                    }
                }
            }
        }

        // Floating auto-dial button, pinned bottom-center over the list. The dial
        // mode (SIP/SIM) comes from the agent's backend profile — no picker.
        if (!state.loading) {
            StartCallingButton(
                busy = startingAutoDial,
                onClick = {
                    banner = null
                    viewModel.startAutoDial(
                        onCall = onStartCall,
                        onEmpty = { banner = "No new leads to call right now." },
                        onNotAvailable = {
                            banner = "Set your status to Available to start calling."
                        },
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        banner?.let { msg ->
            InfoBanner(
                text = msg,
                onDismiss = { banner = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }

    when (activePicker) {
        PickerKind.Status -> AppBottomSheet(onDismiss = { activePicker = null }) {
            StatusPicker(
                selected = state.filters.status,
                onPick = {
                    viewModel.setStatus(it)
                    activePicker = null
                },
                onClose = { activePicker = null },
            )
        }
        PickerKind.Campaign -> AppBottomSheet(onDismiss = { activePicker = null }) {
            CampaignPicker(
                campaigns = state.campaigns,
                selectedId = state.filters.campaignId,
                onPick = {
                    viewModel.setCampaign(it)
                    activePicker = null
                },
                onClose = { activePicker = null },
            )
        }
        PickerKind.Sort -> AppBottomSheet(onDismiss = { activePicker = null }) {
            SortPicker(
                selected = state.filters.sort,
                onPick = {
                    viewModel.setSort(it)
                    activePicker = null
                },
                onClose = { activePicker = null },
            )
        }
        null -> {}
    }

    if (state.importOpen) {
        AppBottomSheet(onDismiss = viewModel::closeImport) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Import leads", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Upload a CSV or Excel file. We'll match name, phone, and campaign columns automatically.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppButton(text = "Choose file", onClick = viewModel::closeImport, fullWidth = true)
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun StartCallingButton(
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brand600)
            .clickable(enabled = !busy) { onClick() }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            if (busy) "Preparing…" else "Start calling",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun InfoBanner(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Toast-style banner: inverse colors flip correctly with the theme
    // (dark pill on light theme, light pill on dark theme).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .clickable { onDismiss() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FiltersSection(
    search: String,
    onSearch: (String) -> Unit,
    statusLabel: String,
    campaignLabel: String,
    sortLabel: String,
    onStatusClick: () -> Unit,
    onCampaignClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SearchInput(value = search, onValueChange = onSearch, placeholder = "Search by name or phone")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown(
                label = "STATUS",
                value = statusLabel,
                icon = Icons.Outlined.FilterList,
                onClick = onStatusClick,
                modifier = Modifier.weight(1f),
            )
            FilterDropdown(
                label = "CAMPAIGN",
                value = campaignLabel,
                icon = Icons.Outlined.Campaign,
                onClick = onCampaignClick,
                modifier = Modifier.weight(1f),
            )
            FilterDropdown(
                label = "SORT",
                value = sortLabel,
                icon = Icons.Outlined.SwapVert,
                onClick = onSortClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = AppColor.ink500,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = AppColor.ink500, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                value,
                color = AppColor.ink900,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = AppColor.ink500, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatusPicker(
    selected: LeadStatus?,
    onPick: (LeadStatus?) -> Unit,
    onClose: () -> Unit,
) {
    PickerSheet(title = "Status", onClose = onClose) {
        PickerRow(label = "All statuses", selected = selected == null, onClick = { onPick(null) })
        LeadStatus.entries.forEach { st ->
            val label = if (st == LeadStatus.FOLLOW_UP) "Callback" else st.label
            PickerRow(label = label, selected = selected == st, onClick = { onPick(st) })
        }
    }
}

@Composable
private fun CampaignPicker(
    campaigns: List<Campaign>,
    selectedId: Int?,
    onPick: (Int?) -> Unit,
    onClose: () -> Unit,
) {
    PickerSheet(title = "Campaign", onClose = onClose) {
        PickerRow(label = "All campaigns", selected = selectedId == null, onClick = { onPick(null) })
        campaigns.forEach { c ->
            PickerRow(label = c.name, selected = selectedId == c.id, onClick = { onPick(c.id) })
        }
    }
}

@Composable
private fun SortPicker(
    selected: LeadSort,
    onPick: (LeadSort) -> Unit,
    onClose: () -> Unit,
) {
    PickerSheet(title = "Sort", onClose = onClose) {
        LeadSort.entries.forEach { s ->
            PickerRow(label = s.label, selected = selected == s, onClick = { onPick(s) })
        }
    }
}

@Composable
private fun PickerSheet(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColor.ink900)
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close",
                tint = AppColor.ink700,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onClose() },
            )
        }
        Spacer(Modifier.size(8.dp))
        content()
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            // Brand tint over the theme surface; theme primary for the label so
            // both flip correctly in dark mode (static Brand50 would stay light).
            .background(if (selected) Brand500.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else AppColor.ink900,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = Brand500, modifier = Modifier.size(18.dp))
        }
    }
}
