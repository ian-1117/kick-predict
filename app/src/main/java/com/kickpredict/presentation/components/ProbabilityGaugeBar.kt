package com.kickpredict.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.OutlineColor
import com.kickpredict.presentation.theme.WinColor

/**
 * The win / draw / loss probability gauges. Each row is a full-width track with a fractional
 * fill, so the layout is entirely proportional and never overflows on the narrow cover screen.
 */
@Composable
fun ProbabilityGauges(
    result: PredictionResult,
    homeName: String,
    awayName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GaugeRow(homeName, result.homeWinPercent, WinColor)
        GaugeRow("Draw", result.drawPercent, DrawColor)
        GaugeRow(awayName, result.awayWinPercent, LossColor)
    }
}

@Composable
private fun GaugeRow(label: String, percent: Int, color: Color) {
    val fraction by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "gauge_$label",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = color,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(OutlineColor.copy(alpha = 0.45f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}
