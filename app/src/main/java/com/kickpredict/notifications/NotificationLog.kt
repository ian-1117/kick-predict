package com.kickpredict.notifications

import android.content.Context

/**
 * Remembers which notification events have already fired (by [MatchNotification.key]) so each one
 * posts at most once. Also used to *prime* the log when notifications are first enabled — marking
 * every current candidate as seen so the backlog doesn't all fire at once.
 */
class NotificationLog(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun wasNotified(key: String): Boolean = current().contains(key)

    fun markNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val set = current().apply { addAll(keys) }
        preferences.edit().putStringSet(KEY_KEYS, set).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_KEYS).apply()
    }

    // getStringSet returns a shared, immutable-in-spirit set — copy before mutating.
    private fun current(): MutableSet<String> =
        HashSet(preferences.getStringSet(KEY_KEYS, emptySet()) ?: emptySet())

    private companion object {
        const val FILE_NAME = "kick_predict_settings"
        const val KEY_KEYS = "notified_keys"
    }
}
