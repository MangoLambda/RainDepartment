package com.raindepartment.weather

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

internal sealed interface ThunderstormRiskValue {
    data class Percentage(val value: Int) : ThunderstormRiskValue

    data class Wording(val value: String) : ThunderstormRiskValue

    data object Unavailable : ThunderstormRiskValue
}

internal data class ThunderstormRiskPoint(
    val timeEpochMillis: Long,
    val value: ThunderstormRiskValue,
)

internal interface ThunderstormRiskClient {
    suspend fun fetchSeries(
        location: WeatherLocation,
        timezone: String,
    ): List<ThunderstormRiskPoint>
}

internal object NoOpThunderstormRiskClient : ThunderstormRiskClient {
    override suspend fun fetchSeries(
        location: WeatherLocation,
        timezone: String,
    ): List<ThunderstormRiskPoint> = emptyList()
}

private const val CITY_PAGE_BBOX_DEGREES = 1.5

internal class HttpThunderstormRiskClient : ThunderstormRiskClient {
    override suspend fun fetchSeries(
        location: WeatherLocation,
        timezone: String,
    ): List<ThunderstormRiskPoint> = withContext(Dispatchers.IO) {
        val numeric = runCatching {
            parseOpenMeteoThunderstormRisk(
                requestJson(openMeteoThunderstormRiskUrl(location, timezone)),
                timezone,
            )
        }.getOrDefault(emptyList())

        val wording = if (numeric.size < THUNDERSTORM_RISK_MINIMUM_NUMERIC_POINTS) {
            runCatching {
                parseEcccCityPageThunderstormRisk(
                    requestJson(ecccCityPageUrl(location)),
                    location,
                )
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        mergeThunderstormRiskSeries(numeric, wording)
    }

    private fun requestJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RainDepartment/${BuildConfig.VERSION_NAME}")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Thunderstorm risk request returned HTTP $responseCode.")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val THUNDERSTORM_RISK_MINIMUM_NUMERIC_POINTS = 24
    }
}

internal fun openMeteoThunderstormRiskUrl(
    location: WeatherLocation,
    timezone: String,
): String = buildString {
    append("https://api.open-meteo.com/v1/forecast?")
    append("latitude=")
    append(String.format(Locale.US, "%.6f", location.latitude))
    append("&longitude=")
    append(String.format(Locale.US, "%.6f", location.longitude))
    append("&hourly=thunderstorm_probability")
    append("&forecast_hours=48")
    append("&timezone=")
    append(URLEncoder.encode(timezone, Charsets.UTF_8.name()))
}

internal fun ecccCityPageUrl(location: WeatherLocation): String = buildString {
    val latitudeMin = location.latitude - CITY_PAGE_BBOX_DEGREES
    val latitudeMax = location.latitude + CITY_PAGE_BBOX_DEGREES
    val longitudeMin = location.longitude - CITY_PAGE_BBOX_DEGREES
    val longitudeMax = location.longitude + CITY_PAGE_BBOX_DEGREES
    append("https://api.weather.gc.ca/collections/citypageweather-realtime/items?")
    append("bbox=")
    append(String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", longitudeMin, latitudeMin, longitudeMax, latitudeMax))
    append("&limit=20&f=json")
}

internal fun parseOpenMeteoThunderstormRisk(
    json: String,
    timezone: String,
): List<ThunderstormRiskPoint> {
    val root = runCatching { JSONTokener(json).nextValue() as? JSONObject }.getOrNull()
        ?: return emptyList()
    val hourly = root.optJSONObject("hourly") ?: return emptyList()
    val times = hourly.optJSONArray("time") ?: return emptyList()
    val values = hourly.optJSONArray("thunderstorm_probability") ?: return emptyList()
    val count = minOf(times.length(), values.length())

    return buildList {
        for (index in 0 until count) {
            val value = if (values.isNull(index)) {
                null
            } else {
                values.optDouble(index).takeIf { it.isFinite() }
                    ?.roundToInt()
                    ?.coerceIn(0, 100)
            }
            if (value != null) {
                add(
                    ThunderstormRiskPoint(
                        timeEpochMillis = parseRiskTimestamp(times.optString(index), timezone),
                        value = ThunderstormRiskValue.Percentage(value),
                    ),
                )
            }
        }
    }
}

internal fun parseEcccCityPageThunderstormRisk(
    json: String,
    location: WeatherLocation? = null,
): List<ThunderstormRiskPoint> {
    val root = runCatching { JSONTokener(json).nextValue() as? JSONObject }.getOrNull()
        ?: return emptyList()
    val features = root.optJSONArray("features") ?: return emptyList()
    val feature = (0 until features.length())
        .asSequence()
        .mapNotNull { features.optJSONObject(it) }
        .filter { it.optJSONObject("properties")?.optJSONObject("hourlyForecastGroup") != null }
        .minByOrNull { featureDistanceSquared(it, location) }
        ?: return emptyList()
    val hourlyForecasts = feature
        .optJSONObject("properties")
        ?.optJSONObject("hourlyForecastGroup")
        ?.optJSONArray("hourlyForecasts")
        ?: return emptyList()

    return buildList {
        for (index in 0 until hourlyForecasts.length()) {
            val hourly = hourlyForecasts.optJSONObject(index) ?: continue
            val timestamp = hourly.optString("timestamp").takeIf { it.isNotBlank() } ?: continue
            val condition = hourly.optJSONObject("condition")?.optString("en").orEmpty()
            val wording = thunderstormWording(condition)
            add(
                ThunderstormRiskPoint(
                    timeEpochMillis = parseRiskTimestamp(timestamp, "UTC"),
                    value = wording?.let(ThunderstormRiskValue::Wording)
                        ?: ThunderstormRiskValue.Unavailable,
                ),
            )
        }
    }
}

private fun featureDistanceSquared(feature: JSONObject, location: WeatherLocation?): Double {
    if (location == null) return 0.0
    val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
        ?: return Double.MAX_VALUE
    val longitude = coordinates.optDouble(0, Double.NaN)
    val latitude = coordinates.optDouble(1, Double.NaN)
    if (!longitude.isFinite() || !latitude.isFinite()) return Double.MAX_VALUE
    val latitudeDistance = latitude - location.latitude
    val longitudeDistance = longitude - location.longitude
    return latitudeDistance * latitudeDistance + longitudeDistance * longitudeDistance
}

internal fun mergeThunderstormRiskSeries(
    numeric: List<ThunderstormRiskPoint>,
    wording: List<ThunderstormRiskPoint>,
): List<ThunderstormRiskPoint> {
    val wordingByTime = wording.associateBy { it.timeEpochMillis }
    val numericByTime = numeric.associateBy { it.timeEpochMillis }
    return (wordingByTime.keys + numericByTime.keys)
        .distinct()
        .sorted()
        .map { timestamp ->
            numericByTime[timestamp]
                ?: wordingByTime[timestamp]
                ?: ThunderstormRiskPoint(timestamp, ThunderstormRiskValue.Unavailable)
        }
}

internal fun nearestThunderstormRisk(
    points: List<ThunderstormRiskPoint>,
    timeEpochMillis: Long?,
    maximumDistanceMillis: Long = 90 * 60_000L,
): ThunderstormRiskValue = if (timeEpochMillis == null || points.isEmpty()) {
    ThunderstormRiskValue.Unavailable
} else {
    points.minByOrNull { kotlin.math.abs(it.timeEpochMillis - timeEpochMillis) }
        ?.takeIf { kotlin.math.abs(it.timeEpochMillis - timeEpochMillis) <= maximumDistanceMillis }
        ?.value
        ?: ThunderstormRiskValue.Unavailable
}

internal fun thunderstormWording(condition: String): String? {
    val trimmed = condition.trim().trimEnd('.')
    if (trimmed.isBlank()) return null
    val lower = trimmed.lowercase(Locale.ROOT)
    val riskIndex = lower.indexOf("risk of")
    if (riskIndex >= 0 && lower.substring(riskIndex).contains("thunderstorm")) {
        return trimmed.substring(riskIndex).trim()
    }
    val chanceIndex = lower.indexOf("chance of")
    if (chanceIndex >= 0 && lower.substring(chanceIndex).contains("thunderstorm")) {
        return trimmed.substring(chanceIndex).trim()
    }
    return null
}

private fun parseRiskTimestamp(value: String, timezone: String): Long {
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.of(timezone))
                .toInstant()
                .toEpochMilli()
        }
        .getOrElse { 0L }
}
