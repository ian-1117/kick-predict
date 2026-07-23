package com.kickpredict.presentation.locale

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Applies the in-app [language] override to everything inside [content] by swapping the [LocalContext]
 * and [LocalConfiguration] for a locale-scoped copy — so `stringResource` resolves against the chosen
 * language and the whole tree recomposes when it changes. [AppLanguage.SYSTEM] leaves the device
 * locale untouched.
 */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val locale = language.locale
    if (locale == null) {
        content()
        return
    }
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val localizedConfiguration = remember(locale, configuration) {
        Configuration(configuration).apply { setLocale(locale) }
    }
    val localizedContext = remember(localizedConfiguration) {
        context.createConfigurationContext(localizedConfiguration)
    }
    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
    ) { content() }
}
