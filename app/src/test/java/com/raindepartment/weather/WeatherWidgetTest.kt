package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherWidgetTest {
    @Test
    fun widgetWeatherUsesCurrentConditionWhileKeepingTodayHighAndLow() {
        val weather = DashboardForecastTestData.forecast.widgetWeather()

        assertEquals(WeatherCondition.PARTLY_CLOUDY, weather.condition)
        assertEquals("Partly cloudy", weather.conditionLabel)
        assertEquals(84, weather.currentFahrenheit)
        assertEquals(89, weather.highFahrenheit)
        assertEquals(73, weather.lowFahrenheit)
        assertEquals(80, weather.precipitationChance)
        assertEquals(true, weather.isDay)
    }

    @Test
    fun widgetWeatherFallsBackToCurrentForecastWhenTodayIsUnavailable() {
        val forecast = DashboardForecastTestData.forecast.copy(daily = emptyList())
        val weather = forecast.widgetWeather()

        assertEquals(forecast.condition, weather.condition)
        assertEquals(forecast.conditionLabel, weather.conditionLabel)
        assertEquals(forecast.currentFahrenheit, weather.currentFahrenheit)
        assertEquals(forecast.highFahrenheit, weather.highFahrenheit)
        assertEquals(forecast.lowFahrenheit, weather.lowFahrenheit)
        assertEquals(forecast.precipitationChance, weather.precipitationChance)
    }

    @Test
    fun locationLabelSplitsCityAndRegion() {
        assertEquals("Sherbrooke\nQuebec", widgetLocationLabel("Sherbrooke, Quebec"))
    }

    @Test
    fun locationLabelTruncatesLongLines() {
        assertEquals("San Franc…\nBritish C…", widgetLocationLabel("San Francisco, British Columbia"))
    }
}
