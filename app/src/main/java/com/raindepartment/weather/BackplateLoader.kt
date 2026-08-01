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
    val crops: List<BackplateCrop>,
)

private data class BackplateCrop(
    val top: Int,
    val bottom: Int,
) {
    val height: Int get() = bottom - top
}

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

// The source sheets contain four generated cards with inconsistent heights and
// vertical positions. Keep the visible-art bounds per sheet, then normalize
// every extracted card before the widget applies its own size-dependent crop.
private object BackplateCatalog {
    fun sheetFor(condition: WeatherCondition): BackplateSheet = when (condition) {
        WeatherCondition.CLEAR -> backplateSheet(
            R.drawable.backplate_clear,
            0,
            1,
            crop(70, 330),
            crop(397, 655),
            crop(722, 950),
            crop(1016, 1242),
        )
        WeatherCondition.MOSTLY_CLEAR -> backplateSheet(
            R.drawable.backplate_clear,
            2,
            3,
            crop(70, 330),
            crop(397, 655),
            crop(722, 950),
            crop(1016, 1242),
        )
        WeatherCondition.PARTLY_CLOUDY -> backplateSheet(
            R.drawable.backplate_clouds,
            0,
            1,
            crop(69, 332),
            crop(397, 664),
            crop(730, 967),
            crop(1031, 1248),
        )
        WeatherCondition.OVERCAST -> backplateSheet(
            R.drawable.backplate_clouds,
            2,
            3,
            crop(69, 332),
            crop(397, 664),
            crop(730, 967),
            crop(1031, 1248),
        )
        WeatherCondition.FOG -> backplateSheet(
            R.drawable.backplate_fog,
            0,
            1,
            crop(69, 335),
            crop(401, 664),
            crop(729, 964),
            crop(1028, 1248),
        )
        WeatherCondition.ATMOSPHERIC_HAZE -> backplateSheet(
            R.drawable.backplate_fog,
            2,
            3,
            crop(69, 335),
            crop(401, 664),
            crop(729, 964),
            crop(1028, 1248),
        )
        WeatherCondition.DRIZZLE -> backplateSheet(
            R.drawable.backplate_drizzle,
            0,
            1,
            crop(69, 333),
            crop(402, 661),
            crop(730, 971),
            crop(1037, 1244),
        )
        WeatherCondition.RAIN -> backplateSheet(
            R.drawable.backplate_drizzle,
            2,
            3,
            crop(69, 333),
            crop(402, 661),
            crop(730, 971),
            crop(1037, 1244),
        )
        WeatherCondition.HEAVY_RAIN -> backplateSheet(
            R.drawable.backplate_rain,
            0,
            1,
            crop(65, 334),
            crop(400, 662),
            crop(731, 981),
            crop(1042, 1263),
        )
        WeatherCondition.THUNDERSTORM -> backplateSheet(
            R.drawable.backplate_rain,
            2,
            3,
            crop(65, 334),
            crop(400, 662),
            crop(731, 981),
            crop(1042, 1263),
        )
        WeatherCondition.SNOW -> backplateSheet(
            R.drawable.backplate_snow,
            0,
            1,
            crop(64, 345),
            crop(409, 686),
            crop(756, 993),
            crop(1054, 1250),
        )
        WeatherCondition.HEAVY_SNOW -> backplateSheet(
            R.drawable.backplate_snow,
            2,
            3,
            crop(64, 345),
            crop(409, 686),
            crop(756, 993),
            crop(1054, 1250),
        )
        WeatherCondition.WINTRY_MIX -> backplateSheet(
            R.drawable.backplate_winter_mix,
            0,
            1,
            crop(70, 347),
            crop(410, 685),
            crop(749, 992),
            crop(1049, 1253),
        )
        WeatherCondition.SEVERE_WEATHER -> backplateSheet(
            R.drawable.backplate_winter_mix,
            2,
            3,
            crop(70, 347),
            crop(410, 685),
            crop(749, 992),
            crop(1049, 1253),
        )
    }
}

private fun backplateSheet(
    @DrawableRes resourceId: Int,
    dayIndex: Int,
    nightIndex: Int,
    vararg crops: BackplateCrop,
): BackplateSheet = BackplateSheet(
    resourceId = resourceId,
    dayIndex = dayIndex,
    nightIndex = nightIndex,
    crops = crops.toList(),
)

private fun crop(top: Int, bottom: Int): BackplateCrop = BackplateCrop(top, bottom)

internal object BackplateLoader {
    private const val CROP_LEFT = 50
    private const val CROP_WIDTH = 860
    private const val OUTPUT_WIDTH = 860
    private const val OUTPUT_HEIGHT = 235

    fun imageProvider(context: Context, weather: DummyWeather): ImageProvider =
        ImageProvider(bitmap(context, weather))

    fun bitmap(context: Context, weather: DummyWeather): Bitmap {
        val sheet = BackplateCatalog.sheetFor(weather.condition)
        val source = BitmapFactory.decodeResource(context.resources, sheet.resourceId)
            ?: error("Unable to load weather backplate ${sheet.resourceId}")
        val index = if (weather.isDay) sheet.dayIndex else sheet.nightIndex
        val crop = sheet.crops[index]
        val top = crop.top.coerceIn(0, source.height - 1)
        val left = CROP_LEFT.coerceIn(0, source.width - 1)
        val width = CROP_WIDTH.coerceAtMost(source.width - left)
        val height = crop.height.coerceAtMost(source.height - top)
        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        source.recycle()
        val normalized = Bitmap.createScaledBitmap(
            cropped,
            OUTPUT_WIDTH,
            OUTPUT_HEIGHT,
            true,
        )
        if (normalized !== cropped) {
            cropped.recycle()
        }
        return normalized
    }
}

internal fun DummyWeather.forBackplate(choice: BackplateChoice): DummyWeather = copy(
    condition = choice.condition,
    conditionLabel = choice.label.substringBefore(" —"),
    isDay = choice.isDay,
)
