package com.kickpredict.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TeamColorsTest {

    @Test
    fun `brand colours resolve across name variants`() {
        assertEquals(0xFFC8102E, TeamColors.of("Liverpool"))
        assertEquals(0xFF6CABDD, TeamColors.of("Man City"))
        assertEquals(0xFF6CABDD, TeamColors.of("Manchester City FC")) // long live form
        assertEquals(0xFFCB3524, TeamColors.of("Club Atlético de Madrid")) // via loose key
        assertNotNull(TeamColors.of("Gwangju FC"))
    }

    @Test
    fun `unmapped teams return null so the caller uses the hash colour`() {
        assertNull(TeamColors.of("Some Unknown FC"))
    }

    @Test
    fun `foreground is dark on a light brand colour and white on a dark one`() {
        assertEquals(0xFF1A1A1A, TeamColors.foregroundFor(0xFFFFFFFF)) // Real Madrid white
        assertEquals(0xFF1A1A1A, TeamColors.foregroundFor(0xFFFDE100)) // Dortmund yellow
        assertEquals(0xFFFFFFFF, TeamColors.foregroundFor(0xFFC8102E)) // Liverpool red
        assertEquals(0xFFFFFFFF, TeamColors.foregroundFor(0xFF000000)) // black
    }
}
