package com.kickpredict.presentation.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers which [AppTheme] the user picked.
 *
 * Backed by SharedPreferences rather than Room: it is one enum name, and it has to be readable
 * synchronously when the activity first composes, or the app flashes the default palette.
 */
class ThemePreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(AppTheme.fromName(preferences.getString(KEY_THEME, null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun select(theme: AppTheme) {
        if (_theme.value == theme) return
        preferences.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    private companion object {
        const val FILE_NAME = "kick_predict_settings"
        const val KEY_THEME = "app_theme"
    }
}
