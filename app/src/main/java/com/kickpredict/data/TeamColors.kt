package com.kickpredict.data

/**
 * Each club's signature colour for the generated (logo-free) crest disc. Keyed by the same feed names
 * as [TeamNamesKo] via the shared [TeamLookup], so every name variant resolves. Unmapped teams fall
 * back to the deterministic hash colour, so the feed can add clubs without breaking. Colours are ARGB
 * longs; the crest's text/border colour is derived from luminance by [foregroundFor] so light brand
 * colours (Real Madrid white, Wolves gold) still read.
 */
object TeamColors {

    private val lookup by lazy { TeamLookup(entries) }

    /** Brand colour for [englishName], or null when unmapped (caller uses the hash fallback). */
    fun of(englishName: String): Long? = lookup.of(englishName)

    /** A readable text/border colour for a crest painted [background]: dark on light, white on dark. */
    fun foregroundFor(background: Long): Long {
        val r = ((background shr 16) and 0xFF).toDouble()
        val g = ((background shr 8) and 0xFF).toDouble()
        val b = (background and 0xFF).toDouble()
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.6) 0xFF1A1A1A else 0xFFFFFFFF
    }

    private infix fun String.col(argb: Long): Pair<String, Long> = this to argb

    private val entries: List<Pair<String, Long>> = listOf(
        // --- K League 1 ---
        "Ulsan HD" col 0xFF0067AC,
        "Jeonbuk" col 0xFF006437,
        "Pohang" col 0xFFB50021,
        "Gimcheon Sangmu" col 0xFFE4032E,
        "Seoul" col 0xFFCE0E2D,
        "Gwangju FC" col 0xFFFFD200,
        "Daegu FC" col 0xFF00A0E9,
        "Suwon FC" col 0xFF000000,
        "Gangwon" col 0xFFF15A22,
        "Jeju SK" col 0xFFEE7523,
        "Incheon" col 0xFF004097,
        "Daejeon Hana Citizen" col 0xFF582C83,
        // --- K League 2 ---
        "Anyang" col 0xFF6A1B9A,
        "Bucheon FC 1995" col 0xFFC8102E,
        "Busan" col 0xFFE4002B,
        "Seoul E-Land" col 0xFF1D2951,
        "Gyeongnam" col 0xFFB4131E,
        "Chungnam Asan" col 0xFF00539B,
        "Jeonnam" col 0xFFFFD200,
        "Gimpo" col 0xFF00A651,
        "Cheonan" col 0xFF0072BC,
        "Ansan" col 0xFF1AAE4E,
        "Seongnam" col 0xFF000000,
        "Chungbuk Cheongju" col 0xFF16264A,
        "Yongin" col 0xFF6D214F,
        "Hwaseong" col 0xFFF37021,
        "Suwon Samsung" col 0xFF1E4EA1,
        "Suwon Bluewings" col 0xFF1E4EA1,
        "Suwon Samsung Bluewings" col 0xFF1E4EA1,
        // --- Premier League ---
        "Arsenal" col 0xFFEF0107,
        "Aston Villa" col 0xFF670E36,
        "Bournemouth" col 0xFFDA291C,
        "Brentford" col 0xFFE30613,
        "Brighton" col 0xFF0057B8,
        "Burnley" col 0xFF6C1D45,
        "Chelsea" col 0xFF034694,
        "Coventry City" col 0xFF6CACE4,
        "Crystal Palace" col 0xFF1B458F,
        "Everton" col 0xFF003399,
        "Fulham" col 0xFF000000,
        "Hull City" col 0xFFF5A12D,
        "Ipswich Town" col 0xFF0044A9,
        "Leeds" col 0xFF1D428A,
        "Leicester City" col 0xFF003090,
        "Liverpool" col 0xFFC8102E,
        "Man City" col 0xFF6CABDD,
        "Man United" col 0xFFDA291C,
        "Newcastle" col 0xFF241F20,
        "Nott'm Forest" col 0xFFDD0000,
        "Southampton" col 0xFFD71920,
        "Sunderland" col 0xFFEB172B,
        "Tottenham" col 0xFF132257,
        "West Ham" col 0xFF7A263A,
        "Wolves" col 0xFFFDB913,
        // --- LaLiga ---
        "Real Madrid" col 0xFFFFFFFF,
        "Barcelona" col 0xFFA50044,
        "Ath Madrid" col 0xFFCB3524,
        "Ath Bilbao" col 0xFFEE2523,
        "Alaves" col 0xFF0761AF,
        "Betis" col 0xFF00954C,
        "Celta" col 0xFF8AC3EE,
        "Elche" col 0xFF00844A,
        "Espanyol" col 0xFF007FC8,
        "Getafe" col 0xFF005999,
        "Girona" col 0xFFC4122E,
        "Levante" col 0xFF004B9F,
        "Mallorca" col 0xFFE30613,
        "Malaga" col 0xFF00A0E1,
        "Osasuna" col 0xFFD91A21,
        "Oviedo" col 0xFF004B9C,
        "Deportivo La Coruna" col 0xFF0072CE,
        "Rayo Vallecano" col 0xFFE53027,
        "Real Sociedad" col 0xFF0067B1,
        "Santander" col 0xFF009B48,
        "Sevilla" col 0xFFD81E05,
        "Valencia" col 0xFFF7A800,
        "Villarreal" col 0xFFFFE667,
        // --- Serie A ---
        "Inter" col 0xFF010E80,
        "Milan" col 0xFFFB090B,
        "Monza" col 0xFFE2001A,
        "Fiorentina" col 0xFF592C82,
        "Roma" col 0xFF8E1F2F,
        "Atalanta" col 0xFF1E71B8,
        "Bologna" col 0xFF1A2F48,
        "Cagliari" col 0xFF00224E,
        "Como" col 0xFF0A4595,
        "Cremonese" col 0xFFB01E2E,
        "Frosinone" col 0xFF004B9F,
        "Genoa" col 0xFF002E5B,
        "Juventus" col 0xFF000000,
        "Lazio" col 0xFF87D8F7,
        "Lecce" col 0xFFED1C24,
        "Napoli" col 0xFF12A0D7,
        "Parma" col 0xFFFFD100,
        "Pisa" col 0xFF00337F,
        "Sassuolo" col 0xFF00A551,
        "Torino" col 0xFF8A1E03,
        "Udinese" col 0xFF000000,
        "Venezia" col 0xFF0A5C36,
        "Verona" col 0xFF002E5B,
        // --- Bundesliga ---
        "Bayern Munich" col 0xFFDC052D,
        "Dortmund" col 0xFFFDE100,
        "RB Leipzig" col 0xFFDD0741,
        "Bayer Leverkusen" col 0xFFE32219,
        "Borussia Monchengladbach" col 0xFF1D6F42,
        "Eintracht Frankfurt" col 0xFF000000,
        "VfB Stuttgart" col 0xFFE32219,
        "Wolfsburg" col 0xFF65B32E,
        "Werder Bremen" col 0xFF1D9053,
        "Koln" col 0xFFED1C24,
        "Union Berlin" col 0xFFEB1923,
        "Mainz" col 0xFFE30613,
        "Augsburg" col 0xFFBA3733,
        "Schalke 04" col 0xFF004D9D,
        "Freiburg" col 0xFFE2001A,
        "Hamburg" col 0xFF003087,
        "Heidenheim" col 0xFFE30613,
        "Hoffenheim" col 0xFF1961B5,
        "Paderborn" col 0xFF004E9E,
        "St Pauli" col 0xFF60271A,
        // Extra name variants (so a colour resolves wherever the name does — the feeds mix short CSV
        // codes with long live names whose loose keys differ).
        "Atletico Madrid" col 0xFFCB3524,
        "Athletic Club" col 0xFFEE2523,
        "Real Betis" col 0xFF00954C,
        "Celta Vigo" col 0xFF8AC3EE,
        "Espanol" col 0xFF007FC8,
        "Espanyol Barcelona" col 0xFF007FC8,
        "Sociedad" col 0xFF0067B1,
        "Vallecano" col 0xFFE53027,
        "Rayo Vallecano Madrid" col 0xFFE53027,
        "Real Oviedo" col 0xFF004B9C,
        "Manchester City" col 0xFF6CABDD,
        "Manchester United" col 0xFFDA291C,
        "Newcastle United" col 0xFF241F20,
        "Nottingham Forest" col 0xFFDD0000,
        "West Ham United" col 0xFF7A263A,
        "Leeds United" col 0xFF1D428A,
        "Borussia Dortmund" col 0xFFFDE100,
        "Bayern Munchen" col 0xFFDC052D,
        "Leverkusen" col 0xFFE32219,
        "M'gladbach" col 0xFF1D6F42,
        "Ein Frankfurt" col 0xFF000000,
        "Stuttgart" col 0xFFE32219,
        "Hamburger" col 0xFF003087,
    )
}
