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
    headlineSmall = fa(FontWeight.Bold, 24, 34),
    titleLarge = fa(FontWeight.Bold, 21, 32),
    titleMedium = fa(FontWeight.SemiBold, 17, 26),
    titleSmall = fa(FontWeight.SemiBold, 15, 22),
    bodyLarge = fa(size = 16, line = 28),
    bodyMedium = fa(size = 15, line = 25),
    bodySmall = fa(size = 13, line = 20),
    labelLarge = fa(FontWeight.SemiBold, 15, 20),
    labelMedium = fa(FontWeight.Medium, 13, 18),
    labelSmall = fa(FontWeight.Medium, 11, 16),
)
