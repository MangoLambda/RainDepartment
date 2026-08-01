package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherSnapshotCodecTest {
    @Test
    fun snapshotRoundTripsThroughCacheCodec() {
        val original = WeatherSnapshot(
            location = AustinLocation,
            timezone = "America/Chicago",
            fetchedAtEpochMillis = 1234L,
            forecast = DashboardForecastTestData.forecast,
        )

        val decoded = WeatherSnapshotCodec.decode(WeatherSnapshotCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun invalidCacheIsIgnored() {
        assertNull(WeatherSnapshotCodec.decode("not weather"))
    }
}

internal object DashboardForecastTestData {
    val forecast = DashboardForecast(
        location = "Austin, Texas",
        condition = WeatherCondition.PARTLY_CLOUDY,
        isDay = true,
        rainStartsIn = "1h",
        currentFahrenheit = 84,
        feelsLikeFahrenheit = 87,
        highFahrenheit = 89,
        lowFahrenheit = 73,
        conditionLabel = "Partly cloudy",
        precipitationChance = 80,
        currentPrecipitationInches = 0.0,
        expectedRainInches = 0.68,
        peakWindMph = 15,
        peakWindDirection = "ESE",
        peakWindTime = "2 PM",
        hourly = listOf(HourlyForecast("Now", 30, 0.0, 84, 8, "N", "N")),
        precipitation24h = listOf(ChartPoint("Now", 0.0f)),
        windByHour = listOf(ChartPoint("Now", 8.0f)),
        daily = listOf(DailyForecast("Today", WeatherCondition.RAIN, "Rain", 80, 0.68, 89, 73)),
        rainfallOutlook = listOf(ChartPoint("Today", 0.68f)),
        sunrise = "6:32 AM",
        sunset = "8:32 PM",
        dryWindow = "5 PM – 8 PM",
    )
}
