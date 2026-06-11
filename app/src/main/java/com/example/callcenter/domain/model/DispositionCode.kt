package com.example.callcenter.domain.model

/**
 * A disposition option shown at call hangup. Comes from the backend
 * (/api/dialer/dispositions/) and is tenant-configurable.
 *
 * [mapped] links the dynamic code back to the built-in [Disposition] enum so the
 * existing hangup / lead-status logic keeps working; it's a best-effort match.
 */
data class DispositionCode(
    val code: String,
    val label: String,
    val mapped: Disposition,
)
