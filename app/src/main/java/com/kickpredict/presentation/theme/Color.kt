package com.kickpredict.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The app's colours, read from whichever [AppTheme] is active rather than baked in at compile time.
 *
 * These are composable getters, so they only resolve inside a composition. Code that needs a colour
 * outside one — a `Canvas` draw lambda, a plain helper function — must read it in the composable that
 * owns it and pass the [Color] down.
 */

// ── Ground and surfaces ────────────────────────────────────────────────────────
val Ground: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ground
val SurfaceSunken: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceSunken
val Surface1: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface1
val Surface2: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface2
val OutlineColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.outline

// ── Text ───────────────────────────────────────────────────────────────────────
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary

// ── Accent ─────────────────────────────────────────────────────────────────────
val AccentPrimary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val AccentDim: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentDim
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent

// ── Semantic outcome colours (charts, gauges, probability bars) ────────────────
val WinColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.win
val DrawColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.draw
val LossColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.loss
