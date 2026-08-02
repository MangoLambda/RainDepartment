package com.raindepartment.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val composeRule = createComposeRule()

    @Before
    fun setUp() {
        WeatherPreferences.clearSelectedLocation(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        composeRule.setContent {
            RainDepartmentTheme {
                RainDepartmentApp(
                    repository = FakeWeatherRepository.create(),
                    requestLocationPermission = false,
                    checkForUpdates = false,
                    updateWidget = false,
                )
            }
        }
    }

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

    @Test
    fun cityPickerCanSelectAnotherCity() {
        composeRule.onNodeWithContentDescription("Choose city").performClick()
        composeRule.onNodeWithText("Choose a city").assertIsDisplayed()
        composeRule.onNodeWithText("Nearby").assertIsDisplayed()
        composeRule.onNodeWithText("Recent").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("Denver")
        composeRule.onNodeWithText("Denver, Colorado").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Denver, Colorado").assertIsDisplayed()
    }

    private object FakeWeatherRepository {
        fun create(): WeatherRepository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(snapshot().forecast, "America/Chicago")
            },
            cache = object : WeatherCache {
                private val value = snapshot()

                override fun read(): WeatherSnapshot = value
                override fun write(snapshot: WeatherSnapshot) = Unit
            },
            locationProvider = object : WeatherLocationProvider {
                override suspend fun currentOrNull(): WeatherLocation = AustinLocation
            },
            clock = { System.currentTimeMillis() },
        )

        private fun snapshot() = WeatherSnapshot(
            location = AustinLocation,
            timezone = "America/Chicago",
            fetchedAtEpochMillis = System.currentTimeMillis(),
            forecast = DashboardForecast(
                location = "Austin, Texas",
                condition = WeatherCondition.PARTLY_CLOUDY,
                isDay = true,
                rainStartsIn = "1h",
                currentFahrenheit = 84,
                feelsLikeFahrenheit = 87,
                highFahrenheit = 89,
                lowFahrenheit = 73,
                conditionLabel = "Partly cloudy",
                precipitationChance = 80,
                currentPrecipitationInches = 0.0,
                expectedRainInches = 0.68,
                peakWindMph = 15,
                peakWindDirection = "ESE",
                peakWindTime = "2 PM",
                hourly = listOf(HourlyForecast("Now", 30, 0.0, 84, 8, "N", "N")),
                precipitation24h = listOf(ChartPoint("Now", 0.0f)),
                windByHour = listOf(ChartPoint("Now", 8.0f)),
                daily = listOf(DailyForecast("Today", WeatherCondition.RAIN, "Rain", 80, 0.68, 89, 73)),
                rainfallOutlook = listOf(ChartPoint("Today", 0.68f)),
                sunrise = "6:32 AM",
                sunset = "8:32 PM",
                dryWindow = "5 PM – 8 PM",
            ),
        )
    }
}
