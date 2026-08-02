package com.raindepartment.weather

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

internal data class EcccRadarTimeWindow(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val intervalMillis: Long,
)

internal data class EcccRadarRainStart(
    val startsAtEpochMillis: Long,
    val confidenceMeaningful: Boolean,
)

internal interface EcccRadarClient {
    suspend fun findRainStart(
        location: WeatherLocation,
        nowEpochMillis: Long,
    ): EcccRadarRainStart?
}

internal object NoOpEcccRadarClient : EcccRadarClient {
    override suspend fun findRainStart(
        location: WeatherLocation,
        nowEpochMillis: Long,
    ): EcccRadarRainStart? = null
}

internal class EcccRadarHttpException(
    val statusCode: Int,
) : IOException("ECCC radar returned HTTP $statusCode.")

internal class EcccRadarDataException(message: String) : IOException(message)

internal class HttpEcccRadarClient(
    private val clock: () -> Long = System::currentTimeMillis,
) : EcccRadarClient {
    override suspend fun findRainStart(
        location: WeatherLocation,
        nowEpochMillis: Long,
    ): EcccRadarRainStart? = withContext(Dispatchers.IO) {
        val window = fetchTimeWindow()
        val firstTime = alignUp(
            value = max(window.startEpochMillis, nowEpochMillis),
            interval = window.intervalMillis,
        )
        val lastTime = min(
            window.endEpochMillis,
            nowEpochMillis + RADAR_RAIN_WINDOW_MINUTES * 60_000L,
        )
        if (firstTime > lastTime) return@withContext null

        var firstPositiveTime: Long? = null
        var consecutivePositiveFrames = 0
        var time = firstTime
        while (time <= lastTime) {
            val rate = fetchRainRate(location, time)
            if (rate != null && rate >= RADAR_MEANINGFUL_RATE_MM_PER_HOUR) {
                if (firstPositiveTime == null) firstPositiveTime = time
                consecutivePositiveFrames += 1
                if (consecutivePositiveFrames >= MEANINGFUL_FRAME_COUNT) {
                    return@withContext EcccRadarRainStart(
                        startsAtEpochMillis = firstPositiveTime,
                        confidenceMeaningful = true,
                    )
                }
            } else if (firstPositiveTime != null) {
                return@withContext EcccRadarRainStart(
                    startsAtEpochMillis = firstPositiveTime,
                    confidenceMeaningful = false,
                )
            }
            time += window.intervalMillis
        }

        firstPositiveTime?.let {
            EcccRadarRainStart(
                startsAtEpochMillis = it,
                confidenceMeaningful = false,
            )
        }
    }

    private fun fetchTimeWindow(): EcccRadarTimeWindow {
        val body = fetchText(
            ecccRadarCapabilitiesUrl(cacheBust = clock()),
        )
        return parseEcccRadarTimeWindow(body)
    }

    private fun fetchRainRate(location: WeatherLocation, timeEpochMillis: Long): Double? {
        val body = fetchText(ecccRadarFeatureInfoUrl(location, timeEpochMillis))
        return parseEcccRadarRainRate(body)
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/xml")
            setRequestProperty("User-Agent", "RainDepartment/${BuildConfig.VERSION_NAME}")
        }

        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) throw EcccRadarHttpException(responseCode)
            return body
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val RADAR_MEANINGFUL_RATE_MM_PER_HOUR = 0.1
        const val MEANINGFUL_FRAME_COUNT = 2
    }
}

internal fun ecccRadarCapabilitiesUrl(cacheBust: Long): String = buildString {
    append(ECCC_RADAR_ENDPOINT)
    append("?")
    appendQueryParameter("lang", "en")
    append("&")
    appendQueryParameter("service", "WMS")
    append("&")
    appendQueryParameter("version", "1.3.0")
    append("&")
    appendQueryParameter("request", "GetCapabilities")
    append("&")
    appendQueryParameter("layer", ECCC_RADAR_LAYER)
    append("&")
    appendQueryParameter("cacheBust", cacheBust.toString())
}

internal fun ecccRadarFeatureInfoUrl(
    location: WeatherLocation,
    timeEpochMillis: Long,
): String {
    val latitudeDelta = 0.01
    val longitudeDelta = 0.01 / max(cos(Math.toRadians(location.latitude)), 0.2)
    val minLatitude = (location.latitude - latitudeDelta).coerceAtLeast(-90.0)
    val maxLatitude = (location.latitude + latitudeDelta).coerceAtMost(90.0)
    val minLongitude = location.longitude - longitudeDelta
    val maxLongitude = location.longitude + longitudeDelta
    val bbox = String.format(
        Locale.US,
        "%.6f,%.6f,%.6f,%.6f",
        minLatitude,
        minLongitude,
        maxLatitude,
        maxLongitude,
    )
    val time = Instant.ofEpochMilli(timeEpochMillis).toString()

    return buildString {
        append(ECCC_RADAR_ENDPOINT)
        append("?")
        val parameters = listOf(
            "lang" to "en",
            "service" to "WMS",
            "version" to "1.3.0",
            "request" to "GetFeatureInfo",
            "layers" to ECCC_RADAR_LAYER,
            "query_layers" to ECCC_RADAR_LAYER,
            "crs" to "EPSG:4326",
            "bbox" to bbox,
            "width" to "3",
            "height" to "3",
            "i" to "1",
            "j" to "1",
            "info_format" to "application/json",
            "time" to time,
        )
        parameters.forEachIndexed { index, (name, value) ->
            if (index > 0) append("&")
            appendQueryParameter(name, value)
        }
    }
}

internal fun parseEcccRadarTimeWindow(xml: String): EcccRadarTimeWindow {
    val dimension = Regex(
        """<Dimension\b[^>]*\bname\s*=\s*[\"']time[\"'][^>]*>([^<]+)</Dimension>""",
        RegexOption.IGNORE_CASE,
    ).find(xml)?.groupValues?.getOrNull(1)?.trim()
        ?: throw EcccRadarDataException("ECCC radar did not return a time window.")
    val parts = dimension.split('/').map(String::trim)
    if (parts.size != 3) {
        throw EcccRadarDataException("ECCC radar returned an invalid time window.")
    }

    val start = runCatching { Instant.parse(parts[0]).toEpochMilli() }.getOrElse {
        throw EcccRadarDataException("ECCC radar returned an invalid start time.")
    }
    val end = runCatching { Instant.parse(parts[1]).toEpochMilli() }.getOrElse {
        throw EcccRadarDataException("ECCC radar returned an invalid end time.")
    }
    val interval = runCatching { Duration.parse(parts[2]).toMillis() }.getOrElse {
        throw EcccRadarDataException("ECCC radar returned an invalid interval.")
    }
    if (start > end || interval <= 0L) {
        throw EcccRadarDataException("ECCC radar returned an unusable time window.")
    }
    return EcccRadarTimeWindow(start, end, interval)
}

internal fun parseEcccRadarRainRate(json: String): Double? {
    val root = runCatching { JSONTokener(json).nextValue() as? JSONObject }.getOrNull()
        ?: return null
    val features = root.optJSONArray("features") ?: return null
    val properties = features.optJSONObject(0)?.optJSONObject("properties") ?: return null
    val value = properties.opt("value") ?: return null
    val rate = when (value) {
        is Number -> value.toDouble()
        else -> value.toString().toDoubleOrNull()
    } ?: return null
    return rate.takeIf { it.isFinite() && it >= 0.0 }
}

private const val ECCC_RADAR_ENDPOINT = "https://geo.weather.gc.ca/geomet"
private const val ECCC_RADAR_LAYER = "Radar_1km_RainPrecipRate-Extrapolation"

private fun StringBuilder.appendQueryParameter(name: String, value: String) {
    append(name)
    append("=")
    append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()))
}

private fun alignUp(value: Long, interval: Long): Long {
    val remainder = value % interval
    return if (remainder == 0L) value else value + interval - remainder
}
