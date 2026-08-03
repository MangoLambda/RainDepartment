package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineIntensityTest {
    @Test
    fun intensityBandsUseRainRateThresholds() {
        assertEquals(TimelineIntensityBand.NONE, timelineIntensityBand(0f))
        assertEquals(TimelineIntensityBand.NONE, timelineIntensityBand(-1f))
        assertEquals(TimelineIntensityBand.LIGHT, timelineIntensityBand(2.49f))
        assertEquals(TimelineIntensityBand.MODERATE, timelineIntensityBand(2.5f))
        assertEquals(TimelineIntensityBand.MODERATE, timelineIntensityBand(7.59f))
        assertEquals(TimelineIntensityBand.HEAVY, timelineIntensityBand(7.6f))
    }

    @Test
    fun scaleRoundsUpToReadableTicksAndHandlesDryData() {
        val scale = timelineIntensityScale(listOf(0.1f, 0.8f, 0.4f))
        assertEquals(1f, scale.maximumMillimetersPerHour)
        assertEquals(0.5f, scale.midpointMillimetersPerHour)

        val dryScale = timelineIntensityScale(listOf(0f, 0f))
        assertEquals(0.5f, dryScale.maximumMillimetersPerHour)
        assertEquals(0.25f, dryScale.midpointMillimetersPerHour)
    }

    @Test
    fun scaleUsesHalfMillimeterMinimumForTraceRain() {
        val scale = timelineIntensityScale(listOf(0.05f, 0.02f))

        assertEquals(0.5f, scale.maximumMillimetersPerHour)
        assertEquals(0.25f, scale.midpointMillimetersPerHour)
    }

    @Test
    fun scaleExpandsForValuesAboveTenMillimetersPerHour() {
        val scale = timelineIntensityScale(listOf(12f))

        assertEquals(20f, scale.maximumMillimetersPerHour)
        assertEquals(10f, scale.midpointMillimetersPerHour)
    }

    @Test
    fun highRainBarsUseA75PercentFloorWithASmoothOneToFiveMillimeterRamp() {
        assertEquals(
            0.1f,
            timelineIntensityBarHeightFraction(1f, 10f),
            0.0001f,
        )
        assertEquals(
            0.425f,
            timelineIntensityBarHeightFraction(3f, 10f),
            0.0001f,
        )
        assertEquals(
            0.75f,
            timelineIntensityBarHeightFraction(5f, 10f),
            0.0001f,
        )
        assertEquals(
            0.75f,
            timelineIntensityBarHeightFraction(8f, 20f),
            0.0001f,
        )
    }

    @Test
    fun displayValuesConvertRadarRatesToSelectedUnits() {
        assertEquals(
            25.4f,
            timelineIntensityDisplayValue(25.4f, UnitSystem.METRIC),
        )
        assertEquals(
            1f,
            timelineIntensityDisplayValue(25.4f, UnitSystem.IMPERIAL),
        )
        assertEquals("1.0", timelineIntensityAxisLabel(25.4f, UnitSystem.IMPERIAL))
        assertTrue(timelineIntensityAxisLabel(0f, UnitSystem.METRIC) == "0")
    }

    @Test
    fun tooltipRateLabelUsesTheSelectedUnitSystem() {
        assertEquals("0.8 mm/h", timelineIntensityRateLabel(0.8f, UnitSystem.METRIC))
        assertEquals("0.03 in/h", timelineIntensityRateLabel(0.8f, UnitSystem.IMPERIAL))
    }
}
