package com.raindepartment.weather

import com.raindepartment.weather.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal class EcccWeatherHttpException(
    val statusCode: Int,
) : IOException("ECCC Citypage returned HTTP $statusCode.")

internal class EcccWeatherDataException(message: String) : IOException(message)

internal class HttpEcccCityPageWeatherClient : WeatherClient {
    override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
        withContext(Dispatchers.IO) {
            val connection = (URL(ecccCityPageUrl(location)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "RainDepartment/${BuildConfig.VERSION_NAME}")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) throw EcccWeatherHttpException(responseCode)
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                EcccWeatherParser.parse(json, location)
            } finally {
                connection.disconnect()
            }
        }
}

internal class EcccFirstWeatherClient(
    private val primary: WeatherClient = HttpEcccCityPageWeatherClient(),
    private val fallback: WeatherClient = HttpGemWeatherClient(),
) : WeatherClient {
    override suspend fun fetch(location: WeatherLocation): ParsedGemWeather = coroutineScope {
        val primaryResult = async { fetchSafely(primary, location) }
        val supplementalResult = async { fetchSafely(fallback, location) }
        val primaryForecast = primaryResult.await().getOrNull()
        val supplementalForecast = supplementalResult.await().getOrNull()

        if (primaryForecast != null) {
            if (supplementalForecast == null) {
                primaryForecast
            } else {
                primaryForecast.withSupplementalPrecipitation(supplementalForecast)
            }
        } else {
            supplementalResult.await().getOrThrow()
        }
    }

    private suspend fun fetchSafely(
        client: WeatherClient,
        location: WeatherLocation,
    ): Result<ParsedGemWeather> = try {
        Result.success(client.fetch(location))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

private const val GEM_HOURLY_MATCH_TOLERANCE_MILLIS = 45 * 60 * 1_000L

private fun ParsedGemWeather.withSupplementalPrecipitation(
    supplemental: ParsedGemWeather,
): ParsedGemWeather {
    val forecast = forecast
    val supplementalForecast = supplemental.forecast
    val hourly = mergeHourlyPrecipitation(forecast.hourly, supplementalForecast.hourly)
    val daily = forecast.daily.mapIndexed { index, day ->
        val supplementalDay = supplementalForecast.daily.getOrNull(index)
        val dayHourly = mergeHourlyPrecipitation(
            day.hourly,
            supplementalDay?.hourly.orEmpty(),
        )
        if (supplementalDay == null) {
            day.copy(hourly = dayHourly)
        } else {
            day.copy(
                rainfallInches = if (day.rainfallAmountAvailable) {
                    day.rainfallInches
                } else {
                    supplementalRainfallAmount(
                        precipitationChance = day.precipitationChance,
                        precipitationChanceAvailable = day.precipitationChanceAvailable,
                        supplementalAmount = supplementalDay.rainfallInches,
                    )
                },
                rainfallAmountAvailable = day.rainfallAmountAvailable ||
                    supplementalDay.rainfallAmountAvailable,
                hourly = dayHourly,
            )
        }
    }
    val expectedRainDay = daily.firstOrNull()?.takeIf { it.rainfallAmountAvailable }
    val precipitation24h = hourly
        .takeIf { rows -> rows.isNotEmpty() && rows.all { it.rainfallAmountAvailable } }
        ?.filterIndexed { index, _ -> index % 3 == 0 }
        ?.map { hour -> ChartPoint(hour.time, hour.rainfallInches.toFloat()) }
        .orEmpty()
    val rainfallOutlook = daily
        .takeIf { days -> days.isNotEmpty() && days.all { it.rainfallAmountAvailable } }
        ?.map { day -> ChartPoint(day.day, day.rainfallInches.toFloat()) }
        .orEmpty()

    return copy(
        forecast = forecast.copy(
            currentPrecipitationInches = supplementalForecast.currentPrecipitationInches,
            expectedRainInches = expectedRainDay?.rainfallInches
                ?: forecast.expectedRainInches,
            expectedRainAmountAvailable = expectedRainDay != null ||
                forecast.expectedRainAmountAvailable,
            hourly = hourly,
            precipitation24h = precipitation24h,
            daily = daily,
            rainfallOutlook = rainfallOutlook,
        ).withAmountBasedRainStart(),
    )
}

private fun supplementalRainfallAmount(
    precipitationChance: Int,
    precipitationChanceAvailable: Boolean,
    supplementalAmount: Double,
): Double = if (precipitationChanceAvailable && precipitationChance <= 0) {
    0.0
} else {
    supplementalAmount
}

private fun DashboardForecast.withAmountBasedRainStart(): DashboardForecast {
    val currentTimeEpochMillis = hourly.firstOrNull()?.timeEpochMillis
    val rainStartsAt = when {
        currentTimeEpochMillis != null &&
            condition.isRainBearing() &&
            currentPrecipitationChanceAllowsRain() -> currentTimeEpochMillis
        else -> hourly.asSequence()
            .drop(1)
            .mapNotNull { hour -> hour.timeEpochMillis?.let { it to hour } }
            .firstOrNull { (timeEpochMillis, hour) ->
                timeEpochMillis >= (currentTimeEpochMillis ?: Long.MIN_VALUE) &&
                    hour.rainfallAmountAvailable &&
                    hour.precipitationChanceAllowsRain() &&
                    hasMinimumRainStartAmount(hour.rainfallInches)
            }
            ?.first
    }
    val rainStartsIn = when {
        rainStartsAt == null -> "No rain expected"
        currentTimeEpochMillis == null || rainStartsAt <= currentTimeEpochMillis -> "Now"
        else -> formatRainStartCountdown(
            ((rainStartsAt - currentTimeEpochMillis + 59_999L) / 60_000L)
                .coerceAtLeast(0L),
        )
    }
    return copy(
        rainStartsIn = rainStartsIn,
        rainStartsAtEpochMillis = rainStartsAt,
        rainStartSource = if (rainStartsAt == null) {
            RainStartSource.NONE
        } else {
            RainStartSource.ECCC_FORECAST
        },
        rainStartConfidenceMeaningful = false,
    )
}

private fun mergeHourlyPrecipitation(
    primary: List<HourlyForecast>,
    supplemental: List<HourlyForecast>,
): List<HourlyForecast> = primary.map { hour ->
    if (hour.rainfallAmountAvailable) return@map hour
    val primaryTimestamp = hour.timeEpochMillis ?: return@map hour
    val match = supplemental
        .asSequence()
        .filter { it.rainfallAmountAvailable }
        .mapNotNull { candidate ->
            candidate.timeEpochMillis?.let { it to candidate }
        }
        .minByOrNull { (timestamp, _) -> abs(timestamp - primaryTimestamp) }
        ?: return@map hour
    if (abs(match.first - primaryTimestamp) > GEM_HOURLY_MATCH_TOLERANCE_MILLIS) {
        hour
    } else {
        hour.copy(
            rainfallInches = supplementalRainfallAmount(
                precipitationChance = hour.precipitationChance,
                precipitationChanceAvailable = hour.precipitationChanceAvailable,
                supplementalAmount = match.second.rainfallInches,
            ),
            rainfallAmountAvailable = true,
        )
    }
}

internal object EcccWeatherParser {
    private val hourFormatter = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
    private val clockFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

    fun parse(json: String, location: WeatherLocation): ParsedGemWeather {
        val root = runCatching { JSONTokener(json).nextValue() as? JSONObject }
            .getOrNull()
            ?: throw EcccWeatherDataException("ECCC returned an invalid JSON document.")
        val feature = selectFeature(root, location)
            ?: throw EcccWeatherDataException("ECCC returned no forecast for this location.")
        val properties = feature.optJSONObject("properties")
            ?: throw EcccWeatherDataException("ECCC returned a feature without weather data.")
        val current = properties.optJSONObject("currentConditions")
            ?: throw EcccWeatherDataException("ECCC returned no current conditions.")
        val hourlyGroup = properties.optJSONObject("hourlyForecastGroup")
            ?: throw EcccWeatherDataException("ECCC returned no hourly forecast.")
        val hourlyArray = hourlyGroup.optJSONArray("hourlyForecasts")
            ?: throw EcccWeatherDataException("ECCC returned no hourly forecast periods.")

        val timezone = properties.localizedText("timezone")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ecccTimezoneForLocation(location)
        val currentInstant = parseTimestamp(current.localizedText("timestamp"))
        val currentTime = currentInstant.atZone(timezone)
        val currentTemperatureC = current.measureNumber("temperature")
            ?: throw EcccWeatherDataException("ECCC returned no current temperature.")
        val currentCondition = conditionForEcccText(current.localizedText("condition"))
        val currentWind = parseWind(current.optJSONObject("wind"))
        val actualHourly = parseHourly(hourlyArray, timezone)
        if (actualHourly.isEmpty()) throw EcccWeatherDataException("ECCC returned no usable hourly data.")

        val nearestHourly = actualHourly.minByOrNull {
            abs(Duration.between(currentTime, it.time).toMinutes())
        } ?: actualHourly.first()
        val currentRecord = EcccHourRecord(
            time = currentTime,
            precipitationChance = nearestHourly.precipitationChance,
            precipitationChanceAvailable = nearestHourly.precipitationChanceAvailable,
            temperatureFahrenheit = celsiusToFahrenheit(currentTemperatureC),
            windMph = currentWind.speedMph,
            windDirection = currentWind.direction,
            condition = currentCondition.first,
            conditionLabel = currentCondition.second,
        )
        val visibleRecords = buildList {
            add(currentRecord)
            actualHourly.filter { it.time.isAfter(currentTime) }.forEach(::add)
        }
        val hourlyForecast = visibleRecords.take(24).mapIndexed(::toHourlyForecast)
        if (hourlyForecast.isEmpty()) throw EcccWeatherDataException("ECCC returned no visible hourly data.")

        val sunriseTime = properties.optJSONObject("riseSet")
            ?.localizedText("sunrise")
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseTimestamp)
            ?.atZone(timezone)
        val sunrise = sunriseTime?.format(clockFormatter)
            .orEmpty()
        val sunsetTime = properties.optJSONObject("riseSet")
            ?.localizedText("sunset")
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseTimestamp)
            ?.atZone(timezone)
        val sunset = sunsetTime?.format(clockFormatter)
            .orEmpty()
        val isDay = isDay(currentTime, sunriseTime, sunsetTime)

        val dailyPeriods = parseDailyPeriods(
            forecastGroup = properties.optJSONObject("forecastGroup"),
            baseDate = currentTime.toLocalDate(),
        )
        val dailyForecast = buildDailyForecast(
            periods = dailyPeriods,
            records = visibleRecords,
            currentDate = currentTime.toLocalDate(),
            currentTemperatureFahrenheit = currentRecord.temperatureFahrenheit,
            sunrise = sunrise,
            sunset = sunset,
        )
        val firstDay = dailyForecast.firstOrNull()
        val chartRecords = visibleRecords.take(24).filterIndexed { index, _ -> index % 3 == 0 }
        val windChart = chartRecords.mapIndexed { index, record ->
            ChartPoint(
                label = if (index == 0) "Now" else formatHour(record.time),
                value = record.windMph.toFloat(),
            )
        }
        val firstDailyHigh = firstDay?.highFahrenheit ?: visibleRecords.maxOf { it.temperatureFahrenheit }
        val firstDailyLow = firstDay?.lowFahrenheit ?: visibleRecords.minOf { it.temperatureFahrenheit }
        val firstDailyChance = firstDay?.precipitationChance ?: currentRecord.precipitationChance
        val firstDailyChanceAvailable = firstDay?.precipitationChanceAvailable
            ?: currentRecord.precipitationChanceAvailable
        val rainStartsAt = currentTime.takeIf {
            currentCondition.first.isRainBearing() &&
                precipitationChanceAllowsRain(
                    currentRecord.precipitationChance,
                    currentRecord.precipitationChanceAvailable,
                ) &&
                precipitationChanceAllowsRain(firstDailyChance, firstDailyChanceAvailable)
        }
        val peakWind = firstDay?.let {
            EcccWind(it.peakWindMph, it.peakWindDirection)
        } ?: visibleRecords.maxByOrNull { it.windMph }?.let {
            EcccWind(it.windMph, it.windDirection)
        } ?: EcccWind(currentWind.speedMph, currentWind.direction)

        return ParsedGemWeather(
            forecast = DashboardForecast(
                location = location.label,
                condition = currentCondition.first,
                isDay = isDay,
                rainStartsIn = if (rainStartsAt == null) "No rain expected" else "Now",
                currentFahrenheit = currentRecord.temperatureFahrenheit,
                feelsLikeFahrenheit = celsiusToFahrenheit(
                    current.measureNumber("humidex") ?: currentTemperatureC,
                ),
                highFahrenheit = firstDailyHigh,
                lowFahrenheit = firstDailyLow,
                conditionLabel = currentCondition.second,
                precipitationChance = firstDailyChance,
                precipitationChanceAvailable = firstDailyChanceAvailable,
                currentPrecipitationInches = 0.0,
                expectedRainInches = 0.0,
                expectedRainAmountAvailable = false,
                peakWindMph = peakWind.speedMph,
                peakWindDirection = peakWind.direction,
                peakWindTime = firstDay?.peakWindTime.orEmpty(),
                hourly = hourlyForecast,
                precipitation24h = emptyList(),
                windByHour = windChart,
                daily = dailyForecast,
                rainfallOutlook = emptyList(),
                sunrise = sunrise,
                sunset = sunset,
                dryWindow = dryWindow(visibleRecords),
                rainStartsAtEpochMillis = rainStartsAt?.toInstant()?.toEpochMilli(),
                rainStartSource = if (rainStartsAt == null) {
                    RainStartSource.NONE
                } else {
                    RainStartSource.ECCC_FORECAST
                },
                source = ForecastSource.ECCC,
            ),
            timezone = timezone.id,
        )
    }

    private data class EcccHourRecord(
        val time: ZonedDateTime,
        val precipitationChance: Int,
        val precipitationChanceAvailable: Boolean,
        val temperatureFahrenheit: Int,
        val windMph: Int,
        val windDirection: String,
        val condition: WeatherCondition,
        val conditionLabel: String,
    )

    private data class EcccWind(
        val speedMph: Int,
        val direction: String,
    )

    private data class EcccDailyPeriod(
        val date: LocalDate,
        val isNight: Boolean,
        val condition: Pair<WeatherCondition, String>,
        val precipitationChance: Int?,
        val highFahrenheit: Int?,
        val lowFahrenheit: Int?,
        val peakWindMph: Int,
        val peakWindDirection: String,
    )

    private fun selectFeature(root: JSONObject, location: WeatherLocation): JSONObject? {
        if (root.optJSONObject("properties") != null) return root
        val features = root.optJSONArray("features") ?: return null
        return (0 until features.length())
            .asSequence()
            .mapNotNull { features.optJSONObject(it) }
            .filter { it.optJSONObject("properties")?.optJSONObject("hourlyForecastGroup") != null }
            .minByOrNull { featureDistanceSquared(it, location) }
    }

    private fun featureDistanceSquared(feature: JSONObject, location: WeatherLocation): Double {
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
            ?: return Double.MAX_VALUE
        val longitude = coordinates.optDouble(0, Double.NaN)
        val latitude = coordinates.optDouble(1, Double.NaN)
        if (!longitude.isFinite() || !latitude.isFinite()) return Double.MAX_VALUE
        val latitudeDistance = latitude - location.latitude
        val longitudeDistance = longitude - location.longitude
        return latitudeDistance * latitudeDistance + longitudeDistance * longitudeDistance
    }

    private fun parseHourly(array: JSONArray, timezone: ZoneId): List<EcccHourRecord> = buildList {
        for (index in 0 until array.length()) {
            val hourly = array.optJSONObject(index) ?: continue
            val timestamp = hourly.localizedText("timestamp").takeIf { it.isNotBlank() } ?: continue
            val temperatureC = hourly.measureNumber("temperature") ?: continue
            val condition = conditionForEcccText(hourly.localizedText("condition"))
            val wind = parseWind(hourly.optJSONObject("wind"))
            val chance = hourly.optJSONObject("lop")?.localizedDouble("value")
                ?: hourly.localizedDouble("lop")
            add(
                EcccHourRecord(
                    time = parseTimestamp(timestamp).atZone(timezone),
                    precipitationChance = chance?.roundToInt()?.coerceIn(0, 100) ?: 0,
                    precipitationChanceAvailable = chance != null,
                    temperatureFahrenheit = celsiusToFahrenheit(temperatureC),
                    windMph = wind.speedMph,
                    windDirection = wind.direction,
                    condition = condition.first,
                    conditionLabel = condition.second,
                ),
            )
        }
    }.sortedBy { it.time }

    private fun parseDailyPeriods(
        forecastGroup: JSONObject?,
        baseDate: LocalDate,
    ): List<EcccDailyPeriod> {
        val forecasts = forecastGroup?.optJSONArray("forecasts") ?: return emptyList()
        return buildList {
            for (index in 0 until forecasts.length()) {
                val forecast = forecasts.optJSONObject(index) ?: continue
                val period = forecast.optJSONObject("period") ?: continue
                val periodValue = period.localizedText("value")
                val periodName = period.localizedText("textForecastName").ifBlank { periodValue }
                val date = resolvePeriodDate(periodValue, periodName, baseDate) ?: continue
                val isNight = periodName.lowercase(Locale.ROOT).contains("night") ||
                    periodName.lowercase(Locale.ROOT).contains("tonight")
                val conditionText = forecast.optJSONObject("abbreviatedForecast")
                    ?.localizedText("textSummary")
                    ?.takeIf { it.isNotBlank() }
                    ?: forecast.optJSONObject("cloudPrecip")?.localizedText("textSummary").orEmpty()
                val temperatureValues = forecast.optJSONObject("temperatures")
                    ?.optJSONArray("temperature")
                    ?.let(::parseTemperatureValues)
                    .orEmpty()
                val winds = parseDailyWinds(forecast.optJSONObject("winds"))
                val summary = listOf(
                    conditionText,
                    forecast.optJSONObject("textSummary")?.localizedText("text"),
                ).filterNotNull().joinToString(" ")
                add(
                    EcccDailyPeriod(
                        date = date,
                        isNight = isNight,
                        condition = conditionForEcccText(conditionText),
                        precipitationChance = extractPercent(summary),
                        highFahrenheit = temperatureValues
                            .firstOrNull { it.first.contains("high") }
                            ?.second,
                        lowFahrenheit = temperatureValues
                            .firstOrNull { it.first.contains("low") }
                            ?.second,
                        peakWindMph = winds.maxByOrNull { it.first }?.first ?: 0,
                        peakWindDirection = winds.maxByOrNull { it.first }?.second.orEmpty(),
                    ),
                )
            }
        }
    }

    private fun parseTemperatureValues(array: JSONArray): List<Pair<String, Int>> = buildList {
        for (index in 0 until array.length()) {
            val value = array.optJSONObject(index) ?: continue
            val className = value.localizedText("class").lowercase(Locale.ROOT)
            val temperatureC = value.localizedDouble("value") ?: continue
            add(className to celsiusToFahrenheit(temperatureC))
        }
    }

    private fun parseDailyWinds(winds: JSONObject?): List<Pair<Int, String>> {
        val periods = winds?.optJSONArray("periods") ?: return emptyList()
        return buildList {
            for (index in 0 until periods.length()) {
                val period = periods.optJSONObject(index) ?: continue
                val speedKmh = period.optJSONObject("speed")?.localizedDouble("value") ?: continue
                val direction = period.localizedText("direction")
                    .ifBlank {
                        period.optJSONObject("bearing")?.localizedDouble("value")
                            ?.let(::compassDirection).orEmpty()
                    }
                add(kmhToMph(speedKmh) to direction)
            }
        }
    }

    private fun buildDailyForecast(
        periods: List<EcccDailyPeriod>,
        records: List<EcccHourRecord>,
        currentDate: LocalDate,
        currentTemperatureFahrenheit: Int,
        sunrise: String,
        sunset: String,
    ): List<DailyForecast> {
        val periodsByDate = periods.groupBy { it.date }
        val recordDates = records.map { it.time.toLocalDate() }.distinct()
        val dates = (periodsByDate.keys + recordDates)
            .filter { !it.isBefore(currentDate) }
            .distinct()
            .sorted()
            .take(7)
            .ifEmpty { listOf(currentDate) }

        return dates.mapIndexed { index, date ->
            val datePeriods = periodsByDate[date].orEmpty()
            val dayPeriod = datePeriods.firstOrNull { !it.isNight }
            val nightPeriod = datePeriods.firstOrNull { it.isNight }
            val dayRecords = records.filter { it.time.toLocalDate() == date }
            val fallbackCondition = dayRecords.firstOrNull()?.let {
                it.condition to it.conditionLabel
            } ?: (WeatherCondition.OVERCAST to "Overcast")
            val condition = dayPeriod?.condition ?: nightPeriod?.condition ?: fallbackCondition
            val chanceValues = datePeriods.mapNotNull { it.precipitationChance } +
                dayRecords.mapNotNull { record ->
                    record.precipitationChance.takeIf { record.precipitationChanceAvailable }
                }
            val high = dayPeriod?.highFahrenheit
                ?: dayRecords.maxOfOrNull { it.temperatureFahrenheit }
                ?: currentTemperatureFahrenheit
            val low = nightPeriod?.lowFahrenheit
                ?: dayRecords.minOfOrNull { it.temperatureFahrenheit }
                ?: currentTemperatureFahrenheit
            val peakPeriod = datePeriods.maxByOrNull { it.peakWindMph }
            val peakRecord = dayRecords.maxByOrNull { it.windMph }
            val peakWindMph = peakPeriod?.peakWindMph?.takeIf { it > 0 }
                ?: peakRecord?.windMph
                ?: 0
            val peakWindDirection = peakPeriod?.peakWindDirection.orEmpty()
                .ifBlank { peakRecord?.windDirection.orEmpty() }
            DailyForecast(
                day = if (index == 0) "Today" else date.format(weekdayFormatter),
                condition = condition.first,
                conditionLabel = condition.second,
                precipitationChance = chanceValues.maxOrNull() ?: 0,
                precipitationChanceAvailable = chanceValues.isNotEmpty(),
                rainfallInches = 0.0,
                rainfallAmountAvailable = false,
                highFahrenheit = high,
                lowFahrenheit = low,
                sunrise = if (index == 0) sunrise else "",
                sunset = if (index == 0) sunset else "",
                peakWindMph = peakWindMph,
                peakWindDirection = peakWindDirection,
                peakWindTime = peakRecord?.let { formatHour(it.time) }.orEmpty(),
                dryWindow = dryWindow(dayRecords),
                hourly = dayRecords.take(24).mapIndexed { recordIndex, record ->
                    toHourlyForecast(
                        index = if (date == currentDate) recordIndex else recordIndex + 1,
                        record = record,
                    )
                },
            )
        }
    }

    private fun toHourlyForecast(index: Int, record: EcccHourRecord): HourlyForecast = HourlyForecast(
        time = if (index == 0) "Now" else formatHour(record.time),
        precipitationChance = record.precipitationChance,
        precipitationChanceAvailable = record.precipitationChanceAvailable,
        rainfallInches = 0.0,
        rainfallAmountAvailable = false,
        temperatureFahrenheit = record.temperatureFahrenheit,
        windMph = record.windMph,
        windDirection = record.windDirection,
        windDirectionLabel = record.windDirection,
        condition = record.condition,
        conditionLabel = record.conditionLabel,
        timeEpochMillis = record.time.toInstant().toEpochMilli(),
    )

    private fun parseWind(wind: JSONObject?): EcccWind {
        val speedKmh = wind?.optJSONObject("speed")?.localizedDouble("value")
            ?: wind?.localizedDouble("speed")
            ?: 0.0
        val direction = wind?.localizedText("direction").orEmpty()
        return EcccWind(kmhToMph(speedKmh), direction)
    }

    private fun dryWindow(records: List<EcccHourRecord>): String {
        if (records.isEmpty()) return "No clear dry window"
        var bestStart = -1
        var bestEnd = -1
        var currentStart = -1
        records.indices.forEach { index ->
            val isDry = records[index].precipitationChanceAvailable &&
                records[index].precipitationChance <= 20
            if (isDry && currentStart == -1) currentStart = index
            if ((!isDry || index == records.lastIndex) && currentStart != -1) {
                val end = if (isDry) index else index - 1
                if (bestStart == -1 || end - currentStart > bestEnd - bestStart) {
                    bestStart = currentStart
                    bestEnd = end
                }
                currentStart = -1
            }
        }
        if (bestStart == -1) return "No clear dry window"
        return "${formatHour(records[bestStart].time)} – " +
            formatHour(records[bestEnd].time.plusHours(1))
    }

    private fun isDay(
        currentTime: ZonedDateTime,
        sunrise: ZonedDateTime?,
        sunset: ZonedDateTime?,
    ): Boolean {
        if (sunrise == null || sunset == null) {
            return currentTime.hour in 6..19
        }
        return !currentTime.isBefore(sunrise) && currentTime.isBefore(sunset)
    }

    private fun resolvePeriodDate(
        periodValue: String,
        periodName: String,
        baseDate: LocalDate,
    ): LocalDate? {
        val lowerName = periodName.lowercase(Locale.ROOT)
        if (lowerName.contains("today") || lowerName.contains("tonight")) return baseDate
        val weekday = DayOfWeek.values().firstOrNull {
            it.name.lowercase(Locale.ROOT).startsWith(periodValue.lowercase(Locale.ROOT).take(3))
        } ?: return null
        return (0L..7L).asSequence()
            .map { baseDate.plusDays(it) }
            .firstOrNull { it.dayOfWeek == weekday }
    }

    private fun extractPercent(value: String): Int? = Regex("(\\d{1,3})\\s*(?:percent|%)", RegexOption.IGNORE_CASE)
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.coerceIn(0, 100)

    internal fun conditionForEcccText(value: String): Pair<WeatherCondition, String> {
        val label = value.trim().trimEnd('.').ifBlank { "Overcast" }
        val lower = label.lowercase(Locale.ROOT)
        val condition = when {
            "severe thunderstorm" in lower -> WeatherCondition.SEVERE_WEATHER
            "thunderstorm" in lower -> WeatherCondition.THUNDERSTORM
            "freezing rain" in lower ||
                "freezing drizzle" in lower ||
                "ice pellet" in lower ||
                "wintry mix" in lower ||
                "mixed precipitation" in lower ||
                lower.containsBoth("rain", "snow") ->
                WeatherCondition.WINTRY_MIX
            "heavy rain" in lower || "heavy shower" in lower -> WeatherCondition.HEAVY_RAIN
            "drizzle" in lower -> WeatherCondition.DRIZZLE
            "heavy snow" in lower || "snow squall" in lower || "blizzard" in lower ->
                WeatherCondition.HEAVY_SNOW
            "rain" in lower || "shower" in lower -> WeatherCondition.RAIN
            "snow" in lower || "flurr" in lower || "ice crystal" in lower ->
                WeatherCondition.SNOW
            "fog" in lower || "mist" in lower -> WeatherCondition.FOG
            "haze" in lower || "smoke" in lower || "blowing dust" in lower ->
                WeatherCondition.ATMOSPHERIC_HAZE
            "mainly sunny" in lower ||
                "mainly clear" in lower ||
                "mostly sunny" in lower ||
                "mostly clear" in lower ||
                "a few clouds" in lower -> WeatherCondition.MOSTLY_CLEAR
            "partly" in lower ||
                "mix of sun" in lower ||
                "sunny break" in lower ||
                "cloudy period" in lower -> WeatherCondition.PARTLY_CLOUDY
            "clear" in lower || "sunny" in lower -> WeatherCondition.CLEAR
            "mostly cloudy" in lower || "cloudy" in lower || "overcast" in lower ->
                WeatherCondition.OVERCAST
            else -> WeatherCondition.OVERCAST
        }
        return condition to label
    }

    private fun String.containsBoth(first: String, second: String): Boolean =
        first in this && second in this

    private fun formatHour(time: ZonedDateTime): String = time.format(hourFormatter)

    private fun celsiusToFahrenheit(value: Double): Int = (value * 9.0 / 5.0 + 32.0).roundToInt()

    private fun kmhToMph(value: Double): Int = (value / 1.60934).roundToInt().coerceAtLeast(0)

    private fun compassDirection(degrees: Double): String {
        val directions = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        return directions[(normalized / 22.5).roundToInt() % directions.size]
    }

    private fun parseTimestamp(value: String): Instant = runCatching {
        Instant.parse(value)
    }.recoverCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneOffset.UTC)
            .toInstant()
    }.getOrElse {
        throw EcccWeatherDataException("ECCC returned an invalid timestamp: $value")
    }

    private fun ecccTimezoneForLocation(location: WeatherLocation): ZoneId = when {
        location.longitude < -123.0 -> ZoneId.of("America/Vancouver")
        location.longitude < -114.0 -> ZoneId.of("America/Edmonton")
        location.longitude < -101.0 -> ZoneId.of("America/Winnipeg")
        location.longitude < -67.0 -> ZoneId.of("America/Toronto")
        location.longitude < -60.0 -> ZoneId.of("America/Halifax")
        else -> ZoneId.of("America/St_Johns")
    }

    private fun JSONObject.localizedText(name: String): String = localizedText(opt(name))

    private fun localizedText(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject -> {
            localizedText(value.opt("en")).ifBlank {
                localizedText(value.opt("value"))
            }
        }
        else -> value.toString()
    }

    private fun JSONObject.localizedDouble(name: String): Double? = localizedDouble(opt(name))

    private fun localizedDouble(value: Any?): Double? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> localizedDouble(value.opt("en")) ?: localizedDouble(value.opt("value"))
        is Number -> value.toDouble().takeIf(Double::isFinite)
        else -> value.toString().toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    private fun JSONObject.measureNumber(name: String): Double? =
        localizedDouble(opt(name)) ?: optJSONObject(name)?.localizedDouble("value")
}
