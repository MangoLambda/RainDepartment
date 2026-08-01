package com.raindepartment.weather

import java.util.Locale
import kotlin.math.roundToInt

internal enum class DashboardTab(val label: String) {
    BRIEFING("Briefing"),
    TIMELINE("Timeline"),
    RADAR("Radar"),
    OUTLOOK("Outlook"),
    SETTINGS("Settings"),
}

internal enum class ForecastRange(val label: String) {
    TODAY("Today"),
    SEVEN_DAYS("7 Days"),
    MONTH("Month"),
}

internal data class HourlyForecast(
    val time: String,
    val precipitationChance: Int,
    val rainfallInches: Double,
    val temperatureFahrenheit: Int,
    val windMph: Int,
    val windDirection: String,
    val windDirectionLabel: String,
)

internal data class DailyForecast(
    val day: String,
    val condition: WeatherCondition,
    val conditionLabel: String,
    val precipitationChance: Int,
    val rainfallInches: Double,
    val highFahrenheit: Int,
    val lowFahrenheit: Int,
)

internal data class ChartPoint(
    val label: String,
    val value: Float,
)

internal data class DashboardForecast(
    val location: String,
    val rainStartsIn: String,
    val currentFahrenheit: Int,
    val feelsLikeFahrenheit: Int,
    val highFahrenheit: Int,
    val lowFahrenheit: Int,
    val conditionLabel: String,
    val precipitationChance: Int,
    val expectedRainInches: Double,
    val peakWindMph: Int,
    val peakWindDirection: String,
    val hourly: List<HourlyForecast>,
    val precipitation24h: List<ChartPoint>,
    val windByHour: List<ChartPoint>,
    val daily: List<DailyForecast>,
    val rainfallOutlook: List<ChartPoint>,
    val sunrise: String,
    val sunset: String,
    val dryWindow: String,
)

internal object MockDashboardData {
    val current = DashboardForecast(
        location = "Austin, Texas",
        rainStartsIn = "1h 20m",
        currentFahrenheit = 84,
        feelsLikeFahrenheit = 87,
        highFahrenheit = 89,
        lowFahrenheit = 73,
        conditionLabel = "Partly Cloudy",
        precipitationChance = 80,
        expectedRainInches = 0.68,
        peakWindMph = 15,
        peakWindDirection = "ESE",
        hourly = listOf(
            HourlyForecast("Now", 30, 0.00, 84, 8, "N", "N"),
            HourlyForecast("11 AM", 50, 0.02, 85, 9, "NE", "NE"),
            HourlyForecast("12 PM", 70, 0.08, 86, 11, "ENE", "ENE"),
            HourlyForecast("1 PM", 80, 0.18, 87, 13, "E", "E"),
            HourlyForecast("2 PM", 90, 0.24, 87, 15, "ESE", "ESE"),
            HourlyForecast("3 PM", 70, 0.12, 86, 14, "SE", "SE"),
            HourlyForecast("4 PM", 40, 0.04, 85, 12, "SSE", "SSE"),
            HourlyForecast("5 PM", 20, 0.01, 84, 10, "S", "S"),
        ),
        precipitation24h = listOf(
            ChartPoint("Now", 0.08f),
            ChartPoint("1 PM", 0.28f),
            ChartPoint("4 PM", 0.74f),
            ChartPoint("7 PM", 0.66f),
            ChartPoint("10 PM", 0.48f),
            ChartPoint("1 AM", 0.22f),
            ChartPoint("4 AM", 0.08f),
            ChartPoint("7 AM", 0.05f),
            ChartPoint("10 AM", 0.10f),
        ),
        windByHour = listOf(
            ChartPoint("Now", 7f),
            ChartPoint("1 PM", 12f),
            ChartPoint("4 PM", 16f),
            ChartPoint("7 PM", 11f),
            ChartPoint("10 PM", 10f),
            ChartPoint("1 AM", 9f),
            ChartPoint("4 AM", 7f),
            ChartPoint("7 AM", 5f),
            ChartPoint("10 AM", 3f),
        ),
        daily = listOf(
            DailyForecast("Today", WeatherCondition.RAIN, "Rain", 80, 0.68, 89, 73),
            DailyForecast("Tue", WeatherCondition.RAIN, "Showers", 70, 0.32, 87, 72),
            DailyForecast("Wed", WeatherCondition.DRIZZLE, "Isolated\nScattered", 40, 0.10, 85, 70),
            DailyForecast("Thu", WeatherCondition.MOSTLY_CLEAR, "Sunshine\nScattered", 10, 0.25, 84, 69),
            DailyForecast("Fri", WeatherCondition.PARTLY_CLOUDY, "Partly Cloudy", 20, 0.00, 86, 71),
            DailyForecast("Sat", WeatherCondition.RAIN, "Showers", 40, 0.45, 82, 67),
            DailyForecast("Sun", WeatherCondition.PARTLY_CLOUDY, "Partly Cloudy", 20, 0.00, 80, 66),
        ),
        rainfallOutlook = listOf(
            ChartPoint("Today", 0.68f),
            ChartPoint("Tue", 0.32f),
            ChartPoint("Wed", 0.10f),
            ChartPoint("Thu", 0.25f),
            ChartPoint("Fri", 0.00f),
            ChartPoint("Sat", 0.45f),
            ChartPoint("Sun", 0.00f),
        ),
        sunrise = "6:32 AM",
        sunset = "8:32 PM",
        dryWindow = "5 PM – 8 PM",
    )
}

internal fun DashboardForecast.temperature(valueFahrenheit: Int, unitSystem: UnitSystem): String {
    val value = when (unitSystem) {
        UnitSystem.IMPERIAL -> valueFahrenheit.toDouble()
        UnitSystem.METRIC -> (valueFahrenheit - 32) * 5.0 / 9.0
    }
    return "${value.roundToInt()}°"
}

internal fun DashboardForecast.temperatureWithUnit(
    valueFahrenheit: Int,
    unitSystem: UnitSystem,
): String = "${temperature(valueFahrenheit, unitSystem)}${unitSystem.temperatureUnitLabel()}"

internal fun DashboardForecast.precipitation(valueInches: Double, unitSystem: UnitSystem): String {
    return when (unitSystem) {
        UnitSystem.IMPERIAL -> String.format(Locale.US, "%.2f in", valueInches)
        UnitSystem.METRIC -> String.format(Locale.US, "%.1f mm", valueInches * 25.4)
    }
}

internal fun DashboardForecast.windSpeed(valueMph: Int, unitSystem: UnitSystem): String {
    val value = when (unitSystem) {
        UnitSystem.IMPERIAL -> valueMph.toDouble()
        UnitSystem.METRIC -> valueMph * 1.60934
    }.roundToInt()
    val unit = if (unitSystem == UnitSystem.IMPERIAL) "mph" else "km/h"
    return "$value $unit"
}
