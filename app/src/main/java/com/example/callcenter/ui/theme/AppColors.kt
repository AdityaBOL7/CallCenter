package com.example.callcenter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware semantic colors. The static palette in Color.kt stays fixed; this
 * layer maps the *semantic* roles (page background, surfaces, text inks) to
 * light/dark values so screens flip correctly when the theme changes.
 *
 * Screens reference these via the [AppColor] accessor (e.g. `AppColor.bg`,
 * `AppColor.ink900`) instead of the fixed Ink and AppBg constants.
 */
data class AppColors(
    val bg: Color,           // page background (was AppBg)
    val surface: Color,      // cards / sheets (was Color.White / SurfaceLight)
    val surfaceAlt: Color,   // subtle filled inputs / chips (was Ink50 / Ink100)
    val ink900: Color,       // primary text
    val ink700: Color,       // strong secondary text
    val ink500: Color,       // secondary text / icons
    val ink400: Color,       // hint text
    val ink200: Color,       // dividers / faint borders
    val ink100: Color,       // very faint fills / tracks
    val ink50: Color,        // lightest fill
    val micro: Color,        // uppercase micro-labels (section eyebrows)
    val successText: Color,  // success emphasis text on soft green chips
    val dangerText: Color,   // danger emphasis text (overdue timestamps etc.)
)

private val LightAppColors = AppColors(
    bg = AppBg,
    surface = SurfaceLight,
    surfaceAlt = Ink50,
    ink900 = Ink900,
    ink700 = Ink700,
    ink500 = Ink500,
    ink400 = Ink400,
    ink200 = Ink200,
    ink100 = Ink100,
    ink50 = Ink50,
    micro = InkMicro,
    successText = SuccessDeep,
    dangerText = DangerText,
)

private val DarkAppColors = AppColors(
    bg = Ink900,                       // deep navy page
    surface = Ink800,                  // raised cards
    surfaceAlt = Ink700,               // filled inputs
    ink900 = Color(0xFFF1F5F9),        // near-white primary text
    ink700 = Color(0xFFE2E8F0),
    ink500 = Color(0xFF94A3B8),        // muted secondary
    ink400 = Color(0xFF64748B),
    ink200 = Color(0xFF334155),        // dividers
    // Recessed fills must sit a visible step BELOW the Ink800 card surface —
    // when these equaled it (0xFF1E293B), key boxes / pills / rails vanished.
    ink100 = Color(0xFF151F2E),
    ink50 = Color(0xFF1A2534),
    micro = Color(0xFF7E8BA0),
    successText = Success,             // brighter green: SuccessDeep fails contrast on dark chips
    dangerText = Color(0xFFF87171),    // brighter red: DangerText fails contrast on dark cards
)

fun appColorsFor(darkTheme: Boolean): AppColors =
    if (darkTheme) DarkAppColors else LightAppColors

val LocalAppColors = compositionLocalOf { LightAppColors }

/** Convenience accessor: `AppColor.bg`, `AppColor.surface`, `AppColor.ink900`, … */
object AppColor {
    val bg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bg
    val surface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
    val surfaceAlt: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceAlt
    val ink900: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink900
    val ink700: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink700
    val ink500: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink500
    val ink400: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink400
    val ink200: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink200
    val ink100: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink100
    val ink50: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink50
    val micro: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.micro
    val successText: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.successText
    val dangerText: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.dangerText
}
