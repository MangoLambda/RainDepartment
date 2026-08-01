package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemWeatherParserTest {
    @Test
    fun parsesCurrentHourlyAndDailyGemData() {
        val parsed = GemWeatherParser.parse(FIXTURE, AustinLocation)
        val forecast = parsed.forecast

        assertEquals("America/Chicago", parsed.timezone)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, forecast.condition)
        assertEquals("Partly cloudy", forecast.conditionLabel)
        assertEquals(84, forecast.currentFahrenheit)
        assertEquals(87, forecast.feelsLikeFahrenheit)
        assertEquals(80, forecast.precipitationChance)
        assertEquals("1h", forecast.rainStartsIn)
        assertEquals(4, forecast.hourly.size)
        assertEquals("Now", forecast.hourly.first().time)
        assertEquals("SE", forecast.hourly[2].windDirectionLabel)
        assertEquals(2, forecast.daily.size)
        assertEquals(WeatherCondition.HEAVY_SNOW, forecast.daily[1].condition)
        assertEquals("12 PM – 1 PM", forecast.dryWindow)
        assertEquals("6:32 AM", forecast.sunrise)
        assertEquals("8:32 PM", forecast.sunset)
    }

    @Test
    fun rejectsApiErrorsAndMismatchedSeries() {
        val error = assertFails { GemWeatherParser.parse("{\"error\":true,\"reason\":\"bad request\"}", AustinLocation) }
        assertTrue(error.message!!.contains("bad request"))

        val mismatched = FIXTURE.replace(
            "\"wind_speed_10m\":[5,8,10,12,14]",
            "\"wind_speed_10m\":[8,10]",
        )
        assertFails { GemWeatherParser.parse(mismatched, AustinLocation) }
    }

    @Test
    fun requestUrlSelectsGEMSeamlessAndRequiredUnits() {
        val url = gemRequestUrl(AustinLocation)

        assertTrue(url.startsWith("https://api.open-meteo.com/v1/gem?"))
        assertTrue(url.contains("models=gem_seamless"))
        assertTrue(url.contains("forecast_days=7"))
        assertTrue(url.contains("temperature_unit=fahrenheit"))
        assertTrue(url.contains("wind_speed_unit=mph"))
        assertTrue(url.contains("precipitation_unit=inch"))
    }

    private fun assertFails(block: () -> Unit): Exception = try {
        block()
        throw AssertionError("Expected parser failure")
    } catch (error: Exception) {
        error
    }

    private companion object {
        val FIXTURE = """
            {
              "timezone":"America/Chicago",
              "current":{
                "time":"2026-08-01T12:00",
                "temperature_2m":84.2,
                "apparent_temperature":87.1,
                "is_day":1,
                "precipitation":0.0,
                "weather_code":2
              },
              "hourly":{
                "time":["2026-08-01T00:00","2026-08-01T12:00","2026-08-01T13:00","2026-08-01T14:00","2026-08-01T15:00"],
                "temperature_2m":[74,84,85,86,87],
                "precipitation_probability":[10,20,50,80,10],
                "precipitation":[0,0,0.1,0.2,0],
                "weather_code":[0,2,61,65,1],
                "wind_speed_10m":[5,8,10,12,14],
                "wind_direction_10m":[0,90,135,135,180]
              },
              "daily":{
                "time":["2026-08-01","2026-08-02"],
                "weather_code":[61,75],
                "temperature_2m_max":[89,82],
                "temperature_2m_min":[73,65],
                "sunrise":["2026-08-01T06:32","2026-08-02T06:33"],
                "sunset":["2026-08-01T20:32","2026-08-02T20:31"],
                "precipitation_sum":[0.68,0.32],
                "precipitation_probability_max":[80,70],
                "wind_speed_10m_max":[15,18],
                "wind_direction_10m_dominant":[112,90]
              }
            }
        """.trimIndent()
    }
}
