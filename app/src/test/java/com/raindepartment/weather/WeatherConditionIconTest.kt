package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherConditionIconTest {
    @Test
    fun everyWeatherConditionHasItsOwnIconKindAndAccessibleLabel() {
        val specs = WeatherCondition.entries.map(::weatherConditionIconSpec)

        assertEquals(WeatherCondition.entries.size, specs.size)
        assertEquals(WeatherCondition.entries.size, specs.map { it.kind }.distinct().size)
        specs.forEach { spec ->
            assertNotEquals("", spec.contentDescription.trim())
        }
    }

    @Test
    fun intensityPairsDifferByVisibleMarkDensity() {
        assertEquals(2, weatherConditionIconSpec(WeatherCondition.DRIZZLE).weatherMarkCount)
        assertEquals(3, weatherConditionIconSpec(WeatherCondition.RAIN).weatherMarkCount)
        assertEquals(4, weatherConditionIconSpec(WeatherCondition.HEAVY_RAIN).weatherMarkCount)

        assertEquals(2, weatherConditionIconSpec(WeatherCondition.SNOW).weatherMarkCount)
        assertEquals(3, weatherConditionIconSpec(WeatherCondition.HEAVY_SNOW).weatherMarkCount)
    }

    @Test
    fun wintryMixCombinesTwoWeatherMarks() {
        val wintryMix = weatherConditionIconSpec(WeatherCondition.WINTRY_MIX)

        assertEquals(WeatherConditionIconKind.WINTRY_MIX, wintryMix.kind)
        assertEquals(2, wintryMix.weatherMarkCount)
    }

    @Test
    fun severeWeatherDoublesTheThunderstormBoltCount() {
        assertEquals(1, weatherConditionIconSpec(WeatherCondition.THUNDERSTORM).weatherMarkCount)
        assertEquals(2, weatherConditionIconSpec(WeatherCondition.SEVERE_WEATHER).weatherMarkCount)
    }
}
