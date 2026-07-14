package com.kickpredict.data.local

import androidx.room.TypeConverter
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.MatchOutcome

/** Room type converters for the enum + list columns on [com.kickpredict.data.local.entity.TeamEntity]. */
class Converters {

    @TypeConverter
    fun leagueToString(value: LeagueType): String = value.name

    @TypeConverter
    fun stringToLeague(value: String): LeagueType = LeagueType.valueOf(value)

    @TypeConverter
    fun formToString(form: List<MatchOutcome>): String =
        form.joinToString(separator = ",") { it.name }

    @TypeConverter
    fun stringToForm(value: String): List<MatchOutcome> =
        if (value.isBlank()) emptyList()
        else value.split(",").map { MatchOutcome.valueOf(it) }

    @TypeConverter
    fun stringListToString(value: List<String>): String = value.joinToString(separator = ",")

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",")
}
