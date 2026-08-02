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
    private const val SELECTED_LOCATION_LATITUDE_KEY = "selected_location_latitude"
    private const val SELECTED_LOCATION_LONGITUDE_KEY = "selected_location_longitude"
    private const val SELECTED_LOCATION_LABEL_KEY = "selected_location_label"

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

    fun selectedLocation(context: Context): WeatherLocation? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.contains(SELECTED_LOCATION_LATITUDE_KEY) ||
            !preferences.contains(SELECTED_LOCATION_LONGITUDE_KEY) ||
            !preferences.contains(SELECTED_LOCATION_LABEL_KEY)
        ) {
            return null
        }

        return runCatching {
            WeatherLocation(
                latitude = preferences.getString(SELECTED_LOCATION_LATITUDE_KEY, null)!!.toDouble(),
                longitude = preferences.getString(SELECTED_LOCATION_LONGITUDE_KEY, null)!!.toDouble(),
                label = preferences.getString(SELECTED_LOCATION_LABEL_KEY, null)!!,
            )
        }.getOrNull()
    }

    fun setSelectedLocation(context: Context, location: WeatherLocation) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SELECTED_LOCATION_LATITUDE_KEY, location.latitude.toString())
            .putString(SELECTED_LOCATION_LONGITUDE_KEY, location.longitude.toString())
            .putString(SELECTED_LOCATION_LABEL_KEY, location.label)
            .apply()
    }

    fun clearSelectedLocation(context: Context) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(SELECTED_LOCATION_LATITUDE_KEY)
            .remove(SELECTED_LOCATION_LONGITUDE_KEY)
            .remove(SELECTED_LOCATION_LABEL_KEY)
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
