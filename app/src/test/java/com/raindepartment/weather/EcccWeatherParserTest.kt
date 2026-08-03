package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(forecast.rainStartsAtEpochMillis != null)
        assertEquals(RainStartSource.ECCC_FORECAST, forecast.rainStartSource)
    }
}
