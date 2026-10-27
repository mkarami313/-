package com.hamraheteyf.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Typography with comfortable sizes and line heights for readability.
 *
 * Uses the system default font family, which renders Persian correctly. To ship a branded
 * Persian font (e.g. Vazirmatn), drop the .ttf files into res/font and set [AppFontFamily].
 * See docs/STATUS.md.
 */
val AppFontFamily: FontFamily = FontFamily.Default

val AppTypography = Typography(
    titleLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 18.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, lineHeight = 18.sp),
)
