package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class EcccWeatherParserTest {
    @Test
    fun parsesCurrentHourlyAndDailyForecastWithoutInventingRainAmounts() {
        val parsed = EcccWeatherParser.parse(
            json = """
                {
                  "type":"FeatureCollection",
                  "features":[{
                    "type":"Feature",
                    "geometry":{"type":"Point","coordinates":[-71.9013,45.4025]},
                    "properties":{
                      "currentConditions":{
                        "timestamp":{"en":"2026-08-03T10:14:00Z"},
                        "temperature":{"value":{"en":21.1}},
                        "condition":{"en":"Light Rain"},
                        "wind":{
                          "speed":{"value":{"en":17}},
                          "direction":{"value":{"en":"SSE"}}
                        },
                        "humidex":null
                      },
                      "riseSet":{
                        "sunrise":{"en":"2026-08-03T09:34:00Z"},
                        "sunset":{"en":"2026-08-04T00:13:00Z"}
                      },
                      "hourlyForecastGroup":{
                        "hourlyForecasts":[
                          {
                            "timestamp":"2026-08-03T11:00:00Z",
                            "condition":{"en":"Showers"},
                            "temperature":{"value":{"en":20}},
                            "lop":{"value":{"en":80}},
                            "wind":{
                              "speed":{"value":{"en":15}},
                              "direction":{"value":{"en":"SE"}}
                            }
                          },
                          {
                            "timestamp":"2026-08-03T12:00:00Z",
                            "condition":{"en":"Showers or thunderstorms"},
                            "temperature":{"value":{"en":21}},
                            "lop":{"value":{"en":70}},
                            "wind":{
                              "speed":{"value":{"en":15}},
                              "direction":{"value":{"en":"SSE"}}
                            }
                          }
                        ]
                      },
                      "forecastGroup":{
                        "forecasts":[
                          {
                            "period":{
                              "textForecastName":{"en":"Today"},
                              "value":{"en":"Monday"}
                            },
                            "temperatures":{
                              "temperature":[
                                {"class":{"en":"high"},"value":{"en":23}}
                              ]
                            },
                            "abbreviatedForecast":{
                              "textSummary":{"en":"Showers or thunderstorms"}
                            },
                            "winds":{
                              "periods":[
                                {
                                  "speed":{"value":{"en":15}},
                                  "direction":{"en":"SE"}
                                }
                              ]
                            }
                          },
                          {
                            "period":{
                              "textForecastName":{"en":"Tonight"},
                              "value":{"en":"Monday night"}
                            },
                            "temperatures":{
                              "temperature":[
                                {"class":{"en":"low"},"value":{"en":15}}
                              ]
                            },
                            "abbreviatedForecast":{
                              "textSummary":{"en":"A few showers"}
                            },
                            "winds":{"periods":[]}
                          }
                        ]
                      }
                    }
                  }]
                }
            """.trimIndent(),
            location = WeatherLocation(45.4025, -71.9013, "Sherbrooke, Quebec"),
        )

        val forecast = parsed.forecast
        assertEquals("America/Toronto", parsed.timezone)
        assertEquals(ForecastSource.ECCC, forecast.source)
        assertEquals(70, forecast.currentFahrenheit)
        assertEquals(WeatherCondition.RAIN, forecast.condition)
        assertEquals("Light Rain", forecast.conditionLabel)
        assertEquals(80, forecast.precipitationChance)
        assertTrue(forecast.precipitationChanceAvailable)
        assertFalse(forecast.expectedRainAmountAvailable)
        assertTrue(forecast.hourly.isNotEmpty())
        assertFalse(forecast.hourly.first().rainfallAmountAvailable)
        assertEquals(73, forecast.daily.first().highFahrenheit)
        assertEquals(59, forecast.daily.first().lowFahrenheit)
        assertFalse(forecast.daily.first().rainfallAmountAvailable)
        assertEquals(80, forecast.daily.first().precipitationChance)
        assertEquals(null, forecast.rainStartsAtEpochMillis)
        assertEquals(RainStartSource.NONE, forecast.rainStartSource)
    }

    @Test
    fun ecccForecastKeepsPrimaryWeatherAndUsesGemRainAmounts() = runBlocking {
        val timestamp = 1_000_000L
        val ecccHour = HourlyForecast(
            time = "Now",
            precipitationChance = 80,
            rainfallInches = 0.0,
            temperatureFahrenheit = 70,
            windMph = 10,
            windDirection = "E",
            windDirectionLabel = "E",
            condition = WeatherCondition.HEAVY_RAIN,
            conditionLabel = "Heavy rain",
            timeEpochMillis = timestamp,
            rainfallAmountAvailable = false,
        )
        val gemHour = ecccHour.copy(
            rainfallInches = 0.42,
            rainfallAmountAvailable = true,
            temperatureFahrenheit = 84,
            condition = WeatherCondition.PARTLY_CLOUDY,
            conditionLabel = "Partly cloudy",
        )
        val ecccDay = DashboardForecastTestData.forecast.daily.first().copy(
            rainfallInches = 0.0,
            rainfallAmountAvailable = false,
            hourly = listOf(ecccHour),
        )
        val gemDay = ecccDay.copy(
            rainfallInches = 0.68,
            rainfallAmountAvailable = true,
            hourly = listOf(gemHour),
        )
        val ecccForecast = DashboardForecastTestData.forecast.copy(
            condition = WeatherCondition.HEAVY_RAIN,
            conditionLabel = "Heavy rain",
            currentFahrenheit = 70,
            expectedRainInches = 0.0,
            expectedRainAmountAvailable = false,
            hourly = listOf(ecccHour),
            precipitation24h = emptyList(),
            daily = listOf(ecccDay),
            rainfallOutlook = emptyList(),
            source = ForecastSource.ECCC,
        )
        val gemForecast = DashboardForecastTestData.forecast.copy(
            expectedRainInches = 0.68,
            expectedRainAmountAvailable = true,
            hourly = listOf(gemHour),
            precipitation24h = listOf(ChartPoint("Now", 0.42f)),
            daily = listOf(gemDay),
            rainfallOutlook = listOf(ChartPoint("Today", 0.68f)),
        )
        val client = EcccFirstWeatherClient(
            primary = object : WeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(ecccForecast, "America/Toronto")
            },
            fallback = object : WeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(gemForecast, "America/Toronto")
            },
        )

        val forecast = client.fetch(AustinLocation).forecast

        assertEquals(ForecastSource.ECCC, forecast.source)
        assertEquals(WeatherCondition.HEAVY_RAIN, forecast.condition)
        assertEquals(70, forecast.currentFahrenheit)
        assertEquals(0.68, forecast.expectedRainInches, 0.0)
        assertTrue(forecast.expectedRainAmountAvailable)
        assertEquals(0.42, forecast.hourly.first().rainfallInches, 0.0)
        assertTrue(forecast.hourly.first().rainfallAmountAvailable)
        assertEquals(0.68, forecast.daily.first().rainfallInches, 0.0)
        assertTrue(forecast.daily.first().rainfallAmountAvailable)
        assertEquals(0.42f, forecast.precipitation24h.first().value, 0.0f)
    }

    @Test
    fun ecccForecastUsesMinimumMergedRainAmountForRainStart() = runBlocking {
        val now = 1_000_000L
        val primaryHourly = listOf(
            HourlyForecast(
                time = "Now",
                precipitationChance = 80,
                rainfallInches = 0.0,
                temperatureFahrenheit = 70,
                windMph = 10,
                windDirection = "E",
                windDirectionLabel = "E",
                timeEpochMillis = now,
                rainfallAmountAvailable = false,
            ),
            HourlyForecast(
                time = "4 PM",
                precipitationChance = 80,
                rainfallInches = 0.0,
                temperatureFahrenheit = 70,
                windMph = 10,
                windDirection = "E",
                windDirectionLabel = "E",
                timeEpochMillis = now + 60_000L,
                rainfallAmountAvailable = false,
            ),
            HourlyForecast(
                time = "5 PM",
                precipitationChance = 80,
                rainfallInches = 0.0,
                temperatureFahrenheit = 70,
                windMph = 10,
                windDirection = "E",
                windDirectionLabel = "E",
                timeEpochMillis = now + 2 * 60_000L,
                rainfallAmountAvailable = false,
            ),
        )
        val supplementalHourly = primaryHourly.mapIndexed { index, hour ->
            hour.copy(
                rainfallInches = when (index) {
                    0 -> 0.0
                    1 -> MINIMUM_RAIN_START_AMOUNT_INCHES / 2.0
                    else -> MINIMUM_RAIN_START_AMOUNT_INCHES
                },
                rainfallAmountAvailable = true,
            )
        }
        val primaryForecast = DashboardForecastTestData.forecast.copy(
            source = ForecastSource.ECCC,
            currentPrecipitationInches = 0.0,
            hourly = primaryHourly,
            rainStartsAtEpochMillis = null,
            rainStartSource = RainStartSource.NONE,
        )
        val supplementalForecast = primaryForecast.copy(
            source = ForecastSource.GEM,
            hourly = supplementalHourly,
        )
        val client = EcccFirstWeatherClient(
            primary = object : WeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(primaryForecast, "America/Toronto")
            },
            fallback = object : WeatherClient {
                override suspend fun fetch(location: WeatherLocation): ParsedGemWeather =
                    ParsedGemWeather(supplementalForecast, "America/Toronto")
            },
        )

        val forecast = client.fetch(AustinLocation).forecast

        assertEquals(now + 2 * 60_000L, forecast.rainStartsAtEpochMillis)
        assertEquals(RainStartSource.ECCC_FORECAST, forecast.rainStartSource)
        assertEquals("2 minutes", forecast.rainStartsIn)
    }
}
