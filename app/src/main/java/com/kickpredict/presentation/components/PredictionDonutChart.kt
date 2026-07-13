package com.kickpredict.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.OutlineColor
import com.kickpredict.presentation.theme.WinColor

/**
 * Donut chart of the home/draw/away probabilities. Ratio-based (uses [aspectRatio] 1:1 inside a
 * [fillMaxWidth] parent) so it scales cleanly from the Fold 3 cover screen to the unfolded panel.
 * The centre shows the predicted outcome and its probability.
 */
@Composable
fun PredictionDonutChart(
    result: PredictionResult,
    modifier: Modifier = Modifier,
) {
    val home = result.homeWinPercent.toFloat()
    val draw = result.drawPercent.toFloat()
    val away = result.awayWinPercent.toFloat()
    val total = (home + draw + away).coerceAtLeast(1f)

    val sweepProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "donutSweep",
    )

    val topLabel = result.predictedOutcome.label
    val topValue = maxOf(home, draw, away).toInt()

    val description = androidx.compose.ui.res.stringResource(
        com.kickpredict.R.string.a11y_prediction,
        result.homeWinPercent,
        result.drawPercent,
        result.awayWinPercent,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // A Canvas draw lambda is not a composition, so the palette is read out here and captured.
        val winColor = WinColor
        val drawColor = DrawColor
        val lossColor = LossColor
        val trackColor = OutlineColor.copy(alpha = 0.45f)

        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val stroke = size.minDimension * 0.16f
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.minDimension - stroke,
                size.minDimension - stroke,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )

            var start = -90f
            val gap = 4f
            listOf(home to winColor, draw to drawColor, away to lossColor).forEach { (value, color) ->
                val sweep = (value / total) * 360f * sweepProgress
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = start + gap / 2,
                        sweepAngle = (sweep - gap).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$topValue%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = topLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
