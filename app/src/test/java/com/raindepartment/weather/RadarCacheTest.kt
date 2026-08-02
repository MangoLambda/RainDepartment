package com.raindepartment.weather

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarCacheTest {
    @Test
    fun fileCacheRoundTripsWindowAndPngFrame() {
        val directory = temporaryDirectory()
        try {
            val now = 1_785_660_000_000L
            val cache = FileEcccRadarMapCache(directory, clock = { now })
            val window = EcccRadarTimeWindow(
                startEpochMillis = now - 30 * 60_000L,
                endEpochMillis = now,
                intervalMillis = 6 * 60_000L,
            )
            val viewport = EcccRadarMapViewport.centeredOn(AustinLocation, zoom = 0.2f)
            val frame = EcccRadarMapFrame(
                timeEpochMillis = now,
                imageBytes = byteArrayOf(1, 2, 3, 4),
                viewport = viewport,
            )

            cache.writeTimeWindow("RADAR_1KM_RRAI", window, now)
            val key = ecccRadarFrameCacheKey(AustinLocation, now, viewport)
            cache.writeFrame(key, frame, now)

            val cachedWindow = cache.readTimeWindow("RADAR_1KM_RRAI")
            val cachedFrame = cache.readFrame(key)
            assertNotNull(cachedWindow)
            assertNotNull(cachedFrame)
            assertEquals(window, cachedWindow!!.window)
            assertEquals(now, cachedWindow.cachedAtEpochMillis)
            assertEquals(viewport, cachedFrame!!.frame.viewport)
            assertTrue(frame.imageBytes.contentEquals(cachedFrame.frame.imageBytes))
            assertTrue(cachedFrame.frame.isFromCache)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cacheKeysSeparateLocationTimeAndViewport() {
        val viewport = EcccRadarMapViewport.centeredOn(AustinLocation)
        val same = ecccRadarFrameCacheKey(AustinLocation, 100L, viewport)
        val otherTime = ecccRadarFrameCacheKey(AustinLocation, 200L, viewport)
        val otherViewport = ecccRadarFrameCacheKey(
            AustinLocation,
            100L,
            EcccRadarMapViewport.centeredOn(AustinLocation, zoom = 0.2f),
        )
        val otherLocation = ecccRadarFrameCacheKey(
            WeatherLocation(30.3, -97.7, "Nearby"),
            100L,
            viewport,
        )

        assertNotEquals(same, otherTime)
        assertNotEquals(same, otherViewport)
        assertNotEquals(same, otherLocation)
    }

    private fun temporaryDirectory(): File = File.createTempFile("radar-cache", "").apply {
        delete()
        mkdirs()
    }
}
