package com.kickpredict.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers which teams the user follows (by team id). Backed by the same SharedPreferences file as
 * the other settings so it's readable synchronously. Followed teams drive the "Followed" match-list
 * filter and scope match notifications to what the user cares about.
 */
class FollowPreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _followed = MutableStateFlow(read())
    val followed: StateFlow<Set<String>> = _followed.asStateFlow()

    fun isFollowed(teamId: String): Boolean = _followed.value.contains(teamId)

    fun toggle(teamId: String) {
        val next = _followed.value.toMutableSet()
        if (!next.add(teamId)) next.remove(teamId)
        preferences.edit().putStringSet(KEY_FOLLOWED, next).apply()
        _followed.value = next
    }

    private fun read(): Set<String> =
        HashSet(preferences.getStringSet(KEY_FOLLOWED, emptySet()) ?: emptySet())

    private companion object {
        const val FILE_NAME = "kick_predict_settings"
        const val KEY_FOLLOWED = "followed_teams"
    }
}
