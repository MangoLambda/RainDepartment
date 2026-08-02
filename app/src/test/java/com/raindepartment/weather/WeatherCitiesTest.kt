package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCitiesTest {
    @Test
    fun catalogIsLargeAndDoesNotRepeatLabels() {
        assertTrue("Expected a broad global catalog", WeatherCities.all.size >= 800)
        assertEquals(
            "Every picker row should identify one city",
            WeatherCities.all.size,
            WeatherCities.all.map { it.label }.toSet().size,
        )
    }

    @Test
    fun catalogCoordinatesAreValid() {
        WeatherCities.all.forEach { city ->
            assertTrue(city.label, city.latitude in -90.0..90.0)
            assertTrue(city.label, city.longitude in -180.0..180.0)
        }
    }

    @Test
    fun searchIgnoresAccentsAndSupportsAliases() {
        assertTrue(WeatherCities.search("sao paulo").any { it.label == "São Paulo, Brazil" })
        assertTrue(WeatherCities.search("NYC").any { it.label == "New York, New York" })
        assertTrue(WeatherCities.search("reykjavik").any { it.label == "Reykjavík, Iceland" })
        assertTrue(WeatherCities.search("saint louis").any { it.label == "St. Louis, Missouri" })
    }

    @Test
    fun nearestCitiesFollowTheSelectedLocation() {
        val newYork = WeatherCities.all.first { it.label == "New York, New York" }
        val nearbyLabels = WeatherCities.nearestTo(newYork.location, limit = 4).map { it.label }

        assertTrue(nearbyLabels.contains("Jersey City, New Jersey"))
        assertTrue(nearbyLabels.contains("Newark, New Jersey"))
        assertTrue(nearbyLabels.contains("Stamford, Connecticut"))
        assertTrue(nearbyLabels.contains("Yonkers, New York"))
    }
}
