package com.hamraheteyf.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
 * Applies the app theme and forces right-to-left layout for the Persian UI.
 *
 * The app uses a fixed LIGHT theme by default (does NOT follow the system dark mode). This
 * is intentional: the calm, high-contrast light palette is the designed experience, and it
 * avoids low-contrast text when the device is in dark mode. A caller can still opt into the
 * dark palette by passing darkTheme = true. Content is wrapped in a themed [Surface] so the
 * background always matches the color scheme and text stays readable.
 */
@Composable
fun HamrahTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    }
}
