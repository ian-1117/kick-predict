package com.kickpredict.data

/**
 * Shared name-matching for the per-team lookups ([TeamNamesKo], [TeamColors]). The feeds return a
 * team's name in many forms — short CSV codes ("Ath Madrid"), long live names ("Club Atlético de
 * Madrid"), founding-year variants ("Bologna FC 1909"). Two keys collapse those onto one entry:
 *
 *  - **strict** — accents stripped, all tokens kept — so clubs that differ only by a club-type token
 *    stay apart (수원 FC vs 수원 삼성, FC 서울 vs the bare "Seoul").
 *  - **loose** — additionally drops generic club tokens (FC/CF/SSC/…) and standalone founding-year
 *    numbers — so the long forms fall back onto the plain name.
 *
 * Lookup tries strict first, then loose.
 */
internal object TeamKey {

    /** Generic club-type tokens the feeds tack on ("Real Madrid CF", "SSC Napoli", "TSG 1899 …"). */
    private val generic = setOf(
        "fc", "cf", "sc", "afc", "cd", "ud", "cp", "rc", "rcd", "ac", "as",
        "ss", "ssc", "cfc", "acf", "ca", "fsv", "bc", "sv", "vfb", "tsg", "us",
        "club", "de", "del", "calcio", "futbol", "football", "balompie",
    )

    private fun tokens(name: String): List<String> =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

    fun strict(name: String): String = tokens(name).joinToString("")

    fun loose(name: String): String =
        tokens(name).filter { it !in generic && !it.all(Char::isDigit) }.joinToString("")
}

/**
 * A two-pass (strict → loose) lookup over a list of (english-name, value) entries. Loose keys can
 * collide (e.g. "Suwon" vs "Suwon FC" both → "suwon"); the strict pass resolves those, so a loose
 * collision is only ever a last-resort fallback.
 */
internal class TeamLookup<V>(entries: List<Pair<String, V>>) {
    private val strict: Map<String, V> = entries.associate { TeamKey.strict(it.first) to it.second }
    private val loose: Map<String, V> = entries.associate { TeamKey.loose(it.first) to it.second }

    fun of(name: String): V? = strict[TeamKey.strict(name)] ?: loose[TeamKey.loose(name)]
}
