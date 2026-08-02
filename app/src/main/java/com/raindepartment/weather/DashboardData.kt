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
    val sunrise: String = "",
    val sunset: String = "",
    val peakWindMph: Int = 0,
    val peakWindDirection: String = "",
    val peakWindTime: String = "",
    val dryWindow: String = "",
    val hourly: List<HourlyForecast> = emptyList(),
)

internal data class ChartPoint(
    val label: String,
    val value: Float,
)

internal data class DashboardForecast(
    val location: String,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val rainStartsIn: String,
    val currentFahrenheit: Int,
    val feelsLikeFahrenheit: Int,
    val highFahrenheit: Int,
    val lowFahrenheit: Int,
    val conditionLabel: String,
    val precipitationChance: Int,
    val currentPrecipitationInches: Double,
    val expectedRainInches: Double,
    val peakWindMph: Int,
    val peakWindDirection: String,
    val peakWindTime: String,
    val hourly: List<HourlyForecast>,
    val precipitation24h: List<ChartPoint>,
    val windByHour: List<ChartPoint>,
    val daily: List<DailyForecast>,
    val rainfallOutlook: List<ChartPoint>,
    val sunrise: String,
    val sunset: String,
    val dryWindow: String,
)

internal data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

internal data class WeatherSnapshot(
    val location: WeatherLocation,
    val timezone: String,
    val fetchedAtEpochMillis: Long,
    val forecast: DashboardForecast,
)

private val compassDirectionRotationDegrees = mapOf(
    "N" to 0f,
    "NNE" to 22.5f,
    "NE" to 45f,
    "ENE" to 67.5f,
    "E" to 90f,
    "ESE" to 112.5f,
    "SE" to 135f,
    "SSE" to 157.5f,
    "S" to 180f,
    "SSW" to 202.5f,
    "SW" to 225f,
    "WSW" to 247.5f,
    "W" to 270f,
    "WNW" to 292.5f,
    "NW" to 315f,
    "NNW" to 337.5f,
)

internal fun windDirectionRotationDegrees(direction: String): Float =
    compassDirectionRotationDegrees[direction.trim().uppercase(Locale.ROOT)] ?: 0f

internal fun DashboardForecast.currentWeather(): CurrentWeather = CurrentWeather(
    location = location,
    condition = condition,
    conditionLabel = conditionLabel,
    isDay = isDay,
    currentFahrenheit = currentFahrenheit,
    highFahrenheit = highFahrenheit,
    lowFahrenheit = lowFahrenheit,
    precipitationChance = precipitationChance,
)

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

internal fun HourlyForecast.temperature(unitSystem: UnitSystem): String {
    val value = when (unitSystem) {
        UnitSystem.IMPERIAL -> temperatureFahrenheit.toDouble()
        UnitSystem.METRIC -> (temperatureFahrenheit - 32) * 5.0 / 9.0
    }
    return value.roundToInt().toString()
}

internal fun HourlyForecast.windSpeed(unitSystem: UnitSystem): String {
    val value = when (unitSystem) {
        UnitSystem.IMPERIAL -> windMph
        UnitSystem.METRIC -> (windMph * 1.60934).roundToInt()
    }
    return value.toString()
}
