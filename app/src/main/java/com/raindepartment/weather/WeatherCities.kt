package com.raindepartment.weather

import java.util.Locale

internal data class WeatherCity(
    val label: String,
    val latitude: Double,
    val longitude: Double,
) {
    val location: WeatherLocation
        get() = WeatherLocation(latitude, longitude, label)
}

internal object WeatherCities {
    // Keep the picker useful without requiring another network request just to choose a city.
    // The list covers the locations most likely to be used by the first-run dashboard and can
    // be expanded independently of the weather API.
    val all: List<WeatherCity> = listOf(
        WeatherCity("Austin, Texas", 30.2672, -97.7431),
        WeatherCity("Atlanta, Georgia", 33.7490, -84.3880),
        WeatherCity("Boston, Massachusetts", 42.3601, -71.0589),
        WeatherCity("Chicago, Illinois", 41.8781, -87.6298),
        WeatherCity("Dallas, Texas", 32.7767, -96.7970),
        WeatherCity("Denver, Colorado", 39.7392, -104.9903),
        WeatherCity("Houston, Texas", 29.7604, -95.3698),
        WeatherCity("Los Angeles, California", 34.0522, -118.2437),
        WeatherCity("Miami, Florida", 25.7617, -80.1918),
        WeatherCity("Minneapolis, Minnesota", 44.9778, -93.2650),
        WeatherCity("New York, New York", 40.7128, -74.0060),
        WeatherCity("Philadelphia, Pennsylvania", 39.9526, -75.1652),
        WeatherCity("Phoenix, Arizona", 33.4484, -112.0740),
        WeatherCity("Portland, Oregon", 45.5152, -122.6784),
        WeatherCity("San Antonio, Texas", 29.4241, -98.4936),
        WeatherCity("San Diego, California", 32.7157, -117.1611),
        WeatherCity("San Francisco, California", 37.7749, -122.4194),
        WeatherCity("Seattle, Washington", 47.6062, -122.3321),
        WeatherCity("Washington, D.C.", 38.9072, -77.0369),
        WeatherCity("Vancouver, British Columbia", 49.2827, -123.1207),
        WeatherCity("Toronto, Ontario", 43.6532, -79.3832),
        WeatherCity("Mexico City, Mexico", 19.4326, -99.1332),
        WeatherCity("London, England", 51.5074, -0.1278),
        WeatherCity("Paris, France", 48.8566, 2.3522),
        WeatherCity("Tokyo, Japan", 35.6762, 139.6503),
        WeatherCity("Sydney, Australia", -33.8688, 151.2093),
        WeatherCity("Singapore, Singapore", 1.3521, 103.8198),
        WeatherCity("Mumbai, India", 19.0760, 72.8777),
    )

    fun search(query: String): List<WeatherCity> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return all
        return all.filter { city ->
            city.label.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
    }
}
