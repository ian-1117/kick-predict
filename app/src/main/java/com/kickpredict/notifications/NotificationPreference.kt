package com.kickpredict.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers whether background match notifications are on. Opt-in (default off); backed by the same
 * SharedPreferences file as the theme/language settings so it's readable synchronously.
 */
class NotificationPreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        if (_enabled.value == value) return
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    private companion object {
        const val FILE_NAME = "kick_predict_settings"
        const val KEY_ENABLED = "notifications_enabled"
    }
}
