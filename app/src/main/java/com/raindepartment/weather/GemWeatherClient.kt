package com.raindepartment.weather

import com.raindepartment.weather.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class ParsedGemWeather(
    val forecast: DashboardForecast,
    val timezone: String,
)

internal interface WeatherClient {
    suspend fun fetch(location: WeatherLocation): ParsedGemWeather
}

internal interface GemWeatherClient : WeatherClient

internal class GemHttpException(
    val statusCode: Int,
) : IOException("Open-Meteo returned HTTP $statusCode.")

internal class GemDataException(message: String) : IOException(message)

internal class HttpGemWeatherClient : GemWeatherClient {
    override suspend fun fetch(location: WeatherLocation): ParsedGemWeather = withContext(Dispatchers.IO) {
        val connection = (URL(gemRequestUrl(location)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RainDepartment/${BuildConfig.VERSION_NAME}")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw GemHttpException(responseCode)
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            GemWeatherParser.parse(json, location)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun gemRequestUrl(location: WeatherLocation): String = buildString {
    append("https://api.open-meteo.com/v1/forecast?")
    append("latitude=")
    append(String.format(Locale.US, "%.6f", location.latitude))
    append("&longitude=")
    append(String.format(Locale.US, "%.6f", location.longitude))
    append("&current=")
    append("temperature_2m,apparent_temperature,is_day,rain,showers,weather_code,")
    append("wind_speed_10m,wind_direction_10m")
    append("&hourly=")
    append("temperature_2m,precipitation_probability,rain,showers,weather_code,")
    append("wind_speed_10m,wind_direction_10m")
    append("&daily=")
    append("weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,")
    append("rain_sum,showers_sum,precipitation_probability_max,wind_speed_10m_max,")
    append("wind_direction_10m_dominant")
    append("&temperature_unit=fahrenheit")
    append("&wind_speed_unit=mph")
    append("&precipitation_unit=inch")
    append("&timezone=auto")
    append("&forecast_days=7")
    append("&models=best_match")
}

internal object GemWeatherParser {
    private val hourFormatter = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
    private val clockFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val compassDirections = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    fun parse(json: String, location: WeatherLocation): ParsedGemWeather {
        val root = runCatching { JSONTokener(json).nextValue() as? JSONObject }
            .getOrNull()
            ?: throw GemDataException("Open-Meteo returned an invalid JSON document.")
        if (root.optBoolean("error")) {
            throw GemDataException(root.optString("reason").ifBlank { "Open-Meteo returned an error." })
        }

        val timezone = root.optString("timezone").ifBlank { "UTC" }
        val zoneId = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneOffset.UTC)
        val current = root.requiredObject("current")
        val hourly = root.requiredObject("hourly")
        val daily = root.requiredObject("daily")

        val hourlyTimes = hourly.requiredStringArray("time")
            .map { parseApiTime(it, zoneId) }
        val hourlyTemperature = hourly.requiredDoubleArray("temperature_2m")
        val hourlyChance = hourly.requiredDoubleArray("precipitation_probability")
        val hourlyRain = hourly.requiredDoubleArray("rain")
        val hourlyShowers = hourly.requiredDoubleArray("showers")
        val hourlyCodes = hourly.requiredDoubleArray("weather_code")
        val hourlyWind = hourly.requiredDoubleArray("wind_speed_10m")
        val hourlyDirections = hourly.requiredDoubleArray("wind_direction_10m")
        val hourlyLength = hourlyTimes.size
        listOf(
            hourlyTemperature.size,
            hourlyChance.size,
            hourlyRain.size,
            hourlyShowers.size,
            hourlyCodes.size,
            hourlyWind.size,
            hourlyDirections.size,
        ).forEach { requireLength("hourly", hourlyLength, it) }
        if (hourlyLength == 0) throw GemDataException("Open-Meteo returned no hourly data.")

        val dailyTimes = daily.requiredStringArray("time")
            .map { LocalDate.parse(it) }
        val dailyCodes = daily.requiredDoubleArray("weather_code")
        val dailyHighs = daily.requiredDoubleArray("temperature_2m_max")
        val dailyLows = daily.requiredDoubleArray("temperature_2m_min")
        val dailySunrise = daily.requiredStringArray("sunrise")
        val dailySunset = daily.requiredStringArray("sunset")
        val dailyRain = daily.requiredDoubleArray("rain_sum")
        val dailyShowers = daily.requiredDoubleArray("showers_sum")
        val dailyChance = daily.requiredDoubleArray("precipitation_probability_max")
        val dailyWind = daily.requiredDoubleArray("wind_speed_10m_max")
        val dailyWindDirections = daily.requiredDoubleArray("wind_direction_10m_dominant")
        val dailyLength = dailyTimes.size
        listOf(
            dailyCodes.size,
            dailyHighs.size,
            dailyLows.size,
            dailySunrise.size,
            dailySunset.size,
            dailyRain.size,
            dailyShowers.size,
            dailyChance.size,
            dailyWind.size,
            dailyWindDirections.size,
        ).forEach { requireLength("daily", dailyLength, it) }
        if (dailyLength == 0) throw GemDataException("Open-Meteo returned no daily data.")

        val allHourlyRows = hourlyTimes.indices.map { index ->
            ForecastHour(
                time = hourlyTimes[index],
                precipitationChance = hourlyChance[index].roundToInt().coerceIn(0, 100),
                precipitationInches = liquidRainfall(hourlyRain[index], hourlyShowers[index]),
                windMph = hourlyWind[index].coerceAtLeast(0.0),
            )
        }
        fun hourlyForecast(index: Int, time: String = formatHour(hourlyTimes[index])): HourlyForecast {
            val condition = conditionForCode(hourlyCodes[index].roundToInt())
            return HourlyForecast(
                time = time,
                precipitationChance = hourlyChance[index].roundToInt().coerceIn(0, 100),
                rainfallInches = liquidRainfall(hourlyRain[index], hourlyShowers[index]),
                temperatureFahrenheit = hourlyTemperature[index].roundToInt(),
                windMph = hourlyWind[index].roundToInt().coerceAtLeast(0),
                windDirection = compassDirection(hourlyDirections[index]),
                windDirectionLabel = compassDirection(hourlyDirections[index]),
                condition = condition.first,
                conditionLabel = condition.second,
                timeEpochMillis = hourlyTimes[index].toInstant().toEpochMilli(),
            )
        }

        val currentTime = parseApiTime(current.requiredString("time"), zoneId)
        val currentIndex = hourlyTimes.indexOfLast { !it.isAfter(currentTime) }
            .takeIf { it >= 0 }
            ?: 0

        val currentCode = current.requiredDouble("weather_code").roundToInt()
        val currentCondition = conditionForCode(currentCode)
        val (currentWeatherCondition, currentConditionLabel) = currentCondition
        val firstDay = dailyTimes.first()
        val firstDayRows = hourlyTimes.indices.filter { hourlyTimes[it].toLocalDate() == firstDay }
        val peakIndex = firstDayRows.maxByOrNull { hourlyWind[it] } ?: currentIndex
        val peakWindDirection = compassDirection(
            dailyWindDirections.firstOrNull() ?: hourlyDirections[peakIndex],
        )
        val peakWindTime = formatHour(hourlyTimes.getOrNull(peakIndex) ?: currentTime)

        val visibleIndices = (currentIndex until hourlyLength).take(24)
        val chartIndices = (currentIndex until hourlyLength).take(24)
        val hourlyForecast = visibleIndices.mapIndexed { visibleIndex, index ->
            hourlyForecast(index, time = if (visibleIndex == 0) "Now" else formatHour(hourlyTimes[index]))
        }

        val currentPrecipitationInches = liquidRainfall(
            current.requiredDouble("rain"),
            current.requiredDouble("showers"),
        )
        val currentPrecipitationChance = hourlyChance[currentIndex].roundToInt().coerceIn(0, 100)
        val rainIndex = hourlyTimes.indices.firstOrNull {
            hourlyTimes[it].isAfter(currentTime) &&
                hourlyChance[it] > 0.0 &&
                hasMinimumRainStartAmount(liquidRainfall(hourlyRain[it], hourlyShowers[it]))
        }
        val rainStartsAt = when {
            currentWeatherCondition.isRainBearing() &&
                precipitationChanceAllowsRain(currentPrecipitationChance, available = true) ->
                currentTime
            rainIndex != null -> hourlyTimes[rainIndex]
            else -> null
        }
        val rainStartsIn = when {
            rainStartsAt == null -> "No rain expected"
            rainStartsAt <= currentTime -> "Now"
            else -> formatDuration(currentTime, rainStartsAt)
        }

        val dailyForecast = dailyTimes.indices.map { index ->
            val condition = conditionForCode(dailyCodes[index].roundToInt())
            val dayIndices = hourlyTimes.indices.filter { hourlyTimes[it].toLocalDate() == dailyTimes[index] }
            val dayRows = dayIndices.map { allHourlyRows[it] }
            val dayPeakIndex = dayIndices.maxByOrNull { hourlyWind[it] }
            DailyForecast(
                day = if (index == 0) "Today" else dailyTimes[index]
                    .atStartOfDay(zoneId)
                    .format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
                condition = condition.first,
                conditionLabel = condition.second,
                precipitationChance = dailyChance[index].roundToInt().coerceIn(0, 100),
                rainfallInches = liquidRainfall(dailyRain[index], dailyShowers[index]),
                highFahrenheit = dailyHighs[index].roundToInt(),
                lowFahrenheit = dailyLows[index].roundToInt(),
                sunrise = parseApiTime(dailySunrise[index], zoneId).format(clockFormatter),
                sunset = parseApiTime(dailySunset[index], zoneId).format(clockFormatter),
                peakWindMph = dailyWind[index].roundToInt().coerceAtLeast(0),
                peakWindDirection = compassDirection(dailyWindDirections[index]),
                peakWindTime = dayPeakIndex?.let { formatHour(hourlyTimes[it]) } ?: "",
                dryWindow = dryWindow(dayRows),
                hourly = dayIndices.map { hourlyForecast(it) },
            )
        }

        val chartRows = chartIndices.map { allHourlyRows[it] }
        val chartRowsSampled = chartRows.filterIndexed { index, _ -> index % 3 == 0 }
        val precipitationChart = chartRowsSampled.mapIndexed { index, row ->
            ChartPoint(
                label = if (index == 0) "Now" else formatHour(row.time),
                value = row.precipitationInches.toFloat(),
            )
        }
        val windChart = chartRowsSampled.mapIndexed { index, row ->
            ChartPoint(
                label = if (index == 0) "Now" else formatHour(row.time),
                value = row.windMph.toFloat(),
            )
        }

        val firstDailySunrise = parseApiTime(dailySunrise.first(), zoneId)
        val firstDailySunset = parseApiTime(dailySunset.first(), zoneId)
        val currentConditionResult = currentWeatherCondition to currentConditionLabel
        return ParsedGemWeather(
            forecast = DashboardForecast(
                location = location.label,
                condition = currentConditionResult.first,
                isDay = current.requiredDouble("is_day").roundToInt() == 1,
                rainStartsIn = rainStartsIn,
                currentFahrenheit = current.requiredDouble("temperature_2m").roundToInt(),
                feelsLikeFahrenheit = current.requiredDouble("apparent_temperature").roundToInt(),
                highFahrenheit = dailyHighs.first().roundToInt(),
                lowFahrenheit = dailyLows.first().roundToInt(),
                conditionLabel = currentConditionResult.second,
                precipitationChance = dailyChance.first().roundToInt().coerceIn(0, 100),
                currentPrecipitationInches = currentPrecipitationInches,
                expectedRainInches = liquidRainfall(dailyRain.first(), dailyShowers.first()),
                peakWindMph = dailyWind.first().roundToInt().coerceAtLeast(0),
                peakWindDirection = peakWindDirection,
                peakWindTime = peakWindTime,
                hourly = hourlyForecast,
                precipitation24h = precipitationChart,
                windByHour = windChart,
                daily = dailyForecast,
                rainfallOutlook = dailyForecast.map { day ->
                    ChartPoint(day.day, day.rainfallInches.toFloat())
                },
                sunrise = firstDailySunrise.format(clockFormatter),
                sunset = firstDailySunset.format(clockFormatter),
                dryWindow = dryWindow(chartRows),
                rainStartsAtEpochMillis = rainStartsAt?.toInstant()?.toEpochMilli(),
                rainStartSource = if (rainStartsAt == null) {
                    RainStartSource.NONE
                } else {
                    RainStartSource.MODEL
                },
            ),
            timezone = timezone,
        )
    }

    private fun liquidRainfall(rain: Double, showers: Double): Double =
        rain.coerceAtLeast(0.0) + showers.coerceAtLeast(0.0)

    private data class ForecastHour(
        val time: ZonedDateTime,
        val precipitationChance: Int,
        val precipitationInches: Double,
        val windMph: Double,
    )

    private fun dryWindow(rows: List<ForecastHour>): String {
        if (rows.isEmpty()) return "No clear dry window"

        var bestStart = -1
        var bestEnd = -1
        var currentStart = -1
        rows.indices.forEach { index ->
            val isDry = rows[index].precipitationChance <= 20 &&
                rows[index].precipitationInches <= 0.005
            if (isDry && currentStart == -1) currentStart = index
            if ((!isDry || index == rows.lastIndex) && currentStart != -1) {
                val end = if (isDry) index else index - 1
                if (bestStart == -1 || end - currentStart > bestEnd - bestStart) {
                    bestStart = currentStart
                    bestEnd = end
                }
                currentStart = -1
            }
        }

        if (bestStart == -1) return "No clear dry window"
        val start = rows[bestStart].time.format(hourFormatter)
        val end = rows[bestEnd].time.plusHours(1).format(hourFormatter)
        return "$start – $end"
    }

    private fun formatDuration(from: ZonedDateTime, to: ZonedDateTime): String {
        val minutes = Duration.between(from, to).toMinutes().coerceAtLeast(0)
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return when {
            hours == 0L -> "${remainingMinutes}m"
            remainingMinutes == 0L -> "${hours}h"
            else -> "${hours}h ${remainingMinutes}m"
        }
    }

    private fun formatHour(time: ZonedDateTime): String = time.format(hourFormatter)

    private fun parseApiTime(value: String, zoneId: ZoneId): ZonedDateTime = runCatching {
        LocalDateTime.parse(value).atZone(zoneId)
    }.getOrElse {
        throw GemDataException("Open-Meteo returned an invalid timestamp: $value")
    }

    private fun conditionForCode(code: Int): Pair<WeatherCondition, String> = when {
        code == 0 -> WeatherCondition.CLEAR to "Clear"
        code == 1 -> WeatherCondition.MOSTLY_CLEAR to "Mostly clear"
        code == 2 -> WeatherCondition.PARTLY_CLOUDY to "Partly cloudy"
        code == 3 -> WeatherCondition.OVERCAST to "Overcast"
        code in 45..48 -> WeatherCondition.FOG to "Fog"
        code in 51..55 -> WeatherCondition.DRIZZLE to "Drizzle"
        code in 56..57 -> WeatherCondition.WINTRY_MIX to "Freezing drizzle"
        code in 61..64 -> WeatherCondition.RAIN to "Rain"
        code == 65 -> WeatherCondition.HEAVY_RAIN to "Heavy rain"
        code in 66..67 -> WeatherCondition.WINTRY_MIX to "Freezing rain"
        code in 71..74 || code == 77 -> WeatherCondition.SNOW to "Snow"
        code == 75 -> WeatherCondition.HEAVY_SNOW to "Heavy snow"
        code == 80 || code == 81 -> WeatherCondition.RAIN to "Showers"
        code == 82 -> WeatherCondition.HEAVY_RAIN to "Heavy showers"
        code == 85 -> WeatherCondition.SNOW to "Snow showers"
        code == 86 -> WeatherCondition.HEAVY_SNOW to "Heavy snow showers"
        code == 95 -> WeatherCondition.THUNDERSTORM to "Thunderstorm"
        code in 96..99 -> WeatherCondition.SEVERE_WEATHER to "Severe thunderstorm"
        else -> WeatherCondition.OVERCAST to "Overcast"
    }

    private fun compassDirection(degrees: Double): String {
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        val index = (normalized / 22.5).roundToInt() % compassDirections.size
        return compassDirections[index]
    }

    private fun JSONObject.requiredObject(name: String): JSONObject = optJSONObject(name)
        ?: throw GemDataException("Open-Meteo response is missing $name.")

    private fun JSONObject.requiredString(name: String): String = optString(name)
        .takeIf { it.isNotBlank() }
        ?: throw GemDataException("Open-Meteo response is missing $name.")

    private fun JSONObject.requiredDouble(name: String): Double = if (has(name) && !isNull(name)) {
        runCatching { getDouble(name) }.getOrElse {
            throw GemDataException("Open-Meteo returned an invalid $name value.")
        }
    } else {
        throw GemDataException("Open-Meteo response is missing $name.")
    }

    private fun JSONObject.requiredStringArray(name: String): List<String> {
        val array = optJSONArray(name)
            ?: throw GemDataException("Open-Meteo response is missing $name.")
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                if (array.isNull(index)) {
                    throw GemDataException("Open-Meteo returned a null $name value.")
                }
                add(array.optString(index).takeIf(String::isNotBlank)
                    ?: throw GemDataException("Open-Meteo returned an invalid $name value."))
            }
        }
    }

    private fun JSONObject.requiredDoubleArray(name: String): List<Double> {
        val array = optJSONArray(name)
            ?: throw GemDataException("Open-Meteo response is missing $name.")
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                if (array.isNull(index)) {
                    throw GemDataException("Open-Meteo returned a null $name value.")
                }
                add(runCatching { array.getDouble(index) }.getOrElse {
                    throw GemDataException("Open-Meteo returned an invalid $name value.")
                })
            }
        }
    }

    private fun requireLength(section: String, expected: Int, actual: Int) {
        if (expected != actual) {
            throw GemDataException("Open-Meteo returned mismatched $section arrays.")
        }
    }
}
