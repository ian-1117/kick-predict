package com.kickpredict.presentation.locale

import java.util.Locale

/** The in-app language override. [SYSTEM] follows the device locale (via the -ko/default resources). */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    KOREAN("ko"),
    ENGLISH("en");

    val locale: Locale? get() = tag?.let { Locale.forLanguageTag(it) }

    companion object {
        fun fromName(name: String?): AppLanguage = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
