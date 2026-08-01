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
