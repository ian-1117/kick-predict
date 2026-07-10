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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Lets the user pick a palette. Each row is painted in the theme it offers — a colour choice should
 * be shown, not described.
 */
@Composable
fun ThemePickerDialog(
    current: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = AccentPrimary) }
        },
        title = { Text("테마", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.entries.forEach { theme ->
                    ThemeRow(
                        theme = theme,
                        selected = theme == current,
                        onClick = { onSelect(theme) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
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
