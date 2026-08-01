package com.raindepartment.weather

import android.content.Context
import kotlin.math.roundToInt
import java.util.Locale

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
        location = "Austin, Texas",
        condition = WeatherCondition.PARTLY_CLOUDY,
        conditionLabel = "Partly cloudy",
        isDay = true,
        currentCelsius = 28.9,
        highCelsius = 31.7,
        lowCelsius = 22.8,
        precipitationChance = 80,
        uvIndex = 7,
    )
}

internal object WeatherPreferences {
    private const val PREFERENCES_NAME = "weather_preferences"
    private const val UNIT_SYSTEM_KEY = "unit_system"
    private const val BACKPLATE_INDEX_KEY = "backplate_index"
    private const val DEFAULT_BACKPLATE_INDEX = 4

    fun unitSystem(context: Context): UnitSystem {
        val stored = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(UNIT_SYSTEM_KEY, null)

        return UnitSystem.entries.firstOrNull { it.name == stored } ?: defaultUnitSystem()
    }

    fun setUnitSystem(context: Context, unitSystem: UnitSystem) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(UNIT_SYSTEM_KEY, unitSystem.name)
            .apply()
    }

    fun backplateIndex(context: Context): Int = context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getInt(BACKPLATE_INDEX_KEY, DEFAULT_BACKPLATE_INDEX)
        .coerceIn(BackplateChoices.indices)

    fun setBackplateIndex(context: Context, index: Int) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(BACKPLATE_INDEX_KEY, index.coerceIn(BackplateChoices.indices))
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

internal fun defaultUnitSystem(locale: Locale = Locale.getDefault()): UnitSystem {
    val country = locale.country.uppercase(Locale.ROOT)
    return if (country in setOf("US", "LR", "MM")) {
        UnitSystem.IMPERIAL
    } else {
        UnitSystem.METRIC
    }
}
