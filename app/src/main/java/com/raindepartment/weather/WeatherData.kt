package com.raindepartment.weather

import android.content.Context
import kotlin.math.roundToInt

internal enum class UnitSystem {
    METRIC,
    IMPERIAL,
}

internal data class DummyWeather(
    val location: String,
    val condition: WeatherCondition,
    val conditionLabel: String,
    val isDay: Boolean,
    val currentCelsius: Double,
    val highCelsius: Double,
    val lowCelsius: Double,
    val precipitationChance: Int,
    val uvIndex: Int,
)

internal enum class WeatherCondition {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    ATMOSPHERIC_HAZE,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    THUNDERSTORM,
    SNOW,
    HEAVY_SNOW,
    WINTRY_MIX,
    SEVERE_WEATHER,
}

internal object DummyWeatherData {
    val current = DummyWeather(
        location = "Austin",
        condition = WeatherCondition.PARTLY_CLOUDY,
        conditionLabel = "Partly cloudy",
        isDay = true,
        currentCelsius = 28.9,
        highCelsius = 31.1,
        lowCelsius = 20.0,
        precipitationChance = 20,
        uvIndex = 7,
    )
}

internal object WeatherPreferences {
    private const val PREFERENCES_NAME = "weather_preferences"
    private const val UNIT_SYSTEM_KEY = "unit_system"

    fun unitSystem(context: Context): UnitSystem {
        val stored = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(UNIT_SYSTEM_KEY, UnitSystem.METRIC.name)

        return UnitSystem.entries.firstOrNull { it.name == stored } ?: UnitSystem.METRIC
    }

    fun setUnitSystem(context: Context, unitSystem: UnitSystem) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(UNIT_SYSTEM_KEY, unitSystem.name)
            .apply()
    }
}

internal fun DummyWeather.temperature(unitSystem: UnitSystem): String {
    val value = when (unitSystem) {
        UnitSystem.METRIC -> currentCelsius
        UnitSystem.IMPERIAL -> currentCelsius * 9 / 5 + 32
    }
    return "${value.roundToInt()}°"
}

internal fun DummyWeather.highLow(unitSystem: UnitSystem): String {
    val high = when (unitSystem) {
        UnitSystem.METRIC -> highCelsius
        UnitSystem.IMPERIAL -> highCelsius * 9 / 5 + 32
    }.roundToInt()
    val low = when (unitSystem) {
        UnitSystem.METRIC -> lowCelsius
        UnitSystem.IMPERIAL -> lowCelsius * 9 / 5 + 32
    }.roundToInt()
    return "H:$high°  L:$low°"
}

internal fun UnitSystem.temperatureUnitLabel(): String = when (this) {
    UnitSystem.METRIC -> "°C"
    UnitSystem.IMPERIAL -> "°F"
}
