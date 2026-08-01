package com.raindepartment.weather

import android.content.Context
import kotlin.math.roundToInt
import java.util.Locale

internal enum class UnitSystem {
    METRIC,
    IMPERIAL,
}

internal data class CurrentWeather(
    val location: String,
    val condition: WeatherCondition,
    val conditionLabel: String,
    val isDay: Boolean,
    val currentFahrenheit: Int,
    val highFahrenheit: Int,
    val lowFahrenheit: Int,
    val precipitationChance: Int,
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

internal object WeatherPreferences {
    private const val PREFERENCES_NAME = "weather_preferences"
    private const val UNIT_SYSTEM_KEY = "unit_system"
    private const val BACKPLATE_INDEX_KEY = "backplate_index"
    private const val AUTOMATIC_BACKPLATE_KEY = "automatic_backplate"
    private const val DEFAULT_BACKPLATE_INDEX = 4
    internal const val AUTOMATIC_BACKPLATE_INDEX = -1

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

    fun isAutomaticBackplate(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.contains(AUTOMATIC_BACKPLATE_KEY)) {
            return preferences.getBoolean(AUTOMATIC_BACKPLATE_KEY, true)
        }

        // Preserve a manual choice made by older builds. Fresh installs default to the
        // current live condition instead of the former static partly-cloudy image.
        return !preferences.contains(BACKPLATE_INDEX_KEY)
    }

    fun backplateIndex(context: Context): Int {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (isAutomaticBackplate(context)) return AUTOMATIC_BACKPLATE_INDEX
        return preferences
            .getInt(BACKPLATE_INDEX_KEY, DEFAULT_BACKPLATE_INDEX)
            .coerceIn(BackplateChoices.indices)
    }

    fun setAutomaticBackplate(context: Context, automatic: Boolean) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AUTOMATIC_BACKPLATE_KEY, automatic)
            .apply()
    }

    fun setBackplateIndex(context: Context, index: Int) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                BACKPLATE_INDEX_KEY,
                index.coerceIn(AUTOMATIC_BACKPLATE_INDEX, BackplateChoices.lastIndex),
            )
            .putBoolean(AUTOMATIC_BACKPLATE_KEY, index == AUTOMATIC_BACKPLATE_INDEX)
            .apply()
    }
}

internal fun CurrentWeather.temperature(unitSystem: UnitSystem): String {
    val value = when (unitSystem) {
        UnitSystem.METRIC -> (currentFahrenheit - 32) * 5.0 / 9.0
        UnitSystem.IMPERIAL -> currentFahrenheit.toDouble()
    }
    return "${value.roundToInt()}°"
}

internal fun CurrentWeather.highLow(unitSystem: UnitSystem): String {
    val high = when (unitSystem) {
        UnitSystem.METRIC -> (highFahrenheit - 32) * 5.0 / 9.0
        UnitSystem.IMPERIAL -> highFahrenheit.toDouble()
    }.roundToInt()
    val low = when (unitSystem) {
        UnitSystem.METRIC -> (lowFahrenheit - 32) * 5.0 / 9.0
        UnitSystem.IMPERIAL -> lowFahrenheit.toDouble()
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
