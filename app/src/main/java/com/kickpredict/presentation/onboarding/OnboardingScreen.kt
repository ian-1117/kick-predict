package com.kickpredict.presentation.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kickpredict.KickPredictApplication
import com.kickpredict.R
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.OnAccent

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as KickPredictApplication).container
    var notificationsOn by remember { mutableStateOf(container.notificationPreference.enabled.value) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { container.setNotificationsEnabled(true); notificationsOn = true }
    }
    fun enableNotifications() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else { container.setNotificationsEnabled(true); notificationsOn = true }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row {
            Text("KICK", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
            Text("PREDICT", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = AccentPrimary)
        }
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        Feature(Icons.Filled.Insights, R.string.onboarding_feat1_title, R.string.onboarding_feat1_body)
        Spacer(Modifier.size(18.dp))
        Feature(Icons.AutoMirrored.Filled.TrendingUp, R.string.onboarding_feat2_title, R.string.onboarding_feat2_body)
        Spacer(Modifier.size(18.dp))
        Feature(Icons.Filled.Science, R.string.onboarding_feat3_title, R.string.onboarding_feat3_body)

        Spacer(Modifier.size(36.dp))

        OutlinedButton(
            onClick = { if (!notificationsOn) enableNotifications() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !notificationsOn,
        ) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(if (notificationsOn) R.string.onboarding_notifs_on else R.string.onboarding_enable_notifs))
        }
        Spacer(Modifier.size(10.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = OnAccent),
        ) {
            Text(stringResource(R.string.onboarding_start), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, titleRes: Int, bodyRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(44.dp).clip(CircleShape).background(AccentPrimary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AccentPrimary)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
