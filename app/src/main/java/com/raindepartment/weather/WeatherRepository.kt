package com.raindepartment.weather

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal val AustinLocation = WeatherLocation(
    latitude = 30.2672,
    longitude = -97.7431,
    label = "Austin, Texas",
)

internal data class WeatherUiState(
    val snapshot: WeatherSnapshot?,
    val isRefreshing: Boolean,
    val isStale: Boolean,
    val errorMessage: String?,
)

internal sealed interface RefreshResult {
    data class Updated(val snapshot: WeatherSnapshot) : RefreshResult
    data object Skipped : RefreshResult
    data class Failed(val message: String, val retryable: Boolean) : RefreshResult
}

internal interface WeatherCache {
    fun read(): WeatherSnapshot?
    fun write(snapshot: WeatherSnapshot)
}

internal class SharedPreferencesWeatherCache(context: Context) : WeatherCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        CACHE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun read(): WeatherSnapshot? = preferences.getString(CACHE_KEY, null)
        ?.let(WeatherSnapshotCodec::decode)

    override fun write(snapshot: WeatherSnapshot) {
        preferences.edit().putString(CACHE_KEY, WeatherSnapshotCodec.encode(snapshot)).apply()
    }

    private companion object {
        const val CACHE_PREFERENCES = "weather_cache"
        const val CACHE_KEY = "snapshot"
    }
}

internal class WeatherRepository(
    private val client: GemWeatherClient,
    private val cache: WeatherCache,
    private val locationProvider: WeatherLocationProvider,
    private val preferredLocation: () -> WeatherLocation? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val radarClient: EcccRadarClient = NoOpEcccRadarClient,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(initialState(cache.read()))
    val state: StateFlow<WeatherUiState> = mutableState.asStateFlow()

    suspend fun refresh(
        force: Boolean = false,
        updateLocation: Boolean = false,
        locationOverride: WeatherLocation? = null,
    ): RefreshResult = mutex.withLock {
        val previous = mutableState.value.snapshot ?: cache.read()
        val now = clock()
        if (!force && previous != null &&
            now - previous.fetchedAtEpochMillis <
            refreshCadenceMinutes(previous.forecast, now) * 60_000L
        ) {
            return@withLock RefreshResult.Skipped
        }

        mutableState.value = mutableState.value.copy(
            isRefreshing = true,
            errorMessage = null,
        )

        try {
            val savedLocation = preferredLocation()
            val location = when {
                locationOverride != null -> locationOverride
                updateLocation -> locationProvider.currentOrNull()
                    ?: savedLocation
                    ?: previous?.location
                    ?: throw LocationUnavailableException()
                savedLocation != null -> savedLocation
                previous != null -> previous.location
                else -> locationProvider.currentOrNull()
                    ?: throw LocationUnavailableException()
            }
            val parsed = client.fetch(location)
            val fetchedAt = clock()
            val baseForecast = parsed.forecast.copy(location = location.label)
            val forecast = applyRadarRainStartIfNeeded(
                forecast = baseForecast,
                location = location,
                nowEpochMillis = fetchedAt,
            )
            val snapshot = WeatherSnapshot(
                location = location,
                timezone = parsed.timezone,
                fetchedAtEpochMillis = fetchedAt,
                forecast = forecast,
            )
            cache.write(snapshot)
            mutableState.value = stateFor(snapshot, isRefreshing = false, errorMessage = null)
            RefreshResult.Updated(snapshot)
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(isRefreshing = false)
            throw cancelled
        } catch (error: Exception) {
            val retryable = when (error) {
                is GemDataException -> false
                is GemHttpException -> error.statusCode == 408 ||
                    error.statusCode == 429 ||
                    error.statusCode >= 500
                else -> true
            }
            val message = error.message?.takeIf(String::isNotBlank)
                ?: "Weather data could not be loaded."
            mutableState.value = mutableState.value.copy(
                isRefreshing = false,
                errorMessage = message,
                isStale = isStale(mutableState.value.snapshot),
            )
            RefreshResult.Failed(message, retryable)
        }
    }

    private suspend fun applyRadarRainStartIfNeeded(
        forecast: DashboardForecast,
        location: WeatherLocation,
        nowEpochMillis: Long,
    ): DashboardForecast {
        val modelRainStart = forecast.rainStartsAtEpochMillis
        if (modelRainStart == null ||
            modelRainStart > nowEpochMillis + RADAR_RAIN_WINDOW_MINUTES * 60_000L
        ) {
            return forecast
        }

        val radarRainStart = try {
            radarClient.findRainStart(location, nowEpochMillis)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return forecast

        val radarCondition = radarRainStart.currentRateMillimetersPerHour
            ?.let(::radarConditionForRainRate)
        val hourly = if (radarCondition == null) {
            forecast.hourly
        } else {
            forecast.hourly.mapIndexed { index, hour ->
                if (index == 0 || hour.time.equals("Now", ignoreCase = true)) {
                    hour.copy(
                        condition = radarCondition.first,
                        conditionLabel = radarCondition.second,
                    )
                } else {
                    hour
                }
            }
        }

        val radarRainStartMinutes =
            ((radarRainStart.startsAtEpochMillis - nowEpochMillis + 59_999L) / 60_000L)
                .coerceAtLeast(0L)

        return forecast.copy(
            condition = radarCondition?.first ?: forecast.condition,
            conditionLabel = radarCondition?.second ?: forecast.conditionLabel,
            rainStartsIn = if (radarRainStartMinutes == 0L) {
                "Now"
            } else {
                formatRainStartCountdown(radarRainStartMinutes)
            },
            hourly = hourly,
            rainStartsAtEpochMillis = radarRainStart.startsAtEpochMillis,
            rainStartSource = RainStartSource.ECCC_RADAR,
            rainStartConfidenceMeaningful = radarRainStart.confidenceMeaningful,
        )
    }

    private fun isStale(snapshot: WeatherSnapshot?): Boolean = snapshot != null &&
        clock() - snapshot.fetchedAtEpochMillis >= STALE_AGE_MS

    private fun initialState(snapshot: WeatherSnapshot?): WeatherUiState = stateFor(
        snapshot = snapshot,
        isRefreshing = false,
        errorMessage = null,
    )

    private fun stateFor(
        snapshot: WeatherSnapshot?,
        isRefreshing: Boolean,
        errorMessage: String?,
    ): WeatherUiState = WeatherUiState(
        snapshot = snapshot,
        isRefreshing = isRefreshing,
        isStale = isStale(snapshot),
        errorMessage = errorMessage,
    )

    private companion object {
        val STALE_AGE_MS = TimeUnit.HOURS.toMillis(6)
    }
}

private class LocationUnavailableException : IllegalStateException(
    "Current location is unavailable. Turn on Location or choose a city.",
)

internal object WeatherRepositoryFactory {
    fun create(context: Context): WeatherRepository = WeatherRepository(
        client = HttpGemWeatherClient(),
        radarClient = HttpEcccRadarClient(
            cache = FileEcccRadarMapCache(context.applicationContext),
        ),
        cache = SharedPreferencesWeatherCache(context.applicationContext),
        locationProvider = AndroidWeatherLocationProvider(context.applicationContext),
        preferredLocation = { WeatherPreferences.selectedLocation(context.applicationContext) },
    )
}

internal object WeatherSnapshotCodec {
    private const val VERSION = 2

    fun encode(snapshot: WeatherSnapshot): String = JSONObject().apply {
        put("version", VERSION)
        put("latitude", snapshot.location.latitude)
        put("longitude", snapshot.location.longitude)
        put("location", snapshot.location.label)
        put("timezone", snapshot.timezone)
        put("fetchedAt", snapshot.fetchedAtEpochMillis)
        put("forecast", encodeForecast(snapshot.forecast))
    }.toString()

    fun decode(json: String): WeatherSnapshot? = runCatching {
        val root = JSONObject(json)
        require(root.optInt("version") == VERSION)
        val location = WeatherLocation(
            latitude = root.getDouble("latitude"),
            longitude = root.getDouble("longitude"),
            label = root.getString("location"),
        )
        WeatherSnapshot(
            location = location,
            timezone = root.getString("timezone"),
            fetchedAtEpochMillis = root.getLong("fetchedAt"),
            forecast = decodeForecast(root.getJSONObject("forecast")),
        )
    }.getOrNull()

    private fun encodeForecast(forecast: DashboardForecast): JSONObject = JSONObject().apply {
        put("location", forecast.location)
        put("condition", forecast.condition.name)
        put("isDay", forecast.isDay)
        put("rainStartsIn", forecast.rainStartsIn)
        put("currentFahrenheit", forecast.currentFahrenheit)
        put("feelsLikeFahrenheit", forecast.feelsLikeFahrenheit)
        put("highFahrenheit", forecast.highFahrenheit)
        put("lowFahrenheit", forecast.lowFahrenheit)
        put("conditionLabel", forecast.conditionLabel)
        put("precipitationChance", forecast.precipitationChance)
        put("currentPrecipitationInches", forecast.currentPrecipitationInches)
        put("expectedRainInches", forecast.expectedRainInches)
        put("peakWindMph", forecast.peakWindMph)
        put("peakWindDirection", forecast.peakWindDirection)
        put("peakWindTime", forecast.peakWindTime)
        put("hourly", encodeHourly(forecast.hourly))
        put("precipitation24h", encodeChart(forecast.precipitation24h))
        put("windByHour", encodeChart(forecast.windByHour))
        put("daily", JSONArray().apply {
            forecast.daily.forEach { item ->
                put(JSONObject().apply {
                    put("day", item.day)
                    put("condition", item.condition.name)
                    put("conditionLabel", item.conditionLabel)
                    put("precipitationChance", item.precipitationChance)
                    put("rainfallInches", item.rainfallInches)
                    put("highFahrenheit", item.highFahrenheit)
                    put("lowFahrenheit", item.lowFahrenheit)
                    put("sunrise", item.sunrise)
                    put("sunset", item.sunset)
                    put("peakWindMph", item.peakWindMph)
                    put("peakWindDirection", item.peakWindDirection)
                    put("peakWindTime", item.peakWindTime)
                    put("dryWindow", item.dryWindow)
                    put("hourly", encodeHourly(item.hourly))
                })
            }
        })
        put("rainfallOutlook", encodeChart(forecast.rainfallOutlook))
        put("sunrise", forecast.sunrise)
        put("sunset", forecast.sunset)
        put("dryWindow", forecast.dryWindow)
        put("rainStartsAtEpochMillis", forecast.rainStartsAtEpochMillis)
        put("rainStartSource", forecast.rainStartSource.name)
        put("rainStartConfidenceMeaningful", forecast.rainStartConfidenceMeaningful)
    }

    private fun encodeChart(points: List<ChartPoint>): JSONArray = JSONArray().apply {
        points.forEach { point ->
            put(JSONObject().apply {
                put("label", point.label)
                put("value", point.value)
            })
        }
    }

    private fun decodeForecast(root: JSONObject): DashboardForecast = DashboardForecast(
        location = root.getString("location"),
        condition = WeatherCondition.valueOf(root.getString("condition")),
        isDay = root.getBoolean("isDay"),
        rainStartsIn = root.getString("rainStartsIn"),
        currentFahrenheit = root.getInt("currentFahrenheit"),
        feelsLikeFahrenheit = root.getInt("feelsLikeFahrenheit"),
        highFahrenheit = root.getInt("highFahrenheit"),
        lowFahrenheit = root.getInt("lowFahrenheit"),
        conditionLabel = root.getString("conditionLabel"),
        precipitationChance = root.getInt("precipitationChance"),
        currentPrecipitationInches = root.getDouble("currentPrecipitationInches"),
        expectedRainInches = root.getDouble("expectedRainInches"),
        peakWindMph = root.getInt("peakWindMph"),
        peakWindDirection = root.getString("peakWindDirection"),
        peakWindTime = root.getString("peakWindTime"),
        hourly = decodeHourly(root.getJSONArray("hourly")),
        precipitation24h = decodeChart(root.getJSONArray("precipitation24h")),
        windByHour = decodeChart(root.getJSONArray("windByHour")),
        daily = decodeDaily(root.getJSONArray("daily")),
        rainfallOutlook = decodeChart(root.getJSONArray("rainfallOutlook")),
        sunrise = root.getString("sunrise"),
        sunset = root.getString("sunset"),
        dryWindow = root.getString("dryWindow"),
        rainStartsAtEpochMillis = root.optionalLong("rainStartsAtEpochMillis"),
        rainStartSource = runCatching {
            RainStartSource.valueOf(root.optString("rainStartSource"))
        }.getOrDefault(RainStartSource.NONE),
        rainStartConfidenceMeaningful = root.optBoolean("rainStartConfidenceMeaningful", false),
    )

    private fun encodeHourly(hourly: List<HourlyForecast>): JSONArray = JSONArray().apply {
        hourly.forEach { item ->
            put(JSONObject().apply {
                put("time", item.time)
                put("precipitationChance", item.precipitationChance)
                put("rainfallInches", item.rainfallInches)
                put("temperatureFahrenheit", item.temperatureFahrenheit)
                put("windMph", item.windMph)
                put("windDirection", item.windDirection)
                put("windDirectionLabel", item.windDirectionLabel)
                put("condition", item.condition.name)
                put("conditionLabel", item.conditionLabel)
                put("timeEpochMillis", item.timeEpochMillis)
            })
        }
    }

    private fun decodeHourly(array: JSONArray): List<HourlyForecast> = buildList(array.length()) {
        for (index in 0 until array.length()) {
            val root = array.getJSONObject(index)
            add(
                HourlyForecast(
                    time = root.getString("time"),
                    precipitationChance = root.getInt("precipitationChance"),
                    rainfallInches = root.getDouble("rainfallInches"),
                    temperatureFahrenheit = root.getInt("temperatureFahrenheit"),
                    windMph = root.getInt("windMph"),
                    windDirection = root.getString("windDirection"),
                    windDirectionLabel = root.getString("windDirectionLabel"),
                    condition = runCatching {
                        WeatherCondition.valueOf(
                            root.optString("condition", WeatherCondition.OVERCAST.name),
                        )
                    }.getOrDefault(WeatherCondition.OVERCAST),
                    conditionLabel = root.optString("conditionLabel", "Overcast"),
                    timeEpochMillis = root.optionalLong("timeEpochMillis"),
                ),
            )
        }
    }

    private fun decodeDaily(array: JSONArray): List<DailyForecast> = buildList(array.length()) {
        for (index in 0 until array.length()) {
            val root = array.getJSONObject(index)
            add(
                DailyForecast(
                    day = root.getString("day"),
                    condition = WeatherCondition.valueOf(root.getString("condition")),
                    conditionLabel = root.getString("conditionLabel"),
                    precipitationChance = root.getInt("precipitationChance"),
                    rainfallInches = root.getDouble("rainfallInches"),
                    highFahrenheit = root.getInt("highFahrenheit"),
                    lowFahrenheit = root.getInt("lowFahrenheit"),
                    sunrise = root.optString("sunrise", ""),
                    sunset = root.optString("sunset", ""),
                    peakWindMph = root.optInt("peakWindMph", 0),
                    peakWindDirection = root.optString("peakWindDirection", ""),
                    peakWindTime = root.optString("peakWindTime", ""),
                    dryWindow = root.optString("dryWindow", ""),
                    hourly = root.optJSONArray("hourly")?.let(::decodeHourly).orEmpty(),
                ),
            )
        }
    }

    private fun decodeChart(array: JSONArray): List<ChartPoint> = buildList(array.length()) {
        for (index in 0 until array.length()) {
            val root = array.getJSONObject(index)
            add(ChartPoint(root.getString("label"), root.getDouble("value").toFloat()))
        }
    }

    private fun JSONObject.optionalLong(name: String): Long? = if (isNull(name)) {
        null
    } else {
        optLong(name).takeIf { has(name) }
    }
}
