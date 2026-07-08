package com.kickpredict.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = LimeGreen,
    onPrimary = PitchBlack,
    primaryContainer = LimeGreenDim,
    onPrimaryContainer = PitchBlack,
    secondary = DrawColor,
    onSecondary = PitchBlack,
    background = PitchBlack,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = OutlineGrey,
    error = LossColor,
)

// The app is designed dark-first; a light scheme is provided so it degrades gracefully if the
// system forces light mode.
private val LightColors = lightColorScheme(
    primary = LimeGreenDim,
    onPrimary = PitchBlack,
    background = TextPrimary,
    onBackground = PitchBlack,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = PitchBlack,
)

@Composable
fun KickPredictTheme(
    darkTheme: Boolean = true, // dark-first per brand; ignores system by default
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme || isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
