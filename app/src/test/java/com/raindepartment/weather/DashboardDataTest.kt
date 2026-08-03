package com.raindepartment.weather

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardDataTest {
    @Test
    fun localeDefaultsUseImperialForUsDevices() {
        assertEquals(UnitSystem.IMPERIAL, defaultUnitSystem(Locale.US))
        assertEquals(UnitSystem.METRIC, defaultUnitSystem(Locale.UK))
    }

    @Test
    fun dashboardTemperatureConvertsBetweenSystems() {
        val forecast = testForecast()

        assertEquals("84°", forecast.temperature(forecast.currentFahrenheit, UnitSystem.IMPERIAL))
        assertEquals("29°", forecast.temperature(forecast.currentFahrenheit, UnitSystem.METRIC))
        assertEquals("0.68 in", forecast.precipitation(0.68, UnitSystem.IMPERIAL))
        assertEquals("17.3 mm", forecast.precipitation(0.68, UnitSystem.METRIC))
    }

    @Test
    fun tinyPositiveRainfallIsShownAsTraceInsteadOfZero() {
        val forecast = testForecast()

        assertEquals("<0.1 mm", forecast.precipitation(0.001, UnitSystem.METRIC))
        assertEquals("<0.01 in", forecast.precipitation(0.001, UnitSystem.IMPERIAL))
        assertEquals("0.0 mm", forecast.precipitation(0.0, UnitSystem.METRIC))
    }

    @Test
    fun currentWeatherCarriesLiveConditionAndWidgetValues() {
        val current = testForecast().currentWeather()

        assertEquals(WeatherCondition.PARTLY_CLOUDY, current.condition)
        assertEquals("Austin, Texas", current.location)
        assertEquals("84°", current.temperature(UnitSystem.IMPERIAL))
        assertEquals("H:89°  L:73°", current.highLow(UnitSystem.IMPERIAL))
    }

    @Test
    fun hourlyConversionsRemainUnitAware() {
        val hourly = HourlyForecast("Now", 20, 0.1, 84, 10, "E", "E")

        assertEquals("84", hourly.temperature(UnitSystem.IMPERIAL))
        assertEquals("29", hourly.temperature(UnitSystem.METRIC))
        assertEquals("16", hourly.windSpeed(UnitSystem.METRIC))
        assertTrue(hourly.rainfallInches > 0.0)
    }

    @Test
    fun windDirectionRotationFollowsCompassLabel() {
        assertEquals(0f, windDirectionRotationDegrees("N"), 0f)
        assertEquals(90f, windDirectionRotationDegrees("e"), 0f)
        assertEquals(112.5f, windDirectionRotationDegrees(" ESE "), 0f)
        assertEquals(180f, windDirectionRotationDegrees("S"), 0f)
        assertEquals(270f, windDirectionRotationDegrees("W"), 0f)
    }

    @Test
    fun radarRainStartTextUsesHoursAndMinutesInsideThreeHourWindow() {
        val now = 1_000_000L
        val forecast = testForecast().copy(
            rainStartsAtEpochMillis = now + 65 * 60_000L,
            rainStartSource = RainStartSource.ECCC_RADAR,
        )

        assertEquals("rain starts, in 1 hour, 5 minutes", forecast.radarRainStartText(now))
        assertEquals(null, testForecast().radarRainStartText(now))
        assertEquals("1 hour, 5 minutes", formatRainStartCountdown(65))
    }

    @Test
    fun rainStartCountdownTextUsesTheTimestampInsteadOfAStaleFormattedValue() {
        val now = 1_000_000L
        val forecast = testForecast().copy(
            rainStartsIn = "1h",
            rainStartsAtEpochMillis = now + 32 * 60_000L,
            rainStartSource = RainStartSource.ECCC_RADAR,
        )

        assertEquals("32 minutes", forecast.rainStartCountdownText(now))
    }

    @Test
    fun radarRainStartTextSaysWhenRainIsAlreadyFalling() {
        val now = 1_000_000L
        val forecast = testForecast().copy(
            rainStartsAtEpochMillis = now - 1L,
            rainStartSource = RainStartSource.ECCC_RADAR,
        )

        assertEquals("rain is falling now", forecast.radarRainStartText(now))
    }

    @Test
    fun radarRateMapsToCurrentConditionIntensity() {
        assertEquals(
            WeatherCondition.DRIZZLE to "Drizzle",
            radarConditionForRainRate(0.1),
        )
        assertEquals(
            WeatherCondition.RAIN to "Rain",
            radarConditionForRainRate(2.5),
        )
        assertEquals(
            WeatherCondition.HEAVY_RAIN to "Heavy rain",
            radarConditionForRainRate(7.6),
        )
    }

    private fun testForecast() = DashboardForecast(
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
    )
}
