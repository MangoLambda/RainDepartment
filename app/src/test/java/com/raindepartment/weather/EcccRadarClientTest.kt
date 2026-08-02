package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcccRadarClientTest {
    @Test
    fun parsesSixMinuteRadarTimeWindow() {
        val window = parseEcccRadarTimeWindow(
            """
                <Layer>
                  <Dimension name="time" units="ISO8601">2026-08-02T03:24:00Z/2026-08-02T04:36:00Z/PT6M</Dimension>
                </Layer>
            """.trimIndent(),
        )

        assertEquals(72 * 60_000L, window.endEpochMillis - window.startEpochMillis)
        assertEquals(6 * 60_000L, window.intervalMillis)
    }

    @Test
    fun parsesRadarRateFromFeatureInfo() {
        val rate = parseEcccRadarRainRate(
            """
                {
                  "type":"FeatureCollection",
                  "features":[{"properties":{"value":1.25,"class":"Light"}}]
                }
            """.trimIndent(),
        )

        assertEquals(1.25, rate!!, 0.0)
        assertNull(parseEcccRadarRainRate("{\"features\":[]}"))
    }

    @Test
    fun featureInfoUsesTheEcccOneKilometreExtrapolationLayer() {
        val url = ecccRadarFeatureInfoUrl(
            location = AustinLocation,
            timeEpochMillis = 1_785_660_000_000L,
        )

        assertTrue(url.contains("Radar_1km_RainPrecipRate-Extrapolation"))
        assertTrue(url.contains("GetFeatureInfo"))
        assertTrue(url.contains("EPSG%3A4326"))
        assertTrue(url.contains("time%3D") || url.contains("time="))
    }

    @Test
    fun mapUsesOneKilometreCompositeAndRequestedFrame() {
        val url = ecccRadarMapUrl(
            location = AustinLocation,
            timeEpochMillis = 1_785_660_000_000L,
            width = 360,
            height = 560,
        )

        assertTrue(url.contains("request=GetMap"))
        assertTrue(url.contains("RADAR_1KM_RRAI"))
        assertTrue(url.contains("image%2Fpng"))
        assertTrue(url.contains("time=2026-08-02T") || url.contains("time%3D2026-08-02T"))
    }

    @Test
    fun latestFrameDoesNotRunPastTheAvailableWindow() {
        val window = EcccRadarTimeWindow(
            startEpochMillis = 1_000L,
            endEpochMillis = 1_000L + 12 * 60_000L,
            intervalMillis = 6 * 60_000L,
        )

        assertEquals(window.endEpochMillis, latestEcccRadarFrameTime(window, Long.MAX_VALUE))
        assertEquals(window.startEpochMillis, latestEcccRadarFrameTime(window, 0L))
    }
}
