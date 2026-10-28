package com.hamraheteyf.app.ui.theme

import androidx.compose.ui.graphics.Color

val Color_White = Color(0xFFFFFFFF)

/**
 * Calm, warm palette for a low-stress support app. A soft teal leads; a warm apricot accent
 * adds life without shouting. Alert colors are reserved for genuinely important messages.
 */

// Brand teal
val Teal = Color(0xFF2F8F7F)
val TealDark = Color(0xFF1F6B5E)
val TealDeep = Color(0xFF17524A)
val TealContainer = Color(0xFFD5EEE7)
val OnTealContainer = Color(0xFF123D36)

// Warm accent (apricot) — used for highlights, the greeting gradient, chips
val Apricot = Color(0xFFF6A94A)
val ApricotContainer = Color(0xFFFCE7CB)
val OnApricotContainer = Color(0xFF5A3B12)

// Neutrals — a hint of warmth in the surfaces instead of flat grey/white
val Background = Color(0xFFF4F7F5)
val Surface = Color(0xFFFFFFFF)
val SurfaceSoft = Color(0xFFEEF3F1)
val OnSurface = Color(0xFF192421)
val OnSurfaceMuted = Color(0xFF657470)
val Outline = Color(0xFFDDE6E2)
val OutlineSoft = Color(0xFFE9F0ED)

val Danger = Color(0xFFBE3A32)
val DangerContainer = Color(0xFFF9E1DF)
val VerifiedGreen = Color(0xFF2E8B63)

// Header gradient (teal → deep teal)
val GradientStart = Color(0xFF34A08D)
val GradientEnd = Color(0xFF1F6B5E)

// Dark theme (kept for callers that opt in; the app ships light by default)
val DarkBackground = Color(0xFF12181A)
val DarkSurface = Color(0xFF1C2426)
val DarkOnSurface = Color(0xFFE6ECE9)
val DarkOutline = Color(0xFF38423F)
