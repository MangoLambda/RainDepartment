package com.raindepartment.weather

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun gestureZoomDirectionExpandsAndContractsTheViewportAsExpected() {
        val source = EcccRadarMapViewport.centeredOn(AustinLocation)

        val zoomedOut = radarViewportForTransform(
            sourceViewport = source,
            scale = radarScaleAfterGesture(previousScale = 1f, zoomChange = 2f),
            translation = Offset.Zero,
        )
        val zoomedIn = radarViewportForTransform(
            sourceViewport = source,
            scale = radarScaleAfterGesture(previousScale = 1f, zoomChange = 0.5f),
            translation = Offset.Zero,
        )

        assertTrue(zoomedOut.latitudeSpan > source.latitudeSpan)
        assertTrue(zoomedIn.latitudeSpan < source.latitudeSpan)
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

    @Test
    fun baseZoomAllowsDraggingToRequestANewViewport() {
        val source = EcccRadarMapViewport.centeredOn(AustinLocation)
        val pan = Offset(x = 80f, y = -120f)

        val target = radarViewportForTransform(
            sourceViewport = source,
            scale = 1f,
            translation = pan,
            width = 360f,
            height = 600f,
        )

        assertEquals(source.latitudeSpan, target.latitudeSpan, 0.000001)
        assertEquals(
            source.centerLongitude - (80.0 / 360.0) * source.longitudeSpan,
            target.centerLongitude,
            0.000001,
        )
        assertEquals(
            source.centerLatitude - (120.0 / 600.0) * source.latitudeSpan,
            target.centerLatitude,
            0.000001,
        )
    }

    @Test
    fun refreshStartedBeforeViewportChangeCannotReplaceTheNewZoom() {
        val previousViewport = EcccRadarMapViewport.centeredOn(AustinLocation, zoom = 2f)
        val unzoomedViewport = EcccRadarMapViewport.centeredOn(AustinLocation, zoom = 1f)

        assertFalse(
            shouldApplyRadarRefreshResult(
                requestSerialAtStart = 4,
                currentRequestSerial = 5,
                requestedViewport = previousViewport,
                activeViewport = unzoomedViewport,
            ),
        )
        assertFalse(
            shouldApplyRadarRefreshResult(
                requestSerialAtStart = 4,
                currentRequestSerial = 4,
                requestedViewport = previousViewport,
                activeViewport = unzoomedViewport,
            ),
        )
    }

    @Test
    fun refreshForTheActiveViewportCanBeApplied() {
        val viewport = EcccRadarMapViewport.centeredOn(AustinLocation, zoom = 1f)

        assertTrue(
            shouldApplyRadarRefreshResult(
                requestSerialAtStart = 4,
                currentRequestSerial = 4,
                requestedViewport = viewport,
                activeViewport = viewport,
            ),
        )
    }
}
