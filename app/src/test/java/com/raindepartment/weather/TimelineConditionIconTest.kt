package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineConditionIconTest {
    @Test
    fun drizzleAndShowersUseDifferentDropCounts() {
        assertEquals(1, timelineRainDropCount(WeatherCondition.DRIZZLE))
        assertEquals(3, timelineRainDropCount(WeatherCondition.RAIN))

        val showerPlacements = timelineRainDropPlacements(WeatherCondition.RAIN)
        assertEquals(3, showerPlacements.map { it.xFraction }.distinct().size)
        assertEquals(3, showerPlacements.map { it.yFraction }.distinct().size)
    }

    @Test
    fun nonRainConditionsDoNotUseRainDropOverlay() {
        assertEquals(4, timelineRainDropCount(WeatherCondition.HEAVY_RAIN))
        assertNull(timelineRainDropCount(WeatherCondition.OVERCAST))
    }
}
