package com.raindepartment.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log2
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.ImageSource

internal const val RADAR_MAP_AMBIENT_CACHE_BYTES = 96L * 1024L * 1024L
internal const val RADAR_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

private const val RADAR_IMAGE_SOURCE_ID = "raindepartment-radar-image"
private const val RADAR_IMAGE_LAYER_ID = "raindepartment-radar-image-layer"

private val radarMapSdkInitialized = AtomicBoolean(false)

internal fun initializeRadarMapSdk(context: Context) {
    if (!radarMapSdkInitialized.compareAndSet(false, true)) return

    val applicationContext = context.applicationContext
    MapLibre.getInstance(applicationContext)
    OfflineManager.getInstance(applicationContext)
        .setMaximumAmbientCacheSize(RADAR_MAP_AMBIENT_CACHE_BYTES, null)
}

@Composable
internal fun RadarMapBase(
    modifier: Modifier,
    viewport: EcccRadarMapViewport,
    radarBitmap: Bitmap?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember(context) {
        initializeRadarMapSdk(context)
        RadarMapView(context).also {
            it.onCreate(null)
            it.initializeMap()
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = {
            it.updateViewport(viewport)
            it.updateRadarImage(radarBitmap, viewport)
        },
    )
}

private class RadarMapView(context: Context) : MapView(context) {
    private var mapLibreMap: MapLibreMap? = null
    private var loadedStyle: Style? = null
    private var pendingViewport: EcccRadarMapViewport? = null
    private var appliedViewportKey: String? = null
    private var pendingRadarImage: Pair<Bitmap, EcccRadarMapViewport>? = null
    private var appliedRadarImageKey: String? = null
    private var radarImageSource: ImageSource? = null

    init {
        setBackgroundColor(AndroidColor.rgb(230, 236, 221))
    }

    fun initializeMap() {
        getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.setAllGesturesEnabled(false)
            map.uiSettings.setLogoEnabled(false)
            map.setStyle(Style.Builder().fromUri(RADAR_MAP_STYLE_URL)) { style ->
                loadedStyle = style
                pendingViewport?.let(::applyViewport)
                pendingRadarImage?.let { (bitmap, viewport) ->
                    applyRadarImage(bitmap, viewport)
                }
            }
        }
    }

    fun updateViewport(viewport: EcccRadarMapViewport) {
        pendingViewport = viewport
        if (loadedStyle != null) applyViewport(viewport)
    }

    fun updateRadarImage(
        bitmap: Bitmap?,
        viewport: EcccRadarMapViewport,
    ) {
        if (bitmap == null) return
        val key = "${viewport.cacheKey()}:${System.identityHashCode(bitmap)}"
        if (key == appliedRadarImageKey) return
        pendingRadarImage = bitmap to viewport
        if (loadedStyle != null) applyRadarImage(bitmap, viewport)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        pendingViewport?.let { viewport ->
            if (width > 0 && height > 0) post { applyViewport(viewport) }
        }
    }

    private fun applyViewport(viewport: EcccRadarMapViewport) {
        val map = mapLibreMap ?: return
        if (width <= 0 || height <= 0) {
            post { applyViewport(viewport) }
            return
        }
        if (viewport.cacheKey() == appliedViewportKey) return

        val bounds = viewport.toLatLngBounds()
        val camera = map.getCameraForLatLngBounds(bounds) ?: return
        map.setMaxZoomPreference(
            (camera.zoom + log2(RADAR_MAX_ZOOM.toDouble()))
                .coerceIn(0.0, 25.5),
        )
        map.cameraPosition = CameraPosition.Builder(camera)
            .bearing(0.0)
            .tilt(0.0)
            .build()
        appliedViewportKey = viewport.cacheKey()
    }

    private fun applyRadarImage(
        bitmap: Bitmap,
        viewport: EcccRadarMapViewport,
    ) {
        val style = loadedStyle ?: return
        val source = radarImageSource ?: ImageSource(
            RADAR_IMAGE_SOURCE_ID,
            viewport.toLatLngQuad(),
            bitmap,
        ).also { imageSource ->
            radarImageSource = imageSource
            style.addSource(imageSource)
            val rasterLayer = RasterLayer(RADAR_IMAGE_LAYER_ID, RADAR_IMAGE_SOURCE_ID)
            val firstSymbolLayer = style.layers
                .firstOrNull { it is SymbolLayer }
                ?.id
            if (firstSymbolLayer != null) {
                style.addLayerBelow(rasterLayer, firstSymbolLayer)
            } else {
                style.addLayer(rasterLayer)
            }
        }

        source.setCoordinates(viewport.toLatLngQuad())
        source.setImage(bitmap)
        appliedRadarImageKey = "${viewport.cacheKey()}:${System.identityHashCode(bitmap)}"
    }
}

private fun EcccRadarMapViewport.toLatLngBounds(): LatLngBounds = LatLngBounds.from(
    latNorth = centerLatitude + latitudeSpan / 2.0,
    lonEast = centerLongitude + longitudeSpan / 2.0,
    latSouth = centerLatitude - latitudeSpan / 2.0,
    lonWest = centerLongitude - longitudeSpan / 2.0,
)

private fun EcccRadarMapViewport.toLatLngQuad(): LatLngQuad {
    val north = centerLatitude + latitudeSpan / 2.0
    val south = centerLatitude - latitudeSpan / 2.0
    val east = centerLongitude + longitudeSpan / 2.0
    val west = centerLongitude - longitudeSpan / 2.0
    return LatLngQuad(
        topLeft = LatLng(north, west),
        topRight = LatLng(north, east),
        bottomRight = LatLng(south, east),
        bottomLeft = LatLng(south, west),
    )
}
