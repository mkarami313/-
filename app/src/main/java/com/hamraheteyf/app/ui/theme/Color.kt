package com.hamraheteyf.app.ui.theme

import androidx.compose.ui.graphics.Color

val Color_White = Color(0xFFFFFFFF)

/**
 * Calm, contrast-safe palette (white text on primary ≥ 6:1). A deep teal leads; a warm coral accent
 * is used for highlights only, never under white text. Alert colors are reserved for genuinely important messages.
 */

// Brand teal
val Teal = Color(0xFF0E6E62)
val TealDark = Color(0xFF0B5D55)
val TealDeep = Color(0xFF084A44)
val TealContainer = Color(0xFFCDEDE6)
val OnTealContainer = Color(0xFF06372F)

// Warm accent (apricot) — used for highlights, the greeting gradient, chips
val Apricot = Color(0xFFE8794A)
val ApricotContainer = Color(0xFFFDE5D9)
val OnApricotContainer = Color(0xFF5C2410)

// Neutrals — a hint of warmth in the surfaces instead of flat grey/white
val Background = Color(0xFFF5F6F8)
val Surface = Color(0xFFFFFFFF)
val SurfaceSoft = Color(0xFFF0F2F5)
val OnSurface = Color(0xFF1A1F24)
val OnSurfaceMuted = Color(0xFF5B6570)
val Outline = Color(0xFFDDE2E7)
val OutlineSoft = Color(0xFFE8EBEF)

val Danger = Color(0xFFBE3A32)
val DangerContainer = Color(0xFFF9E1DF)
val VerifiedGreen = Color(0xFF2E8B63)

// Header gradient (teal → deep teal)
val GradientStart = Color(0xFF0E6E62)
val GradientEnd = Color(0xFF084A44)

// Dark theme (kept for callers that opt in; the app ships light by default)
val DarkBackground = Color(0xFF12181A)
val DarkSurface = Color(0xFF1C2426)
val DarkOnSurface = Color(0xFFE6ECE9)
val DarkOutline = Color(0xFF38423F)

// Expert / clinic accent (blue) — marks verified-professional content
val Expert = Color(0xFF3B5BDB)
val ExpertContainer = Color(0xFFE3E9FF)
val Like = Color(0xFFE03131)
val Gold = Color(0xFFF59F00)

/** Parses a server "#RRGGBB" color; falls back to the brand teal. */
fun hexColor(hex: String?, fallback: Color = Teal): Color =
    runCatching { if (hex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
