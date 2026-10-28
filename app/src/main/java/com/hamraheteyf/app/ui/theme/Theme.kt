package com.hamraheteyf.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color_White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = Apricot,
    onSecondary = Color_White,
    secondaryContainer = ApricotContainer,
    onSecondaryContainer = OnApricotContainer,
    tertiary = TealDark,
    tertiaryContainer = TealContainer,
    onTertiaryContainer = OnTealContainer,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = OnSurfaceMuted,
    surfaceContainerHighest = SurfaceSoft,
    outline = Outline,
    outlineVariant = OutlineSoft,
    error = Danger,
    errorContainer = DangerContainer,
    onError = Color_White,
)

private val DarkColors = darkColorScheme(
    primary = TealContainer,
    onPrimary = TealDeep,
    primaryContainer = TealDark,
    onPrimaryContainer = TealContainer,
    secondary = Apricot,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    outline = DarkOutline,
    error = Danger,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Applies the app theme and forces right-to-left layout for the Persian UI.
 *
 * Ships a fixed LIGHT theme (does not follow system dark mode) — the calm, warm light
 * palette is the designed experience. A caller may still pass darkTheme = true.
 */
@Composable
fun HamrahTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
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
