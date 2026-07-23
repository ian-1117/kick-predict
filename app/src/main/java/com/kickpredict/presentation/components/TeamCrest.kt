package com.kickpredict.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A generated, logo-free club crest: a team-colour disc with the club's short code. Avoids any
 * copyrighted logo asset while still giving every team a distinct, recognisable mark.
 */
@Composable
fun TeamCrest(
    shortName: String,
    primary: Long,
    secondary: Long,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val background = Color(primary)
    val foreground = Color(secondary)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, foreground.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shortName.take(3),
            color = foreground,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.32f).sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
