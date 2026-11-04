package com.hamraheteyf.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.hamraheteyf.app.R

/**
 * Vazirmatn — a modern Persian typeface — is the app font. It renders Persian far more
 * cleanly than the system default and gives the UI a warmer, more finished feel.
 */
val AppFontFamily: FontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

// Persian fonts sit low with the default font padding; turn it off and center each line
// within its line height so text is vertically balanced (fixes titles/labels dropping down).
private val BalancedLine = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun fa(
    weight: FontWeight = FontWeight.Normal,
    size: Int,
    line: Int,
) = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = BalancedLine,
)

val AppTypography = Typography(
    displaySmall = fa(FontWeight.Bold, 28, 40),
    headlineMedium = fa(FontWeight.Bold, 24, 34),
    headlineSmall = fa(FontWeight.Bold, 22, 32),
    titleLarge = fa(FontWeight.Bold, 19, 28),
    titleMedium = fa(FontWeight.SemiBold, 16, 25),
    titleSmall = fa(FontWeight.SemiBold, 14, 22),
    bodyLarge = fa(size = 15, line = 26),
    bodyMedium = fa(size = 14, line = 23),
    bodySmall = fa(size = 12, line = 19),
    labelLarge = fa(FontWeight.SemiBold, 14, 20),
    labelMedium = fa(FontWeight.Medium, 12, 17),
    labelSmall = fa(FontWeight.Medium, 11, 15),
)
