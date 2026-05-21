package com.example.callcenter.ui.screens.leads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.domain.model.LeadStatus
import com.example.callcenter.ui.components.colorForLeadPriority
import com.example.callcenter.ui.components.colorForLeadStatus
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Ink900
import com.example.callcenter.ui.theme.Warn
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a")

@Composable
fun LeadCard(
    lead: Lead,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = colorForLeadStatus(lead.status)
    val priorityColor = colorForLeadPriority(lead.priority)
    val statusLabel = if (lead.status == LeadStatus.FOLLOW_UP) "CALLBACK" else lead.status.label.uppercase()
    val statusBadgeColor = if (lead.status == LeadStatus.FOLLOW_UP) Warn else statusColor

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.height(IntrinsicMin)) {
            // Priority strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(priorityColor),
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = lead.name.first().uppercaseChar().toString(),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lead.name, fontWeight = FontWeight.SemiBold, color = Ink900, fontSize = 15.sp)
                        Spacer(Modifier.size(2.dp))
                        IconText(icon = Icons.Outlined.Phone, text = lead.phone)
                        Spacer(Modifier.size(2.dp))
                        IconText(icon = Icons.Outlined.Campaign, text = lead.campaignName)
                    }
                    StatusBadgeWithDot(text = statusLabel, color = statusBadgeColor)
                }
                val followUpText = lead.nextCallbackAt?.let { "Follow-up: ${it.format(dateFmt)}" }
                val lastCalledText = lead.lastContactedAt?.let { "Last called: ${it.format(dateFmt)}" }
                val bottom = followUpText ?: lastCalledText
                if (bottom != null) {
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (followUpText != null) Icons.Outlined.EventAvailable else Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = Ink500,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(bottom, color = Ink500, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun IconText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Ink500, modifier = Modifier.size(12.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, color = Ink500, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun StatusBadgeWithDot(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color))
        Spacer(Modifier.size(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

private val IntrinsicMin = androidx.compose.foundation.layout.IntrinsicSize.Min
