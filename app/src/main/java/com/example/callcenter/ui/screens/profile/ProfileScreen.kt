package com.example.callcenter.ui.screens.profile

import com.example.callcenter.BuildConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.example.callcenter.data.prefs.AppPreferences
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.AuthRepository
import com.example.callcenter.domain.model.Agent
import com.example.callcenter.domain.model.AgentStats
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.ui.components.colorForAgentStatus
import com.example.callcenter.ui.theme.AccentAmber
import com.example.callcenter.ui.theme.AccentMint
import com.example.callcenter.ui.theme.AccentSky
import com.example.callcenter.ui.theme.AccentViolet
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.HeaderShape
import com.example.callcenter.ui.theme.Success
import com.example.callcenter.ui.theme.Warn
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.callcenter.domain.model.reachedCustomer
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val agentRepo: AgentRepository,
    private val authRepo: AuthRepository,
    private val appPrefs: AppPreferences,
    private val callsRepo: com.example.callcenter.data.repository.CallsRepository,
) : ViewModel() {
    data class State(
        val agent: Agent? = null,
        val stats: AgentStats = AgentStats(),
        val notice: String? = null,
        val provisioned: Boolean = false,   // true once me/ returns a real Agent row
        val updatingStatus: Boolean = false,
        // One-shot toast when a status change fails — silent failure here made a
        // dead session look like a broken button (2026-07-16).
        val statusError: String? = null,
        val themeOverride: String = "system",   // "system" | "light" | "dark"
    )
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                agentRepo.agent,
                agentRepo.stats,
                agentRepo.profileError,
                callsRepo.observeHistory(),
            ) { a, s, err, history ->
                // "N / M connected" on the snapshot card must use the SAME
                // definition as the Reports screen (Call.reachedCustomer — a
                // human actually answered), computed from today's real call
                // history. The backend's connected_calls total uses its own
                // definition and would contradict Reports.
                val today = java.time.LocalDate.now()
                val todays = history.filter { it.startedAt.toLocalDate() == today }
                Triple(
                    a,
                    s.copy(
                        totalCalls = todays.size,
                        connectedCalls = todays.count { it.reachedCustomer() },
                    ),
                    err,
                )
            }.collect { (a, s, err) ->
                // A real Agent row has a non-zero id (the login fallback uses id=0).
                _state.update {
                    it.copy(agent = a, stats = s, notice = err, provisioned = (a?.id ?: 0) > 0)
                }
            }
        }
        viewModelScope.launch {
            appPrefs.prefs.collect { p ->
                _state.update { it.copy(themeOverride = p.themeOverride) }
            }
        }
        viewModelScope.launch { agentRepo.loadProfile() }
        // Today's-snapshot mini-stats (Follow-ups / Missed / Wins) read from
        // agentRepo.stats, which is ONLY filled by the /dashboard/ call — the
        // Profile screen must load it itself, otherwise the numbers sit at 0
        // unless the user happened to open Home first.
        viewModelScope.launch { agentRepo.loadDashboard() }
        // The connected/total override above needs today's call history too.
        viewModelScope.launch { callsRepo.refreshHistory() }
    }

    /** Re-pull the snapshot numbers (e.g. on screen resume). */
    fun refreshStats() {
        viewModelScope.launch { agentRepo.loadDashboard() }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { appPrefs.setTheme(value) }
    }

    fun setStatus(status: AgentStatus) {
        viewModelScope.launch {
            _state.update { it.copy(updatingStatus = true) }
            val result = agentRepo.setStatus(status)
            _state.update {
                it.copy(
                    updatingStatus = false,
                    statusError = result.exceptionOrNull()?.let { e ->
                        if ((e as? retrofit2.HttpException)?.code() == 401) {
                            "Session expired — please log in again."
                        } else {
                            "Couldn't change status. Check your connection."
                        }
                    },
                )
            }
        }
    }

    fun clearStatusError() {
        _state.update { it.copy(statusError = null) }
    }

    @Volatile private var signingOut = false

    fun signOut(onDone: () -> Unit) {
        // Guard against a double-tap launching two logout flows.
        if (signingOut) return
        signingOut = true
        viewModelScope.launch {
            // Best-effort server logout, but never let a hanging endpoint block the
            // user from leaving — cap it at 3s.
            withTimeoutOrNull(3_000) { agentRepo.serverLogout() }
            // Local cleanup must complete even though logout() flips authStatus,
            // which navigates away and cancels this scope. NonCancellable ensures
            // tokens are actually cleared.
            withContext(NonCancellable) {
                authRepo.logout()   // clears tokens + flips authStatus → nav to Login
                agentRepo.clear()   // wipe in-memory profile/stats
            }
            onDone()
        }
    }
}

@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onReports: () -> Unit,
    onHelp: () -> Unit,
    onTerms: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Surface a failed status change as a toast (one-shot).
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(state.statusError) {
        state.statusError?.let {
            android.widget.Toast.makeText(toastContext, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearStatusError()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColor.bg)
    ) {
        Column(Modifier.fillMaxSize()) {
            ProfileHero(
                agent = state.agent,
                onEdit = onEditProfile,
                // Extra gradient below the avatar row hosts the top half of the
                // overlapping snapshot card.
                extraBottomPadding = if (state.provisioned) 80.dp else 0.dp,
            )
            // Snapshot stats are computed from the Calls/Callbacks APIs — hidden
            // until that data is real, to avoid fake zeros. The card straddles
            // the hero's bottom edge (top half on the gradient).
            if (state.provisioned) {
                SnapshotCard(
                    stats = state.stats,
                    modifier = Modifier
                        .overlapTop(78.dp)
                        .padding(horizontal = 18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.notice != null) {
                    NoticeBanner(state.notice!!)
                }

                // Presence control — only meaningful once provisioned as a dialer agent.
                if (state.provisioned && state.agent != null) {
                    ProfileStatusSelector(
                        current = state.agent!!.status,
                        updating = state.updatingStatus,
                        onChange = viewModel::setStatus,
                    )
                }

                SectionLabel("TELEPHONY")
                GroupedCard {
                    InfoRow(
                        icon = Icons.Outlined.AlternateEmail,
                        tint = AccentSky,
                        label = "Username",
                        value = state.agent?.username.orDash(),
                    )
                    RowDivider()
                    InfoRow(
                        icon = Icons.Outlined.Phone,
                        tint = Success,
                        label = "Extension",
                        value = state.agent?.extension.orDash(),
                    )
                    RowDivider()
                    InfoRow(
                        icon = Icons.Outlined.Storage,
                        tint = AccentViolet,
                        label = "SIP user",
                        value = state.agent?.sipUsername.orDash(),
                    )
                }

                SectionLabel("PREFERENCES")
                GroupedCard {
                    NavRow(
                        icon = Icons.Outlined.Settings,
                        tint = Brand500,
                        label = "Settings",
                        onClick = onSettings,
                    )
                }

                SectionLabel("APPEARANCE")
                ThemeSegmented(
                    selected = state.themeOverride,
                    onSelect = viewModel::setTheme,
                )

                SectionLabel("SUPPORT")
                GroupedCard {
                    NavRow(
                        icon = Icons.Outlined.SupportAgent,
                        tint = AccentMint,
                        label = "Help & feedback",
                        onClick = onHelp,
                    )
                    RowDivider()
                    NavRow(
                        icon = Icons.Outlined.Article,
                        tint = AccentViolet,
                        label = "Terms & privacy",
                        onClick = onTerms,
                    )
                }

                Spacer(Modifier.size(4.dp))
                SignOutButton(onClick = { viewModel.signOut(onSignOut) })

                Text(
                    "Dialer Agent • v${BuildConfig.VERSION_NAME}",
                    color = AppColor.ink500,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProfileHero(
    agent: Agent?,
    onEdit: () -> Unit,
    extraBottomPadding: Dp = 0.dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeaderShape)
            .background(AppGradients.brandHeader())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp + extraBottomPadding),
    ) {
        Column {
            Text(
                "Profile",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.48).sp,
            )
            Spacer(Modifier.size(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The avatar is the ONLY edit affordance now — tap it to open the
                // profile-photo picker (Edit Profile). A small camera badge signals
                // it's editable.
                Box {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .clickable { onEdit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!agent?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = agent?.avatarUrl,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                agent?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onEdit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = "Change photo",
                            tint = Brand600,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        agent?.name ?: "Agent",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        agent?.email ?: "",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (agent != null) {
                        Spacer(Modifier.size(6.dp))
                        StatusInlinePill(label = agent.status.label.uppercase(), color = colorForAgentStatus(agent.status))
                    }
                }
            }
        }
    }
}

/**
 * Draws the element [overlap] higher than its layout slot and reports a height
 * reduced by the same amount — lets the snapshot card straddle the hero's
 * bottom edge without leaving a gap in the flow below it.
 */
private fun Modifier.overlapTop(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = overlap.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
        placeable.place(0, -shift)
    }
}

@Composable
private fun StatusInlinePill(label: String, color: Color) {
    // Solid status-colored pill (white dot + label) so it reads on the gradient.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(Color.White))
        Spacer(Modifier.size(5.dp))
        Text(label, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun SnapshotCard(stats: AgentStats, modifier: Modifier = Modifier) {
    // Straddles the gradient hero's edge, so it carries its own lift (no border).
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppColor.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TODAY'S SNAPSHOT",
                        color = AppColor.micro,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.05.sp,
                    )
                    Spacer(Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            stats.connectedCalls.toString(),
                            fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp,
                            color = AppColor.ink900,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "/ ${stats.totalCalls} connected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColor.ink500,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Success.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${(stats.connectionRate * 100).toInt()}%",
                        color = AppColor.successText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            LinearProgressIndicator(
                progress = { stats.connectionRate.coerceIn(0f, 1f) },
                color = Success,
                trackColor = AppColor.ink100,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.size(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                MiniStat(icon = Icons.Outlined.Schedule, tint = Brand500, value = stats.callbacksDue.toString(), label = "Follow-ups")
                MiniStat(icon = Icons.Outlined.CallMissed, tint = Danger, value = stats.missedCalls.toString(), label = "Missed")
                MiniStat(icon = Icons.Outlined.EmojiEvents, tint = AccentAmber, value = stats.conversions.toString(), label = "Wins")
            }
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, tint: Color, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(6.dp))
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AppColor.ink900)
        Text(label, color = AppColor.ink500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "—"

@Composable
private fun ProfileStatusSelector(
    current: AgentStatus,
    updating: Boolean,
    onChange: (AgentStatus) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "YOUR STATUS",
                    color = AppColor.ink500,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f),
                )
                if (updating) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Brand500,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentStatus.entries.forEach { st ->
                    val selected = st == current
                    val color = colorForAgentStatus(st)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) color else color.copy(alpha = 0.12f))
                            .clickable(enabled = !updating) { if (!selected) onChange(st) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = st.label,
                            color = if (selected) Color.White else color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Warn.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Warn.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = Warn, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(message, color = AppColor.ink700, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AppColor.micro,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.05.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 56.dp)
            .background(AppColor.ink200),
    )
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(label, color = AppColor.ink900, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(10.dp))
        // Value flexes and ellipsizes so long emails/usernames can't collide
        // with the label (the old layout let the value overflow into it).
        Text(
            value,
            color = AppColor.ink500,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(label, color = AppColor.ink900, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = AppColor.ink400, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ThemeSegmented(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        Triple("system", "System", Icons.Outlined.PhoneAndroid),
        Triple("light", "Light", Icons.Outlined.LightMode),
        Triple("dark", "Dark", Icons.Outlined.DarkMode),
    )
    // Standalone segmented track: gray rail with a floating white pill on the
    // selected option.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColor.ink100)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label, icon) ->
            val isSelected = selected == key
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected) Modifier.shadow(2.dp, RoundedCornerShape(11.dp)) else Modifier
                    )
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) AppColor.surface else Color.Transparent)
                    .clickable { if (!isSelected) onSelect(key) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) AppColor.ink900 else AppColor.ink500,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    label,
                    color = if (isSelected) AppColor.ink900 else AppColor.ink500,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Danger)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Sign out", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
