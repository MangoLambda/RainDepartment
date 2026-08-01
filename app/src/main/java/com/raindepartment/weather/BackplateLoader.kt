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

internal fun DummyWeather.forBackplate(choice: BackplateChoice): DummyWeather = copy(
    condition = choice.condition,
    conditionLabel = choice.label.substringBefore(" —"),
    isDay = choice.isDay,
)
