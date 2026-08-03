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
    val condition: WeatherCondition = WeatherCondition.OVERCAST,
    val conditionLabel: String = "Overcast",
    val timeEpochMillis: Long? = null,
    val rainfallAmountAvailable: Boolean = true,
    val precipitationChanceAvailable: Boolean = true,
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
    val rainfallAmountAvailable: Boolean = true,
    val precipitationChanceAvailable: Boolean = true,
)

internal data class ChartPoint(
    val label: String,
    val value: Float,
)

internal enum class RainStartSource {
    NONE,
    MODEL,
    ECCC_FORECAST,
    ECCC_RADAR,
}

internal enum class ForecastSource {
    ECCC,
    GEM,
}

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
    val rainStartsAtEpochMillis: Long? = null,
    val rainStartSource: RainStartSource = RainStartSource.NONE,
    val rainStartConfidenceMeaningful: Boolean = false,
    val expectedRainAmountAvailable: Boolean = true,
    val source: ForecastSource = ForecastSource.GEM,
    val precipitationChanceAvailable: Boolean = true,
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
        UnitSystem.IMPERIAL -> if (valueInches > 0.0 && valueInches < 0.005) {
            "<0.01 in"
        } else {
            String.format(Locale.US, "%.2f in", valueInches)
        }
        UnitSystem.METRIC -> {
            val millimeters = valueInches * 25.4
            if (millimeters > 0.0 && millimeters < 0.05) {
                "<0.1 mm"
            } else {
                String.format(Locale.US, "%.1f mm", millimeters)
            }
        }
    }
}

internal fun DashboardForecast.precipitationOrUnavailable(
    valueInches: Double,
    unitSystem: UnitSystem,
    available: Boolean,
): String = if (available) {
    precipitation(valueInches, unitSystem)
} else {
    "—"
}

internal fun chanceOrUnavailable(value: Int, available: Boolean): String = if (available) {
    "$value%"
} else {
    "—"
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

internal const val RADAR_RAIN_WINDOW_MINUTES = 3 * 60L
internal const val WIDGET_RAIN_START_BOTTOM_WINDOW_MINUTES = 45L
internal const val MINIMUM_RAIN_START_AMOUNT_MILLIMETERS = 0.1
internal const val MINIMUM_RAIN_START_AMOUNT_INCHES =
    MINIMUM_RAIN_START_AMOUNT_MILLIMETERS / 25.4
internal const val RADAR_MEANINGFUL_RATE_MM_PER_HOUR = 0.1
internal const val RADAR_MODERATE_RATE_MM_PER_HOUR = 2.5
internal const val RADAR_HEAVY_RATE_MM_PER_HOUR = 7.6

internal fun WeatherCondition.isRainBearing(): Boolean = this in setOf(
    WeatherCondition.DRIZZLE,
    WeatherCondition.RAIN,
    WeatherCondition.HEAVY_RAIN,
    WeatherCondition.THUNDERSTORM,
    WeatherCondition.SEVERE_WEATHER,
    WeatherCondition.WINTRY_MIX,
)

internal fun hasMinimumRainStartAmount(rainfallInches: Double): Boolean =
    rainfallInches.isFinite() && rainfallInches >= MINIMUM_RAIN_START_AMOUNT_INCHES

internal fun radarConditionForRainRate(rateMillimetersPerHour: Double): Pair<WeatherCondition, String>? {
    val rate = rateMillimetersPerHour.takeIf { it.isFinite() } ?: return null
    if (rate < RADAR_MEANINGFUL_RATE_MM_PER_HOUR) return null

    return when {
        rate >= RADAR_HEAVY_RATE_MM_PER_HOUR -> WeatherCondition.HEAVY_RAIN to "Heavy rain"
        rate >= RADAR_MODERATE_RATE_MM_PER_HOUR -> WeatherCondition.RAIN to "Rain"
        else -> WeatherCondition.DRIZZLE to "Drizzle"
    }
}

internal fun DashboardForecast.rainStartMinutesFromNow(nowEpochMillis: Long): Long? {
    val startsAt = rainStartsAtEpochMillis ?: return null
    val remainingMillis = startsAt - nowEpochMillis
    return if (remainingMillis <= 0L) {
        0L
    } else {
        (remainingMillis + 59_999L) / 60_000L
    }
}

internal fun DashboardForecast.rainStartCountdownText(nowEpochMillis: Long): String {
    val minutes = rainStartMinutesFromNow(nowEpochMillis)
    return when {
        minutes == 0L -> if (isCurrentlyRaining(nowEpochMillis)) "Now" else "Soon"
        minutes != null -> formatRainStartCountdown(minutes)
        isCurrentlyRaining(nowEpochMillis) -> "Now"
        else -> rainStartsIn.takeUnless { it.equals("Now", ignoreCase = true) } ?: "Soon"
    }
}

internal fun DashboardForecast.isCurrentlyRaining(
    nowEpochMillis: Long = System.currentTimeMillis(),
): Boolean = condition.isRainBearing() &&
    (rainStartsAtEpochMillis == null || rainStartsAtEpochMillis <= nowEpochMillis)

internal fun DashboardForecast.widgetRainStartText(nowEpochMillis: Long): String? {
    if (isCurrentlyRaining(nowEpochMillis)) return "Rain is falling now"
    val minutes = rainStartMinutesFromNow(nowEpochMillis) ?: return null
    if (minutes >= WIDGET_RAIN_START_BOTTOM_WINDOW_MINUTES) return null
    return if (minutes == 0L) {
        "Rain expected soon"
    } else {
        "Rain will start in ${rainStartCountdownText(nowEpochMillis)}"
    }
}

internal fun formatRainStartCountdown(totalMinutes: Long): String {
    val minutes = totalMinutes.coerceAtLeast(0L)
    if (minutes == 0L) return "now"

    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    val hoursLabel = when (hours) {
        0L -> null
        1L -> "1 hour"
        else -> "$hours hours"
    }
    val minutesLabel = when (remainingMinutes) {
        0L -> null
        1L -> "1 minute"
        else -> "$remainingMinutes minutes"
    }
    return listOfNotNull(hoursLabel, minutesLabel).joinToString(", ")
}
