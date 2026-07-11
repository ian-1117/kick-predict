package com.kickpredict.presentation.locale

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the user's [AppLanguage] override. Backed by SharedPreferences (same file as the theme)
 * so it is readable synchronously on first composition and the app opens in the chosen language.
 */
class LanguagePreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(AppLanguage.fromName(preferences.getString(KEY_LANGUAGE, null)))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun select(language: AppLanguage) {
        if (_language.value == language) return
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
        _language.value = language
    }

    private companion object {
        const val FILE_NAME = "kick_predict_settings"
        const val KEY_LANGUAGE = "app_language"
    }
}
