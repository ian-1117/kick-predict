package com.kickpredict.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kickpredict.domain.model.ConfidenceTier
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.LossColor

/**
 * The headline "AI 예상 적중 확률" badge. Colour tracks the [ConfidenceTier] so a glance
 * conveys how much to trust the prediction.
 */
@Composable
fun ConfidenceBadge(
    confidenceScore: Int,
    tier: ConfidenceTier,
    modifier: Modifier = Modifier,
) {
    val accent = tier.accent()
    Row(
        modifier = modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(50))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = accent,
        )
        Text(
            text = "AI 예상 적중 확률",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$confidenceScore%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = accent,
        )
        Text(
            text = "· ${tier.label}",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun ConfidenceTier.accent(): Color = when (this) {
    ConfidenceTier.VERY_HIGH, ConfidenceTier.HIGH -> AccentPrimary
    ConfidenceTier.MODERATE -> DrawColor
    ConfidenceTier.LOW, ConfidenceTier.VERY_LOW -> LossColor
}
