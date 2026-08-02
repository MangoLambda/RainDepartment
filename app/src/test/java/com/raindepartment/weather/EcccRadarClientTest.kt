package com.raindepartment.weather

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
    fun mapViewportExpandsAndContractsWithZoom() {
        val wideViewport = EcccRadarMapViewport.centeredOn(
            location = AustinLocation,
            zoom = RADAR_MIN_ZOOM,
            width = 360,
            height = 560,
        )
        val baseViewport = EcccRadarMapViewport.centeredOn(
            location = AustinLocation,
            zoom = 1f,
            width = 360,
            height = 560,
        )
        val closeViewport = EcccRadarMapViewport.centeredOn(
            location = AustinLocation,
            zoom = RADAR_MAX_ZOOM,
            width = 360,
            height = 560,
        )

        assertTrue(wideViewport.latitudeSpan > baseViewport.latitudeSpan)
        assertTrue(closeViewport.latitudeSpan < baseViewport.latitudeSpan)

        val wideBbox = decodedQueryValue(
            ecccRadarMapUrl(AustinLocation, 1_785_660_000_000L, wideViewport),
            "bbox",
        ).split(",").map(String::toDouble)
        val closeBbox = decodedQueryValue(
            ecccRadarMapUrl(AustinLocation, 1_785_660_000_000L, closeViewport),
            "bbox",
        ).split(",").map(String::toDouble)

        assertTrue(wideBbox[2] - wideBbox[0] > closeBbox[2] - closeBbox[0])
        assertEquals(360, decodedQueryValue(
            ecccRadarMapUrl(AustinLocation, 1_785_660_000_000L, wideViewport),
            "width",
        ).toInt())
    }

    @Test
    fun minimumZoomReachesContinentScale() {
        val continentViewport = EcccRadarMapViewport.centeredOn(
            location = AustinLocation,
            zoom = RADAR_MIN_ZOOM,
        )

        assertTrue(continentViewport.latitudeSpan > 50.0)
        assertTrue(continentViewport.longitudeSpan > 35.0)
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

    @Test
    fun radarSeriesUsesSixMinuteFramesWithinRequestedWindow() {
        val window = EcccRadarTimeWindow(
            startEpochMillis = 1_000L,
            endEpochMillis = 1_000L + 30 * 60_000L,
            intervalMillis = 6 * 60_000L,
        )

        assertEquals(
            listOf(
                1_000L + 6 * 60_000L,
                1_000L + 12 * 60_000L,
                1_000L + 18 * 60_000L,
            ),
            ecccRadarFrameTimes(
                window = window,
                requestedStartEpochMillis = 1_000L + 5 * 60_000L,
                requestedEndEpochMillis = 1_000L + 20 * 60_000L,
            ),
        )
    }

    private fun decodedQueryValue(url: String, key: String): String = url
        .substringAfter("?")
        .split("&")
        .map { parameter ->
            val parts = parameter.split("=", limit = 2)
            URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
        }
        .first { it.first == key }
        .second
}
