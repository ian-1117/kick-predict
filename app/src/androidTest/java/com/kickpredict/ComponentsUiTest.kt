package com.kickpredict

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kickpredict.domain.model.ConfidenceTier
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.presentation.components.ConfidenceBadge
import com.kickpredict.presentation.components.ProbabilityGauges
import com.kickpredict.presentation.theme.KickPredictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the key result components. Run on a device/emulator with:
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ComponentsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confidenceBadge_showsScoreAndLabel() {
        composeRule.setContent {
            KickPredictTheme {
                ConfidenceBadge(confidenceScore = 82, tier = ConfidenceTier.VERY_HIGH)
            }
        }
        composeRule.onNodeWithText("AI 예상 적중 확률").assertIsDisplayed()
        composeRule.onNodeWithText("82%").assertIsDisplayed()
    }

    @Test
    fun probabilityGauges_showEachOutcomePercent() {
        val result = PredictionResult(
            homeWinPercent = 45,
            drawPercent = 30,
            awayWinPercent = 25,
            confidenceScore = 80,
            rationale = emptyList(),
        )
        composeRule.setContent {
            KickPredictTheme {
                ProbabilityGauges(result = result, homeName = "GUN", awayName = "SEA")
            }
        }
        composeRule.onNodeWithText("GUN").assertIsDisplayed()
        composeRule.onNodeWithText("45%").assertIsDisplayed()
        composeRule.onNodeWithText("Draw").assertIsDisplayed()
        composeRule.onNodeWithText("25%").assertIsDisplayed()
    }
}
