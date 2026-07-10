package com.kickpredict.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app draws with, in one value. Screens read these through the accessors in
 * `Color.kt` rather than naming a hue, so swapping [AppTheme] repaints the whole app.
 *
 * [win]/[draw]/[loss] are semantic and deliberately separate from [accent]: a theme is free to make
 * its accent the same colour as a win (Floodlight) or a different one entirely (Card, where the
 * accent is chalk white so yellow and red carry only their card meanings).
 */
data class AppPalette(
    val ground: Color,
    val surfaceSunken: Color,
    val surface1: Color,
    val surface2: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentDim: Color,
    /** Text/icons drawn on top of [accent]; must contrast with it, not with the ground. */
    val onAccent: Color,
    val win: Color,
    val draw: Color,
    val loss: Color,
    /** Drives which Material colour scheme is built, and the status-bar icon colour. */
    val isLight: Boolean = false,
)

/**
 * The palettes the user can choose between. Each is grounded in something from the sport rather
 * than being a hue rotation of the others.
 */
enum class AppTheme(
    val displayName: String,
    val description: String,
    val palette: AppPalette,
) {
    /** Cool blue-white of stadium lights at night. Cyan/amber/magenta avoids the red-green axis. */
    FLOODLIGHT(
        displayName = "Floodlight",
        description = "야간 조명 · 색각이상 안전",
        palette = AppPalette(
            ground = Color(0xFF080B12),
            surfaceSunken = Color(0xFF0E1320),
            surface1 = Color(0xFF141B2B),
            surface2 = Color(0xFF1D2739),
            outline = Color(0xFF33405A),
            textPrimary = Color(0xFFEAF2FA),
            textSecondary = Color(0xFF94A4BC),
            accent = Color(0xFF4CC9F0),
            accentDim = Color(0xFF2E9BC0),
            onAccent = Color(0xFF080B12),
            win = Color(0xFF4CC9F0),
            draw = Color(0xFFF6BD3B),
            loss = Color(0xFFFF5D8F),
        ),
    ),

    /** Pitch green and chalk lines; the draw and loss colours are the referee's two cards. */
    CARD(
        displayName = "Card",
        description = "잔디와 심판 카드",
        palette = AppPalette(
            ground = Color(0xFF08120C),
            surfaceSunken = Color(0xFF0D1A12),
            surface1 = Color(0xFF132419),
            surface2 = Color(0xFF1B3123),
            outline = Color(0xFF31503C),
            textPrimary = Color(0xFFF1F5EC),
            textSecondary = Color(0xFF93A896),
            accent = Color(0xFFF1F5EC),
            accentDim = Color(0xFF59C97E),
            onAccent = Color(0xFF08120C),
            win = Color(0xFFF1F5EC),
            draw = Color(0xFFFFC93C),
            loss = Color(0xFFE63946),
        ),
    ),

    /** Newsprint: paper, ink, and a single headline red. The one light palette. */
    BROADSHEET(
        displayName = "Broadsheet",
        description = "스포츠 지면 · 라이트",
        palette = AppPalette(
            ground = Color(0xFFF7F6F2),
            surfaceSunken = Color(0xFFEFEEE8),
            surface1 = Color(0xFFFFFFFF),
            surface2 = Color(0xFFF1F0EA),
            outline = Color(0xFFD3D2CA),
            textPrimary = Color(0xFF16181A),
            textSecondary = Color(0xFF5F6569),
            accent = Color(0xFF1D3557),
            accentDim = Color(0xFF2E5484),
            onAccent = Color(0xFFFFFFFF),
            win = Color(0xFF1D3557),
            draw = Color(0xFFB58900),
            loss = Color(0xFFC1272D),
            isLight = true,
        ),
    ),

    /** Warm dark: copper accent, teal draw, crimson loss. */
    EMBER(
        displayName = "Ember",
        description = "따뜻한 어둠 · 구릿빛",
        palette = AppPalette(
            ground = Color(0xFF0D0A08),
            surfaceSunken = Color(0xFF16110D),
            surface1 = Color(0xFF1F1813),
            surface2 = Color(0xFF2B211A),
            outline = Color(0xFF4A3B2E),
            textPrimary = Color(0xFFF6F0E8),
            textSecondary = Color(0xFFAC9C8B),
            accent = Color(0xFFFF8A3D),
            accentDim = Color(0xFFC96A2C),
            onAccent = Color(0xFF0D0A08),
            win = Color(0xFFFF8A3D),
            draw = Color(0xFF3FC9B0),
            loss = Color(0xFFF0426B),
        ),
    ),
    ;

    companion object {
        val Default = FLOODLIGHT

        fun fromName(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * Static because the palette only changes when the user picks a new theme, which recomposes the
 * whole tree anyway — reads of this shouldn't cost a subscription at every call site.
 */
val LocalPalette = staticCompositionLocalOf { AppTheme.Default.palette }
