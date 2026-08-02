package com.raindepartment.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.glance.ImageProvider

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
