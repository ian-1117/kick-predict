package com.kickpredict.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kickpredict.R
import com.kickpredict.presentation.locale.AppLanguage

/**
 * Lets the user pick a palette (shown, not described — each row is painted in the theme it offers)
 * and the app language.
 */
@Composable
fun ThemePickerDialog(
    current: AppTheme,
    onSelect: (AppTheme) -> Unit,
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = AccentPrimary) }
        },
        title = { Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.entries.forEach { theme ->
                    ThemeRow(
                        theme = theme,
                        selected = theme == current,
                        onClick = { onSelect(theme) },
                    )
                }

                Text(
                    stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        LanguageChip(
                            label = stringResource(language.labelRes),
                            selected = language == currentLanguage,
                            onClick = { onSelectLanguage(language) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.KOREAN -> R.string.language_korean
        AppLanguage.ENGLISH -> R.string.language_english
    }

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border = if (selected) AccentPrimary else Color.Transparent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.5.dp, border, RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AccentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ThemeRow(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val palette = theme.palette
    val borderColor = if (selected) palette.accent else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.ground)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                theme.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                theme.description,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
        }

        // The three colours that carry meaning everywhere in the app: win, draw, loss.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Swatch(palette.win)
            Swatch(palette.draw)
            Swatch(palette.loss)
        }

        if (selected) {
            Text(
                "✓",
                color = palette.accent,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(16.dp).clip(CircleShape).background(color))
}
