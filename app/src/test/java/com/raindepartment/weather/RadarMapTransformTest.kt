package com.raindepartment.weather

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarMapTransformTest {
    @Test
    fun transformUsesTheRenderedMapSizeForPanAndPinch() {
        val source = EcccRadarMapViewport.centeredOn(AustinLocation)
        val target = radarViewportForTransform(
            sourceViewport = source,
            scale = 2f,
            translation = Offset(x = -72f, y = 96f),
            width = 360f,
            height = 600f,
        )

        val transform = radarMapTransformForViewport(
            sourceViewport = source,
            targetViewport = target,
            width = 360f,
            height = 600f,
        )

        assertEquals(2f, transform.scale, 0.001f)
        assertEquals(-72f, transform.translation.x, 0.01f)
        assertEquals(96f, transform.translation.y, 0.01f)
        assertEquals(source.latitudeSpan / 2.0, target.latitudeSpan, 0.000001)
    }

    @Test
    fun zoomingAcrossDownloadedFramesKeepsTheAbsoluteZoomLimits() {
        val source = EcccRadarMapViewport.centeredOn(
            location = AustinLocation,
            zoom = RADAR_MAX_ZOOM,
        )

        val target = radarViewportForTransform(
            sourceViewport = source,
            scale = RADAR_MIN_GESTURE_SCALE,
            translation = Offset.Zero,
            width = 360f,
            height = 600f,
        )

        assertEquals(
            RADAR_MAP_LATITUDE_SPAN / RADAR_MIN_ZOOM,
            target.latitudeSpan,
            0.000001,
        )
    }
}
