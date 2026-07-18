package com.example.callcenter.domain.model

import java.time.LocalDateTime

enum class LeadStatus(val label: String) {
    NEW("New"),
    // Dialed at least once but NOBODY ever picked up (confirmed by the device
    // call log). Distinct from NEW ("never tried") and CONTACTED ("we talked") —
    // this is the retry bucket. Backend accepts "no_answer" on leads (verified
    // live 2026-07-16). Agreed with Aditya: NEW must keep meaning "never dialed".
    NO_ANSWER("No Answer"),
    CONTACTED("Contacted"),
    INTERESTED("Interested"),
    NOT_INTERESTED("Not Interested"),
    FOLLOW_UP("Follow-up"),
    CONVERTED("Converted"),
    CLOSED("Closed"),
}

enum class LeadPriority(val label: String, val weight: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    URGENT("Urgent", 4),
}

data class Lead(
    val id: Int,
    val name: String,
    val phone: String,
    val email: String? = null,
    val campaignId: Int,
    val campaignName: String,
    val status: LeadStatus = LeadStatus.NEW,
    val priority: LeadPriority = LeadPriority.MEDIUM,
    val nextCallbackAt: LocalDateTime? = null,
    val lastContactedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val notes: String? = null,
    val address: String? = null,
    val tags: List<String> = emptyList(),
)

data class LeadFilters(
    val status: LeadStatus? = null,
    val campaignId: Int? = null,
    val search: String = "",
    val sort: LeadSort = LeadSort.PRIORITY,
)

enum class LeadSort(val label: String) {
    PRIORITY("Priority"),
    RECENT("Latest first"),
    FOLLOW_UP("Follow-up time"),
}
