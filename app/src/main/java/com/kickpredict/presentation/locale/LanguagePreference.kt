package com.kickpredict.presentation.locale

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Remembers the user's [AppLanguage] override. Backed by SharedPreferences (same file as the theme)
 * so it is readable synchronously on first composition and the app opens in the chosen language.
 *
 * Resources switch via [LocalizedContent] (a Compose config override), but data-driven text like team
 * names has no resource to resolve against — so the effective language is also mirrored onto
 * [Locale.getDefault] here, which [com.kickpredict.domain.model.TeamProfile.displayName] reads.
 */
class LanguagePreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** The device locale, captured before any override, so SYSTEM can restore it. */
    private val systemLocale: Locale = Locale.getDefault()

    private val _language = MutableStateFlow(AppLanguage.fromName(preferences.getString(KEY_LANGUAGE, null)))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    init {
        applyLocale(_language.value)
    }

    fun select(language: AppLanguage) {
        if (_language.value == language) return
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
        applyLocale(language) // before the emit, so the recomposition it triggers reads the new locale
        _language.value = language
    }

    private fun applyLocale(language: AppLanguage) {
        Locale.setDefault(language.locale ?: systemLocale)
    }

    private companion object {
        const val FILE_NAME = "kick_predict_settings"
        const val KEY_LANGUAGE = "app_language"
    }
}
