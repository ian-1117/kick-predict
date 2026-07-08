package com.kickpredict.data

import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.data.mock.ResultSimulator
import com.kickpredict.domain.engine.PredictionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultSimulatorTest {

    private val engine = PredictionEngine()

    @Test
    fun `simulated result is deterministic per match and within bounds`() {
        val match = MockDataProvider.matches().first().let { it.copy(predictedResult = engine.predict(it)) }

        val first = ResultSimulator.simulate(match)
        val second = ResultSimulator.simulate(match)

        assertEquals("same match must simulate the same score", first, second)
        assertTrue(first.first in 0..9)
        assertTrue(first.second in 0..9)
    }

    @Test
    fun `every fixture gets a scoreline`() {
        val simulated = MockDataProvider.matches()
            .map { it.copy(predictedResult = engine.predict(it)) }
            .map { ResultSimulator.simulate(it) }
        assertTrue(simulated.isNotEmpty())
        assertTrue(simulated.all { it.first >= 0 && it.second >= 0 })
    }
}
