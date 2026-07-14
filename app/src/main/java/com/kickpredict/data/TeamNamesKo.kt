package com.kickpredict.data

/**
 * Korean display names for teams the live feeds return in English. Keyed by a **normalised** form of
 * the English name (lowercased, alphanumerics only, a leading/trailing generic club token like "fc"
 * dropped) so "FC Seoul", "Seoul" and "Seoul FC" all resolve to the same entry. Latin sub-brands that
 * Korean fans keep as-is — **FC · SK · HD · SC · CF · AC** — are left in the Korean value verbatim.
 *
 * Only teams present here get a Korean name; anything unmapped falls back to its English name, so the
 * feed can add clubs without breaking. K League 1 & 2 are covered in full; the European set covers the
 * clubs most likely to appear.
 */
object TeamNamesKo {

    /**
     * Korean name for [englishName], or null when we don't have one (caller falls back to English).
     * Two passes: first a **strict** key that keeps every token, so distinct clubs that differ only by
     * a club-type token stay apart (수원 FC vs 수원 삼성 / FC 서울 vs the bare "Seoul"); only if that
     * misses do we fall back to a **loose** key that drops generic tokens, to catch the feeds' long
     * forms ("Real Madrid CF" → "Real Madrid").
     */
    fun of(englishName: String): String? =
        strictMap[strictKey(englishName)] ?: looseMap[looseKey(englishName)]

    /** Generic club-type tokens the feeds tack on ("Real Madrid CF", "Real Sociedad de Fútbol"). */
    private val generic = setOf(
        "fc", "cf", "sc", "afc", "cd", "ud", "cp", "rc", "rcd", "ac", "as",
        "club", "de", "del", "calcio", "futbol", "football",
    )

    private fun deaccentTokens(name: String): List<String> =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

    /** Every token kept — distinguishes clubs that differ only by "FC"/"SK"/etc. */
    private fun strictKey(name: String): String = deaccentTokens(name).joinToString("")

    /** Generic club tokens dropped — collapses the feeds' long forms onto the short name. */
    private fun looseKey(name: String): String =
        deaccentTokens(name).filter { it !in generic }.joinToString("")

    private val strictMap: Map<String, String> by lazy { entries.associate { strictKey(it.first) to it.second } }
    // Loose keys can collide (e.g. "Suwon" vs "Suwon FC" both → "suwon"); the strict pass resolves
    // those, so an arbitrary winner here is only a last-resort fallback.
    private val looseMap: Map<String, String> by lazy { entries.associate { looseKey(it.first) to it.second } }

    private infix fun String.ko(korean: String): Pair<String, String> = this to korean

    private val entries: List<Pair<String, String>> = listOf(
        // --- K League 1 ---
        "Ulsan HD" ko "울산 HD",
        "Ulsan Hyundai" ko "울산 HD",
        "Jeonbuk" ko "전북",
        "Jeonbuk Hyundai Motors" ko "전북 현대",
        "Jeonbuk Motors" ko "전북 현대",
        "Pohang" ko "포항",
        "Pohang Steelers" ko "포항 스틸러스",
        "Gimcheon Sangmu" ko "김천 상무",
        "Seoul" ko "서울",
        "FC Seoul" ko "FC 서울",
        "Gwangju" ko "광주 FC",
        "Gwangju FC" ko "광주 FC",
        "Daegu" ko "대구 FC",
        "Daegu FC" ko "대구 FC",
        "Suwon FC" ko "수원 FC",
        "Gangwon" ko "강원",
        "Gangwon FC" ko "강원 FC",
        "Jeju" ko "제주 SK",
        "Jeju SK" ko "제주 SK",
        "Jeju United" ko "제주 SK",
        "Incheon" ko "인천",
        "Incheon United" ko "인천 유나이티드",
        "Daejeon" ko "대전",
        "Daejeon Hana Citizen" ko "대전 하나 시티즌",
        "Daejeon Citizen" ko "대전 시티즌",
        // --- K League 2 ---
        "Anyang" ko "안양",
        "FC Anyang" ko "FC 안양",
        "Bucheon" ko "부천",
        "Bucheon FC 1995" ko "부천 FC 1995",
        "Busan" ko "부산",
        "Busan IPark" ko "부산 아이파크",
        "Busan I Park" ko "부산 아이파크",
        "Seoul E-Land" ko "서울 이랜드",
        "Seoul E Land" ko "서울 이랜드",
        "Gyeongnam" ko "경남 FC",
        "Gyeongnam FC" ko "경남 FC",
        "Chungnam Asan" ko "충남 아산",
        "Asan" ko "충남 아산",
        "Jeonnam" ko "전남",
        "Jeonnam Dragons" ko "전남 드래곤즈",
        "Gimpo" ko "김포 FC",
        "Gimpo FC" ko "김포 FC",
        "Cheonan" ko "천안 시티",
        "Cheonan City" ko "천안 시티",
        "Ansan" ko "안산 그리너스",
        "Ansan Greeners" ko "안산 그리너스",
        "Seongnam" ko "성남",
        "Seongnam FC" ko "성남 FC",
        "Chungbuk Cheongju" ko "충북 청주",
        "Cheongju" ko "청주",
        // The feeds call Suwon FC just "Suwon"; the Samsung side always carries "Bluewings"/"Samsung".
        "Suwon" ko "수원 FC",
        "Suwon Bluewings" ko "수원 삼성",
        "Suwon Samsung" ko "수원 삼성",
        "Suwon Samsung Bluewings" ko "수원 삼성",
        // --- Premier League ---
        "Arsenal" ko "아스날",
        "Aston Villa" ko "아스톤 빌라",
        "Bournemouth" ko "본머스",
        "Brentford" ko "브렌트포드",
        "Brighton Hove Albion" ko "브라이튼",
        "Brighton" ko "브라이튼",
        "Chelsea" ko "첼시",
        "Crystal Palace" ko "크리스탈 팰리스",
        "Everton" ko "에버튼",
        "Fulham" ko "풀럼",
        "Ipswich Town" ko "입스위치",
        "Leicester City" ko "레스터 시티",
        "Liverpool" ko "리버풀",
        "Manchester City" ko "맨체스터 시티",
        "Manchester United" ko "맨체스터 유나이티드",
        "Newcastle United" ko "뉴캐슬",
        "Nottingham Forest" ko "노팅엄 포레스트",
        "Southampton" ko "사우샘프턴",
        "Tottenham Hotspur" ko "토트넘",
        "Tottenham" ko "토트넘",
        "West Ham United" ko "웨스트햄",
        "Wolverhampton Wanderers" ko "울버햄튼",
        "Wolves" ko "울버햄튼",
        // --- LaLiga ---
        "Real Madrid" ko "레알 마드리드",
        "Barcelona" ko "바르셀로나",
        "FC Barcelona" ko "FC 바르셀로나",
        "Atletico Madrid" ko "아틀레티코 마드리드",
        "Club Atletico de Madrid" ko "아틀레티코 마드리드",
        "Athletic Club" ko "아틀레틱 빌바오",
        "Athletic Bilbao" ko "아틀레틱 빌바오",
        "Real Sociedad" ko "레알 소시에다드",
        "Real Betis" ko "레알 베티스",
        "Villarreal" ko "비야레알",
        "Valencia" ko "발렌시아",
        "Sevilla" ko "세비야",
        "Girona" ko "지로나",
        "Getafe" ko "헤타페",
        "Osasuna" ko "오사수나",
        "Celta Vigo" ko "셀타 비고",
        "Rayo Vallecano" ko "라요 바예카노",
        "Mallorca" ko "마요르카",
        "Malaga" ko "말라가",
        "Espanyol" ko "에스파뇰",
        // --- Serie A ---
        "Inter" ko "인테르",
        "Internazionale" ko "인테르",
        "AC Milan" ko "AC 밀란",
        "Milan" ko "AC 밀란",
        "Juventus" ko "유벤투스",
        "Napoli" ko "나폴리",
        "AS Roma" ko "AS 로마",
        "Roma" ko "AS 로마",
        "Lazio" ko "라치오",
        "Atalanta" ko "아탈란타",
        "Fiorentina" ko "피오렌티나",
        "Bologna" ko "볼로냐",
        "Torino" ko "토리노",
        // --- Bundesliga ---
        "Bayern Munich" ko "바이에른 뮌헨",
        "Bayern Munchen" ko "바이에른 뮌헨",
        "Borussia Dortmund" ko "도르트문트",
        "Dortmund" ko "도르트문트",
        "RB Leipzig" ko "RB 라이프치히",
        "Bayer Leverkusen" ko "레버쿠젠",
        "Borussia Monchengladbach" ko "묀헨글라트바흐",
        "Eintracht Frankfurt" ko "프랑크푸르트",
        "VfB Stuttgart" ko "슈투트가르트",
        "Stuttgart" ko "슈투트가르트",
        "Wolfsburg" ko "볼프스부르크",
        "Werder Bremen" ko "베르더 브레멘",
    )
}
