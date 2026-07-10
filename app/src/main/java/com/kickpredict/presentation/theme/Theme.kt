package com.kickpredict.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Builds the Material scheme from an [AppPalette] so Material components (chips, sliders, buttons)
 * and the app's own drawing share one source of colour. The palette also travels down through
 * [LocalPalette] for the places that want a specific token rather than a Material role.
 *
 * The chosen theme decides light vs dark — the app no longer follows the system setting, since
 * picking Broadsheet *is* picking light.
 */
private fun AppPalette.toColorScheme() = if (isLight) {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentDim,
        onPrimaryContainer = onAccent,
        secondary = draw,
        onSecondary = onAccent,
        background = ground,
        onBackground = textPrimary,
        surface = surface1,
        onSurface = textPrimary,
        surfaceVariant = surface2,
        onSurfaceVariant = textSecondary,
        outline = outline,
        error = loss,
    )
} else {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentDim,
        onPrimaryContainer = onAccent,
        secondary = draw,
        onSecondary = onAccent,
        background = ground,
        onBackground = textPrimary,
        surface = surface1,
        onSurface = textPrimary,
        surfaceVariant = surface2,
        onSurfaceVariant = textSecondary,
        outline = outline,
        error = loss,
    )
}

@Composable
fun KickPredictTheme(
    theme: AppTheme = AppTheme.Default,
    content: @Composable () -> Unit,
) {
    val palette = theme.palette
    val colors = remember(theme) { palette.toColorScheme() }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content,
        )
    }
}
