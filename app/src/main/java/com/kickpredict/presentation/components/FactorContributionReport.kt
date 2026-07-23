package com.kickpredict.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import com.kickpredict.domain.model.FactorContribution
import com.kickpredict.presentation.common.factorLabel
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.OutlineColor
import com.kickpredict.presentation.theme.WinColor
import kotlin.math.abs

/**
 * "What moved this prediction": each factor's tilt on the goal ratio, strongest first. A blue bar
 * leans the pick toward the home side, pink toward the away side; the length is the relative strength.
 */
@Composable
fun FactorContributionReport(
    contributions: List<FactorContribution>,
    homeName: String,
    awayName: String,
    modifier: Modifier = Modifier,
) {
    if (contributions.isEmpty()) return
    val ranked = contributions.sortedByDescending { abs(it.tilt) }
    val maxAbs = ranked.maxOf { abs(it.tilt) }.coerceAtLeast(0.001)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.factor_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        ranked.forEach { c ->
            val towardHome = c.tilt >= 0
            val color = if (towardHome) WinColor else LossColor
            val side = if (towardHome) homeName else awayName
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(factorLabel(c.kind), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("→ $side", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(OutlineColor.copy(alpha = 0.45f))) {
                    Box(Modifier.fillMaxWidth((abs(c.tilt) / maxAbs).toFloat().coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(color))
                }
            }
        }
    }
}
