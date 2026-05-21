package com.example.callcenter.data.mock

import com.example.callcenter.domain.model.Agent
import com.example.callcenter.domain.model.AgentStats
import com.example.callcenter.domain.model.AgentStatus
import com.example.callcenter.domain.model.AppNotification
import com.example.callcenter.domain.model.Call
import com.example.callcenter.domain.model.CallDirection
import com.example.callcenter.domain.model.CallRouteType
import com.example.callcenter.domain.model.CallStatus
import com.example.callcenter.domain.model.Callback
import com.example.callcenter.domain.model.CallbackStatus
import com.example.callcenter.domain.model.Campaign
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.Lead
import com.example.callcenter.domain.model.LeadPriority
import com.example.callcenter.domain.model.LeadStatus
import com.example.callcenter.domain.model.NotificationKind
import java.time.LocalDateTime

object MockData {

    val agent = Agent(
        id = 1,
        name = "Priya Sharma",
        email = "priya@bol7.com",
        username = "priya",
        extension = "1042",
        sipUsername = "priya_sip",
        status = AgentStatus.AVAILABLE,
        campaignIds = listOf(1, 2, 3),
    )

    val stats = AgentStats(
        totalCalls = 42,
        connectedCalls = 27,
        missedCalls = 5,
        callbacksDue = 8,
        conversions = 4,
        newLeads = 3,
    )

    val campaigns: List<Campaign> = listOf(
        Campaign(1, "Q1 Renewals", "Outbound renewals push for Q1 customers.", 220, 142, 28),
        Campaign(2, "Premium Upsell", "Convert existing premium leads to enterprise.", 180, 96, 21),
        Campaign(3, "Cold Outreach – APAC", "New geo expansion outbound list.", 320, 88, 9),
        Campaign(4, "Trial Conversions", "Trial users approaching expiry.", 140, 110, 33),
        Campaign(5, "Winback Q4", "Re-engage churned customers.", 90, 32, 4, active = false),
    )

    val leads: List<Lead> = buildList {
        val first = listOf(
            "Rahul Verma" to "+91 98103 12345",
            "Aisha Khan" to "+91 99876 51234",
            "Vivek Iyer" to "+91 90230 78145",
            "Sneha Patel" to "+91 97654 22311",
            "Karan Singh" to "+91 99100 41122",
            "Meera Nair" to "+91 80450 99001",
            "Arjun Mehta" to "+91 99887 12345",
            "Pooja Reddy" to "+91 98432 88012",
            "Devansh Roy" to "+91 91100 67890",
            "Ananya Gupta" to "+91 99220 11456",
            "Rohan Joshi" to "+91 97333 22781",
            "Isha Malhotra" to "+91 98345 90011",
            "Yash Bansal" to "+91 99000 33145",
            "Kavya Rao" to "+91 98989 44782",
            "Nikhil Shah" to "+91 99811 67320",
            "Riya Kapoor" to "+91 99550 23119",
            "Aditya Bol" to "+91 98765 43210",
            "Tanvi Desai" to "+91 91230 55433",
            "Manish Tiwari" to "+91 98111 22099",
            "Shruti Saxena" to "+91 99700 81234",
            "Harsh Bhalla" to "+91 99221 56781",
            "Neha Chawla" to "+91 99800 11234",
            "Saurabh Yadav" to "+91 97890 11023",
            "Lakshmi Menon" to "+91 99889 11122",
            "Ravi Tripathi" to "+91 90001 23456",
        )
        first.forEachIndexed { idx, (name, phone) ->
            val campaign = campaigns[idx % campaigns.size]
            val status = LeadStatus.entries[idx % LeadStatus.entries.size]
            val priority = LeadPriority.entries[idx % LeadPriority.entries.size]
            add(
                Lead(
                    id = idx + 1,
                    name = name,
                    phone = phone,
                    email = name.split(" ").first().lowercase() + "@example.com",
                    campaignId = campaign.id,
                    campaignName = campaign.name,
                    status = status,
                    priority = priority,
                    nextCallbackAt = if (status == LeadStatus.FOLLOW_UP)
                        LocalDateTime.now().plusHours((idx + 1).toLong()) else null,
                    lastContactedAt = if (status != LeadStatus.NEW)
                        LocalDateTime.now().minusDays((idx % 7 + 1).toLong()) else null,
                    notes = if (idx % 3 == 0) "Asked for callback in the afternoon." else null,
                    address = "Block ${idx + 1}, Sector ${idx + 10}, Bangalore",
                    tags = if (idx % 4 == 0) listOf("priority") else emptyList(),
                )
            )
        }
    }

    val callbacks: List<Callback> = listOf(
        Callback(1, leads[0].id, leads[0].name, leads[0].phone, leads[0].campaignName,
            LocalDateTime.now().plusMinutes(45), CallbackStatus.PENDING, "Customer requested afternoon."),
        Callback(2, leads[2].id, leads[2].name, leads[2].phone, leads[2].campaignName,
            LocalDateTime.now().plusHours(3), CallbackStatus.PENDING),
        Callback(3, leads[4].id, leads[4].name, leads[4].phone, leads[4].campaignName,
            LocalDateTime.now().plusHours(6), CallbackStatus.PENDING, "Discuss pricing tier."),
        Callback(4, leads[6].id, leads[6].name, leads[6].phone, leads[6].campaignName,
            LocalDateTime.now().plusDays(1).withHour(10).withMinute(30), CallbackStatus.PENDING),
        Callback(5, leads[8].id, leads[8].name, leads[8].phone, leads[8].campaignName,
            LocalDateTime.now().plusDays(1).withHour(15), CallbackStatus.PENDING),
        Callback(6, leads[10].id, leads[10].name, leads[10].phone, leads[10].campaignName,
            LocalDateTime.now().plusDays(3), CallbackStatus.PENDING),
        Callback(7, leads[12].id, leads[12].name, leads[12].phone, leads[12].campaignName,
            LocalDateTime.now().plusDays(5), CallbackStatus.PENDING),
        Callback(8, leads[14].id, leads[14].name, leads[14].phone, leads[14].campaignName,
            LocalDateTime.now().minusHours(1), CallbackStatus.OVERDUE, "Missed call earlier."),
    )

    val callHistory: List<Call> = buildList {
        leads.forEachIndexed { idx, lead ->
            val status = when (idx % 6) {
                0 -> CallStatus.COMPLETED
                1 -> CallStatus.COMPLETED
                2 -> CallStatus.NO_ANSWER
                3 -> CallStatus.BUSY
                4 -> CallStatus.FAILED
                else -> CallStatus.COMPLETED
            }
            val dispo = if (status == CallStatus.COMPLETED)
                Disposition.entries[idx % Disposition.entries.size] else null
            add(
                Call(
                    id = "c-${idx + 1}",
                    leadId = lead.id,
                    leadName = lead.name,
                    leadPhone = lead.phone,
                    campaignName = lead.campaignName,
                    direction = if (idx % 5 == 0) CallDirection.INCOMING else CallDirection.OUTGOING,
                    status = status,
                    routeType = CallRouteType.entries[idx % CallRouteType.entries.size],
                    startedAt = LocalDateTime.now().minusHours((idx + 1).toLong()),
                    endedAt = LocalDateTime.now().minusHours((idx + 1).toLong()).plusMinutes((idx % 7).toLong()),
                    durationSec = if (status == CallStatus.COMPLETED) (idx + 1) * 47 % 540 else 0,
                    disposition = dispo,
                    recordingUrl = if (idx % 3 == 0 && status == CallStatus.COMPLETED) "https://example.com/rec/$idx.mp3" else null,
                )
            )
        }
    }

    val notifications: List<AppNotification> = listOf(
        AppNotification(1, NotificationKind.CALLBACK, "Callback due in 45 minutes",
            "${leads[0].name} • ${leads[0].phone}", LocalDateTime.now().minusMinutes(10), false, leads[0].id, 1),
        AppNotification(2, NotificationKind.LEAD, "3 new leads assigned",
            "Q1 Renewals campaign", LocalDateTime.now().minusHours(1), false),
        AppNotification(3, NotificationKind.CAMPAIGN, "Campaign goal reached",
            "Premium Upsell crossed 50% conversions.", LocalDateTime.now().minusHours(4), true),
        AppNotification(4, NotificationKind.SYSTEM, "Welcome to CallCenter",
            "Tour your dashboard to get started.", LocalDateTime.now().minusDays(1), true),
        AppNotification(5, NotificationKind.CALLBACK, "Callback overdue",
            "${leads[14].name} • scheduled 1h ago", LocalDateTime.now().minusHours(1), true, leads[14].id, 8),
        AppNotification(6, NotificationKind.LEAD, "Lead converted",
            "${leads[3].name} marked as converted.", LocalDateTime.now().minusDays(2), true),
    )
}
