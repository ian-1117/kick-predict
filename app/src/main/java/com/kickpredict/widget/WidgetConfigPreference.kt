package com.kickpredict.widget

import android.content.Context

/**
 * What the home-screen widget shows: everything, followed teams only, or a single league (stored as
 * the [com.kickpredict.domain.model.LeagueType] name). Global across widget instances for simplicity.
 */
class WidgetConfigPreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** [FILTER_ALL], [FILTER_FOLLOWED], or a LeagueType name. */
    var filter: String
        get() = preferences.getString(KEY_FILTER, FILTER_ALL) ?: FILTER_ALL
        set(value) {
            preferences.edit().putString(KEY_FILTER, value).apply()
        }

    companion object {
        const val FILTER_ALL = "ALL"
        const val FILTER_FOLLOWED = "FOLLOWED"
        private const val FILE_NAME = "kick_predict_settings"
        private const val KEY_FILTER = "widget_filter"
    }
}
