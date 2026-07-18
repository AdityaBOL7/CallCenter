package com.example.callcenter.ui.screens.leads

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
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
import com.example.callcenter.ui.components.EmptyState
import com.example.callcenter.ui.components.GradientHeader
import com.example.callcenter.ui.components.LoadingState
import com.example.callcenter.ui.components.SearchInput
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.Success

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
    // Delete-all confirm dialog (gated by the client's 6-digit key).
    var showDeleteDialog by remember { mutableStateOf(false) }
    val deleting by viewModel.deleting.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

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
                            .clickable { showDeleteDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Delete new", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        onBlocked = {
                            banner = "Finish the current call's outcome before starting a new one."
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

    if (showDeleteDialog) {
        // Only NEW (un-worked) leads are deletable — worked leads are kept.
        val newLeadCount = state.leads.count { it.status == LeadStatus.NEW }
        DeleteAllDialog(
            newLeadCount = newLeadCount,
            deleting = deleting,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { key ->
                viewModel.deleteAllLeads(key) { error ->
                    if (error == null) {
                        showDeleteDialog = false
                        android.widget.Toast.makeText(context, "New leads deleted", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
}

/**
 * Confirm dialog for "Delete all". The client generates a 6-digit key from their
 * panel and gives it to the agent; entering the matching key authorizes the delete
 * (validated server-side). The Delete button is disabled until 6 digits are entered.
 */
@Composable
private fun DeleteAllDialog(
    newLeadCount: Int,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (key: String) -> Unit,
) {
    // rememberSaveable so a mid-entry rotation doesn't wipe the typed key.
    var key by rememberSaveable { mutableStateOf("") }
    val canDelete = key.length == 6 && !deleting
    Dialog(onDismissRequest = { if (!deleting) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Danger.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = Danger,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                Text(
                    "Delete new leads?",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = AppColor.ink900,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "This permanently deletes your $newLeadCount new (un-worked) " +
                        "${if (newLeadCount == 1) "lead" else "leads"}. Leads you've already " +
                        "contacted or dispositioned are kept. Enter the 6-digit key your " +
                        "client shared to confirm.",
                    color = AppColor.ink500,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(18.dp))
                KeyBoxes(value = key, onValueChange = { key = it }, enabled = !deleting)
                Spacer(Modifier.size(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Cancel — soft neutral pill.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColor.ink100)
                            .clickable(enabled = !deleting) { onDismiss() }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", color = AppColor.ink700, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    // Delete — solid red, armed only once all 6 digits are in.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (canDelete) Danger else Danger.copy(alpha = 0.4f))
                            .clickable(enabled = canDelete) { onConfirm(key) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (deleting) "Deleting…" else "Delete",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Six numeric boxes for the delete key (digits only, capped at 6). */
@Composable
private fun KeyBoxes(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    Box {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = { v -> onValueChange(v.filter { it.isDigit() }.take(6)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            ),
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .focusRequester(focusRequester),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { focusRequester.requestFocus() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(6) { i ->
                val char = value.getOrNull(i)?.toString() ?: ""
                val filled = char.isNotEmpty()
                val active = i == value.length
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        // Idle boxes are soft gray; the active box pops to a white
                        // surface with an indigo ring; filled boxes get a soft tint.
                        .background(
                            when {
                                active -> AppColor.surface
                                filled -> Brand500.copy(alpha = 0.12f)
                                else -> AppColor.ink100
                            }
                        )
                        .border(
                            width = if (active) 2.dp else 1.5.dp,
                            color = when {
                                active -> Brand600
                                filled -> Brand500
                                else -> Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = enabled) { focusRequester.requestFocus() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(char, color = AppColor.ink900, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
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
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(15.dp),
                ambientColor = Success.copy(alpha = 0.3f),
                spotColor = Success.copy(alpha = 0.45f),
            )
            .clip(RoundedCornerShape(15.dp))
            .background(Success)
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
            .padding(horizontal = 18.dp, vertical = 12.dp),
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
    Column(modifier = modifier) {
        Text(
            label,
            color = AppColor.micro,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.05.sp,
            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
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
