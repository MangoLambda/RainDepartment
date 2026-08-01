package com.raindepartment.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.glance.ImageProvider

private data class BackplateSheet(
    @DrawableRes val resourceId: Int,
    val dayIndex: Int,
    val nightIndex: Int,
)

private object BackplateCatalog {
    fun sheetFor(condition: WeatherCondition): BackplateSheet = when (condition) {
        WeatherCondition.CLEAR -> BackplateSheet(R.drawable.backplate_clear, 0, 1)
        WeatherCondition.MOSTLY_CLEAR -> BackplateSheet(R.drawable.backplate_clear, 2, 3)
        WeatherCondition.PARTLY_CLOUDY -> BackplateSheet(R.drawable.backplate_clouds, 0, 1)
        WeatherCondition.OVERCAST -> BackplateSheet(R.drawable.backplate_clouds, 2, 3)
        WeatherCondition.FOG -> BackplateSheet(R.drawable.backplate_fog, 0, 1)
        WeatherCondition.ATMOSPHERIC_HAZE -> BackplateSheet(R.drawable.backplate_fog, 2, 3)
        WeatherCondition.DRIZZLE -> BackplateSheet(R.drawable.backplate_drizzle, 0, 1)
        WeatherCondition.RAIN -> BackplateSheet(R.drawable.backplate_drizzle, 2, 3)
        WeatherCondition.HEAVY_RAIN -> BackplateSheet(R.drawable.backplate_rain, 0, 1)
        WeatherCondition.THUNDERSTORM -> BackplateSheet(R.drawable.backplate_rain, 2, 3)
        WeatherCondition.SNOW -> BackplateSheet(R.drawable.backplate_snow, 0, 1)
        WeatherCondition.HEAVY_SNOW -> BackplateSheet(R.drawable.backplate_snow, 2, 3)
        WeatherCondition.WINTRY_MIX -> BackplateSheet(R.drawable.backplate_winter_mix, 0, 1)
        WeatherCondition.SEVERE_WEATHER -> BackplateSheet(R.drawable.backplate_winter_mix, 2, 3)
    }
}

internal object BackplateLoader {
    private const val CROP_LEFT = 50
    private const val CROP_WIDTH = 860
    private const val CROP_HEIGHT = 235
    private val CROP_TOPS = intArrayOf(70, 400, 730, 1040)

    fun imageProvider(context: Context, weather: DummyWeather): ImageProvider =
        ImageProvider(bitmap(context, weather))

    fun bitmap(context: Context, weather: DummyWeather): Bitmap {
        val sheet = BackplateCatalog.sheetFor(weather.condition)
        val source = BitmapFactory.decodeResource(context.resources, sheet.resourceId)
            ?: error("Unable to load weather backplate ${sheet.resourceId}")
        val index = if (weather.isDay) sheet.dayIndex else sheet.nightIndex
        val top = CROP_TOPS[index].coerceAtMost(source.height - 1)
        val left = CROP_LEFT.coerceIn(0, source.width - 1)
        val width = CROP_WIDTH.coerceAtMost(source.width - left)
        val height = CROP_HEIGHT.coerceAtMost(source.height - top)
        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        source.recycle()
        return cropped
    }
}
