package com.kickpredict.presentation.ads

import android.content.Context
import com.kickpredict.domain.model.LeagueType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which leagues are unlocked. The K League tiers are always free; the European leagues are gated
 * behind a rewarded ad that unlocks them for [UNLOCK_HOURS] hours. The unlock deadline is persisted
 * (SharedPreferences, same file as the other prefs) so it survives restarts.
 */
class LeagueAccessPreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _europeUnlockedUntil = MutableStateFlow(preferences.getLong(KEY_UNTIL, 0L))
    val europeUnlockedUntil: StateFlow<Long> = _europeUnlockedUntil.asStateFlow()

    /** True while the European leagues are unlocked (deadline still in the future). */
    fun isEuropeUnlocked(nowMillis: Long): Boolean = nowMillis < _europeUnlockedUntil.value

    /** Grant European access for [UNLOCK_HOURS] from now (called after a rewarded ad completes). */
    fun unlockEurope(nowMillis: Long) {
        val until = nowMillis + UNLOCK_HOURS * 60L * 60L * 1000L
        preferences.edit().putLong(KEY_UNTIL, until).apply()
        _europeUnlockedUntil.value = until
    }

    companion object {
        const val UNLOCK_HOURS = 24L

        /** The always-free leagues; everything else is gated. */
        val FREE_LEAGUES = setOf(LeagueType.K_LEAGUE, LeagueType.K_LEAGUE_2)

        fun LeagueType.isFree(): Boolean = this in FREE_LEAGUES

        private const val FILE_NAME = "kick_predict_settings"
        private const val KEY_UNTIL = "europe_unlocked_until"
    }
}
