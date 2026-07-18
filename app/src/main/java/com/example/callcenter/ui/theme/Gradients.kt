package com.example.callcenter.ui.theme

import androidx.compose.ui.graphics.Brush

object AppGradients {
    fun brandHeader(): Brush = Brush.linearGradient(listOf(Brand500, Brand600, Brand700))
    /** Indigo gradient for primary CTAs (buttons, promo banners). */
    fun primaryCta(pressed: Boolean = false): Brush = Brush.linearGradient(
        if (pressed) listOf(Brand600, Brand800) else listOf(BrandCtaStart, BrandOnSoft)
    )
    fun loginLogo(): Brush = Brush.linearGradient(listOf(Brand400, Brand600))
    fun loginButton(pressed: Boolean): Brush = Brush.linearGradient(
        if (pressed) listOf(Brand700, Brand800) else listOf(Brand500, Brand700)
    )
    fun callBackground(): Brush = Brush.linearGradient(listOf(Ink900, Ink800, Brand700))
    fun slideTrack(): Brush = Brush.linearGradient(listOf(Emerald100, Emerald200))
    fun slideFill(): Brush = Brush.horizontalGradient(listOf(Emerald500, Emerald600))
}
