package com.kickpredict.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamNamesKoTest {

    @Test
    fun `long feed names with suffixes resolve to the Korean name`() {
        assertEquals("레알 마드리드", TeamNamesKo.of("Real Madrid CF"))
        assertEquals("비야레알", TeamNamesKo.of("Villarreal CF"))
        assertEquals("레알 소시에다드", TeamNamesKo.of("Real Sociedad de Fútbol"))
        assertEquals("아틀레티코 마드리드", TeamNamesKo.of("Club Atlético de Madrid"))
    }

    @Test
    fun `accents and umlauts are ignored when matching`() {
        assertEquals("바이에른 뮌헨", TeamNamesKo.of("Bayern München"))
        assertEquals("묀헨글라트바흐", TeamNamesKo.of("Borussia Mönchengladbach"))
    }

    @Test
    fun `FC and other latin sub-brands are kept in the Korean value`() {
        assertEquals("FC 서울", TeamNamesKo.of("FC Seoul"))
        assertEquals("광주 FC", TeamNamesKo.of("Gwangju FC"))
        assertEquals("부천 FC 1995", TeamNamesKo.of("Bucheon FC 1995"))
        assertEquals("제주 SK", TeamNamesKo.of("Jeju SK"))
        assertEquals("울산 HD", TeamNamesKo.of("Ulsan HD"))
    }

    @Test
    fun `Suwon FC and Suwon Samsung are not conflated`() {
        assertEquals("수원 FC", TeamNamesKo.of("Suwon FC"))
        assertEquals("수원 FC", TeamNamesKo.of("Suwon")) // the feeds call Suwon FC just "Suwon"
        assertEquals("수원 삼성", TeamNamesKo.of("Suwon Samsung Bluewings"))
        assertEquals("수원 삼성", TeamNamesKo.of("Suwon Bluewings"))
    }

    @Test
    fun `short CSV forms and long live forms both resolve`() {
        // Atletico: "Ath Madrid" (CSV) and "Club Atlético de Madrid" (live).
        assertEquals("아틀레티코 마드리드", TeamNamesKo.of("Ath Madrid"))
        assertEquals("아틀레티코 마드리드", TeamNamesKo.of("Club Atlético de Madrid"))
        // Nottingham: "Nott'm Forest" (CSV) and "Nottingham Forest FC" (live).
        assertEquals("노팅엄 포레스트", TeamNamesKo.of("Nott'm Forest"))
        assertEquals("노팅엄 포레스트", TeamNamesKo.of("Nottingham Forest FC"))
        // Man City: abbreviated and full.
        assertEquals("맨체스터 시티", TeamNamesKo.of("Man City"))
        assertEquals("맨체스터 시티", TeamNamesKo.of("Manchester City FC"))
    }

    @Test
    fun `founding-year and club-abbreviation noise is dropped`() {
        assertEquals("호펜하임", TeamNamesKo.of("TSG 1899 Hoffenheim"))
        assertEquals("볼로냐", TeamNamesKo.of("Bologna FC 1909"))
        assertEquals("나폴리", TeamNamesKo.of("SSC Napoli"))
        assertEquals("쾰른", TeamNamesKo.of("1. FC Köln"))
        assertEquals("우디네세", TeamNamesKo.of("Udinese Calcio"))
    }

    @Test
    fun `Santander resolves now that it is mapped`() {
        assertEquals("라싱 산탄데르", TeamNamesKo.of("Real Racing Club de Santander"))
    }

    @Test
    fun `unmapped teams return null so the caller falls back to English`() {
        assertNull(TeamNamesKo.of("Some Unknown FC"))
        assertNull(TeamNamesKo.of("Nonexistent United"))
    }
}
