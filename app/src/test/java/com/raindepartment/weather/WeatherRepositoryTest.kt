package com.raindepartment.weather

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRepositoryTest {
    @Test
    fun successfulRefreshWritesSnapshotAndUpdatesState() = runBlocking {
        val cache = FakeCache()
        val repository = WeatherRepository(
            client = FakeClient(),
            cache = cache,
            locationProvider = FakeLocationProvider(),
            clock = { 10_000L },
        )

        val result = repository.refresh(force = true, updateLocation = true)

        assertTrue(result is RefreshResult.Updated)
        assertEquals(AustinLocation, cache.value?.location)
        assertEquals(84, repository.state.value.snapshot?.forecast?.currentFahrenheit)
        assertEquals(false, repository.state.value.isRefreshing)
        assertEquals(null, repository.state.value.errorMessage)
    }

    @Test
    fun refreshSynchronizesANewerSnapshotWrittenByAnotherWorker() = runBlocking {
        val initial = WeatherSnapshot(
            location = AustinLocation,
            timezone = "America/Chicago",
            fetchedAtEpochMillis = 1L,
            forecast = DashboardForecastTestData.forecast,
        )
        val cache = FakeCache(initial)
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather {
                    throw AssertionError("A recent cached snapshot should skip the network refresh")
                }
            },
            cache = cache,
            locationProvider = FakeLocationProvider(),
            clock = { 100_000L },
        )
        val newer = initial.copy(fetchedAtEpochMillis = 99_000L)
        cache.value = newer

        val result = repository.refresh()

        assertTrue(result is RefreshResult.Skipped)
        assertEquals(newer, repository.state.value.snapshot)
    }

    @Test
    fun failedRefreshKeepsCachedSnapshotAndMarksError() = runBlocking {
        val cached = WeatherSnapshot(
            location = AustinLocation,
            timezone = "America/Chicago",
            fetchedAtEpochMillis = 1L,
            forecast = DashboardForecastTestData.forecast,
        )
        val cache = FakeCache(cached)
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    throw IOException("offline")
            },
            cache = cache,
            locationProvider = FakeLocationProvider(),
            clock = { 7 * 60 * 60 * 1_000L },
        )

        val result = repository.refresh(force = true)

        assertTrue(result is RefreshResult.Failed)
        assertEquals(cached, repository.state.value.snapshot)
        assertEquals("offline", repository.state.value.errorMessage)
        assertTrue(repository.state.value.isStale)
    }

    @Test
    fun unavailableCurrentLocationDoesNotSilentlyUseAustin() = runBlocking {
        var fetchCalled = false
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather {
                    fetchCalled = true
                    return ParsedGemWeather(DashboardForecastTestData.forecast, "America/Chicago")
                }
            },
            cache = FakeCache(),
            locationProvider = object : WeatherLocationProvider {
                override suspend fun currentOrNull(): WeatherLocation? = null
            },
        )

        val result = repository.refresh(force = true, updateLocation = true)

        assertTrue(result is RefreshResult.Failed)
        assertFalse(fetchCalled)
        assertNull(repository.state.value.snapshot)
        assertEquals(
            "Current location is unavailable. Turn on Location or choose a city.",
            repository.state.value.errorMessage,
        )
    }

    @Test
    fun unavailableCurrentLocationFallsBackToPreferredCity() = runBlocking {
        val city = WeatherCity("Denver, Colorado", 39.7392, -104.9903).location
        var requestedLocation: WeatherLocation? = null
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather {
                    requestedLocation = location
                    return ParsedGemWeather(DashboardForecastTestData.forecast, "America/Denver")
                }
            },
            cache = FakeCache(),
            locationProvider = object : WeatherLocationProvider {
                override suspend fun currentOrNull(): WeatherLocation? = null
            },
            preferredLocation = { city },
        )

        val result = repository.refresh(force = true, updateLocation = true)

        assertTrue(result is RefreshResult.Updated)
        assertEquals(city, requestedLocation)
        assertEquals(city, repository.state.value.snapshot?.location)
    }

    @Test
    fun availableCurrentLocationStillWinsOverPreferredCity() = runBlocking {
        val city = WeatherCity("Denver, Colorado", 39.7392, -104.9903).location
        var requestedLocation: WeatherLocation? = null
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather {
                    requestedLocation = location
                    return ParsedGemWeather(DashboardForecastTestData.forecast, "America/Chicago")
                }
            },
            cache = FakeCache(),
            locationProvider = object : WeatherLocationProvider {
                override suspend fun currentOrNull(): WeatherLocation = AustinLocation
            },
            preferredLocation = { city },
        )

        val result = repository.refresh(force = true, updateLocation = true)

        assertTrue(result is RefreshResult.Updated)
        assertEquals(AustinLocation, requestedLocation)
    }

    @Test
    fun explicitCityLocationIsUsedForRefresh() = runBlocking {
        val city = WeatherCity("Denver, Colorado", 39.7392, -104.9903)
        var requestedLocation: WeatherLocation? = null
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather {
                    requestedLocation = location
                    return ParsedGemWeather(DashboardForecastTestData.forecast, "America/Denver")
                }
            },
            cache = FakeCache(),
            locationProvider = object : WeatherLocationProvider {
                override suspend fun currentOrNull(): WeatherLocation? = null
            },
        )

        val result = repository.refresh(force = true, locationOverride = city.location)

        assertTrue(result is RefreshResult.Updated)
        assertEquals(city.location, requestedLocation)
        assertEquals(city.label, repository.state.value.snapshot?.forecast?.location)
    }

    @Test
    fun ecccRadarOverridesModelRainStartInsideThreeHourWindow() = runBlocking {
        val now = 10_000L
        val modelForecast = DashboardForecastTestData.forecast.copy(
            rainStartsAtEpochMillis = now + 2 * 60 * 60 * 1_000L,
            rainStartSource = RainStartSource.MODEL,
            hourly = listOf(
                DashboardForecastTestData.forecast.hourly.first().copy(
                    rainfallInches = MINIMUM_RAIN_START_AMOUNT_INCHES,
                    rainfallAmountAvailable = true,
                    timeEpochMillis = now + 12 * 60_000L,
                ),
            ),
        )
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(modelForecast, "America/Chicago")
            },
            cache = FakeCache(),
            locationProvider = FakeLocationProvider(),
            clock = { now },
            radarClient = object : EcccRadarClient {
                override suspend fun findRainStart(
                    location: WeatherLocation,
                    nowEpochMillis: Long,
                ): EcccRadarRainStart = EcccRadarRainStart(
                    startsAtEpochMillis = now + 12 * 60_000L,
                    confidenceMeaningful = true,
                )
            },
        )

        val result = repository.refresh(force = true)

        assertTrue(result is RefreshResult.Updated)
        val forecast = repository.state.value.snapshot!!.forecast
        assertEquals(RainStartSource.ECCC_RADAR, forecast.rainStartSource)
        assertEquals(now + 12 * 60_000L, forecast.rainStartsAtEpochMillis)
        assertEquals("12 minutes", forecast.rainStartsIn)
        assertTrue(forecast.rainStartConfidenceMeaningful)
    }

    @Test
    fun currentRadarRainOverridesModelConditionAndCurrentHourlyLabel() = runBlocking {
        val now = 10_000L
        val modelForecast = DashboardForecastTestData.forecast.copy(
            condition = WeatherCondition.OVERCAST,
            conditionLabel = "Overcast",
            rainStartsAtEpochMillis = now + 2 * 60 * 60 * 1_000L,
            rainStartSource = RainStartSource.MODEL,
            hourly = listOf(
                HourlyForecast(
                    time = "Now",
                    precipitationChance = 30,
                    rainfallInches = 0.0,
                    temperatureFahrenheit = 84,
                    windMph = 8,
                    windDirection = "N",
                    windDirectionLabel = "N",
                    condition = WeatherCondition.OVERCAST,
                    conditionLabel = "Overcast",
                    timeEpochMillis = now,
                    rainfallAmountAvailable = false,
                ),
            ),
        )
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(modelForecast, "America/Chicago")
            },
            cache = FakeCache(),
            locationProvider = FakeLocationProvider(),
            clock = { now },
            radarClient = object : EcccRadarClient {
                override suspend fun findRainStart(
                    location: WeatherLocation,
                    nowEpochMillis: Long,
                ): EcccRadarRainStart = EcccRadarRainStart(
                    startsAtEpochMillis = now - 6 * 60_000L,
                    confidenceMeaningful = true,
                    currentRateMillimetersPerHour = 8.0,
                )
            },
        )

        val result = repository.refresh(force = true)

        assertTrue(result is RefreshResult.Updated)
        val forecast = repository.state.value.snapshot!!.forecast
        assertEquals(WeatherCondition.HEAVY_RAIN, forecast.condition)
        assertEquals("Heavy rain", forecast.conditionLabel)
        assertEquals("Now", forecast.rainStartsIn)
        assertEquals(WeatherCondition.HEAVY_RAIN, forecast.hourly.first().condition)
        assertEquals("Heavy rain", forecast.hourly.first().conditionLabel)
    }

    @Test
    fun zeroRainChancePreventsRadarFromCountingRain() = runBlocking {
        val now = 10_000L
        val modelForecast = DashboardForecastTestData.forecast.copy(
            precipitationChance = 0,
            condition = WeatherCondition.OVERCAST,
            conditionLabel = "Overcast",
            hourly = listOf(
                DashboardForecastTestData.forecast.hourly.first().copy(
                    precipitationChance = 0,
                    condition = WeatherCondition.OVERCAST,
                    conditionLabel = "Overcast",
                ),
            ),
        )
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(modelForecast, "America/Chicago")
            },
            cache = FakeCache(),
            locationProvider = FakeLocationProvider(),
            clock = { now },
            radarClient = object : EcccRadarClient {
                override suspend fun findRainStart(
                    location: WeatherLocation,
                    nowEpochMillis: Long,
                ): EcccRadarRainStart = EcccRadarRainStart(
                    startsAtEpochMillis = now,
                    confidenceMeaningful = true,
                    currentRateMillimetersPerHour = 8.0,
                )
            },
        )

        val result = repository.refresh(force = true)

        assertTrue(result is RefreshResult.Updated)
        val forecast = repository.state.value.snapshot!!.forecast
        assertEquals(WeatherCondition.OVERCAST, forecast.condition)
        assertEquals(RainStartSource.NONE, forecast.rainStartSource)
        assertNull(forecast.rainStartsAtEpochMillis)
        assertFalse(forecast.isCurrentlyRaining(now))
    }

    @Test
    fun meaningfulRadarStartDoesNotDependOnHourlyModelAmount() = runBlocking {
        val now = 10_000L
        var radarCalls = 0
        val repository = WeatherRepository(
            client = object : GemWeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(
                        DashboardForecastTestData.forecast.copy(
                            rainStartsAtEpochMillis = null,
                            rainStartSource = RainStartSource.NONE,
                            hourly = listOf(
                                DashboardForecastTestData.forecast.hourly.first().copy(
                                    rainfallInches = 0.0,
                                    rainfallAmountAvailable = true,
                                    timeEpochMillis = now + 6 * 60_000L,
                                ),
                            ),
                        ),
                        "America/Chicago",
                    )
            },
            cache = FakeCache(),
            locationProvider = FakeLocationProvider(),
            clock = { now },
            radarClient = object : EcccRadarClient {
                override suspend fun findRainStart(
                    location: WeatherLocation,
                    nowEpochMillis: Long,
                ): EcccRadarRainStart {
                    radarCalls += 1
                    return EcccRadarRainStart(
                        startsAtEpochMillis = now + 6 * 60_000L,
                        confidenceMeaningful = true,
                    )
                }
            },
        )

        val result = repository.refresh(force = true)

        assertTrue(result is RefreshResult.Updated)
        assertEquals(1, radarCalls)
        assertEquals(
            RainStartSource.ECCC_RADAR,
            repository.state.value.snapshot?.forecast?.rainStartSource,
        )
        assertEquals(
            now + 6 * 60_000L,
            repository.state.value.snapshot?.forecast?.rainStartsAtEpochMillis,
        )
    }

    private class FakeCache(var value: WeatherSnapshot? = null) : WeatherCache {
        override fun read(): WeatherSnapshot? = value
        override fun write(snapshot: WeatherSnapshot) {
            value = snapshot
        }
    }

    private class FakeLocationProvider : WeatherLocationProvider {
        override suspend fun currentOrNull(): WeatherLocation = AustinLocation
    }

    private class FakeClient : GemWeatherClient {
        override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
            ParsedGemWeather(DashboardForecastTestData.forecast, "America/Chicago")
    }
}
