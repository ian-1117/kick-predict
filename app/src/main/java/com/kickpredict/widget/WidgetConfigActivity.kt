package com.kickpredict.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.updateAll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kickpredict.KickPredictApplication
import com.kickpredict.R
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.presentation.locale.LocalizedContent
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.KickPredictTheme
import kotlinx.coroutines.launch

/** Configuration screen shown when the widget is placed: choose what it shows. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default to cancelled so backing out doesn't add the widget.
        setResult(Activity.RESULT_CANCELED)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val container = (application as KickPredictApplication).container
        val options = buildList {
            add(WidgetConfigPreference.FILTER_ALL to getString(R.string.filter_all))
            add(WidgetConfigPreference.FILTER_FOLLOWED to getString(R.string.filter_followed))
            LeagueType.entries.forEach { add(it.name to it.displayName) }
        }

        setContent {
            KickPredictTheme(theme = container.themePreference.theme.value) {
                LocalizedContent(container.languagePreference.language.value) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ConfigContent(options) { value -> choose(value, appWidgetId) }
                    }
                }
            }
        }
    }

    private fun choose(value: String, appWidgetId: Int) {
        WidgetConfigPreference(this).filter = value
        lifecycleScope.launch {
            runCatching { KickPredictWidget().updateAll(this@WidgetConfigActivity) }
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@Composable
private fun ConfigContent(options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                stringResource(R.string.widget_config_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(options, key = { it.first }) { (value, label) ->
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AccentPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(value) }
                    .padding(18.dp),
            )
        }
    }
}
