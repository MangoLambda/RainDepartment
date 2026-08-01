package com.raindepartment.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun briefingDashboardShowsReferenceContent() {
        composeRule.onNodeWithText("Rain starts in").assertIsDisplayed()
        composeRule.onNodeWithText("Hourly Precipitation, Temperature & Wind").assertIsDisplayed()
        composeRule.onNodeWithText("80%").assertIsDisplayed()
        composeRule.onNodeWithText("Briefing").assertIsDisplayed()
    }

    @Test
    fun settingsKeepsUnitAndWidgetControls() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Temperature units").assertIsDisplayed()
        composeRule.onNodeWithText("Widget appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Imperial").performClick()
        composeRule.onNodeWithText("°F · mph").assertIsDisplayed()

        composeRule.onNodeWithText("Briefing").performClick()
        composeRule.onNodeWithText("Rain starts in").assertIsDisplayed()
    }
}
