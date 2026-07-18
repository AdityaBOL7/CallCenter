package com.example.callcenter.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.repository.AgentRepository
import com.example.callcenter.data.repository.CallsRepository
import com.example.callcenter.domain.model.Agent
import com.example.callcenter.domain.model.Call
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.reachedCustomer
import com.example.callcenter.ui.components.AppCard
import com.example.callcenter.ui.components.SectionHeader
import com.example.callcenter.ui.theme.AccentAmber
import com.example.callcenter.ui.theme.AccentSky
import com.example.callcenter.ui.theme.AccentViolet
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Danger
import com.example.callcenter.ui.theme.HeaderShape
import com.example.callcenter.ui.theme.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Today's call performance for the logged-in agent. Computed from the agent's own
 * call list (GET /calls/) filtered to today — the backend doesn't expose these
 * splits directly, so we aggregate per-call status / disposition / duration /
 * start-hour.
 */
data class TodayReport(
    val agentName: String = "Agent",
    val totalCalls: Int = 0,
    val connected: Int = 0,
    val talkTimeSec: Int = 0,
    // "Interested" outcomes (interested + converted) — the win metric.
    val conversions: Int = 0,
    // Today's calls dispositioned as a follow-up.
    val followUps: Int = 0,
    // Today's calls that have a recording attached.
    val recordings: Int = 0,
    /** Calls bucketed by hour-of-day (0..23) → count, only hours with activity. */
    val byHour: Map<Int, Int> = emptyMap(),
) {
    val notConnected: Int get() = (totalCalls - connected).coerceAtLeast(0)
    val connectionRate: Float get() = if (totalCalls == 0) 0f else connected.toFloat() / totalCalls
    /** Hour with the most calls (for the "Peak" label), null if no calls. */
    val peakHour: Int? get() = byHour.maxByOrNull { it.value }?.key
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val callsRepo: CallsRepository,
    private val agentRepo: AgentRepository,
) : ViewModel() {

    private val _report = MutableStateFlow(TodayReport())
    val report = _report.asStateFlow()

    init {
        viewModelScope.launch {
            combine(callsRepo.observeHistory(), agentRepo.agent) { calls, agent ->
                summarizeToday(calls, agent)
            }.collect { _report.value = it }
        }
        viewModelScope.launch { callsRepo.refreshHistory() }
    }

    private fun summarizeToday(calls: List<Call>, agent: Agent?): TodayReport {
        val today = LocalDate.now()
        val todays = calls.filter { it.startedAt.toLocalDate() == today }

        // "Connected" = a human actually answered — the shared product rule
        // lives on the model (Call.reachedCustomer) so Reports and the Profile
        // snapshot can never disagree.
        return TodayReport(
            agentName = agent?.name?.takeIf { it.isNotBlank() } ?: "Agent",
            totalCalls = todays.size,
            connected = todays.count { it.reachedCustomer() },
            talkTimeSec = todays.sumOf { it.durationSec },
            conversions = todays.count {
                it.disposition == Disposition.CONVERTED || it.disposition == Disposition.INTERESTED
            },
            followUps = todays.count { it.disposition == Disposition.FOLLOW_UP },
            recordings = todays.count { it.hasRecording },
            byHour = todays.groupingBy { it.startedAt.hour }.eachCount(),
        )
    }

}

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColor.bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Hero is STATIC — pinned at the top, does not scroll.
            ReportHero(report, onBack = onBack)
            // Only the content below scrolls; weight(1f) lets it fill the rest.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---- Two rows of headline stat cards ----
                // Row 1: Total / Connected / Interested
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat(
                        value = report.totalCalls.toString(), label = "Total calls",
                        dot = Brand500, modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        value = report.connected.toString(), label = "Connected",
                        dot = Success, modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        value = report.conversions.toString(), label = "Interested",
                        dot = AccentAmber, modifier = Modifier.weight(1f),
                    )
                }
                // Row 2: Follow-ups / Recordings / Total time (all today).
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat(
                        value = report.followUps.toString(), label = "Follow-ups",
                        dot = AccentSky, modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        value = report.recordings.toString(), label = "Recordings",
                        dot = AccentViolet, modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        value = formatTalkTime(report.talkTimeSec), label = "Total time",
                        dot = Success, modifier = Modifier.weight(1f),
                    )
                }

                // ---- Connection breakdown (donut + legend) ----
                SectionHeader("Connection breakdown")
                AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        DonutRing(
                            fraction = report.connectionRate,
                            color = Success,
                            track = Danger.copy(alpha = 0.18f),
                            centerTop = "${(report.connectionRate * 100).toInt()}%",
                            centerBottom = "connected",
                            size = 124.dp,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegendRow(color = Success, value = report.connected, label = "Connected")
                            LegendRow(color = Danger, value = report.notConnected, label = "Not connected")
                        }
                    }
                }

                // ---- Calls by hour ----
                SectionHeader("Calls by hour")
                AppCard {
                    CallsByHourChart(report)
                }
            }
        }
    }
}

/** Gradient hero: greeting + name + avatar, big talk-time, and a conn-rate donut. */
@Composable
private fun ReportHero(report: TodayReport, onBack: () -> Unit) {
    val ratePct = (report.connectionRate * 100).toInt()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeaderShape)
            .background(AppGradients.brandHeader())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
    ) {
        Column {
            // Back button row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("Reports", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(18.dp))

            // Greeting row: greeting + name on the left, avatar on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(timeGreeting(), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        report.agentName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                AvatarInitials(report.agentName)
            }

            Spacer(Modifier.height(28.dp))

            // Talk time (big) on the left, conn-rate donut on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "TALK TIME TODAY",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatTalkTime(report.talkTimeSec),
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${report.totalCalls} calls",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                }
                // Compact ring on the gradient (white track + white fill).
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(96.dp)) {
                        val stroke = 10.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            color = Color.White.copy(alpha = 0.25f),
                            startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = -90f, sweepAngle = 360f * report.connectionRate.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$ratePct%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("conn rate", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarInitials(name: String) {
    val initials = name.trim().split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2).joinToString("")
        .ifBlank { "A" }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Time-of-day greeting matching the mockup. */
private fun timeGreeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Good morning,"
        h < 17 -> "Good afternoon,"
        else -> "Good evening,"
    }
}

/** Human-readable talk-time: "3h 24m" / "12m 30s" / "45s". */
private fun formatTalkTime(totalSec: Int): String {
    if (totalSec <= 0) return "0s"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

@Composable
private fun MiniStat(
    value: String,
    label: String,
    dot: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = AppColor.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColor.ink100),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                // Sized to fit a wide value like "2m 17s" in the one-third-width
                // box; clips with an ellipsis rather than bleeding into the
                // neighbour if it's ever longer.
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = AppColor.ink900,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(label, fontSize = 11.sp, color = AppColor.ink500, maxLines = 1)
        }
    }
}

@Composable
private fun LegendRow(color: Color, value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColor.ink900)
            Text(label, fontSize = 12.sp, color = AppColor.ink500)
        }
    }
}

/** A ring that fills [fraction] of the circle in [color] over a [track] remainder. */
@Composable
private fun DonutRing(
    fraction: Float,
    color: Color,
    track: Color,
    centerTop: String,
    centerBottom: String,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            // Track (full circle).
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Filled fraction, starting at top (−90°).
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColor.ink900)
            Text(centerBottom, fontSize = 11.sp, color = AppColor.ink500)
        }
    }
}

/**
 * Bar chart of today's calls over a fixed 9 AM–6 PM working window. Every hour
 * gets its own bar; hours with no calls show an empty track (0 height) so the
 * axis stays steady and readable instead of collapsing to a single block.
 */
@Composable
private fun CallsByHourChart(report: TodayReport) {
    val window = (CHART_START_HOUR..CHART_END_HOUR).toList()
    val maxCount = (report.byHour.values.maxOrNull() ?: 0).coerceAtLeast(1)
    // Peak only counts hours inside the visible window.
    val peak = window.filter { (report.byHour[it] ?: 0) > 0 }
        .maxByOrNull { report.byHour[it] ?: 0 }
    val totalInWindow = window.sumOf { report.byHour[it] ?: 0 }

    Column {
        // Peak label (or a neutral hint if there are no calls in the window yet).
        Text(
            text = if (peak != null) "Peak ${hourLabel(peak)}" else "No calls yet",
            color = if (peak != null) Success else AppColor.ink400,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.End),
        )
        Spacer(Modifier.height(12.dp))

        val maxBarHeight = 110.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxBarHeight + 18.dp), // bars + hour labels
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            window.forEach { h ->
                val count = report.byHour[h] ?: 0
                val frac = count.toFloat() / maxCount
                val isPeak = h == peak
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // Bar: full color when there are calls; a faint stub when empty.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (count == 0) 4.dp else (8.dp + maxBarHeight * frac))
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(
                                when {
                                    count == 0 -> AppColor.ink100
                                    isPeak -> Success
                                    else -> Brand500
                                },
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(shortHour(h), fontSize = 10.sp, color = AppColor.ink400)
                }
            }
        }

        if (totalInWindow < report.totalCalls) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${report.totalCalls - totalInWindow} call(s) outside 9 AM–6 PM",
                fontSize = 11.sp,
                color = AppColor.ink400,
            )
        }
    }
}

/** Fixed working-hours window for the chart (9 AM … 6 PM inclusive). */
private const val CHART_START_HOUR = 9
private const val CHART_END_HOUR = 18

private fun shortHour(h: Int): String = when {
    h == 0 -> "12"
    h <= 12 -> "$h"
    else -> "${h - 12}"
}

private fun hourLabel(h: Int): String {
    val ampm = if (h < 12) "AM" else "PM"
    val twelve = when {
        h == 0 -> 12
        h <= 12 -> h
        else -> h - 12
    }
    return "$twelve $ampm"
}
