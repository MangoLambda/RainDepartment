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
