package com.kickpredict.presentation.onboarding

import android.content.Context

/** Remembers whether the one-time onboarding has been shown. Backed by the shared settings file. */
class OnboardingPreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val seen: Boolean get() = preferences.getBoolean(KEY_SEEN, false)

    fun markSeen() {
        preferences.edit().putBoolean(KEY_SEEN, true).apply()
    }

    private companion object {
        const val FILE_NAME = "kick_predict_settings"
        const val KEY_SEEN = "onboarding_seen"
    }
}
