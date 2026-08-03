package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshCadenceTest {
    @Test
    fun cadenceFollowsRainStartHorizon() {
        val now = 1_000_000L
        val forecast = DashboardForecastTestData.forecast

        assertEquals(45L, refreshCadenceMinutes(forecast, now))
        assertEquals(
            45L,
            refreshCadenceMinutes(forecast.copy(rainStartsAtEpochMillis = now + 181 * 60_000L), now),
        )
        assertEquals(
            12L,
            refreshCadenceMinutes(forecast.copy(rainStartsAtEpochMillis = now + 180 * 60_000L), now),
        )
        assertEquals(
            12L,
            refreshCadenceMinutes(forecast.copy(rainStartsAtEpochMillis = now + 61 * 60_000L), now),
        )
        assertEquals(
            6L,
            refreshCadenceMinutes(forecast.copy(rainStartsAtEpochMillis = now + 60 * 60_000L), now),
        )
        assertEquals(
            6L,
            refreshCadenceMinutes(forecast.copy(rainStartsAtEpochMillis = now + 29 * 60_000L), now),
        )
    }

    @Test
    fun widgetRefreshOnlyRunsDuringTheFinalHourBeforeRain() {
        val now = 1_000_000L
        val forecast = DashboardForecastTestData.forecast

        assertTrue(
            shouldScheduleWidgetRefresh(
                forecast.copy(rainStartsAtEpochMillis = now + 60 * 60_000L),
                now,
            ),
        )
        assertFalse(
            shouldScheduleWidgetRefresh(
                forecast.copy(rainStartsAtEpochMillis = now + 61 * 60_000L),
                now,
            ),
        )
        assertFalse(
            shouldScheduleWidgetRefresh(
                forecast.copy(rainStartsAtEpochMillis = now),
                now,
            ),
        )
    }
}
