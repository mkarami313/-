package com.hamraheteyf.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Surface,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealDark,
    secondary = Sand,
    secondaryContainer = SandContainer,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = TealContainer,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Outline,
    error = Danger,
    errorContainer = DangerContainer,
)

private val DarkColors = darkColorScheme(
    primary = TealContainer,
    onPrimary = TealDark,
    primaryContainer = TealDark,
    onPrimaryContainer = TealContainer,
    secondary = Sand,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    outline = DarkOutline,
    error = Danger,
)

/**
 * Applies the app theme and forces right-to-left layout for the Persian UI, regardless of
 * the device's system locale/direction.
 */
@Composable
fun HamrahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
