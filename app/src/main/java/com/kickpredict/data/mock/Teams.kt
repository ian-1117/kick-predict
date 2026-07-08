package com.kickpredict.data.mock

import com.kickpredict.domain.model.LeagueType

/**
 * A club in the sample catalog. Only factual club names and approximate club colours are used;
 * crests are generated in-app from initials + colours (no copyrighted logo assets).
 *
 * @param rating baseline strength; the list order per league also defines table position.
 */
data class TeamSeed(
    val id: String,
    val name: String,
    val korean: String,
    val short: String,
    val rating: Double,
    val primary: Long,
    val secondary: Long,
)

/** Real-club catalog for the five leagues, strongest-first (index defines table position). */
object Teams {

    val byLeague: Map<LeagueType, List<TeamSeed>> = mapOf(
        LeagueType.EPL to listOf(
            TeamSeed("epl_mci", "Manchester City", "맨체스터 시티", "MCI", 90.0, 0xFF6CABDD, 0xFF1C2C5B),
            TeamSeed("epl_ars", "Arsenal", "아스날", "ARS", 87.0, 0xFFEF0107, 0xFFFFFFFF),
            TeamSeed("epl_liv", "Liverpool", "리버풀", "LIV", 86.0, 0xFFC8102E, 0xFF00B2A9),
            TeamSeed("epl_mun", "Manchester United", "맨체스터 유나이티드", "MUN", 83.0, 0xFFDA291C, 0xFFFBE122),
            TeamSeed("epl_che", "Chelsea", "첼시", "CHE", 82.0, 0xFF034694, 0xFFFFFFFF),
            TeamSeed("epl_tot", "Tottenham Hotspur", "토트넘", "TOT", 82.0, 0xFF132257, 0xFFFFFFFF),
            TeamSeed("epl_new", "Newcastle United", "뉴캐슬", "NEW", 80.0, 0xFF241F20, 0xFFFFFFFF),
            TeamSeed("epl_avl", "Aston Villa", "아스톤 빌라", "AVL", 79.0, 0xFF670E36, 0xFF95BFE5),
        ),
        LeagueType.LALIGA to listOf(
            TeamSeed("lal_rma", "Real Madrid", "레알 마드리드", "RMA", 91.0, 0xFFFFFFFF, 0xFFFEBE10),
            TeamSeed("lal_bar", "Barcelona", "바르셀로나", "BAR", 88.0, 0xFFA50044, 0xFF004D98),
            TeamSeed("lal_atm", "Atlético Madrid", "아틀레티코 마드리드", "ATM", 85.0, 0xFFCB3524, 0xFF272E61),
            TeamSeed("lal_ath", "Athletic Bilbao", "아틀레틱 빌바오", "ATH", 80.0, 0xFFEE2523, 0xFFFFFFFF),
            TeamSeed("lal_rso", "Real Sociedad", "레알 소시에다드", "RSO", 79.0, 0xFF0067B1, 0xFFFFFFFF),
            TeamSeed("lal_vil", "Villarreal", "비야레알", "VIL", 78.0, 0xFFFFE667, 0xFF005187),
            TeamSeed("lal_bet", "Real Betis", "레알 베티스", "BET", 77.0, 0xFF00954C, 0xFFFFFFFF),
            TeamSeed("lal_val", "Valencia", "발렌시아", "VAL", 76.0, 0xFFFFFFFF, 0xFFF18E00),
        ),
        LeagueType.SERIE_A to listOf(
            TeamSeed("ita_int", "Inter", "인터 밀란", "INT", 87.0, 0xFF010E80, 0xFF000000),
            TeamSeed("ita_juv", "Juventus", "유벤투스", "JUV", 85.0, 0xFF000000, 0xFFFFFFFF),
            TeamSeed("ita_mil", "AC Milan", "AC 밀란", "MIL", 84.0, 0xFFFB090B, 0xFF000000),
            TeamSeed("ita_nap", "Napoli", "나폴리", "NAP", 83.0, 0xFF12A0D7, 0xFFFFFFFF),
            TeamSeed("ita_ata", "Atalanta", "아탈란타", "ATA", 81.0, 0xFF1D71B8, 0xFF000000),
            TeamSeed("ita_rom", "Roma", "로마", "ROM", 80.0, 0xFF8E1F2F, 0xFFF0BC42),
            TeamSeed("ita_laz", "Lazio", "라치오", "LAZ", 79.0, 0xFF87D8F7, 0xFFFFFFFF),
            TeamSeed("ita_fio", "Fiorentina", "피오렌티나", "FIO", 77.0, 0xFF592C82, 0xFFFFFFFF),
        ),
        LeagueType.BUNDESLIGA to listOf(
            TeamSeed("ger_bay", "Bayern München", "바이에른 뮌헨", "BAY", 90.0, 0xFFDC052D, 0xFF0066B2),
            TeamSeed("ger_lev", "Bayer Leverkusen", "레버쿠젠", "LEV", 85.0, 0xFFE32219, 0xFF000000),
            TeamSeed("ger_dor", "Borussia Dortmund", "도르트문트", "DOR", 84.0, 0xFFFDE100, 0xFF000000),
            TeamSeed("ger_rbl", "RB Leipzig", "라이프치히", "RBL", 82.0, 0xFFDD0741, 0xFF001F47),
            TeamSeed("ger_stu", "Stuttgart", "슈투트가르트", "STU", 79.0, 0xFFFFFFFF, 0xFFE32219),
            TeamSeed("ger_ein", "Eintracht Frankfurt", "프랑크푸르트", "SGE", 78.0, 0xFF000000, 0xFFE1000F),
            TeamSeed("ger_wob", "Wolfsburg", "볼프스부르크", "WOB", 76.0, 0xFF65B32E, 0xFFFFFFFF),
            TeamSeed("ger_scf", "Freiburg", "프라이부르크", "SCF", 75.0, 0xFFE1000F, 0xFF000000),
        ),
        LeagueType.K_LEAGUE to listOf(
            TeamSeed("kor_uls", "Ulsan HD", "울산 HD", "ULS", 82.0, 0xFF002D62, 0xFFF7A800),
            TeamSeed("kor_jbk", "Jeonbuk Hyundai", "전북 현대", "JBK", 81.0, 0xFF00623C, 0xFFFFFFFF),
            TeamSeed("kor_gim", "Gimcheon Sangmu", "김천 상무", "GIM", 78.0, 0xFFC30D23, 0xFF000000),
            TeamSeed("kor_poh", "Pohang Steelers", "포항 스틸러스", "POH", 78.0, 0xFF000000, 0xFFE60012),
            TeamSeed("kor_seo", "FC Seoul", "FC 서울", "SEO", 77.0, 0xFF000000, 0xFFC8102E),
            TeamSeed("kor_gwn", "Gangwon FC", "강원 FC", "GWN", 75.0, 0xFFF37021, 0xFF00954C),
            TeamSeed("kor_swn", "Suwon FC", "수원 FC", "SWN", 74.0, 0xFF0072BC, 0xFFED1C24),
            TeamSeed("kor_dae", "Daejeon Hana", "대전 하나", "DAE", 74.0, 0xFF6A2C91, 0xFFFFFFFF),
        ),
    )
}
