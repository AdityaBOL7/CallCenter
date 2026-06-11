package com.example.callcenter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One disposition code from /api/dialer/dispositions/. Tenant rows override
 * global ones. Used to populate the picker at call hangup. Tolerant: shape is
 * undocumented, so all fields are nullable and unknown keys are ignored.
 */
@Serializable
data class DispositionDto(
    val id: Int? = null,
    val code: String? = null,
    val label: String? = null,
    val name: String? = null,
    val color: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
) {
    val resolvedLabel: String?
        get() = label?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: code?.takeIf { it.isNotBlank() }
}
