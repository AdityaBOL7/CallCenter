package com.example.callcenter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.callcenter.R

/**
 * Plus Jakarta Sans, bundled as static weights (minSdk 24 rules out the
 * variable font). Weight requests resolve to the nearest bundled file.
 */
val PlusJakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private val Sans = PlusJakarta

val AppTypography = Typography(
    // Displays: big numeric stats.
    displayLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp,
        lineHeight = 40.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
        lineHeight = 36.sp, letterSpacing = (-0.4).sp
    ),
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp,
        lineHeight = 32.sp, letterSpacing = (-0.3).sp
    ),
    // Screen titles.
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp,
        lineHeight = 30.sp, letterSpacing = (-0.48).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
        lineHeight = 26.sp, letterSpacing = (-0.3).sp
    ),
    // Card names / row labels.
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Body.
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    // Labels; labelSmall is the uppercase micro-label (pair with AppColor.micro).
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        lineHeight = 14.sp, letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
        lineHeight = 14.sp, letterSpacing = 1.05.sp
    ),
)
