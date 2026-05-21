package com.example.callcenter.domain.model

data class Campaign(
    val id: Int,
    val name: String,
    val description: String,
    val totalLeads: Int,
    val contactedLeads: Int,
    val conversions: Int,
    val active: Boolean = true,
) {
    val progress: Float get() = if (totalLeads == 0) 0f else contactedLeads.toFloat() / totalLeads
}
