package com.raindepartment.weather

import java.io.IOException
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
