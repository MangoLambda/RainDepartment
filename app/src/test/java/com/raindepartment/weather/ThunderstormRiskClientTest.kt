package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThunderstormRiskClientTest {
    @Test
    fun openMeteoParserReadsNumericRiskAndSkipsNullValues() {
        val points = parseOpenMeteoThunderstormRisk(
            json = """
                {
                  "hourly": {
                    "time": ["2026-08-02T14:00", "2026-08-02T15:00"],
                    "thunderstorm_probability": [18, null]
                  }
                }
            """.trimIndent(),
            timezone = "America/Toronto",
        )

        assertEquals(1, points.size)
        assertEquals(ThunderstormRiskValue.Percentage(18), points.single().value)
        assertTrue(points.single().timeEpochMillis > 0L)
    }

    @Test
    fun ecccParserUsesNearestCityAndExtractsThunderstormWording() {
        val points = parseEcccCityPageThunderstormRisk(
            json = """
                {
                  "features": [
                    {
                      "geometry": {"coordinates": [-72.65, 45.31]},
                      "properties": {
                        "hourlyForecastGroup": {
                          "hourlyForecasts": [
                            {
                              "timestamp": "2026-08-02T20:00:00Z",
                              "condition": {"en": "Chance of showers. Risk of thunderstorms."}
                            },
                            {
                              "timestamp": "2026-08-02T21:00:00Z",
                              "condition": {"en": "Clear"}
                            }
                          ]
                        }
                      }
                    },
                    {
                      "geometry": {"coordinates": [-73.8, 46.2]},
                      "properties": {
                        "hourlyForecastGroup": {
                          "hourlyForecasts": [
                            {
                              "timestamp": "2026-08-02T20:00:00Z",
                              "condition": {"en": "Risk of a thunderstorm"}
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
            """.trimIndent(),
            location = WeatherLocation(45.3, -72.65, "Bromont, Quebec"),
        )

        assertEquals(2, points.size)
        assertEquals(
            ThunderstormRiskValue.Wording("Risk of thunderstorms"),
            points[0].value,
        )
        assertEquals(ThunderstormRiskValue.Unavailable, points[1].value)
    }

    @Test
    fun numericRiskWinsWhenBothSourcesHaveTheSameTimestamp() {
        val timestamp = 1_000L
        val merged = mergeThunderstormRiskSeries(
            numeric = listOf(
                ThunderstormRiskPoint(
                    timestamp,
                    ThunderstormRiskValue.Percentage(35),
                ),
            ),
            wording = listOf(
                ThunderstormRiskPoint(
                    timestamp,
                    ThunderstormRiskValue.Wording("Risk of a thunderstorm"),
                ),
            ),
        )

        assertEquals(ThunderstormRiskValue.Percentage(35), merged.single().value)
    }

    @Test
    fun wordingParserKeepsOnlyThunderstormRiskPhrase() {
        assertEquals(
            "Risk of a thunderstorm in the afternoon",
            thunderstormWording("Cloudy. Risk of a thunderstorm in the afternoon."),
        )
        assertEquals(
            "Chance of showers or thunderstorms",
            thunderstormWording("Chance of showers or thunderstorms."),
        )
        assertEquals(null, thunderstormWording("Cloudy with a few showers."))
    }
}
