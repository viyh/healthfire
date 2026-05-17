package io.github.viyh.healthfire.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/*
 * HealthFire uses one dark theme: warm logo accents on near-black, no blue or
 * purple. Every colour role is set so nothing falls back to a Material default.
 */
private val HealthfireColors = darkColorScheme(
    primary = Orange,
    onPrimary = OnAccent,
    primaryContainer = DeepOrange,
    onPrimaryContainer = SoftOrange,
    secondary = Amber,
    onSecondary = OnAccent,
    secondaryContainer = DeepAmber,
    onSecondaryContainer = SoftAmber,
    tertiary = Amber,
    onTertiary = OnAccent,
    tertiaryContainer = DeepAmber,
    onTertiaryContainer = SoftAmber,
    error = ErrorRed,
    onError = OnAccent,
    errorContainer = DeepRed,
    onErrorContainer = SoftRed,
    background = Black,
    onBackground = OffWhite,
    surface = Surface,
    onSurface = OffWhite,
    surfaceVariant = SurfaceHighest,
    onSurfaceVariant = MutedGrey,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Surface,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    outline = Amber,
    outlineVariant = OutlineDim,
)

@Composable
fun HealthfireTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HealthfireColors,
        typography = Typography,
        content = content,
    )
}
