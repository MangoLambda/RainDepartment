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
            scale = radarScaleAfterGesture(previousScale = 1f, zoomChange = 0.5f),
            translation = Offset.Zero,
        )
        val zoomedIn = radarViewportForTransform(
            sourceViewport = source,
            scale = radarScaleAfterGesture(previousScale = 1f, zoomChange = 2f),
            translation = Offset.Zero,
        )

        assertTrue(zoomedOut.latitudeSpan > source.latitudeSpan)
        assertTrue(zoomedIn.latitudeSpan < source.latitudeSpan)
    }

    @Test
    fun fetchedFrameUsesTheViewportThatWasRequested() {
        val requestedViewport = EcccRadarMapViewport.centeredOn(AustinLocation, zoom = 0.5f)
        val responseFrame = EcccRadarMapFrame(
            timeEpochMillis = 1L,
            imageBytes = ByteArray(0),
            viewport = EcccRadarMapViewport.centeredOn(AustinLocation, zoom = 2f),
        )

        assertEquals(
            requestedViewport.cacheKey(),
            radarFrameForRequestedViewport(responseFrame, requestedViewport)
                .viewport
                ?.cacheKey(),
        )
    }

    @Test
    fun radarCityLabelsFollowTheirGeographicPositions() {
        val sherbrooke = WeatherCities.all.first { it.label == "Sherbrooke, Quebec" }
        val viewport = EcccRadarMapViewport.centeredOn(sherbrooke.location)
        val placements = radarMapLabelPlacements(sherbrooke.location, viewport)
        val magog = placements.first { it.label == "Magog" }
        val bromont = placements.first { it.label == "Bromont" }
        val granby = placements.first { it.label == "Granby" }

        assertTrue(magog.point.x < 0.5f)
        assertTrue(magog.point.y > 0.5f)
        assertTrue(bromont.point.x < magog.point.x)
        assertTrue(granby.point.x < bromont.point.x)
    }

    @Test
    fun baseMapGeometryRemainsAnchoredWhenTheDownloadedViewportChanges() {
        val anchorViewport = EcccRadarMapViewport.centeredOn(AustinLocation)
        val zoomedViewport = EcccRadarMapViewport.centeredOn(
            location = AustinLocation,
            zoom = 2f,
        )

        val anchorPoint = radarBaseMapPoint(
            x = 0.25f,
            y = 0.25f,
            anchorViewport = anchorViewport,
            viewport = anchorViewport,
        )
        val zoomedPoint = radarBaseMapPoint(
            x = 0.25f,
            y = 0.25f,
            anchorViewport = anchorViewport,
            viewport = zoomedViewport,
        )

        assertEquals(0.25f, anchorPoint.x, 0.001f)
        assertEquals(0.25f, anchorPoint.y, 0.001f)
        assertEquals(0f, zoomedPoint.x, 0.001f)
        assertEquals(0f, zoomedPoint.y, 0.001f)
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
