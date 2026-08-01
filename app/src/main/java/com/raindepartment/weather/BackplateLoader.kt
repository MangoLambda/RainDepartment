package com.raindepartment.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.glance.ImageProvider

internal data class BackplateChoice(
    val condition: WeatherCondition,
    val isDay: Boolean,
    val label: String,
)

internal val BackplateChoices = listOf(
    BackplateChoice(WeatherCondition.CLEAR, true, "Clear — Day"),
    BackplateChoice(WeatherCondition.CLEAR, false, "Clear — Night"),
    BackplateChoice(WeatherCondition.MOSTLY_CLEAR, true, "Mostly clear — Day"),
    BackplateChoice(WeatherCondition.MOSTLY_CLEAR, false, "Mostly clear — Night"),
    BackplateChoice(WeatherCondition.PARTLY_CLOUDY, true, "Partly cloudy — Day"),
    BackplateChoice(WeatherCondition.PARTLY_CLOUDY, false, "Partly cloudy — Night"),
    BackplateChoice(WeatherCondition.OVERCAST, true, "Overcast — Day"),
    BackplateChoice(WeatherCondition.OVERCAST, false, "Overcast — Night"),
    BackplateChoice(WeatherCondition.FOG, true, "Fog — Day"),
    BackplateChoice(WeatherCondition.FOG, false, "Fog — Night"),
    BackplateChoice(WeatherCondition.ATMOSPHERIC_HAZE, true, "Atmospheric haze — Day"),
    BackplateChoice(WeatherCondition.ATMOSPHERIC_HAZE, false, "Atmospheric haze — Night"),
    BackplateChoice(WeatherCondition.DRIZZLE, true, "Drizzle — Day"),
    BackplateChoice(WeatherCondition.DRIZZLE, false, "Drizzle — Night"),
    BackplateChoice(WeatherCondition.RAIN, true, "Rain — Day"),
    BackplateChoice(WeatherCondition.RAIN, false, "Rain — Night"),
    BackplateChoice(WeatherCondition.HEAVY_RAIN, true, "Heavy rain — Day"),
    BackplateChoice(WeatherCondition.HEAVY_RAIN, false, "Heavy rain — Night"),
    BackplateChoice(WeatherCondition.THUNDERSTORM, true, "Thunderstorm — Day"),
    BackplateChoice(WeatherCondition.THUNDERSTORM, false, "Thunderstorm — Night"),
    BackplateChoice(WeatherCondition.SNOW, true, "Snow — Day"),
    BackplateChoice(WeatherCondition.SNOW, false, "Snow — Night"),
    BackplateChoice(WeatherCondition.HEAVY_SNOW, true, "Heavy snow — Day"),
    BackplateChoice(WeatherCondition.HEAVY_SNOW, false, "Heavy snow — Night"),
    BackplateChoice(WeatherCondition.WINTRY_MIX, true, "Wintry mix — Day"),
    BackplateChoice(WeatherCondition.WINTRY_MIX, false, "Wintry mix — Night"),
    BackplateChoice(WeatherCondition.SEVERE_WEATHER, true, "Severe weather — Day"),
    BackplateChoice(WeatherCondition.SEVERE_WEATHER, false, "Severe weather — Night"),
)

private object BackplateCatalog {
    @DrawableRes
    fun resourceFor(condition: WeatherCondition, isDay: Boolean): Int = when (condition) {
        WeatherCondition.CLEAR -> if (isDay) R.drawable.backplate_clear_day else R.drawable.backplate_clear_night
        WeatherCondition.MOSTLY_CLEAR -> if (isDay) R.drawable.backplate_mostly_clear_day else R.drawable.backplate_mostly_clear_night
        WeatherCondition.PARTLY_CLOUDY -> if (isDay) R.drawable.backplate_partly_cloudy_day else R.drawable.backplate_partly_cloudy_night
        WeatherCondition.OVERCAST -> if (isDay) R.drawable.backplate_overcast_day else R.drawable.backplate_overcast_night
        WeatherCondition.FOG -> if (isDay) R.drawable.backplate_fog_day else R.drawable.backplate_fog_night
        WeatherCondition.ATMOSPHERIC_HAZE -> if (isDay) R.drawable.backplate_atmospheric_haze_day else R.drawable.backplate_atmospheric_haze_night
        WeatherCondition.DRIZZLE -> if (isDay) R.drawable.backplate_drizzle_day else R.drawable.backplate_drizzle_night
        WeatherCondition.RAIN -> if (isDay) R.drawable.backplate_rain_day else R.drawable.backplate_rain_night
        WeatherCondition.HEAVY_RAIN -> if (isDay) R.drawable.backplate_heavy_rain_day else R.drawable.backplate_heavy_rain_night
        WeatherCondition.THUNDERSTORM -> if (isDay) R.drawable.backplate_thunderstorm_day else R.drawable.backplate_thunderstorm_night
        WeatherCondition.SNOW -> if (isDay) R.drawable.backplate_snow_day else R.drawable.backplate_snow_night
        WeatherCondition.HEAVY_SNOW -> if (isDay) R.drawable.backplate_heavy_snow_day else R.drawable.backplate_heavy_snow_night
        WeatherCondition.WINTRY_MIX -> if (isDay) R.drawable.backplate_wintry_mix_day else R.drawable.backplate_wintry_mix_night
        WeatherCondition.SEVERE_WEATHER -> if (isDay) R.drawable.backplate_severe_weather_day else R.drawable.backplate_severe_weather_night
    }
}

internal object BackplateLoader {
    fun imageProvider(context: Context, weather: CurrentWeather): ImageProvider =
        ImageProvider(bitmap(context, weather))

    fun bitmap(context: Context, weather: CurrentWeather): Bitmap =
        BitmapFactory.decodeResource(
            context.resources,
            BackplateCatalog.resourceFor(weather.condition, weather.isDay),
        ) ?: error("Unable to load weather backplate for ${weather.condition} / ${weather.isDay}")
}

internal fun CurrentWeather.forBackplate(choice: BackplateChoice?): CurrentWeather =
    choice?.let {
        copy(
            condition = it.condition,
            conditionLabel = it.label.substringBefore(" —"),
            isDay = it.isDay,
        )
    } ?: this
