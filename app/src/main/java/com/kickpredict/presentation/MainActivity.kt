package com.kickpredict.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kickpredict.KickPredictApplication
import com.kickpredict.presentation.navigation.KickPredictNavHost
import com.kickpredict.presentation.theme.KickPredictTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { KickPredictApp() }
    }
}

@Composable
private fun KickPredictApp() {
    val container = (LocalContext.current.applicationContext as KickPredictApplication).container
    val theme by container.themePreference.theme.collectAsState()

    KickPredictTheme(theme = theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            KickPredictNavHost(
                currentTheme = theme,
                onSelectTheme = container.themePreference::select,
            )
        }
    }
}
